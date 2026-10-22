#!/usr/bin/env bash
# Builda o APK no WSL usando o SDK/JDK do Windows via interop.
#
# O build.sh espera ferramentas POSIX na PATH. Como o SDK/JDK instalados sao
# binarios .exe, este script cria um diretorio temporario com symlinks sem
# extensao (aapt2, javac, ...), um shim para o apksigner (que so existe como
# .jar) e, se necessario, um shim de zip. Depois chama o build.sh.
#
# Uso: tools/wsl-build.sh
# Overrides: ANDROID_SDK, JAVA_HOME, ANDROID_JAR, R8_JAR
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v wslpath >/dev/null 2>&1; then
    echo "tools/wsl-build.sh: requer WSL (wslpath nao encontrado)" >&2
    exit 1
fi

SDK="${ANDROID_SDK:-/mnt/c/Desenv/Android/Sdk}"
JAVA_HOME="${JAVA_HOME:-/mnt/c/Desenv/JDKs/jdk-21.0.7}"
if [ ! -x "$JAVA_HOME/bin/java.exe" ]; then
    JAVA_HOME="/mnt/c/Desenv/JDKs/jdk-21.0.7"
fi
JDK_BIN="$JAVA_HOME/bin"

[ -d "$SDK" ] || { echo "SDK nao encontrado: $SDK" >&2; exit 1; }
[ -x "$JDK_BIN/java.exe" ] || { echo "JDK nao encontrado: $JDK_BIN" >&2; exit 1; }

BT="$SDK/build-tools/34.0.0"
if [ ! -d "$BT" ]; then
    BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
    BT="${BT%/}"
fi
[ -n "$BT" ] && [ -d "$BT" ] || {
    echo "build-tools nao encontrado em $SDK/build-tools" >&2
    exit 1
}
APKSIGNER_JAR="$BT/lib/apksigner.jar"
[ -f "$APKSIGNER_JAR" ] || {
    echo "apksigner.jar nao encontrado: $APKSIGNER_JAR" >&2
    exit 1
}

BIN="$(mktemp -d /tmp/mostradores-wsl-tools.XXXXXX)"
trap 'rm -rf "$BIN"' EXIT

link() {
    local nome="$1" origem="$2"
    [ -x "$origem" ] || { echo "ferramenta nao encontrada: $origem" >&2; exit 1; }
    ln -sf "$origem" "$BIN/$nome"
}

link aapt2 "$BT/aapt2.exe"
link zipalign "$BT/zipalign.exe"
for t in java javac jar keytool; do
    link "$t" "$JDK_BIN/$t.exe"
done

APKSIGNER_WIN="$(wslpath -w "$APKSIGNER_JAR")"
APKSIGNER_WIN="${APKSIGNER_WIN//\\//}"
cat > "$BIN/apksigner" <<EOF
#!/bin/sh
exec "$JDK_BIN/java.exe" -jar "$APKSIGNER_WIN" "\$@"
EOF
chmod +x "$BIN/apksigner"

if ! command -v zip >/dev/null 2>&1; then
    cat > "$BIN/zip" <<'PY'
#!/usr/bin/env python3
import os
import sys
import zipfile

args = [a for a in sys.argv[1:] if not a.startswith('-')]
if len(args) < 2:
    sys.exit('zip shim: uso: zip [-q] [-j] arquivo entrada...')
arquivo, entradas = args[0], args[1:]
with zipfile.ZipFile(arquivo, 'a', zipfile.ZIP_DEFLATED) as z:
    existentes = set(z.namelist())
    for entrada in entradas:
        nome = os.path.basename(entrada)
        if nome in existentes:
            z.writestr(nome, open(entrada, 'rb').read())
        else:
            z.write(entrada, nome)
PY
    chmod +x "$BIN/zip"
fi

# javac.exe/aapt2.exe/java.exe nao entendem /mnt/c/...; use caminhos relativos.
export PATH="$BIN:$PATH"
export ANDROID_JAR="${ANDROID_JAR:-tools/android.jar}"
export R8_JAR="${R8_JAR:-tools/r8.jar}"

./build.sh "$@"
