# Auditoría MAIN — Bloqueo de acceso local V2

- Fecha: 2026-09-01
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `2d6534da7a8b26774605c1c75339b0453da4fa2c`
- Resultado: **APROBADO — INTEGRADO Y VERIFICADO LOCALMENTE POR MAIN**
- Push: no autorizado y no ejecutado

## Puerta 0

MAIN verificó antes de integrar:

- ruta y rama exactas;
- HEAD de entrada y upstream `origin/codex/miguardia-2.0`;
- rama 15 commits adelantada y 0 detrás;
- `main`, `origin/main` y `v1.0.0^{}` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor `joaquin <blackat.systems@gmail.com>` y remoto privado esperado;
- índice vacío, 9 worktrees históricos preservados y candidato sin commit;
- 12 archivos rastreados modificados y 16 archivos nuevos, sin eliminaciones.

Se leyeron las fuentes rectoras, el ADR 0036, el prompt especializado, los
contratos de Copias, Widget, navegación, ciclo de vida y las pruebas afectadas.
No se creó rama, worktree, tarea implementadora ni proyecto adicional.

## Alcance aceptado

El bloque implementa una opción visible `Bloqueo de acceso`, desactivada por
defecto, con cuatro plazos exactos: inmediato, 1, 5 y 15 minutos. Usa la
autenticación oficial del teléfono —biometría fuerte o credencial segura— y no
crea PIN, contraseña, hash ni secreto propio de MiGuardia.

La puerta protege `MainActivity` y `WidgetConfigurationActivity`. Mientras está
cerrada, no compone el Calendario, Resumen ni otra superficie laboral. Los
destinos entrantes quedan sólo en memoria, se revalidan y se consumen una vez.
El ajuste vive en un DataStore exclusivo del dispositivo y permanece fuera de
las diecisiete preferencias transportadas por `.miguardia-backup`.

Los 28 archivos ejecutables aceptados se distribuyen en:

- Gradle y manifiesto: AndroidX Biometric 1.1.0 y permiso normal
  `USE_BIOMETRIC`;
- ocho componentes nuevos bajo `app/.../security/`;
- integración acotada en Application, ambas Activities, raíz Compose, menú y
  textos;
- dieciséis archivos nuevos en total, incluidos tests JVM e instrumentados;
- regresiones vecinas de navegación, Widget y recuperación de Copias.

No se modificaron `core/domain`, `core/database`, Room, esquemas, migraciones,
formato de copias, permisos peligrosos, cuentas, red, nube ni telemetría.

## Auditoría y correcciones de MAIN

El handoff no fue aceptado por afirmación. MAIN revisó cada hunk, encargó
revisiones independientes y corrigió seis grupos de defectos de seguridad o
ciclo de vida:

1. un resultado de autenticación tardío podía aceptarse después de
   `Bloquear ahora`, un bloqueo físico o la pérdida de credencial segura;
2. una escritura de configuración podía terminar sin volver a comprobar la
   autorización en la frontera atómica;
3. el plazo podía perder el tiempo ya transcurrido fuera de primer plano o
   comenzar en un instante incoherente durante la activación;
4. un destino pendiente podía descartarse cuando la puerta se cerraba antes de
   consumirlo;
5. el respaldo a PIN, patrón o contraseña en API 26–29 podía perderse después
   de recrear la Activity;
6. la protección de fondo/Recientes podía retirarse mientras la Activity seguía
   pausada, y el bloqueo del teléfono podía crear una puerta falsa con la función
   desactivada.

También se corrigió el puente de resultados de permisos para compatibilizar
Activity/Compose modernos con `FragmentActivity 1.2.5`, transitiva de Biometric
1.1.0. El Intent normal del launcher conserva acción, categorías y flags sin
transportar extras sensibles.

Después de las correcciones, dos auditorías independientes de sólo lectura
aprobaron el diff sin findings residuales. Una repetición dirigida independiente
pasó 45/45 pruebas; la selección completa de MAIN pasó 50/50.

## Autenticación, ciclo de vida y punto de no retorno

La mutación protegida de DataStore define una frontera explícita:

- si aparece un límite de seguridad antes de iniciar el transform autorizado,
  la operación se aborta;
- una vez iniciado el transform, la operación autenticada termina de forma
  determinista;
- si el resultado continúa habilitado, un límite de seguridad mantiene la
  sesión cerrada;
- si `Desactivar` o `Reparar` ya cruzaron el transform, prevalece el cambio
  autenticado;
- una activación confirmada mientras la app está en segundo plano comienza su
  plazo en el instante del commit.

La sesión autenticada nunca se persiste. Proceso nuevo, muerte del proceso o
bloqueo físico obligan a resolver de nuevo la puerta cuando está habilitada.
Configuración nula, incompatible o con error falla cerrada; configuración
explícitamente desactivada no inventa un bloqueo.

## Privacidad, Copias y Room

Verificado:

- la puerta cerrada no compone contenido laboral ni lo deja en semántica;
- API 33+ deshabilita la fotografía de Recientes; API 26–32 conserva cobertura
  y `FLAG_SECURE` mientras corresponde;
- Widget y Notificaciones mantienen sus controles de privacidad independientes;
- Copias continúa transportando exactamente diecisiete preferencias semánticas;
- combinar, reemplazar o recuperar un journal no cambia el ajuste de bloqueo ni
  crea una sesión autenticada;
- no existen usos productivos de `BIOMETRIC_WEAK`, logs, `printStackTrace`,
  `fallbackToDestructiveMigration` ni `allowMainThreadQueries`;
- no se encontraron archivos con nombres de secretos o credenciales dentro del
  candidato.

Room permanece en versión 5, con 27 tablas e `identityHash`
`77adbc875d0f4ee466cdbd0dd74d5c5c`. Los esquemas permanecen byte a byte
intactos:

| Esquema | SHA-256 |
|---|---|
| 1 | `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E` |
| 2 | `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50` |
| 3 | `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428` |
| 4 | `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B` |
| 5 | `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4` |

## Validación local final

Comando ejecutado desde MAIN, serializado y con repetición real:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Resultado: `BUILD SUCCESSFUL in 15m 26s`, 351/351 tareas ejecutadas.

| Suite JVM | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| `core:domain` | 377 | 0 | 0 | 0 |
| `core:database` | 12 | 0 | 0 | 0 |
| `app` | 264 | 0 | 0 | 0 |
| **Total** | **653** | **0** | **0** | **0** |

Lint terminó con 0 errores y 6 avisos de versiones disponibles. Los avisos de
APIs de prueba de `RemoteViews` y del helper de migraciones son heredados y no
detuvieron la compilación.

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 17.519.136 | `8A4F159B1BCB1096D6059E0091BA3A8C9EBDA5B8B8F2DF5A1E6BF04B825C1F44` |
| QA | 17.404.053 | `8ED40E781561EA73160A51101B65313192464651122D8E809BB886D6B607D345` |
| Release sin firma | 12.399.164 | `BFA32D3704932CE7A94D643344222247B8855419F55AE183B4E0FB3D4B85153C` |
| QA AndroidTest | 2.030.416 | `27E5D693BBF53F86B9EA519EC8D504DA7014B63D4DE2B9F278E0CD6ED36755A8` |
| Room AndroidTest | 4.160.904 | `7C2C78E6400DFC352A9E2B2C25F945F702BD5B6721A9E689F68C2CD181EE5E72` |

`git diff --check` quedó limpio.

## QA Samsung autorizada

Dispositivo: Samsung `SM-S938B`, Android 16/API 36, serial
`R5CY529W6PL`. Se instalaron únicamente los APK QA y QA.test.

La matriz final, repetida después de todas las correcciones, pasó 31/31:

- puerta Compose y ausencia de contenido sensible: 4;
- manifiesto, permiso y DataStore del bloqueo: 3;
- menú y navegación: 7;
- actividad exportada de configuración del Widget: 5;
- recuperación de Copias: 10;
- preferencias portables: 2.

Durante la auditoría se recorrió además con el diálogo real de Samsung:
activación, autenticación del sistema, `Bloquear ahora`, cancelación, reintento,
retorno desde fondo, muerte de proceso, apertura fría, bloqueo de pantalla y
desactivación autenticada. Ese recorrido manual precedió al endurecimiento final
de las carreras; la matriz automatizada 31/31 sí fue repetida sobre los binarios
finales.

La jerarquía de Recientes expuso sólo `MiGuardia` y la ventana en segundo plano
conservó `FLAG_SECURE`. No se tomó una captura que pudiera incluir otras apps;
por eso la inspección visual OEM de la tarjeta permanece pendiente y no se
presenta como verificada.

## Seguridad y estado final del dispositivo

- QA queda instalada, detenida y con datos ficticios preservados;
- QA.test fue desinstalada y está ausente;
- orientación conservada en `accelerometer_rotation=0` y `user_rotation=0`;
- no se consultaron ni modificaron fuente, densidad o tamaño visual del sistema;
- no se modificaron credenciales, red, hora, zona ni ajustes de captura;
- producción no fue abierta, instalada, limpiada, reemplazada ni desinstalada;
- no se usaron datos reales y no se provocaron fallos biométricos repetidos.

## Pendientes explícitos

No bloquean este checkpoint:

- recorrido compatible en API 26 y API 33;
- inspección visual OEM de la tarjeta de Recientes;
- aviso físico hacia un destino exacto y retorno físico desde SAF;
- esperas humanas completas de 1, 5 y 15 minutos;
- reinicio físico, que conserva autorización separada.

## Git y cierre

Antes del cierre documental, el candidato permanecía sin staged y sin commit
sobre el HEAD de entrada. MAIN actualiza en este mismo bloque MAPA,
PLANIFICACIÓN, STATUS, el índice, el prompt especializado y el coordinador
secuencial. Ayuda y recorrido inicial 2.0 queda recomendado, pero no habilitado
ni iniciado.

No hubo push, tag, Release, merge, rebase, reset, descarte, cambio de `main` ni
acción sobre producción. El checkpoint local se crea únicamente después de
revisar el staging exacto de este bloque.
