# Etapa 16 · Experiencia de uso — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 16 · Experiencia de uso**. La etapa 15 (Cierre funcional)
quedó cerrada por Joaquín el 10/11/2026. Después de ésta viene la 17 · Aceptación final y entrega, que
**no** se toca acá.

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md` (el bloque de arriba y las secciones «Etapa 15»),
   `docs/PROCESO_SIMPLE.md` y las filas 16 y 17 de `docs/PLAN_MAESTRO_CIERRE_3_0.md`.
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. STATUS dice cuál
   es el último commit. Hay commits locales sin subir y un pull request abierto contra `main` que no
   está para mergear. Si algo no coincide con STATUS, frená esa parte y avisame.
3. Hablame como dice la sección 1 de mis reglas globales (`~/.claude/CLAUDE.md`): en conversación, de
   vos, primero qué pasó y qué significa, sin formato de informe. Pensá en castellano y corto.

**Qué es esta etapa**
Decisiones de diseño sobre cómo se usa la app, ahora que el funcionamiento interno está resuelto. Lo que
se decida acá sale en la 3.0: por eso va antes de congelar y empaquetar. No se agregan funciones; se
cambia cómo se llega a ellas y cómo se leen.

Temas que ya pedí:
- **Menú lateral:** cómo se abre.
- **Tarjetas:** cómo se muestran los detalles.
- **Pantallas con mucho texto e información:** llevarlas a una estructura más simple y fácil de
  interpretar.
- **Pantalla de conflictos de Copias** (la vi el 10/11 y me pareció «resolver problemas booleanos»):
  - que diga «Encendido / Apagado» y «Ajuste», no «true / false» ni `portable_preferences`;
  - que «Combinar» no arme un cuestionario por cada ajuste distinto: conservar los del teléfono;
  - que confirmar todo con «Conservar actual» avise con claridad que no cambió nada.
- Ligado a lo anterior: con «Combinar» y una copia anterior a las tres opciones del aviso permanente,
  hoy aparece un conflicto por cada opción distinta de fábrica. Decidir si se deja de preguntar por lo
  que la copia no trae (ver `docs/design/ETAPA_14_AVISO_PERMANENTE.md`, «En las Copias»).
- Lo que surja mirando la app.

**Cómo trabajamos esta etapa**
1. **Primero mirar y proponer, sin tocar código.** Relevá cómo están hoy las pantallas de cada tema
   (leyendo el código y, si lo autorizo, con capturas del Samsung). Antecedente y estilo vigente:
   `docs/design/ETAPA_13_5_INVENTARIO.md` (Vigilia R4 con paleta Tokyo Night).
2. **Decidimos de a un tema.** Mostrame cómo está, qué proponés y por qué, con una recomendación. Si
   ayuda, una maqueta o un antes/después. No me traigas diez decisiones juntas.
3. **Después se implementa, por temas chicos**, cada uno probado antes de pasar al siguiente.
4. **Que no se pierda nada:** cada pantalla que cambie conserva sus funciones, estados, datos,
   navegación, temas, letra nativa y TalkBack. Dejá una lista corta de «antes tenía / ahora tiene» por
   pantalla tocada.
5. Si un cambio toca conducta de las Copias (por ejemplo «Combinar»), actualizá el ADR 0035 y las
   pruebas; no es sólo texto.

**Cómo se prueba**
- PC: `python tools/verify.py --profile local --fresh-tests` (al cerrar la 15: 1420 pruebas, 0 fallas) y
  `python tools/verify.py --profile build`.
- Samsung, sólo con mi autorización en el momento. Guiones en `.local-evidence/`:
  `claude-20261110-14/run_classes.sh <salida> [Clase…]` con `QA=` y `RUNNER=`,
  `claude-20261110-14/compare.py <antes.json> <después.json>` y
  `claude-20261107-b1/snapshot_digest.py`. La pareja instalada y la foto de referencia son las que diga
  STATUS (al cerrar la 15: `claude-20261110-15/build1/` y `claude-20261110-15/final-parte-b/`).
- Compilar la pareja: `:app:assembleQa`, tomar el sha256 de `app-qa.apk`, y
  `:app:assembleQaAndroidTest -Pmiguardia.stage12NormalRunner=true -Pmiguardia.stage12QaSha256=<hash>`.
  Instalar con `adb install --user 0 -r`.
- Las pantallas que cambien necesitan sus pruebas de pantalla al día y una mirada mía con el dedo.

**Lo que ya sabemos (no repetir errores)**
- No edites ningún archivo mientras corre `verify.py`, ni documentos: invalida la corrida. Una sola
  batería de Gradle a la vez.
- En Git Bash, exportá `JAVA_HOME` y corré Gradle antes de `MSYS_NO_PATHCONV=1`; usá esa variable sólo en
  los comandos de adb.
- Si armás una lista de pruebas en un archivo desde Python en Windows, sacale los saltos de línea de
  Windows: con ellos el ejecutor rechaza todo sin probar nada (pasó en la 15).
- Seis pruebas del motor del Widget se saltean solas mientras haya un Widget real en mi pantalla de
  inicio. No es una falla: se corren cuando lo quito.
- El Samsung pone el Widget en 5 columnas por 2 filas y no admite una fila. Lo di por correcto.
- Restaurar una copia vacía los informes guardados y la caché de Clima: es conducta de la app, no una
  pérdida. Si la foto de datos da diferencias y yo estuve usando la app QA, preguntame antes de
  investigar.
- Las pruebas viejas que publican avisos reales, programan alarmas o configuran el Widget pisan datos
  de la app QA: no correrlas sobre mis datos. Copias y restauración automáticas, nunca contra la base QA
  real.
- Los pasos que me pidas en el teléfono: pocos, de a uno, diciéndome qué voy a ver. Nada de hacerme
  interpretar valores internos.
- Una falla de la prueba no es un defecto de la app sin reproducción causal. Si algo falla dos veces
  igual, pará y pensá.

**Cuándo está lista**
Cada tema decidido conmigo, implementado, con PC aprobada, corrida en el Samsung sobre el último código
con datos intactos, y mi visto bueno mirándolo. Commit local al cerrar cada tema.

**Límites**
No congelar la candidata ni cambiar la versión: eso es de la 17 (ya está decidido 3.0.0, código 7).
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el momento;
no borrar datos del teléfono. Sin funciones ni dependencias nuevas. Lo que quedó para la 17 (los nueve
casos sin prueba automática, ver «Usar copia» con el dedo) no se resuelve acá.
