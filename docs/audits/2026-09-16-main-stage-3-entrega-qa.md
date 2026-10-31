# MAIN — Ensayo y entrega instalada de Stage 3

Fecha: 2026-09-16.

Joaquín autorizó ejecutar pruebas y dejar la app instalada para verificar.
Se usó exclusivamente el candidato QA en Samsung SM-S938B/API 36.
Base: `932eaeb60c6b6e68975534788e3e6501c967b1ae`, rama
`codex/miguardia-3.0`, checkout acumulado preservado.

## Instalación y frescura

Se compararon todos los inputs del último build completo con el árbol actual.
La única diferencia fue la prueba de recreación corregida, cuyo runner ya tiene
dos recibos oficiales verdes. Se reinstalaron con conservación de datos la APK
QA y su runner. Ambas instalaciones devolvieron Success.

- APK QA: `08246b45605e009224c6af5a0b9d2fbef0d91515b4230b479548409ee435620b`.
- Runner: `5e678c8ed7d7683fee5111168640ca8e87dfbf1a82a860cf6c863787c72a4e8e`.

No se instaló, instrumentó ni consultó contenido del paquete público.

## Pruebas nuevas de MAIN

- Adaptive: 4/4, `.local-evidence/qa/20260916T043229.779714Z`.
- Common: 11/11, `.local-evidence/qa/20260916T043252.880451Z`.
- Carga manual Activity: 6/6, `.local-evidence/qa/20260916T043305.245828Z`.
- Total: 21/21, sin métodos pendientes; runner oficial con verificación de APK.

Recorrido adicional por entrada táctil en QA: toque breve sobre el día 16 abre
el detalle con fecha humana, tarjeta del objetivo sintético, Notas, Avisos y
clima deshabilitado. Atrás vuelve al Calendario. Pulsación de 650 ms activa una
selección con contador, Continuar y Cancelar; Atrás la abandona.
Capturas y jerarquías locales: `.local-evidence/qa/main-20260916-review/`.
Se retiraron los dos archivos temporales creados en el teléfono.

## Hallazgo visual abierto

En la ventana actual del Samsung se fuerza una sola semana, desaparece el acceso
a mes completo y queda un amplio espacio vertical vacío. Confirmado en la
captura `calendar.png`; no es únicamente un riesgo hipotético del umbral de
1050 dp. Requiere corrección/revisión del diseño adaptativo antes de aceptar
Stage 3. Los tests verdes de adaptación no acreditan que esa experiencia sea
adecuada.

No se modificó producto en esta sesión. No se completó observación TalkBack ni
una nueva matriz visual completa de temas/zoom/orientación. Se conserva la
evidencia automatizada previa y se distingue del recorrido táctil nuevo.

## Entrega

Se dejó instalada y en primer plano `com.blackatsystems.miguardia.qa`, en el
Calendario, con el ejemplo ficticio generado por la prueba de carga manual.
El usuario puede revisar el candidato; no representa aceptación ni distribución.

