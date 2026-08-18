# ADR 0015: menú lateral como navegación principal

- Estado: aceptada para implementación
- Fecha: 2026-08-18

## Contexto

MiGuardia usa hoy una barra inferior permanente con Calendario, Resumen y Configuración. Joa decidió liberar esa zona y concentrar la navegación en un botón de tres líneas ubicado arriba a la izquierda. El Calendario ya usa un gesto horizontal para cambiar de mes, por lo que un panel que también se abra arrastrando desde el borde produciría una competencia ambigua.

Configuración ya agrupa Perfil laboral, Objetivos y horarios, Feriados, Vacaciones, Notificaciones, Clima, Apariencia y las demás preferencias vigentes. Duplicar esos apartados dentro del panel lateral crearía dos jerarquías y dos lugares que mantener.

## Decisión

- Eliminar la barra inferior.
- La barra superior muestra un botón hamburguesa con descripción accesible `Abrir menú`.
- El panel lateral contiene exactamente `Calendario`, `Resumen` y `Configuración`, con destino actual visible.
- `Configuración` abre su pantalla agrupada; el panel no despliega sus opciones internas.
- El panel sólo se abre mediante el botón. Se deshabilita el gesto desde el borde para preservar el cambio horizontal de mes.
- Elegir un destino cierra el panel y navega una sola vez.
- Abrir una fecha desde notificación fuerza Calendario y cierra cualquier panel abierto.
- Atrás cierra primero el panel. Si no hay superficie superior, desde Resumen o Configuración vuelve al Calendario; desde Calendario conserva el comportamiento normal de Android.
- No se introduce una biblioteca de navegación nueva para tres destinos y un estado raíz ya acotado.

## Consecuencias

- El Calendario gana altura útil y deja de competir con una barra inferior.
- El botón principal de edición y el contenido deben respetar únicamente barras del sistema y la barra superior.
- Onboarding debe enseñar el menú lateral y no puede referirse a la navegación inferior histórica.
- Cambia la composición raíz, por lo que corresponde regresión amplia de navegación, apariencia, recreación y superficies bloqueantes.
- No cambian Room, DataStore, permisos, manifiesto, red ni datos históricos.

## Alternativas descartadas

### Desplegar todos los ajustes dentro del panel

Se descarta porque duplica la pantalla Configuración y hace demasiado largo un control de navegación principal.

### Permitir arrastre desde el borde

Se descarta porque compite con el gesto horizontal del Calendario y puede cambiar de mes o abrir el panel de forma accidental.

### Mantener también la barra inferior

Se descarta porque duplica destinos y contradice la decisión visual de Joa.
