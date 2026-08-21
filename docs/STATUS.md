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
- Room debe evolucionar desde v5 mediante migraciones explícitas y no
  destructivas. Todavía no existe Room v6 ni una migración nueva implementada.
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

## Estado Git actual

- `main`, `origin/main` y `v1.0.0^{}` permanecen en MiGuardia 1.0.0, commit
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- `codex/miguardia-2.0` conserva checkpoints únicamente locales:
  - `a3e89fdb56aedeed77c89824cec137f37f4c9619`: Calendario adaptable;
  - `6dab82b8f239f8009cfcb32d400b50fcc4080836`: planificación y traspaso a MAIN.
  - `3519606aeda3a26bed7ec8fc0feb8b7f3f788d35`: retiro de las funciones
    remunerativas.
  - el checkpoint documental que contiene este estado: cierre de
    PLANIFICACIÓN, reactivación de MAIN y contrato del primer bloque.
- La rama 2.0 todavía no posee upstream ni existe en GitHub. Nada de esta puerta
  fue enviado o publicado.
- No existe código `workconfig` ni una implementación nueva de la configuración
  laboral; el próximo bloque parte del contrato vigente, no del candidato
  mensual descartado.

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

- Room v6 y la migración `5→6`;
- Perfil/configuración visible para elegir sector y reglas;
- persistencia de guardias pasivas o extras V2;
- motor completo de trabajo habitual, extras exactas y disponibilidad, con su
  presentación en Resumen y Calendario;
- cambio de `versionName`/`versionCode` para una futura entrega 2.0.

## Próximo paso

MAIN debe ejecutar
`docs/prompts/REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md`: reglas puras de
sector, vigencia por fecha y referencia de horas antes de persistencia o
interfaz. Puede consolidar cada bloque aprobado mediante un commit local. Push,
tag, Release y cualquier operación sobre producción continúan prohibidos.
