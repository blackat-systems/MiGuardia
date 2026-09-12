# Mapa maestro de MiGuardia 2.0

- Estado: fuente activa de producto
- Fecha: 2026-09-06
- Propietario del producto: Joaquin
- Alcance: explicar el producto aprobado y el orden de construcción

## 1. La idea en una frase

MiGuardia 2.0 es **una sola aplicación Android para organizar jornadas
laborales**, con un Calendario común y reglas de horas adaptadas a cuatro
sectores distintos: Vigilancia privada, Policía, Enfermería y Medicina.

No son cuatro aplicaciones ni cuatro perfiles simultáneos. Tampoco se fuerza
una misma regla laboral sobre los cuatro sectores.

## 2. De dónde parte

MiGuardia 1.0.0 fue la prueba interna y la base de código que ya resolvió el
caso de Vigilancia privada:

- calendario mensual;
- objetivos, horarios y guardias;
- francos, novedades, feriados, vacaciones y carpetas médicas;
- fotos privadas del cronograma;
- horas, próximo evento, notificaciones, clima y resumen mensual;
- datos locales, sin cuenta ni nube.

MiGuardia 2.0 continúa desde ese código y puede reutilizar todo componente que
siga siendo correcto. No existe, en cambio, una población usuaria ni datos 1.0
que deban migrarse: la experiencia 2.0 comienza como instalación limpia. El tag
`v1.0.0` se conserva como base técnica y evidencia, no como contrato de
compatibilidad de datos.

## 3. Los cuatro sectores exactos

El catálogo está cerrado:

1. Vigilancia privada;
2. Policía;
3. Enfermería;
4. Medicina.

Enfermería y Medicina son sectores independientes. No existe un sector
configurable llamado `Salud` y no existe la opción `Otro`.

## 4. Qué comparten

La base común de la aplicación incluye:

- el mismo Calendario mensual;
- lugares o servicios de trabajo;
- horarios y jornadas con inicio y fin reales;
- carga manual individual y múltiple;
- próximos eventos;
- notas, feriados, vacaciones, carpetas médicas y fotos;
- notificaciones, clima, widget, informes y copias;
- privacidad local y ausencia de cuentas o sincronización.

La interfaz puede cambiar palabras según el sector, pero reutiliza la misma
base técnica y las mismas garantías de datos.

## 5. Personalización sin fórmulas por profesión

El sector cambia palabras y ejemplos, pero no impone una forma universal de
trabajar. Cada persona configura:

- sus lugares de trabajo;
- sus tipos de trabajo;
- horarios exactos y colores;
- si utiliza una referencia de horas y de qué período;
- qué clases de trabajo extra ayudan a completar esa referencia;
- si utiliza disponibilidad y con qué nombre quiere verla;
- la franja nocturna y las reglas de feriado o fin de semana de cada lugar.

Una respuesta individual de un formulario sirve como evidencia, no como valor
predeterminado para todo el sector.

## 6. Piezas del producto

MiGuardia separa conceptos simples para no mezclar cálculo e interfaz:

| Pieza | Qué significa |
|---|---|
| Lugar de trabajo | Dónde trabaja la persona |
| Tipo de trabajo | Qué actividad realiza y cómo se muestra |
| Plantilla | Lugar + tipo + horario exacto + color |
| Plan recurrente | Regla que crea jornadas futuras concretas |
| Jornada | Lo planificado para una fecha |
| Horario real | Lo que finalmente ocurrió |
| Clase extra | Categoría elegida para tiempo adicional |
| Disponibilidad | Guardia pasiva, disponible para llamado o retén |
| Situación especial | Ausencia, carpeta, vacaciones, capacitación, cancelación, intercambio u otra |

Las jornadas guardan una fotografía histórica. Cambiar un lugar, plantilla,
plan o regla desde una fecha no reescribe el pasado.

## 7. Decisiones confirmadas

- MiGuardia 2.0 continúa sobre el código de 1.0 y protege el tag `v1.0.0`.
- No hay traspaso de datos, modo migrado ni activación V1→V2: toda experiencia
  2.0 comienza con configuración nueva.
- Existe una sola configuración laboral, con cambios desde una fecha concreta.
- Una instalación nueva elige primero el sector y entra al Calendario vacío.
- Todo trabajo activo suma al total trabajado.
- Las horas extras se declaran expresamente, poseen inicio y fin exactos y se
  muestran separadas sin dejar de integrar el total.
- Superar una referencia nunca crea horas extras automáticamente.
- Cada clase extra decide si ayuda a completar las horas requeridas.
- Al crear o cambiar una referencia, la persona elige desde qué fecha reinicia
  el conteo —por ejemplo hoy o el próximo lunes—. MiGuardia no prorratea la
  meta ni la aplica hacia atrás.
- Dos trabajos activos superpuestos se advierten y, si el usuario los conserva,
  ambos suman completos.
- La disponibilidad se informa aparte. El trabajo activo reemplaza sólo el
  tramo pasivo coincidente.
- No existen pausas descontables; un corte real se representa con dos jornadas.
- Nocturnidad, feriado y fin de semana son clasificaciones superpuestas: pueden
  coincidir, pero no duplican el total trabajado.
- Una jornada pertenece al día y mes donde comienza; las clasificaciones miran
  los instantes reales aunque cruce medianoche.
- MiGuardia no incorpora tablas salariales, montos ni liquidaciones.

## 8. Calendario y Resumen

El Calendario conserva una sola grilla mensual:

- en consulta, tocar un día abre sus detalles sin modificar datos;
- `Editar este día` permite cambiar únicamente esa fecha;
- `Editar calendario` abre el recorrido separado para una o varias fechas;
- una sola jornada muestra abreviatura y horario;
- varias jornadas muestran únicamente `2 turnos`, `3 turnos`, etc.;
- la tarjeta superior de la aplicación se despliega para mostrar todas las
  jornadas del día, incluidas las completadas.

El Resumen siempre muestra lo esencial y permite ordenar u ocultar detalles
desde `Personalizar resumen`. La persona personaliza la presentación, no las
fórmulas.

## 9. Investigación sectorial

Las respuestas se registran de forma anónima en `docs/sectores/`. La evidencia
disponible confirma diversidad de nombres y modalidades, por lo que no se crean
reglas universales. La investigación futura puede mejorar textos y ejemplos sin
bloquear la arquitectura personalizable aprobada.

## 10. Orden de construcción

```text
Reglas puras y pruebas
        ↓
Configuración local V2
        ↓
Lugares, tipos, plantillas y primera configuración
        ↓
Carga manual
        ↓
Edición y eliminación individual de jornadas
        ↓
Retiro del modo V1 antes de ampliar nuevamente la persistencia
        ↓
Planes recurrentes y edición del Calendario
        ↓
Horario real y extras exactas — cerrado
        ↓
Extras independientes y avance de horas — cerrado
        ↓
Guardias pasivas y disponibilidad — cerrado
        ↓
Calendario final y tarjeta superior desplegable — cerrado
        ↓
Resumen personalizable — cerrado
        ↓
Próximo evento y notificaciones — cerrado
        ↓
Auditoría integral del núcleo y compatibilidad Android — parcial histórica
        ↓
Pruebas cruzadas del núcleo V2 — cerradas
        ↓
Matriz Android 36/26/33 del núcleo — cerrada
        ↓
Repetición de auditoría integral — NÚCLEO APTO PARA SEGUNDA CAPA
        ↓
Widget de próximo evento — cerrado; Samsung API 36, API 26 y API 33 verdes
        ↓
Informes locales — cerrado; local y Samsung API 36 (33/33) verdes
        ↓
Copias y restauración locales — cerradas; local y Samsung API 36 verdes
        ↓
Bloqueo de acceso local — cerrado; local y Samsung API 36 (31/31) verdes
        ↓
Ayuda + simplificación + ubicación/clima por objetivo — cerrado e integrado en 95ebf531
        ↓
Integración y depuración de cambios de último momento — cerrada en 95ebf531
        ↓
Auditoría completa — CANDIDATO LOCAL AUDITADO; 712/712 JVM y 351/351 tareas
        ↓
Compatibilidad final — Samsung API 36 (42/42), Android 8/API 26
(Room 126/126; app 28/28) y Android 13/API 33
(Room 126/126; app 55/55) — cerrada
        ↓
Android 17/API 37 — Room 126/126 y app 176/176 — cerrada
        ↓
Checkpoint remoto de desarrollo — 924fd46 — no es publicación
        ↓
Portal beta privado + contrato de 10 claves — cerrado localmente en b66c1f7
        ↓
Página pública de MiGuardia — publicada en el Site existente, versión 10
        ↓
Acceso Android + opiniones a los 15 días — BLOQUE 25 INTEGRADO LOCALMENTE
        ↓
Página de aporte — bloque 26 publicado; HTTPS verificado, beta cerrada
        ↓
Botón visible Apoyar MiGuardia — cerrado localmente; QA dirigida 32/32
        ↓
Actualización sin perder datos → APK beta.1/code 2 → clave Release
        ↓
Firma + QA física → publicación web limitada → piloto
        ↓
Promoción a MiGuardia 2.0 estable → tag / Release / main
— todas son puertas separadas
```

Corrección de secuencia del 2026-08-27: Joaquin indicó que el bloque siguiente
era Calendario final y tarjeta superior; ese bloque ya quedó cerrado por MAIN.
Las capacidades comunes preservadas
—`F/?`, carpeta médica, vacaciones, feriados, notas y los estados internos de
ausencia/cancelación— continúan disponibles según su alcance actual. Un flujo
V2 ampliado de situaciones especiales o una consolidación adicional de horas
queda diferido y no bloquea este recorrido.

Actualización del 2026-08-28: la dependencia auditora ejecutó la batería local
y no reprodujo defectos P0/P1, pero devolvió `AUDITORÍA PARCIAL — NO CERRABLE`.
Faltan una fotografía transversal única, una carrera CAS real, una prueba
explícita de consultas sin escrituras y la matriz actual en Samsung API 36,
Android 8/API 26 y Android 13/API 33. En ese momento la segunda capa permanecía
cerrada hasta corregir esos huecos y repetir esta puerta. Joaquin indicó
preparar la dependencia `PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md`.

Actualización del 2026-08-29: las tres pruebas cruzadas quedaron integradas y
verificadas. La matriz actual pasó en Samsung API 36, Android 8/API 26 y
Android 13/API 33. La repetición integral devolvió
`NÚCLEO APTO PARA SEGUNDA CAPA` sin findings abiertos. La segunda capa queda
desbloqueada y su primer bloque es Widget.

Actualización posterior del 2026-08-29: MAIN recibió, auditó, corrigió y cerró
`WIDGET_DE_PROXIMO_EVENTO_V2.md`. La batería local y Samsung API 36 quedaron
verdes, sin Glance ni cambio de Room. Android 8/API 26 y Android 13/API 33 se
conservan como compatibilidad pendiente. Informes es el próximo bloque
recomendado.

Actualización de Informes del 2026-08-29: Joaquin autorizó continuar. MAIN
auditó las fuentes V2, fijó ADR 0034 y habilitó
`INFORMES_LOCALES_DE_JORNADAS_Y_HORAS_V2.md`. El bloque debe reutilizar la
misma fórmula de Horas y Resumen, generar PDF/XLSX local sin dependencia nueva
y mantener notas, nombre, puesto y fotos apagados por defecto. La
implementación quedaba pendiente en ese momento hasta que la única tarea
especialista pasara Puerta 0.

Actualización de cierre del 2026-08-29: MAIN recibió el handoff de Informes,
auditó y corrigió el candidato y repitió la batería contractual. PDF y XLSX se
generan localmente desde la misma verdad de Horas/Resumen, las inclusiones
privadas comienzan apagadas y la versión, las entidades, las migraciones y los
esquemas de Room V5 permanecen intactos. La matriz definitiva pasó en Samsung
API 36 con 33/33 casos —app 28 y Room 5—. Informes queda cerrado.

Actualización de Copias del 2026-08-29: Joaquin pidió preparar la dependencia y
decidió que toda recuperación debe preguntar antes de escribir. El usuario podrá
elegir `Combinar con mis datos` —sin sobreescritura silenciosa y con resolución
de conflictos— o `Reemplazar todo` —con vista previa y segunda confirmación—.
MAIN fijó el contrato técnico en ADR 0035 y habilitó
`COPIAS_Y_RESTAURACION_LOCALES_SEGURAS_V2.md`. La implementación todavía no
comenzó y Joaquin abrirá la única tarea especialista.

Actualización de cierre del 2026-09-01: MAIN recibió, auditó, corrigió y cerró
Copias y restauración locales seguras. El formato lógico cubre las 27 tablas de
Room V5, preferencias portables y fotografías opcionales; la recuperación
ofrece combinación consciente o reemplazo total con segunda confirmación. La
batería local pasó 603/603 pruebas JVM y 351/351 tareas. En Samsung API 36
pasaron 121 pruebas Room y una matriz segura de aplicación de 98 pruebas, además
de un recorrido real con SAF para crear, abrir, previsualizar y combinar una
copia cifrada ficticia. Room V5 y sus esquemas permanecen intactos. Bloqueo de
acceso local quedó como siguiente bloque recomendado.

Actualización de cierre de Bloqueo del 2026-09-01: MAIN recibió, auditó y
corrigió el candidato. La puerta opcional usa biometría fuerte o la credencial
segura del teléfono, nunca un PIN propio, y sus preferencias continúan fuera de
las copias. La batería global pasó 653/653 pruebas JVM y 351/351 tareas; la
matriz final pasó 31/31 en Samsung API 36. Room V5, sus 27 tablas y los esquemas
1–5 permanecen intactos; AndroidX Biometric 1.1.0 es la única dependencia
directa nueva. API 26/API 33 y la inspección visual OEM de Recientes continúan
pendientes. Ayuda y recorrido inicial 2.0 queda como siguiente bloque
recomendado.

Actualización del candidato compartido del 2026-09-02: la implementación de
Ayuda quedó en el mismo checkout que simplificaciones de formularios, ubicación
puntual para clima, clima por objetivo y Room V6. La guía conserva las
prioridades de recuperación, Bloqueo y configuración real y comienza sólo con
`V2Ready` y `WorkSetupSurface.NONE`. ADR 0037 fija su DataStore no portable y
ADR 0038 limita la ubicación a conversión consciente de una dirección o captura
aproximada y puntual de la ciudad actual.

Actualización de auditoría MAIN del 2026-09-03: el candidato combinado fue
revisado y corregido como una sola unidad. La QA autorizada en Samsung API 36
descubrió una incompatibilidad real de Fragment 1.2.5 con los launchers modernos
de permisos y un mensaje de ubicación que no finalizaba; ambos defectos quedaron
corregidos y cubiertos. La batería final posterior pasó 707/707 pruebas JVM,
lint sin errores y 351/351 tareas. Room V6 pasó 123/123 y la matriz dirigida de
permisos, configuración y clima pasó 30/30 en el Samsung. El bloque quedó
integrado en `95ebf531`.

Actualización de auditoría final del 2026-09-03: MAIN revisó la aplicación
completa, cerró defectos de concurrencia, arranque, Calendario, accesibilidad,
timeout y vocabulario, e integró un ícono adaptativo. La batería fresca pasó
712/712 JVM y 351/351 tareas; la revisión independiente no dejó findings P0–P3.
El resultado es un candidato local auditado. Samsung API 36 pasó la matriz final
afectada 42/42 y la revisión del ícono/Calendario.

Actualización de compatibilidad del 2026-09-03: Android 8/API 26 pasó Room
126/126 y aplicación 28/28; Android 13/API 33 pasó Room 126/126 y aplicación
55/55. El checkpoint técnico es
`3e0b0bcdcd7a8dfe856f4dd786ec5e936cc4cb37` y la evidencia durable quedó en
`docs/audits/2026-09-03-compatibilidad-api26-api33-candidato-local-v2.md`.
Android 17/API 37 cerró después su puerta con Room 126/126 y aplicación 176/176
sobre la imagen oficial. Espresso 3.7.0 quedó limitado a AndroidTest y no entra
al APK productivo. La ejecución detectó y corrigió un callback redundante al
perder foco en los campos horarios, que podía invalidar una revisión de
disponibilidad. El checkpoint técnico es
`0ef31e02d3b3fb1bb93e0ac94cb04302d6de7afb` y la evidencia durable quedó en
`docs/audits/2026-09-03-compatibilidad-api37-candidato-local-v2.md`. La alarma
exacta real y el reinicio físico continúan como puertas separadas.

Actualización de distribución del 2026-09-03: Joaquin decidió que Google Play
no será el canal de la primera prueba externa. MiGuardia se ofrecerá primero
como `2.0.0-beta.1`, código `2`, desde su sitio existente y con diez plazas. La
APK se descarga e instala antes de pedir dentro de la aplicación una clave de
acceso a la beta. A los quince días se exige completar una devolución honesta;
si falla Internet después de confirmarla, se guarda localmente y no se retienen
los datos laborales. La futura estable será `2.0.0` con un código mayor.

`Apoyar MiGuardia` es una entrada propia y visible del menú principal. Abre
por HTTPS una página externa de aporte voluntario y no habilita funciones,
evita feedback ni guarda pagos. El destino web y el proveedor deben existir
antes de incorporar el botón Android.

El push verificado hasta
`924fd46df018f5e14637dfe90a983fefdbf27af4` es sólo un checkpoint remoto de
desarrollo. La página pública `/miguardia` debe explicar primero las capacidades
reales, privacidad y límites del producto. Su único llamado de participación
beta queda al final y lleva al portal privado `/miguardia/beta`; el formulario,
las diez plazas y las claves no se duplican en la presentación pública.

Portal, página pública, acceso Android, feedback, aporte, actualizaciones,
preparación del APK, clave Release, firma, QA, publicación, promoción estable,
tag, GitHub Release y `main` son puertas secuenciales independientes. La página
pública quedó integrada localmente en `website/2799c47`, incluida la corrección
de foco comprobada por MAIN; esto no acredita publicación. El 2026-09-04 Joaquin
autorizó preparar el bloque 25, `ACCESO_A_LA_BETA_Y_OPINIONES_ANDROID_V2.md`.
MAIN integró esa implementación con correcciones contra el contrato API v1
el 2026-09-05: 749/749 JVM y 71/71 casos físicos Samsung sobre los APK finales.
No reejecutar la dependencia. No se aprovisionan servidores, no se emiten
claves productivas ni se prepara todavía la APK oficial.
La confianza QA permanece separada de Release.

El 2026-09-05 MAIN recibió, auditó y cerró el bloque 26,
`PAGINA_DE_APORTES_VOLUNTARIOS_WEB_V2.md`, en `website/eae7d54`. La página
bilingüe `/miguardia/apoyar` ofrece una salida consciente a Mercado Pago y
regreso sin aportar. MAIN repitió 64/64 pruebas, tipos, lint, base, build y QA
web; no encontró defectos de código abiertos. El 503 local del portal es de
configuración beta no disponible, no una regresión atribuible a la página.
No se cambiaron Android, campaña o claves ni se publicó. Zoom real 200 % y
traza independiente de red permanecen como límites explícitos de la auditoría.
La presentación del nombre `BlackcatSystems` no acredita titularidad o costos.
Esas puertas web se completaron posteriormente, como se registra a continuación.

Actualización de publicación y continuidad del 2026-09-05: Joaquin confirmó su
cuenta receptora y la aceptación de condiciones y comisiones, y autorizó la
publicación pública separada. MAIN publicó la presentación y Aportes en el Site
existente, versión 10, fuente `website/341ea0a`, y verificó 42/42 controles HTTPS.
`https://blackatsystems.com/miguardia/apoyar` está disponible; `/miguardia/beta`
es sólo un aviso de preparación y sus APIs no están publicadas. No se habilitaron
claves, campaña, D1/R2, APK ni pagos dentro de Android. El alojamiento puede
añadir su cookie técnica `__cf_bm`; no se promete ausencia absoluta de cookies.

Joaquin indicó continuar y MAIN preparó **27 — Apoyar MiGuardia** mediante
`APOYAR_MIGUARDIA_EN_EL_MENU_ANDROID_V2.md`. Agrega únicamente una entrada propia
en `Aplicación` que abre esa URL fija en el navegador, sin datos ni parámetros.
La implementación fue entregada por la dependencia y auditada por MAIN. La
publicación anterior no autoriza publicar otra versión del sitio ni distribuir
la aplicación.

Actualización posterior del 2026-09-05: Joaquin entregó el candidato del bloque
27 sobre `ef331e3`. MAIN detectó y corrigió un toque que podía sobrevivir a
pausa/retorno durante el cierre del menú. El 2026-09-06, con autorización
específica de una sesión QA temporal, cerró el recorrido real botón → página →
Atrás y restituyó el estado previo. Resultado: 755/755 JVM y 32/32 pruebas
físicas dirigidas; datos conservados, QA detenida y QA.test retirado. El bloque
queda integrado localmente en `40f2c55`, sin push ni confianza beta productiva. Ver
`docs/audits/2026-09-05-apoyar-miguardia-android-v2-main.md`.

El 2026-09-06 Joaquin pidió preparar el siguiente prompt. **28 — Actualización
manual sin perder datos** queda habilitado mediante
`ACTUALIZACION_MANUAL_SIN_PERDER_DATOS_V2.md`, todavía sin implementación ni chat
creado por MAIN. Consulta y descarga son conscientes; el APK se verifica antes
de guardarlo con el selector Android. La persona actualiza desde Archivos,
sin desinstalar. ADR 0040 conserva paquete, certificado y datos, y excluye
instalador propio, permisos nuevos y búsquedas automáticas. No adelanta versión,
firma ni publicación; la QA del nuevo cliente requiere autorización separada.

MAIN integra un bloque por vez. Cada bloque debe compilar, pasar sus pruebas,
preservar el alcance ajeno y quedar documentado antes del siguiente. El mapa
permite recomendar el orden; Joaquin indica cuándo preparar o abrir cada nueva
tarea.

## 11. Fuentes de verdad

- Este archivo explica **qué producto se está construyendo**.
- `docs/PLANIFICACION_MIGUARDIA_2_0.md` contiene **todas las reglas y el orden**.
- `docs/sectores/` conserva **la evidencia sectorial anónima**.
- `docs/STATUS.md` indica **qué bloque está activo hoy**.
- `docs/prompts/README.md` indica **qué prompt puede ejecutarse**.
- `docs/PROMPT_MAESTRO_MAIN_2_0.md` conserva **el método de integración** como
  trazabilidad; un alcance nuevo requiere una indicación nueva de Joaquin.
- `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md` conserva **cómo MAIN recibe,
  audita y cierra un handoff por vez**; no habilita otra tarea por sí solo.
