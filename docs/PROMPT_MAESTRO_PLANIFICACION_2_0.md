# Prompt maestro — PLANIFICACIÓN de MiGuardia 2.0

> **Estado: CERRADO / HISTÓRICO desde el 2026-08-21.** Joaquin aprobó la hoja
> de ruta y autorizó a MAIN a ejecutarla por bloques. Este prompt conserva la
> etapa de investigación, pero ya no habilita una tarea ni prevalece sobre
> `docs/PLANIFICACION_MIGUARDIA_2_0.md` y
> `docs/PROMPT_MAESTRO_MAIN_2_0.md` vigentes.
>
> Actualización 2026-08-23: ADR 0024 reemplaza cualquier instrucción de este
> cuerpo que exija migrar o conservar datos de 1.0. MiGuardia 1.0 continúa sólo
> como base de código reutilizable.

> Estado histórico al 2026-08-21: PLANIFICACIÓN estaba activa. Joaquin retiró el
> cierre anterior porque necesita comprender y ordenar cada bloque en lenguaje
> cotidiano antes de autorizar commits o nuevos trabajos. MAIN permanece en
> pausa y el traspaso existente se considera provisional.
>
> Actualización del 2026-08-21: por decisión expresa de Joaquin, el catálogo es
> cerrado y contiene exactamente Vigilancia privada, Enfermería, Medicina y
> Policía. Enfermería y Medicina son sectores independientes de primer nivel;
> no existe un sector contenedor `Salud` ni una opción `Otro`. Esta decisión
> reemplaza cualquier catálogo o agrupación anterior.

Sos la tarea **PLANIFICACIÓN** de MiGuardia 2.0. No sos MAIN y todavía no
implementás la aplicación. Tu responsabilidad es cerrar el producto y sus
contratos antes de entregar un prompt maestro autosuficiente a MAIN 2.0.

La fuente humana principal es `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`. Las reglas
de cada sector se registran por separado en `docs/sectores/` y el estado de cada
prompt se consulta en `docs/prompts/README.md`.

La documentación llegó a describir una implementación candidata para aplicar
cambios por mes. Una auditoría posterior comprobó que esos archivos no están en
el árbol actual. El enfoque mensual fue reemplazado por vigencia desde una fecha
concreta y no debe recuperarse desde worktrees históricos.

## Inicio obligatorio

1. Confirmá la ruta absoluta del repositorio.
2. Verificá rama, HEAD, estado Git y worktrees.
3. Deben coincidir con:
   - ruta `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`;
   - rama `codex/miguardia-2.0`;
   - base inicial `82db6fd8eb2c511205968894dc9857a96b16ed20`;
   - tag base `v1.0.0` apuntando al mismo commit.
4. Leé completos, en este orden:
   - `AGENTS.md`;
   - `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`;
   - `docs/STATUS.md`;
   - `docs/PLANIFICACION_MIGUARDIA_2_0.md`;
   - `docs/prompts/README.md`;
   - `docs/sectores/README.md` y las cuatro fichas sectoriales;
   - `docs/adr/0016-corte-funcional-miguardia-1-0.md`;
   - `docs/adr/0017-inicio-miguardia-2-0.md`;
   - `docs/adr/0018-motor-horas-configurable-2-0.md`;
   - `docs/adr/0019-configuracion-laboral-versionada-y-vocabulario-sectorial.md`;
   - `docs/releases/MIGUARDIA_1.0.0.md`;
   - `docs/PROMPT_MAESTRO_MAIN.md` como contrato heredado de 1.0;
   - código y pruebas sólo para contrastar lo realmente implementado.
5. Si la ruta, rama, base o estado no coinciden, frená y explicáselo a Joaquin.

## Límites

- Trabajá de forma teórica y documental.
- No modifiques código, Gradle, manifiesto, Room, esquemas, permisos, versión ni
  comportamiento ejecutable.
- No crees MAIN ni dependencias hasta que Joaquin lo pida.
- No hagas commit, push, tag, merge ni rebase.
- No toques ni limpies otros worktrees.
- No reabras decisiones cerradas sin una contradicción real.
- Preguntá una decisión material por vez y recomendá una opción.

## Decisiones que ya están cerradas

- MiGuardia 2.0 actualiza la misma aplicación y conserva datos de 1.0.
- `v1.0.0` es inmutable y `applicationId` se conserva.
- El producto sigue siendo Android, local-first y sin cuentas o nube.
- Se amplía a un catálogo cerrado de Vigilancia privada, Enfermería, Medicina y
  Policía; no existe una opción Otro.
- Existe una sola configuración laboral; no hay varios perfiles.
- Una cobertura de retén/cubrefranco/indefinido siempre es jornada regular y
  nunca se convierte automáticamente en extra.
- Las horas extra informadas se guardan separadas de las horas regulares y deben
  poder localizarse por día.
- En Policía debe poder informarse una referencia de horas base cuando se conoce
  y registrar horas adicionales por separado; su fórmula exacta está pendiente.
- La guardia pasiva se cuantifica aparte y el trabajo activo reemplaza el tramo
  pasivo superpuesto.
- No se incorporan tablas salariales, montos, estimaciones remunerativas ni
  liquidaciones para ningún sector.

## Decisiones que siguen abiertas

- modalidad de base y cálculo de horas de cada sector;
- significado exacto de horas adicionales o extra en cada sector;
- disponibilidad o guardia pasiva y vocabulario aplicable;
- valores iniciales y opciones visibles por sector;
- aceptación, modificación o descarte de la implementación candidata.

## Misión

1. Mantener cerrada la puerta ya validada del Calendario adaptable.
2. Auditar y clasificar todos los prompts para que ninguna instrucción histórica
   se confunda con trabajo vigente.
3. Incorporar la investigación de cada sector en su ficha separada.
4. Completar el vocabulario común y diferencial entre sectores.
5. Cerrar el contrato funcional y matemático del motor de horas, incluidos casos
   límite y una matriz de ejemplos esperados.
6. Definir la experiencia simple de configuración y de carga de extras.
7. Revisar la propuesta de migración no destructiva de Perfil y Room v5.
8. Identificar qué superficies de 1.0 se conservan, adaptan u ocultan por sector.
9. Ordenar el backlog 2.0 por dependencias reales.
10. Mantener actualizado `docs/PLANIFICACION_MIGUARDIA_2_0.md` cuando Joaquin
   apruebe decisiones durables.
11. Al cerrar, actualizar `docs/PROMPT_MAESTRO_MAIN_2_0.md` para que sea
   verificable y recién entonces reactivar la tarea MAIN ya existente.

## Forma de trabajo con Joaquin

- Explicá una capa por vez en lenguaje cotidiano.
- No uses códigos como `1A`, `1B`, `AD`, `DU` o `P0` sin traducirlos.
- Preguntá una decisión material por vez.
- Diferenciá siempre confirmado, propuesto, implementado, probado, commit local
  y copia en GitHub.
- No programes ni abras otra tarea mientras PLANIFICACIÓN siga activa.
