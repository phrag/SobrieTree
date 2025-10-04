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

# Build Android app (release preferred to embed version in filename)
cd android
# Read versionName from Gradle to construct the expected filename (BSD sed compatible)
VERSION=$(awk -F '"' '/versionName[[:space:]]*"/{print $2; exit}' app/build.gradle)
if [ -z "$VERSION" ]; then
  echo "⚠️ Could not parse versionName from app/build.gradle; defaulting to 0.0.0"
  VERSION="0.0.0"
fi
./gradlew assembleRelease || ./gradlew assembleDebug

# Prefer Release artifact named by outputs config; fall back to Debug
REL_APK="app/build/outputs/apk/release/BrewLog-${VERSION}.apk"
DBG_APK="app/build/outputs/apk/debug/app-debug.apk"
DEST="../BrewLog-${VERSION}.apk"
if [ -f "$REL_APK" ]; then
  cp -f "$REL_APK" "$DEST"
  echo "📦 Copied APK to $(pwd)/$DEST"
elif [ -f "$DBG_APK" ]; then
  cp -f "$DBG_APK" "$DEST"
  echo "📦 Copied APK (debug) to $(pwd)/$DEST"
else
  echo "⚠️ APK not found (checked $REL_APK and $DBG_APK)"
fi

cd ..

echo "✅ Build complete!"

# Cleanup: prune Rust target and stale jniLibs ABIs to keep repo lean (set PRUNE=0 to skip)
if [ -z "$PRUNE" ] || [ "$PRUNE" = "1" ]; then
  echo "🧹 Pruning Rust target and stale jniLibs..."
  (cd rust && cargo clean)
  find android/app/src/main/jniLibs -mindepth 1 -maxdepth 1 -type d ! -name 'arm64-v8a' -exec rm -rf {} + 2>/dev/null || true
fi
