# Guía humana de trabajo — MiGuardia 2.0 en Codex

- Propietario: Joaquin
- Actualización: 2026-08-21
- Objetivo: continuar sin tener que administrar Git o muchos chats

## 1. Estado actual

La etapa de PLANIFICACIÓN terminó. MAIN 2.0 está activo y ejecuta la hoja de
ruta aprobada de a un bloque por vez.

```text
00 — MAIN — MiGuardia 2.0 [ACTIVA]
01 — PLANIFICACIÓN — MiGuardia 2.0 [CERRADA]
02 — UX/UI — Calendario adaptable [CERRADA]
03 — Reglas por mes [DESCARTADA / HISTÓRICA]
04 — Reglas internas por fecha y horas [SIGUIENTE BLOQUE]
```

No hace falta crear otro MAIN. La tarea actual mantiene la continuidad del
proyecto y puede usar colaboradores internos sin convertirlos en chats que
Joaquin deba seguir.

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

Joaquin autorizó a MAIN a crear commits locales cuando un bloque esté probado y
auditado. Eso no autoriza push, tag, Release ni publicación.

MAIN debe informar siempre:

- qué cambió en archivos;
- qué pruebas se ejecutaron;
- si existe o no un commit local;
- si GitHub continúa sin cambios;
- cuál es el próximo bloque.

## 5. Cómo se ejecuta un bloque

1. Leer las reglas y el prompt habilitado.
2. Verificar rama, HEAD y cambios previos.
3. Implementar sólo el alcance de ese bloque.
4. Agregar pruebas de la conducta nueva.
5. Ejecutar pruebas, lint y compilación proporcionales.
6. Revisar el diff, Room, permisos, privacidad y secretos.
7. Corregir cualquier defecto encontrado.
8. Crear un commit local si todo está realmente verde.
9. Actualizar `docs/STATUS.md` y comenzar el siguiente bloque.

No se mezclan dos migraciones, dos motores o varias pantallas grandes sólo para
avanzar más rápido.

## 6. Cuándo usar una tarea especializada

MAIN puede aislar una tarea cuando:

- posee archivos y responsabilidad claros;
- el contrato compartido ya está decidido;
- puede verificarse sin inventar decisiones;
- no compite con otro trabajo sobre Room o los mismos archivos Compose.

Cada tarea recibe ruta, rama o base, alcance permitido, prohibiciones, pruebas y
condición de terminado. MAIN revisa su entrega antes de incorporarla.

## 7. Línea de Git

```text
v1.0.0 / main
82db6fd — MiGuardia 1.0.0 estable
        │
        └─ a3e89fd — Calendario adaptable
                   │
                   └─ 6dab82b — planificación y traspaso inicial
                              │
                              └─ cambios actuales pendientes de checkpoint
```

La rama 2.0 todavía no fue publicada en GitHub. MAIN puede consolidar
checkpoints locales; el push se decidirá por separado.

## 8. Orden aprobado

1. documentación y árbol actual;
2. reglas internas por fecha y referencias de horas;
3. Room v6 y configuración inicial;
4. lugares, tipos y plantillas;
5. recurrencias y edición del Calendario;
6. horario real, extras y cumplimiento;
7. disponibilidad y situaciones especiales;
8. Calendario final y tarjeta superior;
9. Resumen personalizable;
10. próximo evento y notificaciones;
11. auditoría integral y compatibilidad Android.

## 9. Qué no tiene que hacer Joaquin

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
