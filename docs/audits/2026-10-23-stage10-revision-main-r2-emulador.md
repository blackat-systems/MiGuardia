# Stage 10 — auditoría MAIN del ensayo R2

Fecha: 23/10/2026, Argentina. Resultado: **entrega auditada e INCOMPLETA**.
La actualización se ejecutó; la conservación posterior no quedó acreditada.
Estado y autorización únicos: [STATUS](../STATUS.md).

## Entrega y revisión independiente

Mismo chat `01a15d55-500a-7162-be24-f4ed70e990a5`, turno
`01a16676-80ec-7173-9fd5-3dde6ba1d5c7`. HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, rama `codex/miguardia-3.0`.
Staging vacío, un checkout propio y remoto MiGuardia. Entrada modificada preservada.

Payload `.local-evidence/stage10/emulator-rehearsal-r2-20261022/`:

- Sello SHA-256 `de8d5cdd2f26830e1c338fd98f342ad19819f9dc28eaf7bd822d00409edcbc4c`.
- Manifiesto `20ba43aad374ca17ac026462b3112dd36e003374294f38ff6a558555e1f85a72`.
- MAIN rehasheó **917/917 archivos, 109.080.762 bytes**, sin diferencias.
- De 1.019 rutas de entrada cambian exactamente seis fuentes test-only; ninguna
  falta y las seis conservan sus hashes anteriores a la semilla. La revisión de
  preparación cubrió el delta y cuatro regresiones host dirigidas, 4/4. No se
  reatribuyen como pruebas nuevas los 24 JVM/27 host de preparación.
- MAIN comprobó los tres APK locales y rehasheó los streams instalados ADB111 y
  ADB118: ambos contienen QA8 `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
- Antecedentes R1: **578+15 cuerpos** y sus sellos/manifiestos íntegros.
- Revisor independiente: R2 **32/32 archivos, 3.415.626.600 bytes**, hashes y
  metadatos iguales al checkpoint detenido. Primer AVD: conjunto/tamaño/mtime
  32/32 y hashes disponibles 29/29; tres archivos grandes no tenían hash inicial.
  No se afirma igualdad de contenido de esos tres.

Recibos MAIN: `.local-evidence/stage10/main/emulator-rehearsal-r2-review-20261022/`,
incluidos `receipt.json`, `sealed-checks.json`, revisiones de baseline, hipótesis
de cache, arranque y `audit_local_receipts.py`. MAIN y su revisor no operaron
ADB, emulador ni Gradle durante la auditoría.

## Qué quedó comprobado

Escenario `a711836c-78a0-483c-83c2-6143018c7377`: corto 22/10 23:34 AR, largo
24/10 23:04 AR. Baseline a 22/10 23:05:58.909 AR, ambos descartes vigentes,
guía completada. MAIN comprobó el bundle y sus 12 cuerpos; lectura SQLite
independiente, en modo sólo lectura: **Room6, 27 tablas/40 filas**, integridad
`ok`, sin violaciones de claves foráneas. Hay 17 preferencias portables y
onboarding RAW persistido. La captura de Calendario V6 no acredita aceptación
visual ni legibilidad integral del mes.

Diario **199/199 pares** de inicio/resultado, todos dirigidos explícitamente
a `127.0.0.1:5037`, serial `emulator-5586`. Tres instalaciones: QA6, runner y
UN `install -r` QA8, este último a 22/10 23:34:09 AR. Una semilla y una apertura
normal de V6 para completar guía. Sin otra instalación, reset o nueva semilla.

ADB121 rechazó captura V8: `IllegalArgumentException`,
`REJECTED_OR_FAILED_REVIEW_REQUIRED`. El código de salida del proceso ADB fue 0;
el coordinador hizo bien en rechazar el protocolo. No existe bundle V8 válido,
apertura normal de QA8 ni comparación final.

## Bloqueo y límites de atribución

Después de reiniciar el mismo AVD:

1. Usuario 0 `RUNNING_UNLOCKED` y servicio PackageManager disponible. Cuatro
   consultas de ruta para QA vacías; runner presente. No basta atribuirlo al arranque.
2. ADB168/187: QA retenida con `installed=true`, `pkg=null`, fechas y ruta de datos
   registradas. Esto no demuestra que su base de datos sea legible o esté intacta.
3. ADB188: escaneo terminado y `missing scanned package`. ADB189/190: directorio
   registrado y `base.apk` ausentes. ADB191/192: `run-as: unknown package`.
   Revisor independiente confirmó estas observaciones y no halló causa específica
   de eliminación o parseo atribuible a MiGuardia.

Los 135/57 bytes y hashes de `package-scan-diagnostic.json` son mensajes de error,
no APK ni cache extraídos. El handoff y `package-scan-interpretation.json` lo
aclaran. La hipótesis previa sobre timestamp de `profileInstalled` es plausible
por el código y AAR, pero no quedó demostrada: no se recuperó su RAW posterior.
No se relajaron guardas ni se cambió el runner para aprobarla.

No hay prueba de pérdida de la base ni de conservación tras actualizar; tampoco
causa demostrada del rechazo inicial o de la ausencia posterior del código.
Son hechos distintos. Ninguno justifica declarar éxito ni defecto de producto.

## Recursos, criterio y próximo paso

`run/final-resource-release.json` registra sync y apagado confirmado, último
ADB199. MAIN comprobó también ausencia de procesos emulator/qemu y listeners
5586/5587 desde el host. El especialista cesó escrituras y Gradle propios.
Se mantienen originales, fallos y AVD; Samsung no está disponible y no fue usado.

Se acepta **la entrega y su impedimento**, no el cierre de conservación ni de
Stage10 global. El rechazo estricto histórico permanece NO APROBADO; Stage8/9,
Samsung/OEM, restauración, rendimiento, aceptación visual y distribución no se
cierran con este ensayo.

La recuperación mínima quedó primero preparada: copia verificada del AVD
detenido antes de reponer el mismo QA8 conservando sus datos. MAIN revisó la
autoridad de la sesión y resolvió continuar ese paso técnico bajo la autorización
expresa de completar Stage10 y la reiteración «hasta resolver el rechazo y
comprobar la conservación», sin pedir el mismo permiso nuevamente. El
[contrato sucesor](../prompts/REANUDAR_STAGE_10_RECUPERACION_R2_Y_CONSERVACION_3_0.md)
registra la fuente y límites. No había ejecución al cerrar esta auditoría.
La reposición cambia la cronología: aun si permite leer datos conservados, no
aprobaría retroactivamente el ensayo original de una sola actualización.

Controles documentales MAIN: `docs_check` verde, 39.889/40.000 caracteres de
guía, 470 referencias, 42 ADR y 103 snapshots. Se conservó el rechazo previo
por exceso de 93 caracteres y se compactó sólo el cursor. Diff del alcance
sin problemas de espacio; las dos rutas nuevas son esta revisión y el contrato
propuesto. La eliminación heredada de `VerticalScrollbarMetricsTest.kt` sigue
preservada; no es un archivo nuevo ni un borrado de este bloque.
