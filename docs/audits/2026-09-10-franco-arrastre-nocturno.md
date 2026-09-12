# Franco tras arrastre nocturno — 2026-09-10

## Regla confirmada por Joaquin

Una jornada que comienza ayer y termina hoy a las 07:00 no impide marcar hoy
como Franco. La fecha de inicio determina la pertenencia al Calendario. No se
recorta, elimina ni modifica la jornada o sus horas.

## Corrección

Base: `7b691f847269e6410ba6168a4c2e65e0868d6738`.
`RoomExplicitDayStatusRepository.updateDaysOff` consulta las jornadas por
`localStartDate` mediante el DAO existente. Mantiene la transacción, comprobación
de timeline y rechazo completo del lote si hay una jornada PLANNED que comienza
en una fecha seleccionada. Sin cambios de esquema, permisos ni dependencias.

Regresión en `DayOffPersistenceInstrumentedTest`: jornada 22:00→07:00, Franco
al día siguiente y comparación exacta de la jornada antes/después. Un segundo
lote incluye el día de inicio y comprueba rechazo sin escrituras parciales.

## Evidencia ejecutada

- Antes del cambio productivo: Samsung R5CY529W6PL/API36, seis pruebas aprobadas
  y fallo exacto de la nueva regresión. Registro:
  `.local-evidence/qa/20260910T150846.477774Z/`.
- Después: siete aprobadas, cero fallos, omisiones o pendientes. Registro:
  `.local-evidence/qa/20260910T151315.150385Z/invocation-001.json`.
- Compilación y pruebas unitarias database completadas; repetición de comprobación
  `testDebugUnitTest assembleDebugAndroidTest`: BUILD SUCCESSFUL, 77 tareas
  UP-TO-DATE. Esta repetición no se presenta como ejecución fresca de unitarias.
- Catálogo QA actualizado para la regresión: 108 clases/19 grupos.

La instrumentación utilizó exclusivamente la base ficticia propia del paquete
`com.blackatsystems.miguardia.core.database.test` en usuario0. Ese paquete fue
retirado y su ausencia comprobada. No se reemplazó ni consultó la app oficial,
no se borraron datos suyos y no se cambiaron ajustes del Samsung.

## Límite de entrega

Joaquin autorizó posteriormente compilar, firmar, dejar la APK en el Escritorio
y publicar el parche. Versión congelada: 2.0.2/code6.

- Build completo aprobado; 844 JVM ejecutadas, cero fallos/errores/omitidas.
- Repetición Samsung con el AndroidTest final: 7/7, evidencia
  `.local-evidence/qa/20260910T153212.309708Z/`. Paquete de pruebas retirado
  del usuario0 y ausencia comprobada; app oficial intacta.
- Dos reconstrucciones con iguales insumos y SHA unsigned
  `800732623a7c2cec24465833f569633e88521eec5fef7b5aefb244a73ba507ee`.
  La primera dejó hash registrado; se conservaron los bytes de la segunda.
  La segunda compilación Release terminó con 118 tareas ejecutadas y salida0;
  el runner la rechazó al exigir un productor Debug fuera del alcance acotado.
  Se conserva ese fallo; aceptación separada en `release-acceptance.json`,
  sin repetir ni atribuir nuevas compilaciones.
- Joaquin firmó en consola privada. Verificador separado:
  `SIGNED_ARTIFACT_CHECKS_PASSED`, cero errores.
- APK firmada: 12.746.061 bytes, SHA-256
  `be3bca5354d227792ff9a30d11b44777e3dfeef30d0943ed3f99f8dd7caa2f94`.
  Copiada sin sobrescribir a `C:/Users/Joaquin/Desktop/MiGuardia-2.0.2.apk`;
  hash de la copia comprobado.

Evidencia de preparación/firma: `.local-evidence/release/franco-code6/`.
Joaquin subió el archivo a Drive (`1xGUtM2oCI1ZpqXZlxwEO4Oewweoj0TAT`)
y autorizó publicación, commit y push. Descarga anónima completa comprobada:
12.746.061 bytes y SHA firmado exacto. La primera URL de Drive devolvió su
página de confirmación; no se trató como APK. El endpoint fijo del adaptador
existente entregó los bytes correctos, sin cambios al transporte.

Web: 174/174 pruebas y perfil compilado. Commit de catálogo y regresión:
`2adf12784b17385b514fd2e7ff3a31d5467a9ceb`, enviado al repositorio de Sites.
Sites v15, deployment `appgdep_6aa2d04732b48191b50d4631d5baa044`, succeeded.
Se verificaron página HTTPS200, API AVAILABLE/code6, descarga completa desde
`blackatsystems.com` con tamaño/hash exactos y API beta410.
Evidencia web: `website/work/public-publication/code6-remote-verified.json`.

No se acredita instalación/upgrade de code6 en la app oficial. La distribución
del parche está cerrada; la prueba Samsung fue instrumentación aislada.
