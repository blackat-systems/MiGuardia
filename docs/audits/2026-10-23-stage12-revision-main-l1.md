# Stage 12 — revisión MAIN del checkpoint L1

Fecha: 23/10/2026. Chat `01a169e5-d914-7e33-8926-327a354301b3`.
Clave: `Stage12:L1:20261023-local0:80c5ca9cad98a2e6`.

## Decisión y alcance

L1 recibido y auditado como **base para construcción local**, no como matriz
Android ejecutable ni cierre integral. Se habilita continuar en el mismo Stage12
con B01/B02 y soporte QA mínimo del diseño revisado. Samsung permanece HOLD:
sin ADB, emulador, instalación ni prueba física. MAIN revisará delta y preflight
antes de pedir disponibilidad a Joaquín. Rige el [contrato](../prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md).

## Comprobación independiente

- Manifiesto `80c5ca9cad98a2e6cd33ba7117186e24fed9fc8f3cdd429d60aa49831fde4e92`:
  1200/1200 archivos y 47.495.585 bytes rehasheados, sin diferencias ni duplicados.
- 1039 rutas de entrada contrastadas; sin delta, Git idéntico, staging vacío,
  rama/base vigentes. Relevo de archivos, revisores y Gradle recibido.
- MAIN analizó los 107 XML: 981 pruebas, cero fallos, errores y omitidas. No
  repitió Gradle. Recibo host: 76; build con cinco APK/lint **reutilizados**.
- 441 IDs exactos y ordenados, 506 subcasos base, todos SIN_EJECUTAR. FULL/PARTIAL/GAP
  describe fuente candidata, no ejecución; cero rondas integrales.

## Hallazgos devueltos para la siguiente versión

1. C001: receta 2026-02/08/10 y 2028-02 tiene 5/6/5/5 filas; agregar cuatro filas
   reales, por ejemplo 2021-02 ya usado en el mapa, y oráculo por subcaso.
2. C090: FULL sobredeclara la conjunción. Compose sólo muestra controles de dos
   filas iguales; Activity cambia puesto/plantilla antes de eliminar y coordinador
   usa otra plantilla. Falta igualdad visual + toque UUID + confirmar + readback
   en un caso aislado. Es una brecha del mapa, no defecto demostrado del producto.
3. `required_level` genérico AND_OR no decide el nivel. Antes de cada familia,
   fijar niveles obligatorios por aserción, fixture, pasos y selector concretos;
   resolver brechas sin convertir una clase verde en aprobación de una fila.
   W010 exige RemoteViews real y entrada adversa con secretos, no sólo UI pura.

Muestra semántica independiente: 18 IDs, enumerados en el recibo MAIN. W023 usa
un PendingIntent fijo productivo y no puede ejecutarse intacto; C058/V016 incluyen
captura global prohibida. X015 requiere también preferencias/archivos, no sólo
tablas. V013 no habilita TalkBack: la escucha humana mantiene su puerta separada.

Preservar el payload L1; corregir en versión nueva. El diseño de aislamiento ya
incluye Context de ambas Activities, barridos y efectos secundarios de servicios,
callbacks de Copias, scheduler compartido y exclusión de PNG/XML ajenos. Son
requisitos pendientes, no guardas ejecutadas. Hooks en `src/main` son soporte
productivo de prueba: defaults intactos y ámbito inaccesible fuera de runner QA
validado. No aceptar quiet como entrega, reconciliación o éxito de restauración.

Evidencia MAIN: `.local-evidence/stage12/main/l1-received.json` y `l1-review.json`.
Entrada: `.local-evidence/stage12/20261023-local0/`. No cambio de producto por MAIN,
ni teléfono, datos personales, app pública, staging, commit, firma o publicación.
