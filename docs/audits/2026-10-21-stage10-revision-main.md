# Stage 10 — revisión MAIN del origen QA reconstruido

Evidencia del 21/10/2026. No es aceptación de producto ni ensayo Android.

## Recepción y resultado

Productor único `01a15d55-500a-7162-be24-f4ed70e990a5`, turno
`01a15d85-0f27-7cb2-aab6-0fadafde83d9`. Aviso efectivo recibido y acusado
08:27:48 UTC. Clave `stage10-origin-v6-qa-6526a904…23b67`; sello completo en
`.local-evidence/stage10/origin-v6-qa/delivery-seal.json`, acuse en
`.local-evidence/stage10/main/receipts/6526a9045fc9276b15823a89744c385750ba12969e5b2c363d12b3397ed23b67.json`.

**Comprobado localmente:** UN build QA V6 nuevo desde la revisión exacta
`3f1365259e15292a2f24e3361acd523213b49fad`, offline, sin bootstrap del wrapper,
build/configuration cache, claves nuevas ni uso de 2.x. Inicio 08:14:36 y fin
08:18:49 UTC, exit0. La interrupción anterior fue capacidad del modelo, no
Gradle; se recuperó una vez en el mismo chat/modelo, sin build duplicado.

APK nueva de ensayo, NO binario histórico autenticado:

- `.local-evidence/stage10/origin-v6-qa/artifacts/MiGuardia-QA-V6-reconstructed-2.0.2-code6.apk`.
- SHA-256 `1c0d0be8f8f66a2dc65e94a2c8d022836984f210cce8f3b822cb5d555f73148a`.
- `com.blackatsystems.miguardia.qa`, 2.0.2/code6; Room compilado V6/27 tablas,
  identidad `7eb39f6fab5a44e69350e206716554be`.
- SDK verificó ambas firmas QA6/QA8 independientemente: un firmante v2,
  certificado `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
  QA8 conservó `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.

## Auditoría independiente y adversos

MAIN rehasheó los 19 archivos nuevos y los 139+24 anteriores; cotejó los 878
blobs Git, tar y scratch antes/después. Las únicas diferencias frente al blob
son 24 conversiones LF/CRLF no críticas de exportación; Kotlin, Gradle,
esquemas/XML/TOML son iguales byte por byte. El primer rechazo del especialista
por `.ignore` queda preservado; no se editó fuente ni se recompiló para pasarlo.

MAIN ejecutó de nuevo SDK dexdump sobre el DEX vinculado al APK: constructor
Room6, identidad y las 27 tablas coinciden. La comparación inicial de MAIN
rechazó el texto guardado: `write_text` de Windows había convertido CRLF del SDK
en CRCRLF. Se demostró la transformación exacta y se invirtió únicamente para
comparar; hashes RAW y payload originales permanecen intactos. No se ignoraron
líneas o instrucciones. Recibos adversos: tools `504e75` y `6d1efe`.

La revisión interna independiente ejecutó aapt2/apksigner sobre ambos APK:
cuatro salidas 0, hashes antes/después iguales. Esto cierra la dependencia de los
flags `*_matches_qa8` del helper, que aisladamente usaban evidencia previa.

Recibos MAIN: `.local-evidence/stage10/main/origin-audit.json` y
`origin-identity-review.json`; fuentes auxiliares `audit_origin.py` y manifest
sellado. La primera revisión coteja 996 rutas de desarrollo: sólo STATUS y el
contrato tenían cambios MAIN posteriores a la preparación. HEAD/rama/staging
preservados, cero Gradle/Kotlin al relevo. MAIN no repitió build ni baterías.

## Límite y dependencia siguiente

**No comprobado:** instalación compatible, conservación V6→V8 en Android,
restauración, aceptación visual o distribución. Code6→6 es reemplazo técnico QA,
no actualización distribuible creciente. Cero dispositivo/ADB por este bloque.

El origen ya no es el bloqueo. Falta concretar seeder V6 y exportadores V6/V8
que lean 17 preferencias reales y produzcan capturas coherentes. El test AVD
existente no es un seeder Samsung; sus guardas se conservan. MAIN continúa esa
preparación indispensable dentro del mismo Stage10 y contrato, no abre otro
operador. Antes del ensayo se necesita permiso exacto para operaciones/datos QA:
el contenido actual completo NO se supone descartable. Restauración y rendimiento
siguen fuera de este stage. El cursor vigente permanece únicamente en STATUS.
