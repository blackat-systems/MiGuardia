# Handoff Stage 3 a MAIN — MiGuardia 3.0

Fecha: 2026-10-02  
Estado operativo: consultar STATUS. Stage 3.2 cerrado técnicamente como candidato. La entrega previa
no acredita la carga reordenada. Sin aceptación humana ni autorización de distribución.

## Stage 3.2 — entrega actual

Revisión MAIN posterior: `docs/audits/2026-10-02-overall-fluidez-integridad.md`.
Conserva esta entrega técnica como antecedente y prepara Stage 4 sin aceptar
Stage 3 ni atribuirle cobertura física o rendimiento no comprobados.

Contrato: `docs/prompts/STAGE_3_2_CARGA_ORDENADA_Y_BORRADOR_3_0.md`.
Delta y recibos: `docs/audits/2026-10-02-stage-3-2-carga-borrador.md`.

Se reordenó el formulario único: fechas cortas/expandibles, catálogo integrado,
Franco, puesto, retorno a selección, nota, guardar, limpiar y cancelar. El borrador
conserva catálogo/puesto/texto/referencias de imágenes incluso con selección vacía;
al continuar se revalidan catálogo y conflictos. Sin escritura por navegación ni
cambios en persistencia. Calendario mensual y Próxima jornada permanecen intactos.

Focales 68/68 y local completo fresco 900/900; build y lint verdes (0 errores,
24 advertencias). Samsung: formulario 34/34, Activity 8/8, feriados 8/8 y adaptación
5/5 mediante runner oficial. Recibos en auditoría. 71 capturas, 13 inspeccionadas;
temas claro/oscuro, zoom interno 100/150/200, recreación y rotación.

QA instalada y abierta en Calendario, SHA-256
`fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`.
APK público intacto por hash antes/después; no se accedió a datos personales.
Manifiesto: `.local-evidence/stage32-final-manifest.json`.
La auditoría conserva PNG de fixture malformada y recibo adverso 33/34: expectativa
exacta incompleta del puesto, corregida sin relajar aserciones ni tocar producto.

Delta propio: cuatro fuentes UI/coordinador, tres pruebas, catálogo QA y cinco
documentos. Sin cambios de Room/Copias/NextEvent/versionado/distribución. MAIN debe
comparar con `stage32-baseline-20261002`, no atribuirse todo el diff contra HEAD.
Pendientes humanos: diseño, TalkBack y picker OEM; no se demuestra cobertura de
todo dispositivo. Revisión sugerida: día → Editar día → institución/horario →
puesto/nota → volver a elegir días → continuar y comprobar borrador → guardar.
Probar Mostrar todos con cuatro fechas y Agregar horario en institución existente.
La aceptación de Stage 3 continúa siendo de Joaquín.

## Antecedente histórico — 2026-09-16

Los resultados y paquetes siguientes pertenecen al cierre anterior, no a Stage 3.2.

## Cierre de próxima jornada y carga unificada — entrega a MAIN

**Causa y decisión.** El flujo anterior separaba detalle/carga, sustituía por fecha
completa y guardaba notas fuera del lote. Se compuso el recorrido único y una
transacción con CAS de intervalos y asociados, sin migración Room ni cambio de
Copias. Sobrescribir conserva jornadas no superpuestas; Superponer conserva Franco
y registros previos. Limpiar muestra pérdidas, divide rangos y preserva vecinos.
Cancelar no crea jornadas/notas ni elimina catálogo confirmado. La tarjeta usa
fecha, contador temporal y próxima jornada elegible; se actualiza al primer plano.

**Delta propio.** UI de calendario/detalle y carga, NextEvent, planificación V2,
repositorios V2/batch, borrador de notas/archivos privados, integración MainActivity,
regresiones JVM/Android, catálogo y captura opt-in del runner. Inventario detallado
por archivo en la sección «Delta propio» de la auditoría; 22 fuentes de producto
con hashes en `.local-evidence/stage3-closure-final-manifest.json`. El resto del
checkout acumulado se preservó, sin staging, commit ni modificaciones a 2.0.

**Verificación.** 888/888 JVM en perfil completo; luego cinco casos nuevos y
regresiones afectadas focalmente: token/rangos 7/7 y coordinador 50/50. Build
completo inicial verde; QA/AndroidTest/lint posteriores al último cambio verdes
en `stage3-final-candidate-validation.log` (22 warnings, cero errores).
Diez clases físicas pertinentes: 136 casos distintos verdes, recibos detallados
en auditoría. Finales: formulario/diálogos 31/31 `20260916T081817.715027Z`,
adaptación 5/5 `20260916T081859.280840Z`, Activity 7/7
`20260916T081908.680006Z`. Las corridas previas no se reatribuyen al APK final.

**Evidencia adversa.** CLEAR 3/8: huella basada en identidad de contenedores;
corregida a valores canónicos con CAS intacto y cierre 9/9. Activity 6/7: fixture
usaba una plantilla incompatible con su horario; corregida sin relajar producto,
aserciones ni esperas. También corregidos KEEP que no omitía Francos y título
duplicado, detectado visualmente. Recibos fallidos y diagnósticos conservados.

**Instalación.** Samsung SM-S938B/API 36, exclusivamente QA y datos ficticios.
APK `com.blackatsystems.miguardia.qa`:
`c921cde5501c3253acdf0c83b32b60f5f51771193ab760c10aa9bd7aa6ffef79`.
Runner app `6cd9e31590cd042d9e384595051f9d270d743844eba406c382698ca596ae8cc1`.
APK público antes/después:
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`;
no fue abierto, instrumentado ni reemplazado y no se leyeron datos personales.
54 capturas finales en `stage3-closure-verified-flow/` y
`stage3-closure-verified-creation/`, bajo `.local-evidence/`, con manifiesto.

**Riesgos y próximos pasos.** Un cambio de catálogo tras un commit de respuesta
perdida puede impedir reconocer el éxito histórico; falla seguro sin duplicar.
La recuperación de archivos conserva ante duda de referencias, no borra a ciegas.
Pendientes humanos: estética, matriz completa de ventanas/orientación/zoom,
TalkBack y selector OEM de imágenes. No nueva prueba de entrega real de alarmas.
Joaquín: en la QA abierta, tocá un día→Editar día; elegí institución/horario y nota.
Luego usá pulsación larga para varios días y revisá Sobrescribir, Superponer,
Franco, Limpiar y Cancelar. MAIN debe contrastar los recibos y revisar el delta;
los verdes no sustituyen esa revisión ni la aceptación de Joaquín.

Auditoría: `docs/audits/2026-09-15-stage-3-seleccion-calendario-plegable.md`,
secciones «Cierre autorizado», «Hallazgos adversos» y «Entrega técnica».

## Puerta del cierre nuevo — 2026-09-16

Antecedente conservado: la propuesta siguiente fue autorizada expresamente por
Joaquín y está implementada; las frases «no se implementó» describen únicamente
el diagnóstico anterior a esa autorización, no el cursor actual.

Inspección confirmó reemplazo por fecha completa, rechazo de reemplazo desde
fecha vecina, borrado de Franco también al superponer y Sin definir no destructivo.
Notas/adjuntos se guardan separadamente y storageKey es único por adjunto.
La unidad solicitada necesita ampliar la transacción y sus expectativas CAS,
no sólo recomponer UI. Evidencia y alternativas: auditoría Stage 3, sección
«Cierre: puerta de persistencia». Se propone conservar esquema/Copias, componer
una operación atómica con preview de pérdidas y archivos independientes por nota;
viabilidad completa pendiente. No se implementó ese contrato sin la decisión
previa exigida por Joaquín. Tarjeta y formulario tampoco modificados aún.

Delta propio: contrato nuevo, índice, STATUS, esta sección y auditoría. Checkout
acumulado preservado; no Gradle, instalación, ADB ni uso del teléfono en este
diagnóstico. No hay recibos/capturas/hash nuevos; los anteriores siguen siendo
evidencia exclusivamente del candidato anterior. Próximo paso: decidir ampliación
de persistencia antes de implementar la carga, luego completar unidad y QA.

## OBJECTIVE

Hacer del Calendario una superficie mensual completa y accesible, con selección
múltiple contextual y acciones masivas revisables y atómicas, sin rediseñar el
detalle aceptado de Stage 2.

La ampliación autorizada mantiene ese candidato y recompone el detalle como
unidades independientes por jornada, conserva un acceso consciente al clima y
elimina el scroll vertical real de la pantalla principal.

## CHANGES

- Selección por pulsación larga y alternativa visible, con propósito aislado para
  uso general, carga manual y feriados.
- Mes completo de cuatro, cinco o seis filas; el plegado heredado ya no puede
  ocultar fechas.
- Sin barra inferior en modo normal. Contador, continuación y cancelación sólo
  existen durante la selección.
- Hoja compacta de cuatro acciones; preview por fecha para Franco, Sin definir y
  Retén; carga manual reutilizada con el mismo conjunto seleccionado.
- Repositorio Room transaccional con revalidación CAS del conjunto completo y
  rollback ante incompatibilidad o cambio concurrente.
- Un único árbitro decide el dueño efectivo de Atrás entre `GENERAL`,
  `MANUAL_LOAD` y `HOLIDAY`. El manejador general sólo participa si el propósito
  es `GENERAL`; carga manual y feriados completan sus propias salidas sin
  depender del orden de composición.
- Atrás en la primera selección manual cancela el flujo y el modo visual; desde
  etapas posteriores vuelve a elegir fechas y conserva el conjunto. En feriados
  cancela el flujo y también finaliza la selección del Calendario, evitando la
  restauración posterior.
- El detalle usa fecha humana completa, un único `Editar día`, el título
  `Jornadas del día` y una tarjeta por UUID de jornada, ordenada por inicio. Cada
  tarjeta contiene identidad, dirección, horario planificado, estado, Notas,
  Avisos, clima y horario real; las condiciones del día aparecen después y sólo
  si existen.
- El clima contextual permanece accionable si está apagado o todavía no tiene
  resumen. Abre el recorrido vigente sin habilitar, conceder permisos ni
  escribir por sí solo; al cerrarlo reaparece el mismo detalle.
- La pantalla principal ya no tiene `verticalScroll`, estado de scroll ni
  indicador. Las funciones desplazadas viven en el menú existente; las acciones
  masivas aparecen sólo en selección. Una ventana baja conserva el mes y omite
  la tarjeta opcional de próximo evento para reservar área útil.

## FILES

- Estado/UI: `CalendarUiState.kt`, `CalendarViewModel.kt`, `CalendarScreen.kt`,
  `CalendarDayDetail.kt`, `MiGuardiaApp.kt`, `MainActivity.kt` y
  `V2ShiftActualScreens.kt`.
- Integraciones sin controles duplicados: `V2ManualShiftLoadScreens.kt` y
  `ExceptionsScreens.kt`.
- Dominio/datos: `CalendarBatchRepository.kt`, `RoomCalendarBatchRepository.kt`,
  `AvailabilityWindowDao.kt` y `LocalDataStore.kt`.
- Regresiones: `CalendarInteractionStateTest.kt`,
  `CalendarBackPriorityTest.kt`, `CalendarViewModelStage2Test.kt`,
  `V2ManualShiftLoadCoordinatorTest.kt`, `ExceptionsCoordinatorTest.kt`,
  `CalendarCommonV2ComposeTest.kt`, `V2ManualShiftLoadActivityTest.kt`,
  `ExceptionsComposeTest.kt`, `DayOffPersistenceInstrumentedTest.kt` y ajustes
  de consumidores afectados. La ampliación agrega
  `CalendarStaticLayoutPolicyTest.kt` y actualiza
  `CalendarAdaptiveLayoutComposeTest.kt`, `CalendarCommonV2ComposeTest.kt` y
  `CalendarComposeTest.kt`.
- Evidencia durable: auditoría Stage 3, STATUS, índice de contratos y este handoff.

## DECISIONS

- La selección es un estado con propósito, no una suma de booleanos de pantallas.
- Cambiar de mes con fechas elegidas exige confirmación y limpia sólo la selección.
- La contracción es instantánea y determinista: evita movimiento innecesario y es
  compatible con reducción de animaciones.
- No se agregó tabla ni versión Room. La atomicidad se resuelve con una transacción
  coordinadora y un token calculado sobre las fuentes observadas.
- Un lote con al menos un conflicto no escribe nada. Las fechas sin cambio se
  informan, pero no generan escrituras.
- La prioridad de Atrás es una función explícita y testeable. Si dos flujos
  externos aparecen activos durante una transición, el propósito vigente decide
  primero; nunca se habilitan dos consumidores efectivos.
- El UUID sigue siendo la identidad funcional aunque dos tarjetas compartan
  objetivo y horario. El ordinal se conserva sólo para texto accesible.
- La presentación ya no depende del umbral de 1050 dp ni de
  `calendarCollapsed`: siempre usa las filas necesarias del mes.
- En ventanas bajas se omite el próximo evento, que es información secundaria.
  Cuando la carga manual ya confirmó fechas, su wizard reemplaza temporalmente
  la grilla y dispone de scroll propio; esto no vuelve desplazable la pantalla
  principal.

## CAUSE

- MAIN confirmó que el manejador general se habilitaba por estar en `EDIT` sin
  exigir el propósito `GENERAL`. La exclusión dependía indirectamente de otras
  superficies y del orden temporal de estados externos.
- En una transición manual o de feriados, el manejador general podía cerrar sólo
  el estado visual. El `LaunchedEffect` todavía veía activo el flujo externo y
  podía reabrir o restaurar la selección.
- Al explicitar la exclusividad apareció la segunda mitad del caso de feriados:
  su dueño específico cancelaba el flujo externo, pero no finalizaba el estado
  `EDIT`. Ahora ambas salidas ocurren juntas.
- El 5/6 de recreación pertenecía a la prueba. Tras cada rotación,
  `rotateAndVerifyDraft` usaba `onNodeWithText(POSITION).assertIsDisplayed()`.
  El valor restaurado seguía en `SavedStateHandle`, pero el nuevo wizard
  desplazable podía dejar el campo fuera del viewport. Una reproducción con el
  APK de prueba anterior falló exactamente en esa línea y no en restauración,
  persistencia ni guardado. La salida quedó en
  `.local-evidence/qa/20260916T035744.064486Z-diagnostic/diagnostic.txt`.
- La prueba ahora verifica directamente etapa, fechas, plantilla, puesto,
  propósito del Calendario y ausencia de jornadas antes de guardar. Para la
  comprobación visual localiza `v2-manual-position`, lo desplaza al viewport y
  valida su `EditableText`. No aumentó esperas ni eliminó recreación, paisaje,
  retrato o comprobaciones posteriores de snapshot.

## VALIDATION

- Regresión adversa preservada: antes del arreglo,
  `externalCancellationCannotHandManualOrHolidaySelectionToTheGeneralOwner`
  falló dentro de `CalendarBackPriorityTest` (3 ejecutadas, 1 fallo). Con el
  árbitro corregido, el conjunto focalizado pasó 58/58: 3 prioridad, 7 estado de
  Calendario, 40 carga manual y 8 feriados.
- Las pruebas Compose/Activity nuevas compilan y quedan catalogadas. Cubren Atrás
  real para diálogo y selección general, cancelación manual inicial, regreso
  manual desde elección de horario y cancelación de feriados, siempre sin
  escrituras. No se ejecutaron en dispositivo.
- Perfil local fresco: 872/872 JVM verdes, cero fallos u omitidas. Recibo:
  `.local-evidence/optimization/20260915T194933.145344Z/result.json`.
- Perfil build verde: `lintDebug`, APK Debug/QA/Release unsigned y runners
  AndroidTest de app/base. Recibo:
  `.local-evidence/optimization/20260915T195010.540649Z/result.json`; cero inputs
  cambiaron durante la ejecución.
- El primer preflight de `local` no ejecutó pruebas porque Java no estaba en
  `PATH`; quedó preservado en
  `.local-evidence/optimization/20260915T194920.506018Z/result.json`. La corrida
  efectiva usó el JBR explícito y pasó.
- Documentación, catálogo QA (109 clases, 19 grupos) y `git diff --check` se
  verifican al cierre de este handoff.
- No se ejecutó instrumentación ni recorrido manual en dispositivo.
- Focalizada de esta ampliación: política estática JVM verde y compilación de
  AndroidTest verde. Los recorridos Compose quedaron preparados, no ejecutados.
- Validación local completa nueva: 871/871 JVM, cero fallos, errores u omitidas;
  recibo `.local-evidence/optimization/20260916T020844.286657Z/result.json`.
- Perfil build nuevo, incluida compilación de runners instrumentados:
  `lintDebug`, APK Debug/QA/Release unsigned y runners AndroidTest de app/base
  verdes; recibo
  `.local-evidence/optimization/20260916T020947.934854Z/result.json`.
- Ambos recibos registran cero inputs cambiados durante su ejecución.
- Validación local final posterior a los hallazgos físicos: 871/871 JVM verdes,
  sin fallos, errores ni omitidas, en
  `.local-evidence/optimization/20260916T031219.234739Z/result.json`.
- Build final verde, con `lintDebug`, APK Debug/QA/Release unsigned y runners
  AndroidTest de app/base, en
  `.local-evidence/optimization/20260916T031337.785714Z/result.json`; registró
  seis artefactos y cero inputs cambiados.
- Samsung `SM-S938B`/API 36, paquete QA: Adaptive 4/4
  (`20260916T024344.054895Z`), Common 11/11
  (`20260916T024603.679957Z`), Calendar 6/6
  (`20260916T025134.471744Z`), Exceptions 8/8
  (`20260916T025413.575371Z`) y Weather 12/12
  (`20260916T031132.350479Z`). Los identificadores corresponden a carpetas bajo
  `.local-evidence/qa/`.
- `V2ManualShiftLoadActivityTest` pasó dos veces consecutivas 6/6 mediante el
  runner oficial: `.local-evidence/qa/20260916T035826.022573Z` y
  `.local-evidence/qa/20260916T035851.909662Z`. Ambos registran cero fallos,
  omitidas o pendientes, hashes coincidentes y metadatos de paquete sin cambios.
  Antes de esos cierres, el método aislado corregido pasó 1/1. Los recibos
  adversos previos, incluidos los cuatro 5/6, permanecen preservados.
- Hashes de las dos corridas finales: APK QA
  `08246b45605e009224c6af5a0b9d2fbef0d91515b4230b479548409ee435620b`,
  runner QA
  `5e678c8ed7d7683fee5111168640ca8e87dfbf1a82a860cf6c863787c72a4e8e`
  y fuente de la clase
  `beed4a63cc150baa556c8543273050bf78267b5d46441a8b812639c5050dcb93`.
  La app pública permaneció fuera de instrumentación, instalada con hash
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
- La instrumentación confirmó tres defectos y guio cambios acotados: controles
  redundantes que reducían la semana forzada, tags borrados por el orden de
  semántica y wizard manual posterior recortado debajo de la grilla. Todos
  quedaron regresionados en las clases afectadas.

## RISKS

- Falta completar observación humana de composición, pulsación larga nativa,
  navegación del sistema, temas y anuncios TalkBack.
- La evidencia local no acredita instalación, migración/restore real, firma,
  publicación ni aceptación del comportamiento visual.
- La composición mensual requiere revisión humana adicional en teléfonos
  chicos/grandes, retrato/paisaje y zoom 100/150/200. Ya no existe un corte que
  sustituya el mes por una semana.

## PENDING

- Revisión por MAIN de la causa demostrada, la regresión reforzada y los dos
  recibos oficiales consecutivos 6/6.
- Completar la matriz manual ya preparada: gesto largo, composición y acciones
  contextuales, temas, zoom, orientación y TalkBack. No hace falta repetir por
  rutina los recorridos automatizados verdes.

Matriz física adicional preparada: día sin jornadas/una/dos; dos jornadas con
mismo objetivo y mismo horario; orden fecha → editar → jornadas → condiciones;
Notas, imágenes, Avisos, Maps y horario real por UUID; clima habilitado,
cargando, caché/error, apagado, sin ubicación, fuera de ventana y no elegible;
Atrás desde Clima al mismo detalle; toque breve versus pulsación larga; viewport
sin scroll en teléfono chico/grande, retrato/paisaje y zoom 100/150/200; mes
completo de cuatro/cinco/seis filas; acciones contextuales y TalkBack.

## NEXT

MAIN debe revisar la corrección, esta ampliación y la evidencia Samsung antes de
reconsiderar la QA física restante. No declarar Stage 3 aceptado ni iniciar
commit, firma o publicación por inferencia.

## STAGE 3.1 — MES COMPLETO Y ACCIONES CONTEXTUALES

### Causa confirmada

- `calendarRequiresRestrictedWeek` imponía una semana cuando el alto era menor
  a 1050 dp y esa decisión se combinaba con `calendarCollapsed` persistido.
- La grilla mensual reservaba 42 celdas y cada celda exigía al menos 100 dp. La
  respuesta implementada había sido esconder el mes, no componerlo con el alto
  real disponible.
- `CalendarPrimaryActions` permanecía debajo de la grilla aun sin selección y
  las pruebas existentes exigían expresamente tanto la semana forzada como esa
  barra. Esa evidencia adversa se conserva; Stage 3.1 reemplaza esas decisiones.

### Corrección y regresiones

- El Calendario muestra siempre el mes completo y calcula sólo 28, 35 o 42
  posiciones según las cuatro, cinco o seis filas necesarias. La preferencia
  antigua de plegado no se aplica a la presentación.
- El toque breve abre el detalle y la pulsación larga nativa inicia selección.
  Contador, Continuar y Cancelar se componen sólo en ese modo y no reservan
  espacio fuera de él.
- Hoy, fotos, carga manual, alternativa accesible de selección y repetición se
  trasladaron al menú lateral existente. No se eliminó ninguna función.
- En paisaje/ventana baja se omite la tarjeta opcional de próximo evento. La
  primera captura física reveló que esa tarjeta comprimía la grilla hasta volver
  inoperables sus celdas; no se ocultaron fechas ni se reintrodujo la semana.
- Las regresiones cubren meses de 28/29/30/31 días y cuatro/cinco/seis filas,
  extremos contenidos y operables, zoom interno 100/150/200, navegación entre
  meses y años, recreación, preferencia heredada, toque, gesto largo,
  cancelación, menú, ausencia de barra normal, carga manual, feriados y único
  dueño de Atrás.

### Evidencia nueva

- Focalizadas JVM verdes: `CalendarStaticLayoutPolicyTest`,
  `CalendarBackPriorityTest` y `CalendarInteractionStateTest`; AndroidTest QA
  compila.
- Validación local completa: 872/872 JVM, cero fallos, errores u omitidas y cero
  inputs cambiados; recibo
  `.local-evidence/optimization/20260916T053300.438772Z/result.json`.
- Perfil build verde: `lintDebug`, APK Debug/QA/Release unsigned y runners
  AndroidTest de app/base, con cero inputs cambiados; recibo
  `.local-evidence/optimization/20260916T053434.709169Z/result.json`.
- Samsung `SM-S938B`/API 36, exclusivamente paquete QA: Adaptive 5/5
  (`.local-evidence/qa/20260916T052951.917822Z`), Common 11/11
  (`20260916T053007.875816Z`), Calendar 6/6 (`20260916T053020.833463Z`),
  Exceptions 8/8 (`20260916T052823.359552Z`), recreación y paisaje 7/7
  (`20260916T052720.651162Z`) y repetición 1/1
  (`20260916T052808.920409Z`).
- Carga manual cerró nuevamente con dos ejecuciones oficiales consecutivas 6/6:
  `.local-evidence/qa/20260916T053037.961412Z` y
  `.local-evidence/qa/20260916T053058.488376Z`.
- La última instalación verificada usa APK QA
  `7c4bc72b76f3d8492387297acf87b71e20042a26db6c9dd45621b09701c288f2`
  y runner
  `ba3cc9f19037e5e1b35959c67bb578f0173eab31edccd3e387dca9ecc8b08e2b`.
  Ambos hashes coinciden entre los archivos locales y `base.apk` instalados. El
  paquete público conservó
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`
  y no fue reemplazado ni instrumentado.
- Capturas: `calendar-portrait-final.png`, `calendar-landscape-final.png`,
  `calendar-selection-contextual.png`, `day-detail-short-tap.png` y
  `calendar-installed-final.png` bajo
  `.local-evidence/qa/stage-3-1-20260916-review/`. La captura adversa
  `calendar-landscape.png` preserva el recorte previo al arreglo.

### Riesgos y matriz humana pendiente

Queda pendiente que Joaquín evalúe visualmente legibilidad, densidad y
descubribilidad del menú en retrato/paisaje, zoom interno 100/150/200 y meses de
cuatro/cinco/seis filas; además, temas, navegación del sistema y anuncios
TalkBack. Las pruebas automatizadas y capturas no equivalen a aceptación visual.
Stage 3 no está aceptado ni autorizado para distribución.
