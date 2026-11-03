# Ahorro

Proyecto de una aplicación Android para organizar gastos y ahorrar, sin cuenta y con los datos en el teléfono.

## Funciones de la primera versión

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

Repositorio privado por decisión del propietario. Primera implementación Android con cinco pantallas: Inicio, Gastos, Plan, Resumen y Ajustes.

Enfoque técnico inicial: Kotlin y Jetpack Compose. Nombre provisional: Ahorro.

La nota de Obsidian es una copia puntual; no hay sincronización automática con la bóveda. Sus enlaces `[[wikilinks]]` pertenecen al vault personal y no están incluidos en este repositorio.

## Compilar y comprobar

Requisitos: JDK 17 y Android SDK con plataforma 35 y Build Tools 35.0.0. Configurar ANDROID_HOME o sdk.dir en local.properties (archivo excluido de Git). Android Studio también puede abrir esta carpeta y resolver la configuración.

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
