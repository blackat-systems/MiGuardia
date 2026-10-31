# Stage 4 — candidato de fluidez e integridad

Fecha: 2026-10-02. **Cierre parcial; no aceptación global ni distribución.**
Autorización explícita de Joaquín: «autorizo plenamente». Alcance:
[contrato Stage 4](../prompts/STAGE_4_FLUIDEZ_E_INTEGRIDAD_3_0.md).
Los estados operativos vigentes están en [STATUS](../STATUS.md).

## Entrada y atribución

Se verificó `MiGuardiaContainer/MiGuardia-3.0`, rama `codex/miguardia-3.0`,
HEAD/upstream `932eaeb60c6b6e68975534788e3e6501c967b1ae`, repositorio independiente,
un único worktree y remoto `blackat-systems/MiGuardia`. Entrada sin staging:
83 modificados, una eliminación y 39 entradas no rastreadas (40 archivos al
expandir directorios). Se preservó ese trabajo antes de modificarlo.

`.local-evidence/stage4/entry/manifest.json` conserva 925 hashes; `files/` guarda
123 archivos recibidos, junto con `received.patch`, estado y APK/runner de entrada.
Los 568 insumos del recibo JVM de auditoría `20261002T193052.711888Z` coincidieron.
En el manifiesto Stage 3.2 coincidieron producto/pruebas/catálogo; las tres
diferencias documentales correspondían a la auditoría MAIN recibida. Estos
recibos son antecedentes y no prueban el candidato nuevo.

Baseline QA: `fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`.
Runner anterior: `d088ff91479988a7902c82cf3439aac7ed4b3a0a4b7dd06ded39581b8224c05a`.
El SHA Git solo no incluye Stage 3.2 ni Stage 4.

Se mantuvo un integrador de MiGuardiaApp/MainActivity/STATUS, archivos disjuntos
para presentación e imágenes, una sola ejecución Gradle y ningún operador de
teléfono. MiGuardia 2.0, históricos y website permanecieron sin escrituras.

## Hallazgos F01–F07: cambio y evidencia requerida

| Hallazgo | Antes → candidato | Evidencia nueva y límite |
|---|---|---|
| F01 Ayuda | Referencias a navegación anterior → nombres reales del menú, Ir a hoy, Fotos, carga y Opciones avanzadas; la tarjeta depende de altura disponible. Se retira el ancla al desmontar el control. | Regresiones de texto, recorrido y ancla desmontada en HelpComposeTest; recorrido físico/captura final PENDIENTE. |
| F02 Origen | Abrir un hijo cerraba Mi forma de trabajar → el coordinador padre conserva contexto; sólo el hijo visible atiende Atrás. Estado Compose del padre conserva expansión/scroll y se limpia al cerrar la sesión. Catálogo desde Horas oculta su padre sin destruirlo. | Stage4FlowsActivityTest compara snapshot de Copias al entrar/volver de cuatro hijos, recrea y prueba accesos sin padre. Compilación no acredita ejecución Android; PENDIENTE. |
| F03 Imágenes | Detalle truncaba la selección y carga quedaba sin explicación al límite → política compartida de lote completo, rechazo previo a lectura, ajuste explicado y cancelación sin cambios. | JVM de capacidad 0/1/29/30, exceso, lectura fallida y cancelación. Importador real, espacio y picker OEM requieren recibos separados. |
| F04 Resumen | Tres controles grandes e introducción/exportación antes de cifras → flechas compactas, mes actual, cifras esenciales primero, personalización y exportación accesibles. Se explica el origen en lenguaje cotidiano. | SummaryComposeTest comprueba orden/controles y conserva trazabilidad. No cambian proyecciones/cálculos. Captura comparativa y lectura humana PENDIENTES. |
| F05 Clima | Información extensa antes de la decisión → estado, privacidad esencial y activación consciente primero; proveedor/licencia desplegables, atribución visible y Cerrar fijo. | WeatherComposeTest con callbacks y enlaces interceptados; sin red/permisos nuevos. Captura/lectura humana PENDIENTES. |
| F06 Borrador | Imágenes poco identificables e instrucciones repetidas → miniaturas ordenadas, visor privado, quitar por número; Agregar/Editar/Contraer para nota/puesto. Decodificador privado muestreado común al detalle y carga. | Pruebas de borrador y cancelación; Activity incluye exceso31 seguido de selección válida1. No se recicla un bitmap mientras Compose lo usa. OEM y percepción PENDIENTES. |
| F07 Jerarquía/cierre | Cierres al final del scroll y acciones de igual peso → Cerrar fijo en Clima/Avisos/Widget, compartir informe secundario; radios/toggles con una sola acción accesible. | Pruebas en ambos temas y tres zooms, roles/estado/callback único. Copias, formularios y visores conservan cierres según su significado. Matriz visual completa PENDIENTE. |

La comparación anterior describe código y criterios observables, **no capturas
después ejecutadas**. F01–F07 están implementados como candidato; su cierre físico
y visual sigue pendiente. No se reutilizaron capturas ajenas o cuadros mal
atribuidos de la auditoría original.

## Integridad: causas demostradas y límites

**Reintento de nota tras confirmación perdida.** La regresión
`lostNoteSaveAcknowledgementRetriesSameIdentityAfterRecreation` escribió y simuló
la pérdida de confirmación; recreó con otro proveedor UUID. Antes quedaron dos
notas en lugar de una. El borrador ahora reserva y persiste `pendingId` antes de
guardar/importar, recupera esa identidad al reintentar y conserva `createdAt`.
Cancelar después de una confirmación perdida conserva el adjunto ya referenciado.
No se cambia Room ni el formato de Copias.

**Importador y compensación.** Un error al importar sobre una identidad existente
podía borrar el destino previo desde el `catch`. Ahora se rechaza antes de asumir
propiedad del destino; sólo se retiran temporales/archivos creados por ese intento.
La devolución cancelada desde IO también compensa con contexto no cancelable.
La promoción de imágenes de carga registra su clave antes de cruzar IO y consulta
referencias frescas antes de compensar. Pruebas de filesystem Android preparadas;
ese adverso concreto no se declara ejecutado en JVM.

**Borrado mientras se edita.** La revisión independiente detectó que borrar
una nota en edición omitía sus adjuntos nuevos y que el borrador de un reintento
con `pendingId` podía quedar abierto. Dos adversos JVM fallaron antes; después
pasó la clase completa, 23/23. La limpieza reúne claves SQL y del borrador de esa
identidad, consulta referencias frescas y conserva borradores/archivos ajenos.

**Orden de recuperación.** Fotos procesaba `.tmp`/`.trash` antes de excluir los
nombres de archivos de notas/evidencia. Un archivo `cancel_*.trash` todavía
referenciado podía perderse si Fotos reconciliaba antes que Calendario. La
corrección excluye `note_`/`cancel_` desde el principio; cada dueño atiende sus
archivos recuperables. Causa demostrada por lectura de ambas ramas y su parser;
pruebas Android sintéticas preparadas en ambos órdenes. **No se ejecutó una caída
física ni ese adverso Android**; el límite queda visible.

**R03, SQL.** Las cuatro consultas de Limpiar generaban un parámetro por identidad
sin fragmentar. SQLite nativo del host, usando SQL de los DAO y tablas del esquema
V8, reprodujo `too many SQL variables` al superar límites configurados. La
corrección consulta lotes de 900 y restituye el orden global original de adjuntos,
horarios reales y avisos antes del token CAS. Conserva el conjunto completo, la
validación y las transacciones existentes. El experimento preserva 48 casos, ocho fallos originales esperados y 48/48 consultas fragmentadas con igualdad completa de filas/referencias; `.local-evidence/stage4/r03-host/` contiene script y resultado. El límite del Samsung y la ejecución Room con
rollback/adjuntos/no seleccionados siguen PENDIENTES en CalendarClearInstrumentedTest.

**R04, restauración y archivos.** Dos pruebas JVM con coordinador y gate reales,
repositorios/archivos dobles y barreras deterministas perdieron bytes restaurados:
limpieza inicial con referencias viejas y eliminación seguida de limpieza tardía.
Antes 2/2 fallaron. Ahora inicialización/importación/guardado/eliminación/compensación
de notas usan `LocalDataMutationGate` compartido por la aplicación, exterior a
`NoteFileCommitGate`, y vuelven a leer referencias al limpiar. El adaptador interno
NoteAttachmentFiles permite probar esa coordinación sin Android; producción
sigue usando SchedulePhotoFileStore. No se amplía una transacción SQL.

La inicialización de Exceptions corre en segundo plano al abrir MainActivity y
no bloquea el acceso a Copias: es un camino alcanzable por diseño, no una carrera
humana cronometrada en teléfono. Stage4NoteRestoreConcurrencyTest conserva la
prueba con SQLite y BackupPhotoFiles reales para Android. Su ejecución y el ciclo
completo de Copias/journal permanecen PENDIENTES.

**R01 y R02.** No se ha certificado la ventana real con mes denso ni el rendimiento.
Stage4CalendarMatrixTest prepara 28/29/30/31 días, cuatro/cinco/seis filas, ambos
temas, tres zooms y retrato/paisaje; registra geometría, semántica, restricciones y
capturas del nodo QA. No se alteró la grilla ni se decidió un comportamiento
alternativo para una ventana que no admita los invariantes. El
[protocolo R02](2026-10-02-stage4-rendimiento.md) fija conjuntos, mediciones y
umbrales de investigación antes de optimizar. No hay p50/p95/fps inventados.

## Validación y evidencia adversa

| Unidad | Resultado | Evidencia local |
|---|---|---|
| Confirmación perdida, antes | FALLÓ, 1/1: dos notas | `.local-evidence/stage4/imagenes/notes-ack-before-causal/` |
| Notas/imágenes, después inicial | 74/74 JVM | `.local-evidence/stage4/imagenes/notes-images-after/` |
| R04, antes | FALLÓ, 2/2: bytes confirmados perdidos | `.local-evidence/stage4/imagenes/r04-before/` |
| R04/equivalentes, después | 78/78 JVM; 20 Exceptions y 58 carga | `.local-evidence/stage4/imagenes/r04-after/` |
| Borrado durante edición, antes/después | Antes FALLÓ 2/2; después 23/23 JVM | `.local-evidence/stage4/imagenes/delete-draft-before/` y `delete-draft-after/` |
| SQL host R03 | 48 casos; ocho fallos originales esperados, 48/48 fragmentados | `.local-evidence/stage4/r03-host/result.json`; sin Room/Android |
| Primer build integrado | FALLÓ lint por sangría ambigua en ExceptionsViewModel; corregida | `.local-evidence/optimization/20261002T201623.630408Z/result.json` |
| Compilación QA + ambos runners previa a fix R04 | PASÓ; sólo compilación | `.local-evidence/stage4/pre-integrity-build.log`, artefactos/manifiesto en `pre-integrity/` |
| Primer perfil full | 913/913 JVM y 26/26 interop; build FALLÓ por import de assertNull faltante en test Activity, corregido | `.local-evidence/optimization/20261002T205157.595003Z/result.json`; insumos estables |
| Validación final integrada | PASÓ: 916/916 JVM, 26/26 interop, lint/build y ambos runners; 0 fallos/errores/omitidas de tests | `.local-evidence/optimization/20261002T211459.258318Z/result.json`; 574 insumos Android y 38 website estables, `changed_inputs=[]` |
| QA física Stage 4 | PENDIENTE, no ejecutada | Teléfono conectado pero sin disponibilidad confirmada; no instalado candidato nuevo |

También se conserva un intento focalizado que falló por interpretación de
argumentos PowerShell (`notes-ack-before`); no ejecutó pruebas y no cuenta como
adverso del producto. Los verdes posteriores no sustituyen los fallos anteriores.

## Identidad final y revisión del delta

Perfil `full --fresh-tests` aprobado. Lint conserva 24 advertencias, sin errores;
no se relajaron sus reglas. Compilaciones Debug/QA/Release unsigned y ambos
AndroidTest aprobados; no instalación ni firma oficial. Catálogo válido: 114
clases/24 grupos. Documentos y `git diff --check` aprobados.

| Artefacto compilado | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `61fb082e0eacf4d31f8fb66e63f64eca3b3118cf95e341fc4d719e1c4353924c` |
| `app/build/outputs/apk/qa/app-qa.apk` | `587ab6f6e4c1478dac2ebee37fc5d62b755057ed5202adda600ec718b556ecec` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `f1480cee2d00cf72f1837941351cfe3a17be680401c020653771626e2207f8f7` |
| `app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk` | `1dd8d07dcb88269e02bff08fd651ad01eadf0fbe0f17cc76270a600e0352f26e` |
| `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | `9d4be8ff629df0bef020936c6f1064f1bb680eb9a5b1766e2a9654c026bbcfce` |

El cierre preserva QA y runners técnicos, manifiesto de insumos y delta propio
contra la entrada en `.local-evidence/stage4/final/`. Son 46 archivos propios,
con cambios sobre archivos recibidos distinguidos de archivos nuevos; el patch
acumulado de Git conserva también el trabajo anterior. No hubo staging, commit,
push ni cambios de rama. La revisión del delta incluyó causas, consumidores y
oráculos; una revisión independiente detectó los dos casos adicionales de
archivos que se corrigieron antes de esta validación final. El escaneo de rutas
y patrones no detectó secretos ni APK/claves rastreables; los datos de prueba
nuevos son ficticios. Los artefactos y adversos locales permanecen ignorados por Git.

## Consumidores, privacidad y revisión pendiente

Las fuentes compartidas afectadas son notas/adjuntos y la lectura para Limpiar.
Dominio de horas/Resumen/próximo evento/clima/avisos/recurrencia/informes/Copias
se revalida localmente con la batería integrada; los flujos Room y consumidores
Android quedan individualizados en la [matriz de 33 superficies](2026-10-02-stage4-matriz-recorridos.md).
No se cambiaron reglas laborales, dependencias, Room V8, Copias V5–V8, versión,
applicationId, firma, permisos ni distribución. No se consultaron datos personales
ni ajustes de pantalla; no se abrió app pública, Play Store o website de producción.

Siguiente puerta: teléfono disponible para instalar **sólo QA y runners**,
comparación baseline/candidato con identidad verificada, adversos Room/archivos,
recorridos/capturas afectados y medición. TalkBack, selector OEM, teclado y
aceptación visual se registran aparte. Si una ventana no admite mes completo y
objetivos táctiles, elevar esa restricción a MAIN sin sustituir el diseño.
