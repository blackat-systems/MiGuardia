# ADR 0013: Perfil laboral local en DataStore

- Estado: aceptada para implementación
- Fecha: 2026-08-18

## Contexto

MiGuardia necesita una fuente local única para el perfil laboral del vigilador antes de implementar bienvenida, primera carga e informes. El contrato aprobado incluye un nombre o apodo opcional, la empresa inicialmente `Inforce` y editable, y la profesión visible y fija `Vigilancia y seguridad` durante la V1.

Objetivos y horarios ya son datos relacionales persistidos en Room. El puesto es una instantánea opcional de cada guardia. Duplicar cualquiera de esos datos dentro de Perfil produciría fuentes divergentes y podría alterar la lectura histórica.

## Decisión

El perfil se persiste en un DataStore Preferences exclusivo, con archivo propio del paquete de la aplicación.

- `displayName` se normaliza quitando espacios externos; vacío se guarda como ausencia.
- `company` se normaliza quitando espacios externos, es obligatoria al guardar y usa `Inforce` únicamente como valor inicial cuando todavía no existe una preferencia.
- `Vigilancia y seguridad` es una constante visible del producto y no se persiste ni se ofrece para edición en la V1.
- Objetivos y horarios activos se proyectan desde sus repositorios Room existentes. Perfil no crea copias ni nuevas tablas.
- El puesto continúa perteneciendo a cada carga y a la instantánea de su guardia; no se convierte en un valor global.
- El DataStore del perfil será la única fuente para que informes futuros lean la empresa configurada.

La implementación debe manejar errores de lectura `IOException` con valores iniciales seguros y realizar cada edición de forma atómica. El paquete QA conserva su almacenamiento separado por `applicationId`.

## Alternativas descartadas

### Agregar una entidad a Room

Se descarta porque introduciría Room v6 y una migración para dos preferencias simples que no tienen relaciones, consultas históricas ni cardinalidad múltiple.

### Guardar Perfil junto a objetivos, horarios o guardias

Se descarta porque mezclaría identidad laboral actual con plantillas e instantáneas históricas y facilitaría reescrituras accidentales del pasado.

### SharedPreferences

Se descarta para datos nuevos porque el proyecto ya usa DataStore para preferencias persistentes que requieren observación reactiva, pruebas aisladas y escritura segura.

## Consecuencias

- Room permanece en v5, con trece entidades y migraciones explícitas `1→2→3→4→5`.
- No cambian manifiesto, permisos, dependencias, red ni datos históricos.
- Borrar datos de Perfil en una futura restauración deberá tratar su DataStore como parte de la unidad local del usuario.
- Cambiar empresa o nombre afecta presentaciones e informes futuros, nunca las instantáneas de guardias ya creadas.
- Otras profesiones, cuentas, identificadores personales, nube y sincronización siguen fuera del alcance.
