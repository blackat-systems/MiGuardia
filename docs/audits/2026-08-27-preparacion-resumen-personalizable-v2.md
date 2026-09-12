# Auditoría de preparación — Resumen personalizable V2

Fecha: 2026-08-27

Responsable: MAIN 2.0

Resultado: **PROMPT HABILITADO — TAREA AUTORIZADA, PENDIENTE DE APERTURA TRAS
EL CHECKPOINT DOCUMENTAL**

## 1. Objetivo

Preparar un contrato durable y verificable para construir el Resumen mensual
personalizable después del cierre del Calendario final y su tarjeta superior.

Esta entrega es exclusivamente documental. No implementa el Resumen.

## 2. Puerta 0 verificada

- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD y upstream al comenzar:
  `fd6891e446eaa574f3df14348d8d5b1cfd201f2d`.
- Base protegida `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Checkout inicial: limpio.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`.
- Correo Git esperado: `blackat.systems@gmail.com`.
- Nombre configurado observado: `blackat-systems`; el checkpoint documental
  debe fijar el autor esperado `joaquin` sin cambiar la configuración global.
- Worktrees históricos: observados y preservados sin cambios.
- Push de Calendario: ejecutado y verificado en `fd6891e`; autorización
  consumida.
- Push del próximo checkpoint: no autorizado.

## 3. Fuentes contrastadas

Se contrastaron la jerarquía documental activa, las cuatro fichas sectoriales,
la planificación, el mapa maestro, el estado, el índice de prompts, los ADR de
horas y disponibilidad, el contrato histórico de Resumen V1 y el código actual
de horas, reglas por lugar, disponibilidad, navegación y DataStore.

La conclusión es:

- las fuentes laborales necesarias ya existen;
- `HoursProgress` resuelve trabajo y cumplimiento para un tramo de referencia;
- disponibilidad posee un cálculo puro separado y nunca integra trabajo;
- las reglas nocturnas, feriadas y de fin de semana están versionadas por lugar;
- el Resumen mensual V2, su libro de contribuciones y sus preferencias todavía
  no existen;
- no hace falta una nueva tabla Room ni una migración;
- la presentación puede persistirse en un DataStore propio;
- situaciones especiales futuras no bloquean el bloque: sólo se proyectan las
  fuentes comunes que ya existen.

## 4. Contrato preparado

Se crearon:

- `docs/prompts/RESUMEN_PERSONALIZABLE_V2.md`;
- `docs/adr/0031-resumen-derivado-y-presentacion-personalizable.md`.

El contrato habilita exclusivamente:

- Resumen mensual de sólo lectura;
- esenciales automáticos sin tarjetas vacías;
- cumplimiento por semanas o ciclos completos que tocan el mes;
- detalles opcionales ordenables y ocultables;
- preferencias visuales en DataStore;
- explicación exacta de cada cifra desde la misma proyección;
- reactividad, errores, recreación y actualización temporal;
- navegación principal a Resumen;
- pruebas proporcionales sin usar dispositivos sin una autorización nueva.

El contrato prohíbe expresamente:

- totales persistidos;
- una segunda fórmula de cumplimiento;
- semanas o ciclos recortados al mes;
- referencias desconocidas tratadas como cero;
- tarjetas opcionales vacías;
- fórmulas arbitrarias;
- datos privados en desgloses;
- situaciones especiales nuevas;
- cambios Room, Gradle, manifiesto, permisos o dependencias;
- próximo evento, notificaciones, widget, informes y otros bloques futuros;
- commit, push, ramas, worktrees o dispositivos desde la dependencia.

## 5. Revisiones independientes del contrato

Dos revisiones de sólo lectura contrastaron por separado el dominio de horas y
la integración de interfaz/preferencias. No modificaron archivos ni ejecutaron
pruebas o ADB.

Los riesgos detectados quedaron incorporados al prompt:

- no recuperar el Resumen V1, que dependía de 204 horas y reglas reemplazadas;
- no observar únicamente el tramo o sector vigente hoy cuando el mes requiere
  historia diferente;
- no recortar semanas o ciclos al mes;
- clasificar noche, feriado y fin de semana sobre horario real y extras exactas,
  no sobre una planificación reemplazada;
- filtrar disponibilidad por el mes sin sumarla a trabajo;
- usar una representación ordenada en DataStore y no un conjunto sin orden;
- actualizar las pruebas que hoy verifican la ausencia consciente de Resumen,
  sin debilitar el bloqueo de primera apertura;
- formar cifra y detalle desde la misma proyección para proteger igualdad y
  privacidad.

Ambas revisiones coincidieron en que una sola dependencia coherente puede
resolver dominio, DataStore, pantalla y pruebas sin modificar Room.

## 6. Persistencia preservada

- Base: `miguardia-v2.db`.
- Room: versión 5.
- Tablas: 27.
- Identity hash: `77adbc875d0f4ee466cdbd0dd74d5c5c`.
- Esquemas 1–5: sin cambios en esta preparación.
- Código, pruebas, Gradle y manifiestos: sin cambios.

SHA-256 vigentes:

```text
1.json 5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json 39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json 796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json 40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## 7. Verificación proporcional prevista

Para este checkpoint documental corresponde:

- revisar todas las referencias Markdown;
- ejecutar `git diff --check`;
- confirmar un diff exclusivamente documental;
- comprobar que no ingresen secretos ni artefactos generados;
- no ejecutar Gradle porque no cambia código;
- no usar ADB, Samsung ni emulador;
- no hacer push.

## 8. Próximo paso

1. Crear el checkpoint documental local.
2. Comprobar checkout limpio.
3. Abrir una única tarea especializada en el checkout compartido.
4. Recibir el candidato sin commit.
5. Auditar cada hunk desde MAIN.
6. Ejecutar pruebas locales y solicitar autorización separada si corresponde
   usar dispositivos.
7. Integrar sólo si queda verde.

Mensaje recomendado para este checkpoint:

```text
docs: define V2 customizable summary
```
