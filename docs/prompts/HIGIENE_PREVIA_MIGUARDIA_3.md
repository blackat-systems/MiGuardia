# Higiene previa a MiGuardia 3.0

## TASK

Dejar el proyecto MiGuardia 2.0 limpio, recuperable y sin decisiones operativas obsoletas antes de diseñar MiGuardia 3.0.

## CONTEXT

La versión 2.0.2 está publicada. El checkout acumuló worktrees históricos, evidencia de QA pesada, outputs regenerables y documentos que podían confundirse con instrucciones vigentes.

## INPUTS

- Proyecto `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama `codex/miguardia-2.0`.
- Base limpia `57e5faa1bb5700b91060fea6ba83405d5e331f0c`.
- Estado y reglas vigentes en `AGENTS.md` y `docs/STATUS.md`.

## OUTPUT

- Trabajo histórico recuperable mediante bundle, patches y hashes.
- Un solo checkout operativo más el worktree principal requerido por Git.
- Evidencia compacta, sin discos de emulador, APK técnicas duplicadas ni datos crudos de prueba.
- Outputs de compilación y cachés regenerables retirados.
- Documentación operativa coherente y evidencia fechada del saneamiento.

## SCOPE

Git local, worktrees, ramas integradas, archivos ignorados, `docs/` y herramientas de verificación. El código productivo sólo se inspecciona.

## DEPENDENCIES

No aplica. La publicación 2.0.2 es la base estable que se preserva.

## DO NOT

- No cambiar comportamiento de la app, Room, Copias, permisos, versión o publicación.
- No regenerar ni mover claves de firma.
- No usar Samsung, emulador, red de producto ni servicios remotos.
- No borrar trabajo histórico antes de producir y verificar su recuperación.
- No eliminar ADR o pruebas de compatibilidad por antigüedad si aún preservan un contrato.

## VALIDATION

- Puerta 0 y estado Git final.
- Bundle verificable, patches reversibles y manifiestos SHA-256.
- Guardas documentales y catálogo QA.
- Batería local limpia según `docs/VALIDACION.md`.
- Revisión completa del diff y `git diff --check`.

## DONE WHEN

El checkout queda ordenado, la evidencia útil continúa disponible, lo retirado está inventariado, las pruebas locales pasan y STATUS señala el próximo paso real: descubrimiento de MiGuardia 3.0.
