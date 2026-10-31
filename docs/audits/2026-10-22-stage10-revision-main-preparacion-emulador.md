# Stage 10 — revisión MAIN de preparación del emulador

Fecha: 22/10/2026. Preparación RECIBIDA Y AUDITADA; ejecución Android pendiente.
Estado operativo único: [STATUS](../STATUS.md). No aceptación global de Stage10.

## Autoridad y entrega

Joaquín autorizó preparar el ensayo en un emulador dedicado, sin tocar Samsung
ni QA actual, coordinar con el mismo Stage10 y explicar faltantes. No autorizó
en esta vuelta instalar componentes, cambiar Windows ni ejecutar el ensayo.

Chat `01a15d55-500a-7162-be24-f4ed70e990a5`, turno
`01a1661d-c72a-7361-a3d3-624898ac2f00`, clave
`stage10-preparacion-emulador-20261022`. El especialista entregó y cesó recursos.

- [Plan e inventario](../../.local-evidence/stage10/emulator-preparation-20261022/HANDOFF.md).
- Sello `39e2c9415bddc95c82d7ec2c937b395e68a7ac302e1bd0e4ce76f90c07e22ffb`.
- Manifiesto `7d698c62f7362a69fb2cd2dffc3b5445e373399c27032c1f4016ed47d39bc77d`.
- Acuse y comprobaciones MAIN en
  `.local-evidence/stage10/main/emulator-preparation-review-20261022/`.

## Revisión independiente

MAIN rehasheó los ocho archivos del payload, sello/manifiesto y tres APK:
coinciden. Contrastó las 1012 rutas versionables contra la entrada del especialista:
cero diferencias. Los siete archivos de configuración/registro de AVD anteriores
siguen idénticos; HEAD original y staging vacío. Después MAIN registra sólo su
documentación, sin atribuirla al delta del especialista.

Comprobación local adicional: `sdkmanager` 22.0 existe; faltan emulador e imágenes
en el SDK configurado; la raíz del AVD nuevo aún no existe. Consulta CIM de sólo
lectura confirma `HypervisorPlatform.InstallState=2`. No se ejecutaron herramientas
Android, Gradle, instalaciones, cambios globales ni pruebas sobre el Samsung.

La revisión de fuentes independiente confirmó los riesgos del ejecutor anterior:
destino Samsung, descarte/fechas fijos, captura neutral que no acredita Calendario,
onboarding no seleccionado y anexos con bytes/tiempos propios del ensayo anterior.
El plan los trata explícitamente; no se halló un defecto material de preparación.
Esto NO comprueba las guardas futuras, todavía sin implementar.

## Propuesta y puerta pendiente

Un AVD nuevo AOSP Android 16/API36 x86_64, con WHPX y sin cuenta Google:
`MiGuardia_Stage10_API36_20261022`, raíz
`C:/Users/User/.cache/miguardia-stage10-avd/20261022`. Serial previsto
`emulator-5584`, a revalidar junto con nombre, proceso, ruta, API/ABI e identidad.
No reutilizar AVD viejos ni el coordinador Samsung.

Falta autorizar y realizar:

1. Instalar emulador estable 37.2.12 e imagen AOSP API36 r2: metadatos oficiales
   observados suman 1.299.559.945 bytes (~1,30 GB). Revalidar versiones/licencia
   antes de instalar. Reserva propuesta de disco: 20 GiB, no uso medido.
2. Habilitar Windows Hypervisor Platform con administrador y posible reinicio
   coordinado; comprobar aceleración funcional. No implica tocar BIOS, desactivar
   protecciones ni reiniciar por cuenta propia.
3. Adaptar y verificar sólo fixture/runner/comparador/coordinador de prueba;
   luego crear/arrancar el AVD y ejecutar una única secuencia QA6 → QA8.
   Los APK de producto se preservan; la compatibilidad runtime sigue pendiente.

El nuevo caso debe completar la guía V6 y comprobar Calendario antes de baseline,
conservar onboarding como RAW durable adicional y mantener las 17 preferencias
portables. Ambos descartes nacen vigentes; V6 se detiene y el corto vence por reloj
real antes de actualizar. No se modifica el reloj ni se vuelve a sembrar.
Exigir `baseline < fin corto <= actualización` y `última captura < fin largo`;
sellar datos/anexos antes de baseline. Capturar V8 antes y después de apertura
normal comprobada. Sólo el vencido puede depurarse, sin exclusiones generales.

## Límites y próximo dueño

Preparación aceptable, no ensayo ejecutable todavía ni conservación Android
cerrada. No runner nuevo, compilación, AVD creado, arranque ni dato de teléfono.
No se repiten cierre local Stage10, Stage8 ni Stage9. La evidencia adversa anterior
permanece inmutable; un ensayo en AVD no acredita Samsung/OEM, restore o distribución.

MAIN presenta el paquete concreto de autorización a Joaquín. Con permiso suficiente,
el mismo Stage10 implementa la prueba y entrega evidencia para nueva auditoría;
sin permiso queda detenido, sin supervisión automática ni operador adicional.
