# ADR 0008: fotos mensuales y persistencia local v4

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-14
- Autoridad: MAIN, después de auditar la entrega de `FOTOS_MENSUALES_DEL_CRONOGRAMA`

## Contexto

MiGuardia necesita conservar una o varias imágenes de referencia por mes sin acceder a toda la galería, interpretar cronogramas ni exponer datos privados. Los metadatos relacionales deben migrar sin modificar las diez familias de Room v3 y los bytes deben seguir siendo privados.

## Decisión

- La selección usa Photo Picker para imágenes, sin permisos generales de almacenamiento.
- Cada selección se copia mediante streaming a `filesDir/schedule_photos/`; no se conserva dependencia del URI original ni se guarda una ruta absoluta.
- Los nombres físicos son UUID opacos. No contienen nombre original, objetivo ni fecha laboral.
- `SchedulePhoto` conserva mes, referencia opcional al objetivo, instantáneas históricas, clave relativa, MIME, tamaño, dimensiones e instantes.
- Room v4 agrega solamente `schedule_photos`, un índice por mes y un índice único por `storageKey`. No hay FK a objetivos.
- `MIGRATION_3_4` crea la tabla y los índices. Los esquemas v1, v2 y v3 permanecen inmutables y la cadena 1→2→3→4 es obligatoria.
- La importación usa temporal y compensación si Room falla. Eliminación y reemplazo conservan el archivo anterior hasta que la escritura relacional pueda confirmarse.
- El visor decodifica con muestreo y ofrece zoom interno/paneo; no consulta zoom, fuente o densidad del sistema.

## Privacidad y límites

No hay red, nube, telemetría, EXIF, OCR, recorte, importación Excel, cámara propia, exportación ni copia de seguridad en este incremento. No se admiten imágenes de certificados médicos. Las futuras copias deberán tratar metadatos y archivos como una unidad consistente y consciente del usuario.

## Consecuencias

- Desinstalar MiGuardia elimina las copias privadas, conducta coherente con datos locales de la aplicación.
- Room y archivos no forman una única transacción; la implementación usa temporales, renombrado y compensación, y sus fallos deben probarse.
- Eliminar una plantilla no borra fotos ni sus instantáneas.
