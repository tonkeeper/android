#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/shared/api/multichain.yaml"
[ -f "$SPEC" ] || SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/api/multichain.yaml"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SPEC" \
  "walletapi" \
  "tonapi:wallet" \
  "true"
