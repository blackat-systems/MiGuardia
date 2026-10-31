# Cierre final en Samsung — candidata MiGuardia 3.0

## Objetivo y autoridad

Joaquín autorizó el 23/10 instalar la candidata actual y revisar pendientes,
incluido Resumen, sólo en QA y con datos ficticios; Samsung conectado, libre y
desbloqueado. Este bloque no reabre Stages8/10/11 ni autoriza distribución.

## Entradas y alcance

Repositorio MiGuardia-3.0, rama codex/miguardia-3.0, base
932eaeb60c6b6e68975534788e3e6501c967b1ae; checkout acumulado protegido.
Candidata QA R1 auditada Stage11: SHA-256
494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a.
Revalidar fuentes/artefactos/paquete/firma y preparar ejecutor AndroidJUnitRunner
normal: el ejecutor aislado Stage11 de emulador no sirve para esta revisión UI.
Un solo operador Samsung/Gradle; revisores internos sólo lectura.

## Ejecución y límites

1. Verificar Samsung SM-S938B/API36, serial explícito [serial QA en binding local privado], desbloqueo y
   foco QA o Inicio antes de operar. Foco ajeno/indeterminado: detener ese paso.
2. Identificar entrada QA y procedencia sintética sin abrir datos personales.
   No limpiar, desinstalar, sustituir datos desconocidos ni ejecutar fixtures que
   borran la base recibida. Reutilizar evidencia sólo si coincide con esta entrada.
3. Instalar únicamente QA y ejecutor aprobados con -r; comprobar hashes instalados.
   No consultar, abrir, reemplazar ni instrumentar el paquete público.
4. Revisar calendario mensual, detalle/hoja inferior, carga/borrador/Atrás,
   Resumen, Informes y ajustes pertinentes con capturas exclusivamente sintéticas.
   No ajustes globales de letra, seguridad o TalkBack ni permisos silenciosos.
5. Resumen: comprobar contenido/listo, ausencia de escrituras al consultar y
   tiempos observados en candidata actual. No repetir campaña A-B-B-A ni atribuir
   causa histórica de R4 a una medición aislada. Conservar errores/parciales.
6. Selectores OEM/biometría/voz requieren intervención humana contextual; no
   explorar galería, documentos o datos ajenos. No servicios/costos nuevos.

## Entrega y terminado

Registrar artefactos, comandos, pruebas reales frente a reutilizadas, capturas,
hallazgos y límites en auditoría fechada/evidencia nueva; MAIN audita de forma
proporcional. Dejar QA disponible para revisión de Joaquín y registrar recursos
liberados. Sin commit/push/firma/publicación ni aceptación humana por inferencia.
Un defecto productivo material se informa antes de ampliar este bloque.
