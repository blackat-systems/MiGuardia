# ADR 0006: novedades, feriados y notas privadas

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-13
- Autoridad: prompt especializado `NOVEDADES_FERIADOS_Y_NOTAS`

## Contexto

MiGuardia necesita feriados locales reactivos, notas privadas por guardia y correcciones operativas que pueden modificar estado u horas sin perder el plan original. Estas escrituras se relacionan con guardias existentes y deben sobrevivir actualizaciones sin dañar las cinco familias de datos publicadas en Room v1.

## Decisión

- Room pasa de v1 a v2 mediante una migración explícita no destructiva.
- Se agregan `holidays`, `shift_notes`, `shift_novelties` y `formal_shift_changes`; las cinco tablas v1 no cambian.
- Los feriados son únicos por fecha civil y los lotes resuelven conflictos mediante reemplazo o conservación dentro de una transacción.
- Notas y novedades dependen de la guardia mediante claves foráneas. Las notas y descripciones permanecen privadas y no alimentan calendario ni Resumen.
- Ausencia y cancelación se escriben junto con el estado de la guardia. Volver a `PLANNED` elimina la novedad controladora y recupera la proyección temporal por reloj.
- Antes de confirmar ausencia o cancelación, la interfaz ofrece `+ Agregar descripción opcional`; el texto se guarda en la novedad controladora existente, sin migración adicional y sin exposición en Calendario.
- Los cambios formales conservan una instantánea estructurada original y otra final. Correcciones posteriores no reemplazan el original y la restauración usa comparación optimista para evitar sobrescribir un cambio concurrente.
- Una segunda guardia es una `Shift` independiente enlazada mediante una novedad y ambas piezas se crean o eliminan atómicamente.
- Calendario observa feriados del mes. Resumen observa además el día posterior al fin para clasificar correctamente guardias nocturnas atribuidas al mes inicial.
- Las advertencias de cambio formal y segunda guardia se calculan con las guardias reales cercanas antes de confirmar superposiciones, dobles guardias o descansos menores a doce horas.
- Las eliminaciones iniciadas desde la interfaz requieren confirmación y los repositorios rechazan que un UUID existente de nota o novedad sea reutilizado para trasladar el registro a otra guardia.
- La edición de un feriado conserva su UUID y puede actualizar el nombre aun cuando su fecha no cambie; las políticas de conflicto continúan aplicándose frente a otros feriados.

## Consecuencias

- El esquema exportado queda en versión 2 y toda instalación v1 conserva sus filas.
- `ShiftStatus` permanece limitado a `PLANNED`, `CANCELLED` y `ABSENT`; `COMPLETED` continúa derivado.
- Los cálculos existentes no cambian: solo reciben las fechas feriadas reales y las guardias formalmente corregidas.
- No se agregan dependencias, permisos, red, telemetría ni almacenamiento externo.
- La auditoría de MAIN aprobó la migración real v1→v2, la batería global y el recorrido físico en el Samsung Galaxy S25 Ultra.
