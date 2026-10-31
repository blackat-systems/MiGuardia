# Stage 3.2 — Carga ordenada y selección sin pérdida del borrador

## TASK / CONTEXT

Decisión de Joaquín del 2026-10-02. Reorganizar la carga existente de uno o varios
días, incluyendo UI, navegación, conservación/restauración del borrador, pruebas,
documentación y QA sintética. No es una aceptación de Stage 3 ni distribución.
Repositorio MiGuardiaContainer/MiGuardia-3.0, rama codex/miguardia-3.0, base
informada 932eaeb60c6b6e68975534788e3e6501c967b1ae. Preservar checkout acumulado;
MiGuardia-2.0 sólo lectura. Evidencia anterior no acredita este comportamiento.

## INPUTS / PUERTA 0

Leer AGENTS del contenedor y repo, STATUS, LECTURAS_POR_TAREA, handoff Stage 3,
contrato de cierre próxima jornada/carga unificada y auditoría pertinente.
Confirmar ruta, rama, HEAD, upstream, remotos, staging, modificados/no rastreados
y escritor único. Separar delta recibido/propio. Preservar y aclarar diferencias
materiales inexplicadas antes de escribir sobre ellas.
Inspeccionar UnifiedManualLoadForm, coordinador, selección, creación contextual,
puesto, notas/referencias temporales y garantías existentes.

## CONTRATO VISUAL Y FUNCIONAL

Orden exacto, de arriba hacia abajo:

1. Encabezado único: «Cargando jornada para los días:». Reemplaza «Cargando
   jornadas» y «Elegí un lugar, tipo y horario», sin instrucción redundante.
2. Fechas cronológicas, sin duplicados, hasta tres DD/MM/YY separadas por « - ».
   Ejemplo: 02/10/26 - 03/10/26 - 04/10/26. Más de tres: «Mostrar todos» despliega
   todas verticalmente con día de semana (Viernes 02/10/26, Sábado 03/10/26…);
   «Mostrar menos» contrae. Una/tres no necesitan control. Expandir no cambia
   selección; en ancho pequeño/zoom alto permitir líneas sin recortar ni solapar.
3. «Mostrar instituciones o servicios» despliega tarjeta integrada, no diálogo.
   Primero «Crear institución o servicio», separado de catálogo alfabético;
   identificadores/colores útiles, nunca códigos técnicos. Elegir institución
   muestra sus horarios; elegir uno identifica ambos. «Agregar horario» siempre
   disponible con o sin horarios, asociado a la institución correcta. Reutilizar
   creación de institución→primer horario y sus validaciones. Conservar fechas y
   borrador, incluso al cancelar; nunca elegir otro horario silenciosamente.
   El catálogo puede desplazarse dentro del formulario; no habilita scroll mensual.
4. «Marcar como franco», aplicado a todos los días seleccionados mediante preview
   y conflictos existentes, sin éxito parcial silencioso. No bloquear por una
   jornada del día anterior que termina a las 07:00.
5. «Agregar puesto o función» abre editor identificado; ingresar, editar, quitar.
   Al contraer mostrar valor cargado. Opcional, reutiliza campo de jornada y
   alcanza todas las fechas de la carga. Se guarda y conserva en edición posterior;
   no modifica institución ni horario del catálogo.
6. «Volver a la selección de días» vuelve al Calendario dentro del MISMO flujo,
   con fechas actuales seleccionadas; permite agregar/quitar y continuar al mismo
   formulario. Conserva institución, horario, puesto, nota, adjuntos y demás
   valores. No crea jornadas/notas persistidas. Selección vacía impide continuar,
   no borra borrador. Cambiar fechas invalida previews/resoluciones anteriores.
   Si se eliminó/cambió institución u horario, informar y exigir selección válida,
   sin reutilizar datos obsoletos ni sustituirlos automáticamente.
7. Después: nota opcional texto/imágenes, Guardar jornada(s), Limpiar día(s),
   Cancelar carga al final. Guardar es principal, Limpiar no. Conservar accesos
   necesarios sin controles duplicados de institución, horario ni puesto.

Un día/varios comparten formulario/coordinador. Navegación interna conserva
borrador; cancelar descarta sólo lo no confirmado y termina selección. Catálogo
guardado expresamente permanece. Restaurar al recrear/rotar mediante estado y
referencias a archivos temporales privados, nunca imágenes binarias en navegación.
Conservar arbitraje GENERAL/MANUAL_LOAD/HOLIDAY, Sobrescribir/Superponer, pérdidas,
advertencias de descanso/solapamiento, CAS, atomicidad, notas privadas, Retén,
carpeta médica y demás funciones. No recuperar semana forzada ni barra permanente.

## SCOPE / DEPENDENCIES / DO NOT

Sólo formulario/fechas/catálogo/creación/puesto/retorno, conservación-validación
del borrador y pruebas/docs afectadas. Reutilizar modelos/repositorios/operaciones.
Sin migración Room ni Copias; elevar antes cualquier contrato material nuevo.
No reiniciar al elegir fechas, escribir al navegar, conservar decisiones antiguas,
duplicar motores/campos, rediseñar otros módulos ni tocar Próxima jornada salvo
regresión demostrada. No cambiar versión, applicationId, firma, distribución,
app pública/datos personales, fuente/densidad/zoom globales. Sin commit/push/tag
ni publicación.

## VALIDATION / QA FÍSICA

- Fechas una/tres/más, orden, unicidad, expansión, día de semana correcto.
- Catálogo vacío/con registros, orden alfabético, sin horarios, agregar a lugar
  con horarios, selección/cambio/cancelación, creación sin perder borrador.
- Puesto agregar/editar/quitar, guardado uno/varios, catálogo intacto.
- Elegir catálogo/puesto/nota, volver, agregar/quitar/vaciar fechas, continuar;
  preservar borrador, recalcular conflictos, cancelar sin escrituras, recrear
  y confirmar sólo las fechas finales sin duplicados.
- Preservar regresiones críticas de Atrás, feriados, notas y operaciones atómicas.
- Pruebas, compilación, lint proporcionales; catálogo QA, docs_check y diff --check.

Autorización existente cubre Samsung QA/datos ficticios. Si está disponible:
verificar dispositivo/paquete/APK/aislamiento público antes de instalar; usar
runner oficial y recibos, uno/varios, fechas/catálogo, agregar horario/puesto,
regreso conservado, teclado/Atrás/recreación/zoom interno; guardar capturas y
dejar última QA verificada instalada. Si no está disponible, terminar local y
declarar QA física pendiente. No confundir automatización con aceptación visual.

## OUTPUT / DONE WHEN

Actualizar índice, STATUS y handoff según convenciones; evidencia actual separada
de antecedentes. Entregar causa/cambios, archivos propios, pruebas/resultados,
capturas, identidad instalada si corresponde, límites y recorrido humano.
Termina con orden correcto, fechas/catálogo/horarios/puesto operables, borrador
completo conservado al elegir días, guardado sólo de selección final, cancelación
sin cambios, pruebas y documentación. Stage 3 sigue sujeto a aceptación de Joaquín.
