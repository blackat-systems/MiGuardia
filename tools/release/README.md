# Inspección local de Release unsigned

Herramienta de biblioteca estándar Python 3.11+, sin paquetes nuevos. Reutiliza
`tools/verify.py` para hashes, Java y ejecución nativa sin shell. No ejecuta
Gradle, ADB, red, firma, instaladores ni operaciones sobre el sitio.

`policy.json` congela la identidad de entrada del candidato público y la superficie del APK
Release originada en base `c985b6a981846599f224ac1e080a0199fa565a72`: ocho permisos
después de retirar `ACCESS_NETWORK_STATE` residual beta (Q01, 10/09/2026),
nueve componentes y ocho bibliotecas nativas, sin comprimir, en cuatro ABI.
También conserva exactamente la declaración de
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` con `protectionLevel=signature`
(valor 2), y la ausencia de permiso global de aplicación. Omitir, debilitar,
duplicar o agregar una declaración de permiso falla la inspección.
Incluye los componentes transitivos existentes: `ProfileInstallReceiver`
exportado **sigue protegido por `android.permission.DUMP`**, los dos providers
siguen privados y el servicio Room no se exporta. No se genera una lista
permitida automáticamente desde el APK que se pretende aprobar.

## Pruebas sin Android

```powershell
python -m unittest discover -s tools/release/tests -v
```

Las fixtures son metadatos y pequeños ZIP sintéticos creados en temporales del
test; no se confirman APK, binarios o claves en Git.

## Inspeccionar el artefacto existente

Desde la raíz del checkout, con las herramientas **ya instaladas**, por ejemplo:

```powershell
python tools/release/inspect_apk.py inspect app/build/outputs/apk/release/app-release-unsigned.apk `
  --build-tools "$env:LOCALAPPDATA/Android/Sdk/build-tools/37.0.0" `
  --output .local-evidence/release/unsigned-first.json
```

Java se resuelve desde `JAVA_HOME` o PATH; `--java` permite un ejecutable nativo
explícito. No se invocan `.bat`, `.cmd` ni una shell. La herramienta nunca cambia
el APK. El JSON de evidencia debe ser nuevo, bajo `.local-evidence/release/`;
no se sobrescribe una evidencia previa. Sin `--output`, se imprime el JSON.

Se inspeccionan, mediante `aapt2`, manifiesto y badging independientes: paquete,
`2.0.2`/código 6, minSDK 26, target/compileSDK 37, flags de seguridad,
permisos, componentes y exportación. Se rechazan instrumentation, QA/debug,
splits, bundles, bibliotecas extra/faltantes/comprimidas, firmas inesperadas,
ZIP duplicados, traversal, enlaces y límites excesivos. Se calcula SHA-256 y se
comprueba que el archivo no cambie durante la inspección.

La alineación ZIP se comprueba con `zipalign -c -P 16 -v 4`. Además se leen las
cabeceras ELF reales de las ocho bibliotecas: clase/máquina por ABI, segmentos
`PT_LOAD` con `p_align` potencia de dos y al menos 16384, y congruencia de offset
y dirección virtual. Se inventarían los segmentos `PT_GNU_RELRO`, cuyo `p_align`
no es la alineación de página de `PT_LOAD`. Ambos controles son estáticos;
**no** acreditan ejecución real con páginas de 16 KiB ni QA de cada API.
`apksigner.jar verify --verbose --print-certs` debe rechazar exactamente la
ausencia de `META-INF/MANIFEST.MF`. Un error arbitrario, una firma inválida o
una firma válida inesperada fallan el control; no cuentan como unsigned.

Salida 0: controles del candidato unsigned aprobados. Salida 1: artefacto que
contradice la política. Salida 2: entrada, SDK, parser o evidencia inválidos.
El éxito **no** significa APK instalable, publicación oficial,
ausencia demostrada de todo secreto embebido, firma, publicación o QA física.
La búsqueda de secretos y los esquemas Room se revisan además en las fuentes.

## Dos reconstrucciones

MAIN ejecuta los dos builds autorizados, uno por vez, conserva ambos APK fuera
de Git y registra sus mismos insumos. Este script sólo compara archivos ya
existentes; no reconstruye ni atribuye frescura por nombre o fecha.

```powershell
python tools/release/inspect_apk.py compare `
  .local-evidence/release/build-1/app-release-unsigned.apk `
  .local-evidence/release/build-2/app-release-unsigned.apk `
  --output .local-evidence/release/reconstruction.json
```

- `BYTE_IDENTICAL`: SHA-256 de ambos archivos idéntico.
- `ZIP_CONTENT_IDENTICAL_NOT_BYTE_REPRODUCIBLE`: mismos nombres, tamaños, CRC
  y SHA-256 del contenido descomprimido de **cada** entrada, pero distinto ZIP.
  Puede describirse como reconstruible, no byte-reproducible, sólo junto con
  la evidencia de los dos builds y sus insumos.
- `ZIP_CONTENT_DIFFERS`: se enumeran entradas agregadas, retiradas y cambiadas.
  Requiere diagnóstico; no se oculta bajo una afirmación de reproducibilidad.

La comparación canónica ignora orden, timestamps, compresión, offsets y bloques
de firma; no reemplaza inspeccionar cada APK por separado. No extrae entradas.

## Firma interactiva y verificación separada

`sign_release.ps1` prepara un plan por defecto. `-Execute` sólo puede usarse
después de aceptación MAIN, en consola Windows clásica separada y visible, con
confirmaciones de ausencia de grabación. No ejecutar desde la terminal de una
herramienta, no capturar/transcribir esa ventana y no pasar contraseñas por
argumentos, archivos o entorno. La contraseña la recibe directamente apksigner.
El helper reutiliza las guardas de custodia, no su entrada de generación.

Entradas explícitas: `-UnsignedApk`, `-ApprovedSha256`, `-UnsignedReport`,
`-Policy` y `-OutputDirectory` nuevo bajo `.local-evidence/release/`. El plan
exige coincidencia de informe, política, herramientas y custodia existente;
no genera otra clave. La ejecución alinea una copia nueva y firma otra, conserva
parciales ante fallo y produce sólo `SIGNED_UNVERIFIED`, nunca aceptación.

Emisión de esta entrega: v1 desactivada, v2/v3 activadas y v4 desactivada;
sin rotación ni source stamp. Android mínimo 26; la verificación criptográfica
pertenece a las herramientas oficiales, no a código propio.

`verify_signed_apk.py` exige unsigned/hash e informe/hash aprobados, reinspección
unsigned, firma permanente única por intervalo API, identidad/superficie intactas,
alineación y payload exacto. No omite todos los archivos `META-INF`. El inventario
estructural v2/v3 cuenta certificados y limita formato/tamaño; no verifica por
su cuenta firmas criptográficas. Una política congelada anterior requiere tanto
`--policy` como `--policy-sha256`; no se deriva del artefacto a aprobar.

SDK 37, exclusivamente en el control API 26–27: se admite una sola aparición
exacta del aviso de referencia anti-stripping v2 al esquema 3 desconocido para
ese intervalo. Se conserva en `accepted_warnings`; no se oculta. Los controles
general y API 28–actual deben verificar v3 sin avisos, y ambos bloques deben
contener el certificado permanente. Otros avisos, duplicados, errores o una
firma v2 fallida siguen rechazados. No se cambia la APK para evitar el aviso.

```text
python tools/release/verify_signed_apk.py --unsigned <UNSIGNED_APROBADO> --unsigned-sha256 <SHA_UNSIGNED> --unsigned-report <INFORME_APROBADO> --unsigned-report-sha256 <SHA_INFORME> --signed <FIRMADO_NUEVO> --build-tools <SDK_BUILD_TOOLS_EXISTENTE> --output <JSON_NUEVO_EN_LOCAL_EVIDENCE_RELEASE>
```

Pruebas de guardas sin claves ni firma:

```powershell
powershell.exe -NoProfile -File tools/release/test_sign_release_guards.ps1
python -m unittest discover -s tools/release/tests -v
```

Estas pruebas herméticas no acreditan todavía una firma real, recuperación de
contraseña, instalación ni actualización. El primer artefacto firmado requiere
ejecución positiva del verificador y QA instalada independiente. No se detectan
todos los grabadores externos ni se protege un sistema operativo comprometido.
Referencias: [apksigner](https://developer.android.com/tools/apksigner) y
[firma v3 de Android](https://source.android.com/docs/security/features/apksigning/v3).

## Puerta de firma: checklist, no evidencia de ejecución

1. MAIN acepta etapa 19 y congela versión/artefacto. Se conserva el pin público
   Release `AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D`;
   ningún comprobante beta habilita la app ni la descarga. El nombre/código de
   entrada se conserva hasta esa congelación; esta herramienta no firma.
2. Custodiar fuera del repositorio una clave Release permanente y recuperable.
   Nunca usar debug, fixtures ni una clave descartable como firma oficial.
   Los secretos privados históricos del servidor no participan del protocolo público. No incluir
   contraseñas, alias reales o secretos en documentos, argumentos o logs.
3. Registrar el SHA-256 unsigned validado. Antes de firmar, preparar una copia
   nueva alineada; después de firmar **no modificar ni realinear** el APK.
4. Sólo en esa puerta futura, resolver las rutas de los marcadores siguientes.
   Son plantillas deliberadamente no ejecutables como están. El comando de
   firma debe pedir la contraseña interactivamente, nunca con `--ks-pass` ni
   contraseña literal. Si el almacén contiene varias entradas, resolver su
   selección conscientemente en la puerta de custodia, no asumir una clave.

```text
zipalign -P 16 -v 4 <UNSIGNED_VALIDADO.apk> <COPIA_NUEVA_ALINEADA.apk>
java -jar <SDK_EXISTENTE/lib/apksigner.jar> sign --ks <KEYSTORE_CUSTODIADO> --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out <MiGuardia-2.0.0.apk_NUEVO> <COPIA_NUEVA_ALINEADA.apk>
java -jar <SDK_EXISTENTE/lib/apksigner.jar> verify --verbose --print-certs <MiGuardia-2.0.0.apk>
zipalign -c -P 16 -v 4 <MiGuardia-2.0.0.apk>
```

5. La verificación de la firma futura debe terminar en éxito, con certificado
   esperado registrado públicamente y distinto de debug. Este verificador
   **unsigned** rechazará correctamente ese archivo firmado; no debilitar su
   política para aceptarlo. Inspeccionar otra vez identidad/permisos/ABI y
   comparar el payload con el unsigned, separando las entradas de firma que
   Android agregue. Calcular SHA-256 y tamaño nuevos del APK **firmado**.
6. Ejecutar QA de instalación y actualización real a código superior, mismo
   paquete/certificado, preservando Room, preferencias, fotos, residuos beta y
   Keystore. Para el ensayo controlado 2→3 se necesitan dos aplicaciones
   completas: el origen de código 2 conservado y la candidata de código 3,
   ambas con la misma firma. Las mini fixtures históricas del bloque 28 **no**
   son ese origen ni una actualización instalable de MiGuardia. No desinstalar
   ni borrar datos para resolver un conflicto de firmas.
7. Sólo tras cerrar confianza pública, condiciones y QA, usar los metadatos
   del **firmado** en la descarga pública. Publicación, instalación, tag,
   GitHub Release y movimiento de `main` no se acreditan con este checklist.

Ningún comando anterior crea, importa, firma o publica nada por sí solo durante
la preparación: este documento describe la puerta posterior y sus controles.

## Comparación de upgrade con datos ficticios

`upgrade_snapshot.py` recibe copias consistentes suministradas conscientemente por
el operador. No usa ADB ni abre bases del teléfono. Capturar SQLite con su WAL
confirmado, sin una copia parcial mientras la app escribe. Debe tener Room V6,
las 27 tablas/columnas/FK exactas y escenarios poblados; no alcanza crear tablas.
El comparador consulta una copia temporal de DB y WAL, regenerando SHM sólo allí,
y la elimina al terminar. No crea ni modifica archivos auxiliares en la entrada.
La carpeta privada de entrada contiene `files/`, `shared_prefs/` y `no_backup/`.
Seleccionar explícitamente fotos, preferencias, Bloqueo, avisos/Widget y residuos;
excluir cachés volátiles y documentar las exclusiones. No pasar datos reales.

La identidad pública JSON tiene exactamente `application_id`,
`certificate_sha256`, `version_code`, `artifact_sha256`. Derivarla de cada APK
completa e instalación comprobada; el script valida tipos, pero no puede acreditar
que el operador la obtuvo de un dispositivo. Nombres privados y valores se reducen
a hashes; las cuentas y esquema permanecen visibles. Keystore y recorridos UI se
comprueban aparte; no exportar claves.

```text
python tools/release/upgrade_snapshot.py capture --database <COPIA_CONSISTENTE_V6> --private-files <RAICES_SELECCIONADAS> --identity <IDENTIDAD_PUBLICA_JSON> --fictitious-data --output <ANTES_JSON_NUEVO>
python tools/release/upgrade_snapshot.py capture --database <COPIA_DESPUES> --private-files <RAICES_DESPUES> --identity <IDENTIDAD_APK_SUPERIOR_JSON> --fictitious-data --output <DESPUES_JSON_NUEVO>
python tools/release/upgrade_snapshot.py compare <ANTES_JSON> <DESPUES_JSON> --output <COMPARACION_JSON_NUEVO>
```

Una diferencia exige diagnóstico, no regenerar el antes. Igualdad lógica no
certifica autenticidad de APK, QA OEM, semántica de escenarios ni restauración.
Los tests del comparador usan esquema real con filas genéricas para comprobar
el instrumento; no constituyen una instalación poblada de MiGuardia.

## Escaneo local redactado

```text
python tools/release/security_audit.py --apk <APK_LOCAL_VALIDADA> --output <REPORTE_JSON_NUEVO>
```

Lee fuentes rastreadas/nuevas no ignoradas, blobs Git alcanzables y entradas del
APK. Cinco patrones de claves/tokens conocidos; no imprime coincidencias ni
contexto. Cero señales no prueba ausencia de secretos, no cubre todos los
formatos y no sustituye auditoría de dependencias. No transmite archivos.
