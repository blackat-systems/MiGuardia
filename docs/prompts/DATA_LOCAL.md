# Prompt maestro de dependencia — DATA LOCAL, incremento 1

> **HISTÓRICO V1 — NO EJECUTAR.** Room v1 fue el comienzo; la base sellada ya es
> Room v5. Ver `docs/prompts/README.md`.

> Estado: implementado, integrado y verificado por MAIN el 2026-08-13
>
> Proyecto: MiGuardia
>
> Dependencia: DATA LOCAL
>
> Fecha: 2026-08-13

## 0. Rol y autoridad

Sos la dependencia especializada **DATA LOCAL** de MiGuardia. Tu trabajo será implementar el primer almacenamiento relacional local sobre la base Android preparada por MAIN.

Antes de planificar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
4. este prompt;
5. todos los archivos existentes de `core/domain`, `core/database` y las pruebas relacionadas.

Jerarquía: una instrucción explícita actual de Joaquin, luego el prompt maestro, luego `AGENTS.md`, luego el ADR y este documento, y finalmente el código existente.

No redefinas el producto. Si detectás un conflicto o una regla funcional faltante, detené solo esa parte, explicá el caso en español y devolvelo a MAIN con una recomendación. No inventes reglas silenciosamente.

## 1. Objetivo del incremento

Entregar una capa de datos local mínima, compilable y probada que permita a futuros incrementos:

- guardar y observar objetivos;
- guardar y observar combinaciones exactas objetivo+horario con su color;
- guardar guardias con instantáneas históricas;
- guardar estados diarios explícitos `F` y `?`;
- guardar períodos de carpeta médica sin imágenes;
- consultar el contenido de un intervalo mensual;
- modificar datos de manera transaccional sin migraciones destructivas.

No hay interfaz de usuario en este encargo.

## 2. Entradas que MAIN debe haber preparado

Antes de empezar deben existir y compilar:

- módulos `:core:domain` y `:core:database`;
- catálogo de versiones con Room 2.8.4 y KSP2 estable compatible;
- contratos de dominio iniciales o marcadores claros donde implementarlos;
- wrapper Gradle 9.5.0 y AGP 9.3.0;
- `compileSdk/targetSdk 37`, `minSdk 26`, JBR 25 y compatibilidad de código Java 17.

Si falta alguna entrada, informalo a MAIN. No reemplaces versiones, no desactives Kotlin incorporado y no cambies la estructura Gradle por iniciativa propia.

## 3. Alcance funcional congelado

### Objetivo

Debe representar:

- identificador UUID;
- nombre completo obligatorio;
- abreviatura obligatoria de 2 a 5 caracteres, normalizada en mayúsculas;
- dirección manual opcional;
- nota general opcional;
- estado activo u oculto;
- instantes técnicos de creación y última modificación.

La abreviatura es exclusiva del objetivo. La base debe proteger su unicidad sin depender solo de la interfaz.

### Combinación objetivo+horario

Cada combinación exacta es independiente y debe representar:

- identificador UUID;
- referencia al objetivo;
- hora local exacta de inicio;
- hora local exacta de fin;
- color ARGB propio;
- puesto predeterminado opcional si MAIN ya lo incluyó en el contrato;
- estado activo u oculto;
- instantes técnicos de creación y última modificación.

La unicidad debe impedir duplicar dentro de un objetivo la misma pareja inicio+fin. El cruce de medianoche se obtiene porque el fin local no es posterior al inicio local, pero toda guardia concreta debe guardar instantes reales de inicio y fin; nunca se calculan guardias persistidas usando solo texto.

### Guardia

Debe permitir más de una guardia en una misma fecha. No agregues un índice único por fecha.

Cada guardia inicial debe persistir como mínimo:

- identificador UUID;
- instante de inicio y fin en milisegundos Unix;
- identificador IANA de zona, inicialmente `America/Argentina/Cordoba`;
- fecha local de inicio ISO-8601 para consultar y atribuir el mes;
- instantánea de nombre y abreviatura del objetivo;
- instantánea de dirección si el contrato de dominio la contempla;
- instantánea de hora local de inicio y fin;
- instantánea de color ARGB;
- puesto de esa carga, opcional;
- estado temporal/persistente inicial definido por MAIN;
- instantes técnicos de creación y última modificación.

La guardia histórica no debe depender mediante clave foránea de que la plantilla siga existiendo. Puede conservar un identificador de origen informativo solo si no impide eliminar u ocultar plantillas.

Validaciones mínimas de persistencia:

- fin estrictamente posterior al inicio;
- zona válida;
- fecha local inicial coherente con el instante y la zona;
- abreviatura y horas de instantánea no vacías;
- no truncar los valores ARGB.

La advertencia por descanso menor a 12 horas y la confirmación de una segunda guardia son reglas de aplicación, no restricciones SQL bloqueantes. La base debe permitir ambas realidades una vez confirmadas por capas superiores.

### Estado diario explícito

Persistir únicamente estados explícitos:

- `DAY_OFF` para `F`;
- `UNDEFINED` para `?` cuando el usuario lo marque expresamente.

Un día sin fila sigue siendo visualmente `?`, pero no cuenta como `?` explícito ni como franco. Debe haber como máximo un estado diario explícito por fecha local.

No bloquees mediante esquema la coexistencia excepcional con una guardia; esa política se resolverá en repositorio/caso de uso y debe poder evolucionar sin destruir datos.

### Carpeta médica

Representar:

- identificador UUID;
- fecha local inicial y final inclusivas;
- nota opcional privada;
- instantes técnicos de creación y última modificación.

La fecha final no puede ser anterior a la inicial. No existe campo para certificado, foto o archivo adjunto.

## 4. Diseño técnico requerido

- Room 2.8.4 con KSP2; no kapt.
- Esquema versión 1 exportado a JSON y versionado.
- Entidades de base separadas de los modelos de dominio.
- Mapeadores explícitos entre entidades y dominio.
- DAO internos a `core:database`; otras capas acceden por contratos de repositorio.
- Lecturas observables mediante `Flow`.
- Escrituras puntuales mediante funciones `suspend`.
- Operaciones compuestas con `@Transaction` o `withTransaction`.
- Consultas mensuales por límites de fecha/instante, sin formatear visualmente fechas dentro de SQL.
- Índices justificados para abreviatura, objetivo+horario y fecha local de guardias.
- Claves foráneas e índices correspondientes sin cascadas que borren historia.
- No usar `fallbackToDestructiveMigration`, `allowMainThreadQueries` ni base en memoria en producción.
- La creación de UUID y timestamps debe poder controlarse desde pruebas; no ocultar llamadas no deterministas en mapeadores.

El módulo puede usar Room, SQLite y coroutines. No agregues Hilt, red, serializadores, cifrado, nube, analítica ni otra dependencia de producción.

## 5. Contratos esperados

Respetá los nombres existentes definidos por MAIN. Si todavía no existen, proponé interfaces equivalentes y pedí aprobación antes de modificar `core/domain`.

Como capacidades, deben existir:

- observar/listar objetivos activos y todos los objetivos;
- obtener objetivo por ID;
- crear, actualizar, ocultar y eliminar cuando el contrato lo permita;
- observar/listar combinaciones por objetivo y recientes si MAIN ya definió cómo registrar uso;
- obtener combinación por ID;
- crear y actualizar combinaciones;
- observar guardias cuya fecha local inicial esté dentro de un rango inclusivo;
- obtener guardia por ID;
- insertar, actualizar y eliminar una guardia;
- observar estados diarios dentro de un rango;
- establecer o limpiar el estado explícito de una fecha;
- observar carpetas médicas que intersecten un rango;
- crear, actualizar y eliminar una carpeta médica.

No implementes todavía:

- cálculo de horas, nocturnidad, feriados o extras;
- advertencia de descanso;
- selección múltiple o políticas de sobrescritura;
- novedades y cambios formales de objetivo/horario;
- fotos;
- feriados;
- notificaciones, widget o clima;
- informes, copias o restauración;
- remuneración;
- preferencias DataStore;
- cifrado o bloqueo.

## 6. Archivos permitidos

Podés crear o modificar únicamente:

- `core/database/**`;
- pruebas de `core/database/**`;
- esquemas Room dentro de `core/database/schemas/**`;
- catálogo/versiones Gradle solo si MAIN dejó marcadores explícitos para Room/KSP;
- `core/domain/**` exclusivamente si el contrato exacto fue previamente autorizado por MAIN;
- documentación técnica propia del módulo si es necesaria.

No toques:

- `AGENTS.md`;
- `docs/PROMPT_MAESTRO_MAIN.md`;
- otros ADR;
- `app/**` ni interfaz Compose;
- archivos privados ajenos al módulo;
- cronogramas reales;
- configuración de Git o secretos.

Si necesitás salir de estos límites, pedí autorización a MAIN antes de editar.

## 7. Pruebas obligatorias

Usá datos totalmente ficticios. Como mínimo demostrar:

1. abreviaturas normalizadas y únicas;
2. dos horarios distintos del mismo objetivo conservan colores distintos;
3. una combinación exacta duplicada es rechazada de forma controlada;
4. una guardia 19:00–07:00 conserva instantes reales y fecha local inicial;
5. una guardia del 31 que termina el mes siguiente se consulta por el mes de inicio;
6. pueden persistirse dos guardias el mismo día;
7. eliminar u ocultar una plantilla no altera la instantánea histórica de una guardia;
8. un día sin estado persistido se distingue de `UNDEFINED` explícito;
9. `DAY_OFF` se cuenta solo cuando existe la fila explícita;
10. una carpeta médica puede cruzar fin de mes y aparece al consultar cualquiera de los rangos intersectados;
11. una carpeta médica con fin anterior al inicio es rechazada;
12. cerrar y reabrir una base de prueba conserva los datos;
13. el esquema exportado coincide con la versión 1;
14. ninguna API permite consultas en el hilo principal por configuración accidental.

Usá pruebas JVM cuando alcancen y pruebas instrumentadas para Room sobre Android cuando sean necesarias. No declares instrumentación aprobada si no fue ejecutada realmente.

## 8. Criterios de aceptación

El encargo está terminado solo si:

- compila con `gradlew.bat`;
- pasan las pruebas unitarias de los módulos afectados;
- pasan las pruebas Room/instrumentadas relevantes o queda documentado con precisión qué requiere dispositivo;
- no hay migración destructiva;
- el esquema versión 1 está exportado y revisable;
- el diff está limitado al alcance;
- no hay datos reales, secretos, rutas privadas ni contenido sensible;
- no quedan reglas de producto inventadas;
- se entrega a MAIN un resumen de archivos, decisiones, comandos ejecutados y resultados.

No hagas commit, push, merge ni abras otra tarea salvo instrucción explícita de Joaquin o MAIN.

## 9. Entrega a MAIN

Al finalizar, informá en español claro:

- qué quedó funcionando;
- qué archivos cambiaste;
- qué pruebas ejecutaste y su resultado exacto;
- qué no pudiste verificar;
- conflictos o decisiones que MAIN deba resolver;
- cualquier dependencia nueva realmente utilizada y su justificación.

MAIN revisará el diff, ejecutará la batería completa y decidirá la integración. Tu resultado no sustituye esa revisión.

## 10. Contratos autorizados por MAIN — 13 de agosto de 2026

MAIN autoriza crear en `core/domain`:

- modelos `Objective`, `ScheduleCombination`, `Shift`, `ExplicitDayStatus` y `MedicalLeave`;
- contratos separados `ObjectiveRepository`, `ScheduleCombinationRepository`, `ShiftRepository`, `ExplicitDayStatusRepository` y `MedicalLeaveRepository`;
- errores de dominio controlados y distinguibles para abreviatura duplicada, combinación objetivo+horario duplicada y datos inválidos;
- identificadores UUID e instantes recibidos explícitamente, sin generarlos dentro de modelos o mapeadores;
- tipos fuertes de `java.time` en dominio y conversiones explícitas a los tipos persistidos de Room;
- instantánea histórica de dirección dentro de `Shift`;
- un estado persistido de guardia con valores `PLANNED`, `CANCELLED` y `ABSENT`, cuyo valor inicial es `PLANNED`.

Mientras una guardia permanezca `PLANNED`, los estados visuales próxima, en curso y completada se derivan de sus instantes y del reloj; no se persisten. El ciclo de novedades no se implementa en este incremento, aunque los valores persistidos reservados deben poder conservarse y mapearse.

No incluir todavía puesto predeterminado en `ScheduleCombination`. El puesto opcional pertenece a cada carga `Shift`. No agregar registro de uso reciente hasta que MAIN defina su contrato.
