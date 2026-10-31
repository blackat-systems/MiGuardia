# Stage 10 — auditoría de preparación local

Fecha: 21/10/2026. Base `932eaeb60c6b6e68975534788e3e6501c967b1ae`, rama
`codex/miguardia-3.0`. Entrega para revisión independiente de MAIN; no constituye
un ensayo Android ni autorización física. Estado operativo sólo en STATUS.

## Hallazgo comprobado: falta origen binario V6

Inventario acotado a este checkout: 62 rutas de APK QA/runners seleccionadas,
51 binarios distintos; 18 apps QA y 33 runners. Nueve rutas de fixtures OEM o
variantes fuera de QA se excluyeron sin abrir sus APK. Se preservaron todos.
`aapt2` acreditó metadatos y `apksigner verify` las firmas de los 51 binarios.
La matriz conserva rutas, hashes, versiones declaradas y runner/target.

Las 18 apps son `.qa`, 2.0.2/code 6, Room V8/29 tablas. Su DEX con la clase
Room es idéntico: SHA-256
`f14faa2ebf85216c3eb302b86bd93a42daaec6d1195d71407b5d48bc0176277d`.
El SDK `dexdump` muestra el constructor `RoomOpenDelegate(8, ...)`, identityHash
V8 `ba1016e34e957457e7bec8ab18a98aad` y los 29 CREATE TABLE esperados. Las
copias viejas no resuelven el origen V6. Evidencia:
`.local-evidence/stage10/preparation/artifacts/matrix.json` y `room-dex/`.

Impacto: instalar cualquiera como origen no demostraría V6→V8. No se reconstruyó
un origen para ocultar la ausencia. La búsqueda no demuestra que no exista una
APK V6 en otra ubicación: sólo acredita el límite inspeccionado.

## Fuente alternativa verificada; binario pendiente

La revisión inicial `3f1365259e15292a2f24e3361acd523213b49fad` de ESTE repo
contiene `MiGuardiaV2Database version=6`, migraciones hasta 5→6, esquema de
27 tablas/identityHash `7eb39f6fab5a44e69350e206716554be`, versión 2.0.2/code 6,
variante QA con `initWith(debug)`/suffix `.qa` y configuración de firma debug.
Tres blobs Git, hashes y metadatos quedaron en `source-reference/`.

Eso permite **proponer** un APK nuevo de ensayo desde una revisión trazable.
No acredita APK histórico, build reproducido, certificado final, instalación
compatible ni autorización para construirlo. No se leyó otro repositorio, cambió
de revisión, creó clave o ajustó appId/versión/firma. MAIN decide esa procedencia
y el límite aislado para una reconstrucción futura antes de implementarla.

## Reutilización y fixture

Se rehashearon 1.207 archivos del sello Stage9 y 30 de R1. Los 590 insumos
local/build siguen iguales; los 104 XML acreditan las 952 JVM previas sin
fallos/errores/omitidas; seis outputs actuales/preservados coinciden. Logs,
insumos y recibos conservan 54 Python y tres probes R1 detectados. Cero suites
repetidas, builds o instrumentaciones nuevas. `reuse-verification.json` registra
este control, sin transformar evidencia host en Android.

Se reutilizaron las definiciones de lectura BIN del bridge Stage9, sin ejecutar
sus efectos ni generar otra fórmula. Los BIN V6/V8 coinciden campo por campo y
tipo con sus SQLite host; se comprobaron identidad, integridad, FK y 32 filas
en las 27 tablas de origen. V8 sólo agrega las dos tablas vacías. El manifiesto
registra foto/bytes/UUID/relaciones y las 17 preferencias, todas sintéticas.

Advertencia concreta de preparación: las preferencias JVM activan Clima/avisos.
No sirven como prueba de valores Android ni se aplicaron. La propuesta física
debe fijar cuatro toggles en falso antes del baseline, validar enums en el origen
y exportar valores reales de ese entorno. El seeder/exporter compatible depende
del APK V6 elegido. No se relajaron guardas AVD ni se ejecutó restore de Copias.

## Validación, adversos y límite de cierre

Dos fallos del script local de verificación quedaron preservados: lectura del
manifest R1 como mapa plano (`TypeError`) y confusión de digest canónico de
snapshot con hash de archivo (`AssertionError`). Se corrigió sólo el script
de evidencia y su ejecución final pasó; no fueron defectos del comparador ni
de datos. El log intermedio permanece en `reuse-verification.log`; la ejecución
correcta, en `reuse-verification-r2.log`.

Puerta 0 tras despacho: 994 rutas y siete deltas documentales explicados desde
q3; staging vacío y base/worktree/remotos correctos. El delta versionable propio
son este informe y el handoff. Evidencia ignorada bajo `stage10/preparation/`
y capturador de entrada propio. STATUS/índices/contratos y `stage10/main/`
conservan su dueño. La pausa breve comunicada por MAIN fue levantada por su
instrucción humana posterior antes de iniciar otra operación; siguió sólo local.

Docs_check/diff y revisión de delta quedan en `exit-checks.json`. No se hicieron
tests nuevos de producto, Kotlin, Gradle, ADB, teléfono, emulador, instalaciones,
borrados, reconstrucción, commits, distribución o red. El paquete único de
operaciones y snapshots conserva explícitamente los campos pendientes y efectos
de borrar TODO QA. No está listo para ejecución física por autorización genérica.

**Preparación entregada; BLOQUEO de origen binario/decisión de reconstrucción y
permiso físico específico. NO ACTUALIZACIÓN REAL COMPROBADA.** El siguiente dueño
es MAIN: acuse por clave, auditoría y decisión material de fuente antes de avanzar.
