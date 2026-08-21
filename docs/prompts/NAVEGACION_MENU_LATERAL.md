# MiGuardia — navegación principal mediante menú lateral

> **HISTÓRICO V1 — NO EJECUTAR.** El menú lateral ya está integrado y cerrado.
> Ver `docs/prompts/README.md`.

> Estado histórico: implementado en MiGuardia 1.0; no ejecutar nuevamente
>
> Fecha: 2026-08-18
>
> Rama sugerida: `codex/navigation-drawer`

## 1. Misión

Reemplazar la barra inferior por un panel lateral Vigilia agrupado y desplazable, abierto desde un botón hamburguesa en la barra superior. El panel pasa a ser la jerarquía completa de acceso: no conserva una pantalla contenedora `Configuración` ni duplica controles.

Leer antes de editar `AGENTS.md`, el prompt maestro, ADR 0015, este contrato, la raíz `MiGuardiaApp`, `MainActivity`, componentes Vigilia y pruebas de navegación/apariencia. La rama nace del `main` limpio posterior a la corrección de Fotos o del SHA que indique MAIN.

## 2. Conducta

- Barra superior con botón `Abrir menú`, título de la aplicación y semántica clara.
- Encabezado visual Vigilia y destinos principales `Calendario` y `Resumen`.
- Sección `Tu trabajo`: `Perfil laboral`, `Objetivos y horarios`, `Feriados` y `Vacaciones`.
- Sección `Avisos y contexto`: `Notificaciones` y `Clima`.
- Sección `Aplicación`: `Apariencia`, que abre tema y zoom interno.
- No mostrar apartados futuros todavía no implementados.
- Destino raíz actual resaltado por forma/color y estado semántico, sin depender sólo del color.
- Los accesos directos reutilizan sus superficies dueñas; no duplicar formularios ni persistencia.
- Eliminar por completo la barra inferior, su espacio y sus callbacks.
- Deshabilitar apertura del panel por gesto; el gesto horizontal del mes permanece intacto.
- Seleccionar un destino cierra el panel antes de mostrarlo.
- Una solicitud desde notificación o acceso interno al Calendario fuerza ese destino y deja el panel cerrado.
- No abrir el panel por encima de superficies bloqueantes de Perfil, Gestión, Fotos, Vacaciones, Feriados, Notificaciones o Clima.

## 3. Atrás y estado

Orden:

1. si el panel está abierto, Atrás lo cierra; el panel no puede abrirse bajo una superficie bloqueante;
2. superficie, diálogo o borrador superior conserva su contrato dueño;
3. edición del Calendario conserva su salida protegida;
4. desde Resumen o Apariencia, Atrás vuelve a Calendario;
5. desde Calendario, Atrás aplica la conducta normal de Android.

Recreación conserva el destino razonablemente, pero no necesita reabrir el panel. Una apertura fría empieza en Calendario salvo una solicitud válida de fecha.

## 4. Límites

Puede modificar composición raíz, barra superior, enum/modelo de destinos, recursos de texto/icono propios, la extracción de Apariencia desde la antigua pantalla contenedora y pruebas relacionadas. No agregar Navigation 3 ni otra dependencia, no cambiar Room/DataStore, manifiesto, permisos, red, negocio, formularios ni datos.

## 5. Pruebas

- no existe barra inferior;
- el botón abre y Atrás cierra el panel;
- cada acceso implementado aparece exactamente una vez dentro de su sección;
- cada destino o superficie abre correctamente después de cerrar el panel;
- no existe un acceso ni una pantalla contenedora `Configuración`;
- Perfil, Objetivos y horarios, Feriados, Vacaciones, Notificaciones y Clima conservan sus flujos;
- Apariencia conserva tema y zoom;
- Resumen/Apariencia vuelven a Calendario con Atrás;
- cambio horizontal de mes no abre el panel;
- notificación abre el día en Calendario;
- superficies bloqueantes y borradores conservan prioridad;
- recreación, claro/oscuro/Sistema, zoom 100/150/200 %, retrato y paisaje;
- `git diff --check`, JVM global, lint, debug/release/QA e instrumentación de `:app` por tratarse de navegación raíz.

El QA físico usa únicamente paquetes `.qa` en el Samsung y requiere autorización de Joa. Producción permanece intacta.

## 6. Entrega

Informar base/HEAD, diff, estado de navegación y Atrás, archivos, pruebas con conteos, QA físico, privacidad, comprobaciones no realizadas y confirmación de Room/permisos/dependencias intactos. No hacer commit, push, merge o rebase sin autorización.
