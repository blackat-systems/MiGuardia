# Perfil laboral y Configuración — 2026-08-18

## Resultado

Perfil laboral quedó implementado y auditado en `codex/guard-profile-settings`, partiendo de la base canónica limpia `71ffd148e1662a148c1d9c306e4321652ef65bbb`.

- Guarda localmente nombre o apodo opcional y empresa mediante el DataStore exclusivo `guard_profile.preferences_pb`.
- Normaliza espacios externos, representa el nombre vacío como ausencia y rechaza una empresa vacía.
- Muestra la profesión fija `Vigilancia y seguridad` sin persistirla ni permitir su edición.
- Proyecta objetivos y horarios activos desde Room, agrupados y sin duplicados; Perfil no guarda copias.
- Enlaza al flujo real `Objetivos y horarios` y mantiene el puesto como dato opcional de cada guardia.
- Protege borradores al cerrar, evita doble guardado, conserva estado ante recomposición y distingue errores de lectura recuperables de errores de escritura.
- Configuración agrupa Trabajo, Avisos y contexto, tema Vigilia y zoom interno sin crear módulos futuros ni duplicar controles.

## Mapa de impacto

La implementación funcional alcanzó once archivos:

- modelo, normalización, DataStore y proyección de Perfil;
- estado, `ViewModel`, acciones y pantalla Compose;
- wiring en `MiGuardiaApplication`, `MainActivity` y `MiGuardiaApp`;
- pruebas JVM, DataStore instrumentado y Compose.

No modificó Room, entidades, DAO, esquemas, migraciones, manifiesto, permisos, Gradle, dependencias, red, clima, notificaciones, remuneración, calendario ni datos históricos.

Room continúa en v5 con trece entidades, esquemas exportados v1 a v5 y migraciones explícitas `1→2→3→4→5`. No existen `fallbackToDestructiveMigration` ni `allowMainThreadQueries`.

## Defectos encontrados durante la auditoría

- Se agregó el import faltante de `assertCountEquals` que inicialmente impedía compilar la instrumentación nueva.
- La cancelación del `ViewModel` ahora se propaga como `CancellationException` y no se presenta falsamente como error de guardado.
- `Reintentar` aparece sólo ante fallos de lectura; un error de escritura ya no ofrece una acción que únicamente recargaba datos.
- La cobertura de apariencia se amplió para incluir paisaje, ambos temas y zoom interno 100 %, 150 % y 200 %, restaurando la orientación natural al finalizar.

## Verificación local

Se ejecutó:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Resultados:

- `app`: 33 JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:domain`: 129 JVM, 0 fallos, 0 errores, 0 omitidas;
- `core:database`: 5 JVM, 0 fallos, 0 errores, 0 omitidas;
- total JVM: 167 aprobadas;
- Lint: 0 errores y 0 fatales; 2 advertencias de versiones disponibles y 3 sugerencias históricas de autoboxing;
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`, `assembleQa` y `assembleQaAndroidTest`: aprobados.

Después de ampliar el test de paisaje se recompilaron y ensamblaron nuevamente `assembleQa` y `assembleQaAndroidTest` con resultado satisfactorio.

## QA físico por impacto

Dispositivo: Samsung Galaxy S25 Ultra `SM-S938B`, API 36.

Se instalaron temporalmente sólo:

- `com.blackatsystems.miguardia.qa`;
- `com.blackatsystems.miguardia.qa.test`.

La selección inicial ejecutó 18 pruebas:

- 8 de `ProfileComposeTest`;
- 1 de `GuardProfilePreferencesInstrumentedTest`;
- 2 de `AppearanceComposeTest`;
- 6 de `MiGuardiaAppTest`;
- 1 regresión de `ManagementComposeTest`.

Resultado: `OK (18 tests)`.

Tras incorporar paisaje al test de Perfil, se repitió `ProfileComposeTest`: `OK (8 tests)`. Ese recorrido verificó retrato mediante las demás pruebas y paisaje explícito, temas claro y oscuro, zoom interno 100 %, 150 % y 200 %, campos y acción de guardado alcanzables, empresa obligatoria, proyección vacía, entrada única desde Configuración y protección de descarte.

El recorrido físico adicional sobre la aplicación QA verificó con datos ficticios:

- valores iniciales `Inforce` y `Vigilancia y seguridad`;
- guardado de nombre y empresa ficticios;
- persistencia al cerrar y reabrir Perfil;
- conservación del borrador con `Seguir editando`;
- descarte sin modificar el valor persistido;
- apertura del flujo real `Objetivos y horarios` y regreso a Perfil.

La orientación automática quedó restaurada. Al finalizar se desinstalaron únicamente ambos paquetes QA. `com.blackatsystems.miguardia` permaneció instalado en la misma ruta observada antes y después; no se borraron ni modificaron sus datos.

## Privacidad, seguridad y pendiente Git

No se agregaron logs, datos reales, identificadores personales obligatorios, secretos, permisos, telemetría, nube, sincronización ni red. Las pruebas y el recorrido usaron exclusivamente datos ficticios y almacenamiento QA aislado.

La implementación permanece sin commit en `codex/guard-profile-settings`. La revisión final del diff y de los archivos no rastreados, `git diff --check`, la búsqueda de secretos y el estado Git quedaron limpios dentro del alcance esperado. La rama está lista para solicitar autorización de commit. La promoción a `main` y cualquier publicación requieren autorizaciones explícitas separadas.
