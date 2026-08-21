# Planificación canónica de MiGuardia 2.0

- Estado: cerrada para traspaso a MAIN
- Fecha de inicio: 2026-08-20
- Fecha de cierre: 2026-08-20
- Propietario del producto: Joaquin
- Base: MiGuardia `v1.0.0`

## 1. Propósito

MiGuardia 2.0 amplía la aplicación Android local de vigilancia privada para que
pueda ser útil también en Policía, Salud y otros trabajos con jornadas variables,
sin destruir ni reinterpretar el historial de quienes ya usan MiGuardia 1.0.

Continúan vigentes:

- Android como única plataforma inicial;
- calendario mensual como centro de la experiencia;
- carga manual y de baja fricción;
- datos locales, sin cuenta, nube, analítica ni sincronización;
- identidad visual Vigilia;
- capacidades estables de 1.0 mientras no sean reemplazadas por una decisión
  explícita, una migración no destructiva y pruebas suficientes.

## 2. Decisiones vinculantes de continuidad

### Aplicación y datos

- MiGuardia 2.0 actualiza `com.blackatsystems.miguardia`; no es una segunda app.
- El tag `v1.0.0` y la rama `main` permanecen protegidos.
- Room parte de v5, trece entidades y las migraciones explícitas existentes.
- DataStore, SharedPreferences, fotos privadas y cachés locales se preservan.
- Los datos históricos mantienen la semántica con la que fueron creados.
- No se recupera código desde worktrees históricos como sustituto de la base
  sellada.

### Configuración laboral única

- Cada usuario dispone de una sola configuración laboral.
- No existen perfiles o empleos simultáneos dentro de MiGuardia.
- Los cambios en esa configuración forman revisiones históricas, no perfiles
  múltiples.
- Toda revisión creada por el usuario tiene un mes de inicio no nulo y rige
  desde su primer día hasta la siguiente.
- Sólo la raíz migrada de 1.0 carece de mes de inicio. Existe una sola raíz en
  una base migrada y ninguna en una instalación nueva.
- Sólo existe una revisión efectiva por mes.
- El mes actual puede recibir una revisión únicamente si todavía no contiene
  datos laborales; en caso contrario la vigencia mínima es el mes siguiente.
- Una revisión nunca muta guardias ni configuraciones anteriores.

Un mes contiene datos sensibles a reglas cuando existe al menos una `Shift` de
cualquier estado iniciada en ese mes, una ventana pasiva iniciada allí o una
extra atribuida al mes. No bloquean por sí solos plantillas, `F`, `?`, feriados,
vacaciones, carpetas médicas sin jornada, notas, fotos ni preferencias.

Para resolver un mes se elige la revisión no raíz más reciente cuyo inicio sea
menor o igual al mes consultado; si no existe, se usa la raíz migrada. Sin raíz
ni revisión aplicable, la configuración está `sin configurar`.

El modo de exceso mensual sólo puede estar habilitado cuando la base está
definida con minutos positivos. Una base desconocida o no aplicable conserva
valor ausente y modo obligatoriamente deshabilitado.

Una política nocturna de revisión puede estar `definida` con inicio y fin
distintos, en minutos exactos y admitiendo cruce de medianoche, o `deshabilitada`.
Sólo la raíz migrada recibe automáticamente 21:00–06:00. Una instalación nueva
comienza deshabilitada hasta una elección explícita; la regla clasifica `R` y
`D`, nunca `P` por inferencia.

La disponibilidad pasiva se habilita mediante el booleano versionado
`passiveEnabled` de la revisión laboral. En la configuración inicial, Salud lo
propone habilitado y Vigilancia, Policía y Otro lo proponen deshabilitado; el
usuario confirma o cambia esa propuesta. La raíz migrada de 1.0 queda
deshabilitada porque no existe
historial pasivo inferible. Deshabilitarla impide crear nuevas ventanas bajo esa
vigencia, pero nunca oculta ni borra registros de vigencias anteriores.

## 3. Vocabulario multiprofesional

### Conceptos comunes

MiGuardia utiliza como conceptos neutrales:

- sector laboral;
- lugar o servicio;
- horario;
- jornada;
- trabajo activo regular;
- horas extra informadas;
- guardia pasiva;
- feriado, vacaciones, carpeta médica y novedad.

La interfaz adapta las etiquetas sin duplicar entidades ni motores:

| Concepto común | Vigilancia privada | Policía | Salud | Otro |
|---|---|---|---|---|
| Lugar o servicio | Objetivo | Dependencia o servicio | Institución o servicio | Lugar o servicio |
| Trabajo activo | Guardia | Servicio | Guardia activa o jornada | Jornada |
| Etiqueta de tarea | Puesto | Función o destino | Servicio, sala o función | Función |
| Empleador | Empresa | Institución | Institución | Empleador o institución |

La abreviatura, dirección, notas, horarios, colores e instantáneas históricas
continúan perteneciendo al mismo concepto heredado de objetivo/lugar. Cambiar la
etiqueta visible no modifica esos datos.

La configuración puede guardar una profesión o función opcional —por ejemplo
Medicina o Enfermería dentro de Salud— sin solicitar matrícula, jerarquía, DNI,
correo, teléfono ni otros datos sensibles.

### Tipos de jornada

- `Activa regular`: trabajo realizado que integra `R`.
- `Extra informada`: trabajo que el usuario clasifica expresamente como `D`.
- `Pasiva`: disponibilidad que integra `P` sólo mientras no existe actividad en
  el mismo tramo.

Salud propone guardia activa/pasiva de manera visible. En Policía, Vigilancia y
Otro, la configuración propone la pasiva deshabilitada, pero el usuario puede
habilitarla si su realidad laboral la necesita. La elección queda persistida en
la revisión efectiva; el sector no impone por sí solo una base, convenio,
legalidad ni remuneración.

## 4. Contrato del motor de horas

### 4.1 Conceptos separados

- **Horas planificadas:** duración de jornadas programadas.
- **Horas regulares trabajadas (`R`):** parte transcurrida de las jornadas
  activas regulares elegibles.
- **Horas extra informadas (`D`):** tiempo adicional declarado expresamente y
  conservado separado de `R`.
- **Disponibilidad pasiva bruta (`P0`):** ventanas en las que el usuario debía
  permanecer disponible.
- **Guardia pasiva efectiva (`P`):** parte ya transcurrida de `P0` en la que no
  hubo trabajo activo.
- **Pasiva pendiente (`Pp`):** parte futura de `P0` que se proyecta pasiva después
  de descontar jornadas activas ya programadas.
- **Base mensual (`B`):** referencia mensual opcional, nunca una afirmación
  legal o salarial.
- **Tiempo activo total (`T`):** `R + D`.

Todos los intervalos exactos usan minutos enteros, instantes reales, un `Clock`
inyectable y semántica semiabierta `[inicio, fin)`. `Craw` es el instante real
del reloj y `Cmin` es `Craw` truncado hacia abajo al minuto; las fórmulas usan
`Cmin` y las validaciones de trabajo ya realizado usan `Craw`. No se usa `Double`
para duraciones.

### 4.2 Trabajo activo dentro de una guardia pasiva

Decisión de Joaquin del 2026-08-20:

> Si aparece trabajo durante una guardia pasiva, el tramo correspondiente deja
> de ser pasivo y se reemplaza por horas activas.

Contrato matemático:

```text
AR = intervalos de jornadas activas regulares elegibles
AD = intervalos exactos informados como extra
DU   = duraciones extra sin horario exacto
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
T = R + D
```

Consecuencias:

- cada minuto coincidente se descuenta una sola vez de `P`;
- el trabajo dentro de una pasiva es `R` por defecto;
- sólo pasa a `D` si el usuario lo declara expresamente como extra;
- estar dentro de una pasiva nunca lo convierte automáticamente en extra;
- una intervención dentro de una pasiva requiere inicio y fin exactos;
- actividad anterior o posterior a la ventana pasiva se contabiliza normalmente,
  pero no descuenta `P`;
- si la actividad cubre toda la ventana, `P = 0`;
- una pasiva futura aporta cero a `P` y sólo aparece en `Pp`/proyección;
- una duración `DU` no forma parte de `A_realizada`, no descuenta pasiva y no
  clasifica nocturnidad o feriado;
- una extra exacta debe finalizar como máximo en `Craw`; una duración `DU` debe
  atribuirse a una fecha local no futura;
- `P`, `Pp` y la proyección nunca entran en `T`, `B`, `M`, `E` ni `F`.

Resumen muestra `P efectiva transcurrida` como cifra principal y detalla `P0
transcurrida`, `X reemplazada por actividad`, `Pp pendiente` y `Xp ya cubierta
por actividad programada`.

Una intervención regular se guarda exactamente una vez como `Shift` y conserva
una asociación con la ventana pasiva; no crea otro intervalo regular. Una
intervención extra dentro de una pasiva se guarda exactamente una vez como `AD`
vinculada a esa pasiva.

### 4.3 Superposiciones activas

- Dos jornadas regulares explícitas que se superponen conservan la semántica de
  1.0: se advierten con horarios concretos, se permiten tras confirmación y se
  suman independientemente.
- La unión temporal de esas jornadas se utiliza únicamente para no descontar dos
  veces el mismo minuto de una pasiva.
- Una extra exacta no puede superponerse con otra extra ni con una jornada activa
  de la misma configuración. La interfaz pide corregir el intervalo o elegir una
  sola clasificación antes de guardar.
- Dos ventanas pasivas `PLANNED` no pueden superponerse. Los límites contiguos
  sí son válidos; una ventana `CANCELLED` o `ABSENT` no bloquea otra.
- Nunca se fusionan, borran o corrigen registros originales silenciosamente.

### 4.4 Base mensual y exceso

La base tiene tres estados:

1. **Definida:** valor positivo conocido. Puede habilitar comparación y exceso.
2. **Desconocida:** podría existir, pero el usuario no conoce su valor.
3. **No aplicable:** el trabajo no utiliza una carga mensual fija.

`Desconocida` y `No aplicable` usan ausencia de valor, nunca cero.

Con base definida y cálculo mensual habilitado:

```text
M = máximo(R - B, 0)   // parte de R que excede la base
E = D + M              // extras informadas + exceso mensual derivado
F = máximo(B - R, 0)   // diferencia respecto de la base
T = R + D              // tiempo activo real
```

`M` es una clasificación de parte de `R` y no vuelve a sumarse dentro de `T`.
Las extras informadas `D` no completan `B` y permanecen vinculadas a su origen.

La atribución diaria de `M` es determinista: se ordenan los slices elegibles de
`R` por instante de inicio, instante de fin e identificador estable; se acumulan
sus minutos y sólo la porción posterior a alcanzar `B` se clasifica como `M`.
Las jornadas regulares superpuestas conservan slices independientes.

Con base definida pero cálculo mensual deshabilitado, `B` queda visible sólo
como referencia: no existen `M` ni `F` y `E = D`. Con base desconocida o no
aplicable rige la misma ausencia de `M/F` y `E = D`.

### 4.5 Retén, cubrefranco e indefinido

- Una guardia cubierta completa es regular.
- Trabajar más días no convierte por sí solo una cobertura en extra.
- Con base desconocida o no aplicable no se infiere exceso mensual.
- Sólo el tiempo adicional fuera de la jornada puede registrarse expresamente
  como extra.

```text
Guardia cubierta 07:00–19:00 = 12 h R
Extensión informada 19:00–21:00 = 2 h D
T = 14 h
```

### 4.6 Carga de extras e intervenciones

Desde el detalle de un día o jornada se ofrece `Registrar tiempo adicional`.

- La opción rápida propone `Antes de la jornada`, `Después de la jornada` u
  `Otro horario`.
- Fecha y origen son obligatorios.
- Inicio y fin exactos son la opción predeterminada y permiten nocturnidad,
  feriado, validación de solapamiento y vínculo con una pasiva.
- Como alternativa explícita, `No conozco el horario exacto` permite guardar sólo
  una duración `DU` en una fecha no futura. Integra `D`, pero no puede descontar
  pasiva ni clasificarse como nocturna o feriada, y la interfaz debe explicarlo.
- `Registrar intervención activa` desde una pasiva siempre exige intervalo real,
  propone `Activa regular` y permite cambiar conscientemente a
  `Extra informada`.
- La opción regular crea una sola `Shift` vinculada a la pasiva. La opción extra
  crea un solo intervalo `AD` vinculado. Nunca se crean ambas fuentes para la
  misma intervención.
- Toda carga representa exactamente una de `Shift`, `AD` o `DU`. Una extra
  independiente puede convertirse `AD↔DU` mediante reemplazo atómico, nunca por
  duplicación; una `AD` vinculada a pasiva no puede convertirse en `DU`.
- Las antiguas novedades `ADDITIONAL_TIME` continúan siendo notas informativas.
  No se migran a `D` porque carecen de duración o intervalo verificable.

### 4.7 Mes, tiempo y atributos superpuestos

- Cada jornada o intervalo pertenece al mes de su propia fecha local de inicio.
- Una ventana pasiva pertenece al mes en que comienza; una intervención hija
  pertenece al mes en que ella comienza, aunque cruce desde una pasiva del mes
  anterior.
- La intervención conserva el vínculo necesario para descontar la pasiva padre
  en el mes correspondiente.
- Nocturnidad y feriado inspeccionan los instantes reales de `R` y `D`; pueden
  cruzar medianoche y no agregan tiempo.
- No se infieren efectos monetarios o legales de nocturnidad/feriado sobre `P`.
- Una ventana pasiva persiste `PLANNED`, `CANCELLED` o `ABSENT`; sus estados en
  curso/completada se derivan con el `Clock` y no se persisten.
- Sólo una pasiva `PLANNED` cuya fecha local de inicio no esté cubierta por
  carpeta médica o vacaciones integra `P0`. `CANCELLED`, `ABSENT`, carpeta
  médica y vacaciones producen `P=0` y `Pp=0` sin borrar la ventana.
- Una intervención activa conserva su propia fuente y precedencia; no convierte
  automáticamente en elegible una pasiva excluida.
- `AR` conserva las exclusiones heredadas. `AD` y `DU`, por ser trabajo realizado
  declarado explícitamente, sí integran `D` aunque la fecha tenga vacaciones o
  carpeta médica; la interfaz advierte la contradicción y exige confirmación.
- Una jornada pasada `PLANNED` continúa proyectándose como completada cuando su
  fin real ya ocurrió.

### 4.8 Matriz mínima de resultados

| Caso | P | Pp | R | D | Resultado adicional |
|---|---:|---:|---:|---:|---|
| `Cmin=18`, pasiva futura 20–08 | 0 | 12 h | 0 | 0 | todavía no estuvo pasivo |
| `Cmin=18`, pasiva 20–08 y activa programada 22–02 | 0 | 8 h | 0 | 0 | `Xp=4 h` prevista |
| `Cmin=02`, pasiva 20–08, activa 22–00 | 4 h | 6 h | 2 h | 0 | `X=2 h` transcurridas |
| Pasiva cancelada o ausente 20–08 | 0 | 0 | 0 | 0 | se conserva, pero no integra `P0` |
| Pasiva finalizada 20–08, sin actividad | 12 h | 0 | 0 | 0 | `X=0` |
| Pasiva finalizada 20–08, activa regular 22–02 | 8 h | 0 | 4 h | 0 | `X=4 h` |
| Pasiva finalizada 20–08, activa extra 22–02 | 8 h | 0 | 0 | 4 h | `X=4 h` |
| Pasiva 20–08, activa 18–22 | 10 h | 0 | 4 h | 0 | sólo 20–22 reemplaza P |
| Pasiva 20–08, R 22–23 y D 01–03 | 9 h | 0 | 1 h | 2 h | `X=3 h` |
| Pasiva 20–08, activa 08–10 | 12 h | 0 | 2 h | 0 | actividad fuera de P |
| Pasiva 20–08, activa 18–10 | 0 | 0 | 16 h | 0 | toda P reemplazada |
| `B=204`, modo activo, `R=210`, `D=2` | separada | separada | 210 h | 2 h | `M=6`, `E=8`, `T=212` |
| `B=204`, modo activo, `R=200`, `D=8` | separada | separada | 200 h | 8 h | `F=4`, `M=0`, `T=208` |
| `B=204`, modo deshabilitado, `R=210`, `D=2` | separada | separada | 210 h | 2 h | sin `M/F`, `E=2` |
| Base desconocida, `R=210`, `D=2` | separada | separada | 210 h | 2 h | sin `M/F`, `E=2` |

## 5. Persistencia y migración no destructiva

### 5.1 Fuentes heredadas

- Room `miguardia.db` v5 conserva trece familias relacionales.
- `guard_profile.preferences_pb` conserva nombre/apodo y empresa.
- DataStore de remuneración, notificaciones y clima continúa independiente.
- Preferencias de tema y zoom interno continúan en su almacenamiento actual.
- Fotos de cronograma y caché meteorológica permanecen en archivos privados.

No se copian nombre ni empresa a Room y no se crea una falsa transacción entre
Room y DataStore.

En el modelo V2, empleador/institución es opcional: clave `company` ausente
significa `no informado` y se proyecta como `null`; una cadena vacía o compuesta
sólo por espacios no es un valor válido. Antes de retirar el fallback global de
1.0, una actualización identificada por la raíz `MIGRATED_V1` materializa
`Inforce` si la clave faltaba. Una instalación nueva sin esa clave conserva
`null` y nunca hereda `Inforce` por defecto.

### 5.2 Configuración laboral — Room v6

El primer incremento persistente agrega una línea temporal de revisiones de la
única configuración laboral, sin `profileId`.

Cada revisión representa como mínimo:

- mes de vigencia nullable sólo para la raíz migrada;
- sector;
- profesión o función opcional;
- estado de base y minutos positivos cuando corresponda;
- modo de exceso mensual;
- versión de motor;
- política nocturna `definida(inicio, fin)` o `deshabilitada`;
- `passiveEnabled`;
- origen migrado o creado por el usuario;
- metadatos mínimos de creación.

La migración `5→6`:

1. crea sólo la estructura de revisiones;
2. no altera las trece tablas heredadas;
3. inserta una raíz sin límite inferior para usuarios 1.0:
   - Vigilancia privada;
   - base definida de 12.240 minutos;
   - exceso mensual habilitado;
   - nocturnidad 21:00 inclusive–06:00 exclusiva;
   - disponibilidad pasiva deshabilitada;
   - motor legado;
   - mes de inicio nulo y origen migración V1;
4. exporta el esquema v6 y conserva la cadena completa de migraciones;
5. falla de forma segura sin recrear ni borrar la base.

La unicidad de la raíz debe estar protegida por el esquema y el repositorio; no
puede depender de un `UNIQUE` simple sobre `effectiveFromMonth`, porque SQLite
admite múltiples `NULL`. Las pruebas intentan insertar una segunda raíz y exigen
rechazo.

La raíz continúa aplicando motor legado hasta que el usuario confirma su primera
revisión V2, aunque conserve los mismos valores. Esa confirmación crea la
revisión no raíz desde el primer mes permitido y activa el motor V2.

Una instalación nueva no crea raíz. `Cargar datos` abre primero una
configuración laboral mínima. Mientras siga `sin configurar`, se bloquean todos
los accesos de creación —Calendario, Objetivos/horarios, Perfil y accesos
directos—, no sólo ese botón. No permite crear objetivos, jornadas, pasivas o
extras hasta guardar una primera revisión V2 del mes actual. La política nocturna
comienza deshabilitada; la disponibilidad pasiva se propone habilitada para Salud
y deshabilitada para los demás sectores; empleador/institución es opcional.

En el DataStore de perfil, la presencia de la raíz con origen migración V1 es el
criterio durable para materializar la empresa implícita. Una única edición
atómica conserva una empresa explícita o escribe `Inforce` si faltaba, junto con
una marca idempotente. Si falla, no borra datos: muestra reintento. Una
instalación nueva nunca ejecuta esta compatibilidad.

Room v6, su migración y esa configuración mínima constituyen una sola puerta de
integración. MAIN puede desarrollarlas internamente por pasos, pero no integra
ni entrega un estado donde una instalación nueva quede bloqueada sin superficie
para configurarse.

La misma puerta incorpora un adaptador mínimo del motor y Resumen para jornadas
regulares: resuelve por mes sector, base, modo y nocturnidad; una raíz
`MIGRATED_V1` usa el cálculo legado y una revisión V2 usa la configuración
efectiva. Hasta que existan sus fuentes, `D` y `P` permanecen en cero y no se
ofrecen tarjetas ni acciones de carga, aunque `passiveEnabled=true`; SUVICO
continúa oculto fuera de Vigilancia. Así, una instalación nueva configurada puede
cargar `Shift` sin recibir 204 h o 21:00–06:00 por defecto.

### 5.3 Intervalos — incremento posterior

Después de integrar v6, un incremento separado agrega ventanas pasivas y fuentes
de extras junto con el consumo completo del motor. Es una única puerta que
incluye esquema/repositorio, carga UI y cálculo `R/D/P`; ninguna acción de AD,
DU o P0 se expone antes de que el motor pueda consumirla. Esa migración siguiente:

- no guarda totales mensuales opacos;
- conserva intervalos exactos, categoría, origen y vínculos;
- permite duración sin horario sólo para una extra independiente;
- no infiere pasivas ni extras desde guardias o novedades históricas;
- mantiene cada `Shift` legado como jornada regular;
- guarda una intervención regular una sola vez como `Shift` y la asocia a la
  pasiva, sin otro registro activo paralelo;
- guarda una intervención extra una sola vez como intervalo exacto vinculado;
- inicia `P=0` y `D=0` para el historial no cargado conscientemente.

### 5.4 Pruebas obligatorias de migración

- v5 con una fila representativa de cada una de las trece familias;
- v5 vacía y reapertura repetida sin duplicar la raíz;
- intento de segunda raíz rechazado por una restricción real, sin confiar en la
  unicidad nullable de SQLite;
- cadena completa `1→2→3→4→5→6` y migración siguiente;
- relaciones, índices, claves, instantáneas y conteos idénticos;
- conservación de todos los DataStore, preferencias, fotos y cachés;
- empresa explícita y `Inforce` implícita preservadas;
- instalación nueva sin clave de empresa proyectada como `null`, sin cadena
  vacía ni fallback `Inforce`;
- resumen histórico idéntico antes y después: 204 h, exceso, noche, feriado y
  cruce de mes;
- estados de base definida, desconocida y no aplicable;
- base definida con cálculo mensual habilitado y deshabilitado;
- política nocturna definida, deshabilitada, cruce de medianoche e inicio=fin
  inválido;
- `Clock` con segundos truncado hacia abajo al minuto;
- base desconocida/no aplicable con modo habilitado rechazada;
- disponibilidad pasiva versionada, sus propuestas por sector y registros
  históricos visibles después de deshabilitarla;
- dos pasivas `PLANNED` superpuestas rechazadas y límites contiguos admitidos;
- atribución determinista de `M` ante empates y jornadas regulares superpuestas;
- exclusividad `Shift`/`AD`/`DU`, conversión sin duplicación y `AD` vinculada sin
  conversión a duración no localizada;
- `AD/DU` bajo vacaciones o carpeta médica con advertencia y confirmación;
- cambio de sector diciembre→enero y vigencias consecutivas;
- instalación nueva sin raíz, configuración inicial y bloqueo previo de carga;
- activación V2 explícita para usuario migrado sin cambiar valores;
- fallo y reintento de materialización idempotente de `Inforce`;
- fallo de migración con rollback y datos intactos;
- actualización física aislada con paquete QA, nunca con producción.

## 6. Puerta U — Calendario adaptable

**Estado: cerrada, implementada y validada el 2026-08-20.**

La entrega:

- compacta primero la tarjeta del próximo evento y los espacios externos;
- conserva completa la grilla mensual;
- organiza los controles normales del mes en una fila al 100 %;
- habilita desplazamiento cuando la ventana no alcanza;
- comunica el desborde con una barra derecha persistente y proporcional;
- mantiene todo el contenido alcanzable al 100 %, 150 % y 200 %.

Evidencia:

- 175 pruebas JVM, sin fallos, errores ni omitidas;
- 172 instrumentadas físicas en Samsung `SM-S938B`, sin fallos, errores ni
  omitidas, incluidas las tres adaptativas;
- lint sin errores;
- `assembleDebug` y APK QA/AndroidTest aprobados;
- `git diff --check` correcto;
- sin cambios en Room, DataStore, Gradle, permisos, versión, Theme, densidad del
  sistema ni lógica funcional.

La evidencia durable está en
`docs/audits/2026-08-20-calendario-adaptable-2-0.md`. La Puerta U no se reabre
salvo regresión verificable.

## 7. Superficies que se conservan o adaptan

| Superficie | Decisión 2.0 |
|---|---|
| Calendario | Se conserva como inicio; adapta etiquetas sectoriales y marcadores sin agregar otra grilla |
| Lugares y horarios | Conserva entidades e instantáneas; adapta `Objetivo` al vocabulario del sector |
| Resumen | Muestra `R`, `D`, base aplicable y `P` efectiva cuando corresponda |
| Próximo evento | Conserva un único motor; aprende tipos activos/pasivos después del motor de horas |
| Notificaciones | Se conserva; adaptación sectorial posterior y sin ampliar permisos por inferencia |
| Clima | Se conserva opcional y separado de ubicación laboral automática |
| Fotos, notas, feriados, vacaciones | Se conservan con sus contratos de privacidad actuales |
| Remuneración SUVICO | Visible sólo en Vigilancia privada y vigencias documentadas; oculta en otros sectores |
| Informes | Backlog posterior; nunca atribuye reglas monetarias de un sector a otro |

## 8. Orden técnico por dependencias

```text
U. Calendario adaptable [CERRADA]
              ↓
0. Consolidación Git del traspaso
              ↓
MAIN. Puerta 0 de solo lectura
              ↓
1. Dominio de configuración y resolución por vigencia
              ↓
2. Room v6 + Perfil V2 + adaptador regular de motor/Resumen
              ↓
3. Fuentes AD/DU/P0 + motor completo, una sola puerta
              ↓
4. Resumen y Calendario multiprofesional completos
              ↓
5. Próximo evento, notificaciones, clima e informes
              ↓
6. Onboarding, ayuda y backlog 2.0 restante
```

No se modifica Room ni se crea una pantalla multiprofesional antes de que MAIN
audite el contrato de dominio. Cada incremento debe quedar ejecutable,
reversible y probado antes del siguiente.

## 9. Criterios de aceptación transversales

- actualización real desde v1.0.0 sin pérdida de ningún dato local;
- una sola configuración efectiva por mes y sin perfiles múltiples;
- resultados históricos 1.0 idénticos;
- intervalos exactos, medianoche, fin de mes/año y febrero bisiesto;
- pasiva reemplazada sólo en el tramo activo y sin doble descuento;
- activa regular dentro de pasiva por defecto; extra sólo por declaración;
- retén/cubrefranco siempre regular como jornada completa;
- base desconocida/no aplicable nunca tratada como cero;
- extras localizables por día y origen;
- SUVICO ausente fuera de Vigilancia privada;
- claro/oscuro, zoom interno 100/150/200 e interfaz alcanzable;
- sin lectura o modificación de ajustes visuales del sistema;
- pruebas JVM, migraciones, instrumentación QA y recorrido físico según impacto;
- ningún permiso, red, cuenta, nube, telemetría o dato sensible nuevo por
  inferencia.

## 10. Pendientes que no bloquean MAIN

Quedan fuera de los primeros incrementos y requieren puertas propias:

- reglas monetarias de Policía, Salud u Otro;
- prorrateos, pérdida de presentismo, descuentos, neto y vacaciones SUVICO;
- elección legal entre extra al 50 % o 100 % para cada caso;
- proveedor y alcance futuro del clima;
- monetización, distribución y funciones gratuitas/pagas;
- logo y tipografías definitivas;
- prioridad fina entre widgets, informes, copias/restauración y bloqueo local
  después del núcleo multiprofesional.

Estos asuntos no autorizan placeholders ni generalizaciones en los incrementos
de configuración y horas.

## 11. Cierre de PLANIFICACIÓN

PLANIFICACIÓN cierra porque:

- la Puerta U está implementada y validada;
- el vocabulario común y sectorial está definido;
- el contrato matemático de `R`, `D`, `P`, `B`, `M`, `E`, `F` y `T` está cerrado;
- la regla pasiva→activa de Joaquin está registrada;
- la migración no destructiva desde Room v5 y Perfil V1 está diseñada;
- el orden técnico y los criterios de aceptación están definidos;
- los pendientes no bloqueantes están separados;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md` constituye el traspaso formal a MAIN.
