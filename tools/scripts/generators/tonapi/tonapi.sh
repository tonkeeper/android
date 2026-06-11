#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Regenerating Tonapi APIs..."
echo ""

echo "📦 Generating Tonapi Tonkeeper..."
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "https://raw.githubusercontent.com/tonkeeper/opentonapi/master/api/openapi.yml" \
  "tonapi" \
  "tonapi:tonkeeper"

