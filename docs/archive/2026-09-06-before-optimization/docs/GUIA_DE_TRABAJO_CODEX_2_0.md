# Guía humana de trabajo — MiGuardia 2.0 en Codex

- Propietario: Joaquin
- Actualización: 2026-08-23
- Objetivo: que Joaquin marque cuándo nace cada tarea y MAIN se ocupe de la
  integración técnica completa

## 1. Estado actual

La etapa de PLANIFICACIÓN terminó. MAIN 2.0 está activo y ejecuta la hoja de
ruta aprobada de a un bloque por vez.

```text
00 — MAIN — MiGuardia 2.0 [ACTIVA]
01 — PLANIFICACIÓN — MiGuardia 2.0 [CERRADA]
02 — UX/UI — Calendario adaptable [CERRADA]
03 — Reglas por mes [DESCARTADA / HISTÓRICA]
04 — Reglas internas, Room v6 y catálogo Room v7 [CERRADAS]
05 — Primera configuración visible [CERRADA]
06 — Cargar jornadas desde horarios guardados [CERRADA]
07 — Coordinación de handoffs [ACTIVA / BAJO INDICACIÓN DE JOAQUIN]
08 — Corregir o eliminar una jornada [HABILITADA / NO ABIERTA]
```

No hace falta crear otro MAIN. La tarea actual mantiene la continuidad del
proyecto. MAIN no crea sola el prompt ni la tarea siguiente: Joaquin indica
cuándo quiere prepararla y luego entrega su handoff para integrar.

MiGuardia 1.0 sí se conserva como base de código. Lo que se canceló es el
traspaso de datos de una instalación 1.0: no hay usuarios que migrar y V2
comienza limpia.

## 2. Carpeta y rama correctas

Toda tarea de MiGuardia 2.0 trabaja en:

```text
C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0
```

Rama integradora:

```text
codex/miguardia-2.0
```

La carpeta histórica `MiGaurdia` y los worktrees viejos no se limpian ni se
usan como fuente de código para 2.0.

## 3. `main` y MAIN no son lo mismo

- `main` en minúscula es la rama Git protegida de MiGuardia 1.0.0.
- MAIN en mayúscula es la tarea de Codex que dirige MiGuardia 2.0.
- MAIN trabaja sobre `codex/miguardia-2.0`; no necesita una rama llamada
  `main`.

## 4. Archivos, commit y push

Son tres estados distintos:

```text
CAMBIOS EN LA COMPUTADORA
          ↓ commit local
CHECKPOINT EN GIT LOCAL
          ↓ push
COPIA DE LA RAMA EN GITHUB
```

Joaquin autorizó a MAIN a crear automáticamente commits locales cuando un
bloque esté probado y auditado. Eso no autoriza push, tag, Release ni
publicación.

MAIN debe informar siempre:

- qué cambió en archivos;
- qué pruebas se ejecutaron;
- si existe o no un commit local;
- si GitHub continúa sin cambios;
- cuál es el próximo bloque.

## 5. Cómo se ejecuta un bloque

1. Joaquin pide preparar un prompt nuevo o entrega un handoff ya producido.
2. Si pidió el prompt, MAIN lo escribe empezando por explicar qué hace la
   dependencia y por qué existe, lo verifica y crea su checkpoint documental;
   no abre la tarea salvo que Joaquin también lo pida.
3. Al recibir un handoff, MAIN verifica rama, HEAD, base y cambios previos.
4. MAIN revisa cada cambio y confirma que pertenezca al alcance prometido.
5. MAIN agrega o repite pruebas de la conducta nueva.
6. MAIN ejecuta pruebas, lint, compilación y QA proporcionales.
7. MAIN revisa el diff, Room, permisos, privacidad y secretos.
8. MAIN corrige cualquier defecto de integración dentro del alcance.
9. MAIN actualiza las fuentes de verdad.
10. Si todo está verde, MAIN crea automáticamente el commit local del bloque.
11. MAIN informa el resultado, recomienda el próximo paso y espera a Joaquin.

No se mezclan dos migraciones, dos motores o varias pantallas grandes sólo para
avanzar más rápido.

## 6. Cuándo usar una tarea especializada

Joaquin decide cuándo preparar o abrir una tarea especializada. MAIN puede
recomendarla cuando:

- posee archivos y responsabilidad claros;
- el contrato compartido ya está decidido;
- puede verificarse sin inventar decisiones;
- no compite con otro trabajo sobre Room o los mismos archivos Compose.

Cada tarea recibe ruta, rama o base, alcance permitido, prohibiciones, pruebas y
condición de terminado. MAIN revisa su entrega antes de incorporarla.

Además, el prompt y el handoff empiezan con dos explicaciones separadas y sin
jerga: `QUÉ HACE`, para entender el resultado concreto, y `POR QUÉ EXISTE`,
para entender qué problema resuelve y qué parte posterior habilita.

El contrato completo para crear, recibir e integrar esas tareas está en
`docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md`.

## 7. Línea de Git

```text
v1.0.0 / main
82db6fd — MiGuardia 1.0.0 estable
        │
        └─ a3e89fd — Calendario adaptable
                   │
                   └─ 6dab82b — planificación y traspaso inicial
                              │
                              └─ 836d908 — contrato de carga manual V2
                                         │
                                         └─ ca029d1 — coordinador documental
                                                    │
                                                    └─ ae57686 — carga manual V2 integrada
```

La rama 2.0 fue publicada una sola vez en el remoto privado hasta `836d908` para
fijar la base de la carga manual. Esa autorización ya fue consumida: MAIN puede
seguir consolidando checkpoints locales, pero cualquier otro push se decide por
separado.

## 8. Orden aprobado

1. documentación y árbol actual;
2. reglas internas por fecha y referencias de horas;
3. configuración persistente y primera apertura V2;
4. lugares, tipos, plantillas y carga manual;
5. editar o eliminar una jornada individual sin cambiar su fecha;
6. retirar el modo V1 antes de ampliar nuevamente la base, reutilizando el
   código que sirve;
7. recurrencias y edición de una fecha o de todo lo futuro;
8. horario real, extras y avance contra la referencia;
9. guardias pasivas y disponibilidad;
10. Calendario final y tarjeta superior;
11. Resumen personalizable;
12. próximo evento y notificaciones;
13. auditoría integral del núcleo;
14. widget, informes, copias, bloqueo y Ayuda 2.0;
15. auditoría de la aplicación completa y candidato local.

Corrección del 2026-08-27: Calendario final y tarjeta superior es el bloque
siguiente. Las ampliaciones futuras de situaciones especiales o del motor de
horas no se insertan antes sin una nueva indicación de Joaquin.

## 9. Qué no tiene que hacer Joaquin

Joaquin sí elige cuándo quiere el prompt de una nueva tarea, cuándo quiere
abrirla y cuándo entregar su handoff a MAIN.

Joaquin no necesita elegir:

- nombres de clases o tablas;
- comandos de Git o Gradle;
- estructura de paquetes;
- qué prueba técnica corresponde;
- cómo combinar migraciones.

MAIN toma esas decisiones reversibles, muestra el resultado y consulta sólo si
aparece una decisión de producto que cambie materialmente la aplicación.

## 10. Regla final

Un mensaje `BUILD SUCCESSFUL` no significa por sí solo que la función esté
terminada. MAIN diferencia siempre:

- compilación;
- pruebas JVM;
- AndroidTest compilado;
- instrumentación ejecutada;
- recorrido físico;
- pendiente.

La meta no es acumular código ni chats. Es conseguir bloques correctos,
recuperables y entendibles que construyan una MiGuardia 2.0 coherente.
