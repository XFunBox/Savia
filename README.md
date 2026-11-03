# Ahorro

Proyecto de una aplicación Android para organizar gastos y ahorrar, sin cuenta y con los datos en el teléfono.

## Descargar e instalar en Android

**[Descargar Ahorro 1.0.0 desde Releases](https://github.com/XFunBox/ahorro-app/releases/tag/v1.0.0)**

[Descarga alternativa de la APK](https://github.com/XFunBox/ahorro-app/releases/latest)

1. Abre la página de [Releases](https://github.com/XFunBox/ahorro-app/releases/tag/v1.0.0) o descarga la APK directamente con el enlace alternativo.
2. Si GitHub muestra la página del archivo, pulsa **Download raw file** o **View raw**.
3. Abre la APK descargada y, si Android lo solicita, permite instalar aplicaciones desde ese navegador o gestor de archivos.
4. Instala y abre **Ahorro**. Empieza por **Ajustes**, guarda tu configuración y abre el primer ciclo desde **Inicio**.

Requiere **Android 12 o posterior**. APK optimizada con el certificado de las versiones anteriores. Instalación y flujo básico comprobados en un emulador de Android 12; pendiente la valoración en teléfonos reales. Puedes actualizar desde una versión anterior sin desinstalar: la firma y el formato de datos se conservan.

La carpeta [descargas](descargas) incluye la APK y su checksum SHA-256. El código y las instrucciones de compilación también están en este repositorio.

## Novedades de 1.0.0

- Busca movimientos del periodo por categoría, nota o importe.
- Repite un gasto variable desde su menú, revisando la copia antes de guardar.
- APK optimizada de aproximadamente 2,8 MB.

## Diseño introducido en 0.2.0

Inicio con presupuesto diario orientativo, progreso del gasto y acceso a tu meta. Una acción destacada para añadir gastos; categorías con iconos y fechas en calendario. Ajustes agrupados y formulario con Guardar siempre visible encima del teclado.

Capturas reales de Android 12 con datos ficticios: [Inicio](docs/capturas/inicio-0.2.png), [Añadir gasto](docs/capturas/gasto-0.2.png) y [Resumen](docs/capturas/resumen-0.2.png).

## Funciones

- Salario neto, gastos fijos y fechas de cobro configurables.
- Registro de gastos e ingresos durante cada periodo.
- Presupuestos de cobro a cobro.
- Objetivo de ahorro manual o sugerido, con explicación del cálculo.
- Resúmenes por periodo y categorías.
- Funcionamiento local y copias de seguridad manuales.

## Estado

Versión 1.0.0 con tres secciones principales: Inicio, Movimientos y Resumen. La meta se abre desde Inicio y los ajustes desde la cabecera.

La aplicación está escrita en Kotlin y Jetpack Compose. La versión publicada requiere Android 12 o posterior.

## Documentación

- [Diseño y alcance](docs/diseno.md).
- [Cambios de la versión 1.0.0](docs/notas-version-1.0.md).

## Compilar

Requisitos: JDK 17 y Android SDK con plataforma 36 y Build Tools 36.0.0. Configurar ANDROID_HOME o sdk.dir en local.properties (archivo excluido de Git). Android Studio también puede abrir esta carpeta y resolver la configuración.

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease lintRelease
```

La APK optimizada se genera en app/build/outputs/apk/release/app-release.apk. Conserva el certificado usado en las versiones anteriores distribuidas por GitHub. El material de firma no se incluye en el repositorio.

## Licencia

Este repositorio no incluye una licencia de código. Hacerlo público permite consultar el contenido, pero no concede automáticamente permiso para reutilizarlo, modificarlo o redistribuirlo.

## Cómo probar

1. Guardar salario, fecha de cobro, presupuesto variable, fijos y tipo de meta en Ajustes.
2. Abrir un ciclo en Inicio confirmando las fechas y el ingreso recibido.
3. Añadir un gasto en Gastos y marcar un fijo como pagado en Inicio.
4. Comprobar disponible, meta y previsión; consultar Resumen.
5. Exportar una copia, introducir otro movimiento e importar la copia confirmando el reemplazo.

Los datos permanecen en el almacenamiento privado de Android. Desinstalar la app elimina sus datos; exporta una copia antes. Las copias JSON no están cifradas.

## Alcance actual

Interfaz en español. EUR, USD, GBP y MXN, una moneda por ciclo y sin conversión. Los fijos se prevén por ciclo. Las fechas de los periodos se pueden ajustar al abrirlos. El objetivo sugerido se calcula con una fórmula visible; no usa IA ni requiere conexión.

La previsión no representa dinero transferido a una cuenta de ahorro. El historial conserva las condiciones de cada ciclo aunque cambien los ajustes.
