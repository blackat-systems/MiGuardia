# Auditoría MAIN — Integración y depuración de cambios de último momento V2

- Fecha: 2026-09-03
- Veredicto de la corrida: **CANDIDATO INTEGRAL VERDE**
- Integración posterior: checkpoint local
  `95ebf531d71b8b781423475a1c38d15a8bd24742`
- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD preservado: `f8ddbe2754bad62df43d1cef3e1f0c6b3bcb2352`
- Upstream: `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`

## Alcance recibido

MAIN auditó como una sola unidad el candidato compartido de Ayuda y recorrido
inicial, simplificación de formularios, ubicación puntual, clima por objetivo,
Widget, Room V6 y compatibilidad de Copias. No se reconstruyeron piezas desde
worktrees históricos ni se descartaron cambios del checkout.

Puerta 0 confirmó ruta, rama, HEAD, upstream, autor, remoto, staging vacío y las
referencias protegidas. `main`, `origin/main` y `v1.0.0^{}` permanecieron en
`82db6fd8eb2c511205968894dc9857a96b16ed20`.

## Correcciones de MAIN

La revisión independiente encontró y MAIN cerró estos defectos reproducibles:

- edición automática de horas y fechas que reponía caracteres borrados o podía
  dejar una hora corta visualmente bloqueada;
- requisitos obligatorios ocultos dentro de Opciones avanzadas;
- vocabulario residual `Habituales` en Informes;
- callbacks tardíos de ubicación capaces de escribir sobre un formulario que
  ya había cambiado de paso, dirección o superficie;
- invalidación de clima que podía borrar o reponer resultados de otro objetivo;
- títulos del primer lugar que no respetaban el vocabulario sectorial;
- referencias activas de documentación que todavía describían Room V5.
- cierre físico al pedir ubicación aproximada: AndroidX Biometric 1.1.0
  resolvía transitivamente Fragment 1.2.5, que rechazaba el código moderno de
  `ActivityResultRegistry` antes de entregar el callback de permiso;
- mensaje transitorio `Guardando ubicación…` que permanecía visible después de
  que el formulario ya contenía una ubicación confirmada.

Se fijó explícitamente `androidx.fragment:fragment:1.9.0`, se retiró el puente
manual que no podía interceptar aquel cierre y se agregó una regresión que
ejecuta el launcher real. Fragment 1.9.0 es una biblioteca oficial AndroidX con
licencia Apache 2.0; no agrega red, permisos, almacenamiento ni telemetría. La
alternativa de conservar Fragment 1.2.5 y adaptar el callback fue descartada
porque la excepción ocurre antes del callback de la aplicación. El APK QA
final creció 163.864 bytes respecto del artefacto anterior a esta corrección,
un 0,93 %; esa diferencia incluye también las correcciones finales de código.

Se agregaron regresiones JVM y AndroidTest para edición, ubicación, clima,
permisos y vocabulario. Las revisiones independientes no dejaron findings P0,
P1, P2 o P3 abiertos dentro del alcance auditado.

## Validación local final

Comando ejecutado con `--rerun-tasks`, sin paralelismo de workers:

```text
:core:domain:test
:core:database:testDebugUnitTest
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleQa
:app:assembleRelease
:app:assembleQaAndroidTest
:core:database:assembleDebugAndroidTest
```

Resultado posterior a todas las correcciones: **BUILD SUCCESSFUL**, 351/351
tareas ejecutadas desde cero en 18m55s.

- dominio: 379/379;
- base local: 12/12;
- aplicación: 316/316;
- total JVM: 707/707, sin fallos, errores ni omitidas;
- lint: 0 errores y 6 avisos de versiones disponibles;
- Debug, QA y Release sin firma: compilados;
- AndroidTest de aplicación QA y base Debug: compilados;
- `git diff --check`: limpio.

Artefactos finales:

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 17.880.492 | `CAC9449777796AB4C031EFB559F92328052373B356DFDDB991F3590C62584328` |
| QA | 17.765.397 | `E977871E39FA742F63C7EB07A150ECBF659B2D2A3D4DB1A0F35ABA3DDA124CD1` |
| Release sin firma | 12.596.605 | `9BA92C5B04D25DB7546322655BEAE19E8C6541949875E79B69062811D1DE6750` |
| QA AndroidTest | 2.090.266 | `C86BD38AE72DDA6CE8387FDC40E72D5B0FF04B88A512A058B4F6B45F3328A17F` |
| Database AndroidTest | 4.178.918 | `049585D6BE06E78AA4288BA450751C23C4EF364859CDF179984D34F2232AAB4A` |

Una compilación preliminar de la regresión Compose nueva falló porque la prueba
encadenaba una acción que devuelve `Unit` con una aserción. MAIN corrigió sólo
esa prueba y la compilación posterior, seguida por la batería global, quedó
verde. Ese intento no se presenta como evidencia positiva.

## Room y Copias

Room quedó verificado en versión 6 con:

- 27 tablas/entidades;
- 0 vistas Room;
- 2 consultas de preparación del esquema;
- `identityHash = 7eb39f6fab5a44e69350e206716554be`;
- migración explícita `5→6`;
- columnas nuevas `weatherLatitude` y `weatherLongitude`, ambas nulas al migrar;
- esquemas 1–5 intactos;
- esquema 6 con SHA-256
  `BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268`.

El formato lógico de Copias V5 continúa compatible: al leerlo en V6 las nuevas
coordenadas quedan nulas. No se agregó migración destructiva ni escritura Room
desde superficies de consulta.

## QA física en Samsung API 36

Joaquin autorizó expresamente el Samsung `SM-S938B`, serie
`R5CY529W6PL`, con paquetes QA y datos ficticios. La instrumentación y el
recorrido real produjeron estas evidencias:

- Room V6 completa, migraciones, reapertura, integridad y Copias: 123/123;
- matriz segura de Calendario, formularios y Compose: 108/108;
- clima, Widget y consultas de sólo lectura: 41/41;
- Copias y Bloqueo: 37/37;
- después de corregir Fragment, matriz dirigida de permisos, configuración y
  clima multiobjetivo: 30/30;
- Ayuda contextual y DataStore de primera apertura, repetidos después de la
  batería final: 9/9;
- repetición final de Room posterior al arreglo: 123/123.

El recorrido manual confirmó:

- permiso aproximado concedido y rechazado sin cierre de la aplicación;
- retorno correcto desde Ajustes;
- captura puntual de ciudad actual iniciada por la persona;
- conversión consciente de una dirección ficticia mediante Geocoder,
  previsualización y confirmación antes de guardarla;
- dos objetivos ficticios con ubicación y clima aislados;
- mensaje final correcto de ubicación guardada, sin quedar detenido en
  `Guardando ubicación…`;
- apertura y cancelación seguras de Fotos, Abrir copia, Crear copia y guardar
  Informe mediante selectores reales de Android;
- solicitud y concesión real del permiso de notificaciones;
- apertura y cancelación del diálogo biométrico real de Samsung, conservando
  Bloqueo apagado;
- producción ausente y nunca instalada, abierta, consultada o limpiada.

Durante un intento de ejecutar toda la suite de aplicación se iniciaron
fixtures Activity que llaman conscientemente a la limpieza del paquete QA. La
corrida fue detenida, pero nueve fixtures ya habían vaciado las 27 tablas Room
del paquete QA. Esos datos eran exclusivamente sintéticos; no existía una copia
para recuperarlos. DataStore, preferencias y fotografías no fueron limpiados,
y producción estaba ausente. La QA posterior reconstruyó únicamente datos
ficticios. Este incidente no se presenta como una prueba verde ni se oculta del
registro.

Después de reconectar el Samsung, la repetición combinada final pasó 30/30 en
una sola invocación. API 26 y API 33 permanecen pendientes de la matriz de
compatibilidad; no bloquean el cierre funcional en el Samsung principal. El
disparo de una alarma exacta y el reinicio físico siguen siendo puertas
separadas.

## Privacidad y dispositivos

- Sólo se declara `ACCESS_COARSE_LOCATION` para la captura puntual iniciada por
  la persona.
- No se agregó ubicación precisa, en segundo plano, tracking, Maps o Places.
- Geocoder se usa sólo después de una acción consciente y requiere confirmar el
  resultado.
- Sin coordenadas no se inventa un clima predeterminado.
- No se consultó ni modificó `font_scale`, densidad, tamaño visual del sistema,
  Wi-Fi, hora o zona.
- Sólo se usaron `com.blackatsystems.miguardia.qa`,
  `com.blackatsystems.miguardia.qa.test` y
  `com.blackatsystems.miguardia.core.database.test`.
- Al cerrar, los dos paquetes temporales de prueba fueron desinstalados y
  verificados como ausentes. Sólo queda `com.blackatsystems.miguardia.qa`,
  detenida y con datos ficticios. Producción permanece ausente.

## Estado Git final

- ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD preservado: `f8ddbe2754bad62df43d1cef3e1f0c6b3bcb2352`;
- upstream: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- divergencia: 17 adelante, 0 atrás;
- 97 archivos rastreados modificados, 26 nuevos y 0 eliminados;
- 123 rutas candidatas en total;
- diff rastreado: 3.778 inserciones y 1.075 eliminaciones;
- staging vacío;
- `git diff --check`: limpio;
- 0 rutas sensibles o binarios generados entre los cambios;
- 0 marcadores de claves privadas o tokens reconocibles en el diff rastreado;
- autor: `joaquin <blackat.systems@gmail.com>`;
- remoto privado esperado: `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` continúan en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.

No hubo commit, push, tag, Release, merge, rebase, reset, descarte, rama ni
worktree nuevos.

Una auditoría independiente final y de sólo lectura revisó las dos correcciones,
Room V6, Copias, permisos, documentación y evidencia. No encontró defectos
P0–P2, contradicciones ni cambios fuera de alcance y emitió el veredicto
`APROBADA — LISTA PARA CHECKPOINT LOCAL`. Su único P3 —el estado antiguo de
ADR 0038— fue corregido antes de este cierre.

## Estado al terminar esta auditoría

Al terminar esta corrida, el candidato continuaba deliberadamente sin staging
ni commit. La QA física
obligatoria en el Samsung principal fue ejecutada y cerró dos defectos reales.
La batería local posterior quedó verde y no existen findings P0–P2 abiertos.
El bloque queda funcionalmente cerrado y listo para una auditoría integral
final de sólo lectura antes del checkpoint local.

En ese momento, commit, push, tag, Release, `main` y producción continuaban como
puertas separadas.

## Cierre posterior de MAIN

Después de esta auditoría y de su QA física, MAIN confirmó el candidato completo
en `95ebf531d71b8b781423475a1c38d15a8bd24742`. No hubo push, tag, Release ni
operación sobre `main` o producción. La auditoría integral posterior está en
`docs/audits/2026-09-03-auditoria-final-aplicacion-y-candidato-local-v2.md`.
