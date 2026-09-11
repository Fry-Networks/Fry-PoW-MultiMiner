#!/bin/sh
# Regression: status.cgi must be READ-ONLY and must not report running:true when no miner
# process exists.
#
# It is polled every 5 seconds by the Monitor tab, and it used to:
#   1. WRITE miner.pid  -- overwriting the wrapper PID that start.cgi stored with the PID
#      of a miner binary. stop.cgi then killed the miner but not the start.sh supervisor,
#      which noticed "All miner processes died" and respawned it. Stop did not stop.
#   2. rm -f the "stopped" marker, twice. stop.cgi touches that marker and THEN starts
#      killing, so any poll landing in that window -- including the one stopMining() fires
#      itself -- deleted the marker and the supervisor restarted mining.
#   3. Report running:true off a bare `kill -0` against miner.pid. That file holds the
#      WRAPPER pid (start.sh), which stays alive across the whole dev-fee cycle and
#      survives its children dying -- so it succeeded while zero miners existed.
#
# Removing the writes is safe: every reader of miner.pid has a fallback (save.cgi falls
# through to a ps scan, stats.cgi to log-timestamp uptime, stop.cgi has pids/*.pid plus a
# full-path pkill), and both start paths already clear the stopped marker themselves.
#
# RED on pre-fix code: running:true from a stale pid, marker deleted, pid file rewritten.
# GREEN after:         running:false, marker intact, pid file untouched.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_SETUP:-$DIR/../setup_fryminer_web.sh}"
[ -f "$SETUP" ] || { echo "SKIP: installer not found at $SETUP"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" status.cgi "$SETUP" "$SB/status.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract status.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids"
: > "$SB/logs/miner.log"

FAIL=0
running_of() {
    printf '%s' "$1" | tr -d '\r' | grep -o '"running":[a-z]*' | head -1 | sed 's/.*://'
}

# A PID that is alive but is NOT a miner. $$ is this test's own shell -- exactly the shape
# of the real bug, where miner.pid held the live start.sh wrapper rather than a miner.
STALE=$$

# ---- 1. stale pid of a live non-miner must NOT read as running ---------------
printf '%s\n' "$STALE" > "$SB/miner.pid"
rm -f "$SB/stopped"
OUT=$(cd "$SB" && sh "$SB/status.cgi" 2>/dev/null)
R=$(running_of "$OUT")
if [ "$R" != "false" ]; then
    echo "ASSERT FAIL [stale pid]: a live NON-miner pid reported running=$R (want false)"
    echo "    this is the wrapper-pid false positive: kill -0 succeeds, no miner exists"
    FAIL=1
fi

# ---- 2. the stopped marker must survive a poll -------------------------------
printf '%s\n' "$STALE" > "$SB/miner.pid"
: > "$SB/stopped"
(cd "$SB" && sh "$SB/status.cgi" >/dev/null 2>&1)
if [ ! -f "$SB/stopped" ]; then
    echo "ASSERT FAIL [marker]: status.cgi deleted the 'stopped' marker"
    echo "    stop.cgi touches it then kills; a poll in that window undoes the stop"
    FAIL=1
fi

# ---- 3. status.cgi must not rewrite miner.pid --------------------------------
printf 'SENTINEL\n' > "$SB/miner.pid"
: > "$SB/stopped"
(cd "$SB" && sh "$SB/status.cgi" >/dev/null 2>&1)
if [ "$(cat "$SB/miner.pid" 2>/dev/null)" != "SENTINEL" ]; then
    echo "ASSERT FAIL [pid write]: status.cgi rewrote miner.pid (now: $(cat "$SB/miner.pid" 2>/dev/null))"
    echo "    clobbering the wrapper pid is what makes stop.cgi fail to stop"
    FAIL=1
fi

# ---- 4. no pid file at all -> running:false, and none created ----------------
rm -f "$SB/miner.pid" "$SB/stopped"
OUT=$(cd "$SB" && sh "$SB/status.cgi" 2>/dev/null)
R=$(running_of "$OUT")
if [ "$R" != "false" ]; then
    echo "ASSERT FAIL [no pid]: want running=false, got [$R]"
    FAIL=1
fi
if [ -f "$SB/miner.pid" ]; then
    echo "ASSERT FAIL [no pid]: status.cgi created miner.pid out of nothing"
    FAIL=1
fi

# ---- 5. still emits well-formed JSON -----------------------------------------
case "$OUT" in
    *'"running"'*'"crashed"'*) ;;
    *) echo "ASSERT FAIL: malformed payload: [$OUT]"; FAIL=1 ;;
esac

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: status.cgi is read-only and does not report a phantom running miner"
    exit 0
fi
exit 1
