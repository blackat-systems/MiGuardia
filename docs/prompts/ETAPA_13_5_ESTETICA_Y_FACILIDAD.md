# Etapa 13.5 · Estética y facilidad de uso — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 13.5 · Estética y facilidad de uso**. La agregué el 8/11/2026
entre la 13 (Datos y archivos, cerrada; commit `636e4ae` en `origin/codex/miguardia-3.0`) y la 14
(Sistema y aceptación final). Va antes de la 14 para que la pasada completa final cubra la interfaz nueva.

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md`, `docs/PROCESO_SIMPLE.md` y `docs/LECTURAS_POR_TAREA.md` (fila
   «Calendario/navegación/letra»: Planificación §12, ADR 0003/0015/0032/0042 y **0043 letra nativa**).
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. Si algo no
   coincide con STATUS, frená esa parte y avisame.
3. Leé la fila 13.5 de `docs/PLAN_MAESTRO_CIERRE_3_0.md`, la referencia visual elegida
   `docs/design/vigilia-referencia-elegida.png` («Vigilia R4» en el glosario), los componentes comunes de
   `app/src/main/java/com/blackatsystems/miguardia/ui/components/` (en especial `VisualSystem.kt` y
   `AutomaticTimeField.kt`) y el Anexo C de
   `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md` (criterios visuales V001–V016).
4. Usá la skill de revisión de rediseños para no perder funciones, estados, datos, navegación ni
   accesibilidad (matriz de pantallas y capturas antes/después).

**Qué se quiere** (decidido por mí el 8/11)
- **Pulir Vigilia R4, no rediseñar.** El mismo diseño, parejo en toda la app: espacios, jerarquía de
  títulos, botones, tarjetas, colores en tema claro y oscuro, y textos completos sin cortes. Sin
  funciones, estados ni pantallas nuevas o perdidas.
- **Elegir horarios y fechas sin escribirlos.** Selector nativo de Android (Material 3 de Compose:
  reloj o teclado numérico para horas, calendario para fechas) a un toque, **sin quitar** la escritura
  a mano (`0830`, fechas) para quien la prefiera. Hoy se escribe en: `AutomaticTimeField` (Disponibilidad,
  Horas y extras, Horario real, Mi forma de trabajar), los campos de hora del detalle del día y del
  Calendario, las fechas «AAAA-MM-DD» de Disponibilidad, «DD/MM/…» del detalle del día y la lista de
  fechas de Feriados. Confirmá el inventario en el código antes de cambiar.
- Mismas reglas de producto: minutos exactos, cruce de medianoche, fecha local explícita, revisión antes
  de guardar. El selector nunca guarda solo ni cambia el valor sin que la persona confirme.

**Cómo se prueba**
- Primero: inventario por pantalla de qué se pule y dónde entra el selector, con capturas del estado
  actual. Proponeme la lista y un ejemplo de cómo queda (una pantalla) antes de aplicarlo a todo.
- PC: `python tools/verify.py --profile local` (última corrida 1339 pruebas, 0 fallas); pruebas Compose de
  cada pantalla tocada (que siga todo lo que estaba, que el selector y la escritura den el mismo valor,
  cancelar el selector no cambia nada, horas límite 00:00/23:59 y cruce de medianoche).
- Samsung: sólo con mi autorización en el momento. Pareja instalada y script en STATUS. Capturas en tema
  claro y oscuro y con letra grande de Android; lo que necesite mi ojo o mi dedo (mirar si queda bien,
  usar el selector, TalkBack) me lo pedís paso a paso. Regresión de las pruebas de 12.2 y 13 en el
  teléfono porque tocan las mismas pantallas.

**Lo que ya sabemos (no repetir errores)**
- Letra nativa de Android y temas (ADR 0043): sin zoom interno ni controles extra de TalkBack; probar con
  letra grande. No modificar ajustes globales del teléfono durante QA.
- Las pruebas existentes buscan textos y etiquetas: si un rótulo cambia, actualizá la prueba sabiendo
  qué cubría; no la borres. Una falla de la prueba no es un bug sin reproducción causal; si algo falla
  dos veces igual, pará y pensá.
- En el teléfono: el teclado tapa la parte baja (cerrarlo antes de tocar), las listas perezosas sólo
  arman lo visible, los avisos pasajeros desaparecen rápido con el reloj de la prueba, y mientras un
  selector del sistema está encima no hay árbol de Compose.
- Copias, avisos, Widget y Clima tocan datos reales: si una prueba de esta etapa los necesita, usar el
  arnés aislado (`Stage12CalculationStore`) o frenar y explicármelo.
- Foto de datos QA antes y después de cada sesión con el teléfono; las 23 bases tienen que quedar
  idénticas.

**Cuándo está lista**
Todas las pantallas pulidas según la lista acordada, con capturas antes/después (claro, oscuro, letra
grande) y la matriz de funciones sin pérdidas; horarios y fechas elegibles con selector y con escritura
en cada lugar del inventario; pruebas de PC y regresión de 12.2 y 13 en el Samsung verdes; datos QA
intactos; STATUS con la lista ✅/⏳/❌; commit local. Fuera de alcance: rediseño nuevo, funciones nuevas,
avisos/Widget/bloqueo/Clima (etapa 14), firma y publicación.

**Límites**
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el momento;
no borrar datos del teléfono. No agregar funciones ni dependencias nuevas (el selector es de Material 3,
ya incluido). Hablame con nombres claros, sin códigos (los códigos sólo entre paréntesis si hacen falta).
