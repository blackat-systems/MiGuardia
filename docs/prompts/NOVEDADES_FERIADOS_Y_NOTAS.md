# Prompt especializado: NOVEDADES, FERIADOS Y NOTAS

> Estado: contrato histórico; módulo implementado, integrado y verificado por MAIN
> Fecha: 2026-08-13
> Autoridad: MAIN
> Padre funcional verificado: `a4d9dba` — `feat: add monthly hours summary`
> Commit base del worktree: el HEAD documental de MAIN que contiene este prompt y que MAIN entrega junto con la tarea. No crear el worktree directamente desde `a4d9dba`, porque ese padre todavía no contiene este archivo.

## 1. Función de esta tarea

Sos la dependencia especializada **NOVEDADES, FERIADOS Y NOTAS** de MAIN para MiGuardia.

Implementá, depurá y verificá completamente este incremento en un worktree creado exactamente desde el commit base entregado por MAIN. No sos MAIN: no podés redefinir el producto, ampliar el alcance por conveniencia ni hacer commit, push o merge.

Al terminar, entregá a MAIN el trabajo sin confirmar, junto con un informe verificable. MAIN revisará el diff completo, corregirá la integración, repetirá la batería global y decidirá el commit.

## 2. Lectura obligatoria antes de modificar

Leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. este documento;
4. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
5. `docs/adr/0002-persistencia-local-v1.md`;
6. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
7. `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`;
8. `docs/adr/0005-motor-basico-de-horas.md`;
9. `docs/prompts/DATA_LOCAL.md`;
10. `docs/prompts/CALENDARIO_MENSUAL.md`;
11. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
12. `docs/prompts/MOTOR_BASICO_DE_HORAS.md`;
13. las auditorías existentes en `docs/audits/`;
14. el código y las pruebas de `core/domain`, `core/database` y las superficies de calendario, gestión y resumen.

Antes de editar verificá y registrá:

- ruta real del worktree;
- `git status`;
- rama o detached HEAD;
- `git rev-parse HEAD`;
- que HEAD coincida exactamente con el commit base entregado por MAIN;
- que ese HEAD contenga este prompt y tenga a `a4d9dba` en su historia;
- dispositivo físico conectado mediante ADB;
- esquema Room v1 existente y su hash.

Si HEAD no coincide o ya existen cambios ajenos, frená y explicáselo a MAIN. No descartes cambios, no uses `git reset --hard` y no adaptes silenciosamente el trabajo a otra base.

## 3. Objetivo verificable

Al terminar, Joaquin debe poder:

- crear, editar y eliminar feriados manuales individuales o múltiples;
- ver un indicador discreto de feriado en el calendario sin reemplazar Guardia, `F`, `?` o `CM`;
- obtener horas feriadas reales en Resumen, incluidas guardias nocturnas que cruzan de fecha, mes o año;
- abrir el detalle de una guardia y agregar, editar o eliminar notas privadas;
- usar **Informar novedad** para registrar tiempo adicional, salida anticipada, ausencia, cancelación, cambio formal de horario, cambio formal de objetivo, segunda guardia u otra novedad;
- corregir posteriormente una novedad;
- conservar visible el plan original y el resultado final cuando cambien formalmente objetivo u horario;
- comprobar que una nota, tiempo adicional o salida anticipada no alteran horas automáticamente;
- comprobar que ausencia y cancelación siguen siendo distintas y suman cero horas trabajadas;
- comprobar que una segunda guardia es otra `Shift` real y se computa normalmente.

Todo debe funcionar localmente y sin internet, sin cuentas, nube, telemetría ni permisos nuevos.

## 4. Decisiones cerradas de producto

### 4.1 Notas

- En este incremento, una nota pertenece exclusivamente a una guardia existente.
- Es texto libre y privado por defecto.
- No tiene efecto automático en horas, estado, objetivo ni horario.
- No aparece en calendario, notificaciones o widgets.
- Los informes futuros la excluirán salvo elección explícita del usuario.
- No registrar contenido de notas en logs, errores, nombres de prueba o capturas entregadas.
- Permitir varias notas por guardia, ordenadas por creación y con edición/eliminación individual.
- Texto vacío o compuesto solo por espacios es inválido.
- No implementar notas generales por día, objetivo o mes en este módulo.
- La nota privada opcional ya existente en `MedicalLeave` no cambia y nunca se mezcla con notas de guardia.

### 4.2 Feriados manuales

- Un feriado es una fecha civil local completa, desde 00:00 inclusive hasta 00:00 exclusiva del día siguiente.
- Cada fecha puede tener como máximo un feriado.
- El nombre es opcional; un nombre vacío se normaliza a `null`.
- No descargar calendarios, no usar internet y no generar feriados automáticamente.
- Permitir alta individual y selección múltiple manual.
- En carga múltiple, los feriados ya existentes deben mostrarse antes de escribir y ofrecer:
  1. reemplazar nombre/datos de las fechas existentes;
  2. conservar existentes y crear solo las nuevas;
  3. cancelar sin cambios.
- La operación múltiple debe ser atómica.
- Editar o eliminar un feriado recalcula de forma reactiva calendario y Resumen.
- El calendario muestra una marca pequeña y accesible; nunca sustituye el contenido principal de la fecha.
- El detalle de fecha debe informar nombre o, si no existe, “Feriado”.
- La UI productiva deja de pasar `emptySet()` al motor de horas y utiliza las fechas persistidas observadas.
- Una guardia pertenece al mes de su `localStartDate`, pero las horas feriadas se clasifican por la fecha civil real del intervalo.

### 4.3 Novedades informativas

Categorías informativas:

- `ADDITIONAL_TIME` — tiempo adicional;
- `EARLY_DEPARTURE` — salida anticipada;
- `OTHER` — otra.

Reglas:

- Guardan una descripción privada opcional, normalizada; para `OTHER` la descripción es obligatoria.
- Pueden editarse y eliminarse individualmente.
- No cambian `Shift.startAt`, `Shift.endAt`, `Shift.status` ni ningún cálculo.
- No calcular ni mostrar automáticamente minutos a favor/en contra.
- No crear campos de “hora real de entrada/salida” que alimenten el motor.
- El usuario puede escribir que salió antes, después o cubrió a alguien, pero sigue siendo información manual.

### 4.4 Ausencia y cancelación

- `ABSENCE` y `CANCELLATION` son novedades distintas.
- Aplicarlas actualiza atómicamente la guardia a `ShiftStatus.ABSENT` o `ShiftStatus.CANCELLED`.
- Solo puede existir una novedad activa que controle el estado explícito de una guardia.
- Corregirla puede cambiar entre ausencia, cancelación o volver a `PLANNED`.
- Volver a `PLANNED` elimina la novedad controladora y restaura la proyección temporal derivada del reloj.
- Nunca agregar `COMPLETED` a `ShiftStatus`.
- La corrección debe actualizar guardia y novedad en una sola transacción.
- Ausencia y cancelación continúan con cero trabajadas/pendientes/nocturnas/feriadas y conservan sus horas planificadas informativas.

### 4.5 Cambio formal de horario u objetivo

- `SCHEDULE_CHANGE` y `OBJECTIVE_CHANGE` son cambios formales y sí pueden alterar lo computado.
- La operación actualiza la `Shift` existente; no crea una guardia paralela.
- Debe conservarse una instantánea estructurada del plan original y otra del resultado final.
- La primera modificación formal fija la instantánea original. Correcciones posteriores actualizan solamente la instantánea final; no sobrescriben el original.
- Una misma guardia utiliza como máximo un registro acumulado de cambio formal, capaz de indicar si cambió horario, objetivo o ambos.
- El detalle muestra claramente **Plan original** y **Resultado final**.
- Un cambio de horario actualiza instantes reales, fecha local inicial, zona e instantáneas horarias de manera coherente. No inferir cruces de medianoche desde cadenas.
- Un cambio de objetivo actualiza las instantáneas de nombre, abreviatura, dirección, color, puesto y referencias de plantilla que correspondan.
- Reutilizar las validaciones y advertencias existentes para superposición, segunda guardia y descanso menor a 12 horas.
- No modificar otras guardias ni plantillas históricas.
- La corrección formal de guardia y el registro de cambio deben persistirse atómicamente.

### 4.6 Segunda guardia

- `SECOND_SHIFT` se materializa como otra `Shift` real, nunca como horas anexadas a texto o a la guardia original.
- Reutilizar el flujo existente **Agregar segunda guardia**, con sus advertencias confirmables.
- Al crearse desde **Informar novedad**, guardar además un vínculo de novedad desde la guardia de origen a la segunda guardia.
- Crear ambas piezas debe ser atómico.
- Eliminar la segunda guardia mediante su detalle elimina también el vínculo de novedad correspondiente o lo deja resuelto de forma consistente dentro de la misma transacción.
- No duplicar sus horas: el motor ya suma cada `Shift` una vez.

### 4.7 Corrección y eliminación

- Las novedades pueden corregirse.
- Una novedad puramente informativa puede eliminarse con confirmación breve.
- Los cambios formales no ofrecen una reversión silenciosa. Deben permitir editar el resultado final conservando el original.
- Para restaurar el plan original, mostrar una acción explícita **Restaurar plan original**, vista previa y confirmación. Solo ejecutar si la guardia actual coincide con la última instantánea final; si fue modificada por otro flujo, informar conflicto y no sobrescribir.
- Restauración de guardia y eliminación del registro formal deben ser atómicas.
- Toda eliminación de una guardia debe limpiar notas y novedades dependientes sin dejar referencias huérfanas.

## 5. Contratos de dominio autorizados

Creá en `core/domain` estos modelos o equivalentes nominalmente consistentes. No cambies campos existentes de `Objective`, `ScheduleCombination`, `Shift`, `ExplicitDayStatus` o `MedicalLeave`.

### 5.1 Holiday

```kotlin
data class Holiday(
    val id: UUID,
    val date: LocalDate,
    val name: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### 5.2 ShiftNote

```kotlin
data class ShiftNote(
    val id: UUID,
    val shiftId: UUID,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

La privacidad no es una opción por fila en V1: todas las `ShiftNote` son privadas por definición.

### 5.3 ShiftNovelty

```kotlin
enum class ShiftNoveltyType {
    ADDITIONAL_TIME,
    EARLY_DEPARTURE,
    ABSENCE,
    CANCELLATION,
    SECOND_SHIFT,
    OTHER,
}

data class ShiftNovelty(
    val id: UUID,
    val shiftId: UUID,
    val type: ShiftNoveltyType,
    val description: String?,
    val relatedShiftId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

`SCHEDULE_CHANGE` y `OBJECTIVE_CHANGE` se representan en el registro estructurado de cambio formal, no como texto libre duplicado.

### 5.4 FormalShiftChange

```kotlin
data class ShiftOperationalSnapshot(
    val startAt: Instant,
    val endAt: Instant,
    val zoneId: ZoneId,
    val localStartDate: LocalDate,
    val objectiveName: String,
    val objectiveAbbreviation: String,
    val objectiveAddress: String?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorArgb: Int,
    val position: String?,
    val status: ShiftStatus,
    val sourceObjectiveId: UUID?,
    val sourceScheduleCombinationId: UUID?,
)

data class FormalShiftChange(
    val id: UUID,
    val shiftId: UUID,
    val scheduleChanged: Boolean,
    val objectiveChanged: Boolean,
    val description: String?,
    val original: ShiftOperationalSnapshot,
    val final: ShiftOperationalSnapshot,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

Al menos uno de `scheduleChanged` u `objectiveChanged` debe ser `true`.

### 5.5 Repositorios

Contratos mínimos autorizados:

```kotlin
interface HolidayRepository {
    fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Holiday>>

    suspend fun getById(id: UUID): Holiday?
    suspend fun getByDate(date: LocalDate): Holiday?
    suspend fun insert(holiday: Holiday)
    suspend fun update(holiday: Holiday)
    suspend fun delete(id: UUID)
    suspend fun applyBatch(mutation: HolidayBatchMutation)
}

interface ShiftNoteRepository {
    fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>>
    suspend fun getById(id: UUID): ShiftNote?
    suspend fun insert(note: ShiftNote)
    suspend fun update(note: ShiftNote)
    suspend fun delete(id: UUID)
}

interface ShiftNoveltyRepository {
    fun observeForShift(shiftId: UUID): Flow<List<ShiftNovelty>>
    fun observeFormalChange(shiftId: UUID): Flow<FormalShiftChange?>
    suspend fun getById(id: UUID): ShiftNovelty?
    suspend fun applyMutation(mutation: ShiftNoveltyMutation)
}
```

Definí `HolidayBatchMutation` y `ShiftNoveltyMutation` como modelos explícitos, deterministas y validados. `ShiftNoveltyMutation` debe expresar las operaciones atómicas cerradas de este prompt sin exponer una bolsa arbitraria de escrituras. Preferí una jerarquía sellada con casos nominales para:

- novedad informativa;
- cambio/corrección de estado;
- cambio formal;
- restauración del plan original;
- creación/vinculación de segunda guardia;
- eliminación coherente.

Si una operación real no puede representarse con estos contratos sin ambigüedad, frená y pedí autorización a MAIN antes de ampliar la API pública.

UUID, `Instant`, `Clock` y zona deben recibirse o inyectarse; no ocultes reloj real o aleatoriedad dentro de lógica que necesite pruebas deterministas.

## 6. Persistencia Room y migración obligatoria

Este módulo autoriza una migración real de Room **v1 a v2**.

Agregá exactamente estas cuatro familias de tablas:

1. feriados;
2. notas de guardia;
3. novedades de guardia;
4. cambios formales de guardia.

Condiciones:

- No alterar columnas ni semántica de las cinco tablas v1.
- Feriado con índice único por fecha.
- Nota con clave foránea a guardia y borrado en cascada.
- Novedad con clave foránea a guardia de origen y borrado en cascada.
- `relatedShiftId` es nullable; mantené consistencia transaccional al borrar la guardia relacionada. No dejes referencias inválidas.
- Cambio formal con relación uno a uno por `shiftId`, índice único y borrado en cascada.
- Instantáneas originales/finales deben persistirse estructuradas en columnas Room, no como JSON opaco.
- Exportar y confirmar el esquema Room v2.
- Registrar `Migration(1, 2)` explícita y usarla en producción.
- Prohibido `fallbackToDestructiveMigration` y cualquier recreación destructiva.
- Prohibido `allowMainThreadQueries`.
- Probar una base v1 real con datos en las cinco tablas, abrirla con v2 y demostrar que todo se conserva exactamente.
- Probar claves foráneas, índices únicos, cascadas, rollback y concurrencia relevante.
- No cambiar Gradle salvo una necesidad técnica demostrable y aprobada por MAIN. No se espera ninguna dependencia nueva.

Actualizá `LocalDataStore` para exponer los tres repositorios públicos nuevos. El registro formal puede quedar dentro de `ShiftNoveltyRepository`.

## 7. Atomicidad obligatoria

Usá transacciones Room reales para:

- alta múltiple/reemplazo de feriados;
- cambio entre `PLANNED`, `ABSENT` y `CANCELLED` junto con su novedad;
- cambio formal de horario/objetivo junto con la actualización de `Shift` y el registro original/final;
- restauración del plan original;
- creación de segunda guardia junto con su vínculo;
- eliminación de segunda guardia y resolución del vínculo;
- eliminación de guardia con dependencias.

Validá todo el lote antes de abrir la transacción cuando sea posible. Ante UUID duplicado, conflicto de fecha, referencia inexistente o validación fallida, no debe persistirse ninguna parte.

Protegé acciones de UI contra doble toque y escrituras simultáneas, sin usar un bloqueo global que congele lecturas reactivas.

## 8. Integración con calendario

Extendé la proyección pura del calendario para recibir feriados.

`CalendarDay` puede incorporar:

```kotlin
val holiday: Holiday?
```

Reglas:

- Un feriado no vuelve ocupado un día ni deja de ser implícitamente indefinido.
- La marca de feriado coexiste con una o varias guardias, `F`, `?` o `CM`.
- No utilizar solamente color; aportar semántica y texto en el detalle.
- Al tocar una fecha feriada, el detalle informa el nombre sin tapar las acciones existentes.
- La lista mensual observa `HolidayRepository.observeBetween` y reacciona sin recargar manualmente.
- Agregá entrada **Feriados** desde Configuración. Si el menú mensual ya tiene una extensión coherente, también puede enlazar al mismo flujo, pero no reestructures navegación completa.

## 9. Integración con Resumen

Extendé `SummaryMonthObserver` y `SummaryViewModel` para observar feriados relevantes.

Atención: una guardia atribuida a un mes puede cruzar al día o mes siguiente. Por eso, para calcular horas feriadas del resumen de un mes, observá como mínimo desde el primer día del mes hasta el día posterior al último, o derivá el rango máximo real desde las guardias observadas. No limites incorrectamente los feriados al `YearMonth` visible.

Requisitos:

- pasar `holidayDates` reales a `calculateMonthlyHours`;
- quitar la explicación temporal de que la cifra permanece siempre en cero;
- mostrar cero honestamente cuando no hay intersección;
- recalcular al crear, editar o eliminar feriados;
- conservar las actualizaciones por inicio, fin, minuto activo y medianoche;
- no modificar las fórmulas ya aceptadas del motor.

## 10. Integración con detalle de guardia

El detalle existente debe mostrar, sin revelar contenido en superficies externas:

- notas privadas, con alta/edición/eliminación;
- novedades informativas;
- ausencia o cancelación vigente;
- plan original y resultado final cuando exista cambio formal;
- vínculo a segunda guardia cuando corresponda;
- acción **Informar novedad** en guardias próximas, en curso o completadas;
- corrección posterior de novedades.

No bloquees novedades sobre guardias históricas: una guardia retrocargada puede corregirse.

Para formularios con edición sin guardar, Atrás debe pedir confirmación. Los mensajes deben explicar qué cambia horas y qué es solo informativo.

## 11. Validaciones y errores controlados

Agregá errores de dominio distinguibles, sin incluir textos privados, para:

- fecha de feriado duplicada;
- rango o selección inválida;
- nota vacía;
- descripción obligatoria ausente;
- guardia de origen inexistente;
- segunda guardia inexistente o igual a la original;
- más de una novedad controladora de estado;
- instantánea formal incompleta o incoherente;
- intento de restaurar sobre una guardia que ya no coincide con el resultado final;
- UUID repetido o usado en roles incompatibles;
- conflicto de escritura.

La UI convierte estos errores en mensajes claros y recuperables. No mostrar excepciones Room ni trazas al usuario.

## 12. Pruebas obligatorias

### 12.1 JVM de dominio

Cubrir como mínimo:

1. normalización y validación de feriado;
2. un feriado por fecha;
3. lote múltiple con reemplazar, conservar y cancelar;
4. nota vacía rechazada;
5. múltiples notas ordenadas establemente;
6. novedad de tiempo adicional sin cambiar horas;
7. salida anticipada sin cambiar horas;
8. `OTHER` sin descripción rechazado;
9. ausencia y cancelación diferenciadas;
10. volver de ausencia/cancelación a `PLANNED`;
11. cambio formal de horario actualiza horas;
12. cambio formal de objetivo conserva plan original;
13. corrección posterior conserva la primera instantánea original;
14. restauración rechazada si la guardia actual diverge;
15. segunda guardia contada una sola vez;
16. guardia pasada continúa derivándose como completada si queda `PLANNED`;
17. feriado que corta una guardia nocturna;
18. feriado del día siguiente en una guardia del último día del mes;
19. fin/inicio de año;
20. febrero bisiesto;
21. superposición de nocturna, feriada y extra sin duplicar trabajadas;
22. ausencia/cancelación/CM excluidas de horas feriadas;
23. orden estable con múltiples novedades;
24. relojes, UUID e instantes deterministas.

### 12.2 Room e instrumentación

Cubrir como mínimo:

- migración v1→v2 conservando filas y valores de las cinco tablas originales;
- esquema v2 exportado y validado;
- unicidad de fecha de feriado;
- CRUD y `Flow` de feriados, notas y novedades;
- cascada al eliminar guardia;
- vínculo coherente de segunda guardia;
- cambio de estado atómico;
- cambio formal atómico;
- lote de feriados atómico;
- rollback completo ante error intermedio;
- reapertura de base conservando datos;
- ausencia de consultas en hilo principal.

### 12.3 Compose y aplicación

Cubrir como mínimo:

- Configuración > Feriados;
- alta individual y múltiple;
- conflicto de fecha con las tres decisiones;
- indicador de feriado coexistiendo con cada estado principal del calendario;
- resumen reactivo con horas feriadas reales;
- guardia del 31 con feriado del día siguiente;
- alta, edición y eliminación de nota;
- contenido de nota ausente de semántica global no relacionada;
- cada categoría de Informar novedad;
- mensajes que distinguen cambios informativos y cambios de horas;
- plan original frente a resultado final;
- corrección y restauración confirmada;
- estado vacío, carga, error y reintento;
- recreación de actividad conservando formularios o selección razonable;
- tema claro y oscuro;
- retrato y paisaje;
- lector de pantalla y orden semántico;
- insets sin solapamiento con barras del sistema.

## 13. Regla estricta de escala visual

La decisión actual de Joaquin reemplaza cualquier instrucción antigua sobre fuente al 200 %:

- MiGuardia mantiene tipografía, escala y distribución predeterminadas.
- No crear variantes por `font_scale`.
- No consultar ni usar zoom, tamaño de visualización o densidad configurada por el usuario.
- La app usa la densidad estable del dispositivo ya centralizada en `MiGuardiaTheme`.
- No modificar `font_scale`, zoom, tamaño de visualización ni densidad durante las pruebas.
- No retirar ni puentear la política central existente del tema.
- No agregar `LocalDensity.current.fontScale`, `screenWidthDp`, `screenHeightDp`, `densityDpi` ni lógica equivalente para reordenar la interfaz.

Adaptá contenido mediante scroll y layouts robustos dentro de una única estructura, sin observar esos ajustes del sistema.

## 14. Verificación física obligatoria

Usá el Samsung Galaxy S25 Ultra conectado. Para instrumentación con un solo teléfono ejecutá siempre `--max-workers=1`.

Recorrido manual mínimo, únicamente con datos ficticios:

- crear feriado individual y múltiple;
- editar y eliminar;
- comprobar indicador junto a guardia, `F`, `?` y `CM`;
- crear una guardia nocturna del último día de mes y marcar feriado el día siguiente;
- verificar horas feriadas en Resumen;
- agregar y editar nota privada;
- registrar tiempo adicional y salida anticipada, comprobando que no cambian horas;
- alternar ausencia/cancelación y corregir a normal;
- cambiar formalmente horario y objetivo;
- crear segunda guardia;
- comprobar plan original/resultado final;
- cerrar/reabrir y recrear actividad;
- tema claro/oscuro;
- retrato/paisaje;
- barras del sistema y navegación.

Registrá la configuración inicial antes de tocar tema u orientación y restaurala al finalizar. No cambies ni consultes zoom/densidad. No cambies `font_scale`.

No uses nombres, horarios ni cronogramas reales de Joaquin. Eliminá al finalizar todos los datos ficticios y capturas temporales del teléfono.

## 15. Comando global obligatorio

Al finalizar ejecutá exactamente:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Después obtené los conteos exactos desde los XML de resultados. No informes cantidades estimadas.

Además ejecutá:

- `git status`;
- `git diff` completo;
- `git diff --check`, incluyendo archivos no rastreados;
- comparación del esquema v1 original con el commit base;
- inspección del esquema v2;
- búsqueda de `fallbackToDestructiveMigration`;
- búsqueda de `allowMainThreadQueries`;
- búsqueda de `COMPLETED` persistido;
- búsqueda de secretos, datos reales, logs sensibles y archivos generados;
- búsqueda de adaptaciones por `fontScale`, zoom o densidad fuera de la política central aprobada.

## 16. Archivos autorizados

Podés modificar o crear únicamente lo necesario dentro de:

- `core/domain/src/main/` y `core/domain/src/test/`;
- `core/database/src/main/`, `src/test/` y `src/androidTest/`;
- `core/database/schemas/` para el esquema v2;
- `app/src/main/java/com/blackatsystems/miguardia/`;
- `app/src/main/res/values/strings.xml`;
- `app/src/androidTest/` y `app/src/test/`;
- `docs/adr/` para una ADR nueva de este módulo.

No modifiques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md`;
- este prompt;
- Gradle o catálogo de versiones;
- manifiesto o permisos;
- firma;
- archivos de fotos/escalas;
- módulos no relacionados.

Si necesitás salir de esos límites, frená y pedí autorización a MAIN antes de editar.

## 17. Fuera de alcance

No implementar:

- fotos mensuales;
- feriados automáticos o descarga de calendarios;
- notificaciones;
- motor de próximo evento;
- widget;
- clima;
- remuneración;
- informes, PDF o XLSX;
- copias de seguridad;
- cuentas, nube o sincronización;
- historial forense ilimitado de revisiones;
- cálculo automático de minutos a favor/en contra;
- entrada/salida real que altere horas por sí sola;
- permisos nuevos;
- cambios visuales basados en fuente o zoom del sistema.

## 18. Entrega obligatoria a MAIN

No hagas commit, push ni merge.

Entregá:

- ruta absoluta del worktree;
- commit base y HEAD final;
- resumen funcional exacto;
- defectos encontrados y correcciones;
- contratos públicos agregados;
- tablas, índices, claves foráneas y migración v1→v2;
- confirmación de preservación de datos v1;
- archivos nuevos y modificados;
- comando global ejecutado y resultado;
- conteos exactos JVM, app instrumentada y Room instrumentada;
- resultado de lint, debug y release;
- recorrido real en S25 Ultra;
- configuración del teléfono restaurada;
- confirmación de que no se modificó fuente, zoom ni densidad;
- estado Git y `git diff --check`;
- resultado de búsqueda de secretos y configuraciones prohibidas;
- limitaciones pendientes;
- instrucciones concretas para que MAIN integre y audite.

No declares terminado lo que no hayas ejecutado realmente. Si una prueba obligatoria no pudo realizarse, indicá exactamente cuál, por qué y qué evidencia falta.
