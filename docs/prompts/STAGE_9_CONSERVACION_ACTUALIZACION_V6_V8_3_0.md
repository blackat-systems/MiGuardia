# Stage 9 Conservación verificable de datos V6 a V8

## TASK

Corregir y validar localmente las fixtures y comprobaciones de conservación de
datos para una futura actualización MiGuardia 2.0.2/Room V6 a la candidata
Room V8. Entregar un recorrido de prueba coherente y reutilizable: origen y
destino explícitos, metadatos fiables, comparación lógica sin pérdidas,
regresiones adversas y handoff. No modificar el producto ni ejecutar Android.

## CONTEXT

El 2026-10-21 Joaquín indicó directamente en MAIN: «samsung libre, contiua con el
contrato y luego ejecutalo». MAIN despacha este bloque local en un chat propio.
La disponibilidad del Samsung no habilita ADB, instalación ni reemplazo de datos:
el ensayo físico se mantiene en contrato separado y pendiente de permiso específico.
El estado autorizado único vive en `docs/STATUS.md`.

Stage 8 ya está auditado localmente, sin optimización productiva. Su causa de
rendimiento exige medición Android y permanece pendiente. Este bloque es
independiente: resuelve un desajuste ejecutable de pruebas, no añade otra etapa
administrativa ni supone que una hoja de ruta global 3.0 esté aceptada.

Hallazgo contrastado por MAIN y revisor de sólo lectura, antes de ejecutar:

- `PublicUpgradeBackupFixtureTest.kt`, líneas 486–507, genera las 28 tablas de
  `MiGuardiaBackupSchemaV7`, pero omite versión e identidad del snapshot.
- `BackupModels.kt`, líneas 11–18 y 79–83, aplica por defecto versión/identidad V8.
- La línea 116 de esa prueba exige V7 mediante `MiGuardiaBackupSchemaV7.requireValid`;
  `BackupSchema.kt`, líneas 125–130 y 377–381, rechaza la versión/identidad V8.
- El recibo que produciría declara `room_version = 6`, línea 192. Conviven tres
  versiones incompatibles en el mismo ensayo.

Es un defecto determinable por código de una fixture opt-in. No se ejecutó el
ensayo ni se demuestra pérdida de datos de la app. No se debe resolver cambiando
simplemente 28 por 29 ni renombrando el ensayo antiguo como si probara V6→V8.

## INPUTS

Repo: `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`.
Contenedor: su directorio padre. Host observado: `DESKTOP-UF10GUI`.
Rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream
`origin/codex/miguardia-3.0`, remoto MiGuardia. `.git` propio, un worktree,
contenedor sin Git, staging vacío. No absorber como propio el checkout acumulado.

MAIN contrastó las 982 rutas de
`.local-evidence/stage8/exit-r1-attribution/repository.json`: ninguna diferencia.
El despacho posterior modifica sólo documentación y agrega los dos contratos.
Registrar entrada propia DESPUÉS del despacho y detenerse ante otro delta sin dueño.
Preservar la eliminación heredada que ya tiene hash nulo y toda evidencia anterior.

Lecturas iniciales:

1. `../AGENTS.md`, `AGENTS.md`, `docs/STATUS.md` y este contrato.
2. `docs/LECTURAS_POR_TAREA.md`, `docs/WORKFLOW.md`, `docs/VALIDACION.md`.
3. `docs/adr/0035-copias-locales-versionadas-y-restauracion-atomica.md`, su
   alcance vigente y sucesores pertinentes; contratos de versiones reales en código.
4. `docs/audits/2026-10-21-stage8-revision-main.md`: límites de la base recibida.

Fuentes principales:

- `app/src/androidTest/java/com/blackatsystems/miguardia/upgrade/PublicUpgradeBackupFixtureTest.kt`.
- `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/backup/BackupModels.kt`
  y `BackupSchema.kt`, exclusivamente como contrato de sólo lectura.
- `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/6.json`,
  `7.json`, `8.json` y migraciones vigentes, en sólo lectura.
- `core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/V2DatabaseMigrationInstrumentedTest.kt`
  y `BackupDatabaseGatewayInstrumentedTest.kt`: reutilizar garantías existentes.
- `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/backup/BackupContractTest.kt`
  y pruebas de preferencias/archivos pertinentes, según el delta.
- `tools/release/upgrade_snapshot.py` y `tools/release/tests/test_upgrade_snapshot.py`.

`upgrade_snapshot.py` está diseñado para V6 exacta, 27 tablas y comparación de
igualdad entre dos V6. No sirve como prueba V6→V8. Su limitación histórica es
legítima: conservar ese comportamiento y sus rechazos; no ampliarlo silenciosamente.

Entorno por sesión:

```powershell
. .\.local-evidence\structure\20261020\Enter-MiGuardiaEnvironment.ps1
```

Python 3.12.14, JBR 25.0.2 y SDK 37 ya existen. Usar evidencia nueva y rutas cortas
para no exceder límites Windows. El cierre Stage 8 necesitó propiedades JVM de
sesión `org.gradle.caching=false` y `org.gradle.configuration-cache=false` por
resultados lint reutilizados inconsistentes. Revisar su auditoría si reaparece;
no borrar cachés, instalar herramientas ni cambiar dependencias por rutina.

## OUTPUT

1. Reproducción local adversa del desajuste y prueba de regresión que invoque
   el constructor/validador real, no sólo una búsqueda de texto en Kotlin.
2. Fixtures con versión, identidad, tablas, datos y recibo coherentes. Separar
   claramente origen V6, destino V8 y casos legados V5/V7. Preservar como historia
   el antiguo ensayo con sus guardas; no reetiquetar sus resultados.
3. Comparación explícita V6→V8 que valide las fuentes y pruebe conservación de
   identidades, filas, valores y archivos correspondientes. Cualquier extensión
   de herramienta debe ser modo/entrada nuevo explícito o herramienta separada,
   sin cambiar el contrato V6 existente ni aceptar esquemas sólo por su número.
4. Regresiones de pérdida, mutación, esquema/identidad incoherentes, asociaciones,
   preferencias y entradas incompletas; recibos con alcance y procedencia reales.
5. Handoff `HANDOFF_STAGE_9_A_MAIN_3_0.md` dentro de `docs/`, auditoría fechada
   dentro de `docs/audits/`, y evidencia nueva bajo `.local-evidence/stage9/`:
   entrada, originales propios, delta, comandos, hashes, adversos y relevo.
6. Plan físico posterior preciso, SIN ejecutarlo. Indicar qué parte depende de
   instalación, Room real, SAF/fotos, preferencias Android y permiso específico.

## SCOPE

Una capacidad: comprobación de conservación de datos al evolucionar V6→V8.

- Fixtures y pruebas relacionadas en `app/src/androidTest/`, `core/domain/src/test/`
  y `core/database/src/androidTest/`; soporte compartido exclusivamente test-only.
- Si compartir el constructor puro entre pruebas JVM/Android requiere conectar
  un sourceSet de pruebas existente, se permite el cambio mínimo de ese cableado
  en Gradle. Justificarlo; no tocar producción, plugins, dependencias, variantes,
  permisos, versionado ni firma. No duplicar una fórmula sólo para que el test pase.
- Herramientas de comparación y sus tests en `tools/release/` y sus consumidores
  directos cuando exista dependencia demostrada. Código productivo es sólo lectura.
- Catálogo QA si se agregan o cambian clases instrumentadas, preservando guardas,
  aislamiento y permisos explícitos. Compilar no habilita ejecutar esos grupos.
- El especialista escribe implementación de pruebas/herramientas y sus dos
  documentos. MAIN reserva STATUS, índices, contratos y la revisión independiente.
  Un escritor por archivo y una sola ejecución Gradle a la vez.

El especialista decide la estructura mínima, con estos invariantes:

- Snapshot V6 explícito validado por el contrato V6; snapshot V8 explícito por
  el contrato V8. Nunca confiar en defaults de versión para fabricar una copia vieja.
- Diferenciar migración de base, lectura/adaptación de Copias y comparación de
  snapshots. Un comparador SQLite host no demuestra que Room Android migró.
- V6→V7 agrega adjuntos de nota y V7→V8 situaciones. En una migración pura del
  origen V6 esas tablas nuevas comienzan vacías; no inventar imágenes ni situaciones.
  Derivar columnas/valores esperados de esquemas/migraciones vigentes, no de otra fórmula.
- Preservar los registros y campos anteriores con sus relaciones; cualquier
  diferencia permitida debe estar enumerada y sustentada, nunca omitida globalmente.
- Conservar los lectores V5/V6/V7/V8 existentes, fotos, notas y preferencias
  portables admitidas: 17 en la referencia pública, incluido zoom legado en Copias
  sin reintroducir zoom de UI. No fabricar valores para simular conservación.
- Separar identidad lógica de instalación. No modificar versionCode, firma ni
  applicationId para convertir una prueba local en una actualización instalable.

## DEPENDENCIES

La base Stage 8 está congelada y auditada. Este bloque NO necesita ni corrige
la medición física de rendimiento. No depende de Samsung para reproducir el
desajuste lógico, probar comparadores y compilar consumidores.

La actualización instalada desde 2.0.2 y la restauración Android completa quedan
pendientes de un ensayo específico posterior. No sustituirlas por SQLite/Python,
dobles JVM o una APK compilada. Certificado y distribución son puertas aparte.

## DO NOT

- No código productivo, cambios funcionales/UI, migraciones/esquemas, cifrado,
  seguridad, credenciales, permisos, formato de Copias o dependencias nuevas.
- No ADB, Samsung, emulador, instalación, app pública, datos personales o copias
  reales. No saltar guardas API/AVD/consentimiento del ensayo antiguo.
- No ramas/worktrees, staging, commits, push, tags, firma, AAB, website o publicación.
- No borrar, mover ni sobrescribir evidencia o fuentes recibidas. No generar claves.
- No optimizar Resumen/guardado sin evidencia Android ni reabrir Stage 6.
- No relajar el comparador V6, los validadores reales, hashes, identidades,
  relaciones o aserciones para obtener verde. No usar identidad V8 con tablas V7.
- No declarar que una fixture validada o una comparación lógica prueban upgrade,
  restore, funcionamiento OEM o conservación real en un teléfono.
- No crear otros chats de implementación ni cambiar modelo/esfuerzo/ajustes.

## VALIDATION

- Primero demostrar el rechazo actual por versión/identidad y conservar el adverso.
- Host: tests del comparador actual y del nuevo modo, casos negativos de filas
  perdidas/mutadas, datos/tipos nulos, tablas/columnas/índices/claves según su
  contrato, UUID/relaciones, archivos ausentes o alterados, preferencias e insumos
  incompletos. Reutilizar tests útiles y no imprimir contenido privado.
- JVM: validadores/codecs reales y fixtures coherentes por versión; comparar
  adaptación con resultados esperados sustentados. Lectura y preview sin mutación.
- `python tools/verify.py --profile local --fresh-tests` al cierre si cambia Kotlin
  de prueba; perfil `build` para compilar AndroidTest/consumidores afectados.
  Iterar primero con focalizadas y no reconstruir todo sin razón.
- Tests Python pertinentes si cambia una herramienta; `docs_check.py`, catálogo
  QA y `git diff --check`. Los planes QA se muestran SIN `--execute`.
- Sellar insumos/outputs, separar tests ejecutados/reutilizados y revisar delta
  propio. MAIN audita después del cese de escrituras/Gradle, sin repetir baterías
  válidas por rutina. Una decisión material detiene sólo la parte dependiente.

## DONE WHEN

MAIN recibe una capacidad local utilizable: desajuste reproducido y corregido,
fixtures por versión verificadas, comparación V6→V8 explícita con rechazo de
pérdidas, modo V6 anterior conservado, regresiones pertinentes verdes y AndroidTest
compilado. El plan Android restante es autosuficiente y está marcado pendiente.
Entregar archivos/insumos/hashes y confirmar que cesaron escrituras y Gradle.
No declarar actualización real ni cierre global de MiGuardia.

## Continuidad después de la entrega

MAIN recibe y audita en este mismo ciclo. Si hay fallos concretos del contrato,
los devuelve al MISMO chat. Sólo después de revisar el resultado puede elegir
otro bloque local real ya comprendido por el alcance: debe tener necesidad,
entrada estable y salida verificable. No crear un stage por numeración ni una
supervisión que consulte indefinidamente sin trabajo activo.

Rendimiento Stage 8 requiere su ensayo Android separado; aceptación visual de
Vigilia requiere a Joaquín; instalación de actualización/restauración y distribución
conservan sus permisos propios. Si no queda trabajo local independiente, dejar
el estado y contrato de reanudación listos y explicar el gate exacto.
