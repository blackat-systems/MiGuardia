# ADR 0033: Widget local sobre la proyección V2

- Estado: aceptada
- Fecha: 2026-08-29

## Contexto

El núcleo laboral V2 ya publica una proyección pura, tipada e inmutable para
jornadas, tramos efectivos de disponibilidad y franco explícito. La tarjeta
superior y las notificaciones consumen esa misma elegibilidad, prioridades e
identidades. La auditoría integral del 2026-08-29 aprobó el núcleo para recibir
la segunda capa.

El contrato histórico de MiGuardia dejó pendiente un Widget de pantalla de
inicio con tres modos, varios ejemplares independientes, tamaños compacto y
ampliado y privacidad por instancia. El árbol actual no contiene
`AppWidgetProvider`, Glance ni WorkManager. Sólo utiliza `RemoteViews` para las
notificaciones.

El Widget debe actualizarse cuando cambian datos o el tiempo cruza una frontera
sin mantener el proceso vivo, consultar por minuto ni depender de que los
avisos estén habilitados. También debe degradar con honestidad cuando Android
demora una actualización.

## Decisión

### Plataforma nativa

Se implementa con `AppWidgetProvider` y `RemoteViews`, layouts XML y una
actividad Compose de configuración. No se agrega Glance ni otra dependencia de
producción.

Esta elección conserva `minSdk 26`, evita ampliar Gradle y usa una API estable
de Android ya conocida por el repositorio. Glance también termina generando
`RemoteViews` y no permite reutilizar directamente los composables actuales,
por lo que no ofrece una ventaja suficiente para este primer Widget.

### Adaptador, no segundo motor

Una proyección pura del Widget recibe exclusivamente configuración de
presentación y un `NextEventResult` ya calculado. Puede seleccionar o resumir
contenido, pero no recalcula elegibilidad, disponibilidad, protecciones,
recurrencias, horario real, simultáneos ni prioridades.

Los modos son:

1. **Próxima jornada**: primera jornada con inicio futuro;
2. **Próximo franco**: próximo `F`/`DAY_OFF` explícito;
3. **Automático**: refleja la prioridad completa de `projectNextEvent`.

Automático consume `NextEventResult.primaryEvents` completo y en su orden
estable, sin volver a filtrarlo por inicio, tipo o prioridad. Próxima jornada
agrupa sólo las jornadas que comparten el primer inicio futuro. Así el Widget
no redefine qué eventos son simultáneos ni descarta jornadas activas que hayan
comenzado a distintas horas.

La disponibilidad conserva su etiqueta histórica y nunca se convierte en
trabajo. Los extras independientes no son próximos eventos. El franco sigue
excluido del plan de notificaciones; el Widget puede mostrar el `nextDayOff`
que ya forma parte del resultado compartido porque es una superficie visual.

### Instancias y privacidad

Cada `appWidgetId` guarda en un DataStore Preferences exclusivo:

- modo;
- privacidad;
- inclusión opcional de Clima;
- confirmación de configuración.

No se persisten eventos, jornadas, proyecciones ni totales derivados. Borrar
una instancia elimina sólo sus claves. Al restaurar IDs, la aplicación mueve
de forma atómica una preferencia anterior únicamente si esa clave existe; si
no existe, el nuevo ID queda en configuración incompleta y privacidad oculta,
sin copiar otra instancia.

`onRestored()` coordina su trabajo asíncrono con el `onUpdate()` que Android
envía inmediatamente después. Mientras no termina el remapeo sólo puede
renderizar el estado seguro. Después vuelve a renderizar y, desde API 30,
recién entonces marca `OPTION_APPWIDGET_RESTORE_COMPLETED=true` y actualiza.
Como `allowBackup=false`, no se promete recuperar preferencias ausentes.

La privacidad es independiente de Notificaciones:

1. completa;
2. reducida, sin lugar, puesto ni color histórico;
3. oculta, con mensaje genérico y sin pistas laborales.

Antes de confirmar se usa el valor seguro oculto. Nunca se muestra ni
transporta dirección, nota, motivo médico, explicación de horario real, foto,
dato personal o información de pacientes.

### Configuración visible

La configuración inicial es obligatoria. Una actividad valida que el
`appWidgetId` pertenezca al provider y devuelve el resultado exigido por
Android. Cancelar el alta no guarda ni crea una instancia configurada;
cancelar una reconfiguración conserva intactas las preferencias anteriores.

MiGuardia incorpora **Widget de inicio** en `Avisos y contexto`. La superficie
explica cómo agregarlo y permite reconfigurar instancias existentes. Android
que lo soporte conserva además la reconfiguración desde el launcher.

### Tamaños y tiempo

Existen layouts compacto y ampliado. API 31 o superior recibe variantes
responsivas por tamaño; API 26 a 30 elige según las opciones informadas por el
launcher.

El Widget sigue la preferencia global `AppThemeMode` de MiGuardia, sin tema por
instancia. `LIGHT` y `DARK` usan las paletas de la app; `SYSTEM` resuelve el
modo vigente del sistema. Cambiar esa preferencia vuelve a renderizar todas las
instancias.

La cuenta regresiva usa el `Chronometer` nativo de `RemoteViews`. El Widget
configura `updatePeriodMillis=0` y no usa polling, WorkManager periódico ni
servicio permanente.

La base del cronómetro usa `SystemClock.elapsedRealtime()` más la diferencia
entre objetivo y reloj civil. Nunca usa epoch como base y se oculta si la
frontera ya pasó.

Mientras el proceso vive, un coordinador observa el grafo V2. Para proceso
muerto se mantiene, como máximo, una alarma inexacta de una ejecución para la
próxima frontera relevante entre todas las instancias. Al recibirla vuelve a
leer toda la fuente. La alarma se recalcula ante cambios y se cancela al borrar
el último Widget.

La alarma usa un `PendingIntent` de broadcast explícito e inmutable, no un
`OnAlarmListener` ligado al proceso. Los receivers que leen Room o DataStore
usan `goAsync()`, trabajo estructurado, timeout acotado y finalización en
`finally`. Clima queda fuera del camino crítico.

La hora absoluta permanece visible cuando la privacidad lo permite. Una demora
del sistema puede retrasar la transición visual, por lo que el producto no
promete exactitud al segundo.

### Clima

Clima es opcional por instancia y está apagado por defecto. Sólo aparece en el
layout ampliado, privacidad completa, Clima global habilitado, consentimiento
aceptado, cobertura completa y caché fresca.

El evento se renderiza primero desde datos locales. Una actualización climática
puede reutilizar `refreshIfStale(false)` en segundo plano, pero nunca bloquea el
Widget ni accede directamente al proveedor desde el presenter. Si se muestra,
incluye la atribución de Open-Meteo. Tocar esa atribución abre mediante un
`PendingIntent` explícito la superficie Clima existente, que contiene el enlace
HTTPS real al proveedor; no se dispara un navegador con un intent implícito
desde el Widget. No hay Clima para disponibilidad o franco.

### Android y seguridad

No se agregan permisos. El Widget no requiere permiso de notificaciones ni
acceso a alarmas exactas. `BIND_APPWIDGET` pertenece al host y no se declara.

Los `PendingIntent` son explícitos, inmutables y distintos por instancia y
acción. Transportan sólo UUID, fecha o identidad opaca. `MainActivity` vuelve a
leer el dato antes de abrir una jornada. Sin evento se abre el Calendario
actual.

Room permanece en versión 5 con veintisiete tablas y esquemas 1 a 5 intactos.
Consultar o renderizar el Widget no escribe datos laborales.

## Consecuencias

- Widget, tarjeta y avisos no pueden decidir con reglas laborales distintas.
- Varias instancias pueden mostrar modos y privacidades diferentes.
- La pantalla de inicio sigue siendo útil aunque Notificaciones estén apagadas.
- El launcher, Doze y el fabricante conservan control sobre el momento exacto
  de una actualización inexacta.
- El Widget necesita pruebas reales de configuración, resize, launcher,
  reemplazo de paquete y navegación en API 26, 33 y Samsung API 36.
- La muerte de proceso se prueba sin `force-stop`, porque el estado detenido
  puede cancelar `PendingIntent` y deshabilitar widgets.
- El zoom interno aplica a la actividad de configuración; el `RemoteViews` se
  dimensiona con el espacio del launcher sin leer ajustes visuales de Android.
- Mapas y `Cómo llegar` quedan fuera de este primer alcance.

## Alternativas descartadas

### Glance

Se descarta porque agrega una dependencia de producción y no reutiliza los
composables existentes. Puede reevaluarse sólo si una necesidad futura concreta
justifica su costo.

### Reutilizar el observador de la tarjeta

Se descarta porque actualiza por minuto y produce una proyección específica de
la tarjeta superior. El Widget necesita fronteras puntuales y un adaptador
propio sobre `NextEventResult`.

### Depender de las alarmas de Notificaciones

Se descarta porque el Widget debe funcionar con avisos desactivados o permiso
denegado. Puede compartir reglas puras y patrones de seguridad, no preferencias
ni tracking instalado.

### WorkManager o actualización periódica

Se descarta porque no aporta exactitud en fronteras breves, aumenta consumo y
duplica una señal que puede resolverse con cronómetro y una alarma inexacta
reconstruible.

### Guardar la última proyección

Se descarta porque duplica historia laboral y puede dejar datos privados o
temporales obsoletos. Sólo se persiste configuración de presentación.

### Ampliar Room

Se descarta porque las preferencias por `appWidgetId` no son datos relacionales
laborales y no justifican una migración.
