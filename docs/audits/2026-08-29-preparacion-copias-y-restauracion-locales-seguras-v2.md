# Preparación de Copias y restauración locales seguras V2

- Fecha: 2026-08-29
- Rol: MAIN 2.0
- Tipo: contrato documental previo a implementación

## Objetivo

Cerrar el comportamiento funcional y técnico de la siguiente dependencia sin
implementar código, usar dispositivos ni abrir otra tarea.

## Puerta 0

Verificado antes de editar:

- ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD funcional: `ff2f9b6699606f7ef3c7e599e2a6b4da25b40c67`;
- checkout limpio, sin staged ni archivos nuevos;
- upstream: `origin/codex/miguardia-2.0`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- remoto privado: `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- nueve worktrees históricos preservados.

## Decisión de Joaquin

La recuperación debe preguntar antes de modificar datos. La persona podrá
elegir:

1. `Combinar con mis datos`, sin sobreescritura silenciosa, omitiendo duplicados
   exactos y resolviendo conflictos antes de aplicar;
2. `Reemplazar todo`, mostrando qué se perderá y exigiendo una segunda
   confirmación.

## Contrato cerrado

MAIN auditó el estado real de persistencia y fijó ADR 0035:

- copia manual, completa, lógica y versionada;
- las 27 tablas Room y preferencias portables forman la unidad recuperable;
- fotos opcionales y atómicas con sus metadatos;
- formato público sin SQLite, WAL ni DataStore crudos;
- contraseña opcional recomendada; PBKDF2-HMAC-SHA256 y AES-256-GCM mediante
  JCA, sin dependencia nueva;
- vista previa y candidato aislado antes de escribir;
- combinación sólo sobre instalación vacía o la misma `timelineId`;
- dos líneas temporales no vacías no se fusionan;
- journal, rollback y recuperación temprana dejan estado viejo o nuevo;
- SAF sin permiso general de almacenamiento, nube ni sincronización;
- Room V5, 27 tablas y esquemas 1–5 protegidos.

## Archivos documentales

Nuevos:

- `docs/prompts/COPIAS_Y_RESTAURACION_LOCALES_SEGURAS_V2.md`;
- `docs/adr/0035-copias-locales-versionadas-y-restauracion-atomica.md`;
- esta auditoría.

Actualizados:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/prompts/README.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.

No se modificaron código, Room, DataStore, Gradle, manifiesto, permisos,
FileProvider, versión, SDK ni comportamiento ejecutable.

## Verificación

- referencias y estados canónicos revisados;
- revisión independiente sin hallazgos P0/P1/P2; el único P3 de numeración en
  la secuencia fue corregido antes del checkpoint;
- prompt marcado `HABILITADO — IMPLEMENTACIÓN PENDIENTE`;
- exactamente una dependencia implementadora pendiente;
- `git diff --check` limpio;
- no se ejecutó Gradle porque el cambio es exclusivamente documental;
- no se usó ADB, Samsung ni emulador;
- no hubo staging, commit ni push durante la preparación del contenido.

## Próximo paso

MAIN crea el checkpoint documental local. Joaquin abre la única tarea
especialista con ese HEAD exacto y el prompt habilitado. La dependencia devuelve
un candidato sin commit; MAIN lo audita e integra antes de habilitar el bloque de
bloqueo local.
