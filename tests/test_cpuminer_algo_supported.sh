#!/bin/sh
# Regression: save.cgi must not accept a coin whose algorithm the installed
# cpuminer build cannot mine.
#
# Found by the QA gap-closure run on M2 (FryNetworks4844). Selecting Arionum
# saved cleanly, reported "Configuration saved", start.cgi reported "Mining
# started", and then cpuminer died immediately with:
#     Unknown algo parameter 'argon2d4096'
# The installer's first-choice CPU miner is tpruvot/cpuminer-multi, whose
# --help lists no Argon2 variant at all (only JayDDee/cpuminer-opt has
# argon2d4096). So Arionum is unmineable on a default install, but the UI
# reported success at every step — a silent failure.
#
# RED on pre-fix code: save.cgi returns success and writes output/arionum/start.sh.
# GREEN after fix:     save.cgi refuses with a visible error and writes no start.sh.
#
# The test ships a fake cpuminer whose --help mimics tpruvot's (no argon), so it
# is hermetic and does not depend on what is installed on the box running it.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

SB=$(mktemp -d)
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" save.cgi "$SETUP" "$SB/save.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract save.cgi"; exit 1; }

mkdir -p "$SB/logs" "$SB/pids" "$SB/output" "$SB/bin"

# Fake cpuminer: help text modelled on tpruvot/cpuminer-multi 1.3.7 — supports
# sha256d/scrypt/x11 etc, and deliberately has NO argon2 algorithm.
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
                          yescrypt     Yescrypt
  -o, --url=URL         URL of mining server
HELP
    ;;
esac
exit 0
FAKE
chmod +x "$SB/bin/cpuminer"

# Point the sandboxed save.cgi at the fake binary. The CGI resolves cpuminer at
# /usr/local/bin/cpuminer, so the sandbox rewrites that path.
if grep -q '/usr/local/bin/cpuminer' "$SB/save.cgi"; then
    sed "s|/usr/local/bin/cpuminer|$SB/bin/cpuminer|g" "$SB/save.cgi" > "$SB/save.cgi.new"
    mv "$SB/save.cgi.new" "$SB/save.cgi"
    chmod +x "$SB/save.cgi"
fi

FAIL=0

# ---- 1. UNSUPPORTED algo (arionum -> argon2d4096) must be REFUSED ----
DATA='miner=arionum&wallet=4ZqEbEqbCmshRoFxNPRDPqXHPSQTLBCCA6vaHkP5oXtT&worker=w1&threads=1&pool=aropool.com%3A80&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'
OUT=$(printf '%s' "$DATA" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA} sh "$SB/save.cgi" 2>/dev/null)

if [ -f "$SB/output/arionum/start.sh" ]; then
    echo "ASSERT FAIL: start.sh was generated for an algorithm cpuminer cannot mine"
    echo "  $SB/output/arionum/start.sh"
    FAIL=1
fi
if ! printf '%s' "$OUT" | grep -qi "does not support"; then
    echo "ASSERT FAIL: save.cgi did not report the unsupported algorithm. Output was:"
    printf '%s\n' "$OUT" | head -5
    FAIL=1
fi
if printf '%s' "$OUT" | grep -qi "Configuration saved"; then
    echo "ASSERT FAIL: save.cgi claimed success for an unmineable configuration"
    FAIL=1
fi

# ---- 2. SUPPORTED algos must still save (no over-blocking) ----
rm -rf "$SB/output"; mkdir -p "$SB/output"
DATA2='miner=btc&wallet=1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa&worker=w1&threads=1&pool=pool.example.com%3A3333&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'
OUT2=$(printf '%s' "$DATA2" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA2} sh "$SB/save.cgi" 2>/dev/null)
if [ ! -f "$SB/output/btc/start.sh" ]; then
    echo "ASSERT FAIL: sha256d (supported) was blocked — over-blocking regression. Output:"
    printf '%s\n' "$OUT2" | head -5
    FAIL=1
fi

rm -rf "$SB/output"; mkdir -p "$SB/output"
DATA3='miner=dash&wallet=XyAJmoFDXcXcXcXcXcXcXcXcXcXcXcXcXc&worker=w1&threads=1&pool=pool.example.com%3A9999&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'
OUT3=$(printf '%s' "$DATA3" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA3} sh "$SB/save.cgi" 2>/dev/null)
if [ ! -f "$SB/output/dash/start.sh" ]; then
    echo "ASSERT FAIL: x11 (supported) was blocked — over-blocking regression. Output:"
    printf '%s\n' "$OUT3" | head -5
    FAIL=1
fi

# ---- 3. Non-cpuminer coins must be unaffected by the guard ----
rm -rf "$SB/output"; mkdir -p "$SB/output"
DATA4='miner=xmr&wallet=44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A&worker=w1&threads=1&pool=pool.example.com%3A3333&password=x&cpu_mining=true&gpu_mining=false&usbasic_mining=false'
OUT4=$(printf '%s' "$DATA4" | REQUEST_METHOD=POST CONTENT_LENGTH=${#DATA4} sh "$SB/save.cgi" 2>/dev/null)
if [ ! -f "$SB/output/xmr/start.sh" ]; then
    echo "ASSERT FAIL: xmrig coin was blocked by the cpuminer guard. Output:"
    printf '%s\n' "$OUT4" | head -5
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: unsupported cpuminer algorithms are refused; supported ones still save"
    exit 0
fi
exit 1
