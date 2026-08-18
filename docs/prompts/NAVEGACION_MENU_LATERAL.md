# MiGuardia — navegación principal mediante menú lateral

> Estado: contrato listo para implementación y auditoría de MAIN
>
> Fecha: 2026-08-18
>
> Rama sugerida: `codex/navigation-drawer`

## 1. Misión

Reemplazar la barra inferior de Calendario, Resumen y Configuración por un panel lateral Vigilia abierto desde un botón hamburguesa en la barra superior. Conservar las tres pantallas y toda su conducta; no reorganizar internamente Configuración en este incremento.

Leer antes de editar `AGENTS.md`, el prompt maestro, ADR 0015, este contrato, la raíz `MiGuardiaApp`, `MainActivity`, componentes Vigilia y pruebas de navegación/apariencia. La rama nace del `main` limpio posterior a la corrección de Fotos o del SHA que indique MAIN.

## 2. Conducta

- Barra superior con botón `Abrir menú`, título de la aplicación y semántica clara.
- Panel con exactamente `Calendario`, `Resumen` y `Configuración`.
- Destino actual resaltado por forma/color y estado semántico, sin depender sólo del color.
- `Configuración` abre la pantalla vigente; no repetir sus filas dentro del panel.
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
4. desde Resumen o Configuración, Atrás vuelve a Calendario;
5. desde Calendario, Atrás aplica la conducta normal de Android.

Recreación conserva el destino razonablemente, pero no necesita reabrir el panel. Una apertura fría empieza en Calendario salvo una solicitud válida de fecha.

## 4. Límites

Puede modificar composición raíz, barra superior, enum/modelo de destinos, recursos de texto/icono propios y pruebas relacionadas. No agregar Navigation 3 ni otra dependencia, no cambiar Room/DataStore, manifiesto, permisos, red, negocio, formularios, Configuración interna ni datos.

## 5. Pruebas

- no existe barra inferior;
- el botón abre y Atrás cierra el panel;
- los tres destinos aparecen una sola vez y el actual está seleccionado;
- cada destino abre la pantalla correcta y cierra el panel;
- Configuración conserva todas sus filas y flujos;
- Resumen/Configuración vuelven a Calendario con Atrás;
- cambio horizontal de mes no abre el panel;
- notificación abre el día en Calendario;
- superficies bloqueantes y borradores conservan prioridad;
- recreación, claro/oscuro/Sistema, zoom 100/150/200 %, retrato y paisaje;
- `git diff --check`, JVM global, lint, debug/release/QA e instrumentación de `:app` por tratarse de navegación raíz.

El QA físico usa únicamente paquetes `.qa` en el Samsung y requiere autorización de Joa. Producción permanece intacta.

## 6. Entrega

Informar base/HEAD, diff, estado de navegación y Atrás, archivos, pruebas con conteos, QA físico, privacidad, comprobaciones no realizadas y confirmación de Room/permisos/dependencias intactos. No hacer commit, push, merge o rebase sin autorización.
