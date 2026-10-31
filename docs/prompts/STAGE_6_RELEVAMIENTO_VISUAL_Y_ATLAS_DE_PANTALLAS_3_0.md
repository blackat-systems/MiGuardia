# Stage 6 — Registro visual nuevo para mejorar la UI

Contrato vigente ajustado por instrucción directa de Joaquín el 2026-10-11.
Reemplaza el relevamiento anterior. Autorización y cursor: `docs/STATUS.md`.

## Objetivo

Registrar cómo se ve hoy MiGuardia 3.0 y entregar una base visual confiable para
mejorar estética, claridad y facilidad de uso. Arrancar desde cero: no consumir
capturas, inventario, matriz, conclusiones ni diagnóstico de la entrega anterior.
Se conserva ese material con marca OBSOLETO, sin borrarlo ni alterar sus originales.

El relevamiento no es una batería exhaustiva de QA. Cubrir las pantallas principales,
los submenús y formularios que cambian la composición y algunos estados representativos.
No multiplicar recorridos por todas las fechas, errores, combinaciones o dispositivos.
No diseñar ni implementar todavía: la elección de temática corresponde a Joaquín
sobre propuestas posteriores.

## Entrada y seguridad

- Leer AGENTS del contenedor/repo, STATUS y ruta pertinente.
- Puerta 0: checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`, remoto MiGuardia, un worktree,
  staging vacío y trabajo acumulado preservado. Detener cambios sin dueño.
- Samsung libre y desbloqueado, autorizado por Joaquín en este chat.
- Exclusivamente `com.blackatsystems.miguardia.qa`; candidata QA SHA-256
  `6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`.
- Sólo datos ficticios recibidos/reconocibles. No modificar sector/configuración
  laboral ni borrar o reemplazar registros para fabricar estados visuales.
- Verificar foco QA antes de interactuar/capturar. Ante ventana ajena, detener
  antes de leer contenido o guardar imágenes. No enumerar galería/documentos,
  cuentas, Recientes o launcher personal. No registrar credenciales. FLAG_SECURE
  se respeta; una superficie protegida queda explicitada sin eludirla.
- Pública fuera del recorrido; sólo metadatos/hash para preservación. No instalar
  por rutina, activar clima/ubicación/red/avisos, descargar o abrir enlaces externos.

## Recorrido proporcional

Inventariar desde la navegación y el código vigentes: Calendario, detalle,
selección, carga y catálogo, trabajo/horas/extras/disponibilidad/repetición,
situaciones/feriados/vacaciones, notas/fotos, Resumen/Informes, avisos/Clima,
Widget, Copias/bloqueo, Apariencia/Ayuda y Actualizaciones en reposo.

Mostrar tramos con contenido diferente y formularios/confirmaciones representativos.
La cobertura principal será Samsung/retrato/Claro/zoom100, comprobados en la app.
Usar preview y cancelar cuando una acción implicaría pérdidas o cambios innecesarios.
Los estados que requieren datos, autenticación, servicios o selectores inseguros se
registran como límites; no impiden terminar el registro visual representativo.
Si un contraste de tema/zoom/paisaje agrega evidencia visual útil, hacerlo de modo
acotado y restituir ajustes propios. No consultar ni modificar escala/fuente Android.

## Entrega y frontera de escritura

1. Atlas nuevo offline, agrupado y navegable, con PNG originales reales sin retoque,
   títulos/rutas, acceso a tamaño completo y manifest SHA-256 por imagen. Registrar
   fecha, APK, dispositivo/API, viewport, tema/zoom, procedencia ficticia y origen.
2. Cobertura representativa simple: qué se vio, imágenes y límites concretos.
   Sin heredar cobertura anterior ni imponer los 273 estados del intento descartado.
3. Diagnóstico nuevo con ejemplos: defecto visible, dificultad inferida o preferencia
   estética. Explicar impacto y objetivo de mejora; también preservar aciertos.
   No afirmar contraste/dp/fluidez o comprensión humana a partir de imágenes.
4. Handoff nuevo a MAIN y entrada para comparar hasta tres temáticas sobre 4–6
   pantallas iguales; conservar dominio, privacidad, accesibilidad y zoom.
5. Marcar la entrega anterior OBSOLETA, mantener su contenido y sellos como historia.

Escribir sólo evidencia/helpers nuevos ignorados bajo `.local-evidence/stage6/`,
auditoría nueva, handoff nuevo, marcas documentales de obsolescencia, contrato,
índice y STATUS. No cambiar producto, pruebas, Gradle, Room, permisos, dependencias,
versiones, firma, 2.x o rescates. Sin commit/push/tag/publicación ni nuevos chats.

## Verificación y cierre

Revisar visualmente cada original nuevo y su privacidad; comprobar hashes, imágenes,
enlaces y uso offline del atlas. Revisar diff propio y ausencia de cambios ejecutables.
Ejecutar `python tools/docs_check.py` y `git diff --check`; sin Gradle/JVM por ritual.
Dejar QA en una pantalla segura y restaurar sólo ajustes temporales propios.

Terminado cuando el nuevo registro representativo es usable, el diagnóstico se
sostiene en sus imágenes y MAIN recibe evidencia/limitaciones y siguiente paso.
No equivale a aceptación del diseño, cierre de Stage 5, QA integral o distribución.
