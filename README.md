# MiGuardia

Aplicación Android local y privada para que vigiladores registren y consulten guardias, francos, días sin definir, carpetas médicas, novedades, horas, próximos eventos, clima y una estimación remunerativa orientativa.

## Estado de consolidación

La base canónica y operativa es `main`, sincronizada con `origin/main`. La promoción se cerró sobre `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f`; la rama `codex/main-3` se conserva en ese punto como referencia de la línea de integración auditada y ya no es la base de trabajo. Los cambios posteriores continúan únicamente sobre `main`.

La base canónica contiene:

- Kotlin, Jetpack Compose y los módulos `app`, `core:domain` y `core:database`;
- Room v5 con trece entidades, esquemas exportados v1 a v5 y migraciones explícitas `1→2→3→4→5`;
- calendario mensual conectado a los repositorios, carga simple y múltiple, objetivos, horarios, francos, notas, novedades, feriados, carpeta médica, vacaciones y fotos mensuales;
- motores de horas y próximo evento;
- notificaciones locales, alarmas reconstruibles, cronómetro nativo, privacidad, ocultamiento explícito y restauración desde Configuración;
- clima opcional para Córdoba Capital con caché privado y proveedor reemplazable;
- estimación bruta SUVICO limitada a las escalas verificadas de julio a diciembre de 2026;
- identidad Vigilia clara, oscura o siguiendo el sistema;
- zoom interno persistente de 100 %, 150 % y 200 %;
- calendario con modos explícitos de consulta y edición;
- Perfil laboral local con nombre opcional, profesión fija, empresa editable y proyección de objetivos y horarios activos sin duplicarlos.

El cierre de visibilidad de Notificaciones está implementado y validado por impacto: `Eliminar notificación` oculta únicamente el aviso elegido, el reconciliador respeta y depura ese estado, y Configuración permite restaurar individualmente o en conjunto los avisos todavía elegibles.

Perfil laboral está implementado y validado por impacto en su rama candidata. Bienvenida/onboarding, widgets, informes PDF/XLSX, copias/restauración, bloqueo local y preparación de publicación continúan diseñados o planificados, pero no implementados.

## Requisitos de desarrollo

- Android Studio con JDK/JBR compatible con Gradle 9.5;
- Android SDK 37;
- `local.properties` local con la ruta del SDK, generado normalmente por Android Studio.

No se necesita ningún secreto para compilar `debug`. Si existe `.local-signing/debug.keystore`, se usa sólo en el equipo local; si no existe, se conserva la firma debug estándar de Android. También puede indicarse otra ruta local con la propiedad `miguardia.debugKeystore`.

## Comprobaciones principales

En PowerShell, desde la raíz del repositorio:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease
```

La instrumentación física se elige por mapa de impacto. Debe usar el paquete QA cuando el recorrido pueda modificar datos, permisos, canales o alarmas. No se instala, desinstala ni borra el paquete productivo como parte de una auditoría documental.

## Privacidad y red

Los datos laborales permanecen locales. No hay cuentas, nube, sincronización, analítica, telemetría ni ubicación automática. El permiso de Internet se usa únicamente para Clima cuando el usuario lo habilita; nunca se envían guardias, objetivos, notas, identidad ni datos del teléfono al proveedor meteorológico.

## Documentación

- Reglas permanentes: `AGENTS.md`.
- Producto y decisiones aprobadas: `docs/PROMPT_MAESTRO_MAIN.md`.
- Pausa y procedimiento de reanudación: `docs/PROMPT_MAESTRO_PAUSA_REVISION_Y_REANUDACION.md`.
- Auditoría original de Puerta 0: `docs/audits/2026-08-17-puerta-0-consolidacion.md`.
- Cierre de la promoción canónica: `docs/audits/2026-08-18-base-canonica.md`.
- Cierre de visibilidad de Notificaciones: `docs/audits/2026-08-18-notificaciones-visibilidad.md`.
- Implementación y QA de Perfil laboral: `docs/audits/2026-08-18-perfil-laboral.md`.
- Decisiones técnicas: `docs/adr/`.
- Contratos históricos y prompts de dependencias: `docs/prompts/`.
