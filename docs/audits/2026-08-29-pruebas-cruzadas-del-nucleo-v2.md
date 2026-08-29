# Pruebas cruzadas del núcleo V2 — cierre MAIN

- Fecha: 2026-08-29
- Resultado: **CERRADO — VERIFICADO POR MAIN**
- Prompt: `docs/prompts/PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md`
- Rama: `codex/miguardia-2.0`
- HEAD auditado antes del checkpoint:
  `1b697cd3c4db613dd1c3187a9ed0efb8cf4496bf`

## Resultado práctico

Las tres barreras exigidas por la auditoría parcial quedaron implementadas,
auditadas y ejecutadas en su nivel correspondiente. No se reprodujo un defecto
productivo y no fue necesario modificar código de producción.

La dependencia queda cerrada. La segunda capa todavía no se habilita: primero
debe completarse la matriz Android 36/26/33 y repetirse la auditoría integral.

## Alcance auditado

El candidato contiene exactamente tres métodos `@Test` nuevos:

1. `oneDeterministicSnapshotReconcilesEveryCoreProjection`;
2. `concurrentWritersFromOneSnapshotProduceOneWinnerOneConflictAndNoPartialRows`;
3. `calendarSummaryAndTodayCardQueriesLeaveAllApplicationTablesUnchanged`.

Archivos de implementación de pruebas:

- nuevo `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/integration/V2CoreCrossProjectionTest.kt`;
- modificado `core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/V2ShiftPersistenceInstrumentedTest.kt`;
- nuevo `app/src/androidTest/java/com/blackatsystems/miguardia/V2ReadOnlySurfacesInstrumentedTest.kt`.

El diff ejecutable suma 1.102 líneas y no modifica `src/main`, Room, DataStore,
Gradle, manifiesto, permisos, dependencias ni esquemas.

## Auditoría de MAIN

MAIN leyó los tres archivos completos y contrastó sus aserciones con las APIs
productivas. Tres revisiones independientes, de sólo lectura, no encontraron
defectos ni caminos de falso verde.

- La fotografía transversal usa una fixture única con UUID, reloj y zona
  fijos; reconcilia Calendario, Horas, Resumen, disponibilidad, tarjeta,
  próximo evento y avisos sin duplicar fórmulas.
- La carrera usa dos instancias reales de `RoomV2ShiftRepository`, una
  observación compartida y una barrera determinista; exige un éxito, un
  conflicto y ausencia de filas parciales antes y después de reabrir Room.
- La prueba de consultas usa los observadores reales, afirma contenido concreto
  y compara las 27 tablas mediante columnas, valores SQLite tipados, conteos,
  representación canónica, SHA-256 y `PRAGMA data_version`.

## Validación local de MAIN

Comando ejecutado con tareas forzadas:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Resultado: `BUILD SUCCESSFUL` en 17 min 34 s; 351/351 tareas ejecutadas.

- dominio: 303/303;
- database JVM: 12/12;
- app JVM: 184/184;
- total JVM: 499/499;
- fallos, errores y omitidas: 0;
- lint: 0 errores y 6 avisos de actualización fuera del diff;
- APK Debug: 15.619.796 bytes;
- APK QA: 15.504.697 bytes;
- APK Release sin firma: 10.923.855 bytes;
- APK AndroidTest app QA: 1.813.880 bytes;
- APK AndroidTest database: 3.903.981 bytes;
- inventario AndroidTest: 236 casos de app y 108 de database;
- `git diff --check`: limpio.

## Instrumentación Samsung

Joaquin autorizó expresamente el uso del teléfono el 2026-08-29.

- dispositivo: Samsung `SM-S938B`;
- serial: `R5CY529W6PL`;
- API: 36;
- ABI: arm64-v8a;
- paquetes usados: `com.blackatsystems.miguardia.qa`,
  `com.blackatsystems.miguardia.qa.test` y
  `com.blackatsystems.miguardia.core.database.test`;
- datos: exclusivamente ficticios.

Resultados reales del runner:

- carrera CAS aislada: 1/1;
- consulta sin escrituras aislada: 1/1;
- suite Room completa: 108/108;
- regresiones afectadas de Calendario, Resumen y tarjeta: 61/61;
- fallos, errores y omitidas: 0.

La matriz afectada de 61 casos incluyó los observadores reales, Resumen,
Calendario, tarjeta, recreación, tema claro/oscuro, orientación y zoom interno
100/150/200. No se realizó una revisión visual humana separada porque el cambio
agrega únicamente pruebas y no modifica interfaz.

## Room y seguridad

- `MiGuardiaV2Database`: versión 5;
- tablas de aplicación: 27;
- `identityHash`: `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- esquemas 1–5: hashes sin cambios;
- `fallbackToDestructiveMigration`: ausente;
- `allowMainThreadQueries`: ausente;
- cambios productivos, secretos, datos reales o artefactos rastreados: 0.

No se disparó una alarma exacta real, no se reinició el teléfono y no se
consultaron ni modificaron `font_scale`, densidad ni tamaño visual del sistema.
Los tres paquetes QA/de prueba fueron desinstalados. La verificación final no
encontró paquetes `com.blackatsystems.miguardia*` en los usuarios 0 o 10.

## Pendiente posterior

La auditoría integral continúa pausada hasta ejecutar, con autorizaciones
separadas:

1. la matriz completa permitida en Samsung API 36;
2. Room completa y el recorrido esencial en Android 8/API 26;
3. permisos y avisos en Android 13/API 33 exacta;
4. la auditoría integral sobre el único checkpoint resultante.

La alarma exacta real, el reinicio físico, la descarga de una imagen API 33,
API 37, push, tag, Release, `main` y producción siguen siendo puertas
independientes.
