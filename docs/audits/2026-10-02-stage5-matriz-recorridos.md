# Stage 5 — reconciliación de los 33 recorridos

Fecha: 2026-10-02. Estado operativo: [STATUS](../STATUS.md).

Actualización 04/10: la tabla siguiente es histórica. La reconciliación al final
registra el candidato 223f/4113 y enlaza la
[QA final](2026-10-04-stage5-qa-final-y-rendimiento.md); los 32 fallos de calendario
del 02/10 no describen ese candidato. No se sustituyen subcasos pendientes por
una aceptación automática de filas completas.

Actualización 03/10: la tabla y los transversales siguientes conservan resultados
del candidato del 02/10. No describen como fallido el candidato posterior ni
transfieren sus verdes a una nueva APK. La
[continuación autorizada](2026-10-03-stage5-zoom-mensual-autorizado.md) registra
VIEW 48/48 y Adaptive 6/6 sobre el candidato de Insets, los fallos de señales
Common y su corrección posterior todavía sin verificación física. Ayuda Activity
real pasó 2/2 en el candidato lineal anterior; tampoco se reatribuye al último.
Frío y comparación caliente nuevos permanecen pendientes. Los 33 requisitos y
los límites humanos/OEM permanecen íntegros hasta reconciliar el cierre final.

Esta matriz conserva los 33 IDs y sus requisitos de la [matriz Stage 4](2026-10-02-stage4-matriz-recorridos.md).
El antecedente identifica lo efectivamente ejecutado en la [QA MAIN](2026-10-02-stage4-qa-main.md); no transfiere sus verdes al APK Stage 5.
`PARCIAL` significa subcasos ejecutados con los límites escritos, nunca aceptación de la fila.
QA física autorizada; recibos exactos en la [auditoría Stage 5](2026-10-02-stage5-cierre-parcial.md). Las pruebas Compose con dobles no acreditan datos reales ni OEM.

| ID / superficie | Evidencia de entrada Stage 4 | Candidata Stage 5 del 02/10 / siguiente comprobación |
|---|---|---|
| M01 Menú | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: NavigationDrawer 7/7 y Help 12/12, Compose/dobles; menú real también recorrido en mediciones. Revisión humana pendiente. |
| C01 Calendario mensual | FALLÓ: 32/48 combinaciones; 16/48 sólo geometría. Decisión de zoom pendiente. | FALLÓ: nueva matriz final 16/48 geometría verde y 32/48 fallida; 48 capturas idénticas a Stage 4. Política de zoom pendiente. |
| C02 Detalle del día | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: detalle con jornada/contexto real en 48 iteraciones de medición; CalendarCommon 11/11 con dobles. Legibilidad humana pendiente. |
| C03 Selección de días | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: Common 11/11, Exceptions 9/9 y carga Activity 8/8; selección y cancelación dirigidas. Ventanas incompatibles siguen fallando. |
| L01 Carga unificada | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: Activity 8/8, borrador completo/fechas vacías/recreación y guardado sólo de selección final. Teclado/OEM pendientes. |
| L02 Catálogo durante carga | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: Activity 8/8 y catálogo en mediciones reales; no acredita revisión humana completa. |
| L03 Revisión, conflictos y lote | PARCIAL: CalendarClear 11/11 Room, lotes/rollback; recorrido humano pendiente. | PARCIAL: CalendarClear 11/11 SQLite, lotes/CAS/rollback; ManualLoadNotes 5/5 y 48 guardados de tres jornadas. Interacciones humanas pendientes. |
| W01 Mi forma de trabajar | PARCIAL: regreso desde cuatro hijos con recreación y snapshot; 3/5 Flows verdes, medición fallida. | PARCIAL: dos métodos de padres reales Flows pasan; recreación y snapshot sin escrituras. No equivale a toda la configuración laboral. |
| H01 Horas y meta | PARCIAL: regreso al padre/recreación; no cubre toda la edición ni teclado. | PARCIAL: regreso al padre y recreación reales; cálculo JVM vigente. Edición integral/teclado pendientes. |
| H02 Horario real y extras | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| H03 Tipos de horas extra | PARCIAL: regreso al padre/recreación; persistencia completa no atribuida. | PARCIAL: regreso al padre/recreación real; subcasos de edición completos pendientes. |
| A01 Configuración de disponibilidad | PARCIAL: regreso al padre/recreación; no cubre todos los subcasos. | PARCIAL: regreso al padre/recreación real; subcasos completos pendientes. |
| A02 Retén del día | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| RP01 Repetición de jornadas | PARCIAL: regreso al padre con recreación; ensayo completo del plan recurrente pendiente. | PARCIAL: regreso al padre/recreación real; datasets de rendimiento sin repeticiones, capacidad recurrente pendiente. |
| S01 Situaciones y carpeta médica | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PARCIAL: Common 11/11 conserva UUID/estado y entrada contextual con dobles; ensayo humano integral pendiente. |
| E01 Feriados | PARCIAL: Exceptions 9/9 UI con callbacks/dobles; no ensayo OEM. | PARCIAL: Exceptions 9/9 con dobles, cancelación/GENERAL/HOLIDAY; no OEM. |
| V01 Vacaciones | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| N01 Notas e imágenes de jornada | PARCIAL: ManualLoadNotes 5/5, archivos 9/9, concurrencia 2/2; picker OEM pendiente. | PARCIAL: Activity 8/8 con imagen sintética, notas 5/5, archivos 9/9 y concurrencia restore 2/2 reales aislados; picker OEM pendiente. |
| P01 Fotos del mes | PARCIAL: archivos 9/9; visor/picker OEM no acreditados. | PARCIAL: archivos 9/9; Ayuda señala control real sin ejecutarlo. Visor/OEM humano pendiente. |
| U01 Resumen | PARCIAL: Summary 12/12 Compose; observación humana y proyección real completa pendientes. | PARCIAL: proyección real en mediciones y lecturas de 29 tablas sin mutación; revisión humana completa pendiente. |
| U02 Informes | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| O01 Avisos | PARCIAL: Notification 8/8 Compose/dobles; ninguna alarma real. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| T01 Clima | PARCIAL: Weather 12/12 Compose/dobles; sin red real. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| G01 Ubicación para Clima | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| I01 Widget | PARCIAL: Widget 7/7 Compose/dobles; launcher OEM pendiente. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| B01 Crear Copia | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| B02 Restaurar Copia | PARCIAL: concurrencia restore/archivos 2/2 aislados; no restauración personal ni upgrade público. | PARCIAL: concurrencia restore/archivos 2/2 sintéticos en SQLite; migraciones 8/8. Sin Copia personal ni upgrade público. |
| K01 Bloqueo | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| D01 Apariencia | PARCIAL: Appearance 2/2; TalkBack humano pendiente. | PARCIAL: temas/zoom en Help y matriz calendario final; calendario zoom alto falla. TalkBack y legibilidad pendientes. |
| J01 Ayuda y recorrido | FALLÓ: ancla HELP ausente. Corrección Stage 5 implementada; Android pendiente. | PARCIAL: Help 12/12 físicas, original HELP y matrices de anclas reales, zoom vivo y restauración Compose. Cierre usa evento ficticio; Activity/process death y humano pendientes. |
| X01 Apoyar MiGuardia / acceso externo | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| X02 Actualizaciones | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |
| X03 Entrada externa de aviso/Widget | PENDIENTE: no hay recibo específico completo de esta fila en QA MAIN. | PENDIENTE Android; conservar requisitos y pruebas de la matriz original. |

## Transversales

- R01: matriz final física 48 combinaciones, 32 fallos conservados; decisión material de zoom pendiente. El análisis geométrico previo no reemplaza la nueva ejecución.
- R02: [comparación completa caliente](2026-10-02-stage5-rendimiento.md), doce iteraciones por APK/serie y umbrales sin exceder. Frío: TotalTime n12 por APK; contenido listo y equivalencia de ventanas frías pendientes. Sin afirmación de FPS.
- R03/R04: lotes 11/11, notas 5/5, archivos 9/9, restore concurrente 2/2 y migraciones 8/8 con recibos propios Stage 5; SQLite/archivos sintéticos, no datos personales.
- Consumidores: V2ReadOnlySurfaces 1/1, snapshot de 29 tablas; no acredita por sí solo todas las pantallas de Copias, Horas e Informes.
- Las filas aún pendientes conservan todos los subcasos de la matriz original; no fueron omitidas ni declaradas aprobadas por falta de cambios.
- TalkBack, teclado, selector OEM, revisión visual humana y aceptación siguen pendientes. Red, alarmas y datos personales fuera del ensayo autorizado.

## Reconciliación dirigida del 04/10

Producto QA 223f…bab8 / runner app 4113…a45b. Son **69 métodos dirigidos**,
más **dos métodos de matriz con 48 combinaciones**; no 117 pruebas JUnit.
Recibos y causas en la auditoría final enlazada arriba. Todas las filas que
reciben evidencia dirigida siguen PARCIALES respecto de su recorrido integral.

- C01/D01/R01: Common 13, Adaptive 6 (`191023`) y VIEW 48 (`191328`),
  fuente exacta, siete columnas, contención y targets base ≥48 dp sin solapamiento.
  Legibilidad humana, pictogramas y TalkBack no aceptados por geometría.
- C02/C03/S01: Calendar 7/Common 13 (`191023`), UUID, wiring del detalle,
  selección/cancelaciones con dobles; Ready 7 (`191126`) añade mes, detalle y
  expansión restaurados en Activity real. No equivale a todo CRUD humano.
- L01/L02/L03/N01: Manual Activity 8 (`191151`), catálogo/borrador completo,
  fechas finales, conflictos, nota e imagen sintética y recreación. Teclado y
  selector OEM pendientes. La integridad SQLite previa conserva su propio recibo.
- J01/M01: Ayuda real 2 (`191255`), recorrido/recreación/retorno sin escrituras;
  Calendar 7 conserva accesos desde menú. Help Compose 12, Appearance 2,
  Exceptions 9 y Drawer anteriores conservan su APK/runner históricos, no 223f.
  Muerte de proceso, descubribilidad y aceptación de todos los menús pendientes.
- W01/H01/H03/A01/RP01: padres reales 2 (`191316`), retorno/recreación y snapshot
  sin escrituras; no toda edición de configuración, horas, disponibilidad o plan.
- X03: Ready 7, entrada por UUID y confirmación consciente de Maps con monitor;
  no alarma entregada ni widget/Maps OEM real.
- U01/R02: proyecciones/snapshots reales en la comparación caliente y proceso
  frío con contenido listo; recibos y conclusión final en la auditoría 04/10.
  No resumen humano integral, almacenamiento frío ni FPS inferidos.
- E01: Exceptions 9 del `183435` conserva QA 463b/runner 5194. No cambió esa
  superficie durante el arreglo posterior, pero no se presenta como ejecución
  sobre el APK final ni como ensayo humano/OEM completo.
- P01/B02/R03/R04: conservar SQLite/archivos/migraciones del 02/10 con sus hashes
  y límites; código de integridad intacto. No reasignar al nuevo APK ni repetir
  por rutina. No copia personal ni actualización pública real.
- H02/A02/V01/U02/O01/T01/G01/I01/B01/K01/X01/X02: sus doce recorridos Android
  completos siguen pendientes; requisitos originales preservados. Los ensayos
  dirigidos anteriores no los convierten en aprobados.

La parada de medición por foco, lotes incompletos y fallos adversos de altura,
oráculo y expansión se conservan separados de los cierres verdes. La auditoría
registra el par de métricas final, la instalación y el próximo paso humano.

## Continuación dirigida del 09/10

QA 223f…bab8 y runner da318a…ac94. Recibos, causas y límites en la
[continuación](2026-10-09-stage5-continuacion-qa.md). 144 métodos distintos
aprobados (27 clases completas y un método), no 144 recorridos integrales:

- H02/H01/H03: horario real Activity 1/1 y extras Activity 2/2. Rotación,
  recreación, guardado, reapertura/vuelta al planificado; extra independiente
  con puesto, corrección, borrado y referencia futura. No teclado humano integral.
- A02/A01/X03: disponibilidad Activity 3/3, Retén contextual, etiqueta histórica,
  configuración global intacta, CRUD/recreación y entrada por fecha dueña.
  No aviso real entregado ni todos los subcasos de disponibilidad.
- U01: Resumen Activity 1/1, detalle/mes/personalización y retorno al Calendario.
- RP01: repetición Activity 1/1, borrador y plan multimes persistidos/reabiertos.
- I01/K01: WidgetConfiguration Activity 5/5 con host de prueba y preferencias
  exactas, cancelación, recreación y puertas de arranque/bloqueo. No autenticación
  ni launcher OEM reales, ni gestión humana completa de instancias.
- V01: Compose 7/7 final, ViewModel 5/5 con dobles/SavedStateHandle y SQLite
  10/10 aislada, CRUD/rangos/CAS/reapertura. La etiqueta/rango del detalle se
  comprueba visible; se conservan 6/7 adverso y corrección de import del build.
- U02/U01: Informes Compose 4/4, ViewModel 6/6, destino 5/5, PDF 4/4,
  artefactos 3/3, fotos preparadas 3/3 y snapshot mensual SQLite 5/5. Privacidad,
  fallos, archivos completos y paginación aislados; no SAF/chooser OEM integral.
- B01/B02: Copias Compose 10/10 y publicación sintética 4/4, cifrado/sin clave,
  interrupción/fallo/limpieza; no prueba de selector SAF, copia personal ni upgrade.
- O01: Avisos Compose 8/8 con callbacks, no entrega de alarmas reales.
- T01/G01: Clima Compose 12/12 e infraestructura 9/9, más ubicación contextual
  1/1 de WorkSetup con dobles y retiro confirmado. Sin permiso, posición o red
  reales; no se declara aprobada toda WorkSetupComposeTest por ese único método.
- I01/K01: Widget Compose 7/7 y bloqueo Compose 4/4 añaden estados, puertas
  privadas, retorno y errores con dobles. Biometría/launcher OEM pendientes.
- X01/X02/M01: Apoyar menú 9/9 y enlace 3/3; Actualizaciones menú 9/9 y retorno
  3/3. Doble toque, animación, estado/retorno, temas/zoom; enlaces interceptados,
  no browser, descarga o publicación reales.

Los últimos ocho métodos usan runner b96ba8…8770 (Vacaciones 7 y ubicación 1);
los otros 136 conservan sus recibos da318a/base 493cbf. QA producto 223f sin
cambios. Los nuevos subcasos de las doce filas pendientes reciben evidencia y
siguen PARCIALES en alcance integral; no se borran requisitos originales.
Observación humana, TalkBack, teclado, selectores OEM, permisos/biometría y
accesos reales siguen pendientes. Los cortes y callers obsoletos se conservan.

## QA OEM posterior del 09/10

La [auditoría OEM](2026-10-09-stage5-qa-oem-accesibilidad.md) añade diez métodos
distintos verdes, sin reemplazar los requisitos originales de las 33 filas.
Teclado real visible/editable/Atrás; Photo Picker y SAF cancelados; escritura,
lectura y borrado de un documento nuevo sintético; permisos de ubicación y
notificaciones rechazados; sonido previo conservado al volver del selector.
Bloqueo MainActivity con autenticación real de Joaquín y configuración restaurada;
TalkBack conectado con acciones accesibles de menú/selección/cancelación.

Las fronteras OEM usan Composables productivos y callbacks aislados; no certifican
restauración de una copia válida ni importación positiva de fotos. Bloqueo y
accesibilidad sí recorren MainActivity. La audición humana no fue observada por
Joaquín y sigue pendiente, junto con legibilidad/zoom y subcasos integrales.
No posición real, galería personal, avisos entregados ni app pública utilizados.

## Imagen ficticia y escucha — continuación posterior del 09/10

La [auditoría nueva](2026-10-09-stage5-imagen-ficticia-talkback.md) registra una
causa productiva compartida: el aviso interceptaba navegación. La QA c079…7227
corrige Fotos/Notas/Feriados/Vacaciones y suma 32 métodos físicos verdes en esos
consumidores y composición; las 24 combinaciones de confirmación no son métodos.

- P01/J01/E01/V01: reserva de espacio del aviso, Cerrar operable con mensaje,
  ambos temas y zoom 100/150/200; Fotos 8, Feriados/Notas 9 y Vacaciones 7.
  Visor nativo recreado vuelve al primer toque aun con confirmación visible.
- P01: imagen OEM ficticia importada/privada/visualizada/recreada en QA 223f,
  pero regreso fallaba. El diagnóstico aislado pasa en c079; la repetición OEM
  final venció esperando selección. Importar→ver→recrear→borrar por UI en esa
  APK sigue pendiente; finally no sustituye el borrado consciente.
- M01/I01: Joaquín confirmó claros Abrir menú, Seleccionar días y Cancelar
  con TalkBack, en 223f/runner 6b6962…42df. No voz integral ni reatribución a c079.
- C01/D01: composición/insets 5 conserva el overlay del Calendario. No se
  declara aprobación de todos los controles debajo de un aviso en ventana baja.

511 JVM de app y build/lint nuevos; resultados globales/interop/rendimiento
anteriores mantienen atribución. Cortes y fallos quedan preservados. Las 33 filas
mantienen sus límites y estado parcial; no aceptación humana ni distribución.

## Cierre puntual de imagen OEM — 10/10

P01: selector real → importación privada → visor → recreación → Volver al primer
toque → eliminación por UI, **1/1** sobre QA c079…7227/runner 043773…0f55.
UUID/clave privados eliminados antes del finally; fuente ficticia retirada,
ajustes restaurados y público intacto. Sin lectura de medios personales.
La [auditoría del recorrido](2026-10-10-stage5-imagen-oem.md) conserva recibo,
identidades y alcance del callback protegido. No se suman los 32 métodos previos
como una ejecución nueva ni se cambia el estado integral de las 33 filas.
Siguen pendientes revisión humana/zoom/accesibilidad integral y demás subcasos.

## Avisos y restauración de proceso — continuación del 10/10

QA 6ae70d…5b1b3; evidencia, identidades y adversos en la
[auditoría de esta unidad](2026-10-10-stage5-avisos-y-restauracion.md).
13 métodos físicos distintos completados, no 13 recorridos integrales ni una
reejecución de las 33 filas. No se reasignan pruebas antiguas a esta APK.

- C01/C02/S01/D01/R01: aviso breve en encabezado y detalle al tocar; sin overlay
  sobre fechas/flechas ni alto adicional. Composición 8 métodos: incluye doce
  layouts de seis filas a 360×600 dp base, temas/zoom 100/150/200, VIEW/EDIT,
  todos los días tocados, targets ≥48 dp no superpuestos, y una ventana de 550 dp
  con mensaje retenido durante selección. Dialog restaura tras recreación, vence
  su aviso de origen sin perder el texto abierto y Atrás no cancela selección.
- T01: aviso de Clima reserva espacio; Cerrar recibe el primer toque en seis
  combinaciones de tema/zoom. Sin red, ubicación ni permiso nuevos.
- P01/N01/E01/V01: regresión común en 24 combinaciones y propietario actualizado
  al vencer un texto idéntico; junto a Clima y vencimiento son cuatro métodos,
  no 30 pruebas distintas. El recorrido OEM 1/1 conserva su APK c079 anterior.
- J01/M01: runner independiente mata el proceso QA realmente en segundo plano
  y restaura la misma tarea con PID nuevo: Recorrido 5/8 conserva paso; luego
  de cerrarlo vuelve Ayuda sin reentrada tras otra muerte. Regreso a Calendario,
  onboarding intacto y hashes binarios/lógicos de las 31 tablas SQLite iguales
  (29 de negocio más metadatos). No reset
  de datos, Recientes globales ni lectura de sandbox público.

Un lote se cortó por foco no resuelto entre fixtures tras dos métodos verdes;
los demás se ejecutaron individualmente. Errores del arnés y experimentos de
altura/densidad quedan preservados, no acreditan la matriz final. Legibilidad y
composición humanas, TalkBack integral y restantes subcasos de las filas siguen
pendientes. Rendimiento anterior mantiene su candidata; no FPS ni cierre global.
