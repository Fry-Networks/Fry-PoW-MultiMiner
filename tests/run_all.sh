#!/bin/sh
# FryPow regression test driver. Runs T1/T2/T3-static always; T3-live only
# with FRYMINER_LIVE=1. Exit nonzero if any test FAILs (SKIPs don't fail).
set -u
DIR=$(cd "$(dirname "$0")" && pwd)

TOTAL=0; PASS=0; FAILED=0; SKIP=0
run() {
    NAME="$1"
    TOTAL=$((TOTAL+1))
    echo ""
    echo "===== $NAME ====="
    sh "$DIR/$NAME"
    RC=$?
    if [ "$RC" -eq 0 ]; then PASS=$((PASS+1)); echo ">>> $NAME: PASS"
    elif [ "$RC" -eq 77 ]; then SKIP=$((SKIP+1)); echo ">>> $NAME: SKIP"
    else FAILED=$((FAILED+1)); echo ">>> $NAME: FAIL (rc=$RC)"
    fi
}

run test_update_check.sh
run test_save_hostile_lock.sh
run test_cpuminer_flags.sh
run test_cpuminer_algo_supported.sh
run test_worker_suffix_no_trailing_dot.sh
run test_worker_no_double_append.sh
run test_macos_worker_suffix.sh
run test_stats_log_read_bounded.sh
run test_stats_algo_parse.sh
run test_status_readonly.sh
run test_sanitize_preserves_tilde.sh
run test_log_rotation.sh
run test_startcgi_pkill_selfmatch.sh
run test_miner_alive.sh

echo ""
echo "===== SUMMARY: total=$TOTAL pass=$PASS fail=$FAILED skip=$SKIP ====="
[ "$FAILED" -eq 0 ] && exit 0
exit 1
