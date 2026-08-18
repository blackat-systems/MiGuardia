# MAIN — coordinación de experiencia inicial y Perfil laboral

> Estado: traspaso vigente para MAIN
>
> Fecha: 2026-08-17
>
> Alcance: MiGuardia para vigiladores
>
> Reanudación actualizada el 18 de agosto de 2026: Calendario ya está integrado y `main` es la base canónica. La Puerta 0 detectó que el contrato de ocultar/restaurar Notificaciones quedó parcialmente implementado. Se cerrará ahora esa brecha acotada y recién luego se iniciará Perfil; no se abre una nueva decisión funcional ni se amplía el alcance.

## 0. Rol y misión

MAIN conserva la visión integral de MiGuardia y organiza estas decisiones mediante dependencias especializadas secuenciales. No implementa todo junto ni abre worktrees paralelos que compartan navegación, estado raíz o superficies Compose.

Antes de cada dependencia debe:

1. consolidar un MAIN limpio, verificable y con las decisiones canónicas vigentes;
2. guardar un prompt autosuficiente en `docs/prompts/`;
3. crear la rama y el worktree desde el `HEAD` actualizado de MAIN;
4. registrar el SHA exacto de arranque;
5. auditar e integrar la entrega antes de derivar la siguiente.

Joa autoriza crear estas dependencias. La fragmentación debe reducir conflictos y riesgo, no sumar ceremonia.

## 1. Lectura, autoridad y protección

Antes de planificar o editar:

1. leer `AGENTS.md` y `docs/PROMPT_MAESTRO_MAIN.md` completos;
2. releer ADR y prompts de Calendario, Objetivos y guardias, Pulido visual, Notificaciones y Clima según el módulo;
3. inspeccionar código, pruebas, Git, diffs y archivos no rastreados;
4. preservar trabajo ajeno y datos productivos.

La instrucción explícita actual de Joa y el prompt maestro prevalecen sobre prompts históricos. No usar `reset --hard`, limpieza destructiva, push forzado ni reescritura de historia.

Un worktree creado desde un commit viejo no recibe cambios sin confirmar. No derivar trabajo hasta alcanzar un commit base identificable y un estado limpio.

## 2. Alcance congelado

Este bloque termina la experiencia para vigiladores mediante:

1. Calendario con consulta y edición explícita;
2. Perfil laboral del vigilador y organización de Configuración;
3. Notificaciones con presentación Vigilia y control de visibilidad;
4. bienvenida, onboarding y primera carga guiada.

Quedan fuera:

- salud, policía, medicina, bomberos u otras profesiones;
- login, cuentas, backend, nube o sincronización;
- compras, suscripciones, Premium o bloqueo de funciones;
- reglas salariales todavía abiertas;
- rediseños generales fuera de estas superficies.

La comercialización es futura. No crear todavía dependencias de pagos.

## 3. Dependencia 1 — Calendario: consulta y edición

Prompt: `docs/prompts/CALENDARIO_MODO_CONSULTA_Y_EDICION.md`.

Rama sugerida: `codex/calendar-edit-mode`.

Debe reutilizar el calendario, la proyección y las fuentes actuales. Abre en consulta; permite navegar, ver Hoy, fotos, clima y detalles informativos, pero no mutar. La entrada inferior es `Editar calendario`; si todavía no hay cargas puede aparecer `Cargar mi primera guardia`.

El modo edición conserva mes y posición, se identifica con `Editando calendario`, expone sólo las mutaciones vigentes y cambia la acción inferior a `Terminar`. Atrás sale primero de edición y los formularios existentes conservan su protección de cambios sin confirmar.

No reintroducir Vacaciones desde Calendario, duplicación ni limpieza general. Vacaciones sigue en Configuración. El detalle en consulta no muta; el detalle en edición conserva las acciones aprobadas y la eliminación confirmada.

Pruebas mínimas:

- consulta no invoca mutaciones;
- entrada y salida conservan mes, fecha y desplazamiento pertinente;
- vacío conduce a la primera carga mediante el flujo real;
- Atrás y `Terminar` son seguros;
- carga simple/múltiple, franco, segunda guardia, edición, eliminación, clima y detalles no regresan;
- Vigilia clara/oscura, paisaje y zoom interno 100/150/200 %;
- semántica accesible básica, sin activar ni declarar una auditoría específica de TalkBack y sin consultar ajustes visuales del sistema.

MAIN debe auditar e integrar esta entrega antes de crear la segunda dependencia.

## 4. Dependencia 2 — Perfil laboral y Configuración

Prompt: `docs/prompts/PERFIL_LABORAL_Y_CONFIGURACION.md`.

Rama sugerida: `codex/guard-profile-settings`.

Debe nacer del `HEAD` posterior a Calendario e implementar un perfil local, no una cuenta:

- nombre o apodo opcional;
- profesión visible y fija `Vigilancia y seguridad`;
- empresa inicial `Inforce`, editable y persistida;
- objetivos y horarios activos como proyecciones de repositorios existentes, sin duplicarlos;
- Puesto asociado a cada carga;
- Configuración organizada sin repetir Perfil, Objetivos, Notificaciones, Clima, Apariencia, Privacidad u otros valores;
- fuente local única para que informes futuros usen la empresa configurada;
- sin DNI, email, teléfono, domicilio ni identificadores innecesarios;
- editar Perfil no reescribe instantáneas históricas.

MAIN debe decidir y documentar DataStore o Room. Toda persistencia requiere estrategia no destructiva y pruebas de valor inicial, edición, reapertura, nombre vacío, proyecciones sin duplicados e historia intacta.

## 5. Dependencia 3 — Notificaciones Vigilia y control de visibilidad

Prompt a preparar después de integrar Perfil: `docs/prompts/NOTIFICACIONES_VIGILIA_Y_VISIBILIDAD.md`.

Rama sugerida: `codex/notification-vigilia-visibility`.

Debe nacer del `HEAD` posterior a Perfil y conservar Android 8/API 26, Room v5/13 entidades y los contratos vigentes. Su alcance es:

- refinar la notificación como tarjeta de estado Vigilia dentro de los límites del panel Android;
- mantener estado, abreviatura, horario completo y cronómetro dentro del cuerpo;
- agregar `Eliminar notificación` como control explícito del contenido expandido;
- registrar el ocultamiento por guardia en el DataStore existente;
- ofrecer `Mostrar notificación nuevamente` en Configuración mientras el aviso siga siendo elegible;
- reconocer el descarte por gesto que Android 14 o superior puede permitir, sin prometer persistencia absoluta;
- conservar privacidad, clima elegible, agrupación, sonidos, vibración y las tres acciones actuales;
- no agregar widget real, Live Update promovida, servicio en primer plano, polling, permisos, dependencias ni cambios de Room.

La implementación se valida por impacto sobre presenter, `RemoteViews`, receiver, preferencias, reconciliación, Configuración y navegación. MAIN debe auditarla e integrarla antes de abrir Onboarding.

## 6. Dependencia 4 — Bienvenida, onboarding y primera carga

Prompt: `docs/prompts/ONBOARDING_Y_PRIMERA_CARGA.md`.

Rama sugerida: `codex/onboarding-first-shift`.

Debe nacer del `HEAD` posterior a Notificaciones e implementar:

- splash técnico sólo si aporta, sin demora artificial;
- bienvenida clara para vigiladores;
- tres pasos: organizar guardias, conocer horas/próximos eventos y privacidad local;
- omitir y repetir desde Ayuda;
- finalización persistida localmente;
- permisos solicitados únicamente en contexto;
- llegada al Calendario;
- calendario vacío con `Cargar mi primera guardia`;
- primera carga que reutiliza Perfil, Objetivos, Horarios y Calendario reales;
- abandono o Atrás sin filas parciales falsas.

No crear login, cuenta ni formularios paralelos exclusivos del tutorial.

## 7. Contrato común de dependencias

Cada prompt debe incluir:

- base funcional, rama y HEAD de arranque entregado por MAIN;
- alcance incluido y excluido;
- decisiones congeladas y contratos compartidos;
- archivos o zonas permitidas;
- persistencia y migraciones aplicables;
- criterios de aceptación;
- pruebas JVM, instrumentadas y físicas;
- devolución autocontenida a MAIN.

El especialista no redefine producto, no integra, no confirma ni publica por su cuenta. Un cambio compartido no autorizado vuelve a MAIN como bloqueo o propuesta mínima.

## 8. Integración obligatoria de MAIN

Después de cada entrega:

1. verificar ruta, rama, SHA base, estado y diff completo, incluidos nuevos;
2. revisar Room, DataStore, navegación, dominio, permisos, dependencias, secretos y datos reales;
3. construir un mapa de impacto y ejecutar con `--max-workers=1` las pruebas, lint y ensamblados pertinentes para las funciones o contratos realmente modificados;
4. conservar como evidencia la última batería verde de módulos sin cambios, sin repetirla por rutina;
5. usar el Samsung `SM-S938B` y paquetes QA aislados únicamente para las superficies Android afectadas o recorridos destructivos;
6. reservar la batería global para cambios transversales, release, publicación o una auditoría integral solicitada;
7. ejecutar siempre `git diff --check` y revisar alcance, seguridad y privacidad;
8. preservar `com.blackatsystems.miguardia` y sus datos;
9. corregir o rechazar defectos;
10. integrar y alcanzar un nuevo `HEAD` limpio antes de crear el worktree siguiente.

La evidencia del especialista no sustituye la verificación de MAIN.

## 8. Entregables y cierre

MAIN informa en cada etapa qué funciona, archivos y contratos cambiados, pruebas reales, recorrido físico, pendientes y nuevo SHA base. No avanzar a pagos ni otras profesiones hasta terminar estas tres superficies para vigiladores.
