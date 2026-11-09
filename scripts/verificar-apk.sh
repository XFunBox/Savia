#!/usr/bin/env bash
# Uso: bash scripts/verificar-apk.sh ruta.apk 2.3.0
set -euo pipefail
apk="${1:?Falta la ruta de la APK}"
version="${2:?Falta la versión esperada}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Versión inválida: usa X.Y.Z.'; exit 1; }
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${sdk:?Define ANDROID_HOME con la ruta del Android SDK para verificar la APK}"
tools="$sdk/build-tools/36.0.0"

# Certificado público de publicación; cambio de firma aprobado tras las pruebas 2.0.0.
root="$(cd "$(dirname "$0")/.." && pwd)"
expected="$(cat "$root/scripts/certificado-release.sha256")"
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Huella de publicación inválida.'; exit 1; }
certificate="$("$tools/apksigner" verify --verbose --print-certs "$apk")"
fingerprints="$(printf '%s\n' "$certificate" | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p')"
if [ "$fingerprints" != "$expected" ]; then
    echo 'El certificado no coincide con la firma de publicación de Savia. Publicación detenida.'
    exit 1
fi

package="$("$tools/aapt" dump badging "$apk" | sed -n '/^package: /p')"
[[ "$package" == *"name='io.github.xfunbox.savia'"* ]] || { echo 'Identificador de aplicación incorrecto.'; exit 1; }
[[ "$package" == *"versionName='$version'"* ]] || { echo 'La versión de la APK no coincide con la etiqueta.'; exit 1; }
code="$(printf '%s\n' "$package" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")"
[[ "$code" =~ ^[0-9]+$ ]] && (( 10#$code > 6 )) || { echo 'versionCode debe superar el 6 de Savia 2.2.0.'; exit 1; }
echo "APK verificada: Savia $version (versionCode $code), certificado de publicación."
