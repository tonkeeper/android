#!/bin/bash

# Usage: generate-tonapi.sh <openapi-url> <package-name> <target-module> [open-enums] [schema-mappings]
# Example: generate-tonapi.sh "https://..." "tonapi" "tonapi:tonkeeper"
# open-enums: "true" decodes an unknown string enum value as `unknown` instead of failing.
# schema-mappings: "Schema=fully.qualified.Type,..." to use instead of the generated model.

set -euo pipefail

OPENAPI_URL="${API_SPEC_PATH:-${1:-}}"
PACKAGE_NAME="${2:-}"
TARGET_MODULE="${3:-}"
OPEN_ENUMS="${4:-false}"
EXTRA_SCHEMA_MAPPINGS="${5:-}"

download_openapi_spec() {
    local source="$1"
    local destination="$2"

    if [[ "$source" =~ ^https?:// ]]; then
        echo "Downloading OpenAPI spec..."
        curl -fsSL "$source" -o "$destination"
    else
        cp "$source" "$destination"
    fi
}

if [ -z "$OPENAPI_URL" ] || [ -z "$PACKAGE_NAME" ] || [ -z "$TARGET_MODULE" ]; then
    echo "Usage: $0 <openapi-url> <package-name> <target-module> [open-enums]"
    echo "Example: $0 'https://...' 'tonapi' 'tonapi:tonkeeper'"
    exit 1
fi

# Determine project root and paths
# Physical, like `git rev-parse --show-toplevel`: the staged target is the module path relative to it.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
TARGET_MODULE_DIR="$PROJECT_ROOT/${TARGET_MODULE//://}"
if [ -d "$TARGET_MODULE_DIR/src/commonMain" ]; then
    TARGET_SRC_DIR="$TARGET_MODULE_DIR/src/commonMain/kotlin"
else
    TARGET_SRC_DIR="$TARGET_MODULE_DIR/src/main/kotlin"
fi
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT
STAGE=${API_GENERATION_STAGE:-$TEMP_DIR/stage}
PUBLISHER="$REPO_ROOT/shared/tools/api-generation.py"
[ -f "$PUBLISHER" ] || PUBLISHER="$REPO_ROOT/tools/api-generation.py"
TEMPLATE_DIR="$SCRIPT_DIR/templates"
LOCAL_OPENAPI_SPEC="$TEMP_DIR/openapi.json"

echo "Generating $PACKAGE_NAME for module $TARGET_MODULE..."
echo "  OpenAPI spec: $OPENAPI_URL"
echo "  Target directory: $TARGET_SRC_DIR"

download_openapi_spec "$OPENAPI_URL" "$TEMP_DIR/source.yaml"
SCHEMA_MAPPINGS=$(python3 "$SCRIPT_DIR/prepare-spec.py" "$TEMP_DIR/source.yaml" "$LOCAL_OPENAPI_SPEC" "$PACKAGE_NAME" "$OPEN_ENUMS")
if [ -n "$EXTRA_SCHEMA_MAPPINGS" ]; then
    SCHEMA_MAPPINGS="${SCHEMA_MAPPINGS:+$SCHEMA_MAPPINGS,}$EXTRA_SCHEMA_MAPPINGS"
fi
GENERATOR_ARGS=(--type-mappings "number=kotlin.String")
# Templates read openEnums as a section, where any string, "false" included, is true.
[ "$OPEN_ENUMS" = true ] && OPEN_ENUMS_PROPERTY=,openEnums=true
if [ -n "$SCHEMA_MAPPINGS" ]; then
    GENERATOR_ARGS+=(--schema-mappings "$SCHEMA_MAPPINGS")
fi

GENERATOR="$REPO_ROOT/shared/tools/openapi-generator.sh"
[ -f "$GENERATOR" ] || GENERATOR="$REPO_ROOT/tools/openapi-generator.sh"
if [ -f "$SCRIPT_DIR/configs/$PACKAGE_NAME.yaml" ]; then
    GENERATOR_ARGS+=(-c "$SCRIPT_DIR/configs/$PACKAGE_NAME.yaml")
fi
"$GENERATOR" generate \
  -i "$LOCAL_OPENAPI_SPEC" \
  -g kotlin \
  -o "$TEMP_DIR" \
  -t "$TEMPLATE_DIR" \
  --library jvm-okhttp4 \
  --global-property apiTests=false,modelTests=false \
  "${GENERATOR_ARGS[@]}" \
  --additional-properties "serializationLibrary=kotlinx_serialization,packageName=io.$PACKAGE_NAME,platform=android,hideGenerationTimestamp=true${OPEN_ENUMS_PROPERTY:-}"

# Post-process generated files
echo "Post-processing generated files..."
SED_IN_PLACE=(-i)
if [[ "$OSTYPE" == darwin* ]]; then
    SED_IN_PLACE+=('')
fi
# Parameter and argument lists are emitted with a trailing separator so that credentials declared
# as security schemes can be appended without knowing whether the operation declares any
# parameters of its own. Kotlin accepts the trailing comma; drop it anyway for readability.
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed "${SED_IN_PLACE[@]}" -e 's/, )/)/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 perl -0pi -e 's/(\@Serializable\n)\n+/$1/g'
# Remove unnecessary kotlin. prefixes
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed "${SED_IN_PLACE[@]}" \
    -e 's/kotlin\.collections\.List</List</g' \
    -e 's/kotlin\.collections\.Map</Map</g' \
    -e 's/kotlin\.String/String/g' \
    -e 's/kotlin\.Int/Int/g' \
    -e 's/kotlin\.Long/Long/g' \
    -e 's/kotlin\.Boolean/Boolean/g' \
    -e 's/kotlin\.Double/Double/g' \
    -e 's/kotlin\.Float/Float/g'

# Remove same-package imports
find "$TEMP_DIR" -type f -name "*.kt" -print0 | while IFS= read -r -d '' file; do
    # Extract package name from file
    package=$(awk '/^package / { print $2; exit }' "$file")
    if [ -n "$package" ]; then
        # Remove imports from the same package
        sed "${SED_IN_PLACE[@]}" "/^import ${package}\./d" "$file"
    fi
done

# Fix excessive blank lines
echo "Fixing spacing..."
find "$TEMP_DIR" -type f -name "*.kt" -print0 | while IFS= read -r -d '' file; do
    # Use awk to clean up excessive spacing
    awk '
        {
            # Ensure blank line after package declaration
            if (prev_line ~ /^package / && $0 ~ /^import /) {
                print ""
                print
                prev_line = $0
                next
            }

            # Clean up empty KDoc lines (/** or * followed by only whitespace)
            if ($0 ~ /^[[:space:]]*\*[[:space:]]*$/ && prev_line ~ /^[[:space:]]*\/\*\*/) next

            # Skip blank lines between constructor parameters (after comma, before @SerialName)
            if (NF == 0 && prev_line ~ /,$/) {
                # Buffer the blank line
                next_line = ""
                getline next_line
                # If next line is @SerialName, skip the blank
                if (next_line ~ /^[[:space:]]*@SerialName/) {
                    print next_line
                    prev_line = next_line
                    next
                }
                # Otherwise keep the blank
                print
                print next_line
                prev_line = next_line
                next
            }

            # Remove multiple consecutive blank lines
            if (NF == 0) {
                if (prev_blank) next
                prev_blank = 1
            } else {
                prev_blank = 0
            }

            # Remove blank line immediately after opening brace/paren
            if (prev_line ~ /[({]$/ && NF == 0) next

            # Remove blank line immediately before closing brace/paren
            if (NF == 0) {
                next_line = ""
                getline next_line
                if (next_line ~ /^[[:space:]]*[})]/) {
                    print next_line
                    prev_line = next_line
                    next
                }
                print
                print next_line
                prev_line = next_line
                next
            }

            print
            prev_line = $0
        }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"

    # Final pass: ensure no double blank lines remain
    perl -i -0pe 's/\n\n\n/\n\n/g' "$file"
done

GENERATED_SRC_PATH="$TEMP_DIR/src/main/kotlin/"
if [ ! -d "$GENERATED_SRC_PATH" ]; then
    echo "❌ Failed to find generated source directory: $GENERATED_SRC_PATH"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Infrastructure is shared from tonapi:core; publish only the generated package.
if [[ "$TARGET_MODULE" != "tonapi:core" ]]; then
    rm -rf "$GENERATED_SRC_PATH/io/${PACKAGE_NAME}/infrastructure"
fi

RELATIVE="${TARGET_SRC_DIR#"$REPO_ROOT"/}/io/$PACKAGE_NAME"
mkdir -p "$STAGE/$RELATIVE"
cp -R "$GENERATED_SRC_PATH/io/$PACKAGE_NAME/." "$STAGE/$RELATIVE/"
printf '%s\n' "$RELATIVE" >> "$STAGE/.api-generation-targets"
if [ -z "${API_GENERATION_STAGE:-}" ]; then
    if [ "${API_GENERATION_CHECK:-0}" = 1 ]; then
        python3 "$PUBLISHER" --check "$STAGE" "$REPO_ROOT"
    else
        python3 "$PUBLISHER" "$STAGE" "$REPO_ROOT"
    fi
fi

echo "✅ Done generating $PACKAGE_NAME"
