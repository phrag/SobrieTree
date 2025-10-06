#!/bin/bash
set -e

if ! ~/Library/Android/sdk/platform-tools/adb devices | grep -q "emulator"; then
    echo "Starting emulator..."
    ~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro &
fi

echo "Waiting for emulator to be ready..."
~/Library/Android/sdk/platform-tools/adb wait-for-device

echo "Installing application..."
# Uninstall to avoid signature mismatch, then install the latest built APK
~/Library/Android/sdk/platform-tools/adb uninstall com.brewlog.android || true
# Prefer versioned APK produced by build.sh, fall back to debug
APK_PATH="BrewLog-0.0.3-dev.apk"
if [ ! -f "$APK_PATH" ]; then
  APK_PATH="BrewLog-0.0.2.apk"
fi
if [ -f "$APK_PATH" ]; then
  ~/Library/Android/sdk/platform-tools/adb install "$APK_PATH"
else
  ~/Library/Android/sdk/platform-tools/adb install android/app/build/outputs/apk/debug/app-debug.apk
fi

echo "Launching application..."
~/Library/Android/sdk/platform-tools/adb shell am start -n com.brewlog.android/.MainActivity

echo "Launch complete!"

echo "Displaying logcat..."
~/Library/Android/sdk/platform-tools/adb logcat