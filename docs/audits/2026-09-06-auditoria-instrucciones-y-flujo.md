# Auditoría de instrucciones, documentación y flujo — MiGuardia 2.0

Fecha: 2026-09-06. Corte del inventario: 19:40:54, Argentina.

**INFORME DE AUDITORÍA. Propuestas pendientes de decisión de Joaquin.** Este documento registra una revisión; no habilita bloques, no reemplaza fuentes de autoridad y no debe agregarse a la lectura obligatoria general.

## Resultado

El proyecto tiene un problema verificable de mantenimiento de sus instrucciones: **el estado actual se copia en demasiados lugares, la lectura obligatoria arrastra historia extensa y algunos procedimientos repetibles dependen de conocimiento disperso o archivos descartables**. La jerarquía suele permitir resolver los conflictos, pero obliga a reconstruir esa resolución en cada tarea.

Se registran **15 hallazgos**: cuatro de prioridad alta para el flujo, diez media y uno baja. Son prioridades de procedimiento; no equivalen a vulnerabilidades o fallos P0/P1 de la aplicación. Las contradicciones y tamaños están verificados. El tiempo y los tokens desperdiciados son consecuencias inferidas: no se midió facturación, uso real de contexto ni duración acumulada de tareas anteriores.

La recomendación es **simplificar primero la documentación y el procedimiento de verificación**. No hay evidencia en esta auditoría que justifique reescribir la aplicación o reiniciar el proyecto.

## Alcance, método y límites

- Se inventarió recursivamente el directorio completo, incluidos ocultos, carpetas ignoradas y artefactos generados: 33.464 archivos, sin errores de enumeración. No se siguieron enlaces a otros directorios.
- Se identificaron los 688 archivos rastreados o no ignorados y se tomaron sus huellas SHA-256 antes de redactar el informe. Se analizaron estructura, extensiones, tamaño, documentación, referencias, estados y repeticiones.
- La revisión semántica cubrió las fuentes rectoras completas mediante lectura distribuida: AGENTS, README, mapa, planificación, STATUS, índice de prompts, MAIN 2.0 y MAIN V1. También las fichas sectoriales, contratos recientes, auditorías pertinentes y ADR seleccionados. Se escanearon cabeceras, estados y referencias de todos los prompts y ADR.
- Se contrastó la documentación con Gradle, módulos, persistencia V2, navegación, zoom, beta, actualizaciones y pruebas relacionadas. El análisis de código fue dirigido a coherencia documental y mantenibilidad; no fue una revisión línea por línea de todos los algoritmos.
- Tres revisiones auxiliares de sólo lectura separaron autoridad documental, estados/prompts y código/procedimientos. El auditor principal consolidó la evidencia y descartó falsos positivos.
- Las carpetas de compilación, APK y archivos privados se inventariaron por metadatos. Se leyeron resultados XML y referencias de evidencia seleccionadas; no se abrieron bases personales, credenciales ni contenido de copias.
- No se ejecutaron compilaciones, suites nuevas, ADB, dispositivos, emuladores, servicios web ni operaciones remotas. No se auditó el contenido completo del proyecto vecino `website` ni el de los otros worktrees.

El inventario adjunto `2026-09-06-auditoria-instrucciones-y-flujo-inventario.json` conserva métricas, grupos físicos y huellas de los 688 archivos de entrada. Inventariar un archivo no significa haber revisado semánticamente todo su contenido.

## Estado Git observado

| Dato | Resultado verificado localmente |
|---|---|
| Carpeta | `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0` |
| Rama | `codex/miguardia-2.0` |
| HEAD | `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35` |
| Cambios de entrada | 9 archivos rastreados modificados y 28 nuevos; 0 staged |
| Origen de esos cambios | Cliente de actualización manual y su documentación; se preservaron como entrada |
| Referencia local del remoto | `origin/codex/miguardia-2.0` en `924fd46df018f5e14637dfe90a983fefdbf27af4` |
| Diferencia | 10 commits locales por delante, 0 por detrás de esa referencia |
| Referencias protegidas | `main` y `v1.0.0^{}` en `82db6fd8eb2c511205968894dc9857a96b16ed20` |
| Worktrees registrados | 9, incluido éste; se consultó su registro, sin inspeccionar ni modificar los otros |

No se hizo fetch: el dato del remoto es la referencia almacenada localmente, no una comprobación de GitHub en vivo. El bloque 28 está en archivos locales; **su aprobación documental no demuestra que su checkpoint ya exista**. HEAD todavía es el commit de preparación del bloque.

## Tamaño y distribución del problema

| Grupo documental | Archivos Markdown | Líneas | Caracteres |
|---|---:|---:|---:|
| Raíz y documentos directos de `docs/` | 10 | 5.557 | 307.076 |
| Prompts, incluido su índice | 54 | 26.831 | 1.202.510 |
| Auditorías previas y auditoría local de actualizaciones | 54 | 8.444 | 381.258 |
| ADR | 39 | 4.704 | 249.682 |
| Sectores | 5 | 354 | 17.525 |
| Releases | 1 | 117 | 8.068 |
| **Total de entrada** | **163** | **46.007** | **2.166.119** |

Los siete documentos de arranque exigidos por AGENTS suman **4.767 líneas, 278.578 caracteres y 39.872 palabras**, antes del contrato específico, los ADR, las fichas y el código. STATUS tiene 1.831 líneas y 100.789 caracteres; MAIN V1 tiene 809 líneas y 64.994 caracteres. Entre ambos aportan aproximadamente el 59,5 % de esa lectura base.

Los prompts concentran el 55,5 % de los caracteres Markdown. Se encontraron, por comparación de bloques con espacios normalizados, un mismo comando global de verificación en 13 documentos y una misma tabla de hashes de esquemas en 16. Conservar evidencia histórica repetida puede ser válido; exigir su lectura completa en cada arranque es lo que amplifica el costo.

El directorio físico contiene 863.392.950 bytes, aproximadamente 823,4 MiB. De sus 33.464 archivos, 32.763 corresponden a compilaciones/cachés; otros 11 son salidas locales, 2 son metadatos/configuración local y 688 forman el conjunto no ignorado. Los archivos propios se distribuyen en `app` —318—, `core/domain` —99—, `core/database` —98—, `docs` —161— y raíz/herramientas —12—.

**Las cachés no son una anomalía ni deben borrarse para ahorrar tokens.** El riesgo aparece al buscar sin límites dentro de todo el directorio. La búsqueda normal respetando exclusiones ya evita gran parte de ese ruido.

## Hallazgos

### H01 — Lectura obligatoria extensa y sin selección por tarea

**Prioridad alta. Tipo: ineficiencia verificable.**

AGENTS exige leer íntegramente las fuentes generales y el prompt V1 incluso para tareas 2.0 (`AGENTS.md:64–78`). El índice, en cambio, indica consultar V1 sólo por comportamiento heredado (`docs/prompts/README.md:128`). El coordinador vuelve a exigir lectura completa después de cualquier pausa (`docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md:111–123`). El contrato de actualizaciones repite esa obligación y añade otros contratos cerrados (`docs/prompts/ACTUALIZACION_MANUAL_SIN_PERDER_DATOS_V2.md:229–244`).

**Efecto:** el arranque mínimo de una dependencia ya arrastra 278.578 caracteres; una reanudación puede volver a imponerlo sin comprobar qué cambió. El contrato de Apoyar exige también un handoff de 16 campos (`docs/prompts/APOYAR_MIGUARDIA_EN_EL_MENU_ANDROID_V2.md:333–338`).

**Propuesta:** lectura base breve con reglas estables, estado actual e índice; lectura adicional por módulo y riesgo. Reanudar leyendo el cambio desde la última base comprobada, y releer completo sólo si cambió el contrato o falta contexto. Conservar el detalle histórico accesible.

### H02 — Las fuentes obligatorias discrepan sobre la activación de MAIN

**Prioridad alta. Tipo: contradicción de estado operativo.**

`AGENTS.md:11–15,36` presenta MAIN 2.0 y su prompt como activos. `docs/PROMPT_MAESTRO_MAIN_2_0.md:3,11–13` lo declara cerrado y de trazabilidad; `docs/prompts/README.md:101–105,125–127` confirma ese cierre. `docs/STATUS.md:338–340` conserva otra frase de reactivación en presente.

La distinción válida es que MAIN conserva un **rol integrador**, mientras un **alcance nuevo requiere indicación de Joaquin**. El índice resuelve esa distinción, pero las otras entradas obligan a reconstruirla.

**Propuesta:** sacar estados fechados de AGENTS. Definir allí el rol y los límites estables; mantener una sola autoridad para el alcance autorizado y su estado. Ningún prompt histórico debe presentarse simultáneamente como orden general activa.

### H03 — El bloque 28 tiene varios estados incompatibles y un checkpoint ambiguo

**Prioridad alta. Tipo: contradicción operativa y desajuste con Git.**

- `docs/STATUS.md:201–229` registra la QA física dirigida y la aceptación del cliente para checkpoint.
- `docs/STATUS.md:1823–1828`, bajo el próximo paso, todavía dice: “Permanece sin integrar hasta completar su puerta física”.
- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md:401–408` conserva “todavía sin implementación” y preparación de esa QA.
- `docs/prompts/README.md:87–99` declara cliente cerrado y menciona checkpoint local, pero el estado Git real conserva los 37 archivos sin confirmar y HEAD sigue en la preparación `f5df1d7`.
- La auditoría específica sí distingue aprobación local y distribución pendiente (`docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md:7–21,481–495`).

**Efecto:** se puede pedir otra autorización para una prueba ya registrada, repetir QA o asumir que existe un commit todavía ausente. La planificación también conserva el relato de preparación (`docs/PLANIFICACION_MIGUARDIA_2_0.md:776–779`); ese antecedente no debería funcionar como estado actual.

**Propuesta:** una sola ficha actual: implementación presente, cliente aprobado según evidencia documentada, QA dirigida registrada, cambios locales sin checkpoint efectivo, distribución pendiente. El historial queda enlazado. No crear ese commit como parte de esta auditoría.

### H04 — README describe el producto y sus conexiones con información atrasada

**Prioridad media. Tipo: documentación desactualizada.**

`README.md:3–5` encabeza el repositorio desde 1.0. `:28–34` deja Ayuda, Widget, Informes, Copias y Bloqueo como backlog, aunque `docs/STATUS.md:9–14` los declara integrados y el código contiene esos módulos. `README.md:58` afirma que Internet sólo se usa para Clima, pero beta ya implementa activación y opiniones por HTTPS (`app/src/main/java/com/blackatsystems/miguardia/beta/BetaClient.kt:24–29,75–82`); el cliente local de actualizaciones agrega otra conexión consciente.

**Propuesta:** portada centrada en el árbol V2, con estado de distribución explícito y enlaces a historia 1.0. Describir las conexiones autorizadas por finalidad y datos, manteniendo la privacidad laboral local. Esto no prueba una filtración: la expansión beta está aprobada en la planificación y ADR 0039.

### H05 — AGENTS ordena completar una transición V1 que ya terminó

**Prioridad media. Tipo: instrucción obsoleta.**

`AGENTS.md:25–26` llama “deuda temporal del árbol actual” al modo V1 y su cadena Room; `:139–142` aún ordena definir una base exclusiva. La ejecución vigente abre `MiGuardiaV2Database`, versión 6, archivo `miguardia-v2.db`, con migraciones propias (`core/database/src/main/java/com/blackatsystems/miguardia/core/database/MiGuardiaV2Database.kt:83,105,110–114`). MAIN documenta ese retiro (`docs/PROMPT_MAESTRO_MAIN_2_0.md:78–80`). La búsqueda de `MIGRATED_V1` en fuentes de `app` y `core`, excluyendo builds, no devolvió coincidencias.

**Propuesta:** reemplazar órdenes de transición por invariantes actuales y enlazar ADR 0026. No eliminar por coincidencia textual pruebas o lectores de copias que legítimamente conservan compatibilidad entre versiones V2.

### H06 — Algunos ADR reemplazados todavía parecen vigentes

**Prioridad media. Tipo: vigencia documental incompleta.**

ADR 0005 sigue “aceptada” y prescribe extras automáticas al superar 204 horas (`docs/adr/0005-motor-basico-de-horas.md:3,20`), mientras ADR 0020 exige declaración explícita y prohíbe esa conversión (`docs/adr/0020-modelo-laboral-personalizable-y-vigencia-por-fecha.md:34–37`). ADR 0013 sigue “aceptada para implementación” y exige empresa/Inforce (`docs/adr/0013-perfil-laboral-local-en-datastore.md:3,17`); ADR 0026 retira esos campos (`docs/adr/0026-base-room-exclusiva-v2-y-retiro-del-modo-v1.md:129–133`).

La jerarquía resuelve qué producto corresponde. El defecto es que una búsqueda por tema entrega decisiones con apariencia vigente que requieren otra investigación para interpretarlas.

**Propuesta:** señalar reemplazo total o parcial, sucesor y cláusulas todavía válidas. Crear un índice breve por tema a partir de esos metadatos; conservar la historia sin reescribir sus decisiones como si nunca hubieran existido.

### H07 — La futura preparación beta puede heredar una matriz que no cubre sus incorporaciones

**Prioridad media. Tipo: contrato futuro que necesita actualización.**

`docs/prompts/PREPARAR_APK_BETA_OFICIAL_PARA_WEB_V2.md:85` afirma que la “matriz vigente” está verde en API 26/33/36/37. `docs/STATUS.md:33–35,1813–1816` aclara que esa matriz es anterior al acceso beta y que la matriz específica beta sigue pendiente en otras APIs. `:226–228` conserva pendientes propios del actualizador.

**Propuesta:** antes de habilitar el bloque 29, asociar cada evidencia a base, superficie, artefacto y API. Definir la cobertura que corresponde a preparación y a distribución. El prompt está correctamente `PENDIENTE / NO EJECUTAR`; no se detectó una ejecución indebida ni se propone reabrir por defecto todas las pruebas anteriores.

### H08 — “Cerrado” mezcla implementación, integración, QA y distribución

**Prioridad media. Tipo: taxonomía insuficiente y estados históricos dispersos.**

El índice define `CERRADO` como implementado y validado (`docs/prompts/README.md:116`), pero usa además “cliente local”, “integrado localmente”, “publicado” y “cerrado dentro del candidato integral” (`:135–166`). Bloqueo está cerrado con Recientes OEM pendiente (`:156`). STATUS conserva API 26/33 de Widget como pendientes (`docs/STATUS.md:1354–1360`) y más adelante como verdes (`:1746–1748`).

Cerrar un alcance con límites aceptados puede ser correcto. El problema es que la palabra aislada no informa qué se cerró, sobre qué base y qué falta; se requiere recorrer el relato entero.

**Propuesta:** separar columnas: implementación, revisión, commit integrado, validación local, matriz física y distribución. Los resultados viejos quedan bajo una fecha/base y remiten al cierre posterior. No borrar pendientes reales como TalkBack, Recientes OEM o actualización instalada junto con los ya resueltos.

### H09 — Una frase de permisos consumidos abarca más de lo que pretende

**Prioridad media. Tipo: ambigüedad que favorece confirmaciones repetidas.**

`docs/PROMPT_MAESTRO_MAIN_2_0.md:24–31` enumera permisos para procesar bloques, corregir, probar y crear checkpoints locales. `:50–53` dice “Todas las autorizaciones anteriores ya quedaron consumidas”, aunque el resto del párrafo trata pushes y acciones externas. Otras fuentes mantienen los checkpoints locales automáticos dentro de una integración autorizada.

**Propuesta:** precisar “las autorizaciones de push anteriores” y separar reglas permanentes, autorización del bloque y permisos externos puntuales. Esta auditoría sólo registra y propone: no reutiliza la regla de checkpoints de implementación para confirmar archivos por su cuenta.

### H10 — La prohibición de exposición privada no distingue exportación consciente

**Prioridad media. Tipo: ambigüedad, no filtración demostrada.**

`AGENTS.md:160` prohíbe exponer datos privados en informes. ADR 0034 permite nombre, puesto, notas y fotos mediante elecciones explícitas, inicialmente apagadas; las notas médicas requieren confirmación adicional (`docs/adr/0034-informes-locales-derivados-y-exportacion-segura.md:71–83`).

**Propuesta:** expresar la prohibición de exposición involuntaria o fuera de lo elegido conscientemente, sin debilitar las prohibiciones absolutas aplicables a logs o datos excluidos. La política de exportación aprobada debe tener un único lugar de referencia para evitar falsos hallazgos de privacidad.

### H11 — Las comprobaciones del README necesitan dependencias que no documenta

**Prioridad alta. Tipo: requisito de ejecución oculto.**

`README.md:38–51` enumera JDK/SDK y propone las pruebas habituales. `app/src/test/java/com/blackatsystems/miguardia/updates/UpdateWebCryptoTest.kt:19–22` exige `MIGUARDIA_BETA_WEBSITE` mediante `checkNotNull` y ejecuta Node. `app/src/test/java/com/blackatsystems/miguardia/beta/BetaWebCryptoTest.kt:35–38` también ejecuta Node. El oráculo importa código y helpers del otro proyecto (`app/src/test/resources/updates/webcrypto-oracle.mjs:6–11`).

**Efecto inferido directamente del código:** un entorno que cumple sólo el README puede fallar por una variable o ejecutable faltantes; la misma revisión Android puede arrojar resultados distintos con otra revisión de `website`. No se ejecutó ese fallo durante esta auditoría.

**Propuesta:** documentar requisitos y comprobación previa; separar explícitamente la batería local de la prueba de interoperabilidad con el otro proyecto. Esta última debe conservarse como puerta exigible cuando cambia el contrato y registrar las dos revisiones, incluyendo cambios locales relevantes. No ocultar la dependencia omitiendo silenciosamente la prueba.

### H12 — La política proporcional convive con reconstrucción global forzada como plantilla

**Prioridad media. Tipo: desalineación entre contrato y práctica.**

El coordinador define validación por superficie (`docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md:498–520`). Sin embargo, Apoyar y Actualización exigen batería global con `--rerun-tasks` y cinco APK (`docs/prompts/APOYAR_MIGUARDIA_EN_EL_MENU_ANDROID_V2.md:261–278`; `docs/prompts/ACTUALIZACION_MANUAL_SIN_PERDER_DATOS_V2.md:557–573`). MAIN ya documenta una repetición que fuerza las pruebas JVM y conserva tareas actualizadas (`docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md:452–461`).

**Propuesta:** un catálogo común de verificación por impacto, distinguiendo iteración, aceptación del bloque y candidato de distribución. Repetir después de cambios, fallos o incertidumbre concreta; conservar evidencia sólo cuando corresponda a los mismos insumos/artefactos. Reservar la reconstrucción íntegra para una razón registrada. No se concluye que toda batería global sea innecesaria ni que las ejecuciones documentadas hayan sido desperdicio.

### H13 — Procedimientos reutilizables y evidencia clave viven dentro de `build/`

**Prioridad media. Tipo: continuidad frágil.**

El runner beta figura como `app/build/beta-evidence/run_final_device.py` en su auditoría (`docs/audits/2026-09-05-acceso-beta-y-opiniones-android-v2-main.md:146–150`). Handoffs, manifestaciones de preservación y scripts de actualizaciones se guardan bajo `app/build/updates28/`; la auditoría lista la evidencia final allí (`docs/audits/2026-09-06-actualizacion-manual-sin-perder-datos-v2-main.md:476–479`). `.gitignore:11–12` excluye esos directorios. El inventario encontró ejecutores locales, pero no un runner QA versionado equivalente.

**Efecto:** una copia limpia o limpieza de compilación puede conservar el relato y perder el procedimiento exacto, obligando a reconstruirlo. No se detectó ni se afirma pérdida efectiva en esta revisión.

**Propuesta:** versionar sólo scripts reutilizables revisados, parámetros y controles de seguridad. Separar almacenamiento local de evidencia que deba sobrevivir a `clean` de los productos de compilación, con referencias y hashes saneados. Mantener APK, claves, capturas privadas y datos personales fuera de Git. No mover ni borrar nada sin revisar la política de conservación.

### H14 — La clasificación de pruebas con beta abierta/cerrada depende de disciplina manual

**Prioridad media. Tipo: fragilidad del procedimiento QA.**

`app/src/androidTest/java/com/blackatsystems/miguardia/beta/BetaClosedRootTest.kt:31` exige un proceso nuevo sin fixture beta. Los helpers de onboarding activan beta dentro de su preparación (`app/src/androidTest/java/com/blackatsystems/miguardia/OnboardingTestFixtures.kt:7–20`). El contrato de Apoyar exige revisar setup/teardown para elegir suites (`docs/prompts/APOYAR_MIGUARDIA_EN_EL_MENU_ANDROID_V2.md:255–258`).

**Propuesta:** catálogo versionado de grupos, estado requerido, aislamiento por proceso y limpieza admitida; nombres de helpers que revelen cuándo activan beta. Integrarlo con el runner de H13. La autorización de sesiones temporales y uso de dispositivos se conserva. No se afirma que actualmente una suite haya producido un resultado falso.

### H15 — Un archivo central grande amplía las dependencias de cambios pequeños

**Prioridad baja. Tipo: riesgo de mantenibilidad inferido.**

`app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt` tiene 2.919 líneas: menú/navegación, composición de destinos, Calendario, detalle diario y Apariencia. Ejemplos: navegación `:242–444`, Calendario `:1559–2490`, detalle `:2492–2748`, Apariencia `:2750–2819`.

El tamaño por sí solo no demuestra un defecto. Sí concentra lectura y coordinación en un punto que atraviesan varias dependencias, y ayuda a explicar por qué un botón puede exigir revisar la raíz completa.

**Propuesta posterior:** cuando se toque cada superficie, considerar extracciones pequeñas dentro del mismo módulo con pruebas existentes. No abrir un refactor global, más módulos Gradle o una reescritura como primer paso de esta optimización.

## Qué está frenando el flujo

La secuencia observada es: un cambio necesita interpretar reglas estables, luego un STATUS que también es historial, después varios prompts cerrados y contratos con instrucciones repetidas. La tarea produce evidencia y MAIN vuelve a contrastarla; al cerrar, se agregan nuevas actualizaciones en varios archivos, pero algunas frases anteriores quedan vigentes en apariencia. La siguiente tarea recibe un conjunto todavía mayor.

La revisión independiente, las pruebas físicas y las autorizaciones externas tienen utilidad real. Los costos evitables son **reconstruir el estado, releer antecedentes sin necesidad, redescubrir requisitos del entorno y mantener manualmente copias de la misma información**. No se demostró que falten agentes, potencia de cómputo o una nueva arquitectura para resolver estos problemas.

## Propuesta para decidir, en tres bloques

### 1. Entrada breve y estado único — recomendado primero

**Alcance:** documentación e instrucciones del proyecto. Sin cambiar comportamiento Android ni ampliar autorizaciones.

- Reducir AGENTS a invariantes, límites y ruta de lectura. Sustituir mandatos ya cumplidos y aclarar las ambigüedades H09/H10.
- Hacer de STATUS una fotografía corta: objetivo autorizado, estado del bloque, base/HEAD, cambios locales, evidencia vigente, límites y próximo paso. Trasladar el historial a referencias consultables, preservándolo.
- Dejar reglas del producto en planificación/mapa, evitando repetir allí el estado de cada ejecución. El índice de prompts debe apuntar al registro operativo y mostrar vigencia por contrato, sin otro relato paralelo.
- Actualizar README para V2 y requisitos reales. Separar el método MAIN de la antigua autorización de la hoja de ruta; conservar un solo procedimiento y contratos por tarea.
- Marcar ADR reemplazados con sucesores. Sacar V1 de la lectura obligatoria y de búsquedas habituales, manteniéndolo localizable. Puede empezarse sin mover archivos para evitar romper rutas.

**Criterios de aceptación propuestos:** ninguna contradicción sobre MAIN o bloque 28; todos los links preservados; cada dato operativo tiene un solo dueño; lectura base obligatoria de hasta 40.000 caracteres y documentos del módulo por necesidad. Frente a 278.578 caracteres, ese techo implica aproximadamente **85,6 % menos texto fijo**; no es una promesa de ahorro igual en tokens o costo total.

### 2. Verificación reproducible y proporcional

**Alcance:** documentación técnica, scripts de desarrollo y organización de pruebas; cualquier ejecución física conserva su autorización específica.

- Unificar el catálogo de comprobaciones por impacto y separar ejecución local, interoperabilidad, instrumentación y distribución.
- Documentar Node, variables necesarias y revisión del proyecto web. Registrar entradas y resultados; no aceptar una suite omitida como verde.
- Versionar runners revisados y grupos QA con estados de beta explícitos. Conservar límites de paquete, datos ficticios y restauración del estado previo.
- Guardar evidencia que deba durar fuera de carpetas limpiables, con un resumen seguro y verificable en el repositorio.
- Agregar un control pequeño de coherencia documental: rutas existentes, un solo estado actual, prompts habilitados compatibles con el bloque y sucesores ADR válidos. No requiere un servicio nuevo ni una plataforma adicional.

**Criterios de aceptación propuestos:** otro entorno puede conocer todos los requisitos sin leer chats; una prueba declara qué valida y qué necesita; los procedimientos sobreviven a una copia limpia del repo; las matrices no heredan resultados de otra superficie o base.

### 3. Mantenibilidad gradual, sólo donde haga falta

**Alcance eventual:** puntos de código que vuelvan a tocarse. Prioridad posterior a los dos bloques anteriores.

Separar responsabilidades de la raíz Compose si el siguiente cambio muestra una ventaja concreta. Usar fronteras existentes y regresiones pertinentes. No introducir dependencias ni módulos por cantidad de líneas.

**Criterio de aceptación propuesto:** menor superficie de revisión para un cambio concreto, sin alterar comportamiento ni perder evidencia. Si no se demuestra ese beneficio, no ejecutar este bloque.

## Qué conviene conservar

- Puerta 0, respeto del checkout compartido y un solo escritor por superficie.
- La separación entre MAIN integrador y revisión independiente, con tareas acotadas.
- La distinción entre compilación, JVM, instrumentación, revisión física y distribución.
- Protección de datos reales, referencias Git, firma y permisos externos.
- Contratos sectoriales que separan evidencia y decisiones; no se halló una contradicción nueva en su catálogo de cuatro sectores.
- Historia recuperable y auditorías de riesgo. La propuesta cambia cuándo se leen, no pretende borrar trazabilidad.

No se encontraron prompts hoy ejecutables en contradicción con el índice. No se confirmaron enlaces documentales internos rotos: los aparentes faltantes de contratos web están expresamente referidos a `website`. Las instrucciones V1 bien rotuladas no se contaron como órdenes actuales. Tampoco se clasificaron como fallos el versionado `1.0.0`/code 1 pendiente de preparación beta, la ausencia de confianza Release, las migraciones V2, el zoom interno ni la existencia normal de cachés.

## Comprobaciones de esta auditoría y cierre

Se verificaron inventario, hashes de entrada, Git local, cabeceras/estados, referencias y los pares documentales/code indicados. Los XML ya presentes contienen 804 pruebas en 93 suites —379 dominio, 12 database, 413 app— con cero fallos, errores u omitidas. **Se verificó el contenido de esos resultados existentes; no se ejecutaron nuevamente ni constituyen una nueva QA de esta auditoría.** La QA física anterior se cita como evidencia documental, sin revalidarla en un teléfono.

La memoria de trabajos anteriores se utilizó sólo para orientar la búsqueda y distinguir checkout/evidencia histórica. Los hallazgos se sustentan en archivos actuales. Las reglas globales inyectadas, memoria, conversaciones y configuración de la herramienta son capas externas a este repositorio; reducir la documentación local no garantiza eliminar todo el contexto recibido. No se modificó ninguna de esas capas.

La comparación final confirmó **688/688 archivos de entrada idénticos por SHA-256**, HEAD sin cambios y cero archivos staged. Las 47 referencias explícitas comprobadas apuntan a archivos y líneas existentes; el inventario JSON se pudo volver a leer sin errores. Una revisión independiente final no dejó objeciones materiales sobre los estados operativos ni la propuesta. Sólo se añadieron este informe y su inventario.

El trabajo autorizado se limita al informe y su inventario. Las 15 mejoras descritas permanecen **PROPUESTAS**. Se preservan las instrucciones y el código existentes, sin staging, commits, limpieza, movimientos, publicación ni inicio del bloque 29.
