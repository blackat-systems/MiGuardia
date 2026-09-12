# Estado actual de MiGuardia 3.0

<!-- Fuente operativa única. El historial y la evidencia fechada viven en docs/audits/. -->

- Bloque autorizado: HIGIENE_PREVIA_MIGUARDIA_3
- Estado: CERRADO
- Contrato: docs/prompts/HIGIENE_PREVIA_MIGUARDIA_3.md
- Base verificada: 3f1365259e15292a2f24e3361acd523213b49fad
- Procedencia: snapshot funcional `a771d78e3ec33bd2d3ef02bd1ae310514359e004` del repositorio 2.x, importado como raíz nueva sin arrastrar su historial.
- Actualización: 2026-09-12

## Producto de referencia

- Versión pública: MiGuardia `2.0.2`, `versionCode 6`.
- Distribución: APK firmada desde `blackatsystems.com/miguardia`; publicación Sites v15 y descarga remota verificadas en el cierre de 2.0.2.
- Modelo: Android local-first, sin cuenta laboral, nube laboral, analítica, telemetría ni activación beta.
- Datos: Room V6, 27 tablas y seis esquemas preservados; Copias mantiene 17 preferencias portables.
- Actualizaciones: consulta pública consciente, verificación de APK y guardado manual mediante SAF.
- Firma: clave permanente y respaldo fuera de Git; no regenerar. Cada nueva versión requiere firma y QA de actualización proporcionales.
- Soporte y privacidad: `blackat.systems@gmail.com`.

La auditoría vigente de la publicación es `docs/audits/2026-09-10-franco-arrastre-nocturno.md`. La higiene de este bloque no agrega evidencia de instalación o actualización física de `versionCode 6`.

## Cursor de MAIN

- Etapa alcanzada: 0 — workspace de MiGuardia 3.0 creado desde la base estable y saneada de 2.0.
- Siguiente etapa: descubrimiento de MiGuardia 3.0.
- Bloque funcional 3.0 autorizado: ninguno todavía.
- Próxima tarea: inventariar capacidades vigentes, necesidades no implementadas y cambios deseados; decidir alcance y contratos antes de modificar producto, versionado o arquitectura.
- Ruta de orquestación: `docs/ORQUESTACION_MAIN.md`.

## Workspace

- Fuente del proyecto ChatGPT/Codex: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Checkout activo 3.0: `MiGuardia-3.0/`, repositorio independiente y rama `codex/miguardia-3.0`.
- Remoto 3.0: `https://github.com/blackat-systems/MiGuardia.git`; `main` conserva la raíz inicial `3f1365259e15292a2f24e3361acd523213b49fad`.
- Referencia estable: `MiGuardia-2.0/`, perteneciente al repositorio 2.x archivado; sólo lectura salvo corrección 2.x autorizada.
- Administración histórica: `Repositorio-Git/` pertenece al repositorio 2.x archivado; no comparte objetos ni referencias con 3.0 y no debe usarse como directorio ordinario de desarrollo.
- Rescates históricos: `Archivo-recuperacion/`; sólo lectura.

## Resultado de la higiene previa a 3.0

- Trabajo histórico preservado en un bundle, patches binarios y manifiestos SHA-256 externos antes de retirar worktrees o ramas integradas.
- Quedan dos worktrees necesarios: el checkout operativo y el directorio técnico que aloja la administración Git compartida; ambos limpios al cierre.
- `.local-evidence` fue compactada de 7.823,52 MB a evidencia útil verificable; discos de emulador, APK técnicas repetidas y datos crudos quedaron inventariados fuera del repositorio.
- Outputs y cachés regenerables fueron retirados después de una batería fresca verde.
- Código productivo, Room, Copias, permisos, dependencias, versión, firma y publicación no cambiaron.
- Evidencia: `docs/audits/2026-09-12-higiene-previa-miguardia-3.md`.

## Límites y puertas

- La creación del workspace no cambia código productivo, `versionName`, `versionCode`, Room, Copias, permisos, firma ni publicación.
- Las validaciones de este bloque son locales: no constituyen instrumentación, instalación, firma ni publicación nuevas.
- Samsung, emuladores, producción, servicios remotos y claves quedaron fuera del alcance.
- Commit, push, tag, firma y publicación requieren autorización específica para su próximo alcance.
- ADR y contratos históricos siguen como antecedentes; no reemplazan este estado ni autorizan reabrir el piloto beta retirado por ADR 0041.
