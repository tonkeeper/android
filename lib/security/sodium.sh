#!/bin/bash

# Exit script on error
set -e

# Save the current project path
project_path=$(pwd)

# Navigate to the NDK directory and get the latest version
pushd /Users/"$USER"/Library/Android/sdk/ndk/
lastNdk=$(ls | sort -V | tail -n 1)

# Create a directory for building libsodium
mkdir -p "$HOME/sodium_build"
pushd "$HOME/sodium_build"

# Download and extract the latest libsodium
curl -O https://download.libsodium.org/libsodium/releases/LATEST.tar.gz
tar -xvf LATEST.tar.gz
cd libsodium-stable

# Set ANDROID_NDK_HOME environment variable
export ANDROID_NDK_HOME=/Users/"$USER"/Library/Android/sdk/ndk/"$lastNdk"

# Build libsodium
./dist-build/android-aar.sh

# Move the generated .aar file to the project path
mv *.aar "$project_path"/libs/libsodium.aar

# Cleanup: Go back to home and remove the build directory
popd
rm -rf "$HOME/sodium_build"

# Return to the original project path
popd
