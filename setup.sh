#!/bin/bash

# SobrieTree Setup Script
# This script sets up the development environment for the SobrieTree Android app

set -e

echo "🍺 Setting up SobrieTree development environment..."

# Check if we're in the right directory
if [ ! -f "rust/Cargo.toml" ] || [ ! -f "android/build.gradle" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p android/app/src/main/java/com/sobrietree/android
mkdir -p android/app/src/main/jniLibs/arm64-v8a
mkdir -p android/app/src/main/jniLibs/armeabi-v7a
mkdir -p android/app/src/main/jniLibs/x86
mkdir -p android/app/src/main/jniLibs/x86_64

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    echo "❌ Error: Rust is not installed. Please install Rust first:"
    echo "   https://rustup.rs/"
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed. Please install Java 11 or later."
    exit 1
fi

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  Warning: ANDROID_HOME is not set."
    echo "   Please set ANDROID_HOME to your Android SDK location."
    echo "   Example: export ANDROID_HOME=/path/to/android/sdk"
fi

# Check if Android NDK is available
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "⚠️  Warning: ANDROID_NDK_HOME is not set."
    echo "   Please set ANDROID_NDK_HOME to your Android NDK location."
    echo "   Example: export ANDROID_NDK_HOME=/path/to/android/ndk"
fi

# Install Rust Android targets
echo "🔧 Installing Rust Android targets..."
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add x86_64-linux-android

# Build Rust library for Android
echo "🔨 Building Rust library for Android..."
cd rust
cargo build --target aarch64-linux-android --release
cargo build --target armv7-linux-androideabi --release
cargo build --target i686-linux-android --release
cargo build --target x86_64-linux-android --release
cd ..

# Copy Rust libraries to Android project
echo "📋 Copying Rust libraries to Android project..."
cp rust/target/aarch64-linux-android/release/libsobrietree_core.so android/app/src/main/jniLibs/arm64-v8a/
cp rust/target/armv7-linux-androideabi/release/libsobrietree_core.so android/app/src/main/jniLibs/armeabi-v7a/
cp rust/target/i686-linux-android/release/libsobrietree_core.so android/app/src/main/jniLibs/x86/
cp rust/target/x86_64-linux-android/release/libsobrietree_core.so android/app/src/main/jniLibs/x86_64/

# Set up Gradle wrapper if it doesn't exist
if [ ! -f "android/gradlew" ]; then
    echo "📦 Setting up Gradle wrapper..."
    cd android
    gradle wrapper
    cd ..
fi

# Make Gradle wrapper executable
chmod +x android/gradlew

echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Set ANDROID_HOME to your Android SDK location"
echo "2. Set ANDROID_NDK_HOME to your Android NDK location"
echo "3. Run './build_android.sh' to build the Android app"
echo "4. Install the APK on your device or emulator"
echo ""
echo "For more information, see README.md" 