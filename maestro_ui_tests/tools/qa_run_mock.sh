#!/usr/bin/env bash
# qa_run.sh + mitmproxy mock scenario.
# usage: maestro_ui_tests/tools/qa_run_mock.sh <scenario> <adb-serial> <flow.yaml> <case-name> <out-dir> [maestro -e args...]
# Scenarios live in maestro_ui_tests/mocks/scenarios/<scenario>/ (see mocks/README.md).
# Requires on the device: settings put global http_proxy 10.0.2.2:8080 (emulator)
# and the mitmproxy CA installed as a user certificate.
set -u
SCENARIO="$1"; SERIAL="$2"; FLOW="$3"; NAME="$4"; OUT="$5"; shift 5
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT/$NAME"

LOG="$OUT/$NAME/mitm.log"
# shellcheck source=./mitm_lifecycle.sh
. "$HERE/mitm_lifecycle.sh"

mitm_start "$SCENARIO" "$LOG" || exit 2
# Trap first, proxy second. The device keeps the proxy setting after this script
# dies, so a failure in between used to leave the emulator with no network.
trap 'mitm_stop "$SERIAL" "$LOG"' EXIT
# Route the device through the proxy only while the mock is up.
adb -s "$SERIAL" shell settings put global http_proxy "10.0.2.2:$MITM_PORT"

"$HERE/qa_run.sh" "$SERIAL" "$FLOW" "$NAME" "$OUT" "$@"
exit $?
