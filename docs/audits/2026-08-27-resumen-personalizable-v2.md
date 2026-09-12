# Auditoría MAIN — Resumen personalizable V2

- Fecha: 2026-08-27
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `9fad12e39b56a850ce528a2fd5398f3b15258864`
- Upstream de entrada: `fd6891e446eaa574f3df14348d8d5b1cfd201f2d`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró Resumen personalizable V2. La aplicación posee ahora un
destino principal mensual y de sólo lectura que reúne cifras esenciales,
cumplimiento por períodos completos, disponibilidad separada y ocho familias
opcionales ordenables u ocultables.

Cada métrica abre el mismo libro de contribuciones que produjo su cifra. El
Resumen no escribe Room ni guarda totales derivados. Sus preferencias de
presentación viven en un DataStore exclusivo y no alteran fórmulas, fuentes ni
fotografías históricas.

## Auditoría independiente y correcciones de MAIN

Tres agentes de sólo lectura revisaron por separado dominio, estado/reactividad
e interfaz. MAIN contrastó sus hallazgos con el prompt, el código y las pruebas.
Después de las correcciones finales no quedaron bloqueantes.

Se corrigieron:

- el detalle de `Planificado frente a real`, que ahora conserva dos
  contribuciones exactas: horario real positivo y planificación negativa;
- la reutilización de intervalos exactos compartidos para evitar diferencias
  entre trabajo, disponibilidad y Resumen;
- el rango observado para incluir fuentes de semanas o ciclos completos que
  tocan el mes aunque empiecen o terminen fuera de él;
- el ciclo de vida del observador, que queda inactivo cuando Resumen no está
  visible o una superficie funcional lo cubre;
- los límites temporales para no consultar cada minuto cuando ninguna cifra
  puede cambiar;
- una cola FIFO de mutaciones de preferencias con reintento ordenado;
- los estados de error con y sin caché para evitar banners duplicados;
- cobertura de todas las fuentes reactivas, incluidos horario real, extras,
  reglas, feriados, protecciones y disponibilidad;
- tres pruebas Android que dependían de un texto repetido, del alto físico del
  Samsung o de dos pulsaciones Atrás sin esperar la pantalla intermedia.

El conjunto final de implementación quedó en diez archivos modificados y once
nuevos antes de incorporar esta documentación. No se eliminó ningún archivo.

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

El identity hash continúa en `77adbc875d0f4ee466cdbd0dd74d5c5c`. No existe
`fallbackToDestructiveMigration` ni `allowMainThreadQueries` en producción.

## Validación local de MAIN

La batería contractual se ejecutó serialmente con `--max-workers=1`:

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

Resultado final local:

- dominio JVM: 301/301;
- base JVM: 12/12;
- aplicación JVM: 175/175;
- total JVM: 488/488, sin fallos, errores ni omisiones;
- lint: 0 errores y 6 avisos globales de versiones disponibles en archivos
  Gradle no modificados;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- `git diff --check`: correcto.

No se agregaron dependencias, permisos, cambios de Gradle, manifiesto, SDK,
red, telemetría, logs privados ni ajustes visuales del sistema.

## Validación Android de MAIN

Joaquin autorizó expresamente el Samsung `SM-S938B`, API 36, serie
`R5CY529W6PL`.

Room y persistencia pasaron 107/107. La primera corrida completa de aplicación
detectó tres fallas en pruebas nuevas y una omisión histórica de alarmas. Las
tres fallas no correspondían al producto:

- una aserción buscaba un texto `2 h` presente en dos métricas;
- una prueba enviaba dos pulsaciones Atrás sin esperar la recomposición;
- una prueba suponía desplazamiento en una pantalla cuyo alto depende del
  dispositivo.

MAIN volvió deterministas esas pruebas. El bloque específico pasó 11/11 y la
suite completa final registró 225 casos: 224 ejecutados correctamente, cero
fallos y una omisión declarada por
`NotificationAlarmEndToEndInstrumentedTest`. Esa prueba histórica de alarmas
exactas queda como puerta separada y no forma parte del Resumen.

Evidencia física única, sin contar repeticiones diagnósticas:

- base y persistencia: 107/107;
- aplicación ejecutada: 224/224;
- total ejecutado: 331/331;
- omitida por contrato propio: 1 prueba histórica de alarmas.

La instrumentación cubrió Resumen, personalización, recreación, detalles,
regresiones de Calendario y Horas y extras, tema claro/oscuro y zoom interno
100/150/200.

MAIN inspeccionó directamente con datos ficticios:

- Resumen en oscuro/retrato al 100 %;
- Resumen en oscuro/paisaje;
- Resumen en claro/retrato al 200 %;
- desplazamiento de las tarjetas y detalle exacto de una cifra.

No se consultaron ni modificaron `font_scale`, densidad o tamaño visual del
sistema. La rotación temporal quedó restaurada a modo libre con los valores
originales observados.

## Seguridad del dispositivo

Antes de la revisión manual no existía ningún paquete
`com.blackatsystems.miguardia*`. Se usaron exclusivamente:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test` durante la suite Room.

Al finalizar no quedó ninguno de esos paquetes. Producción no fue instalada,
abierta, consultada, limpiada, modificada ni desinstalada. Las capturas y XML
temporales quedaron fuera del repositorio y sus copias del Samsung fueron
retiradas.

## Git y pendientes separados

El candidato llegó directamente al checkout compartido, sin commit ni staged.
MAIN preservó `main`, `origin/main`, `v1.0.0`, worktrees históricos y el
upstream. No hubo merge, rebase, reset, descarte, tag, Release ni acción sobre
producción.

API 26 no se repitió porque este bloque no cambió Room, manifiesto, SDK ni
dependencias. Continúa como validación separada antes del candidato final.

El siguiente bloque del orden aprobado es Próximo evento y notificaciones. No
existe todavía un prompt V2 habilitado ni autorización para abrir otra tarea.
Este cierre no autoriza push.
