# Stage 10 — revisión MAIN de recuperación R2

Fecha: 23/10/2026. Resultado: **RECUPERACIÓN Y CONSERVACIÓN COMPROBADAS**.
No convierte el ensayo original de una sola actualización en aprobado.

## Recepción y alcance

Especialista: chat `01a15d55-500a-7162-be24-f4ed70e990a5`, turno
`01a166bd-c5d4-71a0-9c0f-9af449c34168`. Contrato:
[recuperación R2](../prompts/REANUDAR_STAGE_10_RECUPERACION_R2_Y_CONSERVACION_3_0.md).
Sello recibido `552a134a0c0ecca818dae93d7b557cb01eb3d2da3440292ddc3df9c67554e779`;
manifiesto `8a10c7f7c42315ac78adb11fbc8e93a15eaa17b518fbf851d2ab7cac07257e57`.
Payload: `.local-evidence/stage10/recovery-r2-20261023/`, 812 cuerpos,
3.651.424.390 bytes. Recibos independientes:
`.local-evidence/stage10/main/recovery-r2-review-20261023/`.

La autorización directa de completar Stage10 y los pendientes, sin repreguntas
rutinarias, se conserva en el contrato; Samsung no disponible. MAIN no ejecutó
Android ni Gradle durante esta auditoría. El especialista cesó escrituras y
liberó recursos antes de la recepción. No se usó el paquete público.

## Comprobación independiente

- Rehash de 812/812 cuerpos, sin diferencias. Manifiesto y sello coinciden.
  De 1.021 rutas de entrada, exactamente siete fuentes test-only cambiaron;
  ninguna desapareció. Hashes actuales y fuentes finales preservadas coinciden.
  Los 917 cuerpos del R2 original siguen íntegros.
- Respaldo previo: 32 archivos/3.415.626.600 bytes comprobados contra checkpoint.
  Estado recuperado final: 32 archivos/3.439.743.848 bytes comprobados por un
  revisor independiente; tamaños, hashes, inventario y metadatos concordantes.
- Diario: 162 pares inicio/resultado, destino local y `emulator-5586` explícitos.
  Una reposición QA8 y tres cambios de ejecutor; sin semilla nueva, limpieza,
  desinstalación u otro AVD. Tres capturas rechazadas preservadas; dos aceptadas
  por protocolo real, no sólo por código de proceso cero.
- MAIN abrió SQLite de las copias con `mode=ro&immutable=1` y comparó valores
  tipados de todas las tablas originales: **27 tablas/40 filas idénticas**.
  Baseline Room6 y ambas capturas Room8, 29 tablas, integridad correcta y cero
  violaciones de relaciones. Las dos tablas nuevas están vacías, como corresponde
  a esta fixture antigua; esto no prueba adjuntos/situaciones nuevos no vacíos.
- **17 claves portables** conservadas. Las 16 preferencias durables coinciden;
  la neutral conserva ambos descartes. Tras abrir Calendario sólo desaparece el
  corto vencido `…0023`; permanece el largo `…0024`, todavía vigente.
  MAIN contrastó bytes privados, foto, fixture y guía completada: ningún otro
  cambio. Primera instalación `1792720995991` conservada; actualización de la
  recuperación `1792725418882`, tiempos coherentes con ambos vencimientos.
- Captura real `run/v8-recovered-calendar.png` revisada por MAIN: Calendario
  mensual de octubre con datos FIC ficticios y sin guía superpuesta. No constituye
  aceptación estética, accesible u OEM integral.
- MAIN repitió únicamente cuatro regresiones host de riesgo: **4/4**. El
  especialista aportó 27/27 JVM y 31/31 host, compilación y ejecutor técnico.
  No se duplicaron sus baterías completas ni Stages8/9 por rutina.

## Hallazgos corregidos y límites causales

El rechazo reproducido en recuperación procedía de una guarda del lector que
exigía al cache técnico heredado el timestamp de actualización nuevo. La solución
test-only exige bytes y pertenencia al ZIP/recibo originales, identidad y tiempos
de instalación; no acepta cache antiguo arbitrario. El revisor verificó ese límite.
Esto demuestra la causa del rechazo reproducido, no la IAE original sin traza.

El exportador test-only intentaba serializar V6 con el escritor productivo V8.
Se reutilizó el encoder legacy existente en la rama de captura del emulador;
el escritor productivo sigue rechazando V6. Regresión con hash exacto de fixture.

MAIN encontró además un archivo lateral aceptable aunque no perteneciera al ZIP
baseline. Se devolvió al mismo Stage10: regresión adversa primero fallida, luego
verde al exigir pertenencia y coherencia archivadas. El baseline real sí incluía
ese archivo, por lo que el defecto no invalida las capturas actuales.

La cronología de recuperación contiene **una reposición QA8 adicional**. La causa
del APK ausente tras el reinicio original sigue indeterminada. No se atribuye a
pérdida de datos ni se declara exitoso retroactivamente el ensayo original.
Su [revisión anterior](2026-10-23-stage10-revision-main-r2-emulador.md) se conserva.

## Identidades y relevo

QA8: `2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3`.
Ejecutor final: `f8deb223ca743ee89ac59ec685003ffbbefc9d2213886738264209fc9f4e5230`.
ZIP neutral: `ef730ee449b263acae83d4abc5852605b20c8794e00f06241ddcf62a5abe044c`.
ZIP final: `974d3405bb1d964ecbdc5ab3eccb1fcf1809951389a6f286c5af732fe95f7519`.
Checkpoint recuperado: `b5e71814d68050aa2dc2b0d84f45964944da94b00575a38045a9600c7a5154bf`.

La composición final está en `final-sources/`. El snapshot Python de comienzo
de build conserva el estado anterior al arreglo host; `after.json` y el delta
final identifican la revisión posterior sin sobrescribir la evidencia histórica.

AVD apagado y sincronizado; MAIN comprobó ausencia de procesos/puertos propios.
HEAD y staging vacío conservados; siete cambios test-only, sin producto,
dependencias, esquema, firma de distribución, commit o publicación.

**Cierre de esta tarea:** recuperación auditada, sin defecto conocido abierto en
lo comprobado. No acredita restauración Android de Copias, Samsung, actualización
pública, revisión humana o distribución. Sigue la restauración sintética aislada
como otra tarea real, con contrato y dueño propios; no repetir esta recuperación.
