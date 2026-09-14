# Stage 1 — cierre aceptado de detalle, edición y composición del día

## QUÉ HACE

Implementa el recorrido cohesivo que parte del día del Calendario: estado
completado derivado, detalle jerarquizado, horario real comprensible, edición y
composición del día, Franco, Retén y Notas privadas con hasta 30 imágenes.

## POR QUÉ EXISTE

El recorrido anterior mezclaba identidad, horario y acciones, exponía colores
hexadecimales y no podía conservar imágenes en Notas. El Stage estabiliza esas
interacciones sobre las fuentes de verdad ya existentes, sin cambiar versión
pública, `applicationId`, firma ni distribución.

## OBJECTIVE

Resultado: **IMPLEMENTADO, VERIFICADO Y ACEPTADO POR JOAQUÍN**.

Puerta 0 verificada antes de escribir:

- repositorio `MiGuardia-3.0/`, rama `codex/miguardia-3.0`;
- base `cb83d85552683a0f47f0f4f14f4f43a326fa76a1`, upstream
  `origin/codex/miguardia-3.0`;
- checkout 3.0 independiente; contenedor sin Git y árbol 2.x preservado;
- delta documental de inicialización más trabajo parcial de Stage 1 recibido
  sin staging; se preservó y auditó en lugar de descartarlo;
- escritura limitada al repositorio 3.0 y al contrato autorizado.

## CHANGES

- Calendario: verde tranquilo y check accesible sólo cuando todas las jornadas
  activas del día están completadas; el estado no se persiste.
- Detalle: círculo con el color histórico, dirección interactiva con fallback
  Maps/`geo:`/navegador, Notas antes que Avisos, horario y acciones con jerarquía.
- Horario real: rangos sólo con horas y explicación humana para entrada/salida
  adelantada o demorada, mayor/menor duración e igual duración desplazada.
- Edición: entrada disponible también en días sin jornadas; tarjetas sin código
  hexadecimal; edición filtrada al objetivo histórico; carga manual con fecha
  preseleccionada; eliminación confirmada; advertencias comunes no bloqueantes.
- Día: accesos a Franco y Retén. Franco mantiene la fecha civil elegida. Retén
  abre directamente su intervalo, incluso nocturno, y guarda la etiqueta en la
  ventana sin modificar la configuración de disponibilidad de días futuros.
- Notas: texto, imágenes o ambos; selector múltiple, miniaturas muestreadas,
  orientación EXIF, vista ampliada, confirmación de retiro, límite acumulativo
  de 30 y restauración segura del borrador.

## FILES

Las áreas principales son `CalendarScreen.kt`, `CalendarDayDetail.kt`,
`ui/management/`, `ui/exceptions/`, `ui/availability/`, el almacenamiento de
imágenes, modelos/repositorios de Notas, Room, Copias y sus pruebas. El esquema
nuevo es
`core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/7.json`.

## DECISIONS

- `VERIFICADO`: completado se deriva de `ShiftTemporalStatus`; canceladas y
  ausentes no cuentan como jornadas activas y por sí solas nunca generan check.
- `VERIFICADO`: la edición estructural sigue usando el escritor V2 y su motor de
  planificación; superposición y descanso menor a 12 horas siguen siendo
  advertencias confirmables.
- `VERIFICADO`: Retén sigue siendo `AvailabilityWindowRecord`, no `Shift` ni
  extra, conserva sus cálculos separados en Resumen y usa el `labelSnapshot`
  existente como identidad propia de la ventana contextual.
- `VERIFICADO`: la carga ordinaria continúa usando el nombre versionado de la
  configuración laboral. La acción contextual Retén puede operar aunque ese
  nombre sea otro o esté desactivado, sin crear tabla ni migración adicional.
- `VERIFICADO`: los binarios de Notas usan claves opacas `note_...` dentro del
  almacenamiento privado de imágenes ya endurecido; la asociación semántica es
  una tabla propia, nunca `schedule_photos`. El namespace único mantiene cuota,
  reemplazo de Copias y recuperación de archivos atómicos.

## DATA AND MIGRATIONS

- Room sube de V6 a V7 y de 27 a 28 tablas.
- `shift_note_attachments` tiene FK `CASCADE` a `shift_notes`, orden único por
  `(noteId, position)` y `storageKey` único.
- La migración `6→7` sólo crea tabla e índices; no reescribe datos V6.
- El repositorio guarda nota y metadatos de adjuntos en una transacción Room.
  Los archivos nuevos se copian antes; los retirados se eliminan después del
  commit. Borradores y huérfanos se reconcilian al recrear el coordinador.
- Los seis esquemas históricos permanecen intactos y se agrega `7.json`.

## BACKUPS

- Copias nuevas declaran Room V7 y 28 tablas; V5 y V6 continúan legibles y se
  elevan sin pérdida al modelo V7.
- Una copia con imágenes incluye fotos de cronograma y adjuntos de Notas con
  metadatos, huella, tamaño, dimensiones y tipo validados.
- Una copia sin imágenes vacía ambas tablas de metadatos. Si una nota era sólo
  imagen, también retira esa fila vacía para no restaurar una nota inválida.
- Comparación, merge, referencias, límites de memoria, preview y restauración
  reconocen la tabla nueva.

## VALIDATION

`VERIFICADO` con los mismos inputs finales:

- 854 pruebas JVM frescas: dominio 385, base 12 y app 457; cero fallos y cero
  omitidas. Incluyen regresiones para Retén contextual con disponibilidad
  desactivada, etiqueta histórica inmutable y borrador propio.
- Con el delta final: AndroidTest QA de app compila, `lintDebug` pasa y APK QA
  más su runner ensamblan. La regresión física nueva de miniaturas pasó y las
  clases afectadas de Notas/Fotos pasaron 15/15 en el Samsung.
- Catálogo QA revisado después de los cambios de Stage 1: 108 clases en 19
  grupos, con riesgos y permisos conservados; huellas y métodos V7 actualizados.
- Antes de esa corrección, el candidato también había compilado AndroidTest
  Debug de base y Debug/QA de app, más APK Debug/QA/Release y APK de AndroidTest
  de app/base. Esa evidencia anterior no se promueve a validación fresca del
  delta final; la variante Release tampoco era una entrega firmada oficial.
- Contrato de copia probado con un adjunto real sintético de un píxel y su fila
  lógica V7.
- `tools/docs_check.py`: correcto; `git diff --check`: correcto al cierre.

## GIT STATE

- El Stage se preparó sobre `cb83d855...` y su cierre fue autorizado por
  Joaquín el 14 de septiembre de 2026.
- Al integrar este documento, el Stage queda consolidado en
  `codex/miguardia-3.0`; el hash exacto se comunica en el handoff de MAIN.
- El checkout 2.x conserva cambios de su trabajo independiente de Play Store y
  AAB; este Stage no los modificó.
- No se agregaron dependencias, permisos, secretos, datos reales ni APK
  rastreadas. No hubo tag, firma, AAB ni publicación.

## QA ANDROID — 2026-09-14

`VERIFICADO` en Samsung `SM-S938B`, Android 16/API 36, serial
`R5CY529W6PL`, con paquetes QA exactos y fixtures sintéticas:

- 108/108 pruebas dirigidas pasaron; cero fallos, omitidas o pendientes en los
  16 manifiestos finales.
- Room: 23 pruebas cubrieron migración hasta V7, esquema de 28 tablas,
  persistencia/orden/cascada de adjuntos y Copias Room.
- Copias de app: 30 pruebas cubrieron publicación, recuperación, preferencias y
  archivos privados con los APK finales.
- Compose en dispositivo: 40 pruebas cubrieron Calendario, Disponibilidad y
  Retén, Notas, horario real y edición de jornadas.
- Persistencia y lectura: 3 pruebas cubrieron Franco nocturno y ausencia de
  escrituras desde superficies de consulta.
- Activity end-to-end: 12 pruebas cubrieron navegación real, dirección,
  recreación, horario real, edición/eliminación, disponibilidad y Resumen.
- La inspección visual directa confirmó en tema oscuro el día completado con
  verde/check, el detalle jerarquizado, círculo de color, dirección, Notas antes
  que Avisos y rangos horarios sin fecha ni zona técnica.
- La matriz manual completó tema oscuro con zoom 100% y 150%, y tema claro con
  zoom 200%. En el extremo 200% el contenido conserva jerarquía y permite llegar
  al calendario mediante desplazamiento; el día completado mantiene verde claro,
  check, sigla, horario y estado. Se restauró `Seguir el sistema` y 100%.
- Maps recibió la consulta sintética `Dirección privada ficticia` y el retorno
  conservó abierto el mismo detalle. Maps consumió su historial interno antes de
  devolver el control, sin afectar el estado de MiGuardia.
- `Jornadas del día` mostró indicador circular sin hexadecimal, edición,
  eliminación, agregado, Franco y Retén. El editor contextual abrió como
  `Retén · Fecha 2026-09-01`; el borrador se descartó sin persistir cambios.

La primera corrida de `V2ShiftActualComposeTest` conservó un fallo real de la
prueba: todavía exigía `Planificado: 2026-08-25...`, texto retirado por el
contrato nuevo. Se actualizó para exigir los dos títulos, rangos sólo con horas
y explicación humana; la clase completa pasó 9/9 al repetirla. La evidencia
adversa permanece en `.local-evidence/qa/20260914T205835.957877Z`.

En el primer retorno seguro del Photo Picker con una imagen sintética, la app QA
se cerró por `Canvas: trying to use a recycled bitmap`. La causa era el reciclado
manual de bitmaps todavía retenidos por el dibujo diferido de Compose. Se retiró
ese reciclado en miniaturas de Notas y Fotos y se agregó una regresión que alterna
y renderiza adjuntos repetidamente. Con el APK corregido se verificaron selección,
miniatura, guardado de una nota sólo-imagen, persistencia tras reiniciar el
proceso, reapertura y eliminación; no hubo un nuevo cierre. La nota, su archivo
privado y la imagen sintética de Descargas fueron retirados al terminar.

Al aceptar el Stage, se desinstalaron exactamente
`com.blackatsystems.miguardia.qa`, `com.blackatsystems.miguardia.qa.test` y
`com.blackatsystems.miguardia.core.database.test`. Esto retiró sus datos y los
siete adjuntos sintéticos de fixtures. El paquete público
`com.blackatsystems.miguardia` permaneció instalado y no fue reemplazado,
instrumentado, limpiado ni desinstalado.

## RISKS

- `VERIFICADO`: con autorización, TalkBack Samsung se habilitó temporalmente
  conservando el servicio previo de Alarmy. `dumpsys accessibility` confirmó el
  servicio hablado y los registros de audio confirmaron actividad real de TTS de
  accesibilidad con MiGuardia QA en primer plano. Las etiquetas semánticas también
  se verificaron mediante UIAutomator y Compose.
- `PENDIENTE HUMANO`: una sesión remota sin captura de audio no permite certificar
  qué frase exacta oyó una persona ni su comprensión. Esta limitación no oculta un
  fallo observado y no bloquea la aceptación concedida por Joaquín.
- `VERIFICADO`: se restauraron exactamente Alarmy como único servicio habilitado,
  `accessibility_enabled=1`, exploración táctil desactivada y sin permiso
  telefónico residual para TalkBack.
- `PENDIENTE`: no hay evidencia de actualización real del paquete público desde
  2.0.2 ni de restauración de una copia personal. Las migraciones y
  restauraciones Android ejecutadas usaron paquetes QA y datos sintéticos.

## PENDING

Stage 1 no conserva tareas funcionales pendientes. La actualización real del
paquete público y la restauración de una copia personal pertenecen a una futura
puerta integrada de entrega, no a este cierre. Stage 2 requiere autorización
propia; firma y distribución permanecen fuera de alcance.

## DEPENDENCIES

El candidato deja estabilizados los contratos V7 de Notas/Copias y el recorrido
de día. Cualquier consumidor futuro de imágenes privadas debe respetar el
namespace, límites y reemplazo transaccional actuales.

## NEXT

Revisar y, si corresponde, autorizar el contrato
`docs/prompts/STAGE_2_SITUACIONES_LABORALES_Y_ESTADOS_DEL_DIA_3_0.md`. No iniciar
su implementación, firma ni distribución por inferencia.
