# ADR 0007: vacaciones y persistencia local v3

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-14
- Autoridad: MAIN, después de auditar la entrega de `VACACIONES`

## Contexto

MiGuardia necesita registrar vacaciones manuales que pueden atravesar meses y años, mostrarlas en el Calendario y excluir guardias normales de los cálculos de horas. El dato representa días civiles gozados y no una equivalencia fija de horas. Room v2 ya contiene nueve familias de datos que deben preservarse sin cambios.

## Decisión

- Vacaciones se persiste como un período con fecha inicial y final inclusivas; no se crea una fila por día.
- Dos períodos de vacaciones no pueden compartir fechas. Los períodos contiguos son válidos y permanecen separados.
- Vacaciones no puede intersectar una carpeta médica. Ambos sentidos de escritura validan el conflicto dentro de una transacción Room y nunca recortan ni eliminan datos automáticamente.
- El Calendario deriva `V` para cada fecha aplicable y muestra únicamente ese indicador en la celda. Los feriados, `F`, `?`, guardias y días implícitamente sin definir coincidentes se conservan y permanecen disponibles en el detalle y la descripción accesible.
- Una guardia `PLANNED` cuya `localStartDate` cae en vacaciones permanece persistida, pero queda fuera de horas planificadas, trabajadas, pendientes, extra, nocturnas y feriadas.
- `ABSENT` y `CANCELLED` prevalecen sobre vacaciones y conservan sus categorías actuales. `shiftCount` continúa contando registros de guardia.
- Resumen agrega únicamente `vacationDayCount`, calculado como fechas civiles únicas recortadas al mes. No existe `vacationHours`.
- `ShiftStatus` no incorpora `VACATION` ni `COMPLETED`.
- Room pasa a versión 3 con una única tabla nueva `vacations`, índices por fecha inicial y final, y sin claves foráneas.
- `MIGRATION_2_3` crea solamente esa tabla y sus índices. La cadena `MIGRATION_1_2` + `MIGRATION_2_3` conserva las nueve tablas v2 y las cinco familias originales de v1.

## Límite monetario

Este incremento no calcula ni persiste dinero. MiGuardia no incorpora tablas
salariales, estimaciones remunerativas ni liquidaciones.

## Consecuencias

- Calendario y Resumen reaccionan a un nuevo `VacationRepository` sin acoplar su lógica pura a Room o Android.
- La exclusión por vacaciones es reversible porque nunca modifica la guardia histórica.
- La integridad Vacaciones/Carpeta médica queda protegida aunque una interfaz omita la validación previa.
- Los esquemas Room v1 y v2 permanecen publicados e inmutables.
- Cualquier cálculo salarial, saldo de días, aprobación empresarial o automatización por antigüedad queda fuera de este módulo.
