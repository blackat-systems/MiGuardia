# Stage 12 — Preparación, inventario y revisión visual inicial

## Resultado y límites

Pedido de Joaquín: listar todas las capacidades mínimas del Calendario, generales,
combinadas y del Widget; revisar la interfaz y preparar un prompt exhaustivo,
técnico, sin ambigüedades, con bugs, soluciones y repetición hasta un cierre válido.

Se preparó `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`:
100 puntos de Calendario, 286 de otras familias, 24 de Widget y 15 recorridos
combinados: **425 puntos funcionales**, más **16 criterios visuales**. No son
441 pruebas ejecutadas ni 425 funciones independientes de negocio. Todo empieza
SIN_EJECUTAR; las referencias a pruebas sólo identifican material existente.

Los tres revisores internos inventariaron fuentes y aserciones en lectura; MAIN
consolidó el contrato. No se abrió un chat Stage12, no se implementó producto ni
se lanzó la batería integral durante esta preparación. La ejecución se activará
como bloque propio desde MAIN. No se pide reinterpretar autorizaciones antiguas.

Repositorio MiGuardia-3.0, rama codex/miguardia-3.0, HEAD
932eaeb60c6b6e68975534788e3e6501c967b1ae, un worktree, staging vacío, remoto propio.
La entrada sucia heredada se preservó. Delta propio: contrato, esta auditoría,
cursor/índice y helpers/evidencia ignorados bajo .local-evidence/stage12/preparation/.
Sin cambios ejecutables de producto, Gradle/dependencias, Room, permisos o Copias.

## Inspección visual realizada de verdad

QA682e instalada y ejecutor6018, Samsung SM-S938B/API36/[serial QA en binding local privado].
No se instaló otra candidata para inventariar. La revisión inicial comprende
**siete PNG limpios** de la misma candidata: seis del cierre inmediatamente
anterior y uno nuevo de Resumen. No se presenta el corpus Stage6 como imagen del
APK actual ni se llama nueva QA integral al reaprovechamiento de esos seis PNG.

| Archivo bajo .local-evidence/ | Estado observado | Procedencia |
|---|---|---|
| day-detail-weather-20261023/capture-20261023T164754.683645Z.png | Calendario mensual y jornada única sin expansión repetida | Cierre anterior, QA682e |
| day-detail-weather-20261023/capture-20261023T164759.913833Z.png | Hoy abierto, tarjetas y acciones del día | Cierre anterior, QA682e |
| day-detail-weather-20261023/capture-20261023T164827.129932Z.png | Clima sin ubicación; diálogo con alto sobrante | Cierre anterior, QA682e |
| day-detail-weather-20261023/capture-20261023T164857.429568Z.png | Retorno al mismo día tras cerrar Clima | Cierre anterior, QA682e |
| day-detail-weather-20261023/capture-20261023T164904.361730Z.png | Jornada próxima nocturna, horario sin final civil visible | Cierre anterior, QA682e |
| day-detail-weather-20261023/capture-20261023T164925.017131Z.png | Calendario final sin hoja o selección | Cierre anterior, QA682e |
| stage12/preparation/capture-20261023T165824.994504Z.png | Resumen: títulos, cifras con minutos y acceso a personalización | Captura nueva de preparación |

Observado: coherencia de paleta oscura y jerarquía básica, mes completo, cifras
de Resumen junto a sus unidades, detalle estable y controles visibles en esta
muestra. No medí aquí contraste/targets ni acepté intuición humana. La muestra
no cubre toda la app, claro/Sistema, texto grande, paisaje, teclado, selectores,
TalkBack, Informes, Copias ni Widget OEM. El atlas completo es una obligación
del Stage12, no un resultado de esta preparación.

## Hallazgos demostrados y soluciones propuestas

1. **S12-B01, presentación:** Clima usa dentro de Dialog un contenedor común que
   fuerza fillMaxSize y weight(1f), aun sin mensaje. Fuente y PNG confirman la
   causa del hueco inferior. Solución: política opt-in envolvente sólo en Clima,
   conservando defaults de destinos completos, límite seguro, scroll largo y
   Cerrar accesible. Cambiar sólo reserveSpace=false no resuelve la causa.
2. **S12-B02, dato temporal engañoso:** Widget compone fecha dueña y horas sin
   fecha final. Ventanas válidas de 1 h y 25 h pueden tener texto idéntico.
   La disponibilidad reanudada puede empezar otro día y conservar fecha dueña:
   el Widget muestra entonces fecha/hora de fuentes distintas. Solución: usar
   fechas civiles reales de start/end en zoneId, +N por ChronoUnit.DAYS y fecha
   inicial real para presentación; mantener owner exclusivamente para navegación.
   Barrer consumidores equivalentes, sin cambiar dominio ni filtrar privacidad.
3. **S12-B03, arnés:** WidgetConfigurationActivityTest repara bloqueo, limpia cola
   y crea host; otras clases preparan fixture global. No se ejecutaron aquí.
   Solución: revisar reglas/Application/proveedores y aislar Context, Room,
   DataStore, archivos, colas e IDs antes de correr cada clase.
4. **S12-B04, captura:** foco QA no impide una notificación de otra app superpuesta.
   Una captura completa la incluyó. El archivo local se eliminó de forma puntual
   y quedó excluido; no se transcribe ni se entrega ese contenido. El historial
   del chat no se elimina al borrar el archivo local. Registro de exclusión:
   .local-evidence/stage12/preparation/excluded-capture.json.
   Solución obligatoria previa a más fotos: captura de ventana/nodo QA, no captura
   completa recortada después; probar overlay sintético sin cambiar ajustes globales.

Los dos primeros son defectos de presentación/dato temporal. Los otros dos son
controles de seguridad del ensayo, no nuevas acusaciones de bugs productivos.
El contrato contiene reproducción, fuentes, solución causal, consumidores,
regresiones rojas/verdes y condiciones de aceptación de cada uno.

## Evidencia, conservación y relevo

Primer intento de abrir QA detenido por foco ajeno antes de interactuar:
open-20261023T165654.561667Z.json. Joaquín confirmó luego que dejaba QA visible.
Se validaron paquetes/hashes, se abrió menú y Resumen sin guardar personalización.
Se volvió a Calendario. Las once huellas de los estados de entrada/salida de
preparación permanecieron iguales, incluidas tablas y preferencias controladas:
status-20261023T165745.745088Z.json y status-20261023T165945.345809Z.json.
No se borró/resembró QA ni se tocaron pública o ajustes globales.
La incidencia de notificación accidental se registra arriba sin ocultarla.

Joaquín preguntó si podía usar el teléfono y MAIN lo liberó expresamente.
**No más ADB/capturas/operación Samsung** hasta nueva confirmación de disponibilidad.
Se continúa sólo con documentos y validaciones locales de preparación.

El prompt exige resultados por ID/subcaso/ronda, adversos conservados, causa antes
de corregir, aislamiento real, y dos rondas completas sobre la misma candidata
final. Bloqueados/omitidos no son verdes. La aprobación estética/humana,
pronóstico real, OEM, Resumen/R4 y distribución mantienen sus puertas explícitas.

Próximo dueño: MAIN activa el contrato en un chat Stage12 propio cuando Joaquín
indique iniciar; el especialista entrega plan/aislamiento para revisión antes
de cualquier escritura/Android. Esta preparación no certifica toda MiGuardia.

## Revisión y validación de la preparación

Dos revisores de sólo lectura contrastaron B01/B02 y las guardas del contrato.
Se corrigieron tres ambigüedades reales antes de entregar: preparación no equivale
a autorización de ejecutar; salida externa prevista se distingue de foco ajeno
inesperado; estado inicial unificado SIN_EJECUTAR, sin una variante fuera del enum.
MAIN corrigió también una referencia equivocada del inventario preliminar al pin
de FinalQaReadOnlyReviewTest: la fuente actual exige682e, no494a.

Comprobación mecánica: 441 IDs únicos y consecutivos, 425 funcionales/recorridos
y 16 visuales, 38 rutas productivas existentes. El contrato final tiene 1361
líneas y SHA-256 e2a64e7e08c231eaf6d370b0278507474497bdfb95083d20e5cbc7f955e7c95c.
De las 32 rutas selladas en el cierre anterior sólo cambiaron STATUS y el índice,
como estaba previsto; ningún cambio productivo, APK, prueba o evidencia heredada.

docs_check: verde, 39.992/40.000 caracteres de guía, 523 referencias, 42 ADR y
103 snapshots. git diff --check: salida0, advertencias heredadas de finales de
línea; staging vacío y HEAD/rama originales. No JVM/build por documentos ni
ejecución de Stage12. Recibo/manifiesto: .local-evidence/stage12/preparation/final-review.json.
