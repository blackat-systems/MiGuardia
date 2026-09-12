# ADR 0025: CAS del par histórico al editar o eliminar jornadas V2

- Estado: aceptada
- Fecha: 2026-08-23

## Contexto

Una jornada V2 está formada por dos filas inseparables: `Shift`, con sus datos
operativos, y `ShiftWorkSnapshot`, con la fotografía laboral histórica. La
persona revisa ambas antes de confirmar una edición o eliminación. Entre esa
revisión y la transacción, otra escritura local podría modificar cualquiera de
las dos filas o las jornadas vecinas que determinaron advertencias de
superposición y descanso.

Comparar sólo `updatedAt`, el UUID o la ocupación mensual no detecta todos esos
cambios. Tampoco es válido reemplazar silenciosamente una plantilla histórica
que fue archivada después de abrir el borrador.

## Decisión

La edición entrega a `applyV2Batch(...)` un `V2ShiftWriteExpectation` inmutable
que fotografía el par completo observado. La eliminación entrega directamente
el `V2ShiftWrite` completo que fue confirmado. Room vuelve a leer y compara el
par en ambos casos dentro de la misma transacción que escribiría o eliminaría.

La edición agrega además la `ShiftOccupancyExpectation` definida por ADR 0023
para la ventana vecina usada al calcular superposiciones y descansos. Si cambia
el par o esa ocupación, se lanza `ConflictingLocalWriteException` antes de toda
mutación y la interfaz obliga a revisar.

Editar conserva el UUID, la fecha y la creación originales. Elegir una
plantilla activa construye un par nuevo coherente para esa fecha. Cambiar sólo
el puesto puede conservar fuentes archivadas únicamente cuando todo el resto
del par histórico permanece idéntico.

Eliminar borra exactamente el par confirmado y sus dependencias propias dentro
de una transacción. No elimina catálogo, configuración, estados `F/?`, feriados,
vacaciones, carpetas médicas ni jornadas compañeras. Los enlaces externos que
apuntan a la jornada borrada se retiran para no conservar referencias rotas.

`updatedAt` se normaliza a precisión de milisegundos, compatible con Room, y una
edición válida siempre lo hace avanzar estrictamente. Room permanece en versión
7: no cambian entidades, columnas, DAO, migraciones ni el JSON de esquema.

## Consecuencias

- una confirmación vieja nunca sobreescribe ni borra un par que cambió;
- cambiar solamente la fotografía histórica también invalida la operación;
- las advertencias se apoyan en el mismo vecindario que luego valida Room;
- una plantilla archivada no se sustituye sin decisión de la persona;
- el contrato de concurrencia es reutilizable por futuras operaciones V2 sin
  agregar estado persistente;
- los consumidores deben conservar la expectativa sólo durante la revisión y
  reconstruirla al restaurar una etapa que requiera confirmar.

## Alternativas descartadas

### Comparar solamente `updatedAt`

No protege ante una escritura externa o defectuosa que modifique la fotografía
sin actualizar la fila `Shift`, y reduce una entidad compuesta a una sola
marca temporal.

### Permitir una actualización ciega por UUID

Podría sobrescribir cambios posteriores a la revisión y mostrar advertencias
calculadas sobre una ocupación que ya no existe.

### Agregar una versión global o cambiar el esquema

La comparación transaccional de las filas existentes resuelve el caso sin
ampliar Room ni coordinar un contador entre todos los escritores.

## Verificación

- pruebas puras de captura inmutable, duplicados y edición de sólo puesto;
- pruebas Room de cambio concurrente en `Shift`, sólo en
  `ShiftWorkSnapshot` y en el vecindario observado;
- pruebas Room de rollback, desaparición de la jornada objetivo y matriz exacta de
  eliminación;
- pruebas de coordinador para doble toque, conflicto, error, reintento,
  restauración y cambios de raíz o timeline;
- recorrido integral en Samsung con dos jornadas, recreación, rotación, cambio
  de plantilla, cancelación y eliminación exacta;
- comprobación byte a byte del esquema Room v7.
