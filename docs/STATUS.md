# Estado actual de MiGuardia 3.0

- Etapa: 13 · Datos y archivos — CERRADA por Joaquín (8/11): A166–A207, C078, C079 y C095 ✅
- Próxima etapa: **13.5 · Estética y facilidad de uso** (agregada por Joaquín el 8/11) — NO iniciada; prompt en
  docs/prompts/ETAPA_13_5_ESTETICA_Y_FACILIDAD.md. Después: 14 · Sistema y aceptación final
  (docs/prompts/ETAPA_14_SISTEMA_Y_ACEPTACION.md) y 15 · Entrega
- Etapa anterior: 12.2 · Cálculos y consulta — CERRADA por Joaquín (8/11)
- Base: `636e4ae`, subido a `origin/codex/miguardia-3.0` el 8/11 con pedido de Joaquín
- Actualización: 2026-11-08

## Etapa 13 · Datos y archivos (desde el 8/11)

**Cómo se prueba.** La app real (MiGuardiaApp, ViewModels, Room y **Copias**) sobre una base y una carpeta
propias de cada corrida (`stage13-<id>`), con la fixture de 12.2. Copias usa un coordinador construido sobre
esa base, nunca el de la Application (base QA real). Preferencias de la corrida con su propio nombre y
borradas al terminar. Los selectores los contesta la prueba con archivos de la corrida por el FileProvider
real (`Stage13Pickers`); la sesión humana usa los selectores reales. Arnés: `Stage12CalculationStore`
(parámetros `tagPrefix`, `allowedEffects = photos.import`) y `stage13/Stage13Support.kt`. Selectores en
`Stage13DataSelectors`. Correr con `run_methods_diag.sh` y `RUNNER=<hash del ejecutor>`.

**Resultado en el Samsung (8/11):** QA `7dc36052…` (sin cambios) + ejecutor `07063d22…`: **12/12 ✅**;
sesión humana con ejecutor `ac66cf6f…`: **6/6 pasos ✅**; regresión de 12.2 con el arnés cambiado y el mismo
ejecutor `ac66cf6f…`: **15/15 ✅**. Foto de datos QA antes/después: **23 bases idénticas, ningún archivo previo
faltante**; sólo cambió `files/profileInstalled` (marca de Android al instalar); galería sin imágenes de prueba. Evidencia en `.local-evidence/claude-20261108-13/`.

**Lista de la etapa** (✅ probado · ⏳ falta · ❌ falla). Ninguna ❌.

*Notas privadas (A189–A197, C078, C079)*
- ✅ A189 jornada gemela (misma fecha, horario y objetivo, otro UUID) no ve la nota de la otra; borrar la
  suya no toca la otra — Samsung.
- ✅ C078 vacía o con espacios no se guarda; A190 crear; A191 editar con mismo id y fecha de creación;
  A197 cancelar edición y descartar con confirmación («Seguir editando» conserva) — Samsung.
- ✅ A196 eliminar exige confirmación; cancelarla no borra — Samsung.
- ✅ A192–A194, C079 dos imágenes elegidas, abrir, quitar una del borrador con confirmación, guardar: bytes
  idénticos y la quitada no queda guardada; selector cancelado no cambia nada — Samsung (y selector de fotos
  real en la sesión humana).
- ✅ A195 si la nota se borra por fuera mientras se edita, guardar no la recrea («La nota ya no existe.») y
  el borrador queda — Samsung. Doble guardado y acuse perdido — pruebas existentes de PC.
  Observación: si otra pantalla *edita* (no borra) la nota, al guardar gana el último; no hay control de
  versión del texto. Es la conducta actual; no se cambió.

*Fotos del mes (A198–A207, C095)*
- ✅ A198, C095 se abre en el mes visible del Calendario y al cerrar vuelve al mismo mes; A199 cambiar de mes.
- ✅ A200 importar dos imágenes con bytes exactos (y con el selector de fotos real en la sesión humana);
  A202 asociar al objetivo activo; A203 quitar objetivo sin borrar el archivo; A204 reemplazar conserva el id
  y toma los bytes nuevos (el archivo viejo se retira); A205 eliminar con confirmación (cancelar no borra);
  A206 EXIF ROTATE_90 / ROTATE_270 se ve girada al importar y al reemplazar — Samsung.
- ✅ A207 foto cuyo archivo falta: «No se pudo mostrar la foto» sólo en su tarjeta y en el visor, sin mostrar
  otra; al volver el archivo y reabrirla, se lee la misma — Samsung.
- ✅ A201 pinza y desplazamiento en el visor real — sesión humana; Joaquín confirmó a la vista que ampliar y
  arrastrar anduvieron bien (8/11). Gestos en Compose — prueba existente.

*Copias y restauración (A166–A188)*
- ✅ A166 abrir; A167 con y sin fotos (la copia declara INCLUDED u OMITTED); A168 cifrado por defecto; A169
  7 caracteres o confirmación distinta no habilitan, 256 sí; A170 mostrar/ocultar sólo cambia la vista; la
  contraseña no aparece en ningún archivo ni preferencia de la corrida; A171 sin cifrado sólo tras la
  advertencia; A172 copia coherente (0 nuevos, 0 conflictos al reabrirla); A173 cancelar el selector no deja
  archivos ni cambia datos — Samsung (y selector de documentos real: cancelar, guardar en Descargas y abrir).
- ✅ A174 elegir copia (y selección cancelada); A175–A176 contraseña incorrecta, archivo alterado, truncado y
  no admitido: error visible, sin vista previa ni escritura; A177 vista previa (Room V8, 3 imágenes, nuevos,
  conflictos) antes de escribir — Samsung.
- ✅ A178–A181 combinar con tres conflictos (nota, foto y jornada superpuesta): conservar actual, usar copia y
  conservar ambos, cada uno aplicado; bytes de imágenes sin cambios — Samsung.
- ✅ A182 reemplazar todo sólo con la frase exacta (minúsculas o incompleta no habilita); A187 devuelve la Copia
  exacta (base, preferencias y bytes) aunque la imagen actual tenga los mismos metadatos y otros bytes; A186
  vuelve al Calendario con los datos restaurados — Samsung.
- ✅ A183 otra línea temporal: combinar bloqueado, reemplazar disponible; salir no escribe — Samsung.
- ✅ A184 durante la aplicación no hay cancelar, «Volver» está deshabilitado y Atrás no sale; A185 recuperación
  pendiente: error con Reintentar (nunca éxito), un reintento que falla sigue sin éxito, el siguiente termina y
  deja la Copia exacta — Samsung (fallas inyectadas en el coordinador de la corrida).
  Observación: con la recuperación pendiente, Atrás del sistema cierra la Activity (la app pasa a segundo
  plano); no vuelve a los datos. INFERIDO que al reabrir aparece la recuperación: no probado acá.
- ✅ A188 copias genuinas V5, V6 y V7 (mismas fixtures que `Stage11RestoreInstrumentedTest`) se abren por la
  pantalla real, muestran su Room de origen, bloquean combinar (otra línea temporal) y con reemplazo consciente
  quedan exactamente en la forma V8 declarada aparte (columnas, filas, sin tablas inventadas) y con los bytes de
  sus imágenes — Samsung, `Stage13LegacyCopiesDeviceTest` 3/3 con ejecutor `e86d06c7…`. V8 — arriba.
  (La suite Stage11 original exige su propio ejecutor para emulador; no se corrió en el Samsung.)
- Interrupción real del proceso durante la aplicación: cubierta con fallas en cada punto del registro de
  recuperación (pruebas existentes) y con la falla inyectada de arriba; no se mató el proceso.

**Hallazgo corregido en la app (8/11, pedido de Joaquín):** cada vez que la app abría o aplicaba una Copia creaba una
base candidata temporal y la borraba, pero Room dejaba un archivo vacío `cache/miguardia-backup-candidate-<id>.db.lck`
que se acumulaba. Ahora `BackupDatabaseGateway` borra también ese bloqueo, y el barrido que ya hacía al abrir la base
retira los bloqueos huérfanos de versiones anteriores (sin tocar nombres que no son suyos). Pruebas: el barrido en
`BackupDatabaseGatewayInstrumentedTest` (compila; no se corrió en el teléfono, usa otro paquete de prueba), la
limpieza del arnés ahora **falla** si queda una candidata o un bloqueo, y `orphanCandidateLockIsSweptByTheNextStore`.
Samsung con QA nueva `71523f97…` + ejecutor `1c22148b…`: **16/16 ✅** (las 12 de la etapa, el barrido y V5–V7),
ninguna candidata sobrante; foto de datos QA antes/después de instalar y de probar: 23 bases idénticas, sólo cambió
`files/profileInstalled`. Pruebas de 12.2 y sesión humana no repetidas: no usan Copias (12.2) o sus selectores no
cambiaron (humana). Los 41 bloqueos de las corridas anteriores se borraron con autorización de Joaquín (8/11).

Escrito en castellano llano. Los códigos raros están traducidos en el [glosario](GLOSARIO.md).

## En una frase

Estamos en la etapa **12.2 · Cálculos y consulta**. Con una misma fixture, «Tus horas y tu meta»,
Resumen, el XLSX real y el PDF real muestran los mismos minutos, en la PC y en el Samsung, y los
retornos conservan el mes. Guardar y compartir con los selectores reales pasó con Joaquín (8/11). La
segunda tanda (editores de Horas, clases y extra independiente) y la tanda final (desgloses, interruptores,
fotos y conflictos al guardar) también pasaron. **Corrida final: 15/15 en el Samsung**; la etapa cumple sus
condiciones de «lista» y **Joaquín la dio por cerrada el 8/11**.

## Etapa 12.2 · Cálculos y consulta (desde el 8/11)

**Cómo se prueba.** Una sola fixture para todo
(`app/src/testSupport/.../stage12/Stage12CalculationFixture.kt`): septiembre 2026, meta fija mensual de
20 h, jornada diurna 8 h con 1 h extra que cuenta, nocturna 22:00–06:00 de 8 h, extra independiente de
3 h 30 min que no cuenta, disponibilidad de 12 h aparte, carpeta médica con nota y textos canario de
privacidad. Valores esperados escritos a mano: 16 h sin extras, 4 h 30 min de extras, 20 h 30 min total,
17 h que cuentan, faltan 3 h.
- PC: `Stage12CalculationConciliationTest` (Horas = Resumen = proyección del informe = XLSX real; lista
  blanca con opciones privadas apagadas y encendidas; opciones privadas no cambian números; mes abierto
  igual) y `SummaryViewModelTest` (mes anterior/siguiente/actual exactos, cruce de año, recreación).
- Samsung: `Stage12CalculationsDeviceTest` (5 métodos) monta la app real (`MiGuardiaApp`, ViewModels y Room
  reales) sobre una **base propia de cada corrida** (`stage12-2-<id>.db` + carpeta privada), con reloj
  anclado al 30/9/2026; se borra al terminar. No abre ni modifica los datos QA. Arnés:
  `Stage12CalculationStore.kt`. Correr con `run_methods_diag.sh` (ver «Retomar aquí»).

**Resultado en el Samsung (8/11): 5/5 ✅** con QA `7dc36052…` + ejecutor `327fec25…`, repetidas 5/5 con el
ejecutor final `0d394c22…` (carpeta privada movida a `files/reports/artifacts/stage12-2-<id>` para poder
compartir, y limpieza reforzada). Sesión humana ✅ con `7cfaaa24…`.
**Tanda final y corrida completa (8/11):** las 15 pruebas automáticas de la etapa (7 de cálculos, 8 de
editores) sobre QA `7dc36052…` + ejecutor `ed25efe4…`: **15/15 ✅**; foto de datos QA antes/después idéntica
(23 bases, 353 archivos), sin restos. Evidencia en `.local-evidence/claude-20261108-12-2c/`.
Observación (INFERIDO, sin verificar en el código): si se toca «Guardar meta» justo mientras la pantalla
recarga datos cambiados por fuera, el toque no hace nada ni muestra aviso; un segundo toque sí avisa. No
pone en riesgo datos (no escribe). Queda anotada para la etapa 14 (aceptación).
**Segunda tanda (8/11):** `Stage12HoursEditorsDeviceTest` (6 métodos). Corrida final junto con las 5
anteriores sobre QA `7dc36052…` + ejecutor `f300fe81…`: **11/11 ✅**. Foto de datos QA antes/después: 23 bases
idénticas, ningún archivo faltante ni nuevo; sólo cambió `files/profileInstalled` (marca de Android al
instalar). Sin restos `stage12-2`. Evidencia en `.local-evidence/claude-20261108-12-2b/`.
Foto de datos QA antes/después: 23 bases idénticas, ningún archivo faltante ni cambiado. Quedaron 20
archivos vacíos `cache/stage12-2-<id>.db.lck` de corridas anteriores a corregir la limpieza (ya corregida;
borrarlos espera autorización de Joaquín). La sesión humana dejó además su base
`databases/stage12-2-ff23743f-….db` (+ `-shm`, `-wal`): al volver del selector, las pantallas reabrieron la
base durante la limpieza. Corregido (se detienen los ViewModels antes de cerrar la base) y verificado sin
regresión en las 5 automáticas; el caso exacto de salir a un selector no se repitió. El PDF que Joaquín
guardó (`MiGuardia_2026-09_informe_parcial.pdf`, datos ficticios) quedó en la carpeta que eligió.
**Limpieza (8/11, autorizada por Joaquín):** borrados por nombre exacto los 3 archivos de esa base y los 20
`.lck` vacíos. Foto posterior (`.local-evidence/claude-20261108-12-2/after-cleanup/`): 23 bases y 353
archivos, idénticos a la foto previa a la etapa. (Después se instaló el ejecutor `f300fe81…`.) PDF real revisado en la PC: mismos números y sin dirección,
motivo, puesto, notas, nota médica ni identificadores.
Latencia de primera apertura del Resumen (fixture chica): 210–241 ms en 5 corridas. Sólo se anota; la
alerta de la etapa 8 sigue sin causa conocida y no se reabrió.

**Lista de la etapa** (✅ probado · ⏳ falta · ❌ falla). Ninguna ❌.

*Horas, meta y extras (A077–A110)*
- ✅ A086 avance, meta, período y pendiente — mismos minutos en pantalla real (Samsung) y PC.
- ✅ A087 disponibilidad separada — PC y Samsung.
- ✅ A077, A079, A080, A085, A088, A089, A092, A099, A103, A108, A110 — pruebas existentes.
- ✅ A078 crear por sugerencia («Servicio extra»); A081 ayuda de las dos preguntas; A082 renombrar sin
  reinterpretar el extra guardado; A083–A084 archivar y reactivar sin tocar el extra existente — Samsung.
- ✅ A090 semana con primer día elegido; A091 ciclo con longitud y anclaje; A093 desde hoy y fecha
  propia; A094 corte del tramo anterior (el tramo viejo queda intacto); A095 recálculo pasado y primer tramo
  corto exigen confirmar; A096 guardar después de revisar — Samsung.
- ✅ A097 meta por período sin valor (no se inventa) y luego informada; A098 corrección sólo con
  confirmación y misma identidad — Samsung.
- ✅ A100 sin alta para fecha futura; A101 lugar, tipo, sin plantilla, clase, color y puesto; A102 cruce
  de medianoche y 29 h; A104 clase creada desde el borrador sin perderlo; A105 duración y meta en la
  revisión; A106 superposición y carpeta médica exigen confirmar (la carpeta no se borra); A109 borrar sólo
  ese extra (la jornada del día sigue) — Samsung.
- ✅ A096 si la configuración cambia entre revisar y guardar: avisa «La configuración o la fecha cambió desde
  la revisión…» y no escribe — Samsung.
- ✅ A107 si aparece otro trabajo superpuesto entre revisar y guardar: no guarda, conserva el borrador
  («Los datos cambiaron…»), y al revisar de nuevo exige confirmar la superposición nueva — Samsung.

*Resumen (A127–A145)*
- ✅ A128–A130 meses exactos (PC y Samsung); A131–A133 mismos minutos y disponibilidad aparte (Samsung);
  A135 total con sus 4 contribuciones (Samsung); A145 Informes desde el menú y desde el final (Samsung).
- ✅ Calendario → detalle → Resumen → volver conserva el mes del Calendario (Samsung).
- ✅ A127, A136–A144 — pruebas existentes.
- ✅ A134 desgloses con valores exactos: lugar y tipo 20 h 30 min, clases 3 h 30 min y 1 h, planificado 8 h,
  real 9 h, diferencia 1 h, 1 día de carpeta médica — Samsung.

*Informes (A146–A165)*
- ✅ A146 mes del Resumen (septiembre y agosto); A147 PDF real; A148 XLSX real; A149 vista previa con
  recuentos; A157 archivo coherente con el Resumen; A160 regenerar; A165 vuelve al Resumen en el mismo
  mes — Samsung.
- ✅ A150–A153 interruptores tocados en pantalla (nombre, puesto, notas y nota médica con 2ª confirmación):
  el XLSX real incluye exactamente eso y nunca dirección, motivo ni IDs — Samsung (y PC con/sin opción).
- ✅ A153, A156, A159 (permiso al archivo exacto), A161–A164 — pruebas existentes.
- ✅ A154 fotos elegidas de a una, tope de 12 con aviso «Podés incluir como máximo 12 fotos…», PDF con 12;
  A155 aviso de privacidad de fotos visible — Samsung (13 fotos PNG reales sembradas en la corrida).
- ✅ A158 guardar con el selector real de documentos: lo guardado es idéntico byte a byte al PDF
  (14.120 bytes) — sesión humana 8/11 (`Stage12CalculationsHumanTest`).
- ✅ A159/A161 en el sistema real: se abre la hoja de compartir de Android; cerrarla sin elegir deja el
  informe privado intacto y la pantalla lista; vuelve al Resumen — misma sesión humana.

**PC:** `python tools/verify.py --profile local` ya termina bien. El `preflight_failed` no venía de una
ruta vieja: Windows tiene las rutas largas desactivadas y la copia de los reportes XML superaba 260
caracteres; `verify.py` ahora usa la forma de ruta larga. Última corrida: 1339 pruebas, 0 fallas.

## Retomar aquí (sesión del 7–8/11, Claude)

Joaquín autorizó en el chat instalar y probar en el Samsung y aprobó los arreglos de abajo.
**Dónde quedamos (8/11, ~02:15):** B1 cerrado y guardado en commits locales (`f00d769` y el de este
registro). Teléfono limpio: pareja instalada QA `7dc36052…` + ejecutor `31c30439…`; datos QA iguales a la
foto previa. Nada subido a GitHub: la rama va adelantada respecto de `origin` y el push espera pedido de Joaquín.
**Actualización 8/11 (etapa 12.2):** el ejecutor instalado ahora es `ed25efe4…` (pruebas 12.2); la app QA
sigue `7dc36052…`. Correr las pruebas 12.2 con
`RUNNER=ed25efe4a7f9a46fc349e7cd396be2e5d15793844f4acc4ea2eba6be305ffb0e bash .local-evidence/claude-20261107-b1/run_methods_diag.sh <salida> <selector>…`
(selectores en `Stage12CalculationSelectors`). Evidencia 12.2 en `.local-evidence/claude-20261108-12-2/`.
**Actualización 8/11 (etapa 13):** ejecutor instalado `ac66cf6f52472cef9be73490f80a25508b65bb3aa26d2653479e0abad2682057`
(incluye 12.2 y 13). Después se instaló `e86d06c7b2ea0e8e3b5b46cb4607ef5f9b3633b2bbd84a2e228cfaab87344d5e`
(agrega A188); la app QA sigue `7dc36052…`. Foto de datos QA tras A188 idéntica a la previa (salvo
`files/profileInstalled`). **Pareja instalada desde el arreglo de los `.lck`:** QA
`71523f97946bc2087dac6a45eb3d911f2ea4f728d0fd6f7f524d35ebe462f056` + ejecutor
`1c22148b9e2725ce3d079d55cfc895c557b14fd3fd1c1bbb56453c7f9812e869` (ambos verificados sacándolos del teléfono). Para
correr con esta pareja: `.local-evidence/claude-20261108-13/fix-lck/run.sh` con `QA=` y `RUNNER=`.
**Etapa 13 cerrada por Joaquín (8/11).** **Siguiente:** arrancar la etapa 13.5 · Estética y facilidad de uso; después
la etapa 14 · Sistema y aceptación final (avisos,
Widget, bloqueo, Clima, temas, accesibilidad y pasada completa). Pendientes anotados para esa etapa: «Guardar meta»
durante una recarga (12.2), TalkBack sobre pantallas completas (B1), y Atrás con recuperación de Copias pendiente
(13, INFERIDO que al reabrir aparece la recuperación).

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

**Cambios de B1 (ya guardados en el commit `f00d769`):**
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

**Pendientes anotados:** (`verify.py` arreglado el 8/11, ver etapa 12.2.) Para la etapa 14,
TalkBack podría alcanzar el calendario que queda debajo de pantallas completas.

## Etapas que vienen después

| Etapa | Qué cubre |
|---|---|
| 12.2 · Cálculos y consulta | Horas, metas, extras, Resumen, informes PDF/XLSX |
| 13 · Datos y archivos | Notas, fotos, Copias, restaurar, archivos dañados |
| 13.5 · Estética y facilidad de uso | Pulir Vigilia R4 en toda la app; elegir horarios y fechas con selector nativo (sin quitar la escritura) |
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
