#!/usr/bin/env bash
# Builds the debug APK and runs the engine tests on a clean CI runner.
# Everything is executed in one script so that a failure always ends up in the captured log.
set -euxo pipefail

echo "=== toolchain ==="
java -version || true
echo "JAVA_HOME=${JAVA_HOME:-}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-} ANDROID_HOME=${ANDROID_HOME:-}"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ -n "$SDK" ] && [ -x "$SDKMANAGER" ]; then
  (yes | "$SDKMANAGER" --licenses > /dev/null 2>&1) || true
  "$SDKMANAGER" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi
ls "$SDK/platforms" || true
ls "$SDK/build-tools" || true

echo "=== gradle ==="
curl -sSL -o gradle.zip https://services.gradle.org/distributions/gradle-8.13-bin.zip
unzip -q -o gradle.zip
export PATH="$PWD/gradle-8.13/bin:$PATH"
gradle --version

echo "=== engine tests ==="
gradle --no-daemon --console=plain :core:test

echo "=== apk ==="
gradle --no-daemon --console=plain :app:assembleDebug
ls -la app/build/outputs/apk/debug/
