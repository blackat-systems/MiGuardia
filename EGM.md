# MiGuardia 3.0 · Blueprint del recorrido residual B1

## System Overview

Este índice técnico derivado permite seguir la frontera aplicación → AndroidTest → recibo JSON → consumidor Python → conservación y cierre. No sustituye [STATUS](docs/STATUS.md), el [contrato B1](docs/prompts/STAGE_12_1_CIERRE_B1_3_0.md), [WORKFLOW](docs/WORKFLOW.md), [VALIDACION](docs/VALIDACION.md) ni las decisiones de producto. No documenta recursivamente la aplicación.

La aplicación Android/Kotlin/Compose tiene módulos `app`, `core/domain` y `core/database`, declarados en [settings.gradle.kts](settings.gradle.kts). Los residuales ejecutan componentes reales con datos sintéticos en contextos propios; el host valida observaciones y después compara la superficie recibida. Un recibo de fixture correcto no certifica por sí solo conservación de los datos recibidos, B1 ni rondas integrales.

## Interface · rutas de entrada

| Frontera | Contrato de componente | Define / produce / consume |
|---|---|---|
| Aplicación y productores Android | [Android residual](app/src/androidTest/java/com/blackatsystems/miguardia/stage12/EGM.md) | Siete selectores exactos, sesiones, readbacks, hechos y recibos de tres familias |
| Contratos compartidos y admisión local | [testSupport](app/src/testSupport/java/com/blackatsystems/miguardia/stage12/EGM.md) | Selectores, reloj de fixture, identidad de destino, namespace y publicación; pruebas JVM de esas fronteras |
| Operación y validación de resultados | [Host Python](tools/EGM.md) | Protocolo JUnit, validación tipada, custodia, exclusiones exactas, comparación B0/B1 y cierre QA/ADB |

El host residual vive en evidencia local preservada, no en `tools/`: [entrega r2](.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/DELIVERY.json), [plan](.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/execution-plan.json) y [contrato wire](.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/operator/receipt-contract.json). `tools/EGM.md` es su localizador; no implica traslado de archivos. Si falta evidencia local, usar el [localizador del archivo](.local-evidence/archive-location.json) exclusivamente para la dependencia faltante.

## Invariants · autoridad, base y corte comprobado

- Corte documental: 2026-11-04. Puerta 0 observada: este checkout, rama `codex/miguardia-3.0`, HEAD `7ba158854965f3eacc5a5702fc0ef5b49d948aca`, un worktree, remoto `origin` de MiGuardia en GitHub, sin cambios staged. El delta recibido incluye fuentes modificadas/nuevas, documentación y bajas por poda; no es un árbol limpio ni se reconstruyó desde HEAD. Se preserva íntegro, sin commit.
- MAIN conserva coordinación y aceptación; Stage 12.1 conserva implementación y operación exclusiva de Gradle/Samsung. Este bootstrap sólo escribe estos cuatro `EGM.md`; no emite admisiones ni modifica el checkpoint.
- Las entregas estables son la base de atribución. La [candidata aceptada](.local-evidence/stage12-1/main/calendar-residual-candidate-r1-review/final-review.json) conserva pareja QA `271ac69647cb6d668cb18fa25e8b1e9964f9764e66b2945648c240033c1f951f` / runner `384c20ef5621739d31758f29900d9a17d0569e013aeaabd1a3dc386e6f55b6b2`. r2 corrige Python; no exige recompilar esos APK. Las observaciones sobre otros cambios vivos no reemplazan esa atribución.
- Los esquemas Room, fuentes, contratos de autoridad y payloads sellados no se modifican para hacer pasar un ensayo. No se hereda la excepción `IDSCount` de C097 a otra operación.

## Evidencia y discrepancias al corte

| Evidencia estable | Qué está comprobado | Qué no acredita |
|---|---|---|
| [Replay aceptado](.local-evidence/stage12-1/main/edd-full-replay-review/final-review.json) | Lectura host retenida completa: seis bases, seis preferencias y 90 entradas | B0 actual, nueva ejecución Android o G |
| [C097 n3](.local-evidence/stage12-1/main/c097-n3-review/final-review.json) y [decisión humana puntual](.local-evidence/stage12-1/main/c097-n3-review/single-counter-human-acceptance.json) | Dos variantes institución; aceptación completada por excepción exclusiva del contador 6→7 | Igualdad estricta: permanece `DIFFERENT`; ninguna excepción futura |
| [Candidata residual](.local-evidence/stage12-1/main/calendar-residual-candidate-r1-review/final-review.json) | Once archivos, pareja compilada, 42 JVM de soporte reutilizados; manifiestos y ocho esquemas iguales | Esos JVM no prueban el cambio posterior de `WeatherRuntime`; tampoco instalación ni QA residual nueva |
| [Rechazo operador r1](.local-evidence/stage12-1/main/calendar-residual-operator-r1-review/final-review.json) → [entrega r2](.local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/DELIVERY.json) | r2 admite el wire real de Clima, exige tipos JSON recursivos y deriva siete raíces/22 miembros | La entrega dice preparada para revisión/admisión; no es aceptación nativa |
| [Lector delta r2](.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/consumer-delta-reader.json), [16 controles MAIN](.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/MAIN-controls-and-counterexamples.json), [contrapruebas corregidas](.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/MAIN-counterexamples-corrected-harness.json) | Correcciones conformes en el delta y controles locales registrados PASS, sin llamadas nativas | El primer informe extra comparte una referencia de fixture y contiene dos `accepted=true`; el informe corregido usa deepcopy y ambos rechazos pasan. Fue fallo del arnés, no nuevo defecto de r2 |

Durante la lectura cambió la revisión viva. La [revisión final r2](.local-evidence/stage12-1/main/calendar-residual-operator-r2-review/final-review.json), publicada a las 03:41:28 -03:00, acepta el operador: `ACCEPTED_LOCAL_OPERATOR_DISPATCH_BLOCKED_BY_ENVIRONMENT`, cero hallazgos de código pendientes. El [checkpoint](.local-evidence/stage12-1/dispatch/checkpoint.json) pasó a `R2_LOCAL_REVIEW_ACCEPTED_STAGE_NETWORK_PERMISSION_FAILURE`. Stage terminó con `Fatal error: application network permission was revoked`, según la captura exacta citada por MAIN. No demuestra un fallo de MiGuardia/Clima, ni estado de la red del teléfono, ni cese global de recursos. No hay admisión ni despacho nativo nuevo. La entrega r2 y sus 50 miembros conservan sus hashes; las conclusiones técnicas sobre esas entradas estables se mantienen.

La auditoría B1 consultada resume r1 y la sección «Punto exacto para retomar» del contrato B1 conserva la integración C097 anterior. Son desfasajes respecto de las aceptaciones posteriores enlazadas, no una orden de repetir C097. No se reescriben aquí sus fuentes de autoridad. Antes de operar se consulta el cursor vigente y cualquier sucesor del resultado final, sin repetir por rutina los controles aceptados.

## Side Effects · límites transversales

El ensayo preparado actualiza los dos paquetes QA, ejecuta siete instrumentaciones singleton, crea bases/archivos sintéticos y reportes propios y termina con comparación y cierre. Sólo Stage puede operarlo con la admisión exacta vigente. No consulta producción ni autoriza borrar datos recibidos. La custodia host lee originales preservados, trabaja sobre copias y mantiene primera negativa. El bootstrap no ejecutó ese programa, Gradle, ADB, instalaciones, limpiezas ni publicación.

## Continuación y condiciones de salida

1. MAIN: reutilizar la revisión r2 aceptada; resolver la capacidad de red de Codex y recuperar el mismo Stage y la titularidad/estado de los recursos interrumpidos. No inferir disponibilidad por el error ni abrir otro operador. Una vez recuperado el entorno y comprobada continuidad de identidades, emitir la admisión exacta nueva con pareja/plan/programa fijados, sin reconstruir APK ni repetir los 16/42 controles válidos por rutina.
2. Stage 12.1: recuperar el trabajo existente y recursos de la interrupción; una vez admitida esa operación, usar su lanzamiento exacto y ejecutar los siete residuales con conservación y cierre. No reconstruir comandos desde este documento ni reutilizar admisiones consumidas. La aceptación local de r2 no habilita el dispositivo por sí sola.
3. MAIN: aceptar únicamente evidencia atribuible. `PASSED` + `EVIDENCE_READY_FOR_MAIN` permite revisar cobertura; no cierra automáticamente B1. Primera negativa de método, lector, identidad o publicación detiene sucesores. `STOP` conserva la causa; incertidumbre de efecto/cierre produce `HOLD`, incluso si un cierre posterior retorna éxito. Detener sólo la operación afectada.

No se identificó un defecto nuevo del delta r2; sí se verificó el bloqueo de entorno registrado por MAIN durante esta lectura. Continúa abierto el trabajo ya autorizado: ejecución residual/conservación, C096 con muerte real de proceso, pasos humanos/OEM C032/A041/A042 y reproducción del fallo humano de Clima; B1 y rondas no se acreditan por esta documentación. Stage implementa/ensaya; MAIN acepta. Después del cierre auditado B1 se pausa antes de 12.2.

## Verification

Comandos existentes identificados, no ejecutados como pruebas del producto durante el bootstrap:

```powershell
& 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B tools/verify.py --profile local --fresh-tests
& 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -E -B .local-evidence/stage12-1/20261102-temporal-literal-continuation-r1/calendar-residual-operator-r2/test_program.py
```

El primero usa Gradle y queda reservado a Stage; el segundo es el arnés de 16 controles con fronteras externas bloqueadas, no el operador físico. El alias `python` del PATH de esta sesión deriva a Microsoft Store y falló; los ejemplos usan el runtime existente del recibo de controles, sin instalar nada.

La revisión documental `tools/docs_check.py --live-only`, ejecutada con ese runtime y `-B`, pasó: 211 referencias del catálogo activo, 37 ADR, sin recorrer snapshots ni acreditar respaldo. Este checker no descubre los cuatro EGM automáticamente; requieren además lectura completa y comprobación de enlaces/símbolos de cada archivo. Las comprobaciones documentales se separan de las evidencias históricas del producto anteriores.
