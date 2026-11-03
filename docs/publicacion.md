# Firma y publicación

## Recuperar y conservar la clave original

La APK publicada de Savia 2.0.0 utiliza un certificado Android Debug concreto.
Su huella pública SHA-256 es:

```text
020af9637260439e2781cacedd5b3c2dcdca578f8a99eb7a2684c19d82a82941
```

Necesitamos el archivo de claves del equipo que compiló esa APK. Si se utilizó
la configuración predeterminada, estará en `~/.android/debug.keystore` (Linux)
o `%USERPROFILE%\.android\debug.keystore` (Windows). El nombre y el certificado
Android Debug no bastan: otra instalación tendrá otra clave. El certificado
público de una APK no permite recuperar su clave privada.

Comprueba el archivo candidato sin compartirlo ni mostrar sus contraseñas:

```bash
keytool -list -v -keystore /ruta/privada/debug.keystore
```

La huella SHA-256 debe coincidir con la anterior (ignorando los separadores `:`).
Conserva una copia privada del archivo y sus credenciales en un lugar seguro,
además de los secretos de GitHub. No regeneres el archivo original, no lo subas
a Git y no cambies la huella del verificador para aceptar otra clave.

**Pendiente al preparar este cambio:** recuperar esa clave y configurar los
secretos. Hasta entonces, la publicación debe fallar. Una nueva clave no permite
actualizar la Savia instalada conservando su identidad y sus datos.

## Compilar una APK de publicación

Define estas variables en el entorno de la terminal, sin guardar sus valores
en el repositorio ni introducir contraseñas en el historial:

| Variable | Contenido |
| --- | --- |
| `SAVIA_KEYSTORE_FILE` | Ruta absoluta al archivo original |
| `SAVIA_STORE_PASSWORD` | Contraseña del almacén |
| `SAVIA_KEY_ALIAS` | Alias que contiene la clave original |
| `SAVIA_KEY_PASSWORD` | Contraseña de esa clave |

```bash
export ANDROID_HOME=/ruta/al/Android/Sdk
bash gradlew --no-daemon testDebugUnitTest assembleRelease lintRelease
bash scripts/verificar-apk.sh app/build/outputs/apk/release/app-release.apk 2.0.1
```

El ejemplo supone haber incrementado la versión a 2.0.1 y el código a 5.
El verificador necesita `ANDROID_HOME` (o `ANDROID_SDK_ROOT`), incluso si
Gradle localiza el SDK mediante `local.properties`.
Sin variables de firma se puede compilar una APK sin firmar para inspección;
no se sustituye la clave por la clave debug del equipo. Si se define solo una
parte de las variables, Gradle falla con un mensaje de configuración.

## Configurar GitHub Actions

En **Settings → Secrets and variables → Actions**, crea:

| Secreto | Contenido |
| --- | --- |
| `SAVIA_KEYSTORE_BASE64` | Archivo original codificado en base64 |
| `SAVIA_STORE_PASSWORD` | Contraseña del almacén |
| `SAVIA_KEY_ALIAS` | Alias original |
| `SAVIA_KEY_PASSWORD` | Contraseña de la clave |

Con GitHub CLI autenticado, desde este repositorio y con las variables locales
ya cargadas, puedes guardar los secretos sin imprimirlos:

```bash
base64 -w0 "$SAVIA_KEYSTORE_FILE" | gh secret set SAVIA_KEYSTORE_BASE64
printf '%s' "$SAVIA_STORE_PASSWORD" | gh secret set SAVIA_STORE_PASSWORD
printf '%s' "$SAVIA_KEY_ALIAS" | gh secret set SAVIA_KEY_ALIAS
printf '%s' "$SAVIA_KEY_PASSWORD" | gh secret set SAVIA_KEY_PASSWORD
```

Base64 no cifra la clave: trata ese valor como un secreto. El flujo reconstruye
el archivo temporal con permisos restringidos y lo elimina al terminar.

## Publicar una versión

1. En `app/build.gradle.kts`, cambia `versionName` (por ejemplo, `2.0.1`) e
   incrementa `versionCode` por encima del de **todas** las APK ya distribuidas.
   Savia 2.0.0 usa el código 4. Conserva `applicationId`.
2. Integra los cambios revisados en `main` con las comprobaciones en verde.
3. Crea y sube una etiqueta `vX.Y.Z` que coincida con `versionName`.
4. Actions ejecuta pruebas unitarias, compila debug y ejecuta lint. Después
   compila release, ejecuta su lint y verifica firma criptográfica, certificado,
   identificador, versión y código superior al de 2.0.0. El incremento respecto
   a versiones posteriores se revisa antes de etiquetar.
5. Solo entonces publica `Savia-X.Y.Z.apk` y su suma SHA-256 en Releases, con
   notas generadas. Una versión ya publicada no se sobrescribe.

No hace falta añadir las nuevas APK al repositorio. La APK histórica de
`descargas/` se conserva como referencia. No se ha creado ninguna etiqueta ni
publicado una nueva versión al preparar esta infraestructura.

Para comprobar los rechazos del verificador sin una clave privada:

```bash
bash scripts/probar-verificacion.sh
```

Antes de distribuir la primera actualización, instálala sobre Savia 2.0.0 en
un dispositivo de prueba con datos ficticios y comprueba que se conservan.
La firma compatible es necesaria; esa prueba también confirma el comportamiento
real de la actualización.
