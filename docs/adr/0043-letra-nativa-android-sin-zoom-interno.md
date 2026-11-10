# ADR 0043: letra nativa Android sin zoom interno

- Vigencia: VIGENTE
- Sucesores: ninguno
- Alcance vigente: Tamaño de letra nativo, retiro de zoom interno y compatibilidad de preferencias portables heredadas.
- Fecha: 2026-10-11
- Autoridad: Joaquín: «sacá el zoom interno, respetá el tamaño de letra de Android y mantené TalkBack sin controles extra. Preservá las Copias existentes».

## Decisión

MiGuardia hereda la densidad y conversión SP de Android, incluida su curva de
escalado tipográfico. Tema, Calendario y Widget no reemplazan LocalDensity ni
neutralizan el tamaño de letra del sistema. Apariencia conserva únicamente los
temas; oscuro Vigilia sigue siendo el valor por defecto, sin reemplazar una
elección guardada. No se agrega un control propio para activar TalkBack: se
conservan semántica, foco, acciones y compatibilidad con el lector del sistema.

Esto sustituye el zoom interno 100/150/200 y su escalado geométrico/tipográfico
en ADR 0015/0042. Conserva mes completo, siete columnas, cuatro/cinco/seis filas,
sin desplazamiento mensual ni barra inferior permanente, objetivos de 48 dp y
prioridad de fecha/condiciones. Se omite información secundaria cuando no cabe;
su contenido completo permanece en detalle y descripción accesible.

La clave portable `display.zoom_percent` y su almacenamiento heredado se
conservan sin uso visual: captura/restauración siguen aceptando y preservando
100/150/200. No cambian las 17 preferencias, formato, versiones, validación,
cifrado ni restauración atómica de Copias. No se borra o reinicia ese dato.

## Validación y límites

Comprobar herencia real de LocalDensity y ausencia de lectores visuales del
zoom legado, controles retirados y round-trip portable con rechazo de inválidos.
Ejercitar temas y letra Android simulada 1/1,5/2 en contextos de prueba QA,
declarando configuración y límites; no cambiar ajustes globales del teléfono.
No exigir multiplicación SP lineal si Android usa su conversión no lineal.
Conservar TalkBack y regresiones de mes completo, áreas táctiles y navegación.

Esta decisión autoriza el cambio, no acredita su ejecución ni aceptación:
resultados y pendientes actuales viven en STATUS y en la auditoría Stage 7.
