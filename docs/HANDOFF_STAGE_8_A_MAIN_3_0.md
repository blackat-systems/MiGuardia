# Handoff Stage 8 a MAIN — Resumen y guardado

## Actualización de cierre — 22/10/2026

**Comparación A1 → B1 → B2 → A2 completada. Resumen alerta sólo en el segundo
par; guardado queda dentro de umbral en ambos. La causa diferencial sigue
INCONCLUSA y no se cambió producto.**

Contrato actual: [comparación acotada](prompts/STAGE_8_CIERRE_COMPARACION_RENDIMIENTO_3_0.md).
Auditoría y nueve operaciones: [resultado del 22/10](audits/2026-10-22-stage8-comparacion-cierre.md).
Clave: `stage8-cierre-comparacion-20261022`. Mismo checkout, rama
`codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.

| Operación / par | Mediana base → R4 | Máximo base → R4 | Umbral superado |
|---|---:|---:|---|
| Guardado A1/B1 | 548.335 → 530.990 ms | 630.430 → 753.512 ms | No |
| Guardado A2/B2 | 665.569 → 537.267 ms | 820.916 → 931.182 ms | No |
| Resumen A1/B1 | 1292.765 → 1401.641 ms | 1668.424 → 1644.527 ms | No |
| Resumen A2/B2 | 1238.454 → 1608.104 ms | 1720.547 → 1852.877 ms | Sí, mediana +29.85 % |

**Comprobado:** cuatro métodos Android aprobados, 12 × 9 por corrida, 432
muestras sin descartes, mismo fixture/hash lógico, mes y configuración observada.
Detector original y runner idénticos, sin tracing. Los percentiles y umbrales se
recalcularon por dos vías. Se preservan 25/25 tests offline y dos rechazos previos
sin instalación/ensayo: equipo desconectado y guarda de vigilia demasiado estricta,
corregida con regresión antes de medir. La primera corrida real está en `A1-retry`.

La autorización humana posterior de MAIN y la procedencia sintética se verificaron;
la guarda comparó diez archivos conocidos y WAL vacío antes de regenerar.
Se devolvió la QA actual C por `install -r`, hash
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
Quedó detenida, Inicio al frente, runner E
`bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`
y los once hashes de datos iguales a A2. La fixture instalada Stage 10 fue
reemplazada con autorización; su evidencia local sigue preservada.

**Límite:** no hay alerta consistente en ambos pares, causa exclusiva ni arreglo
demostrado. No se activó el brazo diagnóstico, condicionado a persistencia en ambos
órdenes. El conteo final 1956 se deriva de las aserciones por iteración, sin una
nueva extracción completa de base. No es aceptación de fluidez, integridad,
upgrade, visual o distribución.

**Entrega:** `.local-evidence/stage8/closure-20261022/` contiene comparación,
protocolos, muestras, verificaciones y sello. Sólo se agregó esta sección y la
auditoría nueva; los apartados anteriores conservan sus resultados históricos.
El cese de escrituras y recursos queda en `writer-release.json` al finalizar.
MAIN recibe por el seguimiento existente, audita y decide el estado; no se abre
otro experimento ni se altera STATUS desde este chat.

---

## Entrega histórica del 21/10 — conservada


Fecha: 2026-10-21. Contrato:
[Stage 8](prompts/STAGE_8_RENDIMIENTO_RESUMEN_Y_GUARDADO_3_0.md).
Estado autorizado sólo en [STATUS](STATUS.md).

## Resultado

La continuación **local q3** reconstruyó las 48 trazas q2 y localizó trabajo de
observadores, composición y Calendario posterior a IDLE. La causa diferencial
histórica de R4 cierra **INCONCLUSA**: faltan trazas equivalentes de baseline y
control del orden de ejecución. No se cambió producto ni se repitió QA física.

La sección q3 al final contiene el relevo vigente de este bloque. q1 y q2
conservan sus recibos: dos métodos Android verdes pertenecen a q2, sin ejecución
Android nueva en q3. Esta evidencia no certifica rendimiento E2E ni acepta R4.

La [auditoría](audits/2026-10-21-stage8-rendimiento-resumen-y-guardado.md)
identifica fronteras, evidencia, límites y un único experimento siguiente propuesto.
Los recibos históricos mantienen todas sus muestras. El máximo alertado de
guardado corresponde a iteración 1 en ambos productos; no se elimina.
Resumen ya cancela por visibilidad/lifecycle; ese comportamiento se conserva.

## Archivos y atribución

- `app/src/androidTest/java/com/blackatsystems/miguardia/Stage8BoundaryDiagnosticsTest.kt`:
  dos métodos adicionales representative/stress, fixture original 240/24/365 y
  1920/192/2920, 12×9, crecimiento de tres jornadas. Checkpoints, muestreo ART,
  conservación de corridas parciales y guardas de foco/trazado. Comprueba base
  inalterada al consultar Resumen, conteo total y selección guardada.
- `tools/qa-groups.json`: grupo `stage8-boundaries`, clasificación y hash del
  harness. Requiere QA descartable y autorización específica para ejecutar.
- Esta entrega y la auditoría fechada: evidencia y plan, sin cambiar STATUS,
  contrato ni índice reservados a MAIN.

Detector E2E original intacto, SHA-256
`3331ede6d038657939629a5a9a9bb86f33d246b2314cb8a144f160d5b7f00ad7`.
No cambian código productivo, reglas, Room/esquemas, CAS/transacciones, Copias,
dependencias, permisos, versión, firmas ni publicación.

Se corrigió únicamente la sintaxis del `sdk.dir` privado/ignorado; conserva el
mismo SDK. Originales y adversos en `environment/`. El cache recuperó un hallazgo
de lint anterior después de un análisis fresco verde. Para las comprobaciones
independientes de esta entrega, preparar el entorno habitual y mantener también
en esa sesión `JAVA_TOOL_OPTIONS` con `-Dorg.gradle.caching=false
-Dorg.gradle.configuration-cache=false`. La auditoría registra la reproducción
y la reconstrucción que evita ese resultado; no se borraron cachés ni se cambiaron
dependencias o `gradle.properties`.

## Rectificación R1 — atribución del segundo fallo de lint

MAIN detectó una atribución no respaldada por los originales. Se retira la
afirmación anterior de que se preservó un estado de dos barras en `sdk.dir`.
El original `local.properties.before` tiene 48 bytes y cero barras ASCII 92;
`after`, `double-escape-adverse` y `final` tienen 49 bytes, una barra y el mismo
SHA-256 `6011a2683478fe0fc208ca99ef99486607259b10223e50a8c90d5ff01467b8c0`.
`syntax-fix-final.json` además declara hashes de entrada/salida idénticos:
su causa está mal atribuida y el nombre `double-escape-adverse` mal rotulado.

Todos esos archivos, recibos y sellos se conservan intactos. La rectificación
canónica está en `.local-evidence/stage8/environment/attribution-correction-r1.json`;
originales de los dos documentos y recibos se preservan adicionalmente en
`corrections/20261021-env-attribution-r1/originals/`. El segundo intento usó
análisis/reporte lint `UP-TO-DATE` y un XML idéntico al del primer fallo; esto
demuestra reutilización de ese reporte, no una entrada de dos barras.

Esta corrección sólo cambia los dos documentos y agrega evidencia/sello nuevos.
No modifica producto, herramientas ni `local.properties`, no repite Gradle/JVM
y no toca `main-review/`. Se conservan los resultados locales anteriores con
sus mismos insumos; no se atribuyen ejecuciones nuevas.

## Evidencia local

Raíz `.local-evidence/stage8/`:

- `entry/repository.json` y `entry/files/`: 978 rutas y originales. Sólo tres
  diferencias de despacho contra sello de estructura: STATUS/índice/contrato.
- `diagnosis/historical-stress-analysis.json`: análisis nuevo de cuatro
  recibos históricos, SHA-256 de insumos, muestras y nearest-rank.
- `review/parity-and-boundaries.json`: detector original intacto; cuerpos de
  fixture/auxiliares y orden de nueve operaciones iguales; fronteras protegidas
  sin cambios. No acredita ejecución Android del harness.

- `jvm/result.json`: **935/935 JVM frescas** (395 dominio + 19 base + 521 app),
  cero fallos/errores/omitidas/reutilizadas, `changed_inputs: []`.
- `validation/build-fresh/result.json`: **build completo verde**, 335 tareas
  ejecutadas; lint sin errores y 24 advertencias, Debug/QA/Release unsigned y
  ambos runners generados. Insumos iguales a JVM final, sin cambios durante
  las ejecuciones. Los intentos adversos se conservan y explican en la auditoría.
- `artifacts/manifest.json`: seis artefactos copiados y rehasheados. QA
  `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`;
  runner QA `7eb513f3bece83fa330dac34e8a884c0223d90f18575764574a32f6fea1a8acc`.
  Son builds locales nuevos, nunca instalados.
- `validation/tools-tests.txt`: 75/75 controles de herramientas. Inspector:
  38/38, repetidos por el escritor principal, con recibo
  `diagnosis/inspector-tests-root.txt`. Sólo estructuras sintéticas de parser;
  enlaces nativos no disponibles, ramas de rechazo simuladas declaradas.
- `review/final-checks.json`: docs, catálogo (122 clases/29 grupos), whitespace y
  fronteras. `review/stage8-own.patch` y `delta-summary.json`: cuatro archivos
  propios contra entrada, sin absorber el diff previo. `exit/repository.json`:
  sello y originales de la primera entrega, conservados. Relevo documental R1:
  `exit-r1-attribution/repository.json`, `delta-summary.json`, `validation.json`
  y `writer-release.json`; sustituye el relevo anterior para continuar la revisión.

El inspector de trazas es una herramienta local ignorada, sin dependencia ni
hook productivo. Rechaza datos incompletos/inconsistentes; no interpreta el
binario ART ni declara fluidez. No se ejecutó AndroidTest: ambas aserciones y
trazas nuevas quedan pendientes de observación física autorizada.

## Riesgos y siguiente paso

MAIN debe revisar el delta propio y los recibos locales. El diagnóstico compilado
no acredita una sola traza nueva ni ejecución física de sus aserciones. Los
checkpoints incluyen trabajo asíncrono; el sampler da pilas estadísticas, con
overhead y posibles pérdidas. No sumar fronteras como costos exclusivos ni
comparar sus `diagnostic_samples_ms` con el detector original.

El plan físico exacto está en la auditoría: autorización Stage 8, QA descartable,
hash instalado, guardas de foco/privacidad, grupo nuevo, extracción binaria sólo
de los artefactos QA, validación de cabeceras/overflow y análisis de pilas.
Una causa demostrada permitirá una corrección mínima y regresiones; después
habrá que repetir el detector original pareado sin modificar muestras/umbrales.

El cierre local anterior fue sin dispositivos, emuladores, instalación, servicios
remotos, commit, push, tag, firma de distribución ni publicación. La reanudación
posterior y su HOLD se registran al final. Stage 7 conserva sus puertas de aceptación
y QA; actualización 2.0.2/Copias y distribución siguen separadas.

## Estado Git y relevo

Rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, un worktree, staging vacío.
El checkout acumulado sucio se preserva como entrada; no se absorbe como cambio
propio. **Cesaron nuevamente las escrituras al entregar el sello R1.** Stage 8 no
ejecutó Gradle/JVM en la rectificación. MAIN puede
revisar en lectura y ejecutar sus comprobaciones independientes. Sin commit ni
integración por Stage 8; STATUS, contrato e índice permanecen a cargo de MAIN.

## Reanudación Samsung 21/10 — checkpoint por HOLD de disponibilidad

MAIN comunicó un HOLD posterior al despacho: no instalar, ensayar ni consultar
nuevamente el teléfono hasta aviso vigente. El permiso de alcance QA se conserva,
pero la disponibilidad anterior no habilita el uso actual. No había operación
ADB de este chat en curso al recibirlo. Se detuvo el avance antes de instalar o
ejecutar selectores; no se intentó limpiar, desinstalar ni cambiar ajustes.

**VERIFICADO en host:** entrada de 993 rutas, staging vacío y 590 insumos
idénticos al build seleccionado Stage 9; QA `2e00ab…758a3` y runner actual
`40a24a…15472`, rehasheados. `aapt2` local confirmó paquetes QA/QA.test y
`AndroidJUnitRunner` dirigido exclusivamente a QA. La selección actual no
sobrescribe la identidad histórica del runner `7eb513…8acc` de la entrega local.
El plan `stage8-boundaries` conserva representative/stress y 12×9 operaciones;
no se agregó `--execute`. No se repitió Gradle, build ni pruebas JVM.

**Única observación física de esta reanudación:** probe de metadatos a las
05:26:27 UTC, previo a recibir aquí el mensaje de HOLD. Identificó Samsung
SM-S938B/API 36, foco Inicio y metadatos de desbloqueo en ese instante. Los
hashes instalados seguían siendo QA R4 `99e32e…dc29` y runner `bc31f0…3c3c`.
MAIN informó una restricción de las 05:26 UTC: el probe cae en esa misma
minuta y se conserva esa coincidencia temporal; no acredita disponibilidad
actual ni resuelve por sí solo la autorización temporal. No se volvió a consultar
el teléfono después de recibir el HOLD. No hubo entradas UI, capturas ni consulta
de la app pública o de contenido laboral.

**NO EJECUTADO:** instalación QA/runner, instrumentación, reemplazo de datos
ficticios, extracción binaria o inspector sobre trazas físicas nuevas. No hay
JSON de diagnóstico ni trazas ART nuevos y no se demostró la causa de la alerta.
El último foco/estado registrado pertenece al probe; no se declara un estado
actual comprobado durante el HOLD ni se abre QA para fabricar un cierre.

Evidencia nueva preservada en `.local-evidence/stage8/qa/q1/`: `entry.json`,
`selection.json`, `plan.json`, `apk-metadata.json`, probe completo,
`hold-checkpoint.json`, originales de ambos documentos y recibos de cierre.
La revisión interna de sólo lectura del helper `ops.py` encontró un límite de
recibos: el error ADB no cero se lanza antes de conservar `Failure[...]`, y un
timeout no registra el intento. Corregir y comprobar offline antes de una futura
operación; no se ejecuta/reintenta para demostrarlo. Las guardas `assert` requieren
Python sin optimización, y la colección debe identificar explícitamente los runs
de la campaña entre todos los directorios Stage 8. El helper no acredita pruebas
físicas ni sustituye el permiso de disponibilidad.

**PENDIENTE / NEXT para MAIN:** levantar explícitamente el HOLD con confirmación
humana vigente; revalidar identidad/foco y hashes en ese momento; subsanar el
recibo local antes de la instalación; ejecutar el contrato completo una sola vez
si siguen dadas sus condiciones. El visor/decoder ART compatible todavía debe
seleccionarse: el inspector estructural no interpreta sus registros binarios.
No proponer una optimización ni cerrar rendimiento con esta preparación.
STATUS, workflow, contrato e índices quedan a cargo de MAIN. Se entrega relevo
estable de las rutas propias, sin cambios productivos, Git staging/commit ni
operación nueva del dispositivo; detalle y sello en `q1/writer-release.json`.


## Reanudación q2 — diagnóstico físico observado

Tras el relevo documental de MAIN y la nueva disponibilidad humana de las
05:30:02 UTC se corrigió el helper **offline**, conservando q1 y su sello.
13 controles con dobles pasaron y root los repitió; guardas Python sin
optimización, intentos previos al subprocess, códigos de rechazo, timeouts
inciertos y transporte parcial quedaron cubiertos. No hay reintentos automáticos.

**VERIFICADO:** instalación conservadora `-r` de QA `2e00ab…758a3` y runner
`40a24a…15472`, paquetes/manifiestos y hashes instalados coincidentes. Se ejecutó
una vez el grupo oficial `stage8-boundaries`: **2/2 métodos PASSED**, cero
fallos/omitidos/pendientes; representative y stress en procesos separados,
12×9 operaciones y crecimiento de tres jornadas por iteración. Recibo original
`.local-evidence/qa/20261021T054042.236535Z/`, copiado a `q2/runner-receipt/`.
Esto no ejecutó Stage 9 ni probó upgrade de la app pública.

Se extrajeron binariamente **50 archivos: dos JSON y 48 trazas**, SHA-256
remoto/host coincidente y revalidado tras el análisis. Ambos inspectores
estructurales pasaron; control binario acotado v3 dual: **1.153.890 registros**,
sin acciones reservadas, símbolos desconocidos ni inconsistencia de contadores.
Root contrastó por separado hashes, cabeceras, alineación y cantidad de todos.

**Hallazgo acotado:** el máximo inicial de guardado incluye sincronización
posterior a observar IDLE sin error. Representative: total diagnóstico
1617,227 ms, espera IDLE 336,757 ms y final idle/foco 1277,600 ms; stress:
1615,331 / 662,563 / 949,951 ms. No son transacciones SQL de 1,6 segundos.
Las pilas muestreadas y el código confirman auditoría global repetida en lecturas
y escrituras. No prueban el costo exclusivo ni la causa del delta R4.

Perfetto UI v58.3 procesó copias localmente: cinco trazas observadas. Las dos de
guardado stress tienen pérdida por profundidad del importador (16/9); las tres
de Resumen observadas no reportaron errores. Todos los slices siguen siendo
muestreados/inclusivos, con algunos finales abiertos; no se suman entre hilos.
Los resultados mal atribuidos o leídos antes de terminar SQL se preservaron y
excluyeron; representative válido es sólo `viewer-representative-0-summary-r3-*`.
No se cambió una traza, fixture, muestra, umbral ni producto para aprobar.

Evidencia canónica: `.local-evidence/stage8/qa/q2/physical-analysis.json`,
inspecciones, `art-validation.json`, controles root, tablas/capturas del visor,
recibos de instalación/colección y `writer-release.json`. La auditoría enlazada
arriba conserva métricas, fuentes, adversos y próximos límites con más detalle.

**PENDIENTE / NEXT:** MAIN revisa este bloque. La siguiente observación útil es
analizar callers/propagación sobre estas copias y delimitar la pérdida de
profundidad antes de definir una corrección mínima. No repetir QA física por
rutina. Una corrección posterior necesita preservar validación histórica,
atomicidad/CAS y ser contrastada con detector E2E original pareado; no se ejecutó
ese par en q2 ni se cerró la alerta R4.

**Relevo:** sólo cambiaron este handoff y la auditoría propia contra entrada q2;
STATUS/contrato/workflow/índices siguen reservados a MAIN. No hubo build/JVM,
commit, push, firma ni distribución. Los datos sustituidos fueron exclusivamente
ficticios QA (stress terminó con 1956 jornadas esperadas); runner terminó y
forzó cierre QA. La última consulta del recolector observó Inicio desbloqueado;
no hubo más ADB durante el análisis. Ambos visores temporales se cerraron.
Cesaron escrituras, Gradle y uso del dispositivo al sellar `q2/writer-release.json`.

## Continuación q3 — análisis local entregado a MAIN

**OBJECTIVE / RESULT:** analizar las copias existentes para localizar la causa de
R4, con prohibición humana vigente de QA física y cambios de producto. Se cerró
la unidad de reconstrucción, relojes, fases y callers. Hay mecanismos actuales
localizados; el **delta histórico R4 sigue INCONCLUSO**. No se prescribe todavía
una optimización ni se convierte la sincronización final en mero costo del driver.

**EVIDENCIA:** reconstrucción sin límite artificial de profundidad: 48/48 trazas,
1.153.890 registros, 585.526 segmentos de pila, 17.162 finales naturalmente
abiertos, profundidad máxima 528 y cero inconsistencias. Los intervalos por hilo
se particionaron usando la pila activa antes de cada acción, sin sumar tiempos
inclusivos. 1.753 controles CPU/wall y 2.080 grupos fase/hilo coinciden con una
lectura binaria independiente de root.

La asignación temporal es **condicional** a un modelo afín entre relojes y a los
brackets de las APIs. No se certificó la implementación Samsung; la mayor ventana
de incertidumbre observada es 60,950 ms. Bordes no resolubles quedan AMBIGUOUS.
Los pesos son observaciones muestreadas, con overhead, no costos exactos de métodos.

En stress, guardado inicial, el tramo final posterior a IDLE dura 949,951 ms.
Bajo ese modelo se asignan 822,666 ms de CPU muestreada a `main`: Compose 394,646,
celdas del Calendario 205,475 y transformación `HoursAndExtras.sourceFlow` 60,065,
entre otros contextos. En paralelo se asignan 555,078 ms de CPU de fondo a
integridad, bajo callers de observación. **No sumar hilos como tiempo E2E.**
`captureDeletionExpectations` queda en preparación/espera previa a IDLE; no es el
caller de esas auditorías posteriores. Horas y Extras permanece observado y
alimenta indicadores/detalle del Calendario; no corresponde apagarlo por pantalla
oculta sin preservar esos consumidores.

**LÍMITE DIFERENCIAL:** cuatro recibos originales establecen orden **ABAB**, no
AABB ni contrabalanceo BA; Resumen empeora también entre las dos corridas baseline.
Ocho archivos de lógica examinados coinciden con snapshot Stage 5 y R4; cuatro
archivos de UI/wiring difieren. Esa comparación de fuentes no certifica por sí
sola su vinculación al APK baseline. No hay baseline ART para atribuir un costo
heredado a R4. La auditoría conserva números, rutas, hashes y adversos corregidos.

**FILES / VALIDATION:** sólo este handoff y la auditoría propia cambian contra
entrada q3; herramientas y resultados nuevos ignorados en
`.local-evidence/stage8/qa/q3/`. Canónicos: `causal-analysis-r1.json`,
`phase-analysis-r2.json`, `stack-build.json`, `clock-alignment-r1.json` y controles
root. Pasaron 18 fixtures de pila, 18 de partición y 14 guardas del modelo de reloj;
root repitió las dos baterías y comprobó los datos reales. Son pruebas locales,
sin nueva ejecución Android/JVM/build. q2 se conserva íntegro.

MAIN detectó un defecto matemático de la cota previa de cuantización: +999 ns no
cubría relojes escalados fraccionarios. Se corrigió a +1000 ns, con dos regresiones
exactas y regeneración de derivados; no cambian los pesos ni las atribuciones de
estas 48 trazas. La envolvente máxima aumenta 1 ns. Originales y adversos preservados.

**PENDING / OWNER / NEXT:** MAIN audita y registra en STATUS. Se propone un solo
experimento futuro: baseline/R4 contrabalanceados ABBA, mismo fixture stress y
condiciones, detector original sin tracing y brazo diagnóstico separado con los
mismos checkpoints. La auditoría define hashes, hipótesis, control y resultado que
la favorecería/refutaría. **No ejecutado ni autorizado por este cierre**: requiere
nueva decisión humana de QA/dispositivo/datos ficticios y preflight de compatibilidad.
Una asociación favorable todavía no demostraría causalidad exclusiva sin una
intervención posterior acotada.

**RELEVO:** HEAD/rama y checkout previo preservados; staging vacío. Sin ADB,
Gradle/JVM, instalaciones, modificaciones productivas, commit, push o distribución
durante q3. STATUS/contrato/workflow/índices reservados a MAIN. Delta, comprobaciones
y cese de escrituras quedan sellados en `q3/writer-release.json`; no se consulta el
teléfono para fabricar un estado actual de cierre.
