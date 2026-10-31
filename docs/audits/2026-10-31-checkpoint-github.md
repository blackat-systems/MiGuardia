# Checkpoint de desarrollo para GitHub — 31/10/2026

## Alcance y conservación

Pedido de Joaquín: «Podes subir lo más actualizado de forma cuidadosa?».
Destino autorizado: `codex/miguardia-3.0` de `blackat-systems/MiGuardia`.
Base anterior: `932eaeb60c6b6e68975534788e3e6501c967b1ae`.
Se conserva el trabajo acumulado de Stages 2–12 y el plan maestro de cierre.
Este checkpoint no acepta globalmente la app ni constituye una distribución.

MAIN y Stage 12 confirmaron cese de fuentes y recursos antes de preparar el
commit. Se preservaron localmente las preimágenes del corte y los cinco APK
previos a la comprobación; esos archivos permanecen fuera de Git.

Preparación para el repositorio público:

- Se retiraron 23 apariciones del identificador del dispositivo en 21 archivos.
  Las referencias documentales remiten al binding privado; las fixtures
  negativas utilizan un identificador ficticio. Se conservaron las preimágenes.
  El barrido completo encontró otras 30 referencias en 26 documentos históricos
  sin cambios, ya presentes en el padre publicado. Este corte sanitiza el delta;
  no reescribe ese archivo histórico ni acredita retirar información de GitHub.
- Una prueba de bootstrap dependía de evidencia privada ignorada. Ahora usa una
  fixture normativa sintética incluida en el repositorio, sin identidades ni
  admisiones reales. Su plantilla pendiente sigue siendo rechazada.
- Los assets binarios Stage 10 se contrastaron con sus generadores y hashes:
  son fixtures sintéticas requeridas por las pruebas.
- No se incluyen firmas, credenciales, APK, bases reales, copias ni capturas
  privadas. No se modifica `main`, no se crean tags ni GitHub Releases.

## Validación del corte

| Comprobación | Resultado observado |
|---|---|
| JVM local con `--fresh-tests` | Gradle exitoso; 1.142 tests, cero fallos, errores u omitidos en XML |
| Recolección JVM | El primer recolector falló con `WinError 3` al copiar XML a una ruta de 260 caracteres; repetir con destino corto pasó reutilizando los 1.142 resultados e idéntico hash de insumos Android |
| `tools/release/tests` | 236/236 PASS; incluye las fixtures sanitizadas y bootstrap autosuficiente |
| `tools/tests` | 153 tests: 12 errores por catálogo QA desactualizado; no se presenta como verde |
| Perfil `build` | PASS, insumos sin cambios: tres APK generados, dos reutilizados; lint generado con 25 warnings y cero errores/fatales |
| Documentación | PASS: 529 referencias, 103 snapshots históricos íntegros |
| Índice Git | 417 blobs revisados y una eliminación ya presente al corte; seis coincidencias de secretos confirmadas como fixtures ficticias |
| `git diff --cached --check` | 14 avisos de espacios finales/saltos al EOF en Markdown acumulado; se conserva su formato, sin avisos en código |

El catálogo `tools/qa-groups.json` tiene 29 soportes y 127 clases frente a
57 soportes y 134 clases descubiertos; cinco hashes de clases existentes
también difieren. Las siete clases nuevas requieren el runner especial de
Stage 12 y una clasificación explícita. La guarda rechaza ese desfase; no se
actualizaron hashes ni grupos automáticamente para ocultarlo.

## Límites y continuación

El corte preserva A con dos métodos Android verdes y B1 C090 fallido auditado.
La batería UI de 124 métodos no comenzó. Rondas integrales: 0/2.
Continúan los pendientes de integridad B1, revisión humana, OEM, rendimiento
de Resumen, actualización pública y entrega, según STATUS y el plan maestro.
No se ejecutó QA de dispositivo ni interoperabilidad web para este checkpoint.

Los operadores históricos que consumen admisiones y bindings privados requieren
su sesión local; publicar sus fuentes no vuelve esos ensayos portables ni
autoriza su ejecución. MAIN debe conciliar el nuevo HEAD y los artefactos antes
de reanudar Stage 12; la compilación puede cambiar los outputs locales.

La publicación del commit y su hash remoto se verifican después del push y se
registran en el recibo local y el handoff a MAIN. Este documento acompaña la
preparación, no anticipa ese resultado.
