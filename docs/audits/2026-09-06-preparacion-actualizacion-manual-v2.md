# MAIN — Preparación de Actualización manual sin perder datos

- Fecha: 2026-09-06.
- Veredicto: contrato preparado; no implementación ni publicación.
- Pedido: Joaquin solicitó el prompt y el handoff para la siguiente dependencia.

## Base viva

Puerta 0 de MAIN comprobada antes de editar:

- Checkout `C:/Users/Joaquin/Desktop/chatgptprojects/MiGuardia-2.0`.
- Rama `codex/miguardia-2.0`.
- HEAD `40f2c55418a407751bd2afe9379262b0758baa44`.
- `feat: add MiGuardia support menu entry`.
- Checkout limpio y staging vacío; nueve worktrees preservados.
- Upstream `origin/codex/miguardia-2.0`, `924fd46df018f5e14637dfe90a983fefdbf27af4`;
  nueve adelante, cero atrás antes del nuevo checkpoint documental.
- Autor `joaquin <blackat.systems@gmail.com>` y origin privado habitual.
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`, intactos.

El bloque 27 ya estaba integrado. Su auditoría conserva 755/755 JVM y 32/32
casos físicos dirigidos. Esa evidencia no se atribuye al actualizador futuro.

## Inspección y decisiones

MAIN aplicó `joa-main-orchestrator` para estabilizar la frontera antes de
delegar. Se contrastaron ADR 0039, estado y secuencia con BetaCoordinator,
BetaIdentity, BetaProtocol, BetaStore, transporte, arranque, manifest y Gradle.
Una revisión auxiliar independiente, sólo lectura, identificó la necesidad
de una frontera beta tipada y la diferencia entre permiso de uso y de descarga.

El website fue únicamente fuente de lectura en HEAD
`80ebafd9945f51b13b05c708eec50c22fdf0a52b`. Sus cambios preexistentes de
portfolio, AGENTS, tarballs e imágenes quedaron fuera del alcance. Los contratos
API v1 y operaciones conservan los hashes registrados en el prompt. Se leyó
la implementación real de desafío, autorización, firma y descarga.

Se eligió descarga completa acotada, comprobación privada y guardado SAF;
instalación posterior por la persona desde Archivos. No se requiere permiso
de instalador, FileProvider adicional o cambio de backend. ADR 0040 registra
el paso extra y los límites de lo que se puede verificar antes de instalar.

No se confunde feedback en cola con recibido. No se reutilizan autorizaciones
de sesiones QA anteriores. El cliente local no necesita que la autoridad beta
esté publicada; no puede inventarla o insertar confianza sintética en Release.

## Archivos del checkpoint documental

Exactamente ocho rutas, sin código funcional:

1. `docs/STATUS.md`.
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`.
3. `docs/PLANIFICACION_MIGUARDIA_2_0.md`.
4. `docs/prompts/README.md`.
5. `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.
6. `docs/prompts/ACTUALIZACION_MANUAL_SIN_PERDER_DATOS_V2.md`.
7. `docs/adr/0040-actualizacion-manual-verificada-sin-instalador.md`.
8. Este registro de preparación.

El SHA del checkpoint se comunica en el mensaje de despacho de MAIN, evitando
una referencia circular dentro del propio commit. Su padre debe ser exactamente
`40f2c55418a407751bd2afe9379262b0758baa44` y su diff únicamente esta lista.

## Validación de preparación

Revalidación dirigida observable:

```powershell
$env:MIGUARDIA_BETA_WEBSITE='C:/Users/Joaquin/Desktop/chatgptprojects/website'
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 :app:testDebugUnitTest `
  --tests '*BetaProtocolTest' --tests '*BetaCoordinatorTest' `
  --tests '*SupportMenuActionTest'
```

`BUILD SUCCESSFUL in 38s`, 55 tareas actualizadas. XML disponibles de las tres
suites: 11 + 18 + 4 = 33 pruebas, cero fallos, errores u omitidas. Esta pasada
reutilizó resultados vigentes: no se declara una batería global fresca ni
ejecución Android. No se modificó código desde el checkpoint funcional.

Comprobaciones documentales: secciones obligatorias presentes, plantillas sin
rellenar cero, whitespace final cero en documentos nuevos y `git diff --check`
verde. Contratos web con SHA-256 idénticos y estado ajeno preservado. Sólo las
ocho rutas documentales indicadas integran la preparación.

Revisión independiente final del prompt y ADR 0040: sin hallazgos bloqueantes
ni defectos contractuales concretos. Se contrastaron endpoints, campos, prueba
canónica, vencimientos, cola/recepción, alcance Android y la viabilidad de las
pruebas. Fue sólo lectura; no acredita firma, instalación ni comportamiento
físico del cliente todavía inexistente.

No se ejecuta la implementación del bloque 28 ni se atribuyen pruebas compiladas
a ejecución física.

## Puertas pendientes

Joaquin abre el nuevo chat. El especialista hace Puerta 0, implementa un único
candidato y entrega el diff sin commit. MAIN audita y decide la integración.
No se creó una tarea especialista ni se transferió todavía el rol escritor.

Samsung/API 26/33/37 y cualquier instalación necesitan autoridad nueva. Versión,
firma Release, confianza pública, infraestructura, APK oficial, publicación,
push, tags y producción siguen separados. No se usaron dispositivos, no se
escribió el sitio y no se hizo push en esta preparación.
