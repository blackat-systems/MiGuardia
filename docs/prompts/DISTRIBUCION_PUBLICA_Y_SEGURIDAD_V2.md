# Distribución pública y seguridad V2

> Referencia de requisitos para la etapa 15. El estado, la base, los permisos y el próximo trabajo los fija [STATUS](../STATUS.md) con el [plan 3.0](../PLAN_MAESTRO_CIERRE_3_0.md). Las bases, aprobaciones y secuencias fechadas del cuerpo son antecedentes; no reactivan la beta ni encargan repetir etapas recibidas. ADR 0041 conserva distribución pública y feedback voluntario.

Contrato de capacidad completa de la etapa 19. Estado, escritor y recursos
autorizados sólo en STATUS. No crea una tarea por existir este archivo.

## Qué hace y por qué existe

Transformar coherentemente Android y la presentación/descarga web del producto
beta privado a distribución pública sin cuenta, activación, plazas ni feedback
obligatorio, conservando actualización segura, datos y controles locales.
Auditar y corregir riesgos reproducibles de esa candidata antes de firmarla.
No basta ocultar una pantalla o publicar el APK beta anterior.

## Entradas y contratos

Proyecto Android: `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`, rama
`codex/miguardia-2.0`. Base de decisión verificada:
`080f12e89415253252aa3368f145425eaf5b9242`, entrada limpia. El commit documental
que incorpora este contrato debe comprobarse al despachar; no confundirlo con
un cambio funcional. Un avance explicado de documentación no obliga a reset.

Leer AGENTS, STATUS, LECTURAS_POR_TAREA, WORKFLOW, VALIDACION, planificación §22,
ADR 0041 y el alcance conservado de ADR 0039/0040. Reutilizar cliente 28, bloqueo,
recuperación y pruebas presentes; no reconstruirlos desde worktrees históricos.

Proyecto web relacionado: `C:/Users/Joaquin/Desktop/chatgptprojects/website`.
Antes de modificarlo MAIN debe comprobar su HEAD, cambios preexistentes, AGENTS,
contratos y alojamiento, leer las skills Sites aplicables y fijar ese snapshot
como entrada con su único escritor. Este contrato no incorpora un HEAD web
supuesto ni autoriza sobrescribir trabajo ajeno. No usar el servicio remoto como
fixture de pruebas. Una tarea Android no recibe implícitamente escritura web.

## Paquete de trabajo

Primer encargo concreto: `docs/prompts/DISTRIBUCION_PUBLICA_WEB_Y_CONTRATO_V2.md`.
El proyecto website sólo permite escribir dentro de su raíz: su especialista
produce contrato/API/superficie web y deja Android en lectura. MAIN revisa esa
salida antes de despachar el consumidor Android, que conserva este contrato
marco. No son dos implementaciones de una misma función: son productor y
consumidor con límites de escritura explícitos. El paquete global no se cierra
con la entrega web.

### 1. Contrato común antes de consumidores

Inventariar las dependencias beta de raíz, runtimes, Worker/receivers si existen,
Widget, Copias, navegación, actualizaciones, website y tests. Verificar qué está
realmente publicado sin crear reservas ni enviar datos. Fijar contrato público
versionado de Release/descarga, con campos, errores, límites, autenticidad,
compatibilidad y política de redirecciones; pruebas compartidas y fixtures no
secretas. Definir feedback mínimo con envío consciente y fuera del acceso.

No inventar endpoints incompatibles ni abrir todos los privados. Si contacto,
campos/retención o infraestructura con costos faltan, informar esa decisión y
avanzar únicamente lo independiente; no inventar valores para publicar.

### 2. Transición Android y web de punta a punta

- Raíz: recuperación → Bloqueo → configuración → Ayuda → contenido. No canje,
  identidad obligatoria, timer o feedback que bloquee. Primer arranque offline.
- Eliminar dependencia beta de publicación exterior y destinos sin saltarse
  validaciones locales. Conservar configuración de avisos/Widget y navegación.
- Actualizador público: conservar tamaño/hash/certificado/paquete/versionCode,
  cancelación, presupuestos temporales, límites, caché privada, SAF y regreso.
  No relajar validadores ni sustituir integridad por un botón al navegador.
- Estados previos de beta ausente/válida/corrupta/feedback pendiente: sin pérdida
  o bloqueo, sin reenvío automático ni limpieza destructiva de residuales.
- Página pública bilingüe con CTA final de descarga, sin reserva ni obligación;
  aporte separado. Opciones de feedback voluntario sin datos laborales añadidos.
- Retirar pruebas obsoletas sólo donde su contrato fue reemplazado; sustituirlas
  por las garantías públicas. Mantener pruebas de seguridad y controles conservados.

### 3. Seguridad verificable y correcciones

Producir una matriz de amenazas/control/evidencia/resultado y alcance. Revisar:
secretos del árbol/historial/artefactos; dependencias transitivas y vulnerabilidades
aplicables; componentes Android/URI/intents; privacidad de almacenamiento y
superficies; importación y exportación; SQL parametrizado; límites de entrada;
TLS/redirecciones; API/cabeceras; sesión/admin que permanezcan en web; distribución,
autenticidad y downgrade. Clasificar cada punto de la lista del usuario como
aplicable, no aplicable con razón, verificado, hallazgo o pendiente.

Usar MASVS/MASTG y ASVS pertinentes. No declarar seguridad por cantidad de tests
ni confundir seis avisos de versiones con CVE. Escáneres sólo locales o consultas
de metadatos públicos aprobadas; no enviar repositorio, secretos o APK a servicios
de análisis por inferencia. Si se descubre un secreto, no imprimirlo: registrar
ubicación/tipo y pedir la operación material de revocación necesaria. No reescribir
historia. Sin auditoría intrusiva de servicios de terceros ni pagos reales.

Corregir defectos reproducibles dentro del alcance. Una biblioteca nueva,
cifrado adicional, migración o almacenamiento remoto exige justificar amenaza,
privacidad, licencia, tamaño y recuperación, y resolver la decisión material.
No implementar RLS/login ficticios para Room ni prometer cero exploits.

## Fronteras y coordinación

Un escritor por archivo, MAIN integra. Separar responsables por capacidad real,
no por botón o test. Android/web pueden trabajar sobre contrato común cerrado;
antes no deben inventar interfaces en paralelo. Una sola compilación Gradle y
un solo operador de dispositivo. No crear chats/ramas/worktrees por este texto.

En Android: cambios de raíz/beta/updates/avisos/Widget y pruebas necesarias;
preservar Core, Room V6, seis esquemas y 17 preferencias portables salvo defecto
demostrado y decisión explícita. Website: sólo tras su Puerta 0 propia. MAIN
reserva estado/contratos canónicos; especialistas entregan evidencia del delta.

No firmar, publicar, abrir cupos, generar claves, borrar recursos remotos ni
instalar/limpiar paquetes durante preparación local. No reutilizar autorización
del AVD anterior para un dispositivo distinto. La clave APK permanente ya existe.

## Aceptación

- Instalación nueva utilizable sin activación/red; recuperación y Bloqueo respetados.
- Descarga/actualización públicas interoperables sin identidad de instalación;
  todos los rechazos de integridad/compatibilidad conservados.
- Feedback voluntario no bloquea y sólo envía lo confirmado; aporte independiente.
- Upgrade desde candidata previa con datos y estados beta preservados según contrato.
- Ningún fallo abierto de pérdida/exposición sensible, autenticidad, actualización
  o bloqueo principal. Residuales clasificados y aceptados, no escondidos.
- Perfiles local, interop y build/full de VALIDACION según cambios, XML frescos,
  hashes de insumos, catálogo QA vigente; web test/tipos/lint/build pertinentes.
- QA proporcional autorizada. Instrumentación compilada no equivale a ejecución;
  doble versión Release firmada y upgrade real siguen en etapa 21.
- Diff íntegro revisado, datos y secretos fuera de Git, límites claros. No
  atribuir evidencia beta anterior al nuevo flujo público por semejanza.

## Entrega a MAIN

Qué hace/por qué, contrato compartido final, delta/base por proyecto, pruebas y
hallazgos corregidos, auditoría de seguridad con amenazas no cubiertas, estado
residual beta y datos preservados, APK sólo compilados y QA real diferenciada.
Sin staging/commit del especialista en checkout compartido. MAIN integra tras
revisión; etapa 19 aceptada habilita congelar/firmar en 20, no publica por sí sola.
