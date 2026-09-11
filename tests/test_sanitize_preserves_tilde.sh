#!/bin/sh
# Regression: sanitize_shell / sanitize_url must not strip "~".
#
# The tr -d set ended with the fragment '#~', so every sanitized field lost its tilde. The
# visible damage is the ORE keypair: index.html ships "~/.config/solana/id.json" as both
# the placeholder AND the default value, and sanitizing turned it into
# "/.config/solana/id.json" -- an absolute path at the filesystem root that cannot exist.
#
# Preserving "~" is safe at every one of the 15 call sites. A tilde cannot terminate a
# quote, start a command, or introduce a substitution; tilde EXPANSION only applies to an
# unquoted word whose first character is "~", and every sanitized value lands either in
# plain-text config.txt or inside double quotes in the generated start.sh, where expansion
# is suppressed outright.
#
# That suppression is also why un-stripping alone is not enough: start.sh emits
# --keypair "$ORE_KEYPAIR_PATH" inside double quotes, so the shell would hand ore-cli a
# literal "~/..." and it would fail to open the file. save.cgi therefore expands the tilde
# itself, where $HOME is known, before baking the path in.
#
# RED on pre-fix code: config.txt records /.config/solana/id.json
# GREEN after:         config.txt records ~/.config/solana/id.json
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
FAIL=0

ADDR='44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A'
KEYPATH='~/.config/solana/id.json'

# Same-coin pre-seed keeps save.cgi's coin-change branch (which greps ps and sends
# kill -KILL to real host pids) dormant.
printf 'miner=xmr\n' > "$SB/config.txt"
Q="miner=xmr&wallet=$ADDR&worker=&pool=pool.example.com%3A3333&ore_keypair=%7E%2F.config%2Fsolana%2Fid.json"
QUERY_STRING="$Q" REQUEST_METHOD=GET sh "$SB/save.cgi" > "$SB/out.html" 2>/dev/null

GOT=$(grep '^ore_keypair=' "$SB/config.txt" 2>/dev/null | cut -d= -f2-)

# ---- 1. the tilde must survive sanitization ----------------------------------
if [ "$GOT" != "$KEYPATH" ]; then
    echo "ASSERT FAIL [tilde preserved]"
    echo "    want: [$KEYPATH]"
    echo "    got : [$GOT]"
    case "$GOT" in
        /.config/*) echo "    (leading ~ was stripped -- the sanitize_shell tr -d set still contains it)" ;;
    esac
    FAIL=1
fi

# ---- 2. genuinely dangerous characters must STILL be stripped ----------------
#         Guards against over-correcting into "sanitize nothing".
printf 'miner=xmr\n' > "$SB/config.txt"
EVIL='pool.example.com%3A3333%3Btouch%20%2Ftmp%2Fpwned'   # "pool;touch /tmp/pwned"
Q2="miner=xmr&wallet=$ADDR&worker=&pool=$EVIL"
QUERY_STRING="$Q2" REQUEST_METHOD=GET sh "$SB/save.cgi" > "$SB/out2.html" 2>/dev/null
POOL=$(grep '^pool=' "$SB/config.txt" 2>/dev/null | cut -d= -f2-)
case "$POOL" in
    *';'*) echo "ASSERT FAIL [metachar]: ';' survived sanitization in pool=[$POOL]"; FAIL=1 ;;
esac
case "$POOL" in
    *'`'*) echo "ASSERT FAIL [metachar]: backtick survived in pool=[$POOL]"; FAIL=1 ;;
esac

# ---- 3. the shipped default must round-trip unchanged ------------------------
#         This is the exact string index.html puts in the field by default.
if [ "$GOT" = "/.config/solana/id.json" ]; then
    echo "ASSERT FAIL [ore default]: the shipped default resolved to a root-absolute path"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: sanitize preserves ~ while still stripping shell metacharacters"
    exit 0
fi
exit 1
