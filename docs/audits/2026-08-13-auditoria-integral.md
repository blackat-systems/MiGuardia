# Auditoría integral — 2026-08-13

## Resultado

La base técnica y el incremento DATA LOCAL quedan aprobados para formar una línea base de desarrollo. No se encontraron defectos funcionales abiertos dentro del alcance ya implementado.

Esta aprobación no significa que el producto completo esté terminado. La interfaz visible sigue siendo una base: todavía no conecta el calendario con Room ni permite crear objetivos, horarios o guardias.

## Alcance revisado

- autoridad documental y coherencia entre prompt maestro, ADR y prompt de DATA LOCAL;
- estructura Gradle, wrapper, firma debug y reproducibilidad;
- versiones y árbol real de dependencias;
- manifiesto empaquetado, permisos, copias automáticas, secretos y logs;
- contratos de dominio, validaciones y errores controlados;
- Room, entidades, DAO, repositorios, transacciones, índices y esquema exportado;
- calendario Compose inicial, estados vacíos, navegación, tema claro/oscuro, TalkBack y escalado de texto;
- APK debug y release;
- pruebas JVM e instrumentadas en el Samsung Galaxy S25 Ultra;
- estado de Git y archivos ignorados.

## Correcciones aplicadas

1. Se agregó el SHA-256 oficial de Gradle 9.5.0 al wrapper.
2. La firma debug dejó de depender obligatoriamente de `.local-signing/debug.keystore`: usa ese archivo cuando existe y la firma estándar de Android como alternativa.
3. Se actualizaron parches estables: AGP 9.3.1, Compose BOM 2026.08.00, Navigation 3 1.1.6 en el catálogo y coroutines 1.11.0.
4. Se eliminaron lecturas duplicadas de símbolos decorativos en navegación y controles de mes.
5. Los encabezados de semana exponen nombres completos a accesibilidad y el día actual se anuncia como tal.
6. Las etiquetas de navegación ya no se parten ni se recortan con texto al 200%; cuando no entran, usan elipsis y mantienen el nombre completo para accesibilidad.
7. La prueba de interfaz ahora informa de forma explícita si el dispositivo está bloqueado.
8. Se agregaron pruebas de cambio de destino, navegación mensual y orden temporal de timestamps.
9. Se actualizaron los ADR y el estado documental de DATA LOCAL.
10. Se agregó un README reproducible y acotado al estado real del proyecto.
11. Se fijó una política de finales de línea y archivos binarios para evitar diffs falsos entre Windows y otros entornos.

## Datos y privacidad

- Esquema Room: versión 1, cinco tablas.
- No existe migración destructiva, acceso Room en hilo principal ni base en memoria de producción.
- Las guardias preservan instantáneas históricas y no dependen de claves foráneas de plantillas.
- El manifiesto de la aplicación no solicita internet, ubicación, contactos ni permisos de datos personales.
- `allowBackup` está deshabilitado y las reglas excluyen nube y transferencia entre dispositivos.
- No se encontraron logs de aplicación, credenciales, claves, tokens ni datos laborales reales dentro del código revisado.
- El receptor exportado de AndroidX Profile Installer está protegido por el permiso de sistema `android.permission.DUMP`.
- La consulta a OSV no informó vulnerabilidades conocidas para las dependencias directas de producción auditadas en sus versiones resueltas. Esto es una comprobación puntual, no una garantía sobre vulnerabilidades futuras.

## Verificación final

Comando integral ejecutado después de limpiar artefactos generados:

```powershell
.\gradlew.bat --no-daemon --stacktrace clean testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest
```

Resultado:

- build integral: correcto, 363 tareas;
- pruebas JVM: 6 aprobadas, 0 fallos;
- pruebas Room en SM-S938B/API 36: 11 aprobadas, 0 fallos;
- pruebas de interfaz en SM-S938B/API 36: 3 aprobadas, 0 fallos;
- APK debug: generado y firmado;
- APK release: generado sin firma de publicación, como corresponde en esta etapa;
- lint: sin errores de código; conserva dos avisos informativos de actualización mayor para Gradle 9.7 y Kotlin/Compose plugin 2.4.10;
- inspección visual: aprobada en tema claro, tema oscuro, tamaño habitual 115% y tamaño máximo 200%; la configuración del teléfono fue restaurada a oscuro/115%.

También se verificó que, al señalar un keystore local inexistente, el build usa correctamente `%USERPROFILE%\.android\debug.keystore` como alternativa estándar.

## Decisiones conservadoras

- Se mantiene Gradle 9.5.0 porque es la versión documentada por AGP 9.3 y ahora está verificada por hash.
- Se mantiene Kotlin/Compose plugin 2.3.21; actualizar el compilador a 2.4.10 es una migración separada, no una corrección necesaria para esta línea base.
- Navigation 3 y DataStore permanecen solo en el catálogo: no forman parte del APK hasta que un incremento real los necesite.
- El APK release permanece sin firma de publicación; no se crea ni versiona una clave release durante desarrollo.

## Estado de Git

La implementación auditada quedó registrada en el commit `3b170f3` (`feat: establish audited local-first Android foundation`). Los secretos y artefactos locales permanecen correctamente ignorados.

## Próximo paso recomendado

Publicar la línea base auditada en `main`. Después de preservar ese punto recuperable en el remoto, MAIN debe preparar el prompt autosuficiente de la dependencia CALENDARIO MENSUAL, siguiente incremento del orden de construcción aprobado.
