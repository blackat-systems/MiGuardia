# MAIN — cierre documental de página pública y preparación del acceso beta Android

- Fecha: 2026-09-04.
- Alcance actual autorizado: preparar el prompt de la dependencia 25 y
  reconciliar la documentación con la página pública ya integrada.
- Veredicto: página pública integrada localmente; bloque Android habilitado
  mediante contrato, **todavía no implementado**.

## Página pública: evidencia conciliada

La dependencia entregó el candidato de `/miguardia` sobre
`website/b66c1f79c247872cf61a30db3af2274606956d30`. MAIN lo integró en
`2799c473877acff2f280759d6a3d398c9f39a455`, mensaje
`feat: add public MiGuardia product page`, con siete archivos propios y sin
incorporar los cambios preexistentes del portfolio.

El handoff posterior distinguió correctamente sus pruebas anteriores al commit
de la corrección de foco agregada por MAIN. Esa corrección ya fue comprobada
por MAIN sobre el código integrado: contención del foco del selector inicial,
Tab, Shift+Tab, Enter y continuación al contenido. No corresponde recrearla ni
tratar las pruebas del candidato original como ejecución del código posterior.

Evidencia local ya producida por MAIN en la integración/revalidación precedente:

- 55/55 pruebas del sitio, typecheck y lint verdes sobre `2799c47`;
- build local verde al integrar la corrección;
- prueba regresiva del foco y recorrido de teclado comprobados;
- el intento inicial de ejecutar directamente Node sin la opción de transformación
  de tipos falló; se utilizó después el script correcto `npm test`, que pasó;
- no se modificó el contrato, cupos, claves o API al integrar la página;
- no se publicó ni se comprobó nuevamente el estado remoto.

Esta preparación documental no repitió el build ni el recorrido de navegador.
Conserva las limitaciones conocidas: reflujo por viewport CSS equivalente, no
indicador independiente de zoom real al 200 %, y sin auditoría completa de lector
de pantalla. Se aceptan como límites explícitos de la evidencia local; no son
pruebas positivas inventadas ni una afirmación de conformidad exhaustiva.

## Puerta 0 de la preparación

MiGuardia:

- Ruta `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama `codex/miguardia-2.0`.
- HEAD `2bc803b24625c3e63fd4efaec6c6daae0e08e681`.
- Upstream `origin/codex/miguardia-2.0` en
  `924fd46df018f5e14637dfe90a983fefdbf27af4`; 2 adelante, 0 atrás.
- Árbol inicial limpio, staging vacío, autor `joaquin <blackat.systems@gmail.com>`.
- Nueve worktrees históricos preservados.

Website:

- HEAD `2799c473877acff2f280759d6a3d398c9f39a455`, rama `main`, sin remoto.
- Staging vacío; modificaciones ajenas conservadas en `app/globals.css`,
  `app/layout.tsx` y `app/page.tsx`.
- Tarballs, AGENTS e imágenes no seguidos permanecen fuera del alcance.
- En esta preparación el sitio se utilizó sólo como fuente de lectura.

## Contratos preparados para la dependencia 25

Prompt durable:
[Acceso a la beta y opiniones](../prompts/ACCESO_A_LA_BETA_Y_OPINIONES_ANDROID_V2.md).

MAIN leyó las fuentes obligatorias y el contrato API v1/operaciones del sitio.
Dos auxiliares independientes, de sólo lectura, revisaron respectivamente
protocolo web e integración Android. No implementaron código, generaron claves,
ejecutaron Gradle ni usaron dispositivos.

Un auxiliar realizó después una revisión independiente del prompt escrito
contra API v1 y ADR 0039, sin modificarlo: no devolvió hallazgos concretos.
La autorización física acotada se respalda en las instrucciones expresas de
Joaquin de esta conversación: autorizó QA Samsung y después concedió permiso
para las próximas instancias. No se deduce de que el teléfono esté conectado.

El prompt fija especialmente:

- los dos POST exactos, comprobantes P1363, canonicalización y prueba de posesión;
- diferencia entre identificador de transporte y de idempotencia;
- reutilización de la identidad Keystore ante una respuesta de activación perdida;
- paquete, certificado y versión compatibles sin romper actualizaciones futuras;
- quince días por instante UTC firmado y cola durable antes de permitir continuar;
- recepción remota comprobada distinta de un formulario sólo guardado;
- recuperación, bloqueo, beta, configuración y onboarding como puertas distintas;
- protección de avisos, Widget y actividad exportada, no sólo de Compose;
- claves de ensayo y confianza por variante sin bypass productivo;
- Room V6 y 17 preferencias de Copias intactos;
- autorización Samsung vigente acotada a QA ficticia; otras operaciones sensibles
  conservan sus puertas, sin inferir autorización de una simple conexión.

Se verificaron fuentes oficiales de Android para Keystore, firmas y callbacks
de conectividad. La única ampliación de manifiesto permitida es el permiso
normal `ACCESS_NETWORK_STATE` si la implementación usa ese callback acotado;
no se agregó ningún permiso ni código en esta preparación.

No se necesita provisionar producción para implementar el cliente local. La
ausencia de públicas productivas debe dar un estado cerrado de configuración,
no una autoactivación. El contrato no autoriza diez claves de producción, firma,
publicación, donaciones o actualización de APK dentro de este bloque.

## Documentación reconciliada

- MAPA, PLANIFICACIÓN y STATUS: página pública cerrada localmente y bloque 25
  habilitado; no se altera la evidencia histórica del núcleo.
- Índice: 24.a cerrado; 25 habilitado, todavía no implementado.
- Prompt 24.a: conservado como contrato histórico con aviso de cierre y evidencia.
- Prompt 25: entradas reproducibles, alcance, pruebas, handoff y condiciones de cierre.

El punto de entrada ejecutable de la nueva dependencia será el checkpoint que
agrega su prompt; el documento exige su primer padre exacto y diferencia sólo
documental. MAIN entrega el SHA resultante junto al mensaje para la tarea.
No se intenta escribir dentro de un commit su propio identificador.

## Validación y límites de esta preparación

La validación propia de esta modificación es documental: consistencia de
contratos, estados, rutas Markdown, refs Git, alcance del diff y whitespace.
Resultado: siete documentos dentro del alcance, referencias locales resueltas,
sin whitespace final ni errores de `git diff --check`. Las tres referencias
protegidas permanecen en `82db6fd8eb2c511205968894dc9857a96b16ed20`.
No cambia producción; no corresponde atribuirle una batería Gradle ni QA física.
No se ejecutó ADB, no se instalaron aplicaciones, no se generaron secretos y no
se realizó ninguna operación remota.

La implementación Android, su interoperabilidad ejecutada y la futura conexión
con el servidor remoto siguen pendientes. Esta preparación no declara la beta
lista para descarga, no abre otra tarea automáticamente y no autoriza un push.

## Próximo paso

Entregar a Joaquin el prompt completo y el SHA del checkpoint documental para
que cree la dependencia **25 — Acceso a la beta y opiniones**. Esperar su handoff
antes de avanzar al aporte voluntario o a las actualizaciones.
