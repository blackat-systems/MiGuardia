# QA final y rendimiento de Stage 5

Fecha de cliente: 2026-10-04. Estado actual en [STATUS](../STATUS.md).
Este registro continúa la [entrega local del 03/10](2026-10-03-stage5-zoom-mensual-autorizado.md)
y conserva cada resultado ligado a su candidata. No acredita aceptación humana,
firma pública ni distribución.

## Entrada y autorización

Joaquín confirmó «conectado y libre». Samsung SM-S938B, API 36, serial explícito
[serial QA en binding local privado]. Keyguard oculto; la pantalla inicial era la configuración USB, coherente
con la conexión recién autorizada. Se abrió solamente QA antes de instalar los
tres paquetes de prueba. No se leyó contenido de otras apps ni datos personales.

Repositorio independiente MiGuardia-3.0, rama codex/miguardia-3.0, HEAD
932eaeb60c6b6e68975534788e3e6501c967b1ae. Staging vacío, 162 entradas modificadas
recibidas preservadas. Los insumos de producción/pruebas del perfil local estable
no tenían diferencias antes del ensayo; no se repitieron los 925 JVM ni 26 interop.
Entrada documental y catálogo copiados en `.local-evidence/stage5/device-final-20261004/entry/`.

Comprobación final de la referencia 2.x: MiGuardia-2.0 conserva siete archivos
modificados, todos con fecha de escritura del 13/09/2026, anterior a esta sesión.
No se afirma que ese checkout esté limpio ni se atribuye su autoría; su origen
no se investigó ni integró. Permaneció de sólo lectura, sin limpieza ni cambios.

APK QA inicial: `2e5c6c303d02fb71a315c481ad3c351f29e1e66215c32e91e935efe7f7a774dc`.
Runner app: `32b0b3099ec293c55962bf32d10d5c3723f3002c62314502fe7ac8969eb71292`.
Runner base: `493cbf551f974580d2e8750a71175271f2b02fd0995c05256406605070701059`.
APK pública antes del ensayo: `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
Su hash se consultó sin iniciar ni instrumentar ese paquete.

## Hallazgo de altura real

Common pasó **12/13**, con identidad de APK/runner verificada y sin omitidas:
`.local-evidence/qa/20261004T175532.219779Z`.
El nuevo caso del día 24 al 200 % falló en la suposición de altura de fecha de
32 dp base: el rectángulo observado tiene 96 px, equivalentes a 34,13 dp.
Fuente exacta y ausencia de overflow ya habían pasado. Las comprobaciones
posteriores de contención/check y la transición EDIT de ese método no se ejecutaron.
No se afirma un recorte o solapamiento observado; se demostró una reserva nominal
distinta de la altura natural. Los doce casos verdes conservan su alcance y APK.

Inspección del AAR local Compose 1.12: la política predeterminada ya usa ambos
trims y modo Fixed; para una sola línea esa combinación mantiene FontMetrics
natural. Quitar fontPadding o repetir Trim.Both no demuestra una línea de 32 dp.
La corrección implementada conserva fuente y altura natural, mide la tipografía real
con un TextMeasurer compartido y estilos idénticos a Text, y reduce sólo padding
vertical de 2 a 1 dp. La regresión exige fecha natural, banda y separación dentro
de 48 dp, no overflow ni intersección y transición completado/seleccionado.
No se fuerza LineHeightStyle Tight ni se recortan glifos. Cuatro archivos congelados;
Adaptive no cambió en esta corrección. El perfil completo fresco pasó **925 JVM
(395 dominio, 19 base, 511 app) y 26 interop**, sin fallos, errores, omitidas ni
insumos cambiados: `.local-evidence/stage5/device-final-20261004/full-final/result.json`.
Lint: cero errores, 24 warnings. QA, Debug, Release unsigned y runner app generados;
runner base reutilizado con sus fuentes y hash intactos, no presentado como generado.

QA corregida instalada: `463b5888ef0796ade95e2904fc736bb05debc73d0ffc6e7c9b455792f557c584`.
Runner app: `5515dbbdd3e51287630ed35a3caa2986929725accad0be56a1fd6d345c9c1852`.
Common cerró **13/13**, sin fallos/omitidas/pendientes:
`.local-evidence/qa/20261004T181823.294123Z`. El subcaso causal llegó a contención,
fuente exacta, no overflow, no intersección y VIEW→EDIT. El 12/13 inicial se conserva.

Adaptive **6/6**: `20261004T181937.867108Z`. VIEW: **2 métodos, 48 combinaciones**,
`20261004T181950.760023Z`, sin issues/overflow/targets solapados en los 24 casos
por orientación. Capturas y JSON extraídos en
`.local-evidence/stage5/zoom-approved-20261003/matrix-20261004T182235250607Z/`.
Comparación directa de agosto/seis filas al 200 %: antes faltaban números;
ahora aparecen todas las fechas, sin recorte observado. Pictogramas de 8 dp:
contención comprobada, legibilidad humana no aceptada automáticamente.

## Consumidor con oráculo obsoleto

El recibo `20261004T182237.378579Z` conserva **7 verdes, 1 fallo y 21 pendientes**.
Appearance pasó 2/2; CalendarCompose 5/6; Help y Exceptions no se ejecutaron.
`v2DayDetailKeepsNotesAndExactEditEntry` esperaba `v2-edit-day-action` después
de pulsar Editar día. El contrato vigente abre carga unificada directamente;
el menú secundario corresponde a Otras opciones del día. Código inspeccionado:
DayDetailSheet y MiGuardiaApp, callback dismiss→start→selección→confirmDates.
No se restaura el menú anterior para aprobar el test. La corrección del oráculo
debe validar orden, fecha exacta, ausencia de notas/edición vieja y conservar
el recorrido secundario. Driver, catálogo y fuente fallida preservados en la
entrada del 04/10 antes de modificarla; no se declara todavía repetición verde.

Corrección compilada: primario comprueba root READY y la secuencia exacta,
fecha y conjunto único; el fixture elimina detailDate al cerrar la hoja.
Un séptimo método conserva la edición secundaria, Retén y Carpeta accesibles.
Sin cambios de producto ni esperas añadidas. Build posterior verde y estable:
`.local-evidence/stage5/device-final-20261004/driver-final-build/result.json`.
La única diferencia de insumos respecto del perfil completo es CalendarComposeTest.
QA/Debug/Release, base runner y lint conservan hashes; no se repiten JVM por un
oráculo Android. Runner app nuevo:
`5194bf471502eb84681243cb7ed40799cb7dab80714c837bf8072d8510abf96b`.
Los recibos Common/Adaptive/VIEW anteriores identifican runner 5515, no el 5194;
se conservan como evidencia del mismo APK y clases intactas, sin reasignarlos.

Consumidores cerrados con runner 5194: **30/30** (`20261004T183435.627506Z`),
Appearance 2, Calendar 7, Exceptions 9 y Help Compose 12. Ayuda Activity **2/2**
(`20261004T183522.319040Z`); carga real/borrador **8/8** (`20261004T183532.670542Z`).
No se convierten callbacks Compose en prueba de persistencia ni picker OEM.

## Recreación: diagnóstico y cierre causal

Ready pasó **6/7**, `20261004T183640.963696Z`; el aislado intacto repitió **0/1**,
`20261004T184203.956637Z`. Al recrear y volver del detalle, identidad, fecha,
mes y celda seleccionada pasan; luego no está visible Ocultar jornadas de hoy.
No se declara pérdida de datos, estado, defecto productivo ni intermitencia.
El texto se genera sólo si la tarjeta está expandida; quedan por distinguir
recorte/altura, viewport y cierre de hoja. Se prepara diagnóstico de cuatro
momentos con bounds y recorte de Compose QA, sin ajustes globales ni aserciones
relajadas. Fuente y runner fallidos preservados antes de intervenir. Padres y
mediciones posteriores no se ejecutaron tras el guard del fallo.

El diagnóstico también falló **0/1**, `20261004T185438.748815Z`, con el mismo
APK y runner diagnóstico `008491da0d79b51edfb0672dde2cdebf93d6df379daebe23e1f19114c0ff9fcd`.
Las aserciones finales se evaluaron antes de capturar D. A/B muestran Ocultar,
lista y tarjeta de 975 px; C/D muestran Ver, sin lista y tarjeta de 728 px.
Orientación retrato, ventana 1440×3120 y densidad física permanecen iguales.
No es un recorte observado: la expansión se perdió al recrear. Extracción
acotada a JSON y recortes del nodo QA en
`.local-evidence/stage5/device-final-20261004/ready-diag-20261004T185505546981Z/`.
Se prepara una regresión controlada de contenido→carga sin resultado→contenido
y restauración, antes de cambiar producto. La causa de composición aún debe
demostrarse con ese ensayo; no se cambia el oráculo para aceptar una lista cerrada.

Regresión controlada anterior al arreglo: `20261004T185915.213341Z`,
**0 verdes, 1 fallo y 1 pendiente**. `sameDayExpansionSurvivesContentLoadingWithoutResultAndContent`
falla al recuperar el mismo resultado después de LOADING/result=null; no interviene
rotación, altura, base ni navegación. El segundo método no se ejecutó por la parada
ante fallo. El estado guardable residía dentro de TodayCardContent, compuesto sólo
si había resultado, por lo que la brecha de carga lo descartaba. Se prepara su
elevación al propietario estable NextEventCard, sin cambiar reglas ni lista.
El runner adverso y fuentes están preservados en entrada; la prueba posterior
debe demostrar misma fecha restaurada y fecha civil diferente no expandida.

Corrección congelada: expandedDate queda en NextEventCard, que existe incluso
sin resultado; TodayCardContent recibe estado y acción. Cambia sólo esa fuente
de producto y las dos regresiones Compose. Ready se restauró byte por byte a
su entrada sin observadores ni esperas nuevas. Perfil completo fresco en
`.local-evidence/stage5/device-final-20261004/expansion-final/`; aún no se atribuye
una repetición física verde al arreglo.

Perfil final cerrado: **925 JVM + 26 interop**, todos ejecutados y sin errores,
fallos, omitidas o inputs cambiados; lint sin errores y 24 warnings. QA final
`223f98d97f0a81ac67476dab8d5323aee81c0a00dd2ec102493c703eb11ebab8`, runner app
`4113d4b0747e22f94da8c5f8b0d500ed1c28e0af9d5799c12a7224b61085a45b`.
Runner base 493cb…1059 reutilizado, no generado nuevamente. Instaladas QA/runner
finales, el recibo `20261004T191023.864714Z` pasó **50/50**: NextEvent 24,
Common 13, Adaptive 6 y Calendar 7. Incluye ambas regresiones controladas verdes
con las mismas aserciones. Ready limpio cerró **7/7** (`20261004T191126.413367Z`),
con fuente byteidéntica a la entrada y sin espera/observador nuevos: la pérdida
de expansión del caso Activity original quedó resuelta. Carga real **8/8**
(`20261004T191151.766170Z`) y Ayuda real **2/2** (`20261004T191255.284184Z`).
No se transfieren los recibos físicos 463b al producto 223f.

Padres reales **2/2** (`20261004T191316.469739Z`): retorno/recreación y snapshot,
no toda edición laboral. VIEW final **2 métodos / 48 casos**
(`20261004T191328.506410Z`), cero issues en ambos JSON de 24 casos.
Capturas finales: `matrix-20261004T191451133709Z/` dentro de la continuación
03/10. MAIN inspeccionó agosto/seis filas al 200 %, retrato claro y paisaje
oscuro: todas las fechas observadas, checks separados de la fecha; la
legibilidad humana de pictogramas pequeños continúa pendiente.

## Mediciones frescas y parada por foco

El primer lote representativo de baseline terminó incompleto **11/12**:
`20261004T191522.195463Z`. El guard informó pérdida de foco QA y paró sin leer
ni capturar otra app. No se conoce su causa; no se atribuye al usuario ni al
producto. Las 11 muestras se conservan, pero no integran la comparación.
Estrés/frío/candidata no se ejecutaron tras esa parada. Joaquín reconfirmó
teléfono libre/desbloqueado. Un lote completo nuevo, misma semilla a06159…b8b,
pasó **12/12** en `20261004T191753.472067Z`, con extracción fresca verificada.
No se mezclan ambos lotes ni se excluyen outliers de la serie válida.

Inspección del guard y muestras incompletas: las nueve operaciones tienen 11
muestras y hay 11 ventanas. El fallo ocurrió antes del cronómetro/launch de la
iteración 12, no dentro de una operación medida. No demuestra lentitud ni un
crash; el launcher observado después no permite atribuir la causa. Baseline
estrés completó **12/12** (`20261004T191830.779898Z`, semilla e99b2…4240) y
la nueva preparación fría pasó **1/1** (`20261004T191955.276883Z`, 240 jornadas).

Los pares completos usan el mismo runner 4113, semillas, ventana 1440×3120,
tema claro/zoom 100 y doce muestras por serie. Baseline/candidata representativa:
`191753`/`192138`; estrés: `191830`/`192215`. Frío ligado a semillas nuevas:
`192006`/`192425` (preparaciones `191955`/`192349`). Todos bajo
`.local-evidence/qa/20261004T…Z/`, con identidades y extracciones frescas.
El análisis `comparison-final-20261004.json` en la continuación 03/10 es VALID,
sin errores ni muestras removidas: representativo 0/9 operaciones excedidas,
frío dentro de umbrales, **estrés 4/9 con investigación requerida**.

| Operación de estrés | p50 base→candidata (ms) | p95 base→candidata (ms) | Señal excedida |
|---|---|---|---|
| Cambiar mes | 630,03→709,78 | 763,14→1045,61 | p95 +282,47; límite 190,78 |
| Entrar a carga | 281,60→287,80 | 338,17→513,41 | p95 +175,25; límite 150 |
| Preview | 396,08→431,04 | 430,54→663,81 | p95 +233,26; límite 150 |
| Guardar | 591,30→762,32 | 788,22→1038,36 | p50 +171,02; límite 118,26; p95 +250,14; límite 197,05 |

Los cuatro máximos de la candidata coinciden en iteración 5, pero Guardar también
excede la mediana; no se borra ese dato como outlier ni se atribuye a térmica/GC
sin medición. Se realiza un par diagnóstico de estrés en orden invertido,
candidata→baseline, con lotes completos nuevos y sin cambiar código/umbrales.
El primer par permanece válido y señalado aunque el segundo fuera verde.

Frío de proceso con contenido listo: p50 1704,38→1703,31 ms; p95
1946,77→2092,19 ms, dentro de los umbrales. TotalTime Android se conserva como
métrica separada. La observación incluye shell/accessibility/polling de 50 ms;
no demuestra almacenamiento frío ni FPS.

Contrabalanceo completo: candidata `192734.171324Z` antes de baseline
`192905.609582Z`, doce iteraciones cada una. El informe
`comparison-stress-counterbalanced-20261004.json` es VALID, **0/9 señales**,
mismos APK/driver/semillas/ventana y sin muestras removidas. Guardar por versión:
p50 988,99→672,36 ms; p95 1083,42→1037,83 ms. No se sustituye el primer par
ni se declara mejoría causal. Resultado prudente: variación entre sesiones no
atribuida, cuatro señales iniciales preservadas/no reproducidas con orden inverso.

Inspección acotada: TextMeasurer compartido/recordado sólo para el mes visible
(CalendarScreen), y grilla ausente durante formulario confirmado. CAS/validación
integral ya existían en la entrada Stage 3.2; las imágenes del delta Stage 4 no
se ejercitan en este fixture. La medición de guardado incluye acción/sincronización,
no SQL aislado. El estado escalar de NextEventCard no agrega consultas. No hay
cuello de botella causal probado que justifique cambiar código/atomicidad.
Revisores independientes recomputaron percentiles y asociaciones; no se repite
por rutina ni se afirma térmica, GC, FPS o fluidez integral.

## Instalación y límites del cierre

QA final y ambos runners coinciden local/instalado en
`.local-evidence/stage5/device-final-20261004/qa-installed-final.json`; sólo se
abrió MainActivity QA. La preparación final deja datos ficticios representativos
para revisión, no el dataset de estrés ni la APK baseline. Verificación pública
before/after y snapshot propio están en esa carpeta; el cierre se sella después
de verificar documentos/diff. No commit, push, tag, firma pública ni distribución.

El cierre integral sigue PARCIAL: revisión visual/legibilidad, TalkBack,
teclado/selector OEM y doce recorridos Android completos aún pendientes en la
matriz reconciliada. Las señales de estrés permanecen explícitas sin una causa
de producto demostrada. Próximo MAIN: revisar esta entrega y la experiencia
humana; instrumentar un costo app-only concreto sólo si hay un riesgo reproducible,
sin repetir baterías verdes ni adoptar una optimización especulativa. No se acepta
el producto global ni se mueve a distribución por estos resultados dirigidos.

Validación documental final: docs_check verde, 342 referencias, 41 ADR y 103
snapshots; guía 39963/40000 caracteres. Se mantuvo EN_CURSO y el único contrato
AUTORIZADO del índice porque el cierre integral no terminó; no se amplió el
validador para introducir un estado nuevo. Catálogo válido: 116 clases/26 grupos;
git diff --check sin errores, sólo avisos de normalización CRLF heredados.
