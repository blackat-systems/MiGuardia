# Auditoría MAIN — Calendario final y tarjeta superior V2

- Fecha: 2026-08-27
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `27601ddf50f16f6381eb998f0c01daecc9ced706`
- Upstream de entrada: `80fe8e5f8fdc47d5236941e91a46ffc3b1faab61`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró el Calendario final y la tarjeta superior desplegable.
MiGuardia conserva una sola grilla mensual y presenta en ella jornadas, extras,
disponibilidad y marcadores existentes sin crear una segunda fuente de datos.

La tarjeta superior resume el día civil actual. Cerrada prioriza una jornada en
curso, la próxima de hoy, las completadas o el estado honesto sin trabajo. Si
corresponde, adjunta el próximo evento futuro. Al abrirla enumera todas las
jornadas de hoy, incluidas completadas, canceladas, ausentes y protegidas, y
puede incorporar una nocturna todavía activa iniciada ayer.

La superficie es de consulta. No se agregaron escrituras, recurrencias,
situaciones especiales nuevas, Resumen, notificaciones, widget ni cambios de
persistencia.

## Auditoría independiente y correcciones de MAIN

Tres agentes de sólo lectura revisaron por separado el dominio y la observación,
la tarjeta Compose y el Calendario. MAIN contrastó sus hallazgos con el prompt,
el código y las pruebas. Se corrigieron:

- las colecciones expuestas por la proyección, incluidas las listas anidadas,
  para que no puedan mutarse desde fuera;
- la observación de todos los sectores presentes en la historia laboral, en vez
  de asumir uno solo;
- una carrera entre actualizaciones de fuente y límites temporales que podía
  perder un cambio;
- la fecha transportada por los errores del observador;
- el descarte del resultado del día anterior al cruzar medianoche y la
  conservación del último resultado válido ante un error recuperable del mismo
  día;
- el reintento y los tiempos de actualización para no consultar cada minuto
  cuando el siguiente cambio real es medianoche;
- tres expectativas instrumentadas que todavía buscaban el título histórico
  `Próximo evento` y una que suponía una sola celda completada;
- la prueba Activity para que fije el zoom QA estándar y desplace explícitamente
  el mes antes de pulsar, sin depender de preferencias dejadas por otra corrida.

Se agregó cobertura para entradas mutables, múltiples sectores, medianoche,
error antes y después del cambio de fecha, cableado del temporizador y ausencia
de sondeo innecesario.

## Persistencia

`MiGuardiaV2Database` permanece en versión 5 con 27 tablas. No cambiaron Room,
entidades, DAO, repositorios de base, migraciones ni esquemas.

Esquemas verificados:

- `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- `3.json`:
  `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`;
- `4.json`:
  `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B`;
- `5.json`:
  `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4`.

El identity hash sigue siendo `77adbc875d0f4ee466cdbd0dd74d5c5c`. No existe
`fallbackToDestructiveMigration` ni `allowMainThreadQueries` en producción.

## Validación local de MAIN

La batería contractual se ejecutó desde cero con `--rerun-tasks` y
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

Resultado: `BUILD SUCCESSFUL` en 10 min 31 s, con 238/238 tareas ejecutadas.

- dominio JVM: 285/285;
- base JVM: 12/12;
- aplicación JVM: 159/159;
- total JVM: 456/456, sin fallos, errores ni omisiones;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- AndroidTest compilados: 214 de aplicación y 107 de base;
- `git diff --check`: correcto.

Después de endurecer únicamente pruebas instrumentadas, MAIN recompiló el APK
AndroidTest QA. No se agregaron dependencias, permisos, cambios de Gradle,
manifiesto, SDK, red, telemetría, logs privados ni ajustes visuales del sistema.

## Validación Android de MAIN

### Samsung SM-S938B — API 36

Se instalaron manualmente sólo los APK QA exactos. La ejecución Room completa
quedó en 107/107.

La primera ejecución completa de aplicación produjo 210/214: cuatro pruebas
conservaban expectativas anteriores al nuevo diseño —el título `Próximo evento`
o una única celda completada—. Dos revisiones independientes confirmaron que no
eran fallos de producto ni contaminación del dispositivo. MAIN corrigió sólo
esas aserciones, obtuvo 4/4 de forma aislada y repitió la suite completa desde
datos QA limpios: 214/214.

Durante la revisión manual, MAIN dejó conscientemente el zoom interno en 200 %.
La prueba Activity de recreación heredó esa preferencia y falló porque su guion
no fijaba un zoom inicial ni desplazaba todos los controles. La navegación real
se comprobó manualmente en 200 %. MAIN hizo determinista el fixture, recompiló y
la prueba final pasó 1/1.

Evidencia física única, sin contar repeticiones diagnósticas:

- base y persistencia: 107/107;
- aplicación completa: 214/214;
- total: 321/321.

El recolector final encontró XML de `connectedAndroidTest` generados en bloques
anteriores: 89 pruebas de app del 2026-08-25 y una corrida Room roja anterior a
la corrección de disponibilidad. No se contaron como evidencia actual. Los
resultados de este bloque provienen de las ejecuciones ADB completas realizadas
con los APK finales indicados arriba.

La instrumentación cubrió tarjeta cerrada y abierta, estados históricos,
nocturna iniciada ayer, medianoche, recreación, grilla, carga, edición,
recurrencias, horario real, extras, disponibilidad, navegación, claro/oscuro,
retrato/paisaje y zoom interno 100/150/200.

MAIN inspeccionó visualmente con datos ficticios:

- tarjeta cerrada en oscuro/retrato;
- Calendario y tarjeta en claro con zoom interno 200 %;
- tarjeta desplegada con el estado `Hoy no tenés trabajo`, un próximo evento y
  una jornada cancelada histórica;
- la misma superficie en claro/retrato y claro/paisaje.

No se consultaron ni modificaron `font_scale`, densidad o tamaño visual del
sistema. La rotación temporal volvió a modo libre.

## Seguridad del dispositivo

Antes de instalar, MAIN comprobó que no existía ningún paquete
`com.blackatsystems.miguardia*`. Usó exclusivamente:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test`.

Los tres paquetes quedaron ausentes al finalizar. Producción no fue instalada,
abierta, consultada, limpiada, modificada ni desinstalada. Las capturas y XML
temporales quedaron fuera del repositorio y los archivos temporales creados en
el dispositivo fueron retirados.

## Git y pendiente separado

El candidato llegó directamente al checkout compartido, sin commit ni staged.
MAIN preservó `main`, `origin/main`, `v1.0.0`, worktrees históricos y el
upstream. No hubo merge, rebase, reset, descarte, tag, Release ni acción sobre
producción.

API 26 no se repitió porque este bloque no cambió Room, manifiesto, SDK ni
dependencias. Continúa como validación separada antes del candidato final y no
bloquea este checkpoint API 36.

La próxima etapa recomendada es Resumen personalizable. Su prompt todavía no
fue creado ni habilitado. Este cierre no autoriza push.
