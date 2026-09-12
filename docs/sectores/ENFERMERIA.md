# Sector — Enfermería

- Estado: sector independiente confirmado; evidencia específica todavía abierta
- Fuente recibida: `ENFERMERA.xlsx`
- Limitación: contiene las mismas dos respuestas que `MEDICA.xlsx`

## Alcance de la evidencia recibida

`ENFERMERA.xlsx` y `MEDICA.xlsx` contienen el mismo conjunto de dos respuestas.
No se conoce todavía a qué sector pertenece cada una. Por eso estas filas se
registran como evidencia compartida de trabajo en salud y no como dos respuestas
confirmadas de Enfermería.

Las respuestas muestran, sin permitir generalizaciones, que:

- se usan palabras diferentes como `Guardia`, `Turno` y `Consultorio`;
- una persona informó un solo lugar y otra más de uno;
- se mencionó la especialidad como dato útil para identificar una jornada;
- ninguna de las dos respuestas informó realizar guardias pasivas;
- una respuesta mencionó extensiones ocasionales de salida, sin pedir registrar
  el horario real;
- aparecieron capacitaciones y congresos entre los casos especiales;
- horario e institución fueron mencionados como datos útiles del Calendario;
- los recordatorios solicitados variaron entre inicio, feriados y necesidades
  personales.

Una respuesta mencionó citas individuales. MiGuardia continúa limitado a
bloques laborales y no almacena pacientes, historias clínicas ni información
clínica.

## Decisiones confirmadas por Joaquin

- Enfermería es un sector independiente de Medicina.
- `Institución` o `Servicio` son palabras posibles para presentar el lugar de
  trabajo. La persona completa únicamente los datos que realmente utiliza.
- La arquitectura es personalizable: no todas las personas tienen la misma
  referencia, realizan pasivas, trabajan en varios lugares o necesitan los
  mismos tipos de turno.
- Una persona puede crear varios lugares, varios tipos de trabajo, varias
  plantillas y más de una jornada en una misma fecha.
- `Consultorio` y `Capacitación` son actividades distintas y no se mezclan en
  una misma clase, aunque compartan lugar u horario.
- Las horas extras y las extensiones guardan inicio y fin exactos. Cada clase
  extra decide si ayuda a completar la referencia y siempre aparece dentro del
  total trabajado.
- La referencia de horas puede ser conocida, desconocida o no utilizada, y ser
  mensual, semanal o por un ciclo personalizado.
- La disponibilidad pasiva es opcional. Si se utiliza, la persona elige el
  nombre visible y registra su horario exacto.
- Los cambios de modalidad rigen desde la fecha elegida; nunca borran jornadas
  o configuraciones anteriores.

## Qué necesita más evidencia de Enfermería

- vocabulario preferido para turnos, guardias, servicios, salas y funciones;
- patrones habituales de jornadas fijas, rotativas o dobles;
- uso real de referencias de horas y sus períodos;
- frecuencia y tratamiento organizativo de disponibilidad pasiva;
- información prioritaria del Calendario y del Resumen.

Estas preguntas sirven para mejorar nombres y recorridos. No bloquean el motor
personalizable común ni autorizan un valor predeterminado sectorial.

## No inferir

- que las dos respuestas compartidas pertenecen a Enfermería;
- que Enfermería y Medicina usan el mismo contrato;
- que toda persona trabaja ocho horas, en un solo lugar o sin pasivas;
- una referencia, nocturnidad, convenio o fórmula salarial predeterminada;
- que una respuesta individual representa a todo el sector.
