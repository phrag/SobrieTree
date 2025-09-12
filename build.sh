#!/bin/bash

echo "�� Building BrewLog App"

# Install Android target for Rust (if not already installed)
rustup target add aarch64-linux-android

# Build Rust library for Android
cd rust
cargo build --release --target aarch64-linux-android

# Copy library files to Android
mkdir -p ../android/app/src/main/jniLibs/arm64-v8a/
# mkdir -p ../android/app/src/main/jniLibs/armeabi-v7a/
# mkdir -p ../android/app/src/main/jniLibs/x86/
# mkdir -p ../android/app/src/main/jniLibs/x86_64/

# Copy the compiled library for arm64-v8a
cp target/aarch64-linux-android/release/libbrewlog_core.so ../android/app/src/main/jniLibs/arm64-v8a/
# cp target/aarch64-linux-android/release/libbrewlog_core.so ../android/app/src/main/jniLibs/armeabi-v7a/
# cp target/aarch64-linux-android/release/libbrewlog_core.so ../android/app/src/main/jniLibs/x86/
# cp target/aarch64-linux-android/release/libbrewlog_core.so ../android/app/src/main/jniLibs/x86_64/

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
