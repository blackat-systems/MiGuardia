# Carga manual de jornadas V2

- Estado: **CERRADO — INTEGRADO POR MAIN**
- Fecha: 2026-08-22
- Rama obligatoria: `codex/miguardia-2.0`
- Proyecto obligatorio: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Base funcional cerrada: `1f048643ba70882576295e4683729a35a9584312`
- HEAD de entrada: el commit documental exacto que MAIN informe al abrir la tarea
- Nombre humano: **Elegir días y cargar jornadas desde horarios guardados**

> Actualización 2026-08-23: la carga manual V2 integrada continúa vigente. ADR
> 0024 reemplaza únicamente las condiciones especiales y las regresiones de
> una raíz `MIGRATED_V1`; su retiro pertenece a un bloque posterior.

## ROL

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, Room, el dominio laboral, el Calendario ni el orden de
la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama o worktree. MAIN conservará la integración final.

Antes de modificar:

1. ejecutá Puerta 0 de solo lectura;
2. leé completamente y en el orden obligatorio de `AGENTS.md` las fuentes
   rectoras y este prompt;
3. confirmá ruta, rama, HEAD exacto informado por MAIN, base `v1.0.0^{}`,
   limpieza, worktrees, remoto privado, autor Git, entorno Android y Samsung;
4. inspeccioná el código y las pruebas reales de Calendario, edición mensual,
   `ManagementViewModel`, configuración laboral, catálogo y jornadas V2;
5. detenete ante un mismatch real y no descartes ningún cambio.

## TASK

Construir el primer recorrido utilizable para cargar jornadas V2 de manera
manual:

1. desde un Calendario con configuración `V2Ready`, mostrar una acción clara
   `Cargar jornadas`;
2. usar la única grilla mensual existente para elegir uno o varios días del
   mismo mes;
3. confirmar que se terminó de elegir días;
4. mostrar los lugares, tipos y horarios activos que sirven para esa selección;
5. elegir una plantilla y revisar un resumen exacto antes de guardar;
6. resolver la configuración laboral que corresponde a cada fecha;
7. guardar cada `Shift` junto con su `ShiftWorkSnapshot` mediante la transacción
   V2 existente;
8. volver al Calendario y mostrar inmediatamente las jornadas creadas.

Este incremento **crea jornadas nuevas**. No implementa todavía la edición
estructural de una jornada existente, recurrencias ni carga de horario real.

## EXPERIENCIA ESPERADA

### 1. Punto de entrada

La acción principal en un Calendario `V2Ready` se llama `Cargar jornadas`. La
acción `Mi forma de trabajar` continúa visible como acceso secundario.

Un estado `V2NeedsFirstSet` conserva la guía para crear el primer lugar y no
ofrece una carga que todavía no puede completarse. Un estado V1 conserva el
recorrido heredado y no ve formularios V2.

La carga V2 no depende de `hasAnyShifts`: una persona puede tener historia V1,
ninguna jornada o varias jornadas y continuar usando la misma configuración
V2.

### 2. Una sola grilla para elegir fechas

Al tocar `Cargar jornadas`, el Calendario entra en su modo explícito de
selección. No se abre otro Calendario ni un selector de fechas separado.

- se puede elegir una o varias fechas del mes visible;
- la selección vacía no permite continuar;
- la acción para avanzar usa un texto cotidiano como `Terminar de elegir días`;
- cambiar de mes con una selección conserva la confirmación de descarte ya
  existente;
- Atrás o `Salir` no escribe nada;
- la selección y el paso alcanzado sobreviven a recreación mediante
  `SavedStateHandle`;
- en modo V2 no aparecen las herramientas estructurales V1 de objetivos,
  horarios, francos o días sin definir.

No reutilices `CalendarEditTools` completo ni `ShiftDraft`, porque ambos
representan el recorrido y las `ScheduleCombination` de V1. La superficie V2
muestra únicamente sus herramientas de carga y conserva un borrador propio con
fechas, plantilla, puesto, política y etapa de confirmación.

La grilla sigue siendo consultable fuera del modo de carga. No conviertas un
toque de consulta en una escritura.

### 3. Clasificación por vigencia

Usar `classifyWorkDateSelection(...)` sobre la historia persistida:

- `WorkDateSelection.V2`: continuar;
- `WorkDateSelection.NeedsNewV2Backfill`: pedir la confirmación consciente
  descrita más abajo;
- `WorkDateSelection.LegacyV1`: explicar que esas fechas pertenecen al
  recorrido heredado y no escribirlas como V2;
- una selección que mezcle V1/V2 o sectores distintos muestra el mensaje de
  dominio y exige operaciones separadas.

La configuración se resuelve **por cada fecha seleccionada** con
`ResolvedWorkConfigurationRevision.resolve(...)`. No uses una sola revisión
para todo el mes ni la revisión vigente hoy para días históricos o futuros.

Si las fechas usan revisiones V2 diferentes pero conservan la misma línea
temporal, sector y plantilla válida, la operación puede continuar. Cada
jornada guarda el identificador de la revisión que realmente le corresponde.

### 4. Retrocarga consciente de una instalación nueva

Si una raíz `NEW_V2` incluye fechas anteriores a su primera revisión, mostrar
una confirmación única y clara antes de escribir, por ejemplo:

`MiGuardia empezó a usar esta forma de trabajar desde [fecha]. ¿Querés usar la
misma configuración desde [fecha más antigua elegida]?`

Antes de confirmar no se extiende configuración, no se crean jornadas y esos
días no se tratan como V1.

Al confirmar:

- copiar exactamente el valor de la primera revisión `NEW_V2` desde la fecha
  más antigua elegida, con un UUID nuevo;
- copiar desde esa misma fecha las reglas necesarias del lugar de la plantilla
  elegida, también con UUID nuevos;
- usar `NewV2Backfill`, `WorkplaceRuleBackfill` y
  `WorkCatalogRepository.extendNewV2Backward(...)`;
- no inventar otro sector, referencia de horas, nocturnidad ni reglas;
- releer historia y catálogo antes de preparar las jornadas;
- hacer el recorrido reintentable e idempotente si la extensión ya quedó
  guardada pero la carga posterior falló.

La extensión de configuración y reglas usa la transacción pública existente.
No agregues DAO, tabla ni migración para unirla artificialmente con el lote de
jornadas.

Una raíz `MIGRATED_V1` nunca usa esta retrocarga. Tampoco se activa V2 desde
este recorrido.

### 5. Elección de lugar, tipo y horario

Después de confirmar fechas, mostrar sólo combinaciones coherentes y activas:

- `WorkPlace.isActive`;
- `WorkType.isActive`;
- `WorkTemplate.isActive`;
- misma línea temporal y sector;
- regla del lugar aplicable a cada fecha elegida.

Cada opción muestra siempre:

- nombre corto del lugar;
- nombre del tipo de trabajo;
- inicio y final exactos;
- color.

No ocultes el tipo aunque dos opciones tengan el mismo lugar y horario. El
nombre visible no decide si la jornada es extra. Todas las plantillas de este
incremento representan trabajo normal `ACTIVE_WORK`.

Se pueden priorizar las plantillas recientes V2 mediante
`observeRecentlyUsed(...)`, con un máximo de cinco, pero la lista completa
activa debe seguir alcanzable. No mezcles recientes V1 ni infieras una
plantilla desde una `ScheduleCombination`.

### 6. Vista previa y guardado

La revisión final muestra:

- cantidad y fechas exactas;
- lugar;
- tipo de trabajo;
- horario exacto y aclaración si termina al día siguiente;
- puesto o función opcional, si se conserva el campo heredado;
- qué ocurrirá con fechas ya ocupadas, cuando corresponda.

Construir un candidato por fecha con `buildV2ShiftWrite(...)`, reloj, zona y
UUID inyectables. Para cada candidato:

- `Shift.sourceObjectiveId = WorkPlace.objectiveId`;
- `ShiftWorkSnapshot.workPlaceId = WorkPlace.id`;
- esos dos UUID no se intercambian;
- `sourceScheduleCombinationId` sólo conserva el ID cuando la plantilla fue
  adoptada conscientemente desde V1; en otro caso es `null`;
- las fotografías de lugar, abreviatura, dirección, horario, color, tipo,
  comportamiento, sector, plantilla y revisión coinciden con la selección;
- el estado inicial es `PLANNED`;
- inicio igual a final representa 24 horas;
- un cruce de medianoche termina al día siguiente y conserva como día propio el
  día de inicio.

Preparar el lote con `planV2ShiftBatch(...)` y persistirlo únicamente mediante
`V2ShiftRepository.applyV2Batch(...)`. No usar `ShiftRepository.applyBatch(...)`
como atajo V1 ni escribir `Shift` y `ShiftWorkSnapshot` por separado.

Al completar, cerrar el formulario, limpiar el borrador confirmado y volver al
Calendario con un mensaje de éxito. La observación existente de `Shift` debe
actualizar la grilla; no agregues una segunda fuente de jornadas.

### 7. Fechas ocupadas

Conservar las decisiones heredadas:

Para una sola fecha ocupada:

- `Reemplazar`;
- `Agregar segunda jornada`;
- `Cancelar`.

Para varias fechas con algunas ocupadas:

- `Reemplazar en las fechas elegidas`;
- `Agregar sólo en días libres`;
- `Agregar segunda jornada en las ocupadas`;
- `Cancelar`.

Las políticas corresponden a `OccupiedDatePolicy.REPLACE`, `KEEP_OCCUPIED`,
`ADD_SECOND_SHIFT` y `CANCEL`. Una sustitución confirmada puede borrar una
jornada V1 o V2 de esas fechas; la transacción existente debe borrar también la
fotografía dependiente cuando exista. No convertir las jornadas V1 que se
conservan.

### 8. Advertencias y estados explícitos

Después de elegir la política de ocupadas, conservar las advertencias de:

- más de una jornada en el mismo día;
- superposición horaria;
- descanso menor a 12 horas;
- coexistencias heredadas que la carga no modifica, como una carpeta médica.

Las advertencias no bloquean para siempre: exigen una segunda confirmación
consciente. Volver permite corregir sin perder fechas, plantilla ni política.

Cuando realmente se guarda una jornada en una fecha con `F` o `?`, incluir esa
fecha en `explicitDayStatusDatesToClear`. No limpiar estados de fechas omitidas,
canceladas o fallidas.

### 9. Errores y recreación

- una falla de lectura muestra `Reintentar` y no una lista vacía;
- una falla al guardar conserva fechas, plantilla, puesto, política y
  confirmaciones todavía necesarias;
- durante una escritura se bloquea el doble toque;
- si catálogo, configuración o jornadas cambian antes de confirmar, releer y
  recalcular en vez de usar una vista previa vencida;
- no registrar en logs notas, nombres de lugares, fechas ni horarios reales;
- los borradores no confirmados sobreviven a recreación de actividad y proceso
  cuando `SavedStateHandle` lo permite;
- un borrador incompatible con el estado persistido actual se descarta de
  manera explicada, nunca se guarda a ciegas.

## CONTRATOS CERRADOS A REUTILIZAR

- `WorkSetupState.V2Ready` y `projectLoadedWorkSetupState(...)`;
- `WorkConfigurationRepository`;
- `WorkCatalogRepository`;
- `ObjectiveRepository` para resolver el `Objective` físico del lugar;
- `ShiftRepository`, `ExplicitDayStatusRepository` y
  `MedicalLeaveRepository` únicamente para observación y advertencias
  heredadas;
- `V2ShiftRepository`;
- `classifyWorkDateSelection(...)`;
- `ResolvedWorkConfigurationRevision.resolve(...)`;
- `NewV2Backfill` y `WorkplaceRuleBackfill`;
- `buildV2ShiftWrite(...)`;
- `planV2ShiftBatch(...)`;
- `V2ShiftBatchMutation` y `applyV2Batch(...)`;
- `CalendarViewModel` y su selección mensual persistida;
- `ManagementViewModel` y los diálogos heredados de ocupación/advertencias;
- `LocalDataStore.workConfiguration`, `workCatalog`, `objectives`, `shifts`,
  `explicitDayStatuses`, `medicalLeaves` y `v2Shifts`;
- tema Vigilia, zoom interno y patrones visuales existentes.

Los repositorios Room vuelven a validar dentro de sus transacciones. No
dupliques esas reglas en Compose ni accedas directamente a DAO.

## IMPLEMENTACIÓN PERMITIDA

Se permite modificar solamente:

- `app/src/main/**` para el estado, coordinador, pantallas y cableado mínimo de
  la carga V2;
- `app/src/test/**`;
- `app/src/androidTest/**`;
- textos y test tags estrictamente necesarios.

### Ajuste de integración autorizado por MAIN

La auditoría posterior al handoff detectó una ventana entre la revisión y el
guardado: otra escritura local podía cambiar las jornadas ocupadas antes de que
el lote entrara en su transacción. Joaquin autorizó la recomendación de MAIN
para cerrar exclusivamente ese riesgo.

Esta autorización posterior reemplaza sólo la prohibición original de tocar
`core` y DAO en los siguientes puntos acotados:

- una expectativa inmutable de ocupación en `core/domain`, separada del lote a
  mutar y obligatoria en `V2ShiftRepository.applyV2Batch(...)`;
- una lectura suspendida en `ShiftDao` equivalente a la observación existente;
- comparación de esa expectativa dentro de la misma transacción de
  `RoomV2ShiftRepository`, antes de borrar, insertar, actualizar o limpiar
  `F/?`;
- pruebas puras y Room específicas de este contrato.

El coordinador captura la ocupación de la ventana necesaria para evaluar las
fechas elegidas y el descanso vecino. Si cambió, conserva el borrador pero
descarta la revisión y sus confirmaciones, de modo que la persona deba revisar
otra vez. No se agregó tabla, columna, entidad, índice, migración ni cambio de
versión de Room.

Preferí extender el recorrido de `ui/management/` y la grilla existentes. Si
separar un coordinador V2 mejora las pruebas, mantenelo dentro de esa frontera y
no dupliques la navegación ni el Calendario.

El cableado mínimo puede alcanzar `MainActivity.kt`, `MiGuardiaApp.kt`,
`ManagementUiState.kt`, `ManagementViewModel.kt`, `ManagementScreens.kt`,
`CalendarViewModel.kt`, recursos y sus pruebas. No cambies código fuera de esa
lista por comodidad. `WorkSetup` continúa como fuente observada del estado raíz;
no dupliques ni traslades su responsabilidad al coordinador de carga.

## DO NOT

- no asumir el rol de MAIN;
- no crear otro proyecto, rama o worktree;
- fuera del ajuste de integración autorizado y documentado arriba, no modificar
  `core/domain`, `core/database`, Room v7, entidades, DAO, esquemas o
  migraciones;
- no cambiar DataStore, Gradle, manifiesto, permisos, `applicationId`, versión
  o SDK;
- no implementar edición estructural ni eliminación individual de jornadas
  existentes fuera de los reemplazos explícitos del propio lote;
- no implementar recurrencias ni planes futuros;
- no implementar horario real, extras, cumplimiento, disponibilidad, guardia
  pasiva ni situaciones especiales;
- no implementar activación V2 desde una raíz migrada ni cambios de sector;
- no ampliar el catálogo, crear tipos adicionales, archivar/reactivar ni
  editar reglas o plantillas;
- no adaptar Resumen V2, próximo evento, notificaciones, clima, widgets,
  informes o copias;
- no exponer las herramientas estructurales V1 dentro del modo V2;
- no mostrar en el detalle V2 las acciones heredadas `Editar`, `Eliminar`,
  `Agregar otra` o Novedades; el reemplazo sólo existe dentro del lote V2;
- no inferir tipos para jornadas V1 ni convertirlas silenciosamente;
- no crear `Salud`, `Otro` ni una quinta opción laboral;
- no agrupar Enfermería y Medicina;
- no imponer 204 horas, nocturnidad, horario o reglas por profesión;
- no mostrar montos, salarios, convenios ni liquidaciones;
- no agregar dependencias de producción, cuentas, red, nube, ubicación, OCR,
  telemetría ni datos clínicos;
- no usar datos reales;
- no modificar `docs/STATUS.md`, `docs/prompts/README.md`, ADR, auditorías ni
  este prompt; MAIN es dueño del estado durable y de la auditoría;
- no tocar la aplicación productiva del Samsung;
- no hacer commit, push, tag, merge, rebase, reset ni descartar cambios.

## VALIDATION

### JVM

Agregar pruebas de coordinador o estado para cubrir como mínimo:

1. `V2Ready` habilita la carga y `V2NeedsFirstSet` no;
2. una o varias fechas del mismo mes conservan la única grilla;
3. selección vacía o de varios meses no escribe;
4. fechas V1, mezcla V1/V2 o mezcla de sectores se rechazan sin escritura;
5. revisiones V2 distintas del mismo sector se resuelven por fecha;
6. sólo se ofrecen lugar, tipo y plantilla activos con regla aplicable;
7. dos tipos con igual lugar y horario siguen distinguiéndose;
8. retrocarga `NEW_V2` no escribe antes de confirmar y copia exactamente
   configuración y reglas necesarias;
9. cancelar la retrocarga conserva el borrador y no muta datos;
10. reintentar después de una extensión ya persistida es seguro;
11. cada candidato usa objetivo, lugar, plantilla, tipo y revisión correctos;
12. las cuatro políticas de fechas ocupadas producen el lote esperado;
13. reemplazo, segunda jornada y omisión siguen siendo atómicos;
14. advertencias requieren confirmación y volver conserva el borrador;
15. `F` y `?` se limpian sólo en fechas efectivamente guardadas;
16. una falla o doble toque no deja una jornada sin fotografía;
17. un `ManagementViewModel` nuevo, reconstruido con `SavedStateHandle`,
    recupera el borrador no confirmado sin repetir eventos;
18. el recorrido V1 no cambia y una jornada V1 no gana fotografía V2.

### Compose e instrumentación

Con datos ficticios:

1. `Cargar jornadas` aparece en `V2Ready` y no reemplaza `Mi forma de trabajar`;
2. se eligen uno o varios días directamente en la grilla existente;
3. `Terminar de elegir días` permanece inactivo sin selección;
4. el selector muestra nombre corto, tipo, horario y color;
5. el resumen muestra fechas exactas y cruce de medianoche;
6. ocupadas ofrecen las decisiones correctas para una o varias fechas;
7. superposición y descanso corto permiten volver o confirmar;
8. la retrocarga pide confirmación y cancelar no escribe;
9. éxito vuelve al Calendario y las jornadas quedan visibles;
10. error conserva el borrador y permite reintentar;
11. recreación conserva selección y formulario;
12. V1 no muestra carga V2 ni pierde su recorrido heredado;
13. un editor V1 residual sigue cerrándose al entrar en V2, pero el nuevo
    estado de carga V2 no se cierra por ese efecto;
14. claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % dejan
    todas las acciones alcanzables;
15. selección, color, errores y progreso no dependen sólo del color.

Ejecutar al menos:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Compilar la instrumentación afectada. La ejecución física puede usar únicamente
el paquete QA, el Samsung autorizado y datos ficticios. Desinstalar QA al
finalizar y confirmar que producción no se abrió ni modificó. Distinguir
siempre compilación, instrumentación ejecutada y recorrido manual.

No hace falta repetir pruebas instrumentadas de Room si sus archivos y el
esquema permanecen byte a byte sin cambios; MAIN lo comprobará durante la
integración. Sí debe ejecutarse la regresión V1/V2 de aplicación afectada. Si
la superficie mínima API 26 no está disponible en esta dependencia, registrarla
como pendiente explícito para la integración de MAIN, sin simular evidencia.

## HANDOFF A MAIN

Devolver un informe compacto con:

- objetivo realizado;
- archivos modificados y no rastreados;
- decisiones menores tomadas;
- pruebas exactas, conteos y dispositivo usado;
- qué fue compilado, ejecutado y recorrido manualmente;
- evidencia de que Room v7, dominio, Gradle, manifiesto, permisos, versión y
  producción quedaron sin cambios;
- riesgos o puntos de integración;
- estado Git y confirmación de que no hubo commit ni push;
- próximo paso que queda exclusivamente para MAIN.

La entrega queda directamente en el checkout compartido. No declares integrado
el incremento ni terminado el bloque de recurrencias y edición. MAIN auditará
el diff, repetirá las pruebas proporcionales y decidirá el checkpoint.

## DONE WHEN

La dependencia está lista para entregar a MAIN solamente cuando:

- una persona V2 lista puede elegir uno o varios días en la grilla existente;
- puede elegir un lugar, tipo y horario activo sin ambigüedad;
- cada fecha usa su revisión laboral exacta;
- la retrocarga `NEW_V2` es consciente, segura y reintentable;
- ocupadas, segunda jornada, solapamiento y descanso conservan confirmaciones;
- `Shift` y `ShiftWorkSnapshot` se guardan juntos de forma atómica;
- V1, Room v7 y la configuración ya integrada no se degradan;
- errores y recreación conservan el trabajo no confirmado;
- las pruebas proporcionales pasan;
- el diff queda sin commit y sin push para auditoría de MAIN.

## CIERRE DE MAIN — 2026-08-23

MAIN auditó el candidato, aplicó el ajuste transaccional autorizado, corrigió
los bordes de carga/error, recreación, éxito consumible, semántica y varias
jornadas por día, y ejecutó una auditoría independiente de sólo lectura. No se
encontraron defectos bloqueantes en el diff final.

Verificación final:

- JVM: 317/317 —219 de dominio, 5 de base de datos y 93 de aplicación—;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias heredadas;
- APK Debug y APK de AndroidTest QA: compilados;
- Samsung `SM-S938B`, API 36: 60/60 pruebas Compose y de actividad afectadas,
  14/14 pruebas Room V2 y 1/1 recorrido integral separado `MainActivity +
  Room` para una raíz `NEW_V2` limpia;
- recorrido físico con datos ficticios: carga de una y varias fechas,
  retrocarga, ocupadas, segunda jornada, superposición, recreación, rotación,
  reapertura y zoom interno;
- Room continúa en versión 7 y `7.json` conserva el SHA-256
  `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`;
- Gradle, manifiesto, permisos, `applicationId`, versión, SDK y producción no
  cambiaron;
- QA fue desinstalada al terminar; no hubo push.

La adaptación de próximo evento y notificaciones conserva por ahora el motor
V1, de acuerdo con el índice canónico, y sigue reservada para su bloque futuro.
API 26 física permanece pendiente porque el único dispositivo disponible fue
el Samsung API 36.
