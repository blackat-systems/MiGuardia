# MiGuardia 3.0

Aplicación Android personal para organizar jornadas y horas de Vigilancia privada, Policía, Enfermería y Medicina. Datos laborales locales, una sola configuración y reglas elegidas por cada persona. No calcula sueldos ni reemplaza sistemas institucionales.

Este repositorio inicia la evolución hacia MiGuardia 3.0 desde la base estable 2.0.2. La etapa de descubrimiento todavía no autoriza cambios funcionales, de arquitectura ni del versionado Android.

## Entrar al proyecto

- Estado actual, autorización y pendientes: `docs/STATUS.md`.
- Orden obligatorio de MAIN y flowchart: [ruta de orquestación](docs/ORQUESTACION_MAIN.md); STATUS indica el punto de continuación.
- Reglas estables: `AGENTS.md`; lecturas adicionales: `docs/LECTURAS_POR_TAREA.md`.
- Producto: `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`; detalle en planificación.
- Comprobaciones: `docs/VALIDACION.md`; decisiones técnicas: `docs/adr/README.md`.

La base heredada contiene Calendario, carga/edición, recurrencias, horario real y extras, disponibilidad, Resumen, próximos eventos, notificaciones, Widget, Clima por objetivo, fotos, informes PDF/XLSX, Copias, bloqueo opcional, Ayuda, actualización pública manual, apoyo y feedback voluntarios. Implementación presente, commit integrado, QA y distribución son estados distintos: consultar STATUS antes de asumir una entrega publicable.

MiGuardia 1.0 fue una prueba interna y base de código. V2 comienza limpia, sin migración de datos V1; las migraciones posteriores conservan datos V2.

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
python tools/verify.py --profile interop --website ../website --node 'C:\Program Files\nodejs\node.exe' --fresh-tests
```

La ruta Node es un ejemplo local: indicar la propia o dejar que `--node` se resuelva desde PATH. `--plan` muestra tareas sin ejecutarlas ni escribir evidencia. Para detalle, perfiles completos, compatibilidad histórica, actualizaciones públicas y autorización de dispositivos, consultar VALIDACION. La firma Debug local opcional se configura con `miguardia.debugKeystore`; Release y publicación conservan sus puertas.

## Privacidad y conexiones

Jornadas, preferencias y fotos permanecen en el teléfono. Sin cuentas laborales, nube laboral, sincronización, analítica, telemetría ni ubicación automática. Android Backup/transferencia automática están deshabilitados; las Copias de MiGuardia son conscientes.

Clima consulta su proveedor si la persona lo habilita. Las actualizaciones públicas se consultan y descargan de forma consciente, sin cuenta, activación ni envío de datos laborales. Apoyar MiGuardia y el feedback voluntario abren por acción expresa páginas HTTPS externas. Usar la ciudad actual exige una acción propia y permiso de ubicación aproximada; convertir una dirección también es consciente, pero no pide ese permiso. Los informes y Copias incluyen datos privados sólo según las selecciones y confirmaciones aprobadas.

## Historia y evidencia

`docs/archive/README.md` localiza contratos reemplazados. `.ignore` evita arrastrarlos en búsquedas generales, sin borrarlos ni ocultarlos de Git. Evidencia de ejecución local en `.local-evidence/`, fuera de los directorios que limpia Gradle. Política y rutas anteriores en `docs/EVIDENCIA.md`.
