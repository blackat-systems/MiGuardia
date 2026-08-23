# MiGuardia — reglas permanentes del repositorio

## 0. Estado de MiGuardia 2.0

Esta rama desarrolla **MiGuardia 2.0** como evolución del código de la prueba
MiGuardia 1.0 hacia un producto nuevo sin migración de datos. Nace técnicamente
del tag inmutable `v1.0.0`, commit
`82db6fd8eb2c511205968894dc9857a96b16ed20`, en la rama
`codex/miguardia-2.0`.

Estado operativo vigente desde el 2026-08-21: **PLANIFICACIÓN cerrada y MAIN
2.0 activo**. Joaquin autorizó ejecutar la hoja de ruta completa por bloques,
con implementación, pruebas y checkpoints locales después de cada entrega
verificada. Push, tag, Release y cualquier acción sobre producción siguen
siendo puertas separadas.

Reglas obligatorias durante la transición:

- no mover, reemplazar ni reescribir el tag `v1.0.0`;
- mantener por ahora `applicationId = "com.blackatsystems.miguardia"`; esto no
  implica compatibilidad ni traspaso desde 1.0 y cualquier cambio de paquete
  sigue siendo una puerta separada;
- tratar MiGuardia 2.0 como instalación limpia: no existe un recorrido de
  activación, adopción ni migración de datos desde 1.0;
- considerar el modo `MIGRATED_V1`, el motor V1 y la cadena Room heredada como
  deuda temporal del árbol actual, no como comportamiento que deba preservarse;
- no borrar ni limpiar datos del teléfono de forma silenciosa: toda
  desinstalación o limpieza de una prueba anterior requiere una acción expresa;
- no recuperar código desde worktrees históricos como sustituto de la base
  sellada;
- mantener una sola configuración laboral por usuario; no crear múltiples
  perfiles laborales;
- considerar `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`,
  `docs/PLANIFICACION_MIGUARDIA_2_0.md`, `docs/sectores/` y los ADR de 2.0 como
  autoridad sobre las decisiones nuevas;
- usar `docs/PROMPT_MAESTRO_MAIN_2_0.md` como prompt activo de integración;
- implementar únicamente un bloque verificable por vez, con prompt acotado,
  pruebas proporcionales y documentación coherente antes de avanzar.

## 1. Propósito y alcance

Este repositorio contiene **MiGuardia**, una aplicación Android para organizar
jornadas laborales. MiGuardia 1.0.0 fue una prueba interna de Joaquin,
especializada en Vigilancia privada y sin usuarios externos. MiGuardia 2.0
reutiliza esa base de código, reemplaza la experiencia funcional de aquella
prueba y comienza con datos vacíos para un catálogo cerrado de cuatro sectores:
Vigilancia privada, Policía, Enfermería y Medicina.

El Calendario, la privacidad local y las capacidades comunes se comparten. Las
reglas de horas y el vocabulario se validan por sector; no se copian por
analogía. Enfermería y Medicina son sectores independientes y no existe una
opción `Otro`.

La versión inicial:

- es solo para Android;
- trabaja primero para vigiladores de Inforce en Córdoba Capital, Argentina;
- guarda los datos localmente y no usa cuentas, nube ni sincronización;
- se desarrolla en Kotlin con una interfaz moderna basada en Jetpack Compose, salvo que una decisión técnica documentada demuestre la necesidad de otra API Android;
- se prueba principalmente en el Samsung Galaxy S25 Ultra físico de Joaquin; el emulador Pixel 6a es secundario por su consumo de memoria.

## 2. Autoridad documental y lectura obligatoria

Antes de planificar, editar código o proponer una dependencia, todo agente debe leer, en este orden:

1. este `AGENTS.md` completo;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`, `docs/STATUS.md` y
   `docs/PLANIFICACION_MIGUARDIA_2_0.md` completos;
3. `docs/prompts/README.md` para comprobar el estado del prompt que se pretende
   usar;
4. la ficha aplicable de `docs/sectores/` cuando el trabajo afecte horas,
   vocabulario o comportamiento sectorial;
5. `docs/PROMPT_MAESTRO_MAIN_2_0.md` completo cuando la tarea sea MAIN o una
   dependencia de 2.0;
6. los ADR de MiGuardia 2.0 aplicables;
7. `docs/PROMPT_MAESTRO_MAIN.md` completo como base histórica heredada de 1.0;
8. los documentos del módulo afectado, si existen;
9. el código y las pruebas relacionados.

Jerarquía de decisiones:

1. una instrucción actual y explícita de Joaquin;
2. `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md` y
   `docs/PLANIFICACION_MIGUARDIA_2_0.md` para producto y decisiones de 2.0;
3. las fichas de `docs/sectores/` para reglas sectoriales respaldadas por
   evidencia;
4. `docs/PROMPT_MAESTRO_MAIN_2_0.md` cuando figure expresamente reactivado;
5. `docs/PROMPT_MAESTRO_MAIN.md` para contratos heredados que 2.0 no reemplace;
6. este archivo;
7. documentos técnicos, ADR y prompts de módulos;
8. implementación existente.

Un prompt rotulado `HISTÓRICO`, `CERRADO`, `PAUSADO` o `CANDIDATO` no es una
orden ejecutable. Sólo puede iniciarse trabajo desde un prompt que
`docs/prompts/README.md` marque expresamente como habilitado y después de la
autorización de Joaquin.

Si dos fuentes se contradicen, no inventar una solución silenciosa. Explicar el conflicto en español, recomendar una opción y pedir decisión solo si cambia el producto. Después, actualizar primero la fuente documental correspondiente y luego el código.

La conversación PLANIFICACIÓN definió el producto. El chat o tarea MAIN es el integrador técnico. Los chats especializados son dependencias de MAIN y no pueden redefinir el producto por su cuenta.

## 3. Relación con Joaquin

Joaquin es dueño del producto y está aprendiendo programación, Git y Android Studio. Por eso:

- hablar en español claro, cordial y sin asumir conocimientos previos;
- avanzar despacio, por pasos verificables, explicando qué se hizo, por qué importa y cómo comprobarlo;
- presentar primero el resultado o la decisión práctica;
- no abrumar con jerga ni pegar salidas técnicas innecesarias;
- no hacerle ejecutar manualmente tareas que el agente puede realizar de forma segura en el entorno;
- avisar antes de instalaciones, permisos, gastos, servicios externos, cambios destructivos o decisiones irreversibles;
- cuando falte una decisión funcional real, formular una pregunta concreta y recomendar una opción;
- no volver a preguntar asuntos ya decididos en el prompt maestro.

## 4. Función de MAIN y módulos dependientes

MAIN debe mantener una visión completa del producto, arquitectura, datos, UX, pruebas e integración. Antes de derivar trabajo a un chat o módulo especializado debe:

1. identificar el alcance y sus dependencias;
2. crear en `docs/prompts/` un prompt maestro autosuficiente para ese módulo;
3. incluir decisiones aplicables, límites, entradas y salidas, criterios de aceptación, pruebas y archivos que puede tocar;
4. impedir que el módulo invente reglas de negocio o modifique contratos compartidos sin autorización de MAIN;
5. integrar y verificar el resultado en el proyecto completo.

MAIN puede preparar estos prompts cuando sean necesarios. La creación o apertura de otra tarea/chat de Codex se hace solamente cuando Joaquin la pida o autorice de forma explícita. Cada dependencia devuelve resultados a MAIN; ninguna dependencia sustituye a MAIN.

Módulos conceptuales previstos: datos locales; objetivos y guardias; calendario; excepciones, notas y feriados; fotos; horas; motor de próximo evento; notificaciones; clima; widget; informes; copias de seguridad; configuración y seguridad; diseño y accesibilidad; pruebas y publicación.

## 5. Método de desarrollo

- Trabajar en incrementos pequeños, ejecutables y demostrables.
- Antes de cambios amplios, escribir un plan corto y mantenerlo actualizado.
- Inspeccionar el estado real del repositorio; no asumir archivos, versiones ni APIs.
- Para información técnica que pueda haber cambiado, usar documentación oficial vigente, preferentemente Android/Google, Kotlin, Gradle y la biblioteca involucrada.
- Registrar decisiones arquitectónicas relevantes en `docs/adr/` con contexto, alternativas, decisión y consecuencias.
- Mantener la lógica de negocio independiente de Compose y de Android cuando sea razonable, especialmente fechas, turnos, horas, feriados y resúmenes.
- Usar fechas y horas con semántica explícita. Una guardia debe tener instante local de inicio y fin; nunca inferir cruces de medianoche solo desde texto visual.
- Guardar instantáneas históricas de objetivo, abreviatura, horario, color y puesto en cada guardia; editar una plantilla no altera el pasado.
- Definir una base Room exclusiva de V2 antes de seguir ampliando persistencia.
  Como 1.0 no tiene datos que deban conservarse, no se exige una migración desde
  ella. Una vez fijada la primera base pública de V2, todo cambio posterior sí
  debe preservar sus datos mediante migraciones explícitas.
- No añadir dependencias de producción sin justificar necesidad, mantenimiento, licencia, privacidad, tamaño y alternativa nativa.
- MiGuardia organiza jornadas y horas. No incorporar tablas salariales, montos,
  estimaciones remunerativas, liquidaciones, deducciones ni datos sindicales.

## 6. Calidad y definición de terminado

Un cambio no está terminado hasta que:

- compila con el wrapper de Gradle del repositorio;
- pasa las pruebas unitarias y de instrumentación relevantes;
- tiene pruebas nuevas o actualizadas para la conducta modificada;
- se verificó en el dispositivo físico cuando afecta interfaz, permisos, notificaciones, widget, biometría, archivos o comportamiento del sistema;
- contempla estados vacío, error, sin conexión y permisos denegados cuando corresponda;
- respeta tema claro/oscuro y lector de pantalla en la superficie modificada;
- mantiene el tamaño tipográfico, la escala visual y la distribución predeterminados por MiGuardia en el ajuste interno 100 %; no adapta ni redistribuye la interfaz según `font_scale`;
- ofrece un zoom interno explícito de MiGuardia de 100 %, 150 % o 200 %, elegido y persistido por el usuario; este ajuste escala la aplicación sin consultar ni modificar valores del sistema;
- usa la densidad estable del dispositivo como referencia y no la densidad configurada por zoom o tamaño de visualización; ningún comportamiento puede activarse automáticamente a partir de esos ajustes de Android;
- no expone datos privados en logs, notificaciones, widgets, informes o capturas;
- la documentación refleja cualquier cambio de comportamiento o arquitectura.

Las pruebas pueden recorrer el zoom interno de MiGuardia, pero no deben consultar ni modificar `font_scale`, zoom, tamaño de visualización ni densidad del dispositivo. No implementar variantes automáticas basadas en esos valores del sistema.

Priorizar pruebas de límites: medianoche, fin de mes/año, febrero bisiesto, cambio de mes, dos guardias excepcionales, descanso menor a 12 horas, referencias de horas configuradas por el usuario, franjas nocturnas configuradas por lugar, feriado que corta una guardia nocturna, reprogramación de alertas, restauración parcial y datos históricos creados dentro de V2. Usar 204 horas y 21:00–06:00 sólo como valores explícitos de prueba, nunca como valores predeterminados de V2.

## 7. Privacidad y seguridad

- Los datos del usuario son locales por defecto.
- No agregar analítica, rastreadores, anuncios, cuentas, telemetría, nube ni sincronización sin autorización explícita.
- Solicitar permisos solo en contexto y explicar su beneficio y consecuencia de rechazo.
- Nunca guardar ni subir DNI, correo, teléfono o domicilio personal; el nombre o apodo es opcional.
- No guardar imágenes de certificados médicos. Las notas médicas son privadas.
- No imprimir datos personales, cronogramas, rutas locales, secretos o contenido de copias en logs.
- No confirmar secretos, tokens, claves o archivos de firma en Git. Mantener `local.properties`, `.env`, keystores, credenciales y `google-services.json` fuera del repositorio.
- El cronograma real de ejemplo contiene nombres de trabajadores: es material local de referencia, no se confirma ni se publica.
- Toda exportación, copia o eliminación debe ser una acción consciente del usuario y preservar consistencia aun si falla.

## 8. Git y GitHub

- Repositorio remoto privado: `https://github.com/blackat-systems/MiGuardia`.
- Rama principal: `main`.
- Rama de desarrollo de MiGuardia 2.0: `codex/miguardia-2.0`.
- `main` y el tag `v1.0.0` son referencias protegidas; 2.0 no se desarrolla directamente sobre ellas.
- Autor configurado: `joaquin <blackat.systems@gmail.com>`.
- Revisar `git status` y el diff antes de confirmar cambios.
- Hacer commits pequeños y coherentes con mensajes en inglés tipo Conventional Commits, por ejemplo `feat:`, `fix:`, `test:`, `docs:`, `refactor:` y `chore:`.
- No usar `git reset --hard`, no descartar cambios ajenos y no reescribir historia publicada sin autorización explícita.
- No forzar push.
- No confirmar binarios generados, APK, AAB, directorios de compilación, configuración local ni datos reales.
- MAIN decide cuándo crear ramas por módulo. Integrar solo después de revisión y pruebas.
- Antes de cada push: estado limpio esperado, diff revisado, pruebas pertinentes y ausencia de secretos.

## 9. Límites del alcance inicial

No incorporar en la primera versión salvo nueva decisión explícita:

- iOS;
- cuentas, servidor, nube o sincronización entre dispositivos;
- OCR, recorte de imágenes o importación directa de Excel;
- ubicación automática del teléfono;
- mapa embebido;
- feriados automáticos;
- búsqueda global;
- integración directa con empleadores, sindicatos u organizaciones externas.

## 10. Regla de cierre

Al terminar cada bloque, informar de forma breve:

- qué quedó funcionando;
- qué archivos relevantes cambiaron;
- qué comprobaciones se ejecutaron y su resultado;
- qué sigue y qué decisión, si alguna, necesita Joaquin.

No declarar algo terminado si solo está diseñado, simulado o parcialmente probado.
