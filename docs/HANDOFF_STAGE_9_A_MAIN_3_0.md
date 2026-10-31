# Stage 9 — conservación local V6 a V8

## Relevo R1 — adversos independientes

MAIN encontró que dos regresiones compartían estado alterado entre adversos:
el cambio INTEGER enmascaraba el REAL posterior y el cuerpo de nota restaurado
como `synthetic` seguía siendo distinto de su original Unicode, enmascarando
timestamp y NULL→texto. Se corrigieron los tests, sin cambiar el comparador.

Cada adverso de esas secuencias y sus equivalentes de base, fotos y preferencias
ahora exige comparación positiva antes de modificar el insumo, restaura los
bytes originales en `finally` y vuelve a exigir conservación después. Los
recibos JSON independientes y el modo histórico no necesitaron cambios.

R1 ejecutó sólo Python: 54/54 verdes (48 nuevas + seis históricas). Tres dobles
que ignoran REAL, NULL→texto o timestamps escapaban al test pre-R1 y ahora son
detectados de manera independiente. Son probes de fuerza de las regresiones,
no defectos nuevos del comparador ni pruebas Android.

La evidencia vigente de la corrección está en `.local-evidence/stage9/r1/`:
`entry/repository.json`, originales de las tres rutas, `pre-masking.json`,
`post-masking.json`, `validation.json`, `exit-checks.json`,
`exit/repository.json`, `final-receipt.json` y `evidence-manifest.json`.
R1 cambia sólo el test Python y estas dos entregas documentales. Sus originales
y los 1.207 archivos del sello pre-R1 permanecen intactos. Los recibos raíz
anteriores conservan su validez histórica y no se sobrescriben.

Se reutilizan, con insumos idénticos, las 952 JVM, el build y los recorridos
host anteriores: no se ejecutó Gradle en R1. MAIN recibió la corrección para
su revisión; STATUS y los límites físicos conservan su dueño y alcance.

## Objetivo y base

Entregar un recorrido local reutilizable de conservación de datos sintéticos:
fixture de origen V6, lectura/adaptación de Copias y destino V8 explícitos,
comparación host y prueba preparada de migraciones Android reales.
El estado autorizado sigue exclusivamente en `docs/STATUS.md`, reservado a MAIN.

Base recibida: rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, staging vacío y un worktree.
La entrada `.local-evidence/stage9/entry/repository.json` conserva 984 rutas,
sus originales y el checkout acumulado como entrada protegida. Contra Stage 8
sólo aparecieron los cinco cambios documentales del despacho. No se crearon
ramas, commits ni operaciones remotas.

## Cambios entregados

- `app/src/testSupport/java/com/blackatsystems/miguardia/upgrade/UpgradeFixtureSnapshots.kt`:
  constructor puro compartido, 27 tablas V6 pobladas, coordenadas REAL y valores
  nulos, claves compuestas y relaciones. V5, V6, V7 y V8 tienen metadatos
  explícitos; el esperado V8 toma sus columnas del contrato real y agrega sólo
  adjuntos de nota y situaciones vacíos. La adaptación bajo prueba es la real.
- `LegacyUpgradeFixtureContainer.kt`, en ese mismo directorio: generador
  exclusivamente de prueba del wire histórico V5/V6/V7. Cada base se relee con
  `BackupPayloadCodec`; manifiesto, preferencias y fotos usan codecs reales.
  El contenedor sintético usa la codificación histórica unsealed V1 admitida
  por el lector. No modifica formato, escritor productivo ni criptografía.
- `app/src/test/java/com/blackatsystems/miguardia/upgrade/UpgradeFixtureSnapshotsTest.kt`:
  17 regresiones ejecutables; conserva la reproducción de defaults incorrectos,
  lectura legacy, adaptación, fotos, preferencias, integridad y errores.
- `app/src/androidTest/java/com/blackatsystems/miguardia/upgrade/PublicUpgradeBackupFixtureTest.kt`:
  conserva clase, método y guardas API33/AVD/serial/hardware/fecha/consentimiento.
  El directorio nuevo `public-upgrade-fixture-export-v2` conserva por separado
  `MiGuardia_origen_RoomV6_sintetico.miguardia-backup` y
  `MiGuardia_destino_RoomV8_roundtrip.miguardia-backup`. El recibo formato 2
  distingue origen, destino, identidades, tablas, hashes y ausencia de upgrade
  instalado/migración Room. Comprueba preview sin mutación lógica y restaura
  desde el contenedor V6 en un segundo contexto aislado V8.
- `core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/V6ToV8ConservationInstrumentedTest.kt`:
  prepara una base UUID propia con las 27 tablas pobladas; aplica las migraciones
  reales `6→7→8`, compara cada campo y tipo, valida esquema/identidad/FK/integridad
  y reabre con Room y el validador V2. Es una prueba de base aislada, no de app
  instalada, SAF, preferencias o bytes de fotos.
- `core/database/build.gradle.kts`: sólo conecta esas fuentes puras al componente
  AndroidTest mediante la API de variantes. Se evita duplicar la fixture y no
  se agrega dependencia productiva, plugin, variante ni versión.
- `tools/release/upgrade_v6_v8.py` y su test: modo separado de captura/comparación
  host con versiones exactas. El contrato histórico de `upgrade_snapshot.py`
  y sus seis regresiones V6→V6 se conservan intactos.
- `tools/qa-groups.json`: hashes revisados, efectos de la fixture aclarados y
  grupo `database-v6-v8-conservation` aislado. Se conservan permisos/guardas de
  todos los grupos anteriores. Los planes se generaron sin `--execute`.

No hay cambios en código productivo, migraciones, esquemas exportados,
applicationId, versionCode, firma, permisos o dependencias.

## Evidencia y uso local

La auditoría del bloque es
`docs/audits/2026-10-21-stage9-conservacion-v6-v8.md`.
La evidencia nueva está exclusivamente en `.local-evidence/stage9/`.

- `adverse/original.xml`, `adverse/ImplicitV7Constructor.kt` y
  `adverse-original-escalated.log`: constructor original extraído sin modificar,
  rechazado por el validador real V7. La regresión final prueba por separado el
  rechazo de versión y el de identidad.
- `focused-jvm.xml`: 17/17 regresiones nuevas verdes. `jvm-export/` contiene los
  BIN reales de origen/destino, preferencias, PNG sintético, contenedor V6 y
  `hashes.sha256`. El export sólo ocurre si se indica expresamente
  `MIGUARDIA_STAGE9_FIXTURE_EXPORT` y exige directorio nuevo.
- `local/result.json`: 952 JVM frescas, 395 dominio + 19 base + 538 app,
  cero fallos/errores/omitidas. No se suman como nuevas otras 17 pruebas por
  haber corrido antes la focalizada: son el mismo conjunto dentro de las 952.
- `build/result.json`: resultado y hashes de compilación/lint del perfil build.
  Ambos runners AndroidTest se generaron en este bloque; los tres APK de app
  estaban al día y fueron reutilizados. Lint nuevo: 24 advertencias, cero errores.
- `release-tests-r3.log`: 54/54 Python verdes, 48 nuevas y seis históricas.
  `host/run1/` conserva un recorrido sintético y dos adversos rechazados.
  `cross-fixture-r2/receipt.json` comprueba la misma fixture Kotlin con SQLite
  host: 27→29 tablas, 17 preferencias y una foto, sin migración Room.
- `exit-checks.json`, `exit/repository.json`, `exit-own.patch` y
  `evidence-manifest.json`: delta propio, originales, hashes y cierre verificable.
  `preserved-artifacts/` conserva copias con hash de los cinco APK y el lint.
  La igualdad host nunca constituye prueba Android.

El comparador nuevo recibe una copia SQLite/WAL inactiva y coherente,
directorios seleccionados explícitos `files`, `shared_prefs`, `no_backup`, y
JSON con las 17 preferencias `{key,type,values}` exportadas de la fixture.
No lee una instalación ni fabrica preferencias faltantes.

```powershell
python tools/release/upgrade_v6_v8.py capture --database <copia-v6.db> --private-files <seleccion-v6> --portable-preferences <preferencias-v6.json> --room-version 6 --fictitious-data --inactive-consistent-copy --output <snapshot-v6-nuevo.json>
python tools/release/upgrade_v6_v8.py capture --database <copia-v8.db> --private-files <seleccion-v8> --portable-preferences <preferencias-v8.json> --room-version 8 --fictitious-data --inactive-consistent-copy --output <snapshot-v8-nuevo.json>
python tools/release/upgrade_v6_v8.py compare <snapshot-v6.json> <snapshot-v8.json> --output <recibo-nuevo.json>
```

Los argumentos entre ángulos son destinos a seleccionar conscientemente, no
comandos listos para ejecutar sobre un teléfono. Los outputs deben ser nuevos.
Sólo se permiten las dos tablas nuevas vacías; todas las filas/campos originales,
tipos, identidades, asociaciones, archivos seleccionados y preferencias deben
coincidir. La identidad de instalación se declara `NOT_ATTESTED`: paquete,
certificado y artefactos se vinculan mediante un recibo independiente.

## Qué demuestra cada nivel

La JVM ejecutó constructores, validadores de forma/identidad, codecs,
adaptación de Copias y extracción de contenedores reales sobre datos sintéticos.
El validador de forma no certifica por sí solo todo el grafo de negocio.
La comparación host verifica SQLite y contenido lógico; no ejecuta las
migraciones Kotlin de Room. La foto JVM se genera con ImageIO y se compara por bytes;
el funcionamiento Bitmap/Photo Picker/SAF y la aplicación de preferencias
Android permanecen en el ensayo de sistema. El zoom legado viaja en Copias;
no se reintroduce zoom visual.

AndroidTest compilado significa prueba preparada, no prueba Android ejecutada.
No hubo Samsung, ADB, emulador, instalación, restauración real ni publicación.
Joaquín agregó durante el bloque «usa el samsug si hace falta»; el cierre local
no lo necesitó. MAIN recibe también esta instrucción, sin atribuirle una
actualización instalada que no se realizó.
Durante el sellado MAIN comunicó que Joaquín retiró temporalmente el Samsung
y ordenó continuar sólo el cierre local. Se respetó esa restricción; su regreso
debe confirmarse expresamente antes de cualquier ensayo posterior.

## Plan Android posterior — pendiente

1. Fijar dispositivo/AVD, paquetes, artefactos y datos sintéticos descartables.
   Obtener de Joaquín la decisión concreta que cubra instalación y eventual
   reemplazo de esos datos. Registrar hashes y certificado de origen/destino,
   paquete idéntico y versionCode creciente. Hoy el repositorio sigue en
   `versionCode=6`/`2.0.2`: este bloque no genera una actualización distribuible.
   Nunca usar una copia personal o el paquete público con datos reales.
2. Ejecutar primero la clase de migración aislada nueva en el target de base;
   comprobar el APK/runner realmente instalado y su recibo. Plan sin dispositivo:
   `python tools/qa.py --group database-v6-v8-conservation`.
   Requiere uso de dispositivo y datos QA descartables; no instala por sí solo.
3. Para generar los archivos con la clase histórica, usar exclusivamente el
   AVD API33 nombrado en sus guardas, serial `emulator-5582`, hardware/modelo/
   fingerprint runtime coincidentes, fecha local y consentimiento explícito.
   Plan: `python tools/qa.py --group upgrade-backup-preparation`.
   No adaptar sus guardas para Samsung. Inspeccionar el recibo exitoso y ambos
   archivos v2; los resultados v1 antiguos conservan su procedencia histórica.
4. En un origen 2.0.2 V6 sintético y descartable, importar el archivo V6 por UI y
   SAF antes del baseline. Verificar toda la historia de prueba, foto utilizable,
   17 preferencias y versión/identidad de base. Confirmar que el lector de ese
   artefacto acepta realmente el archivo; la JVM candidata no acredita esto.
5. Actualizar esa misma instalación con el artefacto autorizado, sin desinstalar
   ni limpiar. Tras el primer arranque tomar copias coherentes y capturar V6/V8
   con la herramienta nueva. Inspeccionar calendario, notas, recurrencias,
   horarios reales, extras, disponibilidad y los estados nuevos vacíos.
6. En otro destino descartable, abrir la copia V6 mediante SAF, observar preview,
   cancelar y verificar ausencia de mutaciones; después restaurar mediante la
   confirmación consciente. Comprobar reapertura/recreación, foto decodificable,
   relaciones y las 17 preferencias semánticas, incluido zoom legado en Copias.
   Permisos, canales, alarmas, widgets, sonido URI y reconciliación de Android
   necesitan sus controles propios; no se presentan como preferencias restauradas.
7. Conservar los adversos y recibos, revisar el delta de ese ensayo y devolverlo
   a MAIN. Room real, actualización instalada, restauración Android, aceptación
   humana y distribución se registran por separado.

## Relevo

MAIN revisa el delta contra la entrada y los recibos de esta entrega antes de
integrar o actualizar STATUS. No repetir JVM/build válidos por rutina si sus
insumos no cambiaron. Los cambios del comparador Python se verifican con su
batería y no invalidan por sí solos la compilación Kotlin.
Los hallazgos concretos vuelven a este mismo chat; no se abre otro implementador.
