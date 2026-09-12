# ADR 0003: proyección temporal y calendario mensual

- Estado: aceptada
- Fecha: 2026-08-13
- Autoridad: MAIN, después de revisar la entrega de CALENDARIO MENSUAL

## Contexto

MiGuardia necesita representar un mes completo a partir de tres fuentes locales: guardias que comienzan en el intervalo, estados diarios explícitos y carpetas médicas que lo intersectan. También debe mostrar el ciclo temporal de una guardia sin exigir que el usuario confirme manualmente cada guardia pasada ni escribir en Room únicamente porque avanzó el reloj.

El calendario debe reaccionar a cambios persistidos, conservar el mes seleccionado al recrear la actividad y permanecer comprobable con instantes deterministas. El motor definitivo de próximo evento todavía no forma parte de este incremento.

## Decisión

- La proyección de un `YearMonth` será lógica pura de `core:domain`, independiente de Android, Compose y Room.
- Una guardia `PLANNED` se proyectará como `UPCOMING`, `IN_PROGRESS` o `COMPLETED` comparando un `Instant` recibido con `startAt` y `endAt`.
- `CANCELLED` y `ABSENT` seguirán siendo estados persistidos explícitos y tendrán prioridad sobre la proyección temporal.
- `COMPLETED` no se agregará al esquema ni a `ShiftStatus`: una guardia retrocargada cuyo fin ya pasó aparecerá completada en su primera proyección, sin escritura redundante.
- Las guardias se ubicarán solo en `localStartDate`, incluso si cruzan medianoche, y se conservarán todas las guardias de una misma fecha.
- Un día sin guardia, estado explícito ni carpeta médica será indefinido implícito; seguirá diferenciándose de `UNDEFINED` persistido.
- La pantalla combinará los tres `Flow` de repositorio correspondientes al mes visible y cancelará la observación anterior al cambiar de mes.
- `CalendarViewModel` mantendrá estado inmutable y conservará el mes mediante `SavedStateHandle`.
- La actualización temporal se programará para el próximo inicio, fin o medianoche relevante; no se realizará sondeo frecuente.
- Una instancia de `LocalDataStore` propiedad de `MiGuardiaApplication` compondrá manualmente los repositorios. No se incorpora Hilt.
- La tarjeta de próxima guardia permanecerá neutral hasta que exista el motor único de próximo evento; no inferirá resultados incompletos desde el mes visible.
- La celda de una guardia reservará líneas separadas para abreviatura histórica completa, horario exacto completo y estado temporal. La franja del color histórico tendrá presencia visible suficiente para reconocer la combinación sin depender exclusivamente del color.
- Una fecha sin prioridad visual de Vacaciones se presentará con fondo verde de completada solamente cuando tenga al menos una guardia y todas las guardias de esa fecha estén proyectadas como `COMPLETED`.
- El zoom interno 150 % o 200 % ampliará realmente las celdas y habilitará desplazamiento horizontal cuando siete columnas ya no entren. Abreviatura histórica, horario completo y marcadores ajustarán su texto al mayor tamaño que quepa sin elipsis.

## Consecuencias

- Cargar guardias históricas produce inmediatamente el estado completado esperado sin migrar datos ni pedir confirmaciones repetitivas.
- La misma regla temporal puede reutilizarse en detalles, resúmenes y el futuro motor de próximo evento.
- Las pruebas controlan el instante y no dependen de la hora del equipo.
- El calendario se actualiza con Room y con límites temporales relevantes sin consumo permanente por temporizadores cortos.
- El esquema Room continúa en versión 1.
- Alta, edición, selección múltiple y advertencias de descanso permanecen para incrementos posteriores.
