# Planificación canónica de MiGuardia 2.0

- Estado: **cerrada y aprobada para ejecución**
- Fecha de cierre: 2026-08-21
- Propietario del producto: Joaquin
- Base: MiGuardia `v1.0.0`
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
- Se conserva `applicationId = "com.blackatsystems.miguardia"`.
- El tag `v1.0.0` no se mueve ni reescribe.
- Las migraciones desde Room v5 son explícitas y no destructivas.
- No se guardan DNI, correo, teléfono, matrícula ni domicilio personal.
- No se guardan imágenes de certificados médicos.
- No se incorporan montos, escalas, salarios, liquidaciones ni deducciones.
- No se incorporan agenda de pacientes ni datos clínicos.
- No se incorporan ubicación automática, OCR ni importación directa de Excel.

Joaquin autorizó reemplazar comportamiento heredado cuando permita una 2.0
mejor. MAIN preservará datos e historia siempre que no exista una razón técnica
concreta para cambiar esa decisión; no se destruye información por comodidad.

## 3. Una configuración con historia

- Cada usuario tiene una sola configuración laboral.
- Un cambio se aplica desde una fecha local concreta y continúa hasta el
  siguiente cambio.
- No se limita la vigencia al primer día de un mes.
- Cambiar sector, reglas, lugar, plantilla o plan nunca reinterpreta jornadas
  anteriores.
- Al cambiar de sector, los lugares anteriores dejan de ofrecerse para nuevas
  cargas, pero conservan todo su historial.
- Una instalación nueva no recibe automáticamente 204 horas ni una franja
  nocturna.
- Un usuario actualizado desde 1.0 conserva la semántica histórica de
  Vigilancia hasta activar conscientemente V2 desde una fecha.

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

### Actualización desde 1.0

- No se fuerza una elección inicial que bloquee el Calendario.
- El historial y los cálculos anteriores permanecen comprensibles.
- La activación de V2 es una acción consciente y fechada.

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
- `Objective`, `ScheduleCombination` y `Shift` se conservan como base histórica
  cuando sea seguro; V2 agrega relaciones en lugar de reinterpretar columnas.
- Toda versión de Room posee esquema exportado, migración explícita y prueba de
  actualización con las trece familias heredadas.
- No se permite `fallbackToDestructiveMigration`.

## 17. Orden aprobado de implementación

1. Regularizar documentación y Git.
2. Reglas puras de configuración, fechas y horas.
3. Configuración persistente y migración Room v5→v6.
4. Primera apertura, lugares, tipos, plantillas, carga manual y activación
   consciente desde una instalación anterior.
5. Planes recurrentes y edición puntual/masiva.
6. Horario real, extras y avance contra la referencia.
7. Disponibilidad, situaciones especiales y consolidación final del motor de
   horas y cumplimiento.
8. Calendario final y tarjeta superior desplegable.
9. Resumen personalizable.
10. Próximo evento y notificaciones.
11. Auditoría global, actualización QA y compatibilidad Android.

Cada bloque requiere un prompt acotado, pruebas proporcionales, revisión del
diff y un checkpoint local antes de avanzar. Push, tag, Release y producción no
forman parte de esta autorización.

## 18. Compatibilidad y calidad

Se conservan inicialmente:

- `minSdk 26`;
- `targetSdk 37`;
- `compileSdk 37`;
- Java 17;
- módulos `:app`, `:core:domain` y `:core:database`.

Validación por impacto:

- JVM para dominio y ViewModels;
- instrumentación Room para migraciones;
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
- patrones de recurrencia arbitrarios fuera de los tres modelos aprobados;
- cuentas, nube o sincronización;
- widgets, informes, copias y bloqueo antes de cerrar el núcleo laboral.

## 20. Continuidad después del núcleo

La orden expresa de Joaquin del 2026-08-22 autoriza que, después del checkpoint
y la auditoría del núcleo laboral, MAIN continúe de a un bloque con:

1. widget basado en el motor final de próximo evento;
2. informes locales de jornadas y horas;
3. copias y restauración locales seguras;
4. bloqueo de acceso local;
5. Ayuda y recorrido inicial reescritos para la interfaz 2.0 definitiva;
6. auditoría de la aplicación completa y emisión de un candidato local.

Esta continuidad no autoriza servicios externos, publicación ni decisiones
funcionales todavía ausentes. Cada superficie necesita su contrato acotado y se
detiene ante una decisión material abierta.
