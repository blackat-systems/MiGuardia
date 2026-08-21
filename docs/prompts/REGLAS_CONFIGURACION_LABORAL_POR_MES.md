# Reglas internas para configurar el trabajo y aplicar cambios por mes

> **Estado: REEMPLAZADO / NO EJECUTAR.** El código candidato descripto por este
> prompt no está en el árbol actual y la vigencia exclusivamente mensual quedó
> sustituida por cambios desde una fecha concreta. Se conserva sólo como
> antecedente de diseño.

> **CANDIDATO YA EJECUTADO — NO VOLVER A EJECUTAR.** El código resultante está
> en disco sin commit. PLANIFICACIÓN decidirá si se conserva, modifica o
> descarta después de incorporar la investigación sectorial.

- Estado: implementación candidata en revisión por PLANIFICACIÓN
- Fecha: 2026-08-21
- Auditoría MAIN: 2026-08-21, sin defectos concretos
- Rama y proyecto: `codex/miguardia-2.0` en `MiGuardia-2.0`
- Base obligatoria: `6dab82b8f239f8009cfcb32d400b50fcc4080836`
- Dependencia anterior: Puerta 0 de MAIN 2.0 aprobada
- Commit, siguiente bloque y publicación: no autorizados

## QUÉ SIGNIFICA PARA JOAQUIN

Este bloque **no agrega una pantalla ni cambia lo que hoy ve el usuario**. Deja
preparadas reglas internas para que, en un bloque posterior, MiGuardia pueda
preguntar qué trabajo realiza la persona y aplicar esa configuración desde un
mes determinado sin modificar los meses anteriores.

Concretamente define:

- cuáles son los cuatro sectores permitidos;
- si existe una base mensual de horas y cuál es;
- si debe calcularse un excedente mensual;
- si existe una franja nocturna;
- si la guardia pasiva está habilitada;
- desde qué mes comienza a regir un cambio;
- qué configuración corresponde consultar para cada mes.

No guarda nada en Room, no presenta formularios, no calcula todavía guardias
pasivas o extras y no modifica ningún dato de MiGuardia 1.0.

## TASK

Implementar y probar las reglas internas de configuración laboral y su vigencia
mensual. El resultado prepara la futura persistencia Room v6 sin tocar todavía
Room, DataStore, Compose ni Android.

## CONTEXT

MiGuardia 2.0 actualiza la misma aplicación 1.0 y mantiene una sola
configuración laboral por usuario. Sus cambios futuros se representan como
revisiones mensuales de esa única configuración; nunca como perfiles o empleos
simultáneos.

Decisión explícita más reciente de Joaquin, 2026-08-21: la configuración debe
incluir como opciones laborales visibles y diferenciadas:

1. Vigilancia privada;
2. Enfermería;
3. Medicina;
4. Policía.

Corrección explícita de Joaquin: Enfermería y Medicina son sectores laborales
independientes de primer nivel. No pertenecen a un sector genérico `Salud`, no
se agrupan como variantes de un mismo paquete funcional y deben resolverse como
identidades distintas en configuración, vocabulario y futuras reglas.

Decisión explícita posterior de Joaquin, 2026-08-21: el catálogo es cerrado y
exhaustivo. No existe una opción `Otro` ni un sector contenedor `Salud`.

El conjunto de sectores del dominio queda:

1. Vigilancia privada;
2. Enfermería;
3. Medicina;
4. Policía.

Esto no obliga a duplicar hoy algoritmos que sean realmente comunes: pueden
reutilizar lógica pura compartida. Pero Enfermería y Medicina nunca deben
colapsarse en `Salud`, compartir una única identidad efectiva ni depender de una
profesión secundaria para distinguirse. Los nombres exactos de tipos Kotlin
pueden adaptarse a las convenciones del módulo, pero el conjunto debe ser
explícito, exhaustivo y probado.

Fuentes de verdad, en este orden:

1. instrucción actual de Joaquin y este prompt;
2. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
3. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
4. ADR 0017, 0018 y 0019;
5. `AGENTS.md`;
6. contrato heredado de 1.0 cuando no haya sido sustituido.

## INPUTS

- Checkout esperado:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama esperada: `codex/miguardia-2.0`, no detached.
- HEAD esperado: `6dab82b8f239f8009cfcb32d400b50fcc4080836`.
- Los únicos cambios previos permitidos al iniciar son cambios documentales de
  MAIN en `docs/STATUS.md`, `docs/PLANIFICACION_MIGUARDIA_2_0.md`,
  `docs/PROMPT_MAESTRO_MAIN_2_0.md`, ADR 0019 y este prompt. Registran que
  Enfermería y Medicina son sectores independientes. No debe existir ningún
  cambio previo de código.
- Módulo de implementación: `core:domain`.
- El dominio existente usa Kotlin/JVM con `java.time`, JUnit 4 y lógica sin
  dependencias Android cuando es razonable.
- Room continúa en v5 y Perfil continúa en DataStore V1; no forman parte de este
  bloque.

## OUTPUT

Producir tipos y funciones de dominio puros, inmutables y probados para cubrir:

1. catálogo cerrado de cuatro sectores laborales de primer nivel, con
   Enfermería y Medicina separados y sin `Otro` ni `Salud`;
2. estado de base mensual:
   - definida con minutos enteros positivos;
   - desconocida sin valor;
   - no aplicable sin valor;
3. modo de exceso mensual, habilitable únicamente con base definida;
4. política nocturna:
   - deshabilitada; o
   - definida por minuto de inicio y fin dentro del día, distintos entre sí y
     con cruce de medianoche permitido;
5. `passiveEnabled` como parte versionada de la configuración;
6. versión de motor, distinguiendo al menos legado V1 y V2;
7. origen de revisión, distinguiendo raíz migrada V1 y revisión creada por el
   usuario;
8. revisión normal con mes de inicio obligatorio y raíz migrada con mes nulo;
9. estado efectivo `sin configurar` para una instalación nueva sin raíz ni
   revisión aplicable;
10. línea temporal de una sola configuración, con como máximo:
    - una raíz migrada;
    - una revisión no raíz por mes;
11. resolución efectiva de un mes:
    - revisión no raíz más reciente cuyo inicio sea menor o igual al mes;
    - si no existe, raíz migrada;
    - si no existe ninguna, `sin configurar`;
12. predicado canónico `mes con datos laborales`, verdadero sólo cuando existe
    al menos una guardia de cualquier estado iniciada en el mes, una ventana
    pasiva iniciada allí o una extra atribuida al mes;
13. cálculo puro de la primera vigencia permitida: mes actual si todavía no
    contiene datos laborales; mes siguiente si ya los contiene.

El dominio no debe leer reloj global, repositorios, Room ni Android. Recibe
`YearMonth` y demás datos explícitamente. No persistas totales ni diseñes aún
tablas, DAO o migraciones.

## SCOPE

Archivos permitidos:

- nuevos archivos bajo
  `core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/workconfig/**`;
- nuevas pruebas bajo
  `core/domain/src/test/java/com/blackatsystems/miguardia/core/domain/workconfig/**`.

Se puede elegir otro nombre de paquete inglés claro sólo si evita una colisión
real y se explica en el handoff. No modificar archivos de producción existentes
fuera de ese paquete salvo que una necesidad técnica imprescindible sea primero
informada a MAIN.

## DEPENDENCIES

- Puerta 0 aprobada sobre el HEAD indicado.
- Contrato cerrado de configuración única y vigencia mensual.
- Room v6 y Perfil V2 dependen de este dominio, pero no se implementan aquí.
- El motor completo de horas V2 depende de una puerta posterior.

## DO NOT

- No crear otro proyecto, rama, worktree, perfil laboral o empleo simultáneo.
- No cambiar Room v5, entidades, DAO, repositorios, esquemas o migraciones.
- No tocar Perfil, DataStore, Compose, Calendario, Resumen, Gradle, manifiesto,
  permisos, dependencias, versión, `applicationId` ni paquetes Android.
- No implementar UI, persistencia, migración `5→6`, AD, DU, P0 ni cálculos
  completos `R/D/P`.
- No reinterpretar guardias o meses históricos.
- No usar `Double` para minutos o duraciones.
- No leer ni modificar ajustes del Samsung. No instalar APK ni ejecutar QA
  física: este bloque contiene únicamente reglas Kotlin/JVM.
- No hacer commit, push, tag, merge, rebase, reset ni publicación.
- No ampliar el alcance con refactors oportunistas.

## VALIDATION

Antes de editar, ejecutar Puerta 0 mínima: ruta, rama, HEAD, estado, diff y
worktrees. Detenerse ante una diferencia distinta de este prompt de MAIN.

Agregar pruebas que demuestren como mínimo:

- exactamente cuatro sectores laborales de primer nivel;
- Enfermería y Medicina diferenciadas, sin `OTHER`, `HEALTH`, `SALUD` ni otro
  valor de escape;
- base definida positiva y rechazo de cero/negativos;
- base desconocida/no aplicable sin valor y con exceso deshabilitado;
- rechazo de exceso habilitado sin base definida;
- política nocturna deshabilitada, definida, cruce de medianoche, límites de
  minuto e inicio igual a fin inválido;
- raíz migrada con mes nulo y revisión de usuario con mes obligatorio;
- rechazo de segunda raíz y de dos revisiones en el mismo mes;
- resolución con raíz, revisión anterior, revisión exacta, meses consecutivos,
  instalación sin configurar y transición diciembre→enero;
- revisión futura que todavía no aplica a un mes anterior;
- predicado de datos laborales para guardia, pasiva y extra, y falso para la
  ausencia de esas tres fuentes;
- vigencia actual sin datos y vigencia siguiente con datos;
- `passiveEnabled` y versión de motor preservados por cada revisión.

Ejecutar, serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:domain:testDebugUnitTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug
```

Si una comprobación falla por ambiente, informar el error exacto y conservar la
evidencia válida. Al final ejecutar `git diff --check`, revisar el diff completo
y confirmar que no se tocó nada fuera del alcance.

## DONE WHEN

- el dominio representa las opciones laborales y todas las reglas de este
  bloque;
- la resolución mensual es determinista y no depende de Android/persistencia;
- las pruebas nuevas y regresiones pasan;
- no cambió Room, Perfil, Compose ni configuración Android;
- el diff está acotado y limpio;
- se entrega a MAIN un handoff compacto con OBJECTIVE, CHANGES, FILES,
  DECISIONS, VALIDATION, RISKS, PENDING y NEXT;
- queda explícito que PLANIFICACIÓN debe revisar el producto y MAIN debe auditar
  antes de cualquier commit o de guardar la configuración en Room/UI.
