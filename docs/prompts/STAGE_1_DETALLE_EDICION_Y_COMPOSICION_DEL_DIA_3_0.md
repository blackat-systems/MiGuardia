# Stage 1 — Detalle, edición y composición del día

Estado contractual: **CERRADO / ACEPTADO / INTEGRADO COMO STAGE 1**.

Joaquín autorizó íntegramente la implementación local el 12 de septiembre de
2026 y aceptó su cierre el 14 de septiembre de 2026. El resultado y sus límites
están en `docs/audits/2026-09-12-stage-1-detalle-edicion-dia.md`. El cierre no
habilita firma, AAB, Play Store, website ni publicación.

- Modelo previsto: `gpt-5.6-sol`.
- Razonamiento previsto: `medium`.
- Rol previsto: especialista de implementación bajo coordinación de MAIN.

## QUÉ HACE

Transforma el recorrido que comienza al tocar un día del Calendario en una
experiencia clara, predecible y visualmente cuidada.

El Stage debe resolver como una única capacidad cohesiva:

- identificación inmediata de los días completamente cumplidos;
- presentación comprensible de las jornadas del día;
- acceso elegante a Notas y Avisos;
- Notas con texto e imágenes;
- visualización y corrección del horario realmente cumplido;
- integración correcta con el motor de horas;
- edición de una jornada sin mezclar objetivos;
- agregado de una segunda jornada;
- advertencias no bloqueantes por superposición o descanso insuficiente;
- marcado del día como franco;
- carga contextual de un período de retén;
- representación del retén separada en Resumen;
- actualización de documentación, persistencia, Copias y pruebas afectadas.

No es un rediseño general de toda MiGuardia. Sí debe dejar este recorrido con
calidad visual y de interacción suficiente para no tener que rehacerlo durante
el futuro Stage global de UX/UI.

## POR QUÉ EXISTE

La pantalla actual mezcla información, acciones y estados con poca jerarquía.
El color aparece como una franja flotante, los botones compiten entre sí, el
horario expone datos técnicos innecesarios y la edición del día no presenta con
claridad las operaciones posibles.

Además:

- los días completados no comunican visualmente su estado en la grilla;
- Notas sólo admite texto aunque la documentación contempla imágenes;
- el horario planificado y el real se muestran con fechas completas y zona
  horaria, información innecesaria para el usuario en esta pantalla;
- la edición ofrece alternativas de distintos objetivos dentro de una jornada
  existente;
- no existe una entrada contextual clara para agregar otra jornada en el mismo
  día;
- el código de color hexadecimal se muestra como texto al usuario;
- Franco y Retén necesitan formar parte de un recorrido coherente de edición
  del día;
- el retén debe continuar separado del trabajo realmente cumplido;
- MiGuardia ya posee reglas y fuentes de datos que deben reutilizarse: no
  corresponde crear implementaciones paralelas.

Este Stage debe corregir esas inconsistencias sin reescribir la aplicación ni
debilitar la compatibilidad con MiGuardia 2.0.2.

## TASK

Implementar de punta a punta el nuevo recorrido de detalle y edición del día,
respetando el contrato funcional, visual, temporal, de persistencia y de
compatibilidad definido en este documento.

Antes de modificar:

1. Ejecutar la Puerta 0.
2. Leer las fuentes indicadas.
3. Mapear el recorrido actual desde Calendario hasta los repositorios
   implicados.
4. Identificar todos los productores y consumidores del estado de jornada,
   horario real, Notas, Franco y disponibilidad.
5. Preparar una matriz breve de estados y transiciones.
6. Implementar la capacidad completa.
7. Ejecutar las comprobaciones proporcionales al cambio.
8. Revisar el diff completo.
9. Entregar un handoff autosuficiente a MAIN.

No detenerse por decisiones visuales menores y reversibles. Elevar únicamente
una contradicción material de producto, compatibilidad, privacidad, seguridad o
arquitectura que no pueda resolverse con este contrato y la evidencia existente.

## CONTEXT

### Repositorio

`C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`

Estado observado al redactar este contrato:

- rama: `codex/miguardia-3.0`;
- HEAD: `cb83d85552683a0f47f0f4f14f4f43a326fa76a1`;
- upstream: `origin/codex/miguardia-3.0`;
- remoto: `https://github.com/blackat-systems/MiGuardia.git`;
- delta documental previo esperado:
  - `README.md`;
  - `docs/STATUS.md`;
  - `docs/HANDOFF_A_MAIN_3_0.md`.

Al ejecutarse este Stage, el especialista debe usar la base exacta que entregue
MAIN y verificar su estado real. Este hash registra el punto de redacción, no
habilita ignorar documentación legítima agregada después.

Si aparece una diferencia sin dueño, debe preservarla, describirla y detener la
escritura. No limpiar ni reconstruir el checkout.

### Producto

- MiGuardia 2.0.2 es la base funcional.
- `applicationId = com.blackatsystems.miguardia`.
- Aplicación Android local-first, Kotlin y Compose.
- Room V6, 27 tablas y seis esquemas conservados.
- Copias locales con 17 preferencias portables.
- Funcionamiento offline.
- Sin cuenta obligatoria, nube laboral, telemetría ni feedback obligatorio.
- La UI utiliza el lenguaje visual VIGILIA, pero el violeta es un acento y no
  debe convertirse en grandes bloques decorativos sin jerarquía.

### Evidencia visual aportada por Joaquín

Pantalla actual de detalle:

`C:\Users\Joaquin\AppData\Local\Temp\codex-clipboard-67d6d491-d550-40e8-959d-799331d47da0.png`

Pantalla actual `Jornadas del día`:

`C:\Users\Joaquin\AppData\Local\Temp\codex-clipboard-6cfbfb3a-0c78-4b3c-b44d-06e5527e8704.png`

Las capturas son evidencia del problema visual, no especificaciones
arquitectónicas ni fuentes durables. Si ya no están disponibles al ejecutar el
Stage, este contrato contiene la descripción autosuficiente del resultado.

### Evidencia de código conocida

Verificar de nuevo antes de modificar; las ubicaciones pueden cambiar luego de
que MAIN acepte e integre documentación 3.0.

- `CalendarDayDetail.kt` contiene el detalle del día, la franja de color y las
  acciones actuales de Notas y Avisos.
- `V2ShiftActualScreens.kt` presenta actualmente `Planificado / Real`, instantes
  completos y zona horaria.
- `V2ShiftEditScreens.kt` contiene `Jornadas del día` y muestra el color como
  hexadecimal en más de un paso del recorrido.
- `V2ShiftEditViewModel.kt` carga actualmente plantillas activas de más de un
  objetivo.
- `evaluateShiftWarnings` ya modela jornada en la misma fecha, superposición y
  descanso inferior a 12 horas.
- `ShiftNote` y `shift_notes` persisten sólo texto.
- `schedule_photos` representa fotos mensuales del cronograma, no adjuntos de
  una nota.
- `AvailabilityLabel.ON_CALL_RETAINER` ya representa `Retén` y
  `availability_windows` es una fuente independiente de las jornadas.

## CONTRATO FUNCIONAL Y DE UX

### 1. Identificación de días completados en el Calendario

Cuando un día tenga todas sus jornadas activas en estado `Completada`, su
recuadro en la grilla mensual debe recibir un fondo verde claro y sutil,
acompañado por un indicador de confirmación equivalente a `✓`.

El tratamiento debe:

- comunicar visualmente `tareas completadas`;
- funcionar como una confirmación tranquila, no como una alerta;
- conservar visibles los colores propios de cada jornada;
- mantener contraste correcto en tema claro y oscuro;
- evitar verdes intensos, fluorescentes o grandes bloques saturados;
- conservar legibles la fecha, los indicadores y la información secundaria;
- incluir semántica accesible que anuncie que el día está completado;
- no depender exclusivamente del verde o del símbolo visual.

Reglas de derivación:

- con una jornada activa, el día se considera completado cuando esa jornada
  está completada;
- con varias jornadas activas, sólo se considera completado cuando todas están
  completadas;
- si alguna permanece planificada o en curso, no aplicar el tratamiento de día
  completado;
- jornadas ausentes, canceladas o pendientes no deben producir el check;
- Franco, Retén y otras disponibilidades conservan su identidad y no deben
  confundirse con una jornada completada;
- una jornada nocturna pertenece a su fecha local de inicio: completar una
  jornada de `19:00–07:00` maquilla el día en que comenzó, no el día siguiente.

El estado debe derivarse de las jornadas existentes. No crear otra columna,
preferencia ni estado persistido para recordar si el día está completado.

### 2. Jerarquía del detalle del día

Al tocar un día del Calendario, la pantalla debe presentar la información en
este orden:

1. Fecha.
2. Identidad de cada jornada.
3. Acciones contextuales de Notas y Avisos.
4. Información del horario.
5. Acción para registrar un cambio horario.
6. Entrada `Editar este día`.
7. Información complementaria que corresponda, como disponibilidad.

No presentar una sucesión indiferenciada de botones grandes y tarjetas.

Cuando exista más de una jornada, cada una debe conservar una identidad visual
clara y sus acciones deben afectar inequívocamente a la jornada seleccionada.

### 3. Identidad visual de la jornada

Eliminar la franja vertical de color que actualmente aparece flotando junto a
la información.

Reemplazarla por un pequeño círculo o indicador equivalente que:

- reproduzca exactamente el color histórico de la jornada;
- se ubique a la izquierda del bloque formado por nombre y horario;
- funcione como una etiqueta visual discreta;
- quede alineado y contenido dentro de la composición;
- tenga semántica accesible que no dependa exclusivamente del color;
- no muestre el código hexadecimal.

Orden del contenido:

1. `Rawson (RAW)`.
2. Dirección, si fue proporcionada.
3. `19:00–07:00`.
4. Estado, por ejemplo `Completada`.
5. Puesto u otra información secundaria, si corresponde.

Si no existe dirección, el horario debe ocupar inmediatamente el lugar
siguiente al nombre. No dejar huecos vacíos ni etiquetas redundantes.

### 4. Dirección y Google Maps

Si la jornada contiene una dirección histórica no vacía:

- mostrarla inmediatamente debajo del nombre del objetivo;
- representarla como un elemento interactivo reconocible;
- abrir la ubicación o búsqueda correspondiente en Google Maps;
- codificar correctamente caracteres, espacios y acentos;
- priorizar la aplicación Google Maps cuando esté disponible;
- ofrecer una salida segura mediante un manejador `geo:` o navegador si Google
  Maps no está instalado;
- manejar la ausencia de una aplicación compatible sin cierre inesperado;
- no agregar permisos de ubicación;
- no solicitar GPS;
- no insertar un mapa embebido;
- no enviar información en segundo plano.

La acción sólo debe ocurrir por decisión consciente del usuario.

### 5. Notas y Avisos

Notas debe seguir apareciendo antes que Avisos.

Rediseñar ambas entradas como acciones compactas, claras y coherentes entre sí.
Pueden utilizar filas, pequeñas tarjetas o una composición equivalente, pero
deben cumplir:

- área táctil suficiente;
- iconografía y textos comprensibles;
- jerarquía secundaria respecto de la información de la jornada;
- contraste correcto en tema claro y oscuro;
- ausencia de grandes bloques violetas;
- ausencia de bordes decorativos excesivos;
- estados presionado, deshabilitado y foco reconocibles;
- comportamiento correcto con texto ampliado.

`Avisos de esta jornada` conserva su comportamiento funcional salvo los ajustes
necesarios para integrarlo visualmente.

### 6. Notas con imágenes

Extender Notas para admitir:

- texto;
- una o varias imágenes;
- nota sólo de texto;
- nota sólo de imágenes;
- nota con texto e imágenes.

Una nota no puede quedar completamente vacía.

Contrato:

- máximo de 30 imágenes por nota;
- el límite es acumulativo al editar;
- no mostrar un contador permanente ni anunciar preventivamente el límite;
- si el usuario intenta superar efectivamente el máximo, rechazar sólo el
  excedente y explicar claramente qué ocurrió;
- permitir agregar, ver y eliminar imágenes antes de guardar;
- permitir editar posteriormente texto y adjuntos;
- mostrar miniaturas eficientes;
- permitir abrir una imagen para verla;
- confirmar eliminaciones que puedan provocar pérdida accidental;
- no incluir automáticamente estas imágenes en informes compartibles;
- no ejecutar OCR, reconocimiento, subida, sincronización ni análisis;
- no conservar como dependencia permanente una URI temporal del selector;
- copiar el contenido mediante streaming al almacenamiento privado de la app;
- usar claves de almacenamiento opacas y seguras;
- validar que el contenido sea realmente una imagen decodificable;
- respetar la orientación EXIF;
- evitar cargar bitmaps completos en memoria para las miniaturas;
- eliminar archivos huérfanos ante fallos o cancelaciones;
- impedir que una fila de Room apunte a un archivo inexistente después de una
  operación considerada exitosa.

Reutilizar los componentes robustos de selección, copia, decodificación y
almacenamiento de imágenes existentes cuando sus contratos sean compatibles.
No reutilizar directamente `schedule_photos` como si una foto mensual y un
adjunto de nota fueran la misma entidad.

#### Persistencia propuesta

Crear una asociación explícita para adjuntos de notas, preferentemente una tabla
como `shift_note_attachments`, vinculada a `shift_notes`, con al menos:

- identificador estable;
- identificador de la nota;
- clave opaca de almacenamiento;
- tipo MIME;
- tamaño en bytes;
- dimensiones verificadas;
- orden dentro de la nota;
- fechas de creación y actualización.

Si la inspección confirma este diseño:

- avanzar Room mediante migración explícita y no destructiva `6→7`;
- conservar sin modificación los esquemas anteriores;
- exportar el esquema nuevo;
- probar la migración con datos históricos;
- no usar `fallbackToDestructiveMigration`;
- actualizar el contrato de Copias.

Si la inspección descubre un contrato vigente equivalente y realmente
reutilizable, usarlo y explicar por qué evita la nueva tabla sin mezclar
responsabilidades. No improvisar otra arquitectura sólo para evitar la migración.

#### Copias

Las notas de texto deben seguir siendo portables.

Los binarios de los adjuntos deben integrarse con la decisión existente de
incluir o excluir fotografías en una Copia, sin crear una segunda opción
confusa.

Si una Copia se genera sin imágenes:

- conservar el texto;
- no exportar metadatos que queden apuntando a binarios ausentes;
- no producir adjuntos rotos al restaurar.

Si incluye imágenes:

- exportar y verificar los archivos;
- comprobar integridad;
- restaurar filas y archivos de forma coherente;
- impedir recorridos parciales que dejen datos corruptos.

### 7. Tarjeta de horario

Sustituir la tarjeta actual `Planificado / Real` por una composición más sutil y
legible.

No mostrar en el detalle ordinario:

- fecha completa;
- segundos;
- identificadores técnicos;
- offset UTC;
- zona horaria.

#### Jornada completada sin corrección horaria

Mostrar:

`Horario trabajado`

`19:00 – 07:00`

#### Jornada futura o todavía no completada

Mostrar:

`Horario estipulado`

`19:00 – 07:00`

No afirmar que un horario futuro ya fue trabajado.

#### Jornada con horario real diferente

Mostrar:

`Horario estipulado`

`19:00 – 07:00`

`Horario realmente cumplido`

`19:00 – 09:00`

Y debajo, en texto secundario, una explicación humana del cambio.

Ejemplos:

- `Trabajaste 2 horas más de lo planeado.`
- `Te fuiste 2 horas antes.`
- `Entraste 30 minutos más tarde.`
- `Entraste 1 hora antes.`
- `Trabajaste 45 minutos menos de lo planeado.`
- `Cumpliste la misma cantidad de horas con otro horario.`

Reglas:

- usar minutos exactos como fuente;
- pluralizar correctamente;
- no redondear de manera engañosa;
- si sólo cambió la salida, describir la salida;
- si sólo cambió la entrada, describir la entrada;
- si cambiaron ambos extremos, expresar principalmente la diferencia neta de
  duración;
- contemplar jornadas que cruzan medianoche;
- respetar `ZoneId` y cambios civiles internamente aunque no se muestren;
- no registrar un horario real redundante cuando entrada y salida coinciden
  exactamente con lo estipulado.

### 8. Acción para corregir el horario

El botón debe decir exactamente:

`Registrar cambio en el horario estipulado`

Texto secundario:

`Registrá si te fuiste antes o te quedaste un rato más`

La acción debe reutilizar el editor y el modelo vigente de horario real. No
crear un segundo mecanismo de corrección.

Los cambios confirmados deben llegar correctamente a:

- horario trabajado;
- Resumen;
- extras;
- déficit o superávit;
- clasificaciones horarias;
- informes;
- cualquier consumidor vigente del motor de horas.

No convertir automáticamente toda diferencia en horas extra si el dominio
actual exige clasificación o confirmación. Conservar las reglas existentes de
horario habitual, extras y fragmentos.

### 9. `Editar este día`

Después del bloque horario debe aparecer una entrada clara:

`Editar este día`

Esta acción abre `Jornadas del día`.

La pantalla debe permitir comprender de un vistazo:

- cuántas jornadas hay;
- a qué objetivo pertenece cada una;
- su horario;
- su tipo;
- su color;
- qué acción modifica cada jornada;
- qué operaciones afectan al día completo.

### 10. Tarjetas de `Jornadas del día`

Conservar la información histórica correcta, pero:

- reemplazar `Color: #CA00FF` por un círculo o muestra visual del color exacto;
- no presentar el valor hexadecimal al usuario;
- mantener una alternativa semántica accesible;
- separar visualmente edición y eliminación;
- tratar `Eliminar jornada` como acción destructiva;
- pedir confirmación antes de borrar;
- no usar el violeta como relleno indiscriminado.

Aplicar la eliminación del hexadecimal a revisiones, confirmaciones y resúmenes
del recorrido. No corregir sólo la primera pantalla dejando el código visible en
pasos posteriores.

### 11. Edición de una jornada existente

Al pulsar `Editar jornada`, las opciones de plantilla deben pertenecer
únicamente al objetivo histórico de esa jornada.

Ejemplo: si la jornada pertenece a Rawson, el editor sólo debe ofrecer las
jornadas o plantillas activas de Rawson.

No mezclar dentro de ese editor alternativas de otros objetivos.

Conservar:

- fecha inmutable dentro de este editor;
- historial visible;
- controles de concurrencia;
- restauración de estado;
- protección ante doble toque;
- excepciones vigentes para fuentes archivadas;
- escritura estructural mediante el repositorio autorizado.

### 12. Agregar otra jornada

En `Jornadas del día` debe existir una acción visible:

`Agregar jornada`

Esta acción debe reutilizar el recorrido de carga manual existente,
preseleccionando la fecha del día.

Puede iniciar con el objetivo actual como sugerencia, pero debe permitir elegir
cualquier objetivo activo porque sirve para dos casos:

1. El usuario cargó el objetivo equivocado y necesita crear la jornada correcta
   antes de eliminar la incorrecta.
2. El usuario realmente tiene dos o más jornadas ese día.

No crear un escritor paralelo de jornadas.

### 13. Advertencias no bloqueantes

Al agregar o editar jornadas, evaluar el conjunto resultante mediante el motor
común de planificación.

Advertir si:

- existen varias jornadas con la misma fecha local;
- dos jornadas se superponen;
- entre el final de una y el comienzo de la siguiente hay menos de 12 horas de
  descanso.

Las advertencias deben:

- explicar cuáles son las jornadas afectadas;
- mostrar horarios comprensibles;
- aparecer antes de confirmar;
- permitir volver y corregir;
- permitir continuar conscientemente;
- no bloquear el guardado.

Reutilizar `evaluateShiftWarnings` y su umbral vigente de 12 horas. No duplicar
estas reglas en Compose.

La superposición y el descanso insuficiente son advertencias, no errores de
validación.

### 14. Franco

Dentro de `Jornadas del día` debe existir:

`Marcar este día como franco`

La entrada debe reutilizar la fuente de verdad actual de Franco.

Reglas:

- no crear una jornada ficticia para representar el franco;
- no borrar una jornada sin confirmación explícita;
- no inferir franco por ausencia de jornadas;
- conservar el día civil elegido por el usuario;
- permitir marcar como franco un día en el cual termina por la mañana una
  jornada iniciada el día anterior.

Regresión obligatoria:

Una jornada iniciada el 10 de septiembre a las 19:00 y finalizada el 11 de
septiembre a las 07:00 no debe impedir marcar el 11 de septiembre como franco
por el solo hecho de terminar esa mañana.

### 15. Retén

Dentro de `Jornadas del día` debe existir también:

`Marcar este día como retén`

Al elegirla:

- solicitar hora de inicio;
- solicitar hora de fin;
- permitir intervalos que crucen medianoche;
- mostrar claramente la fecha civil propietaria;
- crear una disponibilidad con etiqueta `Retén`;
- guardar `Retén` como fotografía propia de esa ventana, aunque la
  disponibilidad ordinaria tenga otro nombre o esté desactivada;
- no modificar la configuración laboral versionada ni cambiar el nombre de las
  disponibilidades presentes o futuras;
- reutilizar `AvailabilityWindowRepository` y el flujo vigente;
- no representar el retén como una `Shift`;
- no representar el retén como horas extra;
- no sumar su duración a trabajo, cumplimiento, déficit o superávit;
- mantener sus horas informadas de manera independiente en Resumen;
- conservar el cálculo de disponibilidad efectiva cuando coincide con trabajo
  activo;
- mantener el rechazo de superposición entre ventanas de disponibilidad y
  permitir tramos contiguos.

La nueva entrada es un acceso contextual al mecanismo existente, no una segunda
implementación de disponibilidad.

### 16. Dirección visual

El resultado debe sentirse actual, sobrio y deliberado.

Principios obligatorios:

- jerarquía antes que decoración;
- información primaria, secundaria y acciones claramente diferenciadas;
- espacios consistentes;
- bordes discretos;
- color VIGILIA usado como acento;
- superficies que funcionen en claro y oscuro;
- evitar grandes botones violetas salvo una acción primaria verdaderamente
  dominante;
- evitar píldoras gigantes para cualquier acción;
- evitar tarjetas dentro de tarjetas sin necesidad;
- evitar textos técnicos;
- evitar bloques densos;
- conservar áreas táctiles de al menos 48 dp;
- admitir zoom interno 100, 150 y 200 sin recortes ni acciones inaccesibles;
- TalkBack debe anunciar qué jornada y qué acción se selecciona;
- no depender exclusivamente del color;
- modales y confirmaciones breves, enfocados y previsibles;
- la acción destructiva nunca debe parecer la acción principal.

La interfaz debe poder comprenderse sin conocer el modelo interno de MiGuardia.

`Simple` no significa infantilizar el texto ni ocultar consecuencias importantes.
Significa que cada pantalla deja claro:

- dónde está el usuario;
- qué información está mirando;
- qué puede hacer;
- qué ocurrirá al hacerlo;
- cómo volver sin perder cambios accidentalmente.

## INPUTS Y ORDEN DE LECTURA

1. `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\AGENTS.md`.
2. `MiGuardia-3.0\AGENTS.md`.
3. `MiGuardia-3.0\docs\STATUS.md`.
4. `MiGuardia-3.0\docs\HANDOFF_A_MAIN_3_0.md`.
5. `MiGuardia-3.0\docs\LECTURAS_POR_TAREA.md`.
6. `MiGuardia-3.0\docs\WORKFLOW.md`.
7. Contratos vigentes de:
   - Calendario;
   - edición de jornadas;
   - horarios reales y extras;
   - Franco;
   - disponibilidad y Retén;
   - Notas;
   - Fotos;
   - Copias;
   - Resumen.
8. ADR relacionados con Room, Copias, privacidad, disponibilidad y escritura de
   jornadas.
9. Código y pruebas afectados.

Puntos de entrada ya identificados:

- `app/src/main/java/com/blackatsystems/miguardia/ui/CalendarScreen.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/CalendarDayDetail.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftActualScreens.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftEditScreens.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/management/V2ShiftEditViewModel.kt`;
- pantallas y ViewModel de Notas en `ExceptionsScreens.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/availability/`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/photos/`;
- modelos y repositorios de `ShiftNote`;
- `MiGuardiaV2Database`;
- `BackupSchema`;
- coordinadores de Copias;
- proyecciones de Resumen e informes;
- pruebas V2 de Calendario, edición, carga manual, horario real,
  disponibilidad, Fotos, Copias y actualización pública.

El mapa definitivo de archivos debe surgir de consumidores reales, no de una
búsqueda indiscriminada.

## OUTPUT

Entregar:

1. Días completamente cumplidos identificables mediante verde claro y check.
2. Recorrido de detalle del día rediseñado.
3. Identificador circular de color.
4. Dirección contextual enlazada a Google Maps.
5. Acciones de Notas y Avisos rediseñadas.
6. Notas con hasta 30 imágenes por nota.
7. Persistencia y ciclo de vida completo de adjuntos.
8. Migración Room y esquema, si corresponde.
9. Copias coherentes con los nuevos adjuntos.
10. Tarjeta de horario simplificada.
11. Registro de cambios horarios integrado con el motor vigente.
12. Pantalla `Jornadas del día` reorganizada.
13. Editor de jornada filtrado por objetivo.
14. Acción para agregar otra jornada.
15. Advertencias no bloqueantes por superposición y descanso menor a 12 horas.
16. Acciones contextuales de Franco y Retén.
17. Retén informado separadamente en Resumen.
18. Pruebas de dominio, persistencia, ViewModel y Compose.
19. Documentación canónica actualizada.
20. Handoff a MAIN con diff, decisiones, evidencia, riesgos y pendientes.

## SCOPE

Se permite modificar únicamente lo necesario en:

- UI Compose de la grilla mensual, el detalle y la edición del día;
- navegación interna necesaria para estos recorridos;
- ViewModels y estado de UI correspondientes;
- dominio de presentación del estado y el horario real;
- Notas y sus adjuntos;
- almacenamiento privado de adjuntos;
- Room, DAO, repositorios y migración estrictamente necesarios;
- Copias para preservar adjuntos;
- acceso contextual a disponibilidad Retén;
- consumidores de Resumen afectados;
- pruebas relacionadas;
- documentación canónica del comportamiento implementado;
- `docs/STATUS.md` sólo al registrar un resultado real aceptado por MAIN.

No realizar una modernización general de pantallas ajenas al recorrido.

## DEPENDENCIES

- Descubrimiento y arquitectura 3.0 aceptados por Joaquín.
- Este contrato aceptado y autorizado para implementación por Joaquín.
- Base 2.0.2 preservada.
- Contratos vigentes de jornada, horario real, Franco, disponibilidad, Resumen y
  Copias.
- Repositorio operativo independiente y sin diferencias inexplicables.
- Un único escritor sobre el checkout y sobre cada archivo compartido.

Este Stage es deliberadamente indivisible durante su implementación principal
porque las pantallas, el estado, Room, Copias y el motor horario forman un
recorrido transaccional común.

Puede delegarse investigación o auditoría sin escritura, pero no habilitar dos
escritores simultáneos sobre los archivos compartidos.

## INVARIANTES

- Mantener `applicationId`.
- Mantener compatibilidad de actualización desde 2.0.2.
- Preservar todos los datos existentes.
- Conservar esquemas Room históricos.
- Prohibir migración destructiva.
- Conservar la clave de firma fuera de Git.
- Mantener funcionamiento offline.
- No agregar telemetría.
- No subir Notas ni imágenes.
- No pedir permisos de ubicación por el enlace a Maps.
- No cambiar `Shift.startAt/endAt` para representar horario real.
- No crear una segunda fuente de horarios reales.
- No crear una segunda fuente de disponibilidad.
- No crear una segunda fuente persistida para `día completado`.
- No contar Retén como trabajo.
- No convertir automáticamente diferencias horarias en extras sin respetar el
  dominio actual.
- No mostrar hexadecimal de color en este recorrido.
- No perder direcciones, Notas, fotos, jornadas, francos ni disponibilidades
  durante migración o restauración.
- Mantener escrituras estructurales de jornadas a través del repositorio
  autorizado y sus controles de concurrencia.

## DO NOT

- No crear una rama, worktree, tarea, commit, push o tag sin autorización.
- No modificar el repositorio archivado `MiGuardia-2.0`.
- No usar dispositivos, ADB, Samsung ni emuladores sin autorización de ensayo.
- No firmar ni generar una entrega pública.
- No usar servicios remotos.
- No cambiar `versionName`, `versionCode`, `applicationId` ni certificado.
- No agregar una librería de imágenes, navegación, mapas o arquitectura sin
  demostrar una necesidad que no pueda cubrir la base existente.
- No abrir Google Maps automáticamente.
- No agregar permisos de ubicación.
- No almacenar imágenes como BLOB de Room.
- No conservar URIs temporales como almacenamiento permanente.
- No reutilizar `schedule_photos` mediante una asociación semánticamente falsa.
- No romper Copias que excluyen imágenes.
- No borrar archivos ante una operación de Room fallida sin compensación segura.
- No bloquear jornadas superpuestas o con menos de 12 horas de descanso.
- No eliminar jornadas automáticamente al marcar Franco.
- No representar Retén mediante jornadas falsas.
- No trasladar por inferencia el día civil propietario de una jornada nocturna.
- No convertir este Stage en el rediseño global de MiGuardia.
- No reescribir `docs/ORQUESTACION_MAIN.md` ni renumerar las 24 etapas históricas
  de 2.0.
- No declarar QA física, instalación, actualización real o restauración real si
  no fueron ejecutadas.

## VALIDATION

### Puerta 0

Registrar:

- ruta real;
- rama y HEAD;
- upstream y remotos;
- staged, modificados y no rastreados;
- repositorio independiente;
- diferencias preexistentes;
- límite de escritura.

### Dominio

Agregar o adaptar pruebas para:

- derivación de un día con una jornada completada;
- día con una jornada todavía planificada;
- dos jornadas completadas;
- una jornada completada y otra pendiente;
- jornadas canceladas o ausentes;
- jornada nocturna y fecha local propietaria;
- coexistencia visual con Franco o Retén;
- duración real mayor;
- duración real menor;
- entrada adelantada;
- entrada demorada;
- cambio de ambos extremos;
- igual duración con otro horario;
- diferencias con horas y minutos;
- superposición;
- descanso exactamente igual a 12 horas;
- descanso menor a 12 horas;
- dos jornadas el mismo día;
- retén separado del trabajo;
- jornada nocturna que no impide el franco del día siguiente.

### Persistencia

Si se agrega esquema:

- migración `6→7`;
- actualización desde cada ruta histórica soportada;
- conservación de datos V6;
- claves foráneas;
- orden de adjuntos;
- borrado de nota y sus adjuntos;
- fallos durante copia o eliminación de archivos;
- Copia con imágenes;
- Copia sin imágenes;
- restauración sin referencias rotas;
- esquema exportado y comparado.

### UI y ViewModels

Cubrir:

- día completado con verde claro y check;
- día parcial sin maquillaje incorrecto;
- dirección presente y ausente;
- apertura de Maps y fallback;
- muestra de color sin hexadecimal visible;
- una y varias jornadas;
- filtro del editor por objetivo;
- agregado de jornada;
- advertencia que permite continuar;
- Notas antes que Avisos;
- nota de texto;
- nota con imágenes;
- nota sólo con imágenes;
- límite acumulativo de 30;
- recreación durante selección o edición;
- doble toque;
- eliminación;
- horario planificado;
- horario completado;
- horario real diferente;
- Franco;
- Retén con intervalo nocturno;
- tema claro y oscuro;
- zoom interno 100, 150 y 200;
- semántica accesible relevante.

### Comprobaciones técnicas

Ejecutar primero las pruebas afectadas de forma incremental.

Antes del handoff ejecutar, si el cambio lo requiere:

- pruebas JVM de dominio;
- pruebas JVM de base de datos;
- pruebas JVM de app;
- pruebas de migración Room;
- instrumentación afectada sólo si fue autorizada y existe un recurso habilitado;
- lint;
- compilación Debug;
- compilación de AndroidTest;
- `python tools/docs_check.py`;
- `git diff --check`.

No ejecutar una batería adicional por ritual si ya existe evidencia válida con
los mismos inputs. Sí repetir cualquier comprobación afectada por cambios
posteriores.

### Revisión final

Revisar:

- diff completo;
- archivos no rastreados;
- ausencia de secretos y datos reales;
- ausencia de APK rastreadas;
- ausencia de hexadecimal visible en el recorrido;
- ausencia de fuentes de verdad duplicadas;
- coherencia entre documentación y comportamiento;
- que el verde de completado sea derivado, discreto y accesible;
- que el violeta funcione como acento y no como relleno indiscriminado.

## HANDOFF A MAIN

Entregar en español y con esta estructura:

- `QUÉ HACE`;
- `POR QUÉ EXISTE`;
- `OBJECTIVE`;
- `CHANGES`;
- `FILES`;
- `DECISIONS`;
- `DATA AND MIGRATIONS`;
- `BACKUPS`;
- `VALIDATION`;
- `GIT STATE`;
- `RISKS`;
- `PENDING`;
- `DEPENDENCIES`;
- `NEXT`.

Distinguir claramente:

- `VERIFICADO`;
- `INFERIDO`;
- `PENDIENTE`.

No llamar al Stage integrado ni aceptado. MAIN debe auditarlo y Joaquín debe
aceptar el resultado.

## DONE WHEN

El Stage termina cuando:

- el Calendario permite reconocer inmediatamente los días completamente
  cumplidos mediante verde claro y un check accesible;
- el estado visual se deriva de las jornadas y no duplica persistencia;
- el detalle del día posee una jerarquía clara;
- el color de jornada se representa mediante un indicador discreto y accesible;
- la dirección aparece en el lugar correcto y abre Maps conscientemente;
- Notas y Avisos tienen una presentación coherente;
- Notas admite texto e imágenes sin romper privacidad ni Copias;
- el horario se muestra sin fechas ni zona técnica;
- las diferencias horarias se explican humanamente y alimentan el motor
  correcto;
- `Jornadas del día` permite editar, eliminar y agregar jornadas;
- la edición de una jornada sólo ofrece plantillas de su objetivo;
- superposición y descanso menor a 12 horas advierten pero no bloquean;
- Franco contempla correctamente jornadas nocturnas del día anterior;
- Retén se carga desde el día, pide una franja horaria, se informa por separado
  y no altera la configuración de disponibilidad de otros días;
- no existen códigos hexadecimales visibles en el recorrido;
- la UI resulta legible, predecible, sobria y consistente en claro, oscuro y
  zoom interno 100, 150 y 200;
- las pruebas relevantes pasan;
- Room y Copias preservan datos;
- la documentación coincide con el comportamiento;
- el diff sólo contiene cambios pertenecientes al Stage;
- MAIN recibe un handoff auditable.

## CIERRE — QUÉ HACE Y POR QUÉ EXISTE

Este Stage convierte la edición de un día en una capacidad completa, no en una
colección de botones. El usuario puede reconocer lo ya cumplido, comprender su
jornada, consultar su dirección, adjuntar evidencia privada, corregir el
horario, agregar otra jornada, registrar un franco o declarar un retén sin
conocer la arquitectura interna de MiGuardia.

Existe para eliminar la inconsistencia entre documentación, comportamiento y
experiencia real, y para dejar estabilizados los contratos de día, jornada,
horario, Notas y disponibilidad antes del rediseño UX/UI global.
