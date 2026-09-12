# Sector — Policía

- Estado: una respuesta anónima incorporada; configuración personalizable confirmada
- Fuente incorporada: `policia.xlsx`
- Tamaño de la muestra: una respuesta

## Evidencia observada en la respuesta

- La persona llamó `Guardia` al horario normal y `Servicio` a trabajos
  adicionales o recargos.
- Mencionó varias denominaciones internas para servicios adicionales, pero una
  sola respuesta no permite convertirlas en un catálogo universal.
- Identificó el tipo de servicio como un dato importante.
- Confirmó que puede haber más de un servicio en un mismo día.
- Indicó que los estados posibles de una fecha dependen del caso y que podrían
  coexistir varios.
- Al abrir la aplicación necesita saber si tiene servicio ese día y en qué
  horario.
- En un aviso le resultaría útil ver si tiene un adicional ese día o el
  siguiente y dónde se realiza.
- Consideró que la organización básica de una aplicación para vigiladores se
  aproxima a su trabajo. Esto respalda reutilizar el Calendario, pero no prueba
  que las reglas de horas sean iguales.

## Decisiones confirmadas por Joaquin

- Policía es un sector independiente y utiliza la misma base técnica de
  MiGuardia, sin copiar automáticamente las reglas de Vigilancia privada.
- La interfaz puede usar `Dependencia` o `Lugar de servicio` para el lugar de
  trabajo, según los datos que la persona cargue.
- El trabajo normal y los servicios adicionales son clases diferenciadas. Ambos
  suman al total trabajado y los adicionales se muestran además por separado.
- Cada clase adicional decide si también ayuda a completar las horas requeridas.
- Toda hora extra, extensión de turno o servicio extra guarda inicio y fin
  exactos. Una jornada agregada no se vuelve extra automáticamente.
- La referencia de horas puede ser conocida, existir pero ser desconocida o no
  utilizarse. Su período puede ser mensual, semanal o un ciclo personalizado.
- Puede haber varias jornadas en una fecha; el Calendario muestra la cantidad y
  el detalle las presenta ordenadas por horario.
- La disponibilidad pasiva es opcional y se configura sólo si esa persona la
  utiliza. No se deduce por pertenecer a Policía.
- El Resumen debe mostrar el total trabajado, el desglose de adicionales y el
  cumplimiento de la referencia únicamente cuando corresponda.

## Consecuencias para el producto

- El formulario inicial no ofrece una lista rígida de códigos o clases de
  servicio basada en una única respuesta.
- La persona puede crear nombres propios para sus clases habituales y
  adicionales.
- Próximo evento y notificaciones reutilizan el motor común, mostrando horario,
  tipo y lugar cuando la privacidad elegida lo permite.
- Los estados de un día se registran por caso y no se fijan como una combinación
  obligatoria para todo el sector.

## No inferir

- una referencia universal de 200, 204 u otra cantidad de horas;
- que `Guardia`, `Servicio`, `Adicional` o `Recargo` signifiquen lo mismo para
  todo el personal policial;
- que toda jornada fuera del cronograma sea extra;
- que toda persona realice disponibilidad pasiva;
- una fórmula salarial o legal a partir de una sola respuesta.
