# ADR 0017: inicio aislado de MiGuardia 2.0

- Vigencia: PARCIAL
- Sucesores: `docs/adr/0024-continuidad-de-codigo-sin-migracion-de-datos-v1.md`; `docs/adr/0026-base-room-exclusiva-v2-y-retiro-del-modo-v1.md`
- Alcance vigente: Base técnica y referencia protegida conservadas; compatibilidad de datos V1 retirada.
- Fecha: 2026-08-20

> Actualización 2026-08-22: PLANIFICACIÓN quedó cerrada y MAIN está activo bajo
> `PROMPT_MAESTRO_MAIN_2_0.md` (antecedente en el archivo). La pausa anterior quedó superada; esta ADR
> continúa definiendo únicamente la base aislada y protegida de MiGuardia 2.0.
>
> Actualización 2026-08-23: 1.0 continúa siendo la base de código, pero nunca
> tuvo usuarios externos. ADR 0024 elimina la obligación de migrar sus datos o
> mantener un modo V1 dentro de 2.0.

## Contexto

MiGuardia 1.0.0 quedó sellada en Git y publicada como Release estable. La
evolución multiprofesional y el backlog diferido requieren continuar sin alterar
esa referencia ni trabajar sobre una carpeta histórica con cambios sin integrar.

## Decisión

- MiGuardia 2.0 nace del tag `v1.0.0`, commit
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Se trabaja en `codex/miguardia-2.0` dentro del worktree
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Continúa sobre el código de la misma aplicación y mantiene por ahora
  `com.blackatsystems.miguardia`; esto no exige preservar datos de una prueba
  1.0.
- `main`, `origin/main` y `v1.0.0` no se modifican durante la planificación.
- Primero existe PLANIFICACIÓN; MAIN 2.0 nace después de un prompt maestro
  aprobado.

## Consecuencias

- La 1.0 permanece recuperable e inmutable.
- La base Room exclusiva de V2 se define antes de seguir ampliándola. Después de
  su primer corte público, las migraciones sí preservan datos entre versiones
  V2.
- La carpeta histórica `MiGaurdia` no se limpia ni se usa como base de 2.0.
- Crear la rama no autoriza commit, push, tag o publicación.
