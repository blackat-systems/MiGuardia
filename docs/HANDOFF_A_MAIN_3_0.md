# HANDOFF A MAIN — Inicialización de MiGuardia 3.0

## QUÉ RESUELVE

Este documento permite abrir un proyecto nuevo de ChatGPT/Codex y asumir el rol
MAIN de MiGuardia 3.0 sin reconstruir el historial del chat, confundir los dos
repositorios ni empezar una reescritura improvisada.

La primera dependencia de 3.0 es una etapa integral de descubrimiento y diseño.
Debe convertir la aplicación 2.0.2 realmente existente, las funciones pendientes
y las nuevas decisiones de Joaquín en un alcance 3.0 aceptable, un grafo de
dependencias y contratos implementables.

No autoriza todavía cambios de producto.

## ESTADO RECIBIDO

- Contenedor del proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`.
- Repositorio operativo: `MiGuardia-3.0\`.
- Rama: `codex/miguardia-3.0`.
- HEAD limpio anterior a este handoff:
  `cb83d85552683a0f47f0f4f14f4f43a326fa76a1`.
- Remoto público 3.0: `https://github.com/blackat-systems/MiGuardia.git`.
- `main`: `3f1365259e15292a2f24e3361acd523213b49fad`.
- Procedencia funcional: snapshot
  `a771d78e3ec33bd2d3ef02bd1ae310514359e004` del repositorio 2.x, importado
  como raíz nueva sin arrastrar su historial Git.
- Referencia estable: `MiGuardia-2.0\`, repositorio 2.x archivado y de sólo
  lectura salvo una corrección 2.x autorizada expresamente.
- Repositorio histórico remoto:
  `https://github.com/blackat-systems/MiGuardia-2.x-archive`.
- Recuperaciones históricas: `Repositorio-Git\` y `Archivo-recuperacion\`;
  no son fuentes para reconstruir 3.0 por inferencia.

Producto público de referencia:

- MiGuardia 2.0.2, `versionCode 6`.
- `applicationId = com.blackatsystems.miguardia`.
- Android local-first, Kotlin y Compose.
- Room V6, 27 tablas, seis esquemas y Copias con 17 preferencias portables.
- APK pública firmada con clave permanente externa a Git.
- Actualización pública consciente y manual.
- Sin activación beta, cupos, cuenta laboral, nube laboral, analítica,
  telemetría ni feedback obligatorio.

## PUERTA 0 OBLIGATORIA

El nuevo MAIN debe empezar verificando, sin modificar:

1. Ruta real del contenedor y del repositorio 3.0.
2. Rama, HEAD, upstream, remotos, staging, modificados y no rastreados.
3. Que `MiGuardia-3.0\.git` sea un directorio propio y no un archivo de
   worktree compartido.
4. Que `MiGuardiaContainer` no sea un repositorio.
5. Que `MiGuardia-2.0` pertenezca al repositorio archivado y permanezca limpio.
6. Que el único delta esperado desde `cb83d855...` sea la preparación de este
   handoff: este archivo, su enlace en `README.md` y su enlace en `STATUS.md`.
7. Que no haya APK, claves, almacenes de firma, copias reales, datos del
   teléfono ni evidencia privada dentro del árbol rastreado.

Ante una diferencia adicional sin dueño, preservarla, describirla y detener la
escritura hasta determinar su procedencia. No limpiar ni reconstruir el checkout.

## TASK

Ejecutar la dependencia `DESCUBRIMIENTO_Y_ARQUITECTURA_MIGUARDIA_3` de punta a
punta y devolver a Joaquín un plan 3.0 listo para decidir.

La dependencia debe:

1. Inventariar lo que MiGuardia 2.0.2 hace realmente, tomando código y pruebas
   como evidencia y documentación vigente como contrato.
2. Identificar capacidades trabajadas pero no implementadas, defectos conocidos,
   deuda accidental, decisiones obsoletas y deseos nuevos de Joaquín.
3. Clasificar cada elemento como `CONSERVAR`, `CORREGIR`, `AGREGAR`, `RETIRAR`,
   `INVESTIGAR` o `DIFERIR`, con razón y evidencia.
4. Separar necesidades de producto, UX, dominio, persistencia, Android/sistema,
   privacidad, seguridad, distribución, website y operación.
5. Detectar contradicciones entre planificación 2.0, ADR, código, pruebas,
   publicación real y expectativas actuales. No resolverlas silenciosamente.
6. Definir los invariantes de compatibilidad 3.0: actualización desde 2.0.2,
   preservación de datos, `applicationId`, certificado, Room/migraciones,
   Copias, privacidad y funcionamiento offline.
7. Proponer la arquitectura objetivo sólo hasta el nivel necesario para tomar
   decisiones; evitar una reescritura o dependencia nueva sin beneficio probado.
8. Construir un grafo de dependencias y una ruta de etapas 3.0. Las unidades
   deben ser capacidades completas, no botones o microtareas.
9. Marcar qué frentes pueden delegarse en chats separados, qué contrato deben
   compartir y qué archivos no pueden tener dos escritores.
10. Preparar el contrato autosuficiente de la primera dependencia de
    implementación, pero no ejecutarla hasta que Joaquín acepte el plan.

## CONTEXT

MiGuardia 3.0 no parte de una app vacía ni habilita una reescritura automática.
Parte de la versión 2.0.2 estable y debe justificar cada diferencia.

La documentación 2.0 continúa siendo la fuente vigente de las capacidades que
se conservan mientras no exista un sucesor 3.0 aceptado. Los documentos
archivados sirven como evidencia histórica, no como instrucciones nuevas.

La ruta de 24 etapas en `docs/ORQUESTACION_MAIN.md` describe la construcción y
entrega de 2.0. Para 3.0 debe conservarse como historia hasta que el
descubrimiento produzca y Joaquín acepte su sucesora. No renumerarla ni
reescribirla durante el inventario inicial.

## INPUTS Y ORDEN DE LECTURA

Lectura inicial mínima:

1. `MiGuardiaContainer\AGENTS.md`.
2. `MiGuardia-3.0\AGENTS.md`.
3. `MiGuardia-3.0\docs\STATUS.md`.
4. Este handoff.
5. `docs/LECTURAS_POR_TAREA.md`.
6. `docs/WORKFLOW.md`.
7. `docs/ORQUESTACION_MAIN.md`, sólo para entender la ruta cerrada 2.0 y sus
   puertas de entrega.
8. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md` y las secciones pertinentes de
   `docs/PLANIFICACION_MIGUARDIA_2_0.md`.
9. ADR vigentes, código y pruebas únicamente según el mapa de lectura que MAIN
   construya; no abrir todo el archivo histórico por rutina.

Joaquín debe aportar durante el descubrimiento la lista de ajustes, funciones
faltantes y nuevas ideas que quiere evaluar. MAIN debe ordenarlas y detectar
dependencias; no convertir cada observación aislada en una dependencia nueva.

## OUTPUT

La dependencia debe entregar:

1. Un inventario verificable de capacidades actuales y huecos.
2. Una tabla de decisiones propuestas con estado y justificación.
3. Invariantes y criterios de aceptación transversales de 3.0.
4. Un grafo de dependencias y una secuencia de etapas coherentes.
5. Fronteras concretas para especialistas, con productores, consumidores y
   archivos compartidos.
6. Riesgos, decisiones materiales pendientes y alternativas —máximo tres por
   decisión— con recomendación.
7. Una propuesta de documentación canónica 3.0 que reemplace gradualmente, sin
   duplicar por ritual, las fuentes 2.0.
8. Un prompt autosuficiente para la primera dependencia implementable.
9. Un `HANDOFF A MAIN` compacto con evidencia, límites y próximo paso.

Después de la aceptación de Joaquín, MAIN podrá crear o actualizar las fuentes
canónicas 3.0, mover el cursor de STATUS y despachar implementación.

## SCOPE

Durante descubrimiento:

- Lectura de todo el repositorio 3.0 según necesidad.
- Lectura comparativa de `MiGuardia-2.0` y evidencia histórica sólo cuando
  resuelva una duda concreta.
- Escritura limitada a documentación de descubrimiento expresamente aceptada y
  a `docs/STATUS.md` cuando corresponda registrar una decisión real.
- Código de producción, pruebas ejecutables, Gradle, manifiestos, Room y
  esquemas permanecen de sólo lectura.

## DEPENDENCIES

- Handoff de separación de repositorios completado.
- Base 2.0.2 preservada.
- Repositorio 3.0 independiente y sincronizado.
- No depende de teléfono, firma, website ni servicios remotos.

## DO NOT

- No reimplementar 2.0 ni copiar indiscriminadamente desde el repositorio
  archivado.
- No iniciar una reescritura, cambiar arquitectura o añadir dependencias antes
  de aprobar el diagnóstico.
- No cambiar `versionName`, `versionCode`, `applicationId`, firma, Room,
  migraciones, formato de Copias, permisos o comportamiento público.
- No reactivar beta privada, claves, cupos ni feedback obligatorio.
- No usar Samsung, ADB, emuladores, firma, APK, GitHub Release, website o
  producción.
- No crear ramas, worktrees, tareas, commits, pushes o tags por inferencia.
- No tratar archivos archivados, handoffs antiguos o resultados históricos como
  evidencia fresca.
- No dividir un mismo recorrido funcional en una cadena de microdependencias.

## VALIDATION

Para cerrar el descubrimiento:

- Puerta 0 documentada y sin diferencias inexplicables.
- Cada afirmación importante enlazada a código, prueba, documento vigente o
  evidencia real.
- Contradicciones separadas de defectos comprobados y de mejoras opcionales.
- Grafo sin dependencias circulares ni dos escritores simultáneos sobre los
  mismos archivos.
- Primera dependencia implementable con contrato `TASK / CONTEXT / INPUTS /
  OUTPUT / SCOPE / DEPENDENCIES / DO NOT / VALIDATION / DONE WHEN`.
- Documentación modificada pasa `python tools/docs_check.py` y
  `git diff --check`.
- Diff final exclusivamente documental y revisado.

No corresponde ejecutar Gradle, instrumentación ni QA física si no cambió
código o contrato ejecutable.

## DONE WHEN

La inicialización queda completa cuando Joaquín recibe y acepta:

- el alcance propuesto de MiGuardia 3.0;
- sus invariantes y decisiones materiales;
- el grafo y orden de dependencias;
- la primera dependencia lista para delegar o ejecutar;
- el estado Git y los límites pendientes claramente registrados.

Aceptar este handoff inicia MAIN; no implica aceptar todavía el plan que MAIN
produzca ni autoriza su primera implementación.

## PROMPT COPIABLE PARA EL NUEVO MAIN

```text
Continuá MiGuardia como MAIN de la versión 3.0.

Fuente del proyecto de ChatGPT/Codex:
C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer

Repositorio operativo:
C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0

Leé primero, en este orden:
1. MiGuardiaContainer\AGENTS.md
2. MiGuardia-3.0\AGENTS.md
3. MiGuardia-3.0\docs\STATUS.md
4. MiGuardia-3.0\docs\HANDOFF_A_MAIN_3_0.md

Asumí el rol coordinador MAIN y ejecutá la Puerta 0 exacta del handoff. La base
esperada es la rama codex/miguardia-3.0 en
cb83d85552683a0f47f0f4f14f4f43a326fa76a1, más únicamente el delta documental
que incorpora este handoff. MiGuardia-3.0 es un repositorio independiente;
MiGuardia-2.0 y Repositorio-Git pertenecen al archivo 2.x y son sólo lectura.

Tu única dependencia inicial es DESCUBRIMIENTO_Y_ARQUITECTURA_MIGUARDIA_3.
Inventariá la aplicación 2.0.2 realmente existente, funciones trabajadas pero no
implementadas, ajustes deseados, deuda y contradicciones. Clasificá qué conservar,
corregir, agregar, retirar, investigar o diferir. Definí invariantes de
compatibilidad, grafo de dependencias, etapas cohesivas y fronteras de
especialistas. Prepará el prompt completo de la primera dependencia de
implementación.

No modifiques producto, pruebas ejecutables, Gradle, manifiestos, Room, esquemas,
versionado, applicationId, firma, web ni distribución. No uses dispositivos ni
servicios remotos. No crees tareas, ramas, worktrees, commits o pushes sin una
instrucción posterior de Joaquín.

Trabajá con autonomía dentro del descubrimiento. No abras microtareas ni preguntes
decisiones menores. Detenete únicamente ante una diferencia de entrada sin dueño
o una decisión material de producto, arquitectura, privacidad, seguridad o
compatibilidad que necesite a Joaquín.

Entregá un HANDOFF A MAIN con inventario, decisiones propuestas, evidencia,
riesgos, grafo, dependencias y el próximo prompt listo para usar. No comiences la
implementación antes de que Joaquín acepte ese plan.
```
