# Optimizar instrucciones y flujo de MiGuardia 2.0

- Tipo: contrato del alcance autorizado por Joaquin el 2026-09-06.
- Estado de ejecución: exclusivamente en `docs/STATUS.md`.
- Base de entrada: `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35`, checkout compartido con cliente 28 sin commit.

## Qué hace y por qué existe

Reduce la lectura repetida, elimina estados operativos contradictorios y convierte procedimientos de verificación en herramientas reproducibles. Aplica H01–H15 de `docs/audits/2026-09-06-auditoria-instrucciones-y-flujo.md` sin cambiar reglas laborales, datos o distribución.

## Plan y fronteras

1. MAIN: preservar fuentes anteriores; simplificar AGENTS/README/STATUS/mapa/planificación/método e índice; archivo histórico e índice ADR; lecturas por tarea y control documental.
2. Revisor/implementador local: `tools/verify.py`, `tools/tests/test_verify.py`, `app/build.gradle.kts` y sólo pruebas JVM/oráculos beta/updates necesarios para separar interoperabilidad de pruebas locales. Sin tocar UI ni androidTest.
3. Implementador QA: `tools/qa.py`, `tools/qa-groups.json`, `tools/tests/test_qa.py`, `app/src/androidTest/**` para nombres de helpers e aislamiento explícito. No ejecutar ADB ni dispositivos. Modo plan por defecto y controles de autorización/paquete; sin instalar o limpiar datos automáticamente.
4. Implementador UI: `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt` y nuevos archivos del mismo paquete para extraer Calendario, detalle y Apariencia sin cambiar lógica, estado, semántica ni parámetros. No editar MainActivity, Gradle, tests, dominio o base.
5. MAIN: integrar, verificar secuencialmente Gradle, pruebas de herramientas y documentos, inspeccionar diff/preservación y revisar independientemente.

Los contratos de las herramientas se describen en VALIDACION; cada agente devuelve comandos disponibles, archivos y evidencia. Escrituras separadas; MAIN es único escritor de documentación general y coordinador de Gradle. Todos preservan la entrada del cliente 28. No crear tareas de Codex, ramas, worktrees, commits ni servicios desde las subtareas.

## Validación y terminado

- Arranque común <=40.000 caracteres, rutas preservadas y un solo estado actual comprobado automáticamente.
- Ninguna decisión funcional desaparece: preservar originales y verificar migración documental/ADR.
- Pruebas JVM locales independientes de Node/website; interoperabilidad explícita exigible y con los dos estados Git registrados.
- Runners y catálogo QA versionables, sin evidencia/secretos privados; simulaciones de seguridad y validación de grupos con todas las clases asignadas.
- Extracción UI mecánica comprobada, compilación y regresiones pertinentes; no inferir QA física nueva.
- Evidencia local durable fuera de build, con hashes e inventario seguro. Informe de cierre H01–H15 y límites explícitos.

La autorización de implementación cubrió modificar/crear/eliminar archivos para estas mejoras, con límites propios de dispositivos y distribución. El checkpoint conjunto con el cliente 28 y la continuación posterior exclusivamente en MAIN responden a la autorización adicional del 2026-09-06 registrada en STATUS. Esta tarea no ejecuta el bloque 29.
