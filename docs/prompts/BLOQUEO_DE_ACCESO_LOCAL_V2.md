# Bloqueo de acceso local V2

- Estado: **CERRADO — INTEGRADO Y VERIFICADO POR MAIN**
- Fecha: 2026-09-01
- Cierre MAIN: 2026-09-01
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `7977913579cb92b9d3fefeb945274f312db9bd59`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Bloqueo de acceso local**

El cuerpo siguiente se conserva como contrato histórico del bloque ya cerrado.
La implementación aceptada, las correcciones de integración y la evidencia
final están registradas en
`docs/audits/2026-09-01-bloqueo-de-acceso-local-v2-main.md`.

## QUÉ HACE

Permite que la persona proteja la entrada a MiGuardia con la misma seguridad
que ya usa en su teléfono: huella apta o PIN, patrón o contraseña del
dispositivo. Puede elegir que la aplicación se cierre inmediatamente o después
de 1, 5 o 15 minutos, y también usar `Bloquear ahora`.

Cuando está cerrada, MiGuardia no muestra el Calendario ni otra información
laboral. Después de autenticar, vuelve al lugar exacto donde estaba o al destino
que abrió desde un aviso o Widget.

## POR QUÉ EXISTE

MiGuardia ya guarda jornadas, horarios, extras, disponibilidad, notas, fotos y
referencias médicas de manera local. El bloqueo del teléfono protege sus
archivos, pero no evita que otra persona abra la aplicación si recibe el equipo
ya desbloqueado.

Esta dependencia agrega esa última puerta de privacidad antes de completar la
Ayuda y el recorrido inicial. Existe después de Copias y restauración para que
la seguridad del dispositivo nunca se mezcle ni se transfiera dentro de una
copia portable.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, la arquitectura V2 ni la secuencia de
la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, la auditoría final, el staging y los checkpoints.

Tu responsabilidad exclusiva es proteger el acceso visible a MiGuardia. No
conviertas este bloque en cifrado de base, administración de identidades,
cuentas, control parental, muro de pago ni rediseño general de privacidad.

## TASK

Implementar integralmente **Bloqueo de acceso local** como una opción segura,
reversible y desactivada por defecto.

El recorrido mínimo debe permitir:

1. abrir `Bloqueo de acceso` desde la sección Aplicación del menú lateral;
2. comprender que usa la seguridad del teléfono y no una clave nueva;
3. comprobar si existe una credencial segura compatible;
4. elegir `Inmediatamente`, `Después de 1 minuto`, `Después de 5 minutos` o
   `Después de 15 minutos`;
5. confirmar la activación, autenticar una sola vez y guardar habilitación y
   plazo juntos;
6. cerrar MiGuardia automáticamente según esa elección;
7. usar `Bloquear ahora` sin esperar;
8. desbloquear con el diálogo del sistema;
9. autenticar nuevamente antes de desactivar o cambiar el plazo;
10. permanecer cerrado ante cancelación, error, bloqueo temporal o credencial
    no disponible;
11. volver de manera segura a la pantalla o destino pendiente después del
    éxito;
12. ocultar la información laboral en la vista de aplicaciones recientes;
13. conservar las preferencias de bloqueo del dispositivo durante cualquier
    combinación o reemplazo de una copia;
14. reabrir después de muerte de proceso siempre en estado cerrado cuando la
    función está activa.

No implementes Ayuda, onboarding, agenda profesional, pacientes, cifrado de
Room, borrado remoto, cuenta, nube, sincronización, monetización ni publicación.

## CONTEXT

La base cerrada ya posee:

- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola configuración laboral y una sola grilla mensual;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db`, veintisiete
  tablas y migraciones explícitas 1→2→3→4→5;
- horarios planificados y reales, extras, recurrencias y disponibilidad;
- feriados, vacaciones, carpetas médicas, estados, notas y fotos privadas;
- Resumen, próximo evento, notificaciones, Widget e Informes;
- Copias y restauración locales seguras con diecisiete preferencias portables;
- una recuperación temprana que termina antes de iniciar los runtimes;
- `MainActivity` como entrada principal;
- `WidgetConfigurationActivity` exportada para el launcher y protegida por
  validación de `appWidgetId`;
- destinos entrantes desde avisos y Widget que se revalidan contra la fuente V2;
- DataStore Preferences y SharedPreferences locales, `allowBackup=false`,
  `minSdk 26`, `compileSdk 37` y `targetSdk 37`.

El bloqueo es una frontera de interfaz. Notificaciones, Widget y Clima pueden
seguir funcionando en segundo plano y conservan sus propios niveles de
privacidad. Los informes y copias que el usuario exportó fuera de MiGuardia no
quedan bajo control de esta función.

## INPUTS

Leé completamente y en el orden obligatorio de `AGENTS.md`:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las cuatro fichas de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
9. ADR 0017 a 0036, con atención especial a 0024, 0026, 0032, 0033, 0034,
   0035 y
   `docs/adr/0036-bloqueo-local-con-autenticacion-del-dispositivo.md`;
10. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico V1;
11. prompts cerrados de próximo evento, notificaciones, Widget, Informes y
    Copias;
12. `MainActivity`, `MiGuardiaApplication`, `WidgetConfigurationActivity`,
    composición raíz, menú lateral, intents, preferencias, recuperación de
    copias, manifiesto, Gradle y pruebas afectadas.

Fuentes técnicas oficiales:

- [autenticación biométrica y credencial del dispositivo](https://developer.android.com/identity/sign-in/biometric-auth);
- [AndroidX Biometric](https://developer.android.com/jetpack/androidx/releases/biometric);
- [`KeyguardManager`](https://developer.android.com/reference/android/app/KeyguardManager);
- [`SystemClock.elapsedRealtime()`](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime());
- [`Activity.setRecentsScreenshotEnabled`](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean));
- [`FLAG_SECURE`](https://developer.android.com/security/fraud-prevention/activities#flag_secure).

No uses esta conversación ni un handoff anterior como sustituto de esas
fuentes.

## DEPENDENCIES

Podés asumir cerrados e integrados:

- el núcleo V2 y sus pruebas cruzadas;
- la matriz Android del núcleo;
- próximo evento y notificaciones;
- Widget de próximo evento;
- Informes locales;
- Copias y restauración locales seguras;
- ADR 0036 aceptada para implementación.

Se autoriza una única dependencia oficial nueva:

```text
androidx.biometric:biometric:1.1.0
```

Es la versión estable publicada por AndroidX al preparar este contrato. No uses
`biometric-ktx` alfa, una biblioteca de terceros ni otra dependencia de
autenticación.

## PUERTA 0

Antes de editar, verificá en vivo:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse '@{upstream}'
git rev-parse 'v1.0.0^{}'
git rev-parse main
git rev-parse origin/main
git status --short --branch
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
git worktree list --porcelain
git config --get user.name
git config --get user.email
git remote get-url origin
```

Debe coincidir:

- ruta exacta del proyecto;
- rama `codex/miguardia-2.0`;
- HEAD documental exacto informado por MAIN;
- upstream `origin/codex/miguardia-2.0`;
- checkout limpio, sin staged ni archivos nuevos;
- `v1.0.0^{}`, `main` y `origin/main` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor `joaquin <blackat.systems@gmail.com>`;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- ningún otro agente escribiendo en el mismo checkout.

Inspeccioná también JDK, SDK, wrapper, espacio y dispositivos disponibles sin
iniciar emuladores ni usar ADB. Detenete ante cualquier mismatch o cambio sin
dueño. No uses `reset --hard` ni descartes trabajo.

## DECISIONES CONGELADAS

### 1. Activación consciente y predeterminado seguro

- La función se llama visiblemente `Bloqueo de acceso`.
- Está desactivada por defecto en instalaciones nuevas y existentes.
- No aparece un prompt de autenticación hasta que la persona la activa.
- Antes de activar se puede elegir el plazo como borrador; `Inmediatamente` es
  la selección inicial.
- Confirmar la activación exige una sola autenticación válida y, sólo después
  del éxito, guarda atómicamente `enabled` y el plazo elegido.
- No pidas una segunda autenticación por esa elección inicial. Todo cambio de
  plazo posterior sí requiere autenticar antes de guardarlo.
- La pantalla explica que protege la entrada a la aplicación, no cifra la base
  ni los archivos exportados.

No agregues una sugerencia obligatoria durante la primera selección de rubro.
Ayuda y recorrido inicial pertenecen al bloque siguiente.

### 2. Autenticación del sistema, no PIN propio

MiGuardia usa el diálogo del sistema. No crea una pantalla para escribir un PIN
propio, no deriva una clave, no almacena hashes ni compara credenciales.

Autenticadores aceptados:

- biometría de clase fuerte que Android considere apta;
- PIN, patrón o contraseña segura del dispositivo.

La credencial del dispositivo es el respaldo normal. Una autenticación facial
sólo cuenta cuando Android la clasifica dentro de la fortaleza autorizada. No
uses `BIOMETRIC_WEAK` para simular que cualquier rostro es seguro.

La compatibilidad debe resolver expresamente las combinaciones admitidas:

- API 30 o superior: biometría fuerte o credencial del dispositivo mediante la
  API oficial;
- API 26 a 29: camino compatible probado, sin construir una UI de credencial ni
  tratar una combinación no soportada como éxito.

Abstraé la interacción detrás de una frontera inyectable. Las pruebas comunes
no deben depender de una huella real.

### 3. Teléfono sin credencial segura

Antes de activar, verificá la capacidad real.

Si no existe credencial segura:

- no guardes `enabled=true`;
- explicá `Primero configurá un bloqueo seguro en tu teléfono`;
- ofrecé `Abrir seguridad del teléfono` mediante un intent explícito a Ajustes;
- al volver, comprobá nuevamente sin asumir que el cambio ocurrió.

Si una persona elimina después la credencial compatible, MiGuardia permanece
cerrada y ofrece reintentar o abrir Ajustes. No se desbloquea automáticamente.

### 4. Cambios protegidos

Exigen una autenticación nueva:

- activar;
- desactivar;
- cambiar el plazo;
- reparar o restablecer únicamente las preferencias del bloqueo tras un error
  de lectura.

No exigen autenticación:

- `Bloquear ahora`;
- cancelar un cambio antes de guardarlo;
- salir de la aplicación desde la puerta cerrada.

Un doble toque no abre dos diálogos ni confirma dos escrituras. Una cancelación
o error conserva exactamente la configuración anterior.

### 5. Plazos exactos y reloj

Opciones exactas:

```text
Inmediatamente
Después de 1 minuto
Después de 5 minutos
Después de 15 minutos
```

No agregues `Nunca` dentro de una configuración habilitada. Para no bloquear se
desactiva la función conscientemente.

Medí tiempo fuera de primer plano con `SystemClock.elapsedRealtime()` mediante
un proveedor inyectable. No uses la hora civil, la zona, alarmas, polling,
WorkManager ni un servicio.

Reglas:

- `Inmediatamente` cierra al abandonar MiGuardia;
- los demás plazos cierran al volver cuando el límite se alcanzó;
- el límite es inclusivo: exactamente 1, 5 o 15 minutos ya cierra;
- cambiar manualmente fecha, hora o zona no extiende la sesión;
- muerte de proceso, proceso nuevo o reinicio nunca restaura autenticación;
- si el dispositivo estuvo bloqueado por Android, se exige autenticación al
  volver;
- una recreación por configuración dentro del mismo proceso no cuenta como una
  salida real y no puede inventar otra sesión;
- pasar entre `MainActivity` y `WidgetConfigurationActivity` comparte la misma
  sesión de aplicación y no pide dos veces por la transición interna;
- abrir SAF, selector de fotos, Ajustes, compartir o mapas sí sigue el plazo
  elegido; su resultado o borrador se conserva y se procesa sólo después de
  resolver la puerta al volver;
- el propio diálogo biométrico no se interpreta como abandono y no crea un
  ciclo de bloqueo.

### 6. Puerta realmente bloqueante

Mientras está cerrada:

- no compongas `MiGuardiaApp`, Calendario ni superficies laborales;
- no dejes sus textos, botones o contenido en semántica de accesibilidad;
- no proceses un intent que lea una jornada, dirección, fecha o archivo;
- no muestres datos detrás de una capa translúcida;
- no permitas que Atrás revele una pantalla previa;
- no conserves un drawer abierto ni un diálogo sensible visible.

La puerta muestra únicamente:

- marca genérica de MiGuardia;
- `MiGuardia está bloqueada`;
- explicación breve;
- `Desbloquear MiGuardia`;
- error o reintento seguro cuando corresponda.

Después del éxito, restaura la superficie y el borrador existente si el proceso
sigue vivo. Después de una recreación o muerte, sólo restaura lo que sus
contratos actuales ya guardan de forma segura; nunca serialices contenido
privado adicional para el bloqueo.

### 7. Arranque y recuperación de copias

El orden obligatorio es:

```text
recuperación temprana de copia pendiente
→ estado local coherente
→ lectura de preferencias de bloqueo
→ autenticación si corresponde
→ composición de MiGuardia
```

La superficie genérica de recuperación puede aparecer antes del bloqueo porque
no expone trabajo. Una vez que la recuperación termina, ningún dato laboral se
muestra sin resolver la puerta.

No retrases ni detengas los runtimes de Notificaciones, Widget o Clima sólo
porque la interfaz está cerrada. No uses el estado autenticado como condición
para mantener datos locales consistentes.

### 8. Destinos entrantes y actividades

Protegé por la misma política:

- apertura normal de `MainActivity`;
- toque de notificación;
- toque de Widget;
- apertura de fecha, jornada, dirección, Calendario o Clima;
- resultados de selectores SAF y compartir;
- `WidgetConfigurationActivity`, tanto alta como reconfiguración.

Un destino recibido mientras está bloqueada:

- no se ejecuta antes de autenticar;
- conserva sólo acción e identificadores mínimos;
- nunca se persiste en DataStore ni se registra;
- se revalida contra Room después del éxito;
- se consume exactamente una vez;
- ante dato eliminado, muestra el fallback seguro vigente.

No permitas que la actividad exportada del Widget habilite privacidad completa
o cambie una instancia sin autenticar cuando el bloqueo está activo.

### 9. Aplicaciones recientes y captura

Con bloqueo habilitado, la vista de aplicaciones recientes debe ser genérica y
no contener trabajo.

- API 33 o superior: usá `setRecentsScreenshotEnabled(false)` para la actividad
  protegida;
- API 26 a 32: aplicá una cobertura segura antes de ir al fondo y el mecanismo
  compatible necesario para que la captura residual no muestre contenido;
- mientras la puerta está visible, el contenido debe estar protegido de
  captura y display no seguro;
- al autenticarse en primer plano, una captura consciente puede volver a estar
  disponible: no apliques `FLAG_SECURE` de forma permanente a toda la sesión sin
  necesidad.

Probá que Recientes no conserva el último Calendario ni una pantalla sensible.
No consultes ni modifiques ajustes de captura del teléfono.

### 10. Persistencia del bloqueo

Creá un DataStore Preferences exclusivo de dispositivo. Puede persistir sólo:

- versión del contrato;
- habilitación;
- plazo elegido.

No persistas:

- credenciales, hashes, plantillas biométricas o material criptográfico;
- resultado o tipo de la última autenticación;
- estado `desbloqueado`;
- instante de salida;
- destino pendiente;
- datos laborales;
- intentos o bloqueos del sistema.

Las escrituras deben ser atómicas. Valores desconocidos, incompatibles o
incompletos no se convierten silenciosamente en `desactivado`.

Ante lectura corrupta o `IOException`:

- no compongas información laboral;
- mostrá un error genérico con reintento;
- permití restablecer sólo esas preferencias después de autenticar con la
  credencial del sistema;
- no borres Room, fotos, preferencias de trabajo, avisos, Widget ni copias.

### 11. Copias y restauración

El bloqueo es específico del dispositivo y queda fuera de `.miguardia-backup`.

Debés demostrar que:

- la lista portable sigue teniendo exactamente diecisiete claves semánticas;
- crear una copia no incluye habilitación ni plazo;
- `Combinar con mis datos` no cambia el bloqueo;
- `Reemplazar todo` tampoco lo cambia;
- restaurar no salta una puerta ya activa;
- una recuperación de journal no persiste una sesión autenticada;
- una instalación nueva restaurada continúa con bloqueo apagado hasta que el
  usuario lo active en ese dispositivo.

No cambies formato, versión, MIME, cifrado, comparator ni semántica de Copias.

### 12. Privacidad exterior y explicación honesta

La pantalla debe explicar:

- el bloqueo protege la entrada a MiGuardia;
- Widget y avisos conservan sus privacidades independientes;
- informes y copias ya guardados fuera de la app no quedan bloqueados;
- esta función no cifra la base ni reemplaza el bloqueo seguro del teléfono.

Ofrecé accesos a Notificaciones y Widget sólo mediante la navegación existente;
no dupliques sus controles dentro de Bloqueo.

No cambies automáticamente privacidad, canales, alarmas, widgets, clima o
archivos al activar o desactivar.

### 13. Interfaz y accesibilidad

La pantalla `Bloqueo de acceso` vive en la sección Aplicación del menú lateral.
Debe contener como mínimo:

- estado `Activado` o `Desactivado`;
- explicación de la credencial del teléfono;
- control consciente para activar/desactivar;
- cuatro plazos visibles;
- `Bloquear ahora` cuando está activo;
- estado de capacidad del dispositivo;
- explicación de Recientes, Widget, avisos y archivos externos;
- mensajes de cancelación, error y reintento en lenguaje común.

La puerta y la configuración deben:

- respetar Vigilia clara y oscura;
- funcionar en retrato y paisaje;
- funcionar con zoom interno 100 %, 150 % y 200 %;
- mantener controles alcanzables y textos sin recortes;
- tener roles, etiquetas y descripciones accesibles;
- no depender sólo del color;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del sistema.

### 14. Gradle, manifiesto y permisos

Se permite exclusivamente:

- agregar al catálogo y a `app` la dependencia estable
  `androidx.biometric:biometric:1.1.0`;
- adaptar la actividad base si la API oficial lo requiere sin cambiar su
  comportamiento público;
- declarar el permiso normal de biometría que la biblioteca oficial necesite.

No agregues permiso peligroso, Internet nuevo, servicio, receiver, provider,
WorkManager, librería criptográfica, AppCompat visual ni dependencia de
terceros.

Registrá en el handoff:

- coordenada y versión exacta;
- por qué es necesaria para API 26;
- licencia y mantenimiento oficial AndroidX;
- impacto de APK medido antes/después;
- dependencias transitivas nuevas;
- alternativa nativa descartada y razón.

## OUTPUT

El candidato debe dejar:

- estado y máquina pura del bloqueo;
- proveedor monotónico inyectable;
- frontera de autenticación del sistema inyectable;
- DataStore exclusivo y resistente a errores;
- coordinación de ciclo de vida y sesión;
- puerta Compose realmente bloqueante;
- pantalla visible `Bloqueo de acceso`;
- navegación desde el menú lateral;
- gating de `MainActivity` y `WidgetConfigurationActivity`;
- procesamiento diferido y único de intents;
- protección de Recientes compatible con API 26 y API 33+;
- integración explícita con recuperación de copias sin volver portable el
  ajuste;
- pruebas JVM e instrumentadas proporcionales;
- candidato sin staged, commit ni push;
- handoff autosuficiente a MAIN.

## SCOPE

Podés modificar únicamente lo necesario dentro de:

```text
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt
app/src/main/java/com/blackatsystems/miguardia/MiGuardiaApplication.kt
app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt
app/src/main/java/com/blackatsystems/miguardia/widget/WidgetConfigurationActivity.kt
app/src/main/java/com/blackatsystems/miguardia/backup/**
app/src/main/java/com/blackatsystems/miguardia/security/**
app/src/main/res/values/strings.xml
app/src/test/**
app/src/androidTest/**
```

La mención de `backup/**` autoriza sólo el test o adaptador mínimo para excluir
el bloqueo y conservar la recuperación; no autoriza rediseñar Copias.

No modifiques `core/domain` ni `core/database` salvo que MAIN autorice después
un defecto demostrable imposible de resolver en `app`. El bloqueo no necesita
una entidad Room.

## DO NOT

No hagas ninguna de estas acciones:

- crear un PIN, contraseña o patrón propio de MiGuardia;
- guardar, registrar, pedir por texto o intentar conocer la credencial del
  teléfono;
- aceptar biometría débil como fuerte;
- cifrar Room, DataStore, fotos o reportes;
- cambiar Room V5, tablas, entidades, DAO, migraciones o esquemas;
- cambiar el formato o las diecisiete preferencias portables de Copias;
- cambiar `allowBackup`, `backup_rules.xml` o `data_extraction_rules.xml`;
- cambiar privacidades de Widget o Notificaciones automáticamente;
- detener avisos o Widget por estar la interfaz cerrada;
- agregar cuentas, red, nube, sincronización, telemetría o logs privados;
- agregar Ayuda, onboarding, pacientes, agenda, monetización o borrado remoto;
- usar alarmas, polling, WorkManager o servicio para medir el plazo;
- enumerar o consultar qué biometrías, PIN, patrón o contraseña concretos tiene
  configurados el dispositivo; sólo podés consultar capacidad y estado seguro
  mediante las API oficiales. Tampoco modifiques hora, zona, `font_scale`,
  densidad o tamaño visual del dispositivo;
- bloquear capturas permanentemente cuando la persona está autenticada si una
  protección acotada resuelve Recientes;
- usar datos reales, notas reales, fotos personales o cronogramas reales;
- modificar `applicationId`, versión, SDK o firma;
- modificar `docs/STATUS.md`, `docs/prompts/README.md`, ADR, auditorías o
  documentación canónica;
- crear otra tarea, rama, worktree o subagente;
- hacer commit, push, merge, rebase, reset, tag o Release;
- tocar `main`, `v1.0.0` o producción;
- usar ADB, Samsung o emulador sin autorización nueva y explícita.

## VALIDATION

### Máquina de estado y reloj

Agregá pruebas JVM deterministas para:

- predeterminado desactivado;
- activación sólo después de éxito;
- cancelación/error sin cambio;
- cuatro plazos exactos;
- límites `menos un milisegundo`, `exactamente` y `más un milisegundo`;
- cambio de hora civil sin alterar el plazo;
- inmediato;
- `Bloquear ahora`;
- proceso nuevo y muerte de proceso;
- dispositivo bloqueado durante la pausa;
- diálogo biométrico sin ciclo de auto-bloqueo;
- doble toque y callback tardío ignorados;
- retorno a la pantalla previa tras éxito.

### Autenticación

Probá con fakes la traducción de:

- éxito biométrico fuerte;
- éxito por credencial del dispositivo;
- cancelación de usuario;
- cancelación del sistema;
- demasiados intentos o lockout;
- hardware no disponible;
- biometría no enrolada;
- credencial segura ausente;
- combinación no soportada en API antigua;
- error recuperable y error definitivo.

Ningún error desbloquea ni guarda una preferencia nueva.

### DataStore

Probá:

- lectura inicial;
- persistencia y reapertura;
- cada plazo;
- escritura atómica;
- valor desconocido;
- archivo corrupto o `IOException`;
- reintento;
- reparación autenticada limitada al store;
- ninguna sesión o destino persistidos;
- aislamiento del paquete QA.

### Navegación y composición

Probá que:

- la puerta no compone ni expone semánticas de `MiGuardiaApp`;
- Atrás no revela contenido;
- desbloquear vuelve a Calendario, Resumen, detalle o borrador vigente;
- un toque de notificación o Widget se consume una vez después del éxito;
- cancelar conserva el destino pendiente sin ejecutarlo;
- un destino eliminado usa el fallback vigente;
- recreación no duplica diálogo ni acción;
- una transición interna entre las dos Activities no fuerza otra autenticación
  mientras la sesión vigente continúe permitida;
- resultados SAF no se procesan antes de desbloquear;
- configuración inicial funciona con bloqueo apagado;
- `WidgetConfigurationActivity` no permite bypass;
- menú y navegación histórica no regresan.

### Recientes y privacidad

Agregá cobertura para:

- captura de Recientes deshabilitada en API 33+ cuando está activo;
- protección compatible en API 26–32;
- contenido genérico al ir al fondo;
- puerta sin textos laborales ni semánticas ocultas;
- captura consciente disponible al volver a una sesión autenticada cuando la
  plataforma permite separarla;
- activación sin cambios en preferencias de avisos o Widget.

### Copias

Probá expresamente:

- diecisiete preferencias portables exactas;
- bloqueo ausente del contenedor;
- combinar conserva `enabled` y plazo locales;
- reemplazar conserva `enabled` y plazo locales;
- recuperación temprana termina antes de la puerta;
- una restauración no crea una sesión autenticada;
- el error del store de bloqueo no corrompe ni borra datos laborales.

### Batería local

Ejecutá serializado y con repetición real:

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

Obtené conteos reales desde los XML. Separá:

- `JVM VERIFICADO`;
- `LINT`;
- `COMPILADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA`;
- `PENDIENTE`.

Ejecutá además:

```powershell
git diff --check
git status --short --branch
```

Auditá secretos, logs, credenciales, datos reales, dependencias, permisos y
artefactos generados. Medí el tamaño del APK QA antes y después con evidencia
reproducible.

### Room protegido

Verificá sin modificar:

- `MiGuardiaV2Database` versión 5;
- 27 tablas/entidades;
- `identityHash` `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- esquemas 1–5 byte a byte intactos;
- cero `fallbackToDestructiveMigration`;
- cero `allowMainThreadQueries`;
- cero escritores estructurales nuevos.

Hashes esperados:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## PHYSICAL QA

La instrumentación y QA de biometría, credencial, Recientes y ciclo de vida son
obligatorias para cerrar el bloque, pero sólo se ejecutan después de una
autorización nueva de Joaquin.

Con autorización, usá un dispositivo por vez y sólo paquetes QA/test.

### Samsung físico API 36

Recorrido mínimo con datos ficticios:

1. confirmar paquete y ausencia de producción;
2. bloqueo apagado sin prompt al arrancar;
3. activar autenticando con el diálogo real del sistema;
4. comprobar huella apta o credencial del dispositivo sin pedirle el secreto a
   Joaquin;
5. inmediato, 1, 5 y 15 minutos con reloj/control de prueba proporcional;
6. `Bloquear ahora`;
7. cancelar y reintentar;
8. fondo, Recientes, apagado/bloqueo de pantalla y retorno;
9. muerte de proceso y apertura fría;
10. toque de aviso y Widget hacia un destino ficticio exacto;
11. reconfiguración de Widget protegida;
12. retorno desde SAF sin fuga;
13. combinación y reemplazo de copia conservando el bloqueo local;
14. desactivar con nueva autenticación;
15. claro/oscuro, retrato/paisaje y zoom interno 100/150/200.

No provoques repetidos fallos biométricos que puedan bloquear el teléfono. No
cambies, enumeres, leas ni captures el PIN, patrón, contraseña o las biometrías
configuradas. La comprobación general de capacidad mediante las API oficiales
sí está permitida.

### Android 8/API 26

Con autorización de emulador, verificá el camino compatible:

- credencial segura disponible y no disponible;
- activación, desbloqueo, cancelación y cierre inmediato;
- Recientes sin contenido laboral;
- proceso nuevo;
- navegación pendiente;
- regresiones esenciales de Calendario y Copias.

### Android 13/API 33

Con autorización, verificá:

- autenticación moderna;
- `setRecentsScreenshotEnabled(false)`;
- notificación/Widget hacia puerta y destino;
- rotación y recreación;
- aislamiento QA.

API 37 puede quedar para el candidato final salvo autorización específica. Un
reinicio físico del Samsung sigue siendo una puerta separada; muerte de proceso
no equivale a reboot.

## DEVICE SAFETY

Si recibís autorización:

- identificá serial, modelo y API antes de actuar;
- usá únicamente `com.blackatsystems.miguardia.qa` y paquetes test;
- nunca instales, abras, consultes, limpies, reemplaces ni desinstales
  producción;
- no pidas ni captures la credencial del dispositivo;
- no agregues, elimines ni cambies huellas, rostro, PIN, patrón o contraseña;
- usá sólo datos ficticios;
- no consultes ni modifiques `font_scale`, densidad, tamaño visual, hora, zona,
  Wi-Fi o red;
- restaurá orientación y tema si una prueba los cambia;
- retirá paquetes QA/test al finalizar salvo orden contraria;
- informá exactamente qué quedó en cada dispositivo.

## STOP CONDITIONS

Detenete y devolvé `MAIN BLOQUEADA` si aparece:

- contradicción entre fuentes activas;
- necesidad de PIN o secreto propio;
- imposibilidad de ofrecer credencial del dispositivo como recuperación;
- única solución basada en biometría débil presentada como fuerte;
- bypass desde `MainActivity`, Widget, aviso, SAF o actividad exportada;
- imposibilidad de ocultar contenido sensible de Recientes en una API soportada;
- lectura fallida que sólo pueda resolverse desbloqueando en silencio;
- necesidad de cambiar Room, esquemas, migraciones o formato de Copias;
- necesidad de otra dependencia, permiso peligroso, servicio o red;
- cambio de privacidad pública no autorizado;
- checkout sucio de origen desconocido;
- validación roja no corregible dentro del alcance;
- necesidad de conocer o cambiar credenciales del teléfono;
- dispositivo sin autorización, acción destructiva, push, tag, Release, `main`
  o producción.

No inventes conciliaciones ni rebajes la seguridad en silencio.

## HANDOFF A MAIN

Entregá un handoff autosuficiente con estas secciones exactas:

```text
# HANDOFF A MAIN — Bloqueo de acceso local V2

## QUÉ HACE
## POR QUÉ EXISTE
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## AUTHENTICATION AND LIFECYCLE
## PRIVACY AND BACKUPS
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

- resultado funcional real;
- archivos modificados, nuevos y eliminados;
- coordenada, versión, transitivas e impacto de APK de AndroidX Biometric;
- autenticadores y compatibilidad por API;
- plazos y semántica exacta del reloj;
- comportamiento de arranque, proceso, Recientes e intents pendientes;
- datos persistidos y excluidos;
- evidencia de que las copias conservan diecisiete preferencias;
- conteos JVM reales;
- separación entre AndroidTest compilado y ejecutado;
- estado de Room y hashes;
- estado exacto Git y de dispositivos;
- riesgos y pendientes honestos.

Dejá el candidato directamente en el checkout compartido, sin staged, commit o
push. No hay nada para `cherry-pick`.

## DONE WHEN

El candidato está listo para volver a MAIN sólo cuando:

- bloqueo está apagado por defecto;
- activar, desactivar y cambiar plazo requieren autenticación;
- no existe PIN, contraseña, hash ni secreto propio de MiGuardia;
- biometría fuerte o credencial del dispositivo funcionan según API;
- teléfono sin credencial no queda atrapado ni se habilita en falso;
- los cuatro plazos y `Bloquear ahora` son deterministas;
- proceso nuevo, muerte o bloqueo del dispositivo vuelven a cerrar;
- la puerta no compone ni expone información laboral;
- intents y actividades no permiten bypass y se consumen una vez;
- Recientes nunca conserva contenido laboral con el bloqueo activo;
- DataStore falla de forma segura y puede repararse sin borrar trabajo;
- Widget, avisos y archivos externos conservan sus controles independientes;
- Copias mantiene diecisiete preferencias y no transporta el bloqueo;
- Room V5, 27 tablas y esquemas permanecen intactos;
- sólo se agregó AndroidX Biometric 1.1.0 y el permiso normal necesario;
- batería local queda verde;
- instrumentación/QA requerida se ejecutó o queda honestamente `PENDIENTE` por
  falta de autorización;
- no hubo commit, push ni dispositivo sin autorización.

MAIN sólo cierra la dependencia después de auditar cada hunk, repetir pruebas
proporcionales y ejecutar la matriz Android autorizada.

## PRIMERA RESPUESTA ESPERADA

Antes de implementar, respondé brevemente:

1. resultado de Puerta 0;
2. HEAD exacto recibido;
3. confirmación de lectura completa de fuentes;
4. mapa actual de actividades, lifecycle, intents, recuperación y stores;
5. diseño propuesto de máquina de estado, autenticador, DataStore y Recientes;
6. matriz de compatibilidad API 26/29/30+/33+;
7. archivos previstos;
8. pruebas previstas;
9. confirmación de que no usarás dispositivos, credenciales, commit ni push sin
   autorización.

No empieces a editar si esa respuesta revela una contradicción, bypass o
decisión material todavía ausente.
