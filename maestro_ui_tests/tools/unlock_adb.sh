#!/usr/bin/env bash
# LOCAL ONLY — bring the app to an unlocked home WITHOUT spending Maestro commands.
#
# Why: on the QA emulator one Maestro tap costs ~3 s (it walks the view hierarchy
# three times), so entering a 4-digit passcode inside a flow costs ~10 s, and a
# cold start after `force-stop` costs another ~14 s. The same work through adb
# costs ~0.2 s and ~0.04 s (warm). Measured 2026-09-16, see references/lessons.md.
#
# usage: unlock_adb.sh [--cold] [--no-flags] <serial> <passcode-digit>
#   --cold     force-stop first (only when the case needs a clean start);
#              default is a warm launch, which keeps the app process and
#              usually skips the passcode entirely
#   --no-flags launch without the featureFlags/bootFlags extras
#
# The pin pad is a fixed grid on a 1080x2400 screen (columns 208/540/872, rows
# 1591/1780/1969/2158). Re-check with `ui_drive.py dump` if the AVD changes.
set -u
PKG="${APP_ID:-com.ton_keeper.debug}"
LAUNCH="$PKG/com.tonapps.tonkeeper.DefaultLauncherIcon"
COLD=0; FLAGS=1
while [ "${1:-}" != "" ]; do
  case "$1" in
    --cold) COLD=1; shift;;
    --no-flags) FLAGS=0; shift;;
    *) break;;
  esac
done
SERIAL="$1"; DIGIT="${2:-5}"

EXTRAS=""
if [ "$FLAGS" = "1" ]; then
  EXTRAS="--es featureFlags '{\"android_is_multichain_enabled\": true, \"android_is_import_multichain_wallet\": true, \"android_is_perps_enabled\": true}' --es bootFlags '{\"multichain_enabled\": true}'"
fi

[ "$COLD" = "1" ] && adb -s "$SERIAL" shell am force-stop "$PKG"
adb -s "$SERIAL" shell "am start -W -n $LAUNCH $EXTRAS" >/dev/null

# Wait for the app window to take focus, not the splash (cheap: dumpsys, not
# uiautomator). A cold start on a freshly booted emulator can take ~15 s.
for _ in $(seq 1 80); do
  adb -s "$SERIAL" shell dumpsys window 2>/dev/null | grep -qE "mCurrentFocus=Window\{[^}]*$PKG" && break
  sleep 0.5
done

# A warm launch usually lands straight on home; only a cold start shows the pin.
if [ "$COLD" = "1" ]; then
  sleep 3
  case "$DIGIT" in
    1) X=208; Y=1591;; 2) X=540; Y=1591;; 3) X=872; Y=1591;;
    4) X=208; Y=1780;; 5) X=540; Y=1780;; 6) X=872; Y=1780;;
    7) X=208; Y=1969;; 8) X=540; Y=1969;; 9) X=872; Y=1969;;
    0) X=540; Y=2158;; *) echo "unsupported passcode digit '$DIGIT'"; exit 2;;
  esac
  for _ in 1 2 3 4; do adb -s "$SERIAL" shell input tap $X $Y; done
fi
