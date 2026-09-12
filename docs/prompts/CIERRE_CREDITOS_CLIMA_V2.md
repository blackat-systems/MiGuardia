# Cierre de créditos del clima

Contrato acotado de preparación para publicación, no una nueva función ni una
reimplementación de Clima. Autorización y estado: `docs/STATUS.md`.

## Qué hace y por qué existe

Completa los créditos y el acceso a la licencia de los datos meteorológicos en
las superficies que ya los muestran. Devuelve un candidato comprobable a MAIN,
antes de congelar y firmar la APK. No reabre la auditoría general ni el cliente 28.

## Entrada y rol

- Actuás como especialista, no como MAIN.
- Checkout compartido: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- Base funcional: `2952fc55e2d815b6889ffe8abec665f7693554ed`.
- MAIN entrega el HEAD exacto del checkpoint documental en el mensaje de inicio.
  Debe ser descendiente de esa base y diferir sólo en este contrato, STATUS e
  índice de prompts. Esperado al iniciar: árbol limpio e índice vacío.
- Verificá Puerta 0; pará ante cambios o divergencias sin explicación. Un único
  escritor en el checkout; no abras tareas, ramas ni worktrees.
- Leé AGENTS, STATUS, `docs/LECTURAS_POR_TAREA.md`, `docs/VALIDACION.md`,
  `docs/adr/0033-widget-local-sobre-proyeccion-v2.md` y
  `docs/adr/0038-ubicacion-puntual-y-clima-por-objetivo.md`.
  Room actual es V6; la referencia V5 del contexto histórico de ADR 0033 no es
  una orden de bajar la versión.

## Evidencia y resultado esperado

La comprobación de MAIN observó proveedor y términos en WeatherScreens, sin
enlace explícito a la licencia; resúmenes sin crédito en detalle y avisos; y
crédito del Widget dentro de un elemento truncable de tres líneas. El posible
recorte del Widget es un riesgo estático, no un fallo OEM reproducido. La tarjeta
Hoy no muestra clima: no agregues clima ni créditos donde no corresponde.

Contrastá las condiciones oficiales vigentes antes de editar:
https://open-meteo.com/en/licence y https://creativecommons.org/licenses/by/4.0/.
No confundas licencia de los datos con licencia del código del proveedor ni con
un plan de acceso a su infraestructura.

1. Conservá los créditos existentes y completá proveedor, enlace HTTPS y
   licencia CC BY 4.0; explicá de forma breve la presentación resumida o
   conversión de unidades cuando corresponda, sin atribuir cálculos propios al
   proveedor ni sugerir su respaldo a MiGuardia.
2. Los resúmenes de Clima, detalle, aviso y Widget que realmente muestren datos
   deben ofrecer crédito legible y acceso consciente a la información completa.
   No dupliques el texto a través de fórmulas o motores nuevos.
3. Preservá ADR 0033: el Widget abre Clima mediante el PendingIntent explícito
   existente; allí se ofrecen los enlaces HTTPS. No abras un navegador implícito
   desde RemoteViews. Para avisos reutilizá navegación segura existente; si el
   requisito sólo pudiera cumplirse cambiando un contrato de navegación o
   privacidad, devolvé ese impedimento a MAIN, no inventes otro flujo.
4. El crédito del Widget no debe depender de que sobreviva al recorte del
   pronóstico. Conservá tamaños, acciones, legibilidad y privacidad.
5. Sin datos mostrados, privacidad oculta o beta cerrada no reveles lugar,
   horarios, clima ni contexto laboral. Respetá Bloqueo y puertas de retorno.
   Ningún enlace se abre automáticamente; manejar ausencia de navegador.

## Archivos y límites

Cambios mínimos permitidos bajo `app/src`:

- `ui/weather/WeatherScreens.kt` y `ui/CalendarDayDetail.kt`.
- Presentación de clima en `weather/WeatherRuntime.kt`,
  `notifications/ShiftNotificationPresenter.kt` y
  `widget/WidgetRemoteViewsRenderer.kt`, sólo si el ajuste lo necesita.
- Recursos de esas superficies, incluidos layouts de aviso/Widget y strings.
- Un helper pequeño de créditos/enlaces, sólo si reduce duplicación real.
- Pruebas JVM/Compose/RemoteViews directamente afectadas; actualizar únicamente
  el inventario correspondiente en `tools/qa-groups.json` si agregás una clase
  instrumentada, conforme al runner vigente.

No modificar `core`, Room, fórmulas, ubicación, cachés, periodicidad, red del
proveedor, permisos, dependencias, Gradle, manifiestos, versiones, website,
contratos beta, Copias, Bloqueo ni documentación canónica. No inspeccionar,
copiar, generar ni usar claves privadas: la clave permanente ya existe y su
custodia está cerrada. Sin firma, commit, staging, push, tag o publicación.

## Comprobación y entrega

- Agregá regresiones de créditos/licencia, ausencia de duplicados, privacidad,
  enlaces y retorno cuando se modifiquen esas acciones. Verificá estados sin
  clima y pronósticos largos. No debilites pruebas o puertas para habilitar QA.
- Ejecutá pruebas dirigidas, luego perfiles `local --fresh-tests` y `build`
  de `tools/verify.py`. No reconstrucción global por ritual ni auditoría de toda
  la app. AndroidTest compilado no es instrumentación ejecutada.
- Ejecutá `python tools/qa.py --check-catalog` y `git diff --check`; revisá los
  archivos nuevos. Guardá evidencia sin secretos en un directorio ignorado nuevo.
- Esta entrega especialista es local: no usar ADB, teléfono ni emulador. MAIN
  coordina la QA proporcional ya autorizada sobre los APK finales: Clima/detalle,
  aviso y Widget; legibilidad, navegación, retorno, privacidad, temas y zoom
  interno de las superficies Compose. No leer ni cambiar escala Android.
- Entregá HANDOFF A MAIN: qué cambió, por qué, archivos, HEAD exacto, pruebas
  realmente ejecutadas, hashes de APK, límites y matriz física pendiente.
- Terminado localmente cuando el ajuste mínimo y sus pruebas estén verdes y el
  diff esté disponible sin commit. No declarar publicación ni cumplimiento
  legal general. No iniciar confianza del servidor, firma o la siguiente tarea.
