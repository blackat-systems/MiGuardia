# Auditoría — Calendario adaptable de MiGuardia 2.0

- Fecha: 2026-08-20
- Puerta: U — UX/UI adaptable del Calendario principal
- Rama: `codex/miguardia-2.0`
- Base sellada: `v1.0.0^{}` / `82db6fd8eb2c511205968894dc9857a96b16ed20`
- Dispositivo físico: Samsung Galaxy S25 Ultra `SM-S938B`, Android 16/API 36
- Resultado: aprobada

## Objetivo auditado

Adecuar la pantalla principal del Calendario a la ventana disponible sin cambiar
datos ni comportamiento funcional. La jerarquía conservada es próximo evento,
mes y grilla mensual, y acción inferior. Cuando la altura no alcanza, todo el
contenido debe seguir siendo alcanzable y el desborde debe resultar visible.

## Cambios comprobados

- La tarjeta del próximo evento tiene una presentación compacta que conserva su
  información y estados.
- Los controles normales del mes aprovechan una sola fila al 100 %.
- Se redujeron separaciones verticales externas antes de tocar la grilla.
- La superficie muestra una barra vertical únicamente cuando existe desborde.
- El pulgar representa proporcionalmente la ventana visible, conserva un mínimo
  reconocible y sigue el desplazamiento.
- La barra ocupa el margen derecho, no intercepta gestos y no invade la grilla.
- El contenido completo, incluida la columna derecha y la acción inferior,
  permanece alcanzable con zoom interno 100 %, 150 % y 200 %.
- `HeroCard(compact = false)` conserva por defecto el comportamiento heredado;
  sólo la tarjeta del próximo evento solicita el modo compacto.

## Archivos de la entrega

- `app/src/main/java/com/blackatsystems/miguardia/ui/MiGuardiaApp.kt`
- `app/src/main/java/com/blackatsystems/miguardia/ui/nextevent/NextEventCard.kt`
- `app/src/main/java/com/blackatsystems/miguardia/ui/components/VisualSystem.kt`
- `app/src/androidTest/java/com/blackatsystems/miguardia/CalendarAdaptiveLayoutComposeTest.kt`
- `app/src/test/java/com/blackatsystems/miguardia/ui/VerticalScrollbarMetricsTest.kt`

## Validación automatizada

Comando de compilación y regresión de la entrega:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleQaAndroidTest
```

Resultado final observado: `BUILD SUCCESSFUL`.

- JVM: 175 pruebas, 0 fallos, 0 errores y 0 omitidas.
- Lint: 0 errores, 2 advertencias de versiones disponibles y 3 sugerencias de
  autoboxing; no se modificó la configuración señalada.
- `assembleDebug`: aprobado.
- `assembleQaAndroidTest`: aprobado.
- `git diff --check`: correcto.

La prueba de viewport amplio se corrigió antes de la repetición final para usar
una altura realmente suficiente de 1080 dp. Los escenarios bajos de 700/760 dp
desbordan de manera esperada y comunican ese estado mediante la barra.

## Validación física

Comando ejecutado sobre el único dispositivo ADB autorizado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 connectedQaAndroidTest
```

Resultado desde el XML JUnit conservado:

- aplicación QA: 172 pruebas, 0 fallos, 0 errores y 0 omitidas;
- `CalendarAdaptiveLayoutComposeTest`: 3/3;
- `CalendarComposeTest`: 17/17;
- `NextEventComposeTest`: 8/8;
- `VisualPolishComposeTest`: 9/9;
- `MiGuardiaAppTest`: 7/7.

El recorrido manual en tema oscuro confirmó una composición legible y el acceso
al contenido inferior. La instrumentación recorrió también cambio de tema y los
tres niveles de zoom interno. No se modificaron `font_scale`, densidad, tamaño
de visualización ni otros ajustes globales del Samsung.

El paquete QA se retiró al finalizar la instrumentación. El paquete principal
`com.blackatsystems.miguardia` y sus datos no se instalaron, abrieron, borraron
ni modificaron durante esta puerta.

## Límites verificados

La entrega no modifica Room, DataStore, Gradle, manifiesto, permisos, versión,
dependencias, `Theme.kt`, densidad estable, navegación ni lógica de dominio. No
usa `LocalConfiguration`, `font_scale` ni métricas globales del sistema como
disparador de disposición.

## Cierre

La Puerta U queda implementada, auditada y congelada. Sólo corresponde reabrirla
ante una regresión verificable. Su consolidación Git y la inicialización de MAIN
2.0 son puertas separadas.
