# Guardias pasivas y disponibilidad V2

- Estado: **CERRADO — INTEGRADO POR MAIN**
- Fecha: 2026-08-25
- Proyecto obligatorio:
  C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0
- Rama obligatoria: codex/miguardia-2.0
- Base funcional cerrada:
  964b7cd0ce399ff20ba371fa6585e6e2850fd9b7
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Guardias pasivas y disponibilidad**

## QUÉ HACE

Permite que la persona indique si usa disponibilidad y elija cómo quiere verla
en MiGuardia:

- Guardia pasiva;
- Disponible para llamado;
- Retén.

Después puede registrar, consultar, corregir y eliminar ventanas con fecha,
inicio y final exactos desde el Calendario. MiGuardia informa por separado el
tiempo programado, el tiempo pasivo efectivamente transcurrido, el tramo
reemplazado por trabajo activo y lo que todavía queda pendiente o proyectado.

## POR QUÉ EXISTE

MiGuardia ya conoce las jornadas planificadas, el horario realmente trabajado y
los extras. Todavía no puede representar el tiempo en que una persona queda
disponible para ser llamada sin estar trabajando activamente.

Esta dependencia agrega esa pieza sin convertirla en una jornada ni en horas
trabajadas. También resuelve la regla central de que el trabajo activo reemplaza
sólo el tramo pasivo coincidente. Deja una fuente estable para que las próximas
dependencias incorporen situaciones especiales y, después, cierren el conteo
final de horas y cumplimiento.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, el Calendario, la persistencia V2 ni
la secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

Antes de modificar:

1. ejecutá Puerta 0 de sólo lectura;
2. leé completas y en el orden de AGENTS.md todas las fuentes obligatorias;
3. confirmá ruta, rama, HEAD exacto informado por MAIN, upstream,
   v1.0.0^{}, limpieza, worktrees, remoto privado y autor Git;
4. inspeccioná completos configuración laboral, jornadas, horario real, extras
   independientes, avance de horas, Calendario, Room V2 y sus pruebas;
5. detenete ante un mismatch, HEAD separado, cambios sin dueño o una decisión
   material no resuelta; no descartes ni reemplaces trabajo.

## TASK

Implementar exclusivamente:

1. la elección visible del nombre de disponibilidad dentro de la única
   configuración laboral;
2. el alta, consulta, corrección y eliminación exactas de ventanas de
   disponibilidad;
3. el cálculo puro y reactivo de disponibilidad programada, efectiva,
   reemplazada y pendiente/proyectada;
4. una integración visual mínima con la única grilla mensual, el detalle del
   día y la superficie Horas y extras;
5. persistencia propia mediante Room V2 versión 5 y migración explícita 4→5.

El recorrido mínimo debe permitir:

1. entrar desde Mi forma de trabajar a la configuración de disponibilidad;
2. elegir No uso disponibilidad o uno de los tres nombres exactos;
3. confirmar desde qué fecha local rige el cambio;
4. volver al Calendario sin reiniciar la referencia de horas;
5. elegir un día en la única grilla;
6. registrar inicio y final exactos;
7. revisar duración, nombre visible y posibles conflictos;
8. guardar atómicamente;
9. ver un indicador textual mínimo y el detalle completo;
10. corregir la ventana conservando su fecha dueña;
11. eliminarla mediante confirmación;
12. ver el cálculo actualizado cuando cambien la ventana o las fuentes de
    trabajo activo.

No implementar todavía situaciones especiales nuevas, consolidación final del
motor, Resumen personalizable, Calendario final, tarjeta superior final,
próximo evento, notificaciones, recurrencia de disponibilidades, widget,
informes, copias, bloqueo ni Ayuda.

## CONTEXT

La base cerrada ya posee:

- una experiencia exclusivamente V2;
- cuatro rubros exactos: Vigilancia privada, Policía, Enfermería y Medicina;
- una sola configuración laboral con revisiones vigentes desde LocalDate;
- AvailabilityLabel nullable y persistido con tres textos visibles sugeridos;
- lugares, tipos y plantillas reutilizables;
- jornadas manuales y recurrentes;
- pares obligatorios Shift + ShiftWorkSnapshot;
- horario real y fragmentos extra exactos;
- extras independientes con identidad propia;
- un motor de avance que separa habitual, extras, total, cumplimiento y
  pendiente;
- vacaciones, carpetas médicas, feriados, notas, fotos y F/? como fuentes
  existentes que deben preservarse;
- Room MiGuardiaV2Database versión 4, archivo miguardia-v2.db y veintiséis
  tablas;
- una sola grilla mensual y un detalle consultivo por día.

No existe todavía un modelo, repositorio, DAO ni tabla de ventanas de
disponibilidad. AvailabilityLabel configura el vocabulario; no representa por
sí solo un período real.

Esquemas Room protegidos:

- SHA-256 de 1.json:
  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E;
- SHA-256 de 2.json:
  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50;
- SHA-256 de 3.json:
  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428;
- SHA-256 de 4.json:
  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de AGENTS.md:

- docs/MAPA_MAESTRO_MIGUARDIA_2_0.md;
- docs/STATUS.md;
- docs/PLANIFICACION_MIGUARDIA_2_0.md;
- docs/prompts/README.md;
- las cuatro fichas de docs/sectores/;
- docs/PROMPT_MAESTRO_MAIN_2_0.md;
- docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md;
- ADR 0018 como antecedente reemplazado;
- ADR 0019, 0020, 0022, 0023, 0025, 0026, 0027, 0028, 0029 y 0030;
- docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md;
- docs/prompts/EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md;
- docs/audits/2026-08-25-extras-independientes-y-avance-de-horas-v2.md;
- docs/PROMPT_MAESTRO_MAIN.md sólo como contrato histórico V1;
- WorkConfiguration, WorkConfigurationHistory, AvailabilityLabel y pruebas;
- WorkConfigurationRepository, implementación Room, DAO, entidades y mapeos;
- Shift, ShiftStatus, ShiftWorkSnapshot, ShiftActualAggregate,
  IndependentExtraWork, HoursProgress y pruebas;
- repositorios de jornadas, horario real, extras independientes y
  configuración;
- MedicalLeave, Vacation y sus repositorios como protecciones existentes;
- MiGuardiaV2Database, LocalDataStore, migraciones, integridad, fixtures,
  pruebas Room y esquemas 1.json a 4.json;
- WorkSetup, HoursAndExtras, detalle del día, CalendarMonthObserver,
  MainActivity, MiGuardiaApp y sus pruebas;
- recurrencias, próximo evento y notificaciones sólo como consumidores
  protegidos que no se adaptan en este bloque.

No recuperes código desde worktrees históricos. Si una fuente histórica
contradice este prompt o ADR 0030, prevalece la jerarquía activa de V2.

## DEPENDENCIES

Esta tarea depende de contratos ya integrados:

- vigencia de configuración por fecha local;
- catálogo laboral y fotografías históricas;
- jornadas manuales y recurrentes;
- horario real y fragmentos extra;
- extras independientes;
- referencia y avance de horas;
- Room V2 exclusiva con cadena 1→2→3→4.

Es la primera de tres dependencias consecutivas dentro del stage general:

1. Guardias pasivas y disponibilidad —esta tarea—;
2. Ausencias, cancelaciones y otras situaciones especiales;
3. Conteo final de horas y cumplimiento.

No adelantes la segunda ni la tercera.

## DECISIONES CONGELADAS

### 1. Un concepto y tres nombres visibles

Existe un solo concepto interno de disponibilidad. Los textos exactos son:

- Guardia pasiva;
- Disponible para llamado;
- Retén.

No son tres tipos con fórmulas distintas. La persona puede elegir
No uso disponibilidad, representado por ausencia de AvailabilityLabel.

El cambio se aplica desde una LocalDate concreta mediante la línea temporal
laboral existente:

- no reescribe revisiones ni ventanas anteriores;
- no cambia el sector;
- no cambia la referencia de horas;
- no cambia hoursReferenceStartedOn;
- no crea otra configuración laboral;
- desactivar impide nuevas cargas desde esa fecha, pero conserva la historia.

Una mutación laboral ajena conserva el nombre vigente. Una mutación del nombre
de disponibilidad conserva íntegramente referencia, marcador de reinicio y
demás campos no modificados.

### 2. Ventana de disponibilidad

La disponibilidad no es Shift, horario real, extra independiente ni situación
especial. Tiene agregado y repositorio propios.

Cada ventana conserva como mínimo:

- UUID;
- timelineId y sector;
- revisión laboral exacta aplicable;
- fecha local dueña y ZoneId;
- inicio y final como instantes exactos;
- fotografía histórica del nombre visible;
- createdAt y updatedAt normalizados a milisegundos.

Reglas:

- intervalo positivo, en minutos enteros y semántica [inicio, fin);
- puede ser pasado, actual o futuro;
- puede cruzar medianoche, mes, año y durar más de 24 horas;
- la fecha dueña es la fecha local del inicio;
- toda la ventana pertenece a esa fecha, aunque cruce otro día o período;
- el alta nace desde esa fecha en la única grilla;
- la corrección conserva la fecha dueña; para moverla se elimina y crea otra
  conscientemente;
- no pregunta recurrencia, pago, referencia, lugar, tipo ni clase extra;
- el nombre visible se resuelve por la revisión aplicable al crear y queda
  fotografiado;
- una ventana histórica conserva su nombre aunque la configuración cambie;
- una ventana pasada no necesita una confirmación diaria para ser efectiva: su
  estado temporal se deriva del reloj.

### 3. No solapar disponibilidades

Dos ventanas de disponibilidad de la misma línea temporal no pueden
superponerse. Se rechazan en dominio y persistencia antes de escribir.

Ventanas contiguas sí son válidas: el final exclusivo de una puede coincidir
con el inicio de la siguiente.

No fusionar ventanas automáticamente ni contar dos veces el mismo minuto.

### 4. Trabajo activo que reemplaza disponibilidad

La regla central es:

~~~text
Disponibilidad efectiva
= ventana programada
- unión del trabajo activo coincidente
~~~

Trabajo activo incluye:

- una jornada PLANNED usando horario real cuando existe;
- esa misma jornada usando el horario planificado cuando no existe horario
  real;
- un extra independiente válido;
- la porción ya transcurrida de una fuente en curso cuando se calcula lo
  realizado.

No se suma por separado un fragmento extra de una jornada para descontar la
disponibilidad si ya está contenido dentro del intervalo real de esa jornada.

No integra trabajo activo:

- una jornada ABSENT;
- una jornada CANCELLED;
- una jornada solamente planificada cuya fecha dueña está cubierta por
  vacaciones o carpeta médica;
- una fuente futura cuando se calcula sólo lo efectivamente transcurrido.

Si existe trabajo real declarado dentro de una protección, ese intervalo real
se conserva como actividad y puede reemplazar disponibilidad. Un extra
independiente confirmado también se conserva.

Los trabajos activos superpuestos continúan sumando completos bajo las reglas
vigentes del total laboral. Para descontar disponibilidad se usa exclusivamente
la unión temporal, por lo que el mismo minuto pasivo se reemplaza una sola vez.

No crear vínculos duplicados ni una segunda jornada para representar la
intervención. El trabajo activo sigue viviendo en su fuente actual.

### 5. Resultados temporales

El cálculo es puro, usa Clock y ZoneId inyectables y normaliza el corte al
minuto. Expone por separado, sin persistir totales:

- duración programada;
- disponibilidad efectiva ya transcurrida;
- tramo ya transcurrido reemplazado por trabajo activo;
- tramo futuro pendiente;
- disponibilidad efectiva proyectada al final de la ventana;
- tramo futuro ya ocupado por trabajo activo planificado.

Una ventana futura aporta cero a lo efectivamente transcurrido. Una ventana en
curso se corta en el minuto actual. Una ventana terminada se evalúa completa.

Una ventana cuya fecha dueña está cubierta por vacaciones o carpeta médica se
conserva y se muestra como protegida, pero no aporta disponibilidad efectiva ni
pendiente. No se presenta esa exclusión como trabajo activo reemplazante.

La disponibilidad nunca aumenta:

- trabajo habitual;
- extras;
- total trabajado;
- cumplimiento;
- faltante o superación de la referencia.

Los resultados usan aritmética exacta y detectan overflow.

### 6. Room V2 versión 5

Agregar una tabla estable availability_windows y las entidades, DAO, mapeos,
repositorio e integridad necesarios.

La migración explícita 4→5:

- crea la tabla vacía;
- preserva las veintiséis tablas y todos los datos V2;
- no crea ventanas por inferencia;
- no cambia AvailabilityLabel existente;
- no cambia referencias ni reinicios de horas;
- no usa fallback destructivo;
- no abre, copia, borra ni migra miguardia.db;
- conserva la cadena 1→2→3→4→5;
- exporta 5.json;
- no modifica 1.json, 2.json, 3.json ni 4.json.

La base queda con veintisiete tablas de aplicación.

La tabla debe declarar claves e índices suficientes para:

- identidad primaria;
- consultas por timeline, sector y fecha dueña;
- consultas por intervalo;
- relación con la raíz y la revisión laboral exacta;
- detectar solapamientos;
- proteger integridad de contexto sin depender sólo de la interfaz.

Room guarda datos fuente e historia. No guarda duraciones derivadas, uniones,
totales, porcentajes ni cumplimiento.

### 7. Fronteras de escritura, CAS y observación

Crear AvailabilityWindowRepository como frontera pública única para observar,
crear, corregir y eliminar ventanas. Los ViewModels no escriben DAO ni tablas.

Crear una mutación específica y atómica en WorkConfigurationRepository para
cambiar la disponibilidad desde una fecha. No coordinar revisiones parciales
desde la interfaz ni reutilizar una operación genérica sin control de
concurrencia.

Alta, corrección y eliminación de una ventana usan CAS por:

- registro completo observado;
- historial/revisión laboral aplicable;
- ventanas vecinas que podrían solaparse;
- fuentes activas y protecciones observadas cuando afectan la revisión previa
  al guardado.

Un conflicto no escribe nada, conserva el borrador y obliga a refrescar. Error,
reintento y doble toque no dejan filas parciales.

La lectura es reactiva. Un error de una fuente no se convierte en lista vacía,
cero horas ni ausencia de disponibilidad.

### 8. Flujo visible de configuración

Mi forma de trabajar agrega una entrada entendible para disponibilidad. Sigue el
patrón de coordinadores externos existente; no convierte WorkSetupViewModel en
dueño de la nueva persistencia.

La superficie distingue:

- LOADING;
- CONTENT, incluyendo el caso sin disponibilidad activa;
- ERROR con reintento.

Permite elegir el nombre, confirmar la fecha de vigencia y revisar el cambio
antes de guardar. La fecha puede ser pasada, actual o futura dentro de la línea
temporal V2. Una retrocarga explica qué tramo histórico pasa a usar el nombre,
sin crear ventanas ni reescribir fotografías.

El borrador, fecha, expectativa, etapa y confirmaciones sobreviven a recreación
mediante SavedStateHandle.

### 9. Flujo visible de una ventana

Desde el detalle de un día existe la acción Registrar disponibilidad sólo si la
revisión de esa fecha tiene un AvailabilityLabel.

Secuencia mínima:

1. conservar la fecha elegida en el Calendario;
2. cargar inicio y final exactos;
3. mostrar duración y nombre histórico;
4. advertir un solapamiento y bloquear el guardado;
5. revisar;
6. confirmar;
7. volver al mismo detalle actualizado.

El detalle muestra nombre, inicio, final, duración programada y desglose
temporal. Ofrece Corregir disponibilidad y Eliminar disponibilidad como
acciones separadas. Eliminar exige confirmación con la fotografía exacta.

Abrir, escribir, revisar, volver o cancelar no persiste. Guardar, corregir o
eliminar cierra sólo su superficie y conserva la fecha y el mes consultados.

La fecha dueña, horario, ventana observada, vecinos, revisión, confirmaciones y
etapa sobreviven a recreación mediante SavedStateHandle.

### 10. Integración mínima con Calendario y Horas

Mantener una sola grilla mensual. No reutilizar CalendarInteractionMode.EDIT,
Cargar jornadas ni un segundo selector mensual.

La celda del día de inicio muestra un indicador textual y accesible mínimo. El
detalle contiene la información completa. No terminar todavía el diseño final
de las celdas ni la tarjeta superior.

El Calendario observa ventanas directamente, además del cálculo de horas. Un
error del motor no puede ocultar registros válidos.

La superficie Horas y extras puede mostrar el desglose funcional de
disponibilidad programada, efectiva, reemplazada y pendiente/proyectada. No
construye tarjetas configurables ni anticipa el Resumen final.

Crear, corregir, eliminar o cambiar una fuente activa actualiza reactivamente
las superficies visibles.

### 11. Accesibilidad y límites visuales

- scroll e IME utilizables;
- tags únicos por UUID;
- ninguna distinción depende sólo del color;
- texto que distingue disponibilidad, trabajo y reemplazo;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- no consultar ni modificar font_scale, densidad o tamaño visual del sistema;
- mensajes en español claro, sin fórmulas laborales o legales.

## OUTPUT

La dependencia debe entregar:

- dominio puro de ventanas y reemplazo por unión de trabajo activo;
- configuración visible y versionada del nombre;
- agregado, repositorio y persistencia propios;
- migración explícita 4→5 y esquema 5.json;
- integración mínima con Calendario, detalle del día y Horas y extras;
- borradores, errores, CAS y recreación;
- pruebas puras, JVM, Room instrumentadas, Compose y Activity;
- handoff verificable a MAIN con diff sin commit.

La documentación canónica, ADR, índice, STATUS y auditoría final pertenecen a
MAIN y no se modifican desde esta dependencia.

## SCOPE

Puede modificar solamente lo necesario dentro de:

- core/domain/src/main/** y core/domain/src/test/**;
- core/database/src/main/**;
- core/database/src/test/**;
- core/database/src/androidTest/**;
- core/database/schemas/** únicamente para 5.json;
- app/src/main/**;
- app/src/test/**;
- app/src/androidTest/**.

Puede adaptar proyecciones del Calendario, detalle del día y la vista funcional
de Horas y extras sólo para incluir disponibilidad. No rediseñar superficies
vecinas.

## DO NOT

No:

- modificar AGENTS.md, docs/** ni fuentes canónicas;
- cambiar los cuatro rubros ni crear Salud u Otro;
- unir Enfermería y Medicina;
- imponer 204 horas, lunes, nocturnidad o reglas por sector;
- convertir disponibilidad en Shift, horario real o extra;
- sumar disponibilidad a trabajo o cumplimiento;
- descontar dos veces un minuto pasivo;
- permitir, fusionar o guardar disponibilidades solapadas;
- preguntar pago, referencia, lugar, tipo o recurrencia al crearla;
- crear recurrencia de disponibilidades;
- implementar ausencias o cancelaciones nuevas;
- implementar capacitación, intercambio, cobertura u otra situación;
- duplicar vacaciones o carpetas médicas;
- guardar adjuntos o comprobantes;
- consolidar todavía el motor final de horas;
- crear el Resumen personalizable;
- terminar el Calendario o la tarjeta superior;
- adaptar próximo evento, notificaciones o widget;
- implementar agenda profesional, pacientes o Psicología;
- agregar dependencias de producción;
- modificar Gradle, manifiesto, permisos, applicationId, versión o SDK;
- tocar DataStore salvo una necesidad demostrada y autorizada por MAIN;
- modificar 1.json, 2.json, 3.json o 4.json;
- conectar con Room V1 o miguardia.db;
- usar fallback destructivo;
- acceder a red, cuentas, nube, telemetría o datos reales;
- registrar datos privados en logs;
- abrir, instalar, limpiar o desinstalar producción;
- consultar o modificar font_scale, densidad o tamaño visual del sistema;
- crear commit, push, tag, merge, rebase, reset o descarte;
- crear otra tarea, rama, worktree o subagente.

Ante una necesidad real fuera de alcance, detenete y devolvé a MAIN el punto
exacto. No inventes una extensión.

## VALIDATION

### 1. Dominio JVM

Cubrir como mínimo:

- tres nombres visibles y ausencia de disponibilidad;
- cambio desde fecha pasada, actual y futura sin reiniciar horas;
- intervalo positivo y minutos enteros;
- medianoche, fin de mes/año, febrero bisiesto y más de 24 horas;
- fecha dueña por inicio local;
- ventanas contiguas válidas;
- solapamiento parcial, total, contenido e idéntico rechazados;
- ventana pasada, en curso y futura;
- jornada planificada sin real;
- jornada real distinta de la planificada;
- fragmentos extra que no duplican el intervalo real;
- extra independiente;
- ABSENT y CANCELLED excluidos;
- vacaciones y carpeta médica;
- trabajo real dentro de una protección conservado;
- dos trabajos activos solapados unidos sólo para descontar disponibilidad;
- actividad antes, dentro, después y cruzando los límites de la ventana;
- programada, efectiva, reemplazada, pendiente y proyectada;
- disponibilidad ausente del total, cumplimiento, faltante y superación;
- Clock, ZoneId, minuto normalizado y overflow.

### 2. Room

Verificar:

- migración 4→5 desde una base 4 poblada en sus veintiséis tablas;
- cadena 1→2→3→4→5;
- 1.json, 2.json, 3.json y 4.json byte a byte intactos;
- 5.json exportado y SHA-256 informado;
- veintisiete tablas exactas;
- alta, consulta, corrección, eliminación y reapertura;
- ventanas pasadas, actuales, futuras y multidiarias;
- rechazo persistente de solapamientos;
- ventanas contiguas;
- fotografía histórica y cambio posterior de nombre;
- relaciones de configuración y revisión exactas;
- mutación atómica de AvailabilityLabel preservando referencia y reinicio;
- rollback total, doble toque y conflicto CAS;
- integrity_check y foreign_key_check;
- rechazo de huérfanos, cruces de timeline/sector e intervalos inválidos;
- Flows reactivos;
- preservación de todos los datos de Room 4.

### 3. App JVM, Compose y Activity

Cubrir como mínimo:

- entrada visible en Mi forma de trabajar;
- LOADING, CONTENT, ERROR y reintento;
- tres nombres exactos y No uso disponibilidad;
- fecha de vigencia y revisión antes de guardar;
- referencia y hoursReferenceStartedOn intactos;
- registrar, revisar, guardar, corregir y eliminar;
- acción ausente cuando la revisión no usa disponibilidad;
- fecha proveniente de la única grilla y ausencia de segundo calendario;
- solapamiento bloqueado y ventanas contiguas aceptadas;
- borrador y recreación en todas las etapas;
- error, rollback, reintento, doble toque y conflicto;
- regreso al mismo Calendario/detalle actualizado;
- indicador textual y descripción accesible;
- cálculo recalculado tras cambiar horario real, jornada o extra;
- convivencia con vacaciones, carpeta médica, F/?, notas y feriados;
- regresión de carga, edición, recurrencias, horario real y extras;
- ausencia de situaciones nuevas, Resumen final y adaptación de avisos;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 %;
- accesibilidad sin depender sólo del color.

### 4. Batería local serializada

Ejecutar desde la raíz:

~~~powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:domain:test :core:database:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleQa :app:assembleQaAndroidTest :core:database:assembleDebugAndroidTest
~~~

Obtener conteos reales desde XML y distinguir:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

Ejecutar además:

- git diff --check;
- búsqueda de escritores directos de Shift y availability_windows;
- búsqueda de fallbackToDestructiveMigration;
- comparación/hash de 1.json a 4.json;
- auditoría de secretos, logs, red, permisos, Gradle y manifiesto.

### 5. Android y dispositivos

Compilar AndroidTest es obligatorio pero no equivale a ejecutarlo.

La dependencia sólo puede usar Samsung QA o emulador API 26 si Joaquin lo
autoriza expresamente en esa tarea. Con autorización:

- usar únicamente paquetes QA/test y datos ficticios;
- no abrir ni modificar producción;
- probar migración, Room, configuración, CRUD, cálculo, recreación y
  regresiones;
- recorrer ventana pasada, actual y futura, solapamiento y trabajo coincidente;
- revisar claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- no consultar ni modificar ajustes visuales del sistema;
- desinstalar sólo los paquetes QA autorizados al finalizar;
- informar exactamente qué quedó en cada dispositivo.

Si no existe autorización o un dispositivo no está disponible, marcar la
evidencia como PENDIENTE. No presentar APK compilado como QA física.

## HANDOFF A MAIN

Entregar en español, de forma compacta y verificable:

~~~text
# HANDOFF A MAIN — Guardias pasivas y disponibilidad V2

## QUÉ HACE
## POR QUÉ EXISTE
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
~~~

Incluir:

- ruta, rama, HEAD de entrada y upstream;
- archivos modificados, nuevos y eliminados;
- conteos reales de pruebas;
- qué fue sólo compilado y qué se ejecutó;
- versión Room, tablas, migración y SHA-256 de esquemas;
- paquetes usados y estado final de dispositivos;
- límites no implementados;
- git status, git diff --check y confirmación de cero staged;
- confirmación de que no hubo commit, push, tag, merge, rebase, reset ni
  descarte.

El resultado queda directamente en el checkout compartido, sin commit. No
existe nada para cherry-pick. MAIN audita cada hunk, repite pruebas
proporcionales, encarga una revisión independiente y decide el checkpoint.

## DONE WHEN

La dependencia se considera candidata sólo cuando:

- la persona puede elegir si usa disponibilidad y uno de los tres nombres;
- el cambio rige desde una fecha sin reiniciar ni alterar la referencia;
- puede crear, ver, corregir y eliminar una ventana exacta;
- dos ventanas solapadas se rechazan y las contiguas se aceptan;
- la fotografía histórica no cambia por una edición posterior;
- el trabajo activo reemplaza sólo su intersección;
- dos trabajos activos solapados descuentan una sola vez;
- disponibilidad pasada, actual y futura se distinguen con reloj inyectable;
- la disponibilidad nunca aumenta trabajo ni cumplimiento;
- vacaciones, carpeta médica, ausencia y cancelación conservan sus reglas;
- Room migra de 4 a 5 sin perder datos y con esquemas anteriores intactos;
- escrituras y correcciones son atómicas y protegidas por CAS completo;
- Calendario, Horas y extras, borradores, errores y recreación están cubiertos;
- la batería local requerida está verde;
- la evidencia Android se ejecutó con autorización o quedó marcada
  honestamente como pendiente;
- no se implementaron situaciones especiales, motor final, Resumen ni otro
  bloque futuro;
- el diff está limpio de whitespace, sin staged y sin cambios fuera de alcance;
- el handoff vuelve a MAIN sin commit ni push.
