# Planificación canónica de MiGuardia 2.0

- Estado: **cerrada y aprobada para ejecución**
- Fecha de cierre: 2026-08-21
- Propietario del producto: Joaquin
- Base técnica de código: MiGuardia `v1.0.0`
- Ejecutor: MAIN 2.0 por bloques verificables

## 1. Objetivo

MiGuardia 2.0 organiza jornadas y horas de Vigilancia privada, Policía,
Enfermería y Medicina dentro de una sola aplicación Android local. No existen
cuatro aplicaciones, perfiles laborales simultáneos ni una opción `Otro`.

El diseño es personalizable porque dos personas del mismo sector pueden trabajar
de maneras completamente distintas. El sector adapta palabras y ejemplos; las
reglas reales las configura cada usuario.

## 2. Límites permanentes

- Android es la plataforma inicial.
- Los datos permanecen locales, sin cuenta, nube, sincronización, analítica ni
  telemetría.
- Se mantiene por ahora `applicationId = "com.blackatsystems.miguardia"`, sin
  convertirlo en una promesa de migración desde 1.0.
- El tag `v1.0.0` no se mueve ni reescribe.
- MiGuardia 1.0 fue una prueba interna sin usuarios externos: no se migran sus
  datos, preferencias, permisos, alarmas ni archivos a 2.0.
- No se guardan DNI, correo, teléfono, matrícula ni domicilio personal.
- No se guardan imágenes de certificados médicos.
- No se incorporan montos, escalas, salarios, liquidaciones ni deducciones.
- No se incorporan agenda de pacientes ni datos clínicos.
- No se incorporan ubicación automática, OCR ni importación directa de Excel.

MiGuardia 1.0 continúa como base de código: sus componentes útiles se adaptan en
lugar de reescribirlos sin motivo. Esa continuidad técnica no crea un modo V1
dentro del producto final ni obliga a conservar una base de datos de prueba.
Tampoco autoriza borrados silenciosos en el teléfono de Joaquin.

## 3. Una configuración con historia

- Cada usuario tiene una sola configuración laboral.
- Un cambio se aplica desde una fecha local concreta y continúa hasta el
  siguiente cambio.
- No se limita la vigencia al primer día de un mes.
- Cambiar reglas, lugar, plantilla o plan nunca reinterpreta jornadas
  anteriores.
- Al archivar o reemplazar lugares y plantillas, dejan de ofrecerse para nuevas
  cargas, pero conservan todo su historial.
- Un eventual cambio de sector después de la elección inicial no pertenece a la
  secuencia vigente y requiere una decisión de producto explícita de Joaquin.
- Una instalación nueva no recibe automáticamente 204 horas ni una franja
  nocturna.

## 4. Piezas que se guardan separadas

### Lugar de trabajo

Representa dónde trabaja la persona. La interfaz usa vocabulario sectorial:

| Sector | Palabra visible principal |
|---|---|
| Vigilancia privada | Objetivo |
| Policía | Dependencia o lugar de servicio |
| Enfermería | Institución o servicio |
| Medicina | Hospital, clínica, consultorio o servicio |

Todo lugar nuevo requiere:

- nombre;
- nombre corto de tres a cinco letras, normalizado a mayúsculas;
- dirección opcional;
- nota personal opcional.

Las abreviaturas históricas de dos letras continúan siendo válidas y editables
sin pérdida.

Cada lugar puede definir, desde una fecha:

- si utiliza horas nocturnas y su franja exacta;
- si distingue sábado, domingo o ambos;
- si feriado o fin de semana tiene un tratamiento diferente;
- si esas clasificaciones aparecen en el Resumen.

`Tratamiento diferente` es una marca informativa. No guarda montos.

### Tipo de trabajo

Representa qué actividad realiza la persona. Al configurar el primer lugar se
crea `Trabajo habitual`, con el texto adaptado al sector. Puede renombrarse.

Consultorio y Capacitación son tipos distintos. Compartir lugar y horario no
los mezcla. El usuario puede crear otros tipos cuando los necesite.

### Plantilla

Es una combinación reutilizable de:

- lugar;
- tipo de trabajo;
- inicio exacto;
- final exacto;
- color.

Inicio igual a final representa 24 horas. No existen plantillas genéricas de
`día` o `noche`: siempre se conserva el horario exacto.

### Jornada concreta

Representa lo planificado para una fecha y guarda una fotografía del lugar,
abreviatura, tipo, horario, color, función o puesto. Editar la plantilla no
modifica esa fotografía.

### Plan recurrente

Es una regla que crea jornadas futuras concretas y enlazadas. Se admiten:

- días elegidos de la semana;
- cada cierta cantidad de días o semanas;
- patrón mensual, como primer lunes o último viernes.

Todo plan requiere inicio, finalización y vista previa de fechas.
Cada creación o cambio de plan puede abarcar como máximo 2.000 jornadas
concretas futuras. Si el patrón supera ese límite, se rechaza completo con una
explicación; nunca se recorta silenciosamente.

## 5. Primera apertura

### Instalación nueva

1. Elegir uno de los cuatro sectores.
2. Entrar al Calendario vacío y utilizable.
3. Mostrar `Todavía no cargaste ningún lugar de trabajo`.
4. Crear el primer lugar en dos pasos breves.
5. Crear obligatoriamente al menos una plantilla antes de finalizar ese lugar.
6. Elegir entre volver al Calendario, agregar otro horario o agregar otro lugar.

No se pide al comienzo una fórmula completa de horas. Las opciones avanzadas
aparecen cuando la persona realmente las necesita.

### Reemplazo de la prueba 1.0

- No existe actualización de datos ni un estado `migrado` que deba mostrarse.
- Toda primera apertura de 2.0 comienza con el selector de los cuatro sectores.
- Una instalación de prueba anterior se desinstala o limpia únicamente mediante
  una acción expresa antes de validar 2.0; la app no borra datos silenciosamente.
- El código útil de 1.0 puede seguir siendo la implementación de base después
  de adaptarlo a los contratos V2.

## 6. Horas de trabajo

Todos los cálculos usan minutos enteros e intervalos exactos `[inicio, fin)`. El
reloj y la zona son inyectables para producir pruebas deterministas.

### Totales principales

```text
Total trabajado = trabajo habitual + todas las clases extra

Cumplimiento = trabajo habitual elegible
             + clases extra configuradas para ayudar a cumplir
             + situaciones especiales que expresamente correspondan
```

- El trabajo habitual siempre forma parte del total trabajado.
- Las extras también forman parte del total y se muestran separadas.
- Cada clase extra decide si ayuda a completar la referencia.
- Superar una referencia nunca crea horas extras automáticamente.
- Una cobertura completa o un cubrefranco es trabajo habitual salvo que el
  usuario informe tiempo adicional concreto. El retén permanece como
  disponibilidad; si durante ese retén existe trabajo activo, se registra ese
  trabajo y se reemplaza sólo el tramo pasivo que coincide.
- Dos trabajos activos solapados se advierten. Si el usuario los mantiene, cada
  jornada suma su duración completa.
- No existen pausas descontables. Un corte real se representa con dos jornadas.

## 7. Referencia de horas

La referencia puede estar:

1. definida y conocida;
2. existente pero desconocida;
3. no utilizada.

Desconocida o no utilizada nunca significa cero.

Períodos admitidos:

- mensual;
- semanal, con lunes como sugerencia editable;
- ciclo personalizado, por ejemplo 14, 21 o 28 días, con fecha de inicio.

La cantidad puede permanecer fija hasta que se cambie o informarse período por
período. Un período sin valor queda `Falta informar`, no cero.

Al crear o cambiar la referencia, MiGuardia pregunta desde qué fecha local se
reinicia el conteo. Ofrece como mínimo empezar hoy, empezar en el próximo límite
normal —por ejemplo el próximo lunes o el primer día del mes siguiente— o
elegir otra fecha. La referencia anterior continúa hasta el día previo.

Si la persona elige reiniciar dentro de un mes, semana o ciclo ya iniciado, el
nuevo tramo comienza en cero y usa la meta completa. MiGuardia explica que ese
primer tramo será más corto, pero no prorratea, no combina metas y no aplica la
referencia nueva hacia atrás. Una revisión que cambia otro dato sin cambiar ni
reiniciar conscientemente la referencia no reinicia las horas.

El Resumen mensual muestra el total del mes y, dentro de `Cumplimiento de
horas`, las semanas o ciclos completos que tocan ese mes. Un ciclo no se corta
artificialmente en la medianoche del último día mensual.

## 8. Horario planificado y real

Cada jornada conserva:

- horario planificado;
- horario real opcional;
- motivo obligatorio cuando son distintos;
- explicación opcional;
- fecha de la corrección.

Reglas:

- sin corrección, se usa el horario planificado;
- si se trabajó menos, cuenta el horario real menor;
- si se trabajó más, MiGuardia pregunta cómo clasificar la diferencia;
- si la diferencia es extra, se guarda como intervalo exacto y clase elegida;
- si no es extra, el horario real completo permanece como trabajo habitual.

No se admite una extra con sólo cantidad de horas. Toda extra posee fecha,
inicio y final.

Clases iniciales sugeridas:

- Horas extras;
- Extensión de turno;
- Servicio extra.

La persona puede crear más. Cada clase define si ayuda a cumplir la referencia
y si aparece separada en el Resumen.

## 9. Disponibilidad

Existe un solo concepto interno. La persona elige su nombre visible:

- Guardia pasiva;
- Disponible para llamado;
- Retén.

Se carga mediante fecha, inicio y final exactos. No pregunta recurrencia, pago
ni referencia al crearla.

```text
Disponibilidad efectiva = ventana programada - unión del trabajo activo coincidente
```

- El trabajo activo reemplaza sólo el tramo superpuesto.
- Varias jornadas activas coincidentes no descuentan dos veces el mismo minuto
  de disponibilidad.
- La disponibilidad nunca se mezcla con el total trabajado.
- Una intervención se clasifica como habitual o como una clase extra elegida.
- El nombre elegido se reutiliza en Calendario, Resumen, próximo evento y
  notificaciones.

## 10. Noche, feriado y fin de semana

- Son clasificaciones del tiempo trabajado, no horas nuevas.
- Una misma hora puede ser nocturna y feriada, pero suma una sola vez al total.
- Se calculan sobre los instantes reales aunque la jornada cruce medianoche.
- Una jornada pertenece al día y mes donde comienza.
- Si comienza el 31 y termina el 1, todo su total pertenece al mes del 31.
- El tramo real del día 1 puede aparecer como feriado o nocturno dentro del
  detalle del mes de inicio.
- Las horas nocturnas aparecen en el Resumen cuando la regla del lugar está
  habilitada.
- Los feriados se cargan manualmente y pueden desplegar días y horas trabajadas.
- Fin de semana puede significar sábado, domingo o ambos según el lugar.
- Ninguna de estas clasificaciones convierte automáticamente tiempo en extra.

## 11. Planes recurrentes e historia

Al confirmar un plan se crean inmediatamente sus jornadas futuras concretas.
Cada una conserva un vínculo con el plan.

Al modificar:

- `Cambiar sólo esta jornada` afecta una fecha;
- `Cambiar desde esta fecha` crea una nueva versión futura;
- el pasado permanece intacto.

Al finalizar:

- `Finalizar desde esta fecha` retira únicamente jornadas futuras automáticas
  que no fueron modificadas;
- jornadas futuras retocadas, con notas o situaciones especiales se protegen;
- nunca existe una acción normal que reescriba el pasado.

Conflictos futuros permiten:

- conservar lo existente;
- reemplazar sólo jornadas automáticas intactas;
- mantener ambas después de una advertencia concreta.

## 12. Calendario

MiGuardia conserva una sola grilla mensual.

### Modo normal

- Permite navegar y consultar.
- Tocar un día abre sus detalles.
- Ninguna interacción de consulta escribe datos.
- Dentro del detalle aparece `Editar este día`.
- Ese editor sólo puede modificar la fecha abierta.

### Edición en masa

- Se inicia con `Editar calendario` desde una ubicación ergonómica.
- Permite elegir uno o varios días de la grilla principal.
- No abre otra grilla ni un selector duplicado.
- Una selección no cruza meses sin confirmación.

### Contenido de las celdas

- Una jornada: abreviatura y horario exacto.
- Varias jornadas: `2 turnos`, `3 turnos`, etc., sin mostrar primero una y `+N`.
- Un día vacío se muestra como sin definir; no se convierte en disponibilidad.
- Una jornada completada usa gris, marca y texto o semántica de `Completado`, no
  sólo color.

### Tarjeta superior

- Si hoy posee varias jornadas, se puede desplegar.
- Cerrada muestra la jornada en curso o la próxima pendiente.
- Desplegada muestra todas las jornadas de hoy, incluidas las completadas.
- Si hoy no tiene trabajo, muestra el próximo evento futuro.

Esta tarjeta pertenece a la aplicación. El widget de la pantalla de inicio de
Android es un bloque posterior.

## 13. Resumen

Información esencial automática:

- total trabajado;
- trabajo habitual;
- extras, cuando existan;
- pendiente programado;
- cumplimiento, cuando exista referencia;
- disponibilidad, cuando se utilice.

Desde los tres puntos, `Personalizar resumen` permite mostrar, ocultar y ordenar:

- noches, feriados y fines de semana;
- horario planificado frente a real;
- lugares y tipos de trabajo;
- clases extra;
- situaciones especiales e intercambios.

La primera visita informa que esta personalización existe. No se muestran
tarjetas vacías. Tocar una cifra explica qué jornadas la integran. El usuario no
puede escribir fórmulas arbitrarias.

## 14. Situaciones especiales

> Estado de secuencia actualizado el 2026-08-27: V2 conserva las capacidades
> comunes de `F/?`, carpeta médica, vacaciones, feriados, notas y los estados
> internos `ABSENT`/`CANCELLED`. El antiguo recorrido estructural de Novedades
> V1 fue retirado. Las ampliaciones descriptas en esta sección permanecen como
> diseño futuro y no constituyen la próxima dependencia ni bloquean Calendario
> final y tarjeta superior.

### Ausencia

- Conserva la jornada planificada.
- No suma tiempo trabajado ni cumplimiento.
- La diferencia permanece pendiente y puede recuperarse trabajando otro día.
- Puede guardar motivo y nota privada.

### Carpeta médica

- Guarda fecha inicial, final y nota opcional.
- Informa días en el Resumen.
- No suma ni resta horas y no transforma días en una remuneración.
- Las jornadas comprendidas dejan de aparecer pendientes, pero se conservan.
- Nunca admite una foto del certificado.

### Vacaciones

- Guarda un rango inclusivo de días.
- Informa días, no horas.
- Las jornadas comprendidas se conservan y dejan de estar pendientes.

### Capacitación

- Es distinta de Consultorio y de Trabajo habitual.
- En cada carga se elige paga o no paga.
- Paga integra trabajado y cumplimiento; no paga se informa separada.
- Si reemplaza exactamente una jornada existente, el plan original se conserva
  como antecedente.

### Cancelación

- Conserva la jornada original.
- No suma trabajado ni cumplimiento.
- Exige motivo, quién canceló o `No informado`, y momento del aviso.
- Puede adjuntar evidencia local opcional.
- MiGuardia ofrece un registro personal, no certificación legal.

### Intercambio o cobertura

- Registra la cobertura y su devolución futura.
- Puede quedar pendiente o completarse inmediatamente.
- Si las duraciones difieren, la diferencia puede cerrarse o quedar pendiente.
- Una cobertura sin devolución se clasifica como habitual o extra.

### Otra situación

En cada carga pregunta:

- nombre;
- fecha u horario;
- si reemplaza una jornada;
- si suma trabajado;
- si ayuda al cumplimiento;
- si aparece separada en el Resumen.

No recuerda automáticamente esas respuestas para la próxima ocasión.

## 15. Próximo evento y notificaciones

- Se reutiliza el motor único de MiGuardia 1.0.
- Incluye jornadas habituales, extras programadas, recurrencias y disponibilidad.
- Reutiliza preferencias globales y excepciones por jornada existentes.
- Una extensión registrada después de finalizar no genera un aviso atrasado.
- Editar, cancelar o eliminar reconcilia los avisos.
- La privacidad elegida continúa vigente en pantalla bloqueada.
- No se agregan permisos, servicios o polling por inferencia.

## 16. Persistencia

- Room guarda datos fuente e historia, nunca totales mensuales opacos.
- Nombre/apodo y preferencias simples permanecen en sus DataStore dueños.
- Preferencias de presentación del Resumen se guardan en DataStore.
- La implementación actual heredó tablas, esquemas y migraciones de 1.0, pero
  ya no son un requisito de compatibilidad del producto.
- Antes de ampliar Room nuevamente se define una base exclusiva de V2 y se
  retiran el origen `MIGRATED_V1`, la activación V1→V2 y las rutas de adopción
  histórica que no tengan otro uso real.
- Esa deuda no bloquea recorridos que reutilizan Room v7 sin modificar su
  esquema, como la edición y eliminación individual de jornadas V2.
- Esa limpieza puede reutilizar entidades o repositorios útiles; no exige
  empezar el código desde cero.
- Una vez fijada la primera base pública de V2, cada versión posterior sí debe
  exportar esquema, migrar explícitamente y preservar todos los datos V2.
- No se permite una limpieza silenciosa de datos desde la aplicación.

## 17. Orden aprobado de implementación

1. Regularizar documentación y Git.
2. Reglas puras de configuración, fechas y horas.
3. Configuración persistente y primera apertura V2.
4. Lugares, tipos, plantillas y carga manual.
5. Edición y eliminación individual de jornadas V2 en su fecha original.
6. Retiro del modo V1 antes de ampliar nuevamente la persistencia.
7. Planes recurrentes y edición de una fecha o de todo lo futuro.
8. Horario real, extras y avance contra la referencia.
9. Guardias pasivas y disponibilidad.
10. Calendario final y tarjeta superior desplegable.
11. Resumen personalizable.
12. Próximo evento y notificaciones.
13. Auditoría integral del núcleo, actualización QA y compatibilidad Android.

Las ampliaciones futuras de situaciones especiales y cualquier consolidación
adicional del motor se programan únicamente si Joaquin las vuelve a priorizar o
si una superficie posterior demuestra una dependencia real. No son puertas
previas al punto 10.

Cada bloque requiere un prompt acotado, pruebas proporcionales, revisión del
diff y un checkpoint local antes de avanzar. Joaquin indica cuándo preparar el
prompt o abrir la tarea nueva; al recibir su handoff, MAIN audita, integra,
prueba y crea automáticamente el checkpoint local si todo queda verde. Push,
tag, Release y producción no forman parte de esta autorización.

## 18. Compatibilidad y calidad

Se conservan inicialmente:

- `minSdk 26`;
- `targetSdk 37`;
- `compileSdk 37`;
- Java 17;
- módulos `:app`, `:core:domain` y `:core:database`.

Validación por impacto:

- JVM para dominio y ViewModels;
- instrumentación Room para la base limpia V2 y, después de fijarla, para sus
  migraciones internas;
- Compose instrumentado para recorridos visibles;
- API 26 como piso, API 33 para permisos modernos, Samsung API 36 como
  dispositivo principal y API 37 antes del candidato final;
- tema claro/oscuro, orientación y zoom interno 100 %, 150 % y 200 %;
- no leer ni modificar `font_scale`, densidad o tamaño visual del sistema;
- datos ficticios y paquete QA para pruebas físicas;
- distinguir AndroidTest compilado de instrumentación realmente ejecutada.

## 19. Fuera del núcleo inicial

- agenda de pacientes;
- fichaje o ubicación automática;
- OCR o importación automática de cronogramas;
- feriados automáticos;
- fórmulas legales o salariales;
- patrones de recurrencia arbitrarios fuera de los cuatro patrones aprobados;
- cuentas, nube o sincronización;
- widgets, informes, copias y bloqueo antes de cerrar el núcleo laboral.

## 20. Orden recomendado después del núcleo

Después del checkpoint y la auditoría del núcleo laboral, la hoja de ruta
continúa con:

1. widget basado en el motor final de próximo evento;
2. informes locales de jornadas y horas;
3. copias y restauración locales seguras;
4. bloqueo de acceso local;
5. Ayuda y recorrido inicial reescritos para la interfaz 2.0 definitiva;
6. auditoría de la aplicación completa y emisión de un candidato local.

La decisión de Joaquin del 2026-08-23 reemplaza la apertura automática de esa
cadena: MAIN recomienda el siguiente bloque, pero sólo prepara su prompt o abre
su tarea cuando Joaquin lo indica. Una vez recibido el handoff, la auditoría,
integración, validación, documentación y checkpoint local quedan a cargo de
MAIN. Este orden no autoriza servicios externos, publicación ni decisiones
funcionales todavía ausentes.

## 21. Idea futura guardada: agenda profesional

Joaquin pidió conservar para una etapa posterior la posibilidad de agregar un
apartado opcional de **Agenda profesional** para Medicina y para una eventual
incorporación de Psicología.

La recomendación de orden es terminar primero el núcleo laboral vigente y,
antes de almacenar datos de pacientes, cerrar las copias locales seguras y el
bloqueo de acceso de MiGuardia. Recién después conviene diseñar e implementar
esta ampliación.

Si se aprueba, su primer alcance debería limitarse a pacientes y turnos. No
incluiría historias clínicas, diagnósticos, tratamientos, evoluciones,
certificados ni archivos clínicos. La información continuaría siendo local y
requeriría decisiones explícitas sobre privacidad, eliminación, exportación,
copias y migraciones de la base V2.

Psicología sería un sector independiente, no una variante de Medicina o
Enfermería. Incorporarla ampliaría el catálogo actual de cuatro a cinco
sectores y, por lo tanto, necesita una decisión de producto separada antes de
modificar documentación canónica, Room, pantallas o comportamiento.

Esta sección guarda una recomendación futura. No habilita un prompt, una tarea,
un cambio de esquema ni una implementación dentro de la secuencia actual.
