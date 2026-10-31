# Revisión MAIN de la entrega local de Stage 8

Fecha: 2026-10-21, Argentina. Alcance:
[contrato Stage 8](../prompts/STAGE_8_RENDIMIENTO_RESUMEN_Y_GUARDADO_3_0.md).

**Lectura actual:** el cierre q3 y su revisión están al final. Los apartados
iniciales documentan hitos anteriores, no el cursor actual ni un nuevo despacho.

## Resultado

La entrega local es válida como diagnóstico Android preparado y compilado, no
como corrección demostrada del rendimiento. No cambió código productivo.
Las alertas de Resumen y guardado siguen pendientes de observación física
autorizada. Stage 7, aceptación estética y distribución conservan sus puertas.

## Comprobación independiente

MAIN revisó el harness, el inspector y los documentos; un revisor interno
contrastó en sólo lectura seguridad, paridad y atribución del harness. Su hash
permaneció `ecfd15e18339901688e12c3c460c61e3721d83042a9467a620fcc04c747ce14e`.
El chat propio Stage 8 conservó la implementación; MAIN no lo sustituyó.

Después del relevo y de confirmar cero procesos Gradle, MAIN comprobó:

- Las 978 rutas de entrada: sólo cambió el catálogo existente. Se agregaron el
  harness y los dos documentos de entrega; sin cambios productivos, eliminación
  nueva ni staging. HEAD sigue `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
- Los 103 XML preservados: hashes, casos, fallos, errores y omitidas coinciden
  con las 935 pruebas frescas del especialista, distribuidas 395/19/521.
  MAIN verificó esos resultados, no volvió a ejecutar la batería JVM.
- Los 586 insumos actuales coinciden con los mapas anteriores/posteriores de
  JVM final y build completo. Ambos recibos pasaron sin cambios de insumos.
- Los seis artefactos coinciden por hash/tamaño entre copias preservadas,
  outputs locales y recibo build. Lint conserva 24 advertencias, cero errores.
- MAIN volvió a ejecutar las 38 pruebas del inspector: todas pasaron. Son
  estructuras sintéticas de parser; no prueban el binario ART ni Android real.
- El detector E2E original está intacto. QA/runner R4 y baseline Stage 5
  preservados también conservan sus hashes.

Recibo y comando reproducible de MAIN:
`.local-evidence/stage8/main-review/checks.json` y
`.local-evidence/stage8/main-review/verify_delivery.py`.
La ejecución no usa Gradle, red, ADB ni dispositivos. La revisión estática del
harness no certifica que sus guardas o trazas ya se ejecutaron en Android.

## Hallazgos devueltos al mismo chat

1. El plan físico decía que MAIN autorizaba el ensayo. Se corrigió: MAIN debe
   obtener autorización específica de Joaquín. Un permiso Stage 7 no se hereda.
2. La auditoría atribuía un reintento de lint a dos barras en `sdk.dir`. MAIN
   contrastó bytes y hashes: las tres copias posteriores tienen 49 bytes, una
   barra y el mismo SHA-256; el original tiene 48 bytes y ninguna barra.
   La causa declarada y el nombre del recibo adverso no demostraban ese estado.
   Stage 8 retiró esa afirmación, preservó los originales y rectificó la
   atribución. El reporte lint reutilizado no demuestra una segunda edición.

El [handoff](../HANDOFF_STAGE_8_A_MAIN_3_0.md) y la
[auditoría del especialista](2026-10-21-stage8-rendimiento-resumen-y-guardado.md)
conservan la rectificación y la cadena de evidencia. No se modificó producto
ni se repitió Gradle/JVM para corregir una explicación documental.

## Próximo paso

Obtener autorización específica de Joaquín para instalar exclusivamente QA y
runner técnicos y ejecutar el grupo diagnóstico Stage 8 en datos ficticios
descartables. El ensayo reemplaza datos y preferencias de QA; app pública y
datos personales quedan fuera. Verificar hashes instalados, foco y disponibilidad;
no cambiar ajustes globales del teléfono.

El diagnóstico con muestreo ART perturba tiempos y cachés: sus tiempos no se
comparan con el detector original. Primero observar pilas y esperas; sólo una
causa reproducible justifica corregir producto. Después, detector original
pareado con muestras/umbrales conservados. Ningún resultado local cierra por
sí solo la alerta física.

La supervisión temporal termina al completar esta revisión local y alcanzar
esa puerta. No inicia otra etapa, commit, firma o distribución.

## Continuación, pausa y ajuste acotado del protocolo — 21/10

La frase anterior cierra la revisión local inicial, no describe la continuación
física autorizada después. MAIN despachó el mismo Stage 8 tras auditar Stage 9/R1.
Durante su preflight recibió una restricción posterior de disponibilidad y la
transmitió al mismo operador y supervisor. El permiso QA completo se conservó.

MAIN leyó `.local-evidence/stage8/qa/q1/probe-1792560393858294300.json`: a las
05:26:27 UTC sólo se consultaron identidad/foco/desbloqueo y hashes QA/runner.
Observó Inicio, API36 y APK instaladas anteriores; no acredita candidata instalada.
El operador confirmó cero instalaciones, instrumentaciones, extracciones, trazas,
capturas, entradas UI o lectura de datos; ninguna operación ADB activa al recibir
el HOLD. No se consultó el teléfono para fabricar un estado final tras la pausa.
Su relevo estable está en `q1/writer-release.json`. MAIN no usó ADB en paralelo.

La disponibilidad fue informada nuevamente a las 05:30 UTC; el mismo operador
acusó ese levantamiento y esperó el cierre documental de MAIN, sin otro despacho.
Antes de instalar debe corregir offline un helper local que podía perder códigos
de fallo ADB y no registrar timeout; decisión técnica reversible dentro del ensayo,
no autorización para cambiar producto ni saltar guardas. El visor ART sigue pendiente.

Por pedido de Joaquín se ajustan sólo WORKFLOW, TEMPLATE, contrato de reanudación
y STATUS; esta auditoría registra el delta. Se agrupan permisos concedidos,
pendientes y HOLD, se concreta DONE WHEN y se uniforma final/relevo a MAIN.
Se reutilizan grants completos, con autonomía técnica rutinaria y controles intactos.
No se crea otro marco, operador o supervisor. Originales: `.local-evidence/protocol-20261021/entry/`.

El circuito entrega → hallazgo → mismo Stage 9 → R1 → revisión MAIN ya ocurrió
y tiene evidencia propia; no certifica end-to-end las reglas nuevas. El ajuste
documental, acuses y supervisión única se comprueban separadamente; diagnóstico
Samsung, causalidad y rendimiento siguen pendientes. No JVM/build por documentos.

Verificación del ajuste: revisión independiente documental conforme; los cinco
originales preservados y ningún otro delta respecto del relevo q1. `docs_check`
y `git diff --check` verdes; guía en 39.815/40.000 caracteres. Se encontró un
solo supervisor local pertinente, con el mismo identificador. Recibos y límites:
`.local-evidence/protocol-20261021/checks.json`; el cierre posterior queda en
`final-checks.json`. No se acredita ejecución integral del nuevo protocolo.

## Recepción q2, cierre q3 y auditoría independiente de MAIN

La instrucción directa posterior en Stage 8 autorizó sólo analizar copias y
devolver el handoff, sin nueva QA física ni producto. El mismo chat cerró q3
el 21/10 a las 06:38 UTC y declaró cese de escrituras/Gradle/dispositivo.
MAIN recibió el final por el supervisor existente en el heartbeat 06:42:32;
el aviso de recuperación del coordinador llegó después con la misma entrega.
Se reutilizó esa identidad, no se relanzó Stage 8 ni otra auditoría.
Final 06:38:27 → sondeo 06:42:32: 4 min 05 s dentro de la cadencia de 10 min,
no avería permanente. El sondeo precedió al aviso coordinador 06:44:16;
no constituye un push autónomo del productor.

Acuse durable **RECIBIDO**: `.local-evidence/stage8/main-review/q3/receipt.json`,
registrado 06:46:15 UTC. Clave:
`stage8-q3-065b60481851458722e85eda3f7300bde08aa1d29d3615db3981078c19c123e6`.
MAIN contrastó antes los hashes del sello q3 y del handoff
`7284d10d34ac8df5e92aeb234ebf636d4cb19bb23866e48582941b11aa026027`.
Un archivo/final o aceptación de transporte no bastan para acreditar recepción.

**COMPROBADO por MAIN, sin ADB/Gradle ni escribir evidencia del especialista:**

- 993 rutas de entrada/salida: sólo los dos documentos propios cambiaron;
  las otras 991, la eliminación heredada, HEAD y staging se preservaron.
- Rehash de 149 sellos q2 y 50 q3; los originales de q2 permanecen intactos.
  q2 sí instaló QA `2e00ab…758a3` y runner `40a24a…15472` mediante `-r`:
  recibos y coincidencia instalada/local comprobados, dos selectores aprobados.
  Se extrajeron binariamente dos JSON y 48 trazas; q3 no repitió nada físico.
- Helper q2: revisión estática y repetición independiente de 13/13 pruebas con
  subprocess simulado, sin ejecutar su `main` ni sobrescribir reportes sellados.
- q3: 36/36 fixtures de pila/partición y 14 guardas matemáticas repetidas;
  300 modelos afines racionales adicionales, sin floats ni hipótesis de reloj OEM.
- 48 trazas rehasheadas y 1.153.890 registros contados desde binarios. Base SQLite
  sólo lectura: quick_check OK, 585.526 segmentos, 17.162 abiertos y 528 frames.
  Cuatro trazas seleccionadas (dos operaciones × dos fixtures, iteración 0):
  partición fase/hilo recalculada independientemente, 167 grupos coincidentes.
  Esto no vuelve a certificar todas las categorías de cada registro.
- Dos revisiones internas de sólo lectura, separadas: cotas/reutilización por
  fases y límites causales/callers/contexto histórico. Sin nuevos bloqueantes.

Recibo reproducible MAIN: `.local-evidence/stage8/main-review/q3/checks.json`
y `verify_q3.py`; originales de los seis documentos MAIN guardados en `originals/`.
No se repitieron Stage 9, JVM, build, detector E2E ni QA física por rutina.

### Hallazgo matemático devuelto y corregido

La cota `+999 ns` suponía una granularidad previa entera que no estaba justificada
en el modelo afín. Contraejemplo exacto: duración ART 1000,1 ns, evento 999,9 ns,
extremos host 0/10001 ns; el evento host 9999 escapaba del límite 9991.
El mismo especialista corrigió a envolvente cerrada `+1000`, añadió dos regresiones
con Fraction y regeneró sólo derivados q3. MAIN y revisor contrastaron el arreglo;
las secciones completas de pesos/atribución de las 48 trazas son idénticas entre
r1/r2. La incertidumbre máxima sube 1 ns a 60,950180 ms. Adversos preservados.

### Resultado y gate exacto

**AUDITADO como unidad diagnóstica; R4 diferencial INCONCLUSO.** Las pilas sitúan
UI/observadores después de IDLE y preparación antes. No acreditan SQL exclusivo,
costo exclusivo del driver ni causa de regresión: faltan trazas baseline comparables,
el orden histórico fue ABAB y Resumen también empeoró entre corridas baseline.
No apagar Horas y Extras sin preservar sus consumidores de Calendario.

Un único experimento futuro está definido en el análisis/handoff: baseline/R4
contrabalanceados ABBA, fixture stress y detector original sin trazado; diagnóstico
separado con checkpoints, preflight de artefactos y criterio confirmatorio/refutatorio.
**PROPUESTO, NO EJECUTADO.** Requiere contrato propio de otro stage y decisión
humana QA/dispositivo/datos ficticios. Una asociación no prueba causalidad exclusiva.
No hay autorización actual para ese ensayo, cambiar producto ni distribuir.

### Método y cadena real comprobada

Por pedido de Joaquín se precisaron WORKFLOW/TEMPLATE/contrato/índice y STATUS:
una tarea real por stage, retorno efectivo autorizado, acuse antes de auditar,
recuperación por clave y encadenamiento automático sólo dentro del alcance aprobado.
La recuperación incluye RECIBIDO sin auditoría y AUDITADO sin avance: retomar
revisión y destino existentes; despachar una sola siguiente tarea habilitada tras
resolver gates, guardando clave/chat. Si no existe, registrar BLOQUEADO/CERRADO.
No se añadió watch, daemon, otro supervisor ni garantía exactly-once de transporte.
STATUS actualiza la ruta 1–9; PNG del 02/10 y ruta histórica 2.0 quedan intactos.

| Productor / turno | Transición y evidencia real | Límite |
|---|---|---|
| Stage 9 `01a15cb3-8874-74c0-8303-1a55b687f4f5` / R1 `01a15cde-2a91-70f1-8b49-2d9d5b6f6d56` | Entrega → devolución al mismo chat → R1 → AUDITADO; `stage9/main-review/checks-r1.json` | LOCAL, no Android |
| Stage 8 `01a15c6c-5e1e-7351-a4b2-c90b771aea0b` / q2 `01a15cf6-3df2-73f1-ad94-7b2e14743d86` | Final recibido por heartbeat 06:07 y sello149/149; `stage8/qa/q2/writer-release.json` | No E2E pareado ni causa R4 |
| Mismo Stage 8 / q3 `01a15d10-741c-75d3-ae50-1a29aea9962a` | Final recibido 06:42; recuperación por coordinador con misma clave → ACK durable → AUDITADO; `stage8/main-review/q3/receipt.json` y `checks.json` | No prueba de push autónomo del productor ni cadena nueva completa |

**IMPLEMENTADO:** reglas y acuse local por clave en el mecanismo existente.
**COMPROBADO:** recepción/acuse/auditoría de ESTA entrega, sin relanzar por el aviso
duplicado; corrección rutinaria dentro de la misma unidad. **PENDIENTE:** observar
retorno autónomo de un productor futuro y cadena creación→despacho→entrega→acuse→
auditoría→siguiente tarea autorizada. No crear un stage ficticio para probarla.
Al registrar el gate se elimina el único supervisor temporal; chats y evidencia
permanecen abiertos/preservados. No nuevas consultas al Samsung ni datos personales.

Revisión documental independiente final conforme sobre los seis deltas, sólo lectura.
`docs_check`: verde, 38.883/40.000 caracteres, 449 referencias, 42 ADR y 103 snapshots;
`git diff --check`: salida 0, sólo avisos de finales de línea heredados. Cero procesos
Java Gradle/Kotlin observados al relevo. Sello del delta MAIN y logs en
`.local-evidence/stage8/main-review/q3/final-checks-r2.json`; no commit ni publicación.
