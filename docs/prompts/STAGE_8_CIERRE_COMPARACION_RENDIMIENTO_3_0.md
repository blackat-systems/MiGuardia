# Stage 8 — cierre mediante comparación de rendimiento

## TASK — objetivo único

Resolver la alerta pendiente de Resumen y guardado con una comparación controlada
de las dos versiones QA conservadas. Entregar a MAIN un resultado auditable:
si la diferencia se reproduce, cuánto cambia al invertir el orden y qué causa
puede sostenerse realmente. No dar por corregida una alerta sólo porque una
corrida resulta favorable. Este encargo no incluye una optimización nueva.

## CONTEXT — decisión de Joaquín y límites

El 22/10 Joaquín pidió terminar Stage 8 antes de continuar otras tareas. MAIN
explicó que la comparación instala temporalmente las dos versiones QA y su
ejecutor, vacía y regenera exclusivamente los datos ficticios de QA, conserva la
evidencia de Stage 10 y rompe la continuidad de su estado instalado. Ante esa
pregunta concreta, Joaquín respondió: «Finalicemos stage 8».

Esa respuesta habilita esta comparación, sin repetir la pregunta de alcance.
No autoriza desinstalar, borrar la app pública, leer datos personales, modificar
ajustes de Android ni publicar. La disponibilidad y el foco actuales del Samsung
se verifican antes de operar. Si fallan, pedir solamente la intervención concreta.

Stage 10 está pausado y liberó recursos. Sus archivos y resultados quedan intactos;
después de reemplazar los datos ficticios no se podrá afirmar que conserva el
mismo estado instalado. No restaurar su instantánea ni reanudarlo por inferencia.
El diagnóstico q3 de Stage 8 ya está auditado como inconcluso: no repetir su
análisis, reescribir sus sellos ni atribuirle esta nueva prueba.

Se usa el mismo chat Stage 8, no otro implementador. Por indicación actual de
Joaquín, esta tarea acotada completa su cierre pendiente; no inicia una sucesión
indefinida de experimentos. MAIN recibe y audita, sin usar el teléfono en paralelo.

## INPUTS — entrada comprobable

- Repositorio: `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`.
- Rama: `codex/miguardia-3.0`; HEAD: `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Checkout acumulado modificado, staging vacío. Preservar cambios y eliminación
  heredada; no atribuir todo el diff contra HEAD a este encargo.
- Chat Stage 8: `01a15c6c-5e1e-7351-a4b2-c90b771aea0b`, host local.
- MAIN: `01a09739-5279-75d0-a3b1-84bd2af6b36a`, host local.
- Clave de despacho: `stage8-cierre-comparacion-20261022`. Consultar evidencia
  y estado antes de repetir una operación cuyo resultado sea incierto.

Lectura inicial: AGENTS del contenedor y repositorio, STATUS, este contrato,
WORKFLOW y VALIDACION. Después sólo el apartado final q3 de la auditoría de
Stage 8, la revisión MAIN y el protocolo de rendimiento de Stage 4. Las fuentes
históricas describen aquel ensayo: no conceden permisos actuales.

### Artefactos inmutables para comparar

| Insumo | Archivo relativo al repositorio | SHA-256 |
|---|---|---|
| A: base Stage 5 | `.local-evidence/stage7/qa-main-20261011/artifacts/stage5-baseline.apk` | `6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3` |
| B: candidata R4 | `.local-evidence/stage7/android-font-20261011/artifacts-final-r4/app-qa.apk` | `99e32ed7525b23ac5452d8eebab019f7051c2f7e6e50dc47db9a2e918dd4dc29` |
| Ejecutor E2E original | `.local-evidence/stage7/android-font-20261011/artifacts-final-r4/app-qa-androidTest.apk` | `bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c` |
| QA actual para devolver al finalizar | `.local-evidence/stage9/preserved-artifacts/app-qa.apk` | `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3` |

Detector: `app/src/androidTest/java/com/blackatsystems/miguardia/Stage4FlowsActivityTest.kt`,
SHA-256 `3331ede6d038657939629a5a9a9bb86f33d246b2314cb8a144f160d5b7f00ad7`.
Selector único E2E: `com.blackatsystems.miguardia.Stage4FlowsActivityTest#stressHistoryMeasurements`.
El grupo completo tiene otros ensayos: no ejecutarlos por rutina.

El ejecutor diagnóstico candidato está preservado por Stage 9, SHA-256
`40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472`.
Resolver su ruta desde el manifiesto, comprobar compatibilidad con ambos APK
sin modificar producto y distinguirlo del ejecutor E2E.

Samsung previo: SM-S938B/API 36, serial `[serial QA en binding local privado]`; revalidar identidad.
Paquetes permitidos: `com.blackatsystems.miguardia.qa` y
`com.blackatsystems.miguardia.qa.test`. La existencia histórica del paquete
público no es un requisito: no abrirlo, instalarlo ni consultar sus datos.

Checkpoint de pausa Stage 10:
`.local-evidence/stage10/criterion-continuation/pause-checkpoint-20261022/checkpoint.json`,
SHA-256 `a780173186c968bfe9f74daf1d7f67fd9d9e1205ab48b1f4c505591afdfc6ac4`.
MAIN contrastó sus 13 fuentes sin diferencias antes del despacho. No escribir
en esa carpeta ni tocar las entregas q2/q3 de Stage 8.

## OUTPUT — entrega

1. Cuatro corridas E2E completas y comparables, o evidencia concreta del paso
   que impidió completarlas. Nunca convertir resultados parciales en verdes.
2. Comparación de las nueve operaciones; énfasis en Resumen y guardado.
   Separar los dos pares, el efecto del orden y cualquier dato diagnóstico.
3. Conclusión clara: alerta reproducida, no reproducida en esta campaña o
   resultado inconcluso; causa comprobada frente a hipótesis. No afirmar que
   una diferencia histórica fue corregida sin intervención y prueba causal.
4. Evidencia nueva bajo `.local-evidence/stage8/closure-20261022/`: entrada,
   operaciones previstas/intentadas, comandos, códigos y fallos lógicos,
   hashes locales/instalados, muestras binarias, comparaciones y salida.
5. Crear `2026-10-22-stage8-comparacion-cierre.md` dentro de `docs/audits/` y
   sección inicial nueva en `docs/HANDOFF_STAGE_8_A_MAIN_3_0.md`. No reescribir
   los resultados anteriores. Informar cambios propios y pruebas no repetidas.
6. Handoff y liberación explícita de escrituras, Gradle y teléfono. MAIN acusa
   recepción y verifica resultados antes de resolver el cierre.

## SCOPE — ejecución acotada

### Preparación y seguridad

- Comprobar entrada, artefactos, versión, certificado técnico y compatibilidad
  del ejecutor con ambos APK. No compilar si los insumos preservados bastan.
- Usar el entorno de sesión documentado en
  `.local-evidence/structure/20261020/Enter-MiGuardiaEnvironment.ps1`.
  No descargar ni instalar herramientas nuevas.
- Reutilizar lógica comprobada del ejecutor oficial y helpers q2. Un helper
  nuevo debe probarse sin dispositivo frente a fallo lógico, código no cero,
  timeout, identidad errónea y éxito antes de usarlo. No importar scripts con
  acciones de dispositivo al nivel superior.
- `performance_r2.py` histórico aporta validación de muestras y percentiles,
  pero su operación exige una app pública antigua: no ejecutarlo tal cual.
  Adaptar sólo coordinación local nueva, sin bajar guardas ni alterar el detector.
- Antes de cada operación, confirmar Samsung correcto, desbloqueado y QA o
  Inicio al frente. Si aparece otra app, detener sin capturar ni interactuar
  con ella. Confirmar procedencia ficticia de los datos QA; ante duda, no borrarlos.
- Instalar exclusivamente mediante reemplazo conservador `install -r`.
  Si falla, guardar el intento y detener ese paso: no usar desinstalación,
  `pm clear`, eliminación de archivos ni otra salida destructiva.

### Comparación principal

Ejecutar **A1 → B1 → B2 → A2**, con el mismo ejecutor E2E original y sin trazado
de métodos. Son dos pares en órdenes opuestos, no una sola muestra combinada.
Cada corrida mantiene 1.920 jornadas, 192 notas, 2.920 días, 12 iteraciones,
nueve operaciones y tres jornadas agregadas por iteración. La preparación del
detector vacía tablas y sustituye preferencias QA; eso está dentro del permiso
específico actual. No añadir limpiezas externas.

Guardar y extraer cada resultado antes de la siguiente corrida para impedir que
se pierda al regenerar datos. Usar extracción binaria limitada del archivo
sintético esperado, con hash y vínculo al recibo; nunca galería/copias personales.
Registrar mes ancla, hash lógico inicial, cantidades, tema, configuración de
letra/densidad observada y ventana. Los cuatro brazos deben ser comparables.
El valor legado de zoom 100 es metadato de la fixture, no una restauración del
zoom interno del producto. No cambiar ajustes globales para igualar el pasado.

Regla previa inalterable: percentiles nearest-rank; con n=12, p50 es la sexta
muestra ordenada y p95 la máxima. Investigar delta p50 mayor a
`max(20 % del baseline, 100 ms)` o delta p95 mayor a
`max(25 % del baseline, 150 ms)`. Comparar B1 contra A1 y B2 contra A2;
no diluir alertas promediando pares. Preservar las 12 muestras y todos los
fallos, incluidos máximos y primera iteración.

### Diagnóstico separado, sólo si aporta a la alerta reproducida

Si la diferencia persiste en los dos órdenes, completar un par diagnóstico
A/B con checkpoints equivalentes y el ejecutor preservado compatible. No usar
sus tiempos para aplicar umbrales E2E. Si no es compatible, informar el límite
con evidencia, sin modificar producción para hacerlo compatible ni repetir
otra campaña. Reutilizar el análisis local ya auditado, con sus límites.

Hipótesis falsable: R4 añade trabajo de UI en Calendario/Resumen durante la
navegación y propagación. Una diferencia E2E persistente y mayor CPU muestreada
en esas fases la favorece; desaparición/inversión por orden o costos equivalentes
la debilitan. Muestreo, fases abiertas y relojes ambiguos impiden atribución
exclusiva. No convertir los ~950 ms posteriores a IDLE en SQL o costo del arnés.

Al terminar, devolver mediante `-r` el APK QA actual indicado, sólo si las
guardas permiten operar, y verificar su hash. Mantener los nuevos datos sintéticos;
no restaurar silenciosamente la fixture de Stage 10. Registrar qué ejecutor
queda instalado y la pantalla final. Esto no retoma ni acredita Stage 10.

## DEPENDENCIES — coordinación

- Stage 10 pausado; un solo operador físico: este mismo chat Stage 8.
- MAIN reserva STATUS, índice, contratos y revisión propia. El especialista
  escribe únicamente helpers/evidencia nuevos y sus dos entregas indicadas.
- No volver a pedir el permiso concedido. Una guarda fallida sí requiere la
  intervención concreta del usuario; conexión antigua no prueba disponibilidad.
- MAIN revisa un checkpoint estable, no una edición transitoria. Las correcciones
  de validez dentro de esta campaña se devuelven al mismo chat.

## DO NOT — exclusiones

- No cambiar producto, pruebas del detector, Room, migraciones, Copias, CAS,
  validación de integridad, permisos, fuentes, versión, firma o dependencias.
- No retirar funciones, observadores, controles de ciclo de vida o sincronización
  para obtener números mejores. Una optimización posterior exige causa y alcance.
- No datos personales, app pública, otros paquetes, ajustes globales, emulador,
  servicios remotos, publicación, staging, commit, push, ramas ni worktrees.
- No repetir desinstalaciones anteriores, abrir otro chat, crear supervisor,
  reiniciar Stage 9/10, sobrescribir recibos o restaurar outputs históricos.
- No nuevos experimentos indefinidos ni repetir baterías JVM/build válidas por
  documentación o uso de APK preservados. No declarar aceptación global/visual.

## VALIDATION — comprobación

- Hashes y paquetes verificados antes/después; fuente del detector y catálogo
  estables. Resultado Android real exige protocolo completo, no sólo exit code.
- Tests offline de helpers nuevos y validación de rechazo; sin ADB en esos tests.
- Recibos vinculados a 12 muestras por operación, nueve operaciones, cuatro
  corridas; condiciones iguales y percentiles recalculables por MAIN.
- Comparación lógica de conteos iniciales/finales; datos sintéticos preservados
  en evidencia cuando corresponda. Integridad no se infiere de rendimiento.
- `python tools/docs_check.py`, `git diff --check` y revisión del delta propio.
  Catálogo sólo comprobar si se usa/modifica; no alterar sus fuentes por artefactos.

## DONE WHEN — condición de entrega

La campaña tiene una conclusión reproducible o un límite concreto sustentado,
sin confundir ejecución satisfactoria con rendimiento aceptable. MAIN recibe
evidencia y relevo, audita porcentajes/umbrales e invariantes y registra qué parte
de Stage 8 puede cerrar. Si persiste una alerta sin causa suficiente, declararla
pendiente: no inventar corrección ni encadenar otro experimento por cuenta propia.

Responder en español claro: qué se hizo, qué pasó, qué queda y qué recursos
quedaron libres. Publicar el handoff en este mismo chat; MAIN lo recibe por
seguimiento. No hace falta un mensaje de retorno ni otro chat.
