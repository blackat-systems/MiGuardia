# Integridad y orden del proyecto MiGuardia 3.0

La revisión del 10/10 confirma una estructura de fuentes coherente y no detecta
corrupción Git. Los ajustes corrigen navegación documental desactualizada y
previenen el rastreo accidental de paquetes Android; no cambian la aplicación.
La QA de Stage 5 queda en pausa, con su estado parcial y evidencias preservados.

## Entrada y límites

Contenedor: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer`, sin Git.
Repositorio independiente `MiGuardia-3.0`, `.git` propio, rama
`codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`,
upstream homónimo y remoto público `https://github.com/blackat-systems/MiGuardia.git`.
Un worktree, staging vacío; 179 entradas de estado recibidas, sin conflictos.

El inventario recibió 956 rutas: 886 rastreadas, 70 nuevas y 955 archivos
existentes. La diferencia es la eliminación previa de
`app/src/test/java/com/blackatsystems/miguardia/ui/VerticalScrollbarMetricsTest.kt`.
Todos los hashes coinciden con el sello anterior del recorrido de imagen OEM;
no apareció una diferencia sin dueño ni se agregó el candidato al índice Git.

MiGuardia-2.0 conserva sus siete cambios conocidos, rama 2.0 y remoto archivado;
su administración compartida sigue en Repositorio-Git. Ambos y Archivo-recuperacion
permanecen intactos. `tmp/pdfs/` del contenedor es material auxiliar ajeno a los
módulos Android: no se abrió su contenido, movió o eliminó por inferencia.

## Integridad estática

- `git fsck --full --no-reflogs` terminó sin errores de integridad. Informó dos
  blobs sin referencia, no corrupción; se conservaron, sin prune/gc.
- No hay conflictos de mayúsculas/minúsculas, archivos vacíos, merges sin resolver
  ni generados/cachés/evidencia dentro del conjunto de fuentes versionables.
- No aparecen APK/AAB, claves o bases SQLite por las extensiones inspeccionadas.
  El único JAR rastreado es el wrapper de Gradle. No es una auditoría exhaustiva
  de secretos ni del contenido privado de `.local-evidence/`.
- Tres pares de archivos idénticos corresponden a recursos legítimos:
  iconos normal/redondo de Android y temas claro/oscuro. No se deduplicaron.
- Módulos: `app` depende de `core/database` y `core/domain`; database depende de
  domain, sin ciclo. No se detectan imports Android/database/UI en dominio.
- Los sourceSets contienen 558 archivos, de ellos 531 Kotlin/Java. Los diez
  stubs de `app/src/testSupport/` se incorporan sólo a test/androidTest, no main.
- Los 23 XML de recursos son parseables y seis componentes propios del manifiesto
  resuelven a fuentes existentes. FileProvider corresponde a la dependencia
  AndroidX declarada, no a una clase faltante del proyecto.
- Esquemas Room 1–8 parseables, número de archivo/versión coherentes; V8 tiene
  29 tablas. Exportación y migraciones explícitas permanecen conectadas.
- `WeatherViewModelInstrumentedTest.kt` declara un paquete distinto de su
  directorio, permitido en Kotlin y correctamente catalogado. Moverlo por estilo
  no aporta beneficio y obligaría a alterar su ruta/hash QA; se conserva.

Las cachés, APK compiladas, firmas locales y evidencia ignoradas no son desorden
versionable. No se enumeró su contenido ni se borraron para aparentar limpieza.
El orden visual del Explorador y el índice Git no sustituyen las dependencias
Gradle ni la ruta de lectura del proyecto.

## Hallazgos y correcciones

| Hallazgo | Corrección mínima |
|---|---|
| README del contenedor inicia por el handoff original; README 3.0 aún afirma estar en descubrimiento | Entrada por AGENTS/STATUS y contrato vigente, handoff inicial identificado como procedencia |
| Guías de entrada tratan las 24 etapas 2.0 como despacho obligatorio 3.0 | Aclaración coherente en AGENTS, WORKFLOW, lecturas, índice y aviso inicial de ORQUESTACION_MAIN; cuerpo de las 24 etapas intacto |
| La ruta Calendario/zoom omite ADR 0042 vigente | Añadido al mapa de lecturas, sin modificar su decisión ni producto |
| Dos filas Stage 3 conservan estados anteriores a sus cierres sucesores | Clasificadas como antecedentes ejecutados; aceptación sigue sólo en STATUS |
| El stub ORQUESTACION_SECUENCIAL_MAIN_2_0 falta en el índice histórico | Añadida la fila con su destino existente; ningún archivo movido |
| Guía 2.0 visible indica carpeta/rama antigua sin advertencia | Aviso histórico inicial; texto original conservado |
| APK/AAB fuera de build no tienen protección global | `.gitignore` añade sólo `*.apk` y `*.aab`, conforme a la prohibición ya vigente |

El README 3.0 reúne el mapa de carpetas; no se creó otro inventario canónico que
compita con él. STATUS sigue siendo el único estado operativo. La guía de escritura
ayudó a mantener los avisos breves y a separar entradas vigentes de antecedentes.

## Comprobaciones y custodia final

Dos revisores internos de sólo lectura contrastaron índices y estructura; MAIN
conservó la escritura exclusiva y revisó el diff propio contra la entrada.
Antes de los ajustes: 228 referencias de cinco índices/guías existentes,
41/41 ADR indexados y 52 stubs históricos con destinos válidos. Faltaba sólo la
fila histórica indicada; no había destinos rotos en esos índices.

Comprobaciones finales: `python tools/docs_check.py`,
`python tools/qa.py --check-catalog`, `git diff --check`, prueba directa de
ignore para paquetes Android y comparación de hashes contra la entrada.
Documentación y catálogo válidos; 103 snapshots íntegros, 119 clases QA y
27 grupos. Se conservan los recibos en `.local-evidence/index-audit-20261010/`.

Delta propio: diez archivos existentes y esta auditoría dentro de 3.0, más el
README del contenedor. Sólo documentación y dos reglas de ignore; cero cambios
en Kotlin, XML, Gradle, Room/esquemas, pruebas, versiones, Copias o artefactos.
Entrada, originales, patch propio y hashes finales quedan en el directorio local;
los sellos anteriores no se sobrescriben. Staging vacío, HEAD sin cambios.

No hubo ADB, Samsung, compilación, QA nueva, commit, push, firma ni publicación.
Esta comprobación acredita orden/integridad estática, no rendimiento de la app
ni una migración física nueva. Stage 5 no queda aceptado por ordenar archivos.

## Continuación

Para retomar, leer AGENTS y STATUS, luego el contrato/handoff Stage 5 indicado
allí. No reiniciar 2.0 ni el descubrimiento. Continúan pendientes la revisión
humana de legibilidad/calendario/zoom y los subcasos integrales ya documentados.
