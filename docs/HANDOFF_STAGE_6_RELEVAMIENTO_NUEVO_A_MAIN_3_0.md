# Stage 6 — Registro visual nuevo: handoff a MAIN

Fecha: 2026-10-11. Especialista Stage 6.

**Decisión:** registro visual nuevo entregado: 41 capturas reales, 24 apartados,
diagnóstico sustentado en esas imágenes y entrada concreta para comparar temáticas.
La entrega anterior queda **OBSOLETA** por instrucción de Joaquín, conservada y
excluida. No se eligió temática ni se modificó el producto. Revisión de MAIN y
aceptación humana pendientes; no altera las puertas de Stage 5 o distribución.

## Entrega

- [Atlas local](../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/index.html).
- [Diagnóstico navegable](../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/diagnostico.html).
- [Diagnóstico durable y evidencia](audits/2026-10-11-stage6-relevamiento-visual-nuevo.md).
- [Cobertura y límites por apartado](../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/cobertura.html).
- [Manifest con metadatos y revisiones](../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/manifest.json).

Abrir `index.html` conservando la carpeta y `originals/` juntos. CSS y lógica
incorporados, sin recursos remotos ni servidor requerido. La carpeta de evidencia
está ignorada por Git: debe preservarse localmente para mantener útiles estos enlaces.

## Entrada, autoridad y delta propio

Puerta 0: checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, remoto MiGuardia, un worktree,
staging vacío. Se preservó la entrada modificada, incluidos cambios de pruebas y
la eliminación heredada. Sello de 962 rutas en `entry-repository-seal.json`.

Joaquín autorizó Samsung/QA y luego pidió arrancar desde cero y marcar lo anterior
obsoleto. Se ajustó el contrato a ese objetivo y se agregó aviso al handoff/auditoría
anteriores sin reescribir sus cuerpos. Se crearon únicamente evidencia/helpers
ignorados, esta entrega y diagnóstico; se actualizaron contrato, índice y STATUS.
No hubo cambios ejecutables, commits, push, instalación, firma o publicación.

Samsung SM-S938B/API 36, 1440 × 3120, retrato, Claro, zoom interno 100 %.
QA `com.blackatsystems.miguardia.qa`, APK SHA-256
`6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`.
Datos recibidos ficticios REC y vista previa ficticia de avisos. No se guardaron
entidades ni se editaron/borraron registros. No se activaron servicios ni se
abrieron selectores personales, launcher o enlaces externos. Pública sólo tuvo
consulta de metadatos/hash; no se abrió ni se leyeron sus datos.

## Diagnóstico para priorizar

1. **V01/P2, visible:** cifra `32 h` fragmentada por etiqueta larga en horas (04).
2. **V02/P2, visible:** etiqueta de nota médica llega hasta el interruptor en Informes (23).
3. **V04/P2, inferido:** acciones activas grises se parecen a controles deshabilitados (18–20, 32).
4. **V03/V05/V06/P3:** elipsis mensual, peso de instrucciones/contenedores y vocabulario/fechas variables.
5. **V07/P3, estético:** evaluar jerarquía tipográfica y ritmo visual; no es una temática seleccionada.

Conservar mes completo, estados con icono/texto además de color, detalle con horario
completo, Resumen legible, selección con contador e inclusiones privadas conscientes.
Cada observación tiene impacto, objetivo y PNG precisos en el diagnóstico.

## Validación y límites

`python tools/docs_check.py`: OK (39.998/40.000 caracteres). `git diff --check`: OK.


- 41/41 originales revisados visualmente y por privacidad por este especialista.
  SHA-256 y decodificación verificados; sin retoques ni imágenes del corpus obsoleto.
- 204 enlaces locales y seis comprobaciones de filtros pasaron. La política del
  navegador bloqueó `file:`; vista previa renderizada PENDIENTE, sin eludir el bloqueo.
- `docs-check.txt`, `diff-check.txt`, `atlas-validation.json`, `artifact-verification.json`
  y `repository-exit.json` conservan los recibos finales de documentación/frontera.
- Hashes instalados QA/público iguales a entrada; QA quedó en Calendario sin
  selección/borrador, Claro/100 % conservados (`device-exit.json`, capturas 34 y 40).
- La muestra no certifica QA integral, contraste medido, áreas táctiles, fluidez,
  comprensión humana u otras configuraciones/dispositivos. No hubo cambios de
  producto que exigieran Gradle/JVM/instrumentación.

## Próxima acción de MAIN

Revisar la entrega nueva y abrir manualmente el atlas local. Preparar hasta tres
temáticas sobre las mismas seis bases: **09 Calendario, 13 detalle, 19 carga con
catálogo, 22 Resumen, 23 Informes y 35 Ayuda**. Joaquín elige antes de implementar.
Comparar lectura rápida, jerarquía, disponibilidad de acciones, densidad,
consistencia y privacidad. Preservar dominio, navegación/borradores, accesibilidad,
claro/oscuro y zoom 100/150/200. Llevar V01/V02 como casos de regresión posteriores.
No retomar la matriz ni los pendientes visuales del corpus declarado obsoleto.
