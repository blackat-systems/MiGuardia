# Auditoría MAIN — horario real y horas extra V2

- Fecha: 2026-08-25
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `d1f3e68c1ee5debdc34ef7e30f7376980175ee04`
- Upstream de entrada: `0364b835d07883708e137a7057f235fad9113b38`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró localmente el registro, corrección y retiro consciente
del horario real de una jornada V2 y la clasificación explícita de su tiempo
extra. Registrar lo real no sobrescribe el horario planificado; la edición
estructural consciente sigue disponible cuando sus protecciones lo permiten.
No se implementaron avance contra una referencia, trabajo extra independiente
ni consumidores agregados.

El bloque incluye:

- fechas, horas, offsets y cruces civiles explícitos;
- tiempo habitual y uno o más fragmentos extra exactos;
- clases reutilizables por los cuatro sectores, con fotografía histórica;
- escritura y retiro atómicos con CAS completo;
- protección de carga manual, edición, eliminación y recurrencias;
- borradores restaurables, error/reintento y doble toque protegido;
- detalle planificado/real y acceso al catálogo desde `Mi forma de trabajar`.

## Hallazgos y correcciones de MAIN

Tres revisiones independientes de sólo lectura cubrieron dominio, Room e
interfaz. MAIN corrigió, entre otros puntos:

- colecciones mutables en expectativas y fragmentos restaurados;
- validaciones incompletas de fotografías, timestamps, clases activas y
  transiciones estructurales;
- evidencia CAS del agregado real y del vecindario observado;
- rollback y protecciones frente a recurrencias, reemplazos y eliminaciones;
- cierre prematuro del editor estructural cuando el horario real no podía
  abrirse;
- restauración, conflicto, refresco, reintento y orden estable del Calendario;
- pruebas que dependían del alto visible en lugar del scroll real.

La revisión visual física encontró que la superficie nueva no respetaba la
barra de estado. Se agregó el margen seguro a las pantallas de horario real y
catálogo; una segunda captura en el Samsung confirmó el encabezado separado
del reloj y los iconos del sistema.

## Persistencia

`MiGuardiaV2Database` pasa de versión 2 a 3 mediante migración explícita
`2→3`. Conserva las 22 tablas anteriores y agrega:

- `extra_work_classes`;
- `shift_actual_records`;
- `shift_extra_intervals`.

La base queda con 25 tablas. Esquemas verificados:

- `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- `3.json`:
  `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`.

Las versiones 1 y 2 permanecen byte a byte iguales a sus hashes cerrados. No
se agregó migración destructiva ni acceso Room en el hilo principal.

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

Resultado: `BUILD SUCCESSFUL` en 8 min 9 s, 238/238 tareas ejecutadas.

- dominio JVM: 215/215;
- base JVM: 5/5;
- aplicación JVM: 144/144;
- total JVM: 364/364, sin fallos, errores ni omisiones;
- lint: 0 errores y 4 avisos de versiones disponibles;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- `git diff --check`: correcto.

Después de agregar la comprobación de rotación al recorrido Activity exacto,
`:app:assembleQaAndroidTest` volvió a compilar en verde y ese recorrido pasó
1/1 tanto en Samsung como en API 26, preservando el borrador entre paisaje y
retrato.

## Validación Android autorizada

### Samsung SM-S938B — API 36

- Room y migraciones: 84/84;
- aplicación afectada: 82/82;
- recorrido Activity: registrar con diferencia, recrear el borrador, persistir,
  reabrir y volver al planificado;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200 cubiertos por
  instrumentación física;
- revisión visual directa en oscuro/retrato del Calendario, detalle y editor.

La primera batería de aplicación del Samsung tuvo cinco fallos útiles: una
aserción buscaba una descripción semántica inexistente y cuatro acciones o
textos quedaban recortados o fuera del área visible. Se corrigieron el objetivo
semántico del test, la expansión completa de la hoja del día y los scrolls
explícitos; la repetición final quedó 82/82.

### Emulador Android 8.0 — API 26

- Room y migraciones: 84/84;
- aplicación afectada: 82/82;
- una primera corrida expuso una aserción dependiente del alto visible; el nodo
  y el borrador existían. Se corrigió el scroll del test y la repetición completa
  quedó verde.

La primera corrida Room del Samsung encontró una expectativa equivocada del
test: al cambiar el planificado con evidencia CAS exacta y horario real
existente corresponde dato inválido, no conflicto concurrente. Se corrigió sólo
la aserción y se repitieron las 84 pruebas.

## Seguridad de dispositivos y Git

Se usaron exclusivamente:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test`.

Los tres paquetes quedaron ausentes en Samsung y emulador. El emulador fue
apagado. En el Samsung permanece únicamente producción, que no fue abierta ni
modificada; la actividad visible final fue Ajustes de USB. No se consultaron ni
modificaron `font_scale`, densidad o tamaño visual del sistema.

Antes del checkpoint no había archivos staged. No hubo push, tag, merge,
rebase, reset, descarte ni cambios en `main`, `origin/main` o `v1.0.0`.

## Pendiente separado

La próxima decisión es estabilizar qué referencia de horas corresponde cuando
cambia dentro de una semana o ciclo. Recién después puede abrirse el bloque de
trabajo extra independiente y avance contra la referencia. No existe todavía
un prompt habilitado para ese bloque.
