# Repetir jornadas y cambiar una fecha o todo lo futuro

- Estado: **HABILITADO — ESPERA APERTURA EXPRESA DE JOAQUIN**
- Fecha: 2026-08-23
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `0364b835d07883708e137a7057f235fad9113b38`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Repetir jornadas y decidir si un cambio afecta sólo un día
  o todo lo futuro**

> Esta tarea agrega planes finitos que crean jornadas concretas. No convierte
> el Calendario en un generador oculto ni modifica una jornada por el solo
> hecho de consultarla.

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
4. inspeccioná completos el dominio, Room V2, Calendario, configuración,
   carga manual, edición individual y sus pruebas;
5. detenete ante un mismatch, HEAD separado, cambios sin dueño o una decisión
   material no resuelta; no descartes ni reemplaces trabajo.

## TASK

Implementar exclusivamente planes recurrentes de jornadas V2 y su alcance de
edición:

1. una persona `V2Ready` puede entrar conscientemente a `Repetir jornadas`;
2. elige una plantilla guardada, un puesto o función opcional, un patrón, una
   fecha inicial y una fecha final;
3. antes de guardar ve todas las fechas exactas y los conflictos del plan;
4. confirmar materializa inmediatamente jornadas concretas, cada una con su
   `ShiftWorkSnapshot` y su vínculo durable al plan;
5. una jornada vinculada ofrece `Cambiar sólo esta jornada` o
   `Cambiar desde esta fecha`;
6. eliminar una ocurrencia ofrece `Eliminar sólo esta jornada` o
   `Finalizar desde esta fecha`;
7. cambiar o finalizar desde una fecha afecta sólo el futuro automático
   intacto y conserva pasado, retoques, notas y situaciones protegidas;
8. el Calendario se actualiza inmediatamente al volver.

No implementar horario real, extras, disponibilidad, guardias pasivas,
situaciones especiales nuevas, Resumen V2, adaptación de próximo evento,
adaptación de notificaciones, widget, informes, copias, bloqueo ni Ayuda.

## CONTEXT

La base cerrada ya tiene:

- una experiencia exclusivamente V2;
- cuatro rubros exactos: Vigilancia privada, Policía, Enfermería y Medicina;
- una configuración laboral, varios lugares, tipos y plantillas reutilizables;
- carga manual simple o múltiple desde la única grilla mensual;
- retrocarga consciente solamente mediante el flujo manual;
- edición y eliminación de una jornada exacta con fecha fija;
- pares obligatorios `Shift + ShiftWorkSnapshot`;
- CAS del par y de la ocupación observada;
- Room `MiGuardiaV2Database` versión 1, archivo `miguardia-v2.db` y diecinueve
  tablas;
- Calendario, `F/?`, notas, feriados, vacaciones, carpetas médicas, fotos,
  próximo evento, notificaciones, clima y zoom interno como capacidades
  comunes.

Hoy no existe un modelo, tabla ni vínculo de recurrencia. `Shift` y
`ShiftWorkSnapshot` no identifican un plan. El planificador de carga manual
exige un único mes y no sirve por sí solo para una serie que cruza meses o que
retira fechas que dejaron de pertenecer a una versión futura.

La base Room V2 versión 1 ya es pública dentro del proyecto. Este bloque debe
crear una migración explícita `1→2`; no puede recrear la base ni volver a la
cadena Room histórica de MiGuardia 1.0.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0020, 0022, 0023, 0024, 0025, 0026 y 0027;
- `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md`;
- `docs/prompts/EDICION_Y_ELIMINACION_DE_JORNADAS_V2.md`;
- `docs/prompts/RETIRAR_MODO_V1_Y_FIJAR_BASE_EXCLUSIVA_V2.md`;
- `docs/audits/2026-08-23-retiro-modo-v1-y-base-room-v2.md`;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
- modelos `Shift`, `ShiftWorkSnapshot`, expectativas y mutaciones V2;
- `V2ShiftPlanning.kt`, `ShiftPlanning.kt` y sus pruebas;
- `V2ShiftRepository`, `RoomV2ShiftRepository`, `V2ShiftDao` y
  `V2LocalDataIntegrity`;
- `MiGuardiaV2Database`, `LocalDataStore`, entidades, mapeos, fixtures,
  pruebas Room y esquema `1.json`;
- `V2ManualShiftLoad*`, `V2ShiftEdit*`, WorkSetup, Calendario,
  `MainActivity`, `MiGuardiaApp` y sus pruebas JVM/Compose/Activity.

No recuperes código desde worktrees históricos. Si una fuente histórica
contradice este prompt o ADR 0027, prevalece la jerarquía activa de V2.

## DECISIONES CONGELADAS

### 1. Patrones permitidos

Implementar solamente:

1. **Días de la semana:** uno o más días elegidos; por ejemplo lunes,
   miércoles y viernes.
2. **Cada N días:** entero positivo, anclado en la fecha inicial.
3. **Cada N semanas:** entero positivo, anclado en la fecha inicial y
   repitiendo ese mismo día cada `N × 7` días.
4. **Mensual:** primero, segundo, tercero, cuarto o último lunes, martes,
   miércoles, jueves, viernes, sábado o domingo.

No implementar ciclos rotativos, cronogramas por letras, reglas condicionales,
fechas pares/impares, días hábiles ni expresiones libres.

El inicio y el final son inclusivos. El inicio debe ser hoy o una fecha futura
según `Clock` y zona inyectables. La retrocarga continúa perteneciendo a
`Cargar jornadas`.

El patrón debe producir al menos una fecha. No se fija todavía un máximo como
regla de producto. La expansión termina por la fecha final explícita, detecta
desbordes antes de escribir y nunca recorta silenciosamente. Si necesitás un
límite visible adicional por rendimiento, detenete y devolvé esa decisión a
MAIN.

### 2. Vista previa obligatoria

Antes de confirmar se muestra:

- patrón en lenguaje humano;
- plantilla, lugar, tipo, horario, color y puesto opcional;
- inicio y final;
- cantidad total y fechas exactas en orden;
- fechas libres;
- fechas ocupadas y qué clase de jornada las ocupa;
- fechas protegidas que no pueden reemplazarse;
- advertencias por segunda jornada, superposición, descanso menor a 12 horas
  y carpeta médica;
- resultado exacto de la política de conflictos elegida.

Entrar, cambiar opciones, previsualizar, cancelar, volver o cerrar no escribe.
No generar jornadas al abrir un mes ni al consultar una fecha.

### 3. Configuración resuelta por fecha

Cada fecha se construye con `buildV2ShiftWrite(...)` o una evolución pura que
conserve sus invariantes:

- configuración laboral aplicable a esa fecha;
- mismo timeline y sector;
- lugar, tipo, plantilla y objetivo compatibles;
- fuentes activas al crear o versionar el plan;
- fotografía histórica propia para cada jornada;
- zona e instantes exactos, incluido cruce de medianoche.

Si una fecha no posee una configuración o una fuente compatible, la vista
previa debe explicar el problema y el lote completo no se confirma. No omitir
esa fecha silenciosamente ni copiar la revisión de otra fecha.

Una plantilla archivada puede seguir explicando historia ya materializada,
pero no se usa para una creación o revisión nueva.

### 4. Persistencia Room V2 versión 2

Seguí exactamente ADR 0027:

- raíz estable del plan;
- revisiones inmutables con vigencia futura;
- ocurrencia única por plan y fecha;
- estados exactos `AUTOMATIC`, `CUSTOMIZED`, `EXCLUDED` y `RETIRED`;
- `shiftId` nullable para conservar la exclusión aun después de borrar el
  par;
- vínculo desde la ocurrencia, sin agregar un `planId` suelto a `Shift`.

Respetá también los campos, claves, índices, fotografías y reglas de nulabilidad
mínimos definidos en ADR 0027. `shiftId` debe ser único cuando no es nulo y
ninguna jornada puede pertenecer a dos ocurrencias.

Room debe pasar de versión 1 a 2 mediante una migración explícita. Generá:

`core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/2.json`

Conservá sin cambios el esquema `1.json`. La migración crea las nuevas tablas
vacías y preserva todas las filas existentes de las diecinueve tablas V2.

Prohibido:

- `fallbackToDestructiveMigration`;
- borrar o renombrar `miguardia-v2.db`;
- abrir, copiar, migrar o borrar `miguardia.db`;
- registrar migraciones desde `MiGuardiaDatabase` v1–v7;
- cambiar el nombre de la base;
- permitir jornadas sin fotografía.

### 5. Una sola escritura estructural

`V2ShiftRepository` continúa siendo la única frontera que crea, actualiza o
elimina pares `Shift + ShiftWorkSnapshot`.

Podés agregar un repositorio de lectura para planes, pero la mutación que
afecta plan, revisión, ocurrencias, pares y estados diarios debe atravesar una
única transacción Room. No agregues un DAO o repositorio que escriba jornadas
por separado.

La transacción debe:

- insertar o versionar el plan;
- registrar todas las ocurrencias, incluidas las excluidas;
- insertar, actualizar o eliminar cada par exacto;
- limpiar `F` o `?` sólo en fechas donde realmente insertó una jornada;
- verificar integridad global antes y después;
- revertir todo si falla cualquier fila.

Actualizá también las mutaciones estructurales existentes:

- si `Cargar jornadas` reemplaza una jornada recurrente, su ocurrencia pasa a
  `EXCLUDED` con `shiftId = null` antes de borrar el par, dentro del mismo CAS y
  la misma transacción;
- si una ruta individual actualiza un par recurrente fuera de una mutación del
  plan, su ocurrencia pasa a `CUSTOMIZED`;
- ninguna ruta puede confiar sólo en `ON DELETE SET_NULL` ni dejar
  `AUTOMATIC` o `CUSTOMIZED` sin `shiftId`.

La interfaz de carga manual no cambia por esta adaptación interna.

### 6. Concurrencia y reintento

La revisión previa captura como mínimo:

- plan y última revisión observados;
- ocurrencias del tramo afectado;
- pares `Shift + ShiftWorkSnapshot` alcanzados;
- ocupación y vecindario temporal necesarios para advertencias;
- notas, avisos particulares y señales de protección consultadas.

Dentro de la misma transacción se vuelve a comparar todo. Si cambió:

- no se escribe ninguna parte;
- se muestra que los datos cambiaron;
- el borrador se conserva;
- `Revisar de nuevo` relee fuentes, recalcula fechas y vuelve a la vista
  previa.

Un doble toque produce una sola operación. Un error conserva el borrador y
permite reintentar. Un éxito se consume una sola vez.

### 7. Crear un plan

En Calendario V2 listo aparece `Repetir jornadas` como acción secundaria junto
a `Cargar jornadas`. No aparece durante primera configuración, error o carga.

El flujo propio permite:

1. elegir una plantilla activa;
2. escribir puesto o función opcional;
3. elegir un patrón permitido;
4. indicar inicio y final;
5. revisar fechas y conflictos;
6. confirmar conscientemente.

No reutilices el estado ni el `SavedStateHandle` de carga manual. Compartí
funciones puras cuando corresponda, no borradores ni eventos de interfaz.

`Mi forma de trabajar` agrega el acceso `Planes recurrentes`, con estado vacío,
lista de planes y acciones para abrir, cambiar o finalizar. No agregues un
nuevo destino al panel lateral.

### 8. Conflictos futuros

El lote ofrece exactamente:

- `Conservar lo existente`;
- `Reemplazar sólo jornadas automáticas intactas`;
- `Mantener ambas` después de una advertencia concreta;
- `Cancelar`.

Una jornada manual, personalizada, excluida o protegida nunca se reemplaza.
Si se reemplaza una automática intacta de otro plan, su par se elimina y su
ocurrencia queda `RETIRED` en la misma transacción.

`Conservar lo existente` registra para el plan nuevo una ocurrencia `EXCLUDED`
sin jornada en cada fecha omitida. `Mantener ambas` registra la nueva
ocurrencia como `AUTOMATIC`. Ninguna política deja una fecha ambigua o sin el
resultado mostrado en la vista previa.

La vista previa enumera qué fechas se conservarán, reemplazarán, duplicarán u
omitirán. Si ninguna fecha produciría una jornada concreta, no crea un plan
vacío.

### 9. Cambiar sólo una jornada

Una jornada manual conserva exactamente el editor actual y no muestra una
pregunta de serie.

Una jornada vinculada muestra antes de editar:

- `Cambiar sólo esta jornada`;
- `Cambiar desde esta fecha` cuando la fecha sea hoy o futura;
- `Cancelar`.

`Cambiar sólo esta jornada`:

- reutiliza el editor exacto actual;
- mantiene UUID, fecha y `createdAt`;
- actualiza atómicamente par y ocurrencia;
- marca la ocurrencia como personalizada;
- no cambia la revisión ni otra jornada.

Si la jornada es pasada, sólo puede corregirse esa jornada. Una modificación
futura del plan no debe volver a absorberla.

### 10. Cambiar desde una fecha

`Cambiar desde esta fecha` abre el editor del plan con la fecha de corte
visible e inmutable. Permite cambiar patrón, final, plantilla y puesto.

En patrones cada N días o semanas, la nueva versión se ancla en esa fecha de
corte. Una fecha automática intacta que pertenecía a la versión anterior y ya
no aparece en el patrón nuevo pasa a `RETIRED`; una revisión posterior sólo
puede reactivarla si la incluye expresamente en su vista previa confirmada.
Una fecha `EXCLUDED` no se reactiva silenciosamente.

Al confirmar:

- agrega una revisión, no reescribe la anterior;
- recalcula desde la fecha de corte inclusive;
- toca sólo ocurrencias automáticas intactas futuras;
- conserva personalizadas, excluidas y protegidas;
- aplica las políticas de conflicto al nuevo lote;
- muestra una vista previa de retiradas, conservadas y nuevas antes de
  escribir.

No hay edición masiva retrospectiva. El pasado y todas sus fotografías
permanecen iguales.

### 11. Eliminar una fecha o finalizar el plan

Para una jornada recurrente, la acción de eliminación ofrece:

- `Eliminar sólo esta jornada`;
- `Finalizar desde esta fecha` cuando la fecha sea hoy o futura;
- `Cancelar`.

`Eliminar sólo esta jornada` elimina el par exacto y conserva una ocurrencia
excluida con `shiftId = null`. No cambia otras fechas.

`Finalizar desde esta fecha`:

- agrega una revisión durable de finalización;
- elimina sólo pares automáticos intactos desde el corte inclusive;
- pasa las ocurrencias retiradas automáticamente a `RETIRED`;
- conserva pares personalizados, no `PLANNED`, con notas, con avisos
  particulares o con situaciones aplicables;
- conserva ocurrencias excluidas para impedir regeneraciones;
- nunca toca una jornada anterior al corte.

La confirmación enumera cuántas jornadas se retirarán y cuáles se conservarán
por protección.

### 12. Borrador y recreación

El `SavedStateHandle` propio conserva como mínimo:

- etapa;
- plan y revisión seleccionados;
- plantilla y puesto;
- patrón y parámetros;
- inicio, corte y final;
- política de conflictos;
- confirmaciones pendientes.

No persistas objetos Room, resultados finales ni expectativas CAS antiguas.
Después de recrear, releé plan, catálogo, configuración, ocupación y señales
de protección; recalculá la vista previa.

Si cambió el timeline, el plan desapareció o una fuente dejó de ser válida,
no reemplaces nada silenciosamente: conservá lo recuperable, explicá el cambio
y pedí una elección nueva. Atrás protege un borrador real antes de descartarlo.

### 13. Una sola grilla mensual

El Calendario actual sigue siendo la única grilla mensual de jornadas. El
formulario puede usar controles compactos de fecha para definir inicio, final
o corte, pero no crea otra grilla persistente ni un selector de jornadas
paralelo.

Consultar el Calendario, abrir un detalle, listar planes o previsualizar nunca
escribe. Cargar jornadas manualmente continúa siendo un flujo independiente y
sin cambios de comportamiento.

### 14. Actualización de consumidores

Después de una transacción exitosa:

- el Calendario refleja las jornadas concretas sin reiniciar la app;
- próximo evento y notificaciones reciben la misma reconciliación que ya
  corresponde a altas, cambios o bajas de jornadas;
- no se implementa lógica de patrones dentro de esos consumidores;
- no se generan alarmas, notificaciones o trabajo en segundo plano por cada
  patrón fuera de los mecanismos existentes.

## OUTPUT

Entregá un candidato ejecutable sin commit y limitado a:

### Dominio

- modelos y generador puro de recurrencias;
- vista previa y planificadores de crear, versionar y finalizar;
- expectativas CAS y mutación atómica plan-aware;
- ampliación mínima de `V2ShiftRepository`;
- pruebas JVM exhaustivas.

### Persistencia

- entidades, DAO, mapeos y repositorio de planes;
- ampliación transaccional del escritor V2;
- migración Room V2 `1→2`;
- esquema `2.json`;
- integridad local, fixtures y pruebas de migración/atomicidad.

### Aplicación

- superficie y ViewModel propios para planes recurrentes;
- accesos `Repetir jornadas` y `Planes recurrentes`;
- elección de alcance integrada al editor exacto;
- borrador recuperable, errores, conflictos y regreso reactivo al Calendario;
- textos y semántica necesarios;
- pruebas JVM, Compose y Activity.

No modifiques documentación canónica. MAIN actualizará estado, índice, ADR y
auditoría después de aceptar el candidato.

## SCOPE

Podés crear o modificar solamente lo necesario dentro de:

- `core/domain/src/main/**` y `core/domain/src/test/**` para recurrencias,
  planificación, modelos y repositorios V2;
- `core/database/src/main/**`, `core/database/src/test/**`,
  `core/database/src/androidTest/**` y el esquema Room V2 `2.json`;
- `app/src/main/**`, `app/src/test/**` y `app/src/androidTest/**` para la
  superficie, navegación y pruebas afectadas;
- fixtures V2 existentes cuando deban comprender Room versión 2.

Los nombres internos pueden adaptarse a las convenciones reales. Las tres
piezas persistentes, sus estados, la migración y la única frontera de escritura
no son opcionales.

## DEPENDENCIES

Dependés de contratos ya integrados:

- configuración laboral y catálogo por fecha;
- `buildV2ShiftWrite(...)`;
- `Shift + ShiftWorkSnapshot`;
- `V2ShiftRepository` y sus expectativas CAS;
- carga manual y políticas de ocupación;
- edición/eliminación individual con fecha fija;
- observadores actuales del Calendario;
- Room V2 versión 1 como origen de migración.

No inventes una dependencia futura ni abras otra tarea para resolver este
bloque.

## DO NOT

No:

- modificar Gradle, dependencias, manifiesto, permisos, `applicationId`, SDK,
  `versionCode` o `versionName`;
- tocar DataStore salvo el estado transitorio ya resuelto por
  `SavedStateHandle`;
- cambiar los cuatro rubros ni agregar `Salud`, `Otro` o Psicología;
- cambiar de rubro o crear múltiples perfiles laborales;
- implementar migración o activación desde MiGuardia 1.0;
- crear jornadas sin `ShiftWorkSnapshot`;
- escribir `Shift` desde otra frontera;
- mover una jornada a otra fecha dentro del editor individual;
- recalcular fotografías históricas;
- interpretar una consulta como escritura;
- agregar recurrencias infinitas o sin fecha final;
- agregar trabajo en segundo plano para regenerar planes;
- implementar horario real, extras, pasivas, situaciones especiales nuevas,
  Resumen, widget, informes, copias, bloqueo o agenda profesional;
- agregar cuentas, nube, sincronización, telemetría, anuncios, red o datos
  reales;
- incorporar montos, liquidaciones, deducciones o información sindical;
- modificar `docs/STATUS.md`, `docs/prompts/README.md`, ADR o auditorías;
- crear commit, push, tag, merge, rebase, reset o descarte;
- abrir o modificar producción;
- crear otra tarea, agente, rama o worktree.

## VALIDATION

### 1. Dominio JVM

Cubrir como mínimo:

- todos los días de semana y combinaciones múltiples;
- cada N días y cada N semanas desde su ancla;
- primero, segundo, tercero, cuarto y último día de semana mensual;
- inicio y final inclusivos;
- fin de mes/año y febrero bisiesto;
- rango sin coincidencias, rango amplio finito y desbordes controlados;
- orden, unicidad y determinismo;
- cruces de medianoche por la plantilla;
- cambio desde fecha, finalización y pasado intacto;
- ocurrencia personalizada o excluida nunca regenerada;
- cada política de conflicto;
- segunda jornada, superposición y descanso menor a 12 horas;
- configuración diferente resuelta para cada fecha;
- datos inválidos y conflictos CAS sin mutación parcial.

### 2. Room

Demostrar:

- creación directa y reapertura de Room V2 versión 2;
- migración `1→2` desde una base versión 1 poblada de forma representativa;
- preservación de las diecinueve tablas y sus pares V2;
- nuevas tablas vacías después de migrar;
- claves, índices, nulabilidad y estados válidos;
- `shiftId` único entre ocurrencias y claves foráneas sin dobles dueños;
- plan + revisión + ocurrencias + pares atómicos;
- rollback completo ante fallo intermedio;
- edición individual que marca personalizada;
- eliminación individual que conserva la exclusión;
- cambio futuro que protege personalizadas, excluidas, notas y avisos;
- reemplazo de una automática de otro plan sin huérfanos;
- reemplazo desde `Cargar jornadas` que deja la ocurrencia anterior
  `EXCLUDED` y la jornada nueva como manual;
- `foreign_key_check` e `integrity_check` correctos;
- `1.json` sin cambios y `2.json` generado por Room.

### 3. Aplicación JVM/Compose

Cubrir:

- CTA sólo en `V2Ready`;
- estado vacío y lista de `Planes recurrentes`;
- cada patrón y vista previa exacta;
- fechas/plantillas inválidas;
- conflictos y confirmaciones;
- flujo sólo una jornada;
- flujo desde una fecha;
- eliminación individual y finalización;
- jornada manual sin pregunta de serie;
- carga manual que reemplaza una recurrente sin romper el plan ni reaparecer;
- varias jornadas en el mismo día identificadas;
- doble toque, error, reintento y conflicto concurrente;
- recreación en cada etapa significativa;
- calendario actualizado al volver;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- estado no comunicado únicamente por color.

### 4. Batería local

Ejecutá serializado:

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

Extraé conteos reales desde los XML. Separá claramente:

- JVM ejecutado;
- lint;
- APK compilado;
- AndroidTest compilado;
- instrumentación ejecutada;
- revisión visual;
- pendiente.

Ejecutá además `git diff --check`, revisá archivos modificados/no rastreados y
buscá secretos, datos reales, logs privados, rastros V1 y escritores directos
de jornadas.

### 5. Instrumentación y QA

Este cambio afecta Room, navegación, Compose, recreación, Calendario y
reconciliación de jornadas. Compilar AndroidTest no alcanza.

La tarea sólo puede tocar dispositivos si Joaquin o MAIN lo autorizan
expresamente al abrirla. Con autorización:

- usar exclusivamente el paquete QA y datos ficticios;
- Samsung `SM-S938B`, API 36, como dispositivo principal;
- emulador Android 8.0/API 26 para el piso compatible y la migración;
- no descargar ni crear otro emulador sin permiso;
- no consultar ni cambiar `font_scale`, densidad o tamaño visual del sistema;
- no abrir, limpiar, instalar encima ni modificar producción;
- retirar sólo los paquetes QA autorizados al finalizar y declarar qué quedó.

La instrumentación afectada debe cubrir:

- migración Room `1→2`, reapertura, integridad, atomicidad y rollback;
- crear un plan que atraviese meses;
- recrear un borrador;
- cambiar sólo una jornada;
- eliminar sólo una jornada y comprobar que no reaparece;
- cambiar desde una fecha;
- finalizar desde una fecha con una jornada protegida;
- conflictos con jornada manual y con otro plan;
- regreso al Calendario;
- regresiones de carga manual, edición exacta, WorkSetup, Calendario, próximo
  evento y notificaciones;
- claro/oscuro, orientación y zoom interno 100 %, 150 % y 200 %.

Si no existe autorización o un entorno está indisponible, no inventes el
resultado: dejalo `PENDIENTE` para MAIN.

## HANDOFF A MAIN

Devolvé el candidato sin commit con este formato:

```text
# HANDOFF A MAIN — Repetir jornadas y cambiar una fecha o todo lo futuro

## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## VALIDATION
## ROOM
## PHYSICAL QA
## DEVICE SAFETY
## RISKS
## PENDING
## GIT
## NEXT
```

Incluí:

- ruta, rama, HEAD y upstream;
- lista exacta de modificados, nuevos y eliminados;
- comandos y conteos reales;
- versión Room, ruta y SHA-256 de `2.json`;
- evidencia de migración `1→2` y `1.json` intacto;
- paquetes/dispositivos realmente usados;
- qué no se ejecutó;
- confirmación de que no hubo commit, push, tag, merge, rebase, reset ni
  descarte.

No presentes el candidato como integrado. `NEXT` debe devolverlo
exclusivamente a MAIN para auditoría independiente.

## DONE WHEN

La dependencia termina solamente cuando:

- los cuatro patrones producen fechas correctas y finitas sin truncamiento;
- toda creación muestra vista previa y confirmación;
- cada jornada generada posee fotografía y vínculo de ocurrencia;
- una fecha personalizada o eliminada no reaparece;
- cambiar/finalizar desde una fecha preserva pasado y datos protegidos;
- conflictos y advertencias son explícitos;
- todas las escrituras son atómicas y resisten concurrencia;
- Room V2 migra `1→2` sin pérdida y exporta `2.json`;
- Calendario, carga manual y edición individual siguen funcionando;
- borrador, error, reintento, doble toque y recreación están cubiertos;
- la batería local queda verde;
- la instrumentación y QA autorizadas quedan verdes o sus pendientes se
  declaran con precisión;
- el diff respeta alcance, privacidad y seguridad;
- Git queda sin staging y sin commit.

Si cualquiera de estas condiciones falla, entregá el estado real como
`CANDIDATO INCOMPLETO` y no amplíes el alcance para ocultarlo.
