#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
PLATFORM="$SDK/platforms/android-30/android.jar"
BT30="$SDK/build-tools/30.0.3"
BT34="$SDK/build-tools/34.0.0"
AAPT="$BT30/aapt"
ZIPALIGN="$BT30/zipalign"

if [ -x "$BT34/d8" ]; then
  D8="$BT34/d8"
elif [ -x "$SDK/build-tools/36.0.0/d8" ]; then
  D8="$SDK/build-tools/36.0.0/d8"
else
  D8="$BT30/d8"
fi

JSch="libs/jsch-0.2.26-android.jar"
SHIZUKU="libs/shizuku"
CP="$SHIZUKU/api/classes.jar:$SHIZUKU/provider/classes.jar:$SHIZUKU/aidl-13/classes.jar:$SHIZUKU/shared-13/classes.jar:$JSch"

for f in "$PLATFORM" "$AAPT" "$ZIPALIGN" "$D8" "$JSch"; do
  if [ ! -e "$f" ]; then
    echo "missing: $f" >&2
    exit 1
  fi
done

rm -rf build
mkdir -p build/gen build/classes build/dex

"$AAPT" package -f -m -J build/gen \
  -M AndroidManifest.xml -S res -I "$PLATFORM"

find src build/gen -name '*.java' | sort > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$PLATFORM" -classpath "$CP" \
  -d build/classes @build/sources.txt

find build/classes -name '*.class' | sort > build/classes.txt
"$D8" --lib "$PLATFORM" --min-api 24 --output build/dex \
  @build/classes.txt \
  "$SHIZUKU/api/classes.jar" \
  "$SHIZUKU/provider/classes.jar" \
  "$SHIZUKU/aidl-13/classes.jar" \
  "$SHIZUKU/shared-13/classes.jar" \
  "$JSch"

"$AAPT" package -f -M AndroidManifest.xml -S res -I "$PLATFORM" \
  -F build/app-unsigned.apk

python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/app-unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex', 'classes.dex')
PY

"$ZIPALIGN" -f 4 build/app-unsigned.apk build/TE-cvb-unsigned.apk
echo "built: build/TE-cvb-unsigned.apk"
