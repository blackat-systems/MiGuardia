# Auditoría — Reglas para configurar el trabajo por mes y estado Git

> Fotografía histórica de un candidato descartado. No autoriza recuperar ese
> código ni su vigencia mensual. El contrato vigente está en ADR 0020 y en
> `REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md`.

- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- HEAD auditado: `6dab82b8f239f8009cfcb32d400b50fcc4080836`
- Base sellada: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Resultado técnico: pruebas y alcance correctos
- Estado de producto: candidato en revisión por PLANIFICACIÓN; commit no autorizado

## Línea base comprobada

- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama no detached: `codex/miguardia-2.0`.
- `main`, `origin/main` y el commit señalado por `v1.0.0` continúan en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- La rama 2.0 contiene dos commits locales posteriores:
  - `a3e89fdb56aedeed77c89824cec137f37f4c9619`, Calendario adaptable;
  - `6dab82b8f239f8009cfcb32d400b50fcc4080836`, planificación y traspaso MAIN.
- `git ls-remote` confirmó que `codex/miguardia-2.0` todavía no existe en el
  remoto privado.
- No había archivos staged. Las reglas de configuración laboral y su
  documentación estaban en el
  árbol de trabajo sin commit.

## Alcance auditado

Producción nueva:

- `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/workconfig/WorkConfiguration.kt`;
- `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/workconfig/WorkConfigurationTimeline.kt`;
- `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/workconfig/MonthlyWorkDataRules.kt`.

Pruebas nuevas:

- `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/workconfig/WorkConfigurationTest.kt`;
- `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/workconfig/WorkConfigurationTimelineTest.kt`.

También se revisaron el prompt de este bloque y las actualizaciones de STATUS,
PLANIFICACIÓN, MAIN 2.0 y ADR 0019.

## Resultado funcional

No se encontraron defectos concretos en las reglas implementadas. El dominio
representa:

- exactamente cuatro sectores: Vigilancia privada, Enfermería, Medicina y
  Policía;
- ausencia expresa de sectores `Otro` o `Salud`;
- base mensual definida, desconocida o no aplicable;
- exceso mensual habilitable sólo con base definida;
- política nocturna deshabilitada o definida en minutos;
- disponibilidad pasiva versionada;
- motor legado V1 y motor V2;
- raíz migrada, revisiones de usuario y estado sin configurar;
- resolución determinista por mes;
- predicado de datos laborales y primera vigencia permitida.

El dominio no posee todavía consumidores fuera de sus pruebas. Por lo tanto, no
modifica la interfaz, la persistencia ni los cálculos visibles actuales.

## Validación ejecutada por MAIN

Comando:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug
```

Resultado: `BUILD SUCCESSFUL`.

- JVM global: 197 pruebas, 0 fallos, 0 errores y 0 omitidas.
- Reglas de configuración laboral: 22 pruebas.
- Lint: 0 errores, 2 advertencias de versiones y 3 sugerencias preexistentes.
- `assembleDebug`: aprobado.
- `git diff --check`: correcto.
- Archivos no rastreados: sin espacios finales, mojibake ni falta de salto final.

No se ejecutó instrumentación ni QA física porque este bloque contiene lógica
Kotlin/JVM pura y no toca una superficie Android.

## Límites comprobados

Este bloque no modifica Room v5, DataStore, Compose, Gradle, manifiesto, permisos,
dependencias, `applicationId`, `versionCode`, `versionName` ni el Samsung.
Tampoco implementa Room v6, migración `5→6`, Perfil V2, extras, pasivas
persistidas o el motor completo de horas.

## Estado Git y siguiente puerta

La auditoría no creó commit ni push. Después de esta auditoría, Joaquin reabrió
PLANIFICACIÓN para entender y revisar el alcance en lenguaje cotidiano. Por lo
tanto, el resultado técnico no autoriza todavía commit, siguiente bloque ni
push.
