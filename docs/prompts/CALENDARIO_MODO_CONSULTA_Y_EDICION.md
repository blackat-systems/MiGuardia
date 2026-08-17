# MiGuardia — dependencia CALENDARIO: modo consulta y edición explícita

> Estado: preparada por MAIN para ejecución especializada
>
> Fecha: 2026-08-17
>
> Rama asignada: `codex/calendar-edit-mode`
>
> Base funcional obligatoria: `da4a91880f4d1897ee071207593d9ae9953e4089` (`docs: define initial experience sequence`)

## 0. Rol y autoridad

Sos la dependencia especializada **CALENDARIO — MODO CONSULTA Y EDICIÓN**. Tu misión es separar con claridad mirar de modificar sin duplicar el calendario, su proyección, su estado mensual ni sus fuentes de datos.

MAIN conserva producto, arquitectura e integración. No hagas commit, push, merge o rebase. Dejá la entrega sin confirmar y devolvé evidencia autocontenida.

Antes de editar:

1. verificá ruta, rama, HEAD, `git status --short` y `git worktree list`;
2. confirmá que la rama parte de la base indicada y del commit documental que contiene este prompt, informado por MAIN en la tarea inicial;
3. leé completos y en orden:
   - `AGENTS.md`;
   - `docs/PROMPT_MAESTRO_MAIN.md`;
   - `docs/prompts/COORDINACION_EXPERIENCIA_INICIAL_Y_PERFIL_MAIN.md`;
   - este prompt;
   - `docs/prompts/CALENDARIO_MENSUAL.md`;
   - `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
   - `docs/prompts/PULIDO_VISUAL_Y_UX.md`;
   - `docs/prompts/NOTIFICACIONES.md`;
   - `docs/prompts/CLIMA.md`;
   - ADR 0003, 0004, 0009, 0010 y 0011;
   - código y pruebas vigentes de Calendario, Gestión, navegación raíz y preferencias visuales;
4. inspeccioná todos los cambios locales y no descartes trabajo ajeno.

Jerarquía: instrucción actual de Joa, prompt maestro, `AGENTS.md`, este prompt, ADR/prompts históricos e implementación. Las reglas históricas que exponían mutaciones directas desde el modo normal quedan reemplazadas por este contrato.

## 1. Línea base que debe preservarse

La base ya incluye:

- Android/Kotlin/Compose Material 3 y navegación manual con Calendario, Resumen y Configuración;
- Room v5 con 13 entidades y migraciones 1→2→3→4→5;
- cuadrícula estable de 42 posiciones, semana desde lunes y mes conservado mediante `SavedStateHandle`;
- guardias con abreviatura histórica, horario completo, color ARGB, estados temporales y múltiples guardias por fecha;
- vacaciones, feriados, `F`, `?`, `CM`, fotos, resumen, próximo evento, notificaciones, clima y remuneración;
- formularios reales de guardia/franco, carga simple o múltiple, objetivos y horarios;
- reemplazo atómico de `F` o `?` por una guardia, segunda guardia, advertencia de descanso y eliminación confirmada;
- Vigilia clara/oscura/Sistema y zoom interno 100/150/200 %;
- build QA aislado.

No reimplementes ninguna de esas capacidades. Reutilizá estado, callbacks, ViewModels, repositorios y flujos existentes.

Hashes Room que deben permanecer idénticos:

- v1 `06557907F47669DF0E2F950C00FC7FC89EA45511386A9990803F01B86471AC1B`;
- v2 `8D835CDF9616924A704EF3FDF89CC2BF1268F4275F5E9A978C6F20A6D44D7453`;
- v3 `15299988DA323E9C0C434CC3087308D92605DA12A7AAEAD132E52B2AF7E162F2`;
- v4 `933572FA5CEC8A9B41BEA84B905BCB0A091CB7C8B69C425B4981F5668DB8FE22`;
- v5 `A73B70A1104970092D4155707F3C45429DA5546B5B020A5A6400AF7B33E0C9F9`.

## 2. Resultado funcional obligatorio

MiGuardia debe abrir el Calendario en **modo consulta**. El usuario puede mirar y navegar sin riesgo de cambiar datos. Para modificar debe tocar conscientemente **Editar calendario**.

Debe existir un único calendario y una única fuente de verdad. Consulta y edición son modos de interacción, no rutas, pantallas, repositorios ni proyecciones duplicadas.

## 3. Modo consulta

En consulta se permite:

- mes anterior/siguiente, gesto horizontal y Hoy;
- abrir Fotos del mes;
- abrir un día y leer fecha, estados y guardias;
- consultar próximo evento, clima y demás información no mutante ya disponible;
- cambiar de destino inferior y volver sin perder el mes visible.

En consulta se prohíbe:

- agregar guardia o franco;
- editar, reemplazar, borrar, limpiar o duplicar;
- agregar segunda guardia;
- informar novedad/notas, porque también persiste datos;
- abrir un formulario mutante mediante toque, pulsación larga, menú oculto, semántica o callback residual.

No alcanza con ocultar texto: los callbacks mutantes no deben estar conectados desde la superficie de consulta. Las pruebas deben usar fakes contadores o fallar si una interacción de consulta alcanza una mutación.

El detalle de una fecha en consulta conserva únicamente contenido informativo. Un resumen climático elegible puede abrir su detalle por UUID porque no modifica el calendario.

## 4. Entrada a edición y calendario vacío

En la zona inferior del contenido, por encima de la navegación y respetando insets, mostrar:

- `Editar calendario` cuando existe al menos una guardia histórica;
- `Cargar mi primera guardia` cuando no existe ninguna guardia en la base.

“Primera guardia” significa ausencia global de guardias, no sólo mes visible vacío. MAIN autoriza, si resulta necesario, una única ampliación reactiva y de solo lectura equivalente a:

```kotlin
fun ShiftRepository.observeHasAny(): Flow<Boolean>
```

con una consulta Room `EXISTS`/`COUNT` eficiente y prueba aislada. Esta autorización no permite tablas, columnas, índices, entidades, migraciones ni regeneración de esquemas. Si podés obtener la señal global correctamente mediante un contrato ya existente sin cargar todas las guardias, preferilo y documentalo.

`Cargar mi primera guardia`:

1. entra al mismo modo edición;
2. abre el formulario real de guardia;
3. preselecciona hoy cuando el mes visible es el actual, o una fecha válida del mes visible en otro caso;
4. permite crear objetivo y horario mediante los flujos reales;
5. no crea tutorial, formulario paralelo ni dato parcial.

## 5. Modo edición

Al entrar:

- conservar el `YearMonth`, la fecha seleccionada y el desplazamiento razonable de la pantalla;
- mantener exactamente el mismo calendario y sus datos reactivos;
- mostrar texto inequívoco `Editando calendario`; el estado no puede depender sólo de magenta, color o icono;
- mantener Vigilia y una sola acción primaria luminosa por superficie;
- cambiar la acción inferior a `Terminar`.

En edición, tocar una fecha permite usar sólo las mutaciones vigentes:

- `Agregar guardia`, con un día o varias fechas dentro del mismo mes;
- `Agregar francos`;
- en guardia existente: `Informar novedad / notas`, `Editar`, `Agregar una segunda guardia` cuando corresponde y `Eliminar`;
- eliminar siempre exige confirmación;
- editar conserva UUID e instantáneas según los contratos existentes;
- las reglas de reemplazo, segunda guardia, advertencia de descanso y transacciones siguen intactas.

No reintroducir:

- Vacaciones desde Calendario; se administran sólo en Configuración;
- duplicación desde detalle;
- limpieza general del mes o del día;
- menús de tres puntos redundantes;
- una segunda implementación de selección múltiple.

`Terminar` cierra el modo edición y vuelve a consulta sin cambiar mes ni scroll. Si existe un formulario real abierto, su propio flujo y protección de borrador tiene prioridad: no descartarlo silenciosamente para terminar.

## 6. Atrás, recreación y estado

Orden de Atrás:

1. cerrar diálogo o confirmación superior;
2. proteger/cerrar el formulario de Gestión mediante su contrato actual;
3. cerrar detalle de fecha;
4. salir del modo edición a consulta;
5. recién después aplicar la navegación normal de Android.

El modo es estado efímero de interfaz, nunca dato laboral. No guardarlo en Room o DataStore. Puede sobrevivir a una recreación de actividad mediante estado guardado, pero una apertura fría normal debe empezar en consulta.

Usá flujo unidireccional. La solución preferida es un tipo explícito equivalente a `CalendarInteractionMode.VIEW/EDIT` en estado/controlador de Calendario, con eventos claros. Evitá booleanos duplicados en `MiGuardiaApp`, `CalendarScreen` y detalles.

Conservá `SavedStateHandle` para mes. No alteres el reloj, la proyección mensual o las esperas temporales.

## 7. Diseño Vigilia y accesibilidad vigente

- usar los tokens y componentes compartidos de Vigilia; no agregar HEX dispersos;
- claro, oscuro y Seguir el sistema deben conservarse;
- `Editando calendario` debe ser textual y visualmente claro sin llenar la pantalla de magenta;
- una sola acción primaria por bloque;
- botones y contenido alcanzables al zoom interno 100/150/200 %;
- abreviatura y `HH:mm–HH:mm` completos, sin elipsis;
- retrato y paisaje sin contenido detrás de barras o teclado;
- semántica básica descriptiva y alternativas a gestos;
- no activar, desarrollar ni declarar una auditoría específica de TalkBack;
- no consultar ni modificar `font_scale`, densidad, `densityDpi`, zoom o tamaño de visualización de Android.

## 8. Contratos que no pueden cambiar

Preservar:

- 42 posiciones, lunes primero y guardia dibujada en fecha inicial;
- intervalos `[inicio, fin)` y `COMPLETED` derivado, nunca persistido;
- `PLANNED`, `ABSENT`, `CANCELLED`, vacaciones inclusivas y orden estable;
- `V`, `F`, `?`, `CM`, feriados y múltiples guardias;
- ARGB e instantáneas históricas exactas;
- guardia sobre `F`/`?` elimina el estado explícito atómicamente; `CM` no se borra;
- formularios, `SavedStateHandle`, advertencias y transacciones vigentes;
- notificaciones, cronómetro, clima, fotos, horas y remuneración;
- Room v5/13 entidades y esquemas inmutables;
- permisos, manifiesto, dependencias y build types.

No modificar reglas de dominio para implementar un modo de UI.

## 9. Archivos permitidos

Podés modificar:

- `app/src/main/java/.../ui/MiGuardiaApp.kt`;
- `app/src/main/java/.../ui/calendar/**`;
- `app/src/main/java/.../ui/management/**` sólo para conexión mínima y protección de flujo;
- componentes compartidos sólo si la abstracción es reutilizable y no cambia otras pantallas;
- recursos de texto indispensables;
- pruebas JVM/Compose/instrumentadas relacionadas;
- `core/domain/.../ShiftRepository.kt`, DAO/repositorio Room y tests únicamente para `observeHasAny`, si es necesario;
- un ADR nuevo del módulo si una decisión técnica lo justifica.

No tocar:

- modelos de dominio, cálculos de horas/remuneración o motor de próximo evento;
- entidades, migraciones, versión Room o esquemas;
- notificaciones, clima, fotos, vacaciones o excepciones salvo una llamada ya existente desde navegación;
- Gradle, catálogo, manifiesto, permisos, firma o build types;
- prompt maestro, este prompt o ADR históricos;
- datos reales, escalas salariales o archivos privados.

Si necesitás salir de estos límites, frená esa parte y elevá un cambio mínimo a MAIN.

## 10. Pruebas obligatorias

### JVM/estado

Cubrir:

1. inicia en consulta;
2. entrar/salir cambia sólo el modo;
3. cambio de mes funciona en ambos modos y se conserva;
4. recreación conserva el estado de interacción durante la sesión sin persistencia durable;
5. señal global de primera guardia es verdadera sólo con cero guardias;
6. cualquier contrato nuevo de repositorio es reactivo y no modifica datos.

### Compose

Con fakes y datos ficticios:

1. consulta muestra `Editar calendario` y no muestra mutaciones;
2. todos los gestos/taps de consulta dejan contadores de escritura en cero;
3. detalle consulta no ofrece novedad, editar, segunda guardia ni eliminar;
4. edición muestra `Editando calendario` y `Terminar`;
5. detalle edición ofrece exactamente las acciones aprobadas y en el orden vigente;
6. eliminar confirma;
7. fecha vacía en edición abre Guardia/Francos sin Vacaciones;
8. `Cargar mi primera guardia` entra y abre el formulario real;
9. guardias fuera del mes evitan un falso estado de primera carga;
10. Atrás respeta el orden y protege borradores;
11. entrar/salir conserva mes, fecha y scroll;
12. carga simple/múltiple, franco, reemplazo y segunda guardia no regresan;
13. clima y detalles informativos siguen accesibles en consulta;
14. Vigilia claro/oscuro, 100/150/200 % y paisaje son utilizables.

### Room, sólo si agregaste `observeHasAny`

- cero filas emite `false`;
- insertar emite `true`;
- borrar la última emite `false`;
- usa base UUID aislada;
- esquema y migraciones permanecen idénticos.

## 11. Batería y Samsung

Ejecutá serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Si tocaste consultas Room:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
```

Obtené conteos exactos desde XML. Ejecutá `git diff --check`.

En Samsung `SM-S938B`, API 36, usá exclusivamente `com.blackatsystems.miguardia.qa` y `.qa.test`. No borres, desinstales ni modifiques permisos/datos/alarmas de `com.blackatsystems.miguardia`.

Recorrido físico mínimo con datos ficticios:

- apertura segura en consulta;
- navegación de mes, Hoy, fotos y detalle sin mutaciones;
- entrada a edición, indicador y Terminar;
- primera guardia en QA vacío;
- carga simple, varias fechas y franco;
- guardia ocupada: Editar, segunda guardia y eliminar confirmado;
- Atrás con y sin borrador;
- clima informativo;
- claro/oscuro/Sistema;
- zoom interno 100/150/200 %;
- retrato y paisaje.

No cambies ajustes visuales del sistema. Al terminar, restaurá tema, zoom interno y orientación, retirale sólo QA/QA test y confirmá producción instalada.

## 12. Seguridad y cierre

Antes de devolver:

- revisar diff completo y archivos nuevos;
- confirmar cero cambios fuera de alcance;
- Room v5, 13 entidades, migraciones y hashes intactos;
- sin dependencias, permisos, secretos, datos reales, logs privados, APK/AAB o bases;
- consulta demostrada sin escrituras;
- `git diff --check` limpio;
- sin commit, push, merge o rebase.

## 13. Entrega a MAIN

Entregá un informe copiable con ruta, rama, base y HEAD; estado inicial/final; archivos; decisiones; diff; contratos; pruebas y conteos; recorrido físico real; Room/hashes; permisos/dependencias; privacidad; defectos o pendientes; y confirmación de que todo permanece sin commit.

No declares terminado lo que no ejecutaste. MAIN auditará e integrará antes de crear Perfil laboral.
