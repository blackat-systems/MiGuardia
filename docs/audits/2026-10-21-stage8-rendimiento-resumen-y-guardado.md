# Stage 8 — diagnóstico de Resumen y guardado

Fecha local: 2026-10-21. Contrato:
[Stage 8](../prompts/STAGE_8_RENDIMIENTO_RESUMEN_Y_GUARDADO_3_0.md).
Estado autorizado sólo en [STATUS](../STATUS.md).

## Resultado y límite causal

Se entrega un diagnóstico Android adicional para observar las dos operaciones,
sin cambiar producción ni corregir una causa todavía no demostrada. Las alertas
stress R4 quedaron pendientes de medición física en la entrega local inicial.
En ese cierre, el diagnóstico estaba compilado y aún no había producido trazas
Android; no hubo ADB, emulador, instalación ni consulta de datos del teléfono.
Las reanudaciones posteriores q1/HOLD, q2 física y q3 local se registran al final.
q3 cierra el análisis de estas copias con causa diferencial R4 **INCONCLUSA**;
localiza costos actuales y deja un único experimento propuesto, sin ejecutarlo.

## Entrada y atribución

Checkout `MiGuardiaContainer/MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream original, `.git` propio,
un worktree, staging vacío. Entrada propia: `.local-evidence/stage8/entry/`:
978 rutas, originales y SHA-256. Contra el sello de estructura de 20/10 sólo
cambian STATUS, índice de prompts y contrato Stage 8, como anunció MAIN.
La eliminación previa con hash nulo permanece. No se atribuye el diff acumulado
contra HEAD a este bloque. Sin ramas, staging, commits, push ni distribución.

## Hechos verificados y siguientes observaciones

| Frontera | Evidencia de código/recibo | Qué falta observar |
|---|---|---|
| Navegación/activación | `MiGuardiaApp.kt:778–795` ya controla visibilidad y START/STOP; `SummaryViewModel.kt:215–224` cancela al salir | Tiempo de abrir menú, seleccionar destino y sincronizar Compose; no agregar otro lifecycle |
| Carga de Resumen | `SummaryViewModel.kt:458–514` observa historial completo por sector; reconstruye fuentes por fecha ante emisiones | Pilas de lectura/mapeo Room, cantidad de emisiones y espera de fuentes en Android |
| Proyección | `SummaryViewModel.kt:463–479` calcula en `map`, sin dispatcher explícito; `MonthlySummary.kt:241–275` recorre historial para mes y períodos | Costo síncrono y contexto efectivo Android; no atribuir delta R4 al costo histórico por sí solo |
| Composición | `SummaryScreen.kt:372–430` mide importe, chevrón y palabras por fila con `rememberTextMeasurer` | Pilas de medición/composición y aciertos de caché; cantidad de filas depende de métricas, no directamente del total de jornadas |
| Preparación de guardado | `V2ManualShiftLoadViewModel.kt:728,1180,1260,1400` revalida selección, configuración, catálogo y expectativas | Tiempo de preparación/cola antes de transacción; conserva confirmaciones y huella |
| Transacción/asociados | `RoomV2ShiftRepository.kt:160–165,251–460`; `ManualLoadNotes.kt` conserva promoción/limpieza y callback transaccional | Pilas de validación global, escritura, archivos y esperas; muestreo no da duración SQL exacta |
| Propagación | `V2ManualShiftLoadViewModel.kt:764–789` publica éxito/IDLE y persiste estado; driver espera luego Compose/foco | Intervalos observables de invocar, esperar IDLE/error y sincronización final |

La ruta simple de guardado contiene al menos cinco auditorías globales propias:
catálogo, lectura de jornadas de la ventana, expectativas de eliminación incluso
vacías y validación anterior/posterior del lote. `V2LocalDataIntegrity.kt:37`
valida el conjunto, incluyendo jornadas e instantáneas. Son costos sustentados
por código, **no la causa probada de la diferencia entre productos**. No se
retira ninguna auditoría ni se cambia CAS/transacción por esa observación.

El detector guarda antes de abrir Resumen y recrea Activity en cada iteración.
No sustenta que la proyección activa de Resumen explique el guardado. Tampoco
hay evidencia para culpar exclusivamente a dibujo, SQL o letra Android.

### Recibos históricos conservados

Análisis nuevo, sin ejecutar Android:
`.local-evidence/stage8/diagnosis/historical-stress-analysis.json` conserva
hashes de cuatro recibos originales, las nueve series completas y percentiles
nearest-rank. Para n=12, p95 es el máximo; no se recortan muestras.

| Pareja stress / producto | Guardado p50 / p95 ms | Iteración del máximo | Resumen p50 / p95 ms |
|---|---|---|---|
| 1 baseline | 515,744 / 622,479 | 1 | 873,705 / 909,098 |
| 1 R4 | 506,092 / 622,573 | 1 | 940,728 / 1148,688 |
| 2 baseline | 540,125 / 681,755 | 1 | 1095,832 / 1563,865 |
| 2 R4 | 555,633 / 947,453 | 1 | 1422,559 / 1636,221 |

La primera iteración explica el máximo de guardado en ambas parejas/productos;
no demuestra una causa de calentamiento ni habilita descartarla. La auditoría
[R4](2026-10-11-stage7-qa-main.md) conserva alertas y condiciones originales.
El manifiesto `performance/baseline-stress-20261012T211614.871821Z.json`, bajo
la evidencia Stage 7, identifica la baseline completa:
`6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`.
No se usó ni instaló ese APK en Stage 8.

## Diagnóstico adicional y conservación

`Stage8BoundaryDiagnosticsTest.kt` es un harness AndroidTest separado. Se mantiene
intacto el detector original `Stage4FlowsActivityTest.kt`, SHA-256
`3331ede6d038657939629a5a9a9bb86f33d246b2314cb8a144f160d5b7f00ad7`.
La duplicación test-only permite agregar observación sin cambiar el detector
pareado histórico ni introducir hooks de producción. El sello compara cuerpos
de fixture y auxiliares compartidos; no crea una fórmula alternativa del producto.

Conserva representative 240/24/365 y stress 1920/192/2920
(jornadas/notas/días), 12 iteraciones, nueve operaciones, tres jornadas nuevas
por iteración, IDs y fechas del fixture original. No hay PRNG: semilla lógica
por IDs estables y fórmula de fecha, anclada al mes local registrado. El reloj
productivo sigue siendo real. Comparar sólo corridas con iguales insumos,
ancla, hash lógico, ventana, letra, tema y preferencias.

Produce `files/stage8/<run>/diagnostic-<dataset>.json` y 24 trazas ART
por corrida completa (dos operaciones × 12). El sampler usa 1 ms y buffer 8 MiB;
se detiene en `finally`. Los checkpoints monotónicos separan apertura del menú,
selección, idle, espera de proyección, invocación de guardado, espera IDLE/error
y sincronización final. Las pilas del sampler ayudan a distinguir preparación,
Room/validación, proyección y composición. Son muestreo estadístico y esperas
observadas, no duraciones exclusivas sumables ni duración exacta de SQL.

Las trazas y las capturas sintéticas de base adicionales perturban scheduling
y cachés. El cronómetro excluye las llamadas de inicio/parada del sampler;
incluye su costo mientras está activo y el de los checkpoints.
`diagnostic_samples_ms` y `diagnostic_only` impiden confundir su salida
con `samples_ms` del detector. No se comparan estos tiempos con R4/baseline ni
se usan para superar un umbral. Los límites de frame heredados tampoco prueban
FPS/jank. Archivos únicos, fallos y corridas incompletas se conservan; overflow,
traza ausente o fallos del sampler invalidan la atribución. Los métodos no
serializan argumentos ni contenido laboral.

Regresiones adicionales del harness: tamaño total `initial + 3 × iteración`,
tres jornadas en cada selección, IDLE sin error y snapshot de base igual antes
y después de consultar Resumen. Ese snapshot cubre base, no preferencias ni
archivos. Las regresiones funcionales JVM existentes se ejecutan frescas; las
aserciones Android nuevas quedan pendientes de ejecución física.

No se implementan cachés ni filtros temporales. La equivalencia futura deberá
conservar horario real que muda de mes, semanas/ciclos completos, solapamientos
con disponibilidad cuyo dueño esté fuera del mes, cruces/zonas, clasificaciones,
notas/adjuntos, reintentos, concurrencia y cancelación sin escrituras (ADR 0023,
0028, 0030, 0031 y 0043). No cambian Room/esquemas, Copias, fuentes, reglas,
dependencias, permisos, versión ni firma de distribución.

## Validación local y revisión

Primera batería local fresca: 935/935 (395 dominio, 19 base y 521 app), cero
fallos/errores/omitidas/reutilizadas, `changed_inputs: []`;
`validation/local/result.json`, bajo la evidencia Stage 8. Cambiaron después
sólo controles del harness Android.

**JVM definitiva PASÓ:** `jvm/result.json`, nuevamente 935/935 frescas
(395 + 19 + 521), cero fallos/errores/omitidas/reutilizadas;
`changed_inputs: []`. Su mapa de insumos coincide íntegramente con el del build
definitivo. No se suman las repeticiones como pruebas distintas.

La corrida final en `validation/local-final/` terminó Gradle verde, pero el
recolector falló al copiar un XML largo de base: la primera ruta llegaba a 256
caracteres y el sufijo `-final` excedía el límite Windows observado. El runner
la marca `preflight_failed` aunque el log termina `BUILD SUCCESSFUL`; no se
presenta ese recibo incompleto como verde. Se preserva y se repite con salida
corta `.local-evidence/stage8/jvm/`, sin cambiar herramientas ni activar opciones
globales de Windows.

El primer build falló por `PropertyEscape` en el `local.properties` privado
recuperado: el original preservado tiene 48 bytes y cero barras ASCII 92.
El primer ajuste preservado tiene 49 bytes y una barra, sin cambiar el SDK.
`local.properties.after`, `local.properties.double-escape-adverse` y
`local.properties.final` son idénticos, SHA-256
`6011a2683478fe0fc208ca99ef99486607259b10223e50a8c90d5ff01467b8c0`.
No existe entre estos originales un estado de dos barras ni una segunda
modificación del contenido demostrada. Se retira la atribución anterior de
doble escape: fue una afirmación incorrecta de Stage 8.

MAIN detectó la contradicción y Stage 8 la contrastó por bytes/hash, también
mediante un revisor interno de sólo lectura. El nombre
`local.properties.double-escape-adverse` y la causa declarada por
`environment/syntax-fix-final.json` están mal rotulados. Este último declara
`before_sha256 == after_sha256`: no acredita una nueva corrección. Ambos se
conservan intactos como evidencia adversa, al igual que todos los originales,
fallos y sellos previos. La rectificación que sustituye esa atribución está en
`environment/attribution-correction-r1.json`; originales adicionales de los
documentos y recibos en `corrections/20261021-env-attribution-r1/originals/`.

En el segundo fallo, `build-r2/build/gradle.log` registra análisis y reporte
lint `UP-TO-DATE`; su XML es idéntico al del primer fallo (SHA-256
`8059cde21486ae8970935020e512547afc67c1a23f183bc5113f6cbe8ed9ba5a`),
con la línea original sin barra. Se reutilizó ese reporte; el fallo no demuestra
una entrada con dos barras. Los recibos de ambos fallos permanecen en
`validation/build/` y `build-r2/`. La corrección R1 sólo rectifica documentación
y atribución de evidencia: no modifica `local.properties`, producto ni herramientas,
y no ejecuta Gradle/JVM ni repite baterías.

El análisis/reporte dirigido posterior pasó (cero errores, 24 advertencias), recibo
`validation/lint-r3.json`. El perfil siguiente recuperó nueve análisis lint
`FROM-CACHE` y volvió a publicar el hallazgo anterior, con el archivo ya correcto;
se conserva en `validation/build-final/`. Por esa inconsistencia comprobada se
justificó `--rebuild` en un directorio nuevo, con propiedades JVM de sesión
`org.gradle.caching=false` y `org.gradle.configuration-cache=false` mediante
`JAVA_TOOL_OPTIONS`. No se borraron cachés ni se cambió `gradle.properties`.

**Build definitivo PASÓ:** `validation/build-fresh/result.json`, 335 tareas
ejecutadas, `changed_inputs: []`; lint sin errores y con 24 advertencias.
Debug/QA/Release unsigned y AndroidTest app/base generados. Seis artefactos
preservados y rehasheados en `artifacts/manifest.json`, sin instalación:

| Artefacto | SHA-256 |
|---|---|
| QA | `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3` |
| Runner app QA | `7eb513f3bece83fa330dac34e8a884c0223d90f18575764574a32f6fea1a8acc` |
| Runner base Debug | `57131c2581c2c2142cc8d1b732d3d5c44e4afff1ad2f09de022dd18980415b31` |

Son identidades nuevas de build local, no hashes instalados. Los APK Stage 7 R4
y baseline Stage 5 permanecen idénticos a sus manifiestos anteriores, cotejo
`review/preserved-artifacts.json`.

Herramientas del repo: 75/75 tests stdlib. Inspector ignorado: 38/38, repetidos
por el escritor principal; `diagnosis/inspector-tests-root.txt`. Pruebas y CLI
usan estructuras sintéticas de parser, sin registros binarios ART reales.
El adverso inicial de identidad `lstat/fstat` Windows queda en
`diagnosis/inspector-tests.txt`: resuelto usando identidad/volumen/tamaño/mtime,
sin asumir igualdad de ctime entre APIs. Windows denegó crear enlaces nativos
(WinError 1314); el rechazo symlink/reparse se ejercitó con metadatos simulados.
No se presenta como ensayo nativo de enlaces.

La revisión independiente detectó y permitió corregir el bloqueo de proceso
ante fallo de inicio/parada de ART, brackets temporales y preservación ante fallo
de siembra/hash. Revisión estática final sin hallazgos materiales remanentes.
La ejecución física y la alerta R4 conservan su puerta.

Controles finales: `python tools/docs_check.py`,
`python tools/qa.py --check-catalog` y `git diff --check`; recibo
`review/final-checks.json`. Catálogo: 122 clases, 29 grupos. Delta propio:
cuatro archivos versionables, separado contra la entrada en
`review/stage8-own.patch` y `review/delta-summary.json`; más herramientas/tests
y evidencia ignorados, y sintaxis privada de entorno documentada. Sello inicial
conservado: `exit/repository.json`. Corrección R1: nuevo sello y relevo en
`exit-r1-attribution/`, con delta exclusivamente documental contra ese sello
inicial. No hay cambios productivos ni eliminaciones nuevas. MAIN conserva
STATUS/contrato/índice e integración. Al entregar el relevo R1 cesaron nuevamente
las escrituras; Stage 8 no ejecutó Gradle/JVM durante esta corrección. Corresponde
continuar la revisión independiente de MAIN.

## Plan físico conservado de la entrega local inicial

1. MAIN obtiene autorización específica de Joaquín para una corrida física
   Stage 8 en QA descartable y verifica artefactos
   locales/instalados por hash, disponibilidad, foco/privacidad y paquete público
   preservado. Registrar API, ventana, fontScale y densidad; no cambiar ajustes
   globales. No usar el runner Stage 7 para el harness nuevo.
2. Revisar el plan `python tools/qa.py --group stage8-boundaries`. Con autorización
   real, ejecutar el mismo comando agregando `--execute --authorized-device-use
   --serial <serial-autorizado> --allow-qa-mutations --allow-disposable-data`.
   Cada selector se ejecuta en proceso nuevo; detenerse ante fallo/foco perdido.
3. Conservar recibo del runner y copiar binariamente sólo `files/stage8/<run>`
   de QA al directorio nuevo de evidencia del host, con hashes. No usar redirección
   textual de PowerShell para `.trace`. Revisar inicio/parada, completitud,
   overflow de la cabecera ART y metadatos antes de interpretar pilas.
   Ejecutar el inspector local sobre cada JSON copiado:
   `python .local-evidence/stage8/inspect_diagnostic.py <run>/diagnostic-stress.json
   --output <directorio-nuevo>/inspection.json` (análogamente representative).
   El manifiesto conserva hashes, rechazos y límites; no valida registros binarios
   ART ni certifica rendimiento. No sobrescribir recibos o manifiestos previos.
4. Inspeccionar trazas en un visor ART compatible, usando checkpoints/brackets
   como límites observables, sin asumir alineación exacta entre relojes. Buscar
   pilas `SummaryObserver`, `calculateMonthlySummary`, `SummaryAmountLine`,
   `prepareMutationForCurrentState`, `requireValidV2LocalData`,
   `applyV2Batch`, `ManualLoadNotes`, Room y sincronización Compose/driver.
   Ausencia en muestreo no prueba costo cero. Si una traza es inválida,
   conservarla y justificar otra corrida completa sin tocar el detector.
5. Sólo si la evidencia identifica una causa reproducible, corregirla dentro
   del contrato con regresiones de equivalencia. Repetir detector E2E original
   pareado sobre insumos equivalentes, manteniendo 12×9, crecimiento, p50/p95
   nearest-rank y umbrales originales. La salida instrumentada no cierra alerta.

No hay aceptación estética, fluidez global, instalación, QA nueva, actualización
desde 2.0.2 ni distribución acreditadas por esta entrega.

## Reanudación Samsung 21/10 — checkpoint por HOLD de disponibilidad

MAIN comunicó un HOLD posterior al despacho: no instalar, ensayar ni consultar
nuevamente el teléfono hasta aviso vigente. El permiso de alcance QA se conserva,
pero la disponibilidad anterior no habilita el uso actual. No había operación
ADB de este chat en curso al recibirlo. Se detuvo el avance antes de instalar o
ejecutar selectores; no se intentó limpiar, desinstalar ni cambiar ajustes.

**VERIFICADO en host:** entrada de 993 rutas, staging vacío y 590 insumos
idénticos al build seleccionado Stage 9; QA `2e00ab…758a3` y runner actual
`40a24a…15472`, rehasheados. `aapt2` local confirmó paquetes QA/QA.test y
`AndroidJUnitRunner` dirigido exclusivamente a QA. La selección actual no
sobrescribe la identidad histórica del runner `7eb513…8acc` de la entrega local.
El plan `stage8-boundaries` conserva representative/stress y 12×9 operaciones;
no se agregó `--execute`. No se repitió Gradle, build ni pruebas JVM.

**Única observación física de esta reanudación:** probe de metadatos a las
05:26:27 UTC, previo a recibir aquí el mensaje de HOLD. Identificó Samsung
SM-S938B/API 36, foco Inicio y metadatos de desbloqueo en ese instante. Los
hashes instalados seguían siendo QA R4 `99e32e…dc29` y runner `bc31f0…3c3c`.
MAIN informó una restricción de las 05:26 UTC: el probe cae en esa misma
minuta y se conserva esa coincidencia temporal; no acredita disponibilidad
actual ni resuelve por sí solo la autorización temporal. No se volvió a consultar
el teléfono después de recibir el HOLD. No hubo entradas UI, capturas ni consulta
de la app pública o de contenido laboral.

**NO EJECUTADO:** instalación QA/runner, instrumentación, reemplazo de datos
ficticios, extracción binaria o inspector sobre trazas físicas nuevas. No hay
JSON de diagnóstico ni trazas ART nuevos y no se demostró la causa de la alerta.
El último foco/estado registrado pertenece al probe; no se declara un estado
actual comprobado durante el HOLD ni se abre QA para fabricar un cierre.

Evidencia nueva preservada en `.local-evidence/stage8/qa/q1/`: `entry.json`,
`selection.json`, `plan.json`, `apk-metadata.json`, probe completo,
`hold-checkpoint.json`, originales de ambos documentos y recibos de cierre.
La revisión interna de sólo lectura del helper `ops.py` encontró un límite de
recibos: el error ADB no cero se lanza antes de conservar `Failure[...]`, y un
timeout no registra el intento. Corregir y comprobar offline antes de una futura
operación; no se ejecuta/reintenta para demostrarlo. Las guardas `assert` requieren
Python sin optimización, y la colección debe identificar explícitamente los runs
de la campaña entre todos los directorios Stage 8. El helper no acredita pruebas
físicas ni sustituye el permiso de disponibilidad.

**PENDIENTE / NEXT para MAIN:** levantar explícitamente el HOLD con confirmación
humana vigente; revalidar identidad/foco y hashes en ese momento; subsanar el
recibo local antes de la instalación; ejecutar el contrato completo una sola vez
si siguen dadas sus condiciones. El visor/decoder ART compatible todavía debe
seleccionarse: el inspector estructural no interpreta sus registros binarios.
No proponer una optimización ni cerrar rendimiento con esta preparación.
STATUS, workflow, contrato e índices quedan a cargo de MAIN. Se entrega relevo
estable de las rutas propias, sin cambios productivos, Git staging/commit ni
operación nueva del dispositivo; detalle y sello en `q1/writer-release.json`.


## Reanudación q2 — evidencia física del 21/10

HOLD y q1 se preservan como antecedente. MAIN comunicó disponibilidad humana
posterior (05:30:02 UTC) y, tras validar su ajuste documental, continuó al mismo
operador. Puerta 0 q2: mismo HEAD/rama, staging vacío y 993 entradas preservadas;
590 insumos Android idénticos al build Stage 9. No hubo reconstrucción, Gradle ni
JVM. QA y runner seleccionados conservan sus SHA completos en `q2/selection.json`.
`aapt2` local confirma QA/QA.test y AndroidJUnitRunner dirigido sólo a QA.

### Preparación e instalación

El helper anterior quedó intacto en q1 y como copia original en q2. Se corrigió
sólo la conservación de intentos/fallos/timeouts y transporte parcial, con rechazo
de Python optimizado antes de operar. 13 tests offline con dobles verdes,
repetidos por root; no son tests físicos ni del parser ART. Se preserva un error
local de consulta de la estructura del recibo Stage 9 en `source-check.json`:
la comprobación correcta usó `inputs_after.android.inputs_sha256`, 590 sin delta.

Nuevo probe confirmó Samsung SM-S938B/API 36, serial autorizado, Inicio y
metadatos de desbloqueo. Instalación QA y runner `-r` exitosa, sin clear,
desinstalación ni permisos añadidos. Hashes instalados iguales a QA
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`
y runner `40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472`.
El manifiesto del runner oficial también confirmó identidad instalada/local y
metadatos de paquetes sin cambios durante el ensayo.

### Ejecución, extracción e integridad

`tools/qa.py --group stage8-boundaries --execute --authorized-device-use
--serial <SERIAL_QA_AUTORIZADO> --adb <SDK>/platform-tools/adb.exe --allow-qa-mutations
--allow-disposable-data`, timeout soportado por defecto sin modificación.
Inicio 05:40:42 UTC, fin 05:42:44 UTC: **2 pasados, 0 fallos, 0 omitidos y 0
pendientes**, procesos aislados representative/stress. Registro exacto de comando
en `q2/qa-start.json`; original `.local-evidence/qa/20261021T054042.236535Z/`
y copia completa en `q2/runner-receipt/`. Sin otra corrida física ni detector
original pareado. El runner Stage 9 seleccionado contiene sus tests nuevos,
pero no se ejecutó conservación/upgrade Stage 9 en este ensayo.

Los dos métodos completaron 12×9, con 240→276 y 1920→1956 jornadas,
respectivamente; fixtures relativas a fecha y hashes iniciales reales en sus
JSON. Sustituyeron solamente datos/preferencias ficticios QA con permiso vigente.
Se verificaron el conteo guardado, tres fechas y consulta de Resumen sin cambiar
el snapshot de base. No acredita QA visual/accesibilidad/actualización integral.

Transporte `exec-out/run-as QA/tar` binario: **35.361.792 bytes**, SHA-256
`bae28f835d28c73f635e055a55d3509dab843b95b5fe7c3618b463d17d665cff`.
50 archivos (48 trazas y dos JSON), hashes de entrada/copia iguales y rehasheados
por root después del análisis. Runs: representative `1687026245448`, stress
`1732833667618`; no se mezclaron corridas históricas. No se extrajo la base QA,
contenido personal, app pública ni otra aplicación. No hubo capturas de teléfono.

Ambos inspectores estructurales devolvieron completos 12×9 y 24 trazas por run,
sin overflow. Metadatos observados estables: 1440×3120, densidad 600 y fontScale
0,899999976; no se cambiaron ajustes globales. El harness mantuvo LIGHT/portrait
y la preferencia heredada QA prevista; no es una comprobación de tema vigente.

Control binario independiente acotado a v3 dual no streaming: 48/48 consistentes,
**1.153.890 registros**, contadores/símbolos/acciones válidos y sin retrocesos por
hilo observados. Root verificó directamente hash/cabecera/alineación/conteo de
las 48 copias. El primer validador exigió incorrectamente prefijo `0x`: falso
rechazo del ID cero, corregido con respaldo del formato. El resultado/script
adversos se preservan íntegros y rehasheados como cápsulas en `art-validation.json`;
no se alteró una traza para aprobar. Esto no es un profiler general ni atribuye
costos; el indicador de cabecera `is_precise_trace` no reemplaza la procedencia
real: API de muestreo solicitada cada 1000 µs.

### Observaciones y límites causales

Métricas **diagnósticas instrumentadas**, ms, nearest-rank 12 muestras; p95=max.
No evaluar umbrales E2E con esta tabla ni comparar contra Stage 5/R4.

| Fixture / operación | p50 | p95 / máximo |
|---|---:|---:|
| Representative / Resumen | 464,986 | 857,704 |
| Representative / guardar tres | 662,240 | 1617,227 |
| Stress / Resumen | 667,388 | 921,594 |
| Stress / guardar tres | 881,831 | 1615,331 |

El máximo de guardado es iteración cero en ambas fixtures y se conserva. En
representative, `save_wait_idle_or_error` dura 336,757 ms y el checkpoint final
1277,600 ms; en stress, 662,563 y 949,951 ms, respectivamente. El final ocurre
después de observar IDLE sin error (`Stage8BoundaryDiagnosticsTest.kt:251–257`,
`163–169`). Preparación/aplicación preceden la publicación de éxito en
`V2ManualShiftLoadViewModel.kt:728–771`. **El total de 1,6 s no es una duración
de transacción SQL**; sigue pendiente atribuir internamente la espera posterior.
Las fronteras incluyen asincronía/driver y no son costos exclusivos sumables.

Cinco copias se observaron con Perfetto UI v58.3-11fbaed83, motor WASM local,
sin usar Share. [Formato ART admitido](https://perfetto.dev/docs/getting-started/other-formats#art-method-tracing-format)
y [procesamiento local](https://perfetto.dev/docs/getting-started/system-tracing#viewing-your-first-trace).
Se guardaron SQL, tablas renderizadas, AX y capturas sólo del visor sintético.
Las extensiones `.tsv` originales contienen celdas renderizadas separadas por
saltos de línea; `physical-analysis.json` las normaliza explícitamente.

Stress 0/11 guardado: error `slice_max_depth_exceeded` 16/9, preservado. El
contador de anidación sintética llega a 528; es compatible con esa pérdida del
importador, no prueba pila completa ni causa de producto. Stress 0/11 Resumen y
representative 0 Resumen no reportaron errores de importación, pero conservan
slices abiertos al límite de la traza. No convertirlos en llamadas exactas.

Se observó `requireValidV2LocalData` en hilos `arch_disk_io_*` de ambas
operaciones. El código confirma mecanismo compartido: `observeAll` audita dentro
de transacción (`RoomV2ShiftRepository.kt:73–85`), el lote audita antes/después
(`:258,460`) y la auditoría carga/valida el historial completo
(`V2LocalDataIntegrity.kt:113–136`). Esto sustenta investigar trabajo repetido;
no permite contar auditorías completas desde slices de corrutinas ni sumar su
costo entre métodos/hilos. `calculateMonthlySummary` se observó en `main` en
Resumen inicial (representative 9,006 y stress 13,507 ms inclusivos muestreados);
`SummaryAmountLine`, representative 2,225 ms. No prueban explicación de la espera
total. Una ausencia de muestreo no significa costo cero.

Adversos del visor preservados/excluidos: dos archivos etiquetados
representative que aún mostraban stress 11; resultados R1/R2 leídos antes de
completarse SQL; extracción estructurada inicial vacía por asumir tags HTML
`tr/td`. Representative válido es sólo `viewer-representative-0-summary-r3-*`,
con identidad verificada en visor limpio y observación separada tras ejecutar.
El visor nuevo tuvo un fallo de carga inicial recuperado con un único reload;
no se limpiaron cachés ni se cambiaron ajustes. Las correcciones de atribución
están en los recibos `viewer-*-adverse.json`, `viewer-final-attribution.json` y
`physical-analysis.json`; originales intactos. Ambos tabs temporales cerrados.

**PENDIENTE:** no se alinearon exactamente relojes ART/harness ni se reconstruyó
la relación caller/parent de la propagación. No hay causa exclusiva del delta R4
ni corrección productiva justificada por este bloque. Próxima observación mínima:
usar estas copias/código para localizar auditorías y propagación en las ventanas
posteriores al guardado, delimitando la pérdida de profundidad; no repetir el
ensayo físico por rutina. Luego MAIN podrá decidir un contrato mínimo preservando
atomicidad/CAS y detección histórica de datos inválidos. La verificación E2E
posterior exige detector original pareado, sin tracing ni cambios de umbrales.

El cierre local sufrió una lectura implícita cp1252 de la auditoría UTF-8.
Se preservó la versión adversa, se verificó la inversión exacta de esa
transformación y se recuperó UTF-8 explícito; recibo `encoding-correction.json`.
No alteró trazas, recibos físicos ni código. Los controles documentales y el
delta se repitieron después de esta corrección.

### Relevo q2

La última consulta del recolector observó Inicio desbloqueado, QA/runner con los
hashes seleccionados y sin proceso de ensayo en curso. Runner forzó cierre QA;
quedó fixture stress ficticia, no se limpió el teléfono para el cierre. No hubo
más ADB desde la colección ni consulta ajena. Se liberan dispositivo y Gradle.
Sólo handoff/auditoría propios cambian contra entrada q2; evidencia local nueva
bajo `q2/`, q1 y Stage 9 preservados. MAIN conserva STATUS/contrato/workflow e
integración. Controles, delta y sello final en `q2/writer-release.json`;
cesan las escrituras al emitirlo. Sin commit, firma, actualización pública,
distribución o aceptación Vigilia/R4. El handoff final entrega este bloque y sus
límites a la supervisión existente de MAIN.

## Continuación q3 — pilas completas, fases y límite diferencial

Alcance humano vigente: análisis local de trazas existentes; sin repetir QA física
ni cambiar producto. Entrada q3: misma rama/HEAD, 993 rutas de Git inventariadas
(una eliminación previa), staging vacío, un worktree. Se releen sólo contratos y
fuentes pertinentes. q2 y sus 149 archivos sellados son entradas inmutables;
q3 agrega evidencia ignorada y modifica exclusivamente handoff/auditoría propios.
MAIN conserva STATUS, contrato, workflow, índices y revisión/integración.

### Método y comprobaciones locales

`q3/stack_analysis.py` reconstruye ART v3 dual desde las copias originales, sin
el límite 512 del importador anterior. Resultado `stack-build.json` y SQLite
local: **48/48**, 1.153.890 registros, 585.526 segmentos de pila, 568.364 completos
y 17.162 abiertos naturalmente al terminar; máximo 528 frames. Cero símbolos,
acciones, orden de salida o tiempos inconsistentes. No se inventan extremos ni
se resincronizan pilas ante errores. Pasaron 18 fixtures; root los repitió,
comprobó SQLite y extremos/padres/símbolos por consulta independiente.

`clock-alignment-r1.json` conserva referencias primarias AOSP y brackets start/stop
del harness. Los 48 orígenes ART observados caben en la llamada de inicio y
`end_time` en la de fin; ningún registro cae fuera del elapsed declarado. Esto
es compatible con relojes relacionados, **no prueba identidad ni deriva Samsung**.
Se usa explícitamente un modelo afín positivo condicionado a esos brackets y
cuantización de microsegundos. Incertidumbre máxima observada por evento:
**60,950180 ms**. Pasaron 14 guardas matemáticas y 192 comprobaciones root de
extremos/mínimos/máximos; no son tests de implementación del fabricante.

`phase_analysis.py` atribuye cada diferencia CPU/wall entre registros consecutivos
del mismo hilo al contexto activo **antes** de la acción actual. Es una partición
muestreada, no suma de inclusivos ni duración SQL exacta. Sólo asigna fase si
ambas envolventes temporales completas caben en la misma frontera; el resto es
AMBIGUOUS. No fuerza coincidencia de CPU y wall ni suma wall de distintos hilos
como latencia. 42.053 intervalos tienen peso; 1.110.084 tienen ambos deltas cero.
Se preservan **167 intervalos CPU>wall** tal como están, sin ajustar relojes.

Canónico `phase-analysis-r2.json`, SHA-256
`640e562168119393dd4f5a20515d7269e2c17255bc10175902b8fb5cacbe76e9`.
18 fixtures de partición verdes, repetidos por root. Root decodificó de nuevo los
binarios y recalculó envolventes con fracciones: **1.753 controles por hilo y
2.080 grupos fase/hilo coinciden**, además de hashes de las 48 entradas. Recibos:
`stack-review-root.json`, `clock-review-root-r1.json`, `phase-review-root-r1.json`,
`stack-tests-root.txt` y `phase-tests-root-r2.txt`. Los controles de herramientas
no acreditan QA nueva.

### Trabajo localizado y consumidores

Ejemplos stress, iteración **cero y once**, de las 12 preservadas. CPU muestreada
en ms asignada condicionalmente al checkpoint `final_compose_idle_and_focus`,
posterior a observar IDLE sin error:

| Observación | Guardado 0 | Guardado 11 |
|---|---:|---:|
| Duración del checkpoint, reloj harness | 949,951 | 216,333 |
| CPU particionada `main` | 822,666 | 140,091 |
| Contexto Compose restante, dentro de `main` | 394,646 | 67,907 |
| Celdas/grilla Calendario, dentro de `main` | 205,475 | 24,334 |
| De ellas, descendientes de texto/layout | 86,776 | 11,663 |
| Transformación Hours `sourceFlow`, dentro de `main` | 60,065 | sin muestra asignada |
| Integridad, CPU agregada de hilos de fondo | 555,078 | 105,806 |

Filas incluidas en `main` no se suman otra vez al total; fondo no se suma a main
como E2E. No son costos exclusivos reales ni una descomposición exhaustiva de
la espera. Una categoría ausente no demuestra costo cero. La fuerte diferencia
inicial/final pertenece a esta corrida instrumentada; no se extrapola al detector.

En guardado 0, integridad posterior se observa bajo `RoomV2ShiftRepository.observeAll`
(210,721 ms de CPU muestreada), catálogo (82,747), reales (75,516),
`RoomShiftRepository.observeHasAny` (72,210), extras (66,274) y jornadas por inicio
(47,610). Son contextos de continuaciones/Flow, **no cantidades de auditorías**.
El código corrobora la auditoría completa dentro de observaciones y del lote,
sin demostrar qué invalidación inició cada emisión ni el costo SQL aislado.

`captureDeletionExpectations` es otra ruta: el único consumidor productivo hallado
es `V2ManualShiftLoadViewModel.buildPreparedMutation` (`:1332,1400`), llamado desde
`save` antes de aplicar el lote/publicar IDLE (`:728–771`). El repositorio valida
globalmente incluso con IDs vacíos (`RoomV2ShiftRepository.kt:160–165`). Sus
intervalos clasificados quedan en `save_wait_idle_or_error`, no en el checkpoint
final. La pila larga observada no justifica atribuirle la propagación posterior.

Horas y Extras inicia su observación en `init` y colecta en `viewModelScope`
(`HoursAndExtrasViewModel.kt:256–258,995–1001`), sin depender de su pantalla visible.
Su `sourceFlow` combina historial, catálogo, extras, reales y protecciones, mapea
jornadas/calcula progreso y tiene pulso por minuto (`:1079–1153`). `MiGuardiaApp`
recoge el estado globalmente (`:516`). Calendario consume extras para indicadores,
errores y detalle (`CalendarScreen.kt:197–199,319–339,356`;
`MiGuardiaApp.kt:1556–1563`). Hay consumidores reales: no se propone apagar la
observación completa por estar cerrada su pantalla. Tampoco se ha demostrado que
todo el cálculo de progreso sea necesario en cada emisión.

En abrir Resumen stress 0, la partición de toda la traza asigna 11,974 ms CPU a
`calculateMonthlySummary` y 9,803 a texto/layout de `SummaryAmountLine`, frente a
285,118 en otro Compose y 66,585 en texto genérico; hay también trabajo de
navegación y observadores. El origen de cada costo sigue siendo muestreado, no
un benchmark por método. Resumen ya cancela correctamente por visibilidad/lifecycle;
no hay base para agregar otro control ni para reducir toda la demora al cálculo.

### Por qué no se demuestra todavía el delta R4

`source-comparison.json` coteja 12 archivos actuales con manifiestos R4 y Stage 5.
Los 12 coinciden con las fuentes R4. Ocho archivos de lógica —Calendario, Horas,
carga manual, ViewModel Resumen, dos repositorios, integridad y cálculo mensual—
también coinciden con snapshot Stage 5. Difieren MainActivity, MiGuardiaApp,
CalendarScreen y SummaryScreen. **Igualdad de fuente/snapshot no certifica por sí
sola la correspondencia de todo el APK baseline**. Los costos heredados pueden
interactuar con wiring nuevo; no se descartan ni se atribuyen a R4 por su presencia.

Cuatro manifests/recibos originales verifican cronología **ABAB** con mismo hash
inicial de fixture, pantalla/densidad/fontScale, tema y driver. A=baseline QA
`6ae70d…5b1b3`, B=R4 QA `99e32e…dc29`; no hubo contrabalanceo BA.

| Corrida histórica stress | Resumen p50 / p95, ms | Guardar p50 / p95, ms |
|---|---:|---:|
| A1 | 873,705 / 909,098 | 515,744 / 622,479 |
| B1 | 940,728 / 1148,688 | 506,092 / 622,573 |
| A2 | 1095,832 / 1563,865 | 540,125 / 681,755 |
| B2 | 1422,559 / 1636,221 | 555,633 / 947,453 |

Resumen se degrada también de A1 a A2. Eso expone variabilidad/orden no aislados;
no prueba su causa ni elimina la alerta B. No hay ART baseline comparable y q2
añade tracing. No comparar sus tiempos diagnósticos con esos E2E ni achacar toda
la diferencia a fuente, SQL, driver, calentamiento o crecimiento del fixture.

Adversos preservados: root asumió inicialmente AABB sin vincular manifests;
`historical-context.json` queda excluido, corregido por
`historical-context-r1.json` con hashes/fase/APK. El primer clasificador omitía
caller `captureDeletionExpectations`; revisión root lo detectó. v1 y originales
se conservan, R1 agrega matcher y fixture. Cambian sólo callers, no pesos de
fase/rol/categoría de las 48 trazas. No se cambia una entrada para lograr verde.

Antes del sello, MAIN detectó un defecto real en la cota matemática: +999 ns
suponía granularidad entera de ns anterior al truncado µs, no demostrada para un
contador escalado. Se usan cotas cerradas +1000 ns en numerador superior y
denominador inferior, conservando evento ≤ fin. Dos regresiones con `Fraction`
prueban exclusiones anteriores y contención nueva, sin floats. Root las comprobó
por separado y repitió los extremos y la partición. `clock-bound-fix.json` y
`phase-bound-regen.json` registran regeneración y comparación completa: **no cambia
ninguna atribución ni peso de estas 48 trazas**, aunque el máximo de incertidumbre
aumenta 1 ns. Esto corrige validez del modelo; no prueba reloj Samsung. Los
reportes previos y scripts originales quedan como antecedentes, no canónicos.

### Un único experimento siguiente, propuesto y no ejecutado

**Hipótesis:** el exceso R4 incluye más CPU de `main` en medición/composición del
Calendario/Resumen durante navegación y propagación, por encima de costos
compartidos del historial. La alternativa observable es costo comparable de UI,
variación por orden u otra ruta dominante; q3 no decide entre ellas.

**Insumos/control:** APK baseline
`6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`, R4
`99e32ed7525b23ac5452d8eebab019f7051c2f7e6e50dc47db9a2e918dd4dc29` y runner del detector original
`bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
Un experimento pareado y contrabalanceado ABBA en QA ficticia stress, mismo equipo,
fixture inicial `e99b2e1a…4240`, calendario/mes, tema, densidad/fontScale y condiciones
registradas, 12×9, sin cambiar muestras/umbrales ni quitar la primera. Un brazo
E2E sin tracing y otro diagnóstico separado con checkpoints equivalentes en ambos
APK. Candidato a runner diagnóstico preservado:
`40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472`.
Primero verificar compatibilidad de ambos APK con ese runner y disponibilidad
de artefactos; si falla, volver a MAIN con evidencia, sin modificar producto.

**Resultado decisorio:** diferencia R4 E2E que persiste al invertir orden, junto
con más CPU muestreada de UI en las mismas fases y sin explicación suficiente por
bordes ambiguos, favorecería la hipótesis. Desaparición/inversión al contrabalancear,
UI comparable o aumento concentrado en otra ruta la refutaría o dejaría inconclusa.
Una asociación favorable aún requiere intervención acotada posterior para demostrar
causalidad exclusiva; no autoriza retirar integridad ni cambiar producto ahora.

**Permiso/owner:** hoy está prohibido repetir QA. MAIN debe revisar estos resultados
y obtener nueva autorización humana específica para dispositivo, instalación de
QA/runner y reemplazo de fixture ficticia. Este documento prepara el experimento;
no concede permiso, no lo agenda y no lo ejecuta.

### Relevo local q3

Canónico de conclusiones y referencias: `q3/causal-analysis-r1.json`; reconstrucción,
modelo y pesos en los recibos anteriores. Control documental, diff propio contra
originales q3, 993 rutas de entrada y sello completo q2 se comprueban al entregar
`q3/writer-release.json`. No se escribe STATUS, contrato, workflow o índices.
No hubo ADB, emulador, instalación, nuevo AndroidTest, Gradle/JVM/build, cambio
productivo, commit, push o distribución en q3. Las ejecuciones q2 no se reetiquetan
como nuevas. El estado actual del teléfono no se consultó.

**Cierre de la unidad local: INCONCLUSO para causa diferencial R4.** MAIN recibe
handoff, límites y próximo experimento concreto; audita/registra y decide siguiente
contrato. Cesan escrituras y recursos de este chat al emitir el sello q3.
