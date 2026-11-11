# Preparación de Savia para Google Play

Estado a 11 de noviembre de 2026: **preparación de Savia 3.0.0**. El repositorio
puede generar un AAB mediante ejecución manual; esto no implica que exista una
ficha, una prueba en Play Console ni una versión aprobada o publicada.
La app mantiene datos locales, sin cuentas, Internet, anuncios ni telemetría.
La distribución de APK en GitHub se documenta en [publicacion.md](publicacion.md).

## AAB firmado para revisión

Google Play exige Android App Bundle para aplicaciones nuevas y genera las APK
que recibe cada dispositivo. Un `.aab` no se instala directamente como una APK.
Fuente: [Android App Bundles](https://developer.android.com/guide/app-bundle).

El flujo [google-play.yml](../.github/workflows/google-play.yml) solo admite
`workflow_dispatch`, usa los cuatro secretos de firma existentes descritos en
[publicacion.md](publicacion.md) y tiene permiso `contents: read`.
No crea etiquetas, Releases ni publicaciones en Google Play.

1. Integrar el flujo revisado en la rama predeterminada para que Actions muestre
   **Preparar AAB para Google Play → Run workflow**. Elegir la rama revisada y
   el `versionName` esperado; el valor propuesto es `3.0.0`.
2. Mantener `applicationId = io.github.xfunbox.savia`, la firma definitiva y un
   `versionCode` superior a todos los distribuidos y a los usados en Play Console.
   El verificador actual impone un mínimo histórico; no consulta los códigos de
   Google Play. Revisar ese incremento antes de ejecutar.
3. El flujo ejecuta pruebas unitarias, `assembleRelease`, `bundleRelease` y
   `lintRelease`, además de los controles del verificador. La APK debe superar
   `scripts/verificar-apk.sh`; el AAB debe tener firma JAR válida y el mismo
   certificado definitivo fijado en `scripts/certificado-release.sha256`.
4. Descargar el artefacto `Savia-Play-<versión>-<ejecución>` en Actions antes de
   sus 14 días de retención. Incluye AAB, APK, `mapping.txt` y `SHA256SUMS`.
   Tras extraerlo, ejecutar `sha256sum -c SHA256SUMS` en esa carpeta.
5. Conservar esos archivos y el commit de la ejecución para revisar la entrega.
   La subida del AAB a Play Console se hace después, manualmente.

También puede compilarse localmente con el SDK y las variables privadas de
firma ya cargadas, sin copiar sus valores a comandos, documentos o Git:

```bash
bash gradlew --no-daemon testDebugUnitTest assembleRelease bundleRelease lintRelease
bash scripts/verificar-apk.sh app/build/outputs/apk/release/app-release.apk 3.0.0
```

El AAB queda en `app/build/outputs/bundle/release/app-release.aab`. Sin variables
de firma Gradle puede generar artefactos sin firmar: no son una entrega válida.
La comprobación criptográfica adicional del AAB está en el flujo manual.

## Firma y actualización de las instalaciones actuales

Play App Signing distingue la clave de subida del AAB de la clave que firma
las APK entregadas. Actualmente Google puede generar claves por defecto al
crear la aplicación. Para conservar la actualización desde las APK de GitHub,
configurar la clave de firma de la app con la **clave definitiva existente**,
siguiendo la importación cifrada de Play Console, antes de distribuir una prueba
abierta o producción. No aceptar otra firma sin resolver la migración.
Fuente: [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).

Comprobar que el certificado de firma de la app en Play Console coincide con
`scripts/certificado-release.sha256`; el certificado de subida no basta.
Instalar una versión entregada por Play sobre la APK definitiva anterior y
comprobar conservación de datos. La APK de Actions valida la firma local,
pero no demuestra la firma que Google utilizará. Una clave de subida separada
recomendada por Google requeriría adaptar el flujo y su comprobación del AAB;
este flujo utiliza los secretos actuales y no genera claves nuevas.

## Política y declaraciones

Todas las apps necesitan política de privacidad, incluso sin recopilación.
Debe haber texto o enlace dentro de la app y una URL pública activa, sin
restricción geográfica, sin inicio de sesión, no editable por el visitante y
no PDF en Play Console. Completar identidad, contacto, conservación y borrado
en [privacidad.md](privacidad.md) y publicar su versión definitiva.
Fuente: [Datos de usuario](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

Para Seguridad de los datos, la propuesta basada en la app actual es **no
recopila ni comparte datos**: la información financiera se procesa localmente;
las exportaciones son iniciadas por el usuario mediante el selector de Android.
Confirmar la declaración contra el AAB final y sus dependencias. El acceso solo
local queda fuera de recopilación; una transferencia elegida conscientemente
por el usuario puede estar exceptuada de compartir. No afirmar cifrado de las
copias JSON. El formulario es obligatorio en pruebas cerradas, abiertas y
producción; la excepción de pruebas internas no elimina la política de privacidad.
Fuente: [Seguridad de los datos](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

Completar también anuncios (sin anuncios), acceso a la app (sin credenciales),
clasificación de contenido, audiencia, países y declaración de funciones
financieras. Esta última se exige incluso a apps que no ofrecen servicios
financieros: describir el presupuesto y ahorro locales y revisar las opciones
de la consola, sin declarar banca, préstamos o transferencias inexistentes.
Fuente: [Funciones financieras](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en).

## Requisitos y ficha pendientes

- **API objetivo:** Google exige Android 16/API 36 para nuevas apps y
  actualizaciones desde el 31/08/2026. Savia configura `targetSdk = 36` y
  `minSdk = 31` (Android 12). Confirmarlo en el AAB final.
  [Requisito oficial](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-MZ).
- **Cuenta de desarrollador:** confirmar titular, tipo, fecha de creación,
  verificación de identidad y acceso a Play Console. Si es una cuenta personal
  creada después del 13/11/2023, requiere prueba cerrada con al menos 12 testers
  inscritos durante 14 días seguidos y posterior solicitud de acceso a
  producción. La validación de APK fuera de Play no sustituye ese requisito.
  [Pruebas de cuentas personales](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
- **Datos públicos:** nombre del desarrollador **FunBoX**, confirmado por Sendoa.
  Correo público de soporte y privacidad **mrsfunboxyt@gmail.com**, confirmado.
  Pendiente URL pública de la política. No usar datos ficticios.
- **Ficha propuesta:** nombre «Savia», categoría Finanzas y descripción corta
  «Organiza ingresos, gastos y objetivos de ahorro sin cuentas ni conexión».
  Revisar la descripción larga con las funciones de la versión final, sin
  prometer ahorro garantizado ni movimientos bancarios.
- **Gráficos:** preparar icono PNG de 512 × 512, gráfico de 1024 × 500 sin
  transparencia y al menos dos capturas reales con datos ficticios. Para
  teléfonos, capturas JPEG o PNG sin alfa, dimensiones entre 320 y 3840 píxeles
  y lado largo como máximo doble del corto. Revisar cada formato distribuido
  y sus textos alternativos. [Recursos de la ficha](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

## Validación previa a enviar a revisión

Pendiente de ejecución real en Actions con secretos y de prueba en Play Console:

- Confirmar AAB firmado, versión/código correctos y ausencia de permisos de red
  o SDK de publicidad/analítica en el artefacto final.
- Probar el AAB entregado por el canal interno de Play, incluyendo actualización
  sobre APK definitiva, exportación/importación, avisos concedidos/denegados y
  conservación de datos al actualizar. Usar datos ficticios.
- Revisar el informe previo al lanzamiento, navegación, accesibilidad, inicio
  sin datos y funcionamiento sin conexión en los Android compatibles.
- Completar y verificar URL de privacidad, contacto y declaraciones; cumplir la
  prueba cerrada si corresponde. Registrar incidencias y su resolución antes
  de solicitar producción. Una compilación correcta no acredita aprobación.
