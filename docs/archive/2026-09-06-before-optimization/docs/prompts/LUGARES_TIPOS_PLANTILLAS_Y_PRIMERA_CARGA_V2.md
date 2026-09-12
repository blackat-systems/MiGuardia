# Lugares, tipos, plantillas y primera carga V2

- Estado: **PAUSADO — CONTRATO MARCO DE REFERENCIA, NO EJECUTAR COMPLETO**
- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- Dependencias cerradas: dominio laboral configurable y Room v6
- Nombre humano: **Elegir el sector, preparar el primer lugar y cargar jornadas**

> Actualización 2026-08-23: ADR 0024 reemplaza las cláusulas de instalación
> migrada y activación V1→V2 de este contrato de referencia. El catálogo, los
> lugares, tipos, plantillas y fotografías V2 continúan vigentes.

## TASK

Construir el primer recorrido utilizable de MiGuardia 2.0:

1. una instalación nueva elige uno de los cuatro sectores;
2. entra al Calendario vacío;
3. crea un lugar, su primer tipo de trabajo y una plantilla obligatoria;
4. puede agregar más lugares, tipos o plantillas;
5. puede cargar jornadas manuales individuales o múltiples usando esas
   plantillas;
6. cada jornada V2 conserva también la fotografía histórica del tipo de
   trabajo;
7. una instalación actualizada desde 1.0 sigue funcionando como antes hasta
   activar V2 conscientemente.

El bloque evoluciona Room de v6 a v7 sólo de manera aditiva. No implementa
recurrencias, horario real, extras, disponibilidad, situaciones especiales ni
el Resumen V2.

## CONTEXT

Room v6 posee diecisiete tablas. Las trece familias de MiGuardia 1.0 siguen
intactas y las cuatro nuevas guardan una única línea temporal de configuración.

La base heredada ya contiene:

- `Objective`: nombre, abreviatura, dirección, nota y estado activo;
- `ScheduleCombination`: objetivo, horario exacto, color y estado activo;
- `Shift`: horario concreto e instantáneas históricas del objetivo, horario,
  color y puesto;
- carga manual de una o varias fechas sobre la única grilla del Calendario;
- advertencias de superposición, segunda jornada y descanso corto.

Esas piezas continúan siendo historia válida. No alcanzan por sí solas para V2:
una plantilla nueva también necesita un tipo de trabajo, y dos tipos diferentes
pueden compartir exactamente el mismo lugar y horario. Por eso no se agrega el
tipo dentro de `ScheduleCombination` ni se cambia su restricción histórica.

Room v6 distingue:

- base nueva sin raíz;
- raíz `MIGRATED_V1` sin revisión V2;
- configuración V2 con una o más revisiones efectivas desde una fecha.

## INPUTS

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/sectores/`;
- ADR 0002, 0003, 0004, 0017, 0020 y 0021;
- `docs/prompts/OBJETIVOS_Y_GUARDIAS.md` sólo como contrato histórico V1;
- `Objective`, `ScheduleCombination`, `Shift` y sus repositorios actuales;
- `WorkConfigurationHistory`, `WorkConfigurationRepository` y Room v6;
- la única grilla mensual y su recorrido de edición explícita vigente.

Si el código real contradice este contrato, MAIN debe resolver primero la
contradicción en la fuente de verdad. No se recupera código desde worktrees
históricos.

## OUTPUT

### Dominio común

Agregar modelos puros equivalentes a estos conceptos, con nombres Kotlin claros:

1. **Lugar V2**
   - reutiliza un `Objective` como fuente de nombre, abreviatura, dirección,
     nota y timestamps;
   - posee un UUID V2 propio y un estado activo o archivado independiente;
   - declara la línea temporal y el sector a los que pertenece;
   - una abreviatura nueva requiere entre tres y cinco caracteres en mayúscula;
   - una abreviatura histórica de dos caracteres sigue siendo válida y puede
     conservarse sin cambios al adoptar o editar otro dato del lugar, pero no
     puede reemplazarse por otra abreviatura nueva de dos caracteres.

2. **Tipo de trabajo**
   - UUID;
   - línea temporal y sector;
   - nombre personalizable;
   - comportamiento codificado; este bloque sólo crea `ACTIVE_WORK`;
   - activo o archivado;
   - timestamps controlables.

   El nombre visible nunca decide el cálculo. `Consultorio` puede ser un tipo
   de trabajo activo. `Capacitación` necesita la elección paga/no paga en cada
   carga y no se crea ni se ofrece como tipo en este bloque; su comportamiento
   se habilitará junto con las situaciones especiales. La estructura queda
   preparada para otro código de comportamiento sin convertir ambos conceptos
   en el mismo registro.

3. **Plantilla V2**
   - UUID;
   - lugar;
   - tipo de trabajo;
   - inicio y final exactos en minutos enteros;
   - color ARGB;
   - activa o archivada;
   - combinación V1 de origen opcional, sólo como procedencia;
   - timestamps controlables.

   Inicio igual a final representa veinticuatro horas. La unicidad se aplica a
   `lugar + tipo + inicio + final`: dos tipos activos diferentes pueden
   coincidir en lugar y horario sin mezclarse.

4. **Revisión de reglas del lugar**
   - UUID, lugar y fecha efectiva;
   - `WorkplaceRules` completo;
   - historial insert-only;
   - una sola revisión por lugar y fecha.

5. **Fotografía laboral de una jornada V2**
   - ID de la jornada;
   - revisión de configuración aplicable;
   - lugar V2 y objetivo físico de origen;
   - plantilla y tipo de origen;
   - nombre histórico del tipo;
   - comportamiento histórico del tipo;
   - sector histórico.

   El puesto o función opcional continúa en `Shift.position`. La fotografía V2
   complementa, no duplica, las instantáneas de lugar, horario y color que ya
   guarda `Shift`.

Las reglas del lugar no se congelan en una única fotografía porque una jornada
puede atravesar medianoche y, con ella, la fecha efectiva de una regla nueva.
Cuando el motor las necesite, divide la jornada en tramos por día local y
resuelve la revisión del lugar aplicable a cada fecha. La configuración global
de la jornada sí se fija por su fecha local de inicio.

Las validaciones viven en dominio o en una capa reusable, no solamente en
Compose. Reloj y UUID se reciben explícitamente.

### Repositorios

Exponer contratos de dominio que permitan:

- observar el catálogo de la única línea temporal para un sector;
- observar como máximo cinco plantillas V2 usadas recientemente;
- obtener lugares, tipos, plantillas, reglas vigentes y fotografía de jornada;
- crear de forma atómica el primer conjunto `lugar + regla + tipo habitual +
  plantilla`;
- adoptar conscientemente un `Objective` y, opcionalmente, una
  `ScheduleCombination` V1 existente, sin modificar sus filas;
- crear, editar y archivar lugares, tipos y plantillas posteriores;
- agregar una nueva revisión de reglas desde una fecha;
- extender conscientemente hacia atrás una configuración `NEW_V2` y las reglas
  necesarias cuando la persona quiera retrocargar una fecha anterior;
- guardar o corregir una jornada V2 y su fotografía en la misma transacción;
- borrar una jornada y su fotografía mediante la relación de propiedad, sin
  tocar plantillas ni historia ajena.

No se exponen eliminaciones normales de lugares, tipos, plantillas ni revisiones
V2. Se archivan. Las operaciones V1 existentes permanecen para compatibilidad,
pero la superficie V2 no debe invocarlas.

La escritura V2 no convierte las fotografías en listas opcionales dentro de la
API V1. Debe existir un contrato atómico equivalente a
`V2ShiftWrite(shift, snapshot)` y `applyV2Batch(...)`, con correspondencia
biyectiva obligatoria entre cada jornada insertada/actualizada y su fotografía.
Ese lote puede borrar jornadas V1 o V2 en fechas reemplazadas y limpiar `F` o
`?`, pero nunca puede insertar una jornada V2 sin fotografía ni una fotografía
sin jornada. `ShiftBatchMutation` y `applyBatch` conservan intacta la semántica
V1.

La adopción valida dentro de la misma transacción que el `Objective` exista y
que cualquier `ScheduleCombination` elegida exista, siga perteneciendo a ese
objetivo y conserve el horario esperado. Adoptar otra vez el mismo objetivo en
`línea temporal + sector` reutiliza el mismo `workPlaceId`. Una plantilla exacta
se identifica por `workPlaceId + workTypeId + inicio + final`; el mismo horario
V1 de procedencia puede originar más de una plantilla cuando cambia el tipo. No
existe una restricción única sobre `legacyScheduleCombinationId`.

Si una operación V1 intenta eliminar un `Objective` que ya fue adoptado por V2,
el repositorio la detiene antes de borrar sus horarios y devuelve un error de
dominio claro. Nunca deja que esa situación llegue como un
`SQLiteConstraintException` crudo a la interfaz.

Las plantillas recientes se calculan uniendo `shift_work_snapshots` con
`shifts`, agrupando por plantilla y usando el mayor
`Shift.createdAtEpochMillis` de uso real. Se ordenan de más reciente a más
antigua, con el UUID de plantilla como desempate estable, y el límite permitido
es de uno a cinco. Editar una plantilla o una jornada sin cambiar de plantilla
no actualiza esa marca. Si una jornada cambia conscientemente de plantilla, su
`createdAt` original pasa a contar para la nueva. Nunca se usa `updatedAt` ni la
fecha laboral.

### Room v7

Room v7 agrega exactamente cinco tablas y no altera ninguna de las diecisiete
tablas v6:

1. `work_places`;
2. `work_types`;
3. `work_templates`;
4. `workplace_rule_revisions`;
5. `shift_work_snapshots`.

Campos mínimos congelados:

- `work_places`: `id` —PK propia—, `timelineId`, `sector`, `objectiveId`,
  `isActive`, `createdAtEpochMillis` y `updatedAtEpochMillis`;
- `work_types`: `id`, `timelineId`, `sector`, `name`, `normalizedNameKey`,
  `behavior`, `isActive`, `createdAtEpochMillis` y `updatedAtEpochMillis`;
- `work_templates`: `id`, `timelineId`, `sector`, `workPlaceId`, `objectiveId`,
  `workTypeId`, `startTime`, `endTime`, `colorArgb`, `isActive`,
  `legacyScheduleCombinationId`, `createdAtEpochMillis` y
  `updatedAtEpochMillis`;
- `workplace_rule_revisions`: `id`, `timelineId`, `sector`, `workPlaceId`,
  `objectiveId`, `effectiveFrom`, código nocturno y sus dos horas nullable,
  marcas nocturnas, código de fin de semana y sus marcas, marcas de feriado y
  `createdAtEpochMillis`;
- `shift_work_snapshots`: `shiftId` —también PK—, `timelineId`, `sector`,
  `configurationRevisionId`, `workPlaceId`, `objectiveId`, `templateId`,
  `workTypeId`, `workTypeNameSnapshot` y
  `workTypeBehaviorSnapshot`.

Los campos nullable de reglas sólo pueden estar presentes para el código que
los necesita. Las combinaciones inválidas se rechazan tanto al escribir como al
leer.

El diseño físico debe proteger como mínimo:

- claves UUID como texto;
- fechas y horas ISO;
- códigos explícitos para sector y reglas; nunca `Enum.name` como contrato
  accidental;
- claves foráneas `RESTRICT` desde catálogo e historia;
- `CASCADE` únicamente desde `Shift` hacia su fotografía propia;
- pertenencia coherente a línea temporal, sector y objetivo entre lugar, regla,
  tipo, plantilla y fotografía;
- un solo lugar V2 por `línea temporal + sector + Objective`;
- nombre de tipo único dentro de línea temporal y sector mediante una clave
  normalizada estable, no mediante el `NOCASE` ASCII de SQLite;
- plantilla única por lugar, tipo e intervalo exacto;
- revisión de reglas única por lugar y fecha efectiva;
- una sola fotografía V2 por jornada;
- índices para todas las claves foráneas y consultas de catálogo/recientes.

`work_places.id` es una identidad V2 propia. La restricción única
`timelineId + sector + objectiveId` hace idempotente la adopción dentro del
mismo contexto. El mismo `Objective` puede adoptarse conscientemente después en
otro sector, pero nunca se reutiliza automáticamente el lugar V2 anterior.
`work_places.isActive` es la autoridad de archivado V2; `Objective.isActive`
sigue siendo sólo la autoridad V1 y no archiva ni reactiva lugares V2.
`Shift.sourceObjectiveId` continúa guardando siempre el UUID de `Objective`,
como en V1, y su fotografía V2 conserva además `workPlaceId`.

Al cambiar de sector, los lugares anteriores dejan de ofrecerse. La interfaz
puede proponer reutilizar un `Objective`, pero exige una adopción nueva y sus
propias reglas. Editar luego los datos compartidos del `Objective` afecta la
presentación futura de todos los lugares V2 que lo usan; nunca altera las
instantáneas de jornadas ya guardadas. Si la persona necesita datos diferentes,
crea otro lugar físico en vez de modificar el compartido.

Las cinco tablas referencian la raíz por `timelineId` con `RESTRICT`.
`work_places.objectiveId` referencia `objectives.id` con `RESTRICT`. Los índices
únicos padres necesarios para las claves compuestas son:

- lugar: `(id, timelineId, sector, objectiveId)`;
- tipo: `(id, timelineId, sector)`;
- plantilla: `(id, timelineId, sector, workPlaceId, objectiveId, workTypeId)`.

`work_templates` usa claves foráneas compuestas para demostrar que lugar,
objetivo y tipo pertenecen a la misma línea temporal y sector. Las revisiones de
reglas referencian el mismo lugar compuesto. `shift_work_snapshots` referencia
de manera verificable su plantilla, tipo, lugar y objetivo mediante claves
compuestas; `configurationRevisionId` usa una FK simple `RESTRICT` y el
repositorio comprueba dentro de la transacción que esa revisión pertenezca a la
misma línea temporal y sector. Cada tabla hija posee índices con las mismas
columnas y orden de sus claves foráneas.

Además se mantienen las restricciones funcionales únicas:
`(timelineId, sector, objectiveId)` para lugar,
`(timelineId, sector, normalizedNameKey)` para tipo,
`(workPlaceId, workTypeId, startTime, endTime)` para plantilla y
`(workPlaceId, effectiveFrom)` para regla.
Las reglas poseen además índices no únicos en `timelineId` y en
`(workPlaceId, timelineId, sector, objectiveId)` para su raíz, FK compuesta y
resolución temporal, sin agregar un índice padre redundante sobre su UUID.

La clave normalizada del nombre usa una función única y probada: Unicode NFKC,
espacios exteriores retirados, espacios internos consecutivos colapsados y
`uppercase(Locale.ROOT)`. Se persiste esa clave y se valida al leer; el texto
visible conserva la escritura normalizada elegida por la persona.

`work_templates` puede conservar un `legacyScheduleCombinationId` nullable.
Ésta es la excepción explícita a `RESTRICT`: usa `ON DELETE SET NULL`. Si la
combinación V1 se elimina conscientemente, la plantilla V2 no se borra ni
cambia. La columna posee su índice correspondiente.

La identidad contextual es inmutable. En un lugar no cambian `id`, línea
temporal, sector, objetivo ni creación; en un tipo no cambian `id`, línea
temporal, sector ni creación; en una plantilla no cambian `id`, línea temporal,
lugar, objetivo, tipo ni creación. Cambiar de lugar o tipo crea otra plantilla y
archiva la anterior. Las revisiones de reglas son completamente insert-only.
Toda ruta V1 que actualice un `Objective` adoptado aplica también la regla de
abreviatura V2: una abreviatura histórica de dos puede quedar idéntica, pero
cambiarla exige entre tres y cinco caracteres.

Las lecturas observables y puntuales cuentan filas huérfanas o incoherentes de
las cinco tablas, incluidas relaciones que una restauración externa corrupta
pudiera ocultar. Ante cualquier inconsistencia fallan de forma controlada; no
proyectan un catálogo parcial ni permiten escribir encima.

La auditoría semántica también exige que cada lugar almacenado posea al menos
una revisión de reglas y que cada tramo civil de una jornada V2 tenga una regla
aplicable para su `workPlaceId`. Si una restauración externa borra reglas con las
FK desactivadas o mezcla línea temporal, sector, objetivo y lugar, lecturas y
escrituras fallan con el error controlado de datos locales inválidos; no alcanza
con que `foreign_key_check` esté vacío.

`MIGRATION_6_7` crea las cinco tablas vacías. No adopta objetivos, no crea tipos,
no convierte horarios, no agrega fotografías a jornadas anteriores y no activa
V2. La migración se incorpora a la cadena completa y se exporta únicamente
`7.json`; los esquemas 1–6 permanecen byte a byte.

Una base creada directamente en v7 continúa sin raíz y sin catálogo hasta que
la persona elija su sector.

### Estado de inicio

La aplicación debe resolver un estado único y observable:

- **Cargando:** todavía no se leyó configuración y no se ofrecen escrituras;
- **Error de carga:** no se confunde una falla con una base vacía; muestra un
  mensaje simple y `Reintentar`, sin habilitar elección de sector ni creación;
- **Instalación nueva:** no existe raíz; muestra la elección obligatoria de los
  cuatro sectores exactos;
- **V1 sin activar:** raíz `MIGRATED_V1` sin revisiones; el Calendario y todos
  sus recorridos heredados siguen disponibles;
- **V1 con activación futura:** todavía no existe una revisión aplicable a la
  fecha actual; el modo heredado sigue disponible y la aplicación informa la
  fecha futura ya elegida sin adelantar el cambio;
- **V2 sin primer conjunto:** existe revisión V2 pero falta lugar, regla
  aplicable o plantilla;
- **V2 lista:** existe al menos un lugar activo con regla aplicable, un tipo
  activo y una plantilla activa coherentes con el sector vigente.

Este estado se obtiene de la configuración y del catálogo, nunca de
`hasAnyShifts` ni de contar jornadas. Una persona migrada puede no tener
jornadas y una persona V2 puede conservar historia V1.

Al elegir sector en una instalación nueva:

- se crea la raíz `NEW_V2` y la primera revisión efectiva desde la fecha local
  actual;
- `hoursReference = PendingSetup`;
- `availabilityLabel = null`;
- no se crean 204 horas, nocturnidad ni otra regla sectorial;
- luego aparece el Calendario vacío con el mensaje `Todavía no cargaste ningún
  lugar de trabajo` y una acción clara para crear el primero.

La fecha actual es el valor simple inicial, no una prohibición de retrocarga.
Si una instalación `NEW_V2` intenta cargar días anteriores a su primera
revisión, MiGuardia pregunta una sola vez si quiere usar esa misma forma de
trabajar desde la fecha más antigua elegida. Al confirmar, agrega de manera
atómica una revisión equivalente anterior y las revisiones de reglas necesarias
para los lugares usados. Antes de confirmar no escribe ni trata esos días como
V1. Si existen sectores o reglas diferentes en el intervalo, exige operaciones
separadas.

Una persona migrada no recibe una pantalla bloqueante. Debe existir una acción
explícita `Configurar MiGuardia 2.0`. Al usarla elige fecha y sector —con
Vigilancia privada sugerida, no confirmada automáticamente— y recién entonces
se agrega la primera revisión V2. Los objetivos y horarios V1 se ofrecen para
adopción individual; no se adoptan en silencio ni se reinterpretan jornadas
anteriores.

`Mi forma de trabajar` permite además `Cambiar sector desde una fecha`. Crea una
revisión nueva, deja `PendingSetup` y disponibilidad sin decidir para el sector
nuevo, y no modifica revisiones anteriores. Desde esa fecha sólo se ofrecen
lugares y tipos del sector nuevo. Un cambio futuro no adelanta la interfaz
actual, pero queda visible y se usa al cargar fechas que ya caen dentro de su
vigencia. Los lugares del sector anterior no se borran ni se reactivan o
archivan por tocar `Objective.isActive`.

### Primera configuración visible

El primer lugar se crea mediante dos etapas breves y recuperables:

1. **Lugar y reglas**
   - nombre obligatorio;
   - nombre corto nuevo de tres a cinco letras, normalizado en mayúscula;
   - dirección opcional;
   - nota personal opcional y privada;
   - pregunta cotidiana: `¿En este lugar contás horas nocturnas?`;
   - si responde sí, inicio y final exactos;
   - opciones simples y no monetarias para sábado, domingo y feriado;
   - cada opción explica que sólo clasifica horas y permite mostrarlas aparte.

   La primera revisión de reglas es obligatoria y se guarda desde la misma
   fecha efectiva de la configuración usada por ese lugar. Adoptar un objetivo
   existente también exige confirmar esa regla; no se crea una regla silenciosa
   ni se considera listo un lugar sin ella.

2. **Primer tipo y plantilla**
   - crea un tipo editable sugerido como `${shiftLabel} habitual`: `Guardia
     habitual` en Vigilancia privada y Policía, `Turno habitual` en Enfermería y
     `Jornada habitual` en Medicina;
   - tipo, inicio, final y color son obligatorios;
   - siempre muestra el horario exacto y explica `termina al día siguiente` o
     `24 horas` cuando corresponda.

La confirmación de las dos etapas persiste el conjunto de manera atómica. Si
falla, no queda un lugar sin su primera plantilla. Después de guardar se ofrecen
exactamente:

- `Volver al Calendario`;
- `Agregar otro horario`;
- `Agregar otro lugar`.

También se puede crear un tipo activo adicional. `Consultorio` puede ser uno de
ellos. `Capacitación` no se crea ni se ofrece como atajo en este bloque porque
cada carga debe preguntar si es paga; la interfaz explica que se registrará
desde Situaciones especiales. Nunca se deduce capacitación por el nombre de un
tipo activo. Al crear cualquier tipo, el texto cotidiano aclara: `Esto cuenta
como trabajo normal. Las horas extras, una extensión del turno y un servicio
adicional se cargan aparte.` Así, palabras sectoriales como `Servicio` no
convierten por sí solas el registro en trabajo adicional.

### Catálogo visible

La superficie V2 usa la palabra sugerida por el sector, pero su título común
puede ser `Lugares, tipos y horarios`. Debe permitir:

- consultar activos y archivados;
- agregar y editar datos futuros;
- archivar con confirmación y explicación;
- reactivar cuando no genere un duplicado;
- agregar una plantilla dentro de su lugar;
- cambiar reglas del lugar desde una fecha sin reescribir la revisión previa.

Una nueva revisión de reglas guarda una nueva versión desde su fecha efectiva.
Su intervalo afectado comienza en `effectiveFrom` y termina antes de la próxima
revisión existente del mismo lugar, si la hubiera. Antes de confirmar, el
repositorio comprueba por tramos de día local si ese intervalo alcanzaría una
jornada V2 cuya hora de inicio ya llegó (`startAt <= now`), cualquiera sea su
estado. Si existe una, rechaza la revisión retroactiva con una explicación y no
escribe nada; una corrección histórica consciente pertenecerá a un bloque
posterior. Si sólo alcanza jornadas futuras, no modifica sus fotografías: al
calcular, cada tramo resolverá automáticamente la regla vigente para su fecha.
Esto también impide que una regla intermedia pise la revisión posterior.

La vista previa usa el reloj inyectado, pero no autoriza por sí sola la
escritura. Al confirmar se toma un `now` nuevo y, dentro de la misma transacción,
se releen lugar, reglas y jornadas, se recalcula el intervalo y se repite el
control `startAt <= confirmationNow`. Si una jornada comenzó durante la
confirmación o apareció otra escritura incompatible, la operación revierte y
actualiza la vista previa con un error cotidiano.

Una jornada que cruza medianoche se trata como un único registro del día en que
empezó, pero sus clasificaciones por feriado, noche o fin de semana se dividen en
tramos de día local. Por ejemplo, una jornada del 31 a la noche al día 1 puede
usar la regla del 31 antes de medianoche y la del 1 después de medianoche.
Los tramos usan `Shift.zoneId` y el intervalo real semiabierto
`[startAt, endAt)`: una jornada que termina exactamente a las 00:00 no ocupa el
día siguiente.

Cambiar un nombre, tipo, color u horario sólo afecta selecciones futuras. Las
jornadas ya creadas conservan sus instantáneas.

### Primera carga manual V2

Cuando la fecha elegida usa una revisión V2, la carga individual o múltiple del
Calendario muestra únicamente plantillas activas de la misma línea temporal y
sector. Cada opción enseña:

- nombre corto del lugar;
- tipo de trabajo;
- inicio y final exactos;
- color.

La vigencia se resuelve para cada fecha seleccionada, no una sola vez para todo
el mes. Si la selección mezcla fechas V1 y V2 o sectores distintos, la
aplicación no aplica una plantilla a ciegas: explica cuáles son incompatibles y
pide cargarlas en operaciones separadas. Si las fechas usan revisiones V2
distintas pero conservan el mismo sector y la misma plantilla válida, la carga
puede continuar y cada jornada guarda el identificador de la revisión que le
corresponde.

La carga conserva la única grilla y las reglas actuales de fechas ocupadas,
segunda jornada, solapamiento y descanso. No agrega otro selector de fechas.

Al guardar:

- se crea el `Shift` habitual con sus instantáneas existentes;
- `Shift.sourceObjectiveId = WorkPlace.objectiveId`, es decir `Objective.id`;
- `ShiftWorkSnapshot.workPlaceId = WorkPlace.id`; ambos UUID pueden ser
  diferentes y nunca se intercambian;
- `sourceScheduleCombinationId` se usa sólo cuando la plantilla fue adoptada
  de una combinación V1; en otro caso queda `null`;
- se crea `ShiftWorkSnapshot` con tipo, comportamiento, plantilla, sector,
  lugar V2 y revisión de configuración;
- ambas filas forman una única transacción junto con reemplazos y limpieza de
  `F` o `?` ya autorizada;
- dos tipos con igual lugar y horario permanecen distinguibles;
- editar una jornada V2 actualiza conscientemente ambas fotografías dentro de
  una transacción y conserva UUID, creación y estado;
- borrar la jornada borra sólo su fotografía dependiente.

La actualización V2 exige que la jornada ya posea su fotografía. Si falta,
continúa siendo V1 y la operación V2 falla de forma controlada; nunca la
convierte silenciosamente. Luego de las validaciones puras, todas las
validaciones contra configuración, catálogo y reglas se repiten dentro de la
misma transacción que escribe.

Las jornadas V1 sin fotografía siguen significando V1. Nunca se les asigna un
tipo por inferencia.

Si después de activar V2 existe una jornada V1 futura o retroactiva sin
fotografía, `Editar`, cancelar y borrar conservan el recorrido heredado y su
semántica V1. La fecha por sí sola no la convierte. Una futura acción de
conversión sólo podrá existir si la persona elige expresamente una plantilla y
un tipo V2; no forma parte de este bloque.

### Superficies heredadas todavía no adaptadas

El Resumen V1 fija 204 horas y no puede presentarse como correcto para una
revisión V2. Mientras el motor V2 no esté implementado:

- una configuración `MIGRATED_V1` sin revisión aplicable al período conserva
  Resumen V1;
- los meses completos anteriores a la primera vigencia V2 continúan
  consultables con su Resumen V1;
- un mes que toca una vigencia V2 mantiene el destino para poder volver a meses
  históricos, pero en ese mes reemplaza las cifras V1 por una explicación
  honesta y sin totales: el motor 2.0 todavía no está habilitado;
- nunca muestra 204 horas, extras automáticas ni una cifra parcial como si
  fuera el Resumen V2 terminado.

Perfil laboral tampoco puede seguir afirmando `Vigilancia y seguridad` para
Policía, Enfermería o Medicina. En V2, la nueva superficie `Mi forma de
trabajar` reemplaza ese acceso; el Perfil V1 permanece durante el modo heredado
y mientras una activación futura todavía no rige. Sus datos no se borran.

Calendario, próximo evento, notificaciones y clima pueden seguir usando las
instantáneas comunes de `Shift`. Adaptar sus textos para mostrar el tipo de
trabajo pertenece a bloques posteriores. Este bloque sí debe probar que una
jornada V2 aparece en el próximo evento y no se descarta por no tener una
`ScheduleCombination` V1.

Las superficies actuales de Novedades y Excepciones contienen operaciones que
cambian objetivo u horario, restauran una jornada o crean otra usando sólo
contratos V1. Hasta que su bloque V2 exista, esas operaciones estructurales se
bloquean de forma controlada para cualquier jornada con `ShiftWorkSnapshot`,
incluidas las aperturas directas desde una notificación. Las notas o estados que
no cambien lugar, horario ni tipo pueden seguir disponibles. Ocultar un botón no
alcanza: el repositorio también protege la escritura.

## SCOPE

Se permite modificar:

- `core/domain/src/main/**` y pruebas del dominio;
- `core/database/src/main/**`, pruebas, migraciones y `schemas/7.json`;
- `app/src/main/**`, pruebas JVM e instrumentadas para el nuevo recorrido;
- documentación de estado, prompt, ADR y auditoría del bloque;
- recursos de texto y test tags necesarios.

No se agregan módulos Gradle, dependencias de producción, permisos ni servicios.

## DEPENDENCIES

- checkpoint `8993727`: dominio laboral configurable;
- checkpoint `7dde17d`: configuración persistente y Room v6;
- `WorkConfigurationRepository` como única fuente de origen y vigencia;
- `Objective`, `ScheduleCombination` y `Shift` como historia heredada;
- carga manual y única grilla existentes;
- `Clock`, zona y UUID inyectables.

## ORDEN DE EJECUCIÓN

Este contrato se implementa en dos cortes internos, siempre en este orden:

1. **Corte A — contratos y Room v7:** modelos y validaciones puras, proyección
   del estado raíz, repositorios, entidades, DAO, migración, esquema y pruebas.
   La aplicación todavía no activa ninguna conducta V2 visible. Se audita y se
   permite un checkpoint local sólo cuando dominio, base, migraciones y build
   están verdes.
2. **Corte B — recorrido utilizable:** `WorkSetupViewModel` y pantallas de
   sector/catálogo, conexión de raíz y menú, primera configuración atómica,
   carga y edición manual V2 dentro del `ManagementViewModel` existente,
   bloqueo de escritores laterales y protección del Resumen/Perfil V1. Se cierra
   con la batería integral, API 26, Samsung físico y auditoría final.

Dentro del corte B se estabiliza primero el estado raíz y el primer conjunto;
recién después se conecta la carga manual. No se reemplaza de golpe el recorrido
V1 ni se paralelizan pantallas que dependan de contratos todavía inestables.

## DO NOT

- no modificar ni borrar filas v1–v6 durante `MIGRATION_6_7`;
- no agregar tipo, sector o reglas dentro de las tablas históricas;
- no convertir una `ScheduleCombination` en plantilla V2 automáticamente;
- no asignar tipos a jornadas V1;
- no crear un sector `Otro` ni agrupar Enfermería y Medicina;
- no imponer nombres, horarios, nocturnidad o referencia por profesión;
- no usar abreviatura de dos letras como error para datos históricos;
- no permitir borrar historia V2 desde la superficie normal;
- no implementar planes recurrentes ni materialización futura;
- no implementar horario real, extras, disponibilidad o situaciones especiales;
- no implementar el motor o el Resumen V2 en este bloque;
- no mostrar montos, pagos, convenios o interpretaciones legales;
- no agregar cuentas, nube, red, ubicación, OCR, analítica ni datos clínicos;
- no modificar notificaciones, clima o producción salvo el mínimo cableado
  necesario para conservar compilación y rutas existentes;
- no usar migración destructiva, `allowMainThreadQueries` ni base en memoria en
  producción;
- no hacer push, tag, Release ni tocar `main` o el paquete productivo.

## VALIDATION

### Dominio y repositorios

Probar como mínimo:

1. catálogo exacto de cuatro sectores;
2. abreviatura nueva de dos letras rechazada y una histórica de dos conservada;
3. normalización de nombre, abreviatura, tipo y textos opcionales;
4. inicio igual a final produce veinticuatro horas;
5. dos tipos distintos admiten el mismo lugar e intervalo;
6. duplicado exacto de lugar, tipo e intervalo se rechaza;
7. el nombre `Capacitación` no cambia el comportamiento de un tipo activo y la
   capacitación real queda fuera de este bloque;
8. reglas nocturnas requieren horas exactas distintas;
9. revisiones de reglas se resuelven por cada tramo de fecha local y una jornada
   31→1 puede usar dos revisiones sin dejar de pertenecer al día 31;
10. archivar excluye nuevas cargas y conserva consultas históricas;
11. una jornada V2 copia nombre/comportamiento del tipo, sector, lugar V2 y
    revisión de configuración correctos;
12. dos trabajos superpuestos continúan sumándose como dos registros después de
    la confirmación existente;
13. lote inválido no escribe ni jornadas ni fotografías parciales;
14. recientes V2 ordena por `MAX(Shift.createdAtEpochMillis)`, usa desempate
    estable y limita de uno a cinco;
15. una revisión retroactiva que afectaría cualquier jornada cuya hora de inicio
    ya llegó se rechaza sin escritura, mientras una regla futura se resuelve sin
    reescribir fotografías;
16. selección V1/V2 o con sectores distintos se rechaza sin escritura;
17. una instalación `NEW_V2` puede extender conscientemente la misma
    configuración para retrocargar una fecha anterior.

### Room v7

Ejecutar en Android real o instrumentación equivalente:

1. migración directa 6→7 con filas representativas en las diecisiete tablas;
2. cadena 1→2→3→4→5→6→7;
3. hashes/esquemas 1–6 idénticos y sólo `7.json` nuevo;
4. base nueva v7 sin raíz, catálogo ni valores inventados;
5. base migrada con raíz V1 y catálogo vacío;
6. creación atómica del primer conjunto y reapertura;
7. adopción explícita sin modificar `Objective` ni `ScheduleCombination`;
8. dos plantillas con el mismo lugar/horario y tipos distintos;
9. restricciones de línea temporal, sector, duplicados y filas huérfanas,
   incluidas incoherencias externas que una relación raíz podría ocultar;
10. insert-only de revisiones de reglas;
11. cada par V2 jornada+fotografía se
    inserta/actualiza en la misma transacción y ninguna API acepta sólo una
    mitad;
12. rollback ante fallo después de eliminaciones de un lote;
13. eliminar jornada elimina su fotografía y no su catálogo;
14. `foreign_key_check` vacío tras migración, reapertura y operaciones;
15. ninguna ruta usa hilo principal ni migración destructiva.
16. nombre de tipo con mayúsculas/minúsculas acentuadas colisiona mediante su
    clave NFKC normalizada sin perder el texto visible;
17. nombres compuestos/descompuestos, espacios comunes o no separables y
    `straße`/`STRASSE` prueban la misma función canónica;
18. adoptar un horario ajeno al `Objective` se rechaza y adoptar dos veces es
    idempotente;
19. eliminar un `Objective` adoptado devuelve error de dominio antes de tocar
    sus horarios;
20. una abreviatura histórica de dos letras se conserva, pero cambiarla por
    otra abreviatura nueva de dos se rechaza;
21. una fila externa corrupta falla de forma controlada al leer y al escribir;
22. el mismo `Objective` adoptado en dos sectores produce dos `workPlaceId` y
    archivar uno no altera el otro ni `Objective.isActive`;
23. el mismo horario V1 puede originar dos plantillas de tipos distintos y al
    borrarlo ambas procedencias quedan `null`;
24. cambiar lugar o tipo de una plantilla usada se rechaza como cambio de
    identidad;
25. una regla intermedia respeta la revisión posterior y una regla retroactiva
    no atraviesa una jornada ya iniciada;
26. la ruta V1 tampoco cambia una abreviatura histórica adoptada por otra de dos
    letras;
27. si una jornada empieza entre la vista previa y la confirmación de una regla,
    el segundo control transaccional rechaza y revierte la escritura;
28. al eliminar reglas externamente con FKs desactivadas, reapertura, lectura y
    escritura detectan tanto el lugar sin reglas como cualquier tramo de jornada
    sin cobertura aplicable.

### Aplicación y Compose

Probar con datos ficticios:

1. una instalación nueva muestra sólo los cuatro sectores y no deja continuar
   sin elegir;
2. elegir sector crea `PendingSetup`, abre Calendario vacío y no inventa horas;
3. error de lectura no se presenta como instalación nueva y permite reintentar;
4. V1 migrada abre Calendario sin bloqueo;
5. activar V2 o cambiar de sector es una acción consciente con fecha y sector;
6. primer lugar exige nombre, abreviatura 3–5, regla y primera plantilla;
7. pregunta nocturna explica su efecto con palabras cotidianas;
8. un fallo conserva el borrador y no deja un conjunto parcial;
9. luego de guardar aparecen las tres acciones posteriores acordadas;
10. se pueden crear más lugares, tipos y plantillas;
11. dos tipos iguales en horario se distinguen visual y semánticamente;
12. carga manual V2 usa la grilla existente y guarda la revisión de
    configuración y el lugar V2;
13. retrocarga NEW_V2 exige y conserva la extensión consciente;
14. carga múltiple, ocupadas y advertencias conservan su comportamiento;
15. editar una jornada V1 futura después de activar V2 no la convierte;
16. editar plantilla o reglas no cambia una jornada anterior;
17. meses V1 anteriores siguen consultables y un mes V2 nunca muestra 204 h;
18. cerrar y reabrir recupera sector, catálogo y jornadas;
19. claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % mantienen
    todas las acciones alcanzables;
20. lectores semánticos no dependen sólo del color;
21. errores tienen acción de reintento y no filtran notas, lugares u horarios a
    logs;
22. una regla futura afecta el cálculo por fecha sin reescribir jornadas y una
    retroactiva incompatible muestra el error sin alterar datos;
23. próximo evento acepta una jornada V2 sin combinación V1;
24. Novedades y Excepciones no pueden ejecutar cambios estructurales V1 sobre
    una jornada V2, ni siquiera mediante navegación directa.

### Batería y auditoría

- ejecutar pruebas JVM de `:core:domain`, `:core:database` y `:app`;
- ejecutar instrumentación de migración y persistencia en el Samsung
  `SM-S938B` API 36;
- ejecutar instrumentación Compose de las superficies modificadas;
- recorrer manualmente en paquete QA el inicio nuevo, un modo V1 migrado y una
  primera carga V2 ficticia;
- ejecutar además en API 26 una instrumentación o recorrido mínimo de
  instalación nueva, migración/apertura y primera carga, porque este bloque toca
  Room, repositorios, navegación y Compose de forma transversal;
- ejecutar `lintDebug`, `assembleDebug`, `assembleQaAndroidTest` y
  `git diff --check`;
- revisar esquema, permisos, dependencias, manifiesto, logs, secretos y datos;
- retirar el paquete QA y cualquier permiso temporal al finalizar;
- no tocar la aplicación ni los datos productivos.

Gradle e instrumentación se ejecutan con `--max-workers=1`.

## DONE WHEN

El bloque queda cerrado únicamente cuando:

- Room v7 migra sin pérdida y sus cinco tablas poseen restricciones reales;
- una instalación nueva puede elegir sector, crear el primer conjunto y cargar
  una jornada V2 completa;
- una instalación V1 sigue funcionando sin activación forzada;
- la adopción de un lugar/horario V1 es explícita y no cambia el pasado;
- dos tipos activos pueden compartir lugar/horario sin mezclarse y Capacitación
  queda reservada para su bloque especial;
- cada jornada V2 conserva tipo, sector, lugar, plantilla y configuración
  históricos, mientras las reglas se resuelven correctamente por cada fecha;
- el Resumen V1 no afirma resultados para una configuración V2;
- todas las pruebas proporcionales pasan y la evidencia física está registrada;
- no hay dependencias, permisos, datos, montos ni cambios ajenos al alcance;
- documentación, esquema, código y pruebas cuentan la misma historia;
- el diff fue auditado y existen checkpoints locales coherentes para el corte A
  y el cierre del corte B;
- no hubo push, tag, Release, publicación ni cambios en producción.
