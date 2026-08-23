# ADR 0023: precondición transaccional para lotes de jornadas V2

- Estado: aceptada
- Fecha: 2026-08-23

## Contexto

La carga manual V2 revisa jornadas existentes para decidir reemplazos,
segundas jornadas, superposiciones y descansos cortos. Entre esa revisión y el
guardado, otra escritura local puede cambiar la ocupación. Revalidar sólo las
filas que se van a borrar no detecta una jornada nueva, movida o actualizada en
la misma ventana y permitiría confirmar un resultado distinto del revisado.

El lote ya se persiste mediante una transacción Room. No hace falta agregar
estado persistente ni cambiar el esquema para cerrar esta ventana.

## Decisión

Para la carga de jornadas nuevas, `V2ShiftRepository.applyV2Batch(...)` exige
dos objetos separados:

1. `V2ShiftBatchMutation`, que describe las escrituras autorizadas;
2. `ShiftOccupancyExpectation`, que fotografía las jornadas observadas durante
   la revisión mediante ID, fecha local, inicio, fin, estado y `updatedAt`.

La firma admite además `expectedUpdates`, vacío por defecto para las altas. Las
ediciones V2 lo usan para comparar el par histórico completo según ADR 0025.

El coordinador captura la ocupación desde dos días antes de la primera fecha
elegida hasta dos días después de la última, la misma ventana usada para
evaluar descansos vecinos. La expectativa no se guarda en `SavedStateHandle`:
al reconstruir una revisión se vuelve a leer el estado actual.

`RoomV2ShiftRepository` consulta nuevamente todas las jornadas de ese rango y
compara la fotografía dentro de la misma transacción que aplicaría el lote. La
comparación ocurre antes de borrar, insertar, actualizar o limpiar estados
explícitos. Una diferencia lanza `ConflictingLocalWriteException`; la
transacción no escribe nada y la interfaz conserva fechas, plantilla y puesto,
pero invalida políticas y advertencias para exigir una revisión nueva.

`ShiftDao` agrega únicamente una lectura suspendida equivalente a su `Flow`
existente. Room continúa en versión 7: no cambian tablas, columnas, índices,
entidades, relaciones, migraciones ni JSON de esquema.

## Consecuencias

- dos guardados preparados contra la misma ocupación no pueden confirmarse
  silenciosamente uno detrás del otro;
- altas, bajas, movimientos, cambios de horario, estado o versión dentro de la
  ventana invalidan la revisión;
- el chequeo y las escrituras quedan serializados por una sola transacción;
- los borradores recuperables no conservan una precondición vieja;
- consumidores futuros del repositorio deben proporcionar explícitamente la
  ocupación que revisaron.

La precondición protege las jornadas que determinan ocupación, superposición y
descanso. No convierte en una transacción conjunta la configuración, el
catálogo o las carpetas médicas, que mantienen sus contratos propios.

## Alternativas descartadas

### Confiar en la huella de la pantalla

La huella visual incluye catálogo, textos y confirmaciones que el repositorio
no puede reconstruir. No demuestra que la ocupación siga igual.

### Comparar sólo cantidad, IDs o el máximo `updatedAt`

Esas aproximaciones omiten reemplazos equivalentes, movimientos y cambios de
estado u horario.

### Agregar una tabla o versión global

Persistir un contador ampliaría Room y obligaría a coordinar todas las rutas de
escritura. La lectura y comparación dentro de la transacción resuelven este
caso sin migración.

## Verificación

- pruebas puras de captura, orden, rangos e IDs duplicados;
- prueba Room con dos escritores que parten de la misma expectativa y rechazo
  del segundo sin una segunda jornada o fotografía;
- pruebas transaccionales existentes de rollback completo del lote;
- prueba de aplicación que conserva el borrador e invalida la revisión ante
  `ConflictingLocalWriteException`;
- comprobación byte a byte del esquema Room v7.

Queda como endurecimiento posterior agregar casos Room dedicados para conflicto
durante un reemplazo con limpieza de `F/?`, modificación del mismo UUID y un
cambio fuera de la ventana. La revisión independiente no encontró un defecto
en esos caminos, pero hoy se demuestran por el contrato común y no por una
prueba individual de cada combinación.
