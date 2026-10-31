#!/bin/sh
# Builds the signed APK and publishes it as a GitHub release; Merve on the TV
# picks it up on its next start. Needs keystore.properties + merve-release.jks.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(ls -d ~/.jdks/jdk-17*/Contents/Home | head -1)}" ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
GRADLE="${GRADLE:-$HOME/Android/gradle-8.11.1/bin/gradle}"
GH="${GH:-$HOME/Android/bin/gh}"
"$GRADLE" -q assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
NAME=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
cp "$APK" "/tmp/merve-$CODE.apk"
"$GH" release create "v$NAME" "/tmp/merve-$CODE.apk" --title "Merve $NAME" --notes "${1:-Merve $NAME}"
rm "/tmp/merve-$CODE.apk"
