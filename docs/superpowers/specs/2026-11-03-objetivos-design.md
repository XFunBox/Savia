# Objetivos y presupuesto — diseño aprobado en conversación

Un presupuesto común con varios objetivos, en lugar de presupuestos independientes.

- Presupuesto: ordenar ingresos, gastos fijos, gastos diarios y ahorro por periodo; enseñar un desglose vivo y avisar si se pide ahorrar más de lo previsto. Los gastos diarios dejan de estar ocultos dentro de la meta.
- Objetivos: nombre, cantidad total deseada, importe ya ahorrado, aportación prevista por periodo y moneda. Crear, editar y borrar varios. El importe ahorrado se actualiza manualmente; no se inventan transferencias. Las aportaciones reparten la reserva común, no descuentan de nuevo el disponible. Avisar cuando su suma supera la reserva o la previsión. No sumar monedas diferentes.
- Inicio: distinguir ahorro reservado de previsión y facilitar acceso a objetivos, también sin periodos.
- Periodos: borrado desde Resumen con fechas y número de movimientos en confirmación. Se eliminan únicamente ese periodo y sus movimientos; permanecen configuración y objetivos. Permitir empezar de nuevo tras borrar el último.
- Compatibilidad: copia JSON v2, aceptar v1 conservando ajustes, periodos y movimientos; objetivos inicialmente vacíos. No transformar una reserva de un periodo en ahorro confirmado.
- Conservar céntimos Long, Android 12+, Compose, almacenamiento local y tema actual. Sin dependencias nuevas, Internet, cuentas ni telemetría. No publicar una versión ni fusionar PR durante esta tarea.

Comprobación: pruebas de objetivos y borrado selectivo, importación v1 y round trip v2, rechazo de datos inválidos, testDebugUnitTest assembleDebug lintDebug. Prueba visual en dispositivo si existe; declarar pendiente si no.
