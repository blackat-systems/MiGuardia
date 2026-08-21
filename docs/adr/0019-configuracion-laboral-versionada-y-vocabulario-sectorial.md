# ADR 0019: configuración laboral versionada y vocabulario sectorial

- Estado: aceptada
- Fecha: 2026-08-20

## Contexto

MiGuardia 1.0 posee un único perfil local. Su DataStore
`guard_profile.preferences_pb` guarda solamente nombre o apodo y empresa; la
profesión `Vigilancia y seguridad` es una constante de presentación. Room v5
contiene trece entidades de calendario y trabajo, pero no una configuración
laboral versionada.

MiGuardia 2.0 debe incorporar Vigilancia privada, Policía, Salud y Otro sin
crear perfiles simultáneos, duplicar el perfil existente ni reinterpretar meses
históricos. Sector, base mensual y reglas de cálculo necesitan vigencia porque
pueden cambiar con el tiempo.

## Decisión

### Una configuración, varias revisiones históricas

- Existe una sola configuración laboral por usuario.
- Sus cambios se representan como revisiones sucesivas de esa misma
  configuración; no son perfiles múltiples.
- Toda revisión creada por el usuario tiene `effectiveFromMonth` no nulo,
  comienza el primer día de ese mes y continúa hasta la revisión siguiente.
- Sólo la raíz migrada desde 1.0 usa `effectiveFromMonth = null`. Debe existir
  exactamente una raíz en una base migrada y ninguna en una instalación nueva.
- Sólo puede existir una revisión efectiva para un mes determinado.
- Un cambio de sector o de regla crea una revisión para el mes actual, si todavía
  no contiene datos laborales, o para un mes futuro. Nunca modifica una revisión
  anterior ni reescribe guardias.
- Si el mes actual ya contiene datos laborales, la vigencia mínima es el mes
  siguiente. La interfaz explica el motivo antes de guardar.
- Meses cerrados anteriores no se reconfiguran desde la interfaz normal.

`El mes contiene datos laborales` significa que existe al menos una `Shift` de
cualquier estado cuya fecha local de inicio pertenece al mes, una ventana pasiva
que comienza en el mes o una extra informada atribuida al mes. No bloquean por sí
solos objetivos/horarios de plantilla, `F`, `?`, feriados, vacaciones, carpetas
médicas sin jornada, notas, fotos ni preferencias.

La resolución es unívoca: se elige la revisión no raíz más reciente cuyo
`effectiveFromMonth` sea menor o igual al mes consultado; si no existe, se usa la
raíz migrada. Sin raíz ni revisión aplicable, el estado es `sin configurar`.

El modo de exceso mensual sólo puede habilitarse con base definida y minutos
positivos. Base desconocida o no aplicable exige valor ausente y modo
deshabilitado.

### Vocabulario común y presentación por sector

El dominio utiliza conceptos neutrales y la interfaz adapta las etiquetas:

| Concepto común | Vigilancia privada | Policía | Salud | Otro |
|---|---|---|---|---|
| Lugar o servicio | Objetivo | Dependencia o servicio | Institución o servicio | Lugar o servicio |
| Trabajo activo | Guardia | Servicio | Guardia activa o jornada | Jornada |
| Etiqueta de tarea | Puesto | Función o destino | Servicio, sala o función | Función |
| Empleador | Empresa | Institución | Institución | Empleador o institución |

`Lugar o servicio`, `Horario`, `Jornada`, `Horas regulares`, `Horas extra
informadas`, `Guardia pasiva`, `Feriado`, `Vacaciones` y `Novedad` son conceptos
compartidos. Los nombres sectoriales son presentación; no crean tablas o motores
duplicados.

La configuración puede guardar una profesión o función opcional para explicar
el perfil —por ejemplo Medicina o Enfermería dentro de Salud—, pero no solicita
matrícula, jerarquía, DNI ni otro identificador sensible.

La guardia pasiva forma parte del modelo común y cada revisión persiste si está
habilitada. Al crear una configuración, Salud propone habilitarla y los demás
sectores proponen deshabilitarla; el usuario confirma o cambia esa propuesta.
Deshabilitarla impide crear nuevas ventanas bajo esa vigencia, pero los registros
históricos continúan visibles. Ningún sector recibe automáticamente una
interpretación legal o salarial.

### Superficies por sector

- Calendario, lugares/servicios, horarios, jornadas, notas, fotos, próximo
  evento, notificaciones, clima, feriados y vacaciones continúan disponibles.
- Resumen muestra horas regulares y extras; muestra pasivas sólo cuando la
  configuración las habilita o existen registros.
- La comparación contra una base mensual depende de la configuración elegida,
  no del nombre del sector.
- La estimación SUVICO se muestra exclusivamente en Vigilancia privada y sólo
  para sus vigencias documentadas. Se oculta en Policía, Salud y Otro; no se
  reemplaza por valores inventados ni por un placeholder de liquidación.
- La política nocturna de una revisión está `definida` o `deshabilitada`. Si está
  definida, inicio y fin son minutos distintos y pueden cruzar medianoche. Sólo
  la raíz V1 recibe automáticamente 21:00–06:00; una instalación nueva comienza
  deshabilitada y requiere elección explícita. Clasifica `R` y `D`, no `P`.
- `passiveEnabled` pertenece a la revisión. La raíz migrada lo deja
  deshabilitado porque 1.0 no aporta registros pasivos que puedan inferirse con
  seguridad.

### Persistencia y migración

- Nombre/apodo y empresa permanecen en el DataStore actual como sus únicas
  fuentes de verdad. No se copian a Room.
- En V2, empresa/empleador es nullable: ausencia de la clave significa `no
  informado`; una cadena vacía no es válida. El fallback global `Inforce` sólo se
  retira después de materializarlo para una actualización V1. En una instalación
  nueva, clave ausente se proyecta como `null`.
- Room v6 incorpora una línea temporal de revisiones de configuración laboral,
  sin `profileId` ni relación de múltiples perfiles.
- La unicidad de la raíz se garantiza con una restricción real del esquema y el
  repositorio; no se confía en un `UNIQUE` simple sobre el mes nullable, porque
  SQLite permite múltiples valores `NULL`.
- La migración `5→6` crea una revisión raíz para usuarios 1.0:
  - sector: Vigilancia privada;
  - base mensual: definida en 12.240 minutos (204 h);
  - modo: exceso mensual habilitado;
  - nocturnidad: 21:00 inclusive–06:00 exclusiva;
  - disponibilidad pasiva: deshabilitada;
  - motor: semántica legada;
  - `effectiveFromMonth = null`;
  - origen: migración V1.
- La raíz cubre el historial y continúa vigente hasta que el usuario confirma
  expresamente su primera revisión V2. Confirmar los mismos valores también crea
  esa revisión y activa el motor V2 desde el primer mes permitido.
- Una instalación nueva no crea raíz ni asume sector o empresa. Permanece `sin
  configurar` hasta que cualquier acceso de creación —Calendario, Objetivos,
  horarios, Perfil o acceso directo— conduzca a una configuración laboral mínima;
  antes de esa confirmación no puede crear objetivos, jornadas, pasivas ni extras.
  La primera revisión nace con motor V2 y vigencia en el mes actual; propone
  pasiva habilitada para Salud y deshabilitada para los demás sectores.
- La compatibilidad de empresa se ejecuta sólo cuando Room contiene la raíz con
  origen migración V1. En una única edición atómica de DataStore conserva una
  empresa explícita o materializa `Inforce` si faltaba, y guarda una marca
  idempotente. Un fallo no borra valores: deja un estado recuperable y reintenta.
- Las trece entidades, claves, índices, instantáneas y migraciones previas se
  conservan sin reescritura.
- La puerta v6 incluye un adaptador mínimo del motor y Resumen: la raíz migrada
  conserva el cálculo legado; una revisión V2 resuelve por mes sector, base, modo
  y nocturnidad para `Shift` regular, mantiene `D/P=0` mientras no existan fuentes
  y no muestra sus tarjetas/acciones aunque `passiveEnabled=true`; oculta SUVICO
  fuera de Vigilancia.
- Las ventanas pasivas y extras se incorporan en una migración posterior junto
  con repositorio, UI y consumo completo del motor, sin exponer acciones antes.
  Una intervención activa regular se guarda una sola vez como `Shift` y se
  asocia a su pasiva; una intervención extra se guarda una sola vez como extra
  exacta. No existe un segundo registro regular paralelo.

## Consecuencias

- MAIN implementa configuración, migración, superficie mínima y adaptador regular
  en una sola puerta; no deja una instalación nueva usando 204 h o 21:00–06:00
  por defecto.
- Consultar un mes exige resolver la revisión vigente y la versión de motor que
  corresponde.
- Un usuario migrado que todavía no confirmó V2 continúa con motor legado; una
  instalación nueva sin configuración no puede cargar trabajo.
- Persistencia/UI de `AD`, `DU` y `P0` se integra junto con el motor que las
  consume, no como una superficie temporalmente huérfana.
- Cambiar etiquetas actuales no altera instantáneas históricas de objetivos,
  horarios, colores, puestos ni guardias.
- La escritura Room y la escritura del DataStore de perfil no forman una falsa
  transacción cruzada: cada fuente conserva responsabilidades independientes.
- Toda migración requiere pruebas con datos representativos en las trece tablas,
  reapertura, rollback ante fallo y comparación de resultados históricos.

## Alternativas descartadas

### Varios perfiles laborales

Se descarta porque contradice la decisión de producto y agregaría selección,
relaciones y ambigüedad histórica innecesarias.

### Guardar toda la configuración en DataStore

Se descarta porque las vigencias mensuales, consultas históricas y futuras
relaciones con intervalos requieren integridad y consultas relacionales.

### Mover nombre y empresa a Room

Se descarta porque duplicaría datos sin aportar valor al cálculo histórico y
obligaría a coordinar dos fuentes durante la migración.
