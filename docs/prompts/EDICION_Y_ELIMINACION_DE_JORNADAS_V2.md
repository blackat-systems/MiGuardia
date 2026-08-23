# Edición y eliminación individual de jornadas V2

- Estado: **HABILITADO — PENDIENTE DE IMPLEMENTACIÓN**
- Fecha: 2026-08-23
- Rama obligatoria: `codex/miguardia-2.0`
- Proyecto obligatorio: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Base funcional cerrada: `ae57686`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir la tarea
- Nombre humano: **Corregir o eliminar una jornada cargada**

## ROL

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, el Calendario, el modelo laboral, Room ni el orden de la
hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama o worktree. MAIN conserva la integración, la documentación
canónica y los checkpoints.

Antes de modificar:

1. ejecutá Puerta 0 de solo lectura;
2. leé completamente y en el orden obligatorio de `AGENTS.md` las fuentes
   rectoras y este prompt;
3. confirmá ruta, rama, HEAD exacto informado por MAIN, `v1.0.0^{}`, limpieza,
   worktrees, remoto privado y autor Git;
4. comprobá el entorno Android y el Samsung sólo cuando comience la validación
   física;
5. inspeccioná completos el código y las pruebas afectados;
6. detenete ante un mismatch real o cambios de origen desconocido y no
   descartes ningún trabajo.

## TASK

Implementar exclusivamente la edición y la eliminación de **una jornada V2 ya
cargada** desde el Calendario:

1. tocar un día continúa abriendo su detalle sin escribir datos;
2. una acción consciente `Editar este día` habilita las acciones de sus
   jornadas V2;
3. si el día posee varias jornadas, cada una conserva acciones inequívocas;
4. `Editar jornada` permite elegir otra plantilla guardada —lugar, tipo,
   intervalo y color— o corregir el puesto o función opcional;
5. la fecha permanece fija durante todo este incremento;
6. antes de guardar se muestra un resumen exacto y las advertencias aplicables;
7. `Eliminar jornada` exige una confirmación específica y elimina solamente la
   jornada elegida;
8. guardar o eliminar vuelve al Calendario y muestra inmediatamente el
   resultado persistido.

Este incremento no mueve jornadas, no edita varias fechas, no crea
recurrencias y no implementa edición de una serie.

## CONTEXT

La frontera funcional integrada es `ae57686`:

- la primera apertura permite elegir exactamente Vigilancia privada, Policía,
  Enfermería o Medicina;
- la configuración laboral, el catálogo y Room v7 ya están integrados;
- una persona `V2Ready` puede cargar una o varias jornadas desde la única
  grilla mensual;
- cada jornada V2 se guarda como un par obligatorio `Shift +
  ShiftWorkSnapshot`;
- la carga manual ya confirma fechas ocupadas, segunda jornada,
  superposición, descanso corto y carpeta médica;
- los commits posteriores a `ae57686` son documentales.

El código ya posee contratos útiles:

- `editV2ShiftWrite(...)`;
- `planV2ShiftBatch(..., editingShiftId = ...)`;
- `V2ShiftRepository.applyV2Batch(...)`;
- `ShiftOccupancyExpectation`;
- actualización y eliminación transaccionales del par V2;
- cascada `Shift -> ShiftWorkSnapshot`.

La interfaz V2 oculta hoy la edición y eliminación porque el prompt anterior
las excluía expresamente. No las habilites reutilizando las escrituras V1.

MiGuardia 1.0 es únicamente la base de código. No existe activación, adopción
ni migración de datos V1 hacia V2. La deuda técnica heredada no bloquea este
incremento porque no se amplía el esquema de persistencia.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md` como contrato cerrado;
- ADR 0020, 0022, 0023 y 0024;
- `docs/audits/2026-08-23-carga-manual-de-jornadas-v2.md`;
- las fichas de los cuatro sectores para conservar vocabulario neutral;
- `docs/PROMPT_MAESTRO_MAIN.md` únicamente como contrato histórico V1;
- `Shift`, `ShiftWorkSnapshot`, `V2ShiftWrite` y `V2ShiftBatchMutation`;
- `V2ShiftPlanning.kt` y sus pruebas;
- `V2ShiftRepository`, `RoomV2ShiftRepository`, `V2ShiftDao` y sus pruebas;
- Calendario, detalle del día, carga manual V2, navegación, recreación y sus
  pruebas de aplicación.

No recuperes código desde worktrees históricos. Si una fuente histórica
contradice este prompt o las fuentes activas de V2, prevalece la jerarquía de
`AGENTS.md`.

## DECISIONES FUNCIONALES CONGELADAS

### 1. Una jornada exacta

- Este recorrido sólo opera sobre una jornada que posea su
  `ShiftWorkSnapshot` V2.
- Una fila sin fotografía V2 no gana acciones V2 ni se convierte
  silenciosamente.
- Si el día tiene dos o más jornadas, el lugar, tipo y horario deben dejar
  claro cuál se está editando o eliminando.
- Editar o eliminar una nunca modifica las demás.

### 2. Consulta separada de edición

- Tocar un día sigue siendo una consulta sin escrituras.
- `Editar este día` es la frontera consciente que habilita cambios.
- Entrar, volver, cancelar o cerrar el detalle no escribe nada.
- En modo V2 se usa la palabra `jornada`; no se fuerza el vocabulario
  `guardia` sobre Policía, Enfermería o Medicina.

### 3. Fecha fija

- El editor muestra la fecha completa, pero no permite cambiarla.
- Para corregir una fecha equivocada, la persona elimina esa jornada y usa el
  recorrido ya existente `Cargar jornadas` en la fecha correcta.
- Mover una jornada, seleccionar varios días y editar una serie pertenecen a
  bloques posteriores.

### 4. Datos editables

Se puede cambiar:

- la plantilla activa, que representa lugar, tipo, inicio, fin y color;
- el puesto o función opcional.

Se conservan siempre:

- UUID de la jornada;
- fecha local de inicio;
- zona horaria;
- `createdAt`;
- estado persistido;
- identidad de cualquier jornada compañera.

`updatedAt` se actualiza mediante el reloj inyectable y debe quedar estrictamente
después del valor original a la precisión que persiste Room. Si el reloj entrega
el mismo instante o uno anterior, la versión nueva debe usar el incremento mínimo
representable; nunca puede quedar indistinguible de la versión confirmada.

### 5. Selección histórica o nueva

- Si la persona conserva la misma selección y sólo cambia el puesto, deben
  conservarse exactamente la fotografía y la revisión histórica originales,
  aunque la plantilla haya sido archivada después.
- Esa corrección acotada puede atravesar la validación transaccional de fuentes
  activas únicamente cuando, comparado con el par almacenado, sólo cambian
  `position` normalizado y `updatedAt`. Fecha, instantes, fotografías, fuentes,
  UUID, zona, creación y estado deben coincidir exactamente.
- En este contrato, conservar la fotografía histórica significa conservar el par
  `V2ShiftWrite` completo salvo `shift.position` normalizado y el nuevo
  `shift.updatedAt`; no alcanza con comparar sólo `ShiftWorkSnapshot`.
- Una plantilla histórica no se reactiva ni se ofrece como opción nueva por
  accidente.
- Si la persona elige otra plantilla, ésta debe estar activa, pertenecer a la
  misma línea temporal y sector, y poseer lugar, tipo, objetivo y reglas
  aplicables a la fecha fija.
- Nunca elegir automáticamente una alternativa si la selección original dejó
  de estar disponible.

### 6. Advertencias

La edición relee la ocupación actual y conserva las advertencias vigentes:

- segunda jornada en el mismo día;
- superposición;
- descanso menor a 12 horas;
- carpeta médica coincidente.

La expectativa de ocupación cubre la misma ventana usada por la carga manual:
dos días antes y dos días después de la fecha editada, incluso al cruzar de mes
o de año. Un cambio vecino que pueda alterar superposición o descanso invalida
la revisión.

Las demás jornadas siempre se conservan. Este editor no ofrece reemplazarlas ni
eliminarlas como política de fecha ocupada. Las advertencias permiten volver o
confirmar conscientemente.

### 7. Persistencia de la edición

- La edición valida y persiste `Shift` y `ShiftWorkSnapshot` como un par atómico.
- Si cambia la plantilla, la nueva fotografía refleja exactamente la selección
  confirmada y la configuración correspondiente a esa fecha.
- La edición visible usa `applyV2Batch(...)` con
  `OccupiedDatePolicy.ADD_SECOND_SHIFT`, `editingShiftId` exacto,
  `shiftIdsToDelete` vacío y una expectativa de ocupación recién leída. No llama
  al escritor V1, no actualiza sólo `Shift` y no relaja la invariante que rechaza
  un lote compuesto únicamente por borrados.
- Dentro de la misma transacción se comparan tanto la expectativa completa como
  el `V2ShiftWrite` original confirmado. Un cambio de puesto, fotografía,
  intervalo, estado o cualquier otro campo vuelve obsoleta la revisión aunque
  ocurra en el mismo milisegundo, y no se escribe nada.
- La validación especial para editar sólo puesto debe estar cerrada a la igualdad
  exacta definida arriba; no habilita fuentes archivadas para ninguna carga o
  cambio de plantilla.
- No se limpian ni crean estados explícitos `F/?` durante una edición.
- Un fallo deja el par original completo.

### 8. Eliminación consciente

- `Eliminar jornada` abre una confirmación con fecha, lugar, tipo y horario.
- Cancelar conserva todo sin cambios.
- Confirmar usa una operación de borrado V2 propia, separada de
  `applyV2Batch(...)`, y elimina la jornada exacta sólo si todavía coincide con
  el par completo confirmado.
- Se conserva la semántica transaccional vigente: desaparecen el `Shift`
  objetivo, su `ShiftWorkSnapshot`, sus notas, sus novedades propias, su cambio
  formal y su configuración y recordatorios de notificación. También desaparece
  únicamente la fila de novedad de otra jornada cuyo `relatedShiftId` apuntaba a
  la eliminada; la otra jornada y todos sus demás datos permanecen.
- No elimina lugares, tipos, plantillas, reglas, configuración, otras jornadas,
  notas o novedades no relacionadas, feriados, vacaciones ni carpetas médicas.
- No crea ni restaura automáticamente `F` o `?` si el día queda vacío.
- La eliminación debe comparar dentro de la transacción la versión que la
  persona confirmó. Si la jornada cambió o desapareció, no elimina nada y pide
  revisar nuevamente.

Para cerrar esas precondiciones se autoriza una ampliación mínima del contrato
`V2ShiftRepository` y de su implementación Room: comparación del par original
para la edición, validación histórica exacta de sólo puesto y borrado con
compare-and-set del par completo. No se autoriza relajar `applyV2Batch(...)`,
cambiar entidades ni modificar el esquema.

### 9. Estado, recreación y concurrencia

- El estado de edición es exclusivo de V2 y no reutiliza `ShiftDraft`.
- `SavedStateHandle` conserva la fecha del día, la etapa previa a elegir una
  jornada, UUID objetivo, selección, puesto y etapa no confirmada.
- Al recrear, se releen jornada, fotografía, configuración, catálogo,
  ocupación y advertencias; una expectativa vieja nunca se restaura como
  válida.
- Si cambió la jornada o dejó de ser V2, se invalida la revisión sin mutar.
- `V2NeedsFirstSet` nunca monta ni restaura este editor. Si una recreación ya no
  coincide con la raíz o línea temporal `V2Ready` vigente, descarta la
  expectativa restaurada y vuelve a un estado seguro sin escribir.
- Un error conserva el borrador y permite reintentar.
- Un `Mutex` o mecanismo equivalente impide el doble toque.
- El éxito se consume una sola vez y no reaparece al recrear.
- El borrador se considera modificado sólo si cambió la plantilla o el puesto
  normalizado. Sin cambios, `Guardar cambios` permanece deshabilitado y no
  actualiza `updatedAt`.

### 10. Avisos existentes

- Se conserva el reconciliador único de notificaciones ya existente; este
  incremento no crea otro planificador ni otra fuente de avisos.
- Si una edición cambia los límites horarios, la observación vigente debe
  cancelar los límites anteriores y programar los nuevos. Una edición que no
  cambia horarios no duplica alarmas.
- Al eliminar, se cancelan los límites y la notificación visible de esa jornada,
  y se limpia su seguimiento, sin afectar avisos de otras jornadas.
- Estas garantías se prueban con dobles o fakes del reconciliador y del
  planificador. No se solicitan permisos nuevos ni se ejecuta QA física de
  alarmas exactas en este bloque.

## OUTPUT

### Superficie V2 dedicada

Crear un estado y coordinador exclusivos para este recorrido. Se recomiendan
nombres como:

- `V2ShiftEditViewModel.kt`;
- `V2ShiftEditScreens.kt`;
- `V2ShiftEditCoordinatorTest.kt`;
- `V2ShiftEditComposeTest.kt`.

Podés extraer componentes visuales puros compartidos con la carga manual si
eso reduce duplicación sin mezclar estados o escritores. No conviertas
`V2ManualShiftLoadViewModel` en un coordinador ambiguo de todas las mutaciones.

### Navegación

- El detalle del día sigue siendo la entrada.
- El coordinador carga fuera de Compose las fotografías de las jornadas del día
  y produce un estado por UUID. No consulta Room desde cada tarjeta ni supone
  que toda fila es V2 por estar en `V2Ready`.
- Mientras se determina qué filas son V2 se muestra una espera neutral; una
  falla ofrece `Reintentar` y no habilita escrituras.
- `Editar este día` aparece habilitado cuando el día contiene al menos una
  jornada V2 editable. En un día mixto, una fila sin fotografía permanece
  visible pero nunca recibe acciones V2.
- Después, cada jornada V2 muestra `Editar jornada` y `Eliminar jornada`.
- Dos jornadas visualmente iguales se distinguen también con semántica como
  `Jornada 1 de 2` y test tags ligados a su UUID.
- El editor es una superficie bloqueante clara; Atrás solicita descartar sólo
  cuando exista un cambio no guardado.
- Este recorrido no usa `CalendarInteractionMode.EDIT`: ese modo continúa
  reservado a la selección explícita de fechas y a la carga manual.
- La nueva superficie participa en `hasBlockingSurface` y oculta el
  `ModalBottomSheet` del detalle mientras el editor o un diálogo estén encima.
- Cancelar vuelve al detalle del mismo día; guardar o eliminar limpia el
  detalle y vuelve al Calendario actualizado.
- Atrás resuelve en este orden: diálogo, revisión o advertencia, formulario,
  confirmación de descarte y detalle. El primer Atrás con teclado visible sólo
  cierra el teclado.
- Durante una escritura no se abandona ni se descarta la superficie.

### Formulario

El formulario muestra:

- fecha fija;
- resumen de la jornada original;
- plantilla actual o histórica;
- opciones activas compatibles;
- puesto o función opcional;
- resumen final antes de guardar;
- espera, error y reintento sin perder el borrador.

El botón de guardado permanece deshabilitado mientras faltan datos, hay carga o
guardado en curso, o no existe una selección coherente.

### Contratos puros y Room

Agregar sólo lo mínimo para:

- conservar íntegramente la fotografía al editar únicamente el puesto;
- actualizar el par mediante la precondición de ocupación vigente;
- permitir la edición de sólo puesto sobre fuentes archivadas mediante la
  excepción exacta y cerrada definida arriba;
- eliminar con una precondición inmutable del par completo confirmado, no sólo
  con `updatedAt`;
- distinguir claramente fila inexistente, fila V1 y conflicto concurrente;
- mantener rollback completo.

Room permanece en versión 7. `7.json`, entidades, tablas, índices, claves y
migraciones permanecen byte a byte sin cambios.

SHA-256 de entrada de `7.json`:
`E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`.

## SCOPE

Permitido:

- `app/src/main/**` estrictamente relacionado con el recorrido;
- `app/src/test/**` y `app/src/androidTest/**` afectados;
- recursos de texto y test tags estrictamente necesarios;
- cambios mínimos en `core/domain` para la edición de sólo puesto y las
  precondiciones completas de edición y eliminación;
- cambios mínimos en `core/database` para aplicar esas precondiciones y la
  excepción exacta de sólo puesto dentro de la transacción existente;
- pruebas puras y Room específicas de esos contratos.

Archivos de integración probables:

- `app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`;
- el nuevo coordinador y sus pantallas V2;
- pruebas vecinas de Calendario, primera configuración, carga manual,
  navegación y recreación;
- `V2ShiftRepository.kt`, `RoomV2ShiftRepository.kt` y sus pruebas únicamente
  para los contratos transaccionales autorizados.

## DEPENDENCIES

Reutilizar:

- `WorkSetupState.V2Ready`;
- `WorkConfigurationRepository`;
- `WorkCatalogRepository`;
- `ObjectiveRepository`;
- `ShiftRepository` sólo para lectura de jornada y ocupación;
- `MedicalLeaveRepository` sólo para advertencias;
- `V2ShiftRepository` para todas las escrituras;
- `editV2ShiftWrite(...)` y una variante pura mínima para cambiar sólo puesto;
- `planV2ShiftBatch(..., editingShiftId = ...)`;
- `ShiftOccupancyExpectation`;
- el `NotificationReconciler` y su observación existentes, únicamente para
  conservar la reconciliación posterior a una edición o eliminación;
- la grilla, proyección y detalle existentes del Calendario;
- la política de borradores y eventos consumibles de la carga manual V2.

No dupliques reglas ya estabilizadas. Si una API existente no alcanza, ampliá
el contrato mínimo y agregá sus pruebas; no abras una arquitectura paralela.

## DO NOT

- no retirar ni limpiar el modo V1 en este bloque;
- no cambiar rubro ni configuración laboral;
- no mover una jornada a otra fecha;
- no editar varias jornadas o fechas en masa;
- no implementar recurrencias ni planes futuros;
- no crear jornadas nuevas desde este recorrido;
- no reutilizar `CalendarEditTools`, `ShiftDraft`, `ManagementViewModel`,
  `ShiftRepository.update(...)` ni `ShiftRepository.delete(...)` para escribir
  una jornada V2;
- no editar, archivar ni reactivar lugares, tipos, plantillas o reglas;
- no cambiar el estado de la jornada;
- no implementar horario real, extras, cumplimiento, disponibilidad, guardia
  pasiva ni situaciones especiales;
- no habilitar Novedades V1 en V2;
- no adaptar Resumen, próximo evento, la interfaz o arquitectura de
  notificaciones, clima, widget, informes, copias, bloqueo ni Ayuda; sólo se
  conserva y verifica la reconciliación heredada exigida arriba;
- no cambiar Room v7, entidades, tablas, esquema, migraciones ni versión;
- no cambiar DataStore, Gradle, dependencias, manifiesto, permisos,
  `applicationId`, versión ni SDK;
- no crear `Salud`, `Otro` ni una quinta opción;
- no agrupar Enfermería y Medicina;
- no imponer 204 horas, nocturnidad ni reglas por sector;
- no agregar montos, salarios, liquidaciones ni información sindical;
- no agregar cuentas, red, nube, sincronización, telemetría ni datos reales;
- no modificar documentación canónica, ADR, auditorías ni el índice de prompts;
- no crear otra tarea, rama, worktree o proyecto;
- no hacer commit, push, tag, merge, rebase, reset ni descartar cambios;
- no abrir, instalar sobre ni modificar producción.

Si una corrección exige salir de estos límites, detené esa parte y entregá a
MAIN el defecto concreto. No amplíes el alcance silenciosamente.

## VALIDATION

### Pruebas JVM de dominio y coordinador

Cubrir como mínimo:

1. sólo una jornada con fotografía V2 puede abrir el editor;
2. una fila inexistente o sin fotografía se rechaza sin mutar;
3. la fecha permanece fija;
4. editar conserva UUID, zona, `createdAt` y estado;
5. cambiar sólo el puesto conserva exactamente la fotografía histórica;
6. elegir otra plantilla actualiza lugar, tipo, horario, color, revisión y
   fotografía de forma coherente;
7. una plantilla inactiva no se ofrece como alternativa nueva;
8. una edición de sólo puesto conserva fuentes archivadas sin permitir ningún
   otro cambio;
9. se preservan todas las demás jornadas del día;
10. segunda jornada, superposición, descanso corto y carpeta médica requieren
   confirmación;
11. cancelar, volver o descartar conserva el par original;
12. sin cambios, guardar permanece inactivo y no actualiza timestamps;
13. un reloj que devuelve el mismo instante produce una versión persistida
    estrictamente posterior y distinguible;
14. una edición vieja se rechaza ante cualquier cambio del par original y obliga
    a revisar;
15. una jornada vecina modificada dentro de la ventana de dos días invalida las
    advertencias, incluso al cruzar de mes o año;
16. una eliminación vieja se rechaza y obliga a revisar;
17. `applyV2Batch(...)` continúa rechazando un lote compuesto sólo por borrado;
18. doble toque produce como máximo una escritura;
19. error conserva borrador y reintento seguro;
20. recreación recupera el borrador y relee la expectativa;
21. un éxito se consume una sola vez;
22. V1 y `V2NeedsFirstSet` nunca usan el coordinador ni los escritores V2;
23. una recreación con raíz o línea temporal distinta invalida la superficie
    restaurada sin escribir;
24. cambiar el horario reconcilia los límites de aviso y eliminar cancela los de
    esa jornada sin tocar los de otras.

### Pruebas Room

Cubrir en dispositivo o instrumentación Room:

- actualización y reapertura del par V2;
- rollback si falla cualquier parte de la actualización;
- conflicto de edición concurrente sin sobreescritura;
- eliminación y reapertura del par V2;
- conflicto de eliminación concurrente sin borrado;
- matriz exacta de eliminación: fotografía, notas, novedades propias, cambio
  formal, configuración y recordatorios de notificación;
- eliminación de la sola novedad externa que enlaza mediante `relatedShiftId`,
  preservando su jornada dueña y los demás datos;
- conservación de otras jornadas, configuración y catálogo;
- edición de sólo puesto con plantilla archivada y revisión luego reemplazada,
  sin cambiar ningún otro campo del par;
- rechazo de UUID inexistente y fila sin fotografía V2;
- ausencia de escrituras parciales;
- esquema Room v7 byte a byte sin cambios.

### Pruebas de reconciliación de avisos

Con dobles o fakes, sin permisos ni alarmas físicas exactas:

- cambiar el intervalo cancela los límites anteriores y programa los nuevos;
- cambiar sólo el puesto no duplica límites;
- eliminar cancela límites, notificación visible y seguimiento de la jornada;
- ninguna operación altera los avisos de otras jornadas.

### Compose e integración

Con datos exclusivamente ficticios:

1. tocar un día consulta y no escribe;
2. `Editar este día` abre acciones conscientes sólo para jornadas V2;
3. la identificación V2 posee espera, error y reintento sin escribir;
4. un día mixto deja la fila sin fotografía visible pero sin acciones V2;
5. una jornada, varias jornadas y dos textos idénticos se distinguen sin
   depender del color;
6. `Editar jornada` precarga fecha, selección y puesto correctos;
7. la fecha se muestra pero no puede cambiarse;
8. cambiar plantilla, editar sólo puesto, cancelar y guardar funcionan;
9. las advertencias permiten volver o confirmar;
10. `Eliminar jornada` muestra datos históricos de la jornada exacta y permite
    cancelar;
11. confirmar elimina sólo la jornada elegida;
12. error conserva el formulario y permite reintentar;
13. Atrás respeta teclado, diálogo, revisión, formulario y descarte;
14. recreación en revisión, advertencia y confirmación de eliminación no repite
    escrituras;
15. el Calendario se actualiza inmediatamente tras editar o eliminar, incluido
    el paso de `2 turnos` a la jornada restante;
16. carga manual V2, primera configuración y recorrido V1 no regresionan;
17. claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % mantienen
    contenido y acciones alcanzables;
18. teclado e insets mantienen el formulario alcanzable al 200 %;
19. textos, roles, estados y acciones poseen semántica accesible y no dependen
    únicamente del color.

### Batería local

Ejecutar serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Obtener conteos reales desde XML y distinguir:

- JVM verificado;
- lint;
- APK Debug compilado;
- APK AndroidTest QA compilado;
- instrumentación ejecutada;
- revisión física;
- pendiente.

### Samsung físico

Verificar ADB y usar exclusivamente el Samsung `SM-S938B`, API 36, el paquete
QA y datos ficticios.

Ejecutar:

- pruebas nuevas de coordinador/Compose/actividad;
- regresiones afectadas de Calendario, primera configuración, carga manual y
  recreación;
- `V2ShiftPersistenceInstrumentedTest` y cualquier prueba Room nueva;
- un recorrido integral `MainActivity + Room` desde una configuración
  `NEW_V2` limpia.

Recorrido manual proporcional:

1. configurar V2 y cargar dos jornadas ficticias en el mismo día;
2. abrir el detalle sin escrituras;
3. cancelar una edición;
4. cambiar sólo el puesto de la primera jornada;
5. cambiar la plantilla de esa jornada y confirmar una advertencia si aplica;
6. cerrar y reabrir QA para comprobar persistencia;
7. cancelar la eliminación de la segunda jornada;
8. confirmar después su eliminación y verificar que la primera permanece;
9. recrear o rotar con borrador abierto;
10. comprobar claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y
    200 %.

No consultes ni modifiques `font_scale`, densidad, tamaño visual o zoom del
sistema. No habilites permisos especiales ni recorridos físicos de alarmas
exactas dentro de este bloque.

Antes de instalar, limpiar datos o desinstalar QA, pedí autorización expresa en
la tarea implementadora. Con esa autorización, al finalizar desinstalá
exclusivamente QA y QA.test e informá qué quedó en el teléfono. Sin autorización,
no toques el dispositivo, marcá la validación física como `PENDIENTE` y no
declares la dependencia terminada. Producción no se abre ni se modifica.

## HANDOFF A MAIN

Entregar un handoff compacto con:

- `OBJECTIVE`;
- `CHANGES`;
- `FILES` modificados y nuevos;
- `DECISIONS` y cualquier ajuste mínimo de contrato;
- `VALIDATION` con comandos y conteos reales;
- `ROOM` con versión y hash de `7.json`;
- `DEVICE SAFETY` y paquetes finales;
- `RISKS` y verificaciones pendientes;
- `GIT` con ruta, rama, HEAD, upstream, base y estado;
- `NEXT` reservado a MAIN.

La entrega queda directamente en el checkout compartido, sin commit. MAIN
audita cada hunk, repite pruebas proporcionales, corrige la integración,
actualiza las fuentes de verdad y crea el checkpoint local sólo si todo queda
verde.

No declares terminadas las recurrencias, la edición masiva, el Calendario
final, el motor de horas ni el Corte B completo.

## DONE WHEN

La dependencia está lista para entregar a MAIN solamente cuando:

- una persona `V2Ready` puede editar exactamente una jornada desde el detalle
  del día sin cambiar su fecha ni sus compañeras;
- puede eliminar exactamente una jornada después de una confirmación clara;
- `Shift` y `ShiftWorkSnapshot` se validan y persisten como un par atómico;
  al editar sólo el puesto, la fotografía permanece idéntica; al eliminar,
  ambos desaparecen juntos;
- cambiar de plantilla usa fuentes activas y la revisión correcta de la fecha;
- una revisión concurrente vencida no escribe;
- errores, reintento, doble toque y recreación son seguros;
- el Calendario refleja inmediatamente el resultado;
- Room permanece en v7 sin cambios de esquema;
- carga manual V2 y superficies heredadas no regresionan;
- las pruebas proporcionales pasan con evidencia real;
- la QA física autorizada queda ejecutada, QA retirada, producción intacta y el
  diff sin commit ni push para auditoría de MAIN.
