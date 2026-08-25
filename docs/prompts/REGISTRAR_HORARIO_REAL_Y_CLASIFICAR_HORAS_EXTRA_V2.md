# Registrar horario real y clasificar horas extra V2

- Estado: **HABILITADO — NO IMPLEMENTADO**
- Fecha: 2026-08-25
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `2d41f60840be9e12abde97182d79757ddbb0a992`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Registrar horario real y clasificar horas extra V2**

> Esta tarea distingue lo planificado de lo realmente trabajado dentro de una
> jornada existente. No calcula todavía avance contra una meta de horas ni
> crea trabajo extra independiente sin una jornada dueña.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, el Calendario, la persistencia V2 ni
la secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

Antes de modificar:

1. ejecutá Puerta 0 de sólo lectura;
2. leé completas y en el orden de `AGENTS.md` todas las fuentes obligatorias;
3. confirmá ruta, rama, HEAD exacto informado por MAIN, upstream,
   `v1.0.0^{}`, limpieza, worktrees, remoto privado y autor Git;
4. inspeccioná completos el dominio, Room V2, Calendario, detalle del día,
   edición individual, recurrencias y sus pruebas;
5. detenete ante un mismatch, HEAD separado, cambios sin dueño o una decisión
   material no resuelta; no descartes ni reemplaces trabajo.

## TASK

Implementar exclusivamente el registro, corrección y retiro del horario real
de una jornada V2 existente, junto con la clasificación consciente de su
diferencia adicional:

1. desde la tarjeta de una jornada exacta, una persona puede elegir
   `Registrar horario real`;
2. ve la jornada, fecha y horario planificado sin ambigüedad;
3. informa inicio y final reales exactos;
4. si difieren, escribe un motivo obligatorio y una explicación opcional;
5. si la duración real supera la planificada, decide si toda la diferencia
   continúa como habitual o corresponde a una clase extra;
6. si es extra, identifica uno o más fragmentos exactos y una clase
   reutilizable;
7. revisa planificado, real, habitual, extras y total antes de confirmar;
8. guarda el agregado completo de forma atómica y con protección concurrente;
9. puede corregirlo o volver conscientemente al horario planificado;
10. el detalle del día se actualiza inmediatamente al regresar;
11. una jornada recurrente con horario real queda protegida contra cambios
    automáticos mientras ese registro exista.

No calcular avance contra la referencia, faltante, superación ni cumplimiento.
No crear trabajo extra independiente sin jornada planificada. No implementar
disponibilidad, guardias pasivas, situaciones especiales nuevas, Resumen V2,
rediseño final del Calendario, adaptación de próximo evento, adaptación de
notificaciones, widget, informes, copias, bloqueo ni Ayuda.

## CONTEXT

La base cerrada ya tiene:

- una experiencia exclusivamente V2;
- cuatro rubros exactos: Vigilancia privada, Policía, Enfermería y Medicina;
- una configuración laboral, varios lugares, tipos y plantillas reutilizables;
- carga manual simple o múltiple desde la única grilla mensual;
- planes recurrentes finitos y cambios de una fecha o de todo lo futuro;
- edición y eliminación exacta de una jornada;
- pares obligatorios `Shift + ShiftWorkSnapshot`;
- CAS del par, ocupación, planes, revisiones y ocurrencias;
- Room `MiGuardiaV2Database` versión 2, archivo `miguardia-v2.db` y
  veintidós tablas;
- Calendario, `F/?`, notas, feriados, vacaciones, carpetas médicas, fotos,
  próximo evento, notificaciones, clima y zoom interno como capacidades
  comunes.

`Shift.startAt` y `Shift.endAt` representan el horario planificado. El detalle
del día hoy no conoce horario real ni clases extra. El dominio posee
`HoursReference` y `ExtraWorkClass`, pero no existe persistencia, repositorio,
interfaz ni consumidor para las clases.

La documentación no resolvió todavía qué meta aplicar cuando una referencia
de horas cambia a mitad de una semana o ciclo. Esa decisión impide calcular
correctamente avance y queda fuera de esta dependencia.

Room V2 versión 2 ya contiene datos persistibles de configuración, jornadas y
recurrencias. Este bloque debe crear una migración explícita `2→3`, conservar
la cadena `1→2→3` y no tocar la base histórica `miguardia.db`.

Esquemas protegidos:

- SHA-256 `1.json`:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- SHA-256 `2.json`:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0018, 0019, 0020, 0022, 0023, 0025, 0026, 0027 y 0028;
- `docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md`;
- `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md`;
- `docs/prompts/EDICION_Y_ELIMINACION_DE_JORNADAS_V2.md`;
- `docs/prompts/REPETIR_JORNADAS_Y_CAMBIAR_DESDE_UNA_FECHA_V2.md`;
- `docs/audits/2026-08-25-planes-recurrentes-y-cambios-futuros-v2.md`;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
- `Shift`, `ShiftWorkSnapshot`, `ExtraWorkClass`, `HoursReference` y sus
  pruebas;
- `V2ShiftRepository`, `RoomV2ShiftRepository`,
  `V2LocalDataIntegrity`, DAO y mapeos relacionados;
- `MiGuardiaV2Database`, `LocalDataStore`, migraciones, fixtures, pruebas
  Room y esquemas `1.json` y `2.json`;
- `V2ShiftEdit*`, `V2RecurringPlan*`, detalle del día, WorkSetup,
  `MainActivity`, `MiGuardiaApp` y sus pruebas JVM/Compose/Activity;
- consumidores actuales de jornada en Calendario, próximo evento y
  notificaciones, sólo para impedir regresiones.

No recuperes código desde worktrees históricos. Si una fuente histórica
contradice este prompt o ADR 0028, prevalece la jerarquía activa de V2.

## DEPENDENCIES

Esta tarea depende de los siguientes contratos ya integrados:

- configuración laboral por vigencia exacta;
- catálogo de lugares, tipos y plantillas;
- pares `Shift + ShiftWorkSnapshot`;
- carga manual;
- edición y eliminación de una jornada exacta;
- Room V2 exclusiva;
- planes recurrentes y ocurrencias protegidas.

No depende de una pantalla de Resumen ni de un motor final de horas. Debe dejar
datos exactos y observables para que el bloque siguiente calcule trabajo y
avance sin reinterpretarlos.

## DECISIONES CONGELADAS

### 1. Jornada dueña y horario planificado

- Cada registro real pertenece a un `shiftId` existente y a su
  `ShiftWorkSnapshot` exacta.
- La jornada se identifica visualmente como `Jornada N de M`, con UUID
  accesible para distinguir jornadas iguales.
- La fecha dueña es `Shift.localStartDate` y no se modifica desde este flujo.
- `Shift.startAt`, `Shift.endAt`, `zoneId` y la fotografía planificada no se
  sobrescriben con datos reales.
- Próximo evento y notificaciones continúan usando lo planificado.
- Como el alta se habilita recién al alcanzar el fin planificado, una salida
  real anticipada no crea durante este bloque un estado contradictorio de
  “trabajo terminado” frente a próximo evento o avisos.
- No se crea un estado `COMPLETED`.

### 2. Intervalo real

- Existe como máximo un registro real por jornada.
- Usa minutos enteros e intervalo exacto `[inicio, fin)`.
- El inicio debe ser anterior al final.
- El final debe ser igual o anterior a `Clock.instant()` inyectable. Las
  futuras siguen siendo jornadas planificadas, no trabajo realizado.
- La entrada usa fecha y hora explícitas para el inicio, y fecha y hora
  explícitas para el final. No alcanza un booleano “termina mañana”.
- Puede comenzar o terminar en otro día civil y durar más de 24 horas. La
  interfaz muestra los cuatro valores completos cuando cruza medianoche, fin
  de mes/año o se desplaza de día.
- La zona es `Shift.zoneId`, se muestra y no se edita. Una hora local
  inexistente por DST se rechaza; una hora ambigua exige elegir expresamente
  cuál de sus dos offsets corresponde.
- La jornada continúa en la celda de su fecha planificada; este bloque no la
  mueve de mes. La atribución de horas a un día, mes o período queda para el
  motor siguiente y usará los instantes reales.
- Sólo el estado `PLANNED` acepta horario real. `CANCELLED` y `ABSENT` lo
  rechazan con explicación visible.
- Sin corrección previa, el CTA se habilita únicamente cuando
  `Clock.instant() >= Shift.endAt`. Antes se muestra la planificación sin
  acción. Una corrección existente sigue siendo accesible.
- Si inicio y final reales coinciden exactamente con lo planificado, no se
  guarda una fila redundante. Si ya existía una corrección, esa entrada no la
  borra automáticamente: deshabilita Guardar y dirige a
  `Volver al horario planificado`.
- Si difieren, `differenceReason` es obligatorio, se normaliza y no puede ser
  sólo espacios. `explanation` es opcional.
- `createdAt` se conserva al corregir; `updatedAt` avanza estrictamente y se
  normaliza a milisegundos Room.

No imponer un máximo arbitrario de duración ni inferir el cruce de medianoche
desde un texto. Validá desbordes y representá siempre instantes exactos.

### 3. Trabajo habitual y diferencia adicional

```text
duración real = fin real - inicio real
diferencia = duración real - duración planificada
```

- Si `diferencia <= 0`, todo el horario real es trabajo habitual y no se
  aceptan fragmentos extra.
- Si `diferencia > 0`, la persona elige conscientemente:
  - `Toda la diferencia es habitual`; o
  - `La diferencia es una clase extra`.
- La aplicación no preselecciona una respuesta por sector, tipo, lugar,
  referencia ni historia previa.
- Si la diferencia es habitual, no se guardan fragmentos extra.
- Si es extra, la suma de los fragmentos debe ser exactamente igual a la
  diferencia completa. No se guarda una cantidad opaca.
- Una sola clase extra elegida se aplica a todos los fragmentos de esa
  diferencia.
- Un fragmento extra es positivo, está contenido dentro del intervalo real y
  no se superpone con otro fragmento de la misma jornada.
- La persona elige su ubicación exacta dentro de las porciones del horario
  real que quedan fuera del intervalo planificado. No se deriva ni mueve
  automáticamente y la suma elegida debe ser la diferencia.
- Se admiten varios fragmentos disjuntos. Debe funcionar como mínimo el caso
  de entrada anticipada más salida tardía.
- El trabajo habitual derivado es el intervalo real menos la unión de los
  fragmentos extra.
- El total trabajado derivado es habitual más extras y coincide siempre con
  la duración real. Ningún minuto se cuenta dos veces.

Superar una referencia nunca crea extras automáticamente. Tampoco lo hacen:

- cubrir a otra persona;
- un cubrefranco;
- nocturnidad;
- feriado;
- fin de semana;
- una segunda jornada;
- un nombre o regla sectorial.

### 4. Clases extra reutilizables

Cada clase pertenece al mismo `timelineId` y sector de la jornada, y contiene:

- nombre visible y clave normalizada;
- si ayuda a cumplir la referencia;
- si tendrá un desglose propio en el futuro Resumen;
- estado activo o archivado;
- timestamps normalizados.

`Horas extras`, `Extensión de turno` y `Servicio extra` aparecen sólo como
sugerencias de texto. No se insertan al crear el rubro ni se guardan
silenciosamente. Al usar una sugerencia o escribir otro nombre, la persona
debe responder expresamente `Sí` o `No` para cada uno de los dos indicadores;
ambos comienzan sin respuesta y Guardar permanece deshabilitado hasta
resolverlos. No hay valores predeterminados sectoriales.

- Las clases activas se pueden elegir.
- Una clase archivada no se ofrece para registros nuevos.
- Una corrección existente puede conservar exactamente su clase archivada y
  fotografía si no cambia la clasificación ni sus fragmentos. Cualquier
  reclasificación exige una clase activa.
- Una clase archivada puede reactivarse conscientemente; la unicidad
  normalizada no se elude creando otra igual.
- Archivar no borra historia.
- Renombrar o cambiar indicadores no reinterpreta intervalos anteriores.
- Cada fragmento guarda una fotografía del nombre y los dos indicadores.
- La fotografía debe ser coherente con la clase observada al crear o
  reclasificar. Puede diferir legítimamente del catálogo actual después de un
  renombre, cambio de indicadores o archivo.
- Una clase observada que cambió antes de confirmar provoca conflicto; no se
  sustituye por otra ni se toma su valor nuevo silenciosamente.
- Nombres equivalentes después de normalización no pueden duplicarse dentro
  del mismo timeline y sector.

El catálogo tiene acceso visible desde `Mi forma de trabajar`. La creación
inline es una subetapa del mismo coordinador y `SavedStateHandle` del horario
real; no abre otro host bloqueante ni pierde el borrador al volver.

Una clase nueva iniciada dentro del editor permanece como borrador hasta la
confirmación final. Esa confirmación crea clase, registro real y fragmentos en
la misma transacción. Cancelar el editor no deja una clase huérfana. La
administración general desde `Mi forma de trabajar` sí constituye una acción
propia y consciente de catálogo.

### 5. Room V2 versión 3

Implementar exactamente la decisión de ADR 0028:

1. `extra_work_classes`;
2. `shift_actual_records`;
3. `shift_extra_intervals`;
4. índice único `shiftId + timelineId + sector` en
   `shift_work_snapshots` para la FK compuesta.

Además:

- `shift_actual_records` declara única su combinación
  `shiftId + timelineId + sector` y la referencia completa con `RESTRICT` a
  `shift_work_snapshots`;
- `shift_extra_intervals(shiftId, timelineId, sector)` referencia con
  `CASCADE` a la combinación del registro real;
- `shift_extra_intervals(extraWorkClassId, timelineId, sector)` referencia con
  `RESTRICT` a la combinación única de `extra_work_classes`;
- existen índices hijos exactos por
  `shiftId + timelineId + sector` y
  `extraWorkClassId + timelineId + sector`;
- todos los fragmentos de un mismo `shiftId` comparten la única clase de esa
  corrección.

La migración `2→3`:

- crea las tablas nuevas vacías;
- agrega sólo el índice autorizado a la tabla existente;
- preserva las veintidós tablas y todos los datos V2;
- no transforma jornadas planificadas en registros reales;
- no crea clases por defecto;
- no usa fallback destructivo;
- no abre, copia, borra ni migra `miguardia.db`;
- conserva `MIGRATION_1_2` y permite la cadena `1→2→3`;
- exporta `3.json`;
- no modifica `1.json` ni `2.json`.

La base queda con veinticinco tablas de aplicación. No guardar totales,
cumplimiento, minutos agregados ni una marca booleana de “ya trabajada”.

### 6. Fronteras de escritura

Crear una frontera explícita de dominio para horario real. Su implementación
Room es la única autorizada para:

- observar un registro real y sus fragmentos;
- crear o corregir el agregado;
- volver al horario planificado;
- crear, modificar o archivar clases dentro de su contrato.

No escribir esas tablas desde ViewModels ni DAO expuestos a la UI.

`V2ShiftRepository` continúa siendo la única frontera estructural capaz de
crear, editar o eliminar `Shift + ShiftWorkSnapshot`. Debe ampliarse sólo lo
necesario para:

- observar que una jornada posee horario real;
- impedir que una escritura vieja lo borre por cascada o ignorancia;
- incluirlo en la protección y CAS de edición, eliminación, carga manual y
  recurrencias;
- eliminarlo únicamente dentro de una confirmación estructural consciente.

No agregar un segundo escritor directo de `Shift`.

### 7. Atomicidad, CAS e integridad

Guardar, corregir o quitar horario real compara por valor completo:

- `Shift + ShiftWorkSnapshot`;
- registro real anterior o su ausencia;
- lista completa y ordenada de fragmentos anteriores;
- la única clase elegida o su ausencia;
- ocurrencia recurrente vinculada o su ausencia;
- notas, configuración particular de avisos, estado explícito, carpeta médica,
  vacaciones, feriado y cualquier otra fila consultada para protección,
  contexto o advertencias.

Una única transacción guarda todo o nada. Ante conflicto:

- no modifica ninguna fila;
- conserva el borrador;
- explica que la jornada o configuración cambió;
- ofrece refrescar y revisar de nuevo;
- no reintenta con datos nuevos en silencio.

Cero fragmentos es válido y representa que toda la diferencia es habitual.
Con uno o más fragmentos, la integridad exige que la diferencia sea positiva y
que su suma coincida exactamente con ella.

La integridad local rechaza:

- registro real huérfano;
- timeline o sector cruzados;
- intervalo real vacío o invertido;
- horario real idéntico al planificado guardado como corrección;
- motivo vacío cuando existe diferencia;
- fragmentos fuera del horario real;
- fragmentos vacíos, invertidos o superpuestos;
- extras cuando la duración real no supera la planificada;
- uno o más fragmentos cuya suma difiere de la diferencia positiva;
- fragmentos de una misma corrección con clases diferentes;
- clase inexistente, cruzada o sin fotografía coherente;
- timestamps fuera de la normalización acordada.

“Fotografía coherente” significa que era igual a la clase observada al crear o
reclasificar. No significa compararla para siempre con el catálogo mutable:
una fotografía histórica distinta del nombre o indicadores actuales es válida.

Las acciones protegen doble toque, error, rollback y reintento. No usan sólo
`updatedAt` como CAS.

### 8. Recurrencias y mutaciones estructurales

Registrar horario real no cambia una ocurrencia `AUTOMATIC` a
`CUSTOMIZED`, porque no modifica la regla ni la planificación.

Mientras el registro real exista:

- la ocurrencia no es automática intacta;
- una revisión o finalización del plan no puede retirar ni reemplazar su
  jornada silenciosamente;
- la carga manual no puede reemplazarla sin una decisión expresa;
- eliminar la jornada debe mencionar que también quitará horario real y
  extras;
- cambiar el intervalo planificado queda bloqueado hasta volver
  explícitamente al horario planificado. Corregir el horario real no
  desbloquea esa modificación.

Si se vuelve al horario planificado, la ocurrencia recupera su elegibilidad
automática sólo si no tiene notas, avisos, estados o protecciones adicionales.

Un cambio estructural que conserva exactamente inicio y final planificados
puede conservar el registro real, pero debe incluir el agregado completo en su
CAS. No dejar filas huérfanas ni depender únicamente de la FK.

- Cambiar sólo puesto o aplicar una plantilla que produzca los mismos
  instantes puede continuar después de revisar el CAS.
- Una plantilla o edición que cambie los instantes muestra un bloqueo y acceso
  directo a `Corregir horario real` o `Volver al horario planificado`.
- Reemplazar desde carga manual o desde un plan muestra una advertencia
  específica de que la confirmación eliminaría horario real y extras. La
  advertencia genérica de “fecha ocupada” no alcanza.

### 9. Flujo visible

En cada tarjeta de jornada exacta del detalle del día:

- mientras se lee el agregado: espera neutral, sin CTA;
- si la lectura falla: error y reintento, sin asumir que no existe corrección;
- sin registro y con fin planificado alcanzado: `Registrar horario real`;
- sin registro y antes del fin planificado: planificación visible sin CTA;
- con registro: bloque `Planificado / Real`, motivo, explicación, habitual,
  extras por clase y total, más `Corregir horario real`;
- acción secundaria consciente `Volver al horario planificado`;
- `CANCELLED` o `ABSENT`: sin CTA de registro y con un mensaje que explica por
  qué no se puede cargar horario real; no inventar una causa laboral que el
  modelo no guarda.

El estado de lectura por jornada distingue como mínimo `LOADING`, `CONTENT` y
`ERROR`. Nunca mostrar `Registrar horario real` hasta conocer si el agregado
existe.

Usar una pantalla propia, separada de la edición estructural y del modo de
selección múltiple del Calendario. Como mínimo:

1. identificar jornada y planificación;
2. cargar fecha y hora reales para ambos extremos, con zona visible, y motivo;
3. clasificar la diferencia cuando corresponda;
4. elegir o crear clase y fragmentos exactos;
5. revisar;
6. guardar;
7. volver al mismo detalle actualizado.

Abrir, escribir, cambiar de etapa, revisar, volver o cancelar no persiste el
horario real ni una clase inline. Sólo la confirmación final escribe.

Después de guardar o volver al horario planificado se cierra sólo la
superficie bloqueante. Se conserva `CalendarUiState.detailDate`, se espera el
Flow actualizado y permanece abierto el mismo detalle y la misma jornada; no
se llama a una salida que cierre también la fecha.

La revisión muestra:

- `Jornada N de M` y UUID;
- fecha dueña;
- planificado;
- real;
- duración habitual;
- cada fragmento extra, clase y duración;
- total;
- advertencias aplicables.

`Volver al horario planificado` confirma que borrará la corrección y sus
extras derivadas, pero no una nota, aviso, foto, situación o dato ajeno.

Al corregir:

- pasar de real mayor a real menor o igual limpia clase y fragmentos sólo en el
  borrador y exige revisar antes de guardar;
- pasar de `extra` a `habitual` limpia los fragmentos del borrador;
- si un cambio deja fragmentos fuera de rango o con suma incorrecta, no los
  recorta ni desplaza: vuelve a Clasificación con un error visible;
- ingresar exactamente el horario planificado no habilita Guardar y ofrece la
  confirmación separada `Volver al horario planificado`.

### 10. Borrador, recreación y accesibilidad

`SavedStateHandle` conserva como mínimo:

- `shiftId` y etapa;
- expectativa/fingerprint observada;
- fecha y hora de inicio reales;
- fecha y hora de final reales;
- offset elegido cuando una hora local es ambigua;
- zona visible de la jornada;
- motivo y explicación;
- elección habitual/extra;
- clase elegida o borrador de clase;
- todos los fragmentos exactos;
- confirmaciones y revisión pendientes.

Al recrear:

- no sustituir la jornada por otra del mismo día;
- no cambiar la clase elegida por la primera disponible;
- conservar el texto y los fragmentos;
- si la fuente dejó de existir o cambió, mostrar conflicto y no guardar.

La UI debe:

- funcionar con scroll e IME;
- tener tags únicos aun con dos jornadas visualmente iguales;
- no depender sólo del color;
- ofrecer descripciones que distingan jornada, planificado, real y extras;
- respetar claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- no consultar ni modificar `font_scale`, densidad ni tamaño visual del
  sistema.

### 11. Situaciones existentes

Una carpeta médica, vacaciones, feriado, nota o estado explícito existente no
se borra ni se convierte. Si coincide con el horario real:

- mostrar contexto o una advertencia concreta según la protección ya vigente;
- preservar ambos datos;
- no decidir en este bloque cuánto suma al cumplimiento;
- no crear extras automáticamente;
- incluir la evidencia observada en el CAS cuando ya protege la jornada.

La precedencia de situaciones especiales se define en su bloque posterior.
Una nota o un feriado son contexto informativo y no agregan por sí solos una
confirmación nueva. Las confirmaciones existentes por carpeta médica,
vacaciones o conflicto temporal conservan su semántica.

## OUTPUT

La dependencia debe entregar:

- modelos puros para registro real, fragmentos, borrador, expectativa y
  resultado;
- catálogo persistente de clases extra;
- repositorio(s) de dominio y Room con una única frontera por tipo de escritura;
- entidades, DAO, mapeos e integridad local;
- migración explícita `2→3` y esquema `3.json`;
- flujo visible desde el detalle de la jornada;
- administración mínima de clases desde `Mi forma de trabajar`;
- integración de protecciones con edición, eliminación, carga manual y planes;
- pruebas puras, JVM de repositorio si corresponde, Room instrumentadas,
  Compose y Activity;
- handoff verificable a MAIN con diff sin commit.

La documentación canónica, ADR, índice, STATUS y auditoría final pertenecen a
MAIN y no se modifican desde esta dependencia.

## SCOPE

Puede modificar solamente lo necesario dentro de:

- `core/domain/src/main/**` y `core/domain/src/test/**`;
- `core/database/src/main/**`;
- `core/database/src/test/**`;
- `core/database/src/androidTest/**`;
- `core/database/schemas/**` únicamente para `3.json`;
- `app/src/main/**`;
- `app/src/test/**`;
- `app/src/androidTest/**`.

Puede adaptar código compartido de edición, carga manual y recurrencias sólo
para volverlo consciente del horario real y proteger atomicidad. No ampliar
esas funciones.

## DO NOT

No:

- modificar `AGENTS.md`, `docs/**` ni fuentes canónicas;
- cambiar los cuatro rubros ni crear `Salud` u `Otro`;
- unir Enfermería y Medicina;
- implementar referencia visible, avance, faltante, superación o
  cumplimiento;
- decidir el cambio de referencia dentro de una semana o ciclo;
- crear extras independientes sin `shiftId`;
- crear clases extra silenciosamente;
- inferir extras por superar metas, noche, feriado, fin de semana o cobertura;
- modificar `Shift.startAt/endAt` para representar la realidad;
- agregar `COMPLETED`;
- adaptar celdas finales del Calendario, Resumen, próximo evento o
  notificaciones para consumir horario real;
- implementar guardias pasivas, disponibilidad o situaciones especiales;
- agregar dependencias de producción;
- modificar Gradle, manifiesto, permisos, `applicationId`, versión o SDK;
- tocar DataStore salvo una necesidad demostrada y autorizada por MAIN;
- modificar `1.json` o `2.json`;
- conectar con Room V1 o `miguardia.db`;
- usar fallback destructivo;
- acceder a red, cuentas, nube, telemetría o datos reales;
- registrar datos privados en logs;
- abrir, instalar, limpiar o desinstalar producción;
- consultar o modificar `font_scale`, densidad o tamaño visual del sistema;
- crear commit, push, tag, merge, rebase, reset o descarte;
- crear otra tarea, rama, worktree o subagente.

Ante una necesidad real fuera de alcance, detenete y devolvé a MAIN el punto
exacto. No inventes una extensión.

## VALIDATION

### 1. Dominio JVM

Cubrir como mínimo:

- sin corrección usa planificación;
- real igual no genera registro;
- real menor;
- real mayor totalmente habitual;
- real mayor con un fragmento extra;
- entrada anticipada y salida tardía con dos fragmentos;
- intervalo desplazado con igual duración;
- intervalo desplazado y mayor, eligiendo extras sólo fuera del planificado;
- fragmentos fuera de rango, invertidos, solapados o con suma incorrecta;
- rechazo de fragmentos con dos clases en la misma corrección;
- inicio real el día anterior, final el día siguiente y duración mayor a 24 h;
- medianoche, fin de mes/año, febrero bisiesto, hora local inexistente y hora
  ambigua por DST;
- no extra por referencia superada, cobertura, noche, feriado o fin de semana;
- clase activa, archivada, reactivada, renombrada y fotografía histórica que
  puede diferir del catálogo actual;
- motivo obligatorio y explicación opcional;
- timestamps y desbordes;
- doble conteo imposible;
- reloj inyectado y rechazo de intervalos no finalizados;
- conflicto CAS y resultados tipados.

### 2. Room

Verificar:

- migración `2→3` desde una base 2 poblada en las veintidós tablas, incluidas
  recurrencias;
- cadena `1→2→3`;
- `1.json` y `2.json` byte a byte intactos;
- `3.json` exportado y hash informado;
- veinticinco tablas exactas;
- alta, corrección, retiro y reapertura del agregado;
- cero, uno y dos fragmentos;
- catálogo, normalización, archivo y snapshot histórico;
- rollback total;
- `integrity_check` y `foreign_key_check`;
- FK `RESTRICT` y `CASCADE` sólo en la dirección autorizada;
- rechazo de huérfanos, cruces, intervalos inválidos y fotografías incoherentes;
- conflictos sobre par, registro, fragmentos, clase y ocurrencia;
- eliminación estructural consciente y protección recurrente;
- Flows reactivos.

### 3. App JVM, Compose y Activity

Cubrir como mínimo:

- CTA ausente antes del fin planificado, presente en el límite exacto y
  correcto según estado;
- lectura `LOADING / CONTENT / ERROR`, reintento y ausencia de CTA falso;
- dos jornadas iguales distinguidas por ordinal y UUID;
- planificado/real/habitual/extra/total visibles;
- formulario y revisión;
- menor, mayor habitual, uno y dos fragmentos extra;
- creación inline en el mismo coordinador, error/Atrás/recreación sin perder
  borrador y administración general de clases;
- ambos atributos de clase sin respuesta inicial, opciones Sí/No y Guardar
  bloqueado hasta contestar;
- nombre duplicado normalizado;
- clase archivada no elegible para reclasificar, reactivación consciente y
  fotografía histórica conservable;
- inicio el día anterior, final al siguiente, más de 24 horas y cruce de fin de
  mes/año con fechas claras;
- jornada cancelada o ausente;
- nota, carpeta médica, vacaciones y feriado preservados;
- recurrencia protegida sin marcarla estructuralmente `CUSTOMIZED`;
- volver al horario planificado;
- eliminación estructural con confirmación ampliada;
- cambio planificado bloqueado mientras la corrección lo vuelva ambiguo;
- reemplazos manuales o recurrentes con advertencia específica;
- transiciones mayor→menor, extra→habitual, fragmentos invalidados y real igual
  al planificado sin borrado silencioso;
- error, rollback, reintento, doble toque y conflicto;
- recreación en cada etapa con borrador intacto;
- regreso al mismo detalle actualizado;
- orden ordinal estable por `startAt`, `endAt` e `id`;
- ausencia explícita de acciones de avance, cumplimiento o trabajo extra
  independiente;
- recorrido anterior de carga, edición y recurrencias sin regresión;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- accesibilidad sin depender sólo de color.

### 4. Batería local serializada

Ejecutar desde la raíz:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Obtener conteos reales desde XML y distinguir:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

Ejecutar además:

- `git diff --check`;
- búsqueda de escritores directos de `Shift`;
- búsqueda de `fallbackToDestructiveMigration`;
- comparación/hash de `1.json` y `2.json`;
- auditoría de secretos, logs, red, permisos, Gradle y manifiesto.

### 5. Android y dispositivos

Compilar AndroidTest es obligatorio pero no equivale a ejecutarlo.

La dependencia sólo puede usar Samsung QA o emulador API 26 si Joaquin lo
autoriza expresamente en esa tarea. Con autorización:

- usar únicamente paquetes QA/test;
- usar datos ficticios;
- no abrir ni modificar producción;
- probar migración, Room, flujo Activity/Compose, recreación y regresiones;
- revisar claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- no consultar ni modificar ajustes visuales del sistema;
- desinstalar sólo los paquetes QA autorizados al finalizar;
- informar exactamente qué quedó en cada dispositivo.

Si no existe autorización o un dispositivo no está disponible, marcar la
evidencia como `PENDIENTE`. No presentar APK compilado como QA física.

## HANDOFF A MAIN

Entregar en español, de forma compacta y verificable:

```text
# HANDOFF A MAIN — Registrar horario real y clasificar horas extra V2

## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## VALIDATION
## ROOM
## DEVICE SAFETY
## RISKS
## PENDING
## GIT
## NEXT
```

Incluir:

- ruta, rama, HEAD de entrada y upstream;
- archivos modificados, nuevos y eliminados;
- conteos reales de pruebas;
- qué fue sólo compilado y qué se ejecutó;
- versión Room, tablas, migración y SHA-256 de esquemas;
- paquetes usados y estado final de dispositivos;
- límites no implementados;
- `git status`, `git diff --check` y confirmación de cero staged;
- confirmación de que no hubo commit, push, tag, merge, rebase, reset ni
  descarte.

El resultado queda directamente en el checkout compartido, sin commit. No
existe nada para cherry-pick. MAIN audita cada hunk, repite pruebas
proporcionales, encarga una revisión independiente y decide el checkpoint.

## DONE WHEN

La dependencia se considera candidata sólo cuando:

- una jornada exacta permite registrar, corregir y quitar horario real;
- planificación y realidad permanecen separadas;
- las diferencias mayores se clasifican conscientemente;
- los fragmentos extra son exactos, disjuntos y no duplican minutos;
- las clases son reutilizables, explícitas y conservan fotografía histórica;
- Room migra de 2 a 3 sin perder datos y con esquemas anteriores intactos;
- escritura, retiro y mutaciones estructurales son atómicos y protegidos por
  CAS completo;
- recurrencias, carga manual, edición y eliminación no borran datos reales en
  silencio;
- detalle, borrador, errores, recreación y accesibilidad están cubiertos;
- la batería local requerida está verde;
- la evidencia Android se ejecutó con autorización o quedó marcada
  honestamente como pendiente;
- no se implementó avance, extras independientes ni otro bloque futuro;
- el diff está limpio de whitespace, sin staged y sin cambios fuera de alcance;
- el handoff vuelve a MAIN sin commit ni push.
