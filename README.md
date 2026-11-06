# Savia

Aplicación Android para organizar gastos y ahorrar, sin cuenta y con los datos en el teléfono.

## Descargar e instalar

**[Descargar Savia 2.2.0 desde Releases](https://github.com/XFunBox/Savia/releases/tag/v2.2.0)**

[Descarga directa de Savia 2.2.0](https://github.com/XFunBox/Savia/releases/download/v2.2.0/Savia-2.2.0.apk)

1. Descarga `Savia-2.2.0.apk` desde Releases o desde el enlace directo.
2. Abre la APK y, si Android lo solicita, permite instalar aplicaciones desde ese navegador o gestor de archivos.
3. Abre **Savia**, configura Mi presupuesto y crea el primer periodo desde Inicio.

Requiere **Android 12 o posterior**. La versión 2.2.0 reúne las siete mejoras de planificación y ahorro. Sendoa confirma su validación en teléfono el 6 de noviembre de 2026. Pruebas, compilación, lint y firma se comprueban en la publicación.

### Actualización desde Savia 2.1.0

Exporta una copia JSON y guárdala fuera de la app. Instala la APK 2.2.0 sobre la anterior: conserva el identificador y la firma definitiva. No hace falta desinstalar. Las copias nuevas incluyen el historial y las revisiones; no pueden importarse en versiones antiguas.

### Actualización desde Savia 2.0.0 de pruebas

La versión 2.1.0 usa la firma definitiva y requiere reinstalar una vez. **Exporta tus datos en JSON y guarda la copia fuera de la app antes de desinstalar 2.0.0.** Después instala 2.1.0 e importa la copia. No intentes actualizar directamente la APK antigua de pruebas. Las próximas versiones conservarán la nueva firma.

### Actualización desde Ahorro 1.0

Savia tiene un identificador Android nuevo, así que se instala como una aplicación independiente y no actualiza Ahorro 1.0. Android tampoco transfiere automáticamente los datos entre ambas aplicaciones. Para conservarlos:

1. En Ahorro, exporta una copia JSON desde Ajustes.
2. Instala y abre Savia.
3. Importa la copia JSON desde Ajustes y confirma que aparecen tus datos antes de desinstalar Ahorro.

Las copias JSON no están cifradas. No desinstales Ahorro hasta comprobar la importación en Savia.

## Novedades de 2.2.0

- Aportaciones y retiradas con historial, corrección y fecha opcional de meta.
- Sugerencia de aportación por cobros y guía para preparar el presupuesto.
- Pagos mensuales y anuales con vencimiento y avisos locales opcionales.
- Límites por categoría y revisión del periodo con ahorro confirmado.
- Fecha de última exportación y recordatorio de copia configurable.
- Copias completas compatibles con los formatos anteriores.

## Funciones

- Salario neto, gastos fijos y fechas de cobro configurables.
- Registro de gastos e ingresos durante cada periodo.
- Presupuestos de cobro a cobro.
- Ahorro reservado manual o sugerido y varios objetivos independientes, con explicación del cálculo.
- Resúmenes por periodo y categorías.
- Funcionamiento local y copias de seguridad manuales.

Capturas con datos ficticios: [Inicio](docs/capturas/inicio-0.2.png), [Añadir gasto](docs/capturas/gasto-0.2.png), [Resumen](docs/capturas/resumen-0.2.png), [Búsqueda](docs/capturas/busqueda-1.0.png) y [Repetir gasto](docs/capturas/repetir-1.0.png).

## Compilar

Requisitos: JDK 17 y Android SDK con plataforma 36 y Build Tools 36.0.0. Configura ANDROID_HOME o sdk.dir en local.properties (archivo excluido de Git). Android Studio también puede abrir esta carpeta.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

La APK de desarrollo se genera en `app/build/outputs/apk/debug/app-debug.apk`.
Para generar versiones con la firma definitiva, sigue [Firma y publicación](docs/publicacion.md). El cambio desde la APK de pruebas 2.0.0 requiere exportar los datos y reinstalar una vez.
El material de firma no se incluye en el repositorio.

## Documentación

- [Diseño y alcance](docs/diseno.md).
- [Cambios de Savia 2.2.0](docs/cambios-2.2.0.md).
- [Cambios de Savia 2.1.0](docs/cambios-2.1.0.md).
- [Cambios anteriores de Savia 2.0.0](docs/notas-version-2.0.md).
- [Firma y publicación de actualizaciones](docs/publicacion.md).

## Licencia

Este repositorio no incluye una licencia de código. Hacerlo público permite consultar el contenido, pero no concede automáticamente permiso para reutilizarlo, modificarlo o redistribuirlo.

## Alcance actual

Interfaz en español. EUR, USD, GBP y MXN, una moneda por ciclo y sin conversión. Los fijos se prevén por ciclo o por vencimiento mensual/anual. Las fechas de los periodos se pueden ajustar al abrirlos. El objetivo sugerido se calcula con una fórmula visible; no usa IA ni requiere conexión.

La previsión no representa dinero transferido a una cuenta de ahorro. Los datos se guardan en el almacenamiento privado de Android; exporta una copia antes de desinstalar la app.
