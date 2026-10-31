# Cierre dirigido en Samsung — candidata 3.0

Fecha: 23/10/2026. **Pasada física dirigida completada; aceptación humana y
distribución separadas.** No es una certificación integral de todas las funciones.

## Autoridad, entrada y candidata

Joaquín autorizó directamente instalar la candidata actual y revisar pendientes,
incluido Resumen, exclusivamente QA y datos ficticios. MAIN fue el único operador
del Samsung y Gradle; revisor interno escribió sólo dos pruebas asignadas y luego
auditó en lectura. No se abrió otro chat de implementación ni se reactivó supervisor.

Repositorio/rama/base: MiGuardia-3.0 / codex/miguardia-3.0 /
932eaeb60c6b6e68975534788e3e6501c967b1ae. Entrada sucia y eliminación heredada
preservadas, staging vacío. MAIN contrastó las 1.030 rutas del sello de salida
Stage11: seis rutas existentes modificadas, ninguna perdida; nuevas rutas propias
son prueba, contrato, esta auditoría y evidencia ignorada. Los 320 insumos
productivos/Gradle seleccionados por MAIN coinciden íntegramente con Stage11.

Samsung SM-S938B/API36, serial [serial QA en binding local privado], 1440×3120, densidad 600, letra Android
observada 0,9. No se cambió la configuración global del teléfono.

- QA instalada inicialmente: 2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3.
- Candidata instalada con `-r`: 494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a.
- Ejecutor normal final: e41192753f12e9e2cfe15c54aa8f14caff2845ca0a555e44c2d31100529fc34a.
- Certificado técnico verificado: b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617.

El ejecutor Stage11 de emulador NO se usó: exige x86_64/qemu y Application neutra.
Se generó AndroidJUnitRunner normal y se verificaron manifiesto estructural,
targetPackage QA, firma, identidad y hashes locales/instalados antes de ejecutar.
Cuatro revisiones del ejecutor acompañaron preparación/correcciones de pruebas;
hubo una sola instalación del producto QA. No clear, desinstalación ni reseed.

## Procedencia y conservación

Antes de abrir UI se compararon sólo hashes de rutas QA conocidas con A2 y la
verificación sintética de Stage8. Once huellas coincidentes, incluida DB/WAL;
no hizo falta leer datos desconocidos, borrar ni sustituir la fixture.

Dos recorridos completos de Resumen y el adverso parcial comprobaron snapshot
lógico antes/después: 29 tablas, 1.956 jornadas, SHA
04c7c92a5f63219cf76e2274ac096dea79b069cb12d6b1c57de1b9a8834f92c9.
Cinco archivos de preferencias iguales dentro de cada recorrido. Entre claro y
oscuro sólo cambió la preferencia visual por el botón QA «Usar modo oscuro».

Al terminar, las huellas DB/WAL, cuatro DataStore, marcador y preferencias de
avisos recibidas siguen iguales a A2. Sólo difieren `miguardia_display_preferences.xml`
(tema oscuro elegido conscientemente en QA) y `profileInstalled` técnico tras
instalar. No se borra ni se sustituye la entrada para fabricar igualdad.

Evidencia nueva: `.local-evidence/final-samsung-20261023/`. Cada acción conserva
comandos/resultados y guardas; las capturas XML nuevas usan UUID y filtran nodos
de Sistema antes de persistir. No se operó ni consultó el paquete público,
galería personal, documentos personales, redes, permisos o seguridad global.

## Instrumentación realmente ejecutada

**12 métodos distintos con último resultado pasado.** Ocho invocaciones suman
15 ejecuciones de métodos: 13 pasadas y dos adversas, cero omitidas/pendientes.
No se presenta como 15/15 verde ni como una batería íntegra en el último runner.

| Recibo bajo `.local-evidence/qa/` | Resultado y alcance |
|---|---|
| 20261023T151629.711926Z | Adverso 0/1: booleano de envío Atrás de la prueba; runner83d |
| 20261023T151939.361368Z | Resumen claro 1/1, seis aperturas, detalle/recreación/conservación; runner0a5 |
| 20261023T152018.601951Z | Adaptación 6/7; adverso exigía visibilidad sin scroll del menú; runner0a5 |
| 20261023T152110.729318Z | Resumen: cifras/etiquetas completas en ambos temas y tamaños simulados, 1/1 |
| 20261023T152129.617627Z | Informes: etiqueta médica y único interruptor separados, 1/1 |
| 20261023T152137.370465Z | Vigilia: roles/contraste de paletas claro/oscuro, 2/2 |
| 20261023T152706.684969Z | Único método adaptativo corregido, 1/1; runner final e411 |
| 20261023T152715.312342Z | Resumen oscuro 1/1, seis aperturas y conservación; runner final e411 |

Los seis métodos adaptativos no modificados se reutilizan sobre el mismo
producto; se repitió la comprobación afectada, no toda la batería por rutina.
Los casos cubrieron mes completo, preferencia antigua de plegado, ventanas
bajas, seis filas, paisaje/franja lateral, letra grande y acceso a avisos.
Los tamaños simulados se aplican al contexto de prueba, no a Android global.

Adversos y correcciones exclusivamente test-only:

1. `FinalQaReadOnlyReviewTest`: `UiDevice.pressBack()` devolvió false después de
   completar detalle/recreación. Se usa el envío Espresso estándar y se mantienen
   Calendario visible, idle y foco como postcondiciones. La repetición pasó.
2. `CalendarAdaptiveLayoutComposeTest`: exigía tres acciones simultáneamente
   visibles sin desplazar el menú. ADR0015 prescribe panel desplazable. Ahora
   cada acción hace scroll real y se verifica; al cerrar se vuelve a comprobar
   mes completo y ausencia de barra/scroll mensual. Antes falló, después pasó.

Dos intentos iniciales de importar el supervisor no instrumentaron: ruta ADB
histórica ausente y adaptador constructor sin atributo required. Se corrigió
la adaptación local, sin cambiar guardas o ejecutar contra otro dispositivo.
Los controles de UI que no coincidieron con el estado actual se rechazaron sin
toques: diálogo de descarte y confirmación de fechas en la carga múltiple.

## Resumen: observación, no atribución causal

Seis aperturas por tema, misma Activity y fixture, caché permitido. Reloj
System.nanoTime antes del menú hasta CONTENT del mes correcto y Compose idle;
incluye sincronización y guardas. Un reinicio de Activity para recreación queda
fuera de la muestra correspondiente, pero influye en las reaperturas posteriores.

| Tema | n | Mínimo ms | Mediana ms | Máximo ms |
|---|---:|---:|---:|---:|
| Claro | 6 | 164,410 | 185,873 | 2198,504 |
| Oscuro | 6 | 185,980 | 336,068 | 2266,222 |

Doce resultados CONTENT; detalle, misma métrica tras recreación y regreso al
Calendario comprobados. Primera apertura medida ≈2,2 s; no ocultarla mediante
mediana ni prometer apertura instantánea. Esto NO es ABBA, arranque frío, FPS,
umbral de aceptación ni causa histórica de R4. La alerta Stage8 queda inconclusa.

Tres JSON se extrajeron binariamente por rutas UUID propias y se rehashearon:
`summary-801a1461-d2b0-4198-818f-4023d61f373f.json` (adverso parcial),
`summary-57e7be96-1cbb-41d2-b47f-75b2a011e5d3.json` (claro),
`summary-d0a9d95e-e328-4b44-b6f9-24cc786aeeda.json` (oscuro).
Revisor independiente recalculó muestras/conteos y contrastó protocolos/hashes.

## Recorridos y revisión visual de MAIN

Toque breve → detalle → Editar día → catálogo/horarios → cancelación con
confirmación; pulsación de dos segundos → selección múltiple → cuatro fechas →
formulario con tres fechas iniciales → Mostrar todos, lista vertical ordenada
con días de semana → cancelar sin guardar. No se activó Guardar/Franco/Limpiar.

Se revisaron capturas de Calendario, detalle, carga/catálogo, fechas expandidas,
Resumen, Informes, Apariencia y Ayuda. En la hoja oscura no se observa el bloque
blanco inferior denunciado; el área de navegación pertenece al sistema y es
coherente con el tema. No es aprobación estética de Joaquín ni todas las ventanas.

Clima desactivado abre explicación/configuración sin activarlo ni pedir ubicación;
Atrás vuelve al mismo detalle, XML filtrado idéntico antes/después. Informes
conserva las inclusiones privadas apagadas; no se generó ni exportó un archivo.
Ayuda abre y vuelve al Calendario. QA quedó visible en Calendario/oscuro, sin
selección ni borrador. Captura final: `capture-20261023T153243.539815Z.png`.

## Delta, comprobaciones y puertas

Nuevo `FinalQaReadOnlyReviewTest.kt`, arreglo acotado del método adaptativo,
catálogo/opt-in exacto en `tools/qa.py` y regresión host de permisos/selección.
Cuatro pruebas host distintas ejecutadas en dos conjuntos de tres, todas verdes;
catálogo 125 clases/32 grupos. AndroidTest ensamblado tras cada delta test-only;
producto permanece idéntico. No se repitió JVM/build/lint global de Stage11.

Contrato/STATUS/índice y esta revisión son el delta documental de MAIN. Sin
fuentes productivas, Room, Copias, cifrado, versión, dependencias, firma de
distribución, staging, commits o publicación. Helpers y ejecutor final preservados
en evidencia nueva; nada de Stage8/10/11 se sobrescribe o borra.

Quedan la aprobación visual/funcional de Joaquín y decidir si acepta la latencia
observada de Resumen o pide una investigación nueva acotada. No se repitieron
selector OEM/SAF, biometría, entrega real de alarmas ni audición TalkBack, ni se
demostró upgrade público. No se concedieron permisos o controles humanos por él.
Esas comprobaciones y preparar versión/firma/distribución conservan sus puertas.
