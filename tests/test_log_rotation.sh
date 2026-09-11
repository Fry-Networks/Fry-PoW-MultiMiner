#!/bin/sh
# Regression: miner.log must be rotated, and the rotation must not break the open file
# descriptor the miner holds.
#
# miner.log is append-only and was never rotated -- measured on the live fleet at 30MB,
# 105MB, 151MB and 237MB. That unbounded growth is what let stats.cgi allocate ~450MB and
# invoke the OOM killer (fixed separately by bounding the read); the disk growth itself
# was still unaddressed.
#
# THE CRITICAL PROPERTY: every miner is launched as `... 2>&1 | tee -a "$LOG" &`, and that
# tee holds an open fd for the entire 49-minute dev-fee cycle. A mv-then-touch rotation
# leaves tee writing into the unlinked inode -- the new miner.log stays at 0 bytes forever
# while the old one grows invisibly, and stats.cgi/logs.cgi go blind. tee -a does NOT
# reopen on rename. So rotation must copy-then-truncate-in-place.
#
# Assertion 3 below is the one that distinguishes a correct implementation from a naive
# `mv`: it holds an fd open across the rotation and then writes through it.
#
# RED before the feature exists: no rotation helper is emitted at all.
# GREEN after:                   helper exists, rotates on size, and preserves the inode.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_SETUP:-$DIR/../setup_fryminer_web.sh}"
[ -f "$SETUP" ] || { echo "SKIP: installer not found at $SETUP"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

FAIL=0

# Extract the rotation helper from its heredoc. Same whole-line-anchored technique as
# tests/lib/extract_cgi.sh, but that helper only knows about cgi-bin targets.
tr -d '\r' < "$SETUP" | awk '
    !on && index($0, "cat > \"$BASE/rotate_logs.sh\" <<'"'"'ROTATE'"'"'") { on = 1; next }
    on && $0 ~ /^[[:space:]]*ROTATE[[:space:]]*$/ { found = 1; exit }
    on { print }
    END { if (!found) exit 3 }
' > "$SB/rotate_logs.sh" 2>/dev/null

if [ ! -s "$SB/rotate_logs.sh" ]; then
    echo "ASSERT FAIL: no rotate_logs.sh heredoc found in the installer"
    echo "    miner.log grows without bound (237MB observed on the fleet)"
    exit 1
fi
sed -i "s|/opt/frynet-config|$SB|g" "$SB/rotate_logs.sh" 2>/dev/null || {
    sed "s|/opt/frynet-config|$SB|g" "$SB/rotate_logs.sh" > "$SB/r.tmp" && mv "$SB/r.tmp" "$SB/rotate_logs.sh"; }
chmod +x "$SB/rotate_logs.sh"
mkdir -p "$SB/logs"
LOG="$SB/logs/miner.log"

# ---- 1. it must never mv/rename the live log --------------------------------
if grep -nE '^[[:space:]]*mv[[:space:]]+.*"?\$LOG"?[[:space:]]*$' "$SB/rotate_logs.sh" >/dev/null 2>&1; then
    echo "ASSERT FAIL: rotation renames the live log -- tee keeps writing to the unlinked inode"
    grep -nE '^[[:space:]]*mv[[:space:]]+.*\$LOG' "$SB/rotate_logs.sh" | sed 's/^/    /'
    FAIL=1
fi

# ---- 2. a small log must be left alone ---------------------------------------
printf 'small\n' > "$LOG"
FRYMINER_LOG_MAX_BYTES=1048576 sh "$SB/rotate_logs.sh" >/dev/null 2>&1
if [ ! -f "$LOG" ] || [ "$(cat "$LOG")" != "small" ]; then
    echo "ASSERT FAIL: a below-threshold log was rotated or destroyed"
    FAIL=1
fi

# ---- 3. THE DECISIVE ONE: an open fd must survive the rotation ---------------
#         This is what a `mv` implementation fails.
: > "$LOG"
exec 9>> "$LOG"
i=0
while [ "$i" -lt 2000 ]; do
    printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n' >&9
    i=$((i + 1))
done
BEFORE=$(wc -c < "$LOG" | tr -d ' ')
FRYMINER_LOG_MAX_BYTES=1024 FRYMINER_LOG_KEEP=2 sh "$SB/rotate_logs.sh" >/dev/null 2>&1
AFTER=$(wc -c < "$LOG" | tr -d ' ')
printf 'POSTROTATE_MARKER\n' >&9
exec 9>&-

if [ "$AFTER" -ge "$BEFORE" ]; then
    echo "ASSERT FAIL: log was not truncated (before=$BEFORE after=$AFTER)"
    FAIL=1
fi
if ! grep -q 'POSTROTATE_MARKER' "$LOG" 2>/dev/null; then
    echo "ASSERT FAIL: a write through the pre-rotation fd did not land in the live log."
    echo "    This is the mv-vs-copytruncate failure: the miner's tee is now writing to"
    echo "    an unlinked inode and miner.log will stay empty forever."
    FAIL=1
fi

# ---- 4. an archive must have been produced -----------------------------------
if [ -z "$(ls "$SB/logs"/miner.log.1* 2>/dev/null)" ]; then
    echo "ASSERT FAIL: no rotated archive was created"
    ls -la "$SB/logs" | sed 's/^/    /'
    FAIL=1
fi

# ---- 5. the installer must actually schedule it -------------------------------
if ! grep -q 'rotate_logs.sh' "$SETUP"; then
    echo "ASSERT FAIL: rotate_logs.sh is never referenced (not scheduled, not called)"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: miner.log rotates on size and preserves the inode the miner writes through"
    exit 0
fi
exit 1
