# 21 — Ayuda y recorrido inicial V2

- Estado: **HABILITADO — IMPLEMENTACIÓN PENDIENTE**
- Fecha: 2026-09-02
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `b64f07a6a92ad16f789eceb395c469239ee46eb4`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Ayuda y recorrido inicial 2.0**

## QUÉ HACE

Agrega una Ayuda clara y permanente dentro de MiGuardia y un recorrido breve
que presenta las funciones principales cuando la persona termina de preparar
su primer lugar y horario de trabajo.

La instalación nueva sigue empezando por elegir el rubro. El recorrido puede
omitirse, no carga datos por su cuenta y después puede repetirse desde
`Ayuda`.

## POR QUÉ EXISTE

MiGuardia ya permite configurar el trabajo, organizar jornadas, registrar
horas y extras, entender el mes, recibir avisos, usar un Widget, generar
informes, hacer copias y proteger el acceso. Falta explicar ese conjunto sin
obligar a la persona a descubrir sola dónde está cada función.

Esta dependencia existe después de estabilizar toda la interfaz para no enseñar
controles viejos o lugares que después cambien. Al cerrarla, MAIN podrá auditar
la aplicación completa y decidir si existe un candidato local de MiGuardia 2.0.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, la arquitectura, los cuatro rubros ni la secuencia de la
hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama, worktree, tarea ni subagente. MAIN conserva la documentación
canónica, los ADR, las auditorías, el staging y los checkpoints.

Tu responsabilidad exclusiva es implementar la Ayuda y el recorrido inicial
sobre las superficies V2 reales. No rediseñes la aplicación ni conviertas la
guía en otra forma de cargar datos.

## TASK

Implementar integralmente **Ayuda y recorrido inicial 2.0**.

El resultado mínimo debe permitir:

1. conservar el selector de rubro como primera decisión visible de una
   instalación nueva;
2. conservar el flujo real de primer lugar, tipo y horario;
3. mostrar una sola vez la guía inicial cuando la configuración sea `V2Ready`,
   `WorkSetupSurface` haya vuelto a `NONE` y su versión todavía esté pendiente;
4. omitir la guía de forma consciente y entrar al Calendario;
5. presentar tres ideas breves sobre organización, lectura del trabajo y
   privacidad local;
6. recorrer contextualmente los controles principales reales, sin simular
   datos ni ejecutar mutaciones;
7. finalizar en el Calendario en modo consulta;
8. abrir una superficie real `Ayuda` desde el grupo `Aplicación` del menú
   lateral;
9. consultar temas de ayuda sencillos sobre todas las capacidades V2 vigentes;
10. repetir el recorrido desde Ayuda sin reactivar el bloqueo automático de la
    primera apertura;
11. conservar el paso visible ante recreación y evitar escrituras duplicadas;
12. ofrecer reintento seguro ante errores de lectura o escritura del estado de
    la guía.

No implementes una segunda configuración laboral, una segunda grilla, una
primera carga paralela, soporte remoto, pacientes, agenda profesional,
monetización, publicación ni funciones nuevas de negocio.

## PUERTA 0

Antes de editar, verificá:

- ruta obligatoria:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama obligatoria: `codex/miguardia-2.0`;
- base funcional ancestro:
  `b64f07a6a92ad16f789eceb395c469239ee46eb4`;
- HEAD inicial: el SHA exacto del checkpoint documental que MAIN debe incluir
  en el mensaje de despacho;
- estado inicial esperado: checkout limpio, cero staged y cero no rastreados;
- evidencia inicial esperada: `git diff`, `git diff --cached` y
  `git ls-files --others --exclude-standard` sin contenido;
- archivos premodificados autorizados: ninguno;
- modo: checkout compartido, un solo escritor; MAIN no edita mientras esta
  dependencia está activa;
- worktrees registrados, upstream, remoto y autor Git;
- instrucciones `AGENTS.md` aplicables.

Si MAIN no informó el SHA documental exacto, si HEAD no coincide, si la base no
es ancestro, si el estado no está limpio o aparece cualquier trabajo ajeno,
detenete. No intentes corregir la divergencia mediante reset, clean, checkout,
rebase ni descarte.

## CONTEXT

La base cerrada ya posee:

- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- un selector obligatorio de rubro para una instalación limpia;
- `WorkSetupState.FreshInstall`, `V2NeedsFirstSet` y `V2Ready`;
- creación atómica del primer lugar, tipo y horario mediante el flujo
  `WorkSetup` existente;
- una sola grilla mensual y detalle único del día;
- carga manual y múltiple, recurrencias, edición y eliminación exactas;
- horario real, extras de jornada e independientes y avance de horas;
- guardias pasivas o disponibilidad;
- tarjeta de hoy, Resumen, próximo evento y notificaciones;
- Clima, Widget, Informes, Copias y restauración y Bloqueo de acceso;
- una entrada para restaurar una copia desde el selector inicial;
- recuperación de copias antes de iniciar el resto de la aplicación;
- una puerta de bloqueo que debe resolverse antes de componer contenido
  laboral;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db`, 27 tablas y
  migraciones explícitas `1→2→3→4→5`;
- 17 preferencias semánticas portables dentro de `.miguardia-backup`;
- `minSdk 26`, `compileSdk 37`, `targetSdk 37` y Java 17.

La primera configuración ya existe y es dueña de sus datos. Esta dependencia
no debe volver a pedir el sector, el lugar, el tipo ni el horario dentro de un
formulario tutorial. Después de guardar el primer conjunto, la superficie
`WorkSetupSurface.COMPLETION` continúa mostrando `Volver al Calendario`,
`Agregar otro horario` y `Agregar otro lugar`; la guía no puede taparla ni
cerrarla.

## INPUTS

Antes de modificar, leé completamente y en este orden:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
7. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
8. `docs/adr/0037-ayuda-y-recorrido-inicial-versionado-v2.md`;
9. ADR 0014 y ADR 0015 como antecedentes históricos;
10. ADR 0024, ADR 0035 y ADR 0036;
11. `docs/prompts/PRIMERA_APERTURA_Y_CONFIGURACION_LABORAL_VISIBLE_V2.md`;
12. `docs/prompts/ONBOARDING_Y_PRIMERA_CARGA.md` sólo como contrato histórico
    V1 que este prompt reemplaza;
13. código y pruebas actuales de `MiGuardiaApplication`, `MainActivity`,
    `MiGuardiaApp`, `WorkSetup`, menú lateral, Calendario, Resumen, Copias,
    Bloqueo, tema y zoom.

La jerarquía vigente de `AGENTS.md` prevalece. No recuperes desde el prompt V1
Perfil laboral, Objetivos y horarios, combinaciones, `Cargar datos`,
`Guardia/Francos`, navegación inferior, una pantalla contenedora
`Configuración`, `MIGRATED_V1` ni conteos antiguos de Room.

## OUTPUT

Entregá un candidato ejecutable, directamente en el checkout compartido y sin
commit, que incluya:

- persistencia local y versionada del estado completado;
- coordinación de carga, introducción, recorrido, repetición y errores;
- pantallas Compose para la introducción, el recorrido y Ayuda;
- integración mínima con el estado raíz y el menú lateral;
- textos y semántica accesible;
- pruebas JVM, DataStore, Compose y Activity proporcionales;
- un handoff autocontenido a MAIN.

No modifiques documentación canónica. Si un contrato imprescindible falta o
obliga a cambiar una frontera prohibida, devolvé `MAIN BLOQUEADA` con evidencia
concreta.

## DECISIONES FUNCIONALES CONGELADAS

### 1. Orden de una instalación nueva

El orden exacto es:

1. recuperación pendiente de copias, si existe;
2. bloqueo de acceso, si está habilitado;
3. selector de rubro de `FreshInstall`;
4. Calendario vacío y flujo real para crear el primer lugar, tipo y horario;
5. pantalla de finalización real y cualquier alta adicional que la persona
   elija;
6. después de `Volver al Calendario` —o de otra salida consciente que deje
   `WorkSetupSurface.NONE`—, guía inicial si el estado es `V2Ready` y su versión
   está pendiente;
7. Calendario normal.

La guía nunca aparece antes del selector, nunca tapa un error de configuración
y nunca reemplaza `V2NeedsFirstSet`. Observar `V2Ready` mientras
`WorkSetupSurface.COMPLETION`, `ADDITIONAL_TEMPLATE`, `ADDITIONAL_PLACE` u otra
superficie de configuración sigue abierta no habilita la guía automática.

Una instalación ya configurada que todavía no posea la versión completada ve
la guía una sola vez. Una copia restaurada puede mostrarla nuevamente porque la
marca no es portable.

### 2. Introducción breve

La introducción comunica tres ideas, en este orden:

1. **Organizá tu trabajo**: una sola grilla para cargar, repetir, consultar,
   corregir o eliminar jornadas; horario real, extras y disponibilidad viven
   sobre esa misma historia.
2. **Entendé lo que viene y lo que hiciste**: tarjeta de hoy, Horas, Resumen,
   próximo evento, avisos, Widget e Informes reutilizan la información ya
   guardada.
3. **Tus datos quedan bajo tu control**: MiGuardia es local, no exige cuenta ni
   nube; Copias permite recuperar datos y Bloqueo protege la entrada.

Cada paso tiene texto breve, progreso comprensible, `Atrás` y `Siguiente`. El
último ofrece `Ver recorrido`. Durante la guía automática existe una acción
visible `Omitir guía`.

En el primer paso, Atrás o el gesto del sistema pide confirmar la salida. Salir
equivale a omitir y sólo se concreta si la versión pudo guardarse. En los demás
pasos, Atrás retrocede sin perder el modo ni producir escrituras.

No agregues una demora artificial, video obligatorio, carrusel decorativo
pesado ni ilustraciones que retrasen el contenido.

### 3. Recorrido contextual

El recorrido se apoya en controles reales y enseña sólo lo necesario para
orientarse:

1. botón `Abrir menú` y sus tres grupos;
2. tarjeta superior del día;
3. mes, navegación temporal y única grilla;
4. detalle de un día y acceso a sus notas cuando estén disponibles;
5. superficie separada de Fotos del cronograma mensual;
6. acción real `Cargar jornadas` y acceso a planes recurrentes;
7. `Resumen` y explicación de que cada cifra puede abrir su detalle;
8. entrada `Ayuda` para volver a consultar o repetir la guía.

Puede abrir y cerrar el menú o cambiar temporalmente entre Calendario, Resumen
y Ayuda para señalar un control. Al finalizar vuelve al Calendario en modo
consulta.

La capa del recorrido intercepta acciones no autorizadas. No crea jornadas,
lugares, horarios, extras, disponibilidades, preferencias funcionales,
archivos, avisos, widgets ni permisos. No abre selectores del sistema. No
requiere datos ficticios.

Los focos deben anclarse por estado y semántica estable, no por coordenadas
fijas de un teléfono. Si un control no está disponible en ese estado, el paso
explica dónde encontrarlo o se omite de manera determinista; nunca fabrica el
contenido ausente.

En la guía automática, `Omitir guía` desde cualquier paso confirma la salida,
guarda la versión actual una sola vez y abre el Calendario. `Finalizar` en el
último paso hace lo mismo sin confirmación adicional.

### 4. Ayuda permanente

`Ayuda` aparece exactamente una vez dentro del grupo `Aplicación` del menú
lateral y es una superficie real, no un placeholder.

Permanece disponible tanto en `V2NeedsFirstSet` como en `V2Ready`. En el primer
estado explica cómo terminar el primer lugar y horario, pero no inicia
automáticamente el recorrido completo.

Debe ofrecer temas breves y navegables:

- Primeros pasos y Mi forma de trabajar;
- Calendario, jornadas, feriados, vacaciones, notas y Fotos;
- Horario real, horas extra y disponibilidad;
- Horas, Resumen y tarjeta de hoy;
- Notificaciones, Clima y Widget;
- Informes locales;
- Copias y restauración;
- Bloqueo de acceso y privacidad;
- Apariencia y zoom interno.

Cada tema explica qué hace la función, dónde se abre y una precaución relevante
cuando corresponda. No repite formularios ni muestra datos concretos del
usuario.

La acción `Repetir recorrido inicial` abre la introducción y el recorrido en
modo repetición. En ese modo:

- no aparece `Omitir guía`;
- cerrar vuelve a Ayuda;
- finalizar vuelve a Ayuda;
- no borra, disminuye ni vuelve a escribir innecesariamente la versión ya
  completada.

Quedan fuera búsqueda global, preguntas frecuentes remotas, soporte por chat,
reporte automático, adjuntos, envío de diagnósticos y enlaces comerciales.

### 5. Persistencia versionada

Usá un DataStore Preferences exclusivo:

- archivo `onboarding.preferences_pb`;
- clave entera `completed_version`;
- versión actual inicial `1`;
- flujo observable;
- escritura atómica y monotónica;
- una versión futura nunca se degrada;
- `IOException` se traduce a un estado seguro con `Reintentar`;
- `CancellationException` se propaga;
- constructor interno con `File` y `CoroutineScope` para pruebas aisladas;
- una sola instancia dueña en `MiGuardiaApplication`.

El paso visible puede sobrevivir a recreación mediante `SavedStateHandle` o
estado guardable equivalente, pero no se persiste como progreso de negocio.
Compose no escribe directamente en DataStore ni Room.

La marca es deliberadamente no portable:

- no se agrega a las 17 preferencias de `.miguardia-backup`;
- no cambia el formato ni la versión de las copias;
- combinar o reemplazar datos no escribe la marca;
- la recuperación de journal termina antes de leerla;
- una restauración en otro dispositivo puede mostrar el recorrido otra vez.

### 6. Errores, concurrencia y ciclo de vida

- Mientras se lee el estado, no mostrar fugazmente el Calendario ni la guía
  equivocada.
- Leer la marca no demora ni reemplaza `FreshInstall` o `V2NeedsFirstSet`;
  únicamente `V2Ready` espera una decisión segura antes de mostrar su contenido
  principal.
- Error de lectura: superficie estable, mensaje simple y `Reintentar`.
- Error al completar u omitir: conservar el paso, explicar que no se guardó y
  permitir reintento.
- Doble toque: una sola escritura y una sola navegación.
- Resultado tardío después de cerrar o cambiar de modo: ignorarlo si ya no
  pertenece a la sesión activa.
- Un destino pendiente de aviso o Widget no se consume detrás de la guía: se
  conserva sólo en memoria y, después de completar u omitir, se revalida y se
  consume una sola vez.
- Recreación, rotación y recomposición: conservar paso y modo sin repetir
  efectos.
- Cambio concurrente de `WorkSetupState`: nunca mostrar la guía sobre
  `FreshInstall`, `Loading`, `LoadError` o `V2NeedsFirstSet`.
- Si la configuración deja de ser `V2Ready`, cerrar el recorrido de forma
  segura sin marcarlo como completado.

### 7. Bloqueo, privacidad y permisos

La recuperación de copias y el Bloqueo de acceso conservan prioridad sobre
Ayuda y el recorrido. Cuando MiGuardia está bloqueada, ninguna pantalla ni
semántica de la guía se compone detrás de la puerta.

El recorrido no solicita permisos. Notificaciones, alarmas exactas, fotos,
archivos y Clima conservan sus explicaciones y solicitudes dentro de sus
superficies dueñas.

No mostrar ni registrar nombres, lugares, horarios, notas, motivos médicos,
direcciones, fotografías, contenido de copias ni datos reales. No agregar logs
con rutas o estado laboral.

## SCOPE

Podés modificar solamente lo imprescindible dentro de:

- `app/src/main/java/com/blackatsystems/miguardia/MiGuardiaApplication.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/MainActivity.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`;
- un paquete nuevo y claro bajo `app/src/main/java/.../ui/help/` o
  `app/src/main/java/.../onboarding/`;
- `app/src/main/res/values/strings.xml` y recursos visuales propios mínimos;
- `app/src/test/**`;
- `app/src/androidTest/**`.

Se permiten ajustes mínimos en pruebas existentes de navegación, primera
configuración, destinos pendientes, Copias y Bloqueo para cubrir la integración
real. Los fixtures Activity que parten de `V2Ready` pueden marcar de manera
determinista la versión actual como completada; sólo las pruebas propias de la
guía deben comenzar con esa marca ausente.

No modifiques `core/domain`, `core/database`, Room, esquemas, migraciones,
Gradle, manifiesto, permisos, dependencias, `applicationId`, versión ni SDK.

## DEPENDENCIES

Podés asumir como contratos cerrados:

- `WorkSetupState` y el flujo visible de primera configuración;
- la navegación y el menú lateral actuales;
- Calendario, detalle del día y carga V2;
- Horas, Resumen y proyección de próximo evento;
- Notificaciones, Clima, Widget e Informes;
- Copias y restauración con 17 preferencias portables;
- Bloqueo de acceso y su puerta previa al contenido;
- tema Vigilia y zoom interno 100 %, 150 % y 200 %.

No copies lógica de esos módulos. Ayuda puede describirlos y el recorrido puede
señalar sus accesos, pero cada superficie conserva su dueño.

## DO NOT

- no mostrar la guía antes del selector de rubro;
- no agrupar Enfermería y Medicina;
- no crear `Salud`, `Otro` ni otro sector;
- no volver a pedir el sector dentro de la guía;
- no duplicar la creación del primer lugar, tipo u horario;
- no crear datos, fixtures productivas ni permisos desde el recorrido;
- no reintroducir Perfil, Objetivos y horarios, combinaciones o rutas V1;
- no crear una barra inferior ni una pantalla contenedora Configuración;
- no crear un segundo Calendario ni otro motor de navegación;
- no modificar Room V5, sus 27 tablas o sus esquemas;
- no modificar las 17 preferencias portables ni el formato de copias;
- no transportar la marca de onboarding en una copia;
- no debilitar, omitir ni duplicar la puerta del Bloqueo;
- no agregar dependencias, servicios, receivers, permisos, red o telemetría;
- no implementar soporte remoto, cuentas, nube o sincronización;
- no implementar agenda profesional, pacientes, Psicología ni datos clínicos;
- no agregar salarios, montos, liquidaciones o información sindical;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del sistema;
- no modificar `docs/STATUS.md`, `docs/prompts/README.md`, ADR ni auditorías;
- no crear commit, push, tag, merge, rebase, reset ni descartar trabajo;
- no usar Samsung, ADB o emuladores sin una autorización nueva y expresa de
  Joaquin;
- no tocar producción ni usar datos reales;
- no crear otra tarea ni subagente.

## STOP POINTS

Detenete y devolvé evidencia concreta si:

- ruta, rama, HEAD, base, limpieza, upstream, remoto o autor no coinciden con
  Puerta 0;
- MAIN no informó el SHA exacto del checkpoint documental;
- aparece un archivo premodificado, staged, no rastreado o cambio sin dueño;
- el prompt no figura `HABILITADO` en `docs/prompts/README.md`;
- el objetivo exige tocar un archivo o una capacidad fuera de `SCOPE`;
- falta una decisión funcional material o dos fuentes vigentes se contradicen;
- una prueba obligatoria queda roja o una corrección exigiría cambiar un
  contrato protegido;
- la validación requiere Samsung, ADB o emulador sin una autorización nueva;
- hace falta una acción destructiva o trabajar con datos reales;
- la siguiente acción sería commit, push, tag, Release, producción, merge,
  rebase, reset, descarte, otra rama, worktree, tarea o subagente.

## VALIDATION

### Repetición de Puerta 0

Antes de editar, repetí la Puerta 0 principal y devolvé:

- ruta, rama y HEAD exactos;
- relación con la base funcional y el checkpoint documental de MAIN;
- upstream y divergencia;
- estado limpio, staged y no rastreados;
- worktrees registrados;
- remoto privado y autor Git;
- JDK, SDK, wrapper y espacio disponible;
- ausencia de otra tarea implementadora o candidato sin dueño.

Detenete ante un mismatch, cambios desconocidos o un prompt que no figure
`HABILITADO` en `docs/prompts/README.md`.

### JVM

Probar como mínimo:

1. versión ausente o menor queda pendiente;
2. versión actual o futura no dispara la guía automática;
3. completar y omitir guardan la versión actual una sola vez;
4. una versión futura nunca se reduce;
5. repetición no modifica la marca;
6. error de lectura y escritura conserva un estado seguro y reintentable;
7. cancelación no se convierte en error;
8. doble acción no produce dos escrituras ni dos efectos;
9. `FreshInstall`, `LoadError` y `V2NeedsFirstSet` tienen prioridad;
10. sólo `V2Ready` pendiente con `WorkSetupSurface.NONE` habilita la guía
    automática;
11. el modo repetición vuelve a Ayuda;
12. un resultado tardío de otra sesión se ignora;
13. un destino pendiente espera la guía y después se consume una sola vez.

### DataStore instrumentado

- archivo aislado inicia pendiente;
- completar persiste versión 1;
- reapertura conserva el valor;
- versión futura se conserva;
- lectura corrupta o fallida no se interpreta como completada;
- archivos y scopes de prueba no tocan QA real.

### Compose y Activity

- una instalación nueva sigue viendo primero los cuatro rubros exactos;
- restaurar una copia continúa disponible desde el selector;
- `V2NeedsFirstSet` conserva el Calendario vacío y su flujo actual;
- la pantalla `COMPLETION` conserva sus tres acciones aunque la raíz ya sea
  `V2Ready`;
- elegir `Agregar otro horario` o `Agregar otro lugar` no dispara la guía;
- después de `Volver al Calendario`, `V2Ready` pendiente muestra la introducción
  una sola vez;
- introducción: Atrás, Siguiente, Ver recorrido y Omitir;
- recorrido: menú, tarjeta, mes/grilla, detalle, carga, Resumen y Ayuda;
- focos ausentes se resuelven sin coordenadas fijas ni contenido falso;
- el recorrido no invoca callbacks de mutación;
- finalizar vuelve al Calendario en consulta;
- Ayuda aparece exactamente una vez en el grupo Aplicación;
- todos los temas acordados son alcanzables;
- repetir no muestra Omitir y vuelve a Ayuda;
- errores, reintento, doble toque, recreación y rotación;
- Bloqueo activo impide componer contenido de Ayuda o recorrido;
- un destino de aviso o Widget permanece pendiente hasta completar u omitir y
  después se revalida una sola vez;
- Copias conserva exactamente sus 17 preferencias portables;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200 mantienen texto y
  acciones alcanzables;
- semántica comprensible y no dependiente únicamente del color.

### Regresiones vecinas

- `WorkSetupComposeTest` y primera configuración;
- `NavigationDrawerComposeTest` y jerarquía única;
- Calendario, tarjeta y detalle;
- Resumen;
- Copias y restauración;
- Bloqueo de acceso;
- recreación de `MainActivity`.

### Batería local

Ejecutá serialmente y con tareas repetidas:

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

Obtené los conteos reales desde XML. Informá por separado:

- `JVM VERIFICADO`;
- `LINT`;
- `COMPILADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA`;
- `PENDIENTE`.

### Room y contratos protegidos

Verificá que Room continúe en versión 5, con 27 tablas, `identityHash`
`77adbc875d0f4ee466cdbd0dd74d5c5c` y estos SHA-256 intactos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

Confirmá también:

- cero cambios en Gradle, manifiesto, permisos, SDK, paquete o versión;
- cero cambios al formato de `.miguardia-backup` y a sus 17 preferencias;
- cero escritores Room nuevos;
- cero logs privados, secretos, red o telemetría;
- `git diff --check` limpio.

### QA física

La instrumentación y revisión física requieren una autorización nueva de
Joaquin. La compilación de AndroidTest no equivale a ejecución.

Si MAIN o Joaquin la autorizan, usar exclusivamente Samsung `SM-S938B`, paquete
QA y datos ficticios. Recorrer:

- instalación limpia: rubro, primer lugar y horario, pantalla de finalización,
  `Volver al Calendario`, guía y Calendario;
- usuario ya configurado que recibe la guía una sola vez;
- omitir, finalizar, reapertura y repetición desde Ayuda;
- navegación contextual sin mutaciones;
- Bloqueo activo antes del recorrido;
- recuperación de copia y aparición posterior de la guía no portable;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200.

No consultar ni modificar escala, densidad o tamaño visual del sistema. No
limpiar, abrir, instalar, reemplazar ni desinstalar producción. Informar qué
paquetes QA quedan al finalizar.

## HANDOFF A MAIN

Devolvé un informe autocontenido con estas secciones exactas:

```text
# HANDOFF A MAIN — Ayuda y recorrido inicial V2

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

En `VALIDATION`, separá claramente lo ejecutado de lo sólo compilado. En
`FILES`, enumerá modificados, nuevos y eliminados. En `GIT`, informá ruta,
rama, HEAD de entrada y final, upstream, divergencia, staged y estado exacto.

No declares el bloque integrado ni la aplicación candidata. El resultado debe
quedar sin commit directamente en el checkout compartido para que MAIN audite
cada hunk.

## DONE WHEN

La dependencia está lista para entregar a MAIN solamente cuando:

- el selector de rubro continúa siendo la primera decisión visible;
- el primer lugar y horario siguen usando `WorkSetup` sin duplicación;
- la guía automática aparece sólo en `V2Ready` pendiente con
  `WorkSetupSurface.NONE`, sin tapar la pantalla de finalización;
- completar u omitir persiste una marca versionada una sola vez;
- Ayuda existe, explica las capacidades V2 reales y permite repetir;
- el recorrido usa controles reales, no escribe datos ni solicita permisos;
- recuperación y Bloqueo conservan prioridad;
- Copias mantiene exactamente su formato y 17 preferencias portables;
- recreación, errores y doble toque son seguros;
- claro/oscuro, orientación y zoom interno conservan todas las acciones;
- pruebas y builds exigidos están verdes;
- Room, Gradle, manifiesto, permisos, dependencias, paquete, versión y SDK no
  cambiaron;
- no hay datos reales, secretos, logs privados ni alcance futuro adelantado;
- el diff queda limpio de whitespace, sin staged, commit ni push;
- el handoff devuelve el candidato exclusivamente a MAIN.
