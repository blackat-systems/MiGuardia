# Puerta 0 — consolidación de Git y documentación — 2026-08-17

> Estado posterior: la promoción recomendada en este informe fue autorizada y completada el 18 de agosto de 2026. Esta auditoría se conserva como fotografía anterior a la operación. El cierre y las referencias vigentes están en `docs/audits/2026-08-18-base-canonica.md`.

## Resultado ejecutivo

`codex/main-3` es la candidata recomendada para la continuidad oficial de MiGuardia. Su código representa la línea funcional más avanzada y su historia es descendiente directa de la `main` local. Todavía no es la rama canónica: no se hizo merge, rebase, fast-forward, commit ni push durante esta etapa.

La carpeta principal conserva cambios propios sin confirmar y no fue alterada. Antes de tocarla se creó un punto de recuperación externo verificable. Dos documentos únicos se preservaron en la candidata; las referencias visuales y el PDF duplicado permanecen intactos fuera de Git.

La auditoría también detectó una discrepancia concreta: el contrato de visibilidad de Notificaciones está aprobado y documentado, pero los controles explícitos de eliminar y restaurar todavía no están implementados. Esa brecha debe cerrarse como corrección acotada después de establecer la base canónica y antes de comenzar una función nueva.

## Líneas Git relevantes

| Línea | SHA observado | Estado y relación |
|---|---|---|
| `main` local | `e156b64de99dd0f2b514bf8f58ca32aea6ed9500` | Carpeta principal sucia; un commit por delante de `origin/main` |
| `origin/main` | `c50790170896387a7cb006cf39e80f58e944af27` | Referencia remota observada durante la auditoría |
| `codex/main-3` | `6ce20d4598ad0adb66c16e019549c9ab0af51d4c` | Nueve commits por delante de la `main` local, sin divergencia |
| `origin/codex/main-3` | `6ce20d4598ad0adb66c16e019549c9ab0af51d4c` | Mismo SHA que la candidata al iniciar la consolidación |

La cadena adicional de `codex/main-3` incorpora Clima, mejoras de UX, Vigilia, documentación de experiencia inicial, política de validación por impacto y modo consulta/edición. El último commit documenta los controles de visibilidad de Notificaciones, pero no implementa por sí solo esos recorridos.

## Protección de la carpeta principal

La carpeta principal presentaba 19 entradas pendientes: siete archivos rastreados modificados y doce archivos no rastreados. Antes de clasificar o trasladar contenido se creó:

`C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia_RECOVERY_2026-08-17_PUERTA0`

El punto de recuperación contiene copias de los 19 archivos, inventario, hashes SHA-256, línea base, instrucciones de recuperación, comprobación y un parche binario. El parche tiene SHA-256:

`AFEB26FD6688D7EA8CBFBB6793CA5831AC71546ED04B5888565CE9D5AE6C4A42`

Los hashes de origen, recuperación y copias preservadas se compararon sin diferencias antes de iniciar esta actualización documental. La carpeta principal continuó con las mismas 19 entradas después de la preservación.

## Clasificación de cambios locales

- El código y las pruebas locales de Notificaciones representan una versión anterior o parcial de trabajo que ya evolucionó en la línea avanzada. No deben copiarse sobre `codex/main-3`.
- `docs/PROMPT_MAESTRO_PAUSA_REVISION_Y_REANUDACION.md` era único y necesario; se preservó en la candidata.
- `docs/prompts/VIGILIA_SISTEMA_VISUAL.md` era un contrato histórico único; se preservó en la candidata.
- `interfaz/` contiene referencias visuales locales cuya procedencia y licencia requieren decisión de Joa antes de confirmarlas.
- `output/pdf/Guia_estetica_Vigilia_MiGuardia.pdf` es byte a byte igual a la copia de `interfaz/` y se clasifica como salida generada duplicada. No se borró.
- Los artefactos locales de Gradle, Android, firma y compilación permanecen ignorados y no son deuda del producto.

## Datos, arquitectura y privacidad

La candidata usa Room v5 con trece entidades. Conserva esquemas exportados v1, v2, v3, v4 y v5 y migraciones explícitas `1→2`, `2→3`, `3→4` y `4→5`. No se encontró una migración destructiva configurada.

El manifiesto declara solamente:

- `POST_NOTIFICATIONS`;
- `SCHEDULE_EXACT_ALARM`;
- `RECEIVE_BOOT_COMPLETED`;
- `INTERNET`, exclusivamente para Clima opcional.

La aplicación mantiene copias automáticas deshabilitadas y tráfico en texto claro deshabilitado. No incorpora cuentas, nube, sincronización, analítica, telemetría ni ubicación del teléfono. La inspección no encontró secretos que deban publicarse. Las notas médicas, novedades, fotos y cronogramas continúan sujetos a almacenamiento privado y a las restricciones documentadas.

## Módulos implementados

- calendario mensual, navegación, detalle y modos `VIEW`/`EDIT`;
- objetivos, horarios, carga simple/múltiple, guardias simultáneas y preservación histórica;
- francos, días sin definir, carpeta médica, vacaciones, feriados, notas y novedades;
- fotos mensuales privadas;
- motor de horas y motor de próximo evento;
- base de Notificaciones, alarmas, cronómetro, privacidad, canales y descarte informado por Android;
- Clima para Córdoba Capital con caché privado y degradación sin red;
- identidad Vigilia con `Seguir el sistema`, `Claro` y `Oscuro`;
- zoom interno 100 %, 150 % y 200 % sin depender de ajustes visuales de Android.

## Diseñado o pendiente

- controles explícitos `Eliminar notificación` y `Mostrar notificación nuevamente`;
- Perfil laboral y reorganización de Configuración;
- bienvenida, onboarding, primera carga guiada y Ayuda;
- widgets;
- informes PDF y XLSX;
- copias de seguridad y restauración;
- bloqueo local y revisión final de privacidad;
- reglas remunerativas no demostradas por fuentes;
- logo y tipografías definitivas;
- firma, ficha, política, proveedor meteorológico comercial y preparación de publicación.

Monetización, otras profesiones, cuentas, servidor, nube y sincronización continúan fuera del incremento actual.

## Brecha verificada en Notificaciones

El código conserva IDs opacos de avisos descartados, procesa `deleteIntent` y evita que el reconciliador vuelva a publicar esos IDs. Sin embargo:

- `notification_shift_expanded.xml` no contiene un botón `Eliminar notificación`;
- el estado y las pantallas de Configuración no exponen avisos ocultos elegibles;
- el ViewModel no ofrece restauración individual ni total;
- las pruebas cubren el registro del descarte, no los dos recorridos explícitos aprobados.

Por lo tanto, Notificaciones base está implementada, pero el control de visibilidad explícito está solamente diseñado y parcialmente soportado.

## Verificación ejecutada

Sobre la candidata se ejecutó:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Resultados observados:

- `app`: 28 pruebas JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:domain`: 129 pruebas JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:database`: 5 pruebas JVM, 0 fallos, 0 errores, 0 omitidas;
- total JVM: 162 pruebas, todas aprobadas;
- Lint: 0 errores; `app` informó 2 advertencias de versión y 3 sugerencias, sin fallos; los otros módulos no informaron incidencias;
- `assembleDebug`: aprobado;
- `assembleRelease`: aprobado.

`git diff --check` no informó errores antes de iniciar la actualización documental ni después de las correcciones finales. El recolector pre-commit inspeccionó los 16 archivos modificados o nuevos: cero espacios finales, cero mojibake, cero patrones de migración destructiva y cero archivos con patrones de secretos.

ADB confirmó un Samsung Galaxy S25 Ultra modelo `SM-S938B`, API 36, conectado y autorizado. El paquete productivo permaneció instalado e intacto.

## Comprobaciones no realizadas

- no se instaló ni desinstaló ninguna aplicación;
- no se borraron datos, permisos, canales ni alarmas del teléfono;
- no se repitió instrumentación física completa porque este movimiento sólo cambia documentación y `.gitignore`;
- no se hizo merge, rebase, fast-forward, commit, push, stash, cambio de rama ni limpieza;
- no se borraron worktrees, ramas, referencias visuales ni el PDF duplicado;
- no se inició Perfil, onboarding, widgets ni otra función.

## Worktrees y limpieza futura

Git registra dieciséis worktrees en total: la carpeta principal y quince secundarios. La candidata `6883 / codex/main-3` conserva el mayor valor como línea de integración. Los worktrees de Clima, Calendario, Vigilia, Notificaciones y módulos anteriores son históricos o especializados y pueden contener entregas ya integradas, pero no se consideran descartables por presunción.

Antes de eliminar cada worktree se debe comparar su diff, identificar archivos únicos, comprobar su representación semántica en una rama publicada y pedir autorización explícita a Joa. Esa limpieza queda fuera de la Puerta 0.

## Riesgos abiertos

- promover una rama sin preservar primero la carpeta principal podría perder trabajo local; el punto de recuperación reduce este riesgo, pero no autoriza borrar nada;
- copiar código viejo de Notificaciones sobre la candidata podría hacer retroceder contratos y comportamiento;
- publicar las referencias visuales sin procedencia/licencia podría introducir material de terceros;
- el endpoint meteorológico de desarrollo no debe asumirse apto para comercialización;
- MiGuardia no debe incorporar tablas salariales, montos ni liquidaciones;
- la documentación y el código deben avanzar juntos para no volver a declarar implementado un contrato todavía pendiente.

## Recomendación y operaciones posteriores

Orden recomendado:

1. revisar este movimiento documental y confirmar que el diff sólo contiene documentación y `.gitignore`;
2. con autorización de Joa, crear un commit exclusivo en `codex/main-3`;
3. publicar ese commit en `origin/codex/main-3`;
4. volver a verificar la relación exacta con `main` y preparar un fast-forward seguro;
5. con autorización de Joa, llevar `main` al mismo commit sin reescribir historia y publicar `origin/main`;
6. verificar que ambas referencias remotas apunten a la nueva base canónica;
7. implementar como incremento acotado los dos controles faltantes de Notificaciones y validarlos por impacto;
8. recién entonces continuar con Perfil laboral.

Los pasos 2 a 7 no se ejecutan como parte de este movimiento. Commit, push, promoción de rama y cualquier limpieza requieren una autorización separada y consciente de Joa.
