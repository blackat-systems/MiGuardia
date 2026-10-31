# Stage 7 QA física de MAIN

Fecha local de inicio: 2026-10-11. Ensayo en curso, sin aceptación humana ni
autorización de distribución. Joaquín autorizó instalar la candidata y ejecutar
QA física sintética en Samsung conectado, libre y desbloqueado, exclusivamente
MiGuardia QA, sin app pública ni datos personales.

## Continuación del 13/10 — capturas finales completadas

Joaquín confirmó disponibilidad y autorizó recuperar la revisión de salida.
Puerta 0: 976 rutas idénticas a `android-font-20261011/exit-r4-20261012/`,
rama/HEAD originales, un worktree, staging vacío, 156 entradas rastreadas
modificadas y 89 nuevas preservadas; `.git` propio y contenedor fuera de Git.
No se encontró delta inexplicable. No se reinstaló ni cambió producto.

La guarda identificó Inicio; se abrió sólo QA. Se llevó el menú hasta la entrada
Calendario totalmente visible, se capturó el mes, se abrió por toque un día vacío
y se capturó su hoja. Se volvió con Atrás al mismo mes. Sin guardar jornadas,
abrir selectores, activar servicios o cambiar ajustes globales. Datos ficticios
`Institución QA sintética`/QAS recibidos; sin datos personales.

Originales bajo `android-font-20261011/originals/`, sin retoques, ambos revisados
visualmente por MAIN en oscuro:

| PNG | SHA-256 |
|---|---|
| `installed-r4-calendar-dark-20261013.png` | `e84d0014f625a8a4dee3a81f7f4387ca94001674a94355f3f42bcc147b669190` |
| `installed-r4-sheet-empty-dark-20261013.png` | `eec46791047a01a5e7ccf1df2c76cc84d5c49cb07b61781e4462d6a8ce706da0` |

Se ve octubre completo, sin barra inferior normal; la hoja del 15/10 llega hasta
la navegación del sistema sin franja del calendario debajo. Su respiro interior
y la navegación Android permanecen. Son dos estados reales en retrato, no una
matriz nueva ni aceptación estética global. Los metadatos originales mantienen
`reviewed: false`; la revisión posterior se registra separada, no los sobrescribe.

Recibo `android-font-20261011/captures/exit-20261013.json`: dos hashes comprobados,
QA al frente en Calendario sin hoja, instalada `99e32ed…dc29`, runner
`bc31f05…c3d3c` y pública `be3bca…aa2f94`, coincidentes con R4 y su baseline público.
La pública no se abrió/instrumentó. El primer intento del helper de cierre falló
por un nombre de constante local inexistente, antes de escribir el recibo; se
corrigió sólo el helper ignorado, preservando los valores exigidos. No fue un
fallo de producto ni de una prueba Android.

Un revisor independiente comprobó los seis artefactos del build y sus hashes/
tamaños, los 585 inputs estables de build/local y los 103 reportes frescos de
935/935 JVM. Con fuentes idénticas no se repitieron Gradle, JVM, instrumentación
o rendimiento por tomar capturas. Los 56 métodos R4 y la matriz 48/48 conservan
su fecha/atribución; las alertas stress de Resumen/guardado siguen PENDIENTES.

Sólo se actualizan STATUS, este registro y el handoff; evidencia/helpers nuevos
ignorados. Atribución R4 acumulada: `review/captures-20261013/`, y salida:
`exit-captures-20261013/repository.json`, bajo la evidencia Android. Fuentes
ejecutables y límites sensibles sin cambios. Documentación/catálogo/diff se
comprueban al cierre. Sin commit, push, firma o distribución.

## Ampliación del 12/10 — paisaje, hoja inferior e incongruencias

Joaquín autorizó la franja lateral para ventanas anchas/bajas y pidió revisar
incongruencias UI y el espacio bajo la hoja ascendente. R4 corrige esos casos
y tiene ejecución física nueva; no implica aceptación estética integral.

### Revisión R4 — candidata vigente

La franja lateral se aplica sólo con ancho útil de al menos 600 dp, alto menor
a 600 dp y orientación ancha. Reubica menú, navegación mensual, avisos y acciones
contextuales sin duplicar grilla ni callbacks. Conserva mes completo, selección,
borradores y dueños de Atrás. Al reintentar con días retenidos, el progreso vive
en el rail y no vuelve a restar altura a las casillas. Los errores resueltos no
dejan un diálogo vacío. Los secundarios que no caben se omiten enteros; señales,
descripción accesible y detalle completo permanecen. El presupuesto de texto
usa el mismo redondeo por lado que el padding real, evitando una diferencia de
1 px a densidad 3,75.

La hoja del día elimina sólo el padding exterior redundante: Material conserva
el inset del sistema y el contenido conserva su respiro interior. La regresión
mide las coordenadas reales de la superficie desplazada, no el coordinador
anterior al anclaje; mantiene borde inferior ±1 px, contención y como máximo
un inset entre cuerpo y borde. Cubre dos temas, tres tamaños de letra Android y
día vacío/con jornada: 12 escenarios dentro de un método.

Informes reemplaza el umbral fijo por medición del título y del botón con sus
estilos/padding reales. Usa fila sólo si ambos caben; en otro caso, columna.
No achica letras ni cambia contenido o callbacks. Su prueba distingue límites
visuales de objetivos táctiles, que deben ser de al menos 48 dp y no solaparse.

**Validación local R4:** 935/935 JVM frescas (395 dominio, 19 base, 521 app),
cero fallos/errores/omitidas; build completo verde: lint, Debug/QA/Release
unsigned y AndroidTest app/base. Ambos recibos registran inputs estables:
`android-font-20261011/validation/local-calendar-r4-20261012/result.json` y
`android-font-20261011/validation/build-calendar-r4-20261012/result.json`.
Seis artefactos sellados; catálogo válido: 121 clases/28 grupos. Compilar Release
unsigned no autoriza firma ni distribución.

**Identidad R4:** instalación conservadora de QA/runner, hashes locales e
instalados coincidentes. Manifiesto `artifacts-final-r4/manifest.json` y recibo
`install-20261012T210101.303639Z.json`, bajo `android-font-20261011/`:

- QA: `99e32ed7525b23ac5452d8eebab019f7051c2f7e6e50dc47db9a2e918dd4dc29`.
- Runner: `bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
- Pública: `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`,
  intacta, sin abrirla/instrumentarla ni leer sus datos.

**QA física R4: 56 métodos dirigidos verdes**, sin fallos, omitidas ni pendientes.
Las combinaciones internas no se suman como métodos adicionales:

| Clase/grupo | Métodos | Recibo bajo `.local-evidence/qa/` |
|---|---:|---|
| Calendar | 8/8 | `20261012T210113.717211Z` |
| Reports, 18 casos nativos internos | 5/5 | `20261012T210139.840426Z` |
| Common | 14/14 | `20261012T210203.716600Z` |
| Adaptive | 7/7 | `20261012T210239.395199Z` |
| Matriz mensual, 48/48 combinaciones internas | 2/2 | `20261012T210306.692804Z` |
| Carga Activity | 8/8 | `20261012T210458.338771Z` |
| Familias dirigidas, un método por caso siguiente | 12/12 | índice de campaña |

Las 12 familias cubren avisos, créditos y jornada nocturna de Clima, edición de
jornada, horario real, preview/revisión de repetición, puerta de autenticación,
paleta/créditos Widget, Actualizaciones y menú. Índices con recibo individual:
`campaigns/20261012T210113.169703Z-r4/index.json` y
`campaigns/20261012T210629.432525Z-r4/index.json`, bajo la evidencia Android.
Carga Compose 35/35 y Ayuda 12/12 conservan atribución R3, no se repitieron ni
reasignaron por rutina. TalkBack conserva su recibo anterior y confirmación
humana; no se presenta como una nueva escucha del rail.

**Revisión visual:** se extrajeron originales nuevos y se comprobaron sus hashes
en `diagnostics/20261012T210616.219166Z-reports-r4/` (18 PNG y JSON) y
`diagnostics/20261012T210625.879748Z-calendar-r4/` (retrato/paisaje). MAIN vio
Informes 360/384 dp a letra 200 % y el mes de seis filas en paisaje a 200 %;
un revisor independiente vio los 18 Informes, sin títulos partidos, recortes ni
solapamientos nuevos. Los cuatro casos adversos quedaron corregidos. El JSON
registra cero infracciones, objetivos de 48 dp y un callback por caso. La
reconstrucción semántica conocida sigue identificada, no se oculta como verde
visual. Esta muestra no equivale a aceptación humana ni cobertura universal.

El delta de esta reanudación comprende 18 archivos: cinco fuentes productivas,
ocho pruebas Android, una JVM, catálogo y tres documentos. Los cambios anteriores
acumulados permanecen preservados; no se alteran dominio, Room, Copias/cifrado,
CAS, autenticación, Gradle, manifiestos, permisos, versiones o serialización
legada. Los helpers, capturas y recibos nuevos son evidencia local ignorada.

Persisten revisión humana, selector OEM/teclado/permisos/Widget OEM y
ventanas distintas a la muestra. Seis filas con objetivos de 48 dp requieren
al menos 308 dp de grilla más encabezado semanal: no se certifican ventanas
arbitrariamente bajas. No se infieren FPS, arranque frío listo ni fluidez global.

### Rendimiento R4 — comparación caliente y alerta conservada

Driver y runner idénticos entre APK Stage 5 preservada y candidata R4; nueve
operaciones, 12 iteraciones por fase, mismos datos/hash lógico inicial, ventana
y condiciones observadas. Representative: 240 jornadas/24 notas/365 días;
stress: 1920 jornadas/192 notas/2920 días. Sin cambiar ajustes globales Android.
La configuración observada fue 1440 × 3120 px, densidad 600 dpi y letra Android
0,9; baseline aplica zoom legado 100, candidata hereda Android. Es comparación
de productos completos, no atribución causal aislada ni prueba a fuente 100 %.
Se mantienen muestras y umbrales originales, p50/p95 nearest-rank; con n=12,
p95 es el máximo. El helper pasó antes 29 controles puros sin dispositivo.

- Representative: ambas fases 1/1; ninguna operación supera umbral.
  Índices `performance/baseline-representative-20261012T210848.208711Z.json` y
  `performance/candidate-representative-20261012T211218.774185Z.json`, bajo la
  evidencia Android. Recibos `20261012T210851.380990Z` y
  `20261012T211221.877562Z`, bajo `.local-evidence/qa/`.
- Primera stress: ambas fases 1/1 funcional; comparación con alerta en
  `open_summary`. p95 909,10 → 1148,69 ms: delta 239,59 ms, mayor al umbral
  227,27 ms. p50 873,71 → 940,73 ms: delta 67,02 ms, bajo umbral 174,74 ms.
  Las otras ocho operaciones no disparan alerta. Índices
  `performance/baseline-stress-20261012T211305.648604Z.json` y
  `performance/candidate-stress-20261012T211431.469334Z.json`; recibos
  `20261012T211308.776288Z` y `20261012T211434.553432Z`.

`open_summary` incluye abrir menú, elegir Resumen, idle Compose, esperar la
proyección mensual CONTENT/EMPTY sin error y guarda de foco. No mide sólo el
dibujo de Resumen, FPS ni contenido frío listo. Los máximos pertenecen a la
iteración 12; candidata tarda más que baseline en 11/12 muestras. Esto exige
contraste, no permite descartarlo como ruido ni demuestra una causa productiva.
Se realizó una única repetición pareada completa, conservando este adverso,
datos, driver y umbrales:

- Segunda stress: ambas fases 1/1 funcional. Resumen vuelve a alertar, ahora por
  p50: 1095,83 → 1422,56 ms, delta 326,73 ms sobre umbral 219,17 ms. Su p95
  queda bajo el umbral. También alerta `save_three_shifts` por p95:
  681,75 → 947,45 ms, delta 265,70 ms sobre umbral 170,44 ms; p50 bajo umbral.
  Las otras siete operaciones no alertan. Índices
  `performance/baseline-stress-20261012T211614.871821Z.json` y
  `performance/candidate-stress-20261012T211741.445230Z.json`; recibos
  `20261012T211617.999484Z` y `20261012T211744.742321Z`.

**Rendimiento stress PENDIENTE:** Resumen superó un umbral en ambas parejas;
guardar superó p95 en la segunda. No se elimina ninguna muestra, no se ajustan
umbrales ni se declara fluidez integral. El driver agrega tres jornadas por
iteración y recrea Activity; observa el conjunto creciente. SummaryObserver ya
proyectaba todas las jornadas y su fuente coincide con la entrada de Stage 7.
La medición adaptable de SummaryAmountLine y la nueva política de letra son
hipótesis, no causas demostradas. Próximo diagnóstico mínimo: separar navegación,
espera/proyección y composición en una fixture sintética acotada, preservando
este benchmark como detector extremo a extremo; no optimizar a ciegas dominio
o persistencia. Las correcciones de paisaje/hoja/Informes no se presentan como
solución de estas alertas. No hubo ajustes productivos posteriores.

Restauración explícita de la candidata final R4 PASÓ:
`performance/restore-final-20261012T211934.690068Z.json`. QA/runner/pública
conservaron sus hashes; baseline fue sólo una sustitución temporal de QA, sin
limpieza/desinstalación. El fixture dejó únicamente datos ficticios; no son los
datos de la app pública.

### Estado de salida y atribución

Se accionó `Usar modo oscuro` mediante Apariencia de QA. El helper de navegación
no encontraba Calendario porque el menú conservaba scroll; se volvió arriba y
se accionó la entrada visible, sin cambiar producto. Antes de capturar, la guarda
detectó foco ajeno y detuvo la revisión: no se inspeccionó esa app ni se enviaron
entradas posteriores. Captura final nueva y confirmación de QA al frente quedan
pendientes de disponibilidad; las imágenes R3/R4 ya descritas conservan su
atribución, no se presentan como una captura de salida que no ocurrió.

Revisión propia y frontera: `android-font-20261011/review/exit-r4-20261012/`
incluye patch y manifiesto contra `resume-entry-20261012/`; salida completa en
`exit-r4-20261012/repository.json`, bajo esa evidencia. 976 rutas, 18 cambios
propios y 958 rutas iguales a entrada; sin adiciones/eliminaciones versionables
nuevas, staging vacío y rama/HEAD conservados. Fuentes ejecutables coinciden con
los insumos R4 validados. Documentación, catálogo y diff se verifican al cierre.
No hay APK/AAB/almacenes de firma rastreados. Sin commit, push, tag, firma de
distribución, publicación ni cambios a 2.x.

### Revisión R3 — evidencia nueva y hallazgos adversos conservados

R3 pasó 935/935 JVM frescas y build completo con inputs estables. Recibos bajo
`android-font-20261011/validation/`: `local-calendar-r3-retry-20261012/` y
`build-calendar-r3-20261012/`. El intento local previo falló por un import
`liveRegion` faltante; corregido, sin perder el recibo adverso.
Instalación QA/runner sellada en `artifacts-final-r3/manifest.json`, bajo esa
misma evidencia; hashes locales/instalados iguales, pública intacta.

- Matriz R3: 48/48 combinaciones, retrato y paisaje
  (`.local-evidence/qa/20261012T203459.379975Z`).
- Adaptive: 7/7 (`20261012T203650.609182Z`); carga Compose: 35/35
  (`20261012T204014.813537Z`); carga Activity: 8/8
  (`20261012T204135.891381Z`); Ayuda: 12/12 (`20261012T204235.285685Z`).
- Calendario 7/8 (`20261012T203715.996246Z`): la nueva comprobación de hoja
  comparaba el tag anterior al desplazamiento Material con la ventana. La API
  local confirma coordinadores distintos; se corrige la medición del Surface,
  conservando borde inferior ±1 px y límite de un inset, pendiente de ejecución.
- Common 12/14 (`20261012T203839.774970Z`): dos oráculos exigían etiquetas
  secundarias que ya no cabían. Se conservan señales, estados, descripción
  completa y detalle; una etiqueta opcional presente debe ser íntegra.
- Informes 4/5 (`20261012T204317.990423Z`): las 18 PNG nuevas confirman cuatro
  casos con título fragmentado a letra Android 200 %. El umbral fijo de 360 dp
  elegía fila sin medir texto/botón. Se prepara distribución por ancho real.
  Otras 12 infracciones usaban límites visuales como si fueran táctiles: no
  prueban un defecto táctil; la regresión medirá ambos, sin borrar el adverso.

PNG de Informes: `diagnostics/20261012T204428.332319Z-reports-r3/`; MAIN vio
`360-dark-200.png` y `320-dark-100.png`; revisor independiente vio los 18.
La extracción inicial no encontró nombres por columnas de `ls`; se corrigió
sólo el listado a una entrada por línea, manteniendo foco y sandbox QA.
La captura instalada `originals/sheet-empty-after-r3.png` confirma que ya no
queda calendario debajo de la hoja; el inset del sistema y respiro interior
legítimo no se eliminan. No equivale todavía a la regresión geométrica completa.

Revisión final encontró además `LOADING` con días retenidos al reintentar en
`CalendarViewModel.observeMonth`: el progreso superior restaba altura de nuevo.
Se mueve al rail sólo en esa situación, con regresión de transición y geometría.
Estas fuentes posteriores necesitan nueva compilación/ejecución; ningún verde
R3 se reasigna automáticamente. Rendimiento y aceptación humana siguen aparte.

La matriz nativa R2 (`.local-evidence/qa/20261012T195405.225317Z`) pasó 24/24
combinaciones retrato y 6/24 paisaje. Las otras 18 fallaron por altura real:
casillas de cinco/seis filas menores a 48 dp, objetivos táctiles superpuestos
y última fila fuera del viewport. Texto y fechas pasaron, no es un fallo de
restauración ni motivo para neutralizar la letra Android. PNG/JSON originales:
`android-font-20261011/diagnostics/20261012T195945.258125Z-calendar-r2/`.

Dos revisiones independientes confirmaron el margen exterior adicional de
`ModalBottomSheet`: navegación más 16 dp, además del inset nativo Material.
La captura `originals/sheet-empty-before.png`, bajo la evidencia Android, muestra
la franja del calendario debajo. No confundir con 32 dp interiores de respiro.
También se confirmó `Completa…` truncado: ADR 0043 exige omitir secundario
entero si no cabe. En la adaptación preliminar apareció un diálogo vacío tras
resolver errores; se corrige y regresa antes de compilar. Doble `?` y apariencia
apagada de acciones siguen siendo observaciones estéticas/hipótesis, no fallos
funcionales ni contraste medido.

TalkBack cerró técnicamente 1/1 en
`android-font-20261011/oem/` (`run-20261012T195142.001927Z.json`), ajustes
restaurados. Joaquín confirmó los tres anuncios claros. El intento previo
se detuvo al cerrar Activity; queda preservado y no se atribuye como verde.

R2: Common 14/14, Apariencia 3/3, tema 2/2, preferencias portables 4/4;
carga Activity 8/8, Contextual 8/8, Próximo evento 24/24, configuración laboral
17/17, disponibilidad 8/8, Calendario 7/7, Adaptive 6/6 y Widget 7/7.
Horas 7/7 y Resumen 13/13 usaron la misma candidata productiva y su runner
original. Carga Compose 34/35, Ayuda 11/12 e Informes 4/5 no se ocultan:
los dos primeros tenían oráculos obsoletos; Informes requiere diagnóstico
de geometría/render. Sus correcciones de prueba esperan ejecución nueva.
Recibos por clase en `android-font-20261011/campaigns/` y `.local-evidence/qa/`.
Ningún resultado R2 acredita los cambios productivos de la revisión siguiente.

## Reanudación del 12/10 — antecedente de entrada e instalación

Joaquín revocó la pausa y autorizó continuar sólo en QA. Puerta 0: repositorio
independiente, rama/HEAD originales, un worktree y staging vacío; contenedor
fuera de Git. Las 976 rutas coinciden con `exit-checkpoint/repository.json`.
Entrada preservada en `android-font-20261011/resume-entry-20261012/`, bajo
`.local-evidence/stage7/`. No hubo cambios de producto en esta reanudación.

Build nuevo **PASÓ**: lint, Debug/QA/Release unsigned y runners app/base;
23 tareas ejecutadas y 312 actualizadas, 525,17 s, inputs estables. Recibo
`android-font-20261011/validation/build-resume-20261012/result.json`.
Las 932 JVM de `validation/local-final/result.json` corresponden a las mismas
fuentes; no se repitieron por rutina. El build interrumpido se preserva separado.

Artefactos finales sellados en `android-font-20261011/artifacts-final/manifest.json`:

- QA: `8af48404ec989f5700d45c457b4fe818282f038bc5385c74fcce122c07606f19`.
- Runner: `0f15030da60aa7e9536f243103c64e86bcfa940f0bc59bd65a37eb0b9759a978`.

La revisión independiente no identificó un defecto nuevo en las siete fuentes
acotadas revisadas; Copias/Gateway/BackupViewModel y tema por defecto siguen
iguales a la entrada. Eso no sustituye ejecución física. El helper comparativo
pasó 29 controles puros sin dispositivo; están preparadas campañas que paran
ante el primer fallo y extracción exclusiva de los 24 PNG sintéticos nuevos.

La guarda detectó foco ajeno y se detuvo antes de instalar, sin inspeccionar esa
pantalla, enviar entradas ni consultar hashes instalados. Recibo redactado:
`android-font-20261011/guards/20261012T191649.386944Z.json`.
Una comprobación posterior identificó Inicio y los hashes intermedios esperados,
con pública `be3bca…aa2f94` intacta: `guards/20261012T191841.282748Z.json`.
Antes de la instalación volvió el foco ajeno y no se ejecutó `install`. Una
consulta redactada confirmó que esa ventana no era la app pública; no se
identificó ni inspeccionó la aplicación ajena. Se pidió aclaración al usuario.
Pendientes: instalación final, diagnóstico de cuatro variantes, regresiones,
matriz mensual, recorridos, TalkBack audible, rendimiento y comparación visual.
Esperar QA o Inicio positivamente identificados; no abrir QA sobre otra app ni
debilitar la guarda. Ningún resultado intermedio acredita el APK final.

### Puerta de instalación superada

Joaquín identificó WhatsApp y autorizó continuar. Guarda positiva en Inicio:
`guards/20261012T192210.146435Z.json`. Instalación exclusiva QA/runner con `-r`,
hashes finales coincidentes y pública sin cambios; recibo
`android-font-20261011/install-20261012T192216.979902Z.json`.

Diagnóstico de cuatro variantes **1/1**: `.local-evidence/qa/20261012T192222.784769Z`.
Se extrajeron exclusivamente seis casos/24 PNG del sandbox QA; hashes,
decodificación e igualdad de píxeles comprobados. Seis representantes vistos
por MAIN, texto completo sin recorte. Evidencia:
`android-font-20261011/diagnostics/20261012T192453.680637Z-four-variants/`.
Pillow no estaba en el Python de PATH; se usó el runtime integrado, sin instalar
dependencias ni cambiar el proyecto.

La siguiente clase no ejecutó instrumentación: `.local-evidence/qa/20261012T192231.860965Z`,
0 pasadas y 7 pendientes. `grep mCurrentFocus` terminó sin ventana durante la
transición. Se preservó el supervisor anterior y se ajusta sólo su consulta:
ausencia no autoriza; deben coincidir dos propietarios positivos permitidos.
El fallo de preparación no se clasifica como fallo de las siete pruebas.

Los apartados siguientes conservan las campañas anteriores y sus límites.

## Entrada e instalación

### Ampliación autorizada: letra Android y retiro del zoom interno

Joaquín confirmó retirar el zoom interno, respetar el tamaño de letra Android,
mantener TalkBack sin controles extra, preservar Copias y continuar sólo en QA.
ADR 0043 sustituye el escalado de ADR 0042; conserva las garantías mensuales.
La entrada de esta unidad se selló en
`.local-evidence/stage7/android-font-20261011/entry/repository.json`: 972 rutas,
HEAD/branch originales y staging vacío, sin limpiar el checkout acumulado.

Tema, Activity, Apariencia, Calendario, Ayuda y configuración de Widget dejan
de leer/aplicar zoom visual. Se hereda LocalDensity nativo, sin convertidor SP
lineal propio ni densidad global fija. La clave/enum legado quedan únicamente
para las 17 preferencias portables. Gateway, esquema, formato, versiones y
cifrado no cambian. Nuevas regresiones cubren round-trip100/150/200 y rechazo
de 14 entradas inválidas sin escrituras.

El preflight confirmó Samsung desbloqueado en QA, con APK `e757a2…892aa`,
runner `a7acc0…540725` y pública `be3bca…aa2f94`, iguales a la revisión anterior.
La nueva validación local pasó 932/932 (395 dominio, 19 base, 518 app), sin
resultados reutilizados, fallos, errores, omitidas ni insumos cambiados:
`android-font-20261011/validation/local/result.json`, bajo la evidencia Stage 7.
Catálogo revisado: 121 clases/28 grupos, válido. Build e instalación nuevos
en curso; los recibos históricos siguientes no acreditan esta unidad.

Rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`;
staging vacío y único worktree. Las 971 rutas versionables coincidían con
`stage7/20261011-vigilia/exit/repository-seal.json`: sin diferencias inexplicadas.
Se preserva el trabajo acumulado. Evidencia nueva bajo
`.local-evidence/stage7/qa-main-20261011/`, sin sobrescribir recibos anteriores.

Samsung SM-S938B autorizado, serial explícito [serial QA en binding local privado]. Preflight positivo:
desbloqueado e Inicio al frente. Sólo se sustituyeron QA y runner de app con
instalación conservadora `-r`, sin limpiar/desinstalar paquetes.

- QA: `5d2b65b3a14a45c3a54240d81dedcb12c8741064f3d2f902528a04539a351707`.
- Runner: `1d68df0b70313e1fb0827428c059e357d9c29c8c7c6b160e498cdea5eb7db039`.
- Pública antes/después: `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.

Se conservó la APK QA Stage 5 instalada, con hash `6ae70d…5b1b3`, para una
eventual comparación pareja. No se abrió ni instrumentó el paquete público;
su consulta se limitó a identidad/hash. Recibo de instalación en el directorio
nuevo. Guardas de foco/bloqueo antes y después de UI/capturas y durante pruebas;
una ventana ajena o ambigua detiene el ensayo, sin inspeccionarla ni pulsarla.

## Cobertura y límites

Las seis bases visuales se capturan con datos ficticios REC recibidos antes de
las fixtures que modifican el sandbox QA. Capturas originales, hashes y metadatos
en `originals/` y `captures/`. No son imágenes generadas ni aceptación visual.

Pendientes: cierre de fallos nuevos, recorridos críticos restantes, comparación
de rendimiento pertinente y ensayos OEM/humanos. La
ejecución compilada y los verdes JVM de la entrega conservan su atribución;
no se presentan como ejecución Android nueva.

## Precisión documental

La entrega dice «No se reduce la fuente para hacer entrar contenido». El delta
elige `bodyMedium` de 14 sp en algunas etiquetas antes heredadas de 16 sp:
Horas, Resumen e Informes. No hay auto-reducción en función del ancho; se trata
de una nueva jerarquía fija. Su legibilidad real requiere comprobación. Esta
precisión no modifica producto ni equivale a aprobar ese tamaño.

## Hallazgos en la primera ejecución física

La primera campaña ejecutó el APK y runner originales, sin cambios de producto
ni relajación de pruebas. Los siguientes fueron fallos de comprobación,
no causas de producto demostradas todavía:

| Superficie | Resultado | Evidencia adversa |
|---|---|---|
| Tema | 2/2 | `20261012T012600.017015Z` |
| Horas | 6/7 | `20261012T012855.900447Z`: `32 h`, ventana 320×480, claro/100 %, `hasVisualOverflow` en línea 336 |
| Informes | 5/5 | `20261012T013015.696709Z`: incluye V02 y confirmación médica |
| Resumen | 12/13 | `20261012T013155.017555Z`: comprobación de texto completo, línea 439 |
| Configuración laboral | 16/17 | `20261012T013301.155419Z`: `performScrollTo`, línea 218 |
| Retén | 8/8 | `20261012T013352.183083Z` |
| Detalle/Calendario común | 12/14 | `20261012T013415.920456Z`: badge Completada desborda según layout; mensaje transitorio no encontrado |
| Adaptación | 5/6 | `20261012T013607.758749Z`: expectativa accesible del cruce nocturno |
| Composición contextual | 8/8 | `20261012T013637.796144Z` |
| Próximo evento | 24/24 | `20261012T013732.964591Z` |
| Bloqueo, controles propios | 5/5 | `20261012T013550.838026Z`; no autenticación OEM real |
| Matriz mensual | 2/2 métodos; 48/48 combinaciones | `20261012T013844.011918Z` |
| Carga manual Activity | 8/8 | `20261012T014749.494366Z` |
| Widget RemoteViews | 10/10 | `20261012T015007.683822Z`; no launcher OEM |
| Consultas sin escritura | 1/1 | `20261012T015045.093909Z` |

Todos los identificadores son recibos nuevos bajo `.local-evidence/qa/`.
Horas/Resumen/detalle requieren separar ancho, alto, fuente y redondeo del
layout antes de corregir. El fallo de scroll y el mensaje transitorio también
requieren atribución; no se los declara automáticamente defectos de producto.

El supervisor inicial detuvo ensayos sin ejecutar por transiciones de foco.
Se preservaron los recibos. El supervisor ignorado reconoce ahora únicamente
las ventanas del runner QA durante instrumentación y espera de forma acotada
la coincidencia positiva QA/Inicio/runner. Un `grep` sin dueño se trata como
ausencia de evidencia, no como autorización. Foco ajeno o bloqueo detienen.

Revisión de las siete capturas reales: paleta coherente y mes completo sin barra
permanente; V02 se ve separado en Claro/100 %. Persisten dos signos `?` por día
indefinido y una tipografía visualmente pesada frente a la referencia. No se
atribuyó esa apariencia al sistema/OEM sin prueba. Los botones activos grises
mantienen V04 como hipótesis de comprensión, no fallo funcional demostrado.

La campaña original cerró **124/130 métodos distintos**, seis fallidos, cero
omitidos. Índice: `campaign-20261012T015337.197082Z.json` bajo la evidencia
nueva. No se suman reintentos como casos nuevos ni se reasignan estos resultados
a la revisión posterior. Los recibos interrumpidos por la guarda no son fallos
de producto ni pruebas ejecutadas.

Las 48 combinaciones cruzan cuatro meses, ambos temas, zoom 100/150/200 y
retrato/paisaje. Los PNG y sus hashes están en
`matrix/20261012T013844.011918Z/`. No se observó contradicción con geometría,
mes completo y ausencia de botones permanentes. En zoom alto baja la información
secundaria y aparece `3 t…`; conservar fechas e iconos no certifica comprensión
humana ni fidelidad estética. No se cambió zoom ni fuente global de Android.

## Ampliación autorizada: oscuro por defecto

Joaquín pidió explícitamente iniciar en Vigilia oscuro y permitir Claro desde
ajustes. `AppThemeMode.DEFAULT = DARK`; ausencia/valor inválido usan ese valor.
LIGHT/DARK/SYSTEM guardados se respetan. Los argumentos iniciales de
`MiGuardiaApp` comparten el mismo default; MainActivity, Widget y preferencias
portables consumen el parser existente. No cambian claves, formato de Copias,
Room, versión, permisos ni dependencias.

Se agregaron regresiones JVM de ausencia, valores inválidos y conservación de
las tres elecciones. Apariencia prueba el valor inicial y round-trip del enum;
eso solo no acredita persistencia real en disco. Verificación fresca:
**932/932 JVM** (395 dominio, 19 base, 518 app), sin fallos/errores/omitidos,
sin resultados reutilizados ni inputs cambiados. Build completo verde,
`lintDebug` sin errores y con 24 advertencias; APK Debug/QA/Release unsigned
y runners app/base. Runner base reutilizado con insumos sin cambios.
Recibos: `default-dark-validation/local/result.json` y
`default-dark-validation/build/result.json` bajo la evidencia nueva.

Instalación conservadora exclusiva QA/runner, pública igual antes/después:

- QA oscuro: `e757a2e1e89d4dd9e629fa20be7b1375244c5db184964fedecb7771c99f892aa`.
- Runner inicial de esta revisión: `ec054eb725a22a2ae1624c3d32606292d2033e24d6c7b579f9296016f55e8f82`.
- Recibo: `default-dark-install-20261012T015458.669203Z.json`.

Apariencia **2/2** (`20261012T015501.219990Z`) y tema **2/2**
(`20261012T015511.342113Z`) pasaron en esa revisión. La repetición focalizada de
los seis fallos volvió a fallar, sin guarda interrumpida: recibos
`20261012T015557.038754Z`, `015608.892475Z`, `015620.609199Z`,
`015638.683841Z`, `015651.129710Z` y `015704.363748Z` del mismo día UTC.

## Atribución y corrección de las pruebas

Tres causas de prueba quedaron demostradas, no inferidas:

1. WorkSetup pidió índice `[1]`; el stack preserva exactamente **un** nodo.
   Selector único mantiene scroll, apertura, callback y ausencia sin primer set.
2. El aviso vigente dice `Ver aviso`; el mensaje completo vive en accesibilidad
   y en el detalle al tocarlo. La regresión ahora exige ambos, abre/cierra el
   detalle y conserva `hoursClears == 0`, `availabilityClears == 1`.
3. El fixture nocturno se anuncia con `del día siguiente`; la expectativa ahora
   exige ese cruce junto con objetivo, horas y estado, sin omitir geometría.

Horas, Resumen y badge mantienen intacta la aserción `hasVisualOverflow`.
Sólo se añadieron texto/tag, ancho/alto, restricciones, párrafo e intrínsecos
para atribuir el fallo. No se aumentaron esperas ni tolerancias.
Cinco fuentes AndroidTest y hashes del catálogo cambiaron, no el producto.
Compilación dirigida `assembleQaAndroidTest`: exit 0, sesión local 46184.
Nuevo runner: `23ea7396b20b3d0518d6a7766931c742fa09286564b846bb88d033e8129afe80`.
Los recibos originales y preimágenes se preservan; el nuevo runner tiene
atribución separada y no reemplaza retrospectivamente los resultados anteriores.

La primera corrección de aviso requirió avanzar un frame del reloj Compose
pausado para medir el diálogo nuevo; no se amplió timeout. Recibo adverso
`20261012T020440.005537Z` preservado. Con ese frame, aviso/detalle/callbacks
pasaron **1/1** (`20261012T020835.745067Z`). WorkSetup pasó **1/1**
(`20261012T020432.297845Z`) y adaptación **1/1** (`20261012T020450.826068Z`).
No se suman estos reintentos al total de métodos del candidato original.
Runner diagnóstico final, compilado con exit 0 en sesión 88555 e instalado:
`a7acc0e65e3c80cd7f7f2f99e8142edcc4d17a1f114b6a4697d18f092c540725`.
El producto sigue siendo `e757a2…92aa`, sin cambios tras el build completo.

Persisten tres aserciones de overflow: `20261012T020843.683375Z` (Horas),
`20261012T020855.151747Z` (Resumen), `20261012T020907.531356Z` (badge).
Sus resultados tienen ancho intrínseco 88/258/250 px y párrafos 720/698/579 px;
todos reportan desborde horizontal, ninguno vertical. En Horas/Resumen el
layout accesible ubica la línea a la derecha de su propia caja; en el badge
la línea 0..250 cabe aunque el párrafo sea mayor. Falta contrastar la medición
semántica con el dibujo real; no se declara recorte visible ni se relaja el
assert. Diagnóstico/runner son distintos del APK original y del runner inicial
oscuro. El primer intento dirigido no ejecutó instrumentación por hash del
catálogo sin normalizar CRLF; se corrigió a UTF-8/LF/no-BOM y catálogo VALID.

Persistencia real de Apariencia: se eligió Claro, se cerró/reabrió el proceso
QA y conservó Claro; lo mismo para Oscuro. Evidencia
`theme-real-preferences-*.json`, con capturas de ambas elecciones en `originals/`.
La entrada ya guardaba DARK: este recorrido no prueba una instalación virgen;
el default sin elección tiene regresión JVM y verificación del parser.

Quedan rendimiento nuevo, revisión humana/estética, TalkBack y recorridos OEM
pertinentes. Los recibos previos no se reasignan. No se cerró Stage 7, ni se
usaron distribución, app pública, datos personales, commits o servicios reales.
La consulta posterior de Joaquín sobre retirar zoom/TalkBack abre una decisión
de producto, todavía no implementada; no elimina por inferencia la accesibilidad
ni la compatibilidad de preferencias/Copias.
