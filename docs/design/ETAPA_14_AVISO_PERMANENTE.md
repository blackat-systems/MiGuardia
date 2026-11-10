# Aviso permanente — diseño y lo que quedó hecho

Fecha: 2026-11-10 · Pedido por Joaquín el 10/11 («notificación constante, persistente, con contador,
personalizable de forma sencilla») para que entre en la 3.0, antes de la aceptación final.

Estado: **hecho en la PC; falta el Samsung y la mirada de Joaquín.** Joaquín aprobó hacerlo y pidió
avanzar con todo lo que se pudiera en la PC; el detalle de abajo es lo que propuse en la sesión y él no
cambió. Si al verlo quiere otra cosa, se ajusta.

## Qué había antes

- Un aviso por jornada o disponibilidad, con cuenta regresiva hasta el inicio y, durante el evento,
  hasta el final.
- Aparecía recién con el primer anticipo (por defecto 12 horas antes).
- Era fijo sólo durante el evento; antes se podía descartar.
- Pantalla bloqueada con tres niveles de privacidad: completo, reducido y oculto.

## Qué agrega

Dos controles nuevos en Avisos, en una tarjeta «Aviso permanente»:

| Control | Por defecto | Qué hace |
|---|---|---|
| Mostrar siempre la próxima jornada | Apagado | El primer evento que todavía no terminó queda siempre a la vista, aunque falte una semana. |
| Mostrar en la pantalla bloqueada | Encendido | Apagado, ningún aviso de jornada aparece con el teléfono bloqueado. |

Con «Mostrar siempre» encendido:

- **Es el mismo aviso de siempre.** Los anticipos no crean otro: sólo deciden cuándo suena o vibra.
- **Queda fijo también antes de empezar**, no sólo durante la jornada.
- **Uno solo.** Es el primer evento sin terminar. Si la persona lo desliza, no se reemplaza por el
  siguiente: vuelve con la jornada que sigue, o desde «Notificaciones ocultas».
- **Cuenta regresiva viva sólo cuando faltan 24 horas o menos.** Más lejos, el aviso muestra la fecha y
  el horario, que se leen mejor que «143:20:05». Durante la jornada cuenta hasta el final, como antes.
- **Respeta lo de siempre:** avisos desactivados, permiso de Android, jornada con sus avisos apagados y
  los tres niveles de privacidad.

Con «Mostrar siempre» apagado, la app se comporta exactamente como antes.

## Cómo funciona sin gastar batería

- La app **no corre todo el tiempo**. El contador lo anima Android.
- La app se despierta en los mismos momentos que ya usaba (anticipos, inicio y fin) más uno nuevo y
  silencioso: 24 horas antes del inicio, para pasar de la fecha a la cuenta regresiva
  (`NotificationBoundaryType.REFRESH`). No pide permisos nuevos ni usa red.

## Límites conocidos

- **Desde Android 14 cualquier aviso fijo se puede deslizar.** La app no lo puede impedir.
- **Las dos opciones nuevas no viajan en las Copias.** Son del teléfono; agregarlas al formato de Copia
  es un cambio de contrato que no se hizo.
- **No se hizo** el tercer control que se había conversado (durante la jornada, contador o sólo horario):
  hoy siempre cuenta hasta el final. Se agrega si Joaquín lo quiere.

## Dónde está

- Reglas: `core/domain/.../notification/ShiftNotificationPlan.kt` (despertar silencioso y ventana de 24 h).
- Qué se muestra: `notifications/NotificationVisibility.kt` (`permanentNotificationEventKeys`) y
  `NotificationReconciler.kt`.
- Aviso: `notifications/ShiftNotificationPresenter.kt` (fijo, contador, pantalla bloqueada).
- Preferencias: `notifications/NotificationPreferencesStore.kt` (`alwaysShowNext`, `showOnLockScreen`).
- Pantalla: `ui/notifications/NotificationScreens.kt`, tarjeta «Aviso permanente».

## Cómo se prueba

- **PC:** `Stage14PermanentNotificationPlanTest` (8) y `Stage14PermanentNotificationRulesTest` (10).
- **Pantalla (Samsung, pendiente):** `Stage14PermanentNotificationScreensTest` (5): los dos interruptores,
  sus textos, letra al 200% y el guardado en un archivo temporal.
- **Con el dedo de Joaquín (pendiente):** ver el aviso con una jornada lejana y con una cercana, el paso a
  cuenta regresiva, deslizarlo, la pantalla bloqueada con el interruptor encendido y apagado, y que un
  anticipo suene sin duplicar el aviso.
- **Sin prueba automática:** que el aviso publicado sea fijo y quede fuera del bloqueo
  (`ShiftNotificationPresenter`). Las pruebas existentes que publican avisos reales pisan preferencias
  de la app QA; se cubre con el dedo o cuando exista el arnés aislado de avisos.
