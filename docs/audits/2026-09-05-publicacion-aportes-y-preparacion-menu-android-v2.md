# MAIN — Publicación de Aportes y preparación del menú Android

Fecha: 2026-09-05, Argentina.

## Resultado y alcance

Se reconcilió la documentación de MiGuardia con la publicación pública ya
autorizada y completada del Site. Se preparó exclusivamente el prompt
**27 — Apoyar MiGuardia**, sin implementar Android ni abrir una nueva tarea.

Esta pasada no publicó de nuevo el sitio, no operó dispositivos, no hizo pagos
y no abrió la beta. El checkpoint de cierre contiene sólo documentación.

## Puerta 0 y fuentes

- MiGuardia: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama `codex/miguardia-2.0`; entrada
  `c2ab9c8edb50b7c29b69b4a5408e3ebbb76b72cf`, checkout limpio.
- Upstream `origin/codex/miguardia-2.0` en
  `924fd46df018f5e14637dfe90a983fefdbf27af4`: siete adelante, cero atrás.
- Autor `joaquin <blackat.systems@gmail.com>`; origin privado autorizado.
- Nueve worktrees preservados; `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Leídos completos: skill `joa-main-orchestrator`, ciclo MAIN y plantilla,
  AGENTS, MAPA, STATUS, PLANIFICACIÓN, índice, prompts MAIN actual e histórico,
  orquestación y ADR aplicables. Luego código de menú/lifecycle y evidencia
  vigente de `website/docs/MIGUARDIA_SUPPORT_OPERATIONS.md`.
- Website consultado de sólo lectura en
  `80ebafd9945f51b13b05c708eec50c22fdf0a52b`; su suciedad conocida no se limpió.

## Publicación reconciliada: evidencia precedente, no nueva publicación

- Site: `appgprj_6a90fe153dac8191b8ea5984b4a7e871`, versión **10**.
- Versión: `appgprj_6a90fe153dac8191b8ea5984b4a7e871~appgver_0810db6bd33c8191a156d7c8b0d682f1`.
- Deployment: `appgdep_6a9c5136e4708191be150501c25b31cb`, terminal `succeeded`.
- Fuente publicada: `341ea0ab2d34d31dbd8bcc75303877a7e186a496`.
- El HEAD website `80ebafd...` es posterior y documental/de verificación;
  no se confunde con el artefacto publicado.
- Superficies públicas: inicio, `/miguardia`, `/miguardia/apoyar` y aviso de
  preparación cerrado en `/miguardia/beta`.
- Registro precedente: **42/42 controles HTTPS** y **72/72 pruebas locales**.
  No son una nueva corrida de esta preparación documental ni una QA visual
  posterior al despliegue.
- Ocho endpoints beta dan 404 en el perfil público. No se publicaron APIs de
  reservas/activación, claves, APK, bindings D1/R2, SQL o secretos.
- El portal local completo continúa separado. Su antiguo 503 local no describe
  el aviso público cerrado que ahora responde correctamente.
- Joaquin confirmó receptor `BlackcatSystems` y aceptación de costos/condiciones
  del proveedor. Es confirmación del titular, no auditoría legal de MAIN.

URL propia: <https://blackatsystems.com/miguardia/apoyar>.
Proveedor aprobado: <https://link.mercadopago.com.ar/blackatsystems>.
Android abrirá sólo la primera, sin parámetros ni datos de MiGuardia.

El hosting puede emitir `__cf_bm`, cookie anti-bots de Cloudflare con atributos
Secure/HttpOnly. No equivale a una cookie de producto ni autoriza rastreo nuevo.
No se promete ausencia absoluta de cookies del proveedor de infraestructura.
Referencia: [política oficial de cookies Cloudflare](https://developers.cloudflare.com/fundamentals/reference/policies-compliances/cloudflare-cookies/).

## Verificación actual

Un GET público nuevo a la página propia devolvió **200**, mantuvo URL exacta y
contuvo el título de Aportes y el enlace del proveedor. No se avanzó un pago.
La apertura por herramienta web había sido rechazada por su mecanismo de
acceso; el GET alternativo verificó disponibilidad sin inferir una caída.
No se imprimieron cookies ni datos de cuenta.

Se repitieron regresiones dirigidas de Android sobre la base sin cambios de
producción, con Node 24.20.0 y los JDK/SDK existentes:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:testDebugUnitTest `
  --tests 'com.blackatsystems.miguardia.PendingMainDestinationTest' `
  --tests 'com.blackatsystems.miguardia.security.AccessLockSessionTest' `
  --tests 'com.blackatsystems.miguardia.ui.help.HelpCoordinatorTest' `
  --tests 'com.blackatsystems.miguardia.beta.BetaCoordinatorTest' `
  :app:lintDebug :app:assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL in 1m 17s`, 160 tareas: dos ejecutadas y 158 al día.

XML nuevos: destinos 6, sesión de Bloqueo 11, Ayuda 12, beta 18:
**47/47**, cero fallos, errores u omitidas. La tarea JVM sí se ejecutó.
Lint y ensamblado AndroidTest quedaron verdes con sus artefactos al día;
no se declara un análisis completo nuevo ni instrumentación.

Los esquemas 1–6 se comprobaron contra sus hashes vigentes sin cambios.
Room sigue V6, 27 tablas, identidad `7eb39f6fab5a44e69350e206716554be`.
No se modificaron código, manifiesto, dependencias, datos ni preferencias.

## Revisión de contratos y decisión

Una inspección independiente de sólo lectura confirmó los puntos de conexión:
`DrawerAction`/`ApplicationDrawerActions`, cierre del drawer antes del callback
y apertura desde MainActivity. No hace falta un nuevo destino, WebView, SDK,
permiso o estado persistido. Se conserva el orden de recuperación/Bloqueo/beta.

La revisión detectó dos precauciones incorporadas al prompt:

- El test de navegación existente no demuestra por sí solo el orden temporal
  cierre/callback; el nuevo contrato exige observarlo explícitamente.
- Algunas fixtures Activity limpian datos y activan beta sintética. No pueden
  ejecutarse automáticamente sobre QA preexistente ni probar con ellas una
  puerta cerrada. La autorización continuada de Samsung no autoriza borrados.

Se preparó el prompt completo con ruta/base, límites de archivos, URL exacta,
retorno seguro, pruebas, QA proporcional y handoff. La revisión independiente
final leyó el contrato y este registro completos: **sin defectos bloqueantes**.
Confirmó que el alcance permite probar sin ampliar producción, que la prueba
Activity preserva QA y que Puerta 0 identifica el checkpoint sin SHA
autorreferente. No ejecutó pruebas ni dispositivos ni modificó archivos.

MAIN revisó todos los hunks documentales y los dos archivos nuevos; no hay
placeholders, whitespace final ni cambios de código. Website conserva su HEAD
y sus tres archivos premodificados, con hashes idénticos a los de entrada.

## Git, pendientes y próximo paso

Cambios de esta preparación: MAPA, PLANIFICACIÓN, STATUS, ADR 0039, índice,
orquestación, actualización de los dos prompts web cerrados, este registro y
el único prompt nuevo de Android. Ningún archivo de aplicación cambia.

MAIN verifica diff completo y staging exacto antes del checkpoint documental.
El SHA de entrada de la dependencia es el commit que agrega el prompt y se
entrega completo en el mensaje de lanzamiento, evitando un hash autorreferente.

Pendiente: Joaquin crea la dependencia 27, el especialista implementa y valida,
y MAIN recibe su handoff antes de integrar. Samsung no se usó en esta pasada.
Actualizaciones, confianza productiva, firma, APK y apertura de campaña continúan
como puertas posteriores. No se preparó el bloque 28 ni se hizo push.
