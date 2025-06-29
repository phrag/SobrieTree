#!/bin/bash

# Build script for Android Rust library
set -e

echo "Building Rust library for Android..."

# Create jniLibs directory structure
mkdir -p android/app/src/main/jniLibs/arm64-v8a
mkdir -p android/app/src/main/jniLibs/armeabi-v7a
mkdir -p android/app/src/main/jniLibs/x86
mkdir -p android/app/src/main/jniLibs/x86_64

# Build for ARM64 (most common)
echo "Building for ARM64..."
cd rust
cargo build --target aarch64-linux-android --release

# Build for ARMv7
echo "Building for ARMv7..."
cargo build --target armv7-linux-androideabi --release

# Build for x86
echo "Building for x86..."
cargo build --target i686-linux-android --release

# Build for x86_64
echo "Building for x86_64..."
cargo build --target x86_64-linux-android --release

# Copy libraries to Android project
echo "Copying libraries to Android project..."
cp target/aarch64-linux-android/release/libbeer_tracker_core.so ../android/app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libbeer_tracker_core.so ../android/app/src/main/jniLibs/armeabi-v7a/
cp target/i686-linux-android/release/libbeer_tracker_core.so ../android/app/src/main/jniLibs/x86/
cp target/x86_64-linux-android/release/libbeer_tracker_core.so ../android/app/src/main/jniLibs/x86_64/

cd ..

echo "Build complete! Libraries copied to android/app/src/main/jniLibs/" 