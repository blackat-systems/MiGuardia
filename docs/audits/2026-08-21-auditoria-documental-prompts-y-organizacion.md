# Auditoría documental — prompts y organización de MiGuardia 2.0

> Fotografía histórica anterior a la autorización posterior de Joaquin. Su
> descripción de PLANIFICACIÓN activa y MAIN pausada quedó reemplazada por
> `2026-08-21-reactivacion-main-y-puerta-cero.md`.

- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- HEAD de partida: `6dab82b8f239f8009cfcb32d400b50fcc4080836`
- Alcance: documentación y organización; sin cambios de código ejecutable

## Resultado

La documentación quedó organizada alrededor de una sola verdad de producto:
MiGuardia 2.0 reutiliza el núcleo y Calendario de MiGuardia 1.0 para cuatro
sectores exactos —Vigilancia privada, Policía, Enfermería y Medicina— y valida
las reglas laborales de cada sector mediante investigación separada.

PLANIFICACIÓN permanece activa. MAIN ya existe, pero está pausado. No existe un
especialista habilitado ni autorización de commit o push.

## Línea base verificada

- ruta correcta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama no detached: `codex/miguardia-2.0`;
- `HEAD`: `6dab82b8f239f8009cfcb32d400b50fcc4080836`;
- `main`, `origin/main` y `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- MiGuardia 1.0.0 permaneció intacta;
- se preservaron todos los cambios preexistentes y la implementación candidata
  sin commit.

## Inventario auditado

Se leyeron y clasificaron:

- cuatro prompts rectores en `docs/`;
- veintitrés prompts de módulos en `docs/prompts/`;
- las tareas Codex actuales relacionadas con MiGuardia 2.0;
- las fuentes rectoras, ADR 0016–0019 y el registro de la versión 1.0.0.

Clasificación final:

- un prompt activo: PLANIFICACIÓN 2.0;
- un prompt pausado: MAIN 2.0;
- veintiún prompts históricos de MiGuardia 1.0;
- un prompt V2 cerrado: Calendario adaptable;
- un prompt V2 candidato ya ejecutado: reglas internas de configuración por
  mes;
- ningún prompt especialista habilitado.

## Contradicciones corregidas

- PLANIFICACIÓN figuraba cerrada y MAIN activa en fuentes anteriores;
- el trabajo candidato usaba códigos técnicos `1A/1B` sin explicación humana;
- una orden intermedia incluía cinco sectores y una opción `Otro`;
- Medicina y Enfermería habían sido agrupadas bajo `Salud` en borradores viejos;
- el motor de horas y la migración aparecían aceptados aunque faltan incorporar
  los formularios de Policía, Enfermería y Medicina;
- varios prompts V1 todavía parecían órdenes listas para ejecutar;
- no existía un índice único de prompts ni una fuente durable por sector;
- el título visible de MAIN y el de la tarea candidata quedaron obsoletos;
- una conversación ajena de ChatGPT posee un título parecido a Planificación y
  no debe tratarse como fuente del repositorio.

## Fuentes nuevas

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- `docs/sectores/README.md`;
- `docs/sectores/VIGILANCIA_PRIVADA.md`;
- `docs/sectores/POLICIA.md`;
- `docs/sectores/ENFERMERIA.md`;
- `docs/sectores/MEDICINA.md`;
- `docs/GUIA_DE_TRABAJO_CODEX_2_0.md`.

Los prompts históricos no se borraron. Recibieron un encabezado visible que los
clasifica como historia V1 y prohíbe su ejecución. El índice canónico determina
el estado de uso de cada uno.

## Investigación pendiente

Las respuestas de formularios todavía no están dentro del repositorio. Deben
incorporarse como síntesis anónimas por sector, separando patrones, respuestas
aisladas, contradicciones y decisiones de Joaquin. Hasta entonces no se congela
una fórmula universal ni defaults de guardia pasiva, nocturnidad o base.

## Validación documental

- los 24 archivos actuales de `docs/prompts/` —23 prompts más el índice— están
  listados y clasificados;
- ningún prompt histórico quedó sin el rótulo `NO EJECUTAR`;
- no quedan referencias a los nombres de archivo anteriores de `1A`;
- todas las referencias documentales de las fuentes activas resuelven a
  archivos existentes;
- búsqueda de mojibake: sin hallazgos;
- `git diff --check`: correcto.

No se ejecutó Gradle, ADB ni QA física porque esta auditoría no modificó código,
Room, DataStore, Compose, Gradle, permisos, versión ni comportamiento.

## Estado Git al cierre

Los cambios continúan en disco y sin commit. No se hizo commit, push, tag,
merge, rebase, reset ni modificación de las tareas de Codex.

## Próximo paso único

Incorporar primero las respuestas del formulario de Policía en
`docs/sectores/POLICIA.md` y cerrar, en lenguaje humano, cómo funcionan su base
de horas y sus horas adicionales.
