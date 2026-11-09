# Firma y publicación

## Clave definitiva y transición desde las pruebas

La APK de pruebas Savia 2.0.0 utilizaba una clave Android Debug de otro equipo.
Se ha aprobado sustituirla por una clave de publicación propia, puesto que solo
se distribuyó a testers. Su certificado público SHA-256 está fijado en
`scripts/certificado-release.sha256`:

```text
689a854dd9e2f2a8c862da8a4c678ca31e4c63c5a3bcc3db04fcb2ac64dccc83
```

La clave privada se conserva fuera del repositorio y en secretos de GitHub.
Mantén una copia privada del almacén y sus credenciales en un lugar seguro.
No regeneres la clave para cada versión ni subas secretos a Git.

**Transición única:** la nueva firma no permite actualizar directamente la APK
2.0.0. Los testers deben exportar sus datos en JSON y guardar la copia fuera de
la aplicación, desinstalar la versión antigua, instalar la nueva e importar la
copia. No desinstalar antes de comprobar que la copia está disponible. Las
versiones posteriores conservarán esta nueva firma para admitir actualizaciones.
La APK histórica usa la huella
`020af9637260439e2781cacedd5b3c2dcdca578f8a99eb7a2684c19d82a82941`;
el verificador la rechaza de forma intencionada.

## Compilar una APK de publicación

Define estas variables en el entorno de la terminal, sin guardar sus valores
en el repositorio ni introducir contraseñas en el historial:

| Variable | Contenido |
| --- | --- |
| `SAVIA_KEYSTORE_FILE` | Ruta absoluta al almacén de publicación |
| `SAVIA_STORE_PASSWORD` | Contraseña del almacén |
| `SAVIA_KEY_ALIAS` | Alias que contiene la clave de publicación |
| `SAVIA_KEY_PASSWORD` | Contraseña de esa clave |

```bash
export ANDROID_HOME=/ruta/al/Android/Sdk
bash gradlew --no-daemon testDebugUnitTest assembleRelease lintRelease
bash scripts/verificar-apk.sh app/build/outputs/apk/release/app-release.apk 2.3.0
```

La versión preparada es 2.3.0 con `versionCode` 7. El verificador rechaza
códigos anteriores o iguales a 6, usados por Savia 2.2.0.
El verificador necesita `ANDROID_HOME` (o `ANDROID_SDK_ROOT`), incluso si
Gradle localiza el SDK mediante `local.properties`.
Sin variables de firma se puede compilar una APK sin firmar para inspección;
no se sustituye la clave por la clave debug del equipo. Si se define solo una
parte de las variables, Gradle falla con un mensaje de configuración.

## Configurar GitHub Actions

En **Settings → Secrets and variables → Actions**, crea:

| Secreto | Contenido |
| --- | --- |
| `SAVIA_KEYSTORE_BASE64` | Almacén de publicación codificado en base64 |
| `SAVIA_STORE_PASSWORD` | Contraseña del almacén |
| `SAVIA_KEY_ALIAS` | Alias de publicación |
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

1. En `app/build.gradle.kts`, cambia `versionName` e incrementa `versionCode`
   por encima del de **todas** las APK ya distribuidas. Savia 2.3.0 usa el
   código 7. Conserva `applicationId`.
2. Integra los cambios revisados en `main` con las comprobaciones en verde.
3. Crea y sube una etiqueta `vX.Y.Z` que coincida con `versionName`.
4. Actions ejecuta pruebas unitarias, compila debug y ejecuta lint. Después
   compila release, ejecuta su lint y verifica firma criptográfica, certificado,
   identificador, versión y código superior al de 2.2.0. El incremento respecto
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

Antes de distribuir la primera versión con la nueva firma, comprueba la
exportación desde 2.0.0 e importación en la nueva instalación con datos ficticios.
Después comprueba que una segunda APK firmada con la misma clave se instala
como actualización y conserva los datos. Estas pruebas en dispositivo siguen
pendientes hasta disponer de las APK y de un dispositivo de prueba.
