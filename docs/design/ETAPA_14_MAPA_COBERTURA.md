# Etapa 14.1 · Mapa de cobertura de las superficies del sistema

Fecha: 2026-11-09 · Base: `1dd3899` · Estado: **foto de antes de la tanda 1**. Lo que la tanda 1 agregó
y lo que encontró está en `docs/STATUS.md`, sección «Etapa 14.1 · tanda 1». **El estado de hoy, caso
por caso, está en `docs/design/ETAPA_14_LISTA_CASOS.md`.**

Qué es: para cada caso de la lista de la etapa 14.1, qué prueba existe hoy, a qué nivel y qué falta.
Lo armaron cinco ayudantes de sólo lectura (uno por superficie) leyendo el cuerpo de cada prueba; MAIN
verificó aparte los puntos que condicionan la decisión de aislamiento (ver «Verificado por MAIN»).
No se corrió Gradle ni se usó el teléfono.

## Cómo leerlo

- **Cubierto**: hay prueba que ejerce el caso. Casi siempre en la PC o con datos de mentira en pantalla.
- **Parcial**: hay prueba de una parte; la columna «Falta» dice cuál no.
- Niveles: **PC** · **Pantalla** (instrumentada con estado ficticio) · **Sistema** (instrumentada contra
  Android real: avisos publicados, alarmas, RemoteViews, Activity real) · **Dedo** (necesita a Joaquín).
- «Corrió el 9/11» = está en `Stage135Selectors` y pasó en el Samsung sobre la versión actual.

## Resumen

| Superficie | Casos | Cubiertos | Parciales | Sin prueba |
|---|---|---|---|---|
| Avisos (A208–A231, C080) | 25 | 6 | 19 | 0 |
| Clima (A232–A245, C081–C084) | 18 | 8 | 10 | 0 |
| Bloqueo (A246–A259) | 14 | 8 | 6 | 0 |
| Apariencia y Ayuda (A260–A271) | 12 | 9 | 3 | 0 |
| Actualizaciones y apoyo (A272–A286, X014) | 16 | 7 | 9 | 0 |
| Widget y recorridos (W001–W024, X003, X012, X013, X015) | 28 | 7 | 21 | 0 |
| **Total** | **113** | **45** | **68** | **0** |

Tres hechos que pesan más que los números:

1. **En el Samsung, sobre la versión actual, sólo corrieron pruebas de pantalla con datos de mentira**
   (Avisos 8, Clima 13, Widget 7, Bloqueo 5, Apariencia 3, Ayuda 12, tema 3, próxima jornada 29). Ninguna
   prueba que toque el sistema real corrió sobre esta versión.
2. **Las pruebas existentes que tocan el sistema real no pueden correr tal cual sobre los datos QA**:
   borran la base o pisan preferencias reales (detalle en «Pruebas que no pueden correr sobre los datos QA»).
3. **Las piezas que unen todo no tienen prueba directa**: `NotificationViewModel`, `WidgetRuntime`, los
   avisos del sistema que recibe el Widget (`NextEventAppWidgetProvider`), `WidgetViewModel` y el camino de
   descarga exitosa de `WeatherViewModel`.

## Avisos

Corrió el 9/11: `NotificationComposeTest` (8, pantalla con acciones de mentira).

| Caso | Hoy | Falta |
|---|---|---|
| A208 ver estado global | Parcial · Pantalla | Dibujar la pantalla con avisos desactivados |
| A209 activar/desactivar | Parcial · PC | Tocar el interruptor; que apagar retire avisos y alarmas ya puestos |
| A210 permiso en contexto | Parcial · Sistema supervisada | Aceptar el diálogo real; segundo rechazo · **Dedo** |
| A211 denegado vs bloqueado | Parcial · Pantalla + PC | Lectura real con la app bloqueada en Ajustes · **Dedo** |
| A212 abrir ajustes de avisos | Parcial (sólo existe el botón) | Tocar y comprobar el destino exacto; refresco al volver |
| A213 vista previa ficticia | Cubierto · Pantalla | — |
| A214 aviso de prueba | Parcial · Sistema | Camino del ViewModel; verlo irse solo al minuto · **Dedo** |
| A215 ritmo | Cubierto · Pantalla + PC | — |
| A216 «Personalizado» | Cubierto · Pantalla + PC | — |
| A217 6/8/12/24 h | Parcial | Tocar los cuatro botones; 8 h y 24 h no aparecen en ninguna prueba |
| A218 varios anticipos | Parcial · PC | El editor en pantalla (agregar, quitar, tope de cinco) |
| A219 fijo o descartable | Parcial · Sistema | Tocar la opción; aviso en curso «descartable» sin marca de fijo |
| A220 sonido/vibración/silencio | Cubierto · Sistema (canales) | Oírlo y sentirlo · **Dedo** |
| A221 sonido elegido/predeterminado | Parcial | Elegir un sonido de verdad; botón «Predeterminado» · **Dedo** |
| A222 puntualidad exacta | Parcial | Tocar «Resolver» y el interruptor; texto «no es un despertador» · **Dedo** para el permiso |
| A223 privacidad | Parcial menor · Sistema | El toque en pantalla; mirar la pantalla bloqueada · **Dedo** |
| A224 ver avisos ocultos | Cubierto · PC + Pantalla | — |
| A225 restaurar uno | Cubierto · Sistema | Restaurar una disponibilidad |
| A226 restaurar todos | Parcial | Camino del ViewModel y restauración real de dos o más |
| A227 desactivar en una jornada | Parcial | Tocar el botón; camino del ViewModel |
| A228 anticipos de una jornada | Parcial | Editor propio; alarmas reales con anticipos propios |
| A229 volver a la general | Cubierto · Sistema + Pantalla | — |
| A230 abrir desde el aviso | Parcial · Sistema | Disparar el toque del aviso publicado (hoy se arma a mano); jornada borrada · **Dedo** |
| A231 reconciliar cambios | Cubierto por capas | Pasada manual: descartar, editar, mirar |
| C080 avisos por jornada | Parcial | Estado «usa la general», toques y editor (suma de A227–A229) |

## Clima

Corrió el 9/11: `WeatherComposeTest` (13 de 14), 4 de Calendario y 1 de Mi forma de trabajar. La única
evidencia con red y ubicación reales es la sesión humana del 8/11, con otra versión.

| Caso | Hoy | Falta |
|---|---|---|
| A232 abrir Clima | Cubierto · Pantalla | — |
| A233 explicar y habilitar | Cubierto | — |
| A234 deshabilitar sin permisos | Parcial | Tocar el interruptor; que apagar corte la descarga |
| A235 °C/°F sin redescargar | Parcial · PC | Tocar los botones; «°F» en pantalla |
| A236 Clima en avisos completos | Parcial | `notificationTextFromCache` sin prueba; el interruptor · **Dedo** para ver el aviso |
| A237 proveedor y atribución | Cubierto | — |
| A238 abrir sólo el enlace elegido | Cubierto (destino interceptado) | Navegador real y vuelta · **Dedo** |
| A239 borrar cachés con confirmación | Parcial menor | «Cancelar» no borra; mensaje final |
| A240 intervalo exacto | Cubierto | — |
| A241 actualizar a mano | Parcial | Nadie toca «Actualizar»; descarga exitosa en el ViewModel · **Dedo** con red real |
| A242 distinguir estados | Parcial | En pantalla sólo se ve «sin ubicación»: faltan antiguo, vencido, parcial y fuera de ventana |
| A243 temperatura, lluvia, viento | Parcial | Sensación, viento, ráfagas y tarjetas por hora en pantalla |
| A244 no tocar otro objetivo | Cubierto | — |
| A245 error de red o navegador | Parcial | Dibujar el error con «Reintentar» · **Dedo** con modo avión |
| C081 explicar antes de activar | Cubierto | Diálogo de permiso del sistema · **Dedo** |
| C082 ubicación faltante | Cubierto | Una prueba usa un texto viejo (pasa igual): corregirla |
| C083 jornada nocturna completa | Parcial | Igual que A243 |
| C084 fresco/antiguo/vencido | Parcial | Igual que A241, A242 y A245 |

Reglas ya probadas en la PC: fresco hasta 60 min, antiguo hasta 6 h, vencido después.

## Bloqueo

Corrió el 9/11: `AccessLockComposeTest` (5). La lógica está fuerte en la PC (40 pruebas).

| Caso | Hoy | Falta |
|---|---|---|
| A246 capacidad del teléfono | Parcial | Estado «temporalmente no disponible» |
| A247 activar | Cubierto · PC + Sistema supervisada | PIN o huella · **Dedo** |
| A248 inmediato al salir | Parcial · PC | Salir de la app real y volver · **Dedo** |
| A249–A251 uno, cinco y quince minutos | Cubierto · PC (reloj de prueba) | Opcional: un minuto real |
| A252 cambiar plazo con autenticación | Cubierto · PC | — |
| A253 bloquear ahora | Cubierto · PC + Sistema | — |
| A254 desactivar | Cubierto | PIN o huella · **Dedo** |
| A255 recuperar pantalla y borrador | Cubierto | Borrador real de un formulario (recorrido X011) |
| A256 cancelar deja cerrado | Parcial | «Desbloquear + cancelar = sigue cerrado» no está probado en ningún nivel |
| A257 sin credencial o bloqueo temporal | Parcial | Mensaje por tipo de error; «Abrir seguridad» |
| A258 reintentar o reparar | Parcial | `retryStoreRead()` nunca se llama en una prueba |
| A259 tapar contenido | Parcial | Dibujar la tapa de privacidad y la carga; Recientes y captura · **Dedo** |

## Apariencia, accesibilidad y Ayuda

Corrió el 9/11: `AppearanceComposeTest` (3), `HelpComposeTest` (12), tema (3).

| Caso | Hoy | Falta |
|---|---|---|
| A260 oscuro por defecto | Cubierto · PC | — |
| A261 tema claro | Cubierto · Pantalla | Mirarlo · **Dedo** |
| A262 tema oscuro | Parcial | Ninguna prueba toca Claro → Oscuro |
| A263 seguir el sistema | Cubierto · PC | Cambiar el modo oscuro del teléfono es un ajuste global: sólo si Joaquín quiere |
| A264 letra nativa | Cubierto (letra simulada, sin tocar el ajuste del teléfono) | — |
| A265 TalkBack | Parcial | Sólo 3 controles del Calendario; nada de pantallas nuevas · **Dedo y oído** |
| A266 nueve temas de Ayuda | Parcial | Contraer; temas 1, 6, 7, 8 y 9 no se abren en ninguna prueba |
| A267–A271 recorrido y Ayuda sin escrituras | Cubierto | — |

Sin prueba de letra al 200% ni de ambos temas: ajustes de Bloqueo, pantalla Apariencia, lista de Ayuda,
introducción de Ayuda (fila «Atrás / Siguiente») y tres pasos del recorrido.

**Pendiente «TalkBack bajo pantallas completas»: VERIFICADO en el código que el Calendario sigue en el
árbol** debajo de once pantallas completas (Copias, Informes, Disponibilidad, Feriados, Horas, Repetir,
Horario real, Editar jornada, Fotos, Vacaciones, Mi forma de trabajar): se dibujan como hermanas, no lo
reemplazan (`MiGuardiaApp.kt:1284-1411` y `1589-1719`). Si TalkBack llega o no a esas celdas es INFERIDO:
se mide recorriendo el árbol de accesibilidad real con una pantalla abierta, y después se confirma de oído.

## Actualizaciones y apoyo voluntario

No corrió ninguna el 9/11. Ninguna prueba usa la red real.

| Caso | Hoy | Falta |
|---|---|---|
| A272 abrir sin buscar sola | Cubierto | — |
| A273 versión instalada | Parcial | Nadie comprueba el texto |
| A274 buscar al tocar | Cubierto con dobles | Tocar «Buscar» con red real · **Dedo** |
| A275 cuatro estados | Parcial | Paso a «sin publicación», «actual» e «incompatible» y su texto |
| A276 descarga con progreso | Parcial | Avance y texto; descarga real sólo con la versión firmada (etapa 15) |
| A277 verificar el APK | Cubierto · PC | La instrumentada necesita archivos firmados que no están en el repo |
| A278 guardar con el selector | Cubierto con dobles | Selector real de Samsung · **Dedo** |
| A279 releído o sólo escrito | Parcial | Los dos textos |
| A280 cancelar | Parcial en pantalla | «Cancelar» durante la búsqueda |
| A281 errores y espera | Parcial en pantalla | Cuenta regresiva y error por falta de conexión |
| A282 volver sin repetir | Cubierto | — |
| A283 instalación manual | Parcial | El texto; guarda de que no exista permiso ni llamada al instalador |
| A284 apoyo sin datos laborales | Cubierto | Navegador real · **Dedo** |
| A285 sin doble apertura | Cubierto | — |
| A286 sin navegador | Parcial | El aviso visible |
| X014 recorrido completo | Parcial | No hay punta a punta automático |

- La búsqueda va siempre a `https://blackatsystems.com` (`UpdateTransport.kt:40`). **Desde la app QA nunca
  se puede descargar**: su paquete y firma no coinciden con los públicos, así que a lo sumo dirá
  «no es compatible», «no hay versión publicada» o un error. Descargar, verificar y guardar un APK real
  queda para la etapa 15.
- Las 24 pruebas de interoperabilidad con la web no corren en la corrida normal de la PC (piden el
  proyecto web y Node). Falta decidir si cuentan como evidencia de esta etapa.
- **Prueba intermitente `BetaCoordinatorTest#lostResponse…`**: es código de soporte de pruebas, no del
  producto. Causa más probable (HIPÓTESIS, no reproducida): la prueba lee el último pedido antes de que
  salga el envío que espera (`BetaCoordinatorTest.kt:230-233`, `BetaCoordinator.kt:237-239`). El arreglo
  sería sólo en la prueba.

## Widget y recorridos combinados

Corrió el 9/11: `WidgetComposeTest` (7, la pantalla de gestión; no es el Widget). **Ninguna prueba llega
al Widget real de la pantalla de inicio.** Lo más alto que existe es dibujar sus vistas en un contenedor
de prueba.

| Caso | Hoy | Falta |
|---|---|---|
| W001 abrir y explicar el alta | Parcial | Texto de instrucciones; sin botón propio |
| W002 listar instancias | Parcial | Orden y textos (los arma `WidgetViewModel`, sin prueba) |
| W003 vacío, cargando, error | Cubierto · Pantalla | — |
| W004 valores iniciales | Parcial | Automático/Oculta/Clima apagado contra los valores literales; ID inválido |
| W005–W007 modos | Cubierto · PC | Verlo en un Widget real · **Dedo** |
| W008 privacidad completa | Parcial | Afirmar que la dirección no aparece |
| W009 reducida | Cubierto | — |
| W010 oculta | Cubierto · PC + vistas | La prueba exhaustiva (48 variantes) existe pero no consta aprobada |
| W011 Clima por instancia | Parcial | El interruptor por instancia no tiene prueba |
| W012 guardar/cancelar | Parcial | Botón bloqueado al guardar; error con reintento |
| W013 recrear | Parcial | Widget quitado mientras se configura |
| W014 instancias independientes | Cubierto | Dos Widgets reales · **Dedo** |
| W015 tamaños | Parcial | Cambio de tamaño real · **Dedo** |
| W016 filas y contador | Cubierto | — |
| W017 cuenta regresiva | Parcial menor | Verla correr · **Dedo** |
| W018 tocar abre lo exacto | Parcial | Jornada eliminada → Calendario; toque real · **Dedo** |
| W019 tocar el crédito de Clima | Parcial | Sólo en una sesión supervisada |
| W020 tema y letra | Parcial | Vistas con letra al 150 y 200% |
| W021 carga y error | Parcial | Textos y camino de error |
| W022 se actualiza con los cambios | Parcial | La observación viva de `WidgetRuntime` no tiene prueba |
| W023 inicio/fin/medianoche/reinicio | Parcial | Avisos del sistema (reinicio, hora, zona) sin prueba |
| W024 quitar/restaurar | Parcial | Eliminación diferida |
| X003 coherencia entre vistas | Parcial | Crear o editar y ver el Widget |
| X012 destino exacto | Parcial | Mismo identificador de jornada y disponibilidad, del lado Widget |
| X013 Clima y privacidad | Parcial | «Sin red no bloquea el evento» |
| X015 consultar no escribe | Parcial | Con un Widget realmente instalado |

- **Cómo lee el Widget real:** siempre de la base de la app instalada (`NextEventAppWidgetProvider.kt:110`
  → `MiGuardiaApplication`). `WidgetRuntime` sí acepta otra base por constructor (`WidgetRuntime.kt:53-61`),
  así que se puede probar aislado todo menos el Widget puesto en la pantalla de inicio.
- **Dibujar el Widget no escribe jornadas**, pero sí programa su alarma de límite y puede pedir Clima.
- **Paleta:** el Widget quedó con los colores anteriores a la 13.5 (`WidgetRenderModel.kt:95-114` y dos
  fondos en `res/drawable`). Adoptar la nueva es decisión de Joaquín; cambia cinco lugares y dos pruebas
  con colores literales.
- Las pruebas nativas de la etapa 12 para privacidad oculta y «consultar no escribe» hoy sólo están
  admitidas en emulador (`Stage12RuntimeTarget.kt:130-133`), no en el Samsung.

## Pruebas que no pueden correr sobre los datos QA (verificado por MAIN)

| Prueba | Por qué |
|---|---|
| `NotificationAlarmEndToEndInstrumentedTest` | Borra toda la base de la app antes de cada método (`:81`, `:570`) |
| `HelpTourActivityTest` | Borra la base (`:78`) y pisa tema y preferencias |
| `V2ReadyCalendarRecreationActivityTest`, `AvailabilityActivityTest` | Borran la base (`:78`, `:82`) |
| `ShiftNotificationPresenterInstrumentedTest` | Apaga avisos y puntualidad en las preferencias reales sin restaurar y cancela todos los avisos (`:63-66`, `:73-76`) |
| `NotificationDeferredActionsInstrumentedTest` | Borra preferencias reales de acciones diferidas |
| `WidgetConfigurationActivityTest` | Repone el bloqueo a fábrica (`:52`, `:78`), lo activa (`:120`), vacía la cola del Widget (`:56`) y crea un contenedor real (`:57`) |
| `WidgetBoundarySchedulerInstrumentedTest` | Usa la misma alarma que producción: se la cancela a un Widget instalado |

Otros puntos verificados por MAIN: constructor de `WidgetRuntime` con base inyectable; `WeatherRuntime`
crea su cliente de red adentro, sin forma de reemplazarlo (`WeatherRuntime.kt:34`); las clases que
figuran en `Stage135Selectors`.

## Propuesta de trabajo (aprobada por Joaquín el 9/11; tanda 1 hecha)

**Tanda 1 · PC y pantalla, sin teléfono.** Cierra la mayoría de los «Parcial»:
- Avisos: pruebas de `NotificationViewModel`; pantalla desactivada, toques de 6/8/12/24 h, fijo/descartable,
  privacidad, puntualidad, «Predeterminado»; editor de varios anticipos; estados y botones por jornada;
  destino exacto de «Abrir ajustes» y de alarmas exactas.
- Clima: textos de antiguo, vencido, parcial, fuera de ventana y error con «Reintentar»; toques de
  «Actualizar», habilitar, unidad, Clima en avisos y «Cancelar»; `notificationTextFromCache`; jornada ya
  terminada; corregir el texto viejo. Para probar la descarga exitosa del ViewModel hace falta poder
  pasarle un cliente de red de prueba a `WeatherRuntime` (parámetro con valor por defecto; no cambia la app).
- Bloqueo: desbloquear + cancelar, reintentar lectura, mensajes por error, tapa de privacidad y carga,
  letra al 200% en ajustes.
- Apariencia y Ayuda: Claro → Oscuro; contraer y abrir todos los temas; letra al 200% en Apariencia, lista
  e introducción de Ayuda y los tres pasos que faltan (cubre el pendiente de las filas de dos botones).
- Actualizaciones: paso a cada estado y sus textos, progreso, cuenta regresiva, «Cancelar» al buscar,
  instalación manual, guarda de «sin instalador», aviso sin navegador; arreglar la prueba intermitente.
- Widget: `WidgetRuntime` con base de prueba (se actualiza al cambiar datos, evento primero y Clima
  después, error), avisos del sistema al Widget, `WidgetViewModel`, Clima por instancia, jornada
  eliminada, vistas con letra al 150 y 200%, bordes de la configuración.
- Accesibilidad: prueba del árbol real con una pantalla completa abierta (pendiente de TalkBack).

**Tanda 2 · Samsung automático**, según la decisión de aislamiento.

**Tanda 3 · Con el dedo de Joaquín**, pasos guiados: permiso de avisos (aceptar, rechazar, bloqueado en
Ajustes), aviso de prueba, sonido y vibración, pantalla bloqueada por nivel de privacidad, tocar un aviso;
agregar el Widget, configurarlo, cambiarle el tamaño, tocarlo, dos Widgets, quitar uno; bloqueo con PIN o
huella (activar, salir y volver, cancelar, Recientes, desactivar); Clima con red real, «Actualizar», modo
avión, enlace de atribución; «Buscar actualización» y apoyo en el navegador; temas a la vista; letra al
200% en Ayuda; TalkBack de oído.

## Decisiones que necesita Joaquín

1. ~~**Aislamiento** para avisos, Widget y Clima~~ **Decidido por Joaquín el 9/11: mixto.**
   - Automático en el Samsung: avisos, alarmas y vistas del Widget reales, pero sobre base y
     preferencias propias de cada corrida (ampliando el arnés de las etapas 12–13). Los avisos por
     jornada se prueban sólo ahí, porque se guardan en la base.
   - Con el dedo de Joaquín, sobre la app QA: Widget real, bloqueo, permisos y temas. Las 23 bases
     quedan idénticas; sólo cambian preferencias, listadas antes y devueltas a como estaban.
   - Descartadas: sólo lectura estricta (dejaba casi todo en ⏳) y una variante aparte instalada al
     lado (otro APK que la candidata).
2. **¿El Widget adopta la paleta nueva?**
3. **¿Las pruebas de interoperabilidad con la web cuentan para esta etapa?** (piden el proyecto web y Node).
4. Para 14.2: la versión sigue en `2.0.2` / código 6 (`app/build.gradle.kts:41-42`); la candidata final
   necesita versión y código definidos.
