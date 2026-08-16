# Prompt maestro de dependencia — CLIMA

> Estado: preparado por MAIN; autorizado por Joa para ejecución separada
>
> Proyecto: MiGuardia
>
> Rama reservada: `codex/weather`
>
> Base funcional previa: `c50790170896387a7cb006cf39e80f58e944af27` (`feat: add local shift notifications`)
>
> Fecha: 2026-08-16

## 0. Rol, autoridad y lectura obligatoria

Sos la dependencia especializada **CLIMA** de MiGuardia. Debés incorporar pronóstico para la duración real de cada guardia, con Córdoba Capital fija, caché privada, funcionamiento offline honesto e integración opcional con notificaciones. El dominio debe quedar reutilizable por futuros widgets sin repetir reglas ni acceder directamente al proveedor.

Antes de planificar, investigar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md` a `docs/adr/0010-notificaciones-y-alarmas-locales.md`, en orden;
4. `docs/prompts/MOTOR_DE_PROXIMO_EVENTO.md`;
5. `docs/prompts/NOTIFICACIONES.md`;
6. `docs/prompts/CALENDARIO_MENSUAL.md`;
7. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
8. este prompt;
9. Gradle, manifiesto, composición, navegación, próximo evento, notificaciones, DataStore, almacenamiento y pruebas relacionados.

Jerarquía: instrucción explícita actual de Joa; `docs/PROMPT_MAESTRO_MAIN.md`; `AGENTS.md`; ADR aceptados; este prompt; implementación. No redefinas el producto. Si la plataforma, términos del proveedor o documentación oficial contradicen el contrato, detené sólo esa parte y elevá evidencia y recomendación mínima a MAIN.

## 1. Línea base y estado que debe preservarse

La rama debe partir del commit documental que incorpora este prompt sobre la base funcional `c507901`. Al iniciar reportá ruta, rama, HEAD, `git status --short` y `git worktree list`.

La aplicación ya incluye calendario, guardias, objetivos, horas, excepciones, vacaciones, fotos, próximo evento, notificaciones locales, DataStore, composición manual y QA aislado. Room está en v5 con exactamente 13 entidades y migraciones `1→2→3→4→5`.

Última referencia verificada por MAIN: 130 JVM + 82 app QA + 48 Room = 260 pruebas, 0 fallos; lint sin errores; debug, release y QA correctos. Repetí todo y obtené conteos nuevos desde XML.

Hashes que deben quedar idénticos:

- v1 `06557907F47669DF0E2F950C00FC7FC89EA45511386A9990803F01B86471AC1B`;
- v2 `8D835CDF9616924A704EF3FDF89CC2BF1268F4275F5E9A978C6F20A6D44D7453`;
- v3 `15299988DA323E9C0C434CC3087308D92605DA12A7AAEAD132E52B2AF7E162F2`;
- v4 `933572FA5CEC8A9B41BEA84B905BCB0A091CB7C8B69C425B4981F5668DB8FE22`;
- v5 `A73B70A1104970092D4155707F3C45429DA5546B5B020A5A6400AF7B33E0C9F9`.

## 2. Resultado obligatorio

MiGuardia debe:

1. mantener Clima apagado hasta habilitación consciente;
2. explicar proveedor, coordenada fija, atribución, conexión IP habitual y efecto de desactivar;
3. descargar pronóstico horario de hasta 16 días sin ubicación del teléfono;
4. conservar la última respuesta válida y distinguir fresca, desactualizada, vencida y no disponible;
5. resumir el intervalo real `[startAt, endAt)` de una guardia, incluida una nocturna;
6. mostrar detalle horario sólo al abrir Clima desde esa guardia;
7. actualizar al habilitar, reanudar si corresponde, abrir detalle, solicitar manualmente y preparar una notificación;
8. enriquecer opcionalmente notificaciones completas sin retrasarlas;
9. exponer contrato compartido para widgets futuros;
10. conservar calendario, guardias y notificaciones útiles ante cualquier fallo climático.

## 3. Decisiones de producto congeladas

### 3.1 Habilitación, ubicación y unidades

- Clima está desactivado por defecto y no hace ninguna solicitud mientras siga apagado.
- Ubicación única V1: `Córdoba Capital, Argentina`, ID `cordoba-capital`, latitud `-31.4201`, longitud `-64.1888`, zona `America/Argentina/Cordoba` mediante `AppDefaults`.
- Se envían sólo coordenadas redondeadas de ciudad. Nunca GPS, dirección de objetivo, domicilio, SSID, celda ni ruta.
- No solicitar ubicación precisa, aproximada ni en segundo plano.
- La dirección histórica sigue siendo exclusiva de Cómo llegar y no modifica el clima.
- Celsius es inicial; Fahrenheit es opcional.
- Caché canónico en °C, mm, km/h y grados. La conversión ocurre sólo en presentación y cambiar unidad no descarga.
- Desactivar cancela trabajo climático y evita nuevas solicitudes. Borrar caché es una acción separada y consciente.

### 3.2 Proveedor y límite comercial

Proveedor inicial para desarrollo privado/no comercial: **Open-Meteo Forecast API**, siempre detrás de una interfaz reemplazable.

- Datos bajo CC BY 4.0: atribución visible y enlace obligatorios.
- El endpoint gratuito está limitado por sus términos a uso no comercial y límites de tráfico.
- No publiques ni explotes comercialmente ese endpoint gratis. Antes de una distribución comercial, MAIN y Joa deben contratar un plan compatible o aprobar otro proveedor.
- No agregues claves, tokens, cuentas ni secretos.
- Revalidá API, licencia, términos, privacidad, disponibilidad y precios al implementar. Si cambiaron materialmente, no conectes silenciosamente.

Fuentes primarias comprobadas por MAIN el 16/08/2026:

- <https://open-meteo.com/en/docs>
- <https://open-meteo.com/en/license>
- <https://open-meteo.com/en/terms>
- <https://developer.android.com/develop/connectivity/network-ops/connecting>
- <https://developer.android.com/training/data-storage>

## 4. Dominio puro compartido

Creá en `core/domain` un paquete `weather` sin Android, Room, Compose, JSON ni recursos, con tipos inmutables equivalentes a:

- `WeatherLocation`: ID, nombre, coordenadas redondeadas y zona;
- `WeatherUnitSystem`: Celsius/Fahrenheit;
- `WeatherCondition`: condición normalizada y `UNKNOWN`;
- `WeatherHour`: `[validFrom, validUntilExclusive)`, temperatura, sensación, precipitación, probabilidad, código, viento, ráfaga y dirección;
- `WeatherForecast`: proveedor, ubicación, descarga, cobertura y horas ordenadas;
- `WeatherFreshness`: fresca, desactualizada, vencida;
- `ShiftWeatherSummary`: cobertura, condición relevante, rango térmico, sensación, lluvia, precipitación, viento y ráfagas;
- estados tipados de contenido, parcial, sin datos y error recuperable;
- interfaz `WeatherRepository` independiente de Open-Meteo, URL, JSON y archivos.

Campos ausentes son opcionales, nunca ceros inventados. Validá números finitos y rangos razonables.

### 4.1 Resumen por guardia

- Guardia `[startAt, endAt)`; hora `[validFrom, validUntilExclusive)`.
- Incluí sólo intervalos con intersección positiva; tocar una frontera sin duración no cuenta.
- Cruzá medianoche, mes, año y febrero bisiesto sin partir la guardia.
- Detectá cobertura completa, parcial o ausente; no interpoles ni extrapoles.
- Ponderá precipitación por fracción de hora solapada.
- Calculá máximos/mínimos sólo con valores presentes.
- Dos guardias simultáneas tienen resúmenes independientes.
- `CANCELLED`, `ABSENT` y `PLANNED` en vacaciones no activan clima operativo.
- Reutilizá la elegibilidad compartida del motor de próximo evento; no crees otra regla.
- Priorizá condición representativa de forma estable: tormenta, precipitación helada, nieve, lluvia fuerte, lluvia/chubascos, niebla, nublado, despejado. Es resumen visual, no alerta oficial.
- Mapeá códigos WMO a condiciones propias y español. Código desconocido → `UNKNOWN`, no fallo fatal.

## 5. Cliente Open-Meteo y red

### 5.1 Solicitud permitida

Usá sólo HTTPS hacia `api.open-meteo.com/v1/forecast` con:

- coordenadas fijas de Córdoba;
- `hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m`;
- `forecast_days=16`;
- `timezone=America/Argentina/Cordoba`;
- `timeformat=unixtime`;
- unidades canónicas Celsius, milímetros y km/h.

No uses geocoding, IP geolocation, direcciones, históricos, reanálisis, aire, radar, mapas, cookies ni endpoints innecesarios.

### 5.2 Transporte seguro

- No agregues bibliotecas HTTP/JSON. Usá APIs Android disponibles detrás de interfaces inyectables.
- Red fuera del hilo principal, coroutines y cancelación cooperativa.
- Timeouts finitos de conexión y lectura. Toda actualización iniciada desde un receiver debe quedar además limitada por un timeout total máximo de 8 segundos.
- Sólo HTTPS; `android:usesCleartextTraffic="false"` está autorizado.
- No sigas redirecciones a otro host o esquema.
- Limitá el tamaño de respuesta antes de materializarla.
- `User-Agent` fijo y genérico, sin modelo, serie, Android ID ni identificador de instalación.
- No registres URL completa, payload, IP, respuesta ni datos de guardia.
- Tipá éxito, timeout/sin conexión, `429`, `4xx`, `5xx`, respuesta inválida y cancelación.
- Respetá `Retry-After` válido y no hagas reintentos agresivos.
- `CancellationException` siempre se relanza.

### 5.3 Parseo defensivo

Antes de aceptar un snapshot verificá cuerpo no vacío, JSON real, arrays compatibles, timestamps crecientes, zona/unidades esperadas, números finitos y tamaño permitido. Tolerá códigos WMO desconocidos. Rechazá HTML, cuerpo truncado, arrays desalineados o timestamp inválido. Una respuesta inválida nunca pisa el último caché válido.

## 6. Persistencia local

### 6.1 Room principal inmutable

El clima es remoto y reemplazable, no dato durable del usuario:

- Room queda en v5 con 13 entidades;
- no tocar `LocalDataStore`, `MiGuardiaDatabase`, `Migrations.kt`, entidades, DAO, repositorios Room ni esquemas;
- no agregar otra base ni `fallbackToDestructiveMigration`.

### 6.2 Caché privado atómico

Guardá sólo el último pronóstico canónico en un directorio privado equivalente a `filesDir/weather_cache/`, con archivo opaco y versionado.

- Envoltorio con versión, proveedor, ubicación, `fetchedAt`, cobertura y datos canónicos.
- Escritura a temporal y reemplazo atómico; nunca exponer una escritura parcial.
- Tolerar inexistente, truncado, corrupto o versión desconocida.
- No interpretar URI externa como ruta.
- No almacenar guardias, objetivos, puestos, direcciones ni datos personales.
- Borrar sólo archivos propios con prefijo/versionado de Clima.
- Tests en directorios temporales UUID; nunca almacenamiento productivo.
- QA debe quedar físicamente separado por `applicationId`; probalo.

### 6.3 Frescura

Con `Clock` inyectable:

- fresca: hasta 60 minutos desde descarga válida;
- desactualizada usable en UI: más de 60 minutos y hasta 6 horas, con timestamp y aviso;
- vencida: más de 6 horas; no mostrar como vigente ni incluir en notificaciones;
- cobertura parcial se declara aunque el archivo sea reciente;
- reloj anterior a `fetchedAt` no produce frescura falsa ni duración negativa;
- cada éxito reemplaza el snapshot completo; no acumules historial.

### 6.4 Preferencias

Creá `WeatherPreferencesStore` separado con DataStore Preferences ya disponible. Guardá:

- habilitación;
- Celsius/Fahrenheit;
- inclusión en notificaciones;
- aceptación de explicación de proveedor/conexión;
- metadatos mínimos para evitar solicitudes duplicadas.

Valores iniciales: apagado, Celsius, notificaciones climáticas apagadas. QA/tests usan nombres privados únicos. No agregues dependencia.

## 7. Coordinador de actualización

Sin polling, `WorkManager`, alarmas climáticas, receiver ni servicio en primer plano.

Disparadores permitidos:

1. habilitación;
2. apertura/reanudación si el caché no está fresco;
3. apertura del clima de una guardia;
4. acción manual;
5. preparación de una notificación si ambas preferencias están activas;
6. futuro widget mediante la misma API, sin implementarlo.

Reglas:

- coalescer solicitudes simultáneas en una descarga;
- caché fresco evita actualización automática;
- un fallo no inicia reintento permanente;
- manual permite nuevo intento, nunca duplica uno en curso;
- conservar contenido mientras actualiza;
- exponer carga, actualizando, contenido, stale, vacío y error;
- cancelar correctamente por alcance;
- usar `Clock`, ubicación y cliente inyectables;
- no depender del mes visible.

## 8. Interfaz

### 8.1 Configuración

Agregá tarjeta **Clima** después de Notificaciones y antes del zoom. Pantalla global:

- explicación de Córdoba fija, proveedor, atribución, IP de conexión y límite comercial;
- habilitar/deshabilitar;
- °C/°F;
- incluir/no incluir en notificaciones;
- estado del caché y última actualización;
- Actualizar;
- borrar caché con confirmación;
- abrir atribución/términos con app externa y fallback honesto.

No muestres configuración de widgets todavía.

### 8.2 Clima por guardia

Cada guardia elegible del detalle ofrece **Clima** y abre por UUID. Reconsultá la guardia real. Mostrá:

- objetivo histórico, fecha argentina y horario completo;
- Córdoba Capital;
- resumen de toda la guardia;
- condición, rango térmico, sensación, lluvia, precipitación, viento y ráfagas disponibles;
- lista horaria desde entrada hasta salida, incluso medianoche;
- timestamp, frescura y cobertura completa/parcial;
- atribución visible `Datos meteorológicos: Open-Meteo` con enlace;
- Actualizar y error persistente con Reintentar.

No rellenes horas faltantes. Si la guardia desaparece, cambia, se cancela, pasa a ausencia o queda en vacaciones, recalculá y explicá que el pronóstico operativo ya no aplica.

### 8.3 Estados y presentación

Cubrí desactivado, primera carga, fresco, actualizando con contenido, desactualizado, parcial, fuera de horizonte, offline con/sin caché, `429`, error HTTP, JSON inválido, caché corrupto y guardia no elegible.

- Reutilizá sistema visual compartido.
- Avisos de éxito flotantes ~2,5 s; errores persistentes con cierre/reintento.
- Nada sólo por color o ícono.
- Recursos Material/propios o licencia compatible documentada.
- Claro/oscuro, retrato/paisaje y zoom interno 100/150/200 con scroll.
- No leer/modificar `font_scale`, densidad, zoom o tamaño Android.
- Semántica descriptiva normal, sin modo paralelo para TalkBack.
- Fechas `DD/MM/AAAA`, horas `HH:mm`, español argentino.

## 9. Integración con notificaciones

Clima no cambia plan, identidades, canales, acciones, permisos ni elegibilidad de alarmas. No hagas que `NotificationReconciler` reprograme por clima.

En una frontera válida:

1. reconsultá guardia, vacaciones, configuración y permisos;
2. publicá el aviso principal sin esperar red;
3. si Clima e inclusión están activos, usá caché fresco con cobertura completa;
4. si falta/venció, iniciá actualización acotada a un máximo total de 8 segundos con `goAsync()` y luego actualizá silenciosamente la misma notificación sólo si sigue válida;
5. cualquier fallo deja intacto el aviso y siempre finaliza `PendingResult`.

Privacidad y contenido:

- sólo `COMPLETA` puede incluir clima;
- `REDUCIDA` sigue con estado/horario;
- `OCULTA` sigue genérica;
- nunca dirección, coordenadas, proveedor, errores ni timestamps técnicos;
- máximo tres acciones y cronómetro vigente;
- sin canal, grupo, sonido o notificación climática separada;
- actualización meteorológica silenciosa, sin nueva alerta;
- parcial, stale o vencida se omite.

El texto sale de formatter puro, nunca del payload. No expone notas, medicina, fotos, terceros ni datos reales.

## 10. Futuro widget

No implementes Glance, `AppWidgetProvider`, receivers ni ajustes de widget. Dejá `WeatherRepository` y `ShiftWeatherSummary` independientes, lectura de último resumen sin forzar red, `refreshIfStale` y documentación de degradación a caché. Evitá ciclos entre clima, próximo evento, notificaciones y app.

## 11. Privacidad, seguridad y permiso

Único permiso nuevo autorizado:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No agregues `ACCESS_NETWORK_STATE` sin evidencia oficial concreta y aprobación de MAIN. No agregues ubicación, almacenamiento, servicios, receivers ni permisos de fondo.

Nunca transmitas guardias, horarios, objetivos, puestos, direcciones, usuario, notas, novedades, datos médicos, fotos, archivos, modelo, serie, Android ID, advertising ID, identificador de instalación o ubicación del dispositivo. La solicitud lleva parámetros climáticos, coordenada fija y User-Agent genérico. El proveedor puede ver la IP pública habitual; no prometas anonimato.

Prohibido: analítica, anuncios, backend, nube, sincronización, HTTP claro, WebView, JavaScript remoto, secretos, logs privados, certificados propios, pinning improvisado o desactivar TLS. El pronóstico es orientativo; nunca alerta oficial ni garantía de seguridad laboral.

## 12. Arquitectura y composición

Mantené composición manual:

- `core/domain`: modelos, agregación, unidades, frescura y contratos;
- `app/.../weather`: cliente, parser Android, archivo, DataStore y coordinador;
- `app/.../ui/weather`: estado, ViewModel y Compose;
- `MiGuardiaApplication`: composición lazy, cero red si está apagado;
- `MainActivity`: ViewModel explícito y refresco de ciclo de vida;
- notificaciones dependen de interfaz/formatter, no JSON ni archivo.

No agregues Hilt, Retrofit, OkHttp, Gson, Moshi, Kotlin Serialization, WorkManager ni módulo Gradle. Si APIs nativas tienen una limitación crítica, documentá y pedí permiso antes de una dependencia.

## 13. Archivos permitidos

Podés tocar sólo:

- `core/domain/src/main/**` y `core/domain/src/test/**` para clima puro;
- `app/src/main/java/com/blackatsystems/miguardia/weather/**`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/weather/**`;
- `MiGuardiaApplication.kt`, `MainActivity.kt`, `MiGuardiaApp.kt` para wiring/navegación;
- `notifications/ShiftAlarmReceiver.kt`, `ShiftNotificationPresenter.kt` y wiring mínimo de enriquecimiento;
- `app/src/main/AndroidManifest.xml` sólo para `INTERNET` y cleartext false;
- `strings.xml` y recursos indispensables propios;
- pruebas app/JVM/instrumentadas;
- `docs/adr/0011-clima-local-cache-y-proveedor.md`;
- atribución estrictamente necesaria.

No tocar:

- `AGENTS.md`, prompt maestro, este prompt ni ADR previos;
- Gradle, catálogo, firma o build types;
- Room, `LocalDataStore`, migraciones, entidades, DAO o esquemas;
- reglas de calendario, horas, vacaciones, fotos, feriados, novedades o remuneración;
- elegibilidad del próximo evento;
- planificación/identidad/canales de alarmas salvo enriquecimiento autorizado;
- archivos ignorados o datos reales.

Si necesitás salir, frená esa parte y pedí autorización con diff mínimo.

## 14. Pruebas JVM obligatorias

Con `Clock`, zona, UUID y datos ficticios deterministas:

1. guardia diurna completa;
2. nocturna atravesando medianoche;
3. 19:30–07:00 con solapamiento fraccional;
4. frontera que sólo toca fin excluida;
5. fin de mes/año y bisiesto;
6. cobertura completa, parcial y ausente;
7. huecos sin interpolación;
8. simultáneas independientes;
9. orden estable;
10. temperatura/sensación opcionales;
11. precipitación ponderada;
12. máximos de lluvia, viento y ráfaga;
13. prioridad de condición;
14. WMO conocido/desconocido;
15. conversión °C→°F y redondeos;
16. fronteras exactas 60 min/6 h;
17. reloj anterior a descarga;
18. cancelada, ausente y vacaciones no activan;
19. dominio sin Android/proveedor.

## 15. Pruebas de red, caché y DataStore

Automatizadas sin proveedor real, usando cliente falso y fixtures sanitizados:

- host/parámetros exactos y ausencia de datos de guardia;
- HTTPS y rechazo de redirect externo;
- timeout, cancelación y rethrow;
- `200`, `429` con/sin `Retry-After`, `4xx`, `5xx`;
- vacío, HTML, JSON roto, arrays desalineados, timestamp/unidad inválidos y exceso de tamaño;
- inválido no pisa caché;
- escritura atómica, fallo previo al reemplazo, corrupto, versión desconocida y reapertura;
- borrado sólo meteorológico;
- directorios UUID, nunca productivos;
- preferencias iniciales/persistencia;
- unidad sin red;
- apagado evita/cancela;
- concurrencia coalescida;
- fresco evita red;
- manual/reintento controlados;
- aislamiento QA real.

## 16. Pruebas app, Compose y notificaciones

Cubrí:

1. tarjeta Clima en orden;
2. explicación/habilitación;
3. unidad persistente;
4. inclusión efectiva sólo con Clima activo;
5. todos los estados UI definidos;
6. timestamp argentino;
7. borrar con confirmación/aviso;
8. navegación por UUID;
9. guardia borrada/cancelada/ausente/vacaciones;
10. horas de nocturna;
11. atribución resoluble/no resoluble;
12. proveedor caído no bloquea app;
13. notificación inmediata sin red;
14. enriquecimiento silencioso;
15. completa incluye si corresponde;
16. reducida/oculta omiten;
17. parcial/stale/vencido omiten;
18. fallo no pierde/duplica aviso;
19. `goAsync()` finaliza siempre;
20. plan/canales/acciones intactos;
21. claro/oscuro, retrato/paisaje, zoom 100/150/200;
22. semántica y ausencia de datos privados.

Ningún test automatizado depende de Open-Meteo real. Se permite un recorrido manual QA acotado con coordenada fija y guardias ficticias.

## 17. Batería obligatoria

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Room aislado e intacto:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
```

Obtené conteos exactos desde XML por JVM, app y Room, con fallos/errores/omitidas. Informá lint y todos los ensamblados. Ejecutá `git diff --check` y revisá cada archivo no rastreado completo.

## 18. Samsung físico QA

Dispositivo esperado: `SM-S938B`, API 36; verificalo. Usá sólo `com.blackatsystems.miguardia.qa`, `.qa.test` y guardias ficticias. Protegé `com.blackatsystems.miguardia`.

```powershell
adb install -r -t app\build\outputs\apk\qa\app-qa.apk
adb install -r -t app\build\outputs\apk\androidTest\qa\app-qa-androidTest.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am instrument -w -r com.blackatsystems.miguardia.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Recorrido real mínimo:

- apagado y cero solicitud;
- explicación/habilitación;
- una descarga QA real con Córdoba fija y atribución;
- guardia diurna/nocturna;
- resumen y hora por hora;
- °C/°F sin descargar;
- manual y caché tras reapertura;
- fallo simulado con/sin caché;
- notificación inmediata y enriquecimiento;
- privacidades completa/reducida/oculta;
- claro/oscuro, retrato/paisaje, zoom 100/150/200;
- enlace con y sin app resoluble.

No cambies Wi-Fi, datos, modo avión, VPN, hora, zona, fuente, densidad o zoom Android. Offline se prueba con cliente falso/error controlado. No reinicies sin autorización nueva inmediata de Joa.

Al terminar desinstalá sólo:

```powershell
adb uninstall com.blackatsystems.miguardia.qa.test
adb uninstall com.blackatsystems.miguardia.qa
```

Confirmá QA ausente y producción instalada. No leas/copias/captures datos reales.

## 19. ADR obligatorio

Creá `docs/adr/0011-clima-local-cache-y-proveedor.md`, estado **propuesto para revisión de MAIN**, con ubicación fija, ausencia de ubicación, proveedor/términos/atribución, interfaz, datos enviados, agregación, caché/frescura, DataStore, disparadores, notificaciones no bloqueantes, futuro widget, `INTERNET`, HTTPS, QA, alternativas y consecuencias. Fechá la revisión de términos y no afirmes uso comercial gratuito.

## 20. Fuera de alcance

Sin GPS/geocoding/múltiples ciudades; mapas; alertas oficiales; radar/satélite/aire/históricos; widgets; jobs periódicos; backend/proxy/cuentas/nube/telemetría; claves/compras; dependencias/módulos Gradle; tablas/migraciones Room; remuneración/informes/backups/bloqueo/onboarding; datos reales; cambios visuales según sistema.

## 21. Entrega a MAIN

No hagas commit, push, merge ni rebase. Conservá todo sin confirmar.

Antes de cerrar:

- auditá hunks y nuevos completos;
- confirmá Room v5/13 y hashes;
- Gradle/build types/firma intactos;
- único permiso nuevo `INTERNET`;
- sin ubicación, cleartext, claves, telemetría, servicios ni datos reales;
- revisá logs/secretos/artefactos;
- `git diff --check` limpio;
- sólo QA retirado y producción preservada.

Informe copiable: ruta/rama/base/HEAD/status; diff y no rastreados; contratos/desvíos; proveedor/términos/datos/atribución; archivos; permiso/manifiesto; Gradle; Room/hashes; caché/DataStore/seguridad; app/notificaciones/widget futuro; comandos/conteos/lint/builds; dispositivo/recorrido real; paquetes; riesgos y pendientes; cero commit/push/merge.

Checklist:

- [ ] Apagado por defecto y cero solicitudes silenciosas.
- [ ] Córdoba fija, cero permisos de ubicación.
- [ ] Proveedor reemplazable; licencia/atribución/comercial honestos.
- [ ] HTTPS, host permitido, timeouts y parseo defensivo.
- [ ] Ningún dato de guardia sale.
- [ ] Dominio puro e intervalos correctos.
- [ ] Caché privado atómico y DataStore aislado.
- [ ] Room v5/13/esquemas intactos.
- [ ] Sin polling/worker/alarma/servicio climático.
- [ ] UI offline y usable al 200 %.
- [ ] Notificación nunca espera ni falla por Clima.
- [ ] Reducida/oculta sin clima.
- [ ] Contrato de widget sin widget.
- [ ] Único permiso nuevo `INTERNET`.
- [ ] Batería global, Room y runner QA verdes.
- [ ] Sólo QA retirado; producción preservada.
- [ ] Sin secretos, datos reales, logs ni artefactos.
- [ ] `git diff --check` limpio.
- [ ] Sin commit, push ni merge.
