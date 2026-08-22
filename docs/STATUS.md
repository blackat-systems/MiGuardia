# Estado de MiGuardia 2.0

## Objetivo activo

PLANIFICACIÓN quedó cerrada por autorización expresa de Joaquin. Las decisiones
funcionales reunidas constituyen la base vigente para avanzar; cerrar esta etapa
no significa que exista ya una implementación nueva.

MAIN 2.0 está reactivada y puede ejecutar el plan en bloques pequeños, ordenados
por dependencias y verificados antes de abrir el siguiente. Están autorizados
los commits locales como checkpoints de bloques realmente comprobados. No están
autorizados el push, los tags, un Release, la publicación ni ninguna acción
sobre el paquete o los datos de producción.

Para una explicación sin jerga de tareas, ramas, commits y push, consultar
`docs/GUIA_DE_TRABAJO_CODEX_2_0.md`.

La definición humana del producto está en
`docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`. El estado de uso de cada prompt está en
`docs/prompts/README.md` y la investigación separada por profesión en
`docs/sectores/`.

La auditoría de reactivación y Puerta 0 está registrada en
`docs/audits/2026-08-21-reactivacion-main-y-puerta-cero.md`.

## Base verificada

- Worktree: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- Base: tag anotado `v1.0.0`
- Commit base: `82db6fd8eb2c511205968894dc9857a96b16ed20`
- `main`, `origin/main` y `v1.0.0^{}` coincidían en ese commit al crear el worktree.
- Aplicación heredada: `com.blackatsystems.miguardia`
- Persistencia heredada: Room v5, trece entidades y migraciones explícitas
  `1→2→3→4→5`.

## Terminado

- MiGuardia 1.0.0 fue sellada y publicada como fuente estable.
- Se creó un worktree independiente y limpio para 2.0.
- Se decidió que 2.0 será una actualización de la misma aplicación y conservará
  los datos de 1.0.
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
- Room evoluciona desde v5 mediante migraciones explícitas y no destructivas.
  La configuración laboral ya está en Room v6 y el catálogo laboral del Corte
  A ya está en Room v7; las tablas heredadas permanecen sin cambios.
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
- Continúa deliberadamente pendiente decidir cómo se muestra el cumplimiento si
  una configuración cambia en medio de una semana o ciclo. El dominio conserva
  por separado la vigencia por fecha y la ventana del período para no inventar
  un prorrateo antes del bloque de motor y Resumen.

## Estado Git actual

- `main`, `origin/main` y `v1.0.0^{}` permanecen en MiGuardia 1.0.0, commit
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- `codex/miguardia-2.0` conserva checkpoints únicamente locales:
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
- La rama 2.0 todavía no posee upstream ni existe en GitHub. Nada de esta puerta
  fue enviado o publicado.
- El dominio nuevo vive en `core/domain/.../work/`; no se recuperó el candidato
  mensual descartado. Room v7 ya persiste su Corte A, pero todavía no existe la
  pantalla ni el recorrido visible del Corte B.

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
- una raíz V1 conserva el recorrido heredado sin selector bloqueante, mientras
  V2 oculta Resumen, Perfil, Objetivos y carga manual estructural de V1;
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

## Ejecución autorizada de MAIN

- avanzar de a un bloque pequeño y con nombre humano;
- cerrar primero sus contratos y dependencias;
- implementar sólo el alcance de ese bloque;
- ejecutar las pruebas proporcionales al impacto y revisar el diff;
- permitir un commit local únicamente cuando el checkpoint esté verificado;
- conservar explícitamente lo pendiente antes de iniciar el bloque siguiente.

Esta autorización no permite recuperar el candidato mensual descartado ni
amplía el alcance a push, tags, Release, publicación o producción.

## Backlog posterior

- cualquier cálculo monetario o liquidación permanece fuera del producto;
- monetización y distribución;
- orden fino de widgets, informes, copias/restauración y bloqueo después del
  núcleo de configuración y horas;
- logo y tipografías definitivas.

## Todavía no implementado

- activación consciente de V2 desde una instalación migrada y cambios de sector
  efectivos desde una fecha;
- persistencia de guardias pasivas o extras V2;
- motor completo de trabajo habitual, extras exactas y disponibilidad, con su
  presentación en Resumen y Calendario;
- cambio de `versionName`/`versionCode` para una futura entrega 2.0.

## Próximo paso

El siguiente incremento es la carga manual V2 sobre la única grilla existente.
Debe reutilizar el catálogo ya configurado y seguir fuera de recurrencias,
extras, disponibilidad y pantallas de Resumen. Quedan como verificaciones
separadas el recorrido físico de alarma exacta —sólo con permiso explícito— y
una migración V1 real en el Samsung; ninguna bloquea este incremento visible.
Push, tag, Release y cualquier operación sobre producción continúan prohibidos.
