#!/bin/sh
# T2 — Bug 2 regression: save.cgi must NOT report success when the config
# write fails (unopenable miner.lock makes the flock subshell silently skip).
# RED on pre-fix code: prints "Configuration saved" while config.txt was never written.
set -u

if [ "$(id -u)" -eq 0 ]; then
    echo "SKIP: must run as non-root (root bypasses the permission failure)"
    exit 77
fi

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

SB=$(mktemp -d)
trap 'chmod -R u+rwX "$SB" 2>/dev/null; rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output"
# Hostile lock: unopenable for fd-9 redirect (simulates root-owned lock file)
touch "$SB/miner.lock"
chmod 000 "$SB/miner.lock"

DATA='miner=doge&wallet=D7Yr3ZifuFsLtestWalletAddr0123456789&worker=w1&threads=1&pool=doge.millpools.cc%3A5567&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'

OUT=$(printf '%s' "$DATA" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA} sh "$SB/save.cgi" 2>/dev/null)
echo "--- save.cgi output (hostile lock) ---"
echo "$OUT"
echo "--------------------------------------"

HAVE_CONFIG=0
[ -f "$SB/config.txt" ] && grep -q '^miner=doge' "$SB/config.txt" && HAVE_CONFIG=1
CLAIMED_SUCCESS=0
echo "$OUT" | grep -q "Configuration saved" && CLAIMED_SUCCESS=1

echo "config.txt written: $HAVE_CONFIG | success claimed: $CLAIMED_SUCCESS"

if [ "$CLAIMED_SUCCESS" -eq 1 ] && [ "$HAVE_CONFIG" -eq 0 ]; then
    echo "ASSERT FAIL: silent save failure — success banner shown but config.txt was not written"
    exit 1
fi
if [ "$HAVE_CONFIG" -eq 0 ] && ! echo "$OUT" | grep -q "class='error'"; then
    echo "ASSERT FAIL: save failed but no error banner shown"
    exit 1
fi
echo "PASS: save failure is not silent (error reported or config actually written)"
exit 0
