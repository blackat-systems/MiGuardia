# Integridad y rutas del workspace MiGuardia

Fecha local: 2026-10-20, America/Buenos_Aires.

Los archivos de MiGuardia 3.0 conservan su ubicación relativa y contenido tras
el traslado del perfil Windows `Joaquin` a `User`. Se corrigieron referencias
activas al perfil anterior y al checkout web, se localizó el archivo histórico
externo y se repuso el SDK Android que faltaba. No se reorganizaron fuentes por
apariencia ni se alteró el alcance funcional de Stage 7.

## Integridad de entrada

Contenedor actual: `C:\Users\User\Desktop\chatgptprojects\MiGuardiaContainer`.
Repositorio 3.0 independiente, `.git` propio, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae` y upstream original. Contenedor fuera
de Git, un worktree y staging vacío. Se preservaron 156 entradas rastreadas con
cambios y 89 nuevas; no se atribuyen a esta reparación.

Comparación de entrada con
`.local-evidence/stage7/android-font-20261011/exit-captures-20261013/repository.json`:

- 976 rutas iguales al sello; ninguna añadida, removida o renombrada.
- 975 archivos presentes con SHA-256 idéntico, también en la copia `files/`.
- La entrada restante tiene hash `null`: la eliminación de
  `VerticalScrollbarMetricsTest.kt` ya estaba registrada. No se restauró.
- Atlas Stage 6 nuevo: 41/41 originales coincidentes y páginas del atlas presentes.
- APK QA y runner R4 coincidentes con su manifiesto; ambas capturas finales del
  13/10 conservan los hashes de la auditoría Stage 7.

Estos cotejos acreditan conservación de archivos, no una ejecución física nueva.

## Rutas corregidas

| Fuente | Corrección |
|---|---|
| STATUS y contrato activo Stage 7 | Contenedor actual bajo `C:\Users\User`; fecha de esta revisión y capturas identificadas como históricas, sin afirmar teléfono abierto hoy |
| README y VALIDACION | Ejemplos `--website ../../website`, que resuelven el checkout real junto al contenedor; `../website` no existe desde 3.0 |
| EVIDENCIA | Nueva ubicación externa en `Documents\03_Proyectos\MiGuardia\Archivo_historico\MiGuardia-Pre3-Archive-20260912-r2`, preservando la procedencia anterior |
| `local.properties`, ignorado | `sdk.dir=C:/Users/User/AppData/Local/Android/Sdk`, sin cambiar otros contratos |

El archivo externo localizado contiene `archive-manifest.json`, el bundle Git
y `evidence-curated/curated-manifest.json`; no se revalidó todo su contenido.
Las rutas absolutas de recibos, handoffs y documentos fechados se conservaron:
no se efectuó un reemplazo global sobre evidencia histórica.

## Entorno local reparado

El SDK configurado apuntaba al perfil inexistente y no había SDK en las
ubicaciones comprobadas. Joaquín autorizó reponerlo y aceptó expresamente la
licencia de Android SDK de Google. Se instalaron sólo los paquetes necesarios:

- Plataforma Android 37.0, revisión 2.
- Build Tools 36.0.0, versión predeterminada de AGP 9.3.
- Platform Tools 37.0.1, sin ejecutar ADB ni usar dispositivos.

El gestor de línea de comandos 22.0 procede de la descarga oficial de Google
`commandlinetools-win-15859902_latest.zip`, SHA-256
`90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`.
Los paquetes se instalaron con el gestor oficial, sin emuladores ni imágenes de
sistema. Referencias: [descargas Android](https://developer.android.com/studio),
[gestión de paquetes SDK](https://developer.android.com/tools/sdkmanager) y
[compatibilidad AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes).

Se reutilizaron JBR 25.0.2 existente y Python 3.12.14 del runtime local de Codex.
El alias `python` de Windows no resolvía una instalación. El helper ignorado
`.local-evidence/structure/20261020/Enter-MiGuardiaEnvironment.ps1` prepara
Python, JAVA_HOME y SDK únicamente en la sesión actual, sin modificar el PATH
ni variables globales de Windows. Desde el repositorio:

```powershell
. ./.local-evidence/structure/20261020/Enter-MiGuardiaEnvironment.ps1
python tools/docs_check.py
python tools/qa.py --check-catalog
```

Scripts y logs locales en `.local-evidence/structure/20261020/` y
`C:\Users\User\.cache\miguardia-toolchains\sdk-repair-20261020`.

## Comprobaciones

- Gradle 9.5.0 `--offline help`: correcto, 39 s y una tarea ejecutada.
- `:app:processDebugMainManifest --offline`: correcto, 16 s y seis tareas
  ejecutadas; log `gradle-sdk-smoke.log` en la evidencia de esta reparación.
- Planes local e interoperabilidad generados sin ejecutar sus baterías.
  `validate_website('../../website')` valida el checkout y sus entradas requeridas;
  no se ejecutó código web ni se modificó ese repositorio.
- Comprobación documental, catálogo QA y diff se repiten tras la reparación.
  El catálogo de entrada conserva 121 clases, 28 grupos y 138 rutas de fuentes
  principales/auxiliares existentes; el archivo documental mantiene 103 snapshots.

No se ejecutaron nuevamente las 935 pruebas JVM ni la QA física R4. Las
comprobaciones Gradle anteriores verifican configuración y procesamiento del
manifiesto, no una compilación completa, firma o actualización pública nuevas.

## Límites y continuidad

Los dos checkouts 2.x conservan su administración Git compartida en la ubicación
actual. `Repositorio-Git` está limpio; `MiGuardia-2.0` presenta siete cambios
previos de Ayuda/soporte/UI y pruebas, 83 inserciones, que se preservaron sin
editar, copiar a 3.0 ni tratar como un árbol limpio. Las carpetas de recuperación
y `tmp/pdfs` permanecen intactas; no son fuentes de reconstrucción automática.

El delta versionable de esta reparación es documental: README, STATUS,
VALIDACION, EVIDENCIA, ruta de entrada del contrato activo y esta auditoría.
Código, Gradle/dependencias, manifiestos fuente, Room/esquemas, Copias, versión,
applicationId y firma no cambian. `local.properties` y los helpers permanecen
ignorados. Sin staging, commit, push, dispositivos ni publicación.

Stage 7 sigue EN_CURSO con sus alertas de rendimiento y aceptación pendientes.
Esta reparación permite retomar desde las rutas actuales; no acepta el producto
ni inicia Stage 7.1. Sello de salida local en
`.local-evidence/structure/20261020/exit/repository.json`.
