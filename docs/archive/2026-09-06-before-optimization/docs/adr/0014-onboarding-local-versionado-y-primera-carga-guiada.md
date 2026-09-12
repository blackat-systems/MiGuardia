# ADR 0014: Onboarding local versionado y primera carga guiada

- Estado: antecedente histórico; reemplazada operativamente por ADR 0037
- Fecha: 2026-08-18

> Actualización 2026-09-02: ADR 0037 reemplaza la aplicación operativa de este
> diseño en MiGuardia 2.0. Este archivo se conserva como antecedente de la
> persistencia versionada, pero sus recorridos y fuentes V1 no deben
> implementarse.

## Contexto

Actualización del 19 de agosto de 2026: el diseño se conserva, pero dejó de ser requisito de MiGuardia 1.0 por el corte funcional registrado en `docs/adr/0016-corte-funcional-miguardia-1-0.md`. No debe implementarse antes del sellado.

MiGuardia necesita explicar su valor y su privacidad en la primera apertura, mostrar contextualmente dónde están y cómo se usan sus controles principales, permitir repetir esa guía desde Ayuda y acompañar la primera carga sin duplicar Perfil, Objetivos, Horarios ni Calendario.

El estado de haber visto u omitido la introducción es una preferencia local simple. La existencia de objetivos, horarios y guardias, en cambio, ya tiene fuentes relacionales en Room. Mezclar ambos conceptos o copiar datos produciría estados divergentes y pondría en riesgo la continuidad histórica.

## Decisión

La introducción usa un DataStore Preferences exclusivo, con archivo `onboarding.preferences_pb` y una clave entera `completed_version`.

- La versión inicial del recorrido es `1`.
- Ausencia o valor menor que `1` significa que la introducción inicial sigue pendiente.
- `Omitir guía` desde la introducción o el recorrido contextual y `Finalizar` desde el último foco guardan atómicamente la versión actual.
- Los tres pasos introductorios y el recorrido contextual forman una única guía versionada. El paso visible es efímero y puede sobrevivir a recreación durante la sesión, pero no se persiste como progreso de negocio.
- Repetir la guía desde Ayuda no borra ni disminuye la versión persistida; abre una sesión de repetición independiente y al terminar regresa a Ayuda.
- Un error de lectura no muestra fugazmente la aplicación principal: ofrece un estado seguro con reintento.
- Un error de escritura conserva la pantalla actual y permite volver a intentar; no declara completado lo que no se persistió.

La primera carga guiada no persiste un progreso paralelo. Su avance se deriva de las fuentes reales:

- Perfil se abre mediante su superficie existente;
- objetivos y horarios activos se observan desde sus repositorios Room;
- la primera guardia se determina mediante `ShiftRepository.observeHasAny()`;
- cada creación o edición usa los formularios vigentes.

Si el usuario abandona la guía, sólo permanecen operaciones que ya confirmó en los flujos reales. Nunca se crean filas incompletas, marcadores artificiales ni borradores persistidos por el tutorial.

## Alternativas descartadas

### Guardar onboarding en Room

Se descarta porque obligaría a Room v6 para una preferencia versionada sin relaciones ni historial de negocio.

### Inferir que onboarding terminó porque existen guardias

Se descarta porque una instalación actualizada puede contener datos y aun así no haber visto la introducción; además impediría repetirla de forma explícita.

### Crear formularios especiales para la primera carga

Se descarta porque duplicaría validaciones, navegación y fuentes de verdad de Perfil, Objetivos, Horarios y Guardias.

### Guardar un paso intermedio de la guía

Se descarta en este incremento. El estado real ya permite reconstruir qué falta y evita que una marca auxiliar contradiga los datos confirmados.

## Consecuencias

- Room permanece en v5 con trece entidades y migraciones `1→2→3→4→5`.
- La introducción puede evolucionar aumentando la versión sin borrar datos laborales.
- El recorrido contextual debe construirse sobre el menú lateral y la selección directa del Calendario ya integrados; no conserva coordenadas o referencias a la barra inferior histórica.
- Una actualización puede mostrar la introducción una vez aunque existan datos; la primera carga sólo se ofrece cuando no hay guardias.
- Ayuda se convierte en la entrada durable para repetir el tutorial.
- No cambian permisos, manifiesto, red, cuentas, nube, telemetría ni datos históricos.
