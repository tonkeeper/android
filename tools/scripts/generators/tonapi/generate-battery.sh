#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/shared/api/battery.yaml"
[ -f "$SPEC" ] || SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/api/battery.yaml"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SPEC" \
  "batteryapi" \
  "tonapi:battery" \
  "true"
