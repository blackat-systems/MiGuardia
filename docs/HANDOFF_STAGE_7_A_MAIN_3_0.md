# Handoff Stage 7 a MAIN — Vigilia integral

Fecha: 2026-10-13.

## Entrega vigente — correcciones R4 y QA física

**Decisión:** candidata R4 verificada localmente y en Samsung QA para el alcance
dirigido. Stage 7 continúa EN_CURSO, sin aceptación estética integral ni permiso
de distribución. Joaquín autorizó paisaje lateral, revisión de incongruencias y
corrección de la franja bajo la hoja; no se amplió dominio ni persistencia.

**Resultado:** menú y navegación mensual laterales sólo en ventanas anchas/bajas;
mes completo y una sola grilla, sin barra normal ni scroll mensual. El progreso
de reintento no comprime las casillas y los errores resueltos no dejan un diálogo
vacío. Hoja inferior sin padding exterior duplicado, conservando inset nativo y
respiro interior. Etiquetas secundarias completas u omitidas, nunca truncadas.
Informes distribuye título/botón según ancho y letra reales, sin achicar fuente.
Se mantienen oscuro inicial, elecciones guardadas, letra Android, TalkBack sin
controles extra, Copias legadas, borradores, CAS y dueños de Atrás.

**Delta:** 18 archivos desde la entrada de reanudación: cinco productivos, ocho
pruebas Android, una JVM, catálogo y tres documentos. Checkout acumulado preservado;
sin cambios a Room, dominio, Copias/cifrado, autenticación, Gradle, manifiestos,
permisos, versión o firma de distribución. Revisión independiente final sin
nuevos hallazgos materiales. No hubo commit, push, tag ni publicación.

**Verificación R4:** 935/935 JVM frescas; lint y build completos verdes, seis
artefactos e inputs estables. Samsung: 56 métodos dirigidos verdes, incluidos
48/48 combinaciones mensuales, 18 escenarios de Informes y 12 de hoja inferior
dentro de sus métodos; no se suman dos veces. Otras familias: menú, Clima,
edición, horario real, repetición, avisos, bloqueo, Widget y Actualizaciones.
TalkBack conserva 1/1 técnico y confirmación humana de los tres anuncios claros;
no se atribuye como nueva escucha del rail. Los adversos R2/R3 y sus causas
permanecen, sin reasignar los verdes de candidatas anteriores.

**Identidad:** QA `99e32ed7525b23ac5452d8eebab019f7051c2f7e6e50dc47db9a2e918dd4dc29`;
runner `bc31f05ebcb3119e9a3d3d8ef38402be31a395385ec9ae36ccbd10c92f8c3d3c`.
Artefactos: `.local-evidence/stage7/android-font-20261011/artifacts-final-r4/`.
App pública intacta por hash, fuera de apertura/instrumentación; sin datos
personales ni cambios a ajustes globales Android.
El 13/10 se completaron y revisaron dos capturas originales: mes completo y hoja
vacía sin franja exterior. R4 quedó abierta en Calendario oscuro, sin selección
ni hoja. Hashes instalados coincidentes; recibo `captures/exit-20261013.json`,
bajo la evidencia Android. Sin reinstalar, cambiar producto ni repetir baterías.

**Rendimiento:** representative sin alertas; stress permanece abierto. Resumen
superó un umbral en ambas parejas y el guardado en la segunda, aunque todos los
métodos terminaron funcionalmente bien. Se preservan muestras, umbrales y ambos
adversos. No equivale a fluidez global ni permite atribuir una causa. La próxima
investigación debe separar navegación, proyección y composición sin cambiar a
ciegas dominio/persistencia. Candidata R4 restaurada después de la comparación.

**Límites y NEXT:** revisar visualmente la candidata, especialmente descubribilidad
del rail y densidad del mes. No certifica todos los dispositivos/ventanas, OEM de
imágenes/Widget, teclado/permisos ni fluidez global. Una ventana arbitrariamente
baja puede no contener seis filas táctiles de 48 dp; no se neutraliza la letra
Android para ocultarlo. Rendimiento y estado final se detallan en la
[auditoría de MAIN](audits/2026-10-11-stage7-qa-main.md).
Fuente operativa: [STATUS](STATUS.md). El [checkpoint](audits/2026-10-11-stage7-checkpoint-letra-android.md)
y los apartados siguientes son antecedentes, no reemplazan este cursor.

## Continuación de MAIN — QA autorizada y oscuro por defecto

Joaquín autorizó instalación y QA sintética exclusiva Samsung/QA, y agregó
oscuro inicial sin pisar elecciones guardadas. Ampliación implementada:
932/932 JVM frescos y build completo verdes; revisión instalada, pública
intacta. No implica aceptación visual ni distribución.

La campaña física original cerró 124/130 métodos distintos y matriz mensual
48/48. Seis comprobaciones fallaron; tres expectativas/selecciones obsoletas
de prueba se atribuyeron y sus reintentos pasaron sin relajar contratos. Tres
desbordes reportados por TextLayout requieren contrastar semántica y dibujo
antes de modificar producto. Claro/Oscuro persisten tras reiniciar QA.
La revisión oscura y el runner corregido conservan identidades y recibos
separados. Cursor actual: [STATUS](STATUS.md). Evidencia, causas, delta y
límites: [auditoría de MAIN](audits/2026-10-11-stage7-qa-main.md).
Joaquín confirmó después retirar zoom interno, respetar letra Android y mantener
TalkBack sin controles extra, preservando Copias. ADR 0043 sustituye la política
de zoom anterior; implementación y nueva validación en curso. Los recibos previos
no acreditan esta ampliación.

Los apartados siguientes conservan la entrega local original del especialista;
sus límites «sin dispositivo» describen esa entrega, no la autorización posterior.

## Entrega original — decisión

Candidato de Vigilia implementado; **cierre parcial local**. Se mantiene el
candidato acumulado y la funcionalidad trazada en 32 capacidades. Stage 7 no
está aceptado ni cerrado integralmente: faltan ejecución Android nueva,
comparación visual real, revisión humana y rendimiento pertinente.

Joaquín autorizó «Inicializar Stage 7: implementación y validación local».
No se deriva autorización para dispositivo, instalación, firma ni distribución.
Estado único: [STATUS](STATUS.md). Contrato:
[Stage 7](prompts/STAGE_7_VIGILIA_INTEGRAL_SIN_REGRESIONES_3_0.md).

## Delta propio y conservación

Tema único claro/oscuro/Sistema, paleta Vigilia ajustada por contraste, selección
suave, fecha turquesa, completadas/planificadas distinguibles, sans-serif local
y cifras tabulares. Componentes comunes, calendario/próxima jornada/detalle,
carga/catálogo/configuración y todas las familias propias consumen la misma
dirección visual. Widget tiene paleta, recursos y previews propios coordinados;
su recuperación/error y el bloqueo pintan fondos coherentes.

V01/V02 tienen correcciones de distribución y regresiones de contenido real:
12 dp de separación, etiquetas completas, cada cifra junto a su unidad,
interruptor médico con un solo callback y confirmación preservada. Resumen
prioriza importe sobre flecha decorativa y el detalle angosto da ancho al
estado/acciones. No se reduce la fuente para hacer entrar contenido.

El mes conserva ADR 0042, geometría base, zoom tipográfico y objetivos de 48 dp
base. `08–16` sólo sintetiza horas enteras del mismo día; minutos y cruces
conservan rango completo y `+N` real. La síntesis opcional se omite entera si
no cabe; horario y cruce siguen en detalle/accesibilidad. No se fusionan
jornadas por objetivo/horario. Borradores, fechas finales, Atrás, CAS,
confirmaciones y contratos de privacidad siguen en sus dueños previos.

Fuentes y atribución:

- [Auditoría](audits/2026-10-11-stage7-vigilia-integral.md): autoridad, Puerta 0,
  integración, hallazgos independientes, correcciones, evidencia y límites.
- [Matriz de conservación](audits/2026-10-11-stage7-matriz-conservacion.md):
  entradas anteriores/nuevas, estados, acciones, archivos y pruebas C01–C32.
- `.local-evidence/stage7/20261011-vigilia/entry/`: hashes y originales de
  entrada; `review/stage7-own.patch` y `review/delta-summary.json` atribuyen
  sólo este delta, separándolo del trabajo acumulado de Stages 1–6.
- Repositorio independiente `MiGuardia-3.0`, rama `codex/miguardia-3.0`,
  HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`; sin commit nuevo.

## Validación y artefactos

Primera batería fresca: 930/930 JVM. Recibo
`.local-evidence/stage7/20261011-vigilia/validation/local-jbr/result.json`;
sin cambios de inputs. El intento anterior sin Java en PATH se conserva.
Build final PASÓ: lintDebug sin errores (24 advertencias heredadas), Debug,
QA, Release unsigned y AndroidTest app/base; inputs estables. App/QA/Release
y runner app generados; runner base reutilizado sin cambios de insumos.
Recibo: `validation/build/result.json`, bajo la evidencia Stage 7.
Batería final fresca PASÓ: **930/930 JVM** (395 dominio + 19 base + 516 app),
cero fallos/errores/omitidos o resultados de test reutilizados, 59,997 s.
Recibo `validation/local-final/result.json`: inputs estables y exactamente
coincidentes con la candidata compilada. El primer verde queda como evidencia
intermedia separada. Sello final: `exit/repository-seal.json` de este stage.

QA técnica y runners preservados en
`.local-evidence/stage7/20261011-vigilia/artifacts/manifest.json`:

| Artefacto | SHA-256 |
|---|---|
| `app-qa.apk` | `5d2b65b3a14a45c3a54240d81dedcb12c8741064f3d2f902528a04539a351707` |
| `app-qa-androidTest.apk` | `1d68df0b70313e1fb0827428c059e357d9c29c8c7c6b160e498cdea5eb7db039` |
| `database-debug-androidTest.apk` (reutilizado) | `93af6d35eb158865f4efad65948b6df0deca0e2016a049020dd646d249905a01` |

Hashes de las copias comprobados contra el recibo; no instaladas. Debug/QA
usan firma técnica de build, sin usar la clave de distribución.

Las regresiones Android están definidas para ambos temas y los tres zooms;
Horas/Resumen incluyen ventanas 320×480 y 360×640, importes con minutos y
12345 horas. La matriz mensual de 48 combinaciones se preserva. Existencia y
compilación no acreditan render ni ejecución en dispositivo.

Controles finales: documentación verde (39.861/40.000 caracteres, 41 ADR,
103 snapshots), catálogo VALID (120 clases/28 grupos), `git diff --check`
con salida 0; staging vacío y HEAD original. Delta: 58 archivos propios,
sin eliminaciones nuevas ni cambios en fronteras protegidas. Los recibos
adversos y la eliminación previa quedan preservados.

## Límites y siguiente acción

La base visual válida sigue siendo el corpus Stage 6 nuevo de 41 capturas.
La auditoría enlaza las seis bases (09, 13, 19, 22, 23, 35) con el delta;
el **después del APK Stage 7 está pendiente**. La referencia elegida es una
maqueta estética, no evidencia de producto instalado.

MAIN debe obtener autorización específica de QA Stage 7 antes de instalar
exclusivamente QA con datos sintéticos, comprobar hash instalado/local y
conservar el paquete público. Entonces ejecutar matriz mensual/ventanas bajas,
regresiones nuevas y recorridos críticos completos, privacidad y consultas sin
escritura; revisar TalkBack/foco/teclado, selectores/permisos/retorno, Widget
OEM, recreación/restauración y rendimiento contra la base pertinente Stage 5.
Capturar las seis bases y familias restantes para la revisión visual de Joaquín.

V04 sigue siendo hipótesis humana. Una cifra con unidad que por sí sola exceda
todo el ancho disponible requiere una política adicional; los casos definidos
de 32/12345 horas y minutos todavía necesitan su ejecución Android. El chrome
de notificaciones lo controla Android; los acentos propios del presenter se
conservaron fuera del delta literal, tal como detalla la auditoría.

No se cerraron pendientes de Stage 3/3.2/5 ni se trasladaron sus recibos a este
APK. Dominio, Room, Copias/cifrado, CAS, autenticación, privacidad, permisos,
red, Gradle/dependencias, versión y firma permanecen fuera del delta propio.
Sin dispositivos, servicios remotos, commit, push, tag, firma de distribución, instalación o
publicación. La revisión humana y la distribución son puertas separadas.
