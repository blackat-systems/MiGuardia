# Stage 3 — selección múltiple y calendario plegable

Fecha: 2026-09-15  
Resultado: **STAGE 3.1 VERIFICADO LOCALMENTE Y EN QA FÍSICA SINTÉTICA;
PENDIENTE DE REVISIÓN HUMANA Y ACEPTACIÓN DEL CANDIDATO**.

## Puerta 0 y autoridad

- Checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, base
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`, un solo worktree y remoto esperado.
- El árbol ya contenía el candidato Stage 1/2. Joaquín aceptó Stage 2 y autorizó
  implementar Stage 3 el 2026-09-15; se preservó todo el trabajo previo.
- No hubo commit, push, tag, firma, publicación, tráfico real ni dispositivo.

## Resultado funcional

- Un toque breve conserva la apertura del detalle. La pulsación larga selecciona
  el día; `Seleccionar días` ofrece la misma entrada visible.
- El estado distingue selección general, carga manual y feriados. Fechas,
  propósito, confirmación, mes, plegado y último día abierto sobreviven recreación
  mediante el estado guardado correspondiente.
- La grilla alterna entre el mes de 42 celdas y la semana activa de siete días.
  La semana prioriza selección, último día abierto, hoy dentro del mes y primer
  día del mes. El control anuncia su estado y acción accesible.
- La zona inferior no se desplaza. En vista ofrece carga y selección; al elegir
  días ofrece contador, `Continuar` y `Cancelar`. Carga manual y feriados usan la
  misma zona sin duplicar controles dentro del contenido.
- La hoja de acciones contiene únicamente carga de jornadas, Franco, Sin definir
  y Retén. Retén exige horario explícito: no precarga `21:00–06:00` ni otro valor
  de configuración.

## Escritura y concurrencia

- Franco, Sin definir y Retén pasan por un contrato único de preview con fechas
  `se aplicará`, `ya estaba así` o `conflicto`, explicación concreta y token de
  expectativa.
- La confirmación vuelve a calcular el conjunto dentro de una transacción Room.
  Cualquier cambio concurrente o conflicto aborta el lote completo; Compose no
  encadena escrituras individuales.
- Retén conserva una misma franja y etiqueta solicitadas, pero crea registros
  independientes por fecha. El contrato actual alcanza con DAO/repositorio: no
  hubo migración ni cambio de esquema Room.
- Cargar jornadas entrega el conjunto vigente al flujo manual existente, sin
  limpiar ni reconstruir la selección. Feriados conserva su propósito aislado.

## Verificación local

- Pruebas focalizadas de estado/ViewModel verdes, incluida restauración del
  propósito, prioridad de semana activa y cierre sólo después de aplicar el lote.
- AndroidTest afectado de app y base compila. Las regresiones agregadas cubren
  gesto/alternativa/plegado/hoja, clasificación de fechas, rechazo por cambio
  concurrente sin escrituras parciales y Retenes independientes/idempotentes.
- Los perfiles `local --fresh-tests` y `build`, el chequeo documental, el catálogo
  QA y `git diff --check` pasaron como cierre.
- El perfil local fresco pasó 865/865 pruebas JVM sin fallos ni omitidas: 392 de
  dominio, 12 de base y 461 de app. Recibo:
  `.local-evidence/optimization/20260915T185214.799977Z/result.json`.
- El perfil build pasó `lintDebug` y armó APK Debug, QA, Release unsigned y los
  runners AndroidTest de app/base. Recibo estable, sin cambios de inputs durante
  la corrida: `.local-evidence/optimization/20260915T185314.327483Z/result.json`.

## Riesgos y puerta siguiente

- La compilación de AndroidTest no reemplaza la ejecución instrumentada. Quedan
  pendientes gesto real, scroll/área inferior, rotación, temas, zoom 100/150/200,
  TalkBack, teléfono pequeño/grande, retrato/paisaje y persistencia Room real.
- La matriz física debe ejecutarse sólo con autorización específica y datos
  sintéticos. No debe tocar el paquete público.
- El candidato no está aceptado, integrado por commit, firmado ni distribuido.

## Corrección posterior — prioridad de Atrás

MAIN encontró un defecto bloqueante antes de la QA física: el manejador general
de selección se definía para cualquier estado `EDIT` sin exigir propósito
`GENERAL`. La exclusión de carga manual y feriados dependía indirectamente de
otras superficies y del orden de sincronización; el general podía cerrar sólo la
selección visual y el flujo externo todavía activo podía restaurarla.

La regresión se escribió antes del arreglo. Con el criterio recibido,
`externalCancellationCannotHandManualOrHolidaySelectionToTheGeneralOwner` falló:
`CalendarBackPriorityTest` ejecutó 3 pruebas y registró 1 fallo. El resultado
adverso se conserva aquí; no se reinterpretó como un verde.

La corrección incorpora `calendarSelectionBackOwner`, que entrega un solo dueño
efectivo entre `GENERAL`, `MANUAL_LOAD` y `HOLIDAY`. El general exige propósito
`GENERAL`; los flujos externos tienen prioridad durante sus transiciones. El
dueño de feriados ahora cancela el flujo y finaliza el modo visual en la misma
acción. Carga manual conserva el contrato anterior: cancelación completa en la
etapa inicial y regreso a las mismas fechas desde una etapa posterior.

Regresiones nuevas:

- selección general: Atrás descarta primero el diálogo y después la selección;
- carga manual inicial: queda inactiva, el Calendario vuelve a `VIEW`, no hay
  reentrada ni escrituras;
- carga manual posterior: vuelve a `SELECT_DATES` con las fechas conservadas y
  sin escrituras;
- feriados: queda inactivo, vuelve la superficie de feriados, el Calendario queda
  en `VIEW` y la sincronización no restaura la selección;
- arbitraje: incluso ante estados externos superpuestos existe un solo dueño;
- coordinadores de manual y feriados comprueban ausencia de mutaciones al
  cancelar.

Evidencia posterior al arreglo:

- focalizada JVM: 58/58 verdes —3 prioridad, 7 estado de Calendario, 40 carga
  manual y 8 feriados—;
- perfil local fresco: 872/872, cero fallos u omitidas; recibo
  `.local-evidence/optimization/20260915T194933.145344Z/result.json`;
- perfil build: `lintDebug`, APK Debug/QA/Release unsigned y runners AndroidTest
  de app/base verdes, con cero inputs cambiados; recibo
  `.local-evidence/optimization/20260915T195010.540649Z/result.json`;
- el preflight previo sin Java en `PATH` no ejecutó pruebas y permanece en
  `.local-evidence/optimization/20260915T194920.506018Z/result.json`;
- las regresiones Compose/Activity están compiladas y catalogadas, pero no fueron
  ejecutadas en Android.

La matriz física queda preparada para validar diálogo → selección → pantalla,
cancelación general, cancelación manual inicial sin reentrada, regreso manual
posterior con fechas preservadas, cancelación de feriados sin restauración y cero
escrituras. También conserva gesto largo, barra inferior, temas, zoom,
orientación, recreación y TalkBack. No se ejecutó dispositivo, ADB ni emulador.
Stage 3 sigue pendiente de revisión de MAIN; no está aceptado ni listo para
distribución.

## Ampliación posterior — detalle por jornadas y Calendario estático

Joaquín amplió el candidato Stage 3 antes de la QA física. El detalle normal
tenía fecha corta, adelantaba `Condición del día`, separaba las jornadas sólo con
divisores y dejaba el horario real fuera de su agrupación. Además, el acceso al
clima se omitía o deshabilitaba sin resumen y cerraba el detalle al abrirse. La
pantalla principal todavía tenía `ScrollState`, `verticalScroll` e indicador,
aunque su barra inferior ya estuviera visualmente separada.

La implementación usa fecha humana completa, un único `Editar día`, título
condicional `Jornadas del día` y una tarjeta por UUID, ordenada por inicio. Cada
tarjeta reúne sus callbacks y datos, incluido horario real. La tarjeta de clima
siempre es accionable: explica deshabilitación, inelegibilidad o ausencia de un
pronóstico utilizable y delega el diagnóstico exacto al recorrido vigente. No
activa Clima ni pide permisos; cerrar Clima revela el mismo detalle.

El Calendario principal eliminó el scroll vertical y su indicador. El contenido
estático ocupa el espacio restante y la barra inferior permanece fuera de ese
viewport. `Más opciones` abre un diálogo. Cuando el alto disponible es menor a
1050 dp se muestra la semana activa sin mutar la preferencia explícita de
plegado; el gesto largo sigue siendo `combinedClickable` nativo y el toque breve
conserva la apertura del día.

Regresiones preparadas:

- política JVM para mes versus semana restringida;
- ausencia de contenedor/indicador de scroll, barra fija y opciones avanzadas en
  diálogo;
- teléfono restringido y todos los zoom internos sin scroll vertical;
- dos jornadas del mismo objetivo y horario con tarjetas y callbacks por UUID;
- fecha humana, título condicional, clima apagado accionable y retorno al mismo
  detalle;
- toque breve, pulsación larga, alternativa visible y arbitraje de Atrás previo.

Evidencia focalizada: `CalendarStaticLayoutPolicyTest` verde y AndroidTest de app
compilado. El perfil local fresco pasó 871/871 JVM, sin fallos, errores u
omitidas; recibo
`.local-evidence/optimization/20260916T020844.286657Z/result.json`. El perfil
build pasó `lintDebug`, APK Debug/QA/Release unsigned y runners AndroidTest de
app/base; recibo
`.local-evidence/optimization/20260916T020947.934854Z/result.json`. Ambos
registran cero inputs cambiados. La instrumentación no se ejecutó.

La matriz física anterior se conserva y suma detalle con cero/una/dos jornadas,
identidad duplicada visualmente, orden vertical, Maps/Notas/imágenes/Avisos,
horario real, todos los estados de clima, retorno al detalle, ventana
chica/grande, retrato/paisaje, zoom 100/150/200 y TalkBack. Stage 3 no queda
aceptado ni listo para distribución; MAIN debe revisar este delta antes de
reconsiderar esa QA.

## QA Samsung autorizada y corrección posterior — 2026-09-16

La instrumentación se ejecutó exclusivamente sobre la variante QA en un Samsung
`SM-S938B`/API 36; el paquete público no fue instrumentado. Las primeras corridas
se conservaron como evidencia adversa y no se reemplazaron por los verdes.

La matriz confirmó tres defectos dentro del alcance. En un viewport restringido,
la semana forzada seguía reservando espacio para próximo evento y un control de
plegado deshabilitado, reduciendo el área útil. Además, `clearAndSetSemantics`
quedaba antes del `testTag` del día y borraba el identificador observable. Por
último, después de elegir fechas para carga manual, el wizard se componía debajo
de la grilla estática: sus controles y Cancelar podían quedar recortados.

La corrección omite los bloques no accionables en semana forzada, conserva los
tags después de aplicar semántica y hace que las etapas posteriores del wizard
reemplacen la grilla en una superficie desplazable propia. La pantalla principal
del Calendario continúa sin scroll y su barra inferior sigue fija. No se cambió
Room, Copias, versión, `applicationId`, firma, distribución ni website.

Resultados físicos automatizados finales:

- Adaptive 4/4: `.local-evidence/qa/20260916T024344.054895Z`;
- Common 11/11: `.local-evidence/qa/20260916T024603.679957Z`;
- Calendar 6/6: `.local-evidence/qa/20260916T025134.471744Z`;
- Exceptions 8/8: `.local-evidence/qa/20260916T025413.575371Z`;
- Weather 12/12: `.local-evidence/qa/20260916T031132.350479Z`;
- carga manual: dos 6/6 consecutivos del runner oficial en
  `.local-evidence/qa/20260916T035826.022573Z` y
  `.local-evidence/qa/20260916T035851.909662Z`.

Entre la evidencia adversa preservada hay corridas Adaptive de 2/4 y 3/4,
Calendar de 3/6 y 5/6, Exceptions de 6/8 y 7/8, y carga manual desde 0/6 hasta
5/6. Esas fallas permitieron distinguir expectativas antiguas de los tres
defectos productivos descriptos arriba.

El cuarto cierre investigó específicamente los cuatro 5/6 consecutivos. Antes de
instalar el runner corregido se volvió a ejecutar el método aislado con el APK de
prueba anterior. Falló en `rotateAndVerifyDraft`, línea 545: Compose encontró el
valor `Puesto ficticio integral`, pero `assertIsDisplayed` rechazó el nodo porque
estaba fuera del viewport después de la rotación. La salida completa y los hashes
se preservaron en
`.local-evidence/qa/20260916T035744.064486Z-diagnostic/diagnostic.txt`.

La causa fue una aserción de prueba incompatible con el wizard desplazable, no
un defecto de restauración, producto, fixture o runner. La regresión corregida
comprueba el estado del ViewModel —etapa, fecha, plantilla y puesto— y que no
existan jornadas antes de guardar. Después identifica el campo por
`v2-manual-position`, lo lleva al viewport y valida su `EditableText`. Conserva
recreación, rotación a paisaje y retrato, guardado, snapshot y reapertura; no
aumentó esperas ni relajó datos esperados.

Las dos corridas finales usaron los mismos hashes: APK QA
`08246b45605e009224c6af5a0b9d2fbef0d91515b4230b479548409ee435620b`,
runner QA `5e678c8ed7d7683fee5111168640ca8e87dfbf1a82a860cf6c863787c72a4e8e`
y fuente
`beed4a63cc150baa556c8543273050bf78267b5d46441a8b812639c5050dcb93`.
Ambas registraron identidad verificada, metadatos sin cambios y cero fallos,
omitidas o métodos pendientes. La app pública no fue instrumentada y permaneció
instalada con hash
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.

El cierre local posterior pasó 871/871 JVM en
`.local-evidence/optimization/20260916T031219.234739Z/result.json`. El perfil
build pasó `lintDebug`, APK Debug/QA/Release unsigned y runners AndroidTest de
app/base en
`.local-evidence/optimization/20260916T031337.785714Z/result.json`; ambos
registraron cero inputs modificados durante la corrida.

Quedan pendientes la observación humana de pulsación larga nativa, composición,
barra inferior, temas, zoom y TalkBack. El umbral que fuerza semana por debajo
de 1050 dp sigue pendiente de medición y aceptación visual; no fue modificado.
Esta evidencia permite a MAIN revisar el candidato, pero no acepta Stage 3 ni lo
habilita para firma o distribución.

## Stage 3.1 — decisión anterior reemplazada

Joaquín rechazó la semana impuesta y la barra inferior permanente. Esta orden
reemplaza expresamente el umbral menor a 1050 dp, el plegado persistido y los
controles permanentes de carga/selección/repetición documentados arriba.

La causa quedó demostrada en `calendarRequiresRestrictedWeek`, su combinación
con `calendarCollapsed`, la reserva fija de 42 posiciones con celdas de 100 dp y
`CalendarPrimaryActions` compuesto siempre al pie. Las pruebas anteriores
legitimaban ese comportamiento; no se borró este antecedente adverso.

La corrección muestra siempre el mes completo con cuatro, cinco o seis filas,
ignora la preferencia histórica de plegado y deja la pantalla principal sin
scroll. Un toque abre el detalle; la pulsación larga nativa inicia selección y
sus acciones desaparecen al salir. Las funciones desplazadas continúan en el
menú lateral existente. Carga manual conserva su superficie desplazable propia.

Una primera captura física en paisaje mostró un defecto adicional: la tarjeta
opcional de próximo evento consumía el alto y comprimía las celdas hasta líneas
no operables. Se preservó en
`.local-evidence/qa/stage-3-1-20260916-review/calendar-landscape.png`. La
corrección omite esa tarjeta sólo en ventanas bajas y conserva todo el mes; el
resultado final está en `calendar-landscape-final.png` de la misma carpeta.

Regresiones nuevas comprueban 28/29/30/31 días, meses de cuatro/cinco/seis
filas, extremos contenidos y operables, zoom interno 100/150/200, cambio de
mes/año, recreación, retorno y preferencia heredada; además de toque breve,
gesto largo, cancelación, acciones contextuales, menú y ausencia de barra
normal. El árbitro de Atrás y las garantías de cero escrituras se conservaron.

Evidencia Samsung final: Adaptive 5/5 (`20260916T052951.917822Z`), Common 11/11
(`20260916T053007.875816Z`), Calendar 6/6 (`20260916T053020.833463Z`),
Exceptions 8/8 (`20260916T052823.359552Z`), recreación/paisaje 7/7
(`20260916T052720.651162Z`), repetición 1/1 (`20260916T052808.920409Z`) y dos
cierres consecutivos de carga manual 6/6 (`20260916T053037.961412Z` y
`20260916T053058.488376Z`). Todos pertenecen a `.local-evidence/qa/` y usaron
el paquete QA con datos sintéticos.

La variante final instalada quedó identificada por APK QA
`7c4bc72b76f3d8492387297acf87b71e20042a26db6c9dd45621b09701c288f2`
y runner
`ba3cc9f19037e5e1b35959c67bb578f0173eab31edccd3e387dca9ecc8b08e2b`,
coincidentes en disco y dispositivo. El paquete público permaneció instalado,
fuera de instrumentación, con hash
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.

El cierre local posterior pasó 872/872 JVM, sin fallos, errores, omitidas ni
inputs cambiados, en
`.local-evidence/optimization/20260916T053300.438772Z/result.json`. El perfil
build pasó `lintDebug`, APK Debug/QA/Release unsigned y runners AndroidTest de
app/base, también sin cambios de inputs, en
`.local-evidence/optimization/20260916T053434.709169Z/result.json`.

Quedan pendientes revisión visual humana, temas, combinaciones completas de
zoom/orientación/filas y TalkBack. Este cierre técnico no constituye aceptación
de Stage 3 ni autorización para firma o distribución.

## Cierre: puerta de persistencia — 2026-09-16

### Alcance inspeccionado y procedencia

Nuevo contrato: `docs/prompts/STAGE_3_CIERRE_PROXIMA_JORNADA_Y_CARGA_UNIFICADA_3_0.md`.
Puerta 0 confirmó checkout independiente MiGuardia-3.0, rama
`codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`,
upstream homónimo en origin, remoto `https://github.com/blackat-systems/MiGuardia.git`,
un worktree y sin staging. Cambios acumulados rastreados/no rastreados conservados;
sin locks Git observados. MAIN figuraba idle y no se observó otro escritor activo
de MiGuardia. Eso es una comprobación puntual, no un bloqueo de exclusión mutua.

Se contrastaron código y recibos anteriores. Los 872 JVM y receipts físicos de
Stage 3.1 siguen siendo evidencia del candidato anterior, no del nuevo recorrido.
No se usó Samsung/ADB, no se compiló ni se ejecutaron tests de producto en este
diagnóstico. No se produjo ni instaló APK nuevo. No se verificó nuevamente el
estado físico actual del paquete público ni de QA.

### Causa demostrada por inspección estática

Estas diferencias no son fallos intermitentes ni nuevas regresiones ejecutadas:

1. `core/domain/.../shift/V2ShiftPlanning.kt`, `planV2ShiftBatch`: REPLACE deriva
   eliminaciones de todas las jornadas cuya fecha de inicio está ocupada dentro
   de la selección. No usa exclusivamente la intersección del intervalo nuevo.
   Ejemplo: existente 08–10 y 18–20; nueva 09–11 puede eliminar ambas.
2. `core/database/.../repository/RoomV2ShiftRepository.kt:248–252`: exige que la
   fecha de inicio de cada eliminación pertenezca a writeDates. Un conflicto
   23–07 del día anterior no puede reemplazarse al cargar 06–10 del siguiente.
   En líneas 302–318, una ocurrencia recurrente además requiere reemplazo de la
   misma fecha para su exclusión; no basta cambiar la lista del planificador.
3. `app/.../ui/management/V2ManualShiftLoadViewModel.kt:1171`: pasa savedDates como
   explicitDayStatusDatesToClear también en ADD_SECOND_SHIFT. Por eso Superponer
   no cumple conservar Franco aunque conserve las jornadas.
4. `core/database/.../repository/RoomCalendarBatchRepository.kt`: UNDEFINED
   rechaza contenido ocupado y sólo inserta el marcador (líneas 61–64, 125 en
   adelante). No elimina ni divide rangos. No es la limpieza pedida.
5. `ShiftNoteRepository.saveWithAttachments` / `RoomShiftNoteRepository` guardan
   cada nota en su propia transacción. V2ShiftBatchMutation no incluye notas ni
   adjuntos: guardar jornadas y luego notas permite resultado parcial ante fallo.
6. `ShiftNoteAttachmentEntity.kt:21` impone storageKey único global. Reusar una
   referencia física para varias notas no sirve con ese contrato. El file store
   elimina archivos por clave y reconcilia contra claves conocidas; hay que
   coordinar también borradores vivos antes de añadir otro dueño de adjuntos.
7. La expectativa de ocupación versiona jornadas, no todo el conjunto de notas,
   imágenes, situaciones y rangos que el preview destructivo nuevo debe proteger.
   Las operaciones públicas de médica/vacaciones tampoco ofrecen una limpieza
   multifecha conjunta con la carga y sus referencias.

### Propuesta pendiente de decisión explícita

El usuario exige presentar alternativas antes de un nuevo contrato material de
persistencia. No se modifica aún esa capa. No se demostró necesidad de migración:
las tablas actuales parecen suficientes, pero esto es una hipótesis a validar,
no una garantía de implementación o compatibilidad de Copias ya probada.

**A — Recomendada: ampliar la unidad transaccional sobre el esquema actual.**
Plan puro por intervalos y fechas propietarias, preview de cada pérdida y
expectativas CAS de todos los registros leídos/afectados. Confirmación aplica
atómicamente jornadas, estados, notas y referencias, incluyendo exclusión de
ocurrencias recurrentes y sustracción de fechas de rangos en limpieza. Sin borrar
catálogo ni fechas exteriores. Preparar copias privadas independientes por nota
con claves distintas, compensar archivos no confirmados y coordinar reconciliación
con borradores activos. Archivos y SQLite no comparten una transacción física:
hay que demostrar recuperación segura frente a fallo, no prometer atomicidad de
filesystem por envolver sólo SQL. Conservar esquema/exportaciones/Copias; detenerse
de nuevo si se demuestra imposible sin migración o regla de pérdida adicional.
Costo: amplía el contrato interno y requiere regresiones de integración y fallos,
no sólo pruebas de interfaz. No autoriza reescritura general ni otra persistencia.

**B — Diferir el recorrido unificado completo.** Conservar operaciones actuales
sin presentarlas como cumplimiento y resolver únicamente tarjeta/navegación que
no dependa del nuevo guardado. Reduce riesgo inmediato pero no cierra Stage 3.
No se recomienda simular limpieza con un marcador ni hacer commits parciales de
jornadas y notas: ambas opciones contradicen el pedido y no son alternativas válidas.

### Regresiones preparadas conceptualmente, todavía no implementadas

- Overwrite 09–11 elimina 08–10 y conserva 18–20; contiguas no se eliminan.
- Conflicto nocturno anterior, también recurrente, se muestra y sustituye sólo
  tras confirmar; cambios de datos asociados invalidan la revisión completa.
- Overlay conserva Franco y jornadas, visibles para proyecciones y consumidores.
- Dos fechas con notas/adjuntos independientes; fallo en la segunda revierte DB,
  limpia sólo archivos propios no referenciados y conserva borradores ajenos.
- Limpiar fecha intermedia divide atribuciones, conserva extremos, referencias
  y jornadas vecinas; cancelar no escribe ni borra archivos persistidos.
- Reintento técnico no duplica; cambio concurrente regenera preview.

Delta de esta intervención: sólo contrato, índice, STATUS, handoff y esta sección.
Tarjeta, navegación y formulario aún sin cambios. No hay nuevas regresiones
verdes, capturas, recibos físicos ni hash instalado que entregar. La matriz humana
y toda la validación del contrato nuevo permanecen pendientes. Cierre técnico,
aceptación humana y distribución no declarados.

Validación documental de esta intervención: primer docs_check detectó 40.054
caracteres frente al máximo 40.000; se compactó una frase obsoleta de STATUS,
sin borrar evidencia histórica. Catálogo QA válido (109 clases, 19 grupos) y
git diff --check sin errores. Verificación documental final repetida tras ajustar.

## Cierre autorizado: implementación y verificación — 2026-09-16

Joaquín respondió «autorizo plenamente» a la propuesta A. La puerta anterior
queda resuelta para ampliar transacciones/CAS sobre el esquema actual, no para
migrar ni modificar Copias. Se conserva el diagnóstico anterior como evidencia
adversa; ya no describe el estado de implementación de esta continuación.

### Delta propio

- `NextEventCard`, `NextEventObserver`, `NextEventViewModel`: jerarquía próxima
  jornada/fecha/contador/identidad, unidades completas, reloj por frontera/minuto
  y relectura al primer plano. Resumen completado ya no esconde la próxima jornada.
- `CalendarDayDetail` y `MiGuardiaApp`: Editar día entra directamente a la carga
  con la fecha confirmada; otras capacidades permanecen en opciones contextuales.
- `UnifiedManualLoadForm`, `V2ManualShiftLoadScreens/ViewModel`: único formulario,
  fechas expandibles, catálogo por institución/horario, creación encadenada,
  nota opcional, Franco, Limpiar y Cancelar; sin barra permanente mensual.
- `V2ShiftPlanning`, `ShiftWorkSnapshot`, `V2ShiftRepository` y
  `RoomV2ShiftRepository`: sobrescritura por intervalos, vecino nocturno y
  exclusiones recurrentes; CAS de datos asociados/estados; notas y adjuntos
  dentro del mismo commit; reintento con identidad estable sin doble carga.
- `CalendarBatchRepository`, `RoomCalendarBatchRepository`, `CalendarClearOperation`:
  CLEAR separado de UNDEFINED; elimina sólo registros propietarios, divide
  rangos conservando partes exteriores, preserva feriados informativos/catálogo,
  excluye ocurrencias para que no reaparezcan y revalida todas las referencias.
- `ManualLoadNotes`, `SchedulePhotoFileStore`, `ExceptionsViewModel`, `MainActivity`:
  borradores privados separados, copias independientes por nota, límite de 30,
  promoción coordinada con la puerta existente de restauración y reconciliación.
  Limpieza poscommit por referencias, sin convertir un error de limpieza en un
  falso fallo de guardado. Filesystem y SQLite no se declaran una única transacción:
  archivos primero, metadata atómica después y compensación/reconciliación segura.
- `CalendarScreen` y `CalendarViewModel`: cancelación del formulario reutiliza
  confirmación existente; se evita duplicar su botón en el formulario principal.
- Pruebas JVM/Android afectadas, catálogo QA y opt-in de captura sintética
  `captureContextualEvidence=true` en grupo explícito del runner.

Los cambios previos de versión/configuración, Room V8, Copias y otros módulos
que ya estaban sucios no forman parte de este delta. No se descartaron ni se
incorporaron a un commit. No hay cambio nuevo de esquema, Copias, applicationId,
permisos, firma de distribución, website ni paquete público.

### Evidencia local nueva

- Focal inicial: planificación de intervalos y división de rangos + compilación
  Kotlin, verde (`.local-evidence/stage3-closure-first-compile.log`).
- Focal formulario/Atrás/formato: 55 pruebas verdes y AndroidTest app/base
  compilados (`.local-evidence/stage3-closure-focal.log`); segunda comprobación
  pertinente en `stage3-closure-focal-final.log`.
- Perfil local completo y fresco:
  `.local-evidence/optimization/20260916T074330.187403Z/result.json`;
  **888/888 JVM**: dominio 395, database 16, app 477; cero fallos, errores u
  omitidas y cero inputs cambiados durante esa ejecución. Incluye regresiones
  de respuesta perdida, recreación y rechazo de reconocimiento parcial.
- Runner: 34/34 `tools/tests/test_qa.py`, sin usar dispositivo en esas pruebas.
- Catálogo: 111 clases; grupos explícitos nuevos separan persistencia de carga
  (sin restauraciones) y Activity con capturas opt-in de datos ficticios.

### Límites de implementación a revisar

La carga conserva selección de un único mes. Un cambio/archivo del horario en el
catálogo después de un commit con respuesta perdida puede impedir reconocer el
reintento por diferencias históricas: falla seguro, sin duplicar ni asumir éxito.
La limpieza de archivos ante un fallo de lectura de referencias conserva el
archivo y delega recuperación a reconciliación; no borra a ciegas.
La evidencia del selector OEM de imágenes y TalkBack debe distinguirse de las
pruebas automatizadas de archivos, semántica y selección.
Los recibos nuevos y la identidad final se detallan a continuación; la evidencia
histórica no sustituye la QA del candidato nuevo.

### Hallazgos adversos conservados durante el cierre

1. Primera instrumentación CLEAR: 3/8, cinco fallos, recibo
   `.local-evidence/qa/20260916T075714.373749Z`. Diagnóstico directo sintético
   preservado en `stage3-clear-diagnostic.txt`: `ConflictingLocalWriteException`
   entre preview y confirmación sin mutación intermedia. La huella usaba
   `WorkConfigurationHistory.toString()`, cuyos contenedores no-data incluían
   identidades de objetos recreados por cada lectura. Causa de producto compartida
   por CLEAR y acciones masivas preexistentes, no intermitencia. Se agregó
   `CalendarConfigurationToken`: id, revisiones y valores por período completos,
   con orden estable. Tres JVM demuestran igualdad entre instancias distintas y
   desigualdad ante cambios reales; Android compara previews independientes y
   aplica CLEAR y Franco. Resultado oficial nuevo: 9/9 en
   `qa/20260916T080438.688813Z`. CAS y rollback no fueron relajados.
2. Primer Activity: 6/7 en `qa/20260916T080454.373648Z`. La prueba nueva intentaba
   insertar una jornada 20–23 usando la instantánea de una plantilla 08–16.
   El repositorio rechazó correctamente esa fixture inconsistente. Diagnóstico
   `stage3-load-flow-diagnostic.txt`. Se creó una segunda plantilla ficticia
   mediante el catálogo real y se conservó la aserción exacta de la jornada
   no afectada. No se cambió validación de producto ni se aumentaron esperas.
   El recorrido completo posterior pasó 7/7 (`qa/20260916T081056.134242Z`).
3. Revisión cruzada: KEEP_OCCUPIED no omitía Franco porque el plan sólo conocía
   solapamientos. Se filtran esos candidatos y se conservan marca/omisión; dos
   JVM cubren Franco+libre y todos-Franco, sin lote vacío ni escrituras espurias.
   Las 50 pruebas del coordinador pasan en `stage3-final-integration-validation.log`.
4. Inspección de capturas detectó título doble «Cargando jornadas» en el formulario.
   Se retira sólo la cabecera de selección cuando el formulario ocupa la pantalla,
   conservando el título propio y la cabecera de elección de fechas. Regresión
   Activity exige un único título. Las capturas finales esperan la desaparición
   normal del aviso transitorio usando el reloj Compose y la comprueban; no
   ocultan ni cambian la duración de avisos en producto.

### Verificación proporcional y aislamiento

- Perfil build completo: `optimization/20260916T074516.455721Z/result.json`,
  lint, Debug/QA/Release unsigned y ambos runners verdes, sin cambios de inputs.
  Ese recibo precede a los hallazgos físicos; no se usa para atribuir el APK final.
- Luego del CAS: `stage3-cas-fix-validation.log`, siete JVM (tres token + cuatro
  rangos), QA/app AndroidTest/base AndroidTest y lint, verdes.
- Luego del ajuste KEEP: 50 JVM del coordinador, QA/AndroidTest/lint verdes en
  `stage3-final-integration-validation.log`. Las cinco JVM nuevas posteriores
  al perfil completo se verificaron focalmente; no se afirma una corrida 893/893.
- La corrección de identidad del conflicto muestra institución, abreviatura,
  fecha y horario antiguos; el texto introductorio incluye Franco como contenido.
  Una regresión adicional Compose ejercita ambos diálogos con textos extensos,
  ambos temas y zoom 100/150/200: las cinco acciones son visibles, habilitadas y
  operables en cada configuración. No cambia ajustes globales del Samsung.

Recibos físicos oficiales, todos con identidad de paquetes comprobada y cero
omitidas. Rutas relativas a `.local-evidence/qa/`:

| Superficie | Resultado | Recibo |
|---|---:|---|
| Persistencia V2, asociados, retry y atomicidad | 23/23 | `20260916T075806.775679Z` |
| Recurrencias y exclusión del vecino sobrescrito | 14/14 | `20260916T075811.844981Z` |
| Archivos privados de notas, rollback y respuesta incierta | 5/5 | `20260916T075843.261644Z` |
| Observador temporal y primer plano | 12/12 | `20260916T075847.163289Z` |
| Limpieza, rangos, CAS y Franco | 9/9 | `20260916T080438.688813Z` |
| Tarjeta próxima jornada | 22/22 | `20260916T080812.223851Z` |
| Feriados/Atrás | 8/8 | `20260916T081250.435650Z` |
| Formulario y matriz de diálogos | 31/31 | `20260916T081817.715027Z` |
| Mes/adaptación a ventana | 5/5 | `20260916T081859.280840Z` |
| Carga integral, recreación, rotación, Atrás y capturas finales | 7/7 | `20260916T081908.680006Z` |

Los recibos de superficies sin cambios posteriores conservan su atribución al
APK indicado por cada manifiesto; no se reetiquetan como corridas del último APK.
Horas/Resumen se contrastaron con los snapshots y proyecciones existentes,
incluidos en las 888 JVM: se mantienen intervalos exactos, sin nuevo motor;
Franco+jornada conserva ambas condiciones. Los avisos asociados se incluyen en
CAS y eliminación; no se afirma una prueba nueva de entrega real de alarmas.

Identidad final comprobada en disco y Samsung SM-S938B/API 36, serial [serial QA en binding local privado]:

- QA `com.blackatsystems.miguardia.qa`:
  `c921cde5501c3253acdf0c83b32b60f5f51771193ab760c10aa9bd7aa6ffef79`.
- Runner app `com.blackatsystems.miguardia.qa.test`:
  `6cd9e31590cd042d9e384595051f9d270d743844eba406c382698ca596ae8cc1`.
- Runner base `com.blackatsystems.miguardia.core.database.test`:
  `0bb13fecc84f1d30b57cb529c2acd7d87eba057d6646011042a86b0032f44f01`.
- APK público `com.blackatsystems.miguardia`, antes y después:
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.

Sólo QA/runners fueron instalados e instrumentados. No se abrió, limpió ni
instrumentó la app pública; sus datos no fueron consultados. La igualdad del APK
no se presenta como una inspección del contenido privado. No se modificaron
fuente/densidad/zoom globales, ni hubo commit, push, tag, firma de distribución,
AAB, publicación o acceso al website.

### Entrega técnica y matriz humana pendiente

Validación posterior al último cambio de producto:
`stage3-final-candidate-validation.log`: 50 JVM del coordinador, QA, AndroidTest
y lintDebug verdes (22 advertencias, cero errores). Los recibos finales de
Compose 31, Adaptive 5 y Activity 7 corresponden al APK final identificado arriba.
Las diez clases físicas pertinentes suman 136 casos distintos verdes; no son
una corrida única ni una certificación de toda la aplicación.

Se conservaron 54 capturas del último candidato en
`.local-evidence/stage3-closure-verified-flow/` (18) y
`.local-evidence/stage3-closure-verified-creation/` (36). Manifiesto con SHA-256
de cada imagen y de los 22 archivos de producto intervenidos:
`.local-evidence/stage3-closure-final-manifest.json`. Sus hashes describen archivos
actuales completos, no atribuyen todo el checkout sucio a esta intervención.
Las capturas intermedias con título duplicado/aviso visible permanecen preservadas.

Inspección visual realizada por el agente: calendario instalado con todos los
días y tarjeta Próxima jornada; formulario de nota en 100/150/200, institución,
horario, diálogo de conflicto y limpieza, con muestras de ambos temas. Verificadas
jerarquía sin título duplicado, controles alcanzables mediante scroll propio del
formulario y ausencia de barra permanente o recorte del mes. Esto no equivale a
aceptación visual de Joaquín ni a inspección humana exhaustiva de las 54 imágenes.
`installed-calendar.png` acredita la QA abierta al finalizar, con fixture ficticia.

| Pendiente humano | Preparación y límite |
|---|---|
| Aceptación estética y recorridos | QA final abierta; revisar tarjeta, detalle→Editar, institución/horario/nota, selección múltiple y decisiones destructivas |
| Combinaciones completas de ventanas, zoom y orientación | Matrices automatizadas y recreación/rotación verdes; evaluar comodidad real y todos los extremos, sin cambiar ajustes globales |
| TalkBack | Semántica y acciones automatizadas cubiertas; no hubo nueva sesión TalkBack en este cierre |
| Selector OEM de imágenes | Archivos privados y metadata probados; selección/cancelación humana del picker y experiencia con teclado quedan pendientes |

No se repitió clima/red ni avisos del sistema por ritual. Sin aceptación humana
ni autorización para distribución. Resultado técnico: Stage 3 cerrado como
candidato para revisión humana, con los límites declarados y evidencia adversa
conservada; MAIN debe revisar esta entrega y Joaquín decidir sobre el producto.

Control documental final: `docs_check` verde, guía 39.932/40.000 caracteres;
catálogo 111 clases/21 grupos válido; `git diff --check` sin errores (aviso de
normalización LF/CRLF de `tools/qa.py`, sin transformación realizada). HEAD y
rama permanecen iguales, sin staging. Los hashes de las 22 fuentes de producto
coinciden con el manifiesto final. La comprobación inicial de documentación
marcó 40.045 caracteres; se compactó un resumen histórico de STATUS y se repitió
el control, sin retirar evidencia de auditoría.
