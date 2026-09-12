# Mapa del producto — MiGuardia 2.0

Resumen del producto aprobado. Reglas completas: `docs/PLANIFICACION_MIGUARDIA_2_0.md`. Estado actual, evidencias y siguiente paso: únicamente `docs/STATUS.md`.

MAIN ejecuta el orden del [flowchart de orquestación](ORQUESTACION_MAIN.md), adoptado por Joaquín el 8/09/2026. Este mapa del producto conserva su función de resumen; no mantiene otra secuencia de despacho.

MiGuardia es una aplicación Android personal para organizar jornadas y horas. Comparte Calendario y capacidades locales entre Vigilancia privada, Policía, Enfermería y Medicina. El sector cambia vocabulario y ejemplos; cada persona configura cómo trabaja. Hay una sola configuración laboral, con historia por fecha, sin perfiles simultáneos ni sector `Otro`.

## Piezas

| Pieza | Función |
|---|---|
| Lugar y tipo de trabajo | Dónde y qué se hace |
| Plantilla | Lugar/tipo, horario exacto y color reutilizables |
| Plan recurrente | Crea jornadas futuras concretas y enlazadas |
| Jornada e instantánea | Plan de una fecha e historia que no se reescribe |
| Horario real y extra | Correcciones e intervalos adicionales expresos |
| Disponibilidad | Ventana pasiva informada aparte del trabajo activo |
| Referencia de horas | Meta opcional por mes, semana o ciclo, nunca fórmula salarial |

## Capacidades comunes

Calendario de una sola grilla, carga individual/múltiple, detalle y edición consciente, lugares y horarios, recurrencias, horas y Resumen personalizable, próximo evento, avisos, Widget, notas y feriados manuales, vacaciones y carpetas médicas sin certificados, fotos privadas, informes PDF/XLSX, Copias, bloqueo opcional, Ayuda y zoom interno.

Las jornadas guardan instantáneas. Cambiar una plantilla, lugar o regla no reinterpreta el pasado. Las extras no nacen de superar una meta; todo trabajo activo suma y la disponibilidad se informa aparte. Las clasificaciones noche/feriado/fin de semana pueden coincidir sin duplicar minutos.

## Privacidad y distribución

Datos laborales locales, sin cuenta laboral ni sincronización. Clima y ubicación son opcionales y conscientes. La distribución aprobada es pública desde la web, sin activación, cupos ni feedback obligatorio (ADR 0041); opiniones voluntarias sin adjuntar datos laborales automáticamente. Apoyar MiGuardia abre una página externa voluntaria sin beneficios de acceso. El estado de transición del código beta está en STATUS.

V2 reutiliza código de la prueba V1 y comienza limpia; no migra datos V1. Transición pública, revisión de seguridad, firma, ensayo de actualización instalada y publicación son alcances verificables diferentes. Que una función exista no demuestra que haya una APK publicable: consultar STATUS.

## Ideas que requieren una decisión nueva

La ampliación de situaciones especiales, una futura Agenda profesional y un quinto sector no forman parte del alcance actual. Sus límites están conservados explícitamente en planificación. No iniciar trabajo por aparecer en este mapa.
