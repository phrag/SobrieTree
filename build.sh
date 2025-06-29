#!/bin/bash

echo "�� Building BrewLog App"

# Build Rust library
cd rust
cargo build --release

# Copy library files to Android
mkdir -p ../android/app/src/main/jniLibs/arm64-v8a/
mkdir -p ../android/app/src/main/jniLibs/armeabi-v7a/
mkdir -p ../android/app/src/main/jniLibs/x86/
mkdir -p ../android/app/src/main/jniLibs/x86_64/

# Copy the compiled library (adjust path based on your target)
cp target/release/libbrewlog_core.so ../android/app/src/main/jniLibs/arm64-v8a/
cp target/release/libbrewlog_core.so ../android/app/src/main/jniLibs/armeabi-v7a/
cp target/release/libbrewlog_core.so ../android/app/src/main/jniLibs/x86/
cp target/release/libbrewlog_core.so ../android/app/src/main/jniLibs/x86_64/

cd ..

# Build Android app
cd android
./gradlew assembleDebug
cd ..

echo "✅ Build complete!"
