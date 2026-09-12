# 28 — Actualización manual sin perder datos

- Estado: **CERRADO COMO CLIENTE LOCAL POR MAIN — NO REEJECUTAR**.
- Fecha: 2026-09-06.
- Rol: dependencia especialista Android; no MAIN.
- Modalidad: mismo checkout compartido, un único escritor, entrega sin commit.
- Alcance: implementar el cliente local; no publicar ni instalar una Release.

**Cierre vigente, 2026-09-06:** MAIN aprobó el cliente y su checkpoint local tras
804/804 JVM y QA dirigida en Samsung: 21 aprobadas, una omitida, cero fallos,
sobre los APK finales. Sesión temporal y transporte en memoria expresamente
autorizados; servicio, Keystore, selector OEM, cancelación, guardado/relectura y
retorno reales. Datos y estado beta originales preservados, QA.test y temporales
retirados. No se instaló el fixture ni se usó servidor público. Distribución,
actualización instalada con datos poblados, otras APIs/TalkBack, firma y bloque 29
conservan sus puertas. No recrear la implementación o devolución histórica.
Evidencia: `docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md`.

**Antecedente tras el segundo handoff:** MAIN revisó el delta,
cerró localmente los dos P2 y repitió 49/49 dirigidas y 804/804 JVM globales.
No quedan correcciones concretas abiertas en esta revisión; las regresiones
Compose/Activity y el recorrido físico todavía no se ejecutaron sobre los APK
corregidos. No reejecutar la implementación ni la devolución que sigue como
antecedente. Próxima puerta: QA autorizada del bloque 28; sin commit ni bloque 29.

Primera actualización MAIN, conservada como historial: la dependencia ya entregó los 27 archivos
sobre `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35`. No reejecutar desde una base
limpia ni recrear el cliente. MAIN repitió 789/789 JVM, pero encontró dos P2:
desfase de reloj frente al vencimiento del servidor y repetición de una petición
de Calendario al regresar. La integración y el bloque 29 quedan detenidos.
Reproducciones, correcciones exigidas y límites de QA:
`docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md`.
Joaquin pidió después preparar la devolución al mismo chat. La continuación
acotada siguiente sustituye la exigencia original de entrada limpia y la orden
de implementar desde cero; el resto del contrato sigue vigente. El candidato
permanece sin integrar. MAIN entrega el mensaje a Joaquin, no crea ni despacha
otra tarea y no inicia una corrección en paralelo.

## CONTINUACIÓN CORRECTIVA HISTÓRICA — ENTREGADA Y REAUDITADA; NO REEJECUTAR

### Qué hace y por qué existe

Corrige exclusivamente los dos P2 de la auditoría MAIN y agrega regresiones.
La descarga no debe exigir relojes civiles sincronizados y volver de
Actualizaciones no debe repetir una navegación ya consumida. No es el bloque
29, una reimplementación del actualizador ni una autorización de publicación.

### Puerta 0 de reentrada — prevalece sobre la entrada histórica limpia

- Mismo checkout y rama; HEAD exacto
  `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35`.
- Estado esperado antes de corregir: **ocho rastreados modificados y 23 nuevos,
  cero eliminados y cero staged**. Son los 27 archivos originales de `app/src`
  más cuatro documentos de coordinación MAIN. Es un árbol sucio conocido;
  no limpiarlo, descartarlo ni reconstruirlo desde HEAD.
- Los cuatro documentos de MAIN, de sólo lectura para el especialista, son
  `docs/STATUS.md`, `docs/prompts/README.md`, este prompt y
  `docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md`.
- El inventario y los hashes de entrada están en
  `app/build/updates28/main-correction-input-20260906/manifest.json`.
  Las copias byte a byte de los 27 archivos están bajo su carpeta `sources/`.
  Verificar cada hash antes de editar; no modificar ni regenerar ese snapshot.
- Conservar autor, remoto, upstream diez adelante/cero atrás, nueve worktrees
  y referencias protegidas exigidos por la Puerta 0 original.
- Confirmar rol especialista, sin otro escritor activo. Si aparece una
  divergencia de fuente, ruta, referencia o propietario, detenerse y avisar.

Leer nuevamente las instrucciones aplicables, el estado actual, el contrato
completo de este archivo y la auditoría MAIN. Los registros anteriores son
evidencia histórica: no sustituirlos por los resultados de la corrección.

### P2-1 — Tiempo del servidor frente al reloj local

Reproducir primero con prueba determinista el caso servidor `12:00:00Z`,
teléfono `11:59:30Z`, desafío hasta `12:05:00Z`. El servidor concedió 300 segundos;
`UpdateProtocol.expiry` y `BetaCoordinator.updateAccess.sign` lo rechazan al
compararlo con los 300 segundos del reloj local.

Revisar toda la cadena de desafío, firma, autorización y GET, incluidos los
controles inferiores que puedan fallar con un reloj adelantado. No basta quitar
una comprobación del parser dejando la misma restricción en la frontera beta.

Contrato de la corrección:

- Mantener API v1, campos, fecha UTC, comprobante original, bytes canónicos y
  prueba de posesión; ningún cambio del website ni nuevos emisores de confianza.
- El servidor sigue siendo la autoridad final de vencimiento, consumo y
  revocación. No afirmar verificación local de vigencia absoluta si falta una
  referencia temporal fiable.
- Una cabecera HTTP `Date` no está garantizada por el contrato. No convertirla
  en un nuevo requisito de éxito; si se utiliza, validar su formato, precisión
  y relación con la respuesta. Su ausencia no demuestra vencimiento.
- Mantener una capacidad temporal local conservadora, sólo en memoria y
  acotada por tiempo monótono: como máximo 300 segundos desde el inicio de la
  solicitud correspondiente. Latencia y lectura consumen ese presupuesto; los
  cambios de hora civil nunca lo extienden ni reinician. No persistir permisos
  de descarga ni modificar el reloj del teléfono.
- Cancelación, cambio de sesión/generación o proceso nuevo invalidan la
  operación. No añadir reintentos automáticos ni reutilizar un desafío consumido.
- Identidad beta, firma tipada, límites de transporte y controles de puertas
  siguen vigentes; no cambiar las reglas temporales del acceso beta/feedback.

Regresiones: reloj adelantado y atrasado, caso de -30 segundos, ausencia de
`Date`, precisión de segundos si se usa, saltos civiles durante la operación,
latencia, presupuesto agotado, respuesta perdida/consumo, vencimiento remoto,
autorización de otra operación y preservación del registro beta. Usar relojes
inyectados, sin esperas reales largas ni acceso a dispositivos.

### P2-2 — Consumo de navegación y retorno

Reproducir con el árbol real de Compose dentro de `SaveableStateProvider`:
atender una petición externa `calendarNavigationRequest=1`, navegar a Ayuda,
desmontar la raíz al abrir Actualizaciones y remontarla con el mismo valor.
Debe regresar a Ayuda; hoy el efecto vuelve a seleccionar Calendario.

Implementar consumo idempotente de la petición. La recreación no debe confundir
un contador reiniciado con una petición nueva ni hacer que futuras peticiones
coincidan con un identificador ya consumido. No resolverlo sólo guardando el
último número en un lado y reiniciando el productor en el otro.

Regresiones: petición ya consumida; Ayuda y Resumen como destinos previos; mes
del Calendario conservado; desmontaje/remontaje y recreación; petición realmente
nueva; destino nuevo que llega durante Actualizaciones; puertas cerradas y
retorno seguro. No reabrir automáticamente la descarga o el selector ni omitir
recuperación, Bloqueo, beta, configuración laboral o Ayuda.

### Alcance de escritura de esta devolución

Sólo cambios necesarios en `app/src/main/java/com/blackatsystems/miguardia/`:

- `updates/UpdateProtocol.kt`, `UpdateTransport.kt`, `UpdateService.kt` y helpers
  temporales mínimos dentro de `updates/` si hacen falta.
- `beta/BetaUpdateAccess.kt` y `beta/BetaCoordinator.kt`, exclusivamente su
  frontera de actualización; no política, formato, almacenamiento o activación.
- `MainActivity.kt` y `ui/MiGuardiaApp.kt`, exclusivamente consumo/restauración
  de navegación e integración con Actualizaciones.
- Inyección mínima en `MiGuardiaApplication.kt` sólo si requiere ese reloj.
- Pruebas de actualizaciones, frontera beta, preservación y navegación bajo
  `app/src/test/` y `app/src/androidTest/`, incluido el oráculo existente.

El resto de los 27 archivos se conserva salvo una necesidad directamente ligada
al fix y dentro del contrato original, que debe explicarse antes de ampliarla.
No editar documentación MAIN, `core`, Room, manifiestos, Gradle, SDK, versiones,
dependencias, Copias, Bloqueo, website o confianza productiva.

### Verificación y entrega

1. Demostrar rojo antes/verde después para las regresiones JVM. Para Compose,
   escribir y compilar las regresiones; no presentar esa compilación como
   ejecución. Sin dispositivo autorizado, la ejecución de esas pruebas queda
   pendiente explícita de MAIN.
2. Ejecutar las pruebas dirigidas y después la batería global final exacta del
   contrato, con `--rerun-tasks`, sobre los últimos fuentes.
3. Contar XML frescos: la base fue 789 JVM verdes, no un número a conservar
   artificialmente. No borrar, omitir ni debilitar pruebas para mantenerlo.
4. Confirmar hashes de Room 1–6, manifiesto, Gradle, contratos website,
   registro beta y capacidades prohibidas; verificar `git diff --check`.
5. Guardar evidencia nueva bajo `app/build/updates28/corrections/`, sin datos
   reales, tokens o credenciales, preservando la evidencia anterior.
6. Entregar `HANDOFF A MAIN — 28, correcciones de reloj y retorno`, diferenciando
   el delta de esta continuación del candidato original. Explicar causa, fix,
   regresión y estado de cada P2, comandos, conteos y hashes de los APK finales.

**No usar ADB, Samsung, emuladores, instalaciones, limpieza, desinstalación,
permisos, sesiones sintéticas ni red remota para esta continuación.** Las pruebas
locales de interoperabilidad pueden leer los módulos reales del website sin
levantar servidor ni ejecutar sus fixtures/provisión. El build puede resolver
artefactos de sus dependencias existentes; no añadir herramientas o servicios.

No staging, commit, push, fetch/pull, reset, clean, rebase, merge, rama,
worktree, otra tarea ni bloque 29. MAIN vuelve a auditar antes de la QA nueva
y de cualquier integración. Una omisión o prueba sólo compilada sigue siendo
pendiente; no fabricar una beta habilitada para cambiar su resultado.

---

## CONTRATO ORIGINAL CONSERVADO — NO REIMPLEMENTAR DESDE CERO

## QUÉ HACE

Permite buscar una actualización de MiGuardia, descargarla de manera segura y
guardar el APK verificado. La persona lo abre desde Archivos y elige actualizar
la aplicación existente, sin desinstalarla ni empezar de cero.

## POR QUÉ EXISTE

La beta ya puede reconocer una instalación y recibir su opinión. Falta que esa
misma instalación pueda obtener versiones posteriores sin otra clave de acceso
ni otra plaza, conservando jornadas, preferencias, fotografías y acceso beta.
Este bloque prepara esa capacidad; no convierte el candidato en una beta pública.

## TASK

Implementar una superficie `Actualizaciones`, el cliente de los endpoints de
actualización vigentes, verificación de APK y guardado consciente por SAF.
Reutilizar la identidad beta sin crear un segundo escritor ni un motor de
activación. Entregar código, regresiones y evidencia local verificable a MAIN.

## CONTEXT Y PUERTA 0

Proyecto obligatorio:
`C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.

Antes de editar, comprobar y registrar:

- Rama `codex/miguardia-2.0` y checkout inicialmente limpio: cero modificados,
  nuevos no ignorados, conflictos y staged.
- Base funcional exacta `40f2c55418a407751bd2afe9379262b0758baa44`, integración
  local de Apoyar MiGuardia. No volver a implementar el bloque 27.
- HEAD de entrada: el checkpoint documental indicado en el mensaje de MAIN.
  Debe tener como padre directo esa base funcional y diferir sólo en los ocho
  documentos enumerados en la auditoría de preparación. Guardar su SHA completo
  y mantenerlo sin movimiento durante toda la dependencia. No trabajar sobre
  `ef331e3`, una base histórica o un HEAD inferido.
- Remoto `https://github.com/blackat-systems/MiGuardia.git`; upstream
  `origin/codex/miguardia-2.0` en `924fd46df018f5e14637dfe90a983fefdbf27af4`.
  Tras el checkpoint documental se esperan diez commits adelante y cero atrás.
  No hacer fetch para modificar esta fotografía.
- Autor efectivo `joaquin <blackat.systems@gmail.com>`.
- `main`, `origin/main` y `v1.0.0^{}` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Nueve worktrees existentes preservados. No crear rama, worktree, chat,
  subagente implementador ni cambiar de checkout.

Si difieren ruta, rol, HEAD, archivos o autoridad, detenerse y avisar a MAIN.
No resetear, descartar, limpiar o reconstruir desde worktrees históricos.
Joaquin abre el chat; MAIN no modifica este checkout mientras implementás.

### Lectura obligatoria completa

Seguir el orden de `AGENTS.md`: AGENTS, MAPA, STATUS, PLANIFICACIÓN, índice de
prompts, prompt MAIN 2.0 y prompt MAIN histórico. Luego leer:

- ADR 0039 y ADR 0040 completos.
- `docs/prompts/ACCESO_A_LA_BETA_Y_OPINIONES_ANDROID_V2.md` como contrato cerrado,
  sin reejecutarlo.
- `docs/prompts/PREPARAR_APK_BETA_OFICIAL_PARA_WEB_V2.md` como límite posterior,
  **PENDIENTE / NO EJECUTAR**.
- `docs/audits/2026-09-06-preparacion-actualizacion-manual-v2.md`.
- `docs/audits/2026-09-05-apoyar-miguardia-android-v2-main.md`.
- Fuentes y pruebas beta, arranque, bloqueo, navegación y SAF relacionadas.
- Los dos contratos web de INPUTS, sin editar el sitio.

No afecta fórmulas ni comportamiento sectorial: no inventar nuevos rubros o
reglas de horas. Si aparece esa necesidad, es una ampliación fuera de alcance.

## INPUTS

### Android integrado

- Room V6, 27 tablas, identity hash `7eb39f6fab5a44e69350e206716554be`.
  Esquemas 1–6 inmutables; conservar los SHA-256 de entrada.
- `beta/BetaCoordinator.kt`: único dueño de activación, opinión e identidad;
  mutex, generación, comprobante original y verificación vigentes.
- `beta/BetaIdentity.kt`: Keystore, SPKI, firma P1363 e identidad del paquete.
- `beta/BetaProtocol.kt`: verificador, formatos estrictos y primitivas comunes.
- `beta/BetaStore.kt`: registro privado no portable, sin estado del actualizador.
- `beta/BetaClient.kt`: transporte JSON existente limitado a activación y
  feedback. No convertirlo en un descargador binario de tamaño ilimitado.
- `MiGuardiaApplication`, `MainActivity` y `MiGuardiaApp`: puertas y destinos.
- SAF de Informes/Copias como referencia de integración, no como almacenamiento
  compartido de APK ni autorización para modificar esos módulos.
- Release/Debug tienen confianza beta vacía. QA sólo admite autoridad sintética
  desde instrumentación. No cargar una autoridad de ensayo al arrancar una APK.
- Versiones internas actuales: código `1`, nombre `1.0.0`; no cambiarlas ni
  presentarlas como una beta oficial. El bloque 29 prepara la versión pública.

La base tiene evidencia previa 755/755 JVM y 32/32 pruebas físicas dirigidas al
bloque 27. Son evidencia de entrada, no pruebas nuevas del actualizador.

### Website: fuente de sólo lectura

Ruta `C:/Users/Joaquin/Desktop/chatgptprojects/website`;
HEAD observado `80ebafd9945f51b13b05c708eec50c22fdf0a52b`.
Hay cambios ajenos de portfolio y archivos no rastreados: preservarlos.
No ejecutar servidor, fixtures, builds, migraciones, hosting ni operaciones Git
mutantes en ese proyecto. Verificar estos contratos y sus hashes:

| Archivo relativo a website | SHA-256 |
|---|---|
| `docs/MIGUARDIA_BETA_API_V1.md` | `2B821D750008BC449E491A25E0CFD8C1E900C06BE8DEB831FEE4C5EEFD6DD7B0` |
| `docs/MIGUARDIA_BETA_OPERATIONS.md` | `9D647DDD6C8DA633A51771BBEE231770973793F6E8F389F72029BD3113571D61` |

Leer también `lib/miguardia-beta/service.ts`, `crypto.ts`, `validation.ts` y
`http.ts` para contrastar interoperabilidad. La API pública todavía no está
provisionada: ausencia remota no se resuelve creando secretos o aceptando
respuestas ficticias en producción. Usar dobles locales explícitos en pruebas.

## OUTPUT

Un candidato implementado sin commit con:

1. `Actualizaciones` visible una sola vez dentro de `Aplicación`.
2. Consulta manual, estados comprensibles y descarga privada autenticada.
3. APK verificado y guardado conscientemente, con guía de actualización desde
   Archivos y regreso seguro a MiGuardia.
4. Regresiones de protocolo, privacidad, datos, archivos y lifecycle.
5. Handoff completo con hechos ejecutados y puertas físicas/publicación pendientes.

## SCOPE

Únicas áreas editables, siempre con cambios mínimos:

- Nuevos archivos bajo `app/src/main/java/com/blackatsystems/miguardia/updates/`.
- `app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt`.
- `app/src/main/java/com/blackatsystems/miguardia/MiGuardiaApplication.kt`.
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`.
- `app/src/main/java/com/blackatsystems/miguardia/beta/BetaCoordinator.kt` y,
  si conviene, nuevo `beta/BetaUpdateAccess.kt`: exclusivamente frontera tipada
  de lectura verificada y firma acotada; sin cambiar formato o política beta.
- `app/src/main/res/values/strings.xml`.
- Nuevos tests de actualizaciones bajo `app/src/test/` y `app/src/androidTest/`;
  ampliaciones mínimas de pruebas beta, navegación y puertas directamente
  afectadas. No borrar/regenerar suites para ocultar regresiones.

Se permite inyección de interfaces desde pruebas; ninguna confianza o endpoint
de ensayo persistente en la aplicación. Si hace falta otra ruta de producción,
manifest, dependencia o modificación de variante, detenerse y proponerla a MAIN.
Artefactos de evidencia sólo en `app/build/updates28/`, ignorados y sin secretos.

## DEPENDENCIES

- Consumir API web v1, acceso/opiniones 25 y entrada de menú 27 ya integrados.
- Mantener recuperación → Bloqueo → beta/opinión → configuración laboral →
  onboarding → contenido. No adoptar permisos de uso como permiso de descarga.
- El consumidor siguiente es preparación del APK 29, **no autorizado aquí**.
- Firmas reales, confianza pública, prueba de upgrade distribuible y portal
  operativo tienen puertas posteriores independientes.

## CONTRATO FUNCIONAL Y TÉCNICO

### 1. Recorrido consciente

Cerrar el menú antes de abrir `Actualizaciones`. Mostrar la versión realmente
instalada y `Buscar actualización`. No consultar red al iniciar MiGuardia,
abrir el menú o consultar las demás superficies. No alarmas, polling ni trabajo
periódico. Mostrar el aviso de actualización sólo en esta pantalla.

Distinguir carga, sin Release publicada, sin versión superior, disponible,
incompatible, opinión requerida, servicio no disponible, descarga, verificación,
archivo listo, guardado, cancelación y error recuperable. Un 404/503, problema
TLS o respuesta inválida NO significa que la app esté actualizada.

Comparar `versionCode`, nunca orden alfabético de `versionName`. Sólo ofrecer
código mayor que el realmente instalado; misma versión no es actualización.
Mostrar nombre y tamaño del candidato, sin inventar novedades que la API no da.
Se puede posponer o cancelar sin perder el uso local permitido de MiGuardia.

### 2. Contrato API inmutable

Origen fijo: `https://blackatsystems.com/api/miguardia/beta/v1`.
El módulo nuevo sólo consume:

- `GET /release`.
- `POST /download-challenges`.
- `POST /download-authorizations`.
- `GET /download`.

Respetar el esquema exacto del contrato web, incluido `request_id`, fechas UTC,
Base64URL sin padding, límites y códigos de error. No reescribir la API ni
enviar alias, contacto, opiniones, ubicación o datos laborales desde este módulo.

`current_release` puede ser `null`. De lo contrario validar `id`,
`version_name`, `version_code`, `application_id`, `min_android`, `byte_size`,
`apk_sha256`, `certificate_sha256` y `published_at`. Metadatos, identificador y
bytes forman una misma operación; nunca mezclar resultados de dos consultas.
Los datos de reserva/sesión web no autorizan descargas Android ni consumen plazas.

Desafío: enviar exactamente `activation_receipt` original y `release_id`.
Validar `challenge_id`, `challenge`, `expires_at` y mismo `release_id`.
Firmar con la identidad Keystore existente estos bytes UTF-8, con LF incluso
al final, exactamente como `canonicalDownloadProof` del website:

```text
miguardia-beta-download-proof-v1\n
activation_id={activation_id}\n
release_id={release_id}\n
challenge_id={challenge_id}\n
challenge={challenge}\n
```

En el bloque anterior `\n` representa un byte LF: no enviar barra y letra n.
Usar ES256/P1363 y las conversiones ya probadas; no crear otra fórmula de firma.

Autorización: enviar exactamente `activation_receipt`, `release_id`,
`challenge_id`, `challenge` e `installation_signature`. Validar token,
`expires_at` y mismo `release_id`. Para el GET binario usar únicamente:

```text
Authorization: MiGuardia-Update {authorization_token}
```

No poner autorización, comprobante, firma o desafío en URL, cookies, intents,
Bundle, SavedState, archivos de evidencia o logs. Mantenerlos sólo en memoria
durante la operación. Desafío y autorización vencen a los cinco minutos según
servidor; no cambiar su reloj ni extenderlos localmente. El desafío es de un
solo uso: perder una respuesta exige un desafío nuevo en un reintento consciente.

El servidor permite descargar hasta el instante límite inclusivo de feedback;
después exige recepción. `confirmationSaved` y `allowsContent` no acreditan
recepción. Tratar `FEEDBACK_REQUIRED` con un mensaje y acceso al flujo existente,
sin crear otra opinión, borrar la cola o invalidar activación/avisos. Respetar
rechazos, revocación, límites 429 y `Retry-After`, sin loops ni apagado local.

### 3. Frontera beta mínima

Agregar al coordinador una capacidad tipada para obtener un contexto de
actualización verificado y firmar sólo la prueba de ese contexto. Revalidar
identidad, comprobante, versión instalada y generación antes de las operaciones
sensibles. No exponer una función genérica de firma arbitraria a la UI.

No crear otro BetaStore, regenerar Keystore, canjear otra clave, reemplazar el
comprobante de activación original ni cambiar el registro de feedback. No usar
reflexión productiva o acceder directamente a sus archivos. No retener el mutex
durante la red/descarga. Resultados tardíos de otra sesión no pueden autorizar
guardado ni revivir acciones canceladas.

### 4. Descarga y comprobación antes de exportar

Transporte dedicado, HTTPS fijo, sin redirecciones, cookies o caché HTTP de
credenciales. Reutilizar primitivas, no ampliar el transporte de activación
para aceptar cualquier ruta o binario. JSON máximo 32 KiB y POST máximo 8192
bytes por estos endpoints. Establecer y probar timeouts finitos.

Descargar por streaming en IO a un archivo parcial privado de la operación
bajo `cacheDir/updates/`, sin cargar el APK completo en memoria. Límite inicial
128 MiB; rechazar antes de descargar tamaños superiores y cortar si llegan más
bytes que lo anunciado. Informar el límite sin presentar un archivo truncado.

Primera implementación: GET completo, sin Range. Reintentar empieza desde cero
con autorización vigente. Aceptar éxito binario 200 y MIME APK, no HTML/JSON,
redirección, 206 parcial o 304. Exigir tamaño final exacto y SHA-256 incremental
igual al metadato seleccionado; comprobar Content-Length cuando esté presente.
El nombre recibido por HTTP nunca decide una ruta local.

Inspeccionar el APK universal terminado mediante APIs Android compatibles:

- paquete igual al instalado y al anunciado;
- código igual al anunciado y estrictamente superior al instalado;
- nombre de versión coherente con metadata;
- minSdk anunciado/real compatible con el dispositivo;
- único certificado esperado, igual al instalado y al anunciado;
- archivo parseable y completo, sin split APK ni formato alternativo.

Rechazar ausencia o discordancia de firma, paquete, tamaño, hash, versión o
compatibilidad. No implementar rotación de certificados ni un verificador
criptográfico APK casero. `getPackageArchiveInfo` permite inspeccionar el
paquete; no presentar esa lectura como sustituto de la comprobación final de
firma del instalador Android. La metadata Release no es un manifiesto firmado
nuevo: conservar HTTPS y el contrato actual, sin inventar nuevas claves.

Hasta terminar todas las verificaciones no habilitar `Guardar APK`. Cancelar,
quedar sin espacio o fallar deja descartable únicamente el parcial propio.
La app instalada permanece funcional. No reemplazar bases ni iniciar restauración.

### 5. Guardado e instalación manual

`Guardar APK` abre `ACTION_CREATE_DOCUMENT`, MIME
`application/vnd.android.package-archive`, con nombre propio sanitizado y
explicación de elegir una ubicación fácil de encontrar, por ejemplo Descargas.
No pedir permisos amplios de almacenamiento.

Escribir únicamente bytes verificados, por streaming; comprobar conteo/hash de
salida y manejar fallos de apertura, escritura, flush y cierre. No declarar
guardado si alguno falla. Cuando el proveedor permita releer el documento,
comparar bytes; registrar honestamente si sólo se pudo verificar la escritura.
No borrar documentos preexistentes. La limpieza de una exportación fallida sólo
puede intentar retirar el documento recién creado para esa operación, con URI
exacta propia. Un archivo guardado con éxito queda bajo control del usuario.

Mostrar instrucciones breves: abrir ese APK desde Archivos y elegir Actualizar;
no desinstalar MiGuardia. Si Android rechaza firma o compatibilidad, cancelar,
no borrar datos ni recomendar desactivar Play Protect. El permiso eventual de
fuentes externas pertenece a Archivos, no se solicita ni altera desde MiGuardia.

No usar `PackageInstaller`, `ACTION_INSTALL_PACKAGE`, lanzamiento de APK por
`ACTION_VIEW`, `REQUEST_INSTALL_PACKAGES`, ajustes de instalación ni ampliar el
FileProvider de Informes. No abrir automáticamente el archivo al terminar.
Una copia de seguridad es una recomendación voluntaria mediante el flujo ya
existente, no requisito ni operación automática de este bloque.

Guardar o regresar del selector no prueba que se instaló nada. Volver a consultar
la versión propia en una nueva entrada/reanudación segura, sin reabrir archivos
o acciones anteriores. La promoción de beta a estable conserva el mismo paquete
y certificado, con código mayor; el nombre visible no decide continuidad.

### 6. Lifecycle, privacidad y preservación

La descarga es una operación visible y cancelable, no un servicio. Cancelarla
al salir realmente a segundo plano; una recreación por configuración no debe
crear otra solicitud. El selector SAF ocurre después de la verificación.
Antes de abrirlo y al recibir su resultado, revalidar operación y puertas.
Si el retorno queda bloqueado, no escribir por detrás de Bloqueo/beta ni iniciar
otra salida: volver a un estado seguro que requiera acción consciente.

Conservar mes/destino seguro y estado no sensible mediante SavedState. No
restaurar tokens, permisos ni una operación de red como si siguiera autorizada.
Proceso nuevo: descartar sólo temporales propios antiguos y mostrar operación
interrumpida cuando corresponda, sin descarga o selector automáticos.

No modificar Room, sus 27 tablas, DataStore de usuario, fotografías, Copias,
onboarding, Bloqueo, el registro beta o la clave de instalación. No persistir
el estado de actualización dentro de las 17 preferencias portables. Los
receivers existentes ya manejan reemplazo del paquete: no agregar otro.

No enviar identificadores de hardware, datos laborales, diagnósticos, analítica
o pagos. Comprobantes y tokens tienen `toString` seguro, sin secretos; no
guardar respuestas completas en evidencias. No prometer DRM o impedir copia
del APK por quien lo descargó.

## DO NOT

- No editar `core/**`, Room/esquemas/migraciones, Gradle, manifiestos, SDK,
  dependencias, versión, applicationId, configuración de firma ni website.
- No cambiar políticas beta, formato de Copias, identidad Keystore, confianza
  Debug/Release, permisos ni prioridades de arranque.
- No crear D1/R2, secrets, claves públicas productivas, APK oficial, cuenta,
  servicio remoto, publicación o nueva versión del sitio.
- No activar autoridad sintética automáticamente ni fabricar una Release pública.
- No hacer commit, staging, push, fetch, pull, merge, rebase, reset, clean, tag,
  Release, rama, worktree, tarea o dependencia siguiente.
- No usar Samsung, ADB, emuladores, alarmas, instalación, reemplazo, limpieza,
  desinstalación o reinicio sin una autorización nueva y específica del bloque.
  Las autorizaciones físicas/sesión sintética del bloque 27 no se heredan.

## VALIDATION

### Pruebas exigidas

1. Protocolo real: campos exactos, UTF-8/LF final, firma JCA ↔ Web Crypto con
   `canonicalDownloadProof` del website; no copiar la fórmula al fixture para
   ocultar incompatibilidades. Reusar Node disponible y el patrón de
   interoperabilidad beta, sin instalar herramientas o ejecutar el servidor.
2. Release nula, mayor/igual/menor, nombre beta/estable, paquete/certificado
   incorrectos, API incompatible y números/fechas/hashes inválidos.
3. Desafío/token vencido, usado, ajeno o de otro release; opinión antes, justo
   en el límite y después; opinión en cola vs recepción; revocación, 429/503,
   timeout, sin conexión, redirects y respuesta perdida sin loop.
4. Descarga íntegra, truncada, sobredimensionada, corrupta, falso MIME, parcial,
   cancelación, disco lleno, hash y firma dispares; memoria acotada, limpieza
   limitada, metadata vieja y resultados cruzados entre dos operaciones.
5. Comprobante, identidad beta, cola y preferencias iguales antes/después de
   consultar, descargar, cancelar, fallar o guardar. Ninguna reserva/canje nuevo.
6. Entrada única, cierre del menú, no red al abrir app/menú, errores y reintento,
   doble toque, rotación, proceso nuevo, SAF cancelado/fallido y callback tardío.
   Puertas recuperación/Bloqueo/beta/configuración/Ayuda y destino de retorno.
7. Instrumentación preparada: inspección Android del APK, proveedores SAF de
   prueba y no escrituras Room. Temas, orientación, accesibilidad y zoom interno
   100/150/200; nunca consultar o alterar escala/densidad del sistema.

No debilitar la confianza de la aplicación para fabricar un éxito de prueba.
Un doble de PackageManager demuestra lógica, no firma real ni instalación.
Una fixture firmada puede producirse localmente con herramientas ya disponibles
y clave QA existente, en `build` ignorado; no cambiar versiones de la app base,
crear una clave Release ni instalar el fixture. Documentar su procedencia.

### Batería global final

Después del último cambio, ejecutar serialmente:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test :core:database:testDebugUnitTest `
  :app:testDebugUnitTest :app:lintDebug `
  :app:assembleDebug :app:assembleQa :app:assembleRelease `
  :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
```

Contar XML frescos por módulo con fallos, errores y omitidas. Separar JVM,
compilación AndroidTest, instrumentación y QA humana. Registrar hashes de APK
y de esquemas 1–6; verificar manifiesto sin permisos/componentes nuevos,
certificado/versión sin cambios, ausencia de secretos y `git diff --check`.

### Puertas físicas y de distribución

Sin autorización nueva, entregar candidato local y pedir QA a MAIN; no declarar
el flujo físico cerrado. Con autorización específica posterior, usar un serial
por vez y sólo QA/test, conservar datos preexistentes y no instalar producción.
Descarga → verificación → SAF → Atrás, errores y puertas necesitan recorrido
Samsung real antes de cerrar la integración del cliente.

Una actualización real entre dos versiones firmadas compatibles debe comparar
Room, preferencias, fotos, identidad beta y reapertura sin nuevo canje. Esa
prueba y la firma oficial son puerta posterior de distribución, no se simulan
subiendo permanentemente el código de la QA del usuario. `install -r` con el
mismo código, compilación o guardado del APK no equivalen a dicha prueba.

## STOP POINTS

Detenerse ante base/propietario inesperado, contradicción material de contratos,
necesidad de otro permiso/dependencia/versión, operación sensible no autorizada,
hallazgo de seguridad o validación roja sin corrección acotada. Preservar el
árbol y reportar la evidencia. La API remota sin publicar es un pendiente
conocido: permite probar localmente, nunca falsear éxito ni abrir publicación.

## DONE WHEN

- Cliente, pantalla, firma acotada, descarga y guardado cumplen este contrato.
- No se agregaron capacidades prohibidas ni se alteró la identidad/datos.
- Pruebas nuevas y batería final verdes sobre exactamente el árbol entregado.
- Se documentaron todas las puertas físicas no ejecutadas y la ausencia de
  confianza/Release/API públicas; no se declara producto publicable.
- HEAD de entrada intacto, cero staged, diff limitado e inventario completo.
- MAIN recibe un candidato auditable, no una integración ya decidida.

## HANDOFF A MAIN

Entregar en español: `QUÉ HACE`, `POR QUÉ EXISTE`, `VEREDICTO`, `OBJECTIVE`,
`CHANGES`, `FILES`, `DECISIONS`, `API/CRYPTO`, `DOWNLOAD/SAF`, `PRESERVATION`,
`VALIDATION`, `ROOM`, `PHYSICAL QA`, `DEVICE SAFETY`, `RISKS`, `PENDING`, `GIT`
y `NEXT`. Detallar comandos, resultados, hashes, pruebas realmente ejecutadas,
limitaciones, archivos nuevos y modificados, y confirmación de operaciones
Git/dispositivos no realizadas. No incluir tokens ni comprobantes concretos.

MAIN audita en el mismo checkout. No hay nada para cherry-pick. No abrir ni
preparar el bloque 29 por cuenta propia.
