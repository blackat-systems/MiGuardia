# Créditos del clima — cierre MAIN con corrección de retorno

Base de entrada: `db1d4591c74b7730560f04f43def3d2d8ebf2d2c`.
Fecha: 2026-09-07. Contrato: `docs/prompts/CIERRE_CREDITOS_CLIMA_V2.md`.

## Veredicto y revisión

**APROBADO CON CORRECCIÓN — cierre local y QA proporcional del bloque.**
No equivale a aplicación publicable ni a matriz física completa de distribución.
Integración funcional: `e3cb7409b1f9d598ab78a0d2fbe80cd9c5e8ddf1`,
`fix: complete weather attribution and preserve return navigation`.
Incluye exactamente 17 archivos app/tools. La documentación de cierre se
registra después; el commit no se presenta como otra ejecución de pruebas.

MAIN comprobó los catorce archivos del candidato contra el snapshot especialista,
revisó todos los hunks y archivos nuevos y encargó revisión independiente.
Créditos compartidos Open-Meteo/CC BY 4.0, enlaces conscientes en Clima y crédito
separado del pronóstico truncable en Widget y aviso. Se conservan privacidad y
estados sin datos; no se reimplementa Clima.

MAIN agregó evidencia visual, un ensayo supervisado de retorno y soporte
acotado del runner. Inventario final antes del checkpoint: 19 archivos,
15 rastreados modificados y cuatro nuevos; ninguno eliminado. Los cinco
archivos adicionales son el ensayo supervisado, runner/su regresión y
los documentos MAIN. Room, sus seis esquemas, fórmulas, manifiesto, Gradle,
permisos, website, versiones, confianza pública y claves quedan fuera del diff.

## Defecto reproducido y corrección

El crédito del aviso abría una segunda `MainActivity`: el propietario original
no quedaba reanudado, Clima no aparecía y fallaba el regreso al destino.
El ensayo sobre la Activity real registró dos instancias; diagnóstico de etapas,
conteos y booleanos, sin contenido laboral: `navigation-before-fix.json`.

Corrección: `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` **únicamente**
para `ACTION_OPEN_WEATHER` en el presenter, igual que la entrada existente del
Widget. No modifica las otras acciones, manifiesto o navegación global.
Revisión independiente favorable. El mismo ensayo posterior comprobó una sola
Activity, apertura de Clima, retorno a Ayuda y consumo único del destino
pendiente: `navigation-after-fix.json`.

## Validación local posterior al último cambio productivo

Evidencia ignorada: `.local-evidence/credits-clima-main-20260907/`.

| Comprobación | Resultado final |
|---|---|
| Perfil `local --fresh-tests` | 805 JVM: dominio 379, database 12, app 414; cero fallos, errores, omitidas o reutilizadas |
| Herramientas Python | 72/72 aprobadas |
| Catálogo QA | Válido: 102 clases, 16 grupos |
| Perfil `build` | BUILD SUCCESSFUL en 6m47s; 335 tareas: 24 ejecutadas, 311 vigentes |
| Lint Debug | 0 errores, 6 avisos de versiones disponibles |
| Artefactos | Debug, QA, Release unsigned y ambos AndroidTest compilados |

Resultados: `local-return-fix/result.json` y `build-return-fix/result.json`;
`changed_inputs` vacío. El perfil local no incluye interoperabilidad web;
no se atribuye una nueva ejecución de ese perfil ni de la auditoría del núcleo.

| APK final | Bytes | SHA-256 |
|---|---:|---|
| Debug | 18.912.305 | `b6ee6174af60b88cdb13ccecd42813db764e815b18d4c91e0cc1231245d26414` |
| QA | 18.813.598 | `b347d62f585eaa39019673fd36e628bd73c0485f8f487e8675134d59b0e75442` |
| Release unsigned | 12.750.497 | `2007f0c9928e45170263f08ca76fde6b7ddeb98fead6a57ddfc4bf9c63c8dbf9` |
| QA AndroidTest | 3.118.519 | `5f9a981b2487d90b2665ca3774165591e8337ee417addf955b94257a4ba25e17` |
| Database AndroidTest | 4.184.074 | `b6827a38006eb01adbc3b6af33460437bb194b93b415ddae9a5aac9413f9e315` |

QA conserva el certificado de pruebas SHA-256
`b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
La clave Release no se utilizó. Versión conservada: `2.0.0-beta.1`, código `2`.

## Samsung: pasada final sobre esos APK

Dispositivo autorizado: `R5CY529W6PL`, SM-S938B, API 36. QA preexistente y
QA.test ausente en la entrada. Reemplazo QA conservando datos, sin conceder
permisos por instalación. Hashes locales/instalados comprobados antes del ensayo.

| Clase | Aprobadas | Evidencia |
|---|---:|---|
| WeatherComposeTest | 12 | `.local-evidence/qa/20260907T060850.641226Z` |
| WidgetRemoteViewsInstrumentedTest | 9 | `.local-evidence/qa/20260907T060956.275341Z` |
| WeatherCreditsSessionInstrumentedTest | 1 | `supervised-060819.json` en el directorio MAIN |
| Total único | **22** | Cero fallos u omitidas |

El único método supervisado incluye cinco comprobaciones del presenter y tres
recorridos; no se suman como pruebas adicionales:

1. PendingIntent productivo del Widget → Clima → Ayuda.
2. Click en crédito del RemoteViews de aviso sintético → Clima → Ayuda.
3. Enlace CC BY 4.0 desde Clima real → Intent HTTPS exacto interceptado → Ayuda.

Se verificó el propietario de navegación, destino anterior y consumo único.
**No se abrió un navegador externo:** se acredita el Intent, no su retorno OEM.
No se declara tap sobre el panel de notificaciones ni un Widget del launcher.

Fallos anteriores separados como diagnóstico: la primera suite falló con el
Samsung Dozing y pasó con los mismos APK al desbloquear; otro candidato
reprodujo las dos Activities. Esos APK no acreditan la aprobación del APK final.

## Preservación y cierre del dispositivo

`credits-supervised` es exclusivamente manual; el runner rechaza ejecución
sin supervisión aun con los opt-ins. Único argumento nuevo permitido:
`creditsTemporarySession=authorized`. No ejecuta el setup/teardown mutante
de la suite completa del presenter.

El ensayo exige paquete QA, recuperación lista, Bloqueo abierto, configuración
/onboarding existentes, beta cerrada/vacía, permiso ya concedido y ausencia de
Activities/widgets/avisos. Usa un coordinador real temporal con emisor efímero
sólo de instrumentación, runtimes pausados y restauración del coordinador
original cerrado. No siembra jornadas/configuración, usa servidor/clave Release
ni añade autoridad productiva. Verifica huellas lógicas de las 27 tablas y
restaura registro beta, identidad Keystore, preferencias y canales visibles.
Los diagnósticos no conservan filas ni contenido laboral. Android puede retener
historial de un canal: no representa activación ni aviso vigente.

`device-before.json` y `device-closed.json` son iguales: conteos y hashes
agregados de archivos persistentes, bases y permisos. Se excluyen sólo
`profileInstalled`, `android.app.ActivityThread.IDS.xml` y SQLite shared memory;
la caché no integra la fotografía persistente. No quedan archivo beta
transitorio ni autoridad sintética. `beta-cleanup.json` y los marcadores
acreditan restauración antes de limpiar.

Estado final comprobado:

- QA instalada y detenida; datos conservados.
- Retirado únicamente `com.blackatsystems.miguardia.qa.test`; consulta exacta
  posterior sin paquete.
- Retirados 23 PNG sintéticos, marcadores propios y directorios propios vacíos.
  Evidencia: `visual-device-cleanup.json` y cierre supervisado.
- Producción no consultada ni modificada. Sin credenciales reales, clave APK,
  ajustes visuales Android, reboot ni alarmas exactas.

## Revisión visual y límites de aceptación

`visual-final/manifest.json` contiene 23 capturas sintéticas: doce del diálogo,
seis de resumen breve, cuatro de Widget y una de aviso expandido. Las 23 son
idénticas byte a byte a las imágenes válidas revisadas por MAIN. Créditos y
licencia legibles; crédito Widget separado del pronóstico recortable. Widget y
aviso inflados/dibujados por Android, no capturas del launcher/panel OEM.

La captura inicial del diálogo mostró fondo vacío y una segunda sólo el título.
**Ambas son evidencia inválida**, conservada como diagnóstico. La válida usa
raíz accesible QA activa y límites completos, sin otras apps ni datos reales.

Créditos legibles en ambos temas con zoom interno seleccionado en 100/150/200 %.
**No se acreditó ampliación geométrica del diálogo:** sus capturas fueron
idénticas por tema. El resumen breve sí presentó diferencias. La prueba no mide
la densidad efectiva del dueño Android del diálogo; no afirmar escalado 2×.

Pendientes de distribución: navegador/retorno OEM, TalkBack, launcher/panel real,
API 26/33 y ampliación efectiva del diálogo. MAIN los mantiene explícitos al
aceptar este checkpoint acotado; no son superficies físicamente cerradas.

## Git y continuación

Rama `codex/miguardia-2.0`, nueve worktrees preservados y referencias protegidas
sin movimiento. Diff sin errores. Sin fetch, push, tag, firma, recursos remotos
ni publicación. Release sigue unsigned y con confianza pública vacía.

La observación de Joaquin sobre crear lugar/horario desde la carga se registra
en STATUS como próxima dependencia separada; no implementada ni despachada.
La metodología MAIN/especialistas conservó revisión independiente, un operador
de compilación/dispositivo y evidencia local/física/distribución separada.
