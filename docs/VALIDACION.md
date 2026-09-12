# Comprobaciones por impacto

Este catálogo define qué ejecutar y cómo registrar evidencia. El alcance autorizado y los pendientes viven en `docs/STATUS.md`. Ningún comando de documentación/local/build autoriza dispositivos o distribución.

## Perfiles locales

Python 3.11+ y biblioteca estándar; Java/JBR compatible con el wrapper y SDK Android 37. No se instalan herramientas ni se cambian versiones automáticamente. `local.properties` y firmas permanecen privados.

| Cambio | Comprobación necesaria |
|---|---|
| Sólo documentos/índices | `python tools/docs_check.py` y diff del alcance; no compilar Android por ritual |
| Herramientas Python | `python -m unittest discover -s tools/tests -v`, planes y casos de rechazo pertinentes |
| Dominio/ViewModels/pruebas locales | `python tools/verify.py --profile local --fresh-tests` |
| Beta/actualizador o contrato web | Perfil local más `interop` con checkout web explícito |
| Kotlin/Compose/Gradle/recursos | Perfil local y `build`; revisión dirigida y QA por superficie si cambia comportamiento |
| Room/migración | Lo anterior más esquema e instrumentación de base autorizada; no aceptar sólo compilación |
| Candidato transversal/Release | `full` y matriz de superficie; verificaciones específicas del contrato Release |

`local` ejecuta las pruebas Debug de dominio, database y app. La categoría JUnit `WebCryptoInterop` se ejecuta sólo en `interop`; se mantiene fuera de las pruebas locales para que éstas sean autosuficientes. No presentar `local` como interoperabilidad completa.

`build` ejecuta lint Debug, Debug/QA/Release y los dos APK de instrumentación. `full` ejecuta local + interoperabilidad + build en secuencia. Release aquí es la variante de compilación, no una firma o distribución oficial.

```powershell
python tools/verify.py --profile local --fresh-tests
python tools/verify.py --profile interop --website ../website --node 'C:\Program Files\nodejs\node.exe' --fresh-tests
python tools/verify.py --profile build
python tools/verify.py --profile full --website ../website --fresh-tests
```

`--website` es obligatorio para interop/full. `--node` puede indicar un ejecutable nativo o resolverse en PATH. Se comprueban transformación TypeScript y firma ECDSA/WebCrypto, y se registran HEAD, cambios e insumos relevantes de Android/web. Se usa el código web sin escribirlo ni aprovisionar un servidor. Un requisito faltante produce un fallo claro; nunca un test omitido presentado como verde.

Para invocación directa Gradle: `-Pmiguardia.tests=local` es el valor por defecto; `-Pmiguardia.tests=interop` selecciona la interoperabilidad y necesita `-Pmiguardia.website=...` y `-Pmiguardia.node=...`, o variables `MIGUARDIA_BETA_WEBSITE`/`NODE_BINARY` explícitas. Si se agrega una prueba de interoperabilidad, categorizarla y actualizar el catálogo del runner.

## Iteración y frescura

- `--plan`: muestra perfiles/comandos sin ejecutables externos ni escrituras.
- `--fresh-tests`: fuerza sólo las tareas JVM elegidas; la compilación conserva incrementalidad.
- `--rebuild`: reconstrucción global explícita. Reservarla para cambios transversales, diagnóstico de caché o el criterio de un candidato, no cada corrección documental.
- `--output`: directorio de evidencia nuevo. Por defecto se usa `.local-evidence/optimization/<fecha>`; no se sobrescriben entregas anteriores.

El runner usa Java y el JAR del wrapper del repositorio con argumentos separados. No usa shell para interpretar rutas/comandos. Registra salida/estado Gradle, reportes XML, pruebas ejecutadas frente a resultados reutilizados, hashes de insumos y artefactos. Si cambian insumos durante el proceso, falla la atribución. Ejecutar una sola batería Gradle a la vez.

## QA Android por grupos

`tools/qa-groups.json` asigna clases a grupos con estado beta, riesgo, aislamiento y parámetros explícitos. `python tools/qa.py --check-catalog` valida cobertura. Ejecutar sin `--execute` sólo muestra un plan: no consulta ADB ni un dispositivo.

La ejecución necesita grupo, serial explícito, `--execute`, `--authorized-device-use` y las autorizaciones adicionales requeridas por ese grupo. Esos flags registran una autorización obtenida de Joaquin; no la conceden por sí mismos. Ver `python tools/qa.py --help` para las opciones exactas.

Cada clase/proceso se separa para evitar que una fixture de beta abierta contamine una prueba de raíz cerrada. El catálogo distingue pruebas que modifican datos QA, permisos/avisos, archivos o sesiones temporales; no se agrupan como “seguras” por omisión. Selector OEM, regreso del navegador y sesión temporal de actualización conservan procedimientos supervisados: requieren intervención o restauración posterior que el runner no puede completar.

El runner no instala/desinstala, no limpia datos automáticamente y no recibe shell libre. Verifica paquetes QA, runner y coincidencia SHA-256 entre cada APK local e instalado antes de instrumentar; admite sólo APK universal único. Analiza el protocolo de instrumentación además del código de salida. Fallos, omitidas y superficies no ejecutadas se informan por separado. Paquetes productivos, credenciales, ajustes de pantalla y datos personales quedan fuera de los runners.

## Evidencia y aceptación

Registrar base/HEAD y delta local, insumos, comando/perfil, resultados, artefacto, API/dispositivo y alcance cuando corresponde. Separar COMPILADO, JVM EJECUTADO, RESULTADO REUTILIZADO, INTEROPERABILIDAD, ANDROIDTEST COMPILADO, INSTRUMENTACIÓN y REVISIÓN FÍSICA.

Una matriz previa a beta no cubre beta/actualizaciones. Un APK compilado no demuestra instalación; guardar un APK no demuestra upgrade sin pérdida de datos. La extracción mecánica de archivos conserva cuerpos y se comprueba por comparación/compilación, pero no se informa como una nueva prueba física.

No repetir una batería ya válida sin cambio, fallo o incertidumbre pertinente. No rebajar controles de distribución por un verde local. Guardado y conservación de evidencia: `docs/EVIDENCIA.md`.
