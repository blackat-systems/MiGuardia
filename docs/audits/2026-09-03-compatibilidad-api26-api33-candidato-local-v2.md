# Compatibilidad API 26/API 33 del candidato local V2

- Fecha: 2026-09-03
- Veredicto: **CANDIDATO LOCAL AUDITADO — ANDROID 8/API 26 Y ANDROID 13/API 33
  VERDES**
- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- Base de entrada: `1d055f122476b388d6d359cddc521e3461de64f3`
- Checkpoint técnico: `3e0b0bcdcd7a8dfe856f4dd786ec5e936cc4cb37`
- Upstream conservado: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`

## Objetivo

Cerrar las dos puertas de compatibilidad que la auditoría integral dejó
pendientes: Android 8/API 26 y Android 13/API 33. La matriz debía comprobar el
Geocoder legado, permisos modernos, recursos del ícono, Room V6 y regresiones
representativas sin tocar producción, disparar una alarma exacta real ni
reiniciar el Samsung.

## Puerta 0

Antes de usar emuladores, MAIN verificó ruta, rama, HEAD, upstream, referencias
protegidas, autor, remoto, worktrees, estado limpio, entorno Android e imágenes
disponibles. `main`, `origin/main` y `v1.0.0^{}` continuaron en
`82db6fd8eb2c511205968894dc9857a96b16ed20`. El autor efectivo fue
`joaquin <blackat.systems@gmail.com>` y el remoto siguió siendo el repositorio
privado esperado.

## Hallazgos corregidos

1. **Ícono genérico en API 26.** La pantalla de información de la aplicación
   mostraba el robot de Android. El par adaptativo base fue movido a
   `mipmap-anydpi-v26`, manteniendo la variante monocromática en `v33`. Se agregó
   `AppIconInstrumentedTest`, que resuelve el ícono principal y el redondo en
   toda API soportada. La excepción de lint se limita exclusivamente a ese
   directorio y documenta la evidencia de ejecución que la justifica.
2. **Inspección de permiso biométrico en API 26.** El framework de Android 8 no
   expone metadatos de `USE_BIOMETRIC`. La prueba conserva la exigencia moderna
   en el manifiesto y, sólo en API 26–27, inspecciona además el permiso
   transitivo `USE_FINGERPRINT` que entiende esa plataforma.
3. **Carrera de preparación en una prueba Room.** Una observación podía comenzar
   después de la escritura porque dependía de `yield()`. Se incorporó una
   barrera que espera la fotografía inicial explícita antes de liberar al
   escritor. El caso pasó cinco veces aislado y luego dentro de la suite completa.

Una auditoría independiente y de sólo lectura revisó los cambios finales y no
encontró findings P0, P1, P2 o P3.

## Android 8/API 26

Emulador: `MiGuardia_API_26`.

- Room instrumentada: **126/126**.
- Matriz dirigida de aplicación: **28/28**.
- Geocoder legado: una dirección pública ficticia, `Plaza San Martin, Cordoba,
  Argentina`, se resolvió como Plaza San Martín, Córdoba y fue confirmada desde
  la interfaz.
- Primera configuración: recorrida con datos ficticios.
- Ícono: la información de la aplicación mostró el ícono real de MiGuardia
  después de la corrección.
- Permisos de bloqueo: la prueba distinguió correctamente la superficie de
  permisos disponible en esta API.

## Android 13/API 33

Emulador: `MiGuardia_API_33`.

- Primera ejecución Room: **125/126** por la carrera de preparación descrita.
- Caso corregido aislado: **5/5** ejecuciones consecutivas.
- Room completa final: **126/126**.
- Matriz dirigida de aplicación: **55/55**.
- Ubicación: se verificaron diálogo aproximado, rechazo, explicación, apertura
  de Ajustes, permiso sólo durante el uso y retorno a MiGuardia.
- Ciudad actual: se inyectó una ubicación pública ficticia de Córdoba mediante
  un proveedor de prueba del emulador, porque el producto usa conscientemente
  `NETWORK_PROVIDER`; la interfaz confirmó el guardado puntual.
- Geocoder asíncrono: la dirección pública ficticia de Plaza San Martín fue
  resuelta, mostrada y confirmada.
- Notificaciones: se recorrieron rechazo, reintento, concesión, interruptor
  global de Android y restauración.
- Fallback inexacto: con el acceso especial de alarmas denegado se ejecutaron
  tres pruebas seguras y verdes. La evidencia es compuesta entre decisión pura,
  interfaz y programación inexacta; no se presenta como disparo de una alarma
  exacta real.
- Ícono monocromático: Pixel Launcher mostró visualmente el ícono temático de
  MiGuardia y después se restauró la preferencia del launcher.

## Validación local final

La batería completa se repitió desde cero y en serie:

```text
:core:domain:test
:core:database:testDebugUnitTest
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleQa
:app:assembleRelease
:app:assembleQaAndroidTest
:core:database:assembleDebugAndroidTest
```

Resultado: **BUILD SUCCESSFUL en 19 min 5 s**, 351/351 tareas ejecutadas.

- Dominio JVM: 379/379.
- Base JVM: 12/12.
- App JVM: 321/321.
- Total JVM: **712/712**, sin fallos, errores ni omitidas.
- Lint: 0 errores y 6 avisos de actualización en configuración existente.
- AndroidTest declarados y compilados: app 362; base 126.
- `git diff --check`: limpio.

## Room

Room continúa en V6, con 27 entidades, 0 vistas y dos consultas de preparación.
No se modificaron entidades, migraciones ni esquemas. Los hashes permanecen:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
6.json  BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268
```

## Seguridad del dispositivo

- Sólo se usaron los paquetes QA y de prueba en los dos emuladores autorizados.
- Todos esos paquetes fueron retirados y ambas instancias quedaron apagadas.
- El proveedor de ubicación simulado fue retirado y el permiso de ubicación
  simulada del shell volvió a denegado.
- El acceso especial de alarmas volvió a su estado predeterminado.
- Los íconos temáticos del launcher volvieron a estar apagados.
- El Samsung conectado no fue objetivo de ningún comando.
- Producción no fue instalada, abierta, consultada, limpiada ni desinstalada.
- No se disparó una alarma exacta real ni se reinició ningún dispositivo.

## Pendiente y límites

- API 37 conserva una puerta separada antes de llamar publicable al candidato.
- Una alarma exacta real y el reinicio físico del Samsung requieren
  autorizaciones independientes.
- La inspección visual OEM de la tarjeta de Recientes continúa como evidencia
  posterior del bloque de Bloqueo; no es un defecto de esta matriz.
- El uso de `NETWORK_PROVIDER` para la captura puntual de ciudad es intencional.
  Un timeout propio adicional sería una mejora de robustez P3, no un bloqueo
  reproducido.
- No hubo push, tag, Release, cambio de versión ni acción sobre `main`.

## Veredicto

La compatibilidad obligatoria del candidato local en Android 8/API 26 y Android
13/API 33 quedó cerrada con evidencia ejecutada. Junto con la matriz Samsung API
36 ya registrada, el candidato local está auditado en las tres plataformas
previas a la puerta API 37. Esto no constituye publicación ni autoriza push.
