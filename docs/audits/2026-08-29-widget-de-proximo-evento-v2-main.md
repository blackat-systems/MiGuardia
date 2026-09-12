# Auditoría MAIN — Widget de próximo evento V2

- Fecha: 2026-08-29
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD auditado: `0c2b7dc5737cf66497fda2714a5bdf82c45d8c63`
- Resultado: **CERRADO — IMPLEMENTADO, AUDITADO Y VERIFICADO POR MAIN EN
  SAMSUNG API 36**

## Objetivo

Auditar, corregir, validar e integrar el handoff del Widget de próximo evento
sin publicarlo ni abrir Informes. Android 8/API 26 y Android 13/API 33 se
conservan como compatibilidad pendiente porque su autorización no se hereda de
tareas anteriores.

## Puerta 0

Antes de instalar o editar se verificó:

- ruta, rama y HEAD exactos;
- upstream `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- rama 9 commits adelante y 0 detrás;
- autor `joaquin <blackat.systems@gmail.com>`;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- nueve worktrees históricos preservados;
- diff candidato esperado, sin staged y con `git diff --check` limpio;
- Samsung `SM-S938B`, API 36, serie `R5CY529W6PL`;
- ningún paquete `com.blackatsystems.miguardia*` instalado;
- rotación automática activa con `accelerometer_rotation=1` y
  `user_rotation=0`.

## Auditoría independiente y correcciones MAIN

Tres revisiones de sólo lectura separaron dominio, runtime Android y
UI/pruebas. MAIN corrigió siete huecos concretos dentro del alcance:

1. `WidgetProjection.events` ahora expone una colección realmente inmutable.
2. Una frontera que ya pasó oculta el `Chronometer` en lugar de mantener un
   contador vencido en cero.
3. La identidad completa permitida queda en la URI del `PendingIntent`; dos
   UUID con el mismo hash de 32 bits ya no pueden compartir navegación.
4. Los cambios del tema `SYSTEM` se reciben mediante un receiver dinámico y no
   mediante `ACTION_CONFIGURATION_CHANGED` en el manifest, donde Android no lo
   entrega.
5. El estado transitorio `saving` dejó de ser restaurable, evitando volver de
   una recreación a un guardado cuya coroutine ya fue cancelada.
6. Se agregaron dos pruebas de Activity real para guardado/recreación y
   cancelación de reconfiguración con preferencias previas.
7. Se completó cobertura de carga, vacío, error, reintento y del modo
   Automático con varias jornadas activas iniciadas a horas distintas.

El candidato de código quedó en 38 rutas antes de documentación: 8 modificadas,
30 nuevas, 0 eliminadas y 0 staged. No se modificaron Room, esquemas,
migraciones, Gradle, dependencias, permisos, SDK, versión ni package.

## Validación local MAIN

La batería contractual completa se repitió sobre el estado corregido con
`--rerun-tasks`, `--max-workers=1` y 351/351 tareas ejecutadas.

- JVM: 532/532, sin fallos, errores ni omitidas:
  - dominio: 319;
  - base local: 12;
  - app: 201.
- Lint: 0 errores; 6 avisos de versiones en archivos Gradle no modificados.
- Compilados: Debug, QA y Release sin firma.
- AndroidTest compilado: app 255 y base local 108.
- `git diff --check`: limpio.
- Secretos, Glance, WorkManager, polling, alarmas exactas nuevas,
  `fallbackToDestructiveMigration` y `allowMainThreadQueries`: 0.

## Room

Room permanece byte a byte sin cambios:

- `MiGuardiaV2Database`, archivo `miguardia-v2.db`;
- versión 5;
- 27 tablas;
- `identityHash` `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- esquemas 1–5 intactos.

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## Validación física Samsung

Joaquin autorizó expresamente el uso del Samsung conectado. MAIN ejecutó con
paquetes QA y datos ficticios:

- matriz dirigida Widget/navegación/consulta: 27/27;
- base Room completa: 108/108;
- aplicación QA completa salvo la única clase de siete pruebas que dispara
  alarmas exactas reales: 248/248;
- fallos, errores y omitidas: 0.

La matriz dirigida de 27 casos es un subconjunto repetido dentro de las 248
pruebas de aplicación y no se suma como evidencia única. Los XML finales
registran 248 pruebas de app y 108 de base.

Revisión visual y táctil real en One UI Launcher:

- selector de Samsung con preview correcto y descriptor 3×2;
- actividad de configuración visible, legible y con modo/privacidad seguros;
- alta real de una instancia en privacidad Oculta;
- descripción accesible exacta
  `MIGUARDIA. Tenés información en MiGuardia.`;
- cero filtración visible de tipo, fecha, horario, lugar, color, Clima o cuenta
  regresiva en modo Oculto;
- resize real de compacto 3×2 a ampliado 4×3;
- presentación válida en retrato y paisaje;
- toque del Widget abrió `com.blackatsystems.miguardia.qa/...MainActivity`;
- pantalla de primera apertura conservó los cuatro rubros exactos.

La ejecución anterior del especialista sobre el candidato previo registró dos
instancias, reconfiguración cancelada, muerte de proceso y reemplazo de paquete.
MAIN no presenta esos recorridos como repetidos después de sus correcciones;
quedan como evidencia heredada complementaria.

## Seguridad del dispositivo

- Producción estaba ausente y nunca fue instalada, abierta, limpiada ni
  desinstalada.
- MAIN instaló manualmente sólo `com.blackatsystems.miguardia.qa`; Gradle usó
  además los paquetes QA/test autorizados durante la instrumentación.
- El widget temporal desapareció al retirar QA.
- Estado final: ningún paquete `com.blackatsystems.miguardia*` instalado.
- Página inicial y orientación restauradas; valores finales
  `accelerometer_rotation=1`, `user_rotation=0`.
- No se consultaron ni modificaron `font_scale`, densidad, tamaño visual, hora,
  zona, Wi‑Fi, datos o VPN.
- No hubo reboot, alarma exacta real ni operación sobre producción.

## Pendientes y veredicto

No se reprodujo ningún defecto funcional abierto en Samsung. Android 8/API 26
y Android 13/API 33 no fueron autorizados para este bloque y no corresponde
reutilizar la matriz anterior del núcleo como si hubiese probado este código
nuevo. `DONE WHEN` permite cerrar con esa QA informada honestamente; la deuda se
mantiene para la matriz de compatibilidad posterior.

Por eso:

```text
WIDGET CERRADO POR MAIN
SAMSUNG API 36: VERDE
ANDROID 8/API 26: PENDIENTE DE AUTORIZACIÓN
ANDROID 13/API 33: PENDIENTE DE AUTORIZACIÓN
```

La alarma exacta real y el reinicio físico siguen siendo puertas separadas. La
skill de MAIN autoriza el checkpoint local automático de este bloque verde;
push, Informes y cualquier dispositivo adicional continúan como puertas
separadas.
