# Auditoría general de fluidez e integridad de MiGuardia 3.0

Fecha: 2026-10-02. Revisión MAIN posterior a Stage 3.2, solicitada por Joaquín.
El estado operativo vive en `docs/STATUS.md`.

## Conclusión y alcance

La base mantiene sus 900 pruebas JVM verdes en una ejecución fresca. La revisión
detectó incoherencias de navegación, ayuda e imágenes, y problemas de jerarquía
visual. No demostró pérdida de datos ni un fallo transaccional en los recorridos
inspeccionados. Tampoco demuestra que toda la app sea fluida: faltan mediciones
de rendimiento, matriz física completa, TalkBack y selector OEM.

Se propone una unidad completa, Stage 4 — Fluidez e integridad, para corregir
estos recorridos, medirlos y entregar evidencia de los consumidores afectados.
No se modificó producto en esta auditoría. Preparar el contrato no acepta Stage 3
ni autoriza ejecutar Stage 4, instalar otro candidato o distribuirlo.

## Entrada y preservación

- Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio independiente: `MiGuardia-3.0`, `.git` es un directorio propio;
  el contenedor no tiene `.git`.
- Rama: `codex/miguardia-3.0`; HEAD y upstream observado:
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`; un worktree.
- Entrada: 83 archivos modificados, una eliminación y 37 no rastreados;
  ninguno preparado en staging. Es el delta acumulado recibido, no propio.
- Las 13 entradas `own_files` de `.local-evidence/stage32-final-manifest.json`
  coinciden con los hashes actuales antes de añadir la documentación de esta
  auditoría. La comprobación no convierte todo el checkout acumulado en aceptado.
- No se escribió en 2.0, no se instaló, firmó, publicó ni creó commit/ramas.

## Evidencia nueva y límites

### Pruebas locales

Ejecutado `python tools/verify.py --profile local --fresh-tests` con el JBR
configurado del proyecto. Recibo:
`.local-evidence/optimization/20261002T193052.711888Z/result.json`.

| Módulo | Ejecutadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| Dominio | 395 | 0 | 0 | 0 |
| Base | 19 | 0 | 0 | 0 |
| App | 486 | 0 | 0 | 0 |
| Total | 900 | 0 | 0 | 0 |

Cero insumos cambiados durante la corrida. No equivale a instrumentación Room,
interoperabilidad web, rendimiento ni accesibilidad física. Catálogo comprobado:
111 clases, 21 grupos, válido. No se repitió el build por cambios documentales.

### Evidencia previa contrastada

Se leyeron los manifiestos Stage 3.2 siguientes, sin volver a ejecutar sus casos:

| Recibo bajo `.local-evidence/qa/` | Pasadas | Fallidas/omitidas/pendientes |
|---|---:|---|
| `20261002T190545.748024Z` | 34 | 0/0/0 |
| `20261002T190118.261471Z` | 8 | 0/0/0 |
| `20261002T190655.451270Z` | 8 | 0/0/0 |
| `20261002T190703.914715Z` | 5 | 0/0/0 |

Los 55 casos son evidencia anterior; cada manifiesto conserva su identidad de
artefactos. No se reasignan todos al mismo runner ni se cuentan como pruebas
nuevas de esta auditoría.

### Inspección física parcial

Samsung SM-S938B/API 36, exclusivamente variante QA y contenido ficticio.
Identidades comprobadas al iniciar la revisión:

- QA: `fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`.
- Runner: `d088ff91479988a7902c82cf3439aac7ed4b3a0a4b7dd06ded39581b8224c05a`.
- Paquete público: `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.

Se abrieron Calendario, carga pendiente/cancelación, menú, Resumen, Mi forma de
trabajar, opciones avanzadas, Horas, Avisos, Clima, Copias, Widget y Bloqueo.
Abrir una pantalla no comprueba todas sus acciones. No se concedieron permisos,
activaron servicios, exportaron/restauraron copias ni instrumentaron paquetes.
Se canceló conscientemente un borrador sintético de selección sin guardarlo;
no se ejecutaron altas ni eliminaciones de jornadas persistidas durante el recorrido.

Evidencia local: `.local-evidence/overall-audit/`, 20 pares PNG/XML.
Nombres fiables para hallazgos: `summary`, `weather`, `work-setup`,
`work-advanced`, `hours`, `hours-back`, `drawer-visible`, `drawer-bottom`,
`calendar-main-confirmed`. Los nombres iniciales `calendar` y `resumed`
representan el formulario pendiente; `calendar-main` muestra confirmación de
cancelación, no el Calendario. No usarlos como evidencia de otra superficie.

La revisión fue interrumpida por uso concurrente del teléfono. Tras autorización
para reanudar, volvió a cambiar el foco durante una captura. Se detuvo ADB y se
retiraron los dos archivos de esa captura ajena y los temporales del dispositivo;
su contenido no forma parte de esta auditoría. Se retiró también el helper local
creado para capturas: comprobar foco sólo antes de capturar no evita esa carrera.
Una continuación debe ser supervisada y validar paquete/foco antes y después,
sin conservar ni imprimir contenido ajeno. No se infiere permiso para revisar
otras apps. El cierre actual no acredita un nuevo hash público posterior a esa pausa.

## Hallazgos comprobados

Las prioridades son propuestas para ordenar Stage 4. P2 identifica una corrección
de coherencia/uso; P3 una mejora de presentación. No hay un P0/P1 demostrado.
Las rutas de código siguientes son relativas al repositorio; las líneas refieren
al candidato auditado y pueden moverse.

### F01 Ayuda describe una navegación anterior

P2, contradicción comprobada por código y menú observado. En
`app/src/main/java/com/blackatsystems/miguardia/ui/help/HelpScreens.kt:241`
se describen tres grupos antiguos; el menú actual tiene acciones del Calendario,
Tu trabajo, Avisos y contexto y Aplicación. En líneas 244–249 el recorrido habla
de tarjeta de hoy y de volver a hoy desde los controles del Calendario, aunque
Hoy vive en el menú. Las líneas 425–428 también omiten el paso por opciones
avanzadas de configuración laboral.

Impacto: la guía enseña un recorrido distinto al visible. Corregir textos,
anclas y accesibilidad del recorrido después de estabilizar navegación, sin
renombrar por suposición reglas de dominio. Probar cada paso contra controles reales.

### F02 El regreso desde Horas pierde el contexto de configuración

P2, comportamiento observado: Menú → Mi forma de trabajar → Opciones avanzadas
→ Tus horas y tu meta → Atrás vuelve al Calendario, no a configuración.
Capturas `work-advanced`, `hours`, `hours-back`.
`app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt:1442`
cierra configuración con `requestBack()` antes de abrir horas, repetición,
tipos de extra o disponibilidad. El caso físico comprobado es Horas; los otros
comparten patrón de código pero no se recorrieron todos físicamente.

Recomendación: regreso al origen real, preservando borrador, posición y contexto;
el acceso directo desde menú no debe inventar un padre inexistente. Conservar
el único dueño de Atrás para GENERAL/MANUAL_LOAD/HOLIDAY. No se demostró pérdida
de datos guardados por este comportamiento.

### F03 La política de imágenes cambia entre recorridos

P2, discrepancia comprobada por código, pendiente de reproducción OEM:
`ui/exceptions/ExceptionsViewModel.kt:426` toma sólo las imágenes que caben
y avisa del excedente; `ui/management/V2ManualShiftLoadViewModel.kt:475`
rechaza el lote completo cuando supera 30. Las rutas llevan el prefijo
`app/src/main/java/com/blackatsystems/miguardia/`.
Con 29 imágenes y selección de otras dos, un recorrido acepta una y otro ninguna.
No es un caso demostrado de pérdida: la primera ruta sí informa el rechazo parcial.

Además, `ui/management/UnifiedManualLoadForm.kt:133` anuncia el límite `/30`
en uso normal, contrario al pedido de conservarlo como límite interno.
Recomendación: política coherente, sin importación parcial sorpresiva; por defecto
rechazar el exceso antes de importar y permitir ajustar la selección, sin perder
adjuntos existentes. No anunciar el cupo preventivamente; el rechazo debe seguir
siendo comprensible, nunca silencioso. Mantener el límite y seguridad del almacenamiento.

### F04 Resumen posterga sus cifras principales

P2 de usabilidad, observado en `summary.png`. Tres botones de ancho completo
para Anterior/Hoy/Siguiente, exportación y una tarjeta introductoria preceden
a los totales. En este estado inicial las cifras aparecen cerca de la parte
inferior del primer viewport. No se afirma que estén inaccesibles: hay scroll.
Fuente: `app/src/main/java/com/blackatsystems/miguardia/ui/summary/SummaryScreen.kt:108`
y `SummaryMonthControls` en línea 223. Hay lenguaje técnico en líneas 486 y 572
(proyección inmutable y fecha dueña).

Recomendación: mes con controles compactos, cifras primero, configuración e informe
secundarios, textos cotidianos. Mantener detalle trazable, personalización y
significado exacto de las horas; no cambiar números para simplificar presentación.

### F05 Clima relega la acción de habilitación

P2 de usabilidad, observado en `weather.png`. El bloque de proveedor, privacidad,
licencia y condiciones ocupa casi todo el diálogo antes del control principal.
Fuente: `app/src/main/java/com/blackatsystems/miguardia/ui/weather/WeatherScreens.kt:158`.

Recomendación: explicación esencial breve y acción contextual visible; detalles
ampliables y atribución accesible. No esconder qué se transmite ni omitir el
consentimiento previo, y no cambiar proveedor, permisos o política de red.
Esto no es una evaluación legal de la licencia ni autorización comercial.

### F06 Los adjuntos del borrador carecen de identificación visual

P2/P3 de usabilidad, comprobado en código: `UnifiedManualLoadForm.kt:135`
ofrece únicamente Quitar imagen 1, 2, etc. El mismo bloque mantiene Agregar puesto
o función y la pregunta para agregar nota incluso cuando ya hay valores.

Recomendación: miniaturas privadas identificables, quitar cada imagen con nombre
accesible y acción clara; etiquetas que indiquen agregar/editar/contraer según
estado. No cargar imágenes completas en memoria para una miniatura ni exponer
rutas privadas. Preservar el orden de formulario aceptado en Stage 3.2.

### F07 Falta una jerarquía visual consistente entre superficies

P3, observación de diseño, no defecto de persistencia. Las capturas de menú,
Resumen, configuración, Clima y carga muestran botones de gran peso similar,
texto explicativo abundante y distintos cierres Volver/Cerrar. No todo uso de
estas palabras es incorrecto: un diálogo y una pantalla pueden tener semánticas
distintas. La corrección debe responder a cada recorrido, no reemplazar cadenas
globalmente. Proponer componentes existentes con una acción primaria por contexto,
secundarias discretas y destructivas diferenciadas. No crear un segundo menú ni
eliminar funciones para reducir altura.

## Riesgos que requieren pruebas antes de corregir

- R01 Legibilidad mensual densa. `CalendarScreen.kt:1099–1171` permite mínimos
  de 6–8 sp para distintos textos; eso está comprobado. No se reprodujo aquí un
  recorte en seis filas/paisaje/zoom alto con múltiples condiciones. Probarlo;
  preferir síntesis de información con detalle completo al tocar, no letras
  diminutas, semana forzada ni scroll mensual. No alterar datos para que entren.
- R02 Rendimiento con historia extensa. `RoomV2ShiftRepository.kt:73–85` y
  `core/database/src/main/java/com/blackatsystems/miguardia/core/database/validation/V2LocalDataIntegrity.kt:45` realiza validación
  amplia de catálogo/historia. Hay trabajo potencialmente costoso; no hay medición
  que demuestre un cuello de botella. Medir antes de cachear o retirar validaciones.
- R03 Muchas identidades en Limpiar. `CalendarClearOperation.kt:28–34` pasa
  colecciones completas a consultas IN de notas, horarios y avisos; ver
  `ShiftNoteAttachmentDao.kt:17` y `ShiftActualDao.kt:103`. Otras rutas usan
  `chunked` en `RoomV2ShiftRepository.kt:1313`. El límite depende del SQLite real;
  366 fechas no limita necesariamente el número de jornadas/notas. Probar lotes
  que superen 900 identidades y el límite efectivo; no afirmar que hoy falla.
- R04 Archivos privados frente a restauración concurrente. El coordinador manual
  usa `LocalDataMutationGate` y `NoteFileCommitGate` (`ManualLoadNotes.kt:72,85`);
  restauración usa el primero (`LocalBackupCoordinator.kt:542`). El editor de notas
  de detalle no muestra ese mismo contrato en su factory (`MainActivity.kt:314`).
  La navegación puede impedir concurrencia real: primero demostrar alcanzabilidad
  mediante prueba determinista. No presentar corrupción hipotética como comprobada.

## Integridad inspeccionada y cobertura restante

La revisión de código conservó, sin reescribir, estas defensas:

- `RoomV2ShiftRepository.kt:251–460`, dentro de `writeShiftData`/`withTransaction`
  (1335): revalida ocupación y asociados antes de eliminar; guarda jornadas/notas
  y excepciones de recurrencia en el lote. No convertir CAS en último escritor gana.
- `CalendarClearOperation.kt:94–128`: trabaja por fecha propietaria, divide
  rangos médicos/vacaciones y conserva segmentos no seleccionados.
- `ManualLoadNotes.kt:107–121`: consulta referencias antes de retirar archivos;
  ante incertidumbre conserva en vez de borrar a ciegas.
- `RoomShiftSituationRepository.kt:47–93`: situación y estado de jornada dentro
  de la misma transacción. COMPLETED sigue siendo derivado.
- `backup/BackupDatabaseGateway.kt:39–55`: captura y reemplazo con transacción
  y verificaciones; esto no prueba por sí solo un restore real de archivos/preferencias.

| Superficie | Comprobación en esta auditoría | Lo que no acredita |
|---|---|---|
| Calendario/carga/cancelación | Recorrido parcial, código, JVM y recibos Stage 3.2 | Toda combinación de datos, tamaños o gestos |
| Resumen/Horas/configuración | Apertura, navegación, capturas y código | Totales de todos los casos físicos |
| Notas/imágenes | Código, JVM, evidencia previa | Picker OEM completo y carrera de restore |
| Estados/Franco/Retén/recurrencia | Revisión de garantías y pruebas locales | Nueva batería física transversal |
| Avisos/Clima/Widget | Apertura y presentación de ajustes | Entrega real de alarma, proveedor de red o launcher |
| Copias | Apertura, gateway y pruebas locales | Restauración física, contraseña/SAF y recuperación total |
| Informes/Fotos/feriados/vacaciones | Cobertura local/antecedentes y consumidores de integridad | Nuevo recorrido completo exportación/importación |
| Bloqueo/Ayuda/Apariencia | Apertura de Bloqueo, fuentes Ayuda y capturas previas | Biometría, TalkBack, temas/zoom completos frescos |
| Actualizaciones/soporte/distribución | Fuera del ensayo remoto | Red pública, firma o preparación de Play Store |

## Handoff a MAIN y próximo stage

Delta propio: esta auditoría, contrato Stage 4, referencias en STATUS/índice de
prompts/handoff Stage 3; evidencia local nueva ignorada por Git. Sin cambios de
producto, esquemas, permisos, dependencias ni pruebas ejecutables.

Cierre documental: `docs_check` válido (321 referencias, 103 snapshots), catálogo
válido (111 clases, 21 grupos) y `git diff --check` sin errores. El primer control
documental detectó exceso del presupuesto de lectura de arranque; se abreviaron
sólo las nuevas referencias de STATUS/índice, sin retirar antecedentes ni ampliar
el límite. Los 568 insumos registrados en la prueba fresca siguen sin cambios.
Staging vacío; entrada acumulada preservada. Los dos documentos nuevos explican
el aumento de 37 a 39 archivos no rastreados; la evidencia local está ignorada.

Contrato: `docs/prompts/STAGE_4_FLUIDEZ_E_INTEGRIDAD_3_0.md`.
Recomendación: una capacidad integral con orden interno navegación/estado →
composición y lenguaje → medición e integridad → regresiones y QA. No dividirlo
en stages por botón. El especialista debe cerrar F01–F07, resolver R01–R04 con
evidencia y distinguir los pendientes de dispositivo de un candidato aceptado.

MAIN debe revisar el contrato con Joaquín antes de despachar implementación.
Esta auditoría entrega un diagnóstico parcial verificable y una ruta concreta;
no certifica ausencia universal de errores ni acepta automáticamente Stage 3.
