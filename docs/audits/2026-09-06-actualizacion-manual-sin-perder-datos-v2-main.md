# Auditoría MAIN — 28: Actualización manual sin perder datos

Fecha: 2026-09-06.

## VEREDICTO

**CLIENTE 28 APROBADO PARA INTEGRACIÓN LOCAL — QA SAMSUNG DIRIGIDA VERDE.**

MAIN cerró los dos P2 y la puerta física del cliente con autorización explícita
de Joaquin, incluida una sesión beta temporal y una descarga controlada. En
los APK finales: 804/804 JVM; Android dirigido, 21 aprobadas y una omitida,
cero fallos. Se comprobaron retorno/recreación y selector OEM real, cancelación,
guardado y relectura. La sesión temporal se retiró: 27 tablas, 250 archivos
privados/preferencias y permisos iguales a la entrada; QA instalada/detenida,
QA.test ausente. Sin cambios productivos durante esta QA.

Se acepta el cliente local y su checkpoint, no una distribución publicable ni
una actualización instalada entre versiones. Servidor HTTPS real, firma Release,
matriz adicional y actualización con datos poblados conservan sus puertas.
No se inició el bloque 29 ni se hizo push. El cierre vigente está en la última
sección; los dictámenes y limitaciones anteriores se conservan como historial.

**Primer dictamen, conservado como antecedente:** el candidato compila y sus pruebas existentes pasan, pero la revisión independiente
identificó dos fallos funcionales que todavía no tienen regresión. No se acepta
el bloque 28, no se crea checkpoint y no se habilita el bloque 29.

En la primera auditoría MAIN no modificó los 27 archivos entregados. Aquella intervención sólo incorporó
la auditoría y actualiza su estado en STATUS, índice y cabecera del prompt.

## QUÉ HACE / POR QUÉ EXISTE

Revisa el cliente de actualización manual, su frontera beta, la descarga y
verificación del APK, el guardado por documentos Android y el regreso al
contenido. Evita atribuir a una batería verde la corrección de recorridos que
esa batería no ejercita, o confundir guardar un APK con actualizar una instalación.

## BASE Y ALCANCE VERIFICADOS

- Checkout compartido: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD: `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35`, intacto.
- Base funcional padre: `40f2c55418a407751bd2afe9379262b0758baa44`.
- Upstream: `origin/codex/miguardia-2.0`, en
  `924fd46df018f5e14637dfe90a983fefdbf27af4`; diez adelante, cero atrás.
- Autor: `joaquin <blackat.systems@gmail.com>`.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`.
- `main`, `origin/main` y `v1.0.0^{}` permanecen en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Nueve worktrees preservados; staging vacío.
- Entrada especialista: cinco archivos modificados y 22 nuevos bajo `app/src`.
  MAIN leyó el diff completo y los 22 archivos nuevos, con tres revisiones
  auxiliares de sólo lectura: protocolo, archivos/SAF y UI/lifecycle.
- No hay cambios de producción en Room, esquemas, manifiesto, Gradle, website,
  Copias o Bloqueo. No apareció trabajo ajeno sin dueño en el candidato.

## FINDINGS

### [P2][CERRADO: JVM Y SAMSUNG CONTROLADO] Un reloj local atrasado rechaza un desafío válido del servidor

El diagnóstico siguiente corresponde a la entrega original. Su corrección se
verificó primero en JVM y después en la QA Samsung controlada documentada al final.

Superficies:

- `app/src/main/java/com/blackatsystems/miguardia/updates/UpdateProtocol.kt:98`.
- `app/src/main/java/com/blackatsystems/miguardia/beta/BetaCoordinator.kt:85`.

Ambas fronteras exigen que `expires_at` no supere el reloj civil del teléfono
más 300 segundos. El servidor real genera ese vencimiento desde **su propio
reloj**, con un TTL de 300 segundos (`website/lib/miguardia-beta/constants.ts:14`
y `service.ts:589`). Son orígenes temporales distintos.

Reproducción determinista del predicado, evaluada durante esta auditoría:

- Servidor: `2026-09-06T12:00:00Z`.
- Teléfono: `2026-09-06T11:59:30Z`.
- Vencimiento válido: `2026-09-06T12:05:00Z`.
- TTL del servidor: 300 segundos; restante según teléfono: 330 segundos.
- El predicado actual devuelve `false` antes de poder descargar.

Una latencia inferior a esos 30 segundos no evita el fallo. Corregir solamente
el parser no basta: la frontera de firma repite la misma restricción. No se
ejecutó una descarga remota ni se cambió el reloj de un teléfono; la evidencia
es el contrato del servidor, las dos condiciones productivas y su evaluación
determinista. Las pruebas actuales usan relojes alineados.

**Cierre exigido:** eliminar la dependencia de una sincronización civil exacta
sin cambiar el contrato API, la autoridad server-side de vencimiento, el tipo
de prueba firmada ni la identidad beta. No basta ampliar arbitrariamente el
límite en una sola capa. Agregar regresiones con desfase positivo y negativo,
latencia, vencimiento y cambios del reloj durante la operación; comprobar que
la solución sigue acotada y no escribe el registro beta.

### [P2][CERRADO: JVM, COMPOSE Y ACTIVITY] Volver de Actualizaciones puede repetir una navegación antigua

El diagnóstico siguiente corresponde a la entrega original. La lógica y sus
regresiones JVM, Compose y Activity quedaron verdes en las pasadas posteriores.

Superficies:

- `app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt:466`.
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt:857`.

Al abrir Actualizaciones se retira `MiGuardiaApp` de la composición. Al volver,
`SaveableStateHolder` restaura el destino, pero el `LaunchedEffect` de
`calendarNavigationRequest` vuelve a ejecutarse. Cualquier valor positivo vuelve
a seleccionar Calendario, aunque la petición externa ya se hubiera atendido.

Secuencia mínima deducida directamente del código:

1. Una entrada de aviso/Widget solicita Calendario y deja el contador positivo.
2. La persona navega después a Ayuda o Resumen.
3. Abre Actualizaciones y luego vuelve, sin recibir otro destino externo.
4. La raíz se recompone y el efecto antiguo reemplaza el destino restaurado
   por Calendario.

La implementación del contador y el efecto fueron inspeccionados; esta secuencia
todavía no fue ejecutada como regresión Compose ni como recorrido físico. Las
pruebas nuevas de menú no ejercitan un contador positivo previamente consumido.
La prueba Activity con raíz habilitada figura omitida en el registro final.

**Cierre exigido:** consumo idempotente de la petición, conservando destino y
mes cuando no hay una nueva. La solución debe sobrevivir a desmontaje y
recreación, sin confundir reinicio del contador con una petición nueva y sin
perder destinos nuevos recibidos mientras Actualizaciones está abierta. Agregar
regresiones para petición ya consumida, petición posterior, recreación y puertas
cerradas. No sortear Bloqueo o beta para demostrar el retorno.

## REPETICIÓN LOCAL DE MAIN

Comando ejecutado sobre el candidato sin modificar fuentes:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:testDebugUnitTest --rerun `
  :core:database:testDebugUnitTest --rerun `
  :app:testDebugUnitTest --rerun `
  :app:lintDebug :app:assembleDebug :app:assembleQa :app:assembleRelease `
  :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
```

**BUILD SUCCESSFUL en 1m23s; 351 tareas: cinco ejecutadas y 346 actualizadas.**
Se forzó la repetición de las tres tareas de pruebas; no se afirma que esta
pasada reconstruyera desde cero los 351 artefactos.

XML frescos comprobados después de la ejecución, entre 07:22 y 07:23 UTC:

| Módulo | Suites | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|---:|
| Dominio | 31 | 379 | 0 | 0 | 0 |
| Database JVM | 4 | 12 | 0 | 0 | 0 |
| App JVM | 57 | 398 | 0 | 0 | 0 |
| Total | 92 | 789 | 0 | 0 | 0 |

Lint y los cinco ensamblados quedaron verdes; lint conserva seis advertencias
de versiones en configuración sin cambios. Los cinco APK mantienen los SHA-256
del `app/build/updates28/final-audit.json` entregado. Ese inventario incluye
Release **unsigned**, no una APK oficial publicable.

MAIN comparó nuevamente los seis esquemas y el manifiesto fuente con los hashes
de esa evidencia: todos idénticos. Los contratos API/OPERATIONS del website
también conservan sus hashes. Website permaneció de sólo lectura.

La revisión de archivos no produjo un defecto concreto adicional en streaming,
límite de tamaño, hash, metadatos APK, comprobación de documento vacío, guardado
con/sin relectura o limpieza limitada a temporales propios. Esto no sustituye
la matriz de proveedores Android ni la comprobación final del instalador.

## EVIDENCIA FÍSICA HEREDADA, NO REPETIDA POR MAIN

MAIN leyó `app/build/updates28/device-tests-final.log` y el inventario final de
APK probados. El resultado dirigido es **15 aprobadas, una omitida, cero fallos**;
el `OK (16 tests)` incluye la omisión. Los dos hashes finales QA/test coinciden
con los artefactos presentes y con `device-final-tested-artifacts.json`.

La omisión corresponde a
`authorizedRootReturnsToHelpAndPreservesAll27TablesAndPrivatePreferencesWithoutStartingASearch`:
la sesión existente no habilitaba el contenido. No convertirla en prueba pasada.

Límites de esa evidencia:

- Cuatro clases dirigidas, no suite completa de app ni Room.
- Proveedor SAF local envuelto/injectado, no selector OEM completo.
- Preservación de las 27 tablas con datos existentes, pero sin jornadas ni fotos
  pobladas y sin comprobar continuidad de una activación beta real/Keystore.
- Doce composiciones revisadas, no rotación física ni TalkBack hablado.
- Ninguna instalación manual de una versión superior real.

MAIN no ejecutó ADB, no consultó paquetes ni usó Samsung/emuladores en esta
auditoría. Según la entrega, QA y QA.test quedaron instalados; el estado actual
del dispositivo no se verificó de nuevo. Una operación posterior sobre el
teléfono o una sesión sintética requiere su autorización propia.

## DEVOLUCIÓN CORRECTIVA ORIGINAL — YA ENTREGADA

Continuar **el mismo bloque 28**, no crear ni iniciar el 29. Los dos defectos
deben corregirse con regresiones, conservando el candidato y sin reimplementarlo.
Áreas previstas: protocolo de actualización, frontera beta, navegación y sus
pruebas correspondientes. Si resolverlos requiere alterar API, confianza,
seguridad, persistencia o política funcional, detener esa ampliación y decidirla
con MAIN/Joaquin antes de editar.

Después: revisión independiente de los fixes, batería local sobre sus fuentes
finales y nueva QA autorizada de los recorridos afectados. La Release remota real,
firma y actualización instalada con preservación de datos siguen siendo puertas
separadas; no se inventará una publicación ni se habilitará beta para cerrar tests.

La actualización documental no autoriza otra tarea, dispositivo, commit, push,
tag, firma, publicación o cambio de referencias protegidas. El candidato continúa
sin staging ni commit en el checkout compartido, sin nada para cherry-pick.

## REAUDITORÍA DEL CANDIDATO CORREGIDO — 2026-09-06

MAIN recibió `app/build/updates28/corrections/HANDOFF_A_MAIN.md` y contrastó
el delta real con el snapshot de devolución, sin tocar las fuentes.

### Alcance y preservación comprobados

- HEAD, rama, remoto y nueve worktrees intactos.
- Entrada: nueve rastreados modificados y 26 nuevos; cero staged/eliminados.
  Son 31 fuentes de app y cuatro documentos MAIN.
- Delta correctivo: 18 archivos. Catorce pertenecían al snapshot, uno es la
  adaptación de `NavigationDrawerComposeTest` y tres son nuevos: helper
  `UpdateTimeBudget`, regresión JVM `UpdateClockRegressionTest` y
  `UpdatesReturnComposeTest`.
- MAIN leyó los tres nuevos y todos los hunks correctivos. Trece fuentes
  originales no cambiaron. Los 18 hashes finales del delta coinciden.
- Los 27 archivos de la copia original conservan sus hashes. Antes de esta
  actualización documental, los quince hashes protegidos de la devolución
  también coincidieron: documentos MAIN, snapshot, Room 1–6, manifiesto,
  Gradle y contratos web. El website sigue de sólo lectura.

### Cierre local del reloj

`UpdateTimeBudget` se inicia antes de cada solicitud y vence al llegar a 300
segundos. Producción usa `SystemClock.elapsedRealtimeNanos`; pruebas inyectan
el reloj monótono independientemente del civil. La recepción no reinicia el
plazo; latencia, firma y streaming consumen presupuesto. El GET está acotado
por el restante de la autorización y conserva la desconexión por cancelación.

Parser y frontera beta ya no comparan el vencimiento del servidor contra la
hora civil del teléfono. La fecha UTC sigue validándose; `Date` no se exige.
El servidor conserva la decisión final de vencimiento, consumo y revocación.
Desafío, contexto y autorización quedan vinculados por operación y con consumo
único. No cambiaron identidad, confianza, bytes firmados, almacenamiento ni
política beta/feedback.

Las regresiones cubren desfases ±30/±3600 segundos, ausencia de `Date`, saltos
civiles, latencia/lectura/firma, límite 299/300, respuesta perdida, consumo,
rechazo remoto y preservación sin escrituras. Revisor independiente y MAIN
coinciden en el cierre de la causa; la prueba remota sigue sin ejecutarse.

### Cierre local del retorno

El productor `Long` se guarda/restaura en la Activity antes de capturar otro
destino; su incremento no hace wrap. El consumidor y el destino permanecen
guardables antes de los retornos tempranos de configuración/Ayuda. Sólo se
consume una secuencia mayor, con puertas habilitadas. El valor ya consumido
no fuerza Calendario al volver de Actualizaciones.

Las regresiones nuevas de Compose usan `SaveableStateProvider`, desmontaje,
remontaje y restauración, con Ayuda/Resumen, mes no actual, petición nueva y
puertas locales. La nueva Activity comprueba la restauración del productor.
La condición JVM fue ejecutada; estas cuatro nuevas pruebas Android quedaron
compiladas, no ejecutadas. El revisor independiente no encontró un defecto
concreto nuevo en el fix.

### Pruebas repetidas por MAIN

Dirigida, con repetición forzada de la tarea JVM:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:testDebugUnitTest --rerun --tests '*Update*Test' --tests '*BetaUpdateAccessTest' `
  :app:compileQaAndroidTestKotlin
```

Resultado: **49/49**, sin fallos, errores ni omitidas; 46 segundos,
79 tareas (una ejecutada, 78 actualizadas).

Luego se repitió el comando global de la sección `REPETICIÓN LOCAL DE MAIN`
de esta auditoría, forzando las tres tareas JVM: **BUILD SUCCESSFUL en 37s**,
351 tareas (cinco ejecutadas, 346 actualizadas). No se atribuye una
reconstrucción de 351 tareas a esta pasada.

XML frescos del 2026-09-06, 09:07–09:08 UTC:

| Módulo | Suites | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|---:|
| Dominio | 31 | 379 | 0 | 0 | 0 |
| Database JVM | 4 | 12 | 0 | 0 | 0 |
| App JVM | 58 | 413 | 0 | 0 | 0 |
| Total | 93 | 804 | 0 | 0 | 0 |

Se leyeron además los registros rojo/verde conservados por el especialista:
tres fallos esperados iniciales, después verdes. El rojo de navegación es
la condición extraída para JVM, no una ejecución Compose.

Lint y los cinco ensamblados quedaron verdes; los APK conservan los SHA-256
del handoff correctivo. QA: `0032565DB8CEF11FA9E42BAE5F1A7ACDFB5EC2B681438ABB61E6EA072965BDE3`;
QA.test: `005C6521C076B3B98CFA5E7DD34D02F5F5158CF7F0A8D98A217F7E0FF0895822`.
Release sigue unsigned. Evidencia MAIN ignorada:
`app/build/updates28/corrections/main-directed.log`, `main-global.log` y
`main-reaudit.json`. No se incorporan APK ni registros sensibles a Git.

### Próxima puerta

No se usaron dispositivos ni servicios remotos. Los 15 casos físicos aprobados
y uno omitido del candidato anterior no validan los nuevos APK.

Corresponde autorizar y ejecutar las regresiones Android sobre Samsung QA,
preservando su estado; después completar el retorno con raíz habilitada y
selector OEM. Si hace falta una sesión QA temporal o una fuente de descarga
controlada, debe autorizarse expresamente y no confundirse con beta real.
No se provisiona servidor ni confianza pública para cerrar una prueba local.

MAIN no crea el commit hasta resolver la puerta física del cliente. Firma,
actualización instalada entre versiones compatibles con datos poblados,
publicación y bloque 29 siguen separados. El próximo trabajo es QA del mismo
28, no repetir su implementación ni encadenar otra dependencia.

## CIERRE FÍSICO E INTEGRACIÓN DE MAIN — 2026-09-06

### Autorización, candidato y separación de evidencia

Joaquin respondió «autorizo» a la nueva QA del Samsung, incluyendo sesión beta
temporal y descarga controlada. Serial único `R5CY529W6PL`, `SM-S938B`, API 36.
Se reemplazaron solamente QA y QA.test con `install -r -t`, sin `-g`, sin borrar
datos, sin instalar el fixture ni consultar producción. La configuración laboral,
onboarding y Bloqueo existentes conservaron prioridad; no se salteó autenticación.

Las fuentes productivas corregidas no cambiaron durante esta pasada. MAIN cambió
únicamente la observación del estado en `UpdatesReturnComposeTest` y agregó
dos pruebas opt-in en `app/src/androidTest`: `UpdatesSupervisedActivityTest` y
`UpdatesOemSafActivityTest`. Las autoridades, transportes y claves efímeras de
ensayo sólo existieron dentro de instrumentación; no llegaron a los APK de app.
Tres revisores auxiliares participaron con responsabilidades acotadas; la revisión
final de guardas/restauración no encontró defectos bloqueantes. MAIN operó el
dispositivo y conservó la integración como único escritor final.

### Diagnósticos de las pruebas, corregidos sin cambiar producción

1. La primera ejecución dirigida dio 17 aprobadas, una omitida y dos fallos. El
   probe `issued` de `UpdatesReturnComposeTest` se actualizaba sólo en un
   `SideEffect`, pero la lectura de estado quedaba en la composición hija. El
   probe seguía en 1 aunque la petición real ya era 2. MAIN agregó una lectura
   explícita en la composición raíz; conservó todas las aserciones, sin esperas
   arbitrarias. La repetición dio 19 aprobadas y una omitida, cero fallos.
2. El primer harness supervisado lanzaba `ActivityScenario` con acción externa.
   Al consumirla, Main limpia legítimamente el intent; el observador de Scenario
   filtra callbacks por el intent inicial y perdía esa Activity. Se corrigió sólo
   el harness: lanzamiento normal y entrega posterior mediante
   `callActivityOnNewIntent`, verificando petición positiva consumida y acción
   sanitizada. Se conservó además la excepción original frente a errores de
   limpieza. Después pasó 1/1 y volvió a pasar en la ejecución final.

El segundo diagnóstico se contrastó con el
[código oficial de ActivityScenario](https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java).
Estos rojos son diagnósticos conservados, no fallos productivos omitidos ni parte
de los conteos finales verdes.

### Ejecución sobre los APK finales

| Grupo dirigido | Aprobadas | Omitidas | Fallos |
|---|---:|---:|---:|
| IO Android/inspección y proveedor de prueba | 4 | 0 | 0 |
| Menú/puertas Compose | 9 | 0 | 0 |
| Pantalla y matriz de composiciones | 1 | 0 | 0 |
| Activity con estado original | 2 | 1 | 0 |
| Regresiones de retorno Compose | 3 | 0 | 0 |
| Activity real con sesión temporal autorizada | 1 | 0 | 0 |
| Servicio controlado y selector OEM real | 1 | 0 | 0 |
| Total, sin sumar repeticiones diagnósticas | 21 | 1 | 0 |

`final-directed.log`: 20 aprobadas y una omitida, 30,809 s. El runner informa
`OK (21 tests)`, incluyendo la omisión. El caso omitido exige una sesión beta
preexistente y no se forzó a pasar; los dos harness autorizados comprobaron el
retorno con sesión habilitada sin conservarla. `oem-saf.log`: 1/1, 57,837 s.
Las cuatro regresiones Android nuevas del handoff correctivo sí se ejecutaron.
No fue una suite completa de aplicación ni Room.

La Activity real recibió una petición externa positiva, la consumió y después
volvió de Actualizaciones a Ayuda y Resumen, con y sin recreación, sin repetir
Calendario. Se comprobó nuevamente la restauración al recrear después del retorno.

Para el selector OEM se usaron `UpdateService`, Keystore, comprobante temporal,
inspector Android, streaming, SHA-256 y escritor de documentos productivos. Sólo
el transporte de los cuatro endpoints fue un doble en memoria, sin sockets,
servidor público ni cambio de confianza productiva. El reloj civil del servidor
de ensayo estuvo una hora atrasado; las cuatro peticiones consumieron la prueba
firmada y la autorización una sola vez. No se cambió hora ni zona del teléfono.

Recorrido real: Ayuda → Actualizaciones → Buscar → Descargar → Verificado →
selector Android cancelado → Verificado → segundo selector → Guardar → SAVED
con READ_BACK → Ayuda. Abrir la pantalla no inició peticiones. MAIN eligió
mediante controles observados de `com.google.android.documentsui`, sin inyectar
una URI o falsificar el callback de Activity. El documento nuevo quedó en la
carpeta temporal vacía `Download/MiGuardia-QA-Updates28-20260906`, luego retirada.

Fixture guardado: `MiGuardia-1.0.1.apk`, 8.602 bytes, SHA-256
`A4C4F6050A8215E7F7749DEAB3D6BBE0DEF3472F12C10C991B59437CD20600D6`, idéntico
al archivo de entrada y a la relectura. Su tamaño reducido prueba transporte e
inspección, no rendimiento de un APK completo. **Nunca fue instalado**. La
pantalla indicó correctamente que guardar no significa haber actualizado.

### Evidencia visual y límites de sistema

MAIN inspeccionó las capturas acotadas de la pantalla real: consulta, regreso a
Ayuda, APK listo, selector cancelado y guardado con relectura. No hubo recortes
que ocultasen controles relevantes. Las doce composiciones finales (claro/oscuro,
dos tamaños y zoom interno 100/150/200) fueron byte a byte idénticas a las doce
revisadas por el especialista; sus aserciones de visibilidad/scroll volvieron a
pasar. MAIN volvió a inspeccionar también la variante clara ancha al 200 %.

No se declara rotación física ni TalkBack hablado, ni compatibilidad de otros
proveedores SAF o APIs 26–27/33/37. No se consultó ni modificó `font_scale`,
densidad, tamaño visual, credenciales, hora o zona. El XML temporal del selector
se filtró en memoria para mostrar sólo controles y el nombre ficticio propio;
no se conservaron capturas del listado personal de Descargas.

### Preservación final y limpieza

Antes y después de los reemplazos y pruebas se compararon:

- 27 tablas, `quick_check=ok`, huella lógica
  `d796dc3a0edfaa93a93b5274779c3787af6ac930cf997273e83d2724d1024c58`.
- Los mismos 250 archivos de datos/preferencias, byte a byte, y permisos.
- Ausencia original de `no_backup/beta-v1.preferences_pb`, restaurada **después
  de detener QA**; autoridad sólo en memoria y ausencia del archivo SPKI de ensayo.
- Estado original del alias Keystore conservado: comparación de clave pública
  cuando estaba presente; retiro únicamente del alias propio si fue necesario
  crearlo para la sesión temporal.
- Delegado original del actualizador restaurado; las configuraciones de prueba
  desaparecieron al terminar el proceso.

La comparación externa final se ejecutó después de retirar QA.test y todos los
temporales propios: `sameDatabase`, `samePrivateFiles` y `samePermissions` son
`true`. Se excluyeron sólo cachés y los dos archivos administrativos Android ya
documentados (`files/profileInstalled` y `android.app.ActivityThread.IDS.xml`).
Había configuración, pero cero jornadas y fotos: esta evidencia no demuestra
actualización entre versiones con esos agregados poblados ni una beta pública.

Se retiraron 41 archivos de ensayo por nombre exacto en el teléfono, incluido el
APK externo después de verificar su hash; se conservaron los directorios de
caché preexistentes vacíos. QA quedó instalada/detenida y QA.test ausente. Tras
confirmar preservación se retiraron las ocho copias locales sensibles temporales
de SQLite/WAL/SHM y sus archivos tar; los originales del teléfono están intactos.
No hubo borrados recursivos ni limpieza de datos o cachés ajenos.

### Batería final después del último cambio de fuente

Se ejecutó nuevamente el comando global de esta auditoría, forzando las tres
tareas JVM: **BUILD SUCCESSFUL en 2m38s**, 351 tareas: **siete ejecutadas y 344
actualizadas**. No se presenta como reconstrucción íntegra con `--rerun-tasks`.

XML frescos 2026-09-06 09:49 UTC: 379 dominio, 12 database y 413 app; **804/804**
en 93 suites, cero fallos, errores u omitidas. Lint: cero errores y seis avisos
de versiones existentes. Debug, QA, Release unsigned y ambos APK de pruebas,
verdes. Room 1–6, manifiesto, Gradle, versión, paquete y certificado sin cambios.

| Artefacto final | SHA-256 |
|---|---|
| Debug | `F5B714221D60BC98C0EEAF8C395628067F9E96FBC0BEC8682D5797FBD3B5B82F` |
| QA probado | `0032565DB8CEF11FA9E42BAE5F1A7ACDFB5EC2B681438ABB61E6EA072965BDE3` |
| Release unsigned | `CE27ED3B2F7E6AFF72B5E87ADEA6D132B94926E7CE723B0CDF1AA6D7E0F5FE80` |
| QA AndroidTest probado | `5B7DFD56BD155B7B7637B8826739C7AC69E14282B66FD3EC572301FC8F790E29` |
| Database AndroidTest, no ejecutado aquí | `B6827A38006EB01ADBC3B6AF33460437BB194B93B415DDAE9A5AAC9413F9E315` |

Los APK QA/test después de la batería son idénticos a los de ambas ejecuciones
físicas finales. Firma QA conocida: `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
Metadatos todavía `versionCode=1`, `versionName=1.0.0`: este bloque no modifica
el versionado de distribución ni crea la clave Release.

Evidencia ignorada bajo `app/build/updates28/main-physical/`: `final-global.log`,
`final-evidence.json`, `final-directed.log`, `oem-saf.log`, `before/preservation.json`,
`after/preservation.json`, `cleanup-device.json`, `cleanup-local.json` y capturas
propias. Ningún APK, clave, log privado o copia de base se confirma en Git.

### Decisión de integración y siguiente puerta

MAIN acepta el cliente 28 como unidad local sobre la base `f5df1d7`: 33 fuentes
de app y cuatro documentos MAIN, 37 archivos (nueve modificados y 28 nuevos antes
de staging). No hay eliminaciones ni cambios fuera de alcance. El checkpoint
incluye el cliente, correcciones, regresiones y esta evidencia durable, conforme
a la autorización permanente de checkpoints locales después de una entrega
verificada. Push, tag, firma y publicación continúan separados.

Se aceptan explícitamente como pendientes de distribución: servicio HTTPS y
comprobante públicos, descarga de Release firmada real, actualización instalada
de código superior con datos/jornadas/fotos/identidad poblados, proveedores SAF
adicionales y matriz API/TalkBack indicada. Esta aceptación no reduce los controles
del cliente ni permite declarar el producto publicable. El nodo 29 sigue pendiente;
no se crea ni se despacha automáticamente otra dependencia.
