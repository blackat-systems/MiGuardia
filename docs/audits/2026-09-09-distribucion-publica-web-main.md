# MAIN — revisión del candidato de distribución pública web

Fecha de evidencia entregada: 2026-09-09 UTC / 2026-09-08 Argentina.
Etapa 19. **WEB ACEPTADA E INTEGRADA LOCALMENTE; NO PUBLICADA.**

Cierre de la corrección: 2026-09-09. Checkpoint web
`394964b98be87ccfd6a56877398545deb9848380`. La etapa 19 global sigue abierta:
Android aún conserva beta y el actualizador privado. El cierre vigente y su
evidencia están al final. Las secciones siguientes conservan la revisión inicial
que rechazó el primer candidato; no describen hallazgos todavía abiertos.

## Revisión inicial — evidencia histórica del rechazo

Revisión de aceptación con bloqueadores reproducidos, no auditoría integral
terminada de cada archivo ni certificación de seguridad. No se modificó código
web/Android, no se instalaron dependencias ni se hizo commit, push o deploy.

## Bases y preservación comprobadas

- Android: `a0b93e56e228076e1c96b77490a5a3725f950ab0`, limpio al entrar.
- Website: `80ebafd9945f51b13b05c708eec50c22fdf0a52b`, candidato compartido sin
  commit, más cambios previos del portfolio. No hay cherry-pick.
- 66 archivos del inventario final entregado coinciden con sus hashes actuales.
- Reversión en memoria del hunk MiGuardia devuelve el hash inicial de portada:
  `6858CCFECB62D5B2C14DD8798AF577A6BC57FA380C801AE28CA5A8EF951914B5`.
- 69 fuentes comprobadas coinciden con el perfil compilado entregado
  `work/public-publication/public-v1-l4yYm7`; sus banderas registran
  `buildVerified=true`, `publishable=false`. No se recompiló ese perfil en MAIN.
- Contrato web SHA-256:
  `515A93D5A3EB45DFA0891DFDF52BB05DA3FADE6D9DE430A151907B10B6E891D7`.
- Audit npm entregado SHA-256:
  `511255C41DA353954D3B4DC670A512CB21FBA7F1A628EDEF5C6A4F1751C2183B`.

Las rutas web de este informe son relativas al proyecto website. No fueron
copiados registros ni secretos al repositorio Android. Las comprobaciones MAIN
crearon únicamente temporales de ensayo bajo `website/work/main-public-review-20260909`.

## Comprobaciones MAIN

1. Lectura de contrato, servicio, validadores, adaptador R2, cliente/hook, borde
   Worker, perfil, diffs pertinentes, inventarios y reporte de dependencias.
2. Dos revisores internos sólo lectura: contrato/consumidor y dependencias.
   No crearon tareas visibles ni fueron escritores.
3. Ejecución nueva de `node --experimental-transform-types --test "tests/*.test.mjs"`
   desde website, con temporales y configuración de herramientas locales:
   **130 aprobadas, cero fallos/cancelaciones/omitidas**. Incluye regresiones beta
   históricas; no significa que ese programa esté habilitado.
4. `git diff --check`: correcto. Inventarios y reproducciones siguientes.
5. Consulta de tres avisos públicos oficiales, sin subir código/lockfile ni
   volver a ejecutar npm audit. El informe de vulnerabilidades sigue siendo
   evidencia entregada, corroborada en sus datos, no un nuevo escaneo MAIN.

No se ejecutaron build, navegador, QA Android, firma, pruebas remotas del
candidato, instalación o actualización. La validación anterior de 50 HTTP y
zoom pertenece a la dependencia, no se atribuye como repetida por MAIN.

## Hallazgos

### S01 — Seguridad de dependencias sin resolver — bloquea publicación

El JSON entregado confirma **16 nombres de paquetes afectados, 17 rutas de
instalación: 1 crítico, 11 altos y 4 moderados**. No son 16 CVE independientes.
La guardia previa al framework y la exclusión de optimizadores reducen caminos
observados, pero no aplican parches ni permiten aceptar todos los residuales.

Fuentes oficiales contrastadas:
- [Next sobre Windows](https://github.com/vercel/next.js/security/advisories/GHSA-p293-qw3h-jr36):
  ramas parcheadas 15.5.24 y 16.3.3; no iniciar Next vulnerable sobre Windows.
- [Optimización AVIF](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4).
- [Server Functions](https://github.com/advisories/GHSA-wx67-qw84-cm4g).

El producto utiliza Vinext/Worker: no se demostró explotación del sitio público.
Tampoco se demostró inmunidad. No usar `audit --omit=dev` para ocultar framework
que, aunque esté declarado de desarrollo, forma parte del Worker compilado.

Proponer un conjunto compatible, no `npm audit fix` masivo:
- Next/configuración ESLint y React/ReactDOM/RSC con peers compatibles.
- Vinext/Vite/plugins y sus parsers, manteniendo contrato y entrada Worker.
- Cloudflare plugin/Wrangler/Miniflare: tienen pins directos y transitivos;
  actualizar sólo el plugin no elimina los pins raíz.
- Herramientas DB históricas y transitivos: no aplicar el downgrade sugerido
  de Drizzle ni overrides que violen rangos sin justificar y comprobar.

Las versiones sugeridas por el JSON no constituyen selección aprobada ni
compatibilidad verificada. Autorizar primero el alcance de actualización
mínima; repetir auditoría permitida, tests, tipos/lint y ambos builds/perfil.
Un residual sin parche exige análisis y aceptación expresa, no una supresión.

### F01 — Retry-After descartado — P2

`lib/miguardia-public/client.ts:7` convierte toda respuesta no-200 en error
genérico y pierde `Retry-After`. `app/miguardia/useAvailability.ts:9` permite
consultar de nuevo; focus/pageshow y el botón del componente lo disparan sin
presupuesto de espera. Contrato v1 exige respetarlo para 429.

Reproducción MAIN: invocar dos veces el cliente productivo con un transporte
controlado que responde 429, JSON RATE_LIMITED y `Retry-After: 60`. Resultado:
**2 solicitudes inmediatas**, errores sin propiedad de espera. La conexión de
ese cliente con los disparadores de UI se comprobó estáticamente; no se ejecutó
esa secuencia en navegador.

Cierre: resultado tipado, espera acotada y comprobable, bloqueo de reintentos
manuales/por foco durante el plazo, estado comprensible y regresiones temporales.
No generar polling ni una nueva infraestructura por esta corrección.

### F02 — Oráculos ambiguos/contradictorios para Android — P2

`tests/fixtures/public-distribution/android-oracles.json:124` modifica paquete
y certificado mediante `patch` pero espera INCOMPATIBLE. El esquema exige sus
literales y `contract.ts:39`/`:42` rechaza metadatos alterados.

Reproducción MAIN: aplicar cada patch sobre `available.json/current_release`
y llamar `parseReleaseResponse`: ambos devuelven **INVALID_RESPONSE**, mientras
los oráculos declaran INCOMPATIBLE. El README no define otra base de los patches.

Cierre: separar metadatos inválidos de metadatos válidos frente a una instalación
de paquete/certificado incompatible; definir todas las entradas/defaults y
convertir los oráculos en casos ejecutables. No fijar Android desde ejemplos
contradictorios. No hace falta rehacer el protocolo público.

### F03 — El perfil incorpora portada ajena pendiente — P2 de entrega

`scripts/public-publication.mjs:8` toma portada/layout/CSS completos del checkout;
`tests/public-publication.test.mjs:11` exige esa incorporación. Preservar bytes
originales no equivale a autorizar su inclusión en una futura publicación.

MAIN comprobó mediante `publicSources` que el perfil contiene la propuesta
comercial de portada, contactos y metadatos globales preexistentes. El perfil
anterior aislaba esos cambios de la publicación de MiGuardia. No hubo deploy
accidental: `publishable=false` y los límites de la dependencia se conservaron.

Cierre: selección explícita de fuentes aprobadas para publicación, sin borrar
ni revertir el checkout compartido. Excluir el delta ajeno por un perfil de
proveniencia trazable, o requerir su aprobación separada. No absorberlo en el
commit de distribución MiGuardia ni presentarlo como fix necesario del producto.

## Decisión y continuación

El protocolo tiene una arquitectura coherente con ADR 0041: lectura pública,
selector inmutable, catálogo vacío honesto y administración excluida. **No se
aceptan todavía contrato/oráculos ni candidato web completo.** Primero resolver
F01/F02 y la selección de F03. S01 mantiene el bloqueo de publicación; el contrato
podrá aceptarse por separado cuando sus entradas y resultados sean inequívocos.

Devolver un único paquete al mismo frente web: saneamiento compatible autorizado,
correcciones, regresiones, perfil, pruebas y handoff actualizado. No abrir una
dependencia por paquete o botón ni reiniciar lo ya implementado. MAIN no despachó
otra tarea ni modificó manifest/lockfile. Soporte y feedback siguen sin decisiones
materiales y sin recolección habilitada. Firma/QA de entrega vienen después.

## Cierre MAIN del candidato corregido — 2026-09-09

**Aceptados el contrato API v1, el corpus ejecutable v2 y el frente web local.**
No se declara seguridad absoluta ni cierre de Android, firma o distribución.
Se siguió la metodología MAIN: revisión del delta, dos revisores internos de
sólo lectura, comprobaciones independientes y checkpoint de alcance acotado.
Las skills de Sites mantuvieron separado el perfil aprobado del checkout sucio
y de cualquier publicación remota.

### Hallazgos resueltos

- **S01 cerrado localmente:** conjunto de doce dependencias actualizado y tres
  overrides documentados. Audit completo repetido por MAIN: cero vulnerabilidades
  conocidas, sin excluir desarrollo. El override `esbuild 0.25.12` del cargador
  histórico queda fuera de su rango declarado: se acepta para los usos concretos
  comprobados mediante cargador real, transformaciones, mapas, importación,
  CORS y Drizzle; no implica soporte universal de upstream. Cuatro regresiones
  de compatibilidad incluidas en la batería. Miniflare permanece en 4 estable.
- **F01 cerrado:** error HTTP tipado y plazo monótono compartido por botón,
  foco y regreso; 429/503, segundos/fecha, fallback y máximo acotados, sin polling.
  La recarga completa no conserva el plazo, límite documentado. Las pruebas
  incluyen la frontera exacta de 59.999/60.000 ms.
- **F02 cerrado:** 24 casos ejecutables con entradas completas. Metadatos
  de paquete/certificado ajenos son `INVALID_RESPONSE`; instalación incompatible
  frente a metadatos válidos es `INCOMPATIBLE`. Comparar versión ocurre después
  de validar respuesta y compatibilidad. El modelo web no acredita Android/JCA.
- **F03 cerrado:** portada/layout/CSS/marca desde la base aprobada `b83015d`,
  transformación de enlace aprobada desde `341ea0a` y fuentes propias actuales
  seleccionadas. Proveniencia y hashes por archivo; regresión contra inclusión
  de cambios ajenos. El perfil no incorpora DB ni runtime beta privado.

### Evidencia nueva ejecutada por MAIN

Raíz relativa a website: `work/main-public-corrected-20260909/`.

| Comprobación | Resultado y archivo |
|---|---|
| Suite global | 163/163, cero fallos/cancelaciones/omisiones; `test.log` |
| Typecheck | Salida 0; `typecheck.log` |
| Lint | Salida 0, cero errores y un aviso ajeno en portada preservada; `lint.log` |
| Build del checkout | Aprobado; `build.log` |
| Build/tipos del perfil | Aprobados; `profile.log` |
| Worker compilado por loopback | 40/40; `http.json` |
| Audit completo de dependencias | Cero vulnerabilidades conocidas; `audit.json` |
| Escaneo local de secretos | Sin secreto productivo confirmado; `secrets.json` |
| Diff de trabajo e índice | `git diff --check` correcto |

Los comandos de prueba, tipos, lint, build, perfil, preview y audit usaron
`node scripts/run-public-local.mjs`; HTTP usó `scripts/verify-public-http.mjs`.
El escaneo usó `scripts/audit-public-secrets.mjs` sobre el perfil nuevo y Git:
140 blobs alcanzables, 444 archivos de árbol/artefactos y 444 entradas comprimidas.
Dos señales corresponden al mismo `TEST_PEPPER` sintético en prueba e historia.
Es un escaneo por patrones, no prueba de ausencia universal de secretos.

Perfil nuevo de MAIN: `work/public-publication/public-v1-rPB5GJ/`.
Sus 70 fuentes coinciden con los hashes registrados; `buildVerified=true` y
`publishable=false`. Se compiló antes del checkpoint con los mismos insumos
aceptados: el commit no se presenta como otra compilación. El preview propio
quedó detenido; el endpoint local dejó de responder.

Las evidencias de navegador, zoom y descarga sintética de la dependencia fueron
revisadas, no ejecutadas nuevamente por MAIN. No se acredita una APK mediante
el archivo sintético de 32 bytes. No se ejecutaron Android, ADB, firma, TLS/CDN/IAM
remotos, lector de pantalla, instalación ni publicación.

### Contrato fijado y preservación

- API: `docs/MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md`, SHA-256
  `9AD6C934A20D9A766AAA7DAA3C221EB33589145F78CF985B74DF5230C2D76B00`.
- Corpus: `tests/fixtures/public-distribution/android-oracles.json`, SHA-256
  `D086F721C29A806FD41924529FDA1BDEC9909F8492E4FC492D6882616F587072`.
- `package.json`: `1F7AD364F3BBD215260A57039100E5EC4AA8080130729688D01918CEA6C63374`.
- Lockfile: `4B1AEB1F232103DEFBF4487739BBB66BC75A13D38F702C7FEDB5BE98D47DF682`.
- 71 archivos del inventario corregido coinciden con sus huellas; 850 archivos
  Android de entrada permanecieron idénticos antes del registro documental MAIN.
- El commit web incluye 64 archivos, 3.908 inserciones y 979 eliminaciones.
  Sólo se incorporaron las sustituciones MiGuardia de `app/page.tsx` al índice;
  el contenido de trabajo no se reescribió. CSS/layout, resto de portada,
  cuatro tarballs, AGENTS y referencias visuales quedan fuera del checkpoint.
- Website sin remotos y con un worktree; Android conserva nueve. Sin push,
  ramas/worktrees nuevos, cambios productivos Android ni publicación.

### Continuación habilitada y límites

Preparar una única dependencia Android completa: retirar acceso/feedback beta
obligatorio y protocolo privado, consumir API pública v1 y los 24 oráculos,
conservar TLS, límites, cancelación, reloj monótono, tamaño/hash, paquete,
certificado, ABI, rechazo de downgrade, SAF, borradores y datos existentes.
No reenviar colas antiguas ni inventar otra API. La etapa global no está cerrada.

Catálogo `approvedRelease=null`; ningún APK oficial preparado o publicado por
esta revisión. IAM/costos/CDN, firma con la clave existente y actualización real
con datos requieren sus etapas posteriores. Cuotas por instancia y adquisición
R2 conservan los límites documentados. Correo público y feedback voluntario
continúan sin resolver y sin recolección habilitada. La publicación futura debe
generar un perfil nuevo; nunca usar automáticamente el `dist` raíz del portfolio.
