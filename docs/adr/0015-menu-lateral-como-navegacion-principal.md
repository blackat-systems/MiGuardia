# ADR 0015: menú lateral como navegación principal

- Estado: aceptada para implementación
- Fecha: 2026-08-18

## Contexto

MiGuardia usa hoy una barra inferior permanente con Calendario, Resumen y Configuración. Joa decidió liberar esa zona y concentrar la navegación en un botón de tres líneas ubicado arriba a la izquierda. El Calendario ya usa un gesto horizontal para cambiar de mes, por lo que un panel que también se abra arrastrando desde el borde produciría una competencia ambigua.

La primera implementación dejó el panel con sólo Calendario, Resumen y Configuración. Joa la evaluó visualmente como demasiado desnuda y decidió que el panel sea la jerarquía de acceso completa. Para evitar dos jerarquías, Configuración deja de existir como pantalla contenedora: sus apartados implementados se mueven al panel sin duplicar formularios ni datos.

## Decisión

- Eliminar la barra inferior.
- La barra superior muestra un botón hamburguesa con descripción accesible `Abrir menú`.
- El panel lateral es desplazable, tiene encabezado Vigilia y agrupa:
  - destinos principales: `Calendario` y `Resumen`;
  - `Tu trabajo`: `Perfil laboral`, `Objetivos y horarios`, `Feriados` y `Vacaciones`;
  - `Avisos y contexto`: `Notificaciones` y `Clima`;
  - `Aplicación`: `Apariencia`.
- Configuración deja de existir como destino y pantalla contenedora.
- Cada acceso directo reutiliza su superficie vigente. Apariencia concentra tema y zoom interno.
- No se muestran placeholders de apartados futuros.
- El panel sólo se abre mediante el botón. Se deshabilita el gesto desde el borde para preservar el cambio horizontal de mes.
- Elegir un destino cierra el panel y navega una sola vez.
- Abrir una fecha desde notificación fuerza Calendario y cierra cualquier panel abierto.
- Atrás cierra primero el panel. Si no hay superficie superior, desde Resumen o Apariencia vuelve al Calendario; desde Calendario conserva el comportamiento normal de Android.
- No se introduce una biblioteca de navegación nueva para este estado raíz acotado.

## Consecuencias

- El Calendario gana altura útil y deja de competir con una barra inferior.
- El botón principal de edición y el contenido deben respetar únicamente barras del sistema y la barra superior.
- Onboarding debe enseñar el menú lateral agrupado y no puede referirse a la navegación inferior ni a una pantalla contenedora Configuración.
- Cambia la composición raíz, por lo que corresponde regresión amplia de navegación, apariencia, recreación y superficies bloqueantes.
- No cambian Room, DataStore, permisos, manifiesto, red ni datos históricos.

## Alternativas descartadas

### Conservar sólo tres destinos

Se descarta después de la evaluación visual de Joa porque produce un panel lateral desnudo y esconde innecesariamente los accesos que el usuario busca.

### Conservar Configuración además de sus accesos

Se descarta porque duplicaría la jerarquía y generaría dos lugares que mantener.

### Insertar tema y zoom como controles en línea dentro del panel

Se descarta para mantener el panel como navegación clara y desplazable. `Apariencia` los reúne en una superficie propia.

### Permitir arrastre desde el borde

Se descarta porque compite con el gesto horizontal del Calendario y puede cambiar de mes o abrir el panel de forma accidental.

### Mantener también la barra inferior

Se descarta porque duplica destinos y contradice la decisión visual de Joa.
