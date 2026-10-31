# Stage 4 — protocolo y límites de rendimiento

Estado: **PENDIENTE de medición Android**. No hay comparación ni afirmación de fluidez integral. Este protocolo queda fijado antes de optimizar rendimiento; los cambios actuales corrigen navegación, presentación e integridad.

Ensayo MAIN posterior: [QA física](2026-10-02-stage4-qa-main.md). Se corrigió la
abreviatura inválida del fixture; baseline y candidato tienen muestras incompletas,
sin iteraciones representativas completas. El driver se detuvo por foco en otro
paquete durante la carga. La comparación sigue pendiente: no usar esas muestras
para declarar mejora, regresión de latencia o FPS. Conservar los recibos adversos.

## Identidad y condiciones

Baseline: QA Stage 3.2 recibida, conservada en `.local-evidence/stage4/entry/app-qa.apk`, SHA-256 `fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`. El HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae` solo no representa ese producto: el manifiesto de entrada conserva el checkout acumulado. Un runner nuevo nunca hereda el recibo del runner anterior. Su compatibilidad con el APK baseline también queda pendiente de ejecución: un error de enlace del runner sería un fallo del harness, no una métrica del producto.

Usar el mismo Samsung QA disponible, variante QA, runner, orientación, tema y zoom interno 100 para ambos productos. Registrar SHA de producto/runner, serial/API, fecha, mes ancla y hash del snapshot lógico inicial. Comparar sólo series cuyo mes, hash y cantidades iniciales coincidan. El driver fija tema claro, zoom 100, preferencias predeterminadas de Resumen y Clima desactivado; registrar también las condiciones observables de la corrida. No consultar ni cambiar ajustes globales de pantalla. No mezclar mediciones de emulador con las del teléfono. No borrar datos de la app pública.

El teléfono conectado no equivale a disponible. Con disponibilidad pendiente no se instala, instrumenta ni trae al frente la QA. Si el foco cambia, interrumpir y conservar lo ya medido sin capturar otras apps.

## Datos y recorridos

`Stage4FlowsActivityTest` prepara dos conjuntos deterministas independientes: representativo de 240 jornadas en 365 días con 24 notas; estrés de 1.920 jornadas en 2.920 días con 192 notas. El representativo empieza once meses antes del mes ancla; el estrés empieza 95 meses antes, para cubrir unos ocho años previos y el mes actual. Ambos contienen turnos diurnos/nocturnos y cancelaciones. Inicialmente tienen **0 adjuntos y 0 repeticiones**: no son una prueba de capacidad de imágenes o recurrencia. Cada iteración agrega tres jornadas en un mes futuro separado, y comprueba su persistencia; informar esa evolución, no tratar las doce iteraciones como snapshots iguales.

Se guardan muestras individuales de doce iteraciones: apertura de Activity con proceso caliente, cambio de mes, detalle con jornada cargada, entrada a carga, catálogo, vuelta a selección, previsualización de tres jornadas, guardado y Resumen con proyección actual. La espera termina por estado/contenido real y sincronización Compose/foco; una etiqueta dibujada sola no certifica datos listos. El costo del driver y de esa sincronización forma parte de la latencia observada. Combina clics UI con llamadas a ViewModels: no certifica gestos físicos, selector OEM ni teclado. El detalle espera su fecha y la expectativa contextual cargada, además de la tarjeta visible. El driver verifica el paquete activo antes de operar y al esperar; sólo sus aperturas/recreaciones/cierres admiten transiciones propias por el launcher.

Arranque frío requiere una serie aparte después de `seedRepresentativeForColdStartMeasurements`: detener exclusivamente QA y abrir su Activity con proceso nuevo, registrar `am start -W` y, por separado, cuándo el Calendario tiene contenido. `TotalTime` por sí solo mide la apertura reportada por Android, no datos listos. Si sólo se obtiene ese dato, mantener pendiente la latencia de contenido frío. No presentar la apertura de Activity del test como arranque frío.

Los FrameMetrics registran duración total de frames durante cada sesión después del primer Calendario listo; excluyen arranque. Informar cantidad, percentiles, frames por encima de 16,67 ms y muestras descartadas por el listener. Ese umbral fijo sirve como comparación común: no certifica jank del dispositivo, su frecuencia efectiva ni 60 fps. No extraer rendimiento de capturas.

## Análisis y umbrales

Conservar todas las muestras, fallos e iteraciones incompletas. Calcular p50 y p95 por operación con método de rango más próximo, indicando n; con n=12, p95 corresponde al máximo y es sensible al ruido. No sustituir por un promedio ni borrar atípicos para obtener verde.

**Regla fijada antes de medir:** investigar una diferencia de p50 mayor que `max(20 % del baseline, 100 ms)` o p95 mayor que `max(25 % del baseline, 150 ms)`. Son umbrales de investigación para un driver de integración, no promesas de producto. Una repetición completa del par baseline/candidato bajo condiciones equivalentes distingue ruido de regresión; conservar ambos pares. Si el baseline ya es lento, estar dentro del umbral no demuestra una experiencia aceptable.

Optimizar sólo después de identificar un cuello de botella reproducible en las muestras y su causa. No retirar validación global, ampliar transacciones ni reutilizar datos obsoletos para bajar una cifra. La revisión humana de espera/feedback sigue separada.

## Resultados

| Serie | Baseline | Candidato | Conclusión |
|---|---|---|---|
| Representativo, nueve operaciones | PENDIENTE | PENDIENTE | Sin comparación disponible |
| Estrés identificado por separado | PENDIENTE | PENDIENTE | Sin comparación disponible |
| Arranque frío y contenido listo | PENDIENTE | PENDIENTE | No confundir con Activity caliente |
| Frames durante recorridos | PENDIENTE | PENDIENTE | Sin afirmación de fps |

Los JSON previstos se escriben en `files/stage4/performance-{representative,stress}.json` dentro de QA; el recibo del runner y los hashes de APK determinan a qué corrida pertenecen. Una extracción sin esos vínculos no basta para atribuir resultados al candidato final.
