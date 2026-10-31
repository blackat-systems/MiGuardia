# Stage 6 — Diagnóstico del registro visual nuevo

Fecha: 2026-10-11. Especialista Stage 6. Autorrevisión; aceptación humana pendiente.

## Decisión y alcance

Registro representativo terminado sobre 41 capturas nuevas de MiGuardia QA. Su objetivo es preparar mejoras de estética, claridad y facilidad de uso. No se rediseñó ni eligió temática. La entrega anterior queda OBSOLETA por instrucción directa de Joaquín; su material se conserva sin reutilizar imágenes, inventario, cobertura ni diagnóstico.

Atlas local: [`index.html`](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/index.html). [Diagnóstico navegable](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/diagnostico.html). [Cobertura y límites](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/cobertura.html).

## Entrada y procedencia

- Puerta 0: checkout `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`, remoto `https://github.com/blackat-systems/MiGuardia.git`, un worktree, staging vacío. Entrada modificada preservada; 962 rutas con hash/ausencia en el sello de entrada.
- Samsung libre y desbloqueado, autorizado expresamente por Joaquín en este chat. Modelo SM-S938B, API 36; capturas 1440 × 3120 px, retrato, tema Claro, zoom interno 100 %. No se consultó ni modificó escala/fuente Android.
- Paquete exclusivo: `com.blackatsystems.miguardia.qa`. APK SHA-256 `6ae70da17b39a2a1fb89330d3aa78aefab67998da1464b599e4d05def045b1b3`. La etiqueta interna 2.0.2 en Actualizaciones corresponde a esta candidata QA identificada por paquete/hash; no acredita distribución de 3.0.
- Datos recibidos reconocibles como ficticios: Centro ficticio recurrente (REC), Puesto ficticio recurrente, Enfermería, jornadas 08:00–16:00 y nota S6Atlas ficticia. La vista previa de avisos usa contenido ficticio incorporado en la app. No se guardaron nuevas entidades ni se editaron/eliminaron registros.
- Foco QA y desbloqueo comprobados antes de acciones/capturas. Los árboles de navegación se filtraron al paquete QA en memoria; no se guardaron árboles globales. Un popup de Resumen con título genérico se verificó por ventana propietaria QA y padre QA antes de continuar.
- PNG reales obtenidos de la app, originales sin retoque. Cada imagen tiene ruta, hora UTC, APK, modelo/API, viewport, tema/zoom y SHA-256 en el manifest. Sin runner ni imágenes generadas.

## Hallazgos y objetivos posteriores

P2 indica prioridad media y P3 menor para mejora visual. No son fallos de cálculo o dominio. Reproducción común: abrir MiGuardia QA con la candidata y configuración indicadas, seguir la ruta del manifest de cada captura. No es necesario guardar datos.

### V01 — La cifra de horas pierde su columna (P2)

**DEFECTO VISIBLE.** La etiqueta «Horas de jornadas que todavía faltan trabajar» llega hasta la cifra. El valor 32 h queda dividido en tres líneas: 3 / 2 / h, con Claro y zoom 100 %.

Impacto: Dificulta leer y asociar una cifra con su concepto. No demuestra un error de cálculo.

Objetivo de mejora posterior: Reservar espacio al valor y permitir que la etiqueta se ajuste sin invadirlo.

Evidencia: [04-horas-avanzadas](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/04-horas-avanzadas.png).

### V02 — Etiqueta e interruptor sin separación en Informes (P2)

**DEFECTO VISIBLE.** «Incluir notas privadas de carpeta médica» llega hasta el interruptor de la derecha en la fila de inclusiones.

Impacto: La opción de privacidad pierde separación visual entre texto y control. No se ensayó el área táctil.

Objetivo de mejora posterior: Asignar anchos y separación estables; permitir varias líneas antes del control.

Evidencia: [23-informe](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/23-informe.png), [24-informe-inferior](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/24-informe-inferior.png).

### V03 — El calendario recorta horario y estado (P3)

**LÍMITE VISUAL OBSERVADO.** Se ven horarios «08:00–16…» y estados «Completa…» dentro de las celdas, aunque el mes completo entra en pantalla. El detalle muestra el horario completo.

Impacto: Puede exigir abrir el detalle para consultar información básica. La elipsis podría ser una decisión intencional.

Objetivo de mejora posterior: Comparar una síntesis mensual legible que conserve el mes completo y el acceso al detalle.

Evidencia: [09-calendario](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/09-calendario.png), [39-seleccion](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/39-seleccion.png).

### V04 — Las acciones activas grises se parecen a estados deshabilitados (P2)

**DIFICULTAD INFERIDA.** Acciones utilizables como Mostrar instituciones, Marcar como franco y Elegir copia usan texto gris y borde tenue. Guardar jornada/Guardar lugar/Crear copia local aparecen deshabilitados con otra variante gris.

Impacto: Hipótesis: cuesta anticipar qué se puede tocar. Las acciones activas se comprobaron por navegación o semántica enabled; no se midió contraste ni comprensión humana.

Objetivo de mejora posterior: Distinguir disponibilidad y jerarquía de acción mediante reglas visuales consistentes.

Evidencia: [18-carga](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/18-carga.png), [19-catalogo](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/19-catalogo.png), [20-institucion](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/20-institucion.png), [32-copias](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/32-copias.png).

### V05 — Varias superficies dan mucho peso a instrucciones y contenedores (P3)

**DIFICULTAD INFERIDA.** El menú ocupa dos tramos; un detalle de una jornada necesita desplazamiento. Clima y Ayuda muestran párrafos largos y tarjetas amplias.

Impacto: Hipótesis: compiten el contenido principal, la explicación y las acciones. Una captura no acredita lentitud ni confusión de una persona.

Objetivo de mejora posterior: Priorizar la información que se consulta a diario y dosificar la explicación sin perderla.

Evidencia: [10-menu-superior](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/10-menu-superior.png), [11-menu-inferior](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/11-menu-inferior.png), [13-detalle](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/13-detalle.png), [14-detalle-inferior](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/14-detalle-inferior.png), [30-clima](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/30-clima.png), [36-ayuda-seccion](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/36-ayuda-seccion.png).

### V06 — Fechas y nombres cambian entre recorridos (P3)

**INCONSISTENCIA OBSERVADA.** Conviven 2026-10-11, 16/10/2026 y fechas escritas; Tipos de horas extra pasa a Crear clase/Nueva clase; Crear institución o servicio abre Agregar otro lugar; Fotos del mes abre Fotos del cronograma.

Impacto: Hipótesis: resta previsibilidad al pasar de una entrada a su formulario. No se considera un error de fecha ni de dominio.

Objetivo de mejora posterior: Unificar vocabulario visible y presentación de fechas, preservando los conceptos laborales.

Evidencia: [05-meta](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/05-meta.png), [06-extras-lista](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/06-extras-lista.png), [07-extra-editor](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/07-extra-editor.png), [17-carpeta-medica](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/17-carpeta-medica.png), [20-institucion](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/20-institucion.png), [28-vacaciones-editor](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/28-vacaciones-editor.png), [38-fotos](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/38-fotos.png).

### V07 — Evaluar una jerarquía tipográfica más liviana (P3)

**PREFERENCIA ESTÉTICA A VALIDAR.** La muestra usa tipografía de apariencia monoespaciada, títulos pesados, violeta intenso y contenedores redondeados de forma amplia.

Impacto: Es una lectura estética del especialista, no un defecto demostrado ni una temática elegida.

Objetivo de mejora posterior: Comparar hasta tres propuestas con distinta jerarquía y ritmo visual sobre las mismas seis pantallas.

Evidencia: [13-detalle](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/13-detalle.png), [22-resumen](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/22-resumen.png), [35-ayuda](../../.local-evidence/stage6/20261011T143828Z-relevamiento-nuevo/originals/35-ayuda.png).

## Qué conservar

- Mes completo, marca de hoy y estado completado con texto/icono además del color: 09 y 39.
- Identidad, puesto y horario completos en el detalle: 13–14.
- Cifras alineadas y desgloses visibles del Resumen: 22.
- Inclusiones privadas apagadas y revisión previa del informe: 23–24; no se generó un archivo.
- Selección con contador, estados marcados y salida explícita: 39.
- Estados vacíos que explican qué falta y cómo seguir: 26, 27, 31 y 38.

## Cobertura representativa

Inventario nuevo construido desde el menú observado y las entradas vigentes de `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt` (DrawerAction) y `app/src/main/res/values/strings.xml`. La cobertura no hereda porcentajes ni estados del intento anterior.

| Superficie | Capturas | Límite concreto |
|---|---|---|
| Navegación | 10-menu-superior, 11-menu-inferior | Apoyar MiGuardia visible; enlace externo no abierto. |
| Calendario y selección | 09-calendario, 39-seleccion, 40-calendario-salida | Octubre de cinco filas; no matriz de meses, zooms ni dispositivos. |
| Detalle y acciones del día | 13-detalle, 14-detalle-inferior, 16-opciones-dia | Sin ausencia/cancelación guardada ni registro de horario real. |
| Carpeta médica | 17-carpeta-medica | Cancelado sin guardar; sin certificados ni datos clínicos. |
| Carga y catálogo | 18-carga, 19-catalogo, 20-institucion, 21-descartar-carga | No se guardó lugar ni jornada; sin combinaciones exhaustivas. |
| Mi forma de trabajar | 01-trabajo, 02-trabajo-avanzadas | Sector Enfermería existente; sin modificar configuración. |
| Horas y meta | 03-horas, 04-horas-avanzadas, 05-meta | Meta no configurada; formulario cancelado. |
| Tipos de horas extra | 06-extras-lista, 07-extra-editor | Sin alta ni extra automática. |
| Disponibilidad | 08-disponibilidad, 12-disponibilidad-editor, 12b-descarte | Sin ventanas activas ni cambios guardados. |
| Repetición | 25-repetir | Borrador descartado; sin generar jornadas ni explorar conflictos. |
| Feriados | 26-feriados | Sin selección/alta de feriado. |
| Vacaciones | 27-vacaciones, 28-vacaciones-editor | No se guardó período. |
| Notas | 15-notas | Sin editar, agregar imágenes o eliminar. |
| Fotos mensuales | 38-fotos | No se abrió selector personal; visor y fotos pobladas fuera de muestra. |
| Resumen | 22-resumen | Sin personalizar ni recorrer todos los desgloses. |
| Informes | 23-informe, 24-informe-inferior | Sin generar/exportar/compartir archivo. |
| Avisos | 29-notificaciones | Sin enviar prueba, permisos ni avisos reales. |
| Clima | 30-clima | Sin habilitar, ubicación, proveedor o descarga. |
| Widget | 31-widget | Sin launcher, instalación de widget ni IDs inventados. |
| Copias | 32-copias | Contraseñas vacías; sin SAF, copia ni restauración. |
| Bloqueo | 33-bloqueo | Sin activar ni autenticación del sistema. |
| Apariencia | 34-apariencia | Sin modificar ajustes; oscuro/150/200/paisaje no relevados en este corpus. |
| Ayuda | 35-ayuda, 36-ayuda-seccion | Sin recorrer todas las secciones ni tour completo. |
| Actualizaciones | 37-actualizaciones | Sin consulta remota, descarga o instalación. |

La muestra cubre 24 apartados, con formularios, estados vacíos, desplazamientos y confirmaciones que aportan composiciones distintas. No es QA integral ni aceptación de UX. No se ensayaron otros sectores, meses, dispositivos, oscuro, zoom 150/200 o paisaje. No se abrieron selectores personales, launcher, credenciales, enlaces de aporte, ni estados que exigieran activar servicios. Las confirmaciones sólo descartaron borradores abiertos por este relevamiento.

## Verificación y límites de evidencia

`python tools/docs_check.py`: OK (39.998/40.000 caracteres). `git diff --check`: OK.


- 41/41 PNG revisados visualmente por el especialista de esta sesión, incluida privacidad; 41/41 hashes y decodificaciones verificados. Revisiones en `reviews.json`; captura cruda y manifest final separados, sin reescribir el registro original.
- Verificación estática de enlaces, archivos y lógica local de filtros documentada en `atlas-validation.json`. No hay fuentes, librerías, peticiones ni recursos remotos.
- La política del navegador rechazó abrir `file:`. No se eludió con servidores o superficies alternativas. La vista previa renderizada del atlas en navegador queda PENDIENTE; se entrega como carpeta local preparada para apertura manual.
- Hubo detenciones del operador por etiquetas ausentes, confirmaciones no anticipadas y un popup de título genérico. Se volvió a observar antes de continuar; no son defectos de producto ni capturas válidas. `actions.jsonl` conserva sólo acciones realizadas. Un cotejo manual de hash falló por transcripción y se resolvió comparando automáticamente contra el sello de entrada.
- Los hashes instalados QA y público coinciden con la entrada. El paquete público sólo tuvo consulta de metadatos/hash; no se abrió ni se leyeron sus datos. No hubo instalación, rediseño, activación de servicios, generación/exportación, firma ni publicación.
- QA terminó en Calendario sin selección/borrador; Claro y 100 % conservados. Recibos: `device-entry.json`, `device-exit.json`, 34 y 40.
- Documentación y frontera revisadas en los recibos finales: `docs-check.txt`, `diff-check.txt`, `repository-exit.json`. No se ejecutó Gradle/JVM/instrumentación porque no hubo cambios de producto.

## Entrada para elección de temática

En la etapa posterior, comparar hasta tres propuestas sobre las mismas seis bases: Calendario 09, detalle 13, carga con catálogo 19, Resumen 22, Informes 23 y Ayuda 35. Joaquín elige antes de implementar. Evaluar lectura rápida, jerarquía, acciones activas/deshabilitadas, densidad, consistencia, privacidad y conservación del mes completo. V01 y V02 deben conservarse como ejemplos de regresión para la implementación posterior.

Preservar reglas laborales, datos/borradores, privacidad, accesibilidad, claro/oscuro y zoom 100/150/200. No convertir una elección estética en cambios silenciosos de navegación o dominio. Stage 5, revisión humana y distribución conservan sus puertas previas.
