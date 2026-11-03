# Objetivos y presupuesto Implementation Plan

> Ejecución directa y proporcional conforme a las preferencias del usuario; una revisión independiente al finalizar.

**Goal:** Varios objetivos y un presupuesto comprensible, con borrado selectivo de periodos.
**Architecture:** Extender AppData con objetivos independientes; mantener los cálculos históricos de periodos. UI Compose con formularios existentes y persistencia atómica actual.
**Tech Stack:** Kotlin, Compose, JSON, JUnit.
**Spec:** ../specs/2026-11-03-objetivos-design.md

## Global Constraints
Céntimos Long; Android 12+; sin dependencias nuevas, Internet, cuentas ni telemetría. Conservar datos v1. No publicar ni fusionar.

## Review Focus
- Importación antigua conserva todos los importes, sin convertir previsión en ahorro real.
- Borrar un periodo elimina solo sus movimientos, sin tocar objetivos.
- Varias monedas no se suman ni cambian importes de objetivos existentes.
- Aportaciones y ahorro registrado no se descuentan otra vez del presupuesto.
- Error de escritura deja el formulario abierto y los datos anteriores conservados.

### Task 1: Modelo y persistencia
Files: Budget.kt, Backup.kt, SavingsGoals.kt, SavingsGoalsTest.kt, BackupTest.kt.
- [ ] Pruebas primero: borrado selectivo, múltiples objetivos, importación v1, round trip v2 y límites inválidos.
- [ ] Implementar SavingsGoal(id,name,targetAmount,savedAmount,periodAmount,currency), AppData.goals y deleteCycle(data,cycleId).
- [ ] Comprobar pruebas y preservar validación existente.

### Task 2: Interfaz
Files: ui/GoalsScreen.kt, ui/Forms.kt, ui/Screens.kt, ui/SaviaApp.kt.
- [ ] Objetivos accesibles desde navegación, CRUD con confirmación de borrado y guardado sin cierre prematuro.
- [ ] Presupuesto en cuatro bloques y desglose vivo; distinguir previsión, reserva y ahorro registrado.
- [ ] Borrado de periodo en resumen, conservando configuración y objetivos.

### Task 3: Verificación y entrega
- [ ] Ejecutar testDebugUnitTest assembleDebug lintDebug; corregir fallos concretos.
- [ ] Una revisión independiente de compatibilidad, cálculos y persistencia; corregir hallazgos.
- [ ] Documentar cambios y validación, crear PR revisable sin publicar ni fusionar.
