# MiGuardia 3.0 — reglas permanentes

## Entrada y autoridad

1. Leer este archivo y `docs/STATUS.md`. El estado autorizado vive sólo en STATUS.
2. Usar `docs/LECTURAS_POR_TAREA.md` para elegir contrato, decisiones, código y pruebas pertinentes.
3. MAIN orquesta por `docs/ORQUESTACION_MAIN.md`: respetar sus etapas y dependencias, tomando el cursor y permisos de STATUS. Leer la ruta al entrar y sólo la etapa pertinente al reanudar; no cargar el Mermaid de presentación ni inventarios históricos.
4. Para integrar o delegar, consultar `docs/WORKFLOW.md`; para comprobar, `docs/VALIDACION.md`.

No releer toda la documentación al reanudar: comprobar Git y cambios desde la última base conocida. Leer de nuevo los contratos modificados o cuyo contexto falte. `docs/archive/`, auditorías y prompts cerrados son antecedentes, no lectura de arranque ni órdenes ejecutables. Las rutas antiguas se conservan como enlaces de compatibilidad.

Autoridad: instrucción actual de Joaquin; decisiones de producto vigentes en `docs/PLANIFICACION_MIGUARDIA_2_0.md`; fichas sectoriales para evidencia; ADR vigentes para contratos técnicos; estas reglas y WORKFLOW para procedimiento; código y pruebas para implementación real. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md` resume el producto sin otra hoja de estado. Un texto archivado no reemplaza su sucesor. Si queda una decisión material irresoluble, explicarla y preguntar; corregir incoherencias documentales evidentes dentro del alcance autorizado.

## Producto e invariantes

- Android, Kotlin y Compose; tres módulos: `app`, `core/domain`, `core/database`.
- Cuatro sectores: Vigilancia privada, Policía, Enfermería y Medicina. Una sola configuración laboral; sin `Otro`, perfiles simultáneos ni fórmulas universales por profesión.
- V2 comienza con datos nuevos. Runtime exclusivamente V2 y base `MiGuardiaV2Database` / `miguardia-v2.db`; no existe migración, adopción o activación de datos V1. Conservar componentes heredados que siguen siendo útiles.
- Cambios de reglas desde fecha local explícita; jornadas con inicio y fin reales e instantáneas históricas. Editar plantillas o planes no reescribe el pasado.
- Trabajar en minutos e intervalos exactos, separando negocio de Android. Toda extra es expresa; superar una referencia no la crea. No usar 204 horas ni 21:00–06:00 como valores predeterminados V2; sí como pruebas o ejemplos explícitos.
- Preservar datos V2 mediante migraciones Room explícitas y esquemas exportados. No reformatear esquemas por estilo. Una sola grilla de Calendario y consulta sin escrituras.
- No incorporar sueldos, montos laborales, liquidaciones, deducciones o datos sindicales. Aporte externo voluntario no modifica funciones ni reglas laborales.
- La planificación fija los detalles funcionales. Nuevas capacidades o cambios públicos necesitan autorización de producto; no ampliar un bloque por inferencia.

## Privacidad y límites

- Datos laborales locales; sin cuentas laborales, nube laboral, sincronización, analítica, telemetría, anuncios ni rastreadores.
- Conexiones aprobadas: Clima opcional, distribución/actualizaciones públicas conscientes y feedback voluntario sin datos laborales; enlaces HTTPS abiertos por la persona. Ver ADR 0038–0041. ADR 0041 retiró del producto el acceso beta, los cupos y el feedback obligatorio; no reintroducirlos por compatibilidad histórica. No crear cuentas ni transmitir información laboral.
- No almacenar DNI, correo, teléfono, matrícula, domicilio personal, datos clínicos ni imágenes de certificados médicos. Nombre/apodo opcional. Notas médicas privadas.
- Permisos en contexto y rechazo manejado. Ubicación sólo aproximada, puntual e iniciada por la persona, o conversión consciente de dirección para Clima; sin seguimiento automático ni segundo plano.
- No exponer datos privados involuntariamente o fuera de la selección expresa de una exportación. Los informes aplican la lista blanca y confirmaciones de ADR 0034. Nunca datos personales, contenido de copias, rutas privadas, claves o tokens en logs.
- Secretos, firmas, APK, copias reales y capturas privadas fuera de Git. Exportar, restaurar o eliminar exige acción consciente y consistencia ante fallo.
- Sin iOS, OCR/importación Excel, mapa embebido, feriados automáticos, búsqueda global ni integración con empleadores dentro del alcance actual. Una ampliación necesita decisión explícita.

## Trabajo, Git y recursos compartidos

- Hablar en español argentino claro: resultado, evidencia y siguiente paso. Diferenciar verificado, inferido y pendiente.
- Puerta 0: ruta exacta, rama/HEAD, cambios rastreados/nuevos/staged, worktrees y remotos. Detenerse ante un mismatch inexplicable. Un checkout intencionalmente modificado se preserva como entrada.
- MAIN integra un bloque autorizado por vez; puede delegar subtareas independientes dentro de ese alcance con contrato acotado, un escritor por archivo y revisión final. Crear una nueva tarea de Codex requiere indicación de Joaquin. No confundir tareas nuevas con revisores internos.
- Delegar capacidades completas de punta a punta, con profundidad acorde al modelo elegido por Joaquin (Astra/ultra en este ciclo). Mantener chats especializados separados por responsabilidades reales; aplicar la granularidad de `docs/WORKFLOW.md`, sin convertir botones, correcciones o pruebas del mismo recorrido en dependencias sucesivas.
- Usar el proyecto guardado; crear ramas/worktrees sólo cuando estén autorizados o se acuerde una necesidad real. No recuperar código de worktrees históricos como sustituto de la base vigente.
- Rama de desarrollo `codex/miguardia-3.0`; `main` conserva la instantánea inicial aceptada del repositorio 3.0. Las referencias, etiquetas y hashes de 1.x/2.x pertenecen al repositorio archivado y sólo sirven como procedencia. Mantener `applicationId = com.blackatsystems.miguardia` hasta otra decisión.
- MAIN puede crear checkpoints locales de un bloque autorizado, revisado y comprobado. Incluir sólo su alcance, sin absorber trabajo previo ajeno. Mensajes Conventional Commits en inglés.
- Push, tag, GitHub Release, firma/distribución, producción y servicios remotos requieren su autorización específica; las autorizaciones anteriores de push están consumidas.
- No descartar cambios ajenos, hacer `reset --hard`, reescribir historia publicada o forzar push. No borrar datos del teléfono por una limpieza de proyecto.
- ADB, Samsung y emuladores requieren autorización de ensayo específica. Un teléfono conectado no la concede. No consultar ni modificar producción, desinstalar o limpiar paquetes por inferencia.
- No añadir dependencias de producción sin justificar necesidad, mantenimiento, licencia, privacidad, tamaño y alternativa nativa.

## Validación y cierre

- Aplicar `docs/VALIDACION.md` según impacto: documentación, lógica local, interoperabilidad, compilación, instrumentación y distribución son niveles distintos.
- Repetir pruebas después de cambios/fallos pertinentes; una evidencia previa sólo sirve si cubre los mismos insumos y superficie. No exigir reconstrucción global en cada iteración por plantilla.
- Si cambia conducta, probar límites y errores relevantes. UI/sistema requieren QA física proporcional autorizada; informar pendiente si no se ejecutó, sin confundir una extracción mecánica verificada con QA nueva.
- Preservar tema claro/oscuro, accesibilidad y zoom interno 100/150/200, elegido por la persona y persistido. Usar densidad estable; no adaptar a `font_scale`, zoom o tamaño de Android ni consultar/modificar esos ajustes en pruebas.
- Revisar diff completo del alcance, no rastreados, permisos, dependencias y ausencia de datos privados antes del cierre.
- Registrar implementación, revisión, commit real, validación local, matriz física y distribución por separado. Actualizar sólo STATUS para estado actual; el contrato conserva requisitos, la auditoría evidencia fechada.
- Cierre breve: qué cambió y por qué, archivos relevantes, comprobaciones reales, límites y próximo paso. No declarar publicación, instalación o prueba que no ocurrió.
