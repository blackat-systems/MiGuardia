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
- Una guardia guardada reemplaza el estado diario explícito `F` o `?` de esa fecha. La escritura de la guardia y el borrado del estado se realizan en una única `ShiftBatchMutation` y transacción Room. Una fecha omitida por `Conservar ocupadas` queda intacta. Las carpetas médicas (`CM`) continúan coexistiendo con advertencia y no se modifican.
- Las guardias históricas nuevas se persistirán como `PLANNED`; el calendario seguirá proyectándolas como `COMPLETED` a partir del reloj, sin agregar ese valor a `ShiftStatus`.
- El estado de formularios vivirá en `ManagementViewModel`, que sobrevive a la recreación normal de la actividad. Los formularios anidados volverán a la carga de guardia sin perder su borrador.
- En la carga de guardias, cada objetivo activo se presentará como una carpeta desplegable. Sus horarios activos y la acción `+ Agregar horario` vivirán dentro de esa carpeta; no existirá una lista plana mezclada ni una fila separada de botones por objetivo.
- El color de cada combinación se elegirá mediante un selector visual completo con campo de saturación/luminosidad, barra arcoíris de tono, vista previa y lectura RGB/HEX; no se limitará la gama disponible.
- Las confirmaciones satisfactorias de esta superficie se presentarán como avisos flotantes de 2,5 segundos, sin ocupar espacio estable del formulario.
- Las superficies de gestión respetarán los insets de dibujo seguro para no invadir las barras de estado o navegación.
- La acción inferior `Agregar` separa dos intenciones: `Agregar guardia` y `Agregar francos`. La primera ya conserva sus modos de una o varias fechas; la segunda persiste `F` explícitos sin borrar guardias ni otros datos coincidentes.
- Ante una única fecha ocupada, el diálogo ordena `Reemplazar`, `Agregar segunda guardia` y `Cancelar`. Para lotes se conserva además `Agregar sólo en días libres`, porque evita una mutación parcial ambigua.
- El detalle de una guardia deja como acciones principales solamente novedad/notas, edición y eliminación confirmada. La excepción de avisos por guardia sigue existiendo dentro del formulario de edición.

## Consecuencias

- Un fallo durante un lote revierte tanto eliminaciones como inserciones.
- Editar u ocultar plantillas no reescribe guardias históricas.
- El acceso a recientes no requiere una tabla nueva: se deriva mediante consulta sobre las cinco tablas existentes.
- Room permanece en versión 1, sin migración ni cambio de esquema.
- Las excepciones laborales siguen siendo posibles, pero siempre requieren una confirmación explícita y reversible desde la interfaz.
