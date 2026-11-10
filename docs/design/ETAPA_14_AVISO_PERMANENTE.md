# Aviso permanente — diseño y lo que quedó hecho

Fecha: 2026-11-10 · Pedido por Joaquín el 10/11 («notificación constante, persistente, con contador,
personalizable de forma sencilla») para que entre en la 3.0, antes de la aceptación final.

Estado: **hecho; Joaquín lo usó en el Samsung el 10/11** (aviso fijo con contador, deslizarlo, tocarlo
y pantalla bloqueada). Después se agregaron el tercer control (10/11) y el viaje de las tres opciones en
las Copias (etapa 15, 10/11): esas dos partes están probadas en la PC y falta verlas en el Samsung.

## Qué había antes

- Un aviso por jornada o disponibilidad, con cuenta regresiva hasta el inicio y, durante el evento,
  hasta el final.
- Aparecía recién con el primer anticipo (por defecto 12 horas antes).
- Era fijo sólo durante el evento; antes se podía descartar.
- Pantalla bloqueada con tres niveles de privacidad: completo, reducido y oculto.

## Qué agrega

Tres controles nuevos en Avisos, en una tarjeta «Aviso permanente»:

| Control | Por defecto | Qué hace |
|---|---|---|
| Mostrar siempre la próxima jornada | Apagado | El primer evento que todavía no terminó queda siempre a la vista, aunque falte una semana. |
| Mostrar en la pantalla bloqueada | Encendido | Apagado, ningún aviso de jornada aparece con el teléfono bloqueado. |
| Contar hasta el final durante la jornada | Encendido | Apagado, durante la jornada el aviso muestra sólo el horario, sin cuenta regresiva. |

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

## En las Copias (etapa 15, pedido de Joaquín)

- **Las tres opciones viajan en la Copia.** Una copia nueva lleva 20 preferencias: las 17 de siempre más
  `notifications.always_show_next`, `notifications.show_on_lock_screen` y
  `notifications.countdown_while_active`.
- **Una copia anterior (17 preferencias) se sigue restaurando.** Como no trae las tres opciones, al
  restaurarla quedan de fábrica: apagado, encendido, encendido. La vista previa cuenta 20 ajustes porque
  la app completa la lista antes de comparar.
- **Con «Combinar»** y una copia anterior, cada opción que en el teléfono esté distinta de fábrica aparece
  como un conflicto más («Mostrar siempre la próxima jornada tiene valores diferentes.»); «Conservar lo
  actual» la deja como está. Alternativa sin decidir: que «Combinar» no pregunte por lo que la copia no trae.
- **Una clave desconocida, repetida o faltante se rechaza** igual que antes, sin escribir nada.
- **Restauración interrumpida antes de actualizar la app:** el registro de recuperación que dejó la versión
  anterior tiene 17 preferencias; la app nueva lo completa igual y termina la recuperación.
- **Ensayo de actualización desde la versión pública:** la copia de origen sigue teniendo 17. El contrato
  del ensayo acepta, después de actualizar, las 17 intactas más las tres nuevas sólo en su valor de fábrica.

## Dónde está

- Reglas: `core/domain/.../notification/ShiftNotificationPlan.kt` (despertar silencioso y ventana de 24 h).
- Qué se muestra: `notifications/NotificationVisibility.kt` (`permanentNotificationEventKeys`) y
  `NotificationReconciler.kt`.
- Aviso: `notifications/ShiftNotificationPresenter.kt` (fijo, contador, pantalla bloqueada).
- Preferencias: `notifications/NotificationPreferencesStore.kt` (`alwaysShowNext`, `showOnLockScreen`,
  `countdownWhileActive`).
- Copias: `backup/PortablePreferencesGateway.kt` (`completePortablePreferences`) y la verificación de
  `backup/LocalBackupCoordinator.kt`.
- Pantalla: `ui/notifications/NotificationScreens.kt`, tarjeta «Aviso permanente».

## Cómo se prueba

- **PC:** `Stage14PermanentNotificationPlanTest` (8) y `Stage14PermanentNotificationRulesTest` (11).
  Copias: `PortablePreferenceCompletionTest` (4), una prueba de conflictos en `BackupContractTest` y el
  contrato del ensayo de actualización (`Stage10UpgradeContractTest`, `Stage10EmulatorContractTest`).
- **Pantalla (Samsung):** `Stage14PermanentNotificationScreensTest` (6): los tres interruptores, sus
  textos, letra al 200% y el guardado en un archivo temporal. Cinco pasaron el 10/11; la del tercer
  interruptor no corrió todavía.
- **Copias (Samsung, pendiente):** `Stage14PermanentNotificationCopiesTest` (5), en una carpeta temporal
  propia: copia nueva ida y vuelta, copia anterior de 17, clave desconocida y las dos recuperaciones de
  una restauración interrumpida antes de actualizar.
- **Con el dedo de Joaquín (pendiente):** ver el aviso con una jornada lejana y con una cercana, el paso a
  cuenta regresiva, deslizarlo, la pantalla bloqueada con el interruptor encendido y apagado, y que un
  anticipo suene sin duplicar el aviso.
- **Sin prueba automática:** que el aviso publicado sea fijo y quede fuera del bloqueo
  (`ShiftNotificationPresenter`). Las pruebas existentes que publican avisos reales pisan preferencias
  de la app QA; se cubre con el dedo o cuando exista el arnés aislado de avisos.
