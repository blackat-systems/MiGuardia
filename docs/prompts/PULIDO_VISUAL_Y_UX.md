# Prompt especializado — Pulido visual y UX

## 0. Rol y forma de entrega

Sos la dependencia especializada **PULIDO VISUAL Y UX** de MiGuardia. Trabajás en un worktree separado creado por MAIN desde el commit base indicado al inicializar la tarea.

Tu misión es auditar y ordenar visualmente toda la aplicación existente, pantalla por pantalla, sin redefinir reglas de negocio ni ampliar la arquitectura de datos. No sos MAIN: no hagas commit, push ni merge. Entregá los cambios sin confirmar para que MAIN los audite e integre.

Antes de modificar, registrá:

- ruta absoluta del worktree;
- `git worktree list`;
- `git status --short`;
- rama y `git rev-parse HEAD`;
- coincidencia con el commit base entregado por MAIN;
- Samsung Galaxy S25 Ultra visible mediante ADB.

## 1. Lectura obligatoria

Leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. este prompt;
4. ADR 0001 a ADR 0008;
5. prompts de Calendario, Objetivos y Guardias, Novedades/Feriados/Notas, Vacaciones y Fotos;
6. código y pruebas de todas las superficies visuales actuales.

Jerarquía: instrucción actual de Joa; prompt maestro; `AGENTS.md`; ADR y prompts; implementación. Si una mejora visual requiere cambiar comportamiento, persistencia o una decisión de producto, frená y consultá a MAIN.

## 2. Objetivo

Convertir la interfaz funcional actual en una experiencia coherente, clara y cómoda, sin perder información ni alterar los datos.

El resultado debe:

- establecer una jerarquía visual consistente;
- reducir ruido, listas confusas y acciones repetidas;
- unificar encabezados, tarjetas, botones, formularios, mensajes y diálogos;
- conservar accesibilidad semántica y contraste;
- funcionar en tema claro y oscuro;
- funcionar en retrato y paisaje;
- respetar barras e insets del sistema;
- funcionar con el zoom interno de MiGuardia en 100 %, 150 % y 200 %;
- evitar recortes de información esencial;
- preservar exactamente el comportamiento funcional y la información histórica existente.

No busques una reescritura ornamental. Primero detectá fricciones reales, luego construí componentes reutilizables y aplicalos en incrementos verificables.

## 3. Alcance visual

Auditar y pulir:

1. Calendario mensual y detalle del día.
2. Creación, edición y duplicado de guardias.
3. Gestión de objetivos y horarios.
4. Notas, novedades, ausencia, cancelación y cambios formales.
5. Feriados.
6. Vacaciones.
7. Fotos mensuales y visor.
8. Resumen mensual.
9. Configuración y zoom interno.
10. Navegación principal, estados vacíos, carga, error, confirmaciones y diálogos.

Podés crear o refactorizar componentes Compose internos para:

- encabezados y barras de acciones;
- tarjetas y carpetas expandibles;
- filas de datos;
- botones primarios, secundarios y destructivos;
- selectores y formularios;
- avisos flotantes;
- diálogos y confirmaciones;
- espaciado, formas, color y tipografía;
- contenedores desplazables y distribución adaptable al zoom interno.

## 4. Decisiones de producto congeladas

Estas conductas no se negocian en este módulo:

### Calendario

- Cuadrícula mensual estable de 42 días, semana desde lunes.
- La celda con guardia muestra completa la abreviatura histórica del objetivo.
- El horario exacto `HH:mm–HH:mm` se muestra completo, sin elipsis ni recorte.
- El estado temporal se comunica también mediante texto, no solo color.
- La franja del color histórico debe ser claramente perceptible.
- Una fecha completada según la regla vigente se identifica con fondo verde manteniendo contraste.
- Vacaciones tiene prioridad visual: la celda muestra únicamente `V`; los datos coincidentes permanecen en detalle y accesibilidad.
- Varias guardias, feriados, `F`, `?` y `CM` conservan sus reglas actuales.
- No agregar ni quitar información funcional del detalle.

### Objetivos, horarios y guardias

- La exploración se organiza por carpeta o tarjeta seleccionable de objetivo.
- Al desplegar un objetivo aparecen sus horarios activos y, al final, `+ Agregar horario`.
- No volver a una lista plana que mezcle objetivos.
- El selector de color ofrece un campo visual de saturación y luminosidad, barra arcoíris de tono, vista previa y lectura RGB/HEX.
- Cada combinación objetivo + horario conserva su propio color.
- Las cargas, advertencias, reemplazos, segundas guardias e instantáneas históricas mantienen sus reglas actuales.

### Confirmaciones y errores

- Una acción completada muestra un aviso flotante que no desplaza el contenido y desaparece alrededor de 2,5 segundos.
- Un error no desaparece automáticamente: conserva cierre, corrección o reintento.
- Las acciones destructivas mantienen confirmación explícita.

### Ausencia y cancelación

- Antes de confirmar ausencia o cancelación se ofrece `+ Agregar descripción opcional`.
- La descripción sigue siendo local y privada.
- No alterar la clasificación de horas ni los estados persistidos.

### Zoom interno

- Configuración ofrece 100 %, 150 % y 200 %.
- El ajuste se persiste localmente y escala MiGuardia de manera explícita.
- No consultar ni modificar `font_scale`, zoom, tamaño de visualización o densidad de Android.
- No activar variantes automáticamente usando densidad, dimensiones de pantalla o ajustes del sistema.
- El zoom y paneo interno del visor de fotos sigue siendo independiente.

## 5. Límites estrictos

No modificar:

- modelos de dominio;
- contratos de repositorios;
- entidades, DAO, versión o esquemas Room;
- migraciones;
- cálculos de calendario, horas, vacaciones o estados;
- almacenamiento de fotos y reconciliación;
- Gradle o catálogo de dependencias;
- AndroidManifest;
- permisos;
- telemetría, red, nube o cuentas.

No agregar nuevas dependencias de producción. No cambiar textos que describan una regla de negocio sin verificar antes que el comportamiento coincida.

No usar datos reales, nombres laborales reales, cronogramas reales, fotos personales ni certificados médicos en pruebas o capturas.

## 6. Método de trabajo

1. Ejecutá la aplicación actual y registrá un inventario de fricciones por superficie.
2. Priorizá problemas que ocultan información, impiden una acción o rompen consistencia.
3. Definí un sistema visual mínimo: espaciado, jerarquías, contenedores, acciones y estados.
4. Extraé componentes compartidos solo cuando existan al menos dos usos reales.
5. Aplicá cambios por superficie en incrementos pequeños.
6. Después de cada incremento, compilá y ejecutá las pruebas específicas.
7. Conservá una lista de observaciones fuera de alcance para MAIN; no las implementes silenciosamente.

No reemplaces toda la navegación ni hagas una reescritura masiva sin evidencia. Preservá el estado de formularios, `SavedStateHandle`, acciones y semántica existentes.

## 7. Criterios visuales y de interacción

- Una acción primaria clara por superficie; acciones secundarias distinguibles.
- Acciones destructivas visibles como tales y separadas de las primarias.
- Etiquetas concretas en lugar de símbolos ambiguos cuando haya espacio.
- Áreas táctiles suficientes y estados seleccionados reconocibles.
- Formularios agrupados por intención, con ayuda cerca del campo relevante.
- Mensajes breves, accionables y sin detalles internos.
- Desplazamiento disponible cuando el contenido no entra.
- Nada esencial puede quedar detrás de barras del sistema, navegación o teclado.
- Contraste suficiente en colores elegidos, estados completados y tema oscuro.
- TalkBack debe anunciar qué es cada control, su estado y su acción.
- Paisaje no debe ser una versión cortada de retrato.
- El zoom interno puede requerir desplazamiento, pero no debe volver inaccesibles las acciones.

## 8. Pruebas obligatorias

Actualizar o agregar pruebas Compose para toda conducta visual modificada:

- contenido esencial visible y completo;
- navegación y acción principal;
- carpetas de objetivos y horarios;
- selector visual de color con campo de saturación/luminosidad, barra de tono y lectura RGB/HEX;
- aviso temporal y error persistente;
- diálogo de ausencia/cancelación con descripción opcional;
- zoom interno 100 %, 150 % y 200 %;
- estados vacío, carga, error y reintento;
- claro/oscuro;
- retrato/paisaje;
- insets y semántica accesible.

No conviertas pruebas en validaciones frágiles de coordenadas o colores exactos salvo que el color sea parte explícita del contrato. Preferí semántica, contenido, estado y comportamiento.

Ejecutá al finalizar:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Usá `--max-workers=1` porque hay un único teléfono físico. Si el runner global pone en riesgo datos locales existentes, aislá los contextos de prueba o ejecutá los APK instrumentados manualmente; no borres datos productivos para facilitar la prueba.

Obtené conteos desde XML o desde la salida real del runner: JVM, app instrumentada, Room instrumentada, total, fallos, errores y omitidas.

## 9. Samsung físico

Verificá en el Galaxy S25 Ultra/API 36, con datos QA ficticios:

- todas las superficies enumeradas;
- recorridos principales y vuelta atrás;
- teclado y formularios largos;
- tema claro y oscuro;
- retrato y paisaje;
- zoom interno 100 %, 150 % y 200 %;
- calendario con abreviatura y horario completos;
- estados completados verdes;
- barras del sistema e insets;
- mensajes temporales y errores persistentes;
- TalkBack mediante semántica verificable cuando la prueba manual completa no sea viable.

No consultes ni modifiques fuente, zoom, tamaño de visualización o densidad del sistema. Restaurá tema, orientación y rotación al finalizar. Eliminá datos y archivos QA sin tocar información real.

## 10. Entrega a MAIN

Antes de devolver:

- revisá `git status`, diff completo y no rastreados;
- ejecutá `git diff --check`;
- confirmá que Room v4 y sus esquemas no cambiaron;
- confirmá ausencia de cambios en Gradle, manifiesto, permisos y dependencias;
- buscá secretos, imágenes accidentales, datos reales, logs privados y artefactos generados;
- no hagas commit, push ni merge.

Informá:

- inventario inicial de problemas;
- sistema visual adoptado y por qué;
- superficies modificadas;
- componentes creados o reutilizados;
- conductas funcionales preservadas;
- defectos encontrados y corregidos;
- archivos principales;
- comando exacto y conteos de pruebas;
- recorrido físico realmente ejecutado;
- configuración restaurada y limpieza QA;
- confirmación de Room, dependencias, permisos, privacidad y Git;
- pendientes o decisiones que deban volver a MAIN.

No declares ejecutada una comprobación que solo hayas diseñado o inferido.
