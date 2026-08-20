# MiGuardia — dependencia especializada NOTIFICACIONES: PULSO VIGILIA

> Autorización de Joa: 18 de agosto de 2026.
>
> Rama asignada: `codex/notifications-pulso-vigilia`.
>
> Base obligatoria: `3e1b286a58307b55d7f1d2ac1b7ffa58dacccc87` (`feat: unify calendar editing grid`).
>
> Worktree: `C:\Users\Joaquin\.codex\worktrees\PULSO_VIGILIA_NOTIFICACIONES\MiGaurdia`.
>
> Room v6 autorizado: **NO**. Commit, push y merge autorizados: **NO**.

## 1. Rol y autoridad

Sos la dependencia especializada **NOTIFICACIONES — PULSO VIGILIA** de MiGuardia. Tu tarea es convertir la notificación de guardia existente en una tarjeta de estado reconocible, clara y personalizable, conservando el motor local ya integrado y verificando el resultado real en Android.

La instrucción explícita vigente de Joa reabre y prioriza Notificaciones, y reemplaza únicamente la frase histórica de `docs/PROMPT_MAESTRO_MAIN.md` que indicaba no volver a ampliarlas. No reabre Room, calendario, horas, clima, navegación, otras profesiones ni servicios externos.

MAIN conserva la integración. En este worktree:

- no hagas commit, push, merge, rebase ni publicación;
- no modifiques trabajo ajeno ni otros worktrees;
- entregá un diff sin confirmar, auditable y probado;
- detené cualquier decisión que requiera Room v6, un permiso nuevo, una dependencia nueva o un cambio material no descrito aquí.

Jerarquía:

1. instrucción explícita y actual de Joa;
2. `docs/PROMPT_MAESTRO_MAIN.md`, incluida la autorización Pulso Vigilia;
3. `AGENTS.md`;
4. este prompt;
5. `docs/prompts/NOTIFICACIONES.md`, `docs/adr/0010-notificaciones-y-alarmas-locales.md` y `docs/prompts/VIGILIA_SISTEMA_VISUAL.md`;
6. implementación y pruebas vigentes.

## 2. Condición de inicio

Antes de editar, comprobar y registrar:

- ruta absoluta del worktree;
- rama actual;
- `git status --short` limpio;
- `git rev-parse HEAD` igual a la base obligatoria;
- `git worktree list` sin reutilizar worktrees históricos de Notificaciones;
- `main` limpio y sincronizado con `origin/main` en la base indicada;
- Samsung `SM-S938B` visible por ADB;
- lectura completa de las fuentes de autoridad y del código/pruebas afectados.

No trabajar en la raíz histórica `C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia`, porque contiene cambios ajenos no consolidados.

## 3. Estado verificado de la base

La base ya contiene:

- planificador puro con fronteras `REMINDER`, `START` y `END`;
- reconciliación reactiva e idempotente;
- alarmas únicas, exactas cuando el acceso existe e inexactas como degradación;
- identidad estable por UUID y agrupación de guardias simultáneas;
- `DecoratedCustomViewStyle`, `RemoteViews` compacta/expandida y `Chronometer` nativo;
- transición a `Guardia en curso` y cancelación al finalizar;
- configuración global y excepción por guardia, con hasta cinco anticipaciones;
- privacidad completa, reducida y oculta;
- sonido del sistema o elegido mediante Android;
- comportamiento fijo/descartable;
- tres acciones seguras;
- ocultamiento explícito y restauración individual/total;
- clima opcional desde caché fresca, sin bloquear la publicación;
- QA aislado mediante el paquete `.qa`.

La base visual es funcional pero plana: la compacta tiene dos líneas genéricas y la expandida no expresa todavía una jerarquía propia de Vigilia. La configuración carece de vista previa, notificación de prueba y ritmos comprensibles.

## 4. Resultado de producto

La experiencia se denomina internamente **Pulso Vigilia**. Su firma visual es el **Trazo de guardia**: una franja vertical fina con el color histórico de la combinación objetivo+horario.

El usuario no necesita aprender esos nombres. En la interfaz se usan expresiones directas: `Notificaciones`, `Próxima guardia`, `Guardia en curso`, `Cómo avisar` y `Enviar notificación de prueba`.

Principio rector:

> **Vigilia no grita. Señala.**

La experiencia visible es siempre un recordatorio común. Nunca es una alarma despertador, pantalla completa, sonido en bucle ni interfaz de fichaje.

## 5. Ciclo de una tarjeta

```text
Sin tarjeta
    ↓ primera frontera configurada
PRÓXIMA GUARDIA
cuenta regresiva hasta la entrada
    ↓ recordatorios posteriores
misma identidad, contenido actualizado, una señal breve según el ritmo
    ↓ frontera START, actualización silenciosa
GUARDIA EN CURSO
cuenta regresiva hasta la salida
    ↓ frontera END
cancelación silenciosa
```

Reglas:

- una guardia usa una sola identidad visible; los recordatorios no se apilan;
- el cronómetro se actualiza mediante Android, sin polling ni alarmas por minuto;
- START cambia estado y destino del cronómetro sin volver a alertar por defecto;
- END cancela; no deja una notificación adicional de “completada”;
- editar, eliminar, cancelar, declarar ausencia o entrar en vacaciones invalida el aviso según los contratos existentes;
- una notificación ocultada debe seguir oculta en fronteras posteriores hasta que el usuario la restaure o la guardia deje de ser elegible;
- las fronteras temporales permanecen instaladas para conservar limpieza y reconstrucción, pero el receptor no puede republicar un UUID que siga ocultado;
- múltiples guardias elegibles conservan tarjetas separadas y agrupadas.

## 6. Contrato visual del panel Android

### 6.1 Reglas generales

- Conservar `NotificationCompat.DecoratedCustomViewStyle` y `RemoteViews` acotadas.
- Android conserva encabezado, icono, fondo, tipografía final, expansión, acciones y control de descarte.
- Usar estilos de texto de notificación compatibles con variaciones claras/oscuras; no fijar un fondo propio.
- No usar glow, gradientes, imágenes de fondo, animaciones, bordes pulsantes ni apariencia gamer.
- Conservar `minSdk 26` y una presentación equivalente desde Android 8.
- Completar siempre `contentTitle` y `contentText` como fallback para superficies que no apliquen la vista personalizada.
- Color nunca comunica por sí solo: estado, abreviatura y horario permanecen escritos.

### 6.2 Trazo de guardia

- Franja vertical de 3–4 dp.
- Usa `shift.colorArgbSnapshot` en privacidad completa y reducida.
- En privacidad oculta utiliza un acento neutro de MiGuardia para no revelar una asociación visual elegida por el usuario.
- No sustituye ni transforma el color histórico.

### 6.3 Compacta

Prioridad estricta:

1. estado y objetivo;
2. abreviatura + horario completo;
3. cuenta regresiva.

Referencia:

```text
PRÓXIMA · Hospital Norte
▌ NOR · 19:00–07:00        En 03:12
```

En curso:

```text
EN CURSO · Hospital Norte
▌ NOR · 19:00–07:00   Finaliza en 05:46
```

La abreviatura y `HH:mm–HH:mm` son indivisibles conceptualmente. Si una superficie extrema impone truncado, el fallback y la expandida deben conservar el contenido completo; probar el S25 Ultra y API 26.

### 6.4 Expandida

Referencia:

```text
▌ PRÓXIMA GUARDIA
  Hospital Norte (NOR)
  Hoy 19:00 → mañana 07:00
  Puesto: Acceso principal

  COMIENZA EN 03:12:18
  Lluvia probable · 12–18 °C

  Eliminar notificación
  Ver detalles · Cómo llegar · Informar novedad
```

- La composición permanece estable al pasar a `EN CURSO`.
- Objetivo, abreviatura y horario proceden de la instantánea histórica.
- Puesto y clima son opcionales y se ocultan sin dejar huecos.
- `Eliminar notificación` sigue dentro de la `RemoteViews`, no reemplaza una acción estándar.
- Las tres acciones existentes se preservan y mantienen autenticación requerida cuando la API lo admite.

### 6.5 Privacidad

- `COMPLETA`: estado, objetivo, abreviatura, horario, puesto y clima elegible.
- `REDUCIDA`: estado, horario y cuenta regresiva; sin objetivo, abreviatura identificadora, puesto, dirección ni clima.
- `OCULTA`: `MiGuardia · Tenés un aviso de guardia`; sin cronómetro ni color histórico.
- Nunca mostrar notas, novedades privadas, datos médicos, fotos, nombres de terceros ni dirección.

## 7. Ritmos de aviso

Agregar tres perfiles globales como puntos de partida claros. No reemplazan el editor avanzado.

### Acompañado — recomendado

- anticipaciones: 12 horas y 2 horas;
- sonido y vibración solicitados al canal;
- tarjeta fija mientras siga vigente.

### Esencial

- anticipación: 12 horas;
- sonido y vibración solicitados;
- tarjeta fija mientras siga vigente.

### Discreto

- anticipación: 12 horas;
- publicación silenciosa, sin vibración solicitada;
- tarjeta descartable;
- privacidad reducida.

Reglas:

- El valor inicial histórico sigue siendo Esencial: 12 horas, sonido/vibración y tarjeta fija. No migrar preferencias existentes hacia otro perfil.
- Elegir un perfil aplica sus valores de manera atómica en el DataStore de Notificaciones.
- Si el usuario modifica tiempos, permanencia, privacidad o modo de atención, la UI puede indicar `Personalizado`.
- El editor avanzado conserva cero a cinco anticipaciones positivas y únicas, con accesos 6/8/12/24 h y minutos personalizados.
- Este incremento persiste un único modo global de atención: `SONIDO_Y_VIBRACION`, `SOLO_VIBRACION` o `SILENCIOSO`.
- No se implementa un modo distinto por cada recordatorio ni por guardia: requeriría ampliar el contrato persistente particular. Room v5 queda congelado.

## 8. Canales y control de Android

- Crear una nueva versión determinista del canal administrado por MiGuardia que incluya modo de atención y sonido efectivo en su identidad.
- `SONIDO_Y_VIBRACION`: `IMPORTANCE_DEFAULT`, sonido del sistema/elegido y vibración solicitada.
- `SOLO_VIBRACION`: sin sonido, vibración solicitada y comportamiento compatible con control final de Android.
- `SILENCIOSO`: sin sonido, sin vibración solicitada e importancia no intrusiva que conserve visibilidad.
- Mantener `CATEGORY_REMINDER` y `AudioAttributes.USAGE_NOTIFICATION_EVENT`; nunca usar categoría o uso de alarma.
- Eliminar únicamente canales antiguos propios con prefijo `guard_shifts_`; nunca tocar canales de terceros.
- Explicar en la UI que Android, No molestar y el usuario conservan el control final.

## 9. Centro de Notificaciones dentro de MiGuardia

Reordenar la superficie vigente mediante divulgación progresiva:

1. **Estado de avisos**: activados/desactivados, permiso y acción primaria sólo cuando exista algo que resolver.
2. **Vista previa Vigilia**: tarjeta Compose ficticia, sin leer ni copiar guardias reales; alternancia compacta/ampliada si es proporcionada.
3. **Enviar notificación de prueba**: publica una tarjeta real, ficticia, descartable, no agrupada y con expiración breve. No crea guardia, alarma, frontera, configuración particular ni tracking visible/oculto.
4. **Ritmo de aviso**: Acompañado, Esencial o Discreto.
5. **Cuándo avisar**: resumen y línea temporal legible; el editor avanzado mantiene los cinco tiempos.
6. **Cómo se muestra**: fija/descartable.
7. **Opciones avanzadas**: precisión, privacidad, sonido y modo de atención manual.
8. **Notificaciones ocultas**: restauración individual/total existente.

La vista previa usa exclusivamente datos ficticios, por ejemplo `Hospital Norte`, `NOR`, `19:00–07:00` y `Acceso principal`.

La notificación de prueba:

- requiere acceso efectivo para publicar;
- reutiliza el mismo renderer visual que la tarjeta real;
- usa tag e ID reservados que no colisionan con guardias;
- no incluye las acciones laborales de una guardia inexistente;
- abre MiGuardia mediante un `PendingIntent` explícito e inmutable;
- se puede descartar y expira automáticamente;
- respeta privacidad, sonido y modo de atención elegidos;
- no consulta red ni clima.

## 10. Correctitud y privacidad

Corregir y probar el hueco de ocultamiento observado en la base:

- antes de publicar una frontera `REMINDER` o `START`, el receptor vuelve a consultar `dismissedShiftIds`;
- si el UUID sigue ocultado, no publica ni llama a `markDisplayed`;
- `END` conserva cancelación y limpieza;
- una actualización meteorológica tardía tampoco puede volver a publicar un aviso ocultado;
- restaurar explícitamente conserva el flujo vigente y publica silenciosamente.

No imprimir contenido de guardias, direcciones, notas, URI de sonido o datos privados en logs.

## 11. Arquitectura congelada

No modificar:

- Room v5, sus 13 entidades, esquemas o migraciones;
- modelos o repositorios de dominio;
- planificador de fronteras y elegibilidad;
- motor de próximo evento;
- calendario, horas, vacaciones, remuneración o perfil laboral;
- Gradle, catálogo, wrapper o dependencias;
- `AndroidManifest.xml`, permisos, componentes o package IDs;
- proveedor, red o caché de clima;
- navegación raíz salvo el cableado mínimo de una acción ya existente;
- cuentas, nube, telemetría, ubicación, mapas embebidos o servicios.

No usar WorkManager, servicio en primer plano, Live Update promovida, `ProgressStyle`, polling, alarma repetitiva, full-screen intent ni `USE_EXACT_ALARM`.

## 12. Archivos autorizados

Modificar sólo cuando sea necesario:

- `docs/PROMPT_MAESTRO_MAIN.md`;
- `docs/prompts/NOTIFICACIONES.md` y este prompt;
- `docs/adr/0010-notificaciones-y-alarmas-locales.md`;
- `app/src/main/java/com/blackatsystems/miguardia/notifications/NotificationPreferencesStore.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/notifications/ShiftNotificationPresenter.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/notifications/ShiftAlarmReceiver.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/notifications/NotificationRuntime.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/notifications/NotificationUiState.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/notifications/NotificationViewModel.kt`;
- `app/src/main/java/com/blackatsystems/miguardia/ui/notifications/NotificationScreens.kt`;
- recursos XML de layout, drawable, color o texto exclusivamente de Notificaciones;
- pruebas JVM e instrumentadas directamente relacionadas.

`MainActivity.kt`, `MiGuardiaApplication.kt` o `MiGuardiaApp.kt` sólo pueden tocarse si la notificación de prueba necesita un cableado mínimo imposible de resolver con los contratos existentes; justificarlo en el handoff.

## 13. Pruebas obligatorias

### JVM / DataStore

- valor predeterminado histórico intacto;
- parseo seguro de modo de atención desconocido;
- aplicación atómica de Acompañado, Esencial y Discreto;
- persistencia y reapertura;
- no modificar tracking instalado/visible/oculto al aplicar un perfil.

### Presenter / instrumentación

- compacta y expandida contienen estado, horario y cronómetro;
- franja usa color histórico en completa/reducida y acento neutro en oculta;
- puesto y clima aparecen u ocultan sin filtrar contenido;
- privacidad completa, reducida y oculta;
- fallback estándar conserva título/texto;
- canal correcto para sonido+vibración, solo vibración y silencioso;
- canal versionado y limpieza limitada al prefijo propio;
- notificación de prueba no usa UUID de guardia, tracking, grupo ni acciones laborales;
- transición START silenciosa y END cancelada;
- varias guardias permanecen separadas y agrupadas.

### Ocultamiento

- una frontera posterior no revive un aviso ocultado;
- una actualización meteorológica tardía no lo revive;
- END limpia el registro;
- restauración explícita sigue siendo silenciosa y estable.

### Compose

- estado desactivado, permiso pendiente y listo;
- tarjeta de vista previa con estado, objetivo ficticio, horario y cuenta regresiva demostrativa;
- acción `Enviar notificación de prueba` habilitada sólo cuando corresponde;
- tres perfiles con descripción y selección derivada/personalizada;
- editor de cero a cinco avisos;
- fija/descartable, privacidad, sonido y atención manual;
- notificaciones ocultas y restauración;
- tema claro/oscuro, retrato/paisaje y zoom interno 100/150/200 % sin consultar ajustes visuales de Android;
- semántica básica, sin activar ni declarar auditoría de TalkBack.

## 14. Verificación por impacto

Ejecutar serializado:

```powershell
.\gradlew.bat --no-daemon --stacktrace --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :app:assembleQa :app:assembleQaAndroidTest
```

Ampliar la batería sólo si el diff alcanza contratos compartidos o aparece un fallo no acotable. Room no cambia, por lo que sus pruebas instrumentadas históricas se conservan salvo evidencia contraria.

En el Samsung `SM-S938B` API 36, exclusivamente con `.qa` y datos ficticios:

- permiso concedido/denegado;
- compacta y expandida reales;
- cuenta regresiva próxima y en curso;
- Acompañado, Esencial y Discreto;
- sonido/vibración/silencio observables, distinguiendo lo automatizado de lo oído/sentido realmente;
- notificación de prueba y expiración;
- privacidad de bloqueo sin capturar datos reales;
- ocultar, ejecutar una frontera posterior y confirmar que no reaparece;
- restaurar;
- dos guardias simultáneas;
- tema claro/oscuro de la superficie Compose y zoom interno.

No reiniciar el teléfono, cambiar hora/zona, activar TalkBack ni modificar fuente, zoom, tamaño de pantalla o densidad del sistema sin autorización explícita nueva. Al cerrar, retirar sólo QA y confirmar producción instalada.

## 15. Definición de terminado

La entrega está terminada sólo cuando:

- Pulso Vigilia es reconocible sin intentar controlar la carcasa de Android;
- el trazo, estado, abreviatura, horario y cuenta regresiva sobreviven en la compacta;
- la expandida contiene el detalle aprobado y las tres acciones;
- los perfiles son comprensibles, reversibles y persistentes;
- la prueba real no altera guardias ni planificación;
- ocultar significa permanecer oculto hasta restauración o caducidad;
- Room, dominio, Gradle, manifiesto, permisos y dependencias permanecen idénticos;
- las pruebas pertinentes, lint y ensamblados pasan;
- la QA física realmente ejecutada queda informada con límites honestos;
- `git diff --check` queda limpio;
- no hay secretos, datos reales, logs privados ni artefactos;
- no se creó commit, push ni merge.

## 16. Handoff a MAIN

Entregar:

```text
OBJECTIVE
Aplicar Pulso Vigilia a Notificaciones.

BASE
Worktree, rama y SHA obligatorio.

CHANGES
Conducta, visual, configuración y defecto corregido.

FILES
Lista exacta.

DECISIONS
Perfiles, canales, privacidad y límites.

VALIDATION
Comandos, conteos, lint, builds y dispositivo.

RISKS
Límites Android/OEM y recorridos no ejecutados.

PENDING
Qué queda para MAIN o una etapa posterior.

INVARIANTS
Room, dominio, Gradle, manifiesto, permisos, privacidad y producción preservados.

NEXT
Auditoría e integración independiente de MAIN.
```
