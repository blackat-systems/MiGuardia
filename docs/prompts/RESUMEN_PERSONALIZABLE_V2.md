# Resumen personalizable V2

- Estado: **HABILITADO**
- Fecha: 2026-08-27
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada y publicada:
  `fd6891e446eaa574f3df14348d8d5b1cfd201f2d`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Resumen personalizable**

## QUÉ HACE

Agrega una pantalla mensual que explica, en un solo lugar, cuánto trabajó la
persona, cuánto corresponde a trabajo habitual o extra, qué queda programado y
cómo avanza contra su referencia de horas cuando existe.

El Resumen muestra primero lo esencial. Desde `Personalizar resumen`, la
persona puede decidir qué detalles adicionales quiere ver y en qué orden. Al
tocar una cifra puede revisar exactamente qué jornadas o trabajos la forman.

## POR QUÉ EXISTE

MiGuardia ya guarda jornadas, horario real, extras, referencias de horas,
vacaciones, carpetas médicas, feriados y disponibilidad. Esos datos hoy están
repartidos entre el Calendario, el detalle del día y `Horas y extras`.

Esta dependencia los reúne sin inventar otra fórmula ni guardar totales
congelados. Existe para que la persona pueda comprender su mes antes de adaptar
próximo evento y notificaciones, y para que los futuros informes consuman una
proyección única y verificable.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, el Calendario, la persistencia V2 ni
la secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

Antes de modificar:

1. ejecutá Puerta 0 de sólo lectura;
2. leé completas y en el orden de `AGENTS.md` todas las fuentes obligatorias;
3. confirmá ruta, rama, HEAD exacto informado por MAIN, upstream,
   `v1.0.0^{}`, limpieza, worktrees, remoto privado y autor Git;
4. inspeccioná completos horas, disponibilidad, reglas por lugar, Calendario,
   navegación, DataStore, Room V2 y sus pruebas;
5. detenete ante un mismatch, HEAD separado, cambios sin dueño o una decisión
   material no resuelta; no descartes ni reemplaces trabajo.

## TASK

Implementar exclusivamente el **Resumen personalizable V2** como una
proyección mensual reactiva y de sólo lectura.

El recorrido mínimo debe permitir:

1. abrir `Resumen` desde el menú principal;
2. elegir mes anterior, siguiente o volver al mes actual;
3. ver automáticamente las cifras esenciales que realmente tengan sentido;
4. distinguir total trabajado, habitual, extras y pendiente sin duplicar
   minutos;
5. consultar cumplimiento sólo cuando la referencia permita calcularlo;
6. ver disponibilidad separada cuando existan ventanas aplicables;
7. abrir `Personalizar resumen` desde el menú de tres puntos;
8. mostrar, ocultar y ordenar los detalles opcionales autorizados;
9. conservar esas preferencias después de cerrar y reabrir la aplicación;
10. tocar una cifra y ver las fuentes exactas que la integran;
11. volver al mismo mes sin perder posición, preferencias ni estado;
12. reaccionar a cambios reales de jornadas, horario real, extras,
    configuración, protecciones, feriados y disponibilidad.

Esta tarea no adapta próximo evento, notificaciones, widget, informes, copias,
bloqueo ni Ayuda. Tampoco crea nuevos flujos de situaciones especiales.

## CONTEXT

La base cerrada ya posee:

- una experiencia exclusivamente V2;
- cuatro rubros exactos: Vigilancia privada, Policía, Enfermería y Medicina;
- una sola configuración laboral con revisiones vigentes desde `LocalDate`;
- referencias mensuales, semanales o por ciclo, incluida la fecha consciente
  de reinicio;
- jornadas manuales y recurrentes con par obligatorio
  `Shift + ShiftWorkSnapshot`;
- horario planificado y horario real opcional;
- extras exactas pertenecientes a una jornada;
- extras independientes con fotografía histórica;
- un motor `HoursProgress` que separa habitual, extras, total, cumplimiento y
  pendiente para un tramo de referencia;
- reglas versionadas de noche, feriado y fin de semana por lugar;
- feriados manuales, vacaciones, carpetas médicas, notas y `F/?`;
- ventanas de disponibilidad y cálculo separado de disponibilidad programada,
  efectiva, reemplazada y pendiente;
- una sola grilla mensual y una tarjeta superior final;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db` y veintisiete
  tablas;
- DataStore disponible para preferencias simples, aunque todavía no existe un
  almacén dueño de la presentación del Resumen.

No existe una pantalla Resumen V2. El menú principal muestra Calendario y
Apariencia, y `Horas y extras` es una superficie funcional de configuración y
avance, no el Resumen mensual final.

Esquemas Room protegidos:

- SHA-256 de `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- SHA-256 de `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- SHA-256 de `3.json`:
  `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`;
- SHA-256 de `4.json`:
  `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B`;
- SHA-256 de `5.json`:
  `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4`.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0003, 0005, 0006, 0007, 0015, 0020, 0026, 0028, 0029, 0030 y
  0031;
- `docs/prompts/MOTOR_BASICO_DE_HORAS.md` sólo como antecedente V1;
- `docs/prompts/EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md`;
- `docs/prompts/GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md`;
- `docs/prompts/CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md`;
- las auditorías finales de esos tres bloques V2;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico no reemplazado;
- `HoursProgress`, `HoursReference`, `HoursPeriod`, configuración laboral y sus
  pruebas;
- `Shift`, `ShiftWorkSnapshot`, horario real, extras y disponibilidad;
- reglas y revisiones por lugar, feriados, vacaciones, carpetas médicas y
  estados diarios;
- repositorios públicos y observadores de todas esas fuentes;
- `HoursAndExtras*`, `Availability*`, `Calendar*`, `MainActivity`,
  `MiGuardiaApplication`, `MiGuardiaApp` y sus pruebas;
- almacenes DataStore existentes como patrón de propiedad, errores y pruebas;
- próximo evento y notificaciones sólo como consumidores protegidos que no se
  adaptan en este bloque.

No recuperes el Resumen V1 ni código desde worktrees históricos. Si una fuente
histórica contradice la planificación V2, prevalece la jerarquía activa.

## DEPENDENCIES

Esta tarea depende de contratos ya integrados:

- configuración laboral y vigencia por fecha;
- catálogo y reglas versionadas por lugar;
- carga, edición y recurrencias de jornadas;
- horario real y extras exactas;
- extras independientes y avance contra la referencia;
- guardias pasivas y disponibilidad;
- Calendario final y tarjeta superior;
- Room V2 exclusiva con cadena `1→2→3→4→5`.

Debe dejar una proyección pura y reutilizable para que los futuros informes
puedan leer los mismos resultados sin copiar fórmulas. También debe dejar el
menú principal listo para que el bloque posterior adapte próximo evento y
notificaciones sin modificar el Resumen.

## DECISIONES CONGELADAS

### 1. Un mes, una proyección y ningún total persistido

El Resumen trabaja sobre un `YearMonth` elegido. Conserva ese mes mediante
`SavedStateHandle` y usa `Clock` y `ZoneId` inyectables.

Room continúa guardando sólo fuentes e historia. El Resumen, sus cifras, sus
porcentajes y sus desgloses se calculan al observar datos; no se escriben como
totales, cachés opacos ni filas mensuales.

La proyección mensual vive en `core:domain`, sin Android, Compose ni Room. Debe
reutilizar la semántica vigente de `HoursProgress` o extraer ayudantes puros
compartidos. No se acepta una segunda fórmula que pueda divergir de la vista
`Horas y extras`.

La observación no puede limitarse por comodidad únicamente al sector de la
revisión vigente hoy. Debe resolver todas las revisiones y fuentes históricas
que realmente alcancen el mes o los períodos mostrados, sin habilitar por eso
un flujo nuevo de cambio de rubro.

### 2. Atribución temporal

Cada fuente completa pertenece al mes de su fecha dueña:

- una jornada sin horario real usa la fecha local de su inicio planificado;
- una jornada con horario real usa la fecha local de su inicio real, según el
  contrato vigente de `HoursProgress`;
- los fragmentos extra de esa jornada siguen a la misma jornada;
- un extra independiente usa la fecha local de su inicio exacto;
- una ventana de disponibilidad usa la fecha local fotografiada de su inicio.

La fuente no se parte entre dos meses aunque termine después de medianoche. En
cambio, las clasificaciones de noche, feriado y fin de semana inspeccionan sus
instantes civiles reales, incluso si alcanzan el día o mes siguiente.

El Calendario conserva su propia fecha visual ya aprobada. Si un horario real
mueve la atribución de horas a otro mes, el detalle del Resumen debe explicarlo
sin mover la celda del Calendario.

### 3. Información esencial automática

El contenido esencial es:

- total trabajado;
- trabajo habitual;
- extras, únicamente cuando existan;
- pendiente programado;
- cumplimiento, únicamente cuando exista una referencia aplicable;
- disponibilidad, únicamente cuando existan ventanas aplicables.

Reglas:

- `Total trabajado = habitual + todas las extras`;
- cada clase extra conserva si ayuda o no a cumplir;
- superar la meta nunca crea una extra;
- disponibilidad nunca se suma a trabajo ni cumplimiento;
- una jornada usa horario real cuando existe y planificado en caso contrario;
- los fragmentos extra de una jornada se restan de habitual y se suman una sola
  vez como extra;
- trabajos activos solapados que la persona conservó suman completos;
- vacaciones y carpeta médica excluyen sólo trabajo meramente planificado;
- un horario real o extra confirmado no se borra por coexistir con una
  protección;
- `ABSENT` y `CANCELLED` no suman trabajo ni pendiente;
- una fuente en curso aporta sólo minutos transcurridos al total y conserva el
  resto aplicable como pendiente;
- toda aritmética usa minutos enteros y detecta overflow.

Un mes sin fuentes muestra un único estado vacío honesto. No presenta una pared
de tarjetas con `0`. Una categoría opcional sin datos no se muestra aunque la
persona la haya dejado habilitada.

### 4. Cumplimiento mensual sin cortar semanas o ciclos

El total principal pertenece al mes seleccionado. `Cumplimiento de horas` es
distinto: muestra cada tramo de referencia cuya ventana toca ese mes.

- Una referencia mensual usa sus tramos mensuales reales.
- Una referencia semanal muestra la semana completa aunque empiece en el mes
  anterior o termine en el siguiente.
- Un ciclo personalizado se muestra completo aunque cruce el límite mensual.
- Un reinicio o cambio de referencia conserva los tramos reales y puede dejar
  un primer tramo corto con la meta completa.
- Si más de un tramo toca el mes, se muestran todos en orden cronológico.
- Los cálculos del tramo incluyen todas las fuentes cuya fecha dueña pertenece
  a ese tramo, incluso fuera del mes visible.

Estados `PendingSetup`, `NotUsed`, `Unknown` y `MissingPerPeriodValue` nunca se
transforman en meta cero, faltante cero, superación cero ni porcentaje falso.
`Falta informar` continúa siendo un estado explícito.

### 5. Personalización de presentación

Desde un menú de tres puntos existe la acción exacta
`Personalizar resumen`.

La persona puede mostrar, ocultar y ordenar estas familias opcionales:

- noches;
- feriados;
- fines de semana;
- planificado frente a real;
- lugares de trabajo;
- tipos de trabajo;
- clases extra;
- situaciones especiales e intercambios.

La personalización cambia la presentación, no las fórmulas, fuentes,
atribuciones ni fotografías históricas. No permite escribir fórmulas, crear
categorías arbitrarias ni renombrar conceptos laborales.

La primera visita muestra una explicación breve y no bloqueante de que el
Resumen puede personalizarse. La persona puede marcarla como entendida y
abrirla después desde el mismo menú.

Las preferencias viven en un DataStore Preferences exclusivo del Resumen:

- orden estable de las familias opcionales;
- familias ocultas;
- explicación inicial ya vista.

La lectura tolera claves faltantes o categorías futuras: elimina duplicados,
ignora valores desconocidos y agrega al final cualquier categoría vigente que
falte. Un error de lectura recuperable usa valores iniciales seguros y no
modifica Room. Las escrituras son atómicas. QA conserva su archivo separado por
`applicationId`.

El orden se serializa en una representación que lo preserve explícitamente; no
usar un conjunto sin orden como fuente de verdad.

### 6. Reglas por lugar y fotografías históricas

Noche, feriado y fin de semana son clasificaciones superpuestas. Pueden
coincidir entre sí, pero nunca agregan minutos al total trabajado.

- Noche usa la regla versionada del lugar aplicable a cada tramo civil.
- Feriado exige una fecha cargada y usa la regla versionada del lugar.
- Fin de semana usa sábado, domingo o ambos según la regla del lugar.
- Una regla deshabilitada no produce un desglose.
- `showDedicatedSummary` decide si ese dato puede aparecer como sección
  separada; ocultarlo desde `Personalizar resumen` puede reducir todavía más la
  presentación, pero nunca puede forzar un desglose que su regla histórica no
  habilita.
- Las clases extra se agrupan por su fotografía histórica exacta. Una clase
  renombrada o archivada no reinterpreta registros anteriores.
- Los grupos por lugar y tipo usan las fotografías históricas de cada fuente,
  no el nombre actual del catálogo.

La clasificación trabaja sobre el intervalo realmente trabajado cuando existe
horario real y sobre los instantes exactos de extras independientes. No puede
clasificar una corrección usando por error el intervalo planificado.

El desglose planificado frente a real sólo compara jornadas que poseen un
horario real confirmado. Informa duraciones y diferencia sin interpretar
dinero, deuda, premio, sanción ni saldo legal.

### 7. Situaciones existentes, sin crear recorridos nuevos

La familia opcional de situaciones utiliza únicamente datos que ya existen:

- ausencias y cancelaciones persistidas en jornadas;
- días de carpeta médica;
- días de vacaciones;
- francos `F` explícitos, cuando corresponda al desglose.

`?` conserva el significado de día sin definir y nunca se transforma en
ausencia, franco ni trabajo. Notas, motivos, descripciones y datos médicos no
forman parte de las cifras ni del detalle.

No existe todavía un flujo V2 ampliado de capacitación, intercambio, cobertura
o `Otra situación`. No mostrar tarjetas vacías, no recuperar tablas V1 y no
inventar filas para esas capacidades futuras.

### 8. Tocar una cifra explica su origen

Toda cifra visible abre un detalle de sólo lectura equivalente a
`Qué incluye este valor`.

Ese detalle:

- enumera contribuciones con fecha, horario, duración, fuente y fotografía
  laboral segura;
- permite reconciliar exactamente la suma de filas con la cifra tocada;
- diferencia habitual, extra, pendiente, protección y disponibilidad;
- explica cuando una misma porción aparece también como noche, feriado o fin
  de semana sin sumarse de nuevo al total;
- conserva un orden determinista por inicio, final, tipo e identificador;
- no expone notas, motivos médicos, explicaciones privadas, direcciones, fotos
  ni datos personales;
- no permite editar, eliminar ni navegar a un segundo calendario.

La cifra y su detalle nacen de la misma proyección inmutable. No recalcular el
detalle con otra consulta o fórmula que pueda producir una suma distinta.

### 9. Navegación y estados

`Resumen` vuelve a ser un destino principal del panel, junto con `Calendario`.
No es una acción subordinada a `Mi forma de trabajar`.

- Abrir Resumen cierra el panel y navega una sola vez.
- Atrás desde Resumen vuelve al Calendario antes de salir.
- El mes visible, el detalle abierto y la etapa de personalización sobreviven a
  recreación cuando corresponda.
- El Resumen distingue `LOADING`, `CONTENT`, `EMPTY` y `ERROR`.
- Un error de una fuente no se convierte en lista vacía ni cero.
- Si ya existe una proyección válida para ese mismo mes, un error recuperable
  puede conservarla con aviso y `Reintentar`; nunca muestra datos de otro mes
  como si fueran actuales.
- Cambiar de mes cancela la observación anterior.
- La proyección reacciona a fuentes locales y a límites temporales relevantes;
  mientras haya trabajo en curso puede actualizar en el siguiente límite de
  minuto, sin bucles ocupados ni temporizadores huérfanos.

### 10. Accesibilidad y límites visuales

- contenido desplazable y usable con IME cuando corresponda;
- botones explícitos para mover secciones arriba o abajo, o una alternativa
  igualmente accesible; no depender sólo de arrastrar;
- tags estables y únicos para métricas, familias y filas;
- ninguna distinción depende sólo del color;
- horas y minutos legibles, sin decimales engañosos;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del
  sistema;
- no agregar gráficos si tarjetas y filas explican mejor las relaciones;
- español claro, sin fórmulas salariales o legales.

## OUTPUT

La dependencia debe entregar:

- proyección pura mensual y libro exacto de contribuciones;
- cumplimiento por tramos completos que tocan el mes;
- clasificaciones por reglas históricas de lugar;
- preferencias de presentación en DataStore separado;
- pantalla Resumen, personalización y detalle de cifras;
- destino principal y navegación Android coherentes;
- estados, errores, reintento, recreación y actualización temporal;
- pruebas puras, JVM, DataStore, Compose y Activity;
- handoff verificable a MAIN con diff sin commit.

La documentación canónica, ADR, índice, STATUS y auditoría final pertenecen a
MAIN y no se modifican desde esta dependencia.

## SCOPE

Puede modificar solamente lo necesario dentro de:

- `core/domain/src/main/**` y `core/domain/src/test/**` para proyecciones puras
  y ayudantes compartidos de horas;
- `app/src/main/**` para ViewModel, Compose, navegación, recursos y el
  DataStore exclusivo del Resumen;
- `app/src/test/**`;
- `app/src/androidTest/**`.

Puede adaptar `HoursProgress` de forma compatible sólo si extrae una fuente
pura compartida necesaria para evitar fórmulas duplicadas. Debe conservar sus
resultados y pruebas actuales.

No necesita modificar `core/database`. Si un contrato de lectura realmente
impide observar una fuente existente, detené esa parte y explicá a MAIN el
cambio mínimo; no alteres DAO, entidades o esquema por iniciativa propia.

## DO NOT

No:

- modificar `AGENTS.md`, `docs/**` ni fuentes canónicas;
- cambiar los cuatro rubros ni crear `Salud` u `Otro`;
- unir Enfermería y Medicina;
- imponer 204 horas, lunes, nocturnidad u otra regla sectorial;
- prorratear una referencia o cortar una semana/ciclo al terminar el mes;
- convertir referencia desconocida, no usada o faltante en cero;
- convertir superación en extra;
- sumar disponibilidad al trabajo o cumplimiento;
- contar dos veces un fragmento extra dentro del horario real;
- fusionar trabajos activos superpuestos que la persona conservó;
- reinterpretar fotografías con catálogos actuales;
- persistir totales, porcentajes, faltantes o desgloses en Room o DataStore;
- permitir fórmulas arbitrarias;
- mostrar tarjetas opcionales vacías;
- exponer notas, motivos médicos, direcciones, fotos o explicaciones privadas;
- recuperar Resumen, tablas, escritores o modo V1;
- crear situaciones especiales nuevas;
- adaptar próximo evento, notificaciones o widget;
- implementar informes, copias, bloqueo, onboarding o Ayuda;
- implementar agenda profesional, pacientes o Psicología;
- modificar Room, entidades, DAO, versión, esquemas o migraciones;
- modificar Gradle, manifiesto, permisos, `applicationId`, versión o SDK;
- agregar dependencias de producción;
- acceder a red, cuentas, nube, telemetría o datos reales;
- registrar datos privados en logs;
- abrir, instalar, limpiar o desinstalar producción;
- usar ADB, Samsung o emulador sin autorización expresa posterior de Joaquin;
- consultar o modificar `font_scale`, densidad o tamaño visual del sistema;
- crear commit, push, tag, merge, rebase, reset o descarte;
- crear otra tarea, rama, worktree o subagente.

Ante una necesidad real fuera de alcance, detenete y devolvé a MAIN el punto
exacto. No inventes una extensión.

## VALIDATION

### 1. Dominio JVM

Cubrir como mínimo:

- mes vacío y mes con sólo trabajo futuro;
- jornada pasada, futura y en curso;
- horario planificado, real menor, real mayor habitual y real con extras;
- fragmentos extra sin doble conteo;
- extra independiente y clases que ayudan o no a cumplir;
- trabajos activos solapados que suman completos;
- fecha dueña por inicio planificado, real e independiente;
- medianoche, fin de mes/año, febrero bisiesto y más de 24 horas;
- referencia mensual, semanal con cualquier primer día y ciclos de 14, 21 y 28
  días;
- semanas y ciclos completos que tocan el mes;
- reinicio dentro del período con meta completa y varios tramos en un mes;
- referencia pendiente, no usada, desconocida y valor faltante sin ceros
  falsos;
- vacaciones y carpeta médica sobre trabajo planificado;
- trabajo real confirmado dentro de una protección;
- `ABSENT`, `CANCELLED`, vacaciones, carpeta médica y `F`;
- disponibilidad pasada, actual, futura, protegida y parcialmente reemplazada;
- disponibilidad excluida de total y cumplimiento;
- noche con cualquier ventana válida y cruce de medianoche;
- feriado en el día siguiente de una fuente atribuida al mes inicial;
- sábado, domingo o ambos según lugar;
- cambio de reglas por lugar entre dos fechas;
- regla deshabilitada y `showDedicatedSummary = false`;
- agrupación histórica por lugar, tipo y clase renombrada;
- planificado frente a real;
- solapamiento de noche, feriado y fin de semana sin duplicar total;
- suma exacta de cada detalle con su cifra;
- orden determinista, reloj, zona, minuto normalizado y overflow.

No uses reloj real, UUID aleatorios ni datos de Joaquin.

### 2. DataStore JVM

Verificar:

- valores iniciales estables;
- explicación de primera visita;
- ocultar y volver a mostrar una familia;
- mover arriba y abajo sin duplicados ni pérdidas;
- persistencia al reabrir;
- claves faltantes, valor desconocido, lista repetida y categoría futura;
- error de lectura recuperable;
- edición atómica y dos escrituras consecutivas coherentes;
- archivo QA/test aislado.

### 3. App JVM, Compose y Activity

Cubrir como mínimo:

- `Resumen` visible como destino principal;
- actualización de las pruebas existentes que hoy comprueban correctamente la
  ausencia del Resumen antes de este bloque, sin debilitar la primera apertura
  bloqueante;
- entrada única, cierre del panel y Atrás al Calendario;
- `LOADING`, `CONTENT`, `EMPTY`, `ERROR` y reintento;
- navegación mensual y Hoy;
- mes y detalle conservados al recrear;
- esenciales automáticos y ausencia de tarjetas opcionales vacías;
- primera visita y acceso posterior a `Personalizar resumen`;
- mostrar, ocultar y ordenar con controles accesibles;
- preferencia aplicada después de recrear y reapertura;
- cumplimiento con uno y varios tramos completos;
- estados de referencia no calculables sin cero falso;
- tocar cada tipo de cifra y reconciliar sus filas;
- ausencia de notas, motivos médicos, direcciones y fotos;
- reacción a alta, edición y eliminación de jornadas y extras;
- reacción a horario real, referencia, feriados, protecciones, reglas y
  disponibilidad;
- error parcial sin reproducir cifras de otro mes;
- regresión de Calendario, tarjeta superior y `Horas y extras`;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- semántica accesible sin depender sólo de color.

### 4. Batería local serializada

Ejecutar desde la raíz:

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

Obtener conteos reales desde XML y distinguir:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

Ejecutar además:

- `git diff --check`;
- búsqueda de totales persistidos y fórmulas duplicadas;
- búsqueda de escritores nuevos de Room y `Shift`;
- búsqueda de `fallbackToDestructiveMigration`;
- comparación/hash de `1.json` a `5.json`;
- auditoría de secretos, logs, red, permisos, Gradle y manifiesto;
- revisión de que ningún archivo de `docs/**` haya sido modificado.

### 5. Android y dispositivos

Compilar AndroidTest es obligatorio pero no equivale a ejecutarlo.

Esta tarea nace **sin autorización para usar Samsung, ADB ni emulador**. Si
Joaquin lo autoriza expresamente después, usar únicamente paquetes QA/test y
datos ficticios, no tocar producción y recorrer:

- mes vacío, pasado, actual y futuro;
- cumplimiento mensual, semanal y por ciclo;
- personalización, persistencia y recreación;
- detalle exacto de cifras;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- regresión de Calendario y Horas y extras.

No consultar ni modificar ajustes visuales del sistema. Desinstalar sólo los
paquetes QA autorizados e informar exactamente qué queda en el dispositivo.

Sin autorización, marcar instrumentación y revisión física como `PENDIENTE`.
No presentar APK compilado como QA física.

## HANDOFF A MAIN

Entregar en español, de forma compacta y verificable:

```text
# HANDOFF A MAIN — Resumen personalizable V2

## QUÉ HACE
## POR QUÉ EXISTE
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## VALIDATION
## ROOM
## PHYSICAL QA
## DEVICE SAFETY
## RISKS
## PENDING
## GIT
## NEXT
```

Incluir:

- ruta, rama, HEAD de entrada y upstream;
- archivos modificados, nuevos y eliminados;
- cifras esenciales, familias opcionales y regla de cada una;
- API pública de la proyección y relación con `HoursProgress`;
- formato y recuperación del DataStore del Resumen;
- conteos reales de pruebas;
- qué fue sólo compilado y qué se ejecutó;
- confirmación de Room v5, 27 tablas y hashes intactos;
- paquetes usados y estado final de dispositivos, si hubo autorización;
- límites no implementados;
- `git status`, `git diff --check` y confirmación de cero staged;
- confirmación de que no hubo commit, push, tag, merge, rebase, reset ni
  descarte.

El resultado queda directamente en el checkout compartido, sin commit. No
existe nada para cherry-pick. MAIN audita cada hunk, repite pruebas
proporcionales, encarga una revisión independiente y decide el checkpoint.

## DONE WHEN

La dependencia se considera candidata sólo cuando:

- Resumen es un destino principal mensual y de sólo lectura;
- muestra lo esencial sin tarjetas vacías ni ceros inventados;
- habitual, extras, total y pendiente no duplican minutos;
- cumplimiento muestra tramos completos que tocan el mes;
- referencias desconocidas, no usadas o incompletas siguen siendo honestas;
- disponibilidad permanece separada de trabajo y cumplimiento;
- noche, feriado y fin de semana respetan reglas históricas por lugar;
- la persona puede mostrar, ocultar y ordenar detalles sin cambiar fórmulas;
- las preferencias sobreviven a reapertura mediante DataStore propio;
- cada cifra visible se reconcilia con su detalle exacto;
- no se exponen datos privados;
- errores, reintento, reactividad, tiempo y recreación están cubiertos;
- Room v5 y sus cinco esquemas permanecen byte a byte intactos;
- la batería local requerida está verde;
- AndroidTest fue compilado y la evidencia física se ejecutó sólo con
  autorización o quedó marcada honestamente como pendiente;
- Calendario, tarjeta superior y Horas y extras conservan sus recorridos;
- no se adaptaron próximo evento, notificaciones, widget ni otro bloque futuro;
- el diff está limpio de whitespace, sin staged y sin cambios fuera de alcance;
- el handoff vuelve a MAIN sin commit ni push.
