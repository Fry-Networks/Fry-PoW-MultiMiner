#!/bin/sh
# T1 — Bug 1 regression: update.cgi "check" must not report "Network error"
# when GitHub is reachable, and must return a real 7-hex remote version.
# RED on pre-fix code: Fry-Foundation org URL 301s (no -L) -> empty sha -> "Network error".
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

if ! curl -s --connect-timeout 5 -o /dev/null "https://api.github.com" 2>/dev/null; then
    echo "SKIP: GitHub unreachable from this host"
    exit 77
fi

SB=$(mktemp -d)
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" update.cgi "$SETUP" "$SB/update.cgi" --sandbox "$SB" || {
    echo "FAIL: could not extract update.cgi"; exit 1; }

OUT=$(QUERY_STRING=check sh "$SB/update.cgi" 2>/dev/null)
echo "--- update.cgi check output ---"
echo "$OUT"
echo "-------------------------------"

FAIL=0
if echo "$OUT" | grep -q "Network error"; then
    echo "ASSERT FAIL: output contains 'Network error'"; FAIL=1
fi
if echo "$OUT" | grep -q '"remote":"?"'; then
    echo "ASSERT FAIL: remote version is '?'"; FAIL=1
fi
if ! echo "$OUT" | grep -Eq '"remote":"[0-9a-f]{7}"'; then
    echo "ASSERT FAIL: no 7-hex remote version in output"; FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then echo "PASS: update check returns real remote version"; exit 0; fi
exit 1
