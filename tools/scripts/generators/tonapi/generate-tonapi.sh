#!/bin/bash

# Usage: generate-tonapi.sh <openapi-url> <package-name> <target-module>
# Example: generate-tonapi.sh "https://..." "tonapi" "tonapi:tonkeeper"

OPENAPI_URL="$1"
PACKAGE_NAME="$2"
TARGET_MODULE="$3"

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

patch_wallet_bulk_header() {
    local spec_path="$1"
    local temp_path="${spec_path}.tmp"

    if ! grep -q '^  /v2/pubkeys/wallets/_bulk:$' "$spec_path"; then
        echo "⚠️  Wallet bulk path not found in schema, skipping F header patch."
        return 0
    fi

    awk '
        BEGIN {
            in_path = 0
            in_post = 0
            in_parameters = 0
            has_parameters = 0
            has_f_header = 0
            inserted = 0
        }

        function insert_f_header_param() {
            print "        - in: header"
            print "          name: F"
            print "          schema:"
            print "            type: string"
            print "          required: false"
            inserted = 1
        }

        /^  \/v2\/pubkeys\/wallets\/_bulk:$/ {
            in_path = 1
            print
            next
        }

        in_path && /^  \// {
            if (in_post && !inserted && !has_f_header && !has_parameters) {
                print "      parameters:"
                insert_f_header_param()
            }

            in_path = 0
            in_post = 0
            in_parameters = 0
            has_parameters = 0
            has_f_header = 0
            inserted = 0
        }

        {
            if (in_path && /^    post:$/) {
                in_post = 1
                print
                next
            }

            if (in_post && /^      parameters:$/) {
                has_parameters = 1
                in_parameters = 1
                print
                next
            }

            if (in_post && in_parameters && /^      [^ ]/ && $0 !~ /^      - /) {
                in_parameters = 0
                if (!inserted && !has_f_header) {
                    insert_f_header_param()
                }
            }

            if (in_post && in_parameters && /^          name: F$/) {
                has_f_header = 1
            }

            if (in_post && !has_parameters && !inserted && ($0 ~ /^      requestBody:$/ || $0 ~ /^      responses:$/)) {
                print "      parameters:"
                insert_f_header_param()
            }

            print
        }

        END {
            if (in_post && !inserted && !has_f_header && !has_parameters) {
                print "      parameters:"
                insert_f_header_param()
            }
        }
    ' "$spec_path" > "$temp_path" && mv "$temp_path" "$spec_path"
}

if [ -z "$OPENAPI_URL" ] || [ -z "$PACKAGE_NAME" ] || [ -z "$TARGET_MODULE" ]; then
    echo "Usage: $0 <openapi-url> <package-name> <target-module>"
    echo "Example: $0 'https://...' 'tonapi' 'tonapi:tonkeeper'"
    exit 1
fi

# Determine project root and paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
TARGET_SRC_DIR="$PROJECT_ROOT/${TARGET_MODULE//:///}/src/main/kotlin"
TEMP_DIR="$PROJECT_ROOT/temp_api_gen"
TEMPLATE_DIR="$SCRIPT_DIR/templates"
LOCAL_OPENAPI_SPEC="$TEMP_DIR/openapi.yaml"

echo "Generating $PACKAGE_NAME for module $TARGET_MODULE..."
echo "  OpenAPI spec: $OPENAPI_URL"
echo "  Target directory: $TARGET_SRC_DIR"

rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

download_openapi_spec "$OPENAPI_URL" "$LOCAL_OPENAPI_SPEC"
if [ $? -ne 0 ]; then
    echo "❌ Failed to download/read OpenAPI spec."
    rm -rf "$TEMP_DIR"
    exit 1
fi

patch_wallet_bulk_header "$LOCAL_OPENAPI_SPEC"
if [ $? -ne 0 ]; then
    echo "❌ Failed to patch OpenAPI spec."
    rm -rf "$TEMP_DIR"
    exit 1
fi

openapi-generator generate \
  -i "$LOCAL_OPENAPI_SPEC" \
  -g kotlin \
  -o "$TEMP_DIR" \
  -t "$TEMPLATE_DIR" \
  --library jvm-okhttp4 \
  --global-property apiTests=false,modelTests=false \
  --type-mappings "number=kotlin.String" \
  --additional-properties "serializationLibrary=kotlinx_serialization,packageName=io.$PACKAGE_NAME,platform=android,hideGenerationTimestamp=true"

if [ $? -ne 0 ]; then
    echo "❌ Failed to generate API client."
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Post-process generated files
echo "Post-processing generated files..."
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin.collections.Map<kotlin.String, kotlin.Any>/kotlin.collections.Map<kotlin.String, io.JsonAny>/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/@SerialName(value = "unknown_default_open_api")/@SerialName(value = "unknown")/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/unknown_default_open_api("unknown_default_open_api")/unknown("unknown")/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/.unknown_default_open_api/.unknown/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/values()/entries/g'

# Remove unnecessary kotlin. prefixes
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.collections\.List</List</g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.collections\.Map</Map</g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.String/String/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.Int/Int/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.Long/Long/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.Boolean/Boolean/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.Double/Double/g'
find "$TEMP_DIR" -type f -name "*.kt" -print0 | xargs -0 sed -i '' 's/kotlin\.Float/Float/g'

# Remove same-package imports
find "$TEMP_DIR" -type f -name "*.kt" -print0 | while IFS= read -r -d '' file; do
    # Extract package name from file
    package=$(grep '^package ' "$file" | sed 's/^package //' | tr -d '\n')
    if [ -n "$package" ]; then
        # Remove imports from the same package
        sed -i '' "/^import ${package}\./d" "$file"
    fi
done

# Simplify nested class references (ClassName.NestedClass -> NestedClass within ClassName)
find "$TEMP_DIR" -type f -name "*.kt" -print0 | while IFS= read -r -d '' file; do
    # Extract class name from data class or enum class declarations
    classname=$(grep -E '^(data class|enum class|class) [A-Z]' "$file" | head -1 | sed -E 's/.*(data class|enum class|class) ([A-Za-z0-9_]+).*/\2/')
    if [ -n "$classname" ]; then
        # Replace ClassName.NestedClass with just NestedClass in property types
        sed -i '' "s/: ${classname}\./: /g" "$file"
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

# Copy to target module
echo "Copying to target module..."
mkdir -p "$TARGET_SRC_DIR"
cp -r "$GENERATED_SRC_PATH"* "$TARGET_SRC_DIR/"
if [ $? -ne 0 ]; then
    echo "❌ Failed to copy generated sources to target directory: $TARGET_SRC_DIR"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Remove infrastructure if not core module
if [[ "$TARGET_MODULE" != "tonapi:core" ]]; then
    INFRA_DIR="$TARGET_SRC_DIR/io/infrastructure"
    if [ -d "$INFRA_DIR" ]; then
        echo "Removing infrastructure directory (not core module)..."
        rm -rf "$INFRA_DIR"
    fi
fi

# Format with detekt
DETEKT_VERSION="1.23.8"
GIT_COMMON_DIR="$(git rev-parse --git-common-dir)"
TOOLS_BUILD_DIR="$GIT_COMMON_DIR/detekt-cache"
DETEKT_JAR="$TOOLS_BUILD_DIR/detekt-formatting-${DETEKT_VERSION}.jar"
if [ -f "$DETEKT_JAR" ]; then
    echo "Formatting with detekt..."
    java -jar "$DETEKT_JAR" --input "$TARGET_SRC_DIR" --auto-correct 2>/dev/null || echo "⚠️  Detekt formatting skipped (not available)"
else
    echo "⚠️  Detekt formatting jar not found at $DETEKT_JAR"
fi

rm -rf "$TEMP_DIR"

echo "✅ Done generating $PACKAGE_NAME"
