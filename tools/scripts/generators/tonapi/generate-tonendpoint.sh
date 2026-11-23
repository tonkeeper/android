#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/shared/api/tonkeeper.yaml"
[ -f "$SPEC" ] || SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/api/tonkeeper.yaml"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SPEC" \
  "tonendpointapi" \
  "tonapi:tonendpoint" \
  "false" \
  "" \
  "$(dirname "$SPEC")/filters/tonkeeper.yaml"
