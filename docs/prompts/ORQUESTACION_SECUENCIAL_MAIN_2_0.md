# Prompt coordinador — handoffs secuenciales de MAIN 2.0

- Estado: **ACTIVO / COORDINADOR**
- Fecha de autorización original: 2026-08-22
- Flujo actualizado por Joaquin: 2026-08-23
- Contrato humano de dependencias actualizado: 2026-08-25
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama integradora obligatoria: `codex/miguardia-2.0`
- Base protegida: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Prompt rector: `docs/PROMPT_MAESTRO_MAIN_2_0.md`
- Alcance de la autorización: recibir, auditar, integrar y cerrar **un handoff
  por vez**; preparar o abrir otra tarea sólo cuando Joaquin lo indique

En este documento, `dependencia` significa una tarea especializada de Codex que
produce una parte necesaria para MAIN. No significa agregar una biblioteca de
Gradle ni una dependencia de producción.

Toda dependencia debe poder entenderse antes de leer sus detalles técnicos.
Por eso su prompt y su handoff deben decir expresamente, en lenguaje común,
**qué hace** y **por qué existe**.

## 1. Orden para MAIN

Sos la tarea MAIN real de MiGuardia 2.0. Tu misión es conservar la columna
vertebral del proyecto y procesar, de a uno, los handoffs que Joaquin te
entregue. La hoja de ruta sigue indicando el orden técnico recomendado, pero no
autoriza a MAIN a crear por sí sola el prompt o la tarea siguiente.

Trabajá mediante este ciclo estricto:

```text
inspeccionar el estado real
→ identificar el handoff entregado por Joaquin y su base
→ auditar el resultado desde MAIN
→ corregir y repetir las pruebas necesarias
→ integrar y crear un checkpoint local verificado
→ actualizar las fuentes de verdad
→ comprobar que no quedó trabajo mezclado
→ informar el cierre y el próximo bloque recomendado
→ esperar la próxima indicación de Joaquin
```

Cuando Joaquin pida preparar el prompt de una nueva tarea, redactalo contra el
último checkpoint integrado, validalo y creá automáticamente su checkpoint
documental local. Abrí o creá esa tarea sólo si también lo pide expresamente.
No escribas prompts ni abras dependencias por adelantado.

## 2. Autorización secuencial vigente y límites

Este documento registra —y no amplía— la instrucción expresa más reciente de
Joaquin, que autoriza a MAIN a:

- recibir de Joaquin un handoff especializado por vez;
- comprobar su procedencia, prompt, HEAD de entrada y diff real;
- pedirle correcciones acotadas sobre la misma dependencia cuando la auditoría
  encuentre defectos reales;
- integrar únicamente el resultado auditado en `codex/miguardia-2.0`;
- ejecutar comprobaciones proporcionales y QA con el paquete autorizado;
- crear automáticamente commits locales pequeños como checkpoints después de
  una auditoría verde, sin pedir otra autorización para ese commit local;
- conservar como ejecutado y consumido en `0364b83` el único push adicional
  autorizado por Joaquin el 2026-08-23 para publicar el checkpoint estable
  V2-only y la recomendación futura de Agenda profesional;
- conservar como ejecutado y consumido en `80fe8e5` el único push autorizado
  por Joaquin el 2026-08-27 para publicar guardias pasivas y disponibilidad;
- conservar como ejecutado y consumido en `fd6891e` el push autorizado por
  Joaquin el 2026-08-27 para publicar Calendario final y tarjeta superior;
- preparar el prompt de una nueva tarea únicamente cuando Joaquin lo pida;
- abrir o crear una tarea especializada únicamente cuando Joaquin lo pida.

Esta autorización **no** permite:

- más de una dependencia implementadora activa a la vez;
- que una dependencia cree otras tareas o redefina el producto;
- inventar una decisión funcional o sectorial, o una decisión material sobre
  arquitectura, contratos compartidos, persistencia, privacidad o
  comportamiento público que no esté cerrada;
- agregar dependencias de producción, servicios externos, cuentas, nube,
  telemetría o permisos por conveniencia;
- usar datos reales del usuario;
- hacer un push posterior al cierre autorizado, merge, rebase, tocar `main`,
  crear tag, Release o publicar la aplicación;
- abrir o modificar la aplicación productiva del Samsung;
- cambiar `versionCode`, `versionName`, `applicationId` o la referencia
  `v1.0.0` sin una puerta nueva y expresa de Joaquin;
- ejecutar una prueba sensible —por ejemplo alarmas exactas o borrado de datos—
  sin la autorización específica que corresponda.

El flujo anterior de creación automática de dependencias quedó reemplazado el
2026-08-23 por este modelo guiado por handoffs. Las acciones externas,
destructivas, productivas o de publicación continúan siendo puertas separadas.

## 3. Fuentes de verdad

Antes del primer ciclo y al reanudar después de cualquier pausa, leé
completamente las fuentes obligatorias de `AGENTS.md` en su orden vigente. Como
mínimo:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las fichas aplicables de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md` y este coordinador;
8. los ADR y contratos heredados aplicables;
9. código, pruebas, Git y dispositivo reales.

No uses un handoff, un chat o un recuerdo como sustituto del repositorio. Ante
una contradicción, aplicá la jerarquía de autoridad de `AGENTS.md`, registrá la
resolución y preguntá a Joaquin sólo si todavía cambia materialmente el
producto.

## 4. Decisiones que ninguna dependencia puede reabrir

- MiGuardia 2.0 continúa sobre el código que nació del tag inmutable `v1.0.0`;
  ese tag se protege como fuente técnica.
- MiGuardia 1.0 fue una prueba interna sin usuarios: no se preservan sus datos,
  no existe activación V1→V2 y toda experiencia 2.0 comienza limpia.
- `applicationId` se mantiene por ahora, pero no representa una promesa de
  compatibilidad. Cambiarlo continúa siendo una puerta separada.
- Existen exactamente cuatro sectores: Vigilancia privada, Policía, Enfermería
  y Medicina. No existen `Salud`, `Otro` ni perfiles laborales simultáneos.
- Hay una sola configuración laboral con cambios vigentes desde una fecha
  concreta.
- Existe una sola grilla mensual: consultar no escribe y editar es explícito.
- El trabajo activo reemplaza únicamente el tramo coincidente de una guardia
  pasiva; ese tiempo no puede contabilizarse también como pasivo.
- Los montos, escalas, liquidaciones, deducciones, convenios y datos sindicales
  están fuera del producto.
- Los datos son locales por defecto. No hay cuentas, nube, sincronización,
  analítica ni telemetría.
- Enfermería y Medicina se investigan y validan como sectores independientes;
  ninguna dependencia puede copiar reglas de uno al otro por analogía.
- Los ajustes visuales internos son 100 %, 150 % y 200 %. No se consulta ni se
  modifica `font_scale`, densidad o tamaño visual del sistema.

## 5. Puerta 0 al comienzo de cada ciclo

Antes de escribir, delegar, integrar o confirmar cualquier bloque, MAIN debe
verificar y registrar:

- ruta absoluta y raíz Git;
- rama activa, HEAD completo, upstream y divergencia;
- `v1.0.0^{}`, `main` y `origin/main`;
- estado de todos los worktrees relevantes;
- archivos modificados, staged y no rastreados;
- diff completo contra el checkpoint de entrada;
- remoto privado y autor Git;
- JDK, Android SDK, wrapper de Gradle y espacio razonable;
- dispositivo disponible cuando el bloque necesite QA física;
- ausencia de secretos, datos reales, nombres sindicales específicos, escalas
  o material monetario prohibido en el alcance que se va a confirmar.

Si existe un checkout detached, una base inesperada, cambios sin dueño o un
worktree que pueda pisarse, no avances hasta resolverlo de forma no destructiva.
Nunca uses `reset --hard` ni descartes cambios ajenos.

## 6. Regla de reanudación: nunca duplicar trabajo

Después de Puerta 0, clasificá el estado real:

### A. Ya hay un resultado candidato en el checkout

No crees otra tarea para el mismo bloque. Leé su prompt candidato, identificá
todos sus archivos modificados y no rastreados y solicitá el handoff si la tarea
todavía puede responder. Si falta, registrá su ausencia y tratá el diff como un
candidato no verificado; nunca fabriques ni reconstruyas un handoff. Luego
comenzá la auditoría de MAIN sobre la evidencia real disponible.

### B. Hay una tarea especializada abierta

No abras otra implementadora. Esperá su resultado, respondé sus preguntas
materiales y mantené `docs/STATUS.md` como fuente de continuidad.

### C. El bloque activo tiene prompt pero todavía no tiene implementación

Comprobá que el prompt siga siendo correcto para el HEAD actual e informá su
estado. No crees la dependencia hasta que Joaquin lo pida expresamente. Si
Joaquin solicita actualizar el prompt, corregilo, validalo y confirmá primero
el contrato documental.

### D. El bloque anterior está cerrado y el checkout está limpio

Informá cuál es el primer bloque pendiente del grafo y por qué conviene seguir
con él. Después esperá: no redactes el prompt ni abras la tarea hasta que
Joaquin lo indique.

### E. Hay una contradicción o decisión material abierta

Detené la cadena. Explicale a Joaquin una sola pregunta concreta, recomendá una
opción y no permitas que una dependencia invente la respuesta.

## 7. Antecedente cerrado del primer ciclo

Al crear este coordinador, el último checkpoint remoto era `836d908` y existía
un candidato sin commit de **Elegir días y cargar jornadas desde horarios
guardados**. MAIN cerró ese ciclo en dos checkpoints locales:

- `ca029d1`: coordinación documental;
- `ae57686`: carga manual V2 auditada, probada e integrada.

El prompt `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md` está `CERRADO`. Esta
sección es trazabilidad histórica, no una orden para volver a ejecutar el
bloque ni para crear automáticamente el siguiente.

## 8. Cómo elegir el bloque siguiente

Construí un grafo simple: cada bloque debe depender sólo de contratos ya
integrados. Usá nombres humanos y evitá códigos como `1A` o `Incremento 3`.
El grafo permite recomendar el próximo paso; no reemplaza la indicación de
Joaquin para escribir su prompt o abrir su tarea.

La secuencia conocida al redactar este prompt es:

### Base ya cerrada

1. Adaptar el Calendario al tamaño del teléfono.
2. Crear las reglas internas configurables de trabajo.
3. Guardar la configuración y migrar Room sin destruir historia.
4. Crear lugares, tipos y horarios guardados.
5. Elegir el rubro y preparar el primer lugar de trabajo.

### Núcleo laboral pendiente o en curso

6. Elegir días y cargar jornadas desde horarios guardados —cerrado en
   `ae57686`.
7. Corregir o eliminar una jornada V2 individual —cerrado en `4646f66`.
8. Retirar el modo V1 antes de ampliar nuevamente la persistencia, conservando
   el código útil —cerrado en `b04dd59`.
9. Repetir jornadas y editar una fecha o todo lo futuro —cerrado en
   `2d41f60`.
10. Registrar el horario realmente trabajado y clasificar la diferencia
    adicional de una jornada existente —cerrado en `2e61385`.
11. Extras independientes y avance de horas: registrar trabajo extra sin
    jornada dueña y calcular trabajo activo, extras y avance por mes, semana o
    ciclo. La persona elige la fecha de reinicio y no existe prorrateo
    automático —cerrado en `964b7cd`—.
12. Registrar guardias pasivas y descontar sólo el trabajo coincidente
    —cerrado por MAIN en el checkpoint de disponibilidad del 2026-08-27—.
13. Terminar el Calendario y desplegar todas las jornadas del día —próximo
    bloque habilitado—.
14. Mostrar y personalizar el Resumen de horas.
15. Adaptar el motor de próximo evento a MiGuardia 2.0.
16. Adaptar las notificaciones a todos los eventos compatibles.
17. Auditar integralmente el núcleo y su compatibilidad Android.

Las ampliaciones futuras de situaciones especiales y cualquier consolidación
adicional del motor de horas quedan fuera de esta cadena inmediata. Sólo se
reinsertan por una nueva indicación de Joaquin o por una dependencia real
demostrada durante una auditoría posterior.

El motor de próximo evento debe estabilizarse antes de adaptar notificaciones.
La edición individual reutiliza Room v7 y no amplía el esquema. El retiro del
modo V1 debe cerrar la falsa compatibilidad de datos sin descartar componentes
útiles antes de que un bloque posterior vuelva a ampliar la persistencia.

MAIN puede dividir uno de estos objetivos si su riesgo exige dos contratos
ejecutables, pero no puede fusionar bloques de manera que se pierdan aislamiento
o pruebas. La lista no reemplaza el mapa ni la planificación: si una fuente de
mayor autoridad cambia, se actualizan primero el grafo, el índice y el estado.

### Segunda capa, después del checkpoint del núcleo

Sólo después de cerrar y auditar el núcleo laboral:

20. Adaptar el widget al motor final de próximo evento.
21. Generar informes locales de jornadas y horas.
22. Crear y restaurar copias locales seguras.
23. Proteger el acceso local a MiGuardia.
24. Completar la Ayuda y el recorrido inicial sobre la interfaz definitiva.
25. Auditar la aplicación completa y emitir el candidato local MiGuardia 2.0.

Estas cinco superficies locales permanecen en la hoja de ruta después del
checkpoint del núcleo. Cada una se prepara sólo cuando Joaquin pida su prompt o
su tarea. Si una decisión funcional todavía no está cerrada, MAIN se detiene y
formula la pregunta mínima necesaria. Estas superficies no se adelantan ni
habilitan servicios externos.

## 9. Contrato obligatorio de cada dependencia

Cuando Joaquin pida preparar una nueva tarea, MAIN debe crear o actualizar un
archivo en `docs/prompts/` con estas secciones explícitas:

```text
QUÉ HACE
POR QUÉ EXISTE
ROLE
TASK
CONTEXT
INPUTS
OUTPUT
SCOPE
DEPENDENCIES
DO NOT
VALIDATION
HANDOFF A MAIN
DONE WHEN
```

Las dos primeras secciones son obligatorias y se escriben para Joaquin, no
para otro programador:

- `QUÉ HACE`: explica en uno o dos párrafos breves qué capacidad concreta
  incorpora o qué resultado deja disponible, sin apoyarse en nombres de
  clases, tablas o archivos;
- `POR QUÉ EXISTE`: explica qué problema o hueco del producto resuelve, de qué
  bloque anterior depende y qué trabajo posterior permite desbloquear.

No alcanza con repetir el título ni con usar frases vagas como «avanzar la
aplicación». `TASK`, `SCOPE` y `OUTPUT` conservan después la precisión técnica.

El prompt debe incluir además:

- nombre humano y objetivo demostrable;
- proyecto, rama y HEAD exactos de entrada;
- fuentes que debe leer completas;
- contratos públicos que puede asumir;
- archivos o módulos permitidos y prohibidos;
- decisiones congeladas aplicables;
- comportamiento fuera de alcance;
- casos límite y estados de error;
- pruebas exactas y niveles de evidencia esperados;
- límites de Git, dispositivo, privacidad y datos;
- prohibición de crear otras tareas;
- prohibición de modificar `docs/STATUS.md`, `docs/prompts/README.md`, ADR o
  auditorías, porque esas fuentes son propiedad de MAIN;
- formato de handoff compacto;
- condición para detenerse ante un mismatch.

El prompt no puede pedirle a un especialista que diseñe una regla de negocio
todavía abierta. Primero MAIN estabiliza el contrato o pregunta a Joaquin.

Después de redactar el prompt y antes de cualquier apertura:

1. agregá el prompt a `docs/prompts/README.md` como `HABILITADO`;
2. actualizá `docs/STATUS.md` con objetivo, dependencia y HEAD;
3. revisá también todos los archivos no rastreados y prepará staging sólo con
   las rutas documentales del contrato;
4. ejecutá `git diff --check`, `git diff --cached --check` y revisá el diff
   staged completo;
5. creá un checkpoint local `docs:` para que la dependencia nazca de un HEAD
   reproducible;
6. confirmá que el checkout integrador no contiene trabajo mezclado.

La creación de ese checkpoint documental es automática. Haber pedido el prompt
no equivale por sí solo a pedir que MAIN abra o cree la tarea.

## 10. Creación y conducción de la tarea especializada

Una tarea especializada sólo se abre o crea por una indicación expresa de
Joaquin. Si Joaquin administra esa tarea y después entrega su handoff, MAIN no
la recrea ni simula que la condujo. Si Joaquin pide que MAIN la cree, se abre
exactamente una implementadora desde el checkpoint documental correspondiente.

Al crearla:

- abrí exactamente un subagente implementador;
- entregale el archivo de prompt completo, no un resumen informal;
- informale el HEAD exacto de entrada y el modo de integración previsto;
- preferí aislamiento de rama o worktree cuando reduzca riesgos, siempre desde
  el checkpoint integrado actual;
- impedí que toque otro worktree, `main`, producción o datos reales;
- no permitas commit, push, merge, rebase, tag ni cambios en las fuentes de
  estado de MAIN; el especialista devuelve un diff y un handoff;
- no la presentes como terminada por el solo hecho de haber sido creada.

MAIN recibe el handoff de Joaquin. Puede realizar inspecciones de sólo lectura
mientras la tarea está trabajando, pero no abre otra dependencia implementadora
ni modifica los mismos archivos.

Si la herramienta de colaboración interna no está disponible o falla, no
simules la creación. Conservá el prompt habilitado, registrá `PENDIENTE` y
explicá la limitación.

Si un turno termina antes de cerrar el bloque, dejá en `docs/STATUS.md` o en un
handoff durable, según corresponda, el identificador de la tarea, prompt, base,
HEAD, archivos pendientes, última validación real y próxima acción exacta. Al
reanudar, recuperá ese estado antes de crear nada nuevo.

## 11. Handoff mínimo que MAIN debe exigir

La dependencia devuelve:

- `QUÉ HACE`: explicación humana del resultado que debía entregar;
- `POR QUÉ EXISTE`: problema que resolvía y siguiente parte del proyecto que
  deja habilitada;
- `OBJECTIVE`: resultado concreto;
- `CHANGES`: qué cambió realmente;
- `FILES`: modificados y no rastreados;
- `DECISIONS`: sólo decisiones menores dentro del contrato;
- `VALIDATION`: comandos, conteos y resultados reales;
- `PHYSICAL QA`: dispositivo, paquete y recorrido, o pendiente explícito;
- `RISKS`: defectos o incertidumbres;
- `PENDING`: trabajo deliberadamente excluido;
- `GIT`: rama, HEAD y ausencia o presencia de commit/push;
- `NEXT`: devolución exclusiva a MAIN.

Un handoff no es evidencia suficiente y nunca equivale a integración.

## 12. Auditoría e integración de MAIN

MAIN realiza una verificación independiente antes de aceptar el resultado:

1. repite Puerta 0 y fija la base exacta del especialista;
2. inspecciona cada hunk y cada archivo no rastreado;
3. compara alcance solicitado contra alcance real;
4. busca cambios oportunistas, placeholders, datos reales, logs privados,
   secretos y material monetario o sindical prohibido;
5. verifica que no cambiaron Room, Gradle, manifiesto, permisos, versión,
   dependencias o contratos compartidos fuera del alcance;
6. comprueba esquema, base limpia, reapertura e historia V2 cuando el bloque sí
   autoriza persistencia;
7. ejecuta pruebas nuevas y regresiones vecinas desde MAIN;
8. realiza QA física proporcional cuando la superficie modificada lo exige;
9. encarga una revisión independiente de sólo lectura después de cada bloque
   con cambios ejecutables, sin delegar la decisión final;
10. corrige únicamente defectos de integración o devuelve observaciones a la
    misma dependencia;
11. repite la batería afectada después de la última corrección.

El auditor independiente no modifica el objeto que audita. Si encuentra un
defecto, la corrección vuelve al implementador original o a MAIN dentro de su
frontera de integración. Si el mismo defecto persiste después de dos rondas de
corrección sin evidencia nueva, detené el ciclo y explicá el bloqueo en vez de
crear un bucle infinito.

Si el resultado vive sin commit en el checkout compartido, MAIN lo audita en
el lugar y prepara staging exacto. Si vuelve desde una rama o worktree aislado,
MAIN compara el diff completo desde su base y transporta sólo los cambios
auditados. Nunca integra a ciegas por confiar en el nombre de una rama o en el
resumen del especialista.

## 13. Validación proporcional obligatoria

Como mínimo, todo bloque ejecuta:

- pruebas nuevas y regresiones de módulos vecinos;
- `git diff --check`;
- revisión de archivos modificados, staged y no rastreados;
- revisión de permisos, dependencias, logs, secretos y datos;
- comprobación de documentación contra el resultado real.

Además:

- dominio puro: JVM y límites temporales relevantes;
- Room: durante la limpieza pre-release, prueba de base V2 vacía, esquema,
  reapertura y rollback; después de fijar esa base, migración desde la versión
  V2 inmediata y datos representativos;
- Compose: instrumentación compilada y, cuando corresponda, ejecutada;
- interfaz, permisos, notificaciones, widget, biometría, archivos o sistema:
  QA física con paquete QA, datos ficticios y producción intacta;
- bloques transversales: API 26 como piso, API 33 para permisos modernos,
  Samsung `SM-S938B` API 36 como principal y API 37 antes del candidato final;
- superficies visibles: claro/oscuro, orientación y zoom interno 100 %, 150 %
  y 200 %.

Informá por separado:

- `COMPILADO`;
- `JVM VERIFICADO`;
- `ANDROIDTEST COMPILADO`;
- `INSTRUMENTACIÓN EJECUTADA`;
- `REVISIÓN FÍSICA`;
- `PENDIENTE`.

Nunca conviertas compilación de AndroidTest en afirmación de ejecución física.

## 14. Cierre de un bloque

Un bloque queda cerrado sólo cuando:

- satisface su `DONE WHEN`;
- no contiene cambios fuera de alcance;
- las verificaciones proporcionales están verdes;
- los pendientes deliberados no impiden usar el recorrido entregado;
- `docs/STATUS.md` describe el estado real;
- su entrada en `docs/prompts/README.md` pasa a `CERRADO`;
- existe una auditoría durable si el riesgo justifica conservar la evidencia;
- los ADR aplicables coinciden con la implementación;
- el staging contiene exactamente el bloque auditado;
- se crea un commit local coherente con Conventional Commits;
- el checkout integrador queda limpio. Si existen cambios ajenos que no pueden
  aislarse de forma segura, el ciclo se detiene antes de abrir otra dependencia.

Después del checkpoint, MAIN informa a Joaquin en lenguaje común:

- qué puede hacer ahora la aplicación;
- por qué existía esa dependencia y qué problema dejó resuelto;
- qué archivos o capas cambiaron;
- qué se probó realmente;
- cuál es el commit local;
- qué riesgo queda;
- cuál es el próximo bloque recomendado.

Luego espera la indicación de Joaquin. No prepara el prompt ni crea la próxima
tarea automáticamente.

## 15. Condiciones de parada obligatoria

MAIN no abre una dependencia siguiente sin una indicación expresa de Joaquin.
Además detiene la integración en curso si ocurre cualquiera de estas
condiciones:

- ruta, rama, HEAD, base o upstream no coinciden con lo esperado;
- existen cambios locales sin dueño o no se puede aislar el bloque;
- falta un handoff verificable y tampoco existe un diff atribuible con evidencia
  suficiente para una auditoría independiente;
- fallan pruebas, lint, empaquetado, instrumentación requerida o una migración;
- Room cambia sin esquema y pruebas de la transición autorizada;
- falta QA física requerida por el comportamiento modificado;
- el mismo defecto persiste después de dos rondas de corrección sin evidencia
  nueva;
- una dependencia alteró contratos compartidos fuera de su autorización;
- existe una contradicción funcional o una decisión material abierta;
- haría falta agregar una dependencia de producción o servicio externo no
  autorizado;
- haría falta un push posterior al cierre autorizado, tocar `main`, cambiar
  versión, crear tag o Release, publicar o actuar sobre producción;
- el bloque anterior no quedó integrado, documentado y controlado;
- la colaboración interna no permite crear o recuperar el subagente real.

No conviertas un bloqueo en un cierre falso. Conservá la evidencia disponible,
explicá el punto exacto y pedí sólo la autoridad o decisión que falta.

## 16. Qué significa “aplicación terminada”

MAIN puede declarar **candidato local completo** únicamente cuando:

- todos los bloques del mapa y la planificación aprobados están `CERRADO`, o
  fueron excluidos expresamente por Joaquin;
- no queda un prompt **de implementación** `HABILITADO`, un candidato sin
  auditar ni una tarea abierta; los prompts rectores pueden continuar activos
  como fuentes de verdad;
- `v1.0.0` sigue intacto como base de código y el producto final no contiene un
  modo migrado ni una activación V1→V2;
- las cuatro experiencias sectoriales respetan sus contratos y no se
  homologaron por analogía;
- Calendario, Resumen, horas, disponibilidad, situaciones especiales, próximo
  evento y notificaciones recorren sus estados principales y de error;
- la segunda capa incluida por Joaquin está cerrada o expresamente diferida;
- la batería global, lint, ensamblado, instrumentación y matriz Android exigida
  están documentados con resultados reales;
- privacidad, secretos, permisos, logs y contenido prohibido fueron auditados;
- no quedan placeholders accidentales, borrados silenciosos de datos V2 ni
  datos reales;
- documentación, ADR, índice de prompts, auditorías y Git describen el mismo
  estado;
- el árbol integrador está limpio en un checkpoint local identificable.

En ese cierre, MAIN actualiza el estado del prompt rector y de este coordinador
a `CERRADO / CANDIDATO LOCAL COMPLETO`; dejan de ordenar nuevas implementaciones
y se conservan como fuentes de trazabilidad.

Ese estado **no equivale a publicación**. Al alcanzarlo, MAIN debe detener el
ciclo, entregar la auditoría final y pedir una autorización separada para cada
puerta necesaria: cambio de versión, push, tag, GitHub Release y cualquier
acción sobre producción.

## 17. Estado inmediato

El bloque **Guardias pasivas y disponibilidad** quedó auditado, corregido,
verificado y cerrado por MAIN. `MiGuardiaV2Database`, `miguardia-v2.db` y Room
V2 versión 5 son la base activa, con cadena explícita `1→2→3→4→5`.

Guardias pasivas y disponibilidad está cerrada. Por indicación expresa de
Joaquin, el siguiente bloque es **Calendario final y tarjeta superior**.

El prompt `GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md` está `CERRADO`. El bloque
define un único concepto con los nombres Guardia pasiva, Disponible para
llamado o Retén; impide ventanas superpuestas y descuenta solamente la unión
del trabajo activo coincidente.

El prompt `CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md` está habilitado. No hay
una tarea especializada abierta ni código candidato para ese bloque hasta que
Joaquin entregue el prompt a la nueva tarea o pida abrirla.
