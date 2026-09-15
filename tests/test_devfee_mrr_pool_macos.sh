#!/bin/sh
# Regression: the macOS installer's dev-fee slice must not mine a raw coin
# address at a MiningRigRentals assigned port.
#
# Same defect as the Linux twin (fixed in tests/test_devfee_mrr_pool.sh): the
# dev slice mined the coin's raw dev address at the USER'S pool, and MRR
# assigned ports authenticate only `username.rigid`, so a raw address fails auth
# and the whole 60-second dev minute becomes a client.reconnect storm. Measured
# on the Linux fleet at 40-70 reconnects per dev slice, with one host at 597
# reconnects / 65% efficiency until it was fixed.
#
# WHY THIS TEST IS SHAPED DIFFERENTLY FROM THE LINUX ONE.
# The macOS installer has no select_pool() and its dev launchers expand $POOL
# UNESCAPED, i.e. at generation time (line ~2936 already does exactly that with
# $DEV_SCALA_POOL). So the MRR decision is made at generation time and baked
# into the launcher lines, rather than resolved at runtime inside start.sh.
# There is therefore no runtime prologue to source, the way the Linux test does.
# Also, extract_cgi.sh's --sandbox rewrite targets /opt/frynet-config, which
# macOS does not use (it uses $HOME/.fryminer), so running its save.cgi here
# would write into the real home directory. Following the house style set by
# tests/test_macos_worker_suffix.sh, the installer is checked statically —
# but the two decision blocks are additionally EXTRACTED AND EXECUTED with
# controlled inputs, so the shipped logic itself is exercised rather than just
# pattern-matched.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_MACOS_SETUP:-$DIR/../setup_fryminer_macos.sh}"
[ -f "$SETUP" ] || { echo "SKIP: macOS installer not found at $SETUP"; exit 77; }

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

MRR='us-central01.miningrigrentals.com:50912'
PUBLIC='pool.verus.io:9999'
DEV_VRSC='RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt'
# Stands in for the operator's nominated skip wallet. The real value is an account
# identifier supplied via FRY_DEV_FEE_SKIP_WALLET or the installer's config dir, and
# is deliberately absent from this repo.
SKIP_WALLET='testrig.000000'
FAIL=0

# --- 1. no dev launcher may still mine at the user's pool --------------------
STRAY=$(grep -n 'DEV_WALLET\.frydev' "$SETUP" | grep -F '$POOL' | grep -vF '$DEV_POOL')
if [ -n "$STRAY" ]; then
    echo "ASSERT FAIL: dev launcher(s) still mine \$DEV_WALLET at the user's \$POOL"
    echo "$STRAY" | cut -c1-120 | sed 's/^/    /'
    FAIL=1
else
    echo "  ok  every dev launcher uses \$DEV_POOL"
fi

# --- 2. the MRR decision block exists ----------------------------------------
if ! grep -q 'miningrigrentals\.com\*' "$SETUP"; then
    echo "ASSERT FAIL: no MiningRigRentals detection in the macOS installer"
    FAIL=1
else
    echo "  ok  MRR detection present"
fi

# --- 3. EXECUTE the emitted DEV_POOL decision with controlled inputs ---------
# Pull the generation-time case block out of the installer and run it standalone.
awk '/^# The dev slice cannot authenticate a raw address at an MRR assigned port/{on=1}
     on{print}
     on && /^esac$/{exit}' "$SETUP" > "$SB/devpool.sh"

if [ ! -s "$SB/devpool.sh" ]; then
    echo "ASSERT FAIL: could not extract the DEV_POOL decision block"
    FAIL=1
else
    got_mrr=$(POOL="$MRR" DEV_POOL_PUBLIC_FOR_COIN="$PUBLIC" \
        sh -c '. "$1"; printf "%s" "$DEV_POOL"' sh "$SB/devpool.sh" 2>/dev/null)
    got_pub=$(POOL="$PUBLIC" DEV_POOL_PUBLIC_FOR_COIN="$PUBLIC" \
        sh -c '. "$1"; printf "%s" "$DEV_POOL"' sh "$SB/devpool.sh" 2>/dev/null)
    got_empty=$(POOL="$MRR" DEV_POOL_PUBLIC_FOR_COIN="" \
        sh -c '. "$1"; printf "%s" "$DEV_POOL"' sh "$SB/devpool.sh" 2>/dev/null)

    [ "$got_mrr" = "$PUBLIC" ] \
        && echo "  ok  MRR pool -> dev slice uses the coin's public pool" \
        || { echo "ASSERT FAIL: MRR pool -> DEV_POOL want [$PUBLIC] got [$got_mrr]"; FAIL=1; }
    [ "$got_pub" = "$PUBLIC" ] \
        && echo "  ok  non-MRR pool -> DEV_POOL == POOL (existing behaviour preserved)" \
        || { echo "ASSERT FAIL: non-MRR -> DEV_POOL want [$PUBLIC] got [$got_pub]"; FAIL=1; }
    [ "$got_empty" = "$MRR" ] \
        && echo "  ok  unknown coin (no public default) degrades to \$POOL" \
        || { echo "ASSERT FAIL: empty public pool -> want [$MRR] got [$got_empty]"; FAIL=1; }
fi

# --- 4. EXECUTE the skip-guard decision --------------------------------------
# The block now contains TWO `fi`s - the first closes the config-file lookup, the
# second closes the guard itself - so stop at the second or the guard is silently
# truncated away and every assertion below passes against nothing.
awk '/^# Skip the dev-fee cycle when the user is already mining to the dev/{on=1}
     on{print}
     on && /^fi$/{ if (++n == 2) exit }' "$SETUP" > "$SB/skip.sh"

if ! grep -q 'DEV_FEE_SKIP_FOR_HOST="true"' "$SB/skip.sh" 2>/dev/null; then
    echo "ASSERT FAIL: could not extract the DEV_FEE_SKIP decision block"
    FAIL=1
else
    run_skip() {  # run_skip <wallet> [nominated-wallet]
        WALLET="$1" DEV_WALLET_FOR_COIN="$DEV_VRSC" FRY_DEV_FEE_SKIP_WALLET="${2-}" \
            sh -c '. "$1"; printf "%s" "$DEV_FEE_SKIP_FOR_HOST"' sh "$SB/skip.sh" 2>/dev/null
    }

    skip_dev=$(run_skip "$DEV_VRSC" "$SKIP_WALLET")
    skip_rig=$(run_skip "$SKIP_WALLET" "$SKIP_WALLET")
    skip_usr=$(run_skip "RUserWalletExample000" "$SKIP_WALLET")
    # No nominated wallet: the dev wallet must still skip, but nothing else may.
    skip_dev_bare=$(run_skip "$DEV_VRSC")
    skip_rig_bare=$(run_skip "$SKIP_WALLET")
    skip_empty=$(run_skip "")

    [ "$skip_dev" = "true" ] \
        && echo "  ok  wallet == dev wallet -> cycling skipped" \
        || { echo "ASSERT FAIL: dev wallet should skip, got [$skip_dev]"; FAIL=1; }
    [ "$skip_rig" = "true" ] \
        && echo "  ok  wallet == nominated wallet -> cycling skipped" \
        || { echo "ASSERT FAIL: nominated wallet should skip, got [$skip_rig]"; FAIL=1; }
    [ "$skip_usr" = "false" ] \
        && echo "  ok  ordinary wallet still pays the dev fee" \
        || { echo "ASSERT FAIL: ordinary wallet should not skip, got [$skip_usr]"; FAIL=1; }
    [ "$skip_dev_bare" = "true" ] \
        && echo "  ok  no nominated wallet -> dev wallet still skips" \
        || { echo "ASSERT FAIL: dev wallet should skip unconfigured, got [$skip_dev_bare]"; FAIL=1; }
    [ "$skip_rig_bare" = "false" ] \
        && echo "  ok  no nominated wallet -> nothing extra skips" \
        || { echo "ASSERT FAIL: unconfigured must not skip, got [$skip_rig_bare]"; FAIL=1; }
    [ "$skip_empty" = "false" ] \
        && echo "  ok  an empty wallet never skips" \
        || { echo "ASSERT FAIL: empty wallet should not skip, got [$skip_empty]"; FAIL=1; }
fi

# --- 5. the installer must still parse ---------------------------------------
if ! sh -n "$SETUP" 2>/dev/null; then
    echo "ASSERT FAIL: setup_fryminer_macos.sh no longer parses"
    sh -n "$SETUP" 2>&1 | head -3 | sed 's/^/    /'
    FAIL=1
else
    echo "  ok  installer parses"
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: macOS dev-fee slice is MRR-aware and self-payment is skipped"
    exit 0
fi
echo "FAIL: macOS dev-fee MRR handling is wrong"
exit 1
