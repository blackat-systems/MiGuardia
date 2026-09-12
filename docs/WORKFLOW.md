# Método de trabajo e integración

Este método es permanente. El alcance y el estado actuales viven sólo en `docs/STATUS.md`; el método no habilita otro bloque.

## Un ciclo

1. Recuperar AGENTS, STATUS y la ruta de lectura pertinente. Puerta 0: carpeta, rama/HEAD, cambios y recursos compartidos. Registrar la entrada si será costosa de reconstruir.
2. MAIN toma la siguiente etapa de STATUS y respeta `docs/ORQUESTACION_MAIN.md`. Estabilizar objetivo, contratos, límites y evidencia de aceptación; reutilizar candidato/contrato existente. Una instrucción directa y concreta de Joaquin puede ser el contrato; para una dependencia relevante usar `docs/prompts/TEMPLATE.md`.
3. Implementar el mínimo suficiente. Delegar sólo responsabilidades independientes, con archivos propios y condiciones de integración claras. Un escritor por archivo; una sola ejecución Gradle/dispositivo a la vez.
4. Comprobar por impacto con `docs/VALIDACION.md`. Una revisión independiente de cambios ejecutables busca fallos, no rehace la implementación. Si cambia el objeto auditado, repetir la comprobación afectada.
5. Actualizar STATUS, su cursor del mapa y evidencia fechada sólo tras aceptar la salida. Continuar dentro del paquete ya autorizado según la ruta; no iniciar un alcance nuevo automáticamente ni reabrir etapas cerradas por leerlas en el flowchart.

## Autorizaciones sin repreguntas

- La autorización de un bloque cubre inspección, cambios locales necesarios, pruebas seguras, correcciones y documentación relacionadas. No pedir otra autorización para cada acción reversible ya incluida.
- MAIN puede crear el checkpoint local de un bloque revisado y comprobado; no mezclar cambios preexistentes ajenos o pendientes. Aprobación para checkpoint y commit creado son estados distintos.
- Una nueva tarea de Codex necesita indicación de Joaquin. Subagentes internos acotados pueden colaborar dentro del alcance autorizado; no sustituyen a MAIN.
- Push, tag, firma/publicación, producción, recursos remotos y ensayos físicos conservan su autorización específica. Las autorizaciones **de push anteriores** están consumidas; esto no consume los permisos operativos del bloque actual.
- Si una decisión cambia producto, seguridad, costos, datos o compromisos externos sin autorización, presentar la decisión concreta. No convertir una duda resoluble en una nueva puerta de permiso.

## Estado, contratos e historia

| Fuente | Responsabilidad |
|---|---|
| AGENTS | Invariantes y límites estables |
| STATUS | Único estado actual, autorización, Git real y pendientes |
| ORQUESTACION_MAIN | Orden obligatorio, dependencias y aceptación de las 24 etapas; gráfico derivado de STATUS |
| PLANIFICACION | Reglas del producto y capacidades futuras claramente separadas |
| MAPA | Resumen para orientarse, sin otro historial operativo |
| ADR e índice | Contratos técnicos vigentes y sus sucesores |
| Prompts | Alcance/aceptación de tareas; estado autorizado sólo en STATUS |
| Auditorías | Evidencia fechada y limitada a base/artefacto/superficie |
| Archive | Fuentes reemplazadas, nunca órdenes actuales |

Al reanudar, comparar el estado y los archivos desde la última base conocida; leer sólo el cambio pertinente, salvo contexto faltante. No reconstruir el historial de Git, permisos consumidos o pruebas antiguas si ya está registrado y no cambió. Un candidato presente se audita; no se crea otro implementador para empezar de cero.

## Delegación y entrega proporcionales

### Bloques completos y profundidad de trabajo

Instrucción expresa de Joaquin, 2026-09-07: aumentar sustancialmente la capacidad
de resolución de cada dependencia. En este ciclo usa Astra con razonamiento
ultra y espera tareas más completas y complejas, con autonomía para resolver
el conjunto. El modelo elegido no amplía permisos ni autoriza cambios ajenos.

- La unidad de delegación es una capacidad utilizable de punta a punta. Una
  misma dependencia reúne diagnóstico, diseño técnico dentro del contrato,
  implementación, integración de sus superficies, estados y errores,
  persistencia/restauración que necesite, regresiones, QA autorizada y handoff.
- Dimensionar el bloque por cohesión, dependencias y riesgos reales, no por la
  cantidad de archivos ni por dividir cada operación en un prompt. Botones,
  regreso, borradores y pruebas de un mismo recorrido son hitos internos del
  mismo encargo, no cuatro chats sucesivos.
- Mantener la fragmentación de carga mediante chats especializados separados:
  MAIN define entradas, salidas y dependencias como contratos entre funciones;
  cada especialista resuelve su frente completo y devuelve evidencia. MAIN
  conserva la visión global, revisa e integra; no ejecuta por defecto cada
  microdecisión que el especialista puede resolver dentro de su alcance.
- Antes de abrir otro chat, justificar su responsabilidad independiente y el
  contrato estable que consume o produce. Los revisores internos pueden
  contrastar riesgos en paralelo sin competir por archivos, Gradle o teléfono.
  Una bifurcación de conversación no autoriza por sí sola otra rama Git,
  worktree, escritor o dispositivo.
- Pedir profundidad en casos límite, coherencia transversal y verificación del
  recorrido completo. La complejidad técnica se justifica por esos problemas;
  no por agregar infraestructura, abstracciones, documentación o pruebas sin
  relación con el resultado solicitado.
- El especialista corrige los defectos reproducibles de su bloque y repite
  únicamente las comprobaciones afectadas antes de entregar. No devolver
  anticipadamente piezas desconectadas ni detenerse en cada decisión reversible.
  Mantener las puertas materiales de datos, seguridad, costos y publicación.
- Comunicar hitos verificables y bloqueos concretos. «Local verde», «QA física»,
  «integrado» y «publicado» siguen siendo resultados diferentes. Un límite de
  autorización se informa; no se oculta ni se convierte en aprobación.

Antes de delegar: tarea, entradas, archivos propios, contratos compartidos, prohibiciones, validación y criterio de terminado. Una aclaración breve puede bastar para una revisión de sólo lectura; no crear un documento extenso por ritual.

Handoff: qué cambió y por qué; rutas relevantes; base/HEAD y cambios locales; validación con alcance/resultados; riesgos y próximo paso. Si hay evidencia larga, enlazarla. Evitar repetir reglas y contratos que ya tienen dueño.

## Cierre comprobable

Separar implementación, revisión, commit real, pruebas locales, matriz física y distribución. Un cierre de cliente con pendientes aceptados no equivale a aplicación publicable. Registrar esos pendientes con superficie y evidencia, sin reabrir automáticamente todo lo anterior.

Nunca heredar un verde de otra base o superficie. La revisión de extracción mecánica no sustituye una prueba física; puede acreditar ausencia de cambio lógico mediante comparación de cuerpos y compilación, manteniendo el límite explícito.

Dos rondas sobre el mismo defecto sin evidencia nueva exigen diagnosticar la causa y detener la repetición improductiva. No abrir más chats para ocultar un bloqueo.
