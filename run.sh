#!/bin/bash
set -e

if ! ~/Library/Android/sdk/platform-tools/adb devices | grep -q "emulator"; then
    echo "Starting emulator..."
    ~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro &
fi

echo "Waiting for emulator to be ready..."
~/Library/Android/sdk/platform-tools/adb wait-for-device

echo "Installing application..."
~/Library/Android/sdk/platform-tools/adb install -r android/app/build/outputs/apk/debug/app-debug.apk

echo "Launching application..."
~/Library/Android/sdk/platform-tools/adb shell am start -n com.brewlog.android/.MainActivity

echo "Launch complete!"

echo "Displaying logcat..."
~/Library/Android/sdk/platform-tools/adb logcat