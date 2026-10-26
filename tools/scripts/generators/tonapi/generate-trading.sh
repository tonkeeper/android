#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/shared/api/trading.yaml"
[ -f "$SPEC" ] || SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/api/trading.yaml"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SPEC" \
  "tradingapi" \
  "tonapi:trading" \
  "true"
