# Stage 3.1 — Calendario mensual completo y acciones contextuales

Estado: EJECUTADO; candidato técnico y QA sintética listos para revisión de
MAIN y de Joaquín. No implica aceptación visual ni distribución.

## Objetivo

Corregir el Calendario principal para mostrar el mes completo desde el ingreso,
sin scroll vertical ni acciones permanentes debajo de la grilla. Un toque breve
abre el detalle; una pulsación larga nativa inicia selección múltiple. Las
acciones aparecen sólo durante la selección y las funciones desplazadas siguen
disponibles en el menú lateral existente.

Esta instrucción reemplaza las decisiones anteriores de forzar una semana por
debajo de 1050 dp, persistir una vista plegada, ofrecer `Seleccionar` en forma
permanente y reservar una barra inferior en modo normal.

## Alcance y límites

- Ajustar composición, filas del mes, celdas, menú y estado de plegado del
  Calendario.
- Conservar detalle por jornada, clima contextual, selección general, carga
  manual, feriados, operaciones masivas y árbitro único de Atrás.
- No cambiar Room, Copias, versión, `applicationId`, firma, distribución,
  website ni aplicación pública.
- No hacer commit, push, tag, firma, AAB ni publicación.

## Contrato funcional

- La vista inicial y todos los retornos muestran todas las fechas del mes.
- La grilla usa sólo cuatro, cinco o seis filas según el mes; una preferencia
  histórica de plegado no altera la presentación.
- La pantalla principal no tiene scroll vertical ni reserva lugar para
  controles ocultos.
- En modo normal no existen barra de acciones, botón `Seleccionar` ni acceso
  permanente a carga o repetición debajo de la grilla.
- `Ir a hoy`, fotos, carga, alternativa accesible de selección y repetición
  viven en el menú existente. Las acciones que requieren Calendario cargado se
  mantienen deshabilitadas hasta que sus datos estén listos.
- Un toque abre el detalle vigente. La pulsación larga usa `combinedClickable`
  nativo, selecciona ese día y no abre el detalle.
- En selección se muestran contador, Continuar y Cancelar; fuera de ella no
  existen ni reservan espacio.
- Carga manual posterior puede usar su superficie desplazable propia. El
  Calendario principal permanece estático.
- En ventanas bajas se omite la tarjeta contextual de próximo evento para
  reservar espacio operable al mes, sin sustituirlo por una semana.

## Verificación exigida

- Regresiones de meses de 28, 29, 30 y 31 días y grillas de cuatro, cinco y seis
  filas; extremos visibles; navegación mensual/anual; preferencia antigua;
  toque, pulsación larga, cancelación y retorno a mes completo.
- Regresiones de acciones contextuales, funciones trasladadas, prioridad de
  Atrás, carga manual, feriados y cancelación sin escrituras.
- Zoom interno 100/150/200, retrato, paisaje, recreación y navegación del
  sistema, sin cambiar densidad, fuente o zoom del Samsung.
- Pruebas JVM focalizadas, una validación local completa proporcional, lint,
  ensamblado QA y AndroidTest, catálogo QA, `docs_check` y `git diff --check`.
- QA física sólo sobre `com.blackatsystems.miguardia.qa`, con datos sintéticos,
  recibos y capturas; comprobar antes y después que la aplicación pública no se
  reemplazó ni instrumentó.

## Cierre

La última QA verificada debe quedar instalada y abierta en el Calendario para
revisión humana. MAIN recibe causa, delta, archivos, regresiones, recibos,
capturas, hashes, aislamiento de la aplicación pública, riesgos y matriz humana
pendiente. Stage 3 continúa sin aceptación ni autorización de distribución.
