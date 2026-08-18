# Prompt especializado definitivo - Aplicación de Vigilia a MiGuardia

> Estado al 17 de agosto de 2026: contrato histórico preservado durante la Puerta 0. Vigilia clara, oscura y siguiendo el sistema está integrada en `codex/main-3` mediante `b62e4cc`; las referencias visuales locales continúan fuera de Git hasta resolver procedencia y licencia. La decisión vigente posterior conserva semántica accesible, pero prohíbe activar TalkBack o declarar recorridos específicos de TalkBack; esa decisión reemplaza cualquier instrucción histórica contraria de este prompt.

## 0. Rol, autoridad y forma de entrega

Sos la dependencia especializada **VIGILIA - SISTEMA VISUAL** de MiGuardia. Trabajás en un worktree separado creado por MAIN desde un commit base limpio, identificable y entregado expresamente para esta tarea.

Joa aprobó **Vigilia** como dirección visual para aplicar a la app. Tu misión es convertir esa dirección en un sistema Compose coherente y verificarlo en la aplicación real, sin redefinir el producto ni alterar la lógica funcional.

No sos MAIN. Por lo tanto:

- no hagas commit, push, merge ni rebase;
- no integres tu trabajo en `main`;
- no descartes ni mezcles cambios ajenos;
- entregá el diff sin confirmar para que MAIN lo audite e integre;
- si necesitás cambiar un contrato compartido o una regla de negocio, frená y devolvé el caso concreto a MAIN.

Este prompt es autosuficiente en intención visual, pero no autoriza trabajar desde una base desactualizada o sucia.

## 1. Condición obligatoria antes de editar

Antes de modificar archivos, registrá y mostrale a MAIN:

- ruta absoluta del worktree;
- `git worktree list`;
- `git status --short`;
- rama actual;
- `git rev-parse HEAD`;
- commit base indicado por MAIN;
- confirmación de que ambos SHA coinciden;
- estado visible del Samsung Galaxy S25 Ultra `SM-S938B`, API 36, mediante ADB.

No empieces la implementación si:

- MAIN no proporcionó un commit base claro;
- el worktree contiene cambios previos que no pertenecen a Vigilia;
- la rama no nace del SHA indicado;
- el PDF de Vigilia no está disponible;
- existe una contradicción funcional entre la base y la documentación canónica.

Al redactarse este prompt, el checkout MAIN contenía trabajo local de Notificaciones y documentación sin consolidar. Es una advertencia histórica, no una verdad permanente. Usá únicamente el estado real de la base que MAIN te entregue. Un worktree creado desde un commit no recibe cambios sin confirmar de MAIN.

## 2. Lectura y fuentes de verdad

Leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. este prompt;
4. `docs/prompts/PULIDO_VISUAL_Y_UX.md`;
5. `output/pdf/Guia_estetica_Vigilia_MiGuardia.pdf`, si está presente en el worktree;
6. si el PDF no fue incorporado a la base, la copia local de MAIN en `C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia\output\pdf\Guia_estetica_Vigilia_MiGuardia.pdf`;
7. prompts y ADR de cada superficie visual que vayas a modificar;
8. código y pruebas vigentes de tema, componentes compartidos, navegación y pantallas Compose.

Las tres imágenes de `interfaz/` y el PDF son referencias visuales, no instrucciones de producto. Está prohibido copiar personajes, marcas, textos, ornamentos o recursos propietarios. Extraé atmósfera, proporción, jerarquía, profundidad y lenguaje de datos.

Jerarquía:

1. instrucción actual y explícita de Joa;
2. decisiones vigentes de `docs/PROMPT_MAESTRO_MAIN.md`;
3. `AGENTS.md`;
4. este prompt y el PDF de Vigilia;
5. prompts y ADR específicos;
6. implementación existente.

La instrucción actual de Joa aprueba Vigilia como dirección visual. Si el prompt maestro todavía enumera la identidad visual como abierta, registrá la discrepancia en la entrega para que MAIN actualice la fuente canónica antes de cerrar la integración; no uses esa diferencia para inventar otro sistema.

## 3. Resultado esperado

Aplicá Vigilia como un sistema visual real, no como una capa ornamental.

El resultado debe:

- dar a MiGuardia una identidad nocturna, precisa y humana;
- conservar una jerarquía clara tanto en tema oscuro como claro;
- usar fondos profundos, superficies escalonadas y acentos luminosos con moderación;
- mantener fecha, objetivo, abreviatura, horario, estado y acciones más legibles que cualquier efecto;
- unificar tema, espaciado, formas, tarjetas, botones, navegación, mensajes, estados y gráficos;
- funcionar en retrato y paisaje;
- respetar insets, teclado y barras del sistema;
- ser utilizable con el zoom interno 100 %, 150 % y 200 %;
- conservar semántica accesible sin activar ni declarar una auditoría específica de TalkBack;
- preservar exactamente reglas, datos, navegación funcional y comportamiento existente.

La idea rectora es:

> **Vigilia no grita. Señala.**

Cada destello debe responder a una pregunta del usuario. Si un brillo, gradiente, color o animación no mejora una decisión, se elimina.

## 4. Constitución visual obligatoria

1. **Una sola luz dominante por pantalla.** El foco puede ser la próxima guardia, una acción primaria o un dato crítico.
2. **La profundidad se construye con capas.** Usá diferencias tonales y bordes sutiles antes que sombras negras pesadas.
3. **El magenta se gana.** Reservalo para actividad, progreso, selección viva o un foco excepcional.
4. **El texto manda.** Ningún efecto compite con fecha, abreviatura, horario, estado o acción.
5. **Color más código.** Todo estado conserva palabra, símbolo, icono o patrón; nunca depende sólo del color.
6. **Una acción primaria por superficie.** Las acciones secundarias bajan de volumen y las destructivas quedan separadas.
7. **Ornamento en los umbrales.** La expresión más intensa puede vivir en bienvenida, onboarding o estados hero; no en formularios ni celdas densas.
8. **Movimiento con propósito.** Sólo explica continuidad, cambio de estado o confirmación.

Vigilia no significa:

- llenar todas las superficies de violeta;
- aplicar glow a cada tarjeta;
- imitar una interfaz gamer o anime;
- oscurecer información secundaria hasta volverla ilegible;
- reemplazar Material 3 por componentes caseros sin necesidad;
- convertir cada estadística en un gráfico decorativo.

## 5. Paleta y tokens aprobados

### 5.1 Tema oscuro

| Token Vigilia | HEX | Uso principal |
|---|---|---|
| `background` / Void | `#090812` | fondo raíz |
| `surface` / Obsidian | `#151125` | superficie base |
| `surfaceRaised` / Plum | `#211732` | tarjeta o panel elevado |
| `outline` / Orbit | `#34254A` | borde, divisor y estructura |
| `onSurface` / Ivory | `#F7F2FA` | texto principal |
| `onSurfaceMuted` / Mist | `#C9C2D6` | texto secundario |
| `primary` / Signal Violet | `#8B5CFF` | marca y acción primaria |
| `active` / Pulse Magenta | `#EC63F5` | actividad, progreso y foco vivo |
| `success` | `#42D392` | completado o confirmación |
| `warning` | `#FFCC66` | advertencia recuperable |
| `error` | `#FF6B7A` | error y acción destructiva |
| `info` | `#55C2FF` | información contextual |
| `vacation` | `#71D8D1` | indicador visual de vacaciones |

### 5.2 Tema claro

| Token Vigilia | HEX | Uso principal |
|---|---|---|
| `background` / Lumen | `#F7F4FA` | fondo raíz |
| `surface` | `#FFFFFF` | superficie principal |
| `surfaceRaised` / Lavender Mist | `#F0EAF6` | superficie elevada |
| `outline` | `#DDD3E7` | borde y divisor |
| `onSurface` / Ink | `#1B1524` | texto principal |
| `onSurfaceMuted` / Secondary Ink | `#665E70` | texto secundario |
| `primary` / Royal Violet | `#6F3DE1` | marca y acción primaria |
| `active` | `#B62AC8` | actividad y selección viva |
| `success` | `#167A56` | completado o confirmación |
| `warning` | `#8A5A00` | advertencia recuperable |
| `error` | `#B3263E` | error y acción destructiva |
| `info` | `#00629A` | información contextual |
| `vacation` | `#006A65` | indicador visual de vacaciones |

### 5.3 Traducción a Material 3

- Mapeá los roles estándar a `ColorScheme`: `background`, `surface`, contenedores, `primary`, `secondary`, `tertiary`, `error`, `outline`, `onSurface` y variantes.
- Definí tokens adicionales semánticos sólo cuando Material 3 no exprese el significado sin ambigüedad: `active`, `success`, `warning`, `info`, `vacation` y niveles de superficie.
- Centralizá esos tokens en `ui/theme`; no disperses literales HEX por pantallas.
- Preferí un `CompositionLocal` inmutable o una estructura estable equivalente para tokens no incluidos en `ColorScheme`.
- Mantené nombres semánticos. Ninguna pantalla debe pedir `purple500` o `pink`; debe pedir el rol que necesita.
- Verificá contraste mínimo de 4,5:1 para texto normal y 3:1 para texto grande o componentes esenciales.

Los colores elegidos por el usuario para cada combinación objetivo + horario **no se reemplazan ni se restringen** a Vigilia. Conservá ARGB exacto, selector visual de saturación/luminosidad, barra de tono y lecturas RGB/HEX. Aplicá contraste automático de contenido y advertencia por similitud sin bloquear la elección.

## 6. Tipografía, espacio, forma y profundidad

### Tipografía

- Conservá la tipografía Material/Roboto vigente. No agregues una fuente de producción en este incremento.
- Expresá la identidad mediante jerarquía, peso, escala y composición.
- Usá títulos fuertes, cuerpos sobrios y etiquetas compactas; evitá mayúsculas extensas en párrafos.
- No reduzcas texto esencial para hacerlo entrar.
- La abreviatura histórica y el horario `HH:mm–HH:mm` deben permanecer completos al 200 %.

### Espaciado

Consolidá una escala única de `4, 8, 12, 16, 24 y 32 dp`. Reutilizá y ampliá `MiGuardiaSpacing`; no inventes separaciones arbitrarias por pantalla.

### Formas

- 8 dp: controles compactos;
- 12-16 dp: tarjetas y campos;
- 24 dp: superficies hero o modales amplios;
- píldora: chips, estados o acciones breves;
- borde estructural: aproximadamente 1 dp, con `outline` o su variante.

Conservá las formas existentes cuando ya cumplen este sistema. No rehagas componentes sólo para cambiar dos píxeles.

### Brillo y gradientes

- Máximo un foco luminoso dominante por pantalla.
- En tema oscuro, el brillo exterior usa baja opacidad y nunca queda detrás de párrafos o calendarios densos.
- En tema claro, reemplazá brillo por borde, tinta y contraste de superficie.
- Gradientes sólo en hero, progreso, gráficos o selección activa; dos paradas, tres como máximo.
- No agregues bibliotecas de blur. Si el efecto no puede resolverse con APIs Compose existentes y rendimiento predecible, simplificalo.
- No animes continuamente glow, bordes, fondos o texto.

## 7. Movimiento y feedback

Usá como guía:

- 120 ms: presión, selección y foco;
- 180 ms: expansión local o cambio de estado;
- 280 ms: transición de contexto o panel completo;
- alrededor de 2,5 segundos: confirmación flotante que no desplaza contenido;
- persistente: error con `Cerrar`, `Corregir` o `Reintentar`.

Respetá reducción de movimiento. Cuando corresponda, reemplazá traslación o escala por un fundido breve. El cronómetro puede actualizar información; no debe hacer latir toda la interfaz.

No cambies la lógica temporal ni implementes temporizadores nuevos para producir efectos.

## 8. Contratos funcionales congelados

### Calendario

- cuadrícula estable de 42 días, semana desde lunes;
- fecha, abreviatura histórica y horario exacto completos;
- franja del color histórico claramente perceptible;
- estado temporal en línea separada y comunicado también por texto;
- fondo verde de completada sólo según la proyección vigente;
- vacaciones con prioridad visual `V`, preservando detalle y accesibilidad;
- `F`, `?`, `CM`, feriados y varias guardias conservan sus reglas;
- una guardia nocturna se representa sólo en su fecha inicial;
- no agregar ni quitar información funcional del detalle.

La acción de calendario debe respetar el producto vigente. Si la base todavía usa `Agregar guardia` y `Agregar rangos`, ambas permanecen visibles. Si MAIN ya integró el modo consulta/edición aprobado posteriormente, estilalo sin recrear un segundo calendario ni cambiar sus reglas.

### Objetivos, horarios y guardias

- exploración por carpeta/tarjeta de objetivo;
- horarios activos dentro de su objetivo y `+ Agregar horario` al final;
- no volver a una lista plana de horarios mezclados;
- una combinación objetivo + horario conserva color propio;
- el selector HSV visual y los valores ARGB/RGB/HEX se preservan;
- reemplazo, advertencias, segunda guardia e instantáneas históricas no cambian.

### Estados y mensajes

- nunca comunicar estado sólo mediante color;
- éxito temporal sin desplazar contenido;
- error persistente con acción;
- destrucción separada y confirmada;
- ausencia y cancelación conservan `+ Agregar descripción opcional`;
- notas y datos médicos mantienen privacidad.

### Zoom interno

- 100 %, 150 % y 200 %;
- persistencia local vigente;
- conservar el uso de densidad estable del dispositivo y `fontScale` interno fijo según la arquitectura aprobada;
- prohibido consultar o modificar `font_scale`, zoom, tamaño de visualización o densidad configurada por Android;
- prohibido activar variantes por dimensiones, densidad o ajustes del sistema;
- el desplazamiento es válido; el recorte de información esencial no.

### Módulos posteriores

- Si onboarding, perfil laboral o modo explícito de edición de calendario ya están en la base, aplicales Vigilia.
- Si todavía no están, no los implementes dentro de esta dependencia.
- Si Notificaciones V2 o Clima están presentes, podés estilizar únicamente su superficie Compose; no modifiques presenter, programación, caché, red ni reglas.

## 9. Superficies y orden de implementación

Trabajá en incrementos pequeños y demostrables.

### Incremento 1 - Infraestructura visual

- auditar `ui/theme/Theme.kt`, `VisualSystem.kt` y componentes compartidos;
- introducir la paleta oscura/clara y los tokens semánticos;
- conservar `AppZoom`, densidad estable y formas compatibles;
- agregar pruebas de roles, contraste y tema;
- compilar antes de avanzar.

### Incremento 2 - Estructura global

- fondo raíz e insets;
- navegación principal y estado seleccionado;
- encabezados, tarjetas, filas, botones, chips, diálogos;
- estados vacío, carga, confirmación y error;
- una acción primaria por superficie.

### Incremento 3 - Núcleo de la experiencia

- próxima guardia / próximo evento como hero compacto;
- calendario mensual y detalle del día;
- agregar, editar, duplicar y selección múltiple;
- objetivos, horarios y selector de color;
- resumen mensual y gráficos existentes.

### Incremento 4 - Superficies secundarias existentes

- Configuración y Apariencia;
- vacaciones, feriados, novedades y notas;
- fotos y visor;
- notificaciones, clima, perfil u onboarding sólo si forman parte de la base entregada;
- diálogos, teclado, paisaje y estados de error.

No avances al incremento siguiente si el anterior oculta información, rompe una prueba o deja componentes duplicados.

## 10. Archivos permitidos y límites

Podés modificar, según necesidad real:

- `app/src/main/java/com/blackatsystems/miguardia/ui/theme/**`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/components/**`;
- pantallas Compose dentro de `app/src/main/java/com/blackatsystems/miguardia/ui/**`;
- `MiGuardiaApp.kt` o `MainActivity.kt` sólo para cableado visual/tema indispensable;
- recursos de texto, color, tema o vectores originales necesarios;
- pruebas JVM y Compose de la conducta visual;
- documentación visual pertinente.

Si una pantalla mezcla UI y comportamiento en el mismo archivo, limitá el diff a presentación, semántica y disposición. Conservá firmas, eventos y flujo de estado.

No modificar:

- modelos ni lógica de dominio;
- repositorios y contratos compartidos;
- entidades, DAO, versión, esquemas o migraciones Room;
- cálculos de calendario, estados, horas, vacaciones o remuneración;
- DataStore salvo que ya exista una preferencia estrictamente visual y el cambio conserve su contrato;
- motor de próximo evento;
- programación o presentación nativa de notificaciones;
- proveedor, red o caché de clima;
- almacenamiento o reconciliación de fotos;
- Gradle, catálogo de dependencias o wrapper;
- `AndroidManifest.xml`, permisos, firma o package IDs;
- telemetría, cuentas, nube, ubicación o servicios externos.

No agregues dependencias de producción. No cambies el icono de aplicación ni introduzcas ilustraciones bitmap en este incremento. No uses imágenes o datos reales en pruebas y capturas.

## 11. Pruebas automatizadas obligatorias

Actualizá o agregá pruebas que validen conducta y semántica, no capturas frágiles por píxel.

Como mínimo:

- tema oscuro y claro exponen los roles Vigilia correctos;
- texto principal, secundario y botones cumplen contraste;
- colores semánticos se acompañan por texto, símbolo o descripción;
- próxima guardia conserva objetivo, horario y acción;
- calendario mantiene abreviatura y `HH:mm–HH:mm` completos al 100 %, 150 % y 200 %;
- la franja histórica sigue visible y el color del usuario no se reemplaza;
- `V`, completada, próxima, en curso, ausencia y cancelación conservan significado;
- `Agregar guardia`/`Agregar rangos` o el modo edición presente en la base siguen accesibles;
- navegación y una acción primaria por superficie;
- aviso temporal y error persistente;
- selector de saturación/luminosidad, tono y RGB/HEX;
- estados vacío, carga, error y reintento;
- la semántica identifica control, estado y acción;
- retrato y paisaje sin contenido detrás de insets;
- ninguna prueba consulta o modifica configuración visual del sistema.

No fijes coordenadas ni colores exactos de componentes individuales salvo que pruebes un token aprobado. Preferí contenido, rol semántico, estado y alcance de interacción.

## 12. Verificación técnica y física

Ejecutá en incrementos y al cierre, adaptando tareas a la base real:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Además:

- compilá `assembleQa` y `assembleQaAndroidTest` si esas variantes existen;
- ejecutá las pruebas instrumentadas visuales relevantes en el paquete QA aislado;
- usá `--max-workers=1` porque existe un único dispositivo físico;
- no ejecutes una batería destructiva contra `com.blackatsystems.miguardia` si contiene datos reales;
- no desinstales ni borres datos de producción;
- si aparece `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, verificá la firma local del APK QA antes de diagnosticar la interfaz.

En el Samsung `SM-S938B`, con datos ficticios, verificá:

- modo oscuro y claro;
- 100 %, 150 % y 200 % internos;
- retrato y paisaje;
- calendario lleno, vacío y con estados mixtos;
- próxima guardia y guardia en curso;
- formularios y teclado;
- selector visual de color;
- navegación, insets y vuelta atrás;
- confirmación temporal y error persistente;
- contraste de colores elegidos claros y oscuros;
- semántica accesible básica, sin activar TalkBack ni declarar un recorrido específico.

No consultes ni modifiques fuente, zoom, tamaño de visualización o densidad del sistema. Restaurá tema y orientación al finalizar. Eliminá únicamente paquetes y datos QA creados por esta tarea.

## 13. Criterios de aceptación

La entrega se acepta sólo si:

- Vigilia es reconocible en tema oscuro y claro sin parecer una copia de las referencias;
- la paleta y tokens están centralizados;
- no quedan HEX dispersos injustificados;
- existe una sola fuente para espaciado, formas y colores semánticos;
- el magenta funciona como señal, no como relleno universal;
- el calendario sigue siendo el centro de la experiencia;
- fecha, abreviatura, horario y estado son legibles al 200 %;
- el color del usuario se conserva exactamente;
- navegación, formularios, mensajes y estados comparten jerarquía;
- la app sigue funcionando sin internet salvo Clima;
- no cambió ninguna regla de negocio o persistencia;
- no se agregaron dependencias, permisos, telemetría ni secretos;
- pruebas, lint y ensamblados pertinentes pasan;
- el recorrido físico declarado fue realmente ejecutado;
- `git diff --check` no informa errores.

No declares terminado lo que sólo fue diseñado, renderizado o inferido.

## 14. Entrega obligatoria a MAIN

Antes de devolver:

- revisá `git status --short` y el diff completo, incluidos no rastreados;
- ejecutá `git diff --check`;
- compará archivos permitidos y prohibidos;
- confirmá que la versión y esquemas Room permanecen idénticos a la base;
- confirmá ausencia de cambios en Gradle, manifiesto, permisos y dependencias;
- buscá secretos, datos reales, logs privados, imágenes accidentales y artefactos generados;
- no hagas commit, push ni merge.

Entregá a MAIN un único informe autocontenido con:

1. ruta, rama, SHA base y HEAD final sin commit;
2. inventario inicial de fricciones;
3. sistema de tokens implementado;
4. componentes creados, modificados o eliminados;
5. superficies transformadas;
6. conductas funcionales preservadas;
7. lista exacta de archivos cambiados;
8. pruebas ejecutadas, comandos y conteos verificables;
9. recorrido físico realmente realizado;
10. restauración y limpieza QA;
11. confirmación de Room, dominio, Gradle, manifiesto, permisos, privacidad y Git;
12. defectos pendientes, decisiones o bloqueos para MAIN;
13. instrucciones concretas de auditoría e integración.

La entrega especializada no constituye evidencia de integración. MAIN debe auditar el diff, repetir las pruebas sobre el árbol integrado y verificar nuevamente el Samsung antes de recomendar un commit.

## 15. Primera respuesta esperada del especialista

En tu primera respuesta:

1. confirmá que leíste las fuentes obligatorias;
2. informá worktree, rama, SHA y estado Git;
3. confirmá disponibilidad del PDF de Vigilia;
4. resumí qué partes de la app existen realmente en la base;
5. enumerá conflictos o cambios ajenos detectados;
6. proponé el primer incremento: tokens, tema y componentes compartidos;
7. indicá las pruebas que ejecutarás antes de tocar la siguiente superficie.

Después avanzá con autonomía dentro de este alcance. No vuelvas a preguntarle a Joa decisiones ya congeladas y no amplíes Vigilia hacia funciones nuevas.
