# Stage 10 — revisión MAIN del ensayo en emulador

Fecha local: 22/10/2026. Resultado: **entrega auditada, ensayo Android incompleto**.
Estado vigente: [STATUS](../STATUS.md). No equivale a conservación aprobada.

## Autoridad y recepción

Joaquín autorizó instalar emulador, habilitar aceleración y adaptar pruebas para
Stage10; confirmó aceptar UAC. El [contrato](../prompts/STAGE_10_ACTUALIZACION_QA_Y_CONSERVACION_ANDROID_3_0.md)
limitó la ejecución a un AVD dedicado, sin Samsung ni QA actual. Mismo chat
`01a15d55-500a-7162-be24-f4ed70e990a5`, turno inicial
`01a16631-915d-7f63-abba-d98b5590acf8`; clave `stage10-ensayo-emulador-20261022`.

Entrega en `.local-evidence/stage10/emulator-rehearsal-20261022/`, sello
`c605303cb7190d1006920ba953f193cfa375c990f632347957fb2a9ed51a62ea`.
MAIN acusó recepción antes de auditar. Recibos independientes en
`.local-evidence/stage10/main/emulator-rehearsal-review-20261022/`.

## Comprobaciones independientes

- Rehash de **578/578 archivos**, 68.879.982 bytes, sin diferencias. Manifiesto
  `b3ea9b5978795a950831e6542b9fcd768b547ddf0c03efdbca8bf49da03db177`.
- Entrada: 1013 rutas; 1011 intactas, dos pruebas Android modificadas y cinco
  fuentes test-only nuevas. Ninguna ruta perdida; HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`
  y staging vacío. Sin cambios productivos, Gradle, esquema o dependencias.
- QA6, QA8 y runner preservados coinciden con sus hashes. QA6 `1c0d0be8…3148a`,
  QA8 `2e00ab12…758a3`, runner `d71a4056…dc8e`; valores completos en los recibos.
  Siete metadatos de AVD anteriores siguen idénticos.
- Revisor independiente contrastó 107 pares de comandos/resultados: todos con
  endpoint local y serial `emulator-5584`; dos instalaciones iniciales, una
  instrumentación `seed-v6`, ninguna instalación QA8, clear, uninstall o reseed.
- MAIN abrió la copia SQLite sólo lectura/inmutable: Room6, 27 tablas y 40 filas;
  17 preferencias, diez cuerpos del ZIP sin diferencias. La captura es semilla
  anterior a la guía: `onboarding_completed=false`, no baseline posterior.
- Recibos del especialista: 22/22 JVM y runner compilado; Python final original
  22/22. MAIN repitió proporcionalmente 16 pruebas iniciales, dos de configuración,
  dos de continuidad y tres finales de navegación; no rehízo Gradle ni Android.
  Son verificaciones de revisiones sucesivas, no una batería global nueva.
- SDK: emulator 37.2.12 y AOSP API 36/x86_64 revisión 2 instalados. WHPX funcional
  confirmado por `-accel-check` y arranque. La aceptación UAC no acredita por sí
  sola éxito del helper elevado: falta ese recibo y no se lo declara exitoso.
- Consulta Windows de MAIN: sin procesos propios del AVD ni puertos 5584/5585
  escuchando. Userdata preservada; no se afirma hash integral del disco.

## Hallazgos, corrección y límites

Las guardas iniciales detuvieron normalización INI, ruta del ejecutable y controles
fuera del viewport. Se corrigieron causas test-only y continuó la misma instalación,
sin otra semilla. Los XML muestran once toques de guía y luego Hoy/FIC/mes;
no acreditan grilla completa ni persistencia de onboarding posterior.

La última reanudación fue rechazada el 23/10 a las 01:29:35 UTC, antes de proceso
o ADB: faltaba el margen preventivo de 150 s. No había vencido todavía el caso
corto de 01:32 UTC; el baseline exigía captura anterior a 01:31 UTC. Al cierre esos
plazos ya pasaron. No se cambió reloj, escenario ni evidencia para aprobar.

MAIN reprodujo además un falso positivo local: siete días/contexto previo podían
aceptarse con el aviso accesible de recorrido activo y sin título/botones visibles.
El mismo Stage10 corrigió ambas guardas y agregó regresión. Corrección separada:
`.local-evidence/stage10/emulator-rehearsal-correction-20261022/`, sello
`b46edcb4368676f89d04c15773b8fd013aff77ee7f4f3e6bbb858fcdfdaea431`.
MAIN rehasheó **15/15 archivos** y repitió las tres regresiones afectadas: **3/3**.
Especialista: **23/23 host**. Delta correctivo sólo coordinador y prueba; original
sellado intacto. No se ejecutó esa corrección en Android.

También se preservaron los tropiezos de lectura de MAIN: bloqueo transitorio de
un log vacío, consulta SQLite en memoria incompatible con su cabecera WAL e import
de pruebas sin su directorio. Se resolvieron sólo en lectura/entorno de proceso;
los recibos finales no los presentan como fallos de producto ni pruebas Android.

## Decisión y siguiente paso

IMPLEMENTADO: entorno y herramientas test-only. COMPROBADO: identidad/semilla y
validación local descritas. PENDIENTE: baseline posterior a guía, actualización
real QA6→QA8, apertura de Calendario, recaptura y comparación de conservación.
No hay evidencia de pérdida de datos en una actualización que no se ejecutó.

El AVD `MiGuardia_Stage10_API36_20261022` queda apagado y preservado bajo
`C:/Users/User/.cache/miguardia-stage10-avd/20261022/`. No limpiar ni resembrar.
El ensayo único del contrato terminó incompleto. Recomendación: otro ensayo
aislado, con navegación corregida y margen temporal suficiente, conservando éste;
requiere definir y autorizar esa nueva ejecución antes de arrancar.

MAIN no operó ADB. Samsung, QA actual, aplicación pública y datos personales
permanecieron fuera del ensayo. No commit, publicación, restore, aceptación
Samsung/OEM, cierre Stage7 ni distribución. La siguiente autorización no se
infiere de esta auditoría.
