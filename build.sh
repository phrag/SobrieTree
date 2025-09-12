#!/bin/bash

echo "�� Building BrewLog App"

# Install Android target for Rust (if not already installed)
rustup target add aarch64-linux-android

# Build Rust library for Android via cargo-ndk if available
cd rust
if command -v cargo-ndk >/dev/null 2>&1; then
  cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release
else
  echo "ℹ️ cargo-ndk not found; falling back to direct cargo build (NDK setup required)"
  cargo build --release --target aarch64-linux-android
  mkdir -p ../android/app/src/main/jniLibs/arm64-v8a/
  cp target/aarch64-linux-android/release/libbrewlog_core.so ../android/app/src/main/jniLibs/arm64-v8a/ 2>/dev/null || true
fi

# Copy library files to Android
mkdir -p ../android/app/src/main/jniLibs/arm64-v8a/

cd ..

# Build Android app
cd android
./gradlew assembleDebug

# Copy latest APK to repo root
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
  cp -f "$APK_PATH" ../BrewLog-debug.apk
  echo "📦 Copied APK to $(pwd)/../BrewLog-debug.apk"
else
  echo "⚠️ APK not found at $APK_PATH"
fi

cd ..

echo "✅ Build complete!"

# Cleanup: prune Rust target and stale jniLibs ABIs to keep repo lean (set PRUNE=0 to skip)
if [ -z "$PRUNE" ] || [ "$PRUNE" = "1" ]; then
  echo "🧹 Pruning Rust target and stale jniLibs..."
  (cd rust && cargo clean)
  find android/app/src/main/jniLibs -mindepth 1 -maxdepth 1 -type d ! -name 'arm64-v8a' -exec rm -rf {} + 2>/dev/null || true
fi
