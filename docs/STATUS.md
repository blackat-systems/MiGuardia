# Estado actual de MiGuardia 3.0

- Etapa: **14 · Sistema y aceptación final — INICIADA el 9/11**, parte 14.1 · Superficies del sistema
  (docs/prompts/ETAPA_14_SISTEMA_Y_ACEPTACION.md). Base `1dd3899`, sin diferencias al arrancar.
  - Hecho: [mapa de cobertura](design/ETAPA_14_MAPA_COBERTURA.md) de los 113 casos y **tanda 1** (pruebas
    de PC y de pantalla, sin teléfono): ver la sección de abajo.
  - **Aislamiento decidido por Joaquín (9/11): mixto.** Automático sobre base y preferencias propias de
    cada corrida; con su dedo, sobre la app QA, sólo cambian preferencias y se devuelven.
  - **Decisión de Joaquín (10/11): lo pendiente con el teléfono pasa a la etapa 15**, que él toma como la
    final. Pasan: instalar la última versión, la pasada completa de pantalla con foto de datos, ver el
    «17 °C», probar el Widget quitándolo y agregándolo, y TalkBack. **Sin decidir todavía:** si la
    aceptación final de los 441 casos (14.2) también se hace dentro de la 15 o antes; MAIN recomendó
    hacerla dentro de la 15 pero antes de firmar, porque se firma la versión que se aceptó.
  - **Aviso permanente: hecho y con sus pruebas de pantalla aprobadas en el Samsung (10/11).** Diseño y
    límites en [ETAPA_14_AVISO_PERMANENTE.md](design/ETAPA_14_AVISO_PERMANENTE.md). PC: 1411 pruebas, 0
    fallas; compilación completa aprobada.
  - **Corrida 5 en el Samsung (10/11, autorizada por Joaquín):** QA `547426d9…` + ejecutor `dda5048d…`,
    las 134 de `Stage14Selectors`: **132 ✅ · 1 ❌ · 1 omitida.** La falla es la conocida (horario del
    Widget compacto mínimo al 200%); la omitida, el aviso de prueba con las notificaciones bloqueadas.
    Foto antes y después: 23 bases idénticas, 370 archivos; sólo cambiaron las dos marcas de Android
    (`files/profileInstalled` y `shared_prefs/android.app.ActivityThread.IDS.xml`).
  - **Siguiente: sesión con el dedo de Joaquín**, paso a paso. Va a cambiar preferencias de la app QA
    (avisos, aviso permanente, bloqueo, tema, Widget); se anotan y se devuelven al terminar.
  - Pendiente de su permiso: borrar los 16 `.lck` vacíos del teléfono. Sin decidir: horario del Widget
    compacto al 200%; tercer control del aviso permanente; llevar sus dos opciones a las Copias.
  - **Tanda 2 en el Samsung (10/11):** tras arreglar cuatro defectos, **127 de 129 pasan, 1 falla, 1
    omitida.** La que falla: con letra al 200%, en el Widget compacto mínimo no entra el horario.
  - **Pedidos de Joaquín (10/11), sin empezar:**
    - **Aviso permanente con contador:** hecho en la PC, ver arriba.
    - **Modo claro: un degradado oscureció una tarjeta y no se leía.** Joaquín lo vio una vez el 10/11 y
      no se repitió («sólo fue un bug momentáneo»). Sin reproducir; los colores declarados para claro son
      todos claros. Queda anotado por si vuelve.
    - **Criterio de letra grande:** usable, no pulida; ningún arreglo de letra grande cambia la vista
      normal. Prioridad: que se vea bien con letra normal en cualquier teléfono.
  - Espera decisión de Joaquín, sin bloquear: si el Widget adopta la paleta nueva; si cuentan las
    pruebas de interoperabilidad web.
  - Pendientes heredados de 13.5: ver «⏳» en la lista de 13.5. Después: 15 · Entrega.
- Etapa anterior: **13.5 · Estética y facilidad de uso — CERRADA por Joaquín (9/11)**. Están la lista
  ✅/⏳ y el detalle abajo, y las decisiones en [inventario y decisiones](design/ETAPA_13_5_INVENTARIO.md).
- Etapas anteriores: 13 · Datos y archivos y 12.2 · Cálculos y consulta, CERRADAS por Joaquín (8/11).
- En GitHub: rama `origin/codex/miguardia-3.0` subida el 10/11 a pedido de Joaquín, con pull request
  abierto contra `main` (https://github.com/blackat-systems/MiGuardia/pull/1). No está para mergear:
  la etapa 14 sigue en curso.
- Pareja instalada en el Samsung (10/11): QA `6580449e…` + ejecutor `e9597ece…`
  (`.local-evidence/claude-20261110-14/build11/hashes.txt`). La última corrida completa de las 134 fue
  con `build5`; después sólo se repitieron las clases del Widget. Datos QA: 23 bases, foto
  `claude-20261110-14/after-corrida5/` (10/11), que incluye una jornada del 10/11 cargada a mano por
  Joaquín mientras probaba. **Esa foto es la referencia nueva.** Correr con
  `QA=… RUNNER=… bash .local-evidence/claude-20261110-14/run_classes.sh <salida> [Clase…]`.
- Actualización: 2026-11-10

## Etapa 14.1 · tanda 2: primera corrida en el Samsung (10/11/2026, con autorización de Joaquín)

- **Teléfono:** Samsung SM-S938B, Android nivel 37. Pareja: QA `c9c4f554…` + ejecutor `5dda3cd4…`,
  instaladas encima de las anteriores (sin desinstalar) y verificadas por hash.
- **Corrida:** las 129 pruebas de `Stage14Selectors`, una clase por invocación
  (`.local-evidence/claude-20261110-14/corrida1/`). **122 ✅ · 6 ❌ · 1 omitida.**
- **Datos QA:** foto antes, después de instalar y después de correr: **23 bases idénticas**, ningún archivo
  previo faltante. Diferencias:
  - 16 archivos vacíos nuevos `cache/stage14-widget-*.db.lck`, restos de las bases propias de las pruebas
    del Widget (el mismo resto de Room que en la etapa 13). Borrarlos espera autorización de Joaquín;
    falta que esas pruebas los limpien.
  - cambiaron `files/profileInstalled` y `shared_prefs/android.app.ActivityThread.IDS.xml`. INFERIDO que
    son marcas de Android al instalar e iniciar; la segunda no había aparecido antes.

**Defectos confirmados en el teléfono** (❌ con causa conocida):

- ❌ **Sin conexión, el Widget pide Clima en bucle:** 52 pedidos en 2 segundos
  (`Stage14WidgetEngineTest#failingWeatherIsNotRequestedOverAndOverWithoutANewReason`). Causa en
  `DefaultWeatherRepository.kt:52` + `WidgetRuntime.kt:267`. Sólo con Widget ampliado + Clima + Completa.
- ❌ **Una descarga de Clima en curso frena los redibujados del Widget**
  (`Stage14WidgetEngineTest#aChangeMadeWhileWeatherIsDownloadingIsStillDrawnWithoutWaitingForTheDownload`).
- ❌ **Letra al 200%, vertical:** «Siguient/e» partida en la fila de dos botones del recorrido, en los
  pasos tarjeta de hoy, mes y grilla, y detalle del día, en ambos temas
  (`Stage14HelpScreensTest#tourRowKeepsBothLabelsWholeOnCalendarStepsInPortrait…`). La introducción, la
  lista de temas y el diálogo de omitir pasaron.
- ❌ **Widget en su tamaño mínimo declarado:** con letra normal, en compacto (180×96 dp) se cortan con «…»
  el texto principal y el horario; en ampliado el horario ocupa dos renglones y se ve uno. Con letra al
  150 y 200% se corta también el título (`Stage14WidgetRemoteViewsTest`, dos métodos). Falta decidir si
  el criterio se mide en ese tamaño o en el de la grilla real del Samsung.

**Falla sin causa confirmada (no es defecto de la app hasta reproducirla):**

- ❌ `Stage14AppearanceScreensTest#changingTheModeChangesThePageBackgroundOfTheRealTheme`: al pasar a
  Oscuro, el punto de la página que mira la prueba salió gris claro en vez del fondo oscuro. Ese gris no
  es ningún fondo de la app; INFERIDO que la prueba mira un punto ocupado por otro elemento. Las otras
  tres pruebas de Apariencia pasaron.

**Omitida:** `Stage14NotificationViewModelTest#testNotificationWithPermissionReportsItWasSent`, porque la app
QA tiene las notificaciones bloqueadas en el teléfono. Se cubre en la sesión con el dedo de Joaquín.

**Confirmado como arreglado:** personalizar una jornada con la general sin anticipos
(`Stage14NotificationScreensTest`, 24/24) y el singular de Actualizaciones (`Stage14UpdatesScreenTest`, 8/8).

### Jornada nueva en la base QA (10/11/2026) — EXPLICADA: la cargó Joaquín

- **Hecho (VERIFICADO):** entre la foto `after-corrida2` y la `after-corrida4` la base `miguardia-v2.db`
  ganó una jornada (`shifts` 4 → 5 y `shift_work_snapshots` 4 → 5): fecha 10/11/2026, estado planificada,
  objetivo «AQA», creada el 10/11 a las 00:27:56. Su identificador es del tipo que genera «Cargar
  jornadas» (`V2ManualShiftLoadViewModel.kt:1246` y `:1349`).
- **No fue una prueba:** la corrida anterior había terminado a las 00:25 y la siguiente empezó a las
  00:39. El registro de uso de Android muestra que la pantalla principal de la app QA se abrió a las
  00:27:52, cuatro segundos antes. A las 00:28 se guardó además el tema Claro
  (`shared_prefs/miguardia_display_preferences.xml`, archivo nuevo).
- **Antecedente igual (13.5):** el 9/11 a las 02:10 apareció una jornada para el 9/11, seis segundos
  después de abrirse la app, también con «Cargar jornadas»; Joaquín dijo que no fue él y se quitó con
  «Limpiar día».
- **Resuelto (10/11):** Joaquín confirmó que fue él («estaba probando nada más»). No es un defecto de la
  app y la hipótesis de una carga que se retoma sola queda descartada para este caso. La jornada queda
  en la base QA como dato suyo; no se tocó.
- El antecedente del 9/11 sigue sin explicación propia (esa vez dijo que no había sido él). Si vuelve a
  aparecer una jornada sin que nadie use el teléfono, reproducir con el arnés aislado.
- **Para las próximas sesiones:** si Joaquín usa la app QA entre dos fotos, la comparación va a dar
  diferencias; preguntarle antes de investigarlas.

### Sesión con el dedo de Joaquín (10/11/2026) — hecha, con pendientes

- ✅ **Aviso permanente:** activar avisos, permiso de Android concedido en su diálogo, aviso de prueba,
  aviso de la jornada de hoy sin esperar el anticipo, fijo y con contador, deslizarlo, tocarlo (abre el
  día) y pantalla bloqueada con el interruptor apagado (no aparece bloqueado, sí al desbloquear).
  Confirmado además leyendo el teléfono: publicado fijo, silencioso y, con el interruptor apagado,
  oculto en el bloqueo. ⏳ Falta oír un anticipo sin que se duplique el aviso.
- ✅ **Widget real:** agregarlo, configuración inicial, contenido, cambio de tamaño y toque, «todo
  perfecto» salvo un defecto: **el tamaño vertical por defecto quedaba muy grande, con mucho margen.**
  - Causa: el alto mínimo de 150 dp del arreglo anterior lo obligaba a dos filas en el Samsung (cada
    fila mide unos 107 dp).
  - Arreglo: alto mínimo de vuelta a 96 dp, una celda de alto por defecto, menos relleno vertical y
    **tres tamaños de vista**: una fila (un renglón por texto), compacta alta desde 150 dp (nombre y
    horario en hasta dos renglones) y ampliada.
  - Resignado: en una fila, un nombre largo termina en «…» (completo para TalkBack). Límite conocido sin
    decidir: con letra al 150 y 200% el horario no entra en una fila, ni en la compacta alta al 200%.
  - Pruebas del Widget en el Samsung con QA `ecda8cb7…` + ejecutor de `build7`: 34 de 34. PC aprobada.
  - **Segunda vuelta, con capturas de Joaquín:** el Samsung coloca el Widget a todo el ancho por dos filas
    (405×208 dp) sin importar el mínimo declarado, y ahí quedaban dos renglones chicos en una tarjeta
    grande. Se agregó una **cuarta vista** para ancho ≥ 300 dp y alto ≥ 150 dp, con letra más grande
    (no se agranda si la letra de Android ya es ≥ 130%).
  - **Tercera vuelta:** no dejaba achicarlo a una fila porque una fila del Samsung da 95 dp y el mínimo
    era 96. Mínimo y umbral de la vista de una fila bajados a 80 dp, relleno vertical a 8 dp.
  - ✅ **Joaquín lo vio en Completa, a todo el ancho por dos filas: «quedó bien, por el momento lo
    dejemos así».** Pareja instalada: QA `6580449e…` + ejecutor `e9597ece…` (`build11`); vistas del Widget
    6 de 6 en el Samsung.
  - ⏳ No se vio todavía achicado a una fila ni recién agregado de cero.
- ✅ **Bloqueo de acceso:** activar con huella o PIN, salir y volver (pantalla «MiGuardia está
  bloqueada»), cancelar la autenticación (sigue bloqueada, con su mensaje), Recientes sin mostrar
  pantallas y desactivar. Leído en el teléfono: quedó desactivado, plazo inmediato.
- ✅ **Clima con red real:** el lugar de la jornada no tenía ubicación para el clima; Joaquín se la cambió
  y entonces mostró pronóstico. Quedó un archivo de caché de Clima nuevo.
- ✅ **Actualizaciones:** «Buscar actualización» respondió que no hay versión publicada.
- ✅ **Apoyo:** abrió el navegador en la página para donar y volvió bien.
- **Temas:** Joaquín dijo «todo salió bien»; la preferencia de tema cambió en el teléfono, así que los
  tocó. ⏳ No dijo expresamente si volvió a verse la tarjeta oscura en modo claro.
- ❌→✅ **Clima sin red (defecto encontrado por Joaquín y arreglado):** en modo avión, «Actualizar»
  parecía no hacer nada. Causa: el error se dibuja arriba y el botón está al final de un pronóstico
  largo, así que quedaba fuera de la vista. Ahora la pantalla sube sola hasta el mensaje cuando aparece
  un error (`WeatherScreens.kt`). Prueba nueva
  `Stage14WeatherScreensTest#failedRefreshBringsTheErrorIntoViewFromTheBottomOfALongForecast`; la clase
  dio 17 de 17 en el Samsung con QA `1bd0be8d…` (`build12`). ✅ Joaquín lo repitió en modo avión (captura
  del 10/11, 02:41): se ve «No pudimos conectar. Se conserva el último pronóstico disponible.» con
  «Reintentar» y «Cerrar», y el pronóstico sigue debajo. Observación menor de esa captura: con mínima y
  máxima iguales el resumen decía «17–17 °C» y «16–16 °C». ✅ Arreglado el 10/11 a pedido de Joaquín: una
  sola regla (`roundedTemperatureRange`) para el detalle de Clima, la tarjeta del día y el texto de los
  avisos; ahora dice «17 °C». PC y compilación completa aprobadas; ⏳ sin instalar en el Samsung.
- ⏳ **«Actualizar» con red:** Joaquín dijo «queda igual». La hora de «Última actualización» sí cambió
  (02:35 en su captura), así que la descarga ocurrió; no se confirmó si vio el aviso «Pronóstico
  actualizado.».
- ✅ **Anticipo:** llegó, vibró y sonó.
- ❌ **Widget a una fila:** con el mínimo en 80 dp el Samsung sigue sin dejar bajarlo de dos filas. Leído
  en el teléfono: el proveedor ya declara 180×80 dp. INFERIDO que el inicio guarda los límites al
  agregarlo; falta quitarlo y volver a agregarlo para saberlo.
- ⏳ **TalkBack de oído:** Joaquín no sabe activarlo; queda para otra sesión, con guía.
- **Foto después de la sesión** (`claude-20261110-14/after-dedo/`, referencia nueva): 23 bases, 373
  archivos. Contra la foto anterior cambiaron sólo cosas explicadas: el lugar y el objetivo que Joaquín
  editó (`objectives` y `work_places`, mismas filas, otro contenido); preferencias de avisos, Clima,
  Widget, tema y bloqueo; caché de Clima; cola del Widget; y las dos marcas de Android.
- **Quedó cambiado en la app QA por la sesión** (no se devolvió; Joaquín decide): permiso de
  notificaciones concedido, avisos activados, aviso permanente encendido, «Mostrar en la pantalla
  bloqueada» apagado, Clima habilitado, ubicación del lugar, y un Widget en la pantalla de inicio en
  privacidad Completa.

### Textos del Widget (10/11/2026)

- ✅ Vista compacta: el texto principal y el horario pueden usar dos renglones; vista ampliada: el horario
  también (antes se cortaba sin aviso). Alto mínimo declarado del Widget: de 96 a 150 dp
  (`widget_next_event_info.xml`); el tamaño inicial 3×2 no cambia. Con letra normal, nada se corta en
  el tamaño mínimo (`Stage14WidgetRemoteViewsTest#standardFont…`, pasa en el Samsung).
- **Criterio de letra grande aplicado:** al 150 y 200% el Widget puede terminar un texto en «…», pero cada
  texto tiene que empezar a leerse y TalkBack recibirlo completo. La prueba se cambió a ese criterio.
- ❌ Con ese criterio queda una falla: compacto mínimo (180×150 dp) al 200%, el horario no entra ni en su
  primer renglón. Sin decidir.
- ⏳ Cómo respeta el inicio de Samsung el alto mínimo nuevo sólo se ve agregando el Widget con el dedo.
- **Corrida 4:** QA `6fef3744…` + ejecutor `77114d3e…`, las 129: 127 ✅ · 1 ❌ · 1 omitida. PC:
  `verify.py --profile local --fresh-tests` y `--profile build` aprobados sobre este código.

### Arreglos y segunda corrida (10/11/2026, con el «sí, arreglalos» de Joaquín)

- ✅ **Bucle de Clima del Widget:** el motor ya no observa la hora del último intento ni la espera del
  proveedor, sólo lo que cambia el dibujo (`WidgetRuntime.kt`). La prueba pasó en el Samsung.
- ✅ **Redibujado frenado por la descarga:** leer el último pronóstico ya no espera a una descarga en
  curso (`WeatherRuntime.latestForShift`). La prueba pasó en el Samsung.
- ✅ **«Siguiente» al 200%:** la fila de dos botones de la introducción de Ayuda y del recorrido se apila
  cuando un rótulo no entra en su mitad; con letra normal queda igual que antes (`HelpStepActions`). Las
  8 pruebas de Ayuda pasaron.
- ✅ **La falla de Apariencia era de la prueba:** con el diagnóstico agregado, toda la página estaba en el
  color oscuro correcto y sólo la franja inferior salía gris claro. INFERIDO que es la barra de
  navegación de Android sobre la pantalla de prueba, que no fija el estilo de barras de la app. La prueba
  ahora mira el borde izquierdo: 4 de 4.
- ✅ **Restos:** las pruebas del Widget borran su archivo de bloqueo; la segunda corrida no dejó ninguno.
- **Corridas:** `corrida2` con QA `64cbb2e5…` + ejecutor `b1fe2bd1…`: 125 ✅ · 3 ❌ · 1 omitida. `corrida3`,
  sólo Apariencia con el ejecutor `aa061a79…` (cambia únicamente esa prueba): 4 de 4. Sumado: **126 ✅ ·
  2 ❌ · 1 omitida** sobre la misma app QA.
- **PC:** `verify.py --profile local --fresh-tests`, 1393 pruebas, 0 fallas. `verify.py --profile build`
  no se repitió después de estos arreglos.
- **Datos QA:** foto después de la segunda corrida: 23 bases idénticas, 369 archivos, sólo cambió
  `files/profileInstalled`. Siguen en el teléfono los 16 `.lck` vacíos de la primera corrida; borrarlos
  espera autorización de Joaquín.
- ❌ **Sigue abierto:** textos cortados del Widget en su tamaño mínimo declarado (2 pruebas).

## Etapa 14.1 · tanda 1: pruebas de PC y de pantalla (9–10/11/2026)

**Hecho, sin teléfono.** Seis ayudantes, uno por superficie, con archivos propios y el contrato
`docs/design/ETAPA_14_CONTRATO_AYUDANTES.md`. Ninguno corrió Gradle ni usó el teléfono; MAIN compiló,
revisó y corrió.

- ✅ **PC:** `verify.py --profile local --fresh-tests`, **1392 pruebas, 0 fallas** (eran 1349; 43 nuevas).
- ✅ **Compilación completa:** `verify.py --profile build` aprobado (lint, Debug/QA/Release y los dos APK
  de pruebas).
- ⏳ **129 pruebas instrumentadas nuevas** en `app/src/androidTest/.../stage14/`: compilan; **no corrieron**
  (necesitan el Samsung). Son de pantalla con estado ficticio o aisladas con base y preferencias propias.

**Cambios en la app (aprobados por Joaquín el 9/11):**

- Avisos por jornada: si la configuración general no tiene anticipos, «Personalizar esta jornada» arranca
  con las 12 horas recomendadas y abre el editor. Antes dejaba la jornada desactivada y no abría el
  editor (`NotificationScreens.kt`, `ownShiftStartingReminders`).
- Actualizaciones: «Podés reintentar en 1 segundo.» en singular.
- Sin cambio de conducta, para poder probar: `WeatherRuntime` acepta un cliente de red de prueba y
  `WidgetRuntime` recibe su dibujante por la interfaz `WidgetSurface`.

**Arreglos de pruebas:** `BetaCoordinatorTest` (tres métodos leían el último pedido antes de que saliera
el envío esperado; era la prueba intermitente anotada en 13.5) y un texto viejo en `WeatherComposeTest`.

**Posibles defectos, leídos en el código y todavía sin ejecutar** (cada uno tiene su prueba, que afirma lo
correcto y debería fallar en el Samsung si la lectura es cierta):

- ⏳ **Sin conexión, el Widget pediría Clima en bucle.** Cada intento anota su hora en las preferencias de
  Clima (`DefaultWeatherRepository.kt:52`), el motor del Widget las observa y vuelve a pedir
  (`WidgetRuntime.kt:267`). Sólo con Widget ampliado + Clima + privacidad Completa.
  Prueba: `Stage14WidgetEngineTest#failingWeatherIsNotRequestedOverAndOverWithoutANewReason`.
- ⏳ **Una descarga de Clima lenta frena los redibujados siguientes del Widget.**
  Prueba: `Stage14WidgetEngineTest#aChangeMadeWhileWeatherIsDownloadingIsStillDrawnWithoutWaitingForTheDownload`.
- ⏳ **Letra al 200%:** «Siguiente» partida dentro de la palabra en las filas de dos botones de Ayuda y del
  recorrido, y títulos largos de la lista de Ayuda (`Stage14HelpScreensTest`).
- ⏳ **Widget en su tamaño mínimo:** título, texto principal y horario cortados con «…»
  (`Stage14WidgetRemoteViewsTest`). Falta decidir si el criterio se mide en ese tamaño.

**Observaciones menores, sin decidir:**

- El aviso de prueba dice «Prueba enviada…» aunque no se publique por estar la app en recuperación.
- TalkBack no distingue cuál de los botones 6/8/12/24 h está elegido, salvo por el resumen.
- «Temporalmente no disponible» del Bloqueo sólo aparece si falla abrir los ajustes de seguridad.
- Reintentar la lectura del Bloqueo con una reparación abierta: si después se autentica la reparación,
  escribe «desactivado» sobre una configuración sana (exige autenticarse).

**No se pudo cerrar sin teléfono:** el aviso visible de «no hay navegador» al abrir el apoyo; jornada
eliminada tocada desde el Widget; restauración de Widgets; todo lo marcado **Dedo** en el mapa.

**Entorno de esta PC:** `local.properties` tenía la ruta del SDK sin escapar y el lint la rechazaba; quedó
`sdk.dir=C\:/Users/User/Android/Sdk`. La caché de Gradle siguió devolviendo el error viejo hasta correr
`verify.py --profile build --rebuild` una vez.

## Etapa 13.5 · Estética y facilidad de uso — CERRADA por Joaquín (9/11/2026)

**Lista de la etapa** (✅ probado · ⏳ falta · ❌ falla). Ninguna ❌.

- ✅ **Inventario y propuesta aprobados por Joaquín** (`docs/design/ETAPA_13_5_INVENTARIO.md`). Además,
  decisiones de Joaquín en la etapa:
  - paleta Tokyo Night (Night con acento azul);
  - esquinas asimétricas;
  - estilo v2 con Plus Jakarta Sans;
  - Horario real sin UUID visible;
  - «Volver al planificado» en rojo.
- ✅ **Selector nativo en los 22 campos**, conservando la escritura.
  - Probado en PC y Samsung: igual a escribir, Cancelar no cambia nada, 00:00 y 23:59, cruce de
    medianoche, nunca guarda solo, y botón usable con letra al 200%.
  - Vacaciones: calendario en es-AR, sin día elegido si la fecha está vacía.
- ✅ **Todas las pantallas pulidas en estilo v2**, sin funciones, estados ni pantallas perdidas.
  - Revisión independiente del diff sin defectos graves; el defecto medio se corrigió.
  - Capturas antes/después de Disponibilidad (claro, oscuro, letra 100 y 200%, con los selectores)
    y del Calendario, Clima y Actualizaciones, en `.local-evidence/claude-20261108-13-5/capturas/`.
- ✅ **PC:** `verify.py --profile local`, 1349 pruebas, 0 fallas.
- ✅ **Samsung:** 292/292 pruebas de pantalla de la etapa; regresión de 12.2 15/15, 13 16/16 y
  Horario real 14/14.
- ✅ **Prueba humana de los selectores** (Joaquín, 9/11): «en términos generales anda bien, está
  utilizable». La foto de datos posterior es idéntica: no se guardó nada.
- ✅ **Datos QA:** 23 bases idénticas en cada sesión. Diferencias explicadas:
  - la jornada cargada a mano el 9/11, quitada desde la app, con una marca de «día sin definir» que
    queda;
  - capturas de prueba copiadas y borradas con autorización.
- ⏳ **Mejoras posteriores** que Joaquín quiere hacer («después mejoramos ciertos aspectos»): sin
  detalle todavía; se definen cuando las pida.
- ⏳ **Observaciones menores para la etapa 14:**
  - un selector abierto se cierra si gira la pantalla y cambia la disposición;
  - filas de dos botones de Ayuda y del recorrido, y sugerencias de nombre de Tipos de horas
    extra, a mirar con letra al 200%;
  - prueba intermitente `BetaCoordinatorTest#lostResponse…`;
  - si el Widget adopta la paleta nueva;
  - TalkBack sobre las pantallas nuevas, con escucha humana.
- ✅ **Push a GitHub** (9/11, a pedido de Joaquín): `ac2b6c0..7fef1c5`.

## Etapa 13.5 · resto de pantallas y selectores (9/11/2026)

**Hecho, sin teléfono todavía:**

- **Quién lo hizo:** 6 ayudantes, cada uno con archivos propios y el contrato
  `docs/design/ETAPA_13_5_CONTRATO_AYUDANTES.md`.
  - Pantallas: Horas; Horario real y Editar jornada; Mi forma de trabajar y Repetir jornadas;
    Detalle del día, diálogos del Calendario y Carga manual; Feriados, Vacaciones, Fotos, Resumen
    e Informes; Copias, Bloqueo, Apariencia, Ayuda, Actualizaciones y diálogos de
    Avisos/Clima/Widget.
  - Ninguno corrió Gradle ni usó el teléfono. MAIN integró, compiló y revisó.
- **Selectores:** los 22 campos del inventario tienen selector, conservando la escritura.
  - Vacaciones ya tenía calendario: ahora usa es-AR (semana desde el lunes) y, con la fecha
    vacía, sigue exigiendo elegir un día.
- **Encabezado común:** `SurfaceHeader` acepta `navigationEnabled` y `navigationTestTag`. Con eso,
  Editar jornada recupera `v2-shift-edit-back` y el botón deshabilitado mientras guarda, y Repetir
  jornadas también lo deshabilita mientras carga o guarda. `MonthNavigator` acepta etiquetas de
  prueba.
- **Pruebas:** `Stage12CalendarExceptionsTest.chooseVacationDate` ahora escribe la fecha en orden
  es-AR. `Stage135Selectors` queda regenerado: 34 pruebas propias más 258 de regresión de pantalla,
  todas con datos ficticios. Quedan fuera las que escriben en los datos de la app QA
  (`Stage4CalendarMatrixTest` y `ReportsComposeTest#surfaceRemainsReachable…`). Como el ejecutor
  acepta hasta 256 por corrida, hay que correrlas en dos tandas.
- **Revisión independiente del diff:** ningún defecto grave. Se corrigió uno medio: el calendario
  de Vacaciones daba por elegido el día de hoy. Lo demás quedó anotado como menor:
  - el orden de los campos del extra cambió (son las dos horas juntas, como en la maqueta);
  - un selector abierto se cierra si gira la pantalla y cambia la disposición;
  - el ancho de los diálogos de Clima y Widget pasó a 620 dp;
  - los errores del Resumen ahora son un solo mensaje.
- **Controles:** ninguna etiqueta de prueba borrada; sin colores ni tamaños de letra fijos nuevos.
  PC: 1349 pruebas, 0 fallas.
- **Samsung (9/11, con autorización de Joaquín): 292/292 ✅.**
  - Pareja: QA `56e3368a…` + ejecutor `9db1bd10…`; hashes en `.local-evidence/claude-20261108-13-5/v3/`
    (`build/` y `build2/`).
  - Tandas: la primera dio 144/146 y la segunda 146/146.
  - Las 2 fallas eran de la prueba nueva del aviso de cancelación, no de la app: leía un nodo
    dentro de `runOnIdle`. Corregida, la clase dio 6/6.
- **Antes de instalar, la foto de datos no coincidió con la del inicio de la etapa.** Había una
  jornada nueva el 9/11, de 08:00 a 09:00, creada a las 02:10.
  - El registro de uso de Android muestra que una persona abrió la app QA a las 02:10:30
    (motivo `u-mf`) y que la jornada se guardó 6 segundos después, con «Cargar jornadas». No corrió
    ninguna prueba ni ningún ayudante (cero comandos adb o Gradle en sus registros). Joaquín dijo
    que no fue él.
  - Por decisión de Joaquín se quitó desde la propia app QA: «Editar día» → «Limpiar día», con la
    revisión, que mostraba sólo esa jornada.
  - Las jornadas volvieron a ser idénticas a las del inicio de la etapa. Quedó una marca de «día sin
    definir» explícito para el 9/11 (`explicit_day_statuses`: 1 fila). La app no tiene cómo quitarla
    sola y no se tocó la base por fuera.
  - Esa foto (`v3/after-delete/`) es la referencia de la sesión. Contra ella, la foto posterior da
    23 bases idénticas.
- **Restos de pruebas:** 30 capturas en `cache/` de la app QA, dejadas por
  `WeatherComposeTest#credits…` y `UpdatesScreenTest`. Se copiaron a
  `capturas/v3-cache/` y esas dos pruebas salieron de `Stage135Selectors`.
  - Con autorización de Joaquín (9/11) se borraron sólo esas dos carpetas: se controló antes que
    tuvieran exactamente las 30 capturas copiadas.
  - Foto posterior (`v3/after-cleanup/`): 23 bases idénticas a la referencia y 353 archivos, sin
    faltantes ni sobrantes.
- **Pendiente:**
  - mirar con la letra al 200% las filas de dos botones de Ayuda y del recorrido, y las sugerencias
    de nombre de Tipos de horas extra;
  - ~~decisiones de Joaquín~~ **decididas el 9/11:**
    - Horario real ya no muestra «UUID: …», ni de la jornada ni de los fragmentos; el dato sigue
      guardado.
    - «Volver al planificado» es rojo tonal, con la misma confirmación.
    - Prueba: `V2ShiftActualComposeTest#internalIdsAreHiddenAndReturnToPlannedStaysAConsciousConfirmation`.
  - ~~regresión de 12.2 y 13~~ **hecha en el Samsung (9/11): 12.2 15/15 ✅, 13 16/16 ✅ y Horario real
    14/14 ✅**, sin las sesiones humanas.
    - Pareja: QA `ef83068d…` + ejecutor `14b1e066…` (`regresion/build/hashes.txt`).
    - Foto antes y después: 23 bases idénticas y ningún archivo nuevo.
  - prueba del selector con el dedo de Joaquín (pasos guiados, sin guardar).

## Etapa 13.5 retomada (8/11/2026) — avance sobre la pausa

Joaquín pidió «retomá la 13.5». Ya se hicieron los pasos 1 a 7 de la lista de abajo; quedan en el
commit `180201e`.

- **Paso 1, símbolos de la fuente:**
  - a Plus Jakarta Sans le faltan 18 de los 42 símbolos no ASCII que usa la app (✓, ☰, ⋯, ☁, ♡, ↻
    y otros parecidos usados como íconos);
  - Android los toma de la fuente del sistema, así que se ven igual que antes;
  - → ‹ › · ° … sí están.
- **Pasos 2 a 6:**
  - fuente en `res/font`, licencia en `docs/licencias/` (el `.ttf` también trae el aviso OFL en sus
    metadatos);
  - paleta v2;
  - componentes v2;
  - Disponibilidad v2: horas lado a lado, que se apilan si no entran, fecha completa y «+1 día»;
  - tarjeta de próxima jornada con el horario como dato grande;
  - brillo de fondo en el Calendario.
- **Cambios de color forzados por `VigiliaContrastTest`:**
  - el azul claro pasó de #3D6FE0 a #2F5DC6, porque con el blanco del estado presionado no
    llegaba a 4,5:1;
  - la ayuda oscura pasó de #8F98C4 a #959ECA, porque sobre la tarjeta de próxima jornada daba
    4,34:1;
  - el final del degradado del botón en claro es #6548CE.
- **Decisión técnica:** el título no lleva un color propio en el estilo de letra, porque le ganaría
  al color de botones y celdas. La jerarquía la marcan el peso (800 y 700) y los secundarios más
  apagados.
- **PC:** 1349 pruebas, 0 fallas en la primera corrida completa.
  - **Observación para la etapa 14:** en otra corrida falló una vez
    `BetaCoordinatorTest#lostResponseRestartAndIdempotentRetryKeepExactBodyThenEraseMessage`, con
    un `IllegalArgumentException`. Es código beta que no se tocó, y sola pasó 3 de 3.
  - Es INFERIDO que se trata de una carrera de tiempos: no está reproducida.
- **Paso 8 hecho (Samsung, 8/11, con autorización de Joaquín): 75/75 ✅.**
  - Pareja: QA `034ce9ef…` + ejecutor `16ba5434…`; hashes en
    `.local-evidence/claude-20261108-13-5/v2/final/hashes.txt`.
  - Capturas en `capturas/v2-final/` (28) y matriz del Calendario en `capturas/calendario-matriz/`
    (48 imágenes).
- **Errores encontrados y corregidos en esta sesión:**
  - **Texto negro en la tarjeta de arriba del Calendario, en oscuro** (lo vio Joaquín). El
    contenedor transparente del Calendario no pasaba color de texto. Ahora lo pasa, y además
    `HeroCard` define el suyo. Prueba nueva: `Stage135HeroContentColorTest`.
  - **Letra al 200%: título «Disponibilid/ad» cortado y el botón del calendario que no abría.**
    Ahora el encabezado sube «Cerrar» y corta sólo entre palabras. «+1 día» pasó a la línea de
    ayuda del campo. Prueba nueva:
    `AvailabilityComposeTest#largeFontKeepsHeaderWordsWholeAndTheCalendarButtonUsable`.
    Antes del arreglo falló contra la versión anterior («Header split inside a word at 12») y
    después pasó.
  - La captura fallida del selector no era culpa de Joaquín usando el teléfono, como se supuso
    primero: falló dos veces igual, y la foto de la pantalla completa mostró la causa.
- **Datos QA:** 23 bases idénticas a las del inicio de la etapa. Quedaron **50 archivos nuevos en
  `files/stage4/`**, las capturas que guarda `Stage4CalendarMatrixTest`, una prueba que se sumó a
  la regresión. Se copiaron a la PC y, con autorización de Joaquín (8/11), se borró sólo
  `files/stage4`: se controló antes que tuviera exactamente esos 50 archivos y que no existiera
  en la foto previa. Foto posterior (`v2/final/after-cleanup/`): 23 bases y 353 archivos,
  idénticos a antes de la sesión, salvo `files/profileInstalled`.
  **Ojo en próximas corridas:** `Stage4CalendarMatrixTest` vuelve a escribir en `files/stage4`;
  después de cada corrida hay que copiarlo y pedir autorización para borrarlo.
- **PC:** 1349 pruebas, 0 fallas.

## ⏸ PAUSA — Etapa 13.5 (8/11/2026, pedido de Joaquín: «frenamos acá»)

**Retomar exactamente desde acá.** Lo hecho está en commits locales sobre `origin`, sin push:
`c983166` (selectores y Disponibilidad), `c2f7f32` (paleta Tokyo Night) y `7343cea` (esquinas
asimétricas), más el commit de este registro. Árbol limpio. En el Samsung quedó instalada la pareja
de «esquinas»: QA `d6f9a0f1…` y ejecutor `7e278f06…`. Los hashes completos están en
`.local-evidence/claude-20261108-13-5/esquinas/build/hashes.txt`. Los datos QA quedaron intactos:
23 bases idénticas a la foto inicial de la etapa.

**Qué estábamos haciendo: «estilo v2».** Joaquín pidió que la app se vea «linda, canchera y placentera
de ver».

- Se armó la maqueta `.local-evidence/claude-20261108-13-5/maquetas/estilo-v2.html`, con Calendario y
  Disponibilidad en oscuro y claro y un selector de tipografía.
- Joaquín eligió **Plus Jakarta Sans** y aprobó la maqueta **tal cual**, con degradados y brillo.
- El contraste de la paleta v2 ya está medido: todos los textos dan 4,5:1 o más. El más justo es el
  botón claro: blanco sobre #3D6FE0 da 4,62. Script: `.local-evidence/claude-20261108-13-5/contraste.py`.
- **Fuente descargada y no incluida todavía en la app:**
  - archivos en `.local-evidence/claude-20261108-13-5/fuente/`: `PlusJakartaSans-wght.ttf`, variable
    de 200 a 800, 176 KB, sha256 `89b3fb38…`, de `raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/`;
  - licencia `OFL.txt`, SIL OFL 1.1;
  - tiene números de ancho fijo (`tnum`) y eje de peso;
  - comprobado: ñ, Ñ, vocales con tilde, ü, ¿, ¡, – y —;
  - **falta comprobar** →, ‹, ›, ✓, ·, ° y … (el control se cortó por un error de consola de Windows).

**Pasos que siguen, en orden:**

1. Terminar el control de glifos. Si falta alguno de los que usa la app, como ‹ › del navegador de
   meses o ✓, decidir: se puede usar un ícono o dejar que Android tome ese carácter de otra fuente.
2. Copiar la fuente a `app/src/main/res/font/plus_jakarta_sans.ttf` y la licencia a
   `docs/licencias/PlusJakartaSans-OFL.txt`. En `Theme.kt`, `FontFamily` con
   `Font(R.font.plus_jakarta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(n)))`
   para 400, 500, 600, 700 y 800, y `tnum` en todos los estilos, como ahora. Pesos de la maqueta:
   títulos de pantalla 800, títulos de tarjeta 700, rótulo 700 en mayúsculas con espaciado, botones 700.
3. **Paleta v2** en `Theme.kt` y `VigiliaTokens.kt` (valores en la maqueta):
   - Oscuro: página #13141C, tarjeta #1E2030 (con degradado a #22253A y borde #2A2E45), campo #161824,
     título #EEF1FF, texto #C0CAF5, ayuda #8F98C4, rótulo violeta #BB9AF7, rótulos de estado azul
     #89B4FA/#26304F, violeta #C8A8FF/#2E2650 y verde #A9DC76/#22362A, eliminar #FF8FA3/#33202B,
     caja de fecha #B4F9F8/#10324A.
   - Claro: página #F4F5FA, tarjeta #FFFFFF (borde #E4E7F2 y sombra suave), título #171B30, texto
     #3B4261, ayuda #5B6180, violeta #6D4FD6, botón con degradado #3D6FE0→#6D4FD6 y letra blanca,
     eliminar #A8304B/#FCE8EC.
   - Repetir `VigiliaContrastTest` y `VigiliaThemeComposeTest`, actualizando los valores esperados.
4. **Componentes comunes** (`VisualSystem.kt`):
   - encabezado con flecha y «Cerrar» arriba, rótulo violeta y título grande;
   - `PrimaryAction` con degradado y forma asimétrica 18/6, de 52 dp;
   - secundarios tonales y destructivo tonal;
   - fondo de pantalla con brillo radial suave arriba a la derecha;
   - etiquetas con forma asimétrica 10/3.
5. **Disponibilidad:**
   - horas grandes lado a lado (Inicio → Final), con el reloj dentro del campo;
   - etiqueta «+1 día» calculada sólo con fecha final menos fecha dueña;
   - **no** se agrega la duración en el editor, que ya se muestra en la revisión: calcularla en la
     pantalla con la zona horaria sería lógica nueva;
   - conservar textos y etiquetas que buscan las pruebas; si un rótulo cambia, actualizar la prueba
     sabiendo qué cubría.
6. **Calendario:**
   - tarjeta de próxima jornada con caja de fecha, horario grande y «+1 día»;
   - grilla con celdas asimétricas y colores v2;
   - respetar las decisiones 0042 y 0043: mes completo, 48 dp, letra de Android y la omisión de
     información secundaria accesible.
7. PC: `python tools/verify.py --profile local` (hoy 1344 pruebas, 0 fallas).
8. Samsung, **pidiendo autorización de nuevo**: foto de datos QA, compilar e instalar, y correr
   `.local-evidence/claude-20261108-13-5/run.sh` con los selectores de `Stage135Selectors` en una
   sola invocación. Después capturas (la prueba de capturas guarda en `Android/data/…qa/cache/stage13-5-captures`),
   foto de datos y commit.
9. Después, lo que ya estaba pendiente de la etapa: pulir el resto de las pantallas según
   `docs/design/ETAPA_13_5_INVENTARIO.md`, poner el selector en los 18 campos restantes, la prueba
   del selector con el dedo de Joaquín y la regresión de 12.2 y 13 en el teléfono.

**Decisiones tomadas en esta etapa:** paleta Tokyo Night (Night + azul), esquinas asimétricas
(opción C), estilo v2 tal cual y Plus Jakarta Sans incluida en la app (licencia OFL, sin red). El
detalle está en `docs/design/ETAPA_13_5_INVENTARIO.md`, secciones 0 y 0.b. **Pendiente para la etapa 14:**
decidir si el Widget adopta la paleta nueva.

## Etapa 13 · Datos y archivos (desde el 8/11)

**Cómo se prueba.** La app real (MiGuardiaApp, ViewModels, Room y **Copias**) sobre una base y una carpeta
propias de cada corrida (`stage13-<id>`), con la fixture de 12.2. Copias usa un coordinador construido sobre
esa base, nunca el de la Application (base QA real). Preferencias de la corrida con su propio nombre y
borradas al terminar. Los selectores los contesta la prueba con archivos de la corrida por el FileProvider
real (`Stage13Pickers`); la sesión humana usa los selectores reales. Arnés: `Stage12CalculationStore`
(parámetros `tagPrefix`, `allowedEffects = photos.import`) y `stage13/Stage13Support.kt`. Selectores en
`Stage13DataSelectors`. Correr con `run_methods_diag.sh` y `RUNNER=<hash del ejecutor>`.

**Resultado en el Samsung (8/11):** QA `7dc36052…` (sin cambios) + ejecutor `07063d22…`: **12/12 ✅**;
sesión humana con ejecutor `ac66cf6f…`: **6/6 pasos ✅**; regresión de 12.2 con el arnés cambiado y el mismo
ejecutor `ac66cf6f…`: **15/15 ✅**. Foto de datos QA antes/después: **23 bases idénticas, ningún archivo previo
faltante**; sólo cambió `files/profileInstalled` (marca de Android al instalar); galería sin imágenes de prueba. Evidencia en `.local-evidence/claude-20261108-13/`.

**Lista de la etapa** (✅ probado · ⏳ falta · ❌ falla). Ninguna ❌.

*Notas privadas (A189–A197, C078, C079)*
- ✅ A189 jornada gemela (misma fecha, horario y objetivo, otro UUID) no ve la nota de la otra; borrar la
  suya no toca la otra — Samsung.
- ✅ C078 vacía o con espacios no se guarda; A190 crear; A191 editar con mismo id y fecha de creación;
  A197 cancelar edición y descartar con confirmación («Seguir editando» conserva) — Samsung.
- ✅ A196 eliminar exige confirmación; cancelarla no borra — Samsung.
- ✅ A192–A194, C079 dos imágenes elegidas, abrir, quitar una del borrador con confirmación, guardar: bytes
  idénticos y la quitada no queda guardada; selector cancelado no cambia nada — Samsung (y selector de fotos
  real en la sesión humana).
- ✅ A195 si la nota se borra por fuera mientras se edita, guardar no la recrea («La nota ya no existe.») y
  el borrador queda — Samsung. Doble guardado y acuse perdido — pruebas existentes de PC.
  Observación: si otra pantalla *edita* (no borra) la nota, al guardar gana el último; no hay control de
  versión del texto. Es la conducta actual; no se cambió.

*Fotos del mes (A198–A207, C095)*
- ✅ A198, C095 se abre en el mes visible del Calendario y al cerrar vuelve al mismo mes; A199 cambiar de mes.
- ✅ A200 importar dos imágenes con bytes exactos (y con el selector de fotos real en la sesión humana);
  A202 asociar al objetivo activo; A203 quitar objetivo sin borrar el archivo; A204 reemplazar conserva el id
  y toma los bytes nuevos (el archivo viejo se retira); A205 eliminar con confirmación (cancelar no borra);
  A206 EXIF ROTATE_90 / ROTATE_270 se ve girada al importar y al reemplazar — Samsung.
- ✅ A207 foto cuyo archivo falta: «No se pudo mostrar la foto» sólo en su tarjeta y en el visor, sin mostrar
  otra; al volver el archivo y reabrirla, se lee la misma — Samsung.
- ✅ A201 pinza y desplazamiento en el visor real — sesión humana; Joaquín confirmó a la vista que ampliar y
  arrastrar anduvieron bien (8/11). Gestos en Compose — prueba existente.

*Copias y restauración (A166–A188)*
- ✅ A166 abrir; A167 con y sin fotos (la copia declara INCLUDED u OMITTED); A168 cifrado por defecto; A169
  7 caracteres o confirmación distinta no habilitan, 256 sí; A170 mostrar/ocultar sólo cambia la vista; la
  contraseña no aparece en ningún archivo ni preferencia de la corrida; A171 sin cifrado sólo tras la
  advertencia; A172 copia coherente (0 nuevos, 0 conflictos al reabrirla); A173 cancelar el selector no deja
  archivos ni cambia datos — Samsung (y selector de documentos real: cancelar, guardar en Descargas y abrir).
- ✅ A174 elegir copia (y selección cancelada); A175–A176 contraseña incorrecta, archivo alterado, truncado y
  no admitido: error visible, sin vista previa ni escritura; A177 vista previa (Room V8, 3 imágenes, nuevos,
  conflictos) antes de escribir — Samsung.
- ✅ A178–A181 combinar con tres conflictos (nota, foto y jornada superpuesta): conservar actual, usar copia y
  conservar ambos, cada uno aplicado; bytes de imágenes sin cambios — Samsung.
- ✅ A182 reemplazar todo sólo con la frase exacta (minúsculas o incompleta no habilita); A187 devuelve la Copia
  exacta (base, preferencias y bytes) aunque la imagen actual tenga los mismos metadatos y otros bytes; A186
  vuelve al Calendario con los datos restaurados — Samsung.
- ✅ A183 otra línea temporal: combinar bloqueado, reemplazar disponible; salir no escribe — Samsung.
- ✅ A184 durante la aplicación no hay cancelar, «Volver» está deshabilitado y Atrás no sale; A185 recuperación
  pendiente: error con Reintentar (nunca éxito), un reintento que falla sigue sin éxito, el siguiente termina y
  deja la Copia exacta — Samsung (fallas inyectadas en el coordinador de la corrida).
  Observación: con la recuperación pendiente, Atrás del sistema cierra la Activity (la app pasa a segundo
  plano); no vuelve a los datos. INFERIDO que al reabrir aparece la recuperación: no probado acá.
- ✅ A188 copias genuinas V5, V6 y V7 (mismas fixtures que `Stage11RestoreInstrumentedTest`) se abren por la
  pantalla real, muestran su Room de origen, bloquean combinar (otra línea temporal) y con reemplazo consciente
  quedan exactamente en la forma V8 declarada aparte (columnas, filas, sin tablas inventadas) y con los bytes de
  sus imágenes — Samsung, `Stage13LegacyCopiesDeviceTest` 3/3 con ejecutor `e86d06c7…`. V8 — arriba.
  (La suite Stage11 original exige su propio ejecutor para emulador; no se corrió en el Samsung.)
- Interrupción real del proceso durante la aplicación: cubierta con fallas en cada punto del registro de
  recuperación (pruebas existentes) y con la falla inyectada de arriba; no se mató el proceso.

**Hallazgo corregido en la app (8/11, pedido de Joaquín):** cada vez que la app abría o aplicaba una Copia creaba una
base candidata temporal y la borraba, pero Room dejaba un archivo vacío `cache/miguardia-backup-candidate-<id>.db.lck`
que se acumulaba. Ahora `BackupDatabaseGateway` borra también ese bloqueo, y el barrido que ya hacía al abrir la base
retira los bloqueos huérfanos de versiones anteriores (sin tocar nombres que no son suyos). Pruebas: el barrido en
`BackupDatabaseGatewayInstrumentedTest` (compila; no se corrió en el teléfono, usa otro paquete de prueba), la
limpieza del arnés ahora **falla** si queda una candidata o un bloqueo, y `orphanCandidateLockIsSweptByTheNextStore`.
Samsung con QA nueva `71523f97…` + ejecutor `1c22148b…`: **16/16 ✅** (las 12 de la etapa, el barrido y V5–V7),
ninguna candidata sobrante; foto de datos QA antes/después de instalar y de probar: 23 bases idénticas, sólo cambió
`files/profileInstalled`. Pruebas de 12.2 y sesión humana no repetidas: no usan Copias (12.2) o sus selectores no
cambiaron (humana). Los 41 bloqueos de las corridas anteriores se borraron con autorización de Joaquín (8/11).

Escrito en castellano llano. Los códigos raros están traducidos en el [glosario](GLOSARIO.md).

## En una frase

Estamos en la etapa **12.2 · Cálculos y consulta**. Con una misma fixture, «Tus horas y tu meta»,
Resumen, el XLSX real y el PDF real muestran los mismos minutos, en la PC y en el Samsung, y los
retornos conservan el mes. Guardar y compartir con los selectores reales pasó con Joaquín (8/11). La
segunda tanda (editores de Horas, clases y extra independiente) y la tanda final (desgloses, interruptores,
fotos y conflictos al guardar) también pasaron. **Corrida final: 15/15 en el Samsung**; la etapa cumple sus
condiciones de «lista» y **Joaquín la dio por cerrada el 8/11**.

## Etapa 12.2 · Cálculos y consulta (desde el 8/11)

**Cómo se prueba.** Una sola fixture para todo
(`app/src/testSupport/.../stage12/Stage12CalculationFixture.kt`): septiembre 2026, meta fija mensual de
20 h, jornada diurna 8 h con 1 h extra que cuenta, nocturna 22:00–06:00 de 8 h, extra independiente de
3 h 30 min que no cuenta, disponibilidad de 12 h aparte, carpeta médica con nota y textos canario de
privacidad. Valores esperados escritos a mano: 16 h sin extras, 4 h 30 min de extras, 20 h 30 min total,
17 h que cuentan, faltan 3 h.
- PC: `Stage12CalculationConciliationTest` (Horas = Resumen = proyección del informe = XLSX real; lista
  blanca con opciones privadas apagadas y encendidas; opciones privadas no cambian números; mes abierto
  igual) y `SummaryViewModelTest` (mes anterior/siguiente/actual exactos, cruce de año, recreación).
- Samsung: `Stage12CalculationsDeviceTest` (5 métodos) monta la app real (`MiGuardiaApp`, ViewModels y Room
  reales) sobre una **base propia de cada corrida** (`stage12-2-<id>.db` + carpeta privada), con reloj
  anclado al 30/9/2026; se borra al terminar. No abre ni modifica los datos QA. Arnés:
  `Stage12CalculationStore.kt`. Correr con `run_methods_diag.sh` (ver «Retomar aquí»).

**Resultado en el Samsung (8/11): 5/5 ✅** con QA `7dc36052…` + ejecutor `327fec25…`, repetidas 5/5 con el
ejecutor final `0d394c22…` (carpeta privada movida a `files/reports/artifacts/stage12-2-<id>` para poder
compartir, y limpieza reforzada). Sesión humana ✅ con `7cfaaa24…`.
**Tanda final y corrida completa (8/11):** las 15 pruebas automáticas de la etapa (7 de cálculos, 8 de
editores) sobre QA `7dc36052…` + ejecutor `ed25efe4…`: **15/15 ✅**; foto de datos QA antes/después idéntica
(23 bases, 353 archivos), sin restos. Evidencia en `.local-evidence/claude-20261108-12-2c/`.
Observación (INFERIDO, sin verificar en el código): si se toca «Guardar meta» justo mientras la pantalla
recarga datos cambiados por fuera, el toque no hace nada ni muestra aviso; un segundo toque sí avisa. No
pone en riesgo datos (no escribe). Queda anotada para la etapa 14 (aceptación).
**Segunda tanda (8/11):** `Stage12HoursEditorsDeviceTest` (6 métodos). Corrida final junto con las 5
anteriores sobre QA `7dc36052…` + ejecutor `f300fe81…`: **11/11 ✅**. Foto de datos QA antes/después: 23 bases
idénticas, ningún archivo faltante ni nuevo; sólo cambió `files/profileInstalled` (marca de Android al
instalar). Sin restos `stage12-2`. Evidencia en `.local-evidence/claude-20261108-12-2b/`.
Foto de datos QA antes/después: 23 bases idénticas, ningún archivo faltante ni cambiado. Quedaron 20
archivos vacíos `cache/stage12-2-<id>.db.lck` de corridas anteriores a corregir la limpieza (ya corregida;
borrarlos espera autorización de Joaquín). La sesión humana dejó además su base
`databases/stage12-2-ff23743f-….db` (+ `-shm`, `-wal`): al volver del selector, las pantallas reabrieron la
base durante la limpieza. Corregido (se detienen los ViewModels antes de cerrar la base) y verificado sin
regresión en las 5 automáticas; el caso exacto de salir a un selector no se repitió. El PDF que Joaquín
guardó (`MiGuardia_2026-09_informe_parcial.pdf`, datos ficticios) quedó en la carpeta que eligió.
**Limpieza (8/11, autorizada por Joaquín):** borrados por nombre exacto los 3 archivos de esa base y los 20
`.lck` vacíos. Foto posterior (`.local-evidence/claude-20261108-12-2/after-cleanup/`): 23 bases y 353
archivos, idénticos a la foto previa a la etapa. (Después se instaló el ejecutor `f300fe81…`.) PDF real revisado en la PC: mismos números y sin dirección,
motivo, puesto, notas, nota médica ni identificadores.
Latencia de primera apertura del Resumen (fixture chica): 210–241 ms en 5 corridas. Sólo se anota; la
alerta de la etapa 8 sigue sin causa conocida y no se reabrió.

**Lista de la etapa** (✅ probado · ⏳ falta · ❌ falla). Ninguna ❌.

*Horas, meta y extras (A077–A110)*
- ✅ A086 avance, meta, período y pendiente — mismos minutos en pantalla real (Samsung) y PC.
- ✅ A087 disponibilidad separada — PC y Samsung.
- ✅ A077, A079, A080, A085, A088, A089, A092, A099, A103, A108, A110 — pruebas existentes.
- ✅ A078 crear por sugerencia («Servicio extra»); A081 ayuda de las dos preguntas; A082 renombrar sin
  reinterpretar el extra guardado; A083–A084 archivar y reactivar sin tocar el extra existente — Samsung.
- ✅ A090 semana con primer día elegido; A091 ciclo con longitud y anclaje; A093 desde hoy y fecha
  propia; A094 corte del tramo anterior (el tramo viejo queda intacto); A095 recálculo pasado y primer tramo
  corto exigen confirmar; A096 guardar después de revisar — Samsung.
- ✅ A097 meta por período sin valor (no se inventa) y luego informada; A098 corrección sólo con
  confirmación y misma identidad — Samsung.
- ✅ A100 sin alta para fecha futura; A101 lugar, tipo, sin plantilla, clase, color y puesto; A102 cruce
  de medianoche y 29 h; A104 clase creada desde el borrador sin perderlo; A105 duración y meta en la
  revisión; A106 superposición y carpeta médica exigen confirmar (la carpeta no se borra); A109 borrar sólo
  ese extra (la jornada del día sigue) — Samsung.
- ✅ A096 si la configuración cambia entre revisar y guardar: avisa «La configuración o la fecha cambió desde
  la revisión…» y no escribe — Samsung.
- ✅ A107 si aparece otro trabajo superpuesto entre revisar y guardar: no guarda, conserva el borrador
  («Los datos cambiaron…»), y al revisar de nuevo exige confirmar la superposición nueva — Samsung.

*Resumen (A127–A145)*
- ✅ A128–A130 meses exactos (PC y Samsung); A131–A133 mismos minutos y disponibilidad aparte (Samsung);
  A135 total con sus 4 contribuciones (Samsung); A145 Informes desde el menú y desde el final (Samsung).
- ✅ Calendario → detalle → Resumen → volver conserva el mes del Calendario (Samsung).
- ✅ A127, A136–A144 — pruebas existentes.
- ✅ A134 desgloses con valores exactos: lugar y tipo 20 h 30 min, clases 3 h 30 min y 1 h, planificado 8 h,
  real 9 h, diferencia 1 h, 1 día de carpeta médica — Samsung.

*Informes (A146–A165)*
- ✅ A146 mes del Resumen (septiembre y agosto); A147 PDF real; A148 XLSX real; A149 vista previa con
  recuentos; A157 archivo coherente con el Resumen; A160 regenerar; A165 vuelve al Resumen en el mismo
  mes — Samsung.
- ✅ A150–A153 interruptores tocados en pantalla (nombre, puesto, notas y nota médica con 2ª confirmación):
  el XLSX real incluye exactamente eso y nunca dirección, motivo ni IDs — Samsung (y PC con/sin opción).
- ✅ A153, A156, A159 (permiso al archivo exacto), A161–A164 — pruebas existentes.
- ✅ A154 fotos elegidas de a una, tope de 12 con aviso «Podés incluir como máximo 12 fotos…», PDF con 12;
  A155 aviso de privacidad de fotos visible — Samsung (13 fotos PNG reales sembradas en la corrida).
- ✅ A158 guardar con el selector real de documentos: lo guardado es idéntico byte a byte al PDF
  (14.120 bytes) — sesión humana 8/11 (`Stage12CalculationsHumanTest`).
- ✅ A159/A161 en el sistema real: se abre la hoja de compartir de Android; cerrarla sin elegir deja el
  informe privado intacto y la pantalla lista; vuelve al Resumen — misma sesión humana.

**PC:** `python tools/verify.py --profile local` ya termina bien. El `preflight_failed` no venía de una
ruta vieja: Windows tiene las rutas largas desactivadas y la copia de los reportes XML superaba 260
caracteres; `verify.py` ahora usa la forma de ruta larga. Última corrida: 1339 pruebas, 0 fallas.

## Retomar aquí (sesión del 7–8/11, Claude)

Joaquín autorizó en el chat instalar y probar en el Samsung y aprobó los arreglos de abajo.
**Dónde quedamos (8/11, ~02:15):** B1 cerrado y guardado en commits locales (`f00d769` y el de este
registro). Teléfono limpio: pareja instalada QA `7dc36052…` + ejecutor `31c30439…`; datos QA iguales a la
foto previa. Nada subido a GitHub: la rama va adelantada respecto de `origin` y el push espera pedido de Joaquín.
**Actualización 8/11 (etapa 12.2):** el ejecutor instalado ahora es `ed25efe4…` (pruebas 12.2); la app QA
sigue `7dc36052…`. Correr las pruebas 12.2 con
`RUNNER=ed25efe4a7f9a46fc349e7cd396be2e5d15793844f4acc4ea2eba6be305ffb0e bash .local-evidence/claude-20261107-b1/run_methods_diag.sh <salida> <selector>…`
(selectores en `Stage12CalculationSelectors`). Evidencia 12.2 en `.local-evidence/claude-20261108-12-2/`.
**Actualización 8/11 (etapa 13):** ejecutor instalado `ac66cf6f52472cef9be73490f80a25508b65bb3aa26d2653479e0abad2682057`
(incluye 12.2 y 13). Después se instaló `e86d06c7b2ea0e8e3b5b46cb4607ef5f9b3633b2bbd84a2e228cfaab87344d5e`
(agrega A188); la app QA sigue `7dc36052…`. Foto de datos QA tras A188 idéntica a la previa (salvo
`files/profileInstalled`). **Pareja instalada desde el arreglo de los `.lck`:** QA
`71523f97946bc2087dac6a45eb3d911f2ea4f728d0fd6f7f524d35ebe462f056` + ejecutor
`1c22148b9e2725ce3d079d55cfc895c557b14fd3fd1c1bbb56453c7f9812e869` (ambos verificados sacándolos del teléfono). Para
correr con esta pareja: `.local-evidence/claude-20261108-13/fix-lck/run.sh` con `QA=` y `RUNNER=`.
**Etapa 13 cerrada por Joaquín (8/11).** **Siguiente:** arrancar la etapa 13.5 · Estética y facilidad de uso; después
la etapa 14 · Sistema y aceptación final (avisos,
Widget, bloqueo, Clima, temas, accesibilidad y pasada completa). Pendientes anotados para esa etapa: «Guardar meta»
durante una recarga (12.2), TalkBack sobre pantallas completas (B1), y Atrás con recuperación de Copias pendiente
(13, INFERIDO que al reabrir aparece la recuperación).

**Entorno de esta PC** (la app de Claude redirige en silencio lo que se escribe en
`AppData\Local`, por eso el SDK quedó fuera de ahí):
- SDK Android: `C:\Users\User\Android\Sdk` (platform-tools 37.0.1, platforms;android-37.0,
  build-tools 36.0.0). `local.properties` apunta ahí. El `adb.exe` de
  `~/.cache/miguardia-stage12-runtime` es un bloqueo viejo que rechaza todo: no usarlo.
- Java: `~/.cache/miguardia-toolchains/jbrsdk-25.0.2-b329.117/...` (exportar JAVA_HOME).
- En Git Bash usar `MSYS_NO_PATHCONV=1` para rutas `/sdcard`.

**Pareja instalada en el Samsung** (verificada sacando el APK del teléfono):
QA `7dc36052…d249`, ejecutor `31c30439…c19b` (desde el 8/11; antes `75563034…3d90`). Se compila con
`:app:assembleQa` y luego `:app:assembleQaAndroidTest -Pmiguardia.stage12NormalRunner=true
-Pmiguardia.stage12QaSha256=<hash QA>`; instalar con `adb install --user 0 -r` (conserva datos).

**Evidencia y herramientas** en `.local-evidence/claude-20261107-b1/` (fuera de Git):
`run_methods.sh` (pareja vieja) y `run_methods_diag.sh` (pareja actual; corre métodos), `snapshot_digest.py` (foto lógica de
datos QA), `ui.py` (toques por adb), fotos antes/después y recibos de cada corrida.

**Resultados en el Samsung (versión final):** ✅ Disponibilidad historial, revisiones y
conflictos · ✅ Detalle→Clima→volver · ✅ Activar Clima desde la tarjeta · ✅ Cancelación con
selector de imagen cancelado · ✅ Privacidad de nota médica · ✅ Repeticiones con políticas
(A051) · ✅ Cargar y editar jornada de día y nocturna (C089). PC: 1333 pruebas, 0 fallas.
Datos QA: las 23 bases idénticas tras instalar y probar; sólo se agregaron recibos y el
horario nocturno 22:00–06:00 que se cargó a propósito como dato de partida.

**Cambios de B1 (ya guardados en el commit `f00d769`):**
- App: «Otras opciones del día» sigue abierto al volver de una pantalla encima (p. ej. cancelar
  una eliminación) — `MiGuardiaApp.kt`, `CalendarDayDetail.kt`. Decidido por Joaquín.
- App: al editar una jornada, la hora de modificación se sella al **guardar**, no al armar la
  revisión — `V2ShiftEditViewModel.kt`, `V2ShiftPlanning.kt` (+ prueba de unidad).
- Pruebas: control de texto que excusa sólo el defecto conocido de Compose al reconstruir el
  layout (`Stage12SemanticTextOverflow.kt`); Disponibilidad busca mensaje/«Reintentar» dentro de
  su propia pantalla (el calendario debajo tiene copias); registro de última acción y del texto
  fallido; `files/profileInstalled` (marcador de Android) no cuenta como dato recibido.

## Sesión humana del 8/11 (madrugada) — ✅ aprobada

La prueba humana nunca había corrido en Android. Hubo que corregir la prueba (no la app), sólo en
`app/src/androidTest`:
- `Stage12B1HumanSession.kt`: quitar el observador desde el hilo principal; admitir el lanzamiento de
  Maps que llega directo a la Activity (la hoja del día es otra ventana y usa el contexto de la Activity).
- `Stage12CalendarExceptionsTest.kt`: etiqueta real de la tarjeta (`shift-detail-card-<id>`); abrir el día
  sin «Otras opciones» para Maps y Clima; diagnósticos temporales en el recibo (`debug_primary_failure`,
  `debug_tree`, `debug_weather_wait`) — decidir si se quitan al cerrar.
- `Stage12HumanImageLease.kt`: aceptar el enlace del selector de fotos (`content://media/picker…/<id>`).

**Corrida aprobada:** run `5f8d559c…`, QA `7dc36052…` (sin cambios), ejecutor `31c30439…`
(verificado sacándolo del teléfono), `stage12NormalStatus=COMPLETE`, 1/1, limpieza completa.
Pasos: 1 Maps y vuelta al mismo día ✅ · 2 dirección guardada para Clima ✅ · 3 ciudad aproximada: el
teléfono no dio ubicación → aviso visible, sin escritura (resultado admitido `LOCATION_UNAVAILABLE`) ✅ ·
4 imagen propia elegida en el selector de fotos, bytes exactos ✅ · 5 cancelar reemplazo conserva borrador ✅ ·
6 Clima habilitado, pronóstico al abrir y al reabrir, sólo cambiaron archivos de Clima ✅.
Evidencia: `.local-evidence/claude-20261107-b1/human-session-r9/` (recibo y salida).
En la corrida anterior el paso 6 se trabó una vez esperando la segunda carga de Clima (30 s); no se
repitió y no se cambió código por eso (INFERIDO: demora de red).

**Foto final de datos** (`final-after-human/`): las 23 bases previas idénticas, ningún archivo previo
faltante; sólo cambió `files/profileInstalled` (marcador de Android ya excusado). Lo nuevo es propio de
pruebas: restos de 10 corridas fallidas (una base + archivos cada una) y 2 recibos de la corrida aprobada.
Imágenes de prueba sobrantes en la galería: borradas (con autorización de Joaquín); no queda ninguna.
Restos de las 10 corridas fallidas: **borrados** por Joaquín el 8/11 (10 bases con `-wal`/`-shm` y 10
carpetas privadas; los recibos se conservan). Foto posterior (`final-after-cleanup/`): 23 bases idénticas
a `final-after-tests/`, ningún archivo previo faltante ni extra salvo 19 recibos y `files/profileInstalled`.
Ubicación aproximada quedó concedida a la app QA.

## Qué pasó hasta el 5/11

- **Recuperar el calendario cuando Android cierra la app** (C096): **aprobado**.
  Costó 14 intentos; el que funcionó usa un toque tuyo real para elegir la foto,
  porque la elección automática fallaba sin causa clara. Probó que, al volver,
  el mes, la selección, el detalle y el borrador siguen ahí y no se guarda nada solo.
- **Error al comparar datos** (la copia temporal `derived_copy` hacía creer que los
  datos habían cambiado): arreglado y probado en la PC.
- **Disponibilidad**: arreglo de acentos (UTF-8) hecho y compilado; falta probarlo en el teléfono.
- **Calendario**: cuatro pruebas en el teléfono pasaron; falta la comprobación final de que no se perdieron datos.
- Hay una **versión de prueba armada** con todos estos arreglos juntos. Compila,
  pero **no se instaló** en el Samsung.
- Se hizo el commit de checkpoint y se subió a GitHub (`codex/miguardia-3.0`).

## Qué falta para cerrar esta etapa

1. ✅ (8/11) **Sesión humana** (Joaquín con el teléfono; 3 min por paso): método
   `Stage12CalendarExceptionsTest#humanMapsLocationAndExternalEvidenceStayScoped` con
   `run_methods.sh`. Pasos: (1) tocar la dirección «Plaza del Congreso…» → Maps → Atrás;
   (2) «Objetivo QA Dirección» → «Usar esta dirección para el clima»; (3) «Objetivo QA Ciudad» →
   «Usar mi ciudad actual para el clima», permiso **Aproximada** o «No permitir»;
   (4) «Elegir evidencia opcional» → imagen celeste `MG_QA_C074_…`; (5) «Reemplazar evidencia
   elegida» → cerrar el selector sin elegir; (6) «Entiendo y habilitar Clima».
2. ✅ (8/11) Foto final de datos QA y comparación con `final-after-tests/digest.json`.
3. ✅ (8/11) Diff revisado, commit local hecho con autorización de Joaquín, B1 cerrado.

**Pendientes anotados:** (`verify.py` arreglado el 8/11, ver etapa 12.2.) Para la etapa 14,
TalkBack podría alcanzar el calendario que queda debajo de pantallas completas.

## Etapas que vienen después

| Etapa | Qué cubre |
|---|---|
| 12.2 · Cálculos y consulta | Horas, metas, extras, Resumen, informes PDF/XLSX |
| 13 · Datos y archivos | Notas, fotos, Copias, restaurar, archivos dañados |
| 13.5 · Estética y facilidad de uso | Pulir Vigilia R4 en toda la app; elegir horarios y fechas con selector nativo (sin quitar la escritura) |
| 14 · Sistema y aceptación final | Avisos, Widget, bloqueo, Clima, temas, accesibilidad, pasada completa final |
| 15 · Preparar la entrega | Firma, prueba de actualización desde 2.0.2 y publicación |

Detalle en el [plan maestro](PLAN_MAESTRO_CIERRE_3_0.md).

## Cambio de forma de trabajo (7/11)

Joaquín pasó de Codex a Claude y aprobó el [Proceso simple](PROCESO_SIMPLE.md):
una tarea por vez con nombre claro, Claude programa y prueba en la misma sesión,
sin recibos entre chats. También decidió hacer **una** pasada completa final
(no dos) más una prueba corta tras cada arreglo. Las pruebas en el teléfono
siguen pausadas hasta que Joaquín las retome.

## Dónde está la evidencia vieja

La carpeta `.local-evidence/` (registros de las pruebas, no se sube a Git) no se
había copiado en la migración. El 7/11 se copió desde el respaldo de Codex del
disco D: (91.718 archivos, 9,1 GB). La comparación archivo por archivo dio
idénticos y los enlaces vuelven a abrir. El respaldo de D: quedó sin tocar.

## Límites que siguen valiendo

- Nada en el Samsung (instalar, ADB, pruebas) sin que Joaquín lo autorice en el momento.
- Nada de commits, push, firma ni publicación sin pedido explícito.
- Registro detallado anterior: [cierre B1](audits/2026-11-03-cierre-b1-vigente.md) ·
  [checkpoint GitHub](audits/2026-11-05-checkpoint-github.md) · [Evidencia](EVIDENCIA.md).
