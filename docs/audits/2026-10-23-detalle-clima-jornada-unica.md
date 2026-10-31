# Detalle, Clima y jornada única — revisión MAIN

## Entrada y alcance

Pedido directo de Joaquín del 23/10: detalle que desaparece al tocar hoy/fechas
próximas, Clima y expansión redundante de una única jornada en curso. Contrato:
`docs/prompts/CORRECCION_DETALLE_CLIMA_Y_JORNADA_UNICA_3_0.md`.
Repositorio independiente MiGuardia-3.0, rama codex/miguardia-3.0, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`; checkout/staging heredados preservados.
No se reabrieron las campañas de Stages8/10/11.

Samsung SM-S938B/API36, serial explícito [serial QA en binding local privado]. Exclusivamente
com.blackatsystems.miguardia.qa y su ejecutor normal: guardas de foco/desbloqueo,
hashes, firma técnica y procedencia sintética. Sin abrir/consultar la app pública,
datos personales, galería o ubicación; sin clear, desinstalación ni resemilla.
La preferencia Clima recibida ya estaba habilitada por Joaquín: se conservó.

## Causa, cambios y límites

1. **Ventana del detalle:** reproducción con toque real sobre QA494a. Se
   conservaba la fecha seleccionada pero la ventana sólo exponía «Cerrar hoja»;
   su contenido quedaba oculto. No era una limpieza de la fecha por Room/Clima.
   El SheetState vivía fuera de la condición de apertura y se reutilizaba tras
   cerrar. Ahora se crea dentro de esa condición: cada apertura posee sus
   anclajes/animación. Clima no desmonta esa condición, conservando su retorno.
   No se registró cada anclaje interno de Material3: no atribuir una transición
   interna más específica que la demostrada por el antes/después.
2. **Jornada única:** canExpand equivale a lista no vacía; la tarjeta ofrecía
   expandir la misma jornada principal. El cambio visual compara UUID de la
   única fila con la principal y omite sólo esa expansión redundante. Varias
   jornadas y resúmenes históricos conservan el detalle expandible.
3. **Clima:** acceso/retorno y explicación de estados comprobados. El objetivo
   ficticio no tiene ubicación guardada; el diálogo lo explica. Activarlo no
   inventa coordenadas ni autoriza obtener ubicación. No se acredita una
   descarga meteorológica real ni se diagnostica un fallo del proveedor.

Delta productivo propio: únicamente el ciclo de vida de la hoja en
`app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt` y la condición
de expansión en `app/src/main/java/com/blackatsystems/miguardia/ui/nextevent/NextEventCard.kt`.
Sin cambios de dominio, Room, Copias, permisos, dependencias, versión o firma de distribución.

Regresiones/catálogo: CalendarCommonV2ComposeTest, NextEventComposeTest,
FinalQaReadOnlyReviewTest y tools/qa-groups.json. En el último, el método nativo
no aplica el arnés Compose; el método Resumen conserva su regla original. La
prueba exige opt-in, APK exacta y fixture recibida; compara negocio/preferencias
antes/después, sin prepararlas o restaurarlas. Evidencia nueva y helpers:
`.local-evidence/day-detail-weather-20261023/`.

## Adversos conservados y precisión de pruebas

- Las pruebas Compose, incluso con toques y reloj controlado, pasaron sobre el
  APK defectuoso: no demostraban el recorrido humano. La regresión sin arnés
  Compose, con UiDevice y animación Android real, falló en la segunda apertura
  con la fecha todavía seleccionada. Recibo
  `.local-evidence/qa/20261023T160434.745046Z`; detalle
  `live-497148a9-ff86-4864-b1de-d9d6a8813060.json` bajo la evidencia nueva.
- Candidata intermedia8f9: regresión nativa pasó cuatro aperturas/retornos,
  negocio y preferencias iguales; recibo `20261023T160656.778451Z`.
  CalendarCommon15/15, Weather12/12 y Resumen1/1:
  `20261023T161005.162478Z`, `20261023T161053.236478Z`,
  `20261023T161138.136626Z`. Son evidencia intermedia, no del APK final por inferencia.
- NextEvent dio27/28 en `20261023T160755.337281Z`. La hipótesis inicial de viewport
  del especialista fue **incorrecta**. La aserción explícita posterior reveló
  NO_WORK_TODAY, no ONGOING_SHIFT (`20261023T162551.752106Z`). El motor excluye
  registros con horario real/protecciones de los eventos principales; la nueva
  fixture era inválida para esa expectativa. Se retiraron ese cuarto método y
  el texto productivo innecesario que había motivado. Quedan tres regresiones
  nuevas válidas; la cobertura histórica real/protecciones/privacidad existente
  se conserva. No se alteró dominio para fabricar un verde.
- Primera instalación del ejecutor detenida antes de mutar por Java ausente del
  PATH de apksigner; se usó el JBR explícito y se verificó la firma técnica.
  Un rechazo de catálogo por hash CRLF se resolvió con su normalización UTF-8/LF.
- El primer perfil local ejecutó Gradle satisfactoriamente pero no pudo archivar
  todos los XML en la ruta larga. Se conservó el recibo fallido; rutas cortas
  documentadas `.local-evidence/bd-l2/` y `bd-b1/` dieron verde intermedio.
  No se modificó el verificador para ocultarlo.

## Cierre final

Implementación mínima y entrega física final comprobadas. Perfil local fresco
`.local-evidence/bd-l3/result.json`: 981/981
(395 dominio,19 database,567 app), cero fallos/errores/omitidas/reutilizadas e
inputs estables. Perfil build `.local-evidence/bd-b2/result.json`: verde,
lint sin errores (25 warnings), Debug/QA/Release unsigned y ambos ejecutores;
inputs estables. QA final estaba UP-TO-DATE; el ejecutor fue generado. No atribuir
una generación nueva a un artefacto reutilizado correctamente.

Identidad de candidata mínima local:

- QA: `682e529e79eb398b31c757e51d8d3500e165a8d90293cb9aea07875a6bed0f2e`.
- Ejecutor normal: `6018b6009aa8b6852a94df6318adc8e3b535ae6334dd805baf397a08cc1a2dbb`.
- El único cambio después de8f9 fue retirar texto inalcanzable y la prueba
  inválida; no se modificó la corrección del ciclo de vida de la hoja.
- `selection-minimal.json` fija rutas/hashes y admite como entrada sólo la
  intermedia8f9 o esa misma mínima, con instalación conservadora -r.

El intento de retomar a las16:38 UTC se detuvo en DEVICE_LOCKED **antes** de
abrir/instalar/instrumentar. Recibo
`.local-evidence/day-detail-weather-20261023/open-20261023T163804.425939Z.json`.
Joaquín confirmó después «Sí, queda desbloqueado en QA o Inicio». Se revalidaron
serial, foco, desbloqueo y huellas sintéticas; instalación conservadora -r a las
16:44 UTC, sólo QA682e/ejecutor6018. Hashes locales/instalados coinciden; ambos
con certificado técnico b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617.

**Última candidata, 30/30 Android**, sin fallos, omitidos o pendientes:

- Nativo sin arnés Compose: 1/1, `20261023T164427.457753Z`.
- NextEvent completo: 27/27, `20261023T164455.674432Z`.
- Identidad independiente/retorno de Clima: 1/1, `20261023T164615.761957Z`.
- Explicación de ubicación faltante: 1/1, `20261023T164623.937093Z`.

Son carpetas bajo `.local-evidence/qa/`. El recibo nativo transportado con SHA
`43c86309e208aab3c6391e4794a25d0ea0dd9472be3e038268b368a2c34b0a46`,
`live-694e60f1-0275-4dc7-b59e-f8d26cf5151d.json`, registra cuatro aperturas
23/25/23/26, fecha correcta y negocio/preferencias iguales antes/después.
MAIN y revisor independiente contrastaron los recibos, métodos e identidades.
El revisor también rehasheó 603 insumos y 107 XML de las 981 JVM. La salida
instrumentada cruda se descarta por privacidad: su hash no puede recalcularse
desde un log inexistente; sí se contrastaron JSON, APK, catálogo y fuentes.

Comprobación visual adicional con toque nativo: calendario y jornada única sin
botón redundante, hoy abierto, Clima sin ubicación, retorno al mismo día y
domingo25 abierto. PNG revisados `capture-20261023T164754.683645Z.png`,
`164759.913833Z`, `164827.129932Z`, `164857.429568Z` y `164904.361730Z`, con
el mismo prefijo/extensión bajo la evidencia nueva. Captura final en Calendario:
`capture-20261023T164925.017131Z.png`; sin selección, borrador o diálogo abiertos.

El estado final `status-20261023T164927.338548Z.json` conserva las 11 huellas
controladas respecto de `status-20261023T164356.434032Z.json`, excepto
`files/profileInstalled`, marcador técnico que cambió tras instalar. Una primera
comparación demasiado estricta lo rechazó; la comparación de negocio/preferencias
excluyó sólo ese marcador y no encontró diferencias. Frente a la base histórica
A2, Clima habilitado y tema DARK ya eran cambios recibidos, no de esta prueba.
No se limpiaron ni resembraron datos. No hubo nuevas operaciones sobre la pública.

Relevo MAIN: corrección CERRADA con QA final instalada y abierta para Joaquín.
Recibo agregado `.local-evidence/day-detail-weather-20261023/final-review.json`.
No repetir perfiles/borrados/campañas por este cierre documental.

La aceptación humana, el pronóstico remoto con ubicación configurada, OEM,
la causa histórica de Resumen/R4 y distribución son puertas separadas.
No commit, push, tag, publicación ni aceptación de Joaquín por inferencia.
