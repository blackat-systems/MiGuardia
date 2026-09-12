# Preparación del Widget de próximo evento V2

- Fecha: 2026-08-29
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `7dadd6299a864df939b5de6d6d6f67d9df737c53`
- Resultado: **PROMPT HABILITADO — TAREA NO ABIERTA**

## Objetivo

Cerrar el contrato del primer bloque de la segunda capa sin implementar código,
abrir otra tarea, usar dispositivos ni publicar la rama.

## Puerta 0

Verificado antes de editar documentación:

- ruta y rama exactas;
- HEAD `7dadd6299a864df939b5de6d6d6f67d9df737c53`;
- upstream `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- rama 8 commits adelante y 0 detrás;
- checkout limpio, sin staged, unstaged ni archivos no rastreados;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- autor `joaquin <blackat.systems@gmail.com>`;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- worktrees históricos preservados.

## Fuentes auditadas

Se leyeron en la jerarquía de `AGENTS.md`:

- mapa, estado, planificación e índice canónico;
- prompt maestro MAIN 2.0 y orquestación secuencial;
- las cuatro fichas sectoriales;
- ADR aplicables, especialmente 0009, 0010, 0011, 0015, 0026 y 0032;
- contrato histórico V1 de motor, Widgets y Clima;
- prompts cerrados de Próximo evento y notificaciones, Clima y Vigilia;
- auditoría integral del núcleo del 2026-08-29;
- implementación y pruebas actuales de próximo evento, tarjeta, avisos, Clima,
  navegación, tema y preferencias.

También se contrastaron las guías oficiales vigentes de Android sobre
[App widgets](https://developer.android.com/develop/ui/views/appwidgets),
[layouts responsivos](https://developer.android.com/develop/ui/views/appwidgets/layouts),
[actualizaciones eficientes](https://developer.android.com/develop/ui/views/appwidgets/advanced),
[configuración](https://developer.android.com/develop/ui/views/appwidgets/configuration),
[previews](https://developer.android.com/develop/ui/views/appwidgets/previews) y
[seguridad de PendingIntent](https://developer.android.com/privacy-and-security/risks/pending-intent),
[calidad de widgets](https://developer.android.com/docs/quality-guidelines/widget-quality)
y la referencia de
[`OPTION_APPWIDGET_RESTORE_COMPLETED`](https://developer.android.com/reference/android/appwidget/AppWidgetManager#OPTION_APPWIDGET_RESTORE_COMPLETED).

## Estado real del código

- `projectNextEvent(...)` ya es la única elegibilidad y prioridad para jornada,
  disponibilidad y franco explícito.
- `NextEventResult` conserva eventos activos, próximos, simultáneos,
  `nextDayOff`, identidad estable y duración.
- `V2WorkEventSourceObserver` reúne todas las fuentes V2 y todos los sectores de
  la historia laboral.
- `MainActivity` ya puede revalidar una jornada por UUID y abrir una fecha.
- `RemoteViews` y `PendingIntent` seguros existen en Notificaciones.
- Clima ofrece repositorio, caché, frescura y agregación reutilizables; su
  preferencia `includeInNotifications` no pertenece al Widget.
- No existe `AppWidgetProvider`, Glance, WorkManager ni una implementación
  histórica recuperable.
- No se necesita cambiar Room, Gradle, permisos, SDK, package o versión.

## Decisiones registradas

ADR 0033 y el prompt habilitado congelan:

- `AppWidgetProvider + RemoteViews` nativo;
- adaptador puro sobre `NextEventResult`, no segundo motor;
- modos Próxima jornada, Próximo franco y Automático;
- consumo completo de `primaryEvents` en Automático, sin un segundo filtro de
  simultáneos o prioridad;
- privacidad completa, reducida u oculta por instancia;
- varios widgets independientes y layouts compacto/ampliado;
- DataStore exclusivo por `appWidgetId`;
- `Chronometer`, actualización reactiva mientras el proceso vive y una única
  frontera inexacta mediante `PendingIntent` reconstruible para proceso muerto;
- `updatePeriodMillis=0`, sin polling, WorkManager o servicio permanente;
- receivers con `goAsync()` y finalización garantizada;
- restauración que no inventa preferencias ausentes por `allowBackup=false`;
- coordinación de `onRestored` con `onUpdate` y cierre explícito desde API 30;
- cronómetro basado en tiempo monotónico, no epoch;
- Clima opcional, ampliado, completo, cacheado y no bloqueante;
- atribución meteorológica que abre la superficie interna con el enlace real;
- tema global de MiGuardia, sin una preferencia adicional por instancia;
- destino visible `Widget de inicio` dentro de `Avisos y contexto`;
- mapas, direcciones e Informes fuera de alcance.

## Ambigüedades resueltas

1. El franco explícito continúa excluido de Notificaciones, pero puede aparecer
   en el Widget como superficie visual usando el mismo `nextDayOff`.
2. Próxima jornada elige sólo una jornada futura; la activa pertenece al modo
   Automático.
3. Automático incluye disponibilidad efectiva activa o futura sin convertirla
   en trabajo.
4. El zoom interno aplica a la actividad de configuración. El launcher decide
   el tamaño del `RemoteViews` sin consultar ajustes visuales de Android.
5. Glance no se incorpora porque agregaría una dependencia y no reutilizaría
   directamente Compose.
6. Clima tiene una opción propia por instancia y no reutiliza la preferencia de
   Notificaciones.
7. Cancelar el alta deja la instancia sin configurar; cancelar una
   reconfiguración preserva el estado anterior.
8. La transición de fecha se prueba con reloj inyectado; el recorrido físico
   no modifica el reloj del dispositivo.

## Validación documental

Esta preparación no modifica código, Room, DataStore productivo, Gradle,
manifest, permisos, recursos ni pruebas. Por esa razón no corresponde ejecutar
Gradle ni ADB.

Antes del checkpoint MAIN debe verificar:

- referencias Markdown locales;
- estado único `HABILITADO` del prompt;
- coherencia entre mapa, planificación, estado, índice y orquestación;
- `git diff --check`;
- ausencia de cambios fuera de `docs/**`;
- diff staged exacto.

## Dispositivos y publicación

- ADB: no utilizado.
- Samsung: no utilizado.
- Emuladores: no utilizados.
- Paquetes instalados, abiertos, limpiados o desinstalados: ninguno.
- Commit: pendiente del checkpoint documental de MAIN.
- Push, tag, Release, `main` y producción: no autorizados.

## Próximo paso

MAIN crea el checkpoint documental local. Después puede entregar a Joaquin el
prompt corto de lanzamiento para abrir una única tarea **Widget de próximo
evento**. Informes permanece cerrado hasta recibir, auditar e integrar ese
handoff.
