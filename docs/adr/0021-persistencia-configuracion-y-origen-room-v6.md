# ADR 0021: persistencia de configuración y origen en Room v6

- Estado: aceptada
- Fecha: 2026-08-21

## Contexto

Room v5 contiene trece familias históricas de MiGuardia 1.0. El dominio puro de
2.0 ya representa sector, cambios desde una fecha y referencias de horas, pero
todavía no distingue dos estados que visualmente pueden parecer iguales:

- una instalación nueva que aún debe elegir sector;
- una instalación migrada que debe seguir usando el motor V1 hasta activar V2
  conscientemente.

Inferir ese origen por la existencia de objetivos o guardias sería incorrecto:
una persona podía haber usado 1.0 con la base vacía. Tampoco corresponde sembrar
204 horas, una franja nocturna u otra regla histórica como configuración V2.

## Decisión

Room v6 agrega cuatro tablas y no modifica ninguna tabla v5:

1. `work_configuration_roots` identifica la única línea temporal y su origen;
2. `per_period_hours_definitions` conserva el patrón inmutable de cada
   referencia informada período por período;
3. `work_configuration_revisions` conserva sector, referencia y disponibilidad
   vigentes desde una fecha;
4. `per_period_hours_values` conserva cada valor informado y su ventana exacta.

La raíz posee un `singletonSlot` único. El DAO y el repositorio sólo aceptan el
slot `1`, comprueban que no existan otras raíces y nunca exponen una operación de
borrado. Las nuevas relaciones usan `RESTRICT`: no se borra historia en cascada.

`MIGRATION_5_6` crea las cuatro tablas e inserta exactamente una raíz con origen
`MIGRATED_V1`, un UUID reservado y cero revisiones. No inserta sector,
referencia, disponibilidad, nocturnidad ni ninguna otra regla. Antes de la
primera revisión V2, esa raíz ordena mantener la semántica heredada.

Una base creada directamente en v6 queda sin raíz. Al elegir sector, la
aplicación guardará en una transacción una raíz `NEW_V2` y su primera revisión.
Como la apertura inicial no debe obligar a decidir todavía una fórmula de horas,
el dominio agrega el estado interno `PendingSetup`, distinto de:

- `NotUsed`: la persona decidió que no usa referencia;
- `Unknown`: la referencia existe, pero la persona no conoce el valor.

`PendingSetup` nunca equivale a cero y desaparece cuando la persona configura o
descarta conscientemente la referencia.

Los códigos persistidos poseen codecs explícitos y estables. No se guardan los
textos visibles ni se depende de `Enum.name`. Fechas y horas continúan en ISO,
UUID como texto y minutos como enteros positivos.

## Límites de v6

No se persisten todavía tipos de trabajo, clases extra ni reglas por lugar.
Aunque existan modelos puros, faltan sus contratos de pertenencia, archivado y
relación histórica con `Objective`. Tampoco se guardan totales, progreso,
ventanas calculables, vocabulario sugerido, preferencias visuales ni montos.

`Objective`, `ScheduleCombination` y `Shift` permanecen byte a byte con su
estructura v5. Las abreviaturas históricas de dos letras continúan siendo
válidas y sus referencias sin clave foránea no se reinterpretan.

## Consecuencias

- una actualización conserva la historia y puede seguir funcionando en modo
  V1 sin quedar bloqueada por onboarding;
- una instalación nueva no recibe reglas laborales inventadas;
- la primera revisión V2 activa el nuevo comportamiento desde su `LocalDate` y
  no reescribe fechas anteriores;
- cambiar el patrón de una definición por período requiere un UUID nuevo;
- las revisiones y definiciones son insert-only; una corrección de valor usa una
  actualización explícita del mismo identificador;
- los esquemas v1–v5 permanecen inmutables y se agrega `6.json`.

## Alternativas descartadas

### Inferir el origen por datos existentes

No distingue una instalación V1 vacía de una instalación nueva y mezcla estado
de producto con contenido laboral.

### Sembrar una configuración de Vigilancia con 204 horas

Cambiaría una compatibilidad histórica por una afirmación V2 que la persona no
eligió y no preservaría preferencias particulares.

### Reutilizar `NotUsed` o `Unknown` como estado inicial

Ambos expresan decisiones reales. Usarlos para “todavía no se preguntó” haría
que la interfaz y el Resumen afirmaran algo falso.

### Persistir todo el dominio en v6

Congelaría prematuramente relaciones de tipos, extras y reglas por lugar que
pertenecen a bloques posteriores.

## Verificación requerida

- migración directa `5→6` con filas en las trece familias históricas;
- cadena completa `1→2→3→4→5→6`;
- base nueva v6 sin raíz ni valores predeterminados;
- raíz migrada sin revisiones y activación V2 fechada;
- round-trip de todos los tipos de referencia, incluido `PendingSetup`;
- restricciones, rollback, reapertura y `foreign_key_check`;
- esquema `6.json` nuevo sin modificar los cinco esquemas anteriores.

La estrategia sigue la guía oficial de migraciones y pruebas de Room:
https://developer.android.com/training/data-storage/room/migrating-db-versions
