#!/bin/bash

set -e

echo "🍺 Setting up Beer Tracker Development Environment"
echo "=================================================="

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    echo "❌ Rust is not installed."
    echo "📦 Installing Rust..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
    source ~/.cargo/env
    echo "✅ Rust installed successfully!"
else
    echo "✅ Rust is already installed: $(cargo --version)"
fi

# Check for uniffi-bindgen
if ! command -v uniffi-bindgen &> /dev/null; then
    echo "❌ uniffi-bindgen is not installed or not in your PATH."
    echo "➡️  Please download the latest release for macOS from:"
    echo "   https://github.com/mozilla/uniffi-rs/releases"
    echo "   Then move it to a directory in your PATH, e.g.:"
    echo "   chmod +x ~/Downloads/uniffi-bindgen-*"
    echo "   mv ~/Downloads/uniffi-bindgen-* ~/.cargo/bin/uniffi-bindgen"
    echo "   (or /usr/local/bin if you prefer)"
    echo "   Then re-run this script."
    exit 1
else
    echo "✅ uniffi-bindgen is installed: $(uniffi-bindgen --version 2>/dev/null || echo 'version unknown')"
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or later."
    echo "   You can download it from: https://adoptium.net/"
    exit 1
else
    echo "✅ Java is installed: $(java -version 2>&1 | head -n 1)"
fi

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME is not set."
    echo "📱 Please install Android Studio and set ANDROID_HOME:"
    echo "   1. Download Android Studio from: https://developer.android.com/studio"
    echo "   2. Install it and open it"
    echo "   3. Go to Settings/Preferences > Appearance & Behavior > System Settings > Android SDK"
    echo "   4. Copy the Android SDK Location path"
    echo "   5. Add this to your ~/.bashrc or ~/.zshrc:"
    echo "      export ANDROID_HOME=/path/to/your/android/sdk"
    echo "   6. Restart your terminal or run: source ~/.bashrc"
    exit 1
else
    echo "✅ Android SDK found at: $ANDROID_HOME"
fi

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "⚠️  ADB is not in PATH. Please add it to your PATH:"
    echo "   export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
    echo "   Add this to your ~/.bashrc or ~/.zshrc"
else
    echo "✅ ADB is available: $(adb version | head -n 1)"
fi

# Create necessary directories
echo "📁 Creating project directories..."
mkdir -p android/app/src/main/java/com/beertracker/core
mkdir -p android/app/src/main/jniLibs/arm64-v8a
mkdir -p android/app/src/main/jniLibs/armeabi-v7a
mkdir -p android/app/src/main/jniLibs/x86
mkdir -p android/app/src/main/jniLibs/x86_64

# Test Rust build
echo "🧪 Testing Rust build..."
cd rust
cargo check
cd ..

echo ""
echo "🎉 Setup completed successfully!"
echo ""
echo "📋 Next steps:"
echo "   1. Connect your Android device or start an emulator"
echo "   2. Run: ./build.sh"
echo "   3. Install the app: adb install android/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "🔧 Development commands:"
echo "   - Build: ./build.sh"
echo "   - Rust only: cd rust && cargo build"
echo "   - Android only: cd android && ./gradlew assembleDebug"
echo "   - Install on device: cd android && ./gradlew installDebug" 