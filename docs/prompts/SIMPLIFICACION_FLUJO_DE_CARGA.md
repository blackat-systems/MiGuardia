# MiGuardia — simplificación del flujo de carga

> **HISTÓRICO V1 — NO EJECUTAR.** El flujo ya forma parte de la base 1.0.0. Ver
> `docs/prompts/README.md`.

> Estado histórico: implementado en MiGuardia 1.0; no ejecutar nuevamente
>
> Fecha: 2026-08-18
>
> Rama asignada: `codex/calendar-edit-flow-polish`
>
> Worktree asignado: `C:\Users\Joaquin\.codex\worktrees\17_SIMPLIFICACION_FLUJO_DE_CARGA\MiGaurdia`
>
> Base canónica obligatoria: `3e1b286a58307b55d7f1d2ac1b7ffa58dacccc87` (`feat: unify calendar editing grid`)

## 1. Rol y misión

Sos el especialista de `17_SIMPLIFICACION_FLUJO_DE_CARGA`. Tu misión es simplificar visualmente la carga de guardias y francos sin quitar capacidades ni alterar reglas de negocio.

Joa aprobó aplicar divulgación progresiva: la grilla mensual continúa siendo el único selector de fechas, pero la zona inferior debe mostrar una sola decisión por etapa. Al abrir Guardia o Francos desaparecen por completo la tarjeta `Herramientas de edición` y sus explicaciones técnicas repetidas.

No aceptes este documento como evidencia de que el código ya cumple. Inspeccioná implementación, pruebas y estado Git real antes de editar.

No hacer commit, push, merge, rebase, reset, instalación física ni instrumentación en el Samsung. La entrega vuelve sin commit a MAIN para auditoría e integración. El QA físico requiere una autorización posterior y explícita de Joa.

## 2. Lectura obligatoria en orden

1. `AGENTS.md` completo.
2. `docs/PROMPT_MAESTRO_MAIN.md` completo.
3. Este documento completo.
4. `docs/prompts/CALENDARIO_SELECCION_DIRECTA.md`.
5. `docs/prompts/CALENDARIO_MODO_CONSULTA_Y_EDICION.md`.
6. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`.
7. `docs/adr/0003-proyeccion-y-calendario-mensual.md`.
8. `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`.
9. `MiGuardiaApp.kt`, `ManagementScreens.kt`, componentes Vigilia y sus pruebas relacionadas.

La decisión explícita más reciente de Joa y este contrato refinan la presentación del flujo unificado. No reabren la decisión de usar una única grilla ni autorizan cambios de negocio.

## 3. Puerta 0 — línea base

Antes de editar, registrar:

- ruta absoluta;
- rama;
- `HEAD` completo;
- SHA de `main` y `origin/main`;
- `git status --short --branch --untracked-files=all`;
- `git worktree list --porcelain`;
- archivos no rastreados;
- relación exacta con `3e1b286a58307b55d7f1d2ac1b7ffa58dacccc87`;
- `git diff --check`.

La rama debe comenzar limpia en el SHA indicado. Si existe cualquier diferencia previa, no sobrescribirla ni descartarla: detener la edición, identificarla e informar a MAIN.

## 4. Problema verificado

El flujo actual es funcional, pero muestra simultáneamente demasiada información:

- `Herramientas de edición` sigue visible cuando ya se abrió Guardia o Francos;
- el aviso `La selección se hace únicamente sobre la grilla mensual de arriba` permanece cuando ya dejó de ser útil;
- la selección se repite en la grilla, el contador, la cabecera del formulario y la vista previa;
- Guardia muestra juntos mes, fechas, recientes, explorador, creación, carpetas, puesto, vista previa y guardado;
- `Usados recientemente` ocupa lugar incluso vacío;
- el puesto opcional y la vista previa aparecen antes de ser necesarios;
- Francos repite título, explicación, mes y cantidad;
- varias `SectionCard` consecutivas generan cajas, bordes y divisores que compiten entre sí.

El objetivo no es eliminar funciones. Es reducir repetición, agrandar decisiones importantes y revelar cada bloque cuando corresponde.

## 5. Contrato de interacción final

### 5.0 Preparación inicial de datos

Cuando todavía no existe ninguna guardia, la acción inferior se llama `Cargar datos`.

La acción aparece sólo después de resolver la consulta global de guardias. Mientras esa señal está pendiente se muestra una espera neutral; un fallo ofrece reintento y nunca habilita por defecto una ruta que saltee la preparación.

- Entra al Calendario en edición con selección vacía y abre una preparación inline, sin crear `ShiftDraft` ni abrir Guardia.
- Mientras la preparación está activa, las celdas no se seleccionan; anterior, siguiente, Hoy, gesto mensual y Fotos continúan operativos.
- La preparación permite crear tantos objetivos y horarios como el usuario necesite mediante los formularios reales.
- Guardar o cancelar objetivo/horario vuelve a la preparación y conserva lo ya confirmado.
- `Continuar y elegir días` siempre es visible, pero sólo se habilita cuando existe al menos un horario activo de un objetivo activo.
- El primer horario no autoavanza. Sólo el toque consciente en `Continuar y elegir días` cierra la preparación y habilita la grilla, todavía sin fechas elegidas.
- Objetivos u horarios ocultos no completan el requisito. Mientras el catálogo real todavía carga, no se presenta falsamente un estado vacío.

### 5.1 Etapa A — elegir días

La grilla principal conserva tamaño, contenido, colores, semántica, gesto mensual y selección simple/múltiple.

Debajo de la grilla:

- sin selección, mostrar solamente un encabezado directo equivalente a `Elegí uno o varios días` y una instrucción breve `Tocá las fechas que querés modificar`;
- no mostrar botones deshabilitados ni explicaciones técnicas adicionales;
- con selección, reemplazar la instrucción por un único resumen: `1 día seleccionado` o `N días seleccionados`;
- mostrar una sola acción principal grande, con altura mínima visual de 56 dp: `Terminar de elegir días`;
- no mostrar todavía `Agregar guardia`, `Agregar francos` ni acciones individuales: en esta etapa la única decisión pendiente es terminar la selección;
- `Terminar de elegir días` no guarda, no abre un formulario y no sale del modo edición; confirma el conjunto y avanza a la elección de operación;
- conservar una acción secundaria distinta `Salir de edición`, que vuelve a consulta y limpia la selección;
- no repetir la lista completa de fechas en esta etapa: la selección ya está visible en la grilla.

La etiqueta técnica `Herramientas de edición` puede desaparecer. Si se conserva un contenedor semántico o `testTag`, no debe obligar a mostrar ese título al usuario.

### 5.2 Etapa B — elegir qué cargar

Después de `Terminar de elegir días`:

- bloquear la selección de la grilla sin ocultar las fechas elegidas;
- mostrar una pregunta breve equivalente a `¿Qué querés cargar?`;
- mostrar dos acciones grandes y apiladas, con altura mínima visual de 56 dp:
  - `Agregar guardia`, primaria;
  - `Agregar francos`, secundaria, sólo cuando las reglas actuales lo permitan;
- mostrar `Modificar días elegidos` como acción secundaria delimitada dentro del bloque de selección;
- `Modificar días elegidos` vuelve directamente a la Etapa A, reactiva la grilla y conserva exactamente todas las fechas elegidas;
- Atrás desde esta etapa tiene la misma conducta de volver a la selección conservada;
- no crear `ShiftDraft`, `DayOffDraft` ni escribir datos hasta que el usuario elija una operación.

### 5.3 Etapa C — formulario de Guardia

Al tocar `Agregar guardia`:

- ocultar por completo la tarjeta de herramientas, el contador anterior y los avisos `La selección se hace...` y `La selección queda visible y bloqueada...`;
- mantener la misma grilla arriba, las fechas seleccionadas y la grilla bloqueada;
- desplazar suavemente la atención hacia el inicio del formulario sin crear otra pantalla ni eliminar la grilla de la composición;
- mostrar una sola franja compacta y delimitada de contexto, por ejemplo `Agregar guardia · 4 días`, con una acción `Modificar días elegidos` dentro del mismo recuadro;
- `Modificar días elegidos` vuelve a la Etapa A conservando las fechas: lo hace directamente si el formulario está virgen y muestra la confirmación existente sólo cuando hay una combinación, puesto, advertencia u otro borrador real que perder;
- no volver a mostrar el mes ni el contador de fechas en bloques separados;
- antes de guardar, mostrar las fechas exactas en el resumen o confirmación final. Para muchas fechas puede usarse `N días de agosto · Ver fechas`, siempre que la revisión completa sea accesible.

La decisión principal es `Elegí objetivo y horario`.

#### Recientes y objetivos

- Si existen combinaciones recientes, mostrarlas primero como tarjetas seleccionables grandes, no como filas pequeñas dominadas por un `RadioButton`.
- Cada tarjeta debe comunicar abreviatura, objetivo, horario, franja de color y selección semántica.
- Mantener hasta cinco recientes según el contrato vigente; si ocultar parte reduce ruido, ofrecer una acción clara para ver las restantes.
- Cuando existen recientes, las carpetas completas permanecen plegadas detrás de `Elegir otro objetivo u horario` o texto equivalente.
- Si no existen recientes, no mostrar el encabezado ni el mensaje vacío `Todavía no hay horarios recientes`; mostrar directamente los objetivos disponibles.
- `Crear objetivo` queda como acción secundaria después de las opciones existentes.
- Si no existe ningún objetivo activo, mostrar un estado vacío simple con una única acción principal `Crear mi primer objetivo`; no mostrar recientes, explorador, puesto, vista previa ni otra acción de creación.
- Si existen objetivos activos pero no hay ningún horario activo/utilizable, no pedir que se cree el primer objetivo ni mostrar carpetas vacías. Mostrar `Agregá un horario`, una explicación breve y solamente los objetivos activos necesarios para elegir a cuál agregarlo; tocar un objetivo reutiliza `openSchedule(objective.id, null)`. Puede conservarse `Crear otro objetivo` como acción secundaria que no compita con esa decisión.
- Al volver de crear el horario se conservan el formulario y las fechas, el horario nuevo queda seleccionado mediante el `ManagementViewModel` vigente, el explorador se pliega y recién entonces aparecen resumen, puesto opcional, vista previa y revisión.
- Las carpetas por objetivo y `+ Agregar horario` conservan su función vigente.
- Crear o editar objetivo/horario puede abrir su superficie dueña y, al volver, debe conservar selección, borrador y etapa.

#### Después de elegir una combinación

- Contraer el explorador a un resumen compacto de la elección, con acción `Cambiar`.
- El resumen debe mostrar como mínimo abreviatura/objetivo, horario y color.
- Recién entonces revelar `+ Agregar puesto opcional`.
- El campo de puesto permanece oculto hasta pedirlo; si ya tiene contenido, debe mostrarse al recrear o volver.
- La vista previa no aparece incompleta. Se muestra sólo cuando existe una combinación elegida y no repite fecha/mes en varios bloques.
- Usar un resumen compacto, por ejemplo `QAX · 19:00–07:00 · 4 guardias`, con puesto si existe.
- La acción principal debe ser grande, concreta y con plural correcto: `Revisar y guardar`, `Revisar guardia` o `Revisar 4 guardias` según la decisión visual coherente.

La confirmación final se conserva en este incremento. Debe mostrar las fechas exactas, objetivo, horario, puesto opcional y cantidad con redacción natural, sin `guardia(s)`. No eliminarla ni convertir el guardado simple en acción inmediata sin una nueva aprobación de Joa.

### 5.4 Etapa D — formulario de Francos

Al tocar `Agregar francos`:

- ocultar por completo la tarjeta de herramientas y sus instrucciones;
- mantener la grilla, selección y bloqueo vigentes;
- mostrar un solo bloque compacto con:
  - título `Agregar francos`;
  - cantidad y fechas exactas una sola vez;
  - acción grande y dinámica `Confirmar franco` o `Confirmar N francos`;
  - acción secundaria delimitada `Modificar días elegidos`;
- `Modificar días elegidos` vuelve a la Etapa A con las fechas intactas; si no hay otro borrador, no muestra una falsa confirmación;
- no repetir mes, cantidad o fechas en otra vista previa;
- no mostrar información sobre conflictos inexistentes;
- conservar el guardado atómico y el mensaje de éxito actuales.

### 5.5 Jerarquía visual Vigilia

- Una sola acción luminosa por etapa.
- Acciones principales y opciones frecuentes con blanco táctil mínimo de 48 dp y objetivo visual recomendado de 56–64 dp.
- Reducir cajas anidadas, bordes y divisores; no usar `SectionCard` por ritual.
- Mantener contraste, forma y semántica de selección; no depender sólo del color.
- No agregar dependencia de iconos ni otra biblioteca.
- Conservar Claro, Oscuro y Seguir el sistema.
- Conservar zoom interno 100 %, 150 % y 200 % sin consultar ajustes visuales de Android.
- Mantener retrato y paisaje utilizables mediante desplazamiento.
- No activar ni declarar una auditoría específica de TalkBack.

## 6. Conductas que no pueden cambiar

- La grilla mensual principal continúa siendo el único selector de fechas.
- Consulta, `Editar día`, `Editar calendario`, selección y detalle conservan sus contratos.
- `Editar día` preselecciona una fecha sin escribir datos.
- `Editar calendario` comienza vacío.
- `Terminar de elegir días` confirma la selección sin crear borradores ni escribir datos.
- Guardia y Francos aparecen únicamente después de esa confirmación explícita.
- La grilla permanece durante Guardia y Francos y no aparece otro calendario.
- Mientras existe formulario, la grilla no modifica silenciosamente la selección.
- `Modificar días elegidos` conserva las fechas y vuelve a la selección editable; Atrás recorre primero la etapa anterior y la salida protege borradores.
- Cambio de mes con selección exige confirmación.
- Se preservan recientes, carpetas por objetivo, creación de objetivo/horario y regreso al formulario.
- Se preservan puesto, vista previa y confirmación.
- Se preservan fechas ocupadas, reemplazar, conservar libres, segunda guardia, descanso menor a doce horas y cancelación.
- Se preservan acciones individuales de guardias existentes.
- Se preserva la atomicidad de Guardia y Francos.
- Se preservan mensajes de éxito, recreación y entrada desde notificación.
- No ocultar advertencias críticas: deben aparecer únicamente cuando la condición real ocurra.

## 7. Alcance técnico permitido

Archivos principales esperados:

- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/calendar/**` únicamente para representar, guardar y restaurar la etapa de selección confirmada;
- `app/src/main/java/com/blackatsystems/miguardia/ui/management/ManagementScreens.kt`;
- componentes Vigilia existentes sólo si hace falta un componente visual reutilizable;
- `app/src/main/res/values/strings.xml` si se estructuran textos visibles;
- pruebas Compose/JVM directamente afectadas;
- `docs/PROMPT_MAESTRO_MAIN.md` con una nota breve de la decisión aprobada;
- `docs/prompts/CALENDARIO_SELECCION_DIRECTA.md` si requiere reflejar la jerarquía refinada;
- este documento.

No modificar ViewModels de Gestión, dominio o persistencia salvo que un defecto verificable impida cumplir el contrato. El estado de Calendario puede incorporar la etapa `seleccionando/selección confirmada` y conservarla mediante su `SavedStateHandle`, sin crear una fuente durable nueva. Cualquier otra ampliación vuelve a MAIN.

## 8. Fuera de alcance

No modificar:

- Room, entidades, DAO, repositorios o migraciones;
- DataStore o sus claves;
- manifiesto o permisos;
- Gradle, catálogo o dependencias;
- lógica de horas o cálculos monetarios;
- notificaciones, clima, persistencia o comportamiento interno de Fotos, perfil, vacaciones o feriados; la única excepción explícita posterior es que el acceso a Fotos se oculta en consulta y aparece en edición;
- panel lateral o navegación principal;
- datos históricos;
- producción instalada.

No comenzar:

- multiprofesión;
- eliminación de remuneraciones;
- onboarding o tutorial;
- widgets;
- informes;
- copias de seguridad;
- monetización.

No hacer refactors oportunistas.

## 9. Mapa de impacto obligatorio

Construir antes de editar:

Selección de calendario
→ resumen único
→ `Terminar de elegir días`
→ elección Guardia/Francos
→ `Modificar días elegidos` con fechas conservadas
→ apertura de Guardia/Francos

Formulario de Guardia
→ contexto compacto
→ recientes
→ objetivos plegados
→ creación de objetivo/horario
→ puesto opcional
→ vista previa
→ confirmación

Formulario de Francos
→ contexto único
→ confirmación
→ guardado atómico existente

Estado y navegación
→ grilla bloqueada
→ cambiar días
→ borrador
→ Atrás
→ recreación

Visual
→ Vigilia
→ claro/oscuro/sistema
→ zoom 100/150/200
→ retrato/paisaje

Sin impacto esperado:

- Room;
- dominio;
- DataStore;
- permisos;
- dependencias;
- red;
- privacidad;
- datos históricos.

## 10. Pruebas obligatorias

Revisar assertions reales antes de modificar pruebas. No actualizar expectativas sólo para hacerlas pasar.

### Selección y transición

- `Cargar datos` abre preparación sin fecha, `ShiftDraft` ni escritura;
- permite crear varios objetivos y horarios antes de continuar;
- `Continuar y elegir días` permanece deshabilitado sin una combinación activa y no autoavanza al crearla;
- al continuar, la selección está vacía y la grilla queda habilitada;
- Fotos, anterior, siguiente, Hoy y gesto mensual funcionan durante la preparación;
- sin selección existe una sola instrucción breve;
- con selección editable existe un solo contador y `Terminar de elegir días`, sin Guardia ni Francos;
- `Terminar de elegir días` conserva las fechas, bloquea la grilla y muestra la elección Guardia/Francos;
- antes de elegir una operación no existe `ShiftDraft` ni `DayOffDraft`;
- `Modificar días elegidos` desde la elección de operación reactiva la grilla y conserva exactamente el conjunto;
- al abrir Guardia desaparecen herramientas, instrucción y contador anteriores;
- al abrir Francos ocurre lo mismo;
- `month-grid` permanece y conserva selección semántica;
- la grilla queda bloqueada mientras hay formulario;
- `Modificar días elegidos` desde Guardia o Francos vuelve directamente a la selección y conserva fechas;
- si existe borrador, `Modificar días elegidos` y Atrás muestran confirmación;
- si el formulario está virgen, `Modificar días elegidos` y Atrás vuelven directamente sin una falsa advertencia de descarte;
- recreación conserva la etapa correcta y no repone instrucciones obsoletas.

### Guardia

- los recientes no muestran un estado vacío redundante;
- recientes son opciones grandes y seleccionables;
- con recientes, las carpetas completas comienzan plegadas;
- `Elegir otro objetivo u horario` muestra las carpetas;
- `Crear objetivo` continúa accesible;
- sin objetivos aparece sólo el estado vacío `Crear mi primer objetivo`;
- con objetivos activos pero sin horarios utilizables aparece sólo la decisión `Agregá un horario`, con elección exacta del objetivo dueño;
- elegir una combinación contrae el explorador a un resumen con `Cambiar`;
- el puesto comienza plegado y se restaura si contiene texto;
- la vista previa no aparece incompleta;
- resumen y confirmación contienen combinación, cantidad y fechas exactas;
- no existen textos duplicados de mes o selección;
- objetivo/horario nuevo vuelve sin perder selección ni borrador;
- políticas de ocupadas, segunda guardia y descanso no regresan.

### Francos

- existe un único bloque de confirmación;
- cantidad y fechas aparecen una sola vez;
- existe una única acción primaria de guardado;
- guardado exitoso conserva feedback;
- error conserva toda la atomicidad vigente.

### Visual y regresiones

- botones principales son utilizables y alcanzables;
- sólo hay una acción luminosa por etapa;
- 100 %, 150 % y 200 % conservan scroll y acceso a las acciones;
- retrato y paisaje no cortan botones ni confirmaciones;
- claro, oscuro y sistema conservan contraste;
- no reaparece una segunda grilla ni `Una fecha`/`Varias fechas`;
- detalle, menú lateral, Perfil, Fotos, Vacaciones, Feriados, Notificaciones y Clima no se degradan.

Actualizar como mínimo, según impacto real:

- `CalendarComposeTest.kt`;
- `ManagementComposeTest.kt`;
- `MiGuardiaAppTest.kt`;
- `VisualPolishComposeTest.kt`;
- pruebas de recreación o apariencia si el cambio las alcanza.

Preferir `testTag` y semántica estable para comprobar etapas y evitar assertions frágiles basadas en textos duplicados.

## 11. Validación local

Ejecutar con un solo worker:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease assembleQa assembleQaAndroidTest
```

Después:

- contar resultados JVM reales desde XML por módulo;
- contar métodos AndroidTest compilados;
- revisar XML de lint;
- confirmar APK Debug, Release, QA y QA AndroidTest;
- ejecutar `git diff --check`;
- revisar diff completo y archivos no rastreados;
- verificar ausencia de artefactos generados en Git;
- confirmar mediante diff que Room, DataStore, manifiesto, permisos, Gradle y dependencias permanecen intactos.

No repetir instrumentación física: no está autorizada en esta tarea. MAIN elegirá después las clases y el recorrido físico por impacto.

## 12. QA físico posterior — no autorizado inicialmente

Cuando MAIN reciba autorización explícita de Joa, deberá usar solamente `com.blackatsystems.miguardia.qa` y `.qa.test`, sin tocar producción, y comprobar:

1. selección vacía, simple y múltiple;
2. `Terminar de elegir días`, bloqueo de grilla y aparición posterior de Guardia/Francos;
3. `Modificar días elegidos` desde la elección y desde formularios, conservando fechas;
4. desaparición real de herramientas al abrir cada operación;
5. tamaño y jerarquía de botones;
6. recientes, otros objetivos y creación;
7. elección, cambio, puesto opcional y vista previa;
8. Guardia simple, múltiple, ocupada y segunda guardia;
9. Francos simple y múltiple;
10. borrador, Atrás, cambiar días y recreación;
11. 100 %, 150 % y 200 %;
12. Claro, Oscuro y Seguir el sistema;
13. retrato y paisaje.

Usar sólo datos ficticios, restaurar configuración y eliminar los paquetes QA autorizados al finalizar.

## 13. Seguridad y Git

- No usar datos laborales reales.
- No imprimir cronogramas, notas, rutas privadas o secretos.
- No agregar telemetría, red ni analítica.
- No incorporar APK, AAB, capturas o salidas de build.
- No tocar `com.blackatsystems.miguardia`.
- No hacer commit ni push.
- No hacer merge, rebase, reset ni limpieza de worktrees o ramas.
- No descartar cambios ajenos.

## 14. Entrega a MAIN

Devolver un handoff compacto con:

- ruta, rama, HEAD y base;
- estado Git inicial y final;
- problema UX resuelto;
- estados visuales finales;
- archivos modificados y no rastreados;
- decisiones de presentación;
- diferencias respecto de este contrato;
- mapa de impacto;
- pruebas ejecutadas y comando exacto;
- conteos JVM y AndroidTest;
- lint y ensamblados;
- QA físico declarado honestamente como pendiente;
- confirmación de Room, DataStore, permisos, dependencias, privacidad y producción intactos;
- `git diff --check`;
- riesgos o pendientes;
- confirmación de que no hubo commit ni push.

## 15. Done when

La entrega está lista para volver a MAIN cuando:

- la zona inferior muestra una sola decisión por etapa;
- `Terminar de elegir días` separa selección de operación y no se confunde con `Salir de edición`;
- Guardia y Francos aparecen únicamente después de confirmar la selección;
- `Modificar días elegidos` vuelve a la selección preservando todas las fechas;
- desaparecen herramientas e instrucciones al abrir Guardia o Francos;
- existe un único resumen de selección por etapa;
- las acciones principales son grandes y claras;
- recientes y objetivos usan divulgación progresiva;
- puesto y vista previa aparecen sólo cuando corresponden;
- Francos se confirma en un único bloque;
- la grilla única, reglas, borradores y confirmaciones permanecen intactos;
- temas, zoom y orientación siguen utilizables;
- pruebas y compilación pasan;
- no existen cambios fuera de alcance;
- todo queda sin commit y sin push.
