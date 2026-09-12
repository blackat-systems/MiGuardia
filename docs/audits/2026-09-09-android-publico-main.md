# Android público — aceptación local de MAIN

Fecha: 2026-09-09, Argentina y UTC. Etapa 19.

## Resultado

**ACEPTADO E INTEGRADO LOCALMENTE. NO PUBLICABLE TODAVÍA.**

Checkpoint funcional: `d0105e039ad07c56f8c5ee9c4c1c5a37156efdfe`,
`feat: switch Android to public distribution`.
Entrada: `3e88bc9efd5eb3a797658ac17b8fed0f766a1e82`, rama
`codex/miguardia-2.0`, índice vacío. MAIN conservó la candidata reconocida:
90 rutas sin detección de renombres; Git presenta 80 archivos al reconocerlos,
1.708 inserciones y 1.700 eliminaciones. Los tres documentos de coordinación
MAIN se excluyeron del checkpoint funcional y se registran aparte.

El producto deja de requerir activación, cuenta, cupos u opinión obligatoria.
Recuperación → Bloqueo → configuración → Ayuda → contenido conservan prioridad.
El actualizador usa el contrato público, selección exacta, límites, cancelación,
SHA-256, inspección Android, identidad instalada y guardado consciente mediante
SAF. Guardar un APK no equivale a instalarlo. El soporte histórico beta reside
exclusivamente en fuentes de pruebas; residuos privados existentes no se leen
como autoridad, no se reenvían y no se eliminan.

No se reimplementó el cliente 28 ni se reabrieron las capacidades laborales.

## Revisión y correcciones

MAIN contrastó las 90 rutas y los ocho cambios posteriores del especialista;
revisó el actualizador, integración, nuevas fuentes y evidencia. Dos revisores
internos de sólo lectura cubrieron arranque/avisos/Widget y herramientas/QA/
preservación. No fueron escritores ni ejecutaron dispositivos.

| Hallazgo | Decisión y evidencia |
|---|---|
| R01: APK aceptada después del presupuesto total | Aceptada la comprobación posterior a inspección/finalización y cancelación. Regresión 299/300 segundos ejecutada nuevamente en JVM. |
| R02: consulta SQLite podía crear/modificar SHM de entrada | Aceptada consulta de una copia temporal DB/WAL. Regresión con/sin SHM y transacción sólo en WAL ejecutada nuevamente. Requiere entrada consistente e inactiva. |
| R03: cobertura de recuperación perdía destino/descarte | Aceptada reconstrucción genérica conservando sólo contentIntent/deleteIntent opacos. Sin copiar vistas/acciones/extras laborales. Regresión compilada, no instrumentada; no demuestra envío real de callbacks. |
| M01/P3: instrucciones supervisadas pedían limpiar beta | MAIN corrigió KDoc y catálogo de créditos, eliminó la exclusión del archivo beta de la huella privada y agregó regresión operativa Python. No se borró ningún archivo del teléfono. |

Delta adicional de MAIN limitado a `WeatherCreditsSessionInstrumentedTest.kt`,
`tools/qa-groups.json` y `tools/tests/test_qa.py`, ya incluidos en las 90 rutas.
La huella incluye ahora `noBackupFilesDir` completo; conserva sólo las dos
exclusiones administrativas Android documentadas. No acredita Keystore por
comparación de archivos ni ejecución física de esta nueva comprobación.

Una primera ejecución Python rechazó correctamente el hash binario usado por
MAIN al actualizar el catálogo. Se corrigió a la normalización contractual
UTF-8/LF/sin BOM; catálogo y suite completa pasaron después. No se relajó el
validador ni sus permisos de ensayo. La revisión interna posterior no encontró
otros defectos materiales en ese delta.

## Validación ejecutada por MAIN

Evidencia privada local, ignorada por Git:
`.local-evidence/public-android/main-review-20260909/`.

```text
python tools/verify.py --profile full --website ../website --fresh-tests --output .local-evidence/public-android/main-review-20260909/final
python -m unittest discover -s tools/tests -v
python -m unittest discover -s tools/release/tests -v
python tools/qa.py --check-catalog
```

Pasada final: `20260909T234658.075347Z` → `20260909T235122.881716Z`;
resultado `passed`, insumos Android/web sin cambios. El catálogo, que no es
insumo Gradle, recibió su corrección de hash y fue validado separadamente.
No hubo reconstrucción global ni afirmación de reproducibilidad.

| Superficie | Evidencia nueva |
|---|---|
| JVM locales | 825: dominio 379, database 12, app 434; cero fallos/errores/omitidas |
| Interoperabilidad | 24 oráculos públicos + 2 históricos aislados; cero omitidas |
| XML conservados | 95 reportes, 851 casos ejecutados; no sumar como otra suite |
| Herramientas | 73/73 generales y 46/46 de entrega |
| Catálogo | 104 clases, 17 grupos; ninguna ejecución ADB |
| Build final | 335 tareas: 8 ejecutadas, 327 vigentes; satisfactorio |
| Lint | Sin errores, seis avisos de versiones; informe válido reutilizado |
| Instrumentación/QA física | Cero en esta aceptación |

`final/result.json`: SHA-256
`528653b0a593fd783fb1209597fddfd6e5215afd2aead83212bb631392f3c9cf`.
La pasada `full/` previa también pasó, antes del delta M01; no sustituye la
pasada `final/`. Logs Python: `tools-tests-final.log`, `release-tests.log`;
el intento fallido del catálogo se conserva en `tools-tests.log`.

## Artefactos correspondientes

El build conservó cuatro APK vigentes y regeneró únicamente QA AndroidTest.
Que una tarea esté vigente no significa que se haya compilado nuevamente.

| Artefacto | SHA-256 | Frescura del build final |
|---|---|---|
| Debug | `1f1b688cd59cdf91496d218ec7f8a9323db2e2053ace56e562b857a87daf19d9` | Reutilizado |
| QA | `93e6bb151c3611f628b40aad90708f6188cf82fd4781841a0716577d3f454420` | Reutilizado |
| Release unsigned | `a40adcf1cb75f9f4324ae113528b99e79f569a98b0f9076bf60338279b21fc9e` | Reutilizado |
| QA AndroidTest | `55c482b464858c71bec96fa8bab787c5095a2566a91c4b92e9c4993e412bdde1` | Generado |
| Database AndroidTest | `b6827a38006eb01adbc3b6af33460437bb194b93b415ddae9a5aac9413f9e315` | Reutilizado |

Inspector unsigned ejecutado de nuevo: `UNSIGNED_CANDIDATE_CHECKS_PASSED`,
cero errores; `.local-evidence/release/public-main-20260909-unsigned.json`.
Identidad: paquete productivo conservado, `2.0.0-beta.1`/código 2, min 26,
target/compile 37, nueve permisos, nueve componentes y ocho ELF en cuatro ABI.
Sin nueva exportación; ZIP y PT_LOAD alineados a 16 KiB estáticamente.
No es ensayo Android de páginas de 16 KiB. **No usar el hash unsigned en catálogo.**

## Preservación y seguridad

No cambió Core, Room V6, sus 27 tablas/seis esquemas, reglas laborales, Copias,
17 preferencias, manifiesto fuente, SDK ni dependencias. Gradle sólo conecta
soporte histórico de pruebas y los insumos de interoperabilidad pública.
La comparación final encontró cero cambios inesperados fuera del delta MAIN.
Website permaneció en `394964b98be87ccfd6a56877398545deb9848380`, sólo lectura;
sus tres modificados y ocho nuevos se preservaron.

API pública v1 SHA-256:
`9ad6c934a20d9a766aaa7daa3c221eb33589145f78cf985b74df5230c2d76b00`.
Corpus oracle v2/contract v1 SHA-256:
`d086f721c29a806fd41924529fda1bdec9909f8492e4fc492d6882616f587072`.

Escaneo MAIN nuevo de cinco patrones: 855 archivos, 2.367 blobs históricos y
1.699 entradas de los tres APK de aplicación, sin señales. Reporte
`security-main.json`. No incluyó los dos APK de instrumentación en esa pasada;
cero señales no prueba ausencia universal de secretos. OSV sobre 138 coordenadas
Maven sin avisos al 09/09 05:02:54Z sigue siendo evidencia heredada por iguales
dependencias, no una consulta nueva ni cobertura del sistema/OEM.

MAIN acepta estos controles para integración local, no como aplicación
invulnerable ni autorización para publicar. Room no incorpora cifrado propio:
Bloqueo, sandbox/FBE y Copias cifradas son capas diferentes; un dispositivo
comprometido queda fuera de esa garantía. No se añadió SQLCipher por inferencia.
Hash/metadatos del mismo hosting tampoco son una autoridad independiente para
la primera instalación; la continuidad de certificado sí se exige al actualizar.
Estas limitaciones deben conservarse al resolver las condiciones de entrega.

## Continuación y puertas pendientes

La salida técnica Android de 19 queda aceptada. La etapa global no se declara
cerrada mientras faltan contacto público de soporte/privacidad y condiciones
operativas de feedback/confianza; la preparación técnica independiente puede
avanzar como permite ORQUESTACION_MAIN. Feedback permanece desactivado.

Reutilizar el contrato `CIERRE_ANDROID_PUBLICO_SEGURIDAD_Y_ENTREGA_V2.md` para
una continuación cohesionada, no contratar otro implementador:

1. Etapa 20: confirmar historial de códigos distribuidos, congelar versión,
   reconstruir dos veces iguales insumos, firmar con clave permanente existente
   y verificar el firmado separadamente. La firma no se ejecutó aquí.
2. Etapa 21: fijar dispositivo/aislamiento/paquetes/artefactos/efectos y permiso
   específico. Ensayar raíz offline, residuos, recuperación/Bloqueo, callbacks
   R03 reales, avisos/Widget, SAF, retornos y matriz visual/accesible. Fixtures
   destructivas sólo en entorno desechable, nunca en Samsung conservado.
3. Upgrade: dos apps completas, mismo paquete/certificado y código creciente;
   poblar historia, jornadas/snapshots, planes, horario real, extras,
   disponibilidad, situaciones, notas/fotos y preferencias. Comparar 27 tablas,
   archivos y uso en UI; Keystore aparte. No desinstalar/restaurar para simularlo.
4. Etapa 22: sólo tras aceptación de firmado y QA, cargar/publicar bytes exactos
   mediante el responsable del Site y comprobar descarga remota. Sin APK oficial
   el catálogo sigue vacío. No usar fixtures ni APK QA como publicación.

No hubo ADB, dispositivos, firma, servicios remotos, publicación, push, tag,
ramas ni worktrees nuevos. Los nueve worktrees y referencias V1 protegidas
permanecen intactos. El commit no se presenta como otra ejecución de pruebas.
