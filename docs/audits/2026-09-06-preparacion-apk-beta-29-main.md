# Preparación APK beta 29 — evidencia MAIN

Fecha local: 2026-09-06. El estado operativo se consulta exclusivamente en
`docs/STATUS.md`. Esta evidencia corresponde a preparación unsigned, no a
firma, instalación, actualización instalada o publicación.

## Entrada y alcance

Entrada limpia verificada: `74043fac4be34c7e1cb87b5cb521c5da2ab04d25`, rama
`codex/miguardia-2.0`, checkpoint funcional `df18dd7`. Ancestro requerido
`924fd46` presente; índice vacío, nueve worktrees preservados, autor y remoto
esperados. Referencia upstream local: doce adelante/cero atrás, sin fetch.
Las referencias V1 protegidas no se modificaron.

Joaquin autorizó continuar la entrega beta completa por dependencias. MAIN
registró primero el bloque 29: identidad y comprobación del APK todavía sin
firma. No se reabrieron la auditoría/optimización ni el cliente 28.

Evidencia detallada ignorada: `.local-evidence/release29/2026-09-06-main/`.
`entry.json` conserva hashes de entrada, esquemas y contratos web;
`entry-release-unsigned.apk` preserva el artefacto previo sólo como referencia.
La web permanece de sólo lectura y no se ejecutan sus fixtures ni servicios.

## Cambios y criterio

- Única fuente de versión: `app/build.gradle.kts`, `2.0.0-beta.1` / código `2`.
- Mismo paquete, SDK, permisos, dependencias, datos y comportamiento. Release
  sigue sin minificación ni firma; QA conserva `.qa` y su certificado previo.
- Se habilita explícitamente la prueba JVM de la variante Release, sin crear
  variantes de prueba adicionales para los módulos comunes.
- `tools/release/` incorpora inspección sin secretos mediante herramientas
  existentes del SDK, política fija y comparación de contenido ZIP.

La primera invocación contractual no llegó a ejecutar pruebas: AGP 9 no crea
por defecto `testReleaseUnitTest`. Un primer ajuste usó una propiedad ya oculta
en Kotlin y falló durante la configuración, sin generar artefactos. Se corrigió
mediante `HostTestBuilder.UNIT_TEST_TYPE`; el preflight `--dry-run` quedó verde.
Es un cambio de habilitación de pruebas, no una modificación del runtime.
[Cambio de AGP 9](https://developer.android.com/build/releases/agp-9-0-0-release-notes),
[API de pruebas host](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/HasHostTestsBuilder).

## Validación del candidato

Herramientas ejecutadas: 37/37 regresiones nuevas y 72/72 comprobaciones del
runner existente, sin fallos. Revisión independiente estática cerró una omisión
del verificador: ahora también exige la declaración y protección `signature`
del permiso interno, además de sus usos. MAIN repitió las 37 pruebas y un
control negativo sobre el APK anterior; éste fue rechazado exclusivamente por
versión/código antiguos, validando el parser real del SDK y el rechazo unsigned.

Batería contractual final: `BUILD SUCCESSFUL in 21m 43s`, 363/363 tareas
ejecutadas, con `--rerun-tasks --max-workers=1`. Registro:
`contract-global-r2.log`. Los insumos ejecutables conservan todos sus hashes
de `contract-inputs-r2.json`; el intento anterior se conserva como diagnóstico.

XML nuevos y archivados en `global-reports/`:

| Ejecución | Pruebas | Fallos/errores/omitidas |
|---|---:|---:|
| Dominio Debug | 379 | 0/0/0 |
| Database Debug | 12 | 0/0/0 |
| App Debug local | 410 | 0/0/0 |
| App Release local | 410 | 0/0/0 |

Son 801 pruebas del perfil local y una repetición adicional de 410 sobre
Release, no 1211 casos funcionales distintos. La interoperabilidad se ejecuta
separadamente, con Node y el checkout web explícitos; no se da por cumplida por
esta tabla. Lint Debug: cero errores/seis avisos; lint Release: cero errores/tres
avisos, todos de versiones disponibles. No se actualizó ninguna dependencia.

Debug, QA, Release unsigned y los dos APK AndroidTest fueron compilados.
Debug/QA conservan el certificado de pruebas
`b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617`, paquete base
y sufijo QA respectivamente. Los seis esquemas Room conservan sus hashes de
entrada. No hubo ejecución física dentro de este bloque.

El Release A de esa batería pasó la inspección real del SDK: 12.750.213 bytes,
SHA-256 `8a7ebffe5e3c3a8bc714fa21ddb9fb0bcabb13512d1e2748d02a018f18c0b22e`.
Paquete/versión/SDK correctos, nueve permisos utilizados, permiso propio de
protección `signature`, nueve componentes esperados, cuatro ABI y ocho ELF.
`debuggable=false`, `testOnly=false`, backup automático Android deshabilitado,
sin cleartext ni splits; Copias locales no se modificó.
`zipalign` aprobó 16 KiB; los segmentos `PT_LOAD` también son compatibles con
esa alineación. `apksigner` rechazó únicamente la ausencia de firma esperada.
Reporte: `.local-evidence/release/2026-09-06-main/build-a-inspection.json`.

Segunda reconstrucción consecutiva: `BUILD SUCCESSFUL in 5m 31s`, 118/118
tareas ejecutadas. Se comparó contra ese Release A, no contra el APK anterior
de código 1. Ambos builds usan `--rerun-tasks` y los mismos insumos.
Resultado: **BYTE_IDENTICAL**, mismo SHA-256 unsigned y las 551 entradas ZIP
idénticas. La segunda inspección independiente del APK también pasó.
Reportes: `.local-evidence/release/2026-09-06-main/reconstruction.json` y
`build-b-inspection.json` en esa misma carpeta.

Interoperabilidad: perfil explícito `interop` con Node 24.20.0 y website
`80ebafd` de sólo lectura. **3/3 ejecutadas**, sin fallos, errores, omitidas o
resultados reutilizados; Gradle verde en 32 s. Evidencia:
`.local-evidence/release29/2026-09-06-main/interop/result.json`. Se reutilizaron
compilaciones vigentes, no resultados de pruebas: 1 tarea ejecutada/54 vigentes.
Resultado combinado: 804 casos locales/interoperabilidad, más 410 ejecuciones
adicionales de la variante Release.

`docs_check.py`, diff y revisión de archivos nuevos pasaron. No se encontraron
APK, AAB, keystore, propiedades privadas ni credenciales rastreadas. Los
artefactos y registros permanecen ignorados; no se copian a Git.

**Conclusión de este bloque:** preparación técnica unsigned verificada y
reproducible; no es una aprobación de publicación. La atribución indicada
abajo y las decisiones de custodia/operación deben resolverse antes de la
entrega firmada. El cliente 28 y la optimización no fueron reimplementados.

## Condiciones oficiales revisadas

Android permite distribuir un APK desde un sitio propio. La instalación sigue
siendo consciente y Android 8+ administra la autorización de orígenes
desconocidos por aplicación instaladora; MiGuardia no necesita pedir permiso
para convertirse en instalador. Todo APK instalable debe estar firmado, y la
custodia de esa firma es necesaria para conservar la capacidad de actualizar.
[Distribución alternativa](https://developer.android.com/distribute/marketing-tools/alternative-distribution),
[Firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).

La página vigente de verificación describe un despliegue por etapas, no una
prohibición global inmediata de descarga web. La modalidad gratuita limitada
permite hasta veinte dispositivos autorizados; requiere un registro y un
procedimiento de consentimiento propios de Android. Las diez plazas de
MiGuardia no reemplazan ese procedimiento. No se abrió consola ni se creó una
cuenta o aceptaron términos.
[Verificación de desarrollador](https://developer.android.com/developer-verification),
[Distribución limitada](https://developer.android.com/developer-verification/guides/limited-distribution).

Open-Meteo distingue la licencia de datos del acceso a su infraestructura:
datos CC-BY 4.0 con atribución, frente a API gratuita para uso no comercial,
evaluación y prototipos, sujeta a límites. El piloto gratuito de diez plazas se
analiza como evaluación; eso no acredita automáticamente una futura explotación
comercial ni una excepción general por aportes voluntarios. No se contrató un
plan ni se cambió proveedor. La revisión de atribución por superficie se
documentará separadamente antes de distribuir.
[Términos](https://open-meteo.com/en/terms),
[Planes y evaluación](https://open-meteo.com/en/pricing),
[Atribución y licencia](https://open-meteo.com/en/licence).

La alineación ZIP y la de los segmentos ELF se comprueban por separado. Su
resultado estático no acredita ejecución real con páginas de 16 KiB.
[zipalign](https://developer.android.com/tools/zipalign),
[Compatibilidad con páginas de 16 KiB](https://developer.android.com/guide/practices/page-sizes).

### Brecha de atribución antes de publicación

Comparación directa de fuente y condiciones oficiales: `WeatherScreens.kt`
ofrece proveedor y términos, pero no un enlace explícito a CC BY 4.0;
`CalendarDayDetail.kt` presenta un resumen sin crédito junto al dato;
`WeatherRuntime.kt`/`ShiftNotificationPresenter.kt` hacen lo mismo en el aviso
expandido. El Widget sí añade crédito, pero lo ubica en un elemento truncable
de tres líneas. La tarjeta Hoy no muestra clima y no se le atribuye ese defecto.

MAIN confirma esa brecha técnica de atribución, sin convertirla en un dictamen
legal general. No se cambió producción durante 29. Debe resolverse mediante un
ajuste acotado, con pruebas y QA proporcional, antes de firmar el artefacto final
para publicación; no requiere modificar Room, permisos, proveedor o cálculos.
El posible recorte del Widget es un riesgo inferido, no una reproducción OEM.

## Dependencias que esta preparación no acredita

- Custodia recuperable de la clave permanente del APK, separada de la clave
  del servidor y del pepper. No se crean ni leen secretos en este bloque.
- Confianza pública del servidor incorporada antes del APK que se vaya a
  firmar y distribuir. Debug/Release todavía conservan confianza vacía.
- En la QA siguiente deben ajustarse las fixtures históricas del actualizador:
  código `2` ya no es superior a la beta instalada de código `2`. No se pueden
  debilitar sus aserciones ni instalar aquellas diminutas fixtures como app.
- QA de instalación/upgrade con el certificado definitivo, datos poblados y
  continuidad de comprobante/Keystore; el antecedente QA/debug no la sustituye.
- Fecha de cierre, contacto de privacidad y operación de retención del piloto;
  recursos remotos, APK privado, firma y publicación aún no ejecutados.

No se autoriza por inferencia un push, tag, GitHub Release o movimiento de
`main`. La autorización actual de entrega no cambia los límites de datos y
secretos de los contratos.

## Checkpoint local

MAIN creó `0e8a5b6aa2fbbacfcc2f666940c7a536b7127100`,
`build: prepare reproducible unsigned beta APK`: cinco archivos, 992
inserciones/dos eliminaciones. Incluye únicamente `app/build.gradle.kts` y las
cuatro rutas nuevas de `tools/release/`. La actualización de STATUS, índice y
esta evidencia se registra después como documentación del resultado real.
No hubo push, firma, dispositivo, publicación o cambios en website.
