# Stage 10 — auditoría MAIN del cierre local

Fecha: 22/10/2026. **Entrega local recibida y auditada; conservación física
completa todavía no acreditada.** La captura V8 anterior a la apertura conserva
el caso bajo el criterio humano aceptado de depurar avisos vencidos. El ensayo
estricto anterior continúa NO APROBADO; no se convierte retrospectivamente en PASS.
Estado y siguiente puerta: [STATUS](../STATUS.md).

## Recepción y autoridad

Joaquín reanudó el proceso en MAIN y luego pidió directamente en Stage10 terminar
la auditoría local sin repetir borrados ni tocar QA actual. Único productor:
`01a15d55-500a-7162-be24-f4ed70e990a5`, turno
`01a1660e-7f96-7201-a8a9-505801317a98`.
Clave `stage10-cierre-local-tras-stage8-20261022`. Entrega:
`.local-evidence/stage10/local-audit-20261022/`.

Sello SHA-256 `71d153361d2d225e2d9d78feae6ed2967315aefbd72601f6e32e083d52d9200b`;
manifiesto `e8d6375b8977bb9012cfa84ef15ecae619093fc4dd1659f2c59ff4eef2bf48a1`.
Acuse/revisión independientes fuera del sello:
`.local-evidence/stage10/main/local-close-review-20261022/`.

## Comprobación independiente

- MAIN rehasheó 18 archivos de esta entrega y cinco manifiestos anteriores:
  139 + 24 + 19 + 99 + 149 entradas, 430 en total. También 13 fuentes/copias y
  64 evidencias de la continuación, sin diferencias. Las 1011 rutas versionables
  existentes coinciden con la entrada; staging vacío, HEAD
  `932eaeb60c6b6e68975534788e3e6501c967b1ae`. No cambios de producto ni pruebas.
- Vinculó ZIP, bytes extraídos, recibo Android, UUID, runner y protocolo
  `CAPTURE_COMPLETE`, con el instante de captura dentro de los límites guardados.
  Export portable real del gateway; no JSON esperado utilizado como sustituto.
- Copió las dos DB capturadas a scratch propio y abrió sólo esas copias en lectura.
  Integridad `ok`, sin errores FK; 27 tablas originales y 32 filas iguales por
  columna, tipo SQLite y valor. Las dos tablas nuevas V8 están vacías.
- Comparó las 17 preferencias: 16 idénticas. El único cambio es la retirada de
  `shift:30000000-0000-4000-8000-000000000009`; fin real 1792353600000 ms,
  captura 1792707817867 ms. Seis de siete RAW seleccionados idénticos.
- Revisor interno leyó evaluador y runner: RAW de avisos y export deben coincidir;
  sólo admite el ID conocido vencido y tres claves operativas con bytes exactos.
  Rechaza pérdidas durables, descartes vigentes, nuevas identidades y RAW ajenos.
  El comparador estricto original sigue rechazando la diferencia.
- Verificó JAR/DEX preservados y XML IDS de 108 bytes, SHA exacto, único entero
  `IDSCount=1`. `IdsController` es un escritor técnico compatible; no prueba el
  evento histórico ni estabilidad posterior. No exclusión genérica de archivos.

Se reutilizan, sin reejecutar, 16 tests host, 13 JVM de contrato y 13 JVM de
visibilidad, build/firma/manifiesto previos. El último runner sólo está compilado.
La auditoría no usó ADB, Samsung, emulador, Gradle, nuevas baterías ni redes.
El error inicial del lector de manifiestos del especialista y los rechazos
anteriores permanecen intactos; no alteran esta conclusión.

## Límite que impide el cierre completo

Abrir QA8 mostró la guía inicial, no un recorrido completo de Calendario. La
exportación posterior fue rechazada por el archivo IDS; nueve RAW sin cambios
no reemplazan aquella captura completa. Stage8 regeneró después los datos QA
con autorización: no se puede completar retrospectivamente esa continuidad con
la QA actual ni restaurando copias. Tampoco había un descarte aún vigente en
la fixture física. Su cobertura local no demuestra conservación Android.

## Próximo paso propuesto, no ejecutado

Una sola prueba nueva de actualización V6→V8, apertura normal y relectura,
con datos ficticios y descartes vigente/vencido, en entorno aislado de QA actual.
Recomendación: emulador dedicado si se acepta cobertura Android sin garantía
Samsung/OEM; para esa garantía, dispositivo de prueba separado. Elegir entorno,
artefactos, datos/operaciones y autorizar antes de crear o ejecutar el ensayo.
No repetir borrados ni restaurar la fixture antigua por inferencia.

Stage10 local queda auditado con límite, no aceptado como conservación integral.
Restauración, Resumen, revisión visual y distribución mantienen sus puertas.
Sin procesos del productor pendientes, nuevo supervisor, commits o publicación.
