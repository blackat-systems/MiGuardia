# Auditoría MAIN — Copias y restauración locales seguras V2

- Fecha: 2026-09-01
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada conservado durante la auditoría:
  `d0b5469e38e5404011d192d4a2200574abbfccd6`
- Resultado: **CERRADO — INTEGRADO Y VERIFICADO POR MAIN**

## Puerta 0

MAIN verificó ruta, rama, HEAD, upstream, autor, remoto, referencias protegidas,
worktrees y estado del checkout antes de integrar. `main`, `origin/main` y
`v1.0.0^{}` permanecieron en
`82db6fd8eb2c511205968894dc9857a96b16ed20`. Los cambios sin commit recibidos
pertenecían al candidato de Copias; no se descartó trabajo ni se reutilizaron
worktrees históricos.

## Alcance aceptado

La entrega implementa una copia lógica completa e independiente del archivo
SQLite físico:

- 27 tablas Room V5;
- 17 preferencias semánticas portables;
- fotografías privadas opcionales con su procedencia;
- formato `.miguardia-backup` versionado;
- cifrado opcional recomendado con AES-256-GCM y
  PBKDF2-HMAC-SHA256;
- creación y apertura mediante Storage Access Framework;
- vista previa obligatoria y sin escrituras;
- combinación consciente con resolución total de conflictos;
- reemplazo total con impacto visible y segunda confirmación textual;
- journal privado y barrera de mutación para dejar un estado viejo o nuevo,
  nunca una mezcla parcial;
- recuperación temprana antes de iniciar Notificaciones, Widget y Clima.

No se agregaron permisos, dependencias, cuentas, nube, sincronización,
telemetría ni escrituras sobre producción. No cambiaron entidades, esquemas o
migraciones Room, Gradle ni el manifiesto.

## Auditoría y correcciones de MAIN

MAIN revisó el candidato y reforzó, entre otros puntos:

- presupuesto de memoria y límites de expansión antes de materializar datos;
- integridad del contenedor sin contraseña sin presentarla como cifrado;
- publicación SAF sin aceptar destinos parciales;
- captura coherente entre Room, preferencias y fotografías;
- staging y limpieza privada de fotografías e Informes;
- recuperación idempotente de las fases del journal;
- suspensión y replay de acciones de Notificaciones y Widget durante una
  restauración;
- validación semántica explícita de columnas `LocalTime`, sin confundir
  `timelineId` con una hora;
- lecturas estrictas de los cuatro DataStore portables para que una falla de
  entrada/salida aborte tanto la copia como el journal, sin guardar valores
  vacíos silenciosos;
- fixtures Room de orden extremo, rollback y restricciones reales;
- pruebas de configuración de Widget y teardown de acciones diferidas sin
  carreras artificiales.

Revisiones independientes posteriores no reportaron defectos P0–P2 abiertos.

## Validación local final

Comando contractual repetido con `--rerun-tasks`, `--no-daemon` y
`--max-workers=1`:

- `:core:domain:test`;
- `:core:database:testDebugUnitTest`;
- `:app:testDebugUnitTest`;
- `:app:lintDebug`;
- `:app:assembleDebug`;
- `:app:assembleQa`;
- `:app:assembleRelease`;
- `:app:assembleQaAndroidTest`;
- `:core:database:assembleDebugAndroidTest`.

Resultado: **BUILD SUCCESSFUL en 18 min 12 s; 351/351 tareas ejecutadas**.

Conteos frescos desde XML:

- dominio: 377/377;
- base: 12/12;
- aplicación: 214/214;
- total JVM: **603/603**;
- fallos, errores y omitidas: 0;
- lint: 0 errores, 0 fatales y 6 avisos heredados de versiones en Gradle no
  modificado.

Se compilaron Debug, QA, Release sin firma, AndroidTest QA y AndroidTest de
base. `git diff --check` quedó limpio.

## Room

Room permanece en versión 5 con 27 tablas e `identityHash`
`77adbc875d0f4ee466cdbd0dd74d5c5c`. No cambiaron entidades, DAO, migraciones
ni esquemas. Hashes SHA-256 preservados:

- V1: `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- V2: `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- V3: `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`;
- V4: `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B`;
- V5: `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4`.

## QA Samsung autorizada

Dispositivo: Samsung `SM-S938B`, Android 16/API 36, serie
`R5CY529W6PL`.

Instrumentación ejecutada:

- Room completa: **121/121**;
- matriz segura de aplicación: **98/98**;
- total único: **219/219**, sin fallos, errores ni omitidas.

La matriz de aplicación incluyó Copias, recuperación, publicación, fotos,
preferencias, Notificaciones y Widget diferidos, navegación y regresiones
vecinas. Se excluyó la suite que limpia deliberadamente la base QA y dispara
rutas de alarmas exactas; esa exclusión impide llamar a esta ejecución “suite
global completa”.

Recorrido manual con datos ficticios:

1. acceso desde el menú;
2. copia cifrada con contraseña ficticia mediante el selector real de Android;
3. archivo sugerido con extensión `.miguardia-backup`;
4. apertura mediante `ACTION_OPEN_DOCUMENT`;
5. autenticación y vista previa antes de escribir;
6. combinación de una copia idéntica, sin sobreescritura silenciosa;
7. verificación final y reconciliación silenciosa;
8. muerte del proceso en segundo plano y arranque frío posterior;
9. persistencia de la configuración ficticia de Enfermería;
10. claro/oscuro, retrato/paisaje y zoom interno 100/150/200.

La copia ficticia fue eliminada de Descargas. Se restauraron orientación,
seguimiento del tema del sistema y zoom interno 100 %. Producción estaba ausente
y nunca se instaló, abrió, consultó, limpió ni desinstaló. Al cerrar quedó sólo
`com.blackatsystems.miguardia.qa`; no quedaron paquetes de prueba instalados.

## Pendientes explícitos

- API 26 y API 33;
- revisión humana con TalkBack;
- falta real de espacio;
- matar el proceso exactamente dentro de cada fase del journal;
- recorrido manual de reemplazo total y de archivos corruptos, aunque sus
  contratos poseen cobertura automatizada.

No se disparó una alarma exacta real ni se reinició el Samsung. Esas acciones
mantienen puertas independientes.

## Git y cierre

Este cierre queda registrado mediante un checkpoint local coherente después de
revisar el diff final y el staging exacto. No hubo push y no existe autorización
vigente de push, tag, Release, `main` ni producción. Bloqueo de acceso local es
el próximo bloque recomendado, todavía sin prompt habilitado ni tarea abierta.
