# Ahorro

Proyecto de una aplicación Android para organizar gastos y ahorrar, sin cuenta y con los datos en el teléfono.

## Descargar e instalar en Android

**[Descargar Ahorro 0.2.0 para Android](https://github.com/XFunBox/ahorro-app/releases/latest)**

1. Abre este repositorio en el navegador del móvil con tu sesión de GitHub iniciada: es privado.
2. Pulsa el enlace de descarga. Si GitHub muestra la página del archivo, pulsa **Download raw file** o **View raw**.
3. Abre la APK descargada y, si Android lo solicita, permite instalar aplicaciones desde ese navegador o gestor de archivos.
4. Instala y abre **Ahorro**. Empieza por **Ajustes**, guarda tu configuración y abre el primer ciclo desde **Inicio**.

Requiere **Android 12 o posterior**. Versión de pruebas, con firma de desarrollo. Instalación y flujo básico comprobados en un emulador de Android 12; pendiente la valoración en teléfonos reales. Puedes actualizar sobre la 0.1.0 sin desinstalar: la firma y el formato de datos se conservan.

La carpeta [descargas](descargas) incluye la APK y su checksum SHA-256. El código y las instrucciones de compilación también están en este repositorio.

## Novedades de 0.2.0

Inicio con presupuesto diario orientativo, progreso del gasto y acceso a tu meta. Una acción destacada para añadir gastos; categorías con iconos y fechas en calendario. Ajustes agrupados y formulario con Guardar siempre visible encima del teclado.

Capturas reales de Android 12 con datos ficticios: [Inicio](docs/capturas/inicio-0.2.png), [Añadir gasto](docs/capturas/gasto-0.2.png) y [Resumen](docs/capturas/resumen-0.2.png).

## Funciones

- Salario neto, gastos fijos y fechas de cobro configurables.
- Registro de gastos e ingresos durante cada periodo.
- Presupuestos de cobro a cobro.
- Objetivo de ahorro manual o sugerido, con explicación del cálculo.
- Resúmenes por periodo y categorías.
- Funcionamiento local y copias de seguridad manuales.

## Documentación

- [Nota original del proyecto en Obsidian](docs/diseno.md).
- [Diseño de la primera versión](docs/diseno.md).
- [Plan de implementación](docs/plan.md).

## Estado

Repositorio privado por decisión del propietario. Versión 0.2.0 con tres secciones principales: Inicio, Movimientos y Resumen. La meta se abre desde Inicio y los ajustes desde la cabecera.

Enfoque técnico inicial: Kotlin y Jetpack Compose. Nombre provisional: Ahorro.

La nota de Obsidian es una copia puntual; no hay sincronización automática con la bóveda. Sus enlaces `[[wikilinks]]` pertenecen al vault personal y no están incluidos en este repositorio.

## Compilar y comprobar

Requisitos: JDK 17 y Android SDK con plataforma 36 y Build Tools 36.0.0. Configurar ANDROID_HOME o sdk.dir en local.properties (archivo excluido de Git). Android Studio también puede abrir esta carpeta y resolver la configuración.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

La APK de desarrollo se genera en app/build/outputs/apk/debug/app-debug.apk. Está firmada para pruebas; la firma estable de distribución se preparará al publicar una versión para usuarios.

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
