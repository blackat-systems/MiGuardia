# ADR 0018: motor de horas configurable de MiGuardia 2.0

- Estado: reemplazada por ADR 0020; conservar como antecedente
- Fecha: 2026-08-20

## Contexto

Actualización del 2026-08-21: la fórmula que convertía automáticamente el
exceso sobre una base en horas extra, las extras sin horario exacto y la base
exclusivamente mensual fueron descartadas. El contrato vigente está en ADR
0020. Este archivo no autoriza implementación.

Revisión del 2026-08-21: Joaquin informó que las reglas administrativas de
Policía, Enfermería y Medicina todavía se están relevando mediante formularios.
Por eso este ADR conserva un diseño matemático candidato, pero no constituye el
motor definitivo de esos sectores.

Permanecen confirmadas la separación de horas regulares y adicionales, que una
cobertura completa no sea extra por sí sola y la regla de que el trabajo activo
reemplaza el tramo pasivo superpuesto. Las fórmulas, defaults y su aplicación a
cada sector deben volver a aprobarse después del relevamiento.

El motor 1.0 fija 204 horas y clasifica como extra el excedente mensual. Esa
regla sirve para la especialización original, pero no representa a trabajadores
que desconocen su base, no tienen base fija o trabajan como retén/cubrefranco.
También se necesita registrar tiempo extra ocurrido en días concretos sin
mezclarlo con las horas regulares.

## Diseño candidato

- El usuario mantiene una sola configuración laboral.
- La base mensual puede estar definida, ser desconocida o no aplicar.
- El modo de exceso mensual sólo puede estar habilitado con una base definida;
  con base desconocida o no aplicable debe persistirse deshabilitado.
- Las jornadas cubiertas se acumulan como horas regulares.
- Retén, cubrefranco o modalidad indefinida nunca convierte automáticamente una
  cobertura completa en hora extra.
- El tiempo extra informado se separa de las horas regulares, queda vinculado a
  un día y, cuando sea posible, a un intervalo exacto.
- La guardia pasiva se registra como una ventana de disponibilidad y se informa
  separada de las horas activas.
- Cuando aparece trabajo activo dentro de una guardia pasiva, el tramo activo
  reemplaza al tramo pasivo superpuesto. Ese minuto se contabiliza una sola vez:
  como activo regular por defecto o como extra informada si el usuario lo marca
  expresamente.
- Una intervención activa dentro de una pasiva siempre requiere inicio y fin
  exactos para poder descontar correctamente la disponibilidad reemplazada.
- Una ventana pasiva persiste `PLANNED`, `CANCELLED` o `ABSENT`; completada/en
  curso se deriva del reloj y no se guarda. Sólo una ventana `PLANNED` cuya fecha
  local de inicio no esté excluida por carpeta médica o vacaciones integra `P0`.
  `CANCELLED`, `ABSENT`, carpeta médica y vacaciones producen `P=0` y `Pp=0`
  sin borrar la ventana.
- Los intervalos se tratan como semiabiertos y en minutos exactos. Para descontar
  actividad de una pasiva se usa la unión temporal de todo el trabajo activo, de
  modo que un tramo coincidente se resta una sola vez.
- Dos jornadas regulares explícitas que se superponen conservan la semántica de
  MiGuardia 1.0: se registran y suman independientemente después de una
  advertencia concreta. No se fusionan ni corrigen silenciosamente.
- Una extra informada no puede superponerse con otra extra ni con una jornada
  activa de la misma configuración. La interfaz exige corregir el intervalo o
  elegir una única clasificación antes de guardar.
- Dos ventanas pasivas `PLANNED` no pueden superponerse. Los límites contiguos sí
  son válidos bajo la semántica `[inicio, fin)`; una ventana `CANCELLED` o
  `ABSENT` no bloquea otra.
- Con base definida y cálculo mensual habilitado:

```text
R = horas regulares trabajadas
D = horas extra informadas
B = base mensual
M = máximo(R - B, 0)
E = D + M
F = máximo(B - R, 0)
```

- Con base desconocida o no aplicable, `M` y `F` no se calculan; `E = D`.
- Con base definida y cálculo mensual deshabilitado tampoco se calculan `M` ni
  `F`; `B` queda como referencia visible y `E = D`.
- `R + D` puede exponerse como tiempo total realizado, pero no reemplaza las
  categorías separadas.
- Nocturnidad y feriado son atributos superpuestos y no agregan tiempo.
- Los resultados son informativos y no constituyen interpretación legal,
  liquidación salarial ni recibo.

La normalización V2 usa un `Clock` inyectable y distingue tiempo transcurrido de
tiempo pendiente. `Craw` es el instante real del reloj y `Cmin` es ese instante
truncado hacia abajo al minuto; las fórmulas usan `Cmin` para producir minutos
enteros y las validaciones de trabajo realizado usan `Craw`:

```text
AR = intervalos de jornadas activas regulares elegibles
AD = intervalos exactos informados como extra, validados sin superposición
DU = duraciones extra sin horario exacto
P0   = ventanas pasivas elegibles
Craw = Clock.instant()
Cmin = floorToMinute(Craw)

R = suma de duración(AR ∩ (-∞, Cmin))
D = suma de duración(AD ∩ (-∞, Cmin)) + suma(DU)
A_realizada = unión((AR ∪ AD) ∩ (-∞, Cmin))
A_futura    = unión(AR ∩ [Cmin, +∞))

P  = duración(unión(P0 ∩ (-∞, Cmin)) - A_realizada)
X  = duración(unión(P0 ∩ (-∞, Cmin)) ∩ A_realizada)
Pp = duración(unión(P0 ∩ [Cmin, +∞)) - A_futura)
Xp = duración(unión(P0 ∩ [Cmin, +∞)) ∩ A_futura)
P_proyectada = P + Pp
Tiempo activo total = R + D
```

`DU` nunca forma parte de `A_realizada`: sin ubicación temporal no puede
descontar una pasiva ni clasificarse como nocturna o feriada. Una extra informada
representa trabajo ya realizado: un intervalo `AD` debe finalizar como máximo en
`Craw`, y una `DU` debe atribuirse a una fecha local no futura. `P` es la
disponibilidad efectiva ya transcurrida;
una pasiva futura aporta cero a `P` y aparece únicamente como pendiente o
proyectada. `P`, `Pp` y `P_proyectada` no se incorporan a `R`, `D`, `M`, `E` ni
`F`.

Una intervención activa regular se persiste una sola vez como una `Shift`
ordinaria y conserva una asociación durable con la ventana pasiva. No se crea un
segundo intervalo regular paralelo. Una intervención marcada como extra se
persiste una sola vez en la fuente de `D` exacta y se vincula a la pasiva; esta
intervención dentro de pasiva nunca usa `DU`.
Toda carga representa exactamente una de `Shift`, `AD` o `DU`; nunca combina dos
fuentes. Una extra independiente puede convertirse entre `AD` y `DU` mediante
reemplazo atómico, no duplicación; una `AD` vinculada a pasiva no puede perder su
horario y convertirse en `DU`.

Cuando la comparación mensual está habilitada, la atribución de `M` a fuentes y
días es determinista: se ordenan los slices elegibles de `R` por `startAt`,
`endAt` e identificador estable; se acumulan sus minutos y sólo la porción que
queda después de alcanzar `B` se clasifica como `M`. Las superposiciones
regulares siguen siendo slices independientes, como en 1.0.

`AR` conserva las precedencias heredadas de `Shift`. En cambio, `AD` y `DU` son
trabajo realizado declarado explícitamente y cuentan aun si la fecha posee
vacaciones o carpeta médica; la interfaz advierte la contradicción y exige
confirmación antes de guardar, sin borrar ninguno de los registros.

## Compatibilidad

Los usuarios migrados desde 1.0 conservan Vigilancia privada, base definida de
204 horas, cálculo por excedente mensual y resultados históricos. La semántica
legada se conserva para los meses cerrados de 1.0. La normalización V2 se aplica
desde una vigencia mensual explícita y nunca reinterpreta silenciosamente
jornadas históricas.

## Consecuencias

- La persistencia futura debe representar configuración mensual e intervalos de
  extra sin almacenar totales opacos.
- Resumen y calendario deben identificar los días con extras informadas y con
  exceso mensual derivado.
- Las ventanas pasivas y sus intervenciones activas deben conservarse como datos
  fuente; sus duraciones efectivas son resultados derivados y recalculables.
- El motor requiere reloj inyectable y separa pasiva efectiva, reemplazada,
  pendiente y proyectada.
- El corte temporal se normaliza al minuto y nunca produce segundos o fracciones
  dentro de los totales.
- Las superposiciones entre jornadas regulares conservan su suma independiente
  y su advertencia. La unión temporal se usa únicamente para no descontar dos
  veces el mismo minuto de una pasiva.
- La interfaz debe explicar una superposición y permitir corregirla, sin borrar
  ni fusionar silenciosamente los registros originales.
