#!/usr/bin/env bash
# Put arbitrary text on a device's clipboard, so a flow can PASTE instead of type.
#
# Why: Maestro's inputText is real per-character typing — a 24-word recovery
# phrase costs ~24 taps plus typing (~1.5 min per import), and Android has no
# `cmd clipboard set-primary-clip` on these images. This serves a one-button page
# from the host, opens it in Chrome on the device and taps COPY, which puts the
# text on the guest clipboard. Then the app's own Paste button fills everything
# (the recovery-phrase screen distributes a pasted phrase across all fields).
#
# Also the only way to get non-ASCII text into a field (Maestro inputText is
# ASCII-only on Android — see references/lessons.md, TK-3712).
#
# usage: setclip.sh <serial> "<text>"
set -u
SERIAL="$1"; TEXT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
PY="${PY:-python3}"
PORT="${CLIP_PORT:-8765}"
DIR="$(mktemp -d)"
cat > "$DIR/clip.html" <<'HTML'
<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<body style="font-size:28px">
<button id=b style="font-size:40px;padding:20px">COPYNOW</button>
<div id=s style="font-size:32px;margin-top:20px"></div>
<script>
var t = new URLSearchParams(location.search).get('t') || '';
document.getElementById('b').onclick = function () {
  var ok = false;
  try {
    var ta = document.createElement('textarea');
    ta.value = t; document.body.appendChild(ta); ta.select();
    ok = document.execCommand('copy'); ta.remove();
  } catch (e) {}
  document.getElementById('s').textContent = 'COPIED:' + ok;
};
</script>
HTML
export LC_ALL=en_US.UTF-8   # the Bash tool shell is LC_CTYPE=C and mangles non-ASCII
ENC=$("$PY" -c "import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))" "$TEXT")
( cd "$DIR" && "$PY" -m http.server "$PORT" >/dev/null 2>&1 & echo $! > "$DIR/.pid" )
sleep 1
adb -s "$SERIAL" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null   # Chrome never proxies loopback
adb -s "$SERIAL" shell am start -a android.intent.action.VIEW -d "http://localhost:$PORT/clip.html?t=$ENC" com.android.chrome >/dev/null
maestro --device "$SERIAL" test "$HERE/../flows/amount_local/clip_copy.yaml" 2>&1 | grep -E "COPIED|FAILED" | tail -2
adb -s "$SERIAL" shell am start -n com.ton_keeper.debug/com.tonapps.tonkeeper.DefaultLauncherIcon >/dev/null
sleep 2
kill "$(cat "$DIR/.pid")" 2>/dev/null
adb -s "$SERIAL" reverse --remove "tcp:$PORT" >/dev/null 2>&1
rm -rf "$DIR"
