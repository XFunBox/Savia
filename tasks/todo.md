# Savia: tareas de las siete mejoras

Estado inicial: pendientes; solo se ha redactado el plan. Reglas y flujos completos en [plan.md](plan.md). Áreas indicadas como orientación, a confirmar localizando el código al iniciar cada tarea. Si un bloque supera unos cinco archivos de lógica/interfaz, dividirlo conservando una entrega funcional; las pruebas y migración forman parte del bloque afectado.

## T0 · Validar base 2.1.0

- [x] Funcionamiento general de la 2.1.0 en teléfono confirmado por Sendoa el 5/11/2026.
- [ ] Confirmar por separado exportación/importación sin pérdidas; no repetir la validación general sin motivo.
- [ ] Comprobar actualización con la firma definitiva y anotar resultado; confirmar respaldo privado de firma.
- [ ] Registrar con un tester qué entiende por disponible, reservado y ahorrado.

Dependencias: ninguna. Verificación: recorrido manual documentado con datos ficticios. Áreas: APK 2.1.0 y registro de validación. Alcance: pequeño.

## T1 · Añadir ahorro e historial — punto 1

- [ ] Conservar el ahorro anterior como saldo inicial; registrar aportaciones con identificador, fecha e importe e historial visible.
- [ ] Fijar y probar efecto de aportaciones reservadas/adicionales, sin periodo y en moneda distinta, sin doble descuento.
- [ ] Guardado repetido sin duplicados; migración y copia/importación conservan saldos y entradas.

Dependencias: T0. Verificación: pruebas de cálculo, saldo inicial, persistencia y copias antiguas/nuevas; teléfono con varios objetivos. Áreas: SavingsGoals, GoalsScreen, Backup, validación/persistencia. Alcance: medio, dividir migración si lo exige.

## T2 · Retirar y corregir ahorro — punto 1

- [ ] Registrar retiradas sin permitir saldo negativo; corregir o borrar entradas con confirmación.
- [ ] Totales e historial coherentes tras cambios y reinicio; conservar aportaciones al borrar periodo y confirmar historial al borrar objetivo.
- [ ] Fallos de guardado no pierden la operación introducida.

Dependencias: T1. Verificación: pruebas de retiradas, correcciones, borrado y recuperación; revisión del recorrido en teléfono. Áreas: SavingsGoals y GoalsScreen, validación/pruebas. Alcance: medio.

## T3 · Fecha y sugerencia — punto 2

- [ ] Fecha opcional editable, sin modificar automáticamente la reserva.
- [ ] Cálculo por cobros compatible con céntimos, calendario y moneda; explicar supuestos y falta de calendario válido.
- [ ] Meta cumplida, vencida y próxima muestran resultados claros; formato de copia conserva fecha.

Dependencias: T2. Verificación: pruebas de calendario/redondeo/copias y comprobación visual. Áreas: SavingsGoals, GoalsScreen, calendario, Backup. Alcance: medio.

## T4 · Guía inicial — punto 3

- [ ] Recorrer ingresos, fijos, diarios y ahorro viendo el reparto calculado; volver atrás y omitir.
- [ ] Abrir desde ajustes sin reiniciar datos; revisar antes de sustituir valores.
- [ ] Ejemplos separados de datos reales y avisos comprensibles ante presupuesto insuficiente.

Dependencias: T3. Verificación: primer uso y usuario existente en teléfono; comprobar comprensión con testers. Áreas: Forms, SaviaApp y componentes de presupuesto existentes. Alcance: medio.

## Control A · Entrega propuesta 2.2.0

- [ ] T0–T4 completas; pruebas JVM, assembleDebug y lintDebug correctos; revisión y PR integrado con checks.
- [ ] Teléfono: guía → objetivo → aportación → retirada → copia/importación; conservar datos desde 2.1.0.
- [ ] Actualizar versión/notas, publicar mediante flujo firmado y verificar APK/checksum; guardar checkpoint y limpiar ramas integradas.

## T5 · Vencimientos y lista de pagos — punto 4

- [ ] Añadir vencimientos mensuales/anuales a pagos existentes y mostrar próximos pagos.
- [ ] Definir días inexistentes y provisión de gastos anuales sin doble descuento.
- [ ] Confirmar pago crea un único movimiento vinculado; editar/eliminar mantiene coherencia y copias.

Dependencias: control A. Verificación: pruebas de calendario, duplicados e importación; recorrido manual de pagos. Áreas: modelo de pagos, presupuesto, Forms/Screens, Backup. Alcance: medio; separar calendario si crece.

## T6 · Avisos locales de pagos — punto 4

- [ ] Activación voluntaria y permisos según versión de Android; lista útil con permiso denegado.
- [ ] Programar/cancelar correctamente tras edición, pago, eliminación, reinicio e importación.
- [ ] Abrir pago desde aviso sin contabilizarlo automáticamente ni duplicar avisos al reprogramar.

Dependencias: T5. Verificación: pruebas del cálculo de programación y pruebas reales de notificaciones, permisos y reinicio. Áreas: recordatorios nuevos, Manifest, integración con pagos/ajustes. Alcance: medio.

## T7 · Límites por categoría — punto 5

- [ ] Configurar límite opcional y mostrar gastado/límite/restante usando categorías existentes.
- [ ] Repartir gasto variable sin restarlo de nuevo; avisar si límites superan el presupuesto y permitir registrar sobrecostes.
- [ ] Mantener límites históricos por periodo, correcciones de movimientos y recuperación desde copia.

Dependencias: control A; ejecutar después de T6 para evitar cambios compartidos de persistencia. Verificación: pruebas de sumas, histórico y copias; teléfono con categorías sin límite y sobrepasadas. Áreas: Budget, Forms/Screens y Backup. Alcance: medio, separar configuración y resumen si crece.

## Control B · Entrega propuesta 2.3.0

- [ ] T5–T7 completas; comprobaciones Android, revisión y checks de integración correctos.
- [ ] Teléfono: próximos pagos → aviso → confirmar → límites; sin duplicados, con permisos denegados e importación.
- [ ] Release firmada verificada, notas y checkpoint; eliminar ramas integradas.

## T8 · Revisión al cerrar periodo — punto 6

- [ ] Mostrar previsto/real, pagos pendientes y aportaciones ya registradas.
- [ ] Permitir revisar periodos anteriores; una corrección posterior identifica que las cifras han cambiado.
- [ ] Saldo no se presenta como ahorro confirmado ni se arrastra sin decisión.

Dependencias: control B. Verificación: pruebas de comparación y cambios posteriores; revisión visual del cierre con y sin pendientes. Áreas: Budget, Screens y estado de cierre si procede. Alcance: medio.

## T9 · Confirmar ahorro al cierre — punto 6

- [ ] Registrar solo nuevas aportaciones confirmadas y repartir entre objetivos de la misma moneda.
- [ ] Reabrir o repetir cierre no duplica ahorro; correcciones quedan trazables y borrar periodo conserva ahorro real.
- [ ] Abrir siguiente periodo exige confirmar ingreso/fechas; copias conservan cierre e historial.

Dependencias: T8 y T2. Verificación: pruebas de cierre repetido, corrección, borrado e importación; recorrido de dos periodos en teléfono. Áreas: SavingsGoals, Budget, Screens y Backup. Alcance: medio.

## T10 · Última copia completada — punto 7

- [ ] Mostrar fecha de exportación completada en el dispositivo; no actualizar al cancelar o fallar.
- [ ] Importar no inventa una exportación reciente; explicar que la fecha no verifica la existencia del archivo.
- [ ] Mantener aviso de copia JSON sin cifrado y selector del sistema.

Dependencias: control B. Verificación: exportar, cancelar, provocar fallo controlado y reiniciar; pruebas del estado donde aporten valor. Áreas: SaviaApp, Forms y preferencias locales. Alcance: pequeño/medio.

## T11 · Recordatorio de copia — punto 7

- [ ] Frecuencia configurable, activación explícita, posponer y desactivar.
- [ ] Reutilizar avisos de T6; contemplar permiso denegado y recalcular después de exportación correcta.
- [ ] Aviso abre exportación y nunca afirma que existe una copia nueva antes de completarla.

Dependencias: T10 y T6; ejecutar tras T9. Verificación: pruebas de próxima fecha y prueba real con permisos/posposición/desactivación. Áreas: recordatorios y Forms/ajustes. Alcance: medio.

## Control C · Entrega propuesta 2.4.0

- [ ] T8–T11 completas; pruebas Android, migraciones y revisión correctas; PR integrado con protección vigente.
- [ ] Teléfono: presupuesto → pagos/gastos → objetivos → cierre → siguiente periodo → exportar/importar; todas las cifras conservadas.
- [ ] Validación con testers de los tres conceptos; release firmada verificada, notas/checkpoint y ramas integradas eliminadas.

No marcar casillas por previsión: registrar únicamente comprobaciones realizadas. Al retomar, verificar el estado real antes de ejecutar tareas.

## Avance de desarrollo · 6/11/2026

T1–T11 implementadas en la rama de desarrollo; las casillas compuestas con pruebas de teléfono/publicación permanecen pendientes hasta comprobarlas. El modelo de ahorro, pagos, límites, cierre y migraciones v1–v4 tiene pruebas JVM. Compilación Android y revisión se registran en la PR. Comportamiento y límites en `docs/desarrollo-siete-mejoras.md`. Sin nueva release ni pruebas de teléfono atribuidas al usuario.
