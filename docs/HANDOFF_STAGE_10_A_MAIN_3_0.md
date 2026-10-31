# Stage 10 — preparación local de actualización QA

Entrega fechada 21/10/2026 para MAIN. **Preparación local terminada con gate de
origen y permiso. NO ACTUALIZACIÓN REAL COMPROBADA.** Estado autorizado sólo
en `docs/STATUS.md`, reservado a MAIN. No se inició restauración ni rendimiento.

## Resultado y base

No hay pareja binaria QA V6→V8 acreditada en el checkout. Las 18 APK distintas
de app QA halladas contienen Room V8/29 tablas y code 6, incluidas las antiguas.
Manifests y firmas fueron inspeccionados con SDK; el delegado compilado Room,
idéntico en las 18, fue desensamblado y coincide con el esquema V8. No se eligió
origen por nombre/fecha ni se consultó un dispositivo.

Sí existe una fuente alternativa concreta en ESTE repositorio:
`main` = `3f1365259e15292a2f24e3361acd523213b49fad`. Sus blobs declaran Room V6,
27 tablas, identityHash `7eb39f6fab5a44e69350e206716554be`, variante QA `.qa`,
2.0.2/code 6. Una compilación futura sería un **APK nuevo de ensayo reconstruido
desde esa revisión**, no un APK histórico autenticado. No se construyó ni se
verificó aún su certificado, hash o compatibilidad instalada. No se cambió de
rama ni se creó checkout/worktree. La procedencia histórica descrita por STATUS
no transforma esos fuentes en un binario preservado de la publicación.

Puerta 0: checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream homónimo, origin MiGuardia,
un worktree y staging vacío. Entrada de 994 rutas tras despacho, comparada con
q3: seis documentos de MAIN y contrato nuevo explican todos los deltas. Entrada
acumulada protegida; sin cambios adicionales sin dueño. Chat propio
`01a15d55-500a-7162-be24-f4ed70e990a5`, host local, turno inicial del despacho.

## Entrega

Evidencia propia: `.local-evidence/stage10/preparation/`. Los recibos de
`.local-evidence/stage10/main/` pertenecen a MAIN y están fuera de este sello.

- `checkpoint-01.json` y `entry/repository.json`: identidad, Puerta 0,
  límite literal de escritura, originales y delta explicado.
- `artifacts/matrix.json`: 62 rutas QA/runner, 51 hashes distintos, paquete,
  certificado, versión declarada, runner/target y clasificación. Nueve rutas
  OEM/públicas/debug/release excluidas sin abrir esos APK. Evidencia SDK y DEX
  local adjunta. No se operó el paquete público.
- `source-reference/source-option.json` y sus tres blobs Git: revisión
  V6/variante QA comprobadas, con hashes y límites de reconstrucción futura.
- `fixture-manifest.json`: fixture Kotlin Stage9 intacta, 32 filas en 27 tablas,
  valores/tipos/UUID/FK/timeline, foto sintética y 17 preferencias. Relectura
  exacta de sus BIN y SQLite host; no es una captura Android.
- `reuse-verification.json`: revalidación de 590 insumos, 104 XML/952 JVM,
  seis artefactos, 54 Python y tres probes R1; 1.207 archivos del sello original
  y 30 de R1 intactos. Se reutilizan esas baterías; no se repitieron.
- `permission-and-snapshot-plan.md`: paquete único de operaciones/consecuencias,
  origen faltante, estado QA desconocido, capturas coherentes antes/después,
  comparación estricta y condiciones de corte. **No está listo para autorizar
  ejecución física hasta completar las identidades faltantes.**
- `exit-checks.json`, `payload-manifest.json` y `delivery-seal.json`: validación
  documental, delta propio, hashes y relevo. Transporte separado del payload.

Destino disponible: `stage9/preserved-artifacts/app-qa.apk`, SHA-256
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
Runner disponible: `stage9/preserved-artifacts/app-qa-androidTest.apk`, SHA-256
`40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472`,
`.qa.test` → `.qa`. Ambos: certificado SHA-256
`b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
El runner actual no acredita un seeder/exporter compatible con origen V6 en
Samsung. La fixture histórica AVD conserva todas sus guardas.

## Límites y siguiente decisión

Recomendación: MAIN presenta la reconstrucción aislada de QA desde esa revisión
de ESTE repo como alternativa trazable, o recibe una APK QA V6 preservada con
procedencia. MAIN decide encuadre y guardas antes de construir. La preparación
local rutinaria dentro del alcance no exige otra aprobación humana técnica;
una ampliación material conserva su decisión correspondiente.
Code 6→6 sólo permitiría un reemplazo técnico de ensayo, no certificar una
actualización distribuible con versión creciente. No cambiar versión/firma/appId.

Luego completar APK origen y método exacto de siembra/exportación, comprobar
compatibilidad y obtener una única autorización física con disponibilidad vigente
y tratamiento de TODO QA. No asumir que QA actual es descartable. Cualquier uso
de `clearAllDataForInstrumentation` vaciaría TODO QA; hoy no está autorizado.
Fallo de reemplazo no autoriza downgrade, desinstalación ni limpieza.

La fixture JVM activa Clima/avisos: el plan propone cuatro toggles en `false`
antes del futuro baseline y exige verificar enums/preferencias del origen.
No se aplicó esa propuesta ni se fabricaron preferencias del teléfono.
Se permiten sólo las dos tablas nuevas vacías y la transición de identidad/esquema;
valores, tipos, relaciones y archivos seleccionados deben conservarse.

Implementación productiva: sin cambios. Commit/staging/push/tag: ninguno.
ADB/Samsung/emuladores/instalaciones/Gradle: cero. Docs_check y diff deben quedar
registrados en `exit-checks.json`. Tras el sello cesan escrituras del especialista;
sólo se emite aviso autorizado y recibo de transporte separado. MAIN acusa
RECIBIDO por clave y audita; aceptación de transporte no es recepción ni aceptación.
