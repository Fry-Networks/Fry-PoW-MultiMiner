#!/bin/sh
# T1 — Bug 1 regression: update.cgi "check" must not report "Network error"
# when GitHub is reachable, and must return a real 7-hex remote version.
# Originally RED because the org URL 301'd and, with no -L, yielded an empty sha.
#
# T2 — every GitHub URL shipped in the README and the installers must actually
# resolve. v1.0.2 shipped install instructions AND installer self-update endpoints
# pointing at an org that returns 404. T1 could not catch it: T1 only exercises
# setup_fryminer_web.sh, which was the one file already pointing at the right org.
# T2 checks all of them, so a stale org reference fails the suite instead of
# shipping.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
# Overridable so the reachability check can be run against another checkout -
# that is how RED is demonstrated on a pre-fix tree. Same idiom as
# tests/test_devfee_mrr_pool.sh's FRYPOW_SETUP.
ROOT="${FRYPOW_REPO_ROOT:-$DIR/..}"
SETUP="$ROOT/setup_fryminer_web.sh"

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

# --- T2: every shipped GitHub URL must resolve -------------------------------
echo "--- shipped GitHub URL reachability ---"

# Templated URLs (those built up with shell/PowerShell variables, and release-asset
# paths whose filename is interpolated) are skipped: extracting them yields a
# truncated URL that would 404 for reasons unrelated to the org being wrong.
URLS=$(grep -rhoE 'https://(raw\.githubusercontent\.com|api\.github\.com/repos|github\.com)/[A-Za-z0-9._-]+/Fry-PoW-MultiMiner[A-Za-z0-9._/-]*' \
        "$ROOT/README.md" "$ROOT/setup_fryminer_web.sh" "$ROOT/setup_fryminer_macos.sh" \
        "$ROOT/setup_fryminer_web.ps1" 2>/dev/null \
    | tr -d '\r' \
    | grep -vE '\$|\{|releases/download' \
    | sort -u)

if [ -z "$URLS" ]; then
    echo "ASSERT FAIL: extracted no GitHub URLs - the extraction itself is broken"
    FAIL=1
else
    for u in $URLS; do
        # -L so a legitimate redirect resolves to its final status rather than 301.
        code=$(curl -sL -o /dev/null -w '%{http_code}' \
               --connect-timeout 5 --max-time 20 "$u" 2>/dev/null)
        case "$u=$code" in
            *api.github.com*=403|*api.github.com*=429)
                # Unauthenticated api.github.com allows 60 requests/hour per IP.
                # Failing here would make the suite flaky on repeat runs, so this
                # is a skip - but a printed one, because silently tolerating it
                # would defeat the point of the check.
                echo "  SKIP $code (rate limited, not a failure) $u" ;;
            *=200)
                echo "  ok   200 $u" ;;
            *)
                echo "ASSERT FAIL: got $code, want 200: $u"
                FAIL=1 ;;
        esac
    done
fi

if [ "$FAIL" -eq 0 ]; then
    echo "PASS: update check returns a real remote version and every shipped URL resolves"
    exit 0
fi
exit 1
