# Auditoría MAIN — Próximo evento y notificaciones V2

- Fecha: 2026-08-27
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `af206fad8b6b2ac916bb891a20460d58b1aa01cb`
- Upstream de entrada: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`
- Base protegida `v1.0.0^{}`, `main` y `origin/main`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`

## Resultado

MAIN aceptó e integró Próximo evento y notificaciones V2. La tarjeta superior,
el observador reactivo, el plan de alarmas y el receptor usan una única
proyección tipada para jornadas y tramos efectivos de disponibilidad.

La misma identidad y las mismas reglas deciden qué está activo, qué viene
después y qué aviso corresponde. Una jornada activa reemplaza sólo el tramo
coincidente de Guardia pasiva, Disponible para llamado o Retén. Vacaciones,
carpeta médica, cancelación, ausencia y horario real evitan eventos obsoletos.
Los extras independientes continúan siendo trabajo ya realizado y no se
inventan como eventos futuros.

## Auditoría independiente y correcciones de MAIN

MAIN revisó el diff completo contra el prompt y encargó auditorías de sólo
lectura sobre dominio, observación, runtime Android, receptor, presentación y
compatibilidad API 26. Después de corregir y repetir las pruebas no quedaron
hallazgos bloqueantes.

Se corrigieron, entre otros puntos:

- la prioridad de una jornada completada hoy frente a un evento futuro;
- la reconciliación atómica después de cambios concurrentes de datos o
  preferencias;
- el límite de alarmas instaladas, el control de desbordamientos y los
  reintentos acotados;
- la privacidad del resumen agrupado y la consulta de clima sólo desde caché
  al disparar un aviso;
- la cancelación segura de tracking antes de retirar alarmas;
- la persistencia y el vencimiento de avisos descartados;
- la reconstrucción real después de reemplazar el paquete;
- el aislamiento de las pruebas de presentación frente al runtime vivo de la
  aplicación.

El conjunto previo a esta documentación contiene 40 archivos modificados y dos
nuevos. No se eliminó ningún archivo.

## Persistencia y arquitectura protegida

`MiGuardiaV2Database` permanece en versión 5 con 27 tablas. No cambiaron Room,
entidades, DAO, migraciones ni esquemas. Tampoco cambiaron Gradle, manifiesto,
permisos, dependencias, SDK, paquete o versión.

Esquemas verificados:

- `1.json`: `5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E`;
- `2.json`: `E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50`;
- `3.json`: `39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428`;
- `4.json`: `796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B`;
- `5.json`: `40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4`.

El identity hash continúa en `77adbc875d0f4ee466cdbd0dd74d5c5c`. No existe
`fallbackToDestructiveMigration` ni `allowMainThreadQueries` en producción.

## Validación local de MAIN

La batería contractual se ejecutó serialmente y desde cero:

```text
:core:domain:test
:core:database:testDebugUnitTest
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleQa
:app:assembleQaAndroidTest
:core:database:assembleDebugAndroidTest
```

Resultado final:

- dominio JVM: 302/302;
- base JVM: 12/12;
- aplicación JVM: 184/184;
- total JVM: 498/498, sin fallos, errores ni omisiones;
- lint: 0 errores y 6 avisos globales de versiones disponibles en archivos
  Gradle no modificados;
- APK Debug, QA, AndroidTest QA y AndroidTest de base: compilados;
- AndroidTest declarados y compilados: 235 de app y 107 de base;
- `git diff --check`: correcto.

No se agregaron red, cuentas, nube, telemetría, logs privados, datos reales ni
consultas o cambios del zoom, tamaño visual o densidad del sistema.

## Samsung API 36

Joaquin autorizó expresamente el Samsung `SM-S938B`, API 36, serie
`R5CY529W6PL`.

Evidencia ejecutada:

- Room y persistencia completas: 107/107;
- suite completa de aplicación del candidato antes del último aislamiento de
  pruebas: 233/233;
- matriz afectada final sobre el estado entregado: 84/84;
- permiso de avisos denegado, concedido y bloqueo desde la aplicación del
  sistema comprobados;
- reemplazo de paquete y reconstrucción de límites verificados;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200 cubiertos por
  instrumentación.

MAIN inspeccionó además un recorrido con datos ficticios: una jornada completada
INT y un Retén de 24 horas cuyo trabajo coincidente reemplazó ocho horas. El
segmento efectivo 16:00–00:00 apareció igual en Calendario, tarjeta superior y
aviso activo. Se inspeccionaron oscuro al 100 %, claro al 100 % y claro al
200 %.

No se consultaron ni modificaron `font_scale`, densidad o tamaño visual del
sistema. La orientación quedó restaurada a modo libre.

## Android 8 API 26

El emulador autorizado `MiGuardia_API_26` pasó la matriz final 20/20. Una
secuencia separada preparó un límite persistido, reemplazó realmente el paquete
QA mediante `install -r` y comprobó que `MY_PACKAGE_REPLACED` reconstruyó la
alarma exacta esperada.

Un primer recorrido expuso una caída aislada del launcher de Android 8 al
desparcelar un bitmap. MiGuardia no incluye bitmaps en sus `RemoteViews`; el
hecho no reapareció en la prueba aislada, la clase completa ni la matriz limpia.
El launcher conservó su proceso y el registro final tuvo cero fallos fatales.
La auditoría independiente lo clasificó como incidente transitorio del entorno,
no como defecto reproducible de producción.

API 37 no estaba disponible: la imagen guardada del AVD faltaba. No se descargó
otra imagen ni se amplió el entorno sin autorización.

## Límites y seguridad física

No se disparó una alarma exacta real ni se reinició físicamente el Samsung;
ambas acciones conservan una autorización inmediata separada.

Se utilizaron exclusivamente paquetes QA y de pruebas. Al finalizar no quedó
ningún paquete `com.blackatsystems.miguardia*` en el Samsung ni en el emulador,
y el emulador fue detenido. Producción no fue instalada, abierta, consultada,
limpiada, modificada ni desinstalada.

## Git y próximo paso

El candidato llegó directamente al checkout compartido, sin commit ni staged.
MAIN preservó `main`, `origin/main`, `v1.0.0`, los worktrees históricos y el
upstream. No hubo merge, rebase, reset, descarte, tag, Release ni acción sobre
producción.

La próxima etapa aprobada es la auditoría integral del núcleo y compatibilidad
Android. No existe otro prompt de implementación habilitado. Este cierre
autoriza el checkpoint local automático de MAIN, pero no autoriza push.
