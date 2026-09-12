# Cierre integral Android público, seguridad y preparación de entrega V2

## Continuación de entrega 20–21 desde Android integrado

Esta sección sustituye la entrada de revisión de etapa 19 conservada debajo.
No vuelve a encargar retirada beta, actualizador ni auditoría integral.
Consultar STATUS para saber qué parte está habilitada y quién escribe.

Base de preparación: `c985b6a981846599f224ac1e080a0199fa565a72`, rama
`codex/miguardia-2.0`, entrada limpia e índice vacío. El código público está
integrado en `d0105e039ad07c56f8c5ee9c4c1c5a37156efdfe`; los commits posteriores
son documentación MAIN. Si MAIN confirma esta preparación después, exigir
ancestro y contrastar su delta, no exigir ficticiamente el HEAD anterior.
Este contrato y STATUS son de MAIN. Antes de ceder el escritor a una tarea
especialista, MAIN entrega la fotografía final exacta del checkout.

### Paquete cohesivo de trabajo

1. Preparar y probar la verificación posterior a firma, separada del inspector
   unsigned. Usar `apksigner`, `aapt2` y `zipalign` existentes; comprobar el pin
   permanente, firmante único, identidad, superficie y contenido exacto frente
   al unsigned aprobado. No excluir indiscriminadamente `META-INF` ni confundir
   firma válida con procedencia aprobada. Esta preparación no opera claves.
2. Conservar el APK completo de código 2 y su política/inventario como posible
   origen del ensayo controlado, sin llamarlo versión distribuida. No sobrescribir
   evidencia previa ni instalar mini fixtures del actualizador.
3. MAIN confirma máximo distribuido y congela nombre/código; para la primera
   entrega pública sin APK previa distribuida, `2.0.0` / código 3, conservando
   código 2 sólo como origen controlado. Actualizar coordinadamente Gradle,
   política y fixtures de identidad. Preservar
   paquete, SDK, dependencias, permisos, componentes y seis esquemas.
4. Ejecutar validación proporcional y dos reconstrucciones Release finales con
   iguales insumos. Inspeccionar ambos archivos; diagnosticar cualquier diferencia.
   Separar hashes unsigned, alineados y firmados. La reproducibilidad histórica
   no acredita los bytes nuevos.
5. Tras aceptación del unsigned, preparar la firma de ambas aplicaciones completas
   necesarias para el upgrade con la clave permanente existente. Joaquin ingresa
   la contraseña sólo en consola local separada, sin transcripción/captura.
   Ninguna clave nueva, contraseña en argumentos/entorno ni modificación del
   APK después de firmarlo. La autorización no permite recibir su contraseña.
6. Verificar los firmados y preparar la matriz siguiente. MAIN acepta artefactos
   antes de ejecutar el ensayo; website continúa sólo lectura.

### Matriz instalada y preservación

Proponer AVD desechable como primer aislamiento. Antes de iniciarlo fijar y
autorizar nombre/imagen, serial exclusivo, paquetes, APK/hash, instalación,
datos ficticios, efectos y retirada. No heredar permiso del AVD anterior ni del
Samsung conservado. No descargar imágenes ni modificar ajustes de pantalla por
inferencia. Leer configuración no acredita un dispositivo ejecutado.

- QA final: raíz pública offline, residuos beta, recuperación/Bloqueo, callbacks
  reales de aviso (tap/descarte), Widget, Apoyar, SAF y retornos. Incluir R03 y M01.
- Upgrade Release: dos apps completas, igual paquete/certificado y código
  creciente. Poblar por rutas válidas historia, jornadas/snapshots, planes,
  horario real, extras, disponibilidad, situaciones, notas, fotos, preferencias,
  Bloqueo, avisos y Widget; comprobarlos en UI antes/después. Filas genéricas o
  tablas vacías no acreditan ese dataset.
- Comparar las 27 tablas y archivos desde copias consistentes, sin escritores
  activos. Keystore se prueba por uso, no exportando claves. Clasificar cambios
  administrativos legítimos, sin normalizar pérdidas.
- Separar reemplazo instalado de buscar → descargar → verificar → selector OEM
  → Archivos → Actualizar. Este recorrido necesita servicio/oferta controlados;
  no cambiar origen productivo, TLS o pin para sortear una puerta pendiente.
- Registrar temas, zoom interno 100/150/200, teclado, paisaje, accesibilidad y
  APIs realmente ensayados. Un AVD no acredita Samsung/OEM.

Si cambia la candidata, renovar controles afectados, hashes/firma y QA. Etapa 22
espera aceptación; no publicar hash unsigned ni usar el Site vivo como fixture.
Contacto público confirmado: `blackat.systems@gmail.com`; no autoriza acceso al
buzón. Feedback permanece desactivado hasta definir mecanismo/campos/retención.

## Entrada histórica de revisión de etapa 19 — no ejecutar nuevamente

La entrada inicial limpia descrita más abajo quedó superada por una entrega.
MAIN contrastó el 2026-09-09 las **90 rutas** de
`.local-evidence/public-android/2026-09-09-integral/delta-final.json`: hashes y
ausencias coinciden, HEAD `3e88bc9efd5eb3a797658ac17b8fed0f766a1e82`, índice vacío.
El estado esperado ahora es **74 rastreados modificados/eliminados y 19 nuevos**:
90 rutas de candidata y los tres documentos MAIN indicados en la entrada original.
La fotografía de la tarea pausada es
`.local-evidence/public-android/20260909-192256-puerta0-mismatch/entry.json`.
STATUS y este prompt se actualizaron después por MAIN; refrescar sólo sus hashes
documentales al retomar. No interpretar esta actualización como un delta funcional.

Se reconoce la candidata como entrada trazable, **no como aceptada**. Leer su
HANDOFF_A_MAIN.md, delta-final.json, validation-final.json y full-final/result.json
en la carpeta integral; contrastar insumos/alcance antes de reutilizar evidencia.
Retomar como especialista revisor/corrector del mismo frente: no reimplementar,
revertir, restituir beta ni ejecutar automáticamente toda la batería desde cero.
Revisar el candidato, reproducir hallazgos y completar únicamente faltantes reales
del contrato; repetir pruebas por cambio, fallo o incertidumbre identificada.
Si no hay defecto local, entregar revisión y matriz pendiente, sin cambios por ritual.

Al recibir la confirmación de MAIN, sos el único escritor funcional hasta el
handoff; MAIN no implementa en paralelo. Separar evidencia heredada de la nueva
y registrar delta propio desde esta fotografía. No integrar, aceptar residuales,
firmar, usar dispositivos ni publicar por esta conciliación de Puerta 0.
Las etapas 20–22 conservan las puertas del encargo. Esta sección reemplaza sólo
la expectativa de entrada y el punto de inicio; el contrato funcional sigue vigente.

## Qué hace

Convertir la aplicación existente en una candidata pública utilizable sin clave,
cuenta ni conexión inicial. Completar el actualizador público, la retirada de
beta, la preservación de datos, la revisión de seguridad y la preparación técnica
de firma/QA de entrega. Resolver el recorrido entero, incluidos errores,
regresiones, herramientas y evidencia; no entregar piezas desconectadas.

## Por qué existe

El productor web ya está aceptado. Android todavía depende del acceso beta y
del protocolo privado. Ocultar la pantalla, fabricar una activación o reemplazar
el actualizador por un enlace dejaría el sistema incompleto. Esta dependencia
consume el contrato real y resuelve todo el frente Android pendiente.

## Rol, profundidad y continuidad

Sos la dependencia especialista ANDROID de MAIN. Joaquin trabaja con Astra/ultra
y exige capacidades sustanciales de punta a punta. Los bloques siguientes son
hitos internos de este encargo, no nuevas tareas por botón, bug o prueba.
Diseñá después de inspeccionar; implementá y depurá autónomamente dentro del
contrato. No te detengas después de cada hito verde ni pidas aprobar decisiones
reversibles. Corregí las regresiones de tu propio delta antes del handoff.

Un único escritor en el checkout compartido y una sola ejecución Gradle/operador
Android a la vez. Podés usar revisores internos de sólo lectura para contratos,
seguridad y preservación, sin crear tareas visibles, ramas ni worktrees.
MAIN no escribe mientras estés implementando. Integración y aceptación son de
MAIN; no podés aprobar tu entrega ni iniciar por tu cuenta publicación web.

Este encargo cubre el cierre técnico Android de la etapa 19 y prepara las
etapas 20–22. La misma tarea puede continuar con firma y QA instalada cuando
MAIN haya aceptado la candidata y trasladado los insumos/permisos concretos.
Esas puertas no obligan a abrir otro especialista ni permiten saltar la revisión.
«Todo completo» no incluye inventar contactos, gastar dinero, operar secretos,
publicar antes de QA o certificar hechos que no pudiste ejecutar.

## Entrada exacta y Puerta 0

- Proyecto: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD preparado por MAIN: `3e88bc9efd5eb3a797658ac17b8fed0f766a1e82`.
- Autor Android: `joaquin <blackat.systems@gmail.com>`.
- Origin: `https://github.com/blackat-systems/MiGuardia.git`.
- Nueve worktrees existentes; no recuperar código de ellos.
- Entrada funcional limpia. Sólo se esperan las modificaciones documentales
  posteriores de preparación en `docs/STATUS.md`, `docs/prompts/README.md` y
  este prompt nuevo, con staging vacío. Son propiedad de MAIN y no se editan.
  Si MAIN consolidó después esos tres archivos, verificar ancestro y que el
  delta desde el HEAD indicado sea exclusivamente ese paquete documental.
- Base funcional hasta creación contextual conservada. Room V6, 27 tablas,
  seis esquemas; paquete `com.blackatsystems.miguardia`. Gradle de entrada:
  `2.0.0-beta.1`, código 2; valores finales se congelan después de aceptación.
- `main`, `origin/main` y `v1.0.0^{}` protegidos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.

Verificá ruta, HEAD, rama, índice, modificados/nuevos, remotos, worktrees y
autor antes de escribir. Guardá fotografía de entrada y hashes de fuentes
protegidas en `.local-evidence/public-android/<corrida>/`. Un cambio inexplicado
requiere detenerse, no reset/clean ni incorporar trabajo de otro dueño.

Website, **sólo lectura**: `C:/Users/Joaquin/Desktop/chatgptprojects/website`,
rama `main`, HEAD `394964b98be87ccfd6a56877398545deb9848380`, sin remotos.
Tiene tres archivos ajenos modificados (`app/globals.css`, `app/layout.tsx`,
`app/page.tsx`) y ocho archivos ajenos no rastreados: cuatro tarballs
`.openai/site-build-*.tar.gz`, `AGENTS.md` y tres JPG de `INTERFACE_DESIGN/`.
No los copies a un perfil, edites ni limpies. No escribas siquiera fixtures,
cachés o dependencias en website: ejecutá tu arnés desde Android y verificá
preservación. No iniciar servidor, D1, R2 ni comandos de provisión web.

## Lecturas y contrato ya aceptado

Leer completos AGENTS, STATUS, WORKFLOW, VALIDACION y las rutas pertinentes de
LECTURAS_POR_TAREA. En ORQUESTACION_MAIN, etapas 19–22; planificación §22;
ADR 0041 y únicamente garantías conservadas de 0039/0040. Para los límites
tocados: ADR de Copias, Bloqueo, Ayuda, avisos/Widget y Clima.
Consultar el cierre vigente de `docs/audits/2026-09-09-distribucion-publica-web-main.md`;
su rechazo inicial es histórico, S01/F01/F02/F03 quedaron cerrados.

Fuentes normativas del website aceptado:

- `C:/Users/Joaquin/Desktop/chatgptprojects/website/docs/MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md`.
  SHA-256 `9AD6C934A20D9A766AAA7DAA3C221EB33589145F78CF985B74DF5230C2D76B00`.
- `tests/fixtures/public-distribution/android-oracles.json` y su README.
  SHA-256 JSON `D086F721C29A806FD41924529FDA1BDEC9909F8492E4FC492D6882616F587072`.
  Corpus `oracle_version: 2`, API `contract_version: 1`, 24 casos completos.
- Validador/cliente de `lib/miguardia-public/` y tests públicos relevantes.
- Documentos PUBLIC_DISTRIBUTION_OPERATIONS y SECURITY para límites de entrega.

La evidencia MAIN web es 163 tests, 40 HTTP, tipos/lint/builds y audit de
dependencias sin vulnerabilidades conocidas. No son pruebas Android ni una
publicación. `approvedRelease=null`. No consumir el sitio vivo como fixture.

## A. Retirada beta real y continuidad transversal

Inventariar todas las dependencias de beta, no sólo el paquete con ese nombre:
Application, MainActivity, host Compose, WidgetConfigurationActivity, navegación,
runtimes, receivers, notificaciones, clima, preferencias, Copias, variantes y tests.

- Arranque: recuperación de Copias → Bloqueo → configuración laboral → Ayuda
  → contenido. Funciona offline desde instalación nueva y tras muerte del proceso.
- Sin canje, comprobante ficticio, identidad obligatoria, cuenta, temporizador
  de opinión ni consulta remota automática necesaria para entrar.
- Estados beta ausente, válido, corrupto, incompatible, vencido o feedback en
  cola no bloquean ni borran trabajo. Dejar archivos/identidades heredados
  inertes, sin creación, reenvío ni limpieza silenciosa. Documentar qué queda.
- Desconectar callbacks, observadores, colas, listeners de conectividad y
  alarmas exclusivamente beta. Un callback antiguo no puede reactivar envío,
  cambiar la puerta ni publicar datos después de recuperación/bloqueo.
- Conservar acciones útiles de avisos en cola, dismissals y destinos exactos,
  con idempotencia y revalidación local. Distinguirlos de opiniones beta retiradas.
- Avisos y Widget vuelven a funcionar sin beta; no reemplazar todas las puertas
  por `true`. Mantener privacidad, preferencias, recuperación y Bloqueo según
  su contrato, sin imponer restricciones nuevas a publicaciones exteriores.
- La actividad exportada del Widget no lee/modifica instancia antes de su
  puerta local. Mantener IDs, configuración, restauración y remapeo.
- Conservar navegación desde avisos/Widget, regreso de Apoyar MiGuardia, SAF,
  Ayuda y creación contextual, borradores/mes y entrega única de resultados.
- Copias mantiene formato/cifrado, 17 preferencias portables y exclusiones de
  Bloqueo/Ayuda/beta; combinar/reemplazar/journal no borran datos residuales ajenos.
- Retirar mensajes y acciones públicas beta obsoletos. El nombre histórico de
  versión no se modifica aquí para simular estabilidad. Apoyo sigue voluntario.

Eliminar código muerto exclusivamente privado si queda demostrado y cubierto;
preservar utilidades reutilizadas. No dejar una implementación privada alcanzable
ni conservar toda la beta activa detrás de una bandera. No hacer refactors ajenos.

## B. Actualizador público completo, sin debilitar el cliente 28

Reutilizar `updates/UpdateProtocol`, `UpdateTransport`, `UpdateTimeBudget`,
`UpdateService`, `UpdateFiles`, `UpdateApkInspector`, `UpdateDocumentWriter`,
ViewModel/pantalla y sus regresiones. No duplicar el motor ni crear instalador.

1. Abrir Actualizaciones muestra versión local sin consultar. Buscar es
   consciente. Sólo GET al origen fijo `https://blackatsystems.com`, base
   `/api/miguardia/public/v1`; `/release` y `/download/{id}/{apk_sha256}`.
   Preferir comparar API instalada localmente sin enviarla si no es necesaria.
2. Quitar capture/validate/sign de BetaUpdateAccess, desafíos, Authorization,
   cookies y tokens. Sustituir la autoridad beta por verificaciones públicas
   reales, no por un objeto de permiso siempre válido. No crear otra identidad.
3. Aplicar exactamente UTF-8, JSON, claves, tipos, profundidad, límites, MIME,
   compresión, fechas y errores del contrato. No exigir header Date ni que el
   reloj civil del teléfono se convierta en autoridad final del servidor.
4. Separar UNAVAILABLE, INVALID_RESPONSE, INCOMPATIBLE, NO_NEWER_VERSION y
   errores operativos. Validar metadatos antes de comparar instalación y versión.
   Un paquete/certificado ajeno en metadata es inválido; instalación distinta
   frente a metadata válida es incompatible. QA no se ofrece como actualizable
   al paquete productivo. No sortearlo con allowlists productivas de ensayo.
5. Guardar identidad seleccionada inmutable. Un 409 exige nueva consulta y
   selección consciente; nunca sustituir por latest o descargar otro archivo.
6. Retry-After 429/503 tipado incluso con cuerpo inválido; segundos/fecha HTTP,
   fallback 60 s, máximo 24 h y header limitado. Presupuesto monótono compartido
   por todas las entradas del coordinador, sobreviviendo a recreación y regreso
   de pantalla en el proceso. Vencer la espera no dispara una petición.
7. Conservar watchdog previo a IO bloqueante, límites por operación y presupuesto
   monótono total, cancelación/desconexión, fondo real, respuestas tardías y
   único dueño de la operación. Cambiar hora/zona no extiende un plazo activo.
8. Streaming acotado de APK, parcial privado, SHA-256/tamaño/Content-Length
   exactos conforme al contrato, sin Range, redirect ni cambio de host/puerto.
   Rechazar exceso, truncamiento, tipo/codificación incorrectos y cancelación.
9. Inspección Android real: paquete, versionCode/versionName/minSdk, firmante
   único y continuidad, universal/splits/ABI y bibliotecas coherentes. Conservar
   compatibilidad de lectura de certificados API 26–27. Rechazar downgrade.
   Certificado público Release fijado:
   `AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D`.
   Hash del mismo hosting no sustituye firma ni autenticidad de primera descarga.
10. Revalidar instalación, selección, archivo y puertas antes de Guardar APK.
    Conservar SAF sin truncar documentos existentes, callbacks correlacionados,
    flush/cierre, READ_BACK/WRITE_ONLY y limpieza sólo del documento nuevo propio.
    Guardar no instala: explicar apertura manual desde Archivos.
11. Atrás/cancelación/proceso nuevo/recreación y retorno bloqueado no pierden
    destino previo, mes o borrador, no relanzan un selector ni consumen dos veces
    resultados. Los destinos externos esperan salir de Actualizaciones.

## C. Seguridad Android y preservación comprobables

Entregar matriz de amenazas, superficie, control, evidencia, resultado y residual.
Reutilizar la revisión web aceptada por hash; no rehacer S01–F03 ni atribuirle
alcance Android. Investigar referencias oficiales vigentes de MASVS/MASTG y
avisos de paquetes públicos. No subir código, APK, archivos personales o claves
a servicios de análisis. Registrar alcance y fecha de cada consulta/escáner.

Clasificar los veinte controles pedidos por Joaquin, sin forzar tecnologías:

1. Secretos/API keys; 2. secretos en historia Git; 3. supuesta clave pública DB;
4. RLS; 5. cifrado sensible; 6. autenticación servidor; 7. acceso a registros;
8. manipulación de campos; 9. cookies; 10. passwords; 11. rate limit login;
12. bots; 13. SQL parametrizado; 14. validación; 15. contenido de usuario;
16. archivos/importación; 17. respuestas mínimas; 18. cabeceras;
19. HTTPS; 20. dependencias directas/transitivas y artefactos empaquetados.

Para cada uno distinguir aplicable/no aplicable con razón, comprobado, hallazgo
o pendiente. No inventar login, RLS ni credenciales para Room. Auditar además
intents/URI/componentes exportados, FileProvider, cachés, límites ZIP/fotos/copias,
recuperación atómica, notas médicas, avisos/Widget/Recientes y regresos externos.
Sandbox, bloqueo visual, cifrado del dispositivo, copias cifradas y cifrado
adicional de Room son capas distintas: documentar amenaza real y límites.

Corregir defectos reproducibles dentro del frente y agregar regresiones.
Un secreto confirmado no se imprime: detener la puerta sensible y solicitar
revocación específica; no reescribir historia. Vulnerabilidad sin parche o
necesidad de nueva biblioteca/cifrado/migración requiere decisión material y
alternativas fundamentadas. No aceptar riesgos críticos por tu cuenta ni llamar
a avisos de versiones CVE. No prometer app blindada o cero exploits.

## D. Pruebas y cierre técnico, no sólo compilación

- Ejecutar TODOS los 24 oráculos contra el consumidor Kotlin productivo, no
  contra una segunda fórmula que imite el resultado. Leer el corpus real desde
  website y registrar hash/versiones; no editarlo para hacerlo pasar. Fixtures
  de 32 bytes acreditan transferencia, nunca APK aprobada ni instalable.
- Adaptar interop, inputs Gradle y runner al contrato público; no dejar `full`
  dependiendo únicamente de `lib/miguardia-beta/crypto.ts`. Mantener controles
  históricos útiles separados y explícitos. No ocultar fallos/omisiones ni
  conservar un conteo anterior artificial retirando las regresiones difíciles.
- Pruebas unitarias de transporte, parsers, reloj, espera, cancelación,
  lifecycle/regreso, almacenamiento y matriz de estados beta residuales.
- Prueba transversal con coordinadores reales: arranque sin beta → uso →
  Actualizaciones → consulta/transferencia/validación → guardado/retorno.
  Incluir ausencia de tráfico beta, ausencia de creación de identidad y
  conservación de datos/preferencias en errores y callbacks tardíos.
- Instrumentación diseñada para raíz real abierta sin activación sintética,
  bloqueo/recovery, Widget exportado, aviso, Apoyar, SAF, muerte del proceso,
  recreación, claro/oscuro, zoom interno 100/150/200, scroll/teclado/paisaje.
  Pruebas de bytes no sustituyen PackageManager ni selector OEM.
- Preparar comparación lógica de las 27 tablas y archivos/preferencias antes
  y después de un upgrade con datos ficticios POBLADOS: historia laboral,
  jornadas/snapshots, recurrencias, horario real, extras, disponibilidad,
  situaciones, notas, fotos, preferencias, Bloqueo, avisos y Widget. No aceptar
  una base vacía como demostración de todos esos contratos.
- Actualizar `tools/qa-groups.json` y sus guardas: retirar dependencia beta sin
  degradar aislamiento. Fixtures que limpian datos sólo son ejecutables en un
  entorno desechable expresamente autorizado; nunca en Samsung conservado.

Usar VALIDACION por impacto. Al final, después del último fix ejecutable:

```powershell
python tools/verify.py --profile full --website ../website --fresh-tests
python -m unittest discover -s tools/tests -v
python -m unittest discover -s tools/release/tests -v
python tools/qa.py --check-catalog
python tools/docs_check.py
git diff --check
```

Resolver Node/Java/SDK existentes y registrar versiones; no instalar herramientas
ni reducir validación silenciosamente. Usar `--plan` y las opciones vigentes.
Una única reconstrucción global final si el delta transversal la justifica;
durante iteración sólo controles afectados. Registrar XML frescos, insumos y
cinco APK. `COMPILADO`, `JVM`, `INTEROP`, `INSTRUMENTACIÓN` y `QA FÍSICA` separados.

## E. Preparación de firma, upgrade y publicación

Preparar en el mismo encargo un expediente ejecutable de las etapas siguientes:

- Inspector/política y scripts existentes adaptados técnicamente a pública,
  sin relajación para aceptar QA, splits, firma incorrecta o APK incompleta.
  Si cambian herramientas, probar casos positivos/negativos. Mantener inspector
  unsigned estricto y verificación firmada separada. No regenerar custodia.
- Inventario efectivo de APK, permisos, exportados, SDK, ABI/alineación/ELF,
  flags y seis hashes Room. Dos reconstrucciones de iguales insumos si se
  prepara reproducibilidad; distinguir igualdad ZIP de igualdad de contenidos.
- Propuesta justificada de versión pública y códigos crecientes, contrastada
  con referencias disponibles. No afirmar instalada una versión sin inspección
  autorizada. La meta es 2.0.0, no 3.0.0 por cambiar de canal. No cambiar el
  versionado final hasta congelación aceptada por MAIN.
- Plan de upgrade con DOS apps completas de igual paquete/certificado y código
  creciente; nunca instalar las mini fixtures de inspección. Certificado QA
  distinto del Release implica incompatibilidad, no permiso para desinstalar.
- Lista exacta de metadatos derivados del futuro APK FIRMADO; nada de usar hash
  unsigned en catálogo o convertir ejemplos de pruebas en publicaciones.
- Procedimiento de recuperación/retirada de oferta ante fallo, sin apagar
  instalaciones ni eliminar datos. Preparar los pasos y condiciones, no operar R2.

Puertas: no hay autorización nueva de ADB, emulador, instalación, borrado,
alarma real, reboot, firma ni acceso remoto por recibir este prompt. Antes de
un ensayo presentar serial/aislamiento/paquetes, efecto y preservación; usar
una autorización específica vigente sin volver a pedir permisos genéricos.
Si no está cubierta esa superficie, completar TODO lo local independiente y
entregar la matriz exacta pendiente, sin simularla ni declarar cierre físico.

MAIN acepta primero el candidato de 19. Con versión/artefacto congelados y
alcance concreto, esta misma tarea podrá continuar 20/21: contraseña sólo en
consola local controlada por Joaquin, clave existente fuera de Git, firma
verificada y QA instalada. La publicación web 22 la coordina MAIN con el
responsable del Site después de esa QA; no es escritura Android implícita.
No reiniciar implementación ni contratar otro especialista para cada fix.

## Alcance de escritura y prohibiciones

Permitidos: `app/src/**` implicados en retirada/actualizador/seguridad y tests;
`app/build.gradle.kts` sólo para wiring de pruebas/interoperabilidad necesario;
herramientas `tools/verify.py`, `tools/qa.py`, catálogo, tests y `tools/release/**`
relacionados. Manifiesto sólo puede retirar permisos/componentes exclusivamente
obsoletos con uso comprobado; no añadir permisos ni exportaciones nuevas.
Documentos técnicos de herramientas propios pueden actualizar sus instrucciones
beta obsoletas sin cambiar política de producto ni describir firma ejecutada.

Core/domain/database, seis esquemas, fórmulas, formato de Copias, 17 preferencias,
SDK/applicationId y nuevas dependencias quedan protegidos. Un defecto demostrado
que exija tocarlos se informa con solución/impacto antes de ampliar alcance.
Website y documentación canónica MAIN son sólo lectura, salvo evidencia propia
fuera de Git bajo `.local-evidence/public-android/`. No editar STATUS/ADR/planificación.

Sin staging, commit, push, tag, merge, rebase, reset, clean, ramas/worktrees,
tareas nuevas, Play Store, Google Drive, provisión o publicación. Sin claves
privadas en código, argumentos, logs o handoff; no volver a crear la clave APK.
No limpieza de registros beta, datos reales o recursos remotos por inferencia.
Correo público confirmado: `blackat.systems@gmail.com`. El mecanismo/campos y
retención del feedback siguen sin definición; no reutilizar el endpoint beta.
Mantener recolección desactivada y avisar a MAIN
qué decisión concreta falta antes de publicar, sin bloquear desarrollo Android.

## Aceptación y handoff único

Entregar candidato sin implementación local conocida pendiente del alcance.
No declarar completo si el retiro es cosmético, alguna puerta sigue beta,
faltan oráculos, hay regresiones o una ruta importante depende de una fixture.
Si surge un bloqueo material real, identificarlo y continuar lo independiente;
no fragmentar el paquete ni encubrirlo con un verde parcial.

Handoff autocontenido más evidencia enlazada:

1. QUÉ HACE y POR QUÉ EXISTE; veredicto por superficie y etapa, no «app terminada».
2. Base/HEAD, delta completo, archivos nuevos/eliminados, hashes y preservación.
3. Inventario beta antes/después: retirado, inerte preservado y controles locales
   reutilizados; tráfico, jobs, identidades y colas comprobados.
4. Interoperabilidad de 24/24, comandos, XML frescos, cinco APK y hashes.
5. Matriz de los veinte controles, findings corregidos, residuales y fuentes.
6. Revisión independiente de contrato/actualizador y de datos/privacidad.
7. QA compilada/ejecutada, dispositivo/artefacto exacto, omitidas y restauración;
   si no se ejecutó, plan listo con clasificación destructiva y requisitos.
8. Expediente de firma/upgrade/publicación y decisiones materiales faltantes.
9. Confirmación de ausencia de staging/commit, secretos, dispositivos o efectos
   remotos no autorizados. MAIN recibe el mismo checkout; no hay cherry-pick.

No aceptar tus residuales por MAIN. Al entregar, detenerte y devolver el único
rol escritor; una continuación posterior retoma esta misma capacidad desde la
base/artefactos que MAIN indique, sin reconstruir lo ya validado.
