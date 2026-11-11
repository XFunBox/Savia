# Savia — primera versión Android

Aplicación para cualquier persona, sin cuenta ni servidor. Registra ingresos, gastos fijos y movimientos, propone o acepta una meta y resume cada ciclo de cobro a cobro.

## Decisiones de implementación

Kotlin y Jetpack Compose nativos, Android 12 o posterior. Se eligen para el destino Android pedido. Flutter sería útil si se añadiera iOS; una web empaquetada añadiría una capa que este alcance no necesita. Datos en almacenamiento privado con escrituras atómicas, sin permiso de Internet ni copia automática en nube. Copias manuales JSON mediante el selector del sistema.

Cinco destinos: Inicio, Movimientos, Plan, Resúmenes, Ajustes. Interfaz en español, oscura con acentos neón lima y violeta, controles accesibles y desplazamiento con teclado. Sin datos financieros de ejemplo en el estado inicial.

## Reglas

- Configuración: salario neto, día habitual de cobro (1–31), presupuesto variable estimado, objetivo manual/automático, moneda y lista de gastos fijos.
- El usuario inicia cada ciclo confirmando ingreso y fechas. El siguiente cobro es un extremo exclusivo. Las fechas se pueden ajustar; no se inventan ingresos automáticamente.
- Cada ciclo conserva una instantánea de ingreso, fijos, moneda y meta. Cambiar ajustes no reescribe el historial.
- Gastos variables e ingresos extraordinarios registrables con importe, categoría, fecha y nota. El alta ofrece categorías acordes al tipo de movimiento y permite asignar parte de un ingreso extra a un objetivo. La aportación queda vinculada al ingreso: corregirlo o eliminarlo mantiene los datos sincronizados. Los pagos fijos se marcan pagados creando un movimiento vinculado; no se descuentan dos veces.
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

## Actualización: objetivos y presupuesto

Un presupuesto común y hasta 100 objetivos independientes, con nombre, moneda, cantidad total, ahorro registrado manualmente y aportación prevista por periodo. Las aportaciones reparten la reserva existente: no generan movimientos ni descuentan dos veces el disponible. El progreso no se incrementa automáticamente con una previsión. Cada objetivo conserva su moneda al editarse.

Mi presupuesto separa ingresos, gastos fijos, gastos del día a día y ahorro reservado. Muestra el reparto y avisa si los gastos o la reserva exceden la capacidad prevista. Inicio distingue disponible, reserva y previsión; Objetivos está en la navegación principal.

Resumen permite borrar un periodo y exclusivamente sus movimientos, previa confirmación con fechas y número de movimientos. Presupuesto y objetivos se conservan. Las copias v2 añadieron objetivos; la versión 1 sigue admitida y se importa con objetivos vacíos, conservando todos sus datos originales.

## Desarrollo: historial, calendario y revisión

Las siete mejoras posteriores a 2.1.0 se describen en [desarrollo-siete-mejoras.md](desarrollo-siete-mejoras.md). El almacenamiento conserva lectura de copias v1–v5; v5 vincula aportaciones a ingresos extraordinarios sin alterar copias previas. Los saldos anteriores permanecen como saldo inicial, separado de entradas con fecha; no se inventa un historial. Solo ahorro adicional neto reduce el disponible además de la reserva. Vencimientos y límites conservan instantáneas por periodo; el resumen compara periodos de la misma moneda y desglosa ingresos por origen, y los límites avisan al alcanzar el 80 %. Cierres son revisiones explícitas con aportaciones idempotentes; no generan ingresos del siguiente periodo. Preferencias y metadatos de avisos permanecen en el dispositivo, fuera de la copia financiera.

## Savia 3.0: superficies y jerarquía

La interfaz usa una paleta central en `ui/SaviaTheme.kt`. El fondo oscuro con halos suaves queda visible a través de las tarjetas y de la cápsula de navegación. Los formularios y menús mantienen superficies opacas para lectura y entrada de datos. Se conserva el verde para acciones y disponible y el morado para objetivos y previsión; los avisos llevan texto además de color.

Inicio prioriza disponible, estimación diaria y siguiente cobro, con la reserva ya descontada. Previsión y reserva se explican por separado. El contenido se limita a 640 dp y se centra en pantallas anchas. Se mantiene la navegación y la lógica financiera existentes. La inspiración de Garraio se adapta a Savia mediante superficies translúcidas y jerarquía; no se incorporan sus marcas ni recursos.

La marca 3.0 usa una S con brote, dibujada como vector propio. El símbolo conserva los dos colores; Android recibe versiones adaptable y monocroma, y las notificaciones una versión sin margen de lanzador.
