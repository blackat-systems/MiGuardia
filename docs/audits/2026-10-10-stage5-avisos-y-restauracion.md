# Stage 5 — avisos y restauración real de Ayuda

Fecha: 2026-10-10. Candidata parcial; no aceptación humana ni distribución.

## Puerta de entrada y auditoría del orden

Contenedor y checkout independientes verificados; rama `codex/miguardia-3.0`,
HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`, upstream homónimo en origin.
Origen `https://github.com/blackat-systems/MiGuardia.git`; `.git` directorio propio,
un worktree, contenedor sin Git y staging vacío. Se recibieron 188 entradas de
status y 957 paths; coincide exactamente el sello previo de orden/índices,
incluida la eliminación preexistente de `VerticalScrollbarMetricsTest.kt`.
2.x y recuperación permanecieron de sólo lectura, sin reconstrucciones.

Revisión independiente del delta de orden: aprobada. Doce hashes propios y patch
reversible coincidentes; documentación, catálogo, diff y fsck válidos. Dos blobs
colgantes no son corrupción. No se movió ni reindexó código, ni se modificaron
los sellos anteriores. Evidencia: `.local-evidence/index-audit-20261010/` y
[auditoría del orden](2026-10-10-integridad-y-orden-del-proyecto.md).

Joaquín autorizó retomar Stage 5 exclusivamente en QA Samsung libre, sin abrir
la app pública ni sus datos. Entrada y delta de esta unidad:
`.local-evidence/stage5/continuacion-20261010/`. El manifiesto de entrada incluye
hashes del checkout y README del contenedor; el cierre contrasta todo lo ajeno.

## Defectos comprobados y decisión

1. El overlay del Calendario interceptaba flechas a zoom 150/200 y el día 1
   a 200. Reproducciones `run-20261010T182451.942373Z.json` y
   `run-20261010T182607.530141Z.json`, QA c079…7227. No eran simples nodos ausentes:
   se comparan callbacks después de toques físicos sobre controles/días exactos.
2. Clima conservaba el mismo defecto: Cerrar tapado/no accionado en seis
   combinaciones, `run-20261010T182531.388562Z.json`, misma QA.
3. Reservar el aviso completo en Calendario tampoco resuelve ventanas bajas:
   deja fechas fuera del área visible. Experimento adverso
   `run-20261010T183558.645597Z.json`, QA bae665…1ff81, ventana base real 360×600.

Decisión expresa de Joaquín: **aviso breve en encabezado y detalle al tocar**.
Se sustituye temporalmente la marca por `Ver aviso`, sin añadir filas ni quitar
área mensual. Tiene target base ≥48 dp, anuncio accesible del texto completo y
diálogo consciente desplazable. Abrir el diálogo conserva una copia guardable;
el vencimiento del aviso no lo cierra ni pierde el texto. Atrás cierra el diálogo
antes de cancelar selección. En ventanas de selección menores a 600 dp, donde
ya se oculta el encabezado, el mensaje espera su regreso antes de iniciar el
vencimiento. No se reintroducen scroll mensual, semana forzada ni barra permanente.

Clima usa reserva de espacio. El wizard manual también reserva su aviso; sigue
teniendo su scroll propio. Se conserva la prioridad del mensaje y de su dueño;
el callback de vencimiento se actualiza aun cuando el texto no cambia.

Fuentes productivas: `MiGuardiaApp.kt`, `CalendarScreen.kt`,
`ui/components/TransientConfirmation.kt` y `ui/weather/WeatherScreens.kt`, bajo
`app/src/main/java/com/blackatsystems/miguardia/ui/` (los dos primeros directamente
en ese directorio). Sin cambios de dominio, Room, Copias, permisos o dependencias.

## Validación local atribuible

- `header-candidate-validation-2.log`: 511/511 JVM de app, cero fallos/omitidas;
  `lintDebug`, APK QA, AndroidTest QA de app y AndroidTest Debug de base verdes.
  No se repiten dominio/base/interoperabilidad por estos cambios de presentación.
- Ajustes posteriores exclusivos de pruebas: runners app/base recompilados en
  `header-runner-validation-3.log` y `process-runner-validation-3.log`.
- Herramientas: 34/34 unitarias de QA, 13/13 guardas de foco y 6/6 del digest
  SQLite ficticio. Las últimas comprueban orden independiente, WAL/checkpoint,
  cambio de valor, concurrencia/corrupción y cierre/limpieza ante excepción.
- Catálogo: 120 clases, 28 grupos. El nuevo grupo de restauración es `manual_only`:
  el runner independiente exige opt-in y SHA exacto de QA. No habilita ejecución
  genérica, reset de datos ni exploración de Recientes.

## QA física y atribución

Samsung `SM-S938B`/API 36. QA producto:
`6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`.
Trece métodos distintos completados; no son trece recorridos integrales:

| Área | Casos | Recibos bajo la carpeta de esta unidad |
| --- | --- | --- |
| Composición: formularios y todos los días del mes | 2 | `run-20261010T185057.886585Z.json`, completados antes del corte |
| Diálogo/restauración sin cancelar selección | 1 | `run-20261010T185225.581296Z.json` |
| Aviso en encabezado, sin altura adicional | 1 | `run-20261010T185333.276342Z.json` |
| Insets aplicados una sola vez | 1 | `run-20261010T185341.627402Z.json` |
| Ayuda/reflow/restauración y etiquetas completas | 2 | `run-20261010T185345.824348Z.json`, `run-20261010T185352.142089Z.json` |
| Confirmaciones/Clima/propietario/vencimiento | 4 | `run-20261010T185359.517381Z.json` |
| Mensaje retenido en selección a 550 dp | 1 | `run-20261010T185543.207627Z.json` |
| Muerte real de proceso y regreso a misma tarea | 1 | `process-20261010T185721.299268Z.json` |

Los primeros once métodos de app usaron runner
`7d52ee3f69fe0d959c1e1727ccd76e1caac58d1a29ae64365e604dfdfad8e2e5`;
el caso 550 dp usa el final
`6961de177c34b429060d28a34e962e485b4dce81123295b328d038c56f912324`.
Runner independiente de base:
`93af6d35eb158865f4efad65948b6df0deca0e2016a049020dd646d249905a01`.
No se reasignan once casos al runner posterior ni se presenta el lote cortado
como ocho pasadas en una sola ejecución.

La matriz de avisos tiene doce layouts: mes de seis filas, 360×600 dp base,
dos temas, tres zooms y VIEW/EDIT. Comprueba contención, targets base ≥48 dp,
ausencia de superposición y toca cada uno de los 31 días verificando identidad
del callback, además de flechas y Cancelar. Capturas de **fixture propio** en
`review-header/`, doce PNG con hashes; no capturas generales del teléfono.

## Ayuda: proceso e integridad

Nueva prueba `Stage5HelpProcessRestorationInstrumentedTest.kt`, en
`core/database/src/androidTest/java/com/blackatsystems/miguardia/core/database/`.
UI productiva y runner independiente: abre Ayuda, repite recorrido, llega a
Fotos 5/8, lleva QA a segundo plano, verifica STOPPED, mata ese proceso y observa
su ausencia. Restaura la tarea QA validada con PID distinto y el mismo paso.
Luego vuelve al paso 4, cierra a Ayuda, repite muerte/restauración y comprueba
ausencia de reentrada. Termina en Calendario; sin seed, reset ni force-stop QA
en ese ensayo, ni lectura de Recientes globales.

El recibo registra dos ciclos con PID distintos/misma tarea; onboarding y
DB/WAL binarios iguales. El supervisor además verifica integridad SQLite,
cero violaciones de claves foráneas, esquema/versión y multiconjuntos de filas
de las 31 tablas SQLite iguales antes/después: 29 de negocio y dos de metadatos.
Las copias locales privadas se cierran
y retiran tras calcular hashes; no se guardan filas crudas. Si sólo cambiara un
checkpoint WAL, el test no lo llamaría mutación laboral ni daría integridad por
probada sin el contraste lógico. No se certifican por ello archivos privados
externos a Room ni todos los borradores posibles.

## Evidencia adversa y límites

- Primer fixture de calendario mal escalado: 360×600 de Android resultaba
  540×900 base. Los primeros verdes de reserva, incluidos 183354 y `review/`,
  no acreditan una ventana baja; se preservan y no sustituyen `review-header/`.
  El fixture final declara tamaño base y lo afirma antes de comprobar targets.
- Caso 550 dp falló antes de publicar `onSizeChanged` con reloj congelado
  (`185240`). Ahora avanza dos frames de medición y afirma alto real; sin sleeps
  ni relajación de expectativas. Cierre 185543, sin cambio productivo posterior.
- Lote 185057 detenido por foco null/no resuelto entre fixtures después de dos
  métodos. Se preserva el corte y se ejecutan los restantes por separado;
  no se amplía el guard ni se atribuye la causa al usuario.
- Arnés de proceso: import/constante de accesibilidad no disponible corregido
  después del build fallido; en Android ICU la llave final de regex debía estar
  escapada y `Hist` llevaba dos espacios (185452/185607). Corregidos para el
  formato observado manteniendo una sola Activity/tarea QA; recibos conservados.
  No son defectos de restauración del producto.
- Revisión independiente detectó dos riesgos del helper antes de ejecutarlo:
  conexión SQLite sin cierre explícito y recibo perdido si fallaban probes del
  finally. Corregidos con cierre explícito y sellado anidado; cierre incompleto
  falla seguro. La lectura del recibo tiene timeout y tamaño limitados.

Público antes/después:
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
No se abrió, instrumentó ni leyó su sandbox. Permisos/flags/accesibilidad
conservados; no imágenes personales, credenciales, red nueva, avisos reales,
firma, publicación, commit, push o tag. Los temporales retirados eran copias
de lectura de la base QA, no fuentes ni evidencia adversa.

Revisión humana de legibilidad, composición y accesibilidad integral pendiente;
otros subcasos se conservan en la [matriz](2026-10-02-stage5-matriz-recorridos.md).
La imagen OEM 1/1 anterior, voz de tres controles y rendimiento mantienen sus
candidatas propias. No se declara nueva medición ni fluidez integral global.
Próximo MAIN: contrastar sello/delta y completar sólo riesgos concretos abiertos.
