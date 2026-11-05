# Stage 12 · Requisitos de verificación integral

Esta matriz conserva las capacidades y oráculos requeridos. El bloque ejecutor y los permisos vigentes están en [STATUS](../STATUS.md). Las instrucciones operativas antiguas de AVD, las primeras parejas de APK y las adendas sustituidas están en el [archivo histórico](../archive/README.md).

El programa V5 conserva 441 IDs, 506 subcasos y 1627 obligaciones. Las aceptaciones se atribuyen a requisitos, variantes, artefactos y niveles. Las correcciones posteriores repiten las comprobaciones afectadas. El cierre integral requiere dos rondas completas de la misma candidata congelada y la observación humana que corresponda.

Los controles conservan datos laborales, preferencias y archivos, con cambios de ensayo propios explícitos. El baseline representa el estado real recibido. La autorización de uso normal QA y la instrumentación se aplican según el contrato actual. MAIN revisa las entregas y Stage es el único operador del dispositivo. El estado actual es PAUSADO_POR_USUARIO.

## Guardas de datos y capturas que se conservan

Inventariar los efectos de método, regla, Application, setUp/tearDown y proveedor. Los contextos, Room, DataStore, archivos, colas y hostId de prueba deben estar realmente aislados. Una ruta propia no aísla si un singleton o applicationContext vuelve a los datos normales. Probar con canarios y un adverso que intente salir de la raíz; detenerse antes de acceder al recurso ajeno. No reparar mediante restauración para ocultar un cambio recibido.

Capturar exclusivamente la ventana o nodo de QA mediante captureToImage o PixelCopy, incluyendo sus diálogos propios. No capturar la pantalla completa para recortarla después. Comprobar pertenencia a QA, estabilidad y artefacto. Mantener FLAG_SECURE; no cambiar DND ni notificaciones globales. Probar una superposición sintética antes de generar evidencia. Una captura de Compose con fake no acredita navegación o animación nativa.

## Criterios de regresión S12-B01–B04

Estas etiquetas conservan requisitos de cierre. La evidencia vigente determina cuáles ya están cubiertos; no se reinicia su diagnóstico por esta poda ni se presupone que el defecto siga presente.

### S12-B01 · Tamaño y controles del diálogo de Clima

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

### S12-B02 · Fechas e intervalos inequívocos

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

### S12-B03 · Aislamiento de preparación y limpieza de ensayos

Rige la sección de guardas de datos: inventario de efectos, contextos propios, canarios y rechazo antes del acceso ajeno. Los recursos recibidos permanecen intactos sin reparaciones que oculten una diferencia.

### S12-B04 · Capturas sin información de otras aplicaciones

Rige la sección de guardas de capturas: sólo ventana/nodo QA, superposición sintética adversa, FLAG_SECURE conservado y ausencia de captura global previa al recorte.

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

Handoff compacto y auditoría fechada bajo las rutas vigentes del Stage, con delta, resultados por ID/ronda, fallos corregidos, pendientes, riesgos, artefactos y recepción. No crear placeholders de aprobado.

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
- Identidad, foco y guardas antes de cada bloque Android autorizado. Rigen la pausa y la autoridad vigentes de STATUS; al levantar la pausa se conserva el alcance QA concedido y se requiere admisión técnica nueva para cada operación que la necesite.
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

El estado de cada fila se recupera del registro de cobertura y de sus recibos. Las clases citadas se reutilizan según sus aserciones, insumos y nivel comprobado.

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
