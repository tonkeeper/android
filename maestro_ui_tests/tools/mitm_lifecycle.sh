#!/usr/bin/env bash
# Shared mitmdump lifecycle for the mocked runs. Sourced, not executed:
#
#   . "<maestro_ui_tests>/tools/mitm_lifecycle.sh"
#   mitm_start "$SCENARIO" "$OUT/$NAME/mitm.log" || exit 2
#   trap 'mitm_stop "$SERIAL" "$OUT/$NAME/mitm.log"' EXIT
#   adb -s "$SERIAL" shell settings put global http_proxy 10.0.2.2:$MITM_PORT
#
# Arm the trap BEFORE turning the proxy on. The device keeps the proxy setting
# after the script dies, so a failure between those two lines leaves the
# emulator with no network at all.

MITM_PORT="${MITM_PORT:-8080}"

# Absolute paths to the shared mock.py and this app's fixtures, resolved from this
# file so callers do not have to be in any particular directory.
_MITM_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MITM_MOCK="$_MITM_HERE/../../tools/maestro/mock.py"
[ -f "$MITM_MOCK" ] || MITM_MOCK="$_MITM_HERE/../../../shared/tools/maestro/mock.py"
MITM_MOCK="$(cd "$(dirname "$MITM_MOCK")" && pwd)/mock.py"
MITM_MOCKS="$(cd "$_MITM_HERE/../mocks" && pwd)"

# mitm_start <scenario> <logfile>
# mitm_start --record <host> <logfile>
#
# Sets MITM_PID. Returns non-zero (and prints the log) if the proxy never came up.
#
# The record form passes `--set record=<host>` instead of a scenario: mock.py
# then writes real responses to mocks/_recorded/ rather than serving fixtures.
# It is the same lifecycle either way, which is the point — the checkers that
# record used to carry their own copy of it.
mitm_start() {
  local mode="scenario" scenario="$1" log="$2" i
  if [ "$1" = "--record" ]; then
    mode="record"; scenario="$2"; log="$3"
  fi

  # Only our own mock.py: a looser pattern also matches another project's
  # mitmdump, and another checkout of this one.
  pkill -f "mitmdump.*mocks=${MITM_MOCKS}" >/dev/null 2>&1

  # pkill returns before the old process releases the port, so back-to-back
  # cases in a pack run died with "address already in use" before the flow even
  # started.
  for i in 1 2 3 4 5 6 7 8 9 10; do
    lsof -nP -iTCP:"$MITM_PORT" -sTCP:LISTEN >/dev/null 2>&1 || break
    sleep 0.5
  done

  mkdir -p "$(dirname "$log")"
  if [ "$mode" = "record" ]; then
    mitmdump -q --listen-port "$MITM_PORT" -s "$MITM_MOCK" --set mocks="$MITM_MOCKS" \
      --set record="$scenario" > "$log" 2>&1 &
  else
    mitmdump -q --listen-port "$MITM_PORT" -s "$MITM_MOCK" --set mocks="$MITM_MOCKS" \
      --set scenario="$scenario" > "$log" 2>&1 &
  fi
  MITM_PID=$!

  # Wait for the port to actually LISTEN instead of sleeping a fixed 2 s. The
  # old form only proved the process had not exited yet: on a cold start
  # mitmdump needs longer than 2 s to bind, and the first requests of the flow
  # then went straight to the network — the run silently tested live data
  # against fixture assertions.
  for i in $(seq 1 60); do
    if ! kill -0 "$MITM_PID" 2>/dev/null; then
      echo "mitmdump exited during startup (scenario=$scenario)"
      cat "$log"
      return 2
    fi
    # Ask whether OUR process holds the port, not whether anything does.
    # Scoping the pkill above to our own mock.py made this necessary: another
    # mitmdump (or anything else) may now be sitting on the port, our own
    # instance then fails to bind and dies — and a bare port check would see the
    # stranger's socket, report success, and send the whole run through someone
    # else's mocks.
    lsof -a -p "$MITM_PID" -nP -iTCP:"$MITM_PORT" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    sleep 0.25
  done

  echo "mitmdump did not start listening on port $MITM_PORT within 15s"
  cat "$log"
  kill "$MITM_PID" >/dev/null 2>&1
  return 2
}

# mitm_stop <serial> <logfile>
# Safe to call twice: the trap fires on every exit path.
mitm_stop() {
  local serial="$1" log="$2"
  kill "$MITM_PID" >/dev/null 2>&1
  wait "$MITM_PID" 2>/dev/null
  adb -s "$serial" shell settings put global http_proxy :0
  if [ -f "$log" ]; then
    echo "--- mock hits ---"
    grep -E 'MOCK HIT' "$log" | sort | uniq -c
  fi
}
