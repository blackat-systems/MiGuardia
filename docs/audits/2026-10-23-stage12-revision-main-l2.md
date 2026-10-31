# Stage 12 — auditoría MAIN de L2 y corrección de etiqueta B02

Fecha: 23/10/2026. Mismo chat `01a169e5-d914-7e33-8926-327a354301b3`.
Entrega local parcial recibida; no es cierre integral ni prueba Android.

## Evidencia contrastada

- L2 `c42fbe4442117ed3257669ca698b65b8c6e96be67f6d6cd6c0f6357408e3f1be`:
  MAIN rehasheó 1401/1401 archivos, 128.248.054 bytes, sin diferencias. Analizó
  109 XML y 995 nodos de prueba: dominio395, base19, app581; cero fallos,
  errores u omitidas. No repitió Gradle. Los build/APK/lint conservan su
  atribución de origen; cuatro de los cinco APK normales fueron reutilizados.
- Revisión independiente: 53/53 fuentes y 220/220 archivos core conservados;
  hooks, defaults, Gradle, validador host y captura conformes únicamente al arnés
  intermedio de tres métodos técnicos. No habilita Activities de negocio.
- Plan v3: 441 IDs, 506 subcasos, 1627 aserciones y dos rondas. Las siete
  aserciones W023 ya exigen lectura nativa de sus recursos propios antes/después;
  el cambio documental no ejecuta programación, entrega ni aceptación OEM.

## Hallazgo devuelto y comprobación de su corrección

MAIN confirmó que `NotificationScreens.restorableLabel` conservaba la fecha
propietaria y extremos sólo horarios. La lista local de avisos ocultados podía
mostrar el día anterior en un segmento reanudado y confundir intervalos de1h/25h.
No es la modalidad externa de privacidad Oculta.

El mismo Stage12 extrajo el consumidor y reutilizó `formatEventTimeRange` con
instantes y zona reales, conservando prefijos, owner, identidad y callbacks.
MAIN inspeccionó los tres archivos y el diff contra preimagen: sin otro cambio
funcional. Rehasheó 33/33 miembros y 768.367 bytes del delta, más55/55 fuentes
actuales. Recalculó el rojo de5 pruebas/4 fallas causales y el verde de44/44 en
seis suites, sin errores ni omitidas. No repitió una batería ya pertinente.

Clave: `Stage12:B02:20261023-b02-restorable:15363042692ea028`.
Recibos externos: `.local-evidence/stage12/main/l2-payload-review-r1.json` y
`b02-restorable-review.json`. Se recibió el cese de escritores y recursos;
el chat quedó idle antes de registrar esta revisión.

## Emulador y límites

G1 está recuperado/configurado, aún sin boot. G2 anterior avanzó su guarda de
identidad pero ADB salió antes del listener por hostname no soportado; cero
mensajes. El adverso se conserva, no se convierte en éxito.

La preparación nueva usa `-L tcp:5048`, con listener efectivo obligatorio
exclusivamente `127.0.0.1:5048`. MAIN y revisor contrastaron13/13 miembros y
referencias; MAIN repitió18/18 dobles sin SDK ni sockets. Se admite **un** G2
del servidor privado, dos mensajes fijos y cierre del hijo original, mediante
`avd-g2-listen-spec-retry-admission.json`. No concede boot, registro, selección,
instalación o instrumentación.

Android sigue **0/441 IDs**, sin rondas finales. Los APK anteriores no incluyen
el delta B02; la nueva candidata QA/Release no fue construida. B01 mantiene su
geometría nativa pendiente. El arnés con providers deshabilitados no acredita
arranque normal; ART/Emoji/FileProvider conservan su protocolo y puertas propias.
Samsung, OEM, voz, aceptación estética, causa histórica R4 y distribución siguen
separados. Sin app pública, datos personales, staging, commit, firma o publicación.

### Resultado posterior G2: comprobado, sin boot

La única ejecución consumió esa admisión. MAIN recibió relevo/turno terminado y
rehasheó38/38 miembros/118.780bytes, incluidos28 archivos del intento. Contrastó
cuatro guardas de argv/entorno/listener y cinco pruebas del par de archivos y
handle, PID10904/birth134372638894596925; dos solicitudes/completados exactos.
El parser ligado a la fuente acepta únicamente versión0029 e inventario BEFORE
vacío; las respuestas crudas no fueron archivadas. CLI0 y cierre deliberado del
hijo1 son resultados distintos, no un fallo ocultado. Postestado: tres archivos
del AVD iguales, puertos propios libres, hijo ausente y ADB previo sin cambios.
MAIN también contrastó55/55 fuentes actuales; no repitió servidor/SDK/baterías.

Clave `Stage12:G2:20261023-g2-listen-run:22ff17a99bddd181`; recibo MAIN
`.local-evidence/stage12/main/g2-real-review-g3-preparation.json`. La aceptación
es sólo G2, no Android. G3 conserva brechas concretas en pertenencia del motor,
endpoints y prevención de probes/fallback; el stub por sí solo no las demuestra.
Se admite preparar localmente un único operador de primer boot o devolver el
gate exacto, sin iniciar procesos/servidor/VM ni cambiar seguridad global.

### G3: gate recibido y auditado, no escape observado

La preparación terminó por `G3_PREBOOT_NATIVE_PER_VM_PREVENTION_UNPROVEN`.
MAIN rehasheó8/8 miembros/61.143bytes, cinco referencias/64.362bytes y55fuentes
actuales, sin cambios; revisión independiente conforme. Las ayudas truncadas y
fuentes upstream/antiguas no se atribuyen al binario instalado. La limitación es
falta de prueba bajo el requisito preventivo de MAIN, no un escape observado,
un nuevo bug del producto ni demostración de imposibilidad universal. Sin VM,
instalación o matriz Android. Turno/recurso/revisores en cese.

Recibo `.local-evidence/stage12/main/g3-gate-review-local-artifacts-admission.json`.
El bootstrap y operador G3 no están preparados; cambiar materialmente aislamiento,
seguridad, plataforma o autoridad requiere decisión expresa, no un workaround.

Resta una unidad local útil ya contemplada: los APK actuales no se construyeron
tras el delta B02. Se admite una compilación offline de QA/ejecutor normal,
Release sólo sin firma y lint del cambio, con insumos/metadata/recibos sellados,
sin repetir995 JVM ni instalar. El ejecutor normal tampoco es arnés aislado
admitido; compilarlo no habilita Android. Después MAIN audita esa entrega y
registra el gate si no queda otro progreso seguro útil.

Siguiente dueño: mismo Stage12, cierre de artefactos locales actuales. G3 sigue
sin habilitación. Rige el [contrato](../prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md).
