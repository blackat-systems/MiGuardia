# Stage 3 — Cierre de recreación y evidencia física

Fecha: 2026-09-16  
Estado: `AUTORIZADO`; corrección obligatoria previa a la matriz humana y a la
aceptación de Stage 3.

## PERFIL DE EJECUCIÓN

- Modelo previsto: `GPT-5.6 Sol`, razonamiento `Medium`.
- Resolver como una corrección final y cohesiva de Stage 3, no como Stage 4.
- Empezar por Puerta 0 y preservar íntegramente el checkout sucio recibido.
- Investigar primero; no cambiar producto para silenciar una aserción defectuosa.
- El Samsung sólo puede utilizarse si el task de ejecución recibe autorización
  física específica vigente de Joaquín. Este contrato no la infiere.

## TASK

Determinar y corregir la causa por la que
`V2ManualShiftLoadActivityTest.draftSurvivesRecreationAndShiftSnapshotSurviveReopen`
termina en 5/6 bajo el runner auditable, aunque una ejecución ADB directa haya
informado 6/6. El cierre debe distinguir entre defecto de producto, carrera de
restauración, supuesto incorrecto del test o diferencia del runner.

Después de la corrección, producir evidencia durable mediante el runner oficial,
corregir las inconsistencias documentales detectadas por MAIN y devolver Stage 3
para una nueva revisión. No ejecutar todavía la matriz humana completa.

## CONTEXT

La ampliación de Stage 3 implementó detalle por tarjetas de jornada, clima
contextual, Calendario principal sin scroll, barra fija, selección por pulsación
larga nativa y arbitraje exclusivo de Atrás.

La QA Samsung detectó tres defectos reales que ya fueron corregidos: espacio
innecesario en semana restringida, tags de días perdidos por semántica y wizard
manual recortado debajo de la grilla. Los conjuntos Adaptive, Common, Calendar,
Exceptions y Weather tienen recibos verdes.

Queda una evidencia adversa no cerrada:

- El runner oficial dejó el mismo método en fallo en cuatro corridas finales
  consecutivas:
  - `.local-evidence/qa/20260916T030632.199298Z`;
  - `.local-evidence/qa/20260916T030811.452162Z`;
  - `.local-evidence/qa/20260916T031003.821957Z`;
  - `.local-evidence/qa/20260916T031103.340292Z`.
- En las cuatro, los otros cinco métodos pasaron, incluidos los dos recorridos
  críticos de Atrás.
- Una ejecución ADB directa informó 6/6, pero no dejó un recibo equivalente. No
  puede reemplazar los cuatro resultados auditables ni justificar la palabra
  `intermitencia` por sí sola.
- El recibo guarda veredicto y hashes, pero no el texto completo de la aserción.
  La siguiente reproducción debe preservar suficiente salida para ubicar la
  línea y el estado observado.

MAIN confirmó además dos errores documentales:

- Weather pasó 12/12 en
  `.local-evidence/qa/20260916T031132.350479Z`, no 9/9.
- La interacción aceptada es `pulsación larga nativa`; no debe describirse como
  una espera obligatoria de dos o tres segundos.

Riesgo visual todavía pendiente, no autorizado para cambiar por intuición:
`calendarRequiresRestrictedWeek` fuerza semana cuando el viewport es menor a
1050 dp. Eso puede abarcar la mayoría de los teléfonos. Debe quedar destacado
para aceptación visual, no ajustarse sin medición y evidencia de layout.

## INPUTS

- Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio: `MiGuardia-3.0/`.
- Rama esperada: `codex/miguardia-3.0`.
- HEAD base del checkout acumulado:
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Leer, en este orden:
  1. `MiGuardiaContainer/AGENTS.md`.
  2. `MiGuardia-3.0/AGENTS.md`.
  3. `docs/STATUS.md`.
  4. Este contrato.
  5. `docs/prompts/STAGE_3_AMPLIACION_DETALLE_JORNADAS_Y_CALENDARIO_ESTATICO_3_0.md`.
  6. `docs/HANDOFF_STAGE_3_A_MAIN_3_0.md`.
  7. `docs/audits/2026-09-15-stage-3-seleccion-calendario-plegable.md`.
  8. `docs/VALIDACION.md` y la ayuda vigente de `tools/qa.py`.
- Código principal bajo investigación:
  `V2ManualShiftLoadActivityTest.kt`, estado guardado de carga manual,
  `CalendarScreen.kt`, `MiGuardiaApp.kt` y coordinador/ViewModel correspondiente.
- Recibos locales finales vigentes:
  `.local-evidence/optimization/20260916T031219.234739Z/result.json` y
  `.local-evidence/optimization/20260916T031337.785714Z/result.json`.

## OUTPUT

- Reproducción auditable que conserve método, punto de fallo, estado esperado y
  observado, orientación/recreación y salida suficiente para diagnóstico.
- Clasificación explícita de la causa como producto/restauración,
  prueba/espera/aserción, runner/aislamiento/fixture o una combinación demostrada.
- Corrección mínima en la capa propietaria de la causa.
- Regresión que preserve la intención real: borrador, plantilla seleccionada,
  posición ingresada, fechas elegidas y ausencia de escrituras fantasma deben
  sobrevivir recreación y rotación antes de guardar.
- Evidencia final mediante el runner oficial. La ejecución ADB directa puede
  servir para diagnóstico, pero no es el criterio de cierre.
- Handoff, auditoría y STATUS corregidos para reflejar Weather 12/12, pulsación
  larga nativa, evidencia adversa histórica, causa y resultado final de
  recreación y el riesgo visual de 1050 dp pendiente de aceptación humana.

## SCOPE

- Flujo de carga manual afectado por recreación/orientación.
- Test Activity, fixture, esperas y runner sólo si son la causa demostrada.
- Estado guardado/coordinador/ViewModel/UI sólo si el defecto pertenece al
  producto.
- Documentación y catálogo QA directamente relacionados.

## DEPENDENCIES

- Candidato Stage 3 ampliado y checkout acumulado preservado.
- Recibos físicos adversos y verdes ya existentes.
- Para volver a ejecutar instrumentación: Samsung conectado/autorizado y permiso
  específico vigente en el task de ejecución.

## DO NOT

- No llamar `intermitencia` al fallo sin explicar por qué cuatro recibos
  consecutivos fallaron y qué cambió para estabilizarlo.
- No aumentar tiempos de espera, repetir en bucle o relajar aserciones hasta
  fabricar un verde.
- No eliminar recreación, rotación, texto restaurado ni comprobaciones de ausencia
  de escrituras del escenario.
- No aceptar una ejecución directa sin recibo como sustituto del runner.
- No cambiar el umbral de 1050 dp sin medición física y decisión de producto.
- No repetir por rutina todos los conjuntos físicos ya verdes; sólo los
  consumidores que una corrección real afecte.
- No reabrir el diseño de tarjetas, clima, selección, operaciones masivas o
  prioridad de Atrás salvo causa demostrada.
- No modificar Room, Copias, versión, `applicationId`, firma, distribución,
  website ni app pública.
- No hacer commit, push, tag, firma, AAB ni publicación.
- No usar Samsung, ADB ni instrumentación sin autorización física específica
  vigente.

## VALIDATION

- Reproducir primero el método aislado con el mismo runner y fixture que falló;
  conservar salida diagnóstica.
- Demostrar que la corrección falla con la causa anterior y pasa después, sin
  borrar los recibos adversos.
- Obtener dos ejecuciones finales consecutivas 6/6 de
  `V2ManualShiftLoadActivityTest` mediante el runner oficial, cada una con recibo,
  hashes de APK coincidentes y cero métodos pendientes. Dos verdes no sustituyen
  el diagnóstico: ambos son requeridos.
- Si cambia producto, repetir pruebas JVM focalizadas, consumidores Android y los
  perfiles `local` y `build` completos con inputs estables.
- Si cambia sólo test/fixture, repetir compilación del runner, focalizadas
  relacionadas y el build proporcional que renueve hashes; justificar por qué no
  hace falta otra batería de producto.
- Confirmar que ambos recorridos críticos de Atrás siguen pasando y que cancelar
  no escribe jornadas.
- Confirmar Weather 12/12 en el recibo ya existente; no repetirlo sólo para
  corregir el texto.
- Ejecutar `python tools/docs_check.py`, `python tools/qa.py --check-catalog` y
  `git diff --check`.
- Revisar el diff acumulado y confirmar que no aparecieron secretos, APK
  rastreadas ni archivos ajenos.

## DONE WHEN

- La causa del 5/6 está demostrada y corregida en su capa real.
- Existen dos recibos consecutivos del runner con 6/6, no sólo una salida ADB.
- Borrador, selección y ausencia de escrituras sobreviven recreación y rotación.
- Weather figura como 12/12 y la interacción como pulsación larga nativa.
- El umbral de semana restringida queda explícitamente pendiente de decisión
  visual, sin ser alterado por inferencia.
- MAIN recibe evidencia, diff, límites y matriz humana restante; Stage 3 sigue sin
  aceptación, firma ni distribución hasta revisión posterior.

