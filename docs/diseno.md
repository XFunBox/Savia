# Savia — primera versión Android

Aplicación para cualquier persona, sin cuenta ni servidor. Registra ingresos, gastos fijos y movimientos, propone o acepta una meta y resume cada ciclo de cobro a cobro.

## Decisiones de implementación

Kotlin y Jetpack Compose nativos, Android 12 o posterior. Se eligen para el destino Android pedido. Flutter sería útil si se añadiera iOS; una web empaquetada añadiría una capa que este alcance no necesita. Datos en almacenamiento privado con escrituras atómicas, sin permiso de Internet ni copia automática en nube. Copias manuales JSON mediante el selector del sistema.

Cinco destinos: Inicio, Movimientos, Plan, Resúmenes, Ajustes. Interfaz en español, oscura con verde y morado, controles accesibles y desplazamiento con teclado. Sin datos financieros de ejemplo en el estado inicial.

## Reglas

- Configuración: salario neto, día habitual de cobro (1–31), presupuesto variable estimado, objetivo manual/automático, moneda y lista de gastos fijos.
- El usuario inicia cada ciclo confirmando ingreso y fechas. El siguiente cobro es un extremo exclusivo. Las fechas se pueden ajustar; no se inventan ingresos automáticamente.
- Cada ciclo conserva una instantánea de ingreso, fijos, moneda y meta. Cambiar ajustes no reescribe el historial.
- Gastos variables y otros ingresos registrables con importe, categoría, fecha y nota. Los pagos fijos se marcan pagados creando un movimiento vinculado; no se descuentan dos veces.
- Sugerencia = máximo de cero e ingreso menos fijos menos presupuesto variable. Se explica la fórmula. El objetivo manual puede exceder la capacidad: se muestra el desfase, sin cambiarlo silenciosamente.
- Disponible = ingresos registrados del ciclo menos gastos pagados menos fijos pendientes menos meta. Puede ser negativo.
- Previsión = ingresos menos fijos previstos/pagados menos el mayor entre presupuesto variable y gasto variable registrado. Es una estimación, no ahorro confirmado.
- Resumen: ingresos, gastos pagados, saldo registrado y desglose por categorías. El saldo no implica una transferencia real a una cuenta de ahorro.
- Dinero en enteros de unidades mínimas, entradas con coma o punto y máximo dos decimales. Importes y tamaño del respaldo limitados; fechas y referencias validadas antes de sustituir datos.
- Primera versión: una moneda por ciclo entre EUR, USD, GBP y MXN; pagos fijos por ciclo. Fechas de ciclo ajustables para adaptarse a distintas nóminas.

## Verificación

Pruebas de dinero, fechas cortas/bisiestos, presupuesto negativo, pagos fijos sin duplicación, historial y respaldos inválidos. Compilación de APK y análisis Android. La prueba visual en un teléfono o emulador se declara pendiente si no hay dispositivo disponible.

## Fuentes técnicas

- https://developer.android.com/develop/ui/compose/setup
- https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Context7, documentación oficial de Compose, consultada durante la implementación.
