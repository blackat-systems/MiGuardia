# MiGuardia 1.0.0 — registro de versión

> Nota de continuidad 2.0: este registro conserva el lenguaje histórico del
> corte 1.0. El catálogo vigente de 2.0 contiene exactamente Vigilancia privada,
> Policía, Enfermería y Medicina; no existe `Salud` como sector contenedor ni
> una opción `Otro`.

- Nombre: MiGuardia
- Versión: 1.0.0
- `versionCode`: 1
- Fecha de preparación: 2026-08-20
- Commit base: `5d950587b0674f3baa50a5a8bd6ba0085631f8c6`
- Tag de sellado: `v1.0.0`

## Alcance funcional incluido

MiGuardia 1.0.0 consolida el estado estable actual para vigiladores privados:

- almacenamiento local con Room y DataStore, sin cuenta de usuario;
- calendario mensual con consulta, edición consciente y detalle diario;
- carga individual y múltiple de guardias y francos;
- objetivos, horarios, puestos e instantáneas históricas;
- notas, novedades, feriados manuales, carpetas médicas y vacaciones;
- fotos mensuales privadas, visor con zoom y orientación EXIF local;
- resumen de horas trabajadas, pendientes, extra, nocturnas y de feriado;
- motor de próximo evento;
- Pulso Vigilia con ritmos, vista previa, notificación de prueba, ocultamiento y restauración;
- clima opcional para Córdoba Capital con caché privada y degradación segura;
- perfil laboral local, navegación lateral Vigilia, tema y zoom interno.

## Funciones expresamente diferidas

Quedan para MiGuardia 2.0 y no son defectos ni bloqueantes de este candidato:

- onboarding completo, recorrido contextual y Ayuda;
- widgets;
- informes PDF/XLSX;
- copias de seguridad y restauración;
- bloqueo local;
- ampliación a Salud, Policía y otras profesiones;
- rediseños y mejoras no indispensables.

## Plataforma y persistencia

- Android mínimo: API 26 (Android 8.0).
- Android objetivo: API 37.
- Android de compilación: API 37.
- Room: biblioteca 2.8.4, esquema de aplicación v5.
- Entidades Room: 13.
- Esquemas exportados: v1, v2, v3, v4 y v5.
- Migraciones explícitas: `1→2`, `2→3`, `3→4` y `4→5`.

## Política local y de privacidad

Los datos laborales, preferencias y fotos permanecen en almacenamiento privado del teléfono. MiGuardia no incorpora cuentas, nube, sincronización, analítica, telemetría ni ubicación automática. `allowBackup` está deshabilitado y las reglas de extracción excluyen nube y transferencia entre dispositivos.

El acceso a Internet se usa sólo para consultar el clima de Córdoba Capital cuando la función está habilitada. No se envían al proveedor guardias, objetivos, notas, identidad ni datos del dispositivo. Las notas médicas permanecen privadas y no se guardan imágenes de certificados.

## Limitaciones conocidas

- La aplicación es Android, en español y especializada en vigiladores privados.
- El clima se limita a Córdoba Capital y puede quedar no disponible sin red ni caché utilizable; esto no bloquea el resto de la aplicación.
- La aplicación organiza jornadas y horas; no presenta montos ni liquidaciones.
- La presentación, el descarte, el sonido, la vibración y la puntualidad final de notificaciones dependen también de Android, del fabricante y de los permisos concedidos.
- Este candidato no configura firma privada de publicación ni constituye por sí mismo un APK/AAB publicable.
- Las capacidades listadas como diferidas pertenecen a 2.0.

## Matriz de pruebas ejecutadas

| Grupo | Comando o fuente | Pruebas | Fallos | Errores | Omitidas | Estado |
|---|---|---:|---:|---:|---:|---|
| JVM `app` | `testDebugUnitTest` | 38 | 0 | 0 | 0 | Aprobada |
| JVM `core:domain` | `testDebugUnitTest` | 129 | 0 | 0 | 0 | Aprobada |
| JVM `core:database` | `testDebugUnitTest` | 5 | 0 | 0 | 0 | Aprobada |
| Instrumentadas aplicación QA | `connectedQaAndroidTest` | 169 | 0 | 0 | 0 | Aprobada |
| Instrumentadas Room | `:core:database:connectedDebugAndroidTest` | 52 | 0 | 0 | 0 | Aprobada |

Total JVM: 172 pruebas, 0 fallos, 0 errores y 0 omitidas. Los conteos se obtuvieron de los XML JUnit generados por Gradle.

### Comandos, lint y ensamblados

- Batería global: `.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease assembleQa assembleQaAndroidTest` — aprobada en 3 min 13 s.
- Aplicación instrumentada QA: `.\gradlew.bat --no-daemon --stacktrace --max-workers=1 connectedQaAndroidTest` — aprobada en la repetición completa, 169/169.
- Room instrumentado: `.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest` — aprobado, 52/52.
- Lint `debug`: 0 errores, 2 advertencias y 3 sugerencias. Las advertencias informan que existen versiones más nuevas de Gradle y del plugin Compose de Kotlin; no son defectos del candidato. Las tres sugerencias son `AutoboxingStateCreation`.
- `assembleDebug`: aprobado; generó `app-debug.apk` bajo el directorio ignorado `app/build`.
- `assembleRelease`: aprobado; generó `app-release-unsigned.apk`, sin firma privada, bajo `app/build`.
- `assembleQa`: aprobado; generó el APK QA separado `app-qa.apk` bajo `app/build`.
- `assembleQaAndroidTest`: aprobado; generó `app-qa-androidTest.apk` bajo `app/build`.
- Limpieza final: `.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean` — aprobada; retiró los APK y demás salidas generadas del árbol de trabajo después de registrar la evidencia.

La primera ejecución física de `connectedQaAndroidTest` encontró 1 fallo ambiental porque el paquete QA recién instalado todavía no tenía concedido el acceso opcional a alarmas exactas. Se habilitó ese acceso exclusivamente para `com.blackatsystems.miguardia.qa` y se repitió la batería completa hasta obtener 169 pruebas aprobadas, sin tocar el paquete principal.

## Estado de la prueba física

Completada el 2026-08-20 en el Samsung Galaxy S25 Ultra, modelo `SM-S938B`, API 36. Fue el único dispositivo ADB. Se usó exclusivamente `com.blackatsystems.miguardia.qa` en el usuario activo 0. El paquete principal `com.blackatsystems.miguardia` no está instalado en ese usuario; existe en el perfil 10 y sus datos no se abrieron, borraron ni modificaron.

Recorrido comprobado con datos ficticios:

- apertura del calendario en consulta, entrada y salida explícitas del modo edición;
- creación de `Hospital Norte QA`/`NOR`, horario 19:00–07:00, carga múltiple de dos días y carga individual de un tercer día;
- detalle de guardia y resumen mensual: 36 h planificadas, 0 h trabajadas y 36 h pendientes para las tres guardias de prueba;
- menú lateral y acceso a Resumen, Notificaciones, Clima y Apariencia;
- tema claro, oscuro y siguiendo el sistema;
- zoom interno 100 %, 150 % y 200 %, restaurado finalmente a 100 %;
- clima deshabilitado/sin datos degradando con explicación y sin bloquear el detalle de guardia;
- configuración de Pulso Vigilia, permisos concedidos, vista previa ficticia y notificación de prueba publicada en el canal QA con eliminación automática al minuto;
- importación de una imagen ficticia vertical, miniatura, visor y visualización vertical correcta; persistencia tras reapertura y eliminación posterior desde MiGuardia. La corrección de orientación EXIF quedó cubierta por la instrumentación QA;
- cierre forzado y reapertura conservando las tres guardias y la foto ficticia antes de eliminarla.

No había una notificación real de guardia vigente y descartable durante la ventana manual; por eso el ocultamiento y la restauración de un aviso real no resultaron aplicables físicamente. Su superficie y sus acciones quedaron cubiertas por la instrumentación QA aprobada. Al terminar se borraron los datos ficticios del paquete QA y los archivos temporales creados en el teléfono. No se modificaron `font_scale`, densidad, tamaño de visualización, orientación ni configuraciones globales del teléfono.

## Sellado de la fuente

Este registro acompaña el commit de preparación de MiGuardia 1.0.0. La fuente queda identificada mediante el tag anotado `v1.0.0`, que se realizará mediante una puerta Git posterior.

El repositorio no incorpora ningún APK, AAB, keystore ni clave. La firma y la distribución de un artefacto instalable son procesos separados del sellado de la fuente.
