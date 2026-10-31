# Stage 10 — recuperación del mismo R2 y comprobación de datos

**CONTINUACIÓN AUTORIZADA; consultar STATUS.** MAIN encuadra esta recuperación
acotada en la autorización directa vigente de Joaquín: «Autorizo expresamente
en aceptar todas la solicitudes de main y de stage 10 hasta que stage 10 quede
finalizado» y su reiteración posterior «Dale, seguí con Stage 10 hasta resolver
el rechazo y comprobar la conservación. Después auditá la entrega, samsung no
está dsiponible», seguida de «Continua». No se atribuye una nueva aprobación
literal de reinstalación a Joaquín: MAIN resuelve el paso técnico reversible
necesario para ese objetivo, con respaldo previo y sin borrar datos. La entrega
R2 original sigue auditada e incompleta. Esta recuperación agrega otro reemplazo
del mismo paquete y se registra como tal; no es un tercer AVD ni una nueva semilla.

## Tarea

Intentar recuperar el acceso a los datos sintéticos de QA en el MISMO emulador
R2, comprobarlos contra su baseline inmutable y entregar resultado auditable.
No declarar aprobado el ensayo original de una sola actualización: esta
recuperación agregaría otro reemplazo del paquete y cambiaría su fecha de actualización.

## Contexto y entradas

Repo `C:/Users/User/Desktop/chatgptprojects/MiGuardiaContainer/MiGuardia-3.0`;
rama `codex/miguardia-3.0`, HEAD `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
Checkout acumulado protegido. Mismo chat Stage10
`01a15d55-500a-7162-be24-f4ed70e990a5`; MAIN
`01a09739-5279-75d0-a3b1-84bd2af6b36a`. No crear otro operador.

Leer AGENTS, STATUS, WORKFLOW, VALIDACION y
[auditoría MAIN R2](../audits/2026-10-23-stage10-revision-main-r2-emulador.md).
Payload anterior inmutable `.local-evidence/stage10/emulator-rehearsal-r2-20261022/`:
HANDOFF, RESULT, sello `de8d5cdd2f26830e1c338fd98f342ad19819f9dc28eaf7bd822d00409edcbc4c`,
baseline y `r2-avd-preserved.json`. MAIN verificó 917 cuerpos y 32 archivos del AVD.

R2 está apagado. Android conservó registro QA `installed=true/pkg=null`, pero
no encontró su directorio de código ni `base.apk`; `run-as` no funciona.
IAE inicial y ausencia posterior del código tienen causas aún no demostradas.
No hay comparación V8 válida ni prueba de pérdida de la base.

- AVD `MiGuardia_Stage10_API36_20261022_R2`, serial `emulator-5586`, puertos5586/5587.
- Raíz `C:/Users/User/.cache/miguardia-stage10-avd/20261022-r2`.
- API36/x86_64, configuración/viewport originales1080×2400, densidad360.
- APK QA8: `.local-evidence/stage9/preserved-artifacts/app-qa.apk`, SHA256
  `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
- Runner R2: `artifacts/Stage10-Emulator-R2-Runner.apk` del payload, SHA256
  `639114414350995d764b8b0f82155c71dd3bd811d86a252b46ca1908575654d6`.
- Certificado técnico común
  `b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`.
- Baseline `run/baseline.zip`, SHA256
  `5082ff4b33c506585ddacb5b0506dae243614aea02ee38beb73f1de62b61c7e1`:
  Room6,27tablas/40filas,17preferencias, guía completada, dos descartes vigentes.
- Escenario `a711836c-78a0-483c-83c2-6143018c7377`: corto venció22/10 23:34 AR;
  largo vence24/10 23:04 AR. Si ya venció el largo, detener ese criterio sin
  cambiar reloj, escenario, fixture o expectativas para aprobar.

## Alcance y orden

Ejecutar esta continuidad en el mismo chat Stage10, después del relevo R2:

1. Confirmar relevo, identidad, fuentes/artefactos y ausencia de procesos/puertos.
   Comparar los32 archivos del R2 detenido con el manifiesto. Antes de arrancar o
   reponer, hacer UNA copia verificada del checkpoint completo (aprox.3,4GB) en
   evidencia nueva ignorada `.local-evidence/stage10/recovery-r2-20261023/`.
   Conservar tamaños/hashes y relación con el sello anterior. No sobrescribir
   originales, evidencia anterior ni otro AVD. Falta de espacio o diferencia
   inexplicable detiene ese paso; no limpiar para resolverla.
2. Arrancar exclusivamente el R2 original con las mismas guardas de raíz/PID/
   comando/puerto/serial/configuración y ADB local explícito. Confirmar usuario0
   desbloqueado y PackageManager listo. Registrar estado previo a la reparación.
3. Reponer UNA vez el APK QA8 exacto mediante `install -r`, conservando datos.
   Si falla, preservar el rechazo; no desinstalar, limpiar, resembrar, hacer
   downgrade ni reintentar a ciegas. No actualizar SDK ni construir otra candidata.
4. Verificar paquete/certificado/hash instalado, primera instalación original
   `1792720995991` y nueva fecha real de reemplazo. Registrar que es una segunda
   reposición QA8, nunca ocultarla como la actualización original.
5. Capturar neutralmente antes de abrir UI. Ante rechazo del lector, demostrar
   la comprobación exacta fallida mediante diagnóstico test-only mínimo. Cambiar
   sólo pruebas/herramientas si es necesario, con regresión y procedencia de
   ambos runners; no aceptar caches o valores antiguos arbitrarios. Un anexo
   heredado exige igualdad con bytes sellados del baseline cuando corresponda.
6. Si hay captura válida, abrir realmente Calendario sin guía superpuesta,
   recapturar y comparar tipos/UUID/relaciones y27tablas/40filas originales,
   tablas nuevas vacías,17preferencias, foto,RAW y onboarding. Sólo se admite
   limpieza normal del descarte corto vencido; largo debe seguir vivo.
   Conservar todos los cambios del sistema identificados, sin exclusiones amplias.
7. Cerrar recursos, comprobar cese real de procesos/puertos y entregar evidencia
   nueva sellada. MAIN recibe/audita sin operar el emulador en paralelo.

## Salida

Handoff en español con copia de respaldo verificada, comandos/tiempos, identidad
del paquete y datos, capturas, comparación, fallos, delta propio y límites.
Separar recuperación comprobada, conservación observada después de recuperar y
ensayo original incompleto. Si persiste un impedimento, terminar con causa exacta
o límite de atribución y checkpoint conservado; no otra preparación indefinida.
Avisar efectivamente a MAIN con clave/sello y confirmar cese de escrituras.

## Dependencias y prohibiciones

Depende de la autorización vigente arriba, backup verificable, artefactos
exactos y ventana del descarte largo aún vigente. MAIN conserva
STATUS/contratos/índices; especialista sólo herramientas/pruebas pertinentes
de `tools/release/`, `app/src/androidTest/`, `app/src/testSupport/` y sus pruebas.

Samsung no disponible. No usar Samsung, app pública, datos personales, primer
AVD, tercer AVD, otras apps, permisos/globales/seguridad, root/remount, borrados,
restauración de Copias, reloj, nueva semilla, producto, Room/esquemas, dependencias,
versionado/firma pública, distribución, Git o nuevos chats/supervisores.
La copia es respaldo; no ejecutarla como un tercer emulador.

## Validación y terminado

Respaldo exacto antes de modificar, protocolo de instalación comprobado además
del exit-code, hash local/instalado, continuidad de primera instalación y
comparación independiente de datos realmente leídos. Pruebas sólo por cambios
o fallos pertinentes; no repetir Stage8/9 ni baterías globales válidas.
Documentación y diff del alcance verdes, evidencia/adversos preservados y relevo.

Termina con recuperación y comparación auditables, o impedimento preciso sin
acciones adicionales no autorizadas. No acredita Samsung/OEM, restauración,
aceptación visual, distribución ni éxito retroactivo del ensayo original.
