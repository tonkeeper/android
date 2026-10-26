#!/usr/bin/env bash
#
# Generates EventDelegate1.kt and DefaultEventDelegate1.kt from OpenAPI analytics schemas.
#
# Usage:
#   ./generate_event_delegate.sh [openapi_dir]
#
# If openapi_dir is not provided, the script uses the analytics-schemas checkout shared
# with iOS (.context/ at the repository root), cloning or updating it first.
# Output:
#   lib/bus/src/main/kotlin/com/tonapps/bus/generated/EventDelegate1.kt
#   lib/bus/src/main/kotlin/com/tonapps/bus/generated/DefaultEventDelegate1.kt

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/lib/bus/src/main/kotlin/com/tonapps/bus/generated"

OPENAPI_DIR="${1:-}"
if [ -z "$OPENAPI_DIR" ]; then
    SHARED_TOOLS="$PROJECT_ROOT/tools"
    [ -f "$SHARED_TOOLS/analytics_schemas.sh" ] || SHARED_TOOLS="$PROJECT_ROOT/../shared/tools"
    OPENAPI_DIR="$("$SHARED_TOOLS/analytics_schemas.sh")/openapi-client"
fi

if [ ! -d "$OPENAPI_DIR" ]; then
    echo "Error: $OPENAPI_DIR is not a directory" >&2
    exit 1
fi

echo "Generating from $OPENAPI_DIR..."
python3 "$PROJECT_ROOT/tools/scripts/generators/analytic/generate_event_delegate.py" "$OPENAPI_DIR" "$OUTPUT_DIR"

echo "Done."
