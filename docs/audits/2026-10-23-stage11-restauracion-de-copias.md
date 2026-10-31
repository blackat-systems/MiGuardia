# Stage 11 — restauración Android y corrección de procedencia de imágenes

Fecha: 23/10/2026. Auditoría propia del especialista; revisión MAIN independiente
pendiente al entregar. Contrato: [Stage11 y ampliación R1](../prompts/STAGE_11_RESTAURACION_DE_COPIAS_EN_ANDROID_3_0.md).
Estado autorizado exclusivamente en [STATUS](../STATUS.md).

## Resultado observado

La candidata R1 restauró Copias genuinas V5/V6/V7 y V8 no vacías mediante
`LocalBackupCoordinator`, Room, DataStore y archivos privados reales Android:
**35 métodos pasados, cero fallados, omitidos o pendientes** en seis clases.
La suite nueva incluye 14 checkpoints de journal con datos actuales no vacíos.
La auditoría posterior independiente comparó **41 bases exportadas**, valores
SQL tipados, integridad y relaciones contra sus oráculos; detectó un mutante.

No se atribuye este verde a la QA8 original: esa candidata reprodujo un defecto.
Tampoco prueba selector SAF OEM, muerte real de proceso, Samsung, actualización
pública, aceptación humana o distribución.

## Base, aislamiento y entradas protegidas

Repositorio `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`,
rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
Checkout acumulado preservado y staging vacío. Stage10 ya estaba auditado;
no se repitieron su semilla, actualización original ni comparadores históricos.

Entrada posterior al acuse MAIN registrada en `entry-sources.json` y
`entry-git.json`, bajo `.local-evidence/stage11/restore-android-20261023/`.
Antes de arrancar R2 se copió el estado FINAL Stage10: 32 archivos,
3.439.743.848 bytes, verificados contra checkpoint `b5e71814…`; el respaldo se
reverificó íntegramente al cerrar y permanece inmutable.

El runner crea exactamente `android.app.Application`, sin MainActivity ni
runtimes productivos. Su contexto base redirige archivos, caché, noBackup,
rutas y aperturas SQLite —incluidos flags/handler— a una raíz UUID; SharedPreferences
usa prefijo UUID. También aísla el barrido de candidatos del gateway, que no
queda protegido sólo por nombrar la base principal con UUID.

Cada invocación comparó todos los archivos durables recibidos antes/después,
inventarió las altas propias y retiró únicamente sus recursos. Ante fallo de
copia adversa, Case y runner conservan originales mediante registro pendiente;
la regresión real de ese comportamiento pasó. La publicación de documentos se
adaptó al coordinador aislado; no se ejecutó la clase que castea la Application
productiva. FileProvider controlado no equivale al selector de documentos OEM.

## Defecto encontrado y corrección R1

En QA8 original, ReplaceAll podía confirmar Room/preferencias nuevos pero
conservar bytes anteriores de foto de cronograma o adjunto de nota cuando ambas
filas tenían iguales metadatos. `materializeDesired` prefería el archivo actual;
el coordinador verificaba luego el hash del propio materializado incorrecto.

La reproducción mínima SIN fault quedó fallida y preservada antes de editar
producto. Fuente azul y destino rojo tenían iguales UUID, nombre, tamaño y
dimensiones: foto 2.635 B/800×480 y adjunto 106 B/2×2. El destino conservó rojo;
la evidencia de cancelación, cuya fila incluye SHA, sí tomó azul. MAIN confirmó
independientemente el adverso y amplió el mismo contrato con R1.

La corrección afecta sólo `BackupPhotoFiles.kt` y `LocalBackupCoordinator.kt`:

- ReplaceAll elige `INCOMING_ONLY`, exige archivos de la extracción y verifica
  origen, copia materializada y estado final contra SHA de la copia extraída.
- Merge conserva `MERGE_BY_RECORD` y las resoluciones existentes. Una imagen
  actual retenida no se contrasta por error con el mapa incoming completo.
- Los conjuntos completos de `replaceFrom` y journal no recurren al archivo vivo
  si falta el origen. Rollback usa hashes del journal anterior; VERIFIED usa los
  del nuevo. Fotos omitidas conserva la política anterior, comprobada en Android.

Los originales y deltas propios están en `r1-product-original/` y
`r1-candidate/`. Los dos tests JVM del selector pasaron; las regresiones Android
comprueban además ausencia/hash incorrecto del origen, Merge, OMITTED y rollback.

## Candidatas y resultados separados

| Objeto | SHA256 / resultado | Uso |
|---|---|---|
| QA8 original | `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3` | Conservada; primera suite 6/11, segunda 11/13; defecto reproducido |
| QA R1 | `494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a` | Una actualización `-r` en el mismo R2; matriz final 35/35 |
| Runner final, compiled-06 | `3db95ad7fcfc8e4d1a2966d4b53409d3e2b4afc5f2029b18dfdd675436225285` | Sólo paquete `.qa.test`; fuentes exactas preservadas |
| Bundle matriz R1 | `485fb0aafb7b0ecae62bb1c722ea518f71f94fec6a22e70a8bf40df9b630669d` | 15 métodos nuevos, 41 comprobaciones posteriores |

Compiled-01/02/03 nunca se instalaron. Compiled-04/05 conservan sus APK/fuentes y
recibos anteriores; sólo compiled-06 acredita la ejecución final. La firma
técnica común es `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.

Matriz final: Stage11 15; Recovery 10; PhotoFiles 3; PhotoMutationGate 1;
PortablePreferences 4; NoteRestoreConcurrency 2. Los dos últimos de Stage4 son
complementarios de concurrencia por gateway, no sustitutos del restore completo.
V5 restaura coordenadas nulas; V6 conserva coordenadas; V7 tiene adjunto no vacío;
V8 tiene notas/adjuntos, cancelación con evidencia, foto, jornadas y 17 preferencias.
Cada legacy también combina sobre V8 actual no vacío conservando campos nuevos.

## Conservación de Stage10 y anexo técnico

Se conservó la primera instalación `2026-10-23 02:03:15`. La lectura final mantiene
las 29 tablas/40 filas de la entrada Stage10 —dos tablas nuevas vacías en esa
fixture recibida— y todos sus RAW, preferencias, foto y marcador.

El control posterior a instalar R1 se detuvo al observar una diferencia única:
`files/profileInstalled`, 24 bytes. Se preservó el rechazo antes de continuar.
Big-endian `>iiqq`: `[1,327680,1792725418882,0]` pasó a
`[1,327680,1792730344218,0]`; sólo cambió timestamp. Hash anterior `5952d49f…`,
nuevo exacto `42d57c4f8bddc57b8c89be6f64c77f81c2c25d425fe8748eb354a373856dd261`.
Coincide con `lastUpdateTime=2026-10-23 04:39:04` UTC de esta QA. Dumpsys tiene
precisión a segundos; los milisegundos provienen del archivo y están dentro de
ese segundo, no de una lectura PackageInfo adicional. MAIN lo contrastó.

La guarda liga exactamente ambos hashes, estructura, código, campo final,
identidad QA y fecha de instalación; cualquier otro cambio falla. La entrada
original no se reemplazó. Antes/después, diagnóstico, rechazo y aceptación
acotada del anexo permanecen separados. La comparación final exige todos los
datos originales y exactamente el anexo aprobado.

## Validación, adversos y cierre

- Compilación QA/AndroidTest y lintQa correctos: diez advertencias, cero errores.
- JVM dirigido 2/2. Host Stage11 13/13 y delta puntual del pin exacto 1/1.
- Android R1 35/35; protocolo y transporte binario/hash/ZIP verificados, sin
  omitidas. SQL posterior independiente 41/41, mutante semántico detectado.
- Los fallos iniciales de invocación/compilación, preferencias sin resolver,
  parseo CRLF y conteo de `android_metadata` se conservan como defectos del
  ejecutor corregidos. No se confunden con el defecto productivo causal.
- Diario: 282 comandos ADB explícitos a localhost/serial R2, ocho invocaciones
  de instrumentación, tres instalaciones de tests y una actualización QA R1.
  No otra QA, Samsung, limpieza, desinstalación, wipe, semilla o publicación.
- Catálogo: nueva clase/runner/grupo manual Stage11. Dos hashes heredados
  Stage10 se refrescaron y una fuente support Stage10 ausente se registró contra
  sus fuentes finales ya auditadas. Grupos y clases anteriores no cambiaron.
  `qa-groups-at-entry.json` coincide exactamente con el hash de entrada; el
  delta independiente está en `qa-groups-delta.json` y `qa-catalog-own.patch`.
- Ocho bundles UUID propios transportados se retiraron de la caché sólo después
  de comprobar nombre y hash. La evidencia permanece local. AVD apagado, puertos
  5586/5587 libres y procesos propios ausentes. Checkpoint final: 33 archivos;
  el alta adicional es un temporal técnico `tmpAdbCmds` del emulador, conservado.

Se cesaron Android y Gradle. Fuentes productivas R1 revisadas por un revisor
interno y MAIN sobre copias; el cierre integral corresponde a la recepción y
auditoría final MAIN del payload sellado. Sin commit, staging, push, firma de
distribución ni cambios de Room, formato, cifrado, permisos o dependencias.

Journal simula interrupciones y reapertura; no se mató realmente el proceso.
OEM/SAF humano, Samsung, datos personales, pública, rendimiento y distribución
quedan fuera. No queda un defecto conocido abierto en la capacidad comprobada.
