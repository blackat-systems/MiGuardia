# Reglas internas de configuración y períodos de horas V2

- Estado: **CERRADO**
- Responsable: MAIN o especialista de dominio
- Dependencia: documentación cerrada y ADR 0020
- Tipo de entrega: Kotlin puro y pruebas JVM

## TASK

Implementar la base de dominio que permita representar una configuración
laboral personalizable, sus cambios desde una fecha y las distintas formas de
referencia de horas. Este bloque no calcula todavía un Resumen mensual completo
ni guarda datos en Room.

## CONTEXT

MiGuardia 1.0 posee un cálculo fijo para Vigilancia. MiGuardia 2.0 necesita un
catálogo cerrado de cuatro sectores, pero ninguna profesión puede imponer una
base, nocturnidad o disponibilidad universal. La propuesta anterior por mes fue
descartada y su código no existe en el árbol actual.

## INPUTS

- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/adr/0020-modelo-laboral-personalizable-y-vigencia-por-fecha.md`;
- modelos y estilo del módulo `:core:domain`;
- Java time, UUID y JUnit ya disponibles.

## OUTPUT

Agregar bajo `core/domain/.../work/` modelos y reglas puras para:

1. catálogo exacto `Vigilancia privada`, `Policía`, `Enfermería`, `Medicina`;
2. vocabulario sugerido de lugar y jornada por sector, entendido como copy
   predeterminado y no como una regla laboral universal;
3. una configuración y sus revisiones efectivas desde `LocalDate`;
4. resolución determinista de la revisión vigente para una fecha;
5. referencia de horas:
   - no utilizada;
   - existente pero desconocida, con período opcional;
   - fija, con período y minutos positivos;
   - informada período por período, sin valor cero inventado;
6. períodos mensual, semanal y ciclo personalizado;
7. semana con día inicial configurable y lunes sugerido por la interfaz;
8. ciclo con cantidad positiva de días y fecha de anclaje;
9. valores de referencia informados por período como datos separados, con
   identidad de la definición y ventana estable; ausencia significa
   `Falta informar` y dos valores para la misma ventana se rechazan;
10. tipo de trabajo habitual personalizable; todo trabajo habitual activo ayuda
    al cumplimiento y no lleva una bandera para desactivarlo;
11. clase extra con nombre, si ayuda al cumplimiento y si obtiene un desglose
    propio; desactivar ese desglose nunca elimina su identidad de extra;
12. nombre visible de disponibilidad: guardia pasiva, disponible para llamado o
    retén;
13. reglas de lugar versionables para nocturnidad, fin de semana, marcas
    informativas de tratamiento diferente y visibilidad en Resumen, sin montos.

Los nombres internos exactos pueden ajustarse si mejoran claridad, pero deben
conservar estas capacidades y sus invariantes.

## SCOPE

Permitido:

- `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/work/**`;
- `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/work/**`;
- este prompt y documentación directa del bloque si una contradicción técnica
  concreta lo exige.

## DEPENDENCIES

- No depende de Room, Compose, Android, DataStore ni red.
- Puede reutilizar tipos de Java time y patrones de validación existentes.
- No modifica `MonthlyHours.kt`; el motor V1 continúa como compatibilidad.

## LÍMITE ENTRE VIGENCIA Y PERÍODOS

Este bloque mantiene dos reglas independientes:

- la línea temporal responde qué configuración rige en una `LocalDate`;
- el período responde qué ventana mensual, semanal o de ciclo contiene una
  `LocalDate`.

No debe existir todavía una función que combine ambas para prorratear, partir o
reasignar una referencia cuando una revisión comienza en medio de una semana o
ciclo. Esa decisión pertenece al futuro bloque de motor de cumplimiento y
Resumen; debe cerrarse antes de calcular progreso. El API de este bloque debe
permanecer neutral para admitir esa decisión sin reescribir los modelos.

## DO NOT

- No implementar Room v6, DAO, repositorios ni migraciones.
- No modificar pantallas, navegación, perfil, Calendario o Resumen.
- No recuperar el candidato mensual desde Git, worktrees o memoria.
- No usar `YearMonth` como vigencia de configuración.
- No fijar 204 horas ni 21:00–06:00 para instalaciones nuevas.
- No crear extras automáticamente por superar una referencia.
- No permitir minutos negativos, cero donde se exige un valor o duraciones con
  segundos/fracciones.
- No permitir una nocturnidad deshabilitada con horario o desglose activo, ni un
  fin de semana `ninguno` con tratamiento diferente o desglose activo.
- No usar `Double` para tiempo.
- No agregar dependencias.

## VALIDATION

Pruebas JVM mínimas:

- los cuatro sectores y su vocabulario;
- fecha anterior a toda revisión sin configuración aplicable;
- revisión exacta, entre revisiones y posterior a la última;
- revisiones recibidas desordenadas resueltas de forma determinista;
- dos revisiones en la misma fecha rechazadas;
- cambio 31 de diciembre→1 de enero;
- referencia no utilizada sin valor;
- referencia desconocida nunca proyectada como cero;
- referencia fija positiva y rechazo de cero/negativa/fraccionaria;
- referencia por período faltante sin convertirla en cero, valor positivo y
  duplicado de la misma definición/ventana rechazado;
- semana con cualquier `DayOfWeek`;
- ciclos de 14, 21 y 28 días, más rechazo de longitud inválida;
- anclaje de ciclo antes y después de la fecha consultada;
- clase extra que ayuda y que no ayuda al cumplimiento;
- regla nocturna deshabilitada y definida con cruce de medianoche;
- inicio nocturno igual a final rechazado;
- sábado, domingo, ambos o ninguno;
- estados incompatibles de nocturnidad y fin de semana rechazados;
- ninguna fórmula monetaria ni dependencia Android.

Ejecutar como mínimo:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:domain:testDebugUnitTest
```

Después revisar `git diff --check` y el diff completo del alcance.

## DONE WHEN

- todos los modelos expresan las decisiones sin una fórmula sectorial;
- las invariantes están validadas en construcción o funciones dueñas;
- las pruebas cubren límites y pasan;
- `core:domain` sigue independiente de Android y Room;
- no cambió comportamiento visible ni persistencia;
- MAIN auditó el bloque y puede recomendar un commit local antes de diseñar
  Room v6.

## RESULTADO VERIFICADO

El bloque quedó implementado el 2026-08-21 bajo `core/domain/.../work/`, sin
dependencias nuevas ni cambios en Android, Compose o Room. Incluye 36 pruebas
propias y cerró una revisión cruzada que reforzó la identidad de los valores por
período y la inmutabilidad de las colecciones expuestas.

La batería global aprobó 208 pruebas JVM, lint, la APK `debug` y la APK de
instrumentación `qa`. La evidencia completa está en
`docs/audits/2026-08-21-dominio-configuracion-y-horas-v2.md`.
