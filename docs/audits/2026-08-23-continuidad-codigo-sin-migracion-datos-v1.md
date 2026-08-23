# Auditoría — continuidad de código sin migración de datos V1

- Fecha: 2026-08-23
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `fe911e26e367ff1896a20f504476c2d9874557bc`
- Alcance: decisión y gobernanza documental; sin cambios de aplicación

## Decisión aclarada por Joaquin

MiGuardia 1.0 y MiGuardia 2.0 sí tienen continuidad técnica: el código inicial
es la base sobre la que se construye la versión nueva. Lo que no existe es una
necesidad de conservar o migrar datos de usuarios V1, porque 1.0 fue una prueba
interna y no fue distribuida.

Por lo tanto:

- se conserva y reutiliza el código útil de 1.0;
- no se reescribe MiGuardia desde cero;
- V2 comienza con datos limpios y selección de sector;
- no habrá activación ni migración desde una instalación V1;
- el cambio de rubro desde una fecha será una función interna de V2.

## Estado técnico observado

El código actual todavía implementa compatibilidad de datos heredados:

- Room conserva migraciones hasta la versión 7;
- el dominio distingue `NEW_V2` y `MIGRATED_V1`;
- existen decisiones de motor y recorridos de interfaz para una raíz V1;
- las pruebas cubren migraciones y regresión del flujo heredado.

No se descartó ni modificó ninguna de esas superficies en este checkpoint. Su
retiro afecta arquitectura, persistencia, arranque y pruebas, por lo que debe
resolverse como un bloque ejecutable independiente.

## Fuentes actualizadas

- `AGENTS.md`;
- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/STATUS.md`;
- `docs/GUIA_DE_TRABAJO_CODEX_2_0.md`;
- `docs/sectores/VIGILANCIA_PRIVADA.md`;
- `docs/PROMPT_MAESTRO_PLANIFICACION_2_0.md`;
- `docs/prompts/README.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- los prompts históricos de persistencia Room v6, primera apertura, catálogo y
  carga manual afectados por la regla reemplazada;
- ADR 0017, 0019, 0020, 0021 y 0022;
- ADR 0024, que registra la decisión vigente.

## Resultado de la revisión documental

- la continuidad de código quedó separada de la compatibilidad de datos;
- `main`, `origin/main` y `v1.0.0` siguen protegidos como historia y fuente;
- el `applicationId` actual no se presenta como una promesa de migración;
- se canceló la futura “activación V2 desde una instalación anterior”;
- el próximo bloque recomendado retira el modo y la persistencia V1, pero no se
  creó ni habilitó su prompt;
- el cambio de rubro desde una fecha queda después de esa limpieza técnica;
- no se autorizó ninguna limpieza automática del Samsung.

## Validación

- revisión de contradicciones en las fuentes activas: **CORRECTA**;
- referencias Markdown y rutas citadas: **CORRECTAS**;
- `git diff --check`: **CORRECTO**;
- revisión del diff y staging por rutas explícitas: **CORRECTOS**.

No corresponde ejecutar Gradle ni ADB: este checkpoint sólo corrige la fuente
de verdad documental y no cambia código, Room, DataStore, Gradle, manifiesto,
permisos, versión ni comportamiento de la aplicación. El Samsung no fue
consultado ni modificado.

## Próximo paso recomendado

Cuando Joaquin pida el próximo prompt, preparar un bloque dedicado a dejar
MiGuardia como V2 única y retirar la compatibilidad de datos con la prueba 1.0,
conservando todo el código útil. No abrir esa tarea automáticamente.
