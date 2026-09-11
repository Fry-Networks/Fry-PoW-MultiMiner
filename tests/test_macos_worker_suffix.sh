#!/bin/sh
# Regression: setup_fryminer_macos.sh must not append a bare ".$WORKER".
#
# The macOS installer carries the identical defect the Linux one had: an empty worker
# produces a stratum username with a trailing dot. Same 7+1 structure -- 7 sites inside
# QUOTED heredocs (evaluated at runtime in the generated start script) and 1 inside an
# UNQUOTED heredoc (baked when save.cgi writes the file).
#
# WORKER is always SET but may be EMPTY (it defaults to "worker1" and is then overwritten
# unconditionally by the form parse loop), so ${WORKER:+.$WORKER} is required and
# ${WORKER+.$WORKER} would be a silent no-op.
#
# SCOPE NOTE -- why this is a source assertion rather than an executing one:
# the sibling Linux tests run the generated prologue, which is strictly better. That is
# not portable here: the macOS installer targets $HOME/.fryminer rather than
# /opt/frynet-config, so tests/lib/extract_cgi.sh's --sandbox path rewrite does not apply,
# and the generated scripts are #!/bin/bash with launchd assumptions. The functional
# correctness of the ${WORKER:+...} idiom is already proven by
# test_worker_suffix_no_trailing_dot.sh against the Linux script; what this test pins is
# that the macOS script uses that idiom at every site and never regresses to the bare form.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="${FRYPOW_MACOS_SETUP:-$DIR/../setup_fryminer_macos.sh}"
[ -f "$SETUP" ] || { echo "SKIP: macOS installer not found at $SETUP"; exit 77; }

FAIL=0

# ---- 1. no bare ".$WORKER" appends may remain --------------------------------
#         [^}] excludes the fixed ${WORKER:+.$WORKER} form, whose ".$WORKER" is
#         followed by "}". Lines beginning with a *" glob are case PATTERNS, not
#         appends, and are not flagged.
BARE=$(grep -nE '\.\$WORKER([^}]|$)' "$SETUP" | grep -vE '^[0-9]+:[[:space:]]*\*"')
if [ -n "$BARE" ]; then
    echo "ASSERT FAIL: macOS installer still appends a bare .\$WORKER:"
    printf '%s\n' "$BARE" | sed 's/^/    /'
    FAIL=1
fi

# ---- 2. the fixed form must be present at every site --------------------------
#         8 known sites: 7 runtime (3x SOLOPOOL_MERGED_LTC, 3x SOLOPOOL_MERGED_DOGE,
#         1x NORMAL_WALLET) + 1 generation-time (Unmineable xmrig line).
FIXED=$(grep -cE '\$\{WORKER:\+\.\$WORKER\}' "$SETUP")
if [ "$FIXED" -lt 8 ]; then
    echo "ASSERT FAIL: expected at least 8 \${WORKER:+.\$WORKER} sites, found $FIXED"
    echo "    (7 runtime wallet-string sites + 1 generation-time Unmineable xmrig line)"
    FAIL=1
fi

# ---- 3. ${WORKER+...} (no colon) must never be used ---------------------------
#         It tests only for UNSET. WORKER is always set here, so it would not fire in
#         the production case and the bug would survive the "fix".
if grep -qE '\$\{WORKER\+' "$SETUP"; then
    echo "ASSERT FAIL: found \${WORKER+...} -- must be \${WORKER:+...} (WORKER is set-but-empty)"
    grep -nE '\$\{WORKER\+' "$SETUP" | sed 's/^/    /'
    FAIL=1
fi

# ---- 4. the literal dev-fee suffix must be left alone --------------------------
#         DEV_WALLET_STRING="$DEV_WALLET.frydev" is a fixed suffix, not a worker.
if ! grep -q 'DEV_WALLET_STRING="\$DEV_WALLET\.frydev"' "$SETUP"; then
    echo "ASSERT FAIL: the literal .frydev dev-wallet suffix was altered; it must not be"
    FAIL=1
fi

# ---- 5. the script must still parse -------------------------------------------
if ! sh -n "$SETUP" 2>/dev/null; then
    echo "ASSERT FAIL: macOS installer no longer parses (sh -n)"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: macOS installer uses \${WORKER:+.\$WORKER} at every site, no bare appends"
    exit 0
fi
exit 1
