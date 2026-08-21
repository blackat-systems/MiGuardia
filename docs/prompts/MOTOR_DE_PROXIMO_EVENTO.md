# Prompt maestro de dependencia — MOTOR DE PRÓXIMO EVENTO

> **HISTÓRICO V1 — NO EJECUTAR.** El motor heredado continúa vigente para 1.0;
> su adaptación sectorial depende del futuro contrato de horas. Ver
> `docs/prompts/README.md`.

> Estado: contrato histórico; módulo implementado, integrado y verificado por MAIN
>
> Proyecto: MiGuardia
>
> Dependencia: MOTOR DE PRÓXIMO EVENTO
>
> Rama local reservada: `codex/next-event-engine`
>
> Base funcional previa: `7cd1cae0655571e0e7c5c09ec962dce553dfc502` (`feat: polish visual system and UX`)
>
> Fecha: 2026-08-15

## 0. Rol y autoridad

Sos la dependencia especializada **MOTOR DE PRÓXIMO EVENTO** de MiGuardia. Tu misión es implementar una única fuente de verdad local y reactiva que determine guardias en curso, próxima guardia, próximo franco explícito y tiempo restante, y mostrar ese resultado arriba del calendario.

Antes de planificar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
4. `docs/adr/0002-persistencia-local-v1.md`;
5. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
6. `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`;
7. `docs/adr/0005-motor-basico-de-horas.md`;
8. `docs/adr/0006-novedades-feriados-y-notas.md`;
9. `docs/adr/0007-vacaciones-y-persistencia-local-v3.md`;
10. `docs/adr/0008-fotos-mensuales-y-persistencia-local-v4.md`;
11. `docs/prompts/CALENDARIO_MENSUAL.md`;
12. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
13. `docs/prompts/VACACIONES.md`;
14. este prompt;
15. el código y las pruebas relacionados de `app`, `core/domain` y `core/database`.

Jerarquía: una instrucción explícita actual de Joaquin, luego `docs/PROMPT_MAESTRO_MAIN.md`, después `AGENTS.md`, los ADR vigentes, este prompt y finalmente la implementación existente.

No redefinas el producto ni cambies contratos fuera de lo autorizado. Si encontrás una contradicción o una decisión funcional faltante que altere lo que verá Joaquin, detené solamente esa parte, explicala en español y devolvela a MAIN con una recomendación.

## 1. Línea base confirmada por MAIN

La rama especializada debe partir del commit que incorpora este prompt sobre la base funcional `7cd1cae`. Al iniciar, verificá ruta, rama, `git status --short`, `git rev-parse HEAD` y `git worktree list`. No adaptes silenciosamente la tarea a otra base.

La aplicación ya contiene:

- calendario mensual reactivo con guardias múltiples y estados temporales derivados;
- carga, edición y eliminación de guardias;
- objetivos, horarios, instantáneas históricas y selector RGB completo;
- motor mensual de horas;
- novedades, notas privadas y feriados;
- vacaciones y fotos mensuales;
- zoom interno 100 %, 150 % y 200 %;
- Room versión 4 con 11 entidades y migraciones `1→2`, `2→3` y `3→4`;
- una tarjeta provisional arriba del calendario que todavía dice que el próximo evento se incorporará más adelante;
- composición manual mediante `MiGuardiaApplication`, `LocalDataStore` y ViewModels explícitos.

Contratos relevantes existentes:

- `ShiftRepository.observeStartingBetween(...)` observa guardias por `localStartDate`;
- `ExplicitDayStatusRepository.observeBetween(...)` observa `DAY_OFF` y `UNDEFINED` explícitos;
- `VacationRepository.observeOverlapping(...)` observa períodos inclusivos;
- `Shift.temporalStatusAt(now)` deriva `UPCOMING`, `IN_PROGRESS` y `COMPLETED` para `PLANNED`, preservando `CANCELLED` y `ABSENT`;
- `CalendarViewModel` observa únicamente el mes visible y no debe convertirse en una fuente global incompleta.

La última batería verificada por MAIN aprobó 94 pruebas JVM, 60 instrumentadas de app y 38 instrumentadas de Room: 192 pruebas, 0 fallos. Esos conteos son históricos; repetí la batería y devolvé los nuevos totales exactos.

## 2. Objetivo del incremento

Entregar un motor que:

1. detecte todas las guardias `PLANNED` actualmente en curso;
2. determine la próxima guardia o las guardias que comienzan en el mismo instante;
3. encuentre el próximo franco marcado explícitamente como `DAY_OFF`;
4. calcule tiempos restantes con un `Instant` y una zona inyectables;
5. se actualice al cambiar datos o cruzar límites temporales;
6. sea independiente del mes que el usuario está mirando;
7. exponga un resultado reutilizable por la aplicación, las futuras notificaciones y los futuros widgets;
8. reemplace la tarjeta provisional por información real arriba del calendario.

Este incremento implementa el motor y su presentación dentro de la aplicación. **No implementa todavía notificaciones, alarmas, widgets ni clima.**

## 3. Contrato funcional congelado

### 3.1 Fuente temporal

- Usá `Instant` para comparar guardias y `America/Argentina/Cordoba` mediante `AppDefaults.zoneId()` para fechas civiles.
- El reloj debe ser un `Clock` inyectable. No escondas `Instant.now()`, `LocalDate.now()` ni la zona del equipo dentro de lógica pura.
- Inicio inclusivo: si `now == startAt`, la guardia está en curso.
- Fin exclusivo: si `now == endAt`, ya no está en curso ni es futura.
- Una guardia nocturna iniciada ayer permanece en curso hasta su `endAt` real.
- Ningún cálculo temporal escribe `COMPLETED` ni otra marca en Room.

### 3.2 Guardias candidatas

Una guardia es candidata de trabajo próximo únicamente si:

- `status == PLANNED`;
- `endAt > now`;
- su `localStartDate` no está incluida en un período de vacaciones.

Consecuencias:

- `CANCELLED` y `ABSENT` conservan su clasificación persistida, pero no aparecen como guardia en curso o próxima;
- una guardia `PLANNED` dentro de vacaciones permanece guardada e históricamente visible, pero no se anuncia como trabajo próximo;
- no se modifican guardias, vacaciones ni novedades para resolver una coexistencia;
- las guardias se ordenan de manera estable por `startAt`, luego `endAt` y finalmente UUID;
- si dos guardias están en curso, ambas permanecen en el resultado;
- si varias guardias futuras comienzan en el mismo instante mínimo, ninguna se descarta.

### 3.3 Franco explícito

- Próximo franco significa únicamente una fila `ExplicitDayStatusType.DAY_OFF` cuya fecha sea hoy o posterior.
- Un día implícitamente vacío y `UNDEFINED` no cuentan como franco.
- Si existen varios, elegí la fecha mínima y usá el orden natural de `LocalDate`.
- `DAY_OFF` puede coexistir con una guardia o con Vacaciones porque el modelo vigente conserva esos datos. El motor no borra ni corrige esa coexistencia.

### 3.4 Resultado único y prioridad visual

Definí en `core/domain` un resultado inmutable equivalente a:

- instante de referencia;
- lista ordenada de guardias en curso;
- lista ordenada de próximas guardias que comparten el instante de inicio mínimo;
- próximo franco explícito opcional;
- evento principal;
- duración no negativa hasta el inicio o fin aplicable.

Podés elegir nombres Kotlin idiomáticos, pero preservá estas reglas del evento principal:

1. si hay una o más guardias en curso, el evento principal es **guardia en curso**;
2. si no hay ninguna en curso pero hay guardias futuras, es **próxima guardia**;
3. si no hay guardias candidatas pero existe un `DAY_OFF` futuro o de hoy, es **próximo franco**;
4. en cualquier otro caso es **sin próximos eventos**.

Aunque el evento principal sea una guardia, el resultado conserva el próximo franco para que la aplicación, notificaciones y widgets puedan consumir la misma proyección sin repetir reglas.

### 3.5 Tiempo restante

- Guardia próxima: duración entre `now` y `startAt`.
- Guardia en curso: duración entre `now` y `endAt`.
- Nunca expongas duraciones negativas.
- Para el franco usá semántica de fecha civil: `Hoy`, `Mañana` o una cantidad de días, sin inventar un horario laboral.
- La interfaz usa precisión humana de días, horas y minutos; no necesita un contador por segundo.
- Al alcanzar exactamente el inicio o el fin, el estado debe cambiar de inmediato según los límites anteriores.

## 4. Arquitectura requerida

### 4.1 Lógica pura de dominio

Creá un paquete cohesivo, por ejemplo `core/domain/.../nextevent`, con:

- los tipos del resultado y del evento principal;
- funciones puras que filtren, ordenen y proyecten guardias, francos y vacaciones;
- una función pública única que futuros consumidores puedan reutilizar;
- formateo temporal de presentación fuera del modelo persistido, preferentemente en app o en una función pura separada.

No importes Android, Compose, Room ni recursos en `core/domain`. Reutilizá la regla temporal vigente o extraé una primitiva compartida sin alterar su semántica.

### 4.2 Consultas reactivas autorizadas

Los contratos mensuales actuales no alcanzan para encontrar eventos fuera del mes visible. MAIN autoriza ampliar, con nombres idiomáticos equivalentes:

- `ShiftRepository`: observar guardias cuyo `endAt` sea posterior a un instante recibido;
- `ExplicitDayStatusRepository`: observar estados explícitos desde una fecha inclusiva;
- `VacationRepository`: observar períodos cuyo fin inclusivo sea igual o posterior a una fecha.

Implementá las consultas correspondientes en DAO y repositorios Room con orden estable. Podés filtrar `DAY_OFF` y estados de guardia en dominio para conservar un contrato general coherente.

Estas consultas **no cambian el esquema**. Está prohibido modificar entidades, columnas, índices, migraciones, versión de base o JSON de esquemas. Room debe continuar exactamente en versión 4 con 11 entidades.

### 4.3 Observador y estado de aplicación

Creá un observador/caso de uso de aplicación que combine los tres `Flow` y produzca el resultado puro. Después incorporá un `NextEventViewModel` o nombre equivalente con:

- `Clock` y zona inyectables;
- estado inmutable de carga, contenido y error recuperable;
- acción `retry`;
- actualización reactiva ante alta, edición, eliminación o corrección de guardias, cambios de `F` y cambios de Vacaciones;
- conservación de los últimos datos válidos cuando un error recuperable lo permita;
- cancelación correcta al destruirse el ViewModel.

El motor no depende de `CalendarUiState.visibleMonth`. Cambiar el mes visible no cambia cuál es el próximo evento.

Para mantener el tiempo restante actualizado:

- programá el próximo inicio, fin, medianoche local o cambio de minuto relevante;
- no uses un bucle por segundo;
- evitá trabajo permanente cuando la interfaz no está siendo observada;
- cancelá y recalculá la espera cuando Room emita nuevos datos;
- mantené la estrategia determinista con reloj falso en pruebas.

### 4.4 Composición

- Conservá la composición manual existente; no agregues Hilt ni un service locator global mutable.
- `MainActivity` puede crear el nuevo ViewModel desde los repositorios publicados por `LocalDataStore`.
- Compose recibe estado y eventos; no consulta Room ni el reloj directamente.
- No mezcles el motor dentro de `CalendarViewModel`: ambos pueden compartir primitivas puras, pero tienen responsabilidades y horizontes de consulta distintos.

## 5. Experiencia arriba del calendario

Reemplazá la tarjeta provisional existente, conservando su posición arriba de la navegación mensual.

### Guardia en curso

Mostrá como mínimo:

- título `Guardia en curso`;
- nombre completo y abreviatura histórica del objetivo;
- fecha argentina `DD/MM/AAAA`;
- horario histórico completo `HH:mm–HH:mm`;
- puesto opcional si existe;
- texto equivalente a `Termina en 5 h 20 min`;
- franja del color histórico acompañada siempre por texto.

Si hay más de una guardia en curso, informá la cantidad sin perder la primera guardia ordenada como resumen principal.

### Próxima guardia

Mostrá como mínimo:

- título `Próxima guardia`;
- objetivo completo y abreviatura histórica;
- fecha argentina `DD/MM/AAAA`;
- horario histórico completo;
- puesto opcional;
- texto equivalente a `Comienza en 1 d 4 h 30 min`.

Si varias comienzan en el mismo instante, indicá la cantidad y preservalas en el estado.

### Próximo franco y estado vacío

- Si no hay guardia candidata pero existe `DAY_OFF`, mostrá `Próximo franco` y su fecha argentina.
- Si el evento principal es una guardia y hay un franco conocido, mostrá una línea secundaria breve con su fecha.
- Si no existe ningún evento, mostrá `Sin próximos eventos` y una explicación honesta que no confunda día vacío con franco.
- Un error del motor no debe ocultar ni inutilizar el calendario. Mostrá un mensaje persistente y una acción `Reintentar` dentro de esta superficie.

### Presentación y zoom

- Reutilizá el sistema visual compartido (`SectionCard`, mensajes persistentes y jerarquías existentes) cuando corresponda.
- Conservá funcionamiento en tema claro y oscuro, retrato y paisaje.
- La tarjeta debe seguir siendo desplazable y legible con zoom interno 100 %, 150 % y 200 %.
- No leas ni modifiques `font_scale`, densidad, zoom o tamaño de visualización de Android.
- Mantené semántica básica que describa tipo de evento, objetivo, fecha, horario y tiempo restante; no diseñes un flujo especial separado para TalkBack.
- No expongas notas, descripciones privadas de ausencia/cancelación, notas médicas ni contenido de fotos.

## 6. Fuera de alcance

No implementes en este incremento:

- permisos de notificaciones;
- canales, `NotificationManager`, alarmas, workers, receivers o servicios;
- configuración de anticipación 6/8/12/24 horas;
- notificaciones persistentes, acciones o contenido de pantalla bloqueada;
- reprogramación tras reinicio o cambio de hora;
- widgets;
- clima, red o ubicación;
- navegación a mapas;
- onboarding;
- informes, copias de seguridad o remuneración;
- nuevas tablas o preferencias persistidas;
- cambios funcionales al calendario, horas, fotos, feriados, novedades o vacaciones;
- datos reales o cronogramas personales.

No agregues permisos, componentes de manifiesto, dependencias de producción, claves, telemetría, cuentas, nube ni sincronización.

## 7. Archivos permitidos

Podés crear o modificar únicamente:

- `core/domain/src/main/**` para tipos y funciones del motor y las ampliaciones autorizadas de repositorios;
- `core/domain/src/test/**` para lógica pura;
- `core/database/src/main/**` solamente en DAO y repositorios necesarios para las consultas nuevas;
- `core/database/src/androidTest/**` para pruebas Room aisladas;
- `app/src/main/**` para observador, estado, ViewModel, composición, tarjeta y textos;
- `app/src/test/**` y `app/src/androidTest/**` para pruebas del motor y la interfaz;
- `docs/adr/0009-motor-de-proximo-evento.md` si necesitás registrar la arquitectura final implementada.

No modifiques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md` ni este prompt;
- ADR existentes;
- entidades Room, `MiGuardiaDatabase` o `Migrations.kt`;
- archivos de esquema JSON;
- Gradle, catálogo de versiones o dependencias;
- manifiesto, permisos o firma;
- módulos de fotos o almacenamiento de imágenes;
- archivos reales ignorados.

Si el estado real exige salir de estos límites, frená esa parte y pedí autorización a MAIN antes de editar.

## 8. Pruebas obligatorias

### 8.1 JVM: dominio

Con reloj, zona, UUID y datos ficticios deterministas, probá como mínimo:

1. guardia futura produce próxima y duración hasta `startAt`;
2. instante exacto de inicio produce en curso;
3. instante exacto de fin elimina la guardia de curso;
4. guardia nocturna iniciada ayer permanece en curso;
5. guardia completada no es candidata;
6. `CANCELLED` y `ABSENT` nunca son trabajo próximo;
7. guardia `PLANNED` dentro de vacaciones queda excluida sin mutarse;
8. una guardia posterior a vacaciones sí puede ser próxima;
9. dos guardias simultáneas en curso se conservan y ordenan;
10. varias guardias con el mismo próximo inicio se conservan y ordenan;
11. desempate estable por inicio, fin y UUID;
12. `DAY_OFF` de hoy se reconoce como franco explícito;
13. próximo `DAY_OFF` usa la fecha mínima;
14. `UNDEFINED` y día sin fila no cuentan como franco;
15. un `DAY_OFF` coexistente con guardia permanece en el resultado secundario;
16. prioridad principal: en curso, luego próxima guardia, luego franco, luego vacío;
17. duraciones nunca negativas;
18. cruce de medianoche, fin de mes, fin de año y febrero bisiesto;
19. zona Córdoba independiente de la zona del equipo;
20. la proyección no modifica modelos persistidos.

### 8.2 Room aislado

Usá una base con nombre UUID o una base en memoria de prueba. Nunca uses el nombre productivo.

Probá como mínimo:

1. consulta de guardias con `endAt` posterior al instante;
2. orden estable y dos guardias excepcionales;
3. actualización reactiva al insertar, editar, cancelar y eliminar;
4. estados explícitos desde fecha inclusiva;
5. períodos de vacaciones con fin inclusivo desde una fecha;
6. cierre y reapertura conservando datos;
7. versión Room 4 y 11 entidades sin cambios;
8. hashes/esquemas v1, v2, v3 y v4 idénticos a la base.

### 8.3 Aplicación y Compose

Probá como mínimo:

1. carga inicial y error recuperable con `Reintentar`;
2. guardia en curso con objetivo, fecha, horario y cuenta restante;
3. próxima guardia con formato argentino y horario completo;
4. varias guardias simultáneas anunciadas sin perder datos;
5. próximo franco como evento principal cuando no hay guardias;
6. franco secundario junto con próxima guardia;
7. estado sin eventos honesto;
8. cambio reactivo al editar o eliminar una guardia;
9. transición exacta próxima → en curso → sin curso mediante reloj falso;
10. cambiar el mes del calendario no altera el próximo evento;
11. un error del motor no oculta el calendario;
12. tema claro y oscuro;
13. zoom interno 100 %, 150 % y 200 % sin cortes ni acciones inaccesibles;
14. recreación normal sin datos ficticios persistidos;
15. ausencia de notas privadas, contenido médico y fotos en la tarjeta.

## 9. Verificación integral obligatoria

No aceptes los conteos históricos como prueba nueva. Ejecutá desde tu worktree con un único worker:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest
```

Para la app en el Samsung, no uses `connectedDebugAndroidTest` contra el paquete principal con datos existentes. Usá instalación por actualización y runner manual:

```powershell
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am instrument -w -r com.blackatsystems.miguardia.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.blackatsystems.miguardia.test
```

Si la firma instalada lo exige, usá el keystore local ignorado ya existente mediante la propiedad admitida por el proyecto. No modifiques Gradle ni desinstales la aplicación principal para resolver firma.

Para Room, ejecutá de manera aislada:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
```

Obtené conteos exactos desde XML. Informá JVM, app instrumentada, Room instrumentado, fallos, errores, omitidas, lint, debug y release por separado.

En el Samsung `SM-S938B` verificá el recorrido con datos ficticios, tema claro/oscuro, retrato/paisaje y zoom interno 100/150/200. No consultes ni modifiques configuraciones visuales del sistema. Restaurá cualquier estado autorizado que hayas cambiado y eliminá solamente paquetes/datos QA.

## 10. Criterios de aceptación

El incremento está terminado únicamente si:

- la tarjeta superior muestra información real y reactiva;
- el motor es independiente del mes visible;
- dominio, aplicación y futuros consumidores comparten una única proyección;
- inicios, finales y medianoche cambian de estado correctamente;
- varias guardias no se pierden;
- `DAY_OFF` es el único franco válido;
- canceladas, ausentes y guardias normales dentro de vacaciones no se anuncian como trabajo;
- editar, borrar o agregar datos actualiza el resultado;
- el calendario sigue funcionando si el motor falla;
- no se agregaron permisos, red, alarmas, servicios ni dependencias;
- Room continúa idéntico en versión 4 con 11 entidades;
- todos los tests existentes y nuevos pasan;
- `git diff --check` no informa errores;
- no hay secretos, datos reales, logs sensibles ni artefactos;
- la documentación coincide con lo implementado.

## 11. Entrega a MAIN

No hagas commit, push, merge ni abras otra tarea salvo instrucción explícita de Joaquin o MAIN.

Al finalizar, devolvé en español:

- resultado funcional concreto;
- decisiones técnicas tomadas y cómo preservan este contrato;
- archivos modificados y nuevos;
- cada consulta de repositorio agregada;
- pruebas exactas y conteos obtenidos;
- recorrido físico real en Samsung;
- hashes y estado de Room v4;
- permisos, dependencias, privacidad y seguridad;
- defectos encontrados o limitaciones;
- `git status --short`, `git diff --stat` y `git diff --check`;
- un prompt de integración completo para MAIN con ruta del worktree, rama, base y HEAD.

MAIN auditará cada hunk, repetirá la batería completa y decidirá la integración. Esta dependencia no puede redefinir contratos ni reglas de negocio.
