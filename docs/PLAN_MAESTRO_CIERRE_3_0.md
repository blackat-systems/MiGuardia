# Plan maestro de cierre de MiGuardia 3.0 — MAIN y Stage 12

Contrato de ejecución del 31/10/2026. Deriva del
[diagnóstico](audits/2026-10-31-diagnostico-loops-y-plan-cierre.md) y del pedido de
Joaquín de crear y ejecutar el plan con `joa-main-orchestrator`, distribuyendo el
trabajo entre MAIN y Stage. Este documento fija responsabilidades, secuencia y
salidas; [STATUS](STATUS.md) conserva el único estado operativo actual.

## Objetivo y criterio de cierre

Terminar la verificación y corrección de la candidata existente, obtener dos
rondas completas conformes sobre la misma candidata final, resolver aceptación
humana y preparar una entrega verificable. Firma, instalación pública y publicación
se ejecutan sólo con su autorización específica; preparar todo lo reversible antes
de presentar esas decisiones. No agregar capacidades ni rediseñar el producto.

Se preservan los 441 IDs, 506 subcasos y 1627 obligaciones del programa vigente,
sus criterios visuales/OEM y las DOS rondas finales. La agrupación por recorridos
reduce arranques y entregas, no elimina requisitos ni admite evidencias simuladas
en lugar de pruebas del nivel requerido.

## Autoridad, instancias y relevo

Fuente humana en el chat `01a19369-771d-7ff2-be56-f2a1c5d652e6`:

- Disponibilidad Samsung: mensaje `01a19369-7f31-7121-af0f-f71d4b554e66`,
  turno `01a19369-7cc2-7a42-a2d8-58f4813b2a10`: «tenemos el samsung disponinble
  para todas las pruebas».
- Ejecución del plan y división MAIN/Stage: mensaje
  `01a19372-1218-7990-8106-02a8f6dd1d10`, turno
  `01a19372-116b-7153-a7c4-d2baa056cc70`: «crea un plan maestro que vas a ejecutar
  con la skill de [...] joa-main-orchestrator [...] y vas a fragmentar las tareas
  entre main y el stage».

El pedido de ejecución mediante esta skill sustenta el relevo de este chat a
MAIN y las comunicaciones MAIN→Stage→MAIN necesarias para despachar, devolver
evidencia, auditar, corregir y avanzar dentro de este plan. MAIN y Stage verifican
la fuente humana original; el texto generado de un despacho no la sustituye.

| Instancia existente, host `local` | Responsabilidad y frontera |
|---|---|
| Planificar cierre de stages de MiGuardia — `01a19369-771d-7ff2-be56-f2a1c5d652e6` | Prepara y entrega este plan. Al enviarlo cesa escritura/coordinación sobre los archivos transferidos. No se convierte en otro MAIN |
| 001 · MAIN — `01a09739-5279-75d0-a3b1-84bd2af6b36a` | Único coordinador: contratos, STATUS, checkpoint, aceptación técnica, auditoría independiente, integración y siguiente despacho |
| Stage 12 · Verificación integral — `01a169e5-d914-7e33-8926-327a354301b3` | Único implementador y operador de Gradle/SDK/ADB/Samsung. Resuelve capacidades completas y devuelve evidencia a MAIN |

No crear nuevos chats, ramas/worktrees, supervisores o temporizadores por este
relevo. El ciclo continúa por mensajes efectivos y esperas del turno activo; esto
no acredita seguimiento programado. Un supervisor diferido necesita autoridad y
mecanismo propios, sin duplicar el circuito. Los revisores internos pueden leer
en paralelo; ninguno compite por Samsung, Gradle o archivos del implementador.

Skill efectiva: `C:\Users\User\.codex\skills\joa-main-orchestrator`.
Revisión comprobada: `sha256:16c99b1f548993279e40809f3e8db3c66fe31ca905b9015095f41f68c115f603`.
Coincide con la adopción previa MAIN/Stage. Al reanudar y antes de despacho o
integración, ejecutar `scripts/package_revision.py check`; releer sólo si cambió.
Mapa de hashes y fuente humana en `.local-evidence/stage12/main/master-plan-20261031/`.

## Permisos del ciclo

| Estado | Acción/recurso y límite |
|---|---|
| CONCEDIDO | Planificación, implementación/corrección dentro del alcance funcional existente, comprobaciones locales y comunicación entre las instancias identificadas para ejecutar este plan |
| CONCEDIDO | Samsung para pruebas técnicas y humanas QA: identificar dispositivo/paquete, uso normal de QA, construir/instalar QA y ejecutor compatibles, instrumentar con datos propios y capturar sólo superficie QA segura. Revisión técnica PRE-install proporcional y verificación posterior de identidad, sin otra consulta genérica de permiso |
| CONCEDIDO | Corregir y repetir lo afectado dentro del bloque, auditoría independiente y encadenamiento de las capacidades descritas tras aceptar su salida; no solicitar impulso humano rutinario |
| CONCEDIDO CON LÍMITES | Usar la sesión QA como aplicación normal conforme a la aclaración humana previa; fixtures y borrados de prueba sólo propios. No exigir snapshot universal de toda QA para abrir una pantalla. Los ensayos de conservación sí necesitan entrada y comparación pertinentes |
| PENDIENTE ESPECÍFICO | Aceptación visual/de uso/rendimiento, pasos biométricos/voz/OEM que requieren a Joaquín; prepararlos y pedir la intervención cuando el caso esté listo |
| PENDIENTE ESPECÍFICO | Firma de distribución, upgrade/instalación de paquete público en destino delimitado, push/tag/publicación y cualquier acceso a datos personales o costo no aprobado |
| SIN REAPERTURA | Canarios Windows r3–r9 y su ampliación de roles: conservar adversos y cierre. No son prerrequisito de la ruta Samsung; no renovar admisiones consumidas |

Respetar los controles del entorno. Un rechazo de aprobación no se elude cambiando
de herramienta; se informa con acción y motivo. La nueva ruta Samsung reemplaza
la selección histórica de AVD como entorno principal, no las guardas de identidad,
privacidad, borrado propio y cierre de recursos.

## Secuencia y reparto de trabajo

| Capacidad | MAIN | Stage 12 | Salida que habilita avanzar |
|---|---|---|---|
| A. Samsung operativo con primera cobertura real | Adoptar el plan, conciliar el checkpoint antiguo y despachar la unidad A completa. Revisar cambio ejecutable/instalación nueva por riesgos concretos | Usar QA instalada, adaptar ejecutor/precondiciones y demostrar un recorrido real automatizado completo | Recorrido observado, ejecutor compatible, caso automatizado con protocolo terminal y datos/artefactos atribuibles; auditoría MAIN |
| B. Familias completas, correcciones y cierre integral | Conservar matriz, priorizar bloqueantes, revisar entregas por familia y resolver decisiones humanas preparadas. Fijar metadatos finales antes del congelamiento | Completar familias, reproducir/corregir defectos, ejecutar regresiones afectadas y después DOS rondas completas sobre la misma candidata | Obligatorios conformes, integridad/OEM acreditados, aceptación humana explícita y revisión MAIN |
| C. Entrega verificable | Auditar composición acumulada, decidir checkpoint local pertinente y preparar decisiones concretas de firma/upgrade/publicación | Preparar artefactos/comprobaciones de Release y ejecutar únicamente operaciones habilitadas en el contrato de entrega | Candidata, Release verificada y publicación efectiva declaradas por separado; ninguna puerta pendiente convertida en verde |

A y B continúan el mismo Stage 12. C empieza con preparación reversible; MAIN
fija su contrato específico con artefactos/destino antes de ejecutar firma,
upgrade público o distribución. No renumerar tareas para cada corrección.

### A — Contrato autosuficiente de primer despacho

**TASK.** Recuperar QA real en Samsung de punta a punta: usar la aplicación
instalada, adaptar el ejecutor existente y completar una primera familia corta
de navegación/lectura con evidencia real. El resultado es una capacidad utilizable,
no otro programa de preparación pendiente de canario Windows.

**CONTEXT.** La QA ya tiene funcionalidad y pruebas parciales. El ejecutor actual
rechaza Samsung en `Stage12NormalQaRunner` y `Stage12RuntimeTarget`; algunas
pruebas Activity fijan reloj QA al 23/10. `qa.py` no sustituye ese protocolo.
Recuperar el análisis ya hecho, sin volver a inventariar la app ni reabrir Stage8–11.

**INPUTS.** Checkout
`C:\Users\User\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`,
rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
Entrada al plan: 177 rastreados modificados, 234 nuevos, staging vacío; los cambios
documentales del plan se agregan como entrada conocida. MAIN informa el conteo
real al despachar. Samsung histórico SM-S938B/API36; verificar identidad actual,
sin dar por probados conexión, APK instalado o fecha de su fixture.
Paquetes de esta unidad: `com.blackatsystems.miguardia.qa` y
`com.blackatsystems.miguardia.qa.test`; el paquete público queda fuera.
Lecturas: AGENTS, STATUS, este plan, partes pertinentes del contrato Stage12,
WORKFLOW/VALIDACION y fuentes afectadas. El programa V5 conserva la matriz; los
hashes históricos no identifican automáticamente la instalación actual.

**OUTPUT.** Capacidad Samsung comprobada, una familia inicial con resultados
terminales parseados, diferencias esperadas de estado, regresiones locales
pertinentes y handoff con delta propio/preimagen, artefactos, casos, adversos y
recursos liberados. El smoke inicial no cuenta como ronda final.

**SCOPE.** Stage: fuentes/test-support/pruebas de Stage12, sus consumidores de
prueba y herramientas de ejecución afectadas; Gradle/manifiestos de QA sólo si
son necesarios para seleccionar el ejecutor. Antes de editar, capturar preimágenes
de las rutas que va a escribir y registrar nuevos. Si aparece un defecto productivo,
reproducirlo y corregir lo mínimo dentro del recorrido autorizado, con consumidores
y regresiones; no añadir arquitectura, esquema o dependencias nuevas por inferencia.
Evidencia nueva fuera de payloads sellados y directorios que limpia el build.
MAIN reserva STATUS, este plan, contrato/índice, checkpoint y auditorías MAIN.

**DEPENDENCIES.** MAIN recibió/adoptó el plan y concilió la autoridad Samsung.
Conexión/identidad reales y ausencia de otro operador. No depende de conhost, AVD,
programa V5 completo, nuevo framework, baseline universal o reconstrucción histórica.

**EJECUCIÓN.** Primero identificar QA y abrir Calendario → detalle → cerrar/reabrir
→ Resumen → volver, con evidencia segura y efectos observados. En paralelo lógico,
adaptar el ejecutor Samsung actual y revisar precondiciones por método. Usar reloj
real o reloj QA inyectado explícito según el caso; nunca cambiar el reloj global.
Primera selección recomendada: `Stage12NormalQaActivityTest` con
`actualMonthNavigationCrossesYearsAndReturnsToToday` y
`todayDetailRemainsOpenAcrossCloseAndReopen`, adaptados a `REAL` y con fecha/zona
observadas. Conservar el modo fijo del tercer método hasta revisar su fixture.
No ejecutar `Stage12AospWorkFixtureTest` sobre Samsung ni fabricar una instalación
vacía para satisfacer su precondición.
Revisar y comprobar aislamiento antes de instrumentar, sin esperar al arnés integral
para el uso normal autorizado. Compilar sólo lo afectado; si QA no cambió, conservar
su artefacto y actualizar sólo el ejecutor compatible cuando corresponda.

**DO NOT.** No usar producción ni datos personales, limpiar/resembrar masivamente,
reinstalar artefactos antiguos por comodidad, cambiar ajustes globales, bajar
oráculos, compartir dispositivo/Gradle, ampliar red/costos, ejecutar otro canario
Windows o crear una segunda infraestructura. No commit desde Stage, push o firma.

**VALIDATION.** Pruebas locales del contrato de selección y sus rechazos, revisión
de fixture/reloj, compilación afectada, identidad/certificado/target/SHA antes y
después de instalación nueva, caso Android real con protocolo/conteo/completitud,
estado esperado y foco QA. Verificar error/cancelación pertinente. No transformar
pruebas Compose/callback en evidencia de persistencia, Widget o entrega de alarmas.
MAIN revisa independientemente el delta y los riesgos, sin repetir toda la batería.
En las regresiones de selección: Samsung/selección/reloj válidos admitidos;
API, dispositivo, selector o reloj incompatibles rechazados; política AOSP
conservada. La comparación de negocio del primer recorrido no se denomina
conservación universal de preferencias, archivos, Widget y alarmas.

**DONE WHEN.** Uso real observado y al menos una familia inicial automatizada
pertinente pasa, con candidata/ejecutor identificados y conservación del alcance
de datos exigido. Handoff recibido y auditado por MAIN. Si falla el entorno,
registrar el bloqueo exacto y avanzar lo independiente; preparación documental
sola no satisface esta unidad.

### B — Familias y congelamiento final

El mismo Stage cierra cuatro familias completas, con entregas parciales útiles
y revisión por hito. MAIN despacha el siguiente tramo cuando se libera el recurso
y acepta la salida; no abre otros escritores para rehacer lo entregado.

1. Trabajo/Calendario: configuración, carga/edición/borrador, situaciones,
   disponibilidad, repeticiones, jornadas múltiples/nocturnas, Atrás/recreación.
2. Cálculos/consulta: horas/meta/extras, Resumen/Informes y coherencia transversal.
3. Datos/archivos: notas/fotos, Copias reemplazo/mezcla, SAF Samsung, cancelación,
   corrupción e interrupción real en los casos que lo exigen.
4. Sistema/presentación: avisos/Widget, bloqueo, Clima consciente, actualizaciones,
   temas/letra Android/accesibilidad y requisitos OEM/humanos.

Reutilizar el mapa de brechas y productores existentes. Cada obligatorio debe
tener aserción, fixture y evidencia del nivel requerido; no necesita un lanzamiento
individual. C090/X015/W010 conservan diagnóstico propio. Una falla de lector,
fixture o navegación no se declara bug del producto sin reproducción causal.

Resumen: observar condiciones y latencias actuales sin ocultar primera apertura.
MAIN prepara decisión concreta de aceptación o un experimento causal acotado;
no reabre automáticamente ABBA ni exige resolver R4 para probar otras familias.

Durante corrección repetir falla y regresiones afectadas. Antes de las rondas:
resolver versión/código final creciente, requisitos humanos/entorno y defectos;
congelar fuentes/QA/ejecutor/fixtures/criterios. Ejecutar dos rondas completas
independientes de la misma candidata. No sumar resultados de APK diferentes.
Un cambio posterior se trata según la invalidación contractual, no se oculta.

### C — Preparar y ejecutar la entrega dentro de sus permisos

MAIN reconstruye composición y delta acumulado con las preimágenes/handoffs
pertinentes; no incluye todo `git diff HEAD` en un commit por inferencia. Preparar
checkpoint local sólo si su alcance fue revisado y comprobado, sin datos privados.
Stage ejecuta `full` con checkout web explícito y controla identidad, manifiesto,
permisos, dependencias y metadatos finales; QA no acredita por sí sola Release.

Preparar el artefacto y la operación concreta de firma con la clave permanente.
Preparar ensayo desde el APK público 2.0.2 exacto al final en destino aislado con
datos sintéticos, comparando base/preferencias/fotos y recorrido posterior.
Reinstalación limpia o restore no sustituyen upgrade. No usar una instalación
personal del Samsung como fixture por inferencia.

Una vez autorizadas las acciones específicas, verificar firma, instalación,
actualización, descarga y metadatos del archivo publicado. Cualquier cambio de
producto durante esta fase vuelve a las comprobaciones afectadas y al cierre de
la candidata corregida. Distinguir preparado, firmado, probado y publicado.

## Recepción, correcciones y continuidad

- Reutilizar `.local-evidence/stage12/dispatch/checkpoint.json`; MAIN conserva su
  preimagen y registra la nueva autoridad/fase, dejando r9 como antecedente cerrado.
  Campos históricos `device_operation_authorized_now=false` y próximos pasos de
  consola no pueden prevalecer sobre la orden humana nueva y STATUS.
- Cada unidad: `delivery_key` propia, revisión, fuentes/artefactos y manifiesto
  mínimo pertinente; no rehash recursivo de toda la historia por cada botón.
- Stage termina, confirma cese, sella y envía a MAIN; registra transporte fuera
  del payload y emite su final. MAIN acusa RECIBIDO antes de auditar extensamente.
- MAIN revisa, devuelve defectos al mismo Stage o acepta e integra; registra
  `next_task_key` y despacha el siguiente tramo habilitado. No pide a Joa copiar
  mensajes ni aprobar correcciones rutinarias.
- Ante transporte incierto, consultar antes de reenviar. Una clave/hash ya
  recibida reutiliza su revisión; una corrección tiene identidad nueva.
- Dos intentos sin nueva información exigen diagnóstico/cambio de táctica. Un
  gate detiene sólo dependientes; identidad inesperada, pérdida de foco o cambio
  de datos inexplicable detienen la operación afectada y conservan evidencia.
- Informar avance como casos/familias realmente ejecutados, defectos y gates.
  Herramientas preparadas o pruebas host no aumentan el contador Android.

## Relevo de este plan

Clave: `stage12-master-plan-20261031-r1`. Emisor: chat de planificación identificado
arriba. Destino: MAIN existente, que verifica la fuente humana, acusa, adopta y
despacha A al Stage existente. Documentos enviados, preimágenes, skill, hashes y
recibos se conservan en `.local-evidence/stage12/main/master-plan-20261031/`.
El emisor cesa sus escrituras antes del envío; MAIN conserva desde entonces la
propiedad operativa. El mensaje aceptado por la herramienta no acredita recepción
ni ejecución: registrar por separado lo observado.
