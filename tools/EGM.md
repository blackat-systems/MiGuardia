# B1 · programa host, consumidores y conservación

## Overview

Contrato derivado del recorrido residual, con entrada en [EGM raíz](../EGM.md). Los archivos ejecutables descritos están en la unidad local sellada [calendar-residual-operator-r2](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/DELIVERY.json), llamada **R2** en este documento. Las rutas `R2/...` de las tablas se resuelven desde esa unidad; no son archivos de `tools/`. Sus hashes están en `DELIVERY.json`. No se altera esa unidad.

## Interface

| Archivo / símbolo real en R2 | Entrada y salida |
|---|---|
| [operator/run_program.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/run_program.py): `main`, `prepare`, `execute`, `State` | CLI exige exactamente dos posicionales: ruta de admisión y SHA-256 de sus bytes. `prepare` valida identidad/pins/plan/admisión; `execute` consume una salida nueva una vez, actualiza pareja QA y ejecuta secuencia finita |
| [operator/execution-plan.json](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/execution-plan.json) y `operator/admission-template.json` | Siete `steps`, cada uno con `slot`, `selectors: list[str]` singleton, `scopes: list[str]`, `run_uuid: str`, `receipt_relative_path: str`, `runtime_api: int=37`, `calendar_clock_mode: str=REAL`, argv y timeout. Plantilla no equivale a admisión |
| [operator/normal_protocol_residual.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/normal_protocol_residual.py): `validate`, `CalendarResidualFailed` | stdout/exit instrumentación + identidades/selector → protocolo validado o negativa autenticada; exit 0 solo no basta |
| [residual_receipts.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/residual_receipts.py): `FAMILIES`, `SCHEMAS`, `bind_step`, `family`, `validate_receipt`, `validate_failed_receipt` | JSON original + binding exacto → `stage12-calendar-residual-validated-v1` o `stage12-calendar-residual-failed-v1`. Identidad incluye slot, selector, run y hashes QA/runner |
| [consumers/calendar_detail_consumer.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/consumers/calendar_detail_consumer.py) | Recibo C030/C034 `stage12-calendar-detail-v1` → validación de detalle/clima/retorno |
| [consumers/weather_activation_consumer.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/consumers/weather_activation_consumer.py): `exact`, `validate_receipt`, `validate_failed_receipt` | Mismo schema de detalle, slot de activación → validación causal de ocho campos `weather_activation`; COMPLETE lo exige, FAILED temprano puede omitirlo |
| [consumers/calendar_complements_consumer.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/consumers/calendar_complements_consumer.py): `exact`, `validate_receipt`, `validate_failed_receipt` | C074/C071 `stage12-calendar-exceptions-family-v1` → facts, journals, readbacks, consentimiento, diagnóstico y cierre validados con tipos recursivos |
| [consumers/availability_consumer.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/consumers/availability_consumer.py): `validate_receipt` | `stage12-availability-lifecycle-family-v1`, tres slots → validación de historia/revisiones/conflictos. FAILED esperado se reconoce mediante `AVAILABILITY_FAMILY_FAILED`, sin crédito |

Los resúmenes COMPLETE tienen `own_observations_complete: bool=true`, `received_conservation_complete: bool=false`, `ROOM_complete: bool=false`, `full_key_credit: bool=false`, `receipt_canonical_sha256: str` y `family_validation: object`. El hash se calcula sobre el recibo íntegro; no se elimina el campo de Clima para hacerlo aceptable. FAILED conserva identidad/hash y diagnóstico, con observaciones completas falsas.

### Conservación y custodia

| Archivo / símbolo en R2 | Contrato implementado |
|---|---|
| [residual_conservation.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/residual_conservation.py): `SPECS`, `CalendarResidualConservation.__init__`, `transport_identity`, `cohort_sha256` | Cohorte: `program_uuid`, `qa_sha256`, `runner_sha256`, `program_sha256`, `coverage_sha256` y siete steps ordenados. Ocho UUID distintos entre programa y métodos; selectores, scopes y paths exactos |
| `CalendarResidualConservation._group`, `_project`, `baseline` | Dos capturas estables por fase; metadatos y contenido bytes/hash idénticos, roles completos, máximo 100.000 entradas/512 MiB. B0 requiere ausencia de namespaces propios; lectura lógica real incluso si coincide la física |
| `CalendarResidualConservation.case_roots`, `report_roots`, `_exclude` | 40 raíces propias: ocho scopes × base SQLite/sus tres sidecars/directorio privado. Siete raíces de reportes con conjunto exacto de 22 miembros: directorio, owner, recibo por método y PNG sintético adicional C074 de 68 bytes con hash fijo |
| `CalendarResidualConservation.compare_final`, `last_summary` | Una B1 posterior al cierre QA, misma custodia/cohorte. Revalida recibos originales y resumen tipado antes de excluir sólo reportes propios. `excluded_report_roots: int=7`, `excluded_members: int=22`; resultado `COMPLETE`, `DIFFERENT` o `FAILED` |
| [operator/support/received_storage_scope.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/support/received_storage_scope.py): `BASE_ROOTS`, `cleanup_name`, `preflight_bootstrap` | Raíces recibidas: `databases`, `shared_prefs`, `files`, `cache/updates`, dos journals de restauración `no_backup/miguardia_backup_restore` y `.preparing`, más tombstones `.cleanup-<uuid>` admitidos. Recovery/staging/update pendiente impide bootstrap; no se repara ni borra |
| [operator/support/received_custody.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/support/received_custody.py): `PrivateCustody`, `project_capture`, `compare_captures` | Originales materializados y verificados; SQLite/preferencias leídos sobre copia de trabajo. Compara datos SQLite mediante `received_logic.compare_sqlite`, preferencias y archivos/roles; las rutas temporales del lector no son datos de negocio |

La proyección exige `T1_PRODUCT_STORAGE_R1`, `transport_complete=true`, `observed_stability=true`, `atomic_capture_proven=false`, `continuous_process_absence_proven=false`. Estabilidad observada no equivale a captura atómica ni ausencia continua de procesos. `received_conservation_complete` y `ROOM_complete` permanecen falsos en la comparación técnica: la aceptación corresponde a MAIN.

## Invariants

- `strict_json` en `operator/run_program.py` rechaza claves JSON duplicadas y constantes no válidas. `exact` de complementos exige mismo tipo y recorre dict/list: `false` no equivale a `0`, `true` no equivale a `1`, entero no equivale a flotante. `residual_receipts.exact_json` reutiliza ese helper para ligar G al resumen revalidado.
- Activación real: ocho propiedades del productor Kotlin y prefijo causal de cinco readbacks, cuatro facts y eventos completos. Presencia en FAILED tardío no convierte ese resultado en COMPLETE. No inventar crédito de red, ubicación o reproducción humana.
- `_exclude` exige ausentes las 40 raíces de fixture; cada owner coincide con run/QA/runner y el recibo capturado conserva sus bytes originales. Revalidación por consumidor real y hash canónico vinculan su contenido al resumen. Un archivo extraño bajo un reporte propio o una variación recibida no se excluye por conveniencia.
- `execute`: identidad recibida → captura doble PAIR_BEFORE y lectura lógica → actualización QA y runner una vez cada uno → identidad nueva/UID iguales → PAIR_AFTER físicamente igual → B0 actual físico y lógico enlazado → siete métodos → stop QA terminal → B1 → comparación → cierre ADB propio → publicación. No repetir C097/C026 ni usar su excepción técnica.
- `business` detiene en la primera negativa. `read_failed_receipt` permite una sola lectura diagnóstica ante fallo nativo autenticado, conservando causa primaria, sin sucesor ni crédito. Rechazo diagnóstico es secundario; no relaja el validador.

## Side Effects

`Files.claim` consume OUT una vez. La operación física instala sólo QA/runner, escribe reportes propios y evidencia; custodia trabaja con copias privadas y conserva originales. `State.stop_qa` y `State.close_adb` son cierres acotados, independientes y sin retry/restart: ADB se cierra sólo si el arranque nuevo fue atribuido como propio/exclusivo. La incertidumbre de efecto, retorno, identidad o terminación mantiene HOLD aunque cierre después. No implica ausencia global de procesos ni autoriza intervenir otro servidor.

`publish_result` emite `program-result.json` y `closure.json`; al fallar agrega `STOP.json`, y ante fallo de publicación intenta `PUBLICATION-FAILURE.json`. `status=PASSED` y `disposition=EVIDENCE_READY_FOR_MAIN` exigen siete resultados, ninguna causa primaria/secundaria, comparación y cierre completos, pareja final y pins de fuentes verificados. Caso contrario `STOP`, o `HOLD` si cierre incierto; `B1_complete=false`, `round_credit=0` siempre. MAIN decide conservación y cobertura; un exit exitoso aislado no las acepta.

## Verification · regresiones y evidencia

[test_program.py](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/test_program.py), clase `Controls`, bloquea fronteras proceso/red/SQL mediante helpers del arnés estable `availability-lifecycle-operator-r1/operator/test_program.py`. Sus controles relevantes son:

| Frontera | Método de prueba exacto en `Controls` |
|---|---|
| Pareja/pins y orden de updates | `test_effective_prepare_746_actual_pins_and_two_updates`, `test_installs_require_proof_exact_order_and_once` |
| Siete protocolos/consumidores | `test_seven_positive_consumers_and_failed_no_credit`, `test_protocol_seven_positive_negative_and_foreign_failures`, `test_seven_business_calls_use_real_parser_and_consumers` |
| Campo real Clima y FAILED | `test_weather_actual_publisher_wire_and_failed_prefixes`, `test_weather_summary_requires_exact_typed_fields`, `test_activation_rejects_missing_persistence_scope_or_fake_coverage` |
| Tipos/consentimiento/cierre | `test_complements_recursive_json_types_cover_equivalent_fields`, `test_complements_reject_wrong_source_order_consent_and_closure` |
| Exclusión y resumen G | `test_g_exact_40_roots_22_members_and_no_received_exemption` invoca `compare_final` real con lectura lógica doblada y exclusión real; no acredita lectura SQLite física |
| Comparación y contador técnico | `test_real_comparator_rejects_technical_counter_and_ignores_local_path` |
| Primera negativa y HOLD | `test_first_native_negative_allows_one_diagnostic_and_no_successor`, `test_qa_update_first_negative_keeps_single_known_terminal_closure`, `test_qa_update_identity_uncertainty_remains_hold_after_closures` |
| Reutilización del núcleo | `test_parent_capture_and_projection_AST_preserved` |

Comando existente del [recibo de controles](../.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/controls-r1/command-result.json), desde raíz (identificado, no ejecutado por este bootstrap):

```powershell
& 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -E -B .local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/test_program.py
```

El recibo original fija además el ejecutable Python y su SHA; al reproducir evidencia atribuible se reutiliza ese runtime. Los 16 controles están registrados PASS por Stage y MAIN. Los [contraejemplos MAIN corregidos](../.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/MAIN-counterexamples-corrected-harness.json) separan el alias accidental de fixture de los rechazos reales con deepcopy. No se ejecutaron estos controles otra vez en este bootstrap.

## Pendiente y responsable

Las tres correcciones de r1 están implementadas y [aceptadas por MAIN en r2](../.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/final-review.json), sin hallazgos de código restantes. Esa revisión apareció durante el bootstrap, manteniendo los 50 miembros sellados iguales. El bloqueo vigente es de entorno: Stage terminó con `Fatal error: application network permission was revoked`; no atribuirlo a la red del teléfono, a Clima ni a la validación tipada.

MAIN debe recuperar capacidad de red de Codex y el mismo Stage/estado de recursos interrumpidos, reutilizar la aceptación y emitir admisión nueva exacta cuando corresponda. Después Stage opera y MAIN acepta conservación/cobertura. No hay admisión ni salida nativa nueva al corte, ni evidencia para afirmar cese global por ese error. La CLI física usa dos posicionales y llama inmediatamente `execute(prepare(...))`: no tiene modo de comprobación inocua. Se usa sólo el lanzamiento exacto que emita MAIN; la plantilla y este EGM no habilitan su ejecución.
