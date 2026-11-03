#!/usr/bin/env bash
# Uso: bash scripts/verificar-apk.sh ruta.apk 2.0.1
set -euo pipefail
apk="${1:?Falta la ruta de la APK}"
version="${2:?Falta la versión esperada}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Versión inválida: usa X.Y.Z.'; exit 1; }
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
tools="$sdk/build-tools/36.0.0"

# Huella pública extraída de Savia 2.0.0. No cambiar para aceptar una clave nueva.
expected='020af9637260439e2781cacedd5b3c2dcdca578f8a99eb7a2684c19d82a82941'
certificate="$("$tools/apksigner" verify --verbose --print-certs "$apk")"
fingerprints="$(printf '%s\n' "$certificate" | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p')"
if [ "$fingerprints" != "$expected" ]; then
    echo 'El certificado no coincide con Savia 2.0.0. Publicación detenida.'
    exit 1
fi

package="$("$tools/aapt" dump badging "$apk" | sed -n '/^package: /p')"
[[ "$package" == *"name='io.github.xfunbox.savia'"* ]] || { echo 'Identificador de aplicación incorrecto.'; exit 1; }
[[ "$package" == *"versionName='$version'"* ]] || { echo 'La versión de la APK no coincide con la etiqueta.'; exit 1; }
code="$(printf '%s\n' "$package" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")"
[[ "$code" =~ ^[0-9]+$ ]] && (( 10#$code > 4 )) || { echo 'versionCode debe superar el 4 de Savia 2.0.0.'; exit 1; }
echo "APK verificada: Savia $version (versionCode $code), certificado original."
