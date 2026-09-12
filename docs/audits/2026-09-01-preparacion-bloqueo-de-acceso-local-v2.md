# Preparación de Bloqueo de acceso local V2

- Fecha: 2026-09-01
- Tipo: auditoría documental de MAIN
- Resultado: **CONTRATO HABILITADO — IMPLEMENTACIÓN PENDIENTE**

## Objetivo

Preparar una dependencia única y autosuficiente para proteger el acceso visible
a MiGuardia mediante la autenticación segura del teléfono, sin crear otra
credencial, sin cambiar Room y sin mezclar la seguridad local con Copias,
Widget o Notificaciones.

## Puerta 0

Verificado antes de editar:

- ruta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama: `codex/miguardia-2.0`;
- HEAD funcional de entrada:
  `7977913579cb92b9d3fefeb945274f312db9bd59`;
- upstream: `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- divergencia inicial: 14 commits adelante y 0 detrás;
- checkout inicial limpio, sin staged ni archivos no rastreados;
- `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- remoto privado correcto;
- nueve worktrees históricos registrados y preservados.

No se utilizó ADB, Samsung, emulador, Gradle ni aplicación productiva durante
esta preparación documental.

## Fuentes leídas

MAIN leyó y contrastó las fuentes obligatorias vigentes:

- `AGENTS.md`;
- mapa, estado y planificación 2.0;
- índice de prompts;
- prompt rector y coordinador secuencial;
- ADR aplicables hasta 0035;
- prompt histórico V1 como contrato heredado;
- manifiesto, catálogo y dependencias Gradle;
- `MainActivity`, `MiGuardiaApplication`, `WidgetConfigurationActivity`,
  navegación, preferencias, Copias y pruebas relacionadas.

También se contrastaron fuentes oficiales de Android sobre BiometricPrompt,
credencial del dispositivo, reloj monotónico, captura de Recientes y
`FLAG_SECURE`.

## Estado real encontrado

- No existe todavía un bloqueo de acceso ni una dependencia biométrica.
- `MainActivity` procesa destinos desde avisos y Widget después de la puerta de
  recuperación de Copias.
- `WidgetConfigurationActivity` es una entrada exportada controlada por el
  launcher y debe compartir la misma protección.
- La aplicación puede preservar borradores y superficies durante recreación.
- Notificaciones, Widget y Clima poseen runtimes y privacidad independientes.
- Copias exporta exactamente diecisiete preferencias semánticas y excluye
  estado específico del dispositivo.
- Room continúa en V5 con 27 tablas y no necesita una ampliación para bloquear
  la interfaz.

## Contrato fijado

ADR 0036 y `BLOQUEO_DE_ACCESO_LOCAL_V2.md` establecen:

- bloqueo opcional, apagado por defecto;
- biometría fuerte o credencial segura del dispositivo;
- ningún PIN propio de MiGuardia;
- plazos inmediato, 1, 5 y 15 minutos medidos con reloj monotónico;
- sesión autenticada sólo en memoria;
- proceso nuevo, muerte o bloqueo del dispositivo vuelven a cerrar;
- puerta que no compone información laboral;
- destinos entrantes y actividad del Widget sin bypass;
- protección de la vista de Recientes;
- DataStore exclusivo, sin secretos y con error seguro;
- preferencia no portable y formato V1 de `.miguardia-backup` sin cambios;
- Widget y Notificaciones sin cambios silenciosos de privacidad;
- Room V5 y esquemas intactos.

## Dependencia autorizada y fundamento

Se autoriza exclusivamente `androidx.biometric:biometric:1.1.0`, versión estable
oficial disponible al preparar el contrato. Se descarta implementar una
credencial propia o usar un artefacto alfa. La dependencia especializada deberá
medir tamaño y transitivas y validar compatibilidad desde API 26.

## Validación documental

Antes del checkpoint MAIN debe verificar:

- prompt con todas las secciones obligatorias del coordinador;
- estado `HABILITADO` coherente en índice, mapa, planificación y STATUS;
- cero cambios ejecutables;
- enlaces Markdown locales válidos;
- ausencia de espacios finales, mojibake y archivos sensibles;
- `git diff --check` y `git diff --cached --check` verdes;
- staging exclusivo de los documentos de preparación.

## Límites

Esta preparación no:

- implementa código;
- abre la tarea especialista;
- usa dispositivos;
- agrega realmente la dependencia o el permiso;
- ejecuta Gradle;
- modifica Room, Copias, Widget o Notificaciones;
- autoriza commit de implementación, push, tag, Release o producción.

## Próximo paso

Joaquin puede crear una única tarea y entregarle el prompt completo desde el
checkpoint documental de MAIN. La dependencia debe devolver un candidato sin
commit para auditoría, pruebas y QA independiente antes de cualquier cierre.
