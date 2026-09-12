# Preparación del retiro V1 y la primera base exclusiva V2

- Fecha: 2026-08-23
- Rol: MAIN 2.0
- Rama: `codex/miguardia-2.0`
- HEAD funcional de entrada:
  `4646f665eec84052a544a5179c72b93971df2700`
- Alcance: documentación y contrato de la próxima dependencia

## Resultado

Quedó habilitado el prompt **Dejar MiGuardia únicamente en modo 2.0**. No se
abrió todavía una tarea implementadora y no se modificó el comportamiento de
la aplicación.

El contrato no vuelve al producto anterior ni descarta el trabajo V2 ya
integrado. Conserva el código y las capacidades comunes útiles, retira las
bifurcaciones que sólo existen para sostener el modo V1 y fija una identidad
Room propia para el producto nuevo.

## Puerta 0

MAIN verificó en vivo antes de documentar:

- ruta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD funcional:
  `4646f665eec84052a544a5179c72b93971df2700`;
- upstream:
  `origin/codex/miguardia-2.0` en
  `836d908f54a407c48cc9e3c27c9587c6dc908ca2`;
- tag anotado `v1.0.0`:
  `227c931ff8e381ab00120ad61b1c86ac71c03e46`;
- tag peeled, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- remoto: `https://github.com/blackat-systems/MiGuardia.git`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- worktrees históricos presentes e intactos;
- índice vacío y checkout limpio antes de comenzar.

La rama local estaba seis commits por delante de su upstream. El push puntual
anterior continúa consumido y no se reutilizó.

## Fuentes e inventario

Se leyó la jerarquía obligatoria completa: `AGENTS.md`, mapa, estado,
planificación, índice de prompts, fichas sectoriales, prompt rector MAIN 2.0,
ADR aplicables, prompt histórico MAIN y contratos/auditorías de persistencia,
primera configuración, carga manual y edición/eliminación V2.

También se inspeccionaron las superficies actuales de dominio, Room,
DataStore, navegación, Perfil, gestión V1/V2, próximo evento, notificaciones y
sus pruebas. Tres revisiones acotadas y de sólo lectura separaron:

- código común que debe seguir siendo la columna vertebral V2;
- contratos, tablas y rutas exclusivos del modo V1;
- límites necesarios para que la nueva base no invente un rediseño.

## Decisiones congeladas

ADR 0026 establece:

- clase `MiGuardiaV2Database`;
- archivo `miguardia-v2.db`;
- Room versión 1 y esquema exportado propio;
- ninguna migración, apertura, copia, transformación o eliminación de
  `miguardia.db`;
- diecinueve tablas de aplicación V2, separadas de los metadatos internos de
  Room/SQLite;
- retiro de `schedule_combinations`, `shift_novelties`,
  `formal_shift_changes` y de las tres columnas de procedencia V1 enumeradas;
- preservación exacta del resto del contrato v7 —tipos, defaults, nulabilidad,
  claves, índices, únicas, relaciones y acciones referenciales— salvo los
  cambios expresamente aprobados;
- `Shift.sourceObjectiveId` obligatorio y toda jornada acompañada por una
  única fotografía `ShiftWorkSnapshot`;
- `GuardProfileStore` y `guard_profile.preferences_pb` conservados sólo como
  contrato neutral del nombre/apodo opcional, sin empresa, profesión, pantalla
  ni consumidor V2 nuevo en este bloque;
- selector inicial, configuración, Calendario, carga, edición/eliminación y
  capacidades comunes preservados;
- Perfil, Resumen, Objetivos/horarios, carga estructural, francos y Novedades
  V1 fuera del runtime.

El mismo `applicationId` continúa por ahora, pero no representa una promesa de
actualización compatible. El recorrido soportado comienza con datos de la
aplicación limpios; el runtime no limpia una instalación anterior.

## Auditoría independiente

Una auditoría de sólo lectura detectó dos ambigüedades antes del checkpoint:

1. el destino del nombre/apodo quedaba a criterio del implementador;
2. las restricciones no enumeradas de las entidades Room conservadas no
   estaban congeladas.

MAIN corrigió ambas. La reauditoría confirmó que el DataStore neutral respeta
la planificación, que el esquema conserva exactamente las restricciones v7
fuera de los deltas aprobados y que no quedó una decisión material delegada ni
una contradicción nueva.

## Archivos documentales

Nuevos:

- `docs/adr/0026-base-room-exclusiva-v2-y-retiro-del-modo-v1.md`;
- `docs/prompts/RETIRAR_MODO_V1_Y_FIJAR_BASE_EXCLUSIVA_V2.md`;
- esta auditoría.

Actualizados:

- `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
- `docs/STATUS.md`;
- `docs/prompts/README.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.

## Verificación

- `git diff --check`: correcto;
- referencias Markdown locales: correctas;
- archivos nuevos con salto final y sin espacios finales;
- diff limitado a los siete documentos enumerados;
- ninguna modificación en `app`, `core`, Room ejecutable, DataStore, Gradle,
  manifiesto, permisos, versión, SDK o producción.

No se ejecutó Gradle ni ADB porque este checkpoint sólo define el contrato y no
cambia código. El Samsung no fue consultado, instalado, limpiado ni modificado.

## Siguiente paso

El prompt queda listo para una única dependencia especializada. Al abrirla,
MAIN debe informarle el hash exacto de este checkpoint documental. La
dependencia devolverá un candidato sin commit; MAIN auditará, probará e
integrará antes de habilitar el bloque siguiente.

No hubo push, tag, merge, rebase, reset, publicación ni acción sobre `main`.
