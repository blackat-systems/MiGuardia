# Stage 5 — handoff a MAIN

## Avisos y restauración real — continuación del 10/10

**Resultado:** auditoría independiente del orden/índices aprobada, sin modificar
ese delta ni sus sellos. Stage 5 retomado: se reprodujo que el aviso interceptaba
flechas/fechas del Calendario y Cerrar en Clima. Joaquín eligió aviso breve en el
encabezado y detalle al tocar. Implementado sin quitar altura al mes; Clima reserva
espacio. En selección de ventana baja el mensaje espera el regreso del encabezado.

**Verificación:** 511/511 JVM de app, lint y ensamblado QA/runners; 13 métodos
físicos distintos completados en Samsung: composición 8, confirmaciones 4 y
restauración de Ayuda 1. El primer lote se cortó tras dos métodos: los seis
restantes se completaron por separado; no es una clase completa en una sola
corrida. La matriz nueva recorre 12 layouts de seis filas, 360×600 dp base,
todos sus días tocados y targets de al menos 48 dp; añade selección a 550 dp.
Ayuda conserva paso/tarea tras muerte real en segundo plano, no reentra después
de cerrarse y vuelve al Calendario. Room (29 tablas de negocio más metadatos)
y onboarding sin cambios.

**Identidad y custodia:** QA `6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`;
runner app final `6961de177c34b429060d28a34e962e485b4dce81123295b328d038c56f912324`;
runner base `93af6d35eb158865f4efad65948b6df0deca0e2016a049020dd646d249905a01`.
Los primeros once métodos de app conservan runner 7d52…8e2e5; sólo el caso de
550 dp usa el runner final. Público be3bca…2f94 intacto; permisos/accesibilidad
conservados. Sin datos personales, reset, red nueva, alarmas, firma ni Git externo.

**Delta propio:** cuatro fuentes productivas de avisos/composición, dos clases
Compose, una clase nueva de runner independiente, catálogo/opt-in y documentación.
Entrada, patch propio, hashes, recibos adversos y doce imágenes de fixture en
`.local-evidence/stage5/continuacion-20261010/`. No son capturas de otras apps.
La [auditoría nueva](audits/2026-10-10-stage5-avisos-y-restauracion.md) separa
defectos de producto de errores corregidos del arnés y detalla la atribución.

**Próximo MAIN:** revisar ese delta/sello y completar únicamente subcasos aún
abiertos de la matriz. Revisión humana de composición, zoom y accesibilidad
integral pendiente; no reatribuir voz/rendimiento de APK anteriores. **Stage 5
sigue parcial, sin aceptación global ni autorización de distribución.**

## Imagen OEM completa — continuación del 10/10

**Resultado:** el pendiente de imagen pasó **1/1** sobre QA c079…7227 y runner
043773…0f55, sin reinstalar ni cambiar producto/pruebas. Selector real → importación
privada → visor → recreación → Volver al primer toque → eliminación por interfaz.
El éxito por UI se comprobó antes del finally; la fuente ficticia también se retiró.
Recibo: `.local-evidence/stage5/oem-positive-20261010/run-20261010T173741.146680Z.json`.
Auditoría vigente: [imagen OEM](audits/2026-10-10-stage5-imagen-oem.md).

**Custodia y límites:** permisos/flags/accesibilidad restaurados, cero fuentes
ficticias restantes; hashes locales/instalados coincidentes, público be3bca…2f94
intacto. QA abierta al cierre. El test guarda la URI ficticia exacta mediante
callback protegido y usa UI/ViewModel/repositorio/archivos productivos; no acredita
toda la galería ni aceptación visual. Sin credenciales o medios personales leídos.
Delta propio exclusivamente documental; checkout recibido y sellos previos intactos.
La captura documental de entrada se tomó después del ensayo y antes de escribir,
contrastando los 955 paths con el sello previo, incluida la eliminación ya recibida.

**Próximo MAIN:** revisión humana de legibilidad, composición y zoom; completar
sólo los subcasos integrales que sigan abiertos en la matriz. No repetir los verdes
por rutina ni reatribuir pruebas/rendimiento de APK anteriores. La escucha TalkBack
de tres controles conserva la QA 223f anterior. **Stage 5 sigue parcial, sin
aceptación global ni autorización de distribución.** Los apartados siguientes
son evidencia histórica de sus respectivas candidatas y límites.

## Imagen ficticia y escucha — continuación del 09/10

**Resultado:** nueva QA c079…7227 instalada. Se corrigió un defecto real:
el aviso de foto agregada tapaba Volver e interceptaba el primer toque tras
recreación. Se reprodujo sin selector y se agregó regresión adversa antes del
arreglo. Fotos, Notas, Feriados y Vacaciones ahora reservan espacio para el aviso;
el Calendario conserva su composición. Auditoría y límites actuales:
[imagen y TalkBack](audits/2026-10-09-stage5-imagen-ficticia-talkback.md).
Los apartados posteriores conservan evidencia de sus propias candidatas.

**Verificación nueva:** 511/511 JVM de app; lint y APK Debug/QA/Release unsigned,
runners QA/Debug de app y Debug de base. 32 métodos Android distintos verdes
en la nueva QA: confirmaciones 2, visor nativo 1, Fotos 8, Feriados/Notas 9,
Vacaciones 7 y composición/insets 5. Incluyen 24 combinaciones de destino,
tema y zoom; no se cuentan como 24 métodos adicionales. Cortes conservados;
los últimos dos grupos se completaron con invocaciones por método, sin ampliar
el guard de foco. No se reejecutaron JVM de dominio/base ni interoperabilidad.

**Humano e imagen:** Joaquín confirmó los tres anuncios de TalkBack claros
en QA 223f anterior (recibo 200858, runner 6b6962…42df). La imagen ficticia fue
importada a almacenamiento privado, visualizada y conservada al recrear en esa
QA; allí se detectó el defecto de regreso. En la nueva QA el regreso nativo ya
pasa, pero el recorrido OEM completo quedó pendiente: venció su ventana de
selección sin confirmación y se retiró la fuente. No presentar ese timeout ni
la limpieza finally como éxito de importación/eliminación por interfaz.

**Custodia:** cuatro fuentes productivas, tres archivos AndroidTest, catálogo y
documentación relacionada; sin Room/Copias/permisos nuevos ni cambios Git.
APK final `c079941220236f08045cd00f13d0d811af96be5ffb2cff551cd62872794dc227`;
runner instalado `04377391181c59b6c57fb2a696945d44265cd1e8457a1ee6f335027156b80f55`.
Los 27 métodos de confirmaciones/visor/Fotos/Notas/Vacaciones conservan runner
e0a6db…dc60; los cinco de composición usan el runner final, tras actualizar
el identificador estático retirado. No se reasignan los 27 al runner posterior.
Público be3bca…2f94 sin abrir, instrumentar o reemplazar. Evidencia propia:
`.local-evidence/stage5/oem-positive-20261009/`; sellos anteriores intactos.
QA abierta; identidades local/instalada coincidentes, permisos/accesibilidad
restaurados y cero fuentes ficticias restantes, verificados en final-device-probe.

**Próximo MAIN:** revisar el delta y el sello, coordinar una selección ficticia
completa con Joaquín disponible y continuar la matriz humana/integral pendiente.
No repetir por rutina los verdes ni atribuir rendimiento antiguo a esta APK.
**Stage 5 sigue parcial, sin aceptación global ni autorización de distribución.**

## QA OEM y accesibilidad del 09/10

**Resultado:** diez métodos físicos nuevos aprobados sobre la misma APK QA
223f…bab8, además de los 144 dirigidos anteriores. Son fronteras concretas,
no diez recorridos integrales ni aceptación global. Leer la
[auditoría OEM](audits/2026-10-09-stage5-qa-oem-accesibilidad.md) y STATUS.

Teclado real visible/edición/Atrás; cancelación de Photo Picker y SAF;
documento sintético SAF escrito/leído/eliminado; rechazo de ubicación y
notificaciones sin captura geográfica ni avisos; Atrás del selector Samsung
conserva el sonido previo. Bloqueo real: cancelar activación, autenticar,
bloquear sin semánticas laborales, desbloquear, desactivar y recrear.
Joaquín completó los diálogos en el teléfono; no se leyó ninguna credencial.
TalkBack permaneció conectado para abrir menú, seleccionar y cancelar por
acciones accesibles. Joaquín no llegó a escucharlo: la audición sigue pendiente.

**Custodia:** dos clases Android nuevas, catálogo/opt-in explícito y cuatro
documentos. Cero cambios de producto, Room, firma o distribución; staging vacío.
El grupo `stage5-oem-supervised` sigue siendo manual_only: no habilita ejecución
desatendida de credenciales o ajustes. Recibos separados en
`.local-evidence/stage5/oem-20261009/`; los sellos anteriores no se sobrescriben.
Se conservaron cortes y fallos del arnés; un archivo vacío del corte SAF fue
identificado por nombre UUID/tamaño/instante y retirado sin tocar otros archivos.

**Identidad:** ocho métodos pertenecen al runner 7b2af8…929a; los dos de
notificaciones/sonido, al runner final
`eddebcfd607c3db3af3b6e919b9aea118870b718f3a3f87c4fe31009a56d931d`.
La APK pública conserva be3bca…2f94; no se abrió, instrumentó ni reemplazó.
Permisos y ajustes de accesibilidad restaurados, conservando servicios previos;
QA abierta al cierre. No se repitieron JVM/interop ni rendimiento por este
delta exclusivo de instrumentación y documentación.

**Próximo:** revisión humana de estética, navegación, calendario/zoom y voz de
TalkBack; selección OEM positiva de una imagen ficticia; accesos externos,
Recientes/muerte de proceso y demás subcasos integrales. No hay autorización
para red nueva, alarmas reales, firma o publicación. **Stage 5 sigue parcial.**

## Continuación del 09/10 — 144 métodos dirigidos aprobados

Leer STATUS y la [auditoría nueva](audits/2026-10-09-stage5-continuacion-qa.md).
La corrida `20261009T183106.073930Z` llegó al detalle pero Availability falló
0/3 en preparación: esperaba una alta retirada de esa pantalla. Extras y horario
real no se ejecutaron. Se preservó el fallo; no es evidencia de defecto laboral.

Se corrigieron AvailabilityActivityTest y HoursAndExtrasActivityTest
para entrar por Otras opciones del día y regresar a ese menú tras guardar.
Conservan recreaciones y aserciones Room; Retén además exige etiqueta histórica
y configuración global intactas. Catálogo actualizado; producto sin cambios.
También VacationComposeTest esperaba `Vacaciones:`; ahora exige el rango exacto
vigente, lo desplaza al viewport y comprueba visibilidad, sin perder la explicación
de horas ni la jornada preservada. Fallo físico 6/7 y build con un import omitido
conservados; import corregido, build posterior verde y cierre físico **7/7**.
Los callers recibidos del 04/10 permanecen preservados como entrada.

**Verificación.** 144 métodos distintos aprobados: 27 clases completas y un
método revisado de ubicación contextual. Incluyen los 13 Activity iniciales,
pantallas/dobles de Informes, bloqueo, Vacaciones, Clima, enlaces/actualizaciones,
Copias, Avisos y Widget; además, publicación/archivos/PDF y SQLite aislados.
No son 144 recorridos integrales ni aceptación visual. Recibos completos y límites
por superficie en la auditoría y la matriz; inventario cruza método/hash actual.
Los 136 primeros conservan runner da318a (15 de base usan 493cbf); los últimos
ocho usan b96ba8. No se reasignan todos al último runner ni se suman repeticiones.

**Build/instalación.** Perfil final aprobado, seis artefactos e insumos estables.
Producto QA 223f…bab8 intacto; sólo se regeneró/instaló runner app
`b96ba8f410c4bc579c5eba8a2d151e55effa46ac7df34b2f1df5407be1368770`.
Probe final coincidente de QA/app runner/base; pública be3bca…2f94 sin cambios,
UI, instrumentación ni datos personales leídos. QA quedó abierta en el Samsung.
925 JVM/26 interop y rendimiento del 04/10 conservan su atribución histórica;
no se repiten por cambios exclusivos de oráculos Android.

**Guard y adversos.** Se conservaron los cortes. El helper local reconoce ventana
nula sólo con dos dueños coincidentes QA/Inicio; Inicio exige instrumentación
propia activa. Una ventana del runner QA.test exige dueño previo QA, ambas
confirmaciones QA, invocación activa y teléfono sin bloqueo. Ventanas ajenas,
dueños mixtos/ausentes y bloqueo siguen rechazados; no se amplían esperas ni
permisos. Política final **12/12** Python puras. Cortes no diagnosticados siguen
sin causa atribuida, no se rebautizan como verdes.

**Custodia/próximo MAIN.** Tres callers Android, catálogo y cuatro documentos;
cero delta productivo, staging o commits. Entrada, addendum, fallos, build e
inventario en `.local-evidence/stage5/resume-20261009/`; sello propio final en
`sealed-physical-final/`, sin sobrescribir el primer sello. Revisión humana de
legibilidad, navegación, zoom y TalkBack, más subcasos integrales/OEM pendientes
(teclado, imágenes, SAF/chooser, permisos, biometría y accesos reales). Red,
alarmas reales y datos personales fuera del ensayo. **Stage 5 sigue parcial**,
sin aceptación global ni firma/distribución. No repetir los 144 por rutina.

## Entrega dirigida del 04/10 — cierre integral parcial

Leer primero STATUS. QA final instalada y abierta en Samsung; identidad local e
instalada coincidente. Este cierre resuelve calendario/recarga y entrega mediciones
válidas, pero no acredita fluidez integral, aceptación humana ni distribución.
Los apartados 03/10 y 02/10 siguientes conservan su evidencia histórica.

**Causas y delta propio.** Se reemplazó la reserva nominal de altura por medición
tipográfica real, conservando fuente y targets base ≥48 dp. CalendarCompose
esperaba el menú anterior: ahora valida el flujo primario vigente y conserva el
secundario, sin adaptar producto al oráculo viejo. La QA descubrió pérdida de
expansión al recrear: el estado vivía en contenido condicional que desaparecía
durante result=null. Un control previo falló; NextEventCard pasó a ser su dueño
estable y ambos controles posteriores pasaron. Ready volvió exactamente a la
fuente original sin observadores/esperas nuevas y cerró 7/7.

Fuentes propias: CalendarPresentation/CalendarScreen, política JVM/Common,
CalendarCompose, NextEventCard/NextEventCompose, catálogo y documentos. Ready no
conserva un cambio propio final. Trabajo recibido preservado; no cambios de Room,
Copias, reglas, permisos, dependencias, versión o applicationId.

**Verificación final.** Perfil `expansion-final/result.json`: **925 JVM + 26
interop** ejecutados, cero fallos/omitidas e inputs cambiados; lint sin errores,
24 warnings. Debug/QA/Release unsigned y runner app generados; runner base
reutilizado con fuente/hash intactos. QA final: **69 métodos dirigidos verdes**
(NextEvent 24, Common 13, Adaptive 6, Calendar 7, Ready 7, Manual 8, Ayuda real 2,
padres reales 2), más **dos métodos / 48 combinaciones VIEW** sin overflow ni
solapamiento. No sumarlos como 117 pruebas JUnit. Capturas y recibos con identidades
en la [auditoría final](audits/2026-10-04-stage5-qa-final-y-rendimiento.md).

**Rendimiento.** Doce muestras completas por versión/serie, mismo driver, semillas,
tema/zoom/ventana. Representativo y proceso frío con contenido listo dentro de
umbrales; frío p50 1704→1703 ms, p95 1947→2092 ms. Estrés inicial: **4/9 señales**
(mes, carga, preview, guardar). Contrabalanceo candidata→base: **0/9**; guardar
p50 base→candidata 591→762 ms inicialmente y 989→672 ms después. Ambos pares y
todas las muestras se conservan: variación entre sesiones no atribuida, no mejora
causal ni todo verde. Lote inicial interrumpido por foco 11/12 fuera de comparación.
No se afirma GC/térmica, FPS o almacenamiento frío; no se optimiza por hipótesis.

**Instalación y custodia.** QA `com.blackatsystems.miguardia.qa`:
`223f98d97f0a81ac67476dab8d5323aee81c0a00dd2ec102493c703eb11ebab8`;
runner app `4113d4b0747e22f94da8c5f8b0d500ed1c28e0af9d5799c12a7224b61085a45b`;
base `493cbf551f974580d2e8750a71175271f2b02fd0995c05256406605070701059`.
Pública before/after `be3bca…2f94`, consultada sólo por metadatos/hash; no abierta,
instrumentada, reemplazada ni con datos personales leídos. Se restauró la candidata
final después del benchmark, con dataset ficticio representativo, no la baseline.
Base Git `932eaeb…`, sin staging/commit/push/tag/firma pública/publicación.

**Evidencia durable.** Snapshot/delta propio y manifiesto final en
`.local-evidence/stage5/device-final-20261004/sealed-final/`; perfil, identidades y
entrada preservada en su carpeta padre. Matriz final de 48 capturas en
`matrix-20261004T191451133709Z/`, dentro de la continuación 03/10.
Comparadores `comparison-final-20261004.json` y
`comparison-stress-counterbalanced-20261004.json` conservan ambos resultados.
La [reconciliación de 33 recorridos](audits/2026-10-02-stage5-matriz-recorridos.md)
no transfiere verdes históricos a APKs nuevas ni borra requisitos pendientes.

**Próximo MAIN/Joaquín.** Revisar calendario/gestos, menú/Ayuda, carga/borrador,
zoom 100/150/200 y retorno desde detalle en la QA abierta. Siguen pendientes
legibilidad/pictogramas, TalkBack, teclado/selector OEM y doce recorridos Android
completos. Si aparece un costo reproducible, perfilar sólo ese camino app-only;
no repetir por rutina ni cambiar CAS/atomicidad para bajar una cifra. Mantener
Stage 5 parcial y sin distribución hasta resolver sus puertas explícitas.

## Continuación autorizada del 03/10

Leer primero STATUS. El zoom tipográfico mensual fue autorizado por Joaquín;
ADR 0042 sustituye su escalado geométrico anterior. No queda Stage 5 aceptado
ni autorizado para distribución. El apartado del 02/10 siguiente es histórico.

**Delta propio.** Calendario mensual usa geometría base, texto lineal 100/150/200
y siete columnas sin ancho mínimo/scroll. En selección baja se compactan acciones
y se oculta sólo la marca; Franco/Quitar Franco siguen accesibles en Más opciones.
El detalle, wizard confirmado y drawer conservan zoom previo. Apariencia informa
la excepción. Se agregaron prueba JVM de conversión SP↔DP, observaciones reales
de fuente/bounds y dos recorridos Activity de Ayuda. El probe frío independiente
y su analizador están preparados, todavía no ejecutados. Sin Room, Copias,
dependencias, permisos, versión, applicationId, firma ni app pública modificados.

**Entrega final local.** **925/925 JVM + 26/26 interop**, cero fallos/omitidas e
inputs cambiados; lint sin errores, 24 warnings; seis artefactos de build.
Recibo: `.local-evidence/stage5/zoom-approved-20261003/signals-final-full/result.json`.
La QA final está compilada, **no instalada ni probada físicamente**. El ensayo se
detuvo al detectar otra app; se pidió disponibilidad, no una autorización nueva.

**Recorte verificado.** El diagnóstico 5/6 (`20261003T233611.691909Z`)
demostró celdas de 120 px frente a objetivos de 135 px, solapados 4 px por
una reserva inferior de 180 px fuera del host. La intersección física de
Insets resuelve el caso sin cambiar 520 dp, mínimo ni fuente. Adaptive **6/6**
(`20261003T235001.475864Z`) y VIEW **48/48** (`20261003T235105.745401Z`).
Perfil completo estable: **923 JVM + 26 interop/build/lint**, cero errores e
inputs cambiados, `insets-final-full/result.json`. APK/runner preservados en
`insets-verified-apks/`; no atribuirlos a modificaciones posteriores.

**Causa y corrección posterior.** Common 7/11 sobre QA anterior y nueva con el mismo
driver: `20261003T235534.056971Z` y `20261003T235916.851996Z`. Cuatro señales
visuales de extras/disponibilidad/feriados desaparecieron con el delta anterior;
no son regresión de Insets. Se recuperaron textos cuando caben, banda compartida
de colores/pictogramas y `+` para más condiciones, manteniendo semántica y detalle.
Se retiró el check superpuesto a la fecha y el padding tipográfico de la celda.
Common usa tema real/retrato propio restaurado y tiene 13 casos compilados;
comprueba coexistencia y día 24 completado/seleccionado al 200 %. Revisión de
código independiente sin nuevo defecto concreto; no aprobación visual de 8 dp.

**Identidades y preservación.** QA local
`2e5c6c303d02fb71a315c481ad3c351f29e1e66215c32e91e935efe7f7a774dc`;
runner app `32b0b3099ec293c55962bf32d10d5c3723f3002c62314502fe7ac8969eb71292`.
APK pública reconsultada conserva `be3bca…2f94`, sin abrirla ni leer datos.
Base `932eaeb…`, staging vacío y trabajo recibido preservado. Snapshot y delta
propio: `.local-evidence/stage5/zoom-approved-20261003/local-final-20261003/`.
Los archivos sin baseline exacta se identifican como hash-only, no diff propio.

**Próximo MAIN.** Con teléfono libre/desbloqueado, instalar sólo QA/runners y
ejecutar Common 13, Adaptive 6, matriz VIEW 48 con extracción/inspección, Ayuda
real/consumidores, carga y recreación. Confirmar APK instalada por hash.
Helper revisado en `.local-evidence/stage5/zoom-approved-20261003/`.
Conservar todos los fallos, sin reducir el mínimo ni borrar significado para
aprobar una prueba. Después comparar baseline preservado/candidata con el mismo
driver: doce muestras por serie caliente y doce procesos fríos, mismas semillas
y ventanas, recibos enlazados; sin Gradle durante mediciones. No repetir el perfil
local recién cerrado si sus inputs siguen idénticos. Dejar QA abierta sólo si
el teléfono está disponible. Legibilidad humana, TalkBack/OEM y aceptación pendientes.
No usar app pública, datos personales ni ajustes Android globales; parar ante
interferencia durante el ensayo. No commit/push/firma/publicación por inferencia.

Auditoría, recibos adversos y límites:
[continuación del 03/10](audits/2026-10-03-stage5-zoom-mensual-autorizado.md).
Capturas del candidato Insets en `matrix-20261004T001641195556Z/` dentro de esta
continuación; no pertenecen a la corrección posterior de señales/check.
La matriz de 33 recorridos conserva sus requisitos y distingue la evidencia
histórica del 02/10. No se declara fluidez integral ni Stage 5 terminado.

## Antecedente: cierre parcial del 02/10

## Decisión

Recibir **candidata parcial revisable**, no Stage 5 terminado ni aceptación.
Ayuda está corregida y pasó sus 12 pruebas físicas. El ensayo caliente se
completó con baseline/candidata y no superó umbrales de investigación.
Calendario sigue con 32/48 combinaciones fallidas: requiere decisión material
de zoom. Frío tiene TotalTime, pero no contenido listo ni equivalencia de
ventanas comprobada. Legibilidad humana, TalkBack y OEM siguen pendientes.

La QA final quedó instalada y abierta en el Samsung tras autorización
«Autorizar; Samsung libre y desbloqueado». Su hash local/instalado coincide;
APK pública sin cambios. No se abrió ni se leyó su base de datos.

## Entrada y delta propio

`C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`,
rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`
más checkout acumulado. Un worktree, upstream origin de la misma rama,
staging vacío. Sin commit/push. Entrada íntegra: `.local-evidence/stage5/entry/`.

- MiGuardiaApp/HelpScreens: BringIntoView sobre Fotos/Cargar/Ayuda reales,
  reposicionamiento por paso/sesión/tamaño/zoom, MENU sin drawer que tape su
  botón y explicación limitada al espacio disponible. No ejecuta el control.
- HelpComposeTest: conserva el oráculo original y agrega matrices de anclas
  visibles, temas/zoom/orientación, regreso y restauración Compose. Cierre
  mediante evento ficticio no prueba coordinador real ni muerte de proceso.
- Stage4FlowsActivityTest: diagnóstico mínimo, latch de foco, Atrás nativo
  para la ventana ModalBottomSheet, orientación de Activity y metadatos de
  ventana/datos. El guard sigue rechazando foco ajeno. Catálogo corta por método.
- CalendarCommonV2ComposeTest: tres accesos a situaciones pasan por el menú
  contextual vigente; preserva aserciones de estado y UUID. Fallos conservados.
- STATUS, índice, auditorías y matriz reconciliados. Manifiesto exacto, hashes,
  copias y patch propio contra la entrada: `.local-evidence/stage5/final/`.

Sin cambios propios a calendario, formulario Stage 3.2, Room/esquemas, Copias,
reglas laborales, dependencias, permisos, versión ni applicationId.

## Evidencia verificable

[Auditoría](audits/2026-10-02-stage5-cierre-parcial.md),
[33 recorridos](audits/2026-10-02-stage5-matriz-recorridos.md) y
[rendimiento](audits/2026-10-02-stage5-rendimiento.md) contienen recibos y límites.

- Local final: **916 JVM + 26 interop**, cero fallos/omitidas; lint sin errores
  y 24 advertencias; builds y runners verdes. `final-validation/result.json`
  bajo `.local-evidence/stage5/`, `changed_inputs=[]`. Cambios posteriores sólo
  de runner se compilaron en `measurement-runner-validation/result.json`.
- **85 métodos dirigidos físicos verdes** entre UI/dobles, Activity real y
  SQLite/archivos sintéticos; alcance de cada clase en auditoría. Migraciones
  sintéticas no equivalen a actualizar la app pública ni a restaurar una Copia personal.
- Calendario: dos métodos recorren 48 combinaciones, 16 pasan geometría y 32
  fallan. Las 48 capturas finales son idénticas a Stage 4; no se ocultó el fallo.
- Rendimiento: doce iteraciones × nueve operaciones × dos datasets × dos APK;
  mismo runner/datos/ventanas calientes. Ninguna operación excede umbrales;
  estrés Resumen p50 1602,3 ms no demuestra experiencia aceptable. Frames no son FPS.
- Frío n12 por APK: TotalTime p50 463→488 ms, p95 481→513 ms. Datos iguales;
  tamaño/orientación de esas aperturas y contenido listo pendientes.

QA SHA-256:
`4d708c1f6e398e0675dac885b65b8728c992488a796ed22280f77d72b4330b30`.
Runner final:
`85915a760345fa62fa0804a589f8934399674002e6258fe3103a839e0a2769cc`.
Runner database:
`9d4be8ff629df0bef020936c6f1064f1bb680eb9a5b1766e2a9654c026bbcfce`.
Cada recibo anterior conserva el runner que realmente ejecutó; producto QA
idéntico durante la sesión. Identidad final en
`.local-evidence/stage5/device/final-identity.json`.

## Próxima decisión y trabajo pendiente

1. Resolver con Joaquín el calendario. Recomendación presentada: zoom de texto,
   objetivos de al menos 48 dp de densidad base y menos información secundaria;
   conserva mes completo pero cambia el escalado geométrico. Alternativas:
   permitir desplazamiento o mantener política con ventanas bloqueadas.
   No se recibió decisión ni se aplicó silenciosamente ninguna alternativa.
2. Tras decidir, implementar y repetir matriz completa sobre nuevo APK, guía
   y regresiones afectadas; los recibos actuales no se trasladan a otro producto.
3. Completar contenido frío con observación exclusiva QA y ventanas equivalentes,
   además de guía con coordinador real/recreación y revisión humana visual,
   TalkBack, teclado y selector OEM. Respetar disponibilidad vigente del teléfono.

Se conserva toda evidencia adversa. La aceptación humana, firma y distribución
son puertas independientes; no crear ramas, worktrees ni tareas al continuar.
