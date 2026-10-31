# Stage 4 — QA física de MAIN y devolución

## Resultado

2026-10-02. Joaquín autorizó instalación y runners exclusivamente QA, datos
ficticios y teléfono disponible mediante «acepto». Se instaló el candidato en
Samsung SM-S938B/API 36. **No aceptado: la QA física encontró fallos.**
No se modificó código productivo para ocultarlos.

La ejecución principal del candidato reúne 94 métodos de 13 clases: 89 pasaron,
5 fallaron, sin omitidos. Dos de esos métodos recorren 48 combinaciones de
calendario: 16 pasan geometría y 32 fallan. No sumar esas 48 combinaciones a los
94 métodos ni contar repeticiones diagnósticas como cobertura nueva.

La recepción MAIN anterior comprobó hashes de los 46 archivos recibidos y repitió
81/81 regresiones JVM (23 Exceptions y 58 carga). Los 916 JVM/26 interop y build
de Stage 4 conservan su recibo original; no se reejecutaron completos aquí.

## Identidad y cambios propios

- Rama y HEAD preservados: `codex/miguardia-3.0`,
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`, más delta recibido sin staging.
- QA instalada: `587ab6f6e4c1478dac2ebee37fc5d62b755057ed5202adda600ec718b556ecec`.
- Runner de base: `9d4be8ff629df0bef020936c6f1064f1bb680eb9a5b1766e2a9654c026bbcfce`.
- Runner app original recibido: `1dd8d07dcb88269e02bff08fd651ad01eadf0fbe0f17cc76270a600e0352f26e`.
- Runner con fixture corregida, usado en las 13 clases principales:
  `3d61924384669c0909db81464b87dd204a9a5c41c99a6c7bc5aef731e45fc36b`.
- Runner diagnóstico final instalado:
  `de1455fa171be5d698b6d4a65d4b527bed85a058bbfa8aa0009ec964451ac046`.
  Sólo se repitieron con éste los dos métodos diagnósticos indicados abajo.
- App pública antes/después:
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
  Se comprobó identidad del APK; no se abrió ni se inspeccionaron sus datos.

Cambios propios ejecutables limitados a dos pruebas Android y su catálogo:
`Stage4FlowsActivityTest.kt` cambia abreviatura ficticia QA a QAS (la regla real
exige 3–5 caracteres) y distingue ausencia de ventana de paquete diferente en
el diagnóstico de foco. `HelpComposeTest.kt` añade el paso al fallo de ancla,
conservando la misma aserción. Dos ensamblados de runner terminaron correctamente.
No se relajaron reglas del producto, umbrales, aserciones ni esperas.

Herramientas/evidencia locales: `.local-evidence/stage4-main/`. El wrapper de
baseline reutiliza validación de identidad/parser del runner oficial y declara
expresamente el APK antiguo preservado; nunca reemplaza el APK local candidato
para engañar esa validación. La extracción sólo lee archivos sintéticos bajo
`files/stage4` en QA. No se usaron datos personales, permisos nuevos, logcat global,
capturas de otras apps ni ajustes globales de pantalla. Sin commit/push/publicación.

## Ejecuciones principales

Recibos bajo `.local-evidence/qa/`, cada uno con su identidad y catálogo:

| Clase / alcance | Pasaron / fallaron | Recibo |
|---|---|---|
| Stage4NoteRestoreConcurrencyTest | 2 / 0 | `20261002T215809.422337Z` |
| Stage4FlowsActivityTest | 3 / 2 | `20261002T215825.204736Z` |
| Stage4CalendarMatrixTest | 0 / 2 | `20261002T215908.592293Z` |
| CalendarClearInstrumentedTest | 11 / 0 | `20261002T220144.165747Z` |
| SchedulePhotoFileStoreInstrumentedTest | 9 / 0 | `20261002T220152.606206Z` |
| ManualLoadNotesInstrumentedTest | 5 / 0 | `20261002T220155.976775Z` |
| AppearanceComposeTest | 2 / 0 | `20261002T220158.268802Z` |
| ExceptionsComposeTest | 9 / 0 | `20261002T220202.488428Z` |
| HelpComposeTest | 9 / 1 | `20261002T220210.724094Z` |
| SummaryComposeTest | 12 / 0 | `20261002T220218.870063Z` |
| WeatherComposeTest | 12 / 0 | `20261002T220226.408265Z` |
| NotificationComposeTest | 8 / 0 | `20261002T220246.044368Z` |
| WidgetComposeTest | 7 / 0 | `20261002T220252.872474Z` |

Los tres verdes de Flows son regreso a padre con recreación/snapshot sin escritura,
entrada directa sin padre inventado y preparación del dataset de arranque. Los
dos fallos son medición representativa y estrés; no son medidas válidas de fluidez.
La captura de geometría es de nodos Compose QA, no de la pantalla de otras apps.

Las pruebas de Clima/Avisos/Widget prueban UI y dobles; no acreditan proveedor
remoto, alarmas entregadas o launcher OEM. R03 tiene ahora ejecución de Room y
su ensayo del límite efectivo, conjuntos grandes, CAS y rollback; R04 ejecutó
barreras con Room/archivos reales aislados, no una copia personal ni todo el
journal de restauración ante caída física. No generalizar estos verdes a los
33 recorridos completos de la matriz.

## Hallazgos que impiden aceptar

### P1 Calendario recortado con zoom interno alto

Reproducción: matriz Stage4CalendarMatrixTest, 28/29/30/31 días, claro/oscuro,
retrato/paisaje y zoom interno 100/150/200. No se cambió ningún ajuste de Android.

| Orientación | Zoom 100 | Zoom 150 | Zoom 200 |
|---|---|---|---|
| Retrato | 8/8 geometría | 0/8, recortes/áreas táctiles | 0/8, recortes/áreas táctiles |
| Paisaje | 8/8 geometría | 0/8, recortes/áreas táctiles | 0/8, recortes/áreas táctiles |

En `2021-02-dark-150` retrato, la captura muestra el domingo fuera del borde
derecho y parte del sábado recortado. En `2026-08-light-200` paisaje la grilla
se reduce a franjas sin números legibles. Geometría de este último caso: raíz
3120×1440 px; grilla de 574 px de alto para seis filas, cuando el mínimo táctil
de 48 dp internos necesita 1620 px antes de espacios. La aceptación requiere
resolver composición/zoom, no sólo afirmar que las fechas existen en semántica.

Evidencia: `.local-evidence/stage4-main/candidate-first/calendar-matrix-portrait/`
y `calendar-matrix-landscape/`: 48 PNG, dos JSON y manifiesto de extracción.
MAIN inspeccionó ambas capturas citadas. El candidato no modificó la grilla;
este riesgo pendiente de Stage 4 quedó reproducido, no es prueba de que lo
introdujera Stage 4. No recuperar semana forzada, scroll principal ni barra
permanente, ni aprobar una reducción táctil silenciosa para obtener verde.

### P2 La guía no muestra la referencia del último paso

`HelpComposeTest.contextualTourVisitsAllRealSurfacesWithoutCallingMutationCallbacks`
falla en `HELP`: `help-tour-anchor` no está mostrado. La repetición diagnóstica
confirma ese paso sin modificar el oráculo. Fuente: `MiGuardiaApp.kt`, transición
de HelpTourStep.HELP abre el menú; el elemento Ayuda está después de todos los
grupos dentro de un scroll local. Esa transición no lleva el elemento al área
visible. El overlay descarta anclas fuera del viewport (`HelpScreens.kt`).

Corregir el posicionamiento/visibilidad del control real sin ejecutar su acción
ni sustituir la prueba por aceptar un ancla ausente. Probar todos los pasos,
regreso, recreación y zoom. No confundir este caso con una acción legítimamente
no disponible que sí deba explicar su ausencia.

### Rendimiento sin conclusión por ensayo interrumpido

El primer baseline falló durante seed por abreviatura ficticia de dos letras:
recibo `20261002T215502.246370Z`. Se corrigió únicamente QA→QAS en la prueba.
Baseline repetido: `20261002T215658.156239Z`; representativo incompleto, 0/12
iteraciones, estrés no ejecutado. El candidato también quedó incompleto.
El hash inicial representativo coincide en ambos:
`a06159feee38b88842194a67714eb28f1fcf0e34bd973ef3b5dde5c931101b8b`.
Esa coincidencia no convierte muestras incompletas en una comparación válida.

Diagnóstico final en `.local-evidence/stage4-main/diagnostic-220701/`:
el control reporta **different package**, no ausencia de ventana accesible,
al esperar `manual-institutions` durante entrada a carga. No se leyó el contenido
ni se identificó la función de ese paquete: no afirmar que Joaquín usó el teléfono,
que apareció un teclado o que la app se volvió lenta. Al confirmar la categoría
se pausaron interacciones. El runner final sólo añade precisión al mensaje;
no permite paquetes ajenos, no elimina el guard y no aumenta esperas.

Primero determinar la transición con metadatos mínimos y teléfono libre, sin
capturas ajenas; después repetir baseline/candidato completos con el mismo driver.
No hay p50/p95 comparables, arranque frío medido ni afirmación de FPS aceptable.

## Próxima entrega de Stage 4

Mantener la unidad abierta. Resolver calendario con zoom alto y guía de Ayuda;
cerrar el diagnóstico de foco del ensayo antes de medir. Repetir únicamente
regresiones/consumidores afectados y la matriz final sobre el APK resultante.
No volver a inventariar ni repetir por rutina todo lo ya verde sin cambios.

Si conservar mes completo, legibilidad y objetivos táctiles en una ventana
concreta requiere redefinir la política de zoom, presentar esa decisión a Joaquín
antes de alterar el contrato. Falta revisión humana, TalkBack, teclado, selector
OEM y comparación visual de superficies finales. No declarar aceptación global.

La QA y runners quedan instalados. El proceso QA quedó detenido por el runner;
no se afirma haberla dejado abierta después de la pausa de foco. Se puede abrir
manualmente MiGuardia QA, sabiendo que es un candidato con fallos y datos ficticios.
