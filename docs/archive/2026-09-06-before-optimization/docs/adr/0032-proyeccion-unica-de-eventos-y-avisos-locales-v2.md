# ADR 0032: Proyección única de eventos y avisos locales V2

- Estado: aceptada
- Fecha: 2026-08-27

## Contexto

MiGuardia conserva un motor puro de próximo evento y un sistema local de
notificaciones probado desde 1.0. Después de los incrementos V2, sus fuentes ya
no coinciden por completo.

La tarjeta superior observa jornadas, vacaciones, carpetas médicas y horario
real, pero todavía no disponibilidad ni la fotografía laboral V2 completa. El
planificador, reconciliador y receptor de avisos continúan centrados en
`Shift`, vacaciones y excepciones por jornada. Esa diferencia puede dejar una
alarma obsoleta o hacer que tarjeta y aviso describan estados distintos.

La disponibilidad ya posee un cálculo puro de tramos efectivos: el trabajo
activo reemplaza solamente la unión coincidente. Las recurrencias ya
materializan jornadas concretas. Los extras independientes representan trabajo
realizado y no son eventos futuros.

## Decisión

### Proyección compartida

Dominio publica una única proyección pura, inmutable y tipada para jornada,
tramo efectivo de disponibilidad y franco informativo. La tarjeta superior y
el plan de avisos consumen la misma elegibilidad, prioridades e identidades.

La proyección recibe instante y zona explícitos, usa intervalos
`[inicio, fin)` y ordena por inicio, fin, tipo e identidad estable. Conserva
eventos simultáneos aunque elija uno principal.

La prioridad es:

1. jornada activa;
2. disponibilidad efectiva activa;
3. comienzo futuro más cercano, con jornada antes que disponibilidad en un
   empate;
4. franco explícito sólo para la tarjeta;
5. vacío.

### Jornadas y realidad

Una jornada candidata se lee como el par histórico
`Shift + ShiftWorkSnapshot`. Debe estar `PLANNED`, conservar final futuro y no
estar protegida por vacaciones o carpeta médica. Una jornada cancelada, ausente
o con horario real ya confirmado deja de ser un evento planificado pendiente.

El horario real no reemplaza `Shift.startAt/endAt`: esos instantes continúan
siendo la planificación histórica. Su existencia impide avisos atrasados, pero
no crea nuevas alarmas contra el intervalo real.

Las ocurrencias recurrentes entran únicamente como jornadas materializadas.
Un extra independiente no es un evento futuro. Una `extra programada` se
interpreta como una jornada concreta cuyo tipo histórico fue definido por la
persona; este bloque no crea otra entidad.

### Disponibilidad

La disponibilidad conserva identidad y etiqueta histórica propias. Sus
eventos se derivan de los tramos efectivos producidos por el motor existente,
sin convertirlos en jornada ni trabajo.

Una ventana totalmente protegida o reemplazada no produce evento. Una
reanudación después del trabajo activo puede volver a mostrarse, pero se
reconcilia silenciosamente para evitar una secuencia invasiva de alertas por
fragmentación derivada.

### Avisos y persistencia

Las preferencias globales existentes se aplican a jornadas y disponibilidad.
Las excepciones particulares continúan sólo por jornada; no se agrega una tabla
por disponibilidad.

La identidad de alarmas y avisos pasa a ser tipada. DataStore interpreta una
identidad histórica formada sólo por UUID como jornada, para no revivir alarmas
o avisos ocultados al actualizar el formato.

Las fronteras continúan siendo recordatorio, inicio y fin. Los receptores
releen la fuente y el contexto V2 completo antes de publicar. Editar, eliminar,
cancelar, registrar horario real, agregar una protección o modificar una
ventana invalida y reconstruye sólo lo necesario.

Room continúa en versión 5 con veintisiete tablas y esquemas 1 a 5 intactos.
Se permiten nuevas consultas de sólo lectura, pero no entidades, columnas ni
migraciones.

### Android y privacidad

Se conservan las preferencias, Pulso Vigilia, canales deterministas,
cronómetro nativo, agrupación, ocultamiento, restauración, permiso runtime y
fallback inexacto existentes.

No se agregan permisos, `USE_EXACT_ALARM`, WorkManager, polling, servicio en
primer plano, pantalla completa ni alarma despertador. Una alarma es una
frontera reconstruible, nunca fuente de verdad.

El vocabulario común es `Jornada`; disponibilidad usa `Guardia pasiva`,
`Disponible para llamado` o `Retén` según su fotografía. No se recupera
`Informar novedad` de V1. Los DTO de presentación excluyen notas, motivos
médicos, explicaciones privadas, fotos y cualquier campo que la superficie no
necesite.

## Consecuencias

- Tarjeta y avisos no pueden decidir elegibilidad con reglas distintas.
- Las correcciones V2 retiran alarmas obsoletas de forma reactiva e
  idempotente.
- La disponibilidad puede ser próxima o activa sin sumarse al trabajo.
- El widget futuro puede reutilizar la misma proyección.
- Cambiar las identidades internas exige compatibilidad explícita con las
  claves y `PendingIntent` ya existentes.
- Android conserva el control final de permisos, exactitud y canales.
- API 26, permiso moderno y vistas de sistema requieren instrumentación real;
  compilar AndroidTest no alcanza.

## Alternativas descartadas

### Mantener dos motores

Se descarta porque permitiría que una protección o disponibilidad aparezca en
la tarjeta pero no invalide un aviso.

### Convertir disponibilidad en jornada

Se descarta porque alteraría su semántica y la sumaría incorrectamente como
trabajo.

### Crear extras futuros independientes

Se descarta porque el modelo actual representa extras ya realizados. Una nueva
entidad sería una decisión de producto y persistencia fuera de este bloque.

### Agregar excepciones Room por disponibilidad

Se descarta porque las preferencias globales cubren el alcance inicial y no se
justifica ampliar el esquema.

### Asegurar exactitud con un servicio o polling

Se descarta por consumo, privacidad y porque Android ya ofrece alarmas locales
con una degradación honesta cuando el acceso exacto no está disponible.
