# Auditoría — reactivación de MAIN y Puerta 0 de MiGuardia 2.0

- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- HEAD de partida: `6dab82b8f239f8009cfcb32d400b50fcc4080836`
- Base sellada: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Estado: aprobada

## Resultado

PLANIFICACIÓN quedó cerrada y MAIN 2.0 volvió a estar activa. Joaquin autorizó
la ejecución secuencial del plan, sus pruebas y checkpoints mediante commits
locales. Esta autorización no incluye `push`, tags, Release, publicación ni
operaciones sobre el paquete o los datos de producción.

Se reemplazó el candidato mensual descartado por un contrato de dominio con
vigencia desde una fecha concreta. También se consolidó la retirada completa de
las funciones remunerativas: MiGuardia organiza jornadas y horas, pero no
calcula montos, salarios ni liquidaciones.

## Línea base verificada

- ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama no detached: `codex/miguardia-2.0`;
- `HEAD`: `6dab82b8f239f8009cfcb32d400b50fcc4080836`;
- la base de 1.0 continúa en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- `applicationId`: `com.blackatsystems.miguardia`;
- Room: versión 5, trece entidades y esquemas exportados v1 a v5;
- no se modificaron Gradle, manifiesto, permisos, dependencias ni esquemas Room
  en esta puerta.

## Fuentes de verdad regularizadas

- `AGENTS.md` y `docs/STATUS.md` reflejan MAIN activa;
- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md` explica el producto en lenguaje humano;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md` fija el orden de implementación;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md` es el prompt rector activo;
- `docs/prompts/README.md` clasifica cada prompt;
- `docs/adr/0020-modelo-laboral-personalizable-y-vigencia-por-fecha.md`
  reemplaza la vigencia únicamente mensual, las extras automáticas y las extras
  sin horario;
- `docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md` habilita el primer
  bloque de código, limitado a dominio Kotlin puro.

Las fichas de `docs/sectores/` incorporan la evidencia disponible sin convertir
respuestas particulares en reglas universales. Enfermería y Medicina se
mantienen como sectores separados, aunque los dos archivos recibidos contengan
las mismas dos respuestas.

## Cambios ejecutables auditados

Se retiraron el cálculo remunerativo SUVICO, su preferencia de antigüedad, su
sección de Resumen, sus pruebas y las imágenes de escalas salariales. Los
consumidores de Compose y sus constructores quedaron ajustados a las firmas
nuevas.

La revisión independiente detectó dos expectativas instrumentadas y una
descripción del menú que aún mencionaban una estimación. Se actualizaron a
`Horas y eventos del mes seleccionado` y `Horas y eventos del mes` antes de la
compilación final.

La búsqueda del código de `app` y `core` no encontró referencias restantes a
`Suvico`, `RemunerationPreferences` ni estimaciones remunerativas. La frase de
Vacaciones conserva únicamente la aclaración explícita de que MiGuardia no
calcula montos.

## Verificación automática

Comando:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL` en 1 min 43 s.

- JVM de aplicación: 41 pruebas, 0 fallos, 0 errores y 0 omitidas;
- JVM de base de datos: 5 pruebas, 0 fallos, 0 errores y 0 omitidas;
- JVM de dominio: 126 pruebas, 0 fallos, 0 errores y 0 omitidas;
- total JVM: 172 pruebas aprobadas;
- lint: aprobado, sin errores;
- APK `debug`: compilado;
- APK de instrumentación `qa`: compilado;
- `git diff --check`: correcto;
- espacios finales: 0;
- mojibake detectado por la auditoría: 0;
- `fallbackToDestructiveMigration`: 0 archivos de producción;
- `allowMainThreadQueries`: 0 archivos de producción;
- archivos con nombres potenciales de secretos: 0.

Los hashes de los esquemas Room v1 a v5 permanecen intactos respecto de esta
línea de trabajo.

## QA física

ADB detectó un único dispositivo autorizado:

- modelo: Samsung `SM-S938B`;
- serie: `R5CY529W6PL`;
- estado ADB: `device`.

La primera ejecución se inició mientras la ventana activa del teléfono era
`Bouncer`, es decir, la pantalla segura de bloqueo. Las primeras siete pruebas
no pudieron encontrar una jerarquía Compose y la corrida se interrumpió para no
registrar 169 falsos fallos ambientales.

Con el Samsung desbloqueado, una segunda corrida completó las 169 pruebas. Pasó
168 y falló únicamente el recorrido de alarmas reales antes de comenzar, porque
Android todavía no había concedido acceso a alarmas exactas para el paquete
QA. Las demás pruebas quedaron aprobadas.

Se repitió la batería y se concedió `SCHEDULE_EXACT_ALARM: allow` sólo a
`com.blackatsystems.miguardia.qa` después de que Gradle instaló el paquete. La
tercera corrida obtuvo:

- instrumentación en Samsung: 169 pruebas;
- fallos: 0;
- errores: 0;
- omitidas: 0;
- resultado Gradle: `BUILD SUCCESSFUL` en 3 min 39 s.

El recorrido real verificó recordatorio, comienzo y finalización con alarmas
exactas. Gradle retiró al terminar tanto el paquete QA como su APK de pruebas;
el permiso temporal no quedó instalado. El paquete productivo no fue abierto,
modificado ni reinstalado.

## Estado y próximo paso

La puerta quedó guardada en dos checkpoints locales coherentes:

- `3519606aeda3a26bed7ec8fc0feb8b7f3f788d35`: retiro de las funciones
  remunerativas;
- el checkpoint documental que contiene esta auditoría: cierre de
  PLANIFICACIÓN, reactivación de MAIN y contrato del primer bloque.

No se ejecutó push, tag, Release ni publicación. MAIN puede abrir el primer
bloque:
`Reglas internas de sector, vigencia por fecha y referencia de horas`.
