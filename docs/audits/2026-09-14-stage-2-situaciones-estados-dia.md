# Stage 2 — candidato local de situaciones laborales y estados del día

Fecha: 2026-09-14  
Resultado: **IMPLEMENTADO, CORREGIDO TRAS REVISIÓN VISUAL, VERIFICADO
LOCALMENTE Y EN QA FÍSICA SINTÉTICA, Y ACEPTADO EL 2026-09-15; NO DISTRIBUIDO**.

## Puerta 0 y alcance

- Checkout: `MiGuardia-3.0`, rama `codex/miguardia-3.0`, base
  `932eaeb60c6b6e68975534788e3e6501c967b1ae` y upstream
  `origin/codex/miguardia-3.0`.
- Se confirmó un solo worktree, remoto esperado y árbol limpio antes de escribir.
- Joaquín autorizó expresamente Stage 2. No se tocó MiGuardia 2.0, versión
  pública, `applicationId`, firma, distribución ni dispositivo.
- La matriz durable está en `docs/STAGE_2_MATRIZ_ESTADOS_Y_TRANSICIONES.md`.

## Resultado funcional

- `?` es una marca civil explícita y reversible. Su CAS bloquea únicamente una
  jornada iniciada ese día o un Franco; conserva carpeta médica, vacaciones,
  Retén y una jornada nocturna iniciada el día anterior.
- Carpeta médica permite alta, edición y eliminación confirmada por rango. Las
  correcciones comparan la fila observada, rechazan vacaciones concurrentes y
  nunca guardan certificados ni eliminan jornadas.
- Ausencia y cancelación corrigen `Shift.status` sin reemplazar la jornada.
  Cancelación exige motivo, actor y fecha/hora del aviso; admite una imagen
  privada opcional. Volver a Planificada elimina sólo la situación controladora.
- El cambio compara exactamente jornada V2, situación, agregado de horario real
  con su conjunto de extras y ocurrencia recurrente. Si hay horario real/extras,
  se retiran sólo tras confirmación y dentro de la misma transacción Room.
- Una ocurrencia automática tocada queda `CUSTOMIZED`; la serie no se modifica.
  Un diálogo obsoleto falla, refresca la expectativa visible y exige un segundo
  intento explícito.

## Datos, privacidad y Copias

- Room sube de V7/28 tablas a V8/29 mediante `MIGRATION_7_8`, que sólo crea
  `shift_situations` y sus índices. La FK a `shifts` usa cascada y la clave de
  evidencia es única.
- Estados V7 `ABSENT`/`CANCELLED` sin fila de situación ni metadatos siguen
  válidos, no reciben motivo/actor/aviso inventados y pueden volver a Planificada.
- La evidencia usa claves opacas `cancel_<uuid>_<versión>.<ext>`, Photo Picker,
  límites existentes y almacenamiento privado. Reemplazo/retiro son recuperables;
  la reconciliación elimina temporales y huérfanos del namespace.
- Copias V8 reconoce `shift_situations` en captura, validación, preview,
  comparación, merge y restauración. V5/V6/V7 se leen y elevan con la tabla nueva
  vacía. Sin imágenes se nulifica sólo el vínculo binario; con imágenes se valida
  ruta, tipo, tamaño, dimensiones y SHA-256.

## Consumidores

- Calendario y detalle proyectan `?`, carpeta médica, ausencia y cancelación.
- Motor de horas, desgloses, Resumen e informes reutilizan `Shift.status`, por lo
  que ausencia/cancelación quedan fuera de trabajo/cumplimiento sin perder plan.
- Próximo evento y avisos ya comparten la proyección V2 y excluyen jornadas no
  operativas, vacaciones y carpeta médica.
- Clima ahora consulta también carpeta médica y revalida elegibilidad antes de
  mostrar o refrescar el brief, sin agregar solicitudes de red.

## Validación local

`VERIFICADO` con runtime JBR explícito:

- `testDebugUnitTest`: 862 pruebas JVM, cero fallos y cero omitidas
  (dominio 392, base 12, app 458).
- AndroidTest afectado de app y base compila. Incluye regresiones Room para
  transición encadenada, diálogo stale, conjunto exacto de extras, consentimiento
  de retiro y compatibilidad de estado legacy; además store→modelo de evidencia y
  una prueba Compose dirigida para `?` y selección entre varias jornadas.
- `:app:lintDebug`, APK QA, runner QA de app y runner Debug de base ensamblan.
- El contenedor de Copias hizo ida y vuelta con una evidencia PNG sintética y su
  fila controladora V8.

### Validación física sintética

`VERIFICADO` en Samsung `SM-S938B`, API 36, serial autorizado `[serial QA en binding local privado]`:

- 118/118 pruebas instrumentadas dirigidas pasaron: persistencia y CAS de
  situaciones, migración V8, Copias con 29 tablas, Franco/carpeta médica,
  archivos privados, Calendario y consumidores de Resumen, Horas, Próximo
  evento, Informes y Clima.
- Se recorrió manualmente la variante QA: alta de carpeta médica sintética,
  presentación del rango, apertura de edición, confirmación de eliminación y
  retorno del día a `Sin definir`.
- El dato sintético fue eliminado al terminar. La instalación pública permaneció
  separada y nunca fue objetivo de instrumentación.
- Evidencia no rastreada: manifiestos finales entre
  `.local-evidence/qa/20260915T002936.651601Z` y
  `.local-evidence/qa/20260915T004042.632224Z`; capturas y jerarquías en
  `.local-evidence/qa/visual-20260915T0041/`.
- El Samsung se desconectó después de las verificaciones. La variante QA y sus
  runners quedaron instalados y no se pudo confirmar el borrado de las capturas
  sintéticas `miguardia-stage2-*` en `/sdcard`; no contienen datos personales.

`PENDIENTE`: la sesión no equivale a actualización/restauración del paquete
público ni a una matriz manual exhaustiva de temas, zoom, TalkBack y Photo Picker.

## Corrección visual solicitada por Joaquín

La evidencia física hizo visible una deuda de composición: fecha extensa,
acciones de día mezcladas con información y controles repetidos. El candidato
ahora muestra `DD/MM/YYYY`, un único botón `Editar día` y luego una tarjeta
`Condición del día` que enumera únicamente las atribuciones reales. El submenú
usa fecha humana completa y tres altas principales: `Agregar jornada o franco`,
`Agregar disponibilidad de retén` y `Agregar carpeta médica`. Las correcciones
de registros existentes son contextuales; una disponibilidad inexistente no
genera una tarjeta vacía. Se retiró `Volver al detalle`: Atrás vuelve al
Calendario. Los rangos y formularios del recorrido usan fecha argentina y no ISO.

La corrección no toca dominio, Room ni Copias. `compileQaKotlin`, AndroidTest QA,
458/458 JVM de app y `lintDebug` pasan. La repetición física final quedó 47/47:
8 de Calendario/detalle, 12 de edición de jornadas y 27 de carga
manual/Franco/Retén. El recorrido manual verificó la composición final y Atrás
en el Samsung. Evidencia final:
`.local-evidence/qa/20260915T022118.398119Z`,
`.local-evidence/qa/20260915T022127.592755Z`,
`.local-evidence/qa/20260915T022136.980928Z`,
`.local-evidence/qa/visual-20260915T0223-detail.png` y
`.local-evidence/qa/visual-20260915T0224-menu.png`.

La primera repetición de carga manual falló 1/27 porque el día vacío priorizaba
un inspector sin jornadas. Se corrigió para usar el flujo contextual unificado y
la clase completa pasó 27/27. La evidencia adversa se conserva en
`.local-evidence/qa/20260915T014607.835866Z`.

La primera repetición tras la corrección final quedó 7/8 en Calendario/detalle:
la prueba todavía exigía el texto retirado `Franco marcado explícitamente.` pese
a que la tarjeta mostraba correctamente `Franco`. Se actualizó la expectativa y
la clase pasó 8/8. Evidencia adversa:
`.local-evidence/qa/20260915T021753.515543Z`.

## Evidencia adversa preservada

- El primer comando pidió `:core:domain:compileKotlin`, nombre ambiguo en Android;
  se repitió con tareas Debug explícitas.
- La primera compilación del repositorio de situaciones detectó un smart cast
  inválido; la primera compilación UI detectó visibilidad interna y luego un smart
  cast del agregado real. Se corrigieron sin reducir alcance.
- La primera JVM focalizada encontró 41 fallos: los fixtures seguían declarando
  las 28 tablas V7 bajo el contrato corriente V8. Tras separar fixtures legacy de
  snapshots corrientes quedaron cuatro fallos semánticos, luego uno por tolerancia
  V5 en evidencia y uno por regex sin namespace `cancel_`; todos se corrigieron y
  la batería completa pasó.
- La primera compilación de la regresión Compose detectó el import faltante de
  `assertEquals`; se agregó y el AndroidTest volvió a compilar.
- El primer `docs_check` rechazó un estado descriptivo no canónico y dos filas
  marcadas como `AUTORIZADO`; STATUS pasó a `VERIFICADO_LOCALMENTE` y el índice
  conserva como autorizado únicamente el contrato Stage 2. La repetición pasó.
- En el cierre, `python tools/docs_check.py` volvió a encontrar sólo el alias de
  Microsoft Store fuera de PATH; la repetición con el runtime Python local
  explícito pasó sin problemas.
- La recompilación final del test de transición legacy apuntó primero a una ruta
  de Android Studio inexistente; se repitió con el JBR local explícito ya usado
  por la batería y compiló correctamente.

## Límites y siguiente puerta

- No hubo commit, push, tag, firma, AAB, publicación ni datos reales. La QA
  física autorizada usó exclusivamente los paquetes técnicos del Samsung.
- MAIN completó la revisión independiente y corrigió la aceptación en Copias de
  carpetas médicas superpuestas permitidas por Room y el acceso visible para
  editar una ausencia/cancelación ya registrada.
- La QA física rechazó correctamente una regresión de MAIN que había inventado
  `CANCELLED + horario real`, combinación inválida para el dominio, y otra
  fixture con horario real idéntico al planificado. Se retiró la primera
  conclusión y se corrigieron las fixtures; la evidencia fallida se conserva.
- La primera ejecución física encontró un defecto real: `REPLACE` podía borrar
  una situación al reutilizar por error la misma clave única de evidencia desde
  otra jornada. Se sustituyó por `Upsert`, que conserva la actualización de la
  misma jornada y rechaza el traslado entre jornadas; la regresión final pasó.
- La fixture inicial de Copias intentaba poblar las 29 tablas con una única
  jornada simultáneamente operativa y no operativa. Se reemplazó por dos jornadas
  válidas y el recorrido final de Copias pasó 10/10.
- El catálogo QA quedó sincronizado con las clases y métodos instrumentados de
  Stage 2 (`VALID`, 109 clases, 19 grupos), evitando omisiones silenciosas en una
  futura ejecución física.
- Tras el delta de MAIN: 862/862 JVM verdes; 118/118 pruebas físicas dirigidas;
  runners compilados y `lintDebug` verde.
- Joaquín aceptó Stage 2 el 2026-09-15 y autorizó Stage 3 sobre el mismo checkout.
  La aceptación no autoriza commit, firma, publicación ni nuevas pruebas físicas.
