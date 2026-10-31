# Stage 4 — Fluidez e integridad de MiGuardia 3.0

Contrato preparado para decisión de Joaquín. No es una orden de ejecución por
el solo hecho de abrirlo. Consultar `docs/STATUS.md` y la autorización actual.

## TASK

Conseguir que los recorridos existentes de MiGuardia sean predecibles, legibles,
amigables y coherentes, sin perder datos ni funciones. Resolver la unidad completa:
causa, navegación, estado, composición, mensajes, rendimiento medido, regresiones,
consumidores y evidencia. No una colección de cambios cosméticos ni una reescritura.

Cerrar los hallazgos F01–F07 e investigar R01–R04 de
`docs/audits/2026-10-02-overall-fluidez-integridad.md`. Corregir riesgos únicamente
cuando se demuestre su causa; si no se reproducen, registrar alcance y limitaciones
de la prueba. No afirmar que todo funciona porque compila o pasa JVM.

## CONTEXT

Stage 3.2 es un candidato técnico probado por Joaquín, no aceptación global de
Stage 3. Conserva Calendario mensual completo, acciones contextuales, carga
unificada y borrador persistente al cambiar selección. Auditoría MAIN fresca:
900/900 JVM, navegación física parcial y discrepancias de UX. No hay pérdida de
datos demostrada; rendimiento y matriz física completa siguen sin certificar.

La app es local-first; producto público 2.0.2 separado del candidato QA. Preservar
Room V8, Copias V5–V8, identidad, historial y privacidad. Sin rediseñar las reglas
laborales ni añadir funcionalidades fuera del alcance existente.

## INPUTS

- Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`.
- Rama esperada `codex/miguardia-3.0`, HEAD de la auditoría
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`, más checkout acumulado sin staging.
  No usar ese SHA solo como si incluyera Stage 3.2. No limpiar ni sustituir el árbol.
- Leer en orden AGENTS del contenedor/repositorio, STATUS, LECTURAS_POR_TAREA,
  WORKFLOW, VALIDACION, esta auditoría, este contrato y el contrato Stage 3.2.
  Leer ADR/código/tests de cada superficie según ese mapa, no todo el archivo histórico.
- Evidencia local inicial y manifiesto Stage 3.2 referidos por la auditoría;
  contrastar hashes y recepción antes de reutilizarlos. No reasignar recibos viejos
  a un nuevo APK. MiGuardia-2.0 e históricos permanecen de sólo lectura.

Puerta 0: verificar ruta, rama/HEAD/upstream/remotos/worktrees, `.git` independiente,
staging/modificados/no rastreados, escritor único y ausencia de secretos/artefactos
rastreables. Separar delta recibido/propio mediante hashes o patch local. Detener
escritura ante diferencia material sin procedencia, sin descartar nada.

## OUTPUT

1. Candidato integrado con F01–F07 resueltos y R01–R04 cerrados por evidencia o
   explícitamente pendientes con causa y siguiente prueba, nunca disfrazados de verde.
2. Matriz de recorridos completa: entrada, acción principal, alternativa, Atrás,
   cancelar, error, datos conservados y evidencia por superficie.
3. Comparación antes/después de navegación, composición y rendimiento, vinculada
   al código/APK realmente probado; fallos adversos preservados.
4. Auditoría propia, STATUS actualizado y handoff compacto a MAIN, con delta,
   resultados, riesgos y puertas humanas. QA instalada sólo si existe autorización
   aplicable; sin presentarla como versión pública o aceptada.

## SCOPE

### Navegación y estado

- F02: restaurar origen real al volver desde Horas, disponibilidad, repetición y
  tipos de extra abiertos desde configuración. Conservar borrador/contexto y no
  crear ciclos de regreso. Probar también accesos directos sin padre.
- Mantener un único dueño efectivo de Atrás. Diálogo → formulario → selección
  debe seguir las decisiones vigentes, sin reentradas ni consumidores dobles.
- Navegar, abrir detalles y expandir tarjetas no escribe datos laborales.
  Cancelar descarta sólo borrador confirmado para descarte, no catálogo guardado
  expresamente. Error y doble toque no duplican jornadas/notas/archivos.
- F01: actualizar ayuda, recorridos/anclas y nombres conforme a controles reales.
  No conservar una instrucción obsoleta para que una prueba siga pasando.

### Diseño y lenguaje

- Conservar identidad visual con superficies sobrias, color de acento medido,
  separación consistente, jerarquía clara y una acción principal por contexto.
  Evitar grandes bloques violetas y varias acciones de idéntico peso. Reutilizar
  componentes/tema existentes; no introducir librería o sistema de diseño nuevo.
- F04: en Resumen, controles mensuales compactos y cifras antes que exportación
  e introducciones extensas. Personalización y trazabilidad siguen accesibles;
  explicar conceptos en lenguaje cotidiano sin modificar cálculos.
- F05: Clima muestra estado, explicación esencial de privacidad y acción consciente
  sin enterrarlos debajo de información extensa. Atribución, términos y detalle
  siguen accesibles; permisos/red conservan las puertas vigentes.
- F06/F07: distinguir agregar de editar/contraer cuando corresponde, reducir
  instrucciones redundantes y homogeneizar patrones de cierre según su significado.
  No hacer reemplazos globales ciegos de Volver/Cerrar ni quitar funciones útiles.
- Recorrer menú y todas sus superficies: Calendario, detalle, carga/catálogo,
  configuración, Horas/extras, Retén, recurrencia, situaciones, feriados/vacaciones,
  Resumen/informes, Notas/Fotos, Avisos, Clima, Widget, Copias, Bloqueo, Apariencia,
  Ayuda y accesos de soporte/actualización. Lo externo puede verificarse con dobles
  o marcado pendiente, sin accionar producción para completar una casilla.
- R01: mes completo de cuatro/cinco/seis filas, sin scroll vertical principal,
  semana forzada ni controles inferiores permanentes. Datos densos deben resumirse
  sin ocultar fechas ni convertir texto en ilegible; el detalle conserva el contenido.
  No reducir objetivos táctiles como atajo. Si una ventana no admite los invariantes,
  presentar la restricción real a MAIN, no decidir silenciosamente otro comportamiento.
- Legibilidad y jerarquía en claro/oscuro, zoom interno 100/150/200, retrato/paisaje,
  teclado y navegación del sistema. No consultar ni cambiar font_scale o tamaño
  de pantalla de Android. TalkBack debe anunciar nombre, rol, estado y orden lógico;
  color nunca es el único identificador. Etiquetas largas pueden envolver texto.

### Imágenes e integridad

- F03: unificar carga de imágenes de detalle y formulario. Mantener tope interno
  de 30 sin anunciar `/30` en el uso normal. Recomendación: rechazar un lote que
  excede capacidad antes de importarlo, explicar cómo ajustar la selección y
  conservar todo lo previo. No truncar silenciosamente ni deshabilitar sin explicación.
- Miniaturas privadas ordenadas, selección identificable y eliminación accesible.
  Manejar cancelación, error parcial de lectura, archivos grandes y falta de espacio
  con limpieza de temporales propios; nunca borrar referencias confirmadas.
- Preservar el contrato Stage 3.2: encabezado y fechas expandibles, catálogo
  integrado con creación/horarios, Franco, puesto, retorno a selección conservando
  institución/horario/puesto/nota/imágenes, guardar, limpiar y cancelar.
- Mantener Sobrescribir por intervalo, Superponer consciente, avisos de descanso
  menor a 12 h y superposición no bloqueantes, CAS y atomicidad de todo el lote.
  Franco puede coexistir con arrastre nocturno del día anterior. COMPLETED derivado.
- Revalidar repercusiones en horas, Resumen, próximo evento, avisos, clima,
  recurrencia, informes y Copias cuando un cambio afecte fuentes compartidas.
- R03/R04: reproducir límites de colecciones SQL y concurrencia archivos/restore
  con datos ficticios y barreras deterministas. No quitar validaciones ni ampliar
  transacciones por intuición. Si hace falta migración/formato o nueva política
  material de datos, detener esa parte y elevar decisión a MAIN.

### Fluidez medible

- R02: obtener baseline y comparación con el mismo dispositivo/variante/dataset.
  Separar arranque frío/caliente, abrir detalle, cambiar mes, entrar/volver de carga,
  mostrar catálogo, previsualizar/guardar lote y abrir Resumen.
- Usar historia sintética representativa (por ejemplo un año con turnos mixtos)
  y otra de estrés identificada por separado. Registrar cantidad de jornadas,
  notas/adjuntos, repeticiones, latencia p50/p95 y frames lentos donde sea medible.
  Elegir umbrales justificables desde baseline y documentarlos antes de optimizar;
  no inventar métricas ni afirmar 60 fps basándose en capturas.
- Corregir cuellos de botella demostrados, preservando consistencia y lectura
  fresca. Mantener feedback de operación y prevención de doble guardado, sin
  animaciones que escondan espera. No retirar validación global sólo para acelerar.

## DEPENDENCIES

Autorización de implementación de Joaquín; entrada Stage 3.2 inspeccionada.
Orden interno: inventario/baseline → contrato común de navegación/estado → cambios
cohesivos UI e integridad → consumidores/regresiones → QA comparativa → revisión MAIN.
No convertir cada botón en una dependencia nueva ni ejecutar frentes que inventen
contratos distintos sobre el mismo estado.

MAIN o un integrador único posee MiGuardiaApp, MainActivity, estado del Calendario,
tema/componentes compartidos y STATUS. Revisores independientes pueden auditar
persistencia, lenguaje o pruebas en sólo lectura. Si se delega escritura, fijar
archivos disjuntos y orden de integración; ningún archivo con dos escritores.
No crear tareas visibles, ramas ni worktrees por inferencia.

## DO NOT

No reescritura, nueva dependencia productiva, migración Room, cambio de Copias,
versionado/applicationId/firma, cuenta/nube/telemetría, datos personales, permisos
silenciosos ni eliminación de funciones. No tocar app pública ni datos reales.
No desplazar el diseño hacia semana forzada, scroll mensual o barra permanente.
No relajar aserciones, agregar esperas arbitrarias ni confundir un test defectuoso
con producto: demostrar causa antes/después. No commit/push/tag/firma/publicación
sin instrucción específica para este cierre. No reabrir Play Store/website.

Dispositivo: comprobar autorización y disponibilidad actuales. Usar sólo QA/datos
ficticios. Si cambia el foco, detener acciones; evitar capturas de otras apps.
No consultar datos personales, ajustes globales ni notificaciones del usuario.
La autorización histórica no sustituye disponibilidad ni permiso para nuevas
acciones sensibles (biometría, compartir, restaurar, instalar o permisos externos).

## VALIDATION

- Prueba adversa para cada fallo funcional, regresión causal y casos equivalentes.
  Para hallazgos visuales: captura y criterio concreto antes/después, no sólo conteos.
- Una/varias/sin fechas, meses 28/29/30/31 días, cuatro/cinco/seis filas, nombres
  largos, sin/con catálogo, jornada nocturna, dos jornadas, Franco, Retén, carpeta
  médica/vacaciones; recreación y rotación en cada transición con borrador.
- Guardado/cancelación/doble toque/conflicto concurrente/error: comparar snapshots
  y referencias a archivos. Incluir 0/1/29/30 imágenes y selección excedente.
- Pruebas locales completas y build según VALIDACION; instrumentación pertinente
  por riesgo. Catálogo actualizado por cambio real, no reatribuir pruebas anteriores.
- Matriz física con cada fila EJECUTADA/PENDIENTE/FALLÓ, identidad de APK/runner,
  datos sintéticos y evidencia. TalkBack, selector OEM y percepción humana no se
  dan por aprobados por compilar. Repetir sólo superficies afectadas tras cada cambio.
- `python tools/docs_check.py`, `python tools/qa.py --check-catalog`,
  `git diff --check`, revisión completa del delta propio y ausencia de secretos,
  datos privados/APK rastreados. Insumos estables durante la validación final.

## DONE WHEN

F01–F07 resueltos con evidencia; R01–R04 investigados y conclusión reproducible;
recorridos preservan contexto/datos, jerarquía visible y acciones comprensibles;
rendimiento comparado sin regresiones materiales inexplicadas; pruebas y límites
atribuibles al candidato final. MAIN recibe delta/handoff y Joaquín puede revisar
la QA autorizada. Si falta una comprobación física necesaria, entregar candidato
con cierre parcial explícito, no declarar fluidez integral ni aceptación global.

La aceptación humana y distribución son puertas separadas. Este contrato por sí
solo no acepta Stage 3 ni habilita publicación.
