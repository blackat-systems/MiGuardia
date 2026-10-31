# Stage 8 — revisión MAIN de la comparación final

Fecha: 2026-10-22. Campaña acotada **cerrada y auditada**; causa diferencial de
Resumen **inconclusa**. No equivale a rendimiento global aceptado ni a arreglo.
Estado operativo: [STATUS](../STATUS.md). [Entrega](2026-10-22-stage8-comparacion-cierre.md).

## Resultado comprobado

MAIN recalculó las 432 mediciones de cuatro corridas A1 → B1 → B2 → A2, con el
detector original, 12 muestras por operación y nueve operaciones. Misma fixture,
configuración observada y runner; sin trazado ni descarte de máximos/primera muestra.

| Recorrido | Primer par | Segundo par | Conclusión limitada |
|---|---|---|---|
| Guardar tres jornadas | Sin alerta | Sin alerta | No reprodujo la alerta en esta campaña |
| Abrir Resumen | Sin alerta | Mediana 1238,454 → 1608,104 ms, +29,85 % | Alerta parcial; causa no demostrada |

El delta de Resumen es 369,650 ms, superior al umbral 247,691 ms. Su máximo no
supera el umbral. No se promedian pares para esconder la alerta, ni se atribuye
la variación al orden, SQL, temperatura o arnés. La condición predefinida para
otro diagnóstico exigía alerta en ambos órdenes y no se cumplió. No se cambió
producto. Una nueva investigación o aceptación del pendiente requiere decisión
de alcance; esta campaña no se prolonga automáticamente.

## Recepción y revisión independiente

- Chat especialista `01a15c6c-5e1e-7351-a4b2-c90b771aea0b`, turno de entrega
  `01a165c8-2159-7b91-bb6f-823c06b08314`, clave `stage8-cierre-comparacion-20261022`.
- Sello de entrega SHA-256
  `dd92e8246b1ba33460224c9209605866a6dd1d5f45d0b0b3960198fe64273a4d`.
  Acuse y revisión en `.local-evidence/stage8/main-review/closure-20261022/`.
- MAIN rehasheó **1561/1561** archivos sellados y contrastó 1010 rutas de entrada
  con 1011 actuales antes de sus propias escrituras. Sólo cambiaron los dos
  documentos declarados del especialista; historia del handoff conservada.
- q2: 149 archivos; q3: 50; Stage10: 64 evidencias y 13 fuentes/copias preservadas.
  HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`, rama `codex/miguardia-3.0`,
  staging vacío. Sin cambios productivos, Room, permisos, firma o versión.
- Repetición independiente **25/25 dobles offline**, con procesos externos
  bloqueados y temporales propios. El primer intento MAIN dio dos errores de
  rutas relativas por redirigir un solo HERE; se conservó, se corrigió sólo el
  adaptador MAIN y pasó completo. No era un defecto del producto/entrega.
- Revisor interno de sólo lectura contrastó 757 comandos y sus intenciones:
  cinco instalaciones `-r`, cuatro instrumentaciones, cuatro extracciones y
  trece detenciones exclusivamente QA. Sin clear, desinstalación, ajustes globales
  ni consultas al paquete público. Los rechazos previos sólo hicieron lecturas.
- El protocolo conserva resultados parseados y hashes, no stdout bruto. Se
  revisaron parser, pruebas y recibos; no se afirma reprocesamiento de una salida
  original no guardada. La respuesta inmediata de instalación conserva estado
  incierto; el recibo agregado incorpora después Success y hash instalado.

## Conservación y límites

Los recibos acreditan regreso a QA actual
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`,
runner E2E `bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
Los once hashes conocidos coinciden con A2 tras reinstalar. QA quedó detenida e
Inicio visible en la última observación; no se acredita disponibilidad actual.
No es integridad de todo el almacenamiento privado ni una prueba de upgrade.

La regeneración ficticia autorizada rompió la continuidad instalada de Stage10;
sus copias/evidencia siguen intactas. No se puede cerrar aquella relectura con
datos nuevos. Joaquín levantó la pausa por reinicio y pidió reanudar dependencias:
se habilita únicamente el cierre local de Stage10 con lo ya guardado, sin repetir
borrados ni otra prueba física. Stage9 y baterías válidas no se repiten.

## Evidencia y siguiente dueño

En la revisión MAIN: `independent-comparison.json`, `independent-seals.json`,
`independent-offline-attempt-1.json`, `independent-offline-final.json`, adaptadores
de revisión y acuse. En la entrega: `writer-release.json`, `repository-exit.json`,
recibos/muestras por corrida y fallos de preparación preservados.

No hubo ADB ni Gradle en MAIN. El especialista confirmó cese y no reanudación
tras el reinicio. MAIN conserva pendiente causal de Resumen y audita la salida
local de Stage10. No aceptación visual/global, firma, publicación ni commit.
