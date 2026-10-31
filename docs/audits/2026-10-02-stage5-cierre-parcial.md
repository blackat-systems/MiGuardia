# Stage 5 — cierre parcial con QA física

Fecha de entrada: 2026-10-02. Estado y autorización: [STATUS](../STATUS.md).
Contrato: [Stage 5](../prompts/STAGE_5_CIERRE_VISUAL_FLUIDEZ_E_INTEGRIDAD_3_0.md).
Esta auditoría no acepta Stage 4 ni acredita distribución.

## Puerta 0 y propiedad

- Checkout: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`.
- Rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Upstream `origin/codex/miguardia-3.0`; remoto `https://github.com/blackat-systems/MiGuardia.git`.
- Un worktree, staging vacío; checkout acumulado intencionalmente modificado.
- Entrada en `.local-evidence/stage5/entry/`: 939 rutas, bytes y hashes de los
  archivos presentes, archivos ausentes explícitos, estado Git, patch binario,
  staging y APK QA/runner disponibles. HEAD no representa esa entrada completa.
- Se compararon los 46 archivos del manifiesto final Stage 4. Las ocho
  diferencias corresponden a QA MAIN: HelpComposeTest, Stage4FlowsActivityTest,
  catálogo QA, STATUS, índice, handoff, matriz y rendimiento. Su procedencia
  está documentada en la devolución MAIN; no se descartó ninguna.
- Límite literal de escritura: este checkout 3.0. Sin commits, ramas,
  worktrees ni tareas nuevas; un revisor interno leyó sin escribir ni usar
  Gradle/dispositivo. 2.0 y recuperación intactos.

## Calendario: decisión indispensable

No se modificó su política ni se redujeron objetivos táctiles. Se inspeccionó
la captura preservada `2026-08-light-200.png` en paisaje y se recalcularon las
48 medidas JSON de Stage 4. Después se repitió la matriz sobre QA final: **16/48 pasan geometría, 32/48 fallan** (`20261002T233001.953707Z`). Las 48 capturas propias de Compose son idénticas byte a byte al antes Stage 4; no hay mejora visual de calendario. Evidencia final en `.local-evidence/stage5/device/calendar-final/`.

Con densidad interna 5,625 px/dp a zoom 200:

| Ventana | Espacio total | Mínimo táctil exigido, sin márgenes ni encabezados |
|---|---|---|
| Retrato | 1440 px de ancho | 7 × 48 × 5,625 = 1890 px |
| Paisaje, seis filas | 1440 px de alto | 6 × 48 × 5,625 = 1620 px |

La grilla histórica de agosto en paisaje recibe sólo 574 px de alto. Reducir
el espacio secundario puede mejorarla, pero ni eliminándolo íntegramente
satisface el mínimo actual en esas ventanas. Diez de las 48 combinaciones
exceden al menos una dimensión de la pantalla completa antes de reservar
cualquier control. Otras combinaciones también fallaron con la composición
actual: no confundir estos diez límites absolutos con los 32 fallos reproducidos.

Evidencia reproducible: `.local-evidence/stage5/calendar-feasibility.json`,
derivada de `.local-evidence/stage4-main/candidate-first/calendar-matrix-*/matrix.json`.
La causa horizontal adicional en código es el ancho mínimo de 378 dp con
desplazamiento horizontal en CalendarGridViewport. Retirarlo sin resolver los
objetivos táctiles sólo cambia recorte por superposición/reducción.

Se presentaron tres alternativas a Joaquín:

1. Recomendada: zoom tipográfico en calendario, objetivos de al menos 48 dp de
   densidad base y menos información secundaria; conserva el mes completo,
   pero cambia el escalado geométrico de celdas. Requiere comprobar después
   legibilidad, selección y toda la matriz, no garantiza aprobación anticipada.
2. Permitir desplazamiento del mes cuando no cabe con zoom completo; cambia
   la política de mes simultáneamente visible y sin scroll principal.
3. Mantener la política y declarar bloqueadas las ventanas incompatibles.

La decisión sigue pendiente. ADR 0003 conserva el antecedente de desplazamiento
horizontal; el contrato Stage 5 exige ahora fechas visibles y operables en las
siete columnas. No resolver esta tensión por una excepción silenciosa.

## Ayuda: corrección y alcance

El menú utiliza solicitudes BringIntoView sobre sus controles reales Fotos,
Cargar y Ayuda después de abrirse y disponer de layout. Repite el posicionamiento
al cambiar paso, sesión, tamaño de ventana o zoom. No invoca acciones del menú
ni abre Ayuda como sustituto de señalarla. El panel explicativo limita su altura
al espacio libre para no tapar esos controles en ventanas bajas.
MENU conserva cerrado el panel mientras señala el botón real de apertura:
abrirlo en ese paso cubría el mismo botón al que apuntaba el marco.

Se conserva la prueba original que falló en HELP y se agregan regresiones de
temas, zoom, orientación, regreso y restauración Compose. Las **12 pruebas Help pasaron físicamente** en `20261002T231035.661485Z`, incluyendo la regresión original HELP. StateRestorationTester tampoco demuestra
muerte de proceso ni recreación física de Activity. Ver validación final abajo.
Las pruebas nuevas exigen el nodo real completo dentro del drawer, sin
solapamiento del panel explicativo; cambian zoom con la composición viva y
comprueban cierre/regreso a Ayuda mediante evento de navegación ficticio.
Esto prueba la integración prevista, no una ejecución del coordinador real
ni la escucha humana. Una revisión independiente detectó esos vacíos en la
primera versión del test y se reforzaron sin debilitar el oráculo original.

## Rendimiento: guard y diagnóstico

Se reprodujo pérdida de foco al launcher con QA STARTED: el driver invocaba
el dispatcher de Activity para cerrar un ModalBottomSheet y omitía su ventana.
Se corrigió a Atrás nativo de Espresso, conservando el guard, diagnóstico
mínimo privado y latch. El catálogo ejecuta un método por invocación y corta
ante fallo. No se atribuye retroactivamente esa causa a todos los fallos Stage 4.

Se completó el par baseline/candidata: doce iteraciones representativas y doce
de estrés por APK, mismo runner final, snapshots iniciales iguales y ventanas
1440×3120. Ninguna de las 18 operaciones supera umbrales de investigación.
No se optimizó producto ni se afirma mejora, FPS o aceptación humana.
[Mediciones, percentiles, hashes y fallos preservados](2026-10-02-stage5-rendimiento.md).
Frío: doce aperturas por APK, TotalTime p50 463→488 ms, p95 481→513 ms;
contenido listo y equivalencia de ventanas frías pendientes.

## Integridad y revisión humana

Se reconciliaron [los 33 recorridos](2026-10-02-stage5-matriz-recorridos.md),
separando recibos históricos, candidatos locales y pendientes Android/humanos.
Room, esquemas, Copias, reglas laborales, versión, applicationId, dependencias,
formularios y consumidores de datos no tienen cambios propios.

Regresiones físicas de esta candidata, con recibos bajo `.local-evidence/qa/`:

| Clase / alcance | Resultado | Recibo |
|---|---:|---|
| Help, controles reales y matrices | 12/12 | 20261002T231035.661485Z |
| NavigationDrawer, Compose/dobles | 7/7 | 20261002T231242.179949Z |
| V2ManualShiftLoadActivity, borrador completo/recreación/imagen sintética | 8/8 | 20261002T231254.149674Z |
| V2ReadOnlySurfaces, snapshot 29 tablas | 1/1 | 20261002T231401.495223Z |
| CalendarCommonV2, selección y acciones con dobles | 11/11 | 20261002T232839.072267Z |
| Exceptions, GENERAL/HOLIDAY y cancelación con dobles | 9/9 | 20261002T232928.272883Z |
| Padres reales y recreación, dos métodos Flows | 2/2 | 20261002T232936.759074Z |
| ManualLoadNotes, persistencia/reintento | 5/5 | 20261002T234337.764166Z |
| SchedulePhotoFileStore, archivos sintéticos | 9/9 | 20261002T234340.194520Z |
| Stage4NoteRestoreConcurrency, barreras reales SQLite/archivos | 2/2 | 20261002T234343.731251Z |
| CalendarClear, lotes/rollback/CAS | 11/11 | 20261002T234347.248434Z |
| V2DatabaseMigration, migraciones sintéticas | 8/8 | 20261002T234355.665280Z |

Son 85 métodos dirigidos verdes; no sumar iteraciones de rendimiento ni 48
combinaciones como métodos adicionales. Los tests database identifican su
runner independiente; no se presentan como ejecución de pantallas QA.
No son actualización de la app pública, Copia personal ni selector OEM.

CalendarCommon falló inicialmente por tres entradas obsoletas «Editar día»
para situaciones: ese control abre carga unificada. Se corrigió el recorrido
del test a `day-other-options`, sin retirar comprobaciones de UUID/estado.
Se conservan fallo inicial `20261002T231404.174822Z`, repetición dirigida
`20261002T231500.233869Z` y corrección incompleta `20261002T232035.480310Z`.

Pendientes: revisión visual humana de Ayuda/calendario, TalkBack, teclado,
selector OEM, muerte de proceso de la guía y coordinador real de su cierre.
La restauración Compose y callbacks ficticios no acreditan esos casos.

## Validación final

Perfil **full final aprobado: 916/916 JVM y 26/26 interoperabilidad**, cero
fallos/errores/omitidas; lint Debug sin errores y con 24 advertencias, APK
Debug/QA/Release unsigned y ambos runners compilados. Recibo:
`.local-evidence/stage5/final-validation/result.json`, `status=passed`,
`changed_inputs=[]`. Interoperabilidad usa website sólo en lectura y Node
local; no acredita tráfico productivo ni publicación.

Identidades SHA-256 de producto y runner final instalados/verificados en esta unidad:

- QA: `4d708c1f6e398e0675dac885b65b8728c992488a796ed22280f77d72b4330b30`.
- Runner app: `85915a760345fa62fa0804a589f8934399674002e6258fe3103a839e0a2769cc`.
- Runner base: `9d4be8ff629df0bef020936c6f1064f1bb680eb9a5b1766e2a9654c026bbcfce`.

Catálogo: 114 clases/24 grupos. `docs_check.py` y `git diff --check` aprobados.
Se comprobaron 241 archivos protegidos contra la entrada, sin cambios; los
33 IDs de matriz coinciden en orden y cobertura. Detalle en
`.local-evidence/stage5/review/protected-inputs.json` y `matrix-ids.json`.
Delta propio: inventario exacto por comparación contra la entrada;
manifiesto con SHA-256, copias exactas y patch en `.local-evidence/stage5/final/`.

Se conservan también `local-rerun/` (916 JVM) y `build/` aprobados sobre la
iteración anterior, sin reasignarlos al producto final. El preflight inicial
falló por una ruta Java inexistente, preservado en `local/`; se recuperó del
recibo anterior el JBR instalado, sin instalar dependencias. El chequeo de
documentos encontró presupuesto de lectura excedido y doble contrato activo;
se corrigieron moviendo detalle a esta auditoría y clasificando Stage 4 como
antecedente. Una comprobación auxiliar de IDs falló por codificación Windows;
se repitió con UTF-8 explícito y verificó los 33 IDs.

La autorización explícita de Joaquín reanudó QA con el Samsung libre y
desbloqueado. Se instalaron sólo QA/runners; baseline y candidata conservaron
sus hashes separados. Las corridas previas a cambios exclusivos del runner
mantienen su propio hash en cada recibo; no se reasignan al runner final.
El producto QA no cambió durante esas iteraciones. Build final de runner:
`.local-evidence/stage5/measurement-runner-validation/result.json`, aprobado.
Identidad/preservación final: `.local-evidence/stage5/device/final-identity.json`.
La comprobación del hash público acredita APK intacta; no se leyó su base de
 datos para afirmar preservación mediante comparación de contenido.
