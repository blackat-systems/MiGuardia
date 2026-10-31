# Diagnóstico de los loops y plan de cierre de MiGuardia 3.0

Fecha: 31/10/2026, Buenos Aires. Pedido: estudiar los últimos chats/stages y
proponer un proceso que llegue al cierre de la app. Samsung declarado disponible
para todas las pruebas. Este informe contiene evidencia leída y un plan PROPUESTO;
no es una ejecución Android, aceptación del producto ni autorización de distribución.
El estado operativo único permanece en [STATUS](../STATUS.md).

## Resultado

El bloqueo inmediato de Stage12 es de infraestructura de ensayo Windows, no un
fallo Android observado en su última tentativa. A la vez, la cobertura integral
todavía no está implementada/ejecutada: conectar Samsung no completa la matriz.
La salida recomendada es pasar a Samsung como ruta principal, probar la QA desde
el primer bloque y adaptar el ejecutor existente por familias completas. No hace
falta resolver primero el programa cooperativo de consola/emulador para ese camino.

Se conservan el producto, la matriz y las DOS rondas finales completas. Se propone
cambiar la secuencia de trabajo y limitar la preparación, no declarar verdes por
documentación ni quitar casos difíciles.

La disponibilidad nueva quedó registrada en el chat
`01a19369-771d-7ff2-be56-f2a1c5d652e6`: «tenemos el samsung disponinble para todas
las pruebas». Reemplaza la restricción de Samsung sólo para QA humano. Este
diagnóstico no ejecuta esa autorización ni adopta por sí solo el plan propuesto.

## Fuentes y límites de esta revisión

Puerta0 observada: checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, un worktree, remoto
`https://github.com/blackat-systems/MiGuardia.git`. Antes de esta revisión:
177 archivos rastreados modificados, 233 archivos nuevos y staging vacío.
El último commit es del 14/09/2026. Ese delta es entrada acumulada, no autoría
de este chat; no se agregó al índice, descartó ni convirtió en commit.

Se leyeron los últimos turnos relevantes de estos chats mediante `read_thread`,
además de fuentes del repositorio. No se afirma haber releído toda su historia:

| Chat (título de la aplicación) | ID | Evidencia de la última actividad relevante |
|---|---|---|
| 001 · MAIN | `01a09739-5279-75d0-a3b1-84bd2af6b36a` | Revisión r9: STOP, cierre de recursos, 0/2 rondas; decisión sobre consola y supervisor eliminado |
| Stage 12 · Verificación integral | `01a169e5-d914-7e33-8926-327a354301b3` | Preparaciones r6–r9, timeout/miembro adicional, entregas y esperas de nuevas admisiones |
| Stage 11 · Restauración de Copias | `01a166ec-dd5d-71b3-9401-a4169ffbb0a3` | Restore R1 entregado, recibido y auditado; 35/35 Android |
| Stage 10 · Actualización QA sin pérdida… | `01a15d55-500a-7162-be24-f4ed70e990a5` | Recuperación cerrada; ensayo original de una actualización incompleto |
| Stage 9 · Conservación de datos V6 a… | `01a15cb3-8874-74c0-8303-1a55b687f4f5` | R1 local conforme; reuso explícito de insumos coincidentes |
| Stage 8 · Rendimiento de Resumen y guardado | `01a15c6c-5e1e-7351-a4b2-c90b771aea0b` | Campaña de cuatro corridas/432 muestras cerrada; causal de Resumen inconclusa |

Revisores internos independientes analizaron Stage12 y pendientes 8–11 en sólo
lectura, sin competir por archivos, Gradle o dispositivo. Sus conclusiones se
contrastaron con contrato, código y auditorías. No se reejecutaron las campañas
ni se recalcularon todos sus payloads: sus cifras son evidencia histórica auditada,
no resultados nuevos sobre una candidata del 31/10.

## Por qué se frenó el proceso

| Hallazgo | Evidencia | Consecuencia y corrección propuesta |
|---|---|---|
| La infraestructura pasó a ser el camino crítico | Últimos turnos de Stage12; r9 registra `conhost.exe` como tercer miembro frente a CMD+helper; decisión MAIN preservada en `.local-evidence/stage12/main/canary-result-8e747-review/decision.md` | El ensayo prueba pertenencia/cierre de procesos Windows; no una función de MiGuardia. Retirar esa ruta del camino principal cuando se adopte Samsung, sin convertir el STOP anterior en PASS |
| La cobertura se descompuso más rápido de lo que se ejecutó | Plan V5 y contrato: 441 IDs, 506 subcasos, 1627 aserciones; artefactos/plan locales y rondas completas 0/2 | Usar esa matriz como trazabilidad, agrupar por recorridos completos y producir casos Android reales desde el primer bloque; no crear otro inventario |
| Permisos y destinos cambiaron varias veces | Contrato conserva adendas Samsung→AVD→Samsung→sólo humano; instrucción nueva del 31/10 libera Samsung para todas las pruebas | Una cabecera vigente y STATUS deben resolver la precedencia; no redescubrir ni repreguntar permisos ya dados |
| Las unidades de entrega se volvieron demasiado pequeñas | Chats muestran preparación, sello, revisión, admisión, canario, nuevo sello y otra revisión para cada cambio del arnés | Mantener revisión independiente por familia/hito de riesgo; resolver correcciones de validez dentro del mismo bloque, con un operador |
| No hay una candidata reciente representada por un commit | HEAD del 14/09 y 410 archivos acumulados; hay snapshots/hashes históricos, por lo que no se afirma ausencia de respaldo | Fijar una candidata reproducible y revisar la composición una vez; no auditar repetidamente todo el historial. Checkpoint local sólo tras revisar su alcance |
| Los pendientes se arrastran como bloqueos globales | Stage8 cerrado inconcluso, Stage10 recuperado pero ensayo original incompleto, Stage11 restore aprobado en alcance sintético | Dar a cada pendiente una prueba/decisión final concreta; no reabrir los tres stages completos |

Las guardas surgieron de riesgos reales. El problema inferido es su encadenamiento
y amplitud, no que conservar datos o comprobar identidad sea innecesario. El propio
[contrato Stage12](../prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md)
ya permite agrupar métodos, continuar casos independientes y repetir sólo lo
afectado durante diagnóstico (§7); prohíbe prolongar herramientas indefinidamente
(§10.3). La aclaración humana del 24/10 ya permitía abrir la QA instalada sin
esperar un baseline universal. Hay que ejecutar esas reglas.

La brecha es concreta: `.local-evidence/stage12/20261027-round-program-r2/HANDOFF.md`
registra 149 grupos de faltantes, 24 suites/154 métodos candidatos y cero bindings
completos nuevos. Faltan ejecuciones, fixtures y enlaces entre aserciones y evidencia;
no significa que haya que escribir 1627 pruebas nuevas. Los adversos C090, X015 y
W010 deben conservar diagnóstico propio, sin atribuir un fallo del arnés al producto.

## Lo que se conserva y lo que falta

| Frente | Evidencia aprovechable | Pendiente que realmente importa |
|---|---|---|
| Producto de etapas anteriores | Candidata funcional y correcciones acumuladas; B01/B02 y etiqueta de avisos auditadas localmente | Verificación Android pertinente de la candidata final y aceptación humana; no reiniciar desarrollo 3.0 |
| Stage8 | Guardado sin alerta; Resumen alertó sólo en un par (+29,85%, +369,650ms); el criterio de continuación exigía ambos órdenes | Medir uso actual y obtener aceptación de latencia o abrir una única investigación acotada. No atribuir la causa histórica ni repetir ABBA por rutina |
| Stage9/10 | Conservación local y recuperación Android del mismo conjunto auditadas | Una actualización real desde la versión pública compatible al artefacto final, en entorno aislado autorizado; no presentar reposición QA como aquel upgrade |
| Stage11 | ReplaceAll corregido; 35/35 Android sintéticos y 41 comprobaciones SQL | SAF/OEM, fotos y recuperación/interrupción real donde corresponda, más conservación sobre el candidato final |
| Stage12 | Preparación, matriz y evidencia local; pruebas Samsung parciales de otros recorridos | Adaptación del ejecutor, cobertura obligatoria, dos rondas completas, OEM/humano. 0/2 no significa que nunca se haya probado nada en Samsung |
| Entrega | Identidad pública y clave permanente existentes según documentación | Versión final, `full`, artefacto firmado, firma/upgrade/instalación verificadas y publicación autorizada. `app/build.gradle.kts` todavía declara 6/2.0.2 |

Fuentes: [cierre Stage8](2026-10-22-stage8-revision-main-cierre.md),
[recuperación Stage10](2026-10-23-stage10-revision-main-recuperacion-r2.md),
[revisión Stage11](2026-10-23-stage11-revision-main.md),
[L2 Stage12](2026-10-23-stage12-revision-main-l2.md),
[artefactos Stage12](2026-10-23-stage12-revision-main-cierre-local.md).
La [pasada Samsung del 23/10](2026-10-23-cierre-final-samsung-qa.md) observó 12
aperturas de Resumen con contenido, primera apertura medida cercana a 2,2 s y
reaperturas con caché; no es un umbral aceptado ni otra comparación causal.

## Plan propuesto: tres bloques, con salida verificable

### 1. Fijar candidata y comenzar pruebas reales en Samsung

- MAIN coordina; el mismo responsable Stage12 opera Gradle/ADB/teléfono. No hacen
  falta nuevos chats. Revisores de sólo lectura pueden trabajar en paralelo.
- Identificar estado actual de APK QA/runner, certificado, fuentes y dispositivo;
  preservar el checkout acumulado y sus evidencias. La declaración de disponibilidad
  reemplaza el permiso viejo; conexión/depuración efectivas se comprueban al usarlo.
- Abrir y recorrer la QA instalada con identidad atribuible y datos de prueba.
  Primer resultado: arranque, Calendario, abrir/cerrar detalle, ir a Resumen y
  volver. Esto es smoke observado, no las 441 filas ni una ronda final.
- Hacer la adaptación mínima Samsung del ejecutor existente por familia completa.
  `Stage12NormalQaRunner` y `Stage12RuntimeTarget` hoy rechazan Samsung/restringen
  selectores a AOSP. Revisar además fixture y reloj de cada método: las pruebas
  Activity existentes comparten fecha sintética 23/10, por lo que no basta quitar
  dos guardas y llamarlas fecha real. No cambiar el reloj global.
- `qa.py` no es sustituto inmediato: fija `AndroidJUnitRunner` y no proyecta los
  argumentos de Stage12. Tampoco reinstalar ejecutores históricos con hashes o
  conteos 1956 fijos. Reutilizar suites válidas con sus precondiciones comprobadas.
- Antes de instalar un APK nuevo: revisión proporcional de identidad, target,
  firma técnica, hash y aislamiento; después comprobar lo instalado. Fixtures y
  borrados de pruebas sólo en contextos propios. No limpiar QA/producción por rutina.

**Salida:** un caso real observado y otro automatizado pertinente con resultado
completo y atribuible; candidata/runner identificados, fallos clasificados y una
lista concreta de familias siguientes. El arnés integral no es condición para
empezar a usar la app. Si el primer bloque acaba sólo con documentación/preparación,
el método no alcanzó su salida y MAIN debe cambiar de táctica.

### 2. Completar cobertura, corregir y ejecutar las dos rondas finales

Usar la matriz existente y agrupar su ejecución, sin perder IDs/subcasos:

| Familia de recorrido | Superficies que debe cerrar |
|---|---|
| Trabajo y Calendario | Configuración, carga/edición/borrador, jornadas múltiples/nocturnas, situaciones, disponibilidad, repeticiones, atrás/recreación |
| Cálculos y consulta | Horas/meta/extras, Resumen e Informes, coherencia entre pantallas, lectura sin escrituras |
| Datos y archivos | Notas/fotos, exportar/importar Copias, reemplazo/mezcla, cancelación/corrupción/interrupción, selector SAF Samsung |
| Sistema y presentación | Avisos/Widget, bloqueo, Clima consciente, actualizaciones, temas/letra Android, accesibilidad y recorridos OEM/humanos |

- Cada subcaso queda asociado a una ejecución/prueba manual permitida y oráculos
  reales. Un método puede cubrir varios subcasos con aserciones explícitas; un
  inventario de 1627 obligaciones no exige 1627 lanzamientos ni otro framework.
- Durante exploración, corregir el defecto y sus consumidores equivalentes;
  repetir sólo regresiones afectadas. Continuar familias independientes.
- Priorizar pérdida/corrupción, privacidad, crash/ANR, guardado duplicado,
  cálculo incorrecto y recorridos rotos. Diferenciar fallo de app, fixture,
  ejecutor y entorno. Una mejora fuera de contrato va a backlog; un obligatorio
  fallido no se convierte en mejora opcional para cerrar.
- Resumen: conservar primer acceso y condiciones fría/caliente por separado.
  Mostrar a Joaquín la latencia actual. Resolver explícitamente aceptación o
  investigación acotada con hipótesis, medida y condición de salida; no exigir
  explicar R4 para poder probar Copias, Widget o Calendario.
- Preparar juntos los pasos que necesitan intervención de Joaquín, cuando estén
  listos. Samsung disponible no equivale a aceptación estética, biométrica o de voz.
- Resolver la versión pública final y su código creciente antes del congelamiento;
  comprobar los consumidores afectados. No cambiar esos metadatos después de las
  rondas y seguir llamándola la misma candidata.
- Tras resolver los defectos y fijar esos metadatos, congelar fuentes, QA, runner,
  fixtures y criterios.
  Ejecutar las DOS rondas completas independientes de la MISMA candidata final,
  sin sumar verdes de APK distintos ni reducir la matriz. Evidencia previa válida
  ahorra rediagnóstico; no reemplaza estas ejecuciones finales exigidas.

**Salida:** matriz obligatoria completa, dos rondas conformes, defectos bloqueantes
resueltos, revisión independiente focalizada y aceptación humana explícita de los
pendientes de producto/rendimiento. Si queda un obligatorio, salida parcial con
dueño y acción concreta, nunca aprobación global por inferencia.

### 3. Convertir la candidata aceptada en una entrega comprobable

- Revisar composición acumulada y preparar un checkpoint local coherente; no
  `git add` indiscriminado ni incorporación de evidencias privadas. Revisar permisos,
  dependencias, manifiesto, metadatos y privacidad del artefacto final.
- Conservar la versión/código fijados antes de las rondas y ejecutar `full` con
  checkout web explícito, según [VALIDACION](../VALIDACION.md). Los resultados anteriores se
  reutilizan sólo donde sus insumos/superficie coinciden; no inventar interop.
- Preparar firma con la clave permanente, sin regenerarla, y presentar el artefacto
  y destino concretos para la autorización de firma/distribución que corresponda.
- Ensayar origen público 2.0.2→final con paquete y certificado compatibles en un
  entorno de prueba autorizado sin datos personales. Si el Samsung tiene instalación
  productiva, no usarla por inferencia: preparar un destino aislado específico.
  Comparar DB/preferencias/fotos y arranque; restaurar una copia prueba otra cosa.
- Comprobar el APK final firmado y su instalación/upgrade; comprobar después la
  descarga y metadatos de publicación cuando ésta sea autorizada.

La variante QA no sustituye el ensayo de Release. Comparar fuentes/configuración
entre ambas y probar el artefacto de distribución; si aparece un cambio funcional
necesario, volver a las verificaciones afectadas y a los cierres de la candidata
corregida. No modificar metadatos o producto silenciosamente durante la firma.

**Salida:** distinguir candidata aceptada, release verificada y publicación efectiva.
La app está lista para entregar cuando los requisitos pertinentes de funcionamiento,
datos, aceptación y artefacto final están cumplidos; publicada sólo tras hacerlo y
verificarlo. No usar el build Release unsigned 6/2.0.2 como entrega 3.0.

## Reglas para que no vuelva el loop

1. Un operador de Samsung/Gradle, un escritor por archivo y MAIN responsable de
   decisiones e integración. Handoffs por familia completa; no por cada botón.
2. Toda preparación debe nombrar el próximo caso real que desbloquea. Sin ese
   caso y una brecha demostrada, no agregar runner, capa, skill o documento.
3. Dos intentos sobre el mismo fallo sin evidencia nueva obligan a diagnóstico
   y cambio de táctica, no un tercero idéntico. El bloqueo sólo detiene dependientes.
4. La revisión independiente busca riesgos nuevos del delta y sus interacciones;
   no repite campañas intactas ni vuelve a sellar recursivamente toda la historia.
5. Mantener un resultado por caso/ronda y un registro de defectos en la evidencia
   ya prevista. STATUS conserva el cursor y la autorización actuales; este informe
   no se convierte en una segunda hoja de estado.
6. Avance se informa como casos ejecutados, familias cerradas y defectos pendientes.
   Preparar/aprobar herramientas no incrementa el contador Android. No agregar
   supervisores periódicos para un chat parado por una decisión.

## Cambios y comprobación de este diagnóstico

Sólo se agregó este informe y se actualizó la disponibilidad/cursor documental en
STATUS y la cabecera de Stage12. La cabecera antigua se conserva como antecedente.
STATUS también sintetiza contadores históricos redundantes con su tabla de fuentes
para mantener el límite de lectura de arranque; no cambia los resultados históricos.
Preimágenes de esos dos documentos en `.local-evidence/closure-plan-20261031/`.
Sin cambios de producto, ejecutor, esquema, versión, dependencias o permisos Android.
Sin Gradle, ADB, Samsung, emulador, firma, commit, push ni publicación en esta revisión.

Validación documental: `docs_check.py` conforme, 39840/40000 caracteres de guía,
526 referencias y 103 snapshots históricos verificados por el comprobador.
Diff propio y whitespace revisados contra preimágenes, incluido el informe nuevo.
El primer control excedió el presupuesto; se compactó STATUS sin elevar el límite.
Se usó el Python incluido en el runtime de Codex porque el alias `python` del PATH
no estaba operativo. La revisión independiente del plan detectó y se corrigió el
orden de versionado: metadatos finales antes de las dos rondas, no después.

Siguiente paso recomendado: adoptar la ruta propuesta y ejecutar el bloque 1 en
el mismo Stage12, con el Samsung como entorno principal. No reanudar automáticamente
el canario r9 ni aceptar una ampliación de roles Windows para llegar a ese smoke.
