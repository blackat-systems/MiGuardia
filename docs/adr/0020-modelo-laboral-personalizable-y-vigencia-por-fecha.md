# ADR 0020: modelo laboral personalizable y vigencia por fecha

- Estado: aceptada
- Fecha: 2026-08-21

## Contexto

La investigación de MiGuardia 2.0 demostró que dos personas del mismo sector
pueden usar nombres, períodos de referencia, horarios, extras y disponibilidad
distintos. El diseño provisional anterior suponía revisiones mensuales, una
fórmula automática de exceso y extras sin horario exacto. Esas decisiones ya no
representan el producto aprobado.

## Decisión

### Configuración

- Existe una sola configuración laboral por usuario.
- Sus cambios rigen desde una `LocalDate` concreta hasta el siguiente cambio.
- El sector adapta vocabulario y ejemplos; no impone reglas universales.
- La configuración se compone de lugar, tipo de trabajo, plantilla, jornada,
  plan recurrente, clase extra, referencia de horas, disponibilidad y
  situaciones especiales.
- El historial se conserva mediante instantáneas y revisiones; nunca se
  reescribe por editar una plantilla o regla.

### Horas

- Todo trabajo activo suma al total trabajado.
- Las extras se declaran expresamente, poseen intervalo exacto y se muestran
  separadas.
- Cada clase extra define si ayuda a completar la referencia.
- Superar una referencia no crea extras automáticamente.
- Una referencia puede ser mensual, semanal o por ciclo, conocida, desconocida
  o no utilizada.
- Dos trabajos activos solapados se advierten y suman independientemente cuando
  el usuario los conserva.
- La actividad reemplaza sólo el tramo coincidente de una disponibilidad; la
  unión temporal se usa para no descontar dos veces la misma disponibilidad.
- Noche, feriado y fin de semana son clasificaciones que no agregan tiempo.
- La jornada pertenece al día y mes de inicio, aun si sus clasificaciones cruzan
  medianoche.

### Persistencia

- Room guarda fuentes e historia, no agregados mensuales opacos.
- La actualización desde v5 usa migraciones explícitas y no destructivas.
- El motor legado continúa disponible para la historia de 1.0 hasta una
  activación V2 consciente y fechada.
- Las preferencias puramente visuales permanecen en DataStore.

## Consecuencias

- `YearMonth` no puede ser la clave de vigencia de la configuración general.
- La fórmula `max(R-B, 0)` no clasifica automáticamente horas extra en V2.
- Se elimina el modelo de duración extra sin horario.
- `passiveEnabled` no se deriva del sector; la capacidad es una elección del
  usuario y los registros históricos permanecen visibles.
- Consultorio y Capacitación necesitan tipos o plantillas distintas aunque
  compartan lugar y horario.
- Los cálculos se implementan primero en dominio puro con `Clock`, `ZoneId`,
  minutos enteros e intervalos semiabiertos.

## Alternativas descartadas

### Revisión únicamente mensual

Se descarta porque impide representar cambios que comienzan en una fecha real y
no sirve para referencias semanales o por ciclo.

### Fórmula universal por sector

Se descarta porque una muestra individual no representa a todas las personas y
porque los contratos dependen de institución, función y modalidad.

### Extras automáticas por exceso

Se descarta porque una diferencia sobre la referencia no demuestra por sí sola
que el tiempo sea extra. La clasificación requiere una decisión consciente.

### Destruir los datos de 1.0

Joaquin autorizó reemplazar el comportamiento heredado si fuera necesario, pero
no existe una ventaja técnica que justifique borrar historia. Se adopta la
migración no destructiva como decisión obligatoria.
