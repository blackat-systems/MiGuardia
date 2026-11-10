# MiGuardia 3.0

Aplicación Android personal para organizar jornadas y horas de Vigilancia privada, Policía, Enfermería y Medicina. Datos laborales locales, una sola configuración y reglas elegidas por cada persona. No calcula sueldos ni reemplaza sistemas institucionales.

Este repositorio desarrolla MiGuardia 3.0 desde la base estable 2.0.2. El alcance autorizado y el punto de continuación están en STATUS; el handoff inicial conserva la procedencia, no el estado actual.

## Entrar al proyecto

- Arranque: `AGENTS.md` y `docs/STATUS.md`, único estado actual, autorización y pendientes.
- Lecturas por área: `docs/LECTURAS_POR_TAREA.md`; método de integración: `docs/WORKFLOW.md`.
- Contrato y handoff de continuación: los señalados por STATUS; índice en `docs/prompts/README.md`.
- Archivo recuperable: [ubicación y restauración](docs/EVIDENCIA.md) e [índice](docs/archive/README.md).
- Producto: `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`; detalle en planificación.
- Comprobaciones: `docs/VALIDACION.md`; decisiones técnicas: `docs/adr/README.md`.

La base heredada contiene Calendario, carga/edición, recurrencias, horario real y extras, disponibilidad, Resumen, próximos eventos, notificaciones, Widget, Clima por objetivo, fotos, informes PDF/XLSX, Copias, bloqueo opcional, Ayuda, actualización pública manual, apoyo y feedback voluntarios. Implementación presente, commit integrado, QA y distribución son estados distintos: consultar STATUS antes de asumir una entrega publicable.

MiGuardia 1.0 fue una prueba interna y base de código. V2 comienza limpia, sin migración de datos V1; las migraciones posteriores conservan datos V2.

## Ubicación de los archivos

| Ruta | Responsabilidad |
|---|---|
| `app/src/main/` | Aplicación Android, interfaz, recursos y adaptadores del sistema |
| `core/domain/src/main/` | Reglas y contratos de negocio sin Android |
| `core/database/src/main/` y `core/database/schemas/` | Persistencia Room, migraciones y esquemas exportados |
| `src/test/`, `src/androidTest/` de cada módulo | Pruebas JVM e instrumentadas; `app/src/testSupport/` sólo aporta fixtures de pruebas |
| `settings.gradle.kts`, Gradle y `gradle/` | Módulos, compilación, catálogo de versiones y wrapper |
| `tools/` | Verificación documental, pruebas y catálogo QA |
| `docs/` | Estado, ruta de lectura, planificación y documentación vigente |
| `docs/adr/`, `docs/prompts/` | Decisiones técnicas e índice de contratos |
| `docs/archive/`, `docs/audits/`, `docs/diagrams/` | Historia, evidencia fechada y diagramas de su respectiva base; no otro estado actual |
| `.local-evidence/`, `build/`, `.gradle/`, `.kotlin/` | Evidencia local y generados ignorados; no mover ni eliminar por antigüedad |

El índice Git no determina el orden de lectura. Los archivos nuevos del candidato pueden seguir sin rastrear: no agregarlos al staging ni mover fuentes para ordenar la vista. `local.properties`, firmas y datos privados permanecen fuera de Git.

El [archivo](docs/archive/README.md) conserva la auditoría del traslado del workspace del 20/10/2026. Sus rutas absolutas son procedencia histórica; la ubicación del respaldo actual está en EVIDENCIA.

## Entorno y comprobaciones

Android SDK 37 y Java/JBR compatible con el wrapper Gradle del proyecto. El código usa nivel Java 17; JAVA_HOME identifica el runtime de Gradle. Android Studio puede crear `local.properties` con la ruta local del SDK. Python 3.11+ para herramientas de desarrollo, sin bibliotecas Python adicionales. No se necesita una clave privada de distribución para compilar Debug.

Desde la raíz, en PowerShell:

```powershell
python tools/docs_check.py
python tools/verify.py --profile local --fresh-tests
python tools/verify.py --profile build
```

Las pruebas locales no necesitan Node ni otro checkout. La interoperabilidad es una puerta explícita y obligatoria cuando cambian las actualizaciones públicas o su contrato: requiere el proyecto `website` en sólo lectura y Node con transformación TypeScript. El runner comprueba esas capacidades; no instala herramientas.

```powershell
python tools/verify.py --profile interop --website ../../website --node 'C:\Program Files\nodejs\node.exe' --fresh-tests
```

La ruta Node es un ejemplo local: indicar la propia o dejar que `--node` se resuelva desde PATH. `--plan` muestra tareas sin ejecutarlas ni escribir evidencia. Para detalle, perfiles completos, compatibilidad histórica, actualizaciones públicas y autorización de dispositivos, consultar VALIDACION. La firma Debug local opcional se configura con `miguardia.debugKeystore`; Release y publicación conservan sus puertas.

Si Python o Java no están en PATH, usar sus ejecutables locales o preparar la sesión según la auditoría del traslado; no cambiar dependencias del proyecto para resolver una ruta del equipo.

## Privacidad y conexiones

Jornadas, preferencias y fotos permanecen en el teléfono. Sin cuentas laborales, nube laboral, sincronización, analítica, telemetría ni ubicación automática. Android Backup/transferencia automática están deshabilitados; las Copias de MiGuardia son conscientes.

Clima consulta su proveedor si la persona lo habilita. Las actualizaciones públicas se consultan y descargan de forma consciente, sin cuenta, activación ni envío de datos laborales. Apoyar MiGuardia y el feedback voluntario abren por acción expresa páginas HTTPS externas. Usar la ciudad actual exige una acción propia y permiso de ubicación aproximada; convertir una dirección también es consciente, pero no pide ese permiso. Los informes y Copias incluyen datos privados sólo según las selecciones y confirmaciones aprobadas.

## Historia y evidencia

`docs/archive/README.md` localiza los contratos reemplazados en el respaldo externo. `.ignore` excluye el catálogo histórico de búsquedas generales. La evidencia de ejecución vigente está en `.local-evidence/`, fuera de los directorios que limpia Gradle. Ubicación y recuperación en `docs/EVIDENCIA.md`.
