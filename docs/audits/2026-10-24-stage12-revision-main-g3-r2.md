# Stage12 — revisión MAIN del primer arranque G3-R2

## Dictamen vigente del 24/10/2026

El último ensayo `7736b5b8…` fue rechazado por una conexión IPv6 no-loopback
del QEMU propio, con destino443/estado5. No se demostró propósito, dominio,
callsite ni origen guest/host. Alcanzó registro explícito5589; no propiedades,
bootcomplete, sync o QA. El fallo sigue preservado.
MAIN auditó la custodia: HELD tras errores5 de cierre, única orden por el mismo
PTY, cierre posterior confirmado, reserva liberada última y puertos libres.
Acumulado AVD6servidores/4launchers/4QEMU; bootAVD no confirmado.
En Samsung, diagnóstico auditado129PASS/2FAIL y una tanda incompleta preservada;
0rondas finales. No derivar441IDs del recuento de métodos.
Las seis admisiones SDK y el canario de sockets están consumidos. La revisión
local acotada terminó sin perfil ejecutable acreditado y su automatización
fue eliminada. Posteriormente Joaquín autorizó continuar el MISMO Stage12 en
Samsung usando esta PC, aislamiento/conservación primero y luego pruebas/dos
rondas finales. MAIN actualizó el contrato y Stage12 acusó101 en el turno
01a170a6-135f-7262-93d6-fd7d47e3606f. Preparación PRE-install activa; una aclaración humana posterior autoriza abrir
y usar normalmente la QA instalada sin baseline universal previo. Nueva
candidata/runner todavía requieren revisión pertinente antes de reemplazo. El gate del emulador se conserva;
no se necesita resolverlo para continuar ahora por Samsung.

## Primer dictamen y admisión históricos del 24/10/2026

Se admite un primer arranque técnico acotado del AVD propio, con el operador
corregido `81f98041…`, helper `0e4eee30…` y checkpoint-r2 `839ba204…`.
La admisión no demuestra ejecución: Android sigue en 0/441 puntos y no habilita
instalación, instrumentación, negocio ni capturas. Samsung continúa diferido.

La autoridad es la orden humana del 24/10 y su adenda vigente al contrato Stage12.
El requisito previo de prevención universal quedó sustituido expresamente por
el modelo de SDK confiable privado; no se observó un escape ni se acredita un
sandbox de red/USB. El Job prueba pertenencia y cierre de procesos propios.

## Evidencia comprobada independientemente

MAIN rehasheó 75/75 miembros (9.933.025 bytes), 28 pins y cuatro referencias;
387 archivos tanto del SDK original como de su copia (1.121.613.286 bytes cada
uno), 21 archivos de imagen (2.049.726.687 bytes) y los 618 insumos Android.
No hubo diferencias, extras ni staging. HEAD/rama permanecen en la base prevista.

Dos lectores independientes revisaron Job, identidad nativa, protocolo, rutas,
servidor y cierre. MAIN repitió 68 pruebas puras con llamadas reales a procesos,
sockets y DLL nativas bloqueadas: todas verdes, cero intentos reales.

La revisión detectó dos problemas del operador, devueltos al mismo Stage12:

- El catch de `first_boot` descartaba código/error/recibo nativo. Ahora conserva
  `exception_record` completo en resultado y diario; una regresión ejercita la
  orquestación real con JobError y cierres independientes, incluido un cierre fallido.
- El token estaba fijado en AVDuser pese a HOME privado distinto. Ahora la ruta
  anunciada debe coincidir exactamente con uno de dos candidatos privados;
  se rechazan rutas ajenas, ambiguas y reparse antes de leer. No se registra el token.

MAIN verificó el delta de 18 archivos/253.628 bytes, sus 32 pins y cuatro
referencias; los 75 miembros anteriores permanecen intactos. Comparó AST:
únicamente cambian `boot_spec`/`first_boot` y se agregan tres helpers del token.
Repitió 40 pruebas actuales del driver: cero fallos, errores, omitidos o llamadas
reales. Un lector independiente cerró ambos hallazgos y cesó su revisión.

Los cuatro canarios host R3 acreditan versión, WHPX y ayuda GPU, con cierre de
Jobs; pertenecen a su fuente original. No se repitieron por los cambios exclusivos
del primer arranque. R0/R1/R2 siguen fallidos; la diferencia Win32/nativa observada
en crashpad no explica la causa interna de Windows. No se repitieron JVM ni builds.

## Alcance siguiente y trazabilidad

Una sesión: servidor privado 5048, motor/endpoints propios, registro explícito,
inventario/selección únicos, propiedades y sync, cierre autenticado y conservación.
La prueba de endpoints precede al registro explícito del controlador; no impide
el autorregistro nativo al servidor privado. La red remota no se declara confinada.
Ante un fallo se conservan parciales y se cierran sólo recursos propios, sin reintento.

Recibos MAIN en `.local-evidence/stage12/main/`:
`g3-r2-{received,payload-review,risk-review}.json`,
`g3-r2-main-fixes-{received,delta-review}.json`,
`g3-r2-first-boot-admission.json` y resultados de pruebas puros asociados.
Entrega original: `Stage12:G3-R2:20261024:e05ea50c5f3cefe4`.
Delta: `Stage12:G3-R2-MAIN-FIXES:20261024:e9857f3975b2eb84`.

Después del recibo real de boot se revisarán PRE-install, bootstrap/aislamiento,
familias y dos rondas finales sobre la misma candidata. La matriz completa y los
subcasos Samsung/OEM/humanos conservan sus estados y límites; no hay cierre integral.

## Resultado real posterior: detenido antes de lanzar el emulador

La admisión `eec7482b…` quedó consumida en el run
`d9d6c995-3f3c-4562-ae57-8bb8114ea302`. Se inició el servidor privado una vez;
la primera guarda rechazó dos miembros del Job donde esperaba uno. No se enviaron
version/inventario ni se lanzaron VM, instalación o instrumentación. El ADB raíz
quedó identificado por handle, ambas APIs nativas, metadatos y hash; no se guardó
la identidad del miembro adicional antes del assert. Su causa sigue desconocida.

MAIN rehasheó siete archivos/15.171 bytes del adverso sellado `64531c04…`, leyó
eventos/resultado/cleanup y contrastó el inventario posterior de imagen con el
original archivado. Ambos Jobs registran vacío y handles cerrados, sin errores
o capacidades pendientes; puertos reservados libres. No se consultaron PIDs ya
cerrados ni se repitió el ensayo. El dictamen real es FAILED antes de VM, no boot.

Se permite preparar localmente un diagnóstico mínimo server-only en otra raíz,
con wrapper y pruebas puras que conserven la lista ya medida antes de rechazar,
sin relajar la guarda ni alterar el operador histórico. Requerirá nueva admisión
técnica exacta antes de iniciar otro servidor; no concede VM ni transportes.
Recibos MAIN: `g3-r2-firstboot-adverse-review.json` y
`server-members-diagnostic-preparation.json`. La supervisión continúa porque
queda trabajo local seguro habilitado dentro del mismo Stage12.

## Diagnóstico de miembros y política de consola OS

El diagnóstico server-only posterior conservó dos miembros: ADB raíz y una ruta
nativa `Windows/System32/conhost.exe`, concordante entre dos APIs sobre miembro
vivo del Job. No midió su padre ni ligó retrospectivamente sus bytes DOS al PID.
La política estricta lo rechazó; la admisión `0942b3dc…` quedó consumida y el
recurso cerrado. MAIN verificó 12 archivos/41.094 bytes del diagnóstico y sus
117 miembros precedentes. No hubo frames ni VM en ese diagnóstico.

Stage12 preparó una política específica `OS_CONSOLE`. MAIN verificó 34 miembros/
703.189 bytes, 32 pins, cuatro referencias y 129 miembros anteriores. Releyó
independientemente el archivo DOS exacto: SHA `c8614618…`, 1.003.520 bytes,
dos enlaces, volumen/FileID/mtime/atributos/NT estables antes y después, iguales
al pin. El requisito SDK de un solo enlace conserva su implementación original.
La consola OS nunca acredita raíz ADB, motor ni transporte, y cualquier fila
TCP suya observada se rechaza. No se adopta una familia genérica de archivos OS.

MAIN repitió 60 pruebas puras del driver `502670a5…`, con bloqueadores de
procesos/sockets/DLL: cero fallos, errores, omitidas o llamadas reales. Incluyen
el recorrido completo simulado y adversos de fases, peers, identidad y cierre.
Se conservaron las dos pruebas rojas y su atribución a fuentes anteriores.
Dieciséis superficies y la rama de cierre exitoso mantienen AST equivalente;
helper `0e4eee30…` permanece intacto. Un lector independiente confirmó el alcance.

El servidor SDK confiable conserva identidad, argv/entorno privados, propietario
exclusivo de 5048 y captura TCP. Su guarda no rechaza automáticamente un peer
ajeno o conexión a 5037 del root ADB; ese control automático se aplica a las
conexiones del emulador. No se amplía la garantía a confinamiento universal.

## Ensayo eb4e: QEMU ejecutado, Android no confirmado

La nueva admisión `eb4e3390…` quedó consumida una vez en el run
`beac6bc2-fedb-4ce8-940c-3a8e99af8864`. Se acreditaron ADB privado y consola OS
en ambos Jobs. El controlador completó versión e inventario vacío; se ejecutaron
launcher, motor QEMU headless y `qemu-img` propios con sus pruebas de archivo.
La captura encontró un quinto miembro vivo del Job: PID 15656, FILETIME
134373151642085939, con dos APIs concordantes sobre el NT de 48 caracteres de
`Windows/System32/cmd.exe`. No hay padre, argv, FileID o hash DOS medidos para
ese miembro. La simultaneidad con `qemu-img` no demuestra parentesco ni función.

MAIN verificó el nuevo payload de 14 archivos/445.173 bytes y los 163 miembros
anteriores/11.036.964 bytes. Recalculó la secuencia del diario: miembros en evento
154, error en 158, captura completa en 165 y rechazo terminal en 166. Hay dos
intercambios del controlador; no hubo registro explícito, selección, propiedades,
bootcomplete, sync, cierre grácil por consola, instalación o instrumentación.
El acumulado es tres servidores y un launcher/motor QEMU ejecutados; no equivale
a arranque Android confirmado ni a casos funcionales aprobados.

Ambos Jobs registran vacío, handles cerrados y cero errores o pendientes; puertos
reservados libres. El inventario posterior archivado de los 21 archivos originales
coincide con el original. Los siete archivos del AVD tienen sólo metadata: se
conservaron parciales y los hashes originales de owner/índice/config, sin afirmar
igualdad del contenido de userdata/overlay ni borrar/restaurar nada. Un lector
independiente confirmó el adverso y cesó; no se consultaron PIDs cerrados.

Clave: `Stage12:CONSOLE-POLICY-FIRSTBOOT-ADVERSE:20261024:abacb7202edc43dd`.
Recibos MAIN: `console-helper-firstboot-{received,adverse-review}.json` y
`console-helper-policy-{received,payload-review,risk-review}.json`, en la raíz
local de MAIN. El recibo `cmd-member-causal-preparation.json` autoriza sólo
inspección estática pertinente, lectura del DOS exacto de `cmd.exe` y preparación
prospectiva mínima con pruebas puras. No autoriza ejecutarlo, incorporar su rol
por nombre ni reiniciar SDK/servidor/VM. Se preservan los 177 miembros previos,
fuentes, claims y el AVD parcial; cualquier ensayo siguiente necesita revisión.

## Preparación CMD recibida; corrección ABI antes de admitir ejecución

MAIN recibió la unidad `Stage12:CMD-CAUSAL-PREPARATION:20261024:38902a30-r1`,
verificó 37 miembros/250.311 bytes, 24 pins, los 185 miembros precedentes y el
sidecar V5 por separado. Releyó el DOS exacto de `cmd.exe`: SHA `15cd209c…`,
339.968 bytes, dos enlaces y metadatos/NT estables, iguales al pin. Esto no liga
retrospectivamente el archivo al PID cerrado ni demuestra su función histórica.
La fuente AOSP examinada no se corresponde de forma demostrada con build16428233.

El diagnóstico propuesto lee sólo un miembro vivo y retenido del Job propio,
con un segundo handle de acceso `0x00100410`, identidad y generación antes/después,
dos muestras acotadas y redacción de texto desconocido. La cadena sería memoria
mutable, no argv original; padre heredado y completitud del prefijo se informan
por separado. Mantiene el rechazo de CMD, cero frames del controlador y cierre
independiente de Jobs y handles de lectura. No se ejecutó este diagnóstico.

MAIN repitió 23 pruebas puras finales con procesos, sockets y DLL bloqueados:
cero fallos, errores, omitidas o intentos reales. Dos lectores revisaron las fuentes
finales. El lector de memoria encontró cuatro declaraciones `ctypes.c_bool` de
un byte donde Win32 declara BOOL como entero de 32 bits: el parámetro de herencia
de `OpenProcess` y los retornos de `CloseHandle`, `ReadProcessMemory` e
`IsWow64Process2`. MAIN confirmó los tamaños con DLL dobles, sin llamar esas APIs,
y contrastó los tipos con la documentación primaria de Microsoft. No hay fallo
nativo o bypass observado, ni atribución a los errores históricos.

El hallazgo `MAIN-CMD-BOOL-ABI` fue devuelto al mismo Stage12. Se permite sólo
una variante prospectiva mínima en `cmd-member-cause/bool-r1/`, con esas cuatro
firmas corregidas, regresión ABI y pruebas afectadas, rutas/pins/claim nuevos.
Los 37 miembros originales, driver y helper siguen congelados. No se emitió
admisión de runtime; MAIN revisará la corrección antes de cualquier ensayo.
Recibos: `cmd-causal-{received,payload-review,risk-review}.json`,
`cmd-causal-bool-abi-review.json` y `cmd-bool-abi-correction-preparation.json`.

## Corrección BOOL auditada y admisión diagnóstica b662

La variante `Stage12:CMD-BOOL-ABI:20261024:bool-r1` quedó recibida y auditada:
17 miembros/119.169 bytes, 24 pins y 222 miembros precedentes/20.616.595 bytes,
más sidecar V5 conservado por separado. MAIN comprobó exactamente cuatro cambios
a `c_int32`, dos líneas de ruta/autoridad en el wrapper y únicamente claim/run
en el protocolo. Los 23 métodos anteriores mantienen AST equivalente tras el
cambio de autoridad; MAIN repitió los 24 actuales bajo bloqueadores, todos verdes
y sin llamadas reales. Un lector independiente revisó el delta final y cesó.
El pin OS es copia exacta; no se repitieron SDK, canarios, JVM, builds o lectura OS.

MAIN cerró `MAIN-CMD-BOOL-ABI` y escribió `cmd-live-diagnostic-admission.json`,
SHA `b662a0e1…`, ligada a wrapper `4b0fea69…`, probe `99dbff0d…` y checkpoint
`ddc82e99…`. Autoriza una única invocación: como máximo un servidor, un launcher
y una captura de CMD vivo propio sobre el mismo AVD parcial, seguida de cierre.
No hay reintento automático ni tras rechazo previo al claim. La lectura queda
sujeta a DACL ordinaria, identidad/generación/Job y límites revisados; no habilita
privilegios, fallback, ejecución del texto, adopción de shell o control guest.
La ausencia, salida, lectura denegada o cambio de memoria dejan diagnóstico
incompleto preservado. La salida CLI 2 es prevista incluso con observación completa.

El resultado real todavía no fue recibido: no se afirma captura, parentesco,
causalidad, boot, instalación ni QA por esta admisión. Recibos MAIN:
`cmd-bool-r1-{received,delta-review,risk-review}.json` y pruebas asociadas.

## Diagnóstico real b662 auditado: lectura y padre, propósito desconocido

La admisión `b662a0e1…` se consumió una vez en el run
`1d96c353-29fc-4940-a21b-e9f2afdde582`. MAIN verificó los 18 archivos/259.685 bytes,
24 pins y 239 miembros anteriores/20.735.764 bytes, más sidecar V5. Dos muestras
UTF16 de 394 bytes coinciden por hash; identidad/generación/Job de CMD 9892 están
comprobados antes y después sobre el handle original y el secundario. El padre
heredado 13500 se ligó a un QEMU propio retenido, vivo y anterior al hijo; no es
atestación del creador. La lectura es memoria mutable, no argv original atómico.

El prefijo quedó vacío y los seis tokens redactados: el propósito no se comprobó.
La observación completa, el vínculo del padre y la divulgación completa son tres
resultados distintos. No se reconstruye el texto descartado desde longitud/hash.
Una anotación posterior `in_owned_job` en el objeto del resultado es la única
diferencia frente al diario previo; los cuatro controles explícitos de pertenencia
del CMD ya están en ambos. La pertenencia inicial del padre procede de su guarda
exitosa y prueba SDK, sin un campo booleano independiente inventado por MAIN.

Se recalculó la cronología de 78 eventos: selección 65, memoria 66, rechazo 71,
captura completa 77 y parada 78. Ambos Jobs y los handles de lectura cerraron,
sin errores ni pendientes; puertos reservados libres. El inventario posterior
archivado de 21 imágenes coincide con el original. Los 13 archivos del AVD sólo
se registraron por metadatos; sus parciales pudieron cambiar naturalmente, sin
afirmar igualdad de userdata/overlay ni hacer reset/limpieza/restauración.
CLI 2 fue previsto. Acumulado: cuatro servidores, dos launchers y dos motores
QEMU observados; Android sigue sin confirmar, sin instalación o QA funcional.

La misma captura conserva dos listeners IPv6 `::1`, puertos efímeros 58532/58535,
del QEMU propio; los pares IPv4 58530–58531 y 58533–58534 están establecidos contra
su propio PID. No se aceptaron esos listeners: el rechazo CMD interrumpió antes
la validación de red. Si sólo se resolviera CMD, la guarda de endpoints congelada
los rechazaría por `UNDECLARED_ENGINE_TCP_LISTENER`. Un lector independiente
confirmó datos, límites y el siguiente control afectado, y cesó su revisión.

MAIN autorizó una unidad local conjunta en `g3-integrated-r1/`: clasificador finito
de formas conocidas y redacción de desconocidos, predicado explícito del propósito
CMD y decisión acotada de roles/endpoints QEMU, con pruebas puras y protocolo de
continuación condicional. No permite shell genérico, cualquier loopback, otro
ensayo o una garantía universal de red. Si un rol no puede justificarse, se informa
el gate exacto. Se preservan los 257 miembros anteriores y el sidecar V5.
Recibos MAIN: `cmd-live-r1-{received,runtime-review}.json` y
`g3-integrated-local-preparation.json`. No se emitió otra admisión de runtime.

## Unidad integrada auditada y corrección de peer inverso

MAIN recibió `Stage12:G3-INTEGRATED-PREPARATION:20261024:r1` con cese y fuentes
congeladas. Verificó 29/29 archivos, 250.374 bytes y 32 pins; los 257 miembros
anteriores, 20.995.449 bytes y sidecar V5 de 2.487 bytes permanecen intactos.
Repitió las 57 pruebas puras finales: verdes, sin fallos, errores, omisiones ni
intentos de llamadas reales a procesos, SDK, sockets o DLL nativas.

El delta del probe sólo añade la clasificación en RAM; firmas ABI, máscara,
muestreo y cierre se conservan. La gramática CMD admite únicamente conversión
qcow2 de userdata propia y colas finitas, con pin nuevo realmente consumido.
La clase auxiliar IPv6 de QEMU es una propuesta bajo el SDK confiable: función
inferida, sin callsite o correspondencia exacta del build demostrados. Las
limitaciones de memoria mutable, AutoRun y observación de red siguen explícitas.

Un lector independiente detectó `MAIN-G3-MAPPED-REVERSE-PEER` en
`endpoint_guard.py:203–209`. MAIN reprodujo ocho adversos puros: peers ajenos o
del controlador con alias IPv4-mapped no se emparejan por igualdad literal; el
fallback SYN acepta el caso. Tanto la guarda de endpoints como la integración
completa lo aceptaron indebidamente. Es un defecto sintético comprobado de la
guarda, no un escape real observado. El segundo lector dio conformidad CMD;
ambos cesaron sin recursos.

Se devolvió al mismo Stage12 una corrección mínima en raíz nueva
`g3-integrated-r1/reverse-peer-r1/`: equivalencia pasiva de alias en ambos extremos
del cruce inverso, sólo para rechazar peers ajenos. Los binds, roles, pins y
controles no se amplían; se conservan los 286 miembros sellados anteriores.
No se habilitó otro servidor, VM o consulta de procesos. Recibos MAIN:
`g3-integrated-r1-{received,payload-review,risk-review}.json`,
`g3-integrated-r1-unit-tests/result.json`,
`g3-integrated-r1-mapped-reverse-probe.{py,json}` y
`g3-mapped-reverse-correction-preparation.json`. Android y QA siguen sin cambios.

## Peer inverso corregido y nueva admisión técnica

La entrega `Stage12:G3-MAPPED-REVERSE-CORRECTION:20261024:r1` quedó recibida fuera
del payload, con cese. MAIN verificó 24/24 archivos, 191.905 bytes y 32 pins;
286 miembros previos, 21.245.823 bytes y sidecar V5 permanecen intactos.
El delta contiene exactamente las dos comparaciones pasivas del reverse join;
operador sólo cambia ROOT y autoridad, y protocolo sólo claim/run. Probe,
gramática CMD, pin OS, runner y pruebas previas pertinentes conservan sus bytes
o sus 57 métodos AST según atribución. Binds y roles no se amplían.

MAIN repitió 61 pruebas puras: todas verdes, sin llamadas o intentos reales.
También repitió sus ocho adversos: antes admitidos, ahora rechazados en ambas
capas con `FOREIGN_OR_CONTROLLER_AUX_PEER`. El lector independiente verificó
el delta final y cesó. `MAIN-G3-MAPPED-REVERSE-PEER` queda CERRADO; no se atribuye
un escape real ni evidencia Android a esas comprobaciones.

La nueva admisión `main/g3-integrated-first-boot-admission.json`, SHA
`4fe9dfa29431bc659afb7a60aa22bc4a9735aa32af3674a1c6b4f56255cf54d8`, liga operador
`06d75e0a…`, endpoint guard `8897d287…`, probe `41fabdee…`, CMD guard `64bd58e8…`,
checkpoint `12a7e6c3…`, driver/helper, owner y contrato exactos. Ambos flags de
rol están expresamente admitidos como clases prospectivas del SDK confiable,
con las limitaciones anteriores. MAIN ejecutó la función real de validación de
admisión contra los 32 pins actuales, protocolo y owner, bloqueando primitivas
de runtime: conforme, sin ejecución de recursos.

Se habilita una sola invocación oculta sobre el mismo AVD parcial: máximo un
servidor y un launcher; identidad, registro explícito, propiedades, bootcomplete,
sync y cierre. No instalación, instrumentación, negocio o capturas; ningún retry,
incluso ante fallo anterior al claim. Claim/run nuevos pertenecen exclusivamente
a `g3-integrated-r1/reverse-peer-r1/`. La auditoría del resultado real y su cese
siguen pendientes; las cuatro admisiones anteriores permanecen consumidas.
Recibos MAIN: `g3-reverse-r1-{received,delta-review,risk-review}.json`,
`g3-reverse-r1-unit-tests/result.json`, `g3-reverse-r1-mapped-reverse-probe.json`
y `g3-reverse-r1-admission-validation.json`. Android continúa sin confirmar.

## Ensayo integrado 4fe9: adverso real auditado

MAIN recibió `Stage12:G3-INTEGRATED-NATIVE-ADVERSE:20261024:b0ba0c27` con cese.
Verificó 20/20 archivos, 509.097 bytes y 32 pins; los 310 miembros previos,
21.437.728 bytes y sidecar V5 siguen íntegros. Claim, copia de admisión y run
`b0ba0c27-133a-400f-b7eb-a33e31c94973` coinciden. Una invocación, un servidor,
un launcher y un motor QEMU observados; acumulado cinco/tres/tres.

Se recalcularon los 152 eventos: versión61, inventario vacío86, startup vacío124
y único proof125, captura150, red previa a política151 y rechazo152 por
`QEMU_IPV4_AUX_NOT_DECLARED`. El QEMU19664 conserva nacimiento, doble API nativa,
pin38a y Job propios. Se observaron listeners duales127.0.0.1/::1:55387 y un
SYN127.0.0.1:55386→127.0.0.1:1970. No hay listener o contraparte propios de1970
en la vista archivada; no se atribuye función ni dueño ajeno desde el número.
El predicado completo no aceptó esa captura. CMD_records vacío no demuestra
ausencia transitoria o propósito. No hubo registro, propiedades, bootcomplete,
sync, consola, instalación, instrumentación, negocio o captura.

Ambos Jobs y los handles de lectura tienen recibos vacíos/cerrados, sin errores
ni pendientes. El postcheck original conserva cuatro listeners5588/5589; un
suplemento filtrado posterior registra vacío. La causa de la diferencia sigue
desconocida, sin consulta a PIDs cerrados o atribución de caché, supervivencia,
reuso de PID o timing. `post_observation_errors=[]` significa consulta exitosa,
no puertos vacíos. El resultado conserva `FAILED_PRESERVED_NO_AUTOMATIC_RETRY`
y sus dos motivos de finalización. Las 21 imágenes originales coinciden con el
inventario completo archivado; 21 archivos del AVD sólo por metadata y tres
hashes owner/índice/config conservados. No igualdad de userdata/overlay.

Un lector independiente confirmó los tres puntos y cesó. MAIN autoriza una
única preparación local en `g3-network-close-r1/`, con estática de SDK/fuentes
ya disponibles, decisión conjunta sobre listener dual y peer1970, y cierre
con convergencia acotada que preserve todas las muestras. Si corresponde una
opción sólo por VM, debe acreditar soporte e impacto funcional antes de proponer
ejecución; no se permite1970 por su número ni un peer no probado. No hay nuevo
runtime autorizado. Se conservan 330 miembros sellados y todos los adversos.
Recibos MAIN: `g3-integrated-native-r1-{received,runtime-review}.json` y
`g3-network-close-local-preparation.json`. Android continúa en0/441.

## Red/cierre local auditados y alternativa de reserva propia

MAIN recibió `Stage12:G3-NETWORK-CLOSE-PREPARATION:20261024:r1` con cese y verificó
28/28 archivos, 246.161 bytes y 32 pins. Los 330 miembros previos, 21.946.825 bytes
y sidecar V5 siguen íntegros. Repitió 50 pruebas puras afectadas: verdes, cero
fallos, errores, omisiones o intentos reales. Probe/gramática/pin OS permanecen
idénticos; los métodos intactos no se repitieron por rutina.

La clase dual IPv4/IPv6 y la convergencia de puertos conservan sus límites. El
defecto de oversleep quedó corregido: no comienza una query pasada la fecha
límite. `PEER1970_RUNTIME_READY=False` bloquea antes de admisión/imports nuevos,
claims o capacidades nativas; esta candidata permanece INELEGIBLE. La ausencia
de opt-out verificado es un límite de esta preparación, sin atribución causal
del SYN ni permiso a un servicio ajeno. Dos lectores MAIN confirmaron y cesaron.

Antes de escalar una decisión humana, MAIN contrastó una salida finita con la
adenda1603 y documentación primaria Winsock: reservar exactamente127.0.0.1:1970
con un socket propio exclusivo y no heredable, SIN listen/connect/accept/send,
relay o protocolo SDK Controller. No se declara un peer propio ficticio: sería
un destino local reservado sin servicio, cuya capacidad retenida debe probarse.
La exclusividad se configura antes del bind; una colisión implica detenerse sin
consultar o desplazar al dueño. Otros binds no conceden acceso a la dirección
reservada. [Exclusividad Winsock](https://learn.microsoft.com/en-us/windows/win32/winsock/so-exclusiveaddruse),
[compartición de direcciones](https://learn.microsoft.com/en-us/windows/win32/winsock/using-so-reuseaddr-and-so-exclusiveaddruse)
y [opciones/readbacks](https://learn.microsoft.com/en-us/windows/win32/api/winsock/nf-winsock-setsockopt).

Un lector independiente consideró viable la alternativa dentro de recursos
propios, con prueba de descriptor/opciones/no-listen, permisos sólo para SYN
QEMU exacto al slot retenido, rechazo de listeners/conexiones establecidas o
pérdida de capacidad y liberación posterior al cierre comprobado de Jobs/lectores.
No garantiza un firewall universal ni acredita todavía el comportamiento nativo.

MAIN autoriza únicamente preparar esa variante en `g3-1970-reservation-r1/`:
código mínimo, adversos puros, capacidades/cierre, protocolo finito de canario
nativo futuro y protocolo de boot separado. Ningún bind o canario queda permitido
por preparar el código. Se requieren revisiones y admisiones exactas posteriores;
la candidata con gateFalse y todos sus sellos permanecen intactos. No se necesita
redefinir permiso a servicios ajenos ni pedir una confirmación humana rutinaria
para esta preparación. Se conservan 358 miembros sellados y sidecar V5.
Recibos MAIN: `g3-network-close-r1-{received,payload-review,review}.json`,
`g3-network-close-r1-unit-tests/result.json`,
`g3-1970-{unserved-reservation-assessment,reservation-design-decision}.json`
y `g3-1970-reservation-local-preparation.json`.

## Reserva auditada y admisión de canario exclusivamente

MAIN recibió la preparación `g3-1970-reservation-r1/` con cese y verificó sus
20 miembros, 127.035 bytes y 28 pins. Los 358 miembros previos, 22.192.986 bytes
y sidecar V5 permanecen intactos. Repitió 44 pruebas puras: verdes, sin fallos,
errores, omisiones o intentos nativos. Ambos lectores MAIN dieron conformidad
para considerar exclusivamente el canario y cesaron.

La capacidad primaria usa EXCLUSIVE1/REUSE0 y no herencia antes del bind exacto,
con readbacks directos y sin escuchar. Dos desafíos propios usan EXCLUSIVE0 y
REUSE0/1 respectivamente; sólo errores10048/10013 ocurridos durante bind prueban
la colisión. Cualquier otro error, bind exitoso o cierre fallido queda rojo.
Cada secundario y el primario tienen cierre independiente; no hay consultas
TCP, conexión, servicio, SDK o VM en el canario.

El gate de arranque permanece False antes de imports/pins/claims/capacidades.
El helper congelado puede soltar handles tras un cierre incierto; una referencia
Python o un JSON no conserva custodia más allá de terminar el controlador.
Se identificó como vía futura técnicamente viable un controlador vivo en dos
fases, con handles reales retenidos y una orden propia de limpieza por STDIN;
no está implementada ni admitida. EOF/timeout/cancelación no autorizan liberación
insegura y esa retención no tendría duración estrictamente acotada por sí sola.

MAIN creó y validó contra la función real del operador, los 28 pins, protocolo y
owner actuales la admisión `main/g3-1970-reservation-canary-admission.json`, SHA
`8c67c26de259612a141afcd65ecb71d942f286f4c490e59e8cffb1b53887aaf3`.
Liga operador `1be9f208…`, reserva `ca7aa694…`, checkpoint `4c2b1ab1…` y autoridad
`f9b1b205…`. Sólo habilita una invocación de un primario y dos desafíos propios;
sin retry, incluso preclaim, o cadena hacia boot. `runtime_boot_admitted=false`.
No acredita exclusividad nativa, state100, lifecycle, Android o QA por emitirse.
Recibos: `g3-1970-reservation-r1-{received,payload-review,risk-review}.json`,
`g3-1970-reservation-r1-unit-tests/result.json` y
`g3-1970-canary-admission-validation.json`. Falta revisar el resultado real y cese.

## Canario real auditado y preparación concreta de custodia

MAIN recibió la entrega sellada y el cese del run `7529e26d-247d-4357-b86a-99af80985da1`.
Rehasheó 13/13 miembros, 44.848 bytes y 28 pins, además de los 378 miembros
previos, 22.320.021 bytes y sidecar V5 de 2.487 bytes. Claim y copias exactas de
admisión/checkpoint coinciden con la única invocación oculta. La admisión
`8c67c26d…` está consumida; no hay retry ni continuación automática a boot.

Las cuatro lecturas serializadas del primario coinciden: descriptor404,
127.0.0.1:1970, TCP, no heredable, REUSE0, EXCLUSIVE1 y ACCEPTCONN0. Los dos
secundarios fallaron durante bind con10048 y10013 respectivamente y registran
cierre sin errores con fileno=-1. El primario registra closed=true/errors=[];
su fileno final no se serializó por separado: es inferencia del predicado
`Reservation.close()` pinneado, sin consulta posterior al descriptor/PID ni
sustitución por salida del proceso. Los ceros SDK/TCP proceden del recorrido
source y sus recibos, no de un censo independiente de objetos del kernel.

MAIN recomputó los predicados del resultado y revisó fuente/launcher/cronología.
Un lector independiente corroboró recibos y límites y cesó. No se repitieron
los44 puros ya válidos sobre las mismas fuentes ni hubo nuevas operaciones
nativas. La cadena suma391 miembros, 22.364.869 bytes más sidecar separado.
Counts SDK siguen5 servidores/3 launchers/3 QEMU; Android no confirmado y0/441.
El canario no prueba custodia durante cierre SDK incierto, peer1970 o state100.

Con ese resultado, MAIN envió una única preparación local nueva en
`g3-custody-r1/`, autoridad `main/g3-custody-local-preparation.json`, SHA
`7bdfb89c208d8c95891de072e30f7c3139fd00a9202740b510c7e6dcc0017b46`.
Se prepara una variante mínima en dos fases, con capacidades nativas reales
retenidas antes del finally destructivo y transporte STDIN propio concreto.
HELD conserva errores/handles/reserva; una orden vinculada a run/owner sólo
puede intentar limpieza de capacidades retenidas, sin relanzar SDK. EOF, orden
inválida, timeout o cancelación lógica no conceden release; EOF definitivo
pierde el canal. No se promete duración máxima ni resistencia a TerminateProcess,
crash del controlador o caída del host. Un segundo lector consideró viable
prepararlo con estos límites y cesó. No hay autorización de recursos nativos.

Los originales y sus gates False quedan intactos. La nueva unidad debe entregar
source/protocolo/transporte/pruebas puras/pins/sello/cese para revisión MAIN;
si no conserva custodia, entrega gate exacto y parada antes de SDK. No se crea
un framework general ni otra tarea, operador o supervisor. Recibos MAIN:
`g3-1970-canary-r1-{received,runtime-review,final-review}.json`,
`g3-1970-canary-claim-observed.json` y
`g3-custody-local-preparation{,-message,-transport}` con sus extensiones.

## Custodia concreta auditada y admisión técnica nueva

MAIN recibió `g3-custody-r1/` sellado y con cese:44 miembros/435.456 bytes,
manifest `4a46a4fd…`, sello `441545eb…`, checkpoint `f0d8c7da…` y32pins.
Rehasheó todo y los391 miembros previos/22.364.869 bytes más sidecar V5 separado.
La cadena suma435 miembros/22.800.325 bytes más2.487 del sidecar. Repitió las
73 pruebas nuevas afectadas: verdes, sin fallos, errores, omisiones ni intentos
reales, con procesos/sockets/DLL/os.getpid bloqueados antes de imports.
Recalculó el delta AST exacto de helper/probe y las cinco copias byte-idénticas.
La primera comprobación MAIN supuso el nombre antiguo V5sidecar; se corrigió al
campo real V5_sidecar y se verificó, sin cambiar payload ni consultar recursos.

La nueva variante registra capacidades reales y parciales antes de uso/Resume;
separa detención comprobada de liberación y exige barrera de ambos Jobs,
parciales, lectores y controles. El socket reservado se libera último.
Cierre ambiguo conserva error/capacidad sin reclose sobre un número reutilizado;
un handle retenido no demuestra por sí mismo validez tras una operación ambigua.
HELD se establece en RAM antes de persistencia falible. El PID consultado sólo
entra en la proyección TCP propia después de pertenencia confirmada al Job.
La regresión usa captura real con fronteras fake; no hubo fuga nativa observada
ni atribución retrospectiva a los ensayos históricos.

El launcher conserva Process/pipes y reenvía expresamente una orden STDIN
acotada al mismo controlador. EOF/cancel/timeout/orden inválida no liberan.
La única limpieza retenida no relanza SDK; HELD puede ser indefinido o quedar sin
canal recuperable. No hay garantía frente a muerte forzada/crash/host loss.
COMPLETE_CLOSED prueba cierre de recursos, no boot/QA; CLI2 sigue adverso.
Dos lectores MAIN dieron conformidad acotada y cesaron; no ejecutaron nativo.

MAIN comprobó ParseFile del launcher final:0errores/0ReadLine/1Start/0Kill.
Reflexión de tipos del runtime configurado PowerShell7.6.5/CLR10.0.11 confirmó
StandardInputEncoding presente, sin crear Process/ProcessStartInfo ni iniciar
launcher. Esto no prueba PTY/pipe. El wrapper de lector conserva la conversión
`_as_parameter_` documentada y ejercitada con from_param puro, sin llamada DLL.
[Conversión ctypes](https://docs.python.org/3/library/ctypes.html#calling-functions-with-your-own-custom-data-types).
No se repitieron las baterías44/50 ni JVM/build/SDK por rutina.

MAIN creó la admisión independiente `main/g3-custody-first-boot-admission.json`,
SHA `7736b5b86587e7afb946c85c5d9f4bbb5a804d83896fd25994f0b0236d788591`,
acción `CUSTODY_G3_FIRST_BOOT_IDENTITY_SYNC_CLOSE_ONLY`. Validó la función REAL
del controller contra JSON/32pins/owner/protocolo actuales con primitivas
bloqueadas, sin intentos nativos. Liga controller `71c13942…`, helper `6844e327…`,
probe `74cff53e…`, custody `6dd78c1e…`, channel `c89cb705…`, launcher `02344dea…`,
checkpoint `f0d8c7da…`, driver/histórico/owner/contrato/preparación/canario exactos.

Se envió una vez al mismo Stage12: máximo una invocación, un servidor/un launcher
SDK y una orden separada de limpieza sobre el mismo pipe retenido si entra HELD.
Sin retry, incluso preclaim; sin install/QA/Samsung/adopción ajena. Claims/run
nuevos exclusivos bajo esta unidad. No cambia fuentes, puertos, argv/env o AVD
para obtener verde. Las seis admisiones anteriores siguen consumidas.
Hasta entrega real sellada: SDK5server/3launcher/3QEMU yAndroid0/441 sin cambios.
Recibos MAIN `g3-custody-r1-{received,payload-review,risk-review}.json`,
`g3-custody-r1-unit-tests/result.json`, `g3-custody-r1-launcher-static-review.json`,
`g3-custody-admission-validation.json` y mensaje/transporte de la nueva admisión.

## Ensayo7736 fallido y custodia real cerrada

MAIN recibió los19 miembros/2.265.615 bytes de runtime sellado, verificó32pins,
435 miembros previos/22.800.325 bytes y sidecar V5 separado. Cadena454 miembros/
25.065.940 bytes más2.487 del sidecar. Claim/copiaadmisión/checkpoint exactos
del run `e8be0b01-b4c4-430d-a2e2-8f13e061f89a`; invocación y orden únicas.
Diario314 eventos y prefijoHELD301 coinciden byte por byte. No nuevos tests,
SDK/OS/PID/red ni rehash2GB de MAIN por auditar archivos. Dos lectores
independientes corroboraron coherencia/límites y cesaron.

Identidades nativas, metadata/pins/Job/birth acreditan servidor20100, launcher
24608 y QEMU27904. El acumulado deriva del previo5/3/3 más esta cohorte1/1/1,
no de contar observaciones repetidas:6/4/4. Los readbacks de reserva636 son
iguales en cinco observaciones de la MISMA tupla SYN3, no cinco conexiones.
Eventos63/88/240 prueban versión, inventarioBEFORE vacío y registro5589.
Snapshot295 y errores296/297 rechazan OWN_TCP_NONLOOPBACK_LOCAL por la fila
IPv6 no-loopback del QEMU/443/estado5. Direcciones raw sólo en diario propio;
no lookup/dominio/función/guest inferidos. No propiedades, boot/sync/consola
autenticada, instalación o QA. CMDrecords vacío tampoco prueba lecturaPEB real.

Primera limpieza: ambos Jobs vacíos/parciales salidos, pero errores Win325
terminate_created preservados; released vacío y sin liberar reserva. HELD300/301.
Una orden ownPTY87017 aceptada302; longitudwire179B es atribución de source y
aceptación, no captura externa del pipe. Segunda ronda prueba salida/vacío
actual sin nueva terminación de Job, conserva historias5 y cierra lectores,
tres controles, Jobs y metadata sin pendientes/errores actuales. Reserva312
última, COMPLETE313/business_failedtrue y muestra314 de puertos vacía.
Ningún intercambio/captura/relanzamientoSDK después de la orden; sí limpieza
de capacidades retenidas permitida. La secuencia concreta corrobora custodia
real y cierre; no censo continuo del kernel ni garantía tras muerte forzada.

PTY terminada/toolExit1 observado; retornochild2 sólo inferido por source, no
serializado. Diferencial y error5 sin causa atribuida/reopenPID. Inventario
archivado de21 imágenes/2.049.726.687 bytes coincide entero con baseline.
Sin nueva comparación AVD ni igualdad de userdata/overlay. El resultado sigue
FAILED con BOOT_IDENTITY_OR_SYNC_NOT_COMPLETE y POST_OBSERVATION_FAILED.
Recibos `main/g3-custody-native-r1-{received,runtime-review,final-review}.json`;
reviewSHA `fc490a8a8079f45c92bd6e8ed8a820f2f18181afc09baad4790051421a38d013`.

MAIN envió una preparación local única `g3-egress-scope-r1/`, autoridad
`main/g3-egress-scope-local-preparation.json`, SHA
`7c38da4be9bbacd7594c0ba329f94921652a92bf175a1b5f1b026850e83eebb9`.
Sólo estática acotada de entradas ya pinneadas/fuentes primarias pertinentes,
semántica acreditada de opción porVM si existe, impacto/matriz y pruebas puras.
No aceptar443/IP/SDKidentity como permiso o propósito, ni cambiar NIC/radio/WiFi
por conjetura. Clima/updates posteriores no autorizan este contacto previo.
Sin runtime/SDK/OS/PID/TCPqueries, fuentes viejas/config/AVD/globales intactos.
Si no acredita una vía local segura, entregar límite material exacto y detener
esta preparación; no cadena de investigación/ensayos indefinidos.

## Gate final de entorno auditado y cierre de la supervisión

MAIN recibió g3-egress-scope-r1 con cese y verificó18/18 miembros/121.130 bytes,
22pins y454 miembros previos/25.065.940 bytes más sidecar V5 separado.
Cadena472 miembros/25.187.070 bytes más2.487 del sidecar. MAIN repitió12 pruebas
puras afectadas: verdes, cero fallos/errores/omisiones/intentos de primitivas
bloqueadas. Ambas guardas siguen byte-idénticas. Dos lectores MAIN corroboraron
la insuficiencia finita y el bloqueo; cesaron sin recursos o nuevos ensayos.

pre_sdk_gate rechaza incondicionalmente: runtime_readyfalse y perfil/acción/
claim/run/admisión nulos. Las tres familias revisadas no acreditan una receta
para el contacto7736 no atribuido. restrict se limita a backend guest;
NIC/defaults y proveedores WiFi/radio no prueban supresión de un socket
independiente del host/helpers. MAIN leyó también la semántica primaria
[QEMU user networking](https://www.qemu.org/docs/master/system/qemu-manpage.html#network-options).
No se afirma inexistencia universal de opciones ni se repone el sandbox23/10.
Mapping exacto de build es un límite adicional, no el único fundamento.

La alternativa de un entorno dedicado con uplink aislado es una propuesta,
sin recurso/método/costo/alcance todavía aceptados y sin promesa de bootstrap.
No permite desconectar el host compartido o cambiar firewall/WFP/AppContainer,
privilegios, dependencias, servicios, proxy o AVD. Incluso ese entorno podría
seguir disparando las guardas; Clima/updates reales no quedan dispensados.

Se registra la reanudación exacta y el breve de decisión en MAIN:
stage12-egress-material-gate-resume.json y stage12-egress-environment-decision.md.
Se necesita que Joaquín identifique un equipo/VM dedicado disponible o decida
mantener Stage12 detenido; luego se prepara un contrato concreto antes de cambios.
No se crea otro chat/operador, no se reusan admisiones ni se siguen exploraciones.
Se elimina la automatización tras verificar recibos/docs, sin archivar chats.
Recibos main/g3-egress-scope-r1-{received,payload-review,risk-review}.json y
g3-egress-scope-r1-unit-tests/result.json. Android no confirmado/0funcional;
counts nativos6/4/4 sin cambios y sin recursos HELD declarados.


## Nueva orden directa: Samsung, aislamiento y conservación antes de pruebas

Joaquín autorizó el24/10 continuar Stage12 en Samsung usando esta PC y el mismo
chat, primero verificar aislamiento/conservación y después pruebas/dos rondas.
Autoridad exacta main/samsung-resume-20261024.json y mensaje/transport asociados.
Contrato prospectivo8355ae89f4233f12bab1650171e2bfef72844a33c10501db1faa5e72439199f8;
contrato54eec4 anterior copiado byte-exacto en MAIN. No mutación de payloads,
claims o admisiones antiguos; no reatribución de pruebas ni nuevo ensayo AVD.

MAIN confirmó rama/HEAD,173modificados/182nuevos/staging0/único worktree y remitió
una unidad cohesiva al único Stage12. Lectura mínima Samsung de identidad,
metadatos y snapshotQA coherente sin inicializar Application normal permitida;
instalación/instrumentación/negocio aún detrás de revisión técnica MAIN.
Las1956 jornadas son antecedente, baseline actual manda. Pública sólo metadatos
de código/certificado para conservación, sin abrirla/archivos privados/fixture.
Callbacks Copias, servicios/avisos/Widget/colas y muerte/readmisión deben quedar
aislados antes del primer efecto, con readbacks reales y comparación durables.

Un lector MAIN confirmó: runner actual sólo tres canarios y CaptureActivity;
neutral/quiet suprimen runtimes, y bootstrap normal sólo validadorJSON. No
acreditan arnés441 o funcionalidad normal. Informe sólolectura, sin ADB/Gradle/
pruebas/escrituras; cese. Stage12 deberá integrar el recorrido completo y
entregar canarios nativos/guardas/candidata/runner/protocolo para revisión MAIN.
V5 permanece inmutable y sus45OEM/44padres mixtos son elegibles, no aprobados.

ACK101 observado: Stage12 continúa preparación PRE-SAMSUNG, sin emulador.
Todavía no hay identidad Samsung/baseline/canario/install/QA nativos nuevos.
MAIN no opera el dispositivo o Gradle en paralelo. Firma/distribución/datos
personales/globales/Git no autorizados; límites humanos se coordinan cuando
el paso real esté listo, sin volver a solicitar este permiso de alcance.


## Aclaración humana verificada: uso normal de la QA instalada

MAIN leyó el response_item original role=user/provenance=user del Stage12,
msg_01a170bd-0edf-7882-be51-73a17f6d521a, UTC01:46:40.735Z del25/10 (24/10 BA).
Joaquín indicó abrir y usar la app porque no contiene información importante.
La prohibición de abrir antes del baseline y la conservación QA inmutable/
atómica como condición de uso quedan sustituidas. Inspección y pruebas normales
de la QA instalada permitidas, sin esperar un framework de baseline universal.
Nueva candidata sí mantiene PRE-install proporcional de identidad/cert/SHA/delta.
No orden de vaciar/desinstalar/resembrar masivamente ni ampliación a pública,
otrasapps/secretos/globales/Git/distribución/AVD. Matriz y dos rondas siguen.

Un lector MAIN corroboró esta interpretación sin herramientas/escrituras y cesó.
Recibos main/samsung-normal-use-{human-source-,}20261024.json; contrato8355ae
anterior copiado exacto en main/stage12-contract-before-normal-qa-use-20261024.md.
MAIN no opera Samsung/ADB en paralelo ni promueve reporte a prueba nativa.

MAIN corroboró apertura de QA instalada desde salida nativa del Stage: Samsung
SM-S938B/API36/serialR5CY529W6PL, MainActivity QA, Status:ok/COLD/265ms/266ms.
Recibo main/samsung-installed-qa-opening-review-20261024.json; sin relanzamiento
MAIN, prueba de familias, candidata nueva, certificado/bytes o aceptación UI.
Stage reporta QA dedicada/lint completos, pendientes de revisión pertinente.


## Diagnóstico Samsung auditado:129 métodos aprobados,2 fallidos y cero rondas

Recibido main/samsung-diagnostic-r1-received.json; resumen d973ae94,25 intentos
completos. MAIN verificó57 miembros pertinentes, los bytes de QA682e/runner6018
archivados desde instalado y las fuentes contra bd-b2 antes/después; no confundir
con APK58a/runner43c compilados después. Todos los destinos son Samsung/5050 y
component de instrumentation QA explícitos. Un lector MAIN recalculó transcripts:
131IDs únicos entre esos25,129PASS/2FAIL/0SKIP, sin duplicados y terminales completos.

Primer result tests_completed:false contradice su raw OK(3tests)/terminal-1:
error del recibo host, no incompletitud nativa. Protocol-review conserva lectura
correcta. SHA rawCRLF y normalizado difieren legítimamente; ambos recalculados.
No modificar recibo original. Los2FAIL completos conservan FontScale ausente
en Nav y detalle→Clima con Required value null. MAIN revisó copia SQLite QA
archivada con WAL/SHM: quick_checkok,1956 jornadas,24/10cero;23/10y26/10una,
27/10cero. Sostiene precondición ausente; no demuestra funcionalidadClima o
conservación universal/atómica. El recibo nativo de ese intento declara negocio
y preferencias sin cambios; no ampliar a todos los intentos.

WorkSetup completo NO:240.031s,17seleccionados/16iniciados/15terminados,
11PASS/4FAIL/2pendientes y sinterminal. Los dos pendientes pasan separados en
mismosAPKs (06/07), nivelUI_COMPOSE aunque estén en carpeta native-storage.
No resuelven causa del timeout ni los cuatro fallos anteriores, no se atribuyen
a parches posteriores y no se suman los11 parciales a los129 completos.
Desglose131:122UI_COMPOSE,2RemoteViews_STRUCTURE,1Activity_NATIVE fallida,
1DataStore,3archivos y2FileProvider. Las seis de almacenamiento son reales en
su alcance; no entregaAlarmManager/WidgetOEM ni441funciones por inferencia.

Recibos main/samsung-diagnostic-r1-{payload-review,final-review}.json, con
manifiesto propio de miembros. Diagnóstico aceptado, cero rondas finales.
No nueva candidata/runner/instalación aceptados. Stage continúa preparando QA
ordinaria y runner normal acotado; fuentes activas no auditadas como cierre.
MAIN no ADB/Gradle/SDK en paralelo. OwnADB5050 permanece reporte vivo de Stage;
no cese ni cierre inferidos. Sigue matriz441/506/1627 y dos rondas misma final.

## Reconexión humana del25/10: reanudación del mismo Stage12

Joaquín confirmó directamente en MAIN Samsung reconectado y reanudación.
Recibo main/samsung-reconnected-20261025.json y message/transport asociados.
HOLD temporario levantado; no permiso nuevo de instalación. Stage revalida
identidad/transporte actuales y retoma QA instalada/pruebas seleccionadas.
Sesión15775 terminóCtrl-C/exit1 duranteHOLD segúnreporte; hijo15904/5050 sin
cese corroborado, sus metadatos son históricos. No matar/adoptar PID numérico.
MAIN sinADB/SDK/OSquery paralelo. Candidata nueva detrás de PRE-install pertinente;
diagnóstico129/2 y WorkSetup incompleto preservados, dosrondasfinales pendientes.


## Pareja QA ordinaria admitida e instalada; tres fallos previos a Activity

MAIN recibió PRE-install R1:83 miembros/34566662B, manifiesto63de7688,
20 fuentes propias y390 pins actuales íntegros. Recalculó delta/preimágenes,
inspección de paquetes/target/certificado y13 JVM pertinentes. Dos lectores
internos conformes/cesados: Application/Context normales, sin remap/quiet;
Copias real en modo normal. R3→R4 sólo dos AndroidTests. QA0b65/runner1ce5,
certificado técnico b691, plan997292 de151 métodos/21 clases, diagnóstico.

La interoperabilidad host reveló malformed:false frente a0 entero; original
preservado, protocoloV2 estricto corregido y adversos afectados comprobados.
OperadorV3 sustituye asserts por guardas efectivas bajo-O y recibos por fase,
incluye stdout/stderr de prechecks/timeout y detiene dependientes ante error.
MAIN ejecutó10 comprobaciones puras y9 actualizadas bajo-O; tres de protocolo
repetidas tras cambiar su forma de prueba, no19 pruebas únicas. Todo mediante
dobles, Popen/socket/DLL bloqueados y lectura ADB simulada: ningún intento SDK.
Recibos main/samsung-preinstall-r1-{payload-review,final-review}.json,
unit-tests/result.json y operator-v3-tests/result.json. Candidata no aprobada
por canario:151 métodos no cubren441 IDs/506 subcasos/1627 obligatorias.

Admisión4ccaf08a para instalación-r QA y runner-t exactos y una tanda V3;
guardas reales previas a env/output/ADB validadas sin recursos. Transporte
aceptado/ACK; main/samsung-normal-qa-151-admission-20261025.json y asociados.
Instalación/readbacks auditados:16 comandos/raw, ambos SHA coinciden y target
NormalQaRunner→QA, user0; MainActivity WARM186ms/Wait187. Sin clear/uninstall,
-d/-g/reseed en esos comandos. No prueba conservación universal de preferencias
o archivos privados. Contexto de uso normal autorizado sigue vigente.

Tanda normal-campaign-r1:8 prechecks y una instrumentación, tres métodos,
0PASS/3FAIL con terminal completo-1, sin timeout; raw88ef5a7b. MAIN reprodujo
rechazo del protocolo, sin suites dependientes ni cats. Todos fallan en
guarda compuesta de raíz255 antes Activity272; runner/clock/UUID anteriores
pasaron. Raw no distingue canonical!=absolute de mkdirs fallido; alias sigue
hipótesis, no causa medida. No atribuir fallos a Calendario/Clima ni cero
efectos: Application y amstart previos sí corrieron; mkdirs podría ser parcial.

Recibido durable y revisión main/samsung-normal-install-campaign-r1-*.json:
107 miembros pertinentes, review5695986f. MAIN reparó su contador esperado
18→secuencia real16 y pasó bytes al validador; errores de su script, originales
sin mutación. Lector sólo lectura confirmó ubicación/límites y cesó. Resultado
devuelto al mismo Stage12: corrección mínima del runner, base Context.filesDir
canónica y guardas descendientes/owner/salida preservadas; nueva PRE-install
antes de reemplazarlo. QA0b65 permanece instalada, sin reintento de4cca.

Rondas finales0/2; diagnóstico previo129/2 y WorkSetup incompleto separados.
MAIN no SDK/ADB/Gradle/OS/PID paralelo. Servidor propio actual50417/18560 es
capacidad de Stage; no cese inferido. Emulador detenido y admisiones históricas
consumidas. Continúan matriz completa, OEM/humano y dos rondas misma final.

## Reanudación MAIN local con Samsung no disponible

Joaquín invocó expresamente joa-main-orchestrator y ordenó retomar lo local
mientras Samsung no está disponible. La pausa total previa se levanta sólo
para esa parte: Samsung/ADB/presencia/instalación/instrumentación siguen HOLD,
sin sondeo. Misma tarea Stage12, MAIN supervisa/corrige/audita con permiso
humano previo; no nuevo chat ni otro implementador.

Puerta0: rama/HEAD originales,176rastreados/194nuevos/staging0, un worktree.
Primer contador MAIN usóstrip y quitó espacio inicial: falso staged1; raw y
diffcached vacíos confirmaron0. Últimos3pins de pausa1592/0c23/29ff coinciden;
snapshot MAIN local-resume-20261025T040948Z/sources, autoridad032710e4.
IntermedioR2db35/manifest9945226a no representa esos últimos bytes, sin admisión.
Sesión50417 cerrada reportada; hijo18560/5050 incierto/histórico, sin consultas.

Orden: helper/consumidor/errores y pruebas afectadas → runner compilado final
→ PRE-install MAIN → confirmación humana Samsung y revalidación actual por
único Stage → instalación/hash/campaña151 → faltantes441/506/1627/OEM/humano
→ misma candidata/runner/fixtures/criterios congelados → dos rondas completas
independientes y auditoría. Samsung sólo bloquea las dependientes físicas.
Reanudación local autosuficiente enviada una vez al mismo Stage; transporte
aceptado, ACK pendiente. Dos lectores internos acotados de snapshots/matriz;
no fuentes transitorias ni Gradle/dispositivo MAIN. Supervisor único local,
finito hasta que sólo reste conexión humana, sin watcher USB o ADB.

## PRE-install R3 aceptado local; espera de disponibilidad Samsung

Entrega stage12-local-receipts-preinstall-r3-d787bfe2-c291-4673-8cb7-9dfcbb50ec66.
MAIN acusó durable fuera del payload antes de auditar; manifestf81ef90a/selloa1ee/
handoff24ea59f4.1230 miembros/27887649B íntegros y sin diferencias tras pruebasMAIN;
1150 miembros son probesHOSTmock, no Android.392pins antes/después/actuales y3deltas
propios/preimágenes exactos. QA/main/Gradle no cambian;2archivos nuevos testSupport/
test y runCase del AndroidTest respecto de390pins originales. Contador/paths de
script MAIN reparados sin mutar payload: baseline R4 real es source-before, no
source-after inexistente; verificaciones previas conservaron alcance.

R7 offline31,878s/exit0:9JVM_FILESYSTEM_JUNCTION sinfallos/errores/skips, XML fresco
corroborado y assembleQaAndroidTest en log. MAIN no repitió Gradle. QA0b65 idéntica
20389468B, runner86d59503 nuevo4455704B; manifest binario4392B/SHA05688e50 idéntico
a runner1ce54 previamente inspeccionado. Certificado técnico b691 corroborado en
salida Java apksigner archivada, sin SDKtool MAIN. Nada nuevo instalado.

Helper conserva base confiable canónica/fence/UUID/owner/salidas NOFOLLOW; publish
CREATE_NEW+WRITE por un stream. Consumidor conserva Throwable primario y agrega
suppressed de postcondición/publicación. COMPLETE tras cierre/posthash; fallo al
publicar sigue siendo fallo JUnit. Tres cuerpos nativos/prefijo previo a runCase
byteidénticos; PSI Kotlin2.3.21 y mutante corroborados. No causa Samsung alias
medida, conservación universal de prefs/files, atomicidad/durabilidad o sandbox
ante sustitución concurrente de ancestros. Dos lectores MAIN conformes/cesados.

MAIN ejecutó3 pruebas host afectadas con-O:0fallos/errores/skips/intentos reales.
Popen/system/socket/DLL bloqueados, SDKread simulado y archivos temporales bajo
MAIN; positivo151 HOSTMOCK. ASTrequire/save/command/installed idénticos aV3, sin
repetir batería inalterada. Plan8d9769/operadorV5043547/protocolo4d006 coherentes,
151selectores/21suites únicos; Native3→Nav7→Work17→resto124.11claves prospectivas;
189asociaciones/160padres candidatos,28pins antiguos y45sinbinding: sinPASAactual.
Matriz441/506/1627 y2rondas siguen pendientes, no se cierran con estos9JVM/3host.

Recibos main/samsung-preinstall-r3-{received,payload-review,final-review}.json y
operator-tests/result.json. Finalreview35aa21c6 acepta sólo delta LOCAL compartido,
sin Git. SamsungHOLD, conexión solicitada una vez y sin aviso de disponibilidad;
no admisión ejecutable emitida. Cese local recibido, session50417/hijo18560/5050
históricos/inciertos, sin consulta/adopción. Próximo punto exacto registrado en
main/samsung-preinstall-r3-wait-connection-resume.json: aviso humano→revalidación
actual del mismoStage→NUEVAadmisión→sólo runner siQA0b65 coincide→campañaR2nueva.
4cca/R1 consumidas. Supervisor periódico se retira al quedar sólo disponibilidad
humana; no watcherUSB/ADB ni archivo de chats. Etapa integral aún EN_CURSO.

## Aviso humano de disponibilidad y nueva admisión R2

Joaquín respondió Listop a la pregunta específica de enchufar Samsung y avisar
que estaba disponible. Recibo main/samsung-ready-resume-r2-20261025.json6f5bd797;
levanta HOLD físico, preserva alcance y límites. MAIN recuperó rama/HEAD176/194/
staging0 y392pins actuales idénticos a revisión35aa21c6; QA0b65/runner86d59503,
plan8d9769/operadorV5043547/protocolo4d006 sin cambios, salidaR2 ausente.

Nueva admisión b3fd9bfb en main/samsung-normal-qa-r2-admission-20261025.json:
revalidación fresca por únicoStage; servidor5050 propio exacto USB1/EMU0/mDNS0,
sin adoptar50417/18560; identidad yQA actual0b65 obligatorias; sólo reemplazar
runner-r-t user0 y comprobar bytes/target, después una campañaV5 R2 sin-O.
NoQA reinstall o reutilización4cca/R1. MAIN ejecutó16sentencias reales de guardas
V5 antesenv/output conSDKreadsimulado yprimitivasbloqueadas,0intentosreales.
Transporte aceptado/ACK de Stage; consumo no inferido disponible por ausencia
de claim/resultado. Stage reporta preflight0listeners/0adb antesnuevo servidor;
MAIN no lo trata como censo propio. No instalación/pruebas nuevas acreditadas.

Supervisor único recreado tras eliminación finita anterior, mismoID Samsung;
prompt/estadoACTIVE verificados. MAIN noSDK/ADB/Gradle/OS/PID paralelo. Siguen
441/506/1627 y0/2rondas hasta evidencia nueva, sin permiso humano rutinario extra.

## R2: reemplazo sólo del runner corroborado; campaña activa

Hito estable samsung-resume-r2/result.jsonc6e980a8 recibido durable fuera del
payload antes de auditar. MAIN verificó78miembros/18comandos con rawhashes,
identidad fresca SM-S938B/API36/[serial QA en binding local privado]/user0, QA0b65 antes/después yrunner
1ce54→86d595. Un único install-r-t user0, sólo runner; targetNormalQaRunner→QA
exacto. MainActivity COLD691/Wait693; KEYCODE_WAKEUP en comandos, sin desbloqueo
forzado/settings en esos18. No aceptaciónUI ni conservación universal privada.

Own-server26335/PID11780,parent26064,birth02:01:47.657631-03:00,loopback5050 y
recetaone-device/foreground coherentes en metadatos archivados de Stage. MAIN
noOS/PID/portquery oadopción; no censo continuo. ENTRYreal campañaR2/admisiónb3fd
observados: CONSUMIDA/no reuse. Resultado de instrumentación aún pendiente.
Recibo main/samsung-resume-r2-installed-review.json78b640d3 y received asociado.

## R2 adversa auditada y corrección local de preparación inicial

Entrega REALR2 ae7f4429 recibida durable antesauditar. Manifest60f4cbb9/sellofa78/
handoff8c026362,125miembros105086B íntegros. MAIN revisó cierre26335:Ctrl-C/exit1
por capacidadretenida y una observaciónOS archivada0listeners5050/0adb.exe;
no queryteléfono trasclose/PIDreopen/MAINOSquery/censocontinuo.392fuentes actuales
y manifiesto/selloPREinstallR3 iguales. Resultado y efectos adversos preservados.

Una instrumentación V5 con8prechecks,3START/3FAIL/0PASS/0skip/terminal-1/ADBexit0,
no timeout, raw83d465f2;148métodos restantes no ejecutados,0cats/0dependientes.
B3fd/R2 consumidas. Falló ANDcontextQA/foregroundQA/screenOn enassertQa221,
desdeawait228/runCase265, despuésprepareOutput255 yActivityScenario.launch264.
Keyguard222 no observado; businessHash267/cuerpos268 no alcanzados. No error
Calendario/Clima, estado de bloqueo/foco/off/transición o causa carrera probados.
Faltan observaciones discriminadas; fail-closed fue correcto. HostFAILED_PROTOCOL
es rechazo de su validador de éxito, no corrupción del transporte demostrada.
Application/Activity/archivos propios pudieron producir efectos; no zero-effects.

Dos lectores MAIN de raws/fuente congelada confirmaron y cesaron. Reviewfinal
main/samsung-r2-result-final-review.jsonca29cb09, payload-reviewbf775978 y
native3-protocol-source-review060e9cfd.0crédito a11claves/441padres/rondas.

MAIN devolvió al mismoStage una capacidad LOCAL completa bajo autoridaddfcb4592:
preparación/readiness/observabilidad segura de Activity propia, errores, recibos,
consumidores, regresiones dirigidas, runner yPRE-install nuevos. Estado ambiguo
no se promueve; focoajeno inesperado/contextomalo/off/keyguard/deadline detienen,
guarda estricta antes/despuésgestos; no wake/unlock/taps/settingsparaverde.
Sólo booleans/fases propias saneadas que lleguen al raw permitido sin cats
forenses. CausaR2 sigue inconclusa y esperar no se declara remedio probado.

La próxima observación física propuesta es UNAadmisión técnicaActivity0gestos,
selector explícito, noPASSfuncionalsalteandocuerpos; requiere PRE-install yNUEVA
admisiónMAIN. Ahora sólolocal, sinADB/recursos/instalación/instrumentación por
b3fd. Disponibilidad humanaListop sigue vigente, no nuevoHOLD o repregunta.
Transporteaceptado/ACKpendiente; fuente/payloadR2 preservados, supervisorúnico
actualizado a corrección útil. Siguen441/506/1627 ydosrondas mismafinal.


## 25/10 · Preparación de pantalla auditada y observación física admitida

MAIN verificó los 530 miembros de la entrega (26104197 bytes), los 396 insumos y la pareja. Corroboró 15 pruebas JVM nuevas y reutilización de 10 pruebas de archivos con insumos iguales; ejecutó 11 comprobaciones host simuladas, todas aprobadas, con procesos/red/DLL bloqueados y lecturas SDK simuladas. Dos lectores independientes conformes y en cese. No fue una ejecución Android.

QA conserva SHA 0b65; el programa de pruebas nuevo es 2d44. La nueva admisión permite al único Stage acreditar transporte propio e identidad, instalar sólo ese runner y ejecutar una observación de Activity sin gestos. Envío aceptado y ACK: todavía sin resultado nativo nuevo auditado. READY no acredita casos funcionales ni rondas; la causa histórica de R2 continúa inconclusa.

Revisión: .local-evidence/stage12/main/samsung-readiness-r1-final-review.json, SHA bf438a31fd543c85a6de46a552551b8bc05d722ac9ebe6da7dbd715498ee2bdf. Admisión: .local-evidence/stage12/main/samsung-activity-observation-r1-admission.json, SHA d340043e1ed65d060b0f9204c357736e79376943f558516c14610a485056b018. Las admisiones anteriores siguen consumidas. MAIN no operó Samsung, SDK ni recursos del sistema.


### Observación real aprobada y continuidad funcional R3

MAIN auditó 130 miembros (55137 bytes): 16 comandos de identidad, instalación exclusiva del runner y readbacks; 13 del operador con una instrumentación y dos recibos propios después del pase completo. Recalculó el protocolo desde raw d1deae0535f5778b051ad59d87adae13d29ef1b71293547302fb519f379b2dba. Resultado: un método técnico aprobado, READY/DONE a los 552 ms, cero gestos. QA 0b65 sin cambios y runner2d44 instalado, target exacto. Sesión69570 cerrada mediante capacidad retenida, exit1, recibo OS posterior sin adb/listener5050. Un lector independiente conforme y cesado. No prueba funcional, conservación universal o causa de R2; rondas0/2.

La continuación mínima V6 también se auditó: 588 miembros (1103185 bytes), sólo cinco regiones de literales del operador y referencias del plan; los 151 selectores y 21 suites, relojes, plazos, guardas y protocolo siguen iguales. Sin cambios de fuente o APK ni necesidad de instalar. Se emitió y envió una nueva admisión para campaña funcional R3; Stage confirmó ejecución prevista. Revisión nativa: .local-evidence/stage12/main/samsung-activity-observation-r1-final-review.json. Admisión funcional: .local-evidence/stage12/main/samsung-normal-qa-r3-admission.json, SHA ab7bfb638ee5f8e1e2d3148b2b79cefab405554008559f5873eb361f522c7b78. Resultado funcional todavía pendiente.


### R3 funcional: dos PASS y un FAIL completos; selector ambiguo

MAIN recibió y auditó 57 miembros/38449 bytes, nueve comandos y raw ed84ee450a5fb7196fca777eea793eb82a9b8841df30b7406ddc4132b3427a86. Tres START, dos PASS sin stack y un FAIL en openDate183 desde método52, terminal-1, ADB0, sin timeout. Los otros148 métodos no corrieron; no hubo cats tras el fallo. El parser de éxito completo rechazó conservadoramente el stack; no hubo stack en un evento PASS. El informe READY/DONE terminal corresponde sólo al último método. No se acredita matriz ni conservación desde los dos PASS sin sus recibos.

Sesión71171 cerrada por capacidad retenida, exit1 y recibo OS posterior vacío. Admisión ab7b consumida. El selector descContains de la fecha sin límite puede confundir 1 con11/21/31 y fechas mencionadas dentro de sufijos de vacaciones. Es defecto estático demostrado, sin conteo nativo archivado que determine exactamente este rechazo. Corrección mínima enviada al mismo Stage: anclar fecha inicial al productor real, exigir unicidad, conservar guardas/callback y probar bordes pertinentes, compilar runner y entregar PREinstall. Sin cambio de producto ni rediseño de parser. Revisión: .local-evidence/stage12/main/samsung-normal-qa-r3-final-review.json, SHA bfdce5cf0de97d62707087d16725d9180b9eef584680ccf5ce9c1b3b8904645f.


### Selector de fecha corregido y campaña R4 admitida

MAIN recibió y verificó 613 miembros/26218484 bytes, 398 insumos estables y tres archivos del arnés: helper y pruebas nuevos, consumidor con dos líneas cambiadas. El patrón reutiliza fullDisplayName productivo, anclado a inicio absoluto y coma/fin; conserva paquete, unicidad, guardas y callback único. Cinco JVM ejecutadas y compilación runner offline corroboradas en XML/logs; manifiesto del runner idéntico y firma verificada según recibo sellado. QA 0b65 sin recompilar, runner7aa2 nuevo. Tres pruebas host simuladas de pins/operador declaradas; MAIN comprobó diff real y suites exactas. Revisor independiente conforme y en cese. No prueba Android aún de esta candidata.

Se envió y Stage acusó una admisión nueva R4 para reemplazar sólo runner, verificar pareja y ejecutar una campaña exacta de 151 métodos/21 suites. Protocolo, cuerpos funcionales, orden, relojes, plazos y STOP siguen iguales. Revisión: .local-evidence/stage12/main/samsung-date-selector-r1-final-review.json, SHA 57e6d49fb22b298efbe94baf24893c72f52c1f5a6c1cd3246df1e59cbbababb8. Admisión: .local-evidence/stage12/main/samsung-normal-qa-r4-admission.json, SHA c2f7fd38f48a2bf3c7a1786f94682196999d1149e3fc135cf4f858785c999766. Resultado nativo R4 pendiente; anteriores permanecen consumidos.


### R4 auditada: avance funcional y expectativas anteriores a B02

MAIN verificó 273 miembros/316967 bytes, instalación exclusiva del runner y protocolos/raw de siete suites. Seis completas aprobaron 46 métodos; CalendarCommon 15 produjo nueve PASS observados y seis FAIL completos. Total: 61 START/55 PASS/6 FAIL, sin timeout, otros90 no ejecutados. Los cuatro readbacks Native3 son exactos al raw y acreditan tres recorridos completos y su proyección de negocio sin cambios; no se extrapola a toda la campaña. Sesión69171 cerrada por capacidad retenida, exit1 y recibo OS posterior vacío; admisión c2f7 consumida. Corrección de selector comprobada dentro del método nativo completo.

La causa compartida comprobada es de expectativas: B02 punto4 exige fechas en ambos extremos del detalle; EventTimeRange DETAIL y sus consumidores lo cumplen, mientras CalendarCommon aún busca horas solas. Revisor independiente conforme y cesado. Se devolvió corrección test-only de todos los equivalentes, incluidas expectativas no alcanzadas de Retén/disponibilidad, con valores independientes del formatter y aserciones íntegras. No producto por conveniencia. Próxima propuesta diagnóstica: 15 métodos corregidos y90 pendientes, manteniendo el catálogo151 y la atribución de los 46 previos al runner7aa2. No equivale a completar151 sobre APK nuevo ni recorta las dos rondas finales. Revisión: .local-evidence/stage12/main/samsung-normal-qa-r4-final-review.json, SHA ce2d81c617e6754b513407687f90c32f392e598714955fed28821b373746d2e2.


### Esperados B02 auditados y selección105 admitida

MAIN verificó 603 miembros/26411681 bytes, 398 insumos estables y único delta AndroidTest con siete literales explícitos de detalle. Se conservan conteo 2/UUID/tarjetas/callbacks/retornos/privacidad; producto y QA sin cambios. Compilación runner offline, manifiesto igual y firma corroborada por recibo sellado. Se recalculó el diff del operador y la partición 105 + 46 exacta sin superposición u omisiones; las quince suites coinciden con las pendientes del plan previo. MAIN ejecutó siete pruebas host con SDK simulado y procesos/red/DLL bloqueados, todas verdes. Dos lectores independientes conformes y en cese.

Se emitió y envió una admisión nueva con schema/action y campos methods 105/suites 15 explícitos para reemplazar sólo runner 7aa2 por b70e y ejecutar CalendarCommon 15 más los 90 pendientes, sin repetir 46 por rutina ni reatribuir su evidencia al nuevo APK. Las dos rondas finales completas no se recortan. Revisión: .local-evidence/stage12/main/samsung-detail-expectations-r1-final-review.json, SHA 72456379b88a906c37dc6e108dba269a2a0e3a985249f7b5bc9e113c065e299f. Admisión: .local-evidence/stage12/main/samsung-remaining105-r1-admission.json, SHA db4a7ef6d237314943d700fefe5ea39f9a94c967f11efcfb76fb925a2dfc7188. Stage acusó recepción; ejecución nativa nueva aún no auditada.


### Diagnóstico de 105 auditado: B02 confirmado; falta la anfitriona de Resumen

MAIN verificó 275 miembros/269766 bytes y los ocho protocolos/raw. Siete suites completas aprobaron 52 métodos sobre runner b70e; Summary 13 emitió 12 PASS y 1 FAIL completo antes de inyectar el primer clic. Total de 65 START, 64 PASS y 1 FAIL, terminal-1/ADB0, sin timeout, cero cats; otros 40 métodos no se ejecutaron. QA 0b65 conservada y sólo runner reemplazado. Sesión 75902 cerrada por capacidad retenida, exit1 y recibo OS posterior vacío. Los 46 previos siguen sólo en 7aa2; no hay 151 completos sobre una pareja ni ronda final. Admisión db4a consumida.

El stack getAllSemanticsNodes/fetchSemanticsNode no encontró jerarquías Compose al performClick de SummaryComposeTest:184. El método usa createComposeRule y estado local; el callback aún no se acredita, tampoco detalle/privacidad posteriores. No se encontró causa estática productiva ni cierre deliberado en renderer; los doce métodos posteriores pasaron, sin demostrar causa transitoria. Revisor MAIN independiente conforme y cesado. No relación demostrada con alerta de rendimiento Stage 8.

Se envió y acusó una capacidad local de observabilidad mínima de la anfitriona propia, contenido y fases de ese mismo método, conservando clic real/aserciones/error original. Próxima propuesta física: un método, con admisión nueva después de revisión; no retry ciego ni cambio de producto/dispatcher/esperas arbitrarias. Los40 pendientes se conservan. Revisión: .local-evidence/stage12/main/samsung-remaining105-r1-final-review.json, SHA 6ca2a03389e9121e6ccda5dd8522bd4879d3d924b1932954c3bb450006c077c2.


### Prioridad humana: continuar las cuarenta pruebas independientes

Joaquín ordenó seguir las cuarenta pendientes sin frenar todo por Resumen, resolver ese fallo en el mismo chat y continuar hasta las dos rondas finales, avisando sólo intervenciones necesarias. MAIN registró la autoridad directa en .local-evidence/stage12/main/continue40-human-priority-20261025.json y envió la reordenación al único Stage. Stage acusó recepción: el build local de observación había terminado, quedó preservado y no instalado, con dos hallazgos de revisión pendientes.

La selección de siete suites/cuarenta métodos se prepara sobre QA0b65/runnerb70e ya instalados y auditados, sin nuevo build ni instalación. Sus APK y compilación sellada son entrada independiente de las ediciones legítimas actuales de Summary/runner/recibos; no se exige igualdad global con código que ese APK no contiene. La candidata nueva de observación conserva su trabajo y no bloquea esta selección. Se requiere sólo revisión del delta mínimo del operador/plan y una admisión técnica nueva, sin permiso humano repetido ni reuse de db4a. Las rondas completas y Resumen siguen pendientes, sin reasignar resultados entre APK.


### Cuarenta admitidas sobre la pareja instalada, sin instalación

La selección exacta se recibió y auditó: 402 miembros/25628695 bytes, siete suites y cuarenta métodos idénticos al tramo pendiente del plan anterior. MAIN comprobó los 180 insumos pertinentes contra la compilación archivada b70e, recalculó el delta del operador y ejecutó siete comprobaciones host con SDK simulado y operaciones nativas bloqueadas; todas aprobaron. Lector independiente conforme y en cese. Se preserva la partición 46 + 52 + 13 + 40, con las atribuciones originales y Resumen pendiente.

Admisión nueva enviada y acusada para ejecutar una sola selección sin build/instalación. Revisión: .local-evidence/stage12/main/samsung-pending40-r1-final-review.json, SHA 90aa9086c7c6cd796d25db9d0e17faed3799aae0ce55fa39c39880803d1ff9f4. Admisión: .local-evidence/stage12/main/samsung-pending40-r1-admission.json, SHA 5508b7385e14b60dc69ab1f533caee9dbabba4669718173e868eadf5a1d1b8db. No se exige igualdad global de las fuentes actuales de observabilidad que no pertenecen al APK instalado. Resumen conserva dos hallazgos locales (sincronización añadida y preservación del error original) y no bloquea las cuarenta.

### Cuarenta pruebas completas aprobadas y auditadas; Resumen retomado

MAIN verificó los 228 miembros y 188107 bytes de la entrega, los siete protocolos y los registros originales de comandos. Las siete suites completaron 40 aprobaciones, sin fallos, omitidas ni timeout. Se reutilizó la pareja instalada QA 0b65 y programa b70e, sin compilar, instalar, reintentar ni leer recibos nativos después de los métodos. Los 180 insumos pertinentes siguen iguales a su compilación archivada. No se atribuyen a ese APK las ediciones nuevas de observación de Resumen.

La sesión propia 99711 terminó mediante su capacidad retenida, con exit1; el recibo posterior de sistema mostró cero procesos adb.exe y cero listeners en 5050. MAIN corroboró esos archivos sin consultar procesos, puertos ni teléfono. La admisión 5508 está consumida y los recursos son históricos. Revisión final: .local-evidence/stage12/main/samsung-pending40-r1-result-final-review.json, SHA dfbf27084d75bcdde65fee8a75a2d7030857c8ec6cef32d6f83c3981fb5859d3.

Estos resultados conservan los niveles reales de pruebas Compose, estructura de RemoteViews, archivos propios y proveedor de archivos. No prueban widget de Samsung, SAF humano, conservación universal, los 441 padres ni una ronda final. Los 46 métodos anteriores permanecen en el programa 7aa2; 52 anteriores y estas 40 aprobaciones pertenecen a b70e, en ejecuciones separadas. Resumen conserva su grupo mixto y causa histórica desconocida.

Se aceptó la entrega y se retomó Resumen en el mismo Stage. Los dos hallazgos locales requieren adquirir la anfitriona una sola vez en Main sin sincronización añadida, y mantener primario el error original del cuerpo frente al cierre o un agregado. Se pidieron regresiones de esas rutas y reutilización de la anfitriona después del cierre, conservando el clic real y todas las aserciones. Stage reporta los cambios escritos; todavía faltan validación y entrega estable. Ninguna nueva instalación o instrumentación está admitida. Siguen pendientes las capacidades nativas, OEM y humanas, y dos rondas completas sobre la misma pareja final.

La consulta posterior mostró el turno local interrumpido y el chat sin cargar, con reinicio del cursor. MAIN recuperó la misma unidad mediante un mensaje nuevo acotado, sin reenviar campañas consumidas ni autorizar dispositivo. El transporte fue aceptado y se observó el turno 01a17232-5052-7ee3-9a88-51ef70ef5b77 activo; el acuse aún estaba pendiente. Recibo: .local-evidence/stage12/main/samsung-summary-interrupted-recovery-20261025.json. La recuperación exige atribuir la compilación anterior desde recibos y capacidades retenidas antes de repetirla.

### Resumen local recibido: fuente conforme, contradicción del protocolo devuelta

MAIN recibió la entrega stage12-summary-observation-local-r1-17893a96-3e37-4319-a297-1a314758c9ef y verificó sus 803 miembros y 31253291 bytes, sello e identidad de artefactos. Los 401 insumos coinciden antes/después y con la fuente actual; el diff recalculado confirma cuatro archivos existentes y tres nuevos de pruebas/soporte, sin producto o Gradle. El XML final informa 16 regresiones frescas y la compilación terminó con código 0. FailureOrder está conectado a la ejecución real; la regresión con Statement y RunAfters cubre cuerpo y cierre distinto/agregado. Dos revisores independientes cesaron. QA 0b65 reutilizada; runner 4120 todavía no instalado. MAIN comparó su manifest binario con b70e y extrajo el certificado del bloque v2, coincidente; la verificación criptográfica apksigner corresponde al recibo primario del Stage.

MAIN repitió catorce pruebas host/protocolo con primitivas nativas y lecturas SDK bloqueadas. Aprobaron, pero un positivo sellado contenía una contradicción: START llevaba el reporte EARLY con FAILED / FILES_FAILED / IO y el terminal DONE. Una reproducción específica de MAIN obtuvo PASSED_OBSERVED_METHOD. El consumidor no conservaba el fallo irreversible de reportes anteriores y podía habilitar readbacks. No se emitió admisión ni se ejecutó esa candidata. PUBLISH/PENDING del archivo sigue siendo un estado válido, sin defecto demostrado.

Se devolvió al mismo Stage la corrección local completa del historial de fallos, un inicial real del productor para el positivo y el adverso contradictorio con STOP sin readbacks. Debe usar archivos versionados y entrega nueva, preservando la anterior; reutilizar la pareja y JVM intactos en vez de reconstruir por rutina. Los 401 insumos vivos referidos por el sello quedaron copiados exactamente en .local-evidence/stage12/main/summary-observation-r1-input-custody/custody.json antes de futuros cambios autorizados. Revisión: .local-evidence/stage12/main/samsung-summary-observation-r1-final-review.json, SHA fe9c7ec19c15e94d0a520b349fe0c649e1c44537ba141e69374e0cf22cae0440. El contraejemplo es host sintético, no Android ni causa del fallo histórico. Siguen 0/2 rondas finales.

### Consumidor r2 aceptado; admisión exacta para un método de Resumen

MAIN recibió y verificó 1843 miembros y 32955191 bytes, incluidos los 803 originales intactos. El delta del consumidor fue recalculado. Un inicial real del productor JVM existente reemplaza el positivo inválido; los cinco reportes anteriores conservan sus bytes lógicos y los 401 insumos siguen exactos. Se reutilizan la pareja y las dieciséis regresiones JVM, sin reejecutarlas. La lectura inicial de un nombre largo de clase falló por la ruta ordinaria de Windows; la lectura con ruta local extendida permitió comprobar todos los miembros, sin modificar entradas.

MAIN ejecutó diecisiete pruebas host de protocolo/operador, con procesos, red y lecturas SDK bloqueados. Los tres contraejemplos aceptados por v1 ahora se rechazan conservando el primer fallo y el terminal separado; el operador no hace cats ante esas contradicciones o UTF8 inválido posterior. Ambos revisores independientes confirmaron consumidor y operador/plan y cesaron. La publicación pendiente sigue siendo válida. Revisión: .local-evidence/stage12/main/samsung-summary-observation-r2-final-review.json, SHA d4a8558684a03bf4de09d013a52952ba13d09315d9c30da8b151ca27d6e82ba0.

Se emitió y envió una admisión nueva de instalación exclusiva del runner 4120, conservación de QA 0b65 y un método funcional completo mediante el operador v2, con transporte propio nuevo si hace falta. MAIN validó las once sentencias originales de admisión antes de hash SDK y creación de salida, sin operaciones nativas. Admisión: .local-evidence/stage12/main/samsung-summary-native-r1-admission.json, SHA a0903cf2aa5e0ab0a4711759e057f2d00834c765c90b4e5962dcf75a1dfc4d13. Transporte aceptado; ejecución nativa y cierre todavía pendientes de corroboración. Sin permiso humano repetido. Un pase no completaría los trece métodos de Resumen ni una ronda final; siguen 0/2.

### Un método funcional de Resumen aprobado en Samsung y auditado

MAIN verificó los 130 miembros/687414 bytes, reanalizó el raw con el protocolo v2 y cotejó identidad, instalación, comandos y dos recibos propios. Resultado: 1/1 método Compose completo, cero fallos/omitidas/timeout, terminal completo y sin fallos previos; UUID 4c89a12b-13d3-4590-abc4-feddc9b64a68. La consulta de overview midió un nodo, el clic original retornó, hubo un callback correcto en Main y se alcanzaron el detalle y las ocho aserciones. Contenido entrado y dispuesto una vez; misma anfitriona observada DESTROYED. Archivo PUBLISH/PENDING y terminal DONE/publicación completa coherentes. La traza declara truncamiento y no se presenta como exhaustiva. Raw SHA e7de97746bff13d188a99132e23ea8fc2fca3caf39361a178e728311596dca3c.

Los once comandos previos identificaron Samsung y pareja antigua y realizaron una única instalación del runner 4120. Los trece del operador verificaron identidad/pareja/target, instrumentaron una vez y leyeron exclusivamente los dos recibos del UUID después del PASS. QA 0b65 conserva bytes y no fue reinstalada. Servidor propio de sesión 85379/PID histórico 13124: nacimiento posterior a intención, prueba de pertenencia anterior al primer cliente, cierre por Ctrl-C de la capacidad retenida con exit1 y recibo OS posterior vacío de ADB/listeners5050. MAIN no consultó sistema o teléfono. Dos lectores independientes conformes y en cese. Admisión a090 consumida y sin recursos propios declarados pendientes.

Revisión: .local-evidence/stage12/main/samsung-summary-native-r1-final-review.json, SHA be5a95a1f54bd9ce7a37aed6b3cfea4d629fb12f4df061394a07aa548af925c4. El pase acredita un método Compose en su anfitriona de regla, no el recorrido MainActivity/SummaryViewModel, la suite de trece, causa histórica, conservación universal ni ronda final. El observador puede alterar tiempos mediante ControlledLooper. Las rondas siguen 0/2.

Se envió al mismo Stage una capacidad local cohesiva: completar C090, W010 y X015, resolver o acotar por separado la observación de recursos propios de W023 y preparar bindings por obligación y el programa concreto de las dos rondas. La suite de trece de Resumen entra en la siguiente selección pertinente o rondas finales, sin otro ensayo aislado como precondición. No más tooling de observación sin defecto concreto. Nuevas fuentes tienen preimágenes/custodia y delta; se conserva la evidencia histórica y su atribución. Gradle/Java propios locales permitidos; sin queries de dispositivo, OS o recursos nativos hasta nueva admisión técnica.

### Frontera de implementación: desviación declarada y contención

Stage confirmó que había asignado escritura de pruebas nuevas de C090, W010 y X015 a tres agentes internos, fuera de la regla vigente de único implementador. MAIN había pedido aclaración ante el mensaje ambiguo; la confirmación es explícita y no una inferencia. Stage reportó interrupción de los tres turnos y orden de no seguir escribiendo, probando, revirtiendo o descartando. También declaró que Gradle y teléfono siguieron exclusivos de root y que esta unidad aún no los había usado. Estas son declaraciones pendientes de los tres recibos individuales de cese y sus deltas.

MAIN ordenó preservar autores, rutas, última operación, bytes/hashes y preimágenes antes de que el único implementador modifique los mismos archivos; si queda una escritura incierta se aísla sólo esa frontera y continúa lo independiente. El contenido dentro del alcance puede revisarse e integrarse con su procedencia, sin descarte ni falsa atribución. No se agrega otro chat, implementador, permiso de dispositivo o aprobación humana; se corrige la aplicación de la regla ya vigente. Recibo: .local-evidence/stage12/main/native-completion-writer-incident.json.

Stage entregó los tres ceses declarados y el registro de último estado sin herramientas/escrituras pendientes. MAIN comprobó siete copias parciales atribuidas, sus SHA y la ausencia de esas rutas en la entrada anterior; no inspeccionó las ediciones vivas ni consultó procesos. El único implementador continúa después de esa preservación. Las fuentes parciales aún no fueron compiladas o aceptadas; el incidente y procedencia deben acompañar la entrega final. Revisión de cese: .local-evidence/stage12/main/native-completion-writer-cese-review.json.

### Reanudación y candidata nativa local R1 — revisión MAIN (UTC 26/10)

La orden humana «Continuamos!» levantó la pausa total. Se retomó el mismo Stage y un único supervisor. La entrega `stage12-native-completion-local-r1-0c66510a-c64f-4123-b0d2-2f05a65484de` quedó recibida fuera del payload antes de auditar: 194 miembros y 59307209 bytes coinciden. MAIN comprobó 17 deltas/preimágenes, 745 pins, 30 JVM atribuibles y ambos APK/certificados/manifiestos. QA `7313284e…b39d9` y runner `63b7d15f…d1093` están compilados, no instalados. El único cambio de producto limpia datos y acciones residuales al ocultar información del Widget. Dos lectores de sólo lectura cesaron conformes en C090/W010/X015; no es prueba Android.

El programa mantiene V5, sus 441 casos, 506 subcasos, 1627 obligaciones y 384 aplicaciones visuales. Doce bindings están definidos y 1615 gaps siguen explícitos. La selección concreta prepara tres métodos nativos y trece de Resumen, sin crédito de ronda. Rondas finales: 0/2.

Decisión: corrección LOCAL del control antes de admisión física. El operador exige custodia de servidor por MAIN aunque corresponde al único Stage, y su `finally` envía hasta cuatro consultas después de un rechazo/timeout. MAIN repitió 15 pruebas host y reprodujo ambas salidas post-fallo mediante `execute` simulado; cero SDK/procesos/red reales. El rechazo de protocolo incompleto también debe conservar incertidumbre nativa, no inferir cese del retorno del cliente. Se enviaron hallazgos al mismo Stage como una unidad append-only; APK, JVM y sellos anteriores se reutilizan según sus pins, sin reconstrucción por rutina.

Informe: `.local-evidence/stage12/main/native-completion-preseal-review-20261026/final-review-r1.json`, SHA-256 `e0587a93241ca68423f4c3e12f41c9cdbe99b8b4f08191e0659d17f0726b5b9c`. Las reproducciones y los recibos de transporte están junto al informe. No nueva admisión ni operación Samsung desde MAIN.

### Control R2 aceptado y transporte/actualización admitidos (UTC 26/10)

MAIN verificó 1799 miembros/5675903 bytes, preservación de R1 y 745 pins. Repitió 14 pruebas execute con procesos/sockets/Win32 simulados: verdes, cero clientes posteriores a rechazo. El lector de control cesó conforme. Se corrigió prospectivamente el literal del servidor mediante la admisión MAIN: se reutiliza la forma corroborada -L tcp:5050 con serial único, entorno USB y listener loopback propios; el documento sellado se preserva. Informe en `.local-evidence/stage12/main/native-completion-control-r2-review/final-review.json`, SHA e0f8285ead13cefc6723670bb7f2dcd76641cefb2d69a932c2433da7ec0d9378.

Admisión nueva de transporte/identidad y actualización compatible de ambos APK: `.local-evidence/stage12/main/native-completion-control-r2-review/transport-install-admission.json`, SHA fed45f87da94ea05f160574357b9e532868a89b78a3a09e874982846ccc6e8bc. UUID eb0a0f19-d674-4b91-b077-108f1166c301. Envío aceptado; identidad/instalación real y comprobante de servidor todavía pendientes. Stage conserva la capacidad, MAIN no opera recursos. La instrumentación de 16 requiere fijar después el hash de ese comprobante actual; no hay permiso humano nuevo pendiente. QA/runner aún se consideran no instalados hasta sus readbacks reales; rondas 0/2.

### Intento de transporte detenido por conversión de fecha (UTC 26/10)

La admisión fed45f87 se consumió antes del primer cliente ADB. El preflight de Stage acreditó ausencia de adb.exe/listener5050 y se inició un foreground propio en la sesión 62204. La guarda observó path/comando/nacimiento del proceso 17176 y falló con MethodInvocationException antes de consultar listener. No hubo consultas de identidad Samsung/paquetes, instalación o instrumentación. Stage cerró una vez la capacidad original mediante Ctrl-C/exit1; no hubo postquery de hijo/listener y su ausencia no se inventa.

MAIN reprodujo sólo con JSON y expresiones locales la causa: ConvertFrom-Json ya produce DateTime; DateTimeOffset.Parse lo convierte a texto cultural y falla. La corrección local de tipos/ISO/UTC se envió al mismo Stage, sin nuevo recurso ni repetir APK/JVM/execute. Recibos y reproducción: `.local-evidence/stage12/main/native-completion-control-r2-review/phase1-adverse-received.json` y `phase1-local-date-expression-review.json`. La siguiente tentativa exige guarda corregida comprobada y admisión/UUID nuevos; no hay permiso humano pendiente.

### Guarda de fechas aceptada y nueva tentativa física (UTC 26/10)

MAIN recibió/verificó los 16 miembros y 25641 bytes de transport-guard-r1 y repitió sus 10 pruebas offline con los JSON reales. Todas pasaron; se conservaron nacimiento estricto y las guardas de identidad/propiedad. El lector independiente cesó conforme. No hubo consultas de recursos en la revisión. Informe `.local-evidence/stage12/main/native-date-guard-review/final-review.json`, SHA 02e826ec5e47ca75933b5d776ba3bd8ea05048981f8b136202388c2054a627bf.

Se envió una admisión nueva de transporte/identidad/actualización compatible: `.local-evidence/stage12/main/native-date-guard-review/transport-install-admission-r2.json`, SHA 5f486141eb9576904aa3eddd66192ab6e86936c50203438fac9cbdc5cbd9db76; UUID 85f3e08e-7acf-4dc9-b5d8-143dcf5927c5. Transporte aceptado y ACK observado. El intento anterior queda consumido. Fuente/APK/control R2 se reutilizan, no se repiten sus baterías. La nueva identidad/instalación aún debe comprobarse; instrumentación pendiente del comprobante actual pinneado.

### Actualización Samsung comprobada y 16 métodos admitidos (UTC 26/10)

MAIN auditó 17 comandos/68 piezas de fase1: Samsung SM-S938B/API36/user0/serial explícito, pareja anterior exacta, una instalación compatible de cada APK, dos Success y readbacks instalados QA 7313/runner 63b7 con target NormalQaRunner→QA. Ningún fallo/stderr/timeout. Corroboró ausencia→intención→nacimiento→READY→clientes, listener/propiedad y observación de la capacidad actual de Stage. Se espera retención por install-r; no se afirma conservación universal del dataset.

Informe `.local-evidence/stage12/main/native-install-phase1-r2-review/final-review.json`, SHA 08648fe64bcef0e3c26b377a0035874dcc41878338a0ba726e9b146299b85046. Proof actual SHA 28afa96ca602c2aa7d4c8c51aa75148b1380854937e55014bdc7b9e16c132714, válido hasta 02:12:51 UTC. Stage conserva la capacidad original 85727; MAIN no la opera o adopta. La admisión de transporte 5f486141 está consumida con éxito.

Se emitió `.local-evidence/stage12/main/native-install-phase1-r2-review/clients16-admission.json`, SHA 8a793064fdade8e6a9803fa399da072682423110cd38b4f711b8a7949bbdc931, UUID 85f3e08e-7acf-4dc9-b5d8-143dcf5927c5. El admisor real verificó cuatro bloques/16 métodos sin recursos reales desde MAIN; envío aceptado y Stage confirmó inicio. Todavía sin resultado Android auditado ni crédito de ronda. Fuente/pareja no se modifican; STOP conserva raw/incertidumbre y cierre propio sin clientes adicionales.

### Primer bloque rechazado; continuidad de 14 métodos independientes (UTC 26/10)

MAIN recibió la entrega adversa y verificó sus 45 miembros y 53804 bytes, los nueve comandos y el registro original. Se inició un método C090 y falló completamente, sin aprobados, omitidos o timeout; los otros 15 no corrieron. El cliente retornó 0 y el protocolo contiene START, FAIL y terminal -1. El rechazo «Stack contradicts success» es conservador; no demuestra corrupción de transporte.

La pantalla propia llegó a READY, con foco, pantalla encendida y sin bloqueo. Falló el primer snapshot de Stage12FullStoreReadback con READBACK_UNEXPECTED_PRIVATE_ROOT, antes de elegir o insertar las jornadas de la fixture y antes del borrado. La guarda exige una lista cerrada de nombres privados; el registro no distingue una entrada adicional de un listado nulo. No se conoce el nombre ni se atribuye un defecto al producto. El arranque y los archivos propios sí ocurrieron; no se afirma ausencia universal de efectos o conservación completa.

Stage cerró una vez su capacidad original 85727 mediante Ctrl-C y obtuvo exit1. No hubo clientes, lecturas, suites, reintentos ni consultas de procesos o puertos posteriores al STOP. No está demostrada la ausencia posterior del hijo o listener ni el cese global de Android. Sesión 85727 y PID 22788 son históricos; la admisión 8a793064 queda consumida. La incertidumbre original del controlador se conserva junto a la terminal completa observada.

Revisión MAIN: `.local-evidence/stage12/main/native16-r1-stop-review/final-review.json`.
SHA-256: c02f2be6aa6ae682aae0c4ec3ee3830037b0ddcc69b9560b593497e3892a61b1.

Se envió una sola continuación local al mismo Stage: preparar primero W010 y los 13 métodos de Resumen sobre los APK ya instalados, sin compilación o instalación. C090 y X015 comparten el lector y quedan dependientes. El control actual exige 16: debe versionarse mínimamente para la selección exacta de 14, probar su flujo y entregarse estable antes de otra admisión. Los pins pertinentes se contrastan con las fuentes archivadas de runner 63b7; las ediciones legítimas del lector no congelan todo el desarrollo ni se atribuyen a ese APK. Stage confirmó esta prioridad. No hay permiso humano pendiente ni nueva ejecución nativa admitida.

El lector debe descubrir y contabilizar las entradas QA pertinentes, con límites de ruta, enlaces y lectura, sin adivinar un nombre o ignorar entradas para obtener verde. Se pidieron regresiones locales de entradas adicionales/directas, listado fallido y escapes. No se autorizó investigación forense nueva en el teléfono.

La revisión independiente del programa distingue 12 vínculos declarados y 1615 todavía sin vínculo verificable entre obligación, aserción y evidencia del nivel exigido. No equivale a 1615 pruebas nuevas. Hay candidatos nominales en filas de 560 de esas obligaciones, sin aceptación automática de sus aserciones o niveles. El adaptador actual sólo acepta NATIVE_RECEIPT de tres casos; las dos rondas completas siguen sin estar preparadas ni ejecutadas. Informe: `.local-evidence/stage12/main/native16-r1-stop-review/matrix-gap-readonly-review.json`. Rondas finales: 0/2.

### Selección independiente de 14 auditada; transporte nuevo sin instalación (UTC 26/10)

MAIN registró RECIBIDO antes de auditar y verificó 672 miembros/4454249 bytes, sin diferencias ni miembros adicionales. Las 30 fuentes archivadas corresponden a los pins de compilación de runner 63b7 y los APK locales conservan sus hashes. La selección es exactamente W010 y los 13 métodos originales de Resumen, con reloj REAL y plazos 360/480s; C090/X015 no se ejecutan. Las obligaciones y aplicaciones visuales no cambiaron. execute y el protocolo son idénticos al R2 aceptado; sólo cambia la guarda y selección concreta.

MAIN repitió las 16 pruebas host del flujo y cuatro rechazos adicionales de selección. Todas pasaron con respuestas simuladas y primitivas de procesos, sockets y Win32 bloqueadas; cero recursos reales. Se preservó un fallo MAX_PATH del primer arnés MAIN y se corrigió en su copia mediante rutas extendidas. Un lector independiente de controles terminó conforme y cesó. La mención heredada de 16 en el protocolo documental queda expresamente limitada a 14 por su aclaración final y la admisión concreta, sin modificar el sello. No hay evidencia Android nueva por estas comprobaciones.

Entrega: stage12-independent14-local-r1-99bf40e9-fd99-4257-a268-4e8b6015efab.
Manifiesto SHA-256: 19e7d9e841e252c824440110d5e4b9c82e5ad7aed0b789d46fae7f31954c26c5.
Revisión MAIN: `.local-evidence/stage12/main/independent14-r1-review/final-review.json`.
SHA-256: f3338d25b206e016683a87a6f740976e517e0529e156ec913fec193bb13aae93.

Se envió una sola admisión nueva para recurso propio e identidad/bytes/target actuales, SIN instalar o instrumentar: `.local-evidence/stage12/main/independent14-r1-review/transport-admission.json`, SHA dedbcb782f8bba314976e0b13b03bf2f9c1cd33af513be01e3a6b1c14af1207e, UUID 80ede8e9-b9ef-403d-b2f0-dcf6d75fe0b1. Stage confirmó recepción. Tras la prueba dinámica actual, MAIN fijará su SHA para admitir clientes14 sin otra preparación opcional o permiso humano. Las fuentes vivas del lector se preservan aparte; su corrección no bloquea el código instalado independiente. Rondas 0/2.

Antes de recursos, Stage detectó un filtro de target incorrecto en la admisión de MAIN. MAIN corroboró los comandos primarios 009/016 de la fase anterior: el vector correcto es `shell pm list instrumentation com.blackatsystems.miguardia.qa`, no el paquete de pruebas. Se preservó dedbcb sin consumo y se envió la admisión completa corregida `transport-admission-r2.json`, SHA 45b81f67760e58e4b5610e4fb80f3cc855413ea20278f769ae30fa01836680b7, mismo UUID y salidas aún ausentes. No se ejecutaron recursos para esa corrección ni se repitieron APK/JVM/host por el cambio literal. Evidencia: `target-vector-offline-correction.json` en la revisión MAIN de independent14.

### Identidad actual auditada y clientes14 admitidos (UTC 26/10)

MAIN verificó diez comandos/40 piezas y sus registros originales: cinco comprobaciones de identidad Samsung, cuatro de bytes QA/runner y el target QA corregido. Todo retornó 0, sin stderr o timeout. Cero instalación, am start o instrumentación en esa fase. Ausencia, intención, nacimiento, path/hash/comando/listener/owner y observaciones de la capacidad original Stage son coherentes. No hay evidencia de conservación universal o ejecución funcional por ese hito.

Revisión: `.local-evidence/stage12/main/independent14-r1-review/phase1/final-review.json`, SHA 4df0affd6381169ae43f19d9ec27a7f3214da5ae5eb01127e9c5599b27a372dd. Prueba actual SHA 476925d8df75d18a968cd3a913c8611ef275d081d33a01455769e2da1017638e, válida hasta 03:19:11 UTC; Stage custodia la capacidad original 14512, sin adopción MAIN. La admisión de transporte 45b queda consumida con éxito.

Se envió una única admisión de clientes14 sin instalar, `.local-evidence/stage12/main/independent14-r1-review/phase1/clients14-admission.json`, SHA a5960dd082ff392301241c3375a2bbfa38b68d7221f513651be015afc6526af5, mismo UUID 80ede8e9-b9ef-403d-b2f0-dcf6d75fe0b1. MAIN comprobó el admisor real con recursos bloqueados: exactamente W010 y Summary13, dos bloques/14 métodos. Stage confirmó inicio; resultado nativo todavía no recibido. Guardas/STOP/cierre por capacidad propia y límites de incertidumbre se conservan. Cero crédito de ronda.

El lector local separado fue recibido por clave/hash antes de auditar: `.local-evidence/stage12/main/readback-r1-review/received.json`. Nueva candidata runner a15a no instalada ni admitida; revisión estática independiente sólo lectura en curso, sin bloquear las 14 sobre runner 63b7.

### W010 adverso cerrado; trece métodos de Resumen aprobados y auditados (UTC 26/10)

MAIN auditó el adverso W010 de 54 miembros/96151 bytes, nueve clientes y un FAIL completo sin timeout; los trece Summary no corrieron en esa operación. Se preservó el primer sello y el addendum que ubica el rechazo en View.contentDescription o stateDescription, línea 83, sin campo/ID/valor concretos ni causa atribuida. Capacidad 14512 cerrada por su original/Ctrl-C/exit1, sin consultas posteriores; admisión a596 consumida. Revisión `.local-evidence/stage12/main/independent14-stop-r1-review/final-review.json`, SHA 041d80da0a9c21756b0c11a605abe5ff453950a3130589060f092fbafb41ce53.

Summary13 se preparó independientemente sobre la pareja 7313/63b7, sin instalar. MAIN verificó 547 miembros/4313753 bytes y treinta fuentes archivadas, execute normalizado idéntico, 17 pruebas host y cinco rechazos de selección conformes con recursos bloqueados. Tras la nueva fase de identidad/bytes/target, clients13 4f5c ejecutó una sola instrumentación. La entrega nativa de 61 miembros/73235 bytes quedó auditada: 13 START/13 PASS, cero FAIL/SKIP/timeout, COMPLETE/malformed=false y terminal -1. MAIN reanalizó los bytes originales con el consumidor normal, cotejó los trece comandos y bytes de pareja antes/después y fuente Summary atribuible. Cero cat, otro método o retry. Capacidad 43667 cerrada mediante original/Ctrl-C 40dedc/exit1, sin queries posteriores; no se infiere ausencia de listener, cese global o censo continuo. Admisión 4f5c consumida con éxito.

Revisión MAIN: `.local-evidence/stage12/main/summary13-result-r1-review/final-review.json`.
SHA-256: 27a33cbc85302fa616bba5ceb867a7eafda69da1201b0ebf39a76280e6e5e867.
Raw SHA-256: c47fa08db3e8d4d3a877b1baf84682fa7cb8f6d2ee8cc9d500fe657e15d8a390.
Acredita métodos Compose en su anfitriona propia; no MainActivity global, causa histórica, aceptación humana, conservación universal o ronda. No repetirlos por rutina ni reatribuirlos a otra candidata. Rondas 0/2.

MAIN también corroboró el lector local: 44 miembros/25739972 bytes, siete fuentes/tres preimágenes, XML 25 JVM y compile con 749 pins iguales. Revisor independiente conforme y cesado. Informe `.local-evidence/stage12/main/readback-r1-review/source-build-review.json`, SHA eb5bf3c0d3542a3e2225cc9f34b46cc5645b652ba357641baf5ccfaf31c3476e. W010 observacional: 27 miembros/25402114 bytes, cuatro fuentes/dos preimágenes, XML 7 JVM y compile con 751 pins iguales; conserva las siete fuentes del lector, Oracle/fixture/renderer/NormalQaRunner. Sólo metadatos seguros al mismo rechazo, sin excepción Chronometer o cambio de criterios. Revisor conforme y cesado; informe `.local-evidence/stage12/main/w010-observation-r1-review/source-build-review.json`, SHA 72aeb135ba3196dc9013ab1954d2ea9246f3400e96676e26b8a0e3d755ea164b. Candidata combinada cffa archivada, NO instalada; las 25 pruebas anteriores conservan atribución, no son una corrida nueva con cffa.

El siguiente contrato local ya fue enviado una vez: `.local-evidence/stage12/main/summary13-r1-review/prepare-combined3-local-message.md`. Preparar unidad conjunta y PRE-install concreto para C090, X015 y W010 observacional al final, reutilizando artefactos/pruebas exactos si siguen iguales. Faltan programa/control/certificación antes de nueva actualización sólo del runner y nueva admisión nativa. Sin permiso humano rutinario pendiente ni otra pregunta de disponibilidad. Se mantienen V5, gaps y las DOS rondas finales pendientes.

### Candidata conjunta aceptada y actualización sólo de runner admitida (UTC 26/10)

MAIN verificó 833 miembros/30064558 bytes, las 66 fuentes/recursos archivados contra el build de 751 insumos y procedencia de once deltas/cinco preimágenes. Las 25+7 JVM y compilación exactas se reutilizan, sin repetir o reatribuir Android. Execute normalizado y protocolo permanecen iguales al R2; orden C090→X015→W010, tres métodos completos REAL/360. MAIN repitió 18 pruebas host del flujo y seis rechazos de selección, todas conformes y con recursos bloqueados. Revisor independiente conforme y cesado; el fallo final W010 conserva los dos PASS anteriores y cero clientes posteriores.

MAIN corroboró el recibo apksigner verify de runner cffa con exit0 y extrajo independientemente el certificado DER v2: b691 exacto. Ambos manifest binarios son idénticos a los anteriores. Advertencia JBR25/Conscrypt y rechazo del wrapper local preservados; no se repitió o suprimió el verificador ni se relajó el stderr nativo. Revisión `.local-evidence/stage12/main/combined3-r1-review/final-review.json`, SHA 53638390c5756a2d12422c9f92d9cd94d9421af52d199e7ce05378cd1bda720f.

Se envió una vez la nueva admisión `.local-evidence/stage12/main/combined3-r1-review/transport-runner-update-admission.json`, SHA 50dab4b7e5ba37c81c331a21b27b746888d73f4114d0c48472874fb091f21024, UUID 748000da-bb05-4ee3-834c-1810b27af3b0: transporte propio actual, identidad/pareja anterior exacta, una actualización -r -t --user 0 sólo del runner cffa y bytes/target posteriores. QA 7313 no se reinstala. Todavía sin instalación o instrumentación nueva observadas. En éxito se recibirá prueba/SHA actual antes de admitir los tres métodos, sin permiso humano rutinario o preparación opcional. Mismatch/fallo detiene sin reparar/retry/forensics, cierre sólo por capacidad original actual. Rondas 0/2.

### Runner conjunta instalada y tres métodos admitidos (UTC 26/10)

MAIN verificó 16 comandos/64 piezas de la fase1: identidad Samsung, pareja anterior 7313/63b7, una instalación compatible sólo runner cffa con Success, QA 7313 intacta en bytes y nuevo componente/target exactos. Cronología de intención/ausencia/nacimiento/READY/clientes y observaciones de la capacidad Stage actuales conformes. No se instaló QA ni se abrió o instrumentó en esa fase. Revisión `.local-evidence/stage12/main/combined3-r1-review/phase1/final-review.json`, SHA 46376f3c5cdf628d2741f155fc966feb06e7c1efacec08f9874d043fcd04ec61. Prueba actual 999825a06e0e0a93b5ac09d31c48a66465034a4be30c60c524fe467624959249, vence 04:14:02 UTC. Stage mantiene capacidad original 92914 y PID 17232; MAIN no la adopta ni opera.

Se emitió una vez `.local-evidence/stage12/main/combined3-r1-review/phase1/clients3-admission.json`, SHA 0f5c4abb9f9c6226e894853eea524b429f3dc406a567a9affaecfc69d7132ed5, mismo UUID 748000da-bb05-4ee3-834c-1810b27af3b0. MAIN validó el admisor real con recursos bloqueados: orden C090→X015→W010, tres métodos completos REAL/360. Sin instalación adicional, repetir Summary o alterar criterios por la hipótesis Chronometer. Resultado nativo todavía pendiente; cero crédito de ronda. STOP, recibos sólo tras PASS y cierre por capacidad propia se conservan.

### Candidata W010/X015 conjunta aceptada; nueva actualización admitida (UTC 26/10)

Tras los adversos sellados de W010 y X015707, el reinicio humano fue confirmado y no se convirtió en prueba de corrección. MAIN recibió la unidad conjunta fuera de su payload y verificó 1624 miembros/63697724 bytes, 655 entradas estables, 57 fuentes y ocho deltas propios de pruebas/soporte. Tres archivos adicionales heredados conservan los bytes aceptados de707. Sin producto, permisos, Gradle o dependencias nuevos. C090 queda preservado, todavía pendiente de cierre físico.

El criterio efectivo W010 cambia mediante controles públicos frescos por propiedad, igualdad exacta y ventana/configuración/neutralidad acotadas; los literales privados siempre se rechazan. X015 separa siete operaciones de Atrás, con valores únicos nullable, guardas y cortocircuito originales. MAIN corroboró XML9 reutilizados y24 finales, sin repetir JVM; ejecutó45 pruebas host y9 interoperabilidades JVM→parser, más4 compatibilidades, con recursos reales bloqueados. Dos lectores existentes conformes y cesados. APK3f5, manifiestos iguales a707, certificado DER b691 y recibo apksigner corroborados. No acredita Samsung, causa histórica, Bundle Android o ronda.

Revisión: `.local-evidence/stage12/main/cohesive-w010-x015-r1-review/final-review.json`, SHA ca4068c0219422f05262159dc75fcf8616f44bc293e4d8336a486c6535783445.
Se envió una vez la admisión nueva de transporte/identidad y actualización sólo del runner, SHA328ae0fcb34de25fd73dabce8dddd9e4deebd184cca7b898624e477aa647839d, UUID0392741d-f0c7-448c-927f-6a5b73a57e77. Stage acusó recibo. QA7313 permanece; previo esperado707 y nuevo3f5. Dieciséis clientes exactos, sin instrumentación en fase1; proof actual/SHA/TTL preceden a la admisión separada de W010. Instalación y resultado todavía pendientes, sin éxito inferido. Rondas0/2; programa integral y capacidades restantes siguen pendientes.


### Samsung después del reinicio: actualización verificada y W010 invocado

MAIN verificó los 16 comandos y 64 piezas de identidad/actualización compatible del runner: QA 7313 sin instalación, runner 707 → 3f5 y target exacto. Se corroboraron lanzamiento y lecturas de la capacidad original Stage. Revisión: `.local-evidence/stage12/main/cohesive-w010-x015-r1-review/phase1/final-review.json`. Admisión nueva W010 `64d3b0f5` enviada una vez. Stage reportó fallo por controles públicos divergentes y cierre propio; entrega estable pendiente de auditoría. Sin nuevo PASS ni crédito de ronda; X015 independiente conserva candidata revisada.


### Reanudación local y autorización de emulador — 26/10

Joaquín reanudó el mismo Stage, informó falta de Samsung y autorizó expresamente la salida externa propia al puerto 443 del emulador existente. La decisión resuelve ese gate de red; no acredita finalidad conocida, arranque actual ni permisos sobre el teléfono. Autoridad y contrato en `.local-evidence/stage12/main/emulator-human-authorization-20261026T075840Z/`. Stage prepara reutilización mínima del AVD, controles y selección para admisión técnica, sin repetir G3 por rutina.

El adverso W010 3f5 quedó auditado: 120 miembros, 138566 bytes, 115 copias exactas; nueve clientes nativos, un FAIL completo y cero readbacks o retries. Controles públicos desiguales, causa exacta desconocida. Se corroboró el cierre por capacidad original. Revisión `.local-evidence/stage12/main/w010-public-control-native-stop-r1-review/final-review.json`. Los flags false dentro del objeto pair son metadatos prospectivos heredados, no niegan instalación o ejecución acreditadas. Se conservan sellos.


### 26/10 08:16 UTC — autoridad histórica confundida y contenida

Stage interpretó dos mensajes humanos retenidos por compactación como disponibilidad nueva del Samsung. MAIN verificó sus fechas originales (25/10 06:06 UTC y 26/10 04:43 UTC), anteriores al teléfono no disponible y a la autorización del emulador. Envió corrección inmediata; Stage reconoció el error y retomó el emulador.

Hubo un inicio ADB foreground con literal histórico no compatible; falló antes del primer cliente. MAIN corroboró los tres archivos originales, el script y la herramienta primaria: cero comandos al dispositivo, apertura de QA, instalaciones o instrumentaciones; cierre del handle Popen propio con código 3221226505. No se afirma ausencia global de procesos/efectos. El intento y su autoridad mal atribuida se preservan, corregidos fuera del resultado. Revisión: `.local-evidence/stage12/main/stale-samsung-authority-20261026T0814Z/final-review.json`.

Aprendizaje: al recuperar mensajes tras compactación, usar su fecha original y la autoridad vigente del checkpoint; su reaparición no renueva permisos consumidos ni disponibilidad. No se modificó la skill ni se amplió el alcance. Samsung sigue en espera; preparación del emulador continúa con la excepción TCP443 autorizada.
