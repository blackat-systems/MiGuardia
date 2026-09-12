# Dependencia 24 — Portal beta privado y claves de acceso

- Estado: **CERRADO / IMPLEMENTADO LOCALMENTE — SIN PROVISIÓN NI PUBLICACIÓN**
- Fecha: 2026-09-03
- Autoridad: decisiones explícitas de Joaquin y ADR 0039
- Proyecto de implementación:
  `C:\Users\Joaquin\Desktop\chatgptprojects\website`
- Proyecto MiGuardia de sólo lectura:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Base funcional MiGuardia:
  `924fd46df018f5e14637dfe90a983fefdbf27af4`
- Base Git del sitio:
  `b83015d4e48297b33a9c04ac854e89811405dde4`
- Checkpoint local del sitio:
  `b66c1f79c247872cf61a30db3af2274606956d30`

## QUÉ HACE

Construye, dentro del sitio existente de Blackat Systems, el portal local de la
beta privada de MiGuardia. Deja preparada la página de participación, las diez
plazas, la entrega individual de claves, el acceso privado al futuro APK y los
contratos que después usará la aplicación para activarse y enviar feedback.

Este bloque termina funcionando y probado en local. No crea recursos remotos,
no sube el APK, no genera secretos reales y no publica cambios en la web.

## POR QUÉ EXISTE

La pantalla Android no puede validar una clave de forma segura si antes no
existe un servidor con reglas exactas. Este portal fija esa autoridad primero,
sin incrustar las diez claves dentro del APK ni recopilar datos laborales.

Después de su cierre, una decisión posterior de Joaquin insertó primero la
página pública de MiGuardia. El bloque Android `Acceso a la beta y feedback a
los quince días` espera a que esa presentación quede cerrada.

## ROLE

Sos la única dependencia implementadora activa del portal beta. Trabajás en el
proyecto guardado `website` y sos dueño de ese Site durante esta tarea. El
repositorio MiGuardia es exclusivamente de lectura.

No sos MAIN de MiGuardia, no creás otras tareas y no modificás las fuentes
canónicas de MiGuardia. Entregás el diff sin commit para que MAIN lo audite.

## PUERTA 0

Antes de editar:

1. verificá la ruta exacta del proyecto `website`;
2. leé su `AGENTS.md` completo y respetá que toda escritura quede dentro de esa
   carpeta;
3. verificá rama `main` y HEAD
   `b83015d4e48297b33a9c04ac854e89811405dde4`;
4. verificá que no exista remoto Git configurado;
5. verificá `.openai/hosting.json` con el `project_id` existente y `d1`/`r2` en
   `null`;
6. confirmá que el sitio público existente es `https://blackatsystems.com` y no
   crees otro Site;
7. comprobá que el árbol preexistente coincide exactamente con la fotografía
   siguiente;
8. detenete ante cualquier diferencia o escritor concurrente. No uses reset,
   clean, checkout destructivo ni descartes trabajo.

### Fotografía preexistente que debe preservarse

Archivos rastreados ya modificados antes de esta dependencia:

```text
app/globals.css  E179E14FAE129171E24EEEFC417900EB87957314E73A38CF8A51FD7ACCFC2980
app/layout.tsx   FB757520882C51DE7E9E838A21EECCFB7959AD80F435500F62E8218AEB6D2E9D
app/page.tsx     BA917350CBDB6FE2F3510AF47D6004693FB64B8E9036315525FB96D92F812D69
```

Esos tres hashes son SHA-256 del contenido completo actual. El portal puede
integrarse semánticamente en esos archivos si es imprescindible, pero no puede
reemplazar ni perder el diseño existente.

Archivos no rastreados preexistentes que no se deben editar, borrar, mover ni
incorporar al staging:

```text
.openai/site-build-2d30584.tar.gz
.openai/site-build-397a7ed.tar.gz
.openai/site-build-b43685f.tar.gz
.openai/site-build-b83015d.tar.gz
AGENTS.md
INTERFACE_DESIGN/6d5ee1775add2c466b28f5ca73dfe979.jpg
INTERFACE_DESIGN/792a029c88125c99483e4be3d81bd2a1.jpg
INTERFACE_DESIGN/f989614eacd34db177cecedbf59350e5.jpg
```

`AGENTS.md` debe leerse, pero sigue siendo una excepción conocida de la
fotografía. Los paquetes de build y las referencias visuales no pertenecen a
esta dependencia.

## LECTURA OBLIGATORIA

En este orden:

1. `website/AGENTS.md`;
2. las skills `sites-building` y `sites-hosting`, incluidos los documentos que
   ellas exijan para capacidades, D1, R2, SQLite y autenticación;
3. `.openai/hosting.json`, `package.json`, lockfile y `vite.config.ts`;
4. `app/layout.tsx`, `app/page.tsx`, `app/globals.css` y componentes existentes;
5. desde MiGuardia, sólo lectura: `AGENTS.md`, ADR 0039,
   `docs/PLANIFICACION_MIGUARDIA_2_0.md`, `docs/STATUS.md` y este prompt;
6. documentación oficial vigente de Web Crypto, Cloudflare Workers/D1/R2 y las
   APIs realmente utilizadas.

## TASK

Implementar y probar localmente el portal beta y su contrato API v1 para diez
plazas, sin desplegarlo ni crear secretos o recursos reales.

## CONTEXT

- La web existente es un portfolio público y debe conservar su identidad.
- Hoy no hay D1, R2, API, formulario beta, claves, APK ni página de aporte.
- El portal será una capacidad server-side del Site existente.
- El APK se descarga antes de introducir la clave dentro de MiGuardia.
- Completar el formulario web habilita una plaza y muestra una clave una sola
  vez; el canje real ocurre después dentro de Android.
- El piloto tiene diez plazas exactas.
- La primera beta será `2.0.0-beta.1`, código `2`, pero todavía no existe el APK
  Release firmado.
- La clave de firma del APK no es una clave de acceso beta ni la clave privada
  que firma comprobantes.

## INPUTS

- Dominio canónico: `https://blackatsystems.com`.
- Ruta pública prevista: `/miguardia/beta`.
- Rutas API bajo `/api/miguardia/beta/v1/`.
- Campaña inicial: diez plazas y estado global `CLOSED` por defecto.
- Alias: obligatorio, elegido por la persona; no se exige nombre real.
- Contacto: opcional y sólo para responder.
- Términos y aviso de privacidad: textos versionados; la API debe exigir las
  versiones vigentes y registrar sus aceptaciones separadas.
- Feedback: categoría, área, mensaje de hasta 2.000 caracteres y consentimiento.
- Retención inicial definida en ADR 0039.
- El destino `Apoyar MiGuardia` se implementará en otro bloque cuando Joaquin
  proporcione o apruebe el proveedor y la URL final.

## OUTPUT

1. Página `/miguardia/beta` integrada con el sitio actual que explique:
   - que es una beta privada y gratuita;
   - que existen diez plazas;
   - que los datos laborales permanecen locales y no se usan para administrar
     la beta;
   - que el clima opcional conserva su excepción ya documentada;
   - que la activación inicial necesita Internet;
   - que a los quince días se solicita feedback honesto;
   - que una falla de conexión después de completar el formulario no secuestra
     los datos ni impide seguir trabajando;
   - que Android advertirá sobre la instalación desde fuera de Play;
   - versión, Android mínimo, tamaño y hashes sólo cuando existan metadatos
     Release confirmados; nunca inventarlos.
2. Formulario accesible con alias, aceptación separada de Términos, aviso de
   privacidad y compromiso de feedback. Ninguna casilla comienza marcada.
   La API vuelve a validar las tres aceptaciones y las versiones vigentes; una
   llamada directa que intente saltear la interfaz no reserva una plaza.
3. Asignación transaccional de una de diez plazas `AVAILABLE → RESERVED`, con
   código generado mediante CSPRNG y mostrado una única vez.
4. Código de al menos 80 bits de entropía, legible en grupos, conservado en el
   servidor sólo mediante HMAC-SHA-256 con pepper secreto.
5. Sesión web breve mediante cookie `HttpOnly`, `Secure`, `SameSite=Strict`, sin
   código en URL, localStorage, HTML estático, logs o respuestas posteriores.
6. Descarga privada mediada por servidor y preparada para R2, con soporte de
   reintento y `Range`. Mientras no exista APK firmado, responde con un estado
   claro de no disponibilidad y nunca expone un objeto público.
7. Endpoint de activación atómico. Recibe código en POST y clave pública de la
   instalación; devuelve un comprobante firmado o un error genérico.
8. Idempotencia: misma plaza y huella de clave pública recuperan el mismo
   comprobante; otra instalación no consume ni reemplaza la plaza.
9. Endpoint de feedback idempotente asociado al identificador seudónimo de
   activación. Devuelve un comprobante firmado de recepción.
10. Endpoint de versión actual que más adelante permitirá ofrecer una
     actualización manual, sin entregar APK ni datos privados.
11. Autorización de descarga con dos entradas distintas y explícitas:
    - primera descarga: sesión breve creada únicamente por una reserva válida;
    - actualización: comprobante de activación más prueba de posesión de la
      clave privada de esa instalación mediante desafío firmado.
    Una llamada directa sin una de esas autorizaciones se rechaza. Si el plazo
    de quince días venció y el servidor todavía no confirmó
    `FEEDBACK_RECEIVED`, la versión instalada puede seguir usándose pero no se
    autoriza una descarga posterior. Una devolución sólo en cola local no
    cuenta como recibida por el servidor.
12. Contrato API durable y versionado con esquemas exactos de solicitud,
    respuesta, errores, límites, codificación y firma.
13. Modelo D1 con migración local para campañas, plazas, releases y feedback,
    incluyendo unicidad, estados y timestamps UTC.
14. Adaptadores de almacenamiento para D1/R2 y dobles locales de prueba. No
    escribas credenciales ni simules que los bindings remotos existen.
15. Utilidad operativa segura para preparar diez plazas de prueba y fixtures.
    No generes ni muestres claves reales de producción en esta tarea.
16. Pruebas unitarias, de integración y de interfaz para todos los contratos.

## CONTRATO API MÍNIMO

Las rutas pueden refinar nombres internos, pero el contrato público debe cubrir
estos comportamientos sin duplicarlos:

```text
POST /api/miguardia/beta/v1/reservations
GET  /api/miguardia/beta/v1/release
GET  /api/miguardia/beta/v1/download
POST /api/miguardia/beta/v1/activations
POST /api/miguardia/beta/v1/feedback
POST /api/miguardia/beta/v1/download-challenges
POST /api/miguardia/beta/v1/download-authorizations
```

Reglas:

- sólo HTTPS en producción;
- cuerpos JSON pequeños y límites explícitos;
- la reserva exige alias, aceptación explícita del compromiso de feedback y las
  versiones vigentes aceptadas de Términos y Privacidad; el servidor rechaza
  omisiones, valores falsos o versiones viejas antes de asignar una plaza;
- el código de acceso nunca viaja en URL;
- errores de clave inválida, usada, revocada o inexistente no revelan cuál fue
  el caso;
- tiempos relevantes nacen del servidor en UTC;
- `activated_at` y `feedback_due_at = activated_at + 15 días` forman parte del
  comprobante;
- comprobantes firmados con ECDSA P-256/SHA-256 y `kid` versionado;
- payload canónico documentado byte por byte para que Android pueda verificarlo
  sin biblioteca nueva;
- la clave privada de firma vive únicamente como secreto del servidor;
- CORS limitado al origen canónico para rutas web; las rutas Android no usan
  cookies y validan contenido, tamaño, método e idempotencia;
- protección CSRF para sesiones web;
- la primera descarga exige la cookie de sesión emitida después de reservar; no
  existe acceso directo anónimo al objeto;
- una actualización exige un desafío de servidor de uso único, el comprobante
  de activación y una firma verificable con la clave pública ya vinculada;
- las autorizaciones de descarga son breves, acotadas al Release e inútiles en
  una URL; los secretos o tokens no aparecen en logs;
- vencido `feedback_due_at`, sólo `FEEDBACK_RECEIVED` habilita una descarga de
  actualización. El estado local pendiente no se interpreta como recepción;
- límites de intentos sin huella del dispositivo ni analítica;
- ninguna respuesta incluye códigos completos salvo la única respuesta de
  reserva que acaba de generarlos;
- ninguna ruta recibe datos laborales.

## MODELO DE ESTADOS

Como mínimo:

```text
campaign: CLOSED | OPEN | PAUSED | FINISHED
slot: AVAILABLE | RESERVED | REDEEMED | FEEDBACK_RECEIVED | REVOKED
release: DRAFT | READY | PUBLISHED | WITHDRAWN
```

Toda transición se valida en transacción. Una plaza no vuelve a `AVAILABLE` de
forma automática: perder una clave o cambiar de teléfono requiere una decisión
operativa consciente.

La reserva registra de forma auditable las versiones aceptadas de Términos y
Privacidad y el instante de cada aceptación. No se reserva ni se crea una sesión
de descarga cuando falta cualquiera de las tres decisiones exigidas.

## PRIVACIDAD

Obligatorio:

- no pedir nombre legal, DNI, teléfono, empleador o ubicación;
- no recibir calendarios, horarios, lugares, notas, fotos ni datos médicos;
- no guardar IP como dato de producto ni construir fingerprint;
- no usar analítica, publicidad, píxeles, cookies de marketing o telemetría;
- no admitir adjuntos en la primera beta;
- contacto opcional en campo separado, con finalidad y eliminación claras;
- sanitizar y escapar alias y feedback; nunca renderizar HTML ingresado;
- mecanismo manual documentado para eliminar alias, contacto y feedback;
- no afirmar anonimato absoluto: alias, contacto opcional y logs inevitables del
  proveedor se explican con precisión.

## SEGURIDAD

- CSPRNG del runtime para códigos, IDs e idempotencia.
- HMAC con pepper secreto para buscar claves; no hash rápido sin secreto.
- Comparaciones constantes cuando aplique.
- Clave de firma de comprobantes separada del pepper, las claves beta y la
  futura clave Release del APK.
- Nada secreto en Git, `.openai/hosting.json`, cliente, bundles o mensajes.
- Cabeceras seguras, CSP compatible, protección contra XSS, CSRF y clickjacking.
- Límites de longitud, tipo, cantidad, tiempo y tamaño.
- Concurrencia real: once reservas simultáneas producen como máximo diez plazas.
- No implementar DRM, identificación del teléfono, cuentas, login social ni
  borrado remoto.

## SCOPE

Dentro de `website` podés modificar o crear únicamente lo necesario para:

- rutas `/miguardia/beta` y `/api/miguardia/beta/v1/**`;
- componentes y estilos propios de ese flujo;
- módulos server-side de campaña, plazas, comprobantes, feedback y release;
- migraciones D1 locales;
- interfaces/adaptadores R2;
- pruebas, fixtures y documentación técnica del contrato;
- integración mínima de navegación o metadata del sitio existente.

Si necesitás tocar `app/page.tsx`, `app/layout.tsx` o `app/globals.css`, preservá
semánticamente su contenido preexistente y detallá cada hunk en el handoff.

## DEPENDENCIES

- Sitio existente y fotografía de Puerta 0 coincidentes.
- ADR 0039 vigente.
- Node y dependencias actuales del proyecto disponibles.
- No requiere APK, firma ni recursos remotos para terminar el candidato local.

## DO NOT

- No crees otro Site ni otro proyecto web.
- No despliegues, guardes una versión remota ni modifiques el sitio público.
- No aprovisiones D1, R2, dominios, DNS, secrets o variables alojadas.
- No subas APK ni crees una URL de descarga pública.
- No generes las diez claves reales ni secretos productivos.
- No implementes todavía la pantalla Android.
- No implementes todavía `Apoyar MiGuardia` ni inventes un enlace de pago.
- No aceptes términos externos, contrates planes ni generes costos.
- No modifiques MiGuardia ni escribas fuera del proyecto `website`.
- No edites, muevas, borres ni confirmes los tarballs o imágenes preexistentes.
- No reemplaces el portfolio, su marca o su navegación por una landing genérica.
- No agregues cuentas, OAuth, analítica, publicidad, telemetría, CAPTCHA con
  seguimiento, Web3, DRM o huella del dispositivo.
- No guardes claves completas, secretos, datos laborales o credenciales.
- No hagas commit, push, reset, clean, rebase, merge, rama o worktree.
- No crees otras tareas ni subagentes que escriban el Site.

## VALIDATION

1. Build y lint del sitio con el gestor existente.
2. Pruebas deterministas de código/entropía y de que el código aparece sólo en
   la respuesta inicial.
3. Intentos directos de reserva sin una, dos o las tres aceptaciones, con
   valores falsos o versiones antiguas: todos rechazados sin plaza ni sesión;
   el caso válido registra versiones e instantes.
4. Once reservas concurrentes: exactamente diez éxitos y una respuesta
   cerrada, sin doble asignación.
5. Activación simultánea de la misma plaza: un único vínculo; mismo cliente
   recupera comprobante, cliente distinto recibe error genérico.
6. Firma/verificación de comprobantes, alteración, `kid` desconocido, payload
   excesivo y reloj del servidor.
7. Feedback duplicado/idempotente, límites, caracteres peligrosos y transición
   exacta a `FEEDBACK_RECEIVED`.
8. Campaña cerrada, agotada, pausada, plaza revocada y fallo de almacenamiento.
9. Primera descarga con reserva válida; llamada directa, sesión ausente,
   expirada o ajena rechazadas; R2 simulado, `Range`, reanudación y objeto
   privado.
10. Actualización con comprobante y prueba de posesión válidos; desafío repetido,
    firma ajena, plaza revocada y Release distinto rechazados.
11. Antes del vencimiento se permite actualizar; después, sólo
    `FEEDBACK_RECEIVED`. Una devolución únicamente local no habilita descarga,
    aunque la aplicación instalada sigue fuera de esta restricción web.
12. CSRF, CORS, cookies, CSP, rate limit y ausencia de secretos en cliente/logs.
13. Tema claro/oscuro si el sitio los ofrece, móvil/escritorio, teclado, foco,
    200 % de texto, estados vacío/carga/error/éxito y contraste.
14. Regresión completa del portfolio actual.
15. Búsqueda de secretos, claves, APK, datos privados y artefactos generados.
16. `git diff --check` y auditoría de todos los archivos nuevos/no rastreados.
17. Comparación final con la fotografía inicial para separar cambios previos y
    cambios del portal.

Separá en el handoff:

- `VERIFICADO LOCALMENTE`;
- `COMPILADO`;
- `PROBADO EN SERVIDOR LOCAL`;
- `NO APROVISIONADO`;
- `NO DESPLEGADO`;
- `PENDIENTE`.

## STOP POINTS

Detenete con `MAIN BLOQUEADA` si:

- Puerta 0 o la fotografía preexistente no coinciden;
- otra tarea está escribiendo el sitio;
- el trabajo exige crear o modificar recursos remotos;
- el framework no permite mantener D1/R2/secretos fuera del cliente;
- hace falta guardar códigos completos o exponer el APK;
- aparece una decisión de privacidad, costo o producto no definida;
- falla una validación obligatoria;
- el portfolio preexistente no puede preservarse.

## HANDOFF A MAIN

Entregá exactamente:

```text
# HANDOFF A MAIN — Portal beta privado y claves de acceso

## QUÉ HACE
## POR QUÉ EXISTE
## VEREDICTO
## OBJECTIVE
## CHANGES
## FILES
## API CONTRACT
## DATA MODEL
## SECURITY AND PRIVACY
## VALIDATION
## SITES AND HOSTING
## RISKS
## PENDING
## GIT
## NEXT
```

El primer renglón del veredicto debe ser:

`PORTAL BETA LOCAL VERDE — SIN RECURSOS REMOTOS NI PUBLICACIÓN`

o, ante un bloqueo real:

`MAIN BLOQUEADA`

## DONE WHEN

- El portal local explica la beta y permite recorrer la asignación consciente
  de una de diez plazas con fixtures.
- El contrato API v1 queda exacto, documentado y probado.
- Reserva, activación y feedback son atómicos e idempotentes.
- Términos, Privacidad y compromiso de feedback se validan también en servidor;
  no pueden saltearse llamando directamente a la API.
- Los comprobantes firmados pueden ser verificados después por Android.
- La primera descarga y las actualizaciones tienen autorizaciones separadas; el
  vencimiento del feedback bloquea sólo la actualización hasta que el servidor
  confirme su recepción.
- D1 y R2 tienen modelos/adaptadores y migraciones locales sin provisionarse.
- No se filtran códigos, secretos, datos laborales ni identificadores del
  dispositivo.
- El portfolio y su diseño preexistente siguen funcionando.
- Build, lint, pruebas y auditorías están verdes.
- No hubo deploy, recurso remoto, secreto real, APK, aporte, Android, commit ni
  push.
- El checkout queda con un diff atribuible y handoff suficiente para la
  auditoría independiente de MAIN.
