#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/shared/api/kandelabr.yaml"
[ -f "$SPEC" ] || SPEC="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)/api/kandelabr.yaml"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SPEC" \
  "kandelabrapi" \
  "tonapi:kandelabr" \
  "false" \
  "" \
  "$(dirname "$SPEC")/filters/kandelabr.yaml"
