# MiGuardia — dependencia especializada Perfil laboral y Configuración

> Estado: implementación auditada, integrada y publicada en `main` mediante `32767084ae96c399ab4af40cae35eaa40b1ae925`
>
> Fecha: 2026-08-18
>
> Base requerida: `main` limpia en `ef2daabd35e7e88b11e56879e50f2b383c63664f` o un descendiente autorizado que no cambie estos contratos
>
> Rama sugerida: `codex/guard-profile-settings`

## 1. Rol y autoridad

Implementá únicamente Perfil laboral y la reorganización acotada de Configuración para MiGuardia V1. No redefinas el producto ni amplíes el alcance.

Antes de actuar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/prompts/COORDINACION_EXPERIENCIA_INICIAL_Y_PERFIL_MAIN.md`;
4. `docs/adr/0013-perfil-laboral-local-en-datastore.md`;
5. este documento;
6. código y pruebas de `MiGuardiaApplication`, `MainActivity`, navegación raíz, Configuración, gestión de objetivos/horarios, DataStore y tema Vigilia.

La instrucción explícita actual de Joa y MAIN prevalece. Si aparece una contradicción funcional, detené ese punto y devolvelo a MAIN; no inventes una conciliación.

## 2. Base y precondiciones

Antes de editar, informá:

- ruta de trabajo;
- rama;
- `HEAD`;
- relación con la base indicada;
- `git status --short --branch`;
- archivos no rastreados.

La base debe estar limpia. No uses un worktree histórico ni copies una implementación anterior. No hagas commit, push, merge, rebase, cambio de Room o limpieza de archivos salvo autorización explícita de MAIN.

## 3. Objetivo del incremento

Crear un perfil local —no una cuenta— que concentre la información profesional actual del usuario y ordenar Configuración sin duplicar fuentes ni preferencias.

Al finalizar debe existir:

- entrada visible `Perfil laboral` desde Configuración;
- nombre o apodo opcional;
- profesión visible y fija `Vigilancia y seguridad`;
- empresa inicialmente `Inforce`, editable y persistida;
- proyección de objetivos y horarios activos desde Room, sin copias;
- explicación de que el puesto se define en cada carga;
- organización coherente de las superficies existentes de Configuración;
- una única fuente local de empresa para informes futuros.

## 4. Decisiones funcionales congeladas

### 4.1 Perfil

- Es local y funciona sin cuenta, login, red, nube o sincronización.
- `Nombre o apodo` es opcional. Al guardar se recortan espacios externos; vacío representa ausencia y no muestra saludos ficticios.
- `Profesión` muestra exactamente `Vigilancia y seguridad`, no es editable y no se persiste.
- `Empresa` comienza en `Inforce`, se puede editar, se recortan espacios externos y no puede guardarse vacía.
- No solicitar ni guardar DNI, legajo, correo, teléfono, domicilio personal, fecha de nacimiento, foto, credenciales o identificadores innecesarios.
- No agregar selector de profesión ni preparar otras profesiones en la interfaz.

### 4.2 Datos existentes

- Objetivos activos provienen de `ObjectiveRepository`; no se duplican en Perfil.
- Horarios activos provienen de `ScheduleCombinationRepository` y se agrupan bajo su objetivo; no se duplican.
- La administración sigue ocurriendo en el flujo vigente `Objetivos y horarios`. Perfil puede enlazarlo, pero no crea formularios paralelos.
- El puesto es opcional y pertenece a cada carga/guardia. Perfil sólo explica esa regla; no guarda un puesto predeterminado.
- Cambiar nombre o empresa jamás reescribe guardias, objetivos, horarios, fotos, novedades, vacaciones ni instantáneas históricas.

### 4.3 Configuración

Organizá lo ya existente con divulgación progresiva y sin placeholders de módulos futuros:

1. trabajo: Perfil laboral, Objetivos y horarios, Feriados y Vacaciones;
2. avisos y contexto: Notificaciones y Clima;
3. apariencia: tema Vigilia y zoom interno.

No repitas en Perfil controles de Notificaciones, Clima, tema, zoom, remuneración o privacidad. No crees todavía pantallas vacías para widgets, copias, bloqueo, ayuda o informes.

Conservá las opciones de tema `Seguir el sistema`, `Claro` y `Oscuro`, y zoom interno `100 %`, `150 %` y `200 %`. No consultes ni modifiques `font_scale`, densidad o zoom del sistema.

## 5. Arquitectura y persistencia

Aplicá `docs/adr/0013-perfil-laboral-local-en-datastore.md`:

- DataStore Preferences exclusivo, sugerido `guard_profile.preferences_pb`;
- modelo equivalente a `GuardProfile(displayName: String?, company: String)`;
- valor inicial `displayName = null`, `company = "Inforce"`;
- profesión como constante del producto;
- flujo observable y método de guardado atómico;
- manejo de `IOException` equivalente a los DataStore existentes;
- constructor interno con `File` y `CoroutineScope` para pruebas aisladas;
- instancia única expuesta por `MiGuardiaApplication`;
- ViewModel propio para Perfil; Compose no escribe directamente en DataStore.

Room debe permanecer exactamente en v5. No cambies entidades, esquemas, DAO, migraciones ni `LocalDataStore`. Tampoco cambies manifiesto, permisos, Gradle, dependencias, canales o red.

Para la proyección activa, reutilizá los repositorios existentes y filtrá horarios por `isActive`. Evitá ciclos de flujos y duplicados. La pantalla debe tolerar lista vacía, objetivos sin horarios y cambios reactivos.

## 6. Navegación y estado

Integrá Perfil como una superficie bloqueante coherente con Vacaciones, Notificaciones, Clima y gestión:

- Configuración abre Perfil;
- Atrás cierra Perfil de manera convencional;
- si existen cambios sin guardar, Atrás o cerrar debe pedir confirmación antes de descartarlos;
- guardar una vez produce una sola escritura y un mensaje claro;
- durante guardado se evita doble envío;
- errores de lectura o escritura se muestran sin filtrar rutas ni datos;
- tras guardar, la pantalla refleja la versión persistida;
- rotación/recomposición no debe borrar el borrador ni duplicar operaciones.

No cambies la navegación inferior ni el modo consulta/edición del Calendario.

## 7. Presentación Vigilia

La pantalla debe usar los componentes y tokens existentes:

- encabezado claro `Perfil laboral`;
- resumen breve de almacenamiento local;
- campos con etiquetas visibles, no sólo placeholders;
- profesión diferenciada como dato fijo;
- empresa y nombre editables;
- objetivos/horarios activos como proyección legible y no editable;
- estado vacío útil que enlace a `Objetivos y horarios`;
- explicación breve de que el puesto se elige en cada guardia;
- mensajes persistentes para éxito y error;
- tema claro, oscuro y sistema;
- zoom interno 100/150/200 %, retrato y paisaje sin controles inaccesibles.

No uses datos reales de Joa en previews, pruebas, capturas o logs. Usá nombres y objetivos ficticios.

## 8. Privacidad y seguridad

- No registrar en logs nombre, empresa, objetivos, horarios, puestos ni contenido del DataStore.
- No agregar analítica, telemetría, anuncios, rastreadores o red.
- No enviar Perfil a Clima, Notificaciones ni mapas.
- No mostrar datos del perfil en notificaciones, widgets o pantalla de bloqueo en este incremento.
- No incluir archivos `.preferences_pb`, bases, capturas con datos reales, credenciales ni configuración local en Git.
- Los errores deben ser genéricos para el usuario y conservar la causa sólo sin datos sensibles cuando una prueba la necesite.

## 9. Mapa de impacto esperado

El cambio puede alcanzar:

- nuevo store/modelo de Perfil;
- `MiGuardiaApplication`;
- ViewModel, estado, acciones y pantalla de Perfil;
- wiring de `MainActivity`;
- navegación raíz y Configuración;
- strings;
- pruebas JVM, Compose e instrumentadas;
- documentación de estado.

No debe alcanzar Room, esquema, migraciones, manifiesto, Gradle, permisos, red, Calendar domain, motor de horas, remuneración, notificaciones, clima ni datos productivos.

Antes de elegir pruebas, entregá un mapa real basado en el diff y justificá cualquier desviación.

## 10. Pruebas mínimas

### 10.1 JVM

- normalización de nombre vacío a ausencia;
- normalización de espacios externos;
- empresa `Inforce` por defecto;
- rechazo de empresa vacía;
- proyección activa agrupada sin duplicados;
- objetivos ocultos y horarios ocultos excluidos;
- objetivo activo sin horarios representado correctamente.

### 10.2 Instrumentadas aisladas

- DataStore nuevo abre con valores iniciales;
- guarda nombre y empresa;
- reapertura conserva ambos;
- guardar nombre vacío elimina la preferencia personal sin afectar empresa;
- usa archivo temporal y scope propio; no toca datos productivos.

### 10.3 Compose

- Configuración muestra una sola entrada Perfil y conserva las entradas actuales una vez cada una;
- Perfil muestra profesión fija, empresa inicial y nombre opcional;
- edición invoca una sola acción de guardado;
- empresa vacía presenta error y no guarda;
- proyecciones activas se muestran sin controles de edición duplicados;
- vacío enlaza al flujo real de Objetivos y horarios;
- Atrás protege un borrador modificado;
- 100 %, 150 % y 200 %, claro y oscuro mantienen campos y acciones alcanzables.

### 10.4 Regresión por impacto

- navegación inferior;
- Configuración y Apariencia;
- apertura de Objetivos, Feriados, Vacaciones, Notificaciones y Clima;
- Calendario continúa iniciando en consulta y sus mutaciones sólo aparecen en edición;
- instantáneas históricas permanecen iguales tras editar Perfil.

Ejecutá con `--max-workers=1` las pruebas afectadas, `testDebugUnitTest`, `lintDebug` y ensamblados proporcionados al impacto. MAIN decide si corresponde una batería global adicional.

El QA físico, si MAIN lo autoriza, usa únicamente `com.blackatsystems.miguardia.qa` y su paquete de pruebas. No instalar, actualizar, desinstalar ni borrar datos de `com.blackatsystems.miguardia`.

## 11. Fuera de alcance

- bienvenida, onboarding, primera carga y Ayuda;
- widgets;
- informes PDF/XLSX;
- copias y restauración;
- bloqueo local o biometría;
- cambios de remuneración o reglas SUVICO;
- otras profesiones;
- cuenta, backend, nube, sincronización o monetización;
- rediseño general de Calendario, Resumen o notificaciones;
- Room v6 o cualquier migración.

## 12. Definición de terminado

La dependencia sólo está lista para auditoría cuando:

- el alcance completo funciona sobre la base correcta;
- el diff no contiene archivos ajenos;
- `git diff --check` queda limpio;
- pruebas y builds elegidos terminan con resultados exactos informados;
- no hay secretos, datos reales, artefactos generados ni logs privados;
- Room v5, trece entidades, esquemas `1..5` y migraciones `1→2→3→4→5` permanecen intactos;
- la documentación coincide con el comportamiento;
- no se hizo commit, push, merge o rebase sin autorización.

## 13. Devolución obligatoria a MAIN

Entregá un informe autocontenido con:

1. ruta, rama, base, HEAD y estado Git;
2. arquitectura elegida y archivos cambiados;
3. modelo y normalización;
4. navegación y UX;
5. proyecciones de objetivos/horarios;
6. persistencia y reapertura;
7. protección de historia y privacidad;
8. mapa de impacto;
9. pruebas ejecutadas con conteos reales;
10. lint y ensamblados;
11. QA físico realizado o explícitamente no realizado;
12. riesgos y pendientes;
13. confirmación de cero commits y publicaciones no autorizadas.
