# Prompt especializado — Fotos mensuales del cronograma

> **HISTÓRICO V1 — NO EJECUTAR.** Fotos y Room v4 ya fueron integrados y algunas
> reglas de borrado/navegación fueron reemplazadas. Ver `docs/prompts/README.md`.

## 0. Rol y entrega

Sos la dependencia especializada **FOTOS MENSUALES DEL CRONOGRAMA** de MiGuardia. Trabajás en un worktree separado creado por MAIN desde el commit base que Joaquin te entregue.

Implementá completamente el almacenamiento, organización y visualización local de fotos de cronogramas asociadas a un mes. No sos MAIN: no redefinas el producto, no amplíes contratos fuera de lo autorizado y no hagas commit, push ni merge. Devolvé el trabajo sin confirmar para que MAIN lo audite e integre.

Antes de modificar, registrá:

- ruta absoluta del worktree;
- `git worktree list`;
- `git status --short`;
- rama o detached HEAD;
- `git rev-parse HEAD` y coincidencia con el commit base;
- cambios rastreados o no rastreados preexistentes;
- Samsung Galaxy S25 Ultra visible mediante ADB.

## 1. Lectura obligatoria

Leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. este archivo;
4. ADR 0001 a ADR 0007;
5. código, contratos y pruebas de Calendario, Configuración, objetivos, navegación, Room y almacenamiento local.

Jerarquía: instrucción actual de Joaquin; prompt maestro; `AGENTS.md`; ADR y este prompt; implementación. Si una contradicción cambia el producto, frená y consultá a MAIN.

Para APIs Android que puedan haber cambiado, usá documentación oficial vigente. Referencias iniciales:

- https://developer.android.com/training/data-storage/shared/photo-picker
- https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickMultipleVisualMedia
- https://developer.android.com/training/data-storage/app-specific

## 2. Objetivo aprobado

El usuario puede asociar una o varias fotos de un cronograma a un `YearMonth` para consultarlas mientras carga manualmente sus guardias. Opcionalmente puede identificar cada foto con un objetivo.

Las fotos son únicamente referencia visual. MiGuardia no interpreta ni importa su contenido.

Implementar:

- selección individual o múltiple con el Photo Picker;
- asociación obligatoria a mes/año;
- asociación opcional a un objetivo con instantáneas de nombre y abreviatura;
- copia local privada y persistente;
- listado y miniaturas del mes;
- visor individual con desplazamiento horizontal, zoom interno y paneo;
- indicador de posición;
- agregar, cambiar objetivo, reemplazar y eliminar;
- eliminación de todas las fotos del mes con confirmación reforzada;
- carga, contenido, vacío, error y reintento;
- estado restaurable mediante `SavedStateHandle`;
- acceso desde el botón de fotos y el menú mensual del Calendario;
- claro/oscuro, retrato/paisaje, insets y semántica accesible.

Fuera del alcance:

- OCR o reconocimiento de datos;
- carga automática de guardias;
- importación de Excel;
- recorte, edición, filtros, anotaciones o cámara propia;
- compartir, exportar, informes o copias de seguridad;
- galería global independiente del mes;
- fotos de certificados médicos;
- nube, cuentas, sincronización, telemetría o red;
- remuneración o cambios en horas, vacaciones y estados diarios;
- permisos generales de fotos o almacenamiento;
- límites de negocio arbitrarios no aprobados por MAIN.

La futura copia de seguridad podrá incluir las imágenes, pero no se implementa aquí.

## 3. Selección y permisos

Usá `PickVisualMedia`, `PickMultipleVisualMedia` y `PickVisualMedia.ImageOnly`. El usuario elige conscientemente cada imagen. Cancelar el selector no es un error ni modifica datos.

No solicites `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` ni `MANAGE_EXTERNAL_STORAGE`. No supongas que una URI tiene ruta real ni que el proveedor es local.

## 4. Archivos privados y consistencia

Copiá cada imagen seleccionada mediante `ContentResolver` a `filesDir/schedule_photos/` o una organización interna equivalente. La implementación debe:

1. abrir y leer mediante streaming;
2. validar que sea una imagen decodificable;
3. obtener dimensiones sin cargar innecesariamente el bitmap completo;
4. escribir primero un temporal;
5. usar nombres opacos basados en UUID;
6. mover al destino definitivo cuando la copia esté completa;
7. cerrar streams y descriptores;
8. no guardar rutas absolutas ni depender del URI original.

No usar nombres originales, personas, objetivos o fechas laborales en rutas. La única etiqueta EXIF que puede interpretarse es orientación, de forma local y transitoria durante la decodificación, para mostrar correctamente fotos verticales, horizontales y reflejadas. No persistir esa lectura por separado, modificar el original por ella ni extraer, exponer, registrar o transmitir ubicación, autor, dispositivo u otros metadatos. No registrar URI, rutas o contenido privado.

Room y archivos no comparten transacción. Implementá compensación y reconciliación:

- si Room falla después de copiar, eliminar el archivo nuevo;
- limpiar solamente temporales y huérfanos inequívocamente propios;
- para eliminar, evitar filas activas que apunten a archivos inexistentes y restaurar ante fallo de Room;
- para reemplazar, conservar la imagen anterior hasta confirmar la nueva;
- un fallo en una imagen de una selección múltiple no debe eliminar importaciones válidas;
- relanzar `CancellationException` y no convertir cancelaciones en errores.

No impongas un límite funcional de cantidad o tamaño sin aprobación. Debés, sin embargo, consultar espacio disponible, trabajar por streaming, evitar `OutOfMemoryError` y comunicar almacenamiento insuficiente.

## 5. Dominio y contratos autorizados

Crear un modelo equivalente a:

```kotlin
data class SchedulePhoto(
    val id: UUID,
    val month: YearMonth,
    val objectiveId: UUID?,
    val objectiveNameSnapshot: String?,
    val objectiveAbbreviationSnapshot: String?,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

`storageKey` es relativo y opaco. Tamaño y dimensiones son positivos. Si existe objetivo, las instantáneas son obligatorias; editar, ocultar o eliminar la plantilla no altera la foto histórica.

Contrato autorizado:

```kotlin
interface SchedulePhotoRepository {
    fun observeForMonth(month: YearMonth): Flow<List<SchedulePhoto>>
    suspend fun getById(id: UUID): SchedulePhoto?
    suspend fun insert(photo: SchedulePhoto)
    suspend fun update(photo: SchedulePhoto)
    suspend fun delete(id: UUID)
}
```

Orden determinista por `createdAt` y UUID. La manipulación física queda detrás de otra abstracción testeable. El dominio no depende de `Context`, `Uri`, `Bitmap` ni `ContentResolver`. Cualquier ampliación pública requiere autorización de MAIN.

## 6. Room v4

Subí Room de versión 3 a 4 y agregá solamente `schedule_photos`:

- `id TEXT NOT NULL PRIMARY KEY`;
- `month TEXT NOT NULL`;
- `objectiveId TEXT NULL`;
- `objectiveNameSnapshot TEXT NULL`;
- `objectiveAbbreviationSnapshot TEXT NULL`;
- `storageKey TEXT NOT NULL`;
- `mimeType TEXT NOT NULL`;
- `byteSize INTEGER NOT NULL`;
- `pixelWidth INTEGER NOT NULL`;
- `pixelHeight INTEGER NOT NULL`;
- `createdAtEpochMillis INTEGER NOT NULL`;
- `updatedAtEpochMillis INTEGER NOT NULL`.

Índices mínimos: `month` y `storageKey` único. No crear FK a `objectives`, porque las instantáneas y fotos deben sobrevivir a eliminar una plantilla.

Crear `MIGRATION_3_4` explícita. Debe crear solo la tabla y sus índices, preservar las diez tablas anteriores y soportar la cadena 1→2→3→4. La tabla nueva empieza vacía. No usar migración destructiva ni consultas en hilo principal. Generá el esquema v4 y verificá que v1, v2 y v3 permanezcan byte a byte idénticos al commit base.

## 7. Interfaz

Calendario:

- habilitar el botón superior de fotos;
- abrir el mes seleccionado;
- ofrecer el mismo acceso desde el menú mensual;
- regresar al mismo mes al volver.

Pantalla del mes:

- título de mes/año;
- vacío claro y acción `Agregar fotos`;
- miniaturas eficientes;
- objetivo asociado cuando exista;
- menú individual para cambiar objetivo, reemplazar o eliminar;
- eliminación total del mes con confirmación explícita.

Visor:

- imagen completa con escala inicial apropiada;
- pinza para zoom exclusivamente dentro de la imagen;
- paneo cuando esté ampliada;
- navegación entre fotos e indicador como `2 de 4`;
- decodificación muestreada para evitar cargar resolución completa innecesariamente;
- acciones y descripciones accesibles.

No cambies la cuadrícula ni estados del Calendario.

## 8. Política visual

MiGuardia mantiene tipografía, escala y distribución predeterminadas. No consultes ni uses `font_scale`, zoom del sistema, tamaño de visualización, `densityDpi`, `wm density` ni dimensiones como sustituto. No modifiques esos valores durante pruebas.

El zoom autorizado es solamente el gesto sobre la fotografía en el visor.

## 9. Errores obligatorios

Cubrir selector cancelado, URI ilegible, proveedor temporalmente inaccesible, contenido no decodificable, espacio insuficiente, fallo de copia, fallo Room posterior, archivo ausente, eliminación parcial, reemplazo fallido, recreación y cierre inesperado durante importación.

Mostrar mensajes en español con acción concreta, sin rutas o excepciones internas. En selección múltiple informar cantidad importada y fallida sin nombres privados.

## 10. Dependencias

No agregues dependencias de producción sin autorización. Preferí Android, Compose y bibliotecas existentes. Si una biblioteca de imágenes fuera imprescindible, frená y presentá necesidad, alternativa nativa, versión, mantenimiento, licencia, tamaño, privacidad, impacto y pruebas.

## 11. Pruebas

Dominio/JVM:

- modelo y validaciones;
- instantáneas históricas y orden;
- éxitos y fallos parciales;
- compensación Room/archivo;
- reemplazo fallido conserva original;
- eliminación fallida conserva consistencia;
- limpieza limitada a archivos propios;
- cancelación de corrutinas.

Room instrumentado:

- CRUD y observación;
- varias fotos y meses separados;
- `storageKey` único;
- persistencia y reapertura;
- objetivo opcional y supervivencia al eliminarlo;
- rollback;
- migración 3→4 y cadena 1→2→3→4;
- preservación de tablas anteriores y tabla nueva vacía.

Aplicación:

- ambos accesos desde Calendario;
- mes correcto, vacío e importación individual/múltiple;
- cancelación, asociación, reemplazo y eliminaciones;
- confirmaciones, error/reintento y recreación;
- archivo ausente, navegación, zoom interno y paneo;
- claro/oscuro, retrato/paisaje, insets y semántica.

## 12. Samsung físico

Probá en el Samsung Galaxy S25 Ultra/API 36 con imágenes QA ficticias, nunca cronogramas reales, fotos personales o certificados.

Ejecutá selección individual/múltiple, cancelación, reapertura, visor, zoom, paneo, navegación, asociación, reemplazo, eliminación, claro/oscuro, retrato/paisaje e insets. Eliminá todas las imágenes y archivos QA. No consultes ni modifiques fuente, zoom del sistema, tamaño de visualización o densidad.

## 13. Verificación global

Ejecutá:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Obtené desde XML conteos JVM, aplicación instrumentada, Room instrumentada, total, fallos, errores y omitidas.

Confirmá además: sin red, nube, telemetría, permisos amplios, rutas/URI en logs, datos reales, imágenes en Git, secretos, temporales huérfanos, metadatos EXIF distintos de orientación interpretados, configuraciones Room prohibidas o cambios no autorizados.

## 14. Documentación y entrega

Crear `docs/adr/0008-fotos-mensuales-y-persistencia-local-v4.md` con Photo Picker, almacenamiento privado, separación Room/archivos, compensación, metadatos, migración, privacidad y límites.

Antes de devolver: revisar `git status`, diff completo, no rastreados, `git diff --check`, esquemas, permisos, secretos, imágenes accidentales, datos privados y artefactos. No hagas commit, push ni merge.

Informá a MAIN:

- resultado y decisiones;
- contratos y almacenamiento final;
- consistencia Room/archivos;
- Room v4, migraciones e identidades;
- archivos y defectos corregidos;
- comando, conteos, lint y empaquetados;
- recorrido físico y limpieza;
- dependencias, permisos, privacidad y Git;
- ruta del worktree y commit base;
- instrucciones de integración.

No declares ejecutada una verificación que solo hayas diseñado o inferido.
