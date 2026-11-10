# Etapa 13 · Datos y archivos — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 13 · Datos y archivos**. La etapa 12.2 (Cálculos y
consulta) quedó cerrada el 8/11/2026 (commit `8158b9e`).

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md`, `docs/PROCESO_SIMPLE.md` y `docs/LECTURAS_POR_TAREA.md`
   (fila «Informes/Copias» y «Fotos»: ADR 0008/0026/0034/0035, Planificación §16).
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. Si algo no
   coincide con STATUS, frená esa parte y avisame.
3. Leé la fila 13 de `docs/PLAN_MAESTRO_CIERRE_3_0.md` y, en
   `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`, las secciones
   «A · Copias y restauración» (A166–A188), «A · Notas privadas y fotos mensuales» (A189–A207) y los
   casos C078, C079 y C095 de «C · Notas, avisos, Clima y horario real contextuales». Son la lista de
   qué probar, no un circuito de recibos: seguimos el Proceso simple.

**Qué se quiere**
Demostrar que los datos se conservan y se recuperan: notas, fotos y Copias, en reemplazo y en mezcla,
con el selector de documentos real del Samsung, cancelación, archivos dañados e interrupción real
cuando el caso lo pide. Tres tramos del mismo trabajo:
- **Notas privadas** (A189–A197, C078, C079): por UUID sin confundir jornadas iguales; crear, editar,
  imágenes elegidas a conciencia, abrir, quitar del borrador, guardar con control concurrente,
  eliminar con confirmación y descartar.
- **Fotos del mes** (A198–A207, C095): abrir y cambiar de mes, importar con Photo Picker, visor con
  pinza, asociar y desasociar objetivo, reemplazar, eliminar, orientación EXIF, foto ausente y
  reintento sin sustituirla.
- **Copias y restauración** (A166–A188): crear con y sin cifrado (advertencia expresa), contraseña
  sin guardarla, cancelar, abrir Copia existente, rechazar contraseña incorrecta, integridad inválida,
  truncado o formato no admitido; revisar antes de escribir; fusionar, resolver conflictos (actual,
  de la Copia, ambas), reemplazar todo con frase exacta; bloquear mezcla de otra línea temporal;
  etapa no cancelable; recuperación pendiente sin fingir éxito; volver al Calendario; bytes auténticos
  de imágenes; lectura de V5/V6/V7 además de V8.

**Cómo se prueba**
- Primero en la PC: armá el mapa de qué casos ya cubren las pruebas existentes (BackupComposeTest,
  BackupRecoveryInstrumentedTest, BackupPhotoFilesInstrumentedTest, ExceptionsComposeTest,
  PhotosComposeTest, ManualLoadNotesInstrumentedTest y afines) y cuáles faltan. Proponeme la lista
  agrupada por pantalla antes de escribir pruebas nuevas.
- PC: `python tools/verify.py --profile local` (ya termina bien; última corrida 1339 pruebas, 0 fallas).
- Samsung: sólo con mi autorización en el momento. La pareja instalada, el entorno (SDK, Java,
  `MSYS_NO_PATHCONV=1`) y el script de corrida están en STATUS. Las pruebas que necesiten mi dedo
  (selector de documentos real, Photo Picker, pinza) me las pedís paso a paso, en castellano, con lo
  que voy a ver en pantalla.

**Lo que ya sabemos (no repetir errores)**
- **Copias toca los datos de verdad.** Restaurar o reemplazar NUNCA puede apuntar a la base QA real
  (`miguardia-v2.db`). El coordinador de Copias de la Application usa la base real: las pruebas deben
  usar un grafo aislado por corrida, como `Stage12CalculationStore` (base `stage12-x-<id>.db`, carpeta
  privada bajo `files/reports/artifacts/…`, limpieza que también borra `cache/<base>.lck`). Si un caso
  no puede aislarse, frená y explicame antes de correrlo.
- Foto de datos QA antes y después de cada sesión con el teléfono (`snapshot_digest.py`); las 23 bases
  tienen que quedar idénticas. `files/profileInstalled` cambia al instalar (marca de Android).
- Arnés de 12.2 reutilizable: `Stage12CalculationStore` (app real `MiGuardiaApp` sobre base propia),
  `Stage12CalculationFixture`, `run_methods_diag.sh`, diagnóstico de árbol por logcat ante esperas
  vencidas.
- Compose: las filas tocables fusionan su contenido (las etiquetas internas sólo existen en el árbol
  sin fusionar; tocar la fila, no el hijo); desplazarse hasta el nodo antes de tocarlo, salvo que no
  esté en una lista; las hojas y diálogos usan el contexto de la Activity.
- Antes de cerrar la base de la corrida, vaciar los ViewModels de la Activity de prueba
  (`viewModelStore.clear()`); si no, al volver de un selector reabren la base durante la limpieza.
- La prueba corre dentro del proceso de QA: no puede escribir en la carpeta del APK de pruebas; para
  traer un archivo a la PC usar logcat en base64 o una carpeta de la corrida.
- Una falla de la prueba no es un bug de la app sin reproducción causal. Si algo falla dos veces igual,
  pará y pensá antes del tercer intento.

**Cuándo está lista**
Notas, fotos y Copias conservan y recuperan exacto (bytes, identidades y preferencias) sobre fixtures
propias; cancelación, archivos dañados e interrupción comprobados al nivel que pide cada caso; datos QA
del teléfono intactos; STATUS con la lista ✅/⏳/❌ de la etapa; commit local de lo probado. Fuera de
alcance: avisos, Widget, bloqueo, Clima, temas, firma y publicación.

**Límites**
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el momento;
no borrar datos del teléfono (restos propios de prueba, sólo con mi permiso). No agregar funciones ni
dependencias nuevas. Hablame con nombres claros, sin códigos (los códigos sólo entre paréntesis si
hacen falta).
