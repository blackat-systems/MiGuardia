# ADR 0011: Clima local, caché privado y proveedor reemplazable

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-16
- Revisión de API, licencia, términos, privacidad y precios: 2026-08-16
- Autoridad: MAIN, después de auditar la entrega de `CLIMA`

> La ubicación fija de V1 y el rechazo del permiso de ubicación quedan
> reemplazados para MiGuardia 2.0 por ADR 0038, incluida la búsqueda consciente
> de una dirección mediante Android y la captura puntual aproximada. Las
> secciones de este documento que nombran Córdoba fija, caché único o cero
> permisos describen V1 y son históricas. Los contratos de transporte,
> proveedor reemplazable, degradación offline y ausencia de polling continúan
> vigentes.

## Contexto

MiGuardia necesita mostrar el pronóstico que cubre el intervalo real de una guardia y enriquecer opcionalmente las notificaciones, sin convertir la red en una dependencia del calendario, de las alarmas o de los datos laborales. La V1 tiene una única ubicación funcional: Córdoba Capital. No usa ubicación del teléfono ni la dirección histórica de un objetivo.

El clima es información remota, reemplazable y no durable del usuario. Room v5 ya contiene trece entidades de datos locales y no debe crecer para almacenar un snapshot meteorológico que puede volver a descargarse.

## Decisión

### Ubicación y datos enviados

- La ubicación fija vive en `AppDefaults`: ID `cordoba-capital`, latitud `-31.4201`, longitud `-64.1888` y zona `America/Argentina/Cordoba`.
- No se solicita permiso de ubicación ni se consulta GPS, red, dirección, SSID, celda o ruta.
- La solicitud transmite únicamente esa coordenada fija, las variables meteorológicas, unidades canónicas y un `User-Agent` genérico.
- No se transmiten guardias, horarios, objetivos, puestos, direcciones, usuario, notas, novedades, datos médicos, fotos, modelo, serie ni identificadores del dispositivo o instalación.
- Open-Meteo puede observar la IP pública habitual y sus términos informan que los logs técnicos de la API gratuita pueden contener IP y coordenadas durante hasta 90 días. La interfaz lo explica sin prometer anonimato.

### Proveedor y límite comercial

- El proveedor inicial es Open-Meteo Forecast API detrás de `WeatherForecastClient` y del contrato público `WeatherRepository`.
- Se usa solamente `https://api.open-meteo.com/v1/forecast`, con hasta 16 días, hora Unix, zona Córdoba y variables horarias de temperatura, sensación, precipitación, probabilidad, código WMO, viento, ráfaga y dirección.
- Los datos se mantienen en °C, mm, km/h y grados. Fahrenheit es una conversión de presentación y no causa red.
- La documentación oficial revisada confirma datos bajo CC BY 4.0 y exige crédito y enlace junto a su presentación. MiGuardia muestra `Datos meteorológicos: Open-Meteo` con enlace.
- La API gratuita continúa limitada a uso no comercial, sin garantía de disponibilidad y con límites publicados de 600 solicitudes por minuto, 5.000 por hora, 10.000 por día y 300.000 por mes.
- Esta conexión queda autorizada para desarrollo privado/no comercial. Una distribución comercial deberá contratar un plan compatible que usa `customer-api.open-meteo.com` y clave, o reemplazar el proveedor con autorización de MAIN y Joa. No se incorpora clave ni cuenta en este incremento.

Fuentes primarias revisadas:

- <https://open-meteo.com/en/docs>
- <https://open-meteo.com/en/license>
- <https://open-meteo.com/en/terms>
- <https://open-meteo.com/en/pricing>
- <https://developer.android.com/develop/connectivity/network-ops/connecting>
- <https://developer.android.com/training/data-storage>

### Dominio y agregación

- `core:domain/weather` contiene modelos inmutables, condiciones propias, frescura, conversión y agregación sin Android, JSON, Room, archivos ni Compose.
- Una guardia y una hora meteorológica son intervalos semiabiertos. Sólo cuenta una intersección de duración positiva; una frontera que apenas toca no se incluye.
- La precipitación se pondera por la fracción horaria realmente solapada. Temperaturas, sensación, probabilidad, viento y ráfagas se agregan sólo cuando el proveedor entregó el campo.
- La cobertura es completa, parcial o ausente. Los huecos no se interpolan y los extremos no se extrapolan.
- La condición representativa usa prioridad estable: tormenta, precipitación helada, nieve, lluvia fuerte, lluvia, chaparrones, niebla, nublado y despejado. Un código WMO desconocido queda `UNKNOWN`.
- La elegibilidad operativa reutiliza `Shift.isEligibleUpcomingWork`: sólo `PLANNED`, fin posterior al instante y fecha inicial fuera de Vacaciones. Cancelada, ausente o planificada en vacaciones no inicia clima operativo.

### Transporte y validación

- El transporte usa `HttpsURLConnection` fuera del hilo principal, corrutinas y cancelación cooperativa. No se agregan bibliotecas HTTP o JSON.
- El esquema y host son fijos; se deshabilitan redirecciones automáticas y se rechaza cualquier respuesta que no sea HTTPS del host esperado.
- Existen timeouts finitos de conexión/lectura, límite de un MiB antes de materializar la respuesta y timeout total de ocho segundos al enriquecer desde un receiver.
- El parser valida JSON, zona, unidades, cantidad y alineación de arrays, timestamps crecientes, números finitos y rangos razonables. HTML, vacío, truncado, exceso de tamaño o campos incompatibles son respuestas inválidas.
- `429` conserva un `Retry-After` válido. `4xx`, `5xx`, offline/timeout, respuesta inválida y caché se representan por errores tipados. No hay reintento permanente ni agresivo. `CancellationException` se relanza.
- No se registran URL completa, cuerpo, IP, respuesta o datos de guardia.

### Caché y preferencias

- El último snapshot canónico se guarda en `filesDir/weather_cache/weather_v1.cache`, físicamente separado por `applicationId` en QA.
- El formato es binario, opaco, versionado y acotado. La escritura usa temporal y reemplazo atómico cuando el sistema lo soporta, con reemplazo seguro como fallback.
- Un caché inexistente, truncado, corrupto o de versión desconocida se trata como ausente. Nunca pisa el último archivo válido por una respuesta inválida.
- Borrar elimina solamente archivos propios cuyo nombre empieza con `weather_`; no toca guardias, Room, fotos u otros archivos.
- No almacena guardias, objetivos, horarios, puestos, direcciones ni datos personales y no conserva historial meteorológico.
- Fresca significa edad no negativa de hasta 60 minutos. Desactualizada significa más de 60 minutos y hasta seis horas y puede verse con aviso. Vencida significa más de seis horas y no se presenta como vigente ni se incluye en notificaciones. Un reloj anterior a `fetchedAt` se considera vencido.
- `WeatherPreferencesStore` usa DataStore Preferences separado: apagado por defecto, Celsius, inclusión en notificaciones apagada, aceptación de explicación y último intento mínimo. Los tests usan archivos UUID privados.

### Disparadores y composición

- No existen polling, WorkManager, alarma, receiver o servicio climático.
- Las únicas entradas son habilitación consciente, reanudación con caché no fresco, apertura del clima de una guardia, actualización manual, preparación de notificación y futura llamada de widget.
- `DefaultWeatherRepository` coalesce solicitudes simultáneas y un caché fresco evita una actualización automática. Una acción manual fuerza un intento pero nunca duplica uno ya en curso.
- `MiGuardiaApplication` compone perezosamente preferencias, runtime, cliente, caché y repositorio. Clima apagado no realiza solicitudes. Desactivar cancela la descarga activa y bloquea nuevas entradas.
- `WeatherViewModel` conserva contenido mientras actualiza y distingue carga, actualización, fresco, desactualizado, vencido, parcial, vacío y error recuperable.

### Notificaciones y futuro widget

- La notificación principal se publica y valida con las reglas existentes, sin esperar una descarga.
- Sólo privacidad `COMPLETA`, Clima habilitado e inclusión activa pueden mostrar un resumen. Reducida y oculta lo omiten incluso si un caller entrega texto.
- Sólo caché fresco y cobertura completa se usan inmediatamente. Si falta o venció, el receiver intenta actualizar por hasta ocho segundos y vuelve a consultar guardia, vacaciones, preferencias, permiso y frontera antes de actualizar silenciosamente la misma notificación.
- Un fallo deja intacto el aviso y `PendingResult` finaliza siempre. Clima no modifica plan, identidades, canales, acciones, permisos o reconciliación de alarmas.
- `WeatherRepository`, `ShiftWeatherSummary`, lectura de último snapshot y `refreshIfStale` quedan independientes de Compose, notificaciones y Open-Meteo para un widget futuro. Este incremento no implementa Glance ni componentes de widget.

### Permisos y QA

- El único permiso nuevo es `android.permission.INTERNET`.
- `android:usesCleartextTraffic="false"`; no se agrega `ACCESS_NETWORK_STATE`, ubicación, almacenamiento, servicio, receiver o permiso de fondo.
- Las pruebas automáticas usan cliente falso y fixtures sanitizados. La única consulta real permitida es un recorrido QA manual contra Córdoba fija.
- La instalación y los datos de `com.blackatsystems.miguardia` no se borran ni modifican. La red y cualquier prueba destructiva usan exclusivamente `com.blackatsystems.miguardia.qa` y `.qa.test`.

## Alternativas consideradas

### Guardar pronósticos en Room

Se descarta porque ampliaría un esquema durable con datos remotos reemplazables, exigiría migración y mezclaría la historia laboral con un caché.

### Retrofit, OkHttp o biblioteca JSON

Se descartan porque la solicitud es única y estable, las APIs nativas cubren el transporte y el parser, y una dependencia agregaría superficie de mantenimiento, licencia y tamaño sin necesidad demostrada.

### WorkManager o actualización periódica

Se descarta por consumo y tráfico innecesarios. Los disparadores de producto ya cubren el uso real y el último pronóstico permite degradación offline.

### Dirección del objetivo o ubicación del teléfono

Se descartan por decisión funcional y privacidad. V1 informa únicamente Córdoba Capital y la dirección histórica se reserva para `Cómo llegar`.

### Endpoint gratuito en una versión comercial

Se descarta por contradicción directa con los términos vigentes. La interfaz y esta decisión dejan explícito el límite y la interfaz sustituible.

## Consecuencias

- Calendario, guardias y notificaciones siguen útiles sin internet o con proveedor caído.
- El pronóstico puede ser parcial para guardias fuera del horizonte y la UI debe decirlo sin inventar horas.
- La atribución es parte permanente de toda superficie que muestre datos de Open-Meteo.
- La disponibilidad gratuita no es un contrato de servicio; la aplicación conserva último dato válido y mensajes recuperables.
- Room permanece en v5 con trece entidades y esquemas v1-v5 inmutables.
- Antes de publicar o monetizar MiGuardia, MAIN debe volver a revisar términos y aprobar proveedor/plan compatible.
