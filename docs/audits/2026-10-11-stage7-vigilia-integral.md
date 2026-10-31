# Stage 7 — Vigilia integral: candidato local

Fecha: 2026-10-11. Integración y revisión: MAIN.

## Decisión y autoridad

Joaquín inicializó expresamente «Stage 7: implementación y validación local».
Se implementó la dirección Vigilia sobre el candidato acumulado, conservando
las entradas y garantías funcionales. El cierre es **parcial**: la autorización
no incluye instalación, dispositivo, servicios, firma ni distribución. La
implementación y las pruebas locales no acreditan aceptación visual humana.

Contrato: [Stage 7](../prompts/STAGE_7_VIGILIA_INTEGRAL_SIN_REGRESIONES_3_0.md).
Estado operativo: [STATUS](../STATUS.md). Cobertura por capacidad:
[matriz de conservación](2026-10-11-stage7-matriz-conservacion.md).

## Puerta 0 y atribución

- Checkout: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardiaContainer\MiGuardia-3.0`;
  `.git` propio, único worktree y rama `codex/miguardia-3.0`.
- HEAD y upstream: `932eaeb60c6b6e68975534788e3e6501c967b1ae`,
  `origin/codex/miguardia-3.0`; remoto `blackat-systems/MiGuardia`.
- Staging vacío. El árbol ya tenía modificaciones rastreadas, nuevos y una
  eliminación; HEAD no representa ese candidato. Se preservó su estado.
- El sello de salida Stage 6 tenía 964 rutas y la entrada Stage 7, 966. Las
  diferencias corresponden a preparación de STATUS/índice y a la referencia
  PNG/contrato Stage 7, con procedencia documental explicada antes de escribir.
- Originales y hashes previos: `.local-evidence/stage7/20261011-vigilia/entry/`;
  `repository-seal.json`, `preexisting.patch` y `files/`. El delta propio se
  compara con esa entrada, no se atribuye todo `git diff HEAD` a este stage.
- Los tres especialistas tuvieron archivos sin superposición; MAIN fue dueño
  de tema, componentes, calendario, integración, catálogo QA y documentación.
  La revisión independiente fue de sólo lectura y MAIN contrastó los deltas.

## Cambio integrado

El único tema existente ahora usa fondo carbón/claro, superficies moderadas,
selección suave, fecha turquesa y pares explícitos para completadas/planificadas.
Se conserva Claro/Oscuro/Sistema y el zoom 100/150/200. Sans-serif local,
pesos semibold y cifras tabulares reemplazan la jerarquía pesada; no se agrega
fuente remota ni dependencia. El gradiente discreto vive en próxima jornada.

El texto secundario claro se ajustó de la referencia `#706B80` a `#665F74`
para pasar contraste también sobre la superficie elevada más exigente. Los
bordes funcionales tienen contraste mayor que los divisores decorativos.
Los roles de texto normal se prueban a 4,5:1; los controles esenciales a 3:1,
incluyendo superficies reales, once muestras del gradiente y capas de estado.
Los controles mantienen estados nativos de foco, pulsación y deshabilitado.
Selección y estado tienen texto/semántica, además de color.

La presentación alcanza carga unificada, catálogo/configuración, cuatro
rubros, repetición, edición, horario real/extras, Retén, excepciones/vacaciones,
Resumen/desgloses, Informes, fotos/notas, Avisos, Clima, Widget, Copias,
bloqueo, Apariencia, Ayuda y Actualizaciones. Soporte/aporte conservan sus
enlaces y retorno; no se inventa una pantalla propia donde no existía.
La matriz identifica 32 capacidades, entradas anteriores/nuevas, estados,
acciones, archivos y pruebas. Las familias heredadas sin cambio local de
estructura consumen el tema/componentes comunes.

El calendario conserva geometría base y zoom tipográfico de ADR 0042,
siete columnas, mes completo sin scroll, selección y Atrás vigentes. La
síntesis `08–16` se limita a horas enteras del mismo día. Con minutos o cruce
de fecha se conserva el rango completo y `+N` calculado desde instantes
reales; si no entra, se omite la síntesis entera según política vigente.
Detalle y accesibilidad conservan el horario completo y el cruce de fecha.
Las jornadas múltiples siguen separadas por UUID. En detalle angosto el
indicador de color pasa arriba para dar ancho al contenido, sin recolorear
la institución ni retirar acciones.

V01 se corrige separando etiqueta e importe de horas con 12 dp y distribución
adaptable. V02 separa la inclusión médica y su interruptor; la fila es el único
dueño del cambio y mantiene la confirmación médica. Se cubren equivalentes
en Resumen, personalización y confirmaciones. Las regresiones verifican texto
real renderizado, palabras, cifras/unidades, límites, separación y callbacks.
V04 sigue siendo una hipótesis de descubribilidad hasta revisión humana.
La jerarquía común y las instrucciones preservadas abordan V03/V05/V06/V07;
su eficacia visual no se presume desde una compilación.

## Revisión independiente y correcciones

Se inspeccionaron los deltas por familia contra `entry/files/`, incluido el
inventario de acciones/literales de carga y los 17 mensajes de Actualizaciones.
Se preservaron las confirmaciones privadas y acciones de error/reintento.
La revisión encontró y trató estos casos antes del candidato final:

| ID | Evidencia y riesgo | Corrección / límite |
|---|---|---|
| R01 | AccessLock: al autenticar, el botón queda deshabilitado; `onPrimary` no corresponde a ese fondo | Spinner con `primary`; Surface explícita. Contraste calculado del indicador sobre contenedor deshabilitado: 4,654:1 claro / 6,384:1 oscuro; píxeles Android pendientes. Autenticación y callbacks preservados |
| R02 | Widget: recuperación/error omitían tema y zoom usados por Ready; Column raíz no pintaba el fondo | Mismos parámetros locales y fondo del tema en recuperación/carga. Gate y política de datos preservados |
| R03 | Símbolo, padding y fila de identidad reducían el ancho de Completada a zoom 200 | Badge mide tipografía, omite adorno si compite y ajusta padding; detalle apilado. Regresión de ventana angosta |
| R04 | Resumen: chevron y valor forzado a una línea competían por ancho; cobertura de importe largo insuficiente | Medición efectiva, prioridad del valor y grupos cifra/unidad; ventanas 320/360 base y ambos importes. Inspección del equivalente en Horas |

Se corrigieron además dos expectativas antiguas de la grilla: la jornada
diurna entera ahora muestra `06–10`. Se añadió la comprobación de `06:00` a
`10:00` en accesibilidad y se conservan los horarios completos del detalle.

Los hallazgos iniciales se conservan en
`.local-evidence/stage7/20261011-vigilia/review/independent-findings.json`.
La revisión de código no sustituye la ejecución Android de esos casos.

## Validación local

Primera batería local fresca: **930/930 JVM** (dominio 395, base 19, app 516),
sin fallos/errores/omitidos, tres tareas ejecutadas y `changed_inputs: []`.
Recibo: `.local-evidence/stage7/20261011-vigilia/validation/local-jbr/result.json`.
Es evidencia de la primera candidata; las correcciones posteriores requieren
su recibo final. El primer intento `validation/local/result.json` falló en
preflight por Java fuera de PATH y se conserva. Se usó el JBR ya existente
mediante entorno temporal; no se instaló un runtime ni se modificó Gradle.

El análisis estático `lintDebug` terminó sin errores y con 24 advertencias
en configuración de herramientas/dependencias, recursos sin uso y líneas
heredadas (orden de Modifier y sugerencias KTX). Resumen conservado en
`validation/lint-summary.json`, bajo el directorio de evidencia Stage 7.
No se modifican esas fronteras para eliminar advertencias ajenas al delta.

Perfil build final **PASÓ**, 719,722 s, `changed_inputs: []`:
`validation/build/result.json`. Incluye lint, Debug, QA, Release unsigned y
AndroidTest de app/base. Gradle registró 50 tareas ejecutadas, 5 desde caché
y 280 actualizadas; no se presenta como una reconstrucción global fresca.
Los APK app/QA/Release y runner app se generaron; el runner base se reutilizó
con sus inputs intactos, y se declara expresamente.

| Artefacto | Frescura | SHA-256 |
|---|---|---|
| Debug | generado | `61d5d4610298161ca10224b35a379063d899359dec658c650cd012c2e789a432` |
| QA | generado | `5d2b65b3a14a45c3a54240d81dedcb12c8741064f3d2f902528a04539a351707` |
| Release unsigned | generado | `f7cf1c6d7dfb6706db587c59406e7fdde6901b9896cffd7bd165fe36923aba01` |
| Runner app QA | generado | `1d68df0b70313e1fb0827428c059e357d9c29c8c7c6b160e498cdea5eb7db039` |
| Runner base Debug | reutilizado | `93af6d35eb158865f4efad65948b6df0deca0e2016a049020dd646d249905a01` |

La QA técnica y los dos runners se preservan en `artifacts/`, con hashes
contrastados y `manifest.json`, dentro de la evidencia ignorada de este stage.
No se instalaron. Debug/QA usan la firma técnica del build; Release permanece
unsigned y no se usó la clave de distribución.

Batería local final fresca **PASÓ: 930/930 JVM**, 59,997 s. Dominio 395,
base 19 y app 516; las tres tareas se ejecutaron, cero resultados de tests
reutilizados, fallos, errores u omitidos. Recibo:
`validation/local-final/result.json`. `changed_inputs: []`; sus inputs de
entrada coinciden exactamente con la salida del build. El verde corresponde
al código posterior a todas las correcciones, no al candidato intermedio.

Delta productivo propio: 39 archivos de presentación/recursos; 9 clases
AndroidTest actualizadas, 4 archivos JVM (2 nuevos), catálogo y documentación.
La revisión contra entrada no detectó cambios en las fronteras protegidas.
Se conserva la eliminación previa de `VerticalScrollbarMetricsTest.kt`;
no es una eliminación de Stage 7. Inventario y huellas de salida:
`exit/repository-seal.json`; patch atribuible: `review/stage7-own.patch`.

Regresiones nuevas/actualizadas:

- JVM: `VigiliaContrastTest`, `CalendarFormattingTest`, paleta/contraste Widget
  y expectativas de barras del sistema.
- Compose: selección de configuración y disponibilidad, horas completas,
  Resumen/desglose, inclusión médica con único interruptor, tema y detalle
  angosto con acciones. Las pruebas Android se compilan, no se ejecutan aquí.
- RemoteViews: paleta real/drawable y color institucional preservado.
- La matriz mensual existente de 48 combinaciones y los casos de ventanas
  bajas se preservan; no se reasigna su recibo histórico a este APK.
- El catálogo mantiene grupos/riesgos y sólo actualiza hashes/métodos de las nueve
  clases revisadas. No habilita instrumentación ni mutaciones de QA.

## Comparación antes/después y cobertura pendiente

La referencia elegida es ficticia y se usó sólo para dirección estética. La
base real es el corpus Stage 6 **nuevo** de 41 capturas, no el obsoleto:
`.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/`.

| Base anterior | Delta implementado | Después verificable |
|---|---|---|
| `09-calendario.png` | Tema, celdas/estados, números/horarios, próxima jornada y selección | Captura del APK Stage 7 pendiente |
| `13-detalle.png` | Fecha/estado, jerarquía, color institucional y distribución angosta | Captura del APK Stage 7 pendiente |
| `19-catalogo.png` | Selección suave, opciones reconocibles, jerarquía y acciones preservadas | Captura del APK Stage 7 pendiente |
| `22-resumen.png` | Tema, ritmo, distribución de etiquetas/importes y personalización | Captura del APK Stage 7 pendiente |
| `23-informe.png` | Inclusiones separadas de controles y confirmaciones preservadas | Captura del APK Stage 7 pendiente |
| `35-ayuda.png` | Componentes comunes, tema, jerarquía y controles reales del tour | Captura del APK Stage 7 pendiente |

El resto de familias se traza en la matriz. **No hay capturas nuevas de la app
instalada**, pruebas de dispositivo ni aceptación de Joaquín en este bloque.
No se usaron imágenes generadas para acreditar Android.

Pendientes necesarios para cierre integral: QA Stage 7 expresamente autorizada
con datos sintéticos; identidad/hash del APK instalado; matriz mensual completa
y ventanas bajas; flujos críticos y recreación/restauración; consultas sin
escritura; privacidad de Informes/Copias/bloqueo; TalkBack/foco/teclado,
selectores/permisos/cancelación/retorno; Widget OEM; comparación real de las
seis bases y demás familias; rendimiento contra la base pertinente de Stage 5;
revisión humana de legibilidad y descubribilidad. Los pendientes anteriores
de Stage 3/3.2/5 y sus evidencias adversas siguen vigentes.

## Fronteras preservadas

El delta no cambia dominio, cálculo, Room/esquemas, CAS, identidad de operación,
persistencia, formato/cifrado de Copias, privacidad, permisos, FLAG_SECURE,
autenticación, contratos de red, Gradle/dependencias, versión ni firma.
Los colores institucionales siguen siendo datos. Widget cambia paleta y
recursos propios, no alarmas/proveedor. La UI Compose de Avisos adopta Vigilia. El chrome de notificaciones lo controla
Android; el código propio `ShiftNotificationPresenter` conserva sus acentos
`#8B5CFF` y `#665E70`, fuera del delta autorizado de presentación y sin cambios
en alarmas/proveedor. No se modifica Android, launcher, biometría, SAF, Maps o
navegador.
No se ejecutó interoperabilidad remota porque sus contratos/consumidores no
cambiaron. No se usaron dispositivos, datos reales ni servicios externos.
Sin commit, push, tag, firma de distribución, instalación ni publicación.

## Controles de cierre

`python tools/docs_check.py` pasó: 39.861/40.000 caracteres de guía, 384
referencias, 41 ADR y 103 snapshots preservados. El último intento intermedio
exigió mantener la marca AUTORIZADO en el índice mientras STATUS conserva
Stage 7 activo; se corrigió el índice sin relajar el verificador. Se conserva
`validation/docs-check-before-index-final.json` junto al recibo verde.
`python tools/qa.py --check-catalog`: VALID, 120 clases y 28 grupos.
`git diff --check`: salida 0, staging vacío y HEAD sin cambios.

El sello final separa 53 rutas existentes modificadas y cinco nuevas: 58
archivos propios (39 productivos/recursos, 9 AndroidTest, 4 JVM, 5 documentos
y catálogo). No hay eliminaciones nuevas ni delta en fronteras protegidas;
la ausencia previa del test eliminado se conserva. Los nuevos archivos
versionables son Kotlin/Markdown; APK técnicos y evidencia quedan ignorados.
Los originales de entrada y los recibos adversos permanecen disponibles.
