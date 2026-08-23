# Auditoría — flujo de handoffs y checkpoints de MAIN

- Fecha: 2026-08-23
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `ae576860ced9c51fdd96c3b69c6c050b52c1e0f4`
- Alcance: gobernanza documental; sin cambios de aplicación

> Nota posterior: la recomendación histórica de activar V2 desde una
> instalación anterior quedó cancelada. El flujo de handoffs sigue vigente y
> el próximo contrato habilitado es la edición y eliminación individual V2.

## Decisión de Joaquin

El flujo operativo vigente es:

1. Joaquin decide cuándo hace falta el prompt de una nueva tarea y cuándo se
   abre esa tarea.
2. Joaquin entrega a MAIN el handoff producido por la tarea especializada.
3. MAIN verifica la base, audita el diff, integra el resultado, ejecuta las
   pruebas proporcionales, corrige defectos acotados y actualiza el estado.
4. Cuando el bloque queda verde, MAIN crea automáticamente el commit local que
   funciona como checkpoint, sin pedir otra autorización para ese commit.
5. MAIN informa el cierre y recomienda el siguiente bloque, pero no escribe su
   prompt ni abre su tarea hasta que Joaquin lo indique.

Pedir únicamente un prompt autoriza a redactarlo, validarlo y crear su
checkpoint documental local. No autoriza por sí solo a abrir la tarea.

## Límites conservados

- un solo handoff o una sola tarea implementadora por vez;
- ningún push automático;
- ningún cambio en `main`, `v1.0.0`, tags, Release o producción;
- ninguna acción destructiva ni uso de datos reales;
- una decisión funcional material abierta vuelve a Joaquin;
- una validación roja impide el checkpoint del bloque ejecutable.

La autorización anterior para encadenar dependencias automáticamente queda
reemplazada sólo en ese punto. MAIN sigue siendo responsable de la columna
vertebral, la auditoría independiente, la integración, las pruebas, la
documentación y los checkpoints locales verificados.

## Archivos de gobernanza actualizados

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/STATUS.md`;
- `docs/GUIA_DE_TRABAJO_CODEX_2_0.md`;
- `docs/prompts/README.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.

## Validación

- referencias Markdown y rutas citadas: **CORRECTAS**;
- contradicciones de continuidad automática en las fuentes activas: **CERO**;
- `git diff --check`: **CORRECTO**;
- revisión del diff y staging por ocho rutas documentales explícitas:
  **CORRECTOS**.

No corresponde ejecutar Gradle ni ADB porque este cambio modifica únicamente
la forma de coordinación documentada; no cambia código, Room, DataStore,
Gradle, manifiesto, permisos, versión ni comportamiento de la aplicación.

## Estado registrado al cerrar — reemplazado en su próximo bloque

En ese momento no quedó autorizado otro prompt y se recomendó activar V2 desde
una instalación anterior y cambiar de rubro. Esa recomendación quedó
reemplazada por la nota superior; el contrato de edición y eliminación
individual fue autorizado después.
