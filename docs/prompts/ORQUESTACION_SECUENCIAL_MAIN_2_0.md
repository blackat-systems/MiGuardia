# Prompt coordinador — cadena secuencial de dependencias de MAIN 2.0

- Estado: **ACTIVO / COORDINADOR**
- Fecha de autorización: 2026-08-22
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama integradora obligatoria: `codex/miguardia-2.0`
- Base protegida: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Prompt rector: `docs/PROMPT_MAESTRO_MAIN_2_0.md`
- Alcance de la autorización: crear, recibir, auditar e integrar **una tarea
  especializada por vez** hasta completar la hoja de ruta aprobada

En este documento, `dependencia` significa una tarea especializada de Codex que
produce una parte necesaria para MAIN. No significa agregar una biblioteca de
Gradle ni una dependencia de producción.

## 1. Orden para MAIN

Sos la tarea MAIN real de MiGuardia 2.0. Tu misión es continuar desde el estado
verdadero del repositorio hasta dejar la aplicación completa como candidato
local verificable, siguiendo la hoja de ruta aprobada y sin depender de la
memoria de los chats.

Trabajá mediante este ciclo estricto:

```text
inspeccionar el estado real
→ cerrar cualquier resultado ya presente
→ elegir el primer bloque pendiente por dependencias
→ escribir su prompt autosuficiente
→ registrar y habilitar ese prompt
→ crear una sola tarea especializada
→ esperar su handoff
→ auditar el resultado desde MAIN
→ corregir y repetir las pruebas necesarias
→ integrar y crear un checkpoint local verificado
→ actualizar las fuentes de verdad
→ comprobar que no quedó trabajo mezclado
→ recién entonces iniciar el bloque siguiente
```

No abras todos los chats ni escribas todos los prompts por adelantado. Cada
contrato se redacta contra el código ya integrado del bloque anterior.

## 2. Autorización secuencial vigente y límites

Este documento registra —y no amplía— la instrucción expresa de Joaquin que
autoriza a MAIN a:

- crear un subagente interno especializado por vez para los bloques incluidos
  en la hoja de ruta aprobada;
- enviarle el prompt durable correspondiente y el HEAD exacto de entrada;
- esperar su resultado y recibir su handoff;
- pedirle correcciones acotadas sobre la misma dependencia cuando la auditoría
  encuentre defectos reales;
- integrar únicamente el resultado auditado en `codex/miguardia-2.0`;
- ejecutar comprobaciones proporcionales y QA con el paquete autorizado;
- crear commits locales pequeños como checkpoints después de una auditoría
  verde;
- preparar el prompt de la dependencia siguiente sin volver a pedir permiso
  por una decisión que ya esté congelada.

Esta autorización **no** permite:

- más de una dependencia implementadora activa a la vez;
- que una dependencia cree otras tareas o redefina el producto;
- inventar una decisión funcional o sectorial, o una decisión material sobre
  arquitectura, contratos compartidos, persistencia, privacidad o
  comportamiento público que no esté cerrada;
- agregar dependencias de producción, servicios externos, cuentas, nube,
  telemetría o permisos por conveniencia;
- usar datos reales del usuario;
- hacer otro push, merge, rebase, tocar `main`, crear tag, Release o
  publicación;
- abrir o modificar la aplicación productiva del Samsung;
- cambiar `versionCode`, `versionName`, `applicationId` o la referencia
  `v1.0.0` sin una puerta nueva y expresa de Joaquin;
- ejecutar una prueba sensible —por ejemplo alarmas exactas o borrado de datos—
  sin la autorización específica que corresponda.

Este documento registra la autorización secuencial expresa de Joaquin. Las
acciones externas, destructivas, productivas o de publicación continúan siendo
puertas separadas.

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

- MiGuardia 2.0 es una actualización de la misma aplicación Android y nace del
  tag inmutable `v1.0.0`.
- Se preservan `applicationId`, historia local y cadena de migraciones.
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

Comprobá que el prompt siga siendo correcto para el HEAD actual. Si lo es,
creá una única dependencia desde ese HEAD. Si quedó viejo, actualizalo y
confirmá primero el contrato documental.

### D. El bloque anterior está cerrado y el checkout está limpio

Elegí el primer bloque pendiente del grafo, redactá su contrato y abrilo según
el ciclo de este documento.

### E. Hay una contradicción o decisión material abierta

Detené la cadena. Explicale a Joaquin una sola pregunta concreta, recomendá una
opción y no permitas que una dependencia invente la respuesta.

## 7. Primer arranque obligatorio desde el estado observado

Al crear este coordinador, el último checkpoint integrado observado fue
`836d908f54a407c48cc9e3c27c9587c6dc908ca2`. El prompt que originó el candidato
fue `docs/prompts/CARGA_MANUAL_DE_JORNADAS_V2.md` y ahora debe permanecer
marcado `CANDIDATO / EN AUDITORÍA — NO REEJECUTAR`.

También había un resultado sin commit de **Elegir días y cargar jornadas desde
horarios guardados**, compuesto por tres archivos modificados y cuatro rutas
nuevas bajo `app/src/main`, `app/src/test` y `app/src/androidTest`.

Por lo tanto, la primera acción de MAIN al recibir esta orden es:

1. volver a verificar el estado en vivo, porque este dato puede haber cambiado;
2. si el candidato continúa presente, **no crear otra tarea de carga manual**;
3. auditarlo completamente contra su prompt, incluido cada archivo no
   rastreado;
4. auditar los cambios documentales de este coordinador y crear para ellos un
   checkpoint local `docs:` separado, con staging por rutas explícitas y sin
   incluir ningún archivo del candidato ejecutable;
5. corregir, validar, documentar e integrar el candidato de carga manual desde
   su base registrada;
6. recién con ambos checkpoints controlados, crear el contrato
   siguiente.

Si el repositorio ya avanzó cuando se ejecute esta orden, aplicá la regla de
reanudación de la sección anterior y continuá desde el primer bloque realmente
pendiente. No vuelvas atrás por obedecer una fotografía vieja.

## 8. Cómo elegir el bloque siguiente

Construí un grafo simple: cada bloque debe depender sólo de contratos ya
integrados. Usá nombres humanos y evitá códigos como `1A` o `Incremento 3`.

La secuencia conocida al redactar este prompt es:

### Base ya cerrada

1. Adaptar el Calendario al tamaño del teléfono.
2. Crear las reglas internas configurables de trabajo.
3. Guardar la configuración y migrar Room sin destruir historia.
4. Crear lugares, tipos y horarios guardados.
5. Elegir el rubro y preparar el primer lugar de trabajo.

### Núcleo laboral pendiente o en curso

6. Elegir días y cargar jornadas desde horarios guardados —en curso al crear
   este coordinador.
7. Activar MiGuardia 2.0 desde una instalación anterior y cambiar de rubro
   desde una fecha.
8. Repetir jornadas y editar una fecha o todo lo futuro.
9. Registrar el horario realmente trabajado y las horas adicionales.
10. Calcular trabajo activo, extras y avance contra la referencia por mes,
    semana o ciclo.
11. Registrar guardias pasivas y descontar sólo el trabajo coincidente.
12. Registrar situaciones especiales sin convertirlas automáticamente en
    horas.
13. Consolidar el motor final de horas y cumplimiento con trabajo activo,
    extras, guardias pasivas y situaciones especiales.
14. Terminar el Calendario y desplegar todas las jornadas del día.
15. Mostrar y personalizar el Resumen de horas.
16. Adaptar el motor de próximo evento a MiGuardia 2.0.
17. Adaptar las notificaciones a todos los eventos compatibles.
18. Auditar integralmente el núcleo y su compatibilidad Android.

El motor de próximo evento debe estabilizarse antes de adaptar notificaciones.
La activación desde una instalación 1.0 y los cambios de sector no pueden
quedar como un hueco oculto entre la carga manual y los flujos posteriores.

MAIN puede dividir uno de estos objetivos si su riesgo exige dos contratos
ejecutables, pero no puede fusionar bloques de manera que se pierdan aislamiento
o pruebas. La lista no reemplaza el mapa ni la planificación: si una fuente de
mayor autoridad cambia, se actualizan primero el grafo, el índice y el estado.

### Segunda capa, después del checkpoint del núcleo

Sólo después de cerrar y auditar el núcleo laboral:

19. Adaptar el widget al motor final de próximo evento.
20. Generar informes locales de jornadas y horas.
21. Crear y restaurar copias locales seguras.
22. Proteger el acceso local a MiGuardia.
23. Completar la Ayuda y el recorrido inicial sobre la interfaz definitiva.
24. Auditar la aplicación completa y emitir el candidato local MiGuardia 2.0.

La orden actual de Joaquin incluye continuar secuencialmente con estas cinco
superficies locales después del checkpoint del núcleo. No autoriza a inventar
su comportamiento: si una decisión funcional todavía no está cerrada, MAIN se
detiene y formula la pregunta mínima necesaria antes de escribir el prompt
afectado. Estas superficies no se adelantan ni habilitan servicios externos.

## 9. Contrato obligatorio de cada dependencia

Antes de crear una tarea especializada, MAIN debe crear o actualizar un archivo
en `docs/prompts/` con estas secciones explícitas:

```text
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

Antes de abrir la tarea:

1. agregá el prompt a `docs/prompts/README.md` como `HABILITADO`;
2. actualizá `docs/STATUS.md` con objetivo, dependencia y HEAD;
3. revisá también todos los archivos no rastreados y prepará staging sólo con
   las rutas documentales del contrato;
4. ejecutá `git diff --check`, `git diff --cached --check` y revisá el diff
   staged completo;
5. creá un checkpoint local `docs:` para que la dependencia nazca de un HEAD
   reproducible;
6. confirmá que el checkout integrador no contiene trabajo mezclado.

## 10. Creación y conducción de la tarea especializada

Usá un subagente interno de MAIN para que su resultado vuelva automáticamente a
MAIN. Este documento registra la autorización expresa de Joaquin; no la concede
ni la amplía. No hace falta pedirla nuevamente si el bloque pertenece a la
secuencia aprobada y todas las puertas anteriores están verdes. Sólo creá una
tarea visible e independiente si Joaquin lo pide expresamente.

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

MAIN espera el handoff. Puede realizar inspecciones de sólo lectura en paralelo,
pero no abre otra dependencia implementadora ni modifica los mismos archivos
mientras la tarea está trabajando.

Si la herramienta de colaboración interna no está disponible o falla, no
simules la creación. Conservá el prompt habilitado, registrá `PENDIENTE` y
explicá la limitación.

Si un turno termina antes de cerrar el bloque, dejá en `docs/STATUS.md` o en un
handoff durable, según corresponda, el identificador de la tarea, prompt, base,
HEAD, archivos pendientes, última validación real y próxima acción exacta. Al
reanudar, recuperá ese estado antes de crear nada nuevo.

## 11. Handoff mínimo que MAIN debe exigir

La dependencia devuelve:

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
6. comprueba migraciones, esquemas e historia cuando el bloque sí autoriza
   persistencia;
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
- Room: prueba de migración desde la versión inmediata y cadena histórica,
  esquema exportado, reapertura y datos representativos;
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
- qué archivos o capas cambiaron;
- qué se probó realmente;
- cuál es el commit local;
- qué riesgo queda;
- cuál es la próxima dependencia que va a crear.

Luego comienza el próximo ciclo sin volver a preguntar decisiones ya cerradas.

## 15. Condiciones de parada obligatoria

MAIN detiene la cadena y no abre la dependencia siguiente si ocurre cualquiera
de estas condiciones:

- ruta, rama, HEAD, base o upstream no coinciden con lo esperado;
- existen cambios locales sin dueño o no se puede aislar el bloque;
- falta un handoff verificable y tampoco existe un diff atribuible con evidencia
  suficiente para una auditoría independiente;
- fallan pruebas, lint, empaquetado, instrumentación requerida o una migración;
- Room cambia sin esquema, migración y pruebas históricas;
- falta QA física requerida por el comportamiento modificado;
- el mismo defecto persiste después de dos rondas de corrección sin evidencia
  nueva;
- una dependencia alteró contratos compartidos fuera de su autorización;
- existe una contradicción funcional o una decisión material abierta;
- haría falta agregar una dependencia de producción o servicio externo no
  autorizado;
- haría falta otro push, tocar `main`, cambiar versión, crear tag o Release,
  publicar o actuar sobre producción;
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
- V1 sigue intacta y una actualización representativa conserva su historia;
- las cuatro experiencias sectoriales respetan sus contratos y no se
  homologaron por analogía;
- Calendario, Resumen, horas, disponibilidad, situaciones especiales, próximo
  evento y notificaciones recorren sus estados principales y de error;
- la segunda capa incluida por Joaquin está cerrada o expresamente diferida;
- la batería global, lint, ensamblado, instrumentación y matriz Android exigida
  están documentados con resultados reales;
- privacidad, secretos, permisos, logs y contenido prohibido fueron auditados;
- no quedan placeholders accidentales, migraciones destructivas ni datos
  reales;
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

## 17. Instrucción inmediata

Empezá ahora desde Puerta 0. Si continúa presente el candidato de **Elegir días
y cargar jornadas desde horarios guardados**, auditá e integrá ese resultado
antes de crear otra tarea. Cuando quede verde, escribí y habilitá el prompt de
**Activar MiGuardia 2.0 desde una instalación anterior y cambiar de rubro desde
una fecha**, creá una sola dependencia para ejecutarlo y repetí este ciclo hasta
alcanzar la definición de candidato local completo o una condición de parada.
