# Stage 5 — imagen OEM positiva del 10/10

## Resultado y alcance

**VERIFICADO:** el recorrido pendiente pasó 1/1, sin fallos, omitidas o pendientes.
Samsung SM-S938B/API 36, serial [serial QA en binding local privado]; sólo MiGuardia QA y datos ficticios.
No se reinstaló ni modificó código, pruebas, catálogo o configuración de producto.
Stage 5 permanece parcial: esto no acepta estética ni autoriza distribución.

La autorización vigente permite QA sintética, imagen ficticia y Samsung disponible;
excluye app pública/datos personales. Se reutilizó el arnés supervisado del 09/10
sin ampliar esperas ni guards de foco. La prueba catalogada es
`Stage5PositiveMediaAndSpeechTest.realPhotoPickerImportsSyntheticImageViewsRecreatesAndDeletesOnlyItsOwnData`,
en `app/src/androidTest/java/com/blackatsystems/miguardia/Stage5PositiveMediaAndSpeechTest.kt`.

## Puerta de entrada y custodia

Repositorio independiente `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream homónimo en origin.
Contenedor sin Git, staging vacío, 178 entradas de estado acumuladas al recibir.
Los 955 paths coinciden con entrada+sello del 09/10; ninguna diferencia sin dueño.
Incluyen una eliminación rastreada preexistente, no un archivo nuevo perdido.
MiGuardia-2.0 conserva sus siete cambios ya conocidos y remoto archivado; no se tocó.

Evidencia nueva: `.local-evidence/stage5/oem-positive-20261010/`.
`device-before-20261010T173741.146680Z.json` antecede al ensayo.
`entry/manifest.json` captura documentos/custodia después del ensayo y antes de
escribir; compara con el sello previo. El primer intento de snapshot rechazó el
conteo porque omitía la eliminación conocida; se incluyó como hash nulo y se
exigió igualdad exacta con el sello, sin admitir drift. Sellos previos intactos.

## Recorrido comprobado

Recibo `run-20261010T173741.146680Z.json`: PASSED, 1 pasada, 0 fallidas,
0 omitidas, 0 pendientes. El mes QA 2088-01 debía estar vacío: no hubo reset.

- PNG verde 640×400, 12.836 bytes, UUID `76854f90-6cf8-4420-bc89-139e000fb250`;
  MediaStore ID 1000193518, álbum `000 MiGuardia QA 76854f90`.
- Selector OEM real y callback protegido: sólo admite la URI de esa fuente antes
  de leer metadatos/bytes. UI, ViewModel, repositorio y archivos privados reales;
  no se certifica un callback sin protección ni acceso a otros medios.
- Importación privada con SHA exacto, apertura del visor y recreación conservando
  UUID/imagen/archivo. Volver al primer toque deja `photo_back_state=LIST`.
- Eliminar y confirmar desde la interfaz: registro y archivo privado ausentes
  antes del finally. Marcador `IMPORTED_PRIVATE_VIEWED_RECREATED_DELETED`.
- Finally retiró sólo la fuente ficticia propia y verificó el mes vacío. Esa
  limpieza no sustituye el éxito de eliminación consciente, ya comprobado.

SHA-256 PNG:
`798376801c57d42ddcacd412210a7288ed3a471080087633d19660bf591207a9`.
Hash de clase catalogada:
`6db36238df76c8253eafbaa29c2ab60a11fae95cb1dfd44d84263def91b1fb87`.

## Identidades y cierre

`final-device-probe.json`: QA abierta, hashes locales/instalados coincidentes,
permisos/flags de ubicación/notificaciones y accesibilidad iguales a la entrada;
cero imágenes/documentos ficticios restantes por prefijos propios. Sin enumerar
galería personal ni leer credenciales. No audio, posición o servicios remotos usados.

- QA: `c079941220236f08045cd00f13d0d811af96be5ffb2cff551cd62872794dc227`.
- Runner app: `04377391181c59b6c57fb2a696945d44265cd1e8457a1ee6f335027156b80f55`.
- Runner base: `493cbf551f974580d2e8750a71175271f2b02fd0995c05256406605070701059`;
  identidad verificada, no ejecutado hoy.
- Público antes/después:
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
  No abierto, instrumentado, reemplazado ni datos privados consultados.

Delta propio: STATUS, handoff Stage 5, apéndice de matriz y esta auditoría.
Validación documental/catálogo/diff y sello propio en el directorio de evidencia.
Sin Gradle/JVM rutinarios por no cambiar código; 511 JVM y 32 métodos físicos del
09/10 conservan sus recibos/runner, igual que evidencia global/interop/rendimiento.
Sin staging, commit, push, firma, AAB o publicación.

## Próxima puerta

Revisión humana de estética, legibilidad, densidad/calendario/zoom y accesibilidad
integral. TalkBack de tres controles fue confirmado en la QA anterior, no hoy.
Los subcasos integrales de la matriz continúan parciales: esta imagen no cierra
Recientes, muerte de proceso, todos los accesos externos ni toda la aplicación.
La duda del overlay mensual en ventana baja conserva su límite del 09/10.
MAIN debe elegir el siguiente riesgo concreto; no repetir baterías verdes por rutina.
