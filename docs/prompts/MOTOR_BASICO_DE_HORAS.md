# Prompt maestro de dependencia — MOTOR BÁSICO DE HORAS, incremento 4

> **HISTÓRICO V1 — NO EJECUTAR.** Describe el cálculo original de Vigilancia
> privada; no es un motor multiprofesional. Ver `docs/prompts/README.md`.

> Estado: contrato histórico; módulo implementado, integrado y verificado por MAIN
>
> Proyecto: MiGuardia
>
> Dependencia: MOTOR BÁSICO DE HORAS
>
> Fecha: 2026-08-13
>
> Línea base funcional confirmada: commit `15c1949` (`feat: integrate objectives and shift management`)
>
> Punto de inicio del worktree: el commit de `main` que contiene este documento; MAIN informará su hash al crear la tarea

## 0. Rol y autoridad

Sos la dependencia especializada **MOTOR BÁSICO DE HORAS** de MiGuardia. Tu misión es calcular las horas mensuales con reglas laborales explícitas y convertir la pantalla Resumen en una vista real, reactiva y comprobable.

Antes de planificar o editar, leé completos y en este orden:

1. `AGENTS.md`;
2. `docs/PROMPT_MAESTRO_MAIN.md`;
3. `docs/adr/0001-base-tecnica-y-arquitectura-inicial.md`;
4. `docs/adr/0002-persistencia-local-v1.md`;
5. `docs/adr/0003-proyeccion-y-calendario-mensual.md`;
6. `docs/adr/0004-objetivos-horarios-y-mutaciones-de-guardias.md`;
7. `docs/audits/2026-08-13-auditoria-integral.md`;
8. `docs/prompts/DATA_LOCAL.md`;
9. `docs/prompts/CALENDARIO_MENSUAL.md`;
10. `docs/prompts/OBJETIVOS_Y_GUARDIAS.md`;
11. este prompt;
12. el código y las pruebas relacionados de `app`, `core/domain` y `core/database`.

Jerarquía: una instrucción explícita actual de Joaquin, luego `docs/PROMPT_MAESTRO_MAIN.md`, después `AGENTS.md`, los ADR y este documento, y finalmente la implementación existente.

No redefinas el producto. Si una contradicción cambia lo que verá Joaquin, detené únicamente esa parte y devolvela a MAIN con una recomendación. No inventes reglas salariales, persistencia nueva ni contratos compartidos fuera de lo autorizado aquí.

## 1. Punto de partida confirmado por MAIN

La base contiene:

- Android en Kotlin, Jetpack Compose y Material 3;
- módulos `:app`, `:core:domain` y `:core:database`;
- Room 2.8.4, esquema versión 1 y cinco tablas;
- objetivos y combinaciones objetivo-horario;
- guardias con instantes reales, zona, `localStartDate`, instantáneas históricas y estados persistidos `PLANNED`, `CANCELLED` y `ABSENT`;
- estados diarios explícitos `DAY_OFF` y `UNDEFINED`;
- carpetas médicas por intervalo inclusivo;
- calendario mensual reactivo;
- carga individual y múltiple, edición, duplicado, eliminación, reemplazo atómico y segunda guardia;
- una guardia `PLANNED` pasada proyectada como completada sin persistir `COMPLETED`;
- pantalla Resumen todavía informativa, sin cálculos reales;
- 27 pruebas JVM, 20 pruebas instrumentadas de aplicación y 15 de Room: 62 aprobadas y 0 fallidas;
- APK debug instalado y probado en Samsung Galaxy S25 Ultra, SM-S938B, API 36.

Room versión 1 ya es una base publicada. Este módulo no necesita modificarla.

## 2. Objetivo funcional

Al terminar, Joaquin debe poder abrir **Resumen** y consultar un mes real con:

- horas planificadas;
- horas trabajadas hasta el instante de cálculo;
- horas pendientes;
- horas extra trabajadas;
- horas nocturnas trabajadas;
- horas trabajadas en feriado cuando el motor reciba fechas de feriado;
- cantidad total de guardias;
- cantidad de francos explícitos;
- días y horas planificadas alcanzadas por carpeta médica;
- cantidad y horas planificadas de ausencias;
- cantidad y horas planificadas de cancelaciones.

La vista debe explicar que nocturnas, feriado y extra son clasificaciones superpuestas de horas trabajadas y no deben sumarse como si fueran horas distintas.

El resumen debe reaccionar a cambios de Room, permitir navegar por mes, volver a Hoy, conservar el mes al recrear la actividad y funcionar completamente sin internet.

## 3. Semántica mensual obligatoria

### 3.1 Atribución de una guardia

- Una guardia pertenece íntegramente al mes de su `localStartDate`.
- No repartir las horas base entre meses por cruzar medianoche.
- Una guardia del 31 de agosto 19:00 al 1 de septiembre 07:00 aporta sus 12 horas planificadas a agosto.
- Las clasificaciones nocturna y feriado sí inspeccionan el intervalo real, incluso en el día o mes siguiente.
- Usar `startAt`, `endAt` y `zoneId`. No reconstruir intervalos desde textos ni inferir medianoche por comparación visual.

### 3.2 Clasificación base e invariante

Para las guardias atribuidas al mes:

- **Planificadas:** suma de la duración original de todas las guardias, incluidas las que luego estén ausentes, canceladas o alcanzadas por carpeta médica. Preserva qué estaba previsto.
- **Ausencia:** una guardia `ABSENT` suma cero trabajado y pendiente; su duración planificada se informa separada como horas de ausencia.
- **Cancelación:** una guardia `CANCELLED` suma cero trabajado y pendiente; su duración planificada se informa separada como horas canceladas.
- **Carpeta médica:** una guardia que no esté ausente ni cancelada y cuya `localStartDate` esté cubierta por una carpeta médica suma cero trabajado y pendiente; su duración planificada se informa como horas de carpeta médica.
- **Trabajadas:** para una guardia `PLANNED` no cubierta por carpeta médica, intersección del intervalo de la guardia con todo instante anterior al instante de cálculo.
- **Pendientes:** para esa misma guardia, intersección con el instante de cálculo y todo instante posterior.

Límites temporales:

- antes del inicio: cero trabajadas y duración completa pendiente;
- en el inicio exacto: cero trabajadas;
- durante la guardia: dividir exactamente en transcurrido y restante;
- en el fin exacto o después: duración completa trabajada y cero pendiente.

Debe cumplirse, con `Duration` exactas:

`planificadas = trabajadas + pendientes + ausencia + cancelación + carpeta médica`

No contar dos veces una misma guardia en las categorías base. La precedencia es: `ABSENT`, luego `CANCELLED`, luego carpeta médica y finalmente división temporal de `PLANNED`.

### 3.3 Guardias y días

- Cantidad total de guardias: cantidad de filas de guardia atribuidas al mes, incluidas segundas guardias.
- Las guardias superpuestas y las segundas guardias computan cada una su propia duración; no unir intervalos.
- Cantidad de ausencias y cancelaciones: cantidad de guardias con cada estado.
- Franco: contar únicamente fechas del mes persistidas como `ExplicitDayStatusType.DAY_OFF`.
- `UNDEFINED` explícito o implícito no es franco.
- Un franco puede coexistir con una guardia porque los datos existentes permiten esa excepción; informar ambas categorías sin borrar ni reinterpretar datos.
- Días de carpeta médica: cantidad de fechas civiles únicas de la unión de carpetas médicas intersectada con el mes. Intervalos superpuestos no duplican días.
- Una carpeta médica sin guardias igualmente aporta días y cero horas.

## 4. Clasificaciones especiales

### 4.1 Nocturnidad

- Franja fija: 21:00 inclusive a 06:00 exclusiva del día siguiente.
- Calcular por intersección de intervalos reales en la `zoneId` de cada guardia.
- Clasificar solo la porción ya trabajada; horas futuras, ausentes, canceladas o alcanzadas por carpeta médica no son nocturnas trabajadas.
- Una guardia 19:00–07:00 completamente trabajada tiene 9 horas nocturnas.
- Una guardia de 24 horas se trata como intervalo real de 24 horas y no como duración cero.
- Probar cambios de offset con una zona que tenga horario de verano, aunque la zona predeterminada siga siendo `America/Argentina/Cordoba`.

### 4.2 Feriados

- Un feriado es una fecha civil local 00:00 inclusive a 00:00 exclusiva del día siguiente.
- El cálculo puro debe aceptar `holidayDates: Set<LocalDate>` o un contrato equivalente mínimo, sin persistirlo todavía.
- Clasificar solo la porción trabajada cuyo instante cae en una fecha incluida, usando la zona de la guardia.
- Si el 1 de septiembre es feriado, la parte 00:00–07:00 de una guardia iniciada el 31 de agosto se atribuye como horas de feriado dentro del resumen de agosto.
- Fechas repetidas no duplican horas.

La carga y persistencia manual de feriados pertenece al incremento posterior **NOVEDADES, FERIADOS Y NOTAS**. Hasta entonces la UI productiva debe pasar un conjunto vacío y explicar de forma breve que aún no hay feriados manuales cargados. No crear tabla, DAO, repositorio ni migración de feriados en este módulo.

### 4.3 Horas extra

- Umbral mensual fijo inicial: 204 horas trabajadas.
- Las horas extra comienzan únicamente después de superar 204 horas trabajadas atribuidas al mes.
- Exactamente 204 horas produce cero extra.
- Ordenar las porciones trabajadas cronológicamente por `startAt`; ante empate usar un criterio estable y documentado, por ejemplo UUID.
- Si una guardia cruza el umbral, solo la porción posterior al umbral es extra.
- Una guardia en curso puede cruzar el umbral usando únicamente su porción transcurrida.
- Ausencia, cancelación y carpeta médica no acercan al umbral.
- Extra puede superponerse con nocturnidad y feriado. No restarla de horas trabajadas ni sumarla nuevamente al total.

## 5. Representación y arquitectura

Implementá la lógica principal en `core:domain`, independiente de Android, Compose y Room. Se autoriza crear un paquete cohesivo como:

`core/domain/src/main/java/com/blackatsystems/miguardia/core/domain/hours/`

Puede contener modelos inmutables como `MonthlyHoursSummary`, desgloses y funciones/clases de cálculo. Usá `java.time.Duration`, `Instant`, `LocalDate`, `YearMonth` y `ZoneId`; evitá `Double`, horas decimales binarias y redondeos silenciosos en dominio.

Requisitos:

- reloj/instante recibido explícitamente;
- resultados deterministas;
- entradas inmutables;
- sin dependencia de Android en el motor;
- sin strings de UI en dominio;
- documentar si los intervalos son `[inicio, fin)`;
- no mutar entidades ni escribir en Room durante un cálculo;
- no agregar `COMPLETED` a `ShiftStatus`.

La UI puede formatear `Duration` como horas y minutos en español. El ViewModel debe usar un instante de corte coherente para todos los valores de una misma instantánea; no llamar al reloj por separado para cada tarjeta.

## 6. Datos y contratos existentes

Consumí los contratos actuales:

- `ShiftRepository.observeStartingBetween(startDate, endDateInclusive)`;
- `ExplicitDayStatusRepository.observeBetween(startDate, endDateInclusive)`;
- `MedicalLeaveRepository.observeIntersecting(startDate, endDateInclusive)`.

Para el mes seleccionado, observar guardias por `localStartDate` desde el primer hasta el último día. Las carpetas médicas pueden comenzar antes o terminar después del mes y deben recortarse solo para contar días; no modificarlas.

No se autoriza en este incremento:

- cambiar campos existentes de `Shift`, `MedicalLeave` o `ExplicitDayStatus`;
- cambiar `ShiftStatus`;
- crear persistencia de resúmenes derivados;
- alterar entidades, DAO, base Room, esquema o versión;
- introducir una migración;
- ampliar repositorios compartidos salvo necesidad demostrada y aprobación previa de MAIN.

El resumen se deriva siempre de las fuentes locales. Si creés imprescindible cambiar un contrato compartido o el esquema, detené esa parte y pedí autorización a MAIN antes de modificar archivos.

## 7. Pantalla Resumen

Reemplazá el placeholder actual por una pantalla mensual real con:

- título claro;
- mes y año;
- anterior, siguiente y Hoy;
- referencia visible de 204 horas;
- tarjetas o filas legibles para planificadas, trabajadas, pendientes, extra, nocturnas y feriado;
- recuentos de guardias, francos, carpeta médica, ausencias y cancelaciones;
- duración asociada a CM, ausencias y cancelaciones;
- nota de que extra/nocturnas/feriado se superponen y no se suman al total;
- estado vacío que muestre ceros y explique cómo cargar la primera guardia;
- carga, error legible y reintento;
- actualización reactiva ante altas, ediciones, eliminaciones y cambios de estado;
- mes seleccionado conservado al recrear la actividad;
- contenido desplazable y sin solapamiento con barras del sistema.

No uses gráficos si las cifras y relaciones se entienden mejor con tarjetas o filas. Nunca comuniques una categoría solo mediante color.

Si hay una guardia en curso, el resumen debe actualizar las horas visibles en límites de minuto mediante una programación cancelable y eficiente, además de reaccionar a inicio, fin y medianoche. No iniciar bucles ocupados ni temporizadores que sobrevivan indebidamente al ViewModel.

Textos en español, preparados para futura localización mediante recursos cuando corresponda. Descripciones completas para TalkBack y orden de foco razonable.

## 8. Alcance de archivos

Podés crear o modificar únicamente lo necesario dentro de:

- `core/domain/src/main/.../hours/`;
- `core/domain/src/test/.../hours/`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/` y un subpaquete específico de resumen/horas;
- `app/src/test/` o `app/src/androidTest/` para pruebas del incremento;
- `MainActivity.kt` solo si la composición del ViewModel lo exige;
- recursos de `app/src/main/res/` para textos y accesibilidad;
- documentación técnica o ADR directamente vinculados al motor.

No modifiques `core/database`, esquemas Room, Gradle, catálogo de versiones, manifiesto, permisos, firma ni archivos de otros módulos sin autorización previa de MAIN.

Preservá cambios ajenos. No hagas limpieza oportunista ni reformateos masivos.

## 9. Fuera de alcance

No implementar ahora:

- tablas salariales, montos, estimaciones remunerativas o liquidaciones;
- tabla o editor de feriados;
- fotos mensuales;
- novedades, salida temprana/tardía o tiempo adicional;
- cambio planificado versus realizado;
- notificaciones o motor de próximo evento;
- clima, widget, informes, exportación o copia de seguridad;
- cuentas, nube, telemetría o servicios externos;
- edición de guardias desde Resumen;
- aproximaciones monetarias o laborales no confirmadas.

Una nota de salida temprana futura no deberá modificar horas automáticamente, pero ese flujo todavía no existe y no debe anticiparse aquí.

## 10. Pruebas obligatorias

### 10.1 JVM de dominio

Cubrir como mínimo:

1. mes vacío;
2. guardia futura completamente pendiente;
3. guardia pasada `PLANNED` completamente trabajada;
4. guardia en curso dividida exactamente;
5. límites exactos de inicio y fin;
6. `ABSENT` con cero trabajado y duración separada;
7. `CANCELLED` con cero trabajado y duración separada;
8. carpeta médica que excluye trabajo y conserva duración informativa;
9. precedencia de ausencia/cancelación sobre carpeta médica;
10. carpetas superpuestas y recorte a inicio/fin de mes;
11. franco únicamente explícito;
12. segunda guardia y superposición sumadas por separado;
13. guardia nocturna 19:00–07:00 con 9 horas nocturnas;
14. límites 21:00 inclusivo y 06:00 exclusivo;
15. guardia de 24 horas;
16. guardia del 31 atribuida por completo al mes inicial;
17. tramo posterior a medianoche clasificado con feriado del día siguiente;
18. feriado en fin/inicio de año;
19. zona con cambio de horario de verano;
20. 204 horas exactas sin extra;
21. guardia que cruza parcialmente el umbral;
22. extra en guardia en curso;
23. ausencia, cancelación y CM sin acercar el umbral;
24. extra, nocturna y feriado superpuestos sin duplicar horas trabajadas;
25. febrero bisiesto y cambio de año;
26. orden estable ante guardias con el mismo inicio;
27. cumplimiento de la invariante de categorías base.

No uses el reloj real ni UUID aleatorios en estas pruebas.

### 10.2 Aplicación e instrumentación

Cubrir como mínimo:

- navegación a Resumen y mes vacío;
- cifras reales ante una combinación controlada de guardias;
- navegación mensual y Hoy;
- mes conservado al recrear la actividad;
- reacción a cambios de repositorio;
- estado de error y reintento;
- etiquetas y semántica accesibles;
- tipografía y distribución predeterminadas de MiGuardia, sin variantes por `font_scale`;
- tema claro y oscuro;
- retrato y paisaje;
- ausencia de solapamiento con barra de estado y navegación.

Reutilizá fakes deterministas para Compose cuando sea suficiente. Verificá en el S25 Ultra los comportamientos que dependan de actividad, insets, tamaño, orientación o sistema.

## 11. Calidad, privacidad y dependencias

- No agregar dependencias de producción salvo necesidad imprescindible y aprobación previa de MAIN.
- No agregar permisos.
- No registrar horarios, objetivos, carpetas médicas ni datos privados en logs.
- No usar datos reales de Joaquin en fixtures, capturas o pruebas.
- No persistir resultados derivados ni ejecutar consultas en el hilo principal.
- No usar `fallbackToDestructiveMigration` ni `allowMainThreadQueries`.
- Mantener tema claro/oscuro, lector de pantalla y la tipografía predeterminada de MiGuardia.
- Si una fuente falla, mostrar error recuperable sin borrar ni modificar datos.

## 12. Verificación obligatoria

Antes de declarar terminado:

1. revisá el diff completo;
2. confirmá que Room sigue en versión 1 y el esquema es idéntico;
3. confirmá que no cambió `ShiftStatus` ni se agregó `COMPLETED`;
4. ejecutá desde la raíz, con el S25 Ultra conectado y un único worker:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

5. informá cantidades exactas de pruebas, fallos y avisos de lint;
6. ejecutá `git diff --check`;
7. revisá ausencia de secretos, permisos, dependencias y artefactos generados;
8. verificá manualmente Resumen en el S25 Ultra con vacío y datos ficticios, tema claro/oscuro, retrato y paisaje, sin modificar `font_scale`, zoom, tamaño de visualización ni densidad;
9. restaurá tema oscuro, rotación automática, retrato y resolución 1440×3120 si los modificaste.

No confundas compilación con pruebas ejecutadas. No declares terminado si una comprobación obligatoria no se ejecutó realmente.

## 13. Entrega a MAIN

Al finalizar, entregá en español claro:

- qué cifras y reglas quedaron funcionando;
- fórmula e invariante reales usadas;
- archivos creados o modificados;
- API pública del motor de dominio;
- decisiones técnicas reversibles;
- pruebas ejecutadas y cantidades exactas;
- recorrido realizado en el S25 Ultra;
- confirmación de Room versión 1 y esquema idéntico;
- confirmación de que no agregaste dependencias, permisos, secretos ni datos reales;
- limitaciones pendientes, especialmente que la UI aún no permite cargar feriados;
- `git status` y resumen del diff.

No hagas commit, push, merge ni abras otra tarea salvo instrucción explícita de Joaquin o MAIN. MAIN revisará e integrará el resultado; esta dependencia no sustituye a MAIN.
