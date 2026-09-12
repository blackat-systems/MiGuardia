# ADR 0029: reinicio explícito del conteo y extras independientes

- Estado: aceptado
- Fecha: 2026-08-25

## Contexto

MiGuardia 2.0 ya distingue el horario planificado del horario real y puede
clasificar como extra una diferencia ocurrida dentro de una jornada existente.
También posee referencias mensuales, semanales o por ciclo y conserva sus
cambios desde una fecha.

Faltaban dos contratos para calcular avance de manera confiable:

1. qué ocurre cuando la persona cambia su referencia mientras un período está
   en curso;
2. cómo representar un trabajo extra completo que no nació como extensión de
   una jornada existente.

Prorratear automáticamente una meta o aplicar una regla nueva hacia atrás
inventaría una decisión laboral que MiGuardia no conoce. Guardar un extra
independiente como una jornada habitual también perdería su clasificación real.

## Decisión

### 1. La persona elige cuándo reiniciar el conteo

Al crear o cambiar una referencia, MiGuardia pregunta desde qué fecha local
debe comenzar el nuevo conteo. La interfaz ofrece como mínimo:

- `Desde hoy`;
- `Desde el próximo inicio del período`, con texto concreto como
  `Desde el próximo lunes`;
- `Elegir otra fecha`.

`Desde hoy` significa desde la fecha local actual, no desde un segundo u hora
intermedia. La confirmación siempre muestra la fecha exacta.

`Elegir otra fecha` admite pasado, presente o futuro dentro de la línea
temporal V2 ya configurada. No puede preceder la primera revisión laboral. Una
fecha pasada se trata como retrocarga consciente: antes de guardar muestra los
tramos y resultados históricos que serán recalculados. No reescribe jornadas,
horarios reales ni fotografías.

La fecha elegida crea una nueva revisión de la misma configuración laboral. La
revisión guarda además un inicio explícito de la referencia —conceptualmente
`hoursReferenceStartedOn`—. La referencia anterior continúa hasta el día previo
y el contador nuevo comienza en cero en la fecha confirmada. Una fecha futura
no cambia el conteo actual antes de llegar.

El inicio de la referencia no se deduce de cualquier `effectiveFrom`. Una
revisión que cambia un dato ajeno copia el inicio anterior sin alterarlo. Una
acción consciente de reinicio actualiza ese inicio aunque la meta y el período
sean iguales. De ese modo ambos casos permanecen distinguibles después de
cerrar y reabrir la aplicación.

Si el reinicio corta un mes, semana o ciclo ya comenzado, se forman dos tramos
históricos. La meta del tramo nuevo se usa completa: MiGuardia no la prorratea,
no combina metas y no modifica el tramo anterior. La pantalla explica si el
primer tramo nuevo será más corto y permite elegir el próximo inicio normal del
período para evitarlo.

La referencia semanal conserva su primer día configurable; lunes continúa
siendo sólo la sugerencia. El ciclo conserva su cantidad de días y fecha de
anclaje. La referencia mensual conserva meses calendario. Un reinicio explícito
puede cortar el primer período de cualquiera de estas variantes, pero los
períodos completos posteriores vuelven a sus límites normales.

Una revisión que cambia otro dato sin cambiar ni reiniciar conscientemente la
referencia no reinicia horas. Una referencia desconocida, no utilizada o cuyo
valor por período falta nunca se interpreta como cero.

### 2. El extra independiente es una fuente propia de trabajo activo

Un extra independiente representa un bloque de trabajo con:

- identidad estable;
- inicio y final exactos;
- zona y fecha local dueña;
- lugar y tipo de trabajo;
- clase extra;
- fotografía histórica del contexto laboral y de la clase;
- puesto o función opcional;
- timestamps necesarios para corrección segura.

Su fecha se toma de la única grilla mensual o del detalle de ese día. No se
crea un segundo calendario. La plantilla es opcional: si se utiliza aporta el
color de la fotografía; sin plantilla, la persona elige un color explícito que
se guarda sólo en el registro y no crea una plantilla nueva.

No depende de un `shiftId` y no se guarda como un `Shift` habitual ficticio.
Se muestra en el Calendario y representa trabajo ya realizado: el final exacto
no puede ser posterior al reloj inyectado y su fecha local dueña no puede ser
futura. Programar un extra independiente futuro queda fuera de este bloque y
no se sustituye por una jornada habitual falsa. Su creación, corrección y
eliminación usan un repositorio propio, CAS por valor completo y una transacción
atómica.

Todo su intervalo pertenece a la clase extra elegida. La fotografía histórica
decide si ayuda a cumplir la referencia y no cambia si la clase se renombra,
se archiva o modifica sus opciones más adelante. Dos trabajos activos
superpuestos se advierten; si la persona los conserva, ambos suman completos.
Guardar un extra no borra ni altera los estados explícitos `F/?`; pueden
coexistir porque describen el día y el trabajo realizado por separado.

### 3. El avance se deriva, no se persiste como total

El motor combina:

- jornadas habituales, usando horario real cuando existe y planificado en caso
  contrario;
- fragmentos extra de una jornada;
- extras independientes;
- la revisión y el tramo de referencia aplicables;
- valores informados por período cuando corresponda.

La fecha dueña del cálculo se determina sin ambigüedad:

- una jornada sin horario real usa la fecha local de su inicio planificado;
- una jornada con horario real usa la fecha local de su inicio real en la zona
  preservada; sus fragmentos extra siguen a esa misma jornada;
- un extra independiente usa la fecha local de su inicio exacto.

La fuente completa pertenece al tramo que contiene esa fecha dueña, aun si
finaliza después de medianoche o cruza un reinicio. La celda del Calendario de
una jornada corregida continúa siendo su fecha planificada, como fijó ADR 0028;
la atribución de horas puede pertenecer a otro tramo si su inicio real cambió
de día.

Produce minutos habituales, extras por clase, total trabajado, trabajo que
ayuda a cumplir, meta, faltante o superación y pendiente programado. Superar la
meta no convierte tiempo habitual en extra.

Room guarda únicamente fuentes, fotografías e historia. No guarda totales,
faltantes, porcentajes ni resultados mensuales opacos.

## Consecuencias

- El siguiente bloque debe ofrecer la configuración visible de la referencia y
  su fecha de reinicio antes de mostrar cumplimiento.
- Un reinicio inmediato puede producir deliberadamente un primer tramo corto
  con la meta completa; la persona lo elige después de ver la consecuencia.
- La retrocarga de una referencia recalcula resultados derivados, pero no
  modifica fuentes históricas de trabajo ni estados explícitos del Calendario.
- Los totales pueden recalcularse después de corregir horario real, extras,
  clases o referencias sin reescribir historia.
- La próxima migración de Room parte de `MiGuardiaV2Database` versión 3 y debe
  preservar los esquemas `1.json`, `2.json` y `3.json`.
- La migración incorpora el inicio explícito a las revisiones laborales y una
  tabla propia para extras independientes; no crea una segunda línea temporal
  de configuración.
- Disponibilidad y situaciones especiales se integrarán después sobre este
  motor; no se anticipan en el bloque de extras independientes.

## Alternativas descartadas

### Prorratear automáticamente la meta

No existe una regla universal que permita distribuir una meta por días sin
preguntarle a la persona.

### Aplicar la referencia nueva a todo el período en curso

Reinterpretaría días anteriores a la fecha elegida y rompería la historia por
vigencia.

### Mantener siempre la referencia anterior hasta el período siguiente

Impediría el reinicio inmediato que Joaquin decidió ofrecer como elección
consciente.

### Guardar el extra independiente como una jornada habitual

Obligaría a inferir después que todo su intervalo era extra y mezclaría dos
fuentes con semánticas diferentes.
