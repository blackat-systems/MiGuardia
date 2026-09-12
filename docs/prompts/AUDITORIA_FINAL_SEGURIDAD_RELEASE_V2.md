# Dependencia SECURITY — auditoría final previa al release

## Qué hace y por qué existe

Actuá como especialista independiente de seguridad de MiGuardia Android y su
distribución web. Auditá los veinte controles pedidos por Joaquin sobre la
candidata real, no sobre una arquitectura genérica de aplicaciones con cuentas.
Entregá un único handoff completo, con evidencias, fallos reproducibles,
prioridades y decisión recomendada de salida. MAIN conserva aceptación y release.

Esta dependencia es de auditoría: no implementar cambios de producto ni aceptar
riesgos por MAIN. Podés crear herramientas y pruebas locales aisladas para
comprobar defectos. No devuelvas veinte búsquedas superficiales ni abras nuevas
dependencias por cada control. Podés usar revisores internos de sólo lectura,
con fronteras Android, web y cadena de distribución; coordiná recursos compartidos.

## Entrada real y preservación

- Android: C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0,
  codex/miguardia-2.0, HEAD e91e6f7394131d622153d9cd8fdbc857e3accfb3.
- Website: C:/Users/Joaquin/Desktop/chatgptprojects/website,
  main, HEAD ae9893894988633cb1c60d7218212510627aa10c.
- Android está intencionalmente modificado por MAIN. Incluye Q01/Q02, herramientas,
  pruebas de QA, corrección de la aserción desplazable del menú y documentación.
  No exigir entrada limpia, no descartarla y no atribuirse esos cambios.
- Website conserva archivos ajenos no rastreados: cuatro tarballs .openai,
  AGENTS.md e INTERFACE_DESIGN. No incorporarlos ni limpiarlos.
- Puerta 0 propia en ambos proyectos: HEAD, rama, índice, diff completo,
  no rastreados, remotos y worktrees. Registrar hashes de entrada de fuentes
  relevantes; detener sólo discrepancias inexplicables. No inventar el inventario
  de salida a partir de este resumen.
- Release oficial firmada: .local-evidence/release/upgrade-api33-20260910/
  sign-q02-code3/signed-unverified.apk. El nombre es histórico: verificación
  separada en q02-signed-verification.json aprobada.
- APK SHA-256: a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a,
  12.713.293 bytes, 2.0.0/code3, com.blackatsystems.miguardia.
- Certificado SHA-256:
  AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D.
- QA Samsung anterior: 530 casos distintos aprobados, límites y cambio de APK
  de instrumentación en docs/audits/2026-09-10-qa-upgrade-publico-main.md.
  No atribuirlos como pentest, prueba propia o validación integral de Release.
- Site existente: appgprj_6a90fe153dac8191b8ea5984b4a7e871. La información
  publicada y el candidato local son superficies distintas. No publicar APK.

## Lectura dirigida

Leer AGENTS, STATUS, LECTURAS_POR_TAREA, WORKFLOW y VALIDACION. Seguir etapa19
en ORQUESTACION_MAIN como puerta final de seguridad, sin reimplementar etapas.
Consultar planificación/ADR0034–0041 sólo en sus contratos pertinentes.
Leer tools/release/README.md, policy.json, security_audit.py y verificador firmado.
En website leer sus propias instrucciones y las skills Sites disponibles antes
de operar el proyecto; API pública, SECURITY y OPERATIONS vigentes. Usar sus
pruebas/oráculos, no inventar otra API ni reactivar servicios beta históricos.

## Método y modelo de amenazas

Distinguir Android instalado, APK para primera instalación, actualización sobre
instalación confiable, archivo de copia hostil, componentes exportados, enlaces,
sitio público, Worker/R2 y administración privada. Identificar activos, actores,
fronteras de confianza y fuentes/sumideros de información laboral.

Considerar teléfono desbloqueado prestado, app maliciosa sin root, archivo
manipulado, web comprometida y dispositivo rooteado como escenarios distintos.
No prometer protección absoluta contra dueño del dispositivo/root ni DRM.
El hash servido junto al APK no autentica independientemente un hosting atacado.

## Matriz obligatoria de los veinte controles

Para C01–C20 informar: aplicabilidad por superficie, evidencia actual con
archivo/línea o artefacto/hash, prueba/resultado, limitación, riesgo y remediación.
Estados: VERIFICADO, HALLAZGO, NO APLICA JUSTIFICADO o PENDIENTE. Nunca verde
por ausencia de una búsqueda textual. Cada N/A debe explicar el flujo real.

| ID | Control y comprobación mínima |
|---|---|
| C01 | Hide API keys: distinguir identificadores/certificados públicos de secretos. Revisar fuentes, recursos, BuildConfig, DEX, bundles y artefactos de distribución; no confiar en ofuscación para guardar privadas. |
| C02 | Purge Git secrets: escanear árboles y blobs alcanzables de ambos repositorios con herramientas existentes y cobertura documentada. No mostrar coincidencias sensibles. Si hay secreto real, proponer revocación/rotación primero; no reescribir historia, borrar evidencias ni rotar por cuenta propia. |
| C03 | Public DB key: determinar si existe cliente conectado a DB remota. Room local no necesita clave pública; no introducir Supabase u otro servicio. Comprobar que bindings/credenciales administrativos nunca llegan al navegador. |
| C04 | RLS: clasificar Room local, D1 histórico y almacenamiento público real; verificar autoridad de acceso efectiva. No exigir RLS de un proveedor inexistente ni suponer que DB histórica sigue expuesta. |
| C05 | Encrypt sensitive data: revisar sandbox/FBE, bloqueo, Recientes, copias cifradas, fotos, temporales, exportaciones y backups Android. Declarar que Room no tiene cifrado adicional propio si sigue siendo cierto. Contraseña de copia, límites criptográficos y clave de firma son dominios distintos; no agregar SQLCipher sin decisión/migración. |
| C06 | Server-side auth: administración y escrituras privadas deben permanecer cerradas. GET público no exige login; comprobar retirada beta, métodos, acceso a objetos y ausencia de endpoints administrativos expuestos. |
| C07 | Lock record access: providers/exportaciones, intents, grants URI, FileProvider, rutas y puertas Bloqueo/recuperación; acceso web exacto a objeto aprobado, sin listado ni lectura arbitraria. |
| C08 | Block field tampering: esquemas cerrados, tipos, duplicados, rangos, selección inmutable, paquete/certificado/versiones/minSdk/ABI, downgrade y cambios entre consulta/descarga. |
| C09 | Secure session cookies: inventariar cookies reales del perfil publicado; no afirmar inexistencia universal por no tener cookie beta. Donde haya sesión, flags/CSRF/origen/caducidad; si no hay sesión, justificar. |
| C10 | Hash passwords: no hay contraseña de cuenta por inventar. Diferenciar autenticador del sistema, KDF de Copias y custodia externa del keystore. Revisar que nunca se logueen/persistan en claro. |
| C11 | Rate limit login: sin login puede ser N/A; verificar 429/503/Retry-After, abuso de descarga, cuotas reales y límites por instancia frente a defensa distribuida. |
| C12 | Bot protection: cuantificar agotamiento/concurrencia/costos del servicio público. No agregar CAPTCHA o fingerprint automáticamente. Identificar configuración remota no comprobada. |
| C13 | Parameterize queries: seguir SQL Room y cualquier consulta servidor activa, interpolaciones, nombres dinámicos y herramientas de importación, no sólo buscar el texto SELECT. |
| C14 | Validate all input: límites en JSON/UTF-8, red, intents, importaciones, ZIP, imágenes, fechas, coordenadas y archivos; incluir negativos y cancelación/carreras en fronteras sensibles. |
| C15 | Escape user content: render React/HTML/URLs, WebView si existe, errores reflejados, informes y celdas XLSX contra fórmulas inyectadas; comprobar sumideros reales y no escapar ciegamente texto Compose. |
| C16 | Restrict file uploads: confirmar si existe carga remota. Auditar Copias/fotos/SAF local por separado: firma/tamaño/MIME, traversal, ZIP bombs, enlaces, duplicados, temporales, sobrescritura, rollback y grants. |
| C17 | Trim API responses: datos mínimos, errores sin trazas/secretos/datos laborales; no transmisión accidental de notas, jornadas, fotos o cola beta retirada. |
| C18 | Security headers: CSP y sus excepciones, nosniff, referrer, frame-ancestors, HSTS según superficie, caché y Content-Disposition. Separar configuración local de respuesta desplegada; analizar compatibilidad y huecos, no contar cabeceras. |
| C19 | Force HTTPS: origen fijo y validación TLS, cleartext Android, redirecciones y orígenes externos, enlaces conscientes. Drive propuesto no sustituye el contrato aprobado. No deshabilitar TLS ni agregar pinning arbitrario. |
| C20 | Scan dependencies: Maven y npm con versiones resueltas, runtime/build/transitivas, fecha y fuentes primarias vigentes; revisar overrides y perfil realmente distribuible. Cero avisos conocidos no significa invulnerabilidad. |

## Permisos y límites de ejecución

Lectura de ambos checkouts y pruebas locales aisladas autorizadas. Escritura sólo
en .local-evidence/security-final/ de Android para informe, scripts y fixtures
sintéticas propias. No modificar producto, Gradle/lockfiles, docs canónicos o
web; devolver parches propuestos si corresponde. Un solo Gradle; no competir
con otros escritores o validadores. No abrir tarea/rama/worktree nuevos.

Consultas a avisos públicos de seguridad: sólo nombres/versiones públicos y
documentación primaria; reutilizar autorización previa de consulta npm/OSV.
Nunca subir repositorios, APK, claves, notas o resultados sensibles a escáneres
externos. No leer almacenes de firma, contraseñas, D:/ ni perfil del navegador.
Un hallazgo se registra redactado mediante tipo/ruta/id, nunca valor secreto.

Sin ADB, Samsung, emuladores, instalaciones, borrados, firma, push/tag/commit,
deploy, provisión, pagos, tráfico de ataque o carga sobre producción. Para una
comprobación que requiera esa superficie, entregar procedimiento acotado y
dejar PENDIENTE; no presentarla como verificada localmente. No hacer HTTP remoto
contra el Site en esta dependencia: MAIN cierra luego la diferencia remota.

## Validación y criterio de terminado

Reutilizar evidencia sólo con hashes/insumos/superficie coincidentes; separar
pruebas nuevas de heredadas. Ejecutar las comprobaciones locales necesarias para
los controles, no reconstruir por ritual ni sustituir revisión por volumen de
tests. Las pruebas de explotación usan exclusivamente datos sintéticos y
servidores locales descartables; nunca el Samsung ni servicios de terceros.

Cada hallazgo: severidad, precondiciones, superficie/versión, reproducción mínima,
resultado esperado/real, impacto, corrección recomendada y prueba de regresión.
Determinar si afecta bytes ya firmados y qué evidencia/firma/QA habría que renovar.
No minimizar por ser anterior ni confundir dependencia vulnerable instalada con
explotabilidad demostrada. Prioridades justificadas; cero falsos verdes por N/A.

Terminar con matriz 20/20, inventario de alcance, cobertura explícita del artefacto
firmado y perfil web, hallazgos y residuales. Veredicto: BLOQUEADO o SIN BLOQUEANTES
LOCALES IDENTIFICADOS; nunca APROBADO PARA PUBLICAR si quedan puertas remotas o
upgrade sin cerrar. Una aceptación de riesgo sólo puede atribuirse a MAIN/Joaquin.

## Entrega única a MAIN

Crear .local-evidence/security-final/HANDOFF_A_MAIN.md y evidencias en subcarpeta
nueva fechada, sin sobreescribir antecedentes. Si ya existe handoff, conservarlo
y elegir ruta nueva. Incluir resumen entendible, matriz, findings, comandos y
salidas resumidas, fuentes/fechas de avisos, hashes, preservación y checklist
exacto de pendientes para release. No ocultar fallos intermedios ni credenciales.
Devolver escritor a MAIN. La entrega no realiza integración ni publicación.
