# ADR 0009: motor reactivo de próximo evento

- Estado: aceptada e integrada por MAIN
- Fecha: 2026-08-15
- Autoridad: prompt especializado `MOTOR_DE_PROXIMO_EVENTO`

## Contexto

El Calendario observa solamente el mes visible y no puede determinar de manera completa una guardia nocturna iniciada ayer, una guardia de un mes futuro ni el próximo franco explícito. La aplicación necesita una única proyección reutilizable por la pantalla actual y por futuros consumidores, sin adelantar notificaciones o widgets.

## Decisión

- `core:domain` publica una proyección pura que recibe instante, zona, guardias, estados diarios y vacaciones. Devuelve listas ordenadas de guardias en curso y próximas simultáneas, el próximo `DAY_OFF`, el evento principal y una duración no negativa.
- Las guardias candidatas son únicamente `PLANNED`, con fin posterior al instante y fecha inicial fuera de Vacaciones. `CANCELLED` y `ABSENT` no se anuncian y ningún cálculo modifica Room.
- El orden estable es inicio, fin y UUID. El inicio es inclusivo y el fin exclusivo.
- `ShiftRepository`, `ExplicitDayStatusRepository` y `VacationRepository` incorporan observaciones generales hacia el futuro. Sus consultas se implementan sobre columnas existentes; no hay entidades, índices, migraciones ni cambios de esquema.
- La aplicación combina los tres `Flow` en `NextEventObserver`. Cada emisión de Room cancela la espera temporal anterior y recalcula la proyección.
- La actualización temporal espera el próximo inicio, fin, minuto relevante o medianoche local. No existe un bucle por segundo.
- `NextEventViewModel` expone carga, contenido y error recuperable, conserva el último resultado válido y permite reintentar. Su flujo usa `SharingStarted.WhileSubscribed`, por lo que deja de sostener esperas cuando la interfaz no lo observa.
- La tarjeta Compose recibe estado y eventos. No consulta Room, el mes visible ni el reloj y mantiene el Calendario utilizable ante un error del motor.

## Límites preservados

- Room continúa en versión 4 con 11 entidades y esquemas v1-v4 inmutables.
- No se agregan dependencias, permisos, manifiestos, red, alarmas, workers, receivers, servicios, widgets, clima ni preferencias.
- La tarjeta no expone notas, descripciones privadas, contenido médico ni fotos.

## Consecuencias

- Calendario, futuras notificaciones y futuros widgets pueden consumir la misma regla de dominio sin repetir prioridades.
- Cambiar el mes visible no altera el evento proyectado.
- Los límites temporales son deterministas con `Clock`, zona y espera inyectables.
- Consultar guardias por fin posterior sin un índice nuevo prioriza conservar el esquema publicado; si el volumen real demostrara un problema, MAIN deberá evaluar una migración separada y medida.
