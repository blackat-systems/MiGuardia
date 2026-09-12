# Dependencia 29 — Preparar APK beta oficial para distribución web limitada

- Estado: **PENDIENTE — NO EJECUTAR TODAVÍA**
- Fecha: 2026-09-03
- Autoridad: decisión explícita de Joaquin y ADR 0039
- Rama integradora: `codex/miguardia-2.0`
- Base funcional protegida:
  `924fd46df018f5e14637dfe90a983fefdbf27af4`

## QUÉ HACE

Prepara una versión Release reconstruible y auditable de MiGuardia 2.0 para que,
en una puerta posterior, pueda firmarse y ofrecerse como APK beta oficial desde
la web existente de Joaquin. Fija su número de versión, audita el paquete y crea
comprobaciones repetibles del artefacto sin utilizar secretos.

El resultado de esta dependencia es:

`RELEASE PREPARADA LOCALMENTE — SIN FIRMA REAL NI PUBLICACIÓN`

## POR QUÉ EXISTE

La aplicación ya está funcionalmente cerrada, pero el APK Release actual no es
una entrega oficial: conserva la numeración interna anterior y no tiene firma de
distribución. Antes de crear una clave permanente o publicar un archivo hay que
demostrar que el candidato correcto se puede construir, identificar e
inspeccionar sin mezclar secretos ni acciones externas. Este trabajo espera a
que portal, página pública, acceso beta, feedback, aporte y actualizaciones
queden integrados, para no numerar ni congelar un APK anterior a esas funciones.

## ROLE

Sos la única dependencia implementadora activa de MAIN para preparar el
candidato técnico de distribución. Trabajás directamente en el checkout
compartido. No sos MAIN, no modificás documentación canónica y no abrís otras
tareas.

## PUERTA 0

Antes de editar:

1. verificá la ruta exacta
   `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
2. verificá la rama `codex/miguardia-2.0`;
3. verificá que
   `924fd46df018f5e14637dfe90a983fefdbf27af4` sea ancestro del HEAD;
4. verificá que el HEAD sea el checkpoint documental exacto informado por MAIN
   al entregar este prompt;
5. exigí checkout limpio, índice vacío y upstream sin divergencia inesperada;
6. verificá autor `joaquin <blackat.systems@gmail.com>`, remoto privado y
   worktrees existentes sin tocarlos;
7. confirmá JDK, wrapper, SDK y espacio disponible;
8. detenete ante cualquier diferencia no explicada. No uses reset ni descartes
   trabajo.

## LECTURA OBLIGATORIA

Leé completamente y en el orden indicado por `AGENTS.md`:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
7. ADR 0011, 0016, 0024, 0035, 0036, 0038 y 0039;
8. `docs/PROMPT_MAESTRO_MAIN.md` como contrato histórico solamente;
9. la última auditoría integral y las auditorías de compatibilidad API 26, 33,
   36 y 37;
10. `app/build.gradle.kts`, manifiestos fusionados, configuración Release,
    `.gitignore`, código de permisos/clima y pruebas relacionadas.

No hace falta releer fichas sectoriales porque esta dependencia no puede cambiar
horas, vocabulario ni reglas de ningún rubro.

## TASK

Preparar y verificar localmente el candidato Release de MiGuardia 2.0 para una
futura distribución web limitada, manteniéndolo deliberadamente sin firma final
y sin publicarlo.

## CONTEXT

- MiGuardia 2.0 es una instalación limpia y local-first.
- La matriz vigente está verde en Android API 26, 33, 36 y 37.
- Room vigente es V6 y sus esquemas están protegidos.
- La primera distribución externa será gratuita, de prueba y fuera de Google
  Play.
- Joaquin ya tiene un sitio web. La página pública, el portal, el contrato API y
  la interfaz Android de acceso deben estar cerrados antes de habilitar este
  stage.
- El piloto parte de diez plazas. Participar es voluntario, pero cada plaza
  aceptada debe completar una devolución honesta a los quince días. Ese
  contrato no se implementa ni redefine en esta dependencia.
- Android exige que toda APK instalable esté firmada. La clave definitiva debe
  conservarse durante la vida de la aplicación; por eso no se crea en este
  stage.

## INPUTS

- Base funcional auditada en `924fd46df018f5e14637dfe90a983fefdbf27af4`.
- `applicationId` definitivo para esta beta:
  `com.blackatsystems.miguardia`.
- Versión visible aprobada: `2.0.0-beta.1`.
- Código inicial aprobado: `2`.
- Nombre propuesto del futuro archivo firmado:
  `MiGuardia-2.0.0-beta.1.apk`.
- `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37` y Java 17.
- Release actual sin minificación ni reducción de recursos.
- Artefactos y credenciales deben permanecer fuera de Git.

## OUTPUT

1. `versionName = "2.0.0-beta.1"` y `versionCode = 2` en la única fuente real de
   configuración.
2. `applicationId`, SDK, paquete QA y contratos funcionales sin cambios.
3. Build Release universal y sin firma final, producido de forma repetible.
4. Una comprobación local, segura y repetible que inspeccione como mínimo:
   paquete, versión, SDK, manifiesto, permisos, componentes exportados,
   `debuggable`, `testOnly`, alineación de 16 KiB, tamaño, estado de firma y
   SHA-256.
5. Un comando y checklist exactos para la futura puerta de firma. No conectes
   todavía un `signingConfig` a Release ni agregues una tarea que solicite o
   dependa de credenciales.
6. Evidencia de que ningún secreto, APK, AAB, keystore ni propiedad local quedó
   rastreado.
7. Revisión vigente de las condiciones oficiales de distribución directa de
   Android, verificación de desarrollador y uso no comercial de Open-Meteo. Si
   aparece una incompatibilidad material, detenete y reportala; no aceptes
   términos, crees cuentas ni contrates planes.
8. `HANDOFF A MAIN` completo con el estado exacto y las puertas todavía
   pendientes.

## SCOPE

Podés modificar únicamente:

- `app/build.gradle.kts` para versión y configuración Release mínima;
- `tools/release/**` para verificadores locales sin secretos, si resulta útil;
- `app/src/test/**` exclusivamente para contratos de metadatos o build que
  puedan probarse de manera estable.

Sólo si una prueba reproducible demuestra que es imprescindible, detenete y
pedí a MAIN ampliar una ruta. No modifiques por conveniencia `.gitignore`,
Gradle raíz, catálogo de versiones, manifiesto, código productivo o recursos.

## DEPENDENCIES

- La aplicación integral y la matriz Android deben continuar verdes.
- Deben estar cerrados la página pública de MiGuardia, el portal beta y su
  contrato API, el acceso Android, el feedback a los quince días, el destino web
  de aporte, el botón visible `Apoyar MiGuardia` y la actualización manual
  preservando datos.
- No hay otra dependencia implementadora activa.
- La clave Release, la firma, la carga del APK y la publicación continúan como
  puertas posteriores.

## DO NOT

- No crees, importes, regeneres ni pruebes una clave de firma final.
- No conectes `signingConfig` a Release en este stage.
- No uses la clave debug como clave oficial ni prepares una clave descartable.
- No solicites, muestres, guardes o confirmes contraseñas, alias o secretos.
- No produzcas ni presentes una APK unsigned como APK oficial instalable.
- No firmes, instales, reemplaces ni abras
  `com.blackatsystems.miguardia`.
- No uses ADB, Samsung ni emuladores en esta dependencia.
- No subas archivos ni modifiques la web, DNS, hosting o límites de descarga.
- No abras Google Play Console ni Android Developer Console; no crees cuentas,
  aceptes términos ni registres paquete o certificado.
- No redefinas el acceso beta ya integrado, no crees un instalador, descarga en
  segundo plano, DRM, allowlist adicional o contador nuevo dentro de la app.
- No agregues `REQUEST_INSTALL_PACKAGES` ni permisos nuevos.
- No agregues dependencias, servicios, receivers, red adicional, cuentas, nube,
  sincronización, anuncios, pagos embebidos, analítica o telemetría.
- No cambies `applicationId`, SDK, Room, DataStore, Copias, cifrado, clima,
  comportamiento funcional, accesibilidad ni privacidad.
- No actives minificación o reducción de recursos en esta primera beta.
- No modifiques documentación canónica, ADR, auditorías, STATUS ni este prompt.
- No hagas commit, push, tag, GitHub Release, merge, rebase, reset, descarte,
  rama o worktree.
- No toques `main`, producción ni datos reales.

## CONTRACTS

### Identidad y actualización

- `applicationId` permanece `com.blackatsystems.miguardia`.
- La primera beta usa `versionName 2.0.0-beta.1` y `versionCode 2`.
- `2.0.0` queda reservado para la promoción estable y debe usar un
  `versionCode` mayor que toda beta distribuida.
- Cada APK que llegue a distribuirse después debe usar un `versionCode` mayor.
- Las actualizaciones futuras requieren el mismo certificado de firma.
- Una instalación firmada con debug u otra clave no puede actualizarse con la
  clave oficial. Desinstalar borra datos locales y nunca se hará sin advertencia
  y acción consciente.

### Artefacto

- La descarga web futura será un único APK universal; no un AAB ni un conjunto
  incompleto de splits.
- El APK preparado en esta etapa permanece unsigned y no se publica.
- Release debe resolver sin sufijo QA, `debuggable=false` y `testOnly=false`.
- No se modifica el APK después de su futura firma.
- El SHA-256 de esta etapa identifica sólo el candidato unsigned y no debe
  confundirse con la huella futura del archivo firmado.

### Distribución web

- La instalación será consciente y Android puede pedir permiso para instalar
  apps desconocidas al navegador o gestor de archivos elegido.
- MiGuardia no necesita convertirse en instalador para ser descargada desde una
  web.
- Limitar descargas no evita que una persona copie un APK ya descargado.
- El sitio ya existe; no se crea otro. Su hosting se inspeccionará antes de
  elegir el control de plazas.
- El contrato inicial son diez plazas individuales. Descargar o reanudar no
  consume una plaza; el canje ocurre dentro de Android.
- Página pública, portal, claves, feedback y actualización manual ya deben
  llegar cerrados a esta dependencia. Este stage los audita como parte del APK,
  pero no modifica sus reglas ni crea datos reales.
- El APK no puede contener códigos beta, claves privadas del servidor, secretos
  HMAC, IMEI, serial, Android ID, identificador publicitario ni datos laborales.

### Privacidad y proveedores

- La página futura deberá explicar permisos y privacidad con lenguaje claro.
- El clima sigue siendo opcional y por objetivo. Revalidá uso y atribución de
  Open-Meteo para una beta gratuita limitada, sin contratar ni cambiar proveedor
  automáticamente.
- La gestión de verificación de desarrollador Android queda como puerta externa
  posterior al certificado definitivo.

## VALIDATION

Ejecutá serialmente y con tareas frescas:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:testReleaseUnitTest `
  :app:lintDebug `
  :app:lintRelease `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Además:

1. obtené conteos reales desde XML;
2. inspeccioná el APK Release con herramientas del SDK, no sólo por el nombre;
3. comprobá package, `versionCode`, `versionName`, SDK, permisos y componentes;
4. comprobá que no sea debug ni test-only;
5. comprobá que sea un APK completo y universal;
6. comprobá que no existan splits y que conserve las cuatro ABI actuales:
   `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`, con sus bibliotecas nativas
   esperadas;
7. ejecutá la comprobación de alineación con soporte de páginas de 16 KiB;
8. ejecutá `apksigner verify` y registrá como resultado esperado que el
   candidato unsigned no verifica. Ese exit code esperado no es un fallo del
   stage;
9. calculá tamaño y SHA-256 del candidato unsigned;
10. ejecutá dos builds Release consecutivos con `--rerun-tasks`, preservá ambos
    artefactos en una carpeta temporal fuera de Git y compará sus SHA-256. Si no
    son idénticos, compará nombres, tamaños, CRC y contenido canónico de todas
    las entradas ZIP; declaralo `reconstruible, no byte-reproducible` y no
    ocultes la diferencia;
11. comprobá que los hashes de esquemas Room 1–6 no cambien;
12. ejecutá `git diff --check` y revisá todos los archivos modificados/nuevos;
13. buscá secretos, keystores, propiedades locales y binarios rastreados;
14. comprobá que Debug y QA conserven su conducta anterior.

No llames `INSTRUMENTACIÓN EJECUTADA` a una compilación de AndroidTest. La QA
física de la identidad Release pertenece a la puerta de firma posterior.

## STOP POINTS

Detenete y devolvé `MAIN BLOQUEADA` si:

- Puerta 0 no coincide;
- la declaración durable de Joaquin y ADR 0039 sobre que todavía no existe una
  entrega oficial con `versionCode = 2` resulta contradicha por evidencia real;
- preparar Release exige cambiar comportamiento, manifiesto, permisos,
  dependencias, SDK, Room o privacidad;
- el artefacto resulta firmado por debug u otra clave inesperada;
- no puede producirse un APK universal;
- aparece un secreto, keystore o binario rastreado;
- las condiciones vigentes de Android u Open-Meteo contradicen esta
  distribución;
- falla cualquier prueba o auditoría requerida;
- se necesita una cuenta, pago, aceptación externa, clave o publicación.

## HANDOFF A MAIN

Entregá:

```text
# HANDOFF A MAIN — Preparar APK beta oficial para distribución web limitada

## QUÉ HACE
## POR QUÉ EXISTE
## VEREDICTO
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## VERSION AND PACKAGE
## RELEASE ARTIFACT
## VALIDATION
## ROOM
## PRIVACY AND PROVIDERS
## SIGNING GATE
## WEB DISTRIBUTION GATE
## RISKS
## PENDING
## GIT
## NEXT
```

Separá siempre:

- `VERIFICADO`;
- `INFERIDO`;
- `PENDIENTE`;
- `NO EJECUTADO`.

El handoff debe comenzar con:

`RELEASE PREPARADA LOCALMENTE — SIN FIRMA REAL NI PUBLICACIÓN`

o, ante un bloqueo real:

`MAIN BLOQUEADA`

## DONE WHEN

- La versión quedó en `2.0.0-beta.1` / código `2` y el paquete no cambió.
- El build Release universal se genera localmente sin firma final.
- Su identidad, manifiesto, seguridad, alineación, tamaño y SHA-256 están
  comprobados de forma repetible.
- La batería completa está verde y Room permanece byte a byte estable.
- No existen secretos, claves, APK ni configuración local rastreados.
- Las condiciones de Android, verificación y Open-Meteo fueron revisadas desde
  fuentes oficiales vigentes y sus pendientes están declarados.
- Firma, prueba Release física, límite web, formulario, publicación, tag,
  GitHub Release, `main` y producción siguen sin ejecutarse y claramente
  separados.
- El checkout se entrega sin staging, commit ni push y con un handoff verificable.
