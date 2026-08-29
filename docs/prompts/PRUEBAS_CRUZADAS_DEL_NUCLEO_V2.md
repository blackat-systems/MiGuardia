# Pruebas cruzadas del núcleo V2

- Estado: **CERRADO**
- Fecha: 2026-08-28
- Cierre MAIN: 2026-08-29
- Base documental previa: `d16ca11ee920d0d9be0f220eda60c3bd02d859d4`
- Rama obligatoria: `codex/miguardia-2.0`
- Resultado que corrige:
  `AUDITORÍA PARCIAL — NO CERRABLE`

Este prompt queda habilitado por indicación de Joaquin. Prepararlo no abre por
sí solo otra tarea. La dependencia comienza únicamente cuando Joaquin envíe
este contrato a un nuevo chat o autorice expresamente a MAIN a crearlo.

## QUÉ HACE

Agrega tres pruebas de seguridad que obligan a las piezas centrales de
MiGuardia a demostrar juntas que:

1. Calendario, Horas, Resumen, tarjeta superior, próximo evento y avisos
   interpretan una misma historia laboral sin duplicarla ni contradecirse;
2. dos guardados que compiten por los mismos datos no pueden mezclarse ni dejar
   información a medias;
3. consultar Calendario, Resumen o la tarjeta superior no modifica los datos
   guardados.

No agrega funciones visibles ni cambia cómo trabaja la aplicación.

## POR QUÉ EXISTE

La auditoría integral del núcleo ejecutó 498 pruebas JVM y no reprodujo un
defecto P0/P1. Sin embargo, las comprobaciones actuales demuestran cada pieza
por separado. Faltan tres barreras concretas para saber que las piezas siguen
coincidiendo cuando aparecen juntas y que la persistencia resiste una carrera
real.

Estas pruebas deben cerrar únicamente esos huecos. No autorizan un refactor,
una función nueva ni una corrección productiva silenciosa.

## ROLE

Sos la dependencia especializada **Pruebas cruzadas del núcleo V2**.

Tu responsabilidad se limita a código de prueba y helpers exclusivos de
prueba. No sos MAIN, no decidís producto, no integrás el resultado, no
actualizás documentación canónica y no creás checkpoints.

Si una prueba nueva reproduce un defecto productivo, detenete. Entregá a MAIN
la reproducción, el impacto y los archivos probablemente dueños. No modifiques
`src/main` para hacer pasar la prueba.

## TASK

Implementar exactamente tres métodos nuevos anotados con `@Test`:

1. una prueba JVM transversal con una sola fotografía determinista del núcleo;
2. una prueba Room instrumentada con dos escritores concurrentes reales;
3. una prueba instrumentada que demuestre que las tres consultas principales
   no escriben.

Podés crear fixtures y helpers exclusivamente de prueba. No agregues una
cuarta prueba ni amplíes el caso a capacidades futuras.

## CONTEXT

### Estado confirmado por MAIN

- MiGuardia ejecuta exclusivamente V2 sobre `miguardia-v2.db`.
- `MiGuardiaV2Database` está en versión 5 con 27 tablas de aplicación.
- Las migraciones activas son `1→2→3→4→5`.
- Calendario, Horas, Resumen, tarjeta y avisos ya poseen motores puros o
  observadores de sólo lectura.
- `V2ShiftRepository` es la frontera estructural de jornadas.
- Horas y Resumen comparten `calculateHoursContributions(...)` y
  `summarizeHoursContributions(...)`.
- Tarjeta, próximo evento y avisos comparten `projectNextEvent(...)`.
- La auditoría integral quedó parcial por tres huecos de cobertura, no por un
  defecto funcional reproducido.
- El prompt auditor integral permanece **PAUSADO / NO REEJECUTAR** hasta
  integrar esta dependencia y completar después la matriz Android exigida.

### Base Git

La base previa a este prompt es:

```text
d16ca11ee920d0d9be0f220eda60c3bd02d859d4
```

MAIN informará en el mensaje de apertura el HEAD documental exacto que contiene
este archivo. Ese HEAD debe ser descendiente de la base anterior y su diferencia
adicional debe ser exclusivamente documental. No inventes el SHA ni trabajes
desde otro checkout.

### Room protegida

Los esquemas exportados continúan intactos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

El `identityHash` de Room V5 es
`77adbc875d0f4ee466cdbd0dd74d5c5c`.

## INPUTS

Antes de editar, leé completamente y en el orden exigido por `AGENTS.md`:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las fichas de los cuatro sectores;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. ADR 0023, 0025, 0026, 0027, 0028, 0029, 0030, 0031 y 0032;
9. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
10. `docs/prompts/AUDITORIA_INTEGRAL_DEL_NUCLEO_Y_COMPATIBILIDAD_ANDROID_V2.md`;
11. la auditoría parcial durable del 2026-08-28;
12. el código y las pruebas de Calendario, Horas, Resumen, próximo evento,
    avisos, repositorios V2 y persistencia Room afectados.

Inspeccioná en particular:

- `CalendarProjection.kt` y `CalendarProjectionTest.kt`;
- `HoursProgress.kt` y `HoursProgressTest.kt`;
- `MonthlySummary.kt` y `MonthlySummaryTest.kt`;
- `NextEvent.kt`, `TodayCardProjection.kt` y sus pruebas;
- `ShiftNotificationPlan.kt` y `ShiftNotificationPlanTest.kt`;
- `CalendarMonthObserver`, `SummaryObserver` y `NextEventObserver`;
- `V2ShiftRepository`, `RoomV2ShiftRepository` y
  `V2ShiftPersistenceInstrumentedTest`;
- fixtures V2 instrumentados existentes.

## OUTPUT

La entrega esperada contiene:

- exactamente tres métodos `@Test` nuevos;
- los fixtures o helpers de prueba mínimos para sostenerlos;
- cero cambios en código productivo;
- cero cambios de producto, Room, DataStore, Gradle, manifiesto o permisos;
- comandos y conteos reales de la validación ejecutada;
- un handoff autosuficiente a MAIN.

Si una prueba reproduce un defecto real, la salida cambia a `MAIN BLOQUEADA` y
no contiene un parche productivo.

## SCOPE

### Permitido

Sólo podés modificar o crear archivos dentro de:

```text
core/domain/src/test/**
core/database/src/androidTest/**
app/src/androidTest/**
```

El diseño recomendado es:

- un test JVM nuevo bajo
  `core/domain/src/test/.../integration/V2CoreCrossProjectionTest.kt`;
- un método nuevo dentro de
  `V2ShiftPersistenceInstrumentedTest.kt`;
- un test instrumentado nuevo bajo
  `app/src/androidTest/.../V2ReadOnlySurfacesInstrumentedTest.kt`;
- helpers adicionales sólo si permanecen bajo esos mismos árboles de prueba.

No es obligatorio conservar esos nombres si el árbol real demuestra otro
nombre más coherente, pero la separación de responsabilidades sí es
obligatoria.

### Prueba 1 — una sola fotografía transversal

Usá una única fixture privada e inmutable, con UUID, `Clock` y `ZoneId` fijos.
Debe contener simultáneamente:

- una jornada materializada por una ocurrencia recurrente `AUTOMATIC`;
- su par completo `Shift + ShiftWorkSnapshot`;
- horario planificado 08:00–12:00 local;
- horario real 08:30–13:00 local;
- un fragmento extra 12:00–13:00 que ayuda a la referencia;
- un extra independiente 14:00–15:00 que no ayuda a la referencia;
- una disponibilidad 07:00–17:00 coincidente;
- una jornada vecina al día siguiente protegida por carpeta médica;
- referencia mensual explícita de 300 minutos;
- catálogo y reglas históricas coherentes;
- zona `America/Argentina/Cordoba`;
- instante `2026-08-25T18:00:00Z`, equivalente a las 15:00 locales.

Desde los mismos objetos llamá a:

- `projectCalendarMonth(...)`;
- `calculateHoursContributions(...)` y `calculateHoursProgress(...)`;
- `calculateMonthlySummary(...)`;
- `projectNextEvent(...)`;
- `projectTodayCard(...)`;
- `buildNotificationPlan(...)`.

La única prueba debe demostrar como mínimo:

1. `occurrence.shiftId == shift.id` y
   `occurrence.localDate == shift.localStartDate`; la `revisionId` de la
   ocurrencia pertenece a una revisión de su plan, mientras
   `snapshot.configurationRevisionId` pertenece a la revisión laboral vigente;
   ambas revisiones permanecen diferenciadas y la jornada no aparece duplicada;
2. Calendario muestra la jornada del 25 una vez como completada y conserva la
   jornada vecina protegida el 26;
3. Horas calcula 210 minutos habituales, 60 extra de jornada y 60 extra
   independiente: total 330, ayudan 270, no ayudan 60, meta 300 y faltan 30;
4. Resumen informa habitual 210, extras 120 y total 330; su cumplimiento
   coincide con Horas y su libro posee las mismas fuentes e intervalos una sola
   vez;
5. disponibilidad informa 600 minutos programados, 150 efectivos
   transcurridos, 330 reemplazados, 120 pendientes y 270 proyectados;
6. próximo evento excluye la jornada con horario real y la vecina protegida;
   su único evento vigente es la reanudación de disponibilidad
   `2026-08-25T18:00:00Z→20:00:00Z`;
7. la tarjeta lista una vez la jornada completada y usa exactamente la misma
   proyección futura;
8. los avisos conservan para esa reanudación sólo el límite `END` a las
   `20:00:00Z`, sin otro recordatorio ni inicio invasivo;
9. la jornada protegida permanece visible en Calendario y ausente de Horas,
   Resumen, próximo evento y avisos;
10. identidades, contribuciones, intervalos y límites son únicos y cada cifra
    del Resumen reconcilia con su detalle.

No exijas que una jornada con horario real sea el próximo evento: el contrato
V2 la excluye de la planificación futura. En esta fotografía, tarjeta y avisos
deben coincidir sobre la disponibilidad reanudada.

Esta prueba JVM no demuestra Compose, observación Room ni ausencia de
escrituras. No la presentes como evidencia de esas capas.

### Prueba 2 — carrera CAS real

Agregá una prueba Room instrumentada que:

1. siembre un par V2 original completo;
2. capture una sola `ShiftOccupancyExpectation` y una sola
   `V2ShiftWriteExpectation` compartidas;
3. prepare dos lotes distintos: ambos editan el par original y cada uno agrega
   un par compañero diferente, de modo que cualquier escritura parcial quede
   visible;
4. cree dos escritores del repositorio sobre la misma base Room;
5. lance ambos en coroutines de IO;
6. confirme mediante una barrera determinista que los dos están listos antes
   de liberarlos;
7. no use `delay`, sleeps, hooks productivos ni dependencia del orden del
   scheduler;
8. obtenga exactamente un éxito y un `ConflictingLocalWriteException`;
9. compruebe que sólo existen el par actualizado y el compañero del ganador;
10. compruebe que no existe ninguna fila parcial del perdedor;
11. verifique conteos iguales de jornadas y fotografías, cero huérfanos,
    `foreign_key_check` vacío e `integrity_check=ok`;
12. cierre y reabra Room, y repita las comprobaciones.

Una secuencia de dos escrituras ejecutadas una después de la otra no satisface
esta prueba. Ambos escritores deben partir de la misma observación y comenzar
la competencia desde la misma barrera.

### Prueba 3 — consultar no escribe

Agregá una prueba instrumentada que siembre datos V2 deterministas y ejecute
los observadores reales de:

- `CalendarMonthObserver.observe(month)`;
- `SummaryObserver.observe(month)`;
- `NextEventObserver.observe()`.

Usá `first()` con timeout y cancelación segura, `Clock` y zona fijos y un
`TemporalDelay` cancelable. Afirmá además contenido concreto de cada resultado,
para que una consulta vacía o que nunca leyó la fuente no produzca un falso
verde.

Antes de consultar, construí una fotografía lógica canónica de las 27 tablas
de aplicación:

- nombres de tabla y columnas;
- valores tipados de todas las filas;
- filas ordenadas de forma estable en el test;
- conteos por tabla;
- hash SHA-256 de esa representación;
- timestamps incluidos como cualquier otro valor.

Excluí únicamente metadatos internos de SQLite/Room; no excluyas ninguna tabla
de aplicación. No uses bytes del archivo DB/WAL, `mtime` ni el orden casual de
una consulta como prueba principal. `PRAGMA data_version` puede usarse como
señal adicional, pero la fotografía lógica y los conteos son la barrera
decisiva.

Compará fotografía, conteos y señal adicional:

1. antes de consultar;
2. después de Calendario;
3. después de Resumen;
4. después de la tarjeta;
5. después de cerrar y reabrir Room.

No ejecutes acciones de personalización, confirmaciones ni mutaciones del
usuario: ésas son escrituras legítimas y no forman parte de una consulta.

## DEPENDENCIES

- El prompt debe figurar `HABILITADO` en `docs/prompts/README.md`.
- Joaquin debe haber abierto o autorizado expresamente la tarea.
- El checkout debe comenzar limpio en el HEAD documental informado por MAIN.
- No puede existir otra dependencia implementadora activa.
- Las APIs productivas vigentes son entradas inmutables de esta tarea.
- La instrumentación necesita una autorización posterior y explícita para cada
  dispositivo o emulador; este archivo no la concede.

## DO NOT

No modifiques:

- `app/src/main/**`;
- `core/domain/src/main/**`;
- `core/database/src/main/**`;
- Room, entidades, DAO, repositorios, esquemas o migraciones;
- DataStore;
- Gradle, dependencias o plugins;
- manifiesto, permisos, `applicationId`, versión o SDK;
- documentación canónica, ADR, prompts, STATUS, MAPA o auditorías;
- reglas sectoriales o comportamiento visible.

Tampoco:

- agregues una dependencia de prueba;
- crees un hook productivo para coordinar la carrera;
- conviertas una prueba secuencial en evidencia de concurrencia;
- dupliques fórmulas de Horas, Resumen o próximos eventos dentro del fixture;
- persistas totales derivados;
- uses red, datos reales, cuentas, nube, telemetría o logs privados;
- hagas commit, push, tag, merge, rebase, reset o descartes;
- crees rama, worktree, tarea o subagente;
- uses ADB, Samsung, emuladores, instalaciones o limpiezas sin autorización
  expresa nueva;
- dispares una alarma exacta real ni reinicies físicamente el Samsung.

## PUERTA 0

Antes de editar, verificá y registrá:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse '@{upstream}'
git rev-parse 'v1.0.0^{}'
git status --short --branch
git worktree list --porcelain
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
git config user.name
git config user.email
git remote -v
```

La ruta debe ser:

```text
C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0
```

La rama debe ser `codex/miguardia-2.0`, el autor debe ser
`joaquin <blackat.systems@gmail.com>` y `main`, `origin/main` y
`v1.0.0^{}` deben permanecer en
`82db6fd8eb2c511205968894dc9857a96b16ed20`.

Detenete ante un HEAD diferente del informado por MAIN, un checkout sucio,
staged, archivos sin dueño, detached HEAD, divergencia remota inesperada o un
prompt que ya no esté habilitado. No limpies ni descartes nada.

## VALIDATION

### 1. Validación dirigida

Ejecutá primero las pruebas nuevas de forma aislada cuando el runner lo
permita. La prueba transversal debe ejecutarse realmente en JVM. Las dos
pruebas instrumentadas deben compilar aunque todavía no exista autorización de
dispositivo.

### 2. Batería local completa

Ejecutá serializado y con tareas forzadas:

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

Obtené los conteos reales desde XML. No copies los 498 casos históricos como
si fueran el resultado nuevo. Separá siempre:

- `JVM VERIFICADO`;
- `LINT`;
- `COMPILADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA HUMANA`;
- `PENDIENTE`.

Ejecutá además:

```powershell
git diff --check
git status --short
git diff --stat
git diff
```

Confirmá que el diff pertenece sólo a los tres árboles de prueba autorizados y
que contiene exactamente tres métodos `@Test` nuevos.

### 3. Instrumentación

Este prompt no autoriza dispositivos. Si Joaquin no concede una autorización
nueva dentro de la tarea, no ejecutes ADB y entregá las dos pruebas Android
como `ANDROIDTEST COMPILADO / INSTRUMENTACIÓN PENDIENTE`.

Con autorización expresa, usá un solo serial por vez y solamente paquetes QA o
de prueba autorizados. Ejecutá al menos:

- la carrera CAS en el paquete instrumentado de `core:database`;
- la prueba de superficies de sólo lectura en el paquete QA de aplicación;
- las regresiones directamente afectadas.

No uses `:app:connectedDebugAndroidTest`, porque podría instalar un host con el
identificador productivo. La matriz completa Samsung API 36, Android 8/API 26
y Android 13/API 33 pertenece a la repetición posterior de la auditoría, no se
considera cumplida por compilar o ejecutar sólo estas dos pruebas.

## HANDOFF A MAIN

La entrega debe comenzar exactamente con:

```text
# HANDOFF A MAIN — Pruebas cruzadas del núcleo V2
```

La línea siguiente debe ser una sola de estas:

```text
CANDIDATO VERIFICADO — LISTO PARA AUDITORÍA DE MAIN
CANDIDATO LOCAL — INSTRUMENTACIÓN PENDIENTE
MAIN BLOQUEADA
```

Usá `MAIN BLOQUEADA` cuando una prueba reproduzca un defecto productivo, una
modificación productiva sea necesaria o una contradicción impida completar el
alcance sin ampliarlo.

Después incluí, en este orden:

1. `QUÉ HACE`;
2. `POR QUÉ EXISTE`;
3. `OBJECTIVE`;
4. `CHANGES`;
5. `FILES`;
6. `DECISIONS`;
7. `TEST 1 — FOTOGRAFÍA TRANSVERSAL`;
8. `TEST 2 — CARRERA CAS`;
9. `TEST 3 — CONSULTAS SIN ESCRITURAS`;
10. `VALIDATION`;
11. `ROOM`;
12. `PHYSICAL QA`;
13. `DEVICE SAFETY`;
14. `RISKS`;
15. `PENDING`;
16. `GIT`;
17. `NEXT`.

Informá nombres exactos de pruebas, archivos, comandos, conteos, resultados y
niveles de evidencia. Si existe un fallo, incluí la reproducción mínima y no
lo ocultes detrás de una batería global roja.

El resultado queda sin commit directamente en el checkout compartido. No hay
nada para `cherry-pick`. MAIN audita el diff y decide su integración.

## DONE WHEN

El candidato de pruebas está listo para MAIN cuando:

- Puerta 0 pasó sobre el HEAD exacto;
- existen exactamente tres métodos `@Test` nuevos;
- la fotografía JVM usa una sola fixture y reconcilia los valores e identidades
  exigidos;
- la carrera libera dos escritores desde una barrera real y demuestra un éxito,
  un conflicto y cero efectos parciales;
- la prueba de consultas compara las 27 tablas después de cada superficie y
  después de reabrir;
- la prueba JVM nueva y toda la batería local están verdes;
- ambos AndroidTest compilan;
- toda instrumentación autorizada está informada con resultados reales;
- no existe ningún cambio productivo, documental o fuera del alcance;
- Room V5 y sus cinco esquemas permanecen intactos;
- `git diff --check` está limpio;
- no hubo commit, push ni acción no autorizada;
- el handoff permite a MAIN auditar sin reconstruir este chat.

Si la instrumentación no fue autorizada, el código puede entregarse como
`CANDIDATO LOCAL — INSTRUMENTACIÓN PENDIENTE`, pero la dependencia no queda
cerrada ni habilita por sí sola la repetición de la auditoría integral.

## CONDICIONES DE PARADA

Detenete y devolvé el control a MAIN ante:

- mismatch de ruta, rama, HEAD, upstream, refs protegidas o autor;
- checkout sucio, staged o cambios sin dueño;
- prompt no habilitado o tarea no autorizada;
- necesidad de tocar código productivo, Room, Gradle, manifiesto o docs;
- una prueba nueva que reproduzca un defecto real;
- una cuarta prueba necesaria para cerrar el alcance;
- otra dependencia activa;
- uso obligatorio de un dispositivo que no fue autorizado;
- acción destructiva, descarga, push, tag, Release, `main` o producción.

## CIERRE MAIN — 2026-08-29

MAIN auditó el candidato completo y confirmó exactamente tres métodos `@Test`
nuevos dentro de los árboles permitidos, sin cambios productivos. La batería
local forzada quedó verde con 499/499 pruebas JVM y lint sin errores.

Con autorización expresa de Joaquin se ejecutó en el Samsung `SM-S938B`, API
36, serial `R5CY529W6PL`:

- carrera CAS nueva: 1/1;
- consulta sin escrituras nueva: 1/1;
- suite Room completa: 108/108;
- regresiones afectadas de Calendario, Resumen y tarjeta: 61/61.

No se disparó una alarma exacta real, no se reinició el teléfono y no se
consultaron ni modificaron ajustes visuales del sistema. Los paquetes QA y de
prueba fueron desinstalados; ningún paquete `com.blackatsystems.miguardia*`
quedó instalado en los usuarios 0 o 10.

Room permanece en versión 5 con 27 tablas y los esquemas 1–5 intactos. La
evidencia durable está en
`docs/audits/2026-08-29-pruebas-cruzadas-del-nucleo-v2.md`.

Este cierre habilita la matriz Android 36/26/33 y la repetición de la auditoría
integral. No habilita todavía la segunda capa, un push, tag, Release, `main` ni
producción.
