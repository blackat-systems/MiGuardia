# Auditoría MAIN — Informes locales de jornadas y horas V2

- Fecha: 2026-08-29
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `f2d05b96b8ff11c1c14dfadb2788c6d514d04176`
- Resultado: **CERRADO — LOCAL Y SAMSUNG API 36 VERDES**

## Objetivo

Auditar, corregir, validar e integrar el handoff de Informes locales sin abrir
la dependencia siguiente ni publicar la rama. El bloque transforma la verdad
mensual de Horas y Resumen en PDF y XLSX locales, con privacidad apagada por
defecto y sin escribir datos laborales.

## Puerta 0

Antes de editar o usar el dispositivo se verificó:

- ruta, rama y HEAD exactos;
- upstream `origin/codex/miguardia-2.0` en
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- rama 11 commits adelante y 0 detrás;
- autor `joaquin <blackat.systems@gmail.com>` y remoto privado correcto;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- nueve worktrees históricos preservados;
- candidato esperado sin staged, sin eliminaciones y con
  `git diff --check` limpio;
- Samsung `SM-S938B`, API 36, serie `R5CY529W6PL`, sin paquetes MiGuardia;
- rotación inicial `accelerometer_rotation=1`, `user_rotation=0`.

## Handoff, alcance y auditorías independientes

El handoff llegó sin commit sobre el checkout compartido. El candidato inicial
afectaba 38 rutas. MAIN retiró una ampliación innecesaria de visibilidad y
localizó el encabezado adaptativo dentro de Informes; el bloque final contiene
37 rutas de código y pruebas: 14 modificadas y 23 nuevas, sin eliminaciones ni
staging.

MAIN coordinó revisiones separadas de dominio, persistencia, almacenamiento,
UI, pruebas y documentación. La revisión final independiente no fue usada para
reemplazar la batería ni la revisión directa de cada hunk.

## Correcciones MAIN

MAIN corrigió dentro del alcance:

1. el escritor SAF abre el destino con modo de truncado real;
2. horario real y fotografía histórica resuelven fecha y sector correctos;
3. el snapshot excluye cuerpos privados de meses vecinos;
4. trabajo activo iniciado en el mes anterior se incluye cuando reemplaza
   disponibilidad del mes informado;
5. la captura de Room permanece coherente ante escrituras concurrentes y sus
   pruebas usan dos conexiones con barreras deterministas;
6. recreación, cancelación, error y reintento no reactivan efectos externos ni
   guardan notas, bytes o rutas en estado restaurable;
7. PDF pagina y envuelve contenido sin truncar; fotos se congelan en staging
   privado, se procesan de a una y tienen limpieza acotada;
8. mensajes de error no exponen rutas ni detalles privados;
9. filas completas de opciones son accesibles sin doble acción del control
   interno;
10. el encabezado propio de Informes apila título y navegación en ancho lógico
    reducido, por lo que `Informes locales` ya no corta una palabra al zoom
    interno 200 %;
11. cambiar de PDF a XLSX elimina la selección de fotos, incluso del estado
    restaurable, para que volver a PDF no reactive una inclusión privada oculta;
12. un resultado tardío del selector de guardado intenta retirar el documento
    vacío y avisa que la operación fue interrumpida; los destinos se limitan a
    documentos SAF `content://`, nunca a rutas directas, y sólo se elimina un
    destino cuyo tamaño conocido sigue siendo exactamente cero.

Dos fallos iniciales de Room físico pertenecían a fixtures de prueba: una
jornada nocturna contradecía su plantilla y una carrera no forzaba la
intercalación. Tres fallos iniciales de Compose pertenecían a selectores de
semántica y a un fixture que no estaba realmente vacío. Se corrigieron las
pruebas sin relajar contratos productivos.

## Resultado funcional y privacidad

- `Generar informe` abre desde el mes visible de Resumen.
- PDF y XLSX consumen una proyección mensual pura; no duplican la fórmula de
  trabajo ni guardan totales.
- PDF puede incluir fotos elegidas; XLSX explica que no las transporta.
- Nombre, puesto, notas de jornadas, nota médica y fotos comienzan apagados.
- La nota médica exige una segunda confirmación consciente.
- Direcciones, IDs, rutas, EXIF, explicaciones internas y montos quedan fuera.
- Guardar usa SAF; Compartir usa una URI privada de `FileProvider` y permisos
  temporales.
- La cancelación conserva el artefacto privado válido para reintentar.

## Room y almacenamiento

Los contratos persistidos de Room permanecen byte a byte sin cambios:

- `MiGuardiaV2Database`, archivo `miguardia-v2.db`;
- versión 5, 27 tablas;
- `identityHash` `77adbc875d0f4ee466cdbd0dd74d5c5c`;
- sin `fallbackToDestructiveMigration` ni `allowMainThreadQueries`;
- snapshot de Informes transaccional y de sólo lectura.

```text
1.json  5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json  E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json  39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json  796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json  40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## Validación local final

La batería contractual se repitió sobre el estado definitivo con
`--rerun-tasks`, `--max-workers=1` y 351/351 tareas ejecutadas.

- `BUILD SUCCESSFUL in 13m 32s`.
- JVM: 548/548, sin fallos, errores ni omitidas:
  - dominio: 329;
  - base local: 12;
  - app: 207.
- Lint: 0 errores/fatales; 6 avisos de versiones en Gradle no modificado.
- Compilados: Debug, QA y Release sin firma.
- AndroidTest compilado: inventario fuente app 283 y base 113.
- `git diff --check`: limpio antes de documentación.

Artefactos finales:

```text
Debug APK             15942371  80B4B85BFC288683223FD6300FB33933383C6A6376925512D2184E35A51A6F5E
QA APK                15827284  58AC61F89D93D39CFDF433C3C0D3814E55F807DAC6431B5D332A83E83297219A
Release unsigned      11147415  CF13E4FD2E6CB989828F19C77C3C8D802908328D3BC3B06B0B3518647F74FB22
QA AndroidTest         1899766  2ED654800F69A15471335CA76898AB12760E24C85AA7833BDED5052B09220D17
Database AndroidTest   4008192  1922090489594754F7D5B1383522437ED22542CEFB6C2471D829DBE35E441A52
```

## Validación física Samsung

Joaquin autorizó expresamente el Samsung conectado. MAIN usó exclusivamente
paquetes QA/test y datos ficticios.

Evidencia ejecutada antes de las dos correcciones finales de privacidad y
guardado:

- app: 23/23;
- Room: 5/5;
- total único: 28/28;
- regresión de encabezado al 200 % repetida después de la corrección: 4/4;
- fallos, errores y omitidas finales: 0.

Recorrido manual real:

- instalación limpia y selección de Medicina;
- creación de `Hospital ficticio (HFIC)` y jornada 08:00–16:00;
- acceso Resumen → Generar informe con mes conservado;
- inclusiones privadas apagadas;
- PDF y XLSX generados y guardados mediante el selector real;
- ShareSheet abierto y cancelado sin enviar el archivo;
- guardado cancelado y reintentado con éxito;
- PDF de 87.646 bytes reabierto y renderizado en dos páginas A4, sin cortes ni
  superposiciones;
- XLSX OOXML de 5.397 bytes guardado;
- oscuro/retrato/100 %, claro/paisaje/150 % y claro/retrato/200 % revisados;
- título completo y acción visible al 200 % después de la corrección.

Después de reconectar y autorizar nuevamente el Samsung, MAIN repitió la matriz
afectada definitiva sobre el código final:

- app: 28/28;
- Room: 5/5;
- total único: 33/33;
- fallos, errores y omitidas: 0.

La evidencia anterior se conserva como histórica; el cierre se apoya en esta
repetición definitiva.

Después de crear la primera configuración, la tarjeta superior mostró una vez
un mensaje transitorio desactualizado hasta reabrir QA. La jornada y el catálogo
ya estaban persistidos y el mensaje no reapareció. Es una observación ajena a
Informes y no bloquea este cierre; no se presenta como corregida.

## Seguridad del dispositivo

- Producción estaba ausente y nunca fue instalada, abierta, limpiada ni
  desinstalada.
- Sólo se instalaron `com.blackatsystems.miguardia.qa`,
  `com.blackatsystems.miguardia.qa.test` y
  `com.blackatsystems.miguardia.core.database.test`.
- Los dos informes ficticios se borraron de Descargas.
- Después de la repetición definitiva se desinstalaron
  `com.blackatsystems.miguardia.qa.test` y
  `com.blackatsystems.miguardia.core.database.test`.
- Por el pedido expreso de Joaquin de iniciar una sesión en vivo, quedó instalada
  y abierta únicamente `com.blackatsystems.miguardia.qa`; producción continuó
  ausente.
- Rotación restaurada exactamente a `accelerometer_rotation=1` y
  `user_rotation=0`.
- No se modificaron `font_scale`, densidad ni tamaño visual del sistema.
- Incidente menor de sólo lectura: al comprobar la rotación, un comando mostró
  también el tamaño físico y la densidad dentro de su salida. Esos valores no
  se usaron ni se modificaron.
- No hubo alarma exacta real, reboot, envío externo ni datos reales.

## Git, límites y veredicto

- No se hizo push, tag, merge, rebase, reset, descarte ni acción sobre `main`.
- API 26/API 33 no se repitieron para Informes; el cierre físico corresponde a
  Samsung API 36.
- Informes queda cerrado y habilitado para su checkpoint local después de pasar
  los 33 casos físicos.
- Copias y restauración locales seguras no está habilitado.
- La orden final de Joaquin es detener el proceso después del checkpoint local
  de Informes.

```text
INFORMES CERRADO POR MAIN
LOCAL: 548/548 JVM Y 351/351 TAREAS
SAMSUNG API 36: APP 28/28 + ROOM 5/5 = 33/33
COPIAS Y RESTAURACIÓN: NO HABILITADO
PROCESO: DETENER DESPUÉS DEL CHECKPOINT LOCAL
```
