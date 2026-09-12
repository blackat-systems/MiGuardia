# Auditoría integral del núcleo y compatibilidad Android V2 — resultado parcial

- Fecha: 2026-08-28
- Resultado: **AUDITORÍA PARCIAL — NO CERRABLE**
- Dependencia auditora:
  `docs/prompts/AUDITORIA_INTEGRAL_DEL_NUCLEO_Y_COMPATIBILIDAD_ANDROID_V2.md`
- HEAD auditado: `7570d25421d532a4dd25a03dae3b3cb586a7d8f1`
- Base funcional: `55dcd60aba2512597d3074f9978f228086ddf7ea`
- Rama: `codex/miguardia-2.0`

## Resultado ejecutivo

La dependencia independiente no reprodujo defectos P0/P1 ni contradicciones
materiales entre producto, dominio, persistencia y superficies V2. La batería
local quedó verde y MAIN corroboró sus resultados, los contratos estructurales,
Room, seguridad y el estado Git.

La puerta no puede cerrarse todavía. Faltan tres barreras regresivas exigidas
por el contrato y no se ejecutó la matriz física actual en Samsung API 36,
Android 8/API 26 y Android 13/API 33.

La segunda capa permanece cerrada. Este resultado no autoriza a reejecutar la
auditoría, abrir widget/informes/copias/bloqueo/Ayuda ni publicar la rama.

## Puerta 0 verificada por MAIN

- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD: `7570d25421d532a4dd25a03dae3b3cb586a7d8f1`.
- Upstream: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`.
- Divergencia inicial: 0 detrás, 3 delante.
- `55dcd60` es ancestro de HEAD.
- `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`.
- Autor: `joaquin <blackat.systems@gmail.com>`.
- Checkout inicial: limpio, sin staged ni archivos no rastreados.
- Worktrees históricos: preservados.
- La diferencia `55dcd60..7570d25` contiene únicamente documentación; el código
  funcional es idéntico.

## Evidencia local

La dependencia ejecutó con `--rerun-tasks`:

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

Resultado informado y corroborado contra XML y artefactos:

- `BUILD SUCCESSFUL` en 15 min 40 s; 351 tareas ejecutadas;
- dominio: 302/302;
- database JVM: 12/12;
- app JVM: 184/184;
- total JVM: 498/498, sin fallos, errores ni omitidas;
- lint: 0 errores y 6 advertencias de actualización;
- APK Debug: 15.619.796 bytes;
- APK QA: 15.504.697 bytes;
- APK Release sin firma: 10.923.855 bytes;
- APK AndroidTest app QA: 1.799.228 bytes;
- APK AndroidTest database: 3.894.425 bytes;
- inventario AndroidTest: 235 casos de app y 107 de database;
- `git diff --check`: limpio.

El tiempo y el total de tareas proceden del handoff; no quedó un log durable que
permita reconstruir esas dos cifras. Los XML, informes y artefactos sí respaldan
los resultados finales enumerados.

MAIN volvió a ejecutar el mismo grafo sin `--rerun-tasks`. Dio `BUILD
SUCCESSFUL` en 52 s, con 2 tareas ejecutadas y 349 vigentes. Esta segunda pasada
confirma el ensamblado y el estado del grafo; los conteos de pruebas provienen
de los XML regenerados por la dependencia.

## Hallazgos confirmados

### P2 — falta una fotografía transversal única

No existe una misma fixture determinista que combine:

- jornada materializada por recurrencia;
- horario real;
- fragmento extra de jornada;
- extra independiente;
- disponibilidad coincidente;
- protección vecina;
- mismos UUID, reloj y zona.

Las pruebas existentes cubren esas piezas por separado, pero ninguna
reconcilia desde la misma fotografía Calendario, Horas, Resumen, tarjeta,
próximo evento y avisos.

No se observó una divergencia funcional. El hueco es P2 porque incumple una
puerta obligatoria y podría permitir que varias superficies se separen sin que
sus suites aisladas fallen.

### P2 — falta una carrera CAS real entre dos escritores

Existen pruebas sólidas de expectativa obsoleta, conflicto secuencial y
rollback. No existe una prueba instrumentada donde dos escritores partan de la
misma fotografía, se liberen mediante una barrera y compitan realmente.

El cierre debe demostrar:

- exactamente un éxito;
- exactamente un conflicto controlado;
- ninguna fila parcial ni huérfana;
- integridad y reapertura posteriores.

No se reprodujo un defecto CAS. La severidad P2 corresponde al riesgo de
integridad y a la exigencia contractual, no a daño observado.

### P3 — falta demostrar consultas sin escrituras

La arquitectura separa lectura y escritura, y los DAO recorridos usan
consultas. Sin embargo, no existe una prueba que tome una fotografía lógica o
use `total_changes`/`data_version`, consulte Calendario, Resumen y tarjeta,
reabra la base y demuestre que no cambió ninguna fila, timestamp o versión.

No existe indicio de escritura indebida. Falta una barrera regresiva explícita.

## Contratos estructurales corroborados

- Room V2 versión 5, 27 entidades y `exportSchema=true`.
- Base exclusiva: `miguardia-v2.db`.
- Migraciones explícitas `1→2→3→4→5`.
- `ShiftRepository`, `ShiftDao` y `RoomShiftRepository` son de lectura.
- Las escrituras de jornadas están confinadas a `RoomV2ShiftRepository`
  mediante `V2ShiftDao` y transacciones V2.
- Horas y Resumen reutilizan `calculateHoursContributions()` y
  `summarizeHoursContributions()`.
- `NextEventObserver` obtiene el evento mediante `projectNextEvent()` y luego
  construye la tarjeta; `NotificationReconciler` usa la misma proyección para
  `buildNotificationPlan()`.
- Resumen no persiste totales derivados.
- No existen rutas productivas `MIGRATED_V1`, activación V1→V2 ni uso de
  `miguardia.db`.

Hashes Room verificados:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

`identityHash` v5: `77adbc875d0f4ee466cdbd0dd74d5c5c`.

## Android, seguridad y privacidad

- `minSdk 26`, `compileSdk 37`, `targetSdk 37` y Java 17.
- Permisos exactos: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED` e `INTERNET` para clima.
- `allowBackup=false`; reglas de backup/transfer excluyen los datos.
- `usesCleartextTraffic=false`.
- Receivers internos no exportados.
- Sin secretos, keystores, APK, bases, datos reales ni logs productivos
  sensibles rastreados.
- API 26 tiene imagen instalada.
- API 33 exacta no tiene imagen instalada.
- El AVD Pixel API 37.1 está configurado, pero su imagen falta.

## Evidencia física

Durante esta auditoría:

- ADB no fue ejecutado;
- no se inició ningún dispositivo o emulador;
- no se instaló, abrió, limpió ni desinstaló ningún paquete;
- instrumentación ejecutada: 0;
- revisión física humana: 0.

La auditoría durable del 2026-08-27 conserva evidencia heredada sobre la misma
base funcional: Samsung API 36, Room 107/107, app 233/233 antes del aislamiento,
matriz final 84/84 y una matriz API 26 de 20/20. Esa evidencia no fue repetida.

Los XML conectados conservados en `build/` son antiguos. Database conserva
107/107, pero el único XML de aplicación disponible conserva 84 casos con un
fallo histórico anterior al APK final. Esto no demuestra una regresión actual,
pero impide usar esos XML como evidencia verde del HEAD auditado. Los resultados
físicos del 2026-08-27 se clasifican únicamente como `HEREDADO, NO REPETIDO`.

## Siguiente paso recomendado

Preparar, sólo cuando Joaquin lo indique, una dependencia correctiva única y
acotada a pruebas que:

1. agregue la fotografía transversal;
2. agregue la carrera CAS real;
3. agregue la comprobación de consultas sin escrituras;
4. no cambie comportamiento productivo salvo que una prueba reproduzca un
   defecto real.

Después de integrar esa dependencia:

1. repetir la batería local;
2. ejecutar Room completa y la app común en Samsung API 36;
3. ejecutar Room completa y el recorrido transversal en API 26;
4. instalar y usar una imagen exacta API 33 sólo con autorización expresa;
5. repetir la auditoría integral sobre el nuevo HEAD.

La alarma exacta real, un reinicio físico del Samsung, la descarga de imágenes,
API 37, push, tag, Release, `main` y producción conservan puertas separadas.

## Estado Git al registrar el handoff

- HEAD funcional auditado: `7570d25421d532a4dd25a03dae3b3cb586a7d8f1`.
- Checkout previo a la documentación MAIN: limpio.
- Ningún archivo de código, Room, DataStore, Gradle o manifiesto fue modificado.
- Ningún dispositivo fue utilizado.
- No hubo push, tag, Release, merge, rebase, reset ni descarte.
