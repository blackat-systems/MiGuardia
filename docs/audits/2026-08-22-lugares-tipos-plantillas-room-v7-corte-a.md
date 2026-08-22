# Auditoría de lugares, tipos, plantillas y Room v7 — Corte A

- Fecha: 2026-08-22
- Rama: `codex/miguardia-2.0`
- Base recibida: `475773754ce9a94cdc912fb012982d09709fd026`
- Base inmutable: `v1.0.0^{}` =
  `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Dispositivo físico: Samsung `SM-S938B`, Android API 36
- Publicación: no hubo push, tag, Release ni operación sobre producción

## Puerta 0 y traspaso

MAIN verificó la ruta exacta
`C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`, la rama, el HEAD de
entrada, autor Git `joaquin <blackat.systems@gmail.com>`, worktrees, remoto
privado, entorno Android y dispositivo conectado. `main`, `origin/main` y el
tag peeled continuaron en la línea 1.0.0. El tag anotado siguió siendo el objeto
`227c931ff8e381ab00120ad61b1c86ac71c03e46`.

El árbol de entrada no estaba limpio: conservaba dos modificaciones y trece
archivos no rastreados bajo `core/domain`. Eran una implementación incompleta
del Corte A. Se auditaron y continuaron; no se descartó, reseteó ni reemplazó
ninguno.

También se leyó completa la tarea `[PAUSADA] 03 - ESPECIALIDADES`, identificada
por `01a02304-7cf9-7fc2-85d2-847a6fae344d`. Esa tarea contenía decisiones
explícitas útiles, pero había asumido el rol MAIN sin autorización. Se trató
como evidencia de traspaso, nunca como fuente de autoridad. Las decisiones se
contrastaron con el mapa, planificación, índice de prompts, fichas sectoriales,
prompt MAIN, ADR y contrato activo antes de incorporarlas.

## Resultado del Corte A

El Corte A deja contratos puros y persistencia completa, sin activar todavía
una pantalla ni comportamiento visible V2:

- catálogo cerrado de Vigilancia privada, Policía, Enfermería y Medicina, sin
  `Salud` ni `Otro`;
- lugar laboral separado del `Objective` físico, tipo de trabajo separado de la
  plantilla horaria y reglas versionadas por lugar;
- vigencia de configuración por `LocalDate` exacta, no restringida al inicio de
  un mes;
- normalización de nombres y clave canónica NFKC para tipos;
- abreviatura nueva de tres a cinco caracteres, conservando sin reinterpretar
  las abreviaturas históricas de dos;
- primer conjunto atómico, adopción V1 explícita e idempotente, archivo
  independiente, recientes V2 y retrocarga consciente sólo para `NEW_V2`;
- jornada y `ShiftWorkSnapshot` insertadas o actualizadas como un par obligatorio;
- escritores heredados no pueden modificar sólo la mitad `Shift` de una jornada
  V2; Novedades conserva cambios de estado, pero bloquea cambios estructurales
  hasta que exista su recorrido V2;
- reglas resueltas por cada fecha civil cuando una jornada cruza medianoche;
- lotes V2 atómicos, con reemplazo autorizado de jornadas V1 o V2 únicamente
  en las fechas escritas, incluido un reemplazo legítimo al cambiar de sector.

## Room v7 y preservación

`MIGRATION_6_7` agrega vacías estas cinco tablas:

1. `work_places`;
2. `work_types`;
3. `work_templates`;
4. `workplace_rule_revisions`;
5. `shift_work_snapshots`.

Room pasa de 17 a 22 entidades. La migración no adopta `Objective`, no convierte
`ScheduleCombination`, no crea tipos o reglas y no altera una fila histórica.
Las relaciones de raíz, catálogo e historia usan `RESTRICT`; sólo
`Shift → shift_work_snapshots` usa `CASCADE`, y la procedencia opcional de un
horario V1 en `work_templates` usa `SET NULL`. La referencia ya fotografiada en
una jornada permanece como historia aunque luego se elimine aquel horario V1.

Hashes SHA-256 verificados:

| Esquema | SHA-256 |
|---|---|
| `1.json` | `06557907F47669DF0E2F950C00FC7FC89EA45511386A9990803F01B86471AC1B` |
| `2.json` | `8D835CDF9616924A704EF3FDF89CC2BF1268F4275F5E9A978C6F20A6D44D7453` |
| `3.json` | `15299988DA323E9C0C434CC3087308D92605DA12A7AAEAD132E52B2AF7E162F2` |
| `4.json` | `933572FA5CEC8A9B41BEA84B905BCB0A091CB7C8B69C425B4981F5668DB8FE22` |
| `5.json` | `A73B70A1104970092D4155707F3C45429DA5546B5B020A5A6400AF7B33E0C9F9` |
| `6.json` | `53CD92CFDFCD3826217ED5C093EC8F639EEDF45FE0F2A3AD56DE643EF75F6711` |
| `7.json` | `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03` |

Los seis hashes heredados coinciden exactamente con la auditoría de Room v6.
El esquema nuevo posee `identityHash=b929a5fc400b06412aabee1764319fbb`.

## Pruebas finales

Comandos ejecutados de forma serializada con `--max-workers=1`:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks :core:domain:testDebugUnitTest :core:database:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks :core:database:connectedDebugAndroidTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
```

Resultado final:

- aplicación JVM: 41/41;
- dominio JVM: 217/217;
- base de datos JVM: 5/5;
- total JVM: 263/263;
- base de datos instrumentada: 98/98 en Samsung `SM-S938B` API 36;
- lint, APK `debug`, APK de instrumentación del módulo y APK de pruebas QA:
  aprobados;
- cero fallos, errores u omitidas;
- `git diff --check`: correcto.

La instrumentación física cubrió migración directa `6→7`, cadena `1→7`, base
nueva, restricciones, rollback, ruta ausente, reapertura, catálogo, adopción,
normalización, reglas, recientes, jornada–fotografía, reemplazos y corrupción
externa controlada. También cubrió reemplazo entre sectores, reintento de
adopción después de retrocarga, precisión temporal y bloqueo transaccional de
escritores heredados. Al finalizar, la lista de paquetes mostró únicamente
`com.blackatsystems.miguardia`; la aplicación productiva no se abrió, reinstaló
ni modificó.

## Incidentes y contradicciones resueltos

- el dominio recibido resolvía una configuración V2 sin conservar explícita la
  fecha de referencia, clasificaba una edición como inserción y no protegía
  todos los cruces de línea temporal/sector; se cerraron contratos y regresiones;
- adopción, archivo de padres, actualización de identidades y retrocarga tenían
  casos incompletos; se hicieron explícitos y transaccionales;
- la revisión cruzada detectó que las APIs heredadas de actualización y
  Novedades podían alterar una jornada V2 sin actualizar su fotografía; las
  rutas estructurales quedaron bloqueadas y el cambio de estado quedó limitado
  a `status` y `updatedAt`;
- la adopción reutilizada se comparaba sólo con la regla cronológicamente más
  antigua y dejaba de ser idempotente después de una retrocarga; ahora conserva
  y reconoce la regla original exacta;
- la auditoría de filas externas ahora vuelve a validar toda la jornada V2,
  exige horarios al minuto e instantes coherentes, sin confundir la procedencia
  histórica de `Shift` con el vínculo actual nullable de la plantilla;
- la primera compilación instrumentada encontró dos métodos JUnit cuyo retorno
  Kotlin no era `Unit`; falló antes de ejecutar las nuevas pruebas, se corrigió
  el montaje y se repitió la batería completa;
- una revisión final propuso impedir que un lote V2 borrara una jornada V1. La
  comparación con el contrato activo mostró que los reemplazos confirmados sí
  pueden borrar jornadas V1 o V2. Esa interpretación se retiró antes del
  checkpoint y la batería física se repitió sobre el comportamiento correcto;
- una sonda física final previa al cierre ejecutó 98 casos y expuso dos fallas:
  una expectativa de test demasiado específica y una validación incorrecta que
  olvidaba que la jornada preserva la procedencia V1 aunque la plantilla pase a
  `null`. Ambas se corrigieron y la corrida definitiva aprobó 98/98;
- la tarea pausada proponía y coordinaba como MAIN. Su autoridad quedó anulada
  por la instrucción actual de Joaquin y por la jerarquía documental; no se
  heredó ese rol ni se enviaron nuevas órdenes a esa tarea.

No quedaron contradicciones funcionales abiertas dentro del Corte A.

## Límites preservados y pendiente

- no se modificaron Gradle, dependencias, manifiestos, permisos,
  `applicationId`, versiones ni DataStore;
- no se agregó red, nube, cuenta, telemetría, datos clínicos, tablas salariales,
  montos ni liquidaciones;
- no se ejecutó instrumentación Compose, API 26 ni QA manual de aplicación,
  porque esas superficies pertenecen al Corte B visible;
- el prompt completo continúa activo: el próximo paso es implementar el Corte
  B de sector, catálogo, primera configuración y carga manual sobre la única
  grilla existente;
- push, tag, Release, publicación y producción continúan como puertas separadas.
