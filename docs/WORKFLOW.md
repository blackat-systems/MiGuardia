# Método de trabajo e integración

Este método es permanente. El alcance y el estado actuales viven sólo en `docs/STATUS.md`; el método no habilita otro bloque.

## Un ciclo

1. Recuperar AGENTS, STATUS y la ruta de lectura pertinente. Puerta 0: carpeta, rama/HEAD, cambios y recursos compartidos. Registrar la entrada si será costosa de reconstruir.
2. MAIN toma contrato/cursor de STATUS; ORQUESTACION_MAIN es historia 2.0. Fijar objetivo observable, alcance, exclusiones y DONE WHEN verificable; reutilizar candidato/contrato. Una instrucción directa de Joaquin puede ser el contrato; para delegar usar `docs/prompts/TEMPLATE.md`.
3. Implementar el mínimo suficiente. Delegar sólo responsabilidades independientes, con archivos propios y condiciones de integración claras. Un escritor por archivo; una sola ejecución Gradle/dispositivo a la vez.
4. Comprobar por impacto con `docs/VALIDACION.md`. Una revisión independiente de cambios ejecutables busca fallos, no rehace la implementación. Si cambia el objeto auditado, repetir la comprobación afectada.
5. Acusar recepción, auditar y registrar evidencia en STATUS. Avanzar sólo tras aceptar la salida; no reabrir historia ni ampliar el alcance.

## Autorizaciones sin repreguntas

- Agrupar al inicio los permisos previsibles: acción, recurso/datos, consecuencia y fuente; distinguir CONCEDIDO, PENDIENTE y HOLD. STATUS registra su vigencia. Un permiso completo vigente se reutiliza sin repreguntar; un HOLD suspende la parte dependiente, no revoca el alcance ni habilita sustituir disponibilidad por ADB.
- MAIN y el mismo stage resuelven decisiones técnicas rutinarias, reversibles y dentro del alcance. Escalar sólo cambios materiales de alcance/producto, riesgo de datos, costos/publicación o permisos obligatorios; no aceptar controles por Joaquin ni cambiar políticas. El permiso no elimina guardas operativas.
- MAIN puede crear el checkpoint local de un bloque revisado y comprobado; no mezclar cambios preexistentes ajenos o pendientes. Aprobación para checkpoint y commit creado son estados distintos.
- Una nueva tarea de Codex necesita indicación de Joaquin, incluida su autorización explícita de encadenar tareas reales dentro de un alcance aprobado. Subagentes internos acotados no sustituyen a MAIN.
- Push, tag, firma/publicación, producción, recursos remotos y ensayos físicos conservan su autorización específica. Las autorizaciones **de push anteriores** están consumidas; esto no consume los permisos operativos del bloque actual.
- Si una decisión cambia producto, seguridad, costos, datos o compromisos externos sin autorización, presentar la decisión concreta. No convertir una duda resoluble en una nueva puerta de permiso.

## Estado, contratos e historia

| Fuente | Responsabilidad |
|---|---|
| AGENTS | Invariantes y límites estables |
| STATUS | Único estado actual, autorización, Git real y pendientes |
| ORQUESTACION_MAIN | Orden histórico de las 24 etapas 2.0; no sustituye el contrato 3.0 de STATUS |
| PLANIFICACION | Reglas del producto y capacidades futuras claramente separadas |
| MAPA | Resumen para orientarse, sin otro historial operativo |
| ADR e índice | Contratos técnicos vigentes y sus sucesores |
| Prompts | Alcance/aceptación de tareas; estado autorizado sólo en STATUS |
| Auditorías | Evidencia fechada y limitada a base/artefacto/superficie |
| Archive | Fuentes reemplazadas, nunca órdenes actuales |

Al reanudar, leer cambios/contratos pertinentes desde la base conocida; no reconstruir historia inalterada. Un candidato se audita, no se abre otro implementador para rehacerlo.

## Delegación y entrega proporcionales

### Bloques completos y profundidad de trabajo

Instrucción expresa de Joaquin, 2026-09-07: aumentar sustancialmente la capacidad
de resolución de cada dependencia. En este ciclo usa Astra con razonamiento
ultra y espera tareas más completas y complejas, con autonomía para resolver
el conjunto. El modelo elegido no amplía permisos ni autoriza cambios ajenos.

- Delegar una capacidad completa: diagnóstico/diseño acotados, implementación,
  superficies, errores, persistencia/restauración necesarias, regresiones, QA
  autorizada y handoff. No entregar piezas desconectadas.
- Dimensionar por cohesión, dependencias y riesgo; botones, regreso, borradores
  y pruebas del mismo recorrido son hitos internos, no chats sucesivos.
- Una tarea real y verificable por stage: objetivo, ejecución, evidencia y cierre.
  Correcciones de validez pertenecen al mismo bloque; un experimento nuevo o
  cambio de producto posterior necesita otro stage y contrato, no un apéndice indefinido.
- MAIN define contratos, coordina, revisa e integra. Cada especialista separado
  resuelve su frente completo; MAIN no centraliza sus microdecisiones.
- Otro chat requiere responsabilidad independiente y contrato estable. Revisores
  pueden leer en paralelo, sin competir por archivos, Gradle o teléfono. Bifurcar
  no autoriza rama/worktree, otro escritor ni dispositivo.
- Profundizar casos límite y coherencia transversal; no agregar infraestructura,
  abstracciones, documentos o pruebas sin relación con el resultado.
- Corregir defectos dentro del bloque y repetir sólo comprobaciones afectadas,
  preservando las puertas de datos, seguridad, costos y publicación.
- Comunicar hitos/bloqueos verificables; local, físico, integrado y publicado
  son estados distintos. Un límite de autorización no es una aprobación.

Usar los campos de la plantilla existente; una revisión de sólo lectura puede recibir un contrato breve. No crear marcos o documentos paralelos por ritual. Todos los prompts y mensajes entre chats deben estar en español claro.

Entrega uniforme: final propio con delta/base, pruebas reales/resultados/recibos, reutilizadas/no ejecutadas, límites y próximo dueño. Confirmar cese de escrituras y recursos. Con autorización humana de retorno, enviar aviso efectivo a MAIN tras sellar: chat/turno, rutas, hashes y clave stage/fase/sello; no modificar el payload sellado.

Entrega: TERMINADO/PENDIENTE_ENVIO → ENVIADO (aceptación del transporte) → RECIBIDO (acuse durable MAIN con clave/payload) → AUDITADO. Archivo/final o tool aceptada no acreditan recepción. MAIN acusa antes de la revisión extensa; separa recibos de transporte y evidencia inmutable. Recuperación idempotente por clave: consultar recibo/historia antes de reenviar o auditar; resultado incierto exige verificar, no relanzar. No garantiza exactly-once del transporte ni permite evadir una denegación.

Con encadenamiento autorizado: MAIN crea/despacha contrato autosuficiente → especialista ejecuta/entrega → MAIN acusa/audita/corrige → siguiente tarea real ya autorizada, sin copy/paste ni impulso manual rutinario. Falta de alcance, permiso material, datos o costos detiene sólo ese gate; no crea etapas por numeración. Registrar chat/turno/resultado/recibo y separar IMPLEMENTADO, COMPROBADO y PENDIENTE.

Un único supervisor temporal recupera transiciones pendientes: sin envío/acuse,
RECIBIDO sin auditoría (reanudar la revisión vigente) o AUDITADO sin avance.
Tras aceptación y gates resueltos, registrar/despachar una siguiente tarea real
autorizada. Antes de crear/reenviar, consultar registro/historia y reutilizar destino;
guardar clave de siguiente tarea/chat junto a delivery_key cuando exista.
Con gate o sin tarea habilitada, registrar BLOQUEADO/CERRADO y eliminar supervisor,
sin sondeo indefinido ni archivar chats. No otro watch/daemon; fallos al mismo stage.

## Cierre comprobable

Separar implementación, revisión, commit real, pruebas locales, matriz física y distribución. Un cierre de cliente con pendientes aceptados no equivale a aplicación publicable. Registrar esos pendientes con superficie y evidencia, sin reabrir automáticamente todo lo anterior.

Nunca heredar un verde de otra base o superficie. La revisión de extracción mecánica no sustituye una prueba física; puede acreditar ausencia de cambio lógico mediante comparación de cuerpos y compilación, manteniendo el límite explícito.

Dos rondas sobre el mismo defecto sin evidencia nueva exigen diagnosticar la causa y detener la repetición improductiva. No abrir más chats para ocultar un bloqueo.
