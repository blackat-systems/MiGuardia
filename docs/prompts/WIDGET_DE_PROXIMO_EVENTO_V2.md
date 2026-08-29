# Widget de próximo evento V2

- Estado: **CERRADO — IMPLEMENTADO, AUDITADO Y VERIFICADO POR MAIN EN SAMSUNG API 36**
- Fecha: 2026-08-29
- Cierre MAIN: 2026-08-29
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base documental cerrada:
  `7dadd6299a864df939b5de6d6d6f67d9df737c53`
- Base funcional auditada:
  `c35fffb2abe99eac73e164f99147bf95d11ad83d`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Widget de próximo evento**

> Nota de cierre: el cuerpo conserva el contrato y las puertas vigentes al
> abrir la dependencia. La evidencia ejecutada y los pendientes actuales están
> registrados en
> `docs/audits/2026-08-29-widget-de-proximo-evento-v2-main.md`.

## QUÉ HACE

Agrega a la pantalla de inicio de Android una tarjeta configurable que muestra
la próxima jornada, el próximo franco o el evento laboral que corresponde en
ese momento.

Cada widget puede elegir su propio modo, privacidad y uso opcional de Clima.
Tocar el contenido abre el día correcto dentro de MiGuardia. La persona puede
tener varios widgets a la vez sin que sus configuraciones se mezclen.

## POR QUÉ EXISTE

MiGuardia ya sabe cuál es la jornada o disponibilidad correcta para la tarjeta
superior y los avisos. El Widget lleva esa misma información a la pantalla de
inicio sin obligar a abrir la aplicación y, sobre todo, sin calcularla de otra
manera.

Existe para dar una consulta rápida, local y respetuosa de la privacidad. No
es una nueva agenda, no duplica el Calendario y no depende de que las
notificaciones estén activadas.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, la proyección única de eventos, la
persistencia V2 ni la secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final y los checkpoints.

Primero auditá la proyección V2, las rutas de navegación, las vistas remotas de
Notificaciones, la caché de Clima y sus pruebas. Conservá lo que ya cumple y
agregá únicamente la capa Android necesaria para el Widget.

## TASK

Implementar integralmente el **Widget de próximo evento** como una superficie
local, configurable por instancia y de sólo lectura.

El recorrido mínimo debe permitir:

1. agregar un widget desde el selector del launcher;
2. elegir un modo y una privacidad antes de confirmar;
3. guardar dos o más widgets con configuraciones independientes;
4. mostrar contenido compacto o ampliado según el espacio del launcher;
5. actualizar el contenido cuando cambian los datos o una frontera temporal;
6. tocar una jornada, disponibilidad o franco y abrir su día exacto;
7. abrir el Calendario actual cuando no existe un evento;
8. reconfigurar una instancia sin afectar las demás;
9. eliminar una instancia y limpiar sólo sus preferencias;
10. seguir funcionando con Notificaciones apagadas, permiso denegado y sin
    conexión;
11. preservar privacidad, historia V2 y consumo razonable de batería;
12. no escribir datos laborales durante ninguna consulta del Widget.

No implementes Informes, copias, bloqueo, Ayuda, recorrido inicial ni otras
funciones futuras.

## CONTEXT

La base cerrada ya posee:

- una experiencia exclusivamente V2 y una instalación inicial limpia;
- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola configuración laboral con vigencia desde fechas concretas;
- jornadas manuales y jornadas materializadas por recurrencias;
- horario planificado, horario real, extras y disponibilidad;
- vacaciones, carpetas médicas, feriados, notas, `F/?`, ausencias y
  cancelaciones en sus alcances actuales;
- una sola grilla mensual, una tarjeta superior y un Resumen personalizable;
- una proyección única `projectNextEvent(...)` para jornada, tramo efectivo de
  disponibilidad y franco explícito;
- identidades tipadas y estables para jornadas y disponibilidades;
- un grafo reactivo `V2WorkEventSourceObserver` que reúne todas las fuentes;
- rutas seguras existentes para abrir una jornada o una fecha en
  `MainActivity`;
- `RemoteViews`, `PendingIntent` inmutables y fronteras temporales ya probadas
  en Notificaciones;
- Clima opcional con ubicación fija aprobada, caché privada, proveedor detrás
  de una interfaz y degradación sin conexión;
- tema Vigilia claro/oscuro y zoom interno 100 %, 150 % y 200 % dentro de la
  aplicación;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db` y veintisiete
  tablas.

No existe hoy código de `AppWidgetProvider`, Glance ni WorkManager. La única
aparición productiva de `RemoteViews` pertenece a las notificaciones. No
recuperes un Widget de worktrees históricos: no existe uno integrado que sea
fuente de verdad.

El núcleo fue aprobado el 2026-08-29 mediante:

```text
NÚCLEO APTO PARA SEGUNDA CAPA
FINDINGS: ninguno
```

Evidencia verde heredada, que no reemplaza tu propia validación:

- JVM: 499/499;
- Samsung API 36: Room 108/108 y aplicación 235/235;
- Android 8/API 26: Room 108/108 y recorrido esencial 27/27;
- Android 13/API 33: matriz 24/24;
- Room V5, veintisiete tablas y esquemas 1 a 5 intactos.

## PUERTA 0 OBLIGATORIA

Antes de modificar cualquier archivo:

1. leé completas y en el orden de `AGENTS.md` todas las fuentes obligatorias;
2. verificá en vivo ruta, rama, HEAD, upstream, base protegida, limpieza,
   worktrees, remoto privado y autor Git;
3. confirmá que este prompt figure `HABILITADO` en
   `docs/prompts/README.md`;
4. confirmá que el HEAD informado por MAIN contiene a
   `7dadd6299a864df939b5de6d6d6f67d9df737c53` como ancestro;
5. confirmá que no existe otra dependencia implementadora trabajando sobre el
   checkout;
6. inventariá cualquier código actual relacionado con widgets, vistas remotas,
   próximo evento, Clima, navegación y preferencias;
7. detenete si el checkout no está limpio o el HEAD no coincide con la entrega
   de MAIN.

Comandos mínimos de sólo lectura:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse @{upstream}
git merge-base --is-ancestor 7dadd6299a864df939b5de6d6d6f67d9df737c53 HEAD
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

Resultado esperado:

- ruta exacta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama `codex/miguardia-2.0`;
- autor `joaquin <blackat.systems@gmail.com>`;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- checkout limpio, sin staged ni archivos sin seguimiento;
- worktrees históricos preservados.

No uses ADB, Samsung, emuladores, instalaciones o limpiezas durante Puerta 0.
La QA física requiere una autorización nueva y expresa de Joaquin.

## INPUTS OBLIGATORIOS

Leé completos, en el orden general definido por `AGENTS.md`:

- `AGENTS.md`;
- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- las cuatro fichas de `docs/sectores/`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- ADR 0009, 0010, 0011, 0015, 0026, 0032 y 0033;
- `docs/PROMPT_MAESTRO_MAIN.md` como contrato histórico V1;
- `docs/prompts/PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md`;
- `docs/prompts/CLIMA.md`;
- `docs/prompts/VIGILIA_SISTEMA_VISUAL.md` como referencia visual histórica;
- la auditoría integral del núcleo del 2026-08-29;
- código y pruebas de próximo evento, tarjeta, notificaciones, Clima,
  navegación, tema y preferencias.

Consultá también la documentación oficial vigente:

- [App widgets](https://developer.android.com/develop/ui/views/appwidgets);
- [diseño flexible y tamaños](https://developer.android.com/develop/ui/views/appwidgets/layouts);
- [actualizaciones eficientes](https://developer.android.com/develop/ui/views/appwidgets/advanced);
- [configuración y reconfiguración](https://developer.android.com/develop/ui/views/appwidgets/configuration);
- [vistas previas](https://developer.android.com/develop/ui/views/appwidgets/previews);
- [calidad de widgets](https://developer.android.com/docs/quality-guidelines/widget-quality);
- [seguridad de PendingIntent](https://developer.android.com/privacy-and-security/risks/pending-intent).

Si una API cambió, usá la documentación oficial actual y registrá la diferencia
sin ampliar el producto.

## DEPENDENCIES

Esta dependencia nace después de:

1. configuración laboral y catálogo;
2. carga, edición, recurrencias y horario real;
3. extras y disponibilidad;
4. Calendario final y tarjeta superior;
5. Resumen personalizable;
6. próximo evento y notificaciones V2;
7. tres pruebas cruzadas del núcleo;
8. auditoría integral con matriz API 26/33/36.

Todos están cerrados. No abras otra dependencia mientras ésta permanezca
activa. El bloque siguiente, sólo después de integración y cierre, es
**Informes locales**.

## DECISIONES CONGELADAS

### 1. Una sola verdad laboral

El Widget recibe `NextEventResult` producido por `projectNextEvent(...)` y
aplica únicamente presentación y selección de modo. No recibe fuentes crudas
para recalcular elegibilidad.

Puede existir una función pura nueva, por ejemplo
`projectWidgetContent(config, nextEventResult)`, pero no puede duplicar:

- prioridades;
- estados laborales;
- protección por vacaciones o carpeta médica;
- horario real;
- materialización de recurrencias;
- reemplazo de disponibilidad por trabajo activo;
- simultáneos;
- intervalos `[inicio, fin)`;
- identidades estables.

`NextEventObserver` pertenece a la tarjeta interna y despierta por minuto: no
se reutiliza como observador de fondo del Widget. Sí se reutilizan
`projectNextEvent`, `NextEventIdentity`, `NextEventItem`,
`NextEventResult`, `NextEventSourceData` y `V2WorkEventSourceObserver`.

### 2. Tres modos por instancia

Cada widget guarda exactamente uno:

1. **Próxima jornada**: primera jornada elegible con inicio futuro. Una jornada
   ya activa no aparece en este modo. La disponibilidad tampoco aparece.
2. **Próximo franco**: usa únicamente el próximo estado explícito `F`/
   `DAY_OFF`. Un día vacío o `?` nunca se convierte en franco.
3. **Automático**: refleja la prioridad compartida: jornada activa,
   disponibilidad efectiva activa, próximo comienzo de jornada o
   disponibilidad, franco explícito y por último vacío.

La frase de ADR 0032 “franco explícito sólo para la tarjeta” excluye al sistema
de avisos. El Widget es otra superficie visual y puede leer el mismo
`nextDayOff` ya calculado. No agregues francos a las notificaciones.

Las recurrencias entran sólo mediante sus jornadas materializadas. Los extras
independientes no son eventos futuros. Disponibilidad nunca se presenta como
trabajo y conserva `Guardia pasiva`, `Disponible para llamado` o `Retén` según
su fotografía histórica.

### 3. Simultáneos

En modo **Automático**, consumí `NextEventResult.primaryEvents` completo y en
su orden estable. No vuelvas a filtrarlo por inicio, tipo o prioridad: cuando
hay trabajo activo puede contener jornadas que comenzaron a distintas horas,
y cuando el próximo comienzo es futuro el motor ya reúne todos los eventos de
esa frontera.

En **Próxima jornada**, agrupá únicamente las jornadas futuras que compartan
el primer inicio futuro. No mezcles disponibilidad ni inventes otro orden.

Para el grupo resultante de cada modo:

- compacto: muestra el primero según el orden estable y una indicación
  `N eventos a la vez`;
- ampliado: muestra hasta tres filas y el total cuando existan más;
- tocar cualquiera abre el día correspondiente;
- no inventes un desempate distinto del motor compartido.

### 4. Cuenta regresiva

Usá el `Chronometer` nativo de `RemoteViews`; no despiertes la aplicación cada
segundo ni cada minuto.

Su base se calcula contra tiempo monotónico:

```text
SystemClock.elapsedRealtime() + (targetEpochMillis - nowEpochMillis)
```

Nunca pases epoch directamente como base. Si Android entrega tarde la frontera
y el objetivo ya pasó, el siguiente render detiene u oculta el cronómetro; no
deja un valor negativo o activo indefinidamente.

- una jornada o disponibilidad futura puede contar hasta su comienzo;
- una jornada o disponibilidad activa cuenta hasta su final sólo en modo
  **Automático**;
- **Próxima jornada** nunca convierte una jornada activa en contenido;
- **Próximo franco** muestra fecha civil, no cuenta regresiva horaria;
- la privacidad oculta nunca muestra el contador;
- la hora absoluta permanece visible en completa y reducida para que una
  demora de Android no prometa exactitud inexistente.

### 5. Tamaños

Existen dos experiencias:

- **compacta**: identidad, estado principal, fecha/hora y contador cuando
  corresponda;
- **ampliada**: agrega contexto permitido, simultáneos, acción de configurar y
  Clima opcional.

En API 31 o superior usá mapas de tamaños responsivos de `RemoteViews`. En API
26 a 30 elegí el layout mediante las opciones y dimensiones informadas por el
launcher. Nunca recortes fecha, horario, estado ni acción principal.

El zoom interno 100/150/200 pertenece a las pantallas de MiGuardia y a la
actividad de configuración. El contenido alojado por el launcher se adapta al
espacio del widget; no consulta ni modifica `font_scale`, densidad, zoom o
tamaño visual del sistema.

### 6. Privacidad por instancia

Cada widget guarda una privacidad propia e independiente de Notificaciones:

1. **Completa**: estado, tipo, lugar/abreviatura, horario, puesto si existe,
   color histórico y contador cuando corresponda.
2. **Reducida**: estado genérico, fecha, horario y contador; oculta lugar,
   abreviatura, puesto y color histórico.
3. **Oculta**: mensaje genérico de MiGuardia; oculta tipo de evento, etiqueta
   de disponibilidad, fecha, horario, lugar, puesto, color y contador.

El valor seguro antes de confirmar la configuración es **Oculta**. Nunca
transportes o muestres:

- dirección;
- nota;
- motivo médico;
- explicación de horario real;
- foto;
- dato personal;
- paciente o dato de salud;
- contenido de otra instancia.

### 7. Navegación

- Jornada: acción tipada por UUID; `MainActivity` vuelve a leer el par V2 antes
  de abrir la fecha dueña.
- Disponibilidad y franco: abrir su fecha dueña.
- Vacío o error: abrir el Calendario actual.
- Configuración: abrir la instancia exacta.

Los `PendingIntent` son explícitos, inmutables y distintos por
`appWidgetId`, acción e identidad. Sus extras llevan únicamente UUID, fecha o
identificador opaco; nunca texto laboral, dirección o contenido privado.

### 8. Configuración y gestión

La configuración inicial es obligatoria y permite elegir:

- modo;
- privacidad;
- incluir Clima, sólo si cumple las condiciones posteriores.

`Continuar` confirma de forma consciente. Cancelar devuelve
`RESULT_CANCELED`. En la configuración inicial no guarda preferencias ni deja
una instancia configurada. En una reconfiguración conserva intactos el Widget
y las preferencias anteriores.

Agregá el destino visible **Widget de inicio** en `Avisos y contexto`. Si no
hay instancias, explica cómo agregar una desde la pantalla de inicio de
Android. Si existen, lista cada una con nombre humano, modo y privacidad y
permite reconfigurarla. No muestra IDs técnicos ni duplica datos laborales.

En Android que lo soporte, habilitá también la reconfiguración del launcher.
La gestión dentro de MiGuardia mantiene reconfiguración en API 26 a 30.

### 9. Persistencia por instancia

Usá un DataStore Preferences exclusivo del Widget, con configuración bajo
`appWidgetId`:

- modo;
- privacidad;
- Clima opcional;
- indicador de configuración completa.

No guardes eventos, totales, jornadas, fotografías, proyecciones ni contenido
renderizado. Las ediciones son atómicas. Un ID corrupto, desconocido o ajeno al
provider usa un estado seguro y no lee otra instancia.

- `onDeleted`: elimina únicamente los IDs recibidos;
- `onRestored`: si existe la clave anterior, mueve de forma atómica la
  configuración `oldId → newId`; si no existe, deja el nuevo ID en privacidad
  Oculta y configuración incompleta, sin copiar otra instancia;
- coordiná el trabajo asíncrono de `onRestored` con el `onUpdate` que Android
  envía inmediatamente después: mientras el remapeo no termine sólo puede
  mostrarse el estado seguro y, al completarse, se renderiza otra vez;
- desde API 30, recién después del remapeo y el render final marca
  `OPTION_APPWIDGET_RESTORE_COMPLETED=true` y vuelve a actualizar;
- eliminar el último widget cancela el refresco temporal pendiente;
- dos widgets nunca comparten claves o `PendingIntent` por accidente.

El manifest conserva `allowBackup=false`. Si Android restaura los IDs pero no
existen las preferencias antiguas, no inventes una recuperación: mostrá
configuración incompleta con privacidad Oculta y pedí reconfigurar.

### 10. Arquitectura Android

Implementá con APIs nativas:

- `AppWidgetProvider`;
- `RemoteViews`;
- layouts XML compacto y ampliado;
- actividad Compose de configuración;
- presenter/proyección pura;
- runtime/coordinador de actualización;
- DataStore por instancia.

No agregues Glance. No permite reutilizar directamente los composables
actuales y exigiría una dependencia de producción sin beneficio suficiente en
este alcance. Si aparece una limitación crítica imposible de resolver con la
plataforma, detenete y devolvé evidencia a MAIN antes de tocar Gradle.

La actividad de configuración es la única superficie externa nueva necesaria:
valida que el `appWidgetId` pertenezca al provider antes de leer o guardar. El
receiver del provider usa el mínimo nivel de exportación compatible con la
documentación oficial. No declares `BIND_APPWIDGET`: corresponde al host, no al
proveedor.

### 11. Actualización eficiente

Configurá `updatePeriodMillis=0`. No uses polling, WorkManager periódico,
servicio permanente, foreground service ni actualización por minuto.

Actualizá:

- después de configurar o reconfigurar;
- al agregar, editar o eliminar datos que afecten la proyección;
- al registrar horario real o una protección;
- al modificar disponibilidad;
- al cambiar tema o preferencias del Widget;
- al redimensionar;
- al restaurar IDs;
- en cambio manual de hora o zona;
- al reemplazar el paquete;
- al iniciar el dispositivo, sólo si existen widgets.

Mientras el proceso vive, un runtime reactivo puede observar el grafo V2. Para
proceso muerto, mantené como máximo una alarma local **inexacta y de una sola
ejecución** para la próxima frontera relevante entre todas las instancias.
Recalculala después de cada cambio y cancelala al eliminar la última instancia.

Esa alarma usa un `PendingIntent` de broadcast explícito e inmutable. No uses
`OnAlarmListener`, porque su vida queda ligada al proceso. No pruebes “proceso
cerrado” mediante `force-stop`: ese estado puede cancelar los `PendingIntent` y
deshabilitar widgets hasta una acción posterior del usuario. Para QA aislada,
usá una muerte de proceso que no marque el paquete como detenido.

La frontera puede ser comienzo, final, medianoche o vencimiento del dato
climático actualmente visible. No pidas alarma exacta ni dependas del permiso
de Notificaciones. La alarma es una señal reconstruible: al recibirla se vuelve
a leer la fuente completa.

Todo receiver que lea DataStore o Room usa `goAsync()`, trabajo estructurado y
`PendingResult.finish()` dentro de `finally`, con un límite interno inferior al
del sistema. El render local y la reprogramación son el camino crítico; una
descarga de Clima nunca los demora.

### 12. Clima opcional

Clima puede aparecer únicamente cuando:

- el layout es ampliado;
- la privacidad es Completa;
- Clima global está habilitado;
- la explicación del proveedor fue aceptada;
- esa instancia eligió incluirlo;
- existe cobertura completa y dato fresco para una jornada.

La opción por widget está apagada por defecto y no reutiliza
`includeInNotifications`. Renderizá primero el evento laboral desde la fuente
local. Podés leer la caché existente y, después, solicitar
`refreshIfStale(false)` mediante el runtime vigente, de forma acotada y sin
bloquear el Widget. Una falla o ausencia de red simplemente omite Clima.

Si se muestra, incluye atribución visible `Datos meteorológicos: Open-Meteo`.
Tocar esa atribución abre mediante un `PendingIntent` explícito la superficie
existente **Clima**, donde ya vive el enlace HTTPS real a Open-Meteo. No abras
un navegador con un intent implícito desde el Widget. No muestres Clima para
disponibilidad o franco y no accedas directamente al cliente/proveedor desde
el presenter.

### 13. Diseño

- Vigilia neutral, legible y sobria;
- franja pequeña de color histórico sólo en privacidad Completa;
- texto o símbolo además del color;
- estados vacíos y de error claros;
- sigue exclusivamente la preferencia global `AppThemeMode` de MiGuardia, sin
  tema por instancia: `LIGHT` y `DARK` usan sus paletas de la app y `SYSTEM`
  resuelve el modo vigente del sistema;
- una modificación de esa preferencia global vuelve a renderizar los widgets;
- recursos propios o con licencia documentada;
- preview con datos ficticios y sin capturas reales;
- `previewImage` compatible con API 26 a 30 y `previewLayout` desde API 31;
- descripciones accesibles, orden de lectura estable, acciones con nombre y
  blancos táctiles de al menos 48×48 dp;
- contraste suficiente y ninguna información comunicada sólo por color;
- nada de glow continuo, animación constante o información diminuta.

Mapas y `Cómo llegar` quedan fuera de este primer Widget. La mención histórica
era opcional y transportar dirección no aporta al objetivo mínimo.

## OUTPUT

Entregá un candidato sin commit directamente en el checkout compartido que
incluya, según necesidad real:

- proyección/presenter puro del Widget;
- `AppWidgetProvider`;
- runtime y planificador de fronteras;
- DataStore por instancia;
- actividad y pantalla Compose de configuración;
- destino interno `Widget de inicio`;
- layouts, metadatos, previews y recursos accesibles;
- manifest mínimo;
- pruebas JVM, DataStore, Android, navegación y regresión;
- handoff autocontenido a MAIN.

MAIN conserva `STATUS`, índice, ADR, auditoría final y checkpoint.

## SCOPE

Podés modificar sólo cuando sea necesario:

- `core/domain/src/main/**/nextevent/**` o un paquete puro `widget/**` para la
  adaptación de presentación;
- sus pruebas JVM;
- `app/src/main/java/com/blackatsystems/miguardia/widget/**`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/widget/**`;
- `MiGuardiaApplication.kt` para composición lazy y runtime;
- `MainActivity.kt` para navegación/reconfiguración mínima;
- `MiGuardiaApp.kt` para el destino visible y cableado mínimo;
- `AndroidManifest.xml` sólo para provider y actividad de configuración;
- `res/layout`, `res/xml`, `res/drawable`, `res/values` y `res/values-night`
  estrictamente necesarios;
- pruebas de app JVM e instrumentadas;
- las pruebas cruzadas existentes sólo para agregar el Widget como consumidor.

Si necesitás salir de estas rutas, frená esa parte y devolvé el motivo a MAIN.

## DO NOT

No modifiques ni agregues:

- Room, entidades, DAO, repositorios database, versión, esquemas o migraciones;
- DataStore de Notificaciones, Clima, Resumen o perfil laboral;
- prioridades o elegibilidad de `projectNextEvent`;
- cálculo de horas, disponibilidad, recurrencias, extras o protecciones;
- canales, preferencias, visibilidad, alarmas o receivers de Notificaciones;
- proveedor, parser, ubicación o contrato de red de Clima;
- Gradle, catálogo, wrapper o dependencias;
- permisos;
- `applicationId`, namespace, min/target/compile SDK o Java;
- versión de la app;
- segundo calendario;
- mapas, geolocalización o direcciones en el Widget;
- cuentas, nube, sincronización, analítica, telemetría o logs privados;
- datos reales;
- informes, copias, bloqueo, Ayuda o recorrido inicial;
- `main`, tag, Release o producción.

No reutilices las alarmas de Notificaciones como condición para que el Widget
funcione. Podés reutilizar patrones seguros, no su preferencia global ni sus
identidades instaladas.

## ESTADOS OBLIGATORIOS

Definí y probá:

- configuración incompleta;
- cargando inicial;
- evento activo;
- próximo evento;
- franco explícito;
- varios simultáneos;
- sin próxima jornada;
- sin franco explícito;
- sin eventos;
- Clima fresco;
- Clima no disponible sin romper el evento;
- error recuperable con acción de abrir/reintentar;
- instancia eliminada o ID inválido;
- datos que cambian durante una actualización;
- proceso recreado;
- paquete reemplazado;
- cambio de fecha y zona.

No conserves un dato laboral de ayer ni de otra configuración para ocultar un
error actual.

## VALIDATION

### JVM y DataStore

Con `Clock`, `ZoneId`, UUID y datos ficticios inyectables, cubrí como mínimo:

1. tres modos;
2. jornada activa excluida de Próxima jornada;
3. jornada futura y simultáneas;
4. disponibilidad activa, futura y reanudada en Automático;
5. `F` explícito y rechazo de vacío/`?`;
6. cancelación, ausencia, protección y horario real ya resueltos por el motor;
7. recurrencia sin duplicación;
8. extras independientes sin convertirse en evento;
9. cuenta hasta comienzo y, sólo en Automático activo, hasta final;
10. los tres niveles de privacidad sin fugas;
11. compacto/ampliado y corte determinista por tamaño;
12. dos IDs independientes;
13. alta cancelada sin configuración y reconfiguración cancelada sin perder el
    estado anterior;
14. limpieza selectiva;
15. restauración `oldId → newId`, coordinación con `onUpdate` y marca final
    desde API 30;
16. restauración sin preferencia anterior vuelve a Oculta/incompleta y nunca
    copia otra instancia;
17. configuración corrupta segura;
18. una única próxima frontera para varias instancias;
19. alarma inexacta con `PendingIntent`, no listener ligado al proceso;
20. cancelación de refresco al borrar la última;
21. base monotónica del cronómetro y frontera entregada con demora;
22. Clima apagado, caché fresca, stale, vencida y fallo;
23. tema global `LIGHT`, `DARK` y `SYSTEM`, sin preferencia por instancia;
24. atribución de Clima que abre su superficie interna;
25. igualdad con `NextEventResult` y ausencia de una segunda prioridad.

Extendé la fotografía transversal del núcleo para demostrar que Widget,
tarjeta y avisos eligen identidades compatibles desde el mismo resultado.

### Instrumentación compilada

Agregá pruebas para:

- actividad de configuración, Guardar, Cancelar y reconfiguración cancelada;
- recreación con borrador;
- provider con ID válido e inválido;
- RemoteViews compacto y ampliado;
- `Chronometer` y PendingIntent correctos;
- `goAsync()` termina siempre, incluso ante error o timeout;
- privacidad sin texto prohibido;
- jerarquía accesible, descripciones, contraste y blancos táctiles;
- redimensionamiento;
- dos widgets diferentes;
- reconfiguración;
- borrado y restauración de IDs;
- cambio de datos y frontera;
- navegación a jornada, fecha y Calendario;
- tema global claro/oscuro/sistema y actualización de todas las instancias;
- atribución de Clima que navega a la superficie interna con el enlace real;
- pantalla de configuración al 100/150/200 interno;
- consultas del Widget sin cambios en las veintisiete tablas.

### Batería local final

Ejecutá serializado:

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

Obtené conteos reales desde XML y distinguí:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

Ejecutá además:

```powershell
git diff --check
git status --short
git diff --name-status
git ls-files --others --exclude-standard
```

Revisá cada archivo nuevo completo. AndroidTest compilado no equivale a una
prueba ejecutada.

## ROOM

Room debe permanecer exactamente:

- base `miguardia-v2.db`;
- versión 5;
- veintisiete tablas;
- `identityHash` `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- sin `fallbackToDestructiveMigration`;
- sin `allowMainThreadQueries`.

Hashes protegidos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

Verificalos contra archivos reales. No regeneres ni reformatees esquemas.

## PHYSICAL QA

PENDIENTE hasta una autorización nueva y expresa de Joaquin. El permiso de una
tarea anterior no se hereda.

Cuando sea autorizado, usá exclusivamente paquete QA y datos ficticios, un
dispositivo por vez:

1. Samsung `SM-S938B`, API 36;
2. Android 8/API 26;
3. Android 13/API 33.

Recorrido mínimo real:

- agregar y cancelar configuración;
- agregar dos widgets simultáneos;
- tres modos y tres privacidades;
- compacto y ampliado mediante resize real;
- simultáneos;
- jornada activa/futura;
- disponibilidad activa/futura/reanudada;
- franco `F` y estados vacíos;
- Clima apagado y caché válida;
- tocar y abrir día/Calendario;
- editar/eliminar una fuente y ver actualización;
- registrar horario real/protección y ver reconciliación;
- proceso cerrado;
- transición de fecha mediante `Clock` inyectado en instrumentación;
- en el launcher físico, contenido correcto para la fecha real vigente, sin
  modificar hora ni fecha del sistema;
- muerte del proceso QA sin `force-stop` y reconstrucción posterior;
- reemplazo real del paquete QA con misma firma;
- claro/oscuro y retrato/paisaje;
- configuración al zoom interno 100/150/200;
- eliminación de una instancia y después de la última.

No cambies `font_scale`, densidad, tamaño visual, hora o zona del dispositivo.
No dispares una alarma exacta real. Un reinicio físico del Samsung sigue siendo
una autorización separada; si no existe, registralo PENDIENTE sin simularlo.
No actives TalkBack ni declares un recorrido específico de TalkBack: comprobá
la jerarquía accesible y sus descripciones con herramientas de inspección.

API 37 continúa diferida para la auditoría final de la aplicación completa.

## DEVICE SAFETY

- verificá modelo, API, serial y paquetes antes de instalar;
- no abras, limpies, reemplaces ni desinstales producción;
- instalá únicamente `com.blackatsystems.miguardia.qa`, `.qa.test` y el paquete
  de prueba database cuando corresponda;
- no uses datos laborales reales;
- no cambies Wi-Fi, datos, VPN, hora, zona ni ajustes visuales del sistema;
- restaurá orientación al finalizar;
- retirá únicamente paquetes QA/test creados por la tarea;
- informá exactamente qué queda instalado.

Si producción no está instalada, no la instales para “probar”.

## HANDOFF A MAIN

No hagas commit, push, merge, rebase, reset, tag ni Release. Conservá el
candidato sin confirmar en el checkout compartido.

Entregá un único informe autocontenido con estas secciones exactas:

```text
# HANDOFF A MAIN — Widget de próximo evento V2

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
```

El handoff debe incluir:

- ruta, rama, base, HEAD y upstream reales;
- diff completo y archivos no rastreados;
- modos, tamaños, privacidad, simultáneos y navegación;
- persistencia por instancia y restauración de IDs;
- estrategia de fronteras y ausencia de polling;
- Clima y degradación offline;
- permisos, manifest, Gradle y dependencias;
- conteos XML, lint y APK;
- instrumentación realmente ejecutada o pendiente;
- dispositivos realmente usados, paquetes y limpieza;
- Room y hashes;
- riesgos concretos;
- cero commit y cero push.

No declares integrado lo que sólo está implementado. MAIN audita cada hunk,
repite pruebas proporcionales y decide el checkpoint.

## CONDITIONS STOP

Detenete y devolvé el caso concreto si aparece:

- HEAD/base/checkout incorrectos;
- cambios sin dueño;
- necesidad real de modificar Room;
- necesidad de Glance, WorkManager u otra dependencia;
- necesidad de permiso nuevo;
- contradicción entre el modo Automático y `projectNextEvent`;
- imposibilidad de privacidad por instancia;
- necesidad de exponer dirección o dato privado;
- prueba roja no acotable;
- QA obligatoria imposible;
- acción destructiva;
- Samsung/emulador sin autorización actual;
- alarma exacta real o reinicio físico;
- push, tag, Release, `main` o producción.

No inventes una solución silenciosa.

## DONE WHEN

La dependencia queda candidata para MAIN sólo cuando:

- los tres modos consumen la proyección compartida;
- varias instancias son independientes;
- compacto y ampliado funcionan;
- las tres privacidades no filtran datos;
- simultáneos, disponibilidad y franco se representan correctamente;
- `Chronometer` y una única frontera inexacta reemplazan polling;
- el Widget se actualiza sin depender de Notificaciones;
- Clima es opcional, cacheado y no bloqueante;
- configuración, reconfiguración, borrado y restauración están cubiertos;
- tocar abre el destino correcto y revalidado;
- consultas no escriben datos laborales;
- Room, esquemas, Gradle, permisos, SDK, versión y package permanecen intactos;
- pruebas, lint y ensamblados requeridos pasan;
- instrumentación/QA se informa con honestidad;
- `git diff --check` está limpio;
- el handoff completo llega a MAIN sin commit.

## PRIMERA RESPUESTA ESPERADA

En la primera respuesta:

1. confirmá las lecturas obligatorias;
2. informá Puerta 0 con ruta, rama, HEAD y limpieza;
3. confirmá que el prompt está HABILITADO;
4. resumí el código reutilizable y la ausencia de Widget actual;
5. declará si existe una contradicción o bloqueo;
6. proponé un plan corto por incrementos;
7. indicá qué pruebas ejecutarás antes de tocar la siguiente capa.

Después avanzá con autonomía dentro del alcance. No vuelvas a preguntarle a
Joaquin decisiones congeladas y no abras el bloque Informes.
