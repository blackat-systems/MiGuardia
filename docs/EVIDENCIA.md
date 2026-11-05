# Evidencia, archivo y recuperación

El estado vigente está en [STATUS](STATUS.md). Los originales de la poda autorizada del 3 de noviembre de 2026 se conservan en un respaldo externo íntegro, con Git, cambios locales, evidencia ignorada y custodia privada. El respaldo se llama `MG3-backup-20261103-poda`.

## Ubicación local verificable

`.local-evidence/archive-location.json`, ignorado por Git, indica `checkout_root` y la raíz del respaldo. Su `BACKUP-VERIFIED.json` registra la verificación SHA-256 de cada archivo, la segunda lectura de los originales y la prueba de restauración. `manifest.jsonl` permite buscar la ruta exacta, tamaño y hash. La raíz `private-evidence` conserva una copia opaca de la custodia privada externa; su ubicación original permanece intacta.

El [catálogo documental](archive/catalog.json) relaciona cada archivo retirado o sustituido con la copia anterior y su SHA-256. El [manifiesto histórico](archive/manifest.json) conserva los hashes de los snapshots documentales anteriores. Ninguno otorga autorización para ejecutar instrucciones antiguas.

## Qué permanece en el checkout

- Código, pruebas, esquemas Room, Gradle y herramientas, incluidos cambios previos a esta poda.
- Contratos de producto y ADR vigentes o parcialmente vigentes, con sus sucesores explícitos.
- Evidencia Stage 12 y Stage 12.1: todavía contiene dependencias entre operadores, recibos y capturas selladas.
- Insumos del operador de restauración Stage 11 y su ZIP de comparación Stage 10.
- Cachés y compilados actuales para conservar la compilación incremental.

Las evidencias históricas restantes se retiraron sólo después de comprobar su copia y su hash. Una reproducción histórica puede requerir restituir sus entradas desde el respaldo. Los cierres previos conservan el alcance y las diferencias documentadas.

## Recuperación puntual

1. Resolver el archivo en el catálogo o manifiesto del respaldo. Verificar que la ruta relativa permanezca dentro de la raíz indicada.
2. Comparar el SHA-256 con el manifiesto antes de copiar. Usar una carpeta temporal para inspección; no copiar el respaldo entero sobre el checkout activo.
3. Si hace falta restaurar un archivo del proyecto, comprobar su versión actual y preservar cualquier cambio posterior. Restituir sólo la entrada necesaria con permiso vigente para su uso.
4. Registrar la recuperación y su comprobación. Una autorización de ensayo consumida no se recupera al copiar su archivo.

`python tools/docs_check.py` comprueba enlaces activos, vigencia, índice y los 103 snapshots originales en el respaldo local. En un clon sin ese respaldo, `--live-only` comprueba explícitamente sólo la documentación vigente; su resultado no acredita la custodia histórica.

La evidencia privada sigue fuera de Git. No subir APK, bases, copias, capturas o logs privados. El mapa `evidence-locations.json` conserva procedencia anterior a esta poda; sus rutas archivadas se resuelven en el respaldo completo. El inventario y resultado de esta operación están en la [auditoría de archivos](audits/2026-11-04-poda-y-pausa.md).
