# Prompt maestro de dependencia — OBJETIVOS Y GUARDIAS, incremento 3

> Estado: implementado por la dependencia; integrado y auditado por MAIN
>
> Proyecto: MiGuardia
>
> Dependencia: OBJETIVOS Y GUARDIAS
>
> Fecha: 2026-08-13
>
> Línea base utilizada por la dependencia: commit `bf88bb6` (`docs: define objectives and shifts module`)

## 0. Rol y autoridad

Sos la dependencia especializada **OBJETIVOS Y GUARDIAS** de MiGuardia. Tu misión es habilitar la administración de objetivos y horarios y convertir la acción Agregar del calendario en una carga real, simple y múltiple de guardias.

Antes de planificar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
4. `docs/adr/0002-persistencia-local-v1.md`;
5. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
6. `docs/audits/2026-08-13-auditoria-integral.md`;
7. `docs/prompts/DATA_LOCAL.md`;
8. `docs/prompts/CALENDARIO_MENSUAL.md`;
9. este prompt;
10. el código y las pruebas relacionados de `app`, `core/domain` y `core/database`.

Jerarquía: una instrucción explícita actual de Joaquin, luego `docs/PROMPT_MAESTRO_MAIN.md`, luego `AGENTS.md`, después los ADR y este documento, y finalmente la implementación existente.

No redefinas el producto. Si encontrás una contradicción o falta una decisión funcional que cambie lo que verá Joaquin, detené únicamente esa parte y devolvela a MAIN con una recomendación clara. No inventes reglas silenciosamente ni modifiques contratos fuera de la autorización expresa de este prompt.

## 1. Punto de partida confirmado por MAIN

La línea base publicada contiene:

- Android en Kotlin, Jetpack Compose y Material 3;
- módulos `:app`, `:core:domain` y `:core:database`;
- Room 2.8.4, esquema versión 1 y cinco tablas;
- modelos `Objective`, `ScheduleCombination` y `Shift`;
- contratos `ObjectiveRepository`, `ScheduleCombinationRepository` y `ShiftRepository`;
- `LocalDataStore` como acceso público a los repositorios;
- calendario mensual reactivo conectado a Room;
- proyección temporal pura: una guardia `PLANNED` pasada se muestra `COMPLETED`, mientras `CANCELLED` y `ABSENT` prevalecen;
- composición manual mediante `MiGuardiaApplication`;
- acción Agregar visible pero deshabilitada;
- Configuración y Resumen todavía como superficies vacías.

No asumas que existen navegación formal, Hilt, DataStore en runtime, formularios, motor de horas, novedades, feriados o notificaciones. Inspeccioná el repositorio real antes de actuar.

## 2. Objetivo del incremento

Entregar una experiencia funcional que permita:

- crear, editar, ocultar y eliminar objetivos;
- crear, editar, ocultar y eliminar combinaciones objetivo+horario;
- seleccionar un color propio para cada combinación;
- abrir la administración desde Configuración;
- habilitar Agregar en Calendario;
- cargar una guardia en un día o en varias fechas del mismo mes;
- elegir entre hasta cinco combinaciones utilizadas recientemente o explorar todos los objetivos activos;
- crear un objetivo o una combinación desde el flujo de carga cuando todavía no exista;
- agregar puesto opcional en cada guardia;
- advertir por otra guardia en la misma fecha o por descanso menor a 12 horas y permitir continuar tras confirmación;
- resolver fechas con guardias existentes mediante reemplazar, conservar ocupadas o cancelar;
- editar y eliminar una guardia desde su detalle;
- duplicar una guardia a otras fechas del mismo mes;
- reflejar inmediatamente cada cambio en el calendario observado;
- mantener instantáneas históricas independientes de las plantillas;
- cargar guardias de fechas pasadas y verlas automáticamente completadas sin confirmación diaria.

Este incremento se concentra en objetivos, combinaciones y guardias. No implementa todavía la creación o edición de franco, día sin definir o carpeta médica desde la interfaz.

## 3. Alcance funcional congelado

### 3.1 Objetivos

Cada objetivo representa exactamente los campos existentes:

- nombre completo obligatorio;
- abreviatura obligatoria de 2 a 5 caracteres;
- dirección manual opcional;
- nota general opcional;
- activo u oculto;
- UUID e instantes técnicos.

Reglas:

- recortá espacios externos;
- mostrá y guardá la abreviatura en mayúsculas;
- la abreviatura es única sin distinguir mayúsculas/minúsculas;
- mostrá los errores controlados del repositorio en español junto al campo correspondiente;
- no pidas DNI, correo, teléfono ni domicilio personal;
- la dirección pertenece al objetivo laboral y no se usa para clima ni ubicación automática;
- la nota general del objetivo es privada y no aparece en la cuadrícula, logs o mensajes del sistema.

La administración debe mostrar activos y permitir consultar ocultos. Ocultar quita el objetivo de nuevas cargas, pero no altera guardias existentes. Eliminar requiere confirmación e informa que también elimina sus combinaciones, pero nunca las guardias históricas.

### 3.2 Combinaciones objetivo+horario

Cada combinación usa los campos existentes:

- objetivo propietario;
- hora local exacta de inicio;
- hora local exacta de fin;
- color ARGB propio;
- activo u oculto;
- UUID e instantes técnicos.

Reglas temporales:

- si `endTime > startTime`, la guardia termina en la misma fecha civil;
- si `endTime <= startTime`, termina al día siguiente;
- inicio y fin iguales representan una guardia de 24 horas, no duración cero;
- mostrá claramente cuando el horario termina “al día siguiente”;
- una pareja exacta inicio+fin no puede repetirse dentro del mismo objetivo;
- dos horarios diferentes del mismo objetivo pueden y deben conservar colores independientes.

Proporcioná un selector visual completo, sin biblioteca externa: campo bidimensional de saturación y luminosidad, barra arcoíris de tono, vista previa y lectura precisa RGB/HEX. No lo sustituyas por tres controles separados ni limites la gama a colores predeterminados. Si el color queda demasiado parecido al de otra combinación visible, advertí y permití continuar. El texto nunca puede depender de ese color para ser entendible y debe conservar contraste adecuado.

Ocultar una combinación la excluye de nuevas cargas y de recientes, pero no modifica guardias históricas. Eliminar requiere confirmación y tampoco modifica guardias existentes.

### 3.3 Creación de guardias

Al abrir Agregar desde Calendario:

1. ofrecé “Un solo día” o “Varios días”;
2. preseleccioná la fecha tocada cuando el flujo nazca desde una celda;
3. para varios días, limitá la selección a un único `YearMonth` por operación;
4. mostrale primero hasta cinco combinaciones activas utilizadas recientemente;
5. ofrecé Explorar objetivos mediante una carpeta desplegable por objetivo; dentro de cada carpeta mostrá sus horarios activos y, debajo, `+ Agregar horario`;
6. ofrecé Crear objetivo como acción general; no mezcles todos los horarios en una lista plana ni repitas botones “Crear horario para...” fuera de las carpetas;
7. solicitá puesto opcional para esta carga;
8. mostrale una vista previa con fechas, objetivo, horario, cruce de medianoche y cantidad de guardias;
9. validá conflictos y advertencias;
10. guardá solo tras una confirmación final.

Cada guardia nueva debe construirse con:

- UUID recibido desde una fábrica inyectable;
- `startAt` y `endAt` reales en `America/Argentina/Cordoba`;
- `localStartDate` igual a la fecha seleccionada;
- instantáneas del nombre, abreviatura, dirección, inicio, fin y color vigentes;
- puesto recortado u opcional nulo;
- `status = PLANNED` siempre;
- identificadores informativos del objetivo y la combinación de origen;
- `createdAt` y `updatedAt` iguales al instante inyectado de creación.

No agregues un estado persistido `COMPLETED`. Si el fin ya pasó, el calendario existente la proyectará completada automáticamente. Una guardia nocturna se dibuja solo en su fecha inicial.

### 3.4 Recientes

“Reciente” significa una combinación activa usada para crear una guardia, ordenada por el `createdAt` más nuevo entre sus guardias de origen. Mostrá como máximo cinco combinaciones distintas.

- no incluyas objetivos o combinaciones ocultos/eliminados;
- no uses la fecha laboral de la guardia como señal de uso: una guardia retrocargada hoy cuenta como uso de hoy;
- una combinación jamás utilizada no aparece en recientes, pero sí al explorar;
- no agregues una tabla ni preferencia para recientes;
- resolvelo con una consulta observable sobre las tablas existentes y datos de origen.

### 3.5 Fechas ocupadas y segunda guardia

En este incremento, “ocupada” para una operación de guardias significa que la fecha ya contiene al menos una guardia.

Para selección múltiple con fechas ocupadas, enumerá cuáles son y ofrecé exactamente:

1. **Reemplazar**: eliminar todas las guardias existentes de cada fecha ocupada seleccionada e insertar la nueva guardia;
2. **Conservar ocupadas**: insertar solo en fechas seleccionadas sin guardias;
3. **Cancelar**: no cambiar nada.

El reemplazo afecta únicamente guardias de las fechas seleccionadas. No borra otros días ni otros meses.

Para agregar deliberadamente otra guardia en una fecha ya ocupada, ofrecé una acción separada “Agregar segunda guardia”. Mostrá las guardias existentes y pedí confirmación. No la bloquees definitivamente.

Si una fecha contiene `F` o `?` explícito, guardar una guardia en esa fecha lo reemplaza de forma atómica: la guardia define el día y el estado explícito se elimina. Si contiene `CM`, no lo borres ni lo modifiques; mostrale al usuario una advertencia clara. Nunca ocultes datos ni simules un reemplazo que no se haya persistido.

### 3.6 Descanso menor a 12 horas

Antes de confirmar, compará cada guardia nueva o editada con las guardias inmediatamente anteriores y posteriores que no serán reemplazadas, y también las nuevas guardias entre sí.

- descanso normal mínimo: 12 horas completas;
- superposición o separación menor a 12 horas genera advertencia;
- la advertencia muestra fechas, horarios y descanso real;
- permití continuar tras confirmación explícita;
- no impongas restricción SQL ni descartes silenciosamente la carga;
- una segunda guardia en el mismo día también requiere confirmación aunque el descanso resultara suficiente;
- canceladas no cuentan como trabajo para esta advertencia; ausencias declaradas tampoco;
- no modifiques el cálculo por el mero estado temporal próxima/en curso/completada.

Implementá esta evaluación como lógica pura, independiente de Compose y Android, con intervalos `Instant`.

### 3.7 Edición, eliminación y duplicado

Desde el detalle existente de una guardia, habilitá:

- editar;
- eliminar;
- duplicar en otras fechas del mismo mes.

Editar permite cambiar fecha, combinación y puesto. Al elegir una combinación, actualiza las instantáneas de esa guardia como corrección consciente, pero conserva:

- el mismo UUID;
- `createdAt` original;
- estado persistido existente, salvo que una futura función explícita permita cambiarlo;
- `updatedAt` con el reloj inyectado.

La edición vuelve a validar segunda guardia y descanso. Eliminar pide confirmación y afecta únicamente la guardia elegida. Duplicar crea UUID y timestamps nuevos, conserva la configuración elegida y usa las mismas reglas de selección múltiple y conflictos.

No implementes todavía modificación masiva de guardias existentes. El requisito de editar masivamente solo familias exactas se abordará cuando exista selección múltiple general; no simules esa capacidad.

## 4. Navegación y experiencia de usuario

Podés organizar navegación con estado explícito y rutas simples dentro de Compose. No actives Navigation 3 si el flujo puede mantenerse claro con la base actual. Si la navegación existente deja de ser mantenible, devolvé evidencia a MAIN antes de activar una dependencia del catálogo.

Superficies mínimas:

- Calendario con Agregar habilitado;
- selector de modo simple/múltiple;
- selector de fechas de un solo mes;
- selector de combinación con recientes y exploración;
- formularios de objetivo y horario;
- vista previa y resolución de advertencias;
- detalle de guardia con acciones;
- Configuración > Objetivos y horarios;
- listas de activos y ocultos;
- confirmaciones de ocultar/eliminar.

Requisitos de formulario:

- conservar datos escritos ante recreación y errores recuperables;
- no cerrar ni declarar éxito hasta que la escritura termine;
- deshabilitar confirmación mientras se guarda para evitar doble toque;
- errores comprensibles y acción concreta;
- advertir antes de descartar una edición no guardada;
- volver al calendario y mostrar el cambio observado sin recargar manualmente;
- no usar diálogos tan altos que se vuelvan inaccesibles con fuente al 200 %;
- selectores de hora compatibles con formato de 24 horas.

La pantalla Resumen puede seguir como estado vacío honesto.

## 5. Arquitectura y lógica autorizada

### 5.1 Casos de uso puros

Agregá en `core/domain` lógica reutilizable para:

- construir instantes reales desde fecha, horas y zona;
- crear una `Shift` con instantáneas desde `Objective` y `ScheduleCombination`;
- evaluar conflictos de mismo día, superposición y descanso menor a 12 horas;
- preparar un plan de carga simple/múltiple;
- distinguir fechas vacías, reemplazadas y omitidas;
- validar que una selección múltiple pertenezca a un solo mes;
- comparar colores para producir una advertencia no bloqueante.

Toda dependencia temporal o aleatoria debe recibirse explícitamente: `Clock`, instante o una interfaz mínima de fábrica de UUID. No llames ocultamente a `Instant.now()`, `LocalDate.now()` o `UUID.randomUUID()` dentro de lógica que deba probarse.

### 5.2 Contratos autorizados por MAIN

MAIN autoriza exactamente estas ampliaciones conceptuales; elegí nombres Kotlin equivalentes y documentalos en la entrega:

1. Un modelo de dominio para una combinación utilizada recientemente, que contenga:
   - `Objective` activo;
   - `ScheduleCombination` activa;
   - `lastUsedAt: Instant`.
2. Una función observable en `ScheduleCombinationRepository` equivalente a:

```kotlin
fun observeRecentlyUsed(limit: Int = 5): Flow<List<RecentScheduleCombination>>
```

   Debe validar un límite entre 1 y 5 y consultar Room mediante unión/agrupación sobre las tablas existentes, ordenando por el máximo `shifts.createdAtEpochMillis` de cada combinación.
3. Un modelo de mutación atómica de guardias equivalente a:

```kotlin
data class ShiftBatchMutation(
    val shiftIdsToDelete: Set<UUID>,
    val shiftsToInsert: List<Shift>,
)
```

4. Una función en `ShiftRepository` equivalente a:

```kotlin
suspend fun applyBatch(mutation: ShiftBatchMutation)
```

   La implementación Room debe validar todo primero y ejecutar eliminaciones e inserciones dentro de una única transacción. Un error revierte la operación completa. UUID duplicados entre inserciones o presentes simultáneamente en borrar/insertar deben rechazarse antes de abrir la transacción.

Podés adaptar constructores internos para que `RoomShiftRepository` tenga acceso a `MiGuardiaDatabase`. No agregues campos, tablas, índices ni migraciones sin demostrar primero que son imprescindibles y recibir autorización posterior de MAIN.

No cambies los campos de `Objective`, `ScheduleCombination` o `Shift`, ni los valores de `ShiftStatus`. Conservá las operaciones existentes y su compatibilidad.

### 5.3 Estado de interfaz

Usá flujo unidireccional:

- estado inmutable hacia Compose;
- eventos del usuario hacia ViewModel/controlador;
- repositorios y casos de uso fuera de composables;
- `collectAsStateWithLifecycle` o equivalente estable;
- composición manual a partir de `MiGuardiaApplication`;
- tareas cancelables al cerrar flujo o cambiar de selección.

No incorpores Hilt, Service Locator mutable, singletons globales de edición ni estado de formulario dentro de Room.

## 6. Integridad, historial y transacciones

- Crear o editar una plantilla solo afecta cargas futuras.
- Editar una guardia es una corrección consciente de esa guardia y actualiza sus instantáneas.
- Ocultar o eliminar plantillas no altera guardias existentes.
- Carga múltiple, reemplazo y duplicado múltiple son atómicos.
- Una operación cancelada no escribe nada.
- “Conservar ocupadas” no elimina ni actualiza sus guardias.
- Sólo borrá `ExplicitDayStatus` para las fechas donde una guardia se guardó realmente y hacelo dentro de la misma transacción. No modifiques `MedicalLeave`.
- No uses migración destructiva ni base en memoria en producción.
- No accedas a Room desde el hilo principal.
- No registres nombres, direcciones, puestos, horarios o notas en logs.

## 7. Dependencias y esquema

No agregues dependencias de producción. La base ya contiene Compose, Material 3, Lifecycle, ViewModel, coroutines, Room y `java.time`.

Podés usar las dependencias de prueba ya aprobadas en el catálogo. No actualices versiones ni actives Navigation 3, DataStore, Hilt u otra biblioteca sin autorización posterior de MAIN.

El objetivo es conservar el esquema Room en versión 1. Las consultas de recientes y la transacción de lote deben implementarse sobre las tablas existentes. Si descubrís una necesidad real de migración, detené esa parte y presentá a MAIN:

- cambio exacto;
- necesidad funcional;
- migración propuesta;
- prueba de migración;
- alternativa sin modificar esquema.

## 8. Archivos permitidos

Podés crear o modificar únicamente:

- `app/src/main/**` para navegación, ViewModel, formularios, recursos y composición;
- `app/src/test/**` y `app/src/androidTest/**` para pruebas;
- `app/build.gradle.kts` solo si hace falta activar una dependencia de prueba ya presente;
- `core/domain/src/main/**` para casos de uso, modelos auxiliares y ampliaciones autorizadas;
- `core/domain/src/test/**` para lógica pura;
- `core/database/src/main/**` para consultas de recientes, transacción de lote, mapeadores y adaptaciones internas autorizadas;
- `core/database/src/test/**` y `core/database/src/androidTest/**` para pruebas;
- `core/database/schemas/**` únicamente para comprobar que la versión 1 permanece idéntica, no para regenerarla con cambios;
- documentación técnica nueva del módulo si una decisión arquitectónica reversible lo exige.

No toques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md`;
- prompts o ADR existentes;
- `gradle/libs.versions.toml`;
- escalas salariales o cronogramas reales;
- firma, configuración de Git o secretos;
- módulos de horas, fotos, feriados, notificaciones, clima, widget, informes o remuneración.

Si necesitás salir de estos límites, pedí autorización a MAIN antes de editar.

## 9. Fuera de alcance

No implementes en este encargo:

- creación/edición de franco, día sin definir o carpeta médica desde UI;
- eliminación transversal de `F`, `?` o `CM` al reemplazar guardias;
- novedades, notas por guardia, ausencia o cancelación desde UI;
- cambios formales planificado/real;
- modificación masiva general de guardias existentes;
- fotos del cronograma;
- feriados;
- cálculo de horas, nocturnidad, extras o remuneración;
- motor definitivo de próximo evento;
- notificaciones, clima o widgets;
- informes, copias de seguridad o bloqueo;
- mapas embebidos o ubicación automática;
- datos de demostración persistidos en producción.

No adelantes estos módulos con implementaciones parciales.

## 10. Pruebas obligatorias

### 10.1 JVM: lógica pura

Con reloj, zona y UUID deterministas, probá como mínimo:

1. horario diurno termina el mismo día;
2. horario nocturno termina al siguiente y se dibuja en la fecha inicial;
3. horas iguales producen exactamente 24 horas;
4. una guardia pasada se crea `PLANNED` y se proyecta `COMPLETED`;
5. instantáneas copian nombre, abreviatura, dirección, horario y color;
6. puesto vacío se normaliza a nulo;
7. selección múltiple rechaza fechas de meses diferentes;
8. plan Reemplazar identifica solo guardias de fechas seleccionadas;
9. plan Conservar ocupadas inserta solo fechas sin guardias;
10. Cancelar produce cero mutaciones;
11. dos guardias el mismo día generan advertencia no bloqueante;
12. solapamiento genera advertencia concreta;
13. descanso de 11 h 59 min advierte y 12 h exactas no;
14. guardias canceladas y ausentes no provocan advertencia de descanso;
15. conflictos se evalúan también entre nuevas guardias del mismo lote;
16. editar conserva UUID, `createdAt` y estado persistido, y cambia `updatedAt`;
17. comparación de colores advierte similitud pero no bloquea.

### 10.2 Room e integración

Probá en Android como mínimo:

1. objetivo normaliza abreviatura y rechaza duplicados con error distinguible;
2. combinación exacta duplicada se rechaza;
3. dos combinaciones del mismo objetivo conservan colores propios;
4. recientes devuelve como máximo cinco combinaciones distintas;
5. recientes ordena por creación de la guardia, no por fecha laboral;
6. recientes excluye objetivos y combinaciones ocultos;
7. lote inserta varias guardias atómicamente;
8. reemplazo elimina solo guardias de fechas seleccionadas;
9. un error en una inserción revierte eliminaciones e inserciones completas;
10. ocultar/eliminar plantilla preserva instantáneas históricas;
11. reiniciar/reabrir conserva objetivos, horarios y guardias;
12. el esquema exportado continúa byte por byte en versión 1;
13. ninguna operación usa el hilo principal.

### 10.3 Interfaz Compose

Con fakes y datos ficticios, demostrá:

1. Agregar queda habilitado y abre el selector simple/múltiple;
2. la fecha tocada se preselecciona;
3. recientes y exploración muestran solo opciones activas;
4. formularios validan nombre, abreviatura, horas y duplicados;
5. cruce de medianoche se explica correctamente;
6. selección múltiple no permite cruzar de mes;
7. vista previa enumera fechas y configuración;
8. fechas ocupadas ofrecen Reemplazar, Conservar ocupadas y Cancelar;
9. segunda guardia y descanso corto requieren confirmación;
10. doble toque no duplica una escritura;
11. error recuperable conserva datos escritos;
12. volver con cambios sin guardar pide confirmación;
13. guardia creada aparece en calendario mediante el flujo observado;
14. detalle permite editar, eliminar y duplicar;
15. eliminar requiere confirmación;
16. Configuración permite administrar activos y ocultos;
17. contenido y acciones tienen semántica accesible completa.

### 10.4 Dispositivo físico

Ejecutá todas las pruebas relevantes en el Samsung Galaxy S25 Ultra conectado y desbloqueado. Verificá manualmente un recorrido con datos ficticios:

1. crear objetivo y horario;
2. cargar guardia futura;
3. cargar guardia pasada y verla completada;
4. cargar guardia nocturna;
5. cargar varias fechas;
6. provocar y confirmar advertencia de descanso;
7. editar, duplicar y eliminar;
8. cerrar/reabrir y confirmar persistencia.

Revisá la identidad oscura, fuente habitual y 200 %, retrato y paisaje, semántica, teclado y selectores de hora. Restaurá cualquier configuración del teléfono modificada. No uses datos reales de Joaquin.

## 11. Criterios de aceptación

El incremento está terminado solo si:

- compila con el wrapper del repositorio;
- pasan todas las pruebas JVM;
- pasan lint y las pruebas instrumentadas relevantes;
- Agregar crea guardias reales y el calendario reacciona;
- la carga múltiple y reemplazo son atómicos;
- las advertencias nunca bloquean definitivamente una excepción confirmada;
- una retrocarga pasada aparece completada sin persistir `COMPLETED`;
- plantillas editadas/eliminadas no alteran historia;
- estados `F`, `?` y `CM` preexistentes no se borran;
- el esquema Room continúa en versión 1;
- no se agregaron dependencias de producción ni permisos;
- tema, fuente grande, orientación y accesibilidad fueron verificados;
- `git diff --check` está limpio;
- no hay datos reales, secretos, logs sensibles ni artefactos generados.

Ejecutá como mínimo:

```powershell
.\gradlew.bat --no-daemon --stacktrace clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

No confundas compilación con pruebas ejecutadas. No hagas commit, push, merge ni abras otra tarea salvo instrucción explícita de Joaquin o MAIN.

## 12. Entrega a MAIN

Al finalizar, entregá en español claro:

- qué quedó funcionando para Joaquin;
- archivos creados o modificados;
- contratos ampliados y sus firmas reales;
- decisiones técnicas reversibles;
- comandos ejecutados y resultado exacto, incluyendo cantidades de pruebas;
- recorrido realizado en el S25 Ultra;
- confirmación de esquema Room versión 1;
- confirmación de que no agregaste dependencias de producción, permisos ni datos reales;
- limitaciones o decisiones que MAIN deba resolver;
- `git status` y resumen del diff, sin commit ni push.

MAIN revisará el diff, repetirá la batería integral y decidirá la integración. OBJETIVOS Y GUARDIAS no sustituye a MAIN ni puede redefinir el producto.

## 13. Registro de integración por MAIN

MAIN integró y auditó la entrega el 2026-08-13. Durante la revisión se corrigieron:

- el retorno de formularios anidados de objetivo y horario hacia la carga de guardia, conservando el borrador y seleccionando el horario recién creado;
- la salida de las advertencias para que “Volver y corregir” sea una acción real;
- la política de edición sobre una fecha ocupada, limitada a conservar la otra guardia o cancelar;
- el ingreso horario mediante selector Material 3 de 24 horas;
- el respeto de las barras del sistema en las superficies de gestión;
- pruebas de regresión de recreación de actividad, atomicidad preventiva, selector horario, advertencias e interfaz ocupada.

La decisión arquitectónica resultante quedó registrada en `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`. Room continuó en versión 1 y su esquema permaneció idéntico.
# Enmienda posterior de MAIN (2026-08-16)

La decisión explícita posterior de Joa simplifica el detalle: sus acciones principales son únicamente `Informar novedad / notas`, `Editar` y `Eliminar`. La duplicación deja de exponerse allí. Para una única fecha ocupada, el orden visible es `Reemplazar`, `Agregar segunda guardia`, `Cancelar`; en lotes, la política interna `KEEP_OCCUPIED` se muestra como `Agregar sólo en días libres` y conserva exactamente su semántica atómica.
