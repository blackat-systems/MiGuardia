# Stage 5 — Cierre visual, fluidez e integridad de MiGuardia 3.0

Contrato preparado el 2026-10-02 y ejecutado parcialmente. Joaquín autorizó el
2026-10-03 continuar con zoom tipográfico sólo en el calendario mensual, casillas
adaptadas al espacio y objetivos táctiles de al menos 48 dp de densidad base.
La autorización y el estado actual se consultan en STATUS; la entrega parcial y
sus recibos están en docs/HANDOFF_STAGE_5_A_MAIN_3_0.md.
Stage 5 hereda los pendientes de Stage 4; no los borra ni los declara resueltos.

## TASK

Entregar una candidata integrada de MiGuardia 3.0 corregida, medible y lista para
revisión humana: calendario completo y legible, guía contextual operable,
navegación intuitiva y rendimiento comprobado. Cerrar los defectos reproducidos
y verificar sus consumidores de punta a punta. No agregar funciones nuevas ni
hacer un rediseño general.

## CONTEXT

Repositorio:
`C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`.
Rama esperada: `codex/miguardia-3.0`.
HEAD de referencia: `932eaeb60c6b6e68975534788e3e6501c967b1ae`, con un checkout
acumulado intencionalmente modificado. HEAD solo no representa la candidata.
MiGuardia-2.0 y las recuperaciones son de sólo lectura.

Stage 4 tiene evidencia local de 916 JVM y 26 interoperabilidad verdes.
MAIN comprobó independientemente 81 regresiones JVM y ejecutó 94 métodos físicos:
89 verdes y 5 fallidos. Dos métodos recorrieron 48 combinaciones del calendario;
32 fallaron. No sumar combinaciones y métodos como si fueran pruebas distintas.

Defectos y límites de entrada:

- Calendario: zoom interno 150/200 recorta columnas y vuelve ilegibles fechas
  en paisaje. Zoom 100 pasó geometría, no acredita toda la aceptación visual.
- Ayuda: último paso HELP abre el menú, pero su ancla queda fuera del área
  visible. No alcanza con que el nodo exista.
- Rendimiento: baseline y candidata quedaron incompletos por cambio de foco a
  otro paquete. Causa pendiente; no atribuirla al usuario, teclado o producto
  sin evidencia. No existen percentiles comparables válidos.
- Pendientes humanos: composición, legibilidad, TalkBack, teclado y selector
  OEM de imágenes. Los verdes locales no sustituyen estas observaciones.

## INPUTS

Leer en este orden:

1. AGENTS.md del contenedor y del repositorio.
2. `docs/STATUS.md` y `docs/LECTURAS_POR_TAREA.md`.
3. `docs/WORKFLOW.md` y `docs/VALIDACION.md`.
4. `docs/audits/2026-10-02-stage4-qa-main.md`: evidencia adversa y recibos.
5. `docs/HANDOFF_STAGE_4_A_MAIN_3_0.md`.
6. `docs/audits/2026-10-02-stage4-matriz-recorridos.md` y
   `docs/audits/2026-10-02-stage4-rendimiento.md`.
7. Código, pruebas y contratos sectoriales pertinentes. No reconstruir todo el
   historial ni ejecutar la ruta histórica de 24 etapas de 2.0.

Evidencia bajo `.local-evidence/stage4-main/`; baseline bajo
`.local-evidence/stage4/entry/`. La auditoría identifica APK, runners y datos.
Los cambios MAIN a Stage4FlowsActivityTest, HelpComposeTest y qa-groups.json
son parte de la entrada: fixture QAS válida y diagnóstico reforzado, no producto.

## OUTPUT

- Correcciones completas con causa, regresiones y consumidores revisados.
- Comparación visual antes/después y resultados de rendimiento atribuibles
  al APK final, o bloqueo preciso sin inventar aprobación.
- Matriz de los 33 recorridos reconciliada: verificado, fallido, pendiente o
  fuera de alcance, con evidencia y sin convertir pruebas con dobles en reales.
- QA final instalada y abierta sólo si existe autorización y disponibilidad
  vigentes del Samsung; app pública y datos personales intactos.
- Auditoría Stage 5, handoff autosuficiente a MAIN, STATUS e índice actualizados;
  inventario del delta propio, hashes y límites de aceptación.

## SCOPE

### Entrada y preservación

Ejecutar Puerta 0: rutas, repositorios, rama, HEAD, upstream, remotos, worktrees,
staging y archivos modificados/nuevos. Registrar manifiesto del estado recibido.
Preservar el checkout y los fallos anteriores. Ante una diferencia adicional
sin dueño, describirla y detener escrituras hasta aclararla.

### Calendario legible y accesible

Corregir composición considerando el espacio real disponible. Mantener mes
completo de cuatro/cinco/seis filas, sin scroll vertical principal, semana
forzada, fechas escondidas ni barra permanente de acciones. Conservar toque
breve para detalle, pulsación larga nativa y alternativa accesible para selección;
acciones únicamente contextuales.

Verificar números, siete columnas, encabezados, condiciones y selección:
visibles, distinguibles y operables, no sólo presentes en semántica. Preservar
temas, zoom interno 100/150/200 y densidad estable, sin leer ni modificar ajustes
globales de Android. No reducir silenciosamente objetivos táctiles ni neutralizar
el zoom para pasar pruebas.

Decisión autorizada el 2026-10-03: sólo la superficie mensual usa geometría de
densidad base y escala tipográfica 100/150/200. Mantener siete columnas simultáneas,
áreas táctiles de al menos 48 dp base sin solapamiento y texto realmente ampliado;
retirar el ancho mínimo y scroll horizontal heredados. Reducir información
secundaria visible cuando haga falta, conservándola en detalle y semántica.
Detalle, formularios, menú y demás destinos conservan su zoom geométrico actual.
Actualizar la descripción de Apariencia y el oráculo geométrico con esta unidad
de medida explícita; conservar evidencia adversa y verificar legibilidad visual.
No presentar el cambio de política por sí mismo como resolución de los 32 fallos.

### Ayuda y navegación

Hacer visible el control real de cada paso antes de posicionar la guía. En HELP,
llevar el elemento del menú al área visible sin ejecutar su acción ni mutar datos.
Cubrir avanzar, retroceder, cerrar, recrear, orientación y zoom. No aceptar un
ancla ausente ni inventar otra para evitar el fallo.

Preservar padre y borrador, cierres predecibles, guardado explícito y un único
dueño de Atrás GENERAL/MANUAL_LOAD/HOLIDAY. Revisar etiquetas, jerarquía y
feedback de los recorridos afectados, con estética discreta y sin controles
duplicados. Conservar el formulario Stage 3.2, fechas expandibles, catálogo,
puesto, notas e imágenes al cambiar o vaciar selección.

### Fluidez e integridad

Diagnosticar el cambio de foco con metadatos mínimos y QA aislada. Mantener el
guard: ante foco ajeno inesperado, detener operaciones sin capturar ni leer otra
app. No ampliar permisos, permitir cualquier paquete ni inflar esperas.

Completar el protocolo de rendimiento existente: mismo dispositivo, driver,
condiciones y dataset inicial verificable para baseline/candidata; doce
iteraciones completas por serie representativa y estrés, diferenciadas.
Conservar muestras, fallos y descartes. Informar p50/p95, tamaño de muestra y
umbrales predefinidos; medir arranque frío aparte y distinguir apertura de
contenido listo. FrameMetrics no equivale automáticamente a FPS aceptables.

Optimizar sólo cuellos de botella demostrados. No sacrificar CAS, transacciones,
validación, privacidad ni frescura de datos por bajar una cifra. Revisar
integración de calendario, carga, notas/archivos, situaciones, horas, Resumen,
Copias y navegación según los 33 recorridos y el impacto real.

## DEPENDENCIES

Entrada Stage 4 preservada; activación explícita de este contrato; decisión de
Joaquín si hace falta cambiar la política de presentación/zoom.

QA física requiere autorización aplicable y teléfono libre/desbloqueado.
Estar conectado no basta. Trabajar sólo sobre QA y datos ficticios; no usar la
app pública para comprobar actualización. Compatibilidad desde 2.0.2, Room V6→V8
y Copias V5–V8 debe quedar respaldada por pruebas existentes vigentes y ensayos
sintéticos proporcionales, distinguidos de una actualización pública real.

## DO NOT

- No modificar 2.0, recuperar checkouts, descartar cambios ni crear ramas,
  worktrees, tareas, commits, pushes o tags por inferencia.
- No cambiar versión, applicationId público, firma, Room, esquemas, formato
  de Copias, reglas laborales o dependencias sin autorización material nueva.
- No tocar web, Play Console, firma, AAB, distribución ni publicación.
- No usar datos personales, tráfico externo real, alarmas reales o permisos
  nuevos por inferencia; no limpiar ni desinstalar la app pública.
- No rebajar aserciones, excluir combinaciones difíciles ni eliminar evidencia
  adversa. No atribuir recibos antiguos a un APK nuevo.
- No fragmentar esta unidad en microstages ni reescribir por gusto.

## VALIDATION

1. Reproducir los fallos pertinentes y conservar evidencia antes/después.
2. Repetir la matriz física de 48 combinaciones del calendario sobre la candidata
   final, incluyendo controles táctiles, legibilidad visual y extremos de grilla.
3. Pasar la guía completa y regresiones de navegación, carga, recreación,
   selección y cancelación sin escrituras accidentales.
4. Ejecutar comparativa de rendimiento completa con identidades y condiciones
   registradas; investigar umbrales excedidos antes de declarar fluidez.
5. Verificar integridad afectada: notas/adjuntos, restore, lotes grandes, CAS,
   rollback y confirmación perdida; reutilizar evidencia sólo si aplica a los
   mismos insumos, sin repetir todo por rutina.
6. Revisar matriz humana y explicar exactamente qué quedó observado o pendiente.
7. Tras estabilizar código, ejecutar perfiles locales/build pertinentes, lint,
   runners, catálogo QA, `python tools/docs_check.py` y `git diff --check`.
   Revisar diff propio, no rastreados, hashes y ausencia de secretos/artefactos.
8. Si se instala, verificar identidad QA local/instalada y preservación de la
   app pública; dejar QA abierta únicamente con el teléfono disponible.

## DONE WHEN

Los defectos de calendario y Ayuda están resueltos con evidencia causal; el
rendimiento tiene una conclusión válida; las interacciones y datos afectados
están verificados; documentación y candidata coinciden; MAIN recibe una unidad
revisable y Joaquín puede evaluar la QA autorizada.

Si falta una decisión, recurso o verificación indispensable, entregar cierre
parcial con bloqueo explícito, no “terminado”. La revisión humana y aceptación de
Joaquín son puertas separadas; ninguna autoriza firma o distribución.

