# Auditoría final de la aplicación y candidato local V2

- Fecha: 2026-09-03
- Veredicto: **CANDIDATO LOCAL AUDITADO — SAMSUNG API 36 VERDE;
  COMPATIBILIDAD API 26/API 33 PENDIENTE**
- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- Base funcional integrada:
  `95ebf531d71b8b781423475a1c38d15a8bd24742`
- Ícono adaptativo:
  `381c342c630d8e5ee999ee3afadc25994f3642d8`
- Correcciones finales:
  `f25d097acea37fc6b4126db39e6dbdb0ad793921`
- HEAD de entrada a la QA física:
  `815481b4bdbcf760d1f0424879f63b21fbf66155`
- Correcciones de pruebas físicas:
  `d9c927eb87a50bf9d6cf6a38c7f9e5d10216309a`
- Upstream preservado:
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`

## Alcance

MAIN auditó la aplicación completa después de integrar Ayuda, simplificación de
formularios, ubicación puntual, clima por objetivo, Room V6 y compatibilidad de
Copias. La revisión abarcó dominio, persistencia, navegación, Calendario,
Vacaciones, Feriados, avisos, accesibilidad, Geocoder, Copias y recursos Android.

No se abrió otra dependencia, no se recuperó código desde worktrees históricos
y no se tocó producción. Los worktrees existentes permanecieron intactos.

## Hallazgos cerrados

La auditoría encontró y cerró estos defectos:

1. **Vacaciones y concurrencia.** Editar o eliminar desde una fotografía vieja
   podía reemplazar silenciosamente un cambio más reciente. El repositorio ahora
   exige la fila esperada completa dentro de una transacción Room y la interfaz
   conserva el borrador o diálogo ante conflicto.
2. **Avisos durante arranque frío.** Un broadcast podía llegar mientras la
   recuperación inicial todavía bloqueaba los runtimes y perderse. La identidad
   técnica se encola antes de esa puerta, se revalida al quedar lista la
   aplicación y posee un reintento corto y acotado para fallos transitorios.
3. **Segundo calendario en Feriados.** La selección fue trasladada a la única
   grilla mensual. Mientras ese modo está activo se ocultan las acciones normales
   de carga, repetición y configuración para evitar recorridos mezclados.
4. **Accesibilidad.** Encabezados y títulos de superficie exponen semántica de
   heading y los mensajes persistentes importantes usan una región viva
   asertiva.
5. **Timeout del Geocoder legado.** En API 26–32 el caller vuelve dentro del
   límite aun si el Binder bloqueado ignora la interrupción del worker.
6. **Texto de Copias.** Una copia sin contraseña se describe mediante control de
   integridad y checksum, sin afirmar autenticación criptográfica inexistente.
7. **Ícono de aplicación.** Se reemplazó el vector único por un ícono adaptativo,
   redondo y con capa monocromática desde API 33.

Cada corrección funcional posee cobertura nueva o ampliada. Una revisión final
independiente y de sólo lectura no encontró findings P0, P1, P2 o P3 abiertos.

## Validación local

La batería completa se ejecutó serialmente, con `--rerun-tasks` y un solo worker:

```text
:core:domain:test
:core:database:testDebugUnitTest
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleQa
:app:assembleRelease
:app:assembleQaAndroidTest
:core:database:assembleDebugAndroidTest
```

Resultado: **BUILD SUCCESSFUL en 15m13s**, 351/351 tareas ejecutadas.

Conteos frescos desde XML:

- dominio: 379/379;
- base local: 12/12;
- aplicación: 321/321;
- total JVM: 712/712;
- fallos, errores u omitidas: 0.

Después se corrigió únicamente la calificación de recursos del ícono: la base
adaptativa quedó disponible desde API 26 y la capa monocromática en `v33`. Se
repitieron lint, Debug, QA, Release y AndroidTest de aplicación; el resultado fue
verde. Una última repetición de lint también quedó verde.

Lint informa 0 errores y 0 fatales. Permanecen seis avisos de actualización en
configuración existente: tres `AndroidGradlePluginVersion`, dos
`GradleDependency` y uno `NewerVersionAvailable`.

AndroidTest quedó compilado:

- aplicación: 361 declaraciones `@Test` en fuente;
- base local: 126 declaraciones `@Test` en fuente;
- total declarado: 487.

Artefactos finales:

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 17.883.608 | `30CF37AAA52297C5644DBF580E48153EABCFEC529196E66639669A626B866515` |
| QA | 17.768.513 | `F4D7BF54BC9ED4D3BFF399DA3F81A4FD4504C69304EE99CFE7F5B57E756A6828` |
| Release sin firma | 12.615.001 | `38B5743AC33730DB344C5CDC65EC9ABBCB10E4AD57DEE92697A24B9253427064` |
| QA AndroidTest | 2.107.834 | `69FD03131564EB3F3D9CA7C372C5AAB5A951DB5BF1CFBF45FF75245AA90839EC` |
| Database AndroidTest | 4.183.002 | `F63DE2CE4094F5BC7AD87E711C4CDF07353F6B4E281EC46A308A8AF4B9B10D9F` |

`git diff --check` quedó limpio. El diff confirmado no agrega secretos,
credenciales, placeholders, logs privados, migración destructiva ni consultas
Room en el hilo principal.

Una batería preliminar fue interrumpida cuando apareció el cambio concurrente
del ícono. No se la cuenta como evidencia positiva; el estado combinado fue
validado después de estabilizar el checkout.

## Room y Copias

Room permanece en versión 6:

- archivo `miguardia-v2.db`;
- 27 entidades;
- `identityHash = 7eb39f6fab5a44e69350e206716554be`;
- migración explícita `5→6`;
- sin cambios nuevos de entidad, DAO, esquema o migración en esta auditoría;
- sin `fallbackToDestructiveMigration` ni `allowMainThreadQueries`.

Los esquemas permanecen:

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
6.json  BB5818EA0C086A73B6DFFFF6F1F3F0E547F6BBE05ADCD519D363845679545268
```

La corrección de texto no cambia formato, cifrado, MIME, versión ni semántica de
restauración de Copias.

## Evidencia física

Joaquin autorizó expresamente la QA física final en el Samsung `SM-S938B`,
Android 16/API 36, serie `R5CY529W6PL`. MAIN instaló únicamente los APK QA y de
prueba del candidato actual.

La primera matriz dirigida detectó dos defectos en pruebas, no en producción:

- Feriados seleccionaba correctamente dos fechas, pero la aserción esperaba un
  único nodo semántico;
- una prueba de avisos intentaba eliminar una Vacación con el timestamp anterior
  a la normalización de Room y el CAS nuevo la rechazaba correctamente.

Ambas pruebas fueron alineadas con el comportamiento real: la primera exige dos
fechas seleccionadas y la segunda vuelve a leer la fila persistida antes de una
eliminación CAS. El APK de pruebas se recompiló y los dos casos pasaron 2/2.

La revisión independiente aprobó ambos ajustes y confirmó que no ocultaban un
defecto productivo. También detectó que la clase de avisos debía neutralizar de
forma explícita una preferencia precisa heredada entre corridas. Su preparación
ahora desactiva avisos, fija temporización inexacta y reconstruye el plan antes
de cada caso. El único test que activa voluntariamente alarmas exactas conserva
esa acción dentro de su propio método y fue excluido.

La repetición final sobre ese estado quedó verde:

- Vacaciones y persistencia Room: 9/9;
- aplicación afectada: 33/33;
- total instrumentado único: 42/42;
- fallos, errores u omitidas: 0.

Las 33 pruebas de aplicación fueron repetidas después de incorporar esa barrera
de seguridad y volvieron a pasar 33/33.

La matriz cubrió Vacaciones, Feriados, avisos y arranque frío, accesibilidad y
regresiones de clima sobre el dispositivo.
La revisión directa confirmó además que el ícono adaptativo se representa
correctamente en One UI. Una captura inicial mostró sólo el borde de `Cargar
jornadas`; se comprobó que correspondía al inicio normal de una pantalla
desplazable. Al bajar, la acción apareció completa y por encima de la navegación
del sistema. El zoom interno observado era 100 %.

Al cerrar:

- los dos paquetes de instrumentación fueron desinstalados;
- `com.blackatsystems.miguardia.qa` quedó instalado y detenido; la clase de
  avisos limpió la base QA como parte de su preparación explícita y se conservó
  el estado sintético resultante sin una limpieza adicional al cierre;
- producción `com.blackatsystems.miguardia` permaneció ausente y no fue tocada;
- no se disparó una alarma exacta real ni se reinició el teléfono.

Queda pendiente sobre el mismo candidato:

- API 26: Geocoder legado, recursos adaptativos y regresión esencial;
- API 33: permisos modernos y capa monocromática;
- API 37: puerta posterior del candidato publicable;
- alarma exacta real y reinicio físico: autorizaciones separadas.

## Estado Git

- rama: `codex/miguardia-2.0`;
- HEAD técnico al cerrar la QA:
  `d9c927eb87a50bf9d6cf6a38c7f9e5d10216309a`;
- divergencia al cerrar la QA: 22 adelante, 0 atrás;
- base funcional integrada: `95ebf531`;
- ícono: `381c342`;
- correcciones finales: `f25d097`;
- documentación de entrada a QA: `815481b`;
- correcciones de pruebas físicas: `d9c927e`;
- upstream: `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- `main`, `origin/main` y `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- autor: `joaquin <blackat.systems@gmail.com>`;
- durante esta auditoría no hubo push, tag, Release, merge, rebase, reset o
  descarte.

## Próxima puerta

El código ya forma un candidato local auditado, reproducible y verde en el
Samsung principal. No debe llamarse publicable hasta resolver o aceptar
explícitamente la compatibilidad pendiente en API 26/API 33 y revisar nuevamente
el estado Git resultante. Un eventual push, tag, Release o uso de producción
requiere una decisión separada de Joaquin.
