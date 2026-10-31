# Evidencia local y conservación

Los resúmenes auditables sin datos privados se registran en `docs/audits/`. Los artefactos detallados viven en `.local-evidence/`, ignorado por Git y fuera de los directorios que limpia Gradle.

El 2026-09-12 se ejecutó una compactación previa a 3.0. Se conservaron las rutas relativas de la evidencia útil y un `curated-manifest.json` con sus hashes. Se retiraron discos de emulador, APK técnicas repetidas y datos crudos de prueba una vez preservados sus manifiestos y cierres. El inventario previo completo y la recuperación de worktrees están fuera del repositorio en `C:\Users\Joaquin\Desktop\MiGuardia-Pre3-Archive-20260912-r2`; detalle en `docs/audits/2026-09-12-higiene-previa-miguardia-3.md`.

Ubicación localizada el 2026-10-20 tras el traslado de perfil: `C:\Users\User\Documents\03_Proyectos\MiGuardia\Archivo_historico\MiGuardia-Pre3-Archive-20260912-r2`. Contiene `archive-manifest.json`, el bundle Git y `evidence-curated/curated-manifest.json`. La ruta anterior se conserva como procedencia; esta localización no equivale a revalidar todo el archivo externo. La comprobación del proyecto actual está en [la auditoría del traslado](audits/2026-10-20-integridad-y-rutas-del-workspace.md).

- `optimization/`: ejecuciones del verificador local/interoperabilidad/build.
- `qa/`: planes/resultados de instrumentación expresamente autorizada.
- `optimization-input/`: fotografía y demostración mecánica de la extracción UI.
- `legacy/2026-09-06/`: evidencia previa conservada con comparación SHA-256.

Los mapas de ubicaciones anteriores están en `docs/evidence-locations.json`. Las referencias de auditorías antiguas a `app/build/beta-evidence`, `app/build/updates28` o `build/support27` se resuelven allí. Se verificaron las copias completas; cuando Windows impidió mover una carpeta se conservó el original además de la copia durable. No hay una instrucción de borrar esos originales ni las cachés.

Dentro de cada conservación local existe un manifiesto de hashes. No subir esos directorios: pueden contener capturas, logs, material QA o archivos privados heredados. Los runners nuevos usan listas permitidas de metadatos y no copian bases personales.

Antes de retirar evidencia local: comprobar que el cierre y las huellas necesarios quedaron registrados, identificar datos sensibles, verificar el destino dentro del proyecto y conservar lo no reconstruible. No eliminar por antigüedad automáticamente ni usar `clean` como política de archivo.

Los snapshots documentales en `docs/archive/` sí son versionables: preservan fuentes sin secretos con un manifiesto verificable. `.gitattributes` desactiva la normalización de finales de línea sólo en la fotografía documental, para que Git conserve sus hashes al guardar/recuperar. No son órdenes activas. Los resultados de otro commit/artefacto son antecedentes y no acreditan una ejecución nueva.
