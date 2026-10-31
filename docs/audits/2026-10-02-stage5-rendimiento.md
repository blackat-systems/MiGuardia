# Stage 5 — rendimiento medido y límites

Sesión autorizada por Joaquín: «Autorizar; Samsung libre y desbloqueado». Samsung SM-S938B, API 36, serial [serial QA en binding local privado]. Sólo QA y datos ficticios; sin red, alarmas ni ajustes globales.

## Resultado

Las nueve operaciones de ambas series quedaron dentro de los umbrales predefinidos de investigación. No se demostró una regresión ni una mejora de rendimiento atribuible a Stage 5. En estrés, Resumen conserva una mediana de 1602,3 ms: estar dentro del umbral no certifica una experiencia aceptable. No se optimizó producto sin causa reproducida.

Se completaron doce iteraciones por APK/serie, mismo runner, LIGHT, zoom interno 100, Activity en retrato 1440 × 3120 px en las doce iteraciones. Sin builds concurrentes. El representativo tiene 240 jornadas/24 notas/365 días; estrés 1920/192/2920. Ambos parten de cero adjuntos y repeticiones; agregan tres jornadas por iteración. No es capacidad de imágenes ni recurrencia. El driver mezcla UI y ViewModels, incluye sincronización Compose/foco y no certifica gestos ni teclado OEM.

Percentiles por rango más próximo, n=12 por operación; p95 es el máximo. Umbral absoluto: delta p50 > max(20 % baseline, 100 ms), o delta p95 > max(25 %, 150 ms). Ninguna fila lo superó; no se eliminaron atípicos. [Protocolo previo](2026-10-02-stage4-rendimiento.md).

## Identidades y recibos

- representative/baseline: `.local-evidence/qa/20261002T233211.490686Z/manifest.json`; muestras SHA-256 `dd273a26ed0e2a88d84d06823a94ab1bb79c340797aaa774c7e9a42d11dd6cc8`.
- representative/candidate: `.local-evidence/qa/20261002T234014.518145Z/manifest.json`; muestras SHA-256 `0b6ff651415df611e90f163f3f9616619ddfc8129b55b00475204fa2265d298c`.
- stress/baseline: `.local-evidence/qa/20261002T233313.647784Z/manifest.json`; muestras SHA-256 `30ffb155fb8a0473b1d1bb438c0096c53a9cb1476df89d4f971edb43dd4053a5`.
- stress/candidate: `.local-evidence/qa/20261002T234049.911753Z/manifest.json`; muestras SHA-256 `dd96cbdcab80d70a6e02ab2350bb5187138e381201379135148b7fb8d09dbc38`.

Runner común: `85915a760345fa62fa0804a589f8934399674002e6258fe3103a839e0a2769cc`.
Baseline QA: `fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`.
Candidata QA: `4d708c1f6e398e0675dac885b65b8728c992488a796ed22280f77d72b4330b30`.
Cada recibo verifica hash local/instalado antes y después. El helper declara explícitamente el APK baseline; no reasigna su identidad a la candidata.

## representative

Snapshot inicial coincidente: `a06159feee38b88842194a67714eb28f1fcf0e34bd973ef3b5dde5c931101b8b`. Mes ancla 2026-10.

| Operación | Baseline p50 | Candidata p50 | Baseline p95 | Candidata p95 |
|---|---:|---:|---:|---:|
| warm_activity_start | 387.3 | 369.8 | 944.5 | 920.7 |
| change_month | 128.7 | 146.3 | 277.1 | 292.7 |
| open_detail | 219.3 | 246.8 | 397.2 | 392.8 |
| enter_load | 146.1 | 163.3 | 179.3 | 196.7 |
| show_catalog | 83.4 | 84.0 | 91.7 | 92.3 |
| return_to_selection | 97.4 | 97.4 | 157.4 | 158.4 |
| preview_three_shifts | 165.2 | 174.2 | 250.7 | 207.1 |
| save_three_shifts | 289.7 | 306.3 | 614.2 | 522.0 |
| open_summary | 269.9 | 360.9 | 407.2 | 415.1 |

Valores en ms. Frames después del primer calendario listo; excluyen arranque:

| APK | n frames | p50 ms | p95 ms | >16,67 ms | Descartados |
|---|---:|---:|---:|---:|---:|
| baseline | 380 | 9.12 | 66.76 | 127 | 0 |
| candidate | 391 | 11.29 | 69.81 | 153 | 0 |

## stress

Snapshot inicial coincidente: `e99b2e1abd6605e37239d8521e48105b7e6fab2ba926741e5c6e910d16ce4240`. Mes ancla 2026-10.

| Operación | Baseline p50 | Candidata p50 | Baseline p95 | Candidata p95 |
|---|---:|---:|---:|---:|
| warm_activity_start | 969.4 | 967.6 | 1857.2 | 1627.7 |
| change_month | 622.3 | 639.0 | 680.3 | 687.7 |
| open_detail | 179.8 | 191.6 | 450.9 | 481.5 |
| enter_load | 265.0 | 264.4 | 288.1 | 296.7 |
| show_catalog | 91.6 | 92.4 | 101.2 | 100.8 |
| return_to_selection | 124.6 | 134.7 | 158.2 | 195.8 |
| preview_three_shifts | 381.4 | 357.3 | 539.7 | 489.7 |
| save_three_shifts | 555.2 | 563.0 | 692.4 | 698.3 |
| open_summary | 1448.7 | 1602.3 | 1756.5 | 1715.5 |

Valores en ms. Frames después del primer calendario listo; excluyen arranque:

| APK | n frames | p50 ms | p95 ms | >16,67 ms | Descartados |
|---|---:|---:|---:|---:|---:|
| baseline | 430 | 13.31 | 89.19 | 196 | 0 |
| candidate | 390 | 15.75 | 98.99 | 190 | 0 |

El umbral fijo de frames no prueba jank a la frecuencia efectiva ni FPS aceptables. No sumar duraciones de frames a latencias de operación.

## Arranque frío separado

Doce force-stop exclusivos de QA por APK; pidof devuelve código 1 y salida vacía antes de cada apertura. Se registra `am start -W` TotalTime. No se captura ni lee jerarquía de ventanas. **Contenido frío listo pendiente**: TotalTime sólo acredita apertura reportada por Android.

| APK | n | p50 TotalTime ms | p95 TotalTime ms | Evidencia |
|---|---:|---:|---:|---|
| baseline | 12 | 463 | 481 | `.local-evidence/stage5/device/cold-baseline-20261002T233942.697060Z/result.json` |
| candidate | 12 | 488 | 513 | `.local-evidence/stage5/device/cold-candidate-20261002T234243.352691Z/result.json` |

La orientación y dimensiones de las doce ventanas frías no se midieron; las dimensiones del seed no acreditan las aperturas posteriores a force-stop. Por eso estos TotalTime son descriptivos y su equivalencia de ventanas queda pendiente.

Semilla fría representativa idéntica, comprobada por hash lógico y recibo del mismo APK/runner. Sin controles de temperatura/frecuencia global ni afirmación causal a partir de diferencias pequeñas. El script y los JSON crudos quedan en `.local-evidence/stage5/`.

## Foco y evidencia adversa

El ensayo diagnóstico `20261002T231608.449326Z` se detuvo al entrar a carga: paquete launcher, Activity QA STARTED sin foco. Se preservaron muestras incompletas y diagnóstico privado de paquetes/lifecycle. El driver usaba directamente el dispatcher de Activity para cerrar ModalBottomSheet; omitía la ventana del diálogo y salía de QA. Se reemplazó por Atrás nativo de Espresso. Con el guard intacto, el diagnóstico posterior completó doce iteraciones (`20261002T232120.844630Z`) y luego los dos pares finales completaron. Esto explica la reproducción Stage 5; los recibos Stage 4 sin esos metadatos no permiten afirmar que todos sus cambios de foco tuvieran esa causa.

No se leyó ni capturó contenido ajeno. El latch detiene mutaciones tras pérdida de foco y el catálogo corta entre métodos. El helper conserva cada extracción antes de repetir; una extracción ausente/incompleta falla aunque JUnit haya pasado. Los pares iniciales sí tienen JSON completos verificados. Un script frío con dump global fue rechazado en revisión antes de ejecutarse y sustituido por TotalTime sin capturas.
