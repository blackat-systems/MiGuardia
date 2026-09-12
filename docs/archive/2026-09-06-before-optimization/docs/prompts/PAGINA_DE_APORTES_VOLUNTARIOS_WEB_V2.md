# 26 — Página de aportes voluntarios

- Estado: **CERRADO / PUBLICADO — NO REEJECUTAR**.
- Preparado por MAIN: 2026-09-05.
- Proyecto de escritura: `C:/Users/Joaquin/Desktop/chatgptprojects/website`.
- Entrega original: candidato local sin commit; auditado e integrado por MAIN.
- Publicación pública autorizada y ejecutada después del cierre local: Site
  versión 10, fuente `website/341ea0a`; HTTPS 42/42. No habilita beta ni APK.
- Pagos reales y otra publicación: **NO AUTORIZADOS**. Android se prepara
  exclusivamente mediante el contrato separado del bloque 27.

## Cierre de MAIN

Integrado el 2026-09-05 en `website/eae7d54592e6d1cc58bccc1c288c59356ad2e03f`.
MAIN repitió 22/22 pruebas dirigidas, 64/64 globales, tipos, lint, base y build;
revisó navegación y accesibilidad en navegador. Sin defectos de código abiertos.
El 503 del portal local fue identificado como `CONFIGURATION_UNAVAILABLE`,
sin cambios en su implementación ni reinicialización de campaña o claves.
Zoom real 200 %, traza independiente de red y puertas externas siguen pendientes;
no bloquean este cierre local acotado ni se presentan como verificados.
Evidencia: `docs/audits/2026-09-05-pagina-aportes-voluntarios-web-v2-main.md`.
El contrato siguiente se conserva como histórico; no habilita implementación nueva.

La publicación posterior queda conciliada en
`docs/audits/2026-09-05-publicacion-aportes-y-preparacion-menu-android-v2.md`.
Las prohibiciones y pendientes de publicación del cuerpo original describen
la entrega local, no invalidan esa autorización posterior ya consumida.

## QUÉ HACE

Crea `/miguardia/apoyar`, una página de Blackat Systems en español e inglés
que explica cómo apoyar voluntariamente el desarrollo de MiGuardia. La persona
elige salir hacia Mercado Pago e ingresa allí el importe. Puede volver a la
presentación de MiGuardia sin aportar.

## POR QUÉ EXISTE

Separa el apoyo económico opcional del acceso beta, las opiniones y las
actualizaciones. Prepara un destino propio y estable para la futura entrada
`Apoyar MiGuardia` del menú Android, sin integrar pagos dentro de la app ni
recibir información laboral.

## ROL Y TASK

Sos la dependencia especialista 26. No sos MAIN. Implementá y verificá sólo
esta página en el sitio existente. No abras tareas, implementadores, ramas ni
worktrees; no tomes la integración o publicación a tu cargo. No hagas staging
ni commit, aunque exista una autorización general de checkpoints para MAIN.

El pedido de ejecutar este prompt autoriza código y QA web local, más una
comprobación pública de sólo lectura del enlace externo si resulta accesible.
No autoriza entrar a cuentas, completar un pago, modificar Mercado Pago,
aceptar condiciones, aprovisionar recursos ni publicar el sitio.

## CONTEXT

- El portal beta está integrado localmente en `website/b66c1f7`.
- La página pública `/miguardia` y su manejo de foco están integrados en
  `website/2799c47`. No hay que recrearlos.
- MAIN integró el bloque Android 25 en `3df5b43034521478ed803c3630a79507c9c0d719`:
  acceso beta y opiniones, 749/749 JVM y 71/71 casos finales Samsung.
- Esos resultados son evidencia de otros bloques, no pruebas de esta página.
- Android sigue sin confianza productiva y no se acredita una APK publicada.
- Joaquin eligió `https://link.mercadopago.com.ar/blackatsystems` y aportó una
  captura donde se ve `Ingresá el monto a pagar`, un importe editable y el
  nombre visible **BlackcatSystems**. No corregir ese nombre por analogía con
  la marca Blackat Systems ni presentarlo como titularidad legal verificada.
- La captura no muestra una operación completada ni prueba costos, límites,
  datos que recibirá el titular o disponibilidad futura del enlace. No se
  incorpora a Git ni se copia su sesión o información de cuenta.

## PUERTA 0 — OBLIGATORIA

Antes de editar, confirmá en vivo:

```text
Checkout website: C:/Users/Joaquin/Desktop/chatgptprojects/website
Rama: main
HEAD exacto: 2799c473877acff2f280759d6a3d398c9f39a455
Remotos: ninguno
Worktrees: uno, el checkout indicado
Staged: vacío
Tracked modificados preexistentes: 3
Untracked preexistentes: 8
```

`main` es aquí la rama del sitio, no la referencia protegida de MiGuardia.
Trabajá directamente en este checkout compartido; no lo limpies ni reconstruyas.

Los tres archivos ya modificados están **prohibidos para esta dependencia**.
Conservá exactamente su contenido de trabajo, no el de HEAD:

| Archivo | SHA-256 de entrada |
|---|---|
| `app/globals.css` | `E179E14FAE129171E24EEEFC417900EB87957314E73A38CF8A51FD7ACCFC2980` |
| `app/layout.tsx` | `FB757520882C51DE7E9E838A21EECCFB7959AD80F435500F62E8218AEB6D2E9D` |
| `app/page.tsx` | `6858CCFECB62D5B2C14DD8798AF577A6BC57FA380C801AE28CA5A8EF951914B5` |

Los ocho archivos no rastreados también son ajenos y deben quedar intactos:

| Archivo | SHA-256 de entrada |
|---|---|
| `.openai/site-build-2d30584.tar.gz` | `B7258803905D3A409665021DEFFE5EB1B8E74CD19CD39E357B22A9DA3F3211F6` |
| `.openai/site-build-397a7ed.tar.gz` | `AB79F935648EC0BBF5B4B4380344F63F9FEEC4FFAC154F6DB4C1E4D2495CA284` |
| `.openai/site-build-b43685f.tar.gz` | `F248DED2F20651CD08E5231318BED702AB0E4B32E799B3A2CF03DEF13AA52764` |
| `.openai/site-build-b83015d.tar.gz` | `8EE9F82362E1E33EA111FCB7CD032C58B9B6E3E4B95B3244C20F1B282A27EAF2` |
| `AGENTS.md` | `2DA478210F5BAABC1511BE1AA4BBFA9A4041DF8AB4D1BDEE72121A02D12A2BAC` |
| `INTERFACE_DESIGN/6d5ee1775add2c466b28f5ca73dfe979.jpg` | `0E51F256975D735ECB0A4D8DF33E979C03E5933FF0F191CE9486E28E3BA61418` |
| `INTERFACE_DESIGN/792a029c88125c99483e4be3d81bd2a1.jpg` | `48D83AABE8B454E944EB31CB11132B4FA172154C5C572AC47B3109A505FE934C` |
| `INTERFACE_DESIGN/f989614eacd34db177cecedbf59350e5.jpg` | `0E75F884D0EF20B2EB2748211C58889BD7903A58443976C441B95F55AE417216` |

Protecciones adicionales:

- `.openai/hosting.json`: SHA-256
  `9966872AAEB7C2B2DA571ADC4F93AAEBD23D56F34595D97ACC2D7918E3A502F0`;
  `project_id=appgprj_6a90fe153dac8191b8ea5984b4a7e871`, D1 `DB`, R2 `BETA_APKS`.
- `app/miguardia/MiGuardiaPage.tsx`: SHA-256 inicial
  `205E57DEA74904DFA480083309E42EB5D3A80B4EBA2D1AD1F281407FE7F72EFC`.
- `app/miguardia/miguardia.module.css`: SHA-256 inicial
  `0A6F3FC17992C62D2597C7A086D0A6059A48B236FA6C6EF4F6818B4BB606E534`.
- Los dos hashes anteriores verifican sólo la base de entrada: pueden cambiar
  por los hunks mínimos autorizados del footer. No son invariantes de salida.
  El resto de sus contenidos debe conservarse; revisá su diff completo.
- Los archivos nuevos autorizados abajo deben estar ausentes al entrar.
- Registrá también hashes iniciales de `package.json`, lockfile, APIs, contratos
  beta y configuración de herramientas; no deben cambiar al finalizar.

MiGuardia es exclusivamente fuente de lectura:

```text
Ruta: C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0
Rama: codex/miguardia-2.0
Base documental previa a este prompt: cba90ee698478502186cc38ac29f3125d122727b
main, origin/main y v1.0.0^{}: 82db6fd8eb2c511205968894dc9857a96b16ed20
```

Su HEAD de entrega debe ser el checkpoint documental que agregó este archivo,
resuelto dentro de MiGuardia con:

```powershell
git log --diff-filter=A --format=%H -1 -- docs/prompts/PAGINA_DE_APORTES_VOLUNTARIOS_WEB_V2.md
```

Comprobá que coincida con HEAD, que descienda de la base indicada y que el
checkout MiGuardia esté limpio. Nunca escribas allí, ni artefactos de pruebas.

Usá `rev-parse --show-toplevel`, `branch --show-current`, `rev-parse HEAD`,
`status --short --branch`, `diff --name-only`, `diff --cached --name-only`,
`ls-files --others --exclude-standard`, `worktree list --porcelain`,
`remote -v` y `diff --check` en los checkouts correspondientes.
No ejecutes fetch para esta verificación. Detenete ante cualquier diferencia
sin dueño; no uses reset, clean, descarte ni recuperación desde otro worktree.

## LECTURA OBLIGATORIA / INPUTS

Leé por completo los AGENTS aplicables, incluido `website/AGENTS.md`, y luego
las fuentes de MiGuardia en su orden obligatorio: MAPA, STATUS, PLANIFICACIÓN,
índice de prompts, prompt MAIN V2, ADR 0039 y prompt MAIN histórico como
referencia, nunca como permiso para reabrir V1. Este bloque no cambia reglas
sectoriales; no necesita reinterpretarlas.

Continuá con este contrato completo y con:

- `docs/prompts/PAGINA_PUBLICA_DE_MIGUARDIA_Y_ACCESO_A_LA_BETA_WEB_V2.md`,
  sólo como referencia cerrada, dentro de MiGuardia;
- en website: `package.json`, lockfile, `.openai/hosting.json`, `vite.config.ts`
  y `.gitignore`;
- `app/miguardia/MiGuardiaPage.tsx`, su CSS, `layout.tsx` y `page.tsx`;
- `app/miguardia/beta/BetaPortal.tsx` y los contratos
  `docs/MIGUARDIA_BETA_API_V1.md` y `docs/MIGUARDIA_BETA_OPERATIONS.md`;
- `tests/miguardia-public.test.mjs`, `tests/ui-contract.test.mjs` y el harness
  real de pruebas antes de ejecutarlo.

Usá las skills disponibles `sites-building` y `sites-hosting` porque el
proyecto contiene `.openai/hosting.json`. Leé sus instrucciones completas.
El alcance local explícito de este prompt excluye cualquier paso de publicación.
La tarea especialista es el único Site owner durante su implementación.
Para QA visual local usá la skill de control de navegador disponible y sus
herramientas documentadas; no sustituyas la inspección visual por regex.

## OUTPUT

Un candidato terminado localmente, sin commit, con:

1. Ruta `/miguardia/apoyar`, metadatos propios y contenido bilingüe.
2. Un enlace discreto desde el pie de `/miguardia`, sin tocar el inicio general.
3. Pruebas regresivas nuevas y validación web local.
4. Un documento operativo breve con destino, evidencia y puertas pendientes.
5. Handoff autosuficiente que distinga implementación, QA local, evidencia del
   proveedor y publicación. No declarar una ruta local como URL pública activa.

## SCOPE — ARCHIVOS PERMITIDOS

Única raíz de escritura: `C:/Users/Joaquin/Desktop/chatgptprojects/website`.
Las rutas siguientes son relativas a esa raíz.

Nuevos:

- `app/miguardia/apoyar/page.tsx`
- `app/miguardia/apoyar/layout.tsx`
- `app/miguardia/apoyar/SupportMiGuardiaPage.tsx`
- `app/miguardia/apoyar/apoyar.module.css`
- `tests/miguardia-support.test.mjs`
- `docs/MIGUARDIA_SUPPORT_OPERATIONS.md`

Modificaciones permitidas exclusivamente:

- `app/miguardia/MiGuardiaPage.tsx`: textos español/inglés del enlace de apoyo
  y ese enlace dentro del footer existente. No cambiar selector, foco,
  inventario, condiciones beta ni jerarquía de secciones.
- `app/miguardia/miguardia.module.css`: sólo ajustes de disposición/wrapping
  del footer si son indispensables para conservar acceso en móvil.

Los artefactos de ejecución pueden quedar en directorios ya ignorados del
sitio. Usá `website/work/stage26/` para temporales y evidencia local; verificá
que se resuelva dentro del sitio. No escribas en el temporal global ni en
MiGuardia, tampoco mediante procesos hijos o herramientas.
No amplíes esta lista silenciosamente: devolvé a MAIN una necesidad concreta.

## DEPENDENCIES Y CONTRATO FUNCIONAL

### Recorrido y URLs

```text
/miguardia → enlace discreto Apoyar MiGuardia
          → /miguardia/apoyar
          → acción explícita Aportar con Mercado Pago
          → https://link.mercadopago.com.ar/blackatsystems
```

URL propia prevista: `https://blackatsystems.com/miguardia/apoyar`.
No está acreditada como publicada por este contrato. El futuro botón Android
del bloque 27 abrirá esa URL propia, no directamente Mercado Pago.

- Un solo CTA primario de salida al proveedor en la página nueva.
- Usar ancla HTTPS normal, no checkout, iframe, WebView, SDK ni redirección
  automática. Abrir en pestaña nueva con `target="_blank"`,
  `rel="noopener noreferrer"` y política `no-referrer`; avisar de esa salida.
- No agregar query, fragmento, importe, UTM, código beta ni identificador al enlace.
- Ninguna conexión al proveedor antes del clic: sin fetch, preconnect,
  prefetch, recursos remotos de Mercado Pago o comprobaciones periódicas.
- Enlace claro de vuelta a `/miguardia`. Volver no acredita un pago ni cambia
  el acceso; no mostrar `pago recibido`, confirmación o agradecimiento condicionado.
- El enlace del footer público no es otra sección sustantiva. La sección
  final beta y su único CTA hacia `/miguardia/beta` permanecen intactos.
- No agregar aportes al formulario beta, sus consentimientos o sus API.

### Contenido y privacidad

La página debe explicar con lenguaje natural y sin presión:

- El aporte es voluntario y ayuda a sostener el desarrollo del proyecto.
- No es requisito para participar o usar MiGuardia; no compra plazas,
  funcionalidades, prioridad, atención especial, opiniones positivas o actualizaciones.
- El importe se ingresa en Mercado Pago. No ofrecer importes sugeridos,
  planes, suscripciones, beneficios ni cobros recurrentes.
- MiGuardia no procesa ni almacena datos de pago y no envía jornadas, lugares,
  notas, fotos, datos médicos o la identidad beta al proveedor.
- Al continuar se sale a un servicio externo con sus propias condiciones y
  privacidad. Mercado Pago procesa el pago y puede compartir información de
  la operación con quien lo recibe. No prometer anonimato o ausencia total de
  datos del aportante para Blackat.
- No afirmar `sin comisiones`, `recibimos el 100 %`, deducción fiscal,
  entidad benéfica, certificación, asociación oficial ni costos inventados.
- No recoger alias, correo, teléfono, monto, comprobante o estado del aporte.
  No existe formulario, historial, backend o registro de aportantes propio.

Usar `Apoyar MiGuardia` / `Support MiGuardia` y `aporte voluntario` /
`voluntary contribution`. Mantener la marca **Blackat Systems** de la web.
Si se menciona el nombre mostrado por el proveedor, transcribir exactamente
**BlackcatSystems**, explicando que es el nombre visible en el destino elegido;
no convertirlo en una afirmación legal de titularidad.

### Presentación

- Español argentino e inglés completos, sin mezcla ni contenido del otro idioma
  visible durante la hidratación. Reutilizar patrones visuales existentes.
- Hay selectores locales, no un proveedor global de idioma. No refactorizar
  la navegación, añadir rutas `/es` o `/en` ni persistir idioma con cookies o
  almacenamiento del navegador para resolver esta página.
- Interfaz breve: propósito, carácter opcional, salida y privacidad, CTA y vuelta.
  No recrear el inventario completo del producto o agregar otro formulario beta.
- Metadatos propios: title, description, canonical y ruta Open Graph correctos;
  no heredar un canonical `/miguardia` para `/miguardia/apoyar`.
- Sin imágenes nuevas, capturas inventadas, logos de pago descargados,
  librerías, fuentes nuevas ni refactors oportunistas.
- Semántica clara, un h1, foco visible, teclado completo, contraste mínimo
  AA, reflujo, movimiento reducido y sin trampas de foco. Si hay diálogo de
  idioma, el fondo queda inerte y al cerrarlo se restaura el foco correctamente.

## PROVEEDOR — EVIDENCIA Y PUERTA EXTERNA

Fuentes oficiales para verificar afirmaciones actuales, sin fijar tasas en la UI:

- [Mercado Pago: Tu Link](https://www.mercadopago.com.ar/herramientas-para-vender/link-de-pago).
- [Guía de Tu Link](https://vendedores.mercadolibre.com.ar/nota/genera-tu-link-y-cobra-de-forma-personalizada-y-segura?guideKeyId=GE37&moduleKeyId=MO121).
- [Costos de Link de pago](https://www.mercadolibre.com.ar/ayuda/27563).
- [Privacidad e información compartida](https://www.mercadopago.com.ar/privacidad/declaracion-privacidad/31274).

La captura aportada por Joaquin comprueba la presentación con importe elegido
y el nombre visible, no todos los pasos financieros. La comprobación automática
anterior no pudo abrir el enlace: no es evidencia de que esté caído o sea inválido.

Podés intentar una lectura pública del destino, sin ingresar importe ni avanzar
en el pago. Si exige autenticación o falla la herramienta, registrá el límite;
no accedas a la cuenta de Joaquin ni pidas credenciales. No guardes capturas de
sesiones, nombres personales o datos de pago dentro de la evidencia pública.

La página puede terminar como candidato local sin una transacción real. Antes
de publicarla o habilitar el bloque Android, MAIN y Joaquin deben cerrar:

- destino operativo y receptor previsto;
- costos y condiciones efectivos de la cuenta, y tratamiento de datos;
- condiciones aplicables al aporte voluntario, sin inventar asesoramiento fiscal;
- publicación autorizada y comprobación HTTPS de la página propia.

No abras esas puertas ni ocultes su estado pendiente. No afirmes que el botón
Android puede incorporarse ya sólo porque este candidato compila localmente.

## DO NOT

- No modificar los tres archivos sucios protegidos, AGENTS, tarballs o JPG.
- No cambiar `.openai/**`, bindings, recursos remotos, variables, secretos,
  campaña, plazas, claves, Release, R2, D1, reservas o feedback.
- No modificar API, `lib/miguardia-beta/**`, esquema, migraciones, fixtures,
  package.json, lockfile, dependencias, configuración, middleware ni cabeceras globales.
- No introducir analítica, logging de visitas/aportes, cookies, localStorage,
  sessionStorage, fingerprint, formulario de pago, webhooks o confirmaciones.
- No cambiar Android, Room, copias, beta, versiones, firma o documentación
  canónica de MiGuardia. No ejecutar Gradle, ADB, Samsung ni emuladores.
- No crear, guardar o desplegar una versión Sites; no publicar la página,
  aunque una instrucción general de hosting sugiera hacerlo.
- No ejecutar staging, commit, push, fetch, pull, merge, rebase, reset, clean,
  eliminación de trabajo ajeno o creación de ramas/worktrees/tareas.

## VALIDATION — OBLIGATORIA Y LOCAL

### Preparación segura

Antes de ejecutar herramientas, leer sus efectos y ubicar temporales, cachés
y registros dentro de `website/work/stage26/`. Usar el runtime instalado
compatible con `package.json` (Node >=22.13.0 y soporte transform-types).

En el proceso de pruebas fijar TEMP, TMP, TMPDIR, MINIFLARE_REGISTRY_PATH,
WRANGLER_LOG_PATH y caché de npm a subrutas absolutas propias del sitio;
WRANGLER_WRITE_LOGS=false. Verificar sus destinos reales. No reasignar HOME,
USERPROFILE o CODEX_HOME ni cambiar configuración global de herramientas.

`npm test` no carga `vite.config.ts`: no suponer que sus protecciones de
Miniflare ya están activas. Las pruebas usan D1/R2 efímeros, no necesitan
reinicializar datos existentes. **No ejecutar `fixtures:beta` con ni sin
`--fresh`**, ni `dev:beta`, que modifica material local de ensayo.

El build regenera su salida ignorada `dist` y puede descargar las fuentes
ya configuradas a `.vinext/fonts`. Es un efecto del build existente, no un
permiso para añadir fuentes o publicar. No borrar manualmente carpetas para
arreglar fallos. Si la herramienta exige escribir fuera de website, detenerla.

### Batería

Desde website, con el entorno local anterior:

```powershell
node --experimental-transform-types --test tests/miguardia-support.test.mjs tests/miguardia-public.test.mjs tests/ui-contract.test.mjs
npm test
npm run typecheck
npm run lint
npm run db:check
npm run build
git diff --check
```

Agregar regresiones para ruta/canonical, ambos idiomas, carácter voluntario,
destino HTTPS exacto, ausencia de parámetros e integración de pagos, políticas
del enlace, navegación de regreso, link discreto del footer y CTA beta único.
No debilitar pruebas existentes para admitir la nueva página.
Reportar conteos frescos, fallos y avisos; no heredar los 55 casos del handoff
de la página anterior. Comparar hashes y Git después de la batería.

### QA visual y navegación

Usar el servidor local existente o `npm run dev` en un puerto libre, sin
reinicializar campaña ni claves. No detener procesos ajenos o cambiar bindings.
Si faltan datos beta, reportarlo; no fabricarlos para esta página estática.

Recorrer `/`, `/miguardia`, `/miguardia/apoyar`, ida/vuelta al portal y regreso
desde la página de aportes. Confirmar que abrir la página no llama a Mercado
Pago y que su CTA sólo navega tras una acción explícita.

- Español e inglés; escritorio 1280×720 y móvil 390×844.
- Teclado, Tab/Shift+Tab, idioma, foco, regreso y nombre accesible del enlace
  que abre otra pestaña. Movimiento reducido y consola sin errores nuevos.
- Zoom real 200 % si la herramienta lo permite; si sólo admite viewport
  equivalente, informarlo como reflujo y conservar zoom real como pendiente.
- Contraste medido y ausencia de scroll horizontal no intencional.
- Portal y API sin cambios; CTA beta sólo en su lugar original.

Las pruebas de fuente no equivalen a esta QA. Si no puede recorrerse en un
navegador, entregar evidencia parcial sin declararla revisada visualmente.

## STOP POINTS

Detenete ante discrepancia de base/hashes, trabajo sin dueño, escritura fuera
de raíz, cambios de privacidad o servidor, necesidad de otra dependencia,
validación roja no atribuible al alcance, gasto, autenticación del proveedor,
pago real o publicación. Corregí únicamente defectos reproducibles dentro de
los archivos permitidos y repetí pruebas. Devolvé a MAIN cualquier ampliación.

## DONE WHEN

- Página bilingüe completa y alcanzable localmente, sin pagos embebidos.
- Voluntariedad, privacidad y límites correctos; un CTA externo exacto.
- Regresiones y build verdes, con QA visual distinguida de pruebas estáticas.
- No se modificó el portfolio preexistente, portal, Android ni hosting.
- Hashes de archivos no editables, HEAD y staging permanecen iguales a la
  entrada. Los dos archivos editables existentes sólo contienen el diff del
  footer permitido; sus hashes iniciales no se exigen iguales al finalizar.
- Documento operativo con URL propia prevista, proveedor, evidencia real,
  pendientes de costos/destinatario/condiciones y puerta de publicación.
- Candidato sin commit entregado a MAIN. Ninguna tarea posterior iniciada.

## HANDOFF A MAIN

Entregá en español: QUÉ HACE, POR QUÉ EXISTE, VEREDICTO, OBJECTIVE, CHANGES,
FILES, DECISIONS, VALIDATION, VISUAL AND ACCESSIBILITY QA, PORTAL REGRESSION,
PRIVACY, RISKS, PENDING, GIT y NEXT.

Incluí ambos HEAD, archivos propios frente a preexistentes, hashes preservados,
comandos y conteos reales, nivel exacto de verificación del enlace y todo lo no
ejecutado. El veredicto máximo es **CANDIDATO WEB LOCAL VERDE — NO PUBLICADO**.
MAIN auditará y decidirá el checkpoint; no existe nada para cherry-pick.
