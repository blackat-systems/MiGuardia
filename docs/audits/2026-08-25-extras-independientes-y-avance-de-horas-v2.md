# Auditoría MAIN — extras independientes y avance de horas V2

- Fecha: 2026-08-25
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `6fb04c8ff34eec2c454277dfb086664349a9051b`
- Upstream de entrada: `0364b835d07883708e137a7057f235fad9113b38`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró localmente el registro, consulta, corrección y eliminación
de trabajos extra que no dependen de una jornada. También integró la elección
consciente de la referencia de horas y su fecha de reinicio, más una vista de
avance que distingue habitual, extra, pendiente, faltante y superación sin
inventar metas ni falsos ceros.

El bloque permite:

- elegir referencia fija o variable por mes, semana o ciclo;
- comenzar hoy, en el próximo límite natural o en una fecha elegida;
- reiniciar conscientemente el mismo valor sin reescribir el pasado;
- registrar extras independientes con intervalo, lugar, tipo, clase, color y
  puesto históricos;
- verlos en la única grilla mensual y gestionarlos desde el detalle del día;
- recalcular el avance de forma reactiva usando horario real cuando existe;
- conservar borradores y revisiones exactas ante recreación;
- detectar conflictos concurrentes antes de cualquier escritura.

No se implementaron disponibilidad, situaciones especiales, Resumen final,
remuneraciones ni otro bloque posterior.

## Hallazgos y correcciones de MAIN

Tres auditorías independientes de sólo lectura revisaron dominio, persistencia
e interfaz. MAIN corrigió, entre otros puntos:

- exigir la configuración laboral vigente en la fecha dueña del extra;
- impedir que cambiar una referencia altere sector o disponibilidad;
- conservar automáticamente la fotografía histórica cuando las fuentes son las
  mismas, aunque luego hayan sido renombradas o archivadas;
- volver inmutables las expectativas de ocupación y protección usadas por CAS;
- hacer atómica la creación o corrección de metas por período;
- proteger la revisión visible y el registro abierto frente a cambios
  concurrentes y cruces de fecha o período;
- bloquear navegación y edición mientras una escritura está en curso;
- mostrar período, cantidad, tipo, color y confirmaciones exactas antes de
  guardar;
- respetar `showDedicatedSummary` y conservar mensajes de error o conflicto;
- aplicar correctamente intervalos `[inicio, fin)`, cruces de medianoche y
  protecciones en todos los días alcanzados;
- advertir solapamientos y protecciones sólo para el registro que realmente se
  reemplaza, sin bloquear eliminaciones por datos ajenos.

La primera instrumentación de Room encontró tres defectos en las propias
pruebas: un método no devolvía `Unit`, una comparación usaba identidad en vez de
estructura y un fixture todavía esperaba Room v3. Se corrigieron las pruebas y
la repetición quedó 57/57.

La primera batería física de aplicación quedó 84/89. Las cinco fallas fueron de
automatización: formato decimal no argentino, coincidencia de texto demasiado
estricta, un Atrás que cerraba la actividad y dos esperas UIAutomator agotadas
durante la corrida cargada. El bloque reducido pasó 10/10 y la repetición
conjunta final quedó 89/89, sin cambios de producto por esas cinco fallas.

## Persistencia

`MiGuardiaV2Database` pasa de versión 3 a 4 mediante migración explícita
`3→4`. Conserva las 25 tablas anteriores y agrega
`independent_extra_work_records`; `work_configuration_revisions` incorpora el
marcador nullable `hoursReferenceStartedOn`. La base queda con 26 tablas.

Esquemas verificados:

- `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- `3.json`:
  `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`;
- `4.json`:
  `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B`.

Los esquemas 1 a 3 permanecen byte a byte iguales a sus hashes cerrados. No se
agregó migración destructiva, acceso Room en el hilo principal ni conexión con
`miguardia.db`.

## Validación local

Comando contractual ejecutado desde cero con `--rerun-tasks` y
`--max-workers=1`:

```text
:core:domain:test
:core:database:testDebugUnitTest
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleQa
:app:assembleQaAndroidTest
:core:database:assembleDebugAndroidTest
```

Resultado: `BUILD SUCCESSFUL` en 7 min 46 s, 238/238 tareas ejecutadas.

- dominio JVM: 247/247;
- base JVM: 10/10;
- aplicación JVM: 152/152;
- total JVM: 409/409, sin fallos, errores ni omisiones;
- lint: 0 errores y 4 avisos de versiones disponibles;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- `git diff --check`: correcto.

## Validación Android autorizada

### Samsung SM-S938B — API 36

- Room, migración, persistencia y rollback: 57/57;
- aplicación afectada y regresiones: 89/89;
- alta, corrección y eliminación de extras independientes;
- configuración, revisión, recreación y persistencia del reinicio futuro;
- carga manual, recurrencias, horario real y edición individual preservados;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200 cubiertos por
  instrumentación física.

No se consultaron ni modificaron `font_scale`, densidad o tamaño visual del
sistema. API 26 no fue autorizada para este bloque y permanece pendiente como
evidencia separada, no como bloqueo del checkpoint local.

## Seguridad del dispositivo y Git

Se usaron exclusivamente:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test`.

Los tres paquetes quedaron ausentes al finalizar. Producción no fue instalada,
abierta, consultada, limpiada, modificada ni desinstalada. No se cambiaron
permisos, orientación permanente ni ajustes visuales del Samsung.

Antes del checkpoint no había archivos staged. No hubo push, tag, merge,
rebase, reset, descarte ni cambios en `main`, `origin/main` o `v1.0.0`.

## Pendiente separado

El próximo bloque previsto es disponibilidad pasiva, situaciones especiales y
la consolidación final del motor de horas y cumplimiento. Todavía no tiene un
prompt habilitado ni una tarea abierta; MAIN lo preparará sólo cuando Joaquin lo
pida.
