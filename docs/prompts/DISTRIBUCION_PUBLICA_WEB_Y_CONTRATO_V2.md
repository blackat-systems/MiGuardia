# Distribución pública web y contrato de actualización

> Referencia de requisitos para la etapa 15. El estado, la base, los permisos y el próximo trabajo los fija [STATUS](../STATUS.md) con el [plan 3.0](../PLAN_MAESTRO_CIERRE_3_0.md). Las bases, aprobaciones y secuencias fechadas del cuerpo son antecedentes; no reactivan la beta ni encargan repetir etapas recibidas. ADR 0041 conserva distribución pública y feedback voluntario.

## Rol y resultado

Sos la dependencia especialista WEB de MAIN para la etapa 19 de MiGuardia.
Resolvé esta capacidad de punta a punta, no solamente una página o propuesta:
contrato público, implementación local web, retiro del recorrido beta privado,
errores, privacidad, pruebas, revisión de seguridad y handoff para Android.

Este encargo es local: **no publicar, firmar, aprovisionar ni usar dispositivos**.
No crees otra tarea, rama o worktree. No hagas staging, commit, push, tag ni
reescribas historia. MAIN integra después de revisar. Una skill de hosting no
amplía estos límites explícitos.

### Qué hace

Prepara el sitio existente para explicar MiGuardia y ofrecer una APK oficial
pública, sin cuenta, reserva, clave, diez plazas ni opinión obligatoria. Define
e implementa el contrato que permitirá al cliente Android consultar versiones
y descargar actualizaciones sin identidad beta, conservando autenticidad,
compatibilidad y errores inequívocos.

### Por qué existe

El producto cambió a distribución pública. El actualizador Android todavía
consume comprobantes y desafíos beta. Primero debe existir un contrato público
web comprobado para que Android pueda adaptarse sin inventar una segunda API.
Los controles contra pérdida de datos o APK ajenos no se retiran con la beta.

## Puerta 0 y raíces

**Única raíz de escritura:**
`C:/Users/Joaquin/Desktop/chatgptprojects/website`

Entrada web verificada por MAIN el 2026-09-08:

- Rama `main`.
- HEAD `80ebafd9945f51b13b05c708eec50c22fdf0a52b`.
- Sin remotos; un worktree; staging vacío.
- Cambios ajenos existentes: `app/globals.css`, `app/layout.tsx`, `app/page.tsx`.
- No rastreados protegidos: `AGENTS.md`, `INTERFACE_DESIGN/` y cuatro archivos
  `.openai/site-build-{2d30584,397a7ed,b43685f,b83015d}.tar.gz`.
- Site existente `appgprj_6a90fe153dac8191b8ea5984b4a7e871`; bindings lógicos
  `DB` y `BETA_APKS`. Eso no demuestra recursos remotos provisionados.

Leer íntegramente AGENTS web y skills Sites building/hosting con sus referencias
aplicables. Preservar identidad, assets, dependencias y hosting. No inicializar
otro sitio. La regla de escritura incluye compilaciones, cachés, reportes,
temporales y herramientas: todas sus salidas dentro de website.

SHA-256 de entrada para preservar el trabajo previo:

| Archivo | SHA-256 |
|---|---|
| `app/globals.css` | `E179E14FAE129171E24EEEFC417900EB87957314E73A38CF8A51FD7ACCFC2980` |
| `app/layout.tsx` | `FB757520882C51DE7E9E838A21EECCFB7959AD80F435500F62E8218AEB6D2E9D` |
| `app/page.tsx` | `6858CCFECB62D5B2C14DD8798AF577A6BC57FA380C801AE28CA5A8EF951914B5` |
| `website/docs/MIGUARDIA_BETA_API_V1.md` (contrato del proyecto web) | `2B821D750008BC449E491A25E0CFD8C1E900C06BE8DEB831FEE4C5EEFD6DD7B0` |
| `.openai/hosting.json` | `9966872AAEB7C2B2DA571ADC4F93AAEBD23D56F34595D97ACC2D7918E3A502F0` |

Capturá además hashes de todos los archivos protegidos antes de editar, diff
rastreado/staged y estado de no rastreados en evidencia privada local web.
Ante una diferencia inexplicada, detené sólo la escritura afectada y consultá;
no hagas reset, clean ni descartes para obtener una entrada limpia artificial.

**Android es exclusivamente de lectura:**
`C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
Base documental comprobada `9d1c4a6095e1f8131ae2ac32f26fb127437833e8`;
descendientes documentales explicados de preparación son admisibles después de
comprobar su delta. No ejecutar Gradle, tests, generadores ni escribir evidencia
allí. No delegar esas escrituras. La adaptación Android es el consumidor posterior.

## Fuentes de verdad

En Android, leer STATUS, planificación §22, ADR 0041 y partes conservadas de
0039/0040; contrato marco `docs/prompts/DISTRIBUCION_PUBLICA_Y_SEGURIDAD_V2.md`.
Consultar `beta/BetaUpdateAccess.kt`, `updates/UpdateProtocol.kt`,
`UpdateTransport.kt`, `UpdateService.kt` e inspección/verificación del APK para
conocer el consumidor, sin modificarlo.

En web, leer contratos API/operación existentes, rutas beta, servicio/validación,
perfil de publicación pública, tests y scripts pertinentes. No reconstruir toda
la historia ni aceptar el handoff previo como prueba de un despliegue actual.

## Alcance ejecutable

### A. Contrato público estable

Crear `MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md` en la carpeta `docs` de website como contrato exacto para
Android: endpoints/origen, métodos, JSON y límites, versión de contrato,
disponibilidad, códigos de error, cabeceras/caché, tamaño/SHA-256, paquete,
versionCode/versionName, minSdk, certificado y compatibilidad aplicable.

Reutilizar campos y verificaciones válidos existentes. Justificar diferencias
frente a API beta, sin cambiarla globalmente a permisiva. Definir metadatos y
descarga públicas sin cookie de reserva, credencial de instalación, desafío,
opinión ni activación. Administración/publicación permanece privada.

Documentar claramente `sin publicación`, `publicación incompatible`, error
temporal, límite y respuesta inválida. 404/503/TLS inválido no significan que la
app esté actualizada. Origin/URL permitidos cerrados; sin open redirect, URL de
descarga arbitraria, traversal ni fetch de destinos aportados por visitantes.

El hash de un APK y sus metadatos alojados juntos no constituyen por sí solos
una autoridad independiente frente a compromiso del hosting. Mantener la
continuidad del certificado Android y evaluar controles de metadatos/release.
No introducir firma casera, privadas embebidas ni nuevas claves productivas.
Si hace falta una nueva autoridad criptográfica material, elevar esa decisión
sin bloquear los controles independientes.

### B. Implementación local completa

- `/miguardia`: conservar composición, contenido real, español/inglés, privacidad
  y accesibilidad; reemplazar la solicitud beta final por descarga oficial.
- Mientras no exista una Release firmada aprobada, mostrar honestamente que
  todavía no está disponible; no ofrecer Debug/QA ni inventar una publicación.
- El mismo estado de publicación gobierna página y API; pruebas usan sólo
  fixtures identificables y aisladas, nunca una APK falsa anunciada al público.
- Resolver primera descarga y actualización pública, streaming/límites/errores
  y acceso al archivo exacto. No exponer listados de almacenamiento o D1.
- Retirar del recorrido servido las reservas, activaciones, cupos y obligación
  a quince días. Rutas antiguas con respuesta deliberada/documentada, sin filtrar
  registros ni seguir aceptando reservas de un programa retirado.
- Preservar físicamente datos/tablas/contratos históricos. No borrar D1/R2,
  migraciones, estados beta ni archivos remotos por inferencia.
- Conservar `/miguardia/apoyar` y su salida consciente a Mercado Pago, sin
  relación con acceso/actualización, sin pagos reales ni rastreo nuevo.

Preparar feedback voluntario como propuesta mínima documentada, desacoplada del
acceso. Aún no están aprobados correo público de privacidad/soporte, mecanismo,
campos ni retención: **no inventarlos ni activar recolección real**. Se pueden
probar interfaces/contratos con datos sintéticos e implementación deshabilitada,
dejando esta puerta explícita. La falta de estas decisiones no justifica parar
el trabajo independiente de descarga/actualización; tampoco permite declararlas
resueltas o publicables. No reenviar automáticamente colas antiguas.

### C. Seguridad web y del contrato

Auditar y corregir defectos reproducibles del alcance. Entregar matriz con los
20 controles solicitados: secretos API; secretos Git; claves DB; RLS; cifrado;
autenticación servidor; acceso a registros; manipulación de campos; cookies;
contraseñas; límites de intentos; bots; SQL parametrizado; validación; escape de
contenido; archivos; respuestas mínimas; cabeceras; HTTPS; dependencias.

Para cada control: aplicabilidad, amenaza, ubicación, prueba, resultado y límite.
Usar ASVS/guías OWASP pertinentes y MASVS para la frontera Android observada;
no declarar una auditoría Android ejecutada desde website. No crear login, RLS o
CAPTCHA sin necesidad para una descarga pública.

Comprobar historial/árbol/artefactos sin mostrar valores secretos; vulnerabilidades
conocidas aplicables en dependencias directas/transitivas, no sólo actualizaciones
disponibles. Registrar herramienta, versión, fecha, alcance y limitaciones.
No subir código/APK/secretos a escáneres externos. Consultas de metadatos públicos
no autorizan enviar archivos privados. Un secreto expuesto exige detener la
publicación y reportar ubicación/tipo para revocación; no reescribir Git.

Administración, consultas y almacenamiento privados; errores sin filtraciones,
límites de cuerpo/transferencia, cabeceras/CSP, caché de metadatos/archivos y
comportamiento ante concurrencia o cambio de publicación durante la descarga.
Sin SSRF, raw SQL con entradas concatenadas, bypass TLS ni promesas de anonimato
frente al proveedor. Cero nuevas dependencias o costos sin decisión justificada.

## Archivos y restricciones

Permitidos dentro de website: rutas/superficies MiGuardia y API correspondientes,
servicios relacionados, pruebas, fixtures aisladas, scripts de perfil público
necesarios y documentación contractual/operativa web.

Prohibidos: cambiar globalmente branding/portfolio, `app/globals.css`,
`app/layout.tsx`, assets ajenos, tarballs, AGENTS, historia Git, configuración de
hosting y recursos remotos. `app/page.tsx` admite únicamente el hunk de tarjeta
MiGuardia si hace falta reconciliar su promesa: retirar ese hunk en memoria debe
reproducir el hash inicial. No formatear el archivo entero. Manifiestos/lockfiles
sólo con necesidad material justificada y aprobada, no actualizaciones masivas.

No ejecutar despliegue, guardar versión remota, pedir credenciales, generar
firma, cargar APK oficial, hacer pago, abrir reserva ni tocar dispositivos.
Pruebas con almacenes/fixtures locales separados: no reiniciar `.wrangler/state`
preexistente con `--fresh` sin aislamiento comprobado. Salidas/cachés dentro de web.

## Validación y aceptación

1. Contrato/API probados con ausencia de Release, válida, incompatible, corrupta,
   cambio concurrente, métodos no permitidos, entradas enormes, redirecciones,
   path inválido, cancelación, error de almacenamiento y caché obsoleta.
2. Descarga sin cookies/clave beta; endpoints administrativos siguen cerrados;
   rutas beta antiguas no admiten nuevas plazas ni revelan datos.
3. Fixtures/oráculos de contrato exportables y documentados para los tests de
   Android posteriores. No afirmar interoperabilidad JCA nueva sin ejecutarla.
4. `npm test`, tipos, lint y build vigentes; base/esquema sólo si corresponde.
   Browser QA local de página→descarga/sin publicación→regreso, apoyo, ambos
   idiomas, teclado/foco, móvil/escritorio y zoom. Registrar diferencia entre
   viewport equivalente y zoom real, entre semántica y lector de pantalla.
5. Preservación exacta de archivos protegidos, Android sin escrituras y staging
   vacío. Auditoría de seguridad sin fallos abiertos de exposición, autenticidad,
   autorización administrativa o bloqueo principal. Otros riesgos explícitos.
6. Toda evidencia local diferenciada de publicación, instalación y upgrade real.
   Si faltan decisiones de feedback/soporte o pruebas remotas, declararlas sin
   cerrar la etapa 19 global. La salida web puede quedar lista para revisión del
   contrato y posterior adaptación Android sin fingir entrega pública terminada.

## Handoff

Entregar `MIGUARDIA_PUBLIC_DISTRIBUTION_OPERATIONS.md` en la carpeta `docs` de website y handoff compacto:
qué hace/por qué, bases y delta, contrato exacto, inventario de cambios propios,
pruebas frescas/hashes, riesgos de seguridad, preservación, pendientes materiales,
rutas beta residuales y pasos operativos aún no ejecutados.

MAIN debe poder revisar el contrato y despachar Android sin redescubrirlo. No
devolver sólo un diseño si el código local puede completarse dentro del alcance;
no fraccionar cada error/test en otra dependencia. Trabajar con profundidad de
Astra/ultra, manteniendo fronteras, un escritor y evidencia. Al terminar, detenerse
en el handoff: no integrar, publicar ni iniciar la tarea Android por cuenta propia.
