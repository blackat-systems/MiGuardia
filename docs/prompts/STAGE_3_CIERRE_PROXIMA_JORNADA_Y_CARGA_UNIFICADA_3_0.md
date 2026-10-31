# Cierre de Stage 3 — Próxima jornada y carga unificada

## TASK / CONTEXT

Implementar el cierre solicitado por Joaquín el 2026-09-16: próxima jornada,
carga individual y múltiple unificada, catálogo contextual, notas privadas,
conflictos y limpieza. Completar integración, regresiones, documentación y QA
sintética autorizada. El estado operativo vive exclusivamente en STATUS.

Repositorio: `MiGuardiaContainer/MiGuardia-3.0`, rama `codex/miguardia-3.0`.
Preservar íntegramente el checkout acumulado de Stage 2, Stage 3 y Stage 3.1.
MiGuardia-2.0 es referencia de sólo lectura. No crear ramas, worktrees ni tareas.

## INPUTS / PUERTA 0

Leer en orden AGENTS del contenedor y del repositorio, STATUS,
LECTURAS_POR_TAREA, HANDOFF_STAGE_3_A_MAIN_3_0, contrato Stage 3.1 y secciones
vigentes de auditoría, VALIDACION y EVIDENCIA. No cargar historia por rutina.
Verificar ruta, Git independiente, rama, HEAD, upstream, remotos, staging,
modificados, no rastreados, escritor único y reglas del Samsung antes de escribir.
Separar el delta propio del recibido; preservar diferencias inexplicadas.

Mapa acotado: NextEvent y su observador/card; CalendarDayDetail y MiGuardiaApp;
V2ManualShiftLoad, WorkSetup y creación contextual; ShiftNote y archivos privados;
planV2ShiftBatch, V2ShiftRepository, CalendarBatchRepository; situaciones,
vacaciones, Retén, horas, Resumen, avisos; pruebas y tools/qa-groups.json.

## CONTRATO FUNCIONAL

### Próxima jornada

Orden: «Próxima jornada», «Hoy / Mañana / fecha», «Comienza en …», identificador
de color + institución/servicio + horario. Hoy no es el título principal.
Reutilizar elegibilidad de dominio: no canceladas ni situaciones excluidas.
Calcular con inicio previsto efectivo, Instant y zona, sin restar horas ingenuamente.
Mostrar días/horas/minutos con singular/plural y omitir ceros; menos de un minuto
usa «Comienza en menos de un minuto». No negativos ni contador vencido.
Actualizar con el tiempo y al primer plano, sin permisos/servicios/sondeo intensivo.
Conservar jornadas en curso y ausencia de próximas; nunca llamar futura a una
activa. Mantener omisión adaptativa de la tarjeta si no cabe sin recortar el mes.

### Un solo formulario

Editar día abre directamente la carga principal; selección múltiple abre la
misma implementación con todas las fechas. Sin menú obligatorio intermedio.
Encabezado: «Cargando jornadas» / «Elegí un lugar, tipo y horario» / fechas.
Un día: fecha legible. Varios: una línea DD/MM/YYYY, cantidad restante y flecha
para desplegar todo, ordenado y sin duplicados. Expandir no cambia la selección;
semántica anuncia conjunto completo y estado expandido. El formulario puede
desplazarse; el Calendario principal no.

Primer botón «Instituciones o servicios»: submenú con «Crear institución o
servicio» arriba y objetivos existentes; elegir objetivo muestra sus horarios;
elegir horario retorna al formulario con ambos visibles y modificables.
Crear objetivo encadena creación del primer horario, reutilizando modelo,
pantallas y validaciones existentes. Objetivo sin horarios ofrece crear uno.
Conservar fechas/borrador al volver o recrear; no elegir alternativas silenciosas.
Catálogo confirmado explícitamente puede sobrevivir a cancelar la carga;
cancelar creación sin confirmar no crea registros ni jornadas.

Con institución/horario: «¿Querés agregar una nota a esta jornada?» o «… a estas
jornadas?», luego «Guardar jornada(s)». Nota opcional con texto/imágenes privadas,
límites y protección vigentes. En múltiple, se aplica a cada jornada y luego se
edita independientemente. Sin referencias rotas, eliminación de imágenes ajenas,
notas huérfanas ni ampliación del límite. Preservar integridad archivos/base.
Datos obligatorios habilitan guardar; impedir doble envío y distinguir reintento
técnico de segunda carga intencional.

### Franco, limpieza, cancelación

Debajo del botón de instituciones: «Marcar como franco(s)», «Limpiar día(s)».
Al final «Cancelar carga». Franco usa reglas vigentes: una jornada del día
anterior que termina a las 07:00 no lo impide. Preview de incompatibilidades,
sin borrar contenido silenciosamente.

Limpiar deja realmente Sin definir: preview concreto de registros retirados o
ajustados y pérdidas asociadas; no borra catálogo ni jornadas vecinas sólo por
cruzar medianoche. En atribuciones de varias fechas conserva partes exteriores.
No mostrar Sin definir con atribuciones activas contradictorias. Reutilizar
eliminación/privacidad. Si exige nuevo contrato material de persistencia o una
decisión de pérdida no cubierta, detener esa parte y presentar alternativas.

Cancelar descarta borradores de jornadas/notas no confirmadas, no escribe
jornadas/francos/limpiezas, conserva catálogo confirmado y no restaura selección
abandonada. Mantener dueño único de Atrás.

### Conflictos y atomicidad

Preview de fecha, registro y horario existentes, jornada nueva y consecuencia.
Ofrecer «Sobrescribir», «Superponer» y volver sin escribir.
Sobrescribir elimina sólo jornadas con solapamiento real y franco incompatible,
no todas las jornadas del día. Incluye conflictos nocturnos iniciados en fechas
vecinas, indicadas explícitamente. Superponer conserva registros anteriores,
incluido Franco; consumidores muestran coexistencia sin ocultarla ni corromper
horas/estados/resúmenes. Demostrar y elevar incompatibilidad estructural antes
de descartar contenido.

Solapamiento y descanso menor de 12 h son advertencias, no bloqueos por sí solos.
Inspeccionar notas, imágenes, horario real, situaciones, avisos y referencias;
preview de pérdidas y confirmación, sin trasladar datos incompatibles.
Revalidar al confirmar; cambios concurrentes regeneran revisión. Operación
múltiple atómica, sin aplicar parcialmente ni degradar CAS.

### Capacidades y diseño preservados

Mes completo, sin scroll principal, semana forzada, preferencia antigua de
plegado ni barra inferior permanente. Toque abre detalle; pulsación larga nativa
selecciona, acciones sólo contextuales. Conservar árbitro GENERAL/MANUAL_LOAD/
HOLIDAY, preview y cancelación sin escrituras.
Mantener edición/eliminación de jornadas, Retén, médica, vacaciones/situaciones,
notas/adjuntos, horario real, Maps, avisos, clima/configuración, acciones masivas,
carga manual y feriados mediante accesos contextuales sin menú obligatorio.
No convertir formulario en inventario de funciones. Estética mensual de Stage
3.1; jerarquía clara, acentos de color, sin bloques violetas redundantes,
español argentino y accesibilidad sin depender sólo del color.

## SCOPE / DEPENDENCIES / DO NOT

Sólo superficies anteriores, integración necesaria, pruebas, QA y documentación.
No reescritura general ni motores paralelos. No modificar esquema Room o Copias
por conveniencia. Si se demuestra necesaria migración o contrato material nuevo
de persistencia, presentar evidencia y alternativas ANTES de implementarlo.
No tocar app pública/datos personales, versión, applicationId, firma,
distribución, website ni ajustes globales del Samsung. Sin commit/push/tag,
firma de distribución/publicación ni limpiezas indiscriminadas.

## VALIDATION

Reloj controlado: hoy/mañana/fecha, plurales, unidades, <1 minuto, medianoche,
transición a curso, vacío, exclusiones y primer plano. Sin esperas artificiales.
Carga: uno/varios, expandir fechas, catálogo existente y creación encadenada,
cancelación en cada nivel, recreación/borrador, notas texto/imágenes, guardado,
reintento y retorno mensual.
Conflictos: ninguno, contiguos, parcial/total, noche, anterior, Franco; overwrite
conserva no afectados; overlay conserva anteriores; descanso, datos asociados,
selección mixta, concurrencia, rollback y cancelación.
Limpieza: uno/varios, asociados, rangos, vecinos, Sin definir real, cancelar preview.
Consumidores: Calendario, detalle, horas, Resumen, evento y avisos; repetir Atrás,
carga manual/feriados si se afectan puntos de integración.

Pruebas JVM proporcionales (completas si transversal), compilar AndroidTest,
lint, QA, catálogo, docs_check y diff --check. No fabricar verdes ni repetir
baterías por ritual. Cambios posteriores requieren revalidar y reinstalar.

Samsung autorizado exclusivamente con variante QA y datos ficticios. Verificar
identidad del dispositivo/APK/paquete y aislamiento público ANTES de instalar.
Runner oficial con recibos: mes → detalle → Editar → institución/horario → nota
→ guardar, uno/varios; overwrite/overlay, Franco/limpiar/cancelar, recreación,
Atrás y ausencia de reentrada. Composición/teclado/navegación/temas/zoom interno
100/150/200. Capturas sintéticas; distinguir automatización, visual y TalkBack.
Dejar última QA comprobada instalada y abierta; no atribuirle aceptación humana.

## OUTPUT / DONE WHEN

Actualizar STATUS, handoff, auditoría e índice con decisiones que reemplazan el
recorrido anterior. MAIN recibe delta/archivos, causa, regresiones/resultados/
recibos, adversos preservados, capturas, hash instalado/aislamiento público,
riesgos y guía humana. Termina sólo con contrato integrado, datos protegidos,
código/pruebas/docs coherentes y última QA instalada y abierta.
Estado final permitido: «Stage 3 cerrado técnicamente como candidato para
revisión humana». Nunca aceptación o distribución sin decisión expresa posterior.
