# ADR 0030: disponibilidad como ventana pasiva independiente

- Estado: aceptada
- Fecha: 2026-08-25

## Contexto

MiGuardia V2 ya registra jornadas planificadas, horario real, extras de una
jornada y extras independientes. La configuración laboral también conserva un
nombre opcional para disponibilidad, pero ese nombre no representa períodos
reales ni permite aplicar la decisión confirmada de que el trabajo activo
reemplaza sólo el tramo pasivo coincidente.

El próximo stage general reúne disponibilidad, situaciones especiales y el
cierre del motor de horas. Implementarlo como una sola entrega mezclaría una
migración Room, categorías todavía no estabilizadas y el cálculo final. La
orquestación vigente ya permite separarlo en dependencias consecutivas.

La documentación tampoco definía qué hacer cuando dos ventanas pasivas se
superponen.

## Decisión

El stage general se divide en este orden:

1. guardias pasivas y disponibilidad;
2. ausencias, cancelaciones y otras situaciones especiales;
3. conteo final de horas y cumplimiento.

La primera dependencia adopta estas reglas:

- existe un solo concepto interno con tres nombres visibles exactos: Guardia
  pasiva, Disponible para llamado y Retén;
- el nombre es parte de la configuración laboral versionada y puede estar
  ausente;
- una ventana pasiva tiene identidad, intervalo exacto, fecha dueña, zona,
  revisión laboral y fotografía histórica propias;
- no se guarda como jornada, horario real, extra ni situación especial;
- dos ventanas de la misma línea temporal no pueden superponerse; los límites
  contiguos sí son válidos;
- la disponibilidad efectiva es la ventana programada menos la unión del
  trabajo activo coincidente;
- la unión se usa sólo para no descontar dos veces la pasiva; los trabajos
  activos confirmados conservan sus reglas de suma;
- la disponibilidad se muestra separada y nunca integra trabajo, cumplimiento,
  faltante o superación;
- los estados transcurrido, en curso y futuro se derivan con reloj y zona
  inyectables;
- Room V2 evoluciona de 4 a 5 mediante una tabla propia y migración explícita;
- los esquemas 1 a 4 y todos los datos previos permanecen intactos;
- situaciones especiales nuevas y consolidación final quedan para las dos
  dependencias siguientes.

## Alternativas descartadas

### Guardar disponibilidad como Shift

Se descarta porque convertiría tiempo pasivo en trabajo y contaminaría el total
y el cumplimiento.

### Deducir disponibilidad de días vacíos

Se descarta porque un día sin jornadas significa sin definir, no disponibilidad.
Además no expresa inicio ni final exactos.

### Permitir dos ventanas pasivas superpuestas

Se descarta porque hoy existe un único concepto sin lugar o empleador propio.
Dos registros simultáneos serían ambiguos y podrían duplicar el tiempo pasivo.

### Implementar todo el stage en una sola dependencia

Se descarta porque obligaría a estabilizar a la vez disponibilidad, situaciones
especiales y todas sus consecuencias sobre el motor. La separación deja cada
migración utilizable y auditable.

## Consecuencias

- Se agrega una fuente local nueva sin alterar jornadas ni extras existentes.
- Calendario y Horas y extras pueden observarla de forma reactiva.
- La edición del nombre requiere una mutación laboral atómica que preserve la
  referencia y su fecha de reinicio.
- El cálculo necesita unir intervalos activos antes de restarlos.
- La dependencia siguiente puede incorporar situaciones especiales sobre una
  fuente pasiva ya estable.
- Resumen, próximo evento y notificaciones seguirán pendientes hasta sus bloques
  propios.
