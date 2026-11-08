# Estado actual de MiGuardia 3.0

- Bloque autorizado: STAGE_12_1_CIERRE_B1_3_0
- Estado: CERRADO (8/11) — B1 completo, commits locales sin push
- Próxima etapa: 12.2 · Cálculos y consulta — NO iniciada; prompt de arranque en docs/prompts/ETAPA_12_2_CALCULOS_Y_CONSULTA.md
- Contrato: docs/prompts/STAGE_12_1_CIERRE_B1_3_0.md
- Base: cf8fe1d + cambios locales SIN COMMIT (ver abajo)
- Actualización: 2026-11-08

Escrito en castellano llano. Los códigos raros están traducidos en el [glosario](GLOSARIO.md).

## En una frase

Estamos en la etapa **Cierre de Trabajo y Calendario** (B1). Las 9 pruebas automáticas
**pasan en el Samsung** y la **sesión humana de 6 pasos pasó completa** el 8/11. B1 quedó cerrado
con commit local (sin push). Lo próximo es la etapa 12.2.

## Retomar aquí (sesión del 7–8/11, Claude)

Joaquín autorizó en el chat instalar y probar en el Samsung y aprobó los arreglos de abajo.
**Dónde quedamos (8/11, ~02:15):** B1 cerrado y guardado en commits locales (`f00d769` y el de este
registro). Teléfono limpio: pareja instalada QA `7dc36052…` + ejecutor `31c30439…`; datos QA iguales a la
foto previa. Nada subido a GitHub: la rama va adelantada respecto de `origin` y el push espera pedido de Joaquín.
**Siguiente:** arrancar la etapa 12.2 con el prompt de `docs/prompts/ETAPA_12_2_CALCULOS_Y_CONSULTA.md`.

**Entorno de esta PC** (la app de Claude redirige en silencio lo que se escribe en
`AppData\Local`, por eso el SDK quedó fuera de ahí):
- SDK Android: `C:\Users\User\Android\Sdk` (platform-tools 37.0.1, platforms;android-37.0,
  build-tools 36.0.0). `local.properties` apunta ahí. El `adb.exe` de
  `~/.cache/miguardia-stage12-runtime` es un bloqueo viejo que rechaza todo: no usarlo.
- Java: `~/.cache/miguardia-toolchains/jbrsdk-25.0.2-b329.117/...` (exportar JAVA_HOME).
- En Git Bash usar `MSYS_NO_PATHCONV=1` para rutas `/sdcard`.

**Pareja instalada en el Samsung** (verificada sacando el APK del teléfono):
QA `7dc36052…d249`, ejecutor `31c30439…c19b` (desde el 8/11; antes `75563034…3d90`). Se compila con
`:app:assembleQa` y luego `:app:assembleQaAndroidTest -Pmiguardia.stage12NormalRunner=true
-Pmiguardia.stage12QaSha256=<hash QA>`; instalar con `adb install --user 0 -r` (conserva datos).

**Evidencia y herramientas** en `.local-evidence/claude-20261107-b1/` (fuera de Git):
`run_methods.sh` (pareja vieja) y `run_methods_diag.sh` (pareja actual; corre métodos), `snapshot_digest.py` (foto lógica de
datos QA), `ui.py` (toques por adb), fotos antes/después y recibos de cada corrida.

**Resultados en el Samsung (versión final):** ✅ Disponibilidad historial, revisiones y
conflictos · ✅ Detalle→Clima→volver · ✅ Activar Clima desde la tarjeta · ✅ Cancelación con
selector de imagen cancelado · ✅ Privacidad de nota médica · ✅ Repeticiones con políticas
(A051) · ✅ Cargar y editar jornada de día y nocturna (C089). PC: 1333 pruebas, 0 fallas.
Datos QA: las 23 bases idénticas tras instalar y probar; sólo se agregaron recibos y el
horario nocturno 22:00–06:00 que se cargó a propósito como dato de partida.

**Cambios sin commit (revisar diff y proponer commit a Joaquín):**
- App: «Otras opciones del día» sigue abierto al volver de una pantalla encima (p. ej. cancelar
  una eliminación) — `MiGuardiaApp.kt`, `CalendarDayDetail.kt`. Decidido por Joaquín.
- App: al editar una jornada, la hora de modificación se sella al **guardar**, no al armar la
  revisión — `V2ShiftEditViewModel.kt`, `V2ShiftPlanning.kt` (+ prueba de unidad).
- Pruebas: control de texto que excusa sólo el defecto conocido de Compose al reconstruir el
  layout (`Stage12SemanticTextOverflow.kt`); Disponibilidad busca mensaje/«Reintentar» dentro de
  su propia pantalla (el calendario debajo tiene copias); registro de última acción y del texto
  fallido; `files/profileInstalled` (marcador de Android) no cuenta como dato recibido.

## Sesión humana del 8/11 (madrugada) — ✅ aprobada

La prueba humana nunca había corrido en Android. Hubo que corregir la prueba (no la app), sólo en
`app/src/androidTest`:
- `Stage12B1HumanSession.kt`: quitar el observador desde el hilo principal; admitir el lanzamiento de
  Maps que llega directo a la Activity (la hoja del día es otra ventana y usa el contexto de la Activity).
- `Stage12CalendarExceptionsTest.kt`: etiqueta real de la tarjeta (`shift-detail-card-<id>`); abrir el día
  sin «Otras opciones» para Maps y Clima; diagnósticos temporales en el recibo (`debug_primary_failure`,
  `debug_tree`, `debug_weather_wait`) — decidir si se quitan al cerrar.
- `Stage12HumanImageLease.kt`: aceptar el enlace del selector de fotos (`content://media/picker…/<id>`).

**Corrida aprobada:** run `5f8d559c…`, QA `7dc36052…` (sin cambios), ejecutor `31c30439…`
(verificado sacándolo del teléfono), `stage12NormalStatus=COMPLETE`, 1/1, limpieza completa.
Pasos: 1 Maps y vuelta al mismo día ✅ · 2 dirección guardada para Clima ✅ · 3 ciudad aproximada: el
teléfono no dio ubicación → aviso visible, sin escritura (resultado admitido `LOCATION_UNAVAILABLE`) ✅ ·
4 imagen propia elegida en el selector de fotos, bytes exactos ✅ · 5 cancelar reemplazo conserva borrador ✅ ·
6 Clima habilitado, pronóstico al abrir y al reabrir, sólo cambiaron archivos de Clima ✅.
Evidencia: `.local-evidence/claude-20261107-b1/human-session-r9/` (recibo y salida).
En la corrida anterior el paso 6 se trabó una vez esperando la segunda carga de Clima (30 s); no se
repitió y no se cambió código por eso (INFERIDO: demora de red).

**Foto final de datos** (`final-after-human/`): las 23 bases previas idénticas, ningún archivo previo
faltante; sólo cambió `files/profileInstalled` (marcador de Android ya excusado). Lo nuevo es propio de
pruebas: restos de 10 corridas fallidas (una base + archivos cada una) y 2 recibos de la corrida aprobada.
Imágenes de prueba sobrantes en la galería: borradas (con autorización de Joaquín); no queda ninguna.
Restos de las 10 corridas fallidas: **borrados** por Joaquín el 8/11 (10 bases con `-wal`/`-shm` y 10
carpetas privadas; los recibos se conservan). Foto posterior (`final-after-cleanup/`): 23 bases idénticas
a `final-after-tests/`, ningún archivo previo faltante ni extra salvo 19 recibos y `files/profileInstalled`.
Ubicación aproximada quedó concedida a la app QA.

## Qué pasó hasta el 5/11

- **Recuperar el calendario cuando Android cierra la app** (C096): **aprobado**.
  Costó 14 intentos; el que funcionó usa un toque tuyo real para elegir la foto,
  porque la elección automática fallaba sin causa clara. Probó que, al volver,
  el mes, la selección, el detalle y el borrador siguen ahí y no se guarda nada solo.
- **Error al comparar datos** (la copia temporal `derived_copy` hacía creer que los
  datos habían cambiado): arreglado y probado en la PC.
- **Disponibilidad**: arreglo de acentos (UTF-8) hecho y compilado; falta probarlo en el teléfono.
- **Calendario**: cuatro pruebas en el teléfono pasaron; falta la comprobación final de que no se perdieron datos.
- Hay una **versión de prueba armada** con todos estos arreglos juntos. Compila,
  pero **no se instaló** en el Samsung.
- Se hizo el commit de checkpoint y se subió a GitHub (`codex/miguardia-3.0`).

## Qué falta para cerrar esta etapa

1. ✅ (8/11) **Sesión humana** (Joaquín con el teléfono; 3 min por paso): método
   `Stage12CalendarExceptionsTest#humanMapsLocationAndExternalEvidenceStayScoped` con
   `run_methods.sh`. Pasos: (1) tocar la dirección «Plaza del Congreso…» → Maps → Atrás;
   (2) «Objetivo QA Dirección» → «Usar esta dirección para el clima»; (3) «Objetivo QA Ciudad» →
   «Usar mi ciudad actual para el clima», permiso **Aproximada** o «No permitir»;
   (4) «Elegir evidencia opcional» → imagen celeste `MG_QA_C074_…`; (5) «Reemplazar evidencia
   elegida» → cerrar el selector sin elegir; (6) «Entiendo y habilitar Clima».
2. ✅ (8/11) Foto final de datos QA y comparación con `final-after-tests/digest.json`.
3. ✅ (8/11) Diff revisado, commit local hecho con autorización de Joaquín, B1 cerrado.

**Pendientes anotados:** `tools/verify.py` termina con `preflight_failed` (WinError 3, una ruta
inexistente, probablemente de antes de mover la carpeta) aunque Gradle pasa; para la etapa 14,
TalkBack podría alcanzar el calendario que queda debajo de pantallas completas.

## Etapas que vienen después

| Etapa | Qué cubre |
|---|---|
| 12.2 · Cálculos y consulta | Horas, metas, extras, Resumen, informes PDF/XLSX |
| 13 · Datos y archivos | Notas, fotos, Copias, restaurar, archivos dañados |
| 14 · Sistema y aceptación final | Avisos, Widget, bloqueo, Clima, temas, accesibilidad, pasada completa final |
| 15 · Preparar la entrega | Firma, prueba de actualización desde 2.0.2 y publicación |

Detalle en el [plan maestro](PLAN_MAESTRO_CIERRE_3_0.md).

## Cambio de forma de trabajo (7/11)

Joaquín pasó de Codex a Claude y aprobó el [Proceso simple](PROCESO_SIMPLE.md):
una tarea por vez con nombre claro, Claude programa y prueba en la misma sesión,
sin recibos entre chats. También decidió hacer **una** pasada completa final
(no dos) más una prueba corta tras cada arreglo. Las pruebas en el teléfono
siguen pausadas hasta que Joaquín las retome.

## Dónde está la evidencia vieja

La carpeta `.local-evidence/` (registros de las pruebas, no se sube a Git) no se
había copiado en la migración. El 7/11 se copió desde el respaldo de Codex del
disco D: (91.718 archivos, 9,1 GB). La comparación archivo por archivo dio
idénticos y los enlaces vuelven a abrir. El respaldo de D: quedó sin tocar.

## Límites que siguen valiendo

- Nada en el Samsung (instalar, ADB, pruebas) sin que Joaquín lo autorice en el momento.
- Nada de commits, push, firma ni publicación sin pedido explícito.
- Registro detallado anterior: [cierre B1](audits/2026-11-03-cierre-b1-vigente.md) ·
  [checkpoint GitHub](audits/2026-11-05-checkpoint-github.md) · [Evidencia](EVIDENCIA.md).
