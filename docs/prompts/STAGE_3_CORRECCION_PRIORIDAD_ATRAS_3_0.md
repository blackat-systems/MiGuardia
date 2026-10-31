# Stage 3 — Corrección de prioridad de Atrás en selección

Fecha: 2026-09-15  
Estado: `AUTORIZADO`; corrección obligatoria previa a la QA física de Stage 3.

## PERFIL DE EJECUCIÓN

- Modelo solicitado: `GPT-5.6 Sol`, razonamiento `Medium`.
- Resolver como una corrección cohesiva dentro de Stage 3, no como Stage 4 ni
  como una cadena de microtareas.
- Empezar por Puerta 0 y preservar íntegramente el checkout sucio recibido.
- Un solo escritor sobre `MiGuardiaApp.kt` y las pruebas de navegación afectadas.

## TASK

Corregir la competencia entre los manejadores de Atrás del Calendario para que
cada propósito de selección tenga un único dueño efectivo. En particular,
Atrás debe cancelar correctamente los recorridos de selección de carga manual y
feriados, sin que el manejador general cierre sólo el estado visual y el efecto
de sincronización vuelva a abrirlo.

Agregar regresiones que demuestren la prioridad y la salida correcta en los tres
propósitos: selección general, carga manual y feriados. Volver a verificar el
candidato local de Stage 3 y entregar un handoff actualizado. No ejecutar QA
física en este encargo.

## CONTEXT

Stage 3 quedó implementado con selección múltiple, calendario plegable, barra
inferior fija y operaciones masivas atómicas. La revisión independiente de MAIN
encontró un bloqueo antes de la QA física:

- `MiGuardiaApp.kt` declara un `BackHandler` específico para carga manual.
- Después declara otro específico para feriados.
- Más abajo declara un `BackHandler` general habilitado para cualquier estado
  `EDIT`, sin limitarlo al propósito `GENERAL`.
- En carga manual o feriados pueden quedar habilitados simultáneamente el
  manejador específico y el general. La prioridad de composición puede entregar
  Atrás al general.
- El general finaliza o reanuda únicamente la selección del Calendario, pero no
  cancela el flujo externo que mantiene activo el propósito.
- El `LaunchedEffect` de sincronización observa ese flujo externo y puede volver
  a entrar en selección o restaurar su propósito. Para el usuario, Atrás puede
  parecer ineficaz o producir una transición incorrecta.

Ubicaciones verificadas antes de despachar:

- Sincronización de propósitos externos: `MiGuardiaApp.kt`, líneas 831–869.
- Atrás de carga manual: líneas 1183–1200.
- Atrás de feriados: líneas 1201–1204.
- Atrás general de selección: líneas 1215–1227.

No hay una regresión que pulse Atrás durante esos dos recorridos integrados. Las
pruebas actuales de estado verifican valores como `MANUAL_LOAD`, pero no la
arbitrariedad real entre los manejadores Compose.

## INPUTS

- Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio: `MiGuardia-3.0/`.
- Rama esperada: `codex/miguardia-3.0`.
- HEAD base del checkout sucio: `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Leer, en este orden:
  1. `MiGuardiaContainer/AGENTS.md`.
  2. `MiGuardia-3.0/AGENTS.md`.
  3. `docs/STATUS.md`.
  4. Este contrato.
  5. `docs/prompts/STAGE_3_SELECCION_MULTIPLE_Y_CALENDARIO_PLEGABLE_3_0.md`.
  6. `docs/HANDOFF_STAGE_3_A_MAIN_3_0.md`.
  7. `docs/audits/2026-09-15-stage-3-seleccion-calendario-plegable.md`.
- Código principal: `MiGuardiaApp.kt`, `CalendarUiState.kt`,
  `CalendarViewModel.kt` y las pruebas Compose/Activity del Calendario, carga
  manual y excepciones.
- Evidencia local previa:
  `.local-evidence/optimization/20260915T185214.799977Z/result.json` y
  `.local-evidence/optimization/20260915T185314.327483Z/result.json`.

## OUTPUT

- Un único criterio explícito de prioridad de Atrás para el Calendario.
- Manejador general habilitado solamente cuando el propósito sea realmente
  `GENERAL`, o una solución equivalente más clara que garantice exclusividad.
- Carga manual:
  - en selección inicial, Atrás cancela el flujo y sale del modo selección;
  - después de confirmar fechas, Atrás vuelve a elegir fechas y conserva las que
    correspondan según el contrato vigente;
  - estados de carga, guardado o solicitud en curso siguen protegidos.
- Feriados: Atrás cancela el flujo de feriados y no vuelve a abrir la selección.
- Selección general: Atrás conserva la prioridad prevista —primero diálogo,
  después selección y finalmente pantalla— sin cambiar el comportamiento
  aceptado de Stage 3.
- Regresiones focalizadas para los tres propósitos y para la transición que antes
  permitía la reactivación automática.
- Handoff y auditoría de Stage 3 corregidos con evidencia nueva, incluyendo el
  fallo encontrado y la solución aplicada; no borrar ni maquillar la evidencia
  anterior.

## SCOPE

- Arbitraje de navegación Atrás en `MiGuardiaApp.kt`.
- Estado auxiliar mínimo sólo si hace falta volver explícita y testeable la
  prioridad.
- Pruebas JVM/Compose/Activity directamente relacionadas.
- Documentación Stage 3, catálogo QA y `docs/STATUS.md` para registrar el
  resultado real.

## DEPENDENCIES

- Candidato local de Stage 3 preservado en el checkout actual.
- Contrato original de Stage 3 vigente en todo lo que este documento no corrige.
- No depende del Samsung, ADB, firma, website ni servicios remotos.

## DO NOT

- No ocultar el problema deshabilitando Atrás globalmente.
- No eliminar el comportamiento específico de carga manual o feriados.
- No introducir otro booleano ambiguo ni otro manejador que pueda competir.
- No cambiar la selección múltiple, el calendario plegable, las operaciones
  masivas, Room, Copias o la UX fuera de lo indispensable para esta corrección.
- No reinterpretar Atrás como confirmación ni permitir escrituras al cancelar.
- No borrar ni regenerar el checkout, ni descartar cambios de Stage 1/2/3.
- No cambiar versión, `applicationId`, firma, distribución, website ni app
  pública.
- No usar Samsung, ADB, emulador, dispositivo, commit, push, tag, firma, AAB ni
  publicación.

## VALIDATION

- Verificar mediante prueba el Atrás real o el arbitraje extraído para:
  `GENERAL`, `MANUAL_LOAD` y `HOLIDAY`.
- Demostrar que nunca hay dos consumidores efectivos de Atrás para una misma
  selección.
- Demostrar que cancelar carga manual deja `v2ManualLoadActive` inactivo y el
  Calendario en `VIEW`, sin reentrada posterior.
- Demostrar que cancelar feriados deja `holidaySelectionActive` inactivo y el
  Calendario en `VIEW`, sin restauración posterior.
- Demostrar la vuelta desde la etapa posterior de carga manual a selección de
  fechas sin perder el contrato vigente.
- Demostrar que la selección general conserva diálogo → cancelación → salida.
- Ejecutar primero pruebas focalizadas. Si pasan, ejecutar una sola vez los
  perfiles locales completos requeridos por `docs/VALIDACION.md`, con inputs
  estables.
- Compilar las pruebas instrumentadas que ejecutarán el recorrido en la futura
  QA física; no instalarlas ni correrlas aquí.
- Ejecutar `python tools/docs_check.py`, `python tools/qa.py --check-catalog` y
  `git diff --check`.
- Revisar el diff completo y confirmar que sólo contiene el candidato acumulado
  Stage 1/2/3 y esta corrección, sin archivos ajenos ni secretos.

## DONE WHEN

- Atrás tiene un solo dueño efectivo en cada propósito de selección.
- Carga manual y feriados se cancelan y permanecen cerrados.
- La selección general conserva su comportamiento previsto.
- Las regresiones focalizadas fallarían con el código recibido y pasan con la
  corrección.
- La validación local y documental queda verde con recibos nuevos.
- El handoff reconoce el hallazgo adverso y deja preparada —pero no ejecutada—
  la matriz física específica de Stage 3.
- MAIN puede decidir nuevamente si el candidato está listo para solicitar la QA
  física, sin aceptar, integrar ni distribuir por inferencia.

