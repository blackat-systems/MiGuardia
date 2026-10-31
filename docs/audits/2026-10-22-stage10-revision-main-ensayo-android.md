# Stage 10 — revisión MAIN del ensayo instalado

Fecha: 2026-10-22. Base `932eaeb60c6b6e68975534788e3e6501c967b1ae`,
rama `codex/miguardia-3.0`. Estado vigente únicamente en [STATUS](../STATUS.md).

## Resultado

Entrega física recibida y auditada como **ENSAYO NO APROBADO**. Se ejecutó el
reemplazo QA V6→V8 en la misma instalación, sin limpiar, resembrar ni restaurar
entre baseline y resultado. La conservación estricta de todas las preferencias
no se acreditó: una marca de aviso descartado pasó a conjunto vacío.
Esto no demuestra pérdida arbitraria por Room ni autoriza corregir producto.

La entrega negativa es válida; no equivale a aceptar Stage 10 positivamente,
restauración, rendimiento, revisión humana o distribución.

## Recepción, autoridad y recursos

- Único especialista: chat `01a15d55-500a-7162-be24-f4ed70e990a5`, turno
  `01a16586-ac9c-7830-b16a-9e7e3af30a2f`. MAIN no usó ADB ni Gradle.
- Joaquín reanudó el 22/10 y aprobó el control pendiente; reiteró continuar
  hasta comprobar y auditar, **sin repetir borrados**. Se conservaron las
  autorizaciones específicas previas y los controles obligatorios de herramientas.
- QA se había quitado una vez el 21/10. La pública local se quitó una vez el
  22/10 bajo su permiso expreso anterior; no se abrió ni se leyeron sus datos.
  No se borraron archivos de PC ni otras apps. No hubo otro borrado al reanudar.
- Aviso efectivo final recibido con clave
  `stage10-physical-e730666f70927a9243f6de75bbef210e9ecb803f378f5f9a92f276b0a2abb558`.
  Acuse independiente en `.local-evidence/stage10/main/receipts/` con ese hash.
- Especialista cedió recursos. MAIN verificó ausencia de clientes ADB y Gradle
  activos: sólo quedó el servidor ADB, no una prueba en ejecución. QA8 queda
  instalada y detenida con el estado adverso; no se abrió su UI normal.

## Auditoría independiente

MAIN no aceptó sólo el relato:

1. Rehasheó **149/149** archivos de la entrega física y **99/99** de R4; sello
   final `e730666f…abb558`, manifest `198d5cfb…ffb58`, sin diferencias.
2. Contrastó las 13 rutas distintas desde la entrada: 11 propias test-only y
   dos documentos de MAIN. Cuatro rutas propias cambian después de R4; no hay
   cambio productivo ni delta sin dueño. HEAD y staging permanecen intactos.
3. Comprobó por SDK el paquete/target y certificado del runner final R5b:
   `.qa.test` → `Stage10UpgradeRunner` → `.qa`, certificado
   `b6910698…b627617`. APK instalada R5b:
   `9dd37cc8bd2ed2e8e7c6a05b92b36b7e8a977c8ed86a788e7f2df676908adbde`.
   R5 `5eb42a…` se construyó, pero **no se instaló**. QA8 conserva SHA
   `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
4. Repitió los siete tests host críticos durante la auditoría R4: 7/7. Leyó
   XML final de nueve JVM focales, sin fallos/errores/omitidas, y recibo del
   build R5b verde; no repitió baterías globales ni pruebas físicas válidas.
5. Abrió baseline V6 sólo lectura: Room6, 27 tablas/32 filas, integridad `ok`,
   cero errores FK. Seed/baseline/post-rechazo coinciden en contenido y RAW.
6. Copió DB/WAL/SHM finales a un scratch **propio** de revisión y abrió esa
   copia con SQLite `mode=ro`, sin tocar originales. WAL de 86.016 bytes:
   no se usó `immutable` para ignorarlo. Verificó Room8/identidad `ba1016…aad`,
   las 27 tablas y 32 filas originales, columnas/valores/tipos SQLite iguales,
   integridad `ok`, FK cero y las dos nuevas tablas vacías.
7. Contrastó los siete RAW: **6/7 idénticos**; sólo cambió el protobuf de avisos.
   Ambos anexos técnicos conservan sus bytes/hash exactos. Revisión de fuente
   independiente corroboró el guard, el cambio de avisos y sus límites causales.

La copia V8 y su análisis son **diagnósticos**, no el bundle V8 aprobado del
runner. No se obtuvo export real V8 de las 17 preferencias ni se ejecutó el
comparador completo como aprobado. No se sustituyó ese export por JSON esperado.

## Hallazgo y clasificación

`notification_preferences.preferences_pb.preferences_pb` pasó de 237 bytes/SHA
`f5350889ab827ca8a963f0f64c0125e77b32b9df6b4488d71eef5107f6b58e39`
a 279 bytes/SHA
`513a9dde0649301ec68e0c9c469b3bc53858fcf708e8f8c639dc04c8e1a342a4`.
`dismissed_shift_ids` perdió `shift:30000000-0000-4000-8000-000000000009`;
aparecieron tres claves operativas vacías/default. MAIN confirmó la diferencia
en bytes además de revisar la decodificación diagnóstica del especialista.

La fuente vigente ofrece una explicación fuerte: `NotificationReconciler`
escribe esas claves y persiste `retainLiveDismissedEventKeys`;
`NotificationVisibility` retiene un descarte sólo mientras no terminó su fuente,
incluso con avisos apagados. La fixture sitúa la jornada el 18/10 con fin real a
las 17:00. El cache Android registra instalación del 22/10; eso no es una traza
de la lectura del reloj ni del evento/proceso escritor exacto.

Clasificación: **contradicción entre la expectativa estricta de la fixture/ensayo
y la poda temporal vigente**, compatible con el cambio observado. No se demostró
un defecto Room ni se cambió la regla para obtener verde. Tampoco se redefine
retroactivamente el cambio como permitido: el ensayo original sigue no aprobado.

## Corrección del arnés y evidencia adversa

R4 rechazó primero dos archivos adicionales: cache `files/profileInstalled` y
XML vacío `shared_prefs/notification_deferred_actions.xml`. Se preservaron RAW
y hashes; origen técnico compatible con ProfileVerifier/fuentes de avisos, sin
traza histórica del escritor. R5b reconoce sólo esos dos nombres/bytes/formato
observados, sólo en captura V8, con estabilidad antes/después. Los siete RAW,
gateway, codec y comparador siguen intactos. Los anexos se vinculan a sus RAW
preservados, no se afirma que el bundle R5b los contenga por sí solo.

La nueva captura también fue rechazada. Se conservaron ambos fallos y el WAL;
no se normalizaron archivos, borraron anexos o ajustaron valores para aprobar.

Evidencia: `.local-evidence/stage10/android-rehearsal/`, especialmente
`result.md`, `notification-raw-delta.json`, `final-diagnostic.json`,
`typed-database-comparison.json`, `final-raw/`, protocolos y manifiestos.
Revisión MAIN y scratch propio: `.local-evidence/stage10/main/`.

## Puerta siguiente

Resolver explícitamente el criterio para marcas de avisos **vencidos** frente
a datos/preferencias durables antes de otro ensayo. Recomendación: conservar
la poda vigente y probar por separado que una marca todavía vigente y los
datos laborales sobreviven a la actualización. Es propuesta, no criterio aceptado.
Cambiar el comportamiento del producto es la alternativa material, no un arreglo
de arnés. No se autorizó ni ejecutó otro baseline, borrado, restore o stage.

Quedan pendientes el export gateway V8 válido, comparación completa, reapertura
normal y aprobación del criterio. No hay permiso de distribución ni aceptación
global. La entrega se audita como evidencia negativa, sin prometer cero bugs.
