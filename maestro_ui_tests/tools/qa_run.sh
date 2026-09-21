#!/usr/bin/env bash
# QA runner: screen-records the device while a Maestro flow runs, collects
# JUnit report, Maestro debug output (screenshots on failure, logs), logcat.
#
# usage: maestro_ui_tests/tools/qa_run.sh [--wallet <profile>] <adb-serial> <flow.yaml> <case-name> <out-dir> [maestro -e args...]
# Run from the repo root or maestro_ui_tests; <out-dir> is usually build/maestro-qa/<date>/<ticket>.
#
# Wallet profiles (seed phrases) live OUTSIDE git in maestro_ui_tests/secrets/wallets.env
# (see secrets/wallets.env.example). --wallet <profile> maps WALLET_<PROFILE> /
# WALLET_<PROFILE>_ADDR from that file onto the WALLET_WITH_MONEY / WALLET_WITH_MONEY_ADDR
# variables every import step expects, so the same flow can run against a TON-only,
# a multichain or a TON+BIP39 wallet. PASSWORD_KEY, MAESTRO_AUTH and RECIEVE_WALLET
# from the same file are passed through when set and not given explicitly.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SECRETS="$HERE/../secrets/wallets.env"
PROFILE=""
if [ "${1:-}" = "--wallet" ]; then PROFILE="$2"; shift 2; fi
SERIAL="$1"; FLOW="$2"; NAME="$3"; OUT="$4"; shift 4
mkdir -p "$OUT/$NAME"

EXTRA=()
if [ -f "$SECRETS" ]; then
  set -a; . "$SECRETS"; set +a
  if [ -n "$PROFILE" ]; then
    P=$(echo "$PROFILE" | tr '[:lower:]' '[:upper:]')
    SEED="$(eval echo "\${WALLET_${P}:-}")"; ADDR="$(eval echo "\${WALLET_${P}_ADDR:-}")"
    [ -n "$SEED" ] || { echo "wallet profile '$PROFILE' not found in $SECRETS (expected WALLET_${P}=...)"; exit 2; }
    EXTRA+=(-e "WALLET_WITH_MONEY=$SEED")
    [ -n "$ADDR" ] && EXTRA+=(-e "WALLET_WITH_MONEY_ADDR=$ADDR")
    echo "wallet profile: $PROFILE ($(echo "$SEED" | wc -w | tr -d ' ') words)"
  fi
  for v in PASSWORD_KEY MAESTRO_AUTH RECIEVE_WALLET; do
    val="$(eval echo "\${$v:-}")"
    if [ -n "$val" ] && ! printf '%s\n' "$@" | grep -q "^${v}="; then
      case "$v" in MAESTRO_AUTH) EXTRA+=(-e "auth=$val");; *) EXTRA+=(-e "$v=$val");; esac
    fi
  done
elif [ -n "$PROFILE" ]; then
  echo "--wallet given but $SECRETS is missing — copy secrets/wallets.env.example and fill it in"; exit 2
fi
CASE_DIR="$OUT/$NAME"
REC_PID_FILE="$CASE_DIR/.rec_pid"

# screenrecord is capped at 180 s per file; loop until told to stop.
(
  i=0
  while [ ! -f "$CASE_DIR/.stop" ]; do
    i=$((i+1))
    adb -s "$SERIAL" shell screenrecord --time-limit 180 --size 540x1200 --bit-rate 2000000 "/sdcard/qa_${NAME}_${i}.mp4" >/dev/null 2>&1
  done
) &
echo $! > "$REC_PID_FILE"

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" logcat -v time > "$CASE_DIR/logcat.txt" 2>&1 &
LOGCAT_PID=$!

START=$(date +%s)
maestro --device "$SERIAL" test "$FLOW" "${EXTRA[@]}" "$@" \
  --format junit --output "$CASE_DIR/junit.xml" \
  --test-output-dir "$CASE_DIR/maestro" \
  --debug-output "$CASE_DIR/maestro-debug" 2>&1 | tee "$CASE_DIR/console.txt"
RC=${PIPESTATUS[0]}
END=$(date +%s)

touch "$CASE_DIR/.stop"
# stop the recording loop first so no new segment starts, then SIGINT the
# running screenrecord (SIGINT finalizes the mp4).
kill $(cat "$REC_PID_FILE") >/dev/null 2>&1
adb -s "$SERIAL" shell "kill -2 \$(pidof screenrecord)" >/dev/null 2>&1
sleep 2
kill $LOGCAT_PID >/dev/null 2>&1
rm -f "$REC_PID_FILE" "$CASE_DIR/.stop"

for f in $(adb -s "$SERIAL" shell "ls /sdcard/qa_${NAME}_*.mp4 2>/dev/null" | tr -d '\r'); do
  adb -s "$SERIAL" pull "$f" "$CASE_DIR/" >/dev/null 2>&1 && adb -s "$SERIAL" shell rm "$f"
done

echo "RESULT=$RC DURATION=$((END-START))s CASE=$NAME" | tee "$CASE_DIR/result.txt"
exit $RC
