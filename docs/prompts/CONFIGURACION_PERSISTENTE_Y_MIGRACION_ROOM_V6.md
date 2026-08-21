# Configuración persistente y migración Room v6

- Estado: **CERRADO**
- Responsable: MAIN o especialista de persistencia
- Dependencias: dominio `core/domain/.../work/` cerrado y ADR 0021
- Tipo de entrega: dominio mínimo adicional, Room, repositorio y pruebas

## TASK

Guardar la única configuración laboral de MiGuardia 2.0 y evolucionar Room de
v5 a v6 mediante una migración explícita, aditiva y no destructiva. La
actualización debe reconocer el origen V1 sin inventar una configuración V2.

## CONTEXT

La base v5 posee trece tablas y migraciones `1→2→3→4→5`. Un usuario actualizado
debe conservar el motor V1 hasta activar V2 desde una fecha. Una instalación
nueva debe elegir sector, pero no está obligada a decidir en ese momento si usa
una referencia de horas.

El estado `NotUsed` ya significa una decisión consciente y `Unknown` significa
que existe una referencia cuyo valor no se conoce. Ninguno sirve para “todavía
no se configuró”; este bloque agrega `HoursReference.PendingSetup` como estado
interno neutral, que jamás equivale a cero.

## INPUTS

- `docs/adr/0020-modelo-laboral-personalizable-y-vigencia-por-fecha.md`;
- `docs/adr/0021-persistencia-configuracion-y-origen-room-v6.md`;
- `docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md`;
- esquema Room v5 y migraciones existentes;
- patrones de entidades, mapeos, repositorios y pruebas del módulo
  `:core:database`;
- documentación oficial de `MigrationTestHelper` y migraciones Room.

## OUTPUT

### Dominio y contrato público

1. agregar `HoursReference.PendingSetup`;
2. agregar un origen explícito `MIGRATED_V1` o `NEW_V2`;
3. representar el historial almacenado con origen, línea temporal y valores por
   período;
4. validar que una raíz `NEW_V2` tenga al menos una revisión y que una raíz
   migrada pueda comenzar vacía;
5. agregar `WorkConfigurationRepository` con observación, lectura, creación
   inicial, agregado insert-only de revisiones y creación/actualización explícita
   de valores por período.

No exponer una operación que borre revisiones, definiciones o la raíz.

### Esquema Room v6

Agregar exactamente estas cuatro tablas:

1. `work_configuration_roots`:
   - `timelineId TEXT` PK;
   - `singletonSlot INTEGER NOT NULL` con índice único;
   - `origin TEXT NOT NULL`;
2. `per_period_hours_definitions`:
   - `id TEXT` PK;
   - `timelineId TEXT NOT NULL`, FK `RESTRICT` a la raíz;
   - `periodKind TEXT NOT NULL`;
   - `weeklyFirstDayIso INTEGER` nullable;
   - `cycleAnchorDate TEXT` nullable;
   - `cycleLengthDays INTEGER` nullable;
   - índice por `timelineId`;
3. `work_configuration_revisions`:
   - `id TEXT` PK;
   - `timelineId TEXT NOT NULL`, FK `RESTRICT` a la raíz;
   - `effectiveFrom TEXT NOT NULL`;
   - `sector TEXT NOT NULL`;
   - `availabilityLabel TEXT` nullable;
   - `hoursReferenceKind TEXT NOT NULL`;
   - período inline nullable para `UNKNOWN` y `FIXED` mediante
     `periodKind`, `weeklyFirstDayIso`, `cycleAnchorDate` y
     `cycleLengthDays`;
   - `requiredMinutes INTEGER` nullable;
   - `perPeriodDefinitionId TEXT` nullable, FK `RESTRICT` a definición;
   - índice único por `timelineId + effectiveFrom` e índice por definición;
4. `per_period_hours_values`:
   - `id TEXT` PK;
   - `definitionId TEXT NOT NULL`, FK `RESTRICT` a definición;
   - `windowStartInclusive TEXT NOT NULL`;
   - `windowEndExclusive TEXT NOT NULL`;
   - `requiredMinutes INTEGER NOT NULL`;
   - índice único por `definitionId + windowStartInclusive`.

Todas las inserciones usan `ABORT`. No usar `REPLACE`. Las revisiones y
definiciones son insert-only. Los valores sólo se corrigen mediante `UPDATE` del
mismo `id` y sin cambiar definición o ventana.

### Migración

`MIGRATION_5_6` debe:

1. crear las cuatro tablas y sus índices con la misma estructura que Room crea
   en una instalación nueva;
2. insertar una sola raíz con UUID reservado, slot `1` y origen `MIGRATED_V1`;
3. dejar revisiones, definiciones y valores vacíos;
4. registrarse junto a todas las migraciones anteriores;
5. conservar intactas las trece tablas y los esquemas v1–v5;
6. exportar únicamente el nuevo esquema `6.json`.

La ausencia de raíz en una base creada directamente en v6 significa
“instalación nueva todavía sin sector”. La raíz migrada vacía significa
“continuar con V1 hasta activación consciente”. No inferirlo mediante objetivos,
guardias, perfil o contenido de DataStore.

### Codecs e invariantes

Persistir códigos explícitos y estables, nunca `displayName` ni `Enum.name`.
Validar al guardar y al leer:

- UUID, fechas ISO, día semanal 1–7 y minutos positivos;
- `PENDING_SETUP` y `NOT_USED`: todos los campos de período, minutos y
  definición en null;
- `UNKNOWN`: período opcional, sin minutos ni definición;
- `FIXED`: período y minutos obligatorios, sin definición;
- `PER_PERIOD`: definición obligatoria, período inline y minutos en null;
- mensual sin campos semanales/cíclicos;
- semanal con día y sin campos cíclicos;
- ciclo con anclaje y longitud positiva;
- dos revisiones con la misma fecha rechazadas;
- un patrón de definición no puede modificarse;
- la ventana de un valor coincide exactamente con su definición;
- una segunda raíz o un slot distinto de `1` se consideran datos inválidos.

Las filas corruptas se traducen a `InvalidLocalDataException`.

## SCOPE

Permitido:

- `core/domain/.../work/**` sólo para el agregado persistido y `PendingSetup`;
- `core/domain/.../repository/WorkConfigurationRepository.kt` y sus pruebas;
- `core/database` para cuatro entidades, relaciones de lectura, DAO, mapeos,
  repositorio, `LocalDataStore`, versión 6, migración y pruebas;
- `core/database/schemas/.../6.json`;
- documentación directa de este bloque.

## DEPENDENCIES

- Room 2.8.4, KSP y `room-testing` ya están configurados;
- no se agrega ninguna dependencia;
- la API pública continúa detrás de `LocalDataStore` y contratos de dominio;
- la lógica no Android permanece en `:core:domain`.

## DO NOT

- No modificar columnas, índices, claves ni entidades de las trece tablas v5.
- No regenerar ni cambiar los esquemas `1.json` a `5.json`.
- No persistir todavía tipos, clases extra, reglas por lugar, plantillas,
  recurrencias, jornadas V2, situaciones especiales o preferencias visuales.
- No crear una revisión automática para un usuario migrado.
- No insertar 204 horas, 21:00–06:00, sector, disponibilidad o valores por
  defecto.
- No usar JSON, BLOB, `Double`, `REPLACE`, migraciones automáticas,
  `fallbackToDestructiveMigration`, `allowMainThreadQueries` ni una base en
  memoria de producción.
- No reinterpretar ni agregar FK a referencias históricas de objetivos,
  horarios, guardias o fotos.
- No crear una pantalla en este bloque.

## VALIDATION

### Dominio y repositorio

- `PendingSetup`, `NotUsed` y `Unknown` permanecen distintos y ninguno produce
  cero;
- round-trip de los cuatro sectores, tres disponibilidades y referencias
  PendingSetup, NotUsed, Unknown sin/con período, Fixed y PerPeriod;
- períodos mensual, cualquier inicio semanal y ciclos antes/después del ancla;
- raíz nueva con primera revisión y raíz migrada inicialmente vacía;
- creación inicial atómica y rechazo de segunda raíz;
- agregado de revisión sin reescribir anteriores;
- duplicado de fecha o ID rechazado;
- definición PerPeriod insert-only y patrón inmutable;
- alta y corrección explícita del mismo valor;
- duplicado de definición/ventana y huérfanos rechazados;
- cierre y reapertura conservan exactamente el agregado;
- fila corrupta produce `InvalidLocalDataException`.

### Migración y compatibilidad

- fixture v5 con datos ficticios en las trece familias;
- `5→6` preserva cada fila, relación, instantánea y abreviatura histórica de dos
  letras;
- la migración crea una raíz `MIGRATED_V1` y cero revisiones, definiciones y
  valores;
- cadena completa `1→2→3→4→5→6`;
- base nueva v6 sin raíz, 204 horas, nocturnidad ni configuración inventada;
- `PRAGMA foreign_key_check` sin resultados;
- constraints e índices nuevos verificados;
- una migración que falla revierte sus cambios y conserva `user_version = 5`;
- una ruta 5→6 faltante falla sin borrar datos;
- esquema `6.json` exportado y hashes v1–v5 intactos.

Ejecutar como mínimo:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:domain:testDebugUnitTest :core:database:testDebugUnitTest :core:database:assembleDebugAndroidTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
```

Después ejecutar la batería global, revisar `git diff --check`, el diff completo,
los esquemas y los artefactos de prueba. La QA física debe identificar el
dispositivo y separar compilación de ejecución instrumentada.

## DONE WHEN

- Room abre tanto una base v5 migrada como una base v6 nueva;
- ninguna familia histórica pierde o cambia datos;
- el origen distingue de forma explícita actualización y nueva instalación;
- el repositorio persiste y reconstruye el dominio sin defaults universales;
- el esquema 6 coincide con la migración y los cinco esquemas anteriores no
  cambian;
- pruebas JVM, migraciones instrumentadas, lint y empaquetado pasan;
- MAIN revisó el diff y puede crear un checkpoint local sin publicar nada.

## RESULTADO VERIFICADO

El bloque quedó implementado y auditado el 2026-08-21. Room evolucionó a v6
con las cuatro tablas acordadas, una migración `5→6` aditiva y el esquema
`6.json`. Las instalaciones migradas reciben únicamente la raíz vacía
`MIGRATED_V1`; las instalaciones nuevas permanecen sin raíz hasta que la
persona elija su sector.

Pasaron 217 pruebas JVM, 65 pruebas instrumentadas del módulo de base de datos
y 169 recorridos instrumentados de la aplicación QA en el Samsung `SM-S938B`,
además de lint y los empaquetados acordados. Los paquetes QA temporales fueron
retirados al finalizar. La evidencia y los incidentes encontrados durante la
validación están en
`docs/audits/2026-08-21-configuracion-persistente-y-room-v6.md`.
