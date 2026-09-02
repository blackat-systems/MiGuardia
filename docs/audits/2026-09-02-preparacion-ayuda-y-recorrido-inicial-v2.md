# Preparación de Ayuda y recorrido inicial V2

- Fecha: 2026-09-02
- Rol: MAIN 2.0
- Tipo: auditoría documental y preparación de dependencia
- Base funcional:
  `b64f07a6a92ad16f789eceb395c469239ee46eb4`

## Objetivo

Convertir el diseño histórico de onboarding V1 en un contrato ejecutable para
la interfaz V2 definitiva, sin abrir todavía una tarea implementadora ni
modificar código.

## Puerta 0

Verificado antes de editar:

- ruta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD: `b64f07a6a92ad16f789eceb395c469239ee46eb4`;
- upstream: `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- divergencia inicial: 16 adelante, 0 detrás;
- checkout inicial limpio;
- `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- remoto: `https://github.com/blackat-systems/MiGuardia.git`;
- nueve worktrees registrados y preservados.

## Fuentes revisadas

MAIN leyó las fuentes rectoras en el orden de `AGENTS.md`, el coordinador
secuencial, el prompt histórico V1, ADR 0014, ADR 0015, ADR 0024, ADR 0035,
ADR 0036, el contrato de primera apertura V2 y el código y pruebas actuales de:

- arranque y recuperación;
- Bloqueo de acceso;
- selector de rubro y `WorkSetup`;
- menú lateral y navegación raíz;
- Calendario, Resumen y destinos directos;
- DataStore y preferencias portables de Copias;
- fixtures Compose y Activity.

Dos revisiones independientes de sólo lectura contrastaron el producto y el
código. No modificaron archivos ni usaron dispositivos.

## Estado real encontrado

- La primera pantalla productiva de `FreshInstall` es
  `¿En qué rubro trabajás?` con cuatro opciones exactas y acceso a
  `Restaurar una copia existente`.
- `V2NeedsFirstSet` ya guía la creación del primer lugar, tipo y horario.
- `V2Ready` abre la navegación principal.
- Recuperación de Copias y Bloqueo se resuelven antes de componer
  `MiGuardiaApp`.
- No existe todavía una superficie general Ayuda, un recorrido inicial ni
  `onboarding.preferences_pb`.
- El menú real usa `Calendario`, `Resumen`, `Mi forma de trabajar`, Feriados,
  Vacaciones, Notificaciones, Clima, Widget, Copias, Bloqueo y Apariencia.
- El formato de Copias exporta exactamente diecisiete preferencias semánticas.

## Resolución del contrato histórico

Se descartaron del diseño V1:

- onboarding exclusivo para vigiladores;
- Perfil laboral, Objetivos y horarios y combinaciones V1;
- `Cargar datos`, `Guardia/Francos`, `Editar calendario` y navegación inferior;
- una pantalla contenedora Configuración;
- `MIGRATED_V1`, activación o migración de datos 1.0;
- una primera carga paralela a `WorkSetup`;
- conteos antiguos de Room y la exclusión de funciones V2 que hoy ya existen.

ADR 0037 fija la adaptación:

1. recuperación;
2. Bloqueo;
3. rubro;
4. primer conjunto laboral real;
5. pantalla real de finalización y altas adicionales elegidas;
6. guía versionada después de volver al Calendario, sólo con `V2Ready` y
   `WorkSetupSurface.NONE`;
7. Calendario normal.

La guía explica tres ideas breves y recorre controles reales sin escribir datos
ni pedir permisos. `Ayuda` queda como destino permanente y permite repetir la
guía.

## Persistencia y privacidad

La única persistencia nueva autorizada es:

- DataStore `onboarding.preferences_pb`;
- clave entera `completed_version`;
- versión inicial 1;
- actualización atómica y monotónica;
- error seguro y reintentable.

La marca es no portable. No cambia el contenedor `.miguardia-backup`, sus
diecisiete preferencias, Room, Bloqueo ni la recuperación temprana.

## Archivos documentales

Nuevos:

- `docs/prompts/AYUDA_Y_RECORRIDO_INICIAL_V2.md`;
- `docs/adr/0037-ayuda-y-recorrido-inicial-versionado-v2.md`;
- este informe.

Actualizados:

- `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
- `docs/STATUS.md`;
- `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
- `docs/prompts/README.md`;
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
- `docs/prompts/ONBOARDING_Y_PRIMERA_CARGA.md`;
- `docs/adr/0014-onboarding-local-versionado-y-primera-carga-guiada.md`.

## Límites preservados

- cero cambios en `app/**` o `core/**`;
- cero cambios en Room, DataStore productivo, esquemas o migraciones;
- cero cambios en Gradle, manifiesto, permisos, dependencia, paquete, versión o
  SDK;
- cero uso de Samsung, ADB o emuladores;
- cero commit de código, push, tag, Release, merge, rebase o acción sobre
  producción;
- no se creó otra tarea.

## Validación documental

- Puerta 0 viva: verificada;
- jerarquía de prompts: un único bloque de implementación `HABILITADO`;
- referencias históricas V1 rotuladas como no ejecutables;
- decisiones de selector, Copias, Bloqueo y Room coherentes;
- segunda revisión independiente: corregido el gating para no tapar
  `WorkSetupSurface.COMPLETION`;
- rutas documentales referenciadas: existentes;
- formato de los diez documentos: sin espacios finales y con salto final;
- `git diff --check`: correcto;
- `git diff --cached --check`: correcto sobre los diez documentos exactos;
- Gradle y ADB: no corresponden a una preparación exclusivamente documental.

## Próximo paso

El conjunto quedó apto para un checkpoint local `docs:`. La tarea especialista
se abre sólo si Joaquin la crea o pide expresamente que MAIN lo haga. No existe
autorización vigente de push.
