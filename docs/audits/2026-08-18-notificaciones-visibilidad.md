# Cierre de visibilidad de Notificaciones — 2026-08-18

## Resultado

La brecha detectada durante la Puerta 0 quedó implementada y validada sobre `main`, partiendo de `9ea59f309910a6762779629f33081679fc0e9ccd`.

- La vista expandida ofrece `Eliminar notificación` dentro de las `RemoteViews`, sin ocupar una cuarta acción estándar.
- El mismo `PendingIntent` explícito, inmutable y estable registra tanto el control interno como el descarte que Android comunique.
- Ocultar cancela sólo el aviso elegido, conserva guardia y fronteras temporales, y persiste el UUID opaco en el DataStore exclusivo de Notificaciones.
- El reconciliador no vuelve a publicar un UUID ocultado y elimina registros cuando la guardia termina, se borra, entra en vacaciones, deja de estar `PLANNED` o desactiva sus avisos particulares.
- Configuración observa únicamente guardias ocultas todavía elegibles y ofrece restauración individual; cuando hay varias, también ofrece restaurarlas todas.
- Restaurar revalida guardia, vacaciones, excepción particular, preferencias globales y permiso. Si corresponde, publica silenciosamente el mismo tag UUID; si no, limpia el registro sin publicar.

## Mapa de impacto

El cambio alcanzó presenter y `RemoteViews`, DataStore, reconciliador/runtime, estado y ViewModel, Configuración, wiring de `MainActivity` y pruebas específicas.

No modificó Room, esquemas, migraciones, manifiesto, permisos, Gradle, dependencias, canales, elegibilidad de dominio, calendario, clima ni datos históricos. Room continúa en v5 con trece entidades y migraciones explícitas `1→2→3→4→5`.

## Verificación local

Se ejecutó:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Resultados:

- `app`: 30 JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:domain`: 129 JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:database`: 5 JVM, 0 fallos, 0 errores, 0 omitidas;
- total JVM: 164 aprobadas;
- Lint: 0 errores, 2 advertencias de versiones y 3 sugerencias históricas;
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`, `assembleQa` y `assembleQaAndroidTest`: aprobados.

La advertencia de simetría RTL introducida inicialmente por el control nuevo fue corregida; el segundo Lint volvió a la línea base anterior.

## QA físico por impacto

Dispositivo verificado: Samsung Galaxy S25 Ultra `SM-S938B`, API 36.

Se instalaron temporalmente sólo:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`.

Se ejecutaron 11 pruebas instrumentadas seleccionadas:

- 4 de `ShiftNotificationPresenterInstrumentedTest`;
- 2 de `NotificationPreferencesInstrumentedTest`;
- 4 de `NotificationComposeTest`;
- 1 recorrido de extremo a extremo `explicitDismissControlStaysHiddenAndCanBeRestoredSilently`.

Resultado: `OK (11 tests)`, sin fallos.

El recorrido verificó el control interno renderizado y clickeable, persistencia del ocultamiento, respeto del reconciliador, restauración silenciosa, identidad estable, DataStore, lista individual y total en Configuración y regresiones vecinas de privacidad, contenido y agrupación.

Al finalizar se desinstalaron únicamente ambos paquetes QA. `com.blackatsystems.miguardia` permaneció instalado en la misma ruta observada antes y después. No se reinició el teléfono, no se modificaron sus ajustes y no se tocó ningún dato productivo.

## Seguridad y continuidad

`git diff --check` quedó limpio. No se agregaron logs, secretos, datos reales, permisos, red, telemetría ni artefactos generados. El próximo incremento funcional habilitado por la secuencia aprobada es Perfil laboral y reorganización de Configuración.
