# Poda y pausa verificadas · 4 de noviembre de 2026

Joaquín pidió detener los stages, auditar los archivos, hacer un respaldo y podar los históricos. MAIN y Stage 12.1 confirmaron el cese; sus chats quedaron inactivos. El estado operativo está en [STATUS](../STATUS.md).

## Hallazgos y cambio aplicado

- STATUS seguía en EN_CURSO; el checkpoint mezclaba ACTIVE_STAGE12_1 con PAUSADO_POR_USUARIO. Se unificó la pausa y se compactó el cursor de 247.579 bytes, preservando el original.
- El contrato 12.1 arrancaba desde Selection y seis métodos ya recibidos. Ahora parte de C097, del replay retenido pendiente de revisión y de las obligaciones residuales.
- La matriz Stage 12 acumulaba autorizaciones AVD/Samsung sucesivas y referencias a primeras candidatas. Se conservan oráculos, inventario, niveles, aislamiento, privacidad, permisos y cierre; las instrucciones sustituidas quedan en el archivo.
- Se retiraron 344 documentos históricos, incluidos despachos, handoffs, auditorías y cinco ADR completamente sustituidos. El producto, las fichas sectoriales, los ADR vigentes/parciales y los requisitos necesarios siguen disponibles.
- Se archivaron 40.368 archivos en total, con 5.661.535.076 bytes. La evidencia actual Stage 12/12.1, los insumos usados por el operador Stage 11 y su comparación Stage 10 permanecen en el checkout.

## Respaldo y conservación

El respaldo externo `MG3-backup-20261103-poda` contiene 178.281 archivos y 18.734.247.046 bytes: checkout completo, Git, cambios sin commit, ignorados y custodia privada externa. Cada destino se comparó por SHA-256 y se releyeron los originales para confirmar estabilidad. La prueba de restauración cubrió STATUS, una fuente de Disponibilidad, el checkpoint y HEAD.

Identidad del manifiesto completo: `8de54be44755252f072ff154e65a5153bc52439b719e670a4a987031fc45239a`. Ubicación y recuperación en [EVIDENCIA](../EVIDENCIA.md); originales documentales en el [catálogo](../archive/catalog.json).

Se conservan código Android, pruebas, esquemas, Gradle, cachés, trabajo previo y toda evidencia sellada vigente. El verificador documental se adapta al respaldo externo y distingue explícitamente la comprobación local completa de `--live-only` en un clon sin archivo.

## Validación

- Conservación SHA-256: 30.044 archivos de app, core, Gradle, compilados y cachés, con 740.603.754 bytes, idénticos al respaldo. Los cinco insumos sellados señalados por el acta de cese también permanecen idénticos.
- Matriz: los 425 casos funcionales y 16 criterios visuales mantienen sus filas literalmente; V5 conserva su hash. El primer contador de esta auditoría omitió la familia V; se corrigió el selector y se contrastaron los 441 IDs.
- Documentación: comprobación de enlaces, estado, 37 ADR e integridad de los 103 snapshots históricos aprobada. Los 16 enlaces de las dos auditorías actuales también existen.
- Verificador documental: 18/18 regresiones aprobadas, incluida detección de respaldo ausente/alterado, escape de ruta, enlaces activos rotos y pausa humana.
- Batería Python completa: 153 pruebas antes y 159 después; exactamente los mismos 14 errores previos, sin nuevos errores. Los logs y su comparación quedan junto al respaldo. Los doce errores del catálogo se contabilizan como entradas de error, incluidos subtests y preparación de clase.
- Diff sin errores de espacios. HEAD y staging conservados. La poda dejó 69 archivos en docs frente a 408 al inicio.
- La revisión independiente detectó un HOLD Samsung heredado y referencias B01–B04 sin definición. Se corrigieron ambos, conservando los requisitos de regresión y remitiendo permisos a STATUS.

Windows exigió retirar el atributo de sólo lectura de un archivo de un AVD archivado; su copia ya estaba comprobada. El editor directo de AGENTS fue rechazado por el sistema de archivos y se aplicó con la herramienta de parches. Las incidencias y la lista exacta de retirados están en los recibos del respaldo.

## Límites y continuación

La poda elimina instrucciones obsoletas del recorrido de arranque y reduce archivos activos. No demuestra que toda función esté terminada. B1 sigue abierto, con 0/2 rondas; Clima tiene un fallo informado por reproducir.

Antes de esta poda, la batería Python ya presentaba 14 errores: doce entradas por clasificación/hashes del catálogo QA desactualizados y dos pruebas dependientes de un ADB ausente en la ruta estándar del SDK. Ese antecedente se conserva y no se corrige rebajando guardas. MAIN debe reconciliarlo antes de usar esos runners.

MAIN y Stage permanecen pausados. Al reanudar por indicación de Joaquín, MAIN lee el estado compacto, revisa el replay retenido y acuerda el cierre del lector de fixtures antes de otra operación. No se reabren etapas aceptadas ni se restauran permisos consumidos.
