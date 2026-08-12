#!/bin/sh
# T3-live — Bug 3 outcome verification on a REAL deployment (requires
# FRYMINER_LIVE=1, a saved doge config in /opt/frynet-config, cpuminer
# installed, and the web UI on localhost). Asserts the miner stays alive
# 30 consecutive seconds and the log is free of ambiguous-option errors.
#
# NOTE: this file must NOT contain "cpu"+"miner" adjacent in its name or
# have that literal in its own cmdline-visible name: start.cgi/stop.cgi run
# a bare `pkill -9 -f` on that word and would SIGKILL the test itself.
# The match pattern below is spliced for the same reason.
set -u

MINER_BIN_PAT="/usr/local/bin/cpu""miner"

if [ "${FRYMINER_LIVE:-0}" != "1" ]; then
    echo "SKIP: set FRYMINER_LIVE=1 to run the live miner test"
    exit 77
fi

PORT="${FRYMINER_PORT:-8080}"
LOG=/opt/frynet-config/logs/miner.log

echo "--- starting miner via start.cgi ---"
curl -s "http://localhost:$PORT/cgi-bin/start.cgi" 2>&1
echo

# start.sh's wrapper stops old miners (sleep ~3s) before launching — give it
# a 15s grace window before demanding the miner process exist
sleep 15
FAIL=0
i=0
while [ $i -lt 30 ]; do
    if ! pgrep -f "$MINER_BIN_PAT" >/dev/null 2>&1; then
        echo "ASSERT FAIL: miner not running at t=${i}s"
        FAIL=1
        break
    fi
    i=$((i+1))
    sleep 1
done
[ "$FAIL" -eq 0 ] && echo "miner alive for 30 consecutive seconds"

if [ -f "$LOG" ] && grep -qiE 'ambiguous|usage: cpuminer' "$LOG"; then
    echo "ASSERT FAIL: miner.log shows option-parsing failure:"
    grep -iE 'ambiguous|usage: cpuminer' "$LOG" | head -5
    FAIL=1
fi

echo "--- last log lines ---"
tail -5 "$LOG" 2>/dev/null
echo "--- stats.cgi ---"
STATS=$(curl -s "http://localhost:$PORT/cgi-bin/stats.cgi" 2>/dev/null)
echo "$STATS"
if ! echo "$STATS" | grep -q '"hashrate"'; then
    echo "ASSERT FAIL: stats.cgi returned no JSON (crashed?)"
    FAIL=1
elif echo "$STATS" | grep -q '"hashrate":"--"'; then
    echo "ASSERT FAIL: stats.cgi shows no hashrate despite live mining"
    FAIL=1
fi

echo "--- stopping miner ---"
curl -s "http://localhost:$PORT/cgi-bin/stop.cgi" 2>&1 | tail -2
sleep 3
if pgrep -f "$MINER_BIN_PAT" >/dev/null 2>&1; then
    echo "WARN: miner still running after stop.cgi"
fi

[ "$FAIL" -eq 0 ] && { echo "PASS: miner runs continuously without option errors"; exit 0; }
exit 1
