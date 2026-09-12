# Auditoría MAIN: planes recurrentes y cambios futuros V2

- Fecha: 2026-08-25
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD recibido: `12fa7f64eef7493f8324467c876b55c9883d8625`
- Upstream al iniciar: `0364b835d07883708e137a7057f235fad9113b38`
- Resultado: **ACEPTADO E INTEGRADO LOCALMENTE POR MAIN**
- Publicación: no realizada

## Objetivo auditado

Agregar planes recurrentes finitos V2 y permitir decidir conscientemente si
una corrección o eliminación afecta una jornada exacta o todo lo futuro desde
una fecha, sin convertir la consulta del Calendario en escritura ni perder
historia o excepciones manuales.

## Puerta 0

MAIN verificó en vivo:

- ruta, rama y HEAD de entrada correctos;
- upstream conocido, sin divergencia remota inesperada;
- `v1.0.0^{}`, `main` y `origin/main` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- tag anotado, remoto privado y autor Git esperados;
- índice vacío y candidato sin commit;
- worktrees históricos presentes e intactos;
- Samsung `SM-S938B`, API 36, disponible;
- ningún push, tag, merge, rebase, reset ni descarte.

## Resultado funcional

- `Repetir jornadas` aparece sólo con configuración `V2Ready`.
- Se admiten exactamente días de semana, cada N días, cada N semanas y mensual
  por ordinal y día.
- Inicio y final son inclusivos y la vista previa enumera las fechas exactas.
- Confirmar materializa inmediatamente cada par obligatorio
  `Shift + ShiftWorkSnapshot`.
- Los planes guardan revisiones inmutables y ocurrencias `AUTOMATIC`,
  `CUSTOMIZED`, `EXCLUDED` o `RETIRED`.
- Se puede cambiar o eliminar sólo una jornada, cambiar todo lo futuro o
  finalizar desde una fecha.
- Pasado, personalizaciones, exclusiones, notas, avisos, carpetas médicas y
  jornadas manuales permanecen protegidos.
- Carga manual, Calendario, edición exacta, próximo evento y notificaciones
  continúan consumiendo jornadas concretas.
- Borradores, errores, reintentos, doble toque, recreación y conflictos
  concurrentes tienen tratamiento explícito.

Por decisión expresa de Joaquin, una creación o cambio admite como máximo
2.000 jornadas concretas. Un resultado de 2.001 o más se rechaza entero, con
mensaje visible y sin truncamiento.

## Persistencia

`MiGuardiaV2Database` pasa de versión 1 a versión 2 mediante migración
explícita `1→2`.

Se agregan exactamente:

1. `recurring_plans`;
2. `recurring_plan_revisions`;
3. `recurring_occurrences`.

La base queda con 22 tablas. El esquema de versión 1 permanece intacto:

- `1.json` SHA-256:
  `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- `2.json` identity hash: `897d0e0f70393686f5ab369c9428350f`;
- `2.json` SHA-256:
  `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`.

Todas las mutaciones estructurales siguen pasando por
`V2ShiftRepository` y una única transacción Room. No se agregó fallback
destructivo ni una conexión con la cadena Room histórica.

## Defectos encontrados y corregidos

La auditoría independiente de sólo lectura y MAIN encontraron defectos reales
en el candidato inicial:

- fechas con formato no exacto podían aceptarse;
- un cambio de plan podía ignorar otra jornada ocupante de la misma fecha;
- no existía límite de expansión y un rango extremo podía bloquear la app;
- faltaba comprobar que jornada, fotografía y revisión del plan coincidieran;
- consultas Room podían superar el límite de parámetros de SQLite;
- una mutación recurrente podía intentar modificar una jornada manual;
- algunas protecciones del vecindario y la finalización eran incompletas;
- errores de carga podían mostrarse como listas vacías;
- faltaban reintentos reales y un conflicto CAS podía dejar una vista previa
  guardable;
- un plan finalizado todavía ofrecía acciones futuras imposibles;
- los días de semana usaban semántica de opción única pese a admitir varios.

Se corrigieron esos puntos, se fragmentaron consultas Room en lotes de 900 y
se agregó cobertura para 2.000/2.001 jornadas y para 1.001 ocurrencias
persistidas. Las esperas de tres pruebas Activity también se hicieron
deterministas para el emulador API 26 sin relajar la conducta funcional.

## Validación local

Comando contractual final:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Resultado:

- dominio JVM: 195/195;
- base JVM: 5/5;
- aplicación JVM: 128/128;
- total JVM: 328, sin fallos, errores ni omitidas;
- lint: 0 errores y 4 avisos de versiones disponibles;
- APK Debug y QA compilados;
- AndroidTest de aplicación y base compilados;
- `git diff --check`: correcto.

## Samsung API 36

- Dispositivo: Samsung `SM-S938B`, serial `R5CY529W6PL`, API 36.
- Batería de aplicación afectada: 89/89.
- Room completo: 74/74.
- Después de endurecer tres esperas multiplataforma, las tres pruebas afectadas
  volvieron a pasar 3/3.
- Revisión visual directa en oscuro y retrato: plan recurrente visible en el
  Calendario y próximo evento; accesos `Cargar jornadas`, `Repetir jornadas` y
  `Mi forma de trabajar`; formulario con cuatro patrones y casillas múltiples.
- Claro/oscuro, retrato/paisaje y zoom interno 100 %, 150 % y 200 % quedaron
  ejecutados por instrumentación física.

## Emulador API 26

- AVD existente: `MiGuardia_API_26`; no se creó ni descargó otro.
- Aplicación afectada: 89/89.
- Room completo: 74/74, incluida migración `1→2`, integridad, reapertura,
  atomicidad, rollback y el lote de 1.001 ocurrencias.
- La primera corrida expuso tres esperas visuales no deterministas. Se
  corrigieron y la repetición completa quedó verde.

## Seguridad y límites

- Sólo se usaron datos ficticios y los paquetes QA autorizados.
- Al finalizar quedaron ausentes `com.blackatsystems.miguardia.qa`,
  `com.blackatsystems.miguardia.qa.test` y
  `com.blackatsystems.miguardia.core.database.test`.
- El emulador fue apagado.
- Producción permaneció instalada únicamente en el usuario 10 del Samsung,
  con `stopped=true` y `notLaunched=true`; no se abrió ni modificó.
- No se consultó ni cambió `font_scale`, densidad o tamaño visual del sistema.
- No cambiaron Gradle, dependencias, manifiesto, permisos, `applicationId`,
  versión ni SDK.
- No se agregó red, nube, cuentas, sincronización, telemetría ni datos reales.

## Riesgos y pendientes

- API 37 permanece como verificación del candidato final, no de este bloque.
- El recorrido físico de alarmas exactas conserva su autorización separada.
- Horario real, extras, disponibilidad, situaciones especiales y Resumen V2
  siguen en la hoja de ruta.

## Próximo paso recomendado

Preparar, únicamente cuando Joaquin lo pida, el contrato de **horario real,
extras y avance contra la referencia**. No existe otra dependencia abierta ni
habilitada y este bloque no autoriza push.
