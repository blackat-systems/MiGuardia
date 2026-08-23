# Sector — Vigilancia privada

- Estado: MiGuardia 1.0 verificada; experiencia personalizable 2.0 confirmada
- Fuentes externas nuevas incorporadas: ninguna

## Evidencia heredada verificada

- MiGuardia 1.0.0 está especializada en Vigilancia privada y continúa como base
  de código reutilizable; no existe una instalación o historial de usuario que
  deba migrarse a 2.0.
- El cálculo histórico 1.0 utiliza una referencia mensual de 204 horas y una
  franja nocturna de 21:00 a 06:00.
- El vocabulario heredado utiliza `Objetivo`, `Guardia`, `Horario` y `Puesto`.
- Retén, cubrefranco o una guardia cubierta completa no se transforma
  automáticamente en horas extras.

Los valores 204 horas y 21:00–06:00 documentan el comportamiento de la prueba
1.0 y pueden usarse como casos explícitos de prueba. No son valores
predeterminados para una persona que elija Vigilancia privada en 2.0.

## Decisiones confirmadas para MiGuardia 2.0

- El usuario configura sus propios objetivos, tipos de trabajo, horarios y
  reglas. Pertenecer a Vigilancia privada no impone una modalidad única.
- `Objetivo` es la palabra visible preferida para el lugar de trabajo.
- Puede existir más de una guardia en el mismo día. Cada una conserva objetivo,
  abreviatura, horario exacto, tipo y color.
- Todo trabajo activo suma al total trabajado. Una clase marcada como extra se
  muestra también por separado.
- Las horas extras y las extensiones de turno guardan inicio y fin exactos. La
  aplicación no convierte automáticamente en extra una cobertura, un franco
  trabajado ni el tiempo que supera una referencia.
- Cada clase extra define si ayuda a completar la referencia de horas.
- La referencia puede ser conocida, desconocida o no utilizada, y puede medirse
  por mes, semana o ciclo personalizado.
- La disponibilidad pasiva es opcional. Si la persona la utiliza, elige una vez
  si quiere verla como `Guardia pasiva`, `Disponible para llamado` o `Retén`.
- Las reglas nocturnas, de fin de semana y de feriado se configuran para cada
  objetivo y pueden cambiar desde una fecha sin alterar jornadas anteriores.
- Los cambios de objetivo, horario o modalidad conservan el historial previo.

## Consecuencias para la interfaz

- Una instalación nueva pregunta la configuración real de la persona y no
  presenta 204 horas o 21:00–06:00 como una verdad universal.
- Toda primera apertura V2 comienza con el selector de rubro y sin 204 horas ni
  21:00–06:00 precargados. El código de cálculo V1 puede reutilizarse cuando
  respete la configuración elegida en V2.
- El Calendario admite varias guardias por día y abre el detalle completo al
  tocar la fecha.
- El Resumen incluye el total trabajado y separa extras, disponibilidad y
  cumplimiento sólo cuando la persona los utiliza.

## No inferir

- que toda empresa, objetivo o vigilador usa la misma referencia;
- que toda cobertura o exceso sobre una base es hora extra;
- que toda persona realiza disponibilidad pasiva;
- que nocturnidad, feriados o fines de semana implican automáticamente un pago;
- montos, liquidaciones o derechos laborales.
