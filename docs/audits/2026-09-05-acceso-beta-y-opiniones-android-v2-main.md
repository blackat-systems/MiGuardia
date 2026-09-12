# Auditoría MAIN — Acceso a la beta y opiniones Android V2

- Fecha: 2026-09-05.
- Entrada: `0ef1eb4dfdfb6de43ca425d82d24bf1d8d0b27c7`.
- Rama: `codex/miguardia-2.0`.
- Veredicto: **APROBADA CON CORRECCIONES — INTEGRACIÓN LOCAL; NO PUBLICABLE**.

## Alcance y Puerta 0

MAIN recibió el mismo checkout compartido, con 19 archivos modificados y 24
nuevos, exclusivamente en `app/`, sin staging. Los 43 hashes de entrada
coincidían con el inventario final del especialista. No había trabajo sin dueño.
Se leyeron los hunks y archivos nuevos, incluido el oráculo Web Crypto de
pruebas; no se reconstruyó nada desde worktrees históricos.

HEAD, rama y autor `joaquin <blackat.systems@gmail.com>` coincidían con el
contrato. El upstream era `924fd46df018f5e14637dfe90a983fefdbf27af4`, con tres
commits locales por delante y ninguno por detrás. `main`, `origin/main` y
`v1.0.0^{}` permanecían en `82db6fd8eb2c511205968894dc9857a96b16ed20`.
Los nueve worktrees fueron preservados.

Se aplicó `joa-main-orchestrator` en modo recepción/auditoría/integración. MAIN
fue el único escritor y controlador del dispositivo; dos revisores
independientes trabajaron en paralelo, exclusivamente en lectura.

## Contratos comprobados

- Sólo canje y feedback mediante los dos POST HTTPS del origen fijo.
- Comprobantes ES256/P1363 verificados contra paquete, certificado, versión e
  identidad P-256 de instalación; clave privada en Android Keystore.
- Uso posterior sin conexión; opinión confirmada durable e idempotente. Sólo
  un recibo firmado acredita recepción. La cola no apaga avisos ni revoca acceso.
- Recuperación → Bloqueo → beta/opinión → WorkSetup → Ayuda → contenido.
- Destinos y publicaciones exteriores revalidados; contenido genérico sin
  activación válida. Se conservan las acciones diferidas de avisos y los IDs
  del launcher.
- Beta no entra en Copias: se mantienen las 17 preferencias portables.
- Debug y Release conservan confianza vacía e inmutable. La inyección de QA es
  de instrumentación, no una clave productiva ni una activación publicada.
- Único permiso nuevo del bloque: `ACCESS_NETWORK_STATE`, normal. No cambia
  versión, paquete, componentes exportados, Gradle ni dependencias.

Los contratos web de API y operaciones se mantuvieron de sólo lectura, con
SHA-256 `2B821D750008BC449E491A25E0CFD8C1E900C06BE8DEB831FEE4C5EEFD6DD7B0`
y `9D647DDD6C8DA633A51771BBEE231770973793F6E8F389F72029BD3113571D61`.
No hubo provisión, peticiones productivas de activación, secretos, firma ni deploy.

## Correcciones de MAIN

1. **P2 — reconexión durante el cooldown:** el disparador se perdía. Se conserva
   un único intento diferido hasta cumplir ambos relojes y `Retry-After`; se
   cancela al fondo y un fallo no programa otro intento por sí mismo.
2. **P2 — cancelación durante el commit durable:** quedaba un recibo en disco
   detrás de una puerta que se creía inicializada. Ahora el commit se completa
   sin perder su resultado y la cancelación ganadora exige releer y verificar.
   La aceptación final y la cancelación comparten una exclusión breve; ningún
   guardado suspendido ocurre dentro de ese monitor.
3. **P2 — Atrás desde Widget:** cerraba sin cancelar el canje. Main y Widget usan
   el mismo manejador de salida, con cancelación antes de cerrar.
4. **P2 — reactivación de avisos/Widget:** un arranque fallido quedaba sellado.
   El inicio se serializa y sólo se considera logrado tras éxito; un nuevo
   foreground puede reintentarlo. La pausa de Copias invalida ese estado y la
   recuperación vuelve a iniciar después de `Ready`, propagando los fallos para
   conservar las protecciones del journal.
5. **P2 — punto seguro tras recreación:** el indicador volvía a `true` aun con
   una operación pendiente. Ahora se restaura y permite terminar ese recorrido
   antes de presentar la opinión vencida.

Los primeros dos defectos se reprodujeron mediante dos nuevas pruebas rojas
sobre el candidato original. Después de la corrección pasaron. Se agregaron
ocho regresiones JVM —cuatro de coordinador y cuatro de activación de runtimes—
y dos Compose —Atrás compartido y restauración de punto seguro—. La revisión
independiente final no dejó defectos concretos pendientes en los deltas.

MAIN corrigió ocho archivos de la entrega y agregó
`BetaRuntimeActivationTest.kt`: el candidato ejecutable tiene 44 rutas, todas
en `app/`. La documentación de cierre corresponde exclusivamente a MAIN.

## Evidencia ya ejecutada

- Entrada del especialista: 741 JVM y compilación global verdes; evidencia
  heredada, no sustituye la repetición de MAIN.
- MAIN sobre APK entregado: 67/67 de la matriz y 2/2 fases separadas por
  `am force-stop` de QA; total 69/69. Ningún caso omitido.
- MAIN tras correcciones: beta JVM dirigida 37/37, cero fallos, errores u
  omitidas; QA y AndroidTest recompilados.
- Prevalidación física de las tres pruebas Compose del bloque: 3/3, incluidas
  las dos regresiones nuevas. No se suma como otra suite única al cierre final.
- Pantalla genérica sin confianza revisada directamente en el Samsung: título,
  explicación, Reintentar y Salir visibles; sin contenido laboral.

La regresión del punto seguro usa `StateRestorationTester` y un host pendiente
equivalente. No se presenta como un recorrido real del selector SAF durante el
vencimiento. Temas y zoom son internos de MiGuardia; no se inspeccionaron ni
alteraron escala o densidad Android.

## Validación final

La batería global de MAIN se ejecutó desde cero, después de la última
corrección, con Node 24.20.0 y el website de sólo lectura para interoperabilidad:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test :core:database:testDebugUnitTest :app:testDebugUnitTest `
  :app:lintDebug :app:assembleDebug :app:assembleQa :app:assembleRelease `
  :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
```

Resultado: **BUILD SUCCESSFUL en 15m51s; 351/351 tareas ejecutadas**.
Los XML frescos contienen dominio 379, database 12 y app 358: **749/749 JVM**,
sin fallos, errores ni omitidas. Lint: cero errores y seis avisos de versiones
disponibles, exclusivamente en Gradle no modificado. Debug, QA y Release
unsigned, más ambos APK AndroidTest, compilaron. El registro local ignorado
es `app/build/beta-main-final-validation.log`.

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 17.982.108 | `5D63AA2152FDCDACC608783FA400AD52A7DCD3836EE815BEEBE413110E9B1156` |
| QA | 17.883.397 | `D92848CF0F7B9B266397186957D99A23E8151D2482638FAD1D9791B208A37496` |
| Release unsigned | 12.681.369 | `4A65D481523D74E12604C9226A7D5C1ED06814BF344B3AA428956ADDE658D43F` |
| QA AndroidTest | 2.100.211 | `11C0AAA7599EDD598300799DB8B9E6865EFAB54E2FCBA598F1F6428616EA69D3` |
| Database AndroidTest | 4.184.074 | `B6827A38006EB01ADBC3B6AF33460437BB194B93B415DDAE9A5AAC9413F9E315` |

La firma de QA y QA.test coincidió con la instalación anterior:
`b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
Se reemplazaron ambos paquetes sin borrar datos. La matriz física definitiva
sobre estos APK pasó **69/69**; preparación y reapertura separadas por muerte
real del proceso QA pasaron **1/1 + 1/1**. Total final: **71/71**, cero fallos y
cero omitidas. Las corridas anteriores no se suman a ese total.

| Suite física final | Casos aprobados |
|---|---:|
| Beta: Keystore/persistencia | 2 |
| Beta Compose, incluidas las dos regresiones nuevas | 3 |
| Raíz y publicaciones cerradas sin activación | 4 |
| Copias: recuperación | 10 |
| Copias Compose | 10 |
| Bloqueo Compose | 4 |
| Ayuda Compose | 7 |
| Configuración laboral Compose | 16 |
| Widget Compose | 6 |
| Widget RemoteViews | 7 |
| Preparación y reapertura en procesos distintos | 2 |

El runner dirigido conservado en `app/build/beta-evidence/run_final_device.py`
se ejecutó con `matrix`, `prepare` y `reopen`; entre los dos últimos se aplicó
`adb -s R5CY529W6PL shell am force-stop com.blackatsystems.miguardia.qa`.
Los tres resultados finales quedan en `device-matrix.json`,
`device-prepare.json` y `device-reopen.json` dentro de ese directorio ignorado.
No es una suite Android global ni incluye alarmas exactas reales. Room completo
no se repitió en dispositivo porque no cambia la capa database; sí se ejecutó
su JVM y se compiló su AndroidTest en la batería final.

## Room y superficies protegidas

No hay cambios de `core/domain`, `core/database`, esquemas ni migraciones.
Room permanece V6, con 27 tablas e identidad
`7eb39f6fab5a44e69350e206716554be`. Los seis esquemas se comprobaron contra
los hashes sellados:

| Esquema | SHA-256 |
|---|---|
| 1 | `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E` |
| 2 | `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50` |
| 3 | `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428` |
| 4 | `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B` |
| 5 | `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4` |
| 6 | `BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268` |

## Samsung y límites

Joaquin autorizó este uso del Samsung `SM-S938B`, serial `R5CY529W6PL`, API 36.
Se instalaron mediante reemplazo solamente QA y su APK de pruebas, con la misma
firma debug. La QA y sus datos preexistentes se preservaron. No se consultó ni
instaló producción; no se instaló el APK de pruebas de database.

Al terminar se retiró únicamente `com.blackatsystems.miguardia.qa.test` y se
comprobó su ausencia. `com.blackatsystems.miguardia.qa` permanece instalada,
con sus datos, `stopped=true` y sin proceso activo. No se desinstaló ni limpió QA.
No hubo reinicio del teléfono, alarmas exactas reales, cambios de credenciales,
red, fecha, zona ni ajustes visuales del sistema.

## Puertas posteriores

- Matriz específica de beta en API 26/33/37, TalkBack, autenticación humana y
  el cruce real vencimiento/SAF: no ejecutados en este bloque. La matriz de la
  base funcional previa no se hereda como prueba de estos cambios Android.
- Confianza pública productiva, recursos remotos, APK oficial firmado y QA
  remota: pendientes; este candidato no es publicable.
- Aporte voluntario, botón visible y actualizaciones: fuera del bloque 25.
- No se habilita otra dependencia. Push, publicación, tag, Release y `main`
  siguen requiriendo sus propias autorizaciones.

## Integración y estado Git

El bloque se incorpora como una unidad de 44 archivos de aplicación y seis
documentos de MAIN. Se revisaron el diff, los nuevos, esquemas, contratos web,
ausencia de secretos y el staging explícito; `git diff --check` y la comprobación
del staging quedaron verdes. El checkpoint local se ampara en la autorización
vigente de MAIN después de una integración verde, no en una autorización de push.
No se amplía la matriz física pendiente ni se declara una beta distribuible.
