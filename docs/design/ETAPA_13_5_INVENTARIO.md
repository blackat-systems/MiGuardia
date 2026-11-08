# Etapa 13.5 · Inventario y propuesta (8/11/2026)

Estado: **APROBADA por Joaquín el 8/11/2026**. Sólo Disponibilidad tiene aplicado el ejemplo. Base: `ac2b6c0` (rama `codex/miguardia-3.0`, igual a `origin`).

Fuente: relevamiento del código del 8/11, hecho por dos ayudantes de lectura y revisado por Claude.
Las capturas del estado actual **todavía no existen**. No se pueden sacar en la PC porque no hay
emulador instalado y las pruebas de pantalla corren en Android. Se sacan en el Samsung cuando
Joaquín lo autorice; el plan está en la sección 5.

## 1. Reglas comunes del pulido (mismo Vigilia, parejo)

Hoy cada pantalla resuelve espacios, títulos y botones a su manera. Los componentes comunes de
`VisualSystem.kt` existen, pero casi no se usan: `MiGuardiaSpacing` aparece 0 veces fuera de su
archivo. Propuesta:

1. **Espacios.**
   - Margen exterior de 16 en las pantallas completas, en lugar de la mezcla actual de 16, 20 y 24.
   - Separación de 16 entre bloques y de 8 dentro de una tarjeta.
   - Siempre con `MiGuardiaSpacing`, sin números sueltos como 5, 6, 9, 10 o 14.
2. **Encabezado.**
   - Todas las pantallas completas usan el encabezado común: título a la izquierda y botón a la
     derecha. Hoy Repetir jornadas y Editar jornada ponen el botón a la izquierda, y
     Actualizaciones lo pone arriba del título.
   - Botón «Cerrar» cuando sale de la función y «Volver» cuando retrocede un paso. Hoy Horas usa
     «Atrás».
3. **Un solo título por pantalla.** Se quita el título repetido en el editor de Vacaciones y en Notas.
   Los títulos de sección usan el tamaño de tarjeta, sin títulos gigantes sueltos dentro del contenido.
4. **Tarjetas.** Una sola tarjeta (`SectionCard`) con su borde. Las tarjetas sueltas sin borde del
   detalle del día, Vacaciones, Feriados, Fotos y Ayuda pasan a tener el mismo color y el mismo borde.
5. **Botones.**
   - Acción principal: ancho completo, con «Guardando…» mientras trabaja.
   - Secundaria: con contorno.
   - Destructiva: hoy hay tres estilos. Se dejan dos:
     - en un diálogo de confirmación, botón rojo relleno, como Disponibilidad;
     - en una pantalla, texto rojo.
   - «Descartar carga» del Calendario y «Eliminar» de Horas hoy no son rojos.
6. **Textos de ayuda** en tono secundario, para que los datos se lean primero. Mismo texto, sin
   recortes ni puntos suspensivos en información importante.
7. **Colores, tema claro.** En claro, las tarjetas tienen el mismo color que el fondo (#F5F4F8) y
   sólo las separa un borde casi invisible. Propuesta: tarjetas blancas sobre el fondo gris claro,
   con borde suave. El tema oscuro queda igual porque ya separa bien.
   - Es un cambio global de un solo color. Se aplica junto con el resto, no en el ejemplo.
8. **Navegador de meses.**
   - Resumen pasa a usar el mismo navegador que Fotos y Vacaciones.
   - El Calendario conserva el suyo, porque es parte de la grilla con reglas propias de mes
     completo y letra (decisiones 0042/0043). Sólo se ajustan su tamaño de letra fijo y su tarjeta
     sin borde.

No cambia: el diseño Vigilia, el oscuro por defecto, la letra de Android (sin zoom interno), TalkBack
sin controles extra, las funciones, los estados ni las pantallas.

## 2. Qué se pule en cada pantalla

| Pantalla | Qué se pule |
|---|---|
| Calendario (barra, tarjeta de hoy, grilla, barra de selección) | Letra fija en 16/14 → estilos del tema; tarjeta del mes sin borde; diálogos con «Descartar carga» en rojo. La grilla conserva su recorte accesible de celdas (ya expuesto a TalkBack). |
| Detalle del día y «Otras opciones» | Título de sección más chico que el de la fecha; 5 tarjetas sueltas → tarjeta común; márgenes 24/32 → 16. |
| Carga manual | Títulos de paso parejos; caja de revisión → tarjeta común; error en rojo suelto → mensaje persistente. |
| Editar jornada / Jornadas del día | Encabezado común (botón a la derecha); espacios. |
| Horario real / Tipos de horas extra | Título doble; espacios. Ver hallazgo «UUID» en la sección 4. |
| Repetir jornadas | Encabezado común; títulos de contenido; espacios. |
| Horas y extras | «Atrás» → «Volver»; «Eliminar» en rojo; espacios. |
| Disponibilidad | **Ejemplo aplicado** (sección 3). |
| Mi forma de trabajar (y primer arranque) | Ya es la más pareja. Título «Listo» y tarjetas de rubro. |
| Feriados, Notas, Vacaciones, Fotos | Tarjetas comunes; título único; espacios; confirmación destructiva roja. |
| Resumen, Informes | Navegador de meses común; vacío y error con los componentes comunes; encabezado común en Informes. |
| Copias y restauración (y recuperación al arrancar) | Encabezado común; margen 20 → 16. |
| Bloqueo (pantalla y ajustes), Apariencia, Ayuda, Ayuda inicial y recorrido, Actualizaciones | Títulos y espacios parejos; tarjetas de Ayuda con borde; botón de Actualizaciones al encabezado. |
| Avisos, Clima, Widget (pantallas y configuración) | Sólo espacios, márgenes y fondo de diálogo parejos. Sus funciones son de la etapa 14 y no se tocan. |

Las pruebas que buscan un rótulo que cambie («Atrás», por ejemplo) se actualizan sabiendo qué
cubrían; no se borran.

## 3. Ejemplo aplicado: Disponibilidad

Archivos: `ui/availability/AvailabilityScreens.kt`, `ui/components/NativePickers.kt` (nuevo),
`ui/components/AutomaticTimeField.kt` y dos íconos propios (`res/drawable/ic_pick_time.xml`,
`ic_pick_date.xml`). No hay dependencias nuevas.

- **Selector de hora.**
  - Tiene un botón de reloj dentro de «Inicio» y «Final», que abre el selector de Android con
    esfera o teclado numérico, en 24 h.
  - Arranca en la hora escrita; si el campo está vacío, en la hora actual.
  - «Aceptar» escribe `HH:mm`, el mismo texto que escribir `0830`. «Cancelar» no cambia nada.
  - La ayuda del campo dice «Escribí 4 números o tocá el reloj».
- **Selector de fecha.**
  - Tiene un botón de calendario dentro de «Vigente desde» y «Fecha final».
  - Arranca en la fecha escrita; si no hay ninguna válida, en la fecha dueña o en hoy.
  - «Aceptar» escribe `AAAA-MM-DD` en el campo, igual que escribirla. El cálculo pasa por UTC, así
    que el día no se corre con la zona horaria.
- **El selector nunca guarda.** Sólo cambia el borrador; la revisión y el guardado siguen iguales.
- **Pulido.**
  - Los campos de la ventana quedan dentro de su tarjeta. Antes había una tarjeta vacía con una
    línea divisoria.
  - Los botones principales pasan al botón común, con el mismo texto y la misma conducta.
  - Las explicaciones quedan en tono secundario.
  - Los espacios usan los valores comunes.
  - Los textos que buscan las pruebas no cambiaron.

**Matriz de conservación (Disponibilidad).** Conservado: las 6 superficies (resumen, configurar,
revisar configuración, ventana, revisar ventana, eliminar), descartar cambios, Atrás en cada una,
error con «Reintentar», carga y vacío, todas las etiquetas de prueba y los textos. Sin estados
nuevos. Lo único agregado son los botones de selector.

**Pruebas.**
- PC: `NativePickersTest` cubre:
  - el selector y la escritura dan el mismo texto;
  - 00:00 y 23:59;
  - la fecha queda igual en 4 zonas horarias;
  - las fechas inválidas se rechazan.
- Teléfono: en `AvailabilityComposeTest` hay 3 pruebas nuevas:
  - selector igual a escritura;
  - cancelar no cambia nada;
  - cruce de medianoche;
  - nunca revisa ni guarda solo.

  **Samsung (8/11): 12/12 ✅**, las 9 que ya existían más las 3 nuevas.

**Corregido gracias al Samsung (8/11).**
- El selector de hora se caía al abrirse: guardaba el modo (reloj o teclado) de una forma que
  Android no puede conservar. Ahora guarda un sí/no.
- El calendario del selector empezaba la semana en domingo, porque el teléfono está en «es-US».
  Ahora usa español de Argentina, igual que el resto de la app: semana de lunes a domingo, como el
  Calendario.
- **Pendiente:** el selector de Vacaciones, que ya existía, tiene el mismo problema de idioma. Se
  corrige al pulir Vacaciones.

## 4. Dónde entra el selector (22 campos)

| # | Pantalla | Campo | Formato hoy | Selector |
|---|---|---|---|---|
| 1 | Disponibilidad · Configurar | Vigente desde | AAAA-MM-DD | Calendario ✅ ejemplo |
| 2–4 | Disponibilidad · Ventana | Inicio, Fecha final, Final | hora / AAAA-MM-DD | Reloj y calendario ✅ ejemplo |
| 5–6 | Calendario · varios días · «Agregar disponibilidad» | Desde (HH:mm), Hasta (HH:mm) | texto libre sin dos puntos automáticos | Reloj. Se propone el mismo campo automático de hora con el mismo rótulo, que también agrega los dos puntos. |
| 7 | Detalle del día · Cancelación | Fecha y hora del aviso | DD/MM/AAAA HH:MM juntas | Calendario y reloj sobre el mismo campo. Se guarda sin revisión, como hoy. |
| 8–9 | Detalle del día · Carpeta médica | Desde, Hasta | DD/MM/AAAA | Calendario |
| 10–11 | Horas · Configurar meta | Fecha de anclaje, Fecha | AAAA-MM-DD | Calendario |
| 12–14 | Horas · Trabajo extra | Inicio, Fecha final, Final | hora / AAAA-MM-DD | Reloj y calendario |
| 15–17 | Horario real (y fragmentos) | Fecha y Hora de inicio y final | AAAA-MM-DD / hora | Calendario y reloj |
| 18–19 | Repetir jornadas | Desde, Hasta | DD/MM/AAAA (barras automáticas) | Calendario |
| 20–21 | Mi forma de trabajar · horario / Agregar otro horario | Hora de inicio y de finalización | hora | Reloj |
| 22 | Mi forma de trabajar · franja nocturna | Inicio y Final nocturno | hora | Reloj |

- **Feriados ya no se escriben**: se eligen en el Calendario, así que no necesitan selector.
- **Vacaciones** ya usa el calendario de Android (con botón y sin escritura). Queda como está:
  agregarle escritura sería una función nueva.
- **Fuera del selector**, porque son cantidades y no horas del día: minutos de meta y de período,
  cantidad de días o semanas y minutos de anticipación de avisos.
- **Hallazgo a decidir:** Horario real muestra «UUID: …», un identificador técnico interno
  (`V2ShiftActualScreens.kt:386` y `:768`). Propuesta: ocultarlo de la vista. Joaquín decide.

## 5. Capturas antes/después

**Hechas para Disponibilidad (8/11, Samsung SM-S938B):**
- 20 capturas de antes (base `ac2b6c0`, armada con `git archive` y la prueba agregada) y 28 de
  después, que suman 8 con los selectores abiertos.
- Atlas local en `.local-evidence/claude-20261108-13-5/capturas/atlas.html`, fuera de Git.
- Lo que se ve en las capturas: en claro, la tarjeta tiene el mismo color que el fondo, como dice
  la regla 7; en oscuro se distingue. Con letra al 200% no hay textos cortados y la pantalla se
  desplaza.
- Las PNG quedan en la caché externa de la app QA (`Android/data/…qa/cache/stage13-5-captures`),
  fuera de sus datos.

Plan para las demás pantallas:

- Prueba nueva `stage135/Stage135AvailabilityCaptureTest`: usa datos ficticios, sin Room ni datos
  QA, y guarda PNG en `cache/stage13-5-captures/<antes|despues>/` de la app de prueba.
- Combinaciones: claro y oscuro, letra normal y muy grande (contexto de prueba, sin tocar el
  ajuste del teléfono), cada superficie y los selectores abiertos.
- «Antes» se corre con la versión base y la prueba agregada; «después», con la versión actual.
- Las demás pantallas siguen el mismo patrón a medida que se pulen.
- La revisión de ventana necesita datos que arma el dominio; se captura en el recorrido real.
- Requiere autorización de Joaquín para instalar en el Samsung y correr. Hace falta la foto de
  datos QA antes y después.
