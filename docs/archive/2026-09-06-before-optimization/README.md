# MiGuardia

MiGuardia 1.0 es la versión estable actual para vigiladores privados. Es una aplicación Android local y privada para organizar guardias y consultar el calendario, las horas, los próximos eventos y los avisos sin crear una cuenta.

## Funciones incluidas en 1.0

- calendario mensual con modos separados de consulta y edición;
- carga individual y múltiple de guardias y francos sobre la grilla principal;
- objetivos, horarios, puestos e instantáneas históricas de cada guardia;
- detalle diario, notas, novedades, feriados manuales, carpetas médicas y vacaciones;
- fotos mensuales del cronograma, con visor, zoom y orientación EXIF local;
- resumen mensual de horas trabajadas, pendientes, extra, nocturnas y de feriado;
- motor de próxima guardia o franco y tarjeta superior de estado;
- Pulso Vigilia: avisos locales, ritmos configurables, vista previa, notificación de prueba, ocultamiento y restauración;
- clima opcional para Córdoba Capital, con caché privada y degradación cuando no hay información;
- perfil laboral local, menú lateral, tema claro/oscuro/sistema y zoom interno de 100 %, 150 % o 200 %.

MiGuardia organiza jornadas y horas. No incorpora tablas salariales, montos,
estimaciones remunerativas ni liquidaciones.

## MiGuardia 2.0 en desarrollo

La rama `codex/miguardia-2.0` amplía la aplicación a cuatro sectores exactos:
Vigilancia privada, Policía, Enfermería y Medicina. Comparte el Calendario y
permite que cada persona configure sus lugares, tipos de trabajo, referencias
de horas, extras y disponibilidad sin imponer una fórmula por profesión.

Continúan como backlog posterior al núcleo laboral:

- onboarding completo, recorrido contextual y Ayuda;
- widgets;
- informes PDF/XLSX;
- copias de seguridad y restauración;
- bloqueo local;
- publicación de la experiencia multiprofesional completa;
- rediseños o mejoras no indispensables para estabilizar el producto actual.

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

Los datos laborales, las preferencias y las fotos permanecen en el teléfono. No hay cuentas, nube, sincronización, analítica, telemetría ni ubicación automática. Las copias automáticas y la transferencia de datos de Android están deshabilitadas. El permiso de Internet se usa únicamente para Clima cuando el usuario lo habilita; no se envían guardias, objetivos, notas, identidad ni datos del teléfono al proveedor meteorológico.

## Documentación

- Reglas permanentes: `AGENTS.md`.
- Mapa de producto 2.0: `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`.
- Planificación aprobada: `docs/PLANIFICACION_MIGUARDIA_2_0.md`.
- MAIN 2.0 activo: `docs/PROMPT_MAESTRO_MAIN_2_0.md`.
- Estado operativo: `docs/STATUS.md`.
- Contrato histórico 1.0: `docs/PROMPT_MAESTRO_MAIN.md`.
- Auditoría original de Puerta 0: `docs/audits/2026-08-17-puerta-0-consolidacion.md`.
- Cierre de la promoción canónica: `docs/audits/2026-08-18-base-canonica.md`.
- Cierre de visibilidad de Notificaciones: `docs/audits/2026-08-18-notificaciones-visibilidad.md`.
- Implementación y QA de Perfil laboral: `docs/audits/2026-08-18-perfil-laboral.md`.
- Corte funcional 1.0: `docs/adr/0016-corte-funcional-miguardia-1-0.md`.
- Candidato de versión 1.0.0: `docs/releases/MIGUARDIA_1.0.0.md`.
- Decisiones técnicas: `docs/adr/`.
- Contratos históricos y prompts de dependencias: `docs/prompts/`.
