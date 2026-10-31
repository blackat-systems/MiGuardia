# Stage 5 — continuación con zoom mensual autorizado

## Decisión y entrada

Joaquín respondió «autorizo» el 2026-10-03 a la propuesta de zoom tipográfico
únicamente en calendario mensual, casillas ajustadas al espacio y áreas táctiles
de al menos 48 dp de densidad base. El resto de la aplicación conserva su zoom.
La decisión permite continuar Stage 5; no acepta su candidata ni distribución.

MAIN recibió y contrastó el cierre parcial: 12 archivos y dos APK sellados
coinciden; 85 métodos Android dirigidos verdes. Los cuatro conjuntos de muestras
calientes coinciden con sus tablas y no exceden los umbrales de investigación.
Se conserva el límite: Resumen de estrés p50 1602,3 ms no acredita experiencia
aceptable. Los 916 JVM y 26 interop pertenecen al recibo anterior identificado.

Rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`,
checkout acumulado preservado y staging vacío. Antecedente y recibos:
[handoff parcial](../HANDOFF_STAGE_5_A_MAIN_3_0.md). Entrada de esta continuación:
`.local-evidence/stage5/zoom-approved-20261003/entry/`.

Código y medidas confirman que el zoom anterior escala densidad geométrica y
fuerza ancho mínimo 378 dp con scroll horizontal. A 200 %, siete objetivos de
48 dp internos necesitan 1890 px en una ventana de 1440 px; diez combinaciones
son incompatibles aun sin encabezados. Las 32/48 fallidas son evidencia previa,
no un resultado de la política nueva.

## Alcance y recursos

Implementar superficie mensual con geometría base, texto ampliado, composición
adaptable y controles contextuales; conservar detalle, carga, selección, reglas,
Room, Copias y privacidad. Repetir matriz y consumidores afectados sobre nueva
identidad QA. Completar observación de contenido frío desde un runner separado,
sin capturar ni recorrer ventanas de otra aplicación.

El Samsung inicialmente tenía foco ajeno. MAIN sólo clasificó metadatos de foco,
sin leer contenido. Joaquín confirmó «Sí, lo dejo libre y desbloqueado» para QA
el 2026-10-03. Ensayos limitados a QA y datos ficticios; app pública excluida.

## Estado de esta continuación

Implementación y comprobaciones en curso. Los recibos anteriores no se atribuyen
al producto nuevo. Pendientes actuales y autorización viven en [STATUS](../STATUS.md).

## Implementación y hallazgos causales

- Geometría mensual base y texto ampliado; siete columnas sin ancho mínimo ni
  scroll horizontal. Fecha dominante y líneas secundarias según espacio; detalle
  y descripción accesible completos. Formulario confirmado y drawer mantienen su
  zoom previo. En selección baja se omite sólo el encabezado de marca; Franco y
  Quitar Franco quedan en Más opciones sin cambiar sus efectos ni cancelación.
- El primer build falló por acceso implícito a `maxWidth` desde otro receptor.
  Se precalculó el margen en el receptor correcto. Log adverso preservado en
  `zoom-approved-20261003/build/build/gradle.log`, bajo `.local-evidence/stage5/`.
- Primer perfil completo: 918 JVM, 26 interop, lint y seis artefactos verdes,
  cero inputs cambiados; `zoom-approved-20261003/full/result.json`. Pertenece
  al primer candidato, no se reasigna a una corrección posterior.
- La primera matriz física nueva falló exclusivamente en fuente exacta:
  14 sp registró 39,375 / 61,875 / 73,125 px, frente a multiplicadores esperados
  39,375 / 59,0625 / 78,75. La conversión SP de `Density` Android es no lineal.
  Se implementó conversión local SP↔DP lineal para el zoom interno; fuera del
  mensual no cambia. La regresión cubre cinco tamaños y ambos sentidos.
  El oráculo de la matriz se conserva, no se relaja para aprobar el producto.
- Recibo adverso: `.local-evidence/qa/20261003T214910.500553Z` (0/2 métodos).
  Sus 48 casos, geometría y capturas están en
  `zoom-approved-20261003/matrix-20261003T215107023795Z/` y sus APK en
  `zoom-approved-20261003/first-candidate-apks/`. Contención, zonas táctiles y
  ausencia de solapamientos pasaron; no equivale a una matriz verde integral.
- Ayuda agrega dos regresiones Activity con coordinador real: cierre completo
  y recreación/cierre sin reentrada ni escrituras. El runner independiente frío
  verifica doce procesos nuevos y todas las fechas/franjas sintéticas del mes,
  con marcador enlazado a siembra fresca, identidad QA y ventana equivalente.
  Su latencia es un límite superior observado después de `am start -W`, incluye
  shell/accesibilidad/polling y no certifica almacenamiento frío ni FPS.

MAIN detuvo la primera instalación por foco ajeno antes de modificar paquetes.
Joaquín volvió a confirmar «Sí, queda libre y desbloqueado»; sólo entonces se
abrió QA y se instalaron sus tres paquetes. No se leyó contenido de otra app.

## Resultados posteriores a la conversión lineal

- QA y runner compilaron en `linear-qa-build.log` (1 min 11 s). Perfil local
  fresco: **919/919**, cero fallos, errores u omitidas, cero inputs cambiados;
  `linear-local/result.json` bajo la carpeta de esta continuación.
- Matriz VIEW: **48/48 casos**, 24 retrato + 24 paisaje, sin issues de geometría,
  fuente, recorte, semántica ni solapamiento. Son dos métodos JUnit, **2/2**,
  recibo `.local-evidence/qa/20261003T215536.998720Z`. Los JSON y 48 capturas
  se extrajeron a `matrix-20261003T215725500357Z/`. MAIN inspeccionó imágenes
  de seis filas al 200 % en claro/retrato y oscuro/paisaje; no acepta estética
  ni TalkBack por inferencia. Ventanas menores a 336 dp útiles siguen siendo
  un límite físico de siete objetivos de 48 dp, no cobertura del Samsung.
- Ayuda Activity real: **2/2**, recibo
  `.local-evidence/qa/20261003T215728.411838Z`, sin cambios en snapshot/onboarding.
- Rendering dirigido: **7 pasadas, 1 fallo, 38 pendientes** porque el catálogo
  cortó después de la clase fallida; no es verde de todo el grupo. Recibo
  `.local-evidence/qa/20261003T215843.307413Z`. Falla únicamente la nueva prueba
  de selección manual en ventana baja al 200 %: zonas táctiles se solapan.
  La causa aún requiere medidas; no atribuirla a redondeo sin valores.
- MAIN interrumpió su compilación de cierre para investigar ese fallo antes de
  sellar producto. `linear-build/result.json` queda fallido por interrupción
  controlada del daemon propio, no por un nuevo error de compilación.
- Runner diagnóstico compilado en `adaptive-diagnostic-build.log` (42 s).
  Conserva 520 dp, 48 dp base, fuente exacta y solapamiento estricto; añade
  bounds/insets sin capturar otra app. Producto sin cambios en este diagnóstico.
  APK previos preservados en `linear-before-diagnostic-apks/`.
- El siguiente preflight encontró `NotificationShade`; metadatos de keyguard
  confirmaron bloqueo. Se detuvo antes de instrumentar y se solicitó desbloqueo.
  Disponibilidad, no falta de autorización general, es la puerta del diagnóstico.

Siguen pendientes selección compacta, rendering restante, carga/recreación,
comparación caliente del candidato final y contenido frío. No se trasladan los
85 métodos ni las mediciones calientes anteriores al producto corregido.

## Reanudación con Samsung disponible

Joaquín confirmó «samsung reconectado y listo para la etapa final». MAIN verificó
keyguard oculto y launcher, abrió sólo QA y ejecutó el diagnóstico compilado.
El preflight inmediato a la apertura se detuvo durante la transición de foco;
la lectura posterior confirmó QA antes de instrumentar. No se leyó otra app.

Recibo `.local-evidence/qa/20261003T233611.691909Z`: Adaptive **5/6**. La
selección baja conserva seis filas, pero cada celda mide 120 px y su área táctil
135 px; dos fechas verticalmente vecinas se solapan **4 px**, no es redondeo.
Viewport de 1463 px, grilla de 771 px y toolbar terminando en 1283 px: quedan
180 px reservados al pie para navegación de la ventana completa de 3120 px,
fuera del host corto. La corrección recorta Insets a su intersección física con
el viewport mensual, sin reducir objetivo, fuente, filas ni cambiar otras
superficies. Está en curso, no verificada todavía.

La revisión independiente del ensayo frío detectó una diferencia del helper en
Windows: `write_text` convertiría LF a CRLF y rompería el enlace SHA del marcador.
MAIN cambió sólo su extracción a bytes UTF-8; la comprobación local del enlace
raw-byte pasó. No es un defecto de producto ni una medición física. Probe,
oráculo y analizador revisados; **8/8** comprobaciones internas del analizador.
La primera ejecución fría sigue pendiente.

## Recorte de Insets y consumidores

La corrección quedó limitada al Scaffold y AppBar mensuales: intersección de
franjas físicas de ventana con `boundsInWindow`, sin cambiar densidad de wizard,
detalle o drawer. Cuatro regresiones JVM cubren ventana completa, acotada,
flotante y paisaje. Revisión independiente sin hallazgos concretos, no QA física.

- Adaptive **6/6**: `.local-evidence/qa/20261003T235001.475864Z`.
- VIEW **48/48**, dos métodos **2/2**:
  `.local-evidence/qa/20261003T235105.745401Z`.
- Perfil completo estable **923 JVM + 26 interop**, lint sin errores y 24
  warnings, seis artefactos: `insets-final-full/result.json` dentro de esta
  continuación. Cero inputs cambiados. Es el candidato de Insets, no entrega
  final de Stage 5; no se trasladará a una modificación posterior de marcadores.
- QA `61f600c2e7b869d0d6c2557c60c084ab5d3e3c16c042f4fbbb815bed8e441f00`
  y runner `5edeabfb0aac06bf0537c6300a00dc9bdd1594dcc6cc2e59a1b4c3868a934242`
  preservados en `insets-verified-apks/`.

Rendering posterior a la matriz: **7 pasadas, 6 fallidas, 27 pendientes**,
recibo `20261003T235233.551042Z`; Appearance pasó 2/2 y Common 5/11.
Para atribuir la causa se ejecutó el mismo driver contra QA anterior `4ccb…`,
recibo `20261003T235534.056971Z`, y luego contra QA nueva sin matriz previa,
`20261003T235916.851996Z`: ambos Common **7/11**, los mismos cuatro fallos.
El recorte no introduce esos cuatro fallos. Dos fallos adicionales del primer
grupo dependen del viewport/fixture no fijado; no se atribuyen al producto sin
geometría adicional.

Los cuatro casos restantes exigen señales de extras, disponibilidad y feriados
que el delta tipográfico anterior sustituyó por un resumen único a cualquier
altura. La semántica completa permanece, pero faltan señales visuales incluso
cuando cabrían. Corrección en curso: conservar coexistencia con composición
compacta y probar tema productivo y navegación natural del menú, sin rebajar
objetivos, fuente ni el significado comprobado.

Antes del siguiente recorrido el preflight detectó foco de otra app con keyguard
oculto; se detuvo sin abrir QA ni leer contenido ajeno y preguntó disponibilidad.
No hay mediciones calientes o frías nuevas de la candidata final. La entrada
Common se copió antes de su primera edición propia en esta continuación.

## Recuperación de señales — en curso

Se recuperan textos coexistentes cuando caben y una banda compartida de colores
y pictogramas con `+` para condiciones adicionales. Common usa el tema real,
retrato de su propia Activity con restauración y desplazamiento natural del menú;
el nuevo caso compacto exige dibujo contenido, semántica completa y detalle.
Dos intentos de compilación se conservaron: receiver `maxWidth` dentro de Column
y un import de `onNode` inexistente. Corregidos, todavía sin build final verde.

La revisión independiente detectó que la altura observada de fecha al 200 %
era 34,13 dp, no los 32 presupuestados por la composición, y que el check en
overlay comparte el estrato de fechas de dos dígitos. Se ajusta el padding
tipográfico sólo de esa fecha y se integra el check en la banda, sin reducir
fuente ni objetivo. No se da aprobación geométrica o humana anticipada.
La matriz de Insets previa se extrajo a
`matrix-20261004T001641195556Z/`: 48 PNG y dos JSON, no capturas de producto nuevo.
Herramientas locales: **75/75** en `tool-tests-signals.log`.

La entrega posterior elimina el overlay: check vectorial y colores comparten
un slot de 8 dp; selección sin jornada recibe su propio primer slot. Fecha y
líneas opcionales usan `includeFontPadding=false` sólo dentro de la celda.
La reserva nominal vuelve a 32 + 8 + 2 + 4 = 46 dp al 200 %. La evidencia de
invasión del check anterior es una inferencia geométrica; no se afirma una
captura física de esa superposición en una celda exacta de 48 dp.

Common ahora tiene 13 métodos. El nuevo caso día 24 verifica completado y luego
seleccionado al 200 %: fuente exacta, altura real, ausencia de overflow,
contención y no intersección fecha/check. El host de 380 dp no fuerza una celda
de exactamente 48 dp. Revisión independiente de fuentes sin nuevo defecto
concreto; no acredita ejecución ni legibilidad de pictogramas/colores compactos.
El perfil final está en ejecución; la candidata física aún no se instaló.

La matriz de 33 recorridos distingue ahora explícitamente la columna histórica
del 02/10. Su primera copia falló por un directorio inexistente; la base se
recuperó del snapshot sellado `stage5/final/files/` y se contrastó por diff:
sólo difieren el encabezado fechado y la nota nueva, sin pérdida de requisitos.
Ese origen queda identificado en el manifiesto, no como copia previa recién hecha.

## Entrega local estable — cierre parcial

Perfil completo final: **925/925 JVM**, **26/26 interoperabilidad**, cero fallos
u omitidas; build de Debug/QA/Release unsigned, ambos runners y lint, seis
artefactos. Lint: cero errores, 24 warnings. Cero inputs cambiados.
Recibo: `signals-final-full/result.json` en la carpeta de esta continuación.
Los 12 casos de política JVM pasan; Common tiene 13 métodos compilados, no
ejecutados sobre esta candidata. Se conservaron ambos builds adversos y todas
las corridas físicas anteriores, sin trasladarlas a la APK nueva.

- QA local: `2e5c6c303d02fb71a315c481ad3c351f29e1e66215c32e91e935efe7f7a774dc`.
- Runner app: `32b0b3099ec293c55962bf32d10d5c3723f3002c62314502fe7ac8969eb71292`.
- Runner base reutilizado, sin fuentes nuevas desde su compilación:
  `493cbf551f974580d2e8750a71175271f2b02fd0995c05256406605070701059`.
- APK pública reconsultada sólo por metadatos/hash: conserva
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
  No se inició ni se accedió a datos personales. Lectura en `public-after.json`;
  resultado anterior preservado en `public-after-prior-close.json`.

La QA final no fue instalada ni abierta: se mantiene la puerta de teléfono libre
y desbloqueado tras el foco ajeno detectado. Próximo ensayo: Common 13, Adaptive
6, matriz VIEW 48 con extracción/inspección, consumidores y Ayuda real, carga y
recreación; luego pares baseline/candidata de doce muestras calientes por serie
y doce procesos fríos con siembra enlazada. Sin compilación durante mediciones.
Todos los scripts guardan identidad, foco y recibos; no usar el teléfono público,
ajustes Android globales ni repetir baterías no afectadas por ritual.

La revisión humana, TalkBack, teclado/selector OEM y aceptación siguen pendientes.
No se declara fluidez integral ni Stage 5 terminado; no commit, push, tag, firma
pública, AAB ni publicación. Staging vacío; no APK/AAB/claves rastreadas.
