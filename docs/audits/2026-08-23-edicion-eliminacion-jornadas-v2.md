# Edición y eliminación individual de jornadas V2

- Fecha: 2026-08-23
- Rol: auditoría e integración de MAIN 2.0
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `59e3181d04cf55d02f92b4ae3b6c19c04cb3f972`
- Base funcional: `ae576860ced9c51fdd96c3b69c6c050b52c1e0f4`

## Resultado

Una persona con configuración `V2Ready` puede abrir el detalle de un día,
entrar por `Editar este día` y elegir exactamente una jornada. La pantalla la
identifica como `Jornada N de M`, muestra su fotografía histórica y mantiene la
fecha visible e inmutable.

La edición admite una plantilla activa compatible o un cambio exclusivo de
puesto o función. Antes de guardar muestra resumen y advertencias. La
eliminación tiene una confirmación independiente con la fotografía de la
jornada exacta. En ambos casos, las demás jornadas permanecen y el Calendario
observado refleja inmediatamente el resultado.

## Auditoría del candidato

MAIN revisó los dieciséis archivos del handoff y confirmó que no había cambios
en Gradle, manifiesto, permisos, versión, SDK, entidades, DAO, esquema o
migraciones. Luego amplió el diff sólo con dos archivos productivos de
reconciliación de avisos y la prueba existente asociada, necesarios para
demostrar la limpieza visible y del seguimiento después de eliminar.

Tres auditorías independientes de sólo lectura revisaron dominio, Room y
aplicación. Los hallazgos iniciales fueron corregidos y las reauditorías no
dejaron bloqueantes.

## Correcciones de MAIN

- `V2ShiftWriteExpectation` ahora expone una copia realmente inmutable y rechaza
  UUID duplicados;
- `V2NeedsFirstSet` ya no monta ni ofrece el editor;
- una etapa restaurada vuelve a leer las jornadas del día antes de presentarse;
- lecturas suspendidas quedan invalidadas ante cambios de raíz o timeline;
- una escritura iniciada marca el estado de guardado sin ventana de doble toque
  y termina atómicamente antes de descartar una raíz incompatible;
- Atrás del sistema y el botón visible comparten el cierre consciente del
  teclado y el diálogo de descarte;
- la reconciliación de avisos prueba por UUID que una jornada eliminada deja de
  ser visible o restaurable sin afectar su compañera;
- Room cubre un conflicto que cambia solamente la fotografía histórica;
- la matriz de eliminación verifica que `F/?`, feriado, vacaciones y carpeta
  médica ajenos permanecen. El fixture fue corregido para no crear una carpeta
  médica sobre la misma fecha de vacaciones, combinación que el propio dominio
  rechaza.

## Persistencia y concurrencia

`Shift` y `ShiftWorkSnapshot` se comparan y mutan como un par. La edición valida
también la ocupación del vecindario observado para superposición y descanso. Un
cambio en cualquiera de esas precondiciones produce
`ConflictingLocalWriteException` antes de escribir.

La edición exclusiva del puesto puede conservar fuentes archivadas sólo si no
cambia ningún otro dato histórico. Elegir plantilla exige una fuente activa y
coherente con la configuración de la fecha. `updatedAt` se normaliza a
milisegundos Room y avanza estrictamente.

La decisión durable está en
`docs/adr/0025-cas-par-historico-edicion-eliminacion-v2.md`.

## Verificación local

La batería contractual completa se ejecutó con `--rerun-tasks` y un solo worker:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Resultado: `BUILD SUCCESSFUL`.

- dominio: 226/226 pruebas JVM;
- base de datos: 5/5 pruebas JVM;
- aplicación: 116/116 pruebas JVM;
- total JVM: 347, sin fallos, errores ni omitidas;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias heredadas;
- APK Debug, APK AndroidTest QA y APK AndroidTest Room: compilados;
- después de las últimas correcciones se repitieron las 116 pruebas de
  aplicación y la compilación de ambas superficies AndroidTest: verde;
- `git diff --check`: correcto.

## Verificación física

Dispositivo autorizado: Samsung `SM-S938B`, API 36, serial `R5CY529W6PL`.
Se usaron únicamente paquetes QA y datos ficticios.

- 87/87 pruebas de Compose y superficies vecinas: edición, Calendario,
  configuración inicial, carga manual, gestión, navegación, tema, adaptación y
  zoom interno;
- 2/2 pruebas de recreación con actividad;
- 1/1 recorrido integral de edición y eliminación;
- 1/1 recorrido integral de carga manual;
- 22/22 pruebas de persistencia Room V2;
- total: 113/113 pruebas instrumentadas únicas;
- el recorrido integral de edición se ejecutó una segunda vez directamente para
  dejar un fixture ficticio destinado a la revisión visual: también verde.

La revisión visual directa mostró, en oscuro y retrato:

1. Calendario actualizado de dos jornadas a una;
2. detalle del día con la jornada restante;
3. `Jornada 1 de 1` y su lugar, tipo, horario, color y puesto;
4. acciones separadas `Editar jornada` y `Eliminar jornada`;
5. fecha fija y fotografía original en el formulario;
6. plantillas activas y puesto opcional;
7. confirmación de eliminación que aclara que las demás jornadas se conservan.

Claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % quedaron
cubiertos por instrumentación física. No se consultaron ni modificaron
`font_scale`, densidad o tamaño visual del sistema. La prueba física de alarmas
exactas permaneció fuera del alcance.

## Room y superficies protegidas

Room continúa en versión 7. El esquema
`core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaDatabase/7.json`
conserva el SHA-256
`E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`.

No cambiaron entidades, DAO, esquema, migraciones, DataStore, Gradle,
manifiesto, permisos, `applicationId`, versión ni SDK. No se implementaron
recurrencias, edición masiva, horario real, extras, disponibilidad, situaciones
especiales, Resumen V2, Calendario final, widget, informes, copias o bloqueo.

## Seguridad del dispositivo

Al finalizar quedaron ausentes:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test`.

Ningún comando de MAIN apuntó al paquete de producción. No se tocaron alarmas
exactas, permisos especiales ni ajustes visuales del sistema.

## Pendientes y siguiente bloque

- API 26 física queda pendiente porque no hay un dispositivo disponible;
- el modo V1 residual continúa como deuda técnica y es el siguiente bloque
  recomendado antes de ampliar nuevamente Room;
- no existe todavía un prompt habilitado para esa limpieza;
- recurrencias y edición masiva continúan fuera de este incremento.

No hubo push, tag, merge, rebase, reset ni acciones sobre `main` o producción.
