# Savia: flujo de desarrollo de las siete mejoras

Fecha: 5 de noviembre de 2026. Base comprobada: Savia 2.1.0, main `48d34bd`.

Estado actualizado 6/11/2026: siete mejoras implementadas y CI correcta; Sendoa confirma la validación en teléfono y autoriza publicar. Se reúnen las entregas propuestas en 2.2.0 (versionCode 6). Las fases siguientes conservan el diseño original como referencia. Lista de tareas: [todo.md](todo.md).

## Objetivo y alcance

Hacer más sencillo planificar gastos y registrar el ahorro real mediante siete mejoras: aportaciones a objetivos, metas con fecha, guía inicial, próximos pagos, límites por categoría, cierre del periodo y recordatorios de copia.

Se conserva Android nativo, funcionamiento local sin Internet, cuentas ni telemetría, e importes enteros en céntimos. Se aprovechan los movimientos, periodos, pagos fijos y copias actuales. No se añaden sincronización bancaria, IA ni nuevas plataformas.

## Reglas que deben mantenerse

- **Reservado** significa dinero que se pretende apartar; **previsto** es una estimación; **ahorrado** requiere una confirmación del usuario. Una previsión nunca aumenta automáticamente un objetivo.
- Registrar una aportación refleja dinero que el usuario ya apartó: Savia no realiza transferencias bancarias. Aportaciones, retiradas y reparto entre objetivos no se convierten en ingresos o gastos ordinarios.
- La aportación prevista ya forma parte de la reserva común. Confirmarla no vuelve a descontarla. Antes de implementar se fijará y probará cómo se registra una aportación adicional fuera de esa reserva, sin ocultar su efecto en el disponible.
- No se mezclan monedas. Una meta conserva su moneda y solo usa periodos compatibles para sugerencias o repartos.
- Cada modificación de datos nuevos incluye persistencia, validación, exportación e importación en la misma tarea. No dejar estados que la app pueda guardar pero no respaldar.
- Evolucionar el formato de copia de manera explícita cuando cambie el esquema. Seguir importando v1/v2; no exigir que versiones antiguas lean formatos futuros. Una importación inválida no sustituye datos válidos.
- Una escritura fallida conserva el formulario y muestra el error. Ninguna migración borra datos para conseguir abrir la app.

## Orden y entregas propuestas

| Entrega | Contenido | Resultado para el usuario |
|---|---|---|
| 2.2.0 | Puntos 1, 2 y 3 | Registrar aportaciones, entender el progreso y preparar el presupuesto con ayuda. |
| 2.3.0 | Puntos 4 y 5 | Ver pagos próximos y controlar el gasto por categoría. |
| 2.4.0 | Puntos 6 y 7 | Cerrar el periodo con cifras confirmadas y recordar las copias. |

Dependencias: validar 2.1.0 → aportaciones → metas con fecha → guía → primera entrega. Después, pagos y límites → segunda entrega. El cierre utiliza aportaciones y los datos del periodo; el recordatorio utiliza el resultado real de exportación. No compartir cambios simultáneos del almacenamiento entre tareas independientes.

## Fase 0: comprobar el punto de partida

**Actualización del 5/11/2026:** Sendoa confirma que la 2.1.0 está probada en teléfono y funciona bien. No repetir la validación general sin cambios o una duda concreta. Confirmar por separado exportación/importación, migración y actualización con la firma definitiva; no darlas por probadas por esa afirmación general.

Probar la APK 2.1.0 en teléfono: crear y borrar periodos/objetivos, registrar gastos, exportar e importar. Para pasar desde 2.0.0 con la firma anterior, guardar la copia fuera de la app antes de desinstalar. Verificar además una actualización posterior con la firma definitiva sin perder datos. Confirmar respaldo externo privado de la clave; no guardarla en el repo.

Preparar datos ficticios reutilizables: periodo normal, presupuesto insuficiente, varios objetivos, distintas monedas y copia antigua. Pedir a un tester que explique qué es disponible, reservado y ahorrado; anotar los puntos de confusión, sin datos personales.

## Fase 1: objetivos y guía — propuesta 2.2.0

### 1. Aportaciones y retiradas

Flujo: Objetivos → elegir objetivo → «Añadir ahorro» → importe, fecha y nota opcional → confirmar → progreso e historial actualizados. «Registrar retirada» reduce el ahorro registrado y deja su rastro. El historial permite corregir o eliminar una entrada con confirmación.

El total ya registrado en 2.1.0 se conserva como **saldo inicial**, sin inventar aportaciones históricas ni fechas. Se distingue del historial nuevo. Se admiten objetivos preparados sin periodo activo; no se les atribuye un periodo ficticio. Registrar ahorro por encima de la meta puede permitirse, manteniendo pendiente mínimo cero. Una retirada no puede superar lo ahorrado.

Dividir en dos tareas completas: registrar/consultar aportaciones y después retiradas/correcciones. Definir identificadores para que guardar dos veces la misma operación no duplique el ahorro. Al borrar un periodo no borrar aportaciones reales a objetivos; al borrar un objetivo confirmar que también se elimina su historial.

### 2. Fecha opcional de meta

Flujo: crear/editar objetivo → elegir fecha o dejar «Sin fecha» → ver cuánto falta y una sugerencia por periodo de cobro. Explicar los supuestos del cálculo y cuándo sería la primera aportación; usar periodos de cobro, no asumir meses naturales.

Redondear hacia arriba en céntimos. Tratar explícitamente meta cumplida, plazo vencido, fecha cercana, cambios de día de cobro y moneda incompatible. Sin un calendario de cobros válido, pedir configurarlo en lugar de dar una cifra engañosa. La sugerencia nunca modifica el presupuesto automáticamente.

### 3. Guía inicial y ayuda contextual

Flujo: ingresos y cobro → gastos fijos → gastos diarios → ahorro → revisión del reparto. Reutilizar los formularios y cálculos actuales. Permitir omitir la guía, volver atrás y abrirla desde ajustes sin reiniciar datos. Una segunda ejecución exige revisar antes de sustituir valores.

Mostrar un ejemplo sencillo y explicar los tres conceptos cerca de las cantidades. Marcar claramente cualquier dato de ejemplo para que no se convierta en movimiento real. Permitir guardar un presupuesto insuficiente con aviso comprensible, como hace el modelo actual.

**Control de entrega:** importar copia v1/v2, conservar saldos iniciales, probar el recorrido completo y verificar que reserva/aportaciones/disponible no se descuentan dos veces. Revisar con testers la comprensión de los textos.

## Fase 2: gastos previstos — propuesta 2.3.0

### 4. Próximos pagos y recordatorios

Primero añadir vencimiento a los pagos fijos existentes y una lista «Próximos pagos». Empezar con vencimientos mensuales y pagos anuales; dejar frecuencias arbitrarias para otra necesidad real. Para el día 29/30/31, usar el último día válido del mes cuando proceda y explicarlo.

Después añadir avisos locales configurables. Pedir permiso de notificaciones cuando el usuario active avisos, contemplando las distintas versiones de Android. Elegir una programación proporcional a recordatorios por día; no prometer puntualidad exacta. Reprogramar al editar, reiniciar o importar y cancelar al pagar/eliminar. Si el permiso se deniega, la lista de próximos pagos sigue funcionando.

Flujo: aviso o lista → revisar importe y fecha → «Marcar pagado». Reutilizar el movimiento vinculado actual: avisar no significa pagar. El mismo vencimiento solo puede generar un pago; no crear un segundo sistema de gastos fijos ni descontar dos veces pagos anuales y su provisión.

### 5. Límites opcionales por categoría

Flujo: presupuesto → categoría → límite del periodo → barra «Gastado / límite / restante». Sin límite, conservar el comportamiento actual. Categorías basadas en las existentes; mantener coherencia al cambiar su nombre.

Son un reparto del presupuesto de gasto variable, no una deducción adicional del disponible. Avisar si la suma supera el gasto variable previsto. Empezar con barras y avisos dentro de la app, sin nuevas notificaciones. Permitir superar el límite sin impedir registrar un gasto real. Conservar el límite aplicable a cada periodo para no alterar el historial al cambiar ajustes futuros.

**Control de entrega:** edición/eliminación de pagos, vencimientos cortos y permisos denegados; un solo movimiento por pago; límites consistentes después de editar, borrar o repetir movimientos y después de importar una copia.

## Fase 3: cierre y conservación — propuesta 2.4.0

### 6. Cierre del periodo

Flujo: revisar ingresos/gastos y pagos pendientes → comparar previsto/real → indicar cuánto se ha apartado realmente → repartirlo entre objetivos compatibles → confirmar cierre. El saldo del periodo no se convierte automáticamente en ahorro.

Mostrar aportaciones ya registradas y registrar solo lo pendiente, evitando duplicados al reabrir o repetir la confirmación. Definir un cierre revisable: las correcciones de movimientos posteriores deben permitir actualizarlo y mostrar que ha cambiado. Mantener acceso al historial. Empezar el siguiente periodo sigue exigiendo confirmar ingreso y fechas; no inventar ingresos ni arrastrar saldo sin decisión explícita.

Dividir en comparación/revisión del periodo y confirmación del ahorro. Las aportaciones conservan su trazabilidad si posteriormente se elimina el periodo, según la regla de la fase 1.

### 7. Recordatorio de copia

Primero mostrar fecha de la última exportación completada. Actualizarla solo después de terminar correctamente la escritura; cancelar el selector o fallar al escribir no cuenta. No afirmar que el archivo sigue existiendo o que es recuperable solo porque se exportó una vez.

Después añadir recordatorio opcional con frecuencia configurable, reutilizando la infraestructura local del punto 4. Activarlo explícitamente, permitir posponerlo o desactivarlo y avisar de que la copia JSON no está cifrada. Importar una copia no equivale a haber creado una copia reciente en este dispositivo.

**Control de entrega:** cerrar dos veces no duplica aportaciones; una corrección actualiza las cifras; exportar/cancelar/fallar deja la fecha correcta; recuperación completa de los datos de las siete mejoras.

## Flujo de trabajo de cada tarea

1. Verificar main, árbol de trabajo y plan; crear una rama temporal por bloque coherente. No reutilizar ramas integradas.
2. Fijar casos de aceptación y reglas pendientes de ese bloque antes del código. Anotar aquí las decisiones que cambien el comportamiento.
3. Implementar una función completa con su almacenamiento, interfaz, copia y pruebas de lógica afectadas. Añadir pruebas de migración cuando cambie el formato.
4. Ejecutar las pruebas afectadas; antes de integrar, completar `./gradlew testDebugUnitTest assembleDebug lintDebug` en entorno Android disponible o CI. En Windows, `gradlew.bat` con las mismas tareas. No afirmar prueba en teléfono si solo pasa CI.
5. Una revisión de cambios y corrección de hallazgos concretos. Abrir PR contra main; esperar los checks obligatorios sin saltarse protección.
6. Integrar, eliminar la rama ya integrada y guardar checkpoint con lo comprobado y lo pendiente. Main queda como única rama permanente; habrá ramas temporales mientras se desarrolla.
7. Al completar una entrega, probar en teléfono instalación/actualización y exportación/importación. Preparar versión, versionCode, notas breves y PR de release; después integrar y etiquetar. El flujo existente verifica firma y publica APK y checksum. Confirmar resultados antes de anunciar la release.

## Riesgos y decisiones pendientes de implementación

| Riesgo o decisión | Tratamiento previsto |
|---|---|
| Aportaciones fuera de la reserva y ahorro sin periodo | Definir procedencia y efecto en el disponible en tarea 1; no duplicar deducciones ni atribuir dinero ficticio. |
| Varios cambios del esquema | Migración gradual y pruebas de ida/vuelta de copias por entrega; conservar saldos iniciales. |
| Sugerencias de fecha incorrectas | Casos de calendario, redondeo y explicación de supuestos; no mezclar monedas. |
| Gastos anuales frente a provisión por periodo | Distinguir reserva/provisión y pago real antes de tarea 6; no introducir doble descuento. |
| Avisos limitados por Android | Revisar documentación oficial al implementar; alternativa visible dentro de la app. |
| Cierres obsoletos tras editar gastos | Política explícita de revisión y operaciones de confirmación sin duplicados. |
| Demasiada complejidad en pantalla | Funciones opcionales y prueba con testers antes de ampliar controles. |

Las preguntas pendientes se resolverán al entrar en su tarea con una propuesta concreta. No bloquean guardar este plan. No hay plazos de entrega comprometidos.

## Referencias de la investigación

- [YNAB: metas y aportación calculada por fecha](https://www.ynab.com/features/goal-tracking).
- [Actual Budget: programación y aprobación manual de pagos](https://actualbudget.org/docs/tour/schedules/).
- [Goodbudget: presupuesto por sobres](https://goodbudget.com/).

La división de entregas y las reglas anteriores son una propuesta específica para Savia, basada en el código actual y en el comentario de los testers sobre la confusión entre ahorro y gastos.
