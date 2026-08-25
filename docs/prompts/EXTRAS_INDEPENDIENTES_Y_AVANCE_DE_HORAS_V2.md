# Extras independientes y avance de horas V2

- Estado: **HABILITADO — SIN IMPLEMENTACIÓN ABIERTA**
- Fecha: 2026-08-25
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `2e6138509e4ef6c5faf09657cb6bf094cb7ae610`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Extras independientes y avance de horas**

## QUÉ HACE

Permite registrar un bloque de trabajo extra que no nació como extensión de
una jornada ya cargada. La persona elige fecha, horario exacto, lugar, tipo de
trabajo y clase extra; después puede verlo en el Calendario, corregirlo o
eliminarlo conscientemente.

También permite configurar una meta mensual, semanal o por ciclo, elegir desde
qué fecha se reinicia el conteo y consultar cuánto trabajo habitual y extra se
realizó, cuánto ayuda a cumplir la meta y cuánto falta o se superó.

## POR QUÉ EXISTE

MiGuardia ya registra el horario real y puede clasificar la diferencia de una
jornada existente. Todavía no puede representar un servicio, guardia o bloque
extra completo que exista por sí solo, ni puede responder de forma confiable
`cuánto trabajé` y `cómo vengo con mis horas`.

Esta dependencia completa las fuentes de trabajo activo y crea el primer motor
visible de avance. Existe antes de disponibilidad y situaciones especiales
porque esos bloques necesitarán apoyarse después en un conteo activo ya exacto
y comprobado.

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
4. inspeccioná completos configuración laboral, referencias, jornadas,
   horario real, clases extra, Calendario, Room V2 y sus pruebas;
5. detenete ante un mismatch, HEAD separado, cambios sin dueño o una decisión
   material no resuelta; no descartes ni reemplaces trabajo.

## TASK

Implementar exclusivamente dos capacidades conectadas:

1. **Extras independientes:** crear, consultar, corregir y eliminar un trabajo
   extra exacto que no depende de una jornada existente.
2. **Referencia y avance:** configurar la referencia de horas, elegir cuándo
   reiniciar su conteo y calcular el avance con jornadas habituales, extras de
   jornadas y extras independientes.

El recorrido mínimo debe permitir:

1. entrar a `Referencia y avance de horas` desde `Mi forma de trabajar`;
2. elegir si no usa referencia, la desconoce, usa una meta fija o informa un
   valor por período;
3. elegir período mensual, semanal o ciclo cuando corresponda;
4. confirmar desde qué fecha local comienza o se reinicia el conteo;
5. ver antes de guardar qué tramo termina y cuál empieza;
6. consultar el avance del tramo vigente sin convertir faltantes en cero;
7. entrar desde el Calendario a `Registrar trabajo extra`;
8. elegir lugar, tipo, clase extra, inicio y final exactos y puesto opcional;
9. revisar y guardar el registro atómicamente;
10. verlo en la fecha de inicio, identificado expresamente como extra;
11. corregirlo o eliminarlo con confirmación y actualización inmediata;
12. ver el avance recalculado después de cualquier cambio.

No implementar todavía disponibilidad, guardia pasiva, situaciones especiales
nuevas, Resumen personalizable, Calendario final, tarjeta superior final,
adaptación de próximo evento, adaptación de notificaciones, widget, informes,
copias, bloqueo ni Ayuda.

## CONTEXT

La base cerrada ya posee:

- una experiencia exclusivamente V2;
- cuatro rubros exactos: Vigilancia privada, Policía, Enfermería y Medicina;
- una configuración laboral con revisiones vigentes desde `LocalDate`;
- revisiones que hoy no distinguen todavía la vigencia general de la fecha
  específica en que se reinició la referencia;
- `HoursReference` pendiente, no usada, desconocida, fija o por período;
- períodos mensuales, semanales con primer día configurable y ciclos
  personalizados con anclaje;
- valores por período donde ausencia significa `Falta informar`;
- lugares, tipos y plantillas reutilizables;
- carga manual, edición/eliminación y planes recurrentes;
- pares obligatorios `Shift + ShiftWorkSnapshot`;
- horario real y fragmentos extra exactos de una jornada;
- catálogo persistente de `ExtraWorkClass` con fotografía histórica;
- Room `MiGuardiaV2Database` versión 3, archivo `miguardia-v2.db` y
  veinticinco tablas;
- Calendario, `F/?`, notas, feriados, vacaciones, carpetas médicas, fotos,
  próximo evento, notificaciones, clima y zoom interno como capacidades
  comunes.

La primera apertura guarda `HoursReference.PendingSetup`, pero todavía no hay
una superficie completa para elegir la referencia. Tampoco existe un motor V2
que combine horario planificado/real, clases extra y períodos para mostrar
avance.

ADR 0029 cerró la decisión que faltaba: la persona elige conscientemente desde
qué fecha reiniciar el conteo. MiGuardia no prorratea una meta ni la aplica
hacia atrás.

Esquemas Room protegidos:

- SHA-256 `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- SHA-256 `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- SHA-256 `3.json`:
  `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0018, 0019, 0020, 0022, 0023, 0025, 0026, 0027, 0028 y 0029;
- `docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md`;
- `docs/prompts/REGISTRAR_HORARIO_REAL_Y_CLASIFICAR_HORAS_EXTRA_V2.md`;
- `docs/audits/2026-08-25-horario-real-y-horas-extra-v2.md`;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
- `HoursReference`, `HoursPeriod`, `PerPeriodHoursValues`,
  `WorkConfiguration`, `WorkConfigurationHistory` y sus pruebas;
- `WorkConfigurationRepository`, implementación Room, DAO, entidades y
  mapeos;
- `Shift`, `ShiftWorkSnapshot`, `ShiftActualAggregate`,
  `ShiftExtraInterval`, `ExtraWorkClass` y sus pruebas;
- repositorios de jornadas, horario real, catálogo y configuración;
- `MiGuardiaV2Database`, `LocalDataStore`, migraciones, integridad, fixtures,
  pruebas Room y esquemas `1.json`, `2.json` y `3.json`;
- `V2ShiftActual*`, `WorkSetup*`, detalle del día, Calendario,
  `MainActivity`, `MiGuardiaApp` y sus pruebas;
- próximo evento y notificaciones sólo como consumidores protegidos que no se
  adaptan en este bloque.

No recuperes código desde worktrees históricos. Si una fuente histórica
contradice este prompt o ADR 0029, prevalece la jerarquía activa de V2.

## DEPENDENCIES

Esta tarea depende de contratos ya integrados:

- vigencia de configuración por fecha local;
- referencia mensual, semanal y por ciclo;
- valores informados por período;
- catálogo laboral y fotografías históricas;
- jornadas manuales y recurrentes;
- horario real y fragmentos extra;
- clases extra persistentes;
- Room V2 exclusiva con cadena `1→2→3`.

Debe dejar un motor puro y observable para que disponibilidad, situaciones
especiales y el Resumen final se integren después sin duplicar fórmulas.

## DECISIONES CONGELADAS

### 1. Elección visible de la referencia

La superficie ofrece estas decisiones, sin imponer valores por sector:

- `Todavía no la configuré` mientras siga pendiente;
- `No uso una referencia de horas`;
- `Tengo una referencia, pero no sé cuántas horas`;
- `La cantidad es fija`;
- `La cantidad cambia en cada período`.

Cuando la referencia usa período, permite:

- mes calendario;
- semana con primer día configurable y lunes sugerido;
- ciclo con cantidad positiva de días y fecha de anclaje.

Una cantidad conocida usa minutos enteros positivos. No sugerir 204 horas ni
otro valor por sector. Un valor faltante permanece `Falta informar`.

La persona puede informar o corregir el valor de un período mediante las
fronteras existentes. Corregir un valor exige confirmación y no reinterpreta
la identidad de la definición o su ventana.

### 2. Reinicio elegido por la persona

Toda creación o cambio de referencia pregunta desde qué fecha local comienza
el nuevo conteo. Como mínimo ofrece:

- `Desde hoy`;
- `Desde el próximo inicio del período`, con texto concreto como
  `Desde el próximo lunes` o `Desde el 1 de septiembre`;
- `Elegir otra fecha`.

`Elegir otra fecha` admite pasado, presente o futuro dentro de la línea
temporal V2 ya configurada. No puede preceder la primera revisión laboral; la
interfaz muestra ese límite sin extender la línea temporal por inferencia. Una
fecha pasada exige una confirmación de retrocarga que muestre qué tramos y
resultados se recalcularán. No modifica jornadas, horarios reales ni
fotografías.

`Desde hoy` comienza al inicio de la fecha local actual. No representa un
reinicio a mitad de una hora. La revisión muestra siempre la fecha exacta, el
período elegido y el primer tramo resultante.

La revisión laboral conserva un marcador explícito
`hoursReferenceStartedOn`. No alcanza con deducir el reinicio de
`EffectiveRevision.effectiveFrom`, porque una revisión puede cambiar otro dato
laboral sin reiniciar el contador.

- La referencia anterior continúa hasta el día anterior.
- El conteo nuevo comienza en cero en la fecha confirmada.
- Una fecha futura conserva el conteo anterior hasta que llegue.
- Si el corte cae dentro de un período natural, el tramo nuevo usa la meta
  completa; no se prorratea.
- La pantalla advierte que ese primer tramo será más corto y ofrece mover el
  inicio al próximo límite normal.
- Los períodos posteriores recuperan sus límites mensuales, semanales o de
  ciclo configurados.
- Una revisión que cambia otro dato sin cambiar la referencia no reinicia el
  conteo: copia `hoursReferenceStartedOn` sin modificarlo.
- Guardar el mismo valor como una nueva referencia requiere la acción
  consciente de reinicio y actualiza `hoursReferenceStartedOn`; no ocurre como
  efecto colateral de otra edición.

El marcador es obligatorio para una referencia fija, por período o desconocida
con período definido. Es nulo mientras la referencia siga pendiente, no se use
o todavía no tenga un período que contar.

No aplicar la meta nueva a días anteriores, no sumar dos metas y no inventar
una proporción diaria.

### 3. Tramos de progreso

El dominio modela explícitamente un tramo de referencia con:

- fecha inicial inclusiva;
- fecha final exclusiva;
- revisión y referencia dueñas;
- ventana natural mensual, semanal o de ciclo;
- meta definida, faltante, desconocida o no utilizada;
- motivo del límite: inicio de período, reinicio o revisión siguiente.

Un reinicio puede partir una ventana natural en dos tramos. Cada tramo es
consultable e histórico. El actual se resuelve con `Clock` y `ZoneId`
inyectables; no usa el reloj global.

La fecha dueña se resuelve así:

- una jornada sin horario real usa la fecha local de `Shift.startAt`;
- una jornada con horario real usa la fecha local de `actualStartAt` en la zona
  preservada; todos sus fragmentos extra siguen a esa misma jornada;
- un extra independiente usa la fecha local de su inicio exacto.

La fuente completa pertenece al tramo que contiene su fecha dueña. Si cruza
medianoche o el límite del período, no se parte entre dos metas, igual que la
regla mensual ya aprobada. El Calendario conserva una jornada corregida en su
fecha planificada, pero el avance puede atribuirla a otro tramo si el inicio
real cambió de día. Las clasificaciones nocturna, feriada o de fin de semana
continúan mirando los instantes y no crean minutos nuevos.

### 4. Extra independiente

El extra independiente no tiene `shiftId` y no se guarda como un `Shift`
habitual. Tiene identidad y repositorio propios.

Debe conservar como mínimo:

- UUID;
- `timelineId`, sector y revisión de configuración aplicable;
- `workPlaceId`, `objectiveId`, `workTypeId` y `templateId` opcional;
- `extraWorkClassId`;
- fecha local dueña, zona, inicio y final exactos;
- nombre, abreviatura, dirección opcional, tipo, color y puesto opcional como
  fotografía laboral;
- nombre de clase, si ayuda a la referencia y si tendrá desglose propio como
  fotografía de clase;
- `createdAt` y `updatedAt` normalizados a milisegundos.

Reglas:

- intervalo positivo, en minutos enteros y semántica `[inicio, fin)`;
- puede cruzar día, mes, año y durar más de 24 horas;
- representa trabajo ya realizado: el final no puede ser posterior al
  `Clock.instant()` inyectado y la fecha local dueña no puede ser futura;
- todo el intervalo pertenece a una única clase extra;
- lugar, tipo y clase deben estar activos al crear o reclasificar;
- una plantilla activa es opcional: si se elige, aporta el color y puede
  precargar el horario, que siempre se confirma como intervalo realizado;
- sin plantilla, elegir color es obligatorio; ese color se guarda en la
  fotografía del extra y no crea ni modifica una plantilla;
- una corrección sin reclasificación puede conservar fotografías históricas
  de fuentes luego archivadas;
- cambiar fuentes activas genera una fotografía nueva sin alterar el pasado de
  otros registros;
- no puede cruzar timeline o sector;
- nombres de sugerencia no crean clases silenciosamente;
- clase archivada no sirve para una elección nueva, pero su historia permanece;
- programar extras independientes futuros queda fuera de alcance y no se
  reemplaza por una jornada habitual falsa;
- dos trabajos activos superpuestos muestran datos concretos y requieren
  confirmación; si se conservan, ambos suman completos;
- carpeta médica o vacaciones generan advertencia consciente y se preservan;
- `F/?` se preserva sin cambios aunque exista trabajo extra en esa fecha;
- una operación cancelada o fallida no deja filas parciales.

Crear, corregir o eliminar usa CAS por el registro completo, fuentes
observadas, ocupación vecina y protecciones consultadas. Un conflicto conserva
el borrador y exige refrescar.

### 5. Motor puro de trabajo y avance

El motor recibe fuentes exactas y produce, en minutos enteros:

- habitual trabajado;
- extras de jornadas por clase;
- extras independientes por clase;
- total trabajado;
- trabajo que ayuda a cumplir;
- trabajo que no ayuda a cumplir;
- pendiente programado;
- meta del tramo cuando existe;
- faltante o superación;
- porcentaje sólo cuando la meta es positiva y conocida.

Reglas de cálculo:

```text
Total trabajado = habitual trabajado + todas las extras trabajadas

Cumplimiento = habitual elegible
             + extras cuya fotografía histórica indica que ayudan
```

- Una jornada usa horario real cuando existe y planificado en caso contrario.
- En una jornada con horario real, los fragmentos extra se restan de lo
  habitual y se suman una sola vez como extra.
- Un extra independiente válido suma toda su duración como extra realizada.
- Una fuente futura no suma todavía como trabajada; integra pendiente.
- Una fuente en curso suma sólo los minutos transcurridos hasta el minuto del
  reloj normalizado y conserva el resto como pendiente.
- `ABSENT` y `CANCELLED` no suman trabajo.
- Una jornada sin horario real dentro de vacaciones o carpeta médica conserva
  los contratos vigentes y no se transforma silenciosamente en trabajo.
- Un trabajo real declarado o un extra independiente no se borra por coexistir
  con una protección; se advierte y conserva.
- Dos fuentes activas superpuestas que la persona confirmó suman completas.
- Noche, feriado y fin de semana son desgloses, no sumandos nuevos.
- Superar la meta produce `superación`, nunca una clase extra automática.
- Meta desconocida, no usada, pendiente o faltante no produce cero, porcentaje,
  faltante ni superación falsos.
- Los resultados usan operaciones con detección de overflow.

No persistir estos resultados. Deben recalcularse al cambiar jornadas,
horarios reales, extras, clases, valores por período o configuración.

### 6. Room V2 versión 4

Agregar una fuente persistente propia para extras independientes, con nombre de
tabla estable `independent_extra_work_records`.

Agregar también a `work_configuration_revisions` el campo local nullable
`hoursReferenceStartedOn`. No es una tabla ni una segunda línea temporal: forma
parte de la fotografía versionada de cada revisión laboral.

La tabla contiene los datos e instantáneas definidos arriba y declara claves e
índices suficientes para:

- identidad primaria;
- consultas por timeline, sector y fecha local;
- consultas por intervalo;
- relación con raíz de configuración, revisión, lugar, objetivo, tipo,
  plantilla opcional y clase extra;
- integridad de contexto sin depender sólo de validación de interfaz.

La migración explícita `3→4`:

- crea la tabla vacía;
- agrega `hoursReferenceStartedOn` a las revisiones laborales;
- para datos v3, recorre cada línea temporal por `effectiveFrom`: la primera
  referencia con período usa esa fecha; una referencia realmente distinta usa
  la fecha de su revisión; una revisión ajena que conserva exactamente la misma
  `HoursReference` copia el marcador anterior; pendiente, no usada o sin
  período usan nulo;
- no infiere reinicios conscientes del mismo valor en datos v3: esa acción no
  existía antes de v4 y revisiones idénticas se interpretan como continuidad;
- preserva las veinticinco tablas y todos los datos V2;
- no convierte jornadas existentes en extras independientes;
- no crea referencias, reinicios, metas ni clases por defecto;
- no usa fallback destructivo;
- no abre, copia, borra ni migra `miguardia.db`;
- conserva la cadena `1→2→3→4`;
- exporta `4.json`;
- no modifica `1.json`, `2.json` ni `3.json`.

La base queda con veintiséis tablas de aplicación. Configurar o cambiar la
referencia reutiliza la línea temporal y la persistencia existentes; no crea
una segunda fuente de vigencia.

Room no guarda totales, porcentajes, faltantes ni cumplimiento.

### 7. Fronteras de escritura y observación

Crear un repositorio explícito para extras independientes. Su implementación
Room es la única autorizada para observar, crear, corregir y eliminar esos
agregados.

La configuración de referencia continúa pasando por
`WorkConfigurationRepository`. Si una confirmación necesita crear definición,
revisión y valor inicial, debe hacerlo como una sola operación atómica pública;
no coordinar escrituras parciales desde el ViewModel.

La frontera pública recibe expresamente la fecha elegida de reinicio. Una
mutación laboral ajena debe transportar el marcador anterior, mientras un
reinicio consciente del mismo valor debe persistir uno nuevo. Después de
reabrir Room ambos casos deben seguir siendo distinguibles.

Los ViewModels no escriben DAO ni tablas. No agregar un escritor alternativo
de `Shift`, `ShiftActual` o `ExtraWorkClass`.

Las observaciones del avance reaccionan a todas sus fuentes. Error al leer una
fuente no se interpreta como lista vacía ni cero horas.

### 8. Flujo visible de referencia y avance

Desde `Mi forma de trabajar` existe una acción única
`Referencia y avance de horas`.

La pantalla distingue:

- `LOADING`: espera neutral sin valores falsos;
- `CONTENT`: referencia, tramo y avance disponibles;
- `ERROR`: explicación y reintento sin asumir cero.

En contenido muestra:

- período o ausencia de referencia;
- fecha del último reinicio y próximo límite;
- meta o `Falta informar`;
- trabajado habitual;
- extras;
- total;
- cuánto ayuda a cumplir;
- faltante o superación sólo cuando puede calcularse;
- acceso consciente a configurar o cambiar referencia.

No simular el Resumen final ni agregar tarjetas configurables. Esta es una
vista funcional acotada del motor.

El borrador de configuración conserva referencia, período, día inicial,
anclaje, cantidad, modo por período, fecha de reinicio, confirmación del tramo
corto y revisión mediante `SavedStateHandle`.

### 9. Flujo visible de extra independiente

`Registrar trabajo extra` aparece sólo cuando la configuración está lista y
existe al menos un lugar, tipo y clase extra utilizables. Si falta una clase,
permite crearla mediante el coordinador existente sin perder el borrador.

La acción nace desde una celda de la única grilla mensual o desde su detalle y
conserva esa fecha. No agrega otro calendario ni un selector mensual paralelo;
para cambiar de día se vuelve a la grilla única.

La secuencia mínima:

1. identificar fecha y configuración;
2. elegir lugar y tipo;
3. elegir plantilla opcional o cargar inicio/final exactos;
4. elegir clase extra;
5. confirmar el color de la plantilla o elegirlo si no hay plantilla;
6. puesto o función opcional;
7. revisar horario, duración, fotografías y efecto sobre la referencia;
8. confirmar;
9. volver al Calendario actualizado.

El detalle del día diferencia una jornada habitual de un extra independiente,
muestra clase, horario, lugar, puesto y si ayuda a la referencia. Ofrece
`Corregir trabajo extra` y `Eliminar trabajo extra` como acciones separadas.

Abrir, escribir, revisar, volver o cancelar no persiste. Guardar, corregir o
eliminar cierra sólo su superficie y conserva la fecha de detalle.

La fecha dueña, borrador, expectativa, fuentes, horario, clase, puesto,
confirmaciones y etapa sobreviven a recreación mediante `SavedStateHandle`.

### 10. Accesibilidad y límites visuales

- scroll e IME utilizables;
- tags únicos por UUID;
- ninguna clasificación depende sólo del color;
- texto que distingue habitual, extra, meta, faltante y superación;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del
  sistema;
- mensajes en español claro, sin fórmulas laborales o legales.

## OUTPUT

La dependencia debe entregar:

- dominio puro para tramos de referencia y avance;
- configuración visible de referencia y reinicio con marcador histórico
  explícito;
- modelo, repositorio y persistencia de extras independientes;
- migración explícita `3→4` y esquema `4.json`;
- integración acotada con Calendario y detalle del día;
- vista funcional `Referencia y avance de horas`;
- borradores, errores, CAS y recreación;
- pruebas puras, JVM, Room instrumentadas, Compose y Activity;
- handoff verificable a MAIN con diff sin commit.

La documentación canónica, ADR, índice, STATUS y auditoría final pertenecen a
MAIN y no se modifican desde esta dependencia.

## SCOPE

Puede modificar solamente lo necesario dentro de:

- `core/domain/src/main/**` y `core/domain/src/test/**`;
- `core/database/src/main/**`;
- `core/database/src/test/**`;
- `core/database/src/androidTest/**`;
- `core/database/schemas/**` únicamente para `4.json`;
- `app/src/main/**`;
- `app/src/test/**`;
- `app/src/androidTest/**`.

Puede adaptar proyecciones del Calendario y detalle sólo para incluir extras
independientes y actualizar el avance. No rediseñar superficies vecinas.

## DO NOT

No:

- modificar `AGENTS.md`, `docs/**` ni fuentes canónicas;
- cambiar los cuatro rubros ni crear `Salud` u `Otro`;
- unir Enfermería y Medicina;
- imponer 204 horas, lunes, nocturnidad o reglas por sector;
- prorratear una meta;
- aplicar una referencia nueva hacia atrás;
- inferir un reinicio desde cualquier revisión laboral ajena;
- convertir superación en extra;
- tratar referencia desconocida, no usada o faltante como cero;
- guardar un extra independiente como `Shift` habitual;
- guardar minutos extra sin inicio y final;
- crear clases extra por defecto o silenciosamente;
- duplicar minutos al combinar horario real y fragmentos;
- fusionar trabajos activos superpuestos;
- implementar disponibilidad o descontar tiempo pasivo;
- implementar situaciones especiales nuevas o su consolidación final;
- crear el Resumen personalizable;
- adaptar tarjeta superior, próximo evento, notificaciones o widget;
- implementar recurrencia de extras independientes;
- agregar dependencias de producción;
- modificar Gradle, manifiesto, permisos, `applicationId`, versión o SDK;
- tocar DataStore salvo una necesidad demostrada y autorizada por MAIN;
- modificar `1.json`, `2.json` o `3.json`;
- conectar con Room V1 o `miguardia.db`;
- usar fallback destructivo;
- acceder a red, cuentas, nube, telemetría o datos reales;
- registrar datos privados en logs;
- abrir, instalar, limpiar o desinstalar producción;
- consultar o modificar `font_scale`, densidad o tamaño visual del sistema;
- crear commit, push, tag, merge, rebase, reset o descarte;
- crear otra tarea, rama, worktree o subagente.

Ante una necesidad real fuera de alcance, detenete y devolvé a MAIN el punto
exacto. No inventes una extensión.

## VALIDATION

### 1. Dominio JVM

Cubrir como mínimo:

- referencias pendiente, no usada, desconocida, fija y por período;
- valor por período definido y `Falta informar`;
- mes, semana con cualquier primer día y ciclos de 14, 21 y 28 días;
- reinicio hoy, próximo lunes, próximo mes, próximo ciclo y fecha elegida;
- corte dentro de un período sin prorrateo;
- tramo anterior intacto y tramo futuro todavía no vigente;
- revisión ajena a la referencia que no reinicia;
- reinicio consciente con la misma meta que sí cambia el marcador;
- horario planificado sin real, real menor, real mayor habitual y real con uno
  o varios fragmentos extra;
- extra independiente pasado y finalizado exactamente en el minuto actual;
- rechazo de extra independiente en curso o futuro;
- medianoche, fin de mes/año, febrero bisiesto y más de 24 horas;
- fuente que cruza el límite atribuida al tramo de inicio;
- clase que ayuda y que no ayuda;
- clase renombrada/archivada sin reinterpretar fotografía;
- superposiciones activas que suman completas;
- pendiente, trabajado, total, cumplimiento, faltante y superación;
- ausencia de meta sin cero o porcentaje falso;
- no extra automático por superar, feriado, noche o fin de semana;
- reloj, zona, minuto normalizado y overflow.

### 2. Room

Verificar:

- migración `3→4` desde una base 3 poblada en las veinticinco tablas;
- backfill determinista de `hoursReferenceStartedOn` y conservación después de
  reabrir;
- cadena `1→2→3→4`;
- `1.json`, `2.json` y `3.json` byte a byte intactos;
- `4.json` exportado y hash informado;
- veintiséis tablas exactas;
- alta, corrección, eliminación y reapertura de extra independiente;
- intervalos realizados, finalizados en el minuto actual y multidiarios;
- rechazo persistente de intervalos en curso o futuros;
- fotografías históricas y clase archivada;
- relaciones de configuración, lugar, tipo, plantilla opcional y clase;
- rollback total, doble toque y conflicto CAS;
- `integrity_check` y `foreign_key_check`;
- rechazo de huérfanos, cruces de timeline/sector e intervalos inválidos;
- Flows reactivos;
- configuración de referencia atómica cuando crea revisión, definición o valor.

### 3. App JVM, Compose y Activity

Cubrir como mínimo:

- entrada visible a referencia/avance;
- `LOADING / CONTENT / ERROR` y reintento;
- opciones de referencia sin valor inicial inventado;
- `Desde hoy`, próximo límite y fecha elegida con resumen exacto;
- advertencia y confirmación de tramo corto;
- borrador y recreación en todas las etapas;
- meta conocida, desconocida, no usada y falta informar;
- avance recalculado tras horario real, clase, extra o referencia;
- registrar, revisar, guardar, corregir y eliminar extra independiente;
- lugar/tipo/clase archivados o modificados antes de confirmar;
- superposición, carpeta médica y vacaciones preservadas;
- error, rollback, reintento, doble toque y conflicto;
- regreso al mismo Calendario/detalle actualizado;
- fecha proveniente de la única grilla y ausencia de un segundo calendario;
- color tomado de plantilla o elegido expresamente sin crear una nueva;
- preservación de `F/?` al guardar, corregir o eliminar;
- una y varias jornadas junto con un extra independiente;
- ausencia de disponibilidad, situaciones nuevas y Resumen final;
- regresión de carga, edición, recurrencias y horario real;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- accesibilidad sin depender sólo de color.

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
- búsqueda de escritores directos de `Shift` y tablas nuevas;
- búsqueda de `fallbackToDestructiveMigration`;
- comparación/hash de `1.json`, `2.json` y `3.json`;
- auditoría de secretos, logs, red, permisos, Gradle y manifiesto.

### 5. Android y dispositivos

Compilar AndroidTest es obligatorio pero no equivale a ejecutarlo.

La dependencia sólo puede usar Samsung QA o emulador API 26 si Joaquin lo
autoriza expresamente en esa tarea. Con autorización:

- usar únicamente paquetes QA/test y datos ficticios;
- no abrir ni modificar producción;
- probar migración, Room, configuración, extras, avance, recreación y
  regresiones;
- revisar claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- no consultar ni modificar ajustes visuales del sistema;
- desinstalar sólo los paquetes QA autorizados al finalizar;
- informar exactamente qué quedó en cada dispositivo.

Si no existe autorización o un dispositivo no está disponible, marcar la
evidencia como `PENDIENTE`. No presentar APK compilado como QA física.

## HANDOFF A MAIN

Entregar en español, de forma compacta y verificable:

```text
# HANDOFF A MAIN — Extras independientes y avance de horas V2

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
- conteos reales de pruebas;
- qué fue sólo compilado y qué se ejecutó;
- versión Room, tablas, migración y SHA-256 de esquemas;
- paquetes usados y estado final de dispositivos;
- límites no implementados;
- `git status`, `git diff --check` y confirmación de cero staged;
- confirmación de que no hubo commit, push, tag, merge, rebase, reset ni
  descarte.

El resultado queda directamente en el checkout compartido, sin commit. No
existe nada para cherry-pick. MAIN audita cada hunk, repite pruebas
proporcionales, encarga una revisión independiente y decide el checkpoint.

## DONE WHEN

La dependencia se considera candidata sólo cuando:

- la referencia puede configurarse sin valores sectoriales inventados;
- la persona elige y confirma la fecha de reinicio;
- un cambio laboral ajeno conserva el inicio anterior y un reinicio consciente
  del mismo valor queda distinguible;
- un corte dentro del período no prorratea ni reescribe el pasado;
- un extra independiente puede crearse, verse, corregirse y eliminarse;
- sus intervalos y fotografías históricas son exactos;
- el motor combina habitual, fragmentos extra y extras independientes sin
  doble conteo;
- meta desconocida, no usada o faltante nunca aparece como cero;
- faltante y superación sólo se muestran cuando son calculables;
- Room migra de 3 a 4 sin perder datos y con esquemas anteriores intactos;
- escrituras y correcciones son atómicas y protegidas por CAS completo;
- Calendario, avance, borradores, errores y recreación están cubiertos;
- la batería local requerida está verde;
- la evidencia Android se ejecutó con autorización o quedó marcada
  honestamente como pendiente;
- no se implementó disponibilidad, situaciones especiales, Resumen final ni
  otro bloque futuro;
- el diff está limpio de whitespace, sin staged y sin cambios fuera de alcance;
- el handoff vuelve a MAIN sin commit ni push.
