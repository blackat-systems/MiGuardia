# ADR 0004: objetivos, horarios y mutaciones de guardias

- Estado: aceptada
- Fecha: 2026-08-13
- Autoridad: MAIN, después de auditar la entrega de OBJETIVOS Y GUARDIAS

## Contexto

MiGuardia necesita administrar plantillas locales de objetivos y horarios, cargar una o varias guardias y resolver fechas ocupadas sin alterar por accidente el historial ni dejar lotes parcialmente escritos. También debe advertir superposiciones, segundas guardias y descansos menores a 12 horas, pero permitir que Joaquin confirme excepciones reales.

El calendario ya proyecta como completada una guardia `PLANNED` cuyo final pasó. Este incremento debe reutilizar esa decisión y conservar el esquema Room versión 1.

## Decisión

- Objetivos y combinaciones objetivo-horario seguirán siendo plantillas editables u ocultables; cada guardia conservará sus instantáneas históricas de nombre, abreviatura, dirección, horario, color y puesto.
- Crear guardias, reemplazar fechas ocupadas y duplicar varias guardias se expresará como una `ShiftBatchMutation` validada antes de abrir una transacción Room y aplicada de forma atómica.
- Editar una guardia conservará su UUID, fecha de creación y estado persistido. Si la nueva fecha ya tiene otra guardia, la interfaz solo permitirá conservarla como segunda guardia o cancelar la edición; no ofrecerá un reemplazo ambiguo.
- Las advertencias se calcularán en lógica pura de `core:domain`. Las guardias `CANCELLED` y `ABSENT` no participarán del cálculo de superposición ni descanso.
- Los horarios recientes se ordenarán por el `createdAt` más nuevo de una guardia que los haya utilizado, con un máximo predeterminado de cinco, y solo incluirán objetivos y combinaciones activos.
- Los estados diarios explícitos (`F` y `?`) y las carpetas médicas (`CM`) coexistirán con una guardia nueva: se advertirá al usuario, pero no se eliminarán ni modificarán.
- Las guardias históricas nuevas se persistirán como `PLANNED`; el calendario seguirá proyectándolas como `COMPLETED` a partir del reloj, sin agregar ese valor a `ShiftStatus`.
- El estado de formularios vivirá en `ManagementViewModel`, que sobrevive a la recreación normal de la actividad. Los formularios anidados volverán a la carga de guardia sin perder su borrador.
- Las superficies de gestión respetarán los insets de dibujo seguro para no invadir las barras de estado o navegación.

## Consecuencias

- Un fallo durante un lote revierte tanto eliminaciones como inserciones.
- Editar u ocultar plantillas no reescribe guardias históricas.
- El acceso a recientes no requiere una tabla nueva: se deriva mediante consulta sobre las cinco tablas existentes.
- Room permanece en versión 1, sin migración ni cambio de esquema.
- Las excepciones laborales siguen siendo posibles, pero siempre requieren una confirmación explícita y reversible desde la interfaz.
