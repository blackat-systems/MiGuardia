# Auditoría integral del núcleo y compatibilidad Android V2 — cierre

- Fecha: 2026-08-29
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD auditado: `3385c15586ba9af706452f5df540dc3f305da99f`
- Base funcional: `c35fffb2abe99eac73e164f99147bf95d11ad83d`
- Veredicto: **NÚCLEO APTO PARA SEGUNDA CAPA**
- Findings abiertos: **ninguno**

## Resultado

La repetición integral cerró los tres huecos de cobertura detectados por la
auditoría parcial y completó la matriz obligatoria en Samsung API 36, Android 8
API 26 y Android 13 API 33. No se reprodujeron defectos P0, P1, P2 ni P3.

El núcleo V2 puede recibir la segunda capa en el orden acordado: Widget,
informes, copias y restauración locales, bloqueo y Ayuda y recorrido inicial
2.0. Esta aprobación no publica la aplicación ni habilita por sí sola otro
prompt, push, tag, Release, `main` o producción.

## Procedencia exacta

El código funcional, Gradle y los manifiestos son idénticos entre
`c35fffb2abe99eac73e164f99147bf95d11ad83d` y el HEAD auditor
`3385c15586ba9af706452f5df540dc3f305da99f`; ese tramo contiene únicamente
documentación de coordinación. La batería, los APK y la matriz Android
corresponden por lo tanto al mismo candidato funcional. Esta equivalencia fue
comprobada contra el diff real y no inferida desde el handoff.

## Batería local

Comando serializado y forzado:

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

Resultado:

- `BUILD SUCCESSFUL` en 17 min 50 s;
- 351/351 tareas ejecutadas;
- dominio: 303/303;
- database JVM: 12/12;
- app JVM: 184/184;
- total JVM: 499/499, sin fallos, errores ni omitidas;
- lint: 0 errores y 6 avisos de versiones disponibles fuera del diff;
- APK Debug, QA, Release sin firma y ambos AndroidTest compilados;
- `git diff --check`: limpio.

## Tres barreras cruzadas

Las pruebas correctivas integradas quedaron ejecutadas:

1. una misma fotografía determinista reconcilia Calendario, Horas, Resumen,
   tarjeta, próximo evento y avisos sin duplicaciones;
2. dos escritores Room que parten de una fotografía común producen un solo
   ganador, un conflicto y ninguna fila parcial;
3. consultar Calendario, Resumen y tarjeta deja intactas las 27 tablas, sus
   timestamps, conteos, valores tipados y huella lógica, incluso al reabrir.

La carrera CAS forma parte de Room 108/108. La consulta sin escrituras forma
parte de la suite de aplicación y también fue ejecutada aisladamente durante
su cierre.

## Room y DataStore

- base productiva V2: `miguardia-v2.db`;
- Room: versión 5, 27 entidades/tablas;
- `identityHash`: `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- migraciones explícitas `1→2→3→4→5`;
- `fallbackToDestructiveMigration`: 0;
- `allowMainThreadQueries`: 0;
- esquemas 1–5 intactos;
- Resumen no persiste totales derivados;
- DataStore conserva preferencias separadas y no reemplaza la historia Room.

Hashes de esquemas:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## Matriz Android

### Samsung `SM-S938B` — API 36

- Room completa: 108/108;
- aplicación completa permitida: 235/235;
- se excluyó únicamente el caso que dispara una alarma exacta real;
- permisos de notificaciones denegado, concedido y bloqueado desde Android:
  verificados;
- tema claro/oscuro, retrato/paisaje y zoom interno 100/150/200: verificados;
- orientación final restaurada a `accelerometer_rotation=1` y
  `user_rotation=0`.

Recorrido humano continuo sobre una sola historia ficticia:

1. configuración de Enfermería;
2. lugar, tipo y horario recurrentes;
3. plan recurrente y jornada materializada;
4. jornada pasada con horario real 08:30–16:00;
5. extra independiente 08:00–16:00, clase `Horas extras`;
6. `Guardia pasiva` 08:00–12:00;
7. Calendario con jornada, extra y disponibilidad diferenciados;
8. Resumen reconciliado: 15 h 30 min totales = 7 h 30 min habituales + 8 h
   extra; 4 h de disponibilidad permanecen separadas;
9. Notificaciones activadas, permisos concedidos y alarmas laborales inexactas
   programadas sin dispararlas.

### Android 8 — API 26

- Room completa: 108/108;
- recorrido esencial de aplicación: 27/27;
- carrera CAS y consulta sin escrituras incluidas;
- reemplazo real del paquete QA y reconstrucción de alarmas verificados;
- instalación limpia y recorrido esencial verificados.

### Android 13 — API 33

- matriz afectada: 24/24;
- permiso runtime denegado/concedido, bloqueo desde Android y restauración:
  verificados;
- preferencia de puntualidad con acceso exacto denegado: fallback inexacto
  verificado;
- se usó una fixture Activity adicional únicamente para preparar datos
  ficticios de la comprobación visual de privacidad.

Privacidad real en pantalla bloqueada:

- se eligió `Oculta: mensaje genérico`;
- se publicó la notificación ficticia incorporada por la aplicación;
- Android informó `vis=PRIVATE` y una `publicVersion` pública genérica;
- la pantalla bloqueada mostró sólo `MiGuardia — Tenés un aviso de MiGuardia.`;
- no mostró jornada, lugar, horario, puesto, dirección, nota ni motivo médico;
- el PIN ficticio temporal del AVD fue eliminado al terminar.

## Seguridad de dispositivos y producción

- se usaron únicamente paquetes QA/test y datos ficticios;
- producción estaba ausente y no fue instalada, abierta ni modificada;
- el Samsung terminó sin paquetes `com.blackatsystems.miguardia*` en los
  usuarios 0 y 10;
- API 26 y API 33 terminaron sin paquetes MiGuardia y apagados;
- el AVD y la imagen oficial API 33 autorizada se conservaron como
  infraestructura de prueba;
- no se consultaron ni modificaron `font_scale`, densidad ni tamaño visual del
  sistema;
- no se disparó una alarma exacta real ni se reinició físicamente el Samsung.

## Auditorías independientes

Dos revisiones independientes y de sólo lectura emitieron el mismo veredicto
después de cerrar la evidencia física final:

```text
NÚCLEO APTO PARA SEGUNDA CAPA
FINDINGS: ninguno
```

## Pendientes no bloqueantes

- API 37 permanece para la auditoría de la aplicación completa;
- el disparo de una alarma exacta real conserva autorización separada;
- un reinicio físico del Samsung conserva autorización separada.

Estos tres puntos no contradicen contratos públicos ni bloquean la segunda
capa.

## Git

Antes de registrar este cierre:

- rama: `codex/miguardia-2.0`;
- HEAD: `3385c15586ba9af706452f5df540dc3f305da99f`;
- upstream: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- divergencia: 7 adelante, 0 detrás;
- `main`, `origin/main` y `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- checkout: limpio, sin staged ni archivos sin seguimiento.

No hubo push, tag, Release, merge, rebase, reset, descarte ni acción sobre
`main` o producción.

## Próximo paso

El siguiente bloque de la segunda capa es **Widget**. MAIN debe preparar su
contrato sólo cuando Joaquin lo pida; este cierre no crea ni habilita por sí
solo otra tarea.
