# Retirar el modo V1 y fijar la primera base exclusiva V2

- Estado: **CERRADO — INTEGRADO Y VERIFICADO POR MAIN**
- Fecha: 2026-08-23
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `4646f665eec84052a544a5179c72b93971df2700`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Dejar MiGuardia únicamente en modo 2.0**

> Este bloque no vuelve a MiGuardia 1.0 ni borra el trabajo realizado. Separa
> definitivamente los datos y recorridos de la prueba anterior, conserva el
> código útil y deja como único producto ejecutable la experiencia V2 ya
> integrada.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, la secuencia, las reglas sectoriales ni la arquitectura
fuera de lo congelado en este contrato.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

Antes de modificar:

1. ejecutá Puerta 0 de solo lectura;
2. leé completas y en el orden de `AGENTS.md` todas las fuentes obligatorias;
3. confirmá que el checkout parte limpio del HEAD documental informado por
   MAIN;
4. inspeccioná dominio, Room, DataStore, archivos locales, navegación,
   notificaciones y todas las pruebas afectadas;
5. detenete ante un mismatch real, cambios sin dueño o una dependencia V2 no
   contemplada; no descartes ni reemplaces trabajo.

## TASK

Retirar la bifurcación V1 que todavía existe en el árbol y fijar la primera
base de ejecución y persistencia exclusivamente V2:

1. una instalación limpia comienza siempre con el selector de rubro V2;
2. existen sólo los estados `Loading`, `LoadError`, `FreshInstall`,
   `V2NeedsFirstSet` y `V2Ready`;
3. desaparecen `MIGRATED_V1`, la activación futura, la adopción de objetivos u
   horarios V1 y toda ruta visible o profunda del modo anterior;
4. Room nace con identidad propia V2, versión 1 y sin migración desde la cadena
   histórica;
5. toda jornada persistida es V2 y conserva obligatoriamente el par atómico
   `Shift + ShiftWorkSnapshot`;
6. la configuración inicial, el primer conjunto laboral, la carga manual y la
   edición o eliminación individual ya integradas continúan funcionando;
7. el código genérico útil se conserva aunque haya nacido en 1.0.

Este bloque es una limpieza arquitectónica transversal. No agrega una función
laboral nueva ni cambia lo que la persona eligió para sus jornadas.

## CONTEXT

La base funcional cerrada ya permite:

- elegir exactamente Vigilancia privada, Policía, Enfermería o Medicina;
- crear la única configuración laboral y su primer lugar, tipo y horario;
- cargar una o varias jornadas desde la única grilla mensual;
- editar o eliminar una jornada V2 exacta sin cambiar su fecha;
- guardar y comparar atómicamente `Shift + ShiftWorkSnapshot`;
- conservar calendario, F/?, feriados, vacaciones, carpetas médicas, fotos,
  próximo evento, notificaciones y clima.

El árbol todavía contiene deuda de la prueba 1.0:

- `MiGuardiaDatabase` v7 y migraciones `1→7`;
- `WorkConfigurationOrigin.MIGRATED_V1`;
- estados `LegacyV1` y `LegacyV1WithFutureActivation`;
- adopción V1 y procedencia desde `ScheduleCombination`;
- escritores que pueden persistir un `Shift` sin fotografía V2;
- CRUD y pantallas V1 de objetivos, horarios, guardias, francos, Perfil,
  Resumen y Novedades;
- fixtures y pruebas que fabrican una raíz migrada.

ADR 0024 decidió que no existe migración de datos desde 1.0. ADR 0026 fija la
solución técnica: una base Room nueva con identidad V2, sin transformar ni
borrar la anterior. El mismo `applicationId` se conserva, pero no se promete
una actualización con datos: el recorrido soportado comienza con los datos de
la aplicación limpios.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0017, 0020, 0021, 0022, 0023, 0024, 0025 y 0026;
- `docs/prompts/PRIMERA_APERTURA_Y_CONFIGURACION_LABORAL_VISIBLE_V2.md`;
- `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md`;
- `docs/prompts/EDICION_Y_ELIMINACION_DE_JORNADAS_V2.md`;
- las fichas de los cuatro sectores;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo para reconocer código histórico útil;
- `MiGuardiaDatabase`, `Migrations`, `LocalDataStore`, entidades, DAO,
  mapeos, repositorios, esquemas y pruebas Room actuales;
- configuración laboral, catálogo, planificación y persistencia de jornadas
  V2;
- `MainActivity`, `MiGuardiaApp`, WorkSetup, Calendario, gestión V1 y V2,
  excepciones, Perfil, Resumen, próximo evento y notificaciones;
- todos los DataStore, `SharedPreferences` y almacenes de archivos locales.

Las cláusulas de migración, activación o compatibilidad V1 de los prompts
históricos están reemplazadas. No recuperes código desde worktrees históricos.

## DECISIONES CONGELADAS

### 1. Producto exclusivamente V2

- Existen exactamente cuatro rubros: Vigilancia privada, Policía, Enfermería y
  Medicina.
- No existen `Salud`, `Otro`, una quinta opción ni perfiles laborales
  simultáneos.
- La primera apertura siempre es una configuración nueva.
- No existe activación V1→V2, adopción de datos V1 ni cambio de rubro en este
  bloque.
- Consultar el Calendario no escribe; la selección explícita continúa usando la
  única grilla.

### 2. Nueva identidad Room

Implementar exactamente:

- clase `MiGuardiaV2Database`;
- archivo físico `miguardia-v2.db`;
- versión Room `1`;
- esquema exportado en
  `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/1.json`.

`LocalDataStore` debe abrir únicamente esa base. No registrar migraciones
`1→7` ni una ruta desde `MiGuardiaDatabase`.

El archivo histórico `miguardia.db`:

- no se abre;
- no se copia;
- no se importa;
- no se renombra;
- no se transforma;
- no se borra.

La cadena Room histórica, sus pruebas de migración y su directorio de esquemas
se retiran de la compilación y del árbol activo V2. La historia permanece en
Git, `main` y el tag protegido; no se modifica ninguna de esas referencias.

### 3. Esquema inicial V2 exacto

La nueva base contiene estas diecinueve tablas de aplicación, además de los
metadatos internos que Room y SQLite crean por su cuenta:

1. `objectives`;
2. `shifts`;
3. `shift_work_snapshots`;
4. `explicit_day_statuses`;
5. `medical_leaves`;
6. `holidays`;
7. `shift_notes`;
8. `vacations`;
9. `schedule_photos`;
10. `shift_notification_configs`;
11. `shift_notification_reminders`;
12. `work_configuration_roots`;
13. `per_period_hours_definitions`;
14. `work_configuration_revisions`;
15. `per_period_hours_values`;
16. `work_places`;
17. `work_types`;
18. `work_templates`;
19. `workplace_rule_revisions`.

No contiene:

- `schedule_combinations`;
- `shift_novelties`;
- `formal_shift_changes`.

Tampoco contiene:

- `work_configuration_roots.origin`;
- `work_templates.legacyScheduleCombinationId`;
- `shifts.sourceScheduleCombinationId`;
- claves, índices o mapeos exclusivos de esas columnas.

Fuera de esas tres tablas, esas tres columnas y el cambio de
`Shift.sourceObjectiveId` a obligatorio, preservar exactamente el contrato de
Room v7 de cada entidad conservada:

- nombres y tipos de columnas;
- valores por defecto y nulabilidad;
- claves primarias;
- índices y restricciones únicas;
- claves foráneas y acciones `ON DELETE`/`ON UPDATE`.

Cualquier otro cambio de esquema vuelve a MAIN. La dependencia no puede
aprovechar la nueva versión 1 para rediseñar una restricción, una relación o un
default no enumerado.

Las dos tablas de Novedades V1 no se reutilizan para inventar situaciones
especiales V2. Ese modelo se definirá en su bloque propio.

### 4. Invariantes de configuración y jornadas

- `WorkConfigurationHistory` no posee origen y siempre contiene al menos una
  revisión cuando existe una raíz.
- Una ausencia de raíz significa solamente `FreshInstall`.
- Una fecha anterior a la primera revisión usa la retrocarga consciente ya
  integrada; nunca se clasifica como V1.
- `Shift.sourceObjectiveId` es obligatorio y coincide con el objetivo físico de
  su fotografía.
- Cada `Shift` persistido posee exactamente una `ShiftWorkSnapshot`.
- Una fila sin fotografía se rechaza como `InvalidLocalDataException`; no se
  proyecta como `LegacyV1` ni se deja sólo en modo lectura.
- `V2ShiftRepository` es la única frontera de escritura estructural de
  jornadas. Ningún repositorio público puede insertar, actualizar o borrar un
  `Shift` aislado.
- La carga y la edición conservan políticas de fechas ocupadas, advertencias,
  expectativas inmutables, CAS y rollback ya integrados.

La lógica genérica de selección mensual, ocupación, solapamiento y descanso
puede extraerse o conservarse, pero no puede seguir dependiendo de
`ScheduleCombination` ni producir una mutación persistible de sólo `Shift`.

### 5. Catálogo sin adopción

Retirar:

- `WorkPlaceAdoption`;
- `ResolvedWorkPlaceAdoption`;
- `WorkPlaceAdoptionResult`;
- `WorkCatalogRepository.adoptWorkPlace(...)`;
- procedencia y consultas de `ScheduleCombination`;
- errores que presentan una jornada o un objetivo como “heredado” o
  “adoptado”.

Conservar:

- `Objective` y la tabla `objectives` como identidad física respaldatoria de
  `WorkPlace`;
- creación atómica de lugares desde WorkSetup;
- `WorkPlace`, `WorkType`, `WorkTemplate`, reglas por lugar y sus fotografías;
- archivo/reactivación y edición futura que ya sean compatibles con V2.

No hagas un renombrado cosmético masivo de `Objective`, `Shift` o clases útiles
por el solo hecho de haber nacido en 1.0.

### 6. Runtime y navegación

Retirar por completo de la ejecución:

- Resumen V1;
- Perfil laboral V1;
- Objetivos y horarios V1;
- `Cargar datos` y el CRUD V1 de guardias y francos;
- las herramientas estructurales V1 montadas sobre el modo de edición del
  Calendario;
- Novedades V1 y su entrada profunda desde notificaciones;
- `Informar novedad` como acción de una notificación;
- cualquier pantalla explicativa de activación o raíz migrada;
- observadores, ViewModels, borradores y `SavedStateHandle` exclusivos de esas
  rutas.

No alcanza con ocultar botones. Las rutas, intents, acciones y escritores deben
quedar desconectados o retirados.

Conservar en ejecución:

- Calendario y detalle de consulta;
- `CalendarInteractionMode.EDIT` como soporte de la selección de fechas de la
  carga manual V2;
- selector inicial, primer lugar, tipos y horarios;
- `Mi forma de trabajar`;
- `Cargar jornadas`;
- edición y eliminación exacta V2;
- F/?, feriados, vacaciones, carpetas médicas, notas y fotos compatibles;
- motores y observadores de próximo evento y notificaciones ya usados por las
  jornadas V2;
- clima y zoom interno.

No adaptes todavía el vocabulario ni la arquitectura final de Resumen, próximo
evento o notificaciones. Sólo impedí que abran una ruta V1.

### 7. Perfil, DataStore y archivos

Los DataStore de apariencia, notificaciones y clima son capacidades comunes y
se conservan. Ningún valor suyo puede decidir `FreshInstall`, crear una raíz o
evitar el selector de rubro.

La planificación conserva el nombre o apodo opcional en su DataStore dueño.
Implementar exactamente este límite:

- conservar `GuardProfileStore` y `guard_profile.preferences_pb` únicamente
  como contrato neutral de `displayName` opcional;
- retirar `company`, `DEFAULT_GUARD_COMPANY`, `GUARD_PROFESSION` y toda
  proyección de empresa o profesión;
- retirar la pantalla, el ViewModel y el cableado de Perfil V1;
- no montar ni abrir ese almacén durante el recorrido V2 actual;
- no borrar, copiar, recrear ni migrar su archivo de preferencias;
- no crear una pantalla, saludo ni nuevo consumidor de nombre/apodo en este
  bloque.

No queda a criterio del implementador eliminar el nombre/apodo ni diseñar un
Perfil V2. Esa superficie se resolverá en su bloque futuro.

La garantía funcional corresponde a una instalación con datos de aplicación
limpios. No implementes limpieza, copia, lectura o migración automática de
DataStore, preferencias, fotos, caché, permisos o alarmas de la prueba 1.0. No
presentes el nuevo nombre de base como compatibilidad de actualización.

## OUTPUT

Entregar un candidato ejecutable que incluya:

1. dominio sin origen, clasificación ni errores V1;
2. `MiGuardiaV2Database` versión 1 y su esquema exportado;
3. repositorios V2 sin adopción ni escritor de jornadas incompletas;
4. runtime con una sola experiencia V2;
5. Perfil fijo y Novedades V1 fuera de la ejecución;
6. pruebas actualizadas sin fixtures `MIGRATED_V1`;
7. evidencia de que la base anterior permanece intacta;
8. regresión completa de los recorridos V2 ya cerrados.

Eliminar pruebas obsoletas es válido únicamente junto con pruebas V2
equivalentes. Informá qué suites se retiraron, por qué ya no representan el
producto y qué cobertura las reemplaza.

## SCOPE

Permitido:

- `core/domain/src/main/**` y `core/domain/src/test/**` para retirar contratos
  V1 y conservar las invariantes V2;
- `core/database/src/main/**`, `core/database/src/test/**`,
  `core/database/src/androidTest/**` y `core/database/schemas/**` para la nueva
  base exclusiva;
- `app/src/main/**`, `app/src/test/**` y `app/src/androidTest/**` para retirar
  rutas V1 y verificar la única experiencia V2;
- recursos de texto y test tags estrictamente relacionados;
- borrado explícito y revisable de fuentes, pruebas y esquemas obsoletos dentro
  de estas rutas.

No uses comandos destructivos de Git. Cada eliminación debe aparecer en el
diff normal y seguir siendo recuperable desde la historia.

## DEPENDENCIES

Reutilizar como contratos cerrados:

- configuración por vigencia y referencias de horas;
- `WorkSetupState` V2, después de retirar sus variantes Legacy;
- `WorkConfigurationRepository` y `WorkCatalogRepository` ajustados;
- `Objective` como identidad física;
- `V2ShiftRepository` como única escritura estructural;
- `V2ShiftWrite`, `ShiftWorkSnapshot`, expectativas de ocupación y CAS;
- retrocarga consciente de fechas anteriores;
- única grilla, carga manual y edición/eliminación V2;
- repositorios comunes de F/?, feriados, vacaciones, carpetas médicas, notas,
  fotos y avisos;
- reloj, zona y UUID inyectables;
- tema Vigilia y zoom interno 100 %, 150 % y 200 %.

## DO NOT

- no asumir el rol de MAIN;
- no crear proyecto, rama, worktree, tarea o subagente;
- no migrar, importar, copiar, renombrar ni borrar `miguardia.db`;
- no implementar una migración Room `7→8`;
- no usar `fallbackToDestructiveMigration`, `allowMainThreadQueries`,
  `REPLACE` ni una base en memoria de producción;
- no cambiar Gradle, dependencias, repositorios externos, manifiesto, permisos,
  `applicationId`, `versionCode`, `versionName`, SDK o firma;
- no cambiar rubro ni crear varios perfiles laborales;
- no implementar recurrencias, edición de series ni jornadas automáticas;
- no implementar horario real, extras, cumplimiento, disponibilidad, guardia
  pasiva ni situaciones especiales;
- no implementar Resumen V2 ni adaptar todavía próximo evento o
  notificaciones más allá de retirar la entrada V1;
- no implementar onboarding, Ayuda, widget, informes, copias o bloqueo;
- no crear `Salud`, `Otro` ni una quinta opción;
- no agrupar Enfermería y Medicina;
- no imponer 204 horas, 21:00–06:00, nocturnidad, disponibilidad, horarios o
  reglas por profesión;
- no agregar montos, salarios, liquidaciones, convenios ni información
  sindical;
- no agregar cuentas, red, nube, sincronización, ubicación, OCR, analítica,
  telemetría ni datos reales;
- no modificar documentación canónica, ADR, auditorías ni el índice de prompts;
- no hacer commit, push, tag, merge, rebase, reset ni descartar cambios;
- no abrir, instalar sobre, consultar, limpiar ni modificar producción.

Si una corrección exige salir de estos límites, detené esa parte y devolvé a
MAIN el defecto y la dependencia concreta. No amplíes el alcance en silencio.

## VALIDATION

### Puerta 0 y diff

Antes de editar y antes del handoff:

- confirmar ruta, rama, HEAD, upstream, base protegida, worktrees, remoto y
  autor Git;
- listar modificados, staged y no rastreados;
- revisar cada eliminación y cada archivo nuevo;
- ejecutar `git diff --check`;
- buscar secretos, datos reales, material monetario o sindical y cambios de
  Gradle, manifiesto, permisos, versión o paquete.

### Dominio JVM

Cubrir como mínimo:

1. no existen `WorkConfigurationOrigin`, `MIGRATED_V1`, `LegacyV1` ni
   activación futura;
2. historia ausente proyecta `FreshInstall`;
3. toda historia existente tiene al menos una revisión;
4. sólo `V2NeedsFirstSet` y `V2Ready` representan raíces cargadas;
5. una fecha anterior produce retrocarga consciente, no una selección V1;
6. catálogo y plantillas no poseen procedencia `ScheduleCombination`;
7. toda jornada exige objetivo físico y fotografía V2 coherente;
8. una jornada sin fotografía se rechaza como dato inválido;
9. no existe escritor público de `Shift` aislado;
10. carga, ocupadas, advertencias, CAS, edición y eliminación conservan sus
    invariantes.

### Room V2 versión 1

Ejecutar en instrumentación Android:

1. creación directa de `MiGuardiaV2Database` versión 1;
2. lista exacta de las diecinueve tablas de aplicación autorizadas, distinguidas
   de los metadatos internos de Room y SQLite;
3. ausencia de las tres tablas y las tres columnas V1 retiradas;
4. base nueva sin raíz, sector, catálogo, jornadas, 204 horas ni nocturnidad;
5. creación inicial atómica, reapertura y segunda raíz rechazada;
6. primer conjunto, catálogo y reglas persistidos y reabiertos;
7. carga, actualización y eliminación del par jornada/fotografía;
8. rollback completo ante fallo y conflictos CAS;
9. rechazo controlado de `Shift` sin fotografía y de fotografías huérfanas;
10. fuera de los deltas autorizados, columnas, tipos, defaults, nulabilidad,
    claves primarias, índices, únicas, claves foráneas y acciones
    `ON DELETE`/`ON UPDATE` idénticos a Room v7;
11. `integrity_check` y `foreign_key_check` correctos;
12. no hay migraciones históricas registradas ni
    `fallbackToDestructiveMigration`;
13. un archivo testigo llamado `miguardia.db` conserva contenido y hash byte a
    byte después de crear, usar y cerrar `miguardia-v2.db`;
14. el único esquema activo nuevo coincide con la base creada y posee SHA-256
    registrado en el handoff.

### Aplicación, Compose y navegación

Con datos ficticios:

1. carga y error son bloqueantes y el error permite reintentar;
2. instalación limpia muestra primero los cuatro rubros exactos;
3. no se puede continuar sin elegir y la elección persiste al reabrir;
4. `V2NeedsFirstSet` muestra Calendario vacío y guía del primer lugar;
5. `V2Ready` ofrece `Cargar jornadas`, `Mi forma de trabajar` y edición exacta;
6. primer lugar, tipo, horario y reglas conservan su guardado atómico;
7. carga de una y varias fechas, retrocarga y ocupadas siguen funcionando;
8. editar y eliminar una jornada actualiza inmediatamente el Calendario;
9. ninguna pantalla, panel, deep link, intent o notificación alcanza Resumen
   V1, Perfil V1, Objetivos y horarios, carga V1, francos o Novedades;
10. una notificación de jornada no ofrece `Informar novedad`;
11. una fila corrupta sin fotografía no obtiene acciones de edición y produce
    un error controlado;
12. recreación y reapertura conservan borradores V2 válidos sin restaurar
    superficies V1;
13. próximo evento, notificaciones, feriados, vacaciones, carpetas médicas,
    fotos y clima no regresionan;
14. claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % mantienen
    las acciones V2 alcanzables;
15. semántica, estados y errores no dependen sólo del color.

No mantengas una suite pasando mediante un fixture `MIGRATED_V1`. Cada prueba
que abra `MainActivity` debe declarar un estado limpio, `V2NeedsFirstSet` o
`V2Ready` determinista.

### Batería local

Ejecutar serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Obtener conteos reales desde los XML. La cantidad puede bajar al retirar suites
V1, pero cada eliminación debe estar justificada y reemplazada por cobertura
V2 proporcional. Distinguir siempre:

- JVM verificado;
- lint;
- APK Debug compilado;
- APK QA compilado;
- AndroidTest compilado;
- instrumentación ejecutada;
- revisión física;
- pendiente.

### Instrumentación y revisión física

Este bloque toca Room, navegación, Compose, recreación y notificaciones. La
compilación de AndroidTest no alcanza.

Validar:

- nueva base Room en Android;
- Samsung `SM-S938B`, API 36, sólo con paquete QA y datos ficticios;
- API 26 como piso mediante un dispositivo o emulador controlado;
- instalación limpia, primera configuración, primer conjunto, carga, edición,
  eliminación, reapertura y notificación sin ruta V1;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %.

No consultes ni modifiques `font_scale`, densidad, tamaño visual o zoom del
sistema. No habilites permisos especiales ni ejecutes QA física de alarmas
exactas en este bloque.

Antes de instalar, limpiar o desinstalar QA, pedí autorización expresa en la
tarea implementadora. Con esa autorización, desinstalá exclusivamente QA y sus
paquetes de prueba al finalizar. Sin ella, no toques el teléfono y marcá la QA
física como pendiente. Producción no se abre ni se modifica.

API 26 es parte del cierre transversal. Si no está disponible, devolvé el
candidato y el bloqueo exacto a MAIN; no inventes evidencia ni declares el
bloque terminado.

## HANDOFF A MAIN

Entregar un handoff compacto con:

- `OBJECTIVE`;
- `CHANGES`;
- `FILES` modificados, nuevos y eliminados;
- `DECISIONS` menores dentro del contrato;
- `DATABASE` con clase, nombre, versión, tablas, esquema y SHA-256;
- `VALIDATION` con comandos, conteos y resultados reales;
- `PHYSICAL QA` con dispositivos, paquetes y estado final;
- `REMOVED V1 SURFACES` y cobertura V2 que las reemplaza;
- `PRESERVED SHARED CODE`;
- `RISKS` y `PENDING`;
- `GIT` con ruta, rama, HEAD, upstream y estado;
- `NEXT` reservado a MAIN.

La entrega queda directamente en el checkout compartido y sin commit. MAIN
auditará cada hunk y eliminación, repetirá pruebas proporcionales, encargará una
revisión independiente, actualizará documentación y creará el checkpoint local
únicamente si acepta el resultado.

## DONE WHEN

La dependencia está lista para MAIN solamente cuando:

- Room V2 versión 1 nace y reabre con el esquema exacto;
- `miguardia.db` queda intacta y fuera de la ejecución;
- no existen origen, activación, adopción, procedencia ni estados V1;
- no existe ningún escritor que pueda persistir una jornada sin fotografía;
- una instalación limpia muestra los cuatro rubros y sólo continúa por V2;
- configuración, primer conjunto, carga, edición y eliminación V2 no
  regresionan;
- Resumen, Perfil, Objetivos, carga estructural, francos y Novedades V1 no son
  alcanzables ni por interfaz ni por intents;
- el código común útil permanece y tiene pruebas;
- JVM, lint, compilaciones, Room instrumentado, Samsung y API 26 están verdes;
- QA queda retirada sólo si hubo autorización y producción permanece intacta;
- el diff está revisado, sin cambios fuera de alcance, sin commit y sin push.

## STOP CONDITIONS

Detenete y devolvé el hallazgo a MAIN si:

- ruta, rama, HEAD o limpieza no coinciden;
- aparece un cambio local sin dueño;
- una función V2 real depende de una tabla o ruta marcada para retirar y no
  existe reemplazo dentro del contrato;
- haría falta migrar, copiar o borrar datos anteriores;
- haría falta cambiar paquete, versión, permiso, dependencia o Gradle;
- el esquema no puede verificarse o una prueba queda roja;
- la QA obligatoria no puede ejecutarse;
- una tabla o campo no puede clasificarse sin una decisión material;
- haría falta tocar producción, publicar o hacer push.

## CIERRE DE MAIN — 2026-08-23

MAIN auditó el candidato recibido sobre `a306221`, revisó las 164 rutas de
código y pruebas que componían el diff final previo a documentación y encargó
una revisión independiente de sólo lectura. No quedaron defectos bloqueantes.

Durante la integración se corrigieron, dentro del alcance:

- la preservación visible de Feriados y Notas como capacidades comunes V2;
- la validación global de objetivos, revisiones históricas y pares
  `Shift + ShiftWorkSnapshot`;
- la frontera única y atómica de escritura mediante `V2ShiftRepository`;
- fixtures QA deterministas y protegidos contra ejecución fuera del paquete
  exacto de pruebas;
- pruebas de recreación, calendario, selector RGB y compatibilidad API 26;
- esperas deterministas de Compose y notificaciones sin debilitar los
  contratos funcionales.

La base resultante es `MiGuardiaV2Database`, archivo `miguardia-v2.db`, Room
versión 1, con 19 tablas. El esquema exportado tiene identity hash
`d583ce68e247cba7574a9e3b25b29e69` y SHA-256
`5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`.
La comparación estructural contra Room v7 no encontró diferencias fuera de
las tres tablas, tres columnas y nulabilidad autorizadas por ADR 0026.

Validación final:

- JVM: 283/283 —168 de dominio, 5 de base y 110 de aplicación—;
- lint: 0 errores y 2 avisos de actualización ya conocidos;
- APK Debug, APK QA, AndroidTest QA y AndroidTest de base: compilados;
- Samsung `SM-S938B`, API 36: runner verde `OK (148 tests)`; 147 aprobadas y
  una omitida conscientemente porque requiere acceso especial a alarmas
  exactas, no habilitado por este contrato;
- Room en Samsung: 61/61;
- emulador Android 8.0, API 26: 148/148 de aplicación y 61/61 de Room;
- revisión visual directa en Samsung y API 26 con instalación limpia y los
  cuatro rubros exactos;
- `git diff --check`: correcto.

Los paquetes QA y de prueba quedaron desinstalados en ambos dispositivos. El
emulador quedó apagado, el Samsung recuperó su rotación original y producción
no fue abierta ni modificada. No se consultaron ni cambiaron `font_scale`,
densidad o tamaño visual del sistema. No hubo push, tag, Release ni cambios en
`main`.

La evidencia durable está en
`docs/audits/2026-08-23-retiro-modo-v1-y-base-room-v2.md`. El siguiente bloque
recomendado es recurrencias y edición de una fecha o de todo lo futuro; no está
habilitado hasta que Joaquin pida su prompt.
