# Stage 4 — handoff a MAIN

## Revisión física posterior de MAIN

QA instalada con autorización de Joaquín. El ensayo posterior encontró fallos de
calendario con zoom alto y del último paso de Ayuda; rendimiento sin comparación
completa. Evidencia y devolución: [QA MAIN](audits/2026-10-02-stage4-qa-main.md).
Estado operativo en STATUS. La entrega técnica siguiente conserva el estado y
los recibos anteriores a esa instalación; no reemplaza esta revisión posterior.

## Decisión

Recibir un **candidato con cierre parcial**. F01–F07 tienen implementación;
no declarar fluidez integral, aceptación Stage 3 ni distribución mientras falten
la matriz física y R01/R02 medidos. Joaquín autorizó Stage 4 expresamente.
No se instaló la QA nueva: el teléfono estaba conectado pero su disponibilidad
quedó sin confirmar.

## Delta y propiedad

Base `932eaeb60c6b6e68975534788e3e6501c967b1ae` más checkout recibido; nunca tomar
ese SHA como Stage 3.2 completo. Rama `codex/miguardia-3.0`, sin staging/commit/push.
Entrada intacta en `.local-evidence/stage4/entry/`: manifiesto, copias y patch.

- Navegación: MiGuardiaApp mantiene el origen de configuración/Horas mientras
  muestra un hijo, con estado guardable y un solo dueño visible de Atrás.
- Presentación: Ayuda real, cifras primero en Resumen, privacidad/activación
  primero en Clima, cierres accesibles, acciones y semántica coherentes.
- Imágenes/notas: política común de selección completa, miniaturas privadas,
  borrador identificable, compensación de archivos propios, identidad estable
  ante confirmación perdida y exclusión compartida con restore.
- Limpiar: cuatro lecturas fragmentadas en lotes de 900 con orden global
  restituido; no se cambia CAS, atomicidad ni validación del lote.
- Pruebas y catálogo: recorridos Activity, matriz geométrica, medición,
  adversos SQL/archivos y regresiones JVM. Los runners compilados no cuentan
  como ejecutados en Android.

Room V8, lectura Copias V5–V8, reglas laborales, dependencias, permisos,
versionado/applicationId y firma permanecen sin cambios propios. El repositorio
2.0 y los históricos se mantuvieron de sólo lectura. MAIN integró los archivos
compartidos; especialistas tuvieron archivos disjuntos y revisión independiente.

## Evidencia

Fuente detallada: [auditoría del candidato](audits/2026-10-02-stage4-fluidez-integridad-candidato.md).
Preparación de QA: [33 recorridos](audits/2026-10-02-stage4-matriz-recorridos.md)
y [protocolo de rendimiento](audits/2026-10-02-stage4-rendimiento.md).

Validación final: **916/916 JVM + 26/26 interoperabilidad**, sin fallos/errores/
omitidas; lint/build Debug/QA/Release unsigned y ambos runners aprobados.
Recibo `.local-evidence/optimization/20261002T211459.258318Z/result.json`, con
574 insumos Android y 38 website estables, `changed_inputs=[]`. Lint conserva
24 advertencias, sin reglas relajadas. Documentos, catálogo (114 clases/24 grupos)
y diff aprobados. Delta propio: **46 archivos**, preservado con manifiesto/patch
en `.local-evidence/stage4/final/`; sin secretos o APK rastreables detectados.

QA final: `587ab6f6e4c1478dac2ebee37fc5d62b755057ed5202adda600ec718b556ecec`.
Runner app: `1dd8d07dcb88269e02bff08fd651ad01eadf0fbe0f17cc76270a600e0352f26e`.
Runner base: `9d4be8ff629df0bef020936c6f1064f1bb680eb9a5b1766e2a9654c026bbcfce`.
**Compilados y preservados, no instalados ni instrumentados en esta etapa.**

Los adversos de duplicación y carrera restore/archivos están preservados junto
con sus regresiones. R03 reproduce el límite SQL configurado en el host; todavía
no mide el límite de SQLite del Samsung. Los recibos finales integrados deben
leerse con sus hashes y `changed_inputs`, no sólo con el total de pruebas.

## Próxima acción de MAIN

1. Confirmar teléfono disponible y QA ficticia; cotejar los SHA del producto y
   runner a instalar con el recibo del candidato final. La QA anterior continúa
   siendo otro artefacto. No reutilizar sus recibos ni accionar app pública.
2. Ejecutar baseline/candidato con el mismo driver y datos: navegación directa
   y desde padre, recreación/rotación, imágenes y consumidores afectados.
   `stage4-flows`, `stage4-calendar-matrix` y `stage4-note-restore` están
   clasificados en `tools/qa-groups.json`; el runner de base cubre R03.
3. Conservar fallos y capturas comparativas seguras; registrar todas las filas
   EJECUTADA/PENDIENTE/FALLÓ. Medir R01 y R02 según el protocolo antes de decidir
   optimizaciones o aceptar restricciones de ventana.
4. Completar revisión humana, teclado/TalkBack y selector OEM en sesión
   aplicable. Permisos externos, biometría, compartir y restauración de datos
   personales no se deducen de una fila de prueba; usar dobles o fixtures
   aisladas donde corresponda.

La aceptación visual de Joaquín, la aceptación global de Stage 3 y cualquier
firma/publicación siguen siendo puertas independientes. No hay tarea visible,
rama o worktree nuevo, ni compromiso de publicación.
