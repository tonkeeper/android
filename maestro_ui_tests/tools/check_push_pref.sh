#!/usr/bin/env bash
# usage: check_push_pref.sh <serial> <expected: true|false> [out-file]
# Reads the debug app's wallet_prefs.xml (run-as) and checks push_<walletId>.
SERIAL="$1"; EXP="$2"; OUTF="${3:-/dev/stdout}"
X=$(adb -s "$SERIAL" shell run-as com.ton_keeper.debug cat shared_prefs/wallet_prefs.xml 2>/dev/null)
LINE=$(echo "$X" | grep -oE 'name="push_[^"]+" value="(true|false)"')
echo "wallet_prefs push keys: ${LINE:-<none>}" | tee "$OUTF"
if [ -z "$LINE" ]; then
  if [ "$EXP" = "false" ]; then echo "PUSH_PREF=absent (treated as OFF) expected=$EXP → PASS" | tee -a "$OUTF"; exit 0; fi
  echo "PUSH_PREF=absent expected=$EXP → FAIL" | tee -a "$OUTF"; exit 1
fi
if echo "$LINE" | grep -q "value=\"$EXP\"" && ! echo "$LINE" | grep -qv "value=\"$EXP\""; then
  echo "PUSH_PREF=$EXP expected=$EXP → PASS" | tee -a "$OUTF"; exit 0
fi
echo "PUSH_PREF mismatch expected=$EXP → FAIL" | tee -a "$OUTF"; exit 1
