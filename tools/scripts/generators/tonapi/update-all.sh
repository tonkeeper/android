#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Regenerating Tonapi APIs..."
echo ""

echo "📦 Generating Tonapi Tonkeeper..."
bash "$SCRIPT_DIR/tonapi.sh"

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
echo "📈 Generating Perps API..."
bash "$SCRIPT_DIR/generate-perps.sh"

echo ""
echo "🕯️ Generating Kandelabr API..."
bash "$SCRIPT_DIR/generate-kandelabr.sh"

echo ""
echo "🌐 Generating Multichain API..."
bash "$SCRIPT_DIR/generate-multichain.sh"

echo ""
echo "✅ All APIs generated successfully!"