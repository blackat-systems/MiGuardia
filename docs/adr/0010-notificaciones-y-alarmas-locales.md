# ADR 0010: Notificaciones y alarmas locales

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-15

## Contexto

MiGuardia necesita anticipar guardias, reflejar su comienzo con el cronómetro del sistema y retirar el aviso al finalizar. La función debe operar sin red, cuentas, sincronización, sondeos residentes ni servicios en primer plano. Las guardias, vacaciones y configuraciones locales continúan siendo la fuente de verdad; una alarma instalada sólo representa una frontera temporal reconstruible.

Android separa el permiso para publicar notificaciones del acceso especial para programar alarmas exactas. También hace inmutable el sonido de un canal una vez creado y conserva para el usuario el control final de importancia, vibración, sonido y visibilidad.

## Decisión

### Plan y elegibilidad

Se reutiliza la primitiva pura `Shift.isEligibleUpcomingWork` extraída del motor de próximo evento. Conserva exactamente la elegibilidad existente: estado `PLANNED`, fin posterior al instante evaluado, intervalo `[inicio, fin)`, vacaciones inclusivas y orden estable por inicio, fin y UUID.

`buildShiftNotificationPlan` es puro y determinista. Recibe `Instant`, modelos de dominio y valores tipados, valida entre cero y cinco anticipaciones positivas y únicas, y devuelve fronteras identificadas por UUID, tipo, instante y anticipación. Produce recordatorios que no sean anteriores a `now`, comienzo futuro y fin futuro. Una excepción particular vacía desactiva todas las fronteras de esa guardia.

### Persistencia

Room evoluciona de v4 a v5 sin alterar las once entidades anteriores. Se agregan:

- `shift_notification_configs`, con PK/FK `shiftId` hacia `shifts` y borrado en cascada;
- `shift_notification_reminders`, con PK compuesta `(shiftId, leadMinutes)` y FK en cascada hacia la configuración.

La existencia de la primera fila representa una excepción explícita. El repositorio valida la cardinalidad y reemplaza configuración y recordatorios dentro de una única transacción. `MIGRATION_4_5` sólo crea esas dos tablas e índices.

DataStore Preferences guarda habilitación consciente, precisión solicitada, recordatorios globales, persistencia, privacidad, URI del tono, identidades de alarmas instaladas e IDs opacos de notificaciones visibles. Los tests usan nombres aleatorios y el build QA usa su propio `applicationId`, por lo que base, DataStore, archivos, permisos, canales y alarmas quedan separados del paquete principal.

### Programación y reconciliación

Cada frontera usa una alarma única `RTC_WAKEUP`. Cuando el usuario eligió precisión y `canScheduleExactAlarms()` lo permite se usa `setExactAndAllowWhileIdle`; de lo contrario se usa `setAndAllowWhileIdle` y la interfaz explica que Android puede demorar el aviso. No se declara `USE_EXACT_ALARM` ni se simula precisión con WorkManager, polling o servicios.

El reconciliador observa guardias, vacaciones, excepciones particulares y preferencias mientras el proceso vive. Compara el conjunto deseado con las identidades instaladas, cancela lo obsoleto y programa lo faltante. También restaura notificaciones de guardias en curso después de reconstruir el estado.

Los receptores aceptan únicamente acciones conocidas. La entrega usa `goAsync()`, vuelve a consultar Room y DataStore, valida guardia, vacaciones, configuración y frontera, y nunca trata el intent como fuente de verdad. Reinicio, reemplazo del paquete, cambio de hora o zona y recuperación del acceso exacto disparan una reconciliación completa.

### Presentación, privacidad y acciones

Cada guardia usa el UUID como `tag` estable de `NotificationManager`; el entero es constante y no constituye por sí solo la identidad. Los sucesivos recordatorios actualizan esa notificación y pueden alertar otra vez. Antes de empezar, el título informa `Entrás a las HH:mm`; al comenzar cambia a `Guardia en curso`. El contador regresivo usa un `Chronometer` nativo dentro del cuerpo personalizado y oculta el pequeño contador del encabezado; Android actualiza esa vista sin alarmas por minuto. Por defecto la notificación queda fija hasta el fin y el usuario puede volverla descartable. Las guardias simultáneas conservan notificaciones separadas bajo un mismo grupo y un resumen genérico.

La alarma de Android es sólo el mecanismo interno que despierta el proceso en una frontera temporal; nunca se presenta como alarma de despertador, pantalla completa, sonido en bucle ni interfaz invasiva. La aplicación publica una notificación común y no usa servicio en primer plano ni polling para mantener el cronómetro.

Se usa `NotificationCompat.DecoratedCustomViewStyle` con `RemoteViews` acotadas para ubicar el `Chronometer` dentro del contenido, conservando encabezado, icono, expansión y acciones controlados por Android. No se dibuja una interfaz de alarma. El contenido completo toma exclusivamente las instantáneas históricas de objetivo, abreviatura, horario, puesto y color. Nunca incluye notas, descripciones de novedades, datos médicos, fotos, terceros ni otros datos privados. La privacidad de pantalla bloqueada ofrece contenido completo, versión pública reducida a estado y horario, o versión genérica oculta.

La configuración aplica divulgación progresiva: apagado/encendido, permiso, momento del aviso y comportamiento. Recordatorios múltiples, puntualidad exacta, privacidad y sonido permanecen disponibles como opciones avanzadas, sin enfrentar al usuario con todos los controles al mismo tiempo.

Los canales tienen IDs deterministas `guard_shifts_v2_<hash-del-sonido>`. La versión 2 solicita sonido y vibración predeterminados; el cambio de versión permite aplicarlo sin intentar mutar un canal ya creado. Al usar un sonido distinto se crea el canal correspondiente y se retiran únicamente canales antiguos con el prefijo propio `guard_shifts_`, incluidos los `v1`. La URI elegida por el selector oficial se conserva como metadato; no se copia audio ni se solicita acceso general a archivos. Android y el usuario conservan el control final del sonido y la vibración.

Las acciones son explícitas, inmutables y únicas por acción, UUID y frontera temporal. `Ver detalles` abre el día exacto, `Cómo llegar` delega la dirección histórica a una aplicación externa y vuelve al detalle con explicación si no puede resolverla, e `Informar novedad` abre el flujo existente sin mutar datos. Las acciones declaran autenticación requerida mediante `NotificationCompat` en las versiones compatibles.

### Permisos y QA

El manifiesto queda limitado a `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` y `RECEIVE_BOOT_COMPLETED`. Los receptores son no exportados y `MainActivity` procesa tanto el intent inicial como `onNewIntent`.

El build type `qa` hereda de `debug`, agrega `.qa` al `applicationId` y conserva simultáneamente las variantes instrumentadas debug y QA mediante Android Components. Ninguna prueba debe borrar datos, modificar permisos, canales o alarmas de `com.blackatsystems.miguardia`.

## Alternativas descartadas

- Alarmas repetitivas, polling, WorkManager o servicio en primer plano: añaden consumo y no representan las fronteras reales.
- Confiar en extras completos o en alarmas instaladas: permitiría contenido obsoleto y duplicaría la fuente de verdad.
- Un canal fijo para todos los sonidos: Android no permite cambiar su sonido después de crearlo.
- IDs de notificación basados sólo en `UUID.hashCode()`: una colisión funcional podría reemplazar otra guardia.
- Navegación o mapas embebidos: amplían dependencias, permisos y alcance sin necesidad.

## Consecuencias

- Las ediciones, eliminaciones, vacaciones y cambios de preferencias invalidan el plan anterior de forma reactiva e idempotente.
- Sin acceso exacto el producto sigue funcionando, aunque Android puede demorar las fronteras.
- Tras force-stop Android no garantiza alarmas hasta que el usuario vuelva a abrir la aplicación; es una restricción de plataforma que no se intenta eludir.
- Cambiar el sonido puede reemplazar el canal gestionado por MiGuardia y restablecer personalizaciones previas de ese canal; el usuario sigue pudiendo configurarlo desde Android.
- El recorrido de reinicio físico queda sujeto a autorización explícita inmediatamente anterior.

## Fuentes

- Permiso de notificaciones: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- Alarmas y acceso exacto: <https://developer.android.com/develop/background-work/services/alarms>
- Canales: <https://developer.android.com/develop/ui/compose/notifications/channels>
- Diseño y acciones: <https://developer.android.com/develop/ui/compose/notifications>
- Cronómetro: <https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder.html>
- Privacidad en pantalla bloqueada: <https://developer.android.com/design/ui/mobile/guides/home-screen/notifications>
