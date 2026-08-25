# ADR 0028: horario real separado y extras exactas por jornada

- Estado: aceptado
- Fecha: 2026-08-25

## Contexto

MiGuardia 2.0 ya guarda jornadas planificadas como un par obligatorio
`Shift + ShiftWorkSnapshot`. También permite materializarlas mediante carga
manual o planes recurrentes, y modificar o eliminar conscientemente una
jornada concreta.

La siguiente necesidad es registrar qué horario se trabajó realmente y, si la
duración fue mayor que la planificada, decidir si esa diferencia continúa
siendo trabajo habitual o corresponde a una clase de horas extra.

`Shift.startAt` y `Shift.endAt` no pueden reutilizarse para el horario real:
son la planificación histórica, alimentan Calendario, próximo evento,
notificaciones y recurrencias, y deben continuar siendo estables. Tampoco es
correcto guardar sólo una cantidad de minutos extra, porque se perderían los
instantes necesarios para medianoche, feriados, noche y futuras reglas por
tramos civiles.

La documentación todavía no define qué meta debe prevalecer si la referencia
de horas cambia en medio de una semana o de un ciclo. Por eso este bloque no
calcula todavía avance ni cumplimiento.

## Decisión

### 1. Planificación y realidad son datos distintos

Cada jornada puede tener como máximo un registro opcional de horario real.
`Shift` y `ShiftWorkSnapshot` conservan la planificación; el nuevo registro
guarda los instantes reales.

Sin registro real, los cálculos posteriores usarán el horario planificado. Si
los instantes reales coinciden exactamente con los planificados no se guarda
una corrección redundante. Cuando difieren, se exige un motivo breve y se
admite una explicación opcional.

El intervalo real usa minutos enteros y semántica `[inicio, fin)`. Debe haber
terminado según `Clock` inyectable. Puede comenzar o finalizar en otro día
civil. En este bloque, su celda dueña del Calendario continúa siendo la fecha
planificada; la atribución de horas a un día, mes o período se resuelve en el
motor siguiente usando los instantes reales. La interfaz muestra fecha y hora
explícitas para ambos extremos. La zona es la de la jornada y permanece
visible e inmutable. Una hora local inexistente por un cambio de zona se
rechaza; una hora ambigua exige elegir explícitamente cuál de sus dos offsets
corresponde.

Sólo una jornada en estado `PLANNED` acepta horario real y el alta se ofrece
desde que el instante planificado de fin fue alcanzado. Una corrección ya
existente continúa accesible. No se introduce un estado `COMPLETED` ni se
modifica automáticamente el estado estructural.

### 2. La diferencia mayor se clasifica conscientemente

Si la duración real es menor o igual que la planificada, todo el intervalo real
es trabajo habitual y no puede contener extras.

Si la duración real es mayor, MiGuardia pregunta si toda la diferencia:

- continúa como trabajo habitual; o
- se clasifica como una clase extra elegida por la persona.

Cuando se clasifica como extra, se guardan uno o más intervalos exactos,
positivos, disjuntos y contenidos dentro del horario real. La suma de esos
intervalos debe coincidir exactamente con la diferencia de duración. Esto
permite representar, por ejemplo, una entrada anticipada y una salida tardía
como dos fragmentos sin contar minutos dos veces. Una sola clase elegida se
aplica a todos los fragmentos de esa diferencia. Los fragmentos los elige la
persona dentro de las porciones del horario real que quedan fuera del
intervalo planificado. No se seleccionan automáticamente y su suma debe ser
exactamente la diferencia.

La aplicación nunca crea extras por superar una referencia, cubrir a otra
persona, trabajar de noche, en fin de semana o en feriado. Una cobertura
completa sigue siendo habitual salvo que exista tiempo adicional concreto y la
persona lo clasifique expresamente.

`Horas extras`, `Extensión de turno` y `Servicio extra` son sugerencias de
nombre, no filas ni valores predeterminados. Al crear una clase se elige
conscientemente si ayuda a cumplir la referencia y si tendrá desglose propio.
Renombrar o archivar una clase no reinterpreta registros históricos.
Conservar sin cambios una clasificación anterior puede seguir usando su clase
archivada y su fotografía; cualquier elección nueva exige una clase activa.
La clase archivada puede reactivarse conscientemente. La fotografía se valida
contra la clase observada al crear o reclasificar, pero puede diferir
legítimamente del catálogo actual.

### 3. Alcance deliberadamente acotado

Este bloque clasifica únicamente tiempo adicional de una jornada planificada
existente. No crea trabajo extra independiente sin jornada dueña.

El trabajo independiente necesita definir su propia fotografía laboral,
identidad en el Calendario y relación con lugares, tipos y próximos eventos.
Se estabilizará junto con el motor de trabajo y avance antes de implementarlo.
Esta separación evita inventar ahora un segundo tipo de jornada o reutilizar
`Shift` con una semántica falsa.

Tampoco se calcula avance contra una referencia. La siguiente dependencia debe
resolver primero qué ocurre cuando una referencia cambia dentro de una semana
o ciclo y recién después sumar trabajo habitual y clases extra elegibles.

### 4. Persistencia Room V2 versión 3

La migración explícita `2→3` agrega tres tablas y un índice único compuesto
necesario para la integridad referencial.

#### `extra_work_classes`

Catálogo reutilizable:

- `id`;
- `timelineId` y `sector`;
- `name` y `normalizedNameKey`;
- `helpsMeetHoursReference`;
- `showDedicatedSummary`;
- `isActive`;
- `createdAtEpochMillis` y `updatedAtEpochMillis`.

`timelineId` referencia la raíz de configuración con `RESTRICT`. Son únicos
`timelineId + sector + normalizedNameKey` e `id + timelineId + sector`.
Las clases se archivan; no se eliminan si ya tienen historia.

#### `shift_actual_records`

Una fila opcional por jornada:

- `shiftId` como clave primaria;
- `timelineId` y `sector`;
- `actualStartEpochMillis` y `actualEndEpochMillis`;
- `differenceReason`;
- `explanation` opcional;
- `createdAtEpochMillis` y `updatedAtEpochMillis`.

La clave compuesta `shiftId + timelineId + sector` referencia con `RESTRICT` a
la misma combinación en `shift_work_snapshots`. El esquema agrega a la tabla
padre el índice único compuesto requerido y declara también única esa
combinación en `shift_actual_records`. La restricción impide que una escritura
estructural antigua borre silenciosamente el horario real.

#### `shift_extra_intervals`

Cada fragmento extra contiene:

- `id`;
- `shiftId`, `timelineId` y `sector`;
- `extraWorkClassId`;
- `startEpochMillis` y `endEpochMillis`;
- fotografía histórica del nombre de la clase;
- fotografía de `helpsMeetHoursReference` y `showDedicatedSummary`;
- `createdAtEpochMillis` y `updatedAtEpochMillis`.

La combinación `shiftId + timelineId + sector` referencia con `CASCADE` al
registro real dueño. `extraWorkClassId + timelineId + sector` referencia con
`RESTRICT` a la clase elegida. Posee índices por jornada y clase, y no admite
dos filas con la misma combinación
`shiftId + startEpochMillis + endEpochMillis`. La integridad exige además que
todos los fragmentos de una corrección compartan la misma clase.
Los índices hijos cubren exactamente
`shiftId + timelineId + sector` y
`extraWorkClassId + timelineId + sector`.

Room no guarda totales, cumplimiento ni agregados mensuales. Se derivan de los
intervalos exactos para evitar fuentes de verdad duplicadas.

### 5. Fronteras, atomicidad y concurrencia

Una frontera específica de horario real es la única autorizada para crear,
corregir o quitar el agregado formado por:

- registro real;
- cero o más fragmentos extra;
- la única clase elegida observada o su ausencia.

La escritura compara por valor completo, dentro de una única transacción:

- `Shift + ShiftWorkSnapshot`;
- el registro real anterior o su ausencia;
- todos sus fragmentos;
- la única clase elegida o su ausencia;
- la ocurrencia recurrente vinculada o su ausencia.
- notas, configuración particular de avisos, estado explícito, carpeta médica,
  vacaciones, feriado y toda otra fila consultada para protección o
  advertencias.

Un conflicto no escribe parcialmente y exige refrescar. Los totales derivados
no forman parte del CAS.

Si la clase nace dentro del flujo de horario real, permanece como borrador
hasta la confirmación y se crea en la misma transacción que el registro y sus
fragmentos. Cancelar no deja una clase huérfana. La administración general del
catálogo es una acción consciente independiente.

`V2ShiftRepository` continúa siendo la única frontera estructural de jornadas
y debe volverse consciente del agregado real. Eliminar una jornada con horario
real requiere confirmación explícita y elimina, en la misma transacción, sus
fragmentos, el registro real y luego el par. Cambiar el intervalo planificado
mientras existe horario real se bloquea hasta que la persona vuelva
explícitamente al horario planificado. Corregir el registro real no desbloquea
ese cambio. Un cambio que conserva los instantes planificados puede mantenerlo.

Registrar horario real no convierte por sí solo una ocurrencia recurrente en
`CUSTOMIZED`: la regla del plan no fue editada. Mientras el registro existe,
la ocurrencia deja de ser automática intacta y no puede retirarse ni
reemplazarse silenciosamente. Si se vuelve conscientemente al horario
planificado, recupera su elegibilidad automática siempre que no tenga otra
protección.

### 6. Presentación mínima

El detalle de cada jornada exacta muestra:

- horario planificado;
- horario real, cuando existe;
- motivo y explicación;
- minutos habituales;
- cada fragmento extra con su clase;
- total real sin doble conteo.

La acción es `Registrar horario real` o `Corregir horario real` según el
estado. `Volver al horario planificado` pide confirmación y elimina sólo el
registro real y sus extras derivadas.

Ingresar sobre una corrección los mismos instantes planificados no borra nada
ni simula éxito: dirige a la confirmación explícita
`Volver al horario planificado`.

El formulario identifica `Jornada N de M` y UUID, conserva la fecha dueña,
permite revisar antes de guardar y recupera su borrador con
`SavedStateHandle`. El catálogo de clases se administra desde
`Mi forma de trabajar` y también puede ampliarse dentro del flujo.

Las celdas del Calendario, próximo evento y notificaciones continúan usando la
planificación. El detalle es la primera superficie que distingue planificado y
real. Por este recorte, una salida real anticipada no modifica un próximo
evento ni un aviso hasta el fin planificado. El alta recién se habilita al
alcanzar ese fin y la limitación es visible. La presentación agregada
corresponde a bloques posteriores.

## Consecuencias

- `MiGuardiaV2Database` pasa de versión 2 a 3 y conserva la cadena
  `1→2→3`.
- Los esquemas `1.json` y `2.json` permanecen byte a byte intactos y se
  exporta `3.json`.
- Las veintidós tablas existentes se preservan y la base queda con
  veinticinco tablas de aplicación.
- Los cálculos posteriores podrán usar intervalos reales y extras sin
  reinterpretar la planificación.
- Las situaciones especiales pueden coexistir con el registro, pero este
  bloque no decide todavía su efecto sobre horas o cumplimiento.

## Alternativas descartadas

### Sobrescribir `Shift.startAt` y `Shift.endAt`

Destruiría la comparación entre lo planificado y lo real y alteraría
recurrencias, próximos eventos y avisos.

### Guardar sólo minutos extra

Impediría clasificar correctamente medianoche, noche, feriados y fines de
semana, y permitiría solapamientos invisibles.

### Calcular ahora el avance contra la referencia

Inventaría una regla todavía abierta para cambios de referencia dentro de una
semana o ciclo.

### Marcar toda ocurrencia como `CUSTOMIZED`

Confundiría una observación de trabajo real con una modificación estructural
del plan y seguiría protegiendo la ocurrencia aun después de quitar la
corrección.

### Incluir extras independientes sin fotografía propia

Las convertiría en jornadas planificadas falsas o las dejaría sin contexto
laboral histórico.
