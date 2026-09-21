#!/usr/bin/env bash
# TK-3365 biometry branch driver (emulator with an enrolled fingerprint).
# usage: maestro_ui_tests/tools/bio_driver.sh <serial> <accepted|cancelled> <out-dir>
# Runs the split Maestro flows and answers the system prompt with the virtual
# sensor where Maestro cannot (adb emu finger touch 1).
set -u
SERIAL="$1"; MODE="$2"; OUT="$3"; mkdir -p "$OUT"
Q="$(cd "$(dirname "$0")" && pwd)"
cd "$Q/.." || exit 1
F=flows/onboarding_local
run() { "$Q/qa_run.sh" "$SERIAL" "$1" "$2" "$OUT" -e PASSWORD_KEY=5 -e OUT="$OUT/$2" "${@:3}" 2>&1 | grep -E '^\[|RESULT|Assertion'; }
bio_pref() { adb -s "$SERIAL" shell run-as com.ton_keeper.debug cat shared_prefs/settings.xml 2>/dev/null | grep -oE 'name="biometric" value="(true|false)"' || echo 'biometric key absent (=false)'; }

echo "### step 1: onboarding up to the biometry prompt"
run $F/biometry_1_to_prompt.yaml "bio_${MODE}_1_prompt"
if [ "$MODE" = accepted ]; then
  echo "### answering prompt with virtual fingerprint"
  adb -s "$SERIAL" emu finger touch 1; sleep 2
fi
echo "### step 2: after prompt → home → Finish setting up → Security"
run $F/biometry_2_after_prompt.yaml "bio_${MODE}_2_after" -e BIOMETRY="$MODE"
echo "settings.xml after onboarding: $(bio_pref)" | tee "$OUT/bio_${MODE}_pref_after_onboarding.txt"

if [ "$MODE" = cancelled ]; then
  echo "### step 3: enable from the Finish setting up row (passcode → prompt)"
  run $F/biometry_3_finish_setup_row.yaml "bio_${MODE}_3_row"
  adb -s "$SERIAL" emu finger touch 1; sleep 2
  echo "### step 4: row gone, Security shows biometry ON"
  run $F/biometry_4_row_done.yaml "bio_${MODE}_4_done"
  echo "settings.xml after enabling from card: $(bio_pref)" | tee "$OUT/bio_${MODE}_pref_after_row.txt"
fi
