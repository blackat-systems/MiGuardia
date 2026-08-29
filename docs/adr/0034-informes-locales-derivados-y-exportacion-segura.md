# ADR 0034: Informes locales derivados y exportación segura

- Estado: aceptada
- Fecha: 2026-08-29

## Contexto

MiGuardia V2 ya posee una única fórmula de horas compartida por Horas y
Resumen, una proyección mensual reconciliable y fotografías históricas de cada
jornada. El siguiente bloque debe convertir esa verdad local en archivos PDF y
XLSX sin volver a calcular cifras, persistir totales derivados ni exponer datos
privados por defecto.

El árbol actual no contiene una biblioteca de documentos, un `FileProvider`,
un flujo de Storage Access Framework ni una implementación histórica de
Informes que pueda reutilizarse. El contrato V1 que mencionaba empresa Inforce
y una referencia fija de 204 horas quedó superado: V2 no guarda empresa y la
referencia puede cambiar por vigencia o no utilizarse.

Los datos laborales viven en Room; el nombre opcional vive en un DataStore
separado y las fotos mensuales en almacenamiento privado. Combinar emisiones
reactivas de distintas fuentes durante una exportación podría producir un
archivo mezclado si los datos cambian al mismo tiempo.

## Decisión

### Una fotografía de lectura y una sola fórmula

Se incorpora una frontera de lectura mensual que obtiene dentro de una
transacción Room una fotografía coherente de todas las fuentes laborales
necesarias. No modifica tablas ni esquemas. El nombre opcional se captura como
presentación separada y no participa en ningún cálculo.

La lectura cubre el mes civil y la unión de los segmentos completos de
referencia que la proyección mensual utiliza, aunque alguno comience antes o
termine después del mes. Las horas conservan su asignación canónica por fecha
propietaria; no se recortan nuevamente por instantes de frontera.

La proyección de Informes reutiliza `MonthlySummaryInput`,
`calculateMonthlySummary(...)`, `HoursContribution` y
`MonthlySummaryProjection`. Puede organizar filas y textos, pero no vuelve a
calcular horas, referencias, disponibilidad, faltantes, excesos, nocturnidad o
feriados.

El momento de generación y la zona se congelan una sola vez. Los escritores
PDF y XLSX reciben una proyección inmutable y, para PDF con fotos, descriptores
de copias privadas ya validadas. No consultan repositorios, no reciben rutas
originales ni deciden reglas laborales. Los assets viven en la capa de
aplicación; `core/domain` no contiene `Bitmap`, rutas ni grandes bytes mutables.

### Mes parcial o cerrado

Un mes anterior al mes civil de generación se rotula como informe mensual
cerrado. El mes civil vigente usa exactamente `Informe parcial al dd/MM/yyyy`.
No se generan meses futuros. Un mes sin actividad puede producir un informe
honesto con estado vacío y referencia, si existe, en vez de inventar trabajo o
un cero desconocido.

Regenerar crea una nueva fotografía con el estado local vigente. No modifica
ni invalida archivos ya guardados.

### Privacidad por lista blanca

El archivo incluye por defecto únicamente datos laborales necesarios y
derivados aprobados: fechas, estados, lugar, tipo, horario planificado y real,
horas habituales, extras, disponibilidad separada y situaciones existentes.

Quedan excluidos siempre dirección, UUID, claves internas, rutas, metadatos de
fotos, `differenceReason`, motivos libres y explicaciones del horario real.

Son opciones conscientes y apagadas al iniciar cada informe:

- nombre o apodo local, si existe;
- puesto o función;
- notas de jornadas;
- notas privadas de carpeta médica;
- fotos mensuales.

Las notas médicas requieren una confirmación adicional. Las fotos sólo pueden
incluirse en PDF y se copian desde almacenamiento privado sin exportar EXIF,
ruta ni nombre interno. El XLSX permanece tabular; la interfaz explica que las
fotos se incluyen mediante PDF. Ninguna preferencia de privacidad del informe
se persiste para la próxima sesión.

Una fotografía elegida puede contener nombres u otros datos de terceros. La
interfaz lo advierte; MiGuardia no incorpora OCR, recorte ni redacción. Cada
foto seleccionada se copia y valida primero en un staging privado inmutable. Si
falta o cambia, la generación completa falla de forma visible.

### Formatos sin dependencia nueva

El PDF se genera con `android.graphics.pdf.PdfDocument`, páginas A4,
encabezados repetidos y paginado determinista.

El XLSX se genera como un paquete OOXML mínimo mediante APIs estándar de Java:
ZIP, XML, workbook, hojas, relaciones, estilos y celdas tipadas. No contiene
macros, fórmulas, vínculos externos, gráficos ni fotos. Las cadenas que podrían
interpretarse como fórmula se guardan como texto literal.

Los minutos enteros `Long` son la fuente numérica canónica. Los textos que
superen el máximo de una celda XLSX se dividen en filas de continuación
deterministas; nunca se truncan.

Las hojas iniciales son:

1. `Resumen`;
2. `Jornadas`;
3. `Disponibilidad`;
4. `Situaciones`;
5. `Notas`, sólo si la persona las incluyó.

Agregar una biblioteca de producción o fotos incrustadas en XLSX requiere una
decisión posterior separada.

### Guardar y compartir

El artefacto se construye primero en almacenamiento privado con archivo
temporal, sincronización y reemplazo seguro. Antes de exponerlo se validan su
firma, estructura y contenido mínimo.

Guardar usa `ACTION_CREATE_DOCUMENT` mediante el selector del sistema, con el
MIME exacto y sin permiso de almacenamiento. Compartir usa `FileProvider`, URI
`content://`, permiso temporal de lectura, `ClipData` y el selector de Android.
El provider expone exclusivamente el subdirectorio privado de Informes, nunca
la base, fotos originales ni la raíz de archivos de la aplicación.

Una cancelación, falta de espacio o error del proveedor no modifica Room,
DataStore ni archivos anteriores. Los temporales se limpian con una política
acotada y nunca se registran rutas o contenido privado.

Los artefactos compartibles se conservan como máximo 24 horas y hasta tres
archivos, protegiendo siempre el artefacto de la sesión actual. La limpieza se
ejecuta al iniciar o generar otra exportación, nunca inmediatamente después de
abrir el selector de compartir.

### Superficie

`Generar informe` parte del Resumen y conserva el mes visible. La pantalla
permite elegir PDF o Excel, revisar el estado parcial/cerrado y activar las
inclusiones privadas. Ofrece `Guardar informe`, `Compartir` y `Regenerar`.

El borrador de esa sesión puede sobrevivir a recreación mediante
`SavedStateHandle`, pero las opciones privadas no se guardan como preferencias
durables. Sólo se conservan mes, formato, opciones y etapa mínima: nunca notas,
bytes, URI o rutas. Consultar o previsualizar no escribe datos laborales.

## Consecuencias

- Horas, Resumen, PDF y XLSX deben reconciliar exactamente.
- Room permanece en versión 5 y no almacena informes ni totales.
- El primer XLSX es simple y compatible, no una planilla de análisis avanzada.
- El usuario conserva control explícito del destino y del acto de compartir.
- PDF puede incluir fotos con consentimiento; XLSX no.
- Las fotos se procesan de a una, muestreadas para la página, con un máximo de
  doce por informe y error explícito si se supera el límite.
- La generación debe probarse con archivos reales, no sólo con estados de UI.
- Copias de seguridad, restauración y bloqueo continúan como bloques futuros.

## Alternativas descartadas

### Copiar la fórmula de Resumen

Se descarta porque dos fórmulas podrían divergir sin que el archivo muestre el
error.

### Persistir totales o historial de informes

Se descarta porque duplica información derivada y agrega migraciones sin
necesidad. Los archivos guardados por el usuario son la evidencia exportada.

### Agregar Apache POI, iText u otra biblioteca

Se descarta en este alcance porque la salida requerida es fija y puede
construirse con APIs ya disponibles. Una necesidad futura de gráficos,
plantillas complejas o imágenes dentro de XLSX deberá justificar por separado
tamaño, mantenimiento, licencia y privacidad.

### Escribir directamente al destino elegido

Se descarta porque un fallo intermedio puede dejar un archivo inválido. Primero
se genera y valida una copia privada completa.

### Exponer un directorio amplio mediante FileProvider

Se descarta porque ampliaría innecesariamente la superficie de lectura de
otras aplicaciones.
