# Archivo documental fuera del árbol activo

La poda del 3 de noviembre de 2026 retiró despachos vencidos, handoffs cerrados, diagramas históricos y auditorías sustituidas. Los originales íntegros permanecen en `MG3-backup-20261103-poda`, junto con el estado completo anterior a la poda.

- [Catálogo de archivos](catalog.json): ruta original, ruta de la copia y SHA-256.
- [Manifiesto de snapshots previos](manifest.json): los 103 hashes históricos originales, conservados.
- [Ubicación local, comprobación y recuperación](../EVIDENCIA.md).

Buscar un nombre anterior en el catálogo y resolverlo mediante el localizador ignorado por Git. Restaurar sólo una entrada necesaria y comprobar su hash. Las instrucciones históricas explican decisiones previas; STATUS y los contratos vigentes gobiernan cualquier trabajo nuevo.
