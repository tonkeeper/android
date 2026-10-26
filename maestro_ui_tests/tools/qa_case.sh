#!/usr/bin/env bash
# Record a screen video + logcat around any QA action, Maestro or not.
# usage: qa_case.sh <serial> <case-name> <out-dir> -- <command ...>
set -u
SERIAL="$1"; NAME="$2"; OUT="$3"; shift 3
[ "${1:-}" = "--" ] && shift
CASE="$OUT/$NAME"; mkdir -p "$CASE"; rm -f "$CASE/.stop"
( i=0; while [ ! -f "$CASE/.stop" ]; do i=$((i+1));
    adb -s "$SERIAL" shell screenrecord --time-limit 180 --size 540x1200 --bit-rate 2000000 "/sdcard/qa_${NAME}_${i}.mp4" >/dev/null 2>&1; done ) &
REC=$!
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" logcat -v time > "$CASE/logcat.txt" 2>&1 & LOG=$!
START=$(date +%s)
( "$@" ) 2>&1 | tee "$CASE/console.txt"; RC=${PIPESTATUS[0]}
touch "$CASE/.stop"; sleep 2; adb -s "$SERIAL" shell pkill -INT screenrecord >/dev/null 2>&1; sleep 2
kill "$REC" "$LOG" >/dev/null 2>&1
for f in $(adb -s "$SERIAL" shell ls /sdcard/ 2>/dev/null | tr -d '\r' | grep "^qa_${NAME}_"); do
  adb -s "$SERIAL" pull "/sdcard/$f" "$CASE/$f" >/dev/null 2>&1; adb -s "$SERIAL" shell rm "/sdcard/$f" >/dev/null 2>&1
done
echo "RESULT=$RC DURATION=$(( $(date +%s) - START ))s CASE=$NAME" | tee -a "$CASE/result.txt"
exit $RC
