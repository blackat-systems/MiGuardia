# 22 — Integración y depuración de cambios de último momento V2

- Estado: **CERRADO POR MAIN — CHECKPOINT PENDIENTE / NO REEJECUTAR**
- Fecha: 2026-09-02
- Auditoría local de MAIN: 2026-09-03
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- HEAD/base funcional de entrada:
  `f8ddbe2754bad62df43d1cef3e1f0c6b3bcb2352`
- Upstream de referencia:
  `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`
- Nombre humano: **Integración y depuración de cambios de último momento**

Resultado MAIN del 2026-09-03: la QA autorizada en Samsung API 36 detectó y
permitió corregir la incompatibilidad de Fragment 1.2.5 con los launchers
modernos de permisos y un mensaje de ubicación que no finalizaba. La batería
posterior pasó 707/707 JVM y 351/351 tareas; Room pasó 123/123 y la matriz
dirigida posterior 30/30. El bloque está cerrado funcionalmente, sin commit ni
push. API 26/API 33 continúan como compatibilidad pendiente.

## QUÉ HACE

Revisa como un solo sistema las funciones que quedaron juntas en el checkout:
Ayuda y recorrido inicial, simplificación de formularios, escritura más cómoda
de horas y fechas, opciones avanzadas, ubicación meteorológica por lugar de
trabajo, clima por objetivo, Room V6 y compatibilidad de Copias.

No vuelve a construir esas funciones. Comprueba que estén realmente conectadas
con la aplicación completa, reproduce defectos, corrige únicamente problemas
demostrables y deja un candidato ejecutable o un bloqueo preciso para MAIN.

## POR QUÉ EXISTE

Estas mejoras aparecieron durante el cierre de Ayuda y cruzan varias fronteras
que antes estaban estabilizadas por separado: primera apertura, navegación,
permisos, clima, Widget, persistencia y restauración. Una batería aislada puede
quedar verde y aun así esconder un clima asociado al lugar equivocado, una
migración incompleta, una copia incompatible, una guía que tapa otro flujo o un
formulario simplificado que volvió inaccesible una función avanzada.

Esta dependencia existe para detectar esas contradicciones antes del
checkpoint de MAIN. Su cierre desbloquea la auditoría final de la aplicación y
la eventual emisión de un candidato local de MiGuardia 2.0; no autoriza
publicación.

## ROLE

Sos una dependencia especializada de MAIN 2.0 dedicada a **auditar, integrar y
depurar** el candidato que ya existe en el checkout compartido. No sos MAIN y
no podés redefinir el producto, ampliar el alcance ni dar por válida una
afirmación del handoff sin comprobarla.

Trabajá directamente en el proyecto y la rama existentes. No crees otra rama,
worktree, proyecto, tarea ni subagente. No descartes, reviertas ni reemplaces
en bloque los cambios actuales: son precisamente el objeto de esta auditoría.

Podés corregir código y pruebas cuando exista un defecto reproducible dentro
del alcance. No hagas refactors oportunistas ni agregues capacidades nuevas.
La documentación canónica, el staging, el checkpoint y la decisión final
pertenecen a MAIN.

## TASK

Auditar hunk por hunk el candidato completo surgido sobre el HEAD de entrada,
construir un mapa de impacto real y verificar que las siguientes piezas formen
una sola aplicación coherente:

1. Ayuda, recorrido inicial y estado versionado del onboarding;
2. primera configuración simplificada sin pérdida de funciones;
3. carga directa de varias jornadas y revisión detallada opcional;
4. repetición de jornadas con fechas simples y opciones avanzadas plegadas;
5. ingreso automático y validado de horas y fechas;
6. selector completo de color más diez colores frecuentes;
7. dirección opcional convertida conscientemente por Android a coordenadas;
8. ubicación aproximada y puntual de la ciudad actual cuando no hay dirección;
9. clima, caché y Widget separados por objetivo;
10. Room V6 y migración explícita `5→6`;
11. lectura y restauración compatible de copias lógicas Room V5;
12. navegación, recreación, concurrencia, privacidad y regresiones vecinas.

Primero inspeccioná. Después ejecutá pruebas dirigidas. Corregí los defectos
reproducibles de integración y agregá pruebas de regresión. Repetí finalmente
la batería global local. La instrumentación y el uso de dispositivos quedan
detrás de una autorización nueva y expresa de Joaquin dentro de esa tarea.

No declares el bloque integrado ni terminado: devolvé el candidato a MAIN sin
commit.

## PUERTA 0

Este stage constituye una excepción consciente a la regla habitual de checkout
limpio: **el estado sucio conocido es la entrada del trabajo**. Antes de editar,
verificá y registrá:

- raíz exacta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama exacta: `codex/miguardia-2.0`;
- HEAD exacto:
  `f8ddbe2754bad62df43d1cef3e1f0c6b3bcb2352`;
- upstream exacto:
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- divergencia esperada de commits: 0 detrás y 17 delante;
- `v1.0.0^{}`, `main` y `origin/main` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor efectivo `joaquin <blackat.systems@gmail.com>`;
- remoto privado
  `https://github.com/blackat-systems/MiGuardia.git`;
- cero archivos staged;
- worktrees históricos registrados y sin modificar;
- prompt marcado `HABILITADO` en `docs/prompts/README.md`;
- estado sucio exacto indicado en **FOTOGRAFÍA DE ENTRADA**.

No ejecutes `reset`, `clean`, `checkout`, `restore`, `stash`, rebase ni descarte.
Si cambia HEAD, aparece staging, falta un archivo esperado o existe un archivo
adicional que no pertenece a la documentación de MAIN ni al candidato
registrado, devolvé `MAIN BLOQUEADA` antes de escribir.

## FOTOGRAFÍA DE ENTRADA

Antes de agregar este contrato, el candidato funcional tenía:

- 85 archivos rastreados modificados;
- 20 archivos nuevos sin seguimiento;
- 105 rutas candidatas en total;
- 0 staged;
- `git diff --check` limpio;
- diff rastreado de 3.050 inserciones y 870 eliminaciones;
- patch-id rastreado estable:
  `b968ff57a91af7d1ecfb57c6578da622d078b82e`;
- huella del manifiesto de contenido de los 20 archivos nuevos:
  `e8dee49aeb62d17eb3ad3d094d47052e34e6c042`.

MAIN agrega después únicamente este prompt y las actualizaciones de coordinación
que lo habilitan. Esos documentos son de sólo lectura para vos. Capturá al
comenzar el listado completo mediante:

```powershell
git diff --name-status HEAD
git diff --numstat HEAD
git ls-files --others --exclude-standard
git status --short --branch
git diff --check
```

Clasificá cada ruta y conservá el inventario en tu handoff. No confíes sólo en
los conteos: si la identidad de una ruta no coincide con el candidato o con la
coordinación documental de MAIN, detenete.

## CONTEXT

La base integrada anterior ya tiene un núcleo laboral y una segunda capa local
cerrados: configuración, jornadas, recurrencias, horario real, extras,
disponibilidad, Calendario, Resumen, próximo evento, notificaciones, Widget,
Informes, Copias y Bloqueo de acceso.

El checkout actual combina dos entregas todavía no integradas:

### Ayuda y recorrido inicial

- DataStore local y versionado para la marca de recorrido;
- guía automática sólo con `V2Ready` y `WorkSetupSurface.NONE`;
- prioridad de recuperación, Bloqueo, selección de rubro y primera
  configuración;
- Ayuda permanente y repetición del recorrido;
- destinos pendientes que esperan la guía;
- protección ante corrupción, doble toque, resultados tardíos y recreación.

### Simplificación, ubicación y clima

- horas con `:` automático y fechas con `/` automático;
- primera configuración acortada sin retirar capacidades;
- carga múltiple directa, revisión detallada opcional y recurrencia simplificada;
- opciones avanzadas plegadas con ayuda contextual;
- diez colores frecuentes sin reemplazar el selector completo;
- coordenadas meteorológicas opcionales por objetivo;
- `Geocoder` de Android para una dirección escrita y confirmada;
- captura aproximada, puntual e iniciada por la persona para usar su ciudad
  actual cuando no existe dirección;
- caché y actualización de clima aislados por objetivo;
- Room V6, migración `5→6` y Copias V5 compatibles.

La validación local heredada de la entrega informó 686 pruebas JVM verdes y
AndroidTest compilado. Esa evidencia no alcanza para este stage: hubo
correcciones posteriores de MAIN y debés repetir la validación sobre el estado
que realmente entregues.

## INPUTS

Leé completamente y en este orden:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las cuatro fichas de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
9. este prompt;
10. ADR 0011, ADR 0035, ADR 0036, ADR 0037 y ADR 0038;
11. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico que no haya sido
    reemplazado por V2;
12. los prompts cerrados de primera apertura, recurrencias, clima, Widget,
    Copias y Bloqueo;
13. cada archivo modificado y nuevo del candidato, completo, junto con sus
    consumidores y pruebas.

No uses el chat ni los resúmenes previos como sustituto del código y las fuentes
durables.

## OUTPUT

Entregá directamente en el checkout compartido, sin commit:

- auditoría completa de todas las rutas candidatas;
- mapa de integración por contrato y consumidor;
- correcciones mínimas de defectos reproducibles;
- pruebas de regresión para cada defecto corregido;
- batería local completa repetida desde el estado final;
- instrumentación y QA física sólo si Joaquin las autoriza expresamente en esa
  tarea;
- handoff autocontenido a MAIN con hallazgos cerrados y pendientes reales.

Resultados válidos:

- `CANDIDATO LOCAL VERDE — QA FÍSICA PENDIENTE`, si todo lo local queda verde
  pero todavía no existe autorización de dispositivo;
- `CANDIDATO VERIFICADO PARA AUDITORÍA DE MAIN`, si también se completa la
  matriz física autorizada;
- `MAIN BLOQUEADA`, si queda un defecto, una contradicción o una decisión
  material que no puede resolverse dentro del alcance.

## SCOPE

Podés modificar únicamente lo necesario dentro de las rutas ya afectadas del
candidato y sus pruebas directas:

- `app/src/main/**` para integración, UI, clima, Widget, Copias y navegación;
- `app/src/test/**`;
- `app/src/androidTest/**`;
- `core/domain/src/main/**` sólo para contratos ya modificados por el candidato;
- `core/domain/src/test/**`;
- `core/database/src/main/**` sólo para Room V6, mapeo, repositorio y Copias;
- `core/database/src/androidTest/**`;
- `core/database/schemas/com.blackatsystems.miguardia.core.database.MiGuardiaV2Database/6.json`.

Podés agregar un helper o una prueba nueva únicamente si cierra un hueco
concreto. Si necesitás tocar producción fuera de estas fronteras, agregar una
dependencia, cambiar una regla pública o crear otra migración, devolvé
`MAIN BLOQUEADA` antes de hacerlo.

Son propiedad exclusiva de MAIN y no se modifican:

- `AGENTS.md`;
- `docs/STATUS.md`;
- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- este prompt;
- ADR y auditorías.

## DEPENDENCIES

Podés asumir como cerrados los contratos funcionales anteriores al HEAD de
entrada, pero no suponer que sus consumidores siguen intactos después del diff.
Revalidá especialmente:

- `WorkSetup` y primera apertura;
- una sola grilla mensual;
- carga manual, edición y recurrencias;
- horario real, extras y disponibilidad;
- Horas y Resumen;
- próximo evento, notificaciones y Widget;
- Informes;
- Copias y restauración;
- Bloqueo de acceso;
- tema Vigilia y zoom interno.

## CONTRATOS OBLIGATORIOS

### 1. Primera apertura, Ayuda y navegación

- El selector muestra exactamente Vigilancia privada, Policía, Enfermería y
  Medicina. No existen `Salud` ni `Otro`.
- Recuperación de Copias y Bloqueo conservan prioridad.
- La primera configuración real ocurre antes de la guía.
- La guía nunca tapa `COMPLETION`, otro lugar, otro horario, un error o un
  destino prioritario.
- Completar u omitir escribe una sola vez y de forma monotónica.
- Corrupción de su DataStore se recupera sin bloquear permanentemente la app.
- Un callback tardío no completa ni navega una sesión que ya dejó de aplicar.
- El recorrido no muta datos ni solicita permisos.
- Al finalizar queda Calendario en consulta; repetir vuelve a Ayuda.
- La capa del recorrido oculta semántica y acciones subyacentes.

### 2. Simplificación sin pérdida

- La configuración inicial conserva lugar, tipo, horario y color aunque el
  camino principal sea más corto.
- La carga de varios días guarda directamente sólo después de una confirmación
  suficiente; la revisión detallada opcional sigue accesible.
- Repetir jornadas conserva los cuatro patrones, vista previa, límite de 2.000,
  conflictos y cambios desde una fecha.
- Ningún dato obligatorio puede quedar escondido en un bloque cerrado sin una
  explicación visible que habilite guardar.
- Cada `(?)` explica `Qué hace`, `Cómo usarlo` y `Ejemplo` y es usable con
  scroll, teclado, zoom y orientación.
- Plegar una opción nunca borra su borrador.

### 3. Horas, fechas y colores

- `0830` produce `08:30`; `8:30` produce `08:30`.
- Entradas incompletas pueden editarse sin saltos de cursor, pero `24:00`,
  `12:60` y valores imposibles muestran error y no se guardan.
- Las fechas usan `DD/MM/AAAA`, agregan `/` sin impedir borrar o corregir y
  validan calendario real, bisiesto y rango.
- Existen exactamente diez colores frecuentes y también el selector completo
  RGB/HEX; ninguno reemplaza el otro.
- El vocabulario visible dice `horas normales` o `horas trabajadas sin extras`
  cuando corresponde, sin cambiar las fórmulas.

### 4. Ubicación consciente por objetivo

- La dirección sigue siendo opcional.
- Si existe, `Usar esta dirección para el clima` llama al `Geocoder` sólo tras
  el toque, muestra el resultado y exige confirmación antes de guardar.
- Si no existe, `Usar mi ciudad actual para el clima` solicita en contexto
  únicamente `ACCESS_COARSE_LOCATION` y realiza una captura puntual.
- Esa captura guarda las coordenadas como referencia meteorológica del objetivo;
  después el clima funciona sin seguir la ubicación del teléfono.
- No existe `ACCESS_FINE_LOCATION`, ubicación en segundo plano, servicio,
  seguimiento, historial, mapa embebido, Places ni Google Maps.
- Rechazo, denegación permanente, ubicación apagada, timeout, proveedor ausente,
  dirección ambigua o sin resultado no bloquean guardar el objetivo.
- Volver desde Ajustes permite reintentar conscientemente.
- Doble toque, cambio de pantalla, recreación y resultado tardío no guardan una
  coordenada en el objetivo equivocado.
- Quitar ubicación exige confirmación e invalida sólo el clima de ese objetivo.

### 5. Clima y Widget por objetivo

- Sin coordenadas no se descarga ni muestra un clima falso de Córdoba.
- La identidad del caché incluye como mínimo objetivo, nombre, coordenadas y
  zona; un resultado de A nunca aparece en B.
- Cambiar, quitar o restaurar coordenadas invalida el caché y la UI observada
  sin dejar un spinner permanente.
- Widget, detalle y tarjeta reutilizan el objetivo correcto y se actualizan
  después de la invalidación.
- Varias solicitudes simultáneas no pierden objetivos ni aplican respuestas
  viejas; un fallo individual no cancela los demás.
- Clima sigue siendo opcional, degradable y no bloquea Calendario, avisos,
  Widget ni navegación.
- Open-Meteo recibe coordenadas y parámetros meteorológicos, no la dirección,
  el nombre, las jornadas ni datos privados.

### 6. Room V6

- `MiGuardiaV2Database` queda en versión 6 y mantiene 27 entidades.
- `MIGRATION_5_6` agrega únicamente `weatherLatitude` y `weatherLongitude` como
  `REAL` nulas en `objectives`.
- Base V5 poblada migra, conserva todas las filas, pasa integridad y claves
  foráneas, y reabre.
- Base V6 nueva crea el esquema correcto.
- Ambas coordenadas son válidas juntas o nulas juntas; números no finitos y
  rangos geográficos inválidos se rechazan.
- No existe fallback destructivo ni consultas Room en el hilo principal.
- Esquemas 1–5 permanecen byte a byte intactos.
- Esquema 6 esperado al preparar este prompt:
  - 27 entidades;
  - `identityHash = 7eb39f6fab5a44e69350e206716554be`;
  - SHA-256
    `BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268`.

### 7. Copias y restauración

- El formato de contenedor, MIME, cifrado y las 17 preferencias portables no
  cambian silenciosamente.
- Una copia lógica V5 válida se lee, valida y actualiza a V6 agregando
  coordenadas nulas; no se deriva el contrato V5 desde el esquema mutable V6.
- Una copia V6 conserva las coordenadas exactas y valida la pareja.
- Previsualizar no escribe.
- Combinar y reemplazar conservan sus confirmaciones, atomicidad, rollback,
  journal, fotografías y preferencias.
- Restaurar V5 o V6 reconcilia clima y Widget. Un fallo al limpiar caché o
  reanudar runtimes no puede dejar una mezcla de base, preferencias y fotos sin
  recuperación demostrable.
- Ayuda y Bloqueo continúan fuera de las preferencias portables.

### 8. Regresiones transversales

- Informes PDF/XLSX continúan reconciliando las mismas cifras de Horas y
  Resumen.
- Próximo evento y notificaciones no dependen del clima para existir.
- Bloqueo impide componer contenido laboral detrás de la puerta.
- Consultar Calendario, Resumen, tarjeta, Clima o Ayuda no escribe datos
  laborales.
- Errores conservan contenido válido, borradores y una acción concreta de
  reintento.
- No hay fugas de nombres, direcciones, horarios, coordenadas, notas, fotos o
  datos médicos en logs, semántica bloqueada, Widget oculto o avisos privados.

## DO NOT

- no implementar funciones nuevas;
- no cambiar los cuatro sectores ni agregar Psicología o agenda de pacientes;
- no agregar salarios, montos, liquidaciones o información sindical;
- no crear una segunda configuración laboral, otro Calendario ni otro motor de
  horas, Resumen, eventos o clima;
- no usar Córdoba como respaldo silencioso;
- no pedir ubicación precisa o en segundo plano;
- no capturar ubicación automáticamente al abrir, crear o editar un objetivo;
- no agregar Maps, Places, SDK de ubicación, librería HTTP ni otra dependencia;
- no cambiar formato de Copias, preferencias portables o cifrado salvo bloqueo
  documentado a MAIN;
- no crear Room V7 ni tocar esquemas 1–5;
- no elevar SDK, cambiar `applicationId`, versión o paquete;
- no agregar cuentas, nube, sincronización, analítica o telemetría;
- no consultar ni modificar `font_scale`, densidad o tamaño visual del sistema;
- no usar datos reales;
- no tocar producción;
- no usar Samsung, ADB ni emuladores sin autorización nueva y expresa;
- no disparar una alarma exacta real ni reiniciar el Samsung sin autorizaciones
  específicas separadas;
- no modificar documentación canónica;
- no crear commit, push, tag, Release, merge, rebase, reset, rama, worktree,
  proyecto, tarea ni subagente;
- no descartar ningún cambio del checkout.

## STOP POINTS

Detenete y devolvé `MAIN BLOQUEADA` con evidencia si:

- Puerta 0 no coincide o aparece trabajo sin dueño;
- una fuente vigente contradice una decisión funcional material;
- corregir exige ampliar comportamiento público, permisos, persistencia,
  privacidad, dependencias o arquitectura fuera de este contrato;
- Room V6 no puede preservar una base V5;
- una copia V5 o V6 no puede recuperarse sin pérdida o mezcla parcial;
- una prueba roja reproduce corrupción, pérdida de datos o filtración privada;
- la batería final continúa roja después de una corrección razonable;
- hace falta un dispositivo y no existe autorización expresa;
- la siguiente acción sería destructiva, externa, commit, push, tag, Release,
  `main` o producción.

La falta de autorización de dispositivo no bloquea las correcciones locales:
completá primero todo lo seguro y devolvé `QA FÍSICA PENDIENTE` al llegar a esa
puerta.

## VALIDATION

### 1. Inventario y auditoría

- revisar cada hunk rastreado y cada archivo nuevo completo;
- clasificar cada ruta por dueño y consumidor;
- comprobar que no se retiró una capacidad existente por simplificación;
- ejecutar `git diff --check`;
- revisar whitespace y salto final de archivos nuevos;
- buscar secretos, credenciales, logs privados, binarios y datos reales;
- buscar `ACCESS_FINE_LOCATION`, ubicación de fondo, tracking, polling,
  `fallbackToDestructiveMigration` y `allowMainThreadQueries`;
- verificar manifiesto, permisos y dependencias contra el contrato.

### 2. Pruebas dirigidas mínimas

Ejecutá primero las suites nuevas y sus vecinas, incluyendo:

- `AutomaticTimeFieldTest` y Compose correspondiente;
- validación de fechas de recurrencia y sus coordinadores;
- `WorkSetupCoordinatorTest` y `WorkSetupComposeTest`;
- `HelpCoordinatorTest`, `OnboardingPreferencesStoreTest`, pruebas Compose,
  Activity y DataStore instrumentado;
- pruebas de Geocoder, permiso, captura, doble toque, cancelación y recreación;
- `WeatherInfrastructureTest`, pruebas de ViewModel, runtime, caché y Widget;
- prueba A→B que demuestre que un cambio de objetivo no conserva estado viejo;
- migración Room `5→6`, base nueva V6, reapertura, rollback e integridad;
- contrato completo de Copias V5 y V6, incluida restauración real a Room V6;
- recuperación ante fallo durante invalidación de caché/reanudación de runtimes;
- regresiones de carga manual, recurrencias, horario real, Horas, Resumen,
  Informes, próximo evento, notificaciones, Widget, Copias y Bloqueo.

No aceptes una prueba por su nombre: revisá sus assertions y asegurá que use
reloj, zona, UUID, ubicación y repositorios deterministas cuando corresponda.

### 3. Batería global local

Después de la última corrección, ejecutá serialmente y forzando tareas:

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

Obtené conteos desde los XML actuales. Separá:

- `JVM VERIFICADO`;
- `LINT`;
- `COMPILADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA`;
- `PENDIENTE`.

No uses XML históricos ni llames ejecución física a la compilación del APK de
pruebas.

### 4. Room y hashes

Confirmá los hashes intactos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
6.json  BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268
```

### 5. Matriz Android autorizada

No uses ningún dispositivo hasta recibir autorización nueva y explícita de
Joaquin en la tarea. Si la recibe, usá solamente paquetes QA/test, datos
ficticios y un dispositivo por vez.

En Samsung `SM-S938B` API 36 verificá como mínimo:

- instalación limpia: cuatro rubros, configuración simplificada, pantalla de
  finalización, Ayuda y Calendario;
- omitir, completar, reabrir y repetir la guía;
- corrupción/reintento, recreación y destino pendiente;
- carga de una y varias jornadas, revisión opcional y recurrencia;
- horas y fechas con separadores automáticos, errores y cursor;
- opciones avanzadas y `(?)` con teclado y scroll;
- diez colores frecuentes y selector completo;
- dirección válida, ambigua y sin resultado con confirmación consciente;
- sin dirección: `Usar mi ciudad actual para el clima`;
- permiso aproximado concedido, rechazado y retorno desde Ajustes;
- ubicación apagada o sin resultado sin bloquear el guardado;
- dos objetivos ficticios con ubicaciones distintas y clima separado;
- cambio y eliminación de ubicación sin pronóstico viejo ni spinner eterno;
- Widget actualizado y sin mezclar objetivos;
- migración Room V5→V6 y reapertura;
- lectura/restauración de copia lógica V5 en V6 con coordenadas nulas;
- copia V6 con coordenadas;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200.

API 26 debe cubrir migración y comportamiento compatible de Geocoder/ubicación;
API 33 debe cubrir el permiso moderno. Si no están autorizadas o disponibles,
marcalas `PENDIENTE`; no inventes evidencia.

No consultes ni modifiques escala tipográfica, densidad o tamaño visual del
sistema. No abras, instales, limpies, reemplaces ni desinstales producción.
Informá exactamente qué paquetes quedan en cada dispositivo.

## HANDOFF A MAIN

Entregá un informe autocontenido con estas secciones exactas:

```text
# HANDOFF A MAIN — Integración y depuración de cambios de último momento V2

## QUÉ HACE
## POR QUÉ EXISTE
## VEREDICTO
## OBJECTIVE
## INPUT CANDIDATE
## AUDIT MAP
## FINDINGS
## FIXES
## FILES
## VALIDATION
## ROOM AND BACKUPS
## LOCATION AND WEATHER
## PHYSICAL QA
## DEVICE SAFETY
## PRIVACY AND SECURITY
## RISKS
## PENDING
## GIT
## NEXT
```

En `FINDINGS`, clasificá cada defecto como P0, P1, P2 o P3, con superficie,
reproducción mínima, impacto, corrección y prueba que lo cierra. No escondas
una prueba roja dentro del historial de intentos.

En `FILES`, separá archivos heredados sin tocar, archivos corregidos por vos,
nuevos y eliminados. En `VALIDATION`, distinguí cada nivel real. En `GIT`,
informá ruta, rama, HEAD, upstream, divergencia, staged, modificados, nuevos y
ausencia de commit/push.

El resultado queda directamente en el checkout compartido. No existe nada para
`cherry-pick`.

## DONE WHEN

La dependencia está lista para volver a MAIN cuando:

- Puerta 0 identificó exactamente todo el candidato y ningún cambio quedó sin
  dueño;
- se leyó cada hunk y cada archivo nuevo;
- Ayuda, simplificación, ubicación, clima, Room V6 y Copias funcionan juntos;
- ninguna capacidad anterior quedó inaccesible o cambió de fórmula;
- las decisiones de ubicación consciente y ciudad actual están implementadas
  exactamente, sin seguimiento ni permiso preciso;
- la migración 5→6 preserva datos y los esquemas 1–5 están intactos;
- Copias V5 y V6 se validan, restauran y recuperan sin mezcla parcial;
- cada defecto corregido posee una prueba de regresión;
- no quedan findings P0, P1 o P2 abiertos;
- la batería global local final está verde y sus conteos provienen de XML
  actuales;
- `git diff --check` está limpio;
- no hay secretos, datos reales, logs privados ni cambios fuera de alcance;
- la QA física está ejecutada con autorización o marcada honestamente como
  puerta pendiente;
- no se creó commit, push, tag, Release, rama, worktree, tarea ni subagente;
- el handoff devuelve el candidato exclusivamente a MAIN.

La dependencia no puede declarar por sí sola `CANDIDATO LOCAL COMPLETO` ni
`MiGuardia 2.0 terminada`. Esos veredictos pertenecen a la auditoría final de
MAIN después de integrar y documentar este resultado.
