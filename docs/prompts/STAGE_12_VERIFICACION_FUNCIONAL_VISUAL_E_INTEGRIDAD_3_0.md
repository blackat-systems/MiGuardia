# Stage 12 — Verificación integral funcional, visual y de integridad de MiGuardia 3.0

## Actualización vigente de disponibilidad — 31/10/2026

Joaquín informó en el chat de diagnóstico `01a19369-771d-7ff2-be56-f2a1c5d652e6`:
«tenemos el samsung disponinble para todas las pruebas». Esto reemplaza la
restricción del 26/10 de Samsung sólo para QA humano y los HOLD de disponibilidad
anteriores: las pruebas técnicas y humanas del alcance QA cuentan con el Samsung.
No volver a pedir su disponibilidad genérica. Verificar identidad y conexión al
ejecutar; conservar las guardas de paquete/datos, fixtures y capturas propias.
No habilita por inferencia producción, datos personales, firma o publicación.
Las filas y adendas posteriores que dicen «sólo QA humano», «sin Samsung» o
«sólo AVD» quedan históricas en ese punto. La revisión PRE-install y las guardas
de cada prueba siguen aplicando; la disponibilidad no convierte un runner AOSP
en uno compatible con Samsung.

Joaquín pidió después ejecutar el [plan maestro](../PLAN_MAESTRO_CIERRE_3_0.md)
con `joa-main-orchestrator`, repartido entre MAIN y el Stage existentes. El plan
rige la secuencia operativa y los permisos actuales: Samsung es la ruta principal;
consola/AVD dejan de ser prerrequisito. Este contrato conserva el producto, matriz,
aislamiento y dos rondas. Las admisiones Windows consumidas no se reutilizan.
Estado/acuse/ejecución reales: [STATUS](../STATUS.md) y su checkpoint referenciado.

## Antecedente de uso reemplazado en disponibilidad — 26/10/2026

Joaquín ordenó en MAIN: «El Samsung sólo se utilizará para QA humano. Van a tener
que arreglárselas con el emulador más liviano». Samsung queda fuera de las pruebas
técnicas o automatizadas; sólo casos puntuales de QA humano con alcance preparado.
Conectarlo o desbloquearlo no reactiva ensayos automatizados. Esto sustituye el
HOLD temporal de disponibilidad para el trabajo futuro, sin cambiar evidencias anteriores.

El entorno ordinario es el AVD AOSP existente en modo sin ventana. Priorizar el
menor consumo viable; configuración actual: dos núcleos y 2048 MiB, sin recrear ni
descargar imágenes por rutina. Ajustes menores requieren sustento y validación
pertinente, no reducir oráculos para ahorrar recursos. MAIN admite cada unidad
nativa concreta; la decisión no reutiliza admisiones consumidas ni habilita Samsung.
V5 y sus criterios siguen intactos. Actualizar bindings por entorno y preparar
los pasos humanos/OEM pendientes; no declarar que AVD los reemplaza ni ocultarlos.
Autoridad y estado exactos: checkpoint y recibo
`.local-evidence/stage12/main/emulator-native-stop-r1-review/human-only-samsung-authority.json`.
Las adendas y permisos anteriores conservan valor histórico y ceden ante esta decisión.

## 0. Naturaleza del encargo y regla de autoridad

Este es el prompt autosuficiente del Stage 12, escrito para un **chat propio de
Codex**, no para reemplazarlo por un worker de MAIN. No requiere reconstruir
otros chats. Leerlo completo y recorrer sus anexos antes de ejecutar.

Pedido de Joaquín del 23/10/2026: inventariar todas las funciones del Calendario,
todas las capacidades generales y combinadas de la app y el Widget; revisar la
presentación; probar desde el primer punto al último, registrar aprobados y
fallidos, corregir causas y repetir hasta obtener un cierre comprobable. Precisó
que el prompt debe ser exhaustivo, técnico, sin ambigüedades y contener los bugs
identificados y sus soluciones propuestas.

**Separación obligatoria:** preparar este contrato no equivale a haber ejecutado
Stage 12. La fuente del estado operativo y la activación es docs/STATUS.md.
Durante esta preparación no se modificó producto ni se ejecutó la batería integral.
**Orden del 23/10: LOCAL autorizado; ampliación AVD en adenda final.** Joaquín pidió:
«Dale, creá un chat propio para Stage 12, mandale el prompt completo y supervisá
el trabajo. Que arranque por las verificaciones locales; avisame cuando necesite
dejar libre el Samsung. Después auditá la entrega».
Se autoriza crear/despachar ese chat, comprobar localmente, preparar y revisar
su plan/aislamiento, continuar las correcciones mínimas del contrato bajo
supervisión de MAIN y auditar su entrega. Verificación local incluye Gradle y
sus salidas de pruebas/evidencia. Android en AVD se delimita en la adenda final.
Samsung sigue liberado para Joaquín: **NO ADB, ni siquiera consultas, instalación,
instrumentación, capturas o cambios del teléfono** hasta que MAIN avise y obtenga
la confirmación humana de alcance/disponibilidad para ese bloque físico.
Registrar STATUS no amplía permisos; la orden humana posterior funda la adenda AVD.

Trabajar en español argentino comprensible. Los IDs, comandos y términos técnicos
se mantienen exactos. No prometer ausencia absoluta de errores; demostrar los
criterios definidos y registrar explícitamente lo no comprobado.

## 1. TAREA (TASK)

Entregar una candidata que supere el inventario funcional del anexo A, los
recorridos combinados del anexo B y los criterios visuales del anexo C,
sin perder capacidades existentes, datos, privacidad ni contratos históricos.

La unidad de trabajo es **verificación y corrección de las capacidades existentes
de extremo a extremo**, no un rediseño libre, una nueva función o una batería
estadística indefinida. El inventario inicial contiene:

- 100 puntos de Calendario y recorridos contextuales: C001–C100.
- 286 puntos de otras familias: A001–A286.
- 24 puntos de Widget: W001–W024.
- 15 recorridos combinados: X001–X015.
- 16 criterios visuales/transversales: V001–V016.

Son **425 puntos funcionales/recorridos y 16 criterios visuales**, no 425 funciones
independientes de negocio, no 441 pruebas ya ejecutadas ni una promesa de cobertura
universal. Un acceso contextual y su editor pueden compartir la misma prueba,
pero deben tener trazabilidad y aserciones distintas. No inflar conteos duplicando
un mismo método o multiplicando configuraciones de una prueba.

## 2. CONTEXTO Y ENTRADAS (CONTEXT / INPUTS)

### 2.1 Base exacta

- Workspace: C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer.
- Repositorio activo independiente: C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0.
- Ejecutar Git, Gradle y herramientas desde MiGuardia-3.0.
- Rama: codex/miguardia-3.0.
- HEAD de referencia: 932eaeb60c6b6e68975534788e3e6501c967b1ae.
- HEAD **no representa todo el candidato**: existe un checkout acumulado sucio,
  con numerosos archivos modificados y no rastreados. Preservar toda entrada.
- MiGuardia-2.0, Repositorio-Git y Archivo-recuperacion son fronteras de sólo
  lectura; no copiar sus árboles para reemplazar el candidato.
- QA comprobada al preparar: com.blackatsystems.miguardia.qa.
- Ejecutor normal: com.blackatsystems.miguardia.qa.test.
- QA SHA-256:
  682e529e79eb398b31c757e51d8d3500e165a8d90293cb9aea07875a6bed0f2e.
- Ejecutor SHA-256:
  6018b6009aa8b6852a94df6318adc8e3b535ae6334dd805baf397a08cc1a2dbb.
- Certificado técnico recibido:
  b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617.
- Samsung de referencia: SM-S938B / API 36 / serial [serial QA en binding local privado].
  Identidad y disponibilidad son hechos temporales: revalidar antes de operar.
- Sesión QA recibida: 1956 jornadas ficticias; no es una base vacía descartable.
  Preservar sus datos y preferencias, incluido Clima habilitado y tema DARK.
- Datos de producción/pública y datos personales: siempre fuera del alcance.

### 2.2 Lectura obligatoria, en este orden

1. AGENTS.md, docs/STATUS.md y docs/LECTURAS_POR_TAREA.md.
2. docs/WORKFLOW.md, docs/VALIDACION.md y docs/EVIDENCIA.md.
3. Este contrato completo y sus anexos: son la lista de requisitos del ensayo.
4. docs/adr/0043-letra-nativa-android-sin-zoom-interno.md y ADR pertinentes por
   familia, siguiendo la ruta de lecturas; no cargar toda la historia por rutina.
5. Código de cada familia, sus coordinadores/repositorios y las aserciones de
   sus pruebas. Un nombre histórico de test no define comportamiento correcto.
6. docs/audits/2026-10-23-detalle-clima-jornada-unica.md y la revisión visual
   preparatoria de Stage 12. Los recibos históricos sirven de entrada limitada,
   no como resultados nuevos de este stage.

### 2.3 Evidencia que se conserva, sin reatribuir

- Cierre local anterior: 981 JVM frescas, build/lint sin errores, 30 métodos
  Android dirigidos sobre QA682e; no equivale a la matriz integral del Stage 12.
- Perfil local: .local-evidence/bd-l3/result.json.
- Build: .local-evidence/bd-b2/result.json. QA se reutilizó UP-TO-DATE;
  ejecutor generado. No afirmar que todo se compiló desde cero.
- Cierre previo y 32 rutas selladas:
  .local-evidence/day-detail-weather-20261023/final-review.json.
- Regresión nativa actual FinalQaReadOnlyReviewTest exige QA682e en
  EXPECTED_QA, línea 361 al preparar. **No está fijada a QA494a.**
- Catálogo de entrada: 125 clases / 32 grupos. No ejecutar los 32 grupos
  automáticamente: algunas fixtures modifican datos globales.
- Stages 8, 9, 10 y 11 conservan sus alcances y límites. No repetir una campaña
  cerrada para fabricar un resultado más cómodo.

## 3. PERMISOS, RECURSOS Y FRONTERAS

| Acción | Regla exacta |
|---|---|
| Chat propio, contrato completo, supervisión y auditoría | CONCEDIDO por la orden humana posterior del 23/10 citada arriba; no crear chats duplicados. |
| Verificaciones locales, plan, evidencia y Gradle | CONCEDIDO; un escritor/operador, entrada protegida y delta propio. Android sólo en AVD nuevo según adenda. |
| Correcciones mínimas existentes | Dentro del contrato, después de revisar MAIN el plan/aislamiento; no pedir otra autorización humana rutinaria ni cruzar una frontera material. |
| Samsung | SÓLO QA HUMANO puntual según decisión vigente del 26/10. Fuera de pruebas técnicas/automatizadas; conexión o desbloqueo no las reactiva. |
| Instalar QA/ejecutor | Concedido sólo para AVD Stage12 según adenda y revisión previa de protocolo/artefactos; paquete, certificado y SHA ANTES, instalado DESPUÉS. Sin Samsung. |
| Datos de ensayo | Sólo sintéticos propios, con UUID/nombres Stage12 y aislamiento real; no borrar/resembrar las 1956 jornadas recibidas. |
| Clima de red | Prueba separada consciente, sin ubicación personal; no convertir un doble en descarga real. Si falta permiso del ensayo, BLOQUEADO en ese subcaso. |
| Permisos, biometría, selectores, sonidos, launcher | No aceptar confirmaciones por Joaquín, no leer contenido personal. Solicitar la intervención concreta cuando sea obligatoria; no hacerlo repetidamente sin nueva necesidad. |
| Ajustes globales de Android | Prohibido cambiarlos para facilitar tests: letra, densidad, reloj/zona, seguridad, TalkBack, Doze, notificaciones de otras apps o DND. Usar contextos QA para simulación. |
| Emulador | Concedido un AVD NUEVO dedicado Stage12 según adenda final. Prohibido usar, iniciar, modificar o reemplazar AVD de Stages 10/11. |
| App pública | No abrir, consultar sus archivos, instrumentar, reemplazar, limpiar, desinstalar ni usar sus datos. |
| Firma/publicación/costos/commit/push | No autorizados por este contrato; no usar claves de distribución ni generar compromisos externos. |

Un permiso de alcance no elimina las guardas. Ante foco inesperado o bloqueo, detener
sólo dispositivo, no operar esa pantalla y continuar trabajo local independiente.
Excepción acotada: una salida externa prevista debe originarse en una acción QA
registrada, con Intent/componente o tipo de diálogo esperado validado y autorización
de ese subcaso. Registrar inicio/fin y esperar la intervención humana requerida;
no inspeccionar contenido personal ni fotografiar la pantalla externa. Un proveedor
de prueba controlado se identifica como tal, no como selector OEM. Cualquier otro
foco ajeno interrumpe el paso. No devolver a QA mediante pulsaciones sobre otra app.
No usar el primer dispositivo
implícito de ADB: servidor local y serial explícitos en cada operación.

MAIN conserva STATUS, índices, contrato y revisión. Stage 12 escribe sólo su
implementación/pruebas/evidencia/handoff. Un escritor por archivo; no competir
con MAIN por Gradle, Samsung, AVD o rutas de evidencia.

## 4. BUGS E INCONGRUENCIAS IDENTIFICADOS ANTES DEL ENSAYO

No son una lista ficticia de defectos que supuestamente ya se encontraron en toda
la aplicación. Éstos sí tienen evidencia concreta. Otros fallos se incorporan
cuando se reproduzcan, con el mismo protocolo.

### S12-B01 — Diálogo de Clima ocupa altura innecesaria

**Estado:** defecto visual confirmado por fuente y captura; no demuestra fallo
del proveedor ni pérdida de datos. Prioridad P2 de presentación.

**Evidencia:** WeatherScreens.kt, WeatherSurfaceHost, usa
TransientConfirmation(... reserveSpace = true) dentro de Dialog. En
TransientConfirmation.kt, el ramal reserveSpace impone Column.fillMaxSize()
y Box.weight(1f), aunque message sea null. El contenido de Clima termina
bastante antes del fondo del contenedor. Captura limpia QA682e:
.local-evidence/day-detail-weather-20261023/capture-20261023T164827.129932Z.png.

**Causa:** política de contenedor de destino completo reutilizada en un diálogo
envolvente. El weight(fill=false) interior no evita que crezca el exterior.

**Solución requerida:** política explícita de tamaño envolvente, opt-in sólo en
el diálogo afectado, conservando el valor predeterminado de pantalla completa
para los consumidores que lo necesitan. No forzar alto completo en ese modo;
ajustar al contenido corto y limitar al área segura disponible para contenido
largo desplazable. Cerrar y encabezado permanecen fuera del scroll y nunca
quedan debajo de un mensaje. No aplicar safeDrawing/insets dos veces.

No basta cambiar reserveSpace a false: el otro ramal también usa fillMaxSize
y reintroduce el mensaje flotante sobre controles. No quitar temporizador,
anuncio accesible o confirmación para esconder el síntoma.

**Prueba roja previa y cierre:**
- En host alto, contenido corto, sin mensaje: medir bounds de Surface, último
  elemento y padding/insets declarados; el alto no excede su contenido medido
  más paddings por más de 1 dp de tolerancia de redondeo.
- Mensaje visible y expiración real definida de 2500 ms: Cerrar sigue visible,
  contenido no se tapa, un toque cierra una sola vez; sin reserva vacía permanente.
- Cargando → sin ubicación → contenido largo/créditos expandidos.
- Claro/oscuro, letra Android 1/1,5/2 por contexto, retrato/paisaje/ventana baja.
- Fotos, notas/feriados y vacaciones conservan tamaños/navegación previos.
- Ampliar TransientConfirmationComposeTest y WeatherComposeTest. Su test de
  Cerrar existente no demuestra altura envolvente: agregar aserción de geometría.

### S12-B02 — Fecha/intervalo ambiguos y fecha errónea de disponibilidad reanudada

**Estado:** pérdida de información temporal confirmada en fuente; todavía no
hay una ejecución de regresión nueva Stage12. Prioridad P1 por dato temporal
engañoso. La lógica de negocio no se cambia.

**Evidencia:** WidgetRenderModel.kt:162–164 compone ownerLocalDate y
HH:mm–HH:mm, sin final civil. Dos disponibilidades válidas:
23/10/2026 08:00 → 23/10/2026 09:00 y
23/10/2026 08:00 → 24/10/2026 09:00 producen la misma etiqueta actual.
La segunda dura 25 horas. El dominio admite estas ventanas.

Además, NextEvent.kt proyecta segmentos efectivos de disponibilidad conservando
ownerLocalDate, pero asigna start/end del segmento. WidgetProjection conserva
esas tres fuentes. Si una ventana dueña del 23 reanuda el 24, el Widget puede
mostrar fecha 23 junto a hora del segmento del 24. **Añadir sólo +N no lo arregla.**

Fuentes equivalentes a inspeccionar: CalendarDayDetail.ShiftDetail,
CalendarFormatting.timeRange, WeatherScreens.timeRange, NextEventCard,
ShiftNotificationPresenter y resúmenes planificados/reales de V2ShiftActualScreens.
No modificar un consumidor que ya expone ambas fechas correctas.

**Solución requerida:**
1. Formateador puro de presentación alimentado por start/end Instant y zoneId
   del evento, no por el reloj global ni zona del equipo en vez de la guardada.
2. Calcular fechas civiles reales con atZone(zoneId).toLocalDate().
3. Calcular desplazamiento con ChronoUnit.DAYS.between(fechaInicio, fechaFinal),
   nunca Duration/24h. No cambiar instantes, duración, UUID o fecha dueña.
4. En detalle/revisión debe ser posible leer ambos extremos como
   DD/MM/YYYY HH:mm – DD/MM/YYYY HH:mm.
5. En resumen compacto del mismo día conservar HH:mm–HH:mm; si cruza fechas,
   añadir (+1 día) o (+N días). Si el contexto no da la fecha inicial real,
   mostrarla explícitamente. En cambio de mes/año mostrar ambos extremos
   completos, no sólo números de día ni horas.
6. En Widget la fecha visible corresponde a start local del segmento; ownerLocalDate
   se conserva exclusivamente para navegación/propiedad, o se rotula separadamente
   si ya era necesario. El toque continúa abriendo su fecha dueña correcta.
7. La celda mensual conserva calendarTimeRange: 08–16 sólo para horas enteras
   del mismo día; minutos/cruces con +N. No duplicar +N al usar el helper.
   Si la síntesis no cabe, omitirla entera y conservar el detalle/semántica.
8. Privacidad Oculta continúa retornando antes sin fechas/horarios/+N ni filas.
   Reducida no agrega institución, función, color ni tipo privado.
9. Evitar formateadores divergentes: reutilizar/extender una función pequeña
   de presentación, sin llevar reglas de UI al dominio ni crear un framework.

**Regresiones obligatorias:** mismo día con minutos; nocturna; 24 h; 25 h;
varios días; fin de mes/año; 29 de febrero; zona con transición de horario;
segmento reanudado que empieza otro día; UUID distintos con igual apariencia;
privacidad Completa/Reducida/Oculta; color/puesto históricos y navegación intactos.

CalendarFormattingTest.detailKeepsTheFullTimeAndArgentineDatesAcrossMonthBoundary
exige actualmente un texto sin +1: preservar ese adverso y corregir la expectativa
sólo después de demostrar la pérdida. No relajar un test sin esa causa.
Ampliar pruebas del formateador, WidgetRenderModelTest, NextEventComposeTest,
WeatherComposeTest y presenter. Éstas no acreditan entrega real de alarmas.

### S12-B03 — Riesgo comprobado del arnés: preparación destructiva sobre QA recibida

**Estado:** efectos de setUp/tearDown confirmados en código; no se afirma que
hayan borrado datos en este relevamiento. Prioridad P0 como guarda del ensayo.

WidgetConfigurationActivityTest repara preferencias de bloqueo, vacía
widget_deferred_actions y crea/bindea un AppWidgetHost con identidad shell.
SummaryActivityTest y otras clases pueden preparar/borrar su fixture global.
Stage11 y Stage10 tienen runners, contextos y pins particulares.

**Solución:** inventariar los efectos de cada método, regla, Application,
setUp/tearDown y proveedor antes de ejecutarlo. Adaptar un host/runner test-only
con Context/Room/DataStore/archivos/colas/hostId realmente aislados. Application
neutral cuando se requiera; no ejecutar coordinador vivo de Copias sobre
la QA recibida. Una ruta con UUID no aísla si applicationContext, singleton o
proveedor vuelve a la base o preferencias normales.

Probar el aislamiento con canarios y un adverso que intente salir de la raíz:
debe detenerse ANTES de abrir/escribir/borrar el recurso ajeno. Fallar si cambia
una tabla/preferencia/archivo recibido. No reparar mediante restore del original
para fingir que no cambió. No limpiar automáticamente ante fallo de evidencia.

### S12-B04 — Guarda de captura insuficiente frente a notificación superpuesta

**Estado:** incidente confirmado del relevamiento, no bug productivo de MiGuardia.
Prioridad P0 para privacidad de la evidencia.

Con foco QA y teléfono desbloqueado se superpuso una notificación de otra app
entre controles. Una captura de pantalla completa la incluyó. Se eliminó
exactamente ese PNG local y se excluyó de la entrega; no se transcribe contenido.
La eliminación local no retira lo ya mostrado por la herramienta en el chat.
Registro: .local-evidence/stage12/preparation/excluded-capture.json.

**Solución obligatoria antes de otra captura:** capturar sólo ventana/nodo de QA:
captureToImage del nodo raíz correcto o PixelCopy de la Window de la Activity QA,
incluyendo las ventanas propias de diálogos cuando corresponda. Una imagen de
pantalla completa recortada después NO satisface el requisito: ya habría capturado
el overlay. No habilitar DND ni modificar notificaciones globales para sortearlo.

Validar que la Activity/Window/nodo pertenece a QA y que la captura corresponde
al estado/artefacto esperado; esperar estabilidad sin adelantar la animación que
se intenta probar. Un screenshot de Compose con fake no reemplaza el toque/animación
nativos que detectó el fallo anterior. Para pantallas protegidas, mantener
FLAG_SECURE y registrar el límite, no desactivarlo para fotografiar.

Probar offline/host QA una superposición sintética y confirmar que no aparece
en el archivo capturado; validar política ANTES de persistir/publicar imágenes.
No producir nuevos archivos con contenido de notificaciones personales.

### No reabrir como fallos los arreglos comprobados

La desaparición del detalle y la expansión repetida de jornada única están
corregidas en QA682e y tienen adversos/pruebas antes/después. Reejecutar sus
regresiones en cada candidata pertinente; no reconstruir su historia ni
cambiar dominio para satisfacer una fixture equivocada.

Clima sin coordenadas es un estado válido. No presentarlo como “proveedor roto”
ni como “pronóstico real aprobado”. Probar por separado activación,
ubicación/consentimiento, caché, cobertura y red.

## 5. PUERTA 0 Y PLAN EJECUTABLE OBLIGATORIOS

Antes de modificar o instrumentar:

1. Leer fuentes vigentes y verificar carpeta, rama/HEAD, remotos, worktrees,
   staging, rastreados/no rastreados y recursos activos. Si hay mismatch no
   explicado, no corregirlo a ciegas.
2. Crear evidencia de entrada NUEVA bajo .local-evidence/stage12/<run-id>/,
   sin sobrescribir preparation ni evidencia previa. Registrar originales,
   hashes SHA-256, tamaños y delta de insumos; no copiar claves/datos personales.
3. Verificar localmente manifiesto, applicationId/targetPackage, versión,
   certificado y hashes de QA/runner **antes** de cualquier instalación.
   APK errónea debe ser rechazada con un doble ANTES del install -r.
4. Registrar de forma explícita si QA/runner fueron generados o reutilizados,
   fuente exacta y opciones Gradle/runner. No restaurar outputs históricos para
   forzar que coincidan con una huella.
5. Construir plan-casos.json a partir de TODOS los IDs del contrato. Por fila:
   ID, subcasos, pantalla/entrada, fixture, precondiciones, pasos, resultados
   esperados, aserciones, nivel exigido, método/selector, efectos, riesgo,
   configuración de texto/tema/ventana, limpieza permitida y evidencia prevista.
6. Nivel exigido: UI pura para aspecto/callback; Activity nativa para navegación,
   animación, recreación y entradas reales; SQLite/archivos reales aislados para
   persistencia; RemoteViews/host real de prueba para Widget; OEM/humano para
   permisos, selectores/biometría/launcher/voz cuando se reclama esa capacidad.
   No aceptar fake callback como prueba de persistencia o entrega de alarma.
7. Trazar cada fila a una aserción concreta. “La clase pasó” sin demostrar que
   recorrió ese ID no aprueba la fila. Un método puede cubrir varios IDs sólo
   si hay evidencia individual de sus aserciones.
8. Todo ID inicial entra SIN_EJECUTAR. Los verdes históricos se registran
   como antecedente, nunca como una pasada nueva de este contrato.
9. MAIN revisa plan y aislamiento antes de implementar cambios de producto o
   ejecutar pruebas con escrituras de negocio/Android. Las salidas locales de
   Gradle y evidencia de Puerta0/verificación ya están autorizadas. Publicar un
   checkpoint estable con plan/efectos/casos para MAIN; continuar lo local seguro
   mientras se revisa, sin editar STATUS/índices/contrato del supervisor.
10. Confirmar cese de recursos de otros chats. Stage12 será el único operador
    Gradle/Android. No crear otro stage/implementador para un botón de esta misma tarea.

## 6. CASOS, FIXTURES Y ORÁCULOS SIN AMBIGÜEDAD

### 6.1 Fixture sintética propia, no reseteo del teléfono

La familia de ensayo debe contener datos con IDs deterministas dentro de una raíz
UUID Stage12 independiente. Ningún caso escribe sobre un UUID recibido.

Incluir al menos:
- Un contexto inicial vacío para onboarding separado del contexto poblado.
- Los cuatro sectores en contextos independientes; no cuatro perfiles simultáneos.
- Meses de cuatro/cinco/seis filas; febrero común/bisiesto y diciembre/enero.
- Día sin definir, Franco explícito y Sin definir explícito.
- Día con una jornada y día con varias, incluidas dos del mismo objetivo/horario
  con UUID diferentes y dos con inicio simultáneo de instituciones distintas.
- Horarios 08:00–16:00, 19:00–07:00 siguiente, 08:15–16:45 y 24 h válidos.
- Disponibilidad de 1 h y 25 h con mismos relojes, cruce de año y segmento reanudado.
- Plan semanal y cada patrón avanzado; ocurrencia intacta, modificada, eliminada,
  excepción protegida y plan finalizado.
- Jornada futura, en curso, finalizada, con horario real, ausente, cancelada
  y protegida por médica/vacaciones; reloj de pruebas fijo para cada límite.
- Nota sólo texto, sólo imagen y ambas; imágenes ficticias con hash conocido
  e identidades/archivos independientes. Ninguna foto de galería personal.
- Meta no configurada/desconocida/no usada/fija/por período; mensual/semanal/ciclo.
- Extra explícita que cuenta/no cuenta para meta y tiene/no tiene resumen separado;
  clase activa/archivada y snapshots históricos.
- Feriado nombrado/sin nombre y vacaciones/médica con cruces, bordes y conflicto.
- Copias V5/V6/V7/V8 genuinas, vacías/pobladas, fotos/notas/situaciones, cifradas
  y sin cifrar; truncadas, corruptas, contraseña errónea y otra línea temporal.
- Widget en tres modos, tres privacidades, compacto/ampliado, varias instancias,
  cero/un evento y más de tres simultáneos, caché climática válida/inválida.
- Clima deshabilitado, sin ubicación, fuera de ventana, parcial/completo, caché
  fresca/antigua/vencida y error del proveedor.
- Carga/error y dato fuente cambiado/eliminado entre preview y confirmación.

No cambiar reloj global, zona, permisos globales o tamaño de letra del Samsung.
Usar Clock/contextos de prueba existentes. Para el límite nativo de la hoja usar
animación/reloj Android reales, no acelerar Compose para ocultar el defecto.

### 6.2 Oráculos de negocio y persistencia

- Todo cálculo usa minutos/intervalos exactos. No redondear a horas enteras para
  que coincida. Una extra es expresa; superar meta no la crea.
- Comparar conjuntos por UUID, snapshots y asociados, no por cantidad o texto.
- Ordenar por claves canónicas antes de calcular hash lógico de tablas.
  Registrar tabla/esquema/filas y diferencias, sin contenido privado en logs.
- Copias: comparar Room, 17 preferencias portables e imágenes por bytes/SHA.
  Comparar catálogo, situaciones, rangos, notas/evidencias y claves de archivos.
- Cancelar/consultar no modifica negocio. Registrar por separado caché técnica,
  evidencia propia, profileInstalled y preferencias modificadas conscientemente.
- Un fallo CAS o conflicto invalida TODO el lote; ninguna escritura parcial.
- Respuesta perdida/doble toque no duplica operaciones, jornadas, notas o adjuntos.
- Limpiar no equivale a Sin definir y no elimina fechas vecinas.
- Restore no sirve como “limpieza” del fixture recibido ni como forma de fabricar
  igualdad después de haberlo alterado.
- Proceso realmente terminado/restaurado, Activity.recreate, cambio de orientación
  y fake de SavedStateHandle son cuatro niveles diferentes; rotularlos.

### 6.3 UI, sistema y revisión visual

Por cada familia de pantallas: estado normal, vacío, cargando, error/reintento,
guardando, inválido, confirmación y retorno, si existe ese estado en el producto.
No inventar pantallas de error o botones que no existen para llenar una matriz.

Usar temas Claro/Oscuro y comprobar Sistema; configuraciones Android de texto
1/1,5/2 en contextos QA. Usar retrato, paisaje y ventanas bajas, con objetivos
táctiles de 48 dp base, sin solapamientos ni recorte de información crítica.
Incluir las ventanas pequeñas 320×480 y 360×640 cuando el host admita el caso;
distinguir viewport simulado, ventana Android emulada y tamaño físico del Samsung.

Comprobar foco de teclado, IME, siguiente/anterior, validación y Atrás en campos
de fecha/hora/nombre/nota; no perder borrador al cerrar teclado ni dejar botones
inaccesibles. Las hojas se desplazan si su contenido lo exige; el Calendario
mensual principal no adquiere scroll vertical.

La revisión visual completa incluye Calendario, próxima jornada, detalle,
selección/carga/catálogo, configuración inicial/laboral, repeticiones, horas,
extras, disponibilidad, situaciones, feriados, vacaciones, notas/fotos, Resumen,
Informes, Copias, Avisos, Clima, Widget, bloqueo, Apariencia, Ayuda y Actualizaciones.
Capturar controles/estados secundarios necesarios, no sólo una portada bonita.
Crear índice navegable de imágenes locales, sin recursos remotos, con ID,
pantalla/estado, tema, letra, ventana, candidata y fixture.

La comprensión estética humana y el anuncio audible de TalkBack requieren a
Joaquín. No decir “intuitivo para usuarios” sólo porque un test encontró testTag.
Medir descubribilidad con entradas/etiquetas correctas, orden de acción,
confirmaciones y retorno sin documentación externa; registrar la interpretación
humana como tal. No rediseñar Vigilia ni cambiar vocabulario aprobado por gusto.

## 7. EJECUCIÓN, CORRECCIÓN Y REPETICIÓN

### 7.1 Primera ronda completa

Recorrer los IDs en orden C → A → W → X; aplicar V a cada familia. Se pueden
agrupar métodos para ahorrar arranques, pero no saltar IDs. Registrar cada
subcaso y no detener todos los demás casos independientes por el primer fallo.

Para cada falla:
1. Guardar adverso original, entradas, candidato, pasos, observado/esperado,
   captura segura o evidencia textual, excepción y diferencia de estado.
2. Confirmar si la causa es producto, fixture, arnés, entorno o autoridad.
   Una carrera o intermitencia exige reproducción, no sólo más espera.
3. Crear regresión que falle en el código anterior por la causa correcta.
4. Corregir el mínimo causal y revisar consumidores equivalentes.
5. Demostrar la regresión verde y las interacciones afectadas; conservar el rojo.
6. Volver a la función desde una entrada nueva, no continuar sobre un estado
   accidental que sólo existe después de un fallo.
7. Registrar commit NO realizado, delta propio y artefacto nuevo si cambió.

No cambiar el valor esperado, el número de muestras, selección, percentiles,
umbral o estado de aprobación para esconder una falla. Una corrección de fixture
necesita prueba de que la anterior no representaba el dominio, con expectativa
independiente conservada.

### 7.2 Candidata final y dos rondas completas

Joaquín pidió repetir desde el primer punto al último; esta repetición completa
final es intencional, no una rutina en cada microcambio.

- Durante diagnóstico: repetir sólo falla y regresiones relacionadas.
- Tras corregir: congelar fuentes/QA/runner/fixture/criterios y ejecutar UNA
  ronda integral completa sobre esa candidata.
- Si pasa: repetir TODA la matriz exigida en una segunda ronda independiente,
  con raíz/estado de prueba propios equivalentes y la misma candidata.
- Cualquier nueva corrección cambia el objeto probado: ambos cierres deben
  pertenecer a la candidata final, no sumar verdes de APK diferentes.
- No ejecutar destrucciones sobre la sesión recibida “otra vez”. Repetir
  pruebas destructivas únicamente dentro de sus contextos sintéticos aislados.
- Dos ciclos sin evidencia nueva del mismo fallo obligan a diagnosticar y
  devolver gate concreto a MAIN; no lanzar intentos indefinidos.
- El cierre requiere dos rondas completas sin fallas, omitidos, bloqueos ni
  pendientes obligatorios. Un límite real produce entrega PARCIAL, no verde.
- Si una intervención humana obligatoria no está disponible, registrar quién
  y qué falta; continuar lo independiente, sin aceptar un diálogo por Joaquín.

### 7.3 Rendimiento: límite preciso

Stage8 cerró su campaña con guardado sin alerta y causa histórica de Resumen
INCONCLUSA; un par mostró +29,85 %. No borrar ese antecedente ni repetir ABBA
sin un contrato de experimento aparte. No optimizar a ciegas.

En Stage12 comprobar apertura/contenido listo, respuesta de botones, bloqueos,
doble acción, ANR y errores del hilo principal por el recorrido afectado. Registrar
relojes, condición fría/caliente, métricas y fixtures; no afirmar FPS ni que una
causa histórica está resuelta por una lectura rápida. Un defecto reproducible
actual dentro de una función se corrige con causa/regresión mínima; otra campaña
de rendimiento o rediseño arquitectónico requiere decisión de MAIN.

## 8. ESQUEMA DE RESULTADOS Y REGISTRO DE BUGS

### 8.1 Estados cerrados

| Estado | Condición exacta |
|---|---|
| SIN_EJECUTAR | No existe ejecución de ese subcaso/artefacto en Stage12. |
| PASA | Todas las aserciones exigidas del subcaso pasaron y hay recibo válido del nivel requerido. |
| FALLA | Se ejecutó y al menos una aserción exigida no se cumple. |
| BLOQUEADO | No pudo ejecutarse por requisito/permiso/recurso; identificar acción pendiente y dueño. |
| NO_APLICA | Rama inexistente en esa configuración concreta; justificación verificable. No elimina una función real del inventario. |

La fila padre sólo PASA si TODOS sus subcasos obligatorios pasan. Una prueba
compilada, omitida, con timeout, sin protocolo terminal, con count diferente,
salida parcial o hash no coincidente no PASA. N/A no puede sustituir un OEM
pendiente ni un módulo difícil. Una falla histórica permanece en el historial
aunque el último estado sea PASA.

### 8.2 Archivos de salida

Crear bajo .local-evidence/stage12/<run-id>/:

- plan-casos.json: matriz congelada y trazabilidad de todos los IDs/subcasos.
- resultados.jsonl: eventos append-only; cada intento incluye ID, subcaso,
  ronda, fecha UTC, comando/selector, base/insumos, QA/runner, fixture, entorno,
  nivel, precondición, esperado, observado, estado, duración y ruta/SHA del recibo.
- resultados.csv: vista final legible, una fila por subcaso y ronda, no otro
  estado operativo del proyecto; derivarla del JSONL sin editar estados a mano.
- bugs.json y bugs.md: ID, prioridad, clasificación, causa, ruta/línea, reproducción,
  impacto, solución, archivos, adverso, regresión roja/verde, consumidores y estado.
- capturas/ e index.html: capturas propias seguras, metadatos y revisión; excluir
  overlays/personales. Originales inmutables, no maquetas como evidencia instalada.
- manifest.json: rutas/tamaños/SHA-256 y origen de datos/artefactos/recibos.
- writer-release.json: recursos cesados, archivos liberados, sello y clave de entrega.

Cada evento debe indicar qué es ejecución nueva, reutilización histórica,
simulación o revisión humana. El CSV nunca pierde los intentos fallidos.

Handoff compacto en docs/HANDOFF_STAGE_12_A_MAIN_3_0.md, con requisitos,
delta propio vs entrada, resultados por ID/ronda, fallos corregidos, pendientes,
riesgos, artefactos, recepción y próximo dueño. Auditoría propia fechada en
docs/audits/2026-10-23-stage12-verificacion-integral.md (o fecha real del cierre).
Estas dos rutas son salidas futuras: no crear placeholders de “aprobado”.

## 9. ALCANCE TÉCNICO Y EXCLUSIONES (SCOPE / DO NOT)

Permitido al ejecutar:
- Pruebas Kotlin/JVM/Android y herramientas test-only necesarias para la matriz,
  con catálogo actualizado y adversos de los propios runners.
- Correcciones mínimas de bugs reproducidos de funciones existentes, incluyendo
  B01/B02 y sus consumidores afectados, sin introducir nuevas capacidades.
- Cableado test-only indispensable para aislamiento/runner/captura; preservar
  valores por defecto y opciones existentes. Sin nuevas dependencias/plugins/variantes.
- Evidencia, catálogo, auditoría propia y handoff del Stage12.
- MAIN revisa cambios productivos, causas y fronteras antes de integrarlos.

Prohibido:
- Reset/clean, descartar entrada, cambiar ramas/worktrees o importar árboles 2.0.
- pm clear, desinstalar, resembrar QA recibida, borrar 1956 jornadas, manipular
  datos personales o usar la pública como fixture.
- Cambiar Room/esquemas/migraciones, formato/cifrado/Copias, cálculos laborales,
  reglas de privacidad, autenticación, red/permisos, applicationId, versión,
  dependencias o clave de distribución para hacer pasar la matriz.
- “Resolver” un error retirando una función, un botón necesario, una confirmación,
  compatibilidad V5–V8, una aserción o un caso difícil.
- Reintroducir semana forzada, barra normal permanente, scroll mensual,
  zoom interno, controles TalkBack propios, nube/cuenta/beta/OCR/sueldos.
- Cambiar ajustes del teléfono o usar selector/galería/launcher con datos personales.
- Instalar otra app o herramienta no cubierta, activar servicios remotos,
  compartir datos laborales, firmar/publicar o aceptar decisiones humanas.
- Capturar pantalla completa de Android, incluido emulador, para recortarla después.
- Declarar “todo funciona” basándose sólo en build, una clase verde o relato.

Si una falla exige cambiar una frontera prohibida, conservar el adverso y plantear
a MAIN la decisión concreta, alternativas y riesgo. No ampliar el alcance en silencio.

## 10. VALIDACIÓN, DEPENDENCIAS Y TERMINADO (VALIDATION / DEPENDENCIES / DONE WHEN)

### 10.1 Comandos y criterios de atribución

Seguir docs/VALIDACION.md con Java/JBR/SDK explícitos de sesión, sin instalación
automática ni cambio de versiones. Python sin optimización; no usar assert como
única guarda de seguridad del runner.

Para documentación: tools/docs_check.py y git diff --check.
Para herramientas: dobles adversos y unittest pertinentes.
Para cambios Kotlin/UI: perfil local fresco y build una vez que la unidad se
estabilice; después sólo delta afectado hasta congelar candidata final.
Interoperabilidad sólo si se toca su frontera, con checkout web explícito.
Android por catálogo/selector revisado, nunca selección indiscriminada.

Guardar XML reales, conteos, omitidas, tareas ejecutadas/reutilizadas, insumos
antes/después y hashes. Estabilidad de inputs y hash no sustituyen conducta.
Guardas host se prueban con exit no cero, Failure lógico con exit cero, timeout,
salida parcial, falta de bundle/hash, APK/runner equivocados y éxito válido.
No instalar hasta superar validación PRE-install; no marcar verde sin evidencia
extraída íntegra y protocolo terminal correcto.

### 10.2 Dependencias y gates

- Entrada/sello y plan/aislamiento revisados antes de pruebas con escritura.
- Captura sólo ventana QA validada antes de fotografiar de nuevo.
- Identidad/foco/guardas antes de cada bloque Android autorizado; Samsung sigue HOLD.
- Autenticación/selector/voz/launcher sólo con intervención autorizada y sin
  datos personales; falta de intervención no se suple con un fake presentado como OEM.
- MAIN acepta evidencia técnica; Joaquín acepta estética/uso humano.
- Upgrade público, firma/distribución y causa histórica R4 son puertas separadas.

### 10.3 Cierre aceptable

El Stage12 sólo se declara terminado integralmente cuando:

1. Los 425 puntos y sus subcasos están trazados, sin filas perdidas o eliminadas.
2. Los 16 criterios visuales se aplicaron a todas las familias pertinentes,
   con evidencia legible y distinción entre render QA y OEM.
3. B01/B02 tienen regresión roja válida, corrección mínima y pruebas verdes.
4. B03/B04 tienen guardas verificadas; ninguna conservación depende de reparar
   silenciosamente el estado después de la prueba.
5. Dos rondas completas consecutivas de la misma candidata final pasan todos
   los subcasos obligatorios, sin omitidos ni pendientes ocultos.
6. Las tablas, preferencias y archivos recibidos permanecen preservados; sólo
   mutaciones propias de ensayo justificadas y datos de evidencia agregados.
7. Producto/pruebas/recibos/catálogo/docs coinciden y el diff propio está auditado.
8. Hay handoff, manifiesto, sello y relevo real de archivos/Gradle/dispositivo.
9. MAIN acusa RECIBIDO y realiza auditoría independiente proporcional de delta,
   invariantes, adversos, protocolos, hashes y comportamientos críticos.
10. No se transforma este cierre en aceptación visual de Joaquín, rendimiento
    global aprobado ni permiso de firma/distribución.

Si falta un requisito material o humano, entregar PARCIAL/BLOQUEADO con el
subconjunto comprobado y el gate exacto. Ésa es una salida honesta, no un pase.
No prolongar herramientas/documentación indefinidamente ni repetir el mismo
fallo sin evidencia nueva. Informar a MAIN en español: resultado, causa, evidencia,
límite y siguiente acción.

## ANEXO A — Inventario funcional numerado, una comprobación por fila

Todas las filas empiezan SIN_EJECUTAR. Las clases siguientes son **candidatas a reutilización**, no prueba de ejecución ni mapeo suficiente: el especialista debe enlazar cada ID a aserciones concretas y revisar efectos/aislamiento. Las fuentes están contrastadas con el árbol actual; sus nombres abreviados se resuelven al final.

### C · Calendario mensual

Fuentes: CAL, APP, CVM. Pruebas localizadas: CalendarComposeTest; CalendarStaticLayoutPolicyTest; CalendarAdaptiveLayoutComposeTest; CalendarCommonV2ComposeTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C001 | Mostrar el mes completo en cuatro, cinco o seis filas; incluir febrero bisiesto. |
| C002 | Retroceder un mes, incluido enero a diciembre del año anterior. |
| C003 | Avanzar un mes, incluido diciembre a enero del año siguiente. |
| C004 | Cambiar de mes por gesto horizontal sin confundir un toque o un gesto corto. |
| C005 | Volver al mes actual mediante Ir a hoy del menú. |
| C006 | Distinguir carga inicial de contenido listo; no habilitar decisiones con datos incompletos. |
| C007 | Mostrar error de lectura del mes y permitir reintentar; no simular un mes vacío. |
| C008 | Conservar marcadores previos ante error parcial de extras/disponibilidad y reintentar su fuente. |
| C009 | Adaptar mes completo a ventanas bajas/anchas con franja lateral, sin scroll mensual ni semana forzada. |
| C010 | Mostrar confirmaciones y aviso breve del encabezado; abrir su explicación cuando falta alto. |
| C011 | Identificar fecha, hoy, estado y condiciones mediante texto/iconos/semántica, además del color. |

### C · Tarjeta superior

Fuentes: NEXT. Pruebas localizadas: NextEventComposeTest; NextEventTest; NextEventObserverInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C012 | Mostrar jornada en curso y tiempo restante hasta finalizar, con transiciones en los límites exactos. |
| C013 | Mostrar próxima jornada, Hoy/Mañana/fecha y cuenta regresiva en días, horas y minutos. |
| C014 | Conservar la próxima futura cuando las de hoy ya terminaron o fueron canceladas/protegidas. |
| C015 | Mostrar institución, color, horario y puesto de la principal sin notas ni motivos privados. |
| C016 | Distinguir varias jornadas/eventos simultáneos sin fusionar identidades. |
| C017 | Identificar disponibilidad activa/próxima como disponibilidad, no como horas trabajadas. |
| C018 | Mostrar próximo Franco explícito; no convertir un día sin jornadas en Franco. |
| C019 | Mostrar un estado honesto cuando no existe próximo evento. |
| C020 | Expandir/contraer las jornadas de hoy sólo si agrega información; no repetir la única principal. |
| C021 | Conservar por UUID las jornadas completadas, ausentes, canceladas o protegidas de la lista. |
| C022 | Restaurar expansión sólo para el mismo día; conservar último contenido válido y reintento ante error. |

### C · Detalle del día

Fuentes: DAY, APP, ACT. Pruebas localizadas: CalendarComposeTest; CalendarCommonV2ComposeTest; V2ShiftActualComposeTest; FinalQaReadOnlyReviewTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C023 | Toque breve abre la fecha humana completa y Editar día. |
| C024 | Editar día entra directamente a la carga con la fecha exacta, sin menú intermedio redundante. |
| C025 | Mostrar tarjetas independientes por jornada ordenadas por inicio, incluso con objetivo/horario iguales. |
| C026 | Mostrar institución, dirección, horario completo, cruce de fecha, estado y puesto sin recortes. |
| C027 | Mostrar Condición del día sólo si existe una condición, sin tarjetas vacías. |
| C028 | Abrir notas de la jornada exacta, reemplazando la hoja sin dejarla por encima. |
| C029 | Abrir avisos particulares de la jornada exacta. |
| C030 | Abrir Clima conservando el mismo día como destino de retorno. |
| C031 | Mostrar carga/error/reintento de horario real sin inventar contenido vacío. |
| C032 | Abrir una dirección en app compatible y manejar ausencia de Maps u otra app. |
| C033 | Abrir Otras opciones del día sin alterar el acceso directo a carga. |
| C034 | Cerrar y reabrir hoy/futuro sin desaparición involuntaria; Clima → Cerrar → mismo día. |

### C · Selección múltiple y acciones masivas

Fuentes: CAL, CVM, APP, BATCH. Pruebas localizadas: CalendarInteractionStateTest; CalendarBackPriorityTest; CalendarCommonV2ComposeTest; DayOffPersistenceInstrumentedTest; CalendarClearInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C035 | Pulsación larga nativa inicia selección e incluye el día pulsado. |
| C036 | Seleccionar días del menú ofrece la misma selección sin necesitar gesto sostenido. |
| C037 | Toques agregan/quitan fechas sin abrir el detalle; manejar selección vacía y fuera del mes. |
| C038 | Contador, Continuar y Cancelar aparecen sólo durante selección; cero fechas bloquea Continuar. |
| C039 | Separar selección GENERAL, MANUAL_LOAD y HOLIDAY; cada Atrás tiene un único dueño. |
| C040 | Pedir confirmación al cambiar de mes con fechas elegidas; conservar o limpiar conscientemente. |
| C041 | Cancelar selección general sin escribir ni reabrirla automáticamente. |
| C042 | Transferir el conjunto elegido a carga manual conservando fechas y propósito correcto. |
| C043 | Marcar Franco en lote después de revisar; un conflicto no permite una escritura parcial. |
| C044 | Marcar Sin definir sin borrar jornadas ni condiciones; distinguirlo de Limpiar. |
| C045 | Agregar Retén/disponibilidad por fechas con etiqueta e intervalo; cada fecha posee su registro. |
| C046 | Revisar fechas aplicables, iguales y conflictivas antes de confirmar; no guardar lotes vacíos. |
| C047 | Revalidar lote frente a cambios concurrentes y rechazar todo sin escritura parcial. |

### C · Carga unificada

Fuentes: FORM, LOAD, LVM, NOTE, APP. Pruebas localizadas: V2ManualShiftLoadComposeTest; V2ManualShiftLoadActivityTest; V2ManualShiftLoadCoordinatorTest; ManualLoadNotesInstrumentedTest; CalendarClearInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C048 | Mostrar Cargando jornada para los días y las fechas exactas del borrador. |
| C049 | Resumir hasta tres fechas; Mostrar todos abre lista vertical ordenada con día de semana; contraer sin modificar selección. |
| C050 | Desplegar y contraer Mostrar instituciones o servicios. |
| C051 | Ordenar catálogo y distinguir institución, tipo y horario aunque se parezcan visualmente. |
| C052 | Crear institución/servicio y su primer horario desde la sesión; cancelar no borra catálogo ya confirmado. |
| C053 | Elegir un horario por identidad exacta y mostrar su resumen fuera del catálogo. |
| C054 | Agregar un horario a una institución existente, incluso si todavía no tiene horarios. |
| C055 | Agregar, editar, contraer o quitar puesto/función sin alterar el catálogo. |
| C056 | Agregar, editar o contraer la nota opcional sin perder texto. |
| C057 | Elegir, ver o quitar imágenes del borrador; cada jornada guardada recibe archivos independientes. |
| C058 | Volver a selección conservando horario, puesto, nota e imágenes, incluso al vaciar/reseleccionar y recrear. |
| C059 | Guardar directamente sin obligar a abrir una revisión opcional. |
| C060 | Ver fechas y detalles con instantáneas y horario nocturno completos antes de guardar. |
| C061 | Confirmar uso en fechas anteriores antes de extender la vigencia de configuración. |
| C062 | Sobrescribir sólo intervalos incompatibles y Franco, con pérdidas explícitas; preservar otros intervalos y vecinos. |
| C063 | Agregar sólo en días libres de un lote ocupado; no ofrecer un guardado sin fechas. |
| C064 | Superponer conservando jornadas y Franco previos. |
| C065 | Exigir confirmación explícita de advertencias: solapamiento, descanso corto y protecciones. |
| C066 | Marcar Franco sin elegir institución ni horario, respetando la puerta de configuración necesaria. |
| C067 | Quitar sólo Franco, conservando jornadas y otras condiciones. |
| C068 | Limpiar días con revisión de pérdidas, división de rangos y preservación de fechas vecinas. |
| C069 | Cancelar carga o descartar borrador conscientemente; Atrás inicial cancela y Atrás posterior vuelve a elegir fechas. |

### C · Situaciones y condiciones

Fuentes: DAY, CVM, AV. Pruebas localizadas: CalendarCommonV2ComposeTest; ShiftSituationPersistenceInstrumentedTest; CalendarViewModelStage2Test; AvailabilityComposeTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C070 | Agregar carpeta médica por rango inclusivo y nota privada; validar fechas y conflictos con vacaciones. |
| C071 | Editar rango y nota privada de una carpeta médica existente. |
| C072 | Eliminar carpeta médica con confirmación sin eliminar jornadas. |
| C073 | Registrar ausencia en una jornada concreta, con motivo/nota opcionales. |
| C074 | Registrar cancelación con motivo obligatorio, actor, aviso y evidencia privada opcional no clínica. |
| C075 | Editar ausencia/cancelación o volver a Planificada con pérdidas asociadas explicadas. |
| C076 | Abrir registro, corrección y eliminación de extra independiente para el día exacto. |
| C077 | Crear Retén y consultar/corregir/eliminar ventanas del día sin cambiar la configuración global. |

### C · Notas, avisos, Clima y horario real contextuales

Fuentes: EXC, NOT, WEA, ACT. Pruebas localizadas: ExceptionsComposeTest; NotificationComposeTest; WeatherComposeTest; WeatherViewModelTest; V2ShiftActualComposeTest; V2ShiftActualCoordinatorTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C078 | Crear, editar, cancelar o eliminar notas privadas de la jornada exacta; vacío no se guarda. |
| C079 | Agregar, ver, reemplazar o quitar adjuntos privados con confirmación y conservación del borrador. |
| C080 | Heredar avisos generales, personalizar anticipos o desactivar sólo una jornada; restaurar los generales. |
| C081 | Explicar proveedor/privacidad antes de activar Clima conscientemente, sin permisos silenciosos. |
| C082 | Explicar ubicación faltante e impedir refresco imposible; dirección textual no equivale a coordenadas. |
| C083 | Mostrar cobertura, temperaturas, lluvia, viento, horas y atribución de toda la jornada, incluida una nocturna. |
| C084 | Actualizar Clima y distinguir fresco/antiguo/vencido; conservar caché ante error y mostrar límites. |
| C085 | Registrar/corregir inicio y fin reales sólo cuando corresponde; conservar fecha, zona y límites temporales. |
| C086 | Clasificar diferencia como normal o extra expresa; elegir/crear clase y fragmentos con suma exacta. |
| C087 | Revisar y guardar horario real con identidad y retorno correctos, sin duplicar guardados. |
| C088 | Volver al horario planificado con confirmación y manejo de conflicto/pérdidas. |

### C · Edición y entradas vinculadas

Fuentes: EDIT, REP, APP, EXC, DAY. Pruebas localizadas: V2ShiftEditComposeTest; V2ShiftEditCoordinatorTest; V2RecurringPlanComposeTest; ExceptionsComposeTest; VacationComposeTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C089 | Editar jornada individual manteniendo fecha fija, puesto/plantilla histórica y revisión explícita. |
| C090 | Eliminar el UUID confirmado sin borrar otra jornada visualmente igual. |
| C091 | Distinguir una ocurrencia de cambiar/finalizar una serie desde esa fecha; preservar el pasado. |
| C092 | Entrar a repetición, revisar/confirmar y gestionar planes existentes desde su configuración. |
| C093 | Seleccionar feriados en la misma grilla, editar fecha y resolver Reemplazar/Conservar/Cancelar. |
| C094 | Consultar vacaciones del mes y reflejar sus protecciones en calendario y detalle. |
| C095 | Abrir Fotos del mes del calendario visible y retornar al mismo contexto. |

### C · Restauración e integridad del calendario

Fuentes: APP, CVM, LVM, BATCH, NOTE. Pruebas localizadas: V2ReadyCalendarRecreationActivityTest; V2ManualShiftLoadActivityTest; V2ManualShiftLoadCoordinatorTest; ManualLoadNotesInstrumentedTest; FinalQaReadOnlyReviewTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| C096 | Restaurar mes, selección, propósito, detalle y borrador sin guardar automáticamente. |
| C097 | Invalidar institución/horario cambiado o archivado durante selección/recreación; exigir reselección. |
| C098 | Evitar doble guardado y recuperar respuesta perdida sin duplicar jornadas, notas o imágenes. |
| C099 | Rechazar sobrescritura/limpieza si cambiaron fuentes, asociados, rangos o catálogo desde la revisión. |
| C100 | Consultar/cerrar/retroceder sin escribir negocio; distinguir caché técnica de preferencias consentidas. |

### A · Navegación y arranque

Fuentes: APP, SETUP, HELP. Pruebas localizadas: NavigationDrawerComposeTest; WorkSetupComposeTest; HelpComposeTest; HelpTourActivityTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A001 | Abrir el menú lateral mediante su botón accesible. |
| A002 | Desplazar el menú para alcanzar todas sus secciones y acciones. |
| A003 | Elegir un destino cerrando el menú antes de navegar. |
| A004 | Cerrar primero el menú al pulsar Atrás. |
| A005 | Volver desde Resumen, Apariencia o Ayuda al Calendario. |
| A006 | Impedir abrir el menú mientras otra superficie o borrador posee el control. |
| A007 | Preservar destino y jerarquía de retorno tras recreación. |
| A008 | Mostrar preparación o error recuperable al resolver configuración laboral. |
| A009 | Ofrecer restaurar una Copia en el arranque sin configuración. |
| A010 | Decidir la guía inicial según configuración y versión completada. |
| A011 | Avanzar y retroceder por la introducción automática. |
| A012 | Omitir la guía automática sólo con confirmación. |
| A013 | Completar y recordar versión de guía sin repetirla indebidamente. |
| A014 | Restaurar paso de la guía tras recreación. |
| A015 | Mostrar error de lectura/guardado de la guía y permitir reintentar. |

### A · Mi forma de trabajar y catálogo

Fuentes: SETUP, APP, LOCATION. Pruebas localizadas: WorkSetupComposeTest; WorkSetupCoordinatorTest; ContextualWorkCreationTest; ContextualLayoutComposeTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A016 | Seleccionar explícitamente Vigilancia privada en la primera configuración. |
| A017 | Seleccionar explícitamente Policía en la primera configuración. |
| A018 | Seleccionar explícitamente Enfermería en la primera configuración. |
| A019 | Seleccionar explícitamente Medicina en la primera configuración. |
| A020 | Adaptar nombres y ejemplos al sector, sin llamar objetivo a todos los lugares. |
| A021 | Consultar el único sector/configuración laboral vigente. |
| A022 | Consultar cantidades de lugares, tipos y horarios activos. |
| A023 | Crear primer lugar con nombre y abreviatura de tres a cinco caracteres. |
| A024 | Agregar dirección opcional del lugar. |
| A025 | Agregar nota privada opcional del lugar. |
| A026 | Configurar una franja nocturna opcional válida. |
| A027 | Distinguir sábado/domingo por configuración expresa. |
| A028 | Elegir separación de horas de fin de semana en Resumen. |
| A029 | Distinguir feriados por configuración expresa. |
| A030 | Elegir separación de horas de feriado en Resumen. |
| A031 | Crear primer tipo/horario con inicio, final y color. |
| A032 | Introducir horas con separador automático y validación. |
| A033 | Explicar cruce nocturno y jornada de 24 horas antes de guardar. |
| A034 | Elegir o cambiar color del horario. |
| A035 | Agregar horario a lugar y tipo activos existentes. |
| A036 | Agregar otro lugar sin reemplazar el actual ni reescribir historia. |
| A037 | Terminar creación y elegir volver, agregar horario o agregar lugar. |
| A038 | Cancelar borrador sucio con descarte consciente. |
| A039 | Recuperar creación contextual incierta mediante Comprobar guardado. |
| A040 | Abrir herramientas opcionales desde Opciones avanzadas. |
| A041 | Convertir dirección del lugar a ubicación aproximada mediante acción y confirmación conscientes. |
| A042 | Solicitar ubicación aproximada actual sólo por acción expresa y permiso contextual. |
| A043 | Quitar ubicación meteorológica del lugar. |

### A · Repeticiones

Fuentes: REP. Pruebas localizadas: V2RecurringPlanComposeTest; V2RecurringPlanActivityTest; V2RecurringPlanCoordinatorTest; RecurringPlanPersistenceInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A044 | Abrir lista de repeticiones guardadas. |
| A045 | Abrir detalle e historial de revisiones de un plan. |
| A046 | Crear repetición eligiendo un horario existente. |
| A047 | Repetir en días de semana elegidos. |
| A048 | Repetir cada N días. |
| A049 | Repetir cada N semanas. |
| A050 | Repetir un día ordinal de cada mes. |
| A051 | Definir inicio/final con fecha de formato automático. |
| A052 | Agregar puesto o función opcional al plan. |
| A053 | Conservar registros existentes ante fechas ocupadas. |
| A054 | Reemplazar sólo jornadas automáticas intactas cuando ésa es la política elegida. |
| A055 | Mantener ambas jornadas expresamente ante ocupación. |
| A056 | Cancelar ante conflicto sin escribir el lote. |
| A057 | Revisar fechas, acciones, protecciones y advertencias antes de guardar. |
| A058 | Guardar plan y ocurrencias como una unidad coherente. |
| A059 | Cambiar plan desde hoy conservando pasado y excepciones. |
| A060 | Finalizar plan desde hoy conservando pasado y excepciones. |
| A061 | Descartar formulario o preview sin crear jornadas. |
| A062 | Distinguir lista vacía de error y reintentar lectura. |

### A · Feriados y vacaciones

Fuentes: EXC, VAC. Pruebas localizadas: ExceptionsComposeTest; VacationComposeTest; VacationViewModelInstrumentedTest; VacationPersistenceInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A063 | Consultar feriados locales/manuales del mes. |
| A064 | Elegir/cambiar fechas de feriado usando la selección del calendario principal. |
| A065 | Guardar uno o varios feriados con nombre opcional. |
| A066 | Editar feriado existente. |
| A067 | Resolver feriado coincidente con Conservar, Reemplazar o Cancelar. |
| A068 | Eliminar feriado con confirmación. |
| A069 | Cancelar selección/borrador de feriados sin reentrada ni escritura. |
| A070 | Consultar vacaciones por mes. |
| A071 | Crear vacaciones con fechas inclusivas y cantidad de días visible. |
| A072 | Editar vacaciones conservando su identidad. |
| A073 | Eliminar período completo con confirmación. |
| A074 | Rechazar vacaciones solapadas o en conflicto con carpeta médica. |
| A075 | Descartar borrador de vacaciones conscientemente. |
| A076 | Conservar borrador/confirmación ante conflicto concurrente y permitir reintento consciente. |

### A · Clases de horas extra

Fuentes: ACT. Pruebas localizadas: V2ShiftActualComposeTest; V2ShiftActualCoordinatorTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A077 | Abrir catálogo de tipos/clases de extra desde la configuración laboral. |
| A078 | Crear clase por nombre propio o sugerencia expresa. |
| A079 | Responder explícitamente si cuenta para la meta. |
| A080 | Responder explícitamente si tiene resumen separado. |
| A081 | Consultar ayuda contextual de ambas decisiones. |
| A082 | Editar clase sin reinterpretar extras históricos. |
| A083 | Archivar clase de extra. |
| A084 | Reactivar clase archivada. |
| A085 | Reintentar lectura del catálogo sin inventar clases ni reseleccionar silenciosamente. |

### A · Tus horas y tu meta

Fuentes: HOURS. Pruebas localizadas: HoursAndExtrasComposeTest; HoursAndExtrasActivityTest; HoursReferenceDraftTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A086 | Consultar avance, meta, período y horas pendientes. |
| A087 | Consultar disponibilidad separada de trabajo. |
| A088 | Elegir referencia Pendiente, No uso, Desconozco, Fija o Por período. |
| A089 | Configurar período mensual. |
| A090 | Configurar período semanal y primer día. |
| A091 | Configurar ciclo con longitud y fecha de anclaje. |
| A092 | Introducir meta positiva en minutos sin valor profesional predeterminado. |
| A093 | Elegir vigencia desde hoy, próximo período o fecha propia. |
| A094 | Revisar corte anterior y efecto del nuevo tramo. |
| A095 | Confirmar recálculo pasado o primer tramo corto cuando corresponde. |
| A096 | Guardar referencia después de revisar y revalidar. |
| A097 | Cargar valor faltante de un período variable. |
| A098 | Corregir valor de período con confirmación expresa. |
| A099 | Recuperar borrador revisado tras recreación. |

### A · Trabajo extra independiente

Fuentes: HOURS, ACT. Pruebas localizadas: HoursAndExtrasComposeTest; HoursAndExtrasActivityTest; HoursExtraRestorationRaceInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A100 | Iniciar extra independiente sólo para fecha válida hoy/pasada y catálogo utilizable. |
| A101 | Elegir lugar, tipo, horario opcional, clase y color. |
| A102 | Introducir inicio/final incluyendo cruces de fecha y más de 24 horas. |
| A103 | Agregar puesto/función opcional al extra. |
| A104 | Crear clase faltante conservando borrador del extra. |
| A105 | Revisar duración y si cuenta para meta. |
| A106 | Confirmar solapamientos o fechas protegidas cuando corresponde. |
| A107 | Guardar revalidando ocupación y protecciones. |
| A108 | Corregir extra independiente existente. |
| A109 | Eliminar sólo ese extra con confirmación, conservando jornadas y estados del día. |
| A110 | Recuperar borrador y rechazar restauración tardía que pise una corrección. |

### A · Disponibilidad

Fuentes: AV. Pruebas localizadas: AvailabilityComposeTest; AvailabilityActivityTest; AvailabilityCoordinatorTest; AvailabilityWindowPersistenceInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A111 | Consultar configuración y cantidad de ventanas guardadas. |
| A112 | Configurar el nombre Guardia pasiva. |
| A113 | Configurar el nombre Disponible para llamado. |
| A114 | Configurar el nombre Retén. |
| A115 | Elegir No uso disponibilidad. |
| A116 | Elegir vigencia y revisar impacto histórico. |
| A117 | Guardar configuración sin alterar referencia de horas. |
| A118 | Crear ventana con fecha dueña, inicio y final explícitos. |
| A119 | Crear ventana mayor de 24 horas o con cruce de mes/año. |
| A120 | Revisar duración y efecto de protecciones. |
| A121 | Consultar duración programada, efectiva, reemplazada y pendiente. |
| A122 | Corregir ventana conservando contexto histórico. |
| A123 | Eliminar ventana con confirmación. |
| A124 | Descartar borrador con decisión expresa. |
| A125 | Distinguir error de cálculo, último contenido válido y ausencia de ventanas. |
| A126 | Impedir superposiciones y detectar cambios posteriores a la revisión. |

### A · Resumen mensual

Fuentes: SUMMARY. Pruebas localizadas: SummaryComposeTest; SummaryActivityTest (revisar fixture antes de usar); SummaryViewModelTest; FinalQaReadOnlyReviewTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A127 | Abrir Resumen desde el menú. |
| A128 | Retroceder un mes en Resumen. |
| A129 | Avanzar un mes en Resumen. |
| A130 | Volver al mes actual en Resumen. |
| A131 | Consultar trabajo normal, extras, total y pendiente con minutos exactos. |
| A132 | Consultar avance por períodos de referencia. |
| A133 | Consultar disponibilidad sin sumarla a trabajo. |
| A134 | Consultar desgloses opcionales: noche, feriados, fin de semana, planificado/real, lugares, tipos, clases y situaciones. |
| A135 | Tocar una métrica y revisar sus contribuciones exactas. |
| A136 | Volver del desglose conservando desplazamiento del Resumen. |
| A137 | Restaurar detalle y mes después de recreación. |
| A138 | Mostrar introducción explicativa del Resumen. |
| A139 | Ocultar la introducción mediante Entendido. |
| A140 | Abrir personalización del Resumen. |
| A141 | Mostrar/ocultar familias opcionales. |
| A142 | Reordenar familias opcionales con controles accesibles. |
| A143 | Reintentar escritura fallida de personalización sin perder la superficie. |
| A144 | Distinguir carga, vacío, error y último contenido válido. |
| A145 | Abrir Informes desde menú o final del Resumen. |

### A · Informes

Fuentes: REPORTS. Pruebas localizadas: ReportsComposeTest; ReportsViewModelInstrumentedTest; MonthlyReportSnapshotInstrumentedTest; ReportDestinationWriterInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A146 | Preparar informe para el mes elegido en Resumen. |
| A147 | Elegir formato PDF. |
| A148 | Elegir formato XLSX/OOXML. |
| A149 | Revisar recuentos y contenido antes de exportar. |
| A150 | Optar por incluir nombre/apodo existente, sin inventar un editor de datos personales. |
| A151 | Optar por incluir puesto/función. |
| A152 | Optar por incluir notas de jornadas. |
| A153 | Incluir nota médica sólo con segunda confirmación. |
| A154 | Elegir individualmente hasta doce fotos mensuales para PDF. |
| A155 | Revisar advertencia de privacidad sobre fotos. |
| A156 | Retirar consentimiento de fotos al cambiar de PDF a XLSX. |
| A157 | Generar artefacto coherente sin mezclar distintas versiones de fuentes. |
| A158 | Guardar informe por selector documental. |
| A159 | Compartir informe con permiso temporal limitado al archivo exacto. |
| A160 | Regenerar explícitamente desde datos actuales. |
| A161 | Cancelar selector conservando artefacto privado válido. |
| A162 | Detectar foto seleccionada ausente y pedir deselección consciente. |
| A163 | Iniciar cada sesión con inclusiones privadas apagadas; conservarlas sólo en la misma sesión recreada. |
| A164 | Distinguir cargar, generar, guardar, compartir y error. |
| A165 | Volver al Resumen de origen. |

### A · Copias y restauración

Fuentes: BACKUP. Pruebas localizadas: BackupComposeTest; BackupRecoveryInstrumentedTest; BackupPhotoFilesInstrumentedTest; PortablePreferencesGatewayInstrumentedTest; suites Stage11 sólo con aislamiento revisado.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A166 | Abrir Copias y restauración. |
| A167 | Elegir si se incluyen fotos e imágenes de notas. |
| A168 | Comenzar creación con cifrado habilitado. |
| A169 | Introducir contraseña y confirmación válidas de ocho a 256 caracteres. |
| A170 | Mostrar/ocultar contraseña del borrador sin guardarla. |
| A171 | Elegir copia sin cifrado sólo tras advertencia expresa. |
| A172 | Preparar instantánea coherente y elegir destino documental. |
| A173 | Cancelar preparación/salida conservando estado anterior cuando la etapa lo permite. |
| A174 | Elegir archivo de Copia existente. |
| A175 | Desbloquear Copia cifrada sin almacenar la contraseña. |
| A176 | Rechazar contraseña incorrecta, integridad inválida, truncado o formato no admitido. |
| A177 | Revisar versión, nuevos/iguales, preferencias, fotos y conflictos antes de escribir. |
| A178 | Fusionar una Copia compatible. |
| A179 | Conservar registro actual al resolver un conflicto. |
| A180 | Elegir registro de la Copia al resolver un conflicto. |
| A181 | Conservar ambas identidades sólo cuando la resolución es válida. |
| A182 | Reemplazar todo sólo con pérdidas revisadas y frase exacta confirmada. |
| A183 | Bloquear fusión de otra línea temporal o complejidad excesiva sin impedir reemplazo consciente válido. |
| A184 | Impedir abandonar aplicación/recuperación atómica durante su etapa no cancelable. |
| A185 | Reintentar recuperación pendiente sin fingir éxito. |
| A186 | Finalizar restore y volver al Calendario con runtimes reconciliados. |
| A187 | Restaurar bytes auténticos de imágenes de la Copia aun con metadatos iguales a los actuales. |
| A188 | Conservar lectura de V5/V6/V7 además de V8. |

### A · Notas privadas y fotos mensuales

Fuentes: EXC, PHOTOS. Pruebas localizadas: ExceptionsComposeTest; PhotosComposeTest; SchedulePhotoBitmapDecoderInstrumentedTest; SchedulePhotoFileStoreInstrumentedTest; ManualLoadNotesInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A189 | Consultar notas por UUID sin confundir jornadas visualmente iguales. |
| A190 | Crear nota privada. |
| A191 | Editar nota existente. |
| A192 | Agregar imágenes elegidas conscientemente a una nota. |
| A193 | Abrir imagen privada de nota. |
| A194 | Quitar imagen del borrador sin reciclar un bitmap que sigue dibujándose. |
| A195 | Guardar nota/adjuntos conservando identidad y control concurrente. |
| A196 | Eliminar nota con confirmación. |
| A197 | Cancelar o descartar edición de nota conscientemente. |
| A198 | Abrir Fotos del mes del cronograma. |
| A199 | Cambiar mes de fotos. |
| A200 | Importar una o varias imágenes mediante Photo Picker. |
| A201 | Abrir foto en visor con ampliación/gesto de pinza y desplazamiento. |
| A202 | Asociar foto a objetivo activo. |
| A203 | Quitar asociación de objetivo sin borrar la foto. |
| A204 | Reemplazar foto conscientemente. |
| A205 | Eliminar una foto individual con confirmación. |
| A206 | Conservar orientación EXIF en miniatura, reemplazo y visor. |
| A207 | Mostrar foto ausente/error y reintentar sin sustituirla por otra. |

### A · Avisos y notificaciones

Fuentes: NOT. Pruebas localizadas: NotificationComposeTest; NotificationPreferencesInstrumentedTest; ShiftNotificationPresenterInstrumentedTest; NotificationDeferredActionsInstrumentedTest; NotificationAlarmEndToEndInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A208 | Consultar activación global de avisos. |
| A209 | Activar/desactivar avisos globales. |
| A210 | Solicitar permiso de notificaciones en contexto. |
| A211 | Distinguir permiso denegado de bloqueo en ajustes Android. |
| A212 | Abrir ajustes de notificaciones sólo de QA, con intervención cuando corresponda. |
| A213 | Consultar preview ficticio sin crear jornadas. |
| A214 | Enviar notificación de prueba ficticia de caducidad propia. |
| A215 | Elegir ritmo Acompañado, Esencial o Discreto. |
| A216 | Reconocer configuración Personalizada tras modificar detalles. |
| A217 | Elegir aviso previo de seis, ocho, doce o 24 horas. |
| A218 | Configurar varios anticipos avanzados. |
| A219 | Elegir aviso fijo durante evento o descartable. |
| A220 | Elegir sonido/vibración, sólo vibración o silencio. |
| A221 | Elegir sonido Android o restaurar predeterminado. |
| A222 | Solicitar opcionalmente puntualidad exacta sin prometer un despertador. |
| A223 | Elegir privacidad completa, reducida u oculta. |
| A224 | Consultar avisos ocultos aún vigentes. |
| A225 | Restaurar un aviso oculto vigente. |
| A226 | Restaurar todos los avisos ocultos vigentes. |
| A227 | Desactivar avisos sólo para una jornada. |
| A228 | Personalizar anticipos sólo para una jornada. |
| A229 | Restaurar configuración general para una jornada. |
| A230 | Abrir desde aviso el UUID/fecha exactos de jornada o disponibilidad. |
| A231 | Reconciliar edición, vacaciones, eliminación y recuperación sin resucitar descartados. |

### A · Clima global y proveedor

Fuentes: WEA, LOCATION. Pruebas localizadas: WeatherComposeTest; WeatherViewModelTest; WeatherInfrastructureInstrumentedTest; WeatherCreditsSessionInstrumentedTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A232 | Abrir configuración global de Clima. |
| A233 | Leer explicación y habilitar Clima conscientemente. |
| A234 | Deshabilitar Clima sin conceder permisos silenciosos. |
| A235 | Elegir Celsius/Fahrenheit sin redescargar. |
| A236 | Incluir Clima en avisos sólo con privacidad completa. |
| A237 | Consultar proveedor, privacidad, licencia y atribución. |
| A238 | Abrir sólo el enlace HTTPS de atribución seleccionado. |
| A239 | Borrar cachés meteorológicas sólo con confirmación. |
| A240 | Consultar intervalo meteorológico exacto de una jornada. |
| A241 | Actualizar manualmente pronóstico elegible. |
| A242 | Distinguir sin ubicación, fuera de ventana, caché antigua/vencida y cobertura incompleta. |
| A243 | Consultar temperatura, sensación, lluvia, viento y ráfagas disponibles por resumen/horas. |
| A244 | Conservar caché de otro objetivo al invalidar una ubicación concreta. |
| A245 | Manejar error de red o navegador sin perder retorno. |

### A · Bloqueo y privacidad de entrada

Fuentes: LOCK. Pruebas localizadas: AccessLockComposeTest; AccessLockManifestInstrumentedTest; AccessLockCoordinatorTest; AccessLockSessionTest; AccessLockWindowPolicyTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A246 | Consultar capacidad de seguridad del dispositivo. |
| A247 | Activar bloqueo mediante autenticación segura del dispositivo. |
| A248 | Elegir bloqueo inmediato al salir. |
| A249 | Elegir bloqueo tras un minuto. |
| A250 | Elegir bloqueo tras cinco minutos. |
| A251 | Elegir bloqueo tras quince minutos. |
| A252 | Cambiar plazo con autenticación si está activo. |
| A253 | Bloquear ahora. |
| A254 | Desactivar bloqueo mediante autenticación segura. |
| A255 | Desbloquear y recuperar superficie/borrador del proceso vivo. |
| A256 | Cancelar autenticación manteniendo cerrado el contenido. |
| A257 | Manejar falta de credencial, bloqueo temporal o hardware no disponible. |
| A258 | Reintentar lectura o reparar configuración fallida con autenticación. |
| A259 | Cubrir contenido durante recuperación/pérdida de privacidad/autenticación. |

### A · Apariencia, accesibilidad y Ayuda

Fuentes: APPEARANCE, HELP. Pruebas localizadas: AppearanceComposeTest; VigiliaThemeComposeTest; VisualSystemComposeTest; HelpComposeTest; HelpTourActivityTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A260 | Usar Vigilia oscuro por defecto sólo cuando no existe elección guardada. |
| A261 | Elegir tema claro. |
| A262 | Elegir tema oscuro. |
| A263 | Seguir tema del sistema. |
| A264 | Respetar letra nativa Android sin zoom interno. |
| A265 | Conservar semántica/foco TalkBack sin controles extra propios. |
| A266 | Expandir/contraer los nueve temas de Ayuda. |
| A267 | Repetir recorrido inicial después de completar configuración. |
| A268 | Avanzar/volver/cerrar replay sin omisión automática. |
| A269 | Usar anclas reales; explicar controles indisponibles sin inventarlos. |
| A270 | Volver a Ayuda tras replay sin reentrada ni escrituras laborales. |
| A271 | Mantener Ayuda consultable sin modificar datos laborales. |

### A · Actualizaciones y apoyo voluntario

Fuentes: UPDATES, SUPPORT. Pruebas localizadas: UpdatesScreenTest; UpdatesMenuComposeTest; UpdatesReturnComposeTest; UpdatesActivityTest; UpdatesOemSafActivityTest; SupportMenuComposeTest; SupportLinkInstrumentedTest; SupportMenuActivityTest.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| A272 | Abrir Actualizaciones sin buscar automáticamente. |
| A273 | Consultar versión instalada. |
| A274 | Buscar actualización conscientemente. |
| A275 | Distinguir sin publicación, actual, disponible e incompatible. |
| A276 | Descargar versión disponible con progreso. |
| A277 | Verificar APK antes de habilitar guardado. |
| A278 | Elegir destino y guardar APK por SAF. |
| A279 | Informar si el destino pudo releerse o sólo se verificaron bytes escritos. |
| A280 | Cancelar búsqueda/descarga/guardado y manejar interrupción. |
| A281 | Manejar respuesta inválida, indisponibilidad, error y espera de reintento. |
| A282 | Volver al destino anterior sin repetir consumo de solicitudes externas. |
| A283 | Explicar instalación manual sin lanzar instalador ni afirmar que guardar equivale a actualizar. |
| A284 | Abrir apoyo voluntario sin adjuntar datos laborales. |
| A285 | Evitar doble apertura/replay de apoyo tras foco, destino o bloqueo. |
| A286 | Mostrar error recuperable si no hay navegador o Android rechaza abrirlo. |

### W · Widget de inicio

Fuentes: WCONFIG, WUI, WMODEL, WRENDER, WRUNTIME, WPREF, WPROVIDER, WDOMAIN.
Pruebas localizadas: WidgetProjectionTest, WidgetRenderModelTest, WidgetWeatherTest, WidgetComposeTest, WidgetConfigurationActivityTest, WidgetRemoteViewsInstrumentedTest, WidgetPreferencesInstrumentedTest, WidgetBoundarySchedulerInstrumentedTest, WidgetManifestInstrumentedTest. **La clase de configuración modifica bloqueo/colas/host: no ejecutarla intacta sobre QA recibida.** Un render RemoteViews no demuestra launcher OEM, Doze ni cada toque integrado.

| ID | Función/comportamiento y resultado exigido |
|---|---|
| W001 | Abrir Widget de inicio desde Avisos y contexto; explicar alta desde Android sin inventar botón propio de pin. |
| W002 | Listar instancias ordenadas, con modo, privacidad, Clima y acción Configurar/Reconfigurar. |
| W003 | Distinguir gestión vacía, cargando y error con reintento; Cerrar siempre alcanzable. |
| W004 | Validar alta/ID de instancia; defaults Automático, privacidad Oculta y Clima apagado. |
| W005 | Modo Próxima jornada muestra primera futura y agrupa simultáneas; no muestra una ya activa como futura. |
| W006 | Modo Próximo Franco usa sólo F explícita de hoy en adelante. |
| W007 | Modo Automático comparte prioridad del motor para jornada/disponibilidad/franco/vacío, incluidas protecciones. |
| W008 | Privacidad Completa muestra datos laborales permitidos sin dirección, notas, fotos ni motivos privados. |
| W009 | Privacidad Reducida oculta institución, puesto y color histórico conservando estado/fecha/horario permitidos. |
| W010 | Privacidad Oculta no filtra fecha, horario, tipo, contador, simultáneos, color laboral ni Clima. |
| W011 | Clima por instancia sólo aparece ampliado+Completa+consentimiento global+caché/cobertura válidas. |
| W012 | Guardar/Continuar/Cancelar respetan borrador; guardado activo bloquea doble acción y permite reintentar fallo. |
| W013 | Recrear configuración preservando opciones, revalidando instancia y puertas antes de persistir. |
| W014 | Mantener opciones independientes entre varias instancias; no copiar configuraciones por error. |
| W015 | Responder a tamaños compacto/ampliado y resize sin confundir preview estática con render real. |
| W016 | Mostrar principal y hasta dos filas adicionales en ampliado; contador conserva total real de simultáneos. |
| W017 | Usar cuenta regresiva nativa de comienzo/final, ocultarla si venció y conservar horario absoluto. |
| W018 | Tocar tarjeta/fila abre UUID o fecha exacta; vacío o registro eliminado vuelve al Calendario de modo seguro. |
| W019 | Tocar crédito/Clima abre superficie interna de Clima; no abre Maps ni navegador automáticamente. |
| W020 | Respetar tema global, letra Android y accesibilidad, sin tema/zoom/TalkBack propios por instancia. |
| W021 | Mostrar carga, error y recuperación de datos con rutas de reintento/entrada protegida. |
| W022 | Actualizar por cambios de datos, preferencias, tema y caché sin escribir jornadas; evento no bloqueado por Clima. |
| W023 | Reaccionar a inicio/fin/medianoche, boot, reemplazo de paquete, hora/zona y configuración; no prometer exactitud OEM. |
| W024 | Eliminar/remapear/restaurar instancias selectivamente; diferir durante recuperación; Copias no recrea el launcher. |

## ANEXO B — Multifunciones: recorridos combinados de integridad

No aprobarlos por inferencia a partir de pantallas aisladas. Cada flujo necesita fuentes reales y observación posterior independiente de las mutaciones que afirma conservar.

| ID | Recorrido completo y resultado exigido |
|---|---|
| X001 | Crear institución/horario desde carga → volver con fechas, puesto, nota e imágenes intactos; catálogo confirmado no se borra al cancelar jornadas. |
| X002 | Modificar selección hasta vaciarla/recrearla → guardar sólo las fechas finales, una única vez y con archivos independientes. |
| X003 | Crear/editar jornadas múltiples o nocturnas → calendario/detalle/próxima jornada/Resumen/Widget coherentes por UUID y fecha dueña. |
| X004 | Configurar referencia de horas → períodos, Resumen e Informes coherentes; superar meta no crea una extra. |
| X005 | Cambiar clase extra → conservar instantáneas de horario real y extras independientes históricos. |
| X006 | Cruzar disponibilidad con jornadas, horario real, extras, vacaciones y médica → duración efectiva exacta sin sumar disponibilidad como trabajo. |
| X007 | Combinar plan recurrente con excepciones individuales y carga manual → preservar pasado/personalizadas, exclusiones y atomicidad/CAS. |
| X008 | Borrar/limpiar jornada con notas, imágenes y evidencia → explicar pérdidas, preservar vecinos y evitar archivos compartidos o borrados prematuros. |
| X009 | Fotografías mensuales → informe PDF → Copia → restore → mismas imágenes y privacidad consentida por sesión. |
| X010 | Crear/combinar/reemplazar Copia → Room, preferencias, imágenes y runtimes coherentes; todo aplicado o estado anterior recuperable. |
| X011 | Bloqueo/autenticación y selector externo → ocultar datos antes del desbloqueo; volver a borrador sin replay de resultados. |
| X012 | Notificación o Widget → jornada/disponibilidad exactas; UUID coincidente de otra familia no confunde destino. |
| X013 | Clima/ubicación → resumen contextual, avisos y Widget respetan objetivo, cobertura y privacidad; error de red no bloquea evento laboral. |
| X014 | Actualizador/apoyo → cerrar menú → salida externa consciente → retorno único sin instalar/publicar ni transmitir datos laborales. |
| X015 | Consultar Calendario, Resumen, próxima jornada y Widget → conservar todas las tablas y preferencias recibidas; distinguir cachés técnicas. |

## ANEXO C — Revisión visual y de uso transversal

Son criterios aplicables por familia/estado, no dieciséis funciones de negocio extra. Un resultado estético humano no se inventa a partir de geometría. Aplicar a pantallas, hojas, diálogos, formulario, teclado, RemoteViews y retornos pertinentes.

| ID | Criterio visual y de interacción |
|---|---|
| V001 | Textos completos, cifras junto a unidad y horarios/cruces comprensibles, sin elipsis que oculten información crítica. |
| V002 | Botones y objetivos táctiles contenidos, sin solapamientos; mínimo base vigente, incluida ventana baja. |
| V003 | Contraste de composición final medido según criterios vigentes, no sólo de colores planos. |
| V004 | Estados, selección, errores y completadas distinguibles por texto/icono además de color. |
| V005 | Acciones habilitadas diferenciadas de deshabilitadas y destructivas; un toque genera un solo callback. |
| V006 | Jerarquía de título, contenido y acción principal; instrucciones proporcionadas y sin hueco inferior accidental en hojas. |
| V007 | Mes entero, insets, paisaje/franja lateral y teclado sin recortar el control necesario ni tapar confirmaciones. |
| V008 | Carga, vacío, error, contenido previo, guardando y éxito honestos; botón de reintento y retorno claros. |
| V009 | Vocabulario coherente en los cuatro sectores; no mezclar objetivo/institución/servicio sin contexto. |
| V010 | Fechas numéricas no ambiguas y fecha humana completa en encabezados; cruce nocturno claro también en Widget. |
| V011 | Vigilia oscuro inicial, elección previa respetada y modos Claro/Sistema coherentes en todas las familias. |
| V012 | Letra nativa Android estándar/grande/muy grande mediante contexto QA; no cambiar ajustes globales ni reintroducir zoom interno. |
| V013 | TalkBack: nombre, rol, estado, orden de foco y anuncios comprensibles; escucha humana no se sustituye por semántica presente. |
| V014 | Imágenes, miniaturas y visor con orientación correcta, carga/error y privacidad; el pinch del visor no es zoom global. |
| V015 | RemoteViews compacto/ampliado con privacidad y texto grande sin recortes; host de prueba rotulado y separado de OEM. |
| V016 | Capturas limitadas a ventana/nodo QA, sin overlays, notificaciones, launcher o selector con contenido personal. |

## ANEXO D — Fuentes exactas y exclusiones verificadas

| Clave | Fuente del repositorio activo |
|---|---|
| APP | `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt` |
| CAL | `app/src/main/java/com/blackatsystems/miguardia/ui/CalendarScreen.kt` |
| CVM | `app/src/main/java/com/blackatsystems/miguardia/ui/calendar/CalendarViewModel.kt` |
| DAY | `app/src/main/java/com/blackatsystems/miguardia/ui/CalendarDayDetail.kt` |
| NEXT | `app/src/main/java/com/blackatsystems/miguardia/ui/nextevent/NextEventCard.kt` |
| FORM | `app/src/main/java/com/blackatsystems/miguardia/ui/management/UnifiedManualLoadForm.kt` |
| LOAD | `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ManualShiftLoadScreens.kt` |
| LVM | `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ManualShiftLoadViewModel.kt` |
| EDIT | `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftEditScreens.kt` |
| ACT | `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftActualScreens.kt` |
| EXC | `app/src/main/java/com/blackatsystems/miguardia/ui/exceptions/ExceptionsScreens.kt` |
| AV | `app/src/main/java/com/blackatsystems/miguardia/ui/availability/AvailabilityScreens.kt` |
| WEA | `app/src/main/java/com/blackatsystems/miguardia/ui/weather/WeatherScreens.kt` |
| NOT | `app/src/main/java/com/blackatsystems/miguardia/ui/notifications/NotificationScreens.kt` |
| REP | `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2RecurringPlanScreens.kt` |
| NOTE | `app/src/main/java/com/blackatsystems/miguardia/ui/management/ManualLoadNotes.kt` |
| BATCH | `core/database/src/main/java/com/blackatsystems/miguardia/core/database/repository/RoomCalendarBatchRepository.kt` |
| SETUP | `app/src/main/java/com/blackatsystems/miguardia/ui/worksetup/WorkSetupScreens.kt` |
| LOCATION | `app/src/main/java/com/blackatsystems/miguardia/ui/components/ObjectiveLocationCapture.kt` |
| VAC | `app/src/main/java/com/blackatsystems/miguardia/ui/vacation/VacationScreens.kt` |
| HOURS | `app/src/main/java/com/blackatsystems/miguardia/ui/hours/HoursAndExtrasScreens.kt` |
| SUMMARY | `app/src/main/java/com/blackatsystems/miguardia/ui/summary/SummaryScreen.kt` |
| REPORTS | `app/src/main/java/com/blackatsystems/miguardia/reports/ReportsScreen.kt` |
| BACKUP | `app/src/main/java/com/blackatsystems/miguardia/backup/BackupScreen.kt` |
| PHOTOS | `app/src/main/java/com/blackatsystems/miguardia/ui/photos/PhotosScreens.kt` |
| LOCK | `app/src/main/java/com/blackatsystems/miguardia/security/AccessLockScreens.kt` |
| APPEARANCE | `app/src/main/java/com/blackatsystems/miguardia/ui/AppearanceScreen.kt` |
| HELP | `app/src/main/java/com/blackatsystems/miguardia/ui/help/HelpScreens.kt` |
| UPDATES | `app/src/main/java/com/blackatsystems/miguardia/updates/UpdatesScreen.kt` |
| SUPPORT | `app/src/main/java/com/blackatsystems/miguardia/support/SupportLink.kt` |
| WCONFIG | `app/src/main/java/com/blackatsystems/miguardia/widget/WidgetConfigurationActivity.kt` |
| WUI | `app/src/main/java/com/blackatsystems/miguardia/ui/widget/WidgetScreens.kt` |
| WMODEL | `app/src/main/java/com/blackatsystems/miguardia/widget/WidgetRenderModel.kt` |
| WRENDER | `app/src/main/java/com/blackatsystems/miguardia/widget/WidgetRemoteViewsRenderer.kt` |
| WRUNTIME | `app/src/main/java/com/blackatsystems/miguardia/widget/WidgetRuntime.kt` |
| WPREF | `app/src/main/java/com/blackatsystems/miguardia/widget/WidgetPreferencesStore.kt` |
| WPROVIDER | `app/src/main/java/com/blackatsystems/miguardia/widget/NextEventAppWidgetProvider.kt` |
| WDOMAIN | `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/widget/WidgetProjection.kt` |


Las fuentes de pruebas Android parten de app/src/androidTest/ y las JVM de
app/src/test/, core/domain/src/test/ o core/database/src/test/. Los Instrumented
de SQLite pueden pertenecer a core/database/src/androidTest/. Buscar la clase
exacta; no inventar una ruta o prueba por su nombre.

### Capacidades que NO se presentan como existentes

- No hay zoom interno vigente; sí ampliación/desplazamiento del visor de imágenes.
- No hay semana forzada, plegado visible del mes ni barra normal permanente.
- No hay selector directo mes/año localizado: hay flechas, gesto horizontal e Ir a hoy.
- La tarjeta superior no ofrece abrir el detalle del evento como acción general:
  su interacción propia es expansión de jornadas de hoy cuando aporta contenido.
- Activar Clima no crea coordenadas ni concede ubicación.
- Marcar Sin definir no tiene semántica de Limpiar.
- La jornada nocturna pertenece a su fecha local de inicio; no se clona al día siguiente.
- No hay feriados automáticos, cuenta/nube laboral, nómina/sueldos, OCR/importación
  Excel, mapa embebido, agenda clínica ni acceso beta.
- No se localizó entrada UI vigente de feedback, editor de nombre/apodo, cambio
  de sector ya configurado ni gestión completa de editar/archivar/borrar
  instituciones/horarios. No inventarlas porque exista un método en un repositorio.
- Informes puede incluir nombre/apodo restaurado si existe; no acredita un editor.
- El widget no tiene botón propio de pin, exportar, Maps ni edición de jornadas.
  Bloqueo de entrada no oculta automáticamente un widget Completo: preservar
  privacidad elegida por instancia y explicitar esa frontera.
- Una Copia no recrea la disposición ni widgets del launcher.

### Pendientes explícitos que no se ocultan

- C071/C072: no se localizó una regresión UI exclusiva suficiente de editar y
  eliminar carpeta médica; el ensayo debe añadirla o ejecutar una prueba trazable.
- C032: falta demostrar la cadena externa completa de Maps/ausencia de receptor.
- El arnés Compose puede pasar mientras falla animación nativa de la hoja:
  conservar la regresión sin arnés Compose y esperar sobre reloj Android real.
- Capturas de esta preparación son sólo una revisión inicial limitada, no el atlas
  completo de Stage12, ni contraste medido, TalkBack humano o aceptación.
- W015/W023: los tamaños/entrega/supervivencia del launcher OEM no se acreditan
  por XML, renderer ni AppWidgetHost de prueba. Registrar ese nivel por separado.
- La causa histórica de Resumen/R4, upgrade público y distribución siguen abiertos
  como gates separados; este contrato no los da por resueltos.

## Respuesta de entrega exigida al especialista

Primero: “terminado integral” o “parcial/bloqueado”, sin calificativos triunfalistas.
Después: conteos por ID/subcaso/ronda y por candidata, lista de bugs reales y
correcciones, recibos/rutas/hashes, lo reutilizado y lo no ejecutado, conservación
de entrada, recursos liberados y gate exacto.
Cerrar con próximo dueño y acción concreta de MAIN/Joaquín.
No terminar con un mero “todo verde” ni con pruebas de otro APK sumadas como final.

## Adenda vigente — continuidad nocturna en AVD dedicado, 23/10/2026

### Autoridad y alcance exacto

Joaquín indicó directamente en MAIN: «No vas a tener disponible Samsung durante
toda la noche. Yo vuelvo tarde de trabajar, así que vas a fijarte de ajustar
emuladores y cosas por el estilo para reemplazarlo».

Esta instrucción habilita preparar y usar un emulador dedicado para continuar
el MISMO Stage12: Android sintético, instalación de QA/ejecutor, pruebas del
contrato, correcciones mínimas ya autorizadas y auditoría MAIN. No crea otro stage,
chat u operador, ni levanta el HOLD del Samsung. Prevalece sobre las prohibiciones
anteriores de emulador de este contrato y de los mensajes LOCAL L1/L2, únicamente
para el recurso nuevo delimitado aquí. No amplía producto, seguridad ni distribución.

Stage12 sigue como único operador de Gradle, emulador y su ADB; MAIN sólo revisa.
El especialista liberó documentos sin Gradle activo ni sello en curso en
`.local-evidence/stage12/20261023-local2/documentation-release.json` antes de esta
edición. Registrar el delta documental de MAIN como entrada, no como implementación
propia. Conservar L1 y todos los adversos/originales; no rehacer validaciones válidas
sólo por cambiar autoridad documental.

### Recurso nuevo y preparación sin tocar otros ensayos

- SDK existente: `C:/Users/User/AppData/Local/Android/Sdk`.
- Imagen YA instalada: `system-images/android-36/default/x86_64`, API36/AOSP,
  revisión2. No descargar imagen nueva, instalar herramientas, aceptar licencias
  ni cambiar hipervisor/seguridad/configuración global de Windows por inferencia.
- Nombre exclusivo: `MiGuardia_Stage12_API36_20261023`.
- Raíz exclusiva: `C:/Users/User/.cache/miguardia-stage12-avd/20261023/`.
  Crear índices/configuración/userdata nuevos con variables sólo del proceso hijo;
  no usar el directorio AVD global para sustituir una entrada existente.
- Puerto propuesto5588, serial esperado `emulator-5588`; alternativa5590 únicamente
  si se registra el cambio ANTES del arranque y no hay colisión. Servidor ADB
  dedicado propuesto en loopback5048. Revalidar disponibilidad local inmediatamente
  antes; si la raíz/nombre ya existe sin pertenencia probada a Stage12, detener
  ese paso sin sobrescribir, forzar, borrar ni reutilizar datos desconocidos.
- Un AVD, dos núcleos,2048MiB RAM, userdata6GiB y pantalla720×1600/densidad320
  como configuración inicial propia. Registrar los valores reales y metadatos
  de imagen/herramientas; no confundir sus hashes de metadatos con imágenes completas.
- No crear cuentas ni importar información del teléfono. Toda entidad, imagen,
  archivo, ubicación de prueba y credencial ficticia debe ser propia e inventariada.
  No copiar las1956 jornadas, preferencias, Copias o archivos del Samsung.
- No acceder, iniciar, restaurar ni modificar AVD10/11, sus índices, checkpoints o
  respaldos. No repetir sus campañas ni instalar la app pública.

### Guardas antes de crear, arrancar e instrumentar

1. Preflight host con rutas canónicas, ausencia/pertenencia de destino, imagen
   instalada, espacio/RAM, puertos, identidad de proceso y aceleración local.
   Usar herramientas existentes; si falta algo material, informar y seguir lo local
   independiente. No cambios globales, limpieza de otros proyectos o descargas.
2. Servidor ADB exclusivo y restringido al emulador propio ANTES de levantarlo.
   Verificar soporte y funcionamiento de restricción a un dispositivo mediante
   ayuda local y dobles, por ejemplo `--one-device` si esa versión lo admite.
   No arrancar un servidor que explore USB/Samsung. No consultar el servidor ADB
   común, usar `adb devices` general ni detener servidores ajenos. Si no se puede
   garantizar restricción y ausencia de fallback, detener Android, no consultar
   el Samsung para comprobarlo. Clientes con servidor/serial explícitos y sin
   autoarranque irrestricto cuando el servidor propio falle.
3. Entregar a MAIN preflight/protocolo estable antes del primer boot. MAIN puede
   admitir técnicamente la creación/arranque rutinarios dentro de esta autorización,
   sin volver a pedir permiso humano. No otro implementador ni ADB de MAIN paralelo.
4. Arranque oculto, sin cargar snapshots ajenos, con PID, raíz, puertos, servidor y
   serial fijados. Verificar identidad AOSP/API/ABI/propiedades del AVD exacto,
   foco previsto y ausencia de procesos/objetivos extra antes de operar QA.
5. Antes de instalar/instrumentar, MAIN revisa delta/guardas, candidata/ejecutor y
   protocolo. Validar localmente paqueteQA, target, versión, certificado técnico,
   hashes y fuente ANTES del instalador; verificar instalados DESPUÉS. Primera
   instalación sólo si paquete ausente comprobado; actualización posterior sólo
   sobre la instancia Stage12 propia y conservando canarios. Nunca forzar coincidencia
   restaurando un output histórico ni relajar los pins de un runner10/11.
6. Fixture nueva propia en el AVD no convierte en seguras las clases destructivas
   heredadas: conservar las guardas B03/B04, revisar Application/Activity/servicios
   y ejecutar canarios/adversos antes de negocio. No `pm clear`, desinstalar,
   `wipe-data`, resembrar para ocultar fallos ni limpiar recursos recibidos.
   Cada ronda final usa raíces equivalentes nuevas de prueba dentro del mismo AVD,
   la misma candidata final y sus recursos propios, con readback independiente.
7. Captura sólo Window/nodoQA y XML propio también en emulador. Conservar FLAG_SECURE,
   no capturar aplicaciones externas ni usar pantalla completa como atajo. Sin
   cambios globales de letra, reloj/zona, permisos de otras apps, TalkBack, DND o
   seguridad; variantes de prueba mediante contextos QA. Selección documental o
   navegador controlado se rotulan AOSP/proveedor de prueba, nunca Samsung/OEM humano.
8. Al relevo: detener sólo el emulador/servidor que pertenezcan probadamente a esta
   sesión, sincronizar y preservar datos/configuración/logs/manifiesto, incluidas
   fallas. No borrar el AVD para fabricar conservación ni matar procesos por nombre.

### Alcance de la evidencia y pendientes que no desaparecen

Por aserción registrar entorno `ANDROID_AVD_API36_AOSP`, serial, imagen, candidata,
runner, fixture, tema/letra/ventana, reloj y recursos reales/dobles. Separar prueba
JVM, UI pura, Activity/Room/archivos Android, RemoteViews/host y simulación de servicio.
Corregir los hallazgos L1 en plan nuevo, con niveles obligatorios sin AND_OR.

Dos rondas consecutivas sobre el mismo APK pueden acreditar los subcasos que el
AVD realmente ejecuta; no sustituyen las exigencias humanas/Samsung/OEM. No marcar
PASA una fila padre si un subcaso obligatorio sigue BLOQUEADO, ni usar NO_APLICA
para retirar una capacidad existente. Clasificar los pendientes por recurso y
seguir las comprobaciones independientes permitidas sin pedirle el teléfono a
Joaquín durante la noche. MAIN recibirá/auditará checkpoint y entrega en este chat.

Quedan separados: biometría y autenticación humana real, escucha de TalkBack,
estética/comprensión, selector/launcher/permisos OEM Samsung, consumo real de Clima
no autorizado, conservación de la sesión Samsung, actualización pública y firma/
distribución. El rendimiento de un emulador no resuelve la causa histórica R4 ni
se compara como equivalente con tiempos Samsung. Si sólo quedan esas puertas,
entregar cierre parcial con evidencia y límites, sin repetir ni inventar trabajo.

### Puerta de reanudación tras el gate preventivo G3

El estado y los insumos actuales se recuperan de STATUS y la
[auditoría de cierre local](../audits/2026-10-23-stage12-revision-main-cierre-local.md),
no de un APK histórico ni de esta sección como permiso de ejecución.

1. Un gate de evidencia preventiva no es un escape observado, un bug productivo
   demostrado ni prueba de imposibilidad universal. No rehacer investigación o
   ayudas indefinidamente para ocultar esa distinción.
2. MAIN debe resolver el límite concreto de aislamiento/plataforma o recibir una
   confirmación humana vigente de alcance y disponibilidad del dispositivo. No
   inferir regreso del Samsung por USB ni ampliar autorización de seguridad global,
   firma, datos o servicios por una disponibilidad física.
3. La reanudación utiliza el MISMO chat Stage12 y un nuevo despacho acotado MAIN.
   Revalidar fuentes/artefactos/hashes actuales y permisos pertinentes; conservar
   sellos/adversos y no repetir G2 ni consumir otra vez sus admisiones agotadas.
4. Un APK/ejecutor normal compilado, con providers activos, no habilita el arnés
   aislado ni sus escrituras. Antes de negocio/captura, MAIN revisa bootstrap,
   aislamiento y canarios/escape/overlay/recursos propios del recorrido concreto.
   No instalar ni ejecutar la batería heredada destructiva sobre datos recibidos.
5. Mantener todos los requisitos de subcasos obligatorios y dos rondas finales
   sobre la misma candidata. Lo local auditado no cierra Android, Samsung/OEM,
   escucha/estética humanas, R4 histórico o distribución. Sin nuevo trabajo seguro
   habilitado, registrar la puerta y retirar el supervisor temporal, no crear
   etapas o tareas ficticias para mantenerlo activo.

## Adenda vigente 24/10/2026 — cierre en emulador y Samsung diferido

### Orden humana, alcance y dictamen de cierre

Joaquín indicó directamente en MAIN: «samsung no está dispoible por el momento,
vas a tener que trabajar con emuladores. finalizá stage 12, más adelante
comprobaremos samsung».

Se reanuda el MISMO Stage12, no otro chat/stage. Se autoriza preparar y usar el
AVD propio, instalar QA/ejecutor propios, ejecutar la matriz del contrato,
corregir causas dentro de su alcance y entregar evidencia para auditoría MAIN.
La indisponibilidad Samsung deja de ser una dependencia del cierre del alcance
emulador; se conserva como validación física posterior expresamente diferida.
NO ADB/consultas/instalación/operación del Samsung, incluso si aparece conectado.

DONE WHEN del alcance actual: todos los441 IDs y sus subcasos/aserciones están
conciliados; los obligatorios ejecutables en AVD superan dos rondas finales
sobre la misma candidata/ejecutor e insumos atribuibles, o cuentan con un gate
material demostrado que MAIN no pueda resolver con esta autorización. No basta
terminar una herramienta o una batería compilada. Para cada dependencia
exclusivamente Samsung/OEM/humana, mantener estado BLOQUEADO y razón
`SAMSUNG_HUMANO_DIFERIDO_POR_JOAQUIN_20261024`, fuente y procedimiento posterior.
No convertir esa postergación en PASA o NO_APLICA; un padre con obligatorio
Samsung pendiente no se presenta como aprobado integralmente. La entrega puede
cerrarse como ALCANCE_EMULADOR_COMPROBADO con esa lista física explícita, nunca
como aceptación Samsung, estética/voz humana, publicación o cierre de R4.
Los demás bloqueos no se atribuyen a Samsung para evitar trabajo de AVD.

### G3-R2: configuración privada verificable, no sandbox universal

La falta de prueba G3 del23/10 permanece como evidencia válida bajo su requisito
anterior. MAIN sustituye expresamente la exigencia adicional de demostrar una
prevención universal de todo comportamiento posible del SDK por un ensayo
acotado del SDK instalado confiable, con rutas, procesos y operaciones propios.
Esto no permite acceso al teléfono, ADB común, datos reales o cambios de
seguridad global, ni convierte observación posterior en aislamiento de red.

La vía revisada es un SDK sombra PRIVADO, separado del AVD y de los originales:

- Raíz nueva exclusiva propuesta:
  `C:/Users/User/.cache/miguardia-stage12-runtime/20261024/`.
  Probar ausencia/pertenencia y destinos canónicos antes de escribir. Nunca
  sobrescribir una raíz desconocida, limpiar globalmente ni modificar el SDK
  instalado. Mantener el AVD Stage12 y sus datos/checkpoints; no otro AVD.
- Copiar el paquete `emulator/` instalado completo a `sdk/emulator/` propio,
  por inventario exacto de archivos/bytes/SHA, sin enlaces ni cambios de binarios.
  Es reutilización local de herramientas existentes, no descarga, actualización
  o instalación/licencia nueva. Registrar original/copia y resolución de archivos.
- En `sdk/platform-tools/adb.exe` colocar SOLAMENTE una copia del stub inerte
  existente SHA256
  `998bdab32c547fc8b1e221eb5e9979b538068f16e14bce11f55e34c9c5330ad1`.
  No copiar allí ADB real. Verificar el stub antes de ejecutar; no relay,
  fallback ni registro de argumentos arbitrarios. Su salida113 es intencional.
- Entorno sólo del launcher/motor hijo: ANDROID_HOME y ANDROID_SDK_ROOT apuntan
  al SDK privado válido; CWD propio y PATH OS revisado sin ADB real. Usar también
  `-adb-path` al stub y `-sysdir` absoluto a la imagen AOSP36 original en lectura.
  Configuración/keys/temp/userdata sólo en raíces propias. No alterar variables
  globales o preferencias del usuario. La inferencia por entorno, ubicación del
  launcher y PATH debe resolver al stub o nada, no al SDK/ADB original.
- El servidor privado es una función DISTINTA: ADB instalado original y pinneado,
  explícito en su callsite, puerto5048, USB/EMU/mDNS0 y entorno propio ya revisado.
  No se pone ese ADB real en el SDK/PATH del emulador. Consumir una admisión G2
  anterior no autoriza reiniciarlo; el nuevo G3 integra su arranque y cierre.
- ConfigDirs/AdbInterface/AdbHostServer del snapshot oficial son evidencia causal
  útil, no correspondencia demostrada con el build instalado. La copia, env y
  estructura deben verificarse y el comportamiento real observarse en el ensayo.
  No se exige esa correspondencia como prueba universal previa, ni se afirma
  que las variables o el stub sean una barrera del sistema operativo.
- Usar job/handles propios para pertenencia y cierre de launcher/motor/hijos,
  sin breakaway. Asignar antes de reanudar el proceso (creación suspendida o
  mecanismo nativo equivalente). Si falla la asignación, cerrar el proceso propio
  aún suspendido. El job no filtra TCP/USB ni es sandbox contra código arbitrario.

No AppContainer/framework/firewall/WFP, habilitación de features/hipervisor,
perfiles, cambios de seguridad de Windows/Android o nuevas dependencias por
inferencia. Un cambio material sigue requiriendo decisión expresa.

### Secuencia técnica y autonomía dentro de la unidad

1. Recuperar entrada actual y evidencias; la QA archivada58a354ca… y sus618
   insumos son la base, no un output que deba restaurarse. Hay44 JVM dirigidos
   B02 pertinentes; los995 anteriores mantienen su atribución histórica.
2. Preflight fresco de espacio/RAM, puertos5048/5588/5589, pertenencia raíz,
   archivos/copias/imagen y rutas. No consultar dispositivos ni el servidor común.
   Preparar la copia privada y adversos puros de SDK inválido, stub/PATH/binario
   alterados, owner/puerto/identidad/endpoint ajenos y timeout/cierre.
3. Se admiten canarios host de versión, aceleración y ayudas concretas pertinentes
   desde la copia privada, con argv exacto, límites, entorno y hashes registrados,
   sin argumento AVD ni VM/servidor implícito. Priorizar ayudas ya conservadas;
   no usar una ayuda para iniciar VM encubierta ni prolongar investigación.
   Refrescar aceleración real: WMI actual y WHPX histórico no son equivalentes.
   No habilitar features globales. Si se necesita software acceleration, verificar
   soporte y declarar antes el perfil porVM con límites; no fallback silencioso.
4. Entregar un checkpoint estable y cohesivo G3-R2 de protocolo/operador/canarios
   para revisión MAIN antes del PRIMER boot. MAIN puede admitir ese ensayo
   técnicamente dentro de esta orden, sin nueva pregunta humana rutinaria.
   Fijar todos los procesos/helpers/endpoints propios previstos, límites de tiempo
   y cierre. Sin normalización genérica de rutas ni procesos identificados por nombre.
5. Después de admisión: primer arranque oculto y acotado, servidor privado,
   pertenencia de5588/5589 del motor ANTES del registro, inventario exactamente
   del emulador propio, selección explícita y propiedades API/ABI/nombre/imagen.
   Servicios auxiliares legítimos deben estar declarados y pertenecer a la sesión;
   no aceptar endpoints ajenos por conveniencia. Si el servidor falla, detener
   sólo recursos propios sin arrancar otro, consultar5037 o cambiar de dispositivo.
6. MAIN revisa guardas/delta y PRE-install de candidata/ejecutor; después Stage12
   instala sólo QA/runner en la instancia propia, comprueba hashes instalados y
   ejecuta canarios/escape/overlay antes de negocio. Un APK normal compilado no
   habilita por sí solo el arnés ni las clases heredadas. Integrar bootstrap
   normal/aislamiento y validar el alcance concreto sin suprimir capacidades.
7. Completar las familias funcionales/combinadas/visuales posibles en AVD,
   corregir causas y sus equivalentes, regresiones rojas/verdes, conservación y
   dos rondas finales. Revisión MAIN por riesgos/hitos reales, no por cada botón
   o archivo. Un escritor/operador, fuentes y adversos preservados.
8. Las lecturas de los proveedores públicos YA aprobados de la app que sean
   necesarias para comprobar Clima/actualización se separan de dobles, con datos
   ficticios no personales, sin cuentas/costos/endpoints nuevos ni escritura de
   servicios remotos. No ubicación/direcciones personales ni secretos en recibos.
   Un caso sin permiso/capacidad real se marca con su límite, no pasa por mock.
9. Entregar matriz completa con estado/nivel/entorno/artefacto/evidencia por
   aserción, bugs/correcciones, omitidos y lista Samsung diferida. MAIN audita
   independientemente, devuelve defectos al MISMO chat y decide el cierre del
   alcance emulador. Firma/distribución/upgrade público conservan autorización aparte.

Se resuelven autónomamente decisiones técnicas rutinarias y reversibles de este
alcance. No pedir de nuevo el Samsung ni permisos ya concedidos. Escalar sólo
un límite material concreto o un control humano obligatorio; no simular su aceptación.

## Adenda prevalente 24/10/2026 — reanudación Samsung autorizada

Joaquín ordenó directamente en MAIN:
«Dale, seguí Stage12 en el Samsung usando esta PC. Primero verificá el aislamiento
y la conservación de mis datos; después ejecutá las pruebas y las dos rondas
finales. Seguí con el mismo chat y supervisalo.»

Reemplaza el HOLD Samsung y el orden emulador-primero anteriores. Continúa el
MISMO chat01a169e5-d914-7e33-8926-327a354301b3, esta PC como controlador y el
Samsung como dispositivo. Autoriza identificación ADB, lectura de conservación
QA y pruebas conforme a las guardas. No autoriza nuevos ensayos de emulador,
red desconocida, firma/distribución pública, Git, cambios globales o uso de datos
personales. Admisiones/cleanup de emulador consumidos y fallos preservados.

### Unidad completa, conservación y revisión MAIN

1. Puerta0 actual; Stage12 único operador Gradle/ADB/Samsung. MAIN sólo recibe
   entregas estables. ADB instalado con ruta/hash, servidor y serial explícitos.
   SM-S938B/API36/[serial QA en binding local privado] es referencia histórica a revalidar, no identidad
   actual acreditada. No primer dispositivo implícito, AVD, matar/adoptar
   servidores ajenos ni reutilizar el entorno USB0/emulator anterior.
2. Primero fuente/pruebas locales: protocolo de conservación antes de abrir QA,
   instrumentar o instalar. Preparar juntos candidata/arnés/bootstrap normal,
   muerte/readmisión, colas/servicios/hosts y limpieza propia. Canario técnico,
   quiet y build no prueban familias ni arranque normal; runner restringido a
   canarios no habilita441 IDs. No usar runner default como escape.
3. Se permiten consultas mínimas de identidad/transporte y metadatos QA/runner,
   y snapshot QA coherente de sólo lectura si el camino se justificó estáticamente.
   No iniciar Application normal para medir baseline. Contenido privado en raíz
   ignorada y protegida; recibos sólo conteos/hashes/esquemas/síntesis sin datos
   personales. Pública sólo metadatos de paquete/código/certificado; no abrir,
   instrumentar o leer sus archivos privados.
4. Las1956 jornadas QA eran antecedente: baseline fresco sin imponer ese conteo
   si Joaquín cambió la sesión. Preservar Room coherente, prefs/DataStore, fotos/
   archivos, instalaciones/certificados y estado recibido de avisos/widgets según
   superficie. No limpiar/resembrar/restaurar ni forzar cierre de sesión normal
   para fabricar baseline. Si medir requiere mutación, entregar límite/alternativa
   antes de tocarlo; no asegurar igualdad pública sin evidencia.
5. B03/B04: Context UUID, Room/DataStore/archivos/Copias, colas/alarmas/avisos/
   canales, AppWidgetHost/IDs/RemoteViews/trabajos/servicios propios. Probar ANTES
   del primer efecto Application/providers y tras muerte/readmisión; callbacks
   secundarios de Copias/Widget también aislados. Sin fallback normal/IDs recibidos
   ni limpieza global. Canarios positivos/adversos de escape/overlay/conservación/
   limpieza propia; quiet no sustituye reconciliación.
6. UNA entrega completa PRE-install/aislamiento estable: protocolo nativo,
   variante documental Samsung, pruebas afectadas, paquetes/target/runner/cert/
   SHA de artefactos, baseline y cese. MAIN revisa fuente/guardas/ciclo completo y
   admite técnicamente bloque exacto ANTES de instalar/instrumentar/escribir
   negocio. Permiso humano YA concedido; no repregunta rutinaria.
7. Tras aceptación MAIN: instalar sólo QA/runner compatibles conservando sesión,
   verificar hashes instalados, canarios de aislamiento/escape/captura/conservación
   y luego familias completas. Negocio sólo en raíces/IDs sintéticos propios.
   Cambios APK/runner requieren nueva PRE-install, no reatribuir evidencia.
8. PlanV5 aceptado permanece inmutable. Variante Samsung conserva441IDs/
   506subcasos/1627aserciones obligatorias.45OEM antes diferidas y44 padres mixtos
   vuelven a elegibles, no PASA automático. No reemplazar por AVD/dobles ni omitir.
   Intervenciones humanas reales voz/biometría/selectores/estética sólo cuando el
   paso esté listo; no aceptar diálogos por Joaquín ni inventar prueba.
9. Corregir causas existentes con adversos/regresiones pertinentes; congelar
   candidata/runner/fixtures/criterios finales. DOS rondas completas independientes
   de la MISMA candidata, raíces equivalentes propias distintas y conservación
   antes/después. Cambiar candidata invalida ambos cierres anteriores; no ocultar
   obligatorios bloqueados/omitidos ni sumar APK diferentes.
10. Captura sólo Window/nodo/árbol QA validado; no fullscreen/crop/retirar
    FLAG_SECURE/overlays/personales. Salida externa sólo desde acción QA prevista,
    Intent/proveedor autorizado y control humano cuando corresponda; foco ajeno
    inesperado detiene bloque. No cambios globales reloj/zona/letra/densidad/
    seguridad/TalkBack/DND ni notificaciones ajenas.
11. Clima/actualización reales sólo lecturas públicas ya aprobadas con datos/
    ubicación ficticios, sin cuentas/costos/dominios nuevos/instalación pública.
    Dobles son evidencia distinta. No causaR4 o distribución/estética aceptadas
    por inferencia. Entrega y cese reales para auditoría independiente MAIN.
12. MAIN conserva STATUS/índice/contrato/revisión; Stage12 fuentes/pruebas/
    catálogo/evidencia. No repetir L1/L2/B02/Stages8–11 por ritual o adjudicarlos
    a nuevos inputs. Sin dependencias/variantes/applicationId/Roomschema/cifrado
    Copias/versiones nuevas por inferencia. Límite material concreto sólo detiene
    dependientes; sin cadena indefinida de herramientas o ensayos.

Contrato54eec4 anterior preservado byte-exacto en
.local-evidence/stage12/main/stage12-contract-before-samsung-20261024.md.
Sus pins son históricos; nuevo contrato y admisiones prospectivos. No editar
manifests/claims/sellos viejos o elevarlos a evidencia Samsung.

## Aclaración humana prevalente 24/10/2026 — abrir y usar QA instalada

Joaquín escribió directamente en el MISMO chat Stage12:
«pero por qué no abrís la app? usala como gustes, no tiene información relevante
ni importante». MAIN verificó el mensaje humano original, no sólo el relato
del especialista (msg_01a170bd-0edf-7882-be51-73a17f6d521a,
2026-10-25T01:46:40.735Z = 24/10 por la noche en Buenos Aires).

Esta corrección autoriza ABRIR Y USAR NORMALMENTE LA QA INSTALADA en Samsung,
sus pantallas, flujos y efectos funcionales esperados. Reemplaza la prohibición
de abrir/iniciar la Application normal antes del baseline y la exigencia de
inmutabilidad/snapshot atómico de toda la sesión QA como condición para ese uso.
No esperar al arnés integral o a un inventario universal de alarmas/Widget/
avisos para comenzar la inspección y pruebas proporcionadas de la app instalada.
La sesión QA declarada irrelevante puede usarse en los flujos de prueba; medir
una entrada ligera y los cambios esperados sirve para atribuir, no para crear
otro bloqueo. Los1956 registros siguen siendo sólo referencia histórica.

Los casos y borrados previstos de fixtures propias siguen permitidos; no hay
orden de vaciar/desinstalar/resembrar masivamente por conveniencia. Documentar
los efectos reales y diferencias, sin declarar una conservación absoluta que
la nueva instrucción ya no exige. Mantener identidad Samsung/serial/paquete/
versión/artefacto atribuibles; ninguna observación manual equivale a las441 filas
o a instrumentación. Captura segura sólo Window/nodo/árbol QA.

Una candidata/runner NUEVOS siguen requiriendo revisión PRE-install proporcional
de identidad, target, certificado, SHA e insumos/cambios pertinentes antes de
reemplazar los instalados, y comprobar después los hashes instalados. Este gate
no impide usar hoy la QA existente. No suplantar runner, inventar resultados ni
convertir quiet en pruebas de reconciliación, servicios o familias nativas.
Conservar la matriz441/506/1627 y DOS rondas completas independientes de la MISMA
candidata final, con lo humano pendiente explícito.

No se extiende a app pública/archivos privados ajenos, otras apps, secretos,
ajustes globales, firma/distribución/Git/costos o nuevos ensayos de emulador.
ADB y Samsung siguen sólo a cargo del mismo Stage12; MAIN supervisa. No repetir
la petición de disponibilidad ni permiso de uso normal ya concedido.
