# Informes locales de jornadas y horas V2

- Estado: **CERRADO — INTEGRADO Y VERIFICADO POR MAIN**
- Fecha: 2026-08-29
- Cierre MAIN: 2026-08-29
- Proyecto obligatorio:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama obligatoria: `codex/miguardia-2.0`
- Base funcional cerrada:
  `d22c5a19ab4722b36116230678511e2cfcd886fa`
- HEAD de entrada: el checkpoint documental exacto que MAIN informe al abrir
  la tarea
- Nombre humano: **Informes locales**

> Nota de cierre: el cuerpo conserva el contrato y las puertas vigentes al
> abrir la dependencia. La evidencia ejecutada y los límites finales están
> registrados en
> `docs/audits/2026-08-29-informes-locales-de-jornadas-y-horas-v2-main.md`.

## QUÉ HACE

Permite convertir un mes de MiGuardia en un archivo claro y verificable:

- PDF para leer, imprimir o adjuntar;
- Excel/XLSX para revisar las filas y cifras en una planilla;
- resumen mensual y detalle cronológico por día;
- estado parcial cuando el mes todavía está en curso;
- guardado en el lugar que el usuario elija;
- compartir mediante el selector de Android;
- regeneración desde los datos locales actuales.

El usuario decide conscientemente si agrega su nombre, puesto, notas privadas o
fotos. Esas opciones empiezan apagadas.

## POR QUÉ EXISTE

MiGuardia ya conoce las jornadas, el trabajo real, las horas extra, la
disponibilidad y el avance contra la referencia elegida. Falta poder llevar esa
información fuera de la pantalla sin volver a copiarla a mano.

Esta dependencia existe para transformar la misma verdad que usa Resumen en un
documento útil y privado. No crea otro motor de horas, no guarda totales y no
es una copia de seguridad: es una fotografía local, consciente y legible de un
mes.

## ROLE

Sos una dependencia especializada de MAIN 2.0. No sos MAIN y no podés
redefinir el producto, los cuatro rubros, el motor de horas, Resumen, Room ni la
secuencia de la hoja de ruta.

Trabajá directamente en el proyecto y rama existentes. No crees otro proyecto,
rama, worktree, tarea ni subagente. MAIN conserva documentación canónica,
auditoría final, staging y checkpoints.

Primero inspeccioná las proyecciones de Horas y Resumen, los repositorios de
lectura, las fotos privadas y las rutas de navegación. Conservá lo que cumple y
agregá únicamente la capa necesaria para Informes.

## TASK

Implementar integralmente **Informes locales** como una superficie mensual,
derivada y de sólo lectura sobre los datos laborales.

El recorrido mínimo debe permitir:

1. abrir `Generar informe` desde el Resumen conservando el mes visible;
2. elegir PDF o Excel/XLSX;
3. ver si será `Informe parcial al dd/MM/yyyy` o informe mensual cerrado;
4. revisar qué información incluye antes de crear el archivo;
5. activar conscientemente las inclusiones privadas permitidas;
6. generar una única fotografía coherente de ese mes;
7. guardar el archivo mediante el selector de documentos de Android;
8. compartirlo mediante el selector de Android;
9. cancelar cualquiera de esas acciones sin dejar un archivo inválido;
10. regenerarlo desde los datos locales vigentes;
11. abrirlo con aplicaciones reales compatibles;
12. preservar exactamente los datos, cifras y privacidad de MiGuardia.

No implementes copias, restauración, bloqueo, Ayuda, pacientes, historias
clínicas, liquidaciones, nube ni otra función futura.

## CONTEXT

La base cerrada ya posee:

- cuatro rubros exactos e independientes: Vigilancia privada, Policía,
  Enfermería y Medicina;
- una sola configuración laboral con vigencia histórica por fecha;
- jornadas manuales y materializadas por recurrencias;
- fotografías históricas de lugar, tipo, horario, color y puesto;
- horario planificado y horario real sin reescribir el plan;
- extras de jornada y extras independientes;
- disponibilidad separada del trabajo;
- vacaciones, carpetas médicas, feriados, `F/?`, notas, ausencias y
  cancelaciones en sus alcances vigentes;
- una sola fórmula en `HoursProgress` y una proyección mensual pura en
  `MonthlySummary`;
- `SummaryMetric` y `SummaryContribution` reconciliables y deterministas;
- `SummaryObserver`, que reúne hoy el grafo reactivo de fuentes para la UI;
- un nombre o apodo opcional en `GuardProfileStore`, sin empresa;
- fotos mensuales privadas y decodificación local con orientación EXIF;
- Room `MiGuardiaV2Database` versión 5, archivo `miguardia-v2.db` y veintisiete
  tablas;
- `minSdk 26`, `targetSdk 37` y AndroidX Core ya disponible.

El histórico V1 definió PDF/XLSX, informe parcial, resumen, tabla diaria y
opciones de notas/fotos. No heredes sus campos obsoletos:

- no existe empresa Inforce en V2;
- no existe una referencia predeterminada de 204 horas;
- no existe un único sector fijo para toda la historia;
- no existe un perfil laboral V1 visible que deba restaurarse.

## INPUTS

Fuentes obligatorias de producto y arquitectura:

1. `AGENTS.md`;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
3. `docs/STATUS.md`;
4. `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
5. `docs/prompts/README.md`;
6. las cuatro fichas de `docs/sectores/`;
7. `docs/PROMPT_MAESTRO_MAIN_2_0.md`;
8. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`;
9. ADR 0026, 0028, 0029, 0030, 0031, 0032, 0033 y 0034;
10. `docs/PROMPT_MAESTRO_MAIN.md` sólo como contrato histórico que V2 no haya
    reemplazado;
11. `docs/PROMPT_MAESTRO_PAUSA_REVISION_Y_REANUDACION.md` sólo para el
    inventario histórico de Informes;
12. este prompt;
13. código y pruebas actuales de Horas, Resumen, Room, notas, fotos,
    navegación y apariencia.

Fuentes técnicas oficiales aplicables:

- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files);
- [`FileProvider`](https://developer.android.com/reference/androidx/core/content/FileProvider);
- [configuración segura de `FileProvider`](https://developer.android.com/training/secure-file-sharing/setup-sharing);
- [`PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument);
- [estructura mínima de SpreadsheetML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document).

## DEPENDENCIES

Dependencias cerradas que debés reutilizar:

- base exclusiva V2;
- configuración laboral y fotografías históricas;
- carga, edición, recurrencias y horario real;
- extras independientes y referencia de horas;
- disponibilidad;
- Calendario final;
- Resumen personalizable;
- próximo evento, avisos y Widget sólo como superficies vecinas;
- pruebas cruzadas y auditoría integral del núcleo.

No abras ni inventes una dependencia paralela. Si una necesidad exige una
biblioteca de producción, una migración Room o una decisión de privacidad no
definida, detenete y devolvé el bloqueo a MAIN.

## PUERTA 0 OBLIGATORIA

Antes de modificar cualquier archivo:

1. leé completas las fuentes en el orden obligatorio;
2. verificá en vivo ruta, rama, HEAD, upstream, base protegida, limpieza,
   worktrees, remoto privado y autor Git;
3. confirmá que este prompt figure `HABILITADO` en
   `docs/prompts/README.md`;
4. confirmá que el HEAD informado por MAIN contiene a
   `d22c5a19ab4722b36116230678511e2cfcd886fa` como ancestro;
5. confirmá que no existe otra dependencia implementadora sobre el checkout;
6. inventariá todo código de exportación, documentos, compartir y
   `FileProvider`; el estado esperado es que no exista todavía;
7. detenete si el checkout no está limpio o el HEAD no coincide.

Comandos mínimos de sólo lectura:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse @{upstream}
git merge-base --is-ancestor d22c5a19ab4722b36116230678511e2cfcd886fa HEAD
git rev-parse v1.0.0^{}
git status --short --branch
git worktree list --porcelain
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
git remote get-url origin
git config user.name
git config user.email
```

Resultado esperado:

- ruta exacta:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
- rama `codex/miguardia-2.0`;
- autor `joaquin <blackat.systems@gmail.com>`;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- `main`, `origin/main` y `v1.0.0^{}` intactos en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- checkout limpio, sin staged ni archivos sin seguimiento;
- worktrees históricos preservados.

No uses ADB, Samsung, emuladores, instalaciones o limpiezas durante Puerta 0.
Esta tarea no hereda ninguna autorización anterior de dispositivos.

## OUTPUT

### 1. Fotografía mensual coherente

Creá una frontera de lectura explícita para Informes que:

- lea en una transacción Room todas las fuentes laborales del mes;
- no modifique filas, timestamps, versiones ni esquemas;
- reúna configuración, catálogos, jornadas y fotografías históricas;
- reúna horario real, extras, disponibilidad y protecciones;
- reúna feriados, vacaciones, carpetas médicas, `F/?`, ausencias y
  cancelaciones;
- reúna metadatos de notas y fotos sólo cuando la persona haya optado por
  incluirlos;
- termine con una fotografía coherente anterior o posterior a cualquier
  escritura concurrente, nunca una mezcla.

El rango de lectura no es sólo el mes civil. Primero resolvé los segmentos
completos de cumplimiento que usa Resumen y capturá la unión del mes con todos
esos segmentos, incluso cuando una semana o ciclo empiece antes o termine
después. Así la referencia no pierde contribuciones vecinas.

Podés extraer y reutilizar el ensamble de fuentes de `SummaryObserver`, pero no
uses una combinación ingenua de varios `Flow.first()` como garantía de
atomicidad. La nueva frontera es de lectura y no requiere una tabla nueva.

El nombre opcional proviene de DataStore y queda fuera de los cálculos. Si
cambia durante la generación, el archivo conserva el valor ya congelado.

Room no puede congelar bytes de `filesDir`. Después de la transacción:

1. capturá nombre y opciones de la sesión;
2. copiá cada foto elegida a un staging privado de Informes;
3. verificá identidad, tamaño y lectura de la copia;
4. si el original falta o cambia durante esa copia, abortá toda la generación;
5. no entregues al escritor rutas originales ni repositorios vivos.

### 2. Proyección pura de Informes

Creá en `core/domain` una proyección inmutable, por ejemplo
`MonthlyWorkReportProjection`, que contenga:

- mes;
- instante y zona de generación;
- estado `PARTIAL_AS_OF` o `CLOSED_MONTH`;
- sectores presentes, entre los cuatro exactos;
- `MonthlySummaryProjection` calculada una sola vez;
- estado explícito de cada referencia de horas;
- filas diarias seguras y deterministas;
- filas separadas de disponibilidad;
- situaciones existentes;
- inclusiones privadas aprobadas para esa generación.

Los escritores no reciben entidades crudas ni llaman repositorios. Sólo
renderizan esta proyección junto con un `FrozenReportAssets` de aplicación que
contiene descriptores de copias privadas validadas. No coloques `Bitmap`, rutas
originales ni grandes `ByteArray` mutables dentro de `core/domain`.

Reutilizá sin duplicar:

- `calculateMonthlySummary(...)`;
- `calculateHoursContributions(...)`;
- `summarizeHoursContributions(...)`;
- segmentación mensual, semanal o por ciclo;
- clasificación histórica de trabajo habitual y extras;
- reglas de horario real;
- disponibilidad programada, efectiva, reemplazada, pendiente y proyectada.

Transportá explícitamente los cinco estados de referencia:

- pendiente de configurar;
- no utilizada;
- desconocida;
- valor faltante para el período;
- definida.

Ninguno se transforma en `0 h`.

### 3. Mes y estado

- mes civil actual: texto exacto `Informe parcial al dd/MM/yyyy`;
- mes anterior: informe mensual cerrado;
- mes futuro: no se puede generar;
- mes sin actividad: se puede generar y debe decir `Sin actividad registrada`;
- regenerar vuelve a leer el estado local actual;
- un archivo anterior no se modifica silenciosamente.

Usá `Clock` y `ZoneId` inyectables. El límite es el primer instante del mes
siguiente en la zona aprobada por MiGuardia. Probá justo antes y exactamente en
ese límite.

### 4. Contenido del resumen

Incluí, cuando sean calculables:

- total trabajado;
- trabajo habitual;
- extras de jornada e independientes;
- trabajo pendiente programado;
- avance y referencia por segmentos completos;
- faltante o superación;
- disponibilidad programada, efectiva, reemplazada, pendiente y proyectada;
- noches, feriados, fines de semana, planificado frente a real, lugares,
  tipos, clases extra y situaciones existentes.

Las categorías superpuestas son clasificaciones, no horas adicionales. La
suma diaria utilizada para el total debe reconciliar exactamente con Resumen.

### 5. Tabla diaria

Ordená por fecha propietaria, inicio, fin y una identidad interna estable que
no se exporta. Admití varias jornadas y extras en un mismo día.

Para cada jornada incluí:

- fecha y estado;
- lugar y tipo históricos;
- horario planificado;
- horario real, si existe, sin ocultar el plan;
- duración imputada al mes;
- horas habituales;
- extras y su clase histórica;
- nocturnidad y feriado cuando correspondan;
- puesto o función sólo si la persona lo autorizó.

Para extras independientes usá filas propias. En cruces de mes conservá la
contabilidad canónica por `ownerLocalDate`: la contribución completa pertenece
al mes propietario y no se recorta con una fórmula nueva. Mostrá el intervalo
real completo y una columna `Minutos contabilizados` tomada directamente del
ledger canónico.

La disponibilidad usa una sección u hoja separada. Nunca se suma a trabajo o
cumplimiento.

Las situaciones muestran únicamente estados seguros: `F`, `?`, vacaciones,
carpeta médica, ausencia y cancelación. La marca de carpeta médica puede
aparecer; su nota privada no.

### 6. Privacidad

Siempre excluí:

- direcciones;
- UUID e IDs técnicos;
- claves o rutas de almacenamiento;
- EXIF y metadatos de dispositivo;
- `differenceReason`, motivos libres y explicaciones del horario real;
- explicaciones internas;
- logs o nombres de archivos con datos personales;
- montos, salario, liquidación, deducciones o información sindical;
- pacientes o datos clínicos.

Opciones conscientes, inicialmente apagadas cada vez que se abre Informes:

1. `Incluir mi nombre o apodo`, sólo si existe;
2. `Incluir puesto o función`;
3. `Incluir notas de jornadas`;
4. `Incluir notas privadas de carpeta médica`, con segunda confirmación;
5. `Incluir fotos mensuales`, disponible sólo para PDF y con explicación.

No reutilices `SummaryPreferences` para privacidad. Conservá el borrador de la
sesión con `SavedStateHandle`, pero no persistas estas opciones como
preferencias durables.

Si una foto o nota elegida no puede leerse, no la omitas silenciosamente:
mostrá un error concreto, preservá el borrador y permití reintentar o desmarcar
esa inclusión.

Antes de incluir fotos advertí que la imagen puede contener nombres u otros
datos de terceros. MiGuardia no hace OCR, recorte ni redacción. Permití como
máximo doce fotos por informe; si hay más, pedí reducir la selección.

### 7. PDF

Usá `android.graphics.pdf.PdfDocument`, sin nueva dependencia.

Requisitos:

- tamaño A4 y márgenes consistentes;
- encabezado y numeración de páginas;
- encabezados de tabla repetidos;
- filas que no quedan cortadas entre páginas;
- textos largos ajustados sin superposición;
- caracteres españoles correctos;
- claro sobre fondo blanco e imprimible;
- ninguna dependencia del tema o zoom de Android;
- fotos opcionales escaladas y orientadas con el decodificador vigente;
- una foto por bloque/página cuando sea necesario;
- decodificación de una foto por vez, muestreada a un máximo de 1600 px en su
  lado mayor, con reciclado inmediato después de dibujarla;
- nada de EXIF, ruta o clave interna en el archivo.

Validá el PDF generado abriendo todas sus páginas con `PdfRenderer` y
renderizándolas a bitmap en pruebas Android.

### 8. XLSX

Generá un paquete OOXML mínimo con APIs estándar de Java y sin dependencia de
producción nueva.

Hojas:

1. `Resumen`;
2. `Jornadas`;
3. `Disponibilidad`;
4. `Situaciones`;
5. `Notas`, sólo cuando corresponda.

Requisitos:

- ZIP y relaciones OOXML válidas;
- nombres de hojas estables y compatibles;
- estilos simples de encabezado, fecha, hora y duración;
- fechas y números como celdas tipadas cuando corresponda;
- minutos como enteros `Long` canónicos y horas legibles como texto; nunca
  uses `Double` como fuente de verdad;
- texto completo y escapado;
- notas de más de 32.767 caracteres divididas de forma determinista en filas
  de continuación; nunca truncadas;
- controles XML inválidos eliminados o normalizados;
- cadenas que comienzan con `=`, `+`, `-` o `@` tratadas como texto literal;
- sin fórmulas, macros, vínculos externos, gráficos ni fotos;
- apertura real en Excel, LibreOffice o un lector independiente compatible.

Cuando el formato elegido sea XLSX, la interfaz debe explicar que las fotos
sólo pueden incluirse en PDF.

### 9. Artefacto privado, guardar y compartir

Generá primero el archivo completo en un subdirectorio privado exclusivo de
Informes:

- nombre interno opaco;
- temporal dentro del mismo directorio;
- escritura fuera del hilo principal;
- `flush`, sincronización y movimiento seguro;
- firma y estructura mínima verificadas antes de exponerlo;
- limpieza acotada de temporales y artefactos antiguos;
- retención de hasta tres artefactos y 24 horas;
- protección del artefacto de la sesión actual;
- limpieza sólo al iniciar o generar otra exportación, nunca inmediatamente
  después de abrir el selector de compartir.

Nombres sugeridos al usuario, sin datos personales:

```text
MiGuardia_2026-08_informe_parcial.pdf
MiGuardia_2026-07_informe_mensual.xlsx
```

Guardar usa `ActivityResultContracts.CreateDocument` o el equivalente
`ACTION_CREATE_DOCUMENT` con MIME exacto:

```text
application/pdf
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

No agregues permisos de almacenamiento.

Compartir usa:

- `FileProvider` de AndroidX Core ya disponible;
- autoridad `${applicationId}.fileprovider`;
- `exported=false`;
- `grantUriPermissions=true`;
- XML limitado sólo al subdirectorio privado de Informes;
- `ACTION_SEND`, `EXTRA_STREAM`, `ClipData`;
- MIME exacto y `FLAG_GRANT_READ_URI_PERMISSION`;
- `Intent.createChooser`.

No expongas `files-path path="."`, almacenamiento externo, fotos originales,
base de datos, backups u otro directorio amplio.

Si guardar o compartir falla o se cancela:

- Room y DataStore permanecen intactos;
- el artefacto privado válido puede reutilizarse;
- el destino parcial se elimina cuando el proveedor lo permita;
- el usuario recibe un error claro y puede reintentar;
- no se crea una segunda exportación por doble toque.

### 10. Interfaz

Agregá `Generar informe` al Resumen. Abrí la pantalla con el mes visible, sin
crear un segundo calendario.

Estados mínimos:

- `LOADING`;
- `CONTENT`;
- `EMPTY` honesto;
- `GENERATING`;
- `READY`;
- `SAVING`;
- `SHARING`;
- `ERROR` con reintento.

Acciones visibles:

- elegir PDF o Excel;
- configurar inclusiones;
- `Guardar informe`;
- `Compartir`;
- `Regenerar`;
- `Volver al Resumen`.

Conservá mes, formato, opciones y etapa mínima mediante `SavedStateHandle`. No
guardes allí notas, bytes, URI ni rutas. No restaures como completada una
exportación que estaba a mitad de escritura. Las opciones privadas sobreviven
únicamente a la recreación de esa misma sesión y vuelven a apagarse cuando se
inicia una sesión nueva de Informes.

La pantalla debe respetar tema claro/oscuro, retrato/paisaje, zoom interno
100 %, 150 % y 200 %, textos largos y accesibilidad que no dependa sólo del
color.

## SCOPE

Podés modificar únicamente lo necesario dentro de:

- `core/domain/src/main/**/summary/**` o un paquete nuevo `report/**`;
- `core/domain/src/test/**` para las pruebas del contrato;
- `core/database/src/main/**` para una frontera transaccional de **sólo
  lectura**, sin esquema nuevo;
- `core/database/src/androidTest/**` para atomicidad de lectura;
- `app/src/main/**/reports/**`;
- integración mínima en Resumen, navegación, `MainActivity` y
  `MiGuardiaApplication`;
- `app/src/main/AndroidManifest.xml` sólo para el `FileProvider`;
- un XML de rutas limitado a Informes;
- `strings.xml` y recursos estrictamente necesarios;
- pruebas JVM y AndroidTest afectadas.

No modifiques documentación canónica. No hagas refactors ajenos al alcance.

## DO NOT

No:

- dupliques fórmulas de Horas o Resumen;
- persistas totales, proyecciones, archivos o historial de exportación en Room
  o DataStore;
- cambies Room V5, entidades, tablas, esquemas o migraciones;
- agregues bibliotecas, plugins o dependencias de producción;
- agregues permisos;
- cambies Gradle, SDK, `applicationId`, paquete o versión;
- agregues empresa, Inforce, 204 horas predeterminadas o un sector `Otro`;
- combines Enfermería y Medicina;
- incluyas información privada por defecto;
- exportes direcciones, IDs, rutas, EXIF, motivos médicos o explicaciones
  libres sin el consentimiento aprobado;
- generes CSV renombrado como XLSX;
- uses fórmulas o macros;
- escribas directamente sobre la base o el archivo de fotos;
- habilites red, nube, cuenta, sincronización, analítica o telemetría;
- implementes backups, restauración, bloqueo, pacientes, agenda profesional,
  Ayuda o publicación;
- uses ADB, Samsung o emuladores sin una autorización nueva de Joaquin;
- hagas commit, push, tag, merge, rebase, reset o descarte;
- modifiques `main`, `origin/main`, `v1.0.0` o producción.

## VALIDATION

### Dominio/JVM

Probá como mínimo:

- reconciliación exacta Informe ↔ `MonthlySummaryProjection`;
- ausencia de doble suma en noches, feriados, fines de semana, lugar o tipo;
- los cinco estados de referencia sin ceros falsos;
- referencias mensuales, semanales y por ciclos completos;
- mes parcial/final justo antes y en el límite;
- febrero bisiesto y cambio de año;
- varias jornadas por día;
- medianoche, jornada mayor a 24 horas y cruce de mes;
- horario real más corto, más largo o con fecha de inicio distinta;
- extra de jornada e independiente;
- disponibilidad solapada, reemplazada, protegida y multidiaria;
- ausencia, cancelación, `F`, `?`, vacaciones y carpeta médica;
- catálogos históricos renombrados o archivados;
- mes sin actividad y mes futuro;
- orden determinista y sin duplicados;
- lista blanca de privacidad.

### Snapshot/Room

Probá en Android:

- fotografía mensual transaccional coherente;
- una escritura concurrente produce estado anterior o posterior, nunca mezcla;
- consultar o generar no modifica ninguna de las veintisiete tablas;
- reapertura, error y rollback;
- esquemas 1 a 5 byte a byte intactos.

### Archivos

PDF:

- firma `%PDF-`;
- todas las páginas abren con `PdfRenderer`;
- textos largos, español y paginado;
- cero páginas vacías accidentales;
- fotos orientadas sólo cuando fueron elegidas.

XLSX:

- firma ZIP;
- `[Content_Types].xml`, relaciones, workbook, hojas y estilos válidos;
- XML bien formado y UTF-8;
- tipos de celdas correctos;
- defensa contra inyección de fórmulas;
- apertura con un lector independiente real;
- no es CSV ni HTML renombrado.

Ambos:

- MIME y extensión exactos;
- PDF semántica y visualmente equivalente para la misma fotografía; no exijas
  igualdad binaria entre versiones de Android;
- XLSX binariamente determinista para la misma fotografía, normalizando orden
  y timestamps de las entradas ZIP;
- ninguna dirección, UUID, ruta, nota o foto prohibida en los bytes;
- nombre sugerido sin información personal;
- error o cancelación sin artefacto final corrupto.

### UI/Android

Probá:

- entrada desde el mes visible de Resumen;
- cambio PDF/XLSX;
- opciones privadas apagadas por defecto;
- confirmación adicional de nota médica;
- fotos disponibles sólo en PDF;
- guardar, cancelar, compartir y reintentar;
- `FileProvider` limitado y permisos temporales;
- doble toque;
- recreación en cada etapa;
- vuelta a Resumen;
- claro/oscuro, retrato/paisaje y zoom interno 100/150/200;
- accesibilidad y textos largos.

### Batería contractual

Ejecutá serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 --rerun-tasks `
  :core:domain:test `
  :core:database:testDebugUnitTest `
  :app:testDebugUnitTest `
  :app:lintDebug `
  :app:assembleDebug `
  :app:assembleQa `
  :app:assembleRelease `
  :app:assembleQaAndroidTest `
  :core:database:assembleDebugAndroidTest
```

Extraé conteos reales desde XML y distinguí:

- JVM VERIFICADO;
- LINT;
- COMPILADO;
- ANDROIDTEST COMPILADO;
- INSTRUMENTACIÓN EJECUTADA;
- REVISIÓN FÍSICA;
- PENDIENTE.

`git diff --check` debe quedar limpio.

### Room protegido

Room debe permanecer:

```text
MiGuardiaV2Database versión 5
27 tablas
identityHash 77adbc875d0f4ee466cdbd0dd74d5c5c
```

Hashes protegidos:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

### Dispositivos

No ejecutes instrumentación, QA física ni emuladores sin una autorización
nueva y expresa de Joaquin después del handoff local. Compilar AndroidTest no
equivale a ejecutarlo.

La dependencia puede entregar un **candidato local** con JVM, lint,
compilación y AndroidTest compilado verdes, marcando instrumentación y apertura
física como `PENDIENTE`. El cierre del bloque corresponde a MAIN después de una
autorización nueva de dispositivo; no bloquees el handoff por no poseerla.

## STOP CONDITIONS

Detenete y devolvé el bloqueo si aparece:

- contradicción entre fuentes activas;
- necesidad de otra fórmula de horas;
- snapshot coherente imposible sin una escritura o migración;
- necesidad de una dependencia de producción;
- necesidad de incluir información privada no aprobada;
- cambio de esquema, permisos, arquitectura pública o servicio externo;
- checkout sucio de origen desconocido;
- validación roja que no pueda corregirse dentro del alcance;
- acción destructiva, push, tag, Release o producción.

No inventes una conciliación.

## HANDOFF A MAIN

Entregá un handoff autosuficiente con estas secciones exactas:

```text
# HANDOFF A MAIN — Informes locales de jornadas y horas V2

## QUÉ HACE
## POR QUÉ EXISTE
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## VALIDATION
## ROOM
## PHYSICAL QA
## DEVICE SAFETY
## RISKS
## PENDING
## GIT
## NEXT
```

Incluí:

- resultado funcional real;
- archivos modificados, nuevos y eliminados;
- conteos JVM reales;
- pruebas de PDF y XLSX realmente ejecutadas;
- evidencia de compatibilidad de archivos;
- estado exacto de Room y hashes;
- separación entre compilación e instrumentación;
- ausencia de QA física si no fue autorizada;
- estado Git exacto;
- afirmaciones PENDIENTES sin maquillarlas como verificadas.

Dejá el candidato directamente en el checkout compartido, sin staged, commit o
push. No hay nada para `cherry-pick`.

## DONE WHEN

El candidato local está listo para volver a MAIN sólo cuando:

- PDF y XLSX se generan desde una única fotografía coherente;
- sus cifras reconcilian exactamente con Resumen;
- parcial/cerrado y referencias desconocidas se muestran honestamente;
- la tabla diaria explica el total sin duplicaciones;
- disponibilidad permanece separada;
- privacidad está apagada por defecto y se respeta en los bytes finales;
- existen AndroidTests compilados que renderizan todas las páginas PDF y
  comprueban la estructura OOXML;
- la apertura real y la revisión física están ejecutadas o marcadas
  honestamente `PENDIENTE` por falta de autorización;
- guardar, compartir, cancelar, recrear y reintentar funcionan;
- consultar o exportar no escribe datos laborales;
- Room y esquemas permanecen intactos;
- la batería local queda verde;
- el handoff distingue toda evidencia pendiente;
- no hubo commit, push ni uso de dispositivos sin autorización.

MAIN sólo cierra la dependencia después de auditar el diff y ejecutar la QA
Android/física que Joaquin autorice.
