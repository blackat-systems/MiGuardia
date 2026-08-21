# Prompt maestro — UX/UI Calendario adaptable de MiGuardia 2.0

> Estado al 2026-08-20: implementado y auditado. La evidencia de cierre está en
> `docs/audits/2026-08-20-calendario-adaptable-2-0.md`. Este prompt se conserva
> como contrato de alcance de la entrega.

Sos la tarea especializada **UX/UI CALENDARIO ADAPTABLE** de MiGuardia 2.0.
Joaquin autorizó esta única implementación visual acotada antes de crear MAIN
2.0. No sos PLANIFICACIÓN ni MAIN y no podés ampliar el producto.

## Objetivo

Adecuar la disposición de la pantalla principal del Calendario al ancho y alto
físicos disponibles en la ventana del teléfono, preservando esta jerarquía:

1. próximo evento o estado equivalente arriba;
2. mes y grilla mensual como contenido principal;
3. `Cargar datos`, `Editar calendario` o la acción contextual equivalente debajo
   de la grilla.

Al 100 % de zoom interno, la pantalla debe intentar mostrar simultáneamente los
tres bloques sin desplazamiento vertical. Si la altura no alcanza, debe quedar
claro que existe contenido debajo mediante una barra de desplazamiento visible a
la derecha.

## Inicio obligatorio

Antes de editar:

1. Confirmá que la ruta sea exactamente
   `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
2. Verificá:
   - rama `codex/miguardia-2.0`;
   - `HEAD` inicial `82db6fd8eb2c511205968894dc9857a96b16ed20`;
   - tag `v1.0.0` apuntando al mismo commit;
   - estado Git completo y lista de worktrees.
3. El árbol contiene documentación de planificación modificada o todavía no
   seguida. Es trabajo preexistente de Joaquin/PLANIFICACIÓN: no la descartes,
   limpies, confirmes ni reescribas.
4. Leé completos, en este orden:
   - `AGENTS.md`;
   - este prompt;
   - `docs/STATUS.md`;
   - `docs/PLANIFICACION_MIGUARDIA_2_0.md`, especialmente la puerta U;
   - `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato heredado de 1.0;
   - el código y las pruebas indicados abajo.
5. Si ruta, rama, `HEAD`, tag o propiedad del worktree no coinciden, frená y
   explicáselo a Joaquin. No improvises otra base.

## Fuentes de implementación que debés inspeccionar

- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`
  - `CalendarScreen`;
  - `CalendarGridViewport`;
  - controles del mes y entrada `Cargar datos`/`Editar calendario`.
- `app/src/main/java/com/blackatsystems/miguardia/ui/nextevent/NextEventCard.kt`.
- `app/src/main/java/com/blackatsystems/miguardia/ui/components/VisualSystem.kt`
  - `HeroCard`, sólo para comprender el espaciado compartido.
- `app/src/main/java/com/blackatsystems/miguardia/ui/theme/Theme.kt`
  - contrato de densidad estable y zoom interno; es fuente de verdad y queda
    fuera de modificación.
- Pruebas relevantes:
  - `CalendarComposeTest.kt`;
  - `NextEventComposeTest.kt`;
  - `MiGuardiaAppTest.kt`;
  - `VisualPolishComposeTest.kt`.

## Contrato visual congelado

### Pantalla que entra sin desplazamiento

Con zoom interno 100 % y cuando la altura física disponible lo permita:

- próximo evento, mes, grilla completa y acción inferior quedan visibles en una
  sola mirada;
- no aparece una barra de desplazamiento si realmente no existe desborde;
- se conservan las áreas seguras de barras del sistema;
- no se tapa el último bloque con la navegación ni con otro contenido.

### Orden de compactación

Antes de recurrir al desplazamiento:

1. compactá la tarjeta del próximo evento;
2. reducí separaciones y márgenes verticales no esenciales;
3. preservá la grilla mensual.

Compactar no significa eliminar, truncar u ocultar datos. La tarjeta debe seguir
mostrando la información vigente aplicable: estado, objetivo, abreviatura,
fecha, horario, puesto cuando exista, cuenta regresiva, cantidad adicional y
próximo franco cuando corresponda. Podés reorganizar esa información para usar
menos altura, manteniendo jerarquía y comprensión.

La grilla no se achica antes que la tarjeta o los espacios. Abreviatura, horario,
estado, selección y marcadores deben seguir completos y legibles, con áreas
táctiles seguras.

### Desborde claro

Si, después de compactar, la altura física sigue siendo insuficiente:

- habilitá desplazamiento vertical de forma segura;
- mostrale al usuario una barra vertical reconocible a la derecha siempre que
  exista contenido fuera de la ventana;
- el indicador debe ser visible al entrar y durante el recorrido, no depender
  únicamente de una animación fugaz;
- su tamaño y posición deben representar razonablemente el contenido y el
  desplazamiento;
- no debe tapar texto, botones ni celdas, ni interceptar gestos necesarios;
- debe funcionar en tema claro y oscuro;
- no agregues una explicación textual permanente. Sólo proponela en el handoff
  si una prueba visual demuestra que la barra sigue siendo ambigua.

La pantalla ya utiliza desplazamiento vertical. No alcanza con conservarlo: el
objetivo incluye comunicar visualmente el desborde.

### Adaptación permitida y prohibida

La composición puede responder a las restricciones reales disponibles después
de padding e insets. Debe usar el contrato de densidad estable ya provisto por
MiGuardia.

Está prohibido:

- leer o modificar `font_scale`;
- leer o modificar zoom, tamaño de visualización o densidad configurada por
  Android;
- activar variantes mediante esos valores del sistema;
- usar `LocalConfiguration` o métricas globales del sistema como atajo para
  inferir esas configuraciones;
- cambiar `MiGuardiaTheme`, `DEFAULT_DISPLAY_DENSITY`, `DEFAULT_FONT_SCALE` o el
  contrato de zoom interno;
- modificar ajustes del teléfono durante pruebas.

Zoom interno 150 % y 200 % conserva su comportamiento explícito: puede requerir
desplazamiento vertical y horizontal, pero todo debe seguir siendo alcanzable y
la barra vertical debe comunicar el desborde correspondiente.

## Conductas que deben permanecer iguales

- El Calendario sigue siendo la pantalla inicial.
- Se mantiene una sola grilla mensual y el gesto horizontal de cambio de mes.
- Consulta no escribe datos.
- `Cargar datos`, `Editar calendario`, `Editar día`, selección simple/múltiple,
  preparación inicial, formularios, borradores y confirmaciones conservan su
  lógica vigente.
- No cambies contenido ni cálculo del próximo evento.
- No cambies carga, error, estados vacíos ni reintentos salvo su disposición.
- No cambies navegación, panel lateral, tema, zoom interno ni accesibilidad
  semántica existente.
- No ocultes información para hacerla entrar.

## Alcance de archivos

Podés modificar únicamente, si el diff real lo justifica:

- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/nextevent/NextEventCard.kt`;
- un componente visual nuevo y acotado dentro de
  `app/src/main/java/com/blackatsystems/miguardia/ui/components/`, si evita
  duplicación y no altera otras superficies;
- las pruebas Compose o instrumentadas directamente relacionadas dentro de
  `app/src/androidTest/java/com/blackatsystems/miguardia/`;
- pruebas JVM puras sólo si introducís lógica visual pura comprobable.

`VisualSystem.kt` puede tocarse únicamente si agregás una opción explícita con
valor predeterminado compatible. No cambies globalmente `HeroCard` de manera que
afecte otras pantallas.

Si necesitás tocar otro archivo, detenete, explicá por qué y pedí autorización a
Joaquin antes de hacerlo.

## Fuera de alcance

No modifiques:

- Room, entidades, DAO, esquemas o migraciones;
- DataStore o datos del perfil;
- dominio, repositorios, motor de horas o motor de próximo evento;
- Gradle, dependencias, manifiesto, permisos, SDK o versión;
- `applicationId`, firma o paquetes;
- notificaciones, clima, fotos, remuneración o informes;
- documentación de planificación existente, salvo que Joaquin lo pida;
- otras pantallas por consistencia estética;
- código de worktrees históricos.

No hagas commit, push, tag, merge, rebase ni limpieza de cambios ajenos.

## Validación obligatoria

Agregá pruebas que demuestren al menos:

1. En un viewport con altura suficiente y zoom 100 %, existen y son visibles la
   tarjeta del evento, la grilla y la acción inferior sin desplazamiento inicial.
2. En un viewport de menor altura existe desborde vertical, el contenido inferior
   es alcanzable y la barra derecha está visible.
3. Cuando no hay desborde, la barra no aparece.
4. La tarjeta conserva todos sus datos en estados: próxima guardia, guardia en
   curso, franco, sin eventos, carga y error.
5. Tema claro y oscuro mantienen contraste y legibilidad.
6. Zoom interno 100 %, 150 % y 200 % conserva acceso a todo el contenido.
7. La grilla mantiene gesto mensual y no pierde abreviatura, horario, estado ni
   selección.
8. `Cargar datos` y `Editar calendario` siguen abriendo exactamente sus flujos
   actuales.

Preferí pruebas con contenedores de tamaño controlado para representar al menos
una ventana amplia y otra baja, sin consultar ni modificar ajustes globales del
dispositivo.

Ejecutá de forma serializada, como mínimo:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
```

Ejecutá además las pruebas instrumentadas específicas si el entorno QA ya está
preparado y Joaquin lo autoriza. Compilar AndroidTest no equivale a QA física.
No instales APK ni uses ADB sobre el Samsung o el paquete principal sin
autorización explícita separada.

Al terminar:

- ejecutá `git diff --check`;
- revisá el diff completo, incluidos archivos nuevos;
- confirmá que no cambiaste archivos fuera de alcance;
- informá por separado pruebas automatizadas, compilación y QA física.

## Definición de terminado

La tarea termina cuando:

- la pantalla principal aprovecha el espacio disponible según el contrato;
- la compactación prioriza tarjeta y separaciones antes que la grilla;
- el desborde vertical es visible y comprensible mediante la barra derecha;
- no se perdió ni ocultó información;
- los recorridos existentes siguen funcionando;
- las pruebas relevantes pasan;
- el diff está limpio de cambios ajenos;
- entregás un handoff y no integrás ni confirmás nada.

## Handoff obligatorio

Respondé en español con:

- **OBJECTIVE:** qué resolviste;
- **CHANGES:** disposición y comportamiento visual implementados;
- **FILES:** archivos modificados;
- **DECISIONS:** decisiones técnicas reversibles tomadas;
- **VALIDATION:** comandos y resultados reales;
- **PHYSICAL QA:** realizada o pendiente, sin confundir compilación con Samsung;
- **RISKS:** límites o incertidumbres;
- **PENDING:** qué queda;
- **NEXT:** qué debe revisar PLANIFICACIÓN/Joaquin.

No declares terminado lo que no hayas ejecutado o comprobado.
