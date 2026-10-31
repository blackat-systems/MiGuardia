# Handoff Stage 2 a MAIN — MiGuardia 3.0

Fecha: 2026-09-14  
Estado: candidato corregido tras revisión visual, verificado localmente y en QA
física sintética; aceptado por Joaquín el 2026-09-15.

## OBJECTIVE

Entregar como una sola capacidad el día `?`, carpeta médica, ausencia,
cancelación, correcciones entre estados y retorno a Planificada, sin perder
jornadas ni degradar Stage 1.

## CHANGES

- Recorridos Compose de alta, edición, confirmación y corrección desde el detalle.
- CAS atómico para `?`, carpeta médica y situaciones; captura la expectativa que
  vio la pantalla, compara jornada/situación/horario real/extras/ocurrencia y
  refresca tras éxito o conflicto.
- Cancelación con metadatos obligatorios y una evidencia Photo Picker opcional,
  privada, versionada, compensada y reconciliada.
- Estado común para Calendario, horas, Resumen, próximo evento, avisos, clima,
  recurrencia, informes y Copias.
- Room y Copias V8 retrocompatibles con V5/V6/V7 y con estados legacy sin fila.

## FILES

- Dominio: `core/domain/**/model/ShiftSituation.kt`, repositorio, Calendario,
  próximo evento/clima y contrato completo de Copias V8.
- Base: Entity/DAO/mappers/repositorio de situación, CAS de `?` y carpeta médica,
  `MIGRATION_7_8`, gateway de Copias y esquema `8.json`.
- App: `CalendarDayDetail.kt`, `CalendarViewModel.kt`, cableado en
  `MiGuardiaApp.kt`/`MainActivity.kt`, formato de fecha, superficies de
  disponibilidad/extras/edición, clima y almacenamiento/Copias privadas.
- Pruebas: dominio/Copias, ViewModel stale, Compose dirigido, store privado,
  Room CAS/migración y fixtures de contrato V8.
- Documentación: matriz, auditoría, STATUS, índice de contratos y este handoff.

Lista literal del diff:

```text
app/build.gradle.kts
app/src/androidTest/java/com/blackatsystems/miguardia/CalendarCommonV2ComposeTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/SchedulePhotoFileStoreInstrumentedTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/SupportMenuActivityTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/UpdatesActivityTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/UpdatesSupervisedActivityTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/V2ManualShiftLoadComposeTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/V2ReadOnlySurfacesInstrumentedTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/V2ShiftEditComposeTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/backup/BackupComposeTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/backup/BackupDocumentPublicationInstrumentedTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/backup/BackupPhotoFilesInstrumentedTest.kt
app/src/androidTest/java/com/blackatsystems/miguardia/backup/PortablePreferencesGatewayInstrumentedTest.kt
app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt
app/src/main/java/com/blackatsystems/miguardia/backup/BackupPhotoFiles.kt
app/src/main/java/com/blackatsystems/miguardia/backup/LocalBackupCoordinator.kt
app/src/main/java/com/blackatsystems/miguardia/ui/CalendarDayDetail.kt
app/src/main/java/com/blackatsystems/miguardia/ui/CalendarFormatting.kt
app/src/main/java/com/blackatsystems/miguardia/ui/CalendarScreen.kt
app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt
app/src/main/java/com/blackatsystems/miguardia/ui/availability/AvailabilityScreens.kt
app/src/main/java/com/blackatsystems/miguardia/ui/calendar/CalendarUiState.kt
app/src/main/java/com/blackatsystems/miguardia/ui/calendar/CalendarViewModel.kt
app/src/main/java/com/blackatsystems/miguardia/ui/hours/HoursAndExtrasScreens.kt
app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftEditScreens.kt
app/src/main/java/com/blackatsystems/miguardia/ui/photos/SchedulePhotoFileStore.kt
app/src/main/java/com/blackatsystems/miguardia/ui/weather/WeatherViewModel.kt
app/src/test/java/com/blackatsystems/miguardia/ui/calendar/CalendarViewModelStage2Test.kt
core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/8.json
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/BackupDatabaseGatewayInstrumentedTest.kt
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/DayOffPersistenceInstrumentedTest.kt
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/MonthlyReportSnapshotInstrumentedTest.kt
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/ShiftSituationPersistenceInstrumentedTest.kt
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/V2DatabaseMigrationInstrumentedTest.kt
core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/VacationPersistenceInstrumentedTest.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/LocalDataStore.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/MiGuardiaV2Database.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/backup/BackupDatabaseGateway.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/dao/AvailabilityWindowDao.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/dao/ShiftSituationDao.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/entity/ShiftSituationEntity.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/mapping/ShiftSituationEntityMappers.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/repository/RoomExplicitDayStatusRepository.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/repository/RoomMedicalLeaveRepository.kt
core/database/src/main/java/com/blackatsystems/miguardia/core/database/repository/RoomShiftSituationRepository.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupComparator.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupContainer.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupModels.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupPayloadCodec.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupSchema.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/calendar/CalendarProjection.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/model/ShiftSituation.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/nextevent/NextEvent.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/repository/ExplicitDayStatusRepository.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/repository/MedicalLeaveRepository.kt
core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/repository/ShiftSituationRepository.kt
core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/backup/BackupContractTest.kt
core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/backup/BackupMemoryBudgetTest.kt
core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/model/ShiftSituationTest.kt
core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/nextevent/NextEventTest.kt
docs/HANDOFF_STAGE_2_A_MAIN_3_0.md
docs/STAGE_2_MATRIZ_ESTADOS_Y_TRANSICIONES.md
docs/STATUS.md
docs/audits/2026-09-14-stage-2-situaciones-estados-dia.md
docs/prompts/README.md
gradle/libs.versions.toml
tools/qa-groups.json
```

No hay cambios fuera de `app/**`, `core/**`, el catálogo test-only de
coroutines y documentación directamente afectada.

## DECISIONS

- Room V8 es necesaria: motivo, nota privada, actor, aviso y metadatos de una
  evidencia no caben de manera honesta en `Shift.status`.
- `Shift.status` conserva `PLANNED|ABSENT|CANCELLED`; `COMPLETED` sigue derivado.
- `shift_situations` controla sólo ausencia/cancelación. Legacy V7 sin fila es
  válido y corregible, pero no recibe metadatos inventados.
- `?` sólo bloquea una jornada cuyo `localStartDate` coincide o un Franco;
  carpeta médica, vacaciones y Retén coincidentes se preservan.
- El namespace `cancel_` comparte cuotas y directorio privado, pero tiene vínculo
  lógico propio y clave UNIQUE nullable. Copias compara también el SHA de Room.
- Un `PLANNED → PLANNED` sin situación se rechaza para evitar cambiar versión u
  ocurrencia recurrente mediante un no-op.

## VALIDATION

- JVM: 862/862 verdes, sin omitidas (392 dominio, 12 base, 458 app).
- AndroidTest app/base: compila; QA APK, runner QA app y runner Debug base
  ensamblan.
- `lintDebug`: verde.
- Copias: V5/V6/V7 legibles; V8 con/sin evidencia, SHA, rutas y metadatos
  validados; fixtures corrientes actualizados a 29 tablas.
- `tools/docs_check.py`: verde tras conservar el fallo inicial documentado.
- `git diff --check`: verde.

### QA física sintética autorizada

- Dispositivo: Samsung `SM-S938B`, Android API 36, serial autorizado
  `[serial QA en binding local privado]`.
- Paquetes técnicos aislados: `com.blackatsystems.miguardia.qa`, su runner y el
  runner de base. El paquete público `com.blackatsystems.miguardia` permaneció
  instalado, separado y fuera de la instrumentación.
- Resultado final: 118/118 pruebas instrumentadas dirigidas verdes. Cubren Room
  V8, migración, situaciones, Franco/carpeta médica, Copias, archivos privados,
  Calendario, Resumen, Horas, Próximo evento, Informes y Clima.
- Recorrido manual sintético: alta, presentación, edición y eliminación
  confirmada de una carpeta médica de un día. El dato técnico se eliminó al
  terminar y el calendario volvió a `Sin definir`.
- Evidencia local no rastreada: `.local-evidence/qa/20260915T002936.651601Z`
  hasta `.local-evidence/qa/20260915T004042.632224Z`, más capturas y jerarquías
  en `.local-evidence/qa/visual-20260915T0041/`.
- El teléfono se desconectó después de completar la QA. Quedaron instalados la
  variante QA y sus runners, y no pudo confirmarse el retiro de las capturas
  sintéticas temporales `miguardia-stage2-*` de `/sdcard`; no contienen datos
  personales. Su limpieza es operativa, no una condición de validez de Stage 2.
- Joaquín volvió a conectar y autorizar el Samsung para la corrección visual
  final. Se reinstalaron sólo los APK QA, se retiraron del teléfono los XML
  temporales creados en esa sesión y la app pública siguió fuera de alcance.
  El equipo se desconectó después de las capturas finales, antes de poder dejar
  el submenú abierto para inspección posterior.

### Corrección de aceptación posterior

La captura física mostró que el detalle mezclaba información con todas las
acciones de Stage 1/2. Joaquín rechazó esa presentación y fijó un contrato más
simple:

- fecha visible `DD/MM/YYYY`, también en rangos y formularios del recorrido;
- detalle principalmente informativo;
- un único acceso de nivel día: `Editar día`;
- submenú único para jornadas, Franco, Retén, `?`, carpeta médica, situaciones
  de jornada, extras y disponibilidad.

La corrección final precisó esa frontera:

- el detalle muestra `DD/MM/YYYY`, `Editar día` y luego una tarjeta
  `Condición del día` con jornada, Franco, Retén, carpeta médica, vacaciones u
  otras atribuciones efectivamente presentes;
- el submenú titula la fecha como `Lunes 14 de Septiembre del 2026` y ofrece
  exactamente `Agregar jornada o franco`, `Agregar disponibilidad de retén` y
  `Agregar carpeta médica` como altas principales;
- las tarjetas y correcciones existentes aparecen sólo si hay una atribución
  cargada; no existe una plantilla vacía de disponibilidad;
- se retiró `Volver al detalle`; Atrás cierra la hoja y regresa al Calendario;
- la alta explícita de `Sin definir` deja de ocupar el menú principal; una marca
  ya existente conserva su corrección contextual.

La corrección conserva Notas, Avisos y horario real dentro de cada jornada porque
son accesos funcionales explícitos de Stage 1, pero retira del detalle las
mutaciones del día. Compila para QA, AndroidTest afectado compila, JVM app y lint
están verdes. La repetición física final pasó 47/47: Calendario/detalle 8/8,
edición de jornadas 12/12 y carga manual/Franco/Retén 27/27. El recorrido manual
confirmó en el Samsung la fecha `14/09/2026`, el único acceso `Editar día` y el
submenú final. La repetición exacta pasó 47/47 en
`.local-evidence/qa/20260915T022118.398119Z`,
`.local-evidence/qa/20260915T022127.592755Z` y
`.local-evidence/qa/20260915T022136.980928Z`. Las capturas finales son
`.local-evidence/qa/visual-20260915T0223-detail.png` y
`.local-evidence/qa/visual-20260915T0224-menu.png`.

La primera ejecución del tercer grupo quedó 26/27: el día vacío priorizaba el
inspector de jornadas aunque correspondía el flujo contextual unificado. Se
corrigió la decisión por contexto y se repitió la clase completa 27/27; el fallo
permanece en `.local-evidence/qa/20260915T014607.835866Z`.

## REVISIÓN INDEPENDIENTE DE MAIN

MAIN inspeccionó los contratos críticos y no tomó este handoff como prueba por
sí solo. La revisión encontró y corrigió tres huecos antes de elevar el
candidato:

- Copias rechazaba dos carpetas médicas superpuestas aunque el contrato Room y
  la matriz Stage 2 las admiten como registros independientes. El gateway ahora
  conserva esa semántica y la regresión acredita que el estado sigue siendo
  respaldable.
- Una ausencia o cancelación ya guardada no tenía acceso para editar sus
  metadatos sin cambiar de estado. El detalle ofrece ahora `Editar ausencia` o
  `Editar cancelación`, precarga lo existente y explicita cuándo conserva una
  evidencia privada ya guardada.

MAIN también actualizó `tools/qa-groups.json`: el catálogo no enumeraba varias
clases y métodos instrumentados incorporados por Stage 2. `qa.py
--check-catalog` vuelve a declarar `VALID`, con 109 clases y 19 grupos.

Después de las correcciones locales MAIN repitió las 862 pruebas JVM completas con
cero fallos u omitidas, compiló nuevamente el runner QA de app y ejecutó
`lintDebug`; todo quedó verde. La QA física posterior ejecutó 118/118 pruebas
dirigidas y el recorrido manual sintético descripto arriba.

La primera QA física demostró además que una regresión agregada por MAIN había
construido el estado inválido `CANCELLED + horario real`; fue retirada junto con
la conclusión que pretendía acreditar. Otra fixture usaba un horario real
idéntico al planificado y se corrigió para representar una diferencia válida.
Estos fallos no se reinterpretan como pases.

La misma sesión detectó que `@Insert(REPLACE)` podía trasladar silenciosamente
una evidencia privada ya vinculada a otra jornada por efecto de la clave única.
El DAO usa ahora `@Upsert`: actualiza la misma situación, pero rechaza reutilizar
la evidencia entre jornadas. La regresión física final pasó. También se corrigió
una fixture de Copias que pretendía poblar las 29 tablas usando una combinación
de estados imposible; el escenario válido usa jornadas distintas.

## RISKS

- La composición corregida se recorrió en tema oscuro y zoom normal; no constituye
  por sí sola una inspección visual manual exhaustiva de todos los temas y zooms.
- No se probó actualización/restauración con datos personales o paquete público.
- La dependencia `kotlinx-coroutines-test` es test-only y se usa para controlar
  `viewModelScope` en la regresión de diálogo stale.

## PENDING

- Stage 2 fue aceptado por Joaquín el 2026-09-15.
- Higiene opcional al reconectar: retirar paquetes QA y capturas sintéticas del
  teléfono cuando Joaquín ya no necesite inspeccionar el candidato.
- Sólo con autorización posterior: commit/push, firma o distribución. Ninguna
  está implícitamente habilitada.

## DEPENDENCIES

- Base exacta: Stage 1 aceptado, HEAD inicial
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- No depende de red nueva, cuenta, dispositivo, firma ni publicación.

## NEXT

MAIN debe inspeccionar especialmente:

1. transición/CAS y refresco tras conflicto en `RoomShiftSituationRepository` y
   `CalendarViewModel`;
2. migración/esquema/compatibilidad legacy y semántica de Copias V8;
3. compensación, UNIQUE, SHA y reconciliación de evidencia privada;
4. convergencia de clima, próximo evento, avisos, horas e informes.

La revisión fue completada y Joaquín aceptó Stage 2 el 2026-09-15. La aceptación
no autoriza commit, push, firma, publicación ni nuevas pruebas físicas.
