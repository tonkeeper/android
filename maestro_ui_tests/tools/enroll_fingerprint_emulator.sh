#!/usr/bin/env bash
# Enrol a fingerprint on an Android emulator so BiometricManager reports
# BIOMETRIC_SUCCESS (needed to exercise the TK-3365 onboarding biometry prompt).
# Sets a device PIN (required by the enrolment flow), drives the Settings
# enrolment UI, and taps the virtual sensor with `adb emu finger touch`.
# usage: enroll_fingerprint_emulator.sh <serial> [pin]
set -u
SERIAL="$1"; PIN="${2:-1234}"
A() { adb -s "$SERIAL" "$@"; }

echo "== device lock: set PIN $PIN =="
A shell locksettings set-pin "$PIN" 2>&1 | tail -1
A shell svc power stayon true
A shell settings put system screen_off_timeout 1800000

echo "== open fingerprint enrolment =="
A shell am start -a android.settings.FINGERPRINT_ENROLL >/dev/null 2>&1
sleep 3
# Enrolment asks for the device PIN first.
A shell input text "$PIN"; A shell input keyevent KEYCODE_ENTER; sleep 2
A shell screencap -p /sdcard/enroll1.png >/dev/null

# Intro screens: "I agree" / "Next" / "Start" — tap whatever primary button exists.
for _ in 1 2 3; do
  for label in "I agree" "Agree" "Next" "Start" "Continue" "Got it"; do
    A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    if A shell grep -q "text=\"$label\"" /sdcard/ui.xml 2>/dev/null; then
      b=$(A shell grep -o "text=\"$label\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" /sdcard/ui.xml | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
      x1=$(echo "$b" | tr -d '[]' | cut -d, -f1); y1=$(echo "$b" | tr -d '[]' | cut -d, -f2 | cut -d']' -f1)
      x2=$(echo "$b" | sed 's/.*\]\[//' | cut -d, -f1); y2=$(echo "$b" | sed 's/.*,//')
      A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); sleep 1.5
    fi
  done
done

echo "== touch the virtual sensor until enrolled =="
for i in $(seq 1 12); do
  A emu finger touch 1 >/dev/null 2>&1
  sleep 1.2
  if A shell dumpsys fingerprint 2>/dev/null | grep -qE '"count":[1-9]'; then
    A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    A shell grep -q 'text="Done"' /sdcard/ui.xml 2>/dev/null && break
  fi
done
A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(A shell grep -o 'text="Done"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' /sdcard/ui.xml | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
if [ -n "$b" ]; then
  x1=$(echo "$b" | tr -d '[]' | cut -d, -f1); y1=$(echo "$b" | tr -d '[]' | cut -d, -f2 | cut -d']' -f1)
  x2=$(echo "$b" | sed 's/.*\]\[//' | cut -d, -f1); y2=$(echo "$b" | sed 's/.*,//')
  A shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
fi
sleep 1
A shell screencap -p /sdcard/enroll_done.png >/dev/null
echo "== result =="
A shell dumpsys fingerprint 2>/dev/null | grep -oE '"prints":\[[^]]*\]'
A shell input keyevent KEYCODE_HOME
