# Stage 9 — evidencia local de conservación V6 a V8

Fecha local: 2026-10-21. Contrato:
`docs/prompts/STAGE_9_CONSERVACION_ACTUALIZACION_V6_V8_3_0.md`.
Entrega y plan físico: `docs/HANDOFF_STAGE_9_A_MAIN_3_0.md`.
Esta auditoría describe evidencia del bloque; no es otra hoja de estado.

## Entrada protegida

Puerta 0 comprobó `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`,
host `DESKTOP-UF10GUI`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream homónimo,
remoto `https://github.com/blackat-systems/MiGuardia.git`, un worktree y staging vacío.

`.local-evidence/stage9/entry/repository.json` registra 984 rutas, incluidos
los archivos ausentes como hash nulo, y conserva los originales bajo `entry/files`.
Contra las 982 rutas Stage 8 sólo cambiaron MAPA, STATUS, índice de prompts y
los dos contratos del despacho. Se preservó el checkout acumulado; el delta
de este bloque se calcula contra esa entrada, nunca contra HEAD como si todo
el trabajo anterior fuera propio. MAIN reservó STATUS/contratos/índices.

## Causa reproducida

La fixture original iteraba las 28 tablas V7 pero omitía versión e identidad
del snapshot. Los defaults productivos eran V8; el validador exigía V7 y el
recibo afirmaba V6. Se extrajo el constructor puro, sin cambiar su cuerpo ni
datos, para llamarlo desde una prueba JVM real.

La focalizada produjo un fallo esperado, no un verde:
`InvalidBackupException: La copia usa una versión de datos no compatible`,
desde `MiGuardiaBackupSchemaV7.requireValid`. Se preservaron log, XML y fuente
en `adverse-original-escalated.log` y `adverse/`. El test final además corrige
sólo el número a V7 y demuestra el segundo rechazo por identidad V8.
No se ejecutó el ensayo original en Android ni se infirió pérdida de datos.

También se comprobó que escribir V7 explícito no resolvía el recorrido:
gateway y escritor actuales exigen V8. La solución usa un origen V6 validado,
wire sintético legacy comprobado con el lector real y un destino V8 explícito.
El export actual V8 queda separado del contenedor fuente V6. Las 27 entidades
de los esquemas 6/7/8 son idénticas; las migraciones sólo añaden adjuntos y
situaciones, que comienzan vacíos. No hay columnas o valores anteriores que
puedan descartarse durante la comparación.

## Implementación y revisión

La fixture compartida llena las 27 tablas y cubre UUID, claves compuestas,
historia de configuración, notas, recurrencias, jornadas reales, extras,
disponibilidad, foto, nulos y coordenadas REAL no nulas. El esperado V8 conserva
literalmente las tablas anteriores y toma las dos nuevas del contrato real.
Las pruebas ejecutan la adaptación productiva, no una copia de su fórmula.

El generador legacy sólo existe en fuentes de prueba. Reutiliza los codecs
de manifiesto/preferencias/fotos y congela el wire histórico unsealed V1 ya
utilizado por `BackupContractTest`. Cada BIN se decodifica inmediatamente con
el lector productivo. No altera la seguridad o formato del producto.

La clase Android histórica conserva sus guardas y contextos de aislamiento;
se verificó por comparación con la entrada. Cambia a salida v2 para no
sobrescribir ni reatribuir los resultados v1. El recibo distingue versión/
identidad/manifiesto/filas de origen y destino; installed upgrade y migración
Room permanecen explícitamente falsos. El PNG ya no afirma códigos de
instalación antiguos: identifica conservación lógica Room V6 a V8.

La prueba Room nueva usa una base con nombre UUID propio y no el gateway,
que podría realizar limpieza de candidatos huérfanos ajenos. Aplica las dos
migraciones productivas, compara cada tipo/campo y reabre con el validador V2.
Su ejecución Android sigue pendiente. El único cableado Gradle nuevo agrega
fuentes puras al AndroidTest de base mediante la API de variantes.

Un revisor interno independiente contrastó los contratos, guardas, lector y
wire; otro revisó el comparador host. MAIN deberá efectuar su revisión propia
después del relevo. Ninguna revisión interna se presenta como aceptación MAIN.

## Validación ejecutada y adversos preservados

- JVM focalizada: 17/17 verdes, cero omitidas, recibo `focused-jvm.xml`.
- Perfil local con `--fresh-tests`: 952/952 verdes, cero fallos/errores/omitidas;
  395 dominio, 19 base y 538 app. `local/result.json` registra ejecución fresca
  de los tres tasks y hashes de insumos. Incluye las 17 nuevas, sin contarlas dos veces.
- Herramientas generales: 75/75 verdes en `tools-tests-escalated.log`.
- Documentación/catalogación previas: válidas; planes generados sin dispositivo.

Los primeros intentos Gradle/Python encontraron permisos del sandbox en cachés
Gradle y temporales Windows; se conservaron y se repitieron mediante el control
de escalación del entorno. No prueban un defecto del producto.

La primera integración de sourceSet en la librería falló al configurar Gradle
por un cast de `DefaultAndroidLibrarySourceSet` a `AndroidLibrarySourceSet`.
Se reemplazó ese acceso por la API de fuentes AndroidTest de variantes y la
focalizada siguiente compiló y pasó. Los logs `focused-jvm.log` y
`focused-jvm-r2.log` conservan ambas ejecuciones.

La primera batería integrada del comparador terminó 45/46: una regresión
pretendía crear una segunda fila de feriado con fecha única repetida y fallaba
al preparar el adverso. Se corrigió el dato de prueba, manteniendo la aserción
de conservación. El log rojo `release-tests.log` se conserva.

El revisor host reprodujo un falso verde de esquema: los PRAGMA no distinguían
`COLLATE` de columna no indexada, `CHECK` o diferimiento de FK. Se agregó
comparación de DDL y regresiones, sin ampliar silenciosamente el contrato V6
histórico ni relajar los validadores. Su comprobación final se registra abajo.

El primer bridge de BIN a SQLite se detuvo por una entidad sin lista `indices`.
Se usa ahora la ausencia como lista vacía, conforme el formato JSON, conservando
el directorio parcial. La revisión además exigió validar el manifiesto SHA-256
del export JVM, conjunto/cantidad de tablas, columnas, PK y timeline antes de
atribuir el resultado a la fixture Kotlin. El bridge conserva esas comprobaciones.

La batería r2 terminó 53/54: el caso positivo de `ALTER` suponía que SQLite
almacenaría textualmente la PK antes de las columnas agregadas. El motor de
esta sesión conserva otra representación equivalente. Se reemplazó esa
suposición por comparación de columnas reales contra JSON V6 y conservación
de coordenadas, manteniendo el `ALTER` real y la comparación exacta V6→V8.
`release-tests-r2.log` conserva el fallo; la revisión independiente de DDL
comprobó los adversos `COLLATE`/`CHECK`/`DEFERRABLE` y compatibilidad con ALTER.

## Cierre local previo a R1

- `release-tests-r3.log`: 54/54 verdes, 48 nuevas + seis del modo V6 histórico.
- `host/run1/`: comparación positiva y rechazo de mutación de fila e identidad
  Room incoherente. Son datos genéricos sintéticos, sin validez laboral asumida.
- `cross-fixture-r2/receipt.json`: los BIN reales de la fixture JVM, verificados
  por su manifiesto SHA-256 y contratos exactos, se materializaron en SQLite
  host de versiones 6 y 8. Comparación verde de 27 tablas originales, 29 de
  destino, 17 preferencias y una foto. Las dos tablas nuevas están vacías.
  El directorio parcial anterior se conserva. No es una migración Room.
- `build/result.json`: perfil build verde y 590 insumos estables. Dos APK
  AndroidTest generados; tres APK de app reutilizados `UP-TO-DATE`, sin cambios
  productivos. Lint generado nuevamente, 24 advertencias y cero errores.
- `local/result.json`: los mismos 590 insumos vigentes cubren las 952 JVM.
  Se contrastaron hashes y conteos de los 104 XML preservados, no sólo el texto
  de salida de Gradle. No se repitió la batería por cambios Python/documentales.
- `review-checks.json` y su sucesor `exit-checks.json`: verificación reproducible
  con `verify_delivery.py`, sin Gradle/red/dispositivos. Comprueban los insumos
  actuales, los 104 XML, las seis salidas contra sus copias preservadas, guardas
  originales y el modo V6 intacto. Separan resultados host anteriores rojos del
  rerun final verde; herramienta y bridge no cambiaron entre sus ejecuciones.

El delta propio es de once rutas: tres existentes modificadas y ocho nuevas.
Todos los demás insumos recibidos permanecen idénticos, incluida la ausencia
heredada de `VerticalScrollbarMetricsTest.kt`. No cambió ninguna fuente de
producción, esquema/migración, dependencia, permiso, versión, firma o estado
reservado a MAIN. Las copias de los APK permanecen fuera de Git.

Hashes de los runners generados:

| Artefacto | SHA-256 |
|---|---|
| app QA AndroidTest | `40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472` |
| database Debug AndroidTest | `3d5288bc8f36881ca4d570db4d479aad7722ff57b605b1fb779a1cdf650da140` |

Fuente host final: `d20c768c5e396842e47708538314e9a2679330a1b8ebc00961ebc8d24db5fd58`.
Test host pre-R1: `6eaf404bf2c814d427e4d83c5d480264172fefd56d54b0cb1b6cf12dc5357bad`.
El manifiesto de evidencia y snapshot de salida conservan los demás hashes,
comandos, artefactos, delta y comprobación del cese de Gradle para MAIN.
La consulta CIM inicial fue denegada por el sandbox: el cero que dejó en
`processes-final.json` no es evidencia válida de ausencia. El reintento
`processes-final-escalated.json` comprobó `QuerySucceeded=true` y cero procesos
Gradle/Kotlin el 21/10 a las 05:06:22 UTC. No se detuvo ningún proceso ajeno.

## Alcance y puertas

La herramienta host se ejecuta sobre copias sintéticas explícitas, valida
identidades Room, DDL/columnas/índices/FK, filas y valores tipados, asociaciones,
archivos y las 17 preferencias. Conserva referencias históricas sin FK como
contenido, sin inventar una restricción que borre referencias huérfanas admitidas.
No fabrica preferencias ni acepta esquemas sólo por su número. El modo V6
histórico mantiene su contrato de igualdad V6→V6.

Las materializaciones SQLite host se identifican como tales; no ejecutan las
migraciones Kotlin/Room. Los recibos no certifican identidad de aplicación
instalada, SAF, aplicación de preferencias Android, decodificación OEM de fotos,
instalación, actualización ni restauración física. Los codecs y contenedores
JVM son evidencia de lectura/adaptación, no de aplicación Android.

Joaquín agregó «usa el samsug si hace falta» durante este bloque. No fue
necesario usarlo para el criterio local; no se consultó ni operó dispositivo.
El ensayo de instalación debe fijar artefactos, paquete, certificado,
versionCode y datos descartables con sus consecuencias explícitas. La candidata
permanece `2.0.2`/código 6; este bloque no cambia identidad de instalación.
El handoff contiene un plan físico autosuficiente y sus grupos sin `--execute`.
En el cierre MAIN comunicó la retirada temporal del Samsung y restringió el
trabajo al sellado local. Se mantuvo esa restricción, sin consultar ADB; el
regreso físico y su autorización de ensayo deben comprobarse al reanudar.

## R1 — corrección de regresiones devuelta por MAIN

MAIN contrastó el cierre anterior en
`.local-evidence/stage9/main-review/checks.json`: 992 rutas, 590 insumos,
104 XML/952 JVM, seis artefactos y bridge, además de 54 Python verdes propios.
Su revisión encontró una insuficiencia real de los tests, sin defecto
productivo ni bloqueo del comparador:

- `test_integer_precision_and_real_changes_are_rejected` mantenía el cambio
  INTEGER al probar REAL; el segundo rechazo no acreditaba detectar REAL.
- `test_null_and_text_mutations_are_rejected` usaba `synthetic` como si fuera
  el cuerpo original, que es `Nota sintética á ñ 🌙`. Ese cuerpo alterado
  permanecía durante los adversos de timestamp y NULL→texto.

Se preservó el cierre como pre-R1, sin sobrescribir archivos ni sellos.
La entrada R1 comprobó las 992 rutas idénticas al exit previo y rehasheó
los 1.207 archivos de su manifiesto. Los originales del test y ambas entregas
están copiados en `.local-evidence/stage9/r1/entry/files/`.

Antes de corregir, `r1/probe_masking.py pre` reprodujo el enmascaramiento con
tres dobles temporales de `cell`: ignorar REAL, ignorar NULL→texto e ignorar
timestamps. Los tres probes seguían verdes bajo esos comparadores defectuosos;
`pre-masking.json` registra `MUTANT_ESCAPED`, sin cambiar producción.

La corrección agrega comparación positiva antes de cada adverso y restauración
byte por byte en `finally`, seguida de otra comparación positiva. La misma
garantía se aplica a equivalentes secuenciales de tipos/relaciones, tablas,
índices, identidad, vistas/triggers, tablas nuevas, fotos y preferencias.
Se conservan aserciones y mensajes esperados. Los loops que ya usan `deepcopy`
o insumos independientes y el comparador histórico permanecen intactos.
Un revisor interno de sólo lectura contrastó el diff contra la entrada R1 y
no encontró nuevos casos enmascarados.

Validación R1:

- `r1/comparator-tests.log`: 54/54 Python verdes, cero errores/fallos/omitidas.
- `r1/post-masking.json`: los mismos tres dobles ahora dan `MUTANT_DETECTED`;
  cada caso falla por no emitir el rechazo esperado, con cero errores de setup.
  Estos fallos intencionales no se cuentan como pruebas productivas fallidas.
- `r1/validation.json`: comandos, logs y hashes anteriores/posteriores idénticos
  durante la ejecución. Comparador real y modo histórico sin cambios.
- `r1/exit-checks.json`: sólo cambiaron el test Python y las dos entregas;
  590 insumos Kotlin/build y evidencias anteriores conservados. Las 952 JVM,
  build, bridge y ejercicio host se reutilizan; no se repiten por este delta.
- `r1/final-receipt.json` y `r1/evidence-manifest.json`: relevo y sello nuevos,
  con referencia a la cadena pre-R1 que permanece intacta. R1 no ejecutó
  Gradle, ADB, dispositivos, instalaciones ni operaciones remotas.

Test Python R1: `a632b496541844dde997afd266b050a17bc52423127bdadd95ea22f95d610be2`.
La herramienta conserva `d20c768c5e396842e47708538314e9a2679330a1b8ebc00961ebc8d24db5fd58`.
MAIN conserva la revisión independiente y actualización de STATUS. Su aviso
de disponibilidad del Samsung para Stage 8 no modifica el alcance local R1.
