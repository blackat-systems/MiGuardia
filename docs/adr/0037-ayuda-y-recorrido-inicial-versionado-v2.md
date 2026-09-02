# ADR 0037: Ayuda y recorrido inicial versionado para V2

- Estado: aceptada para implementación
- Fecha: 2026-09-02
- Autoridad: Joaquin y MAIN 2.0

## Contexto

MiGuardia V2 ya posee una experiencia funcional estable: selección de uno de
cuatro rubros, configuración laboral, Calendario, Horas, Resumen, próximo
evento, avisos, Widget, Informes, Copias y Bloqueo de acceso. La aplicación
todavía no ofrece una Ayuda general ni un recorrido que explique cómo se
relacionan esas capacidades.

ADR 0014 diseñó un onboarding para MiGuardia 1.0. Su razón principal continúa
siendo válida: la marca de haber visto una guía es una preferencia local simple
y no debe mezclarse con datos laborales Room. Su secuencia y vocabulario, en
cambio, dependían de Perfil, Objetivos, horarios combinados, `Cargar datos`,
`Guardia/Francos`, navegación inferior y una pantalla Configuración que ya no
existen en la experiencia V2.

La planificación vigente exige que la primera decisión visible de una
instalación nueva siga siendo el rubro y que el primer lugar, tipo y horario se
creen mediante `WorkSetup`. Copias y Bloqueo también fijaron dos prioridades de
arranque: una recuperación pendiente se resuelve antes de iniciar runtimes y,
si el bloqueo está activo, la autenticación ocurre antes de componer contenido
laboral.

## Decisión

### Orden de arranque

La secuencia de prioridad es:

1. recuperación pendiente de Copias;
2. Bloqueo de acceso, si está habilitado;
3. lectura del estado laboral;
4. selector de rubro para `FreshInstall`;
5. Calendario vacío y creación real del primer lugar, tipo y horario para
   `V2NeedsFirstSet`;
6. pantalla real `WorkSetupSurface.COMPLETION` y cualquier alta adicional que
   la persona elija;
7. guía inicial sólo después de volver conscientemente al Calendario, cuando el
   estado es `V2Ready`, `WorkSetupSurface` es `NONE` y la versión está
   pendiente;
8. Calendario normal.

La guía no se antepone al selector ni duplica la primera configuración. Un
usuario ya configurado que recibe esta función por primera vez puede verla una
vez. Si existe un destino pendiente procedente de una notificación o Widget,
se conserva únicamente en memoria hasta completar u omitir la guía y después
se revalida y consume una sola vez. Observar `V2Ready` no puede cerrar ni tapar
`COMPLETION`, `ADDITIONAL_TEMPLATE`, `ADDITIONAL_PLACE` u otra superficie de
configuración todavía abierta.

### Guía breve y contextual

La guía posee dos partes dentro de una misma versión:

- tres pasos breves sobre organización del trabajo, lectura de horas/eventos y
  control local de los datos;
- un recorrido contextual acotado sobre el menú, la tarjeta de hoy, el mes y
  la grilla, el detalle del día, la carga de jornadas, Resumen y Ayuda.

Los focos se asocian a controles y semántica reales, no a coordenadas fijas.
Un control no disponible se explica o se omite de forma determinista. El
recorrido no crea lugares, horarios, jornadas, extras, disponibilidades,
archivos, widgets, avisos ni permisos. Tampoco usa datos ficticios en
producción.

En la primera ejecución puede omitirse conscientemente. Completar u omitir
abre el Calendario en consulta. El paso visible puede sobrevivir a recreación,
pero no se guarda como progreso de negocio.

### Ayuda como destino permanente

`Ayuda` aparece una sola vez dentro del grupo `Aplicación` del menú lateral.
Es un destino real que explica por temas las capacidades V2 ya implementadas y
ofrece `Repetir recorrido inicial`.

La repetición abre una sesión separada: no vuelve a mostrar la acción de omitir,
no borra ni reduce la versión completada y, al cerrar o finalizar, vuelve a
Ayuda.

No se incorporan búsqueda remota, chat de soporte, reporte automático,
adjuntos, diagnósticos, analítica ni enlaces comerciales.

### Persistencia local y versionada

Se conserva la decisión técnica de ADR 0014:

- DataStore Preferences exclusivo `onboarding.preferences_pb`;
- clave entera `completed_version`;
- primera versión `1`;
- valor ausente o menor que la versión actual significa pendiente;
- completar u omitir guarda atómicamente el máximo entre el valor existente y
  la versión actual;
- una versión futura nunca se degrada;
- error de lectura muestra un estado seguro con reintento;
- error de escritura conserva el paso y permite volver a intentar;
- Compose no escribe directamente en DataStore ni Room.

La marca es una preferencia de experiencia de este dispositivo y no es
portable. El formato `.miguardia-backup` conserva exactamente sus 17
preferencias semánticas actuales. Combinar, reemplazar o recuperar una copia no
lee ni escribe `completed_version`. Por eso una instalación restaurada en otro
dispositivo puede ofrecer la guía nuevamente.

### Privacidad y bloqueo

Ayuda y la guía se componen dentro de la puerta existente de Bloqueo. Cuando la
aplicación está cerrada, no existen pantallas ni semánticas de onboarding por
detrás de la cobertura.

La guía no solicita permisos anticipados. Notificaciones, alarmas, fotos,
archivos y Clima conservan sus decisiones dentro de las superficies dueñas.
Los textos de Ayuda no muestran datos concretos de la persona ni se registran
datos laborales en logs.

### Contratos protegidos

Este bloque no modifica:

- Room V5, sus 27 tablas, esquemas o migraciones;
- el formato ni la versión de Copias;
- el DataStore de Bloqueo;
- Gradle, manifiesto, permisos, dependencias, `applicationId`, versión o SDK;
- las fórmulas, proyecciones o escritores laborales;
- los cuatro rubros ni la configuración única.

## Consecuencias

- La primera pantalla de una instalación nueva continúa siendo el selector de
  rubro.
- `WorkSetup` sigue siendo la única fuente de la primera configuración.
- La guía puede evolucionar aumentando su versión sin reescribir datos
  laborales.
- Ayuda enseña la interfaz definitiva y puede mantenerse por temas sin abrir
  un segundo sistema de navegación.
- Un usuario que restaura sus datos puede volver a recibir orientación sin que
  la copia cambie la seguridad o las preferencias del dispositivo.
- Los fixtures Activity que parten de `V2Ready` deben marcar explícitamente la
  versión completada, salvo las pruebas dedicadas a la primera guía.
- Corresponde regresión de arranque, navegación, WorkSetup, Copias, Bloqueo,
  recreación, tema, orientación y zoom.

## Relación con ADR 0014

ADR 0014 se conserva como antecedente y fuente de la elección de DataStore
versionado. Este ADR reemplaza su aplicación operativa en MiGuardia 2.0:
elimina la secuencia y los formularios V1, fija el selector de rubro y
`WorkSetup` antes de la guía, incorpora las superficies V2 actuales y declara
la marca no portable.

## Alternativas descartadas

### Mostrar la bienvenida antes del rubro

Se descarta porque contradice la primera apertura V2 aprobada y agrega una
pantalla antes de la decisión que adapta el vocabulario.

### Repetir la configuración laboral dentro del recorrido

Se descarta porque duplicaría validaciones y podría crear datos parciales o dos
fuentes de verdad.

### Inferir finalización desde la existencia de un lugar o una jornada

Se descarta porque no permite distinguir si la persona vio la guía, impide una
evolución versionada y no resuelve a usuarios ya configurados.

### Incluir la marca en Copias

Se descarta porque es una preferencia de experiencia del dispositivo, no un
dato laboral. Mantenerla fuera evita cambiar el formato cerrado de Copias y
permite orientar nuevamente después de una restauración.

### Crear una Ayuda remota o dependiente de Internet

Se descarta porque la información básica debe estar disponible sin conexión y
no justifica cuentas, telemetría, backend ni otra dependencia.
