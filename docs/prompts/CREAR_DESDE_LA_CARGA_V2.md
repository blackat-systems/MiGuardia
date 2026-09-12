# Crear desde la carga

## Qué hace y por qué existe

Permite crear lugares de trabajo y horarios reutilizables mientras la persona
carga una jornada o varios días. Al terminar, vuelve al mismo borrador con el
horario nuevo seleccionado, listo para confirmar las jornadas.

Hoy el selector ofrece únicamente combinaciones existentes. Los creadores
ya existen en WorkSetup, pero no tienen un regreso contextual a esa carga.
Esta dependencia resuelve toda la capacidad: interfaz, creación, estado,
regreso, actualización del catálogo, validación histórica, errores y pruebas.
No es un encargo de dos botones ni una reimplementación del catálogo.

## Rol, profundidad y entrega

Actuás como especialista en una tarea separada de MAIN. Joaquin eligió Astra
con razonamiento ultra y exige bloques sustanciales resueltos de punta a punta.
Aplicá `docs/WORKFLOW.md`: los hitos de este recorrido se resuelven dentro de
esta dependencia. No abras chats separados para interfaz, regreso y pruebas.
Podés usar revisores internos acotados y de sólo lectura mientras implementás;
un solo escritor y un solo operador de Gradle/dispositivo. No crear otras tareas,
ramas o worktrees. MAIN conserva aceptación e integración final.

Diseñá la solución completa después de inspeccionar los contratos; implementá
y depurá autónomamente dentro de este alcance. No te detengas en decisiones
reversibles. Devolvé un candidato coherente con pruebas y evidencia, no piezas
para que MAIN complete la función. Ante una decisión material no resuelta,
explicá exactamente qué contrato la bloquea y conservá todo lo ya comprobado.

## Entrada verificable

- Checkout compartido: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- Base funcional exacta: `8249006cc171cd92acc4b571c65a24969141fe13`.
- MAIN entrega en el mensaje de inicio el HEAD del checkpoint documental.
  Debe descender de esa base y diferir exclusivamente en AGENTS, WORKFLOW,
  PLANIFICACION, STATUS, índice de prompts y este contrato. Árbol e índice
  vacíos al iniciar. Registrar SHA completo y detenerse ante otro delta.
- Autor: `joaquin <blackat.systems@gmail.com>`; origin existente de MiGuardia.
  Nueve worktrees previos, ninguno para recuperar código o editar.
- Room V6, 27 tablas, seis esquemas; versión `2.0.0-beta.1`, código `2`.
- Clima/créditos ya integrado; no reabrir ese bloque. Evidencia de la base:
  805 JVM, 72 pruebas de herramientas y 22 pruebas Samsung proporcionales.
  Son antecedentes de entrada, no pruebas de esta implementación.

Leé AGENTS, STATUS, WORKFLOW, VALIDACION y la ruta pertinente de
`docs/LECTURAS_POR_TAREA.md`. En planificación: configuración con historia,
lugares/tipos/plantillas, primera apertura y Calendario. Consultá ADR 0023 para
CAS y ADR 0037 para prioridad de WorkSetup/Ayuda. Sus referencias históricas
a otros esquemas no reemplazan Room V6 ni la puerta beta actual.
No reconstruyas auditorías cerradas, website o la historia completa de MAIN.

## Recorrido funcional obligatorio

1. **Entrada contextual.** En el selector de lugar/tipo/horario de la carga
   manual ofrecer «Crear lugar» y «Crear horario», con lenguaje sectorial.
   Una fecha y varias comparten el recorrido. Acciones encontrables también
   cuando no hay opciones válidas; no quedan ocultas en Opciones avanzadas.
   Mientras haya una operación de carga/guardado incompatible, impedir otro
   lanzamiento sin perder el estado ni presentar un botón aparentemente roto.
2. **Horario para un lugar existente.** Reutilizar el formulario actual,
   trasladando por IDs la selección disponible y válida. Si hay varias opciones,
   permitir elegir conscientemente; no elegir la primera por orden incidental.
   Conservar tipo, horas exactas, color y validadores actuales. «Horario» es la
   plantilla reutilizable; «jornada» es lo que después se guarda en cada fecha.
3. **Lugar nuevo y su horario.** Reutilizar el creador de lugar, con sus reglas
   y opciones existentes, y continuar directamente a crear el horario para ese
   lugar. No mandar a la persona a buscar manualmente lo recién creado en otra
   sección. No añadir otro perfil, primer onboarding o calendario.
4. **Regreso útil.** Al confirmar el horario, volver al selector original,
   actualizar el catálogo y seleccionar su ID exacto sólo si sigue siendo
   elegible. Conservar mes, conjunto de fechas, confirmación de fechas, puesto
   y demás entradas del borrador. No guardar jornadas automáticamente.
5. **Cancelar sin pérdida.** Volver/Atrás respetan el mismo origen y la
   confirmación de descarte del formulario cuando corresponda. Cancelar antes
   de guardar no altera el catálogo ni la selección anterior. Si se guardó el
   lugar y luego se canceló el horario, el lugar permanece guardado, se explica
   ese resultado parcial consciente y se permite continuar sin duplicarlo.
6. **Confirmación final.** «Guardar jornada» o «Guardar N jornadas» mantiene
   el flujo simplificado, revisión detallada opcional y advertencias necesarias.
   Revalidar nuevamente al confirmar; no se heredan aprobaciones de otro horario.

Vocabulario de lugares: Objetivo; Dependencia o lugar de servicio; Institución
o servicio; Hospital, clínica, consultorio o servicio. Reutilizar WorkSector y
sus helpers; no duplicar otro mapa sectorial. Calendario conserva «jornada».
Mantener separadores automáticos de horas, selector de colores completo,
opciones avanzadas y ayuda contextual existentes. No rediseñar toda la app.

## Estado, concurrencia y contratos que debe resolver el diseño

- Origen y resultado explícitos, tipados y vinculados a la operación. El
  resultado identifica al menos lugar/horario creados y la solicitud que los
  espera. Consumo idempotente después de recreación: no inferir éxito por texto,
  cierre de pantalla, último elemento del catálogo o sólo lugar/tipo.
- El borrador de carga sigue vivo durante el creador. No llamar a `start()`
  al regresar: hoy reinicializa ese borrador. Agregar la actualización pertinente
  al coordinador existente, no una segunda implementación de carga.
- El resultado de guardado y la emisión del catálogo pueden llegar en distinto
  orden. Resolver ambos órdenes sin bucles de polling, timers arbitrarios ni
  selección inexistente. Un error de lectura permite reintentar sin otra alta.
- Conservar estado suficiente mediante los mecanismos guardables existentes
  para recreación y restauración disponible tras muerte del proceso. No asumir
  que un callback o ViewModel en memoria sobrevivirá. Releer datos para resolver
  operaciones interrumpidas; no repetir automáticamente una escritura o acción
  externa incierta. Probar la ventana guardar → recrear → consumir resultado.
- Proteger doble toque, guardado/reintento, resultados tardíos y solicitudes
  reemplazadas. Una respuesta anterior no cambia otra carga. Si un alta quedó
  persistida pero se interrumpió la entrega del resultado, recuperarla por
  identidad antes de ofrecer reintentar; no crear duplicados por esa carrera.
- El creador usa contexto laboral vigente; la carga resuelve revisiones por
  cada fecha. Revalidar timeline, sector, lugar, tipo, horario y reglas para
  **todos** los días. No retroceder vigencias silenciosamente, recortar fechas
  incompatibles ni eludir la confirmación V2 existente de carga anterior.
- Lugar/horario archivado, eliminado o cambiado mientras el creador estaba
  abierto produce una respuesta recuperable con fechas conservadas. Nunca una
  opción inexistente seleccionada ni un guardado que ya no coincide con la UI.
- Seleccionar un horario nuevo invalida revisión, huella, ocupación, políticas
  y advertencias calculadas para el anterior. Reutilizar las comprobaciones de
  segunda jornada, reemplazo, solapamiento, descanso y protecciones, además de
  CAS transaccional y `Shift + ShiftWorkSnapshot`. No nuevos escritores de Shift.
- Crear o consultar catálogo no cambia jornadas anteriores, extras, notas,
  fotos, configuraciones horarias o totales. Separar las escrituras conscientes
  del catálogo de las escrituras posteriores del lote de jornadas.
- Recuperación → Bloqueo → beta → configuración → Ayuda → contenido mantienen
  prioridad. Un cierre de puerta suspende la presentación/acciones laborales;
  una respuesta tardía no abre la app detrás de la puerta. Los destinos externos
  pendientes no sustituyen el borrador ni se consumen dos veces.
- La entrada habitual desde «Mi forma de trabajar» y la primera configuración
  mantienen su recorrido: sólo quien llegó desde la carga vuelve a esa carga.

## Puntos de integración comprobados y alcance de archivos

Prefijo de producción: `app/src/main/java/com/blackatsystems/miguardia/`.

- `ui/management/V2ManualShiftLoadScreens.kt`: selector y acciones compartidos.
- `ui/management/V2ManualShiftLoadViewModel.kt`: borrador, lectura de opciones,
  revalidación, confirmaciones y persistencia guardable del coordinador.
- `ui/worksetup/WorkSetupViewModel.kt`: `startAnotherPlace`, `saveAdditionalPlace`,
  `openAdditionalTemplate`, `saveAdditionalTemplate`. Hoy el alta de lugar termina
  en COMPLETION sin horario; el resultado guarda lugar/tipo pero no templateId.
- `ui/worksetup/WorkSetupUiState.kt` y `WorkSetupScreens.kt`: formularios,
  estado, acciones y navegación del productor.
- `ui/MiGuardiaApp.kt` y `ui/CalendarScreen.kt`: conexión contextual del flujo.
- `MainActivity.kt` sólo si hace falta conectar estado/factorías existentes;
  no rediseñar las puertas de acceso ni su autoridad.
- Recursos de texto pertinentes y helpers/coordinadores nuevos pequeños bajo
  esas áreas cuando reduzcan duplicación y tengan un dueño claro.

Extender pruebas locales/instrumentadas del recorrido; actualizar sus entradas
en `tools/qa-groups.json` cuando cambien inventario, hash o aislamiento.
Principales pruebas existentes: `V2ManualShiftLoadCoordinatorTest`,
`WorkSetupCoordinatorTest`, `V2ManualShiftLoadComposeTest`, `WorkSetupComposeTest`,
`V2ManualShiftLoadActivityTest` y restauración de Calendario si se afecta.

Core, repositorios y esquemas se consultan para entender contratos, no se editan.
No cambiar Room, formatos Copias, preferencias portables, reglas laborales,
recurrencia, Clima, Widget, avisos, beta, actualizaciones, firma, versión,
manifiesto, permisos, Gradle o dependencias. No editar documentación canónica
desde el especialista. No recursos remotos, publicación, secretos ni red nueva.
Si aparece una necesidad real fuera de esa frontera, devolver evidencia y la
decisión precisa; no simular la capacidad debilitando contratos.

## Matriz de aceptación del bloque completo

| Familia | Evidencia mínima exigida |
|---|---|
| Camino feliz | Un día y varios; lugar nuevo con horario; horario nuevo de lugar existente; IDs correctos seleccionados; lote correcto después de confirmar |
| Entrada sin opciones | Mensaje útil, acción para crear y salida sin callejón sin salida; respetar primera configuración si todavía corresponde |
| Cancelación | Antes del primer guardado y después de guardar lugar; selección/fechas conservadas; estado parcial explicado; cero borrados compensatorios ocultos |
| Fallos y carreras | Falla al crear lugar/horario/refrescar; reintento sin duplicado; doble toque; callback viejo; catálogo antes/después del resultado |
| Restauración | Recrear en ambos formularios y alrededor de guardado/consumo; regreso exacto; interrupción disponible tras proceso nuevo sin escritura automática |
| Historia | Fechas antes/después de revisión laboral; opción inválida para parte del lote; archivado concurrente; no omisión ni reescritura silenciosa |
| Guardado | Sólo catálogo antes de confirmar; CAS y controles de reemplazo/protección tras elegir el nuevo horario; jornada/fotografía por fecha |
| Navegación | Cambiar días, cancelar carga, entrada normal de WorkSetup, primera apertura, Bloqueo/beta/Ayuda y destino externo pendiente |
| Presentación | Cuatro sectores, texto largo, claro/oscuro, retrato/paisaje, teclado/Atrás, foco/semántica y zoom interno 100/150/200 |

Usar funciones y coordinadores productivos. No reemplazar el recorrido por
pantallas sueltas con callbacks vacíos ni copiar fórmulas en tests. Probar que
antes de «Guardar jornadas» sólo cambió el catálogo explícitamente creado;
después verificar fechas, número de pares y fotografías. Mantener fixtures
deterministas con reloj, zona e IDs controlados y un caso transversal real de
productor → consumidor → confirmación, además de tests aislados de límites.

Ejecutar primero regresiones dirigidas; luego, sobre los insumos finales:

- `python tools/verify.py --profile local --fresh-tests`
- `python tools/verify.py --profile build`
- `python tools/qa.py --check-catalog`

Comprobar diff completo/no rastreados y documentación sin cambios.
No exigir un número artificial de tests ni repetir build por un hunk documental.
Los fallos detectados en el bloque se corrigen y se repite la superficie afectada.

### QA física y conservación

Preparar y clasificar la instrumentación del mismo candidato. Ejecutarla sólo
cuando MAIN/Joaquin transmitan autorización expresa del ensayo en esta tarea;
este prompt no autoriza por sí mismo ADB, borrado o apertura de producción.
La QA forma parte del mismo bloque, no requiere una nueva dependencia funcional.
Si aún falta autorización/dispositivo, terminar la evidencia local y dejar los
casos físicos exactos pendientes sin denominarlos aprobados.

Advertencia verificada: `V2ManualShiftLoadActivityTest` llama a
`clearAllDataForInstrumentation()` y activa beta/onboarding en su preparación.
**No ejecutar esa fixture sobre la QA preexistente conservada.** Requiere un
entorno de prueba desechable expresamente autorizado, o un ensayo preservador
revisado con restauración demostrable. No desactivar beta/Bloqueo en producción
para abrir el recorrido. Declarar el riesgo real de cada grupo en el catálogo.

Usar sólo datos ficticios. Sin PIN/credenciales, claves, inventario de producción,
capturas ajenas, consulta/cambio de font_scale o densidad Android. Conservar QA
y sus datos salvo autorización concreta de entorno desechable. Comprobar APK
local/instalado; distinguir seleccionado/visible de escalado efectivo al revisar
zoom. Registrar temas, tamaño, legibilidad y regreso en la Activity real.

## Handoff a MAIN y condición de entrega

El recorrido debe estar implementado y localmente verde, sin defectos conocidos
ocultos ni partes aplazadas a otro implementador dentro de este contrato.
Un pendiente físico autorizado/no ejecutado se informa por separado; no invalida
lo probado ni se convierte en cierre integral. Toda evidencia pertenece al
artefacto final, o declara qué insumos cambiaron y qué necesita repetición.

Entregar un handoff compacto: QUÉ HACE, POR QUÉ EXISTE, decisiones de estado y
regreso, archivos, pruebas efectivas, errores corregidos, matriz física,
preservación, riesgos, Git exacto y próximo paso. Enlazar evidencia extensa en
un directorio ignorado propio; no volcar datos privados ni binarios a Git.

Devolver el mismo checkout a MAIN con el diff, sin staging, commit, push, tag,
merge, rebase, reset, descarte, rama/worktree/tarea nueva o publicación. No hay
cherry-pick. MAIN audita, decide el checkpoint y coordina el siguiente bloque.
