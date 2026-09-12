# Dependencia 24.a — Página pública de MiGuardia

- Estado: **CERRADO / PUBLICADO — NO REEJECUTAR**
- Cierre MAIN: 2026-09-04; sitio en
  `2799c473877acff2f280759d6a3d398c9f39a455`, incluida la corrección de foco.
- Evidencia y límites:
  [auditoría MAIN](../audits/2026-09-04-pagina-publica-y-preparacion-acceso-beta-android-v2.md).
- Publicación posterior: Site versión 10, fuente `website/341ea0a`, HTTPS
  verificado. El acceso beta público es sólo un aviso de preparación, sin
  formulario ni APIs. Ver `../audits/2026-09-05-publicacion-aportes-y-preparacion-menu-android-v2.md`.
- El cuerpo siguiente conserva el contrato original y su base de entrada como
  trazabilidad; no habilita otro trabajo web ni una nueva publicación.
- Fecha: 2026-09-03
- Autoridad: decisión explícita de Joaquin del 2026-09-03 y ADR 0039
- Proyecto de implementación:
  `C:\Users\Joaquin\Desktop\chatgptprojects\website`
- Proyecto MiGuardia de sólo lectura:
  `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`
- Base funcional MiGuardia:
  `924fd46df018f5e14637dfe90a983fefdbf27af4`
- Base Git del sitio:
  `b66c1f79c247872cf61a30db3af2274606956d30`

## QUÉ HACE

Crea una sección pública propia de MiGuardia en `https://blackatsystems.com/miguardia`.
La página explica en lenguaje claro para qué sirve la aplicación, a quién está
dirigida, qué funciones reales ofrece y cómo protege los datos. Recién al final
presenta el botón `Solicitar acceso a la beta`, que lleva al portal privado ya
existente en `/miguardia/beta`.

No duplica el formulario beta, no entrega claves y no publica ningún cambio.

## POR QUÉ EXISTE

Una persona debe comprender el producto antes de decidir si quiere probarlo.
El portal privado administra plazas, claves y condiciones de participación;
esta página pública funciona como la presentación honesta de MiGuardia y evita
que la primera experiencia sea un formulario sin contexto.

También deja una dirección estable para explicar MiGuardia aunque la campaña
beta esté cerrada, completa o todavía no publicada.

## ROLE

Sos la única dependencia implementadora activa de la página pública de
MiGuardia. Trabajás directamente en el proyecto guardado `website`. El
repositorio `MiGuardia-2.0` es exclusivamente de lectura y sirve como fuente de
verdad para las capacidades y límites del producto.

No sos MAIN, no creás otras tareas y no redefinís el producto. Entregás el
candidato sin commit para que MAIN audite el diff y decida su integración.

## PUERTA 0

Antes de editar:

1. verificá la ruta exacta
   `C:\Users\Joaquin\Desktop\chatgptprojects\website`;
2. leé completamente el `AGENTS.md` del sitio y respetá que toda escritura
   quede dentro de ese proyecto;
3. verificá rama `main` y HEAD exacto
   `b66c1f79c247872cf61a30db3af2274606956d30`;
4. verificá que no exista remoto Git configurado;
5. verificá que `.openai/hosting.json` conserve el proyecto existente y los
   nombres lógicos `DB` y `BETA_APKS`;
6. confirmá que se reutiliza el Site existente de Blackat Systems y que no se
   crea otro;
7. comprobá que el árbol preexistente coincide con la fotografía siguiente;
8. detenete ante una diferencia inexplicada o un escritor concurrente. No uses
   reset, clean, checkout destructivo ni descartes trabajo.

Configuración exacta esperada:

```json
{
  "project_id": "appgprj_6a90fe153dac8191b8ea5984b4a7e871",
  "d1": "DB",
  "r2": "BETA_APKS"
}
```

### Fotografía preexistente que debe preservarse

Archivos rastreados ya modificados antes de esta dependencia:

```text
app/globals.css  E179E14FAE129171E24EEEFC417900EB87957314E73A38CF8A51FD7ACCFC2980
app/layout.tsx   FB757520882C51DE7E9E838A21EECCFB7959AD80F435500F62E8218AEB6D2E9D
app/page.tsx     CC41296A947772980E508A883AE7A39F934B0C1FF0A6ABBD1990E3A18D18698B
```

Esos hashes SHA-256 describen el contenido completo al habilitar este prompt.
`app/page.tsx` puede recibir únicamente la integración mínima indicada más
abajo. Preservá todos sus demás cambios preexistentes.

Archivos no rastreados preexistentes que no se deben editar, borrar, mover ni
incorporar al staging:

```text
.openai/site-build-2d30584.tar.gz
.openai/site-build-397a7ed.tar.gz
.openai/site-build-b43685f.tar.gz
.openai/site-build-b83015d.tar.gz
AGENTS.md
INTERFACE_DESIGN/6d5ee1775add2c466b28f5ca73dfe979.jpg
INTERFACE_DESIGN/792a029c88125c99483e4be3d81bd2a1.jpg
INTERFACE_DESIGN/f989614eacd34db177cecedbf59350e5.jpg
```

`AGENTS.md` debe leerse, pero sigue siendo una excepción conocida de la
fotografía. Los paquetes de build y las imágenes de referencia no pertenecen a
esta dependencia.

## LECTURA OBLIGATORIA

En este orden:

1. `website/AGENTS.md` completo;
2. las skills `sites-building` y `sites-hosting`, con sus referencias
   obligatorias; como este contrato es local, `sites-hosting` confirma la puerta
   de publicación pero no autoriza a cruzarla;
3. `.openai/hosting.json`, `package.json`, lockfile, configuración de build y
   estructura actual del sitio;
4. `app/layout.tsx`, `app/page.tsx`, `app/globals.css` y los componentes que
   definan la identidad visual vigente;
5. `app/miguardia/beta/**`, sin cambiar su lógica de campaña;
6. `docs/MIGUARDIA_BETA_API_V1.md` y
   `docs/MIGUARDIA_BETA_OPERATIONS.md`;
7. desde `MiGuardia-2.0`, sólo lectura: `AGENTS.md`,
   `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`, `docs/STATUS.md`,
   `docs/PLANIFICACION_MIGUARDIA_2_0.md`, ADR 0039 y este prompt;
8. código o textos de MiGuardia necesarios para confirmar cada afirmación
   funcional que se vaya a publicar.

## TASK

Implementar y validar localmente la página pública `/miguardia`, integrada con
el portfolio existente y con un único llamado de participación al final que
enlace a `/miguardia/beta`.

## CONTEXT

- Blackat Systems ya tiene un sitio y una identidad visual; no se diseña otro
  producto ni otro Site.
- El portal `/miguardia/beta` ya existe localmente y administra sus diez plazas.
- La aplicación está técnicamente avanzada, pero todavía no existe una APK
  oficial publicada ni una Release pública.
- La página debe explicar el producto sin convertir el estado interno del
  desarrollo en una lista técnica de commits, Room o conteos de pruebas.
- MiGuardia es Android, local-first y sirve a cuatro sectores exactos:
  Vigilancia privada, Policía, Enfermería y Medicina.
- `Salud`, `Otro` y Psicología no forman parte del catálogo actual.

## INPUTS

- La identidad bilingüe español/inglés y el selector de idioma del portfolio.
- La tarjeta actual de MiGuardia dentro de `app/page.tsx`.
- El portal beta local existente en `app/miguardia/beta/**`.
- Las capacidades y límites confirmados en las fuentes de verdad de MiGuardia.
- Los estilos, tipografías, colores y recursos ya aprobados del sitio.

## OUTPUT

1. Ruta pública local `/miguardia` completa y navegable.
2. Metadatos honestos y canónicos para esa ruta.
3. Presentación bilingüe coherente con el selector de idioma existente.
4. Enlace desde la tarjeta MiGuardia del inicio hacia `/miguardia`.
5. Enlace de regreso del portal beta hacia `/miguardia`.
6. Único CTA de conversión beta al final de la nueva página:
   - español: `Solicitar acceso a la beta`;
   - inglés: `Request beta access`.
7. Pruebas de contenido, navegación, accesibilidad y regresión acordes al
   proyecto.
8. Handoff completo a MAIN, sin commit.

## CONTRATO DE CONTENIDO

La página debe contar una historia clara. El orden recomendado es obligatorio
en intención, aunque el diseño visual puede resolverlo con secciones propias.
No alcanza con nombrar cuatro beneficios generales: debe existir un inventario
visible y comprensible de todas las familias funcionales confirmadas abajo. Se
pueden agrupar o desplegar detalles, pero ninguna capacidad debe quedar oculta
exclusivamente detrás de una interacción inaccesible.

### Inventario funcional mínimo

- elección inicial entre los cuatro sectores y configuración laboral única;
- lugares, tipos de trabajo, colores y horarios reutilizables;
- una sola grilla mensual para consultar y elegir fechas;
- carga individual o de varios días;
- repetición finita y cambios para una fecha o desde una fecha hacia adelante;
- edición y eliminación exacta de jornadas;
- francos, marcas existentes `F/?` y estados de jornada, incluidos ausencia y
  cancelación, explicados sin trasladar códigos internos incomprensibles;
- feriados, vacaciones, carpetas médicas, notas privadas y fotos del cronograma;
- horario planificado, horario real y vuelta consciente al planificado;
- horas extra de una jornada, sus clases y extras independientes;
- referencia de horas elegida por la persona y avance sin metas inventadas;
- Guardia pasiva, Disponible para llamado o Retén, separadas del trabajo hecho;
- tarjeta de hoy, Calendario final y detalle del día;
- Resumen mensual personalizable con explicación de sus cifras;
- próximo evento y notificaciones locales con opciones de privacidad;
- clima opcional e independiente por lugar;
- Widget de próximo evento configurable por instancia;
- informes locales PDF/XLSX;
- copias locales, cifrado opcional y restauración segura por combinación o
  reemplazo total confirmado;
- bloqueo de acceso opcional mediante la seguridad del teléfono;
- Ayuda, recorrido inicial, tema claro/oscuro y zoom interno.

Antes de redactar la página, contrastá este inventario con el código y las
fuentes vigentes. Si algo no está realmente integrado, no lo anuncies como
disponible: informalo a MAIN en vez de completar el vacío con una promesa.
Estas capacidades comunes no autorizan a inventar un flujo V2 nuevo o ampliado
de situaciones especiales.

### 1. Presentación

- Qué es MiGuardia: una aplicación Android para organizar jornadas, horas y
  avisos laborales sin depender de planillas.
- Para quiénes existe: Vigilancia privada, Policía, Enfermería y Medicina como
  cuatro opciones independientes.
- Idea central: una sola configuración laboral y un solo Calendario mensual.
- Estado honesto: `Beta privada en preparación` o una formulación equivalente;
  no anunciar disponibilidad pública ni una Release inexistente.

La apertura puede invitar a recorrer las funciones, pero no debe mostrar un
botón hacia la beta. Joaquin pidió que esa petición quede al final.

### 2. Organizar el trabajo

Explicar en palabras cotidianas:

- lugares de trabajo, tipos y horarios reutilizables;
- carga de una o varias jornadas desde la única grilla mensual;
- repetición finita de jornadas;
- edición o eliminación de una jornada y cambios desde una fecha;
- fotografías históricas para que un cambio futuro no reescriba el pasado.

### 3. Registrar lo que realmente ocurrió

- horario planificado y horario real separados;
- horas extra pertenecientes a una jornada;
- trabajos extra independientes;
- referencia de horas elegida por la persona y avance calculado sin inventar
  metas del sector;
- Guardia pasiva, Disponible para llamado o Retén, sin contarlos como trabajo
  realizado.

No presentar salarios, liquidaciones, escalas, deducciones ni asesoramiento
laboral.

### 4. Entender el mes y anticiparse

- Calendario único y tarjeta superior de hoy;
- Resumen mensual personalizable y explicación de cada cifra;
- próximo evento y avisos locales;
- widget configurable con privacidad;
- clima opcional por lugar cuando la persona aporta una dirección o solicita
  una captura puntual aproximada.

No prometer seguimiento de ubicación: no existe ubicación en segundo plano ni
rastreo.

### 5. Conservar el control de los datos

- datos laborales locales por defecto;
- informes PDF y XLSX creados conscientemente;
- copias locales cifradas y restauración mediante combinación o reemplazo con
  confirmación;
- bloqueo opcional usando la seguridad del teléfono;
- Ayuda y recorrido inicial;
- apariencia y zoom interno 100 %, 150 % o 200 %.

Explicar de forma destacada y comprensible:

- MiGuardia no usa cuentas laborales, nube, sincronización, analítica ni
  telemetría;
- el programa beta no recibe Calendario, jornadas, lugares, notas, fotos ni
  información médica;
- Internet se usa sólo en capacidades voluntarias que lo requieren, como clima
  y el servicio de la beta.

### 6. Límites honestos de esta beta

- Sólo Android.
- APK todavía no publicada en esta etapa.
- Sin Google Play en la primera prueba externa.
- Sin cuentas ni sincronización entre dispositivos.
- Las diez plazas limitan el piloto, pero no constituyen DRM ni impiden de forma
  absoluta que alguien redistribuya un APK obtenido.
- Participar no exige una opinión positiva.

### 7. Cierre y petición de acceso

Ésta debe ser la última sección sustantiva. Debe explicar, antes del botón:

- que la beta tendrá diez plazas;
- que se puede usar un alias y el contacto es opcional según el portal vigente;
- que la persona acepta dar una devolución honesta después de quince días;
- que sus datos laborales no se envían ni se utilizan;
- que entrar al portal todavía no garantiza una descarga mientras la campaña o
  la publicación no estén abiertas.

El único botón o enlace con función de conversión beta debe estar aquí y llevar
a `/miguardia/beta`. No duplicar el formulario ni mostrar o reservar claves en
`/miguardia`.

## UX Y DISEÑO

- Extender la identidad Blackat vigente: profesional, angular, directa y con
  personalidad; no crear una landing genérica desconectada del portfolio.
- Diseñar una lectura narrativa con jerarquía clara, aire y agrupación visual;
  no volcar una pared de texto ni una grilla uniforme de tarjetas repetidas.
- Usar español argentino natural y una traducción inglesa completa, no literal
  ni incompleta.
- Conservar el mecanismo de idioma existente y evitar un cambio visible de
  idioma durante la hidratación.
- Reutilizar estilos o componentes cuando sea razonable, sin forzar la página
  dentro de abstracciones que la vuelvan ilegible.
- No inventar capturas de la aplicación. No usar los JPG no rastreados de
  `INTERFACE_DESIGN` ni generar imágenes nuevas sin autorización.
- Si se reutiliza la ilustración de Calendario del inicio, tratarla como una
  representación conceptual y no como una captura exacta del producto.
- Encabezados semánticos, enlace para saltar al contenido, navegación por
  teclado, foco visible, contraste suficiente y textos de enlace explícitos.
- Responsive sin desborde horizontal en 390×844 y 1280×720.
- Reflujo usable con zoom del navegador al 200 %.
- Respetar `prefers-reduced-motion`; ninguna animación puede ser necesaria para
  entender o usar la página.

## INTEGRACIÓN MÍNIMA

- En `app/page.tsx`, reemplazar el destino directo al portal por `/miguardia` y
  usar un texto equivalente a `Conocer MiGuardia` / `Explore MiGuardia`.
- Actualizar únicamente las afirmaciones de la tarjeta que hayan quedado
  objetivamente obsoletas, contrastándolas con el estado actual de MiGuardia.
- En el portal beta, cambiar el enlace `Volver al proyecto` para que regrese a
  `/miguardia`.
- No alterar el formulario, la reserva, las claves, los estados de campaña ni
  las rutas API.

## SCOPE

Escritura permitida:

- `website/app/miguardia/**`, excepto cambios funcionales en el portal beta;
- un componente o módulo de estilos nuevo, si pertenece exclusivamente a la
  página pública;
- el mínimo hunk necesario en `website/app/page.tsx`;
- el mínimo hunk de navegación en
  `website/app/miguardia/beta/BetaPortal.tsx`;
- metadatos y pruebas específicas de esta ruta;
- documentación propia del sitio sólo si hace falta registrar el recorrido
  público, sin reescribir el contrato API.

Si la implementación exige tocar otra superficie, detenete y explicá por qué.

## DEPENDENCIES

- Portal beta local integrado en el HEAD del sitio indicado.
- Contrato API v1 existente, que esta dependencia consume sólo como contexto.
- Fuentes de verdad de MiGuardia accesibles en modo lectura.
- No depende de D1/R2 remotos, secretos, APK firmado ni campaña abierta.

## DO NOT

- No modificar `lib/miguardia-beta/**`, rutas API, esquema o migraciones D1,
  fixture de diez plazas, contratos criptográficos ni descarga privada.
- No crear o aprovisionar D1, R2, secretos, dominios, DNS o un Site nuevo.
- No guardar una versión remota ni desplegar o publicar la web.
- No generar, subir ni enlazar una APK.
- No implementar acceso Android, feedback dentro de la app, donaciones,
  actualización de APK, firma ni Release.
- No agregar cuentas, analytics, telemetría, píxeles, cookies de seguimiento,
  CAPTCHA o formularios adicionales.
- No agregar dependencias salvo que una imposibilidad concreta lo justifique y
  MAIN la autorice primero.
- No tocar `MiGuardia-2.0`.
- No tocar, incorporar ni borrar los tarballs, `AGENTS.md` o las imágenes no
  rastreadas preexistentes.
- No hacer commit, push, deploy, rama, worktree, reset, merge, rebase ni clean.
- No prometer funciones no verificadas, una fecha de lanzamiento, soporte iOS,
  una descarga disponible o una privacidad absoluta.

## VALIDATION

Ejecutar con el package manager y los scripts existentes, sin actualizar
dependencias:

1. pruebas específicas de la nueva página;
2. suite completa del sitio;
3. typecheck;
4. lint;
5. validación local de D1 existente para demostrar que no se rompió el portal;
6. build de producción local;
7. `git diff --check`;
8. inventario final de archivos modificados y nuevos.

Comprobar además:

- `/miguardia` responde y contiene una estructura semántica válida;
- el inicio enlaza a `/miguardia`;
- el portal vuelve a `/miguardia`;
- existe un solo CTA de petición beta y está en la sección final;
- ese CTA enlaza a `/miguardia/beta`;
- no existe un formulario de reserva duplicado en la página pública;
- las cuatro profesiones aparecen separadas y no aparecen `Salud` u `Otro`
  como opciones;
- cada afirmación funcional tiene respaldo en las fuentes vigentes;
- no se anuncian APK, Release, publicación o descarga que todavía no existan;
- el portal beta y el portfolio conservan su recorrido actual;
- no cambió ninguna ruta API, tabla, migración, binding, secreto o política de
  campaña;
- no hay errores de consola ni enlaces rotos en la validación local;
- escritorio 1280×720, móvil 390×844, teclado, foco, idioma español/inglés,
  contraste, movimiento reducido y reflujo al 200 % quedan revisados;
- los cambios preexistentes de `app/globals.css`, `app/layout.tsx` y
  `app/page.tsx` se preservan y se distinguen de los hunks propios.

No presentar compilación como publicación. No presentar una captura visual
como prueba de navegación, accesibilidad o ausencia de cambios server-side.

## HANDOFF A MAIN

Entregar en español y con estas secciones:

```text
# HANDOFF A MAIN — Página pública de MiGuardia
## QUÉ HACE
## POR QUÉ EXISTE
## VEREDICTO
## OBJECTIVE
## CHANGES
## FILES
## DECISIONS
## CONTENT CONTRACT
## VALIDATION
## VISUAL AND ACCESSIBILITY QA
## PORTAL REGRESSION
## PRIVACY
## RISKS
## PENDING
## GIT
## NEXT
```

El handoff debe separar claramente:

- VERIFICADO LOCALMENTE;
- COMPILADO;
- REVISADO VISUALMENTE;
- PENDIENTE;
- NO PUBLICADO.

Debe informar ruta, rama, HEAD, fotografía Git inicial y final, archivos
preexistentes preservados, comandos y conteos reales, enlaces recorridos y
cualquier afirmación que no haya podido comprobarse.

## DONE WHEN

- `/miguardia` explica de forma honesta y comprensible las capacidades reales
  de MiGuardia.
- Los cuatro sectores están nombrados correctamente y separados.
- La privacidad local y los límites de la beta son explícitos.
- La página es bilingüe, responsive, accesible y coherente con Blackat Systems.
- La tarjeta del inicio lleva a la página pública.
- El portal beta vuelve a la página pública.
- El único CTA de petición beta está al final y lleva al portal existente.
- No se duplicó ni alteró la autoridad server-side del portal.
- Pruebas, tipos, lint, validación de base local y build están verdes.
- No se creó ningún recurso remoto, secreto, APK, publicación ni cambio Android.
- Git conserva el trabajo preexistente, staging vacío y candidato sin commit.
- MAIN recibe evidencia suficiente para auditar e integrar el resultado.
