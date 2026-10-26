#!/usr/bin/env bash
# LOCAL ONLY — fill the "Enter recovery phrase" screen from adb instead of
# typing 24 words through Maestro.
#
# Why: steps/mnemonic/24key_words.yaml taps and types every field one by one,
# which is ~24 Maestro taps (~3 s each) — about a minute and a half per import.
# The screen advances to the next field on a space, so adb can fill the whole
# phrase in a couple of seconds. Sending the entire phrase in ONE `input text`
# does NOT work: characters outrun the field switch and land scrambled
# ("clarify resource" -> word_1="esource", word_2="eightclarify"), so each word
# is sent separately with a space keyevent between them.
#
# Preconditions: the phrase screen is open, the 12/24 toggle already matches the
# phrase, and the first field is focused (tap word_1 first — this script does it
# when ui_drive.py can resolve it).
#
# usage: type_phrase.sh <serial> "<12 or 24 words>"
set -u
SERIAL="$1"; PHRASE="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
PY="${PY:-python3}"
ANDROID_SERIAL="$SERIAL" "$PY" "$HERE/ui_drive.py" tap-id word_1 >/dev/null 2>&1 || true
sleep 1
for w in $PHRASE; do
  adb -s "$SERIAL" shell input text "$w" >/dev/null
  adb -s "$SERIAL" shell input keyevent 62 >/dev/null   # KEYCODE_SPACE -> next field
done
sleep 1
