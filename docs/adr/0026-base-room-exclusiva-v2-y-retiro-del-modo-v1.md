# ADR 0026: base Room exclusiva V2 y retiro del modo V1

- Estado: aceptada
- Fecha: 2026-08-23

## Contexto

ADR 0024 estableció que MiGuardia 1.0 es únicamente la base de código de
MiGuardia 2.0. No existe una población usuaria ni un conjunto de datos 1.0 que
deba migrarse. El árbol de desarrollo, sin embargo, todavía abre
`miguardia.db`, registra la cadena Room `1→2→3→4→5→6→7`, conserva el origen
`MIGRATED_V1` y permite distinguir jornadas y recorridos V1 de jornadas V2.

No es correcto declarar esa versión 7 como la primera base pública de V2:
contiene tablas, columnas, contratos y pruebas creados exclusivamente para una
compatibilidad que ya no forma parte del producto. Tampoco corresponde ejecutar
una migración `7→8` que transforme o elimine esos datos, porque MiGuardia 2.0 no
ofrece una actualización de datos desde la prueba anterior y no puede borrar
estado local en silencio.

Al mismo tiempo, piezas nacidas en 1.0 —como `Objective`, `Shift`, la grilla, los
feriados, las vacaciones, las fotos, el motor de próximo evento y el
reconciliador de notificaciones— siguen siendo una base técnica válida. Retirar
el modo V1 no significa reescribirlas ni borrarlas por su antigüedad.

## Decisión

### Nueva identidad de Room

La primera base de persistencia de MiGuardia 2.0 será:

- clase `MiGuardiaV2Database`;
- archivo físico `miguardia-v2.db`;
- versión Room `1`;
- esquema exportado propio en
  `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/1.json`.

La aplicación V2 no registra migraciones desde `MiGuardiaDatabase` v1–v7. No
abre, copia, transforma ni borra `miguardia.db`. La cadena anterior, sus pruebas
de migración y sus esquemas dejan de formar parte de la compilación activa de
la rama V2; el tag `v1.0.0`, `main` y la historia Git continúan como evidencia
íntegra.

El mismo `applicationId` se conserva por ahora, pero no convierte este diseño
en una actualización compatible. La experiencia garantizada comienza con los
datos de la aplicación limpios. Toda desinstalación o limpieza de una
instalación anterior es externa, expresa y separada; el runtime no la ejecuta.

### Esquema inicial V2

La base V2 conserva exactamente diecinueve tablas de aplicación que ya
sostienen una función V2 o una capacidad común confirmada, además de los
metadatos internos que Room y SQLite crean por su cuenta:

1. `objectives`;
2. `shifts`;
3. `shift_work_snapshots`;
4. `explicit_day_statuses`;
5. `medical_leaves`;
6. `holidays`;
7. `shift_notes`;
8. `vacations`;
9. `schedule_photos`;
10. `shift_notification_configs`;
11. `shift_notification_reminders`;
12. `work_configuration_roots`;
13. `per_period_hours_definitions`;
14. `work_configuration_revisions`;
15. `per_period_hours_values`;
16. `work_places`;
17. `work_types`;
18. `work_templates`;
19. `workplace_rule_revisions`.

No forman parte de la base inicial V2:

- `schedule_combinations`;
- `shift_novelties`;
- `formal_shift_changes`.

Las dos últimas pertenecen al recorrido estructural de Novedades V1. Las
situaciones especiales V2 se modelarán en su bloque propio y no quedarán
condicionadas por esas tablas antiguas.

También se retiran del contrato persistido:

- `work_configuration_roots.origin`;
- `work_templates.legacyScheduleCombinationId`;
- `shifts.sourceScheduleCombinationId`;
- toda clave foránea o índice asociado exclusivamente a esas columnas.

Fuera de esas tres tablas, esas tres columnas y el cambio de
`Shift.sourceObjectiveId` a obligatorio, cada entidad conservada mantiene
exactamente la definición de Room v7: nombres y tipos de columnas, valores por
defecto, nulabilidad, claves primarias, índices y restricciones únicas, claves
foráneas y acciones `ON DELETE`/`ON UPDATE`. Cualquier otro cambio de esquema
requiere una decisión nueva de MAIN y no puede resolverlo el implementador.

`Shift.sourceObjectiveId` pasa a ser obligatorio dentro del contrato V2. Cada
jornada almacenada debe tener exactamente una `ShiftWorkSnapshot`; una jornada
sin fotografía no representa un modo anterior válido, sino datos locales
inválidos. Todas las escrituras estructurales continúan pasando por
`V2ShiftRepository` para guardar o modificar el par en una sola transacción.

### Dominio y ejecución

Se retiran `WorkConfigurationOrigin`, `MIGRATED_V1`, los estados
`LegacyV1`/`LegacyV1WithFutureActivation`, la activación futura, la adopción de
objetivos u horarios V1 y las variantes que aceptan una jornada sin fotografía
como `LegacyV1`.

Una instalación sin raíz es `FreshInstall`. Toda raíz válida nace con su primera
revisión V2 y luego se proyecta únicamente como `V2NeedsFirstSet` o `V2Ready`.
Las fechas anteriores a la primera revisión siguen usando la retrocarga
consciente ya implementada; no se clasifican como V1.

Se eliminan del runtime el CRUD V1 de objetivos, horarios, guardias y francos,
el Perfil laboral fijo de Vigilancia, el Resumen V1 y Novedades V1. La única
grilla mensual, `Objective` como identidad física de `WorkPlace`, `Shift`, las
fotografías, el Calendario, la configuración, la carga manual, la edición y
eliminación V2, F/?, feriados, vacaciones, carpetas médicas, fotos, próximo
evento, notificaciones, clima y preferencias comunes continúan.

Los DataStore y preferencias que ya representan funciones comunes —por
ejemplo apariencia, notificaciones y clima— se reutilizan. Ninguno puede decidir
el estado de configuración laboral ni evitar el selector inicial.

La planificación conserva el nombre o apodo opcional en su almacén dueño. Por
eso `GuardProfileStore` y `guard_profile.preferences_pb` quedan únicamente como
contrato neutral de `displayName`; se retiran `company`, la empresa `Inforce`,
la profesión fija `Vigilancia y seguridad`, sus defaults y todas las
proyecciones laborales V1. El archivo de preferencias no se borra, copia ni
migra. Este bloque desmonta la pantalla, el ViewModel y el cableado del Perfil,
no abre ese almacén durante el recorrido V2 y no crea una interfaz de Perfil V2.

### Versionado futuro

El esquema exportado de `MiGuardiaV2Database` versión 1 es la primera base
pública de V2. Toda ampliación posterior de Room comienza en `1→2`, exporta su
esquema y preserva todos los datos creados dentro de V2 mediante una migración
explícita.

## Consecuencias

- una instalación V2 limpia siempre comienza en el selector de cuatro rubros;
- no existe una bifurcación de motor o interfaz basada en el origen de la raíz;
- el archivo Room anterior puede coexistir físicamente, pero queda intacto y
  fuera del runtime V2;
- los datos QA creados en Room v7 dejan de ser visibles para la nueva base; esto
  es deliberado y no constituye una migración;
- las capacidades comunes probadas se conservan sin mantener pantallas o
  escritores V1;
- el próximo cambio de esquema tendrá como origen la versión 1 de la base V2,
  no la versión 7 histórica;
- la eliminación de una fila, archivo, paquete o preferencia anterior nunca se
  realiza automáticamente.

## Alternativas descartadas

### Migrar Room 7→8

Obligaría a definir qué datos V1 convertir o borrar y presentaría esa
transformación como una actualización soportada, en contra de ADR 0024.

### Reutilizar `miguardia.db` con una versión menor o un esquema distinto

Una instalación que todavía conserve la base anterior podría fallar por una
versión o identidad incompatibles. También haría ambiguo cuál es la primera
base pública de V2.

### Conservar Room v7 como baseline

Mantendría origen, migraciones, tablas y procedencias V1 dentro del contrato
que las futuras versiones estarían obligadas a preservar.

### Reescribir todas las entidades heredadas

Confundiría antigüedad con obsolescencia y descartaría componentes que ya
sostienen recorridos V2 verificados.

## Verificación requerida

- creación directa y reapertura de `MiGuardiaV2Database` versión 1;
- esquema exportado, lista exacta de tablas, índices, claves, `integrity_check`
  y `foreign_key_check` correctos;
- ausencia de migraciones registradas desde la base histórica;
- prueba de que crear y usar la base V2 no modifica byte a byte un
  `miguardia.db` testigo;
- rechazo controlado de una jornada sin fotografía;
- primera apertura, primer conjunto, carga, edición, eliminación y
  reconciliación V2 verdes;
- ninguna ruta visible o profunda de modo V1;
- producción intacta y cualquier limpieza limitada al paquete QA después de
  autorización expresa.
