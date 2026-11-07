# Estado actual de MiGuardia 3.0

- Bloque autorizado: STAGE_12_1_CIERRE_B1_3_0
- Estado: PAUSADO_POR_USUARIO
- Contrato: docs/prompts/STAGE_12_1_CIERRE_B1_3_0.md
- Base verificada: b7fd3818e4d490c91c17a7d32a5fae0b4c181143
- Actualización: 2026-11-07

Escrito en castellano llano. Los códigos raros están traducidos en el [glosario](GLOSARIO.md).

## En una frase

Estamos en la etapa **Cierre de Trabajo y Calendario** (B1), en pausa desde el 5/11.
La app está casi toda programada; lo que falta es sobre todo **probarla en el Samsung
y cerrar las etapas de verificación**, no hacer funciones nuevas.

## Qué pasó hasta el 5/11

- **Recuperar el calendario cuando Android cierra la app** (C096): **aprobado**.
  Costó 14 intentos; el que funcionó usa un toque tuyo real para elegir la foto,
  porque la elección automática fallaba sin causa clara. Probó que, al volver,
  el mes, la selección, el detalle y el borrador siguen ahí y no se guarda nada solo.
- **Error al comparar datos** (la copia temporal `derived_copy` hacía creer que los
  datos habían cambiado): arreglado y probado en la PC.
- **Disponibilidad**: arreglo de acentos (UTF-8) hecho y compilado; falta probarlo en el teléfono.
- **Calendario**: cuatro pruebas en el teléfono pasaron; falta la comprobación final de que no se perdieron datos.
- Hay una **versión de prueba armada** con todos estos arreglos juntos. Compila,
  pero **no se instaló** en el Samsung.
- Se hizo el commit de checkpoint y se subió a GitHub (`codex/miguardia-3.0`).

## Qué falta para cerrar esta etapa

1. Preparar los datos de entrada de la versión de prueba e instalarla en el Samsung.
2. Probar en el teléfono, con pasos tuyos donde haga falta:
   - Disponibilidad.
   - Fechas de inicio y fin de repeticiones (A051) y editar una jornada sin cambiar su historia (C089).
   - Abrir dirección en Maps (C032), ubicación para Clima (A041, A042) y cancelaciones (C074).
3. Comprobar que el teléfono no perdió datos después de las pruebas.

## Etapas que vienen después

| Etapa | Qué cubre |
|---|---|
| 12.2 · Cálculos y consulta | Horas, metas, extras, Resumen, informes PDF/XLSX |
| 13 · Datos y archivos | Notas, fotos, Copias, restaurar, archivos dañados |
| 14 · Sistema y aceptación final | Avisos, Widget, bloqueo, Clima, temas, accesibilidad, pasada completa final |
| 15 · Preparar la entrega | Firma, prueba de actualización desde 2.0.2 y publicación |

Detalle en el [plan maestro](PLAN_MAESTRO_CIERRE_3_0.md).

## Cambio de forma de trabajo (7/11)

Joaquín pasó de Codex a Claude y aprobó el [Proceso simple](PROCESO_SIMPLE.md):
una tarea por vez con nombre claro, Claude programa y prueba en la misma sesión,
sin recibos entre chats. También decidió hacer **una** pasada completa final
(no dos) más una prueba corta tras cada arreglo. Las pruebas en el teléfono
siguen pausadas hasta que Joaquín las retome.

## Dónde está la evidencia vieja

La carpeta `.local-evidence/` (registros de las pruebas, no se sube a Git) no se
había copiado en la migración. El 7/11 se copió desde el respaldo de Codex del
disco D: (91.718 archivos, 9,1 GB). La comparación archivo por archivo dio
idénticos y los enlaces vuelven a abrir. El respaldo de D: quedó sin tocar.

## Límites que siguen valiendo

- Nada en el Samsung (instalar, ADB, pruebas) sin que Joaquín lo autorice en el momento.
- Nada de commits, push, firma ni publicación sin pedido explícito.
- Registro detallado anterior: [cierre B1](audits/2026-11-03-cierre-b1-vigente.md) ·
  [checkpoint GitHub](audits/2026-11-05-checkpoint-github.md) · [Evidencia](EVIDENCIA.md).
