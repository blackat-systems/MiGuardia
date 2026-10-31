# Stage 11 — Restauración de Copias en Android, sin perder datos

Fecha: 23/10/2026. Estado y dueño actuales: [STATUS](../STATUS.md).
Una tarea completa: comprobar exportación/restauración real de Copias sintéticas
en Android, incluidos errores y recuperación. No es otra prueba de actualización.

## Ampliación acotada MAIN — R1, 23/10

El primer ensayo Android detectó un defecto de la capacidad que este stage debe
comprobar: ReplaceAll puede confirmar Room/preferencias nuevos conservando la
imagen anterior de cronograma o nota si sus filas tienen iguales metadatos pero
distintos bytes. MAIN y su revisor contrastaron código y ZIP adverso
`320aa6cbcc418efe828f339a002fa12052549159824a8c0c20e6f76bd7d8e4df`.
La selección prioriza el archivo actual; después verifica hashes del propio
materializado. No es un deseo nuevo de producto, formato o criterio de Merge.

Bajo la instrucción directa de terminar los pendientes, MAIN habilita esta
corrección mínima dentro de la misma restauración. Esta sección prevalece sobre
las prohibiciones test-only/QA congelada siguientes **sólo en estos puntos**:

1. Antes de editar producto, ejecutar y preservar la reproducción ReplaceAll
   sin fault sobre QA8 original: mismos metadatos y bytes distintos, comprobación
   de SHA de origen y destino. Si no reproduce, devolver la discrepancia a MAIN,
   no aplicar por hipótesis. Registrar originales/hashes/delta antes de modificar.
2. Permitir únicamente `BackupPhotoFiles.kt` y `LocalBackupCoordinator.kt` en
   `app/src/main/java/com/blackatsystems/miguardia/backup/`, más regresiones.
   Propagar procedencia/modo explícito para ReplaceAll y contrastar los bytes
   elegidos contra hashes de la copia extraída, no contra el resultado equivocado.
   No invertir globalmente la prioridad ni cambiar Merge conservador,
   KEEP_CURRENT/USE_BACKUP/KEEP_BOTH, formatos, Room, cifrado o política de fotos
   excluidas. Si eso exige una decisión nueva, detener ese cambio y avisar a MAIN.
3. Con regresión causal anterior fallida y corrección comprobada localmente,
   compilar nueva QA y ejecutor con sus fuentes/hashes/firmas técnicas separados.
   Está permitido actualizar **sólo QA del mismo R2** con `install -r`, sin borrar
   datos, previa guarda de artefacto aprobado/paquete/certificado y captura
   semántica del estado Stage10 recibido. Conservar primera instalación y comparar
   ese estado después; ante diferencia no explicada, parar sin reset/rollback
   automático ni ocultarla. Samsung y paquete público siguen excluidos.
4. Ejecutar la regresión sin fault y errores/journal/fotos afectadas sobre la
   nueva QA; verificar ReplaceAll trae bytes de copia, Merge conserva su contrato,
   fotos excluidas no cambian de política y fallos dejan agregado completo.
   No atribuir resultados de QA antigua a la nueva. Repetir sólo casos afectados
   y comprobaciones necesarias; no campaña global por rutina.

También corregir guardas host antes de nuevas mutaciones: artefacto aprobado
ANTES de instalar; verde exige bundle único/hash/transporte válido. Si falla
preservación de un adverso, inhibir limpieza de Case y runner y conservar origen.
Adversos anteriores y checkpoints compilados quedan inmutables.
MAIN conserva contrato/STATUS y revisará el resultado; el especialista sigue
como único escritor de implementación y operador Gradle/AVD.

## Permisos previsibles al inicio

Joaquín indicó directamente a MAIN: «No te pauses en autorizaciones, solo trabaja
hasta que me despierte» y «Stage 10 y los que falten». Ya autorizó emulador,
aceleración y adaptación de pruebas. MAIN encuadra este siguiente pendiente real
en ese alcance; no inventa una aceptación de producto ni firma por él.

| Estado | Acción, destino y consecuencia | Condición |
|---|---|---|
| CONCEDIDO | Preparar/ejecutar pruebas Android de Copias, generar archivos ficticios, compilar e instalar ejecutor técnico en el AVD R2 propio | Sólo recursos locales existentes y datos inequívocamente sintéticos |
| CONCEDIDO | Merge, reemplazo y fallos deliberados sobre bases, preferencias y archivos privados aislados con UUID de esta prueba | Inventario explícito, canarios y comprobación de aislamiento antes de mutar |
| CONCEDIDO | Respaldar y verificar el estado FINAL de Stage10 antes de arrancar el mismo AVD; apagarlo al terminar | No sobrescribir respaldo anterior ni evidencia; no ejecutar otra copia como AVD |
| EXCLUIDO | Samsung, app pública, datos personales, Copias reales, claves de distribución, publicación | Samsung NO disponible; la autonomía no levanta esta exclusión |
| PENDIENTE | Ninguna autorización rutinaria conocida | Una decisión material fuera del contrato se devuelve a MAIN; no se supone concedida |

## TASK

Demostrar con ejecución Android y lectura posterior que las Copias compatibles
con V5/V6/V7/V8 restauran el agregado correcto en V8, y que cancelar, rechazar
entradas inválidas o fallar durante la operación no deja datos parcialmente
reemplazados. Cubrir datos actuales no vacíos de notas con imágenes y situaciones.
Usar coordinador, formatos, archivos privados y persistencia reales; no sustituir
la restauración por un comparador host o por insertar directamente el resultado.

## CONTEXT

Stage9 aportó fixtures/mecánica local; Stage10 comprobó conservación de la fixture
V6 en Room8 y recuperó el acceso al mismo AVD. Las dos tablas nuevas estaban
vacías: no prueban restauración de adjuntos/situaciones. El ensayo original R2
de una sola actualización sigue incompleto; no repetirlo ni rebautizarlo.

La app es local-first. Copias conserva 17 preferencias portables y formatos
antiguos; cifrado y claves siguen sus contratos vigentes. La preferencia antigua
de zoom se conserva donde lo requiera compatibilidad, pero no habilita controles
ni reemplaza la letra Android (ADR0043). Una prueba verde no es aceptación humana,
SAF OEM, Samsung, actualización pública o distribución.

## INPUTS

Repositorio operativo:
`C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`.
Rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`. Checkout sucio protegido, staging vacío;
la eliminación heredada de `VerticalScrollbarMetricsTest.kt` no es de este stage.

Lectura en orden: AGENTS del contenedor y del repo, STATUS, este contrato,
LECTURAS_POR_TAREA, WORKFLOW y VALIDACION en sus secciones pertinentes. Luego:

- [Revisión Stage10](../audits/2026-10-23-stage10-revision-main-recuperacion-r2.md)
  y entrega inmutable `.local-evidence/stage10/recovery-r2-20261023/HANDOFF.md`.
- ADR0035 de Copias, ADR0038 sólo en lo pertinente a archivos/restauración y
  ADR0043 de letra Android; planificación 2.0 sección16 sólo como contrato que
  no haya sido sustituido. No abrir archivos históricos por rutina.
- `LocalBackupCoordinator.kt`, gateways/archivos/journal y sus pruebas vigentes:
  `BackupRecoveryInstrumentedTest.kt`, `BackupPhotoFilesInstrumentedTest.kt`,
  `BackupPhotoMutationGateInstrumentedTest.kt`, `BackupDocumentPublicationInstrumentedTest.kt`,
  `PortablePreferencesGatewayInstrumentedTest.kt`, `Stage4NoteRestoreConcurrencyTest.kt`
  y `PublicUpgradeBackupFixtureTest.kt`. Ampliar lectura según dependencias reales.
- Encoders/fixtures test-only existentes y lectores por versión. V6 se genera
  con encoder legacy, no forzando al escritor productivo V8 a aceptar V6.
  Fixtures V5/V7 deben ser genuinas y válidas, no V8 con etiqueta cambiada.

QA8 congelada: `.local-evidence/stage9/preserved-artifacts/app-qa.apk`, SHA256
`2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
No instalar una APK de producto distinta por conveniencia del ensayo.
Ejecutor Stage10 actual SHA256
`f8deb223ca743ee89ac59ec685003ffbbefc9d2213886738264209fc9f4e5230`;
puede reemplazarse sólo el paquete de tests, con identidad nueva registrada.

Único AVD: `MiGuardia_Stage10_API36_20261022_R2`, API36/x86_64,
`emulator-5586`, raíz
`C:/Users/User/.cache/miguardia-stage10-avd/20261022-r2`.
Checkpoint FINAL:
`.local-evidence/stage10/recovery-r2-20261023/recovered-avd-checkpoint.json`,
SHA256 `b5e71814d68050aa2dc2b0d84f45964944da94b00575a38045a9600c7a5154bf`.
Está apagado; no asumir disponibilidad a partir de un puerto ni iniciar otro AVD.
El respaldo anterior es PRE recuperación y no reemplaza la protección de este estado final.

Python y JBR locales existentes; entorno documentado en
`.local-evidence/structure/20261020/Enter-MiGuardiaEnvironment.ps1`.
SDK `C:/Users/User/AppData/Local/Android/Sdk`. Sin descargas/dependencias nuevas.
Confirmar Puerta0, recursos libres y entrada propia antes de escribir.

## OUTPUT

1. Matriz breve de versiones/casos, entradas y oráculo semántico esperado antes
   de ejecutar. Identificar cobertura reutilizada, faltante y nueva, sin rehacer
   baterías Stage8/9/10 por rutina.
2. Pruebas/adaptadores test-only necesarios; recibos de ejecución real con casos
   pasados/fallidos/omitidos separados, hashes de APK/ejecutor/fuentes/fixtures,
   protocolos completos y lecturas posteriores. Preservar todos los adversos.
3. Handoff autosuficiente y auditoría propia con delta desde entrada, resultado,
   límites y pendientes. Payload nuevo bajo `.local-evidence/stage11/`, sellado
   una sola vez al finalizar; recepción MAIN siempre fuera del payload.
4. Cese explícito de escrituras/Gradle/Android y AVD apagado; estado recibido
   Stage10 protegido y verificable. Nada de recursos activos sin dueño.

## SCOPE

Pruebas de Copias en `app/src/androidTest/`, tests unitarios/testSupport y
helpers host estrictamente necesarios en `tools/release/` con sus regresiones.
Nuevas rutas claras Stage11; no reescribir herramientas Stage10 para encubrir
sus antecedentes. Auditoría/handoff Stage11 propios en docs; MAIN reserva
STATUS, índices, contratos y su revisión.

Única excepción Gradle: cableado test-only mínimo en `app/build.gradle.kts`
para seleccionar explícitamente un ejecutor Stage11 con Application neutra,
manteniendo defaults y opción Stage10 intactos y exclusión entre ambos opt-ins.
No plugins, dependencias, variantes, opciones productivas ni cambio de QA8.

Un solo escritor y un operador Gradle/AVD. Reutilizar pruebas ya válidas y el
mismo candidato QA8. Si un guard histórico exige API33/puerto5582, mantenerlo:
crear adaptador/prueba explícita Stage11 para API36/R2, no relajarlo en silencio.

Preferir bases Room con nombre UUID, contextos privados y gateways reales
aislados, como las pruebas actuales del coordinador. Inspeccionar también rutas
globales/DataStore/archivos/foto para que el aislamiento no sea sólo el nombre
de DB. Hacer fallar la prueba si toca la DB o preferencias vivas de Stage10.
No arrancar MainActivity/runtimes/red/avisos como efecto lateral del ejecutor.
El ejecutor debe crear `android.app.Application` neutra y comprobarla antes de
operar. `BackupDocumentPublicationInstrumentedTest` utiliza la aplicación y el
coordinador vivos: **no ejecutarla intacta** ni asumir aislamiento por su nombre.
Reutilizar sus casos mediante coordinador/contextos UUID explícitos; revisar
cualquier otra prueba reutilizada que haga cast a `MiGuardiaApplication`.

Las operaciones de reemplazo y limpieza de temporales sólo pueden afectar los
recursos UUID inventariados creados por Stage11. No limpiar toda la QA ni su
base recibida. Respaldo final verificado antes del primer arranque y controles
semánticos de datos recibidos antes/después; distinguir cache técnico de datos.

Si se reproduce un defecto productivo, preservar entrada/caso mínimo y enviarlo
a MAIN antes de modificar producto. MAIN decidirá el ajuste acotado del contrato;
continuar mientras tanto comprobaciones independientes habilitadas. No esperar
por detalles rutinarios ni cambiar formato/cifrado para lograr un verde.

## DEPENDENCIES

Recuperación Stage10 auditada y liberación de recursos; encadenamiento autorizado
por Joaquín. Contratos de Copias vigentes y fixture sintética reproducible.
No depende de Samsung, credenciales reales, red, firma de distribución o servicios.

## DO NOT

No `wipe-data`, `pm clear`, desinstalaciones, nueva semilla Stage10, nuevo AVD,
cambios globales de reloj/seguridad o `clearAllDataForInstrumentation` sobre QA
recibida. No tocar respaldos/sellos/AVD anteriores, salvo arrancar el único R2
protegido. No usar otro emulador o Samsung aunque aparezca conectado.

No fuentes productivas, Room/migraciones/esquemas, dependencias, permisos,
versionado, cifrado, autenticación, red, formato de Copias ni firma de producto.
Gradle sólo admite la excepción test-only delimitada en SCOPE.
No commit/push/tag/staging/ramas/worktrees, publicación, costos o modelo/esfuerzo.
No contenido privado ni contraseñas en logs/entregas: claves de prueba efímeras
en memoria, datos ficticios marcados, rutas y evidencia sin secretos.

No atribuir al APK congelado el resultado de otra compilación. No ocultar fallos,
asunciones omitidas, timeout o errores de instrumentación como pruebas verdes.
No equiparar simulación de journal a muerte real de proceso, proveedor de prueba
a selector OEM, ni emulador a teléfono público. No reabrir rendimiento Stage8.

## VALIDATION

Usar `LocalBackupCoordinator` y dependencias reales Android. Oráculo independiente
por valores/UUID/relaciones, preferencias y hashes de imágenes después de cerrar
y reabrir; no basta el retorno exitoso del mismo método que se está probando.

- Exportación/restauración V8 no vacía: notas, adjuntos y situaciones, jornadas,
  foto de cronograma y preferencias portables; imágenes privadas coherentes,
  sin referencias huérfanas ni duplicación al repetir Merge.
- Lectura/restauración de formatos V5/V6/V7 genuinos sobre V8. Matriz declara
  qué campos existían por versión y qué valores derivados se esperan; no exigir
  tablas inexistentes ni aceptar pérdidas de campos actuales por accidente.
- Vista previa y cancelación sin escritura; Merge válido/idempotente, rechazo de
  conflictos sin resolver o timeline incompatible. Conservar las resoluciones
  admitidas `KEEP_CURRENT`/`USE_BACKUP` y `KEEP_BOTH` sólo para identidades distintas
  válidas; no convertir un conflicto resoluble en un rechazo obligatorio.
  ReplaceAll con confirmación
  exacta y canarios conocidos que sólo desaparecen en el destino de prueba.
- Copia sin cifrar y cifrada; clave incorrecta, integridad alterada, versión no
  admitida y entrada truncada rechazadas con el agregado anterior intacto.
- Journal: checkpoints de fallo/reinicio ya soportados en pruebas actuales;
  verificar estado antiguo o nuevo completo y limpieza privada/reanudación sólo
  tras cierre correcto. Reportar con precisión simulación versus proceso real.
- Si se usa SAF, proveedor controlado sólo con archivos ficticios y cancelación;
  selector OEM/humano no disponible queda pendiente y no bloquea lo independiente.

Justificar cantidad de ejecuciones según riesgo, no una campaña indefinida.
Antes de Android: dobles para guardas/timeout/código y fallos lógicos si se crea
helper; Python sin optimización. ADB explicita servidor local y serial, valida
identidad API/AVD/paquete/hash; jamás selección implícita del primer dispositivo.
El guard falla cerrado ante identidad/foco ajenos sin abrir otra app.

Compilar y ejecutar sólo variantes/pruebas afectadas necesarias; sin baterías
globales por ritual. Documentación: `python tools/docs_check.py`, catálogo QA
si cambió, y `git diff --check`. Distinguir fuentes del ejecutor de QA congelada.

## DONE WHEN

Las rutas principales, compatibilidad y fallos anteriores tienen prueba Android
real y comprobación posterior; no hay defecto conocido en el alcance sin dueño.
Estado Stage10 recibido protegido, artefactos/evidencia/hash/delta revisables,
adversos conservados, recursos liberados y handoff sellado recibido por MAIN.

Tras sellar, enviar efectivamente a MAIN chat
`01a09739-5279-75d0-a3b1-84bd2af6b36a` la clave stage/fase/sello, chat/turno y rutas.
Joaquín autorizó esta coordinación. MAIN acusa RECIBIDO y audita independientemente;
no confundir envío aceptado con recepción/auditoría. Terminar con respuesta final
propia. Si existe gate material, entregar parcial con causa y próximo dueño exacto,
no declarar restauración integral ni pedir una autorización ya concedida.
