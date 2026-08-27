# ADR 0031: Resumen derivado y presentación personalizable

- Estado: aceptada
- Fecha: 2026-08-27

## Contexto

MiGuardia V2 ya conserva las fuentes necesarias para explicar el trabajo:
jornadas, horario real, extras, referencias de horas, reglas por lugar,
feriados, protecciones y disponibilidad. También existe `HoursProgress` para un
tramo de referencia. Todavía no existe una proyección mensual única ni una
pantalla Resumen V2.

Guardar totales mensuales crearía una fuente paralela que podría quedar vieja
al corregir una jornada. Copiar la fórmula de `HoursProgress` dentro de un
ViewModel produciría dos resultados potencialmente distintos. Mezclar las
preferencias visuales con Room también confundiría historia laboral con la
forma elegida de verla.

## Decisión

### Proyección derivada

El Resumen se calcula en dominio puro desde las fuentes vigentes. No escribe ni
persiste totales, porcentajes, faltantes o desgloses.

La proyección mensual reutiliza `HoursProgress` o ayudantes puros compartidos
para conservar una sola semántica de trabajo habitual, extra, pendiente y
cumplimiento. Una misma instantánea produce tanto las cifras como el libro de
contribuciones que explica cada valor.

El total principal usa las fuentes cuya fecha dueña pertenece al mes. La fecha
dueña conserva el contrato V2: inicio planificado sin horario real, inicio real
cuando existe corrección, inicio exacto para extras independientes y fecha de
inicio fotografiada para disponibilidad.

### Períodos de cumplimiento

El mes sólo elige qué períodos mostrar. Una semana o ciclo que toca ese mes se
calcula completo, aunque empiece antes o termine después. Reinicios y cambios de
referencia conservan sus tramos reales y nunca se prorratean.

Una referencia pendiente, no utilizada, desconocida o sin valor informado
mantiene ese estado. No se transforma en cero.

### Presentación personalizable

El Resumen muestra automáticamente total, habitual, extras cuando existan,
pendiente, cumplimiento calculable y disponibilidad aplicable. Los detalles de
noche, feriado, fin de semana, planificado/real, lugar, tipo, clase extra y
situaciones existentes pueden mostrarse, ocultarse y ordenarse.

La personalización no cambia reglas ni fuentes. Las preferencias de orden,
visibilidad y explicación inicial se guardan en un DataStore Preferences
exclusivo. Room permanece como dueño de los datos laborales.

Las reglas históricas `showDedicatedSummary` de lugar o clase limitan qué puede
aparecer como desglose separado. Una preferencia visual puede ocultar una
sección habilitada, pero no reactivar una que su fuente histórica no autorizó.

### Explicación de cifras y privacidad

Tocar una cifra abre un detalle de sólo lectura formado por las mismas
contribuciones exactas. No consulta una segunda fórmula y no permite editar.
Notas, motivos médicos, explicaciones privadas, direcciones y fotos quedan
excluidos.

Las situaciones se limitan a fuentes ya existentes. Este bloque no recupera
Novedades V1 ni crea capacitación, intercambio, cobertura u otras situaciones
futuras.

## Consecuencias

- Room V2 continúa en versión 5 con veintisiete tablas y esquemas intactos.
- Los futuros informes pueden reutilizar la proyección sin guardar agregados.
- Corregir cualquier fuente actualiza el Resumen de forma reactiva.
- DataStore puede evolucionar la disposición visual sin migrar historia
  laboral.
- El dominio debe probar igualdad entre las cifras y sus contribuciones, además
  de coherencia con `HoursProgress`.
- Próximo evento, notificaciones, widget e informes permanecen fuera de este
  bloque.

## Alternativas descartadas

### Guardar un total por mes en Room

Se descarta porque duplicaría información derivable y exigiría reconciliarla
ante cada cambio histórico.

### Calcular dentro de Compose

Se descarta porque acoplaría reglas laborales a presentación y dificultaría
pruebas deterministas.

### Cortar semanas o ciclos al final del mes

Se descarta porque alteraría la referencia elegida por la persona y produciría
un cumplimiento artificial.

### Permitir fórmulas creadas por el usuario

Se descarta porque el alcance aprobado personaliza la presentación, no el
motor de cálculo.
