# Preparación de Informes locales de jornadas y horas V2

- Fecha: 2026-08-29
- Proyecto: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- HEAD de entrada: `d22c5a19ab4722b36116230678511e2cfcd886fa`
- Resultado: **PROMPT HABILITADO — TAREA PENDIENTE DE PUERTA 0**

## Objetivo

Cerrar el contrato de Informes sin implementar código, usar dispositivos ni
publicar la rama.

## Puerta 0

Verificado antes de editar documentación:

- ruta y rama exactas;
- HEAD `d22c5a19ab4722b36116230678511e2cfcd886fa`;
- upstream `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`;
- rama 10 commits adelante y 0 detrás;
- checkout limpio, sin staged, unstaged ni archivos no rastreados;
- remoto privado `https://github.com/blackat-systems/MiGuardia.git`;
- autor `joaquin <blackat.systems@gmail.com>`;
- `main`, `origin/main` y `v1.0.0^{}` en
  `82db6fd8eb2c511205968894dc9857a96b16ed20`;
- nueve worktrees históricos preservados.

## Fuentes auditadas

Se leyeron según la jerarquía de `AGENTS.md`:

- mapa, estado, planificación e índice canónico;
- prompt maestro MAIN 2.0 y orquestación secuencial;
- las cuatro fichas sectoriales;
- ADR aplicables, especialmente 0026 y 0028 a 0033;
- prompt histórico MAIN y documento de pausa únicamente para el contrato
  heredado de Informes;
- release 1.0 como evidencia de capacidades ausentes;
- implementación y pruebas actuales de Horas, Resumen, Room, perfil, notas,
  fotos, navegación y apariencia.

Se realizaron además dos auditorías independientes y de sólo lectura:

1. dominio y proyecciones;
2. almacenamiento, PDF/XLSX y compartir.

También se contrastaron las guías oficiales vigentes de Android sobre
[documentos compartidos](https://developer.android.com/training/data-storage/shared/documents-files),
[`FileProvider`](https://developer.android.com/reference/androidx/core/content/FileProvider),
[compartir archivos de forma segura](https://developer.android.com/training/secure-file-sharing/setup-sharing),
[`PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument)
y la estructura oficial de
[SpreadsheetML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document).

## Estado real del código

- `MonthlySummaryInput` ya reúne las fuentes necesarias para las cifras.
- `calculateMonthlySummary(...)` reutiliza el ledger canónico de Horas.
- `SummaryMetric` y `SummaryContribution` permiten reconciliar cada valor.
- `SummaryObserver` es un buen inventario reactivo, pero no garantiza por sí
  solo una fotografía atómica para un archivo.
- Todas las fuentes laborales viven en Room y pueden leerse dentro de una
  transacción sin cambiar el esquema.
- `GuardProfileStore` conserva sólo un nombre o apodo opcional; no existe
  empresa.
- notas, notas médicas y fotos son privadas y no forman parte de la proyección
  mensual actual.
- no existe código de PDF, XLSX, SAF, compartir o `FileProvider`.
- AndroidX Core, ZIP/XML estándar y `PdfDocument` permiten el primer alcance
  sin dependencia nueva.

## Decisiones registradas

ADR 0034 y el prompt habilitado congelan:

- una fotografía transaccional de sólo lectura;
- una proyección de Informes sobre la fórmula existente;
- PDF nativo y XLSX OOXML mínimo;
- mes actual parcial, meses pasados cerrados y meses futuros bloqueados;
- meses vacíos exportables de forma honesta;
- disponibilidad separada;
- nombre, puesto, notas y fotos apagados por defecto;
- confirmación adicional para notas médicas;
- fotos opcionales sólo en PDF;
- staging inmutable de fotos, máximo de doce y procesamiento de una por vez;
- minutos `Long` y división sin truncamiento de notas largas en XLSX;
- retención privada de hasta tres artefactos y 24 horas;
- guardado mediante el selector del sistema;
- compartir mediante un `FileProvider` limitado;
- cero permisos, dependencias, migraciones o totales persistidos;
- Copias, bloqueo, Ayuda y agenda profesional fuera de alcance.

## Validación documental pendiente del checkpoint

MAIN debe verificar:

- referencias Markdown locales;
- estado único `HABILITADO` del prompt;
- coherencia entre mapa, planificación, estado, índice y orquestación;
- `git diff --check`;
- ausencia de cambios fuera de `docs/**`;
- diff staged exacto.

No corresponde ejecutar Gradle ni ADB porque esta preparación sólo modifica
documentación.

## Dispositivos y publicación

- ADB: no utilizado.
- Samsung: no utilizado.
- Emuladores: no utilizados.
- Paquetes instalados, abiertos, limpiados o desinstalados: ninguno.
- Push, tag, Release, `main` y producción: no autorizados.

## Próximo paso

MAIN crea el checkpoint documental local. Luego abre una sola tarea
`18 - Informes locales`, verifica su Puerta 0 y le transfiere el checkout como
único escritor. La dependencia entrega un candidato sin commit para auditoría
de MAIN.
