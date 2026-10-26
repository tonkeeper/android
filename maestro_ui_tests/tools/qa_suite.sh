#!/usr/bin/env bash
# Batch QA runner: several cases in ONE `maestro test` invocation, one recording.
#
# Why: starting the Maestro CLI costs ~15 s (JVM + driver handshake) no matter
# how short the flow is, and a cold app start costs another ~14 s. Running ten
# cases one by one therefore burns ~5 minutes before a single assert executes.
# This script pays that once. Measured 2026-09-16, see references/lessons.md.
#
# usage: qa_suite.sh [--wallet <profile>] [--no-video] <adb-serial> <suite-name> <out-dir> <flow.yaml>... [-- maestro -e args...]
#
# Each flow keeps its own screenshot namespace: pass -e OUT=<out>/<suite> and
# have every flow write to "${OUT}/<case>_NN_name". Flows run in the order given.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SECRETS="$HERE/../secrets/wallets.env"
PROFILE=""; VIDEO=1
while [ "${1:-}" != "" ]; do
  case "$1" in
    --wallet) PROFILE="$2"; shift 2;;
    --no-video) VIDEO=0; shift;;
    *) break;;
  esac
done
SERIAL="$1"; NAME="$2"; OUT="$3"; shift 3

FLOWS=()
while [ "${1:-}" != "" ] && [ "$1" != "--" ]; do FLOWS+=("$1"); shift; done
[ "${1:-}" = "--" ] && shift
[ ${#FLOWS[@]} -gt 0 ] || { echo "no flows given"; exit 2; }

CASE_DIR="$OUT/$NAME"
mkdir -p "$CASE_DIR"

EXTRA=()
if [ -f "$SECRETS" ]; then
  set -a; . "$SECRETS"; set +a
  if [ -n "$PROFILE" ]; then
    P=$(echo "$PROFILE" | tr '[:lower:]' '[:upper:]')
    SEED="$(eval echo "\${WALLET_${P}:-}")"; ADDR="$(eval echo "\${WALLET_${P}_ADDR:-}")"
    [ -n "$SEED" ] || { echo "wallet profile '$PROFILE' not found in $SECRETS"; exit 2; }
    EXTRA+=(-e "WALLET_WITH_MONEY=$SEED")
    [ -n "$ADDR" ] && EXTRA+=(-e "WALLET_WITH_MONEY_ADDR=$ADDR")
  fi
  for v in PASSWORD_KEY MAESTRO_AUTH RECIEVE_WALLET; do
    val="$(eval echo "\${$v:-}")"
    if [ -n "$val" ] && ! printf '%s\n' "$@" | grep -q "^${v}="; then
      case "$v" in MAESTRO_AUTH) EXTRA+=(-e "auth=$val");; *) EXTRA+=(-e "$v=$val");; esac
    fi
  done
elif [ -n "$PROFILE" ]; then
  echo "--wallet given but $SECRETS is missing"; exit 2
fi

# Maestro takes ONE path, so the flows are staged as an ordered directory of
# symlinks. The staging dir MUST sit at flows/<dir>/ depth: Maestro resolves a
# flow's `runFlow: ../../steps/...` against the path it opened, so staging
# anywhere else breaks every shared step.
SUITE_DIR="$HERE/../flows/.suite_$NAME"
rm -rf "$SUITE_DIR"; mkdir -p "$SUITE_DIR"
trap 'rm -rf "$SUITE_DIR"' EXIT
i=0
for f in "${FLOWS[@]}"; do
  i=$((i+1))
  [ -f "$f" ] || { echo "flow not found: $f"; exit 2; }
  ln -s "$(cd "$(dirname "$f")" && pwd)/$(basename "$f")" "$SUITE_DIR/$(printf '%02d' $i)_$(basename "$f")"
done

if [ "$VIDEO" = "1" ]; then
  (
    n=0
    while [ ! -f "$CASE_DIR/.stop" ]; do
      n=$((n+1))
      adb -s "$SERIAL" shell screenrecord --time-limit 180 --size 540x1200 --bit-rate 2000000 "/sdcard/qa_${NAME}_${n}.mp4" >/dev/null 2>&1
    done
  ) &
  echo $! > "$CASE_DIR/.rec_pid"
fi

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" logcat -v time > "$CASE_DIR/logcat.txt" 2>&1 &
LOGCAT_PID=$!

START=$(date +%s)
maestro --device "$SERIAL" test "$SUITE_DIR" "${EXTRA[@]}" "$@" \
  --format junit --output "$CASE_DIR/junit.xml" \
  --test-output-dir "$CASE_DIR/maestro" \
  --debug-output "$CASE_DIR/maestro-debug" 2>&1 | tee "$CASE_DIR/console.txt"
RC=${PIPESTATUS[0]}
END=$(date +%s)

if [ "$VIDEO" = "1" ]; then
  touch "$CASE_DIR/.stop"
  kill "$(cat "$CASE_DIR/.rec_pid")" >/dev/null 2>&1
  adb -s "$SERIAL" shell "kill -2 \$(pidof screenrecord)" >/dev/null 2>&1
  sleep 2
  rm -f "$CASE_DIR/.rec_pid" "$CASE_DIR/.stop"
  for f in $(adb -s "$SERIAL" shell "ls /sdcard/qa_${NAME}_*.mp4 2>/dev/null" | tr -d '\r'); do
    adb -s "$SERIAL" pull "$f" "$CASE_DIR/" >/dev/null 2>&1 && adb -s "$SERIAL" shell rm "$f"
  done
fi
kill $LOGCAT_PID >/dev/null 2>&1

echo "--- per case ---"
grep -oE '<testcase[^>]*name="[^"]*"[^>]*status="[^"]*"' "$CASE_DIR/junit.xml" 2>/dev/null |
  sed -E 's/.*name="([^"]*)".*status="([^"]*)".*/\2  \1/' || true
echo "RESULT=$RC DURATION=$((END-START))s CASES=${#FLOWS[@]} SUITE=$NAME" | tee "$CASE_DIR/result.txt"
exit $RC
