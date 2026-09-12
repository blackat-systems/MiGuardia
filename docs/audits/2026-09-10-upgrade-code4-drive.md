# Upgrade code4 y origen Drive

Joaquín autorizó expresamente retomar el emulador y utilizar Google Drive el
10/09/2026. Samsung no se modificó durante esta continuación. Entrada Android:
e91e6f7394131d622153d9cd8fdbc857e3accfb3, candidato compartido preservado.

Se reutilizó AVD MiGuardia_Public_Upgrade_API33_20260910, emulator-5582/API33,
con GPU host/Vulkan desactivado (configuración anterior que ya completó ensayos),
sin wipe ni restauración. Red de ensayo desactivada. Origen firmado completo
code2 SHA 5345a2b460b2b16244a7e3ba20035b92c6af857d3f5fb1ff12c3d69a1cdb778f.

Captura inactiva previa con origen y esquema comprobados. Install -r del code4
firmado exacto, Success; reapertura offline de MainActivity y captura inactiva
posterior. Mismo paquete/certificado, código creciente. Comparador productivo:
LOGICAL_SNAPSHOTS_EQUAL: 27 tablas/32 filas, fotos y preferencias/archivos privados
iguales. Sólo se excluyen los tres archivos administrativos Android explícitos
del script, no residuos beta ni preferencias de producto. No se extrae Keystore.

Evidencia en .local-evidence/release/upgrade-api33-20260910/:
capture_code4_upgrade.py, code4-upgrade-before, code4-upgrade-after,
code4-upgrade-comparison.json, code4-photo.png y code4-widget.png.
Lectura UI: calendario con jornadas y disponibilidad; foto ficticia abierta a
tamaño completo; Widget muestra evento 11/09 08:00–16:00 y toque conserva
detalle ID 30000000-0000-4000-8000-000000000021. Imagen de origen conserva su
texto histórico «código 3», sin alterar bytes; el destino realmente instalado es 4.

No se acredita con esta comparación Bloqueo habilitado/Keystore ni todo escenario
de avisos, accesibilidad u OEM. No es descarga→SAF→instalador; instalación fue ADB.
La evidencia complementa Samsung 3→4 y sus 411 pruebas, no las reejecuta.

APK de escritorio MiGuardia-2.0.0.apk es copia del firmado verificado, 12.729.677
bytes, SHA 6fa8b481dc0837a02795c7db1bfe4acc8a606a81ac7586b43043a3bebf81137e.
Joaquín la subió a Drive. Descarga anónima desde file ID
12AKiH2WyTqznnsZBsZtH8vfuutRP1bQZ coincide en tamaño y hash. HTML de advertencia
sin confirmación no se considera APK. Contrato web actualizado por MAIN para
usar origen Drive sin redirigir el actualizador Android. No cambia APK/firma.

Publicación ejecutada: Sites v13 succeeded 2026-09-10T13:40:30.334449Z,
fuente pública 8038dc1341ccf19f8fd5f746eead0b3f6a5d2b83. Dominio
https://blackatsystems.com/miguardia: presentación y endpoint públicos funcionan.
Descarga remota completa SHA/tamaño exactos del firmado. Website conserva
work/drive-remote-verification.json, 174 pruebas web finales y documento
MIGUARDIA_DRIVE_DISTRIBUTION_20260910.md con condiciones operativas y límites.
No implica que el recorrido remoto completo navegador→instalador se haya repetido.
No se afirma seguridad absoluta, preservación de claves por lectura del sandbox,
ni disponibilidad masiva de Drive. Se mantiene el mecanismo de retirada del catálogo.
AVD detenido con datos; no se borran escenarios. Firma permanente y binario code4
intactos. Publicación aprobada por MAIN para el alcance verificado y con estos
residuales explícitos; no habilita ampliaciones de producto ni Google Play.
