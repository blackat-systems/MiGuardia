# Auditoría MAIN: retiro del modo V1 y primera base Room V2

- Fecha: 2026-08-23
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD recibido: `a306221efa850e026a4009d3c8a8640c0ed263ea`
- Upstream al iniciar: `836d908f54a407c48cc9e3c27c9587c6dc908ca2`
- Resultado: **ACEPTADO E INTEGRADO LOCALMENTE POR MAIN**
- Publicación: no realizada

## Objetivo auditado

Dejar MiGuardia con una única experiencia funcional V2 y fijar su primera base
Room propia, sin migrar, abrir, copiar, transformar ni borrar datos de la prueba
MiGuardia 1.0.

La base de código histórica se conserva en Git. El producto nuevo comienza con
datos vacíos y ofrece exactamente Vigilancia privada, Policía, Enfermería y
Medicina.

## Puerta 0

MAIN volvió a verificar en vivo:

- ruta, rama y HEAD correctos;
- upstream conocido y rama siete commits adelante;
- tag anotado `v1.0.0` en `227c931ff8e381ab00120ad61b1c86ac71c03e46`;
- `v1.0.0^{}`, `main` y `origin/main` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- remoto `https://github.com/blackat-systems/MiGuardia.git` y autor
  `joaquin <blackat.systems@gmail.com>`;
- índice vacío, sin staged;
- worktrees históricos presentes e intactos;
- ningún commit, push, tag, merge, rebase, reset o descarte durante la
  auditoría.

El candidato y las correcciones de MAIN sumaron, antes de documentar, 164
rutas: 95 modificadas, 57 eliminadas y 12 nuevas. El diff rastreado quedó en
`+2776/-26119`. Cada eliminación se contrastó con el prompt, sus reemplazos V2
y las pruebas vigentes.

## Resultado funcional

- Una instalación limpia abre el selector de cuatro rubros exactos.
- Sólo existen `Loading`, `LoadError`, `FreshInstall`, `V2NeedsFirstSet` y
  `V2Ready`.
- No quedan activación, adopción, origen `MIGRATED_V1`, procedencias V1 ni
  rutas de compatibilidad.
- Configuración laboral, primer conjunto, carga manual múltiple, retrocarga,
  edición y eliminación exacta continúan funcionando.
- Calendario, `F/?`, Feriados, Notas, Vacaciones, carpetas médicas, fotos,
  próximo evento, notificaciones, clima y apariencia permanecen como
  capacidades comunes V2.
- Perfil, Resumen, CRUD estructural de objetivos/horarios, guardias/francos y
  Novedades V1 dejaron de ser alcanzables.
- `GuardProfileStore` conserva únicamente el nombre o apodo opcional y no se
  monta en el recorrido actual.

## Persistencia

La única base activa es:

- clase: `MiGuardiaV2Database`;
- archivo: `miguardia-v2.db`;
- versión: 1;
- identity hash: `d583ce68e247cba7574a9e3b25b29e69`;
- esquema:
  `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/1.json`;
- SHA-256:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`.

Contiene exactamente 19 tablas:

1. `objectives`;
2. `shifts`;
3. `shift_work_snapshots`;
4. `explicit_day_statuses`;
5. `medical_leaves`;
6. `holidays`;
7. `shift_notes`;
8. `vacations`;
9. `schedule_photos`;
10. `shift_notification_configs`;
11. `shift_notification_reminders`;
12. `work_configuration_roots`;
13. `per_period_hours_definitions`;
14. `work_configuration_revisions`;
15. `per_period_hours_values`;
16. `work_places`;
17. `work_types`;
18. `work_templates`;
19. `workplace_rule_revisions`.

La comparación programática contra Room v7 dio cero diferencias fuera de las
tres tablas, tres columnas y nulabilidad autorizadas por ADR 0026. No hay
fallback destructivo, consultas Room en el hilo principal ni migraciones desde
la cadena histórica.

Cada jornada exige un `sourceObjectiveId` y exactamente una fotografía
`ShiftWorkSnapshot`. Las escrituras estructurales pasan por
`V2ShiftRepository` y se ejecutan atómicamente. La integridad global también
audita objetivos, catálogo, revisión laboral aplicable y referencias
históricas.

Una prueba instrumentada crea un archivo testigo llamado `miguardia.db`,
compara su SHA-256 antes y después de operar la base V2 y ejecuta
`integrity_check` y `foreign_key_check`.

## Correcciones de MAIN

La auditoría no aceptó el handoff de forma automática. MAIN corrigió:

- Feriados y Notas, que habían quedado retirados junto con superficies V1 pese
  a ser capacidades comunes expresamente preservadas;
- conflictos, reintento, doble toque, recreación y borradores de esas
  superficies;
- validación de objetivos y fotografías históricas en el chequeo global V2;
- una expectativa CAS mutable y la exposición de un escritor de jornadas que
  podía evitar la frontera transaccional;
- `REPLACE` de SQLite, reemplazado por `@Upsert` para no borrar y recrear filas
  referenciadas;
- el montaje innecesario del almacén de Perfil en el arranque;
- fixtures físicos deterministas, con limpieza limitada al paquete QA exacto;
- cobertura de esquema, selector RGB, calendario V2 listo y recreación;
- compatibilidad de pruebas Compose y NotificationManager en API 26.

Una auditoría independiente de sólo lectura revisó alcance, contratos,
eliminaciones y afirmaciones. Su cierre no encontró defectos P0 o P1.

## Validación local

Comando contractual final:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Resultado:

- `BUILD SUCCESSFUL`;
- dominio JVM: 168/168;
- base JVM: 5/5;
- aplicación JVM: 110/110;
- total JVM: 283, sin fallos, errores ni omitidas;
- lint: 0 errores y 2 avisos de actualización ya conocidos;
- APK Debug y QA: compilados;
- AndroidTest de aplicación y base: compilados;
- `git diff --check`: correcto.

También se ejecutó una construcción desde salida limpia antes del cierre, con
238 tareas verdes.

## Samsung API 36

- Dispositivo: Samsung `SM-S938B`, serial `R5CY529W6PL`, API 36.
- Aplicación: runner verde `OK (148 tests)`.
- Resultado efectivo: 147 aprobadas y una omitida conscientemente porque el
  teléfono no tiene acceso especial a alarmas exactas y este bloque prohíbe
  habilitarlo.
- Room: 61/61.
- Revisión visual directa: primera apertura, cuatro rubros, Calendario vacío,
  menú exclusivamente V2, persistencia tras reapertura, claro/oscuro, zoom
  interno 200 % y retrato/paisaje.
- La rotación quedó restaurada en `accelerometer_rotation=1` y
  `user_rotation=0`.

No se abrió ni modificó producción. Al finalizar quedaron ausentes
`com.blackatsystems.miguardia.qa`,
`com.blackatsystems.miguardia.qa.test` y
`com.blackatsystems.miguardia.core.database.test`.

## Emulador API 26

- AVD controlado: `MiGuardia_API_26`, Android 8.0, API 26, Google APIs x86_64.
- Aplicación: 148/148.
- Room: 61/61.
- Revisión visual directa desde instalación limpia: cuatro rubros exactos,
  sectores Enfermería y Medicina separados y `Continuar` deshabilitado sin
  selección.
- Los tres paquetes QA quedaron ausentes y el emulador fue apagado.

El AVD y los componentes del SDK quedaron instalados para futuras pruebas; no
son archivos del repositorio.

## Seguridad y límites

- No cambiaron Gradle, dependencias, manifiesto, permisos, `applicationId`,
  versión o SDK del proyecto.
- No se agregó nube, cuenta, sincronización, telemetría ni dato real.
- No se consultó ni modificó `font_scale`, densidad o tamaño visual del
  sistema.
- No se habilitaron permisos especiales ni se ejecutó QA física de alarmas
  exactas.
- No se encontraron binarios generados, secretos ni archivos sensibles en el
  diff.
- `main`, `origin/main` y `v1.0.0` permanecen intactos.

## Riesgos y pendientes

- API 37 continúa pendiente para la auditoría previa al candidato final, no
  para este bloque.
- El recorrido físico real de alarmas exactas requiere autorización separada.
- Recurrencias, extras, guardias pasivas, disponibilidad, situaciones
  especiales, Resumen V2 y la adaptación final de próximo evento y
  notificaciones siguen en la hoja de ruta.

## Próximo paso recomendado

Preparar, sólo cuando Joaquin lo pida, el prompt de **recurrencias y edición de
una fecha o de todo lo futuro**. No existe todavía otra dependencia habilitada.
