# Glosario: códigos y jerga del proyecto

Traducción a castellano llano de los nombres que quedaron de la etapa con Codex.
Desde el 7/11/2026 los documentos para Joaquín usan el **nombre claro**; el código
puede ir entre paréntesis sólo cuando hace falta ubicarlo en el código o en la evidencia vieja.

Los códigos **no se renombran dentro del código ni de las pruebas** (por ejemplo
`C096Fixture.kt`): cambiarlos rompería el rastro entre pruebas, evidencia y catálogo
sin ganar nada. Este glosario es el puente.

## Etapas y bloques

| Código | Nombre claro | Qué es |
|---|---|---|
| Stage N / Etapa N | Etapa N | Tramo del plan de cierre (ver `docs/PLAN_MAESTRO_CIERRE_3_0.md`) |
| B1 · Stage 12.1 | **Cierre de Trabajo y Calendario** | Etapa actual: probar a fondo cargar, editar y ver jornadas en el calendario |
| 12.2 | Cálculos y consulta | Horas, metas, extras, Resumen e informes PDF/XLSX |
| 13 | Datos y archivos | Notas, fotos, Copias, restaurar, archivos dañados |
| 14 | Sistema y aceptación final | Avisos, Widget, bloqueo, Clima, temas, accesibilidad |
| 15 | Preparar la entrega | Firmar, probar la actualización desde 2.0.2 y publicar |
| Stage 13 / «SOS Stage 13» | Rescate de noviembre | Arreglo de emergencia dentro de B1; **no** es la etapa 13 del plan |
| Rondas integrales (0/2) | **Pasada completa final** | Probar *todo* de punta a punta sobre la versión final. Se pedían dos; desde el 7/11 es una sola más una prueba corta tras cada arreglo |

## Intentos y versiones

| Código | Nombre claro | Qué es |
|---|---|---|
| R4, R12, R13, R14… | **Intento 4, 12, 13, 14…** | Número de intento de una misma prueba. «C096 R14» = el 14.º intento de la prueba de recuperación del calendario |
| r1, r2 (en nombres de carpeta) | Versión 1, 2 de un arreglo | Igual que arriba, pero para entregas o correcciones |
| Vigilia R4 | Diseño visual elegido (versión 4) | Candidata de diseño de la app |
| Candidata ordinaria | **Versión de prueba armada** | El APK de prueba compilado con todos los arreglos juntos |
| build-human-r2 | Compilación de prueba con toque humano, versión 2 | Una candidata concreta |
| Pareja QA/runner | **App de prueba + app que la maneja** | Android instala dos APK para pruebas automáticas: la app (QA) y el «robot» que la usa (runner) |
| Inputs/bindings | Datos de entrada y amarre de la prueba | Archivos que le dicen a la prueba qué versión exacta y qué datos usar |
| Admisión | Visto bueno para correr en el teléfono | Revisión previa antes de instalar en el Samsung |
| Fixture | **Datos de prueba** | Jornadas y configuraciones inventadas para probar |

## Roles y circuito

| Código | Nombre claro | Qué es |
|---|---|---|
| MAIN | **Coordinador** | El chat que organiza, revisa y acepta |
| Stage (chat) | **Ejecutor** | El chat que programa y prueba una etapa |
| Handoff | Entrega / relevo | Resumen de lo hecho para el siguiente |
| Recibo, sello, payload, delivery_key | Comprobante de entrega | Archivos que prueban qué se entregó. Burocracia del circuito Codex |
| HOLD | **Freno** | Algo detenido hasta que se resuelva. HOLD=false: no quedó frenado |
| STOP auditado | Parada registrada | Un intento que se detuvo y se documentó por qué |
| Puerta 0 | Chequeo inicial | Mirar ruta, rama y cambios antes de tocar nada |
| Gate | Condición para avanzar | |
| Checkpoint | Punto de guardado | Commit o archivo de estado para retomar |

## Pruebas y entorno

| Código | Nombre claro | Qué es |
|---|---|---|
| QA | Prueba de calidad | |
| QA humana / toque humano | **Paso que hacés vos con el dedo** | Partes que no se pueden automatizar (elegir una foto, aceptar un permiso) |
| OEM | **Cosas propias de Samsung** | Pantallas o conductas del fabricante, no de Android puro |
| Muerte real del proceso | Android cierra la app por falta de memoria | Se prueba que al volver no se pierda nada |
| Recreación | La pantalla se reconstruye (por ejemplo al girar) | |
| Conservación | **No perder datos** | Comprobar que las bases y archivos quedaron iguales |
| ADB | Cable de control del teléfono | Herramienta para instalar y manejar el Samsung desde la PC |
| AVD | Emulador | Teléfono virtual en la PC |
| PTY | Terminal donde corrió la prueba | Detalle técnico; «PTY 59687» es el número de esa terminal |
| JVM / pruebas locales | Pruebas que corren en la PC | Rápidas, sin teléfono |
| Instrumentación | Pruebas que corren en el teléfono | |
| SAF | Selector de archivos de Android | El que aparece para elegir dónde guardar o abrir |
| Room V8 | Base de datos, versión 8 del formato | |
| ADR | Decisión técnica registrada | En `docs/adr/` |
| IDSCount | Contador interno de Android | Un dato técnico que Android cambia solo; tiene excepción en las pruebas |
| B0 | Foto inicial de los datos | Estado de las bases antes de la prueba, para comparar después |
| G (en «G final», «G calificado») | Verificación de conservación global (interpretación) | No está definido en los documentos vigentes; es la mejor lectura del contexto |
| EGM.md | Mapa técnico de la prueba pendiente | Índice para programadores |
| derived_copy | Copia temporal | Causó un falso «distinto» al comparar datos; ya se arregló |
| wire / native-two | Formato de los comprobantes de prueba | Detalle interno |
| ABBA | Método de medición de rendimiento | Comparar A, B, B, A para descartar azar |

## Catálogo de casos de prueba (441 casos)

Definidos en el anexo de `docs/prompts/STAGE_12_VERIFICACION_FUNCIONAL_VISUAL_E_INTEGRIDAD_3_0.md`.
La letra dice el área y el número es correlativo.

| Letra | Área |
|---|---|
| C | Calendario (grilla, tarjeta, detalle del día, carga, edición) |
| A | Resto de la app (configuración, repeticiones, horas, Resumen, Copias, Clima, etc.) |
| W | Widget de inicio |
| X | Recorridos combinados que cruzan varias áreas |

Los que aparecen en el estado actual:

| Código | Nombre claro |
|---|---|
| C032 | Abrir la dirección de un lugar en Maps (y qué pasa si no hay Maps) |
| C074 | Registrar una cancelación con motivo |
| C089 | Editar una jornada sin cambiar su fecha ni su historia |
| C096 | **Recuperar el calendario después de que Android cierra la app** (mes, selección, detalle y borrador intactos, sin guardar solo) |
| C097 | Si un lugar u horario cambia o se archiva mientras elegís, pedir que vuelvas a elegirlo |
| A041 | Convertir la dirección del lugar en ubicación aproximada (para Clima) |
| A042 | Pedir la ubicación aproximada actual, sólo si la persona lo pide |
| A051 | Elegir fechas de inicio y fin de una repetición |
| W010 | Widget en modo privado no muestra datos |
| X015 | Consultar pantallas sin modificar ningún dato |
