#!/usr/bin/env bash
# qa_run.sh + mitmproxy mock scenario.
# usage: maestro_ui_tests/tools/qa_run_mock.sh <scenario> <adb-serial> <flow.yaml> <case-name> <out-dir> [maestro -e args...]
# Scenarios live in maestro_ui_tests/mocks/scenarios/<scenario>/ (see mocks/README.md).
# Requires on the device: settings put global http_proxy 10.0.2.2:8080 (emulator)
# and the mitmproxy CA installed as a user certificate.
set -u
SCENARIO="$1"; SERIAL="$2"; FLOW="$3"; NAME="$4"; OUT="$5"; shift 5
HERE="$(cd "$(dirname "$0")" && pwd)"
MOCKS="$(cd "$HERE/../mocks" && pwd)"
mkdir -p "$OUT/$NAME"

pkill -f "mitmdump.*mock.py" >/dev/null 2>&1
mitmdump -q --listen-port 8080 -s "$MOCKS/mock.py" --set scenario="$SCENARIO" > "$OUT/$NAME/mitm.log" 2>&1 &
MITM_PID=$!
sleep 2
if ! kill -0 $MITM_PID 2>/dev/null; then echo "mitmdump failed to start"; cat "$OUT/$NAME/mitm.log"; exit 2; fi
# Route the device through the proxy only while the mock is up; an orphaned
# proxy setting leaves the emulator without network.
adb -s "$SERIAL" shell settings put global http_proxy 10.0.2.2:8080

"$HERE/qa_run.sh" "$SERIAL" "$FLOW" "$NAME" "$OUT" "$@"
RC=$?

kill $MITM_PID >/dev/null 2>&1
adb -s "$SERIAL" shell settings put global http_proxy :0
echo "--- mock hits ---"; grep -E 'MOCK HIT' "$OUT/$NAME/mitm.log" | sort | uniq -c
exit $RC
