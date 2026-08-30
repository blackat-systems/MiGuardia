# Mapa maestro de MiGuardia 2.0

- Estado: fuente activa de producto
- Fecha: 2026-08-23
- Propietario del producto: Joaquin
- Alcance: explicar el producto aprobado y el orden de construcción

## 1. La idea en una frase

MiGuardia 2.0 es **una sola aplicación Android para organizar jornadas
laborales**, con un Calendario común y reglas de horas adaptadas a cuatro
sectores distintos: Vigilancia privada, Policía, Enfermería y Medicina.

No son cuatro aplicaciones ni cuatro perfiles simultáneos. Tampoco se fuerza
una misma regla laboral sobre los cuatro sectores.

## 2. De dónde parte

MiGuardia 1.0.0 fue la prueba interna y la base de código que ya resolvió el
caso de Vigilancia privada:

- calendario mensual;
- objetivos, horarios y guardias;
- francos, novedades, feriados, vacaciones y carpetas médicas;
- fotos privadas del cronograma;
- horas, próximo evento, notificaciones, clima y resumen mensual;
- datos locales, sin cuenta ni nube.

MiGuardia 2.0 continúa desde ese código y puede reutilizar todo componente que
siga siendo correcto. No existe, en cambio, una población usuaria ni datos 1.0
que deban migrarse: la experiencia 2.0 comienza como instalación limpia. El tag
`v1.0.0` se conserva como base técnica y evidencia, no como contrato de
compatibilidad de datos.

## 3. Los cuatro sectores exactos

El catálogo está cerrado:

1. Vigilancia privada;
2. Policía;
3. Enfermería;
4. Medicina.

Enfermería y Medicina son sectores independientes. No existe un sector
configurable llamado `Salud` y no existe la opción `Otro`.

## 4. Qué comparten

La base común de la aplicación incluye:

- el mismo Calendario mensual;
- lugares o servicios de trabajo;
- horarios y jornadas con inicio y fin reales;
- carga manual individual y múltiple;
- próximos eventos;
- notas, feriados, vacaciones, carpetas médicas y fotos;
- notificaciones, clima, widget, informes y copias;
- privacidad local y ausencia de cuentas o sincronización.

La interfaz puede cambiar palabras según el sector, pero reutiliza la misma
base técnica y las mismas garantías de datos.

## 5. Personalización sin fórmulas por profesión

El sector cambia palabras y ejemplos, pero no impone una forma universal de
trabajar. Cada persona configura:

- sus lugares de trabajo;
- sus tipos de trabajo;
- horarios exactos y colores;
- si utiliza una referencia de horas y de qué período;
- qué clases de trabajo extra ayudan a completar esa referencia;
- si utiliza disponibilidad y con qué nombre quiere verla;
- la franja nocturna y las reglas de feriado o fin de semana de cada lugar.

Una respuesta individual de un formulario sirve como evidencia, no como valor
predeterminado para todo el sector.

## 6. Piezas del producto

MiGuardia separa conceptos simples para no mezclar cálculo e interfaz:

| Pieza | Qué significa |
|---|---|
| Lugar de trabajo | Dónde trabaja la persona |
| Tipo de trabajo | Qué actividad realiza y cómo se muestra |
| Plantilla | Lugar + tipo + horario exacto + color |
| Plan recurrente | Regla que crea jornadas futuras concretas |
| Jornada | Lo planificado para una fecha |
| Horario real | Lo que finalmente ocurrió |
| Clase extra | Categoría elegida para tiempo adicional |
| Disponibilidad | Guardia pasiva, disponible para llamado o retén |
| Situación especial | Ausencia, carpeta, vacaciones, capacitación, cancelación, intercambio u otra |

Las jornadas guardan una fotografía histórica. Cambiar un lugar, plantilla,
plan o regla desde una fecha no reescribe el pasado.

## 7. Decisiones confirmadas

- MiGuardia 2.0 continúa sobre el código de 1.0 y protege el tag `v1.0.0`.
- No hay traspaso de datos, modo migrado ni activación V1→V2: toda experiencia
  2.0 comienza con configuración nueva.
- Existe una sola configuración laboral, con cambios desde una fecha concreta.
- Una instalación nueva elige primero el sector y entra al Calendario vacío.
- Todo trabajo activo suma al total trabajado.
- Las horas extras se declaran expresamente, poseen inicio y fin exactos y se
  muestran separadas sin dejar de integrar el total.
- Superar una referencia nunca crea horas extras automáticamente.
- Cada clase extra decide si ayuda a completar las horas requeridas.
- Al crear o cambiar una referencia, la persona elige desde qué fecha reinicia
  el conteo —por ejemplo hoy o el próximo lunes—. MiGuardia no prorratea la
  meta ni la aplica hacia atrás.
- Dos trabajos activos superpuestos se advierten y, si el usuario los conserva,
  ambos suman completos.
- La disponibilidad se informa aparte. El trabajo activo reemplaza sólo el
  tramo pasivo coincidente.
- No existen pausas descontables; un corte real se representa con dos jornadas.
- Nocturnidad, feriado y fin de semana son clasificaciones superpuestas: pueden
  coincidir, pero no duplican el total trabajado.
- Una jornada pertenece al día y mes donde comienza; las clasificaciones miran
  los instantes reales aunque cruce medianoche.
- MiGuardia no incorpora tablas salariales, montos ni liquidaciones.

## 8. Calendario y Resumen

El Calendario conserva una sola grilla mensual:

- en consulta, tocar un día abre sus detalles sin modificar datos;
- `Editar este día` permite cambiar únicamente esa fecha;
- `Editar calendario` abre el recorrido separado para una o varias fechas;
- una sola jornada muestra abreviatura y horario;
- varias jornadas muestran únicamente `2 turnos`, `3 turnos`, etc.;
- la tarjeta superior de la aplicación se despliega para mostrar todas las
  jornadas del día, incluidas las completadas.

El Resumen siempre muestra lo esencial y permite ordenar u ocultar detalles
desde `Personalizar resumen`. La persona personaliza la presentación, no las
fórmulas.

## 9. Investigación sectorial

Las respuestas se registran de forma anónima en `docs/sectores/`. La evidencia
disponible confirma diversidad de nombres y modalidades, por lo que no se crean
reglas universales. La investigación futura puede mejorar textos y ejemplos sin
bloquear la arquitectura personalizable aprobada.

## 10. Orden de construcción

```text
Reglas puras y pruebas
        ↓
Configuración local V2
        ↓
Lugares, tipos, plantillas y primera configuración
        ↓
Carga manual
        ↓
Edición y eliminación individual de jornadas
        ↓
Retiro del modo V1 antes de ampliar nuevamente la persistencia
        ↓
Planes recurrentes y edición del Calendario
        ↓
Horario real y extras exactas — cerrado
        ↓
Extras independientes y avance de horas — cerrado
        ↓
Guardias pasivas y disponibilidad — cerrado
        ↓
Calendario final y tarjeta superior desplegable — cerrado
        ↓
Resumen personalizable — cerrado
        ↓
Próximo evento y notificaciones — cerrado
        ↓
Auditoría integral del núcleo y compatibilidad Android — parcial histórica
        ↓
Pruebas cruzadas del núcleo V2 — cerradas
        ↓
Matriz Android 36/26/33 — cerrada
        ↓
Repetición de auditoría integral — NÚCLEO APTO PARA SEGUNDA CAPA
        ↓
Widget de próximo evento — cerrado; Samsung verde, API 26/33 pendientes
        ↓
Informes locales — cerrado; local y Samsung API 36 (33/33) verdes
        ↓
Copias y restauración locales — siguiente recomendado / no habilitado
        ↓
Bloqueo
        ↓
Ayuda y recorrido inicial 2.0
        ↓
Auditoría de la aplicación completa y candidato local
```

Corrección de secuencia del 2026-08-27: Joaquin indicó que el bloque siguiente
era Calendario final y tarjeta superior; ese bloque ya quedó cerrado por MAIN.
Las capacidades comunes preservadas
—`F/?`, carpeta médica, vacaciones, feriados, notas y los estados internos de
ausencia/cancelación— continúan disponibles según su alcance actual. Un flujo
V2 ampliado de situaciones especiales o una consolidación adicional de horas
queda diferido y no bloquea este recorrido.

Actualización del 2026-08-28: la dependencia auditora ejecutó la batería local
y no reprodujo defectos P0/P1, pero devolvió `AUDITORÍA PARCIAL — NO CERRABLE`.
Faltan una fotografía transversal única, una carrera CAS real, una prueba
explícita de consultas sin escrituras y la matriz actual en Samsung API 36,
Android 8/API 26 y Android 13/API 33. En ese momento la segunda capa permanecía
cerrada hasta corregir esos huecos y repetir esta puerta. Joaquin indicó
preparar la dependencia `PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md`.

Actualización del 2026-08-29: las tres pruebas cruzadas quedaron integradas y
verificadas. La matriz actual pasó en Samsung API 36, Android 8/API 26 y
Android 13/API 33. La repetición integral devolvió
`NÚCLEO APTO PARA SEGUNDA CAPA` sin findings abiertos. La segunda capa queda
desbloqueada y su primer bloque es Widget.

Actualización posterior del 2026-08-29: MAIN recibió, auditó, corrigió y cerró
`WIDGET_DE_PROXIMO_EVENTO_V2.md`. La batería local y Samsung API 36 quedaron
verdes, sin Glance ni cambio de Room. Android 8/API 26 y Android 13/API 33 se
conservan como compatibilidad pendiente. Informes es el próximo bloque
recomendado.

Actualización de Informes del 2026-08-29: Joaquin autorizó continuar. MAIN
auditó las fuentes V2, fijó ADR 0034 y habilitó
`INFORMES_LOCALES_DE_JORNADAS_Y_HORAS_V2.md`. El bloque debe reutilizar la
misma fórmula de Horas y Resumen, generar PDF/XLSX local sin dependencia nueva
y mantener notas, nombre, puesto y fotos apagados por defecto. La
implementación quedaba pendiente en ese momento hasta que la única tarea
especialista pasara Puerta 0.

Actualización de cierre del 2026-08-29: MAIN recibió el handoff de Informes,
auditó y corrigió el candidato y repitió la batería contractual. PDF y XLSX se
generan localmente desde la misma verdad de Horas/Resumen, las inclusiones
privadas comienzan apagadas y la versión, las entidades, las migraciones y los
esquemas de Room V5 permanecen intactos. La matriz definitiva pasó en Samsung
API 36 con 33/33 casos —app 28 y Room 5—. Informes queda cerrado; Copias y
restauración continúa como siguiente bloque recomendado, pero no tiene prompt ni
tarea habilitados. Por indicación expresa de Joaquin, MAIN se detiene después
del checkpoint local de Informes.

MAIN integra un bloque por vez. Cada bloque debe compilar, pasar sus pruebas,
preservar el alcance ajeno y quedar documentado antes del siguiente. El mapa
permite recomendar el orden; Joaquin indica cuándo preparar o abrir cada nueva
tarea.

## 11. Fuentes de verdad

- Este archivo explica **qué producto se está construyendo**.
- `docs/PLANIFICACION_MIGUARDIA_2_0.md` contiene **todas las reglas y el orden**.
- `docs/sectores/` conserva **la evidencia sectorial anónima**.
- `docs/STATUS.md` indica **qué bloque está activo hoy**.
- `docs/prompts/README.md` indica **qué prompt puede ejecutarse**.
- `docs/PROMPT_MAESTRO_MAIN_2_0.md` gobierna **la integración activa**.
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md` gobierna **cómo MAIN recibe,
  audita y cierra un handoff por vez, y cuándo puede preparar otra tarea**.
