# Continuación de QA de Stage 5

Fecha de cliente: 2026-10-09. Estado actual en [STATUS](../STATUS.md).
Joaquín autorizó retomar las pruebas interrumpidas y los pendientes de Stage 5,
y confirmó Samsung conectado, libre y desbloqueado. Sólo variante QA y datos
ficticios; sin app pública, datos personales, red real, alarmas reales, cambios
globales de Android, firma ni distribución.

## Entrada preservada

Repositorio independiente MiGuardia-3.0, rama codex/miguardia-3.0, HEAD
932eaeb60c6b6e68975534788e3e6501c967b1ae; upstream origin/codex/miguardia-3.0.
Un worktree, .git propio y contenedor sin .git. Staging vacío. Entrada preservada
en `.local-evidence/stage5/resume-20261009/entry/`: 949 rutas con hashes,
patch recibido y siete archivos críticos copiados. Son 167 entradas con -uall,
166 con el agrupamiento normal de carpetas nuevas; no un cambio concurrente.
MiGuardia-2.0 conserva siete cambios preexistentes; no se escribe ni integra.

El [cierre del 04/10](2026-10-04-stage5-qa-final-y-rendimiento.md) conserva sus
925 JVM, 26 interop, 69 métodos físicos y 48 combinaciones, sin reasignarlos.
La continuación posterior del mismo día modificó exclusivamente tres callers
Android y sus hashes del catálogo: AvailabilityActivityTest, HoursAndExtrasActivityTest
y V2ShiftActualActivityTest. Se cambió performScrollTo por assertIsDisplayed
al tocar una fecha del calendario estático; no se relajaron datos esperados,
recreación, persistencia ni aserciones posteriores.

Los seis fallos previos eran de preparación: intento de scroll sin ancestro
desplazable. Recibos 20261004T195731.461225Z, 195831.669063Z y 200003.976555Z
bajo `.local-evidence/qa/`. El siguiente 200705.979143Z se detuvo por foco:
cero resultados, ocho casos pendientes. No prueba un fallo de negocio.

## Build y paquetes

Build aprobado y estable en
`.local-evidence/stage5/continuation-20261004/build-static-oracles/result.json`.
El 09/10 se verificaron todos sus insumos Android y artefactos: sin diferencias.
No se recompila ni repite JVM por rutina; el cambio recibido es sólo de pruebas
Android y su catálogo. Catálogo válido: 116 clases, 26 grupos.

Samsung SM-S938B/API 36, serial [serial QA en binding local privado]. El probe nuevo verifica identidad
local/instalada inicial de QA 223f98…bab8, runner app 78133f…65b3b y runner base
493cbf…1059. App pública be3bca…2f94 consultada sólo por metadatos/hash,
sin abrirla ni leer sus datos. Probe fechado en la carpeta resume-20261009.

## Ejecución y límites

Se retoman primero los tres recorridos Activity, incluyendo alta, recreación,
corrección, guardado y reapertura con Room real. El wrapper utiliza el runner
oficial, exige identidad instalada y conserva un guard de primer plano/keyguard;
ante interferencia detiene QA sin capturar ni conservar salida ajena.
Primer recibo nuevo: `.local-evidence/qa/20261009T183106.073930Z`, QA 223f / runner
7813. Disponibilidad 0/3, con tres fallos en preparación esperando
`availability-add-fecha`; extras y horario real no ejecutados (tres pendientes).
Guard activo: 57 comprobaciones, sin interferencia de foco.

La selección mensual ya llegó al detalle. La causa siguiente es otro caller
obsoleto: la alta contextual ahora está en `day-other-options` →
`day-add-retainer`, y los controles de extras también están en ese menú.
La sección genérica de disponibilidad sólo se compone si ya existen ventanas.
Se actualizan ambos recorridos para reabrir el menú después de cada retorno,
sin relajar recreación, datos, Room ni tiempos. La entrada por aviso verifica
la fecha dueña visible. Se agregan aserciones del nombre histórico Retén y de
configuración laboral intacta al corregirlo. No se modifica producto.
El cierre posterior de ambos callers está registrado más abajo; el fallo inicial
se conserva y compilar por sí solo no equivale a aprobar QA.

Después se revisan los consumidores y filas pendientes de la
[matriz Stage 5](2026-10-02-stage5-matriz-recorridos.md), preservando las puertas
supervisadas de OEM, permisos, navegador, SAF, bloqueo y revisión humana.
Los dos pares de estrés anteriores permanecen completos y contradictorios
respecto de una mejoría causal: 4/9 señales y 0/9 en orden inverso.
No se repite rendimiento ni se optimiza código sin un riesgo reproducible nuevo.

No commit, push, tag, firma, publicación ni aceptación global.

## Preparación posterior al fallo

Build `resume-20261009/build-current-callers/result.json` aprobado: lint sin
errores, 24 warnings, seis artefactos e insumos estables. Runner app generado
`da318a87bb202bc99b72501eba7c1eccbca5571f191a895336766646bcf6ac94`.
QA 223f, Debug, Release unsigned y runner base se reutilizan sin diferencias;
no son APKs de producto nuevas. No se repiten JVM/interop por cambiar callers
Android: siguen como evidencia histórica, no ejecución nueva.

El primer guard previo a instalar rechazó el estado de pantalla/keyguard.
No se instaló ni instrumentó en ese intento. Tras la nueva confirmación de
disponibilidad se instaló exclusivamente el runner app da318a. QA 223f y pública
be3bca no fueron reemplazadas. Los probes nuevos verifican los cuatro hashes
locales/instalados; la pública sólo por metadatos, sin UI ni datos personales.

Se revisaron subsets sintéticos para Resumen, repetición, Widget, Vacaciones,
Informes, Copias, Avisos, Clima, bloqueo y enlaces/actualizaciones interceptados.
El wrapper permite sólo clases revisadas, no grupos completos por inferencia.
Las pruebas de pantallas con dobles no equivalen a un recorrido integral con
Room, ni a OEM, red, biometría, alarmas reales o aceptación humana.

## Resultados físicos nuevos

Todos bajo `.local-evidence/qa/`, con QA 223f y runner app da318a:

| Recibo | Resultado y alcance |
|---|---|
| 20261009T184549.378620Z | Disponibilidad 3/3 y extras 2/2. Horario real quedó pendiente por corte de foco; cero métodos fallidos. No es un recibo global verde. |
| 20261009T184717.881479Z | Horario real 1/1: rotación, recreación, corrección, persistencia, reapertura y vuelta al planificado. |
| 20261009T184745.582881Z | Sin resultados, siete pendientes. Ventana sin foco con actividad QA reanudada; corte conservado para diagnóstico. |
| 20261009T184843.549356Z | Resumen 1/1, repetición 1/1 y configuración Widget 5/5, siete métodos distintos. |

Primer bloque: **13 métodos físicos distintos aprobados**, cero omitidos en esos métodos.
La configuración Widget usa host de prueba e instancia propia, no el launcher OEM.
No se atribuyen los resultados históricos al runner nuevo ni se acepta una fila
integral por ejecutar algunos de sus subcasos.

La disponibilidad contextual conserva etiqueta Retén al corregir, configuración
global PASSIVE_GUARD intacta y regreso a la fecha dueña. Extras conserva puesto
tras recrear y corrige/elimina en Room. Sus aserciones no fueron relajadas.

## Control de primer plano

El segundo corte demostró `mCurrentFocus=null` con actividad QA reanudada durante
recreación. Otros cortes demostraron la misma ventana nula al pasar de Inicio a
una nueva Activity QA y al cerrar una regla Compose hacia Inicio. El helper local
reconoce esas transiciones sólo sin bloqueo, con dueño previo aprobado y dos
confirmaciones coincidentes: actividad reanudada y mFocusedApp exclusivamente QA
o exclusivamente Inicio. Inicio sólo se admite durante la instrumentación propia
activa; fuera de ella se conserva la exigencia QA→QA. Un corte posterior identificó
una ventana del propio runner QA.test, no otra app. Sólo se reconoce durante la
instrumentación activa, con dueño previo QA, sin bloqueo y ambas confirmaciones
QA; no se añade a la lista permisiva general. No usa gracia temporal, esperas
mayores, permisos ni paquetes ajenos nuevos. Ventana ajena, dueños mixtos/ausentes
y bloqueo siguen rechazados. Doce pruebas Python puras cubren la política final; recibo en
`resume-20261009/physical-inventory.json`. Los cortes previos no se borran ni se
presentan como verdes. La causa exacta del primer corte 184549 sigue sin atribuirse.

Después del bloque verde, el preflight siguiente sí observó dueño, actividad y
app enfocada ajenos, sin bloqueo. Se detuvo antes de instrumentar y se pidió
QA/Inicio. Sólo se conservan categorías redactadas, nunca identidad ni UI ajenas.
Joaquín confirmó nuevamente esa disponibilidad antes del bloque siguiente.

## Continuación de superficies e integridad

Con QA 223f y runner app da318a; base 493cbf para SQLite. Todos los recibos
están bajo `.local-evidence/qa/`. Un resultado parcial por foco no se transforma
en aprobación global; sólo se cuentan métodos efectivamente cerrados.

| Recibo | Resultado y límites |
|---|---|
| 20261009T185426.581508Z | Informes Compose 4/4 y bloqueo Compose 4/4; otros 45 pendientes por foco. Ventana ajena con actividad/app enfocada QA; identidad desconocida, no causa atribuida. |
| 20261009T185530.876363Z | Apoyar MiGuardia 9/9, enlaces interceptados, doble toque, animación, retorno/estado y temas/zoom. |
| 20261009T185555.896588Z | Actualizaciones menú 9/9 y retorno 3/3; 24 pendientes por transición Inicio→QA sin ventana enfocada. Sin red ni navegador real. |
| 20261009T185709.577733Z | Vacaciones Compose 6/7; fallo de etiqueta obsoleta descrito abajo. Otros 17 pendientes, no ejecutados. |
| 20261009T185754.029515Z | Vacaciones ViewModel 5/5 con dobles/SavedStateHandle; Clima pendiente por transición Inicio→QA. |
| 20261009T185838.296036Z | Clima Compose 12/12, activación consciente, errores, atribución, jornada nocturna, temas/zoom; sin red ni permiso real. |
| 20261009T185904.983256Z | Copias: cero resultados, 14 pendientes por transición QA→Inicio con ventana nula. |
| 20261009T185942.378957Z | Copias 14/14: UI con dobles 10 y publicación de documento sintético 4, cifrado/sin contraseña, fallo y limpieza segura; no selector SAF. |
| 20261009T190014.897273Z | Informes 21/21: ViewModel 6, destino 5, PDF 4, artefactos 3 y fotos preparadas 3. Ficheros sintéticos aislados, fallos/limpieza/paginación/privacidad; no documento personal ni chooser OEM. |
| 20261009T190027.827652Z | Vacaciones SQLite 10/10 y snapshot mensual 5/5: rangos/CAS/reapertura y lectura sin mutar; bases de prueba aisladas. |
| 20261009T190042.765209Z | Avisos Compose 8/8, enlaces 3/3 e infraestructura Clima 9/9; Widget 7 pendientes por dueños QA/Inicio distintos. El guard rechazó correctamente la mezcla. |
| 20261009T190116.186901Z | Widget Compose 7/7, sin launcher OEM ni alarma real. |

### Etiqueta de Vacaciones: causa y corrección proporcional

El caso `calendarShowsVacationWithShiftAndExplainsExcludedHoursInDetail` falló
en la línea 212 buscando `Vacaciones:`. La explicación de exclusión de horas
ya había pasado. El detalle vigente usa `Vacaciones · DD/MM/YYYY – DD/MM/YYYY`
en Condición del día, coherente con la composición por tarjetas autorizada.
No se cambia producto para satisfacer un texto antiguo: la prueba ahora exige
el rango ficticio exacto `13/08/2026 – 15/08/2026`, lo lleva al viewport y comprueba
que se vea. Conserva la explicación de horas y la jornada original en proyección.
Original preservado en `caller-addendum/`, con hash coincidente con la entrada.

El primer build de esa corrección falló por un import omitido de assertIsDisplayed;
se corrigió el import y se conserva `build-vacation-oracle/result.json` como fallido.
No se instaló ese intento. `build-vacation-final/result.json` aprobó build/lint,
seis artefactos e insumos estables, 335 tareas (ocho ejecutadas). Sólo se regeneró
el runner app; APK Debug/QA/Release unsigned y runner base se reutilizaron intactos.
No se ejecutaron JVM ni interoperabilidad de nuevo por este cambio Android.

## Cierre de esta continuación

Runner app final instalado/verificado:
`b96ba8f410c4bc579c5eba8a2d151e55effa46ac7df34b2f1df5407be1368770`.
QA producto conserva `223f98d97f0a81ac67476dab8d5323aee81c0a00dd2ec102493c703eb11ebab8`.
La primera ejecución final de Vacaciones, 20261009T190816.822191Z, dejó siete
pendientes por la ventana QA.test descrita arriba; no falló un método. Tras
corregir únicamente el guard local, 20261009T190854.105427Z cerró **7/7** con la
etiqueta/rango visibles y la explicación de horas intacta. 20261009T190907.445320Z
cerró **1/1** de ubicación contextual: opciones conscientes según dirección y
retiro confirmado con dobles, sin conceder permisos ni obtener ubicación real.

Inventario automático: **144 métodos distintos aprobados**, 27 clases completas
y un método revisado de otra clase. Cruza método y hash de fuente actual contra
cada recibo; no suma repeticiones, fallos, pendientes ni evidencia del 04/10.
Los 136 anteriores conservan runner da318a (base 493cbf para sus 15 casos);
los últimos ocho pertenecen a b96ba8. La APK productiva es idéntica en todos.
No se afirma que los 144 hayan corrido sobre b96ba8 ni que acepten filas enteras.

Probe posterior verifica los cuatro paquetes: QA/runner app/runner base coinciden
con los locales; pública conserva `be3bca…2f94`, sin UI, instrumentación o lectura
personal. QA quedó abierta mediante su MainActivity; recibo `opened-qa-*` local.
Delta propio: tres callers Android, catálogo y cuatro documentos. Entrada y
adición de Vacaciones preservadas; cero cambios productivos, staging o commits.

## Entrega y límites

Catálogo válido: 116 clases y 26 grupos. docs_check aprobado y git diff --check
sin errores. `resume-20261009/sealed-final/` conserva el primer corte local sin
físicos nuevos; no se sobrescribe. `sealed-physical-final/` separa estos recibos,
helper y documentos del checkout acumulado y del cierre del 04/10, con hashes
y patch propio reversible. Los recibos fallidos/interrumpidos permanecen íntegros.

Próxima puerta: revisión humana de QA y subcasos integrales aún no cubiertos.
Calendario/legibilidad, teclado, selector de imágenes/SAF/chooser OEM, biometría,
permisos/red, alarmas, TalkBack y aceptación conservan sus puertas. Las pantallas
con dobles y archivos aislados no sustituyen esos recorridos. No commit ni distribución.
