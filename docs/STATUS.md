# Estado de MiGuardia 2.0

## Objetivo activo

PLANIFICACIÓN quedó cerrada por autorización expresa de Joaquin. Las decisiones
funcionales reunidas constituyen la base vigente para avanzar; cerrar esta etapa
no significa que exista ya una implementación nueva.

El núcleo laboral V2 quedó aprobado por MAIN. La segunda capa está
desbloqueada. **Widget de próximo evento** e **Informes locales de jornadas y
horas** quedaron cerrados. Informes pasó la batería local definitiva y la matriz
Samsung API 36 de 33/33 casos. Copias y restauración locales seguras permanece
sin prompt ni tarea habilitados. MAIN se detiene después del checkpoint local
de Informes.

MAIN 2.0 está reactivada para recibir los handoffs que Joaquin entregue,
auditarlos, integrarlos, probarlos y cerrarlos. Joaquin decide cuándo pedir el
prompt de una nueva tarea y cuándo abrirla; MAIN no encadena dependencias por su
cuenta. Después de una integración verde, MAIN crea automáticamente el commit
local que funciona como checkpoint. El push puntual autorizado el 2026-08-22 ya
fijó en el remoto privado la base `836d908` de `Cargar jornadas` y quedó
consumido. Joaquin autorizó el 2026-08-23 un único push adicional para publicar
el checkpoint estable V2-only y esta recomendación futura. MAIN lo ejecutó y
verificó hasta `0364b83`; esa autorización quedó consumida. Joaquin autorizó el
2026-08-27 un único push adicional para publicar el cierre verde de guardias
pasivas y disponibilidad. MAIN lo ejecutó y verificó en `80fe8e5`; esa
autorización quedó consumida. Joaquin autorizó después publicar el cierre verde
de Calendario final y tarjeta superior; MAIN lo ejecutó y verificó en
`fd6891e`. Joaquin autorizó después publicar Resumen personalizable; MAIN lo
ejecutó y verificó en `ad777bb`. Ambas autorizaciones quedaron consumidas y no
se extienden a pushes posteriores, tags, un Release, `main`, la publicación de
la aplicación ni ninguna acción sobre el paquete o los datos de producción.

Decisión de producto del 2026-08-23: MiGuardia 1.0 fue una prueba interna sin
usuarios y continúa únicamente como base de código. MiGuardia 2.0 no migra datos
de 1.0, no necesita un modo `MIGRATED_V1` ni una activación V1→V2 y comienza con
una configuración limpia. Esta decisión no autoriza borrar la prueba instalada
en el teléfono: cualquier limpieza física continúa siendo una acción expresa.

Para una explicación sin jerga de tareas, ramas, commits y push, consultar
`docs/GUIA_DE_TRABAJO_CODEX_2_0.md`.

La definición humana del producto está en
`docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`. El estado de uso de cada prompt está en
`docs/prompts/README.md` y la investigación separada por profesión en
`docs/sectores/`.

La auditoría de reactivación y Puerta 0 está registrada en
`docs/audits/2026-08-21-reactivacion-main-y-puerta-cero.md`.
El flujo vigente de handoffs y checkpoints está registrado en
`docs/audits/2026-08-23-flujo-handoffs-y-checkpoints-main.md`.
La separación entre continuidad de código y ausencia de migración de datos V1
está registrada en `docs/adr/0024-continuidad-de-codigo-sin-migracion-de-datos-v1.md`
y auditada en
`docs/audits/2026-08-23-continuidad-codigo-sin-migracion-datos-v1.md`.

## Base verificada

- Worktree: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- Base: tag anotado `v1.0.0`
- Commit base: `82db6fd8eb2c511205968894dc9857a96b16ed20`
- `main`, `origin/main` y `v1.0.0^{}` coincidían en ese commit al crear el worktree.
- Identificador técnico actual: `com.blackatsystems.miguardia`
- Base de código heredada: Room v5, trece entidades y migraciones explícitas
  `1→2→3→4→5`.

## Terminado

- MiGuardia 1.0.0 fue sellada y publicada como fuente estable.
- Se creó un worktree independiente y limpio para 2.0.
- La decisión anterior de conservar datos 1.0 quedó reemplazada: se conserva el
  código útil, no una instalación ni sus datos de prueba.
- Se rechazaron múltiples perfiles laborales: existe una sola configuración
  laboral por usuario.
- Se cerró la etapa de decisiones funcionales sobre base, horas adicionales,
  disponibilidad, recurrencias, excepciones, Calendario y Resumen. Su traducción
  a contratos técnicos y código corresponde ahora a MAIN por bloques.
- Se decidió cuantificar la guardia pasiva en un apartado propio. Cuando existe
  trabajo activo, cada minuto activo reemplaza al minuto pasivo superpuesto: el
  intervalo se computa una sola vez como activo y se descuenta de la pasiva.
- Se implementó y auditó la Puerta U del Calendario adaptable. La tarjeta de
  próximo evento se compacta sin perder información, los controles normales del
  mes aprovechan una fila al 100 %, la grilla conserva su tamaño y, cuando hay
  desborde, aparece una barra vertical persistente con pulgar proporcional.
- El contenido completo permanece alcanzable al 100 %, 150 % y 200 %, incluida
  la columna derecha y la acción inferior.
- Evidencia del 2026-08-20: 175 pruebas JVM y 172 instrumentadas en el Samsung
  `SM-S938B`, sin fallos, errores ni omitidas; lint con cero errores,
  empaquetados requeridos aprobados y `git diff --check` correcto. Ver
  `docs/audits/2026-08-20-calendario-adaptable-2-0.md`.
- La Puerta U quedó consolidada en el checkpoint
  `a3e89fdb56aedeed77c89824cec137f37f4c9619` sin tocar `main` ni `v1.0.0`.
- Se definió un catálogo de cuatro sectores: Vigilancia privada,
  Enfermería, Medicina y Policía. Por decisión de Joaquin del 2026-08-21,
  Enfermería y Medicina son sectores independientes; no existe un sector
  genérico Salud ni una opción Otro.
- Las reglas confirmadas distinguen trabajo habitual, clases de horas extras y
  disponibilidad; el trabajo activo reemplaza sólo el tramo pasivo superpuesto.
- El árbol actual evolucionó técnicamente desde Room v5 hasta v7. La
  configuración laboral y el catálogo ya están persistidos, pero la cadena
  heredada y el modo V1 dejaron de ser requisitos del producto final.
- `docs/PROMPT_MAESTRO_MAIN_2_0.md` vuelve a ser el traspaso rector activo. MAIN
  debe concretar cada bloque sin presentar propuestas antiguas como código
  vigente.
- La planificación y el prompt de MAIN fueron reauditados contra los contratos
  reales de Room v5, DataStore, horas V1 y la regla pasiva→activa.
- Por decisión explícita de Joaquin del 2026-08-21, MiGuardia queda limitado a
  organizar jornadas y horas: se retiraron del árbol actual las fuentes,
  montos, fórmulas, código, preferencias, pruebas y documentación del estimador
  monetario. No se incorporan tablas salariales ni liquidaciones en ningún
  sector.
- Se implementó el primer bloque puro de MiGuardia 2.0 bajo
  `core/domain/.../work/`: catálogo de cuatro sectores, vocabulario sugerido,
  cambios efectivos desde una fecha, referencias mensual/semanal/por ciclo,
  valores informados período por período, trabajo habitual, clases extra,
  disponibilidad y reglas versionables por lugar.
- El dominio no fija 204 horas, horario nocturno ni reglas propias de una
  profesión. Las referencias faltantes no se convierten en cero y las
  colecciones validadas no pueden modificarse desde afuera.
- Verificación del bloque puro el 2026-08-21: 208 pruebas JVM aprobadas —36
  nuevas del paquete `work`—, lint aprobado y APK `debug` y de instrumentación
  `qa` compiladas. No se repitió QA física porque no cambió ninguna superficie
  Android; sigue vigente la línea base física de 169/169 de Puerta 0.
- El pendiente histórico sobre cambios dentro de una semana o ciclo quedó
  resuelto: la persona elige cuándo reiniciar el conteo, no existe prorrateo y
  los tramos conservan por separado su vigencia y meta. El motor y el Resumen
  ya consumen ese contrato.

## Estado Git consolidado actual

- `main`, `origin/main` y `v1.0.0^{}` permanecen en MiGuardia 1.0.0, commit
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- `codex/miguardia-2.0` conserva estos checkpoints de desarrollo previos al
  cierre documentado en este mismo cambio:
  - `a3e89fdb56aedeed77c89824cec137f37f4c9619`: Calendario adaptable;
  - `6dab82b8f239f8009cfcb32d400b50fcc4080836`: planificación y traspaso a MAIN.
  - `3519606aeda3a26bed7ec8fc0feb8b7f3f788d35`: retiro de las funciones
    remunerativas.
  - `8b7fa31fb3865e6ef162a6474d57a0061a32c588`: cierre de PLANIFICACIÓN,
    reactivación de MAIN y contrato del primer bloque.
  - `89937270df90d7d1739725a6be73539a2d0bade9`: dominio laboral configurable;
  - `7dde17d`: configuración persistente y Room v6;
  - `4757737`: contrato de lugares, tipos, plantillas y primera carga V2.
  - `49a8179b619b6005899773bacfb0a2ac16920fcd`: Corte A de catálogo laboral y
    persistencia Room v7.
  - `cb64948`: auditoría y estado del Corte A.
  - `e6ec746`: contrato de primera apertura y configuración visible.
  - `1f048643ba70882576295e4683729a35a9584312`: primera apertura y
    configuración laboral visible integradas.
  - `ca029d1`: coordinador documental secuencial.
  - `ae57686`: carga manual V2 auditada, probada e integrada.
  - `fe911e2`: flujo de handoffs y checkpoints locales de MAIN.
  - `a63ca25`: continuidad del código V1 sin migración de sus datos.
  - `59e3181`: contrato de edición y eliminación individual de jornadas V2.
  - `4646f665eec84052a544a5179c72b93971df2700`: edición y eliminación
    individual auditada, probada e integrada por MAIN.
  - `a306221efa850e026a4009d3c8a8640c0ed263ea`: contrato de runtime
    exclusivamente V2.
  - `b04dd59cbb1da656a46f88710c2a846002a143b0`: retiro del modo V1 y base Room
    exclusiva V2 integrados.
  - `0364b835d07883708e137a7057f235fad9113b38`: registro de la recomendación
    futura de Agenda profesional y último push autorizado.
  - `12fa7f64eef7493f8324467c876b55c9883d8625`: contrato de planes recurrentes.
  - `2d41f60840be9e12abde97182d79757ddbb0a992`: planes recurrentes auditados,
    probados e integrados.
  - `d1f3e68c1ee5debdc34ef7e30f7376980175ee04`: contrato de horario real y
    clasificación exacta de extras.
  - `2e6138509e4ef6c5faf09657cb6bf094cb7ae610`: horario real y clasificación
    exacta de extras auditados, probados e integrados por MAIN.
  - `6fb04c8ff34eec2c454277dfb086664349a9051b`: contrato de extras
    independientes, reinicio consciente y avance de horas.
  - `964b7cd0ce399ff20ba371fa6585e6e2850fd9b7`: extras independientes y avance
    de horas auditados, probados e integrados por MAIN.
  - `11bbdb41f0a948f5c45dce6adb8b5c95a5b3c931`: contrato de guardias pasivas y
    disponibilidad.
  - `80fe8e5f8fdc47d5236941e91a46ffc3b1faab61`: guardias pasivas y
    disponibilidad auditadas, probadas, integradas y publicadas con autorización
    puntual.
  - `27601ddf50f16f6381eb998f0c01daecc9ced706`: contrato de Calendario final y
    tarjeta superior desplegable.
  - `fd6891e446eaa574f3df14348d8d5b1cfd201f2d`: Calendario final y tarjeta
    superior auditados, probados, integrados y publicados con autorización
    puntual.
  - `9fad12e39b56a850ce528a2fd5398f3b15258864`: contrato de Resumen
    personalizable.
  - `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`: Resumen personalizable
    auditado, probado, integrado y publicado con autorización puntual.
  - `af206fad8b6b2ac916bb891a20460d58b1aa01cb`: contrato de Próximo evento y
    notificaciones V2.
  - `55dcd60aba2512597d3074f9978f228086ddf7ea`: Próximo evento y
    notificaciones V2 auditados, probados e integrados por MAIN.
  - `7570d25421d532a4dd25a03dae3b3cb586a7d8f1`: contrato de auditoría integral
    del núcleo y compatibilidad Android.
  - `d16ca11ee920d0d9be0f220eda60c3bd02d859d4`: resultado parcial y tres huecos
    de cobertura registrados.
  - `1b697cd3c4db613dd1c3187a9ed0efb8cf4496bf`: contrato de pruebas cruzadas.
  - `c35fffb2abe99eac73e164f99147bf95d11ad83d`: tres barreras cruzadas
    auditadas, probadas e integradas por MAIN.
  - `3385c15586ba9af706452f5df540dc3f305da99f`: repetición integral reactivada
    después de cerrar API 26 y API 33.
  - `7dadd6299a864df939b5de6d6d6f67d9df737c53`: auditoría integral repetida y
    núcleo declarado apto para la segunda capa.
  - `0c2b7dc5737cf66497fda2714a5bdf82c45d8c63`: contrato del Widget de próximo
    evento.
  - `d22c5a19ab4722b36116230678511e2cfcd886fa`: Widget auditado, probado e
    integrado por MAIN.
  - `f2d05b96b8ff11c1c14dfadb2788c6d514d04176`: contrato de Informes locales de
    jornadas y horas.
- Al iniciar la preparación documental, la rama todavía no poseía upstream. El
  push puntual posterior fue ejecutado y verificado: rama local y remoto privado
  coincidían en `836d908`; esa autorización no puede reutilizarse.
- El dominio nuevo vive en `core/domain/.../work/`; no se recuperó el candidato
  mensual descartado. El runtime V1 ya fue retirado. `MiGuardiaV2Database`
  conserva su cadena explícita `1→2→3→4→5` y ya persiste configuración, carga,
  edición/eliminación, recurrencias, horario real, clases extra, extras
  independientes, el inicio consciente de la referencia de horas y ventanas de
  disponibilidad.

## Antecedente histórico descartado: candidato mensual

Nombre que tuvo el bloque: **Reglas internas para configurar el trabajo y
aplicar cambios por mes**.

Ese candidato fue evaluado como una propuesta técnica sin pantalla. El código
ya no está en el árbol actual y no debe recuperarse desde worktrees o ramas
históricas. La vigencia limitada al inicio de un mes quedó reemplazada por el
diseño confirmado de cambios efectivos desde una fecha local concreta, sin
reescribir el pasado.

La evidencia siguiente se conserva sólo como historia de lo que llegó a
evaluarse. No demuestra la existencia ni la validez actual de ese código.

- El candidato histórico agregó dominio puro bajo
  `core/domain/.../workconfig`.
- Incluyó el catálogo cerrado de Vigilancia privada, Enfermería, Medicina y
  Policía.
- Modeló base mensual, política nocturna, pasiva, motor legado/V2 y revisiones
  por mes.
- Llegó a incluir veintidós pruebas nuevas de dominio.
- No modificaba Room v5, DataStore, Compose, Gradle, manifiesto, permisos,
  `applicationId`, versión ni comportamiento visible.

Validación repetida por MAIN el 2026-08-21:

- `testDebugUnitTest lintDebug assembleDebug`: `BUILD SUCCESSFUL`;
- JVM: 197 pruebas, 0 fallos, 0 errores y 0 omitidas;
- reglas internas de configuración laboral: 22 pruebas;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias preexistentes;
- `git diff --check`: correcto;
- sin QA física, porque este bloque no toca Android ni una interfaz.

Validación posterior a retirar el estimador monetario el 2026-08-21:

- `testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest`:
  `BUILD SUCCESSFUL`;
- JVM: 172 pruebas, 0 fallos, 0 errores y 0 omitidas;
- lint: 0 errores, 2 advertencias y 3 sugerencias;
- pruebas instrumentadas compiladas;
- QA física final: 169 pruebas, 0 fallos, 0 errores y 0 omitidas en el Samsung
  `SM-S938B`; la alarma real se verificó con acceso exacto temporal concedido
  sólo al paquete QA;
- búsqueda exhaustiva del árbol actual sin nombres, símbolos, fuentes ni
  archivos del estimador retirado;
- `git diff --check`: correcto.

La auditoría no encontró defectos concretos y quedó registrada en
`docs/audits/2026-08-21-reglas-configuracion-laboral-por-mes-y-estado-git.md`.

## Configuración persistente y Room v6

> Estado actual: la evidencia siguiente describe correctamente lo que se
> implementó, pero la raíz `MIGRATED_V1` y su activación futura quedaron
> obsoletas por la decisión de reemplazo limpio del 2026-08-23.

El segundo bloque de MAIN 2.0 quedó implementado y verificado el 2026-08-21:

- `HoursReference.PendingSetup` distingue “todavía no se configuró” de una
  decisión consciente de no usar referencia o de una referencia desconocida;
- una instalación nueva comienza sin configuración y crea una única línea
  temporal al elegir sector;
- una instalación actualizada desde 1.0 recibe una raíz `MIGRATED_V1` vacía y
  sigue usando el comportamiento heredado hasta una activación V2 fechada;
- Room v6 agrega cuatro tablas y no modifica las trece familias de la versión
  5;
- revisiones y definiciones son históricas e insert-only; los valores por
  período sólo admiten una corrección explícita de minutos;
- los esquemas `1.json` a `5.json` conservaron exactamente sus hashes y se
  agregó únicamente `6.json`.

Validación final:

- JVM: 217 pruebas, 0 fallos, 0 errores y 0 omitidas;
- base de datos en Samsung `SM-S938B`: 65 pruebas, 0 fallos, 0 errores y 0
  omitidas;
- aplicación QA completa en el mismo Samsung: 169 pruebas, 0 fallos, 0 errores
  y 0 omitidas;
- `lintDebug`, `assembleDebug` y `assembleQaAndroidTest`: aprobados;
- paquetes QA retirados al finalizar; la aplicación productiva no fue tocada;
- evidencia completa en
  `docs/audits/2026-08-21-configuracion-persistente-y-room-v6.md`.

## Lugares, tipos y plantillas — Corte A y Room v7

El Corte A del bloque activo quedó implementado y verificado el 2026-08-22. La
aplicación todavía no activa ninguna conducta V2 visible; este checkpoint fija
los contratos y la persistencia que necesita el recorrido utilizable del Corte
B.

- el dominio separa lugar laboral, tipo de trabajo, plantilla horaria y reglas
  versionadas por lugar;
- Enfermería y Medicina siguen siendo sectores independientes dentro del
  catálogo cerrado de cuatro sectores;
- la vigencia se resuelve por fecha local exacta, también cuando una jornada
  atraviesa dos fechas civiles;
- una jornada V2 se escribe junto con su fotografía histórica de sector,
  configuración, lugar, tipo y plantilla;
- las rutas heredadas de actualización y Novedades no pueden cambiar objetivo,
  horario o puesto de una jornada V2 sin su fotografía; los cambios de estado
  siguen disponibles sin alterar la estructura;
- adopción V1, archivo independiente, retrocarga consciente de una instalación
  `NEW_V2`, normalización NFKC y recientes V2 poseen contratos explícitos;
- la selección para una carga V2 rechaza mezclar vigencias V1/V2 o sectores;
  una sustitución ya confirmada puede borrar jornadas V1 o V2 en esas fechas,
  pero nunca deja una jornada V2 sin su fotografía;
- Room v7 agrega exactamente cinco tablas a las diecisiete de v6 y
  `MIGRATION_6_7` las crea vacías, sin adoptar ni modificar historia;
- los esquemas `1.json` a `6.json` conservaron exactamente sus hashes y se
  agregó únicamente `7.json`, con 22 entidades.

Validación final del corte:

- aplicación JVM: 41/41;
- dominio JVM: 217/217;
- base de datos JVM: 5/5;
- total JVM: 263/263, sin fallos, errores ni omitidas;
- base de datos instrumentada: 98/98 en el Samsung `SM-S938B` API 36;
- `lintDebug`, APK `debug`, APK de instrumentación del módulo y APK de pruebas
  QA: aprobados;
- `git diff --check`: correcto;
- los paquetes de prueba fueron retirados al finalizar y permaneció instalado
  únicamente `com.blackatsystems.miguardia`, sin abrirlo ni modificarlo;
- evidencia completa en
  `docs/audits/2026-08-22-lugares-tipos-plantillas-room-v7-corte-a.md`.

## Primera apertura y configuración laboral visible — integrada por MAIN

La dependencia visible quedó auditada e integrada sobre el Corte A el
2026-08-22. Este incremento completa la entrada y la configuración laboral
visible, pero no completa el Corte B ni habilita todavía la carga manual V2 de
jornadas.

- una instalación nueva resuelve primero el estado persistido y, sólo si no
  existe raíz, muestra el selector obligatorio de Vigilancia privada, Policía,
  Enfermería o Medicina antes del Calendario;
- la confirmación crea `NEW_V2` con `HoursReference.PendingSetup`, sin
  disponibilidad, horario nocturno ni valores sectoriales inventados;
- después se abre el Calendario vacío con una guía para crear el primer lugar,
  sus reglas, el tipo habitual editable y el primer horario exacto;
- el primer conjunto usa la transacción pública `createFirstWorkSet`, conserva
  el borrador ante error y ofrece las tres continuaciones acordadas;
- se pueden agregar horarios reutilizando lugar y tipo, y también lugares
  adicionales con sus reglas; cada operación usa el contrato atómico público
  correspondiente;
- el código todavía conserva una raíz V1 y un recorrido heredado; esa rama ya no
  pertenece al producto final y queda pendiente de retiro en el bloque V2-only;
- los borradores no confirmados se conservan mediante `SavedStateHandle` y los
  estados de carga o error nunca se confunden con una instalación nueva;
- catálogo y objetivos se cargan como una sola operación observable: si
  cualquiera falla, la pantalla queda en error recuperable y no avanza con
  información parcial;
- cada carga usa una única fecha local de referencia, conserva la selección al
  cerrar y reabrir `Agregar otro horario`; al entrar en V2, oculta las
  superficies y borradores V1 residuales sin dejar que bloqueen el Calendario,
  termina cualquier edición V1 y no muestra acciones V1 inertes dentro del
  detalle de un día.

Validación final repetida por MAIN:

- JVM: 287/287 —65 de aplicación, 217 de dominio y 5 de base de datos—; 24
  pertenecen al coordinador de configuración laboral;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias existentes;
- APK Debug y APK de AndroidTest QA: compilados correctamente;
- instrumentación afectada en Samsung `SM-S938B` API 36: 18/18 —11 pruebas de
  configuración y 7 regresiones vecinas del panel—;
- regresión de las dos suites históricas que lanzan la actividad: 9/9 mediante
  un fixture QA determinista de raíz `MIGRATED_V1`;
- primera corrida global observada por MAIN: 179/180; las otras tres pruebas de
  `NotificationAlarmEndToEndInstrumentedTest` aprobaron y sólo el recorrido
  físico de alarma exacta se detuvo en su precondición porque el paquete QA no
  tenía el acceso especial requerido;
- repetición excluyendo la clase de cuatro pruebas que contiene ese recorrido
  físico: 176/176;
- Room instrumentado: 98/98;
- el esquema Room v7 conservó el SHA-256
  `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03` y
  dominio, base de datos, Gradle, manifiesto, permisos, versión y SDK quedaron
  sin cambios;
- se preserva como evidencia heredada el recorrido manual QA aprobado por la
  dependencia desde instalación nueva hasta un lugar, un tipo y un horario
  persistidos; MAIN no repitió ese recorrido visual manual;
- el paquete QA se desinstaló al cerrar y la aplicación productiva permaneció
  instalada y sin abrir;
- `git diff --check`: correcto.

Evidencia completa en
`docs/audits/2026-08-22-primera-apertura-configuracion-laboral-visible.md`.

## Carga manual de jornadas V2 — integrada por MAIN

La persona con una configuración `V2Ready` puede usar `Cargar jornadas` desde
la única grilla mensual, elegir una o varias fechas del mismo mes, seleccionar
un lugar, tipo y horario guardados, revisar el resultado y volver al Calendario
con las jornadas visibles.

- cada fecha resuelve su configuración y reglas vigentes;
- una retrocarga `NEW_V2` exige confirmación y no activa una raíz V1;
- fechas ocupadas permiten reemplazar, conservar o agregar una segunda jornada;
- superposición, descanso menor a 12 horas y carpeta médica conservan sus
  confirmaciones;
- cada `Shift` se guarda junto con su `ShiftWorkSnapshot` mediante una sola
  transacción pública;
- la limpieza de `F/?` alcanza únicamente las fechas realmente insertadas;
- selección, formulario y etapa sobreviven a recreación; un error conserva el
  borrador y un éxito se consume una sola vez;
- si otra escritura cambia la ocupación después de revisar, Room rechaza el
  lote antes de mutar y obliga a revisar de nuevo;
- el código todavía conserva un recorrido V1 que ahora debe retirarse; V2 no
  habilita edición, eliminación, recurrencias, horario real, extras ni
  Novedades V1.

Validación final de MAIN:

- JVM: 317/317 —219 de dominio, 5 de base de datos y 93 de aplicación—;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias heredadas;
- APK Debug y APK de AndroidTest QA: compilados correctamente;
- Samsung `SM-S938B`, API 36: 60/60 pruebas afectadas de aplicación, 14/14 de
  persistencia Room V2 y 1/1 recorrido integral separado con `MainActivity`,
  raíz `NEW_V2`, recreación, rotación, guardado y reapertura;
- revisión física con datos ficticios: una y varias fechas, retrocarga,
  ocupadas, segunda jornada, superposición, persistencia, claro/oscuro y zoom
  interno 100 %, 150 % y 200 %;
- Room continúa en versión 7 y `7.json` conserva el SHA-256
  `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`;
- no cambiaron entidades, esquemas, migraciones, Gradle, manifiesto, permisos,
  `applicationId`, versión ni SDK;
- QA y QA.test fueron desinstaladas; producción permaneció únicamente en el
  usuario 10, cerrada, no iniciada y sin modificaciones;
- API 26 física queda pendiente por falta de un dispositivo disponible.

La tarjeta heredada de próximo evento conserva temporalmente el motor y
vocabulario V1: su adaptación V2 pertenece a un bloque posterior y no fue
ampliada en esta integración.

Evidencia completa en
`docs/audits/2026-08-23-carga-manual-de-jornadas-v2.md` y decisión técnica en
`docs/adr/0023-precondicion-transaccional-para-lotes-v2.md`.

## Edición y eliminación individual de jornadas V2 — integrada por MAIN

Una configuración `V2Ready` puede abrir el detalle de un día, elegir
conscientemente `Editar este día` y operar sobre una jornada exacta, identificada
como `Jornada N de M`. La fecha permanece visible e inmutable y las demás
jornadas del día se conservan.

- la edición permite elegir una plantilla activa compatible o cambiar solamente
  el puesto o función conservando la fotografía histórica;
- la eliminación exige una confirmación que vuelve a mostrar lugar, tipo,
  horario, color y puesto de la jornada elegida;
- `Shift` y `ShiftWorkSnapshot` se comparan y escriben o eliminan como un par
  atómico;
- la precondición transaccional compara el par histórico completo y la ocupación
  vecina que se usó para revisar superposiciones y descansos;
- un conflicto concurrente no sobreescribe ni borra: conserva el borrador y
  obliga a revisar el estado actual;
- borrador, revisión, advertencias y confirmación sobreviven a recreación con
  `SavedStateHandle`; doble toque, error y reintento quedan protegidos;
- el Calendario observado se actualiza al volver, sin una segunda fuente de
  jornadas;
- la reconciliación posterior retira la visibilidad y el seguimiento de una
  jornada eliminada sin afectar sus compañeras;
- carga manual, `V2NeedsFirstSet` y las rutas heredadas quedan separadas del
  coordinador nuevo.

La auditoría de MAIN corrigió antes de aceptar el bloque: expectativas mutables,
una carrera entre cambios de raíz y escrituras, restauraciones sin las filas del
día, entrada indebida desde `V2NeedsFirstSet`, navegación Atrás con teclado
abierto, cobertura explícita de avisos y dos huecos de pruebas Room. Tres
revisiones independientes de sólo lectura confirmaron después que no quedaban
bloqueantes.

Validación final de MAIN:

- JVM: 347/347 —226 de dominio, 5 de base de datos y 116 de aplicación—;
- lint: 0 errores, 2 advertencias de versiones y 3 sugerencias heredadas;
- APK Debug, APK AndroidTest QA y APK AndroidTest de Room: compilados;
- Samsung `SM-S938B`, API 36: 113/113 pruebas instrumentadas únicas —87 de
  Compose y superficies vecinas, 2 de recreación, 1 recorrido integral de
  edición, 1 de carga manual y 22 de persistencia Room—; el recorrido integral
  de edición se repitió una vez para preparar la revisión visual, también verde;
- revisión visual directa con datos ficticios: Calendario con una sola jornada,
  `Jornada 1 de 1`, fecha fija, fotografía histórica, plantillas activas,
  acciones separadas y confirmación exacta de borrado;
- claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % quedaron
  cubiertos en el dispositivo por la instrumentación; la inspección visual
  directa final fue en oscuro y retrato;
- Room continúa en versión 7 y `7.json` conserva el SHA-256
  `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`;
- no cambiaron entidades, DAO, esquema, migraciones, Gradle, manifiesto,
  permisos, `applicationId`, versión ni SDK;
- `com.blackatsystems.miguardia.qa`, `.qa.test` y
  `com.blackatsystems.miguardia.core.database.test` quedaron ausentes al
  finalizar; ningún comando apuntó a producción;
- API 26 física permanece pendiente por falta de un dispositivo disponible.

Evidencia completa en
`docs/audits/2026-08-23-edicion-eliminacion-jornadas-v2.md` y decisión técnica
en `docs/adr/0025-cas-par-historico-edicion-eliminacion-v2.md`.

## Base exclusiva V2 y retiro del modo V1 — cerrado

MAIN auditó, corrigió y verificó el candidato **Dejar MiGuardia únicamente en
modo 2.0**. El runtime ya no contiene una bifurcación V1/V2 ni abre la base
histórica.

ADR 0026 resolvió la decisión arquitectónica que faltaba:

- la primera base pública de V2 será `MiGuardiaV2Database`, archivo
  `miguardia-v2.db`, Room versión 1 y esquema propio;
- no existe migración desde `MiGuardiaDatabase` v1–v7;
- `miguardia.db` no se abre, copia, transforma ni borra;
- la nueva base conserva diecinueve tablas que sostienen V2 y capacidades
  comunes, y excluye `schedule_combinations`, `shift_novelties` y
  `formal_shift_changes`;
- desaparecen el origen `MIGRATED_V1`, la activación, la adopción, la
  procedencia de horarios V1 y los escritores de una jornada sin fotografía;
- una jornada almacenada sin `ShiftWorkSnapshot` pasa a ser un dato local
  inválido;
- el runtime conserva Calendario, configuración, carga, edición/eliminación,
  próximo evento, notificaciones, clima y las demás capacidades comunes, pero
  retira Perfil, Resumen, Objetivos/horarios, guardias/francos y Novedades V1.

El resultado verificado agrega además:

- validación global de objetivos, configuración, catálogo y jornadas, incluidas
  sus fotografías históricas;
- una única frontera transaccional `V2ShiftRepository` para crear, editar o
  eliminar jornadas;
- Feriados, Notas, Vacaciones, carpetas médicas, fotos, próximo evento,
  notificaciones, clima y apariencia preservados como funciones comunes V2;
- primera apertura con los cuatro rubros exactos y navegación sin Perfil,
  Resumen, gestión estructural ni Novedades V1.

Room quedó fijado como `MiGuardiaV2Database`, archivo `miguardia-v2.db`, versión
1 y 19 tablas. Su esquema tiene identity hash
`d583ce68e247cba7574a9e3b25b29e69` y SHA-256
`5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`.

Validación de cierre:

- JVM: 283/283 —168 de dominio, 5 de base y 110 de aplicación—;
- lint: 0 errores y 2 avisos de actualización ya conocidos;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: runner verde `OK (148 tests)`, con 147
  aprobadas y una omisión consciente por no habilitar acceso especial a
  alarmas exactas; Room 61/61;
- emulador Android 8.0, API 26: aplicación 148/148 y Room 61/61;
- revisión visual directa en ambos entornos: primera apertura limpia, cuatro
  rubros exactos y `Continuar` deshabilitado sin selección;
- paquetes QA retirados, emulador apagado, Samsung con rotación original y
  producción intacta.

El contrato quedó `CERRADO` en
`docs/prompts/RETIRAR_MODO_V1_Y_FIJAR_BASE_EXCLUSIVA_V2.md`. Evidencia completa
en `docs/audits/2026-08-23-retiro-modo-v1-y-base-room-v2.md`.

## Repetir jornadas y cambiar una fecha o todo lo futuro — cerrado

MAIN auditó, corrigió e integró localmente el candidato recibido sobre
`12fa7f64eef7493f8324467c876b55c9883d8625`.

Quedó funcionando:

- creación de planes finitos por días de semana, cada N días, cada N semanas o
  patrón mensual por ordinal y día;
- vista previa exacta y materialización inmediata de cada
  `Shift + ShiftWorkSnapshot`;
- planes versionados con ocurrencias `AUTOMATIC`, `CUSTOMIZED`, `EXCLUDED` y
  `RETIRED`;
- cambio o eliminación de una sola jornada, cambio de todo lo futuro y
  finalización desde una fecha;
- preservación del pasado, jornadas manuales, personalizaciones, exclusiones,
  notas, avisos y carpetas médicas;
- acceso desde Calendario y `Mi forma de trabajar`, con borrador recuperable,
  errores reintentables y conflictos concurrentes explícitos.

Por decisión expresa de Joaquin del 2026-08-25, una creación o modificación
admite hasta 2.000 jornadas concretas. Si produciría 2.001 o más, el lote se
rechaza completo y nunca se recorta silenciosamente.

Room V2 pasó de la versión 1 a la 2 mediante migración explícita. Conserva las
19 tablas anteriores y agrega `recurring_plans`, `recurring_plan_revisions` y
`recurring_occurrences`. El esquema `1.json` permanece intacto y `2.json`
queda fijado con SHA-256
`E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`.

La auditoría independiente y MAIN corrigieron validaciones de patrón, rango y
fotografía histórica; ocupación propia y ajena; protección de jornadas
manuales; consultas SQLite de más de 999 parámetros; reintentos, estados de
carga, conflictos CAS y semántica de selección múltiple.

Validación de cierre:

- JVM: 328/328 —195 de dominio, 5 de base y 128 de aplicación—;
- lint: 0 errores y 4 avisos de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: aplicación 89/89 y Room 74/74; las tres pruebas
  ajustadas durante QA volvieron a pasar 3/3;
- emulador Android 8.0, API 26: aplicación 89/89 y Room 74/74;
- revisión visual directa en Samsung: plan recurrente visible en Calendario y
  próximo evento, accesos V2 y formulario de cuatro patrones; claro/oscuro,
  retrato/paisaje y zoom interno 100/150/200 quedaron cubiertos físicamente;
- paquetes QA retirados, emulador apagado y producción intacta, detenida y no
  abierta.

El contrato quedó `CERRADO` en
`docs/prompts/REPETIR_JORNADAS_Y_CAMBIAR_DESDE_UNA_FECHA_V2.md`. La evidencia
completa está en
`docs/audits/2026-08-25-planes-recurrentes-y-cambios-futuros-v2.md`.

## Horario real y clasificación exacta de extras — cerrado

MAIN auditó, corrigió e integró localmente el candidato recibido sobre
`d1f3e68c1ee5debdc34ef7e30f7376980175ee04`.

Quedó funcionando:

- horario planificado separado, que no se sobrescribe al registrar el horario
  real opcional;
- alta, corrección y regreso consciente al planificado desde una jornada
  exacta;
- fecha, hora y offset explícitos, incluidos cruces de día, mes y año;
- clasificación consciente de una duración real mayor como habitual o extra;
- uno o más fragmentos extra exactos, sin solapamiento ni doble conteo;
- clases reutilizables por sector, con alta, renombrado, archivo, reactivación
  y fotografía histórica;
- borradores recuperables, doble toque bloqueado, errores reintentables y CAS
  completo frente a cambios concurrentes;
- protección transaccional de edición, eliminación, carga manual y
  recurrencias cuando una jornada ya posee horario real.

Room V2 pasó de la versión 2 a la 3 mediante migración explícita. Conserva las
22 tablas anteriores y agrega `extra_work_classes`, `shift_actual_records` y
`shift_extra_intervals`. Los esquemas `1.json` y `2.json` permanecen intactos;
`3.json` queda fijado con SHA-256
`39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`.

La auditoría de MAIN y tres revisiones independientes corrigieron validaciones
de transiciones y fotografías, evidencia CAS inmutable, clases archivadas,
rollback, protección de recurrencias, restauración del borrador, conflictos de
integración y orden estable del Calendario. La QA física detectó además una
aserción sensible al alto de API 26 y una superposición real con la barra del
sistema; ambas quedaron corregidas y revalidadas.

Validación de cierre:

- JVM: 364/364 —215 de dominio, 5 de base y 144 de aplicación—;
- lint: 0 errores y 4 avisos de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados desde cero;
- Samsung `SM-S938B`, API 36: aplicación afectada 82/82 y Room 84/84;
- emulador Android 8.0, API 26: aplicación afectada 82/82 y Room 84/84;
- recorrido físico registrar → recrear → persistir → reabrir → volver al
  planificado, más claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- revisión visual directa en Samsung del Calendario, detalle y editor de
  horario real, con barra del sistema respetada;
- paquetes QA retirados, emulador apagado y producción intacta y no abierta.

El contrato quedó `CERRADO` en
`docs/prompts/REGISTRAR_HORARIO_REAL_Y_CLASIFICAR_HORAS_EXTRA_V2.md`. La
evidencia completa está en
`docs/audits/2026-08-25-horario-real-y-horas-extra-v2.md`.

Este bloque no calcula todavía avance contra la referencia ni crea trabajo
extra independiente sin jornada dueña. Esas decisiones siguen separadas tal
como fija ADR 0028.

## Extras independientes y avance de horas — cerrado

Joaquin decidió el 2026-08-25 que MiGuardia debe preguntarle a la persona desde
cuándo quiere reiniciar el conteo de horas. Puede elegir, por ejemplo, empezar
hoy o desde el próximo lunes. La referencia anterior continúa hasta el día
previo; la nueva comienza en cero en la fecha elegida.

No existe prorrateo automático. Si se elige una fecha dentro de un mes, semana
o ciclo ya comenzado, MiGuardia explica que el primer tramo será más corto y
usa la meta completa. También permite elegir el próximo límite normal para
evitar ese tramo corto. La meta nueva nunca se aplica hacia atrás.

El prompt
`docs/prompts/EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md` quedó
`CERRADO`. El bloque integra:

- configuración visible de referencia y fecha de reinicio;
- extras independientes con horario exacto, lugar, tipo, clase y fotografía
  histórica;
- motor puro de habitual, extras, total, cumplimiento, faltante y superación;
- vista funcional de avance sin construir todavía el Resumen final;
- migración explícita de Room V2 `3→4`, preservando los tres esquemas previos.

MAIN auditó el dominio, Room, CAS e interfaz con tres revisiones independientes.
Corrigió la vigencia exacta de la configuración, la conservación automática de
fotografías históricas, la atomicidad de metas por período, el control de
concurrencia desde que se abre un borrador, los límites civiles de intervalos y
las advertencias de solapamiento o protección para que sólo aparezcan cuando
corresponden.

Evidencia final:

- JVM: 409/409 — dominio 247, base 10 y aplicación 152;
- lint: 0 errores y 4 avisos de versiones disponibles fuera del alcance;
- APK Debug, QA y ambos APK AndroidTest: compilados desde cero;
- Samsung `SM-S938B`, API 36: Room y migraciones 57/57;
- Samsung `SM-S938B`, API 36: aplicación afectada 89/89;
- claro/oscuro, retrato/paisaje, zoom interno 100/150/200, recreación,
  error/reintento y regresiones de carga, recurrencia, horario real y edición
  cubiertos por instrumentación física;
- paquetes QA retirados al finalizar; producción no fue abierta ni
  modificada;
- API 26 no fue autorizada para este bloque y queda como evidencia separada.

La decisión funcional continúa registrada en ADR 0029. La auditoría completa
está en
`docs/audits/2026-08-25-extras-independientes-y-avance-de-horas-v2.md`.
Este cierre no autorizó push ni otra acción externa.

## Guardias pasivas y disponibilidad — cerrado

El prompt `docs/prompts/GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md` quedó
`CERRADO`. MiGuardia permite elegir No uso disponibilidad o uno de los tres
nombres exactos Guardia pasiva, Disponible para llamado y Retén, con vigencia
desde una fecha concreta.

Desde la única grilla mensual se pueden registrar, consultar, corregir y
eliminar ventanas exactas. Las ventanas contiguas son válidas y las
superpuestas se rechazan. El trabajo activo reemplaza solamente la unión del
tramo coincidente; la disponibilidad nunca se convierte en trabajo ni se suma
al cumplimiento, faltante o superación.

MAIN auditó dominio, Room, concurrencia e interfaz con tres revisiones
independientes. Corrigió especialmente:

- protecciones de vacaciones y carpeta médica en todos los días alcanzados por
  una ventana multidiaria;
- conflictos por configuración, edición o eliminación concurrentes;
- conservación de la fotografía original del registro al editar y recrear;
- validación de contexto y solapamiento en la frontera de dominio;
- doble toque, navegación durante guardado, descarte consciente y reintento;
- conservación de datos visibles ante errores temporales y actualización al
  cruzar medianoche.

Room V2 evoluciona mediante migración explícita `4→5`, agrega únicamente
`availability_windows` y queda con 27 tablas. Los esquemas 1 a 4 permanecen
byte a byte intactos.

Evidencia final de MAIN:

- JVM: 433/433 — dominio 265, base 12 y aplicación 156;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: Room, migraciones y persistencia 107/107;
- Samsung `SM-S938B`, API 36: aplicación y regresiones 190/190;
- claro/oscuro, retrato/paisaje, zoom interno 100/150/200, recreación,
  error/reintento y regresiones cubiertos por instrumentación física;
- revisión visual humana en oscuro/retrato del Calendario, acceso laboral y
  configuración de disponibilidad;
- los tres paquetes QA fueron retirados y producción no fue tocada.

La decisión arquitectónica está en ADR 0030 y la auditoría completa en
`docs/audits/2026-08-27-guardias-pasivas-y-disponibilidad-v2.md`. API 26 no se
repitió para Room V2 versión 5 y queda como evidencia de compatibilidad separada,
no como bloqueo de este checkpoint.

## Calendario final y tarjeta superior — cerrado

El prompt `docs/prompts/CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md` quedó
`CERRADO`. MiGuardia conserva una sola grilla mensual y consolida en ella las
jornadas, extras, disponibilidad y marcadores existentes. Las jornadas
completadas mantienen una marca histórica de color, pero su estado también se
expresa con texto y accesibilidad; varias jornadas o disponibilidades informan
su cantidad sin ocultar los nombres del detalle.

La tarjeta superior ahora resume el día civil actual. Cerrada prioriza una
jornada en curso, la próxima de hoy, las completadas o el estado honesto sin
trabajo. Cuando corresponde adjunta el próximo evento futuro. Al desplegarla
enumera todas las jornadas de hoy, incluidas completadas, canceladas, ausentes y
protegidas, además de una nocturna todavía activa iniciada ayer. La expansión se
asocia a la fecha y no se restaura sobre el día siguiente.

MAIN auditó dominio, observación reactiva, estado, Compose y restauración con
tres revisiones independientes. Corrigió especialmente:

- copias defensivas para que la proyección y sus listas anidadas sean realmente
  inmutables;
- observación de todos los sectores presentes en la historia laboral;
- carreras entre cambios de fuente y límites temporales;
- descarte del resultado del día anterior al cruzar medianoche;
- conservación del último resultado válido ante un error recuperable del mismo
  día, con reintento;
- expectativas instrumentadas que todavía buscaban el encabezado histórico
  `Próximo evento` o una única celda completada;
- determinismo de la prueba Activity frente a preferencias QA persistidas y
  desplazamiento explícito hasta el mes siguiente.

Room V2 permanece en versión 5 con 27 tablas. No cambiaron entidades, DAO,
repositorios de base, migraciones ni esquemas 1 a 5. Tampoco cambiaron Gradle,
manifiesto, permisos, identificadores, versión ni SDK.

Evidencia final de MAIN:

- JVM: 456/456 — dominio 285, base 12 y aplicación 159—;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: Room y persistencia 107/107;
- Samsung `SM-S938B`, API 36: aplicación completa 214/214;
- las cuatro expectativas instrumentadas corregidas pasaron 4/4 de forma
  aislada y la prueba Activity endurecida pasó 1/1 con el APK final;
- claro/oscuro, retrato/paisaje, zoom interno 100/150/200, expansión,
  recreación, medianoche y regresiones quedaron cubiertos por instrumentación;
- MAIN inspeccionó visualmente la tarjeta cerrada y desplegada, la grilla, el
  tema oscuro en retrato, el tema claro en 100/200 % y el paisaje;
- la orientación volvió a modo libre, los tres paquetes QA fueron retirados y
  producción no fue instalada, abierta, consultada ni modificada.

La auditoría completa está en
`docs/audits/2026-08-27-calendario-final-y-tarjeta-superior-v2.md`. API 26 no se
repitió para este bloque sin cambios de Room y queda como evidencia de
compatibilidad separada antes del candidato final. Este cierre no autoriza push
ni ninguna acción sobre producción.

## Resumen personalizable — cerrado

El prompt `docs/prompts/RESUMEN_PERSONALIZABLE_V2.md` quedó `CERRADO`. Resumen
es ahora un destino principal mensual y de sólo lectura. Reúne trabajo total,
habitual, extras cuando existen, pendiente programado, cumplimiento por
períodos completos y disponibilidad separada. Las ocho familias opcionales se
pueden ordenar u ocultar sin modificar fórmulas ni historia laboral.

Cada cifra abre el mismo libro exacto de contribuciones que produjo su valor.
MAIN corrigió especialmente:

- la representación exacta de `Planificado frente a real`, con una
  contribución positiva del horario real y otra negativa del planificado;
- la observación de fuentes necesarias para semanas o ciclos completos que
  cruzan el límite del mes;
- el apagado del observador cuando Resumen deja de estar visible o una
  superficie funcional lo cubre;
- la cola FIFO de preferencias para conservar dos cambios rápidos y permitir
  reintento sin perder el orden;
- los límites temporales para actualizar sólo cuando una cifra puede cambiar;
- los estados de error para no repetir avisos ni confundir fallos de fuentes
  con fallos de preferencias;
- tres pruebas físicas que dependían de texto duplicado, del tamaño del
  Samsung o de dos pulsaciones Atrás sin esperar la recomposición intermedia.

Room V2 permanece en versión 5 con 27 tablas y los esquemas 1 a 5 intactos. La
presentación usa un DataStore propio; no guarda totales, porcentajes, faltantes
ni desgloses derivados.

Evidencia final de MAIN:

- JVM: 488/488 —dominio 301, base 12 y aplicación 175—;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: Room y persistencia 107/107;
- Samsung: 224/224 pruebas de aplicación ejecutadas correctamente y una prueba
  histórica de alarmas omitida por su propio contrato;
- las once pruebas específicas de Resumen pasaron 11/11 después de endurecer
  sus esperas y selectores;
- claro/oscuro, retrato/paisaje, zoom interno 100/150/200, detalle,
  personalización, recreación y regresiones quedaron cubiertos físicamente;
- MAIN inspeccionó directamente Resumen en oscuro al 100 %, claro al 200 %,
  retrato, paisaje, desplazamiento y detalle de una cifra;
- la orientación volvió a modo libre, no quedó ningún paquete
  `com.blackatsystems.miguardia*` y producción no fue instalada, abierta,
  consultada ni modificada.

Las revisiones independientes de dominio, estado/reactividad e interfaz no
dejaron hallazgos bloqueantes después de las correcciones. La auditoría durable
está en `docs/audits/2026-08-27-resumen-personalizable-v2.md`. Este cierre no
autoriza push ni ninguna acción sobre producción.

El checkpoint del Resumen fue publicado después por autorización expresa de
Joaquin y quedó verificado en `ad777bb`; esa autorización está consumida.

## Próximo evento y notificaciones — cerrado

El prompt `docs/prompts/PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md` quedó
`CERRADO`. Tarjeta superior, observador y avisos consumen una única proyección
V2 tipada para jornadas y tramos efectivos de disponibilidad. Recurrencias
entran por sus jornadas materializadas; vacaciones, carpeta médica, ausencia,
cancelación y horario real invalidan eventos obsoletos; los extras
independientes no se convierten en eventos futuros.

MAIN corrigió durante la auditoría prioridades temporales, reconciliaciones
con fuentes concurrentes, límites de alarmas, reintentos, privacidad del grupo,
restauración después de reemplazar el paquete y aislamiento de pruebas frente
al runtime real de avisos. No se agregó otro motor, una migración Room, permisos
o dependencias.

Evidencia final de MAIN:

- JVM: 498/498 —dominio 302, base 12 y aplicación 184—;
- lint: 0 errores y 6 avisos globales de versiones disponibles;
- APK Debug, QA y ambos APK AndroidTest: compilados;
- Samsung `SM-S938B`, API 36: Room 107/107; suite completa previa del
  candidato 233/233; matriz afectada final 84/84;
- Samsung: permiso denegado, concedido y bloqueo de la aplicación comprobados;
- Samsung: recorrido manual con jornada completada, Retén activo recortado por
  trabajo coincidente y la misma franja 16:00–00:00 en tarjeta, Calendario y
  aviso;
- Android 8, API 26: matriz final 20/20 y reconstrucción real después de
  `install -r` verificada;
- claro/oscuro y zoom interno 100/200 inspeccionados; las pruebas cubren además
  retrato/paisaje y zoom 150 %;
- los paquetes QA quedaron desinstalados y producción no fue instalada,
  abierta, consultada ni modificada.

Room V2 continúa en versión 5 con 27 tablas y esquemas 1 a 5 intactos. La
alarma exacta realmente disparada y un reinicio físico del Samsung no se
ejecutaron porque conservan autorización inmediata separada. API 37 no estaba
disponible en el entorno y no se descargó una imagen sin autorización.

La preparación está registrada en
`docs/audits/2026-08-27-preparacion-proximo-evento-y-notificaciones-v2.md`, la
arquitectura en
`docs/adr/0032-proyeccion-unica-de-eventos-y-avisos-locales-v2.md` y el cierre
en `docs/audits/2026-08-27-proximo-evento-y-notificaciones-v2.md`.

## Auditoría integral del núcleo y compatibilidad Android — resultado parcial histórico resuelto

Joaquin indicó el 2026-08-28 que esta puerta también se gestione como una
dependencia especializada. MAIN preparó
`docs/prompts/AUDITORIA_INTEGRAL_DEL_NUCLEO_Y_COMPATIBILIDAD_ANDROID_V2.md`
contra la base funcional cerrada `55dcd60`.

La dependencia no agrega funciones ni corrige el código que revisa. Su misión
es contrastar juntos configuración, jornadas, recurrencias, horario real,
extras, disponibilidad, Calendario, Horas, Resumen, próximo evento,
notificaciones, Room y compatibilidad Android. Puede devolver el núcleo apto,
MAIN bloqueada por un defecto reproducible o una auditoría parcial si falta una
autorización o evidencia obligatoria.

La dependencia auditó el HEAD documental `7570d25`, cuyo código funcional es
idéntico a `55dcd60`, y devolvió `AUDITORÍA PARCIAL — NO CERRABLE`. Su batería
local forzada quedó verde: 498/498 pruebas JVM, lint sin errores y APK Debug,
QA, Release y ambos AndroidTest compilados. MAIN comprobó los XML, artefactos,
Room, Git y contratos estructurales, y revalidó el conjunto Gradle sin cambios
con `BUILD SUCCESSFUL`.

En esa ejecución no se reprodujeron defectos P0/P1. MAIN confirmó tres huecos
reales:

- faltaba una fotografía transversal única que reconciliara Calendario, Horas,
  Resumen, tarjeta y avisos desde las mismas fuentes, UUID, reloj y zona;
- faltaba una carrera CAS instrumentada entre dos escritores reales que
  partieran
  de la misma fotografía;
- faltaba demostrar explícitamente que consultar Calendario, Resumen y tarjeta
  no modifica filas, timestamps ni versiones de datos.

La instrumentación de aquella auditoría no fue ejecutada. La evidencia física
del 2026-08-27 se trató como heredada, no repetida. Los XML conectados presentes
eran anteriores: Room conservaba 107/107, mientras el único XML de aplicación
conservaba 84 casos con un fallo histórico previo al APK final; por eso no se
usó como evidencia del HEAD auditado. Android 13/API 33 todavía no tenía imagen
instalada. API 37 también carecía de imagen utilizable.

El prompt auditor quedó **PAUSADO / NO REEJECUTAR** en ese punto hasta cerrar
los tres huecos. No autorizaba ADB, imágenes, instalaciones, limpiezas, una
alarma exacta real, reinicio físico ni push. El resultado durable está en
`docs/audits/2026-08-28-auditoria-integral-del-nucleo-y-compatibilidad-android-v2-parcial.md`.

La segunda capa —Widget, informes, copias y restauración locales, bloqueo y
Ayuda y recorrido inicial 2.0— quedó
cerrada en ese momento. Las pruebas correctivas, la matriz y la repetición
posterior resolvieron esos pendientes; el cierre vigente se registra más abajo
y en
`docs/audits/2026-08-29-auditoria-integral-del-nucleo-y-compatibilidad-android-v2.md`.

## Pruebas cruzadas del núcleo V2 — cerrado

Joaquin indicó el 2026-08-28 continuar con la dependencia correctiva recomendada.
MAIN preparó `docs/prompts/PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md` sobre la base
documental limpia `d16ca11`.

La dependencia agrega exclusivamente tres barreras de prueba:

1. una fotografía JVM única que reconcilia Calendario, Horas, Resumen, tarjeta,
   próximo evento y avisos desde los mismos UUID, reloj, zona y fuentes;
2. una carrera Room real entre dos escritores que parten de la misma fotografía
   y debe terminar con un éxito, un conflicto y ningún dato parcial;
3. una prueba instrumentada que demuestra que consultar Calendario, Resumen y
   tarjeta no cambia ninguna de las 27 tablas de aplicación, sus timestamps ni
   su huella lógica.

El alcance queda limitado a `core/domain/src/test/**`,
`core/database/src/androidTest/**` y `app/src/androidTest/**`, con exactamente
tres métodos `@Test` nuevos. Si alguno reproduce un defecto productivo, la
dependencia debe detenerse y devolver `MAIN BLOQUEADA`; no puede corregir
`src/main` ni ampliar su misión.

La dependencia entregó exactamente los tres métodos `@Test` exigidos en un
archivo modificado y dos nuevos, con 1.102 líneas agregadas y cero cambios en
producción, Room, DataStore, Gradle, manifiesto, permisos o esquemas.

MAIN auditó todos los archivos y realizó tres revisiones independientes sin
hallazgos. La batería local forzada ejecutó 351/351 tareas en 17 min 34 s:

- dominio: 303/303;
- database JVM: 12/12;
- app JVM: 184/184;
- total JVM: 499/499, sin fallos, errores ni omitidas;
- lint: 0 errores y 6 avisos de versiones disponibles;
- APK Debug, QA, Release y ambos AndroidTest compilados;
- `git diff --check`: limpio.

Con autorización expresa de Joaquin, MAIN usó únicamente el Samsung
`SM-S938B`, API 36, serial `R5CY529W6PL`, con paquetes QA y datos ficticios:

- carrera CAS nueva aislada: 1/1;
- consulta sin escrituras nueva aislada: 1/1;
- suite Room completa: 108/108;
- regresiones afectadas de Calendario, Resumen y tarjeta: 61/61.

No se disparó una alarma exacta real, no se reinició el Samsung y no se
consultaron ni modificaron ajustes visuales del sistema. Los tres paquetes QA y
de prueba fueron desinstalados; ningún paquete
`com.blackatsystems.miguardia*` quedó instalado en los usuarios 0 o 10.

Room V2 permanece en versión 5 con 27 tablas, `identityHash`
`77adbc875d0f4ee466cdbd0dd74d5c5c` y esquemas 1–5 intactos. El prompt queda
`CERRADO` y la evidencia durable está en
`docs/audits/2026-08-29-pruebas-cruzadas-del-nucleo-v2.md`.

El prompt auditor integral quedó habilitado para completar la matriz Android
obligatoria y emitir un nuevo veredicto. Este cierre no autoriza una alarma
exacta real, reinicio físico, descarga de imágenes, push, tag, Release, `main`
ni producción.

## Auditoría integral del núcleo y compatibilidad Android — cerrada

MAIN repitió la puerta sobre el HEAD documental `3385c15`; `app`, `core`,
Gradle y manifiestos son idénticos al candidato funcional `c35fffb`.

Evidencia final:

- batería local: 499/499 pruebas JVM, lint sin errores y cinco APK requeridos
  compilados;
- Samsung API 36: Room 108/108 y aplicación 235/235, sin ejecutar el único
  caso de alarma exacta real;
- Android 8/API 26: Room 108/108 y recorrido esencial 27/27;
- Android 13/API 33: matriz 24/24, fallback inexacto y privacidad genérica
  visible en pantalla bloqueada;
- recorrido humano continuo Samsung: configuración, recurrencia, horario real,
  extra independiente, Guardia pasiva, Calendario, Resumen y Notificaciones
  sobre una sola historia;
- reconciliación visible: 15 h 30 min = 7 h 30 min habituales + 8 h extra; 4 h
  de disponibilidad separadas;
- Room V5, 27 tablas y esquemas 1–5 intactos;
- findings P0/P1/P2/P3 abiertos: ninguno.

Los paquetes QA/test fueron retirados de Samsung, API 26 y API 33. Producción
no fue instalada ni abierta. La orientación Samsung quedó restaurada y el PIN
ficticio temporal de API 33 fue eliminado. API 37, una alarma exacta real y un
reinicio físico del Samsung permanecen diferidos y no bloquean la segunda
capa.

Veredicto:

```text
NÚCLEO APTO PARA SEGUNDA CAPA
FINDINGS: ninguno
```

El prompt auditor queda `CERRADO`. La evidencia durable está en
`docs/audits/2026-08-29-auditoria-integral-del-nucleo-y-compatibilidad-android-v2.md`.

## Widget de próximo evento — cerrado por MAIN

MAIN recibió el handoff implementado directamente en el checkout compartido,
auditó cada frontera con tres revisiones independientes y corrigió findings de
inmutabilidad, frontera temporal, identidad de `PendingIntent`, cambio de tema,
recreación del guardado y cobertura Activity/UI.

El candidato conserva estas decisiones cerradas:

- tres modos por instancia: Próxima jornada, Próximo franco explícito y
  Automático;
- varias instancias con privacidad y Clima independientes;
- tamaños compacto y ampliado;
- adaptación exclusiva de `projectNextEvent`, sin segundo motor;
- `AppWidgetProvider + RemoteViews`, sin Glance ni dependencia nueva;
- DataStore exclusivo por `appWidgetId`, sin persistir datos laborales;
- cronómetro nativo y una sola frontera inexacta reconstruible, sin polling;
- acceso visible `Widget de inicio` dentro de `Avisos y contexto`;
- Room V5, veintisiete tablas, permisos, Gradle, SDK, versión y package
  intactos;
- Samsung API 36: app QA 248/248 y Room 108/108; matriz dirigida 27/27;
- preview 3×2, configuración, privacidad Oculta, resize, retrato/paisaje y
  navegación verificados en One UI Launcher;
- Android 8/API 26 y Android 13/API 33: pendientes de autorización;
- alarma exacta real, reinicio y push continúan como puertas separadas.

La evidencia MAIN está en
`docs/audits/2026-08-29-widget-de-proximo-evento-v2-main.md`. El bloque queda
cerrado con API 26/API 33 registradas como compatibilidad pendiente, no como
evidencia simulada.

## Informes locales de jornadas y horas — cerrado por MAIN

MAIN recibió el candidato directamente en el checkout compartido, auditó sus
37 rutas finales de código y pruebas, corrigió defectos de coherencia, privacidad,
almacenamiento y presentación, sin cambiar la versión, las
entidades, las migraciones ni los esquemas de Room, y sin agregar dependencias.

Resultado funcional:

- acceso `Generar informe` desde el mes visible de Resumen;
- una fotografía mensual de sólo lectura que reutiliza la fórmula de Horas y
  Resumen, sin persistir totales derivados;
- PDF nativo multipágina y XLSX OOXML tabular;
- guardado consciente mediante SAF y compartir mediante `FileProvider`
  limitado a artefactos privados;
- destinos de guardado limitados a documentos `content://`; una respuesta
  tardía intenta retirar únicamente un documento de tamaño conocido igual a
  cero y nunca acepta rutas directas;
- nombre, puesto, notas, nota médica y fotos apagados en cada sesión nueva;
- segunda confirmación para notas médicas privadas y fotos limitadas al PDF;
- estados parcial, cerrado, sin actividad, desconocido e incompleto sin ceros
  inventados;
- consultas Room transaccionales y sin escrituras; disponibilidad siempre
  separada del trabajo.

Correcciones de MAIN incluyeron truncado real del destino SAF, fecha y sector
históricos del horario real, exclusión de notas privadas vecinas, trabajo activo
que cruza de mes, restauración segura, paginado PDF, congelado temporal de fotos
y un encabezado propio de Informes que conserva palabras completas al zoom
interno 200 %.

Validación final:

- batería local: 351/351 tareas ejecutadas, `BUILD SUCCESSFUL`;
- JVM: 548/548, sin fallos, errores ni omitidas —dominio 329, base 12 y app
  207—;
- lint: 0 errores/fatales; 6 avisos de versiones en Gradle no modificado;
- Debug, QA, Release sin firma y ambos AndroidTest compilados;
- inventario AndroidTest: app 283 y base 113;
- evidencia Samsung anterior, conservada sólo como histórica: matriz dirigida
  28/28 —app 23 y Room 5—, más 4/4 del encabezado al 200 %;
- repetición Samsung definitiva sobre el código final: 33/33 —app 28 y Room
  5—, sin fallos, errores ni omitidas;
- recorrido real con datos ficticios: instalación limpia, Medicina, lugar y
  jornada, Resumen, privacidad apagada, PDF, XLSX, cancelación/reintento de
  guardado, ShareSheet, retrato/paisaje, claro/oscuro y zoom interno
  100/150/200;
- el PDF guardado por SAF se volvió a abrir y renderizar en dos páginas A4 sin
  cortes ni superposiciones;
- Room continúa en V5, 27 tablas, `identityHash`
  `77adbc875d0f4ee466cdbd0dd74d5c5c` y esquemas 1–5 intactos;
- producción permaneció ausente; los paquetes de prueba fueron retirados y sólo
  quedó QA instalada y abierta por el pedido expreso de iniciar una sesión en
  vivo; la matriz final no modificó ajustes visuales ni otros valores del
  sistema.

La evidencia durable está en
`docs/audits/2026-08-29-informes-locales-de-jornadas-y-horas-v2-main.md`.

## Flujo vigente de MAIN

- Joaquin entrega un handoff o pide preparar el prompt de una nueva tarea;
- MAIN verifica Git y la base antes de tocar el resultado;
- MAIN audita el diff, integra sólo su alcance y ejecuta pruebas proporcionales;
- MAIN corrige defectos de integración acotados y actualiza las fuentes de
  verdad;
- cuando todo está verde, MAIN crea automáticamente el checkpoint local e
  informa su commit;
- MAIN recomienda el siguiente bloque y espera la indicación de Joaquin;
- MAIN escribe un prompt nuevo o abre una tarea sólo por pedido expreso de
  Joaquin;
- toda dependencia nueva explica primero, en lenguaje común, `QUÉ HACE` y
  `POR QUÉ EXISTE`; el handoff repite ambos campos para que MAIN compruebe que
  la entrega responde a su propósito original.

El coordinador `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md` conserva un
solo handoff o una sola tarea implementadora por vez. La decisión del 2026-08-23
reemplaza únicamente la creación automática de prompts y dependencias; mantiene
la integración, pruebas, documentación y checkpoints locales a cargo de MAIN.

Esta autorización de flujo no permite recuperar el candidato mensual descartado
ni amplía el alcance a tags, Release, `main`, publicación de la aplicación o
producción. Las autorizaciones puntuales que publicaron disponibilidad y
Calendario final, y la que publicó Resumen, ya fueron consumidas; no existe una
autorización vigente para publicar el próximo checkpoint documental ni bloques
posteriores.

## Backlog posterior

- cualquier cálculo monetario o liquidación permanece fuera del producto;
- un eventual cambio de profesión después de la selección inicial de rubro;
  no forma parte de la secuencia actual y sólo se abre si aparece un caso real;
- recomendación futura, todavía no habilitada: después de cerrar el núcleo
  laboral, las copias y restauración locales seguras y el bloqueo de acceso,
  evaluar una
  `Agenda profesional` opcional para Medicina y una posible Psicología. Su
  primer alcance sería pacientes y turnos, sin historias clínicas,
  diagnósticos, tratamientos ni evoluciones. Psicología requeriría aprobar por
  separado la ampliación del catálogo actual de cuatro sectores;
- monetización y distribución;
- segunda capa ordenada: Widget —cerrado por MAIN; Samsung verde, API 26/API 33
  pendientes de compatibilidad—, Informes —cerrado por MAIN; local y Samsung
  API 36 verdes—, copias y restauración locales, bloqueo y Ayuda y recorrido
  inicial 2.0;
- logo y tipografías definitivas.

## Todavía no implementado

- flujo visible V2 para marcar ausencia o cancelación y ampliaciones avanzadas
  de situaciones especiales; quedan diferidos y no bloquean el Calendario;
- consolidación adicional del motor de horas dentro de las superficies finales
  que realmente la necesiten;
- cambio de `versionName`/`versionCode` para una futura entrega 2.0.

## Próximo paso

El núcleo quedó aprobado y la segunda capa está desbloqueada. **Widget de
próximo evento** e **Informes locales** están cerrados. Este cierre documental y
el código verificado forman el checkpoint local de Informes. Por indicación
expresa de Joaquin, MAIN se detiene aquí: no prepara ni abre otra dependencia.
Copias y restauración permanece sin prompt ni tarea habilitados. API 26/API 33
del Widget continúan pendientes para la matriz de compatibilidad posterior.

El disparo físico de una alarma exacta, un reinicio real del Samsung y API 37
conservan puertas separadas para el candidato final. Los pushes autorizados para
disponibilidad, Calendario final y Resumen fueron ejecutados y consumidos en
`80fe8e5`, `fd6891e` y `ad777bb`. Cualquier push posterior, tag, Release y toda
operación sobre `main` o producción continúan prohibidos.
