# Cómo continuar MiGuardia 2.0

Referencia 2.x, no entrada operativa de 3.0. Las menciones de carpeta/rama siguientes pertenecen a 2.0; para continuar en este repositorio usar `AGENTS.md`, `docs/STATUS.md` y `docs/WORKFLOW.md`.

Joaquin decide el objetivo; MAIN inspecciona, implementa o coordina, comprueba e integra ese alcance. El estado de hoy y lo pendiente están en `docs/STATUS.md`. El procedimiento técnico vive en `docs/WORKFLOW.md`.

La carpeta de desarrollo es `MiGuardia-2.0` y la rama `codex/miguardia-2.0`. MAIN es el rol que integra; `main` es una rama Git protegida. Los proyectos/worktrees antiguos se conservan como antecedentes, no como base de trabajo.

Tres cosas diferentes: cambios guardados en archivos; commit local que los registra; push que los lleva a GitHub. Una aprobación o una prueba verde no crea el commit. Ninguno de esos pasos publica por sí mismo una aplicación firmada.

Para retomar alcanza con el estado actual y el contrato del objetivo. No hace falta leer todos los chats, auditorías o prompts anteriores. Si hay un candidato presente, MAIN lo revisa; no lo vuelve a construir desde cero.

Cada entrega explica qué cambió, por qué, qué se comprobó realmente y qué falta. Compilar, ejecutar pruebas, probar un teléfono y distribuir una APK son comprobaciones distintas.

MAIN toma decisiones técnicas reversibles del alcance autorizado. Consulta si falta una decisión material de producto, seguridad, datos, costos o una acción externa. No encadena la siguiente tarea por haber cerrado la anterior.
