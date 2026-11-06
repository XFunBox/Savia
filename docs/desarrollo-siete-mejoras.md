# Desarrollo de las siete mejoras de Savia

Implementación en rama `feat/plan-mejoras-savia`, a partir de 2.1.0. Este documento describe el desarrollo; no anuncia una versión publicada. El plan de entregas y sus controles en teléfono siguen en `tasks/plan.md` y `tasks/todo.md`.

## Comportamiento

- Objetivos: el saldo anterior se conserva como saldo inicial sin fecha inventada. Las aportaciones y retiradas tienen identificador estable, fecha, importe, nota y periodo opcional. Se pueden corregir o eliminar; cada saldo histórico debe seguir siendo válido. Borrar un periodo conserva el ahorro y desliga su referencia.
- Reserva y ahorro: registrar una aportación dentro de la reserva no descuenta el disponible otra vez. Solo el ahorro adicional neto vinculado al periodo reduce el disponible. El ahorro externo conserva su moneda y no altera otro periodo.
- Fecha de meta opcional: la sugerencia divide lo pendiente entre los cobros configurados desde hoy hasta la fecha inclusive, ajustando días cortos y redondeando al céntimo superior. Explica metas cumplidas/vencidas, moneda incompatible y calendario insuficiente. No modifica el presupuesto.
- Guía reabrible en ajustes: ingresos, pagos, diarios, reserva y revisión; permite volver y omitir. El ejemplo es exclusivamente texto ilustrativo.
- Pagos: por periodo, mensuales con día o anuales con día y mes. Los días inexistentes vencen el último día del mes. Cada ciclo conserva sus vencimientos completos, sin segunda provisión anual. La media mensual anual en ajustes es orientativa; al crear un periodo se usa el pago completo que vence en él. Confirmar fecha crea un solo movimiento; ver la lista o el aviso no paga nada. Los cambios de ajustes afectan a próximos periodos.
- Límites opcionales por categoría: cada ciclo conserva su reparto. Gastado/límite/restante se actualizan con movimientos sin impedir sobrecostes ni descontar dos veces. La comparación ignora espacios exteriores y mayúsculas; no fusiona nombres diferentes.
- Cierre: muestra previsto/real, pendientes y ahorro neto registrado. Guardar una revisión no genera ingresos ni ahorro. Repartir al cerrar requiere confirmar importes reales por objetivo de la misma moneda; sustituye únicamente aportaciones del cierre anterior y conserva las manuales. No supera el saldo tras pagos pendientes. Una huella del periodo señala cambios posteriores. Corregir un cierre vuelve a validar el historial; retiradas se registran en el objetivo.
- Avisos locales voluntarios: comprobación diaria inexacta, aproximadamente desde las 9:00; Android puede retrasarla. Permiso de notificaciones solicitado al activar en Android 13+. Reprogramación tras cambios, reinicio, actualización y cambios horarios. Los avisos abren Inicio o la sección de copias; nunca contabilizan pagos.
- Copias: formato v4 con lectura v1–v4. Conserva historial, fechas, límites, vencimientos y revisiones; valida tipos, referencias y límites antes de reemplazar. La última exportación se anota solo tras cerrar correctamente el archivo. Cancelación, fallo e importación no la adelantan. Preferencias de avisos, posposición y fecha de exportación pertenecen al dispositivo y no se importan. Recordatorio opcional de 7/14/30 días, con posposición de un día.

## Verificación y salida

Pruebas JVM del modelo disponibles en `app/src/test`. La compilación Android, lint y controles de publicación se ejecutan en la CI de la PR. La validación de interfaz, permisos, reinicio y copia/recuperación en teléfono debe registrarse separadamente; no se deduce del resultado de CI. No se ha cambiado versión, firmado una APK de publicación ni creado un tag.

Antes de publicar: comprobar en el teléfono actualización con firma definitiva, exportación/importación sin pérdidas, aportación/retirada/corrección, pagos anuales y pendientes, notificaciones concedidas/denegadas, límites, cierre repetido y dos periodos. Mantener los controles A/B/C del plan y decidir el alcance final de cada versión con esos resultados.
