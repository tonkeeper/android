#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SCRIPT_DIR/openapi/perps.yaml" \
  "perpsapi" \
  "tonapi:perps"
