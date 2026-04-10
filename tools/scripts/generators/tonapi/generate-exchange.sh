#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "$SCRIPT_DIR/openapi/exchange.yaml" \
  "exchangeapi" \
  "tonapi:exchange"
