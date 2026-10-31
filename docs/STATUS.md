# Estado actual de MiGuardia 3.0

<!-- Fuente operativa única. El historial y la evidencia fechada viven en docs/audits/. -->

- Bloque autorizado: STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0
- Estado: EN_CURSO
- Contrato: docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md
- Base verificada: 932eaeb60c6b6e68975534788e3e6501c967b1ae
- Procedencia: snapshot funcional `a771d78e3ec33bd2d3ef02bd1ae310514359e004` del repositorio 2.x, importado como raíz nueva sin arrastrar su historial.
- Actualización: 2026-10-31

## Plan maestro y Samsung — 31/10/2026

Joaquín habilitó Samsung para todas las pruebas QA; reemplaza «sólo humano» del
26/10. Rigen identidad/aislamiento y límites de producción, datos y distribución.
[Plan maestro](PLAN_MAESTRO_CIERRE_3_0.md): ejecución pedida con joa-main-orchestrator;
A: dos Android verdes. B1: C090 fallido auditado; UI sin iniciar. Rondas: 0/2.
[Checkpoint Git](audits/2026-10-31-checkpoint-github.md): cese hasta reanudación.

## Producto de referencia

- Versión pública: MiGuardia `2.0.2`, `versionCode 6`.
- Distribución: APK firmada desde `blackatsystems.com/miguardia`; publicación Sites v15 y descarga remota verificadas en el cierre de 2.0.2.
- Modelo: Android local-first, sin cuenta laboral, nube laboral, analítica, telemetría ni activación beta.
- Datos: Room V6, 27 tablas y seis esquemas preservados; Copias mantiene 17 preferencias portables.
- Candidato Stage 2: Room V8, 29 tablas y ocho esquemas; agrega situaciones de jornada y evidencia privada opcional, y eleva Copias a V8 sin retirar lectura V5/V6/V7.
- Actualizaciones: consulta pública consciente, verificación de APK y guardado manual mediante SAF.
- Firma: clave permanente y respaldo fuera de Git; no regenerar. Cada nueva versión requiere firma y QA de actualización proporcionales.
- Soporte y privacidad: `blackat.systems@gmail.com`.

Publicación: `docs/audits/2026-09-10-franco-arrastre-nocturno.md`.
Este estado no acredita nueva instalación/actualización física de `versionCode 6`.

## Cursor de MAIN

- Stage8 auditado: 432 muestras; guardado sin alerta, Resumen +29,85 % en un par,
  causa INCONCLUSA. [Revisión](audits/2026-10-22-stage8-revision-main-cierre.md).
- Stage10 auditado: datos conservados, AVD respaldado/apagado; R2 original
  incompleto por reposición adicional, causa APK ausente desconocida.
  [Revisión](audits/2026-10-23-stage10-revision-main-recuperacion-r2.md).
- Stage11 auditado: ReplaceAll corregido; 35/35 Android, datos conservados,
  payload 1331/1331 íntegro; emulador apagado. [Revisión](audits/2026-10-23-stage11-revision-main.md).
- Corrección682e instalada: 981 JVM y 30/30 Android verdes; detalle estable,
  jornada única sin duplicación. Clima sin ubicación: límite explícito; datos conservados.
  [Evidencia y relevo](audits/2026-10-23-detalle-clima-jornada-unica.md).
- Stage12: r9 cerrado; rol consola pendiente sólo para esa ruta de emulador.
  Chat `01a169e5-d914-7e33-8926-327a354301b3`; dos métodos auditados; rondas 0/2.
  Resumen 13 y 40 auditadas. [Revisión](audits/2026-10-24-stage12-revision-main-g3-r2.md).
- Pendientes: aceptación humana, Resumen, OEM y entrega pública; sin aceptación inferida.
- Skill adoptada: revisión en checkpoint. Push autorizado; sin distribución.

## Ruta 3.0 conciliada — fuente para el diagrama

Estados al 23/10, sin renumerar ni reasignar recibos.
La imagen `docs/diagrams/miguardia-3-ruta-pendiente-2026-10-02.png` es histórica;
sin editable localizado. El visual vigente se deriva de esta tabla.

| Stage / capacidad | Implementado y comprobado | Pendiente real / dependencia | Fuente |
|---|---|---|---|
| 1 · Detalle y edición | Aceptado e integrado históricamente el 14/09 | No es aceptación nueva ni permiso de distribución | [Contrato](prompts/STAGE_1_DETALLE_EDICION_Y_COMPOSICION_DEL_DIA_3_0.md) |
| 2 · Situaciones del día | Aceptado el 15/09; Room V8 y QA sintética | Upgrade público pendiente; restore sintético cerrado en 11 | [Handoff](HANDOFF_STAGE_2_A_MAIN_3_0.md) |
| 3 / 3.1 / 3.2 · Calendario y carga | Candidato técnico probado por Joaquín; mes completo, acciones contextuales, catálogo y borrador conservado | Aceptación global pendiente sobre candidata vigente; no rehacer arreglos cerrados | [Handoff](HANDOFF_STAGE_3_A_MAIN_3_0.md), [3.2](audits/2026-10-02-stage-3-2-carga-borrador.md) |
| 4 · Fluidez e integridad | F01–F07; notas duplicadas, concurrencia de archivos y límites SQL corregidos; QA dirigida | Cierre parcial heredado por 5–7, no otra implementación 4 | [QA MAIN](audits/2026-10-02-stage4-qa-main.md) |
| 5 · Cierre de recorridos | Base parcial ejecutada, no sólo preparada; Ayuda/restauración, aviso bajo, Clima, voz/imagen OEM y QA dirigida | Revisión humana y subcasos integrales según candidata; rendimiento continúa en 7–8 | [Handoff](HANDOFF_STAGE_5_A_MAIN_3_0.md), [continuación](audits/2026-10-10-stage5-avisos-y-restauracion.md) |
| 6 · Registro visual nuevo | 41 capturas y atlas entregados; corpus anterior OBSOLETO | No reactivarlo ni tratarlo como QA integral | [Registro nuevo](HANDOFF_STAGE_6_RELEVAMIENTO_NUEVO_A_MAIN_3_0.md) |
| 7 · Vigilia integral | Candidata R4; 935 JVM, 56 métodos físicos y matriz mensual 48/48; paisaje, hoja e Informes corregidos | Aceptación visual/global; Resumen continúa en 8, no distribución | [QA MAIN](audits/2026-10-11-stage7-qa-main.md), [checkpoint](audits/2026-10-11-stage7-checkpoint-letra-android.md) |
| 8 · Diagnóstico de rendimiento | q3 y comparación A-B-B-A auditados; 432 muestras, guardado sin alerta | Campaña cerrada, causa Resumen inconclusa: alerta en un par; no aceptación global ni optimización | [Revisión MAIN](audits/2026-10-22-stage8-revision-main-cierre.md) |
| 9 · Conservación V6→V8 | Auditado LOCAL con R1: 952 JVM/104 XML, 590 insumos, seis artefactos; MAIN repitió 54 Python y tres probes detectados | Clase propia sólo compilada; evidencia Android posterior en 10/11, no upgrade público | [Revisión MAIN](audits/2026-10-21-stage9-revision-main.md) |
| 10 · Actualización QA conservando datos | Recuperación auditada: 27 tablas/40 filas/17 preferencias, foto y RAW conservados | R2 original incompleto; no prueba actualización pública/Samsung | [Revisión MAIN](audits/2026-10-23-stage10-revision-main-recuperacion-r2.md) |
| 11 · Restauración de Copias | R1 auditada: 35/35 Android, imágenes y conservación comprobadas | OEM/humano/Samsung y entrega pública separados | [Revisión MAIN](audits/2026-10-23-stage11-revision-main.md) |
| 12 · Verificación integral | Local y diagnóstico Samsung auditados | Diagnóstico Samsung parcial; rondas0/2 | [Contrato](prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md) |

Dependencias de cierre; Stage8 y recuperación Stage10 ya auditados:

1. Salida local 9 → recuperación 10 auditada; su ensayo original incompleto no
   prueba upgrade público. No repetir campañas cerradas para ocultar ese límite.
2. Stage11 comprobó restore Android sintético; no acredita SAF OEM, Samsung,
   muerte real de proceso, aceptación humana o actualización pública.
3. Rendimiento: comparación ejecutada y auditada; pendiente causal de Resumen.
   Otra investigación/corrección necesita alcance propio. ART no acredita E2E;
   no compartir teléfono/Gradle ni repetir la campaña cerrada.
4. Candidata estable y evidencia pertinente → revisión visual/funcional de
   Joaquín sobre Vigilia y pendientes reales heredados → aceptación o corrección.
5. Con esos gates resueltos: preparar entrega y verificar compatibilidad,
   privacidad y firma bajo contrato específico; publicar sólo con autorización aparte.

Un stage = una tarea real. MAIN encadena sólo con autorización y respeta gates
de producto/datos/costos/permisos, sin numeración artificial. `docs/WORKFLOW.md`.

Vigentes: mes completo sin scroll mensual, semana forzada ni barra permanente;
toque abre detalle y pulsación larga selecciona; un dueño de Atrás; Retén
contextual, Clima consciente, oscuro inicial, letra Android sin zoom interno
y TalkBack sin controles extra (ADR 0043). La ruta 24 etapas 2.0 es historia.

## Workspace

- Fuente del proyecto ChatGPT/Codex: `C:\Users\User\Desktop\chatgptprojects\MiGuardiaContainer`.
- Checkout activo 3.0: `MiGuardia-3.0/`, repositorio independiente y rama `codex/miguardia-3.0`.
- Remoto 3.0: `https://github.com/blackat-systems/MiGuardia.git`; `main` conserva la raíz inicial `3f1365259e15292a2f24e3361acd523213b49fad`.
- Referencia estable: `MiGuardia-2.0/`, perteneciente al repositorio 2.x archivado; sólo lectura salvo corrección 2.x autorizada.
- Administración histórica: `Repositorio-Git/` pertenece al repositorio 2.x archivado; no comparte objetos ni referencias con 3.0 y no debe usarse como directorio ordinario de desarrollo.
- Rescates históricos: `Archivo-recuperacion/`; sólo lectura.

## Higiene previa a 3.0

Higiene integrada con histórico externo preservado, sin cambios de producto.
Inventario y validación: `docs/audits/2026-09-12-higiene-previa-miguardia-3.md`.

## Antecedentes de validación y puertas

Estos antecedentes no prueban disponibilidad, APK actual ni orden de ejecución.
Rige el cursor anterior.

- Las pruebas, adversos y correcciones de Stages 1–3 permanecen en las fuentes
  enlazadas en la tabla anterior. Sus recibos no acreditan el APK ni dispositivo
  actuales; no reabrir arreglos cerrados ni ocultar fallos históricos.
- Hashes, capturas e incidencias históricas: handoffs/auditorías enlazados, no
  disponibilidad actual. Zoom sustituido por ADR 0043.
- No hay evidencia nueva de actualización del paquete público 2.0.2 ni restore
  de una copia personal. La compilación/mecánica host de Stage 9 no lo acredita.
- Aceptaciones 1/2 no autorizan distribución ni aceptan globalmente 3–9. Firma,
  publicación, datos o ensayos nuevos conservan sus gates y guardas específicos.
  No commit/push/tag nuevos en este cierre; checkout acumulado y adversos intactos.
- ADR/contratos antiguos no autorizan reabrir beta, cupos o feedback obligatorio
  retirados por ADR 0041. `docs/ORQUESTACION_MAIN.md` conserva historia 2.0.
