# ADR 0001: base técnica y arquitectura inicial

- Estado: aceptada
- Fecha: 2026-08-13
- Autoridad: MAIN, de acuerdo con `AGENTS.md` y `docs/PROMPT_MAESTRO_MAIN.md`

## Contexto

MiGuardia comienza desde un repositorio sin proyecto Android. La aplicación será Android, local y privada, con lógica temporal importante y una interfaz basada en Jetpack Compose. La base debe permitir trabajar por dependencias en tareas separadas sin convertir desde el inicio cada responsabilidad conceptual en un módulo Gradle.

Las decisiones de producto ya están congeladas en el prompt maestro. Este ADR decide únicamente una base técnica reversible.

## Decisión

### Herramientas y plataforma

| Elemento | Decisión inicial |
|---|---|
| Lenguaje | Kotlin con soporte incorporado de AGP 9 |
| Interfaz | Jetpack Compose y Material 3 |
| Android Gradle Plugin | 9.3.1 |
| Gradle Wrapper | 9.5.0 |
| JDK de compilación | JBR 25 incluido en Android Studio, generando código compatible con Java 17 |
| `compileSdk` | 37 |
| `targetSdk` | 37 |
| `minSdk` | 26 |
| Compose BOM | `2026.08.00`, canal estable |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Navegación | Navigation 3, línea estable 1.1.6, cuando exista más de un destino real |
| Base relacional | Room 2.8.4 |
| Preferencias | DataStore 1.2.1 |

Se usarán versiones fijas en un catálogo Gradle. No se usarán rangos dinámicos ni versiones alpha, beta o RC en producción salvo una decisión posterior documentada.

AGP 9 habilita Kotlin incorporado. No se aplicará `org.jetbrains.kotlin.android`. El plugin de Compose usará Kotlin 2.3.21, versión indicada por la configuración oficial vigente. Para Room se usará KSP2, no kapt, y su compatibilidad se comprobará mediante un build real antes de aceptar el módulo de datos.

`minSdk 26` permite usar `java.time` de forma nativa. Esto evita introducir desugaring solo para bajar hasta API 23 y reduce diferencias en una aplicación donde fechas, zonas e intervalos son críticos. Bajar el mínimo se podrá reevaluar con datos reales de dispositivos antes de publicar.

### Estructura Gradle inicial

Se comenzará con tres módulos:

- `:app`: `Application`, actividad única, navegación y superficies Compose;
- `:core:domain`: modelos y contratos compartidos, sin imports de Android aunque se compile como biblioteca Android para compartir el Kotlin incorporado de AGP;
- `:core:database`: Room, entidades persistentes, DAO, mapeos y repositorios locales.

Las funcionalidades se organizarán primero por paquetes claros. Solo se creará un nuevo módulo Gradle cuando exista una frontera estable, beneficio de pruebas o necesidad real de aislar dependencias. Los quince módulos conceptuales del producto no implican quince módulos Gradle.

### Capas y flujo de datos

- La interfaz seguirá flujo unidireccional: eventos hacia el `ViewModel`, estado inmutable hacia Compose.
- Compose no accederá directamente a Room, DataStore ni archivos.
- Los repositorios serán la frontera de la capa de datos y expondrán `Flow` para observación y funciones `suspend` para operaciones puntuales.
- Room será la fuente de verdad de los datos relacionales locales.
- Se agregarán casos de uso en la capa de dominio cuando una regla sea reutilizada o suficientemente compleja; no se crearán envoltorios vacíos por rutina.
- La inyección de dependencias será manual al comienzo. No se incorpora Hilt mientras el grafo sea pequeño y explícito.

### Fechas y horas

- La lógica usará `java.time` y no cadenas visuales para calcular.
- La zona funcional inicial será `America/Argentina/Cordoba`.
- Reloj y zona se inyectarán en la lógica que dependa del momento actual para permitir pruebas deterministas.
- Una guardia persistirá inicio y fin como instantes (`epochMillis`) junto con el identificador de zona usado y la fecha local inicial indexable.
- La fecha local inicial determina el mes base de la guardia; las clasificaciones especiales se calculan mediante intersecciones de intervalos reales.
- La base persistirá instantáneas históricas de los datos visuales y operativos de cada guardia. No dependerá de la plantilla vigente para reconstruir el pasado.

### Persistencia y migraciones

- No se usará `fallbackToDestructiveMigration`.
- Los esquemas JSON exportados por Room se versionarán en Git.
- Cada cambio de versión tendrá migración y prueba antes de integrarse.
- La versión 1 contendrá solo las tablas necesarias para objetivos, combinaciones objetivo-horario, guardias, estados diarios y carpetas médicas. Fotos, novedades, feriados, escalas y otras áreas agregarán sus tablas mediante migraciones cuando corresponda.
- Identificadores internos serán UUID representados como texto. Fechas locales se persistirán en ISO-8601 y los instantes como milisegundos Unix.

### Privacidad

- El proyecto base no solicitará permisos ni declarará acceso a internet.
- No habrá analítica, telemetría, rastreadores ni servicios externos.
- Las pruebas usarán únicamente datos ficticios.
- Los repositorios y errores no registrarán contenido de cronogramas, notas, direcciones ni datos personales.

## Alternativas consideradas

### `minSdk 23` con desugaring

Amplía compatibilidad, pero agrega otra transformación y posibles diferencias para `java.time`. Se pospone hasta conocer una necesidad real.

### Room 3.0.1

Es estable y moderno, pero su cambio principal apunta a Kotlin Multiplatform, fuera del alcance de V1, y fue publicado hace pocas semanas. Room 2.8.4 tiene una superficie Android más madura y suficiente para MiGuardia.

### Un solo módulo Gradle

Reduce configuración, pero deja débiles las fronteras entre interfaz, contratos y almacenamiento, precisamente las áreas que se trabajarán e integrarán por separado.

### Un módulo Gradle por funcionalidad conceptual

Aísla más, pero introduce sobreingeniería y tiempo de compilación antes de conocer fronteras estables.

### Hilt desde el primer build

Automatiza el grafo, pero añade procesamiento y abstracción sin beneficio suficiente para el grafo inicial. La decisión puede revisarse si la composición manual deja de ser clara.

## Consecuencias

- La base queda preparada para trabajo fraccionado con contratos claros.
- API 25 y anteriores no estarán soportadas inicialmente.
- El proyecto adoptará APIs vigentes de Android 17 y deberá probarse tanto en el S25 Ultra/API 36 como en el emulador API 37 para cambios de plataforma.
- Elegir Room 2 implica evaluar una migración de biblioteca en el futuro, pero evita asumir hoy el costo de una tecnología orientada a plataformas que MiGuardia no soporta.
- El wrapper verifica la distribución Gradle 9.5.0 mediante el SHA-256 oficial antes de usarla.
- La firma `debug` usa el keystore local ignorado cuando existe y conserva la firma estándar de Android como alternativa, de modo que una copia limpia no dependa de un secreto privado del equipo de MAIN.
- No se instala un segundo JDK: el JBR 25 existente ejecuta Gradle 9.5 y compila con `sourceCompatibility` y `targetCompatibility` 17.

## Fuentes oficiales consultadas

- Android Gradle Plugin 9.3: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Compatibilidad entre Android Studio y AGP: https://developer.android.com/build/releases/about-agp
- Kotlin incorporado en AGP 9: https://developer.android.com/build/migrate-to-built-in-kotlin
- Android 17 SDK: https://developer.android.com/about/versions/17/setup-sdk
- Android 17 Platform Stability: https://developer.android.com/about/versions/17/release-notes
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Arquitectura recomendada: https://developer.android.com/topic/architecture/recommendations
- Room 2.8.4: https://developer.android.com/jetpack/androidx/releases/room
- Room 3: https://developer.android.com/jetpack/androidx/releases/room3
- DataStore: https://developer.android.com/jetpack/androidx/releases/datastore
- Navigation 3: https://developer.android.com/jetpack/androidx/releases/navigation3
- Compatibilidad Java de Gradle: https://docs.gradle.org/current/userguide/compatibility.html
- `java.time` en Android: https://developer.android.com/reference/java/time/LocalDate
