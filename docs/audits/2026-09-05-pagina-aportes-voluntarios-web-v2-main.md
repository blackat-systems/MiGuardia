# MAIN — Página de aportes voluntarios web V2

- Fecha: 2026-09-05.
- Veredicto: **CERRADO E INTEGRADO LOCALMENTE — NO PUBLICADO**.
- Bloque: 26, `PAGINA_DE_APORTES_VOLUNTARIOS_WEB_V2.md`.
- Checkpoint web: `eae7d54592e6d1cc58bccc1c288c59356ad2e03f`,
  `feat: add voluntary MiGuardia support page`.

## Objetivo y alcance

MAIN recibió el handoff, auditó la página bilingüe `/miguardia/apoyar`, su
enlace discreto desde la presentación y sus pruebas. Conserva el carácter
voluntario del aporte, la única salida consciente al proveedor y el regreso
sin pagar, sin relación con plazas, funciones, opiniones o actualizaciones.
No agrega pagos embebidos, registros de aportantes, APIs ni funciones Android.

La skill `joa-main-orchestrator` guio Puerta 0, revisión independiente y staging
exacto. Sites guio el trabajo sobre el sitio existente sin publicar; computer-use
permitió la revisión local de navegador. No ampliaron las autorizaciones.

## Puerta 0 y propiedad

MiGuardia comenzó limpio en `635f9a6d9aad4a77a7272cccb5551b32ff41944a`, rama
`codex/miguardia-2.0`, 6 adelante/0 atrás de `origin/codex/miguardia-2.0`
en `924fd46df018f5e14637dfe90a983fefdbf27af4`. Autor:
`joaquin <blackat.systems@gmail.com>`; remoto privado existente, nueve worktrees
preservados y staging vacío. `main`, `origin/main` y `v1.0.0^{}` resuelven a
`82db6fd8eb2c511205968894dc9857a96b16ed20` y no se mueven.

Website comenzó en `2799c473877acff2f280759d6a3d398c9f39a455`, rama `main`
propia del sitio, sin remotos, un worktree y staging vacío. Conserva su autor
efectivo `blackat-systems <blackat.systems@gmail.com>`, igual al checkpoint
web anterior; no se cambió la configuración Git global o local.

MAIN recalculó los 104 hashes del inventario inicial: 103 idénticos y una
diferencia permitida, las tres líneas del footer en `MiGuardiaPage.tsx`.
Los tres archivos sucios del portfolio, ocho archivos ajenos sin rastrear,
hosting, contratos/API, configuración y dependencias permanecen intactos.

## Auditoría e integración

Se revisaron los siete archivos: componente, CSS, layout, entrada de ruta,
pruebas, documento operativo y hunk de tres líneas de la presentación.
La revisión independiente de sólo lectura no encontró defectos concretos.
Otra revisión acotada comprobó el diagnóstico del 503 local del portal.
Los auxiliares no escribieron, no ejecutaron builds, no operaron Sites ni
usaron navegador. MAIN conservó el único rol escritor y operador del sitio.

No fueron necesarias correcciones funcionales. MAIN agregó el cierre al
documento operativo, revisó el diff completo y preparó staging exacto de los
siete archivos. `git diff --cached --check` pasó antes del commit.
El checkpoint web contiene siete archivos y 782 inserciones, cero eliminaciones.

## Validación fresca de MAIN

En website, Node 24.20.0 y temporales propios en `work/stage26-main/`:

| Comprobación | Resultado |
|---|---|
| Aportes + página pública + contrato UI | 22/22 |
| Suite completa | 64/64; cero fallos, omitidas o cancelaciones |
| Tipos, lint y `db:check` | Salida 0 |
| Build | Cinco etapas verdes; ruta nueva incluida |
| Diff y staging | Sin errores de whitespace |

No se ejecutaron `fixtures:beta`, `dev:beta`, instalaciones o cambios de
dependencias. Los tests D1/R2 usaron sus instancias efímeras. Los logs de MAIN
son evidencia local ignorada, no datos productivos ni artefactos publicables.
Los avisos informativos de Node experimental, clasificación de rutas Vinext y
normalización LF/CRLF no hicieron fallar la batería; no se cambió configuración
para silenciarlos. El cierre posterior sólo cambia documentación, no el código
o los tests que pasaron esta batería.

## QA visual y límites

- ES/EN, escritorio 1280×720 y móvil 390×844; reflujo adicional 640×360.
- Sin desborde horizontal en los tamaños revisados; contenido legible y
  disposición responsive comprobados en capturas locales.
- Cambio de idioma, foco conservado, Tab/Shift+Tab y salto que enfoca el `main`.
- Contraste computado mínimo 8,97:1; CTA 9,84:1.
- Movimiento reducido efectivo activo, cero animaciones y transiciones de 0 s.
- Recorrido portfolio → presentación → Aportes y regreso; ida/vuelta al portal.
- Consola consultada sin errores o advertencias de cliente.

No se acredita zoom real 200 %, transición emulada de movimiento reducido,
lector de pantalla ni traza independiente de red. Código, dependencias y DOM
no incorporan una conexión automática a Mercado Pago; esa auditoría no es una
captura de tráfico. Se aceptan estos límites para el cierre local acotado.

Al activar el enlace externo desde MAIN no apareció una nueva pestaña entre
las superficies controlables. Por eso la comprobación de la presentación del
proveedor y el referrer vacío continúa como evidencia heredada del especialista,
no repetida por MAIN. No se ingresó importe ni se avanzó un pago. No se accedió
a cuentas o credenciales. La captura aportada por Joaquin no acredita receptor
legal, costos o condiciones efectivos.

## El 503 no se ocultó ni se reparó fuera de alcance

Aportes respondió 200. Release respondió 503 con `CONFIGURATION_UNAVAILABLE`;
CSRF respondió 200. El portal mostró estado desconocido y formulario
deshabilitado. El arranque local común no tiene la configuración beta que
requiere `runtime.ts`; esa ruta y su configuración no cambiaron en el bloque.
No se atribuye una regresión a Aportes ni se afirma que la campaña esté cerrada
o falte una APK. No se crearon claves ni se reinicializó estado para forzar 200.

La disponibilidad real del portal en este servidor o en remoto no queda
verificada por esta entrega. La suite existente de API sí pasó aisladamente.

## Estado final y próximos pasos

Website quedó en `eae7d54`, staging vacío, con sólo tres modificaciones ajenas
(`app/globals.css`, `app/layout.tsx`, `app/page.tsx`) y ocho archivos ajenos
sin rastrear. No es un árbol limpio, sino la fotografía residual esperada.
El servidor propio fue detenido y el puerto 5186 quedó sin escucha.
MiGuardia recibe sólo este cierre y la coordinación documental; código Android,
Room, permisos, beta y dispositivos no se tocaron ni se revalidaron aquí.

No hubo push, publicación, nueva versión Sites, recursos remotos, pagos,
ramas/worktrees nuevos, tarea siguiente ni cambios en referencias protegidas.
El checkpoint documental de MAIN continúa la rama existente y la deja limpia.

Antes del bloque 27 corresponde cerrar con Joaquin receptor, costos,
condiciones y tratamiento de datos del aporte; autorizar la publicación y
comprobar HTTPS de `https://blackatsystems.com/miguardia/apoyar`. Sólo después
corresponde pedir el prompt de la entrada visible `Apoyar MiGuardia` en Android.
Este cierre no autoriza ninguna de esas operaciones ni inicia el siguiente bloque.
