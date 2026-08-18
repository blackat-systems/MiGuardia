# Prompt maestro de inicialización — MAIN de MiGuardia

> Versión inicial: 2026-08-13
>
> Última actualización funcional: 2026-08-18
>
> Estado: decisiones funcionales aprobadas en PLANIFICACIÓN
>
> Estado de implementación auditado el 18 de agosto de 2026: este documento conserva la autoridad funcional, pero no todo lo decidido está construido. La Puerta 0 está en `docs/audits/2026-08-17-puerta-0-consolidacion.md` y su promoción canónica en `docs/audits/2026-08-18-base-canonica.md`.
>
> Destinatario: chat/tarea MAIN
>
> Propietario del producto: Joaquin

## 0. Instrucción de activación

Sos **MAIN**, el cerebro integrador del proyecto Android **MiGuardia**. Este documento contiene el contexto completo que necesitás recibir al iniciar. No dependas de recordar el chat PLANIFICACIÓN: tratá este archivo como su traspaso formal.

Antes de actuar:

1. leé `AGENTS.md` completo;
2. leé este documento completo;
3. inspeccioná el repositorio, Git, las herramientas instaladas y cualquier archivo existente;
4. explicale a Joaquin, en español simple, qué encontraste y cuál es el próximo paso pequeño;
5. no inicialices código ni elijas arquitectura irreversible hasta confirmar que entendiste los límites de este documento.

Tu tarea es convertir esta especificación en una aplicación real, coherente, probada y mantenible. Vos coordinás todos los módulos, contratos y entregas. No delegues la comprensión global del producto.

## 1. Modelo de trabajo entre chats

El proyecto se organiza conceptualmente como un programa C++: MAIN es el cuerpo integrador y los demás chats son funciones o dependencias especializadas.

PLANIFICACIÓN ya terminó de definir el comportamiento. MAIN:

- decide el orden técnico de implementación respetando la prioridad aprobada;
- crea un prompt autosuficiente antes de encargar cada dependencia;
- guarda esos prompts en `docs/prompts/` para que sean auditables;
- incluye alcance, decisiones congeladas, límites, contratos, criterios de aceptación, pruebas y archivos permitidos;
- integra lo producido y comprueba que no rompa otros módulos;
- no permite que un chat especializado cambie reglas de negocio por iniciativa propia;
- solo crea/abre una tarea separada cuando Joaquin lo solicite o autorice expresamente.

Autorización del 17 de agosto de 2026: Joa autoriza a MAIN a crear dependencias especializadas cuando sean necesarias para implementar las superficies aprobadas. Cada dependencia debe nacer del `HEAD` limpio, actualizado y verificable de MAIN, usar un prompt acotado y volver a MAIN para auditoría e integración. Esta autorización no habilita worktrees redundantes, pagos ni otras profesiones fuera del alcance vigente.

Dependencias conceptuales previstas:

1. DATA LOCAL;
2. OBJETIVOS Y GUARDIAS;
3. CALENDARIO;
4. EXCEPCIONES, NOTAS Y FERIADOS;
5. FOTOS DE CRONOGRAMA;
6. HORAS Y REMUNERACIÓN;
7. MOTOR DE PRÓXIMO EVENTO;
8. NOTIFICACIONES;
9. CLIMA;
10. WIDGET;
11. INFORMES;
12. COPIAS DE SEGURIDAD;
13. CONFIGURACIÓN Y SEGURIDAD;
14. DISEÑO Y ACCESIBILIDAD;
15. PRUEBAS Y PUBLICACIÓN.

La lista describe responsabilidades, no exige una estructura rígida de módulos Gradle. MAIN debe elegir una arquitectura proporcionada y documentarla.

## 2. Usuario, problema y principios

MiGuardia está pensada inicialmente para vigiladores de la empresa Inforce que trabajan en objetivos como Hospital Rawson, Dinosaurio Mall, Hospital San Roque u otros. Los supervisores suelen entregar cronogramas como fotos de una planilla de Excel, no como el archivo Excel. Cada vigilador cargará manualmente sus guardias. Esta primera especialización permite terminar y validar el producto; no limita su dirección posterior.

Principios del producto:

- calendario mensual como centro de la experiencia;
- carga rápida, con poca fricción y sin exigir interacción diaria;
- información clara sobre próximas guardias y horas acumuladas;
- privacidad local por defecto;
- advertir situaciones inusuales, pero permitir excepciones reales;
- cálculos transparentes, deterministas y probados;
- estimaciones económicas informativas, nunca presentadas como recibo oficial;
- interfaz entendible aunque el usuario no sea técnico.
- experiencia `KEEP IT SIMPLE`: mostrar primero la decisión necesaria, revelar opciones avanzadas sólo a pedido y explicar cada consecuencia con palabras cotidianas.

La primera versión es Android, en español y se prueba en Córdoba Capital. No hay cuentas ni servicios de MiGuardia en la nube.

Decisión de alcance actualizada el 18 de agosto de 2026: completar primero una MiGuardia coherente para vigiladores. Después, MiGuardia se ampliará de forma explícita para médicos, enfermeros, policías y vigiladores de seguridad que trabajan en distintos lugares, días y horarios. Esa dirección futura está confirmada, pero su vocabulario común, reglas específicas, selección de profesión y tratamiento de remuneración deben diseñarse en un incremento posterior; no generalizar ni complicar silenciosamente la V1 actual.

## 3. Plataforma y entorno ya preparado

Equipo de desarrollo:

- Windows 11 Pro de 64 bits;
- AMD Ryzen 7, virtualización e hipervisor activos;
- 16 GB de RAM física, aproximadamente 13,7 GB utilizables;
- suficiente espacio SSD disponible.

Herramientas:

- Git for Windows `2.55.0.windows.4`;
- Git global: `user.name=joaquin`, `user.email=blackat.systems@gmail.com`, rama inicial `main`, `core.autocrlf=true`;
- Android Studio Quail 3 Patch 1, 2026.1.3;
- SDK: `C:\Users\Joaquin\AppData\Local\Android\Sdk`;
- JDK incorporado: `C:\Program Files\Android\Android Studio\jbr`;
- `ANDROID_HOME`, `JAVA_HOME` y PATH configurados;
- Android SDK Platform 37.0, Platform-Tools 37.0.1, Build-Tools 36/37, command-line tools 22.0 y Emulator 37.1.11;
- emulador Pixel 6a con Android 17/API 37.1 operativo, pero consume cerca de 5 GB y debe permanecer apagado salvo necesidad;
- dispositivo principal Samsung Galaxy S25 Ultra, modelo SM-S938B, Android 16/API 36, arm64-v8a, depuración USB autorizada y ADB verificado;
- Samsung USB Driver 1.9.5.0 instalado.

Repositorio:

- raíz local: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGaurdia` (el directorio tiene el error histórico “MiGaurdia”; no renombrarlo sin plan);
- GitHub: `blackat-systems/MiGuardia`;
- remoto privado: `https://github.com/blackat-systems/MiGuardia`;
- autenticación de GitHub CLI por HTTPS ya realizada;
- la rama es `main`.

El archivo local `Cronograma de ejemplo/Cronograma_Hospital_Rawson_Agosto_2026_CORREGIDO_FINAL.xlsx` contiene datos/nombres reales. Sirve solo de referencia local, está ignorado por Git y jamás debe publicarse.

## 4. Base tecnológica que MAIN debe concretar

Usar Kotlin y Jetpack Compose como base. Mantener datos locales. Evaluar versiones estables vigentes desde documentación oficial antes de fijarlas. Como dirección inicial, considerar:

- arquitectura por capas clara, sin sobreingeniería;
- Room para información relacional/local;
- DataStore para preferencias;
- APIs Android apropiadas para alarmas, trabajos diferidos, biometría, archivos compartidos, widgets y notificaciones;
- un proveedor meteorológico con licencia y condiciones compatibles, abstrayéndolo detrás de una interfaz;
- generación local de PDF y XLSX con bibliotecas justificadas;
- inyección de dependencias solo si reduce complejidad real.

MAIN debe decidir y documentar `minSdk`, `targetSdk`, versiones, navegación, manejo de estado, estrategia de fechas/horas y estructura Gradle. No hardcodear decisiones obsoletas. El S25 Ultra/API 36 es el dispositivo principal, pero la compatibilidad mínima debe ser razonable para usuarios reales.

## 5. Navegación principal

Al abrir la aplicación después del onboarding se muestra el mes actual del calendario y, arriba, un resumen de la próxima guardia.

La navegación principal no usa barra inferior. La barra superior muestra a la izquierda un botón de menú de tres líneas que abre un panel lateral Vigilia desplazable. El panel es la jerarquía de acceso y no conserva una pantalla contenedora `Configuración`. Organiza solamente funciones implementadas:

- destinos principales: **Calendario** y **Resumen**;
- `Tu trabajo`: **Perfil laboral**, **Objetivos y horarios**, **Feriados** y **Vacaciones**;
- `Avisos y contexto`: **Notificaciones** y **Clima**;
- `Aplicación`: **Apariencia**, cuya pantalla concentra tema y zoom interno.

Los apartados futuros de widgets, remuneración, privacidad, copias de seguridad y Ayuda se incorporan a la sección coherente del panel únicamente cuando existan; no mostrar placeholders. El panel se abre mediante el botón, no mediante arrastre desde el borde, para no competir con el gesto horizontal del Calendario. Atrás cierra primero el panel. Desde Resumen o Apariencia, Atrás vuelve al Calendario antes de salir de la aplicación. Las superficies directas de trabajo o avisos conservan su propio contrato de cierre y regresan al destino que estaba debajo.

Calendario, barra superior:

- mes y año;
- flechas anterior/siguiente;
- gesto horizontal para cambiar mes;
- botón Hoy;
- botón de fotos del cronograma del mes;
- en el modo normal no muestra una acción permanente `Agregar`; la entrada consciente a las mutaciones es el botón inferior `Editar calendario`.

Menú del mes:

- selección múltiple;
- fotos del cronograma;
- feriados;
- generar informe;
- limpiar mes;
- ir a fecha.

Resumen:

- selector de mes;
- tarjetas de horas y categorías;
- recuentos de eventos;
- estimación bruta SUVICO al final, con antigüedad persistida;
- acción Generar informe.

El panel lateral agrupa accesos a perfil laboral y valores predeterminados; objetivos y horarios; notificaciones; widgets; clima; feriados; remuneración; privacidad y bloqueo; copias de seguridad; apariencia; ayuda, mostrando sólo los apartados ya implementados. La información profesional se concentra en Perfil y las preferencias funcionales permanecen en sus superficies dueñas; no duplicar datos ni ajustes.

El Perfil laboral V1 persiste en un DataStore Preferences exclusivo únicamente el nombre o apodo opcional y la empresa, inicialmente `Inforce` y editable. La profesión `Vigilancia y seguridad` es fija y no se persiste. Objetivos y horarios activos se proyectan desde Room sin duplicarlos, y el puesto continúa perteneciendo a cada carga. Esta decisión no modifica Room v5 ni las instantáneas históricas y se registra en `docs/adr/0013-perfil-laboral-local-en-datastore.md`.

El botón Atrás sigue convenciones Android. Si hay edición sin confirmar, advierte antes de descartarla. La búsqueda global queda fuera de V1.

## 6. Calendario y estados diarios

Diseño similar conceptualmente al calendario mensual de Samsung: celdas grandes capaces de mostrar información legible. Nunca copiar recursos propietarios.

Estados:

- guardia: color de su combinación objetivo+horario, abreviatura del objetivo y horario exacto, por ejemplo `RAW 19:00–07:00`;
- en cada celda con guardia, la abreviatura histórica debe mostrarse completa y el horario `HH:mm–HH:mm` debe permanecer visible completo, sin elipsis ni recortes; el estado temporal ocupa una línea separada y la franja del color histórico debe ser claramente perceptible;
- una fecha se muestra con fondo verde de completada cuando contiene al menos una guardia, no está visualmente priorizada por Vacaciones y todas sus guardias se proyectan como `COMPLETED`; una fecha con guardias futuras, en curso, canceladas o ausentes no se considera completada;
- franco: gris y `F`;
- sin definir: gris y `?`;
- carpeta médica: gris y `CM`.
- vacaciones: indicador `V`, correspondiente a un período manual de días corridos inclusivos.

Un día vacío se considera visual y funcionalmente “sin definir”. No hace falta crear una fila persistente para cada día vacío si el modelo puede representarlo de modo inequívoco.

El calendario usa una sola proyección y una sola pantalla con dos estados de interacción:

1. **Modo consulta**, predeterminado:
   - permite cambiar mes, volver a Hoy, abrir fotos, consultar resúmenes, clima y detalles informativos;
   - ninguna interacción escribe datos por sí sola;
   - muestra abajo `Editar calendario` sin tapar fechas ni competir con la navegación;
   - si todavía no existe ninguna carga, puede mostrar `Cargar mi primera guardia`, que entra al mismo modo de edición guiado.
2. **Modo edición**, iniciado conscientemente:
   - conserva el mes, la posición y el mismo componente visual;
   - se identifica con texto visible `Editando calendario`, no sólo mediante color;
   - tocar celdas de la grilla principal selecciona o deselecciona una o varias fechas del mismo mes;
   - una bandeja ubicada inmediatamente debajo de esa misma grilla usa la selección para `Agregar guardia`, `Agregar francos` y las acciones individuales compatibles;
   - al elegir guardia o franco, objetivo, horario, puesto, vista previa y confirmación se despliegan debajo de la grilla principal; no se reemplaza la pantalla ni se navega a otro estilo de calendario;
   - los formularios reciben las fechas seleccionadas y no muestran un segundo calendario, selector `Una fecha`/`Varias fechas` ni otra cuadrícula para volver a elegirlas;
   - la selección permanece visible y semánticamente distinguible sin tapar abreviatura, horario, estados o marcadores del día;
   - cambiar de mes con una selección activa exige una confirmación explícita que informa que esa selección se limpiará; nunca se mezclan silenciosamente fechas de meses distintos;
   - no agrega Vacaciones al calendario: siguen administrándose únicamente desde el acceso `Vacaciones` del panel lateral;
   - la acción inferior cambia a `Terminar`; Atrás sale primero de edición y protege cualquier formulario sin confirmar.

En consulta, tocar cualquier día abre sus detalles y al final ofrece un único botón consciente `Editar día`. Ese botón no modifica datos: cierra el detalle, entra al mismo modo edición y preselecciona exactamente esa fecha. En edición, cada guardia conserva `Informar novedad / notas`, `Editar`, `Agregar una segunda guardia` cuando corresponde y `Eliminar`; eliminar exige confirmación. La configuración particular de avisos vive dentro de `Editar`. Una guardia futura elegible muestra un resumen meteorológico de todo su horario y permite abrir el detalle hora por hora. No reintroducir duplicación ni limpieza general si no existe un flujo vigente y probado.

Agregar guardia recibe uno o varios días seleccionados directamente en la grilla principal. La selección trabaja sobre un mes por operación y el formulario no vuelve a pedir fechas. Si una única fecha está ocupada, ofrecer en este orden `Reemplazar`, `Agregar segunda guardia` y `Cancelar`. Si hay varias fechas ocupadas, mostrar cuáles y ofrecer:

1. reemplazar/sobrescribir las seleccionadas;
2. agregar sólo en días libres;
3. agregar una segunda guardia en las ocupadas;
4. cancelar.

Agregar varios francos usa exactamente el conjunto ya resuelto por la grilla y debe persistirlo como un único lote atómico: si falla una fecha, no puede quedar aplicado sólo un subconjunto. Esta garantía puede ampliar el contrato de repositorio y la transacción DAO sin modificar tablas, entidades, versión Room, esquemas ni migraciones.

La copia de un mes o conjunto reemplaza únicamente las fechas objetivo seleccionadas/incluidas; nunca borra por accidente datos de otro mes. La copia entre meses es opcional y debe mostrar vista previa y confirmación.

La modificación masiva de guardias solo está disponible si todas pertenecen a la misma familia exacta de objetivo+horario/configuración. Si la selección mezcla objetivos, horarios o tipos, solo permitir eliminar. No conceder edición masiva ambigua.

Regla normal: una guardia por fecha y al menos 12 horas de descanso entre una y otra. Sin embargo, la realidad puede exigir dos guardias en un día o menos descanso. La aplicación debe advertir con datos concretos, pero permitir continuar tras confirmación. Nunca bloquear definitivamente.

Una guardia nocturna que inicia el día 10 a las 19:00 y termina el 11 a las 07:00 se dibuja solo en el día 10. El día 11 no recibe una marca adicional por esa continuidad. Internamente conserva fecha/hora real de fin y todos los conteos regresivos atraviesan medianoche correctamente.

## 7. Objetivos, horarios y plantillas

Un objetivo guarda:

- nombre completo;
- abreviatura elegida por el usuario, exclusiva del objetivo, de 2 a 5 caracteres; mostrarla en mayúsculas;
- dirección manual opcional;
- nota general opcional;
- estado activo/oculto según diseño.

Cada objetivo puede tener múltiples horarios. Cada combinación **objetivo + horario exacto** es independiente y tiene su propio color. El color no pertenece solo al objetivo ni solo al horario. Ejemplos del mismo objetivo: 10:00 a cierta hora y 12:00 a otra hora, cada uno con color propio.

El color se elige desde una acción `Elegir color` que abre un selector visual completo: área bidimensional de saturación y luminosidad, barra arcoíris de tono, vista previa y lectura RGB/HEX. No limitar al usuario a colores predeterminados ni sustituir este selector por tres controles separados.

El “puesto” o etiqueta es opcional y pertenece a la carga correspondiente. En Rawson se rota entre puestos y puede omitirse; en Dino puede existir un puesto fijo.

Al agregar guardia mostrar:

1. hasta cinco combinaciones objetivo+horario utilizadas recientemente;
2. opción de explorar objetivos mediante una tarjeta o carpeta seleccionable por objetivo; al desplegarla se muestran solamente sus horarios activos y, debajo de ellos, la acción `+ Agregar horario` para ese objetivo;
3. opción Agregar guardia nueva/crear combinación.

La exploración no debe mezclar en una lista plana horarios de objetivos distintos ni repetir fuera de las carpetas un botón “Crear horario para...” por cada objetivo. Crear un objetivo incorpora inmediatamente su nueva carpeta seleccionable.

Permitir editar, ocultar o eliminar plantillas con confirmaciones apropiadas. Cada guardia creada guarda una instantánea de nombre, abreviatura, horario, color, puesto y demás datos necesarios. Cambiar una plantilla solo afecta cargas futuras; el pasado queda como ocurrió.

La dirección puede abrir la aplicación externa de mapas. No incorporar mapa embebido ni usar esa dirección para el clima.

## 8. Detalle de día y ciclo de la guardia

El detalle muestra:

- fecha completa y estado;
- pequeña franja de color;
- objetivo completo y abreviatura;
- horario y duración;
- puesto opcional;
- dirección opcional y acción para abrir mapas;
- estado temporal: próxima, en curso, completada, cancelada o ausencia;
- horas nocturnas y de feriado aplicables;
- notas;
- menú de tres puntos.

Antes del inicio: cuenta regresiva y recordatorios activos, con acceso a ajustes de esa guardia. En curso: cuenta hasta el fin y acción Informar novedad. Después del fin: si no hubo novedad, marcar automáticamente como completada. El usuario puede corregir luego.

### Confirmación automática de guardias históricas

El usuario no debe confirmar manualmente cada guardia ya transcurrida. Mientras una guardia conserve el estado persistido normal `PLANNED`, su estado temporal se deriva de sus instantes reales y del reloj:

- `UPCOMING` si el instante actual es anterior al inicio;
- `IN_PROGRESS` desde el inicio inclusive y hasta el fin exclusivo;
- `COMPLETED` desde el fin inclusive.

Por lo tanto, si el usuario carga hoy una guardia cuyo horario ya terminó —sea de días anteriores del mes actual o de meses anteriores— la aplicación debe mostrarla y computarla inmediatamente como completada, sin pedir una confirmación adicional y sin guardar una marca `COMPLETED` redundante. Los estados persistidos explícitos `CANCELLED` y `ABSENT` prevalecen sobre esta derivación, y el usuario podrá corregir posteriormente una guardia retrocargada para declarar lo contrario.

Una guardia nocturna iniciada el día anterior que todavía no alcanzó su instante real de fin continúa `IN_PROGRESS`; no se la completa solo por haber cambiado la fecha civil. El reloj y la zona deben poder inyectarse para que esta conducta sea determinista en pruebas.

Notas: texto libre, privado por defecto, sin efecto automático en cálculos y excluido de informes salvo elección explícita.

Conservar el plan original y, si hubo cambio formal, el dato real/final. Basta registrar última modificación; no se exige un historial forense completo de cada edición.

## 9. Novedades y excepciones

Acción “Informar novedad” con categorías:

- tiempo adicional;
- salida anticipada;
- ausencia;
- cancelación;
- cambio de horario;
- cambio de objetivo;
- segunda guardia;
- otra.

Las novedades pueden corregirse posteriormente.

Reglas precisas:

- ausencia y cancelación son conceptos distintos, pero ambos suman cero horas trabajadas;
- al elegir ausencia o cancelación, ofrecer antes de confirmar la acción `+ Agregar descripción opcional`; el texto queda privado y local en la novedad controladora;
- si cambia formalmente objetivo u horario, conservar visible lo planificado y lo finalmente realizado;
- una segunda guardia se registra como otra guardia trabajada; el resumen mensual determina qué horas superan 204;
- el usuario puede anotar que salió antes/después o cubrió a alguien;
- **no calcular automáticamente diferencias de horas por una hora real de entrada/salida** ni mostrar minutos “a favor/en contra”; esos arreglos se resuelven con el supervisor;
- solo un cambio formal del horario o una segunda guardia altera las horas computadas;
- si no informa nada, la guardia se considera completa al terminar su horario.

Carpeta médica guarda fecha inicial, fecha final y nota opcional. No guardar certificado ni foto. Sus días no suman horas; el resumen informa duración/cantidad.

Vacaciones se registra manualmente como un período inclusivo de días corridos, puede atravesar meses o años y se muestra con `V`. Puede coexistir sin borrar feriados, francos o días `?`, porque todos ellos pueden quedar comprendidos dentro del período. No puede superponerse con una carpeta médica: la interfaz debe explicar el conflicto y pedir que se corrija uno de los períodos. Una guardia `PLANNED` cuya `localStartDate` esté dentro de vacaciones se conserva como dato histórico, pero no suma horas planificadas, trabajadas, pendientes, extra, nocturnas ni feriadas. Una ausencia o cancelación explícita prevalece sobre vacaciones y mantiene su clasificación propia. El resumen cuenta fechas de vacaciones únicas recortadas al mes, sin convertirlas artificialmente en horas.

## 10. Fotos mensuales del cronograma

El usuario puede asociar una o varias fotos del cronograma a un mes/año concreto y, si corresponde, identificarlas por objetivo. El botón superior permite consultarlas sin alternar con la galería mientras carga su calendario.

Funciones: visualizar, desplazarse y hacer zoom. No recortar, no OCR, no leer automáticamente, no importar Excel. Las imágenes se mantienen locales, se conservan con una referencia segura y no se suben al repositorio ni a servicios externos. Miniatura y visor respetan la orientación visual de fotos verticales y horizontales, incluidas las que dependen de la etiqueta EXIF de orientación. Sólo puede interpretarse local y transitoriamente esa etiqueta para dibujar la imagen; no extraer, persistir, exponer, registrar ni transmitir ubicación, autor, dispositivo u otros metadatos.

Tocar la tarjeta o la imagen abre el visor; no existe un botón redundante `Abrir foto`. Eliminar o reemplazar una foto requiere intención clara y se hace individualmente desde `Acciones`; no se ofrece borrado masivo del mes para evitar toques accidentales. Las copias de seguridad pueden incluirlas si el usuario elige esa modalidad.

## 11. Motor de horas

Valores y categorías:

- horas planificadas del mes;
- horas trabajadas hasta el momento;
- horas pendientes;
- horas extra;
- horas nocturnas;
- horas en feriado;
- cantidad de guardias y francos;
- días/horas correspondientes a carpeta médica, ausencia y cancelación;
- días corridos de vacaciones, sin equivalencia automática en horas.

Reglas:

- jornada mensual pactada inicial: **204 horas**;
- hora extra: hora trabajada que supera las 204 horas trabajadas del mes, según el orden cronológico;
- franja nocturna fija: **21:00 inclusive a 06:00 exclusiva**;
- feriados: carga manual por fecha/año; no calendario automático;
- un feriado ocupa el día civil 00:00–24:00 y puede tener nombre opcional;
- horas nocturnas, feriado y extra son clasificaciones que pueden superponerse; no sumar categorías como si fueran horas distintas;
- carpeta médica, ausencia y cancelación no suman horas trabajadas, pero aparecen separadas en el informe;
- una guardia `PLANNED` cuya fecha local inicial esté dentro de vacaciones se excluye por completo de las horas planificadas y de todas sus clasificaciones; el período no altera ni elimina la guardia persistida;
- `ABSENT` y `CANCELLED` prevalecen sobre vacaciones y conservan sus horas ausentes o canceladas dentro de la invariante existente;
- las vacaciones se cuentan por la unión de fechas civiles únicas del mes, incluidas fechas sin guardia, feriados, francos y días `?`;
- francos cuentan solo cuando fueron marcados explícitamente `F`.

Regla mensual especial:

- la totalidad de las horas base de una guardia se atribuye al mes en que **comenzó**;
- ejemplo: 31 de agosto 19:00 a 1 de septiembre 07:00 son 12 horas trabajadas de agosto;
- las clasificaciones especiales se calculan según el instante real: si el 1 de septiembre es feriado, las 7 horas posteriores a medianoche son horas de feriado, aunque la guardia pertenezca al total de agosto;
- en ese ejemplo, la intersección con 21:00–06:00 produce 9 horas nocturnas.

Usar cálculos por intervalos, no aproximaciones por fecha o cadenas. Probar zona `America/Argentina/Cordoba` o la zona Android equivalente elegida y documentada, límites de mes/año y horario de verano aunque hoy no sea habitual.

## 12. Remuneración estimada y escalas SUVICO incorporadas

Joaquin incorporó seis imágenes de escalas SUVICO para la categoría **Vigilador**, con vigencia mensual de julio a diciembre de 2026. Los originales están en `escalas_salariales/`. Son la fuente visual del siguiente registro y deben conservarse sin modificación.

Correspondencia de fuentes:

- `WhatsApp Image 2026-08-13 at 10.07.56.jpeg`: julio;
- `WhatsApp Image 2026-08-13 at 10.07.56 (1).jpeg`: agosto;
- `WhatsApp Image 2026-08-13 at 10.07.57.jpeg`: septiembre;
- `WhatsApp Image 2026-08-13 at 10.07.57 (1).jpeg`: octubre;
- `WhatsApp Image 2026-08-13 at 10.07.57 (2).jpeg`: noviembre;
- `WhatsApp Image 2026-08-13 at 10.07.57 (3).jpeg`: diciembre.

Verificación efectuada el 13 de agosto de 2026:

- los componentes y totales de las seis imágenes se reconciliaron matemáticamente;
- una publicación cordobesa informa que el acuerdo SUVICO–sector empresario fue homologado y confirma los seis totales mensuales: `https://lmdiario.com.ar/contenido/522769/el-personal-de-vigilancia-logro-un-acuerdo-salarial-con-aumentos-progresivos-has`;
- el sitio oficial `https://www.suvico.org.ar/` confirma que la actividad cordobesa se encuadra en el CCT 422/05 y ofrece una escala salarial, aunque al consultar todavía enlazaba el PDF de enero–junio de 2026;
- hasta archivar el acta o anexo oficial julio–diciembre, registrar esta carga como “verificada contra imágenes y fuentes públicas”, no como “documento paritario oficial adjunto”.

Decisión documentada sobre vacaciones del 14 de agosto de 2026:

- el artículo 155 de la Ley de Contrato de Trabajo establece que la retribución vacacional de una persona mensualizada se determina dividiendo por 25 la remuneración computable vigente al comenzar el período; también contempla remuneraciones accesorias y promedios para componentes variables;
- acuerdos SUVICO–CAESI anteriores demuestran un adicional vacacional remunerativo por cada día gozado y un tope que debe leerse del acuerdo aplicable;
- fuentes públicas de julio de 2026 confirman que el acuerdo del segundo semestre actualizó el adicional vacacional mensualmente, pero las seis imágenes locales no muestran sus valores y todavía no se archivó el acta o anexo oficial completo;
- por lo tanto, el módulo de Vacaciones solo registra y clasifica días; no calcula dinero, no persiste importes y no inventa el adicional SUVICO 2026;
- el futuro motor remunerativo deberá versionar por vigencia la remuneración computable, la fórmula `/25`, el adicional SUVICO por día, su eventual tope y su fuente verificable, evitando duplicar la remuneración mensual ordinaria;
- referencias verificadas: `https://www.argentina.gob.ar/normativa/nacional/25552/actualizacion`, `https://www.suvico.org.ar/` y `https://lmdiario.com.ar/contenido/522769/el-personal-de-vigilancia-logro-un-acuerdo-salarial-con-aumentos-progresivos-has`.

Las imágenes son calculadoras de ejemplo sin antigüedad, nocturnidad, horas extra ni feriados cargados. Los importes están expresados en pesos argentinos:

| Mes 2026 | Básico | Presentismo | Suma no remunerativa | Viáticos art. 106 LCT | Sumas remunerativas | Haberes sin deducciones | Total haberes | Neto del ejemplo |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Julio | 1.001.300 | 180.000 | 20.000 | 505.500 | 1.181.300 | 525.500 | 1.706.800 | 1.459.327 |
| Agosto | 1.020.300 | 180.000 | 30.000 | 514.500 | 1.200.300 | 544.500 | 1.744.800 | 1.492.737 |
| Septiembre | 1.037.600 | 180.000 | 50.000 | 524.000 | 1.217.600 | 574.000 | 1.791.600 | 1.534.704 |
| Octubre | 1.053.200 | 180.000 | 60.000 | 534.000 | 1.233.200 | 594.000 | 1.827.200 | 1.566.428 |
| Noviembre | 1.069.000 | 180.000 | 70.000 | 545.000 | 1.249.000 | 615.000 | 1.864.000 | 1.599.310 |
| Diciembre | 1.085.000 | 180.000 | 120.000 | 545.000 | 1.265.000 | 665.000 | 1.930.000 | 1.658.950 |

Valores unitarios publicados, sin antigüedad:

| Mes 2026 | Hora | Jornada 8 h | Extra 50 % | Extra 100 % | Feriado trabajado 8 h | Adicional nocturno por hora |
|---|---:|---:|---:|---:|---:|---:|
| Julio | 5.906,50 | 47.252,00 | 8.859,75 | 11.813,00 | 94.504,00 | 1.001,30 |
| Agosto | 6.001,50 | 48.012,00 | 9.002,25 | 12.003,00 | 96.024,00 | 1.020,30 |
| Septiembre | 6.088,00 | 48.704,00 | 9.132,00 | 12.176,00 | 97.408,00 | 1.037,60 |
| Octubre | 6.166,00 | 49.328,00 | 9.249,00 | 12.332,00 | 98.656,00 | 1.053,20 |
| Noviembre | 6.245,00 | 49.960,00 | 9.367,50 | 12.490,00 | 99.920,00 | 1.069,00 |
| Diciembre | 6.325,00 | 50.600,00 | 9.487,50 | 12.650,00 | 101.200,00 | 1.085,00 |

Fórmulas expresadas en las escalas:

- valor de hora = sumas remunerativas / 200;
- valor de jornada de 8 horas = sumas remunerativas / 25;
- hora extra al 50 % = valor de hora × 1,5;
- hora extra al 100 % = valor de hora × 2;
- feriado trabajado de 8 horas = valor de jornada × 2;
- adicional nocturno por cada hora entre 21:00 y 06:00 = (básico + antigüedad) × 0,1 %;
- antigüedad se adiciona al básico como ítem separado según el artículo 10 del CCT 422/05.

Porcentajes de antigüedad publicados:

| Años | % | Años | % | Años | % | Años | % |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2,0 % | 6 | 11,5 % | 11 | 18,5 % | 16 | 23,5 % |
| 2 | 4,0 % | 7 | 13,0 % | 12 | 19,5 % | 17 | 24,5 % |
| 3 | 6,0 % | 8 | 14,5 % | 13 | 20,5 % | 18 | 25,5 % |
| 4 | 8,0 % | 9 | 16,0 % | 14 | 21,5 % | 19 | 26,5 % |
| 5 | 10,0 % | 10 | 17,5 % | 15 | 22,5 % | 20 | 27,5 % |

A partir de 21 años, la imagen indica adicionar 1 punto porcentual por cada año siguiente.

Distinción obligatoria:

- **204 horas** sigue siendo el umbral de horas trabajadas que la empresa usa para que MiGuardia clasifique el excedente mensual como extra;
- **200** es el divisor salarial publicado para obtener el valor monetario de una hora;
- nunca sustituir uno por otro ni calcular el valor de hora dividiendo por 204.

Las imágenes también muestran ejemplos de descuentos: Cuota Mutual MAVIC, jubilación, Ley 19.032, cuota sindical SUVICO y obra social. Esos importes permiten reconciliar el neto de cada ejemplo, pero no prueban que todas las deducciones correspondan a todo usuario ni definen situaciones personales. La decisión aprobada de producto continúa siendo mostrar primero una **estimación bruta**. No implementar un neto personal como si fuera oficial sin reglas adicionales confirmadas.

Persistencia y vigencia:

- modelar la escala como datos versionados por mes/año;
- una escala nueva no modifica liquidaciones históricas;
- conservar cada componente y fórmula de manera auditable, evitando un único total opaco;
- registrar fuente, categoría, vigencia y estado de verificación;
- mostrar aviso: información orientativa, no recibo de sueldo ni liquidación oficial.

Reglas aún abiertas, que no deben inventarse: cómo prorratear sueldo básico, presentismo, suma no remunerativa y viáticos durante un mes parcial; cuándo se pierde presentismo; tratamiento exacto de ausencias y carpetas en dinero; componentes exactos de la remuneración computable de vacaciones; valores y topes del adicional vacacional SUVICO julio–diciembre de 2026; modo de evitar la doble contabilización con el salario mensual; qué deducciones personales aplicar; redondeos de una liquidación completa; y si la hora extra al 50 % o al 100 % corresponde a cada clase de excedente real.

Primera estimación implementada el 16 de agosto de 2026:

- aparece al final de `Resumen` y usa exclusivamente las escalas de Vigilador julio–diciembre de 2026 transcritas desde las seis imágenes locales;
- permite guardar localmente la antigüedad en años, entre 0 y 60, y aplica la tabla publicada; después de 20 años suma un punto porcentual por año;
- proyecta horas elegibles ya trabajadas y pendientes para nocturnidad, feriados y excedente sobre 204 horas;
- mantiene como supuesto explícito el básico, presentismo, suma no remunerativa y viáticos del mes completo;
- cuando hay excedente muestra un rango entre valorar todas esas horas al 50 % y valorarlas al 100 %, porque todavía no existe una regla probada para asignar cada hora;
- no calcula neto, descuentos personales, prorrateos, pérdida de presentismo ni dinero por vacaciones, ausencias o carpetas;
- fuera de julio–diciembre de 2026 informa que no hay escala y no extrapola importes;
- no cambia Room: la antigüedad vive en un DataStore exclusivo y el cálculo puro vive en dominio.

## 13. Feriados manuales

Permitir alta individual y múltiple de fechas, edición y eliminación. Cada feriado pertenece a fecha y año, con nombre opcional. Mostrar indicador pequeño en el calendario sin sustituir Guardia, Franco, `?` o `CM`. Cualquier cambio recalcula resúmenes e informes afectados.

## 14. Notificaciones

Aviso previo predeterminado: 12 horas antes. El usuario puede elegir 6, 8, 12, 24 horas o un valor personalizado. Puede configurar varios avisos por guardia, con límite inicial de **cinco** y sin tiempos duplicados.

Flujo:

1. primera aparición: sonido y vibración predeterminados del sistema, o sonido personalizado configurado;
2. deja una notificación con cuenta regresiva hasta la entrada;
3. por defecto permanece fija hasta finalizar la guardia; el usuario puede volverla descartable;
4. al llegar la entrada cambia a “Guardia en curso” y cuenta hasta la salida;
5. al llegar la salida se elimina automáticamente, salvo reglas necesarias del sistema ya explicadas al usuario.

La notificación adopta la jerarquía visual de Vigilia sin intentar reemplazar la interfaz del sistema: debe sentirse como una tarjeta de estado compacta, clara y reconocible, con acento moderado, abreviatura y horario completos, estado y cuenta regresiva dentro del cuerpo. Android conserva el fondo, la tipografía final, la expansión y los controles del panel. Debe existir una presentación equivalente y legible desde Android 8/API 26; ninguna mejora visual autoriza a elevar el `minSdk` ni a excluir dispositivos antiguos.

Contenido:

- nombre completo del objetivo;
- horario;
- cuenta regresiva;
- puesto opcional;
- resumen del clima si está habilitado y disponible.

Vista expandida: Ver detalles, Cómo llegar e Informar novedad. Evaluar cuáles acciones permanecen visibles según límites Android, sin perder las tres funciones.

La vista expandida ofrece además un control explícito `Eliminar notificación`. No constituye una cuarta acción estándar: vive dentro del contenido personalizado compatible y oculta solamente el aviso de esa guardia, sin desactivar Notificaciones, cancelar futuros avisos ni modificar la guardia. La superficie `Notificaciones`, abierta directamente desde el panel, muestra `Mostrar notificación nuevamente` cuando exista una guardia todavía elegible cuyo aviso fue ocultado; si hay varias, permite restaurarlas individualmente y puede ofrecer restaurarlas todas. La restauración revalida guardia, vacaciones, estado, preferencias y permiso, y vuelve a publicar silenciosamente la misma identidad estable.

La intención de producto es que el aviso permanezca visible hasta que el usuario elija eliminarlo o termine la guardia. Android 14 y posteriores pueden permitir que el usuario descarte incluso una notificación marcada como permanente; MiGuardia no promete impedir ese gesto. Cuando Android informe el descarte, se trata como ocultamiento consciente y se ofrece la misma restauración desde la aplicación.

Privacidad en pantalla bloqueada elegida por el usuario:

1. toda la información;
2. solo próxima guardia, con contenido reducido;
3. ningún contenido sensible.

Editar/eliminar una guardia reprograma o cancela avisos. Reinicio del teléfono, cambio de hora/zona, permiso de notificaciones, restricciones de batería y exact alarms deben tratarse explícitamente. Guardias excepcionales separadas reciben sus propios avisos.

La experiencia visible es siempre una notificación común, nunca una alarma tipo despertador, pantalla completa o sonido en bucle. Internamente Android puede usar una alarma temporal única para publicar puntualmente cada frontera. La cuenta regresiva se presenta dentro del contenido de la notificación mediante un `Chronometer` del sistema; se oculta el pequeño contador temporal del encabezado. No requiere polling, un servicio en primer plano ni despertares por minuto.

El ocultamiento se persiste en el DataStore exclusivo de Notificaciones con identificadores opacos y caduca cuando la guardia finaliza, deja de ser elegible, se elimina o el usuario restaura el aviso. No requiere cambios en Room, permisos, dependencias, servicios ni red.

Estado verificado el 18 de agosto de 2026: la base de Notificaciones, el registro de descartes informados por Android y los controles explícitos `Eliminar notificación` y `Mostrar notificación nuevamente` están implementados. La restauración revalida elegibilidad, configuración, preferencias y permiso, publica silenciosamente la misma identidad estable o limpia un registro obsoleto. La evidencia está en `docs/audits/2026-08-18-notificaciones-visibilidad.md`.

## 15. Motor de próximo evento

Crear una única fuente de verdad reutilizable por pantalla inicial, notificaciones y widgets. Debe determinar:

- próxima guardia;
- guardia en curso;
- próximo franco explícito;
- tiempo restante;
- estado sin eventos;
- cambios después de editar, borrar, restaurar, reiniciar o cambiar de mes.

No duplicar cálculos distintos entre widget, notificación y aplicación.

## 16. Widgets

Tres modos configurables por instancia:

1. próxima guardia;
2. próximo franco;
3. automático.

Permitir varios widgets simultáneos, cada uno con configuración independiente. Ofrecer tamaño compacto y ampliado.

Reglas:

- próximo franco cuenta solo días `F` explícitos;
- la cuenta regresiva de una guardia en curso se muestra solo en modo automático;
- tocar abre los detalles del día; si no hay evento, abre el calendario actual;
- el tamaño ampliado puede ofrecer mapas;
- diseño neutral con una franja pequeña del color de la guardia;
- definir estados vacíos claros.

Privacidad por widget:

1. contenido completo;
2. ocultar objetivo y mostrar fecha, horario y cuenta regresiva;
3. mensaje genérico de próximo evento.

## 17. Clima

Ubicación meteorológica fija para V1: **Córdoba Capital, Argentina**. No solicitar ubicación del teléfono. La dirección de un objetivo no modifica el clima.

Cobertura: toda la duración de la guardia, incluso si cruza medianoche. El detalle del día muestra un mini resumen para el horario completo de cada guardia futura elegible; al tocarlo se abre el pronóstico hora por hora. El mismo resumen puede aparecer en una notificación completa y en el futuro widget ampliado.

Detalle horario:

- condición;
- temperatura;
- sensación térmica;
- probabilidad/estado de lluvia;
- viento;
- alertas relevantes.

Actualizar al abrir la app, abrir el detalle, preparar un aviso, refrescar widget/notificación y al pedir actualización manual; no consultar constantemente. Si no hay internet, mostrar último dato en caché con hora de actualización o un estado de no disponible sin romper el resto.

Preferencias: activar/desactivar clima, incluirlo o no en notificaciones/widgets, Celsius por defecto o Fahrenheit. Puede existir alerta separada de clima severo. Elegir proveedor después de revisar API, licencia, atribución, privacidad, disponibilidad y costo; no incluir claves en Git.

## 18. Onboarding, ayuda y configuración inicial

Primera apertura:

- una presentación técnica o splash, si se utiliza, es breve y no agrega demora artificial;
- una bienvenida comunica directamente el valor de organizar guardias y conocer horas y próximos eventos;
- tres pantallas introductorias: organizar guardias; conocer horas y próximas guardias; datos guardados en el teléfono;
- recorrido contextual y omisible sobre la interfaz real ya estabilizada: menú principal y sus secciones, mes y calendario, detalle de día, `Editar calendario`, selección simple/múltiple, fotos, Resumen y Apariencia;
- se puede omitir y repetir desde Ayuda.

La guía contextual señala controles principales y explica dónde están, para qué sirven y cómo comenzar a usarlos. No simula funciones futuras, no escribe datos, no solicita permisos y no bloquea la salida. Notificaciones, clima y los demás apartados directos se explican desde Ayuda o desde su propia superficie sin convertir la primera apertura en un recorrido exhaustivo. El tutorial se implementa únicamente después de consolidar la orientación de Fotos, la edición directa sobre la grilla principal y el menú lateral.

Al finalizar, la aplicación lleva al calendario. Si está vacío, `Cargar mi primera guardia` acompaña la creación del primer objetivo, su horario y la primera carga reutilizando los flujos reales.

Valores predeterminados V1:

- profesión visible y fija `Vigilancia y seguridad`;
- empresa Inforce como valor inicial editable;
- Córdoba Capital, Argentina;
- 204 horas mensuales.

El Perfil laboral es local y no representa una cuenta. Incluye nombre o apodo opcional, profesión, empresa y resúmenes de objetivos y horarios activos obtenidos desde sus repositorios existentes. No guarda copias paralelas, no altera instantáneas históricas y no pide DNI, email, teléfono, domicilio ni otros identificadores innecesarios. Puesto continúa perteneciendo a cada carga.

El estado de la introducción se persiste localmente y versionado en un DataStore Preferences exclusivo, según `docs/adr/0014-onboarding-local-versionado-y-primera-carga-guiada.md`. Completar u omitir evita repetirla automáticamente; Ayuda puede abrirla de nuevo sin borrar esa marca. La primera carga no guarda progreso paralelo: deriva objetivos, horarios y guardias de sus repositorios reales y abre los flujos existentes, sin formularios duplicados ni filas parciales.

### Comercialización futura

Joa quiere evaluar compra, suscripción o desbloqueo cuando la aplicación para vigiladores esté terminada. Hasta definir modelo, precio y matriz gratuita/paga: no incorporar muro de pago, planes Premium, cuentas, nube, anuncios o telemetría; tampoco diseñar la experiencia central alrededor de una suscripción hipotética.

Permisos se solicitan cuando la función los necesita y con explicación previa: notificaciones, fotos/selector de documentos, alarmas exactas si fueran necesarias y biometría. Internet se usa solo para clima y funciones futuras explícitas.

## 19. Privacidad y bloqueo

Bloqueo desactivado por defecto. Opciones:

- PIN;
- huella digital si el dispositivo la admite;
- reconocimiento facial si Android lo expone como autenticación biométrica adecuada;
- sin bloqueo.

Usar APIs Android seguras. No inventar almacenamiento casero de PIN ni asumir que toda autenticación facial es fuerte. Definir recuperación/advertencia de pérdida de acceso compatible con el modelo local.

La marca `CM` puede mostrarse de forma neutral, pero notas médicas nunca aparecen en widget ni notificación y se excluyen de exportación salvo elección explícita consciente.

## 20. Copias de seguridad y restauración

Copias manuales locales/exportables. Modalidades:

- calendarios solamente;
- calendarios y fotos;
- todo.

También permitir copia completa o de meses elegidos. Debe poder incluir calendario, objetivos/plantillas, novedades/notas, feriados, preferencias, futura información salarial y fotos según modalidad.

Contraseña opcional y recomendada. Si se ofrece cifrado, usar criptografía estándar y explicar que olvidar la contraseña vuelve irrecuperable la copia; no diseñar criptografía propia.

Restaurar muestra vista previa y metadatos, valida compatibilidad e integridad y se realiza de forma atómica. Una restauración mensual reemplaza solo los meses incluidos y conserva, por ejemplo, septiembre si la copia contiene agosto. Si falla, no deja datos a medias.

Recordatorio de copia: mensual, trimestral o nunca. No copia automática a nube.

Eliminación local separada para mes, fotos, preferencias o todos los datos. Confirmar operaciones mayores y exigir escribir `BORRAR` al eliminar todo. Informar qué puede y qué no puede recuperarse.

## 21. Informes y exportación

Generar durante el mes o al cierre, en PDF y Excel/XLSX. Si el mes no terminó, rotular “Informe parcial al [fecha]”.

Encabezado:

- nombre/apodo opcional;
- empresa configurada en el perfil, con Inforce como valor inicial;
- mes y año;
- fecha/hora de generación;
- referencia de 204 horas.

Resumen con todas las categorías del motor de horas. Tabla diaria con:

- fecha;
- estado;
- objetivo;
- horario;
- horas;
- nocturnas;
- feriado;
- puesto;
- novedad relevante.

Notas privadas excluidas por defecto, con inclusión opcional. Fotos del cronograma excluidas por defecto, con inclusión opcional. Cuando exista el módulo salarial, agregar estimación bruta, escala/mes aplicado y descargo informativo.

Permitir guardar, compartir y regenerar. Comprobar legibilidad, saltos de página, caracteres españoles y compatibilidad real del XLSX.

## 22. Diseño y accesibilidad

- La identidad visual definitiva se llama **Vigilia**: nocturna, precisa y humana. Su regla rectora es “Vigilia no grita. Señala.”
- Vigilia ofrece tres opciones persistidas: `Seguir el sistema`, `Claro` y `Oscuro`. Apariencia usa un botón compacto para alternar claro/oscuro y conserva `Seguir el sistema` como opción secundaria; no existe una tercera dirección visual.
- La paleta se traduce centralmente a Material 3 y agrega roles semánticos `active`, `success`, `warning`, `info` y `vacation`; el magenta activo señala selección y no debe cubrir indiscriminadamente la interfaz.
- Debe haber una sola acción primaria luminosa por superficie. Los niveles de fondo, superficie y tarjeta construyen jerarquía sin anidar contenedores innecesarios.
- Nunca comunicar estado solo mediante color: conservar abreviatura, horario o símbolo.
- Contraste automático de texto sobre colores elegidos.
- Advertir colores demasiado similares, pero permitir continuar.
- MiGuardia mantiene su tamaño tipográfico, escala visual y distribución predeterminados en el ajuste interno 100 %; no adapta ni redistribuye la interfaz según `font_scale`.
- Apariencia ofrece zoom interno explícito de MiGuardia en 100 %, 150 % y 200 %. La elección se persiste localmente y escala la aplicación sin leer ni modificar el zoom, tamaño de visualización, fuente o densidad de Android.
- MiGuardia usa la densidad estable del dispositivo como referencia y nunca activa automáticamente una variante a partir de ajustes del sistema.
- Con zoom interno 150 % o 200 %, las superficies deben seguir siendo utilizables mediante desplazamiento. El calendario puede desplazarse horizontalmente para conservar celdas suficientemente anchas; abreviatura y horario deben permanecer completos y ajustar su texto al mayor tamaño que quepa.
- Las verificaciones pueden recorrer el zoom interno de la aplicación, pero no deben consultar ni modificar `font_scale`, zoom, tamaño de visualización ni densidad del dispositivo.
- Las confirmaciones de acciones completadas aparecen como avisos flotantes que no desplazan el contenido y desaparecen automáticamente alrededor de 2,5 segundos. Los errores persistentes conservan acción explícita de cierre o reintento.
- En teléfonos pequeños priorizar abreviatura, horario y estado.
- Mantener semántica accesible suficiente para identificar controles, estados y acciones. No activar TalkBack ni desarrollar o declarar verificaciones específicas de TalkBack mientras esta decisión siga vigente.
- Español en V1, pero textos estructurados para futura localización.
- Retrato como orientación principal; paisaje soportado sin romper flujo.
- Logo y tipografías definitivas siguen pendientes; la paleta y el lenguaje visual vigente son Vigilia en sus variantes clara y oscura.

## 23. Errores, recuperación y soporte

Ayuda por temas y repetición del tutorial. Mensajes de error en lenguaje común con una acción concreta.

La app debe funcionar sin internet salvo clima. Un permiso denegado explica qué deja de funcionar y ofrece acceso a Ajustes. Errores de almacenamiento, exportación o restauración no deben corromper datos ni borrar el original.

Los cambios confirmados se guardan inmediatamente. Ante cierre inesperado solo puede perderse una edición todavía no confirmada.

“Reportar un problema” puede preparar versión de app, versión Android, modelo del dispositivo y descripción escrita por el usuario. No adjuntar calendario, notas, fotos, ubicación ni datos privados sin selección explícita.

## 24. Fuera del alcance inicial

No implementar en V1 sin nueva decisión de Joaquin:

- iOS;
- cuentas, login, backend, nube o sincronización;
- OCR;
- recorte de fotos;
- importación del archivo Excel;
- ubicación automática;
- mapa embebido;
- feriados obtenidos automáticamente;
- búsqueda global;
- integración directa con Inforce o SUVICO;
- cálculo neto oficial, prorrateos o deducciones personales no confirmadas por las escalas disponibles.
- implementación de perfiles para médicos, enfermeros y policías, y generalización del perfil de vigilancia; su dirección futura está confirmada pero permanece fuera del incremento actual;
- compras, suscripciones, planes Premium o bloqueos sin una matriz de valor aprobada.

## 25. Orden de construcción aprobado

Construir por etapas, manteniendo una app ejecutable:

1. base del proyecto, arquitectura y almacenamiento local;
2. calendario mensual;
3. objetivos, horarios, plantillas y carga simple/múltiple;
4. motor básico de horas;
5. fotos mensuales;
6. novedades, feriados y notas;
7. motor de próximo evento y, después, notificaciones;
8. clima;
9. widgets;
10. informes y copias de seguridad;
11. bloqueo, privacidad, accesibilidad y pulido visual;
12. pruebas integrales y preparación de publicación;
13. ampliar la remuneración versionada sólo cuando se confirmen las reglas abiertas de prorrateo y aplicabilidad.

Decisión de secuencia del 13 de agosto de 2026: Joaquin autorizó implementar **novedades, feriados y notas** inmediatamente después del motor básico de horas. El módulo de fotos mensuales continúa pendiente y no queda cancelado; solamente se pospone en el orden de ejecución.

Decisión posterior del 14 de agosto de 2026: después de integrar novedades, feriados y notas, Joaquin autorizó implementar **Vacaciones** como incremento separado sobre Room v3. Este módulo registra y clasifica días, pero no implementa todavía remuneración vacacional.

Decisión posterior del 15 de agosto de 2026: después de integrar fotos, accesibilidad y pulido visual, Joaquin autorizó implementar primero el **Motor de próximo evento** como dependencia separada. Debe ser la única fuente reutilizable para la aplicación, las futuras notificaciones y los futuros widgets; las notificaciones no forman parte de ese primer incremento.

Decisión posterior del 16 de agosto de 2026: después de integrar y publicar Notificaciones sobre Room v5, Joa autorizó **Clima** como la siguiente dependencia separada. V1 conserva Córdoba Capital como ubicación fija, no solicita ubicación del teléfono, usa caché privada y debe degradar sin bloquear Calendario ni notificaciones. El proveedor queda detrás de una interfaz reemplazable; cualquier uso comercial debe respetar términos vigentes y no puede asumir que un endpoint gratuito lo autoriza.

Decisión posterior del 16 de agosto de 2026: Joa autorizó un incremento conjunto de UX y remuneración. Se simplifican Agregar, Fotos, conflictos y detalle de guardia; se aclara que los avisos visibles son notificaciones comunes con cronómetro nativo; y se incorpora al final de Resumen una primera estimación bruta SUVICO con antigüedad. En una fecha ocupada, el bloque de acciones conserva `Informar novedad / notas` y ordena las acciones de gestión como `Editar`, `Agregar una segunda guardia` y `Eliminar`; no ofrece `Agregar francos`. Las reglas salariales todavía abiertas permanecen fuera de alcance y no pueden inferirse del estimador.

Decisión posterior del 16 de agosto de 2026: el detalle meteorológico horario se recorre horizontalmente mediante tarjetas, para no alargar la pantalla en vertical. Los calendarios usados al agregar guardias o francos ocupan todo el ancho disponible, tienen celdas ampliadas y muestran número más inicial histórica del día (`3L`, `4M`, `5X`, `6J`, `7V`, `8S`, `9D`). Esta decisión no cambia fechas, estados ni cálculos.

Decisión posterior del 17 de agosto de 2026: al guardar o editar una guardia, cualquier `F` o `?` explícito de la fecha efectivamente guardada se elimina dentro de la misma transacción. La guardia pasa a definir ese día; una fecha omitida por la política `Conservar ocupadas` no se toca. Las carpetas médicas conservan su advertencia y no se modifican.

Decisión posterior del 17 de agosto de 2026: MiGuardia adopta **Vigilia** como identidad visual definitiva en variantes oscura y clara, con selección persistida `Seguir el sistema`, `Claro` y `Oscuro`. Conserva superficies profundas, acentos magenta/violeta usados con moderación, tokens semánticos y una acción dominante por bloque. La navegación y Configuración aplican divulgación progresiva. Regla rectora: “Vigilia no grita. Señala.”

Decisión de hoja de ruta actualizada el 18 de agosto de 2026: después de cerrar la experiencia actual, MiGuardia se ampliará para cuatro grupos explícitos: médicos, enfermeros, policías y vigiladores de seguridad que trabajan en distintos lugares, días y horarios. No forma parte del incremento actual. Antes de implementarlo se deben definir el vocabulario compartido y específico, la configuración por profesión, qué módulos aplican a cada grupo y cómo se oculta SUVICO fuera de vigilancia. La futura distribución o monetización se decide por separado y esta dirección no autoriza cuentas, servidor, nube ni sincronización.

Decisión posterior del 17 de agosto de 2026: la experiencia inicial se implementa secuencialmente: primero un calendario en modo consulta con `Editar calendario`; luego Perfil laboral y reorganización de Configuración; después el rediseño Vigilia y control de visibilidad de Notificaciones; por último bienvenida, onboarding y primera carga guiada. Cada dependencia nace del `HEAD` verificado de MAIN después de integrar la anterior.

Decisión posterior del 17 de agosto de 2026: Notificaciones debe conservar Android 8/API 26 como mínimo compatible. Su contenido se refina como tarjeta de estado Vigilia dentro de los límites visuales del panel de Android, con degradación equivalente en versiones antiguas. `Eliminar notificación` oculta sólo el aviso de esa guardia y `Mostrar notificación nuevamente` permite restaurarlo desde Configuración mientras siga siendo elegible. En Android 14 o superior el sistema puede permitir también el descarte por gesto, aun cuando MiGuardia solicite persistencia; la aplicación debe reconocer esa limitación sin prometer un bloqueo imposible.

Corrección de secuencia actualizada el 18 de agosto de 2026: el Calendario en consulta/edición, los controles explícitos de visibilidad de Notificaciones y Perfil laboral con la reorganización acotada de Configuración están integrados y publicados en `main`. Perfil quedó consolidado en `32767084ae96c399ab4af40cae35eaa40b1ae925`, conservando `codex/guard-profile-settings` como referencia en el mismo SHA. La orientación visual de Fotos quedó implementada, auditada y publicada en `84d2fafc3ed4abcaa648e23ab75dcb8878e1dbba`, sin migrar archivos ni modificar Room, permisos o datos. La puerta correctiva continúa secuencialmente con la sustitución de la barra inferior por menú lateral y, después, con la selección simple/múltiple sobre la grilla principal del Calendario y `Editar día` desde el detalle. Recién después se construyen el recorrido contextual, la primera carga y Ayuda sobre la interfaz definitiva. No se reabre ni amplía Notificaciones.

Decisión posterior del 18 de agosto de 2026: Joa rechazó el panel lateral desnudo de tres destinos y decidió que sea la jerarquía completa de acceso. Se elimina `Configuración` como pantalla contenedora; el panel desplazable agrupa Calendario, Resumen y los apartados implementados de trabajo, avisos y aplicación. Perfil, Objetivos y horarios, Feriados, Vacaciones, Notificaciones y Clima abren directamente sus superficies existentes. `Apariencia` concentra tema y zoom. No se agregan placeholders de funciones futuras ni se duplican controles.

Decisión posterior del 18 de agosto de 2026: Joa rechazó que la edición simple o masiva reemplace el Calendario por una pantalla secundaria con otra grilla pequeña o circular. La grilla mensual principal es el único selector de fechas. En consulta, el detalle termina en `Editar día`; en edición, la misma grilla selecciona días y conserva su representación completa, mientras la bandeja y los controles reales de guardia o franco aparecen debajo. `Editar calendario` comienza vacío, `Editar día` preselecciona sólo esa fecha y ninguno escribe por sí solo. Los calendarios internos y el selector `Una fecha`/`Varias fechas` desaparecen de Guardia y Francos. El lote de varios francos debe ser atómico sin cambiar Room v5 ni sus esquemas.

La primera versión utilizable debe alcanzar almacenamiento local, calendario, carga individual/múltiple, objetivos/horarios, fotos y horas básicas antes de sumar capas más complejas.

## 26. Criterios transversales de aceptación

### Política de validación por impacto

Decisión de Joa del 17 de agosto de 2026: una función que ya fue auditada y permanece sin cambios conserva su última evidencia verde. No se repite automáticamente toda la batería histórica en cada integración sólo para volver a demostrar lo mismo.

Para cada entrega, MAIN y la dependencia deben:

1. construir un mapa de impacto a partir del diff real: archivos, contratos, consumidores y recorridos modificados;
2. ejecutar las pruebas nuevas o modificadas y las regresiones vecinas que puedan verse afectadas;
3. volver a auditar físicamente sólo las superficies o integraciones Android cuyo comportamiento cambió;
4. dejar asentado qué se ejecutó, qué no se repitió y cuál es la última evidencia verde que se conserva;
5. ampliar la batería si aparece un fallo inesperado, una dependencia compartida cambió o el impacto no puede acotarse con confianza.

La batería global completa se reserva para cambios transversales —por ejemplo Gradle, manifiesto, dependencias, Room/migraciones, contratos compartidos de dominio, composición raíz o navegación con impacto amplio— y para hitos de release, publicación o auditoría integral solicitada. Compilar el módulo afectado, revisar el diff, ejecutar `git diff --check` y controlar seguridad/privacidad siguen siendo obligatorios en toda integración.

Cambiar una función invalida su evidencia anterior: esa función y sus dependencias directas deben auditarse nuevamente. Un cambio meramente documental no obliga a repetir pruebas de aplicación salvo que revele una contradicción con el comportamiento implementado.

Cada módulo debe demostrar como mínimo:

- persistencia tras cerrar/reabrir y reiniciar cuando corresponda;
- conducta correcta sin internet;
- permisos concedidos y denegados;
- Vigilia clara y oscura, contraste y tipografía predeterminada de MiGuardia;
- datos vacíos, inválidos y límites;
- errores recuperables sin pérdida;
- pruebas automatizadas de lógica;
- prueba en S25 Ultra para funciones Android del sistema;
- ninguna filtración de datos reales o secretos al repositorio/logs.

Casos críticos obligatorios:

1. guardia 19:00–07:00 mostrada solo en la fecha inicial;
2. cuenta regresiva atravesando medianoche;
3. advertencia por descanso menor a 12 h y continuación autorizada;
4. segunda guardia excepcional en el mismo día;
5. selección múltiple con reemplazar, omitir ocupados y cancelar;
6. edición masiva prohibida para familias diferentes;
7. plantilla editada sin alterar guardias históricas;
8. guardia del 31 atribuida al mes inicial;
9. tramo posterior a medianoche clasificado como feriado del día siguiente;
10. noche calculada solo en la intersección 21:00–06:00;
11. extra recién al superar 204 horas;
12. ausencia/cancelación/CM con cero horas y recuento separado;
13. nota de salida temprana sin modificar horas automáticamente;
14. editar/borrar guardia reprograma notificación y widget;
15. reinicio conserva/recrea trabajo programado;
16. restaurar agosto no elimina septiembre;
17. informe parcial correctamente fechado;
18. clima no disponible no bloquea calendario ni avisos.
19. guardia retrocargada cuyo fin ya pasó aparece automáticamente completada, salvo ausencia o cancelación explícita.
20. vacaciones que atraviesan fin de mes/año cuentan fechas únicas en cada mes;
21. guardia `PLANNED` dentro de vacaciones queda fuera de todas las horas sin ser borrada ni modificada;
22. ausencia o cancelación explícita dentro de vacaciones conserva su clasificación propia;
23. feriado, `F` o `?` puede coexistir con `V`, mientras carpeta médica superpuesta se rechaza.
24. el calendario abre en modo consulta y ninguna interacción de ese modo modifica repositorios;
25. `Editar calendario` conserva mes y posición, se distingue con texto y `Terminar` o Atrás regresan con seguridad a consulta.

## 27. Política para decisiones todavía abiertas

MAIN puede decidir detalles técnicos reversibles. Para una decisión de producto no definida:

1. comprobar que realmente no esté resuelta aquí;
2. describir el caso concreto y su impacto;
3. proponer una opción recomendada y hasta dos alternativas;
4. pedir a Joaquin una respuesta simple;
5. registrar la decisión aprobada en este documento o en el documento funcional canónico antes de implementar.

Abiertos conocidos:

- prorrateo mensual de básico, presentismo, sumas no remunerativas y viáticos;
- pérdida de presentismo y tratamiento monetario de ausencias/carpetas;
- componentes remunerativos, valores históricos, tope y contabilización exacta de vacaciones SUVICO;
- selección entre recargo extra del 50 % y del 100 % según cada situación;
- aplicabilidad de descuentos personales y cálculo neto;
- proveedor meteorológico;
- `minSdk` y versiones técnicas;
- detalles finos de publicación y distribución;
- modelo de comercialización: compra única, suscripción o combinación;
- matriz de funciones gratuitas y pagas, restauración y tratamiento de usuarios existentes;
- límite/forma final de cualquier personalización no especificada por Android.

No reabrir decisiones cerradas solo por preferencia técnica. Si Android impone una limitación, mostrar evidencia oficial y proponer la adaptación más fiel.

## 28. Primera misión de MAIN

En tu primer turno:

1. confirmá que leíste `AGENTS.md` y este prompt;
2. revisá `git status`, estructura, remoto y herramientas sin exponer credenciales;
3. resumí en lenguaje sencillo tu comprensión del producto y sus límites;
4. proponé el primer hito técnico: proyecto Android mínimo, arquitectura local y una pantalla inicial ejecutable;
5. identificá decisiones técnicas que podés tomar de forma reversible y cualquier decisión de producto que de verdad necesite a Joaquin;
6. esperá la autorización de Joaquin antes de crear la estructura Android si él inició MAIN solo para validar el traspaso;
7. una vez autorizado, implementá, compilá y ejecutá en el S25 Ultra, explicando cada paso.

No crees todos los chats ni todos los módulos al inicio. Avanzá por dependencias reales. Podés modelar las escalas versionadas después de que Joaquin valide esta transcripción, pero no implementes prorrateos, netos ni reglas salariales abiertas. No uses datos reales del cronograma en pruebas; crear fixtures ficticios.

## 29. Declaración final de autoridad

Todo lo anterior representa la intención funcional aprobada de MiGuardia al cierre de PLANIFICACIÓN. Tu responsabilidad no es reinterpretarla libremente, sino transformarla en software confiable y ayudar a Joaquin a comprender cada avance. Si proponés mejorar algo, diferenciá con claridad:

- **decisión ya aprobada**;
- **detalle técnico elegido por MAIN**;
- **propuesta todavía no aprobada**.

Empezá pequeño, verificá de verdad y mantené siempre la visión completa.
