#!/bin/sh
# extract_cgi.sh <name.cgi> <setup_script> <out_file> [--sandbox <dir>]
# Extracts a CGI heredoc body from setup_fryminer_web.sh.
# End marker MUST be whole-line anchored: a naive /SCRIPT/ substring match
# truncates save.cgi at its internal SCRIPT_DIR= lines (historical BUG-001).
set -eu

NAME="$1"
SETUP="$2"
OUT="$3"
SANDBOX=""
if [ "${4:-}" = "--sandbox" ] && [ -n "${5:-}" ]; then
    SANDBOX="$5"
fi

START_MARKER="cat > \"\$BASE/cgi-bin/${NAME}\" <<'SCRIPT'"

# tr -d '\r': a Windows-checkout setup script (CRLF) must still yield a
# valid POSIX sh CGI — the deployed artifact is always LF.
tr -d '\r' < "$SETUP" | awk -v start="$START_MARKER" '
    !on && index($0, start) { on = 1; next }
    on && $0 ~ /^[[:space:]]*SCRIPT[[:space:]]*$/ { found = 1; exit }
    on { print }
    END { if (!found) exit 3 }
' > "$OUT.tmp" || { echo "extract_cgi: heredoc for $NAME not found/terminated in $SETUP" >&2; rm -f "$OUT.tmp"; exit 3; }

if [ ! -s "$OUT.tmp" ]; then
    echo "extract_cgi: extracted $NAME is empty" >&2
    rm -f "$OUT.tmp"
    exit 4
fi

if [ -n "$SANDBOX" ]; then
    sed "s|/opt/frynet-config|$SANDBOX|g" "$OUT.tmp" > "$OUT"
    rm -f "$OUT.tmp"
else
    mv "$OUT.tmp" "$OUT"
fi
chmod +x "$OUT"
