#!/bin/sh
# T3-static — Bug 3 regression: generated start.sh for a cpuminer coin (doge)
# must use the unambiguous --retries flag. `--retry` is an ambiguous
# getopt_long prefix (--retries / --retry-pause) -> cpuminer exits <1s.
# RED on pre-fix code: start.sh contains `--retry 10`.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

SB=$(mktemp -d)
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output"

DATA='miner=doge&wallet=D7Yr3ZifuFsLtestWalletAddr0123456789&worker=w1&threads=1&pool=doge.millpools.cc%3A5567&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'

OUT=$(printf '%s' "$DATA" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA} sh "$SB/save.cgi" 2>/dev/null)

SS="$SB/output/doge/start.sh"
if [ ! -f "$SS" ]; then
    echo "FAIL: $SS was not generated. save.cgi output was:"
    echo "$OUT"
    exit 1
fi

FAIL=0
if grep -Eq -- '--retry [0-9]' "$SS"; then
    echo "ASSERT FAIL: start.sh uses ambiguous --retry flag:"
    grep -n -- '--retry ' "$SS"
    FAIL=1
fi
COUNT=$(grep -c -- '--retries 10' "$SS" || true)
if [ "$COUNT" -ne 2 ]; then
    echo "ASSERT FAIL: expected 2 cpuminer lines with --retries 10 (user + dev-fee), found $COUNT"
    FAIL=1
fi
if grep -Eq 'cpuminer [^|]*--retry [0-9]' "$SETUP"; then
    echo "ASSERT FAIL: setup script still emits cpuminer --retry:"
    grep -nE 'cpuminer [^|]*--retry [0-9]' "$SETUP"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then echo "PASS: cpuminer launch lines use --retries"; exit 0; fi
exit 1
