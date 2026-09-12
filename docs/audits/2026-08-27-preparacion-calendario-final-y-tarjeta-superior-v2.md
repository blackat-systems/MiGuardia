# Auditoría de preparación — Calendario final y tarjeta superior V2

Fecha: 2026-08-27

Responsable: MAIN 2.0

Resultado: **PROMPT HABILITADO — TAREA TODAVÍA NO ABIERTA**

## 1. Objetivo

Ubicar el siguiente bloque real de la hoja de ruta y preparar un contrato
durable, autosuficiente y verificable para terminar la presentación del único
Calendario mensual y su tarjeta superior.

Esta entrega es exclusivamente documental. No implementa el bloque.

## 2. Decisión de producto aplicada

Joaquin confirmó que el siguiente bloque acordado es **Calendario final y
tarjeta superior**.

Las situaciones comunes que ya existen en V2 —`F`, `?`, carpeta médica,
vacaciones, feriados, notas y los estados internos disponibles— se conservan y
deben convivir correctamente en el Calendario. Eso no significa que estén
implementados los escritores históricos V1 de `ShiftNovelty` o
`FormalShiftChange`: fueron retirados al sellar MiGuardia exclusivamente V2 y no
se recuperan en este bloque.

Las situaciones especiales nuevas y la consolidación final del motor de horas
quedan diferidas. No bloquean la terminación visual del Calendario.

## 3. Puerta 0 verificada

- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD y upstream al comenzar:
  `80fe8e5f8fdc47d5236941e91a46ffc3b1faab61`.
- Base protegida `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Checkout inicial: limpio.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`.
- Autor: `joaquin <blackat.systems@gmail.com>`.
- Worktrees históricos: observados y preservados sin cambios.
- Autorización anterior de push: ejecutada y consumida en `80fe8e5`.
- Push actual: no autorizado.

## 4. Fuentes contrastadas

Se contrastaron la jerarquía documental activa, la planificación, el mapa
maestro, el estado, el índice de prompts, la orquestación secuencial, los ADR y
prompts históricos aplicables de Calendario y próximo evento, y el código y las
pruebas actuales de Calendario, tarjeta, horario real, extras y disponibilidad.

La conclusión es:

- ya existe una sola grilla mensual avanzada;
- ya existen el detalle del día y los recorridos V2 dueños;
- ya existen horario real, extras independientes y disponibilidad;
- la tarjeta superior actual no representa de forma completa todas las jornadas
  de hoy ni ofrece una expansión final;
- el motor heredado de próximo evento continúa siendo una fuente única útil,
  pero por sí solo no resuelve la lista completa de hoy;
- no hace falta una nueva tabla ni una migración para completar este bloque.

## 5. Contrato preparado

Se creó:

`docs/prompts/CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md`

El contrato habilita exclusivamente:

- terminar la jerarquía visual de las celdas;
- mantener un único detalle del día;
- formar una proyección reactiva de las jornadas de hoy;
- mostrar una jornada nocturna todavía activa aunque haya comenzado ayer;
- desplegar todas las jornadas aplicables sin duplicarlas;
- conservar completadas, canceladas, ausentes y protegidas con semántica clara;
- mantener separados jornada, horario real, extra independiente y
  disponibilidad;
- reutilizar el motor único de próximo evento cuando hoy no tenga jornadas;
- verificar claro, oscuro, orientación, zoom interno y accesibilidad;
- reutilizar las consultas reactivas existentes sin modificar Room.

El contrato prohíbe expresamente:

- una segunda grilla o agenda paralela;
- recuperar escritores o tablas V1;
- crear situaciones especiales nuevas;
- adelantar Resumen, notificaciones, widget u otros bloques;
- cambiar entidades, versión, esquema o migraciones Room;
- modificar DataStore, Gradle, manifiesto, permisos, paquete, SDK o versión;
- commit, push, ramas, worktrees o producción desde la dependencia.

## 6. Revisión independiente del contrato

Una revisión independiente y de solo lectura detectó ambigüedades que fueron
corregidas antes del checkpoint:

- ADR de vacaciones, horario real y extras incorporados a la lectura;
- lista desplegada definida como nocturna activa anterior más jornadas de hoy,
  con orden estable y deduplicación;
- estado honesto sin trabajo cuando sólo hay canceladas, ausentes o protegidas;
- trabajo real confirmado preservado aun cuando exista una protección;
- jornadas completadas con base neutral y marca histórica secundaria;
- convivencia explícita de extras y disponibilidad con `F` o `?`;
- expansión asociada a la fecha y cerrada nuevamente al cambiar de día;
- privacidad de notas, motivos médicos, fotos y descripciones;
- Room completamente fuera del alcance de implementación;
- extras y disponibilidad excluidos como sustitutos del próximo evento;
- prueba de degradación parcial sin vaciar la grilla.

No quedaron defectos contractuales bloqueantes conocidos.

## 7. Estado de persistencia preservado

- Base: `miguardia-v2.db`.
- Room: versión 5.
- Tablas: 27.
- Identity hash: `77adbc875d0f4ee466cdbd0dd74d5c5c`.
- Esquemas 1–5: sin cambios en esta entrega.
- Código, pruebas, Gradle y manifiestos: sin cambios en esta entrega.

SHA-256 vigentes:

```text
1.json 5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json 39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json 796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json 40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## 8. Verificación proporcional

Para esta preparación documental:

- se revisó el diff documental completo;
- se verificaron referencias Markdown y archivos citados;
- se ejecutó `git diff --check`;
- se revisó que no ingresaran archivos de aplicación, Room, Gradle o
  manifiesto;
- no se ejecutó Gradle porque no cambió código ni configuración;
- no se usó ADB ni el teléfono porque no existe implementación nueva;
- no se hizo push.

## 9. Próximo paso

1. Crear una única tarea especializada usando el prompt habilitado.
2. Recibir el candidato sin commit en el checkout compartido.
3. Auditar cada hunk desde MAIN.
4. Ejecutar batería local y QA proporcional con una autorización vigente.
5. Actualizar documentación y crear el checkpoint funcional sólo si queda
   verde.

Mensaje recomendado para este checkpoint documental:

```text
docs: prepare final calendar and top card
```
