# Carga manual de jornadas V2

- Fecha: 2026-08-23
- Rol: auditoría e integración de MAIN 2.0
- Rama: `codex/miguardia-2.0`
- Base de integración: `ca029d15d8a000002006bcd887a458c627d06e38`

## Resultado

La carga manual V2 quedó integrada como un recorrido explícito sobre la única
grilla mensual. Una configuración `V2Ready` ofrece `Cargar jornadas`; una raíz
V1 o una configuración todavía incompleta no la ofrecen.

El recorrido permite elegir una o varias fechas del mismo mes, reutilizar un
lugar, tipo y horario activos, conservar un puesto opcional y revisar antes de
guardar. Resuelve configuración y reglas por cada fecha, admite retrocarga
consciente `NEW_V2`, trata fechas ocupadas y confirma superposición, descanso
corto y carpeta médica. Cada jornada se persiste junto con su fotografía
laboral mediante `V2ShiftRepository.applyV2Batch(...)`.

La selección, el formulario y la etapa sobreviven a recreación mediante
`SavedStateHandle`. Un error conserva el borrador. El éxito se consume una sola
vez y vuelve al Calendario observado, sin una segunda fuente de jornadas.

## Correcciones de integración de MAIN

MAIN auditó cada hunk del candidato y corrigió estos bordes antes de aceptarlo:

- carga y error del Calendario bloquean grilla, navegación y confirmación;
- varias jornadas en un día muestran únicamente `N turnos`, sin destacar una
  arbitrariamente;
- selección, color y tipo poseen texto y semántica explícitos;
- V2 usa `jornada`, mientras V1 conserva `guardia` y `seleccionado para editar`;
- una salida exitosa se consume y no vuelve a navegar tras recreación;
- una prueba real de `MainActivity + Room` cubre raíz `NEW_V2`, recreación,
  rotación, guardado de `Shift + ShiftWorkSnapshot` y reapertura;
- una expectativa de ocupación obligatoria se compara dentro de la transacción
  Room antes de cualquier escritura. Un cambio concurrente conserva el borrador
  pero obliga a revisar de nuevo.

Una auditoría independiente de sólo lectura no encontró defectos bloqueantes.
Recomendó como endurecimiento futuro ampliar casos Room individuales de
reemplazo vencido, modificación del mismo UUID y cambios fuera de la ventana;
el contrato actual los resuelve, aunque no poseen una prueba dedicada por caso.

## Verificación local

Comando completo ejecutado con repetición de tareas:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL`.

- dominio: 219/219 pruebas JVM;
- base de datos: 5/5 pruebas JVM;
- aplicación: 93/93 pruebas JVM, incluidas 27 del coordinador nuevo;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias heredadas;
- APK Debug: compilado;
- fuente y APK de AndroidTest QA: compilados;
- repetición final de lint y ambos APK después de los últimos ajustes:
  `BUILD SUCCESSFUL`;
- `git diff --check`: correcto.

## Verificación física

Dispositivo autorizado: Samsung `SM-S938B`, API 36. Se usaron únicamente el
paquete QA y datos ficticios.

- persistencia Room V2: 14/14;
- suites afectadas de aplicación: 60/60 —16 de carga V2, 11 de configuración,
  17 de Calendario, 2 de recreación, 7 de aplicación y 7 de navegación—;
- recorrido integral separado `MainActivity + Room`: 1/1 desde una instalación
  QA limpia;
- el recorrido integral conservó el borrador al recrear y rotar, guardó la
  jornada y su fotografía, cerró y reabrió la aplicación y mostró la jornada
  persistida;
- QA manual ficticio cubrió una y varias fechas, retrocarga cancelada y
  confirmada, ocupadas conservadas, segunda jornada, superposición, persistencia,
  claro/oscuro y zoom interno 100 %, 150 % y 200 %;
- la revisión visual final mostró la jornada creada sin cortes ni solapamientos
  de interfaz.

Las suites `NEW_V2` y `MIGRATED_V1` se ejecutaron con instalaciones QA limpias
y separadas para no reemplazar fixtures entre sí. API 26 física no estaba
disponible y queda pendiente explícita.

Al finalizar se desinstalaron `com.blackatsystems.miguardia.qa.test` y
`com.blackatsystems.miguardia.qa`. Producción permaneció únicamente en el
usuario 10 con `stopped=true` y `notLaunched=true`; no se abrió ni modificó.

## Superficies preservadas

Room continúa en versión 7. El esquema `7.json` conserva el SHA-256
`E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`.
No cambiaron entidades, esquemas, migraciones, Gradle, manifiesto, permisos,
`applicationId`, versión ni SDK.

No se implementaron edición o eliminación de jornadas V2, recurrencias,
horario real, extras, disponibilidad, guardias pasivas, situaciones especiales,
Resumen V2, adaptación de próximo evento, notificaciones, widget, informes,
copias ni bloqueo. El motor heredado de próximo evento se conserva sin adaptar,
según el índice canónico.

## Próximo paso

El siguiente bloque es activar MiGuardia 2.0 conscientemente desde una
instalación anterior y permitir cambiar de rubro desde una fecha. MAIN debe
crear primero su prompt durable y abrir una sola dependencia implementadora
después de comprobar que este checkout quedó limpio.

No hubo push, tag, merge, rebase, reset ni acciones sobre `main` o producción.
