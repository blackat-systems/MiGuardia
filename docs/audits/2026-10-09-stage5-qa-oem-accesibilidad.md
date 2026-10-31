# Stage 5 QA OEM y accesibilidad

## Resultado y autoridad

Joaquín autorizó pruebas de TalkBack, teclado, selectores, permisos y bloqueo
exclusivamente en MiGuardia QA, con Samsung libre y sin app pública ni datos
personales. Se aprobaron diez métodos nuevos: ocho en Stage5OemBoundaryTest y
dos en Stage5NotificationOemTest. Stage 5 continúa parcial; estos resultados
no acreditan aceptación visual o todos los recorridos de la aplicación.

La puerta de entrada confirmó rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream/remoto vigentes, staging
vacío y 950 rutas preservadas sin diferencias respecto del sello anterior.
`.git` propio es un directorio y el contenedor no es repositorio. La referencia
2.x conserva sus siete diferencias preexistentes y se mantuvo de sólo lectura.
Entrada y evidencia: `.local-evidence/stage5/oem-20261009/`.

## Pruebas aprobadas

Las fuentes son
[Stage5OemBoundaryTest](../../app/src/androidTest/java/com/blackatsystems/miguardia/Stage5OemBoundaryTest.kt)
y [Stage5NotificationOemTest](../../app/src/androidTest/java/com/blackatsystems/miguardia/Stage5NotificationOemTest.kt).
Los seis primeros casos y los dos de notificaciones usan Composables productivos,
launchers Android reales y callbacks aislados, sin resetear Room ni escribir
datos laborales. Bloqueo y TalkBack usan MainActivity real.

| Frontera | Comprobación y límite |
| --- | --- |
| Teclado | IME OEM visible por Insets, eventos de entrada al campo productivo de puesto, borrador conservado y Atrás oculta IME sin guardar. No teclas táctiles, predicciones ni composición humana integral. |
| Photo Picker | Se abre el selector Android real y cancelar devuelve lista vacía. No se recorren miniaturas, nombres o imágenes personales; importación positiva pendiente. |
| SAF crear | Cancelar devuelve null y retorna a Copias. |
| SAF abrir | Cancelar devuelve null sin leer documentos existentes. |
| SAF escribir | Documento UUID nuevo de cero bytes, nombre verificado antes de escribir; bytes sintéticos escritos/leídos iguales y documento eliminado por su URI exacto. No es una copia válida ni prueba de restore. |
| Ubicación | Diálogo real denegado, alternativas/ajustes siguen disponibles, cero capturas geográficas. Permiso QA inicialmente concedido, revocado temporalmente y restaurado junto con sus flags. |
| Notificaciones | Diálogo real denegado y callback de refresco recibido; interfaz sigue abierta. No se emiten avisos ni se prueban alarmas o canales reales. Permiso/flags restaurados. |
| Sonido | Selector Samsung real y Atrás; se conserva el alias de sonido existente. El estado de permisos del host es un doble para alcanzar el selector sin habilitar avisos reales. |
| Bloqueo | Cancelación no activa ni altera configuración; Joaquín autentica activación, desbloqueo y desactivación. Bloquear ahora elimina calendario/menú de las semánticas; se recrea con configuración inicial restaurada. No PIN propio, capturas del diálogo, credencial leída ni autenticación simulada. |
| TalkBack | Servicio Samsung conectado y exploración táctil activa; foco/activación accesibles abren menú, seleccionan días y cancelan. ViewModel verifica EDIT→VIEW. No UiDevice durante esa prueba: bridge conserva servicios mediante FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. |

Joaquín confirmó haber completado los diálogos reales de autenticación. No llegó
a escuchar los anuncios de TalkBack: **audición y claridad humana pendientes**,
aunque la ruta técnica de acciones accesibles pasó. No se generaliza este método
a orden de foco, todas las pantallas o gestos humanos de lector de pantalla.

## Recibos y atribución

Recibos bajo `.local-evidence/stage5/oem-20261009/`:

| Recibo | Casos | Runner |
| --- | --- | --- |
| run-20261009T194051.791271Z.json | 6/6 fronteras originales | 7b2af8…929a |
| run-20261009T193451.178256Z.json | 1/1 bloqueo real | 7b2af8…929a |
| run-20261009T193745.235908Z.json | 1/1 TalkBack técnico | 7b2af8…929a |
| run-20261009T194608.773007Z.json | 2/2 notificaciones/sonido | Identidad completa en recibo |

Los diez métodos son distintos; repeticiones y cortes no se suman. Los ocho de
la primera clase conservan su hash actual de fuente
`31c3cf62870cd4a365aec85df816a4a854b8fe7ee149f5e1c3e5abd9422ce53a`
y runner `7b2af8831592537618938a158878421933a974657375a9eb2d442fb9cdac929a`.
No se reatribuyen al último runner instalado. Los 144 métodos dirigidos previos
conservan sus recibos y límites en la
[continuación anterior](2026-10-09-stage5-continuacion-qa.md).

Los dos métodos de notificaciones/sonido pertenecen al runner final
`eddebcfd607c3db3af3b6e919b9aea118870b718f3a3f87c4fe31009a56d931d`
y a la fuente `2444e6197172e5b5131aa0e26c8bd366fe74fda6722f2879ba96b869fe713c49`.

QA productiva permanece
`223f98d97f0a81ac67476dab8d5323aee81c0a00dd2ec102493c703eb11ebab8`.
APK pública antes/después:
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
Se consulta solamente identidad estática del paquete público: no UI,
instrumentación, instalación ni datos personales. Sólo se reemplaza runner QA.

## Adversos y seguridad

Los recibos STOPPED se conservan. El supervisor inicial leía marcadores de fase
con retraso y no reconocía el retorno de un OEM propio a una ventana nula con
dos dueños QA positivos. Se corrige el supervisor local, no producto: marcador
leído al llegar; nueva observación sólo ante cambio explícito de fase; retorno
desde el OEM anunciado sólo con dueños QA coincidentes e instrumentación activa.
No hay gracia temporal ni aprobación de dueños mixtos, ausentes o apps ajenas.
Política pura final: 13/13. Un corte de TalkBack no recibió causa confirmada y
no se convierte en éxito; la ejecución posterior independiente sí pasó.

Otros fallos de preparación preservados: compilación por month omitido y acceso
incorrecto a Activity del rule; callback SAF del doble mantenía WAITING y ahora
sale a IDLE; identificación del botón de rechazo distinguía mal paquete Google
y namespace Android; matcher de permiso esperaba Rows presentes en semántica
fusionada. El recorrido final identifica el permiso concreto y exige llegar al
controlador Android real, no Ajustes.

La primera aserción de sonido exigía cero callbacks y falló: Samsung devolvió
confirmación al usar Atrás. La regresión final usa el alias público explícito
de Android y exige conservar exactamente el sonido previo, con a lo sumo un
resultado. No prueba conservar un ringtone personal ni declara falsamente
RESULT_CANCELED; no se corrigió producto a partir de un contador de callbacks.

Un corte tras crear SAF dejó un documento vacío. `own-document-inventory.json`
lo identificó por UUID, ID de proveedor, cero bytes e instante 19:38:41 UTC,
coincidente con la corrida interrumpida. Se verificó otra vez antes de eliminar
esa fila exacta. El comando borró el archivo; falló una aserción sobre el formato
de su salida. Se reconcilió con consulta sin resultado, **sin repetir el borrado**.
`owned-document-cleanup.json` conserva la reconciliación. No se borraron archivos
personales ni carpetas. La corrida final elimina su documento en finally.

## Ajustes y delta

Permisos aproximado/notificaciones y flags USER_SET/USER_FIXED se restauran a
sus valores de entrada, no a defaults inventados. Para TalkBack se conserva la
lista previa de servicios y se agrega sólo el servicio Samsung durante el caso;
al finalizar se restauran lista, accessibility_enabled y touch_exploration_enabled.
El servicio preexistente no se elimina. Recibos verifican restauración exacta.
La configuración de bloqueo QA se restituye y se lee de nuevo; el finally de
limpieza no cuenta como autenticación ni habilita aserciones protegidas.

Delta propio: dos clases Android, opt-in explícito en tools/qa.py, catálogo
manual_only y documentación. Sin fuentes productivas, Gradle, manifiestos,
Room/esquemas, Copias, versión, applicationId, firma, commits o distribución.
El grupo supervisado no puede ejecutarse automáticamente por tools/qa.py;
su controlador local exige paquetes/fases concretos y guarda sólo resultados
y metadatos acotados, nunca dump global, galería, credenciales o posiciones.

Build AndroidTest QA aprobado tras cada corrección; los intentos fallidos
permanecen. Herramientas QA: 34/34 tests Python, además de 13/13 del supervisor.
JVM/interop, lint/build completos y rendimiento previos conservan fecha e
identidad históricas; no se vuelven a presentar como corridos por este delta.

Control final: catálogo válido (118 clases, 27 grupos), docs_check válido
(344 referencias, 103 snapshots) y git diff --check sin errores. El probe final
confirmó QA en primer plano, los cuatro hashes instalados/locales, permisos y
accesibilidad iguales a la entrada y cero documentos con el prefijo sintético.
Recibo: `.local-evidence/stage5/oem-20261009/final-device-probe.json`.
Sello separado: `.local-evidence/stage5/oem-20261009/sealed-final/manifest.json`,
con delta propio reversible y hashes de evidencia; no reemplaza sellos previos.

## Pendientes y siguiente paso

Revisión humana de calendario, zoom, temas, navegación y voz/gestos TalkBack;
importación OEM positiva con una imagen ficticia elegida conscientemente;
composición humana con el teclado y proveedor específico; permisos concedidos con ubicación
sintética, sin posición personal; copia válida/restauración completa y accesos
por Widget/notificaciones, Recientes/muerte de proceso. Mantener la matriz
integral: las fronteras nuevas no aprueban filas completas por inferencia.

MAIN debe revisar delta y recibos, presentar la QA a Joaquín y acordar el próximo
bloque cohesivo según esa revisión. QA queda abierta. No nuevas búsquedas de red,
alarmas reales, firma, AAB, publicación ni aceptación global autorizadas.
