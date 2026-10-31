# Stage 12 — cierre local auditado; Android bloqueado

Fecha: 23/10/2026. Mismo chat `01a169e5-d914-7e33-8926-327a354301b3`.
Clave `Stage12:CURRENT_ARTIFACTS:489a4d5445096a87`.

## Dictamen

Aceptado el empaquetado local actual de B01/B02 y la corrección de la etiqueta de
avisos ocultados. **No se cierra Stage12 ni MiGuardia3.0 integralmente.** La matriz
Android sigue0/441, sin dos rondas ni instalación. No hubo uso del Samsung,
app pública, datos personales, firma de distribución, staging, commit o push.

## Comprobación independiente MAIN

- Relevo/final recibidos, turno `01a16ab7-2f54-7093-ac74-56f905b708b8` terminado
  y chat idle antes de la inspección MAIN. Ningún Gradle/ADB/VM concurrente.
- 44/44 miembros, 37.695.874bytes rehasheados, sin diferencias ni archivos extra;
  temporales sin archivos. Manifiesto
  `489a4d5445096a876ac8813d6196c6b7837fab84c634a1cf7d1c23d707b904f7`.
- MAIN rehasheó618 insumos actuales y contrastó los cuatro outputs vivos con su
  archivo sellado. Revisor independiente confirmó entrada/before/after/B02green
  y55/55 fuentes protegidas sin cambios.
- CRC de los tres ZIP válido, sin entradas duplicadas. MAIN volvió a leer los
  APK archivados con aapt2/apksigner, sin compilar: paquetes, versiones, SDK,
  providers, runner, destino y certificado técnico coincidieron.
- QA y Release: versión6/2.0.2, min26/target37. Esto conserva la metadata de
  entrada; no es una actualización pública ni una versión3.0 publicada.
- QA: FileProvider/Startup activos y no exportados. Ejecutor normal
  AndroidJUnitRunner destinado a QA; versiones omitidas, no6/2.0.2 inventadas.
  Certificado técnico QA/test
  `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
- Release carece de bloque de firma y firmasv1; rechazo de apksigner esperado,
  no fallo del empaquetado. No se firmó para distribución.
- Lint:25 advertencias, cero errores/fatales. Una invocación offline exit0;
  cuatro productores y compilaciones Kotlin pertinentes ejecutados. Los329
  registros de log y285 tareas accionables tienen denominadores distintos.
- `command.json` es snapshot inicial; result/CLI/closure prueban finalización.
  El lectorR0 falló con la versión omitida del ejecutor anterior; R1 preserva
  null/vacío y el adverso, sin modificar producto ni repetir Gradle.

## Artefactos recibidos

Raíz `.local-evidence/s12-current-artifacts/artifacts/`.

| Archivo | SHA256 | Alcance |
|---|---|---|
| app-qa.apk | `58a354ca7b79ab0aaa082c66acb534926386cb2cf615c40db76257e63138303d` | Candidata QA normal, no instalada |
| app-qa-androidTest.apk | `43c2231dcdfbb4afc6735fd0daff63d007803a09ef1a6d201a465a6632df8c26` | Ejecutor normal, no arnés aislado admitido |
| app-release-unsigned.apk | `2fcefb105ea9cc9ec7f35674e9802e3847c63707deca252aea6b4694c58a9c47` | Compilación unsigned, no distribución |

Pins generados `ENABLED=false`/QA_SHA vacío; no opt-in ni providers desactivados.
No confundir estos APK con el arnés técnico intermedio de tres métodos.
Los44/44 JVM B02 son ejecuciones anteriores sobre insumos coincidentes; no se
repitieron en este empaquetado. Los995 de L2 siguen siendo evidencia histórica
de aquella base, no una batería nueva sobre este binario. Lint regeneró su
salida pero conserva el SHA anterior; generado no implica contenido distinto.

## Gate y reanudación

G2 host aceptado permanece válido sólo en su alcance; servidor propio detenido.
G3 conserva `G3_PREBOOT_NATIVE_PER_VM_PREVENTION_UNPROVEN`: falta acreditar la
prevención previa de contactos nativos bajo el requisito de MAIN. No se observó
escape de un emulador nunca arrancado ni se demostró imposibilidad universal.
No queda otro bloque local independiente útil ya habilitado tras este cierre.

Para Android hay que resolver explícitamente el entorno/aislamiento o confirmar
una disponibilidad física vigente, sin deducirla de USB ni permisos históricos.
Después quedan la integración/admisión del arnés seguro y bootstrap normal,
canarios/escape/captura propia y la matriz con dos rondas sobre la misma candidata.
No se admite ejecutar el runner normal sobre datos recibidos por inferencia.
OEM, voz/estética humanas, Clima real, R4 histórico, conservación del teléfono,
upgrade público y firma/distribución siguen separados.

MAIN registra el estado bloqueado y retira la supervisión temporal; ambos chats
permanecen abiertos. No hay un bucle de consultas que pueda resolver este gate.
La reanudación corresponde al MISMO Stage12 tras decisión humana/material y
nuevo despacho acotado MAIN, con insumos vigentes y sin repetir entregas válidas.

Recibos MAIN fuera del payload: `.local-evidence/stage12/main/`
`current-artifacts-received.json`, `current-artifacts-offline-inspection.json` y
`current-artifacts-final-review.json`. Antecedentes en la
[revisión L2](2026-10-23-stage12-revision-main-l2.md).
