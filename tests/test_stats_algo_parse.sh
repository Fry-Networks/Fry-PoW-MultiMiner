#!/bin/sh
# Regression: stats.cgi must report the MINER's algorithm, not the USB-ASIC one, and must
# never emit a stray closing paren.
#
# start.sh writes two "Algorithm:" banner lines on every start:
#     [date] Algorithm: verushash
#     [date] USB ASIC Mining: false (Algorithm: sha256d)
# The USB-ASIC line is emitted SECOND, and the parser did `grep "Algorithm:" | tail -1`,
# so it always picked that one. The greedy `sed 's/.*Algorithm: *//'` then chewed past
# "USB ASIC Mining: false (" to the LAST match, and `awk '{print $1}'` kept the trailing
# ")" because "sha256d)" contains no whitespace. Net effect: a Verus rig reported its
# algorithm as "sha256d)".
#
# It fires even with USB ASIC mining disabled -- USBASIC_ALGO defaults to sha256d and the
# line is written unconditionally.
#
# Note the symptom is INTERMITTENT on a live miner: CLEAN_LOG is a bounded tail -n 5000,
# so both banners scroll out of the window after a while and the parser falls through to
# its later methods. A clean live reading is therefore NOT evidence the bug is gone --
# which is why this test pins the banner lines explicitly at the end of the window.
#
# RED on pre-fix code: algo comes back "sha256d)".
# GREEN after:         algo comes back "verushash".
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

# Reproduce the exact banner pair start.sh emits, in the order it emits them.
emit_log() {
    {
        printf '[Thu Sep 11 12:00:00 UTC 2026] Starting miner\n'
        printf '[Thu Sep 11 12:00:00 UTC 2026] Algorithm: %s\n' "$1"
        printf '[Thu Sep 11 12:00:00 UTC 2026] USB ASIC Mining: %s (Algorithm: %s)\n' "$2" "$3"
        printf '[Thu Sep 11 12:00:01 UTC 2026] CPU T0: Verus Hashing. (null), 439.00 kH/s\n'
    } > "$SB/logs/miner.log"
}

algo_of() {
    # Pull the field out of the JSON body only -- the CGI emits a Content-type header
    # first, and a naive sed across the whole response would capture that too.
    printf '%s' "$1" | tr -d '\r' | grep -o '"algo":"[^"]*"' | head -1 | sed 's/^"algo":"//; s/"$//'
}

# ---- 1. USB ASIC disabled (the default) -- the common case -------------------
emit_log verushash false sha256d
OUT=$(cd "$SB" && sh "$SB/stats.cgi" 2>/dev/null)
ALGO=$(algo_of "$OUT")
case "$ALGO" in
    *\)*) echo "ASSERT FAIL [usbasic off]: algo contains a stray paren: [$ALGO]"; FAIL=1 ;;
esac
if [ "$ALGO" != "verushash" ]; then
    echo "ASSERT FAIL [usbasic off]: want algo=verushash, got [$ALGO]"
    FAIL=1
fi

# ---- 2. USB ASIC enabled -- the miner's algorithm still wins -----------------
emit_log rx/0 true sha256d
OUT=$(cd "$SB" && sh "$SB/stats.cgi" 2>/dev/null)
ALGO=$(algo_of "$OUT")
case "$ALGO" in
    *\)*) echo "ASSERT FAIL [usbasic on]: algo contains a stray paren: [$ALGO]"; FAIL=1 ;;
esac
if [ "$ALGO" != "rx/0" ]; then
    echo "ASSERT FAIL [usbasic on]: want algo=rx/0, got [$ALGO]"
    FAIL=1
fi

# ---- 3. panthera (xlarig) -- covers the M3 assignment ------------------------
emit_log panthera false sha256d
OUT=$(cd "$SB" && sh "$SB/stats.cgi" 2>/dev/null)
ALGO=$(algo_of "$OUT")
if [ "$ALGO" != "panthera" ]; then
    echo "ASSERT FAIL [panthera]: want algo=panthera, got [$ALGO]"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: stats.cgi reports the miner's algorithm, with no stray paren"
    exit 0
fi
exit 1
