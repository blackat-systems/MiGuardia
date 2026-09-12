# Higiene previa a MiGuardia 3.0

## Resultado

Se saneó el entorno local de MiGuardia 2.0 antes de iniciar el diseño de 3.0. La operación no cambia comportamiento de producto, Room, Copias, permisos, versión ni artefactos publicados.

Base auditada: `57e5faa1bb5700b91060fea6ba83405d5e331f0c`, rama `codex/miguardia-2.0`, inicialmente limpia y sincronizada con su upstream.

## Recuperación de trabajo histórico

La inspección encontró ocho worktrees históricos adicionales; seis contenían cambios no confirmados. Todos sus commits base eran ancestros del HEAD vigente y las capacidades principales tenían implementaciones actuales, pero los deltas se preservaron antes de retirarlos.

Archivo externo:

`C:\Users\Joaquin\Desktop\MiGuardia-Pre3-Archive-20260912-r2`

Contiene:

- bundle de todas las referencias Git de entrada;
- patch binario reversible por cada worktree modificado;
- copia exacta y manifiesto SHA-256 de cada archivo no rastreado;
- estado, HEAD y rama de cada worktree;
- evidencia compactada y manifiestos de conservación.

Se retiraron siete worktrees vinculados y diecisiete ramas locales sólo después de comprobar que estaban fusionadas. La carpeta `C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia` permanece porque aloja la administración Git compartida del checkout actual; quedó limpia y reducida a su contenido versionado.

## Evidencia local

`.local-evidence` ocupaba 7.823,52 MB. Se conservaron 4.332 archivos útiles, verificados por SHA-256, con 104,89 MB de contenido: handoffs, manifiestos, resultados, logs, XML, capturas y los Release firmados históricos identificados.

Se retiraron 281 archivos por 7.718,63 MB:

- imágenes y discos reconstruibles de emuladores;
- APK Debug, QA, unsigned y duplicadas;
- bases, WAL, SHM y una copia de prueba crudas;
- binarios temporales no esenciales.

El inventario previo de lo retirado está en el archivo externo. Las rutas relativas conservadas dentro de `.local-evidence` no cambiaron, por lo que los enlaces históricos continúan resolviendo cuando su artefacto fue retenido.

## Compilación y cachés

Se retiraron outputs regenerables del checkout actual: `.gradle`, `.kotlin`, `build`, `app/build`, `core/domain/build`, `core/database/build` y `output`. `local.properties`, firma, claves, producción y datos de dispositivos quedaron fuera del alcance.

El Java histórico de Android Studio ya no existía en su ruta registrada. Se recuperó fuera del repositorio el JBR SDK oficial `25.0.2+1-b329.117`, se comprobó su SHA-512 (`f0f15a984312c8dbf72b7bd39e1d7f949aef31bc14626abcd2af9f46408b39722da69f99fcee934dc12adb2cb3e20008164752d5504ce4552af263ce8fa33b2e`) y se usó sólo como herramienta local.

Dos ejecuciones diagnósticas con Temurin 25.0.4.1 y 17.0.20.1 reprodujeron diez fallos de reemplazo atómico de DataStore en Windows. Las mismas 41 pruebas pasaron con el JBR SDK exacto. No se alteró código productivo ni de pruebas para ocultar una diferencia de runtime; las salidas fallidas quedaron preservadas en `.local-evidence/optimization/`.

## Documentación

- El handoff operativo de 2026-09-06 pasó del directorio raíz al archivo histórico.
- El contrato de francos quedó marcado como cerrado y publicado.
- Se retiró el PNG obsoleto del plan beta privado; su copia histórica idéntica permanece en auditorías y la fuente Mermaid pública continúa vigente.
- ADR, contratos históricos y stubs de compatibilidad se conservaron: documentan decisiones o mantienen enlaces y su eliminación no aportaba una reducción material.

## Código histórico de beta

La implementación beta productiva ya no existe. Los archivos restantes bajo `testSupport` y pruebas de paquete `beta` se conservaron deliberadamente porque aún verifican residuos de instalaciones anteriores, interoperabilidad histórica y preservación durante Copias/actualizaciones. No forman parte del APK productivo. Simplificarlos requiere una dependencia de refactor de pruebas con equivalencia demostrada; no son basura eliminable sin reemplazo.

## Límites

- La unidad `D:` no estaba conectada. El archivo de recuperación quedó en el Escritorio.
- El primer intento parcial de archivo y el directorio físico huérfano de Calendario fueron enviados a la Papelera sólo después de verificar el archivo `-r2`, el bundle y la ausencia de fuentes útiles. No quedan registrados ni participan del proyecto.
- La poda física no acredita comportamiento Android. La validación posterior recompila y ejecuta pruebas según `docs/VALIDACION.md`.

## Validación final

La batería contractual se ejecutó después de la última modificación de producto —que en este bloque fue ninguna—, con todas las tareas forzadas y un único worker:

```text
BUILD SUCCESSFUL in 14m 46s
351 actionable tasks: 351 executed
```

- JVM: 844/844 — dominio 379, base 12 y app 453; cero fallos, errores u omisiones.
- Lint Debug: cero errores y trece avisos no bloqueantes — doce de versiones disponibles y uno de estilo KTX.
- Compilados: Debug, QA, Release unsigned, QA AndroidTest y database Debug AndroidTest.
- Herramientas: 75/75 pruebas; catálogo QA válido con 108 clases en 19 grupos; comprobación documental y `git diff --check` verdes.

Huellas de los artefactos regenerados antes de retirarlos del checkout:

| Artefacto | Bytes | SHA-256 |
|---|---:|---|
| Debug | 18.067.655 | `FFC8A17891FA4928CADA2037198B8595DE08E82BF8B3021FF20064EB4DB3FDDD` |
| QA | 17.952.564 | `1C0D0BE8F8F66A2DC65E94A2C8D022836984F210CCE8F3B822CB5D555F73148A` |
| Release unsigned | 12.734.073 | `800732623A7C2CEC24465833F569633E88521EEC5FEF7B5AEFB244A73BA507EE` |
| QA AndroidTest | 2.456.683 | `8EC84933F5127FFB7A025DD6DD24217E8D1EA975F5AA781FCCBA09D1053F0A28` |
| Database AndroidTest | 4.196.342 | `A95F2FA4B59E9D73C91E92615473045BADAA68B394ED4E43DDB72D8B5824014F` |

Estas APK son evidencia de compilación local. No fueron firmadas para distribución, instaladas ni publicadas.

Después de registrar las huellas se ejecutó `clean` y se retiraron nuevamente sus cachés. El checkout completo quedó en 117,21 MB, de los cuales 106,57 MB corresponden a evidencia local compacta. El archivo externo verificado ocupa 117,01 MB. El JBR SDK conservado como herramienta local está fuera del repositorio.

## Continuidad

La base queda preparada para una etapa separada de descubrimiento de MiGuardia 3.0: inventariar funciones actuales, contrastar trabajo deseado y decidir contratos antes de crear otra rama o modificar producto.
