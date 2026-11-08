# Etapa 14 · Sistema y aceptación final — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 14 · Sistema y aceptación final**. La etapa 13 (Datos y
archivos) quedó cerrada el 8/11/2026 (commit `c74284f`, subido a `origin/codex/miguardia-3.0`).

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md`, `docs/PROCESO_SIMPLE.md` y `docs/LECTURAS_POR_TAREA.md`
   (filas «Próximo evento/avisos/Widget», «Bloqueo/Ayuda», «Clima/ubicación», «Calendario/navegación/letra»
   y «Distribución pública…» sólo para Actualizaciones: ADR 0033, 0036–0043, Planificación §12, §15 y §22).
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. Si algo no
   coincide con STATUS, frená esa parte y avisame.
3. Leé la fila 14 de `docs/PLAN_MAESTRO_CIERRE_3_0.md` y, en
   `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`, las secciones
   «A · Avisos y notificaciones» (A208–A231), «A · Clima global y proveedor» (A232–A245), «A · Bloqueo y
   privacidad de entrada» (A246–A259), «A · Apariencia, accesibilidad y Ayuda» (A260–A271),
   «A · Actualizaciones y apoyo voluntario» (A272–A286), «W · Widget de inicio» (W001–W024), los casos
   C080–C084, el Anexo B (recorridos combinados X001–X015) y el Anexo C (revisión visual V001–V016).
   Son la lista de qué probar: seguimos el Proceso simple.

**Qué se quiere**
Dos partes, en este orden:
- **14.1 · Superficies del sistema.** Avisos (permiso en contexto, anticipos generales y por jornada,
  rechazo manejado, alarmas exactas), Widget (agregar, configurar, contenido correcto y privado,
  actualización tras cambios), bloqueo de acceso con el bloqueo del teléfono, Clima (activación
  consciente, ubicación aproximada o dirección, caché fresco/viejo/vencido, error), temas y letra
  nativa de Android, TalkBack, Ayuda, y Actualizaciones/apoyo voluntario (sólo conexiones aprobadas por
  ADR 0038–0041, sin datos laborales).
- **14.2 · Aceptación final.** Fijar la candidata final (versión, código, QA, ejecutor y fixtures
  congelados) y hacer **una sola pasada completa** de los 441 casos sobre ella. Tras cada arreglo
  posterior: repetir lo afectado más la **prueba corta** (cargar, editar y ver jornadas; horas y
  Resumen; Copias; avisos y Widget). No sumar resultados de APK distintos.

**Cómo se prueba**
- Primero en la PC: mapa de qué casos de 14.1 ya cubren las pruebas existentes (NotificationComposeTest,
  WeatherComposeTest, WeatherViewModelTest, AppearanceComposeTest, pruebas de widget, bloqueo y Ayuda,
  `Stage12W010NativeTest`, `Stage12X015NativeTest` y afines) y cuáles faltan. Proponeme la lista
  agrupada por superficie antes de escribir pruebas nuevas.
- PC: `python tools/verify.py --profile local` (última corrida 1339 pruebas, 0 fallas).
- Samsung: sólo con mi autorización en el momento. Pareja instalada (QA `71523f97…`, ejecutor
  `1c22148b…`), entorno y script (`.local-evidence/claude-20261108-13/fix-lck/run.sh` con `QA=` y
  `RUNNER=`) en STATUS. Lo que necesite mi dedo (permisos del sistema, agregar el Widget a la pantalla
  de inicio, bloqueo del teléfono, TalkBack, mirar un tema) me lo pedís paso a paso, en castellano, con
  lo que voy a ver en pantalla.

**Lo que ya sabemos (no repetir errores)**
- **Avisos, Widget y Clima son de la Application, no de una pantalla.** En el arnés de las etapas 12–13
  (`Stage12CalculationStore`) esos efectos se rechazan a propósito. Probarlos de verdad publica avisos,
  programa alarmas y dibuja el Widget del sistema, y hoy leen la base QA real (`miguardia-v2.db`).
  Antes de correr nada, decidí conmigo: o se prueban sobre los datos QA en sólo lectura (sin escribir),
  o se arma un aislamiento propio. Nunca modificar ajustes globales del teléfono ni desinstalar.
- Copias y restauración: nunca contra la base QA real; usar el coordinador de la corrida
  (`Stage12CalculationStore.backupCoordinator`). La limpieza del arnés falla si queda una base
  candidata o su `.lck`.
- Foto de datos QA antes y después de cada sesión (`snapshot_digest.py`); las 23 bases tienen que
  quedar idénticas. `files/profileInstalled` cambia al instalar (marca de Android).
- En el teléfono el teclado queda abierto tras escribir y tapa la parte baja: cerrarlo antes de tocar
  (`Stage13DeviceBase.tap`). Las listas perezosas sólo arman lo visible: desplazarse hasta el nodo y
  reintentar si la lista se rearma. Los avisos pasajeros desaparecen enseguida con el reloj de la
  prueba: esperar el estado o la base, no el aviso. Mientras un selector o pantalla del sistema está
  encima, no hay árbol de Compose: esperar con `device.currentPackageName`.
- Compose: las filas tocables fusionan su contenido; tocar la fila, no el hijo. Hojas y diálogos usan
  el contexto de la Activity. Vaciar los ViewModels antes de cerrar la base de la corrida.
- Una falla de la prueba no es un bug de la app sin reproducción causal. Si algo falla dos veces
  igual, pará y pensá antes del tercer intento.
- Pendientes anotados para esta etapa: «Guardar meta» durante una recarga no hace nada ni avisa
  (12.2); TalkBack podría alcanzar el Calendario debajo de pantallas completas (B1); Atrás con una
  recuperación de Copias pendiente cierra la app, INFERIDO que al reabrir aparece la recuperación (13).

**Cuándo está lista**
14.1: cada caso de las superficies con ✅/⏳/❌ y evidencia del nivel que pide (PC, Samsung automático o
con mi dedo); datos QA intactos; commit local. 14.2: pasada completa de los 441 casos sobre la
candidata congelada, con la lista en STATUS y la prueba corta tras cada arreglo. Fuera de alcance:
firma, prueba de actualización desde 2.0.2 y publicación (etapa 15).

**Límites**
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el momento;
no borrar datos del teléfono (restos propios de prueba, sólo con mi permiso). No agregar funciones ni
dependencias nuevas. Red sólo para lo aprobado (Clima, actualizaciones públicas). Hablame con nombres
claros, sin códigos (los códigos sólo entre paréntesis si hacen falta).
