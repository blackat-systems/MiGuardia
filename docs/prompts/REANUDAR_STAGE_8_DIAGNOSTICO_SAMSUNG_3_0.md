# Reanudar el diagnóstico Android de Stage 8

## Cierre y límite posterior

q2 ejecutó el diagnóstico físico autorizado. Por instrucción posterior de Joaquín,
q3 sólo analizó copias locales, sin ADB, QA nueva ni producto. MAIN recibió su
sello `065b60…123e6` y auditó el cierre: causa diferencial R4 INCONCLUSA.
No reejecutar este contrato ni inferir disponibilidad actual del Samsung.
El experimento ABBA es sólo propuesta; requiere otro stage, contrato y permiso
específico antes de ejecutarse. La autorización de retorno cubre avisar a MAIN
con clave/payload y recibir su acuse, no ampliar el ensayo. Estado único en STATUS.

## Permisos agrupados de entrada

- CONCEDIDO: ADB, instalación conservadora QA/runner y diagnóstico con sustitución
  exclusivamente de datos ficticios descartables QA. Permiso específico de Joaquín
  preservado en MAIN; no volver a pedirlo sin cambio de alcance.
- PENDIENTE: ninguno adicional previsto dentro de ese alcance. Producto, upgrade
  instalado y distribución están excluidos, no se autorizan por este ensayo.
- HOLD/disponibilidad: rige STATUS. La pausa posterior al probe se respetó; Joaquín
  informó disponibilidad nuevamente a las 05:30 UTC. Esperar el relevo documental
  MAIN y revalidar foco/estado al retomar; no confundir permiso con disponibilidad.

Ninguna concesión elimina guardas ni controles obligatorios. El mismo operador
resuelve decisiones técnicas rutinarias y reversibles; escala sólo cambios materiales.

## TASK

Observar en Samsung las fronteras de `open_summary` y `save_three_shifts` con
el harness ya auditado de Stage 8. Identificar evidencia causal sin confundir
muestreo instrumentado con rendimiento E2E. No implementar correcciones de
producto hasta demostrar la causa y revisar el alcance.

## CONTEXT

Este archivo no concede permisos por sí solo; autorización y despacho viven en
STATUS. Joaquín autorizó específicamente instalación QA/runner, diagnóstico y
reemplazo de datos ficticios descartables; luego confirmó directamente en MAIN
que el Samsung volvió, libre y desbloqueado, y pidió continuar tras la auditoría
Stage 9. Verificar disponibilidad real al ejecutar; no heredar permisos de otro ensayo.

Este ensayo es independiente del bloque local de conservación V6→V8. No debe
competir con otro operador por checkout, Gradle, artefactos o dispositivo.
Consultar `docs/STATUS.md` antes de actuar; si otro especialista está escribiendo
o compilando, esperar su relevo o acordar una frontera estable con MAIN.

## INPUTS

Repo `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`,
rama `codex/miguardia-3.0`, HEAD
`932eaeb60c6b6e68975534788e3e6501c967b1ae` más checkout acumulado preservado.
Leer AGENTS/STATUS, `docs/VALIDACION.md`,
`docs/HANDOFF_STAGE_8_A_MAIN_3_0.md`,
`docs/audits/2026-10-21-stage8-revision-main.md` y plan físico de la auditoría
`docs/audits/2026-10-21-stage8-rendimiento-resumen-y-guardado.md`.

Artefactos auditados en `.local-evidence/stage8/artifacts/manifest.json`:

- QA `com.blackatsystems.miguardia.qa`, SHA-256
  `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
- Runner QA SHA-256
  `7eb513f3bece83fa330dac34e8a884c0223d90f18575764574a32f6fea1a8acc`.
- Harness `Stage8BoundaryDiagnosticsTest`, SHA-256 fuente
  `ecfd15e18339901688e12c3c460c61e3721d83042a9467a620fcc04c747ce14e`.
- Inspector local `.local-evidence/stage8/inspect_diagnostic.py` y 38 controles
  stdlib, sólo de parser. No interpretan los registros binarios ART.

Si se modifica algún insumo pertinente después del sello, no reatribuir el build
anterior: seleccionar conscientemente una candidata/runner coherentes y registrarlo.
No instalar un APK por su nombre sin verificar manifiesto, paquete y hash.

Selección de MAIN tras auditar Stage 9/R1, sin sobrescribir la identidad histórica
anterior: usar los outputs actuales y sus copias preservadas Stage 9.
QA conserva `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`;
runner app nuevo `40a24ac4a5789d3e53caf0b81c2940679d4db41b6324559def9e95e06d715472`.
El harness Stage 8 y la app no cambiaron; el runner incorpora las nuevas pruebas
test-only de Stage 9. Compilación: `.local-evidence/stage9/build/result.json`;
contraste independiente: `.local-evidence/stage9/main-review/checks-r1.json`.
Paquetes locales comprobados con aapt2: QA y `com.blackatsystems.miguardia.qa.test`,
instrumentación `androidx.test.runner.AndroidJUnitRunner` dirigida sólo a QA.
Reverificar fuentes, artefactos y guardas antes de la instalación. No restaurar
outputs antiguos encima de éstos para forzar coincidencias.

## OUTPUT

Recibos nuevos de instalación y ejecución exclusiva QA, hashes, configuración
observada, JSON y trazas privadas sintéticas extraídas binariamente, inspección
de integridad y observación causal limitada. Handoff con resultados, fallos,
causa comprobada o incertidumbre, estado final y siguiente acción segura.

## SCOPE

Sólo QA y runner técnicos. El harness reemplaza base y preferencias QA por datos
ficticios descartables: registrar la consecuencia y el permiso específico ya concedido.
No tocar app pública, copias personales, galería, mensajes ni otra aplicación.

1. Puerta 0 local, consentimiento registrado y serial explícito; comprobar foco
   QA/Inicio y desbloqueo sin leer texto/capturar otras apps. Ante duda, parar.
2. Rehashear artefactos; instalación conservadora QA/runner y cotejo instalado/local
   con controles soportados ya revisados. No desinstalar ni limpiar paquetes.
3. Revisar `python tools/qa.py --group stage8-boundaries` como plan. Sólo tras
   permiso real, agregar `--execute --authorized-device-use --serial <serial>
   --allow-qa-mutations --allow-disposable-data`. Un proceso por selector.
4. Ejecutar representative y stress, guardando también parciales/fallos. No
   eliminar máximos, muestras, iteraciones ni modificar la fixture para aprobar.
5. Extraer sólo los artefactos `files/stage8/<run>` de QA con transporte binario;
   no redirigir `.trace` mediante texto PowerShell. Rehashear entrada y copia.
6. Ejecutar el inspector sobre copias locales, con output nuevo. Rechazar falta
   de trazas, overflow, estados incompletos o brackets incoherentes. Un manifiesto
   estructural correcto no certifica registros binarios ni causalidad.
7. Observar pilas en visor ART compatible y separar navegación, espera/proyección,
   composición, preparación de guardado, transacción/asociados y propagación.
   Las fronteras no son costos exclusivos sumables. Ausencia de una pila no
   demuestra costo cero. Registrar overhead y limitaciones.
8. Si la causa se demuestra, devolver la evidencia para otro stage de corrección
   con contrato propio; no implementarlo dentro del diagnóstico. El E2E posterior usa detector original pareado,
   mismas condiciones/datos/muestras/umbrales, sin trazar dentro del detector.

## DEPENDENCIES

Autorización específica de Joaquín para instalación QA, ADB/ejecución y sustitución
de datos ficticios QA; Samsung disponible y desbloqueado; artefactos coherentes;
único operador. No hay autorización de distribución ni de modificar producción.

## DO NOT

No APK pública, datos personales, emuladores, cambios globales Android, claves,
firma, publicación, permisos silenciosos, limpieza/desinstalación, cambios de
modelo/esfuerzo, ramas/worktrees o commits. No crear otra implementación ni
reactivar Stage 6. No ejecutar este plan sólo por estar conectado el teléfono.
Ante foco ajeno, detener entradas/capturas; no operar esa aplicación.

## VALIDATION

Identidad instalada/local, guardas de foco, consentimiento y estado de ejecución
registrados. Separar prueba funcional de validez del muestreo y de comparación
de rendimiento. Preservar baseline Stage 5, R4, detector y recibos previos.
No repetir JVM/build sin cambio relevante. Documentación y diff del alcance.

## DONE WHEN

MAIN recibe observaciones trazables y límites explícitos; QA queda en estado
seguro registrado, sin tocar datos ajenos. Si la causa no se demuestra, informar
la observación adicional necesaria, no una solución inventada. Medir no acepta
Vigilia ni autoriza actualizar la app pública o distribuir MiGuardia.
Entregar respuesta final propia con delta, pruebas/resultados/evidencia, pendientes
y próximo dueño. Confirmar cese de escrituras/Gradle/dispositivo o recurso activo.
MAIN recibe por su supervisión existente y contrasta antes de otro bloque; no crear otra.
Tras el sello y con retorno autorizado, avisar efectivamente a MAIN con chat/turno,
clave stage/fase/sello y rutas/hashes; MAIN registra RECIBIDO antes de auditar.
Reutilizar el acuse por clave, no duplicar ejecución. Un envío aceptado no prueba recepción.
