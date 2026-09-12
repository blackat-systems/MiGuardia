# Próximo evento y notificaciones V2

- Estado: **CERRADO — IMPLEMENTADO, AUDITADO Y VERIFICADO POR MAIN**
- Fecha: 2026-08-27
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada y publicada:
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Próximo evento y avisos**

## QUÉ HACE

Hace que MiGuardia use una sola regla para decidir qué trabajo está ocurriendo
ahora, cuál viene después y qué aviso local corresponde mostrar.

Comprende jornadas manuales, jornadas creadas por una repetición y los tramos
efectivos de Guardia pasiva, Disponible para llamado o Retén. La tarjeta
superior y las notificaciones dejan de poder contradecirse entre sí.

## POR QUÉ EXISTE

La tarjeta actual ya reconoce jornadas, vacaciones, carpetas médicas y horario
real, pero todavía no incorpora disponibilidad. El sistema heredado de avisos
quedó más atrás: observa principalmente jornadas y vacaciones, por lo que una
carpeta médica, un horario real ya registrado o una disponibilidad pueden no
reconciliarse de la misma manera.

Esta dependencia cierra esa diferencia antes de la auditoría integral del
núcleo. Reutiliza el motor local probado, sus preferencias, privacidad,
canales y alarmas; no crea un sistema paralelo.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, el Calendario, la persistencia V2 ni
la secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

No comiences con una reimplementación. Primero auditá el motor de próximo
evento, Pulso Vigilia, los avisos locales y sus pruebas actuales. Conservá lo
que ya cumple este contrato y modificá sólo lo necesario para volverlo V2.

## TASK

Adaptar integralmente **Próximo evento y notificaciones** para que ambos
consumidores compartan una única proyección V2 pura, tipada y verificable.

El recorrido mínimo debe permitir:

1. mostrar en la tarjeta una jornada activa, una disponibilidad efectiva
   activa o el próximo evento laboral real;
2. conservar la lista final de jornadas de hoy y sus estados ya integrada;
3. mostrar el nombre histórico exacto de la disponibilidad;
4. programar avisos locales de jornadas y disponibilidad con las preferencias
   globales existentes;
5. conservar las excepciones particulares existentes únicamente para
   jornadas;
6. cancelar o reprogramar avisos cuando se edita, cancela, elimina o protege
   una fuente;
7. impedir avisos atrasados cuando el trabajo real ya fue registrado;
8. respetar permiso, acceso exacto, privacidad, atención, sonido, ocultamiento
   y restauración;
9. degradar honestamente a una alarma inexacta cuando Android no conceda acceso
   exacto;
10. conservar funcionamiento sin red, cuenta, nube, sincronización, polling o
    servicio en primer plano.

No implementes widget, informes, copias, bloqueo, Ayuda ni situaciones
especiales nuevas.

## CONTEXT

La base cerrada ya posee:

- una experiencia exclusivamente V2 y una instalación inicial limpia;
- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola configuración laboral con vigencia desde fechas concretas;
- una sola grilla mensual y una tarjeta superior final desplegable;
- jornadas manuales y jornadas concretas materializadas por recurrencias;
- el par obligatorio `Shift + ShiftWorkSnapshot`;
- horario planificado inmutable y horario real opcional;
- extras exactas de jornada y extras independientes ya realizados;
- vacaciones, carpetas médicas, feriados, notas, `F/?`, ausencias y
  cancelaciones ya representadas en sus alcances actuales;
- Guardia pasiva, Disponible para llamado o Retén como ventanas propias, con
  tramos efectivos derivados al restar la unión del trabajo activo;
- Resumen personalizable cerrado;
- motor puro `NextEvent`, observador reactivo y tarjeta `TodayCardProjection`;
- preferencias, planificador, reconciliador, alarmas, receptores, presentación,
  privacidad, ocultamiento y restauración de avisos heredados;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db` y veintisiete
  tablas.

Brechas verificadas antes de escribir este contrato:

- `NextEvent` sólo modela jornada, franco o vacío;
- `NextEventObserver` todavía no observa disponibilidad ni consume el par V2
  completo para presentar el tipo histórico;
- el planificador, reconciliador y receptor de avisos todavía reciben una
  visión centrada en `Shift` y vacaciones;
- carpeta médica, horario real y disponibilidad no forman hoy un mismo
  contexto obligatorio de elegibilidad para cada consumidor;
- la identidad de alarmas, avisos visibles y avisos ocultados es todavía
  exclusiva de una jornada;
- quedan textos históricos como `Guardia` u `Objetivo` que no sirven como
  vocabulario genérico para los cuatro rubros;
- la excepción particular de avisos existe y se guarda, pero su acceso desde
  el detalle V2 debe quedar nuevamente alcanzable.

Las jornadas generadas por recurrencias ya son jornadas concretas: no leas los
patrones recurrentes como una segunda fuente. Los extras independientes
representan trabajo ya realizado y no son candidatos futuros. Cuando la
planificación llama `extra` a un trabajo futuro, este bloque la interpreta
como una jornada concreta cuyo tipo histórico fue elegido por la persona; no
inventa otra entidad.

Esquemas Room protegidos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

El `identityHash` de Room 5 es
`77adbc875d0f4ee466cdbd0dd74d5c5c`.

Última evidencia verde heredada, que no reemplaza tu propia validación:

- JVM: 488/488;
- lint: 0 errores;
- Samsung `SM-S938B`, API 36: Room 107/107;
- Samsung: aplicación 224/224 ejecutadas correctamente y una prueba histórica
  de alarma exacta omitida por su propio contrato;
- Room V2 versión 5, sus veintisiete tablas y esquemas 1 a 5 intactos.

## PUERTA 0 OBLIGATORIA

Antes de modificar cualquier archivo:

1. leé completas y en el orden de `AGENTS.md` todas las fuentes obligatorias;
2. verificá en vivo ruta, rama, HEAD, upstream, base protegida, limpieza,
   worktrees, remoto privado y autor Git;
3. confirmá que este prompt figure `HABILITADO` en
   `docs/prompts/README.md`;
4. confirmá que el HEAD exacto informado por MAIN contiene a
   `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce` como ancestro;
5. confirmá que no existe otra dependencia implementadora sobre el checkout.

Comandos mínimos de sólo lectura:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse @{upstream}
git merge-base --is-ancestor ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce HEAD
git rev-parse v1.0.0^{}
git status --short --branch
git worktree list --porcelain
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
git remote get-url origin
git config user.name
git config user.email
```

Condiciones obligatorias:

- ruta exacta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama exacta: `codex/miguardia-2.0`;
- autor efectivo: `joaquin <blackat.systems@gmail.com>`;
- `v1.0.0^{}` continúa en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- checkout inicial limpio;
- `main`, `origin/main`, el tag y los worktrees históricos intactos.

Detenete ante rama distinta, detached HEAD, base ausente, cambios sin dueño,
autor incorrecto, prompt no habilitado o contradicción material. No uses
`reset`, `checkout`, `stash`, `clean` ni descartes para forzar la puerta. No
inspecciones ni recuperes código desde worktrees históricos.

## INPUTS

Leé como mínimo, además de las fuentes obligatorias de `AGENTS.md`:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0009, 0010, 0011, 0015, 0020, 0026, 0027, 0028, 0029, 0030,
  0031 y 0032;
- `docs/PROMPT_MAESTRO_MAIN.md` sólo como base histórica no reemplazada;
- `docs/prompts/MOTOR_DE_PROXIMO_EVENTO.md`,
  `docs/prompts/NOTIFICACIONES.md` y
  `docs/prompts/NOTIFICACIONES_PULSO_VIGILIA.md` sólo como contratos
  históricos que deben adaptarse, no reejecutarse;
- `docs/audits/2026-08-18-notificaciones-visibilidad.md`;
- los prompts y auditorías finales de recurrencias, horario real, extras,
  disponibilidad, Calendario final y Resumen;
- `NextEvent`, `TodayCardProjection`, su observador, estado, ViewModel, tarjeta
  y todas sus pruebas;
- `ShiftNotificationPlan`, preferencias, acceso del sistema, reconciliador,
  scheduler, receivers, runtime, presenter, visibilidad, pantallas y pruebas;
- `V2ShiftWrite`, `ShiftWorkSnapshot`, `ShiftActualAggregate`, estados diarios,
  vacaciones y carpetas médicas;
- `AvailabilityWindowRecord`, su cálculo puro, repositorio, DAO y pruebas;
- configuración laboral e historia de los cuatro sectores;
- `MainActivity`, `MiGuardiaApplication`, `MiGuardiaApp`, manifiesto y recursos
  de notificación;
- la documentación oficial vigente de Android sobre permisos de notificación,
  alarmas exactas y canales.

Referencias oficiales mínimas:

- permiso de notificaciones en Android 13 y posteriores:
  <https://developer.android.com/develop/ui/compose/notifications/notification-permission>;
- alarmas y acceso exacto:
  <https://developer.android.com/develop/background-work/services/alarms>;
- cambios de acceso exacto desde Android 14:
  <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>;
- canales de notificación:
  <https://developer.android.com/develop/ui/compose/notifications/channels>.

La fuente histórica V1 no puede reintroducir Perfil, Novedades, migración V1,
datos salariales ni vocabulario exclusivo de Vigilancia.

## DEPENDENCIES

Esta tarea depende de contratos ya cerrados:

- configuración laboral e historia por fecha;
- catálogo y fotografías históricas;
- carga, edición y recurrencias de jornadas;
- horario real y extras exactas;
- extras independientes y avance de horas;
- disponibilidad y su cálculo de tramos efectivos;
- Calendario final y tarjeta superior;
- Resumen personalizable;
- Room V2 exclusiva con cadena `1→2→3→4→5`;
- sistema local de avisos, privacidad, Pulso Vigilia y Clima opcional ya
  preservados.

Debe dejar un contrato único y reutilizable para el widget futuro. No debe
implementar ese widget.

## DECISIONES CONGELADAS

### 1. Una sola proyección laboral V2

Dominio publica una proyección pura, inmutable y tipada de eventos laborales.
La misma elegibilidad alimenta la tarjeta superior y el plan de avisos. No se
permiten dos copias de prioridades, protecciones o disponibilidad.

La proyección recibe `Clock`/instante y `ZoneId` explícitos. Usa intervalos
`[inicio, fin)`, orden determinista y no consulta Android, Room ni el reloj por
su cuenta.

La identidad interna diferencia, como mínimo:

- jornada + UUID;
- disponibilidad + UUID de ventana + límites del tramo efectivo;
- franco explícito sólo informativo.

Una identidad no puede colisionar con otra por compartir UUID o entero de
notificación.

### 2. Fuentes autorizadas

Las jornadas candidatas son los pares V2 completos
`Shift + ShiftWorkSnapshot`:

- creados manualmente o materializados por recurrencia;
- estado `PLANNED`;
- con final posterior al instante evaluado;
- no protegidos por vacaciones o carpeta médica;
- sin horario real ya confirmado.

`CANCELLED` y `ABSENT` no son eventos futuros ni generan avisos. `F` y `?`
pueden conservar su presentación actual en Calendario, pero no generan
notificaciones.

`Shift.startAt` y `Shift.endAt` continúan siendo la planificación histórica y
no se reescriben con el horario real. Como el flujo V2 registra horario real
sólo para trabajo ya realizado, ese registro invalida fronteras planificadas
pendientes y evita avisos atrasados; no se programan alarmas contra el horario
real.

Las recurrencias se consumen sólo mediante sus jornadas materializadas. Los
extras independientes son trabajo pasado: no son próximo evento ni aviso
futuro. Sí participan, junto con el trabajo activo correspondiente, al derivar
qué tramo de disponibilidad fue reemplazado.

### 3. Disponibilidad efectiva

La disponibilidad no se convierte en jornada ni en horas trabajadas. Reutilizá
su motor puro existente para obtener los segmentos efectivos después de restar
la unión del trabajo activo y aplicar protecciones.

Cada segmento conserva la fotografía histórica del nombre exacto:

- `Guardia pasiva`;
- `Disponible para llamado`;
- `Retén`.

Una ventana totalmente reemplazada o protegida no produce evento ni aviso. Si
una jornada reemplaza sólo un tramo, la disponibilidad desaparece únicamente
en ese tramo y puede volver a estar activa después.

Una reanudación derivada por el fin del trabajo activo actualiza o restaura el
aviso de manera silenciosa. No crea una secuencia invasiva de recordatorios
por cada fragmento. Los recordatorios anticipados se asocian al comienzo
efectivo que realmente corresponda y nunca se emiten retroactivamente.

### 4. Prioridad y simultaneidad

Para un mismo instante, la proyección prioriza:

1. jornadas activas;
2. disponibilidad efectiva activa;
3. el comienzo futuro más cercano entre jornadas y disponibilidad;
4. el próximo franco explícito, sólo para la tarjeta;
5. vacío.

En igualdad temporal, una jornada queda antes que una disponibilidad. Dentro
del mismo tipo, ordenar por inicio, fin e identidad estable. Conservar listas
de eventos simultáneos: elegir un principal no autoriza a descartar los demás.

La lista desplegable de jornadas de hoy sigue siendo una lista de jornadas y
su contador no aumenta por disponibilidad. La tarjeta puede presentar además
la disponibilidad activa o el próximo evento adjunto sin disfrazarlo de
jornada.

### 5. Fotografía laboral y DTO seguro

La proyección usa el par V2 para conservar tipo, lugar, abreviatura, horario,
color y puesto históricos. No resuelve un catálogo actual para reinterpretar
el pasado.

Los DTO de tarjeta y aviso contienen solamente los campos necesarios. No
transportan el objeto `Shift` completo si con ello arrastran dirección, puesto
u otros campos hacia superficies que no los necesitan.

La zona inyectada gobierna también francos, medianoche y textos temporales. No
consultar una zona global distinta desde Compose.

### 6. Plan de avisos y reconciliación

El plan de avisos es puro y determinista. Sus fronteras continúan siendo:

- `REMINDER` para anticipaciones válidas;
- `START` para el inicio efectivo;
- `END` para limpiar el aviso y su seguimiento.

Conservá el comportamiento de alerta que ya corresponde a las fronteras
reales y al ritmo elegido. Reconciliaciones, refrescos de clima, restauraciones
derivadas y reanudaciones de disponibilidad son silenciosos. No inventes
alarmas por minuto.

Cada alarma es reconstruible. Antes de mostrar algo, el receiver vuelve a leer
la fuente y valida el contexto V2 completo. Un intent, una alarma instalada o
un payload no son fuente de verdad.

Editar, cancelar o eliminar una jornada; registrar horario real; agregar una
protección; editar o eliminar disponibilidad; cambiar preferencias; o cruzar
una frontera debe cancelar lo obsoleto y programar sólo lo faltante, de forma
idempotente.

### 7. Preferencias y excepciones

Las preferencias globales existentes se aplican a jornadas y disponibilidad:

- habilitación consciente;
- entre cero y cinco anticipaciones positivas y únicas;
- precisión solicitada;
- fijo o descartable;
- privacidad;
- modo de atención, sonido y ritmo;
- ocultamiento y restauración.

Las excepciones particulares existentes continúan únicamente por jornada. No
agregues una tabla ni una pantalla de excepción por ventana de disponibilidad.

Generalizá el tracking de DataStore a identidades tipadas sin revivir avisos
viejos. Un UUID antiguo sin prefijo se interpreta como jornada y puede
normalizarse al escribir nuevamente. Conservá preferencias visibles y avisos
ocultados existentes.

El detalle V2 de una jornada vuelve a ofrecer un acceso alcanzable a su
configuración particular. No mezcles esa acción con edición estructural de la
jornada.

### 8. Vocabulario, privacidad y acciones

Para los cuatro rubros usá `Jornada` como término genérico. El contenido
completo de una jornada puede mostrar, según la fotografía histórica:

- estado;
- horario;
- tipo de trabajo;
- lugar y abreviatura;
- puesto cuando el contrato actual lo permita;
- clima cacheado sólo cuando ya estaba permitido y tiene sentido para ese
  lugar.

La disponibilidad usa su etiqueta histórica y horario. No muestra lugar,
puesto ni clima.

Privacidad:

- completa: contenido laboral autorizado;
- reducida: estado y horario, sin lugar ni puesto;
- oculta: aviso genérico de MiGuardia.

Nunca incluir notas, motivos médicos, explicaciones privadas de horario real,
fotos, direcciones en texto, pacientes, terceros ni datos personales. La causa
de una protección nunca se revela.

Acciones:

- jornada: `Ver detalles` y `Cómo llegar` sólo si el contrato actual puede
  revalidar una dirección histórica;
- disponibilidad: `Ver detalles`, abriendo su fecha dueña;
- no reintroducir `Informar novedad`, porque pertenecía al flujo V1 retirado.

Ocultar cancela sólo esa identidad y evita que el reconciliador la reviva.
Restaurar exige revalidarla y no vuelve a alertar de forma invasiva.

### 9. Android, permiso y alarmas exactas

Conservá `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` y
`RECEIVE_BOOT_COMPLETED` ya declarados. No agregues `USE_EXACT_ALARM`, servicio
en primer plano, WorkManager, polling, full-screen intent ni interfaz de alarma
despertador.

En Android 13 y posteriores, permiso runtime concedido y notificaciones de la
aplicación habilitadas son condiciones distintas. La interfaz explica el
estado real sin bucles de solicitud.

Sólo usar alarma exacta cuando la persona la pidió y
`canScheduleExactAlarms()` lo permite. En caso contrario, conservar
`setAndAllowWhileIdle` y explicar que Android puede demorar el aviso. La app
sigue siendo útil si se rechaza cualquiera de los dos accesos.

Los canales son deterministas y Android conserva el control final. No intentes
mutar sonido o vibración de un canal ya creado. Preservá el prefijo propio y
las personalizaciones existentes; sólo se pueden retirar canales antiguos que
pertenezcan inequívocamente a MiGuardia.

Conservá `minSdk 26`, cronómetro nativo, agrupación, vista pública y degradación
compatible de `RemoteViews`.

### 10. Reactividad, errores y ciclo de vida

El observador combina todos los sectores presentes en la historia laboral, no
sólo el vigente hoy. Reacciona a fuentes y fronteras temporales, incluido
cambio de día y zona, sin polling residente.

Tarjeta y configuración conservan estados de carga, contenido y error
recuperable. Un error parcial puede mantener el último resultado válido del
mismo día, expresarlo y ofrecer reintento; nunca reproduce el resultado de otra
fecha ni convierte una fuente desconocida en vacío confirmado.

Cuando la superficie deja de observar, sus esperas y fuentes se cancelan. El
runtime de notificaciones conserva únicamente las observaciones necesarias
para reconciliar avisos mientras el proceso vive y los receivers/rebuilds
reconstruyen desde la fuente al despertar.

## OUTPUT

Entregá directamente en el checkout compartido, sin commit:

- proyección V2 pura y tipada compartida;
- adaptación de tarjeta, observación y estados;
- plan, tracking, scheduler, receiver, reconciliación y presentación V2;
- acceso alcanzable a excepciones particulares de una jornada;
- pruebas de dominio, JVM, DataStore, Compose, Activity y Android/Room
  proporcionales;
- cualquier consulta reactiva adicional estrictamente necesaria sin cambiar
  el esquema;
- un handoff completo a MAIN.

La documentación canónica, ADR, STATUS, índice de prompts, auditoría final y
checkpoint quedan reservados a MAIN.

## SCOPE

Áreas permitidas cuando sean necesarias:

- `core/domain/**/nextevent/**`;
- `core/domain/**/notification/**`;
- modelos V2 sólo para DTO o identidad de lectura, sin alterar persistencia;
- repositorios públicos y consultas de sólo lectura de jornadas,
  disponibilidad, horario real y protecciones;
- `app/**/ui/nextevent/**`;
- `app/**/ui/notifications/**`;
- `app/**/notifications/**`;
- integración mínima en `MainActivity`, `MiGuardiaApplication` y
  `MiGuardiaApp`;
- recursos de interfaz o notificación existentes;
- pruebas vecinas en app, dominio y base local.

Si necesitás tocar una ruta no listada, justificá por qué es indispensable y
mantenela dentro de esta única dependencia.

## DO NOT

No:

- crear otra grilla, otro calendario ni otra tarjeta superior;
- reinterpretar patrones recurrentes además de sus jornadas materializadas;
- convertir extras independientes en eventos futuros;
- convertir disponibilidad en jornada u horas trabajadas;
- reescribir `Shift.startAt/endAt` con horario real;
- inventar `Salud`, `Otro` ni un quinto rubro;
- unir Medicina y Enfermería;
- introducir cuentas, red nueva, nube, sincronización, analítica o telemetría;
- agregar salarios, montos, liquidaciones, deducciones o datos sindicales;
- recuperar Perfil, Novedades, Objetivos V1 ni `Informar novedad`;
- implementar ausencias/cancelaciones nuevas ni otras situaciones especiales;
- modificar Resumen, widget, informes, copias, bloqueo o Ayuda;
- cambiar Room v5, entidades, migraciones o esquemas 1–5;
- agregar dependencias, permisos, componentes de manifiesto, SDK,
  `applicationId`, versión o paquete;
- usar `fallbackToDestructiveMigration` o `allowMainThreadQueries`;
- usar WorkManager, alarmas repetitivas, polling, servicio en primer plano,
  alarma despertador o pantalla completa;
- consultar o modificar `font_scale`, densidad o tamaño visual del sistema;
- usar datos reales;
- instalar, abrir, limpiar o desinstalar producción;
- tocar el Samsung o un emulador sin una autorización expresa nueva de
  Joaquin para esta dependencia;
- modificar documentación canónica;
- crear commit, push, tag, merge, rebase, reset, rama, worktree, tarea o
  subagente.

## VALIDATION

### 1. Revisión estática

Revisá cada hunk y confirmá:

- una sola proyección de elegibilidad y prioridad;
- contexto obligatorio: sin parámetros opcionales que permitan omitir
  silenciosamente carpeta médica, horario real o disponibilidad;
- DTO seguros y fotografías históricas correctas;
- ausencia de escritores estructurales nuevos;
- Room, esquemas, Gradle, manifiesto, permisos y dependencias intactos;
- sin secretos, datos privados, logs sensibles, red nueva ni patrones
  destructivos;
- `git diff --check` limpio.

### 2. Batería local serializada

Ejecutá desde el estado final:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Obtené conteos reales desde los XML. Diferenciá expresamente:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

### 3. Pruebas obligatorias de dominio

Cubrir, como mínimo:

- jornada manual y ocurrencia recurrente exactamente una vez;
- fotografía histórica de tipo, lugar, abreviatura y horario;
- disponibilidad futura, activa, totalmente reemplazada y dividida;
- reanudación después de trabajo activo;
- vacaciones y carpeta médica;
- horario real ya registrado sin aviso atrasado;
- cancelación, ausencia, edición y eliminación;
- extra independiente excluido como candidato futuro;
- sectores históricos distintos;
- simultáneos y empates con orden estable;
- límites `[inicio, fin)`;
- medianoche, cambio de mes/año, febrero bisiesto y zona explícita;
- entradas y listas inmutables;
- igualdad entre el evento elegido por tarjeta y el plan de avisos;
- cero recordatorios, hasta cinco, duplicados, negativos y excepción vacía;
- fragmentación de disponibilidad sin alerta repetitiva.

### 4. Pruebas de aplicación y Android

Cubrir, como mínimo:

- observación reactiva de todas las fuentes y sectores;
- error, último resultado válido del mismo día, reintento y cancelación;
- cambio de fecha/zona y recreación;
- permiso de notificación denegado y concedido;
- notificaciones de la app deshabilitadas desde Android;
- acceso exacto denegado y concedido, con fallback inexacto;
- edición/eliminación/protección que cancela alarmas y avisos obsoletos;
- cambio de disponibilidad y reanudación silenciosa;
- aviso ocultado que no reaparece y restauración revalidada;
- varias jornadas y disponibilidades sin colisiones;
- privacidad completa, reducida y oculta;
- acciones correctas y ausencia de `Informar novedad`;
- agrupación, cronómetro, canales, ritmo, sonido y notificación de prueba;
- detalle particular de una jornada nuevamente alcanzable;
- claro/oscuro, retrato/paisaje, zoom interno 100/150/200 y accesibilidad no
  dependiente sólo del color.

### 5. Matriz de dispositivos

La compilación de AndroidTest no reemplaza su ejecución. La dependencia no
puede usar dispositivos hasta recibir autorización expresa nueva de Joaquin.

Con esa autorización:

- Samsung `SM-S938B`, API 36: suite afectada completa, revisión visual y un
  recorrido corto con una jornada, disponibilidad, superposición, edición,
  protección, ocultamiento y restauración;
- API 26: canales, `RemoteViews`, cronómetro, scheduler, receivers, rebuild y
  navegación;
- API 33 o superior: instalación QA limpia, denegar/conceder
  `POST_NOTIFICATIONS` y fallback sin acceso exacto;
- API 37: queda como puerta transversal antes del candidato final; si está
  disponible durante este bloque, ejecutar al menos presenter, permisos y
  regresiones de vistas personalizadas.

Una alarma exacta real y un reinicio físico del Samsung son acciones separadas:
no las ejecutes sin que Joaquin las autorice expresamente en ese momento.

Usá exclusivamente paquetes QA/test y datos ficticios. No consultes ni
modifiques `font_scale`, densidad o tamaño visual del sistema. Restaurá
orientación, permisos temporales y estado del dispositivo; desinstalá sólo los
paquetes QA/test usados e informá exactamente qué quedó. Producción permanece
intacta.

## HANDOFF A MAIN

La entrega final debe comenzar con:

```text
# HANDOFF A MAIN — Próximo evento y notificaciones V2
```

Y contener, en este orden:

1. `QUÉ HACE`;
2. `POR QUÉ EXISTE`;
3. `OBJECTIVE`;
4. `CHANGES`;
5. `FILES` —modificados, nuevos y eliminados—;
6. `DECISIONS`;
7. `VALIDATION` —comandos, conteos y niveles reales—;
8. `ROOM` —versión, tablas y hashes—;
9. `PHYSICAL QA`;
10. `DEVICE SAFETY`;
11. `RISKS`;
12. `PENDING`;
13. `GIT` —ruta, rama, HEAD, upstream, estado y staged—;
14. `NEXT`.

No presentes compilación como instrumentación. No digas que algo fue revisado
físicamente si sólo lo cubrió un test. Enumerá cualquier prueba omitida y su
precondición exacta.

Entregá el candidato sin commit, sin push y directamente en el checkout
compartido. No existe nada para `cherry-pick`; MAIN audita el diff real.

## DONE WHEN

La dependencia está terminada únicamente cuando:

- tarjeta y avisos usan una sola elegibilidad V2;
- para la misma instantánea no discrepan sobre jornada o disponibilidad;
- jornadas manuales y recurrentes aparecen una sola vez;
- disponibilidad efectiva se muestra sin convertirse en trabajo;
- vacaciones, carpeta médica, horario real, cancelación y ausencia impiden
  eventos o avisos obsoletos;
- editar o eliminar reconcilia sin alarmas residuales;
- no existe aviso atrasado por trabajo ya registrado;
- fotografías históricas y vocabulario de los cuatro rubros son correctos;
- privacidad, permiso, fallback exacto/inexacto, ocultar y restaurar funcionan;
- el acceso particular por jornada vuelve a ser alcanzable;
- Room sigue exactamente en versión 5 con 27 tablas y esquemas 1–5 intactos;
- no hay permisos, servicios, dependencias ni componentes nuevos;
- batería local y pruebas autorizadas están verdes con conteos reales;
- checkout contiene únicamente el diff de esta dependencia, sin staged ni
  commit;
- el handoff permite a MAIN auditar todo sin reconstruir este chat.

## CONDICIONES DE PARADA

Detenete y devolvé el control a MAIN ante:

- contradicción entre fuentes activas;
- necesidad real de una entidad futura de extra programada;
- necesidad de cambiar Room, permisos, manifiesto, dependencia o arquitectura;
- cambios sin dueño o fuera de alcance;
- prueba roja sin corrección segura dentro del bloque;
- QA obligatoria imposible;
- acción sobre producción;
- acción destructiva;
- commit, push, tag, Release o `main`.
