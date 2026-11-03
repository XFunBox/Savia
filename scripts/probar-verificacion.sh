#!/usr/bin/env bash
# Prueba los rechazos de publicación sin necesitar una clave privada.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
# Con SDK, comprueba también la firma real de la APK histórica: debe superar
# firma/identidad/versión y rechazarse únicamente por su código antiguo.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -x "$sdk/build-tools/36.0.0/apksigner" ]; then
  if result="$(bash "$root/scripts/verificar-apk.sh" "$root/descargas/Savia-2.0.0.apk" 2.0.0 2>&1)"; then
    echo 'ERROR: se ha aceptado volver a publicar la APK histórica'
    exit 1
  fi
  [ "$result" = 'versionCode debe superar el 4 de Savia 2.0.0.' ] || { printf '%s\n' "$result"; exit 1; }
  echo 'Firma e identidad de la APK histórica comprobadas con herramientas Android reales.'
else
  echo 'Sin SDK: comprobación criptográfica de APK histórica pendiente; se ejecuta en CI.'
fi
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
export ANDROID_HOME="$temporary/sdk"
mkdir -p "$ANDROID_HOME/build-tools/36.0.0"
cat > "$ANDROID_HOME/build-tools/36.0.0/apksigner" <<'STUB'
#!/usr/bin/env bash
if [ "${CASE:-}" = unsigned ]; then exit 1; fi
digest=020af9637260439e2781cacedd5b3c2dcdca578f8a99eb7a2684c19d82a82941
if [ "${CASE:-}" = wrong-key ]; then digest=otra-clave; fi
if [ "${CASE:-}" != missing-certificate ]; then
  echo "Signer #1 certificate SHA-256 digest: $digest"
fi
STUB
cat > "$ANDROID_HOME/build-tools/36.0.0/aapt" <<'STUB'
#!/usr/bin/env bash
id=io.github.xfunbox.savia
version=2.0.1
code=5
case "${CASE:-}" in
  wrong-package) id=otra.app ;;
  wrong-version) version=2.0.2 ;;
  old-code) code=4 ;;
esac
echo "package: name='$id' versionCode='$code' versionName='$version'"
STUB
chmod +x "$ANDROID_HOME/build-tools/36.0.0/"{aapt,apksigner}
bash "$root/scripts/verificar-apk.sh" prueba.apk 2.0.1
for scenario in unsigned wrong-key missing-certificate wrong-package wrong-version old-code; do
  if CASE="$scenario" bash "$root/scripts/verificar-apk.sh" prueba.apk 2.0.1 > "$temporary/result" 2>&1; then
    echo "ERROR: se ha aceptado el caso $scenario"
    exit 1
  fi
done
if bash "$root/scripts/verificar-apk.sh" prueba.apk '../version' > "$temporary/result" 2>&1; then
  echo 'ERROR: se ha aceptado una versión inválida'
  exit 1
fi
echo 'Controles de publicación: 1 aceptación y 7 rechazos correctos.'
