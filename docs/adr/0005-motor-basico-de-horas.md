# ADR 0005: motor básico de horas y resumen mensual

- Estado: aceptada
- Fecha: 2026-08-13
- Autoridad: dependencia MOTOR BÁSICO DE HORAS

## Contexto

MiGuardia necesita convertir las guardias y novedades locales ya persistidas en un resumen mensual verificable. El cálculo debe seguir siendo correcto para guardias nocturnas, meses distintos, cortes parciales por el reloj y clasificaciones que pueden superponerse, sin convertir horas a valores decimales ni cambiar el esquema Room.

La aplicación todavía no cuenta con un editor local de feriados. Sin embargo, el motor debe aceptar sus fechas como entrada para que ese módulo futuro no obligue a redefinir las fórmulas.

## Decisión

- La API pública `calculateMonthlyHours` vivirá en `core:domain` y recibirá `YearMonth`, listas de guardias, estados diarios y carpetas médicas, un `Instant` de referencia, fechas feriadas y el umbral mensual.
- Todos los cálculos usarán intervalos semiabiertos de instantes y `Duration`. No se usarán horas decimales ni aproximaciones.
- Una guardia pertenece íntegramente al mes de su `localStartDate`, aunque cruce de día o mes.
- Las horas planificadas conservarán la duración original de todas las guardias. Cada duración se clasificará una sola vez, con esta precedencia: ausencia, cancelación, carpeta médica y, finalmente, división entre trabajada y pendiente según el reloj.
- Se mantendrá la invariante `planificadas = trabajadas + pendientes + ausentes + canceladas + carpeta médica`.
- Las horas extra serán únicamente la parte de las trabajadas que exceda las 204 horas mensuales. Alcanzar exactamente el umbral no generará extra.
- Las horas nocturnas serán la intersección real de horas trabajadas con ventanas locales de 21:00 inclusive a 06:00 exclusiva.
- Las horas feriadas serán la intersección real de horas trabajadas con los días civiles recibidos. Las clasificaciones nocturna y feriada podrán superponerse sin sumarse nuevamente a las trabajadas.
- Los francos se contarán por fechas explícitas únicas del mes. Las carpetas médicas se contarán por la unión de sus fechas, recortada al mes.
- `SummaryViewModel` observará los tres repositorios existentes. Actualizará el cálculo al cambiar Room, al comenzar o terminar una guardia, en cada medianoche local y, solamente mientras una guardia esté en curso, en el siguiente límite de minuto.
- El mes de Resumen se conservará en `SavedStateHandle`. La interfaz mostrará carga, error recuperable, estado vacío y contenido, respetando los insets administrados por el `Scaffold` principal.
- La interfaz usará una única distribución, la tipografía predeterminada de MiGuardia y la densidad estable del dispositivo como referencia. No adaptará su estructura ni su escala según `font_scale`, zoom, tamaño de visualización o densidad configurada por el usuario.
- Hasta que exista el editor correspondiente, producción pasará un conjunto vacío de feriados y la pantalla lo explicará de forma explícita.

## Consecuencias

- El motor puede probarse en JVM sin Android ni Room.
- `ShiftStatus` permanece limitado a `PLANNED`, `CANCELLED` y `ABSENT`; lo completado continúa derivándose del reloj.
- Room permanece en versión 1, sin entidades, DAO, migraciones ni contratos modificados.
- El resumen no calcula remuneración ni deducciones y no presume reglas de SUVICO aún no confirmadas.
- El futuro módulo de feriados solo deberá proporcionar fechas locales al motor existente.
