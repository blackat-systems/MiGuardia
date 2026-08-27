# Prompt maestro de dependencia — CALENDARIO FINAL Y TARJETA SUPERIOR V2

> Estado: **HABILITADO / PRÓXIMA DEPENDENCIA**
>
> Fecha: 2026-08-27
>
> Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
>
> Rama: `codex/miguardia-2.0`
>
> Base funcional mínima y ancestro obligatorio:
> `80fe8e5f8fdc47d5236941e91a46ffc3b1faab61`

## QUÉ HACE

Termina la única grilla mensual de MiGuardia 2.0 para que jornadas, extras,
disponibilidad y marcadores comunes convivan de manera clara. También convierte
la tarjeta superior en un resumen desplegable del día actual: cerrada muestra
lo más importante y, al abrirla, enumera todas las jornadas de hoy, incluidas
las ya completadas.

## POR QUÉ EXISTE

El Calendario ya permite cargar, repetir, corregir y consultar jornadas, horario
real, extras y disponibilidad. Esos incrementos agregaron información de forma
acotada, pero todavía falta ordenar su presentación final y la tarjeta superior
sólo sabe mostrar un próximo evento. Esta dependencia une visualmente lo que ya
existe antes de construir el Resumen y de adaptar notificaciones, sin volver a
inventar datos ni crear otro calendario.

## 0. ROL Y AUTORIDAD

Sos la dependencia especializada **CALENDARIO FINAL Y TARJETA SUPERIOR V2**.
Trabajás directamente en el checkout compartido indicado arriba. No sos MAIN,
no redefinís el producto y no abrís otra tarea, rama, worktree o dependencia.

Tu única misión es completar la presentación del Calendario y de la tarjeta
superior conforme a este contrato. Al terminar devolvés un handoff a MAIN con
el checkout sin commit.

Antes de planificar o editar, leé completamente y en este orden:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las cuatro fichas de `docs/sectores/`, porque el vocabulario visible debe
   funcionar para Vigilancia privada, Policía, Enfermería y Medicina;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
9. `docs/adr/0006-novedades-feriados-y-notas.md`;
10. `docs/adr/0007-vacaciones-y-persistencia-local-v3.md`;
11. `docs/adr/0009-motor-de-proximo-evento.md`;
12. `docs/adr/0015-menu-lateral-como-navegacion-principal.md`;
13. `docs/adr/0026-base-room-exclusiva-v2-y-retiro-del-modo-v1.md`;
14. `docs/adr/0028-horario-real-y-extras-exactas-v2.md`;
15. `docs/adr/0029-reinicio-explicito-y-extras-independientes.md`;
16. `docs/adr/0030-disponibilidad-como-ventana-pasiva.md`, incluida su nota de
    secuencia posterior;
17. `docs/PROMPT_MAESTRO_MAIN.md` únicamente como contrato histórico heredado;
18. `docs/prompts/CALENDARIO_MENSUAL.md`;
19. `docs/prompts/MOTOR_DE_PROXIMO_EVENTO.md`;
20. `docs/prompts/UX_UI_CALENDARIO_ADAPTABLE_2_0.md`;
21. `docs/prompts/EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md`;
22. `docs/prompts/GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md`;
23. el código y todas las pruebas relacionadas de Calendario, próximo evento,
    horario real, extras y disponibilidad.

La instrucción actual de Joaquin y las fuentes activas de 2.0 tienen prioridad.
Los prompts históricos o cerrados explican contratos que deben preservarse,
pero no autorizan recuperar recorridos V1 ni ampliar este alcance.

Si aparece una contradicción funcional material, detené solamente esa parte,
explicala en español y devolvela a MAIN con una recomendación. No la resuelvas
inventando comportamiento.

## 1. PUERTA 0 OBLIGATORIA

Antes de modificar cualquier archivo, verificá en vivo:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git merge-base --is-ancestor 80fe8e5f8fdc47d5236941e91a46ffc3b1faab61 HEAD
git rev-parse v1.0.0^{}
git status --short --branch
git worktree list --porcelain
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
```

Condiciones:

- ruta exacta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama exacta: `codex/miguardia-2.0`;
- el `HEAD` debe contener este prompt y tener a `80fe8e5` como ancestro;
- `v1.0.0^{}` debe continuar en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- el checkout debe comenzar limpio;
- no debe existir otra dependencia implementando simultáneamente sobre el
  mismo checkout.

Detenete ante rama distinta, detached HEAD, base ausente, cambios sin dueño o
un prompt que el índice ya no marque como habilitado. No uses reset, checkout,
stash, clean ni descartes para forzar la puerta.

No inspecciones ni recuperes código desde worktrees históricos.

## 2. CONTEXT

Estado funcional de entrada:

- MiGuardia es exclusivamente V2 y empieza con datos limpios;
- existen exactamente cuatro rubros separados: Vigilancia privada, Policía,
  Enfermería y Medicina;
- existe una sola configuración laboral y una sola grilla mensual;
- consulta no escribe;
- primera apertura, lugares, tipos, plantillas y horarios están integrados;
- carga manual simple o múltiple, edición/eliminación exacta y recurrencias
  finitas están integradas;
- horario planificado, horario real y extras de jornada están separados;
- existen extras independientes, avance contra una referencia elegida por la
  persona y fecha consciente de reinicio;
- existen Guardia pasiva, Disponible para llamado o Retén como una única
  disponibilidad configurable;
- `F`, `?`, carpeta médica, vacaciones, feriados, fotos y notas son capacidades
  comunes preservadas;
- `ShiftStatus` conserva `PLANNED`, `CANCELLED` y `ABSENT`, pero este bloque no
  crea el flujo V2 que permita escribir ausencia o cancelación;
- la antigua pantalla y las tablas V1 de Novedades y cambios formales fueron
  retiradas y no deben recuperarse.

La base activa es `MiGuardiaV2Database`, archivo `miguardia-v2.db`, Room versión
5, con migraciones explícitas `1→2→3→4→5` y 27 tablas.

Hashes protegidos de los esquemas:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

El `identityHash` de Room 5 es
`77adbc875d0f4ee466cdbd0dd74d5c5c`.

Última evidencia verde heredada, que no reemplaza tu validación:

- JVM: 433/433;
- Samsung API 36, Room: 107/107;
- Samsung API 36, aplicación: 190/190;
- Room V2 versión 5 y esquemas 1–4 intactos.

## 3. INPUTS

### Calendario actual

La aplicación ya posee:

- `CalendarViewModel`, `CalendarUiState` y proyección mensual pura;
- una sola grilla con navegación mensual y gesto horizontal;
- detalle del día en consulta;
- una jornada con abreviatura, horario y estado;
- varias jornadas representadas como `2 turnos`, `3 turnos`, etc.;
- marcadores de extras independientes y disponibilidad;
- indicadores `F`, `?`, `CM`, `Fer.` y `V`;
- carga, error y reintento;
- zoom interno 100 %, 150 % y 200 %;
- desplazamiento vertical visible cuando la altura no alcanza;
- tema claro/oscuro, retrato/paisaje y semántica accesible.

### Tarjeta superior actual

`NextEventCard` y el motor heredado ya muestran:

- jornada en curso;
- próxima jornada futura;
- próximo franco explícito;
- cuenta restante;
- carga, error y reintento;
- actualización por límites temporales sin polling por segundo.

Huecos verificados:

- la tarjeta no se despliega;
- no conserva en su fuente las jornadas de hoy cuyo final ya pasó;
- cuando varias jornadas comparten estado sólo informa la cantidad;
- no puede listar todas las jornadas de hoy;
- todavía usa vocabulario visible heredado como `Guardia` en algunos textos;
- los marcadores agregados por incrementos recientes todavía no recibieron un
  cierre visual conjunto.

## 4. TASK

Implementar exclusivamente:

1. la terminación visual y semántica de la única grilla mensual;
2. una proyección reactiva de las jornadas del día actual;
3. la tarjeta superior cerrada y desplegable;
4. la integración segura con el próximo evento futuro cuando hoy no tenga
   jornadas;
5. pruebas proporcionales de dominio, aplicación, Compose y recreación.

## 5. OUTPUT

Al finalizar debe existir un candidato sin commit que:

- permita comprender cada celda sin perder datos importantes;
- muestre arriba qué ocurre hoy;
- permita desplegar todas las jornadas de hoy cuando corresponda;
- siga mostrando el próximo evento futuro si hoy no hay trabajo pendiente, en
  curso o completado, incluso si conserva registros cancelados, ausentes o
  protegidos;
- reaccione a altas, cambios, eliminaciones, paso del tiempo y cambio de fecha;
- preserve todos los recorridos V2 existentes;
- no cambie Room, horas, Resumen, notificaciones ni widget.

## 6. CONTRATO FUNCIONAL

### 6.1 Una sola grilla y consulta segura

- Existe una única grilla mensual y una única proyección de Calendario.
- Tocar un día en consulta abre su detalle y no escribe datos.
- No agregues un segundo calendario, carrusel semanal, agenda paralela ni una
  pantalla diaria que duplique la fuente mensual.
- `Editar este día`, carga múltiple, recurrencias, horario real, extras y
  disponibilidad conservan sus recorridos dueños.
- Cambiar el mes visible no cambia la fecha real que gobierna la tarjeta
  superior.

### 6.2 Jerarquía final de las celdas

Cada celda debe priorizar, en este orden conceptual:

1. fecha, hoy y selección;
2. jornada única o cantidad de jornadas;
3. estado temporal textual o semántico;
4. extras independientes;
5. disponibilidad con el nombre elegido por la persona;
6. marcadores comunes.

Reglas exactas:

- una jornada muestra abreviatura histórica completa, horario planificado
  exacto `HH:mm–HH:mm` y estado comprensible;
- varias jornadas muestran únicamente `N turnos`; no muestran una primera
  jornada seguida de `+N`;
- una jornada completada usa una base neutral o gris atenuada, conserva una
  marca pequeña de su color histórico y mantiene texto o semántica equivalente
  a `Completada`; nunca depende sólo del gris, del color histórico ni de otro
  color;
- una jornada próxima, en curso, cancelada o ausente conserva una etiqueta
  textual o semántica inequívoca;
- un extra independiente muestra `Extra` o `N extras`;
- una disponibilidad muestra Guardia pasiva, Disponible para llamado o Retén,
  según su fotografía histórica; varias pueden resumirse con cantidad sin
  perder sus nombres en el detalle ni en accesibilidad;
- `F`, `?`, `CM`, `Fer.` y `V` mantienen su significado actual;
- un extra o una disponibilidad pueden convivir tanto con `?` implícito como
  con `F` o `?` explícitos; mostrarlos nunca permite inferir, borrar ni cambiar
  ese estado del día;
- Vacaciones puede conservar su prioridad visual vigente, pero no puede borrar
  datos del detalle ni de la descripción accesible;
- hoy y selección no pueden tapar abreviatura, horario, cantidad o marcadores;
- toda reducción visual debe mantener la información completa en el detalle y
  en la semántica;
- el resultado debe seguir siendo utilizable en zoom interno 100 %, 150 % y
  200 %, sin consultar ajustes visuales de Android.

No conviertas un día vacío en disponibilidad, franco, ausencia ni otra
categoría. Continúa siendo `?` implícito.

### 6.3 Detalle único del día

El detalle existente continúa siendo la explicación completa de la celda:

- todas las jornadas ordenadas e identificadas;
- fotografía planificada;
- horario real y extras de jornada cuando existan;
- extras independientes;
- ventanas de disponibilidad;
- `F`, `?`, carpeta médica, feriado y vacaciones;
- notas mediante su acción consciente existente;
- acciones V2 dueñas ya implementadas.

No dupliques formularios dentro del detalle ni conviertas la apertura en una
escritura. No escondas una fuente sólo porque otra tenga prioridad visual en la
celda.

### 6.4 Fuente de las jornadas de hoy

Definí una proyección pura e inmutable, o una ampliación equivalente del
resultado actual, que reciba explícitamente:

- instante de referencia;
- zona;
- todas las jornadas cuya `localStartDate` sea hoy;
- las fuentes de protección que realmente necesite para no anunciar trabajo
  inválido;
- el resultado futuro del motor de próximo evento.

Reglas:

- `hoy` se deriva del instante y `AppDefaults.zoneId()`; no usa la zona del
  equipo de forma implícita;
- las jornadas de hoy se ordenan por inicio, fin y UUID;
- se conservan todas, incluidas completadas, `CANCELLED` y `ABSENT` si esos
  estados existen en los datos;
- una jornada nocturna pertenece a la fecha donde comenzó;
- la lista visible de la tarjeta se forma con una jornada activa iniciada ayer,
  si existe, más todas las jornadas cuya fecha dueña es hoy; se ordena de forma
  estable y se deduplica por UUID;
- una jornada iniciada ayer que sigue en curso puede ser la prioridad cerrada y
  aparece una sola vez en esa lista, claramente identificada con su fecha de
  inicio;
- una jornada normal protegida por Vacaciones o carpeta médica no se presenta
  como pendiente o en curso mientras siga siendo sólo trabajo planificado; si
  existe un registro de horario real, no se oculta ese trabajo confirmado y se
  explican a la vez la protección y el registro real;
- si una jornada protegida se conserva en la lista desplegada, debe quedar
  claramente identificada como protegida;
- el horario planificado permanece como fotografía; este bloque no reescribe
  `Shift` con el horario real;
- no se persisten estados derivados ni totales;
- no se consulta sólo el mes visible;
- al cruzar medianoche se cancela la observación anterior, cambia la fecha civil
  y se vuelve a observar el nuevo día.

Usá `Clock` y `ZoneId` inyectables. No escondas `Instant.now()`,
`LocalDate.now()` ni temporizadores globales dentro de lógica pura.

### 6.5 Tarjeta cerrada

La tarjeta superior mantiene su posición arriba del mes.

Prioridad cerrada:

1. una jornada en curso, incluida una nocturna iniciada ayer;
2. la próxima jornada pendiente de hoy;
3. si hoy sólo tiene jornadas completadas, un resumen honesto equivalente a
   `Hoy: N jornadas completadas`;
4. si hoy sólo tiene registros cancelados, ausentes o protegidos sin trabajo
   real confirmado, `Hoy no tenés trabajo` junto con el próximo evento futuro,
   si existe;
5. si hoy no tiene ninguna jornada, el próximo evento futuro del motor
   existente;
6. si tampoco existe un evento futuro, un estado vacío honesto.

La tarjeta cerrada muestra como mínimo:

- `Hoy`, `Jornada en curso`, `Próxima jornada` o el estado aplicable;
- lugar/abreviatura histórica y horario completo cuando exista una jornada
  principal;
- puesto opcional cuando exista;
- cuenta humana hasta inicio o fin cuando corresponda;
- cantidad de jornadas de hoy cuando sea mayor que una;
- una acción explícita y accesible para desplegar cuando existan registros de
  hoy que la tarjeta cerrada no muestre por completo, aunque sea uno solo.

La jornada principal y el conteo de esta tarjeta representan jornadas `Shift`.
Los extras independientes y la disponibilidad siguen visibles en la grilla y
el detalle, pero no se convierten en el próximo evento ni en una jornada futura
de esta tarjeta.

La tarjeta y su semántica accesible nunca exponen el texto de notas, motivos o
descripciones médicas, fotos ni descripciones privadas. Sólo muestran los
estados mínimos necesarios para comprender el Calendario.

Usá vocabulario común V2: `jornada` o `turno` según el texto ya congelado. No
presentes toda la aplicación como exclusiva de vigilancia mediante `guardia`.
No renombres Guardia pasiva, porque ése es uno de los tres nombres aprobados de
disponibilidad.

### 6.6 Tarjeta desplegada

Cuando la lista visible tenga más de una jornada, o cuando la tarjeta cerrada
resuma u omita un registro histórico de hoy, la persona puede abrir y cerrar la
tarjeta.
La expansión:

- muestra, sin duplicados, la jornada activa iniciada ayer si existe y todas las
  jornadas cuya fecha dueña es hoy;
- incluye las completadas;
- conserva canceladas, ausentes o protegidas si existen;
- usa orden cronológico estable;
- identifica cada jornada por abreviatura/lugar, horario y estado;
- informa el puesto sólo cuando exista;
- no pierde información por compartir hora o estado con otra jornada;
- ofrece una acción accesible equivalente a `Ocultar jornadas de hoy`.

Con una sola jornada podés mantener la tarjeta sin expansión redundante sólo si
toda la información obligatoria ya es visible. Un único registro cancelado,
ausente o protegido que no aparezca completo en la tarjeta cerrada debe seguir
siendo desplegable. La decisión de expansión debe ser clara por texto o
semántica, no sólo por la dirección de un ícono.

El estado abierto/cerrado debe sobrevivir a la recreación normal de la Activity
mediante estado guardable y quedar asociado a la fecha civil que estaba
mostrando. Al cambiar de día se vuelve a cerrado. No se persiste en Room ni
DataStore.

### 6.7 Próximo evento futuro

Si hoy no existe trabajo pendiente, en curso o completado, reutilizá el motor
único de próximo evento, aunque haya registros cancelados, ausentes o
protegidos. No dupliques sus reglas ni lo reemplaces por una consulta limitada
al mes visible.

Este bloque puede ampliar de forma compatible el resultado o el observador de
la aplicación para obtener las jornadas de hoy. No debe adelantar la adaptación
integral V2 de notificaciones ni cambiar sus políticas.

El próximo franco continúa significando únicamente `DAY_OFF` explícito. Un día
vacío o `UNDEFINED` no es un franco.

### 6.8 Reactividad, carga y error

La tarjeta y la grilla se actualizan ante:

- alta, edición o eliminación de jornadas;
- materialización o cambio de una recurrencia;
- cambio de estado persistido ya existente;
- cambio de mes del Calendario, sólo para la grilla;
- cruce de inicio, fin o medianoche;
- cambios de extras o disponibilidad que afecten marcadores.

Requisitos técnicos:

- conservar último contenido válido cuando un error recuperable lo permita;
- mostrar carga, error y `Reintentar` sin ocultar ni bloquear la grilla;
- cancelar observaciones y esperas obsoletas;
- programar el próximo límite relevante o minuto de cuenta humana;
- no usar polling por segundo, servicio, worker ni alarma.

## 7. DEPENDENCIES

Esta tarea depende de los bloques ya cerrados:

- configuración laboral y catálogo V2;
- base Room V2 exclusiva;
- carga y edición exacta;
- recurrencias;
- horario real y extras de jornada;
- extras independientes y avance;
- guardias pasivas y disponibilidad;
- Calendario, próximo evento y diseño adaptable heredados.

No depende de Resumen, notificaciones V2, widget, informes, copias o bloqueo.

## 8. SCOPE

Podés crear o modificar, sólo si el diff lo justifica:

- `core/domain/src/main/**` para proyección pura de Calendario/tarjeta de hoy;
- `core/domain/src/test/**` para esas reglas;
- `app/src/main/**` para observador, estado, ViewModel, tarjeta, Calendario,
  composición y componentes visuales acotados;
- `app/src/test/**` para lógica de aplicación o presentación pura;
- `app/src/androidTest/**` para Compose, Activity y recreación.

No modifiques documentación canónica. MAIN conserva `STATUS`, mapa, índice,
ADR, auditorías y este prompt.

Si necesitás tocar otro archivo o ampliar un contrato público ajeno, detené esa
parte y explicá la necesidad a MAIN.

## 9. DO NOT

No implementes ni modifiques:

- el flujo para marcar ausencia o cancelación;
- capacitación, suspensión, licencia, intercambio, cobertura u otra situación
  especial nueva;
- fórmulas, referencia, cumplimiento o motor final de horas;
- Resumen personalizable;
- notificaciones, alarmas, canales, receivers, workers o servicios;
- widget;
- recurrencias o mutaciones estructurales nuevas;
- horario real, clases extra o disponibilidad como fuentes de escritura;
- Room, DAO, repositorios de base, entidades, versión, esquemas, migraciones,
  claves o índices;
- DataStore;
- Gradle, dependencias, manifiesto, permisos, `applicationId`, SDK o versión;
- fotos, clima, informes, copias, bloqueo, Ayuda u onboarding;
- red, cuentas, nube, sincronización, analítica o telemetría;
- montos, salarios, liquidaciones, deducciones o información sindical;
- `font_scale`, densidad, zoom o tamaño de visualización del sistema;
- producción ni datos reales.

No recuperes `ShiftNovelty`, `FormalShiftChange`, sus tablas, repositorios o la
pantalla `Informar novedad` de V1.

No hagas commit, push, tag, merge, rebase, reset, limpieza, descarte, nueva rama
o worktree. No abras otra tarea ni delegues otra implementación.

## 10. VALIDATION

### 10.1 Dominio JVM

Con instantes, zona y UUID deterministas, cubrí como mínimo:

1. hoy sin jornadas usa el próximo evento futuro;
2. una jornada próxima de hoy queda como principal;
3. una jornada en curso queda antes que una próxima;
4. una nocturna iniciada ayer y todavía activa queda como principal sin
   duplicarse en la lista de hoy;
5. una jornada completada hoy permanece en la lista desplegable;
6. varias jornadas de hoy se ordenan por inicio, fin y UUID;
7. dos jornadas simultáneamente en curso se conservan y se ordenan sin perder
   ninguna;
8. mismo inicio y distinto fin o UUID conserva ambas;
9. `CANCELLED` y `ABSENT` se conservan en la lista histórica sin anunciarse
   como trabajo pendiente;
10. Vacaciones y carpeta médica no anuncian una jornada normal como pendiente;
11. sólo canceladas, ausentes o protegidas muestran que hoy no hay trabajo,
    conservan el despliegue histórico y permiten ver el próximo evento;
12. un registro de horario real no queda oculto por una protección;
13. `DAY_OFF` explícito funciona como próximo franco sólo cuando hoy no tiene
    una jornada prioritaria;
14. `UNDEFINED` y día vacío no son francos;
15. inicio inclusivo y fin exclusivo;
16. cambio de día a medianoche;
17. fin de mes, fin de año y febrero bisiesto;
18. zona Córdoba independiente de la zona del equipo;
19. ninguna proyección muta o persiste modelos.

### 10.2 Aplicación y observadores

Probá como mínimo:

1. observación reactiva del día actual y del próximo evento;
2. alta, edición y eliminación actualizan la tarjeta;
3. cambio de fecha cancela la fuente anterior y observa el nuevo día;
4. el mes visible no gobierna la tarjeta;
5. límites temporales actualizan sin polling permanente;
6. error conserva último dato válido cuando corresponde;
7. `Reintentar` recupera contenido;
8. cancelar la colección cancela esperas y fuentes obsoletas;
9. un fallo parcial al observar extras o disponibilidad conserva la grilla
   mensual y expone el error correspondiente en vez de reemplazar todo por vacío;
10. la expansión se restaura para la misma fecha y vuelve a cerrada al cruzar
    medianoche.

### 10.3 Compose y Activity

Probá como mínimo:

1. celda con una jornada: abreviatura, horario y estado completos;
2. celda con dos o más: texto exacto `N turnos`;
3. celda completada identificada sin depender sólo del color;
4. celda con extra independiente;
5. celda con disponibilidad y su nombre correcto;
6. convivencia de jornada, extra, disponibilidad y marcadores comunes;
7. Vacaciones no elimina la información del detalle ni la semántica;
8. tarjeta cerrada con una jornada en curso;
9. tarjeta cerrada con próxima jornada de hoy;
10. tarjeta con sólo jornadas completadas hoy;
11. abrir muestra todas las jornadas de hoy, incluidas completadas;
12. cerrar vuelve al resumen sin perder datos;
13. varias jornadas con misma hora o estado siguen diferenciadas;
14. ausencia/cancelación de fixture se identifica correctamente, permite abrir
    el registro y no anuncia trabajo para hoy;
15. un único registro histórico resumido sigue ofreciendo despliegue;
16. sin jornadas hoy muestra el próximo evento futuro;
17. sin ningún evento muestra un vacío honesto;
18. carga y error no ocultan el Calendario;
19. recreación conserva mes, detalle y expansión aplicable;
20. cambio de mes no altera la tarjeta;
21. tema claro y oscuro;
22. retrato y paisaje;
23. zoom interno 100 %, 150 % y 200 %;
24. viewport bajo conserva barra de desplazamiento y acceso al contenido;
25. semántica describe fecha, cantidad, jornada, horario y estado sin exponer
    notas, motivos médicos, fotos ni descripciones privadas;
26. carga, edición, recurrencias, horario real, extras y disponibilidad siguen
    abriendo sus flujos existentes.

Reutilizá y ampliá proporcionalmente, entre otras:

- `CalendarProjectionTest`;
- `NextEventTest`;
- `NextEventFormattingTest`;
- `CalendarComposeTest`;
- `CalendarCommonV2ComposeTest`;
- `CalendarAdaptiveLayoutComposeTest`;
- `NextEventComposeTest`;
- `NextEventObserverInstrumentedTest`;
- `CalendarMonthObserverInstrumentedTest`;
- `V2ReadyCalendarRecreationActivityTest`;
- regresiones afectadas de Horas y extras, Disponibilidad y navegación raíz.

### 10.4 Batería local

Ejecutá serializado:

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

Obtené conteos reales desde los XML. Diferenciá:

- JVM ejecutado;
- lint;
- APK compilado;
- AndroidTest compilado;
- instrumentación realmente ejecutada;
- revisión física realmente realizada;
- pendiente.

Ejecutá además:

```powershell
git diff --check
git status --short
```

Revisá cada hunk y cada archivo nuevo. Confirmá ausencia de secretos, datos
reales, logs privados, red, telemetría, permisos, dependencias y artefactos.

### 10.5 Room

Room no debe cambiar. Verificá:

- versión 5;
- 27 tablas;
- esquemas 1–5 byte a byte iguales a la entrada;
- hashes indicados en este prompt;
- ausencia de `fallbackToDestructiveMigration` y `allowMainThreadQueries` en
  producción.

No presentes compilación de AndroidTest como instrumentación ejecutada.

### 10.6 QA física — puerta separada

No uses ADB ni el Samsung hasta que Joaquin lo autorice expresamente para esta
dependencia o MAIN entregue esa autorización en el mismo encargo.

Con autorización:

- usar exclusivamente Samsung `SM-S938B`, API 36, y paquetes QA/test;
- no abrir, instalar, consultar, limpiar ni desinstalar producción;
- ejecutar las suites afectadas de Calendario, próximo evento, recreación,
  Horas y extras, Disponibilidad y navegación;
- recorrer con datos ficticios los estados vacíos, una jornada, varias jornadas,
  mezcla completada/en curso/próxima, expansión, cambio de fecha, marcadores,
  claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del sistema;
- desinstalar sólo QA y test al finalizar e informar qué quedó instalado.

API 26 es una validación separada. No la declares realizada si sólo compiló.

## 11. DONE WHEN

La dependencia termina únicamente cuando:

- la grilla conserva una sola fuente y presenta todas las categorías existentes
  con jerarquía comprensible;
- una y varias jornadas cumplen su representación exacta;
- completada no depende sólo del color;
- la tarjeta cerrada prioriza correctamente lo de hoy;
- la tarjeta abierta muestra todas las jornadas de hoy, incluidas completadas;
- sólo canceladas, ausentes o protegidas no se anuncian como trabajo de hoy y
  siguen accesibles en el despliegue;
- sin jornadas hoy reutiliza el próximo evento futuro;
- medianoche, fin de jornada y cambios de datos actualizan la superficie;
- la consulta no escribe y los flujos V2 existentes siguen intactos;
- Room, esquemas, Gradle, manifiesto, permisos y versión permanecen intactos;
- pruebas, lint y compilaciones obligatorias pasan;
- QA física está realizada con autorización o informada honestamente como
  pendiente;
- `git diff --check` está limpio;
- el checkout contiene sólo cambios atribuibles a esta dependencia;
- el handoff permite que MAIN audite cada afirmación.

## 12. HANDOFF A MAIN

No hagas commit ni push. Entregá el resultado directamente en el checkout
compartido y respondé en español con:

### QUÉ HACE

Resultado visible y concreto.

### POR QUÉ EXISTE

Problema que quedó resuelto y bloque siguiente que habilita.

### OBJECTIVE

Objetivo efectivamente alcanzado.

### CHANGES

Cambios funcionales, de dominio, observación y Compose.

### FILES

Archivos modificados, nuevos y eliminados, con cantidades exactas.

### DECISIONS

Decisiones técnicas tomadas dentro de este contrato.

### VALIDATION

Comandos, conteos reales, fallos, errores, omitidas, lint y APK.

### ROOM

Versión, tablas, hashes y confirmación de que no cambió.

### PHYSICAL QA

Dispositivo, paquetes, suites y recorrido realmente ejecutados, o `PENDIENTE`.

### DEVICE SAFETY

Qué se instaló, abrió, limpió o desinstaló y qué quedó en el dispositivo.

### RISKS

Defectos, límites o incertidumbres reales.

### PENDING

Qué debe resolver MAIN.

### GIT

Ruta, rama, HEAD, upstream, estado, diff y confirmación de ausencia de commit,
push, tag, merge, rebase, reset o descarte.

### NEXT

MAIN debe auditar cada hunk, repetir pruebas proporcionales, decidir la QA física
y crear el checkpoint local sólo si acepta el candidato. La dependencia no
prepara ni abre Resumen por su cuenta.
