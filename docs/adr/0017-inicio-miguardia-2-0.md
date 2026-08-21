# ADR 0017: inicio aislado de MiGuardia 2.0

- Estado: aceptada
- Fecha: 2026-08-20

## Contexto

MiGuardia 1.0.0 quedó sellada en Git y publicada como Release estable. La
evolución multiprofesional y el backlog diferido requieren continuar sin alterar
esa referencia ni trabajar sobre una carpeta histórica con cambios sin integrar.

## Decisión

- MiGuardia 2.0 nace del tag `v1.0.0`, commit
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Se trabaja en `codex/miguardia-2.0` dentro del worktree
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Es una actualización de la misma aplicación: conserva
  `com.blackatsystems.miguardia` y debe preservar datos locales.
- `main`, `origin/main` y `v1.0.0` no se modifican durante la planificación.
- Primero existe PLANIFICACIÓN; MAIN 2.0 nace después de un prompt maestro
  aprobado.

## Consecuencias

- La 1.0 permanece recuperable e inmutable.
- Todo cambio futuro de Room debe incluir migración explícita desde v5 y pruebas
  con datos representativos de las trece entidades.
- La carpeta histórica `MiGaurdia` no se limpia ni se usa como base de 2.0.
- Crear la rama no autoriza commit, push, tag o publicación.
