# Auditoría MAIN — orientación visual de Fotos

> Fecha: 2026-08-18
>
> Estado: integrada y publicada
>
> Base documental: `b9e3e7e04d46a140272e504318e8713c5a4622c0`
>
> Commit canónico: `84d2fafc3ed4abcaa648e23ab75dcb8878e1dbba`

## 1. Resultado

MAIN auditó, integró y publicó la corrección que respeta la orientación visual EXIF de las fotos mensuales del cronograma. La misma ruta de decodificación se usa para miniaturas y visor; alcanza archivos existentes y nuevos sin migrarlos, reescribirlos ni modificar sus registros.

La implementación interpreta las ocho orientaciones EXIF, incluidas rotaciones y reflejos, decodifica con muestreo antes de transformar y conserva la imagen sin transformación cuando la orientación es desconocida o no está disponible. Un error al leer metadatos no bloquea el intento de decodificación normal.

## 2. Impacto auditado

El commit canónico contiene siete archivos:

- `app/build.gradle.kts`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/photos/PhotosScreens.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/photos/SchedulePhotoBitmapDecoder.kt`;
- `app/src/test/java/com/blackatsystems/miguardia/ui/photos/SchedulePhotoBitmapDecoderTest.kt`;
- `app/src/androidTest/java/com/blackatsystems/miguardia/PhotosComposeTest.kt`;
- `app/src/androidTest/java/com/blackatsystems/miguardia/SchedulePhotoBitmapDecoderInstrumentedTest.kt`;
- `gradle/libs.versions.toml`.

MAIN agregó una regresión para orientación EXIF desconocida. La única dependencia de producción incorporada es `androidx.exifinterface:exifinterface:1.4.2`, fijada en el catálogo de versiones. No usa red en ejecución ni agrega permisos.

## 3. Verificación JVM y compilación

Comando global ejecutado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease assembleQa assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL`.

Se volvió a ejecutar `testDebugUnitTest` con `--rerun-tasks`. Resultado real por módulo:

- `app`: 34/34;
- `core:domain`: 129/129;
- `core:database`: 5/5;
- total: 168/168;
- fallos, errores y omitidas: 0.

`lintDebug` cerró con 0 errores, 2 advertencias y 3 sugerencias informativas. `dependencyInsight` confirmó la resolución de ExifInterface 1.4.2.

## 4. QA físico por impacto

Dispositivo: Samsung Galaxy S25 Ultra `SM-S938B`, API 36.

Se instalaron únicamente `com.blackatsystems.miguardia.qa` y `com.blackatsystems.miguardia.qa.test`. Se ejecutaron 20 pruebas instrumentadas seleccionadas:

- 9 del decodificador y sus orientaciones;
- 8 de la superficie Compose de Fotos;
- 3 del almacenamiento privado de fotos.

Resultado: `OK (20 tests)` en 4,859 segundos. Al finalizar se desinstalaron solamente ambos paquetes QA. `com.blackatsystems.miguardia` permaneció instalado y no se tocaron sus datos.

No se repitió instrumentación global ajena al impacto ni se hicieron recorridos separados de tema o zoom: el cambio se acotó al decodificador y a la representación de Fotos, y la política vigente conserva la evidencia verde de superficies no modificadas.

## 5. Datos, privacidad y publicación

- Room continúa en versión 5 con 13 entidades.
- Los esquemas 1 a 5, el hash de identidad `53905d3b7992c29ef5cb0c511ffc25af` y las migraciones explícitas no cambiaron.
- El manifiesto y los permisos no cambiaron.
- No se agregaron servicios de red, telemetría ni lectura funcional de GPS u otros metadatos EXIF.
- No se registran rutas, URI, metadatos o contenido privado.
- No se incluyeron fotos reales, datos personales, secretos, APK ni artefactos generados.

## 6. Continuidad

La corrección de Fotos queda cerrada en la línea canónica. El siguiente incremento habilitado es `codex/navigation-drawer`, regido por `docs/prompts/NAVEGACION_MENU_LATERAL.md` y `docs/adr/0015-menu-lateral-como-navegacion-principal.md`. Calendario con selección directa debe nacer después de integrar y publicar Navegación; Onboarding continúa bloqueado hasta cerrar físicamente ambas correcciones.
