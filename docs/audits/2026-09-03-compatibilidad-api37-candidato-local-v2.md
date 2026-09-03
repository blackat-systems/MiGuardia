# Auditoría MAIN — compatibilidad Android 17/API 37 y candidato local V2

- Fecha: 2026-09-03
- Rama: `codex/miguardia-2.0`
- Base técnica de esta puerta: `759c5191a17a91a009e6d7a7c3fc82db16014756`
- Checkpoint técnico: `0ef31e02d3b3fb1bb93e0ac94cb04302d6de7afb`
- Resultado: **VERIFICADO — API 37 VERDE; CANDIDATO LOCAL COMPLETO**

## Objetivo

Cerrar la última puerta Android pendiente de la hoja de ruta inicial sobre el
mismo candidato ya aprobado en Samsung API 36, Android 8/API 26 y Android
13/API 33. La ejecución debía conservar producción, Git, Room V6 y todas las
puertas externas separadas.

## Puerta 0

MAIN verificó antes de actuar:

- ruta `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama `codex/miguardia-2.0` y HEAD esperado;
- checkout limpio al comenzar;
- upstream `origin/codex/miguardia-2.0`;
- autor `joaquin <blackat.systems@gmail.com>`;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- worktrees históricos separados e intactos;
- documentación obligatoria y reglas de MAIN vigentes.

## Entorno API 37

Se instaló la imagen oficial
`system-images;android-37.1;google_apis_playstore_ps16k;x86_64`, revisión 9. El
AVD `Pixel_6a` se inició con una dimensión de piel temporal `1080x2400`, porque
su referencia de skin anterior no estaba disponible. No se cambió su
configuración persistente.

Entorno ejecutado:

- Android 17/API 37;
- ABI x86_64;
- dispositivo ADB `emulator-5554`;
- paquetes exclusivos QA y test;
- datos ficticios.

## Hallazgos y correcciones

### Espresso anterior incompatible con Android 17

La primera ejecución falló antes de evaluar la interfaz porque Espresso 3.5.0
intentaba acceder por reflexión a `InputManager.getInstance`, retirado en la
plataforma nueva. Se fijó `androidx.test.espresso:espresso-core:3.7.0`, versión
que migró ese acceso a `getSystemService` según las
[notas oficiales de AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test).

La dependencia quedó exclusivamente bajo `androidTestImplementation`. El grafo
de `qaRuntimeClasspath` no contiene Espresso y
`qaAndroidTestRuntimeClasspath` resuelve 3.7.0.

### Carrera real al perder foco en un horario

La matriz de disponibilidad reprodujo una carrera de interfaz. Cada edición de
`AutomaticTimeField` ya entregaba el valor canónico, pero al perder foco volvía
a emitirlo. Ese segundo callback podía invalidar la revisión de disponibilidad
mientras se preparaba de forma asíncrona.

La corrección conserva la normalización visual al salir del campo, pero evita el
segundo cambio funcional. Se agregaron regresiones para la edición corta y para
un padre que todavía no recompuso. El grupo dirigido de disponibilidad y campo
horario pasó cinco repeticiones completas, 15 invocaciones en total.

### Ajustes de pruebas para la interfaz vigente y API 37

Se actualizaron únicamente selectores y cierres de escenarios obsoletos:

- los desplegables avanzados se identifican dentro de su superficie exacta;
- recurrencias comprueba una fecha materializada concreta;
- Copias desplaza la advertencia antes de exigir que sea visible;
- dos cierres Activity verifican que la ventana QA desaparezca aunque API 37
  conserve un estado interno desactualizado en `ActivityScenario`.

Una auditoría independiente no encontró defectos P0, P1 o P2. Registró una
observación **P3 — ACEPTADA / NO BLOQUEANTE / CERRADA** de higiene: en la
excepción de estado desactualizado, el helper no invoca `close()` después de
verificar la desaparición real de la ventana, para no activar el fallo del
framework. No afecta producción ni oculta fallos de la aplicación.

## Validación local fresca

La batería completa se ejecutó con `--rerun-tasks`, un único worker y las 351
tareas contractuales:

- dominio JVM: 379/379;
- database JVM: 12/12;
- app JVM: 321/321;
- total JVM: 712/712;
- fallos, errores y omitidas: 0;
- lint: 0 errores y 6 avisos de versiones disponibles;
- Debug, QA y Release sin firma: compilados;
- AndroidTest de app QA y base Debug: compilados;
- `git diff --check`: limpio.

Artefactos:

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 17.883.780 | `38466CEEFFE285989CADEEF8F42D13A976A8C038C18EDEB88CCB619FEA88849D` |
| QA | 17.768.685 | `59D621D0E410F672295A9404989DA5BFB718BB319005F0A51231947DCDC1E9B2` |
| Release sin firma | 12.615.801 | `53B49A43686B6EE3B719F806E5D4136C7365CEDEFBA68B4DA0A805D91A033504` |
| QA AndroidTest | 2.045.535 | `0EC42F6234FD3D02ED69EF4D17ADFBC821162289D833CFF79C1361A7ABE2FF52` |
| Database AndroidTest | 4.184.074 | `B6827A38006EB01ADBC3B6AF33460437BB194B93B415DDAE9A5AAC9413F9E315` |

## Instrumentación API 37

- Room V6 completa: **126/126**.
- Aplicación proporcional: **176/176**.
- Fallos, errores y omitidas: 0.

La matriz excluyó deliberadamente el único caso que dispara una alarma exacta
real. No se ejecutó un reinicio. El total esperado inicialmente por el envoltorio
era 175, pero la nueva regresión elevó legítimamente el inventario a 176; la
salida real de instrumentación fue `OK (176 tests)`.

## Revisión visual

MAIN verificó directamente en el emulador:

- primera apertura con Vigilancia privada, Policía, Enfermería y Medicina;
- ausencia de `Salud`, `Otro` o una quinta opción;
- `Continuar` deshabilitado hasta elegir rubro;
- opción de restaurar una copia disponible;
- selección de Enfermería y llegada al Calendario vacío;
- Calendario legible y sin recortes en retrato y paisaje.

Tema oscuro y zoom interno 100 %, 150 % y 200 % fueron cubiertos por la matriz
instrumentada. No se presentan como revisión manual. No se consultó ni modificó
`font_scale`, densidad o tamaño visual del sistema.

## Room

Room permanece en versión 6, con 27 entidades, 0 vistas persistidas, 2 consultas
de preparación e `identityHash = 7eb39f6fab5a44e69350e206716554be`.
No se modificaron entidades, DAO, migraciones ni esquemas durante esta puerta.

## Seguridad del dispositivo

- Producción no fue instalada, abierta, consultada, limpiada ni desinstalada.
- El Samsung conectado no fue utilizado.
- Se retiraron los tres paquetes QA/test del emulador.
- La orientación volvió a automática, con rotación 0.
- Las capturas remotas del emulador fueron eliminadas.
- El emulador fue apagado.
- Tres copias locales de capturas ficticias quedaron fuera del repositorio en
  `%LOCALAPPDATA%\Temp\miguardia-api37-qa` porque el entorno rechazó la operación
  de borrado; no contienen datos reales ni forman parte de Git.

## Estado y puertas restantes

La matriz inicial queda completa en Android API 26, 33, 36 y 37. El resultado es
un **candidato local completo y auditado**, no una publicación.

Continúan como autorizaciones independientes:

- disparar una alarma exacta real;
- reiniciar físicamente el Samsung;
- push;
- tag;
- Release firmado o publicación;
- cualquier operación sobre `main` o producción.
