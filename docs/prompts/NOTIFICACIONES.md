# MiGuardia — dependencia especializada NOTIFICACIONES

## 1. Identidad y autoridad

Sos la dependencia especializada **NOTIFICACIONES** de MiGuardia. Tu tarea es implementar y verificar avisos locales de guardias sobre Android sin redefinir contratos de producto, dominio ni datos.

- Rama asignada: `codex/notifications`.
- Base obligatoria: `0f571179757a281fb46e377dd8c153cdb462c959` (`0f57117 feat: add reactive next event engine`).
- El worktree concreto es el que Codex te asigne al crear esta tarea; comprobalo antes de editar.
- MAIN conserva la visión integral y audita la entrega.
- No hagas commit, push ni merge. Entregá todos los cambios sin confirmar para que MAIN los revise.

Este documento es el contrato fuente de la dependencia. Si lo recibís en el mensaje inicial pero todavía no existe en tu worktree, tu primera acción mecánica debe ser guardarlo sin alterar su contenido en `docs/prompts/NOTIFICACIONES.md`; recién después podés implementar.

## 2. Lectura y comprobación obligatorias

Antes de modificar archivos:

1. Confirmá ruta, worktree, rama, `git status --short`, `git rev-parse HEAD` y `git worktree list`.
2. Leé completos y en este orden:
   - `AGENTS.md`;
   - `docs/PROMPT_MAESTRO_MAIN.md`;
   - este prompt;
   - todos los ADR vigentes, en especial `docs/adr/0009-motor-de-proximo-evento.md`;
   - los prompts `MOTOR_DE_PROXIMO_EVENTO`, `CALENDARIO_MENSUAL`, `OBJETIVOS_Y_GUARDIAS`, `NOVEDADES_FERIADOS_Y_NOTAS`, `VACACIONES` y `CONFIGURACION_Y_SEGURIDAD` si existe;
   - manifiestos, Gradle, modelos, repositorios, DAOs, migraciones, motor de próximo evento, navegación, configuración y pruebas relacionados.
3. Confirmá que la base coincide exactamente con el commit indicado. Si MAIN avanzó o existe cualquier contradicción contractual, no la resuelvas silenciosamente: informala antes de seguir.
4. Preservá cambios ajenos. No uses `git reset --hard`, no descartes archivos y no toques datos productivos.

## 3. Objetivo del módulo

Implementar notificaciones locales de guardias que:

- anticipen una guardia mediante uno o varios avisos configurables;
- se actualicen al comenzar para mostrar que está en curso y cuánto falta para terminar;
- desaparezcan al finalizar o cuando la guardia deje de ser válida;
- reaccionen a creación, edición, eliminación, vacaciones y cambios de configuración;
- usen como fuente de verdad los contratos vigentes del motor de próximo evento y de los repositorios locales;
- funcionen sin red, cuentas, nube, sincronización ni telemetría.

No implementes clima, widgets, informes ni copias de seguridad. La notificación puede dejar puntos de integración claros para módulos futuros, pero no inventar sus datos ni sus contratos.

## 4. Decisiones funcionales cerradas

### 4.1 Activación y recordatorios

- Las notificaciones permanecen desactivadas hasta que el usuario las habilite conscientemente.
- La configuración global inicial propone un aviso **12 horas antes**.
- Debe ofrecer accesos rápidos de **6, 8, 12 y 24 horas**, además de un valor personalizado positivo.
- Se admiten entre **cero y cinco avisos únicos por guardia**.
- Los avisos duplicados están prohibidos.
- Una guardia sin configuración propia usa los recordatorios globales.
- Una configuración propia vacía significa que los avisos de esa guardia están desactivados.
- Debe existir una acción explícita para volver a usar la configuración global, eliminando la excepción particular.
- Ningún aviso anterior al instante actual debe programarse tardíamente como si todavía fuera puntual.

### 4.2 Elegibilidad de una guardia

No dupliques ni flexibilices la regla de negocio vigente:

- sólo participan guardias `PLANNED` cuyo fin sea posterior al instante evaluado;
- los intervalos son `[inicio, fin)`;
- vacaciones inclusivas excluyen las guardias planificadas alcanzadas;
- `ABSENT` y `CANCELLED` no producen notificaciones de guardia;
- una edición o eliminación invalida inmediatamente los avisos anteriores;
- guardias simultáneas se conservan como eventos separados;
- el orden estable continúa siendo inicio, fin y UUID.

Si necesitás reutilizar una primitiva pura del motor de próximo evento, extraela sin cambiar su comportamiento y cubrí con pruebas de regresión tanto el motor como las notificaciones. No crees una segunda interpretación divergente.

### 4.3 Comportamiento visible

- Cada guardia usa una identidad de notificación estable basada en su UUID; no dependas únicamente de un `Int` susceptible a colisiones.
- Los recordatorios sucesivos de la misma guardia actualizan la misma notificación y pueden volver a alertar.
- Al comenzar, se actualiza a **Guardia en curso** y muestra una cuenta regresiva hasta el fin mediante el cronómetro del sistema; no programes alarmas por minuto.
- Al finalizar, la notificación se cancela.
- Varias guardias simultáneas se muestran por separado y agrupadas sin perder ninguna.
- Usá plantillas estándar de Android; no agregues `RemoteViews` personalizados.
- Preservá los datos históricos reales ya guardados en la guardia: objetivo, abreviatura, horario, puesto y color.
- La abreviatura y el horario completo nunca se reemplazan por etiquetas inventadas de día o noche.

### 4.4 Persistencia, privacidad y sonido

El usuario puede elegir:

- notificación descartable o persistente mientras la guardia está vigente;
- privacidad de pantalla bloqueada:
  - `COMPLETA`: datos habituales de la guardia;
  - `REDUCIDA`: estado y horario sin objetivo, puesto ni dirección;
  - `OCULTA`: contenido genérico;
- sonido predeterminado del sistema o un sonido elegido mediante el selector oficial de tonos/notificaciones de Android.

Reglas de seguridad:

- Nunca muestres notas, descripciones de ausencias o cancelaciones, información médica, fotos, nombres de terceros ni otros datos privados.
- No copies audio a almacenamiento propio y no pidas permisos generales de archivos.
- Como el sonido de un canal es inmutable después de crearlo, diseñá identificadores de canal deterministas y versionados para cambios de sonido, sin acumular canales innecesarios.
- El usuario de Android conserva el control final del canal, el sonido, la vibración y la visibilidad.

## 5. Acciones de la notificación

La notificación puede ofrecer como máximo tres acciones claras:

1. **Ver detalles**: abre exactamente la guardia o el día correspondiente.
2. **Cómo llegar**: sólo por toque explícito. Usa la dirección histórica si existe y delega a una aplicación externa compatible. Si falta dirección o no hay aplicación capaz de resolverla, abre el detalle de MiGuardia y explica honestamente la limitación. No pidas ubicación ni incorpores mapas.
3. **Informar novedad**: abre el flujo existente de ausencia o cancelación asociado a esa guardia; nunca muta datos desde la propia notificación.

Los `PendingIntent` deben ser explícitos, inmutables y únicos por componente, acción, UUID y frontera temporal. En Android 12 o superior, las acciones sensibles deben requerir autenticación del dispositivo cuando la API compatible lo permita. `MainActivity` debe procesar tanto el intento inicial como `onNewIntent` y traducirlos a la navegación manual existente, sin agregar una biblioteca de navegación.

## 6. Arquitectura de programación

### 6.1 Planificador puro

Creá un planificador de dominio puro y determinista. Sus entradas deben ser datos tipados —`Instant`, `LocalDate`, `Duration`, `UUID` y modelos de dominio—, no cadenas visuales. Su salida debe describir identidades y fronteras programables, no invocar Android.

Para cada guardia elegible puede producir:

- una frontera por cada recordatorio en `startAt - lead`;
- una frontera de comienzo;
- una frontera de fin.

Probá explícitamente límites exactos, guardias nocturnas, año bisiesto, cambio de mes/año, zona horaria local, vacaciones, simultaneidad y edición/eliminación.

### 6.2 Alarmas Android

- Usá alarmas únicas `RTC_WAKEUP`; no uses alarmas repetitivas.
- Si el acceso a alarmas exactas está concedido, usá `setExactAndAllowWhileIdle` para las fronteras necesarias.
- Si no está concedido, mantené la aplicación utilizable, mostrale al usuario que el horario puede demorarse y degradá honestamente a `setAndAllowWhileIdle`.
- No uses `WorkManager`, polling, bucles residentes ni servicios en primer plano para simular precisión.
- No solicites `USE_EXACT_ALARM`. El permiso especial permitido para esta función es `SCHEDULE_EXACT_ALARM`.
- Antes de programar exactas comprobá `canScheduleExactAlarms()`.
- El acceso especial sólo se solicita desde Configuración cuando el usuario habilita avisos precisos, después de explicar para qué sirve.

### 6.3 Reconciliación y receptores

Implementá un reconciliador idempotente que observe mientras la aplicación está viva:

- guardias relevantes;
- vacaciones;
- excepciones/configuración particular;
- preferencias globales y permisos efectivos.

Debe calcular el plan deseado, compararlo con el instalado, crear lo faltante y cancelar lo obsoleto. No dependas de información privada en extras de intents.

Autorizaciones de manifiesto limitadas a:

- `android.permission.POST_NOTIFICATIONS`;
- `android.permission.SCHEDULE_EXACT_ALARM`;
- `android.permission.RECEIVE_BOOT_COMPLETED`.

Podés agregar receptores mínimos para:

- entrega de las alarmas propias;
- `BOOT_COMPLETED`;
- `MY_PACKAGE_REPLACED`;
- `TIME_SET`;
- `TIMEZONE_CHANGED`;
- cambio del acceso a alarmas exactas.

Los componentes deben ser no exportados salvo que la documentación oficial demuestre que una acción del sistema exige otra configuración. Validá estrictamente cada acción recibida. No agregues `INTERNET`, ubicación, servicio en primer plano, pantalla completa, accesibilidad ni ningún permiso ajeno al alcance.

Cuando se dispare una alarma:

- reconsultá la guardia, su estado, vacaciones y preferencias actuales;
- descartá avisos obsoletos, cancelados, ausentes, alcanzados por vacaciones o ya finalizados;
- tratá los extras sólo como identificadores opacos, no como fuente de verdad;
- si necesitás trabajo asíncrono, usá `goAsync()` con finalización acotada y sin red.

Tras reinicio, actualización de paquete, cambio de hora/zona o recuperación de acceso exacto, reconstruí el plan desde almacenamiento local. Las alarmas no constituyen la fuente de verdad.

## 7. Datos y Room v5 autorizados

Esta dependencia está autorizada a evolucionar Room de **v4 a v5**, agregando exactamente dos entidades y conservando las once existentes:

1. `shift_notification_configs`
   - clave primaria y foránea `shiftId` hacia `shifts`;
   - eliminación en cascada;
   - la existencia de la fila representa una excepción explícita a los valores globales.
2. `shift_notification_reminders`
   - clave primaria compuesta `(shiftId, leadMinutes)`;
   - clave foránea en cascada hacia la configuración;
   - sólo minutos positivos y únicos;
   - máximo cinco validado antes de persistir.

La sustitución de recordatorios particulares debe ser atómica. Agregá `MIGRATION_4_5`, exportá el esquema v5 y probá la cadena completa de migraciones. No modifiques entidades ni columnas previas y no uses migración destructiva.

Los esquemas v1-v4 deben permanecer idénticos. Hashes esperados:

- v1: `06557907F47669DF0E2F950C00FC7FC89EA45511386A9990803F01B86471AC1B`
- v2: `8D835CDF9616924A704EF3FDF89CC2BF1268F4275F5E9A978C6F20A6D44D7453`
- v3: `15299988DA323E9C0C434CC3087308D92605DA12A7AAEAD132E52B2AF7E162F2`
- v4: `933572FA5CEC8A9B41BEA84B905BCB0A091CB7C8B69C425B4981F5668DB8FE22`

Las preferencias globales —habilitación, recordatorios predeterminados, persistencia, privacidad y sonido— deben almacenarse con DataStore Preferences. Está autorizada únicamente la dependencia oficial ya catalogada `implementation(libs.androidx.datastore.preferences)`. No agregues bibliotecas de terceros.

## 8. Interfaz

En Configuración agregá una tarjeta **Notificaciones** antes del zoom interno. La pantalla de notificaciones debe permitir:

- habilitar o deshabilitar avisos;
- ver y resolver por separado el permiso de notificaciones y el acceso a alarmas exactas;
- editar los recordatorios globales, incluidos valores personalizados;
- elegir descartable o persistente;
- elegir privacidad;
- elegir sonido mediante Android;
- abrir los ajustes del canal o de la aplicación cuando corresponda.

En el detalle de guardia agregá **Avisos**, con los recordatorios efectivos, la posibilidad de crear una excepción propia, desactivar esa guardia o volver a los valores globales.

Requisitos de UX:

- explicación previa y contextual antes de cualquier pedido de permiso;
- la denegación no bloquea MiGuardia y puede corregirse después desde Configuración;
- estados de carga, contenido, vacío y error persistente con reintento;
- desplazamiento correcto y acciones alcanzables al zoom interno 100 %, 150 % y 200 %;
- tema claro y oscuro, vertical y horizontal;
- semántica básica coherente para controles y contenido;
- no consultar ni modificar `font_scale`, densidad, zoom ni tamaño de visualización del sistema.

## 9. Aislamiento de QA

Las pruebas del sistema de notificaciones nunca deben poner en riesgo los datos ni los permisos del paquete principal.

Está autorizado agregar en `app` un build type `qa` que:

- herede de `debug`;
- use `applicationIdSuffix = ".qa"`;
- use `matchingFallbacks += "debug"` cuando corresponda;
- no altere los IDs, firma ni comportamiento de `debug` o `release`.

El paquete QA debe tener base, DataStore, archivos, permisos, canales y alarmas independientes. Nunca ejecutes contra `com.blackatsystems.miguardia` comandos que borren datos, desinstalen, concedan o revoquen permisos, eliminen canales o cancelen alarmas.

Al finalizar la prueba física, eliminá solamente los paquetes QA y QA test. Confirmá que la aplicación principal permanece instalada.

Un reinicio físico del Samsung requiere una autorización nueva y explícita de Joa inmediatamente antes de hacerlo. Sin esa autorización, probá directamente el receptor y declaralo como recorrido de reinicio pendiente. Nunca cambies hora, zona, `font_scale`, densidad, zoom ni tamaño de visualización del dispositivo.

## 10. Pruebas obligatorias

La base histórica verificada antes de esta dependencia fue:

- 109 JVM;
- 71 instrumentadas de app;
- 42 instrumentadas de Room;
- 222 pruebas totales, sin fallos.

Estos conteos son referencia, no evidencia de tu entrega. Ejecutá y reportá conteos nuevos exactos.

### 10.1 JVM y dominio

Cubrí al menos:

- recordatorios globales y particulares;
- cero, uno y cinco avisos;
- rechazo de seis, duplicados, cero y negativos;
- frontera exacta de recordatorio, comienzo y fin;
- guardia nocturna y cambio de fecha/año;
- vacaciones inclusivas;
- estados cancelado y ausente;
- guardias simultáneas y orden estable;
- edición, eliminación y reemplazo de configuración;
- alarmas pasadas omitidas;
- identidades estables y sin colisiones funcionales;
- regresiones del motor de próximo evento.

### 10.2 Room y DataStore aislados

Cubrí:

- migración 4→5 con datos existentes;
- cadena 1→2→3→4→5;
- trece entidades finales;
- hashes idénticos de v1-v4;
- cascadas de guardia a configuración y recordatorios;
- reemplazo atómico;
- reapertura y persistencia;
- DataStore con directorios temporales exclusivos por prueba.

Nunca uses la base, preferencias o directorios productivos.

### 10.3 Compose e instrumentación

Cubrí:

- configuración global y por guardia;
- permiso concedido y denegado;
- acceso exacto concedido y denegado, incluido fallback honesto;
- contenido completo, reducido y oculto;
- persistente y descartable;
- sonido predeterminado y selección externa simulada;
- acciones y navegación al destino exacto;
- actualización por inicio y cancelación por fin;
- múltiples guardias agrupadas;
- tema, orientación y zoom interno.

### 10.4 Recorrido físico QA mínimo

En el Samsung `SM-S938B` API 36, usando únicamente datos ficticios del paquete QA:

- habilitación y denegación/aceptación de permisos;
- guardia corta futura con aviso programado;
- actualización exacta al comenzar;
- cronómetro regresivo sin alarmas por minuto;
- cancelación al finalizar;
- edición y eliminación reactivas;
- avisos globales, personalizados y desactivados;
- persistente y descartable;
- privacidad en pantalla bloqueada sin exponer contenido real;
- sonido predeterminado y metadatos del sonido elegido;
- acciones Ver detalles, Cómo llegar e Informar novedad;
- guardias simultáneas;
- reprogramación por receptor; reinicio real sólo si Joa lo autoriza en ese momento.

No registres, leas, copies ni captures datos reales.

## 11. Batería de cierre

Ejecutá serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Room instrumentado, con bases UUID o assets de migración aislados:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
```

Para el Samsung:

1. verificá los nombres y rutas reales generados para los APK QA;
2. instalá por actualización el APK QA y su APK test;
3. despertá y desbloqueá el equipo;
4. verificá el paquete del runner antes de ejecutarlo; el esperado es `com.blackatsystems.miguardia.qa.test/androidx.test.runner.AndroidJUnitRunner`;
5. ejecutá el runner manualmente y de manera serializada;
6. retirale solamente QA y QA test;
7. confirmá que `com.blackatsystems.miguardia` continúa instalada.

Además ejecutá `git diff --check` y revisá secretos, datos reales, logs y artefactos antes del traspaso.

## 12. Documentación y fuentes Android

Creá `docs/adr/0010-notificaciones-y-alarmas-locales.md` con estado propuesto para revisión de MAIN. Documentá arquitectura, persistencia, permisos, degradación sin exactitud, privacidad, canales, QA y consecuencias.

Usá documentación oficial vigente como fuente primaria:

- permiso de notificaciones: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- alarmas exactas: https://developer.android.com/develop/background-work/services/alarms
- canales: https://developer.android.com/develop/ui/compose/notifications/channels
- diseño y acciones: https://developer.android.com/develop/ui/compose/notifications
- cronómetro compatible: https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder.html
- privacidad en pantalla bloqueada: https://developer.android.com/design/ui/mobile/guides/home-screen/notifications

Si la plataforma vigente contradice una técnica de este prompt, no improvises: documentá el conflicto, recomendá el cambio mínimo y pedí decisión a MAIN.

## 13. Fuera de alcance

No agregues:

- clima ni red;
- cuentas, nube, sincronización o telemetría;
- widgets;
- informes o copias de seguridad;
- ubicación o mapas embebidos;
- reconocimiento de imágenes u OCR;
- servicios en primer plano o pantalla completa;
- permisos generales de archivos;
- biometría o bloqueo integral de la aplicación;
- reglas de remuneración;
- datos reales, fotos privadas o certificados médicos;
- adaptación basada en configuraciones de visualización del sistema.

No modifiques reglas de calendario, resúmenes, horas, vacaciones, excepciones ni proyección del próximo evento salvo la extracción pura y probada estrictamente necesaria para compartir elegibilidad.

## 14. Entrega a MAIN

No confirmes ni publiques. Devolvé un informe copiable que incluya:

- ruta, rama, base y HEAD;
- `git status --short`;
- diff completo frente a la base, incluyendo archivos no rastreados;
- contratos implementados y cualquier desviación;
- archivos modificados y nuevos;
- versión Room, número de entidades, migraciones y hashes v1-v5;
- cambios exactos de Gradle, manifiesto, permisos, canales, receptores y build QA;
- resultados y conteos exactos de JVM, app instrumentada y Room;
- resultado de lint, debug, release, QA y `git diff --check`;
- dispositivo físico, recorrido efectivamente realizado y recorridos pendientes;
- confirmación de aislamiento, preservación del paquete principal y eliminación exclusiva de QA;
- revisión de secretos, datos reales, logs y artefactos;
- riesgos conocidos y decisiones que MAIN deba tomar.

Checklist final:

- [ ] Contrato del motor de próximo evento reutilizado sin divergencias.
- [ ] Planificador puro y reconciliador idempotente probados.
- [ ] Permisos pedidos sólo en contexto y degradación honesta verificada.
- [ ] Room v5 con 13 entidades y migraciones no destructivas.
- [ ] Esquemas v1-v4 idénticos a sus hashes esperados.
- [ ] Datos, preferencias, alarmas, canales y permisos QA aislados.
- [ ] Ninguna información privada aparece en notificaciones o logs.
- [ ] Batería global verde con `--max-workers=1`.
- [ ] Room conectado aislado verde.
- [ ] Runner manual QA físico verde.
- [ ] Paquete principal preservado y sólo QA retirado.
- [ ] `git diff --check` limpio.
- [ ] Sin commit, push ni merge.
# Enmienda posterior de MAIN (2026-08-16)

La configuración particular `Avisos` se conserva, pero se abre desde `Editar` la guardia y no como acción principal del detalle. La presentación visible es una notificación común: la alarma de Android es sólo programación interna de fronteras. Por defecto la notificación permanece fija hasta finalizar y usa el cronómetro nativo, sin polling ni alarmas por minuto.
