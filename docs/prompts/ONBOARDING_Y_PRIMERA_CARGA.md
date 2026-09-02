# MiGuardia — bienvenida, onboarding, primera carga y Ayuda

> **BORRADOR HISTÓRICO V1 — NO EJECUTAR; REEMPLAZADO PARA 2.0.** Fue diferido,
> no contempla la elección de los cuatro sectores y quedó reemplazado por
> `docs/prompts/AYUDA_Y_RECORRIDO_INICIAL_V2.md`. Ver
> `docs/prompts/README.md`.

> Estado histórico: no implementado en 1.0; reemplazado para 2.0 por
> `docs/prompts/AYUDA_Y_RECORRIDO_INICIAL_V2.md`.
>
> Fecha: 2026-08-18
>
> Base requerida: `main` limpia posterior a orientación de Fotos, menú lateral y selección directa del Calendario, en el SHA exacto que entregue MAIN
>
> Rama sugerida: `codex/onboarding-guided-tour`

## 1. Rol y autoridad

Implementá únicamente la experiencia inicial de la versión actual de MiGuardia para vigiladores: bienvenida, introducción de tres pasos, recorrido contextual sobre la interfaz real, Ayuda y primera carga guiada mediante flujos reales. La futura ampliación a médicos, enfermeros y policías está documentada, pero no se implementa ni se simula aquí.

Antes de actuar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/prompts/COORDINACION_EXPERIENCIA_INICIAL_Y_PERFIL_MAIN.md`;
4. `docs/adr/0014-onboarding-local-versionado-y-primera-carga-guiada.md`;
5. este documento;
6. ADR 0015, contratos de selección directa y menú lateral, y código/pruebas vigentes de Perfil, Calendario, Objetivos/horarios, navegación, tema/zoom y DataStore.

La instrucción explícita actual de Joa y el prompt maestro prevalecen. No reabras decisiones cerradas ni amplíes el alcance para completar módulos futuros.

## 2. Base y precondiciones

Antes de editar, informá:

- ruta de trabajo;
- rama y `HEAD`;
- relación con la base requerida;
- `git status --short --branch`;
- archivos no rastreados;
- worktrees registrados.

La base debe estar limpia. No uses un worktree histórico ni copies implementaciones anteriores. No hagas commit, push, merge, rebase, cambio de Room o limpieza de archivos salvo autorización explícita de MAIN.

## 3. Objetivo del incremento

Al finalizar debe existir:

- una bienvenida inicial breve y sin demora artificial;
- tres pasos claros sobre organización, horas/próximos eventos y privacidad local;
- un recorrido contextual que muestre dónde están y cómo se usan los controles principales reales;
- opciones Atrás, Siguiente, Finalizar y Omitir guía con comportamiento inequívoco;
- finalización local versionada y resistente a recreación;
- una entrada `Ayuda` desde Configuración;
- temas de ayuda acotados y acción `Repetir guía inicial`;
- preparación de datos guiada cuando todavía no existe ninguna guardia;
- reutilización estricta de Perfil, Objetivos/horarios y carga de guardias existentes;
- regreso final al Calendario sin filas parciales ni datos ficticios.

## 4. Decisiones funcionales congeladas

### 4.1 Primera apertura

- No agregar splash con temporizador, animación obligatoria ni espera artificial.
- Mientras se lee el estado local, mostrar una superficie Vigilia estable; no dejar ver fugazmente el Calendario antes de saber si corresponde onboarding.
- Si `completed_version < 1`, abrir la introducción antes de la navegación principal.
- Si la versión ya está completa, abrir normalmente el Calendario.
- Una actualización con datos existentes puede mostrar esta introducción una vez. Nunca altera esos datos.

### 4.2 Introducción de tres pasos

La introducción comunica, en este orden:

1. **Organizá tus guardias**: Calendario, `Editar calendario`, carga individual o múltiple, objetivos, horarios y fotos del cronograma.
2. **Entendé tu mes**: Resumen de horas, próxima guardia, notificaciones y clima opcional.
3. **Tus datos quedan en tu teléfono**: sin cuenta, nube, sincronización, analítica ni ubicación automática; Configuración y Ayuda concentran los controles.

Los textos son breves, verificables y propios de las funciones reales. No mencionar widgets, informes, copias, bloqueo, Premium ni funciones todavía no implementadas como si existieran.

- `Omitir guía` está disponible durante la introducción inicial y marca la versión actual como completada.
- La acción principal del último paso abre el recorrido contextual; todavía no declara terminada la guía.
- Atrás cambia al paso anterior; en el primero, solicita confirmación antes de salir si eso equivaldría a omitir.
- Guardar una vez produce una sola escritura. Durante la escritura se evita doble envío.
- Tras completar el recorrido contextual u omitir la guía, la aplicación abre Calendario.

### 4.3 Recorrido contextual sobre la interfaz real

- Comienza sobre el Calendario ya estabilizado, sin crear guardias, objetivos, horarios, preferencias ni permisos.
- Usa focos visuales y texto breve anclados a controles reales. Cada paso ofrece `Atrás`, `Siguiente` y `Omitir guía`; no depende de que el usuario acierte un gesto oculto.
- Recorre, en este orden razonable:
  1. botón de tres líneas y destinos Calendario, Resumen y Configuración;
  2. próxima guardia, mes, anterior/siguiente, Hoy y gesto horizontal;
  3. toque de un día para detalles y `Editar día`;
  4. botón grande `Editar calendario`, selección directa de uno o varios días, `Terminar de elegir días` y elección posterior entre Guardia o Francos;
  5. Fotos del cronograma;
  6. Resumen;
  7. Configuración y sus entradas principales, incluida Ayuda.
- Puede abrir y cerrar el panel lateral y cambiar temporalmente de destino para señalar una superficie, pero al terminar vuelve al Calendario en consulta.
- No dispara callbacks de mutación, no abre selectores del sistema, no solicita permisos y no necesita datos ficticios.
- Si un control no está disponible por estado vacío, explica dónde aparecerá sin simular que la función ya produjo datos.
- `Finalizar` desde el último foco persiste la versión actual una sola vez y abre Calendario.
- Cerrar o Atrás desde el primer foco solicita confirmación si equivale a omitir.
- No incluir widgets, informes, copias, bloqueo, Premium ni módulos no implementados.

### 4.4 Repetición desde Ayuda

- Configuración muestra una única entrada `Ayuda`.
- Ayuda es una superficie real, no un placeholder.
- Incluye temas breves: Calendario y edición; objetivos/horarios y primera guardia; Resumen; Perfil y Configuración; notificaciones y clima; privacidad local.
- `Repetir guía inicial` abre los tres pasos y el recorrido contextual en modo repetición.
- En repetición no se muestra `Omitir guía`; cerrar o finalizar regresa a Ayuda.
- Repetir no borra ni reduce `completed_version` y no vuelve a activar el bloqueo de primera apertura.
- `Reportar un problema`, soporte remoto y adjuntos quedan fuera de este incremento.

### 4.5 Primera carga guiada

- Sólo se presenta como recorrido especial cuando `ShiftRepository.observeHasAny()` es falso.
- La acción `Cargar datos` abre la preparación inline real en lugar de lanzar un formulario de guardia imposible de completar sin horarios.
- La guía deriva el avance de datos reales y muestra, como máximo, tres bloques:
  1. revisar Perfil laboral mediante la superficie existente;
  2. crear uno o varios objetivos y horarios activos mediante los formularios reales;
  3. tocar `Continuar y elegir días` cuando exista al menos una combinación activa, seleccionar una o varias fechas en la grilla principal, confirmar `Terminar de elegir días`, elegir Guardia o Francos y usar el formulario vigente sin un segundo calendario.
- Perfil es revisable, no obligatorio: nombre sigue opcional y `Inforce` continúa siendo la empresa inicial válida.
- Si ya existe un horario activo, `Continuar y elegir días` está habilitado, pero no avanza automáticamente: permite crear más objetivos u horarios antes de seguir.
- La selección comienza vacía. La primera carga no usa `firstShiftDate` ni elige una fecha por el usuario.
- Guardia y Francos no aparecen durante la selección. `Terminar de elegir días` confirma el conjunto sin guardar, mientras `Modificar días elegidos` vuelve a la grilla conservando las fechas y `Salir de edición` abandona el modo.
- Al guardar la primera guardia, la guía observa la fuente real, muestra confirmación y permite ir al Calendario.
- Salir por ahora no crea ni elimina datos. Los objetivos u horarios ya confirmados permanecen porque son datos válidos, no residuos del tutorial.
- Atrás desde Perfil o gestión vuelve a la guía con su estado derivado actualizado.
- No crear formularios paralelos, flags por cada paso, filas temporales ni guardias incompletas.

### 4.6 Permisos y privacidad

- Onboarding no solicita permisos preventivamente.
- Notificaciones, alarmas exactas, fotos e Internet conservan sus solicitudes y condiciones en los flujos donde realmente se necesitan.
- No solicitar ni guardar DNI, legajo, email, teléfono, domicilio, fecha de nacimiento, foto personal ni credenciales.
- No registrar en logs el contenido del perfil, objetivos, horarios, guardias o preferencias.

## 5. Arquitectura y persistencia

Aplicá `docs/adr/0014-onboarding-local-versionado-y-primera-carga-guiada.md`:

- DataStore Preferences exclusivo `onboarding.preferences_pb`;
- clave `completed_version` y versión actual `1`;
- flujo observable y escritura atómica;
- manejo de `IOException` coherente con los stores existentes;
- constructor interno con `File` y `CoroutineScope` para pruebas aisladas;
- instancia única en `MiGuardiaApplication`;
- ViewModel propio para introducción/Ayuda/primera carga;
- Compose no escribe directamente en DataStore ni Room.

Para el avance real reutilizá:

- `GuardProfileStore` y la superficie Perfil;
- `ObjectiveRepository.observeActive()`;
- `ScheduleCombinationRepository.observeByObjective()` filtrando `isActive`;
- `ShiftRepository.observeHasAny()`;
- `ManagementViewModel` y sus formularios existentes;
- `CalendarInteractionMode` y la selección directa de fechas.

Room debe permanecer exactamente en v5. No cambies entidades, esquemas, DAO, migraciones ni `LocalDataStore`. Tampoco cambies manifiesto, permisos, Gradle, dependencias, red, canales ni servicios.

## 6. Navegación y estado

- La introducción inicial es bloqueante hasta completar, omitir o resolver un error explícito.
- Ayuda y la guía de primera carga son superficies bloqueantes coherentes con Perfil y gestión.
- El recorrido contextual puede señalar la raíz y cambiar de destino, pero su capa intercepta interacciones no autorizadas y nunca escribe datos.
- Los formularios reales pueden aparecer por encima de la guía y, al cerrarse, deben devolver al punto que los abrió.
- El menú lateral no aparece por encima de las tres pantallas introductorias; durante el recorrido sólo se abre cuando el paso lo requiere.
- Recreación, rotación o recomposición conservan el paso visible y evitan escrituras duplicadas.
- Un borrador real mantiene la protección de descarte de su ViewModel dueño.
- No cambiar el modo inicial de Calendario: continúa abriendo en consulta.
- Cuando la primera guardia queda confirmada, no dejar el Calendario atrapado en edición salvo que la acción final lo indique claramente.

## 7. Presentación Vigilia

- Usar tokens, componentes y superficies existentes.
- Una acción primaria por pantalla.
- Indicador de progreso textual y visual comprensible, sin depender sólo del color.
- Ilustración opcional sólo si se construye con recursos propios y no retrasa ni bloquea la entrega.
- Tema `Seguir el sistema`, claro y oscuro.
- Zoom interno 100 %, 150 % y 200 %.
- Retrato principal y paisaje desplazable, sin controles inaccesibles.
- No consultar ni modificar `font_scale`, densidad, zoom o tamaño de visualización de Android.
- Mantener semántica accesible básica. No activar ni declarar una auditoría específica de TalkBack.

## 8. Errores y recuperación

- Lectura del estado inicial fallida: mensaje genérico, `Reintentar` y sin mostrar contenido principal incorrecto.
- Escritura de finalización fallida: permanecer en onboarding y permitir reintentar.
- Repositorios de primera carga fallidos: conservar acciones independientes seguras y ofrecer reintento.
- Si una superficie real falla, usar su manejo vigente; la guía no oculta ni sustituye ese error.
- Propagar `CancellationException`; no convertir cancelación en fallo visible.
- No imprimir rutas, datos o causas sensibles.

## 9. Mapa de impacto permitido

El cambio puede alcanzar:

- nuevo store/modelo de estado inicial;
- `MiGuardiaApplication`;
- ViewModel, estado, acciones y pantallas de onboarding/Ayuda/primera carga;
- wiring de `MainActivity`;
- raíz `MiGuardiaApp`, Configuración y CTA de primera guardia;
- strings y recursos visuales propios mínimos;
- pruebas JVM, Compose e instrumentadas;
- documentación de estado.

No debe alcanzar Room, esquemas, migraciones, manifiesto, permisos, Gradle, dependencias, red, notificaciones, clima, remuneración, widgets, informes, copias, bloqueo, cuentas, nube ni datos productivos.

## 10. Pruebas mínimas

### 10.1 JVM

- versión ausente o menor requiere introducción;
- versión actual la omite;
- completar y omitir producen la misma marca durable;
- repetición no modifica la marca;
- estado derivado distingue sin horarios, con horario activo y con primera guardia;
- objetivos/horarios ocultos no completan el paso;
- doble acción no produce dos escrituras;
- cancelación no se transforma en error.

### 10.2 Instrumentadas aisladas

- DataStore inicia pendiente;
- completar persiste versión 1;
- reapertura conserva la versión;
- una versión futura no se degrada;
- archivo temporal y scope propio; no toca datos productivos.

### 10.3 Compose

- primera apertura no deja ver fugazmente la app principal;
- tres pasos, Atrás, Siguiente, Finalizar y Omitir;
- confirmación de salida en el primer paso;
- focos contextuales recorren menú, calendario, detalle, edición directa, Fotos, Resumen y Configuración sin mutar;
- la guía omite módulos futuros y vuelve al Calendario en consulta;
- error de lectura/escritura y reintento;
- Ayuda aparece una sola vez en Configuración;
- repetición completa vuelve a Ayuda y no muestra Omitir guía;
- CTA `Cargar datos` abre la preparación sin preseleccionar fecha ni crear `ShiftDraft`;
- guía abre Perfil y gestión reales, sin formularios duplicados;
- horario activo habilita la carga real;
- selección vacía, elección múltiple, `Terminar de elegir días` y elección posterior Guardia/Francos respetan la secuencia;
- `Modificar días elegidos` conserva las fechas elegidas, se muestra como un control secundario delimitado y `Salir de edición` se mantiene como acción distinta;
- primera guardia confirmada conduce al Calendario;
- claro/oscuro, 100/150/200 %, retrato y paisaje mantienen acciones alcanzables.

### 10.4 Regresión por impacto

- usuario con onboarding completado abre Calendario en consulta;
- usuario con datos existentes no recibe guía de primera carga;
- menú lateral, sus tres destinos y gesto horizontal del mes;
- Perfil conserva datos y protección de borrador;
- Objetivos/horarios y carga simple/múltiple conservan validaciones;
- `Cargar datos` no elige ninguna fecha y permite varios objetivos/horarios antes de continuar;
- Guardia y Francos no aparecen antes de `Terminar de elegir días`, y volver a seleccionar no vacía el conjunto;
- ninguna preferencia, entidad o instantánea histórica cambia al repetir o abandonar.

Ejecutá con `--max-workers=1` pruebas afectadas, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease` y APK de instrumentación/QA. MAIN decide la selección física final por mapa de impacto.

El QA físico usa únicamente `com.blackatsystems.miguardia.qa` y su paquete de pruebas. No instalar, actualizar, desinstalar ni borrar datos de `com.blackatsystems.miguardia`.

## 11. Fuera de alcance

- widgets;
- informes PDF/XLSX;
- copias y restauración;
- bloqueo local o biometría;
- cálculos monetarios o liquidaciones;
- otras profesiones;
- cuentas, backend, nube, sincronización o monetización;
- permisos anticipados;
- tutorial exhaustivo sobre cada opción interna o mediante overlays que impidan salir; el recorrido contextual acotado de controles principales sí pertenece al incremento;
- soporte remoto o envío automático de diagnósticos;
- Room v6 o cualquier migración.

## 12. Definición de terminado

El incremento sólo está listo para auditoría cuando:

- la introducción inicial, repetición, Ayuda y primera carga funcionan sobre la base correcta;
- no existen formularios ni fuentes duplicadas;
- abandono y errores no dejan datos parciales falsos;
- `git diff --check` queda limpio;
- pruebas y builds informan resultados exactos;
- QA físico por impacto se ejecutó o se declara pendiente;
- no hay secretos, datos reales, artefactos, logs privados ni paquetes QA instalados;
- Room v5, trece entidades, esquemas 1..5 y migraciones 1→2→3→4→5 permanecen intactos;
- no se hizo commit, push, merge o rebase sin autorización.

## 13. Devolución obligatoria a MAIN

Entregá un informe autocontenido con:

1. ruta, rama, base, HEAD y estado Git;
2. persistencia versionada y manejo de errores;
3. pasos y navegación inicial;
4. Ayuda y repetición;
5. integración con Perfil, Objetivos/horarios y Calendario;
6. garantías ante abandono y datos históricos;
7. mapa de impacto;
8. pruebas y conteos exactos;
9. lint y ensamblados;
10. QA físico realizado o pendiente;
11. privacidad, riesgos y comprobaciones no ejecutadas;
12. confirmación de cero publicaciones no autorizadas.
