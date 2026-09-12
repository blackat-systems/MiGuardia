# Optimización de instrucciones y flujo — cierre de implementación

Fecha: 2026-09-06. Base: `f5df1d78d49e51ba9a12b437f64ca4e9095aaa35`, rama `codex/miguardia-2.0`.

Este informe registra la aplicación de las propuestas de la [auditoría inicial](2026-09-06-auditoria-instrucciones-y-flujo.md), autorizada expresamente por Joaquin después de recibirla. Es evidencia fechada, no una nueva fuente de órdenes. El estado operativo vive en [STATUS](../STATUS.md).

## Resultado y alcance

Se reemplazó la lectura histórica obligatoria por una entrada breve y lectura por tarea; se separaron reglas permanentes, estado actual, contratos e historia; se hicieron reproducibles las comprobaciones locales y la planificación de QA. La extracción de pantallas reduce el archivo principal sin modificar los cuerpos de sus funciones.

Se preservaron las reglas laborales, la base y migraciones V2, privacidad, zoom interno elegido y persistido, límites de dispositivos y distribución, y los cambios preexistentes del cliente de actualización manual. No se instalaron dependencias nuevas ni se modificó el proyecto web. No se ejecutaron ADB, teléfonos o emuladores, ni firma oficial, publicación o servicios remotos.

## Resolución de los 15 hallazgos

| Hallazgo | Cambio implementado y comprobación |
|---|---|
| H01 — lectura excesiva | AGENTS y STATUS son la entrada común. LECTURAS_POR_TAREA dirige el detalle; WORKFLOW permite reanudar por diferencias. `tools/docs_check.py` controla el presupuesto de arranque, enlaces y estados. |
| H02 — MAIN activo/cerrado | Se separó el rol permanente de integración del bloque autorizado. STATUS es la única autoridad de ejecución; los MAIN anteriores son enlaces históricos. |
| H03 — bloque 28 contradictorio | STATUS distingue cliente presente y revisión previa, ausencia de commit real, QA dirigida registrada y distribución pendiente. No se inventó ni creó su checkpoint. |
| H04 — README desactualizado | Portada V2, capacidades presentes y conexiones por finalidad: Clima, beta, actualizaciones y enlaces conscientes. Sin afirmar publicación ni enviar datos laborales. |
| H05 — retiro V1 ya realizado | AGENTS conserva invariantes V2 en lugar de ordenar repetir la transición. Base `miguardia-v2.db`, migraciones V2 y componentes heredados útiles permanecen. |
| H06 — decisiones reemplazadas | Los 39 ADR tienen vigencia, alcance y sucesores. Se identificaron reglas V1, destinos antiguos del menú, zoom, Ayuda y ampliación de copias/Room V6. Índice generado desde esos metadatos, con detección de ciclos. |
| H07 — matriz anterior heredada | El contrato futuro 29 distingue antecedentes y candidato actual; requiere evidencia de beta/actualizaciones propia. Sigue pendiente de autorización de ejecución. |
| H08 — cierre ambiguo | STATUS/WORKFLOW separan implementación, revisión, commit, compilación, pruebas ejecutadas, QA física y distribución. “Cerrado” no infiere todos esos niveles. |
| H09 — permisos consumidos | La regla se limita a autorizaciones anteriores de push. Los cambios y comprobaciones seguros del bloque actual no necesitan repreguntas. Dispositivos y acciones externas mantienen sus puertas. |
| H10 — privacidad y exportación | Se distingue exposición involuntaria de exportaciones elegidas conforme al contrato de informes; las prohibiciones de logs, secretos y datos ajenos siguen vigentes. |
| H11 — Node oculto en JVM | Perfil `local` autónomo; categoría JUnit `WebCryptoInterop` y perfil `interop` explícito con ruta web, Node y precomprobación de TypeScript/ECDSA. No hay omisión silenciosa ante un requisito faltante. |
| H12 — repetir toda la batería | Perfiles `local`, `interop`, `build`, `full`; frescura de tareas JVM separada de reconstrucción global. Documentación se valida sin compilar Android. Resultados ejecutados y reutilizados se distinguen. |
| H13 — evidencia descartable | 324 archivos conservados con comparación SHA-256 en `.local-evidence/legacy/2026-09-06/`. Runners nuevos guardan evidencia fuera de build. Mapa de rutas antiguas versionable, contenidos privados ignorados. |
| H14 — QA manual y beta mezcladas | Catálogo de 101 clases en 15 grupos, fixtures explícitas, planes sin ADB y ejecución con permisos específicos. Separación de procesos, raíz beta cerrada y grupos supervisados. Renombres mecánicos de ayudas en nueve archivos. |
| H15 — archivo UI concentrado | Calendario, detalle de día, Apariencia y formatos extraídos a cuatro archivos del mismo paquete. Comparación mecánica de cuerpos, comprobación de imports/visibilidad y compilación. No se reescribieron flujos. |

## Estructura que queda

- `AGENTS.md`: reglas estables y límites.
- `docs/STATUS.md`: único estado actual, autorización y pendientes por superficie.
- `docs/LECTURAS_POR_TAREA.md`, `WORKFLOW.md` y `VALIDACION.md`: navegación documental, método y comprobaciones por impacto.
- Mapa y planificación: resumen y reglas de producto, sin otra cronología operativa.
- `docs/adr/README.md`: índice generado; cada ADR define su vigencia.
- `docs/prompts/`: contratos de alcance y plantilla breve; 52 rutas cerradas se conservan como enlaces de compatibilidad.
- `docs/archive/`: 103 originales completos y manifiesto SHA-256. `.ignore` los excluye de búsquedas rutinarias; `.gitattributes` conserva sus bytes al pasar por Git.
- `tools/`: verificador documental, perfiles locales y catálogo/ejecutor de QA, todos con biblioteca estándar Python 3.11+.
- `.local-evidence/`: evidencia privada durable, ignorada por Git. Las auditorías versionables del repositorio sólo conservan resúmenes sin datos privados.

No se borraron las cachés de compilación para reducir tokens: son útiles para las comprobaciones incrementales. La reducción se logra seleccionando fuentes y excluyendo historia de las búsquedas rutinarias. No se eliminaron registros no reconstruibles por antigüedad.

## Mediciones

La auditoría inicial midió 278.578 caracteres y 4.767 líneas de lectura obligatoria en siete documentos. La nueva entrada AGENTS + STATUS suma **10.545 caracteres: 96,2 % menos**. Aun incluyendo las cuatro guías e índice adicionales, son **24.304 caracteres: 91,3 % menos** que la lectura base anterior. Son conjuntos de lectura distintos por diseño: el detalle funcional ahora se consulta por tarea. Mediciones y resultados en el manifiesto adjunto `2026-09-06-optimizacion-instrucciones-y-flujo-resultados.json`.

`MiGuardiaApp.kt` pasa de 2.919 a 1.385 líneas: 52,6 % menos en ese archivo. Los cuerpos extraídos siguen existiendo; no se presenta esto como una reducción equivalente del código total. El Calendario conserva 1.228 líneas: la separación es por responsabilidad, sin fragmentarlo arbitrariamente.

Las cifras de caracteres miden volumen de lectura prescrito. No se midió facturación ni consumo real del modelo: una reducción de caracteres no demuestra el mismo porcentaje de ahorro efectivo de tokens o tiempo.

## Comprobación y revisión independiente

| Comprobación | Resultado observado |
|---|---|
| JVM local, ejecución nueva | **801/801**: dominio 379, database 12, app 410. Cero fallos, errores u omitidas. |
| Interoperabilidad web, ejecución nueva | **3/3**, dos clases; contrato web y Node explícitos. Cero fallos, errores u omitidas. |
| Herramientas Python | **72/72**: documentación 12, verificador 29, QA 31. Incluye casos de rechazo y fallos simulados, sin dispositivo. |
| Análisis lint Debug | Cero errores; seis avisos sobre versiones de herramientas/bibliotecas. No se cambiaron versiones como parte de esta optimización. |
| Compilación | Debug, QA, Release sin firma oficial, instrumentación QA y Room: aprobada. Instrumentación compilada no equivale a ejecutada. |
| Atribución final de artefactos | Cinco APK y lint con SHA-256/tamaño/tarea productora. Segunda comprobación incremental: seis salidas reutilizadas, no presentadas como reconstrucción nueva. |
| Documentación | Un estado operativo; enlaces, presupuesto, 39 ADR, sucesores sin ciclos, índice generado y 103 originales íntegros. |
| QA | Catálogo 101 clases/15 grupos: 12 ejecutables con puertas explícitas y tres sólo supervisados. No se ejecutó ADB. |
| Preservación | Cuerpos UI idénticos según comparación mecánica; nueve renombres AndroidTest exactos; sin cambios en `core/`, esquemas, manifiestos o versiones. |

La batería completa terminó el 2026-09-06 a las 20:27:33 de Argentina. Las pruebas locales tardaron 199,878 s, interoperabilidad 44,727 s y build 583,290 s. Después de corregir la atribución del verificador, se ejecutaron sus regresiones y una comprobación build incremental de 28,739 s; no se repitieron JVM ni se modificaron fuentes del producto. Esto comprueba el flujo local, no estima tiempos universales ni una mejora de rendimiento del producto.

Tres especialistas con fronteras de escritura propias implementaron las herramientas/pruebas y extracción; MAIN mantuvo documentación y coordinó Gradle. La revisión cruzada detectó y corrigió cinco defectos de las herramientas nuevas: insumos lint/ProGuard omitidos; omitidas contadas como ejecutadas; APK sin huellas; sesión temporal que necesitaba restauración manual; APK instalado no vinculado al local. La segunda revisión confirmó esas correcciones. También se corrigieron la vigencia parcial del menú V1 y copias V5, y la distinción entre permiso de ubicación y conversión consciente de dirección.

Evidencia local: `.local-evidence/optimization/full-20260906-main/result.json`, `build-artifacts-20260906-main/result.json` y `final-controls-20260906/`. El manifiesto versionable adjunto conserva conteos, huellas y ubicación de resultados. La primera versión del verificador se preserva junto a su corrida; sus omitidas eran cero y lint/ProGuard conservaron la huella de entrada. La comprobación final utiliza la herramienta corregida y registra su propia huella.

## Preservación y Git

Se tomó una huella de los 690 archivos de entrada a la optimización: 688 originales más el informe/inventario de auditoría. Se conservó el candidato deliberadamente modificado. Las nueve pruebas Android sólo cambian nombres de helpers, verificado contra las huellas previas después de revertir esos dos renombres en memoria. La extracción UI conserva todas las líneas no vacías de los cuerpos originales; sólo normaliza finales de línea, imports y ocho visibilidades privadas a internas para poder compartirlas dentro del módulo.

Los 103 documentos originales permanecen íntegros en el archivo histórico. El informe de auditoría inicial conserva sus conclusiones y el estado de autorización de aquel momento; este cierre registra la autorización posterior y su aplicación. Los 324 archivos de evidencia tienen conservación exacta: 13 movidos; 274 y 37 copiados porque Windows no permitió mover sus carpetas, con originales retenidos. No se forzaron cierres de procesos ni borrados.

HEAD y referencias protegidas permanecen en la base indicada. No hay staging, commit, push, tag o cambio de rama de esta optimización. El checkout comparte archivos con el cliente 28 todavía sin checkpoint; crear un commit global absorbería esa entrega. El cambio queda aplicado y revisable, sin confundirlo con un checkpoint ya creado.

## Límites y continuidad

Esta entrega optimiza instrucciones, mantenimiento y comprobación del proyecto; no acredita velocidad de ejecución de la app ni una auditoría completa de seguridad. Las comprobaciones físicas históricas mantienen su base y superficie. No se afirmó QA física nueva por haber movido funciones o compilado AndroidTest.

El bloque 29, clave/firma, publicación, APIs y proveedores no cubiertos, TalkBack y actualización realmente instalada con conservación de datos siguen siendo puertas de distribución del producto, fuera de esta optimización. El nuevo runner facilita ejecutar ensayos ya autorizados y rechaza los que requieren intervención supervisada; no concede permisos por sí mismo.

Para continuar, leer AGENTS y STATUS, tomar la instrucción concreta nueva y consultar sólo la ruta temática correspondiente. No repetir esta auditoría ni reabrir contratos cerrados sin un cambio que lo justifique.
