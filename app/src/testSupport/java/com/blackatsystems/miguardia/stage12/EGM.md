# B1 · contratos compartidos de pruebas

## Overview

Este componente comparte código Kotlin entre JVM `test` y `androidTest`, mediante los dos `sourceSets` de [app/build.gradle.kts](../../../../../../../build.gradle.kts). Fija identidad, selectores, reloj de fixture y publicación; no implementa admisión física ni aceptación de B1. Ver [entrada raíz](../../../../../../../../EGM.md), [productores Android](../../../../../../androidTest/java/com/blackatsystems/miguardia/stage12/EGM.md) y [consumidores host](../../../../../../../../tools/EGM.md).

## Interface

| Archivo / símbolo | Entrada → salida / estado |
|---|---|
| [Stage12CalendarDetailContract.kt](Stage12CalendarDetailContract.kt): `SELECTOR`, `SLOT`, `ACTIVATION_SELECTOR`, `ACTIVATION_SLOT`, `SCHEMA`, `keys`, `activationKeys` | Correspondencia exacta de los dos métodos de detalle a slots/schemas, diez claves C030/C034 y cuatro C081 por nivel UI/Activity |
| Mismo archivo: `clock`, `scope`, `today`, `future`, `reference` | `Clock.fixed(2026-10-23T10:00:00Z, Stage12TemporalLiteralOracle.zone)`, fechas 23/24 octubre, scope SAME_DAY; `FIXTURE_ROLE="CALENDAR_ONLY"`, mínimo estable 1200 ms y deadline 30000 ms |
| [Stage12B1Selectors.kt](Stage12B1Selectors.kt): `calendarPrivacyComplements`, `availabilityLifecycleFamily` | Sets exactos de métodos C071/C074 y tres Disponibilidad; usados por la admisión del runner, no por búsqueda de nombres aproximada |
| [Stage12RuntimeTarget.kt](Stage12RuntimeTarget.kt): `Target`, `identify` | API/modelo/manufacturer/build/fingerprint/ABI → `SAMSUNG`, `AOSP_STAGE12` o `SAMSUNG_TEMPORAL_API37`, o rechazo. API37 sólo Samsung SM-S938B arm64-v8a |
| Mismo archivo: `requireSelection` | target, `selectors: List<String>`, `clock: String`, `hasSummaryObservation: Boolean`, `clockExplicit: Boolean` → Unit o excepción; familias residuales requieren singleton, API37 y REAL explícito sin Summary |
| [Stage12NativeReceiptFiles.kt](Stage12NativeReceiptFiles.kt): `prepareOutput` | `filesDir: File`, `runId: String` UUID canónico, `identity: String` no vacía ≤4096 caracteres, `name: String` de lista cerrada → File nuevo bajo `reports/artifacts/stage12-normal-<runId>/<name>.json` |
| Mismo archivo: `publish` | `output: File`, `content: String`, `primaryFailure: Throwable?` → escritura UTF-8 por único handle `CREATE_NEW/WRITE`, o excepción. Si publicación falla con causa previa, conserva esa primaria y agrega fallo suprimido |

`prepareOutput` recibe la identidad ya serializada; no parsea ni certifica hashes QA/runner por sí mismo. Si la raíz existe, exige `owner.json` regular, ≤4096 bytes y texto idéntico a identity; si es nueva, crea owner. El host exige después el objeto JSON exacto `{run_uuid, qa_sha256, runner_sha256}`. Esta separación evita atribuir a la utilidad de archivos una validación semántica que pertenece al consumidor.

La lista cerrada incluye los siete slots residuales. `Stage12PathPolicy` resuelve bajo base confiable; se rechazan symlinks de los segmentos y owner, recibos previos y links colgantes. Publicación no reemplaza un recibo anterior. No documentar ese mecanismo como reemplazo atómico: se reclama un archivo nuevo y escribe una sola vez.

## Invariants

- Los contratos compartidos se compilan en `test` y `androidTest`; no son dependencias runtime de producción. [GenerateStage12Pins](../../../../../../../build.gradle.kts) genera `Stage12BuildPins` sólo para APK test con hash QA de 64 hex cuando runner está habilitado. Cambiar UUID de run no requiere recompilar QA.
- `Stage12NormalQaRunner` en androidTest identifica el destino y consume estos selectores. API37 real no se presenta como AOSP ni como permiso de uso. El plan físico agrega pins, serial, actor exclusivo, número de intentos y autorización que estos helpers no conceden.
- Runner REAL y reloj de negocio fijo de la fixture cumplen responsabilidades distintas; no cambiar ajustes Android para hacer coincidir fechas. La prueba de detalle es CALENDAR_ONLY y no reabre crédito de C026.
- Las fixtures y su readback usan contratos de scopes, tablas, instantáneas y paths propios. No mezclar limpieza de datos sintéticos con conservación de datos recibidos; el host exige ausencia final de esas raíces y comparación separada.

## Side Effects

`Stage12CalendarDetailContract` y `Stage12RuntimeTarget` calculan/validan sin recursos Android. `Stage12NativeReceiptFiles` crea directorios/owner y escribe recibos en disco de QA; no borra, no instala y no transmite. La operación puede dejar un recibo parcial si falla su escritura: debe mantenerse como evidencia negativa, nunca reintentarse sobrescribiéndolo.

## Verification

Pruebas JVM pertinentes, código localizado y leído/contrastado; no ejecutado por este bootstrap:

| Fuente de prueba | Contrato que comprueba |
|---|---|
| [Stage12CalendarDetailAdmissionTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12CalendarDetailAdmissionTest.kt): `reviewedSingletonRequiresSamsungApi37AndExplicitRealClock`, `onlyTheExactCalendarSlotPublishesUnderItsOwnRun` | Rechaza destino/reloj/selector equivocados, duplicados y alias de slot; publicación sólo bajo run propio y rechazo de recibo repetido |
| [Stage12CalendarWeatherAdmissionTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12CalendarWeatherAdmissionTest.kt): `calendarPrivacyComplementsRejectMixedSelectionsAndOtherDevices`, `weatherActivationAndOriginalDetailRequireIndependentExactSingletons` | C071/C074 y activación son singleton independientes, sin mezcla/duplicados/Summary ni reloj implícito/fijo |
| [Stage12RuntimeTargetTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12RuntimeTargetTest.kt) | Identidad/selección de destinos y familias admitidas |
| [Stage12NativeReceiptFilesTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12NativeReceiptFilesTest.kt) | Namespace, owner y rechazo de archivos/enlaces previos; conservación de fallo primario de publicación |
| [Stage12RecurringReadbackOracleTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12RecurringReadbackOracleTest.kt), [Stage12CalendarExceptionOracleTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12CalendarExceptionOracleTest.kt) | Contrastes y oráculos de fixture/readbacks de recurrencias/situaciones reutilizados por complementos |
| [Stage12AvailabilityReviewRegressionTest.kt](../../../../../../test/java/com/blackatsystems/miguardia/stage12/Stage12AvailabilityReviewRegressionTest.kt) | Minutos del candidato, protección de owner previo, fuente fresca y competidor alrededor de revisión |

La [entrada raíz](../../../../../../../../EGM.md) da el comando existente con runtime explícito de `tools/verify.py --profile local --fresh-tests`. Incluye Gradle; no se ejecuta como parte de este bootstrap. Las 42 JVM de soporte reutilizadas por la candidata son evidencia de sus insumos originales, no una nueva ejecución ni prueba del cambio posterior de Clima. El wire Kotlin→Python además se prueba en los 16 controles del [host](../../../../../../../../tools/EGM.md), en particular campo real/tipos/FAILED, sin Android.

## Pendiente y responsable

No se requiere modificar estos contratos para las tres correcciones host r2, ya aceptadas por MAIN. El bloqueo de entorno y la recuperación del mismo Stage están localizados en la [entrada raíz](../../../../../../../../EGM.md); esa aceptación no equivale a admisión física. Stage conserva implementación y validación de los productores; MAIN revisa identidades/entregas y admite el ensayo. Si productor y consumidor divergen, localizar el campo aquí/en el productor y la prueba host correspondiente; corregir el defecto en su dueño sin cambiar producto, descartar campos reales ni relajar límites para obtener un verde.
