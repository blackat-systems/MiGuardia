# Reglas de producto — MiGuardia 2.0

- Estado: decisiones de producto vigentes; no es una orden de implementación.
- Producto: Joaquin. Base de código V1 conservada; runtime V2.
- Estado operativo y evidencia: exclusivamente `docs/STATUS.md`.
- Ejecución actual: `docs/PLAN_MAESTRO_CIERRE_3_0.md` y cursor de STATUS. La ruta histórica 2.0 se conserva en el archivo.
- Las secciones 14 y 21 distinguen diseños futuros no habilitados.

## 1. Objetivo

MiGuardia 2.0 organiza jornadas y horas de Vigilancia privada, Policía,
Enfermería y Medicina dentro de una sola aplicación Android local. No existen
cuatro aplicaciones, perfiles laborales simultáneos ni una opción `Otro`.

El diseño es personalizable porque dos personas del mismo sector pueden trabajar
de maneras completamente distintas. El sector adapta palabras y ejemplos; las
reglas reales las configura cada usuario.

## 2. Límites permanentes

- Android es la plataforma inicial.
- Los datos laborales permanecen locales, sin cuenta laboral, nube laboral,
  sincronización, analítica ni telemetría. La distribución y las actualizaciones
  públicas conscientes y el feedback voluntario siguen §22 y ADR 0041, sin
  acceso beta, cupos ni feedback obligatorio.
- Se mantiene por ahora `applicationId = "com.blackatsystems.miguardia"`, sin
  convertirlo en una promesa de migración desde 1.0.
- El tag `v1.0.0` no se mueve ni reescribe.
- MiGuardia 1.0 fue una prueba interna sin usuarios externos: no se migran sus
  datos, preferencias, permisos, alarmas ni archivos a 2.0.
- No se guardan DNI, correo, teléfono, matrícula ni domicilio personal.
- No se guardan imágenes de certificados médicos.
- No se incorporan montos, escalas, salarios, liquidaciones ni deducciones.
- No se incorporan agenda de pacientes ni datos clínicos.
- No se incorporan seguimiento automático ni ubicación en segundo plano. Por
  decisión explícita del 2026-09-02, cada objetivo puede guardar una ubicación
  aproximada mediante una captura puntual iniciada por la persona o convertir
  conscientemente su dirección con el servicio de Android, únicamente para
  consultar su clima. La conversión no usa un mapa embebido y su resultado se
  confirma antes de guardarse. Si no existe dirección, la acción usa la
  ubicación aproximada de la ciudad actual sólo después del toque y del permiso;
  nunca se activa sola. Tampoco se incorporan OCR ni importación directa
  de Excel.

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

### Francos explícitos

Corrección autorizada por Joaquin, 2026-09-10: el Calendario permite marcar o
quitar `Franco` en una o varias fechas del mismo mes, con confirmación expresa.
No exige objetivo ni horario. Quitar borra únicamente la marca `F`, nunca una
jornada ni otra clase de estado. Marcar no reemplaza trabajo existente: ante
conflicto se informa y no se aplica parcialmente la selección. La consulta de
un día no escribe; su acción abre la selección/confirmación del Calendario.
El conflicto se define por fecha de inicio: terminar a las 07:00 una jornada
del día anterior no impide Franco. Se conserva todo el intervalo y sus horas.

### Crear desde la carga

Decisión de Joaquin, 2026-09-07. Al cargar una jornada o varios días, el selector
de lugares y horarios ofrece también crear un lugar y crear un horario. Usa el
vocabulario del sector y reutiliza los formularios de configuración existentes.

El recorrido de lugar nuevo continúa hacia su horario. Al guardar un horario,
se vuelve a la carga original con las fechas y el borrador conservados, se
actualizan las opciones y se selecciona el horario por su identidad exacta si
es aplicable a todos los días. Crear catálogo no guarda jornadas: éstas siguen
requiriendo la confirmación de carga y sus controles vigentes.

Cancelar conserva la carga previa. Un lugar ya guardado no se borra al cancelar
su horario; se explica qué quedó guardado. No se cambia silenciosamente la
vigencia histórica, no se omiten días incompatibles y no se reescribe el pasado.
La implementación y aceptación viven sólo en STATUS; contrato del recorrido:
`docs/prompts/CREAR_DESDE_LA_CARGA_V2.md`.

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

> Diseño futuro no habilitado: V2 conserva `F/?`, carpeta médica, vacaciones,
> feriados, notas y los estados internos `ABSENT`/`CANCELLED`. El recorrido
> estructural de Novedades V1 fue retirado. Las ampliaciones de esta sección
> requieren una decisión de producto; no son requisitos pendientes del núcleo.


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

## 15.1 Widget de pantalla de inicio

- Reutiliza `projectNextEvent`; no crea otra prioridad ni fórmula laboral.
- Cada instancia elige **Próxima jornada**, **Próximo franco** o
  **Automático**.
- Próximo franco significa únicamente un `F` explícito; un día vacío o `?` no
  se interpreta como descanso.
- Automático puede mostrar una jornada o un tramo efectivo de disponibilidad y
  conserva la etiqueta histórica de esta última.
- Automático consume todos los `primaryEvents` del motor sin volver a
  filtrarlos por inicio, tipo o prioridad; Próxima jornada agrupa sólo las
  jornadas del primer inicio futuro.
- Se permiten varios widgets simultáneos, cada uno con modo, privacidad y
  Clima opcional propios.
- Existen tamaños compacto y ampliado; tocar abre el día correspondiente o el
  Calendario actual cuando no hay evento.
- La privacidad por instancia puede ser completa, reducida u oculta. Nunca se
  muestran dirección, notas, motivos médicos, fotos ni datos personales.
- Clima puede aparecer sólo en el tamaño ampliado, con privacidad completa,
  habilitación consciente y caché válida; nunca bloquea el evento laboral y su
  atribución abre la superficie Clima que contiene el enlace al proveedor.
- Se implementa con `AppWidgetProvider` y `RemoteViews`, sin dependencia nueva,
  polling, servicio permanente, permiso adicional ni cambio de Room.
- El contador usa el cronómetro nativo y una frontera inexacta reconstruible;
  el Widget funciona aunque Notificaciones estén apagadas.
- El tema sigue la preferencia global de MiGuardia; no existe una elección de
  tema por instancia.
- `Widget de inicio` aparece en `Avisos y contexto` únicamente cuando la
  función existe y permite explicar, listar y reconfigurar instancias.
- Mapas y `Cómo llegar` quedan fuera de este primer alcance.

## 16. Persistencia

- Room guarda datos fuente e historia, nunca totales mensuales opacos.
- Nombre/apodo y preferencias simples permanecen en sus DataStore dueños.
- Preferencias de presentación del Resumen se guardan en DataStore.
- La base activa es `MiGuardiaV2Database`, archivo `miguardia-v2.db`, versión 6
  y veintisiete tablas, con migraciones explícitas `1→2→3→4→5→6`.
- La migración `5→6` agrega sólo coordenadas meteorológicas opcionales por
  objetivo. Las copias lógicas V5 continúan admitidas y se leen con esas
  coordenadas nulas.
- No existe activación, adopción o migración de datos desde 1.0. El archivo
  histórico `miguardia.db` no se abre, transforma ni borra.
- Cada versión posterior debe exportar esquema, migrar desde la versión V2
  inmediata anterior y preservar todos los datos V2.
- No se permite una limpieza silenciosa de datos desde la aplicación.
- La primera copia V2 es manual, completa, lógica y versionada; no copia SQLite
  ni DataStore en bruto y no hereda el recorte mensual de V1.
- La copia puede incluir fotos conscientemente y recomienda contraseña. El modo
  cifrado usa APIs JCA disponibles desde API 26; no agrega servicio, cuenta,
  nube ni sincronización.
- Antes de restaurar se valida una vista previa sin escrituras. El usuario elige
  `Combinar con mis datos` o `Reemplazar todo`.
- Combinar conserva lo actual por defecto, omite duplicados exactos y exige
  resolver conflictos. Sólo combina un destino vacío o la misma `timelineId`;
  dos configuraciones laborales independientes no se mezclan.
- Reemplazar todo muestra qué desaparecerá y exige una segunda confirmación.
- La restauración coordina Room, preferencias y fotos con candidato, rollback y
  journal durable; un fallo o muerte de proceso deja íntegramente el estado
  anterior o el nuevo.

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
- fichaje, seguimiento de ubicación o ubicación en segundo plano; la captura
  puntual y consciente de la ubicación de un objetivo para su clima sí está
  admitida;
- OCR o importación automática de cronogramas;
- feriados automáticos;
- fórmulas legales o salariales;
- patrones de recurrencia arbitrarios fuera de los cuatro patrones aprobados;
- cuentas, nube o sincronización;
- widgets, informes, copias y bloqueo antes de cerrar el núcleo laboral.

## 21. Idea futura guardada: agenda profesional

Joaquin pidió conservar para una etapa posterior la posibilidad de agregar un
apartado opcional de **Agenda profesional** para Medicina y para una eventual
incorporación de Psicología.

Esta ampliación sólo puede activarse por una decisión explícita de Joaquin.

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

## 22. Distribución pública desde la web

Decisión confirmada por Joaquin el 2026-09-08: MiGuardia se ofrece desde su
página web, sin activación, claves de acceso, cuenta, diez plazas ni opinión
obligatoria a quince días. Esta decisión reemplaza el piloto privado del
2026-09-03; su implementación histórica no desaparece automáticamente.
ADR 0041 define la transición y STATUS distingue decisión, código y distribución.

El recorrido será presentación bilingüe en `/miguardia` → descarga oficial al
final → instalación → recuperación/Bloqueo/configuración/Ayuda según corresponda
→ uso local. No se obliga a demostrar lectura de la página ni a identificarse.
Google Play queda fuera de esta primera entrega, sin prohibir evaluarlo después.

### Funciones conservadas y transición

- Todas las funciones laborales, privacidad, Copias, bloqueo y Ayuda se conservan.
- `Apoyar MiGuardia` permanece visible, externo y voluntario, sin ventajas.
- Opiniones e incidencias voluntarias, iniciadas y confirmadas por la persona,
  sin datos laborales adjuntos automáticamente. No se condiciona uso ni actualización.
- Quitar el acceso beta requiere adaptar raíz, runtimes, Widget, avisos,
  configuración exportada y actualizador, no fabricar autorizaciones ni ocultar
  únicamente una pantalla.
- La descarga pública no depende de reservas, cookies de acceso, comprobante de
  instalación ni feedback recibido. Contrato de metadatos y distribución
  interoperable acordado entre Android/web antes de cambiar sus consumidores.
- Reutilizar verificación de APK, transferencia acotada, cancelación, SAF y
  apertura manual desde Archivos del cliente 28. No instalar automáticamente,
  permitir downgrade ni eliminar datos para simular actualización.
- Los estados beta residuales no pueden impedir el uso. No enviar o borrar
  silenciosamente feedback previo, identidades o datos remotos; detener los
  reintentos del protocolo retirado y documentar su transición.

### Identidad y madurez

Se conserva `applicationId = com.blackatsystems.miguardia`, la clave Release
permanente existente y un versionCode creciente. No regenerar firma ni borrar
datos V2. La meta es `2.0.0`; retirar la restricción beta no obliga a `3.0.0`
ni acredita por sí solo una versión estable. Nombre y código finales se fijan
al congelar una candidata comprobada, contrastando entregas e instalaciones.
Mientras tanto los valores efectivos siguen en Gradle/STATUS, sin cambiarlos
sólo por registrar esta decisión.

### Paquete de entrega y seguridad

El plan maestro 3.0 conserva el cierre por capacidades: Stage 12.1 Trabajo y Calendario, 12.2 Cálculos y consulta, 13 Datos y archivos, 14 Sistema y aceptación integral, 15 Entrega y upgrade público. Los hitos públicos mantienen revisión de seguridad, aceptación/firma, instalación y upgrade con datos poblados, publicación exacta y seguimiento voluntario.

La revisión de seguridad cubre Android, web, archivos, dependencias/historial,
claves y distribución. Aplicar controles pertinentes de MASVS/MASTG y ASVS;
no inventar login, clave pública de Room o RLS remoto para una base local.
Comprobar cifrado y recuperación según amenaza: bloqueo de pantalla, sandbox,
protección del sistema y copias cifradas no acreditan cifrado adicional de Room.

No publicar con fallos abiertos de pérdida de datos, exposición sensible,
autenticidad de APK, instalación/actualización o bloqueo principal. Clasificar
otros residuales y aceptar explícitamente sus límites. No prometer que una APK
es invulnerable, no redistribuible o libre de todo exploit.

Antes de publicar deben quedar definidos privacidad, contacto público de soporte,
mecanismo/campos/retención de feedback voluntario y requisitos externos de
distribución Android pertinentes. Ya no hay fecha de inscripción ni cupo por
resolver. La página informativa, el feedback y el aporte no deben introducir
rastreo o nube laboral. Revisar infraestructura real antes de operar: no borrar
ni provisionar recursos beta simplemente porque cambió el modelo.

Secretos, APK y firmas fuera de Git. Firma, recursos remotos, dispositivo,
publicación, push y tag conservan sus autorizaciones específicas; el cambio
de producto no autoriza limpieza destructiva, nuevos costos o envío de datos.
Estado de implementación, evidencia y permisos: `docs/STATUS.md`.
