#!/bin/sh
# Regression: stats.cgi must read a BOUNDED window of miner.log, never the whole file.
#
# miner.log is append-only and unrotated. In normal operation it reaches hundreds
# of megabytes (measured on the live fleet: 30MB / 105MB / 151MB / 237MB). stats.cgi
# assigned the entire file to a shell variable:
#
#     CLEAN_LOG=$(sed 's/...//g' "$LOG_FILE")
#
# On a 2GB board that allocated ~450MB of VM and invoked the kernel OOM killer.
# Captured from dmesg on a production miner, 2026-09-11 01:33:11 UTC:
#
#     stats.cgi invoked oom-killer: gfp_mask=0x500cc2, order=0, oom_score_adj=0
#     oom-kill:constraint=CONSTRAINT_NONE,...,global_oom,task=stats.cgi,pid=1316838
#     Out of memory: Killed process 1316838 (stats.cgi) total-vm:448968kB, anon-rss:185904kB
#
# The mining process stopped two seconds later. So this is not a performance nit:
# merely OPENING THE STATISTICS TAB could kill the miner on a host with a large log.
#
# Every lookup in stats.cgi is a "tail -1" / "last match" query, so only the most
# recent lines can affect the result -- a bounded read is behaviour-preserving for
# every field except the accepted/rejected counters, which become window-scoped.
#
# RED on pre-fix code: the whole-file slurp makes runtime scale with log size.
# GREEN after:         runtime is flat and the source no longer slurps.
#
# Hermetic: builds its own synthetic log in a sandbox. Set FRYPOW_SETUP to point
# at an alternate installer (used to prove RED against a pre-fix backup).
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_SETUP:-$DIR/../setup_fryminer_web.sh}"
[ -f "$SETUP" ] || { echo "SKIP: installer not found at $SETUP"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" stats.cgi "$SETUP" "$SB/stats.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract stats.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids"
printf 'miner=verus\nwallet=Wtest\npool=pool.example.com:3333\n' > "$SB/config.txt"

FAIL=0

# ---- 1. Source guard: the log read must be bounded -------------------------
#         Checked on the EXTRACTED artifact, which is what actually ships.
ASSIGN=$(grep -n 'CLEAN_LOG=' "$SB/stats.cgi" | head -1)
if [ -z "$ASSIGN" ]; then
    echo "ASSERT FAIL: no CLEAN_LOG assignment found in stats.cgi"
    FAIL=1
elif echo "$ASSIGN" | grep -q 'tail -n'; then
    :
else
    echo "ASSERT FAIL: stats.cgi reads miner.log unbounded (no 'tail -n' in the CLEAN_LOG read)."
    echo "    This is the OOM-killer path. Offending line:"
    echo "    $ASSIGN" | cut -c1-160
    FAIL=1
fi

# ---- 2. Functional: a large log must not make stats.cgi scale with its size -
#         150k lines of realistic ccminer output (~12MB). A whole-file slurp
#         must echo that entire buffer through ~20 separate grep pipelines.
build_log() {
    _n="$1"; _f="$2"
    yes '[2026-09-11 01:00:00] Server requested reconnection to stratum+tcp://pool.example.com:50912' \
        2>/dev/null | head -n "$_n" > "$_f"
    # the values the parser must still find, at the very end
    {
        printf '[2026-09-11 01:59:58] Stratum difficulty set to 262140\n'
        printf '[2026-09-11 01:59:59] CPU T0: Verus Hashing. (null), 512.25 kH/s\n'
        printf '[2026-09-11 01:59:59] accepted: 7/7 (diff 460516.216), 873.17 kH/s yes!\n'
    } >> "$_f"
}

LOG="$SB/logs/miner.log"
build_log 150000 "$LOG"
BYTES=$(wc -c < "$LOG" | tr -d ' ')

T0=$(date +%s)
OUT=$(cd "$SB" && sh "$SB/stats.cgi" 2>/dev/null)
T1=$(date +%s)
ELAPSED=$((T1 - T0))

echo "INFO: synthetic log ${BYTES} bytes, stats.cgi took ${ELAPSED}s"

# Parser must still find values that live at the END of the log.
if ! printf '%s' "$OUT" | grep -q '512.25 kH/s'; then
    echo "ASSERT FAIL: hashrate not parsed from the tail of a large log. Got:"
    printf '%s' "$OUT" | tail -3 | sed 's/^/    /'
    FAIL=1
fi
if ! printf '%s' "$OUT" | grep -q '"diff"'; then
    echo "ASSERT FAIL: stats.cgi did not emit a well-formed payload. Got:"
    printf '%s' "$OUT" | tail -3 | sed 's/^/    /'
    FAIL=1
fi

# Runtime ceiling. A bounded read is ~instant; the whole-file slurp pipes the
# entire buffer through every grep and blows past this on the same machine.
CEILING=${STATS_TIME_CEILING:-20}
if [ "$ELAPSED" -gt "$CEILING" ]; then
    echo "ASSERT FAIL: stats.cgi took ${ELAPSED}s on a ${BYTES}-byte log (ceiling ${CEILING}s)."
    echo "    Runtime scaling with log size is the signature of the unbounded read."
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: stats.cgi reads a bounded window; large logs neither slow it down nor exhaust memory"
    exit 0
fi
exit 1
