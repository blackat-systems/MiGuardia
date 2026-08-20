# MiGuardia — edición directa sobre la grilla del Calendario

> Estado: contrato listo para implementación y auditoría de MAIN
>
> Fecha: 2026-08-18
>
> Rama asignada: `codex/calendar-unified-grid`
>
> Worktree asignado: `C:\Users\Joaquin\.codex\worktrees\16_CALENDARIO_GRILLA_UNIFICADA\MiGaurdia`
>
> Base canónica obligatoria: `6c2162e43aa3dc3f598dc294518fc19c338616e6` (`feat: add Vigilia navigation drawer`)

## 1. Misión

Corregir la experiencia de carga para que la grilla principal sea el único lugar donde se eligen una o varias fechas. En consulta, tocar un día abre detalles y ofrece `Editar día`. En edición, tocar las celdas selecciona fechas; la bandeja exige confirmar `Terminar de elegir días` antes de ofrecer Guardia o Francos y después usa exactamente ese conjunto en sus formularios.

La decisión explícita de Joa es que el Calendario no navegue ni sea reemplazado por una pantalla secundaria con otra grilla más chica, circular o de estilo diferente. Durante la edición simple o masiva debe seguir viéndose el mismo calendario mensual, con su tamaño, celdas, estados, colores y gesto mensual vigentes. Las herramientas aparecen debajo y el contenido puede desplazarse para alcanzarlas.

Antes de editar leer completos `AGENTS.md`, el prompt maestro, el contrato histórico `CALENDARIO_MODO_CONSULTA_Y_EDICION.md`, Objetivos/guardias, ADR 0003 y 0004, este documento, y el código/pruebas de Calendario, Gestión y mutaciones por lote. La instrucción actual de Joa y este contrato reemplazan la prohibición histórica de mostrar un acceso `Editar` en el detalle de consulta.

La rama ya nace de `main` limpia y publicada en el SHA indicado. El menú lateral Vigilia es parte de la base y no debe reimplementarse ni degradarse.

### 1.1 Estado real que explica el defecto

La base ya posee una única grilla principal con `CalendarInteractionMode.VIEW/EDIT`, pero el modo edición todavía usa el toque del día para abrir el detalle. La selección real de una o varias fechas continúa dentro de `SHIFT_FORM` y `DAY_OFF_FORM`, mediante otro `SelectableMonthCalendar`. Además, el detalle de consulta todavía no ofrece `Editar día`.

El incremento debe trasladar la selección al estado dueño del Calendario, separar consulta de selección y retirar esos dos calendarios duplicados sin borrar `SelectableMonthCalendar` de consumidores legítimos como Feriados.

## 2. Modelo de interacción

Separar explícitamente:

- `detailDate`: fecha cuyo contenido se consulta en un popup;
- `editSelectedDates`: conjunto de fechas elegidas para una operación.

No reutilizar un único campo para ambas responsabilidades.

### 2.1 Consulta

- Es el modo inicial.
- Tocar cualquier celda abre sus detalles sin escribir datos.
- Al final del popup aparece un botón de tamaño claro `Editar día`.
- `Editar día` cierra el popup, entra al mismo modo edición y preselecciona exactamente esa fecha.
- El botón grande `Editar calendario` entra en edición con selección vacía.
- `Editar día` y `Editar calendario` cambian solamente modo y selección; no abren por sí solos `SHIFT_FORM`, no crean borradores laborales y no escriben repositorios.
- El acceso a Fotos del mes permanece oculto en consulta.
- Clima y demás información de consulta permanecen accesibles.

### 2.2 Edición sobre la grilla

- El acceso a Fotos del mes se muestra únicamente en este modo.
- Se conserva mes, scroll y representación completa de guardias, colores, horarios, `F`, `?`, `CM`, vacaciones y feriados.
- Tocar una celda del mes visible alterna su selección; no abre el detalle.
- La selección se distingue visual y semánticamente sin ocultar el contenido del día.
- Sólo se seleccionan fechas del mismo mes visible.
- Cambiar de mes con selección no vacía debe pedir una decisión clara o limpiar de forma explícita antes de cambiar; nunca mezclar silenciosamente meses.
- Debajo de la grilla aparece una bandeja que separa selección y operación. La grilla continúa siendo parte visible de la misma pantalla y no se reemplaza por otra ruta o superficie de calendario.
- Sin selección, la bandeja explica brevemente que hay que elegir uno o varios días.
- Con selección todavía editable, la bandeja muestra la cantidad y una única acción principal `Terminar de elegir días`; no muestra todavía Guardia ni Francos.
- `Terminar de elegir días` confirma únicamente el conjunto elegido, bloquea la grilla y abre una etapa separada que pregunta qué se quiere cargar.
- Recién en esa etapa aparecen `Agregar guardia` y, cuando corresponda, `Agregar francos`, junto con `Modificar días elegidos`.
- `Modificar días elegidos` aparece como un control secundario delimitado dentro del bloque de selección y vuelve a la grilla editable sin vaciar ni modificar el conjunto elegido.
- Al elegir `Agregar guardia`, la zona inferior muestra o expande los selectores reales de objetivo, horario, puesto y confirmación necesarios. Al elegir `Agregar francos`, muestra la confirmación correspondiente. En ambos casos se conserva arriba la misma grilla con la selección visible.
- Crear o editar una plantilla de objetivo/horario puede abrir su superficie dueña existente. Al volver debe restaurar la selección y la bandeja sin introducir otro selector de fechas.
- Con una sola fecha ocupada, ofrecer acceso individual inequívoco a cada guardia para `Informar novedad / notas`, `Editar`, segunda guardia cuando corresponda y `Eliminar` confirmado.
- No inventar edición colectiva de guardias heterogéneas.
- `Salir de edición` abandona el modo, limpia la selección y conserva mes/posición. No confundirla con `Terminar de elegir días`, que sólo avanza a la elección de operación.
- Atrás recorre primero la etapa superior: desde la elección de operación vuelve a seleccionar conservando fechas; un formulario o confirmación superior mantiene prioridad; recién después se puede salir a consulta.

### 2.3 Jerarquía visual refinada para la carga

La decisión posterior de Joa del 18 de agosto de 2026 simplifica la zona inferior sin cambiar la selección ni las reglas anteriores:

- sin fechas elegidas se muestra solamente `Elegí uno o varios días` y una instrucción breve;
- con selección todavía editable se muestra un único contador y la acción grande `Terminar de elegir días`;
- después de confirmarla se muestra una pregunta breve, las acciones grandes `Agregar guardia` y, cuando corresponda, `Agregar francos`, y la acción secundaria delimitada `Modificar días elegidos`;
- `Modificar días elegidos` siempre vuelve a la selección preservando las fechas elegidas;
- al abrir cualquiera de esos formularios desaparecen por completo la tarjeta `Herramientas de edición`, su contador y las explicaciones técnicas; la grilla permanece visible, seleccionada y bloqueada;
- Guardia usa un contexto compacto con `Modificar días elegidos` dentro del mismo recuadro, muestra recientes antes que el explorador completo y revela puesto y vista previa sólo después de elegir objetivo y horario;
- Guardia diferencia la ausencia total de objetivos de la existencia de objetivos sin horarios utilizables: el primer caso inicia la creación del objetivo y el segundo pide elegir a cuál objetivo agregar el horario, sin carpetas vacías;
- Francos concentra cantidad, fechas exactas, `Modificar días elegidos` y la única acción primaria en un solo bloque delimitado;
- la confirmación final, las advertencias, la protección de borradores reales y las mutaciones atómicas permanecen vigentes; un formulario virgen vuelve directamente a la selección conservada sin una falsa advertencia de descarte.

### 2.4 Preparación de la primera carga

- Una base sin guardias muestra `Cargar datos`, no `Cargar mi primera guardia`.
- Hasta resolver la presencia global de guardias no se ofrece una entrada de edición; ante fallo se permite reintentar sin asumir que la base está vacía.
- La acción abre una preparación inline sin fecha preseleccionada, `ShiftDraft` ni formulario de guardia.
- La grilla no admite selección durante esa preparación, pero mes anterior/siguiente, Hoy, gesto horizontal y Fotos permanecen operativos.
- Se pueden crear varios objetivos y varios horarios usando los formularios reales. Guardar o cancelar vuelve a la preparación.
- `Continuar y elegir días` se muestra deshabilitado hasta que exista al menos un horario activo perteneciente a un objetivo activo.
- Crear el primer horario no avanza automáticamente: el usuario puede agregar más objetivos u horarios.
- Sólo `Continuar y elegir días` cierra la preparación y deja edición con selección vacía; recién entonces se eligen una o varias fechas.
- Después de elegirlas se debe tocar `Terminar de elegir días`; esa acción no guarda ni sale de edición, sólo habilita la elección Guardia/Francos.

## 3. Herramientas y formularios reales sin segundo calendario

- Guardia recibe `Set<LocalDate>` no vacío y el `YearMonth` correspondiente.
- Franco recibe el mismo tipo de selección ya resuelta.
- Retirar del formulario de guardia el selector `Una fecha`/`Varias fechas` y su `SelectableMonthCalendar`.
- Retirar del formulario de francos su segundo calendario.
- La selección de objetivo y horario para un alta debe componerse debajo de la grilla principal o extraerse como un componente reutilizable que la bandeja pueda mostrar allí. No alcanza con ocultar el calendario duplicado y seguir reemplazando toda la pantalla principal por `SHIFT_FORM`.
- La edición individual de una guardia existente puede reutilizar su superficie actual, pero no debe permitir cambiar fechas mediante un calendario secundario. La fecha o selección de entrada proviene del Calendario.
- Conservar objetivo, horario, puesto, vista previa, confirmación, advertencia de descanso, ocupadas, reemplazo, conservar libres, segunda guardia y cancelación.
- `SelectableMonthCalendar` puede permanecer donde otra función real lo use, por ejemplo Feriados; no borrarlo globalmente.
- La primera carga y el futuro tutorial no preseleccionan una fecha ni restauran un calendario duplicado: primero preparan objetivos y horarios; después dejan que el usuario elija sobre la grilla principal.
- En una base QA vacía, `Cargar datos` nunca abre por sí solo Guardia o Francos.

## 4. Consistencia y datos

- Reutilizar `ShiftBatchMutation` y su transacción atómica.
- Corregir la escritura de varios francos para que sea atómica: un fallo no puede dejar una parte del lote aplicada. Se permite ampliar contrato de repositorio/DAO y usar `@Transaction` sin cambiar tablas, columnas, índices, entidades, versión Room o esquemas.
- Guardia sobre `F` o `?` conserva el reemplazo atómico vigente; `CM` no se borra.
- Fechas omitidas por conflictos permanecen intactas.
- Dos guardias excepcionales y descanso menor a doce horas conservan advertencias y confirmación.
- No alterar UUID, instantáneas históricas, cálculos, notificaciones, clima, fotos o remuneración.

## 5. Mapa permitido

- estado y ViewModel del Calendario;
- composición de grilla, detalle y bandeja en `MiGuardiaApp.kt`/calendario;
- estado, ViewModel y pantallas de Gestión sólo para recibir selecciones externas y quitar calendarios duplicados;
- contratos/implementación/pruebas de estado diario exclusivamente para lote atómico de francos;
- entrada desde `MainActivity`/notificación;
- recursos y pruebas directamente relacionados;
- documentación y evidencia.

No cambiar manifiesto, permisos, dependencias, versión Room, entidades, esquemas, migraciones, perfil, vacaciones, feriados, clima, notificaciones, fotos o reglas remunerativas.

## 6. Pruebas mínimas

### Estado/JVM

- consulta y edición conservan responsabilidades separadas;
- `Editar día` preselecciona una fecha;
- `Editar calendario` empieza vacío;
- alternar fechas no sale del mes;
- confirmar la selección no modifica fechas ni escribe datos;
- volver a seleccionar conserva las fechas confirmadas;
- salir limpia selección sin alterar datos;
- cambio de mes no mezcla selecciones;
- lote de francos es atómico ante éxito y fallo.

### Compose/instrumentación

- consulta abre detalles y el único acceso de mutación es `Editar día`;
- pulsar `Editar día` no escribe por sí solo;
- edición selecciona/deselecciona sobre la grilla principal;
- contenido del día sigue legible mientras está seleccionado;
- bandeja recibe cantidad correcta;
- Guardia y Francos no aparecen antes de `Terminar de elegir días`;
- `Terminar de elegir días` bloquea la grilla y muestra una única elección de operación;
- `Modificar días elegidos` vuelve a la grilla editable conservando el conjunto;
- guardia/franco reciben exactamente las fechas elegidas;
- formularios no contienen un segundo calendario ni selector simple/múltiple;
- después de elegir `Agregar guardia` o `Agregar francos`, `month-grid` continúa existiendo y mostrando la selección mientras objetivo, horario o confirmación aparecen debajo;
- no existe una segunda cuadrícula circular o compacta en `SHIFT_FORM`/`DAY_OFF_FORM`;
- día con dos guardias identifica acciones por guardia;
- políticas de ocupadas, segunda guardia, descanso y borradores no regresan;
- entrada desde notificación abre detalle seguro en consulta;
- claro/oscuro/Sistema, zoom 100/150/200 %, retrato y paisaje.

Ejecutar `git diff --check`, pruebas afectadas, JVM global, lint, debug/release/QA y la instrumentación de aplicación y base alcanzada por el lote transaccional, siempre con `--max-workers=1`. Contar resultados reales por módulo.

## 7. QA físico y seguridad

El especialista no tiene autorización inicial para instalar APK ni ejecutar instrumentación física. Si Joa la autoriza después, usar sólo `com.blackatsystems.miguardia.qa` y `.qa.test` en el Samsung. Recorrer con datos ficticios: consulta, `Editar día`, edición masiva sobre la misma grilla, selección múltiple, objetivo/horario debajo, guardia, franco, ocupadas, dos guardias, Atrás, cambio de mes, tema y zoom. No tocar producción ni datos reales.

Room debe seguir en v5 con trece entidades, esquemas 1..5 y migraciones 1→2→3→4→5. No registrar datos laborales, fechas reales, rutas o contenido privado.

## 8. Entrega

Devolver a MAIN base/HEAD, estado Git, mapa de impacto, archivos, transición de estados, garantía transaccional, pruebas y conteos, QA físico realizado o pendiente, Room/hashes, privacidad, riesgos y pendientes. No hacer commit, push, merge o rebase sin autorización. MAIN auditará e integrará antes de iniciar Onboarding y la primera carga guiada.
