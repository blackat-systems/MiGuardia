# Stage 7 — checkpoint de letra Android y QA

Fecha local: 2026-10-11, al cerrar la sesión. Joaquín pidió: «frená, hace un
checkpoint, seguimos mañana». No continuar hasta su reanudación. No hay tareas
programadas ni autorización para trabajar en segundo plano durante la pausa.

## Estado y frontera

Repositorio independiente `MiGuardia-3.0`, rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae`; staging vacío. Checkout acumulado
preservado, sin commit, push, tag, firma de distribución ni publicación.
Entrada de esta unidad: 972 rutas en
`.local-evidence/stage7/android-font-20261011/entry/repository.json`.
Salida final: `exit-checkpoint/repository.json`, bajo esa misma carpeta; sus
originales y hashes permiten atribuir el delta sin reconstruir desde Git limpio.
`exit/` preserva el sello anterior a corregir el estado documental. STATUS usa
el estado técnico admitido EN_CURSO porque la unidad no está cerrada; su cursor
declara la pausa humana, no ejecución en segundo plano. El primer docs_check del
checkpoint rechazó un estado no catalogado; se corrigió sin modificar la herramienta.

Autoridad vigente al reanudar: quitar zoom interno, heredar tamaño de letra Android,
mantener TalkBack sin controles extra, preservar Copias; continuar Stage 7 sólo
en QA sintética. Pública y datos personales excluidos. No mutar ajustes globales
de tamaño de letra, pantalla o densidad para probar la aplicación.
ADR 0043 sustituye explícitamente esa parte de ADR 0015/0042. Oscuro Vigilia por
defecto se conserva, sin pisar elecciones claras/oscuras/Sistema ya guardadas.

## Implementación conservada

- Tema hereda `LocalDensity` y conversión SP nativa, incluida conversión no lineal;
  no quedan reemplazos de densidad ni lectores visuales de zoom legado.
- Apariencia sólo ofrece temas; Activity, Calendario, Ayuda y Widget dejan de
  aplicar zoom interno. TalkBack conserva semántica y acciones, sin controles propios.
- Clave y enum legados se conservan para las 17 preferencias portables. Gateway y
  BackupViewModel son byte a byte iguales a la entrada; no cambian Room, formato,
  versiones, cifrado ni validación de Copias.
- Regresiones migradas a contextos Android de prueba con letra 1/1,5/2; no son
  ajustes globales ni un selector de zoom del producto. Matriz conserva 48 casos.
- Tres textos usan `AnnotatedString` para mantener dibujo y layout accesible por
  la misma ruta: valor de Horas, valor de Resumen y etiqueta de StatusBadge.
- Alto del Calendario se conserva en píxeles y deriva dp de la densidad vigente.
  El driver de rendimiento registra letra/densidad reales de la Activity para no
  atribuir una comparación entre condiciones distintas a la temática.

## Verificación fresca y atribución

Todos los paths de esta sección parten de
`.local-evidence/stage7/android-font-20261011/`, salvo los recibos QA indicados.

| Evidencia | Resultado y límite |
|---|---|
| `validation/local/result.json` | 932/932 JVM, primera revisión nativa; inputs estables |
| `validation/build/result.json` | Lint falló por dos lecturas de Configuration en una prueba; evidencia preservada |
| `validation/build-repair/result.json` | Build completo verde tras usar LocalConfiguration; artefactos intermedios |
| `validation/local-final/result.json` | 932/932 JVM frescos, cero fallos/errores/omitidas; incluye corrección final, inputs estables |
| `validation/build-final/` | INTERRUMPIDO por petición humana durante lint AndroidTest; no acredita build final |
| `.local-evidence/qa/20261012T024401.760719Z` | Apariencia 3/3, candidata intermedia |
| `.local-evidence/qa/20261012T024414.762666Z` | Preferencias portables 4/4: round-trip 100/150/200 con 17 claves y rechazo de 14 inválidos sin escritura |
| `.local-evidence/qa/20261012T024428.924495Z` | Diagnóstico de layout 1/1: seis casos, tres variantes y 18 PNG, candidata intermedia |

932 JVM finales = 395 dominio + 19 base + 518 app. No se reasignan a este APK los
124/130 métodos o la matriz 48/48 de la campaña anterior.
Catálogo revisado válido: 121 clases y 28 grupos. Último docs_check previo al
checkpoint: 39.970/40.000 caracteres guía, 42 ADR, 103 snapshots; se vuelve a
validar únicamente la documentación del checkpoint al cerrar.

### Diagnóstico adverso resuelto causalmente, confirmación final pendiente

Los tres avisos `hasVisualOverflow` anteriores eran inconsistencias del layout
reconstruido para semántica en la ruta optimizada String de Compose. Bytecode
local exacto y seis casos físicos muestran párrafo semántico más ancho, mientras
el dibujo conserva tamaño y todos los píxeles de las variantes completas.
Evidencia: `diagnostics/text-layout-bytecode/` y
`diagnostics/text-layout-physical/` con report, 18 PNG y manifiesto de hashes.

Se aplicó la corrección mínima a tres componentes, sin quitar aserciones,
cambiar geometría ni bajar fuentes. El diagnóstico ahora agrega cuarta variante
AnnotatedString sin callback (24 PNG esperados). Esa versión del diagnóstico,
los tres casos originales y el conjunto físico final TODAVÍA NO se ejecutaron.

Dos intentos preparatorios de matriz no ejecutaron instrumentación: primero un
nombre de clase equivocado, luego catálogo desactualizado tras editar el driver.
Clase real: `Stage4CalendarMatrixTest`; catálogo ya actualizado antes del build final.
No son fallos de comportamiento ni corridas de matriz.

## Samsung y artefactos

Última instalación verificada: Samsung SM-S938B/API 36, exclusivamente QA.

- QA: `39f4eac2202ada92f94f5b29b830693d813d94d78855a6197f24368c9fca45ba`.
- Runner: `367bba92d50d461ec7f2b32a89811dc04a6b4cca03199d7885946c8426bbbc96`.
- Pública sin cambios en las comprobaciones efectivas:
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
- Manifest intermedio: `artifacts/manifest.json`; instalación:
  `install-20261012T024353.973852Z.json`. No se instaló la corrección final.

Al cerrar, la consulta de foco del dispositivo no pudo completarse; no se afirma
hash/foco actual nuevo y no se insistió ni se accedió a otra pantalla. Reconfirmar
conexión, disponibilidad y hashes al retomar. La compilación y los especialistas
fueron detenidos; no quedó una prueba de dispositivo en ejecución.

TalkBack NO se activó en esta continuación. Joaquín había confirmado que podía
escucharlo; esa disponibilidad temporal se reconfirma mañana. Avisar justo antes
del ensayo audible y pedir después si se entendieron los tres controles.

## Reanudación ordenada

1. Leer STATUS, este checkpoint, ADR 0043 y auditoría Stage 7 MAIN. Contrastar
   checkout con sello de salida; preservar cualquier delta adicional y atribuirlo.
2. Completar build con JBR explícito y un NUEVO directorio de evidencia, por
   ejemplo `validation/build-resume-20261012`. No sobrescribir `build-final`:
   su result.json quedó en `preflight` por interrupción del proceso supervisor;
   el registro separado `checkpoint-interruption.json` acredita la pausa, no éxito.
   Perfil: `python tools/verify.py --profile build --output <directorio nuevo>`.
   Repetir JVM sólo si cambia código después del sello; no por rutina.
3. `qa_final.py` está preparado, NO ejecutado. Antes de preparar artefactos,
   apuntarlo al nuevo recibo exitoso (actualmente espera `build-final/result.json`).
   Crear `artifacts-final/manifest.json` y `expected-driver.json` sin sobrescribir
   el manifest intermedio. Instalar sólo QA y runner con `-r`, guardas y hashes.
4. Ejecutar diagnóstico de cuatro variantes y regresiones originales Horas,
   Resumen y detalle. Luego Apariencia/Copias y matriz mensual de 48 combinaciones
   con fontScale Android local simulado, meses de cuatro/cinco/seis filas y temas.
   Recorridos afectados: carga/recreación/Atrás, Ayuda, próximo evento, Widget,
   Informes y consumidores críticos restantes; cada recibo atribuido al APK real.
5. Revisar `talkback_final.py` (adaptación estrecha del supervisor OEM ya revisado),
   anunciar ensayo audible y ejecutar caso exacto. Restaurar servicios/ajustes
   anteriores, conservar resultado técnico separado del reporte humano.
6. `performance_native.py` quedó como BORRADOR interrumpido: revisar antes de usar.
   Contrato previsto: manifest final y expected-driver con class/hash; baseline
   Stage 5 preservada `6ae70d…5b1b3`, mismo runner, 12 iteraciones × 9 operaciones,
   series representative/stress, semilla/ventana/letra/densidad comparables.
   Candidata exige índice baseline explícito. No sobrescribir evidencia previa;
   volver a APK final al cerrar. Esto no mide contenido frío ni certifica FPS.
7. Capturas reales nuevas con `ui_final.py`, seis bases y familias pertinentes;
   comparar con corpus Stage 6 NUEVO. Completar pendientes OEM/privacidad y
   rendimiento proporcional, distinguir aceptación humana y distribución.

No iniciar Stage 8, commit, push, firma, AAB, publicación o app pública por esta
reanudación. Helpers finales pendientes y resultados intermedios no habilitan
declarar Stage 7 cerrado. La auditoría general conserva íntegra la historia.
