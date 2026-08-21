# Estado de MiGuardia 2.0

## Objetivo activo

Entregar la línea base consolidada e inicializar la tarea MAIN 2.0 con una
Puerta 0 estrictamente de solo lectura.

## Base verificada

- Worktree: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Rama: `codex/miguardia-2.0`
- Base: tag anotado `v1.0.0`
- Commit base: `82db6fd8eb2c511205968894dc9857a96b16ed20`
- `main`, `origin/main` y `v1.0.0^{}` coincidían en ese commit al crear el worktree.
- Aplicación heredada: `com.blackatsystems.miguardia`
- Persistencia heredada: Room v5, trece entidades y migraciones explícitas
  `1→2→3→4→5`.

## Terminado

- MiGuardia 1.0.0 fue sellada y publicada como fuente estable.
- Se creó un worktree independiente y limpio para 2.0.
- Se decidió que 2.0 será una actualización de la misma aplicación y conservará
  los datos de 1.0.
- Se rechazaron múltiples perfiles laborales: existe una sola configuración
  laboral por usuario.
- Se resolvió conceptualmente el tratamiento de base mensual, retén/cubrefranco
  y horas extra informadas; ver ADR 0018.
- Se decidió cuantificar la guardia pasiva en un apartado propio. Cuando existe
  trabajo activo, cada minuto activo reemplaza al minuto pasivo superpuesto: el
  intervalo se computa una sola vez como activo y se descuenta de la pasiva.
- Se implementó y auditó la Puerta U del Calendario adaptable. La tarjeta de
  próximo evento se compacta sin perder información, los controles normales del
  mes aprovechan una fila al 100 %, la grilla conserva su tamaño y, cuando hay
  desborde, aparece una barra vertical persistente con pulgar proporcional.
- El contenido completo permanece alcanzable al 100 %, 150 % y 200 %, incluida
  la columna derecha y la acción inferior.
- Evidencia del 2026-08-20: 175 pruebas JVM y 172 instrumentadas en el Samsung
  `SM-S938B`, sin fallos, errores ni omitidas; lint con cero errores,
  empaquetados requeridos aprobados y `git diff --check` correcto. Ver
  `docs/audits/2026-08-20-calendario-adaptable-2-0.md`.
- La Puerta U quedó consolidada en el checkpoint
  `a3e89fdb56aedeed77c89824cec137f37f4c9619` sin tocar `main` ni `v1.0.0`.
- Se cerró el vocabulario común y sectorial para Vigilancia privada, Policía,
  Salud y Otro sin duplicar el dominio heredado.
- Se cerró el contrato de horas `R/D/P/B/M/E/F/T`, incluidas pasivas,
  intervenciones, extras, superposiciones, vigencias y matriz de ejemplos.
- Se diseñó la migración no destructiva: Perfil V1 permanece en DataStore y
  Room v6 agrega revisiones mensuales de la única configuración laboral.
- Se creó `docs/PROMPT_MAESTRO_MAIN_2_0.md` como traspaso autosuficiente.
- La planificación y el prompt de MAIN fueron reauditados contra los contratos
  reales de Room v5, DataStore, horas V1 y la regla pasiva→activa.

## Pendientes no bloqueantes

- reglas remunerativas ajenas a SUVICO y vacíos monetarios todavía no
  demostrados;
- monetización y distribución;
- orden fino de widgets, informes, copias/restauración y bloqueo después del
  núcleo de configuración y horas;
- logo y tipografías definitivas.

## Bloqueado deliberadamente

- MAIN 2.0 no debe modificar comportamiento durante su Puerta 0 ni implementar
  el Incremento 1A antes de informar y cerrar esa inspección.
- La excepción UX/UI previa a MAIN quedó ejecutada y validada. No habilita por sí
  sola otros cambios funcionales ni de datos.

## Próximo paso

Crear la tarea `MAIN — MiGuardia 2.0` sobre esta rama limpia, ejecutar únicamente
su Puerta 0 y, después del informe, iniciar el Incremento 1A de dominio puro.
