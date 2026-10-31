# Stage 9 — revisión independiente de MAIN

Fecha: 2026-10-21. Resultado: capacidad local auditada, incluida corrección R1.
No acredita migración Room ejecutada, restore Android ni actualización instalada.
Estado operativo y siguiente bloque: `docs/STATUS.md`.

## Evidencia contrastada

MAIN comprobó rama/HEAD, staging vacío y relevo del especialista. Consulta CIM
correcta: cero procesos Gradle/Kotlin. Se rehashearon las 992 rutas de salida;
delta inicial de once rutas y R1 limitado al test Python y las dos entregas.
Sin cambios productivos, esquemas/migraciones, dependencias, versión o firma.
La eliminación heredada continúa preservada. No hubo commit ni push.

Se contrastaron los 590 insumos comunes de local/build, los 104 XML preservados
y sus hashes: 952 pruebas ejecutadas, cero fallos/errores/omitidas; 395 dominio,
19 base y 538 app. Se rehashearon seis outputs y sus copias: tres APK app
reutilizadas, dos runners generados y lint nuevo. No se repitió Gradle.
También se verificaron adverso original JVM, guardas API/AVD intactas, modo V6
histórico sin cambios y hashes del export BIN/foto y del bridge host 27→29 tablas,
17 preferencias y una foto. Estos recibos no se convierten en prueba Android.

MAIN repitió 54/54 pruebas Python antes y después de R1. Un revisor interno
de sólo lectura contrastó el comparador y los esquemas, sin bloqueo funcional.
Evidencia propia: `.local-evidence/stage9/main-review/checks.json`,
`checks-r1.json` y los dos logs `comparator-tests*.log`.

## Hallazgo devuelto y resuelto

Los adversos secuenciales de INTEGER/REAL y texto/NULL/timestamp conservaban
mutaciones previas: el rechazo posterior podía quedar enmascarado. MAIN devolvió
el hallazgo al mismo chat Stage 9, sin otro implementador. R1 exige baseline
positivo antes de cada adverso y restauración comprobada después, también en
equivalentes. Sólo cambió pruebas Python y entregas; no cambió el comparador.

MAIN reprodujo independientemente los tres dobles que omiten REAL, NULL→texto
y timestamps: ahora cada uno provoca un fallo de aserción esperado, sin error
de preparación. No se cuentan como tres fallos del producto. El cierre pre-R1
y su evidencia permanecen intactos; la cadena R1 tiene su propio sello/relevo.

## Continuación autorizada y límites

Joaquín autorizó específicamente instalar QA/runner, ejecutar el diagnóstico y
reemplazar sólo datos ficticios descartables QA, preservando app pública y datos
personales. Tras retirar temporalmente el Samsung, confirmó directamente en MAIN:
«Ya traje el Samsung, está conectado, libre y desbloqueado. Terminá la auditoría
de Stage 9 y después seguí con el diagnóstico de Stage 8, sólo en QA, sin tocar
la app pública ni mis datos personales».

MAIN retoma el mismo Stage 8 con su contrato de diagnóstico, no un upgrade V6→V8.
Se seleccionan QA sin cambios y runner nuevo de Stage 9; sus hashes y paquetes
locales están en `docs/prompts/REANUDAR_STAGE_8_DIAGNOSTICO_SAMSUNG_3_0.md`.
El diagnóstico debe verificar identidad instalada/foco, conservar adversos y
separar ART de detector E2E. No autoriza optimizar producto sin causa demostrada,
usar el paquete público, cambiar ajustes globales o distribuir. La aceptación
visual, rendimiento pendiente y ensayo de actualización conservan sus puertas.
