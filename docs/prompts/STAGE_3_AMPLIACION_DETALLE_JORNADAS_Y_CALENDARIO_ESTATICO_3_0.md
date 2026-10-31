# Stage 3 — Ampliación de detalle por jornadas y Calendario estático

Fecha: 2026-09-15  
Estado: `AUTORIZADO`; Joaquín fijó el comportamiento y aceptó las recomendaciones
de interacción el 2026-09-15.

## PERFIL DE EJECUCIÓN

- Modelo previsto: `GPT-5.6 Sol`, razonamiento `Medium`.
- Resolver como una única ampliación cohesiva de Stage 3. No crear Stage 4 ni
  separar UI, clima y adaptación en microdependencias.
- Priorizar reutilización: la tarjeta de jornada, Notas, Avisos, clima, selección
  múltiple y calendario plegable ya existen parcialmente.
- Empezar con Puerta 0 y preservar íntegramente el checkout sucio acumulado.

## TASK

Recomponer el detalle de un día alrededor de sus jornadas reales y convertir la
pantalla principal del Calendario en una superficie no desplazable, con acciones
fijas y calendario plegable. Un toque breve abre el detalle; una pulsación larga
nativa de Android inicia selección múltiple.

El detalle debe identificar el día con fecha humana completa, ofrecer un único
acceso `Editar día` y mostrar una tarjeta independiente por cada jornada. Cada
tarjeta reúne identidad, horario, Notas, Avisos y clima correspondiente a esa
jornada. Si el clima no está habilitado o no tiene ubicación utilizable, su
tarjeta debe conducir conscientemente al recorrido vigente de configuración.

## DECISIONES DE PRODUCTO FIJADAS

- Fecha del detalle: `Martes 15 de septiembre de 2026`; no ISO ni fecha corta.
- Orden principal: fecha → `Editar día` → `Jornadas del día` → tarjetas de
  jornadas → demás condiciones o aportes del día.
- Cada jornada es una tarjeta propia. Dos jornadas producen dos tarjetas; nunca
  se mezclan Notas, Avisos, clima, horario real o estado entre ellas.
- Pulsación larga: usar el gesto nativo accesible de Android, aproximadamente el
  umbral del sistema. No exigir una espera literal de dos o tres segundos.
- Pantalla principal: no tiene scroll vertical ni acciones escondidas debajo del
  calendario. Conserva una barra inferior fija y compacta para las acciones
  principales.
- El detalle del día sí puede desplazarse: puede contener varias jornadas y no
  debe comprimirlas para entrar artificialmente en una sola pantalla.
- El plegado sigue siendo explícito. Sólo una restricción física de ventana,
  orientación o zoom puede obligar a mostrar la semana para conservar áreas
  táctiles accesibles; no plegar por seleccionar o navegar.

## CONTEXT

- Stage 1 estabilizó el detalle, Notas con imágenes, horario estipulado/real,
  dirección y edición de jornadas.
- Stage 2 estabilizó situaciones laborales y la tarjeta `Condición del día`.
- Stage 3 implementó selección múltiple, mes/semana, barra inferior fija y
  operaciones masivas; su prioridad de Atrás fue corregida y verificada
  localmente.
- `CalendarDayDetail.kt` ya itera `day.shifts`, conoce el color, objetivo,
  dirección, horario, Notas, Avisos, horario real y `ShiftWeatherBrief`.
- Hoy el detalle usa fecha corta en vista normal, muestra `Condición del día`
  antes de las jornadas y separa cada jornada sólo con divisores.
- `ShiftWeatherBriefCard` existe, pero queda deshabilitada cuando el clima está
  apagado o no hay resumen. Eso contradice el nuevo acceso consciente a la
  configuración.
- `CalendarScreen.kt` mantiene un `ScrollState`, contenido desplazable y una
  barra visual de scroll, aunque las acciones principales ya están fuera del
  contenido. Esta ampliación debe eliminar el scroll de la pantalla principal,
  no sólo ocultar su indicador.

## INPUTS

- Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio: `MiGuardia-3.0/`.
- Rama esperada: `codex/miguardia-3.0`.
- HEAD base del checkout acumulado:
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Leer, en este orden:
  1. `MiGuardiaContainer/AGENTS.md`.
  2. `MiGuardia-3.0/AGENTS.md`.
  3. `docs/STATUS.md`.
  4. Este contrato.
  5. `docs/prompts/STAGE_1_DETALLE_EDICION_Y_COMPOSICION_DEL_DIA_3_0.md`.
  6. `docs/prompts/STAGE_3_SELECCION_MULTIPLE_Y_CALENDARIO_PLEGABLE_3_0.md`.
  7. `docs/prompts/STAGE_3_CORRECCION_PRIORIDAD_ATRAS_3_0.md`.
  8. `docs/HANDOFF_STAGE_3_A_MAIN_3_0.md` y su auditoría vigente.
- Código principal: `CalendarDayDetail.kt`, `CalendarScreen.kt`,
  `CalendarUiState.kt`, `MiGuardiaApp.kt`, `WeatherViewModel.kt` y consumidores
  de Notas/Avisos.

## OUTPUT

### Detalle del día

- Encabezado con fecha humana completa, capitalización natural en español y año
  explícito: `Martes 15 de septiembre de 2026`.
- Debajo, un único botón primario `Editar día` que abre el menú vigente. No
  volver a dispersar altas, correcciones o eliminaciones en el detalle.
- Si existe al menos una jornada, mostrar el título `Jornadas del día` y una
  tarjeta sutil por jornada, ordenadas cronológicamente por inicio y con identidad
  estable aun si comparten horario u objetivo.
- Si no existen jornadas, no mostrar una plantilla vacía de jornada. Las demás
  condiciones del día continúan visibles cuando correspondan.
- Cada tarjeta incluye como mínimo:
  - círculo con el color real de la jornada y descripción accesible;
  - nombre y abreviatura, por ejemplo `Rawson (RAW)`;
  - dirección inmediatamente debajo cuando exista, conservando Maps;
  - horario planificado limpio, por ejemplo `19:00 – 07:00`;
  - estado o situación sólo cuando aporte información y sin competir con el
    horario;
  - acceso a `Notas` asociado al ID exacto de esa jornada;
  - acceso vigente a `Avisos de esta jornada`, conservado como acción secundaria;
  - bloque `Clima durante la jornada` correspondiente a esa jornada;
  - horario real/diferencia y demás información propia de la jornada dentro de
    la misma agrupación visual, no suelta entre tarjetas.
- Con varias jornadas, cada tarjeta conserva sus propios callbacks, etiquetas,
  semántica y datos. No usar ordinal visual como identidad de escritura.
- Después de las jornadas, mostrar `Condición del día`, Retén, carpeta médica,
  vacaciones, extras u otras atribuciones sólo cuando realmente existan. No
  mostrar tarjetas vacías por ritual.

### Clima contextual

- Una jornada elegible con clima habilitado muestra su resumen vigente y abre el
  detalle meteorológico de esa jornada.
- Mientras carga, mostrar estado de carga dentro de la tarjeta sin bloquear el
  resto del detalle.
- Si el clima está apagado, la tarjeta sigue habilitada y explica brevemente que
  puede activarse. Al tocarla abre el recorrido vigente de Clima enfocado en
  habilitación y ubicación; no activa nada ni concede permisos silenciosamente.
- Si falta ubicación, dirección utilizable o consentimiento, la tarjeta conduce
  al paso exacto que falta y permite volver al mismo detalle.
- Si la jornada está fuera de la ventana de pronóstico o no es elegible por una
  razón laboral, mostrar una explicación honesta en vez de una tarjeta rota o un
  pronóstico perteneciente a otra jornada.
- No iniciar solicitudes remotas por recomposición. Reutilizar caché, política de
  actualización, privacidad, consentimiento y créditos existentes.

### Gestos y selección

- Un toque breve sobre una celda en modo normal abre el detalle de ese día.
- Una pulsación larga nativa entra en selección múltiple y selecciona esa fecha.
- El gesto largo no abre también el detalle ni ejecuta una acción masiva.
- Mantener `Seleccionar días` como alternativa accesible en la barra fija; no
  depender exclusivamente del gesto.
- TalkBack debe anunciar claramente toque/acción, pulsación larga y estado de
  selección sin depender sólo de color.

### Pantalla principal no desplazable

- El Calendario principal no usa scroll vertical ni muestra indicador de scroll.
- Encabezado, navegación mensual, grilla y barra inferior viven en un único
  viewport estable; ninguna acción principal queda debajo de la grilla.
- La barra inferior fija conserva:
  - modo normal: acción principal de carga y `Seleccionar días`;
  - modo selección: contador, `Continuar` y `Cancelar`.
- Opciones avanzadas que hoy dependen del contenido desplazable deben moverse a
  una hoja, diálogo o destino ya existente; no comprimirlas debajo de la grilla.
- Mes expandido conserva celdas legibles y áreas táctiles mínimas. Si la ventana
  real —por paisaje, pantalla pequeña o zoom alto— no permite mes completo sin
  scroll ni pérdida de accesibilidad, mostrar semana activa para esa restricción
  y conservar la preferencia del usuario para cuando vuelva a caber.
- No reducir permanentemente tipografía, indicadores, check de completado ni
  altura táctil para simular que todo entra.
- Ayuda/onboarding debe enfocar elementos sin depender de desplazamientos que ya
  no existen.

## RUTA INTERNA DE IMPLEMENTACIÓN

Es una sola dependencia con cuatro fases internas:

1. **Contrato visual y adaptativo:** fijar agrupación por jornada, orden
   condicional y política sin scroll en todos los tamaños/zooms.
2. **Detalle por jornadas:** encapsular cada jornada con sus acciones, clima y
   horario real sin alterar identidad ni dominio.
3. **Calendario estático:** retirar scroll real, reubicar opciones avanzadas y
   asegurar barra fija, plegado y gestos.
4. **Regresión y auditoría:** múltiples jornadas, clima habilitado/deshabilitado,
   tamaños, zoom, orientación, TalkBack, Atrás y selección.

## SCOPE

- Composición y semántica del detalle de día y tarjetas de jornada.
- Entrada contextual al clima/configuración existente.
- Layout principal del Calendario, barra fija y adaptación mes/semana.
- Pruebas JVM, Compose e instrumentadas directamente afectadas.
- Documentación, catálogo QA, auditoría Stage 3 y `docs/STATUS.md`.

## DEPENDENCIES

- Stage 1 y Stage 2 aceptados.
- Stage 3 y su corrección de Atrás preservados como candidato local.
- No depende de migración Room, Samsung, firma, website ni servicios remotos.

## DO NOT

- No reemplazar Compose, navegación, motor de clima ni repositorios existentes.
- No crear una tabla Room, migración o formato de Copias para estado puramente
  visual.
- No mezclar datos o acciones entre jornadas del mismo día.
- No eliminar Notas, imágenes, Avisos, dirección/Maps, horario real ni situaciones
  laborales para simplificar la tarjeta.
- No volver a mostrar fechas ISO, códigos hexadecimales ni bloques vacíos.
- No exigir una pulsación literal de dos o tres segundos.
- No depender únicamente de color, gesto o animación.
- No esconder acciones principales en el contenido ni mantener un scroll
  invisible.
- No achicar controles por debajo del mínimo accesible para hacer entrar el mes.
- No pedir ubicación ni habilitar clima sin explicación y acción del usuario.
- No cambiar versión, `applicationId`, firma, distribución, website ni app
  pública.
- No usar Samsung, ADB, emulador, commit, push, tag, firma, AAB ni publicación.

## VALIDATION

- Día sin jornadas, con una y con al menos dos jornadas diferentes.
- Dos jornadas del mismo objetivo y dos con el mismo horario: acciones y clima
  siguen apuntando al ID correcto.
- Fecha humana exacta; orden fecha → editar → jornadas → otras condiciones.
- Notas, imágenes, Avisos, Maps, horario real y situación continúan accesibles.
- Clima habilitado con resumen, cargando, error/caché, deshabilitado, sin
  ubicación, fuera de ventana y no elegible.
- Tocar clima deshabilitado abre configuración consciente y Atrás retorna al
  mismo detalle sin activar ni escribir por sí solo.
- Toque breve abre detalle; gesto largo nativo entra sólo en selección; alternativa
  visible conserva el mismo estado.
- Pantalla principal sin scroll en teléfono pequeño/grande, retrato/paisaje y
  zoom 100/150/200; sin contenido tapado por barras del sistema.
- Mes completo cuando cabe y semana activa cuando la restricción física lo exige,
  preservando preferencia y selección.
- Regresión completa del árbitro de Atrás, carga manual, feriados y operaciones
  masivas.
- Pruebas focalizadas primero; luego perfiles locales completos una sola vez con
  inputs estables.
- Compilar instrumentación y preparar matriz física, sin ejecutarla hasta una
  autorización específica posterior.
- `python tools/docs_check.py`, `python tools/qa.py --check-catalog` y
  `git diff --check` verdes.

## DONE WHEN

- El detalle se comprende de arriba hacia abajo y cada jornada forma una unidad
  visual y funcional inequívoca.
- El clima apagado o incompleto ofrece una salida útil, consciente y privada.
- Toque y pulsación larga nunca compiten.
- La pantalla principal no necesita ni permite scroll vertical y sus acciones
  permanecen visibles.
- La accesibilidad no se sacrifica para hacer entrar el calendario.
- Stage 1/2 y la corrección de Atrás no retroceden.
- MAIN recibe diff, evidencia, riesgos, matriz física pendiente y handoff
  actualizado; no se infiere aceptación, distribución ni publicación.

