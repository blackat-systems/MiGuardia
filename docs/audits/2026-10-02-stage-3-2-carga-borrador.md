# Stage 3.2 — carga ordenada y borrador

Fecha: 2026-10-02. Candidato técnico verificado, instalado para revisión humana.
No aceptación de Stage 3 ni autorización de distribución.
Contrato: `docs/prompts/STAGE_3_2_CARGA_ORDENADA_Y_BORRADOR_3_0.md`.

## Puerta 0 y causa

Ruta independiente MiGuardiaContainer/MiGuardia-3.0, rama/upstream
`codex/miguardia-3.0` / `origin/codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`; remoto
`https://github.com/blackat-systems/MiGuardia.git`. Sin staging ni index.lock.
MAIN inactivo. Los 22 hashes de producto del manifiesto de cierre previo
coincidían. Checkout acumulado preservado; fotografía de doce archivos afectados
antes de escribir: `.local-evidence/stage32-baseline-20261002/`.
Una sola ejecución Gradle y operador de dispositivo; colaborador limitado a
coordinador/JVM y luego ActivityTest, sin escrituras simultáneas en esos archivos.

La carga anterior tenía otro encabezado, fechas largas, catálogo modal y puesto/
retorno detrás de opciones avanzadas. El coordinador persistía fechas confirmadas,
no cada selección transitoria vacía; al regresar quedaban decisiones preparadas
incompletamente invalidadas. El catálogo revalidaba identidad, no cambios de valores.
No se diagnosticó pérdida de datos SQL ni se necesitó migración.

## Delta propio

- `UnifiedManualLoadForm.kt`: orden contractual, tres fechas cortas y expansión
  con días de semana, catálogo integrado alfabético, creación/horarios accesibles,
  editor de puesto, retorno directo, notas y jerarquía guardar/limpiar/cancelar.
- `V2ManualShiftLoadScreens.kt`: acción de sincronización de fechas y retiro de
  códigos hexadecimales, conservando acento de color y selección textual/semántica.
- `MiGuardiaApp.kt`: sincronización exclusiva EDIT/MANUAL_LOAD/SELECT_DATES.
- `V2ManualShiftLoadViewModel.kt`: conserva borrador y vacío; invalida previews,
  conflictos y decisiones; huella del catálogo en SavedState, reselección explícita
  al detectar cambios; bloquea callbacks de guardar/revisar durante selección.
- `V2ManualShiftLoadCoordinatorTest.kt`, `V2ManualShiftLoadComposeTest.kt`,
  `V2ManualShiftLoadActivityTest.kt`: regresiones locales, UI y recorrido real.
- `tools/qa-groups.json`: métodos/huellas actualizados, sin ampliar permisos.
- Contrato/índice, STATUS, handoff y esta auditoría.

Se reutilizan campo position y notas/archivos temporales privados. Ninguna imagen
binaria entra en navegación. No se cambian NextEvent, Room, Copias, repositorios de
persistencia, versión, applicationId, firma, distribución ni website.

## Evidencia local nueva

- Focal: 68/68 (coordinador 57, Atrás 3, feriados 8), cero fallos.
  `stage32-focused.log` y XML en `stage32-focused-results/`.
- Local completo fresco: 900/900 (dominio 395, database 19, app 486), cero
  omitidas/fallos/errores, recibo `optimization/20261002T184041.265728Z/result.json`.
- Build completo: verde, `optimization/20261002T184149.103231Z/result.json`.
  Ajustes finales de presentación/fixture revalidados separadamente.
- Revalidación final QA/AndroidTest/lint: `stage32-final-qa-build.log`, verde;
  lint cero errores y 24 advertencias. Último ajuste exclusivamente de aserción:
  AndroidTest recompilado en `stage32-test-assertion-build.log` (verde).
- Catálogo QA: válido (111 clases, 21 grupos). Diff --check sin errores.
  Docs_check exigió conservar AUTORIZADO para el único contrato de STATUS aun
  con ejecución cerrada; se corrigió el índice sin modificar el validador.

## QA física nueva e identidad

Samsung SM-S938B, serial [serial QA en binding local privado], variante QA y datos ficticios.
Runner oficial; 55 casos distintos finales verdes, ninguno omitido:

| Clase / alcance | Resultado | Recibo bajo .local-evidence/qa |
|---|---|---|
| V2ManualShiftLoadComposeTest | 34/34 | 20261002T190545.748024Z |
| V2ManualShiftLoadActivityTest | 8/8 | 20261002T190118.261471Z |
| ExceptionsComposeTest | 8/8 | 20261002T190655.451270Z |
| CalendarAdaptiveLayoutComposeTest | 5/5 | 20261002T190703.914715Z |

Activity verifica selección inicial de cuatro fechas, vaciado, recreación, dos
fechas finales, valores completos e imagen privada conservados, ausencia de
escrituras anticipadas y guardado exacto con notas/archivos independientes.
Preserva además rotación, Atrás, creación/cancelación contextual, carga individual/
múltiple, Sobrescribir/Superponer, Franco y limpieza. Las regresiones de formulario
incluyen catálogo vacío/sin horarios, agregar horario a uno existente, alfabético,
fechas una/tres/más, puesto agregar/editar/quitar y controles con temas/zoom.

APK instalado `com.blackatsystems.miguardia.qa`, versionName 2.0.2/code 6 intactos:
`fbcfdf05cccaa7b9b1f69b5424979317ca1ad53c8a1becf7265b4c4fb3f004f2`.
Runner instalado final `com.blackatsystems.miguardia.qa.test`:
`d088ff91479988a7902c82cf3439aac7ed4b3a0a4b7dd06ded39581b8224c05a`.
Activity usó el runner `528b386f4691a1873cbb0cdf5f1d321dc68dcfd15d4b0d958b2ae903a16738bf`;
después cambió únicamente la aserción Compose documentada abajo. El APK de
producto y la clase Activity no cambiaron; no se repitió Activity por ritual.

El SHA-256 del APK público antes/después coincide:
`be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
Se verificaron nombres mediante AAPT antes de instalar y hashes local/instalado
mediante runner; no se abrió/instrumentó/reemplazó el paquete público ni se
consultaron datos personales. QA queda instalada y abierta en Calendario.

## Capturas y revisión visual limitada

71 PNG bajo `.local-evidence/stage32-captures/`: 70 del fixture y una de la
instalación final. Huellas en `.local-evidence/stage32-final-manifest.json`.
Se inspeccionaron 13 imágenes representativas: fechas expandidas, catálogo,
selección vacía, borrador final, Calendario completo, detalle, creación de horario
y controles de formulario en claro/oscuro y zoom interno 100/150/200.
Texto adaptable, sin superposición observada; el formulario tiene scroll propio.
Las capturas de controles desplazados no implican recorte irrecuperable.
El Calendario final muestra 1–31 sin barra permanente inferior.
No se afirma revisión manual de cada PNG ni aceptación visual de Joaquín.

Todas las rutas de evidencia anteriores son relativas a `.local-evidence/`.
Las pruebas anteriores de Stage 3 no se presentan como validación de Stage 3.2.

## Hallazgos adversos conservados

La revisión detectó una PNG malformada en el nuevo fixture, copiada de una prueba
que sólo trasladaba bytes y no decodificaba imágenes. CRC de IDAT inválido y chunk
final desalineado: no era un defecto demostrado del importador. Se reemplazó por
una imagen sintética creada mediante Bitmap; se conservan aserciones
de bytes, referencias independientes, recreación y ausencia de escrituras previas.
No se aumentaron esperas ni se eliminó una comprobación para fabricar un verde.
Diagnóstico PNG preservado: `stage32-fixture-png-diagnostic.log`.

Primer formulario físico: 33/34, recibo `qa/20261002T185915.021720Z/`.
La regresión nueva de puesto comparaba texto exacto «Recepción ficticia», pero el
nodo contenía correctamente «Puesto o función: Recepción ficticia». Diagnóstico
directo focal reproduce el mismo fallo, con estado semántico completo:
`stage32-position-diagnostic.log`. Capa responsable: aserción de prueba, no
conservación del dato. Se corrigió a la etiqueta completa, sin usar una
comparación más laxa ni retirar las comprobaciones de editar/contraer/quitar.

La auditoría histórica de Stage 3 conserva el defecto de Atrás, semana forzada,
fallos del runner y correcciones previas. No se sobrescriben esos antecedentes.

## Límites y revisión humana

No se presume aceptación a partir de pruebas o capturas. TalkBack y picker OEM
siguen siendo revisión humana; la imagen nueva entra por FileProvider sintético
privado, no por medios personales. No se modifican ajustes globales del Samsung.

Un reintento con confirmación SQL perdida conserva la identidad de la operación:
si ya se había guardado, recupera esa carga y lo informa, sin duplicarla. Volver a
fechas no convierte una escritura ya confirmada en otra carga silenciosa.

Recorrido humano: abrir un día → Editar día; desplegar instituciones y elegir
horario; agregar puesto y nota; volver a elegir días, agregar/quitar y continuar;
comprobar borrador y guardar. Con más de tres fechas, revisar Mostrar todos/menos.
Probar Agregar horario en una institución con horarios y cancelar la creación.
