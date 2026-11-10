# Etapa 15 · Cierre funcional, aceptación y entrega — prompt de arranque

Pegar en una sesión nueva de Claude, desde `MiGuardia-3.0/`:

---

Retomá MiGuardia 3.0 y arrancá la **etapa 15 · Cierre funcional, aceptación y entrega**. La etapa 14
(Sistema) quedó cerrada por Joaquín el 10/11/2026 con pendientes que pasan a ésta. Después viene la
16 · Experiencia de uso (decisiones de diseño), que **no** se toca acá.

**Antes de tocar nada**
1. Leé `CLAUDE.md`, `docs/STATUS.md` (las secciones de la etapa 14 de arriba), `docs/PROCESO_SIMPLE.md` y
   la fila 15 de `docs/PLAN_MAESTRO_CIERRE_3_0.md`.
2. Puerta 0: rama `codex/miguardia-3.0`, HEAD, cambios sin commit, worktrees y remoto. STATUS dice cuál es
   el último commit y cuántos van adelante de `origin`. Hay un pull request abierto contra `main`
   (https://github.com/blackat-systems/MiGuardia/pull/1) que **no está para mergear**. Si algo no coincide
   con STATUS, frená esa parte y avisame.
3. Hablame con nombres claros, sin códigos. Resultado primero, razones después, próximo paso al final.

**Qué se quiere, en este orden**

*A · En la PC, sin teléfono*
1. **Las tres opciones del aviso permanente viajan en las Copias** (pedido de Joaquín, no empezado). Las
   opciones son `alwaysShowNext`, `showOnLockScreen` y `countdownWhileActive` de `NotificationPreferences`.
   Diseño ya pensado:
   - `backup/PortablePreferencesGateway.kt`: tres claves nuevas y opcionales
     (`notifications.always_show_next`, `notifications.show_on_lock_screen`,
     `notifications.countdown_while_active`). `capture` emite las 20. `normalize` acepta las 17 de siempre
     más cualquier subconjunto de las tres, y **completa las que falten con el valor de fábrica**
     (falso, verdadero, verdadero), para que comparación, registro de recuperación y verificación trabajen
     siempre con 20. `decode` las lee. `NotificationPreferencesStore.replacePortable` las escribe.
   - Una copia anterior (17 claves) se tiene que seguir restaurando; al restaurarla, las tres opciones
     quedan de fábrica.
   - El número 17 está escrito en: `app/src/test/.../upgrade/UpgradeFixtureSnapshotsTest.kt` (líneas ~158,
     ~167 y la lista de ~282), `app/src/testSupport/.../upgrade/Stage10UpgradeContract.kt` (~64 y ~88),
     `app/src/androidTest/.../upgrade/PublicUpgradeBackupFixtureTest.kt` (~142) y
     `app/src/androidTest/.../backup/PortablePreferencesGatewayInstrumentedTest.kt` (~129). Ojo: las tres
     primeras son el contrato del ensayo de actualización desde la versión pública; cambiá sólo lo que el
     cambio obliga y entendé qué comprueba cada una antes de tocarla. Las copias de origen de esos ensayos
     siguen teniendo 17.
   - Pruebas nuevas: copia nueva ida y vuelta con las tres opciones; copia anterior de 17 aceptada y con
     valores de fábrica; clave desconocida rechazada como hoy.
   - Actualizá `docs/design/ETAPA_14_AVISO_PERMANENTE.md` (hoy dice que no viajan) y el ADR de Copias si
     describe la lista de preferencias.
2. **Pasar en limpio la lista de la etapa 14.1:** los 113 casos de `docs/design/ETAPA_14_MAPA_COBERTURA.md`
   con ✅/⏳/❌ y su nivel de evidencia, usando lo anotado en STATUS. Hoy está contado por superficie, no
   caso por caso.

*B · En el Samsung, sólo con mi autorización en el momento*
3. Foto de datos QA, compilar la pareja, instalar encima (sin desinstalar), correr **todas** las pruebas de
   `Stage14Selectors` (138 al cerrar la 14), foto y comparación. Si se tocaron las Copias, correr también
   las de la etapa 13 (`Stage13DataSelectors`). La pareja instalada al cerrar la 14 es **anterior** a los
   últimos cambios: no tiene la paleta nueva del Widget, la letra grande, el tercer control ni el «17 °C».
4. Con mi dedo, paso a paso y con lo que voy a ver:
   - Widget con la paleta nueva, en claro y en oscuro; quitarlo y volver a agregarlo para ver con qué
     tamaño lo pone el Samsung y si deja achicarlo a una fila.
   - Clima: que diga «17 °C» y no «17–17 °C» en una jornada de una hora.
   - Aviso permanente: el tercer interruptor («Contar hasta el final durante la jornada»).
   - TalkBack de oído: guiame para activarlo y para apagarlo (no sé hacerlo).
   - Si se tocaron las Copias: crear una copia, restaurarla y ver que vuelven las tres opciones del aviso.
5. Pedime permiso para borrar los 16 archivos vacíos `cache/stage14-widget-*.db.lck` de la primera
   corrida de la 14 (nombres exactos, sólo esos).

*C · Aceptación final (era la segunda parte de la 14)*
6. Fijar la candidata: versión, código, QA, ejecutor y fixtures congelados. La versión sigue en `2.0.2`,
   código 6 (`app/build.gradle.kts`): **preguntame** qué número va.
7. Una sola pasada completa de los 441 casos sobre esa candidata, con la lista en STATUS. Sumá una
   revisión con letra normal en teléfono angosto, normal y grande. Tras cada arreglo: repetir lo afectado
   más la prueba corta (cargar, editar y ver jornadas; horas y Resumen; Copias; avisos y Widget). No sumar
   resultados de APK distintos.

*D · Entrega (cada paso con mi autorización específica)*
8. Firma, prueba de actualización desde la 2.0.2 pública sin pérdida de datos, y publicación. La clave de
   firma nunca entra al repositorio.

**Cómo se prueba**
- PC: `python tools/verify.py --profile local --fresh-tests` (al cerrar la 14: 1413 pruebas, 0 fallas) y
  `python tools/verify.py --profile build`.
- Samsung: guiones y hashes en `.local-evidence/claude-20261110-14/`:
  `run_classes.sh <salida> [Clase…]` con `QA=` y `RUNNER=` (corre cada clase de `Stage14Selectors` en su
  propia invocación), `compare.py <antes.json> <después.json>` y `snapshot_digest.py` en
  `.local-evidence/claude-20261107-b1/`. La foto de referencia es la que diga STATUS.
- Compilar la pareja: `:app:assembleQa`, tomar el sha256 de `app-qa.apk`, y
  `:app:assembleQaAndroidTest -Pmiguardia.stage12NormalRunner=true -Pmiguardia.stage12QaSha256=<hash>`.
  Instalar con `adb install --user 0 -r`. Si agregás pruebas a `stage14/`, regenerá `Stage14Selectors`.

**Lo que ya sabemos (no repetir errores)**
- **No edites ningún archivo mientras corre `verify.py`**, ni documentos: invalida la corrida («inputs
  changed»). Una sola batería de Gradle a la vez.
- En Git Bash, exportá `JAVA_HOME` y corré Gradle **antes** de `MSYS_NO_PATHCONV=1`; con esa variable
  puesta, Gradle no encuentra Java. Usala sólo en los comandos de adb (en un subshell).
- `local.properties` usa `sdk.dir=C\:/Users/User/Android/Sdk`. Si lint repite un error viejo, es la caché:
  `verify.py --profile build --rebuild` una vez.
- Si la foto de datos da diferencias y yo estuve usando la app QA, **preguntame antes de investigar**.
- Mi teléfono quedó con cambios de la sesión de la 14 (avisos activados, aviso permanente encendido,
  «Mostrar en la pantalla bloqueada» apagado, Clima habilitado, un Widget en la pantalla de inicio). No
  los devuelvas sin preguntarme.
- Samsung coloca el Widget a todo el ancho por dos filas (405×208 dp) sin importar el mínimo declarado;
  una fila le da 95 dp. Hay cuatro vistas por tamaño en `WidgetRemoteViewsRenderer.kt`.
- Un mensaje que aparece lejos de donde toqué parece que la app no hizo nada (pasó con «Actualizar» de
  Clima): revisá dónde queda la respuesta de cada acción.
- Las pruebas viejas que publican avisos reales, programan alarmas o configuran el Widget borran la base o
  pisan preferencias de la app QA (lista en el mapa de cobertura): no correrlas sobre mis datos.
- Copias y restauración: nunca contra la base QA real; usar el coordinador de la corrida.
- Una falla de la prueba no es un defecto de la app sin reproducción causal. Si algo falla dos veces
  igual, pará y pensá.
- Criterio de letra grande: usable, no pulida; ningún arreglo de letra grande cambia la vista normal.

**Cuándo está lista**
A: Copias con las tres opciones, PC aprobada, lista de 14.1 caso por caso. B: corrida completa en el
Samsung sobre la última versión, datos intactos, y mi revisión con el dedo. C: pasada de 441 sobre la
candidata congelada. D: lo que yo autorice de firma, actualización y publicación. Commit local al cerrar
cada parte.

**Límites**
Nada de push, firma ni publicación sin pedido mío. Nada en el teléfono sin autorización en el momento; no
borrar datos del teléfono. No agregar funciones ni dependencias nuevas. La etapa 16 (cómo se abre el menú
lateral, cómo se muestran los detalles en las tarjetas, simplificar pantallas con mucho texto) es
posterior: si aparece una idea de diseño, anotala para la 16 y no la implementes.
