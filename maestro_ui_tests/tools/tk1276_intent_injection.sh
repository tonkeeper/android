#!/usr/bin/env bash
# TK-1276 — Intent injection via exported RootActivity ("dapp_deeplink" extra).
# Black-box checks driven through adb (an attacker app == any process that can
# call startActivity; `adb shell am start` runs as the shell uid, which is a
# third party from the app's point of view).
#
# usage: tk1276_intent_injection.sh <adb-serial> <out-dir>
# Precondition: app installed, a wallet exists and is UNLOCKED on the home screen.
set -u
SERIAL="$1"; OUT="$2"; mkdir -p "$OUT"
PKG=com.ton_keeper.debug
ROOT="$PKG/com.tonapps.tonkeeper.ui.screen.root.RootActivity"
SHORTCUT="$PKG/com.tonapps.tonkeeper.ui.screen.root.ShortcutDeeplinkActivity"
MARK_HOST="tk1276-forged.example.com"
LEGIT_HOST="ton.org"
REPORT="$OUT/report.md"
: > "$REPORT"
pass=0; fail=0

adb_() { adb -s "$SERIAL" "$@"; }
shot() { adb_ shell screencap -p /sdcard/tk1276.png >/dev/null; adb_ pull /sdcard/tk1276.png "$OUT/$1.png" >/dev/null 2>&1; }
frag_dump() { adb_ shell dumpsys activity "$PKG" 2>/dev/null | grep -oE '[A-Za-z]*(DApp|Dapp|Browser|WebView)[A-Za-z]*(Screen|Fragment)' | sort -u | tr '\n' ' '; }
webview_open() { adb_ shell dumpsys activity "$PKG" 2>/dev/null | grep -qiE 'DAppScreen|DappScreen|BrowserFragment|WebViewFragment'; }
go_home() {
  # Back out of any sheet/screen (bounded), then re-front the app.
  for _ in 1 2 3 4; do adb_ shell input keyevent KEYCODE_BACK; sleep 0.6; done
  adb_ shell am start -n "$ROOT" >/dev/null 2>&1; sleep 2
}
record() { # name status detail
  if [ "$2" = PASS ]; then pass=$((pass+1)); else fail=$((fail+1)); fi
  printf '| %s | **%s** | %s |\n' "$1" "$2" "$3" >> "$REPORT"
  echo "[$2] $1 — $3"
}

printf '# TK-1276 intent injection — %s\n\n| Case | Result | Evidence |\n|---|---|---|\n' "$(date '+%Y-%m-%d %H:%M')" >> "$REPORT"

adb_ logcat -c
adb_ logcat -v time > "$OUT/logcat.txt" 2>&1 &
LOGPID=$!

# ---------------------------------------------------------------- Case 1
# Non-exported ShortcutDeeplinkActivity must reject a third-party start.
go_home
res=$(adb_ shell am start -n "$SHORTCUT" --es dapp_deeplink "https://$MARK_HOST" 2>&1)
echo "$res" > "$OUT/case1_am_output.txt"
sleep 3; shot case1_after_shortcut_activity_start
if echo "$res" | grep -qiE 'Permission Denial|SecurityException|not exported'; then
  record "1. Start ShortcutDeeplinkActivity from a foreign uid" PASS "am start rejected: $(echo "$res" | grep -iE 'Permission Denial|not exported' | head -1 | cut -c1-140)"
else
  record "1. Start ShortcutDeeplinkActivity from a foreign uid" FAIL "am start accepted (see case1_am_output.txt); dapp open: $(webview_open && echo yes || echo no)"
fi

# ---------------------------------------------------------------- Case 2
# Exported RootActivity with a forged dapp_deeplink extra: activity opens (it is
# the launcher) but the URL must NOT be loaded (no matching pinned shortcut).
go_home
adb_ shell am start -n "$ROOT" --es dapp_deeplink "https://$MARK_HOST" > "$OUT/case2_am_output.txt" 2>&1
sleep 6; shot case2_after_root_forged_extra
frags=$(frag_dump)
if webview_open; then
  record "2. RootActivity + forged dapp_deeplink extra (cold path)" FAIL "dApp screen opened: $frags"
else
  record "2. RootActivity + forged dapp_deeplink extra (cold path)" PASS "no dApp/browser fragment on the stack (${frags:-none})"
fi

# ---------------------------------------------------------------- Case 3
# Same forged extra while the app is already in front (onNewIntent path).
adb_ shell am start -n "$ROOT" --es dapp_deeplink "https://$MARK_HOST" >/dev/null 2>&1
sleep 5; shot case3_after_root_forged_extra_onNewIntent
frags=$(frag_dump)
if webview_open; then
  record "3. RootActivity + forged dapp_deeplink extra (onNewIntent)" FAIL "dApp screen opened: $frags"
else
  record "3. RootActivity + forged dapp_deeplink extra (onNewIntent)" PASS "no dApp/browser fragment on the stack (${frags:-none})"
fi

# ---------------------------------------------------------------- Case 4
# Sibling extra "link" with an arbitrary https URL — must not open a WebView.
go_home
adb_ shell am start -n "$ROOT" --es link "https://$MARK_HOST" >/dev/null 2>&1
sleep 5; shot case4_after_root_link_extra
frags=$(frag_dump)
if webview_open; then
  record "4. RootActivity + \"link\" extra with foreign https URL" FAIL "dApp screen opened: $frags"
else
  record "4. RootActivity + \"link\" extra with foreign https URL" PASS "no dApp/browser fragment (${frags:-none})"
fi

# ---------------------------------------------------------------- Case 5
# Forged extra combined with a push-style bundle (type + deeplink) must not
# turn into a dApp open either.
go_home
adb_ shell am start -n "$ROOT" --es type console_dapp_notification --es dapp_url "https://$MARK_HOST" --es dapp_deeplink "https://$MARK_HOST" >/dev/null 2>&1
sleep 5; shot case5_after_root_push_bundle
frags=$(frag_dump)
if webview_open; then
  record "5. RootActivity + push-style bundle carrying foreign URL" FAIL "dApp screen opened: $frags"
else
  record "5. RootActivity + push-style bundle carrying foreign URL" PASS "no dApp/browser fragment (${frags:-none})"
fi

# ---------------------------------------------------------------- Case 6
# Positive control: the public tonkeeper://dapp/<host> deeplink still opens the
# in-app browser (fix must not break the legit route).
go_home
adb_ shell am start -a android.intent.action.VIEW -d "tonkeeper://dapp/$LEGIT_HOST" >/dev/null 2>&1
sleep 8; shot case6_legit_dapp_deeplink
frags=$(frag_dump)
if webview_open; then
  record "6. Legit tonkeeper://dapp/$LEGIT_HOST deeplink (positive control)" PASS "dApp screen opened: $frags"
else
  record "6. Legit tonkeeper://dapp/$LEGIT_HOST deeplink (positive control)" FAIL "no dApp screen opened (${frags:-none}) — check screenshot"
fi

# ---------------------------------------------------------------- Case 7
# Same-uid start of ShortcutDeeplinkActivity (what a real pinned shortcut does).
go_home
res=$(adb_ shell run-as "$PKG" am start -n "$SHORTCUT" --es dapp_deeplink "https://$LEGIT_HOST" 2>&1)
echo "$res" > "$OUT/case7_am_output.txt"
sleep 8; shot case7_same_uid_shortcut
if echo "$res" | grep -qiE 'Permission Denial|not exported|denied|not found|No such file|Error'; then
  record "7. ShortcutDeeplinkActivity from the app's own uid" SKIP "run-as cannot start activities here: $(echo "$res" | head -1 | cut -c1-120) — cover manually by pinning a dApp shortcut"
  fail=$((fail-1))
elif webview_open; then
  record "7. ShortcutDeeplinkActivity from the app's own uid" PASS "own-uid start opens the dApp: $(frag_dump)"
else
  record "7. ShortcutDeeplinkActivity from the app's own uid" FAIL "own-uid start did not open the dApp"
fi

go_home
kill $LOGPID >/dev/null 2>&1
grep -iE 'openDApp|ShortcutDeeplink|dapp_deeplink|Permission Denial|DAppScreen' "$OUT/logcat.txt" > "$OUT/logcat_filtered.txt"
printf '\nPASS=%s FAIL=%s\n' "$pass" "$fail" | tee -a "$REPORT"
[ "$fail" -eq 0 ]
