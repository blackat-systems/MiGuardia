# Etapa 14.1 · Lista de los 113 casos, uno por uno

Fecha: 2026-11-10 · Armada en la etapa 15 con lo anotado en `docs/STATUS.md` (secciones «Etapa 14.1»),
el [mapa de cobertura](ETAPA_14_MAPA_COBERTURA.md) y los resultados de las corridas en
`.local-evidence/claude-20261110-14/`. El mapa es la foto de antes; esta lista es el estado de hoy.

## Cómo leerla

- ✅ probado: lo que el caso pide tiene evidencia que pasó, en el nivel indicado.
- ⏳ falta: una parte no tiene evidencia todavía. La columna «Falta» dice cuál.
- ❌ falla: hay una falla conocida y abierta.
- **Niveles de evidencia:**
  - **PC**: prueba que corre en la computadora.
  - **Pantalla**: prueba en el Samsung que dibuja la pantalla con datos de mentira.
  - **Aislada**: prueba en el Samsung contra Android real, con base y preferencias propias de la prueba.
  - **Dedo**: Joaquín lo hizo en el Samsung, sobre la app QA (sesión del 10/11).
  - **13.5**: prueba de pantalla anterior que pasó en el Samsung el 9/11.

## Límites de esta lista

- **Las pruebas automáticas pasaron sobre el último código** (`580b2c3`) en la corrida completa de la
  etapa 15 (10/11, `.local-evidence/claude-20261110-15/corrida1/`): 143 esperadas, **137 ✅, 0 ❌ y 6
  omitidas**. Las 6 omitidas son pruebas aisladas del motor del Widget (5 de `Stage14WidgetRuntimeTest` y
  1 de `Stage14WidgetViewModelTest`) que se saltean solas mientras haya un Widget real en la pantalla de
  inicio; su última pasada fue con una versión anterior. Las pruebas «13.5» no se repitieron en la 15.
- **Cada caso se asignó a sus pruebas por el nombre y la descripción de cada prueba y por las notas de
  STATUS**, no releyendo el cuerpo de las 143. Donde el nombre no alcanzaba, se buscó en el código; lo que
  no tiene evidencia directa quedó en ⏳.
- Un ✅ con «Dedo» dice lo que Joaquín hizo y contó ese día; lo que no dijo expresamente quedó en ⏳.

## Resumen

| Superficie | Casos | ✅ | ⏳ | ❌ |
|---|---|---|---|---|
| Avisos (A208–A231, C080) | 25 | 12 | 13 | 0 |
| Clima (A232–A245, C081–C084) | 18 | 13 | 5 | 0 |
| Bloqueo (A246–A259) | 14 | 14 | 0 | 0 |
| Apariencia y Ayuda (A260–A271) | 12 | 10 | 2 | 0 |
| Actualizaciones y apoyo (A272–A286, X014) | 16 | 12 | 4 | 0 |
| Widget y recorridos (W001–W024, X003, X012, X013, X015) | 28 | 18 | 9 | 1 |
| **Total** | **113** | **79** | **33** | **1** |

De los 33 ⏳: 21 se cierran con el dedo de Joaquín (entre ellos A214, A243 y W020, que ya pasaron la corrida
sobre el último código y sólo esperan una mirada), 3 recién con la versión firmada (A276, A278 y X014) y 9
no tienen hoy una prueba que los cubra en el teléfono (ver «Lo que queda sin camino automático»).

## Avisos

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| A208 ver estado global | ✅ | Pantalla: `Stage14NotificationScreensTest#disabledNotificationsShowTheOffState…` | — |
| A209 activar/desactivar | ⏳ | Pantalla: `…#switchTurnsNotificationsOffAndTheButtonTurnsThemBackOn` | Que apagar retire los avisos y alarmas ya puestos, en el teléfono |
| A210 permiso en contexto | ⏳ | Dedo: aceptó el diálogo real de Android | Rechazarlo, y el segundo rechazo · Dedo |
| A211 denegado vs bloqueado | ⏳ | Pantalla + PC | Leerlo con la app bloqueada en Ajustes · Dedo |
| A212 abrir ajustes de avisos | ✅ | Pantalla: `…#openingNotificationSettingsTargetsThisAppAndRefreshesAccessOnReturn` y `…#disabledStateStillOpens…` | — |
| A213 vista previa ficticia | ✅ | 13.5 | — |
| A214 aviso de prueba | ⏳ | Dedo: lo recibió. Aislada: `Stage14NotificationViewModelTest#testNotificationWithoutPermission…` y `…#testNotificationWithPermissionReportsItWasSent` (pasó en la corrida de la 15, con el permiso ya concedido) | No se anotó si se va solo al minuto |
| A215 ritmo | ✅ | 13.5 + PC | — |
| A216 «Personalizado» | ✅ | 13.5 + PC | — |
| A217 6/8/12/24 h | ✅ | Pantalla: `…#eachQuickLeadButtonSendsItsMinutes…` | — |
| A218 varios anticipos | ✅ | Pantalla: `…#reminderEditorAddsAndRemoves…` y `…#reminderEditorStopsAtFiveLeads…` | — |
| A219 fijo o descartable | ⏳ | Pantalla: `…#persistenceChoiceSendsPinnedOrDismissible…`. Dedo: vio el aviso fijo | Ver un aviso en curso en modo «descartable» |
| A220 sonido/vibración/silencio | ✅ | Dedo: el anticipo llegó, vibró y sonó | No se probaron «sólo vibración» ni «silencio» con el dedo |
| A221 sonido elegido/predeterminado | ⏳ | Pantalla: `…#defaultSoundButtonClears…`, `…#soundControlsOnlyExist…`, `…#soundPickerIsAsked…` | Elegir un sonido de verdad · Dedo |
| A222 puntualidad exacta | ⏳ | Pantalla: `…#preciseTimingSwitch…` y `…#resolvingExactTiming…` | El permiso real de Android · Dedo |
| A223 privacidad | ⏳ | Pantalla: `…#eachLockScreenPrivacyChoice…`. Dedo: bloqueo con «Mostrar en la pantalla bloqueada» apagado | Mirar la pantalla bloqueada en completo, reducido y oculto · Dedo |
| A224 ver avisos ocultos | ✅ | 13.5 + Pantalla: `…#singleHiddenNotification…` y `…#hiddenNotificationsStayListed…` | — |
| A225 restaurar uno | ⏳ | PC (jornada) | Restaurar una disponibilidad |
| A226 restaurar todos | ⏳ | Aislada: `Stage14NotificationViewModelTest#restoreAllWithNothingHidden…` | Restaurar de verdad dos o más |
| A227 desactivar en una jornada | ✅ | Pantalla: `…#shiftUsingGeneralSettingsCanBeSilenced…`. Aislada: `…ViewModelTest#shiftOverrideFollows…` | — |
| A228 anticipos de una jornada | ⏳ | Pantalla: `…#shiftWithOwnSettingsEditsItsLeads…` | Alarmas reales con anticipos propios |
| A229 volver a la general | ✅ | Pantalla + Aislada (las mismas de A227 y A228) | — |
| A230 abrir desde el aviso | ⏳ | Dedo: tocó el aviso y abrió el día | Tocar el aviso de una jornada ya borrada · Dedo |
| A231 reconciliar cambios | ⏳ | PC por capas. Dedo: lo deslizó | Pasada a mano completa: editar la jornada y mirar el aviso |
| C080 avisos por jornada | ✅ | Pantalla: 24 de 24 en `Stage14NotificationScreensTest`, incluida `…#personalizingAShiftOpensItsOwnEditor…` | — |

## Clima

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| A232 abrir Clima | ✅ | 13.5 | — |
| A233 explicar y habilitar | ✅ | 13.5 | — |
| A234 deshabilitar sin permisos | ✅ | Pantalla: `Stage14WeatherScreensTest#enableSwitchTurnsWeatherOffAndOn…`. Aislada: `Stage14WeatherViewModelTest#disablingWeatherCancelsTheDownloadInFlight…` | — |
| A235 °C/°F sin redescargar | ✅ | Pantalla: `…#unitChoiceSelectsFahrenheit…` y `…#shiftDetailShowsEveryTemperatureInFahrenheit…`. Aislada: `…#changingTheUnitKeepsTheSameForecast…` | — |
| A236 Clima en avisos completos | ⏳ | Aislada: `Stage14WeatherNotificationCacheTest` (4). Pantalla: `…#weatherInNotificationsSwitch…` | Ver un aviso real con el clima · Dedo |
| A237 proveedor y atribución | ✅ | 13.5 | — |
| A238 abrir sólo el enlace elegido | ⏳ | 13.5 (destino interceptado) | Navegador real y vuelta · Dedo |
| A239 borrar cachés con confirmación | ✅ | Pantalla: `…#cancellingTheCacheDeletion…`. Aislada: `…#clearingTheCacheRemovesTheSavedForecast…` | — |
| A240 intervalo exacto | ✅ | 13.5 + PC | — |
| A241 actualizar a mano | ⏳ | Pantalla: `…#tappingRefreshAsksOnce…`. Aislada: `…#manualRefreshForcesADownload…`. Dedo: con red real la hora de «Última actualización» cambió | Joaquín dijo «queda igual»: confirmar si se ve el aviso «Pronóstico actualizado.» |
| A242 distinguir estados | ✅ | Pantalla: `…#shiftDetailLabelsFreshOldAndExpired…`, `…DeclaresPartialCoverage…`, `…#shiftOutsideTheForecastWindow…`, `…#dayCardLabels…` | — |
| A243 temperatura, lluvia, viento | ⏳ | Pantalla: `…#shiftDetailShowsFeelsLikeRainWindAndGusts…` y `…NeverDrawsAValueTheProviderDidNotGive`. PC: «17 °C» en vez de «17–17 °C» | Ver «17 °C» en el Samsung en una jornada de una hora (el arreglo ya está instalado) · Dedo |
| A244 no tocar otro objetivo | ✅ | 13.5 + PC | — |
| A245 error de red o navegador | ✅ | Pantalla: `…#refreshErrorKeepsTheLastForecast…` y `…#failedRefreshBringsTheErrorIntoView…`. Dedo: modo avión, con captura | — |
| C081 explicar antes de activar | ⏳ | 13.5 | El diálogo de ubicación de Android · Dedo |
| C082 ubicación faltante | ✅ | 13.5, con el texto viejo corregido. Dedo: el lugar sin ubicación lo dijo | — |
| C083 jornada nocturna completa | ✅ | Pantalla: las tarjetas por hora de A243 | — |
| C084 fresco/antiguo/vencido | ✅ | Pantalla y Aislada de A242 y A245; PC: fresco hasta 60 min, antiguo hasta 6 h | — |

## Bloqueo

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| A246 capacidad del teléfono | ✅ | Pantalla: `Stage14AccessLockScreensTest#closedGateWithoutUsableDeviceSecurity…` y `…#settingsWithoutUsableDeviceSecurity…`. PC: `Stage14AccessLockCoordinatorTest` | — |
| A247 activar | ✅ | PC. Dedo: activó con huella o PIN | — |
| A248 inmediato al salir | ✅ | PC. Dedo: salió y volvió, pantalla «MiGuardia está bloqueada» | — |
| A249 un minuto | ✅ | PC (reloj de prueba) | — |
| A250 cinco minutos | ✅ | PC (reloj de prueba) | — |
| A251 quince minutos | ✅ | PC (reloj de prueba) | — |
| A252 cambiar plazo con autenticación | ✅ | PC | — |
| A253 bloquear ahora | ✅ | PC + Pantalla | — |
| A254 desactivar | ✅ | Pantalla: `…#enabledSettingsCallDisableOnce…`. Dedo: lo desactivó | — |
| A255 recuperar pantalla y borrador | ✅ | PC + 13.5 | — |
| A256 cancelar deja cerrado | ✅ | Pantalla: `…#closedGateShowsTheOwnMessageOfEachAuthenticationErrorAndStaysClosed`. PC. Dedo: canceló y siguió bloqueada | — |
| A257 sin credencial o bloqueo temporal | ✅ | Pantalla: `…#settingsShowTheOwnMessageOfEachAuthenticationError` y las de A246 | — |
| A258 reintentar o reparar | ✅ | Pantalla: `…#readErrorDoorCallsRetryAndRepair…`. PC: cinco pruebas de reintento | — |
| A259 tapar contenido | ✅ | Pantalla: `…#recoveryLoadingAndPrivacyCoverAreDrawn…`. Dedo: Recientes no mostró pantallas | No se probó una captura de pantalla con el dedo |

## Apariencia, accesibilidad y Ayuda

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| A260 oscuro por defecto | ✅ | PC | — |
| A261 tema claro | ⏳ | 13.5. Dedo: «todo salió bien» | No dijo expresamente si volvió a verse la tarjeta oscura en modo claro |
| A262 tema oscuro | ✅ | Pantalla: `Stage14AppearanceScreensTest#lightToDarkToggleSendsDark…` y `…#changingTheModeChangesThePageBackground…` | — |
| A263 seguir el sistema | ✅ | PC | — |
| A264 letra nativa | ✅ | 13.5 + Pantalla: `…#everyControlStaysReachableWithWholeTextAtEveryFontScaleAndTheme` | — |
| A265 TalkBack | ⏳ | 13.5 (tres controles del Calendario) | TalkBack de oído, con guía · Dedo. Tampoco hay prueba del árbol real bajo una pantalla completa |
| A266 nueve temas de Ayuda | ✅ | Pantalla: `Stage14HelpScreensTest#eachOfTheNineTopicsOpens…` y `…#openingAnotherTopicCollapsesThePreviousOne` | — |
| A267 recorrido | ✅ | 13.5 | — |
| A268 recorrido | ✅ | 13.5 | — |
| A269 recorrido | ✅ | 13.5 | — |
| A270 recorrido | ✅ | 13.5 | — |
| A271 Ayuda sin escrituras | ✅ | 13.5 | — |

Letra al 200% y ambos temas: 8 de 8 en `Stage14HelpScreensTest` después del arreglo de «Siguiente».

## Actualizaciones y apoyo voluntario

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| A272 abrir sin buscar sola | ✅ | PC | — |
| A273 versión instalada | ✅ | Pantalla: `Stage14UpdatesScreenTest#installedVersionIsShown…` | — |
| A274 buscar al tocar | ✅ | PC con dobles. Dedo: respondió que no hay versión publicada | — |
| A275 cuatro estados | ✅ | Pantalla: `…#eachSearchResultHasItsOwnExactText…`. PC: `Stage14UpdatesViewModelTest` | — |
| A276 descarga con progreso | ⏳ | Pantalla: `…#downloadShowsBytesReceived…`. PC | Descarga real: sólo con la versión firmada (parte de entrega) |
| A277 verificar el APK | ✅ | PC | Con un APK firmado real, en la parte de entrega |
| A278 guardar con el selector | ⏳ | PC con dobles | Selector real de Samsung con un APK descargado (parte de entrega) · Dedo |
| A279 releído o sólo escrito | ✅ | Pantalla: `…#savedApkSaysWhether…`. PC | — |
| A280 cancelar | ✅ | Pantalla: `…#cancelIsVisibleWhileSearching…`. PC | — |
| A281 errores y espera | ✅ | Pantalla: `…#retryWaitShowsTheCountdown…` y `…#connectionErrorIsExplained…` | — |
| A282 volver sin repetir | ✅ | PC | — |
| A283 instalación manual | ✅ | Pantalla: `…#manualInstallationIsExplained…`. PC: `Stage14NoInstallerGuardTest` (4) | — |
| A284 apoyo sin datos laborales | ✅ | PC. Dedo: abrió el navegador y volvió | — |
| A285 sin doble apertura | ✅ | PC | — |
| A286 sin navegador | ⏳ | PC: `Stage14SupportNoBrowserNoticeGuardTest` (2) | Ver el aviso en el teléfono (no se puede provocar sin quitar el navegador) |
| X014 recorrido completo | ⏳ | Sus partes, arriba | Punta a punta con descarga real (parte de entrega) |

Desde la app QA nunca se puede descargar: su paquete y su firma no son los públicos.

## Widget y recorridos combinados

| Caso | Estado | Evidencia | Falta |
|---|---|---|---|
| W001 abrir y explicar el alta | ✅ | Pantalla: `Stage14WidgetScreensTest#emptyManagementExplainsHowToAddFromAndroid…` | — |
| W002 listar instancias | ✅ | Pantalla: `…#managementListsInstancesInOrder…`. Aislada: `Stage14WidgetViewModelTest` (2) | — |
| W003 vacío, cargando, error | ✅ | 13.5 | — |
| W004 valores iniciales | ✅ | Pantalla: `…#firstConfigurationOpensWithAutomaticHiddenAndWeatherOffSelected`. PC: `Stage14WidgetDefaultsTest`. Dedo: configuración inicial | — |
| W005 modo | ✅ | PC. Dedo: Widget real visto | — |
| W006 modo | ✅ | PC | Verlo en un Widget real es opcional: Joaquín vio un solo modo |
| W007 modo | ✅ | PC | Igual que W006 |
| W008 privacidad completa | ✅ | Aislada: `Stage14WidgetRemoteViewsTest#completePrivacyNeverShowsOrCarriesTheStoredObjectiveAddress`. Dedo: lo vio en Completa | — |
| W009 reducida | ✅ | PC + vistas | — |
| W010 oculta | ✅ | PC + Aislada: `…#recoveryViewShowsItsTextsWithoutLaborData…` | La prueba exhaustiva de 48 variantes sigue sin constar aprobada en el Samsung |
| W011 Clima por instancia | ✅ | Aislada: `Stage14WidgetEngineTest#weatherTextReachesOnlyTheExpandedCompleteInstance…` y `…#instancesThatCannotShowWeather…` | — |
| W012 guardar/cancelar | ✅ | Pantalla: `Stage14WidgetScreensTest#activeSaveLocksSaveAndCancel…` y `…#failedSaveShowsTheError…` | — |
| W013 recrear | ⏳ | 13.5 | Widget quitado mientras se configura |
| W014 instancias independientes | ⏳ | Aislada: `Stage14WidgetEngineTest#twoInstancesKeepTheirOwnOptions…` | Dos Widgets reales en la pantalla de inicio · Dedo |
| W015 tamaños | ❌ | Dedo: cambió de tamaño a dos filas y quedó bien | **El Samsung no deja bajarlo a una fila.** Sin causa confirmada: falta quitarlo y volver a agregarlo |
| W016 filas y contador | ✅ | PC + vistas | — |
| W017 cuenta regresiva | ⏳ | Aislada: `Stage14WidgetRemoteViewsTest#expiredCountdownIsHidden…` | Verla correr en el Widget real: no quedó dicho · Dedo |
| W018 tocar abre lo exacto | ⏳ | Dedo: lo tocó y abrió bien | Tocar con la jornada ya eliminada · Dedo |
| W019 tocar el crédito de Clima | ⏳ | — | Tocarlo en un Widget ampliado con Clima · Dedo |
| W020 tema y letra | ⏳ | Aislada: `Stage14WidgetRemoteViewsTest`, 8 de 8 sobre el último código (paleta nueva y vistas con letra al 150 y 200%) | Verlo en claro y en oscuro en el Widget real · Dedo |
| W021 carga y error | ✅ | Aislada: `Stage14WidgetEngineTest#readFailureDrawsTheErrorView…` y `…ViewModelTest#refreshFailureShowsTheReadError…` | — |
| W022 se actualiza con los cambios | ✅ | Aislada: `…#engineRedrawsByItselfWhenAShiftIsSaved…` y `Stage14WidgetRuntimeTest#theSourceTheEngineObservesReemits…` | — |
| W023 inicio/fin/medianoche/reinicio | ⏳ | Aislada: `…#upcomingShiftSchedulesItsStart`, `…#ongoingShiftInAutomaticSchedulesItsEnd`, `…#emptyBaseSchedulesTheNextLocalMidnight…` | Reinicio del teléfono, cambio de hora y de zona |
| W024 quitar/restaurar | ⏳ | Aislada: `Stage14WidgetRuntimeTest#deletingOneInstanceRemovesOnlyItsPreferences…` y `…#pauseForRestoreStopsBackgroundWork…` | Widgets después de restaurar una Copia |
| X003 coherencia entre vistas | ✅ | Aislada: `Stage14WidgetEngineTest#engineRedrawsByItselfWhenAShiftIsSavedAndDrawingNeverChangesTheBase` | — |
| X012 destino exacto | ⏳ | Dedo: el toque abrió la jornada | El mismo identificador para una disponibilidad, del lado del Widget |
| X013 Clima y privacidad | ✅ | Aislada: `…#failingWeatherNeverHidesTheShift…` y `…#weatherTextNeedsGlobalConsentAndAFreshCompleteCache` | — |
| X015 consultar no escribe | ✅ | Aislada: la misma de X003. Con el Widget real instalado, la foto de datos del 10/11 dio 23 bases sin cambios ajenos a lo que Joaquín editó | — |

Límites conocidos del Widget, sin decidir: con letra de Android al 150% o más, el contador de una fila queda
cortado (calculado, sin medir en el teléfono); en una fila un nombre largo termina en «…».

## Lo que queda sin camino automático

Nueve ⏳ no se cierran ni con la corrida ni con una mirada simple; necesitan una decisión de Joaquín sobre
si se prueban a mano, se les arma una prueba o se aceptan como están:

- **Avisos:** A209 (apagar retira lo ya puesto), A225 (restaurar una disponibilidad), A226 (restaurar dos o
  más de verdad) y A228 (alarmas reales con anticipos propios). Las pruebas viejas que lo hacían borran la
  base o pisan preferencias de la app QA; no existe todavía el arnés aislado de avisos.
- **Actualizaciones:** A286 (aviso de «no hay navegador»).
- **Widget:** W013, W023, W024 y X012.
