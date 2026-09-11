#!/bin/sh
# Regression: an EMPTY worker must never leave a trailing dot on the stratum
# username. save.cgi appended the worker suffix unconditionally, so a config
# with worker= (empty) generated output/<coin>/start.sh that mined against
#     -u <account>.<rigid>.     <- trailing dot
#     -u 44AFF...GQBEP3A.       <- trailing dot
# Pools reject or mis-credit that username. Live incident: miner M1 mined ~15h
# against a malformed wallet while being reported as "strict-verified".
#
# WORKER is always SET (default "worker1" at the top of save.cgi) but is
# overwritten unconditionally by the parse loop, including with "". So the fix
# must use ${WORKER:+.$WORKER} -- ${WORKER+...} tests only for UNSET and would
# still emit the bare dot in exactly the production case.
#
# Two distinct mechanics are covered, because they fail in different places:
#
#   (a) RUNTIME  - 7 sites build USER_WALLET_STRING inside QUOTED heredocs
#                  (NORMAL_WALLET, SOLOPOOL_MERGED_LTC, SOLOPOOL_MERGED_DOGE).
#                  The text is written LITERALLY into start.sh, so a static
#                  grep of start.sh cannot see the trailing dot on either red
#                  or green code -- it only appears when dash runs the script.
#                  This test therefore EXECUTES the generated prologue.
#
#   (b) GENERATE - the Unmineable xmrig line is inside an UNQUOTED heredoc and
#                  bakes ".$WORKER" at save time, bypassing USER_WALLET_STRING
#                  entirely. That one IS statically visible.
#
# Hermetic: ships a fake cpuminer so the scrypt merged-mining coins are not
# gated on what is installed on the host, and pre-seeds config.txt with the
# coin being saved so save.cgi's "coin changed -> stop the running miner"
# block never runs. That block greps `ps` and sends kill -TERM/-KILL to REAL
# host pids -- extract_cgi.sh sandboxes /opt/frynet-config paths but NOT ps or
# kill. Without the seed this test could SIGKILL a live miner.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

SB=$(mktemp -d) || { echo "FAIL: mktemp -d failed"; exit 1; }
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output" "$SB/bin"

# Fake cpuminer advertising scrypt/sha256d/x11 so the algorithm capability
# guard passes deterministically for the ltc-lotto / doge-lotto (scrypt) cases.
cat > "$SB/bin/cpuminer" <<'FAKE'
#!/bin/sh
case "${1:-}" in
--help)
cat <<'HELP'
Usage: cpuminer [OPTIONS]
Options:
  -a, --algo=ALGO       specify the algorithm to use
                          blake2s      Blake-2 S
                          decred       Decred
                          scrypt       Scrypt
                          sha256d      SHA-256d
                          x11          X11
  -o, --url=URL         URL of mining server
HELP
    ;;
esac
exit 0
FAKE
chmod +x "$SB/bin/cpuminer"

if grep -q '/usr/local/bin/cpuminer' "$SB/save.cgi"; then
    sed "s|/usr/local/bin/cpuminer|$SB/bin/cpuminer|g" "$SB/save.cgi" > "$SB/save.cgi.new"
    mv "$SB/save.cgi.new" "$SB/save.cgi"
    chmod +x "$SB/save.cgi"
fi

XMR='44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A'
LTC='ltc1qexampleuseraddr00000000000000000000'
DOGE='DExampleUserDogeAddr000000000000000'
DEVLTC='ltc1qrdc0wqzs3cwuhxxzkq2khepec2l3c6uhd8l9jy'

FAIL=0

# gen <coin> <query-string>
# save.cgi takes QUERY_STRING when REQUEST_METHOD is not POST.
gen() {
    _coin="$1"; _q="$2"
    rm -rf "$SB/output"; mkdir -p "$SB/output"
    # Same coin as the one being saved => the stop-the-miner block stays dormant.
    printf 'miner=%s\n' "$_coin" > "$SB/config.txt"
    QUERY_STRING="$_q" REQUEST_METHOD=GET sh "$SB/save.cgi" > "$SB/last_out.html" 2>/dev/null
    if [ ! -f "$SB/output/$_coin/start.sh" ]; then
        echo "ASSERT FAIL: no start.sh generated for $_coin. save.cgi said:"
        sed 's/^/    /' "$SB/last_out.html" | head -5
        return 1
    fi
    return 0
}

# wallet_string <coin> -> the value start.sh would actually compute at runtime.
# Cut at "# Remove clean stop marker": that string is unique in the source and
# is the first line appended after the wallet-string block. Everything above it
# is assignments plus the wallet case -- no miner launch, no loop, no probe.
wallet_string() {
    awk '/^# Remove clean stop marker$/{exit} {print}' \
        "$SB/output/$1/start.sh" > "$SB/prologue.sh"
    sh -c '. "$1"; printf "%s" "$USER_WALLET_STRING"' sh "$SB/prologue.sh" 2>/dev/null
}

check_ws() {
    _label="$1"; _coin="$2"; _want="$3"
    _got=$(wallet_string "$_coin")
    if [ "$_got" != "$_want" ]; then
        echo "ASSERT FAIL [$_label]: wrong USER_WALLET_STRING"
        echo "    want: [$_want]"
        echo "    got : [$_got]"
        FAIL=1
        return
    fi
    case "$_got" in
        *.) echo "ASSERT FAIL [$_label]: stratum username ends in a trailing dot: [$_got]"
            FAIL=1 ;;
    esac
}

# ---- 1. Standard path (NORMAL_WALLET), EMPTY worker -- the production incident
if gen xmr "miner=xmr&wallet=$XMR&worker=&pool=pool.example.com%3A3333"; then
    check_ws "standard/empty worker" xmr "$XMR"
else FAIL=1; fi

# ---- 2. Standard path, SET worker: must still be ADDRESS.WORKER --------------
#         Guards against over-correcting the fix into "never append".
if gen xmr "miner=xmr&wallet=$XMR&worker=rig7&pool=pool.example.com%3A3333"; then
    check_ws "standard/set worker" xmr "$XMR.rig7"
else FAIL=1; fi

# ---- 3. LTC merged mining on solopool, user DOGE addr -----------------------
if gen ltc-lotto "miner=ltc-lotto&wallet=$LTC&doge_wallet=$DOGE&worker=&pool=eu3.solopool.org%3A8003"; then
    check_ws "ltc merged/empty worker" ltc-lotto "$LTC, $DOGE"
else FAIL=1; fi
if gen ltc-lotto "miner=ltc-lotto&wallet=$LTC&doge_wallet=$DOGE&worker=rig7&pool=eu3.solopool.org%3A8003"; then
    check_ws "ltc merged/set worker" ltc-lotto "$LTC, $DOGE.rig7"
else FAIL=1; fi

# ---- 4. DOGE merged mining on solopool, NO ltc addr -> dev addr -------------
if gen doge-lotto "miner=doge-lotto&wallet=$DOGE&worker=&pool=eu3.solopool.org%3A8003"; then
    check_ws "doge merged/dev ltc/empty worker" doge-lotto "$DOGE, $DEVLTC"
else FAIL=1; fi

# ---- 5. DOGE merged mining on solopool, user LTC addr ----------------------
if gen doge-lotto "miner=doge-lotto&wallet=$DOGE&ltc_wallet=$LTC&worker=&pool=eu3.solopool.org%3A8003"; then
    check_ws "doge merged/user ltc/empty worker" doge-lotto "$DOGE, $LTC"
else FAIL=1; fi

# ---- 6. Non-solopool fallback branches -------------------------------------
#         The doge-lotto one is the site most easily missed: it is the twin of
#         the ltc-lotto branch but lives in a different heredoc.
if gen ltc-lotto "miner=ltc-lotto&wallet=$LTC&worker=&pool=notsolo.example.com%3A3333"; then
    check_ws "ltc non-solopool/empty worker" ltc-lotto "$LTC"
else FAIL=1; fi
if gen doge-lotto "miner=doge-lotto&wallet=$DOGE&worker=&pool=notsolo.example.com%3A3333"; then
    check_ws "doge non-solopool/empty worker" doge-lotto "$DOGE"
else FAIL=1; fi

# ---- 7. Source guard: no bare ".$WORKER" append may be reintroduced ---------
#         The [^}] class deliberately excludes the fixed form
#         ${WORKER:+.$WORKER}, whose ".$WORKER" is followed by "}".
if grep -qE '\.\$WORKER([^}]|$)' "$SETUP"; then
    echo "ASSERT FAIL: setup script still appends a bare .\$WORKER:"
    grep -nE '\.\$WORKER([^}]|$)' "$SETUP" | sed 's/^/    /'
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: empty worker never produces a trailing dot; set worker still yields ADDRESS.WORKER"
    exit 0
fi
exit 1
