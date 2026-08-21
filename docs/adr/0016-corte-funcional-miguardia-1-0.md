# ADR 0016: corte funcional de MiGuardia 1.0

> Nota de continuidad 2.0: las referencias históricas a `Salud` u “otras
> profesiones” quedaron reemplazadas por un catálogo cerrado de Vigilancia
> privada, Policía, Enfermería y Medicina, con Enfermería y Medicina separadas.
> Esta ADR sólo define el corte de alcance de MiGuardia 1.0.

- Estado: aceptada
- Fecha: 2026-08-19

## Contexto

La planificación original de MiGuardia incluía, además del núcleo actualmente estable, onboarding completo, widgets, informes, copias de seguridad, bloqueo local y una posterior ampliación a otras profesiones. Exigir todo ese alcance antes del primer sellado prolongaría indefinidamente la versión inicial y mezclaría estabilización con funciones nuevas.

Pulso Vigilia fue auditado, probado, confirmado y publicado en `main`. La aplicación alcanzó un estado estable y verificable para vigiladores privados que puede constituir una primera versión real sin declarar defectuosas las capacidades todavía no construidas.

## Decisión

MiGuardia 1.0 se define como el estado estable publicado para vigiladores privados después de integrar Pulso Vigilia.

Antes del sellado:

- no se implementan funciones nuevas;
- sólo se corrigen defectos bloqueantes o regresiones verificables del estado actual;
- no se recupera código desde worktrees históricos;
- no se realizan rediseños ni mejoras oportunistas;
- `main` debe permanecer limpia y publicada.

Quedan diferidos a MiGuardia 2.0:

- onboarding completo, recorrido contextual y Ayuda;
- widgets;
- informes PDF/XLSX;
- copias de seguridad y restauración;
- bloqueo local;
- ampliación a Salud, Policía y otras profesiones;
- cualquier mejora no indispensable para estabilizar el producto actual.

Las especificaciones existentes de esos módulos se conservan como backlog y fuente de diseño futuro. No son criterios de aceptación, defectos ni bloqueantes de MiGuardia 1.0.

Esta decisión no crea todavía la etiqueta `v1.0.0`, no cambia `versionName` ni `versionCode`, no crea una rama release y no inicia MiGuardia 2.0. Esas operaciones requieren puertas posteriores explícitas.

## Consecuencias

- El corte 1.0 puede auditarse y sellarse sobre una base funcional ya verificada.
- La deuda funcional planificada permanece visible sin confundirse con regresiones.
- Las pruebas de release deben validar el comportamiento implementado, no módulos diferidos.
- Toda propuesta nueva queda fuera de alcance hasta completar el sellado.
- La futura 2.0 debe revalidar prioridades y contratos antes de implementar el backlog; esta ADR no autoriza por sí sola ninguna de esas funciones.

## Alternativas descartadas

### Esperar a completar la planificación original

Se descarta porque convierte funciones valiosas pero no esenciales para el núcleo actual en bloqueantes artificiales de la primera versión.

### Eliminar la documentación de funciones pendientes

Se descarta porque perdería decisiones y trabajo de diseño reutilizable. Las especificaciones se conservan y se reclasifican como alcance futuro.

### Recuperar implementaciones desde worktrees históricos

Se descarta porque esos árboles no sustituyen la línea canónica auditada y podrían reintroducir cambios obsoletos o incompletos.
