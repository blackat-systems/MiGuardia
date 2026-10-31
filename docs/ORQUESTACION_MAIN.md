# Orden de orquestación de MAIN — MiGuardia 2.0

> Referencia histórica 2.0. Para continuar 3.0 rigen `docs/STATUS.md`, su contrato y `docs/WORKFLOW.md`. Las etapas y expresiones de despacho siguientes conservan su contexto 2.0; no reabren trabajo ni constituyen una ruta 3.0 aceptada.

**Contrato de orden adoptado por Joaquín y actualizado por su decisión de distribución pública del 8/09/2026.** MAIN debe organizar la continuación desde las 24 etapas de este flowchart. Este archivo define **orden, dependencias y aceptación**; `docs/STATUS.md` conserva **etapa alcanzada, siguiente despacho, estado, evidencia y autorizaciones**. Planificación y ADR conservan las reglas de producto y técnicas.

Al entrar por primera vez, leer este documento. Al reanudar, comprobar STATUS y el delta Git; releer sólo la etapa pertinente y los contratos modificados. El código de presentación Mermaid y los inventarios históricos no son lectura de arranque.

## 1. Regla de despacho

1. Hacer Puerta 0 según AGENTS. Preservar entradas modificadas y recursos compartidos; resolver diferencias de base antes de atribuir evidencia.
2. Leer en STATUS `Etapa del mapa alcanzada` y `Siguiente etapa del mapa`, junto con el bloque autorizado y sus límites. Una etapa alcanzada no se vuelve a implementar por aparecer arriba en el diagrama. El número siguiente indica dónde preparar/retomar el trabajo, no que ya esté ejecutado o autorizado sin límites.
3. Contrastar la entrada y aceptación de esa etapa en §4. Si existe un contrato o candidato del mismo alcance, reutilizarlo. MAIN delimita el bloque en STATUS antes de despachar ejecución; no convierte contratos cerrados en una nueva implementación.
4. Resolver capacidades completas con la metodología de WORKFLOW: diagnóstico, cambios, errores, integración, regresiones, QA autorizada y handoff. Los números son hitos, **no una orden de crear 24 chats**. Las etapas 19–22 forman un paquete de entrega con dependencias internas; pueden repartirse por responsabilidades reales, con un escritor por archivo y un solo operador de build/dispositivo.
5. Avanzar dentro de las autorizaciones vigentes sin pedir activación por cada acción reversible. Si falta una decisión material, mantener pendiente la parte dependiente y avanzar la preparación independiente. No heredar permiso para otro dispositivo, borrar datos, un recurso externo diferente o push/tag.
6. Aceptar la etapa sólo con evidencia de su salida. Actualizar STATUS y vincular el cierre fechado; avanzar su cursor al siguiente hito. Una preparación, compilación o prueba parcial no permite saltar la puerta restante.
7. Ante regresión, registrar en STATUS la etapa afectada y el defecto, sin borrar su cierre histórico. Corregir la causa, repetir lo afectado y recuperar la ruta. Un cambio de candidata renueva firma/hashes y QA pertinente; un texto externo no obliga a refirmar bytes idénticos.

No cambiar el orden material, retirar puertas o ampliar producto por conveniencia. Una decisión nueva de Joaquín se registra primero en la fuente que gobierna esa decisión y luego se reconcilia aquí. No convertir una fecha, cantidad de pruebas o número de tarjetas en porcentaje de finalización.

## 2. Etapas estables del flowchart

La tabla identifica capacidades; no contiene un segundo estado. Los cierres concretos, bases y límites se consultan en STATUS y sus auditorías.

| Etapa | Área | Capacidad / salida |
|---|---|---|
| 1 | Fundaciones V2 | Reglas laborales y horas: cuatro sectores, configuración única, vigencias e intervalos exactos. |
| 2 | Fundaciones V2 | Catálogo y primera apertura: sector, lugar, tipo y horario reutilizable. |
| 3 | Jornadas y Calendario | Carga manual individual o múltiple con confirmación expresa. |
| 4 | Jornadas y Calendario | Editar/eliminar la jornada elegida conservando historia y concurrencia. |
| 5 | Jornadas y Calendario | Runtime exclusivamente V2, sin adopción de datos V1. |
| 6 | Jornadas y Calendario | Recurrencias finitas, excepciones protegidas y cambios desde fecha. |
| 7 | Motor de horas | Horario real y extras expresas de una jornada. |
| 8 | Motor de horas | Extras independientes y avance de la referencia de horas. |
| 9 | Motor de horas | Guardias pasivas/disponibilidad separadas del trabajo activo. |
| 10 | Experiencia final | Calendario único y tarjeta superior; consulta sin escrituras. |
| 11 | Experiencia final | Resumen personalizable coherente con el motor de horas. |
| 12 | Experiencia final | Próximo evento y notificaciones sobre una proyección única. |
| 13 | Cierre del núcleo | Auditoría transversal del núcleo y compatibilidad Android en su base. |
| 14 | Cierre del núcleo | Widget, informes, copias/restauración, bloqueo, Ayuda, fotos y Clima. |
| 15 | Cierre del núcleo | Candidato local integrado y comprobado; sin inferir distribución. |
| 16 | Preparación beta | Acceso beta, opiniones, Apoyar MiGuardia y cliente de actualización manual. |
| 17 | Preparación beta | Preparación de APK unsigned y custodia de la clave permanente. |
| 18 | Preparación beta | Creación desde la carga, créditos y terminación visual. |
| 19 | Entrega oficial | Transición pública Android/web y revisión de seguridad. |
| 20 | Entrega oficial | Candidata final verificada y firmada. |
| 21 | Entrega oficial | QA de entrega y actualización instalada con datos preservados. |
| 22 | Entrega oficial | Publicación pública y recorrido externo verificado. |
| 23 | Seguimiento y estable | Feedback voluntario, incidencias y correcciones. |
| 24 | Seguimiento y estable | Continuidad de versión estable, entregada y actualizable. |

Los números agrupan el recorrido aprobado. No reemplazan números de contratos o tareas: por ejemplo, el cliente «bloque 28» corresponde a la etapa 16 de este mapa. La etapa 9 no habilita situaciones especiales ampliadas; Agenda profesional/Psicología, iOS, nube laboral y OCR/importación quedan fuera del cierre actual.

## 3. Flowchart integrado

[Fuente Mermaid del mapa](diagrams/orquestacion-main.mmd). Los colores, cifras y punto actual son una **vista derivada de STATUS**, nunca autoridad alternativa. La fuente conserva la numeración y refleja la distribución pública definida por ADR 0041.

La exportación PNG del plan beta privado fue retirada durante la higiene previa a 3.0 porque estaba obsoleta y duplicaba byte por byte la fotografía histórica conservada en `docs/audits/2026-09-08-mapa-visual-y-guia-de-cierre.png`. Si se necesita una nueva imagen, regenerarla desde la fuente vigente, revisarla visualmente y fecharla; no instalar dependencias de producción sólo para dibujar documentación.

## 4. Dependencias y aceptación de la entrega

### Etapa 19 — Transición pública y revisión de seguridad

**Entrada:** salida local hasta 18; decisión explícita de distribución pública,
planificación §22 y ADR 0041. **Dueños:** MAIN fija contratos; responsables
Android/web resuelven superficies completas. No crear tareas sin indicación.

- Cambiar de producto beta privado a descarga pública sin cuenta, canje, plazas
  ni opinión obligatoria. No reabrir capacidades laborales ni rehacer el cliente
  28: preservar sus controles y retirar únicamente su dependencia privada.
- Contrastar código y despliegue web real antes de fijar contrato público de
  metadatos/descarga y feedback voluntario. Preservar el portal histórico sin
  exponer sus APIs por accidente ni borrar recursos/datos remotos.
- Resolver correo público de privacidad/soporte y mecanismo mínimo, campos y
  retención del feedback. Plazo de inscripción y criterios de diez plazas
  quedan retirados, no pendientes. No solicitar identificadores de instalación.
- Adaptar productores y consumidores Android/web sobre contrato común; conservar
  recuperación, Bloqueo, configuración, Ayuda, navegación, avisos y Widget.
  Ausencia/corrupción/cola beta antigua no debe bloquear ni borrar trabajo.
- Auditar amenazas, secretos del árbol/historial/artefactos, dependencias y
  vulnerabilidades aplicables, archivos/URI, componentes, privacidad, transporte,
  SQL/validación y superficie administrativa. Usar MASVS/MASTG y ASVS según
  corresponda; cifrado local requiere análisis de amenaza y recuperación.
- Corregir defectos reproducibles, verificar integridad/autenticidad de descarga,
  preservar datos y probar errores. No prometer invulnerabilidad. No confundir
  claves públicas verificadoras con credenciales secretas del servidor.
- Aceptar sólo con cero fallos abiertos de pérdida/exposición sensible,
  autenticidad, instalación/actualización o bloqueo principal, y otros riesgos
  clasificados con aceptación explícita. Los ensayos finales de 21 no se
  declaran ejecutados aquí.

**Salida exigida:** candidata pública coherente localmente, contrato interoperable,
revisión de seguridad con evidencia y condiciones operativas definidas. Preparación
técnica independiente puede avanzar mientras se resuelve el contacto. Servicio de
ensayo antes de 21; publicación pública únicamente después de aceptar 20/21.
Fuentes: ADR 0041, partes conservadas de 0039/0040 y contratos web verificados.

### Etapa 20 — Candidata final verificada y firmada

**Depende de:** salida de 19. **Dueño:** MAIN y operador de entrega; Joaquín
custodia la contraseña.

- Congelar insumos Android/web, versión y mecanismo de autenticidad público.
  Nombre/código efectivos basados en versiones anteriores; conservar paquete
  y clave APK existente. No regenerar custodia.
- Dos reconstrucciones finales sobre iguales insumos; comprobar identidad,
  permisos/componentes, ABI, flags, alineación y esquemas. No heredar hashes o
  reproducibilidad de la candidata privada.
- Firmar con la clave permanente, verificar certificado/firma/alineación y
  comparar contenido frente al unsigned aceptado. Mantener el inspector
  unsigned estricto; claves y artefactos fuera de Git.
- Registrar SHA-256/tamaño del APK firmado y metadatos concordantes.

**Salida exigida:** APK firmado verificable, no instalación ni publicación.
Reutilizar herramientas y garantías de preparación/custodia; el prompt histórico
de preparación beta no reinstala condiciones privadas sustituidas por ADR 0041.

### Etapa 21 — QA de entrega y actualización con datos

**Depende de:** 20 y servicio controlado preparado. **Dueños:** operador único
autorizado y MAIN revisor.

- Separar JVM, interoperabilidad, QA y Release real. No simular distribución
  pública mediante inyección de una sesión beta autorizada.
- Usar dos aplicaciones completas, igual paquete/certificado y códigos
  crecientes. Mini APK de inspección no se instala como aplicación.
- Probar instalación sin canje y offline; upgrade con datos poblados preservando
  configuración, jornadas/fotografías históricas, planes, extras, notas, fotos,
  preferencias, bloqueo, avisos y Widget. Estados beta antiguos sin bloqueo.
- Recorrer buscar → descargar → verificar → guardar → abrir desde Archivos →
  Actualizar → reabrir. No desinstalar ni restaurar copias para simular conservación.
- Probar corrupción, certificado/paquete/versiones ajenos, cancelación, desconexión,
  proveedor SAF, retorno bloqueado, destinos, temas/zoom, teclado/paisaje,
  accesibilidad y APIs/OEM pertinentes. Datos destructivos sólo en aislamiento
  autorizado; nunca inferir permiso para limpiar Samsung.
- Registrar límites por superficie; un fallo bloqueante impide 22. Si cambia
  candidata, renovar los controles afectados de 20 y repetir QA pertinente.

**Salida exigida:** instalación pública y upgrade real con datos preservados,
matriz resuelta, riesgos residuales explícitos y regresiones cerradas.
ADR 0040 sólo aplica en su alcance conservado por ADR 0041.

### Etapa 22 — Publicación pública verificada

**Depende de:** aceptación de 19–21. **Dueños:** MAIN y responsable web,
dentro del alcance de recursos/publicación autorizado.

- Publicar página bilingüe y APK exacta aceptada; CTA final de descarga, sin
  formulario de reserva, activación, cupo ni dependencia de una cookie beta.
- Verificar HTTPS, archivo servido/hash/certificado, metadatos, instalación y
  actualización externas. Opiniones y aporte voluntarios, separados del acceso.
- Revisar rutas beta residuales: no filtrar datos ni exponer operaciones privadas.
  Desactivar oferta obsoleta no significa borrar D1/R2 o registros por inferencia.
- Publicar límites reales, soporte, privacidad e instrucciones. Ante incidente,
  retirar la oferta de archivos afectados sin apagar instalaciones ni borrar datos.

**Salida exigida:** descarga oficial pública y recorrido externo verificados.
Push, deploy exitoso o página informativa no sustituyen esa evidencia.

### Etapa 23 — Seguimiento voluntario y correcciones

**Depende de:** 22. **Dueños:** Joaquín como producto, MAIN y responsable web.

- Recibir únicamente opiniones/incidencias enviadas conscientemente, sin datos
  laborales exigidos. No temporizador bloqueante, plaza ni obligación a quince días.
- Priorizar seguridad, pérdida, actualización y uso principal; ausencia de
  feedback no demuestra éxito. No reabrir funciones nuevas por inferencia.
- Distribuir correcciones de código creciente mediante controles pertinentes
  20 → 21 → 22. No reiniciar todo el proyecto por cada corrección.

**Salida exigida:** incidencias/residuales evaluados y soporte operativo. No es
un piloto obligatorio de diez personas ni impone plazo para poder usar la app.

### Etapa 24 — Continuidad de versión estable

**Depende de:** entrega aceptada y seguimiento de 23.

- Mantener versión estable, firma y código creciente; el cambio de acceso no
  fuerza 3.0.0. Si la primera publicación ya cumple aceptación estable, no exigir
  otra publicación cosmética para cumplir esta tarjeta.
- Verificar upgrade desde las versiones compatibles distribuidas, con datos
  preservados; notas de versión, recuperación, privacidad y soporte disponibles.
- Mantenimiento posterior conserva garantías, sin activar proyectos ajenos.

**Salida exigida:** versión estable entregada, mantenible y actualizable con
riesgos explícitos; ningún punto permite saltar la seguridad o QA anteriores.

### Carril separado — Git y recursos externos

Push, tag, GitHub Release y movimiento de `main` no son efectos automáticos de 19–24. Comprobar autorización específica, rango/artefacto exacto y referencia resultante. Preservar autorizaciones de entrega ya registradas sin volver a pedir otra genérica; tampoco trasladarlas a un ensayo o recurso no cubierto.

## 5. Conservación de la ruta y handoff

En cada aceptación, MAIN actualiza sólo en STATUS la etapa alcanzada, siguiente etapa y evidencia; el bloque/contrato autorizado se mantiene coherente con su índice. Un fallo posterior deja visible el cierre histórico y el nuevo trabajo afectado. No avanzar el cursor por tener un commit o una APK compilada.

Si cambia el orden por decisión de producto, actualizar este contrato, planificación/ADR afectados y la numeración del Mermaid juntos. Si sólo cambia estado, actualizar STATUS y después derivar rótulos/colores/fecha del Mermaid y regenerar el PNG; mantener los números estables. Revisar enlaces y `python tools/docs_check.py`; el código de presentación se lee sólo al modificar la gráfica.

Handoff de despacho/aceptación: etapa(s), objetivo de punta a punta, HEAD/delta e insumos, contrato reutilizado, dueño y recursos, archivos, autorización aplicable, salida esperada, evidencia por superficie, omisiones/riesgos y siguiente etapa. No copiar inventarios completos a cada tarea.

Origen de la ruta: [mapa visual aprobado](audits/2026-09-08-mapa-visual-y-guia-de-cierre.md). [Auditoría de respaldo](audits/2026-09-07-auditoria-integral-y-plan-de-cierre.md) para trazabilidad puntual; no es lectura obligatoria en cada arranque ni otro plan activo. Esta adopción convierte el **orden** del mapa en instrucción de MAIN; no ejecuta sus pasos sensibles ni convierte las propuestas pendientes en decisiones de Joaquín.
