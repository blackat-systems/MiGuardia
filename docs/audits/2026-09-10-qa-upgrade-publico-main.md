# QA instalada y actualización pública — MAIN

Fecha: 10/09/2026. **EN CURSO; no acredita upgrade ni publicación.**

## Autorización e identidad

Joaquin autorizó expresamente crear un emulador desechable Android 13, instalar
las dos Release completas, cargar datos ficticios, comparar datos internos y
retirar al finalizar sólo ese entorno. La autorización incluye copia inactiva
privilegiada del sandbox del emulador; no exportación de claves. Samsung y otras
instalaciones excluidos. Entrada Git limpia `e91e6f7394131d622153d9cd8fdbc857e3accfb3`.

- AVD propio: `MiGuardia_Public_Upgrade_API33_20260910`, serial `emulator-5582`.
- Imagen existente `system-images;android-33;google_apis;x86_64`, revisión 17.
- Directorio: `.local-evidence/release/upgrade-api33-20260910/avd`.
- Creación por avdmanager, sin reemplazar un AVD anterior ni descargar imagen.
  Se observaron avisos de archivos `devices.xml` ausentes; la creación terminó
  con salida 0 y configuración/registro propios comprobados.
- Primer arranque con puertos libres, sin ventana, audio ni snapshots. ADB se
  dirigió exclusivamente al serial autorizado; `emu avd name`, API 33 y root
  comprobados. No se enumeró ni operó Samsung.
- Comandos de desactivar red antes de que arrancaran los servicios fallaron;
  se repitieron después del arranque. Antes de abrir Release: Wi-Fi desactivado
  y datos móviles 0 comprobados. No se cambiaron reloj, densidad ni fuente.

## APK y primeras pruebas

QA/test del freeze previo instaladas, sin permisos `-g`:

- QA: `d71623d3393d11c07efe6a9727b667e3f522fc5cfa37cae6c5f44846c34f16a4`.
- QA AndroidTest: `ee31c693f7e3fe10f43f79cc27a5ed4509b2689fb17d6377691dd170c5b6003a`.

El runner comprobó igualdad de bytes locales e instalados antes de ejecutar.
Resultados sobre esa candidata, **anteriores al fix de permiso**:

| Evidencia bajo `.local-evidence/qa/` | Superficie | Resultado |
|---|---|---|
| `20260910T044942.456929Z` | Presenter de avisos, incluida R03 | 11 aprobadas |
| `20260910T045011.211629Z` | Raíz pública/recreación | 1 aprobada |
| `20260910T045023.881069Z` | Sistema, detenido ante fallo | 23 aprobadas, 1 fallida; 18 pendientes allí |
| `20260910T045540.046609Z` | Continuación independiente sistema | 1 aprobada |
| `20260910T045544.386593Z` | Continuación independiente sistema | 6 aprobadas |
| `20260910T045613.737851Z` | Continuación independiente sistema | 2 aprobadas |
| `20260910T045616.948767Z` | Continuación independiente sistema | 9 aprobadas |

Total de esa selección: **53 aprobadas, 1 fallida**, sin omitidas. Las 18
pendientes del runner detenido se ejecutaron en los cuatro registros separados;
no se reescribió el fallo original. Una repetición dirigida del método fallido
reprodujo la misma línea 48; es diagnóstico adicional, no otro caso aprobado.

Origen Release completo código 2 instalado y abierto en frío: `Status: ok`,
1.874 ms. Hash del APK instalado recalculado por ADB e idéntico al firmado
`5345a2b460b2b16244a7e3ba20035b92c6af857d3f5fb1ff12c3d69a1cdb778f`.
Se revisó `first-open.png` y jerarquía XML: cuatro sectores, Continuar y
Restaurar una copia existente; sin clave beta, cuenta ni requisito de conexión.
Esto acredita primera apertura del origen, no actualización ni candidata final.

## Q01 — permiso residual beta

`WeatherInfrastructureInstrumentedTest.manifestAllowsOnlyApproximateObjectiveLocationAndRejectsCleartext`
falló porque la APK declaraba `ACCESS_NETWORK_STATE`. El commit `3df5b43`
lo agregó para `BetaConnectivity`; `d0105e0` retiró ese único consumidor.
Inspección actual y revisión independiente no encontraron consumidores de
ConnectivityManager/NetworkCapabilities/listeners en producción. El merger
atribuye la declaración sólo al manifiesto de app. No se demostró transmisión
de datos causada por el permiso normal; es un residuo de mínimo privilegio.

Corrección: retirar la declaración y la entrada de la política vigente;
actualizar conteo exacto a ocho en el inspector y rechazar explícitamente su
reintroducción. **La prueba negativa Android se conserva**, no se invierte ni
omite. Herramientas Release: 79/79; documentación/diff aprobados. No cambian
Kotlin, Room, datos, dependencias, SDK o versiones. La modificación de empaquetado
requiere nuevos unsigned, firma y QA del destino; no tocar artefactos anteriores.
El origen 2 no necesita refirma. Dos reconstrucciones nuevas, 118/118 tareas
ejecutadas cada una, terminaron en 7m51s y 6m11s. Sus bytes son idénticos:
12.701.305 bytes, SHA-256
`210dc575133acbef324f3992f4b00937e438a7f23bd63df29e22ad7083d98981`.
Inspector: `UNSIGNED_CANDIDATE_CHECKS_PASSED`; comparación `BYTE_IDENTICAL`.
Política congelada `corrected-policy.json`: ocho permisos, SHA-256
`9457578dbd118002392d5afa9f9790b7f37d93fbf1ee42d73e26ff9834af3180`.
Un primer inspector usó una ruta Java inexistente y falló antes de ejecutar;
se repitió con el Java exacto registrado por el runner. No se alteraron informes
o políticas anteriores. El plan de firma confirma custodia existente; no firma.

## Preparación del dataset: distinción necesaria

Revisión independiente encontró que `REQUIRED_POPULATED` exige medical_leaves
y explicit_day_statuses, pero no existe alta pública V2 para esos estados.
Planificación §14 los conserva sin habilitar el recorrido estructural V1.
No se agregará producto ni se relajará el comparador para simular cobertura.

Se prepara una copia lógica ficticia mediante contratos productivos y bases
aisladas de QA. Se restaurará conscientemente mediante UI de Copias del origen
Release **antes** del snapshot; nunca después del upgrade. Esto acreditará
preservación de estados restaurados, no alta de esos estados por UI. La fixture
deberá validarse/restaurarse aislada antes de usarla. Bloqueo, Widget y sus
preferencias locales no se transportan como preferencias portables.

Un especialista preparó el archivo fuera de `app/src` hasta finalizar los dos
builds Release. MAIN lo revisó con un segundo revisor y agregó espera acotada
de recuperación/estabilidad antes de fijar la huella QA, conservando la igualdad
final estricta. La primera compilación detectó acceso a un comparador internal
de otro módulo y varianza de Array; corregidos sólo en la fixture. Se usa clave
estable pública para los UUID de longitud fija de esta fixture; el esquema y
los gateways siguen validando la representación completa. Segunda compilación
QA/AndroidTest aprobada. Catálogo: 105 clases/18 grupos; preparación supervisada,
aislamiento y opt-in obligatorios; 74/74 pruebas de herramientas generales.

Ejecución `fixture-run-1/result.json`: 1 aprobada, cero omisiones. Exportación y
restauración en dos bases aisladas verificadas: 27 tablas/32 filas, foto PNG
800×480 y 17 preferencias. Huella viva QA idéntica. Contenedor validado:
33.002 bytes, SHA-256 `3f26f2b88f4c922f501ef3f47442dcd2250452a9e4c7b4732100024e83404027`.
Foto: `de5cfeb0aff7be36c021a2848f8f3a4a01e353e847b9600753562f877ee38acb`.
Clima y notificaciones desactivados, sin direcciones/coordenadas reales.

MAIN transfirió exclusivamente esa copia ficticia al Download del AVD y la
seleccionó mediante DocumentsUI del origen firmado 2. Vista previa: 0 filas
actuales, 32 entrantes, 27 tablas, una foto; tres conflictos de preferencias
explícitos. Se eligió Reemplazar todo y se escribió su segunda confirmación.
Restauración verificada; Ayuda apareció según prioridad y se omitió por UI.
Calendario muestra disponibilidad de 10/09, guardia automática de 11/09,
vacaciones y feriado. Detalle manual de 07/09: real 08–17, normales 8 h,
extra 16–17 de 1 h y nota ficticia. Foto abierta/revisada visualmente.
Resumen: 11 h trabajadas, 8 normales, 3 extras y 8 pendientes; sólo lectura.
Evidencias `origin-restore-preview.png`, `origin-restore-done.png`,
`origin-photo.png`, `origin-manual-detail.png`, `origin-summary.png`.
Estas pruebas son del origen; aún no se instaló el destino ni se tomó el
snapshot definitivo. No se escribe SQL en Release ni se restaura tras upgrade.

## APK corregidos y repetición instrumentada

QA final: `61d446f813cfde031e807f1ad6cdcb20f0fdc17e2e709bdf1976dfbb0b67c59c`.
QA AndroidTest: `946372be35d8808fbd9b77bd428fc77321a09e7e03fe6437aa1862c4534d47b3`.
Conservados junto a `corrected-qa-fixture-build-r2/result.json`; build 48 s,
104 tareas (6 ejecutadas, 98 actualizadas). Reemplazados sólo QA/test del AVD.

| Evidencia nueva bajo `.local-evidence/qa/` | Superficie | Resultado |
|---|---|---|
| `20260910T051412.863471Z` | Infraestructura Clima, incluye Q01 | 9 aprobadas |
| `20260910T052230.819357Z` | Sistema completo | 42 aprobadas |
| `20260910T052349.086753Z` | Raíz pública | 1 aprobada |
| `20260910T052355.753556Z` | Presenter y R03 | 11 aprobadas |

La selección inicial de 54 casos queda repetida íntegra y verde sobre los
APK corregidos; las 9 de Clima están incluidas en sistema, no son casos nuevos.
Preparación de copia: una prueba adicional independiente, verde. Un intento
de raíz sin bandera system-effects fue rechazado antes de ejecutar; se repitió
con la autorización de efectos ya cubierta por el ensayo desechable.
Copias `20260910T052444.050090Z`: 30/30. Almacenamiento
`20260910T052533.054953Z`: 65/65, cero fallos/omitidas.

La ampliación pública `20260910T052632.074958Z` falló en
`HoursAndExtrasActivityTest.createRecreateCorrectAndDeleteReturnsToTheSameCalendarDetail`.
Logcat del TestRunner del AVD permitió recuperar el stack: 02:27:45 local,
ComposeTimeoutException 15.000 ms en línea 153, esperando la posición corregida
persistida. No fue una aserción de visibilidad. Repeticiones sin cambios:
método aislado 1/1, clase completa en mismo orden 2/2 y clase dentro de nueva
ampliación 2/2. **Fallo observado no reproducido; no se declara corregido** ni
se cambian tiempos/asserts por inferencia. La revisión independiente no identificó
causa local demostrada.

Repetición pública `20260910T053040.193596Z`: 24 aprobadas, ninguna fallida
por aserción, 17 pendientes cuando se perdió el dispositivo. Windows Application
Error 1000 a las 02:32:24 confirma caída de
`qemu-system-x86_64-headless.exe`, excepción nativa `0xc0000005`, proceso
0x29B0, informe `7fa8360a-d260-4961-a095-c17b406981a5`.
No se atribuye ese cierre a MiGuardia ni se presenta la suite como aprobada.
Una lectura logcat posterior quedó esperando ADB: se detuvo sólo ese cliente
identificado por PID/argumentos. Reinicio del mismo AVD, mismos archivos y sin
wipe-data solicitado. Tras arranque se reconfirmaron AVD/API/root y red
desactivada. Origen firmado 2 intacto por SHA-256 instalado. Con Release
force-stopped, se copiaron DB/WAL y raíces privadas: 27 tablas/32 filas,
esquema V6 y FK/integridad correctos, conteos idénticos a la fixture validada.
`origin-population-check/snapshot.json` es control de población, **no baseline
definitivo**: faltan estados locales de Bloqueo/Widget. `attestation.json`
registra selección y excluye sólo dos archivos administrativos de profileinstaller;
no se normalizan fotos/preferencias/residuos. No se exportan claves Keystore.

Lint final: build 5m40s, 98 tareas (12 ejecutadas); cero errores y **12 avisos**
de versiones disponibles en Gradle no modificado. Son seis avisos más que el
informe anterior, todos de actualizaciones; no se instalaron versiones ni se
suprimieron advertencias. Escaneo local actual: 863 fuentes, 2.385 blobs históricos y 551
entradas APK; cero señales en los patrones, sin garantía universal.

## Segunda interrupción y diagnóstico acotado

La reanudación `20260910T053722.665481Z` no completó ninguna de las cuatro
pruebas de V2ManualShiftLoadActivityTest: Windows volvió a registrar caída
nativa de qemu a las 02:37:38.815, excepción 0xc0000005, proceso 0x5584,
informe eb5528c4-5844-4f7c-a753-47f311e46d7f. No es un fallo de aserción
Android ni prueba de una causa productiva. Se inició el mismo AVD con el motor
existente swangle en lugar de swiftshader, sin wipe ni imagen nueva. Es una
alternativa diagnóstica, no una causa gráfica demostrada. Logs únicos
`emulator-swangle-3.*`. Control posterior `origin-population-check-after-crash2`
confirma de nuevo el APK firmado original, integridad/FK, 27 tablas y 32 filas.

La revisión detectó otra hipótesis: una respuesta de reconstrucción de revisión
de extra podría sobrescribir una revisión nueva. Se preparó una prueba con
ViewModel/Main reales y repositorios en memoria, sin Activity/Room. Se incorpora
como diagnóstico antes de modificar producción. Todavía no acredita la carrera
ni explica el timeout original; no se agrega infraestructura o dependencia global.

## Cierre operativo de esta pasada

El tercer intento `20260910T054614.804350Z` falló en preparación antes de
instrumentar: cero ejecutadas, cuatro pendientes. Qemu cayó a las 02:46:37.728,
proceso 0x4474, misma excepción, informe 4d6a456f-1a71-4624-b975-83ac69c8142a.
`emulator-native-crashes.json` conserva los tres eventos Windows. Swangle no
resolvió el bloqueo; no se deduce una causa ni se repite otro arranque idéntico.
Qemu ya no está en ejecución; se preservan el AVD y sus datos para continuar.
La comprobación de 27 tablas/32 filas fue anterior a esta tercera caída, no
se atribuye a una apertura posterior inexistente.

Diagnóstico HoursExtraRestorationRaceInstrumentedTest: dos casos en memoria,
compilación verde 2m27s (87 tareas, cuatro ejecutadas). APK de prueba congelado
en `hours-review-race/qa-test-before.apk`, SHA-256
`8efacec40b744f664eaaec3a0a9e69ba8e11316473ce792ce55937772e8466dd`.
**No instalado ni ejecutado**. Test fuente SHA-256
`24e4e939ed29453a11c8ec1bf7aeec97b914a5a32954947591cd985abcf8029a`.
Catálogo: 106 clases/19 grupos; nuevo grupo exige aislamiento desechable y
argumento explícito. Herramientas posteriores: 75/75, incluidas guardas nuevas.
No cambió Kotlin productivo por esta hipótesis. Las pruebas Android anteriores
usan el APK de pruebas anterior, no este diagnóstico nuevo.

La nueva firma permanece pendiente de interacción privada del usuario. Ninguna
contraseña fue leída ni pedida en chat. No se firmó nuevamente, no se actualizó
el origen, no se publicó ni se hizo staging/commit/push. Los dos especialistas
terminaron sus entregas. No quedan builds o instrumentaciones propias corriendo.
La eliminación del AVD se difiere porque el ensayo poblado aún no terminó;
no se limpia la evidencia ni se sustituye este aislamiento por Samsung.

## Pendientes

Cerrar Q01 con nueva firma; su regresión Android ya pasó. La fixture y su
restauración/UI están verificadas. Completar estados locales, tomar copias consistentes
de 27 tablas/archivos y ejecutar upgrade sin desinstalar ni restaurar después.
Completar matriz de retornos, bloqueo, avisos, Widget y accesibilidad pertinente.
El servicio controlado de descarga HTTPS sigue siendo una puerta separada.
No firma nueva, actualización, publicación o retirada final acreditadas aquí.

## Continuación autorizada: Q02 confirmado y congelación renovada

Joaquin autorizó completar agenticamente el alcance. Se conservó el único
operador/escritor; dos revisores internos trabajaron en lectura. Samsung no se
consultó. HEAD e91e6f7394131d622153d9cd8fdbc857e3accfb3 e índice vacío.

La prueba de carrera requirió corregir primero su catálogo ficticio incompleto
y una comparación por referencia de configuración. Esos fallos no demostraban
producto. Con fixture válida, `20260910T055920.465950Z` aprobó el control y
reprodujo que la respuesta tardía reemplazaba la última revisión y guardaba el
puesto anterior. Q02 agrega comprobación de cancelación y de superficie,
borrador/revisión vigentes antes de publicar la respuesta restaurada. La
regresión posterior `20260910T060209.686875Z` aprobó 2/2. Revisión independiente
favorable. No prueba que esta carrera haya causado el timeout previo de Activity.

V2ManualShiftLoadActivityTest mantenía una aserción inmediata con prefijo ambiguo
y árbol combinado. Tras fallos de comprobación, la prueba final espera el texto
completo visible en árbol no combinado durante el límite existente. No se cambió
producto, no se desplazó el aviso ni se sustituyó visibilidad por existencia.
`20260910T061510.520533Z`: cuatro aprobadas, incluido cancelar y conservar catálogo,
fechas/confirmación, sin guardar jornadas. Hash normalizado de fuente:
480065e95a51ce3b24e9b6143bbde6c09aab27617cb0eabaf7585a018a2fd34e.

### Estabilidad y QA atribuida

Desactivar Vulkan manteniendo Swiftshader no evitó una cuarta caída nativa de
qemu: 03:04:05.4419 local, PID 492, excepción 0xc0000005, informe
bf340f81-f951-4e64-a6c3-a0fe904f59c9. No hubo nueva imagen, driver ni cambio de
seguridad Windows. El quinto arranque usa `-gpu host -feature -Vulkan`; el log
confirma AMD Radeon/OpenGL Translator, no fallback software. PID headless 24708.
Completó las pruebas y recorridos posteriores sin nueva caída observada; no
constituye diagnóstico universal de la causa ni estabilidad indefinida.

Grupo público: 41 casos únicos aprobados entre `060212.042950Z` (24 antes de
caída), `061229.722273Z` (Widget, 5), `061510.520533Z` (carga, 4),
`061547.551492Z` (recreación, 5), `061612.565543Z` (recurrencia, 1),
`061623.541588Z` (horario real, 1), `061643.835128Z` (edición, 1), todos prefijados
20260910T bajo `.local-evidence/qa/`. No omisiones. Una llamada con opciones
--class repetidas seleccionó sólo la última; se detectó en el manifest y las
restantes se ejecutaron individualmente. `061456.486278Z` fue rechazo previo por
APK test recompilado distinto del instalado, cero ejecutadas; se instaló el test
correcto antes de repetir. No se contaron rechazos como pruebas aprobadas.

APK QA común: 758bc780ba96da9435463207c29822ecec16aebd62c8a776d6c2211e1466e5f9.
Test inicial Q02: 24ebadad59b876e9b29a342a52fa397bd11b489e6eb6f3e21e9947ac07721729.
Test final tras sincronización: 1e6c46f626ec465d5739f5646db53b3d4946989ec25a5b503b7b45dc53ed82b2.
Cada manifest identifica los bytes realmente instalados.

### Widget y datos del origen firmado

Launcher real asignó ID 7 al provider de com.blackatsystems.miguardia, host
NexusLauncher 1024. Se configuró Próxima jornada, Reducida, Clima apagado.
La reconfiguración a Próximo franco se canceló; el Widget conservó la jornada
11/09 08:00–16:00. Toque abrió el detalle exacto de la jornada
30000000-0000-4000-8000-000000000021, con objetivo ficticio. Capturas inspeccionadas
`origin-widget.png` y `origin-widget-cancel.png`. No datos reales ni red.

`origin-population-check-widget` confirma nuevamente el origen 2 firmado,
27 tablas/32 registros, FK/integridad y archivo de foto en copia inactiva.
Incluye preferencias reales del Widget. No es baseline final: Bloqueo permanece
apagado y la UI exige configurar seguridad del sistema. No se creó credencial,
no se fingió autenticación ni se escribió el DataStore directamente.

### Validación y artefacto vigente

`q02-full/result.json`: 825 JVM locales + 26 interop, cero fallos/errores/skips;
build cinco APK, 335 tareas (19 ejecutadas), 6m35s, insumos sin cambios durante
la pasada. El ajuste posterior sólo de sincronización AndroidTest compiló en
47s y pasó en el dispositivo. Herramientas generales 75/75; herramientas de
entrega 79/79; catálogo 106 clases/19 grupos y documentación válidos. Lint:
cero errores, 12 avisos de versiones, sin actualizar dependencias.

Dos reconstrucciones Release posteriores con 118/118 tareas ejecutadas cada
una: 5m38s y 6m27s. Comparación `q02-rebuild-comparison.json`: ambas 12.701.305
bytes, SHA-256 1331ac6fb60bda77b55945ceb8f8bdf2b8955a8b1c1a6a09185c025eb16dc763.
`q02-unsigned-inspection.json` aprobado: 2.0.0/código 3, ocho permisos,
sin debug/test/cleartext/backup, controles de ABI/alineación/firma unsigned.
`q02-security-audit.json`: 864 archivos, 2397 blobs y 551 entradas APK,
sin señales en sus patrones; no garantía universal. Q01 y firmas anteriores
no representan este destino Q02.

Preflight de firma PLAN_ONLY aprobado con custodia COMPLETE/hash coincidente.
Primer intento rechazó rutas con separadores no admitidos; se corrigió la
invocación a rutas Windows exactas, sin relajar guardas. No se inició firma ni
se solicitó contraseña mediante herramientas. Directorio previsto ausente:
`sign-q02-code3`. Requiere intervención privada y verificación firmada separada.

### Puertas restantes, sin inventar aceptación

Website verificó HEAD ae9893894988633cb1c60d7218212510627aa10c. Sus commits y
PUBLICATION_2026-09-10 documentan presentación/portfolio aprobados; Sites informa
v12 pública. API normativa intacta, sin APK ofrecida. Preservar latest-portfolio,
no sustituir por portada histórica. No se escribió ni publicó website aquí.

Faltan firma privada, credencial de ensayo para Bloqueo habilitado, baseline
final y upgrade conservando estado. También falta resolver carga privada R2
verificable y servicio HTTPS de ensayo en el origen fijado: las herramientas
Sites presentes no ofrecen operación R2 ni restricción de ruta para este ensayo.
No cambiar audiencia del Site, origen/TLS, agregar credenciales beta ni ofrecer
prematuramente una APK. Upgrade local no reemplaza descarga→SAF→Archivos.
Soporte confirmado; formulario/retención consultados, sin respuesta ni recolección.

No staging, commit, push, firma nueva, upgrade ni publicación de APK. Builds y
QA finalizaron; se conserva AVD propio activo y datos sintéticos para la
intervención pendiente. Release quedó force-stopped para su copia inactiva.

## Firma Q01/Q02 verificada después de intervención privada

Joaquin confirmó presencia y ausencia de grabación/pantalla compartida; se abrió
consola clásica separada mediante conhost, PID 23572, sin redirección ni captura.
El usuario informó finalización. MAIN no leyó ni recibió la contraseña.
Recibo `sign-q02-code3/signing-unverified.json`: 2026-09-10T06:36:06.1687227Z.
La alineación conservó exactamente el hash unsigned aprobado.

MAIN ejecutó después el verificador separado sobre esos bytes, informe unsigned
por hash y política congelada: **SIGNED_ARTIFACT_CHECKS_PASSED**, cero errores.
Evidencia: `.local-evidence/release/upgrade-api33-20260910/q02-signed-verification.json`.
APK `sign-q02-code3/signed-unverified.apk` (nombre de producción del firmador,
no estado final de aceptación): 12.713.293 bytes, SHA-256
a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a.
Certificado permanente AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D;
paquete com.blackatsystems.miguardia, 2.0.0/código 3. Contenido coincide con
unsigned aprobado; verificación oficial por intervalos SDK y alineación 16 KiB.
No nueva firma de origen, regeneración de clave, instalación, upgrade o publicación.
El siguiente paso es QA instalada con el origen poblado y estados locales completos.

## Samsung: instalación oficial y primera apertura autorizadas

Joaquin indicó «te habilito el samsung, probala». Esta autorización posterior
habilita ensayo conservador en R5CY529W6PL; no extiende fixtures destructivas ni
limpieza de datos del AVD al teléfono. ADB confirmó device, SM-S938B/API 36.
`pm path` y `dumpsys package` confirmaron ausencia previa del paquete oficial
com.blackatsystems.miguardia. No se enumeraron ni alteraron otras aplicaciones.

Se instaló exclusivamente la APK firmada Q01/Q02, sin -g, sin desinstalar y sin
APK de instrumentación. Resultado Success; hash instalado leído en el teléfono:
a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a.
VersionName 2.0.0, código 3; arranque MainActivity COLD, Status ok.
Jerarquía y captura inspeccionadas: cuatro rubros, Continuar sin selección,
Restaurar una copia y explicación de reglas. Sin acceso beta/cuenta, sin recortes
observados en esta pantalla. No se cambió tema, orientación, fuente/densidad,
red, permisos ni credenciales; no se acredita offline porque no se aisló la red.

Evidencia local ignorada `samsung-signed-first-open/first-open.png` y XML.
Los dos temporales exactos creados en /data/local/tmp se retiraron después de
copiarlos. No se capturaron credenciales ni contenido de otras aplicaciones.
La app oficial queda instalada y abierta en configuración inicial; no se eligió
rubro ni se poblaron datos ficticios en la instalación personal. No se tocaron
paquetes QA preexistentes. Pendientes configuración consciente, recorrido de uso
y Bloqueo; esta instalación limpia no equivale a upgrade con datos ni publicación.

### Reanudación de uso y solicitud de autenticación

Joaquin informó configuración iniciada y autorizó las pruebas necesarias.
Se comprobó MainActivity y menú sin exportar textos laborales; se abrió Bloqueo.
La pantalla declaró Desactivado y credencial segura compatible. MAIN tocó Activar
tras avisar al usuario; a partir de allí no leyó UI ni tomó capturas. Resultado
de autenticación pendiente de respuesta humana, no se presume activación.
Los XML transitorios propios de menú/Bloqueo fueron retirados por ruta exacta.
No jornadas modificadas, limpieza de datos, fixtures ni nueva instrumentación.

Propuesta del usuario de enlazar descarga a Drive: evaluación, no implementación
ni subida. Google documenta archivos compartidos por enlace y descarga en navegador;
el contrato público vigente fija dominio/ruta y rechaza 3xx/cambio de origen.
Un enlace web manual a Drive no es reemplazo transparente del actualizador.
No se recibió URL concreta de archivo ni se accedió a su cuenta Drive.

### Continuación física y cambio explícito a Samsung

Joaquin autenticó Bloqueo: la UI confirmó Activado. Bloquear ahora mostró puerta
genérica sin información laboral; luego del desbloqueo humano volvió a la app.
Se recorrió Resumen septiembre/agosto/septiembre y se generó un PDF privado de
septiembre: Archivo listo, 25.193 bytes, application/pdf. Las cinco inclusiones
privadas estaban desmarcadas. No se guardó externamente ni se compartió.

Después de exigir pruebas en su Samsung, Joaquin declaró que no contiene datos
importantes. MAIN pasó la batería automática al serial R5CY529W6PL: QA existente
actualizada con install -r -t, QA.test antes ausente instalado, ambos Success.
Hashes QA 758bc780ba96da9435463207c29822ecec16aebd62c8a776d6c2211e1466e5f9;
QA.test 1e6c46f626ec465d5739f5646db53b3d4946989ec25a5b503b7b45dc53ed82b2.
No se limpió ni reemplazó la APK oficial. Grupo app-rendering en ejecución con
runner autorizado; evidencia .local-evidence/qa/20260910T070243.463482Z.
No constituye aún resultado final. QA.test deberá retirarse al cerrar.

El emulador se dejó de usar por esa orden: 27 clases/180 PASS consolidados,
sin fallos/omisiones en lo consolidado; siete clases/45 casos sin acreditar y
database no iniciado. Evidencia 20260910T065301.708926Z, sin manifest final por
detención deliberada del coordinador. No se interrumpió la instrumentación
en curso ni se modificó el origen oficial. No atribuir esta evidencia a Samsung.
MAIN repitió herramientas generales 75/75 y entrega 79/79: aprobadas.

### Samsung: fallo de aserción de menú y regresión corregida

Primera pasada app-rendering: diez clases verdes; NavigationDrawerComposeTest
tuvo seis PASS y un FAIL. Se reprodujo aislado: línea 368 exigía ver
drawer-action-work-setup sin desplazar, en viewport corto y zoom 200 %. ADR0015
define panel desplazable, no visibilidad simultánea de todas las opciones.
Revisión independiente de sólo lectura coincidió. Se cambió únicamente el test:
desplazar hasta Trabajo, Apariencia y de vuelta a Calendario; no producción.
Catálogo actualizado con hash normalizado 74da7fff91d2026463240c6a8c0e8877dc65d3e42d7c9d098afb2609c799e95e.

Las otras 23 clases rendering se ejecutaron con el APK previo: 224 PASS y un
fallo original entre las 34 clases. AndroidTest recompilado (59s, 4 tareas
ejecutadas/83 reutilizadas), hash 96eebb83c93b7872fea873de024069aaa53645c07f2887d93edad7c2f540d01f.
Instalado sólo QA.test con reemplazo; clase NavigationDrawer repetida 7/7 PASS
en 20260910T071040.364084Z. Así las 225 pruebas rendering tienen resultado
aprobado vigente por insumos propios; seis ejecuciones repetidas no se suman
como casos distintos. No se atribuye una repetición global al APK de test nuevo.

Database.test antes ausente se instaló separado: 126/126 PASS en
20260910T070951.198886Z, 15 clases. No es la base de la APK oficial.
Continúan app-storage, app-backup, app-public-fixtures y app-system sobre Samsung.
No alarmas exactas ni pruebas históricas beta; las pruebas requieren solamente
el aislamiento QA autorizado. Ambos paquetes de instrumentación nuevos quedan
pendientes de retiro al finalizar; la APK oficial se preserva.

### Cierre de batería Samsung solicitada: 530 casos distintos aprobados

| Grupo | Casos aprobados | Evidencia local bajo .local-evidence/qa |
|---|---:|---|
| Rendering | 225 | 20260910T070243.463482Z + continuaciones individuales 070444–070730; menú corregido 071040.364084Z |
| Database | 126 | 20260910T070951.198886Z |
| Storage/fotos/informes | 65 | 20260910T071108.983991Z |
| Copias/recuperación | 30 | 20260910T071141.885757Z |
| Activity pública/jornadas/avisos | 41 | 20260910T071226.940071Z |
| Widget/sistema | 42 | 20260910T071404.792065Z |
| Raíz pública | 1 | 20260910T071450.846067Z |

Total 530 casos distintos con resultado final PASS, cero omisiones. Se conserva
el FAIL inicial de menú y su reproducción; no se borran ni se presentan como
pasadas. Las seis pruebas de esa clase repetidas no aumentan el total. Primer
APK de instrumentación y segundo APK se distinguen arriba; producto QA no cambió.
Esta es instrumentación real en SM-S938B/API36 con fixtures QA; no significa
530 recorridos manuales de la Release ni auditoría visual humana completa.
MAIN repitió además 825 JVM locales con fresh-tests: resultado aprobado en
.local-evidence/optimization/20260910T070104.695913Z/result.json.

Al terminar: force-stop sólo QA; uninstall de QA.test y database.test introducidos
por MAIN, ambos Success y pm path posterior vacío. QA preexistente queda instalada,
con el estado sintético resultante; no se hizo pm clear. Se retiraron por nueve
rutas exactas los XML temporales propios del teléfono. La APK oficial se volvió
a abrir (Status ok), hash instalado a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a
idéntico al inicial. No se leyó su almacenamiento privado ni ninguna credencial.

Pendientes explícitos: actualización instalada con dataset poblado, descarga
HTTPS real/SAF completo del actualizador, TalkBack hablado, matriz OEM manual
integral, alarmas exactas y compatibilidad en otras APIs. No se ejecutaron las
fixtures exclusivamente emuladas ni se rehabilitó beta. No publicación, firma
nueva, commit o push. No se ofrece garantía de ausencia universal de errores.
