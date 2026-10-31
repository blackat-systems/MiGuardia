# Stage 8 Rendimiento de Resumen y guardado

La preparación del 22/10 descrita abajo fue sucedida, tras la respuesta de Joaquín,
por [el contrato acotado de comparación](STAGE_8_CIERRE_COMPARACION_RENDIMIENTO_3_0.md).
Este documento conserva el alcance e historia originales; el permiso actual
se consulta en STATUS y en ese sucesor. No reejecutar q2/q3.

## Prioridad retomada el 22/10 — preparación local del cierre

Joaquín indicó en MAIN: «Perfecto, antes de continuar cualquier cosa terminemos
stage 8». MAIN pidió pausar Stage10 en un punto seguro, conservando QA8 y evidencia.
El diagnóstico q3 ya auditado no se repite: la causa de la alerta sigue inconclusa.

Esta instrucción permite preparar el cierre de rendimiento en local. La comparación
física propuesta alterna baseline/R4 y su detector vacía tablas y sustituye
preferencias QA: no es una consulta ni conserva la fixture instalada de Stage10.
Antes de despacharla hacen falta un contrato acotado para esa tarea nueva y una
decisión expresa sobre entorno/datos/instalación. No deducir esos permisos del
cambio de prioridad, de una conexión antigua ni del permiso específico de Stage10.
No iniciar ADB, otro chat, experimento u optimización mientras falte esa decisión.
Preservar q2/q3, artefactos y límites; estado y próximo paso sólo en STATUS.

## TASK

Diagnosticar y estabilizar las operaciones `open_summary` y
`save_three_shifts` bajo historial grande, conservando todas las funciones de
MiGuardia 3.0. Entregar una unidad completa: causa sustentada, corrección mínima
cuando la evidencia la justifique, regresiones, validación local y handoff a
MAIN. Si confirmar la causa requiere Android real, entregar el diagnóstico
instrumentable compilado y la prueba física pendiente, sin optimizar a ciegas.

## CONTEXT

Joaquín pidió el 2026-10-21 crear Stage 8 en un **chat propio**, enviarle este
contrato, supervisar su trabajo y recibir su resultado en MAIN. Esto autoriza
implementación y validación locales dentro de este alcance; no autoriza nuevos
ensayos físicos, aceptación estética, cambios de producto o distribución.

Stage 7 Vigilia aporta la candidata R4. Conserva 935 JVM y 56 métodos Android
dirigidos históricos, más una matriz mensual de 48 combinaciones. Esos resultados
no son ejecuciones de Stage 8 ni aceptación global. La comparación caliente
representative no alertó; las dos parejas stress alertaron en Resumen y la
segunda también en guardado. Son alertas observadas, no una causa demostrada.

`open_summary` incluye menú, sincronización Compose, proyección CONTENT/EMPTY
y comprobación de foco. `save_three_shifts` incluye guardado y propagación a
estado inactivo. No atribuir los tiempos exclusivamente a dibujo o SQL.
Observar todas las jornadas ya existía antes de Vigilia; medir texto y usar
letra Android son hipótesis por contrastar. Resumen ya tiene control de
visibilidad/lifecycle: no implementar otra vez lo existente.

MAIN es el chat `001 · MAIN`, id
`01a09739-5279-75d0-a3b1-84bd2af6b36a`. Stage 8 es el único escritor de su
implementación; MAIN supervisa en sólo lectura mientras el especialista trabaja.
No es un worker interno ni debe crear otro chat para ejecutar esta tarea.

## INPUTS

- Contenedor: `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer`.
- Trabajo: `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`.
- Rama: `codex/miguardia-3.0`; HEAD verificado:
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Repo independiente, `.git` propio, un worktree, upstream
  `origin/codex/miguardia-3.0`, remoto MiGuardia. Checkout acumulado sucio
  intencional y staging vacío. No interpretar todo el diff contra HEAD como propio.
- El sello `.local-evidence/structure/20261020/exit/repository.json` tiene 977
  rutas; MAIN lo contrastó antes de despachar: ninguna diferencia. El despacho
  agrega este contrato y modifica sólo STATUS e índice de prompts. Crear una
  entrada propia después de esos cambios. Una prueba ausente ya tiene hash
  nulo en el sello; no restaurarla por inferencia.

Lectura inicial, en orden:

1. `../AGENTS.md`, `AGENTS.md`, `docs/STATUS.md` y este contrato.
2. `docs/LECTURAS_POR_TAREA.md`, `docs/WORKFLOW.md`, `docs/VALIDACION.md`.
3. `docs/HANDOFF_STAGE_7_A_MAIN_3_0.md`, entrega vigente R4; auditoría
   `docs/audits/2026-10-11-stage7-qa-main.md`, apartado «Rendimiento R4».
4. `docs/audits/2026-10-20-integridad-y-rutas-del-workspace.md` para entorno.
   ADR 0043 y ADR de proyección/persistencia sólo según la superficie afectada.

Fuentes de partida, no lista obligatoria de archivos a modificar:

- `app/src/main/java/com/blackatsystems/miguardia/ui/summary/SummaryViewModel.kt`
  (`SummaryObserver`) y `SummaryScreen.kt` (`SummaryAmountLine`).
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`:
  activación/navegación de Resumen y dueños de Atrás.
- `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ManualShiftLoadViewModel.kt`:
  guardar, confirmación y propagación.
- `core/database/src/main/java/com/blackatsystems/miguardia/core/database/repository/RoomV2ShiftRepository.kt`
  sólo si el diagnóstico lo exige; consultar transacción y asociados antes de tocar.
- Driver E2E vigente:
  `app/src/androidTest/java/com/blackatsystems/miguardia/Stage4FlowsActivityTest.kt`.
- Regresiones existentes: `SummaryViewModelTest`, `SummaryStateTest`,
  `MonthlySummaryTest`, `V2ManualShiftLoadCoordinatorTest`, `SummaryComposeTest`,
  `SummaryActivityTest`, `V2ManualShiftLoadActivityTest` y
  `ManualLoadNotesInstrumentedTest`. Resolver las rutas con `rg --files`.

Evidencia a preservar bajo `.local-evidence/stage7/android-font-20261011/`:

- Auditoría y recibos `performance/` representative y ambas parejas stress del
  12/10; no sobrescribirlos ni reatribuirlos.
- `artifacts-final-r4/`: QA SHA-256
  `99e32ed7525b23ac5452d8eebab019f7051c2f7e6e50dc47db9a2e918dd4dc29`,
  runner `bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
- Baseline Stage 5 preservada: `6ae70d…5b1b3`; recuperar su SHA completo del
  manifiesto antes de usarla como insumo, nunca a partir de la abreviatura.

El entorno se prepara por sesión, no globalmente:

```powershell
. .\.local-evidence\structure\20261020\Enter-MiGuardiaEnvironment.ps1
```

Python 3.12.14, JBR 25.0.2 y SDK Android 37 fueron repuestos. `local.properties`
es privado e ignorado. No reinstalar herramientas ni cambiar dependencias para
resolver errores de PATH. La ayuda Gradle y procesamiento de manifiesto pasaron;
eso no constituye un build o batería JVM nueva de Stage 7/8.

## OUTPUT

1. Diagnóstico causal que separe hechos, hipótesis y limitaciones de medición.
2. Corrección mínima sólo de causas reproducibles, con consumidores y errores
   pertinentes cubiertos. Si no hay causa comprobada, diagnóstico preparado y
   pendiente físico explícito, sin fingir una corrección.
3. Pruebas de conservación funcional y un diagnóstico de rendimiento por
   fronteras, sin convertir microbenchmarks locales en certificación Android.
4. Evidencia nueva bajo `.local-evidence/stage8/`: entrada, originales propios,
   delta contra entrada, hashes, insumos, comandos, resultados y adversos.
5. Crear la auditoría `2026-10-21-stage8-rendimiento-resumen-y-guardado.md`
   dentro de `docs/audits/`, y `HANDOFF_STAGE_8_A_MAIN_3_0.md` dentro de `docs/`:
   causa, cambios, validación real, pendientes, prueba siguiente y estado Git.
   Son salidas futuras, no evidencia ya existente. No repetir todo el historial.
6. Respuesta final que avise explícitamente que cesaron las escrituras y Gradle,
   para que MAIN pueda revisar y ejecutar comprobaciones independientes.

## SCOPE

- Una capacidad: fluidez de consulta de Resumen y guardado íntegro de la carga.
- Diagnóstico, implementación y regresiones en esas rutas; consumidores directos
  sólo con dependencia demostrada. Consultas/observación pueden ajustarse sin
  esquema nuevo si conservan íntegro el contrato y la equivalencia se prueba.
- Herramientas de diagnóstico preferentemente locales e ignoradas; si una
  herramienta versionable cambia, justificarlo y probar sus casos de rechazo.
- Stage 8 escribe código, pruebas relacionadas, catálogo QA si corresponde y
  sus dos documentos de entrega. MAIN reserva `docs/STATUS.md`, este contrato,
  `docs/prompts/README.md` y la integración. No editarlos en paralelo.
- Una sola ejecución Gradle a la vez. Revisores internos, si hacen falta, sólo
  lectura o archivos expresamente separados; no son sustitutos del chat Stage 8.

Hitos internos de la misma tarea, no otros stages:

1. Puerta 0, sello de entrada y revisión del driver antes de interpretar números.
2. Separar navegación/activación, carga/proyección, composición y sincronización;
   en guardado, preparación, transacción/asociados y propagación posterior.
3. Usar fixtures deterministas representative y stress. Conservar el detector
   E2E original: 240/24/365 y 1920/192/2920 (jornadas/notas/días), 12 iteraciones,
   nueve operaciones, crecimiento de tres jornadas por iteración y percentiles
   nearest-rank. Un diagnóstico acotado adicional debe identificarse como tal.
4. Corregir causas sustentadas, comprobar equivalencia y revisar el delta.
5. Entregar diagnóstico Android compilado y plan físico exacto si es necesario.
   No detenerse ante decisiones menores resolubles; sí ante una ampliación material.

## DEPENDENCIES

- Candidata Stage 7 R4 y entorno recuperado. No depende de teléfono para el
  diagnóstico y validación locales.
- Medir de nuevo E2E en Android requiere autorización específica Stage 8,
  disponibilidad real, guardas de foco/privacidad y hash instalado verificado.
  Que el Samsung esté conectado o autorizado para Stage 7 no basta.
- La aceptación estética/global de Stage 7 y las pruebas de actualización desde
  2.0.2/Copias siguen siendo puertas separadas; no se cierran por este encargo.

## DO NOT

- No ADB, Samsung, emuladores, instalación ni consulta de app pública/datos
  personales. No trasladar un permiso histórico a esta etapa automáticamente.
- No ramas, worktrees, staging, commits, push, tags, firma, AAB, distribución,
  website, backend ni servicios remotos. No crear más chats de implementación.
- No limpiar, mover o sobrescribir el checkout previo, los rescates o evidencia.
- No modificar Room/esquemas/migraciones, Copias/formato/preferencias legadas,
  versionado, applicationId, permisos, seguridad, autenticación o dependencias.
- No cambiar reglas laborales, cálculo en minutos, límites temporales, zonas,
  resultados, CAS/transacciones, identidad de notas/archivos o confirmaciones
  para acelerar. Cachés y deduplicación deben invalidarse correctamente.
- No rediseñar Vigilia ni retirar funciones. Preservar oscuro inicial y elecciones
  guardadas, letra Android, TalkBack sin controles extra y ausencia de zoom interno;
  mes completo sin scroll/barra normal, borradores y único dueño de Atrás.
- No rebajar umbrales, cambiar muestras, descartar máximos, reducir datasets o
  esperas de corrección funcional para obtener un verde. Una medición inválida
  se conserva con su causa y se distingue del resultado de producto.
- No proclamar fluidez integral, FPS, arranque frío listo, aceptación humana ni
  pruebas físicas por un resultado JVM o AndroidTest compilado.

## VALIDATION

- Iterar con pruebas focalizadas; al modificar Kotlin/Compose, cerrar con
  `python tools/verify.py --profile local --fresh-tests` y perfil `build` en
  directorios de evidencia nuevos. Si cambia Python, ejecutar sus pruebas.
- AndroidTest diagnóstico/regresiones compilan; ejecución física queda pendiente.
- Casos de conservación: mes/sector/configuración vigente, cambios de mes,
  medianoche y cruces, horario real, notas/adjuntos, fechas finales del borrador,
  sin duplicación ante reintento, cancelación sin escrituras y fallo/concurrencia
  sin guardado parcial. Seleccionar las regresiones pertinentes al delta.
- Medir etapas sin alterar el contrato E2E; registrar dataset, semilla, hashes,
  número de muestras y configuración. Comparaciones requieren insumos equivalentes.
- `python tools/docs_check.py`, `python tools/qa.py --check-catalog`,
  `git diff --check` y revisión independiente del delta propio y fronteras.
- MAIN audita en lectura durante el trabajo. Antes de pruebas independientes,
  confirmar relevo de escritor y ausencia de Gradle concurrente.

## DONE WHEN

MAIN recibe una entrega local trazable: causa y corrección comprobables, o un
diagnóstico acotado que explique exactamente qué falta observar físicamente;
regresiones/build pertinentes, fuentes y evidencia preservadas, delta propio
separado y plan siguiente ejecutable. No cerrar como corregida una alerta Android
que todavía no se volvió a medir. Stage 8 queda sujeto a revisión de MAIN;
Stage 7, aceptación de Joaquín y distribución conservan sus puertas.

Escribí el handoff y tu respuesta final en este chat. MAIN recibe el resultado
mediante las herramientas de seguimiento; no hace falta crear un chat ni enviar
un mensaje de retorno por tu cuenta. Cuando MAIN te comunique hallazgos dentro
de este contrato, corregilos y devolvé evidencia nueva sin reabrir otros frentes.
