# ADR 0040 — Actualización manual verificada sin instalador propio

- Fecha: 2026-09-06.
- Estado: contrato técnico del bloque 28; implementación pendiente.
- Continúa ADR 0039, sin habilitar firma ni distribución.

## Contexto

El acceso beta, las opiniones y Apoyar MiGuardia están integrados. Falta el
cliente Android que use la descarga de actualización ya definida por el portal.
El usuario pidió preparar esta dependencia, conservando datos y control manual.
La API local existe; su autoridad pública, las claves y el APK oficial siguen
sin publicarse. No corresponde inventar un segundo servicio ni un instalador.

## Decisión

1. Una entrada `Actualizaciones` permite buscar, elegir y descargar una versión
   superior. Abrir la aplicación o el menú no consulta actualizaciones.
2. Se consume el contrato API v1 existente: Release, desafío, prueba de posesión
   y autorización breve. El comprobante y la identidad beta originales se
   reutilizan mediante una frontera acotada del coordinador, sin otro escritor.
3. La descarga se verifica en almacenamiento privado: tamaño, SHA-256, paquete,
   versión, compatibilidad y certificado del APK. La inspección de metadatos
   no reemplaza la verificación de firma que realiza Android al instalar.
4. La persona guarda conscientemente el archivo verificado mediante
   `ACTION_CREATE_DOCUMENT` y lo abre desde Archivos para elegir `Actualizar`.
   MiGuardia no usa `PackageInstaller`, no lanza el instalador y no solicita
   `REQUEST_INSTALL_PACKAGES`. No se amplía el FileProvider de Informes.
5. Sólo se ofrece una versión con código estrictamente mayor que el instalado,
   mismo paquete y mismo certificado. No hay downgrade, desinstalación,
   reinstalación, rotación de firma ni importación automática de copias.
6. No hay búsquedas periódicas, descargas en segundo plano, instalación
   automática ni avisos nuevos del sistema. El aviso de versión disponible es
   parte de la pantalla consultada conscientemente.
7. Una opinión en cola permite seguir trabajando según el contrato beta, pero
   no equivale a recepción. El servidor conserva autoridad sobre la descarga
   después del plazo. Un rechazo no borra datos ni invalida el uso local.

Android utiliza `versionCode` para ordenar versiones y requiere continuidad
de firma para actualizar el mismo paquete. Se mantienen las puertas de versión
y firma del ADR 0039. Fuentes: [versionado](https://developer.android.com/studio/publish/versioning)
y [firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).

El selector permite elegir dónde guardar sin permisos amplios de almacenamiento;
`ACTION_CREATE_DOCUMENT` no sobrescribe un documento existente. Fuente:
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).

## Alternativas y consecuencias

- Abrir un enlace público en el navegador no sirve para el contrato privado de
  actualización: expondría o perdería la autorización ligada a la instalación.
- Lanzar el instalador desde MiGuardia agregaría una superficie de permisos y
  entrega de URI innecesaria para este primer flujo manual.
- Guardar y abrir desde Archivos agrega un paso visible, pero mantiene la
  instalación bajo control de la persona y no amplía permisos de MiGuardia.

El cliente usa transferencia completa, cancelable y acotada, sin reanudación
Range en esta primera implementación. Límite técnico inicial: 128 MiB por APK;
una versión mayor informa incompatibilidad con este descargador, sin truncar.
Este límite no cambia el portal, la cantidad de plazas ni el trabajo guardado.

Guardar el archivo no demuestra instalación. La versión instalada se consulta
de nuevo al regresar o abrir la pantalla. Tampoco se promete impedir que una
persona redistribuya un APK guardado por ella.

## Evidencia y puertas

El bloque 28 entrega cliente y pruebas; no cambia `versionCode`, `versionName`,
paquete, Room, Copias, confianza pública ni firma. Las pruebas locales usan
dobles acotados y la instrumentación requiere autorización nueva.

El reemplazo real entre dos versiones firmadas compatibles, con comparación
de datos e identidad antes/después, sigue siendo obligatorio en la puerta de
QA de distribución. Compilar, guardar un APK o reemplazar un paquete con el
mismo código no sustituye esa evidencia. Cerrar el cliente no publica la beta.
