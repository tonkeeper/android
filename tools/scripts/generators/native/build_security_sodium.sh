#!/usr/bin/env bash
#
# Builds lib/security release native libraries (libsodium.so + libsodium_jni.so)
# and copies them into lib/security/src/main/jniLibs/<abi>/.
#
# Usage:
#   ./build_security_sodium.sh

set -euo pipefail

PROJECT_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
MODULE_DIR="$PROJECT_ROOT/lib/security"
SRC_DIR="$MODULE_DIR/build/intermediates/cmake/release/obj"
DST_DIR="$MODULE_DIR/src/main/jniLibs"
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
LIBS=(libsodium.so libsodium_jni.so)

echo "Building :lib:security release native libs..."
"$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" :lib:security:externalNativeBuildRelease

if [ ! -d "$SRC_DIR" ]; then
    echo "Error: build output not found at $SRC_DIR" >&2
    exit 1
fi

echo "Copying .so files to $DST_DIR..."
for abi in "${ABIS[@]}"; do
    mkdir -p "$DST_DIR/$abi"
    for lib in "${LIBS[@]}"; do
        src="$SRC_DIR/$abi/$lib"
        if [ ! -f "$src" ]; then
            echo "Error: missing $src" >&2
            exit 1
        fi
        cp "$src" "$DST_DIR/$abi/$lib"
    done
done

echo "Done."
