# Dependencia 25 — Acceso a la beta y opiniones

- Estado: **CERRADO E INTEGRADO LOCALMENTE; NO REEJECUTAR; SIN PUBLICACIÓN**
- Fecha: 2026-09-04.
- Autoridad: aprobación de Joaquin para este prompt y ADR 0039.
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- Base de preparación: `2bc803b24625c3e63fd4efaec6c6daae0e08e681`.
- Nombre breve para la tarea: **25 — Acceso a la beta y opiniones**.

MAIN cerró el bloque con correcciones el 2026-09-05: 749/749 JVM, 351/351 tareas
y 71/71 casos físicos finales en Samsung API 36. La confianza productiva sigue
ausente; API 26/33/37 y los recorridos humanos no ejecutados quedan explícitos
en la [auditoría de MAIN](../audits/2026-09-05-acceso-beta-y-opiniones-android-v2-main.md).
El contrato de implementación siguiente se conserva para auditoría, no para
abrir otra ejecución de esta dependencia.

## QUÉ HACE

Agrega dos recorridos propios de MiGuardia: ingresar el código recibido desde
la web para habilitar esta instalación y dejar una opinión honesta después de
quince días. Después de activar, la app funciona sin conexión. Si una opinión
completada no puede enviarse, se guarda con seguridad y la persona puede seguir
trabajando mientras el envío queda pendiente.

## POR QUÉ EXISTE

La web ya administra las diez plazas y emite comprobantes. Falta que Android
los compruebe y respete, sin incrustar códigos en el APK, transmitir datos
laborales ni perder información por una caída de Internet. Este bloque conecta
ese contrato con la app; no publica la beta ni promete impedir toda copia del APK.

## ROLE

Sos la única dependencia implementadora de este bloque. Trabajás en el checkout
compartido de MiGuardia. No sos MAIN y no abrís otra tarea, agente, rama o
worktree. Entregás el candidato sin staging ni commit. MAIN audita, integra,
registra y crea el checkpoint después de comprobarlo.

`website` es una dependencia de sólo lectura. Sus cambios preexistentes son
ajenos a tu tarea. No reconstruyas código desde worktrees históricos.

## TASK

Implementar el cliente Android del contrato API v1 de activación y feedback,
su identidad de instalación, comprobantes verificables, almacenamiento no
portable, UI, cola de envío e integración con todas las puertas actuales.
Agregar pruebas de seguridad, interoperabilidad y regresión. No basta una pantalla
demostrativa: el cliente y las barreras productivas deben quedar implementados.

## PUERTA 0 — BASE REPRODUCIBLE

La base funcional integrada es
`924fd46df018f5e14637dfe90a983fefdbf27af4`. El HEAD de preparación documental
es `2bc803b24625c3e63fd4efaec6c6daae0e08e681` y no contiene otra evolución
funcional. MAIN entrega además el SHA del checkpoint documental de este prompt.

Para evitar escribir dentro de un commit su propio hash, el punto de entrada se
identifica sin ambigüedad mediante el commit que agregó este archivo:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git log --diff-filter=A -1 --format=%H -- docs/prompts/ACCESO_A_LA_BETA_Y_OPINIONES_ANDROID_V2.md
git rev-parse HEAD^
git rev-parse '@{upstream}'
git rev-list --left-right --count '@{upstream}...HEAD'
git rev-parse main origin/main 'v1.0.0^{}'
git status --short --branch
git diff --cached --name-only
git ls-files --others --exclude-standard
git worktree list --porcelain
git remote -v
git var GIT_AUTHOR_IDENT
git diff --check
```

Exigencias al iniciar:

- `HEAD` debe coincidir con el commit que agregó este prompt y con el SHA
  entregado por MAIN; su primer padre debe ser `2bc803b24625c3e63fd4efaec6c6daae0e08e681`.
- La diferencia padre→HEAD debe contener sólo documentación; checkout limpio,
  staging vacío y ningún escritor simultáneo.
- Upstream `origin/codex/miguardia-2.0` en
  `924fd46df018f5e14637dfe90a983fefdbf27af4`; se esperan 3 adelante y 0 atrás
  después del checkpoint documental.
- `main`, `origin/main` y `v1.0.0^{}` permanecen en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Remoto privado `https://github.com/blackat-systems/MiGuardia.git` y autor
  `joaquin <blackat.systems@gmail.com>`.
- Nueve worktrees registrados; preservarlos, sin usarlos para esta implementación.
- Si el estado difiere, no inventar un descendiente válido: informar la diferencia
  y detenerse antes de editar. Nada de reset, clean, descarte o reescritura.

## CONTEXT Y LECTURA OBLIGATORIA

Leé por completo y en el orden de `AGENTS.md`: AGENTS; MAPA, STATUS y
PLANIFICACIÓN; índice de prompts; MAIN V2; ADR aplicables; MAIN histórico;
contratos del módulo; código y pruebas relacionados. Aplican especialmente
ADR 0032, 0035, 0036, 0037 y 0039. No cambia ninguna regla sectorial.

Después leé este prompt y, desde
`C:\Users\Joaquin\Desktop\chatgptprojects\website`, exclusivamente como entradas:

- `AGENTS.md` y los dos documentos `docs/MIGUARDIA_BETA_API_V1.md` y
  `docs/MIGUARDIA_BETA_OPERATIONS.md` completos;
- `lib/miguardia-beta/crypto.ts`, `encoding.ts`, `validation.ts`, `service.ts`
  y sus pruebas para comprobar bytes, límites y estados reales.

Website: rama `main`, HEAD `2799c473877acff2f280759d6a3d398c9f39a455`, sin
remoto. Portal integrado originalmente en `b66c1f79`; página pública integrada
en `2799c47`. No recrear ninguno. Se esperan cambios ajenos en `app/globals.css`,
`app/layout.tsx`, `app/page.tsx`, cuatro tarballs sin seguimiento, `AGENTS.md` e
`INTERFACE_DESIGN/`; no editarlos, borrarlos, incorporarlos ni ejecutar fixtures
que reinicialicen ese sitio.

Huellas SHA-256 de los contratos de entrada, bytes del checkout sin normalizar:

```text
MIGUARDIA_BETA_API_V1.md    2b821d750008bc449e491a25e0cfd8c1e900c06be8deb831fee4c5eefd6dd7b0
MIGUARDIA_BETA_OPERATIONS.md 9d647ddd6c8da633a51771bbee231770973793f6e8f389f72029bd3113571d61
```

La página local y el backend no acreditan servidor remoto disponible, anillo
de claves productivo ni APK oficial firmado. Esa ausencia **no bloquea implementar
y probar localmente** mediante transporte y emisor de ensayo aislados. Tampoco
autoriza generarlos, abrir plazas o cambiar el backend.

## INPUTS

- Android nativo Kotlin/Compose, DataStore, coroutines, JCA y HTTPS ya disponibles.
- Room V6, 27 entidades; seis esquemas congelados en este bloque.
- `MiGuardiaApplication`, recuperación de Copias y `StartupRecoveryGate`.
- Bloqueo opcional de acceso y coordinador de destinos pendientes en memoria.
- `WorkSetup`, Ayuda y onboarding versionado, sin cambiar sus contratos.
- Runtimes, receivers, renderer, reconciliación y configuración exportada del Widget.
- `OpenMeteoForecastClient` como precedente de conexiones HTTPS inyectables,
  límites y timeouts, no como un cliente que se deba mezclar con la beta.
- Build actual: `versionName=1.0.0`, `versionCode=1`; la preparación de
  `2.0.0-beta.1`/código `2` sigue siendo otro bloque. No adelantar ese cambio.

## OUTPUT

1. Identidad privada EC P-256 propia de la instalación y verificador estricto.
2. Cliente real para `POST /activations` y `POST /feedback`, con transporte inyectable.
3. Estado observable independiente de recuperación y bloqueo local.
4. Puerta Android antes del contenido y de las acciones laborales.
5. Formulario obligatorio al vencimiento, cola durable y comprobante de recepción.
6. Estado visible de la beta y reintento consciente desde una entrada
   `Acceso a la beta` en el grupo `Aplicación`; no incluir aporte ni actualizador.
7. Barrera exterior para avisos y Widget cuando no hay activación válida.
8. Pruebas locales e instrumentadas, fixtures compatibles y handoff auditable.

## CONTRATO FUNCIONAL CERRADO

### Activación y uso cotidiano

- Orden: recuperación de Copias → Bloqueo local si está activo → beta/opinión
  vencida → configuración laboral → guía inicial → Calendario.
- La primera activación requiere Internet; no ofrecer códigos offline,
  autoactivación ni claves embebidas. Explicar el motivo y permitir reintentar.
- El código sólo permanece en memoria mientras se ingresa/canjea. No escribirlo
  en SavedStateHandle persistente, DataStore, URL, logs, crash reports o archivos.
  La rotación puede conservar el borrador en el ViewModel; una muerte de proceso
  puede pedir que se vuelva a ingresar sin regenerar la clave de instalación.
- Persistir y verificar el comprobante antes de abrir la puerta. Error de guardado,
  firma o Keystore no equivale a activación. Cancelación, doble toque y callbacks
  tardíos no abren una sesión ajena ni pisan un comprobante válido.
- Después de activar, no consultar periódicamente al servidor para permitir
  trabajar. Revocación remota no borra ni apaga una instalación ya autorizada.
- Almacenamiento corrupto, identidad ausente o confianza desconocida dan una
  puerta recuperable y genérica; nunca se reparan borrando trabajo ni regenerando
  silenciosamente una identidad ya canjeada. No existe recuperación automática
  por reinstalación; la reasignación sigue siendo operativa y externa.

### Opinión y reloj

- `feedback_due_at = activated_at + 1.296.000.000 ms`, quince días de 24 horas;
  no quince aperturas ni días civiles. Se usan instantes UTC y reloj inyectable.
- Al llegar al límite `now >= feedback_due_at`, pedir completar categoría, área,
  mensaje y consentimiento explícito, desmarcado por defecto. No exigir estrellas,
  aprobación, alias, contacto, ubicación o acceso al contenido laboral.
- Pantalla propia, no permiso de superposición ni notificación invasiva. No
  interrumpir a mitad de una transacción, exportación o restauración: terminar
  la operación coherentemente y mostrar la puerta en el siguiente punto seguro.
  Conservar borradores y destinos para la continuación.
- Evaluar en arranque/resume y en el límite mientras la app está visible; nada
  de alarmas, polling, servicio permanente o tarea exacta de quince días.
- El plazo firmado no se recalcula al cambiar hora o zona. Usar tiempo monótono
  para la espera en proceso; una vez observado el vencimiento, no deshacerlo por
  retroceso del reloj. No inventar DRM, reloj remoto periódico ni comprobación
  temporal invulnerable offline entre reinicios; documentar esa limitación.
- La API acepta una opinión temprana, pero este bloque no crea una campaña nueva
  ni cambia el compromiso: la exigencia automática comienza al vencer.
- Primero guardar atómicamente el envío confirmado (identificador, cuerpo
  normalizado y prueba reproducible); recién entonces se puede continuar si no
  hay red o falla el servicio. Si ese guardado falla, mostrar error y reintento,
  no afirmar que la opinión está a salvo.
- Envío en cola, en curso, recibido y error definitivo son estados diferentes.
  Muerte de proceso, respuesta perdida y doble toque preservan la misma operación.
  No cambiar el cuerpo ni generar otro identificador de un envío incierto.
- Una cola durable permite usar esta versión, pero no acredita recepción remota
  ni derecho a una actualización posterior. Sólo un comprobante válido acredita
  `recibida`. Esta tarea expone esa distinción, no implementa descargas.
- Reintentar al volver al primer plano, por acción explícita o al recuperarse la
  conexión mientras el proceso está activo. Serializar intentos, respetar
  `Retry-After`, desregistrar callbacks y evitar bucles. No pedir abrir la app
  cada cierto tiempo ni prometer envíos con el proceso muerto.
- No enviar automáticamente diagnósticos, base de datos, capturas ni campos
  laborales. Advertir que el texto libre no debe contener información privada.
  Tras recepción verificada, retirar el cuerpo ya enviado de la cola; conservar
  sólo comprobantes e identificadores/hash necesarios, no un historial de opiniones.

## CONTRATO CRIPTOGRÁFICO Y API V1

Base prevista: `https://blackatsystems.com/api/miguardia/beta/v1`.
Sólo dos POST dentro de este bloque; sin cookies, CSRF, reservas ni llamadas
automáticas a `/release` o rutas de descarga. HTTPS con validación TLS normal,
origen fijo, sin redirecciones que reenvíen cuerpos, timeouts y respuestas acotadas.

### Identidad y confianza

- Generar una sola clave `secp256r1` mediante `AndroidKeyStore`, privada no
  exportable. No condicionar la clave a biometría/credencial: la seguridad local
  de pantalla es otra capacidad. No exigir StrongBox, attestation o hardware
  particular ni agregar PIN propio.
- SHA-256 de los bytes DER SubjectPublicKeyInfo es la huella de instalación;
  no es el hash del texto Base64URL ni un identificador del teléfono.
- Mantener la misma clave al reintentar. No enumerar claves ajenas del Keystore.
- Verificar con un anillo de públicas confiables por `kid`, nunca una clave
  incluida por el propio mensaje o descargada sin autoridad previa. La privada
  emisora sólo existe en el servidor o en el proceso de pruebas aislado.
- Paquete y certificado se contrastan con la aplicación instalada. El código
  firmado de activación debe ser positivo y no superar el código instalado:
  un recibo anterior válido sigue funcionando tras actualizar con la misma firma.
  No exigir igualdad de versión para siempre ni admitir un recibo de otro paquete.
- Persistir el sobre firmado original: el servidor compara sus campos exactos.
  Rotación de `kid` no elimina públicas antiguas todavía necesarias.

Referencia técnica: [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
Operaciones criptográficas y red fuera del hilo principal.

### Sobre y payload

Sobre exacto: `contract_version=1`, `alg=ES256`, `kid`,
`signature_format=P1363`, `payload_base64url`, `signature_base64url`.
Su orden JSON no importa; campos desconocidos, duplicados o tipos incorrectos
no se aceptan. `kid`: `[A-Za-z0-9._-]`, 1–40 caracteres.

Base64URL sin `=`; SHA-256 en 64 hex minúsculas. Firma ECDSA P-256/SHA-256,
64 bytes `r || s`, no DER. Implementar conversión estricta donde la JCA del
dispositivo use DER; no asumir que `Signature` devuelve P1363 en toda API.
Comprobar rangos, ceros iniciales, signo y longitudes; no usar criptografía casera.

Payload UTF-8 exacto, una línea por campo en este orden, LF al final de cada
línea **incluida la última**, sin CR, BOM, duplicados ni campos adicionales:

```text
miguardia-beta-receipt-v1
receipt_type=ACTIVATION
contract_version=1
kid={kid}
activation_id={activation_id}
activated_at={activated_at}
feedback_due_at={feedback_due_at}
application_id={application_id}
release_id={release_id}
release_version_code={entero positivo}
release_certificate_sha256={sha256}
installation_public_key_sha256={sha256}
```

En producción `application_id=com.blackatsystems.miguardia`. QA prueba su propio
paquete y certificado mediante fixture aislada, nunca relajando la validación.
Fechas estrictas `YYYY-MM-DDTHH:mm:ss.sssZ`, instantes reales; validar coherencia
del plazo, `kid` del sobre/payload, identidad de instalación y números seguros.
El parser web existente no sustituye estas validaciones Android.

### Canje

Cuerpo: `access_code`, `installation_public_key_spki`. Límite 8.192 bytes.
SPKI Base64URL sin padding, 80–256 caracteres y P-256 válido. Código de hasta
80 caracteres, formato mostrado `MG-` más seis grupos de cuatro, alfabeto real
`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`. Aceptar edición/pegado y normalización del
contrato; no reemplazarlo por un alfabeto Crockford distinto.

Éxito HTTP 200: `activation_receipt`, `idempotent`, `request_id` de transporte.
Mismo código y huella recuperan el mismo recibo; otra instalación obtiene un
rechazo genérico. El servidor puede firmar la Release publicada actual: si no
es compatible con el build instalado, mostrar error recuperable, no modificar
la API para enviar campos nuevos.

### Envío de opinión

Cuerpo exacto, máximo 24.576 bytes: `activation_receipt`, `request_id`,
`category`, `area`, `message`, `consent`, `installation_signature`.

- Categorías: `BUG`, `USABILITY`, `IDEA`, `OTHER`.
- Áreas: `ACCESS`, `CALENDAR`, `HOURS`, `NOTIFICATIONS`, `WEATHER`, `BACKUP`, `OTHER`.
- Mostrar etiquetas españolas claras; enviar los valores exactos.
- `request_id` propio: ASCII `[A-Za-z0-9_-]`, 8–80, generado una sola vez y
  persistido antes del envío; no confundirlo con el UUID exterior de respuesta.
- Mensaje NFC y recorte ECMAScript de extremos antes de firmar; 1–2.000 unidades
  UTF-16. Permitir LF, CR y tab; rechazar otros `Cc`/`Cf`. Consentimiento `true`.
- Hash de UTF-8 de JSON compacto equivalente byte por byte a `JSON.stringify`
  en orden `category`, `area`, `message`, `consent`; sin escapes HTML arbitrarios.

Firma de instalación sobre este texto con LF final:

```text
miguardia-beta-feedback-proof-v1
activation_id={activation_id}
request_id={request_id}
body_sha256={sha256}
```

Éxito HTTP 200: `feedback_receipt`, `idempotent`, `request_id` de transporte.
Verificar firma y payload canónico de recepción:

```text
miguardia-beta-receipt-v1
receipt_type=FEEDBACK
contract_version=1
kid={kid}
feedback_id={feedback_id}
activation_id={activation_id}
request_id={identificador persistido de la opinión}
received_at={UTC}
body_sha256={hash del cuerpo confirmado}
```

También termina en LF. Exigir coincidencia con activación, identificador y hash
del envío local. HTTP 200 por sí solo no permite marcar recibido.

Errores: `ACCESS_DENIED` genérico; 429 respeta `Retry-After`; timeout/503 conserva
estado y permite reintento acotado. Errores de contrato/403 no crean un bucle.
`FEEDBACK_ALREADY_RECEIVED` no autoriza inventar un recibo: conservar estado
pendiente e informar necesidad de resolverlo. Puede existir un tombstone por
borrado de privacidad sin endpoint de recuperación. No crear endpoints nuevos.

## INTEGRACIÓN SIN ATAJOS

### Recuperación, navegación y privacidad exterior

- Estado beta separado de `StartupRecoveryGate`, `pausedForRestore` y Bloqueo.
  Una caída de Internet nunca se convierte en un error de Copias.
- `MiGuardiaApplication.onCreate` y `resumeRuntimes` hoy arrancan observadores y
  colas tras recuperar. Incorporar la autorización antes de fuentes y publicación
  laboral; no basta envolver el Composable de Main.
- `MainActivity`: puerta dentro de Bloqueo y antes de componer/evaluar contenido
  laboral; revisar `sensitiveContentReady`, efectos, refresh, retorno, destinos y
  apertura de configuración del Widget. Cero contenido detrás en semántica.
- Destinos sólo en memoria, con generación, revalidación antes/después de cada
  suspensión y consumo único. No abrir mapas, detalle o archivos desde un callback
  viejo. Conservar la prioridad posterior de WorkSetup/onboarding.
- `WidgetConfigurationActivity` exportada atraviesa las mismas puertas antes de
  leer/modificar una instancia. Cancelar conserva preferencias y resultado seguro.
- Sin comprobante válido, avisos y widgets existentes pasan a contenido genérico
  sin horario, ubicación, color laboral, clima, cuenta regresiva ni acciones
  laborales. Conservar preferencias e identidades; no borrar datos ni duplicar
  recordatorios. Reconciliar silenciosamente al quedar autorizado.
- Cubrir receivers, alarmas ya programadas, resumen de grupo, replays, renderer
  final y carreras de publicación. No perder mantenimiento del launcher
  (eliminación/remapeo/restauración de IDs) por retornar indiscriminadamente.
- Una opinión vencida o en cola **no invalida** un comprobante de activación
  válido. No extender el estado exterior genérico a esos casos ni apagar avisos.
- Recuperación y reparación temprana de Copias siguen funcionando sin beta/red.
  Combinar y Reemplazar conservan activación, clave, cola, Bloqueo y onboarding
  propios del dispositivo; una copia ajena nunca activa esta instalación.

### Almacenamiento y QA aislada

- DataStore privado exclusivo de beta, coherente y no portable. Sin nueva tabla
  Room, totales derivados, autenticación del bloqueo ni códigos en disco.
- No ampliar las 17 preferencias portables ni cambiar formato/MIME/cifrado de
  Copias. Probar también journal, reapertura y restauración con beta cerrada.
- Identidad, recibos, estado del plazo y cola durable tienen versión y validación;
  no confiar sólo en un booleano `activated=true` o `feedback_sent=true`.
- Permitir configuración por variante para públicas y transporte de pruebas.
  Las claves privadas de ensayo se generan en el proceso de pruebas, no se
  incrustan. No hay aceptación global de fixtures ni bypass en `main`/Release.
- QA termina en `.qa` y usa su firma actual. Debug comparte paquete productivo:
  compilarlo no autoriza instalarlo en el teléfono.
- Mientras falta confianza productiva, Release falla cerrada con configuración
  no disponible, sin aceptar claves QA ni autofirmar sus recibos. El cliente
  real, no un placeholder, debe quedar completamente probado con dobles.
- No añadir un ajuste, intent exportado o archivo editable por el usuario que
  permita elegir cualquier servidor/pública o saltarse beta en producción.

## SCOPE

Permitido, únicamente cuando se justifique por este contrato:

- Código nuevo bajo `app/src/main/java/com/blackatsystems/miguardia/beta/**`:
  modelos puros, firma, codec, almacenamiento, cliente, coordinador y Compose.
- Integración mínima en MainActivity, MiGuardiaApplication, MiGuardiaApp,
  WidgetConfigurationActivity, runtimes/receivers/renderer/presenter/reconciliador
  de avisos y Widget; sin cambiar sus reglas de selección de eventos.
- Fuentes de prueba/QA y configuración de confianza por variante;
  `app/build.gradle.kts` sólo para esa separación, no versiones/dependencias.
- Manifiesto sólo para `ACCESS_NETWORK_STATE` si se utiliza el callback nativo
  de conectividad para la cola; permiso normal, sin diálogo ni identificación de
  redes/personas. Registrar y justificar su delta. No cambiar componentes exportados.
- Textos/recursos de la nueva interfaz y tests JVM/AndroidTest afectados,
  incluidos fixtures de la suite existente para autenticación sintética aislada.
- Regresiones de Copias/arranque sin modificar su formato ni escritores.

Referencia: [estado de red Android](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
El callback sólo sugiere reintentar: no prueba que el servidor responderá.

## DEPENDENCIES

- ADR 0039 y API v1 estables; website permanece de sólo lectura.
- Recuperación, Bloqueo, configuración, Ayuda, Widget y avisos ya integrados.
- JCA/Keystore, HTTPS y DataStore existentes; no se necesita otra biblioteca.
- Firma APK, públicas productivas, despliegue, diez plazas reales y prueba
  extremo a extremo remota son dependencias de publicación posteriores, no
  motivos para aceptar inseguridad ni para detener la implementación local.

## DO NOT

- No modificar `core/domain`, `core/database`, Room V6, DAO, esquemas, migraciones
  ni fórmulas laborales. No recuperar V1 ni crear perfiles/sectores nuevos.
- No editar `docs/**`, ADR, AGENTS o documentación canónica; findings documentales
  vuelven a MAIN. No modificar website ni ejecutar allí fixtures destructivas.
- No agregar dependencias, cuentas, analítica, anuncios, SDKs, WorkManager,
  servicios permanentes, alarmas, permisos peligrosos, identificadores del teléfono
  ni reconocimiento de hardware. No relajar TLS/cleartext para QA.
- No emitir diez claves productivas, registrar secretos, cambiar sitio/DNS,
  provisionar D1/R2, abrir campañas, publicar, firmar ni instalar producción.
- No incorporar aporte/donación, su proveedor o botón, actualizador, descarga de
  APK, instalador, Play Store, nueva versión/SDK/applicationId, tag o Release.
- No instrumentar recibos, claves o mensajes mediante logs/capturas que los expongan.
- No commit, staging, push, reset, merge, rebase, descarte, rama ni worktree.
- No declarar backend remoto disponible o beta publicable por pasar pruebas locales.

## VALIDATION

Antes de implementar, escribir un plan breve con fronteras y pruebas. Auditar
errores y fuentes reales antes de tocar integración. Usar, como mínimo:

### JVM e interoperabilidad

- Activación válida, alterada, firma incorrecta, tipo/algoritmo/kid ajenos,
  paquete/certificado/huella/versiones incorrectos y fechas/plazo inválidos.
- Parsers estrictos: padding, UTF-8, CR/BOM, LF final, campos duplicados,
  reordenados, desconocidos y sobrecargas de tamaño. DER/P1363 malformado.
- Vectores compartidos con el contrato web: firma cruzada con Web Crypto/JCA y
  hashes iguales para tildes descompuestas, emoji, espacios Unicode, comillas,
  barra inversa, `/`, `<>&`, CR/LF/tab y UTF-16 límite. No duplicar el cliente
  Kotlin en el test como supuesto oráculo.
- Idempotencia y carrera de doble canje/confirmación; respuesta perdida,
  persistencia fallida, cancelación tardía, 403/409/429/503 y reintento estable.
- Antes, en y después de quince días; medianoche/zona, reloj que retrocede,
  recreación y reinicio del proceso. No modificar el reloj real del teléfono.
- Cola durable antes de liberar UI; recibo ajeno nunca marca recepción; repetir
  tras muerte confirma una sola opinión; estado en cola no habilita actualización.

### AndroidTest y regresiones de integración

- Keystore real en QA: generación, firma, reapertura y reutilización del mismo
  alias propio; no exportación de privada. Pública QA rechazada por configuración
  Release; inventario del APK sin privadas, códigos o bypass de pruebas.
- Instalación sin comprobante; activar con emisor sintético; abrir/reabrir sin
  red; formulario vencido, persistencia, reintento, confirmación y arranque frío.
- Main y actividad exportada cerradas sin semántica laboral; avisos/widgets
  genéricos y revalidación de publicaciones tardías. Destinos consumidos una vez.
- Copias/journal sin red ni activación; combinación/reemplazo no transportan beta;
  reparar Copias no inicia runtimes laborales prematuramente ni produce deadlock.
- Bloqueo, WorkSetup, onboarding, retorno SAF, navegación y cancelación intactos.
- No se envía ningún dato laboral: transporte espía debe afirmar exactamente
  las dos solicitudes admitidas; la activación no pide ubicación ni permisos
  de avisos/biometría. Consultas laborales no escriben.
- Adaptar fixtures explícitamente; nunca saltar globalmente la puerta productiva
  para hacer verde la suite. Mantener los asserts laborales anteriores.

Batería final serializada sobre el candidato final:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Extraer conteos de XML frescos por módulo, no de declaraciones `@Test` ni de
reportes conectados antiguos. Verificar diff, nuevos archivos, privacidad,
manifest merge por variante, artefactos, identidad y hashes intactos de esquemas
1–6 contra HEAD. No atribuir a este bloque una prueba heredada no repetida.

### QA física y límites de autorización

Joaquin dejó autorización de QA Samsung para las próximas instancias. Este
contrato la acota a `SM-S938B`, serial `R5CY529W6PL`, API 36, paquetes
`com.blackatsystems.miguardia.qa`, `.qa.test` y
`com.blackatsystems.miguardia.core.database.test`, con datos ficticios y emisor
de pruebas aislado. Un dispositivo diferente o una operación adicional no
hereda esa autorización. No hace falta usar un servidor productivo para esta QA.

- Verificar serial/modelo y estado QA antes de instalar/reemplazar. No limpiar
  ni desinstalar una QA preexistente con datos sin autorización específica.
- Usar APK exacto QA y test; no `connectedDebugAndroidTest` que pueda instalar
  un host con paquete productivo. No consultar ni abrir producción.
- Ejecutar instrumentación afectada y regresión de raíz, Bloqueo, Ayuda,
  avisos/Widget y Copias; informar exclusiones de alarmas reales.
- Recorrer activación, cierre/reapertura offline, formulario a plazo inyectado,
  cola y reintento; claro/oscuro, retrato/paisaje, zoom interno 100/150/200,
  teclado, textos, focos y accesibilidad. No consultar ni modificar font_scale,
  densidad, tamaño visual, hora, zona ni redes del sistema para forzar casos.
- Credenciales/biometría las introduce Joaquin; nunca capturarlas. Describir
  qué exige intervención humana en lugar de simularla como validación física.
- Retirar sólo paquetes test introducidos por esta ejecución; preservar QA
  preexistente y documentar qué queda instalado y si está abierto o detenido.
- Emuladores, descarga de imágenes, reboot y alarma exacta real son puertas
  separadas. Probar API 26/33/37 sólo si MAIN acredita autorización vigente para
  esa ejecución; si no, dejar la matriz pendiente, no presentarla como ejecutada.

Si Samsung no está disponible, completar todo lo local y entregar instrumentación
pendiente. Eso no acredita cierre físico ni autoriza improvisar otro dispositivo.

## HANDOFF A MAIN

Entregar en español: QUÉ HACE, POR QUÉ EXISTE, VEREDICTO, OBJECTIVE, CHANGES,
FILES, DECISIONS, API/CRYPTO, STARTUP/RUNTIMES, PRIVACY/BACKUPS, VALIDATION,
PHYSICAL QA, DEVICE SAFETY, RISKS, PENDING, GIT y NEXT.

Distinguir JVM VERIFICADO, LINT, COMPILADO, ANDROIDTEST COMPILADO,
INSTRUMENTACIÓN EJECUTADA, REVISIÓN FÍSICA y PENDIENTE. Incluir comandos/conteos
reales, base exacta, diff y nuevos, configuración QA/Release, permisos añadidos,
pruebas de bytes, protección de claves y qué falta para producción. No copiar
secretos, códigos, comprobantes reales ni opiniones en el handoff.

## DONE WHEN

- El cliente y las dos pantallas están implementados, no sólo simulados.
- Activación inicial online y uso posterior offline cumplen ADR 0039.
- La opinión vence en el instante firmado; confirmada y guardada permite seguir
  ante caída de red, sin mentir sobre recepción ni habilitar actualizaciones.
- Muerte de proceso, idempotencia, relojes, errores y callbacks tardíos tienen
  regresiones; firma/canonicalización y bindings son comprobables.
- Main, Widget exportado, avisos, renderer y rutas de arranque no filtran datos
  por omitir la puerta. Recuperación, Bloqueo y onboarding conservan prioridad.
- Room, fotos y las 17 preferencias de Copias no cambian ni transportan beta.
- La confianza de ensayo no puede activar Release. Producción aún sin provisionar
  queda explícitamente pendiente, no suplida con claves de ejemplo.
- Batería local verde, instrumentación/QA ejecutada o pendiente identificada;
  cualquier fallo rojo bloquea recomendar integración.
- Mismo checkout con cambios trazables, sin staging/commit/push ni modificaciones
  en website o documentación canónica. MAIN recibe el candidato y no se inicia
  por cuenta propia el aporte, actualizador, firma o publicación.
