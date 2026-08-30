# ADR 0035: copias locales versionadas y restauración atómica

- Estado: aceptada para implementación
- Fecha: 2026-08-29
- Autoridad: Joaquin y MAIN 2.0

## Contexto

MiGuardia V2 guarda toda la historia laboral localmente. La fuente principal es
`MiGuardiaV2Database`, actualmente en versión 5 con veintisiete tablas; el
nombre opcional, la apariencia y otras preferencias viven en almacenes locales
separados; las fotos poseen metadatos Room y bytes privados en
`filesDir/schedule_photos/`.

Android Auto Backup y la transferencia automática entre dispositivos están
deshabilitados mediante `allowBackup=false` y reglas que excluyen todos los
dominios. Los informes PDF/XLSX son documentos de lectura y no contienen el
estado relacional completo. Por lo tanto, hoy una desinstalación o pérdida del
teléfono puede hacer irrecuperables los datos de MiGuardia.

El contrato histórico V1 mencionaba copias por meses y reemplazo mensual. V2
agregó una configuración única con historia, recurrencias, fotografías
laborales, horarios reales, extras, disponibilidad y relaciones que pueden
cruzar meses. Copiar o restaurar un mes aislado sin cerrar todo ese grafo puede
crear una historia inválida. Ese comportamiento histórico no se hereda en esta
primera versión V2.

Joaquin decidió que, antes de restaurar, MiGuardia debe preguntar si la persona
quiere combinar la copia sin sobreescribir silenciosamente o reemplazar todo el
estado recuperable.

## Decisión

### Copia manual, completa y portable

La primera copia V2 es manual y representa una fotografía completa de la unidad
local recuperable. No existe copia automática, sincronización, cuenta ni
integración propia con nube.

Guardar y abrir usan el Storage Access Framework. Android puede mostrar los
proveedores de documentos que la persona tenga instalados; elegir uno es una
acción externa y consciente del usuario, no una subida automática realizada por
MiGuardia.

La copia usa un contenedor propio, documentado y versionado. El formato posee,
como mínimo:

- magia y versión de contenedor;
- versión mínima de lector;
- fecha de creación UTC y zona informativa;
- versión Room de origen;
- identidad de la línea temporal V2;
- modalidad de fotos;
- algoritmo y parámetros de cifrado cuando corresponda;
- manifiesto canónico con cantidades y SHA-256 de cada entrada;
- datos lógicos de las veintisiete tablas de aplicación;
- preferencias portables expresadas por campos semánticos, no por archivos
  DataStore copiados a ciegas;
- bytes de las fotos únicamente cuando fueron incluidos conscientemente.

La extensión pública es `.miguardia-backup`, el MIME es
`application/vnd.blackatsystems.miguardia.backup` y el nombre sugerido no
contiene datos personales, por ejemplo
`MiGuardia_copia_2026-08-29_1430.miguardia-backup`.

No se copia el archivo SQLite en bruto, su WAL, `room_master_table`, archivos
DataStore completos ni rutas privadas. La representación lógica permite
validar compatibilidad, mostrar una vista previa y preparar una combinación por
registros sin depender de detalles físicos de una instalación.

### Lista blanca de datos

La copia incluye:

- las veintisiete tablas de `MiGuardiaV2Database` y sus relaciones;
- el `displayName` opcional de `GuardProfileStore`;
- tema y zoom interno de MiGuardia;
- orden, visibilidad e introducción del Resumen;
- preferencias durables de Notificaciones: habilitación, precisión solicitada,
  anticipación, persistencia, privacidad y modo de atención;
- avisos que la persona ocultó conscientemente, sólo como identidades
  normalizadas que sigan correspondiendo a eventos restaurados;
- preferencias durables de Clima: habilitación, unidad, inclusión en avisos y
  aceptación de la explicación del proveedor;
- metadatos y bytes de fotos como una sola unidad, si la persona activa
  `Incluir fotos`.

La copia excluye:

- `miguardia.db` y cualquier dato V1;
- IDs y preferencias por instancia del Widget;
- IDs de alarmas instaladas, avisos mostrados y demás tracking reconstruible de
  Notificaciones;
- permisos Android, canales, alarmas y estado del launcher;
- URI de sonido personalizada; después de restaurar debe elegirse nuevamente
  si ya no es válida;
- intentos, bloqueos temporales y caché de Clima;
- informes exportados, artefactos compartibles y staging de Informes;
- archivos temporales, journals de recuperación y cachés;
- APK, configuración local, secretos, logs o datos ajenos a MiGuardia.

Si se excluyen las fotos, no se exportan sus filas `schedule_photos`: nunca se
crean metadatos sin bytes. La vista previa identifica la copia como
`Sin fotos` y explica el efecto antes de una restauración total.

### Contraseña y cifrado

La contraseña es opcional y recomendada. Crear una copia sin contraseña exige
una advertencia explícita: el archivo será legible por quien obtenga acceso a
él. MiGuardia no guarda, recupera ni registra contraseñas.

Cuando existe contraseña:

- la clave se deriva con `PBKDF2WithHmacSHA256`, disponible desde el mínimo
  API 26, sal aleatoria única y parámetros incluidos en el encabezado
  versionado;
- el contenido completo se cifra y autentica con `AES-256-GCM`, nonce aleatorio
  único y encabezado estable como datos autenticados;
- la implementación usa JCA y `SecureRandom`, sin proveedor fijado, algoritmo
  casero ni dependencia nueva;
- contraseña y material derivado se mantienen el menor tiempo posible y se
  limpian cuando la API lo permite;
- contraseña incorrecta, etiqueta GCM inválida o manipulación producen un error
  seguro antes de mostrar o escribir datos.

La cantidad de iteraciones se fija en el formato, se prueba en API 26 y queda
registrada en el handoff. Aumentarla en el futuro requiere una nueva versión de
parámetros compatible con copias anteriores.

Sin contraseña, el manifiesto y sus hashes detectan corrupción accidental, pero
no se presentan como autenticación frente a un atacante.

### Vista previa antes de escribir

Abrir una copia primero la lleva a staging privado. Antes de tocar datos vivos,
MiGuardia:

1. limita tamaño total, cantidad de entradas y tamaño descomprimido;
2. rechaza rutas absolutas, `..`, entradas duplicadas, nombres desconocidos
   obligatorios y cualquier escape del staging;
3. descifra, valida versión, manifiesto, hashes, tipos, UUID, fechas, conteos y
   relaciones;
4. valida todos los invariantes V2, claves foráneas e integridad en un candidato
   aislado;
5. muestra fecha, versión, sector o sectores históricos, cantidades, presencia
   de fotos y resultado de compatibilidad;
6. no cambia Room, preferencias, fotos, avisos ni widgets durante la vista
   previa.

Una versión futura no compatible se rechaza sin intentar una restauración
parcial. Una copia antigua sólo puede abrirse mediante un lector o adaptador
explícito y probado.

### Dos modos conscientes de restauración

#### Combinar con mis datos — opción recomendada

Combinar conserva todo lo actual que la copia no modifica. Está disponible
cuando el destino está vacío o cuando ambos estados pertenecen a la misma
`timelineId` V2.

Los datos se comparan como agregados lógicos completos, no fila por fila sin
contexto:

- `Nuevo`: no existe y puede incorporarse con todas sus dependencias;
- `Idéntico`: misma identidad y contenido canónico; se omite sin duplicar;
- `Conflicto`: misma identidad con contenido diferente o una restricción
  histórica/relacional incompatible;
- `Solapamiento`: identidades distintas que coinciden en fecha o intervalo y
  que el dominio permite conservar sólo mediante decisión consciente;
- `Inválido`: no puede formar un estado V2 íntegro y bloquea la restauración.

Antes de aplicar, la persona puede usar una política general y revisar cada
conflicto:

- `Conservar lo actual` — predeterminado seguro;
- `Usar lo de la copia` — reemplaza el agregado completo, nunca una parte;
- `Conservar ambos` — sólo cuando ya son identidades distintas y todas las
  invariantes V2 permiten coexistencia.

`Conservar ambos` no reescribe UUID ni fabrica copias para evadir una
restricción. No está disponible para una configuración única, una misma
identidad modificada ni una relación semánticamente exclusiva.

En `Combinar`, los avisos ocultados válidos se unen con los locales. Todas las
demás preferencias en conflicto usan la resolución elegida; jamás se copian
claves DataStore desconocidas.

Una copia de otra `timelineId` no puede combinarse automáticamente con una
instalación no vacía: MiGuardia admite una sola configuración laboral. En ese
caso explica la incompatibilidad y ofrece conservar el estado actual o usar
`Reemplazar todo`; nunca inventa un segundo perfil.

No se escribe nada mientras quede un conflicto sin resolver.

#### Reemplazar todo

Reemplazar todo reconstruye la unidad recuperable desde la copia y elimina de
esa unidad los registros actuales que no estén en ella. Conserva únicamente
estado local deliberadamente no portable, como las instancias actuales del
Widget y los artefactos externos que el usuario ya guardó. Los artefactos y el
staging privados de Informes se limpian después del cierre para que una pantalla
no conserve un documento derivado del estado anterior; ningún PDF/XLSX guardado
fuera de la app se consulta, elimina ni reemplaza.

La pantalla muestra qué cantidades desaparecerán, qué se recuperará y si la
copia contiene fotos. Exige una segunda confirmación mediante una acción
rotulada exactamente `Reemplazar todo`. No se reutiliza una confirmación de
otro flujo.

Después de cualquiera de los dos modos, Notificaciones y Widget descartan
tracking reconstruible, releen las fuentes restauradas y se reconcilian. Los
permisos, canales, acceso a alarmas exactas y widgets del launcher siguen bajo
control de Android y no se simulan como restaurados.

### Atomicidad entre Room, preferencias y fotos

Una transacción Room aislada no alcanza. La restauración usa un coordinador
único y una bitácora privada durable con fases explícitas.

Antes de aplicar:

- prepara y valida el candidato completo;
- crea un punto de recuperación privado del estado actual;
- conserva bytes originales de fotos y preferencias hasta cerrar;
- bloquea nuevas mutaciones visibles y pausa reconciliadores que puedan escribir
  estado reconstruible;
- registra la fase `PREPARED` antes de la primera mutación.

La bitácora vive en `noBackupFilesDir` y usa como mínimo las fases
`PREPARED`, `SWAPPED`, `VERIFIED` y `COMMITTED`. Su recuperación ocurre al
arrancar, antes de crear los runtimes de Notificaciones, Widget o Clima.

Durante la aplicación:

- Room cambia en una única transacción dedicada y en orden compatible con sus
  claves;
- preferencias se escriben por sus APIs semánticas;
- fotos usan staging, nombres opacos, renombrado y compensación;
- cada fase queda registrada de forma sincronizada y recuperable.

Al finalizar se ejecutan `foreign_key_check`, `integrity_check`, validación V2,
conteos, hashes y reapertura. Recién entonces se marca `COMMITTED`, se elimina
el punto temporal y se reactivan observadores.

Si existe error, cancelación tardía o muerte del proceso, el próximo arranque
lee la bitácora y deja íntegramente el estado anterior o el nuevo; nunca expone
una mezcla. El punto de recuperación no es una copia exportable, no sale del
almacenamiento privado y se elimina sólo después de verificar el cierre.

Antes de comenzar se comprueba espacio para candidato, estado anterior, punto
de recuperación, fotos y margen de escritura. La falta de espacio detiene el
flujo antes de la primera mutación.

Crear una copia también comprueba coherencia transversal: si Room, preferencias
o fotos cambian durante la captura, revalida huellas antes y después y aborta o
reintenta de forma visible en vez de emitir un archivo mezclado.

### Android, privacidad y alcance

- `allowBackup=false`, `backup_rules.xml` y `data_extraction_rules.xml`
  permanecen intactos.
- Crear usa `ACTION_CREATE_DOCUMENT`; restaurar usa `ACTION_OPEN_DOCUMENT`,
  ambos con `application/vnd.blackatsystems.miguardia.backup`.
- No se solicita permiso general de almacenamiento y no se amplía el
  `FileProvider` limitado a Informes.
- La copia se construye y valida primero en almacenamiento privado; cancelar el
  selector no deja un archivo parcialmente válido ni cambia datos.
- No se imprimen rutas, contraseña, notas, horarios, nombres, manifiestos ni
  contenido de la copia en logs.
- Los archivos seleccionados son entrada no confiable y se procesan con límites
  y streaming; no se cargan copias o fotos completas en memoria.
- El bloque no incorpora copia por meses, recordatorios, borrado general,
  bloqueo de acceso, pacientes, nube, sincronización ni publicación.

Fuentes técnicas:

- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files);
- [criptografía de Android](https://developer.android.com/privacy-and-security/cryptography);
- [`SecretKeyFactory` y PBKDF2](https://developer.android.com/reference/javax/crypto/SecretKeyFactory).

## Consecuencias

- Una copia puede recrear la historia V2 sin depender del archivo físico Room.
- Combinar es conservador y no convierte configuraciones independientes en
  perfiles múltiples.
- Reemplazar todo sigue siendo una acción destructiva, pero posee vista previa,
  confirmación, punto de recuperación y rollback verificable.
- Las fotos aumentan tamaño y sensibilidad; por eso son opcionales y se procesan
  como una unidad con sus metadatos.
- Preferencias ligadas al dispositivo se reconstruyen o se vuelven a elegir.
- Room permanece en versión 5; agregar una tabla o migración exige otra decisión
  de MAIN.
- La restauración mensual histórica queda diferida hasta que exista un contrato
  que cierre todas las relaciones transmensuales.

## Alternativas descartadas

### Copiar el archivo SQLite y los DataStore en bruto

Se descarta porque impide una vista previa semántica, mezcla estado portable con
runtime y no permite combinar de forma segura.

### Restaurar cada fila directamente mientras se decide

Se descarta porque una cancelación o conflicto tardío dejaría datos parciales.
Toda decisión se convierte primero en un plan completo validado.

### Permitir combinar dos líneas temporales distintas

Se descarta porque MiGuardia posee una sola configuración laboral. Remapear su
grafo completo sería otra decisión de producto y persistencia.

### Copia mensual en el primer formato V2

Se descarta por ahora porque recurrencias, configuraciones, horas y jornadas
pueden cruzar el límite mensual. No se hereda una garantía V1 que el modelo V2
ya no puede sostener de forma trivial.

### Contraseña obligatoria

Se descarta como única modalidad porque Joaquin aprobó contraseña opcional. La
interfaz recomienda cifrar y explica claramente el riesgo de una copia legible.

### Android Auto Backup o un servicio propio de nube

Se descartan porque eliminan el acto consciente, amplían privacidad y contradicen
el producto local.
