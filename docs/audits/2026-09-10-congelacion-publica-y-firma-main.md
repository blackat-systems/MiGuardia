# Preparación de congelación pública y firma — MAIN

Fecha de trabajo: 9–10/09/2026 en Argentina, 10/09/2026 UTC.

**VEREDICTO: candidata 3 y origen controlado 2 firmados y verificados; QA instalada pendiente.**

## Par firmado para ensayo de actualización

Continuación autorizada desde `02a09e8effab13c35e69754037f77bc434707dac`,
checkout limpio. Se comprobó el hash de la candidata 3 sin modificarla y se
repitió el plan del origen 2: `PLAN_ONLY`, custodia existente COMPLETE y hash
coincidente. Joaquin introdujo la contraseña sólo en la consola clásica
separada. MAIN no leyó su entrada ni volvió a firmar la candidata 3.

El origen 2 pasó el verificador separado sin cambios adicionales de herramienta:
`SIGNED_ARTIFACT_CHECKS_PASSED`. Evidencia:
`.local-evidence/release/public-2.0.0-baseline-code2/signed-origin2-verification.json`.
Recibo original `sign-origin2/signing-unverified.json` conservado.

| Rol | Versión/código | Bytes firmados | SHA-256 firmado |
|---|---|---:|---|
| Origen de ensayo, no distribuido | 2.0.0-beta.1 / 2 | 12.729.677 | `5345a2b460b2b16244a7e3ba20035b92c6af857d3f5fb1ff12c3d69a1cdb778f` |
| Candidata pública, aún no distribuida | 2.0.0 / 3 | 12.713.293 | `0e2396fe6403be183c090b950ab590ee24505e07783a8eac30207516d5cc256d` |

Ambas conservan `com.blackatsystems.miguardia`, certificado permanente único,
v2/v3 verificadas por intervalo, minSdk 26 y targetSdk 37. Los dos payloads
coinciden con sus respectivos unsigned aprobados; no se afirma igualdad entre
versiones. El origen tiene 551 entradas, ninguna añadida y SHA canónico
`96ab94ba8972edb3cd4e0b1e275d8c49c942981976faade9c7034e32bfaaa2e1`.
Se recalcularon ambos hashes firmados; coinciden con recibos e informes.
No se recompiló ni se repitieron pruebas de código sin cambios.

**Propuesta de aislamiento, todavía no ejecutada ni autorizada:** AVD nuevo
`MiGuardia_Public_Upgrade_API33_20260910`, serial exclusivo propuesto
`emulator-5582` (disponibilidad por comprobar antes de iniciar), imagen existente
`system-images;android-33;google_apis;x86_64`. Se verificó sólo su presencia en
disco; sin comandos ADB/emulator. Ensayo de las dos Release completas anteriores
y QA/test congeladas, datos exclusivamente ficticios, root sólo del emulador
para obtener copias inactivas de las 27 tablas/archivos y compararlas sin exportar
claves. Instalación, actualización, fixtures destructivas sólo allí, efectos de
avisos/Widget sólo del ensayo, y retirada final de ese entorno/datos propios.
Samsung y otras instalaciones excluidos. Sin descarga de imagen, cambios de
escala del sistema, publicación, alarma exacta o reboot físico.

El ensayo local de upgrade no acredita descarga HTTPS/SAF de una oferta real:
ese recorrido continúa necesitando el servicio controlado del contrato.

## Continuación: firma real de candidata código 3

Desde checkpoint limpio `d2e4558d0fc332d95309e3bfa28b23d5f33181fe`, Joaquin
confirmó presencia frente a la PC y ausencia de grabación. Se abrió una consola
clásica privada para el helper autorizado. La contraseña fue introducida allí;
MAIN no capturó su entrada ni leyó la consola. Se utilizó la clave existente,
sin regenerarla. El recibo de emisión se conserva como `SIGNED_UNVERIFIED`,
sin reescribirlo después de la aceptación independiente.

El primer verificador rechazó un aviso de SDK 37 exclusivo de API 26–27:
`WARNING: APK Signature Scheme v2 signer #1: APK Signature Scheme v2 signer: 0 references unknown APK signature scheme ID: 3`.
apksigner terminó con exit 0 y v2 válida. MAIN y un revisor independiente
confirmaron que `getSupportedSchemeNames` restringe ese intervalo al esquema 2;
el atributo anti-stripping que referencia 3 produce el aviso. El mismo APK
verifica v3 sin avisos en el control general y API 28–37. Se contrastó el bytecode
del JAR oficial instalado y el [verificador AOSP](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/master/src/main/java/com/android/apksig/internal/apk/v2/V2SchemeVerifier.java).

Corrección exclusiva de herramienta: una aparición exacta se admite sólo en el
control 26–27 y se registra en `accepted_warnings`. Los otros controles, pin,
inventario estructural y payload siguen obligatorios. Otros avisos, duplicados,
errores, certificado distinto o v2 fallida se rechazan. Dos métodos nuevos
cubren la excepción y sus rechazos; primera ejecución falló por interfaz aún
inexistente, después **79/79** pruebas Release aprobaron. El fallo real previo
se conserva en `diagnose_signed.py` y en la salida de diagnóstico de esta sesión.
No se recompiló Android ni se volvió a firmar para sortear el aviso.

**Verificación real separada: `SIGNED_ARTIFACT_CHECKS_PASSED`.**

- Artefacto: `.local-evidence/release/public-2.0.0-freeze/sign-candidate3/signed-unverified.apk`.
- Tamaño: **12.713.293 bytes**; SHA-256 firmado:
  `0e2396fe6403be183c090b950ab590ee24505e07783a8eac30207516d5cc256d`.
- Paquete `com.blackatsystems.miguardia`, `2.0.0`/3, no depurable/test-only.
- Certificado permanente coincidente en v2 y v3, sin rotación ni otras autoridades.
- 551 entradas de payload idénticas, ninguna añadida. SHA canónico:
  `7bef3aa8c1c5c1e4a0c325355bbc82fb97bed31b3ffe9bb592cbda0100aa6624`.
- Permisos, componentes, ABI, metadatos y alineación conservados. Hash firmado
  igual al recibo original: el verificador no modificó la APK.
- Informe: `.local-evidence/release/public-2.0.0-freeze/signed-candidate3-verification.json`.

El nombre original `signed-unverified.apk` se preserva por trazabilidad: la
aceptación está en el informe separado. No es publicación ni instalación.
El origen completo código 2 continúa unsigned. Ningún dispositivo, emulador,
servicio remoto o clave adicional se utilizó en esta continuación.

Revisión independiente final aceptada: diff, excepción, informe y hash firmado
contrastados, sin hallazgos nuevos. Guardas PowerShell repetidas: **33/33**, sin
claves reales. Control documental y diff aprobados. Cambios de esta continuación:
verificador, sus pruebas, README, auditoría y STATUS; no fuentes Android,
Gradle, política congelada o artefactos. Checkpoint local permitido por MAIN;
su SHA exacto se obtiene de Git. Sin push ni tag.

Las secciones siguientes conservan la evidencia de la preparación anterior;
sus menciones a firma pendiente describen ese momento, no esta continuación.

## Alcance y base

Continuación de etapas 20–21 desde `c985b6a981846599f224ac1e080a0199fa565a72`,
rama `codex/miguardia-2.0`, entrada limpia. Android público ya estaba integrado
en `d0105e039ad07c56f8c5ee9c4c1c5a37156efdfe`; no se reimplementó ni se volvió
a presentar el handoff de `3e88bc9` como una candidata nueva.

Joaquin aclaró que no distribuyó una APK ni la tiene instalada. MAIN fijó
`versionName=2.0.0`, `versionCode=3`, conservando paquete, SDK, dependencias,
permisos, componentes, reglas laborales, Room V6 y sus seis esquemas.

El correo público de soporte/privacidad ya confirmado sigue siendo
`blackat.systems@gmail.com`. No se accedió al buzón ni se implementó feedback.

## Origen controlado para actualización

Se conservó la aplicación pública completa de código 2, no una mini fixture:

- Directorio ignorado: `.local-evidence/release/public-2.0.0-baseline-code2/`.
- APK unsigned: 12.717.729 bytes; SHA-256
  `a40adcf1cb75f9f4324ae113528b99e79f569a98b0f9076bf60338279b21fc9e`.
- Política anterior: SHA-256
  `16153441ceb0fe82f4cb3ad0d036f14e9e4f18db4f64cead46abe89bd0f25bf5`.
- Informe previo: `.local-evidence/release/public-main-20260909-unsigned.json`.

Ese origen no se declara distribuido. Dos aplicaciones completas de código
creciente, firmadas con el certificado permanente, son necesarias para el ensayo
posterior. Conservar un unsigned no acredita su firma o instalación.

## Herramientas de firma y aceptación

Se agregaron `tools/release/sign_release.ps1` y
`tools/release/verify_signed_apk.py`, con sus respectivas pruebas. README y
política se actualizaron dentro del mismo alcance. No se agregaron dependencias.

El helper PowerShell es sólo plan por defecto. Su ejecución real exige consola
Windows clásica separada y visible, entrada/salida no redirigidas, confirmación
del operador de privacidad y ausencia de grabación, custodia COMPLETE y hashes
concordantes. La contraseña se introduce directamente en la herramienta oficial:
no se admite por argumentos, entorno, archivo ni transcript de Codex. Las guardas
no pueden demostrar que no exista un grabador externo o un sistema comprometido.

Se reutiliza la única clave existente fuera del repositorio. No hay generación,
importación, cambio de alias ni regeneración de respaldo. Antes y después se
comprueban archivo cifrado y permisos privados; se conservan parciales ante fallo.
La emisión produce únicamente `SIGNED_UNVERIFIED`.

Política de emisión: v1 desactivada, v2 y v3 activadas, v4 desactivada; sin rotación
ni source stamp. El verificador separado exige unsigned/informe/política por hash,
firma oficial válida para los intervalos Android, certificado permanente único,
payload exacto, identidad, permisos, componentes, ABI y alineación intactos.
La lectura estructural acotada de v2/v3 no sustituye la criptografía de apksigner.
No se omiten indiscriminadamente las entradas `META-INF`.

Pin público permanente conservado:
`AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D`.

MAIN y revisores internos contrastaron las implementaciones y sus guardas.
El plan real del origen código 2 terminó en `PLAN_ONLY`, con custodia existente
y hash coincidentes, sin abrir PKCS12, pedir contraseña, crear salida o firmar.
Un primer intento con separadores `C:/` fue rechazado por la guarda de rutas;
el segundo usó rutas Windows absolutas `C:\`. No se relajó esa validación.

Joaquin indicó después `C:\Users\Joaquin\Desktop\MiGuardia-Firma` como carpeta
que contiene la firma, no como destino de la APK. MAIN comprobó exclusivamente
estructura, registro público y hashes: `release-key-01/release-signing.p12`
coincide byte a byte con el almacén de trabajo; registro COMPLETE y certificado
permanente concordantes. No se abrió PKCS12, leyó contraseña, movió archivos ni
cambió la custodia operativa. Esta copia en el mismo equipo no sustituye la
independencia del respaldo USB histórico, que no fue revalidado en esta pasada.

## Verificaciones ejecutadas

- 825 JVM locales: dominio 379, database 12, app 434.
- 26 de interoperabilidad: 24 públicos y dos históricos; total XML 851, cero
  fallos, errores u omisiones.
- Herramientas Release Python: 77/77; incluyen 31 del verificador firmado.
- Guardas PowerShell de firma: 33/33, con dobles, sin claves ni firma real.
- Herramientas generales: 73/73.
- Control documental y `git diff --check`: aprobados antes del cierre.
- Escaneo local de secretos sobre árbol/historia y primera Release nueva:
  861 archivos, 2.369 blobs alcanzables y 551 entradas APK; cero señales en los
  patrones cubiertos. No es garantía universal ni consulta de vulnerabilidades.

Logs de herramientas bajo `.local-evidence/release/public-2.0.0-freeze/`:
`release-tools.log`, `signing-guards.log`, `general-tools.log`.

La corrida `full` terminó sus etapas de pruebas, pero su build se interrumpió
sin resultado final. MAIN observó ausencia de sesión, propietario PID 14652 y
procesos Java/Python. Se conservaron todos los registros y el lock original en
`full/interrupted-owner-lock.json`; la causa de la terminación no está probada.
No se cambió el JSON incompleto para atribuirle éxito.

Se reinició exclusivamente build/lint con reconstrucción explícita en
`build-resumed`: **BUILD SUCCESSFUL, 21m13s, 335/335 tareas ejecutadas**, cinco
APK generados y lint con cero errores/seis advertencias de versiones. Insumos
Android intactos antes/después. Se copiaron los cinco a `first/`, comprobando
cada SHA-256 contra el manifiesto de esa ejecución; el runner inventaría APK,
pero no congela sus bytes automáticamente.

| Primer artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 18.051.295 | `3cb340f395f04a8e5d4237bc5518e2a41655a8187aa3815d464dc469467d66cf` |
| QA | 17.936.200 | `d71623d3393d11c07efe6a9727b667e3f522fc5cfa37cae6c5f44846c34f16a4` |
| Release unsigned | 12.701.333 | `ddc8c265aa242f9952e7cbd3b470841a24ef327087c26810cb2c9c5f3452f7ad` |
| QA AndroidTest | 2.371.041 | `ee31c693f7e3fe10f43f79cc27a5ed4509b2689fb17d6377691dd170c5b6003a` |
| Database AndroidTest | 4.184.074 | `b6827a38006eb01adbc3b6af33460437bb194b93b415ddae9a5aac9413f9e315` |

La primera Release pasó el inspector unsigned (`unsigned1.json`). Su plan de
firma también pasó en modo `PLAN_ONLY`; no creó salida ni firmó. Una segunda
reconstrucción exclusivamente Release se inició con el mismo runner sin editar,
seleccionando `:app:assembleRelease` y `--rebuild`; orquestación exacta en
`rebuild_release.py`, plan/log en `rebuild2/`. Terminó en **7m34s, 118/118 tareas
ejecutadas**. No equivale a una copia o repaquetado. El segundo APK se conservó
en `second/` y pasó su inspección independiente (`unsigned2.json`).

La comparación dio **BYTE_IDENTICAL**: ambos APK tienen 12.701.333 bytes y
SHA-256 `ddc8c265aa242f9952e7cbd3b470841a24ef327087c26810cb2c9c5f3452f7ad`.
Identidad `com.blackatsystems.miguardia`, versión `2.0.0`/3, SDK 26/37/37,
nueve permisos/componentes, ocho ELF en cuatro ABI, sin splits y alineación
ZIP/PT_LOAD de 16 KiB verificados. Esto no acredita arranque en páginas de 16 KiB.

El runner de la segunda reconstrucción conservó `inputs_unverified` porque
falló su consulta Git final (`ls-files`); la etapa Gradle sí quedó `passed`,
exit 0, con APK/manifiesto. MAIN no reescribió ese resultado. Repitió la captura
de insumos: **535 Android y 36 web idénticos** a los registros de entrada y
pruebas; HEAD conservados. También contrastó los SHA-256 de los 95 XML archivados
y de ambos APK frente a sus respectivos manifiestos. La conciliación explícita
está en `freeze-summary.json`, generado por `summarize.py`, con captura nueva,
error original y resultado `UNSIGNED_FREEZE_CHECKS_PASSED`.

Esta aceptación describe esos dos builds y los mismos insumos. No garantiza
reproducción en otra máquina, no considera el commit una nueva ejecución y no
autoriza usar el hash unsigned en el catálogo público.

La política código 3 se conserva también byte a byte en
`.local-evidence/release/public-2.0.0-freeze/policy.json`, coincidente con los
informes unsigned: `9c8ace4bfe9bd6dec44778f5ef7e9c3c85f13e48fee10b539c8ec6fa54e8fbdc`.
La firma posterior debe usar esa política y esos artefactos aprobados, no
insumos reconstruidos o una política que haya cambiado después.

## Límites y continuidad

No se firmó una APK, ejecutó ADB, consultó Samsung, inició emulador, instaló
paquete, publicó, hizo push o movió referencias protegidas. Nueve worktrees
preservados. Website permaneció en `394964b98be87ccfd6a56877398545deb9848380`,
sólo lectura; API/corpus y cambios preexistentes conservados.

No atribuir las pruebas herméticas de firma a una emisión real. No atribuir
JVM o compilación a avisos R03, instalación, SAF OEM, actualización con datos,
TalkBack o preservación de Keystore instalado.

Antes del ensayo instalado falta confirmar el aislamiento específico propuesto:
AVD nuevo y desechable API 33 usando imagen ya instalada, datos ficticios,
Samsung excluido. Esa autorización aún no fue recibida. No se permite limpiar
QA Samsung para ejecutar fixtures destructivas.

La firma requiere interacción privada de Joaquin. Publicación espera aceptación
de artefacto firmado y QA; el catálogo público continúa sin APK. La recolección
de feedback permanece apagada y sus campos/retención no se inventaron.

## Preparación del ensayo: revisión independiente, no ejecución

Un revisor interno contrastó las herramientas existentes con etapa 21:

- `qa.py` y las fixtures Activity operan sobre QA, no pueblan ni certifican la
  Release. Los escenarios de una Release completa deben cargarse por UI pública;
  las filas genéricas de los tests del comparador no son datos funcionales.
- `upgrade_snapshot.py` compara copias consistentes, pero no las obtiene. Release
  no es depurable y tiene backup Android desactivado. Para comparar su sandbox
  completo se propone lectura privilegiada exclusivamente en el AVD desechable,
  con escritores detenidos y sin extraer claves. Capacidad y autorización de ese
  aislamiento deben comprobarse antes de ejecutar; una Copia portable no sustituye
  todos los archivos privados de Bloqueo/Widget.
- Una instalación nueva de código 2 público no crea identidad beta histórica.
  El ensayo puede verificar ausencia conservada, no prometer que usó y conservó
  una clave beta preexistente. No introducir una clave o sembrador por inferencia.
- Reemplazo por ADB, apertura manual desde Archivos y descarga→verificación→SAF
  son superficies diferentes. El último recorrido requiere oferta controlada
  en el origen HTTPS fijado; el Site actual sin publicación no la proporciona.
  No cambiar TLS/origen o aprobar el catálogo sólo para ocultar ese pendiente.

Estos límites no reabren la implementación pública. Separan el upgrade local
con datos de la prueba completa del actualizador y de cualquier QA Samsung/OEM.
