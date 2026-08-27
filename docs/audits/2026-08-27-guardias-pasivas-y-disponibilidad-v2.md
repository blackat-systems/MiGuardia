# Auditoría MAIN — guardias pasivas y disponibilidad V2

- Fecha: 2026-08-27
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `11bbdb41f0a948f5c45dce6adb8b5c95a5b3c931`
- Upstream de entrada: `0364b835d07883708e137a7057f235fad9113b38`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró guardias pasivas y disponibilidad como una fuente local
separada del trabajo. La persona puede elegir No uso disponibilidad o uno de
los tres nombres exactos Guardia pasiva, Disponible para llamado y Retén, con
vigencia histórica desde una fecha concreta.

Desde la única grilla mensual se pueden registrar, consultar, corregir y
eliminar ventanas exactas. Las ventanas contiguas son válidas y las
superpuestas se rechazan. El trabajo activo reemplaza únicamente la unión del
tramo coincidente; ningún minuto pasivo se suma a trabajo, cumplimiento,
faltante o superación.

No se implementaron recurrencias de disponibilidad, situaciones especiales
nuevas, conteo final, Resumen, próximo evento, notificaciones ni otro bloque
posterior.

## Auditoría independiente y correcciones de MAIN

Tres agentes de sólo lectura revisaron dominio, persistencia e interfaz. MAIN
contrastó sus hallazgos con el prompt, ADR 0030, código, pruebas y esquema. Se
corrigieron:

- la observación y el CAS de vacaciones y carpetas médicas en todos los días
  alcanzados por una ventana multidiaria;
- la conversión consciente en `Conflict` cuando cambia la configuración, una
  ventana es movida o eliminada, o se repite una eliminación;
- la validación de línea temporal, sector, fecha dueña y solapamiento en la
  mutación de dominio;
- la conservación de la fotografía original al abrir una corrección, incluso
  después de recrear la actividad;
- la protección contra doble toque y navegación durante escrituras;
- la confirmación antes de descartar borradores;
- la conservación de ventanas y resultados visibles ante errores temporales;
- la explicación y el reintento de fallos de eliminación;
- la actualización del nombre vigente al cruzar medianoche.

Se agregó cobertura específica para concurrencia, doble eliminación, contexto
incorrecto, protección multidiaria, solapamiento de dominio, descarte
consciente y preservación de datos ante error.

## Persistencia

`MiGuardiaV2Database` evoluciona de versión 4 a 5 mediante migración explícita
`4→5`. Conserva las 26 tablas anteriores y agrega únicamente
`availability_windows`; la base queda con 27 tablas.

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

El identity hash de Room 5 es `77adbc875d0f4ee466cdbd0dd74d5c5c`. Los
esquemas 1 a 4 permanecen byte a byte iguales. No existe migración destructiva,
acceso Room en el hilo principal ni conexión con `miguardia.db`.

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

Resultado: `BUILD SUCCESSFUL` en 9 min 15 s, con 238/238 tareas ejecutadas.

- dominio JVM: 265/265;
- base JVM: 12/12;
- aplicación JVM: 156/156;
- total JVM: 433/433, sin fallos, errores ni omisiones;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- `git diff --check`: correcto.

Una confirmación final incremental repitió las mismas ocho tareas y terminó
verde. No se agregaron dependencias, permisos, cambios de Gradle, manifiesto,
SDK, red, telemetría, logs privados ni ajustes visuales del sistema.

## Validación Android de MAIN

### Samsung SM-S938B — API 36

La primera ejecución Room propia de MAIN detectó que un método nuevo de prueba
no devolvía `Unit`; Android rechazó inicializar esa clase. La declaración se
corrigió y se recompiló el APK de prueba. La repetición completa quedó:

- Room, cadena `1→2→3→4→5`, persistencia, rollback y concurrencia: 107/107;
- aplicación completa y regresiones: 190/190;
- total final ejecutado y aprobado: 297/297.

La instrumentación física recorrió configuración, CRUD, migración, cálculo,
ventanas pasadas/actuales/futuras, solapamientos, contigüidad, trabajo
coincidente, recreación, claro/oscuro, retrato/paisaje y zoom interno
100/150/200.

MAIN también inspeccionó visualmente en oscuro/retrato el Calendario con datos
ficticios, el acceso desde Mi forma de trabajar, el estado No uso
disponibilidad y la pantalla que muestra los tres nombres exactos y su fecha de
vigencia. No se consultaron ni modificaron `font_scale`, densidad o tamaño
visual del sistema.

El handoff especializado afirmaba 293/293, pero su evidencia durable contenía
una corrida Room roja y una corrida antigua de la app. MAIN no reutilizó esa
afirmación: recompiló y ejecutó las suites actuales después de sus correcciones.

## Seguridad del dispositivo

MAIN verificó al comenzar que no existía ningún paquete
`com.blackatsystems.miguardia*` instalado. Usó exclusivamente:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`;
- `com.blackatsystems.miguardia.core.database.test`.

Los tres paquetes quedaron ausentes al finalizar. Producción no fue instalada,
abierta, consultada, limpiada, modificada ni desinstalada. Las capturas y XML
temporales de la revisión visual fueron eliminados fuera del repositorio.

El especialista informó que una corrida previa había instalado temporalmente
un host debuggable con el identificador de producción. MAIN no repitió ese
comando: instaló manualmente sólo los APK QA exactos y comprobó ausencia total
antes y después.

## Git y pendiente separado

El candidato llegó directamente al checkout compartido, sin commit ni staged.
MAIN preservó `main`, `origin/main`, `v1.0.0`, worktrees históricos y el upstream.
No hubo merge, rebase, reset, descarte, tag, Release ni acción sobre producción.

API 26 no se repitió con Room V2 versión 5. Queda como evidencia de
compatibilidad separada antes del candidato final y no bloquea este checkpoint
API 36. La próxima dependencia recomendada es Ausencias, cancelaciones y otras
situaciones especiales; su prompt todavía no fue creado ni habilitado.
