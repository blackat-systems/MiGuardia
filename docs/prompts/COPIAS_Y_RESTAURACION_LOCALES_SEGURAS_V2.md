# Copias y restauración locales seguras V2

- Estado: **HABILITADO — IMPLEMENTACIÓN PENDIENTE**
- Fecha: 2026-08-29
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `ff2f9b6699606f7ef3c7e599e2a6b4da25b40c67`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Copias y restauración seguras**

## QUÉ HACE

Permite crear una copia completa de los datos locales de MiGuardia y
recuperarla después mediante dos decisiones conscientes:

- `Combinar con mis datos`, que conserva lo actual, omite duplicados exactos y
  pregunta cómo resolver cada conflicto permitido;
- `Reemplazar todo`, que reconstruye los datos recuperables desde la copia sólo
  después de mostrar qué se perderá y pedir una segunda confirmación.

La persona elige dónde guardar o abrir el archivo con el selector de Android,
si incluye fotos y si lo protege con contraseña.

## POR QUÉ EXISTE

MiGuardia guarda localmente jornadas, configuración laboral, horarios reales,
extras, disponibilidad, situaciones, preferencias y fotos. Hoy una
desinstalación o pérdida del teléfono puede volver irrecuperable esa historia.

Esta dependencia existe para que el usuario pueda conservarla y restaurarla sin
copiar archivos internos a ciegas, sin sobrescribir datos silenciosamente y sin
convertir MiGuardia en un servicio de nube o sincronización.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, la arquitectura V2 ni la secuencia de
la hoja de ruta.

Trabajá directamente en el proyecto y rama existentes. No crees otro proyecto,
rama, worktree, tarea ni subagente. MAIN conserva documentación canónica,
auditoría final, staging y checkpoints.

Primero inspeccioná los veintisiete agregados/tablas Room, todos los almacenes de
preferencias, las fotos privadas, los reconciliadores y el arranque de la app.
Conservá las fronteras vigentes y agregá únicamente la infraestructura necesaria
para copia y restauración.

## TASK

Implementar integralmente **Copias y restauración seguras** como un flujo local,
manual, versionado, verificable y recuperable.

El recorrido mínimo debe permitir:

1. abrir `Copias y restauración` desde la sección Aplicación del menú;
2. crear una copia completa de la unidad local recuperable;
3. elegir conscientemente si incluye fotos;
4. protegerla opcionalmente con contraseña, recomendando el cifrado;
5. guardarla mediante el selector de documentos de Android;
6. abrir una copia mediante el selector de documentos;
7. ingresar la contraseña cuando corresponda;
8. validar el archivo sin modificar datos vivos;
9. ver una vista previa clara de contenido, compatibilidad y conflictos;
10. elegir `Combinar con mis datos` o `Reemplazar todo`;
11. resolver todos los conflictos antes de escribir;
12. aplicar el plan completo o no aplicar nada;
13. recuperar automáticamente un corte o muerte del proceso dejando íntegro el
    estado anterior o el nuevo;
14. reabrir la aplicación con Calendario, Horas, Resumen, Widget y avisos
    reconciliados con el estado restaurado.

No implementes copias mensuales, copia automática, nube, sincronización,
recordatorios, bloqueo de acceso, Ayuda, agenda de pacientes ni publicación.

## CONTEXT

La base cerrada ya posee:

- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola línea temporal/configuración laboral V2 identificada por
  `timelineId`;
- jornadas manuales y materializadas por recurrencias;
- fotografías históricas de lugar, tipo, horario, color y puesto;
- horario planificado y real, extras por jornada e independientes;
- disponibilidad separada del trabajo;
- feriados, vacaciones, carpetas médicas, estados explícitos, notas y fotos;
- Resumen, próximo evento, notificaciones, Widget e Informes;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db` y veintisiete
  tablas;
- preferencias locales separadas para perfil, apariencia, Resumen, Clima,
  Notificaciones y Widget;
- `allowBackup=false` y reglas de extracción que excluyen el estado de la app;
- `minSdk 26`, `compileSdk 37` y `targetSdk 37`;
- APIs estándar JCA, ZIP/streams y AndroidX ya disponibles.

El contrato histórico V1 de copias mensuales no gobierna este bloque. V2 posee
relaciones y revisiones que cruzan meses; la primera copia V2 siempre representa
la unidad recuperable completa.

## INPUTS

Leé completamente y en el orden obligatorio definido por `AGENTS.md`:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las cuatro fichas de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
9. ADR 0017 a 0035, con atención especial a 0024, 0026, 0032, 0033, 0034 y
   `docs/adr/0035-copias-locales-versionadas-y-restauracion-atomica.md`;
10. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
11. prompts cerrados de persistencia V2, recurrencias, horario real, extras,
    disponibilidad, Resumen, notificaciones, Widget e Informes;
12. manifiesto, reglas de backup/extracción, FileProvider, inicialización,
    entidades, DAO, repositorios, validación local, stores y pruebas afectadas.

Fuentes técnicas oficiales:

- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files);
- [criptografía de Android](https://developer.android.com/privacy-and-security/cryptography);
- [`SecretKeyFactory` y PBKDF2](https://developer.android.com/reference/javax/crypto/SecretKeyFactory).

No uses la conversación ni este resumen como sustituto de esas fuentes.

## DEPENDENCIES

Dependencias cerradas que podés asumir:

- núcleo V2 declarado apto para segunda capa;
- Room V5 y sus migraciones 1→2→3→4→5;
- pruebas cruzadas del núcleo;
- Widget de próximo evento;
- Informes locales de jornadas y horas;
- ADR 0035 aceptado para implementación.

No existe dependencia externa ni servicio de nube requerido.

## PUERTA 0

Antes de editar, verificá en vivo:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse @{upstream}
git rev-parse v1.0.0^{}
git rev-parse main
git rev-parse origin/main
git status --short --branch
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
git worktree list --porcelain
git config --get user.name
git config --get user.email
git remote get-url origin
```

Debe coincidir:

- ruta exacta del proyecto;
- rama `codex/miguardia-2.0`;
- HEAD documental exacto informado por MAIN;
- upstream `origin/codex/miguardia-2.0`;
- checkout limpio, sin staged ni archivos nuevos;
- autor `joaquin <blackat.systems@gmail.com>`;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- worktrees históricos preservados.

Confirmá además JDK, SDK, Gradle wrapper y espacio libre suficiente. No uses ADB
ni inicies emuladores durante Puerta 0.

Ante mismatch, detached HEAD, cambios de origen desconocido, prompt no
habilitado o dependencia ausente: detenete sin editar y devolvé `MAIN BLOQUEADA`.

## DECISIONES CONGELADAS

### 1. Unidad recuperable completa

La copia lógica incluye las veintisiete tablas de aplicación y sus relaciones:

```text
objectives
shifts
shift_work_snapshots
explicit_day_statuses
medical_leaves
holidays
shift_notes
vacations
schedule_photos
shift_notification_configs
shift_notification_reminders
work_configuration_roots
per_period_hours_definitions
work_configuration_revisions
per_period_hours_values
work_places
work_types
work_templates
workplace_rule_revisions
recurring_plans
recurring_plan_revisions
recurring_occurrences
extra_work_classes
shift_actual_records
shift_extra_intervals
independent_extra_work_records
availability_windows
```

Incluye, mediante campos semánticos versionados y no archivos crudos:

- nombre o apodo opcional;
- tema y zoom interno 100/150/200;
- orden, visibilidad e introducción de Resumen;
- preferencias durables de Notificaciones;
- identidades normalizadas de avisos ocultados conscientemente, únicamente si
  siguen correspondiendo a eventos restaurados;
- preferencias durables de Clima;
- filas y bytes de fotos como una unidad, si `Incluir fotos` está activo.

Excluye:

- cualquier dato, archivo o migración V1;
- SQLite/WAL/SHM, `room_master_table` y DataStore copiados en bruto;
- IDs y preferencias por instancia del Widget;
- alarmas instaladas, avisos mostrados y tracking reconstruible;
- URI personalizada de sonido;
- permisos, canales, estado del launcher y ajustes Android;
- intentos/reintentos/caché temporal de Clima;
- informes PDF/XLSX guardados, artefactos privados o staging de Informes;
- temporales, journals, cachés, logs, secretos, APK y configuración local.

Si no incluye fotos, tampoco incluye filas `schedule_photos`. Nunca restaures
metadatos que apunten a bytes ausentes.

### 2. Formato lógico, canónico y versionado

No copies el archivo Room ni DataStore en bruto. Definí un contenedor propio con:

- magia y versión;
- versión mínima de lector;
- fecha UTC y zona sólo informativa;
- versión Room de origen;
- `timelineId`;
- modalidad de fotos;
- algoritmo y parámetros de cifrado;
- manifiesto canónico con cantidades, tamaños y SHA-256 por entrada;
- representación lógica determinista de los agregados;
- preferencias semánticas conocidas;
- fotos con nombre opaco, tamaño, MIME validado y digest.

Documentá internamente el formato, orden canónico, límites y reglas de evolución.
Una versión futura desconocida se rechaza sin escribir. Una versión anterior sólo
se acepta mediante lector/adaptador explícito y probado.

Contrato público exacto:

```text
Extensión: .miguardia-backup
MIME: application/vnd.blackatsystems.miguardia.backup
Nombre sugerido: MiGuardia_copia_yyyy-MM-dd_HHmm.miguardia-backup
```

El nombre usa la fecha/hora local sólo para ayudar a reconocer el archivo y no
incluye nombre, sector, lugar ni otro dato personal.

Usá streaming y límites previos. No cargues toda la copia o todas las fotos en
memoria. Rechazá:

- rutas absolutas, `..`, entradas duplicadas o escape del staging;
- enlaces simbólicos u objetos no esperados;
- cantidad/tamaño total o descomprimido excesivo;
- expansión ZIP desproporcionada;
- manifest incompleto, hash incorrecto o contenido truncado;
- UUID, fechas, enums, relaciones o tipos inválidos;
- MIME declarado que no coincide con bytes permitidos.

Los límites deben ser constantes productivas razonadas y tener pruebas de borde.

### 3. Contraseña opcional y recomendada

Con contraseña:

- derivá AES-256 mediante `PBKDF2WithHmacSHA256`, sal aleatoria y parámetros
  versionados;
- cifrá y autenticá con `AES/GCM/NoPadding`, nonce aleatorio único y encabezado
  estable como AAD;
- usá JCA y `SecureRandom`, sin algoritmo casero, proveedor fijado ni dependencia;
- no guardes, registres ni recuperes la contraseña;
- contraseña incorrecta o manipulación falla antes de mostrar datos o escribir.

La creación pide contraseña y confirmación, permite mostrar/ocultar localmente y
explica que perderla hace imposible recuperar la copia.

Sin contraseña exige una confirmación explícita que explique que cualquiera con
acceso al archivo podrá leerlo. SHA-256 detecta corrupción accidental; no lo
presentes como protección contra manipulación.

### 4. Captura coherente

La exportación debe representar una sola fotografía lógica:

- leé Room dentro de una frontera transaccional coherente;
- congelá o revalidá las preferencias y fotos incluidas;
- compará huellas antes y después;
- si una mutación concurrente impide coherencia, reintentá de forma acotada o
  fallá visiblemente;
- generá primero un archivo privado completo, validalo y recién después copialo
  al URI elegido;
- cancelar el selector no modifica datos ni deja un archivo externo que parezca
  válido a medias;
- doble toque no inicia dos capturas.

### 5. Vista previa de sólo lectura

`ACTION_OPEN_DOCUMENT` sólo abre una entrada no confiable hacia staging privado.
Antes de escribir:

1. detectá si requiere contraseña;
2. descifrá y validá contenedor, versión, límites, manifiesto y hashes;
3. construí un candidato aislado Room V5;
4. validá las 27 tablas, claves foráneas, integridad e invariantes V2;
5. compará candidato y estado actual como agregados lógicos completos;
6. mostrale al usuario fecha, sector o sectores históricos, conteos, fotos,
   compatibilidad, novedades, duplicados, conflictos y elementos que desaparecerían;
7. no cambies Room vivo, preferencias, fotos, Widget, avisos ni Informes.

No solicites el modo de restauración antes de validar la copia.

### 6. Combinar con mis datos — recomendado

Combinar está disponible si el destino no posee línea temporal o si copia y
destino comparten el mismo `timelineId`.

Clasificá agregados completos:

- `Nuevo`: incorporar con todas sus dependencias;
- `Idéntico`: omitir sin duplicar;
- `Conflicto`: misma identidad con contenido distinto o relación incompatible;
- `Solapamiento`: identidades diferentes que el dominio permite coexistir sólo
  tras una decisión consciente;
- `Inválido`: bloquea toda restauración.

Resoluciones:

- `Conservar lo actual` — valor predeterminado seguro;
- `Usar lo de la copia` — reemplaza el agregado completo;
- `Conservar ambos` — únicamente para identidades ya distintas que pueden
  coexistir válidamente.

No fabriques UUID para evadir conflictos. No ofrezcas `Conservar ambos` para una
configuración única, una misma identidad modificada o una relación exclusiva.
No escribas mientras quede un conflicto sin resolver.

La selección por defecto conserva lo actual. Permití aplicar una decisión
general sólo a conflictos equivalentes y seguir revisando excepciones. Mostrá un
resumen final antes de confirmar.

Los avisos ocultados válidos se unen. Las demás preferencias usan la resolución
elegida y nunca aceptan claves desconocidas.

Una copia con otro `timelineId` no se combina con un destino no vacío. Explicá
que MiGuardia usa una sola configuración laboral y ofrecé conservar lo actual o
continuar por `Reemplazar todo`.

### 7. Reemplazar todo

Reconstruí toda la unidad recuperable desde la copia y eliminá de esa unidad lo
que no esté en ella. Antes:

- mostrale al usuario cantidades actuales que desaparecerán y cantidades que se
  recuperarán;
- indicá claramente si la copia no contiene fotos;
- exigí una segunda confirmación con acción exacta `Reemplazar todo`;
- no reutilices confirmaciones de otro flujo ni admitas doble toque.

Conservá las instancias actuales del Widget y los documentos externos ya
guardados por el usuario. Después del éxito, limpiá sólo artefactos/staging
privados de Informes para que no representen datos anteriores. No consultes,
elimines ni reemplaces PDF/XLSX externos.

### 8. Restauración atómica y recuperación de proceso

Aplicá siempre un estado completo planificado, también en `Combinar`. No vayas
escribiendo mientras el usuario decide.

Implementá un coordinador único con bitácora durable privada en
`noBackupFilesDir`, al menos con:

```text
PREPARED
SWAPPED
VERIFIED
COMMITTED
```

Antes de la primera mutación:

- comprobá espacio para candidato, rollback, fotos y margen;
- prepará y validá el estado final completo;
- creá un punto privado de recuperación del estado actual;
- bloqueá mutaciones visibles y pausá reconciliadores con escritura reconstruible;
- registrá `PREPARED` de forma durable.

Durante la aplicación:

- Room cambia en una sola transacción o mediante un intercambio controlado que
  preserve exactamente su esquema V5;
- preferencias se escriben por APIs semánticas;
- fotos usan staging, nombres opacos, movimientos seguros y compensación;
- cada fase se sincroniza antes de avanzar.

Antes de `COMMITTED` verificá:

- `foreign_key_check` vacío;
- `integrity_check=ok`;
- invariantes V2;
- conteos y hashes esperados;
- reapertura de Room y stores;
- filas/fotos sin huérfanos.

El arranque de la app debe recuperar una bitácora inconclusa antes de crear
runtimes de Notificaciones, Widget o Clima. Frente a error, cancelación tardía o
muerte del proceso, debe quedar íntegramente el estado anterior o el nuevo;
nunca una mezcla observable.

El punto de recuperación es privado, temporal y no exportable. Se elimina sólo
después de verificar `COMMITTED`.

### 9. Reconciliación posterior

Después del commit:

- cerrá y reabrí observadores sobre la fuente restaurada;
- descartá tracking reconstruible de Notificaciones;
- reconciliá avisos sin disparar una alerta invasiva por la restauración;
- conservá IDs de Widget instalados y pedí que se vuelvan a renderizar;
- no simules permisos, canales, alarma exacta ni estado del launcher;
- si una URI de sonido ya no es portable, usá el fallback vigente y explicá que
  debe elegirse nuevamente;
- regresá al Calendario ya actualizado.

### 10. Interfaz y estados

Agregá `Copias y restauración` en la sección Aplicación del menú principal.

Pantalla inicial:

- `Crear una copia`;
- `Restaurar una copia`;
- explicación breve de que la copia es manual, local y no sincroniza;
- última operación sólo como estado de sesión seguro, sin ruta ni contenido.

Estados mínimos:

```text
IDLE
CAPTURING
WAITING_FOR_CREATE_DESTINATION
COPYING_OUT
WAITING_FOR_OPEN_SOURCE
READING
PASSWORD_REQUIRED
VALIDATING
PREVIEW
RESOLVING_CONFLICTS
READY_TO_APPLY
APPLYING
RECOVERING
SUCCESS
ERROR
```

Conservá mediante `SavedStateHandle` sólo opciones no sensibles y la etapa que
pueda reconstruirse. No guardes contraseña, bytes, URI persistente, rutas,
manifiesto ni contenido laboral. Después de recreación, revalidá cualquier
fuente y nunca restaures visualmente un éxito no confirmado.

Todos los estados largos deben estar fuera del hilo principal, mostrar progreso
honesto, bloquear doble toque y permitir cancelar únicamente mientras siga
siendo seguro. Error y cancelación conservan los datos actuales.

Respetá:

- claro/oscuro;
- retrato/paisaje;
- zoom interno 100/150/200;
- textos completos y controles alcanzables;
- TalkBack, foco, roles, etiquetas y estados que no dependan sólo del color;
- ninguna consulta o modificación de `font_scale`, densidad o tamaño visual del
  sistema.

### 11. Android y privacidad

- Crear usa `ACTION_CREATE_DOCUMENT` con
  `application/vnd.blackatsystems.miguardia.backup`.
- Restaurar usa `ACTION_OPEN_DOCUMENT` con el mismo MIME y fallback consciente
  si un proveedor no filtra correctamente.
- No solicites permiso general de almacenamiento.
- No amplíes el `FileProvider` limitado a Informes.
- El selector puede mostrar proveedores instalados por el usuario; MiGuardia no
  integra ni sube automáticamente a ninguno.
- `allowBackup=false`, reglas de backup/extracción, permisos y exported
  components deben permanecer intactos salvo que una necesidad real obligue a
  detenerse y volver a MAIN.
- No registres rutas, contraseñas, nombres, horarios, notas, contenido, hashes de
  datos sensibles ni manifiestos.
- No uses red, cuentas, nube, telemetría, analítica ni datos reales.

## OUTPUT

Entregá directamente en el checkout compartido:

- modelos puros del formato, manifiesto, comparación, conflictos y plan final;
- exportador/importador lógico versionado y con límites;
- cifrado/descifrado JCA probado;
- repositorio Room de snapshot/validación/aplicación sin cambio de esquema;
- adaptadores semánticos de preferencias;
- coordinador atómico, journal y recuperación temprana;
- integración de fotos opcionales;
- flujo SAF y pantalla Compose;
- reconciliación posterior de Notificaciones y Widget;
- pruebas JVM e instrumentadas proporcionales;
- handoff completo a MAIN.

## SCOPE

Podés modificar únicamente lo necesario dentro de:

```text
core/domain/src/main/**/backup/**
core/domain/src/test/**/backup/**
core/database/src/main/**/backup/**
core/database/src/test/**/backup/**
core/database/src/androidTest/**/backup/**
app/src/main/**/backup/**
app/src/test/**/backup/**
app/src/androidTest/**/backup/**
```

Y, de forma acotada, los puntos de integración existentes:

```text
MiGuardiaApplication.kt
MainActivity.kt
MiGuardiaApp.kt
strings.xml
repositorios/DAO/stores existentes cuando necesiten lectura o escritura semántica
pruebas de navegación, recreación, avisos, Widget e integridad afectadas
```

Podés crear un DAO/repository dedicado de copia si no altera entidades ni
esquema. Si la implementación requiere tocar otro archivo, justificá primero que
es una frontera indispensable y mantené el cambio mínimo.

## DO NOT

No:

- modifiques entidades, versión Room, migraciones o esquemas 1–5;
- agregues dependencia de producción;
- copies archivos Room/DataStore crudos como formato público;
- reescribas UUID para forzar una combinación;
- crees múltiples perfiles o líneas temporales activas;
- guardes totales derivados de Horas/Resumen;
- cambies fórmulas laborales, sectores o vocabulario;
- agregues red, nube, cuenta, sincronización o backup automático;
- agregues copia/restauración por mes;
- exportes informes como si fueran backups;
- toques permisos, package, SDK, versión, backup del sistema o FileProvider;
- implementes bloqueo, Ayuda, pacientes o funciones futuras;
- abras producción o uses datos reales;
- modifiques documentación canónica;
- hagas staging, commit, push, tag, merge, rebase, reset o descarte.

## VALIDATION

### Dominio y formato

Probá como mínimo:

- serialización canónica determinista y round-trip;
- extensión, MIME y nombre sugerido exactos, sin datos personales;
- una fila/agregado representativo de cada una de las 27 tablas;
- preferencia portable incluida y estado de runtime excluido;
- copia con fotos y sin fotos;
- UTF-8, caracteres largos, fechas extremas, UUID y enums;
- tamaño/cantidad exactamente en el límite y una unidad por encima;
- entrada truncada, hash incorrecto, versión futura y campo obligatorio ausente;
- ruta absoluta, traversal, duplicado, symlink y expansión ZIP excesiva;
- MIME/firma de foto falsa y foto faltante/cambiada;
- resultado determinista sin depender del orden de lectura.

### Cifrado

Probá:

- contraseña correcta;
- contraseña incorrecta;
- contraseña vacía sólo en modalidad explícitamente sin cifrar;
- sal y nonce diferentes entre dos copias idénticas;
- AAD/encabezado alterado;
- ciphertext/tag truncado o modificado;
- parámetros versionados y rechazo seguro de parámetros inválidos;
- compatibilidad real de PBKDF2-HMAC-SHA256 y AES-256-GCM en API 26.

### Comparación y decisiones

Probá:

- destino vacío;
- misma `timelineId` con sólo nuevos;
- copia aplicada dos veces sin duplicaciones;
- identidad y contenido exactos;
- mismo UUID con contenido distinto;
- conflicto natural/relacional;
- identidades distintas con solapamiento permitido;
- `Conservar actual`, `Usar copia` y `Conservar ambos` válido;
- ausencia de `Conservar ambos` cuando no corresponde;
- otra `timelineId` sobre destino no vacío bloqueando combinación;
- conflictos pendientes impidiendo cualquier escritura;
- preferencias conocidas, desconocidas y avisos ocultados filtrados.

### Room, stores, fotos y atomicidad

Creá instrumentación que pruebe:

- exportar y restaurar las 27 tablas con claves y orden reales;
- `foreign_key_check`, `integrity_check`, validación V2 y reapertura;
- combinación idempotente;
- reemplazo total eliminando únicamente la unidad recuperable;
- fotos como par fila+bytes, sin huérfanos;
- UI/preview/consultas sin escrituras;
- captura concurrente que aborta o reintenta sin archivo mezclado;
- mutación concurrente bloqueada durante apply;
- rollback por fallo inyectado antes y después de cada fase del journal;
- muerte de proceso simulada en `PREPARED`, `SWAPPED` y `VERIFIED`;
- siguiente arranque dejando exactamente estado viejo o nuevo;
- falta de espacio antes de la primera mutación;
- DataStore y Room nunca visibles en versiones cruzadas;
- limpieza exclusiva del punto temporal después de `COMMITTED`;
- Informes privados limpios tras reemplazo y documentos externos intactos;
- Notificaciones y Widget reconciliados sin alerta invasiva.

La fixture integral debe contener, al menos, configuración y revisiones, catálogo,
jornada+snapshot, recurrencia+ocurrencia, horario real+extra, extra independiente,
disponibilidad, feriado, estado explícito, carpeta médica, vacaciones, nota, foto,
preferencias y avisos.

### Interfaz

Probá:

- navegación desde el menú y regreso al Calendario;
- creación con/sin fotos, con/sin contraseña;
- advertencias y confirmación de contraseña;
- cancelar selectores;
- contraseña equivocada y reintento;
- vista previa exacta y sin escrituras;
- combinación sin conflictos y con cada tipo de conflicto;
- reemplazo y segunda confirmación;
- doble toque, error, reintento y recreación;
- proceso recreado durante lectura y aplicación;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- TalkBack/semántica sin depender del color;
- selector SAF con proveedor real y URI `content://`.

### Batería local

Ejecutá serializado y con salida final real:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Obtené conteos reales desde XML y distinguí:

- `JVM VERIFICADO`;
- `LINT`;
- `COMPILADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA`;
- `PENDIENTE`.

Ejecutá además:

```powershell
git diff --check
```

Revisá el diff completo y buscá secretos, logs, red, migración destructiva,
consultas Room en main thread, escrituras fuera de la frontera y cambios fuera de
alcance.

### Room protegido

Room debe permanecer:

```text
Base: miguardia-v2.db
Versión: 5
Tablas: 27
identityHash: 77adbc875d0f4ee466cdbd0dd74d5c5c
```

Hashes protegidos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## PHYSICAL QA

No uses Samsung, emulador, ADB, instalaciones ni selectores reales sin una
autorización nueva y expresa de Joaquin después del handoff local. Compilar
AndroidTest no equivale a ejecutarlo.

Con autorización posterior, MAIN definirá la matriz. El candidato debe dejar
preparadas pruebas para:

- Samsung API 36 con proveedor SAF real y archivos ficticios;
- Android 8/API 26 para cifrado, SAF, recreación y recuperación;
- Android 13/API 33 para compatibilidad y permisos vigentes;
- reemplazo de paquete, muerte de proceso y reapertura;
- copias chicas y con fotos suficientes para probar streaming;
- no usar datos reales ni producción.

No dispares alarmas exactas ni reinicies físicamente el Samsung: continúan como
puertas independientes.

## DEVICE SAFETY

Si más adelante recibís autorización:

- identificá serial/modelo/API antes de actuar;
- usá sólo paquetes QA y test;
- nunca abras, limpies, reemplaces ni desinstales producción;
- usá datos, nombres, fotos y contraseñas ficticios;
- no consultes ni modifiques `font_scale`, densidad o tamaño visual;
- registrá paquetes instalados y retiralos al finalizar salvo orden contraria;
- restaurá orientación si la prueba la modifica;
- informá exactamente qué quedó en el dispositivo.

## STOP CONDITIONS

Detenete y devolvé `MAIN BLOQUEADA` si aparece:

- contradicción entre fuentes activas;
- necesidad de combinar dos `timelineId` no vacíos;
- necesidad de copiar archivos internos crudos como formato público;
- imposibilidad de garantizar estado viejo-o-nuevo tras muerte de proceso;
- necesidad de cambiar Room, esquema, migración, permisos o FileProvider;
- necesidad de dependencia de producción o servicio externo;
- tamaño/límite de seguridad material sin fundamento verificable;
- checkout sucio de origen desconocido;
- validación roja no corregible dentro del alcance;
- acción destructiva, dispositivo sin autorización, push, tag, Release, `main`
  o producción.

No inventes una conciliación ni rebajes la atomicidad en silencio.

## HANDOFF A MAIN

Entregá un handoff autosuficiente con estas secciones exactas:

```text
# HANDOFF A MAIN — Copias y restauración locales seguras V2

## QUÉ HACE
## POR QUÉ EXISTE
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## FORMAT AND SECURITY
## RESTORE SEMANTICS
## VALIDATION
## ROOM
## PHYSICAL QA
## DEVICE SAFETY
## RISKS
## PENDING
## GIT
## NEXT
```

Incluí:

- resultado funcional real;
- archivos modificados, nuevos y eliminados;
- versión exacta del contenedor y límites adoptados;
- algoritmos y parámetros sin exponer contraseñas;
- datos incluidos y excluidos;
- decisiones de combinación y reemplazo;
- evidencia de idempotencia, rollback y recuperación;
- conteos JVM reales;
- separación entre AndroidTest compilado y ejecutado;
- estado de Room y hashes;
- estado exacto Git y del dispositivo;
- riesgos y pendientes honestos.

Dejá el candidato directamente en el checkout compartido, sin staged, commit o
push. No hay nada para `cherry-pick`.

## DONE WHEN

El candidato local está listo para volver a MAIN sólo cuando:

- crea una copia completa, lógica, canónica y versionada;
- fotos son opcionales pero atómicas con sus filas;
- contraseña protege realmente con PBKDF2-HMAC-SHA256 + AES-256-GCM;
- una copia sin contraseña muestra una advertencia consciente;
- la vista previa valida sin escribir;
- `Combinar` es conservador, idempotente y resuelve todos los conflictos;
- líneas temporales distintas no se mezclan silenciosamente;
- `Reemplazar todo` muestra pérdidas y exige segunda confirmación;
- cualquier fallo deja exactamente estado anterior o nuevo;
- recuperación ocurre antes de runtimes y observadores;
- Room V5, 27 tablas y esquemas permanecen intactos;
- permisos, FileProvider, Gradle, dependencias y SDK permanecen intactos;
- selector, cancelación, recreación, error y reintento funcionan;
- la batería local queda verde;
- instrumentación/QA física está ejecutada o marcada honestamente `PENDIENTE`;
- no hubo commit, push ni dispositivo sin autorización.

MAIN sólo cierra la dependencia después de auditar el diff, repetir pruebas
proporcionales y ejecutar la QA Android/física que Joaquin autorice.

## PRIMERA RESPUESTA ESPERADA

Antes de implementar, respondé brevemente:

1. resultado de Puerta 0;
2. HEAD exacto recibido;
3. confirmación de que leíste las fuentes obligatorias;
4. mapa de datos portables y excluidos;
5. diseño propuesto de contenedor, cifrado, comparación, journal y recuperación;
6. archivos que prevés tocar;
7. pruebas previstas;
8. confirmación de que no usarás dispositivos, commit ni push sin autorización.

No empieces a editar si esa respuesta revela una contradicción o una decisión
material todavía ausente.
