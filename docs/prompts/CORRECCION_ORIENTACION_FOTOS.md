# MiGuardia — corrección de orientación visual de Fotos

> Estado: implementado, auditado, integrado y publicado en `main`
>
> Fecha: 2026-08-18
>
> Rama sugerida: `codex/photo-orientation-fix`
>
> Cierre verificado: `84d2fafc3ed4abcaa648e23ab75dcb8878e1dbba` (`fix: respect schedule photo EXIF orientation`)

## 1. Objetivo

Corregir miniaturas y visor para que toda foto se muestre con la orientación visual esperada, incluidas las imágenes verticales cuyos píxeles están almacenados horizontalmente y dependen de EXIF. La corrección debe alcanzar fotos ya guardadas y nuevas sin migrar, reescribir ni borrar archivos.

## 2. Lectura y base

Antes de editar, leer completos `AGENTS.md`, `docs/PROMPT_MAESTRO_MAIN.md`, `docs/prompts/FOTOS_MENSUALES_DEL_CRONOGRAMA.md`, `docs/adr/0008-fotos-mensuales-y-persistencia-local-v4.md`, este contrato, y el código/pruebas de Fotos. Informar ruta, rama, `HEAD`, relación con la base canónica, estado Git y archivos no rastreados.

La rama debe nacer de un `main` limpio que ya contenga este contrato. No hacer commit, push, merge, rebase o limpieza sin autorización de MAIN.

## 3. Causa verificada

`SchedulePhotoFileStore` copia correctamente los bytes y `PhotoImage` decodifica mediante `BitmapFactory.decodeFile`. `BitmapFactory` no aplica por sí solo la etiqueta EXIF que muchas cámaras usan para indicar cómo orientar la imagen. El defecto no está en Room ni en la selección del Photo Picker.

## 4. Decisiones congeladas

- Miniatura y visor usan un único decodificador interno.
- Aplicar las ocho orientaciones EXIF, incluidas rotaciones y reflejos.
- Decodificar con muestreo antes de transformar para evitar consumo innecesario de memoria.
- Calcular correctamente el tamaño visual cuando la rotación intercambia ancho y alto.
- Si no existe orientación compatible, mostrar la imagen sin transformación.
- Un error de metadatos no debe impedir intentar la decodificación normal.
- No modificar el archivo privado, su nombre, su clave, su registro Room ni sus instantes.
- No migrar ni volver a importar fotos existentes: deben verse corregidas al abrirse de nuevo.
- No leer para uso de producto ubicación, autor, modelo, fecha, GPS ni otros metadatos.
- No registrar rutas, URI, metadatos ni contenido privado.

## 5. Dependencia permitida y justificación

Se permite agregar únicamente `androidx.exifinterface:exifinterface:1.4.2`, versión estable verificada en la documentación oficial de AndroidX el 18 de agosto de 2026, para interpretar orientación en API 26 o superior. Android recomienda AndroidX frente a la clase del framework y su API cubre rotación y reflejo sin implementar un parser EXIF propio.

La dependencia:

- no usa red en ejecución;
- no agrega permisos;
- no transmite datos;
- no cambia Room;
- debe declararse en el catálogo de versiones y sólo en `:app`.

No agregar Coil, Glide ni otro cargador de imágenes para esta corrección acotada.

## 6. Impacto permitido

- un decodificador interno dentro de `app/.../ui/photos/`;
- `PhotosScreens.kt` para reemplazar la decodificación directa;
- `gradle/libs.versions.toml` y `app/build.gradle.kts` únicamente para ExifInterface;
- pruebas JVM si se extrae una transformación pura;
- pruebas instrumentadas aisladas del decodificador y Compose de Fotos;
- documentación de Fotos y evidencia de auditoría.

No tocar entidades, DAO, repositorios Room, versión o esquemas, migraciones, manifiesto, permisos, red, selección de archivos, compensación, reemplazo, eliminación, asociación a objetivos ni datos productivos.

## 7. Pruebas mínimas

- orientación normal conserva dimensiones y contenido;
- 90°, 180° y 270° se muestran derechas;
- orientaciones reflejadas se transforman correctamente;
- una imagen vertical asimétrica con píxeles horizontales termina visualmente vertical;
- muestreo usa el mayor lado sin decodificar a tamaño completo;
- archivo y hash no cambian después de decodificar;
- metadatos inválidos degradan a decodificación normal o error visual recuperable;
- miniatura y visor usan el mismo resultado;
- reemplazar y reabrir mantienen la corrección.

Ejecutar pruebas afectadas, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, `assembleQa` y el APK QA instrumentado con `--max-workers=1`. `git diff --check` es obligatorio.

## 8. QA físico

Con autorización explícita, usar sólo `com.blackatsystems.miguardia.qa` y `.qa.test` en el Samsung `SM-S938B`. Probar con imágenes sintéticas o no sensibles:

- retrato con orientación EXIF;
- horizontal normal de control;
- miniatura, visor, zoom, cierre y reapertura;
- reemplazo de una foto;
- tema claro/oscuro y zoom interno pertinente.

No tocar, instalar sobre, desinstalar ni borrar datos de `com.blackatsystems.miguardia`. Retirar únicamente paquetes QA al terminar si así fue autorizado.

## 9. Cierre

Devolver a MAIN ruta, rama, base, diff completo, dependencia exacta, estrategia de transformación, archivos, pruebas y conteos, QA físico realizado o pendiente, privacidad, Room/permisos intactos y comprobaciones no ejecutadas. No declarar corregido sin una prueba que contenga orientación EXIF real o sintética.
