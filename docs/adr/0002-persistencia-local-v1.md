# ADR 0002: persistencia local versión 1

- Estado: aceptada
- Fecha: 2026-08-13
- Autoridad: MAIN, después de revisar la entrega de DATA LOCAL

## Contexto

MiGuardia necesita conservar localmente objetivos, combinaciones objetivo+horario, guardias históricas, estados diarios explícitos y carpetas médicas. Los datos deben poder observarse de forma reactiva, sobrevivir al cierre de la aplicación y mantener el pasado aunque una plantilla se oculte o elimine.

## Decisión

- Room 2.8.4 será la fuente de verdad relacional local.
- El esquema inicial tendrá versión 1 y cinco tablas: `objectives`, `schedule_combinations`, `shifts`, `explicit_day_statuses` y `medical_leaves`.
- El esquema JSON exportado se versionará y todo cambio posterior exigirá migración y pruebas; no se habilitarán migraciones destructivas.
- Entidades y DAO serán internos a `core:database`. El acceso público se realizará mediante `LocalDataStore` y los contratos definidos en `core:domain`.
- Los repositorios expondrán `Flow` y operaciones `suspend`. Se fija `kotlinx-coroutines-core` 1.11.0 como dependencia de API de `core:domain`, porque `Flow` forma parte de sus contratos.
- Los UUID se persistirán como texto, las fechas locales como ISO-8601 y los instantes como milisegundos Unix.
- Una guardia conservará instantáneas de objetivo, abreviatura, dirección, horario, color y puesto. Sus referencias de origen serán informativas y no tendrán claves foráneas que permitan alterar o borrar la historia.
- El estado persistido inicial de una guardia será `PLANNED`; también se reservan `CANCELLED` y `ABSENT`. Próxima, en curso y completada se derivarán del reloj mientras permanezca `PLANNED`.
- Eliminar un objetivo eliminará sus combinaciones dentro de una transacción, pero nunca sus guardias históricas.
- Un día sin fila seguirá siendo visualmente indefinido, pero se distinguirá de una fila `UNDEFINED` creada explícitamente.

## Dependencia de corrutinas

`kotlinx-coroutines-core` es mantenida por JetBrains y usa licencia Apache 2.0. Se incorpora porque los contratos observables requieren `Flow`; reemplazarla por callbacks propios duplicaría una abstracción estándar y complicaría cancelación, composición y pruebas. Solo se agrega el artefacto `core`: este incremento no necesita `Dispatchers.Main` ni `kotlinx-coroutines-android`.

## Consecuencias

- La interfaz y futuros casos de uso no dependen de Room ni de SQLite.
- La historia de guardias sigue siendo legible después de modificar o eliminar plantillas.
- Los tipos fuertes de `java.time` y los valores explícitos de UUID e instantes mantienen las pruebas deterministas.
- Nuevas áreas como novedades, fotos, feriados y remuneración deberán ampliar el esquema mediante migraciones no destructivas.

## Fuentes oficiales

- Room: https://developer.android.com/jetpack/androidx/releases/room
- Kotlin Flow: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/
- Proyecto y licencia de kotlinx.coroutines: https://github.com/Kotlin/kotlinx.coroutines
