#SHELL := /bin/bash

all: android

android:
	@echo "Step: Generating Android builds"
	@echo "1: arm64-v8a"
	cargo ndk -t arm64-v8a build -p ur-registry-ffi --release
	@echo "2: armeabi-v7a"
	cargo ndk -t armeabi-v7a build -p ur-registry-ffi --release
	@echo "3: x86"
	cargo ndk -t x86 build -p ur-registry-ffi --release
	@echo "4: x86_64"
	cargo ndk -t x86_64 build -p ur-registry-ffi --release
	@echo "Android buildup"
