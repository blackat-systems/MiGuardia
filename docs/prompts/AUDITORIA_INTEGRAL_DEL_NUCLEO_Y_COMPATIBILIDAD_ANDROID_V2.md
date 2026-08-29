# Auditoría integral del núcleo y compatibilidad Android V2

- Estado: **HABILITADO — REPETICIÓN INTEGRAL AUTORIZADA**
- Fecha: 2026-08-29
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `55dcd60aba2512597d3074f9978f228086ddf7ea`
- Upstream al cerrar esa base:
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea; debe contener esta base funcional como ancestro
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Nombre humano: **Auditoría integral del núcleo y compatibilidad Android**

## QUÉ HACE

Revisa MiGuardia 2.0 como un solo sistema, desde la primera apertura hasta el
Calendario, las horas, el Resumen, el próximo evento y los avisos. Comprueba
que las piezas ya terminadas coincidan entre sí, preserven los datos V2 y se
comporten correctamente en las versiones de Android relevantes.

No agrega una función nueva. Devuelve un diagnóstico verificable: qué está
bien, qué falta probar, qué defecto real existe y si el núcleo puede habilitar
la segunda capa del proyecto.

## POR QUÉ EXISTE

Cada dependencia del núcleo fue auditada al cerrarse, pero una colección de
bloques verdes no demuestra por sí sola que todos usen exactamente la misma
historia laboral, los mismos límites temporales y las mismas reglas al
combinarse.

Esta dependencia es la puerta entre el núcleo laboral y la segunda capa
—widget, informes, copias locales, bloqueo y Ayuda 2.0—. Existe para detectar
contradicciones ahora, cuando todavía pueden corregirse en un bloque acotado,
antes de apoyar más superficies sobre la columna vertebral.

## ROLE

Sos una dependencia auditora especializada de MAIN 2.0. No sos MAIN, no sos
una dependencia implementadora y no podés redefinir el producto.

Tu trabajo es de diagnóstico independiente:

- inspeccionás documentación, Git, código, pruebas, Room, DataStore y
  artefactos;
- ejecutás comprobaciones locales;
- con una autorización expresa nueva de Joaquin, ejecutás QA sobre paquetes
  QA/test y dispositivos concretos;
- registrás hallazgos con evidencia reproducible;
- no corregís el objeto que auditás.

No modifiques código, pruebas, documentación, Gradle, manifiesto, esquemas ni
configuración. Los directorios de compilación ignorados pueden cambiar como
consecuencia normal de ejecutar Gradle, pero el checkout debe continuar sin
cambios rastreados o no rastreados al terminar.

No crees otro proyecto, rama, worktree, tarea ni subagente. MAIN conserva las
decisiones, las correcciones, la documentación canónica y los checkpoints.

## TASK

Emitir un veredicto integral, independiente y basado en evidencia sobre el
núcleo actual de MiGuardia 2.0.

La auditoría debe responder, como mínimo:

1. si la instalación limpia y los cuatro rubros recorren una única experiencia
   V2 sin activar rutas V1;
2. si configuración, lugares, tipos, horarios y reglas se resuelven por la
   revisión exacta de cada fecha;
3. si toda jornada posee su fotografía histórica y las mutaciones estructurales
   pasan por las fronteras transaccionales V2 autorizadas;
4. si cargas manuales, recurrencias, edición, horario real, extras y
   disponibilidad conservan historia y concurrencia;
5. si Horas, Calendario, Resumen, tarjeta y notificaciones interpretan las
   mismas fuentes sin falsos ceros, duplicaciones ni reglas paralelas;
6. si Room V2 versión 5 y su cadena `1→2→3→4→5` preservan datos, claves,
   integridad y atomicidad;
7. si la interfaz mantiene una sola grilla mensual, consulta sin escrituras,
   restauración de borradores y errores recuperables;
8. si Android 8/API 26, el modelo moderno de permisos y el Samsung principal
   conservan un comportamiento compatible;
9. si privacidad, paquetes, permisos, alarmas, receptores, archivos y logs
   respetan los límites aprobados;
10. si existe deuda o cobertura faltante que bloquee apoyar la segunda capa
    sobre este núcleo.

La auditoría puede terminar con el núcleo apto o con MAIN bloqueada. Encontrar
un defecto no obliga a arreglarlo para completar el diagnóstico: debe quedar
descrito de forma que MAIN pueda abrir una corrección separada, reproducirla y
verificar después su cierre.

## CONTEXT

La base funcional ya contiene:

- una instalación inicial limpia y exclusivamente V2;
- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola configuración laboral con revisiones vigentes desde fechas
  concretas;
- lugares, tipos, horarios y reglas por lugar con fotografías históricas;
- carga manual simple o múltiple desde la única grilla mensual;
- edición y eliminación exacta de jornadas;
- planes recurrentes finitos, revisiones y excepciones durables;
- horario planificado inmutable, horario real opcional y extras exactas de una
  jornada;
- extras independientes ya realizados y avance contra una referencia elegida
  por la persona;
- reinicio consciente de la referencia desde hoy, el próximo límite natural o
  una fecha elegida;
- Guardia pasiva, Disponible para llamado o Retén como disponibilidad separada
  del trabajo;
- Calendario final, detalle único del día y tarjeta superior desplegable;
- Resumen mensual personalizable y derivado, sin totales persistidos;
- una proyección V2 compartida por tarjeta, próximo evento y notificaciones;
- feriados, vacaciones, carpetas médicas, notas, fotos, `F/?` y los estados
  internos de ausencia o cancelación dentro de su alcance actual;
- clima local heredado que no debe bloquear las funciones sin red.

No existe todavía un flujo V2 ampliado para crear ausencia o cancelación, ni
widget, informes exportables, copias/restauración, bloqueo o Ayuda 2.0. Estas
ausencias están diferidas y no son defectos de esta auditoría salvo que el
código o la documentación afirmen falsamente que ya existen.

### Base técnica vigente

- `compileSdk = 37`;
- `targetSdk = 37`;
- `minSdk = 26`;
- Java 17;
- `applicationId = "com.blackatsystems.miguardia"` y variante QA con sufijo
  `.qa`;
- Room `MiGuardiaV2Database` versión 5;
- archivo `miguardia-v2.db`;
- 27 tablas;
- `identityHash = 77adbc875d0f4ee466cdbd0dd74d5c5c`;
- migraciones explícitas `1→2`, `2→3`, `3→4` y `4→5`;
- sin migración desde la base de MiGuardia 1.0;
- permisos actuales: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED` e `INTERNET` para clima;
- datos locales por defecto, sin cuenta, nube, sincronización, analítica ni
  telemetría.

Esquemas protegidos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

### Evidencia heredada

La última integración cerró con:

- JVM: 498/498;
- dominio: 302;
- base local: 12;
- aplicación: 184;
- lint: 0 errores;
- AndroidTest compilados: 235 de aplicación y 107 de base;
- Samsung `SM-S938B`, API 36: Room 107/107, una suite completa de aplicación
  233/233 y matriz afectada final 84/84;
- Android 8/API 26: matriz final 20/20 y reconstrucción después de reemplazar
  el paquete QA;
- Room V2 versión 5 y sus esquemas intactos.

Esta evidencia es un antecedente, no un sustituto de la auditoría actual. No
la vuelvas a presentar como ejecución propia. La alarma exacta disparada
realmente, el reinicio físico del Samsung y API 37 siguen pendientes y poseen
puertas separadas.

Los XML y artefactos de `build/` pueden pertenecer a una corrida o a fuentes
anteriores. No los uses como evidencia del HEAD auditado sin comprobar su
procedencia; la batería global debe regenerar resultados actuales.

## INPUTS

Leé completamente y en el orden obligatorio de `AGENTS.md`:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. `docs/sectores/README.md` y las cuatro fichas de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
9. ADR 0017 a 0032 aplicables, con atención especial a 0024–0032;
10. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
11. los prompts V2 cerrados que construyeron el núcleo;
12. las auditorías MAIN de cierre desde la base exclusiva V2 hasta Próximo
    evento y notificaciones;
13. Gradle, manifiesto, esquemas Room, código productivo y todas las pruebas
    JVM e instrumentadas relevantes.

Como mínimo, contrastá expresamente estas auditorías durables:

- `docs/audits/2026-08-23-retiro-modo-v1-y-base-room-v2.md`;
- `docs/audits/2026-08-25-planes-recurrentes-y-cambios-futuros-v2.md`;
- `docs/audits/2026-08-25-horario-real-y-horas-extra-v2.md`;
- `docs/audits/2026-08-25-extras-independientes-y-avance-de-horas-v2.md`;
- `docs/audits/2026-08-27-guardias-pasivas-y-disponibilidad-v2.md`;
- `docs/audits/2026-08-27-calendario-final-y-tarjeta-superior-v2.md`;
- `docs/audits/2026-08-27-resumen-personalizable-v2.md`;
- `docs/audits/2026-08-27-proximo-evento-y-notificaciones-v2.md`.

Si necesitás confirmar una conducta cambiante de Android, usá únicamente
documentación oficial de Android/Google y distinguí la fuente externa de la
evidencia observada en el proyecto.

## OUTPUT

Entregá a MAIN un informe único, autosuficiente y sin modificar el repositorio.
Debe contener:

1. veredicto ejecutivo;
2. base Git y fuentes de verdad realmente verificadas;
3. mapa de componentes y fronteras de escritura;
4. matriz de capacidades contra fuentes, consumidores y pruebas;
5. resultado de dominio y casos temporales cruzados;
6. resultado de Room, DataStore, migraciones, CAS e integridad;
7. resultado de navegación, estado, restauración y experiencia visible;
8. resultado de compatibilidad Android y permisos;
9. resultado de privacidad, seguridad y paquetes;
10. pruebas ejecutadas con comandos y conteos obtenidos desde resultados
    reales;
11. hallazgos ordenados por severidad;
12. riesgos y cobertura faltante;
13. recomendación inequívoca para MAIN.

Cada hallazgo debe indicar:

- severidad `P0`, `P1`, `P2` o `P3`;
- estado `VERIFICADO`, `INFERIDO` o `PENDIENTE`;
- archivo y línea o superficie exacta;
- conducta esperada y conducta observada;
- impacto para la persona usuaria o para los datos;
- reproducción mínima;
- evidencia disponible;
- bloque dueño recomendado para corregirlo;
- prueba que debería demostrar su cierre.

No completes el informe con sugerencias estéticas opcionales. Un hallazgo debe
ser una contradicción, un defecto, un riesgo demostrable o una afirmación sin
evidencia.

## SCOPE

### Permitido en sólo lectura

- todo `app/src/main`, `core/domain/src/main` y `core/database/src/main`;
- todas las pruebas JVM e instrumentadas;
- Gradle, manifiesto, recursos, esquemas Room y reglas de backup;
- almacenes de preferencias de apariencia/zoom, perfil opcional, Resumen,
  clima y notificaciones;
- fuentes documentales y auditorías del núcleo;
- historial Git, referencias, diffs, worktrees y estado del remoto;
- artefactos de compilación, XML de pruebas, informes lint y APK generados;
- documentación oficial de Android cuando sea necesaria;
- dispositivos y paquetes QA/test únicamente después de autorización expresa.

### Ejes obligatorios de auditoría

1. autoridad documental y estado Git;
2. arquitectura, dependencias y ausencia de rutas V1 activas;
3. dominio temporal y sectorial;
4. persistencia, migraciones y fronteras de escritura;
5. coherencia entre Calendario, Horas, Resumen, tarjeta y avisos;
6. navegación, restauración, errores y concurrencia;
7. compatibilidad Android, permisos y runtime del sistema;
8. privacidad, seguridad local y datos ficticios;
9. rendimiento y límites operativos aprobados;
10. suficiencia y honestidad de las pruebas existentes.

## DEPENDENCIES

La dependencia parte de todos los bloques del núcleo cerrados hasta
`55dcd60aba2512597d3074f9978f228086ddf7ea` y del checkpoint documental que
contiene este contrato.

No depende de widget, informes, copias, bloqueo, Ayuda, Agenda profesional ni
ampliaciones de situaciones especiales. Ninguna de esas superficies puede
usarse para justificar un cambio durante esta auditoría.

No debe existir otra dependencia implementadora activa sobre el checkout. Si
aparece trabajo local ajeno, la auditoría se detiene antes de ejecutar una
batería que pueda confundir su procedencia.

## DO NOT

No hagas ninguna de estas acciones:

- editar, crear, eliminar, renombrar o formatear archivos del repositorio;
- aplicar autofix de lint o un formateador;
- agregar pruebas para demostrar un hallazgo;
- corregir el defecto encontrado;
- cambiar Room, esquemas, migraciones, DAO, entidades o DataStore;
- cambiar Gradle, dependencias, manifiesto, permisos, SDK, paquete o versión;
- agregar o recuperar recorridos V1;
- reintroducir activación o migración de datos desde MiGuardia 1.0;
- agregar `Salud`, `Otro`, un quinto rubro o varios perfiles laborales;
- adelantar widget, informes, copias, bloqueo, Ayuda o Agenda profesional;
- agregar cuentas, nube, sincronización, analítica, telemetría o red nueva;
- agregar montos, liquidaciones, deducciones, escalas o datos sindicales;
- usar nombres, jornadas, fotos, ubicaciones o datos reales;
- crear commit, push, tag, Release, merge, rebase, reset o descarte;
- tocar `main`, `origin/main`, `v1.0.0` o worktrees históricos;
- instalar, abrir, limpiar o desinstalar paquetes sin autorización expresa;
- abrir, consultar o modificar el paquete productivo;
- disparar una alarma exacta real o reiniciar el Samsung sin autorización
  inmediata y específica;
- consultar o modificar `font_scale`, densidad o tamaño visual del sistema;
- presentar compilación como instrumentación o cobertura automatizada como
  revisión visual humana.

## PUERTA 0 OBLIGATORIA

Antes de ejecutar Gradle o usar cualquier dispositivo:

1. confirmá que este prompt figure `HABILITADO` en
   `docs/prompts/README.md`;
2. verificá que Joaquin autorizó abrir esta tarea de auditoría;
3. verificá ruta, rama, HEAD, upstream, divergencia, refs protegidas, remoto y
   autor Git;
4. confirmá que el HEAD informado por MAIN contiene a
   `55dcd60aba2512597d3074f9978f228086ddf7ea` como ancestro;
5. confirmá checkout limpio, índice vacío y cero archivos no rastreados;
6. revisá todos los worktrees sin moverlos ni limpiarlos;
7. confirmá wrapper, JDK 17, Android SDK y espacio suficiente;
8. confirmá que no existe otra tarea implementadora activa;
9. registrá el estado de dispositivos como `PENDIENTE`; no uses ADB para
   descubrirlos hasta recibir la autorización física correspondiente.

Comandos mínimos de sólo lectura:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse "@{upstream}"
git rev-list --left-right --count "@{upstream}...HEAD"
git rev-parse "v1.0.0^{}"
git rev-parse main
git rev-parse origin/main
git merge-base --is-ancestor 55dcd60aba2512597d3074f9978f228086ddf7ea HEAD
git status --short --branch
git diff --name-status
git diff --cached --name-status
git ls-files --others --exclude-standard
git worktree list --porcelain
git remote get-url origin
git config user.name
git config user.email
git diff --check
```

Detenete ante cualquier mismatch, detached HEAD, cambio sin dueño o diff
inesperado. No intentes devolver el checkout a un estado anterior.

## AUDITORÍA OBLIGATORIA

### 1. Producto y jerarquía

Comprobá que código, MAPA, PLANIFICACIÓN, STATUS, ADR, prompts cerrados y
auditorías describan el mismo producto.

Verificá especialmente:

- cuatro rubros exactos y separados;
- ninguna opción `Salud` u `Otro`;
- una sola configuración laboral y una sola grilla mensual;
- instalación limpia sin activación V1→V2;
- funcionalidades diferidas no presentadas como terminadas;
- ausencia de montos, cuentas, nube y datos sindicales;
- la segunda capa todavía cerrada.

Una contradicción histórica de V1 sólo es hallazgo si todavía gobierna o se
filtra al comportamiento V2.

### 2. Arquitectura y fronteras

Trazá desde `MiGuardiaApplication` y `MainActivity` todas las dependencias
productivas hasta dominio, repositorios, Room y DataStore.

Confirmá:

- consultas y escrituras diferenciadas;
- `ShiftRepository` como lectura y `V2ShiftRepository`/contratos V2 como
  frontera estructural de escritura;
- ausencia de inserciones, actualizaciones o eliminaciones directas de
  `Shift` fuera de transacciones autorizadas;
- una fotografía `ShiftWorkSnapshot` exacta por jornada;
- CAS y precondiciones completas para operaciones concurrentes;
- observadores cancelables, sin polling ocupado ni colecciones duplicadas;
- una sola fórmula de horas y una sola proyección de eventos/avisos;
- ausencia de rutas productivas `MIGRATED_V1`, adopción o `miguardia.db`;
- ausencia de `fallbackToDestructiveMigration` y
  `allowMainThreadQueries`.

### 3. Dominio y tiempo

Auditá con reloj y zona inyectables:

- límites `[inicio, fin)`;
- medianoche, fin de mes/año y febrero bisiesto;
- zona local preservada y cambio de zona del sistema;
- varias jornadas el mismo día y descanso menor a doce horas;
- plan recurrente de 2.000 jornadas aceptado y 2.001 rechazado atómicamente;
- ocurrencias `AUTOMATIC`, `CUSTOMIZED`, `EXCLUDED` y `RETIRED`;
- edición de una fecha frente a todo lo futuro;
- horario real que cambia de día, mes o año sin mover la fecha visual de la
  jornada;
- extras de jornada e independientes sin doble conteo;
- referencia mensual, semanal o por ciclo, incluida fecha de reinicio;
- referencia desconocida, no usada o incompleta sin falso cero;
- disponibilidad programada menos la unión del trabajo activo coincidente;
- vacaciones y carpeta médica sin borrar historia;
- feriados, `F/?`, ausencia, cancelación y notas dentro de sus contratos;
- reglas históricas de lugar y sector sin reinterpretarlas con catálogos
  actuales.

No copies reglas entre Enfermería y Medicina ni entre otros sectores por
analogía. Una regla sectorial sólo es válida si está configurada o respaldada
por su ficha.

### 4. Persistencia e integridad

Verificá Room V2 versión 5 y sus 27 tablas contra los cinco esquemas
exportados.

Comprobá:

- hashes e `identityHash`;
- migraciones `1→2→3→4→5` con datos representativos;
- apertura limpia y reapertura;
- claves foráneas, índices, unicidad y `integrity_check`;
- rollback completo ante fallo;
- ausencia de jornadas o snapshots huérfanos;
- revisión laboral exacta aplicable a cada fotografía;
- atomicidad de lotes, recurrencias, horario real, extras, disponibilidad y
  reinicio de referencia;
- consultas por lotes que no superen parámetros de SQLite;
- DataStore separados, compatibles y sin totales derivados persistidos;
- un archivo testigo llamado `miguardia.db` permanece byte a byte intacto al
  abrir y usar exclusivamente `miguardia-v2.db`;
- limpieza instrumentada disponible sólo para la base QA aislada.

No modifiques ni regenerés los esquemas durante la auditoría. Si una tarea los
cambia, registrá el mismatch y detené la aprobación.

### 5. Coherencia entre superficies

Para la misma fotografía de datos y el mismo reloj, compará:

- celda y detalle del Calendario;
- Horas y extras;
- Resumen mensual y sus contribuciones;
- tarjeta superior y lista de hoy;
- próximo evento;
- plan de avisos y receiver al disparar.

Las cifras y decisiones deben reconciliarse. En particular:

- habitual + extras = total trabajado;
- disponibilidad nunca suma como trabajo;
- solapamientos conservados se suman sólo según el contrato explícito;
- un trabajo activo reemplaza una sola vez el tramo pasivo coincidente;
- una jornada completada, ausente, cancelada o protegida no genera un aviso
  obsoleto;
- tarjeta y avisos eligen la misma identidad laboral;
- un evento nocturno iniciado ayer aparece donde corresponde sin duplicarse;
- un error parcial conserva sólo el último resultado válido compatible y
  ofrece reintento;
- consultar el Calendario, Resumen o tarjeta no escribe datos.

### 6. Interfaz y restauración

Comprobá recorridos de instalación nueva y estado ya configurado:

- selector inicial con cuatro rubros y Continuar deshabilitado sin elección;
- Calendario vacío y primer lugar/tipo/horario;
- carga de una o varias fechas;
- políticas de fechas ocupadas;
- edición/eliminación exacta;
- repetición y cambio futuro;
- horario real y vuelta al planificado;
- extras independientes y referencia;
- disponibilidad;
- detalle del día, Calendario, Resumen y Notificaciones;
- regreso y Atrás coherentes;
- estados `LOADING`, `CONTENT`, `EMPTY`, `ERROR` y reintento;
- doble toque, error de escritura, conflicto y reintento;
- borradores mediante `SavedStateHandle` y recreación;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- controles identificables sin depender únicamente del color;
- textos largos y desplazamiento alcanzable.

No actives TalkBack ni consultes ajustes visuales del sistema. La accesibilidad
se revisa mediante semántica, etiquetas y navegación visible ya existentes.

### 7. Android, permisos y runtime

Auditá:

- permisos solicitados sólo en contexto;
- rechazo y recuperación sin bloquear el núcleo;
- separación entre `POST_NOTIFICATIONS` y notificaciones deshabilitadas por la
  aplicación del sistema;
- alarma exacta sólo cuando fue elegida y Android la concede;
- fallback inexacto seguro;
- canales, `RemoteViews`, cronómetro, agrupamiento y privacidad;
- receptores de boot, reemplazo de paquete, hora, zona y acceso exacto;
- alarmas obsoletas canceladas y tracking tipado consistente;
- archivos privados de fotos y orientación EXIF sin filtrar metadatos;
- backup deshabilitado según manifiesto y reglas vigentes;
- clima sin cleartext, con timeout/caché y sin bloquear funciones locales;
- paquete QA separado del identificador productivo.

### 8. Privacidad, seguridad y alcance

Buscá:

- secretos, keystores, `.env`, credenciales o `google-services.json`;
- datos personales, nombres reales, cronogramas reales o rutas privadas en
  código, tests, logs y artefactos rastreados;
- notas o motivos médicos en avisos, tarjeta, Resumen o logs;
- direcciones completas persistidas en alarmas o tracking;
- red, cuenta, telemetría o servicios no autorizados;
- permisos o componentes exportados innecesariamente;
- patrones destructivos o borrados silenciosos;
- binarios, APK, AAB, bases o archivos generados rastreados.

### 9. Rendimiento y recuperación

Sin inventar un benchmark público, comprobá límites que puedan bloquear el uso
local:

- expansión máxima recurrente 2.000/2.001;
- consultas y borrados en lotes seguros para SQLite;
- cálculos mensuales con varias fuentes y solapamientos;
- observación reactiva sin bucles por segundo;
- temporizadores cancelados al cambiar fecha o cerrar ViewModel;
- ausencia de desbordamientos aritméticos;
- recuperación tras recreación, reemplazo de paquete y reapertura;
- errores de una fuente que no destruyan el último estado válido de otra.

Un posible problema de rendimiento debe incluir una reproducción medible. No
declares un defecto sólo por el tamaño de un archivo o una preferencia de
estilo.

## VALIDATION

### 1. Batería local global

Ejecutá serialmente y forzando tareas reales:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Obtené los conteos JVM desde los XML generados. Informá lint, APK y AndroidTest
por separado. Revisá también:

```powershell
git diff --check
git status --short --branch
git diff --name-status
git diff --cached --name-status
git ls-files --others --exclude-standard
```

Si la batería altera un archivo del repositorio, no lo descartes: registrá el
hecho, detené la aprobación y devolvé el control a MAIN.

### 2. Pruebas cruzadas mínimas

Confirmá que la cobertura existente —JVM o Android según corresponda— demuestre
al menos:

1. primera apertura para los cuatro rubros;
2. una jornada nocturna 31→1 atribuida correctamente;
3. carga múltiple con libres, ocupadas, segunda jornada y cancelación;
4. edición exacta y edición desde una fecha con historia preservada;
5. 2.000 ocurrencias válidas y 2.001 rechazadas;
6. horario real más corto, más largo, cruzando día y vuelta al planificado;
7. extra de jornada más extra independiente sin doble conteo;
8. reinicio de referencia hoy, próximo período y fecha elegida;
9. disponibilidad sola, parcialmente reemplazada, reanudada y protegida;
10. vacaciones, carpeta médica, feriado, `F/?`, ausencia y cancelación;
11. igualdad entre contribuciones de Horas y Resumen;
12. igualdad entre tarjeta, próximo evento y avisos;
13. recreación, error, conflicto, reintento y doble toque;
14. migraciones Room 1→5, rollback, claves e integridad;
15. archivo testigo `miguardia.db` intacto;
16. ausencia de escrituras durante consultas.

Exigí además una fotografía cruzada única —mismos UUID, reloj y zona— que
combine una jornada materializada por recurrencia, horario real, un fragmento
extra, un extra independiente, disponibilidad coincidente y una protección
vecina. Calendario, Horas, Resumen, tarjeta y avisos deben reconciliar esa misma
fuente sin duplicaciones. Comprobá también un choque CAS real entre dos
escritores que parten de la misma fotografía observada. Si la cobertura no
existe, registrá el hueco; no agregues la prueba durante esta auditoría.

Una prueba inexistente es un hueco de cobertura: no la agregues. Describí el
caso exacto y la dependencia correctiva que debería incorporarla.

### 3. Matriz Android

Este prompt no autoriza por sí solo ADB, instalaciones, limpiezas ni
desinstalaciones. Antes de usar un dispositivo, pedile a Joaquin autorización
expresa para el modelo/AVD, los paquetes QA/test, la instalación y su retiro.

Con autorización:

#### Samsung `SM-S938B`, API 36

- ejecutar la suite Room instrumentada completa;
- ejecutar la suite QA de aplicación completa excluyendo el único caso que
  dispara una alarma exacta real, mediante:

  ```powershell
  .\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
    :app:connectedQaAndroidTest `
    -Pandroid.testInstrumentationRunnerArguments.notClass='com.blackatsystems.miguardia.NotificationAlarmEndToEndInstrumentedTest#realQaAlarmsDeliverReminderUpdateAtStartAndCancelAtEnd'
  ```

  El conteo esperado según el inventario actual es 235/235; verificá el número
  real desde la salida y los XML en vez de copiar esa cifra;
- recorrer con datos ficticios el flujo continuo configuración → jornada →
  repetición → horario real → extra → disponibilidad → Calendario → Resumen →
  aviso;
- comprobar permiso de notificaciones denegado, concedido y deshabilitado por
  Android;
- revisar claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- comprobar cierre/reapertura y restauración;
- inspeccionar que producción no fue abierta ni modificada.

#### Android 8/API 26

- instalación nueva QA;
- apertura, configuración y recorrido esencial del Calendario;
- suite Room instrumentada completa;
- canales, `RemoteViews`, cronómetro y acciones;
- scheduler exacto/inexacto compatible;
- receivers y reconstrucción después de reemplazar el paquete QA;
- regresiones de vistas Compose críticas para el piso soportado.

#### Android 13/API 33 exacta

- `POST_NOTIFICATIONS` denegado, concedido y revocado;
- notificaciones deshabilitadas desde Android;
- acceso a alarmas exactas denegado y fallback inexacto;
- navegación desde aviso y privacidad en pantalla bloqueada cuando el entorno
  permita comprobarla.

El Samsung API 36 demuestra el comportamiento moderno actual, pero no sustituye
la ejecución literal del borde incorporado en API 33. Sin API 33 autorizada y
ejecutada, el veredicto permanece `AUDITORÍA PARCIAL — NO CERRABLE`.

#### API 37

API 37 es puerta transversal antes del candidato final. Si existe una imagen
instalada y Joaquin autoriza su uso, ejecutá al menos instalación, apertura,
permisos modernos, presenter/`RemoteViews`, Calendario y una regresión Room. Si
la imagen no existe, no la descargues sin autorización: registrá el pendiente
exacto para el cierre final.

Usá un solo serial por vez. No lances una tarea conectada amplia si puede
alcanzar simultáneamente el Samsung y un emulador.

Paquetes permitidos sólo con autorización:

```text
com.blackatsystems.miguardia.qa
com.blackatsystems.miguardia.qa.test
com.blackatsystems.miguardia.core.database.test
```

No uses `:app:connectedDebugAndroidTest`, porque puede instalar un host con el
identificador productivo. Esta prohibición no alcanza al runner instrumentado
de `:core:database`, cuyo paquete de prueba autorizado es independiente. No
limpies ni desinstales nada que no haya sido incluido expresamente en la
autorización.

La alarma exacta realmente disparada y el reinicio físico del Samsung siguen
siendo dos autorizaciones inmediatas independientes. Simular el receiver no
equivale a probar un reinicio real. Sólo si Joaquin autoriza expresamente la
alarma real, ejecutá el caso excluido de manera aislada:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:connectedQaAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class='com.blackatsystems.miguardia.NotificationAlarmEndToEndInstrumentedTest#realQaAlarmsDeliverReminderUpdateAtStartAndCancelAtEnd'
```

### 4. Niveles de evidencia

Separá siempre:

- `COMPILADO`;
- `JVM VERIFICADO`;
- `LINT`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA HUMANA`;
- `HEREDADO, NO REPETIDO`;
- `PENDIENTE`.

No sumes una repetición diagnóstica al total de pruebas únicas. Informá
omitidas, supuestos, dispositivo, API, paquete y precondición exactos.

## HANDOFF A MAIN

La entrega debe comenzar exactamente con:

```text
# HANDOFF A MAIN — Auditoría integral del núcleo y compatibilidad Android V2
```

La línea siguiente debe ser una sola de estas:

```text
NÚCLEO APTO PARA SEGUNDA CAPA
MAIN BLOQUEADA
AUDITORÍA PARCIAL — NO CERRABLE
```

Usá `MAIN BLOQUEADA` cuando exista un defecto o contradicción que impida apoyar
la segunda capa. Usá `AUDITORÍA PARCIAL — NO CERRABLE` cuando falte una
autorización, dispositivo o evidencia obligatoria y todavía no sea posible
decidir. No rebajes un bloqueante a pendiente para emitir un cierre verde.

Después incluí, en este orden:

1. `QUÉ HACE`;
2. `POR QUÉ EXISTE`;
3. `OBJECTIVE`;
4. `CHANGES` —debe ser `ninguno`—;
5. `FILES` —debe confirmar cero archivos modificados, nuevos o eliminados—;
6. `DECISIONS` —sólo clasificación de evidencia, no decisiones de producto—;
7. `BASE Y FUENTES`;
8. `MAPA DEL NÚCLEO`;
9. `FINDINGS` —ordenados por severidad—;
10. `DOMAIN`;
11. `ROOM Y DATASTORE`;
12. `UI Y NAVEGACIÓN`;
13. `ANDROID Y PERMISOS`;
14. `VALIDATION` —comandos, conteos y niveles reales—;
15. `PHYSICAL QA`;
16. `PRIVACY AND SECURITY`;
17. `DEVICE SAFETY`;
18. `RISKS`;
19. `PENDING`;
20. `GIT` —ruta, rama, HEAD, upstream, limpieza y staged—;
21. `NEXT`.

Si no hay hallazgos, escribí expresamente `FINDINGS: ninguno` y explicá qué
áreas fueron revisadas para sostener esa conclusión. Si hay hallazgos, no
incluyas un parche: devolvé la reproducción y la prueba de cierre propuesta.

MAIN registrará la auditoría durable y decidirá el checkpoint. Esta
dependencia no modifica `docs/STATUS.md`, el índice, ADR ni auditorías.

## DONE WHEN

### Auditoría concluida

La dependencia auditora está terminada cuando ocurre una de estas dos salidas:

1. completó toda la matriz obligatoria y puede emitir un veredicto integral; o
2. reprodujo un bloqueante suficiente para emitir `MAIN BLOQUEADA`, documentó
   su impacto y detuvo de forma segura las comprobaciones posteriores que ya no
   podían cambiar ese veredicto.

En cualquiera de las dos salidas:

- Puerta 0 quedó verificada sobre el HEAD exacto informado por MAIN;
- todas las fuentes obligatorias fueron leídas y jerarquizadas;
- las fronteras de lectura y escritura del núcleo quedaron trazadas;
- cada bloque cerrado fue contrastado con su código, consumidores y pruebas;
- la batería local global fue ejecutada con conteos reales;
- Room V2, migraciones, integridad, CAS y DataStore fueron auditados;
- los casos cruzados obligatorios quedaron verificados o documentados como
  huecos concretos;
- la matriz física autorizada fue ejecutada y separada por API y nivel de
  evidencia;
- privacidad, permisos, paquetes y producción quedaron controlados;
- todos los hallazgos poseen severidad, reproducción y dueño recomendado;
- el checkout sigue limpio y sin staged;
- el handoff permite a MAIN decidir sin reconstruir este chat.

### Núcleo aprobado

El núcleo sólo puede recibir `NÚCLEO APTO PARA SEGUNDA CAPA` cuando:

- no existen hallazgos P0 o P1 abiertos;
- cualquier P2 o P3 está acotado, no contradice un contrato público y posee
  seguimiento claro;
- batería local, Room y casos cruzados están verdes;
- Samsung API 36, Android 8/API 26 y Android 13/API 33 fueron ejecutados con
  autorización sobre el HEAD auditado;
- no quedan afirmaciones de compatibilidad sustentadas únicamente por
  compilación o evidencia heredada;
- privacidad, producción y estado de los dispositivos quedaron controlados.

La auditoría puede estar concluida con `MAIN BLOQUEADA`: completar el
diagnóstico no significa que el núcleo haya aprobado la puerta. Si no existe
un bloqueante demostrado y falta QA física obligatoria o una dispensa expresa
de Joaquin, sólo puede entregarse como
`AUDITORÍA PARCIAL — NO CERRABLE`.

API 37 puede permanecer como pendiente explícito para el candidato final si la
imagen no está disponible y no fue autorizada su descarga. No ocultes ese
pendiente ni lo conviertas en evidencia API 37.

## CONDICIONES DE PARADA

Detenete y devolvé el control a MAIN ante:

- ruta, rama, HEAD, upstream, base o refs protegidas inesperadas;
- checkout sucio, staged o archivos sin dueño;
- prompt no habilitado o tarea no autorizada por Joaquin;
- otra dependencia implementadora activa;
- contradicción material entre fuentes activas;
- necesidad de modificar un archivo para continuar la auditoría;
- prueba roja o migración fallida;
- esquema Room, permiso, manifiesto, dependencia, paquete o versión distintos
  de la base declarada;
- hallazgo P0 o P1 que ponga en riesgo datos, privacidad o comportamiento
  estructural;
- QA obligatoria imposible o dispositivo no autorizado;
- necesidad de usar datos reales;
- necesidad de disparar una alarma exacta real o reiniciar el Samsung;
- cualquier acción destructiva, externa, productiva o de publicación;
- commit, push, tag, Release, merge, rebase, reset, descarte o acción sobre
  `main`.

Conservá toda evidencia segura ya obtenida, describí el punto exacto y no
intentes corregirlo dentro de esta dependencia.
