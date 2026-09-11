#!/bin/sh
# Regression: a wallet that already carries the worker as a dotted suffix must not have
# that worker appended a SECOND time.
#
# The UI's worker auto-detect reads the suffix off the wallet (address.worker), mirrors it
# into the worker field and disables the field; a capture-phase submit listener re-enables
# it so it serialises. Nothing anywhere strips the suffix from the wallet, so the form
# posts wallet=ADDR.RIG together with worker=RIG and start.sh builds ADDR.RIG.RIG.
#
# Both listener orderings are broken, which is why this is fixed server-side:
#   re-enable wins  -> worker=RIG      -> ADDR.RIG.RIG
#   re-enable loses -> worker omitted  -> save.cgi's default "worker1" -> ADDR.RIG.worker1
#
# Fixed in save.cgi rather than in the JS because save.cgi also accepts a plain GET
# (POST_DATA="$QUERY_STRING"), so any browser-side fix is bypassable, and because a
# config.txt that is already poisoned re-poisons itself on every reload via loadConfig().
#
# RED on pre-fix code: the standard path yields ADDR.RIG.RIG.
# GREEN after:         it yields ADDR.RIG exactly once, and a DIFFERENT manually-typed
#                      worker is still appended normally.
#
# Hermetic. Set FRYPOW_SETUP to point at an alternate installer (used to prove RED against
# a pre-fix backup).
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_SETUP:-$DIR/../setup_fryminer_web.sh}"
[ -f "$SETUP" ] || { echo "SKIP: installer not found at $SETUP"; exit 77; }

command -v python3 >/dev/null 2>&1 || { echo "SKIP: python3 needed for save.cgi field decode"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output"

ADDR='44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A'
RIG='381462'

FAIL=0

# gen <coin> <query-string>
# Pre-seeds config.txt with the SAME coin being saved so save.cgi's coin-change branch
# stays dormant -- that branch greps ps and sends kill -TERM/-KILL to REAL host pids, which
# extract_cgi.sh does not sandbox. Without this a run on a mining host could kill the miner.
gen() {
    _coin="$1"; _q="$2"
    rm -rf "$SB/output"; mkdir -p "$SB/output"
    printf 'miner=%s\n' "$_coin" > "$SB/config.txt"
    QUERY_STRING="$_q" REQUEST_METHOD=GET sh "$SB/save.cgi" > "$SB/out.html" 2>/dev/null
    [ -f "$SB/output/$_coin/start.sh" ] || {
        echo "ASSERT FAIL: no start.sh generated for $_coin. save.cgi said:"
        sed 's/^/    /' "$SB/out.html" | head -4
        return 1; }
    return 0
}

# The runtime sites live in QUOTED heredocs, so start.sh holds identical literal text on
# red and green trees -- a grep would be a tautology. Execute the prologue instead, cut at
# the marker that is unique in the source and precedes any miner launch or loop.
wallet_string() {
    awk '/^# Remove clean stop marker$/{exit} {print}' "$SB/output/$1/start.sh" > "$SB/prologue.sh"
    sh -c '. "$1"; printf "%s" "$USER_WALLET_STRING"' sh "$SB/prologue.sh" 2>/dev/null
}

check() {
    _label="$1"; _coin="$2"; _want="$3"
    _got=$(wallet_string "$_coin")
    if [ "$_got" != "$_want" ]; then
        echo "ASSERT FAIL [$_label]"
        echo "    want: [$_want]"
        echo "    got : [$_got]"
        FAIL=1
    fi
}

# ---- 1. THE BUG: wallet carries the suffix AND worker repeats it -------------
if gen xmr "miner=xmr&wallet=$ADDR.$RIG&worker=$RIG&pool=pool.example.com%3A3333"; then
    check "suffix+matching worker" xmr "$ADDR.$RIG"
else FAIL=1; fi

# ---- 2. A DIFFERENT worker must still be appended ----------------------------
#         Guards against over-correcting into "never append when the wallet has a dot".
if gen xmr "miner=xmr&wallet=$ADDR.$RIG&worker=rig9&pool=pool.example.com%3A3333"; then
    check "suffix+different worker" xmr "$ADDR.$RIG.rig9"
else FAIL=1; fi

# ---- 3. Plain wallet + worker is unaffected ----------------------------------
if gen xmr "miner=xmr&wallet=$ADDR&worker=rig9&pool=pool.example.com%3A3333"; then
    check "no suffix+worker" xmr "$ADDR.rig9"
else FAIL=1; fi

# ---- 4. Empty worker still yields no trailing dot (prior fix, guard it) ------
if gen xmr "miner=xmr&wallet=$ADDR&worker=&pool=pool.example.com%3A3333"; then
    check "no suffix+empty worker" xmr "$ADDR"
else FAIL=1; fi

# ---- 5. The default worker must not be appended to a wallet already ending in it
#         (the "re-enable loses the race" path: worker omitted entirely)
if gen xmr "miner=xmr&wallet=$ADDR.worker1&pool=pool.example.com%3A3333"; then
    check "suffix matches default worker" xmr "$ADDR.worker1"
else FAIL=1; fi

# ---- 6. config.txt must record the de-duplicated worker ----------------------
if gen xmr "miner=xmr&wallet=$ADDR.$RIG&worker=$RIG&pool=pool.example.com%3A3333"; then
    _w=$(grep '^worker=' "$SB/config.txt" | cut -d= -f2-)
    if [ -n "$_w" ]; then
        echo "ASSERT FAIL [config.txt dedup]: worker should be empty after dedup, got [$_w]"
        FAIL=1
    fi
else FAIL=1; fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: a worker already present as a wallet suffix is never appended twice"
    exit 0
fi
exit 1
