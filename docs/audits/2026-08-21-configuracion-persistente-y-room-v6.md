# Auditoría de configuración persistente y Room v6

- Fecha: 2026-08-21
- Rama: `codex/miguardia-2.0`
- Base del bloque: `89937270df90d7d1739725a6be73539a2d0bade9`
- Dispositivo físico: Samsung `SM-S938B`, Android API 36
- Publicación: no hubo push, tag, Release ni operación sobre producción

## Resultado

Room abre tanto una base creada directamente en la versión 6 como una base
histórica migrada. El bloque guarda una única configuración laboral, su origen,
sus cambios desde una fecha y los valores de referencias que cambian por
período.

La migración `5→6` es aditiva: crea cuatro tablas y una raíz vacía con origen
`MIGRATED_V1`. No crea sector, referencia, disponibilidad, 204 horas ni franja
nocturna. Una instalación nueva queda sin raíz hasta que la persona elija su
sector. Ninguna de las trece tablas anteriores recibió columnas, índices o
claves nuevas.

## Decisiones comprobadas

- `PendingSetup`, `NotUsed` y `Unknown` son estados distintos y ninguno se
  transforma en cero horas.
- Una configuración nueva nace atómicamente con su primera revisión.
- Una configuración migrada puede permanecer vacía y recibir su primera
  revisión V2 más adelante, desde una fecha concreta.
- Las revisiones y las definiciones por período no se reescriben ni se borran.
- Un valor por período conserva identidad, definición y ventana; sólo pueden
  corregirse sus minutos de forma explícita.
- Los códigos guardados son estables y no dependen del nombre visible ni de
  `Enum.name`.
- Datos corruptos, raíces múltiples, referencias huérfanas y patrones
  incompatibles se rechazan como datos locales inválidos.

## Migración y esquemas

Las pruebas usaron datos ficticios en las trece familias de Room v5 y
comprobaron cada fila después de migrar. También recorrieron la cadena completa
`1→2→3→4→5→6`, una ruta faltante y una migración forzada a fallar. Los dos
últimos casos conservaron los datos y la versión anterior sin recurrir a una
migración destructiva.

Hashes SHA-256 conservados:

| Esquema | SHA-256 |
|---|---|
| `1.json` | `06557907F47669DF0E2F950C00FC7FC89EA45511386A9990803F01B86471AC1B` |
| `2.json` | `8D835CDF9616924A704EF3FDF89CC2BF1268F4275F5E9A978C6F20A6D44D7453` |
| `3.json` | `15299988DA323E9C0C434CC3087308D92605DA12A7AAEAD132E52B2AF7E162F2` |
| `4.json` | `933572FA5CEC8A9B41BEA84B905BCB0A091CB7C8B69C425B4981F5668DB8FE22` |
| `5.json` | `A73B70A1104970092D4155707F3C45429DA5546B5B020A5A6400AF7B33E0C9F9` |

Nuevo esquema:

| Esquema | SHA-256 |
|---|---|
| `6.json` | `53CD92CFDFCD3826217ED5C093EC8F639EEDF45FE0F2A3AD56DE643EF75F6711` |

## Pruebas finales

Comandos ejecutados de forma serializada:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:domain:testDebugUnitTest :core:database:testDebugUnitTest :core:database:assembleDebugAndroidTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :core:database:connectedDebugAndroidTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 connectedQaAndroidTest
```

Resultado final:

- aplicación JVM: 41/41;
- base de datos JVM: 5/5;
- dominio JVM: 171/171;
- total JVM: 217/217;
- base de datos instrumentada: 65/65 en el Samsung;
- aplicación QA instrumentada: 169/169 en el Samsung;
- lint, APK de depuración y APK de pruebas QA: aprobados.

El permiso de alarmas exactas se concedió temporalmente sólo a
`com.blackatsystems.miguardia.qa` para recorrer las pruebas heredadas de alarmas.
Gradle retiró al finalizar tanto el paquete QA como su paquete de pruebas. El
paquete productivo `com.blackatsystems.miguardia` y sus datos no fueron abiertos,
reinstalados ni modificados.

## Incidentes encontrados durante la verificación

La primera ejecución de dominio encontró que un historial podía volver a usar
la misma definición y el mismo patrón en más de una revisión. La reconstrucción
suponía erróneamente una sola aparición; se corrigió para aceptar repeticiones
coherentes y se agregó la prueba de regresión.

La primera corrida física de base de datos pasó 57 de 64 pruebas. Cinco fallos
eran expectativas históricas que todavía indicaban Room v5 o no agregaban la
nueva migración al reabrir. Los otros dos comprobaban el estado de claves
foráneas en la conexión cruda de `MigrationTestHelper`; esa propiedad pertenece
a cada conexión. Se actualizaron las expectativas antiguas y la prueba de
restricciones activa las claves foráneas antes de provocar los rechazos. La
conexión real creada por `MiGuardiaDatabase.build()` conserva su comprobación
independiente con claves foráneas activas.

Después de esos ajustes se repitieron las 64 pruebas completas y pasaron. La
revisión independiente posterior encontró un P2: una copia dañada creada con
claves foráneas apagadas podía contener filas sin padre, y la consulta por
relaciones no llegaba a verlas cuando tampoco existía una raíz. Se agregó una
comprobación reactiva de huérfanos para lecturas y observaciones, más una prueba
que cierra Room, inyecta tres filas inválidas mediante SQLite externo y vuelve a
abrir la base. Los primeros montajes de esa prueba fallaron antes de validar
producción por un retorno de JUnit, por intentar apagar claves dentro del grupo
de conexiones de Room y por no haber materializado todavía el archivo de la
base. Se corrigió el montaje y la batería final pasó 65/65.

Una segunda pasada independiente comprobó que la lectura ya era segura, pero
detectó que la creación inicial todavía podía intentar escribir una raíz encima
de filas huérfanas. La misma comprobación se agregó dentro de la transacción de
creación y la prueba confirma que la operación falla sin insertar ninguna raíz.
Después del ajuste volvieron a pasar 65/65 de Room, la batería global y la
instrumentación completa de la aplicación.

La revisión independiente final confirmó el cierre de ambos puntos y no dejó
hallazgos P0, P1 ni P2 pendientes en este bloque.

Una primera corrida de la aplicación QA fue interrumpida antes de producir un
XML final; no se contabilizó. Se detuvo únicamente el proceso QA huérfano y se
repitió la batería desde cero hasta obtener 169/169.

## Revisión de seguridad y límites

- No se agregó ninguna dependencia, permiso, red, nube, cuenta o telemetría.
- No se usa `fallbackToDestructiveMigration`, `REPLACE`, `allowMainThreadQueries`
  ni una base en memoria de producción.
- No se persistieron todavía lugares V2, tipos, extras, disponibilidad,
  recurrencias, situaciones especiales, montos o reglas nocturnas.
- La estrategia se contrastó con la guía oficial de migraciones y pruebas de
  Room: <https://developer.android.com/training/data-storage/room/migrating-db-versions>.

## Pendiente habilitado

El próximo bloque debe definir lugares, tipos de trabajo y plantillas V2. Room
v6 deja esa decisión abierta deliberadamente: no congela relaciones nuevas con
`Objective`, `ScheduleCombination` ni las instantáneas históricas hasta que su
contrato esté cerrado.
