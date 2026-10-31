# Inventario de procesos reutilizables y biblioteca personal

Fecha: 2026-10-24. Pedido directo de Joaquín: identificar procesos utilizados y reutilizados en MiGuardia 3.0 y, si era posible, 2.0; extraer skills utilizables en futuros proyectos y seleccionar la pertinente en cada caso.

Este informe registra extracción de procedimientos. El estado de producto y Stage12 continúa exclusivamente en [STATUS](../STATUS.md). No reabre contratos históricos ni registra QA nueva de la aplicación.

## Base y cobertura

| Fuente | Estado observado al iniciar |
|---|---|
| MiGuardia 3.0 | `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`, cambios acumulados intencionales y staging vacío |
| MiGuardia 2.0 | Sibling `MiGuardia-2.0`, rama `codex/miguardia-2.0`, HEAD `95e5e3eca60c0b7370600f07d951751ba2831244`, siete modificaciones preexistentes de Ayuda/Support conservadas |
| Biblioteca existente | `C:/Users/User/.codex/skills/joa-main-orchestrator`, ocho archivos; invocación explícita preservada |

Se leyeron reglas/STATUS, rutas de lectura, WORKFLOW, VALIDACION, EVIDENCIA, plantillas y herramientas pertinentes de ambos proyectos; contratos/auditorías representativos de implementación, QA visual, copias, conservación, rendimiento, firma, distribución e higiene. Dos revisores internos relevaron 3.0 y 2.0 por separado. MAIN contrastó fuentes críticas y revisó las skills generadas.

Cobertura: familias de procesos recurrentes documentados y patrones técnicos comprobados en código. No se inspeccionó cada cuerpo de cada prompt histórico ni todo chat privado, ni se revalidaron los artefactos/dispositivos/servicios de las auditorías anteriores. Los resultados históricos aquí citados son antecedentes documentales, no ejecuciones nuevas.

Fotografía de entrada y comprobaciones de esta extracción: `.local-evidence/skill-extraction/20261024/`. Incluye hashes de 1070 rutas de 3.0, 878 de 2.0 y preimagen de instrucciones globales; los archivos eliminados se registran como ausentes. Evidencia local ignorada por Git.

Durante el control final cambiaron concurrentemente tres documentos preexistentes: `docs/STATUS.md`, `docs/audits/2026-10-24-stage12-revision-main-g3-r2.md` y `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`. Sus contenidos incorporan una aclaración humana del 24/10 y sus marcas coinciden con la continuidad de MAIN/Stage12. La extracción no escribió esas rutas ni las revirtió. Sus hashes de entrada/salida se registran aparte: no se afirma inmutabilidad global de las 1070 rutas ni autoría del escritor externo. No se atribuye ese delta a las skills.

## Procesos identificados y destino

Las skills nuevas son procedimientos generales. Los identificadores A–J agrupan capacidades, no etapas ni un orden obligatorio.

| ID | Skill personal | Qué resuelve |
|---|---|---|
| A | `joa-skill-router` | Contexto suficiente, selección y brechas de skills |
| B | `joa-main-orchestrator` — existente, explícita | MAIN, contratos, dependencias y especialistas |
| C | `project-handoff-audit` | Recepción, delta propio, auditoría, evidencia e integración |
| D | `change-validation` | Comprobaciones por impacto, frescura y atribución |
| E | `android-qa-isolation` | QA Android con datos, artefactos y recursos controlados |
| F | `data-conservation-audit` | Conservación en migración, copias, restore y upgrade |
| G | `transactional-flow-safety` | Borradores, resultados async, idempotencia y concurrencia |
| H | `performance-causal-diagnosis` | Comparación y diagnóstico antes de optimizar |
| I | `ui-conservation-review` | Conservación funcional, matriz visual y atlas |
| J | `release-readiness` | Artefacto, privacidad, firma, confianza y distribución |

| Proceso reutilizado | Destino | Evidencia y límite |
|---|---|---|
| Puerta 0: ruta, Git, dirty/staged/nuevos, worktrees y remotos | A/B/C | [AGENTS](../../AGENTS.md), [WORKFLOW](../WORKFLOW.md); preservar entrada, no fingir limpieza |
| Reanudación por delta y lectura dirigida | A | [LECTURAS](../LECTURAS_POR_TAREA.md), WORKFLOW; no cargar historia por ritual |
| Autoridad documental, estado único, ADR/sucesores y archivo | A/B | WORKFLOW; reglas, requisitos, estado y evidencia tienen funciones distintas |
| Descomposición por capacidades completas y grafo de dependencias | B | WORKFLOW; no un chat por botón ni contratos inventados por consumidores |
| Contrato autosuficiente de delegación | B | [TEMPLATE](../prompts/TEMPLATE.md); base, alcance, exclusiones y DONE WHEN |
| Un escritor por archivo y serialización de recursos | B/E | WORKFLOW; worktree no aísla puertos, dispositivos o Gradle |
| Permisos vigentes, HOLD y escalamiento de cambios materiales | A/B/E/J | WORKFLOW/TEMPLATE; no repetir permisos concedidos ni heredar autorizaciones históricas |
| Entrega, envío, acuse y auditoría separados | C | WORKFLOW y [revisión Stage8](2026-10-22-stage8-revision-main-cierre.md); un tool aceptado no demuestra recepción |
| Recuperación idempotente por clave y payload inmutable | C | WORKFLOW/TEMPLATE; consultar recibo antes de retry; no exactly-once global |
| Encadenamiento y supervisor temporal, cuando se autorizan | B/C | WORKFLOW; no crear automatización ni nuevo chat durante esta extracción |
| Auditoría independiente de contratos, consumidores y delta | C | WORKFLOW y [revisión Stage9](2026-10-21-stage9-revision-main.md); handoff no se acepta como prueba suficiente |
| Comparación base/preimagen/salida en checkout acumulado | C | Revisión Stage8 y base local de esta extracción; no atribuir todo `diff HEAD` al bloque |
| Evidencia nueva, manifiestos, hashes y adversos conservados | C/D | [EVIDENCIA](../EVIDENCIA.md); hash acredita bytes, no semántica |
| Higiene recuperable, bundle/patch/nuevos y destino validado | C | [higiene 2.0→3.0](2026-09-12-higiene-previa-miguardia-3.md); no autoriza limpieza actual |
| Validación por impacto y plan sin ejecución | D | [VALIDACION](../VALIDACION.md), `tools/verify.py`; comandos se adaptan al repo |
| Tests frescos frente a caché, XML efectivo y omisiones | D | `tools/verify.py`; proceso exitoso no basta |
| Insumos antes/después y atribución invalidada por cambios | D | `tools/verify.py`, revisión Stage9; no heredar un verde de otra entrada |
| Interoperabilidad productor/consumidor separada de local | D/J | VALIDACION; un perfil local no prueba contrato web |
| Catálogo QA por riesgo/efectos y plan sin ADB | E | `tools/qa.py`, `tools/qa-groups.json`; flags documentan permiso, no lo conceden |
| Identidad APK/runner local e instalado y protocolo real | E | VALIDACION/qa.py; versión o exit 0 no bastan |
| Aislamiento de Application, DB, preferencias, archivos y efectos | E/F | [handoff Stage11](../HANDOFF_STAGE_11_A_MAIN_3_0.md); separar procesos no separa almacenes |
| Custodia/cierre de procesos propios y límites de red | E | Contrato/auditorías Stage12; no exportar confinamiento no demostrado |
| Snapshot consistente y comparación tipada de datos | F | Revisión Stage9 y [recuperación Stage10](2026-10-23-stage10-revision-main-recuperacion-r2.md); WAL y tipos importan |
| Compatibilidad con formatos antiguos genuinos | F | Stage9/11 y contratos de Copias; no fabricar compatibilidad desde el lector actual |
| Preferencias y bytes privados comprobados aparte de Room | F | Handoff Stage11; DB verde no prueba imágenes |
| Procedencia incoming/local según reemplazo o combinación | F | Handoff Stage11 R1; igualdad de metadatos no acredita bytes correctos |
| Preview sin escritura, conflicto explícito, journal y rollback | F/G | ADR0035 y auditoría 2.0 `2026-09-01-copias-y-restauracion-locales-seguras-v2-main.md` |
| Oráculo independiente, baseline limpio y mutantes | F | Revisión Stage9 R1; no acumular adversos que enmascaren rechazos |
| Borrador separado del commit y retorno contextual | G | `V2ManualShiftLoadViewModel.kt`, `WorkSetupViewModel.kt`; patrón presente en 2.0 y 3.0 |
| Request/result con identidad, contexto y consumo protegido | G | Métodos `receiveCreationResult`/recuperación contextual; ignorar callbacks obsoletos |
| Resultado incierto y retry sólo después de consulta por identidad | G/C | ViewModels y WORKFLOW; un UUID solo no asegura idempotencia |
| Revalidación atómica, ocupación/versiones/CAS y lote completo | G | `RoomV2ShiftRepository.applyV2Batch`, `RoomCalendarBatchRepository`; mutex UI no protege otro escritor |
| Proyecciones/consultas sin escrituras y oráculos transversales | G/F | Auditoría 2.0 `2026-08-29-pruebas-cruzadas-del-nucleo-v2.md`; no confundir consulta con reconciliación |
| Carrera determinista con dos escritores y barrera | G | Auditoría anterior; no depender sólo de sleeps |
| Recreación, recomposición y muerte de proceso diferenciadas | G/E/I | Auditoría 2.0 `2026-09-07-crear-desde-carga-main.md`; garantías separadas |
| Medición congelada, detector previo y campaña comparativa | H | Revisión Stage8; ABBA es opción, no receta universal |
| Muestras, máximos/primeras y pares sin esconder alertas | H | Stage8, 432 muestras históricas; causa de Resumen quedó inconclusa |
| Diagnóstico causal y condición finita antes de corrección | H | Stage8; no optimizar ni proclamar arreglo desde correlación |
| Matriz funcional antes/después de rediseño | I/G | [Stage6](2026-10-11-stage6-relevamiento-visual-nuevo.md), matrices Stage7 y ADR0043 vigente |
| Corpus original, hashes, atlas local y obsolescencia explícita | I | [handoff Stage6](../HANDOFF_STAGE_6_RELEVAMIENTO_NUEVO_A_MAIN_3_0.md); captura no prueba fluidez/TalkBack |
| Privacidad por superficie, selección consciente y datos sintéticos | F/I/J | AGENTS, ADR0034/35/38/41; reglas específicas de datos siguen en MiGuardia |
| Política independiente del candidato y superficie empaquetada | J | `tools/release/README.md`, `inspect_apk.py`; no generar allowlist desde el APK a aprobar |
| Reconstrucción de bytes frente a igualdad de payload | J | README release; comparar ZIP no cuenta como otro build |
| Custodia, firma y verificador posterior separados | J | `tools/release/SIGNING_CUSTODY.md`, `verify_signed_apk.py`; secretos fuera de Git |
| Confianza independiente de checksum del mismo hosting | J | Auditorías de distribución 2.0; coherencia no agrega una raíz de autenticidad |
| Instalación, upgrade, restore, publicación y descarga real separados | E/F/J | Auditorías 2.0 de firma/upgrade y Stage10/11; HTTP200 no prueba binario ni conservación |
| Detección futura de skills existentes, faltantes y duplicadas | A | Pedido actual; catálogo personal y regla global, sin instalar servicios ni crear automatizaciones |

Las auditorías 2.0 citadas por nombre están bajo `../MiGuardia-2.0/docs/audits/` desde la raíz 3.0, en sólo lectura. Las fuentes 3.0 enlazadas se consultan como evidencia fechada; ninguna modifica por sí sola el cursor operativo.

## Instalación y selección futura

- Implementación canónica de nueve skills nuevas: `C:/Users/User/.codex/skills/`.
- Exposición personal de diez skills, incluida la existente: enlaces de directorio individuales en `C:/Users/User/.agents/skills/`, apuntando a la única copia canónica. No se duplican instrucciones dentro de proyectos.
- Regla agregada a `C:/Users/User/.codex/AGENTS.md`: seleccionar por tarea y cargar únicamente lo pertinente; detectar brechas sin convertir cada pedido en una extracción.
- Catálogo humano/operativo: `joa-skill-router/references/catalogo.md` dentro de la biblioteca.
- Las nueve nuevas permiten selección implícita. `joa-main-orchestrator` conserva `allow_implicit_invocation: false` y su contenido original.

La [documentación oficial](https://learn.chatgpt.com/docs/build-skills) establece descubrimiento personal en `~/.agents/skills`, seguimiento de enlaces y carga por nombre/descripción. La creación de archivos y resolución de enlaces se comprueban localmente. Un revisor interno nuevo observó las nueve skills nuevas en su catálogo efectivo y la existente explícita fuera de la lista implícita. Esto comprueba descubrimiento en esa instancia; la aparición en un selector visual o una conversación externa a esta evaluación no fue observada. Si no aparecen tras la detección automática, reiniciar Codex según esa guía.

## Decisiones y exclusiones

No se trasladaron las 24 etapas 2.0, zoom interno retirado, beta/cupos/feedback obligatorio, nombres de paquetes, pines, versiones/tablas, seriales, perfiles de red, rutas SDK ni modelo/razonamiento como defaults globales. Tampoco permisos pasados de ADB, dispositivos, firma, push o publicación.

La skill de integración que aparecía en memoria era un antecedente con rutas/lecturas ya históricas. Se contrastó con la autoridad actual; no se instaló como receta vigente ni se editaron memorias. La skill personal existente de MAIN se preservó, evitando duplicarla o cambiar silenciosamente su modo explícito.

Esta extracción no modificó producto, código de aplicación, esquemas, permisos Android, dependencias de producción, versión, firma ni documentos de Stage12. No ejecutó ADB, VM, pruebas de aplicación MiGuardia, commit/push, mensajes externos ni distribución. Sus comprobaciones usan archivos locales/sintéticos y el verificador documental.

## Validación de esta entrega

Resultados y límites finales en `.local-evidence/skill-extraction/20261024/validation.json` y `independent-review.md` contiguo:

- Validador oficial `quick_validate.py`: **10/10**, incluyendo la skill existente; UI YAML, prompts, modo de invocación y diez enlaces personales comprobados. **18 referencias locales** de la biblioteca resueltas.
- `tools/docs_check.py`: **OK**, sin problemas; 523 referencias, 42 ADR y 103 snapshots comprobados en la ejecución final. El informe nuevo también tiene sus enlaces locales y espacios finales revisados.
- Conservación: **1945 rutas preexistentes sin cambio** entre ambos repositorios; tres documentos concurrentes de Stage12 con hashes y límite separados. 2.0 no recibió cambios; ambos HEAD y staging vacío se conservaron. La única adición propia versionable en 3.0 es este informe.
- Los ocho archivos de `joa-main-orchestrator` permanecen idénticos. Las reglas globales conservaron íntegro su prefijo original y recibieron sólo la sección 15 solicitada para selección futura.
- Revisión independiente: sin defectos bloqueantes en las nueve skills nuevas; doce escenarios de selección evaluados. Observó descubrimiento real de las nueve en una nueva instancia interna.
- Ensayo sintético real de recepción: detectó dos defectos preparados en la entrega del caso —preferencia fuera de alcance y tests de caché del código anterior— pese a manifiesto 2/2 correcto. Siete ejemplos reducidos ejecutados por el revisor; integración sintética rechazada como correspondía.

El validador oficial requiere PyYAML: se instaló sólo en `validation-deps/` de esta evidencia local; no se modificaron el Python global, requisitos del proyecto ni sus dependencias. Los fallos iniciales de preparación quedaron registrados. La revisión señaló una expresión heredada de “batería global” en la skill existente; su propia precedencia conserva la validación proporcional del proyecto y no se alteró silenciosamente el contenido ni su modo explícito.

La evaluación de decisiones con escenarios y el ensayo sintético de recepción no prueban QA Android, firma o publicaciones reales. El uso futuro en otras conversaciones debe contrastarse con su catálogo efectivo y sus instrucciones vigentes.

## Actualización posterior: pingpong MAIN → STAGE → MAIN

La consulta siguiente de Joaquín pidió comprobar disponibilidad automática, vigencia del método y existencia del circuito automático. La revisión confirmó las nueve skills nuevas en el catálogo actual y descubrió que la skill heredada de MAIN no incorporaba completo el protocolo vigente de WORKFLOW. La conservación de sus ocho archivos descrita arriba corresponde al primer cierre; esta adenda registra su actualización posterior autorizada por la extracción original. Preimágenes y recibos anteriores permanecen en `pingpong-update/before/` y la evidencia del primer cierre.

Se actualizaron los ocho archivos del orquestador y se agregó `references/pingpong.md`; las cinco plantillas ahora llevan autoridades humanas, identidades reales, delivery_key/sello, recibos separados, acuse y corrección al mismo stage. Se incorporaron recuperación de transporte incierto, deduplicación, conflicto de clave/hash, siguiente tarea real ya autorizada y supervisor temporal único cuando se pidió seguimiento. No se creó otra skill de orquestación ni se habilitó selección implícita: `joa-main-orchestrator` sigue explícita.

Se reemplazó la batería global rutinaria por validación proporcional y se adaptó la lectura por delta, el checkout compartido y un único estado operativo. En QA Android se aclaró que la comparación exhaustiva de almacenes depende del contrato de conservación: no exige snapshot universal para uso normal ya autorizado. El catálogo se actualizó; las demás skills nuevas mantienen sus procedimientos generales.

La revisión independiente detectó tres defectos de instrucciones/plantilla y se corrigieron: el aviso y sus recibos deben completarse **antes** del final que termina el turno; el control de alcance evalúa el delta propio preservando el checkout modificado heredado; y los no rastreados se revisan por pertinencia, sin abrir contenido privado ajeno por rutina. Fallos y correcciones se conservan en el informe de revisión, sin adjudicarlos al producto.

Validación estructural posterior: **10/10 skills** y **22 referencias locales** resueltas; política explícita MAIN conservada. La matriz independiente de decisiones está en `.local-evidence/skill-extraction/20261024/pingpong-update/independent-review.md`; los hashes y demás comprobaciones, en `validation.json` y `skills-manifest.json` contiguos.

Disponibilidad y ejecución son cosas distintas: las nueve nuevas admiten elección automática en este perfil; si una conversación abierta no refleja cambios, la documentación oficial indica reiniciar Codex. MAIN requiere invocación explícita, una vez para el ciclo; mensajes en ambos sentidos, creación de chats, encadenamiento y seguimiento usan permisos humanos aplicables. Esas acciones no se iniciaron al crear/actualizar esta biblioteca. Las evaluaciones son locales/de decisión: **no se afirma ensayo nuevo de transporte real ni supervisor activo**. El método es coherente con las fuentes actuales revisadas, pero no se sincroniza solo con futuras decisiones de cada proyecto.

## Catálogo ampliado solicitado posteriormente

Joaquín pidió un catálogo completo con función, cuándo usar, disponibilidad para otros proyectos y ruta de carpeta. Se creó `C:/Users/User/.codex/skills-catalog/catalogo.html`, con búsqueda/filtros y enlaces/copia de rutas; `catalogo.json` conserva nombres exactos, archivos, versión observada, hashes y evidencia. El selector personal enlaza ambos archivos, sin cargar todas sus instrucciones por rutina.

Se catalogaron **44 entradas**: diez procesos formalizados, cinco skills complementarias con uso acreditado, dos accesos históricos actualizados y 27 disponibles sin uso acreditado en las fuentes inspeccionadas. Es exhaustivo respecto de las skills declaradas en esta sesión, la biblioteca personal explícita y los antecedentes localizados; no se afirma rastreo exhaustivo de todos los chats privados históricos. Las cinco complementarias son Skill Creator, OpenAI Docs, Sites, Computer Use y Plugin Management. El uso histórico de una familia no acredita ejecución de su versión actualmente instalada.

Las recetas antiguas `miguardia-main-integration` y `transcriptor-independent-stage-orchestration` tenían referencias/gates obsoletos. Sus originales de memoria permanecen como antecedentes. Se crearon accesos personales actuales bajo esos nombres, explícitos y enlazados desde `~/.agents/skills`, que remiten a auditoría/validación/conservación/QA vigentes sin duplicar orquestación ni activar MAIN indirectamente. La biblioteca personal queda en **12 skills: nueve automáticas y tres explícitas** (MAIN y dos accesos compatibles).

Validación: doce skills personales con el validador oficial, 32 referencias personales resueltas y 44 rutas/archivos/hashes comprobados. Las otras 32 skills provienen del sistema/plugins: frontmatter y versión activa local inspeccionados; dos SKILL.md coincidieron además con el manifiesto de integridad disponible de su paquete. La consulta de plugins confirmó Sites GLOBAL/ENABLED/instalado y no informó versiones remotas. **No se afirma última versión remota para todos los paquetes ni se editaron sus caches.** Plugins y servicios dependen de la disponibilidad/conexión de cada sesión; la cobertura comprobada es este perfil/host.

El catálogo se renderizó en un contexto headless nuevo, sin perfil personal: 44 fichas, filtros combinados, búsqueda por tarea/Spotify, vacío, 88 enlaces locales exactos, copia de ruta con clipboard simulado y vistas escritorio/móvil sin overflow horizontal. Cero solicitudes automáticas de página y cero errores JavaScript observados. Los enlaces de carpeta se cotejaron sin abrir carpetas y no se modificó el clipboard real. Se corrigió una generación inválida de opciones detectada por la primera prueba de filtros; se conservaron ese fallo y los intentos de preparación de navegador.

Evidencia específica: `.local-evidence/skill-extraction/20261024/full-catalog/`, incluidos `validation.json`, `browser-checks.json`, capturas, generación, revisión independiente y recibo. No se inició pingpong real, no se crearon chats/automatizaciones ni se ensayó MiGuardia en dispositivo. La revisión de vigencia es del método; no sustituye QA runtime de todas las capacidades.
