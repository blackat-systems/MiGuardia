# Stage 5 — imagen ficticia, navegación y escucha TalkBack

Fecha: 2026-10-09. Continuación autorizada, exclusivamente QA y datos ficticios.
Estado operativo y próximos pasos: `docs/STATUS.md`. Esta auditoría conserva
evidencia fechada, no acepta Stage 5 ni autoriza distribución.

## Entrada y límite

Repositorio independiente MiGuardia-3.0, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream homónimo en origin.
Contenedor sin Git, staging vacío y checkout acumulado preservado. Los 953
paths recibidos coinciden con el sello OEM previo: cero diferencias sin dueño.
MiGuardia-2.0 conserva sus siete cambios recibidos y permanece de sólo lectura.
Entrada: `.local-evidence/stage5/oem-positive-20261009/entry/manifest.json`.

Autoridad: continuar los pendientes, probar una imagen ficticia y avisar para
escuchar TalkBack. Samsung libre/desbloqueado; sin app pública, datos personales,
credenciales, red nueva, alarmas reales, firma, distribución ni operaciones Git.

## Defecto demostrado, no fallo del selector

La imagen se importaba y el visor sobrevivía a recreación, pero el primer toque
en Volver no cambiaba VIEWER→LIST. Dos fallos positivos conservaron esa evidencia:
`run-20261009T201136.125945Z.json` y `run-20261009T201326.209205Z.json`.
No se atribuyó la causa al usuario ni se aumentaron esperas para ocultarla.

Un diagnóstico sin selector, con una única imagen privada creada por la prueba,
reprodujo el mismo fallo en MainActivity:
`run-20261009T201920.748834Z.json`. El aviso «1 foto(s) agregada(s).» ocupaba
Y=151–286 px; Volver, Y=162–275 px. Snackbar interceptaba el toque.
La nueva regresión también falló antes del arreglo por superposición con Cerrar:
`run-20261009T202236.392085Z.json`.

Se agregó reserva de espacio optativa a TransientConfirmation. Fotos, Notas,
Feriados y Vacaciones la usan; sus controles ya no quedan detrás de la
confirmación. Se preservan duración de 2,5 segundos, colores y región accesible.
El Calendario conserva su overlay y consumo de insets existentes; no cambia
su geometría mensual ni se afirma aprobación de toda interacción bajo avisos.

Después: `run-20261009T202705.198159Z.json`, diagnóstico nativo 1/1. Con el aviso
todavía visible, Volver queda en Y=343–456 px y el primer toque produce LIST.
`run-20261009T202631.991230Z.json`, confirmaciones 2/2: incluye 24 combinaciones
de cuatro destinos × dos temas × tres zooms, sin superposición y con click real.
Son dos métodos JUnit, no 24 métodos. La desaparición automática sigue probada.

## TalkBack: técnica y confirmación humana separadas

Recibo `run-20261009T200858.789627Z.json`, 1/1: servicio Samsung conectado,
exploración táctil y foco/activación accesibles de Abrir menú, Seleccionar días
y Cancelar; MainActivity verifica EDIT→VIEW. Sin UiDevice durante la escucha.
Joaquín respondió «Sí, los tres claros». Reporte literal:
`human-talkback-report.json` en el directorio de evidencia de esta continuación.

Pertenece a QA `223f98d97f0a81ac67476dab8d5323aee81c0a00dd2ec102493c703eb11ebab8`
y runner `6b6962560891f30f20d1b422785ecc43b36f2279877b4920ff765a00622742df`.
No se reasigna esa escucha a la APK posterior al arreglo de confirmaciones.
No hubo grabación, lectura de PIN ni aceptación global de TalkBack.

## Imagen positiva y custodia

La prueba publica un PNG verde 640×400, 12.836 bytes, en un álbum UUID propio.
SHA-256: `798376801c57d42ddcacd412210a7288ed3a471080087633d19660bf591207a9`.
El callback valida la URI local exacta antes de leer metadatos/bytes; rechaza
cualquier archivo distinto. No se conceden permisos de galería ni se guardan
capturas, nombres o jerarquías de medios personales.

Usa el mes QA vacío 2088-01, repositorio y almacenamiento privados reales. No
restablece la base. La limpieza se limita a UUID/clave/URI creados por el ensayo;
no se confunde finally con una eliminación exitosa desde la interfaz.
Si el álbum no aparece en la vista inicial OEM, sólo Joaquín puede localizarlo.

Los cortes iniciales de dueño OEM, visibilidad de paquetes y controles aún no
compuestos se conservan, junto con fallos de fixture/build. La primera fuente
interrumpida se recuperó por ID y SHA exactos usando exec-out binario y se
confirmó su ausencia sin repetir el borrado. Ningún fallo se cuenta como verde.

En la QA nueva, `run-20261009T202714.609538Z.json` venció esperando selección
humana durante 150 segundos. No hubo confirmación dentro de esa ventana. El
archivo fue retirado y luego se canceló solamente el picker propio. El recorrido
OEM completo importar→ver→recrear→Volver→eliminar por UI sigue pendiente;
no se aumentó la espera ni se eligió otro medio para lograr un verde.

## Validación final y atribución

QA instalada y local coincidentes:
`c079941220236f08045cd00f13d0d811af96be5ffb2cff551cd62872794dc227`.
Runner final instalado:
`04377391181c59b6c57fb2a696945d44265cd1e8457a1ee6f335027156b80f55`.
App pública, antes/después:
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
No fue abierta, instrumentada ni reemplazada. QA abierta al cierre.

32 métodos distintos verdes sobre c079, cero fallos/omitidas/pendientes en sus
recibos aprobados: confirmaciones 2, visor nativo 1, Fotos 8, Feriados/Notas 9,
Vacaciones 7 y composición/insets 5. Los 27 primeros usan runner
`e0a6db9e2b2398c86d564264d6c8ee0d44dc0193ec54b58a3e339c484650dc60`;
los cinco de composición usan 043773…0f55. Inventario por método, hash de fuente
y recibo en `sealed-final/manifest.json`, bajo el directorio local de este bloque.
La escucha TalkBack anterior conserva su candidato/runner propios.

Cortes de Exceptions y Vacaciones (`203121` y `203231`) observaron ventana nula
con dueños Inicio/QA no coincidentes. Se detuvo la invocación: no se relajó el
guard ni se atribuyó causa al usuario o producto. Exceptions cerró 9/9 después;
Vacaciones y composición se ejecutaron por método para conservar esa frontera.
El fallo `203442` de composición esperaba `calendar-scroll-viewport`, retirado
del producto en favor de `calendar-static-content`. Sólo se actualizó ese selector;
se mantienen insets, coordenadas, palabras completas y los seis temas/zooms.
Los cinco métodos posteriores pasaron, incluido el causal `203705`.

`build-confirmation-candidate.log`: BUILD SUCCESSFUL, lintDebug, Debug/QA/Release
unsigned, runners QA/Debug de app y Debug de base. 511/511 JVM de app en 63 suites,
sin errores/omitidas; todos los XML posteriores al inicio del build, inventario
en `app-jvm-summary.json`. Tras corregir el selector del test, QA AndroidTest
se recompiló en `build-contextual-oracle.log`. No se reasigna esa recompilación
al runner Debug anterior ni se repite lógica JVM por un selector Android.

`final-device-probe.json`: permisos/flags y ajustes de accesibilidad iguales
a entrada; servicios previos conservados, cero fuentes de imagen y documento
ficticios por prefijo propio, QA al frente y hashes coincidentes. Sin audio,
PIN, posición, red ni medios personales leídos. La prueba nativa restableció
el mes 2088-01 vacío y retiró sus archivos privados exactos.

## Delta propio y próxima puerta

Cuatro fuentes de producto: `ui/components/TransientConfirmation.kt`,
`ui/photos/PhotosScreens.kt`, `ui/exceptions/ExceptionsScreens.kt` y
`ui/vacation/VacationScreens.kt`, bajo `app/src/main/java/com/blackatsystems/miguardia/`.
Tres fuentes Android: nueva Stage5PositiveMediaAndSpeechTest, ampliación de
TransientConfirmationComposeTest y selector de ContextualLayoutComposeTest.
Catálogo actualizado (119 clases/27 grupos); la clase supervisada sigue manual_only.
STATUS, handoff, matriz y esta auditoría completan doce paths propios.

No cambios a Room/Copias/versiones/permisos/dependencias/firma; no staging,
commit, push ni publicación. Entrada de 953 paths y sellos anteriores preservados.
Sello final nuevo: `.local-evidence/stage5/oem-positive-20261009/sealed-final/`,
con originales, delta propio reversible, checks y manifiesto de hashes.

MAIN: revisar ese delta y coordinar selección ficticia cuando Joaquín pueda
confirmarla dentro de la ventana. Después, continuar la revisión humana/integral;
no repetir por rutina los verdes. El overlay mensual en ventana baja conserva
una duda no probada sobre coincidencia con controles internos: el ensayo actual
comprueba posición, no toque debajo del aviso; no declararlo cerrado.

## Límites que permanecen

Los 144 métodos dirigidos, diez OEM anteriores y rendimiento/JVM/interop del
04/10 mantienen sus propios APK, runners y límites; no se reatribuyen.
Faltan revisión humana de estética/legibilidad/calendario/zoom y accesibilidad
integral, además de subcasos de los 33 recorridos (accesos externos, Recientes,
muerte de proceso y otros). La escucha de tres controles y una imagen ficticia
no cierran toda esa matriz. Stage 5 continúa parcial.
