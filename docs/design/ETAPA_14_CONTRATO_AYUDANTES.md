# Etapa 14.1 · Contrato común para ayudantes (tanda 1: pruebas de PC y de pantalla)

Base: commit `1dd3899` en `codex/miguardia-3.0`, directorio
`C:\Users\User\Desktop\Claude Projects\MiGuardiaContainer\MiGuardia-3.0`. Escrito por MAIN el 9/11/2026.

## Qué se pide

Escribir **pruebas nuevas** que cierren los «Falta» de tu superficie en
`docs/design/ETAPA_14_MAPA_COBERTURA.md` y que se puedan cerrar **sin teléfono en la mano**: en la PC
(JUnit 4, sin Robolectric: no hay) o como prueba instrumentada de pantalla con estado ficticio.
No se cambia la app: no se agregan funciones, textos ni dependencias.

Lo que el mapa marca **Dedo**, red real, Widget real en la pantalla de inicio o sistema real con la base
de la app no es de esta tanda: dejalo anotado como pendiente.

## Antes de escribir

1. Leé la sección de tu superficie en el mapa y la fila de cada caso en
   `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`.
2. Leé el código productivo que vas a probar y **una prueba existente de la misma superficie** para
   copiar su forma (reglas, dobles, etiquetas de prueba, letra simulada con
   `app/src/androidTest/.../testsupport/AndroidFontScaleTheme.kt`).
3. Los textos y etiquetas que afirmes salen del código real, leído; no de memoria ni del mapa.

## Reglas de las pruebas

- **Archivos nuevos solamente**, con prefijo `Stage14`, salvo los archivos existentes que tu encargo
  nombre expresamente. Cada ayudante tiene los suyos: no toques los de otro.
  - PC: `app/src/test/java/com/blackatsystems/miguardia/<paquete de lo probado>/Stage14…Test.kt`
    (o `core/domain/src/test/...`).
  - Pantalla: `app/src/androidTest/java/com/blackatsystems/miguardia/stage14/Stage14…Test.kt`.
- Las pruebas instrumentadas de esta tanda **no pueden**: abrir la base de la app (`miguardia-v2.db`) ni
  `application.localDataStore`; leer o escribir preferencias reales de la app (avisos, bloqueo, Clima,
  Widget, tema, recorrido); publicar avisos, programar alarmas, usar la red o la ubicación; dejar
  archivos en `files/` o `cache/` de la app; cambiar ajustes del teléfono. Estado y acciones ficticios,
  en memoria; si necesitás un archivo, `TemporaryFolder` y borrado.
- Datos ficticios evidentes. Nada de datos personales.
- Una prueba afirma **conducta observable** (texto visible, estado, llamada recibida), no sólo que un
  nodo existe. Probá también el borde o el error del caso cuando lo tenga.
- Filas tocables de Compose fusionan su contenido: tocá la fila, no el hijo. Listas perezosas: desplazate
  hasta el nodo antes de afirmar.
- Nombre de método en inglés que diga la conducta; un comentario corto arriba con el caso que cubre
  (por ejemplo `// A217`).
- Si para probar algo hiciera falta **cambiar código de la app**, no lo cambies: anotalo y seguí.
- Si encontrás que **la app hace algo distinto de lo que el caso exige**, no acomodes la prueba al
  defecto: dejá la prueba afirmando lo correcto, marcala en tu informe como «posible defecto» y dá
  archivo:línea de la causa.

## Lo que no podés hacer

- Correr Gradle, adb, pruebas o cualquier build: **MAIN es el único que compila y ejecuta**. Vos
  verificás leyendo firmas, importaciones y visibilidad (`internal` es visible desde las pruebas del
  mismo módulo; `private` no).
- Git: nada de commit, stash, checkout ni restore.
- Tocar `docs/`, `tools/`, Gradle, recursos, `app/src/main`, `app/src/testSupport` o pruebas existentes
  fuera de las que nombre tu encargo.

## Informe de vuelta

En castellano, corto:

- Archivos creados o tocados.
- Tabla: caso → `Clase#método` → nivel (PC / pantalla) → qué afirma.
- Casos de tu lista que **no** pudiste cerrar y por qué (necesita teléfono, necesita un cambio de app…).
- Posibles defectos de la app, con archivo:línea.
- Dudas de compilación: cada lugar donde no pudiste confirmar una firma o una importación.
