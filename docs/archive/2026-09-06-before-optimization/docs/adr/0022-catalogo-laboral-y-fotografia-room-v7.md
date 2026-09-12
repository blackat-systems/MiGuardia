# ADR 0022: catálogo laboral y fotografía histórica en Room v7

- Estado: aceptada para catálogo y fotografías V2; adopción V1 reemplazada por
  ADR 0024
- Fecha: 2026-08-22

> Actualización 2026-08-23: permanecen el catálogo separado y la fotografía de
> cada jornada V2. Se retiran del producto final la adopción de objetivos u
> horarios V1 y la obligación de preservar una base 1.0.

## Contexto

Room v6 conserva una única línea temporal de configuración, pero sus diecisiete
tablas todavía no representan los conceptos separados que necesita MiGuardia
2.0: lugar laboral, tipo de trabajo, plantilla horaria, reglas vigentes por
lugar y la fotografía laboral de cada jornada.

Reutilizar `Objective` como catálogo completo mezclaría el lugar físico con el
comportamiento del trabajo. Reutilizar `ScheduleCombination` como plantilla V2
impediría que dos tipos distintos compartan lugar y horario. Guardar sólo los
identificadores actuales tampoco preservaría el nombre y comportamiento que la
persona confirmó al crear una jornada.

## Decisión

Room v7 agrega exactamente cinco tablas y conserva sin cambios las diecisiete
tablas y los esquemas anteriores:

1. `work_places` vincula una línea temporal y sector con un `Objective` sin
   copiar su identidad;
2. `work_types` conserva nombre visible, clave canónica NFKC, comportamiento y
   estado de archivo;
3. `work_templates` combina lugar, tipo, intervalo exacto y color; una
   procedencia V1 opcional usa `SET NULL` si desaparece el horario heredado;
   una jornada que ya fotografió ese UUID lo conserva como antecedente y no se
   obliga a igualarlo con el vínculo actual nullable de la plantilla;
4. `workplace_rule_revisions` conserva reglas insert-only desde una fecha local
   exacta;
5. `shift_work_snapshots` forma un par obligatorio en las APIs V2 con `Shift` y
   guarda sector, revisión de configuración, lugar, tipo, plantilla, nombre y
   comportamiento históricos.

Las relaciones de configuración, catálogo e historia usan `RESTRICT`. El único
`CASCADE` nuevo pertenece a `Shift → shift_work_snapshots`: al borrar una
jornada se elimina su fotografía, pero nunca su catálogo. Las claves compuestas
impiden cruzar línea temporal, sector, lugar, objetivo, tipo o plantilla. La
unicidad funcional diferencia dos tipos en el mismo intervalo y rechaza sólo la
misma combinación exacta.

`MIGRATION_6_7` crea las cinco tablas vacías. No adopta objetivos, no convierte
horarios V1, no asigna tipos, no crea reglas, no activa V2 ni modifica una fila
v1–v6. Una adopción V1 posterior es explícita, idempotente y conserva intactos
`Objective`, `ScheduleCombination` y las jornadas existentes.

Los repositorios validan toda escritura dentro de la transacción y auditan
también filas externas que las relaciones normales podrían ocultar. Cada
jornada insertada o actualizada por un lote V2 posee una fotografía obligatoria.
Las APIs heredadas no pueden actualizar sólo la fila `Shift` de un par V2 ni
aplicar sobre él cambios estructurales de Novedades; un cambio de estado sí se
admite cuando conserva todos los demás campos.
Un reemplazo confirmado puede borrar jornadas V1 o V2 en las fechas elegidas,
conservando la semántica histórica de la carga manual y la atomicidad del lote.

## Consecuencias

- una instalación migrada puede seguir en modo V1 con catálogo vacío;
- una instalación nueva no recibe lugares, tipos, horarios ni reglas
  predeterminados;
- archivar lugar, tipo o plantilla evita nuevas selecciones sin reescribir
  jornadas anteriores;
- editar una plantilla no puede cambiar lugar o tipo, porque eso sería cambiar
  su identidad;
- una jornada que cruza medianoche puede resolver una revisión de reglas por
  cada fecha civil sin cambiar su día de pertenencia;
- Room v7 queda preparado para el recorrido visible del Corte B, pero este ADR
  no habilita todavía navegación, Compose ni carga manual desde la aplicación.

## Alternativas descartadas

### Convertir automáticamente datos V1 durante la migración

No existe evidencia suficiente para asignar un tipo o reglas V2 a cada objetivo
histórico. La migración debe preservar, no interpretar.

### Una plantilla por lugar y horario

Mezclaría guardia, consultorio u otros tipos activos que pueden compartir el
mismo intervalo. La identidad incluye el tipo.

### Copiar reglas dentro de cada jornada

Las reglas se resuelven por fecha civil y pueden tener una revisión futura. La
jornada conserva sus identidades históricas; duplicar reglas produciría dos
fuentes de verdad.

### Permitir borrado en cascada del catálogo

Eliminaría contexto requerido por fotografías históricas. El catálogo se
archiva y las relaciones históricas se protegen con `RESTRICT`.

## Verificación requerida

- migración directa `6→7` y cadena completa `1→2→3→4→5→6→7` con datos
  históricos preservados;
- base nueva v7 con 22 tablas y catálogo vacío;
- esquemas `1.json` a `6.json` byte a byte idénticos y sólo `7.json` nuevo;
- claves foráneas, unicidades, `foreign_key_check`, rollback y reapertura;
- creación, adopción, archivo y reglas del catálogo;
- escritura, edición, lote, borrado y corrupción controlada del par
  jornada–fotografía;
- prueba física del módulo de base de datos antes del checkpoint local.
