# Stage 11 R1 — auditoría independiente MAIN y cierre

Fecha: 23/10/2026. **Tarea cerrada y auditada en el alcance Android sintético**.
No implica aceptación de Joaquín, Samsung, actualización pública o distribución.

## Entrega y autoridad

Chat `01a166ec-dd5d-71b3-9401-a4169ffbb0a3`, turno
`01a166ec-e14b-7013-80cb-7dd515a2af3b`, completado y sin más escrituras.
Clave `stage11-restauracion-android-20261023-r1`, fase `R1_RESTAURACION_ANDROID`.
MAIN acusó RECIBIDO fuera del payload antes de auditar.

Contrato: [Stage11 y R1](../prompts/STAGE_11_RESTAURACION_DE_COPIAS_EN_ANDROID_3_0.md).
Joaquín autorizó continuar con Stage10 y los pendientes mientras descansaba;
Samsung estaba excluido. MAIN amplió expresamente el contrato sólo para los dos
archivos de Copias y la candidata QA necesarios al demostrar un defecto, sin
cambiar formato, Room, cifrado, permisos, dependencias ni criterios de Merge.

Payload inmutable `.local-evidence/stage11/restore-android-20261023/`:

- Sello `9fea02dcfaff5b1dfd805bfe16d8ecdd7881933e93dd3caf540b12935827d859`.
- Manifiesto `07c2bdf1c6d3e20faace411f37440a74a1b3f1f769b9a411d0c08a48b0d33c3e`.
- **1.331 cuerpos / 4.027.778.719 bytes**, rehasheados por MAIN sin diferencias;
  inventario, tamaños y estabilidad durante lectura comprobados.

Recibos y scripts independientes: `.local-evidence/stage11/main/`.
El [handoff del especialista](../HANDOFF_STAGE_11_A_MAIN_3_0.md) y su
[auditoría propia](2026-10-23-stage11-restauracion-de-copias.md) son entradas,
no sustitutos de las comprobaciones siguientes.

## Causa y corrección comprobadas

ReplaceAll podía confirmar datos nuevos y conservar imágenes anteriores si las
filas tenían iguales UUID, tamaño y dimensiones, pero distintos bytes.
`BackupPhotoFiles.materializeDesired` priorizaba current al recibir snapshots;
el coordinador verificaba después hashes del propio materializado equivocado.

MAIN contrastó el primer adverso y la reproducción **sin inyector de fallos**
en QA8 original. En el ZIP `1beb12fd…2210bf3`, cronograma de 2.635 B/800×480
y nota de 106 B/2×2 seguían con bytes rojos del destino frente a los azules
de la copia. La evidencia de cancelación, con SHA en su fila, sí se restauraba.
El fallo de journal revelaba el problema; no causaba la selección equivocada.

R1 cambió exclusivamente:

- `BackupPhotoFiles.kt`: procedencia explícita; conjuntos completos exigen el
  origen entrante y SHA esperado antes/después de copiar, sin fallback silencioso.
- `LocalBackupCoordinator.kt`: ReplaceAll verifica contra los hashes del payload
  extraído; rollback contra el journal OLD. Merge mantiene selección por registro.

MAIN y un revisor independiente leyeron los deltas congelados y callers
pertinentes. OMITTED conserva la política previa: Merge retiene imágenes
actuales; ReplaceAll aplica el conjunto sin imágenes de esa copia. No se
invirtieron prioridades globales ni se cambiaron resoluciones o formatos.
La regresión mínima antes fallida pasó en la nueva QA, junto con fuente ausente,
SHA incorrecto, Merge, OMITTED y recuperación de journal.

## Comprobación independiente de ejecución y valores

QA original: `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
QA R1: `494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a`.
Ejecutor real R1: `3db95ad7fcfc8e4d1a2966d4b53409d3e2b4afc5f2029b18dfdd675436225285`.
Firma técnica: `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.

MAIN revalidó los **35 métodos en seis clases**, sin fallos, omitidos o pendientes:
15 propios y 20 complementarios. Vinculó cada resultado a su salida ADB por SHA,
reprocesó protocolo, comprobó ZIP/CRC/hash/identidad UUID y canarios exactos.
No heredó verdes de QA original a R1 ni tomó un exit-code cero como éxito.

El revisor MAIN contrastó **21 snapshots SQL y preferencias**, incluidos los
**14/14 casos de journal**: once estados anteriores y tres nuevos, coherentes
con las fases. Verificó 29 tablas, valores tipados, integridad, relaciones y 17
preferencias por frame. Incluyó mínimo causal, V7 vacío/sobre V8 actual y OMITTED.
Abrió sólo copias en RAM, normalizando dos bytes de modo WAL del encabezado para
el lector independiente; ningún archivo de evidencia fue alterado.

Límite explícito: esos frames exitosos no contienen PNG finales. El revisor no
rehashéó imágenes ausentes; revisó que el test hashea archivos reales y compara
contra originales de fixture separados, dejando las huellas resultantes.
Los PNG adversos preservados sí fueron inspeccionados y rehasheados.
El especialista aportó además su comparador posterior de 41 oráculos y un
mutante detectado; MAIN no presenta ese total como otra ejecución propia.

Pruebas locales aportadas: JVM dirigido 2/2 y host 13/13 más pin de perfil 1/1.
MAIN repitió **ocho dobles focalizados**: cuatro de guardas/transportes y cuatro
del delta final de actualización, CRLF y pin exacto dentro del mismo segundo.
Todos verdes, sin ADB o subprocess reales; no repitió JVM/Android/Gradle.
El intento preparatorio MAIN sin ruta del módulo qa ejecutó cero tests y quedó
separado de la corrida efectiva. LintQa: MAIN leyó SARIF, cero errores/diez avisos.
Catálogo validado nuevamente por MAIN: 124 clases, 31 grupos.

## Aislamiento, conservación y adversos

Application exactamente neutra; directorios, SQLite y preferencias UUID aislados.
MAIN revisó el contexto y comprobó canarios antes/después en cada bundle final.
Se corrigieron antes del cierre los defectos host de instalación sin aprobación
previa y verde sin bundle. Ahora exige candidato/paquete/certificado antes de
mutar y evidencia binaria verificada antes de verde. Fallar al copiar un adverso
inhibe limpieza de Case y runner; inspección y regresión Android conformes.

282 pares ADB íntegros y dirigidos expresamente a localhost/`emulator-5586`:
tres instalaciones de tests, **una sola actualización QA R1**, ocho invocaciones
de instrumentación incluyendo adversos. Sin reset, wipe, desinstalación o semilla.
Se retiraron ocho ZIP UUID propios de caché por nombre/hash; copias locales
permanecen en el payload. No hubo Samsung, app pública o datos personales.

MAIN comparó el TAR inicial y final: 13 archivos, sin altas/bajas; todos los
bytes idénticos salvo `files/profileInstalled`, anexo técnico de 24 B. También
contrastó SQL y archivos contra la salida Stage10: **29 tablas/40 filas**, RAW,
preferencias, foto y marcador conservados. La primera instalación continuó en
`2026-10-23 02:03:15`, según dumpsys del dispositivo, con precisión a segundos.

La guarda se detuvo ante el cambio de perfil y se conservó el rechazo. MAIN
explicó su transición exacta `[1,327680,1792725418882,0]` a
`[1,327680,1792730344218,0]`, big-endian, ligada a esta actualización QA R1.
Nuevo hash `42d57c4f8bddc57b8c89be6f64c77f81c2c25d425fe8748eb354a373856dd261`.
PackageManager muestra 04:39:04; los milisegundos proceden de la caché y caen
dentro de ese segundo. No se fingió lectura PackageInfo de precisión adicional.
No se reemplazó la entrada ni se excluyó el archivo a ciegas: pin exacto y todo
otro dato estricto. La validación se reanudó sin otra instalación o rollback.

## Delta y recursos

MAIN contrastó 1.023 rutas de entrada y 1.030 de salida: **11 rutas propias**,
siete nuevas y cuatro modificadas; ningún faltante. Separó dos cambios de MAIN
(STATUS/contrato R1), comprobó las once fuentes finales congeladas y los siete
archivos Stage10 intactos. El opt-in Gradle sólo agrega el runner Stage11,
excluye simultaneidad con Stage10 y preserva defaults, plugins y dependencias.

El catálogo conserva grupos/clases anteriores. Los tres metadatos Stage10
heredados —dos hashes desactualizados y un support omitido— coinciden ahora
con sus fuentes finales ya auditadas; no son implementación Stage11.

El respaldo previo de 32 archivos está ligado al checkpoint FINAL Stage10 y
rehashéado como parte del payload. Un revisor independiente verificó el AVD final
detenido: **33 archivos/3.509.277.725 bytes**, hashes/tamaños/mtime estables y sin
reparse points. Única alta de inventario: `tmpAdbCmds/adbcommand828f67ed-1b11-454b-93b8-16e60ebce5fe`, conservada.
Checkpoint final `2afbfe95044c1a3d3e393a454d739a6a9eadcf726823b137f99ca8d2eefab2fb`.
MAIN comprobó procesos de emulador ausentes y puertos 5586/5587 sin listeners.
Chat especialista idle/completed, HEAD original y staging vacío; sin commit/push.

## Puertas restantes y próximo paso

La capacidad queda auditada, sin defecto conocido abierto en lo comprobado.
Journal simula interrupción/reapertura; FileProvider es controlado. No prueba
muerte real de proceso, selector OEM/SAF humano, Samsung ni aceptación global.
Stage10 original de una única actualización sigue incompleto; esta corrección
y restore no lo convierten retroactivamente en éxito ni prueban upgrade público.

No se crea otro stage por numeración. Quedan revisión humana de Vigilia sobre
candidata actual y QA Samsung/OEM al estar disponible; decidir el tratamiento
de la alerta inconclusa Resumen/R4 antes de otra investigación; y contrato de
versión/actualización pública/firma/distribución con sus autorizaciones específicas.
Se revisó también la clase de migración UUID preparada en Stage9: sigue sólo
compilada. No se transforma esa ausencia de una corrida particular en otra etapa
automática: Stage10 ya observó Room6 y Room8 con conservación en su recuperación,
y Stage11 comprobó el restore lógico Android. Son pruebas distintas con límites;
no se atribuyó a esa clase una ejecución inexistente ni se cerró el upgrade público.
Sin un bloque local independiente útil ya habilitado, MAIN registra estas puertas
y retira la supervisión nocturna; no deja sondeo ni implementadores ociosos.
