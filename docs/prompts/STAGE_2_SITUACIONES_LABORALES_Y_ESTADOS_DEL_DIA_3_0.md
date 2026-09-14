# Stage 2 — Situaciones laborales y estados del día

Estado contractual: **PROPUESTO / LISTO PARA DECIDIR / NO AUTORIZADO PARA IMPLEMENTAR**.

- Modelo previsto: `gpt-5.6-sol`.
- Razonamiento previsto: `medium`.
- Rol previsto: especialista de implementación bajo coordinación de MAIN.

## QUÉ HACE

Convierte las situaciones laborales que MiGuardia ya sabe representar en
recorridos completos, claros y reversibles desde el Calendario y el detalle del
día:

- día explícitamente sin definir (`?`);
- carpeta médica;
- ausencia de una jornada;
- cancelación de una jornada;
- retorno de una jornada ausente o cancelada a su estado planificado.

Debe unir interacción, dominio, persistencia, motor de horas, próximo evento,
avisos, clima, Resumen, informes y Copias. No debe reimplementar Franco ni Retén,
que quedaron resueltos en Stage 1, pero sí debe convivir correctamente con
ellos.

## POR QUÉ EXISTE

La base 2.0.2 contiene `UNDEFINED`, `ABSENT`, `CANCELLED` y carpeta médica en
modelos, Room y proyecciones. Calendario, Resumen e informes pueden leer gran
parte de esos estados, pero la aplicación pública no ofrece recorridos
coherentes para crearlos, corregirlos y retirarlos.

La edición estructural V2 conserva deliberadamente el estado de una jornada.
No corresponde debilitar esa protección para reutilizar una pantalla: Stage 2
debe introducir una operación específica para situaciones laborales, con
comparación optimista, confirmación y efectos derivados atómicos.

## TASK

Implementar de punta a punta las situaciones laborales y los estados del día
definidos en este contrato.

Antes de modificar:

1. Ejecutar la Puerta 0 y confirmar la base integrada de Stage 1.
2. Leer las fuentes indicadas y mapear productores y consumidores.
3. Escribir una matriz de estados, precedencias y transiciones.
4. Confirmar si el contrato puede cumplirse con Room V7. Si falta persistencia
   estructurada para ausencia/cancelación, diseñar la migración mínima V7→V8.
5. Implementar una única capacidad cohesiva, no microtareas por pantalla.
6. Verificar dominio, Room, Copias, app y recorridos Android afectados.
7. Revisar el diff completo y entregar un handoff a MAIN.

No detenerse por decisiones visuales menores, reversibles y compatibles con el
lenguaje VIGILIA. Detenerse ante una contradicción material que obligue a perder
datos, cambiar privacidad, redefinir remuneración o alterar compatibilidad.

## CONTEXT

### Repositorio

`C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`

La ejecución debe usar la rama `codex/miguardia-3.0` y el checkpoint exacto que
MAIN entregue después de cerrar Stage 1. No usar el hash anterior
`cb83d855...` como base de implementación si Stage 1 ya fue integrado.

Si aparece una diferencia sin dueño, preservarla, describirla y detener la
escritura. No limpiar ni reconstruir el checkout.

### Base funcional recibida

- Android local-first, Kotlin y Compose.
- `applicationId = com.blackatsystems.miguardia`.
- Stage 1 deja Room V7, 28 tablas y siete esquemas.
- Copias V7 sigue leyendo V5 y V6.
- Stage 1 deja operativo el detalle, `Jornadas del día`, agregado, edición,
  eliminación, Franco, Retén, horario real y Notas con imágenes privadas.
- `ShiftStatus` ya contiene `PLANNED`, `CANCELLED` y `ABSENT`.
- `ExplicitDayStatusType` ya contiene `DAY_OFF` y `UNDEFINED`.
- `MedicalLeave` ya contiene rango inclusivo y nota privada.
- Calendario, Resumen e informes ya proyectan parte de estas situaciones.
- La edición V2 exige conservar el estado de la jornada: esa invariante no debe
  relajarse para cambios estructurales ordinarios.

### Contrato de producto

#### 1. Día sin definir

- Un día vacío continúa siendo implícitamente sin definir.
- La marca `?` significa que el usuario decidió registrar explícitamente que
  todavía no conoce la situación de esa fecha.
- Debe poder marcarse y quitarse desde `Jornadas del día` o el detalle vacío.
- No debe inferirse por ausencia de jornadas ni convertirse en disponibilidad.
- Guardar una jornada en esa fecha elimina `?` dentro de la misma transacción,
  como ya establece el contrato V2.
- Marcar `?` nunca puede borrar una jornada, un Franco, una carpeta médica,
  vacaciones, Retén ni otros datos. Si existe una jornada o Franco, la interfaz
  debe explicar el conflicto y no ejecutar una mutación destructiva.
- Los `?` históricos ya persistidos deben seguir legibles y removibles.

#### 2. Carpeta médica

- Se crea como rango inclusivo con fecha inicial, fecha final y nota privada
  opcional.
- Debe poder abrirse desde cualquier día alcanzado, mostrar el rango completo,
  editarse y eliminarse con confirmación.
- Puede atravesar meses y años.
- No puede intersectar vacaciones. La validación debe existir en dominio/UI y
  volver a comprobarse dentro de la transacción Room.
- Las carpetas médicas existentes pueden superponerse; no fusionarlas, cortarlas
  ni eliminarlas por inferencia.
- Las jornadas incluidas se conservan, dejan de estar pendientes y no generan
  trabajo, cumplimiento, avisos laborales ni clima operativo.
- Informa días, no horas ni dinero.
- La nota es privada. Nunca admitir foto del certificado ni presentar la app
  como archivo clínico o certificación laboral.

#### 3. Ausencia

- Se aplica a una jornada concreta, no al día entero.
- Conserva UUID, horario estipulado, instantáneas laborales, fecha de creación y
  vínculo histórico de la jornada.
- No suma tiempo trabajado ni cumplimiento. Conserva la duración planificada en
  su categoría de ausencia, según el motor vigente.
- Admite motivo opcional y nota privada opcional.
- Debe poder corregirse a cancelación o volver a `PLANNED`.
- Si la jornada tiene horario real o extras vinculados, la confirmación debe
  explicar que son incompatibles. Su retiro sólo puede ocurrir dentro de la
  misma mutación y con consentimiento explícito; nunca de forma silenciosa.

#### 4. Cancelación

- Se aplica a una jornada concreta y conserva el plan original.
- No suma trabajo ni cumplimiento; conserva su duración en la categoría
  cancelada del motor de horas.
- Exige:
  - motivo;
  - quién canceló, con la opción explícita `No informado`;
  - fecha y hora del aviso.
- Admite nota privada opcional.
- Admite una única imagen opcional de evidencia, elegida mediante Photo Picker,
  copiada al almacenamiento privado y tratada como registro personal. No es una
  certificación legal.
- Debe poder corregirse a ausencia o volver a `PLANNED`.
- La evidencia se elimina sólo al retirar o reemplazar la cancelación mediante
  una confirmación explícita y una operación recuperable frente a fallos.

#### 5. Vuelta a planificada

- Restaurar `PLANNED` elimina la situación controladora y recupera la proyección
  temporal por reloj: próxima, en curso o completada.
- Antes de confirmar, la interfaz debe enumerar qué metadatos privados o
  evidencia se retirarán.
- La operación conserva íntegramente la jornada y no inventa horario real.
- Si la jornada pertenece a un plan recurrente, sólo personaliza esa ocurrencia;
  no cambia retrospectiva ni prospectivamente toda la serie.

### Matriz normativa mínima

| Fuente | Estado visible | Efecto principal |
|---|---|---|
| `PLANNED` + reloj | Próxima, en curso o completada | Motor temporal vigente |
| `ABSENT` | Ausencia | Planificada sí; trabajada y pendiente no |
| `CANCELLED` | Cancelada | Planificada sí; trabajada y pendiente no |
| Carpeta médica | `CM` / Carpeta médica | Días informados; jornadas conservadas y no pendientes |
| `DAY_OFF` | `F` / Franco | Fecha civil explícita; sin inferencia por vacío |
| `UNDEFINED` | `?` / Sin definir | Decisión explícita, distinta del vacío implícito |
| Retén | Ventana de Retén | Horas separadas; nunca jornada trabajada |

Precedencia por jornada: `ABSENT` o `CANCELLED` prevalece sobre carpeta médica,
vacaciones y proyección temporal. Carpeta médica y vacaciones no pueden
coexistir entre sí. Las fuentes coincidentes que el contrato permite se
preservan, aunque una tenga prioridad visual.

### Interacción y presentación

- Las acciones viven en el contexto de la jornada o fecha que modifican.
- `Ausencia` y `Cancelación` deben aparecer dentro de la tarjeta de cada jornada
  en `Jornadas del día`; nunca como acción ambigua del día completo.
- `Sin definir` y `Carpeta médica` son acciones del día/rango.
- Usar títulos directos, ayuda breve y confirmaciones que describan efectos.
- Diferenciar acciones destructivas con jerarquía, no con grandes bloques de
  color. El violeta sigue siendo acento; rojo se reserva para riesgo o retiro.
- Mostrar primero el estado actual y luego las acciones válidas.
- No exponer UUID, hexadecimal, zona horaria, nombres de tablas ni códigos de
  enum.
- Con 150% y 200% de zoom interno, tema claro/oscuro y tamaño de fuente grande,
  todo control debe seguir alcanzable, legible y sin texto cortado.
- Semántica accesible: nombre de situación, jornada afectada, intervalo/rango,
  estado actual y consecuencia de cada acción. No depender sólo del color.

### Persistencia y concurrencia

- No usar la edición estructural ordinaria como atajo para cambiar
  `ShiftStatus`.
- Crear una operación de dominio/repositorio específica que compare la jornada,
  situación y horario real observados antes de escribir.
- La mutación de estado, metadatos controladores, horario real/extras
  incompatibles y vínculo de ocurrencia recurrente debe ser atómica.
- Una escritura concurrente debe fallar con mensaje recuperable y obligar a
  revisar; nunca gana silenciosamente la última pantalla.
- Si se requiere Room V8, agregar sólo las estructuras mínimas para la situación
  controladora y la evidencia de cancelación. No duplicar la jornada ni crear
  una tabla genérica de eventos sin consumidores reales.
- Conservar todos los esquemas publicados y agregar `7→8` no destructiva con
  prueba de migración real.
- Archivos privados: claves opacas, nombre no sensible, límites de tamaño y tipo,
  huella, escritura atómica, limpieza de borradores/huérfanos y nunca rutas
  externas persistidas como fuente de verdad.

### Consumidores obligatorios

Una transición debe reflejarse sin reiniciar la app en:

- Calendario y detalle del día;
- tarjeta de hoy/próximo evento;
- motor de horas y extras;
- Resumen y desglose;
- alarmas/avisos locales;
- elegibilidad de clima;
- planes recurrentes y protección de ocurrencias;
- informes PDF/XLSX;
- Copias, comparación, preview, merge y restauración.

Las notas privadas y la evidencia sólo se exportan cuando el contrato de
privacidad correspondiente está activado y confirmado. Los marcadores del
Calendario nunca revelan motivos, notas ni quién canceló.

### Caso nocturno obligatorio

Una jornada iniciada el día A y terminada a las 07:00 del día B pertenece al
día A. Marcar Franco, `?` o una situación del día B no puede quedar bloqueado
por la jornada ya terminada del día A. Ausencia y cancelación se aplican por ID a
la jornada nocturna y se muestran en su fecha local de inicio.

## INPUTS

Leer en este orden:

1. `MiGuardiaContainer/AGENTS.md`.
2. `MiGuardia-3.0/AGENTS.md`.
3. `docs/STATUS.md`.
4. Este contrato.
5. `docs/LECTURAS_POR_TAREA.md`.
6. `docs/WORKFLOW.md`.
7. `docs/audits/2026-09-12-stage-1-detalle-edicion-dia.md`.
8. `docs/PLANIFICACION_MIGUARDIA_2_0.md`, secciones 12, 13 y 14.
9. ADR 0003, 0004, 0006, 0007, 0018, 0026, 0027, 0028, 0031 y 0032 según
   vigencia declarada en `docs/adr/README.md`.
10. Código y pruebas de:
    - proyección de Calendario;
    - `ShiftStatus`, `ExplicitDayStatus` y `MedicalLeave`;
    - repositorios Room V2 y operaciones CAS;
    - horario real/extras;
    - recurrencia;
    - Resumen, próximo evento, avisos, clima e informes;
    - Copias y almacenamiento privado.

No abrir todo el archivo histórico por rutina. Consultar 2.x sólo para resolver
una duda concreta y tratarlo como sólo lectura.

## OUTPUT

1. Matriz final de estados, transiciones, precedencias y conflictos.
2. Recorridos operables para `?`, carpeta médica, ausencia, cancelación y vuelta
   a planificada.
3. Mutaciones atómicas con protección de concurrencia.
4. Integración reactiva de todos los consumidores obligatorios.
5. Persistencia y migración mínima si son necesarias.
6. Copias retrocompatibles y archivos privados seguros.
7. Pruebas JVM, Room y Android proporcionales al riesgo.
8. Auditoría fechada y `docs/STATUS.md` actualizado sin declarar aceptación.
9. Handoff compacto a MAIN con resultado, evidencia adversa y puertas restantes.

## SCOPE

Permitido:

- `core/domain/**` para reglas y contratos de situaciones;
- `core/database/**` para persistencia, CAS, migración y esquemas;
- `app/**` para recorridos, consumidores, Copias, archivos privados y pruebas;
- documentación directamente afectada.

La unidad de trabajo es la capacidad completa. Se permiten refactors locales
indispensables para evitar dos fuentes de verdad; no una reescritura general.

## DEPENDENCIES

- Stage 1 aceptado e integrado.
- Room V7 y Copias V7 como base.
- Un solo escritor sobre el checkout.
- No depende de firma, AAB, Play Store, website, cuenta, red ni teléfono con
  datos reales.

## DO NOT

- No ejecutar Stage 2 sin autorización expresa posterior de Joaquín.
- No reimplementar ni degradar Franco, Retén, Notas o horario real de Stage 1.
- No convertir `COMPLETED` en estado persistido.
- No inferir Franco, `?`, ausencia o cancelación por falta de datos.
- No borrar jornadas para representar situaciones.
- No cambiar `applicationId`, versión pública, certificado o firma.
- No usar migraciones destructivas ni modificar esquemas publicados.
- No agregar nube, cuenta laboral, telemetría, feedback obligatorio o permisos
  amplios de almacenamiento.
- No admitir fotos de certificados médicos.
- No calcular salario, descuentos, licencias pagas ni validez legal.
- No rediseñar globalmente MiGuardia; el Stage general de UX/UI sigue posterior.
- No firmar, publicar, crear Release, subir AAB, modificar website ni usar Play
  Console.
- No crear commit, push, rama, worktree o tarea por inferencia.

## VALIDATION

Como mínimo:

### Dominio/JVM

- todas las transiciones válidas y rechazos;
- precedencia de estados y coexistencias permitidas;
- ausencia/cancelación excluidas de descanso y superposición;
- carpeta médica fuera de pendiente, avisos, clima y próximo evento;
- invariantes del motor de horas y desgloses;
- jornada nocturna atribuida a su fecha de inicio;
- ocurrencia recurrente personalizada sin alterar la serie.

### Room y Copias

- migración V7→V8, si existe, preservando las 28 tablas y todos sus datos;
- CAS frente a jornada, situación y horario real concurrentes;
- cascadas y archivos de evidencia sin huérfanos;
- ida y vuelta de Copias con y sin imágenes;
- lectura/restauración de V5, V6 y V7;
- comparación, merge y preview de situaciones nuevas;
- rechazo de metadatos, rutas, tipos, tamaños u órdenes inválidos.

### Android/Compose

- día vacío implícito frente a `?` explícito;
- crear/quitar `?` y conflicto no destructivo;
- crear/editar/eliminar carpeta médica y conflicto con vacaciones;
- ausencia, cancelación, cambio entre ambas y vuelta a planificada;
- varias jornadas en un día: acción sobre la tarjeta correcta;
- horario real incompatible con confirmación explícita;
- Photo Picker y evidencia privada de cancelación;
- reactividad de Calendario, detalle, Resumen y próximo evento;
- proceso recreado, navegación atrás y errores recuperables;
- tema claro/oscuro, zoom 100/150/200 y semántica accesible.

### Cierre

- batería JVM completa;
- AndroidTest afectado compila;
- lint relevante;
- APK y runners QA necesarios ensamblan;
- QA física sólo en paquete QA y con fixtures sintéticas;
- `python tools/docs_check.py`;
- `git diff --check`;
- revisión del diff y búsqueda de secretos, datos reales, APK rastreadas,
  placeholders y permisos inesperados.

No usar el paquete público, firma ni datos personales para acreditar este Stage.

## DONE WHEN

- las cinco operaciones son comprensibles, reversibles y persistentes;
- ninguna transición pierde una jornada ni datos incompatibles sin confirmación;
- todos los consumidores muestran la misma verdad;
- Room y Copias conservan compatibilidad;
- las pruebas y QA proporcionales pasan o sus límites quedan explícitos;
- el diff se limita al Stage 2;
- MAIN recibe un candidato local verificable y Joaquín conserva la decisión de
  aceptarlo antes de integrar o distribuir.

## PROMPT COPIABLE

```text
Continuá MiGuardia como especialista del Stage 2 con GPT-5.6 Sol Medium.

Repositorio:
C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0

Leé primero AGENTS.md del contenedor y del repositorio, docs/STATUS.md y el
contrato completo:
docs/prompts/STAGE_2_SITUACIONES_LABORALES_Y_ESTADOS_DEL_DIA_3_0.md

Ejecutá Puerta 0 y verificá que Stage 1 esté aceptado e integrado. Implementá
como una sola capacidad cohesiva el día explícitamente sin definir, carpeta
médica, ausencia, cancelación y vuelta a planificada. Conservá las jornadas,
usá mutaciones específicas con comparación optimista, integrá motor de horas,
Calendario, Resumen, próximo evento, avisos, clima, recurrencia, informes y
Copias. Si el contrato exige nuevos metadatos, realizá la migración mínima desde
Room V7 sin tocar esquemas publicados.

No reimplementes Franco ni Retén, no persistás COMPLETED, no admitas fotos de
certificados médicos, no agregues red/cuenta/telemetría y no cambies versión,
applicationId, firma o distribución. Usá sólo paquete QA y datos sintéticos para
Android. No hagas commit, push, firma ni publicación sin una orden posterior de
MAIN/Joaquín.

Trabajá con autonomía en decisiones menores. Entregá matriz de transiciones,
implementación, pruebas, evidencia adversa preservada, diff revisado, auditoría
y handoff autosuficiente a MAIN. No declares aceptación del producto.
```
