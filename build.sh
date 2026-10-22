#!/usr/bin/env bash
# Compila e assina o APK sem Android SDK/Gradle, usando:
#   aapt2 (Debian) + javac (JDK) + D8/R8 (jar) + zipalign + apksigner
set -euo pipefail

cd "$(dirname "$0")"

if [ -f tools/android.jar ]; then
    DEFAULT_ANDROID_JAR="$PWD/tools/android.jar"
    DEFAULT_R8_JAR="$PWD/tools/r8.jar"
else
    DEFAULT_ANDROID_JAR="/tmp/opencode/android-sdk/android-34/android.jar"
    DEFAULT_R8_JAR="/tmp/opencode/r8.jar"
fi
ANDROID_JAR="${ANDROID_JAR:-$DEFAULT_ANDROID_JAR}"
R8_JAR="${R8_JAR:-$DEFAULT_R8_JAR}"
MIN_SDK=26
TARGET_SDK=34
VERSION_FILE="version.txt"
[ -f "$VERSION_FILE" ] || { echo "version.txt nao encontrado" >&2; exit 1; }
VERSION_NAME="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [[ ! "$VERSION_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "version.txt deve conter X.Y.Z (encontrado: '$VERSION_NAME')" >&2
    exit 1
fi
VERSION_CODE=$((10#${BASH_REMATCH[1]} * 10000 + 10#${BASH_REMATCH[2]} * 100 + 10#${BASH_REMATCH[3]}))
OUT="out/mostradores-${VERSION_NAME}-debug.apk"

for tool in aapt2 javac jar keytool zipalign apksigner unzip zip; do
    command -v "$tool" >/dev/null || { echo "faltando: $tool" >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "android.jar nao encontrado: $ANDROID_JAR" >&2; exit 1; }
[ -f "$R8_JAR" ] || { echo "r8.jar nao encontrado: $R8_JAR" >&2; exit 1; }

echo "== limpando =="
rm -rf build out
mkdir -p build/gen build/classes build/dex out

echo "== aapt2 compile =="
aapt2 compile --dir res -o build/res.zip

echo "== aapt2 link =="
aapt2 link \
    -o build/base.apk \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --java build/gen \
    build/res.zip

echo "== javac =="
find src build/gen -name '*.java' > build/sources.txt
javac --release 8 -Xlint:-options \
    -classpath "$ANDROID_JAR" \
    -d build/classes \
    @build/sources.txt

echo "== jar =="
jar cf build/classes.jar -C build/classes .

echo "== d8 (dex) =="
java -cp "$R8_JAR" com.android.tools.r8.D8 \
    --release \
    --min-api "$MIN_SDK" \
    --lib "$ANDROID_JAR" \
    --output build/dex \
    build/classes.jar

echo "== empacotando =="
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk

echo "== keystore de debug =="
if [ ! -f tools/debug.keystore ]; then
    keytool -genkeypair -v \
        -keystore tools/debug.keystore \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=BR" >/dev/null
fi

echo "== assinando =="
apksigner sign \
    --ks tools/debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$OUT" \
    build/aligned.apk

echo "== verificando =="
apksigner verify --verbose "$OUT" | head -6
ls -lh "$OUT"
