#!/usr/bin/env bash
# android-emulator-runner runs each line of `script:` as a separate `sh -c` — keep full logic here (one invocation).
set -euo pipefail

EMU="emulator-${EMULATOR_PORT:?EMULATOR_PORT not set}"
: "${APK_PATH:?APK_PATH not set}"

REPO_ROOT="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_ROOT"

MW="maestro_ui_tests"
CONFIG_FILE="${MW}/config.yaml"
REPORT_FILE="build/maestro-results/report.xml"

if [[ -n "${MAESTRO_FLOW_GROUP:-}" ]]; then
  CONFIG_FILE="${MW}/.ci-flow-shard.yaml"
  REPORT_FILE="build/maestro-results/report-${MAESTRO_FLOW_GROUP}.xml"
  cat > "$CONFIG_FILE" <<EOF
platform:
  android:
    disableAnimations: true
flows:
  - flows/${MAESTRO_FLOW_GROUP}/*
EOF
fi

mkdir -p build/maestro-results
adb -s "$EMU" wait-for-device

i=0
while [ "$i" -lt 60 ]; do
  booted=$(adb -s "$EMU" shell getprop sys.boot_completed | tr -d '\r')
  [ "$booted" = "1" ] && break
  sleep 2
  i=$((i + 1))
done

[ "$(adb -s "$EMU" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]

sleep 5
adb -s "$EMU" shell input keyevent 82 || true
adb -s "$EMU" shell input keyevent 3 || true
adb -s "$EMU" install -r "$APK_PATH"
adb -s "$EMU" shell pm path com.ton_keeper.debug

TEST_OUT="build/maestro-results/test-output"
DEBUG_OUT="build/maestro-results/debug-output"
if [[ -n "${MAESTRO_FLOW_GROUP:-}" ]]; then
  TEST_OUT="build/maestro-results/test-output-${MAESTRO_FLOW_GROUP}"
  DEBUG_OUT="build/maestro-results/debug-output-${MAESTRO_FLOW_GROUP}"
fi

"$HOME/.maestro/bin/maestro" --version

REC_PID=""
DONE_FLAG=""
if [[ "${MAESTRO_SCREEN_RECORDING:-1}" != "0" ]]; then
  RECORD_DIR="build/maestro-results/screen-recordings"
  mkdir -p "$RECORD_DIR"
  DONE_FLAG="${TMPDIR:-/tmp}/maestro_recording_done_$$"
  rm -f "$DONE_FLAG"

  record_segments() {
    local i=0
    local remote
    while true; do
      remote="/sdcard/maestro_ci_${i}.mp4"
      adb -s "$EMU" shell rm -f "$remote" 2>/dev/null || true
      adb -s "$EMU" shell screenrecord --time-limit 179 "$remote" 2>/dev/null || true
      if adb -s "$EMU" shell "test -f $remote" 2>/dev/null; then
        adb -s "$EMU" pull "$remote" "$RECORD_DIR/part-$(printf '%03d' "$i").mp4" 2>/dev/null || true
        adb -s "$EMU" shell rm -f "$remote" 2>/dev/null || true
      fi
      i=$((i + 1))
      [[ -f "$DONE_FLAG" ]] && break
    done
  }

  record_segments &
  REC_PID=$!
fi

set +e
"$HOME/.maestro/bin/maestro" --verbose --device "$EMU" test \
  --config "$CONFIG_FILE" \
  --format junit \
  --output "$REPORT_FILE" \
  --test-output-dir "$TEST_OUT" \
  --debug-output "$DEBUG_OUT" \
  -e PASSWORD_KEY=5 \
  -e "MNEMONIC_PHRASE=${MAESTRO_MNEMONIC_PHRASE}" \
  -e "WALLET_WITH_MONEY=${MAESTRO_WALLET_WITH_MONEY}" \
  -e "WALLET_WITH_MONEY_ADDR=${MAESTRO_WALLET_WITH_MONEY_ADDR}" \
  -e "auth=${MAESTRO_AUTH}" \
  -e "RECIEVE_WALLET=${MAESTRO_RECIEVE_WALLET}" \
  -e "TESTNET_MNEM=${MAESTRO_TESTNET_MNEM}" \
  "./${MW}"
MAESTRO_EXIT=$?
set -e

if [[ -n "$REC_PID" ]]; then
  touch "$DONE_FLAG"
  adb -s "$EMU" shell "pkill -2 screenrecord" 2>/dev/null || adb -s "$EMU" shell "killall -2 screenrecord" 2>/dev/null || true
  sleep 2
  wait "$REC_PID" 2>/dev/null || true
  rm -f "$DONE_FLAG"
fi

exit "$MAESTRO_EXIT"
