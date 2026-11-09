# Etapa 13.5 · Contrato común para ayudantes (pulido v2 y selectores)

Base: commit `aea02a1` en `codex/miguardia-3.0`, directorio
`C:\Users\User\Desktop\Claude Projects\MiGuardiaContainer\MiGuardia-3.0`. Escrito por MAIN el 9/11/2026.

## Qué se pide

Aplicar a tus pantallas el **estilo v2 aprobado por Joaquín**, que ya está en el tema y en los
componentes comunes. Agregar el **selector nativo** a tus campos de hora y fecha **sin quitar la
escritura a mano**. No es un rediseño: no se agregan ni se quitan funciones, estados ni pantallas.

Antes de tocar, leé:

- `docs/design/ETAPA_13_5_INVENTARIO.md`: reglas comunes (sección 1), qué se pule en cada pantalla
  (sección 2) y la tabla de los 22 campos (sección 4).
- `app/src/main/java/com/blackatsystems/miguardia/ui/availability/AvailabilityScreens.kt`: la
  pantalla de ejemplo ya terminada. Copiá sus patrones.
- `ui/components/VisualSystem.kt`, `NativePickers.kt` y `AutomaticTimeField.kt`: los componentes
  que tenés que usar.

## Componentes disponibles (no los modifiques: son de MAIN)

**Estructura de pantalla y tarjetas**

- `SurfaceHeader(title, navigationLabel, onNavigation)`: encabezado de pantalla completa, con el
  botón a la derecha. Si el título no entra con letra grande, el botón pasa arriba solo.
- `ScreenHeading(title, supportingText?, eyebrow?)`: título dentro del contenido.
- `SectionCard(title, supportingText?, eyebrow?) { … }`: la tarjeta común, con esquinas
  asimétricas, degradado en oscuro y sombra en claro.
- `Eyebrow(text)`: rótulo violeta chico arriba de un título.
- `HeroCard(title) { … }`, `EmptyState(...)`, `PersistentMessage(message, onDismiss?, onRetry?)`,
  `MonthNavigator(...)`.
- `Modifier.vigiliaScreenBackground()`: fondo de página con el brillo de arriba. Va en la `Column`
  raíz de las pantallas completas, como en `FullAvailabilitySurface`.

**Acciones**

- `PrimaryAction(label, onClick, modifier, enabled, working)`: botón principal con degradado.
  Ocupa todo el ancho; poné uno solo por pantalla.
- `SecondaryAction(label, onClick, modifier, enabled)`: botón secundario.
- `DestructiveAction(label, onClick, modifier, enabled)`: botón destructivo tonal, para usar dentro
  de una pantalla.
- En diálogos, para confirmar algo destructivo:
  `FilledTonalButton(shape = MiGuardiaButtonShape, colors = destructiveButtonColors())`.
- Para cualquier otro `Button`, `OutlinedButton` o `FilledTonalButton` que quede: pasale
  `shape = MiGuardiaButtonShape`.

**Espacios**

- `MiGuardiaSpacing.extraSmall/small/medium/large/extraLarge/huge` = 4/8/12/16/24/32 dp.
- No uses números sueltos como 5, 6, 9, 10, 14 o 20 dp.
- Margen exterior de 16 en pantallas completas; 16 entre bloques; 8 dentro de una tarjeta.

**Selectores**

- `AutomaticTimeField(value, onValueChange, label, modifier, enabled, withPicker = true,
  pickerTestTag = "<tag-del-campo>$PICKER_BUTTON_SUFFIX", pickerFallback?, textStyle?, showHint?)`.
  - Con `withPicker = true` aparece el reloj.
  - `textStyle = MaterialTheme.typography.headlineSmall` muestra las horas grandes, como en
    Disponibilidad.
  - `showHint = false` sirve cuando ponés una sola ayuda debajo de un par de campos.
- `PickableDateField(value, onValueChange, label, format = DateFieldFormat.ISO o
  DateFieldFormat.DAY_MONTH_YEAR, modifier, supportingText, pickerFallback, pickerTestTag,
  enabled, badge?)`: campo de fecha escrito más el botón de calendario.
- Para casos especiales (un campo que no sea `OutlinedTextField` simple, o fecha y hora juntas):
  `MiGuardiaDatePickerDialog(initial, onConfirm, onDismiss)` y
  `MiGuardiaTimePickerDialog(initial, title, onConfirm, onDismiss)`, más
  `PickerTrailingButton(iconRes = R.drawable.ic_pick_date o R.drawable.ic_pick_time, description,
  enabled, testTag, onClick)`.
- Etiquetas de prueba: el botón usa `"<tag del campo>-picker"`; los botones del diálogo,
  `PICKER_CONFIRM_TAG` y `PICKER_CANCEL_TAG`.

## Reglas que no cambian

- **Producto:** minutos exactos, cruce de medianoche, fecha local explícita y revisión antes de
  guardar, en las pantallas donde existe hoy. El selector **nunca guarda ni revisa solo**: sólo
  escribe en el borrador el mismo texto que escribiría la persona, en el mismo formato que acepta
  hoy el campo. «Cancelar» no cambia nada. Donde hoy se guarda sin revisión, sigue igual: no
  agregues pasos.
- **No cambies** etiquetas de prueba (`testTag`), rótulos ni textos que buscan las pruebas
  (`app/src/androidTest`, `app/src/test`). Buscalos con `rg` antes de cambiar un texto. Si un rótulo
  tiene que cambiar por la regla del inventario (por ejemplo «Atrás» → «Volver»), actualizá la
  prueba sabiendo qué cubría. No borres pruebas.
- **Letra nativa de Android** (ADR 0043): no fijes tamaños en `sp` en lugar del tema, no agregues
  zoom ni controles de TalkBack, y todo tiene que seguir entrando con letra al 200%.
  - Si ponés dos campos lado a lado, hacé como `WindowTimes` de Disponibilidad: lado a lado sólo si
    entra (`maxWidth >= 280.dp * fontScale`), y si no, apilados.
  - No pongas etiquetas ni chips dentro del lugar del ícono de un campo: con letra grande le quitan
    el lugar al botón (pasó en Disponibilidad).
- **Colores:** usá siempre colores del tema (`MaterialTheme.colorScheme.*` y
  `MaterialTheme.vigiliaColors.*`). No pongas `Color(0x…)`, salvo los colores de jornada que elige
  la persona. Si dibujás un fondo propio sin `Surface`, poné también el color de texto con
  `LocalContentColor`: el texto heredado puede salir negro (pasó en el Calendario).
- **Sin dependencias nuevas.**
- **Sin decisiones de producto:** el «UUID» visible en Horario real queda **como está**, porque es
  una decisión pendiente de Joaquín.
- **Archivos:** tocá sólo los de tu alcance. Si necesitás algo en un archivo ajeno (componentes
  comunes, tema, `Stage135Selectors`, otra pantalla), no lo cambies: anotalo en tu entrega.
- **No ejecutes Gradle ni `tools/verify.py`, y no uses adb ni el teléfono.** MAIN es el único que
  compila, prueba y usa el dispositivo. Escribí código que compile: revisá imports, tipos y nombres
  exactos leyendo las fuentes. No hagas commits.
- **Finales de línea:** conservá los del archivo. Si escribís con Python, usá `newline=''`.

## Pruebas que tenés que escribir (en `app/src/androidTest`)

En la clase de prueba Compose existente de tu pantalla, o en una nueva dentro de
`com.blackatsystems.miguardia.stage135`, con datos ficticios y sin Room. Envolvé en
`AndroidFontScaleTheme`, como `AvailabilityComposeTest`. Para cada lugar con selector:

1. El selector y la escritura dan el mismo texto.
2. «Cancelar» no cambia nada.
3. Hora: límites 00:00 y 23:59. Si el campo cruza la medianoche, un caso de cruce.
4. Nunca guarda ni revisa solo: el callback de guardar o revisar no se llama.
5. Con letra `EXTRA_LARGE` y una caja de 360×800 dp, el botón del selector se puede tocar y abre el
   diálogo.

## Entrega (respuesta final, en español, breve)

- **ARCHIVOS:** cada archivo cambiado o creado.
- **CAMBIOS:** por pantalla, qué se pulió y qué campos tienen selector, con su `testTag` de botón.
- **PRUEBAS NUEVAS:** nombre completo `clase#método` de cada una, para que MAIN las agregue a
  `Stage135Selectors`.
- **TEXTOS O ETIQUETAS CAMBIADOS:** cuáles, y qué pruebas actualizaste.
- **DUDAS O RIESGOS:** lo que no pudiste resolver, lo que necesita un archivo ajeno y cualquier
  supuesto. Marcá lo INFERIDO.
