# B1 · productores Android del residual vigente

## Overview

Contrato técnico derivado de los siete métodos residuales. La [entrada raíz](../../../../../../../../EGM.md) enlaza autoridad y revisiones; el [contrato testSupport](../../../../../../testSupport/java/com/blackatsystems/miguardia/stage12/EGM.md) define admisión/paths; [host Python](../../../../../../../../tools/EGM.md) consume sus JSON y comprueba conservación recibida. Los métodos de esta carpeta son productores de evidencia, no contratos de producto por sí mismos.

## Interface · métodos, entradas y salidas

Paquete común `com.blackatsystems.miguardia.stage12`. Cada fila es una instrumentación singleton, no una clase completa. La correspondencia exacta está también en `SPECS` de `residual_conservation.py` y en `operator/receipt-contract.json` de r2.

| Archivo / método exacto | Slot → schema JSON | Scope propio / observación |
|---|---|---|
| [Stage12CalendarDetailNativeTest.kt](Stage12CalendarDetailNativeTest.kt): `ownedWeatherReturnAndTodayFutureReopenStayStable` | `calendar-detail-weather` → `stage12-calendar-detail-v1` | `c026-minutes`; C030/C034 abrir/cerrar hoy y futuro, ida/vuelta de Clima y estabilidad 1200 ms |
| Mismo archivo: `enablingWeatherFromDayCardPersistsAndReopensWithoutLocation` | `calendar-weather-activation` → mismo schema | `c026-minutes`; activar desde tarjeta, persistir y reabrir sin ubicación |
| [Stage12CalendarExceptionsTest.kt](Stage12CalendarExceptionsTest.kt): `pendingCancellationEvidenceSurvivesPickerCancellation` | `calendar-cancellation-pending` → `stage12-calendar-exceptions-family-v1` | `single-occurrence`, `keep-existing`; C074 conserva imagen pendiente tras cancelar otro resultado |
| Mismo archivo: `medicalNotePrivacyUsesRealWidgetAndReportBoundaries` | `calendar-medical-privacy` → mismo schema | `single-occurrence`; C071 nota canario Room → Widget aplicado e informe XLSX con consentimiento |
| [Stage12AvailabilityLifecycleTest.kt](Stage12AvailabilityLifecycleTest.kt): `configurationHistoryPreservesWindowsAndReferences` | `availability-history` → `stage12-availability-lifecycle-family-v1` | `a119-25h`; historial de etiquetas/referencias sin reescribir ventanas |
| Mismo archivo: `protectedReviewsUseFreshSourcesAndExactBreakdowns` | `availability-reviews` → mismo schema | `a119-days`; revisión fresca, protecciones y desglose de fuentes |
| Mismo archivo: `conflictsErrorsAndConsciousDiscardPreserveTheWholeStore` | `availability-conflicts` → mismo schema | `a119-month`; CAS competidor, errores y descarte consciente, cancelación y reapertura |

Entradas comunes: argumentos de `Stage12NormalQaRunner`, UUID del método, hashes exactos de pareja QA/runner, selector, destino Samsung API37 y reloj runner `REAL` explícito. `main_loan=ABSENT` en el plan residual. Las fixtures reutilizan scopes temporales/recurrentes propios; no vuelven a acreditar C026/A119. El reloj fijo de negocio de detalle (`Stage12CalendarDetailContract.clock`) no cambia el reloj Android.

Salida común: `files/reports/artifacts/stage12-normal-<run_uuid>/<slot>.json`, junto con `owner.json`. `schema/case/status/run_uuid/qa_sha256/runner_sha256` son strings; `runtime_api` es entero 37; `received_state="NOT_CERTIFIED"` y `post_ui_certified=false`. `COMPLETE` requiere evaluación y limpieza propias completas; `FAILED` conserva primera causa, prefijo alcanzado y recursos retenidos. Journals/readbacks/facts/sessions se validan por familia, no con un único parser permisivo.

### Activación Clima → consumidor

`Stage12CalendarDetailNativeTest.checkWeatherActivation` pulsa `weather-enable`, observa explicación, enabled/accepted y segunda apertura UI con el mismo objetivo/día. Después, `runCalendarDetail` cierra owner/auxiliares/Room, reabre el store y llama a `requirePersistedWeatherActivation` antes de publicar el objeto. Esta última función decodifica protobuf y exige exactamente dos preferencias BOOLEAN verdaderas: `enabled` y `provider_explanation_accepted`. Cambia sólo `files/datastore/weather.preferences_pb`, inicialmente vacío; tablas y otros archivos/preferencias quedan iguales en la fixture.

Después de esa comprobación publica este objeto exacto `weather_activation`:

```json
{
  "variant": "OBJECTIVE_WITHOUT_LOCATION",
  "enabled_and_explanation_persisted_after_owner_and_store_reopen": true,
  "exact_boolean_keys": 2,
  "business_tables_equal": true,
  "other_preferences_and_files_equal": true,
  "user_failure_reproduced": false,
  "real_network_or_location_tested": false,
  "full_C081_C084_credit": false
}
```

`variant` es string; `exact_boolean_keys` entero; los seis campos restantes son booleanos JSON. El consumidor r2 exige los ocho campos con esos tipos/valores en COMPLETE y prefijo causal completo: cinco readbacks, cuatro facts y eventos correspondientes. Puede faltar en FAILED temprano si aún no se alcanzó su publicación; si aparece adelantado es inválido. Un FAILED tardío puede conservarlo sin crédito. Ver `weather_activation_consumer.validate_body/validate_receipt/validate_failed_receipt` y la prueba `Controls.test_weather_actual_publisher_wire_and_failed_prefixes` localizadas en [host](../../../../../../../../tools/EGM.md).

La corrección productiva aceptada en [WeatherRuntime.kt](../../../../../../main/java/com/blackatsystems/miguardia/weather/WeatherRuntime.kt), `refreshIfEnabled`, verifica habilitación y ubicación antes de exigir capacidad `weather.network`, conservando la guarda antes de la consulta. La prueba sin ubicación no prueba proveedor/red, ni reproduce el fallo humano. No convertir esta limitación en cierre de C081–C084.

### C074 y C071 → consumidor de complementos

C074 usa `ActivityResultRegistry` propio para entregar URI sintética y después null. En [CalendarDayDetail.kt](../../../../../../main/java/com/blackatsystems/miguardia/ui/CalendarDayDetail.kt) el callback de selección sólo reemplaza `evidenceUri` si el resultado no es null. El test verifica variantes `PENDING_ONLY` y `PENDING_OVER_STORED`: razón/nota/aviso/actor, owner/fecha y URI original se conservan; cancelar no importa ni escribe imagen; confirmar usa la URI original, verifica archivo privado y luego reapertura readonly. No acredita galería externa.

El objeto `calendar_complement` usa `schema="stage12-calendar-complement-v1"`, `kind="C074_PENDING"`, `observations: list` y contadores enteros `owner_closures`, `composition_closures`, `model_jobs_joined`. Secuencia por variante: `PENDING_CANCEL_PRESERVED`, `ORIGINAL_URI_COMMITTED`, `REOPENED_READONLY`. Los facts enlazan `assertion_key`, `level` y `observed={variant: string, external_picker_proven: false, controlled_result: true}`. Niveles: `UI_COMPOSE`, `ACTIVITY_NATIVE`, `ROOM_ISOLATED`, `FILES_ISOLATED`.

C071 usa [Stage12MedicalPrivacyProbe.kt](Stage12MedicalPrivacyProbe.kt): nota sintética persistida en Room, `V2WorkEventSourceObserver` → `projectNextEvent` → `projectWidget` → `WidgetRemoteViewsRenderer.createViews(...).apply(...)`. Comprueba jornada/lugar como controles positivos y ausencia de canario en todos los TextView, incluidos ocultos y contentDescription. Inspecciona XML de XLSX reales: nota ausente por defecto, cancelación conserva artefacto, segundo consentimiento consciente incluye nota y sesión nueva restablece privacidad.

Facts C071 en `UI_COMPOSE/ACTIVITY_NATIVE/ROOM_ISOLATED`: `widget_remote_views_applied=true`, `report_format="XLSX"`, `explicit_second_consent=true`, `external_export_proven=false`, `installed_widget_proven=false`. Es una vista aplicada en Activity propia y XLSX local, no launcher instalado, PDF, SAF o compartir. El consumidor r2 compara recursivamente tipos/valores; sustituir esos booleanos por 0/1 debe fallar.

### Disponibilidad → consumidor de familia

Los tres métodos usan ventanas y fuentes propias con sesiones/cierres/reaperturas. Historia conserva referencias históricas; revisiones incluye mixed/medical/vacation/prior-owner/future; conflictos comprueba competidor y corrección exacta, errores observación/cálculo y descarte consciente. Sus facts usan `key/check/own_property_only`, distintos de `assertion_key/level/observed` de complementos. `availability_consumer.validate_receipt` conserva su formato; no homogeneizarlo por estilo ni inferir nueva cobertura A119.

## Invariants

- `Stage12RuntimeTarget.requireSelection` exige singleton Samsung SM-S938B, API37, arm64-v8a, reloj REAL explícito y sin Summary para estas familias. La admisión técnica local no concede permiso físico.
- Los datos sintéticos se aíslan mediante `Stage12TemporalStore` y `LocalDataStore.create(context, paths.name)` con `Stage12RecurringPaths` y paths propios de run/scope. Las observaciones completas son de esos stores; nunca una afirmación automática de igualdad de datos recibidos.
- Cierre: owner/Activity y auxiliares antes de Room/reapertura/limpieza. Se esperan ViewModelStore vacío, composición cerrada, jobs unidos y recursos propios cerrados (incluidos PendingIntents de C071). Se valida ausencia de bases/sidecars/árbol privado al terminar correctamente. Ante fallo se retienen recursos y primera causa para diagnóstico; no limpiar por inferencia.
- `Stage12NativeReceiptFiles` publica una sola vez con `CREATE_NEW`; el host relee bytes originales y después los revalida antes de excluir reportes propios. Las observaciones, diagnósticos y campos de límite de cobertura son parte del wire, no adornos.

## Side Effects

Creación/edición de fixtures Room y archivos privados sintéticos, preferencias Clima aisladas, Activity/Compose y jobs propios, RemoteViews aplicadas y XLSX locales. Se produce PNG C074 en su reporte. La limpieza correcta borra sólo raíces propias verificadas; el cierre de la operación QA/ADB y la comparación recibida pertenecen al host/Stage. No hay permiso para tocar producción ni ajustes globales Android.

## Verification

Estos siete métodos son la prueba Android focal preparada, aún sin ejecución nueva al corte. La [revisión compilada aceptada](../../../../../../../../.local-evidence/stage12-1/main/calendar-residual-candidate-r1-review/final-review.json) distingue compilación, 42 JVM reutilizados y pendientes físicos. Sus once archivos Kotlin siguen coincidiendo con los hashes de la entrega estable al inicio/cierre de la lectura independiente; Disponibilidad coincide con el mapa de fuentes aceptado. No se compilaron ni probaron de nuevo aquí.

Pruebas JVM asociadas, en el [contrato testSupport](../../../../../../testSupport/java/com/blackatsystems/miguardia/stage12/EGM.md): admisión de singleton, paths/publicación, oráculos/readbacks y regresión Disponibilidad. Pruebas Python del wire y casos adversos: [host](../../../../../../../../tools/EGM.md).

La [entrada raíz](../../../../../../../../EGM.md) da la invocación con runtime explícito de `tools/verify.py --profile local --fresh-tests`; usa Gradle y queda reservado a Stage, no ejecutado por este bootstrap. El alias `python` de esta sesión no resuelve un runtime utilizable. La instrumentación se ejecuta sólo con el plan/lanzamiento exacto admitido por MAIN, no como batería de toda la clase.

## Pendiente y responsable

MAIN ya aceptó r2 localmente; su revisión final registra un bloqueo de capacidad de red de Codex, no un fallo Android. La [entrada raíz](../../../../../../../../EGM.md) localiza ese sucesor aparecido durante la lectura. MAIN recupera entorno/mismo Stage/recursos interrumpidos y decide admisión exacta nueva; Stage ejecuta después el recorrido residual y conservación. Si un método/lector/identidad/publicación falla, no hay sucesor automático. La guarda de Clima está implementada; reproducción humana/red/ubicación y pasos OEM siguen pendientes en sus niveles. C096 exige muerte real del proceso, no recreación de Activity. No hay nuevo defecto productivo comprobado por esta lectura documental.
