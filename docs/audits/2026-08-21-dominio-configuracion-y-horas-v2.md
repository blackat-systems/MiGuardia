# Auditoría — dominio configurable de MiGuardia 2.0

- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- HEAD de partida: `8b7fa31fb3865e6ef162a6474d57a0061a32c588`
- Base sellada: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Estado: aprobado

## Resultado

Se implementó la primera pieza ejecutable propia de MiGuardia 2.0 como dominio
Kotlin puro. Puede describir una sola configuración laboral personalizable, sus
cambios desde una fecha, distintas formas de referencia de horas, clases de
trabajo extra y reglas propias de cada lugar sin imponer conductas por sector.

Este bloque no modifica Room, DataStore, Compose, navegación, permisos,
notificaciones, manifiesto, Gradle ni dependencias. Tampoco cambia todavía el
comportamiento visible de la aplicación.

## Contratos implementados

- catálogo cerrado de Vigilancia privada, Policía, Enfermería y Medicina;
- vocabulario sugerido por sector, sin convertirlo en una regla obligatoria;
- línea temporal de revisiones efectivas desde `LocalDate`;
- referencias de horas no usada, desconocida, fija o informada período por
  período;
- ventanas mensuales, semanales con día inicial configurable y ciclos
  anclados de longitud positiva;
- minutos positivos enteros, acotados para que su conversión a `Duration` no
  desborde;
- trabajo habitual que siempre ayuda al cumplimiento;
- clases extra que deciden individualmente si ayudan al cumplimiento y si
  tienen desglose propio;
- tres nombres visibles para una única disponibilidad: guardia pasiva,
  disponible para llamado o retén;
- reglas versionables de nocturnidad, fin de semana y feriado por lugar, con
  tratamiento diferente y visibilidad opcional, sin fórmulas monetarias.

Los valores informados período por período usan una identidad estable de
definición más ventana. La ausencia se representa como `Missing`, nunca como
cero, y se rechazan identificadores, ventanas o patrones contradictorios.

## Revisión independiente

Una revisión estática separada no encontró bloqueantes. Detectó dos problemas
menores antes del checkpoint:

1. la consulta de un valor podía recibir la misma identidad con un patrón de
   período reconstruido de forma incoherente;
2. las listas públicas podían alterarse mediante Java o un cast mutable y
   romper invariantes ya validadas.

Ambos fueron corregidos antes de la batería final. La consulta ahora valida el
patrón canónico y busca por identidad lógica; las colecciones públicas son no
modificables. Se agregaron pruebas de regresión para las dos conductas.

## Decisión deliberadamente pendiente

Este bloque conserva separados:

- qué configuración rige para una fecha;
- qué ventana mensual, semanal o de ciclo contiene esa fecha.

No prorratea ni reasigna todavía una referencia cuando la configuración cambia
en medio de una semana o ciclo. Esa regla debe cerrarse en el futuro bloque de
cumplimiento y Resumen. Tampoco decide aquí cómo versionar una regla del lugar
en medio de una jornada nocturna; la persistencia debe conservar la información
necesaria sin anticipar ese cálculo.

## Verificación automática

Comando global:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
```

Resultado: `BUILD SUCCESSFUL` en 2 min 9 s.

- JVM de aplicación: 41 pruebas, 0 fallos, 0 errores y 0 omitidas;
- JVM de base de datos: 5 pruebas, 0 fallos, 0 errores y 0 omitidas;
- JVM de dominio: 162 pruebas, 0 fallos, 0 errores y 0 omitidas;
- paquete nuevo `work`: 36 pruebas;
- total JVM: 208 pruebas aprobadas;
- lint: aprobado;
- APK `debug`: compilada;
- APK de instrumentación `qa`: compilada;
- no se agregó ninguna dependencia Android, Room o externa al dominio nuevo.

La instrumentación fue compilada pero no ejecutada en el Samsung para este
bloque, porque el cambio es Kotlin puro y no altera Android. La línea base de
Puerta 0 permanece en 169 pruebas físicas aprobadas sobre `SM-S938B`.

## Estado Git

El bloque puede consolidarse como un checkpoint local coherente. No se ejecutó
push, tag, Release, publicación ni ninguna operación sobre el paquete o los
datos de producción.

El siguiente bloque permitido es diseñar y probar Room v6 con migración
explícita `5→6`; no puede empezar desde una migración destructiva ni inventar
una traducción de los datos históricos.
