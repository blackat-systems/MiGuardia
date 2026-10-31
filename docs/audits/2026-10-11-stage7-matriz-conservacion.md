# Stage 7 — Matriz de conservación de Vigilia

Fecha: 2026-10-11. Responsable de integración: MAIN.

## Base y alcance de esta evidencia

- Contrato: [Stage 7](../prompts/STAGE_7_VIGILIA_INTEGRAL_SIN_REGRESIONES_3_0.md).
- Entrada: rama `codex/miguardia-3.0`, HEAD
  `932eaeb60c6b6e68975534788e3e6501c967b1ae` y candidato acumulado preservado.
  HEAD no describe por sí solo ese candidato.
- Sello anterior a los cambios: `.local-evidence/stage7/20261011-vigilia/entry/`.
  Para atribuir el delta se comparan los originales de `entry/files/`, no sólo
  `git diff` contra HEAD.
- Comparación visual anterior: corpus Stage 6 **nuevo**, enlazado por
  [su handoff](../HANDOFF_STAGE_6_RELEVAMIENTO_NUEVO_A_MAIN_3_0.md). Sus seis
  bases son 09 Calendario, 13 detalle, 19 carga con catálogo, 22 Resumen,
  23 Informes y 35 Ayuda. Las 41 capturas no constituyen cobertura exhaustiva.
- Esta matriz registra correspondencia observada en código y existencia de
  pruebas. No atribuye una ejecución anterior al candidato Stage 7. La
  compilación, la ejecución local, la instrumentación, la comparación visual
  y la aceptación humana se registran por separado en la auditoría del bloque.

En las tablas, las rutas de producción parten de
`app/src/main/java/com/blackatsystems/miguardia/`. Los nombres de pruebas Compose,
Activity e Instrumented corresponden a `app/src/androidTest/`, salvo indicación
JVM o de `core/database`. **P** significa ejecución Android/observación física
de Stage 7 pendiente; JVM y build se atribuyen a los recibos de la auditoría.
Que una prueba exista o compile no equivale a haberla ejecutado.

## Entradas y capacidades

La entrada anterior y la nueva se expresan en la misma celda con `→`. Se
conservan los dueños y callbacks existentes. MAIN controla la integración de
las familias indicadas como MAIN; el frente Carga/configuración es dueño del
delta de presentación de sus archivos, sin alterar coordinadores ni contratos.

| ID / responsable | Capacidad y entrada anterior → nueva | Estados y acciones conservados | Implementación concreta | Prueba existente / evidencia nueva |
|---|---|---|---|---|
| C01 / Carga-configuración | Arranque y rubro → misma puerta de arranque y cuatro rubros | Carga/error/reintento; Vigilancia privada, Policía, Enfermería y Medicina; elección expresa; ninguna configuración por defecto | `ui/worksetup/WorkSetupScreens.kt`, `WorkSetupStartupScreen`, `WorkSectorSelection` | `WorkSetupComposeTest`, `WorkSetupCoordinatorTest` JVM; P |
| C02 / Carga-configuración | Primer conjunto → mismos dos pasos | Lugar/reglas y tipo/horario; vocabulario sectorial, color elegido, horario nocturno y 24 h; ubicación consciente; guardar, volver, descarte; finalización con tres acciones | `ui/worksetup/WorkSetupScreens.kt`, `FirstWorkSetScreen`, `PlaceAndRulesForm`, `TypeAndTemplateForm`, `WorkSetupCompletion` | `WorkSetupComposeTest`, `ContextualLayoutComposeTest`; P |
| C03 / MAIN | Menú → misma jerarquía completa | Cinco destinos principales y catorce acciones; cierre antes de abrir; accesibilidad, estado seleccionado y ausencia de barra inferior permanente; bloqueo por superficie/borrador | `ui/MiGuardiaApp.kt`, `MainDestination`, `DrawerAction`, `openDrawerAction` | `NavigationDrawerComposeTest`, `SupportMenuComposeTest`, `UpdatesMenuComposeTest`; P |
| C04 / MAIN | Calendario/Hoy → Calendario/Hoy del menú | Mes entero, siete columnas y 4/5/6 filas; anterior/siguiente, consulta, carga/error/reintento; hoy, condiciones, jornadas múltiples, nocturnidad y minutos; sin scroll mensual | `ui/CalendarScreen.kt`, `ui/calendar/CalendarPresentation.kt`, `ui/MiGuardiaApp.kt` | `CalendarAdaptiveLayoutComposeTest`, `CalendarComposeTest`, `Stage4CalendarMatrixTest`, `CalendarStaticLayoutPolicyTest` JVM; P |
| C05 / MAIN | Próxima jornada → misma tarjeta cuando corresponde | Hoy/mañana/fecha, identidad/horario, cuenta real días/horas/minutos, en curso/sin próxima; actualizaciones temporales/primer plano, omisión en ventana baja | `ui/nextevent/NextEventCard.kt`, `NextEventViewModel.kt`, `NextEventObserver.kt` | `NextEventComposeTest`, pruebas de proyección; P |
| C06 / MAIN | Toque breve de fecha → mismo detalle | Fecha humana, único Editar día, Otras opciones; tarjetas por UUID, color/institución/dirección/Maps/horario/estado/puesto; Notas, Avisos, Clima, horario real; condiciones sólo si existen | `ui/CalendarDayDetail.kt`, `DayDetailSheet`, `ShiftDetail`, `DayConditionCard` | `CalendarCommonV2ComposeTest`, `V2ShiftActualComposeTest`; P |
| C07 / MAIN + Carga-configuración | Pulsación larga o Seleccionar del menú → mismas entradas | Selección múltiple, contador/acciones contextuales, confirmar/reanudar/limpiar; propósito GENERAL/MANUAL_LOAD/HOLIDAY; Atrás con un dueño efectivo | `ui/CalendarScreen.kt`, `ui/MiGuardiaApp.kt`, `calendarSelectionBackOwner` | `CalendarBackPriorityTest` JVM, `CalendarCommonV2ComposeTest`, `V2ManualShiftLoadActivityTest`; P |
| C08 / Carga-configuración | Cargar del menú o Editar día → misma carga unificada | Fechas hasta tres resumidas y resto expandible ordenado con día; catálogo, institución/servicio, horario, Franco, puesto, nota/imágenes; volver/guardar/revisar/limpiar/cancelar | `ui/management/UnifiedManualLoadForm.kt`, `V2ManualShiftLoadScreens.kt` | `V2ManualShiftLoadComposeTest`, `V2ManualShiftLoadActivityTest`; P |
| C09 / Carga-configuración | Catálogo en carga → mismo catálogo y altas contextuales | Crear institución y primer horario; elegir/agregar horario; retener servicio, horario y borrador al volver, vaciar/reseleccionar y recrear; lote cancelado no contamina otro | `UnifiedManualLoadForm.kt`, `ui/worksetup/WorkSetupScreens.kt`, handoffs existentes en `MiGuardiaApp.kt` | `V2ManualShiftLoadActivityTest.contextualPlaceAndTemplateSurviveRecreationThenConfirmExactPairs`, `reselectingDatesPreservesCompleteDraftAcrossEmptySelectionAndRecreationThenSavesOnlyFinalDates`; P |
| C10 / Carga-configuración | Guardar/Limpiar con conflictos → mismas revisiones y confirmaciones | Preview de pérdidas; Sobrescribir, Superponer, sólo días libres, cancelar; advertencias y extensión consciente de configuración; CAS, identidad, atomicidad y cancelación sin escritura permanecen en sus dueños | `V2ManualShiftLoadScreens.kt`, `CalendarScreen.kt` para preview masivo; coordinadores/base sin rediseño | `V2ManualShiftLoadActivityTest.unifiedSingleAndMultipleLoadOverwriteOverlayClearAndCancelPreserveExactData`, coordinadores JVM y pruebas transaccionales; P |
| C11 / Carga-configuración | Opciones/corrección de jornada → mismas entradas por UUID | Inspección/carga/error; plantilla histórica/activa, formulario, warnings/revisión; editar sólo ocurrencia o cambiar serie; eliminar sólo jornada/finalizar serie; descartar/conservar | `ui/management/V2ShiftEditScreens.kt`; handoffs en `MiGuardiaApp.kt` | `V2ShiftEditComposeTest`, `V2ShiftEditActivityTest`, `V2ShiftEditCoordinatorTest` JVM; P |
| C12 / Carga-configuración | Repetir y Repeticiones guardadas → mismas entradas | Lista/detalle, patrón semanal/avanzado, corte, inicio/fin, horario/color/puesto, conflictos, preview/confirmación; pasado y excepciones preservados | `ui/management/V2RecurringPlanScreens.kt` | `V2RecurringPlanComposeTest`, `V2RecurringPlanActivityTest`, `V2RecurringPlanCoordinatorTest` JVM; P |
| C13 / Carga-configuración | Mi forma de trabajar → mismo overview/catálogo | Agregar lugar/horario; opciones avanzadas conservadas: repeticiones, tipos de extras, horas/meta, disponibilidad, ubicación para Clima; padre/scroll conservados al volver | `ui/worksetup/WorkSetupScreens.kt`, `WorkSetupOverview`, `AdditionalPlaceScreen`, `AdditionalTemplateScreen`; SaveableStateProvider en `MiGuardiaApp.kt` | `WorkSetupComposeTest`, `ContextualLayoutComposeTest`, `Stage4FlowsActivityTest`; nueva regresión de selección abajo; P |
| C14 / MAIN | Tus horas y tu meta → mismo destino secundario | Carga/error/contenido, referencia, valor por período, revisión/confirmaciones, metas, extras independientes y desgloses; no falsos ceros ni conversión automática en extra | `ui/hours/HoursAndExtrasScreens.kt` | `HoursAndExtrasComposeTest`, `HoursAndExtrasActivityTest`, `HoursReferenceDraftTest` JVM; P |
| C15 / Carga-configuración | Registrar/corregir horario real y Tipos de extras → mismas entradas | Identidad, horas reales/fragmentos, clasificación/revisión, guardar/cancelar/retornar al planificado; catálogo, editar/archivar/reactivar; borrador no recuperable explícito | `ui/management/V2ShiftActualScreens.kt` | `V2ShiftActualComposeTest`, `V2ShiftActualActivityTest`, `V2ShiftActualCoordinatorTest` JVM, `HoursExtraRestorationRaceInstrumentedTest`; P |
| C16 / Carga-configuración | Disponibilidad avanzada o Retén contextual → mismas entradas | Overview/configuración/revisión, tres nombres y No uso, fecha de vigencia; ventana/revisión, corregir/eliminar/descarte; Retén contextual no cambia configuración laboral; resultados separados de trabajo | `ui/availability/AvailabilityScreens.kt`, handoff Retén de `MiGuardiaApp.kt` | `AvailabilityComposeTest`, `AvailabilityActivityTest`, `AvailabilityCoordinatorTest` JVM; nueva regresión de selección abajo; P |
| C17 / Carga-configuración | Franco desde carga/acciones del día → misma carga y confirmación | Marcar/quitar, conflictos y preview, cancelar; no crear jornadas ni extras por apariencia | `V2ManualShiftLoadScreens.kt`, `UnifiedManualLoadForm.kt`; proyección de condiciones en `CalendarDayDetail.kt` | `V2ManualShiftLoadComposeTest`, `CalendarViewModelStage2Test` JVM; P |
| C18 / MAIN | Condiciones del día → mismas acciones existentes | Situaciones de jornada, motivos/notificación/evidencia privada opcional; carpeta médica por rango, nota privada, editar/eliminar/confirmación; sin bloques vacíos ni imágenes clínicas | `ui/CalendarDayDetail.kt`, `ShiftSituationDialog`, `MedicalLeaveDialog`; `Stage2DayActions` sin rediseño | `CalendarViewModelStage2Test` JVM, pruebas Stage 2 de dominio/base; P |
| C19 / Carga-configuración | Feriados y Vacaciones del menú → mismos destinos | Feriados: selección calendario/nombre/editar/conflicto Reemplazar-Conservar/cancelar/borrar. Vacaciones: lista, rango inclusivo, preview/guardar/editar/borrar/descarte y date picker propio | `ui/exceptions/ExceptionsScreens.kt`, `ui/vacation/VacationScreens.kt` | `ExceptionsComposeTest`, `VacationComposeTest`, `VacationViewModelInstrumentedTest`, `core/database:VacationPersistenceInstrumentedTest`; P |
| C20 / MAIN | Resumen → mismo overview, menú y detalle | Mes, métricas exactas/familias, intro/personalización/orden/visibilidad; desglose sin datos privados; retorno con scroll; carga/vacío/error/reintentos | `ui/summary/SummaryScreen.kt` | `SummaryComposeTest`, `SummaryActivityTest`, `SummaryViewModelTest` JVM; `summaryAndContributionKeepLongLabelsAndAmountsWholeAtEveryThemeAndZoom` define la regresión nueva de distribución; ejecución Android P |
| C21 / MAIN | Informes desde Resumen → mismo destino secundario | PDF/XLSX, mes, inclusiones privadas apagadas, fotos sólo PDF, selección de fotos, doble confirmación médica, revisión/generación/listo/guardar/compartir; carga/vacío/error/retorno | `reports/ReportsScreen.kt`; ViewModel/exportadores preservan contrato | `reports/ReportsComposeTest`, `ReportsViewModelInstrumentedTest`, `ReportDestinationWriterInstrumentedTest`; `medicalInclusionKeepsEveryWordSeparatedFromOneAccessibleSwitch` define la regresión nueva V02; ejecución Android P |
| C22 / MAIN | Fotos del cronograma del menú → misma lista mensual/visor | Selección/agregado, miniaturas/detalle/visor, quitar/confirmar/cancelar; contenido/vacío/carga/error; Photo Picker y retorno consciente | `ui/photos/PhotosScreens.kt`, `PhotosSurface.LIST/VIEWER` | `PhotosComposeTest`, pruebas de archivos/decodificación y retorno OEM pertinentes; P |
| C23 / Carga-configuración + MAIN | Notas por jornada e imágenes → misma entrada por UUID y visores | Nota nueva/editar/guardar/cancelar/borrar; imágenes privadas, agregar/ver/quitar/confirmar; vacío/poblado/error; no perder lote/identidad al restaurar | `ui/exceptions/ExceptionsScreens.kt`, `ui/photos/PrivateNoteImage.kt`, `ui/management/UnifiedManualLoadForm.kt`; almacenamiento fuera del rediseño | `ExceptionsComposeTest`, `ManualLoadNotesInstrumentedTest`, `Stage4NoteRestoreConcurrencyTest`; P |
| C24 / MAIN | Avisos globales/por jornada → mismas entradas | Preferencias, override/restaurables, acceso/permisos, carga/guardado/error/mensaje, retorno desde ajustes; alarmas/proveedor sin cambios estéticos | `ui/notifications/NotificationScreens.kt` | `NotificationComposeTest`, `NotificationPreferencesInstrumentedTest`, `Stage5NotificationOemTest`; P |
| C25 / MAIN | Clima global/contextual y ubicación → mismas entradas | Habilitación consciente/unidades, ubicación puntual/quitar, vigencia/resumen/horas/error/reintento; créditos y enlace; retorno/cerrar sin intercepción | `ui/weather/WeatherScreens.kt`, `ui/components/ObjectiveLocationCapture.kt`, `CalendarDayDetail.kt` | `WeatherComposeTest`, `WeatherCreditsSessionInstrumentedTest`, `WeatherViewModelInstrumentedTest`, `WorkSetupComposeTest`; P |
| C26 / MAIN | Widget del menú/configuración launcher → mismas entradas y contenido propio | Instancias, modos/privacidad/Clima, guardar/cancelar, configuración protegida, compacto/expandido, cronómetro/filas/navegación/créditos, carga/error/recuperación; no rediseñar launcher | `ui/widget/WidgetScreens.kt`, `widget/WidgetConfigurationActivity.kt`, `WidgetRemoteViewsRenderer.kt`, `WidgetRenderModel.kt`, XML propios | `WidgetComposeTest`, `WidgetConfigurationActivityTest`, `WidgetRemoteViewsInstrumentedTest`, `WidgetRenderModelTest` JVM; P |
| C27 / MAIN | Copias del menú/recuperación de arranque → mismas entradas | Cifrado inicial/contraseña, advertencia sin cifrar, fotos/preferencias, SAF/cancelar, lectura/validación/contraseña/preview, Fusionar-Reemplazar/conflictos/segunda confirmación, aplicación/recuperación/éxito/error; aplicación no cancelable | `backup/BackupScreen.kt`; coordinadores/cifrado/formato preservados | `backup/BackupComposeTest`, `BackupRecoveryInstrumentedTest`, `BackupDocumentPublicationInstrumentedTest`, `BackupContractTest` dominio; P |
| C28 / MAIN | Bloqueo → misma puerta y configuración | Gate cerrado no compone datos sensibles; cargar/error/reparar/desbloquear, cuatro tiempos, activar/cambiar/desactivar/bloquear ahora; regreso al borrador; biometric/FLAG_SECURE preservados | `security/AccessLockScreens.kt`, `MainActivity.kt`, `WidgetConfigurationActivity.kt` | `security/AccessLockComposeTest`, `AccessLockManifestInstrumentedTest`, `AccessLockCoordinatorTest` JVM; P |
| C29 / MAIN | Apariencia → mismo destino | Claro/oscuro/Sistema, oscuro inicial sin pisar elección; letra Android y TalkBack sin controles extra por ADR 0043. Zoom retirado expresamente; dato legado preservado sólo en Copias | `ui/AppearanceScreen.kt`, `ui/theme/Theme.kt`, `AppThemeMode.kt`; legado en Gateway/`AppZoom.kt` | `AppearanceComposeTest`, `AppThemeModeTest`, `AppZoomTest` legado, `PortablePreferencesGatewayInstrumentedTest`, `VigiliaThemeComposeTest`; P |
| C30 / MAIN | Ayuda/tour → mismas entradas e introducción | Carga/error/reintento, introducción, continuar/omitir/repetir/salir, ocho pasos/anclas, overlay/restauración sin reentrada | `ui/help/HelpScreens.kt`, `HelpTourOverlay`, `MiGuardiaApp.kt` | `HelpComposeTest`, `HelpTourActivityTest`, `HelpCoordinatorTest` JVM, `core/database:Stage5HelpProcessRestorationInstrumentedTest`; P |
| C31 / MAIN | Actualizaciones del menú → mismo destino consciente | Consulta, sin versión/actual/disponible/incompatible, descarga/verificación, selector/guardado/listo/cancelado/interrumpido/error/reintento con espera; acceso y retorno | `updates/UpdatesScreen.kt`, `MainActivity.kt` | `UpdatesMenuComposeTest`, `UpdatesReturnComposeTest`, `updates/UpdatesScreenTest`, `UpdatesActivityTest`, `UpdatesOemSafActivityTest`, `UpdatesViewModelTest` JVM; P |
| C32 / MAIN | Aporte voluntario/soporte → mismo enlace consciente | Cerrar menú antes de abrir, control de acceso vigente, error al abrir y retorno del navegador. No hay pantalla Compose propia de aporte que deba inventarse | `support/SupportLink.kt`, `SupportMenuAction`, `MainActivity.openSupportMiGuardia` | `SupportMenuComposeTest`, `SupportMenuActivityTest`; P |

## Garantías cruzadas y fronteras

- ADR [0043](../adr/0043-letra-nativa-android-sin-zoom-interno.md) sustituye el zoom
  de ADR 0042: toda la app hereda letra/densidad Android, sin cambiar ajustes
  globales. Se conservan mes completo, siete columnas, objetivos contenidos de
  48 dp, privacidad y navegación. Matriz QA con letra nativa simulada 1/1,5/2.
- Guardar sólo las fechas finales, cancelar sin escribir, transacciones/CAS,
  identidad del borrador, compatibilidad de Copias y Room V8 permanecen en
  coordinadores/base. El delta de presentación no debe reimplementar esos
  contratos. Prueba especialmente pertinente:
  `V2ReadOnlySurfacesInstrumentedTest.calendarSummaryTodayCardAndWidgetQueriesLeaveAllApplicationTablesUnchanged`.
- Photo Picker, SAF, Maps/navegador, teclado, permisos/biometría y launcher son
  superficies del sistema: conservar transición, cancelación, retorno y estado
  propio; su apariencia no pertenece a esta matriz de cambios.
- Los colores ARGB de instituciones/horarios son datos: se conservan en
  catálogos, detalle, próxima jornada, extras y Widget; no se sustituyen por
  acentos del tema. Se mantiene texto/selección semántica además del color.
- Widget RemoteViews usa paleta propia (`WidgetRenderModel.kt`), drawables y
  preview XML. Un cambio de `Theme.kt` por sí solo no cubre C26. El renderer
  compara el fondo oscuro con su constante para elegir drawable: deben
  mantenerse sincronizados.
- El corpus anterior sirve para comparar estética, no para acreditar
  guardado/CAS/privacidad, TalkBack, uso OEM, comprensión de acciones o
  rendimiento de otro APK.

## Delta de Carga/configuración y regresiones nuevas

Los nombres y recibos de esta sección corresponden a la entrega original del
especialista. ADR 0043 migró sus escenarios de zoom a letra Android simulada;
la ejecución nueva se atribuye sólo en la auditoría de MAIN, sin reasignar verdes.

El frente de Carga/configuración mantiene las entradas de C01/C02/C08–C13,
C15–C17/C19/C23. Cambia sólo su presentación: selección suave con
`selection/onSelection`, bordes funcionales en opciones disponibles,
jerarquía semibold y colores destructivos heredados por los botones, incluidos
sus estados deshabilitados. La selección de tipos/lugares usa RadioButton
local de Compose, conservando el único callback de la fila y su semántica;
sus objetivos tienen mínimo 48 dp del escalado vigente para formularios.

Las selecciones de institución desplegada sólo expresan estado de UI; no
cambian la plantilla elegida, la identidad de operación ni la persistencia.
Ningún texto laboral, advertencia o confirmación sensible se retira.

Regresiones agregadas en Stage 7, **pendientes de ejecución Android autorizada**:

- `WorkSetupComposeTest.templateTypeSelectionKeepsWholeLabelsAndContainedTargetsAcrossThemesAndZooms`:
  etiqueta laboral larga completa, sin overflow, dentro de su fila; objetivo
  táctil contenido de al menos 48 dp; selección explícita y un callback por
  toque en ambos temas y los tres zooms.
- `AvailabilityComposeTest.configurationSelectionIsExplicitAndKeepsOneActionAcrossThemesAndZooms`:
  selección accesible independiente del color y check visible, un callback por
  toque y fecha de vigencia intacta en ambos temas y los tres zooms.

V01/V02 tienen regresiones nuevas en
`HoursAndExtrasComposeTest.pendingHoursKeepTheWholeAmountAndUnitSeparatedFromTheCompleteLabel`
y `ReportsComposeTest.medicalInclusionKeepsEveryWordSeparatedFromOneAccessibleSwitch`.
Miden contenido renderizado íntegro, cifras/unidades, límites y separación
real de 12 dp en claro/oscuro y los tres zooms; su ejecución Android sigue
pendiente. `SummaryComposeTest.summaryAndContributionKeepLongLabelsAndAmountsWholeAtEveryThemeAndZoom`
cubre el caso equivalente en Resumen/desgloses. Los tests previos de
navegación/zoom no midieron esos defectos. V04 conserva
el carácter de hipótesis de descubribilidad hasta revisión humana. La nueva
paleta requiere contraste sobre superficies reales y estados, incluidos
gradientes, celdas seleccionadas y controles; los pares planos anteriores no
agotan esa comprobación.

## Estado de comprobación de la matriz

VERIFICADO por inspección: correspondencia de todas las familias obligatorias
con rutas/hosts/estados y pruebas existentes; no falta una familia de producto
en esta matriz. El diff propio de Carga/configuración se contrasta con el sello
de entrada para distinguir modificaciones acumuladas de Stage 7.

Autoauditoría del frente: en los nueve archivos productivos propios se
contrastaron los usos de `actions.*` y los literales con sus originales sellados;
no se retiró ningún callback ni literal anterior. `git diff --check` del alcance
pasó. Esto no demuestra equivalencia de ejecución: se complementa con la
revisión del diff y los recibos que produzca MAIN.

VERIFICADO localmente por MAIN: 930/930 JVM frescos y perfil build verde,
con inputs coincidentes y sin cambios durante las corridas. Las nueve clases
AndroidTest actualizadas compilan en el runner app; el runner base se reutiliza
con insumos intactos. Revisión independiente del delta y de las 32 capacidades
completada; recibos, hashes y límites en la
[auditoría Stage 7](2026-10-11-stage7-vigilia-integral.md).

PENDIENTE: instrumentación Stage 7 autorizada, comparación antes/después del APK real,
TalkBack/teclado/selectores/permisos/bloqueo, rendimiento pertinente y revisión
visual humana. La matriz no declara terminado ni aceptado el stage y no
modifica el estado operativo, que vive sólo en [STATUS](../STATUS.md).
