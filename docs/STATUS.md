# Estado actual de MiGuardia 3.0

<!-- Fuente operativa única. El historial y la evidencia fechada viven en docs/audits/. -->

- Bloque autorizado: STAGE_1_DETALLE_EDICION_Y_COMPOSICION_DEL_DIA_3_0
- Estado: CERRADO
- Contrato: docs/prompts/STAGE_1_DETALLE_EDICION_Y_COMPOSICION_DEL_DIA_3_0.md
- Base verificada: cb83d85552683a0f47f0f4f14f4f43a326fa76a1
- Procedencia: snapshot funcional `a771d78e3ec33bd2d3ef02bd1ae310514359e004` del repositorio 2.x, importado como raíz nueva sin arrastrar su historial.
- Actualización: 2026-09-14

## Producto de referencia

- Versión pública: MiGuardia `2.0.2`, `versionCode 6`.
- Distribución: APK firmada desde `blackatsystems.com/miguardia`; publicación Sites v15 y descarga remota verificadas en el cierre de 2.0.2.
- Modelo: Android local-first, sin cuenta laboral, nube laboral, analítica, telemetría ni activación beta.
- Datos: Room V6, 27 tablas y seis esquemas preservados; Copias mantiene 17 preferencias portables.
- Candidato 3.0: Room V7, 28 tablas y siete esquemas; agrega adjuntos privados de Notas y eleva Copias a V7 sin retirar lectura V5/V6.
- Actualizaciones: consulta pública consciente, verificación de APK y guardado manual mediante SAF.
- Firma: clave permanente y respaldo fuera de Git; no regenerar. Cada nueva versión requiere firma y QA de actualización proporcionales.
- Soporte y privacidad: `blackat.systems@gmail.com`.

La auditoría vigente de la publicación es `docs/audits/2026-09-10-franco-arrastre-nocturno.md`. La higiene de este bloque no agrega evidencia de instalación o actualización física de `versionCode 6`.

## Cursor de MAIN

- Etapa alcanzada: Stage 1 aceptado e integrado como base funcional de MiGuardia 3.0.
- Corrección aceptada: Retén contextual conserva etiqueta por ventana y no
  modifica la configuración laboral presente ni futura; sin nueva tabla Room.
- Siguiente etapa propuesta: Stage 2 — situaciones laborales y estados del día.
- Bloque funcional 3.0 autorizado: ninguno.
- Próxima tarea: Joaquín revisa y, si corresponde, autoriza
  `docs/prompts/STAGE_2_SITUACIONES_LABORALES_Y_ESTADOS_DEL_DIA_3_0.md`.
- Handoff de inicialización: `docs/HANDOFF_A_MAIN_3_0.md`.
- Cierre local del candidato: `docs/audits/2026-09-12-stage-1-detalle-edicion-dia.md`.
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
- Samsung `SM-S938B`/API 36 autorizado: variante QA y runners técnicos
  instalados; 108 pruebas dirigidas pasaron con datos sintéticos. El paquete
  público permaneció instalado y fuera de instrumentación.
- Un nuevo commit, push, tag, firma o publicación requiere autorización específica
  para su próximo alcance. El cierre autorizado de Stage 1 no habilita Stage 2.
- Stage 1 tiene evidencia JVM/build local, instrumentación real QA y recorrido
  manual de Photo Picker, Maps, Retén y temas/zoom. Un crash de miniatura
  reciclada hallado en esa sesión fue corregido y regresionado; las clases
  afectadas pasaron 15/15.
- TalkBack se habilitó temporalmente con autorización. Se verificaron el servicio,
  la semántica y actividad real del canal TTS de accesibilidad con MiGuardia QA
  en primer plano; la frase exacta escuchada no es observable remotamente. La
  configuración previa de accesibilidad y Alarmy se restauró y los paquetes QA
  se desinstalaron. Sólo permanece el paquete público, sin modificar.
- La evidencia no acredita actualización del paquete público desde 2.0.2 ni
  restauración de una copia personal.
- ADR y contratos históricos siguen como antecedentes; no reemplazan este estado ni autorizan reabrir el piloto beta retirado por ADR 0041.
