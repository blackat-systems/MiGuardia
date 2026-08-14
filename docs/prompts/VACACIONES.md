# Prompt especializado: VACACIONES

> Estado: listo para ejecutar en un worktree especializado
>
> Fecha: 2026-08-14
>
> Autoridad: MAIN y decisiones explícitas de Joaquin
>
> Padre funcional: integración auditada de NOVEDADES, FERIADOS Y NOTAS sobre Room v2
>
> Commit base del worktree: el HEAD de `main` que contiene este documento y que MAIN entregará junto con la tarea. No iniciar desde `7998745`, porque ese commit todavía no contiene Room v2 ni este prompt.

## 1. Rol y misión

Sos la dependencia especializada **VACACIONES** de MAIN para MiGuardia.

Implementá, depurá y verificá completamente el registro manual de vacaciones, su persistencia local, su representación en Calendario y su efecto diario —no monetario— en Resumen. Trabajá únicamente en un worktree creado exactamente desde el commit base entregado por MAIN.

No sos MAIN. No podés redefinir el producto, ampliar el alcance por conveniencia, inventar reglas salariales ni hacer commit, push o merge. Al terminar, devolvé el trabajo sin confirmar con un informe verificable para que MAIN revise el diff completo, repita la batería global e integre.

## 2. Lectura obligatoria

Antes de planificar o modificar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. este documento;
4. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
5. `docs/adr/0002-persistencia-local-v1.md`;
6. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
7. `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`;
8. `docs/adr/0005-motor-basico-de-horas.md`;
9. `docs/adr/0006-novedades-feriados-y-notas.md`;
10. `docs/prompts/DATA_LOCAL.md`;
11. `docs/prompts/CALENDARIO_MENSUAL.md`;
12. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
13. `docs/prompts/MOTOR_BASICO_DE_HORAS.md`;
14. `docs/prompts/NOVEDADES_FERIADOS_Y_NOTAS.md`;
15. las auditorías existentes en `docs/audits/`;
16. el código y las pruebas relacionados de `core/domain`, `core/database`, Calendario, Resumen y Novedades/Feriados.

Jerarquía: instrucción explícita actual de Joaquin; `docs/PROMPT_MAESTRO_MAIN.md`; `AGENTS.md`; ADR y prompts; implementación existente.

Antes de editar, verificá y registrá:

- ruta real del worktree;
- `git status`;
- rama o detached HEAD;
- `git rev-parse HEAD`;
- coincidencia exacta con el commit base entregado por MAIN;
- que HEAD contenga este prompt y Room v2 ya integrado;
- dispositivo físico visible por ADB;
- esquema Room v1 y v2 existentes y sus hashes/identidades.

Si HEAD no coincide, hay cambios ajenos o Room v2 no está integrado, frená y avisá a MAIN. No uses `git reset --hard`, no descartes cambios y no adaptes silenciosamente la tarea a otra base.

## 3. Resultado verificable

Al finalizar, Joaquin debe poder:

- agregar vacaciones manualmente indicando fecha inicial y fecha final inclusiva;
- crear períodos pasados, actuales o futuros;
- atravesar meses y años con un mismo período;
- editar o eliminar un período con confirmación;
- ver `V` en cada fecha de vacaciones del Calendario;
- abrir el detalle del día y conocer el período de vacaciones aplicable;
- conservar visibles, sin borrarlos, feriados, francos `F`, días `?` y guardias que coincidan con vacaciones;
- recibir un error claro si intenta superponer vacaciones con carpeta médica;
- ver en Resumen la cantidad de días corridos de vacaciones del mes;
- comprobar que una guardia normal dentro de vacaciones no genera ninguna clase de horas;
- comprobar que ausencia o cancelación explícita prevalece sobre vacaciones;
- cerrar y reabrir la aplicación sin perder los períodos.

Todo debe funcionar localmente y sin internet, cuentas, nube, telemetría ni permisos nuevos.

## 4. Decisiones cerradas de producto

### 4.1 Naturaleza del período

- Vacaciones es un período manual con `startDate` y `endDateInclusive`.
- Ambas fechas están incluidas y representan días civiles locales completos.
- Puede atravesar fin de mes, fin de año y febrero bisiesto.
- Puede cargarse retroactivamente o en el futuro.
- No requiere una guardia ni una plantilla asociada.
- No agregar título, importe, comprobante, imagen ni nota en este incremento.
- No crear una fila por cada día: persistir el período y derivar sus fechas.
- Dos períodos de vacaciones no pueden compartir ninguna fecha. Rechazar solapamientos con un error controlado; los períodos contiguos son válidos y permanecen separados.

### 4.2 Calendario y coexistencia

- Cada fecha comprendida muestra un indicador accesible `V`.
- `V` puede coexistir con feriado, `F`, `?`, una o más guardias o un día implícitamente sin definir.
- Agregar vacaciones no elimina ni modifica feriados, estados diarios ni guardias.
- Una guardia sigue siendo accesible desde el detalle del día. Allí debe explicarse que no computa horas si queda excluida por vacaciones.
- El detalle del día muestra las fechas inicial y final del período aplicable.
- Una fecha no puede pertenecer simultáneamente a Vacaciones y Carpeta médica. Rechazar la creación o edición que produzca cualquier intersección; no borrar ni recortar automáticamente ninguno de los períodos.
- La interfaz debe permitir acceder al alta desde el flujo Agregar del Calendario. Integrá la administración mensual o de períodos en la superficie más coherente con la navegación actual, sin agregar un cuarto destino inferior.

### 4.3 Horas y precedencia

Vacaciones es una clasificación por días, no una equivalencia artificial de horas.

Para cada `Shift`, aplicar esta precedencia:

1. `ABSENT`;
2. `CANCELLED`;
3. Vacaciones según `localStartDate`;
4. Carpeta médica;
5. división normal entre trabajada y pendiente para `PLANNED`.

Como la persistencia impedirá Vacaciones/Carpeta médica superpuestas, el orden entre ambas es además defensivo para datos imposibles o legados.

Consecuencias:

- una guardia `PLANNED` cuya `localStartDate` cae en vacaciones se conserva intacta, pero se excluye de `planned`, `worked`, `pending`, `overtime`, `nightWorked` y `holidayWorked`;
- no crear `vacationHours`;
- no agregar `VACATION` ni `COMPLETED` a `ShiftStatus`;
- `ABSENT` y `CANCELLED` prevalecen y conservan sus horas/categorías actuales, aunque la fecha pertenezca a vacaciones;
- `shiftCount` conserva su significado actual y continúa contando registros `Shift`; no redefinirlo silenciosamente;
- `vacationDayCount` cuenta la unión de fechas de vacaciones dentro del mes, aunque no exista guardia y aunque la fecha también sea feriado, `F` o `?`;
- el cálculo mensual sigue usando `Duration` e instantes para guardias, pero vacaciones se calcula exclusivamente con `LocalDate` y rangos inclusivos;
- la invariante vigente permanece para las guardias incluidas en `planned`; las guardias excluidas por vacaciones quedan fuera de ambos lados.

### 4.4 Remuneración: únicamente documentación y límite

Este módulo no calcula dinero.

Decisiones respaldadas que deben preservarse para el futuro:

- la retribución vacacional de personal mensualizado se basa en la remuneración computable al inicio dividida por 25, conforme al artículo 155 de la Ley de Contrato de Trabajo;
- SUVICO acuerda adicionales vacacionales remunerativos por día y sus valores/topes cambian por vigencia;
- fuentes públicas confirman actualización mensual para julio–diciembre de 2026, pero las escalas locales disponibles no muestran esos importes exactos y el acta completa no está archivada en el repositorio;
- no derivar el adicional desde básico, jornada, nocturnidad ni otro valor;
- no hardcodear importes, topes, componentes remunerativos, prorrateos ni descuentos;
- no persistir dinero ni crear todavía un motor de remuneración vacacional.

Fuentes documentales ya registradas por MAIN:

- `https://www.argentina.gob.ar/normativa/nacional/25552/actualizacion`;
- `https://www.suvico.org.ar/`;
- `https://lmdiario.com.ar/contenido/522769/el-personal-de-vigilancia-logro-un-acuerdo-salarial-con-aumentos-progresivos-has`.

No hace falta navegar nuevamente para implementar este módulo salvo contradicción real o necesidad técnica vigente.

## 5. Contratos autorizados

Podés agregar en `core/domain`:

```kotlin
data class Vacation(
    val id: UUID,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

```kotlin
interface VacationRepository {
    fun observeOverlapping(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Vacation>>

    suspend fun getById(id: UUID): Vacation?
    suspend fun insert(vacation: Vacation)
    suspend fun update(vacation: Vacation)
    suspend fun delete(id: UUID)
}
```

Normalización y validación autorizadas:

- `startDate <= endDateInclusive`;
- `updatedAt >= createdAt`;
- UUID recibido explícitamente;
- rechazo de períodos de vacaciones superpuestos;
- rechazo de intersección con carpeta médica;
- actualización inexistente o conflictiva mediante error de dominio controlado;
- una actualización no puede reutilizar un UUID para transformar silenciosamente otro período.

Podés nombrar errores concretos coherentes con `DataErrors.kt`, por ejemplo:

- `InvalidVacationRangeException`;
- `OverlappingVacationException`;
- `VacationMedicalLeaveConflictException`.

No cambies modelos existentes salvo las extensiones de proyección y resumen expresamente autorizadas aquí.

## 6. Extensiones exactas del motor y proyección

Extendé `calculateMonthlyHours` con una entrada compatible por defecto:

```kotlin
vacations: List<Vacation> = emptyList()
```

Extendé `MonthlyHoursSummary` con:

```kotlin
val vacationDayCount: Int
```

Actualizá `emptyMonthlyHoursSummary` con cero días de vacaciones.

No agregues `vacationHours` ni campos monetarios.

Extendé la proyección de Calendario con la mínima información necesaria para:

- saber si una fecha pertenece a vacaciones;
- identificar el período aplicable;
- anunciarlo mediante semántica accesible;
- coexistir con feriado, estado diario y guardias.

Mantené parámetros nuevos con valores predeterminados cuando sea razonable para reducir roturas, pero actualizá todas las llamadas productivas y pruebas relevantes para que producción observe datos reales.

## 7. Persistencia Room v3

Room v1 y v2 ya son esquemas publicados e inmutables.

Agregá una única tabla nueva `vacations` con:

- `id` UUID serializado como `TEXT`, clave primaria;
- `startDate` como fecha ISO;
- `endDateInclusive` como fecha ISO;
- `createdAt` y `updatedAt` con la representación temporal ya usada por el proyecto;
- índice por `startDate`;
- índice por `endDateInclusive`;
- sin claves foráneas.

Room pasa de versión 2 a versión 3. Creá y exportá el esquema v3.

La migración `MIGRATION_2_3` debe:

- crear explícitamente solo `vacations` y sus índices;
- no eliminar, recrear ni alterar ninguna de las nueve tablas v2;
- conservar intactas todas las filas v2;
- integrarse junto con `MIGRATION_1_2`, permitiendo la cadena real v1→v2→v3;
- no usar migración destructiva.

La protección contra solapamientos de vacaciones y contra intersección con carpetas médicas debe ocurrir dentro de la misma transacción de escritura. No alcanza con validar en la UI. Las consultas de conflicto deben excluir el propio ID al editar.

Actualizá `LocalDataStore` para exponer `VacationRepository` sin introducir inyección de dependencias nueva.

## 8. Interfaz y estado

La UI debe incluir:

- selector de fecha inicial;
- selector de fecha final inclusiva;
- vista previa de cantidad de días corridos;
- alta y edición;
- confirmación antes de eliminar;
- mensaje explícito ante solapamiento de vacaciones;
- mensaje explícito ante conflicto con carpeta médica;
- lista o contexto suficiente para localizar y administrar los períodos aplicables;
- estados carga, contenido, vacío, error y reintento;
- confirmación al salir con cambios sin guardar;
- conservación razonable de superficie, mes/período seleccionado y borrador mediante `SavedStateHandle` o el mecanismo ya adoptado;
- actualización reactiva de Calendario y Resumen.

No solicites título, nota, dinero ni comprobante.

No borres, recortes ni cambies silenciosamente guardias, `F`, `?`, feriados o carpetas médicas.

Los textos deben explicar:

- que los días se cuentan de forma corrida e inclusiva;
- que una guardia normal dentro del período no computa horas;
- que los cálculos salariales de vacaciones se incorporarán posteriormente con fuentes verificadas.

## 9. Escala visual, accesibilidad e insets

Decisión obligatoria de Joaquin:

- MiGuardia mantiene su tipografía, escala visual y distribución predeterminadas;
- no consultes ni modifiques `font_scale`;
- no consultes ni modifiques zoom, tamaño de visualización o densidad;
- no agregues adaptaciones basadas en esos valores;
- conservá la política central de densidad estable de `MiGuardiaTheme`;
- usá scroll y una única estructura robusta;
- respetá barras del sistema e insets;
- verificá tema claro/oscuro, retrato/paisaje, lector de pantalla, orden semántico y objetivos táctiles.

No agregues `LocalDensity.current.fontScale`, `screenWidthDp`, `screenHeightDp`, `densityDpi` ni equivalentes para adaptar la interfaz.

## 10. Pruebas JVM obligatorias

Cubrí como mínimo:

1. rango de un día;
2. rango inclusivo de varios días;
3. rango inválido rechazado;
4. períodos contiguos permitidos;
5. períodos solapados rechazados;
6. edición que conserva UUID y `createdAt`;
7. edición inexistente/conflictiva rechazada;
8. unión de fechas y recorte al mes;
9. vacaciones que cruzan mes;
10. vacaciones que cruzan año;
11. febrero bisiesto;
12. día de vacaciones sin guardia;
13. guardia `PLANNED` en vacaciones excluida de `planned`, `worked` y `pending`;
14. guardia pasada `PLANNED` en vacaciones no aparece como trabajada;
15. guardia en curso `PLANNED` en vacaciones no activa actualización por minuto;
16. guardia nocturna en vacaciones sin horas nocturnas;
17. guardia en vacaciones y feriado sin horas feriadas;
18. guardia en vacaciones sin horas extra;
19. ausencia dentro de vacaciones prevalece y conserva `absenceHours`;
20. cancelación dentro de vacaciones prevalece y conserva `cancellationHours`;
21. carpeta médica superpuesta rechazada;
22. vacaciones coexistiendo con `F`, `?` y feriado sin duplicar días;
23. `shiftCount` conserva la semántica previa;
24. invariante mensual correcta con guardias vacacionales excluidas;
25. reloj, zona, UUID e instantes deterministas.

## 11. Pruebas Room e instrumentadas obligatorias

Cubrí como mínimo:

- creación real de una base v2 con datos representativos en las nueve tablas;
- migración v2→v3 conservando filas y valores de las nueve tablas;
- cadena v1→v2→v3 conservando los cinco datos v1 representativos;
- validación del esquema v3 exportado;
- tabla `vacations` inicialmente vacía tras migrar;
- CRUD y `Flow` reactivo;
- consulta por intersección inclusiva;
- persistencia tras cerrar y reabrir la base;
- rechazo transaccional de vacaciones solapadas;
- rechazo transaccional de conflicto con carpeta médica;
- rollback completo ante error;
- edición que no se detecta como conflicto consigo misma;
- ausencia de consultas en el hilo principal.

No modifiques los archivos de esquema v1 o v2. Comparalos byte a byte o por hash con el commit base.

## 12. Pruebas Compose y aplicación

Cubrí como mínimo:

- acceso a Vacaciones desde Agregar;
- alta de un día y de un rango;
- vista previa inclusiva;
- período atravesando mes/año;
- edición y eliminación confirmada;
- volver con cambios sin guardar;
- error recuperable por solapamiento;
- error recuperable por carpeta médica;
- `V` en Calendario;
- coexistencia visual y semántica con feriado, `F`, `?` y guardia;
- detalle del período y explicación de guardia excluida;
- Resumen reactivo con `vacationDayCount`;
- guardia vacacional sin horas;
- ausencia/cancelación prevaleciente;
- estados carga, vacío, error y reintento;
- conservación al recrear actividad;
- tema claro/oscuro;
- retrato/paisaje;
- lector de pantalla y orden semántico;
- insets sin solapamiento con barras del sistema.

## 13. Verificación física obligatoria

Usá el Samsung Galaxy S25 Ultra conectado. Con un único teléfono físico, ejecutá instrumentación con `--max-workers=1`.

Recorrido manual mínimo, solo con datos ficticios:

1. registrar un período de un día;
2. editarlo a un rango que atraviese fin de mes;
3. comprobar `V` en ambos meses;
4. comprobar detalle y cantidad mensual;
5. crear feriado, `F` o `?` dentro del rango y verificar coexistencia;
6. crear una guardia dentro del rango y verificar cero horas;
7. alternarla temporalmente a ausencia y cancelación y comprobar precedencia;
8. intentar carpeta médica superpuesta y comprobar rechazo sin pérdida;
9. intentar vacaciones solapadas y comprobar rechazo;
10. cerrar/reabrir y recrear la actividad;
11. eliminar el período con confirmación;
12. tema claro/oscuro;
13. retrato/paisaje;
14. barras del sistema e insets.

Registrá tema y orientación iniciales y restauralos al finalizar. No consultes ni modifiques fuente, zoom, tamaño de visualización o densidad. Eliminá todos los datos y archivos QA creados.

## 14. Comando global obligatorio

Ejecutá exactamente:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Después obtené conteos exactos desde los XML, separados en JVM, aplicación instrumentada y Room instrumentada.

Además verificá:

- `git status`;
- `git diff` completo;
- todos los archivos no rastreados;
- `git diff --check`, incluyendo archivos nuevos;
- esquemas Room v1 y v2 idénticos al commit base;
- esquema Room v3 y su identidad;
- migraciones reales v1→v2→v3;
- ausencia de `fallbackToDestructiveMigration`;
- ausencia de `allowMainThreadQueries`;
- ausencia de `COMPLETED` o `VACATION` persistidos en `ShiftStatus`;
- ausencia de secretos, datos reales, logs sensibles y archivos generados;
- ausencia de adaptaciones por fuente, zoom o densidad fuera de la política central aprobada;
- ausencia de nuevas dependencias y permisos.

## 15. Archivos autorizados

Podés modificar o crear únicamente lo necesario dentro de:

- `core/domain/src/main/` y `core/domain/src/test/`;
- `core/database/src/main/`, `src/test/` y `src/androidTest/`;
- `core/database/schemas/` únicamente para agregar el esquema v3;
- `app/src/main/java/com/blackatsystems/miguardia/`;
- `app/src/main/res/values/strings.xml`;
- `app/src/androidTest/` y `app/src/test/`;
- `docs/adr/` para una ADR nueva del módulo.

No modifiques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md`;
- este prompt;
- esquemas Room v1 o v2;
- Gradle, catálogo de versiones o wrapper;
- AndroidManifest o permisos;
- firma;
- `escalas_salariales/`;
- módulos no relacionados.

Si necesitás salir de esos límites, frená y pedí autorización a MAIN antes de editar.

## 16. Fuera de alcance

No implementar:

- remuneración vacacional;
- valores o topes del adicional SUVICO;
- sueldo, recibo, bruto o neto;
- importación o descarga de escalas;
- vacaciones automáticas según antigüedad;
- solicitud o aprobación empresarial de vacaciones;
- saldos disponibles, gozados o pendientes;
- licencias distintas de Vacaciones y Carpeta médica;
- fotos, comprobantes o adjuntos;
- notificaciones;
- motor de próximo evento;
- clima, widget, informes o copias de seguridad;
- nube, cuentas o sincronización;
- permisos o dependencias nuevas;
- cambios visuales basados en fuente, zoom o densidad.

## 17. ADR obligatoria

Creá una ADR nueva que documente:

- por qué Vacaciones es un período de días y no horas;
- precedencia frente a estados de guardia;
- exclusión de guardias normales del total planificado;
- coexistencia con feriados y estados diarios;
- prohibición de superposición con carpeta médica;
- Room v3 y migración v2→v3;
- límite explícito del cálculo monetario y fuentes pendientes.

No marques la ADR como aceptada por MAIN; dejala como propuesta implementada para revisión.

## 18. Entrega obligatoria a MAIN

No hagas commit, push ni merge.

Entregá:

- ruta absoluta del worktree;
- commit base y HEAD final;
- resumen funcional exacto;
- decisiones aplicadas y defectos corregidos;
- API pública final;
- tabla, índices y validaciones de Vacaciones;
- migración v2→v3 y resultado de la cadena v1→v2→v3;
- identidades de los esquemas v1, v2 y v3;
- confirmación de que v1 y v2 no cambiaron;
- archivos nuevos y modificados;
- comando global ejecutado y resultado;
- conteos exactos JVM, app instrumentada y Room instrumentada;
- resultado de lint, debug y release;
- recorrido real en el S25 Ultra;
- configuración restaurada;
- confirmación de que no se consultó ni modificó fuente, zoom ni densidad;
- estado Git y `git diff --check`;
- resultado de búsquedas de secretos y configuraciones prohibidas;
- limitaciones pendientes, especialmente remuneración vacacional;
- instrucciones concretas para que MAIN integre y audite.

No declares terminado lo que no ejecutaste realmente. Si una verificación obligatoria no pudo realizarse, indicá exactamente cuál, por qué y qué evidencia falta.
