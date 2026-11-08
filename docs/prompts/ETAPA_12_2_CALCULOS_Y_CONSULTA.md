# Etapa 12.2 · Cálculos y consulta — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 12.2 · Cálculos y consulta**. B1 (Trabajo y
Calendario) quedó cerrado el 8/11/2026.

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md`, `docs/PROCESO_SIMPLE.md` y `docs/LECTURAS_POR_TAREA.md`.
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. Si algo no
   coincide con STATUS, frená esa parte y avisame.
3. Leé el plan detallado de la etapa:
   `.local-evidence/stage12-1/main/reorganization-20261103/next-stage12-2-plan/plan.md`
   y la fila 12.2 de `docs/PLAN_MAESTRO_CIERRE_3_0.md`. Es antecedente para elegir qué probar,
   no un circuito de recibos: seguimos el Proceso simple.

**Qué se quiere**
Que, con una misma fixture, Calendario, «Tus horas y tu meta», Resumen e Informes PDF/XLSX
muestren los mismos minutos, extras y referencia, y que cada pantalla vuelva a su origen
conservando mes y contexto. Tres tramos del mismo trabajo:
- **Horas, meta y extras** (casos A077–A110): habitual, extra por clase, total, pendiente,
  disponibilidad separada; metas fijas, variables y sin valor; vigencias y cortes; extras de
  jornada e independientes con minutos exactos y preservación histórica.
- **Resumen** (A127–A145): mes, períodos, desgloses, carga/vacío/error/último válido,
  personalización y retorno Calendario → detalle → Resumen → vuelta. Anotar la latencia de
  primera apertura (la causa de la alerta de la etapa 8 sigue sin resolver; no reabrirla sola).
- **Informes** (A146–A165): abrir desde menú y desde Resumen, conservar mes y regreso, conciliar
  PDF y XLSX reales con la misma proyección, privacidad (lista blanca de ADR 0034),
  consentimientos, cancelación y error.

**Cómo se prueba**
- Primero en la PC: armá el mapa de qué casos ya cubren las pruebas existentes y cuáles faltan.
  Proponeme la lista agrupada por pantalla antes de escribir pruebas nuevas.
- PC: `python tools/verify.py --profile local` (anotado: termina con `preflight_failed` por una
  ruta vieja aunque Gradle pasa; arreglarlo o correr Gradle directo y decirlo).
- Samsung: sólo con mi autorización en el momento. La pareja instalada y el entorno (SDK, Java,
  `MSYS_NO_PATHCONV=1`) están en STATUS. Las pruebas que necesiten mi dedo me las pedís paso a
  paso, en castellano, con lo que voy a ver en pantalla.
- Lección de B1: las hojas y diálogos de Compose usan el contexto de la Activity, no el de la
  prueba; un código de prueba que nunca corrió en el teléfono hay que esperarlo con fallas de
  arnés. Si algo falla dos veces igual, pará y pensá antes del tercer intento.

**Cuándo está lista**
Horas, Resumen y archivos concilian exacto sobre la misma fixture; período, privacidad,
conservación y retornos comprobados al nivel que pide cada caso; STATUS con la lista
✅/⏳/❌ de la etapa; commit local de lo probado. Fuera de alcance: Copias, sistema, firma y
publicación.

**Límites**
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el
momento; no borrar datos del teléfono. No agregar funciones ni dependencias nuevas.
Hablame con nombres claros, sin códigos (los códigos sólo entre paréntesis si hacen falta).

---
