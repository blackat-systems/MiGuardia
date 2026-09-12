# ADR 0039: piloto beta privado, acceso por clave y distribución web

- Vigencia: PARCIAL
- Sucesores: `docs/adr/0041-distribucion-publica-web-sin-activacion.md`
- Alcance vigente: Se conservan firma permanente, continuidad de datos, privacidad y Apoyar MiGuardia. Acceso beta, diez plazas, feedback obligatorio, descarga privada y promoción condicionada al piloto quedan sustituidos por ADR 0041; el cuerpo conserva la decisión histórica.
- Fecha: 2026-09-03
- Autoridad: decisiones explícitas de Joaquin

## Contexto

MiGuardia 2.0 cerró su hoja funcional y su matriz Android. El código auditado
quedó respaldado en el remoto privado hasta
`924fd46df018f5e14637dfe90a983fefdbf27af4`, pero todavía no existe una entrega
oficial firmada.

Joaquin decidió no publicar por ahora en Google Play. La primera prueba externa
se realizará con una APK descargable desde su sitio web existente y un piloto
de diez personas. La aplicación se instalará primero y luego mostrará una
interfaz propia para ingresar una clave de acceso a la beta.

Este acceso no es la antigua y descartada activación V1→V2. No migra datos ni
cambia el sector laboral: sólo limita quién participa del piloto.

## Decisión

### Identidad de versión

- La primera beta usa `versionName = "2.0.0-beta.1"`, `versionCode = 2` y
  `applicationId = "com.blackatsystems.miguardia"`.
- El nombre propuesto del archivo es `MiGuardia-2.0.0-beta.1.apk`.
- Las betas posteriores incrementan siempre `versionCode` y pueden llamarse
  `2.0.0-beta.2`, `2.0.0-beta.3` o, si corresponde, `2.0.0-rc.1`.
- La primera versión estable será `2.0.0`, con el siguiente `versionCode` libre
  y mayor que todas las betas distribuidas. `3.0.0` queda reservado para una
  futura ruptura mayor del producto.
- Toda actualización conserva el mismo `applicationId`, el mismo certificado
  Release y un `versionCode` creciente. Debe instalarse sobre la versión
  anterior, sin desinstalar y sin perder Room, DataStore, fotos ni copias.

### Diez plazas y claves de acceso

- El piloto tiene exactamente diez plazas, no diez solicitudes ni diez
  descargas HTTP.
- Existe una clave opaca y de alta entropía por plaza. Los códigos completos se
  muestran o entregan individualmente y nunca se guardan en Git, HTML, logs ni
  D1.
- El servidor conserva únicamente un HMAC de cada clave, su plaza, estado y
  tiempos mínimos. El secreto HMAC vive sólo en la configuración segura del
  servidor.
- Descargar o reanudar el APK no consume la plaza. El canje ocurre en el primer
  inicio de la aplicación, cuando la persona ingresa su clave.
- El formulario reserva la plaza mediante `AVAILABLE → RESERVED`. El canje
  posterior `RESERVED → REDEEMED` es atómico. Repetirlo con la misma clave y la
  misma instalación devuelve el mismo resultado; otra instalación no puede
  apropiarse de esa plaza.
- Una reinstalación o un teléfono nuevo requiere una reasignación consciente de
  Joaquin. Las copias laborales no transportan la plaza ni la activación.
- Revocar una plaza impide nuevos canjes y futuras descargas o actualizaciones,
  pero no borra datos ni apaga remotamente una aplicación ya autorizada.

### Acceso a la beta dentro de Android

La aplicación genera una clave EC P-256 no exportable en Android Keystore y
envía al servidor únicamente la clave pública junto con el código ingresado.
El servidor devuelve un comprobante firmado que contiene como mínimo:

- versión de contrato y `kid` de la clave pública verificadora;
- identificador seudónimo de activación;
- instante UTC de activación;
- `applicationId` y huella del certificado Release autorizado;
- huella de la clave pública de esa instalación;
- vencimiento del compromiso de devolución.

El APK contiene sólo claves públicas de verificación versionadas. La clave
privada del servidor, la clave de firma del APK y las diez claves de acceso son
secretos diferentes y nunca se mezclan.

El comprobante y el estado de devolución se guardan en almacenamiento privado
no portable y excluido de Copias. La activación inicial requiere Internet;
después, MiGuardia funciona sin conexión. El orden de arranque queda:

```text
recuperación pendiente de Copias
→ Bloqueo de acceso local, si está activo
→ acceso a la beta y devolución vencida
→ configuración laboral
→ guía inicial
→ Calendario
```

Mientras no exista un comprobante válido, MiGuardia no compone datos laborales,
no resuelve destinos de avisos y reemplaza Widget o notificaciones anteriores
por contenido genérico sin borrar su configuración.

### Devolución obligatoria a los quince días

- `feedback_due_at` es exactamente el instante de activación informado por el
  servidor más quince días de 24 horas.
- Durante los primeros catorce días completos la aplicación funciona sin
  exigir otra conexión.
- Al vencer, una pantalla completa propia de MiGuardia pide una devolución
  honesta. No es una ventana del sistema ni una superposición sobre otras apps.
- La devolución exige categoría, área, mensaje breve y consentimiento. No exige
  una opinión positiva, nombre legal, correo ni ningún dato laboral.
- Si hay conexión, el envío es idempotente y el servidor devuelve un comprobante
  firmado de recepción.
- Si no hay conexión o el servicio falla después de que la persona completó y
  confirmó el formulario, MiGuardia guarda localmente la devolución pendiente,
  permite continuar y reintenta cuando vuelve la conexión. No borra, bloquea ni
  retiene los datos laborales por una falla ajena a la persona.
- Mientras la devolución siga sólo en cola local, se puede seguir usando la
  versión instalada, pero no se habilita una beta posterior ni la actualización
  estable hasta que el servidor confirme su recepción.
- Ningún error de feedback habilita un apagado remoto, reinterpreta datos del
  trabajo ni obliga a compartir notas, turnos, fotos, direcciones o ubicación.

### Portal y privacidad

El sitio existente `https://blackatsystems.com` incorporará una página beta y
una API de servidor. La página explica el estado beta, los riesgos, la
privacidad, el compromiso de devolución y las instrucciones de instalación.

Antes de recibir el acceso, la persona usa un nombre o apodo elegido por ella;
no se exige identidad legal. Términos, aviso de privacidad y compromiso de
feedback se aceptan mediante decisiones separadas, inicialmente desmarcadas.
El servidor valida las tres aceptaciones y las versiones vigentes de Términos
y Privacidad antes de reservar una plaza; llamar la API directamente no permite
saltear esta condición.
El contacto para recibir respuesta es opcional y nunca autoriza marketing.

La primera descarga requiere la sesión breve creada por una reserva válida. Una
descarga directa sin esa sesión se rechaza. Las actualizaciones posteriores
requieren demostrar que la instalación conserva la clave privada asociada a su
comprobante de activación. Si ya venció el plazo y el servidor todavía no
registró `FEEDBACK_RECEIVED`, la versión instalada puede seguir funcionando,
pero no se entrega una versión posterior. Una devolución guardada en cola local
no cuenta como recibida hasta que el servidor la confirma.

El portal no guarda horarios, lugares, direcciones, notas, datos médicos,
fotografías, Android ID, IMEI, serial, identificador publicitario ni huella del
dispositivo. No incorpora analítica, publicidad ni seguimiento. Como política
inicial, los estados de plaza se eliminan hasta treinta días después del cierre,
el contacto opcional hasta treinta días después de la última respuesta o del
cierre, el feedback original hasta 180 días y los logs técnicos hasta catorce
días cuando el proveedor lo permita. Antes de publicar deben existir fecha de
cierre, contacto de privacidad y mecanismo manual de eliminación.

### Apoyar MiGuardia

`Apoyar MiGuardia` será una entrada propia y visible dentro del grupo
`Aplicación` del menú principal. No queda escondida dentro de Ayuda.

La acción abre en el navegador una URL HTTPS fija de Blackat Systems, sin
WebView, SDK de pagos, parámetros de seguimiento ni identificadores. Desde esa
página la persona puede realizar un aporte voluntario mediante el proveedor
externo que Joaquin apruebe.

El aporte:

- nunca habilita funciones, quita límites, evita feedback ni da prioridad;
- no es requisito para usar la beta o la versión estable;
- no registra monto, estado o historial dentro de MiGuardia;
- no transmite cuentas ni datos laborales;
- se presenta como `aporte voluntario`, sin afirmar que sea una donación
  benéfica o deducible de impuestos.

La URL y el proveedor deben existir y estar verificados antes de incorporar el
botón Android. Costos, términos y obligaciones legales o fiscales son una
puerta web separada.

El 2026-09-05 Joaquin eligió Mercado Pago y proporcionó como destino externo
`https://link.mercadopago.com.ar/blackatsystems`. Esto fija la selección, no
acredita una operación de pago ni una verificación del destinatario. La consulta
automatizada no pudo abrir el enlace; antes de habilitarlo se debe comprobar
destinatario, posibilidad de elegir el importe, costos y condiciones aplicables.
Se mantiene el recorrido Android → página HTTPS de Blackat Systems → proveedor;
no se sustituye la página propia por un cobro embebido o un enlace directo desde
Android. Publicación y botón Android siguen siendo bloques posteriores.

Ese mismo día Joaquin aportó una captura de la pantalla del proveedor: permite
ingresar el importe y muestra `BlackcatSystems` como nombre visible. Esta
evidencia complementa la selección del enlace; no verifica titularidad legal,
costos efectivos, límites, datos compartidos ni una operación completada.
La marca propia sigue siendo Blackat Systems; no se modifica por el nombre
mostrado en el proveedor. La captura de la sesión no se incorpora al repositorio.

MAIN cerró localmente el bloque 26 para `/miguardia/apoyar` el 2026-09-05 en
`website/eae7d54`, en español e inglés y con un enlace discreto desde el footer
de la página pública. Su auditoría repitió 64/64 pruebas y build, sin cambiar
este contrato. La URL propia prevista
es `https://blackatsystems.com/miguardia/apoyar`; prepararla localmente no
acredita su publicación. Mercado Pago se abre sólo mediante el clic explícito,
sin importe, identificadores, integración de cobro ni afirmación de pago recibido.
MiGuardia no procesa datos de pago; no se promete anonimato frente al proveedor
o al receptor de la operación. Los costos, condiciones aplicables y publicación
fueron puertas independientes antes del botón Android.

Actualización del 2026-09-05: Joaquin confirmó que `BlackcatSystems` es su
cuenta receptora y que revisó y acepta las comisiones y condiciones del
proveedor. Es una confirmación del titular, no una auditoría legal/fiscal de
MAIN ni una operación de pago. Autorizó la publicación acotada a presentación y
Aportes: Site existente, versión 10, fuente `website/341ea0a`, resultado
`succeeded` y 42/42 controles HTTPS. La URL propia ya está disponible.

El perfil público excluye la autoridad beta: `/miguardia/beta` sólo informa
que está en preparación, sin formulario, APIs, claves ni descarga de APK.
No se aprovisionaron D1/R2 ni secretos. El alojamiento añade una cookie técnica
`__cf_bm`; no se promete anonimato ante el navegador, hosting o proveedor.
Esta evidencia habilita preparar el botón Android, no publicar la beta ni
cambiar sus contratos. El bloque 27 se limita al enlace fijo propio, sin
transferir identificadores y respetando las puertas existentes al volver del
navegador.

### Orden de las puertas

La secuencia es:

1. portal beta local, contrato API y diez claves de acceso de prueba;
2. página pública `/miguardia` para explicar el producto; el único llamado de
   petición beta queda al final y lleva al portal privado;
3. acceso Android y devolución obligatoria a los quince días;
4. destino web de aporte voluntario;
5. botón visible `Apoyar MiGuardia` en Android;
6. aviso y descarga manual de actualizaciones preservando datos;
7. preparar la APK `2.0.0-beta.1` / código `2` sin firma final;
8. crear y custodiar la clave Release del APK;
9. firmar, verificar y probar la actualización física;
10. subir el APK a almacenamiento privado y publicar el portal;
11. ejecutar el piloto, corregir y distribuir betas posteriores si hacen falta;
12. promover a `2.0.0` estable sin perder datos;
13. decidir por separado tag, GitHub Release y movimiento de `main`.

Cada punto es una puerta propia. Preparar código no autoriza crear recursos,
secretos, firmar, desplegar, publicar ni operar sobre producción.

## Alternativas descartadas

### Incrustar las diez claves en el APK

Se descarta porque podrían extraerse y no existiría un canje atómico entre
instalaciones.

### Usar una clave beta descartable para firmar

Se descarta porque cambiar el certificado impide una actualización normal y
puede obligar a desinstalar, borrando los datos locales.

### Confiar sólo en un enlace oculto

Se descarta porque no limita plazas ni evita redistribuir el APK. Las claves
reducen el acceso casual, pero tampoco constituyen DRM invulnerable.

### Bloquear indefinidamente por falta de conexión al enviar feedback

Se descarta porque una caída del servicio no debe secuestrar datos laborales.
La acción de completar el formulario sigue siendo obligatoria; la confirmación
remota queda pendiente y condiciona actualizaciones posteriores.

### Integrar pagos dentro de la aplicación

Se descarta porque agrega credenciales, dependencias y obligaciones que no son
necesarias para un aporte voluntario externo.

### Publicar directamente `2.0.0`

Se descarta porque ocultaría que el producto está en prueba. La promoción a
estable ocurre sólo después del piloto y sus correcciones.

## Consecuencias

- La app necesita una excepción de red estrictamente limitada a acceso beta,
  feedback y consulta de actualización; los datos laborales siguen siendo
  locales.
- El servicio beta administra plazas, no cuentas personales ni sincronización.
- La primera instalación funciona sólo después de un canje online exitoso; el
  uso cotidiano posterior sigue siendo local y offline.
- La clave de firma del APK, la clave privada que firma comprobantes, el pepper
  HMAC y los códigos de acceso requieren custodias separadas.
- El mismo certificado y un `versionCode` creciente permiten actualizar sobre
  la beta sin desinstalar ni perder información.
- Retirar un enlace detiene nuevas descargas, pero no recupera copias del APK ya
  descargadas. No se promete impedir toda redistribución o modificación.
- Una futura publicación en Google Play deberá volver a revisar su política de
  pagos. El aporte no otorga contenido ni ventaja digital y la variante Play
  deberá poder excluir el enlace si la política vigente lo exige.
