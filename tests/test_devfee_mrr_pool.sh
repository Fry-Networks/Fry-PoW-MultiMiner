#!/bin/sh
# Regression: the dev-fee slice must not mine a raw coin address at a
# MiningRigRentals assigned port.
#
# MRR authenticates only `username.rigid`. The dev-fee slice mined the coin's
# raw dev address (for verus: RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt) at the USER'S
# pool, so on an MRR port it failed auth and spent the entire 60-second dev
# minute in a client.reconnect storm before the user slice restarted. Live
# measurement across the fleet: 40-70 reconnects per dev slice, and M2 sat at
# 597 reconnects / 65% efficiency from this alone. The one non-MRR host had 0.
#
# Two behaviours are asserted:
#
#   DEV_POOL      - on an MRR pool the dev slice goes to the coin's PUBLIC pool
#                   instead of the user's MRR port. On any other pool DEV_POOL
#                   must equal POOL, so existing setups are bit-for-bit
#                   unchanged.
#   DEV_FEE_SKIP  - when the user's wallet already IS the dev destination
#                   (the coin's dev wallet, or Fry Networks' MRR rig) the cycle
#                   is skipped entirely. Paying a dev fee from a wallet to
#                   itself gains nothing and costs a miner teardown every 50
#                   minutes.
#
# MECHANICS. Both values live in QUOTED heredocs, so they are written literally
# into output/<coin>/start.sh and only resolve when dash runs it -- a static
# grep of start.sh cannot distinguish red from green. This test therefore
# EXECUTES the generated code: the prologue (assignments, cut at the unique
# "# Remove clean stop marker" line) plus the emitted DEV_POOL case block,
# which sits further down inside the cycling loop and so must be extracted
# separately.
#
# Hermetic: pre-seeds config.txt with the coin being saved so save.cgi's
# "coin changed -> stop the running miner" branch stays dormant. That branch
# greps `ps` and sends kill -TERM/-KILL to REAL host pids; extract_cgi.sh
# sandboxes /opt/frynet-config paths but NOT ps or kill.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
# FRYPOW_SETUP lets the same assertions run against an alternate installer --
# a pre-fix backup, to demonstrate RED.
SETUP="${FRYPOW_SETUP:-$DIR/../setup_fryminer_web.sh}"
[ -f "$SETUP" ] || { echo "SKIP: installer not found at $SETUP"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output"

MRR_POOL='us-central01.miningrigrentals.com:50912'
PUBLIC_POOL='pool.verus.io:9999'
DEV_VRSC='RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt'
# Stands in for the operator's nominated skip wallet. The real value is an account
# identifier and is deliberately absent from this repo; save.cgi reads it from
# /opt/frynet-config/devfee-skip-wallet, which extract_cgi.sh --sandbox rewrites to
# $SB, so the file mechanism is exercised here exactly as it works on a rig.
SKIP_WALLET='testrig.000000'
NOMINATED="$SB/devfee-skip-wallet"
USER_VRSC='RUserWalletExampleAddr0000000000000'

FAIL=0

# gen <coin> <query-string> -- save.cgi reads QUERY_STRING unless REQUEST_METHOD=POST
gen() {
    _coin="$1"; _q="$2"
    rm -rf "$SB/output"; mkdir -p "$SB/output"
    printf 'miner=%s\n' "$_coin" > "$SB/config.txt"
    QUERY_STRING="$_q" REQUEST_METHOD=GET sh "$SB/save.cgi" > "$SB/last_out.html" 2>/dev/null
    if [ ! -f "$SB/output/$_coin/start.sh" ]; then
        echo "ASSERT FAIL: no start.sh generated for $_coin. save.cgi said:"
        sed 's/^/    /' "$SB/last_out.html" | head -5
        return 1
    fi
    return 0
}

# resolve <coin> -> "DEV_POOL|DEV_FEE_SKIP" as the generated script computes them.
# The prologue holds POOL / DEV_POOL_PUBLIC / DEV_FEE_SKIP; the DEV_POOL case
# block lives inside the cycling loop, so it is spliced on separately. Neither
# piece launches a miner, loops, or probes the network.
resolve() {
    _sh="$SB/output/$1/start.sh"
    awk '/^# Remove clean stop marker$/{exit} {print}' "$_sh" > "$SB/frag.sh"
    awk '/MiningRigRentals assigned ports authenticate/{on=1}
         on{print}
         on && /^    esac$/{exit}' "$_sh" >> "$SB/frag.sh"
    sh -c '. "$1"; printf "%s|%s" "${DEV_POOL-}" "${DEV_FEE_SKIP-}"' sh "$SB/frag.sh" 2>/dev/null
}

check() {
    _label="$1"; _coin="$2"; _want_pool="$3"; _want_skip="$4"
    _got=$(resolve "$_coin")
    _want="$_want_pool|$_want_skip"
    if [ "$_got" != "$_want" ]; then
        echo "ASSERT FAIL [$_label]"
        echo "    want DEV_POOL|DEV_FEE_SKIP: [$_want]"
        echo "    got                       : [$_got]"
        FAIL=1
    else
        echo "  ok  $_label"
    fi
}

Q_BASE='miner=verus&threads=2&password=x&worker='

# 1. MRR pool, ordinary user wallet -> dev slice must move to the public pool,
#    and the dev fee must still be taken.
gen verus "$Q_BASE&wallet=$USER_VRSC&pool=$MRR_POOL" \
    && check "MRR pool + user wallet -> public dev pool" verus "$PUBLIC_POOL" "false"

# 2. MRR pool, wallet IS the operator's nominated wallet -> no dev cycle at all.
printf '%s\n' "$SKIP_WALLET" > "$NOMINATED"
gen verus "$Q_BASE&wallet=$SKIP_WALLET&pool=$MRR_POOL" \
    && check "MRR pool + nominated wallet -> skip cycling" verus "$PUBLIC_POOL" "true"

# 2b. Same wallet, but nothing nominated - the public default. Nothing beyond the
#     coin's own dev wallet may skip, and an unset value must not match everything.
rm -f "$NOMINATED"
gen verus "$Q_BASE&wallet=$SKIP_WALLET&pool=$MRR_POOL" \
    && check "no nominated wallet -> nothing extra skips" verus "$PUBLIC_POOL" "false"

# 3. Non-MRR pool -> DEV_POOL must equal POOL. This is the no-regression case:
#    every existing non-MRR configuration must behave exactly as before.
gen verus "$Q_BASE&wallet=$USER_VRSC&pool=$PUBLIC_POOL" \
    && check "non-MRR pool -> DEV_POOL == POOL" verus "$PUBLIC_POOL" "false"

# 4. User already mining to the coin's dev wallet -> no dev cycle.
gen verus "$Q_BASE&wallet=$DEV_VRSC&pool=$PUBLIC_POOL" \
    && check "wallet == dev wallet -> skip cycling" verus "$PUBLIC_POOL" "true"

# 5. The dev launchers must reference DEV_POOL, not the user's POOL. Statically
#    checkable because these lines are baked at generation time.
if ! grep -q 'stratum+tcp://\$DEV_POOL -u \$DEV_WALLET\.frydev' "$SB/output/verus/start.sh"; then
    echo "ASSERT FAIL: verus dev launcher still mines \$DEV_WALLET at the user's \$POOL"
    grep -n 'DEV_WALLET\.frydev' "$SB/output/verus/start.sh" | sed 's/^/    /' | head -3
    FAIL=1
else
    echo "  ok  verus dev launcher uses \$DEV_POOL"
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: dev-fee slice is MRR-aware and self-payment is skipped"
    exit 0
fi
echo "FAIL: dev-fee MRR handling is wrong"
exit 1
