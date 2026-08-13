# MiGuardia

Aplicación Android local y privada para registrar y consultar guardias. El producto está orientado inicialmente a vigiladores de Inforce en Córdoba Capital, Argentina.

## Estado actual

La base técnica y DATA LOCAL están implementadas y auditadas:

- aplicación Android en Kotlin y Jetpack Compose;
- módulos `app`, `core:domain` y `core:database`;
- Room con esquema versión 1 y cinco tablas;
- contratos reactivos con `Flow` y escrituras `suspend`;
- calendario visual inicial, resumen y configuración como superficies base;
- pruebas JVM e instrumentadas sobre el Samsung Galaxy S25 Ultra.

El calendario todavía no está conectado a los repositorios ni permite cargar datos. Objetivos, horarios, horas, notificaciones y las demás funciones se incorporarán en incrementos posteriores según `docs/PROMPT_MAESTRO_MAIN.md`.

## Requisitos de desarrollo

- Android Studio con JDK/JBR compatible con Gradle 9.5;
- Android SDK 37;
- un archivo local `local.properties` con la ruta del SDK, generado normalmente por Android Studio.

No se necesita ningún secreto para compilar `debug`. Si existe `.local-signing/debug.keystore`, se usa solo en el equipo local; si no existe, se conserva la firma debug estándar de Android. También puede indicarse otra ruta local con la propiedad `miguardia.debugKeystore`.

## Comprobaciones principales

En PowerShell, desde la raíz del repositorio:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Con un dispositivo Android conectado, autorizado y desbloqueado:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Documentación

- Reglas permanentes: `AGENTS.md`.
- Producto y decisiones aprobadas: `docs/PROMPT_MAESTRO_MAIN.md`.
- Decisiones técnicas: `docs/adr/`.
- Prompts de dependencias: `docs/prompts/`.
- Auditorías: `docs/audits/`.

Los datos son locales por defecto. El proyecto no declara acceso a internet, analítica, telemetría ni copias automáticas del contenido de la aplicación.
