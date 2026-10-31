# Entrega Stage11 a MAIN — Copias Android, R1

Fecha: 23/10/2026. Chat `01a166ec-dd5d-71b3-9401-a4169ffbb0a3`, turno
`01a166ec-e14b-7013-80cb-7dd515a2af3b`. Fase `R1_RESTAURACION_ANDROID`.
Clave de entrega `stage11-restauracion-android-20261023-r1`.
Estado al sellar: `TERMINADO_PENDIENTE_ENVIO`; recepción/auditoría MAIN externas.

## Resultado

Restauración real comprobada en Android para Copias V5/V6/V7 genuinas y V8
con datos actuales no vacíos. Se encontró, reprodujo SIN fault y corrigió un
defecto de ReplaceAll que conservaba bytes viejos si los metadatos de una imagen
eran iguales. MAIN amplió el mismo contrato con R1 antes de editar producto.
Sobre la QA R1: **35/35 métodos Android**, cero fallados/omitidos/pendientes;
41 bases exportadas contrastadas por un auditor SQL independiente, mutante detectado.

Repositorio `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`,
rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
Checkout sucio recibido preservado; staging vacío. La eliminación heredada de
VerticalScrollbarMetricsTest no pertenece al delta. Sin commit/push/tag.

## Implementación y alcance

Sólo dos fuentes productivas, autorizadas por R1:

- `app/src/main/java/com/blackatsystems/miguardia/backup/BackupPhotoFiles.kt`:
  modo explícito por registro para Merge o incoming obligatorio para conjuntos
  completos; verificación SHA del origen y materializado, sin fallback silencioso.
- `app/src/main/java/com/blackatsystems/miguardia/backup/LocalBackupCoordinator.kt`:
  ReplaceAll usa hashes externos de la copia extraída; rollback los del journal
  anterior. Merge, conflictos, OMITTED y cifrado mantienen sus contratos.

Nuevos runner/suite Stage11 bajo `app/src/androidTest/.../backup/`, JVM
`BackupPhotoSourcePolicyTest.kt` y helper/regresiones `tools/release/stage11_restore.py`.
`app/build.gradle.kts` agrega opt-in Stage11 exclusivo con Stage10, sin cambiar
defaults. Catálogo actualizado con la clase/runner propios y metadatos heredados
Stage10 por separado. Auditoría propia:
[restauración Stage11](audits/2026-10-23-stage11-restauracion-de-copias.md).

El runner crea Application exactamente neutra y filesystem/DB/SharedPreferences
UUID aislados. Inventaría y compara todos los archivos durables recibidos;
la limpieza sólo alcanza recursos propios. Conserva adversos ante copia fallida;
su regresión real pasó. No se ejecutó intacta DocumentPublication productiva:
sus casos usan coordinador aislado y FileProvider controlado.

## Evidencia e identidades

Payload: `.local-evidence/stage11/restore-android-20261023/`.
`delivery-manifest.json` y `delivery-seal.json` fijan inventario/sello; no escribir
dentro después. Acuse, revisión y recibos de transporte MAIN van fuera.

- QA original preservada `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
- QA R1 `r1-candidate/Stage11-R1-QA.apk`, SHA
  `494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a`.
- Runner final `compiled-06/Stage11-Restore-Runner.apk`, SHA
  `3db95ad7fcfc8e4d1a2966d4b53409d3e2b4afc5f2029b18dfdd675436225285`.
- Firma técnica común `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
- Bundle matriz R1 `run/r1-restoration-android-evidence.zip`, SHA
  `485fb0aafb7b0ecae62bb1c722ea518f71f94fec6a22e70a8bf40df9b630669d`.

`r1-product-original/` conserva los dos originales y contrato ampliado antes de
editar. `r1-candidate/` conserva fuentes/diffs productivos. `compiled-06/` fija
las fuentes Kotlin del ejecutor real. El helper host tuvo después el ajuste
acotado de caché técnica: sus fuentes finales y pruebas están en `final-sources/`,
sin reatribuirlas a la compilación anterior.

## Matriz comprobada

15 métodos nuevos: exportación V8 con/sin contraseña; Merge repetido; ReplaceAll
con confirmación exacta y canarios; V5/V6/V7 genuinos sobre V8 vacío y actual no
vacío; preview/cancelación; versiones/integridad/truncados/clave; resoluciones
KEEP_CURRENT/USE_BACKUP y KEEP_BOTH válido/rechazo; timeline incompatible;
14 checkpoints de journal; publicación controlada; fuente ausente/SHA alterado;
fotos omitidas y reproducción causal de imágenes ahora verde.

20 complementarios ejecutados nuevamente sobre R1: Recovery 10, PhotoFiles 3,
PhotoMutationGate 1, PortablePreferences 4 y NoteRestoreConcurrency 2.
No se heredan verdes de QA original a R1. Los dos de Stage4 prueban concurrencia
de gateway y son complementarios al restore completo del coordinador.

V5 deriva coordenadas nulas; V6 las conserva; V7 incluye adjunto no vacío; V8
incluye notas/imágenes, cancelación con evidencia, foto, jornadas y relaciones.
Cada oráculo verifica SQL tipado después de cerrar/reabrir, 17 preferencias
DataStore reabiertas y conjuntos completos de imágenes por hash, sin huérfanas.

Pruebas locales: JVM dirigido 2/2, host Stage11 13/13 más delta puntual del pin
de perfil 1/1; lintQa sin errores y diez advertencias. `postexport_audit.py`
recomputa 41 oráculos SQL y detectó una alteración deliberada. Doccheck,
catálogo y diff constan en los recibos finales. No campaña global por rutina.

## Adversos y conservación

QA original: primera corrida 6 pasados/5 fallados; segunda 11/13, donde fallaron
sólo la reproducción mínima SIN fault y journal por el defecto productivo.
Los otros fallos iniciales del ejecutor —preferencias sin resolver, CRLF,
android_metadata, compilación/invocación— permanecen con sus correcciones y
recibos independientes; no se ocultaron ni se informan como verdes históricos.

Antes de arrancar se respaldó FINAL Stage10, no su respaldo PRE recuperación:
32 archivos/3.439.743.848 bytes, comprobados de nuevo al cerrar. La misma R2
API36/x86_64, `emulator-5586`, recibió una actualización QA R1 y tres paquetes
de tests. Ningún reset, wipe, limpieza de datos, desinstalación o semilla nueva.

Primera instalación `2026-10-23 02:03:15` conservada. Stage10 mantiene sus 29
tablas/40 filas y todos los bytes durables/preferencias/foto/marcador. La única
diferencia técnica de instalar R1 fue profileInstalled: 24 B, formato/código/campo
final iguales; timestamp `1792725418882→1792730344218`. El nuevo SHA exacto
`42d57c4f8bddc57b8c89be6f64c77f81c2c25d425fe8748eb354a373856dd261`
queda ligado a QA R1 y al segundo UTC 04:39:04 de su lastUpdateTime.
Dumpsys sólo acredita segundos; no se fingió otra precisión PackageInfo.
Rechazo, diagnóstico y aceptación acotada del anexo permanecen separados; la
entrada original nunca se sustituyó y los demás datos siguen estrictos.

`qa-groups-delta.json` separa metadatos Stage10 heredados: dos hashes anteriores
refrescados y una fuente support ausente agregada, contra sus fuentes finales
ya auditadas. No se modificaron fuentes Stage10 ni grupos/clases anteriores.

## Recursos y próximo dueño

Android y Gradle cesaron. Se retiraron ocho ZIP UUID de caché propios únicamente
después de verificar transporte/hash; copias locales conservadas. R2 apagada,
puertos 5586/5587 libres y procesos propios ausentes. `final-avd-checkpoint.json`
fija 33 archivos del estado final; el alta adicional es tmpAdbCmds técnico del
emulador, conservado. `final-host-resources.json` y recibos run acreditan el cierre.

MAIN debe acusar la clave/fase/sello, rehashear el payload, revisar delta propio,
contrastar adverso original y regresión R1, canarios Stage10 y excepción exacta
del perfil, y actualizar STATUS tras dictamen. No reiniciar R2 ni repetir
baterías para auditar lo que ya está exportado.

No queda un defecto conocido abierto en la capacidad comprobada. Journal usa
interrupción/reinicio simulados, no muerte real de proceso. Selector OEM/SAF
humano, Samsung, app pública, datos personales, rendimiento, aceptación humana,
firma/distribución y publicación permanecen fuera. Esta entrega no acepta la
versión por Joaquín ni reemplaza el dictamen independiente MAIN.
