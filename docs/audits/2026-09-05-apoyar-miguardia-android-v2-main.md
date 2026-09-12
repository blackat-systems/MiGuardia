# MAIN — 27 · Apoyar MiGuardia

Inicio: 2026-09-05. Cierre supervisado: 2026-09-06, Argentina.

## Veredicto

**APROBADO CON CORRECCIÓN — CERRADO E INTEGRADO LOCALMENTE.**
755/755 JVM y 32/32 casos físicos dirigidos finales. Recorrido real de navegador
y regreso verificado, sesión QA temporal restituida y datos conservados.
Sin push ni bloque siguiente. La evidencia inicial pendiente se conserva abajo
como historia, separada del cierre del 2026-09-06.

## Qué hace y por qué existe

Agrega la entrada visible Apoyar MiGuardia, cierra el menú antes de abrir la
página HTTPS propia y conserva las puertas de acceso y datos. Conecta la
página publicada con Android sin pagos, beneficios o tracking dentro de la app.

## Puerta 0 y alcance

- Checkout compartido `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama `codex/miguardia-2.0`, HEAD de entrada y validación
  `ef331e3fb5dedf335ff796db2f7b39ae7467e972`.
- Padre `c2ab9c8edb50b7c29b69b4a5408e3ebbb76b72cf`; HEAD es el checkpoint
  documental que agregó el prompt.
- Upstream `924fd46df018f5e14637dfe90a983fefdbf27af4`: ocho adelante, cero atrás.
- Origin `https://github.com/blackat-systems/MiGuardia.git`; autor
  `joaquin <blackat.systems@gmail.com>`; nueve worktrees preservados.
- `main`, `origin/main` y `v1.0.0^{}` permanecen en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Entrada: cuatro rastreados modificados y seis nuevos, todos permitidos en
  `app/`. Sin staging, conflictos ni cambios ajenos documentales.
- Handoff original preservado en `build/support27/HANDOFF_A_MAIN.md` y sus
  registros ignorados. No se reemplaza su evidencia con resultados de MAIN.
- Skill MAIN y ciclo releídos; contrato del bloque leído completo. Autoridades
  documentales leídas al preparar el checkpoint anterior, sin delta al recibir
  este candidato; se refrescaron estado y contrato vigentes. No se trata una
  entrega ni evidencia de otra base como prueba de integración.

MAIN leyó los diez archivos, diff completo y contexto de gates/lifecycle.
No aparecieron cambios en Room, beta, Copias, manifiestos o dependencias.
El auditor independiente trabajó de sólo lectura y no ejecutó dispositivos.

## Hallazgo y corrección

**P2 — Toque pendiente sobrevive a pausa y retorno durante el cierre.**

En la entrega, `SupportMenuAction.open` esperaba `drawer.close()` y luego
comprobaba el estado actual `RESUMED`. Con Bloqueo desactivado, el cierre
podía quedar suspendido en segundo plano y completar después de volver, con
ese estado nuevamente verdadero. El toque anterior abría el navegador al
reanudar. El contrato exige cancelar, no reproducir esa acción.

MAIN agregó primero
`SupportMenuComposeTest.leavingAndResumingDuringCloseCancelsTheOriginalTap`:
composición real de menú, reloj detenido durante la animación y un
LifecycleRegistry que recorre pausa → stop → start → resume sin disponer la
composición. Ejecutada en Samsung con el candidato original: **1 prueba,
1 fallo**, `expected:<0> but was:<1>`. No es una inferencia ni un fallo ajeno.
Es un lifecycle controlado dentro de instrumentación, no un recorrido manual
del teléfono que abra el navegador.

Corrección mínima de MAIN, dentro de cuatro archivos ya permitidos:

- `SupportLink.kt`: token de generación por identidad y cancelación explícita.
- `MiGuardiaApp.kt`: invalidar en ON_PAUSE/ON_STOP/ON_DESTROY y al disponer el
  observador; quitarlo al salir de composición.
- `SupportMenuActionTest.kt`: cancelar el toque suspendido aunque el acceso sea
  válido al finalizar; un toque posterior sigue funcionando.
- `SupportMenuComposeTest.kt`: regresión del intercalado reproducido.

No se modifican Bloqueo o beta. La revisión independiente posterior no encontró
defectos concretos en el ajuste ni en el resto del contrato revisado.

## Verificación local

Antes del ajuste MAIN repitió las suites de Aportes, destinos, sesión de
Bloqueo, Ayuda y beta: tarea JVM ejecutada; `BUILD SUCCESSFUL in 49s`, 55 tareas
(una ejecutada, 54 al día). Estos resultados son anteriores a la corrección.

La verificación final fuerza la ejecución de las tres suites JVM, y revalida
lint y ensamblados sin reconstruir innecesariamente dependencias intactas:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:testDebugUnitTest --rerun `
  :core:database:testDebugUnitTest --rerun `
  :app:testDebugUnitTest --rerun :app:lintDebug `
  :app:assembleDebug :app:assembleQa :app:assembleRelease `
  :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
```

XML nuevos de la corrida MAIN: **755/755 JVM**, dominio 379, database 12 y app
364; cero fallos, errores u omitidas. Incluye cuatro pruebas de la acción y
dos de retorno. Marcas UTC entre `2026-09-06T02:41:44.208Z` y
`2026-09-06T02:42:45.389Z` (2026-09-05 en Argentina).
Resultado final: **BUILD SUCCESSFUL in 7m 9s**, 351 tareas, 32 ejecutadas y
319 al día. Las tres tareas JVM se ejecutaron forzadamente; no se presenta
esta corrida como una reconstrucción de las 351 tareas desde cero.
Lint: cero errores y seis avisos de versiones, fuera del diff. Debug, QA,
Release unsigned y ambos AndroidTest verdes; algunos artefactos intactos se
reutilizaron. No se declaran como propios los 754 JVM ni las 30 pruebas físicas
anteriores del especialista.

Artefactos de la batería local MAIN, ignorados por Git. QA AndroidTest fue
recompilado después para el ensayo supervisado; su hash final figura más abajo:

| APK | Bytes | SHA-256 |
|---|---:|---|
| Debug | 18553494 | `5E1588A4F4DB2D1BB08E25B78682FC264BB4163C69446DA8C92957CA4C911EAB` |
| QA | 18438526 | `C0061E372995F36E403F2C7AA0E1B6D84453972A41B1E447EADC4A5AB94D2805` |
| Release unsigned | 12682713 | `CEEB29A24915CB923D9A0C9714D4A8ED2A16B9DC5BB2CD717A39C4C0747E6BB3` |
| QA AndroidTest | 2884680 | `285F35395FEF9284004FD0FF541425AE89161B327EAF4B7AB3B2CF69CF4953B9` |
| Database AndroidTest | 4184074 | `B6827A38006EB01ADBC3B6AF33460437BB194B93B415DDAE9A5AAC9413F9E315` |

## QA inicial y límite encontrado — 2026-09-05

Samsung `R5CY529W6PL`, `SM-S938B`, API 36, verificado conectado y autorizado por
la continuidad expresa para QA. MAIN usó exclusivamente QA/test; sin consultar
producción, emuladores, pagos, red del sistema o credenciales.

La entrega tenía 30 casos aprobados y uno omitido. Se leyó el registro del
runner: `OK (2 tests)` de Activity contenía un aprobado y un supuesto omitido,
no dos aprobados. El hash de QA original coincide con el APK informado.

Causa de la omisión inicial: `BetaVariant` QA inicia con `BetaEnvironment()` sin
claves; `BetaCoordinator.initialize()` publica UNAVAILABLE antes de aceptar
activación. Debug/Release también conservan confianza vacía. No es un defecto
del botón ni falta un código que la persona pueda ingresar en esa pantalla.

Comprobación acotada de existencia, sin leer contenido: no hay autoridad de
ensayo `beta-test-root-authority.spki` ni archivo `beta-v1.preferences_pb` en
el directorio no-backup de la QA actual. No se escribieron para habilitarla.
La fixture existente de activación está diseñada para datos QA descartables;
no se ejecutó sobre esta instalación preservada.

Pendiente material en esa primera pasada: definir y autorizar una sesión QA de ensayo acotada,
temporal y preservadora para completar botón → página propia → Atrás,
inspección visual, accesibilidad y puertas de retorno. No habilitar beta
productiva, crear claves públicas o limpiar datos como atajo.

### Instrumentación MAIN anterior a la sesión supervisada

La misma regresión que falló se repitió con la corrección: **1/1 verde**.
Después se ejecutó la matriz sobre los APK finales anteriores:

| Suite | Aprobadas | Omitidas |
|---|---:|---:|
| SupportLinkInstrumentedTest | 3 | 0 |
| SupportMenuComposeTest | 9 | 0 |
| NavigationDrawerComposeTest | 7 | 0 |
| HelpComposeTest | 7 | 0 |
| security.AccessLockComposeTest | 4 | 0 |
| SupportMenuActivityTest, proceso nuevo | 1 | 1 |
| Total único final | **31** | **1** |

Los primeros cinco grupos: `OK (30 tests)`, 24,131 segundos. La raíz real:
un aprobado, un omitido (`INSTRUMENTATION_STATUS_CODE: -4`), 0,633 segundos.
La reproducción roja y la repetición dirigida verde son diagnóstico; no se
suman al total único final. La corrección P2 queda **cerrada y verificada**.

Runner utilizado exclusivamente:
`com.blackatsystems.miguardia.qa.test/androidx.test.runner.AndroidJUnitRunner`,
con serial explícito `R5CY529W6PL`, filtro de esas cinco clases y una segunda
invocación para `SupportMenuActivityTest`. No se ejecutó el runner de database.

La raíz cerrada conserva fingerprints de las 27 tablas y archivo beta a través
de recreación, sin apertura espontánea. La prueba de raíz abierta vuelve a
omitir por falta de acceso, correctamente y sin modificarlo. Temas, espacios
retrato/paisaje y zoom interno se ejercitan dentro de Compose; no se atribuyen
a rotación manual, TalkBack o una inspección humana del recorrido real.

### Estado del Samsung al cerrar la primera pasada

QA preexistía y QA.test no preexistía al comenzar MAIN. Se actualizó QA con
`install -r`, sin limpiar ni desinstalar sus datos. Al finalizar MAIN retiró
únicamente `com.blackatsystems.miguardia.qa.test` y comprobó su ausencia.
QA queda instalada y detenida (`stopped=true`); sus datos se conservaron.
No se habilitó la autoridad de ensayo ni se creó el almacenamiento beta.
El paquete de pruebas puede reinstalarse desde su APK local. No se tocaron
producción, pagos, credenciales, conectividad ni ajustes visuales del sistema.

## Cierre supervisado — 2026-09-06

Joaquin autorizó explícitamente la sesión beta sintética temporal propuesta por
MAIN, exclusivamente en QA y preservando el estado anterior. El contrato 27
registra esa excepción; no es una habilitación general ni confianza productiva.
MAIN usó la skill `joa-main-orchestrator` para mantener la frontera de auditoría,
autoridad, evidencia e integración de este único bloque.

### Ensayo opt-in y revisión independiente

Se amplió únicamente `SupportMenuActivityTest.kt`, dentro de los diez archivos
permitidos. El nuevo método
`authorizedTemporarySessionOpensRealBrowserAndPreservesOwnerData` exige el
argumento `support27TemporarySession=authorized`, QA exacta, ausencia de datos
beta y autoridad de ensayo, Bloqueo ya abierto, configuración y onboarding ya
completos. Sin esa autorización se omite deliberadamente; no es una prueba
desatendida para cualquier instalación.

Usa el coordinador, almacenamiento e identidad Keystore existentes, con emisor
efímero y transporte doble en memoria del APK de pruebas. No usa la fixture
descartable que persiste una autoridad, no crea otro DataStore ni escribe claves
en Debug/QA/Release. Conserva la identidad preexistente; si no existiera, retira
solamente el alias que el ensayo creó. No lee ni exporta claves privadas.
Notificaciones y Widget se pausan, y el refresh de clima se cancela mientras se
usa esa sesión: este caso no certifica su publicación exterior durante la beta.

El revisor independiente de sólo lectura señaló reforzar la restitución ante
fallos y preservar el alias por existencia previa. MAIN corrigió esos puntos,
espera la cancelación del trabajo de activación y ejecuta todas las restituciones
aunque una falle. La revisión final quedó sin defectos bloqueantes conocidos.
La exclusión de metadata en la comparación se restringió a dos rutas exactas.

### Dos diagnósticos intermedios, no contabilizados como éxitos

1. La primera ejecución no avanzó la animación Compose antes de esperar al
   operador y agotó su plazo de 180 segundos. Además, la comparación incluía
   `shared_prefs/android.app.ActivityThread.IDS.xml`, cuyo contador del framework
   Samsung cambió al crear actividades. Se agregó `compose.waitForIdle()`.
   Reconstruir en memoria el valor previo de ese único contador reprodujo el
   SHA inicial; no se reescribió el archivo del sistema ni datos del usuario.
2. La segunda ejecución abrió Chrome y volvió correctamente, pero la comparación
   todavía incluía `files/profileInstalled`, un caché de verificación de
   compilación de AndroidX. Se confirmó su naturaleza en la
   [fuente oficial de ProfileVerifier](https://raw.githubusercontent.com/androidx/androidx/androidx-main/profileinstaller/profileinstaller/src/main/java/androidx/profileinstaller/ProfileVerifier.java).

El test final excluye sólo esas dos rutas completas de metadata, no categorías
ni nombres arbitrarios. No se descartaron errores de datos de MiGuardia para
volver verde una prueba. Ambos intentos se restituyeron y no suman al total final.

### Artefacto y matriz finales

No cambió producción después de la batería 755/755. Sólo se recompiló el APK
de instrumentación: `:app:assembleQaAndroidTest`, **BUILD SUCCESSFUL in 25s**,
87 tareas, cuatro ejecutadas y 83 al día.

- QA instalado: 18438526 bytes, SHA-256
  `C0061E372995F36E403F2C7AA0E1B6D84453972A41B1E447EADC4A5AB94D2805`,
  idéntico al artefacto de la batería local anterior.
- QA AndroidTest final: 2897498 bytes, SHA-256
  `CA4F1B75A89F80D43808864C1B37631803FEA40A30BC29B003D8234A70D2571C`.

| Ejecución dirigida sobre los APK finales | Aprobadas | Omitidas |
|---|---:|---:|
| SupportLinkInstrumentedTest | 3 | 0 |
| SupportMenuComposeTest | 9 | 0 |
| NavigationDrawerComposeTest | 7 | 0 |
| HelpComposeTest | 7 | 0 |
| security.AccessLockComposeTest | 4 | 0 |
| Sesión temporal supervisada, navegador real y retorno | 1 | 0 |
| Raíz cerrada en proceso nuevo después de restituir | 1 | 0 |
| Total dirigido | **32** | **0** |

El recorrido supervisado pasó en 35,816 segundos; las cinco clases se repitieron
después de la restitución, **30/30 en 23,575 segundos**. La raíz cerrada pasó
**1/1 en 0,446 segundos**. Cero fallos en estas ejecuciones finales. No se presenta
como suite global de toda la app ni se suma como test adicional el método de
intent interceptado que el ensayo supervisado invoca internamente.

El ensayo usa el mismo serial y runner QA. Filtro y argumento excepcionales:

```text
-e class com.blackatsystems.miguardia.SupportMenuActivityTest#authorizedTemporarySessionOpensRealBrowserAndPreservesOwnerData
-e support27TemporarySession authorized
```

En la pausa `BROWSER_OPENED_REVIEW_AND_CONTINUE`, MAIN comprobó la página real
y creó exclusivamente el marcador `cache/support27-supervised/browser-reviewed`
dentro de QA. No automatizar ese consentimiento sin revisar el navegador. Tras
terminar, MAIN detuvo el proceso antes de retirar el archivo beta sintético;
el DataStore vivo sólo podía restituir un registro vacío, no su ausencia previa.

### Recorrido y conservación verificados

- Entrada única y legible en el menú real; menú cerrado antes de salir.
- Chrome abrió `https://blackatsystems.com/miguardia/apoyar`, con identidad
  Blackat, título Apoyar MiGuardia y explicación de aporte voluntario.
- No se siguió el enlace de Mercado Pago ni el ofrecimiento del navegador de
  instalar el sitio. No se ingresaron importes ni datos ni se realizó un pago.
- Atrás volvió a Ayuda, menú cerrado. Recrear volvió a Ayuda sin abrir el
  navegador otra vez. El intent interceptado adicional fue exacto y sin extras.
- Las 27 tablas conservaron su fingerprint lógico. Los 250 archivos de datos y
  preferencias comparados conservaron el SHA-256 conjunto
  `5A12498A3163B869F5598B8ABB138A48AACD9EABB8C63761D581E507540ACAC4`.
- No se modificaron Bloqueo, onboarding o configuración para obtener acceso.
  El emisor doble recibió únicamente `/activations`; ninguna solicitud al
  servidor beta público ni envío de opinión.

Capturas de menú, página y retorno revisadas por MAIN y conservadas sólo en el
directorio local ignorado `build/support27/`. No se confirman imágenes ni bases
en Git. La revisión directa fue en retrato/oscuro; temas, espacios de orientación
y zoom interno 100/150/200 también están cubiertos por Compose, no por una
repetición manual de toda la matriz. TalkBack real, biometría humana y API
26/33/37 no se repitieron en este bloque mínimo; permanecen límites explícitos,
no fallos reproducidos de esta entrada.

### Restitución y estado final real

Se restauró el registro beta vacío, se canceló y esperó su trabajo pendiente,
se repuso la confianza vacía y se verificó la conservación de Keystore. MAIN
detuvo QA y retiró exclusivamente el archivo sintético
`no_backup/beta-v1.preferences_pb`, restaurando su ausencia original. Se verificó
que `no_backup/beta-test-root-authority.spki` nunca fue creado.

Se retiraron el directorio de evidencia temporal de esta prueba y sus capturas
temporales del teléfono; las copias locales ignoradas conservan la evidencia.
No se alteraron los dos cachés del framework para ocultar sus cambios.
Después de las regresiones y de la prueba en proceso nuevo, se volvió a detener
QA y se comprobó la ausencia de archivo beta, autoridad y carpeta temporal.

MAIN desinstaló únicamente `com.blackatsystems.miguardia.qa.test`, introducido
por este ensayo, y verificó su ausencia. Es recuperable reinstalando el APK de
pruebas compilado. QA preexistente quedó instalada y detenida (`stopped=true`),
con sus datos preservados. Producción no se consultó ni tocó. Sin emuladores,
alarmas reales, reboot, cambios de credenciales, conectividad o escala visual.

## Privacidad, persistencia y Git

Se comprobó el intent nuevo ACTION_VIEW a la URL exacta, sin extras, datos,
query, fragmento, SDK, WebView, pago ni permiso nuevo. La lógica laboral y
preferencias no cambian. Room V6 conserva 27 tablas; esquemas 1–6 coinciden
con sus SHA-256 conocidos y no hay diff de `core/**`.

MAIN acepta el candidato corregido y el cierre físico dentro del alcance del
bloque. El checkpoint local automático autorizado consolida el checkout original;
no hay cherry-pick ni cambios de rama. No se crea otra dependencia ni se prepara
actualizador, firma o publicación.

El diff final agrega a los diez archivos de aplicación seis documentos de MAIN:
STATUS, MAPA, índice, estado del prompt 27, orquestación y esta auditoría.
Todos tienen dueño identificado. La revisión final y el staging son exactamente
esas 16 rutas; se comprueba `git diff --cached --check` antes del checkpoint
`feat: add MiGuardia support menu entry`. El commit que contiene esta auditoría
identifica la integración, evitando una referencia autorreferente. MAIN verifica
después el árbol limpio y las referencias protegidas. No se autoriza ni ejecuta
push. El siguiente nodo propuesto es 28, actualización manual preservando datos,
que requiere otra indicación de Joaquin.
