# Reglas internas de configuración y períodos de horas V2

- Estado: **HABILITADO**
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
2. vocabulario visible de lugar y jornada por sector;
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
9. tipo de trabajo habitual personalizable;
10. clase extra con nombre, si ayuda al cumplimiento y si se muestra separada;
11. nombre visible de disponibilidad: guardia pasiva, disponible para llamado o
    retén;
12. reglas de lugar versionables para nocturnidad, fin de semana y visibilidad
    en Resumen, sin montos.

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

## DO NOT

- No implementar Room v6, DAO, repositorios ni migraciones.
- No modificar pantallas, navegación, perfil, Calendario o Resumen.
- No recuperar el candidato mensual desde Git, worktrees o memoria.
- No usar `YearMonth` como vigencia de configuración.
- No fijar 204 horas ni 21:00–06:00 para instalaciones nuevas.
- No crear extras automáticamente por superar una referencia.
- No permitir minutos negativos, cero donde se exige un valor o duraciones con
  segundos/fracciones.
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
- semana con cualquier `DayOfWeek`;
- ciclos de 14, 21 y 28 días, más rechazo de longitud inválida;
- anclaje de ciclo antes y después de la fecha consultada;
- clase extra que ayuda y que no ayuda al cumplimiento;
- regla nocturna deshabilitada y definida con cruce de medianoche;
- inicio nocturno igual a final rechazado;
- sábado, domingo, ambos o ninguno;
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
