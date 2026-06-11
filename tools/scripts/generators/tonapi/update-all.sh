#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Regenerating Tonapi APIs..."
echo ""

echo "📦 Generating Tonapi Tonkeeper..."
bash "$SCRIPT_DIR/generate-tonapi.sh" \
  "https://raw.githubusercontent.com/tonkeeper/opentonapi/master/api/openapi.yml" \
  "tonapi" \
  "tonapi:tonkeeper"

echo ""
echo "🔋 Generating Battery API..."
bash "$SCRIPT_DIR/generate-battery.sh"

echo ""
echo "💱 Generating Exchange API..."
bash "$SCRIPT_DIR/generate-exchange.sh"

echo ""
echo "💱 Generating Trading API..."
bash "$SCRIPT_DIR/generate-trading.sh"

echo ""
echo "✅ All APIs generated successfully!"