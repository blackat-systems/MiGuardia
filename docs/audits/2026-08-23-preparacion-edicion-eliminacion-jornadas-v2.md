# Preparación de edición y eliminación individual de jornadas V2

- Fecha: 2026-08-23
- Rol: MAIN 2.0
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `a63ca2526070e794951ba8363de52bf80e61efa7`
- Alcance: auditoría y contrato documental; sin cambios de aplicación

## Resultado

La etapa funcional más avanzada continúa siendo la carga manual V2 integrada
en `ae57686`. La siguiente función autorizada es corregir o eliminar una
jornada V2 individual desde el detalle de su día.

No se reabren las decisiones del dominio laboral, los cuatro sectores, el
catálogo, la primera apertura ni la carga manual. Room permanece en v7 y no
cambia su esquema. La limpieza del modo V1 deja de ser una puerta previa para
este incremento: permanece pendiente antes de ampliar el esquema Room o cerrar
el candidato final.

Un eventual cambio de profesión después del rubro inicial no forma parte de la
secuencia actual.

## Puerta 0

Verificado antes de editar documentación:

- proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD: `a63ca2526070e794951ba8363de52bf80e61efa7`;
- upstream: `836d908f54a407c48cc9e3c27c9587c6dc908ca2`;
- `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- tag anotado: `227c931ff8e381ab00120ad61b1c86ac71c03e46`;
- árbol limpio, sin staged ni archivos no rastreados;
- remoto privado y autor Git correctos;
- worktrees históricos observados y no modificados.

No existe autorización de push, tag, Release, `main` ni producción.

## Auditoría de contratos existentes

El árbol actual ya contiene:

- `editV2ShiftWrite(...)`, que conserva identidad, creación, estado y zona;
- `planV2ShiftBatch(..., editingShiftId = ...)`, que produce una actualización;
- `V2ShiftRepository.applyV2Batch(...)` con precondición de ocupación;
- actualización atómica de `Shift + ShiftWorkSnapshot`;
- eliminación transaccional y cascada de la fotografía;
- pruebas puras y Room parciales para edición y borrado.

La interfaz V2 mantiene esas acciones ocultas porque el contrato anterior las
excluía. No falta otra versión de base de datos: faltan una superficie V2 propia,
la comparación transaccional del par confirmado, la excepción cerrada de
edición histórica de sólo puesto y cobertura de integración.

## Riesgos tratados por el contrato

- la edición visible no usará `ShiftDraft`, `ManagementViewModel` ni escritores
  V1;
- cambiar sólo el puesto conservará todo el par histórico salvo puesto y versión,
  incluso si la plantilla original fue archivada;
- elegir otra plantilla exigirá fuentes activas y coherentes para la fecha;
- la edición comparará la ocupación de dos días a cada lado y el par original
  completo dentro de la transacción;
- la eliminación agregará una precondición del par completo y una matriz exacta
  de datos dependientes dentro de su transacción;
- la fecha permanecerá fija;
- las demás jornadas nunca se reemplazarán ni eliminarán;
- eliminar no recreará `F/?` ni borrará catálogo o configuración;
- editar o eliminar conservará la reconciliación existente de avisos, probada
  con dobles sin abrir permisos ni alarmas exactas físicas;
- Room continuará en v7 sin cambios de esquema.

## Contrato producido

Se creó
`docs/prompts/EDICION_Y_ELIMINACION_DE_JORNADAS_V2.md` con estado
`HABILITADO — PENDIENTE DE IMPLEMENTACIÓN`.

El contrato incluye rol, tarea, contexto, entradas, decisiones congeladas,
salidas, alcance, dependencias, prohibiciones, validación local, Room, Samsung,
handoff a MAIN y definición concreta de terminado.

## Alineación documental preparada

Se actualizan las fuentes activas para que la secuencia sea:

1. carga manual V2 —cerrada—;
2. edición y eliminación individual —prompt habilitado—;
3. retiro del modo V1 antes de una futura ampliación de persistencia;
4. recurrencias y edición de una fecha o de todo lo futuro.

Las auditorías anteriores conservan su contenido histórico y reciben una nota
de reemplazo sobre su antiguo “próximo paso”.

## Verificación de este checkpoint

VERIFICADO:

- las referencias Markdown locales de los catorce archivos del checkpoint
  existen;
- el prompt y su índice coinciden en `HABILITADO / NO ABIERTO`;
- las fuentes activas sitúan primero la edición/eliminación individual y no
  presentan el cambio de rubro como próximo bloque;
- las menciones históricas contradictorias quedaron rotuladas como reemplazadas;
- tres revisiones independientes de sólo lectura cubrieron jerarquía documental,
  contratos de dominio/Room y navegación/Compose; los hallazgos fueron
  incorporados y las revisiones finales no dejaron bloqueantes;
- SHA-256 de `7.json`:
  `E3DA609D63A26609C9679DF49766714A74809CF2259CDA14FEBDF4E11D753C03`;
- no hay espacios finales y todos los archivos terminan en salto de línea;
- `git diff --check` y `git diff --cached --check`: correctos;
- el staging contiene exactamente catorce archivos bajo `docs/**`, sin código,
  Gradle, Room, manifiesto, permisos ni binarios.

No se ejecutan Gradle ni ADB porque este checkpoint no modifica código ni el
dispositivo. La implementación y sus pruebas pertenecen a la dependencia
habilitada.

No se realiza push.
