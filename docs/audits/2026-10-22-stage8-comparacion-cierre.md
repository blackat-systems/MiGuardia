# Stage 8 — comparación de cierre del 22/10/2026

## Resultado y alcance

**Campaña A1 → B1 → B2 → A2 completa; reproducción parcial de la alerta de
Resumen; causa diferencial R4 INCONCLUSA.** Guardado no supera el umbral en
ninguno de los pares. En Resumen, sólo el segundo par supera el umbral de p50:
1238.454 → 1608.104 ms, diferencia 369.650 ms (29.85 %) frente a 247.691 ms
permitidos por la regla de investigación. Su p95 no supera el umbral. El primer
par no presenta alertas en ninguna de las nueve operaciones.

Esto no demuestra una corrección ni una regresión estable atribuible al producto.
Tampoco demuestra que el orden haya causado la variación: se observan dos pares
distintos, sin aislamiento causal del orden, cachés, temperatura o planificación.
La hipótesis de un exceso estable de trabajo de UI en R4 no queda confirmada.
El diagnóstico adicional requería persistencia en ambos órdenes; no se activó.
No se ejecutaron nuevos trazados ni se amplió la campaña.

Contrato vigente: [comparación acotada](../prompts/STAGE_8_CIERRE_COMPARACION_RENDIMIENTO_3_0.md).
Estado y aceptación pertenecen a [STATUS](../STATUS.md) y a MAIN. La revisión q3
anterior sigue siendo evidencia local auditada e inconclusa; no fue reabierta.

## Base, autorización y atribución

- Checkout: `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`.
- Rama `codex/miguardia-3.0`; HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Entrada acumulada: 1010 rutas versionables, staging vacío, un worktree.
  Se preservan cambios y eliminación heredados; este bloque no los incorpora.
- Clave única: `stage8-cierre-comparacion-20261022`.
- La autorización posterior de Joaquín fue verificada en `001 · MAIN`, turno
  `01a165c1-67df-7730-9da9-ee8d21a1da74`: «Finalicemos stage 8», como respuesta
  a la pregunta explícita sobre instalar ambas QA y reemplazar sólo sus datos
  ficticios. Además confirmó reconexión, desbloqueo e Inicio durante este ensayo.
- Stage 10 estaba pausado. Sus archivos permanecen intactos; la regeneración de
  la fixture rompe la continuidad de sus datos instalados. No se retomó ese ensayo.
- Únicos documentos propios: esta auditoría y una sección inicial nueva del
  [handoff](../HANDOFF_STAGE_8_A_MAIN_3_0.md). Helpers, recibos y muestras nuevos
  quedan en `.local-evidence/stage8/closure-20261022/`.

## Preparación y protección de datos

Se comprobaron hashes de cinco APK preservados, paquetes, versión, certificado
técnico y firmas mediante herramientas ya disponibles. A/B/C son QA con
versionCode 6; E/D apuntan al mismo paquete QA. La compatibilidad de E con A/B
tenía antecedentes físicos y quedó comprobada en las cuatro corridas nuevas.
D sólo recibió preflight de manifiesto/certificado, sin validación ABI de ejecución.
Los cinco hashes están en `selection.json`; no hubo build ni firma nueva.

Antes de reemplazar la fixture se compararon diez hashes conocidos de Stage 10:
base SQLite, marcador sintético, preferencias, foto sintética y archivos técnicos.
El WAL estaba vacío y coincidía con el SHA-256 de cero bytes. Las referencias se
validan contra el manifiesto sellado de la pausa. No se enumeraron datos privados
ni se leyeron contenidos personales. Antes de cada corrida, los hashes se
contrastaron con la salida anterior y otra vez antes de instrumentar.

Hubo dos rechazos previos a cualquier ensayo:

1. `operations/probe/`: Samsung ausente, salida ADB 1. El texto del fallo se
   identificó por coincidencia exacta de SHA-256; no se repitió una operación
   incierta. Joaquín reconectó el equipo y confirmó que estaba en Inicio.
2. `operations/A1/`: la guarda de vigilia esperaba una única línea. El Samsung
   informa `mWakefulness=Awake` y además `mWakefulness=1`. La lectura acotada
   `operations/awake-diagnostic/` comprobó ese formato. Se corrigió el helper
   para aceptar ambos campos consistentes, manteniendo rechazo ante estados
   contradictorios, desconocidos o sólo numéricos. El intento quedó intacto;
   `operations/A1-retry/` contiene la primera y única corrida medida A1.

Ninguno de esos rechazos instaló APK, inició instrumentación o regeneró datos.
La batería final del coordinador pasa **25/25 tests offline**. Cubre errores
lógicos aun con exit 0, exit no cero, timeout/estado desconocido, identidad,
bloqueo/foco/vigilia, extracción binaria, procedencia y protocolo Android.
Se preservan logs anteriores de 21 y 24 tests; no se suman como tests distintos.

## Ejecución y comparabilidad

Cuatro métodos Android completos: 4 aprobados, 0 fallidos/omitidos/pendientes.
Selector único: `Stage4FlowsActivityTest#stressHistoryMeasurements`; mismo runner
E2E E, sin method tracing. Fuente del detector intacta:
`3331ede6d038657939629a5a9a9bb86f33d246b2314cb8a144f160d5b7f00ad7`.

- Orden físico real: A1, B1, B2, A2; cuatro series de 12 × 9, **432 muestras**.
- Fixture inicial idéntica: 1920 jornadas, 192 notas, 2920 días, 0 adjuntos y
  0 planes recurrentes; inicio 2018-11-01 y mes ancla 2026-10.
- SHA lógico inicial idéntico:
  `e99b2e1abd6605e37239d8521e48105b7e6fab2ba926741e5c6e910d16ce4240`.
- Samsung SM-S938B, API 36, serial [serial QA en binding local privado]; 1440 × 3120, vertical,
  font scale 0.8999999761581421, densidad 600 dpi, tema LIGHT.
  Zoom legado 100 es metadato de la fixture. No se tocaron ajustes globales.
- Cada iteración agrega y comprueba tres jornadas: 1956 al final es el total
  esperado de esas aserciones, **no un conteo independiente de la base final**.
  El detector de rendimiento no sustituye un ensayo de integridad o de upgrade.
- Se extrajo cada JSON por transporte binario limitado, con coincidencia entre
  hash local y dos hashes remotos, antes de regenerar la corrida siguiente.
- No se eliminó ninguna muestra, máximo ni primera iteración. Frames completos
  permanecen en los JSON; no se deriva una afirmación de FPS o aceptación visual.

Regla inalterada: nearest-rank, n=12, p50 sexta muestra ordenada y p95 máximo.
Investigar delta p50 > max(20 % de base, 100 ms), o delta p95 > max(25 % de
base, 150 ms). La igualdad no dispara alerta. Se validaron ambos pares con
el helper histórico puro y un recálculo directo de percentiles y umbrales.
`comparison.json` conserva valores completos; tablas redondeadas a 0.001 ms.

### Primer par: base A1 y después R4 B1

| Operación | Base p50 / p95 (ms) | R4 p50 / p95 (ms) | Delta p50 / p95 (ms) | Umbral p50 / p95 (ms) | Alerta |
|---|---:|---:|---:|---:|---|
| Activity caliente | 909.450 / 1513.998 | 958.780 / 1529.974 | 49.329 / 15.976 | 181.890 / 378.499 | No |
| Cambiar mes | 598.459 / 696.655 | 579.969 / 687.543 | -18.490 / -9.112 | 119.692 / 174.164 | No |
| Abrir detalle | 301.561 / 645.359 | 171.182 / 465.211 | -130.379 / -180.148 | 100.000 / 161.340 | No |
| Entrar a carga | 264.048 / 348.648 | 264.154 / 357.227 | 0.106 / 8.579 | 100.000 / 150.000 | No |
| Mostrar catálogo | 85.171 / 100.640 | 91.459 / 100.724 | 6.288 / 0.085 | 100.000 / 150.000 | No |
| Volver a selección | 123.915 / 136.670 | 121.137 / 156.017 | -2.777 / 19.348 | 100.000 / 150.000 | No |
| Previsualizar tres jornadas | 349.783 / 406.325 | 348.076 / 540.393 | -1.707 / 134.068 | 100.000 / 150.000 | No |
| Guardar tres jornadas | 548.335 / 630.430 | 530.990 / 753.512 | -17.345 / 123.083 | 109.667 / 157.607 | No |
| Abrir Resumen | 1292.765 / 1668.424 | 1401.641 / 1644.527 | 108.876 / -23.897 | 258.553 / 417.106 | No |
### Segundo par: R4 B2 y después base A2

| Operación | Base p50 / p95 (ms) | R4 p50 / p95 (ms) | Delta p50 / p95 (ms) | Umbral p50 / p95 (ms) | Alerta |
|---|---:|---:|---:|---:|---|
| Activity caliente | 1082.895 / 1801.891 | 991.891 / 1724.497 | -91.004 / -77.394 | 216.579 / 450.473 | No |
| Cambiar mes | 704.624 / 854.138 | 592.161 / 864.079 | -112.463 / 9.941 | 140.925 / 213.535 | No |
| Abrir detalle | 417.232 / 637.486 | 170.883 / 457.380 | -246.349 / -180.106 | 100.000 / 159.372 | No |
| Entrar a carga | 304.347 / 455.574 | 274.325 / 338.155 | -30.023 / -117.419 | 100.000 / 150.000 | No |
| Mostrar catálogo | 86.135 / 96.120 | 92.105 / 100.600 | 5.970 / 4.480 | 100.000 / 150.000 | No |
| Volver a selección | 117.794 / 141.009 | 118.379 / 134.996 | 0.586 / -6.013 | 100.000 / 150.000 | No |
| Previsualizar tres jornadas | 446.845 / 763.586 | 362.446 / 507.396 | -84.399 / -256.191 | 100.000 / 190.897 | No |
| Guardar tres jornadas | 665.569 / 820.916 | 537.267 / 931.182 | -128.302 / 110.266 | 133.114 / 205.229 | No |
| Abrir Resumen | 1238.454 / 1720.547 | 1608.104 / 1852.877 | 369.650 / 132.329 | 247.691 / 430.137 | Sí, p50 |

## Lectura causal y límites

**VERIFICADO:** Resumen alerta sólo en B2/A2; guardado no alerta. Entre las dos
corridas de R4, la mediana de Resumen sube de 1401.641 a 1608.104 ms (+14.73 %).
La base pasa de 1292.765 a 1238.454 ms (-4.20 %). En guardado la base también
varía: 548.335 → 665.569 ms (+21.38 %), mientras R4 pasa de 530.990 a 537.267 ms.

**INFERIDO:** la variabilidad de corrida sigue siendo relevante; este resultado
no sostiene una diferencia estable en los dos órdenes. No identifica por sí solo
caché, arnés, SQL, observadores, composición o temperatura como causa.

**PENDIENTE:** atribución causal diferencial y aceptación de la espera real.
Resumen sigue teniendo medianas de aproximadamente 1.24 a 1.61 segundos en
esta fixture; estar bajo un umbral relativo no demuestra experiencia aceptable.
No hubo intervención de producto que permita afirmar «corregido».

Se conserva la conclusión q3 sobre trabajo actual posterior a IDLE, con sus
límites de muestreo/reloj y falta de baseline trazado. No se trasladaron los
tiempos de trazas a latencia E2E ni se sumaron spans de distintos hilos.
No se abre otro experimento por cuenta propia. MAIN debe auditar y resolver qué
parte del bloque cierra y cómo registra el pendiente causal.

## QA devuelta y evidencia

Se reinstaló con `install -r` la QA actual C y se verificó su SHA:
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
Queda instalado el runner E original:
`bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
QA quedó detenida e Inicio al frente. Los once hashes de datos conocidos,
incluido WAL, coinciden exactamente con A2 tras la reinstalación. No se restauró
la fixture Stage 10. Hubo cinco instalaciones conservadoras exitosas en total;
cero desinstalaciones, limpiezas externas o consultas a la aplicación pública.

Raíz de recibos: `.local-evidence/stage8/closure-20261022/`.

| Corrida | Recibo relativo | SHA-256 del JSON de muestras |
|---|---|---|
| A1 | `operations/A1-retry/receipt.json` | `11352b0cf1281273a78b2ec5d92efbf10cfeb92b60d236ee9464ba481c6659c7` |
| B1 | `operations/B1/receipt.json` | `b87e93ba36d9c3a113d474c4ddc78cb9f9df1c5701a57844d2d8d59a7d4897fe` |
| B2 | `operations/B2/receipt.json` | `bec04c701eee1c28a97946e6ff1360bff2dd2fe4430e2c0d20bcb8dc4e675708` |
| A2 | `operations/A2/receipt.json` | `ec8b461d7682ce1f2bbb2a0545c8ff085c01c48e8fa289a22e585c1799144ade` |

- `comparison.json`: matriz completa, muestras, máximos, condiciones y doble cálculo.
- `operations/restore/receipt.json` y `operations/verify-restored-synthetic/receipt.json`:
  APK final, foco, reemplazo conservador y preservación por hashes.
- `entry.json`, `selection.json`, `local-checks/offline-tests-r3.txt`: entrada,
  artefactos y 25 tests offline. Los rechazos originales siguen disponibles.
- `repository-exit.json`, `own.patch`, `writer-release.json`: delta, conservación
  de q2/q3/Stage 10, resultados de comprobación y cese al entregar.

## Verificación y próximo dueño

El cierre documental aplica `python -B tools/docs_check.py`, comprobación del
catálogo, `git diff --check` y revisión del delta respecto de la entrada, incluyendo
los documentos no rastreados. Los resultados efectivos quedan en
`local-checks/final-checks.json` y `repository-exit.json`; este texto no reemplaza
esos recibos. No se repitieron Gradle/JVM/build, Stage 9, q3 o QA ajena al contrato.

MAIN recibe el handoff en el mismo chat y conserva STATUS/índices/contrato y
aceptación. Debe verificar los cuatro protocolos y hashes, recalcular p50/p95,
conservar la alerta del segundo par y los rechazos de preparación, comprobar
la QA devuelta y distinguir esta campaña completada de una causa resuelta.
Sin cambios de producto, commit, push, distribución, nueva tarea o supervisor.
