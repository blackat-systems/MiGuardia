# Primera apertura y configuración laboral visible V2

- Estado: **CERRADO — INTEGRADO EN `1f048643`**
- Fecha: 2026-08-22
- Rama obligatoria: `codex/miguardia-2.0`
- Proyecto obligatorio: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Dependencia cerrada: Corte A de catálogo laboral y Room v7
- Nombre humano: **Elegir el rubro y preparar el primer lugar de trabajo**

> Actualización 2026-08-23: la primera apertura V2 y su configuración visible
> permanecen vigentes. ADR 0024 reemplaza únicamente el recorrido especial para
> una raíz `MIGRATED_V1`, que debe retirarse en un bloque posterior.

## ROL

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, la arquitectura general, Room, el Calendario ni el orden
de la hoja de ruta.

Trabajá directamente en el proyecto y la rama existentes. No crees otro
proyecto, rama o worktree. MAIN conservará la integración final.

Antes de modificar:

1. ejecutá Puerta 0 de solo lectura;
2. leé completamente y en el orden de `AGENTS.md` las fuentes rectoras;
3. confirmá que el árbol parte limpio del HEAD indicado por MAIN;
4. inspeccioná el código y las pruebas de inicio, navegación, Perfil,
   Calendario, configuración laboral y catálogo Room v7;
5. detenete ante un mismatch real y no descartes ningún cambio.

## TASK

Construir el recorrido visible que comienza la primera vez que se abre una
instalación nueva de MiGuardia 2.0:

1. antes de mostrar el Calendario, preguntar en qué rubro trabaja la persona;
2. ofrecer exactamente cuatro opciones: Vigilancia privada, Policía,
   Enfermería y Medicina;
3. no permitir continuar sin elegir una opción;
4. guardar esa elección de manera segura y consciente;
5. abrir después el Calendario vacío;
6. desde el Calendario, guiar la creación del primer lugar de trabajo, sus
   reglas básicas, el tipo de trabajo habitual y su primer horario reutilizable;
7. permitir volver al Calendario o seguir agregando horarios o lugares.

Este bloque crea la configuración visible. No conecta todavía las plantillas
V2 con la carga manual de jornadas en la grilla; esa integración pertenece a
la dependencia siguiente y será coordinada por MAIN.

## EXPERIENCIA ESPERADA

### 1. Mientras se determina el estado

- Mostrar una espera neutral.
- No mostrar el selector ni habilitar escrituras hasta conocer el estado real.
- Si la lectura falla, mostrar un mensaje simple y `Reintentar`.
- Un error nunca puede interpretarse como una instalación nueva.

### 2. Primera pantalla de una instalación nueva

La primera decisión visible es el rubro laboral. La pantalla debe ser simple y
explicar que esta elección adapta palabras y ejemplos, pero que los horarios y
reglas se completan después.

Opciones exactas:

- Vigilancia privada;
- Policía;
- Enfermería;
- Medicina.

Enfermería y Medicina son tarjetas independientes. No existe `Salud`, `Otro`
ni una opción genérica.

La persona selecciona una tarjeta y confirma con una acción clara como
`Continuar`. La selección visual por sí sola no escribe datos. `Continuar`
permanece deshabilitado hasta elegir un rubro. Durante el guardado se evita el
doble toque y se muestra progreso sin perder la selección.

Al confirmar:

- crear la única raíz `NEW_V2` y su primera revisión desde la fecha local
  actual;
- usar el sector elegido;
- conservar `HoursReference.PendingSetup`;
- conservar disponibilidad sin configurar;
- no inventar 204 horas, horario nocturno ni reglas por profesión;
- usar reloj y UUID inyectables;
- manejar errores sin dejar una raíz o revisión parcial.

Después de guardar correctamente, abrir el Calendario vacío. No volver a
mostrar automáticamente el selector al cerrar y abrir la aplicación.

### 3. Instalación actualizada desde MiGuardia 1.0

Una raíz `MIGRATED_V1` sin activación V2 nunca muestra el selector bloqueante al
abrir. Entra al recorrido heredado como hasta ahora.

La activación consciente `Configurar MiGuardia 2.0` y los cambios futuros de
sector quedan visibles desde `Mi forma de trabajar`, pero este especialista no
debe forzar ni convertir datos V1. Si esa superficie no puede cerrarse sin
ampliar el alcance, implementar sólo el acceso y el estado explicativo, y dejar
la mutación futura claramente pendiente para MAIN; nunca simularla.

### 4. Calendario V2 todavía sin lugar

Después de elegir el rubro, el Calendario permanece utilizable y vacío. Debe
mostrar:

`Todavía no cargaste ningún lugar de trabajo`.

La acción principal abre la creación del primer lugar. No preselecciona fechas,
no crea una jornada y no muestra un segundo Calendario.

### 5. Primer lugar y primer horario

La creación se divide en dos pasos breves, con borrador recuperable durante la
recreación de la pantalla.

#### Paso A — Lugar y reglas

- nombre obligatorio;
- abreviatura nueva de tres a cinco caracteres, en mayúsculas;
- dirección opcional;
- nota personal opcional y privada;
- pregunta simple: `¿En este lugar contás horas nocturnas?`;
- si responde sí, inicio y final exactos;
- opciones no monetarias para sábado, domingo y feriado;
- explicar que estas opciones sólo clasifican horas para mostrarlas aparte.

Usar el vocabulario sugerido para el sector sin cambiar el concepto común de
lugar de trabajo.

#### Paso B — Tipo y horario

- nombre del tipo editable y obligatorio;
- sugerencia inicial:
  - `Guardia habitual` para Vigilancia privada y Policía;
  - `Turno habitual` para Enfermería;
  - `Jornada habitual` para Medicina;
- hora exacta de inicio;
- hora exacta de finalización;
- color obligatorio;
- inicio igual a final se explica como 24 horas;
- cuando cruza medianoche se explica `termina al día siguiente`.

El nombre visible nunca decide si una actividad es extra. Aclarar con palabras
simples que este registro cuenta como trabajo normal y que extras,
disponibilidad y situaciones especiales se cargarán más adelante.

La confirmación usa `WorkCatalogRepository.createFirstWorkSet(...)` y debe ser
atómica. Si falla, conserva el borrador y no deja un lugar incompleto.

Después del guardado se ofrecen exactamente:

- `Volver al Calendario`;
- `Agregar otro horario`;
- `Agregar otro lugar`.

## ESTADO Y COMPONENTES

Crear una superficie cohesiva bajo un paquete nuevo y claro, preferentemente
`app/.../ui/worksetup/`, con:

- `WorkSetupViewModel`;
- `WorkSetupUiState` y borradores explícitos;
- pantallas Compose de selección, error, primer lugar y finalización;
- efectos de navegación de una sola emisión;
- `SavedStateHandle` o mecanismo equivalente para preservar borradores que aún
  no fueron confirmados;
- mensajes cotidianos y acciones de reintento.

Reutilizar:

- `WorkSetupState` y `projectLoadedWorkSetupState(...)`;
- `WorkConfigurationRepository`;
- `WorkCatalogRepository`;
- `WorkConfiguration`, `FirstWorkSet`, `WorkplaceRules` y vocabulario sectorial
  ya existentes;
- `LocalDataStore.workConfiguration` y `LocalDataStore.workCatalog`;
- tema Vigilia, zoom interno y patrones visuales existentes.

No dupliques reglas ya disponibles en dominio ni accedas directamente a DAO.

## INTEGRACIÓN PERMITIDA

Se permite el cableado mínimo necesario en:

- `MiGuardiaApplication.kt`;
- `MainActivity.kt`;
- `MiGuardiaApp.kt`;
- navegación y menú lateral existentes;
- recursos de texto y test tags;
- pruebas JVM e instrumentadas del recorrido.

La aplicación debe decidir qué mostrar según `WorkSetupState`:

- `Loading`: espera;
- `LoadError`: error y reintento;
- `FreshInstall`: selector obligatorio;
- `LegacyV1` o activación futura todavía no vigente: recorrido V1;
- `V2NeedsFirstSet`: Calendario con guía para crear el primer conjunto;
- `V2Ready`: recorrido habitual y acceso a `Mi forma de trabajar`.

No conviertas `hasAnyShifts` ni una base vacía en fuente de verdad del inicio.

## SCOPE

Se permite modificar solamente:

- `app/src/main/**` para el nuevo recorrido y su cableado imprescindible;
- `app/src/test/**`;
- `app/src/androidTest/**`;
- textos y recursos estrictamente necesarios;
- este prompt, `docs/STATUS.md` y una auditoría propia únicamente para reflejar
  el resultado real.

Si encontrás que falta un contrato público imprescindible en `core/domain`, no
lo cambies silenciosamente: documentá el bloqueo y devolvelo a MAIN. Room v7 y
sus repositorios son entradas cerradas de esta dependencia.

## DO NOT

- no asumir el rol de MAIN;
- no crear otro proyecto, rama o worktree;
- no modificar `core/domain`, `core/database`, esquemas ni migraciones;
- no cambiar Gradle, manifiesto, permisos, `applicationId`, versión o SDK;
- no implementar carga manual V2 de jornadas en `ManagementViewModel`;
- no implementar recurrencias, horario real, extras, cumplimiento,
  disponibilidad, situaciones especiales ni Resumen V2;
- no adaptar próximo evento, notificaciones, clima, widgets o informes;
- no agrupar Enfermería y Medicina;
- no crear `Salud` ni `Otro`;
- no imponer horarios, referencia de horas o nocturnidad por sector;
- no mostrar montos, salarios, convenios ni liquidaciones;
- no agregar cuentas, red, nube, ubicación, OCR, telemetría ni datos clínicos;
- no usar datos reales;
- no tocar la aplicación productiva del Samsung;
- no hacer commit, push, tag, merge, rebase, reset ni descartar cambios.

## VALIDATION

### JVM

Probar como mínimo:

1. carga inicial y error con reintento;
2. error no se proyecta como instalación nueva;
3. las cuatro opciones exactas y ninguna quinta;
4. `Continuar` deshabilitado sin selección;
5. doble toque no crea dos raíces;
6. elección correcta crea `NEW_V2` con `PendingSetup` y sin valores inventados;
7. error de guardado conserva selección y permite reintentar;
8. raíz V1 no muestra selector bloqueante;
9. `V2NeedsFirstSet` abre la guía sin crear jornadas;
10. validaciones del primer lugar y del primer horario;
11. guardado atómico y conservación del borrador ante error;
12. navegación posterior entre las tres acciones acordadas;
13. recreación conserva un borrador no confirmado sin repetir eventos.

### Compose e instrumentación

Con datos ficticios:

1. primera apertura muestra el selector antes que el Calendario;
2. Vigilancia privada, Policía, Enfermería y Medicina aparecen separadas;
3. claro y oscuro son legibles;
4. retrato y paisaje conservan todas las acciones;
5. zoom interno 100 %, 150 % y 200 % mantiene el contenido alcanzable;
6. selección, errores y progreso no dependen sólo del color;
7. Atrás y edición sin guardar no pierden datos silenciosamente;
8. tras elegir sector se llega al Calendario vacío;
9. el primer conjunto puede completarse y persiste al cerrar y reabrir;
10. una base V1 entra sin bloqueo.

Ejecutar al menos:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQaAndroidTest
```

Compilar la instrumentación Compose afectada. La ejecución física se coordina
con MAIN y sólo puede usar paquetes QA y datos ficticios. Distinguir siempre
compilación de AndroidTest, instrumentación ejecutada y revisión manual.

## HANDOFF A MAIN

Devolver un informe compacto con:

- objetivo realizado;
- archivos modificados;
- decisiones menores tomadas;
- pruebas exactas y conteos;
- qué fue compilado y qué se ejecutó realmente;
- riesgos o puntos de integración;
- estado Git y confirmación de que no hubo commit ni push;
- próximos pasos que quedan exclusivamente para MAIN.

No declares integrado ni terminado el Corte B completo. MAIN revisará el diff,
conectará las piezas transversales que correspondan, repetirá las pruebas y
decidirá el checkpoint.

## DONE WHEN

La dependencia está lista para entregar a MAIN solamente cuando:

- una instalación nueva ve primero el selector de rubro;
- sólo puede elegir uno de los cuatro sectores confirmados;
- la elección persiste sin valores laborales inventados;
- después puede entrar al Calendario vacío y crear el primer conjunto completo;
- una instalación V1 no queda bloqueada;
- los borradores y errores son recuperables;
- el recorrido respeta Vigilia y el zoom interno;
- las pruebas proporcionales pasan;
- no se modificaron Room, dominio, permisos ni módulos fuera del alcance;
- el diff queda sin commit y sin push para auditoría de MAIN.
