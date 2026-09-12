# 27 — Apoyar MiGuardia

Estado: **CERRADO / INTEGRADO LOCALMENTE — NO REEJECUTAR**.
MAIN preparó este contrato el 2026-09-05. Joaquin crea la tarea especialista;
este documento no abre una tarea ni autoriza iniciar el bloque 28.

Actualización MAIN del 2026-09-05: candidato recibido sobre `ef331e3`, diez
archivos de aplicación, sin commit. MAIN reprodujo y corrigió una apertura
tardía al pausar/reanudar durante el cierre del menú. El cierre inicial quedó
pendiente por QA beta cerrada. Con la autorización específica indicada abajo,
MAIN completó el 2026-09-06 navegador real y retorno, restableciendo después el
estado previo. 755/755 JVM y 32/32 casos físicos dirigidos verdes. No iniciar
otra implementación desde este prompt. Evidencia y verificación final en
`docs/audits/2026-09-05-apoyar-miguardia-android-v2-main.md`.
El contrato original se conserva debajo como trazabilidad del bloque integrado.

Autorización posterior de Joaquin a MAIN: sesión beta sintética temporal,
exclusivamente QA, para completar botón → página → Atrás preservando datos y
restaurando el estado anterior. Se permite un ensayo opt-in en AndroidTest con
autoridad sólo en memoria, sin cambiar producción, publicar la beta, borrar
datos laborales ni modificar Bloqueo/configuración/onboarding. Al terminar se
restaura la ausencia o contenido previo del almacenamiento beta, con el proceso
detenido, y se conserva cualquier identidad Keystore preexistente. Esta
excepción reemplaza sólo la prohibición de habilitar esa sesión QA; las demás
puertas del contrato continúan vigentes.

## QUÉ HACE

Agrega una entrada visible **Apoyar MiGuardia** en el menú de la aplicación.
Al tocarla, cierra el menú y abre en el navegador la página pública de aportes.
Se puede volver sin aportar y continuar usando MiGuardia normalmente.

## POR QUÉ EXISTE

La página de aportes ya está publicada, pero todavía falta su acceso desde
Android. Este bloque conecta ambas superficies sin incorporar pagos dentro
de la app ni confundir un aporte voluntario con el acceso a la beta.

## ROL

Sos la dependencia especialista 27 de MiGuardia, no MAIN. Implementás y
verificás exclusivamente este contrato sobre el checkout compartido. MAIN
audita tu handoff y decide documentación, staging e integración posterior.

## TASK

Implementar la acción de menú, su apertura externa segura, errores recuperables
y pruebas. No crear una pantalla de pagos ni una nueva pestaña principal.

## CONTEXT

- Android ya integra recuperación de Copias, Bloqueo, beta/opinión, configuración
  laboral, Ayuda, Calendario, Resumen, Widget y avisos.
- Su orden vigente es recuperación → Bloqueo → beta/opinión → configuración
  laboral → onboarding → contenido. Este bloque no cambia esa prioridad.
- La web pública está publicada en el Site existente, versión 10. La página
  `/miguardia/beta` es sólo un aviso cerrado: no entrega claves ni APK.
- La página de aportes explica voluntariedad, privacidad y la salida consciente
  a Mercado Pago. El aporte no habilita beta, funciones, prioridad ni derechos.
- Joaquin confirmó el receptor y aceptó las condiciones del proveedor. No
  corresponde reevaluar aquí comisiones, condiciones legales ni la cuenta.
- Android abre únicamente `https://blackatsystems.com/miguardia/apoyar`.
  La salida posterior al proveedor pertenece a la web y requiere otro toque.

## PUERTA 0

Antes de editar, verificá y registrá:

- Ruta: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- Base funcional y documental previa:
  `c2ab9c8edb50b7c29b69b4a5408e3ebbb76b72cf`.
- HEAD de entrada: **el checkpoint documental que agregó este prompt**, cuyo
  SHA completo MAIN incluye en el mensaje de entrega. Resolverlo también con:

  ```powershell
  git log --diff-filter=A -1 --format=%H -- docs/prompts/APOYAR_MIGUARDIA_EN_EL_MENU_ANDROID_V2.md
  ```

- HEAD debe coincidir con ese resultado y con el SHA recibido; su padre debe
  ser `c2ab9c8edb50b7c29b69b4a5408e3ebbb76b72cf`. El delta entre ambos contiene
  exclusivamente documentación. No usar una base histórica alternativa.
- Checkout limpio: cero modificados, nuevos no ignorados, staged o conflictos.
- Autor efectivo: `joaquin <blackat.systems@gmail.com>`.
- Origin: `https://github.com/blackat-systems/MiGuardia.git`.
- Upstream: `origin/codex/miguardia-2.0`, actualmente en
  `924fd46df018f5e14637dfe90a983fefdbf27af4`; después del checkpoint documental
  esperado: ocho commits adelante, cero atrás. No hacer fetch para alterarlo.
- `main`, `origin/main` y `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Nueve worktrees registrados, preservados; ninguna rama o worktree nuevo.
- Un solo escritor: vos sobre este checkout. MAIN conserva documentación;
  no coordines otra implementación paralela sobre los archivos compartidos.

Si diverge la fotografía o aparece trabajo sin dueño, detenete y devolvé la
evidencia a MAIN. No uses reset, clean, descarte, rebase ni cambios de identidad
Git para ocultar el problema.

## INPUTS Y LECTURA OBLIGATORIA

Leé completos y en el orden de `AGENTS.md`: ese archivo, MAPA, STATUS,
PLANIFICACIÓN, índice de prompts, prompt MAIN 2.0, ADR aplicables y prompt MAIN
histórico. Después leé este contrato y el código/pruebas relacionado.

Fuentes particulares:

- `docs/adr/0039-distribucion-beta-mediante-apk-firmada-y-web-limitada.md`.
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.
- `docs/audits/2026-09-05-publicacion-aportes-y-preparacion-menu-android-v2.md`.
- Los contratos cerrados de Bloqueo, Ayuda y beta sólo como referencia de sus
  invariantes, no como órdenes para reimplementar esos módulos.
- `MainActivity.kt`, las dos sobrecargas de `MiGuardiaApp`, el menú y sus tests;
  lifecycle de Bloqueo/beta y manejo de destinos pendientes, de sólo lectura.
- Referencia de apertura externa ya existente en `WeatherScreens.kt`, sin
  refactorizarla ni cambiar sus condiciones.

Website es **sólo lectura**: `C:/Users/Joaquin/Desktop/chatgptprojects/website`.
Su HEAD observado es `80ebafd9945f51b13b05c708eec50c22fdf0a52b` y conserva cambios
ajenos del portfolio. No limpiarlos ni incorporarlos. La fuente funcional
publicada es `341ea0ab2d34d31dbd8bcc75303877a7e186a496`, no el HEAD documental
posterior. Consultar `docs/MIGUARDIA_SUPPORT_OPERATIONS.md` sólo si hace falta
verificar el destino; no ejecutar fixtures, instalaciones o builds allí.

No se modifican horas ni vocabulario sectorial; no abrir reglas por rubro.
Contrato prevalente de esta dependencia: este prompt y la autoridad superior
vigente de MiGuardia. Una contradicción material se devuelve a MAIN.

## OUTPUT

Un candidato Android acotado, sin commit, con acción de menú funcional y
pruebas de apertura, privacidad, errores y retorno. Entregá inventario exacto,
evidencia local y física separadas, y pendientes explícitos.

## SCOPE — ARCHIVOS PERMITIDOS

Producción, únicamente:

- `app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt`.
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`.
- `app/src/main/res/values/strings.xml`.
- Opcional, si facilita probar el intent y su resultado:
  `app/src/main/java/com/blackatsystems/miguardia/support/SupportLink.kt`.

Pruebas:

- `app/src/androidTest/java/com/blackatsystems/miguardia/NavigationDrawerComposeTest.kt`.
- Nuevos `SupportMenuComposeTest.kt`, `SupportLinkInstrumentedTest.kt` y/o
  `SupportMenuActivityTest.kt` bajo ese mismo paquete de AndroidTest.
- Nuevos tests propios bajo `app/src/test/java/com/blackatsystems/miguardia/support/`.
- Ajustes mínimos de aserciones en `HelpComposeTest.kt`,
  `security/AccessLockComposeTest.kt` y `beta/BetaClosedRootTest.kt`, bajo
  AndroidTest, sólo si la nueva entrada cambia su inventario o permite una
  regresión necesaria. No relajar la seguridad para facilitar la fixture.

No es obligatorio crear todos los archivos opcionales. Elegí el conjunto
mínimo verificable. Cualquier archivo adicional requiere volver a MAIN.

## DEPENDENCIES

Completas: página pública de Aportes, menú actual, lifecycle de acceso y beta.
No depende de claves productivas, APK firmado, publicación de la beta,
actualizador ni pago de prueba. No adelantes ninguna de esas puertas.

## CONTRATO FUNCIONAL E IMPLEMENTACIÓN

### Menú visible y acción única

1. Una única entrada `Apoyar MiGuardia` en el grupo `Aplicación`, visible y
   alcanzable sin entrar en Ayuda, sin esconderla entre opciones avanzadas.
2. Reutilizar `DrawerAction`, `ApplicationDrawerActions`, `DrawerActionItem`
   y el patrón `openDrawerAction`. No crear un `MainDestination` de aportes.
3. Descripción breve que indique aporte voluntario y apertura en el navegador.
   Recursos localizados, glifo coherente, nombre accesible y testTag propio.
4. Conectar un callback sin argumentos por ambas sobrecargas de MiGuardiaApp.
   La frontera Android en MainActivity decide cómo lanzar el intent fijo.
5. Esperar el cierre real del drawer antes de lanzar. Un doble toque no debe
   abrir dos navegadores. La prueba debe observar el orden callback/cierre,
   no solamente comprobar el estado cuando Compose ya quedó inactivo.
6. Sólo abre por un toque explícito. No abre al componer, leer estado, restaurar,
   iniciar, reanudar, recibir un aviso o finalizar una autenticación.

### Frontera externa segura

- URL constante y exacta: `https://blackatsystems.com/miguardia/apoyar`.
- Intent `ACTION_VIEW` normal; no fijar el paquete de un navegador ni usar
  WebView, Custom Tabs, SDK de pagos o una biblioteca nueva.
- Sin query, fragmento, headers, referrer agregado, ClipData, permisos URI,
  identificadores beta, alias, tokens, extras privados o información laboral.
- No aceptar una URL entrante ni copiar extras del intent que abrió MiGuardia.
- No incluir la URL directa de Mercado Pago ni hacer redirecciones alternativas.
- Capturar ausencia de manejador y rechazo de apertura como error recuperable
  (`ActivityNotFoundException`/`SecurityException` según corresponda), con texto
  claro; MiGuardia permanece utilizable y sólo reintenta por otro toque.
- No consultar inventario de apps ni agregar `<queries>` o permisos. Para
  `ACTION_VIEW` no hace falta una búsqueda previa de todos los navegadores.
- No agregar comprobación de conectividad ni cliente de red. Si el navegador
  no tiene Internet, la persona puede regresar sin que MiGuardia cambie acceso
  o interprete el resultado como un pago.

### Retorno y puertas existentes

- Conservar el destino y mes actuales mediante el estado existente. El drawer
  permanece cerrado al regresar. No almacenar una acción pendiente de aportes.
- Revalidar que la aplicación siga en estado apto para contenido al ejecutar
  el callback: recuperación terminada, sesión autorizada, beta habilitada y
  superficie permitida. Si la puerta cambia durante la animación, cancelar la
  apertura; no reproducirla después de desbloquear o completar la opinión.
- No saltar Bloqueo, beta, configuración, onboarding o formularios protegidos.
- El regreso del navegador atraviesa el lifecycle normal: un plazo de Bloqueo
  vencido exige autenticar; la opinión beta que venza conserva su prioridad.
  No conceder períodos de gracia ni alterar callbacks de esos módulos.
- No marcar donación, pago, agradecimiento o beneficio por volver del navegador.
  No persistir contadores de clics ni preferencias de aportes.
- Aportes no añade red propia. Esto no prohíbe las reconciliaciones ya
  autorizadas de beta o clima que el lifecycle existente pueda ejecutar.

Referencias técnicas oficiales: [intents implícitos y errores de apertura](https://developer.android.com/training/basics/intents/sending)
y [abrir URLs sin ampliar visibilidad de paquetes](https://developer.android.com/training/package-visibility/use-cases#open-urls).

## DO NOT

- No modificar `core/**`, Room V6, esquemas 1–6, migraciones o escritores.
- No modificar producción de beta, Bloqueo, Ayuda, Widget, avisos, clima,
  Copias o sus preferencias; únicamente conectar la nueva acción autorizada.
- No alterar manifiestos, permisos, Gradle, dependencias, SDK, paquete o versión.
- No modificar website, APIs, D1/R2, claves, confianza, backend o publicación.
- No incluir pagos en la app, Billing, WebView, tracking, anuncios ni analítica.
- No condicionar uso, activación, opiniones, actualizaciones o datos al aporte.
- No realizar pagos, llenar datos financieros ni inspeccionar credenciales.
- No cambiar las 17 preferencias portables ni persistir estado nuevo.
- No editar documentación canónica, crear el bloque 28 o actuar como MAIN.
- No crear tareas, ramas, worktrees o especialistas adicionales.
- No hacer staging, commit, push, fetch, pull, merge, rebase, reset, descarte,
  tag, firma productiva o Release. MAIN integra después de auditar.

## VALIDATION

### Pruebas específicas

- Entrada única, grupo correcto, texto completo y acción ausente de pantallas
  cerradas. Navegación del resto del drawer sin cambios.
- Cierre anterior al callback; doble toque, recomposición, recreación y retorno
  sin aperturas duplicadas o espontáneas.
- Intent real con acción/URL exactas y sin datos adicionales prohibidos.
- Fallos de manejador/apertura controlados y reintento consciente.
- Destino y mes preservados; Bloqueo desactivado, plazo vigente y vencido;
  opinión beta que vence fuera de la app, y puertas de Ayuda/configuración.
- Consulta del menú sin escrituras en datos laborales ni activación. Usar
  spies/fingerprints sobre fuentes reales donde corresponda, no duplicar reglas.
- Claro/oscuro, retrato/paisaje, zoom **interno** 100/150/200, texto y semántica
  accesibles. No consultar o modificar escala o densidad del sistema.

Precauciones de fixtures: `SummaryActivityTest` y
`V2ReadyCalendarRecreationActivityTest` usan `clearAllDataForInstrumentation()`.
No ejecutarlas sobre QA con datos que deban conservarse. Además,
`markOnboardingCompletedForTest()` habilita una fixture beta: no demuestra por
sí sola una puerta cerrada. `BetaClosedRootTest` requiere un proceso nuevo sin
esa habilitación. Inspeccioná cada setup/teardown antes de seleccionar suites.
No contaminar producción con emisores o bypasses de prueba.

### Batería local final

Usar el wrapper, JDK/SDK existentes y Node compatible con
`--experimental-transform-types` (la base se verificó con Node 24.20.0).

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test :core:database:testDebugUnitTest `
  :app:testDebugUnitTest :app:lintDebug `
  :app:assembleDebug :app:assembleQa :app:assembleRelease `
  :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
```

Contar resultados desde XML de esta corrida, diferenciando ejecutado de
compilado. No reutilizar números históricos como si fueran nuevos. MAIN
ejecutó 47 pruebas dirigidas de destinos/Bloqueo/Ayuda/beta al preparar este
prompt; no fue una repetición de la batería global ni QA física del bloque 27.

Revisar todo el diff y nuevos; `git diff --check`; ausencia de secretos/logs;
esquemas 1–6 byte a byte contra HEAD; manifiesto/dependencias sin cambios;
APK, tamaño y SHA-256 finales. Release compilada permanece unsigned y no
publicable. Los artefactos quedan ignorados, nunca en Git.

### QA Samsung: autorización vigente y límites

Joaquin autorizó expresamente QA física y concedió permiso para las próximas
instancias. Esta continuidad permite probar **únicamente QA/test** del bloque
27 en Samsung `SM-S938B`, API 36, serial `R5CY529W6PL`. Anunciá antes qué se
instala y qué se prueba; conexión no equivale a ampliar estos límites.

- Usar un serial explícito y un dispositivo por vez. Verificar identidad y
  disponibilidad antes de operar; no usar emuladores como sustituto automático.
- Sólo APK exactos `com.blackatsystems.miguardia.qa` y `.qa.test`, nunca Debug
  productivo ni runners que instalen un host con el identificador productivo.
- Preservar la QA preexistente y sus datos. No desinstalar QA, no limpiar datos,
  no sembrar sobre datos ajenos ni simular una instalación vacía borrándolos.
- Si la sesión QA no permite la prueba, pedir intervención de Joaquin o dejar
  pendiente. No modificar su activación ni sus controles para abrir el menú.
- Abrir realmente la página propia mediante el nuevo botón y volver con Atrás.
  Comprobar conservación del estado, drawer, error controlado en pruebas,
  apariencia y puertas de retorno. No avanzar al proveedor ni efectuar pagos.
- No modificar red, hora, zona, credenciales, ajustes visuales o permisos del
  sistema. Probar falta de manejador/conectividad con dobles antes de inventar
  cambios del teléfono. Biometría/PIN sólo los opera Joaquin, sin registrarlos.
- Producción no se consulta, abre, instala, reemplaza, limpia ni desinstala.
  Reinicio físico y alarmas reales siguen siendo puertas separadas.
- Retirar sólo el paquete de pruebas introducido por esta ejecución, si no era
  preexistente. Conservar QA/datos, registrar su estado final y cualquier
  pendiente si una desconexión impide comprobarlo. No borrar otras aplicaciones.

Si no se puede completar, entregar **CANDIDATO LOCAL — QA FÍSICA PENDIENTE**;
no atribuir una compilación o evidencia vieja al APK final.

## STOP POINTS

Detenete ante mismatch sin dueño, contradicción material, validación roja no
resoluble dentro del alcance, necesidad de ampliar archivos/arquitectura,
modificar datos QA o producción, credenciales/costos, pago o publicación.
Un fallo local reproducible del bloque se corrige con su regresión antes de
repetir pruebas; no ocultarlo ni modificar otro módulo para sortearlo.

## DONE WHEN

La entrada existe exactamente una vez, abre únicamente la URL propia por toque,
permite volver sin aportar, conserva privacidad/estado y respeta todas las
puertas. Pruebas locales verdes y QA física proporcional sobre APK finales,
o limitaciones físicas explícitas sin declarar integración. El checkout sólo
contiene cambios permitidos, sin staged/commit, listo para revisión de MAIN.

## HANDOFF A MAIN

Entregar: **QUÉ HACE, POR QUÉ EXISTE, VEREDICTO, OBJECTIVE, CHANGES, FILES,
DECISIONS, VALIDATION, PHYSICAL QA, DEVICE SAFETY, PRIVACY, ROOM, RISKS,
PENDING, GIT y NEXT**. Incluir ruta, rama, base/HEAD completos, inventario,
pruebas/artefactos exactos y estado final de QA/test. Distinguir JVM,
AndroidTest compilado, instrumentación y recorrido real. No hay nada para
cherry-pick: MAIN recibe el mismo checkout compartido y decide su integración.
