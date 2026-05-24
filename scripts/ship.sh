#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
APK="$PROJECT_DIR/android/build/outputs/apk/debug/android-debug.apk"

echo "=== Building Android debug APK ==="
cd "$PROJECT_DIR"
./gradlew :android:assembleDebug

echo "=== Installing on connected device ==="
"$ADB" install -r "$APK"

echo "=== Launching app ==="
"$ADB" shell am start -n com.odyssey.innovationodyssey/com.odyssey.AndroidLauncher

echo "=== Done ==="