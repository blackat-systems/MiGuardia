# Primera apertura y configuración laboral visible

- Fecha: 2026-08-22
- Rol: dependencia especializada de MAIN 2.0
- Rama: `codex/miguardia-2.0`
- Base verificada: `e6ec74650da175fb2159650fd103252417f29504`

## Resultado

La superficie visible de configuración laboral quedó implementada sin cambiar
dominio, Room, esquemas, migraciones, Gradle, manifiesto, permisos ni datos de
producción.

El recorrido distingue todos los estados de `WorkSetupState` y usa como fuente
de verdad `projectLoadedWorkSetupState(...)`:

- carga y error bloquean el Calendario y ofrecen reintento;
- una instalación realmente nueva muestra primero las cuatro opciones exactas
  de sector y no permite continuar sin selección;
- una raíz V1 mantiene el recorrido heredado sin activación silenciosa;
- `V2NeedsFirstSet` abre el Calendario vacío con una guía visible;
- `V2Ready` habilita `Mi forma de trabajar` y evita exponer las rutas
  estructurales V1 que todavía no interpretan el catálogo V2.

La elección inicial usa `WorkConfigurationRepository.createInitial(...)` con
fecha local, reloj y UUID inyectables, `PendingSetup` y disponibilidad nula. El
primer lugar, su primera revisión de reglas, el tipo habitual y la plantilla
horaria se guardan juntos mediante `createFirstWorkSet(...)`. Un error conserva
el borrador y no deja filas parciales.

Después de guardar se ofrecen exactamente `Volver al Calendario`, `Agregar otro
horario` y `Agregar otro lugar`. Los horarios adicionales usan
`createWorkTemplate(...)`. Los lugares adicionales guardan objetivo, lugar y
primera regla juntos mediante `createWorkPlace(...)`; desde la finalización se
les puede asociar un horario reutilizando un tipo activo.

No se crearon jornadas, recurrencias, extras, disponibilidad, situaciones
especiales, Resumen V2, próximo evento ni notificaciones.

## Archivos

Implementación principal:

- `app/src/main/java/com/blackatsystems/miguardia/ui/worksetup/WorkSetupUiState.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/worksetup/WorkSetupViewModel.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/worksetup/WorkSetupScreens.kt`;
- cableado acotado en `MainActivity.kt` y `MiGuardiaApp.kt`;
- textos del menú y exposición interna del selector de color existente.

Pruebas:

- `app/src/test/java/com/blackatsystems/miguardia/ui/worksetup/WorkSetupCoordinatorTest.kt`;
- `app/src/androidTest/java/com/blackatsystems/miguardia/WorkSetupComposeTest.kt`;
- ajuste del contrato visual heredado en `NavigationDrawerComposeTest.kt`;
- fixture QA V1 en `WorkSetupActivityFixtures.kt` y adopción por
  `MiGuardiaAppTest.kt` y `ManagementActivityRecreationTest.kt`.

## Verificación

Puerta 0 confirmó ruta, rama, HEAD y árbol limpio antes de editar.

Comando final ejecutado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL`.

Comando físico afectado, ejecutado dos veces para confirmar la corrección del
test:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:connectedQaAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.blackatsystems.miguardia.WorkSetupComposeTest,com.blackatsystems.miguardia.NavigationDrawerComposeTest"
```

- `:app:testDebugUnitTest`: 61 pruebas, 0 fallos, 0 errores y 0 omitidas;
- nuevas JVM de configuración laboral: 20;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias existentes;
- compilación Debug: aprobada;
- AndroidTest QA: fuente y APK compilados; 9 pruebas Compose nuevas incluidas;
- primera pasada física: 15/16 aprobadas; una aserción de prueba confundía los
  dos accesos visibles llamados `Mi forma de trabajar`;
- corrección acotada: la prueba identifica ahora el ítem del panel mediante
  `drawer-action-work-setup`;
- segunda pasada física en Samsung `SM-S938B` API 36: 16/16 aprobadas, con 9
  pruebas de `WorkSetupComposeTest` y 7 regresiones vecinas de
  `NavigationDrawerComposeTest`, sin fallos, errores ni omitidas;
- recorrido real QA con datos ficticios: instalación nueva, elección de
  Enfermería, reapertura en Calendario vacío, creación de `Centro ficticio`
  (`CEF`) y turno habitual 08:00–20:00, guardado y segunda reapertura con un
  lugar, un tipo y un horario activos;
- revisión visual real: selector oscuro al 100 %, Calendario vacío, formularios
  de dos pasos, selector de color, finalización y catálogo persistido;
- el paquete QA se desinstaló después de la prueba y la aplicación productiva
  permaneció instalada y sin abrir;
- `git diff --check`: correcto.

## Riesgos informados en el handoff

`MiGuardiaAppTest` y `ManagementActivityRecreationTest`, que lanzan
`MainActivity`, daban por hecho que una base QA vacía abría el Calendario. La
primera apertura correcta ahora muestra el selector. Antes de la ejecución
física global, MAIN debe separar fixtures deterministas para instalación nueva
y V1 migrada; no debe depender de datos residuales del paquete QA. Esa batería
global no se ejecutó en esta dependencia: la evidencia física corresponde a
las 16 pruebas afectadas y al recorrido manual real descrito arriba.

La carga manual V2 sigue siendo la dependencia siguiente. En este punto del
handoff la entrega todavía no se declaraba integrada ni Corte B completo y el
diff quedó deliberadamente sin commit ni push para revisión de MAIN.

## Auditoría e integración de MAIN

MAIN revisó la entrega archivo por archivo y corrigió los bordes concretos
detectados antes de aceptarla:

- catálogo y objetivos ahora se observan dentro de una única carga; un error de
  cualquiera muestra `LoadError` y `retryLoad()` reinicia ambos;
- la proyección completa usa una sola fecha local inyectada y no puede mezclar
  dos días si el reloj cruza medianoche durante la carga;
- la selección de lugar y tipo sobrevive al cierre y reapertura de `Agregar
  otro horario`;
- al entrar en V2 se cierra una edición V1 que hubiera quedado activa; las
  superficies residuales de Perfil, Objetivos, carga, guardias o francos V1 no
  se renderizan ni bloquean navegación o acciones V2, pero sus borradores no se
  borran; el detalle de día V2 ya no ofrece el botón V1 inerte `Editar día`;
- las suites históricas que lanzan `MainActivity` reciben una raíz
  `MIGRATED_V1` determinista antes de abrir la actividad. El fixture sólo puede
  ejecutarse contra `com.blackatsystems.miguardia.qa`, exige Room v7 y no
  reemplaza ni elimina datos.

El alcance final auditado comprende 15 archivos: 8 modificados y 7 nuevos.

Las correcciones sumaron pruebas directas para fallo y reintento de objetivos,
cruce de medianoche, conservación de selección, serialización completa de
`SavedStateHandle`, salida del modo de edición V1, neutralización de todas sus
superficies y borradores residuales y ausencia de la acción inerte en V2.

### Verificación final de MAIN

Comando local completo:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:testDebugUnitTest `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL`, con 287/287 pruebas JVM —65 de aplicación, 217
de dominio y 5 de base de datos—, lint sin errores, APK Debug y APK de pruebas
QA compilados. El coordinador nuevo tiene 24 pruebas JVM.

En el Samsung `SM-S938B`, API 36:

- configuración visible y panel: 18/18;
- suites históricas con fixture V1: 9/9;
- primera corrida global observada por MAIN: 179/180; las otras tres pruebas de
  `NotificationAlarmEndToEndInstrumentedTest` aprobaron y la única falla fue
  `realQaAlarmsDeliverReminderUpdateAtStartAndCancelAtEnd`, con el mensaje
  `QA necesita acceso exacto para este recorrido físico.`;
- repetición excluyendo la clase de cuatro pruebas que contiene ese recorrido
  físico: 176/176;
- Room: 98/98.

MAIN no concedió permisos ni cambió el Samsung. La prueba pendiente de alarma
exacta es una puerta física separada y no una falla del recorrido de
configuración laboral. Al finalizar sólo permanecía instalado
`com.blackatsystems.miguardia`; producción no se abrió ni se modificó.

El esquema Room v7 permaneció byte a byte igual al de la base, con SHA-256
`E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`. Tampoco
cambiaron `core/domain`, `core/database`, Gradle, manifiesto, permisos,
`applicationId`, versión ni SDK.

## Pendientes posteriores

- la carga manual V2 sigue siendo el próximo incremento del Corte B;
- la migración V1 real en el Samsung no se reprodujo en esta integración;
- MAIN preservó, pero no repitió, el recorrido visual manual de primera apertura
  realizado por la dependencia;
- segundo lugar y segundo horario están cubiertos por pruebas automáticas, no
  por un segundo recorrido manual real;
- TalkBack continúa pendiente según la política vigente;
- recurrencias, extras, disponibilidad, situaciones especiales, Resumen V2,
  próximo evento y notificaciones siguen fuera de alcance.

La dependencia queda integrada, pero el Corte B todavía no está completo. No
hubo push ni acciones sobre producción.
