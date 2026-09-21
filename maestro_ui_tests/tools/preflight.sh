#!/usr/bin/env bash
# Preflight for local Maestro QA runs (tk-qa-verify skill).
# usage: maestro_ui_tests/tools/preflight.sh [adb-serial]
# Checks the host tools, the device, the installed debug build and the mock
# prerequisites, and prints one OK/WARN/FAIL line per item. Exit code 1 when
# anything required is missing.
set -u
SERIAL="${1:-}"
APP=com.ton_keeper.debug
RC=0
ok()   { printf "OK    %s\n" "$1"; }
warn() { printf "WARN  %s\n" "$1"; }
fail() { printf "FAIL  %s\n" "$1"; RC=1; }

command -v maestro >/dev/null && ok "maestro $(maestro --version 2>/dev/null | head -1)" || fail "maestro not installed: curl -fsSL https://get.maestro.mobile.dev | bash"
command -v adb >/dev/null && ok "adb $(adb --version | head -1 | awk '{print $NF}')" || fail "adb not in PATH (Android SDK platform-tools)"
command -v python3 >/dev/null && ok "python3 $(python3 --version | awk '{print $2}')" || fail "python3 missing"
if command -v mitmdump >/dev/null; then ok "mitmdump $(mitmdump --version 2>/dev/null | head -1 | awk '{print $2}') (mocks available)"; else warn "mitmdump missing — mocked scenarios unavailable: brew install mitmproxy"; fi
[ -f "$HOME/.mitmproxy/mitmproxy-ca-cert.cer" ] && ok "mitmproxy CA generated (~/.mitmproxy)" || warn "no mitmproxy CA yet — run mitmdump once, then install the CA on the device (see mocks/README.md)"

DEVICES=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
if [ -z "$DEVICES" ]; then
  fail "no adb device online. Emulator: \$ANDROID_HOME/emulator/emulator -avd <AVD> -memory 4096 -cores 4"
  exit $RC
fi
if [ -z "$SERIAL" ]; then
  SERIAL=$(echo "$DEVICES" | head -1)
  [ "$(echo "$DEVICES" | wc -l | tr -d ' ')" -gt 1 ] && warn "several devices online, using $SERIAL — pass the serial explicitly"
fi
echo "$DEVICES" | grep -qx "$SERIAL" && ok "device $SERIAL online" || { fail "device $SERIAL not online"; exit $RC; }

A() { adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }
MODEL=$(A getprop ro.product.model); REL=$(A getprop ro.build.version.release)
ok "device: $MODEL, Android $REL"
if echo "$SERIAL" | grep -q '^emulator-'; then
  RAM=$(A cat /proc/meminfo | awk '/MemTotal/{printf "%d", $2/1024}')
  [ "${RAM:-0}" -ge 3500 ] && ok "emulator RAM ${RAM} MB" || warn "emulator RAM ${RAM} MB — restart with -memory 4096, otherwise taps take 60s+"
  [ "$(A settings get global airplane_mode_on)" = "0" ] && ok "airplane mode off" || warn "airplane mode ON — adb shell cmd connectivity airplane-mode disable"
fi
LOCALE=$(A getprop persist.sys.locale); APPLOC=$(A cmd locale get-app-locales $APP | grep -oE '\[[^]]*\]')
case "${APPLOC:-[]}${LOCALE}" in
  "[]"|*en*) ok "app locale English (system '${LOCALE:-default}', app '${APPLOC:-[]}')";;
  *) warn "app locale is not English (system '$LOCALE', app '$APPLOC') — flows assert English copy: adb shell cmd locale set-app-locales $APP --locales en-US";;
esac
if A dumpsys window | grep -q 'isKeyguardShowing=true'; then warn "device is locked (keyguard) — unlock it before running"; else ok "device unlocked"; fi

PKG=$(A dumpsys package $APP | grep -E 'versionName' | head -1 | sed 's/^ *//')
[ -n "$PKG" ] && ok "$APP installed: $PKG" || fail "$APP not installed — build :apps:wallet:instance:main:assembleDefaultDebug and adb install -r"
PROXY=$(A settings get global http_proxy)
case "$PROXY" in ""|":0"|"null") ok "no global http proxy set";; *) warn "global http_proxy=$PROXY — leftover from a mock run? settings put global http_proxy :0";; esac
if [ -f "$HOME/.mitmproxy/mitmproxy-ca-cert.cer" ]; then
  if A ls /data/misc/user/0/cacerts-added >/dev/null 2>&1; then :; fi
  warn "cannot verify the user CA store without root — if mocked runs show 'Client TLS handshake failed' for $APP, install ~/.mitmproxy/mitmproxy-ca-cert.cer via Settings"
fi
STALE=$(pgrep -fl 'adb.*logcat|mitmdump' | wc -l | tr -d ' ')
[ "$STALE" = "0" ] && ok "no stale logcat/mitmdump processes" || warn "$STALE stale adb logcat / mitmdump process(es) — they can break the Maestro driver: pkill -f 'adb.*logcat'; pkill -f mitmdump"

exit $RC
