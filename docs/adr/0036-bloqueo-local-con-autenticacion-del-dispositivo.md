# ADR 0036: bloqueo local con autenticación del dispositivo

- Estado: aceptada para implementación
- Fecha: 2026-09-01
- Autoridad: Joaquin y MAIN 2.0

## Contexto

MiGuardia V2 conserva localmente jornadas, horarios reales, extras,
disponibilidad, notas, fotografías y referencias médicas. Android ya protege
ese almacenamiento mediante el sandbox y el cifrado del dispositivo, pero una
persona que recibe el teléfono desbloqueado todavía puede abrir la aplicación y
consultar esa información.

El contrato histórico V1 mencionaba PIN, huella y reconocimiento facial, pero
también prohibía inventar un almacenamiento casero de PIN o asumir que toda
autenticación facial es fuerte. La aplicación actual tiene `minSdk 26`, usa una
sola `MainActivity`, posee una actividad separada para configurar el Widget y
puede recibir destinos desde avisos, Widget y selectores del sistema. El
bloqueo debe cubrir todos esos accesos sin duplicar credenciales ni interferir
con los procesos locales de avisos, Widget, Clima o recuperación de copias.

## Decisión

### Bloqueo opcional de la interfaz

Se incorpora **Bloqueo de acceso** como opción local, desactivada por defecto.
Al activarla, MiGuardia exige autenticación antes de componer o exponer datos
laborales en sus actividades.

La pantalla cerrada es genérica: muestra el nombre de la aplicación, el estado
`MiGuardia está bloqueada` y la acción `Desbloquear MiGuardia`. No compone el
Calendario ni conserva sus semánticas accesibles detrás de una capa visual.

Este bloqueo protege el acceso a la interfaz. No se presenta como cifrado de
Room, DataStore, fotografías, informes o copias exportadas. El almacenamiento
continúa protegido por el sandbox y el cifrado que Android aplique al
dispositivo.

### Una sola credencial: la del teléfono

MiGuardia no crea, pide, compara ni guarda un PIN propio. Usa el diálogo de
autenticación provisto por Android y acepta:

- biometría de clase fuerte reconocida por Android, como una huella compatible;
- PIN, patrón o contraseña segura configurados como credencial del dispositivo.

Un rostro sólo se acepta como biometría cuando Android lo clasifica dentro de
la fortaleza autorizada. Si no existe una biometría apta, la credencial segura
del dispositivo continúa siendo la recuperación normal. Si el teléfono no
tiene ninguna credencial segura, MiGuardia explica el requisito y puede abrir
los ajustes de seguridad; no activa un bloqueo imposible de recuperar.

Activar, desactivar o cambiar el tiempo de bloqueo exige una autenticación
nueva. `Bloquear ahora` no la exige porque sólo cierra el acceso.

### Sesión y tiempos

Antes de confirmar la activación, la persona elige una de cuatro opciones
exactas:

1. `Inmediatamente`;
2. `Después de 1 minuto`;
3. `Después de 5 minutos`;
4. `Después de 15 minutos`.

`Inmediatamente` es la selección inicial segura. Al confirmar, MiGuardia pide
una sola autenticación y guarda atómicamente la habilitación junto con el plazo
elegido. El plazo se mide con un reloj monotónico basado en
`SystemClock.elapsedRealtime()`, no con fecha y hora civil. La autenticación se
conserva sólo en memoria durante la sesión permitida.

Un proceso nuevo, una muerte de proceso, un reinicio del teléfono o volver
después de que el dispositivo estuvo bloqueado exige autenticación aunque el
plazo anterior no pudiera reconstruirse. El diálogo biométrico propio no debe
provocar un ciclo de auto-bloqueo. Una recreación por configuración y el paso
entre las dos actividades de MiGuardia comparten la sesión vigente y no cuentan
como abandono. Las salidas a SAF, fotos, Ajustes, compartir o mapas siguen el
plazo elegido y, al volver, el contenido permanece cubierto hasta que la
decisión de sesión esté resuelta.

### Navegación y recuperación

Un toque en una notificación, Widget o enlace interno mientras MiGuardia está
bloqueada conserva únicamente el destino necesario y lo procesa después de una
autenticación válida. No consulta una dirección, jornada o fecha protegida antes
de abrir el acceso, no registra extras privados y no ejecuta dos veces la
acción.

La recuperación de una restauración interrumpida continúa antes que cualquier
otra inicialización. Una vez que el estado recuperable queda coherente, el
bloqueo se evalúa antes de mostrar la aplicación. Notificaciones, Widget y
Clima pueden seguir reconciliándose en segundo plano: el bloqueo es una frontera
de interfaz, no una suspensión de la verdad local.

La configuración del Widget también queda protegida. Un launcher no puede usar
su actividad exportada para cambiar una instancia o habilitar privacidad
completa sin pasar antes por la misma autenticación cuando el bloqueo está
activo.

### Protección de Recientes

Cuando el bloqueo está habilitado, la representación de MiGuardia en la vista
de aplicaciones recientes no debe contener el Calendario, horarios, nombres,
notas, fotos ni otra información laboral. En Android 13/API 33 o superior se
usa la API pública específica para deshabilitar la captura de Recientes. En
versiones anteriores se aplica una cobertura segura y, cuando corresponda,
`FLAG_SECURE` durante la transición al fondo o mientras la puerta está cerrada.

La aplicación no impide innecesariamente una captura consciente mientras la
persona está autenticada y usando MiGuardia. El objetivo de este bloque es
evitar la fotografía residual de Recientes y la exposición durante el bloqueo,
no convertir todas las pantallas en contenido permanentemente incapturable.

### Preferencias locales y copias

Se crea un DataStore de dispositivo exclusivo para:

- habilitación;
- plazo elegido;
- versión del contrato de preferencias.

No almacena credenciales, plantillas biométricas, resultado de autenticación,
hora civil, destino pendiente ni información laboral. Un error de lectura no
se convierte silenciosamente en `bloqueo desactivado`: muestra una puerta segura
con reintento y permite reparar únicamente esa preferencia después de autenticar
con el sistema, sin borrar datos de trabajo.

Las preferencias de bloqueo son deliberadamente **no portables**. El formato
`.miguardia-backup` continúa con sus diecisiete preferencias semánticas y no
incluye el bloqueo. Combinar o reemplazar una copia no activa, desactiva ni
cambia el plazo del dispositivo actual.

### Widget, avisos y archivos externos

El bloqueo no modifica silenciosamente las privacidades elegidas para Widget o
Notificaciones. Esas superficies viven fuera de la actividad y conservan sus
propios controles. La pantalla de Bloqueo explica esa diferencia y enlaza a sus
ajustes cuando corresponda.

Tampoco revoca ni cifra informes o copias que el usuario ya guardó o compartió
fuera de la aplicación. Los flujos internos para generar, abrir o restaurar sí
quedan detrás de la puerta de acceso.

### API y dependencia

Se autoriza una única dependencia oficial de producción:
`androidx.biometric:biometric:1.1.0`, versión estable publicada por AndroidX al
cerrar este contrato. No se usa el artefacto KTX alfa ni otra biblioteca de
autenticación.

La dependencia existe para ofrecer una adaptación compatible desde API 26 al
diálogo del sistema. La implementación debe abstraer el autenticador detrás de
una frontera testeable y resolver explícitamente las combinaciones admitidas en
API 26–29 y API 30 o superior. Se autoriza únicamente la declaración de
biometría que la biblioteca oficial necesite; no se agregan permisos peligrosos
ni un servicio.

Fuentes oficiales:

- [diálogo biométrico y credencial del dispositivo](https://developer.android.com/identity/sign-in/biometric-auth);
- [versiones de AndroidX Biometric](https://developer.android.com/jetpack/androidx/releases/biometric);
- [`SystemClock.elapsedRealtime()`](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime());
- [`Activity.setRecentsScreenshotEnabled`](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean));
- [`FLAG_SECURE` para actividades sensibles](https://developer.android.com/security/fraud-prevention/activities#flag_secure).

## Consecuencias

- La persona usa la misma huella o credencial que ya protege el teléfono.
- MiGuardia no puede recuperar una credencial ni saltear un bloqueo del sistema.
- Un proceso nuevo nunca hereda una sesión autenticada.
- Los accesos desde avisos, Widget y configuración quedan detrás de una sola
  puerta coherente.
- Room continúa en versión 5 con veintisiete tablas y esquemas 1 a 5 intactos.
- El formato V1 de `.miguardia-backup` sigue siendo legible porque su lista
  blanca no cambia.
- La dependencia agrega tamaño y código transitivo de AndroidX, pero evita una
  implementación propia divergente entre API 26 y versiones modernas.
- El comportamiento biométrico, Recientes y credencial del dispositivo exige
  instrumentación y QA real; compilar AndroidTest no alcanza.

## Alternativas descartadas

### PIN propio de MiGuardia

Se descarta porque exigiría almacenar o derivar otro secreto, diseñar
recuperación y asumir riesgo de bloqueo definitivo. La credencial del sistema
ya resuelve esos problemas con una interfaz confiable.

### Sólo huella

Se descarta porque dejaría afuera teléfonos sin sensor o personas que no puedan
usarlo. La credencial segura del dispositivo es el respaldo obligatorio.

### Aceptar cualquier reconocimiento facial

Se descarta porque Android distingue clases de fortaleza. MiGuardia no declara
seguro un mecanismo que el sistema no clasifica como apto.

### Cifrar Room dentro de este bloque

Se descarta porque cambia persistencia, claves, recuperación y copias. El
objetivo actual es controlar el acceso visible a la aplicación, no reemplazar
el modelo de almacenamiento.

### Llevar el bloqueo dentro de la copia portable

Se descarta porque una restauración en otro teléfono podría activar una
preferencia incompatible o cambiar la seguridad local sin consentimiento.

### Bloquear Notificaciones y Widget desde la misma opción

Se descarta porque ya poseen controles de privacidad independientes y visibles.
Cambiar esas elecciones en silencio haría impredecible la información exterior.
