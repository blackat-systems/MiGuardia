# Investigación por sector — MiGuardia 2.0

- Estado: activa
- Fecha de actualización: 2026-08-21

## Propósito

Esta carpeta evita mezclar las reglas laborales de Vigilancia privada, Policía,
Enfermería y Medicina. Cada sector tiene su propia ficha y ninguna respuesta
individual se transforma en una regla general.

Las fichas distinguen siempre:

- evidencia anónima recibida;
- decisiones expresas de Joaquin;
- asuntos que todavía necesitan más evidencia;
- supuestos que MiGuardia no debe hacer.

## Fuentes incorporadas

| Fuente | Contenido anónimo | Uso permitido |
|---|---|---|
| `policia.xlsx` | Una respuesta del sector policial | Orienta vocabulario y necesidades concretas; no define por sí sola a todo el sector |
| `ENFERMERA.xlsx` | Dos respuestas a un formulario de trabajo en salud | Evidencia sanitaria compartida mientras no se identifique de qué sector proviene cada respuesta |
| `MEDICA.xlsx` | El mismo conjunto de dos respuestas de `ENFERMERA.xlsx` | No cuenta como una segunda muestra ni como evidencia independiente de Medicina |

`ENFERMERA.xlsx` y `MEDICA.xlsx` contienen los mismos encabezados, marcas
temporales y respuestas. Hasta identificar su origen, esas dos filas no se
atribuyen por separado a Enfermería o Medicina y no se cuentan dos veces.

No se registran nombres, correos, teléfonos, matrículas, instituciones,
dependencias ni otros datos personales.

## Decisiones comunes confirmadas por Joaquin

- El catálogo contiene exactamente Vigilancia privada, Policía, Enfermería y
  Medicina. No existe `Salud` ni `Otro` como sector.
- Existe una sola configuración laboral por persona. Sus cambios rigen desde
  una fecha elegida y no modifican el pasado.
- La configuración es personal: dos personas del mismo sector pueden usar
  referencias, lugares, tipos de trabajo y disponibilidades diferentes.
- `Lugar de trabajo` es el concepto común. La interfaz adapta la palabra visible
  al sector sin duplicar el motor ni la base de datos.
- Una persona puede tener varios lugares, varias plantillas y más de una jornada
  en el mismo día.
- Cada jornada conserva lugar, tipo de trabajo, horario exacto y sus datos
  históricos, aunque una plantilla cambie después.
- El trabajo habitual y las horas extras suman al total trabajado. Las extras se
  muestran además por separado y siempre guardan inicio y fin exactos.
- Cada clase de trabajo extra decide si también ayuda a completar una referencia
  de horas. Trabajar más tiempo no crea extras automáticamente.
- La referencia de horas puede ser conocida, existir pero ser desconocida o no
  utilizarse. Cuando existe, puede ser mensual, semanal o por un ciclo
  personalizado.
- La disponibilidad pasiva es opcional. `Guardia pasiva`, `Disponible para
  llamado` y `Retén` son nombres posibles para un mismo concepto, no tres clases
  distintas.
- Si existe trabajo activo durante una disponibilidad, sólo el tramo
  superpuesto deja de contar como pasivo.
- `Consultorio` y `Capacitación` representan actividades diferentes y nunca se
  mezclan en una misma clase de trabajo.
- MiGuardia organiza jornadas y horas. No calcula montos, liquidaciones ni
  interpretaciones salariales o legales.

## Fichas

| Sector | Archivo | Estado de evidencia |
|---|---|---|
| Vigilancia privada | `VIGILANCIA_PRIVADA.md` | Base 1.0 verificada; configuración personal 2.0 confirmada |
| Policía | `POLICIA.md` | Una respuesta incorporada y decisiones de producto confirmadas |
| Enfermería | `ENFERMERIA.md` | Evidencia sanitaria compartida, todavía sin atribución sectorial independiente |
| Medicina | `MEDICINA.md` | Evidencia sanitaria compartida, todavía sin atribución sectorial independiente |

## Cómo incorporar nuevas respuestas

Por cada lote:

1. indicar fecha, cantidad de respuestas y sector comprobado;
2. resumir patrones sin copiar datos personales;
3. separar coincidencias de respuestas aisladas;
4. registrar contradicciones y límites de la muestra;
5. contrastar la evidencia con las decisiones ya confirmadas;
6. pedir una decisión únicamente si cambia el producto o el cálculo.

Una respuesta orienta nombres, casos y recorridos. Los comportamientos continúan
siendo configurables salvo que exista una decisión expresa que los vuelva
comunes a todos.
