# ADR 0041: distribución pública desde la web sin activación

- Vigencia: VIGENTE
- Sucesores: ninguno
- Alcance vigente: Sustituye el acceso privado, cupos, feedback obligatorio y autorización beta de descarga de ADR 0039/0040. Conserva privacidad, aporte voluntario, firma e instalación manual verificada. Implementación y evidencia sólo en STATUS.
- Fecha: 2026-09-08
- Autoridad: Joaquin confirmó descarga pública desde su web, sin activación y con feedback voluntario.

## Decisión de producto

El recorrido público será página `/miguardia` que explica funciones, privacidad
y límites → descarga oficial → instalación → uso sin cuenta, clave de acceso
ni canje online. Google Play no forma parte de esta entrega; no se descarta
para siempre. No se exige probar que alguien leyó la página ni se promete
impedir enlaces directos o redistribución de una APK pública.

Se retiran diez plazas, reservas, plazo de inscripción, identidad obligatoria
de instalación, compromiso a quince días y cualquier condicionamiento del uso
o la actualización a una opinión. Feedback pasa a ser voluntario, iniciado y
confirmado por la persona; rechazo, silencio, falta de red o caída del servicio
no afectan funciones. Sin recopilación automática de datos laborales, adjuntos,
logs privados ni identificadores de instalación para opinar.

`Apoyar MiGuardia` permanece visible y voluntario, con el recorrido externo y
proveedor ya aprobados; no otorga ventajas ni registra pagos dentro de la app.

## Arranque y transición

Orden objetivo: recuperación de Copias → Bloqueo local, si corresponde →
configuración laboral → Ayuda inicial → contenido. Avisos, Widget, configuración
exportada, destinos pendientes, retorno SAF/navegador y restauración deben dejar
de depender de beta, sin saltarse recuperación, bloqueo o validación de destino.

No alcanza con ocultar la pantalla de acceso ni fabricar un comprobante válido.
La dependencia se retira del cliente de actualizaciones, runtimes, pruebas y
mensajes, preservando sus garantías no relacionadas con el acceso privado.

La actualización desde instalaciones de ensayo con estado beta ausente, válido,
corrupto, vencido o feedback en cola no debe borrar datos ni quedar atrapada.
No crear nuevas identidades beta. Detener reintentos automáticos del protocolo
retirado; no enviar ni borrar silenciosamente opiniones/identidades residuales.
Su tratamiento debe quedar explícito antes de cualquier limpieza de datos.
No borrar Room, preferencias, fotos, bloqueo, configuración o claves ajenas.

## Descarga y actualización públicas

La consulta consciente de versiones y la descarga ya no requieren cookie de
reserva, comprobante beta, desafío de instalación ni feedback recibido.
Android y web deben acordar el contrato público exacto antes de implementarlo;
no relajar globalmente la API privada ni inventar rutas incompatibles.

Reutilizar del cliente 28 transferencia acotada/cancelable, manejo de errores,
almacenamiento privado, comprobación de tamaño, SHA-256, paquete, versión,
compatibilidad y certificado. Guardado mediante SAF y apertura manual desde
Archivos; sin instalador propio ni permisos nuevos por este cambio.

HTTPS y certificado de firma Android conservan su función. Un hash alojado
junto al APK no es una autoridad independiente frente a compromiso del servidor.
La auditoría debe evaluar metadatos, autenticidad del artefacto, rechazo de
downgrade y protección de las credenciales de publicación. No mezclar clave APK
con credenciales web ni incluir privadas en la app.

Mismo applicationId y certificado permanente; versionCode creciente. La clave
APK existente se conserva, no se regenera. La meta sigue siendo `2.0.0`, no
`3.0.0` por cambiar de canal. Retirar beta no acredita estabilidad: nombre/código
final se fijan al congelar una candidata aceptada, verificando las versiones
realmente instaladas/distribuidas. No cambiar Gradle sólo por esta decisión.

## Seguridad y privacidad como condición de entrega

Evaluar amenazas para app Android, importaciones/exportaciones, web, cadena de
compilación, servidor de descargas y actualización. Usar controles pertinentes
de OWASP MASVS/MASTG y ASVS para web; no aplicar RLS o login artificialmente a
Room local ni inventar una base remota laboral.

- Examinar secretos del árbol, historial y artefactos sin imprimir sus valores.
  Si se detecta exposición, detener la publicación, revocar/rotar según autoridad
  y acordar saneamiento; no reescribir Git preventivamente.
- Revisar dependencias directas/transitivas y vulnerabilidades aplicables. Lint
  de versiones y conteo JVM no equivalen a un escaneo de vulnerabilidades.
- Verificar intents/componentes, archivos/URI, validación y SQL parametrizado,
  límites de importación/descompresión, privacidad de avisos/Widget/Recientes,
  transporte, respuestas, cabeceras y superficie administrativa del sitio.
- Evaluar almacenamiento sensible y recuperación. La base Room actual no
  configura cifrado adicional: bloqueo visual, aislamiento Android y copias
  cifradas son capas diferentes. Una nueva estrategia de cifrado requiere
  justificar amenazas, claves, compatibilidad, recuperación y pruebas; no se
  autoriza introducir dependencias o una migración destructiva por inferencia.
- No publicar con fallos abiertos de pérdida de datos, exposición sensible,
  autenticidad de APK, instalación/actualización o bloqueo del flujo principal.
  Otros residuales requieren clasificación, evidencia y aceptación explícita.

No prometer invulnerabilidad, anti-exploits absoluto ni DRM. El cierre se basa
en controles ejecutados y límites declarados; seguimiento y correcciones
continúan después de distribuir.

## Web, soporte y recursos

La página bilingüe sustituirá su CTA beta final por descarga oficial, sin
formulario obligatorio. Reutilizar el sitio existente. Retirar el recorrido
público de reservas y activaciones sin destruir bases/recursos remotos ni datos
preexistentes; verificar primero qué está realmente desplegado.

Faltan definir el canal público de privacidad/soporte y el mecanismo mínimo de
feedback voluntario, sus campos, retención y tratamiento. No inventar contacto,
plazo ni autorización de marketing. El cupo y cierre de inscripción dejan de
ser decisiones pendientes: ya no existen en el producto aprobado.

## Dependencias y aceptación

Etapa 19: transición coherente Android/web + contrato público + revisión y
correcciones de seguridad. Etapas 20–22: candidata firmada → QA de upgrade real
con datos poblados → publicación exacta verificada. Etapas 23–24: seguimiento
voluntario y mantenimiento de una entrega estable, no piloto obligatorio.
Los hitos no implican chats separados ni obligan a reimplementar lo conservado.

La decisión autoriza su preparación y adaptación local. Conserva las puertas
de dispositivo, recursos, costos, secretos y publicación del alcance concreto;
no traslada un permiso de ensayo QA a datos reales ni una autorización anterior
de push. El código actual sigue beta hasta aceptar su reemplazo; decidir,
implementar, probar, firmar y publicar son hechos distintos.
