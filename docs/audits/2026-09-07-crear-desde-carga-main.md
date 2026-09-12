# Crear desde la carga — auditoría MAIN

Fecha: 2026-09-07. Contrato: `docs/prompts/CREAR_DESDE_LA_CARGA_V2.md`.

## Veredicto y alcance

INTEGRADO EN CHECKPOINT FUNCIONAL LOCAL — QA ANDROID EMULADA COMPLETADA.

Resultado vigente: 826 JVM, 47/47 casos Android dirigidos y un recorrido visual
adicional aprobado, con 36 capturas revisadas. Tres observaciones visuales P3
quedan explícitas al final; no se declara terminación visual perfecta, Samsung,
TalkBack ni aptitud de distribución. Las secciones previas al cierre definitivo
conservan la evidencia y los bloqueos históricos, ya superados.

MAIN revisó el recorrido completo lugar → horario → regreso → confirmación,
el diff de los diez archivos rastreados y los dos archivos nuevos completos.
No encontró defectos reproducibles que exigieran corregir producción. Dos
revisores internos independientes, separados entre productor y consumidor/host,
también cerraron sin hallazgos accionables. No editaron ni ejecutaron pruebas.
Esto no certifica comportamiento OEM ni equivale a aceptación física.

El candidato añade creación contextual desde carga de uno o varios días,
reutilizando WorkSetup y la carga V2. Conserva fechas y puesto, selecciona por
ID exacto y requiere confirmar las jornadas después de crear el catálogo.

## Base, identidad y preservación al cierre local previo a QA

- Checkout compartido: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD intacto: `f750de4d4f671628e862f6886071ae8387a521a9`.
- Entrada: diez archivos modificados, dos nuevos, cero staged.
- Los doce SHA-256 coinciden con el manifiesto de la entrega
  `.local-evidence/crear-desde-carga/candidate-files.json`.
- Sin delta en Core, Room/esquemas, manifiesto, Gradle, dependencias o versión.
- Nueve worktrees preservados; ninguna tarea, rama o worktree nuevo.
- Referencia remota local: 20 adelante, 0 atrás; sin fetch ni consulta GitHub.
- `main`, `origin/main` y `v1.0.0^{}` continúan en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- MAIN sólo agregó este registro y actualizó STATUS; no alteró el candidato.
- Salida esperada: once rastreados modificados y tres nuevos, cero staged.
- No commit, push, tag, firma, publicación, descarte ni operación de dispositivo.

## Revisión por frontera

| Frontera | Comprobación de MAIN | Límite |
|---|---|---|
| Solicitud y resultado | Tipos correlacionados por UUID/timeline, serialización compatible con SavedStateHandle, resultado guardado antes del reconocimiento | La restauración depende del estado que Android entregue; no sobrevive por contrato a borrar datos o retirar la tarea |
| Productor | UUID reservados antes de escribir, pendiente persistido, recuperación leyendo IDs, ausencia confirmada habilita reintento expreso | No se presenta la simulación JVM como transacción Room ejecutada |
| Cancelación parcial | Lugar confirmado se conserva; horario sin guardar se descarta con explicación; ninguna eliminación compensatoria | Recorrido del diálogo físico pendiente |
| Consumidor | Catálogo fresco, resultado antes/después de emisión, lectura fallida conserva inbox, selección exacta, entrega duplicada/vieja no reemplaza otra elección | Consultas de historia/catálogo no forman una sola transacción; la escritura final conserva su validación CAS existente |
| Fechas y confirmación | Invalida ocupación/avisos/revisión anteriores; no descarta fechas incompatibles silenciosamente; backfill sigue siendo expreso; no escribe jornadas al crear catálogo | La prueba transversal usa repositorios controlados, no SQLite |
| Host y navegación | No reinicia la carga al volver; misma timeline conserva borrador al archivar último horario; destinos externos esperan y revalidan después de suspensión | Activity real y puertas físicas todavía no ejecutadas |
| Superficies | Acciones visibles sin opciones, mensajes de recuperación/cancelación, formularios existentes, vocabulario sectorial, habilitación según estado | Compilar Compose no demuestra legibilidad, teclado o TalkBack |

Se leyeron íntegros `ContextualWorkCreation.kt` y sus 21 pruebas nuevas. La prueba
transversal usa ambos coordinadores productivos y confirma finalmente mediante
`applyV2Batch`: uno/tres días y cuatro sectores, identidad de plantilla/lugar,
fechas, puesto, duración e instantáneas. Los tests adicionales cubren respuesta
perdida antes/después del commit, recuperación fallida, recreación, doble toque,
resultados viejos, catálogo demorado, archivo concurrente, cambio de sector,
revisiones históricas y backfill. Las pruebas no duplican las fórmulas productivas.

No se cambiaron cálculos de horas, seguridad beta/bloqueo, contratos de copias,
autoridades de confianza ni escritores Room. No se agregaron permisos, red,
secretos o logging en el diff auditado.

## Evidencia realmente ejecutada por MAIN

```powershell
python tools/verify.py --profile local --fresh-tests --output .local-evidence/crear-desde-carga-main-20260907/local
python tools/qa.py --check-catalog
python tools/docs_check.py
git diff --check
```

La batería local comenzó 08:01:57 UTC y terminó 08:02:58 UTC. Estado `passed`;
insumos sin cambios durante la ejecución. Registro completo:
`.local-evidence/crear-desde-carga-main-20260907/local/result.json` y
`local/local/gradle.log`.

| Módulo | Ejecutadas | Fallos | Errores | Omitidas | Resultados reutilizados |
|---|---:|---:|---:|---:|---:|
| core:domain | 379 | 0 | 0 | 0 | 0 |
| core:database | 12 | 0 | 0 | 0 | 0 |
| app | 435 | 0 | 0 | 0 | 0 |
| Total local | 826 | 0 | 0 | 0 | 0 |

Incluye las 21 transversales nuevas, 27 WorkSetup y 28 de carga manual. El perfil
local excluye expresamente WebCryptoInterop; no se declara otra ejecución de
interoperabilidad, ajena al cambio. Catálogo QA válido: 102 clases, 16 grupos.

## Build reutilizado con identidad comprobada

Se conserva el build del especialista, ejecutado de 07:44:06 a 07:52:29 UTC.
MAIN comparó sus **534 insumos Android** con los de la nueva batería local:
cero diferencias; `changed_inputs` vacío en ambos. También comparó los cinco
APK y el XML lint actuales con los hashes registrados: seis coincidencias.
No hubo reconstrucción ni nueva instrumentación de MAIN. Repetir un build sin
cambio, fallo o incertidumbre no añadiría cobertura física.

| Artefacto | SHA-256 comprobado | Procedencia |
|---|---|---|
| Debug | `f53b358dfc771ea2c8ed8d3266eec6c7202f26fb648ca0e44c617d39846b03d3` | Build entregado, generado |
| QA | `d65eaf34ae0a0ba3474097767c8e25bff0238a0cba6d24b0d0f77fcd7a668b1b` | Build entregado, generado |
| Release unsigned | `a687e2a37ab726d06ec82823d0f4501cb1843d02e8a2b739b04a89669fe202bd` | Build entregado, generado; no distribuible |
| QA AndroidTest | `f8f641884e7a7496fcf1b2dff5d249a0bdf7eb5be50e639a70ad9c062721b09c` | Build entregado, generado |
| Database AndroidTest | `b6827a38006eb01adbc3b6af33460437bb194b93b415ddae9a5aac9413f9e315` | Build entregado, UP-TO-DATE; Core sin cambios |

Lint conservado: cero errores y seis avisos de versiones. Advertencia Kotlin
entregada: llamada segura innecesaria; no se hizo un cambio cosmético para
provocar otro build. La JVM advierte además sobre `Unsafe` de DataStore.

Fuentes de evidencia del especialista:
`.local-evidence/crear-desde-carga/HANDOFF_A_MAIN.md`,
`VALIDACION_FINAL.md`, `build/result.json` y `candidate-files.json`.
Los artefactos ignorados no se incorporan a Git.

## Puerta física definida antes de QA

No se consultó ni usó ningún dispositivo. Instrumentación de este candidato:
cero. Tampoco se acredita revisión visual nueva, instalación o publicación.

1. **Fixture desechable autorizada:** cuatro casos de
   `V2ManualShiftLoadActivityTest` (recreación, lugar+horario, destino externo
   diferido y cancelación parcial). Su `Before` limpia datos y siembra
   beta/onboarding; nunca usarla sobre la QA Samsung conservada. Aislar procesos
   no aísla datos. Recomendación: emulador QA separado, sin tocar esa instalación.
2. **Compose y recorrido del mismo candidato:** botones con catálogo vacío,
   último horario archivado, cuatro sectores, errores, cancelar/reintentar,
   fechas confirmadas, guardado exacto y retorno. Corroborar el APK por hash.
3. **Presentación y sistema:** claro/oscuro, textos largos, teclado, Atrás,
   orientación, zoom interno efectivo 100/150/200 y accesibilidad. Las pruebas
   nuevas pasan appZoom al tema que configura LocalDensity; están compiladas,
   no ejecutadas ni inspeccionadas visualmente. No consultar ni cambiar escala
   tipográfica, densidad o tamaño visual de Android.
4. Acordar ensayo Samsung preservador si se desea revisión OEM: no implica
   permiso para borrar datos, reiniciar ni deshabilitar puertas productivas.

La autorización general de preparar la entrega no se usa para inferir permiso
de borrar QA. Tras acordar el aislamiento y obtener evidencia proporcional,
MAIN decide el checkpoint local; hasta entonces se conserva el candidato sin
staging. No corresponde abrir otro especialista para reimplementar esta función.

## Continuación Android autorizada

Joaquin respondió «siii» a ejecutar la fixture destructiva en un emulador QA
desechable, preservando el Samsung. MAIN creó únicamente la instancia
`MiGuardia_Contextual_QA_20260907`, serial `emulator-5580`, con imagen API 33
google_apis/x86_64 ya instalada. Almacenamiento separado bajo
`.local-evidence/crear-desde-carga-main-20260907/emulator/avds`. Sin descargas,
consultas al Samsung, producción, firma o servicios beta reales.

La ejecución inicial encontró defectos del harness, no defectos productivos:

- La configuración y primera regla empezaban el día 1, pero el contexto de la
  fixture se resolvía para hoy. Room rechazó correctamente la primera escritura.
  Se alineó `referenceDate` con `revision.effectiveFrom`, conservando el
  invariante productivo.
- Los casos nuevos esperaban la existencia del formulario y escribían mientras
  la comprobación asíncrona de catálogo aún lo deshabilitaba. Se agregó espera
  semántica de habilitación, acotada a 10 segundos; un bloqueo persistente sigue
  fallando, no se omite ni se aumenta el plazo arbitrariamente.
- El cierre del test cambiaba orientación antes de destruir la Activity,
  compitiendo con su recreación. Se retiró ese cambio del teardown; el test
  de rotación mantiene sus comprobaciones. Una petición externa sintética
  remanente se drena por su identidad exacta en `finally`, sin navegar ni
  consumir otra petición.

Cambios MAIN de QA limitados a `V2ManualShiftLoadActivityTest.kt` y su hash en
`tools/qa-groups.json`. Se preservan cuatro métodos. El helper opcional
`captureContextualEvidence=true` obtiene imágenes de tres estados, dos temas y
tres zoom internos, campos y acción final, con datos ficticios. Sólo modifica
las preferencias internas de apariencia y restaura tanto presencia como valor;
no consulta ni modifica escala/densidad/tamaño de Android.

Diagnósticos conservados, sin atribuirlos a una pasada final:

| Evidencia en `.local-evidence/qa/` | Resultado |
|---|---|
| `20260907T082911.293866Z` | Cuatro fallos de preparación por fecha de primera regla |
| `20260907T083029.316676Z` | 20 Compose de carga aprobados; APK de pruebas anterior |
| `20260907T083304.924717Z` | Sin invocaciones: terminó el proceso host del emulador |
| `20260907T083553.245311Z` | Sin invocaciones: paquete de pruebas no disponible tras recuperación |
| `20260907T083606.403245Z` | Una Activity aprobada, tres fallidas; sincronización y cierre del harness |

La primera terminación del emulador registra excepción Windows `0xc0000005`
en qemu, no crash de MiGuardia. Se reanudó esa misma instancia desechable con
renderizado software y Vulkan deshabilitado. Una interrupción posterior del
turno dejó sin resultado recuperable la invocación diagnóstica de un método;
no se cuenta como aprobada. La instancia se volvió a iniciar y conservó sólo
sus datos sintéticos. Registros de arranque bajo el directorio `emulator`.

### Continuación previa de 10:32–10:35 UTC, superada por el cierre siguiente

La corrección final del harness compiló mediante
`gradlew.bat --no-daemon --stacktrace --max-workers=1 --console=plain :app:assembleQaAndroidTest`:
BUILD SUCCESSFUL en 1m27s, 87 tareas (4 ejecutadas/83 vigentes).
Fuente Activity normalizada y catálogo:
`c13746a3a8d3a73c3fba0e59a0b4640b9f67ab4567336b906c77736e95720543`.
APK QA AndroidTest final:
`3b6d5cfb70808e17428786f1904899fb1242c2966c84f08d33968be8595e0dec`.
Producción y pruebas JVM coinciden con las huellas de entrega; las 826 pruebas
locales continúan siendo evidencia válida de esos mismos insumos, no una
repetición después de editar exclusivamente AndroidTest.

Las nuevas ejecuciones no alcanzaron la invocación de pruebas:

| Evidencia en `.local-evidence/qa/` | Resultado |
|---|---|
| `20260907T103211.808042Z` | APK finales identificados; qemu-headless termina durante preparación, 0 pruebas ejecutadas/4 pendientes |
| `20260907T103453.427443Z` | Runner de pruebas ausente tras recuperación; 0 ejecutadas/4 pendientes |
| `20260907T103517.110276Z` | Paquete test reinstalado; qemu con Qt termina durante preparación, 0 ejecutadas/4 pendientes |

Windows registra `0xc0000005` también para `qemu-system-x86_64.exe` a las
10:35:36 UTC. Emulador instalado 37.1.11.0/build 15917651. Cambiar headless por
Qt oculto no resolvió el fallo. Se probó además `-accel off` sin cambiar
virtualización ni seguridad de Windows: terminó con exit 1, sin completar
arranque; su ayuda local advierte que x86_64 puede no funcionar sin aceleración.
No se atribuye una causa raíz más precisa ni un crash Android a estos intentos.

**BLOQUEADO POR ENTORNO:** la última corrección Activity está compilada pero no
ejecutada. Tampoco se ejecutó el helper de 36 capturas ni se declara revisión
visual verde, TalkBack, Samsung o matriz de distribución. Las dos imágenes
diagnósticas del emulador no sustituyen esa matriz. Los demás 43 casos de la
pasada dirigida final permanecen pendientes; 20 Compose tienen evidencia previa
separada, no una nueva pasada sobre el último APK test.

Estado final: no quedan procesos qemu observados; la instancia desechable está
detenida. Su almacenamiento local y los APK/datos ficticios quedan ignorados
para reproducción, sin purga recursiva ni consulta al Samsung. No se abrió otra
dependencia ni se alteraron fuentes productivas, ajustes de Windows, credenciales,
firma, servicios remotos o producción. Sin staging ni commit; el checkpoint
requiere completar la puerta Android pendiente.

## Cierre definitivo de MAIN: Android emulado y revisión visual

Joaquin autorizó continuar («ok, vamos»). Se mantuvo el mismo AVD desechable,
imagen API 33 y serial `emulator-5580`. El arranque de 10:54 UTC con `-gpu host`,
WHPX y Vulkan deshabilitado permaneció estable durante toda la matriz final.
El renderer observado fue OpenGL ES Translator / AMD Radeon, no SwiftShader.
Esto demuestra un workaround útil en este equipo, no una causa raíz probada
de los crashes anteriores. No se actualizaron SDK, drivers o seguridad Windows.

### Defectos del ensayo corregidos y evidencia que los distingue

- Con el host estable, `20260907T105458.760481Z` terminó 3/4: el cuarto método
  completó las comprobaciones funcionales y falló únicamente en `@After`.
  MAIN y el revisor comprobaron el bytecode local de AndroidX Test core 1.7.0:
  ActivityScenario ignora eventos si el Intent actual deja de coincidir con
  el Intent de lanzamiento. MiGuardia sanitiza legítimamente el destino
  consumido. El harness conserva el Intent original y lo repone exclusivamente
  después de las aserciones, antes de `close()`; el cierre no se silencia y se
  comprueba `Lifecycle.State.DESTROYED`. Sin cambios de sanitización productiva.
- La primera matriz visual falló antes de capturar una ventana no validada.
  Se reforzó la guardia: espera acotada por raíz activa QA y foco de la Activity,
  seguida de otra comprobación inmediata. No fuerza ventanas ni relanza la app.
  La hipótesis inicial del teclado no se dio por confirmada.
- Se comprobó además el cierre efectivo de «Usar color» después del toque,
  después de recrear y en cada recreación de la matriz. Esto sincroniza el
  ensayo antes de guardar/restaurar estado e impide operar detrás de un modal.
  Una captura diagnóstica intermedia del selector no se presenta como prueba de
  un defecto de restauración productivo. Tras estas aserciones, el recorrido
  visual completo pasó. No se cerró el diálogo por fuerza ni se omitió un fallo.

Todos los cambios MAIN ejecutables se limitan al archivo AndroidTest y su hash
de catálogo. Un revisor independiente confirmó el cierre, guardias, aserciones
y restauración de preferencias. Producción, pruebas JVM y otros diez archivos
de la entrega conservaron sus huellas originales.

La última compilación `:app:assembleQaAndroidTest` terminó en 36 segundos:
87 tareas, 4 ejecutadas y 83 vigentes. Los intentos de diagnóstico anteriores
no se atribuyen a este APK final:

- QA de aplicación: `d65eaf34ae0a0ba3474097767c8e25bff0238a0cba6d24b0d0f77fcd7a668b1b`.
- QA AndroidTest: `1086926e9c6eb89d4a12481af35466834dd501a16f9b4bad2f6c83fd7c1f134f`.
- Fuente Activity normalizada y catálogo: `5d1c1ad254342b643bb0352253a71a091945b0865a8ef76b2b68dc218d2e4f8e`.

### Pasada dirigida final

Cada clase fue ejecutada por separado mediante `tools/qa.py`, que verificó
hashes local/instalado, runner, clasificación y permisos de ensayo. Los flags
de datos desechables/beta se usaron sólo en los grupos que los requieren.
Todos los manifiestos están bajo `.local-evidence/qa/`:

| Clase | Aprobadas | Fallos/omitidas | Manifiesto final |
|---|---:|---|---|
| V2ManualShiftLoadActivityTest | 4 | 0/0 | `20260907T111813.123164Z/manifest.json` |
| V2ManualShiftLoadComposeTest | 20 | 0/0 | `20260907T111849.635312Z/manifest.json` |
| WorkSetupComposeTest | 16 | 0/0 | `20260907T111926.832921Z/manifest.json` |
| V2ReadyCalendarRecreationActivityTest | 5 | 0/0 | `20260907T111956.855995Z/manifest.json` |
| AutomaticTimeFieldComposeTest | 2 | 0/0 | `20260907T112024.108349Z/manifest.json` |
| Total único | 47 | 0/0 | Sin casos pendientes de esta pasada |

La pasada anterior también alcanzó 47/47 con APK test `20eecf5f...`; queda como
evidencia anterior, no se suma al total único. Los demás intentos preliminares
fallidos siguen siendo diagnósticos. Las 826 JVM son la ejecución local ya
registrada: insumos productivos/JVM idénticos, no una ejecución nueva por cada
edición del harness. No se repitió Room completa, ya que Core no cambió.

### Imágenes reales y límites

El método `contextualPlaceAndTemplateSurviveRecreationThenConfirmExactPairs`
se ejecutó además con `captureContextualEvidence=true`: **1/1, 33,477 s**.
Es repetición de un caso de los 47, no una prueba única adicional. Confirmó dos
jornadas y sus fotografías únicamente después de guardar conscientemente.

Las 36 PNG están en
`.local-evidence/crear-desde-carga-main-20260907/visual-final/`:
tres etapas (lugar, horario, regreso), claro/oscuro, zoom interno 100/150/200,
y dos posiciones de desplazamiento por combinación. Directorio propio del
ensayo: `a3a69cd1-7d8c-4e39-8917-9c65b0d7ce15`. MAIN revisó las doce de regreso;
dos revisores internos independientes inspeccionaron las doce de lugar y las
doce de horario, respectivamente. Ninguno modificó archivos ni usó ADB.

Verificado: escala efectiva, temas, selección CTX/10:15–18:45, dos fechas
conservadas, confirmación «Guardar 2 jornadas», acciones finales alcanzables,
ausencia de teclado/modal en las imágenes finales y datos exclusivamente
ficticios. Los recortes del formulario por desplazamiento no significan pérdida
de sus campos. Rotación de la Activity, entradas de texto, Atrás y recreación
se ejecutaron en las pruebas; las 36 capturas son de retrato y teclado oculto,
no una matriz visual completa de paisaje/teclado ni TalkBack hablado.

### Observaciones visuales no bloqueantes, aún abiertas

| ID | Prioridad y evidencia | Alcance de la observación |
|---|---|---|
| V01 | P3: `place-*-200-work-place-continue.png` | «Opciones avanzadas» se parte en «Opciones / avanzada / s». Texto completo y acción alcanzable, pero lectura mejorable. |
| V02 | P3: `template-*-200-work-template-save.png` | «Cambiar color» se parte dentro de una palabra. No falta contenido ni se bloquea la elección. |
| V03 | P3: `return-*` | La confirmación verde transitoria queda parcialmente cubierta por la barra superior. El componente compartido se alinea arriba sin el padding de esa barra; selección y botón de guardado permanecen visibles. |

MAIN acepta estas tres observaciones para un checkpoint **funcional local**,
no como QA visual perfecta ni como aceptación de distribución. No se amplió
esta integración a rediseñar componentes compartidos. Deben revisarse durante
la terminación visual previa a la entrega. Sin defectos funcionales P0–P2
reproducidos en esta matriz.

### Seguridad y cierre del entorno

Sólo se utilizó `emulator-5580`, verificando nombre AVD y API antes del retiro.
Tras preservar evidencia se desinstalaron exactamente QA y QA.test de esa
instancia desechable, eliminando sus fixtures y archivos propios. Se retiraron
por nombre los tres PNG diagnósticos de `/sdcard/Download`; la carpeta quedó
vacía y `pm path` no devolvió ninguno de los dos paquetes. Finalmente se detuvo
esa instancia y no quedó un proceso con su nombre AVD.

El almacenamiento AVD y las evidencias sintéticas locales quedan ignorados para
reproducción; no hubo borrado recursivo del checkout ni limpieza de otros AVD.
Samsung, sus datos, producción, credenciales reales, permisos de producción,
escala/densidad Android, firma, servidores y publicación no fueron consultados
ni modificados. Sin descargas de imágenes, nueva tarea o dependencia.

Se vuelve a comprobar catálogo, documentación, diff y alcance antes del
checkpoint. Push, firma, provisión beta y distribución siguen fuera de este
cierre; la matriz OEM/TalkBack y los tres P3 no se presentan como completados.

### Checkpoint real

MAIN creó `ea47d71b258a7c82aca2be44a24857d53258f618`,
`feat: create work places and templates from shift loading`:
doce archivos, 1.673 inserciones y 122 eliminaciones. El commit incluye sólo
implementación, pruebas y catálogo del bloque. La evidencia y STATUS se
registran en un commit documental posterior; ese registro no altera fuentes
ni acredita otra ejecución. Resumen verificable, huellas de las 36 imágenes y
preservación: `.local-evidence/crear-desde-carga-main-20260907/qa-final.json`.

## Continuación MAIN — terminación visual V01–V03

Base de esta continuación: `46bb18769ac3ef8fdcf36aa957cd2c1149eb2a9d`,
checkout limpio. Joaquin indicó «ok, continuemos» después de proponer el cierre
de estas tres observaciones antes de retomar preparación beta. No se abre otra
dependencia ni se reimplementa la creación contextual. MAIN conserva el rol
escritor y dos revisores internos contrastan el diff en sólo lectura.

### Corrección y alcance

- V01: `AdvancedOptionsSection` conserva la fila normal; con menos de 240 dp
  disponibles apila el botón y la ayuda. No reduce letras ni zoom. La expansión,
  los textos y acciones permanecen iguales.
- V02: los dos formularios de horario reutilizan una pequeña acción privada de
  color. Con menos de 200 dp, muestra y botón se apilan; con más espacio conservan
  la fila. Se mantienen selección, callbacks y condiciones de habilitación.
- V03: `TransientConfirmation` acepta un modificador. Calendario aplica y
  consume el padding de su Scaffold en ese wrapper, no sólo en su contenido.
  Aviso y Calendario comparten el área bajo la barra; los otros destinos usan el
  modificador por defecto y conservan su geometría.
- Tags internos de barra y aviso permiten comprobar límites reales; no agregan
  texto visible ni acciones. No hay cambios en Core, Room, reglas, escritor de
  jornadas, permisos, SDK, versiones, dependencias, beta o servicios.

### Regresiones y diagnóstico intermedio

`ContextualLayoutComposeTest` agrega cinco pruebas de presentación sin Room ni
activación: palabras completas/ayuda en todos los zoom, ambos formularios y
etiquetas de color, Calendario real con aviso bajo su barra, consumo único de
insets explícitos y ayuda abierta durante reflujo/restauración.

La primera compilación detectó un import `TextAlign` duplicado; se retiró. La
pasada local posterior aprobó 826 JVM y el build inicial 335 tareas (28 ejecutadas,
307 vigentes). Lint: cero errores/seis avisos de versiones. Estos resultados son
intermedios, no los del candidato final posterior a la corrección siguiente.

La primera instrumentación nueva aprobó **4/5**. El caso dirigido de ayuda falló
al cambiar de fila a columna: el estado vivía dentro de la rama reemplazada.
Se reprodujo aisladamente en `ContextualLayoutComposeTest.kt:106` antes de
corregir; el éxito del proceso ADB no se confundió con éxito JUnit.

Corrección: estado guardable de ayuda y diálogo fuera de ambas ramas; botón y
diálogo privados reutilizados por la API existente. La prueba exige conservar
el diálogo abierto en 100→200 %, restauración guardada y regreso a 100 %, y luego
cerrarlo conscientemente. Revisión independiente sin otros defectos accionables.

Evidencia intermedia:

- `.local-evidence/contextual-visual-20260907/local/`: import duplicado, no verde.
- `local-r2/` y `build/` del mismo directorio: primera compilación completa.
- `.local-evidence/qa/20260907T123853.740931Z/manifest.json`: cuatro aprobadas,
  una fallida, cero omitidas; identidad de APK comprobada.
- `.local-evidence/contextual-visual-20260907/help-regression-red.log`:
  reproducción aislada fallida antes del fix de estado.

### Verificación posterior

Perfil local final ejecutado después de la última corrección productiva:
**826/826**, sin fallos ni omisiones; build local 1m01s, 66 tareas (7 ejecutadas).
Incluye 379 dominio, 12 database y 435 app, sin la categoría WebCryptoInterop.
Los insumos no cambiaron durante la ejecución. Evidencia:
`.local-evidence/contextual-visual-20260907/local-final/result.json`.

Build final posterior: **BUILD SUCCESSFUL en 4m42s**, 335 tareas: 25 ejecutadas y
310 vigentes. Cinco APK compilados; lint cero errores/seis avisos existentes de
versiones. Evidencia: `.local-evidence/contextual-visual-20260907/build-final/`.
Las huellas de insumos Android de ese resultado siguen coincidiendo al terminar
instrumentación y revisión visual. No se confunde este build incremental con
351 tareas ejecutadas ni con una nueva pasada de interoperabilidad.

| Artefacto final | SHA-256 |
|---|---|
| QA | `0013e12773732e6c1f7affebb14d542e9f55dadd2f5d04a4fa0bfb2884b7846b` |
| QA AndroidTest | `2408a3fa6f1064d0f78b682535f2537d0175c50d8290d6458d3568c47e56c221` |
| Release unsigned | `6cdd894cb9c9e279f5a0d284bd557211fb0617100c19c6188a29fd9c0dc23e47` |

Release conserva `2.0.0-beta.1`, código 2, 12.766.881 bytes; no es un APK firmado
para distribución. Core, Room, seis esquemas, Gradle, manifiesto, dependencias,
permisos, versión y contratos beta permanecen fuera del diff.

### Instrumentación final y revisión visual

**56/56 aprobadas, cero fallos y cero omisiones**, exclusivamente en el AVD
desechable autorizado API 33. Las nueve ejecuciones comprobaron identidad de los
mismos APK finales anteriores. Manifiestos individuales bajo `.local-evidence/qa/`;
resumen: `.local-evidence/contextual-visual-20260907/final-android.json`.

| Clase | Aprobadas | Directorio del manifiesto |
|---|---:|---|
| ContextualLayoutComposeTest | 5 | `20260907T124758.504853Z` |
| ProgressiveDisclosureComposeTest | 1 | `20260907T124845.180091Z` |
| RgbColorPickerComposeTest | 2 | `20260907T124852.042768Z` |
| TransientConfirmationComposeTest | 1 | `20260907T124901.436674Z` |
| V2ManualShiftLoadComposeTest | 20 | `20260907T124909.293350Z` |
| WorkSetupComposeTest | 16 | `20260907T124948.986923Z` |
| V2ManualShiftLoadActivityTest | 4 | `20260907T125023.529378Z` |
| V2ReadyCalendarRecreationActivityTest | 5 | `20260907T125106.760529Z` |
| AutomaticTimeFieldComposeTest | 2 | `20260907T125135.969565Z` |

Se repitió el recorrido Activity contextual con `captureContextualEvidence=true`:
**1/1 en 34,954 s**, no sumado como otro caso único a los 56. Tras crear catálogo,
el caso conserva fechas/confirmación/borrador y escribe las dos jornadas sólo
después de la confirmación final. Registro `visual-final.log` y **36 PNG nuevos**
1080×2400 en `visual-final/`, ambos bajo el directorio de evidencia de esta
continuación. Identidad de sesión sintética:
`0bf84a3f-2864-4e75-8fba-e0ddb116c507`.

Dos revisores independientes inspeccionaron las doce imágenes de lugar y las
doce de horario; MAIN inspeccionó las doce del retorno. Las tres superficies
cubren claro/oscuro, zoom interno 100/150/200 y dos posiciones de desplazamiento.

- **V01 cerrado:** palabras completas en Opciones avanzadas; ayuda accesible en
  su fila inferior al 200 %. Guardar lugar visible al desplazar.
- **V02 cerrado:** Cambiar color sin cortar palabras; muestra y botón apilados
  al 200 %, distribución anterior al 100/150 %. Guardar horario alcanzable.
- **V03 cerrado:** confirmación íntegra bajo la barra; selección exacta del
  horario ficticio y las dos fechas conservadas, Guardar 2 jornadas alcanzable.
  Se mantiene la superposición transitoria de 2.500 ms sobre el contenido; no se
  afirma que todo el formulario sea visible a la vez ni se rediseña el aviso.
- La regresión adicional de ayuda abierta pasó después del fix. No se atribuye
  como prueba verde la primera ejecución 4/5 ni las imágenes de la etapa previa.

Las 36 imágenes son retrato con teclado oculto. Recreación y cambios de
configuración están cubiertos por Activity, pero esto no acredita una matriz
visual completa de paisaje/teclado, TalkBack hablado, Samsung/OEM, otro launcher
ni distribución. No se ejecutó la suite Room completa: persistencia no cambió.

### Retiro y checkpoint de la continuación

Tras comprobar nombre del AVD y API, se retiraron exclusivamente
`com.blackatsystems.miguardia.qa.test` y `com.blackatsystems.miguardia.qa` de
`emulator-5580`. Ambas operaciones devolvieron Success y el inventario acotado
quedó vacío. Se eliminaron sus datos ficticios y archivos privados de captura;
las 36 copias locales revisadas permanecen en evidencia ignorada. La instancia
se cerró mediante su comando de salida y se comprobó después ausencia de
procesos con ese AVD. Evidencia: `cleanup.log`.

No hubo uso del Samsung, inventario de producción, ajuste visual Android,
descarga de imágenes, cambio de drivers, reinicio físico, firma, publicación,
push ni limpieza recursiva del proyecto. El almacenamiento local del AVD queda
preservado e ignorado.

MAIN creó el checkpoint `3aa124e30adb7e35f0d1d8c7cfbcb714578b7134`,
`fix: preserve contextual layout at large app zoom`: siete archivos,
351 inserciones y 63 eliminaciones. Incluye cinco archivos productivos, una
clase de pruebas y el catálogo QA (103 clases/16 grupos comprobados). La
documentación se confirma aparte; el commit no representa otra compilación.
El bloque queda integrado localmente, con V01–V03 cerrados en la matriz indicada.
Las puertas de términos, confianza pública, firma y actualización instalada
continúan en STATUS; no se abre automáticamente otra dependencia.
