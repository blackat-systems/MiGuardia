# ADR 0027: planes recurrentes versionados y ocurrencias protegidas

- Estado: aceptado
- Fecha: 2026-08-23

## Contexto

MiGuardia 2.0 ya permite crear, editar y eliminar jornadas concretas. Cada
jornada se persiste como un par obligatorio `Shift + ShiftWorkSnapshot` y
`V2ShiftRepository` es la única frontera autorizada para modificar ese par.

La hoja de ruta agrega ahora planes recurrentes. Un plan debe materializar de
inmediato jornadas futuras, permitir cambiar una sola fecha o todo lo futuro y
proteger el pasado y las excepciones manuales. El árbol actual no contiene un
modelo de plan ni un vínculo entre una jornada y la regla que la creó.

Además, `MiGuardiaV2Database` versión 1 ya es la primera base pública de V2.
Todo cambio de esquema debe preservar sus datos mediante una migración
explícita.

## Decisión

### 1. Tres piezas persistentes

Room V2 pasa de la versión 1 a la 2 y agrega exactamente tres tablas:

1. **`recurring_plans`:** `id`, `timelineId`, `sector` y
   `createdAtEpochMillis`. `id` es la clave primaria. `timelineId` referencia
   la raíz de configuración con `RESTRICT` y existe un índice por
   `timelineId + sector`.
2. **`recurring_plan_revisions`:** `id`, `planId`, `revisionNumber`,
   `effectiveFrom`, `kind`, `endDateInclusive`, `patternKind`,
   `weekdaysMask`, `intervalCount`, `monthlyOrdinal`, `monthlyDayOfWeek`,
   `templateId`, `workPlaceId`, `objectiveId`, `workTypeId`,
   `objectiveNameSnapshot`, `objectiveAbbreviationSnapshot`,
   `objectiveAddressSnapshot`, `workTypeNameSnapshot`,
   `workTypeBehaviorSnapshot`, `startTimeSnapshot`, `endTimeSnapshot`,
   `colorArgbSnapshot`, `positionSnapshot`, `zoneId` y
   `createdAtEpochMillis`. `id` es la clave primaria; `planId` referencia al
   plan con `RESTRICT`; `templateId` referencia la plantilla con `RESTRICT`; y
   `planId + revisionNumber` es único. También existe una clave candidata
   `id + planId` y un índice ordena
   `planId + effectiveFrom + revisionNumber`.
3. **`recurring_occurrences`:** `planId`, `localDate`, `revisionId`,
   `shiftId`, `state`, `createdAtEpochMillis` y `updatedAtEpochMillis`.
   `planId + localDate` es la clave primaria. `revisionId + planId` referencia
   la revisión dueña con `RESTRICT`. `shiftId` referencia `shifts.id` con
   `SET_NULL` y posee un índice único cuando no es nulo, por lo que una jornada
   nunca puede pertenecer a dos ocurrencias.

Las cadenas persistidas de `kind` son `ACTIVE` y `FINALIZED`. Los estados de
ocurrencia son exactamente:

- `AUTOMATIC`: posee `shiftId` y todavía puede ser reemplazada por el plan;
- `CUSTOMIZED`: posee `shiftId` y está protegida;
- `EXCLUDED`: no posee `shiftId` y representa una exclusión consciente que una
  revisión posterior no puede reactivar silenciosamente;
- `RETIRED`: no posee `shiftId` y salió automáticamente de una revisión. Sólo
  una nueva revisión confirmada, cuya vista previa la incluya expresamente,
  puede reactivarla como `AUTOMATIC`.

La integridad local rechaza cualquier otra cadena y exige la nulabilidad de
`shiftId` indicada para cada estado.

Las cadenas de `patternKind` son `WEEKDAYS`, `EVERY_N_DAYS`,
`EVERY_N_WEEKS` y `MONTHLY`. `weekdaysMask` usa del bit 0 para lunes al bit 6
para domingo. `monthlyDayOfWeek` usa ISO 1 para lunes a 7 para domingo y
`monthlyOrdinal` admite `FIRST`, `SECOND`, `THIRD`, `FOURTH` o `LAST`.
`intervalCount` es positivo. La integridad exige sólo los parámetros propios
del patrón y rechaza combinaciones ambiguas.

Una revisión `FINALIZED` copia la última definición y fotografía conocida, y
su `effectiveFrom` es el corte desde el que deja de generar. Así no necesita
campos estructurales nulos ni una interpretación posterior de una plantilla
mutable.

Una jornada manual no tiene ocurrencia. Una jornada recurrente conserva una
sola ocurrencia aunque su par concreto se edite o elimine.

No se agregan campos de recurrencia a `Shift` ni a `ShiftWorkSnapshot`: el
vínculo pertenece a la nueva tabla de ocurrencias. Los pares históricos siguen
siendo utilizables por las capacidades comunes sin conocer planes.

### 2. Excepciones durables

`Cambiar sólo esta jornada` mantiene la fecha y el UUID del par, y marca la
ocurrencia como personalizada dentro de la misma transacción.

`Eliminar sólo esta jornada` elimina el par exacto y conserva la ocurrencia
como excluida, con `shiftId = null`. Esa tumba evita que una modificación
posterior del plan vuelva a crear silenciosamente la fecha.

Toda frontera estructural ya existente debe volverse consciente de la
ocurrencia. Si `Cargar jornadas` reemplaza una jornada recurrente, primero pasa
su ocurrencia a `EXCLUDED` con `shiftId = null` y después elimina el par dentro
de la misma transacción y el mismo CAS. Una actualización individual de un par
recurrente fuera de una mutación del propio plan lo marca `CUSTOMIZED`.
`ON DELETE SET_NULL` es una defensa de clave foránea, no reemplaza esa
transición explícita ni permite un estado `AUTOMATIC` sin jornada.

Una ocurrencia deja de ser automática intacta cuando fue personalizada, fue
excluida, su jornada ya no está `PLANNED`, posee una nota o una configuración
particular de avisos, o tiene una situación aplicable que el modelo vigente
pueda consultar. Las futuras situaciones especiales deberán integrarse a esta
misma protección sin cambiar el significado histórico.

### 3. Versiones futuras y pasado inmutable

`Cambiar desde esta fecha` agrega una revisión; no modifica una revisión
anterior. El número de revisión avanza estrictamente. Para una fecha se aplica
la revisión de mayor número cuya `effectiveFrom` sea igual o anterior; así una
corrección posterior puede usar el mismo corte u otro corte futuro y siempre
queda claro qué decisión más reciente prevalece desde allí.

Para `cada N días` y `cada N semanas`, la nueva versión se vuelve a anclar en
la fecha de corte. Sólo puede retirar o reemplazar ocurrencias automáticas
intactas en la fecha de corte o después de ella. Una fecha de la versión
anterior que ya no aparece en el patrón nuevo pasa a `RETIRED` y pierde su par.
Puede reaparecer únicamente si una revisión posterior la incluye en su vista
previa y el usuario confirma esa revisión. Las ocurrencias pasadas,
personalizadas, excluidas o protegidas permanecen intactas.

`Finalizar desde esta fecha` también se representa como una revisión durable.
Retira únicamente pares futuros automáticos intactos y pasa esas ocurrencias a
`RETIRED`. Las ocurrencias que ya estaban `EXCLUDED` continúan excluidas. No
existe una acción normal que reescriba el pasado.

Cada jornada nueva se construye resolviendo la configuración laboral exacta de
su fecha y conserva su propia fotografía histórica. Un cambio posterior del
catálogo, de la configuración o del plan no recalcula esa fotografía.

La revisión del plan tampoco depende de leer luego una plantilla mutable.
Además de los identificadores de plantilla, lugar, objetivo y tipo, conserva
como mínimo nombre y abreviatura del lugar, dirección opcional, nombre y
comportamiento del tipo, horario inicial y final, color y puesto opcional. La
interfaz explica la versión histórica desde esos valores; los identificadores
se usan para validar una fuente activa al crear la siguiente revisión.

### 4. Patrones exactos

Se admiten solamente:

- uno o más días elegidos de la semana;
- cada `N` días, anclado en la fecha inicial;
- cada `N` semanas, anclado en la fecha inicial y repitiendo ese mismo día de
  la semana cada `N × 7` días;
- patrón mensual formado por primero, segundo, tercero, cuarto o último más un
  día de la semana.

El inicio y el final son inclusivos. El inicio debe ser hoy o una fecha futura,
según reloj y zona inyectables. Todo plan necesita al menos una ocurrencia y
una vista previa exacta antes de confirmar.

Por decisión expresa de Joaquin del 2026-08-25, cada creación o cambio de plan
puede abarcar como máximo 2.000 jornadas concretas futuras. La expansión debe
terminar por la fecha final explícita, detectar desbordes de fecha o cantidad
antes de escribir y no truncar silenciosamente. Si el patrón produciría 2.001
o más jornadas, se rechaza completo con una explicación visible.

### 5. Una sola frontera transaccional

Las lecturas de planes pueden vivir en un repositorio específico, pero ninguna
nueva implementación puede escribir `shifts` por su cuenta.
`V2ShiftRepository` se amplía con una mutación consciente de planes capaz de
guardar en una sola transacción:

- plan y revisión;
- ocurrencias y exclusiones;
- inserciones, actualizaciones o eliminaciones de `Shift +
  ShiftWorkSnapshot`;
- limpieza de `F` o `?` sólo en fechas donde realmente se inserta una jornada.

La expectativa CAS conserva por valor completo el plan observado o su ausencia,
las revisiones ordenadas, las ocurrencias del tramo, cada par histórico, la
ocupación del rango y las filas que determinan protección. No se acepta usar
sólo una marca horaria. Si algo cambió entre revisión y confirmación, no escribe
parcialmente y exige actualizar la vista previa.

### 6. Conflictos

Ante fechas ocupadas, el lote completo ofrece:

- conservar lo existente;
- reemplazar solamente jornadas automáticas intactas, incluso si pertenecen a
  otro plan, dejando su ocurrencia `RETIRED`;
- mantener ambas después de una advertencia concreta.

Una jornada manual o protegida nunca se reemplaza silenciosamente. Se
conservan además las advertencias por segunda jornada, superposición, descanso
menor a 12 horas y carpeta médica.

Al conservar una fecha ocupada, el plan nuevo registra una ocurrencia
`EXCLUDED` sin jornada. Al mantener ambas, su ocurrencia nueva queda
`AUTOMATIC`. Al reemplazar una automática de otro plan, la ocurrencia anterior
pasa a `RETIRED` y la nueva queda `AUTOMATIC`, todo en la misma transacción.

## Consecuencias

- Se exporta el esquema
  `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/2.json`.
- La migración `1→2` crea las nuevas tablas vacías y preserva las diecinueve
  tablas y todos los datos existentes de V2.
- Crear, modificar o finalizar un plan puede atravesar varios meses; no se
  reutiliza sin cambios el planificador manual limitado a un solo mes.
- La carga manual conserva su comportamiento visible, pero sus reemplazos
  actualizan también cualquier ocurrencia recurrente alcanzada.
- El Calendario, próximo evento y notificaciones siguen consumiendo jornadas
  concretas. No necesitan interpretar patrones.
- La persistencia adicional permite distinguir una excepción manual de una
  jornada todavía controlada por el plan.

## Alternativas descartadas

### Guardar sólo un `planId` dentro de `Shift`

No conserva una exclusión después de eliminar la jornada y permitiría que la
fecha reaparezca al regenerar el plan.

### Calcular las jornadas al abrir cada mes

Convertiría una consulta en escritura, haría depender el resultado de cuándo
se abrió el Calendario y complicaría notificaciones y próximo evento.

### Editar todas las jornadas futuras sin revisiones

Perdería la definición histórica y volvería ambiguo qué regla produjo cada
ocurrencia.

### Crear una segunda frontera que escriba jornadas

Rompería la atomicidad y la validación global fijadas para V2. Toda escritura
estructural continúa pasando por `V2ShiftRepository`.
