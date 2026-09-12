# MAIN — cierre correctivo y destino 2.0.0/code4

Fecha: 2026-09-10. Bloque autorizado: continuación integral de seguridad,
congelación, firma y QA. Este registro no acredita publicación ni instalación.

## Aceptación técnica local

MAIN retomó el handoff `.local-evidence/security-fix/20260910-corrective/HANDOFF_A_MAIN.md`
y su procedimiento `QA_Y_RELEASE.md`. No reimplementó la retirada beta.
Revisión independiente de Android y del toolchain, ambas de sólo lectura:

- AND-01: intención externa no es consentimiento. La salida a mapas necesita
  confirmación local vigente después de recuperación/Bloqueo/puertas; un nuevo
  Intent, pausa o cierre de puerta cancela el consentimiento. Un lookup que
  termine al volver puede ofrecer un diálogo nuevo, nunca abrir mapas solo.
- U-01: un cupo global y ejecutores acotados contienen llamadas SAF abandonadas.
  No hay éxito después de cancelación/plazo ni eliminación tardía de documentos.
  Se acepta el límite de plataforma: IO Binder/nativo no cooperativo puede
  continuar; un cierre incierto deja guardado en cuarentena hasta reiniciar el
  proceso. Puede quedar un documento parcial. No se promete interrupción física
  universal ni se vincula ese residuo con pérdida de Room.
- DEP: se conserva la evidencia de 343 coordenadas y avisos de tooling. No hay
  componentes afectados en los runtime Release inventariados. KAPT no se aplica
  y Jetifier está deshabilitado en este build. MAIN acepta esa aplicabilidad
  acotada, no un certificado de tooling invulnerable. Reabrir ante KAPT,
  Jetifier, processors, CI/cachés o entradas no confiables; no hacer overrides
  transitivos a ciegas para maquillar el escaneo.

No se identificaron nuevos defectos reproducibles bloqueantes en esa revisión
estática. La ejecución Android nueva de AND/U sigue siendo una puerta distinta.

## Versión y preservación

Se prepara **2.0.0/code4**, superior al code3 firmado que MAIN instaló antes en
el Samsung. Cambios de versión exclusivamente en Gradle app, política y texto
del inspector. Se preservan firma permanente, paquete, Room, esquemas,
preferencias, código anterior y artefactos firmados históricos. Code4 evita
presentar una reinstalación de código igual como actualización.

HEAD de entrada: `e91e6f7394131d622153d9cd8fdbc857e3accfb3`, rama
`codex/miguardia-2.0`, índice vacío y nueve worktrees. La entrada modificada
incluye Q01/Q02 de MAIN y el candidato correctivo; no se limpia ni descarta.

## Evidencia nueva y ejecución

- Batería Android en `.local-evidence/optimization/20260910T114636.123678Z/`:
  **aprobada**, local 834/834 e interop 26/26 ejecutados nuevamente; build
  335 tareas, 29 ejecutadas y 306 reutilizadas, 25m25s. Cero insumos cambiados.
  Cinco APK inventariados; no confundir compilación incremental con las dos
  reconstrucciones Release posteriores. Lint: cero errores y 13 avisos actuales
  (12 de versiones y uno UseKtx/Uri.parse). La sugerencia KTX no es un defecto
  funcional y no se modifica el código congelado para ocultarla.
- Herramientas generales: 75/75; herramientas Release: 79/79; catálogo válido,
  106 clases y 19 grupos. Ejecutadas nuevamente por MAIN.
- Website: 167/167 pruebas, tipos y perfil compilado aprobados.
  `work/security-corrections/tests-sYBCMb`, `typecheck-2zxDMj`,
  `profile-6sXaJ8`; perfil nuevo `work/public-publication/public-v1-o4I4fC`,
  deliberadamente `publishable:false`. Ninguna escritura de fuentes web por MAIN.
- Reconstrucciones nuevas preparadas en
  `.local-evidence/release/security-code4-20260910/rebuild.py`, no firmadas.
- Dos reconstrucciones aprobadas, 14m02s y 4m54s, 118 tareas ejecutadas cada una,
  insumos estables y **bytes idénticos**. Cada copia mide
  12.717.685 bytes, SHA-256
  `612278fc139847dc1bcae784155e318e8b3c5b4756c00470f1358056d47595f9`.
  `unsigned-1.json` y `unsigned-2.json`: UNSIGNED_CANDIDATE_CHECKS_PASSED,
  cero errores, 2.0.0/code4, ocho permisos, sin debug/test/cleartext/backup,
  ABI y alineación comprobados. Esta igualdad corresponde a las dos
  reconstrucciones, no al primer build incremental, cuyo hash fue distinto.
  SHA-256 de informe1: `00f160cad4edf39f16d6e427c22ab3b0fae073b7b1523c9aa8f2845f80dcc50e`.
  Política congelada: `97f162e0969f9c8af36424d7690a9c6c41a10a07141503623e73b8258b82fcce`.
- MAIN aprueba esos bytes para firma. Plan de firma aprobado con custodia
  EXISTING_COMPLETE_HASH_MATCH y salida nueva `sign-code4`. Se lanzó consola
  clásica separada mediante `sign_interactive.ps1`, PID conhost19828. Exige
  que Joaquin confirme privacidad/ausencia de grabación escribiendo FIRMAR
  antes de delegar contraseña al firmador oficial. No se captura esa consola
  ni se recibe la contraseña. Firma pendiente de intervención humana y
  verificación separada: un proceso lanzado no prueba firma completada.
- Escaneo nuevo `security-code4-20260910/security-scan.json`: 871 archivos,
  2.414 blobs históricos y 551 entradas de esa primera APK, cero señales en
  cinco patrones. No comprueba ausencia universal de secretos o vulnerabilidades.
- Samsung exacto `R5CY529W6PL`: la consulta actual devuelve dispositivo ausente.
  Ninguna instalación, limpieza o prueba física nueva en esta continuación.
- Auxiliar QA de dos UID empaquetado con clave debug existente, sin instalar:
  `security-code4-20260910/probe-r2/security-probe.apk`, SHA-256
  `3e3a637de73ae8378d10a29a3977cbd22ec86034e441753376d525a8ef4b7abf`.
  Primera preparación quedó como diagnóstico: PowerShell 5 trató una advertencia
  nativa Java como error. Segunda ejecución registra stderr y controla exit code;
  firma técnica verificada y clave debug preservada. No usa custodia Release.

## Contacto web aprobado

MAIN añadió sólo a `app/miguardia/MiGuardiaPage.tsx` el contacto confirmado para
soporte/privacidad y una advertencia bilingüe: el correo es voluntario, abre el
cliente local, no envía automáticamente y no debe contener datos laborales,
copias, fotos ni contraseñas. Se aclara que el destinatario recibe remitente y
contenido que la persona envíe. Sin formulario, plantilla de mensaje, adjuntos,
telemetría o recolección de opiniones integrada. Regresión añadida a
`tests/miguardia-public.test.mjs`: suite final posterior 168/168,
  `website/work/security-corrections/tests-WRvvO4`. Tipos posteriores aprobados:
  `typecheck-CY7g4m`. Perfil posterior compilado `public-v1-ySiE2C`,
  `profile-jUA2Gs`, publishable=false. Navegador local sobre Worker compilado:
  contacto/aviso completos en ES/EN, revisión visual de escritorio y 320px,
  ancho documental 305px en viewport320; enlace mailto exacto con foco visible.
  No se abrió cliente de correo ni se envió mensaje. Sin QA de lector hablado
  ni zoom real nuevo. Viewport restaurado, pestaña propia cerrada y servidor
  temporal detenido; puerto5192 sin listener. Publicación sigue pendiente.

## Entrega remota todavía pendiente

Sites se consultó en lectura: Site existente
`appgprj_6a90fe153dac8191b8ea5984b4a7e871`, versión más reciente 12,
URL pública `https://blackatsystems.com`, sin publicar una versión nueva.
HEAD sobre raíz devuelve 200 y sobre API pública 405/Allow GET; se observan
headers protectores en API, no la cobertura nueva sobre la raíz. El CDN emite
cookie de infraestructura `__cf_bm`; no es sesión beta ni anonimato absoluto.
No se conservan valores de cookies en este informe.

Las herramientas Sites disponibles no ofrecen carga de objetos R2 ni control
de presupuesto/cuotas distribuidas. No se reemplaza ese límite agregando una
API administrativa pública o cambiando el origen Android. Las propuestas de
USD5/día, USD25/mes y métricas siete días **no están aprobadas ni configuradas**.
No se cambia audiencia del Site ni se anuncia un APK antes de firma, QA y
comprobación de sus bytes/remoto. Soporte confirmado: blackat.systems@gmail.com;
feedback voluntario sigue desactivado, sin reenvío de colas beta.

## Continuidad

Verificar firma cuando Joaquin complete la consola privada. Retomar
Samsung conectado para QA dirigida y upgrade preservando datos. Resolver después
el canal privado de carga/operación y costos antes de publicación. Sin commit,
push ni deploy; la firma nueva no se declara completada en este registro inicial.

## Reanudación: firma y Samsung code4

Joaquin completó la consola privada. Verificador separado:
`security-code4-20260910/signed-verification-final.json`,
`SIGNED_ARTIFACT_CHECKS_PASSED`, todos los ejecutables de inspección exit0.
APK firmado 12.729.677 bytes, SHA-256
`6fa8b481dc0837a02795c7db1bfe4acc8a606a81ac7586b43043a3bebf81137e`.
Payload idéntico al unsigned aprobado, certificado permanente, código4/nombre2.0.0,
ocho permisos y alineación verificados. No acredita instalación.

Nueva autorización expresa: «TELEFONO CONECTADO Y PRUEBAS AUTORIZADAS».
ADB confirma R5CY529W6PL, SM-S938B/API36. Oficial instalado todavía código3,
hash `a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a`.
Se conserva sin limpiar ni desinstalar. QA/test reemplazados con install -r -t,
sin -g; hashes del build final contrastados:
QA `3b62c64ee81f9e353db32a468d812d584999c97f25dbcc6d5fd9fd220cc94f23`,
test `3baebb0ad0066f1b8f5d6ba700ea3cf033f25239f5e3787fbe201738cce51aaf`.
Grupo sistema: 42 PASS, cero fallos/omisiones, metadatos antes/después iguales,
`.local-evidence/qa/20260910T124804.470530Z/manifest.json`.
QA automatizada final de esta selección: 409 casos distintos aprobados, cero
fallos/omisiones. No se transfiere la instrumentación QA a la Release.

| Grupo | PASS | Evidencia bajo `.local-evidence/qa/` |
|---|---:|---|
| Sistema | 42 | 20260910T124804.470530Z |
| Pantallas | 225 | 20260910T124842.617835Z |
| Storage/fotos/informes | 65 | 20260910T125238.186069Z |
| Copias | 30 | 20260910T125308.174446Z |
| Activity pública/avisos/carga, incluye AND-01 | 42 | 20260910T125340.941297Z |
| Raíz pública | 1 | 20260910T125516.671810Z |
| Actualizador/inspector/ContentProvider | 4 | 20260910T125544.703030Z |

Repetición de V2ReadyCalendarRecreationActivityTest 6/6 en
20260910T125556.700012Z para disponer del fixture sintético dos-UID; no suma
casos distintos. No se ejecutaron nuevamente las pruebas database sin cambios.

Ensayo dos UID reales: QA10876, auxiliar10361. Auxiliar r2 presentaba su campo
debajo de ActionBar; se corrigió exclusivamente su UI de laboratorio en una
copia r3, sin tocar producto/Release. APK técnico r3:
`16dd78d47f6083b7e4bab9a16e54c6d68f1156c18ce972153b3645613f9bd2b2`.
Emisor real startActivity con DIRECTIONS, UUID sintético válido y trusted=true:
diálogo local; cancelar conserva contador geo0; repetir exige confirmación.
Confirmar y seleccionar receptor ficticio con Solo una vez produce contador1.
Regresar y enviar ID inválido: cero diálogos y contador permanece1. No se eligió
Maps ni se fijó manejador permanente. Una primera entrega fría sólo navegó al
detalle; no acredita diálogo en cada condición de arranque. No se acreditaron
todavía Bloqueo/recuperación/ráfaga/rotación mediante el auxiliar. Auxiliar
retirado al cerrar el ensayo, Success; sus datos sintéticos se eliminan con él.

SAF OEM supervisado: primer intento falló en línea67 al regresar tras cancelar
DocumentsUI (`No compose hierarchies found`), antes de guardar documento.
El helper awaitTag consultaba raíces inexistentes durante la transición;
se cambió sólo el test para admitir ausencia temporal de raíz, manteniendo el
deadline15s y la exigencia del mismo tag. No se relajó ninguna aserción de
producto. Catálogo actualizado y válido106clases/19grupos. Recompilación de
AndroidTest inicial corregido compiló en59s. La repetición conservó el selector
abierto y venció el plazo: el primer Atrás no había completado su salida. Se
agregó espera de idle y hasta tres Atrás, exclusivamente mientras el picker
conserva primer plano, con comprobación expresa de salida. Esa pasada alcanzó
el segundo selector y detectó la misma consulta prematura de raíz en la espera
del guardado; se aplicó allí la misma corrección. Tres fallos del arnés quedan
registrados; ninguno se convierte en PASS retroactivamente.

AndroidTest final compilado23s,4tareas ejecutadas/83reutilizadas; SHA-256
`e83815c3b0107ec92c3c322682a3ee95331a62aefd2261676c689176fe1acf84`.
Sólo cambió UpdatesOemSafActivityTest y su catálogo: producción QA y Release
siguen intactas. Repetición supervisada final PASS1/1 en93,570s: cancelación
OEM, segundo guardado, regreso a Ayuda y recreación. El picker recordaba una
carpeta de QA vieja inexistente; se eligió Descargas, no se recreó aquella
carpeta ni se tocó contenido ajeno. Documento exacto nuevo
`Download/MiGuardia-code4-QA-20260910-1310.apk`,54bytes, SHA-256
`ea2e6379b9bd1aadbd7697173f828fa4d57d67890323c2dc4c4eef424f13bf8c`,
idéntico al texto sintético esperado. No se instaló. Segunda prueba de
incompatibilidad QA/publicación PASS1/1. Total411casos distintos con resultado
final PASS; los409 anteriores corresponden al APK test anterior con sus insumos
sin cambios, no a una nueva batería completa sobre el último testAPK.

## Upgrade oficial Samsung y cierre

Joaquin desbloqueó la oficial antes y después del reemplazo. Antes: código3,
hash firmado anterior confirmado. `adb install -r` del firmado code4: Success;
sin desinstalar, limpiar, restaurar copias ni instrumentar producción.
Después: código4/nombre2.0.0; hash instalado exactamente `6fa8b481…f81137e`.
UID10336 y primera instalación2026-09-10 03:44:17 conservados;
última actualización10:04:21. Ambos desbloqueos fueron humanos, sin lectura o
captura de credenciales. Tras el segundo, Calendario idéntico por las49 entradas
de texto/descripción, SHA-256 semántico antes y después
`f651c313fa4a388247077898e515e5391f2d8124e891a8a1e4bfeced4aadb8bd`.
Septiembre no mostraba jornadas. Esto acredita estado visible y continuidad de
instalación/Bloqueo, **no snapshot de27tablas pobladas ni lectura de sandbox
Release**. No se utilizó run-as/root contra producción. No acredita el circuito
descargar APK oficial→SAF→instalador: upgrade fue por ADB y SAF usó54bytes sintéticos.

Retirados por ruta exacta: documento sintético54bytes; siete APK de inspección
en cachéQA y sus siete copias propias en /data/local/tmp; cinco XML/PNG propios
de esta sesión. No borrado recursivo. Auxiliar y QA.test desinstalados, pm path
vacío; QA preexistente detenida/conservada. Oficial code4 abierta, Statusok.
Sin permisos concedidos con-g, cambios de reloj, red, fuente/densidad del sistema,
credenciales, firma nueva posterior, commit, push o publicación.

Pendientes: dataset completo/27tablas y fotos en upgrade, matriz OEM ampliada
de Bloqueo/recuperación/ráfagas/todas las variantes, TalkBack hablado, otrasAPIs,
descarga oficial HTTPS y operación remota. La batería ejecutada no garantiza
ausencia universal de errores. No se reabre la auditoría local ya aceptada.
