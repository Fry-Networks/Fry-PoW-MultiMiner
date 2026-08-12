#!/bin/sh
# T4 — Bug 3 (root cause 2) regression: start.cgi's critical section runs as
# `flock -c '<script>'`, embedding the script text in the executing shell's
# cmdline. ANY `pkill -f <pattern>` inside that text whose pattern can match
# a substring of the text itself (miner names — even inside comments) matches
# the shell's own cmdline and SIGKILLs it before the miner spawns.
# The safe form is `pkill -x <name>` (exact comm match, cmdline never read).
# RED on pre-fix code: cmdline-matching -f pkills present in the block.
set -u

DIR=$(cd "$(dirname "$0")" && pwd)
SETUP="$DIR/../setup_fryminer_web.sh"

SB=$(mktemp -d)
trap 'rm -rf "$SB"' EXIT

sh "$DIR/lib/extract_cgi.sh" start.cgi "$SETUP" "$SB/start.cgi" || {
    echo "FAIL: could not extract start.cgi"; exit 1; }

# The flock -c block is a single-quoted string: a stray apostrophe anywhere
# in it (even in a comment) breaks the whole generated CGI at runtime.
if ! sh -n "$SB/start.cgi" 2>"$SB/synerr"; then
    echo "ASSERT FAIL: extracted start.cgi does not parse:"
    cat "$SB/synerr"
    exit 1
fi

# Isolate the flock -c '...' region: from the flock line to the closing quote
awk "/flock -x -o .*miner.lock -c '/,/^    '/" "$SB/start.cgi" > "$SB/flock_block"
if [ ! -s "$SB/flock_block" ]; then
    echo "FAIL: could not locate the flock -c critical section in start.cgi"
    exit 1
fi

# No cmdline-matching pkill (-f) may appear inside the flock -c text at all —
# the miner names unavoidably appear in that text (in the pkill lines
# themselves), so only comm-based matching (-x, no -f) is self-safe.
BAD=$(grep -nE 'pkill [^|#]*-f ' "$SB/flock_block" || true)
if [ -n "$BAD" ]; then
    echo "ASSERT FAIL: cmdline-matching pkill -f inside flock -c block (self-kill risk):"
    echo "$BAD"
    exit 1
fi
if ! grep -qE 'pkill -9 -x cpuminer' "$SB/flock_block"; then
    echo "ASSERT FAIL: expected comm-based (pkill -9 -x) miner kills in flock -c block"
    exit 1
fi

echo "PASS: all pkill calls in start.cgi's flock -c block are comm-based (self-match-safe)"
exit 0
