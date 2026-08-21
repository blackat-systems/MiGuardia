# Prompt maestro de dependencia — CALENDARIO MENSUAL, incremento 2

> **HISTÓRICO V1 — NO EJECUTAR.** Contrato ya integrado y superado por la
> experiencia actual. Ver `docs/prompts/README.md`.

> Estado: implementado, integrado y verificado por MAIN el 2026-08-13
>
> Proyecto: MiGuardia
>
> Dependencia: CALENDARIO MENSUAL
>
> Fecha: 2026-08-13

## 0. Rol y autoridad

Sos la dependencia especializada **CALENDARIO MENSUAL** de MiGuardia. Tu misión es convertir el calendario estático existente en una superficie mensual real, reactiva, accesible y conectada con la persistencia local ya aprobada.

Antes de planificar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
4. `docs/adr/0002-persistencia-local-v1.md`;
5. `docs/audits/2026-08-13-auditoria-integral.md`;
6. `docs/prompts/DATA_LOCAL.md`;
7. este prompt;
8. todo el código y las pruebas de `app`, `core/domain` y las interfaces públicas de `core/database` que intervengan en el calendario.

Jerarquía: una instrucción explícita actual de Joaquin, luego `docs/PROMPT_MAESTRO_MAIN.md`, luego `AGENTS.md`, después los ADR y este documento, y finalmente la implementación existente.

No redefinas el producto. Si encontrás una contradicción o falta una decisión funcional que cambie lo que verá Joaquin, detené únicamente esa parte, explicá el caso en español y devolvelo a MAIN con una recomendación. No inventes reglas silenciosamente ni modifiques contratos compartidos fuera de la autorización expresa de este documento.

## 1. Punto de partida confirmado por MAIN

La línea base auditada está integrada en `main` y contiene:

- aplicación Android en Kotlin y Jetpack Compose;
- `compileSdk/targetSdk 37`, `minSdk 26`, código compatible con Java 17;
- AGP 9.3.1, Gradle 9.5.0, Kotlin/Compose plugin 2.3.21 y Compose BOM 2026.08.00;
- módulos `:app`, `:core:domain` y `:core:database`;
- Room 2.8.4 con esquema versión 1 exportado y cinco tablas;
- contratos públicos `ShiftRepository`, `ExplicitDayStatusRepository` y `MedicalLeaveRepository`, suficientes para observar un intervalo mensual;
- `LocalDataStore` como acceso público a esos repositorios;
- modelos `Shift`, `ExplicitDayStatus` y `MedicalLeave` con fechas e instantes fuertes de `java.time`;
- una pantalla Compose inicial con calendario estático de 42 celdas, controles anterior/siguiente/Hoy, tarjeta provisional de próxima guardia y tres destinos inferiores;
- pruebas JVM, Room e interfaz ya aprobadas en el Samsung Galaxy S25 Ultra SM-S938B/API 36.

No asumas que existen Hilt, Navigation 3 en runtime, DataStore en runtime, un motor de próximo evento, formularios de carga ni casos de uso de calendario. Inspeccioná el estado real antes de actuar.

## 2. Objetivo del incremento

Entregar un calendario mensual **de consulta**, conectado a los datos reales locales, que permita:

- abrir inicialmente el mes actual de `America/Argentina/Cordoba`;
- cambiar al mes anterior o siguiente con controles visibles y gesto horizontal;
- volver al mes actual con Hoy;
- conservar el mes visible ante recomposición y recreación de actividad;
- observar reactivamente las guardias cuya fecha local inicial pertenece al mes;
- observar estados diarios explícitos y carpetas médicas que intersecten el mes;
- proyectar cada fecha, incluidas las fechas sin fila persistida;
- mostrar guardias, `F`, `?` y `CM` sin depender únicamente del color;
- mostrar correctamente varias guardias del mismo día;
- abrir una consulta básica, no editable, de la fecha seleccionada;
- derivar de forma determinista si una guardia está próxima, en curso, completada, cancelada o ausente;
- tratar automáticamente como completadas las guardias retrocargadas cuyo horario ya terminó;
- representar carga, contenido y error recuperable sin perder el mes elegido;
- seguir funcionando completamente sin internet.

Este incremento no crea ni edita guardias, objetivos, horarios, francos, indefinidos o carpetas médicas. Es una base de lectura real para que el siguiente incremento incorpore esos flujos sin reescribir el calendario.

## 3. Regla funcional congelada: guardias pasadas

Joaquin decidió explícitamente el 13 de agosto de 2026 que una guardia cargada después de haber ocurrido no requiere confirmación manual. Esto incluye guardias de días anteriores del mes actual y de meses anteriores.

Mientras `Shift.status` sea `PLANNED`, derivá el estado temporal usando `Shift.startAt`, `Shift.endAt` y un instante actual inyectable:

- `UPCOMING`: `now < startAt`;
- `IN_PROGRESS`: `startAt <= now < endAt`;
- `COMPLETED`: `now >= endAt`.

Los estados persistidos explícitos prevalecen:

- `CANCELLED` siempre se proyecta como cancelada;
- `ABSENT` siempre se proyecta como ausencia.

Consecuencias obligatorias:

- si hoy se inserta una guardia cuyo `endAt` ya pasó, el primer estado observado debe ser completada;
- no pedir confirmación adicional;
- no agregar `COMPLETED` a `ShiftStatus` ni a Room;
- no escribir en la base solo porque pasó el tiempo;
- una corrección posterior a ausencia o cancelación reemplaza visualmente el estado derivado;
- una guardia nocturna iniciada ayer que todavía no llegó a `endAt` sigue en curso, aunque haya cambiado el día civil;
- los límites de inicio son inclusivos y los de fin también producen inmediatamente completada;
- reloj y zona deben inyectarse en la lógica para que las pruebas no dependan de la hora real.

Esta proyección es una regla de dominio reutilizable. No la escondas dentro de un `@Composable` ni la dupliques en varios componentes.

## 4. Proyección del mes y reglas por fecha

### Intervalo mensual

Para cada `YearMonth` visible:

- consultá guardias desde el primer hasta el último `LocalDate` del mes, ambos inclusive, mediante `ShiftRepository.observeStartingBetween`;
- consultá estados explícitos con el mismo intervalo mediante `ExplicitDayStatusRepository.observeBetween`;
- consultá carpetas médicas que intersecten el intervalo mediante `MedicalLeaveRepository.observeIntersecting`;
- cancelá las observaciones del mes anterior al cambiar de mes, evitando recolectores acumulados;
- no hagas consultas SQL desde Compose ni accedas a DAO internos.

### Ubicación de guardias

- Una guardia se dibuja únicamente en `localStartDate`.
- Una guardia 19:00–07:00 no agrega una marca al día siguiente.
- Dentro de una fecha, las guardias se ordenan por `startAt` y luego por identificador como desempate estable.
- Nunca supongas una sola guardia por fecha.

### Día vacío y estados explícitos

- Una fecha sin guardia, estado explícito ni carpeta médica se muestra visualmente como `?` implícito.
- Una fila `UNDEFINED` también se muestra `?`, pero el modelo de presentación y la descripción accesible deben poder distinguir que fue marcada explícitamente.
- `DAY_OFF` se muestra como `F` y solo es franco explícito cuando existe la fila.
- Cada fecha incluida entre `MedicalLeave.startDate` y `endDateInclusive` muestra `CM`.
- No persistas una fila `UNDEFINED` para rellenar días vacíos.

### Coexistencias y datos excepcionales

La versión 1 de Room permite ciertas coexistencias para no destruir realidades excepcionales. La vista de lectura no debe ocultar datos:

- si hay dos guardias en una fecha, ambas deben estar disponibles y anunciarse;
- si una guardia coexiste con `F`, `?` explícito o `CM`, mostrala junto con los marcadores presentes;
- si el espacio visual no alcanza, usá un indicador de cantidad adicional, pero el detalle y la semántica accesible deben enumerar todo;
- no corrijas, borres ni resuelvas automáticamente una coexistencia desde este módulo.

## 5. Experiencia visual e interacción

### Estructura principal

Conservá los tres destinos inferiores aprobados: Calendario, Resumen y Configuración. Resumen y Configuración pueden continuar como estados vacíos honestos.

En Calendario:

- mostrale al usuario mes y año en español;
- conservá flechas anterior/siguiente y botón Hoy;
- agregá un gesto horizontal inequívoco para cambiar de mes;
- mantené una alternativa accesible mediante botones: el gesto nunca puede ser la única forma;
- evitá que un arrastre vertical accidental cambie de mes;
- conservá el lunes como primera columna;
- mantené una cuadrícula estable de seis semanas/42 posiciones para evitar saltos bruscos entre meses;
- las posiciones fuera del mes no deben anunciarse como días interactivos.

La tarjeta superior de próxima guardia pertenece al futuro MOTOR DE PRÓXIMO EVENTO. No lo implementes parcialmente consultando solo el mes visible, porque podría dar un resultado falso. Mientras tanto, su texto debe ser neutral y honesto; nunca debe afirmar que no hay guardias si el calendario ya está mostrando alguna.

La acción Agregar debe continuar visible pero claramente no operativa hasta el incremento OBJETIVOS Y GUARDIAS. Puede permanecer deshabilitada. No simules un guardado ni muestres un éxito ficticio. Los botones de fotos, menú mensual, selección múltiple, feriados e informes también quedan fuera de este encargo.

### Contenido de las celdas

- Mostrá el número de día con jerarquía clara.
- Para una guardia, mostrá una franja o indicador pequeño con `colorArgbSnapshot`, más abreviatura y horario exacto tomados de la instantánea histórica.
- La forma visual objetivo es equivalente a `RAW 19:00–07:00`. Por decisión posterior de Joaquin, la celda debe mostrar siempre la abreviatura histórica completa y el horario exacto completo, sin elipsis: ambos ocupan líneas propias y el estado temporal se muestra por separado.
- La franja de `colorArgbSnapshot` debe ser claramente perceptible. Una fecha sin prioridad visual de Vacaciones usa fondo verde de completada únicamente cuando contiene guardias y todas ellas están proyectadas como `COMPLETED`.
- Para cancelación o ausencia, mostrá además una palabra o abreviatura comprensible; no lo comuniques solo cambiando color u opacidad.
- Para guardia completada, próxima o en curso, la descripción accesible debe nombrar el estado.
- El día actual debe distinguirse visualmente y anunciarse como “hoy”.
- `F`, `?` y `CM` deben conservar su texto visible, no reemplazarse por color solamente.
- No expongas notas médicas ni datos privados en la cuadrícula o en logs.

### Fecha seleccionada

Al tocar una fecha válida, abrí una superficie de consulta básica —por ejemplo, panel o `ModalBottomSheet`— que muestre solo lo ya disponible:

- fecha completa;
- guardias de ese día con abreviatura, objetivo, horario, puesto opcional y estado temporal;
- dirección solo como texto si existe; no agregues todavía integración con mapas;
- `F` o `?` explícito cuando corresponda;
- presencia de `CM` sin mostrar automáticamente la nota médica privada;
- mensaje claro de “sin definir” para una fecha realmente vacía.

Esta superficie es de solo lectura. No agregues editar, eliminar, duplicar, informar novedad ni cálculos de nocturnidad/feriado. Prepará eventos o límites composables claros para que esos flujos puedan sumarse después sin acoplarlos a la cuadrícula.

## 6. Arquitectura y estado

### Lógica de dominio autorizada

MAIN autoriza agregar dentro de `core/domain` tipos y funciones **nuevos y exclusivos de proyección de calendario** que cubran estas capacidades:

- un estado temporal visible con valores equivalentes a `UPCOMING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` y `ABSENT`;
- una función pura que proyecte un `Shift` a ese estado para un `Instant` recibido;
- una representación inmutable de una fecha de calendario con su lista ordenada de guardias, estado diario explícito opcional y presencia de carpeta médica;
- una función pura que construya los días de un `YearMonth` a partir de las listas observadas.

Podés elegir nombres idiomáticos coherentes con el repositorio. No estás autorizado a:

- modificar campos de `Shift`, `ExplicitDayStatus` o `MedicalLeave`;
- agregar valores a `ShiftStatus`;
- cambiar firmas de repositorios existentes;
- importar Android o Compose en la lógica pura;
- introducir llamadas ocultas a `Instant.now()`, `LocalDate.now()` o generación de UUID dentro de proyectores.

### Estado de pantalla

Usá flujo unidireccional y un `ViewModel` o controlador equivalente con estado inmutable. Debe existir una única fuente de verdad para:

- mes visible;
- instante de referencia actual;
- carga, contenido o error;
- días proyectados;
- fecha seleccionada.

Requisitos:

- Compose recibe estado y emite eventos; no consulta Room directamente;
- la dependencia de repositorios y `Clock` es explícita e inyectable;
- usá `collectAsStateWithLifecycle` o la API estable equivalente ya disponible;
- el mes se conserva al recrear la actividad;
- un cambio emitido por Room actualiza la cuadrícula sin reiniciar la pantalla;
- si la app permanece abierta al cruzar el inicio o fin de una guardia, el estado visible se actualiza con una estrategia liviana y cancelable;
- no implementes un motor global de próximo evento ni un sondeo constante de alta frecuencia;
- los errores se muestran en español con acción Reintentar, sin borrar los últimos datos válidos si pueden conservarse;
- cerrar la pantalla o cambiar de mes cancela el trabajo que ya no corresponde.

La composición de dependencias continúa manual. Podés crear una clase `Application` propia que sea dueña de un único `LocalDataStore` para el proceso y declararla en el manifiesto. No agregues Hilt ni un localizador global mutable difícil de sustituir en pruebas. Si elegís otra composición manual, justificá por qué es más simple y mantené un punto de inyección para pruebas.

## 7. Accesibilidad, adaptabilidad y privacidad

La superficie está terminada solamente si:

- funciona en tema claro y oscuro;
- se mantiene utilizable con fuente Android al 200 %;
- no corta silenciosamente información esencial;
- cada fecha interactiva tiene una descripción completa en español;
- TalkBack anuncia fecha, si es hoy, estado implícito o explícito y todos los eventos relevantes;
- las franjas de color tienen contraste suficiente y siempre están acompañadas por texto;
- las áreas táctiles respetan tamaños razonables aun cuando la celda visual sea compacta;
- retrato es la orientación principal y paisaje no rompe el flujo;
- el desplazamiento necesario con fuente grande no bloquea las acciones del mes;
- no hay datos laborales, direcciones, notas ni cronogramas en logs;
- no se agregan permisos, internet, analítica, telemetría ni servicios externos.

Usá datos ficticios para previews y pruebas. No leas ni copies el cronograma real ignorado del repositorio.

## 8. Fuera de alcance

No implementes en este encargo:

- alta, edición o eliminación de objetivos y combinaciones;
- carga simple o múltiple de guardias;
- recientes, duplicado, selección múltiple o sobrescritura;
- advertencia de descanso menor a 12 horas;
- creación o edición de `F`, `?` o `CM`;
- novedades, notas o cambios finales de horario/objetivo;
- fotos del cronograma;
- feriados;
- horas, nocturnidad, extras o remuneración;
- motor definitivo de próxima guardia;
- notificaciones, clima o widgets;
- informes o copias de seguridad;
- navegación a mapas;
- onboarding;
- cambios de esquema Room o migraciones;
- datos de demostración persistidos en producción.

No uses este incremento para “adelantar” módulos posteriores. Sí dejá límites claros y comprobables para que MAIN pueda integrarlos después.

## 9. Dependencias

No agregues una dependencia de producción nueva. La base existente ya proporciona Compose, Material 3, Lifecycle, ViewModel, coroutines, Room y `java.time`.

Está autorizado activar para pruebas los artefactos de Compose UI Test que ya figuran en `gradle/libs.versions.toml`, si son necesarios:

- `androidx-compose-ui-test-junit4`;
- `androidx-compose-ui-test-manifest`.

No actualices versiones ni actives Navigation 3, DataStore, Hilt u otra biblioteca. Si una necesidad real no puede resolverse con la base, detené esa parte y pedí autorización a MAIN incluyendo necesidad, mantenimiento, licencia, privacidad, tamaño y alternativa nativa.

## 10. Archivos permitidos

Podés crear o modificar únicamente:

- `app/src/main/**` para composición de dependencias, estado, ViewModel, UI y recursos del calendario;
- `app/src/test/**` y `app/src/androidTest/**` para pruebas del incremento;
- `app/build.gradle.kts` solo para activar dependencias de prueba ya aprobadas;
- `core/domain/src/main/**` únicamente para los nuevos proyectores/tipos de calendario autorizados en la sección 6;
- `core/domain/src/test/**` para probar esa lógica pura;
- documentación técnica nueva propia del módulo, solo si una decisión reversible necesita quedar explicada.

No toques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md`;
- `docs/prompts/DATA_LOCAL.md` ni este prompt;
- ADR existentes;
- entidades, DAO, mapeadores, repositorios o esquema de `core/database`;
- firmas de los contratos actuales de `core/domain`;
- `gradle/libs.versions.toml` salvo autorización posterior de MAIN;
- datos o cronogramas reales;
- configuración de Git, firma o secretos.

Si el estado real exige salir de estos límites, pedí autorización antes de editar. No hagas una modificación provisional fuera de alcance.

## 11. Pruebas obligatorias

### JVM: lógica pura

Con reloj fijo y datos ficticios, probá como mínimo:

1. `now` anterior al inicio produce próxima;
2. `now` exactamente en el inicio produce en curso;
3. `now` exactamente en el fin produce completada;
4. una guardia retrocargada de un día o mes anterior produce completada en su primera proyección;
5. cancelación y ausencia prevalecen aunque el horario haya terminado;
6. una guardia nocturna iniciada ayer permanece en curso antes de su fin;
7. una guardia nocturna solo pertenece a su fecha local inicial;
8. dos guardias del mismo día quedan ordenadas y ninguna se pierde;
9. un día sin filas es indefinido implícito y se distingue de `UNDEFINED` explícito;
10. `DAY_OFF` solo aparece con fila explícita;
11. una carpeta médica inclusiva cruza fin de mes y marca únicamente las fechas intersectadas;
12. febrero bisiesto, cambio de mes y cambio de año generan fechas correctas;
13. la proyección usa `America/Argentina/Cordoba` sin depender de la zona del equipo de prueba;
14. proyectar completada no modifica el `Shift.status` persistido `PLANNED`.

### Interfaz e integración

Usá un punto de inyección o estado falso; no contamines la base de producción del dispositivo. Demostrá como mínimo:

1. apertura en el mes fijado para la prueba;
2. flechas anterior/siguiente y Hoy;
3. gesto horizontal en ambos sentidos sin activación por un movimiento insuficiente;
4. conservación del mes visible tras recreación;
5. render de guardia con abreviatura y horario histórico;
6. render de `F`, `?` implícito, `?` explícito y `CM`;
7. dos guardias en una fecha accesibles desde la celda y el detalle;
8. etiquetas de completada, próxima, en curso, cancelada y ausencia;
9. apertura y cierre del detalle de una fecha;
10. estado de carga, error y Reintentar;
11. actualización de la cuadrícula al emitir nuevos datos;
12. descripciones accesibles completas sin depender del color.

Con la integración real, verificá además que los tres `Flow` del mes se combinen correctamente y que cambiar de mes cambie sus límites inclusivos. Reutilizá una base de prueba aislada o fakes; nunca borres ni reemplaces datos personales del teléfono.

### Dispositivo físico

Ejecutá las pruebas relevantes en el Samsung Galaxy S25 Ultra conectado y desbloqueado. Hacé una inspección visual con:

- tema claro;
- tema oscuro;
- tamaño de fuente habitual;
- tamaño de fuente 200 %;
- retrato y un control básico en paisaje.

Restaurá al finalizar cualquier ajuste del teléfono que hayas cambiado. Si el dispositivo no está disponible, informalo con precisión y no declares esta verificación como aprobada.

## 12. Criterios de aceptación

El incremento está terminado solo si:

- la app compila con el wrapper del repositorio;
- pasan todas las pruebas JVM del proyecto;
- pasan lint y las pruebas instrumentadas relevantes;
- el calendario usa datos observados de Room en producción, no fixtures;
- previews y pruebas usan solo información ficticia;
- la retrocarga pasada se proyecta automáticamente como completada sin escritura redundante;
- una guardia nocturna se dibuja solo en su fecha inicial;
- se representan correctamente vacío, error, `F`, `?`, `CM`, varias guardias y coexistencias;
- el calendario funciona offline y no agrega permisos;
- tema, fuente grande y TalkBack fueron verificados;
- el esquema Room versión 1 no cambió;
- no se alteraron contratos compartidos no autorizados;
- `git diff --check` no informa errores;
- el diff se limita al alcance y no contiene datos reales, secretos, logs sensibles ni artefactos generados.

Ejecutá, como mínimo, una batería equivalente a:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Si alguna tarea exige una adaptación por el entorno, documentá el comando exacto y el motivo. No confundas una compilación con una prueba ejecutada.

No hagas commit, push, merge ni abras otra tarea salvo instrucción explícita de Joaquin o MAIN.

## 13. Entrega a MAIN

Al finalizar, entregá en español claro:

- qué quedó funcionando para Joaquin;
- lista de archivos creados o modificados;
- decisiones técnicas reversibles tomadas;
- comandos ejecutados y resultado exacto, incluyendo cantidad de pruebas;
- resultado de la prueba física y configuraciones visuales revisadas;
- confirmación de que el esquema Room continúa en versión 1;
- confirmación de que no agregaste dependencias de producción, permisos ni datos reales;
- cualquier limitación o decisión que MAIN deba resolver;
- `git status` y resumen del diff, sin hacer commit ni push.

MAIN revisará el código, repetirá la batería integral y decidirá la incorporación. La tarea CALENDARIO MENSUAL no sustituye a MAIN ni puede redefinir el producto.

## 14. Integración verificada por MAIN — 13 de agosto de 2026

MAIN comparó la entrega del worktree especializado con la línea base `eec9024`, separó los cambios documentales preexistentes e integró únicamente los archivos técnicos atribuibles a CALENDARIO MENSUAL.

Resultado aprobado:

- proyección mensual pura en `core/domain`, sin dependencias Android ni cambios en contratos persistentes;
- `COMPLETED` derivado del reloj para guardias `PLANNED`, con `CANCELLED` y `ABSENT` como excepciones persistidas prioritarias;
- observación reactiva de guardias, estados diarios y carpetas médicas mediante los tres contratos Room existentes;
- estado de pantalla unidireccional con mes conservado mediante `SavedStateHandle`;
- actualización programada en inicios, finales y medianoches relevantes sin sondeo frecuente;
- cuadrícula Compose de 42 posiciones, navegación por controles y gestos, detalle de solo lectura y estados de carga/error;
- composición manual mediante una única instancia de `LocalDataStore` propiedad de `MiGuardiaApplication`;
- dependencias nuevas limitadas a artefactos de Compose UI Test ya aprobados en el catálogo.

Verificación integral ejecutada por MAIN:

```powershell
.\gradlew.bat --no-daemon --stacktrace clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

- build: correcto, 363 tareas;
- pruebas JVM: 15 aprobadas, 0 fallos;
- pruebas instrumentadas: 19 aprobadas, 0 fallos —11 Room y 8 de aplicación— en Samsung Galaxy S25 Ultra SM-S938B/API 36;
- lint: 0 errores y 2 avisos informativos de versiones disponibles;
- APK debug y release: generados correctamente;
- esquema Room: continúa idéntico en versión 1;
- inspección visual independiente de MAIN: aprobada en el S25 Ultra;
- sin dependencias de producción, permisos, telemetría, secretos, datos reales ni logs sensibles nuevos.
