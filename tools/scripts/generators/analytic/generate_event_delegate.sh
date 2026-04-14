#!/usr/bin/env bash
#
# Generates EventDelegate1.kt and DefaultEventDelegate1.kt from OpenAPI analytics schemas.
#
# Usage:
#   ./generate_event_delegate.sh [openapi_dir]
#
# If openapi_dir is not provided, the script clones the repo into a temp dir.
# Output:
#   lib/bus/src/main/kotlin/com/tonapps/bus/generated/EventDelegate1.kt
#   lib/bus/src/main/kotlin/com/tonapps/bus/generated/DefaultEventDelegate1.kt

set -euo pipefail

PROJECT_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
OUTPUT_DIR="$PROJECT_ROOT/lib/bus/src/main/kotlin/com/tonapps/bus/generated"

OPENAPI_DIR="${1:-}"
TEMP_DIR=""

if [ -z "$OPENAPI_DIR" ]; then
    TEMP_DIR="$(mktemp -d)"
    echo "Cloning analytics-schemas..."
    git clone --depth 1 git@github.com:tonkeeper/analytics-schemas.git "$TEMP_DIR/analytics-schemas"
    OPENAPI_DIR="$TEMP_DIR/analytics-schemas/openapi"
fi

if [ ! -d "$OPENAPI_DIR" ]; then
    echo "Error: $OPENAPI_DIR is not a directory" >&2
    exit 1
fi

echo "Generating from $OPENAPI_DIR..."
python3 "$PROJECT_ROOT/tools/scripts/generators/analytic/generate_event_delegate.py" "$OPENAPI_DIR" "$OUTPUT_DIR"

# Clean up temp dir if we created one
if [ -n "$TEMP_DIR" ]; then
    rm -rf "$TEMP_DIR"
fi

echo "Done."
