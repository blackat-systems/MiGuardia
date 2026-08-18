# Cierre de base canónica — 2026-08-18

## Resultado ejecutivo

La continuidad oficial de MiGuardia quedó establecida en `main` mediante un avance lineal, sin merge commit ni reescritura de historia. Inmediatamente después de la promoción, las cuatro referencias relevantes quedaron alineadas en:

`e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f`

| Referencia en la instantánea de promoción | SHA verificado |
|---|---|
| `main` | `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f` |
| `origin/main` | `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f` |
| `codex/main-3` | `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f` |
| `origin/codex/main-3` | `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f` |

`main...codex/main-3` y `main...origin/main` informaron cero commits de diferencia. El árbol común verificado fue `17eff812e5e2427a98ab862993f60d373a158f77`. Este SHA es el punto de promoción, no un HEAD permanente: los commits posteriores continúan sólo en `main` y no requieren mover nuevamente `codex/main-3`.

## Operación autorizada

Joa autorizó expresamente:

1. crear `codex/recovery-main-pre-consolidation` conservando los 19 cambios de la carpeta principal;
2. avanzar la `main` local por fast-forward hasta `e3caf6f`;
3. publicar ese mismo SHA en `origin/main`.

La carpeta histórica `C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia` dejó de mantener abierta `main` y quedó en `codex/recovery-main-pre-consolidation`, SHA `e156b64de99dd0f2b514bf8f58ca32aea6ed9500`. Sus 19 entradas continuaron intactas: siete rastreadas modificadas, doce no rastreadas y ninguna staged.

La `main` local se movió atómicamente desde `e156b64de99dd0f2b514bf8f58ca32aea6ed9500` hasta `e3caf6f4acba8af8a1ff27620b7c8c99a4ff176f`, comprobando el SHA anterior esperado. `origin/main` avanzó por fast-forward desde `c50790170896387a7cb006cf39e80f58e944af27` hasta el mismo destino. El worktree limpio `C:\Users\Joaquin\.codex\worktrees\6883\MiGaurdia` quedó seleccionado sobre `main` como entorno operativo.

No se ejecutó merge, rebase, force push, stash, descarte, borrado, limpieza de worktrees ni modificación de archivos de la carpeta histórica.

## Recuperación preservada

Además de la rama de recuperación, continúa disponible el punto externo:

`C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia_RECOVERY_2026-08-17_PUERTA0`

Contiene los 19 archivos, inventario, hashes, línea base, instrucciones y el parche `tracked-working-tree.patch`. Su SHA-256 verificado es:

`AFEB26FD6688D7EA8CBFBB6793CA5831AC71546ED04B5888565CE9D5AE6C4A42`

No se autoriza borrar ninguna de estas dos capas de recuperación por el solo hecho de haber promovido `main`.

## Evidencia de aplicación conservada

La promoción sólo modificó referencias Git; no cambió el árbol ejecutable auditado. Por política de validación por impacto se conserva la evidencia verde de Puerta 0:

- 162 pruebas JVM aprobadas: `app` 28, `core:domain` 129 y `core:database` 5;
- Lint sin errores;
- `assembleDebug` y `assembleRelease` aprobados;
- Room v5, trece entidades, esquemas v1 a v5 y migraciones explícitas `1→2→3→4→5`;
- Samsung Galaxy S25 Ultra `SM-S938B`, API 36, observado conectado durante la auditoría, con el paquete productivo intacto.

No se repitieron Gradle ni instrumentación física porque no hubo cambios de código, recursos, manifiesto, dependencias, Room ni comportamiento. No se instaló, desinstaló ni borró nada del teléfono.

## Próximo incremento obligatorio

La consolidación de base quedó cerrada. Antes de iniciar Perfil laboral debe implementarse únicamente la brecha ya documentada de Notificaciones:

- `Eliminar notificación` dentro de la vista expandida;
- `Mostrar notificación nuevamente` desde Configuración, con restauración individual y, si corresponde, total.

Ese incremento debe partir de `main`, construir su mapa de impacto, incluir pruebas y repetir solamente el QA físico que el cambio invalide. La clasificación y eventual eliminación de worktrees históricos continúa siendo una operación separada que requiere inventario individual y autorización explícita de Joa.
