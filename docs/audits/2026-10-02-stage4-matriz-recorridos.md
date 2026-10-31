# Stage 4 — Matriz de recorridos y evidencia pendiente

Fecha: 2026-10-02. Preparación para QA sobre el checkout acumulado de
`codex/miguardia-3.0`, base Git `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
El estado operativo y la autorización continúan en [STATUS](../STATUS.md).
Requisitos: [contrato Stage 4](../prompts/STAGE_4_FLUIDEZ_E_INTEGRIDAD_3_0.md).

## Cómo interpretar esta matriz

Actualización posterior: [QA física MAIN](2026-10-02-stage4-qa-main.md) registra
13 clases/94 métodos, 89 verdes y 5 fallidos. C01/R01 falla en 32/48 combinaciones;
J01 falla en HELP; R02 incompleto. W01/H01/H03/A01/RP01 tienen ensayo de regreso
con recreación, R03/R04 y las clases de notas/archivos cuentan con ejecución
dirigida; no se completan automáticamente sus subcasos OEM o humanos.
Las tablas siguientes conservan el plan original. Para resultados nuevos y sus
límites usar los recibos de esa revisión, sin convertir una fila entera en verde.

Se inspeccionaron el código y el inventario de pruebas pertinentes para preparar estos
recorridos. **Esta preparación no ejecutó instrumentación ni acciones en el
teléfono.** Las filas empiezan en `PENDIENTE`: nombrar una prueba existente o
compilada no acredita su ejecución sobre el candidato nuevo. MAIN debe incorporar
los recibos nuevos y sus límites antes de cambiar un estado.

- `EJECUTADA`: existe recibo identificable del candidato y se cumplieron las
  aserciones indicadas. Acredita sólo ese alcance; no significa aceptación humana.
- `FALLÓ`: se ejecutó y quedó una aserción incumplida, error o restricción que
  impide satisfacer el criterio. Conservar recibo, caso y artefacto adversos.
- `PENDIENTE`: no se ejecutó, faltó una combinación, faltó identidad/frescura de
  insumos o el escenario requiere una intervención todavía no realizada.

Las tablas A y B comparten ID: juntas forman una fila completa por superficie.
A fija entrada, caminos y salida; B fija datos, errores, pruebas y estado.
La columna de pruebas describe **cobertura prevista por inspección**, no verdes.
Una prueba con callbacks o repositorios dobles no demuestra persistencia real;
una prueba Activity no demuestra lectura humana ni navegación OEM.

La [auditoría original](2026-10-02-overall-fluidez-integridad.md) conserva F01–F07,
900 JVM y navegación física parcial del candidato anterior. La
[evidencia Stage 3.2](2026-10-02-stage-3-2-carga-borrador.md) conserva sus propios
recibos. Ninguno se reasigna al APK Stage 4 ni completa estas filas. Los resultados locales nuevos se registran al final; no cambian por sí solos una fila física a EJECUTADA.

## Identidad que debe acompañar cada ejecución

| Campo | Valor o condición de completitud |
|---|---|
| Entrada preservada | `.local-evidence/stage4/entry/manifest.json`, `received.patch` y APK de entrada. El SHA Git solo no incluye Stage 3.2. |
| Candidato | Registrar SHA-256 de QA y runner realmente instalados, junto con manifiesto de insumos del build. La auditoría del candidato identifica el build final; instalación todavía pendiente. |
| Base/runner de datos | Registrar APK/runner de `core/database` si se usa `database.test`; no atribuir esos casos al runner de app. |
| Entorno | Dispositivo, API, variante, orientación, tema y zoom interno. No consultar ni cambiar `font_scale` o tamaño de pantalla de Android. |
| Fixture | Cantidades exactas de jornadas, notas, adjuntos, repeticiones y fechas. Datos exclusivamente ficticios. |
| Resultado | ID de fila, método/caso, `EJECUTADA`/`FALLÓ`/`PENDIENTE`, recibo, captura cuando aplica y limitación. Si es parcial, desglosar los subcasos. |
| Conservación | Comparar snapshot lógico y referencias/hashes de archivos antes/después donde la fila exige ausencia de escritura o rollback. |
| Foco | Ante pérdida de foco, detener interacciones/capturas. Descartar capturas ajenas; no conservar datos de otras apps. |

## A. Caminos y salidas por superficie

| ID / superficie | Entrada | Acción principal | Alternativa existente | Atrás y cancelar esperados |
|---|---|---|---|---|
| M01 Menú | Abrir menú desde un destino habilitado | Elegir Calendario, Resumen o una acción | Ayuda, Bloqueo y Apariencia; desplazarse entre grupos | Atrás cierra menú. Elegir una acción cierra el menú antes de invocar al dueño; un borrador o puerta activa no habilita accesos duplicados. |
| C01 Calendario mensual | Inicio, menú Calendario o regreso de una superficie | Consultar mes completo y tocar un día | Flechas/gesto horizontal; menú Ir a hoy | Atrás no guarda datos. Sin scroll vertical principal, semana forzada ni barra permanente. Mes anterior/actual conservan sus fechas reales. |
| C02 Detalle del día | Toque breve en una fecha | Consultar tarjetas de cada jornada y condición del día | Editar día; notas, avisos, clima y opciones disponibles de cada jornada | Cerrar/Atrás retorna al Calendario. No mezcla dos jornadas iguales visualmente ni convierte consulta en edición. |
| C03 Selección de días | Pulsación larga nativa o menú Seleccionar días | Marcar una/varias fechas y elegir operación | Quitar marcas o revisar selección | Un solo dueño de Atrás: diálogo → operación/formulario → selección. Cancelar general elimina sólo selección/borrador confirmado; no reentra. |
| L01 Carga unificada | Menú Cargar jornadas o francos; entrada del detalle | Elegir fechas, institución/horario o Franco; guardar | Puesto, nota, imágenes; limpiar borrador; revisión opcional | Volver a fechas conserva borrador completo, incluso tras vaciar selección. Cancelar requiere descarte cuando corresponde; guardando no admite segundo envío. |
| L02 Catálogo durante carga | Instituciones dentro de L01 | Elegir institución y horario | Crear institución; agregar horario a una existente | Cancelar creación vuelve a la carga sin guardar borrador incompleto. Lo que se guardó expresamente en catálogo permanece aunque se cancele la carga. |
| L03 Revisión, conflictos y lote | Guardar/revisar L01 o aplicar operación masiva | Confirmar lote exacto | Sobrescribir por intervalo; Superponer conscientemente; conservar existentes cuando esté ofrecido | Cancelar conflicto/advertencia vuelve al borrador. Atrás no acepta avisos ni descarta datos por sí solo. |
| W01 Mi forma de trabajar | Menú Tu trabajo; primera configuración | Crear primer lugar/tipo/horario o consultar configuración existente | Otro lugar/horario; Opciones avanzadas | Atrás/descarte protege borrador. Hijos Horas, disponibilidad, repetición y tipos de extra retornan al padre real; entrada directa no inventa padre. |
| H01 Horas y meta | W01 → Opciones avanzadas → Tus horas y tu meta | Consultar avance o definir referencia explícita por fecha | Valor por período, corrección y reinicio explícito | Revisión vuelve al editor; cancelar no altera referencia. Cerrar retorna al origen; recreación conserva contexto. |
| H02 Horario real y extras | Tarjeta de jornada; entrada contextual correspondiente | Registrar/corregir horario real y fragmentos extra | Volver al planificado; extra independiente cuando corresponda | Advertencia/descarte consciente. Cancelar restaura consulta sin escribir; volver al planificado es una mutación confirmada, no navegación. |
| H03 Tipos de horas extra | W01 → Opciones avanzadas; catálogo desde flujo de horas | Crear/corregir clase y sus respuestas expresas | Usar clase existente; archivar/reactivar según acciones ofrecidas | Cerrar vuelve al dueño real, incluidas entradas directas. Cancelar edición no borra una clase previamente guardada. |
| A01 Configuración de disponibilidad | W01 → Opciones avanzadas → Disponibilidad | Elegir nombre/regla y fecha efectiva; revisar | No usar disponibilidad | Volver desde revisión conserva editor; cerrar con cambios solicita descarte. Regreso al W01 que abrió el flujo. |
| A02 Retén del día | Detalle → Editar día → Agregar disponibilidad de retén | Guardar ventana contextual | Corregir/eliminar una ventana existente | Cancelar no cambia ventana ni configuración laboral. Cerrar vuelve al día de origen, no a una fecha arbitraria. |
| RP01 Repeticiones | Menú Repetir jornadas o W01 → Repeticiones guardadas | Crear patrón, revisar fechas y guardar | Consultar plan; cambiar/cortar futuro; una sola fecha | Atrás conserva la etapa correspondiente; descartar borrador no crea jornadas. Cerrar vuelve al padre real o Calendario si fue directo. |
| S01 Situaciones y carpeta médica | Detalle → opciones del día/jornada | Registrar situación o carpeta médica | Corregir/restaurar situación; eliminar rango confirmado | Cancelar diálogo no escribe. COMPLETED es derivado; no se ofrece como modificación arbitraria. |
| E01 Feriados | Menú Tu trabajo → Feriados | Elegir fechas en la grilla principal y guardar | Editar/eliminar; conservar o reemplazar ante conflicto | Atrás cancela selección de feriados sin restaurarla. Cerrar borrador requiere descarte; volver a Feriados conserva el contexto previsto. |
| V01 Vacaciones | Menú Tu trabajo → Vacaciones | Crear rango inclusivo | Editar/eliminar confirmado | Volver/cancelar conserva lo guardado; un borrador se descarta conscientemente. Coincidencias de jornadas siguen consultables. |
| N01 Notas e imágenes de jornada | Tarjeta de una jornada concreta; nota de L01 | Guardar texto e imágenes ordenadas | Ver miniatura; quitar imagen del borrador; editar nota | Cancelar selector no agrega nada; cancelar borrador no borra archivos confirmados. Contraer nota conserva texto/imágenes. |
| P01 Fotos del mes | Menú Acciones del Calendario → Fotos del mes | Agregar y consultar cronograma del mes visible | Visor, zoom/pan, asignación y acciones individuales | Cerrar lista vuelve al origen; Volver del visor retorna al listado. Cancelar picker/cámara o eliminación no sustituye otra foto. |
| U01 Resumen | Menú Resumen | Leer cifras y abrir qué incluye cada una | Mes anterior/actual/siguiente; personalizar; generar informe | Volver del detalle conserva mes y scroll. Personalizar altera presentación, no cifras; cancelar/cerrar no escribe datos laborales. |
| U02 Informes | U01 → Más opciones o Generar informe al final | Preparar archivo con selección explícita; Guardar informe | PDF/XLSX, inclusiones privadas conscientes; Compartir secundario | Volver al Resumen conserva origen. Cancelar selector no afirma guardado; no se comparte sin acción expresa. |
| O01 Avisos | Menú Avisos y contexto → Notificaciones; tarjeta de jornada | Configurar avisos opcionales | Preferencias por jornada, reponer avisos ocultos, opciones avanzadas | Cerrar fijo y Atrás cierran diálogo. Cerrar no equivale a desactivar preferencias ya guardadas. Permisos se piden sólo desde su acción. |
| T01 Clima | Menú Clima o tarjeta de jornada | Leer estado/privacidad y habilitar conscientemente; consultar pronóstico | Unidades, aviso completo, actualizar, detalles del proveedor y borrar caché confirmado | Cerrar fijo/Atrás cierra diálogo sin cambiar habilitación. Cancelar borrado conserva caché; expandir créditos no abre enlaces. |
| G01 Ubicación para Clima | W01 → Opciones avanzadas → Clima del objetivo | Elegir ciudad aproximada o dirección conscientemente | Confirmar resultado; quitar ubicación guardada | Cancelar resultado/permiso no guarda coordenadas inventadas. Quitar requiere confirmación; la configuración laboral continúa usable sin ubicación. |
| I01 Widget | Menú Avisos y contexto → Widget | Consultar instancias y reconfigurar una | Configuración local de modo/privacidad/clima; sin instancias se explica alta en Android | Cerrar fijo/Atrás cierra gestión; cancelar configuración externa no guarda borrador. Nunca asociar otra instancia por posición visual. |
| B01 Crear Copia | Menú Aplicación → Copias | Crear copia local completa, cifrada inicialmente | Inclusión de imágenes; creación sin cifrar sólo tras advertencia expresa | Cancelar preparación/selector mantiene fuentes. Una fase no cancelable no admite un falso cierre que interrumpa atomicidad. |
| B02 Restaurar Copia | B01 → Restaurar desde un archivo | Leer, verificar, revisar y confirmar | Combinar con conflictos; reemplazar con segunda confirmación | Cancelar antes de aplicar conserva destino. Aplicación/recuperación protegen la salida; volver sólo cuando corresponda. |
| K01 Bloqueo | Menú Aplicación → Bloqueo | Activar/desactivar mediante autenticación del dispositivo | Plazo de bloqueo y bloquear ahora | Rechazar/cancelar autenticación conserva configuración. Puerta cerrada no compone contenido laboral; desbloqueo recupera el contexto permitido. |
| D01 Apariencia | Menú Aplicación → Apariencia | Elegir claro/oscuro/sistema y zoom interno | Cambiar entre 100/150/200 % | Atrás navega; las preferencias ya elegidas permanecen. Cada radio tiene un único objetivo de acción con nombre, rol y selección. |
| J01 Ayuda y recorrido | Menú Aplicación → Ayuda; guía inicial tras configuración | Leer tema o avanzar recorrido | Repetir guía; omitir sólo guía automática | Repetición cierra hacia Ayuda; automática confirma omisión. Falta de ancla real se explica; no se inventa un control ni se ejecuta la acción señalada. |
| X01 Apoyar MiGuardia / acceso externo | Menú Aplicación → Apoyar MiGuardia | Abrir enlace voluntario mediante dueño existente | Reintentar si no hay navegador | Cierra menú antes del callback; doble toque no abre dos veces. Regreso conserva destino/mes. No constituye envío a soporte ni aceptación de aporte. |
| X02 Actualizaciones | Menú Aplicación → Actualizaciones | Buscar y guardar una actualización conscientemente | Cancelar descarga; reintentar; ayuda de instalación manual | Regreso conserva destino y solicitudes pendientes; cancelar SAF no afirma archivo guardado. No instalar ni actuar en servidor para completar una fila. |
| X03 Entrada externa de aviso/Widget | Intención dirigida a QA con ID ficticio | Abrir fecha/jornada/ventana correspondiente | Descartar destino eliminado u obsoleto con mensaje | Bloqueo, guía y borrador aplazan consumo; consumir una sola vez tras revalidar. Regreso no reinicia una solicitud consumida. |

## B. Integridad, fallos y prueba atribuible por superficie

| ID | Datos/estado que deben conservarse; error adverso | Pruebas existentes o nuevas a ejecutar y alcance | Estado / evidencia nueva |
|---|---|---|---|
| M01 | Destino y mes. Menú cerrado antes de callback; accesos bloqueados durante otro dueño. | `NavigationDrawerComposeTest`: jerarquía única, entradas, bloqueo y temas/zoom/orientación; `SupportMenuComposeTest`/`UpdatesMenuComposeTest`: doble toque y cancelación por cambio de estado. | PENDIENTE; sin recibo incorporado. |
| C01 | Cero escrituras por consulta/cambio de mes. Todas las fechas visibles, sin texto cortado ni objetivos táctiles reducidos. | `CalendarComposeTest`, `CalendarAdaptiveLayoutComposeTest`, `Stage4CalendarMatrixTest` (geometría/semántica/capturas; 4/5/6 filas × temas × zoom × orientaciones). Restricción real de ventana se informa como tal. | PENDIENTE; R01 sin conclusión incorporada. |
| C02 | Identidad UUID, historial, dos jornadas, arrastre nocturno, Franco y condiciones coexistentes. Error/carga no equivalen a vacío. | `CalendarCommonV2ComposeTest`, `V2ShiftEditComposeTest`, `NextEventComposeTest`; `V2ReadOnlySurfacesInstrumentedTest` para ausencia de escritura. | PENDIENTE. |
| C03 | Fechas seleccionadas y borrador según propósito GENERAL/MANUAL_LOAD/HOLIDAY; ningún consumidor doble. | `CalendarCommonV2ComposeTest`, `ExceptionsComposeTest.backCancelsHolidaySelectionCompletelyWithoutRestorationOrWrites`, `V2ManualShiftLoadActivityTest.backFromInitialDateSelectionCancelsCompletelyWithoutReentryOrWrites`. | PENDIENTE. |
| L01 | Una/varias/sin fechas; institución/horario/puesto/nota/imágenes al volver, vaciar, recrear y rotar. | `V2ManualShiftLoadComposeTest`; `V2ManualShiftLoadActivityTest.reselectingDatesPreservesCompleteDraftAcrossEmptySelectionAndRecreationThenSavesOnlyFinalDates` y `unifiedSingleAndMultipleLoadOverwriteOverlayClearAndCancelPreserveExactData`. | PENDIENTE; evidencia Stage 3.2 sólo histórica. |
| L02 | Catálogo confirmado independiente de borrador; error/archivo de último horario no cambia timeline ni selección silenciosamente. | `V2ManualShiftLoadComposeTest` (catálogo ordenado, sin horarios, cancelación, archivo); `V2ManualShiftLoadActivityTest.contextualPlaceAndTemplateSurviveRecreationThenConfirmExactPairs` y `cancellingContextualTemplateKeepsSavedPlaceAndOriginalConfirmedLoad`. | PENDIENTE. |
| L03 | CAS del par histórico, notas y todo el lote; doble toque/reintento no duplica. Error SQL revierte todas las filas. | `V2ShiftPersistenceInstrumentedTest` (intervalo, Superponer, nota concurrente, rollback, dos escritores); `CalendarClearInstrumentedTest`; `ManualLoadNotesInstrumentedTest`. UI de advertencias en `V2ManualShiftLoadComposeTest`. | PENDIENTE. |
| W01 | Primera configuración real; catálogo y borrador; expansión/scroll del padre. No escritura al entrar/volver. | `WorkSetupComposeTest`; `Stage4FlowsActivityTest.configurationChildrenReturnToTheirRealParentAfterRecreationWithoutWrites` y `directChildrenCloseWithoutInventingAConfigurationParent`. Comparan snapshot lógico. | PENDIENTE; F02 requiere recibo de ambas entradas. |
| H01 | Referencias por fecha, sin cero falso; borrador y meta completa tras recreación. | `HoursAndExtrasComposeTest`; `HoursAndExtrasActivityTest.reviewedReferenceDraftSurvivesRecreationAndPersistsTheChosenFutureRestart`; `HoursExtraRestorationRaceInstrumentedTest`; pruebas de padre W01. | PENDIENTE. |
| H02 | Horario planificado/real y extras exactas; conflicto conserva datos actuales y ofrece recuperar, no sobreescribir. | `V2ShiftActualComposeTest.sourceConflictOffersRefreshAndDirtyDraftOffersConsciousDiscard`; `V2ShiftActualActivityTest.registerRecreatePersistReopenAndReturnToPlannedEndToEnd`; `ShiftActualPersistenceInstrumentedTest`; `HoursAndExtrasActivityTest`. | PENDIENTE. |
| H03 | Versiones históricas, selección exacta de clase y respuestas explícitas; rollback de alta inline fallida. | `V2ShiftActualComposeTest.catalogStartsBothAnswersEmptyAndSaveOnlyEnablesAfterExplicitYesNo`; `ShiftActualPersistenceInstrumentedTest.inlineDuplicateRollsBackClassAndAggregateAndClassCasRejectsStaleSelection`; pruebas de padre W01. | PENDIENTE. |
| A01 | Configuración por fecha independiente de horas trabajadas; error de observación no oculta ventanas guardadas. | `AvailabilityComposeTest` (error, revisión, descarte, paisaje 200); `AvailabilityActivityTest.configurationDraftAndReviewSurviveRecreationWithoutChangingHoursReference`; pruebas de padre W01. | PENDIENTE. |
| A02 | Ventana contextual y etiqueta histórica; no modifica configuración laboral global ni suma jornada activa. | `AvailabilityActivityTest.createRecreateCorrectAndDeleteReturnsToSameCalendarDetail`; `AvailabilityWindowPersistenceInstrumentedTest`; `NextEventComposeTest.activeAvailabilityKeepsItsHistoricalLabelAndDoesNotBecomeAShift`. | PENDIENTE. |
| RP01 | Plan, fechas exactas, historial protegido; error no se presenta como lista vacía, cancelación no escribe. | `V2RecurringPlanComposeTest`, `V2RecurringPlanActivityTest.draftSurvivesRecreationAndMultiMonthPlanReturnsReactivelyToCalendar`, `RecurringPlanPersistenceInstrumentedTest`; pruebas de padre W01. | PENDIENTE. |
| S01 | Sólo jornada/rango elegido; notas/evidencia privadas; vacaciones y arrastre conservados. | `CalendarCommonV2ComposeTest`, `ShiftSituationPersistenceInstrumentedTest`, `VacationComposeTest`; agregar observación del diálogo real con teclado y cancelación si el caso no está instrumentado. | PENDIENTE; reproducción física del diálogo pendiente. |
| E01 | Fechas no elegidas y feriados previos; conflicto conservar/reemplazar/cancelar explícito. | `ExceptionsComposeTest` (grilla única, Atrás sin reentrada, descarte y conflicto); persistencia en pruebas de base pertinentes. | PENDIENTE. |
| V01 | Rango inclusivo, jornadas coexistentes, cálculo excluido explicado; descarte deja rango original. | `VacationComposeTest.editorShowsInclusivePreviewAndSaves`, `discardConfirmationProtectsUnsavedDraft`, `calendarShowsVacationWithShiftAndExplainsExcludedHoursInDetail`; `VacationPersistenceInstrumentedTest`. | PENDIENTE. |
| N01 | Texto, orden e identidad de miniaturas; 0/1/29/30 y lote excedente; no truncar ni borrar referencias confirmadas. Fallo parcial, archivo grande/falta de espacio y restore concurrente. | `ExceptionsComposeTest`; JVM `ExceptionsCoordinatorTest.imageSelectionCapacityAcceptsExactFitAndRejectsWholeExcessBeforeImport`, `imageSelectionReadFailureOrCancellationRollsBackOnlyNewImages`, `coroutineCancellationStillCompletesSuspendingImageCleanup`; `V2ManualShiftLoadCoordinatorTest.excessImageSelectionPreservesManualDraftAndRejectsBeforeReadingUris`; `ManualLoadNotesInstrumentedTest` y `Stage4NoteRestoreConcurrencyTest`. La selección OEM real es otra prueba. | PENDIENTE; F03/F06/R04 requieren recibos separados. |
| P01 | Mes, selección exacta, orientación EXIF y archivo; selección desaparecida no muestra otra foto. | `PhotosComposeTest` (visor, reemplazo, EXIF y selección ausente); `SchedulePhotoFileStoreInstrumentedTest`, `SchedulePhotoBitmapDecoderInstrumentedTest`. Picker/cámara OEM quedan aparte. | PENDIENTE. |
| U01 | Mes/scroll, cifras y contribuciones de la misma proyección; preferencias independientes. Error recuperable sin falso cero. | `SummaryComposeTest.monthControlsShareOneRowAndNumbersPrecedeIntroductionAndReport`, `metricDetailUsesTheExactRowsAndNeverDisplaysPrivateFields`, `returningFromDetailRestoresTheOverviewScrollPosition`; `SummaryActivityTest`. | PENDIENTE; captura comparativa F04 pendiente. |
| U02 | Selección de privacidad y origen Resumen; nota médica exige segunda confirmación. Error/SAF cancelado no publica archivo parcial. | `reports.ReportsComposeTest`, `ReportsViewModelInstrumentedTest`, `ReportArtifactStoreInstrumentedTest`, `ReportDestinationWriterInstrumentedTest`, `ReportPhotoStagerInstrumentedTest`; callbacks de guardado/compartir con dobles. | PENDIENTE; SAF/compartir real no ejecutados por esta matriz. |
| O01 | Preferencias y avisos guardados; cierre no dispara permiso, prueba ni mutación. Error/rechazo permite seguir usando app. | `NotificationComposeTest.notificationSurfaceRemainsReachableInLightDarkAndEveryInternalZoom` comprueba cierre fijo en seis combinaciones y cero callbacks de mutación; otros métodos prueban permisos con estados dobles. | PENDIENTE; permisos/notificación real separados de Compose. |
| T01 | Habilitación consciente, coordenadas/IP explicadas; créditos accesibles; sin pronóstico de otro objetivo. Cierre/error no habilita red. | `WeatherComposeTest` (activación visible, créditos/links interceptados, fallo de navegador, caché y jornada); `WeatherViewModelInstrumentedTest`; `WeatherInfrastructureInstrumentedTest`. | PENDIENTE; F05 captura/lectura humana y red real no acreditadas. |
| G01 | Denegación y resultado vacío no bloquean catálogo; no seguimiento ni permiso preciso. | `WorkSetupComposeTest.objectiveLocationOffersAddressOrActivateWhenThereAndCanBeRemoved`; adaptadores/VM con dobles. Diálogo real aproximado, geocoder y regreso desde Ajustes requieren ensayo supervisado autorizado. | PENDIENTE; no accionar permisos/red por completar casilla. |
| I01 | ID real de instancia pese a etiqueta visual; cerrar no reconfigura; cancelar conserva preferencias previas. | `WidgetComposeTest.managementCloseStaysVisibleAfterScrollingInBothThemesAndEveryZoom`, pruebas de configuración/restauración; `WidgetConfigurationActivityTest`. No demuestra launcher OEM ni visual de widget real. | PENDIENTE. |
| B01 | Room/fuentes no mutadas; referencias e imágenes verificadas; contraseña no exportada accidentalmente. | `backup.BackupComposeTest` (cifrado inicial y advertencia sin cifrar), `BackupDocumentPublicationInstrumentedTest`, `BackupPhotoFilesInstrumentedTest`, `PortablePreferencesGatewayInstrumentedTest`. | PENDIENTE; SAF y copia ficticia final requieren recibo propio. |
| B02 | Copias V5–V8, identidad, fuentes y archivos; rollback/recuperación sin pérdida. Conflictos nunca sobreescritura silenciosa. | `backup.BackupComposeTest`, `BackupRecoveryInstrumentedTest`, `BackupPhotoMutationGateInstrumentedTest`, `BackupDatabaseGatewayInstrumentedTest`, `Stage4NoteRestoreConcurrencyTest`; restauración de fixture aislado. | PENDIENTE; ninguna copia personal ni restauración pública autorizada por esta fila. |
| K01 | Ninguna semántica laboral tras bloqueo; borrador recuperado sólo al desbloquear. Fallo del store permanece seguro. | `security.AccessLockComposeTest.closedGateDoesNotComposeSensitiveContentAndReturnsToItAfterSuccess`, `unlockRestoresTheExistingSaveableSurfaceAndDraftWithinTheLiveProcess`; manifiesto y dobles de autenticación. | PENDIENTE; biometría/credencial real requiere autorización específica actual. |
| D01 | Preferencias internas; radio sin doble callback; no tocar ajustes Android. | `AppearanceComposeTest.appearanceOffersStandardLargeAndExtraLargeInternalZoom` verifica rol, estado, hijo sin acción y callback único; tema en el otro método; `VigiliaThemeComposeTest`. | PENDIENTE; TalkBack humano separado. |
| J01 | Marca de guía sin degradación; navegación sin escrituras laborales ni permisos. Ancla retirada no deja foco fantasma. | `HelpComposeTest` (todos los temas/recorrido, rutas actuales, ancla desmontada y ausencia explicada); `OnboardingPreferencesInstrumentedTest`. | PENDIENTE; F01 necesita recorrido físico y lectura humana. |
| X01 | Destino/mes/borrador; URL autorizada sin datos laborales; error permite reintentar. | `SupportMenuComposeTest`, `SupportMenuActivityTest`, `SupportLinkInstrumentedTest`: callbacks/intents y puerta; distinguir simulación de navegador real. | PENDIENTE; no contacto/envío/aporte real. |
| X02 | Identidad, firma esperada, integridad del APK y regreso; cancelación y doble toque sin descarga repetida. | `UpdatesMenuComposeTest`, `UpdatesReturnComposeTest`, `updates.UpdatesScreenTest`, `UpdatesActivityTest`. `UpdatesOemSafActivityTest` y `UpdatesSupervisedActivityTest` sólo en sesión supervisada aplicable. | PENDIENTE; sin instalación, consulta de producción ni publicación para completar matriz. |
| X03 | ID vigente, destino conservado y consumo único; bloqueo/borrador no se saltean. | `NavigationDrawerComposeTest.calendarNavigationRequestClosesDrawerAndShowsRequestedDay`, `AvailabilityActivityTest.notificationAvailabilityActionOpensItsOwnerDate`, `UpdatesReturnComposeTest.newRequestDuringUpdatesWaitsForReturnAndAllLocalGatesThenConsumesOnce`. | PENDIENTE; entrega real desde sistema separada del intent ficticio. |

## Casos transversales que no se cierran con una fila verde

| Riesgo / combinación | Criterio y prueba concreta | Estado |
|---|---|---|
| R01 Mes denso | `Stage4CalendarMatrixTest`: febrero 2021, febrero 2024, abril 2026 y agosto 2026 (28/29/30/31 días; 4/5/6 filas), jornadas y condiciones densas, claro/oscuro, 100/150/200, retrato/paisaje. Registrar fechas, texto, rol y límites de celdas. Son 24 casos previstos por orientación, 48 en total. | PENDIENTE; no resolver una restricción mediante scroll, semana o reducción táctil. |
| R02 Rendimiento | `Stage4FlowsActivityTest.representativeYearMeasurements` y `stressHistoryMeasurements`, mismo dispositivo/variante/dataset/driver en APK preservado y candidato. Separar `seedRepresentativeForColdStartMeasurements` del arranque frío externo. | PENDIENTE; no hay métricas incorporadas en este documento. |
| R02 Cobertura del dataset | Driver inspeccionado: representativo 240 jornadas/365 días, estrés 1.920/2.920 días; notas cada diez jornadas, cero adjuntos y cero planes recurrentes iniciales. Doce iteraciones previstas, tres jornadas nuevas guardadas por iteración. Informar los ceros como limitación; no generalizar a carga multimedia/repeticiones. | PENDIENTE; comparar cantidades realmente ejecutadas, no sólo previstas. |
| R02 Operaciones | Arranque Activity caliente, cambio de mes, detalle, entrar a carga, catálogo, volver a selección, previsualizar/guardar tres jornadas, Resumen. Guardar muestras crudas, p50/p95, frames medidos y muestras perdidas; fijar umbrales desde baseline antes de optimizar. | PENDIENTE; no atribuir tiempo de sincronización del driver exclusivamente a UI ni afirmar 60 fps. |
| R03 Colecciones SQL | `CalendarClearInstrumentedTest.clearMoreThanNineHundredShiftsPreservesUnselectedPairsAndRejectsStalePreview` y `clearNotesBeyondMeasuredSqliteBindLimitKeepsAllReferencesAndRollsBackOnFailure`: medir límite real, superar ese límite, conservar no seleccionados y probar rollback/concurrencia. | PENDIENTE; requiere SQLite real del entorno registrado, no sólo JVM. |
| R04 Restore/archivos | `Stage4NoteRestoreConcurrencyTest.startupReconciliationCannotDeleteAnAttachmentConfirmedByConcurrentRestore` y `noteDeletionCleanupCannotDeleteAnAttachmentConfirmedByConcurrentRestore`: barreras deterministas, referencias Room más existencia/hash de archivo. | PENDIENTE; no alcanza que el archivo quede mientras la referencia apunta a otro objeto. |
| Imágenes y reintento | Lotes 0/1/29/30; excedente rechazado antes de importar; cancelación picker; una lectura falla a mitad; falta de espacio; doble toque; recreación y cleanup de temporales propios. Comparar lista ordenada y hashes antes/después. | PENDIENTE; desglosar lector doble, filesystem real y picker OEM. |
| Causalidad laboral | Sobrescribir sólo intervalo; Superponer expreso; avisos de descanso <12 h y superposición no bloqueantes; Franco con arrastre previo; COMPLETED derivado; toda mutación de lote atómica. | PENDIENTE; enlazar JVM de dominio y prueba Room aplicable, no sustituir una con otra. |
| Consumidores | Tras cambio de fuente compartida, comparar Horas, Resumen, próximo evento, avisos, clima, repetición, informes y Copias contra misma fixture; consultar/expandir no escribe. | PENDIENTE; registrar cada consumidor realmente afectado y ejecutado. |
| Recreación/rotación | En selección, formulario, catálogo inline, revisión, confirmación y retorno al padre, repetir con borrador completo; una aserción fuera de viewport se diagnostica y conserva, no se presenta como pérdida de estado. | PENDIENTE; casos Activity existentes cubren una parte, no cada transición en todas combinaciones. |
| Teclado | Nombre/puesto/nota largos, horarios y rangos: campo, mensaje de error y acción principal alcanzables; Atrás primero atiende teclado/superficie según comportamiento real; no guardar al perder foco. | PENDIENTE; agregar registro supervisado por formulario afectado. |
| TalkBack | Nombre, rol, estado y orden de foco; selección por alternativa al gesto; etiquetas largas, eliminación de adjunto identificable y un solo objetivo por radio/toggle. Probar claro/oscuro independientemente del color. | PENDIENTE; semántica automatizada no acredita frase escuchada ni percepción humana. |
| Sistema/OEM | Picker/cámara, permisos aproximados, navegador, SAF, compartir, launcher/widget, credencial/biometría y notificaciones reales. | PENDIENTE; dobles permitidos para lógica, ejecución sensible conserva autorización y disponibilidad actuales. |

## Registro de resultados para MAIN

Agregar una fila por unidad efectivamente ejecutada; no modificar la evidencia
histórica ni reemplazar un fallo con el último verde. Si una clase cubre varias
filas, anotar los métodos/casos pertinentes. Un resultado parcial debe conservar
los subcasos pendientes.

| Ejecución / IDs | QA / runner / insumos | Fixture y entorno | Resultado verificable | Evidencia y límite |
|---|---|---|---|---|
| JVM integrado; N01, L01–L03, R04 y consumidores locales | Insumos del perfil full `20261002T211459.258318Z`; no APK instalado | Host, repositorios/archivos dobles cuando la clase lo indica | EJECUTADA local: 916/916, 0 fallos/errores/omitidas | `.local-evidence/optimization/20261002T211459.258318Z/local/`; build final y estabilidad quedan en auditoría. No completa recorridos Android. |
| Interoperabilidad local | Mismo perfil, checkout website sólo leído, Node explícito | Criptografía/contratos locales; sin tráfico productivo | EJECUTADA local: 26/26 | Subcarpeta `interop/`; no publicación/servidor real. |
| R03 SQL host | SQL real de DAO, tablas V8, hashes propios | SQLite host3.50.4, límites configurados999/32766; claves y filas ficticias | EJECUTADA condicional: ocho fallos originales esperados; consultas fragmentadas48/48 | `.local-evidence/stage4/r03-host/result.json`; FK desactivadas en fixture. Room/CAS/rollback Android PENDIENTES. |
| Confirmación perdida y R04 | Coordinador/gate reales, dobles explícitos | JVM con barreras; archivos/repositorios en memoria | EJECUTADA causal: antes1/1 y2/2 FALLÓ; después78/78 | `.local-evidence/stage4/imagenes/`; no restauración real Android/journal. |
| Borrado durante edición | Coordinator real, doble de archivos/referencias | Adjunto nuevo, referencia ajena y pendingId | EJECUTADA causal: antes2/2 FALLÓ; después23/23 | `delete-draft-before/` y `delete-draft-after/`; conservación Android pendiente. |
| Matriz visual, R01, R02 y dispositivo | APK baseline preservado; candidato sin instalar | Disponibilidad actual no confirmada | PENDIENTE | Ninguna captura, latencia o ejecución física Stage4 atribuida. |

Para F01/F04/F05/F06/F07 se necesitan capturas comparables o un criterio visual
observado sobre la superficie final, además de callbacks/pruebas funcionales.
Para R01, una captura por sí sola no mide objetivo táctil o texto omitido.
Para R02, una captura no mide latencia ni frames. Para R03/R04, compilar el
runner no ejecuta SQLite, barreras ni rollback.

## Selección de pruebas sin ampliar permisos

El catálogo vigente es `tools/qa-groups.json`: revisar grupos y riesgos al
momento de ejecutar. Las clases de presentación se reparten entre `app-rendering`
y `app-system`; no asumir que todo Compose carece de efectos de sistema.
`stage4-flows` reemplaza datos ficticios QA; `stage4-note-restore` usa restore
y archivos aislados; los casos SQL pertenecen al runner de base. Las sesiones
supervisadas de actualización/SAF no se convierten en una batería desatendida.

Vacíos que deben quedar visibles si no se ejecutan: TalkBack humano; picker y
otras superficies OEM; vuelta con teclado abierto; rotación/recreación de cada
transición; carga representativa con adjuntos/planes; 28/29/30/31 días completos;
capturas antes/después; retorno físico directo y desde padre; cierre concurrente
con guardado/error. La aceptación visual de Joaquín, la aceptación global de
Stage 3 y cualquier distribución son decisiones separadas.
