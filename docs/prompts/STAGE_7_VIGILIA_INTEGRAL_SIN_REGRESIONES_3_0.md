# Stage 7 Vigilia integral sin pérdida de funciones

## TASK

Llevar el diseño Vigilia elegido por Joaquín a toda MiGuardia 3.0 mediante una
implementación Android coherente, legible, intuitiva y verificable. Conservar
todas las capacidades existentes y sus garantías: no entregar sólo un calendario
retocado ni reemplazar la app por las funciones simplificadas de una maqueta.

Este contrato se preparó el 2026-10-11 por pedido de Joaquín, sin implementar.
La elección de Vigilia aprueba la dirección visual, no una candidata Android,
QA integral ni distribución. Ejecutar el contrato sólo cuando Joaquín inicialice
expresamente Stage 7; entonces registrar ese inicio en STATUS. Preparar o leer
este documento no lo inicia ni autoriza teléfono, instalación o servicios externos.

## CONTEXT

MiGuardia es una aplicación Android local-first en Kotlin y Compose con trabajo
acumulado de Stages 1–6. Stage 5 es base local parcial y conserva sus pendientes;
Stage 3/3.2 no tiene aceptación global. No reabrirlos para reconstruir la app:
preservar el candidato, sus regresiones y las puertas registradas en STATUS.

Joaquín eligió Vigilia entre tres propuestas interactivas. Busca superficies
carbón, violeta suave y turquesa contenido; tarjetas cuidadas, jerarquía clara,
fechas y horarios fáciles de leer, botones reconocibles y menús predecibles.
Vigilia debe señalar lo importante, sin llenar todas las superficies de violeta,
neón, bordes o sombras. El objetivo es mejorar la app completa, no agregar temas
alternativos, funciones nuevas, dependencias o una arquitectura nueva.

La maqueta expresa estilo, no reglas de negocio. Sus datos, contador temporal,
menú reducido, acciones simuladas, tamaños fijos y guardado en memoria no son
implementación reutilizable ni contratos funcionales. La tarjeta permanente
debajo del calendario, el control Hoy de la referencia original y sus dos signos
de interrogación no deben copiarse: rigen los recorridos Android vigentes.

## INPUTS

- Contenedor: `C:\Users\User\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio operativo: `MiGuardia-3.0`, rama `codex/miguardia-3.0`.
- HEAD observado al preparar: `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
  El candidato real incluye cambios rastreados, nuevos y una eliminación previa;
  HEAD solo no lo describe. No limpiar, sustituir ni reconstruir ese checkout.
- Referencia visual elegida: [captura de Joaquín](../design/vigilia-referencia-elegida.png).
  Es una referencia estética ficticia, no una captura de la app implementada.
- Registro visual vigente: [handoff Stage 6 nuevo](../HANDOFF_STAGE_6_RELEVAMIENTO_NUEVO_A_MAIN_3_0.md)
  y [diagnóstico](../audits/2026-10-11-stage6-relevamiento-visual-nuevo.md).
  Atlas: `.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/`.
  Sólo ese corpus: el relevamiento anterior está OBSOLETO.
- La muestra nueva tiene 41 capturas y 24 apartados; es representativa, no
  exhaustiva. Bases de comparación: 09 Calendario, 13 detalle, 19 carga con
  catálogo, 22 Resumen, 23 Informes y 35 Ayuda. Completar cobertura desde rutas
  y estados reales, no desde las pantallas que casualmente aparecen en la muestra.

Leer primero AGENTS del contenedor y del repositorio, `docs/STATUS.md`, este
contrato, `docs/LECTURAS_POR_TAREA.md`, `docs/WORKFLOW.md`, `docs/VALIDACION.md`
y ADR 0042. Consultar después los ADR funcionales por superficie y el código
pertinente. No leer todo el archivo histórico ni tratarlo como órdenes nuevas.

Puerta 0 antes de escribir: comprobar ruta, `.git` propio, rama, HEAD, upstream,
remotos, worktrees, staging y cambios rastreados/nuevos/eliminados. Registrar
huellas de entrada y contrastarlas con el último sello pertinente. Detener la
escritura ante una diferencia sin procedencia explicable. `MiGuardia-2.0`,
`Repositorio-Git` y `Archivo-recuperacion` son sólo lectura y no son la base
para copiar archivos. Un HEAD posterior conocido se compara, no se revierte.

## OUTPUT

1. Sistema visual Vigilia único, integrado en las superficies propias de la app,
   con claro, oscuro y modo Sistema conservados; componentes comunes suficientes,
   sin un segundo framework ni estilos locales divergentes por pantalla.
   Ampliación autorizada el 2026-10-11: Oscuro es el valor inicial si no existe
   una elección guardada. Claro y Sistema siguen disponibles en Apariencia;
   no sobrescribir preferencias explícitas ni alterar Copias o datos laborales.
2. Matriz trazable de conservación: capacidad y entrada anterior → nueva entrada,
   estados y acciones → prueba/evidencia o pendiente. Ninguna función desaparece
   para simplificar una captura. Incluir destinos secundarios y estados poblados.
3. Comparación antes/después sobre las seis bases y el resto de las familias,
   con capturas reales identificadas por APK/configuración si se autoriza QA.
   No usar imágenes generadas para acreditar el producto instalado.
4. Regresiones funcionales y visuales, revisión independiente, auditoría fechada
   con delta propio y handoff autosuficiente a MAIN. Actualizar STATUS sin
   convertir validación local en aceptación humana ni cerrar pendientes ajenos.

## SCOPE

### Sistema visual y componentes

Reutilizar `ui/theme/Theme.kt`, `VigiliaTokens.kt`, `AppThemeMode.kt`
y los componentes Compose existentes; `AppZoom.kt` queda sólo como legado de
Copias (ADR 0043), no como control visual. El nombre Vigilia ya existe en el código,
pero sus tokens actuales no garantizan la estética ahora elegida. Ajustarlos
selectivamente; no crear un tema paralelo ni migrar preferencias.

Paleta de partida del diseño elegido, no sustituto de medir contraste:

| Uso | Oscuro | Claro |
|---|---|---|
| Fondo | `#15151E` | `#F5F4F8` |
| Superficie | `#23232F` | `#FFFFFF` |
| Celda neutra | `#2C2938` | `#EFEDF4` |
| Texto principal | `#F2EFF8` | `#292738` |
| Texto secundario | `#B5AFC3` | `#706B80` |
| Borde sutil | `#3B3749` | `#E5E1ED` |
| Acento violeta | `#B8A2F8` | `#6951B4` |
| Selección suave | `#44375E` | `#EBE5F7` |
| Fecha protagonista | `#BFDEE0` | `#D8ECEB` |
| Texto sobre fecha | `#23434B` | `#284B53` |
| Completada y texto | `#263F3B` / `#B0D9CB` | `#DEEEE7` / `#2A6957` |
| Planificada y texto | `#2D3A50` / `#C2D3EC` | `#E4EDF5` / `#3E5B7C` |

Usar un gradiente discreto sólo donde aporte jerarquía, especialmente próxima
jornada. Conservar los colores de instituciones elegidos por la persona: los
tokens de tema no recolorean sus datos ni cambian el significado de estados.
Ajustes menores por contraste o Android son autónomos si conservan identidad
y se justifican; cambiar la dirección visual requiere decisión de Joaquín.

Tipografía sans-serif local, jerarquía de pesos moderada y cifras alineadas;
evitar apariencia monoespaciada general y títulos pesados en todas las superficies.
Definir espaciado, radios, elevación, iconografía y botones consistentes. Preferir
los recursos existentes de Android/Compose, sin fuentes remotas ni nuevas librerías.

Definir estados normal, pulsado, seleccionado, foco, deshabilitado, carga, vacío,
error y destructivo. Las acciones activas no deben parecer deshabilitadas. Usar
texto e iconos además de color; no esconder opciones detrás de iconos ambiguos.
Priorizar la acción principal sin convertir cada botón en un bloque violeta.

### Cobertura funcional obligatoria

Aplicar Vigilia a todas las familias, incluyendo formularios, diálogos,
confirmaciones, menús, listas, visores y estados de error o sin datos:

- Inicio, Calendario, próxima jornada, selección, detalle y acciones por jornada.
- Carga unificada, instituciones/servicios, horarios, puesto/función, notas e
  imágenes privadas, edición, conflictos, repetición y carga masiva.
- Mi forma de trabajar, primeros pasos y cuatro sectores laborales; horas,
  meta, tipos de extras, disponibilidad/Retén, francos, feriados, vacaciones,
  carpeta médica y situaciones de jornada ya implementadas.
- Resumen, desgloses y filtros; Informes, inclusiones privadas y confirmaciones.
- Fotos del cronograma, Notas y sus visores/selectores propios.
- Avisos, Clima/ubicación/créditos, configuración y contenido propio del Widget.
- Copias, exportación/restauración, bloqueo/autenticación, Apariencia, Ayuda/tour,
  Actualizaciones, soporte y aporte voluntario en sus entradas existentes.

No modificar la interfaz del sistema Android, Google Maps, navegador, teclado,
Photo Picker, SAF, autenticación biométrica o launcher. Sí conservar y comprobar
la transición, cancelación, retorno y estado de la UI propia alrededor de ellos.
La presentación de documentos exportados y el comportamiento del Widget,
alarmas, proveedores y actualizador no se reinventan por estética.

### Calendario y próxima jornada

Conservar el mes completo, siete columnas y cuatro/cinco/seis filas, sin scroll
mensual vertical u horizontal, semana forzada, barra inferior permanente ni
espacios reservados a acciones ausentes. Toque breve abre detalle; pulsación
larga nativa activa selección múltiple, con alternativa accesible en el menú.
Contador y acciones aparecen sólo en selección; Hoy y funciones desplazadas
siguen accesibles desde el menú, sin duplicados permanentes en el calendario.

Aplicar ADR 0043, autorizado el 2026-10-11: retirar zoom interno y heredar letra
y densidad nativas de Android, sin neutralizar su conversión SP. Conservar
objetivos táctiles de 48 dp y garantías mensuales de ADR 0042. TalkBack sin
controles extra. No modificar ajustes globales del teléfono. Copias conserva
las 17 claves y el zoom legado inerte, sin cambiar formato ni restauración.

Mantener próxima jornada elegible real: título, cuándo ocurre —hoy, mañana o
fecha—, tiempo restante real en días/horas/minutos, identidad y horario. Conservar
estados en curso/sin próxima y actualización temporal/primer plano. En ventanas
bajas priorizar el mes y aplicar la política vigente de omisión de esa tarjeta;
preservar el aviso breve en encabezado con detalle al tocarlo. No imponer la
altura ni las proporciones fijas de la maqueta a todos los dispositivos.

Mostrar completadas con verde discreto y check, hoy y selección distinguibles,
y condiciones con semántica completa. Reducir información secundaria sólo según
la política vigente; detalle y accesibilidad conservan el contenido completo.
Una síntesis horaria como `08–16` no puede ocultar minutos, cruce de medianoche
o jornadas múltiples: resolver esos casos explícitamente, no inventar datos.

### Detalle y carga sin pérdida de garantías

Conservar fecha humana, un único Editar día y tarjetas independientes por UUID,
con color, institución, dirección opcional/Maps, horario completo, estado,
puesto, Notas/imágenes, Avisos, Clima contextual y horario real. No fusionar dos
jornadas porque coincidan objetivo/horario. Las condiciones del día aparecen
cuando existen; no ocupar espacio con plantillas vacías.

Conservar el formulario unificado: fechas seleccionadas —hasta tres resumidas,
resto expandible en orden con día de semana—, catálogo, creación contextual de
institución y primer horario, elección/agregado de horarios, Franco, puesto,
nota/imágenes, regreso a selección, guardar, limpiar y cancelar. Mantener
servicio, horario y borrador al volver, vaciar/reseleccionar fechas y recrear.
Guardar sólo las fechas finales; un lote nuevo no hereda un borrador cancelado.

Mantener preview de pérdidas, Sobrescribir/Superponer, confirmaciones, identidad
de operación, transacciones atómicas y CAS —detección de cambios concurrentes—.
Cancelar no escribe. No reemplazar esos mecanismos por el guardado de ejemplo.
Conservar un dueño efectivo de Atrás: GENERAL, MANUAL_LOAD o HOLIDAY; teclado,
diálogo, hoja y navegación no deben consumir la misma pulsación dos veces.

### Hallazgos y legibilidad transversal

Resolver V01 —cifra de horas fragmentada— y V02 —texto de inclusión médica contra
el interruptor— con regresiones que midan contenido y separación reales. Tratar
V04 como hipótesis de descubribilidad: diferenciar acciones disponibles de
deshabilitadas y someterlo a revisión humana, sin afirmar comprensión automática.

Abordar V03/V05/V06/V07 mediante síntesis legible, ritmo visual, instrucciones
dosificadas y vocabulario consistente. Conservar explicaciones, riesgos y
confirmaciones sensibles; no ocultarlas para que una pantalla parezca limpia.
Fechas visibles coherentes con su contexto y español argentino cuidado; no
cambiar formatos de persistencia/exportación ni términos laborales de dominio.

### Frontera de archivos y ejecución

Permitidos: presentación en `app/src/main/java/com/blackatsystems/miguardia/ui/`,
UI de `reports/`, `backup/`, `security/`, `updates/` y Widget; recursos locales,
integración visual mínima en Activity, regresiones, catálogo QA y documentación
del bloque. Dentro de paquetes mixtos, los coordinadores, acceso a datos,
archivos, red, cifrado y reglas siguen fuera del rediseño. ViewModels sólo si
un ajuste de presentación es indispensable y queda demostrado como tal.

Primero fijar tokens/componentes compartidos e inventario de conservación;
después integrar por familias completas y verificar el conjunto. Son hitos
internos de un stage, no chats nuevos por botón. Si se delega, el productor del
tema estabiliza su contrato antes de los consumidores. MAIN controla
Theme/VigiliaTokens/AppZoom, Activity, MiGuardiaApp, strings, catálogo y STATUS;
un escritor por archivo, una compilación y un operador de dispositivo a la vez.
Revisores independientes pueden trabajar sólo lectura. No crear tareas,
worktrees o ramas sin autorización específica.

## DEPENDENCIES

- Dirección Vigilia elegida por Joaquín y este contrato inicializado expresamente.
- Candidato acumulado preservado, Puerta 0 resuelta y fuentes actuales accesibles.
- Corpus Stage 6 nuevo como comparación; no hace falta repetirlo desde cero.
- QA física requiere autorización específica para Stage 7, disponibilidad del
  Samsung y sólo datos ficticios. Estar conectado o un permiso de otro stage
  no la concede. La implementación local no depende de dispositivo ni servicios.

## DO NOT

- No implementar durante la preparación documental de este contrato.
- No quitar funciones, imponer una navegación incompleta, copiar placeholders,
  fabricar disponibilidad ni sustituir reglas por el prototipo.
- No cambiar dominio, cálculos de horas, estados, identidad, recurrencia, CAS,
  Room V8/esquemas/migraciones, formato o compatibilidad de Copias, cifrado,
  preferencias de privacidad, permisos, FLAG_SECURE, autenticación ni política
  de red. Si un impedimento exige esas fronteras, demostrarlo y pedir decisión.
- No cambiar applicationId, versión, certificado, firma, Gradle/dependencias,
  website, distribución, ni reactivar beta/cupos/feedback obligatorio.
- No tocar la app pública ni datos personales, publicar capturas privadas o
  transmitir datos laborales. Sin backend, cuentas, telemetría o servicios nuevos.
- No forzar contraste bajo, objetivos pequeños, recortes, scroll mensual o
  texto diminuto para imitar la referencia. No rediseñar pantallas del sistema.
- No limpiar checkout/cachés/evidencia, relajar pruebas para hacerlas pasar,
  reutilizar corpus obsoleto o atribuir verdes antiguos al APK nuevo.
- No commit, push, tags, firma, instalación o publicación por este prompt.
  La eventual instalación QA necesita autorización explícita del ensayo.

## VALIDATION

1. Sellar entrada y delta propio. Completar la matriz de conservación desde
   código/rutas y pruebas, no sólo desde el atlas; revisar todas las familias
   y sus estados. Contrastarla independientemente antes de entregar.
2. Regresiones dirigidas para tokens/estados, composición, V01/V02, fechas,
   cifras y unidades, etiquetas largas, interruptores, teclado, foco y acciones.
   Medir contraste sobre superficies reales: exigir al menos 4,5:1 para texto
   normal y 3:1 para texto grande y controles esenciales; no depender sólo de color.
3. Matriz mensual: claro/oscuro × letra Android simulada 1/1,5/2 × retrato/paisaje × cuatro/cinco/seis
   filas. Incluir ventanas bajas, 28/29/30/31 días, horario nocturno/con minutos,
   varias jornadas y estados combinados. Medir fechas/objetivos contenidos,
   mínimos de 48 dp base, ausencia de solapamientos y scroll mensual.
4. Resto de superficies: letra Android/temas, contenido largo/poblado/vacío/error,
   guardado/cancelación y retorno. Probar las rutas críticas completas:
   selección → carga → catálogo → nota/imágenes → volver → guardar final;
   conflictos/Sobrescribir/Superponer/Limpiar; Atrás por dueño; recreación y
   restauración; consultas sin escrituras; privacidad de Informes/Copias/bloqueo.
5. Ejecutar perfiles local/build y catálogo según `docs/VALIDACION.md`, con
   regresiones pertinentes antes de la batería final. Interoperabilidad sólo
   si cambian sus consumidores/contratos o aparece un riesgo concreto; no usar
   servicios remotos para una renovación estética. Registrar reutilización y
   frescura con honestidad; no reconstruir todo después de cada cambio menor.
6. Con autorización física, instalar exclusivamente QA, comprobar hashes local/
   instalado y conservar el paquete público. Recorrer las familias con datos
   sintéticos, comparar capturas reales antes/después y verificar TalkBack,
   teclado, selectores, permisos y bloqueo en el alcance autorizado. No consultar
   álbumes personales ni solicitar PIN/huella por chat. Restaurar sólo ajustes
   temporales expresamente autorizados y registrar pendientes humanos.
7. Comparar rendimiento de recorridos afectados con la base pertinente de
   Stage 5, sin inventar umbrales nuevos o trasladar resultados de otro APK.
   Preservar fallos adversos, diagnosticar causas y repetir sólo lo afectado.
8. `python tools/docs_check.py`, `git diff --check` y revisión de todo el delta,
   no rastreados y ausencia de secretos, artefactos o datos privados. Registrar
   en auditoría APK/insumos/configuración/cobertura y lo que no pudo comprobarse.

## DONE WHEN

Vigilia está integrado en todas las familias propias, sin mezcla accidental de
estilos ni funciones perdidas; matriz y regresiones cubren la conservación;
V01/V02 están resueltos; mes, letra Android, accesibilidad, navegación, borradores,
transacciones, privacidad y fluidez mantienen sus garantías.

MAIN recibe un candidato revisado con delta, comparación, evidencia y límites;
Joaquín puede comprobarlo y decidir aceptación visual. Separar implementación,
verificación local, QA física, revisión humana y distribución. Si falta permiso,
recurso o una comprobación necesaria, entregar cierre parcial preciso: no
declarar acabado el stage ni aceptada la app. No comenzar otro alcance por inferencia.
