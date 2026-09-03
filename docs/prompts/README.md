# Índice canónico de prompts — MiGuardia

- Estado: activo
- Última auditoría completa: 2026-09-03
- Regla: un prompt sólo se ejecuta si este índice lo marca `ACTIVO` o
  `HABILITADO` y Joaquin autoriza la tarea

## Estado operativo actual

- **PLANIFICACIÓN:** cerrada por autorización expresa de Joaquin.
- **MAIN 2.0:** reactivada y habilitada.
- **Ejecución:** guiada por los handoffs y pedidos expresos de Joaquin.
- **Orquestación secuencial:** MAIN recibe, audita, integra y cierra un handoff
  por vez mediante `ORQUESTACION_SECUENCIAL_MAIN_2_0.md`; no crea el prompt ni
  la tarea siguiente por su cuenta.
- **Contrato humano:** toda dependencia nueva explica primero `QUÉ HACE` y
  `POR QUÉ EXISTE`, tanto en su prompt como en su handoff.
- **Última dependencia cerrada:**
  `INTEGRACION_Y_DEPURACION_DE_CAMBIOS_DE_ULTIMO_MOMENTO_V2.md`; batería local
  y Samsung API 36 verdes.
- **Candidato compartido actual:** Ayuda y recorrido inicial, simplificación de
  formularios, ubicación puntual para clima, clima por objetivo, Room V6 y
  compatibilidad de Copias fueron auditados y corregidos por MAIN como una sola
  unidad. La batería local final pasó 707/707 pruebas JVM y 351/351 tareas;
  Room pasó 123/123 y la matriz dirigida posterior 30/30 en Samsung API 36.
- **Puerta pendiente:** auditoría de la aplicación completa y emisión de un
  candidato local antes del checkpoint. API 26/API 33 continúan como matriz de
  compatibilidad pendiente.
- **Commits locales:** MAIN los crea automáticamente como checkpoints de
  bloques comprobados.
- **Push puntual anterior de la rama 2.0:** ejecutado y verificado hasta
  `836d908`; esa autorización quedó consumida al fijar la base de
  `Cargar jornadas`.
- **Push del cierre V2-only:** Joaquin autorizó y MAIN ejecutó el 2026-08-23 un
  único push adicional para publicar el checkpoint estable V2-only y la
  recomendación futura de Agenda profesional. Esa autorización quedó
  consumida en `0364b83`.
- **Push de disponibilidad:** Joaquin autorizó el 2026-08-27 publicar el
  checkpoint verde de guardias pasivas y disponibilidad. MAIN lo ejecutó y
  verificó en `80fe8e5`; la autorización quedó consumida.
- **Push de Calendario final:** Joaquin autorizó el 2026-08-27 publicar el
  checkpoint verde de Calendario final y tarjeta superior. MAIN lo ejecutó y
  verificó en `fd6891e`; la autorización quedó consumida.
- **Push de Resumen:** Joaquin autorizó el 2026-08-27 publicar el checkpoint
  verde de Resumen personalizable. MAIN lo ejecutó y verificó en `ad777bb`; la
  autorización quedó consumida.
- **Pushes posteriores, tag, Release, `main` y producción:** no autorizados.

El prompt rector activo es `docs/PROMPT_MAESTRO_MAIN_2_0.md`. El mapa del
producto está en `docs/MAPA_MAESTRO_MIGUARDIA_2_0.md`, la planificación cerrada
en `docs/PLANIFICACION_MIGUARDIA_2_0.md` y la evidencia sectorial en
`docs/sectores/`. Si el cuerpo de un prompt todavía describe a MAIN como
pausada, este índice y la autorización expresa más reciente de Joaquin
prevalecen para el estado operativo.

## Significado de los estados

- `ACTIVO`: puede guiar la tarea actual dentro de sus límites.
- `HABILITADO`: puede ejecutarse cuando MAIN abra el bloque correspondiente.
- `PAUSADO`: existe, pero no puede ejecutar trabajo hasta una nueva activación.
- `CANDIDATO`: ya produjo una propuesta o código en disco; debe revisarse, no
  volver a ejecutarse.
- `DESCARTADO`: se conserva como antecedente, pero no debe ejecutarse ni
  recuperarse como implementación.
- `CERRADO`: fue implementado y validado; sólo se reabre ante una regresión.
- `HISTÓRICO V1`: explica cómo se construyó MiGuardia 1.0; no crea tareas 2.0.
- `REESCRIBIR PARA V2`: idea pendiente cuyo prompt antiguo no sirve para
  implementar la versión nueva.

## Prompts rectores

| Archivo | Estado | Uso permitido |
|---|---|---|
| `docs/PROMPT_MAESTRO_PLANIFICACION_2_0.md` | **CERRADO / REFERENCIA** | Conserva las decisiones que dieron forma al plan; no abre otra planificación |
| `docs/PROMPT_MAESTRO_MAIN_2_0.md` | **ACTIVO / HABILITADO** | Integrar los handoffs indicados por Joaquin en bloques pequeños y verificables |
| `docs/prompts/ORQUESTACION_SECUENCIAL_MAIN_2_0.md` | **ACTIVO / COORDINADOR** | Recibir un handoff por vez, auditarlo, integrarlo y esperar que Joaquin indique la tarea siguiente |
| `docs/PROMPT_MAESTRO_MAIN.md` | **HISTÓRICO V1** | Consultar sólo comportamiento heredado de 1.0 |
| `docs/PROMPT_MAESTRO_PAUSA_REVISION_Y_REANUDACION.md` | **HISTÓRICO V1** | Fotografía antigua de Git; nunca reanudar desde sus SHA |

## Prompts de MiGuardia 2.0

| Archivo | Estado | Explicación humana |
|---|---|---|
| `UX_UI_CALENDARIO_ADAPTABLE_2_0.md` | **CERRADO** | El Calendario ya se adapta y conserva todo su contenido alcanzable |
| `REGLAS_CONFIGURACION_LABORAL_POR_MES.md` | **DESCARTADO / HISTÓRICO** | El código candidato no está en el árbol y no debe recuperarse; la vigencia mensual fue reemplazada por cambios desde una fecha concreta |
| `REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md` | **CERRADO** | Reglas puras verificadas de sector, vigencia por fecha, referencias de horas, extras y reglas por lugar, sin Room ni pantallas |
| `CONFIGURACION_PERSISTENTE_Y_MIGRACION_ROOM_V6.md` | **CERRADO / IMPLEMENTACIÓN HISTÓRICA** | La configuración global ya se guarda; la raíz migrada y la obligación de conservar datos V1 fueron reemplazadas por ADR 0024 |
| `LUGARES_TIPOS_PLANTILLAS_Y_PRIMERA_CARGA_V2.md` | **PAUSADO / REFERENCIA** | Contrato marco ya dividido: Corte A y primera apertura están cerrados; no debe reejecutarse completo |
| `PRIMERA_APERTURA_Y_CONFIGURACION_LABORAL_VISIBLE_V2.md` | **CERRADO** | Primera pantalla de rubro y creación visible del primer lugar y horario integradas en `1f048643` |
| `CARGA_MANUAL_DE_JORNADAS_V2.md` | **CERRADO** | La carga de jornadas nuevas desde horarios guardados fue auditada, probada e integrada por MAIN |
| `EDICION_Y_ELIMINACION_DE_JORNADAS_V2.md` | **CERRADO** | La edición y eliminación exacta de una jornada V2 fue auditada, probada e integrada por MAIN |
| `RETIRAR_MODO_V1_Y_FIJAR_BASE_EXCLUSIVA_V2.md` | **CERRADO** | MiGuardia ya ejecuta una sola experiencia V2 sobre `MiGuardiaV2Database` versión 1; Samsung API 36 y emulador API 26 quedaron verificados |
| `REPETIR_JORNADAS_Y_CAMBIAR_DESDE_UNA_FECHA_V2.md` | **CERRADO** | Planes finitos, excepciones durables y cambios de una jornada o de todo lo futuro integrados sobre Room V2 versión 2 |
| `REGISTRAR_HORARIO_REAL_Y_CLASIFICAR_HORAS_EXTRA_V2.md` | **CERRADO** | Horario real y extras exactas por jornada integrados sobre Room V2 versión 3, sin calcular todavía avance |
| `EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md` | **CERRADO** | Extras sin jornada dueña, reinicio consciente de la referencia y avance de horas integrados sobre Room V2 versión 4 |
| `GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md` | **CERRADO** | Ventanas pasivas exactas integradas sobre Room V2 versión 5; sólo la unión del trabajo activo coincidente las reemplaza y nunca suman como horas trabajadas |
| `CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md` | **CERRADO** | Única grilla mensual consolidada y tarjeta desplegable con todas las jornadas de hoy, auditadas por MAIN |
| `RESUMEN_PERSONALIZABLE_V2.md` | **CERRADO** | Resumen mensual derivado, detalles ordenables y explicación exacta de cada cifra sin guardar totales ni modificar Room |
| `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md` | **CERRADO** | Una sola verdad V2 para la tarjeta y los avisos de jornadas o disponibilidad, auditada y verificada por MAIN |
| `AUDITORIA_INTEGRAL_DEL_NUCLEO_Y_COMPATIBILIDAD_ANDROID_V2.md` | **CERRADO** | El núcleo aprobó la batería, las tres barreras y la matriz Samsung API 36, Android 8/API 26 y Android 13/API 33 |
| `PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md` | **CERRADO** | Las tres barreras quedaron auditadas: fotografía transversal, carrera CAS real y demostración de que consultar no escribe |
| `WIDGET_DE_PROXIMO_EVENTO_V2.md` | **CERRADO** | Widget nativo auditado y verificado por MAIN en Samsung API 36; API 26/API 33 quedan como compatibilidad pendiente |
| `INFORMES_LOCALES_DE_JORNADAS_Y_HORAS_V2.md` | **CERRADO** | PDF y XLSX locales derivados de Horas/Resumen, auditados y verificados por MAIN en Samsung API 36 |
| `COPIAS_Y_RESTAURACION_LOCALES_SEGURAS_V2.md` | **CERRADO** | Copia lógica completa, cifrado opcional, vista previa y restauración consciente auditadas por MAIN; Samsung API 36 verde dentro de la matriz autorizada |
| `BLOQUEO_DE_ACCESO_LOCAL_V2.md` | **CERRADO** | Puerta opcional con biometría fuerte o credencial segura del teléfono, plazos conscientes y privacidad de Recientes sin PIN propio; auditada y verificada por MAIN en Samsung API 36, con API 26/API 33 y revisión visual OEM de Recientes pendientes |
| `AYUDA_Y_RECORRIDO_INICIAL_V2.md` | **CERRADO DENTRO DEL CANDIDATO INTEGRAL** | Su implementación fue revisada junto con simplificación, ubicación, clima, Room V6 y Copias; local y Samsung API 36 verdes |
| `INTEGRACION_Y_DEPURACION_DE_CAMBIOS_DE_ULTIMO_MOMENTO_V2.md` | **CERRADO POR MAIN — CHECKPOINT PENDIENTE** | MAIN cerró defectos reproducibles y obtuvo 707/707 JVM, 351/351 tareas, Room 123/123 y matriz dirigida Samsung 30/30; no reejecutar |

## Contratos históricos de MiGuardia 1.0

Todos los archivos de esta tabla están rotulados `HISTÓRICO V1 — NO
EJECUTAR`. Sus cuerpos se conservan para entender y reutilizar código ya
probado, no para sostener compatibilidad de datos o un modo V1 en el producto.

| Archivo | Qué construyó o documentó | Situación en 2.0 |
|---|---|---|
| `DATA_LOCAL.md` | Primer Room local | Integrado y evolucionado hasta Room v5 |
| `CALENDARIO_MENSUAL.md` | Primera grilla mensual | Reemplazado por la experiencia de Calendario actual |
| `OBJETIVOS_Y_GUARDIAS.md` | Lugares, horarios y cargas | Herencia funcional; adaptar sólo vocabulario visible |
| `MOTOR_BASICO_DE_HORAS.md` | Horas V1 de Vigilancia | Fuente técnica reutilizable; no constituye un motor legado que deba permanecer visible |
| `NOVEDADES_FERIADOS_Y_NOTAS.md` | Novedades y Room v2 | Integrado; notas antiguas no se convierten en extras V2 |
| `VACACIONES.md` | Vacaciones y Room v3 | Integrado; su tratamiento de horas en V2 se define en las reglas nuevas |
| `FOTOS_MENSUALES_DEL_CRONOGRAMA.md` | Fotos privadas y Room v4 | Integrado; no repetir migración ni reglas de borrado antiguas |
| `CORRECCION_ORIENTACION_FOTOS.md` | Orientación EXIF | Cerrado; reabrir sólo por regresión |
| `MOTOR_DE_PROXIMO_EVENTO.md` | Próximo evento compartido | Adaptado y cerrado para V2 mediante `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md` |
| `NOTIFICACIONES.md` | Avisos locales y Room v5 | Adaptadas y cerradas para V2 mediante `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md` |
| `NOTIFICACIONES_PULSO_VIGILIA.md` | Presentación y ritmos | Integrado en 1.0; no reejecutar |
| `CLIMA.md` | Pronóstico opcional | Herencia vigente; proveedor comercial debe revalidarse |
| `PULIDO_VISUAL_Y_UX.md` | Pulido previo a Vigilia | Principios útiles, orden de implementación histórico |
| `VIGILIA_SISTEMA_VISUAL.md` | Identidad visual | Referencia visual heredada; implementación cerrada |
| `PERFIL_LABORAL_Y_CONFIGURACION.md` | Perfil local V1 | No sirve para configurar cuatro sectores; su superficie fija se retira en el bloque V2-only |
| `NAVEGACION_MENU_LATERAL.md` | Menú lateral | Integrado y cerrado |
| `CALENDARIO_MODO_CONSULTA_Y_EDICION.md` | Consulta y edición explícita | Integrado y reemplazado por contratos posteriores |
| `CALENDARIO_SELECCION_DIRECTA.md` | Una sola grilla para elegir días | Integrado en la base 1.0 |
| `SIMPLIFICACION_FLUJO_DE_CARGA.md` | Carga progresiva | Integrado en la base 1.0 |
| `COORDINACION_EXPERIENCIA_INICIAL_Y_PERFIL_MAIN.md` | Secuencia MAIN de V1 | Fotografía histórica de coordinación |
| `ONBOARDING_Y_PRIMERA_CARGA.md` | Diseño de onboarding V1 | **HISTÓRICO V1 / REEMPLAZADO** por `AYUDA_Y_RECORRIDO_INICIAL_V2.md` |

## Secuencia de contratos

MAIN crea o actualiza el prompt de un bloque cuando Joaquin se lo pide. Una
dependencia nueva sólo puede habilitarse cuando la anterior está cerrada y
Joaquin indicó que quiere preparar o abrir la siguiente:

1. **Cerrado:** adaptar el Calendario al tamaño del teléfono.
2. **Cerrado:** crear las reglas internas configurables de trabajo.
3. **Cerrado:** guardar la configuración y migrar Room sin destruir historia.
4. **Cerrado:** crear lugares, tipos y horarios guardados.
5. **Cerrado:** elegir el rubro y preparar el primer lugar de trabajo.
6. **Cerrado:** elegir días y cargar jornadas desde horarios guardados.
7. **Cerrado:** corregir o eliminar una jornada V2 individual.
8. **Cerrado:** retirar el modo V1 conservando el código útil.
9. **Cerrado:** repetir jornadas y decidir si un cambio afecta una fecha o todo
   lo futuro mediante `REPETIR_JORNADAS_Y_CAMBIAR_DESDE_UNA_FECHA_V2.md`.
10. **Cerrado:** registrar el horario real y clasificar horas extra mediante
    `REGISTRAR_HORARIO_REAL_Y_CLASIFICAR_HORAS_EXTRA_V2.md`.
11. **Cerrado:** registrar extras independientes y medir el avance de horas
    mediante `EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md`.
12. **Cerrado:** registrar guardias pasivas y descontar sólo el trabajo
    coincidente mediante `GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md`.
13. **Cerrado:** terminar el Calendario y la tarjeta superior mediante
    `CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md`.
14. **Cerrado:** mostrar y personalizar el Resumen mediante
    `RESUMEN_PERSONALIZABLE_V2.md`.
15. **Cerrado:** adaptar próximo evento y notificaciones como una sola verdad
    mediante `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md`.
16. **Cerrado:** auditoría integral del núcleo y compatibilidad Android.
    - **16.a — Cerrado:** tres barreras de integración incorporadas mediante
      `PRUEBAS_CRUZADAS_DEL_NUCLEO_V2.md`.
    - **16.b — Cerrado:** matriz Samsung API 36, Android 8/API 26 y Android
      13/API 33, seguida por la repetición de
      `AUDITORIA_INTEGRAL_DEL_NUCLEO_Y_COMPATIBILIDAD_ANDROID_V2.md` con
      veredicto `NÚCLEO APTO PARA SEGUNDA CAPA`.
17. **Cerrado:** Widget de próximo evento mediante
    `WIDGET_DE_PROXIMO_EVENTO_V2.md`; Samsung API 36 verde, API 26/API 33
    pendientes de compatibilidad.
18. **Cerrado:** Informes locales de jornadas y horas mediante
    `INFORMES_LOCALES_DE_JORNADAS_Y_HORAS_V2.md`; local y Samsung API 36 verdes.
19. **Cerrado:** copias y restauración locales seguras mediante
    `COPIAS_Y_RESTAURACION_LOCALES_SEGURAS_V2.md`; batería local y matriz Samsung
    API 36 autorizada verdes, con recorrido SAF cifrado real.
20. **Cerrado:** bloqueo de acceso local mediante
    `BLOQUEO_DE_ACCESO_LOCAL_V2.md`; batería global y Samsung API 36 verdes.
21. **Cerrado dentro del candidato integral:** Ayuda y recorrido
    inicial 2.0 mediante `AYUDA_Y_RECORRIDO_INICIAL_V2.md`, junto con la
    simplificación, ubicación puntual, clima por objetivo y Room V6.
22. **Cerrado por MAIN — checkpoint pendiente:** integración y
    depuración mediante
    `INTEGRACION_Y_DEPURACION_DE_CAMBIOS_DE_ULTIMO_MOMENTO_V2.md`; 707/707 JVM,
    351/351 tareas y Samsung API 36 verdes, sin commit.
23. **Pendiente:** auditoría de la aplicación completa y candidato local,
    después de cerrar la puerta física anterior.

Cuando Joaquin pide un prompt, MAIN lo habilita en este índice después de cerrar
su contrato y dependencias, y crea automáticamente el checkpoint documental
local verificado. Pedir el prompt no abre por sí solo la tarea. Todas las
autorizaciones de push anteriores, incluidas las que publicaron disponibilidad
en `80fe8e5`, Calendario final en `fd6891e` y Resumen en `ad777bb`, ya fueron
consumidas. Cualquier publicación posterior requiere una autorización nueva.

Antes de los detalles técnicos, cada contrato debe explicar en lenguaje común
qué resultado aporta y qué problema del proyecto justifica que esa dependencia
exista. El handoff repite ambas explicaciones para que MAIN pueda comprobar que
lo entregado coincide con la razón original de la tarea.

El coordinador secuencial no habilita paralelismo entre implementadores. Si ya
existe un resultado candidato o una tarea abierta, MAIN debe cerrarlo antes de
aceptar otro handoff. Ninguna dependencia siguiente se crea sin una indicación
expresa de Joaquin.

## Nombres humanos obligatorios

No presentar a Joaquin códigos como `1A`, `1B` o `Incremento 2`. Usar el objetivo
real, por ejemplo:

- `Reglas internas para configurar el trabajo y aplicar cambios desde una fecha`;
- `Guardar la configuración y permitir elegirla`;
- `Registrar horas adicionales y guardias pasivas`;
- `Mostrar horas por sector en Calendario y Resumen`.

Las siglas técnicas pueden quedar dentro de especificaciones internas, siempre
acompañadas por su traducción.

Un nombre entendible no reemplaza la explicación. Cada dependencia conserva
además dos campos separados: `QUÉ HACE` describe su resultado concreto y
`POR QUÉ EXISTE` describe el problema, la dependencia previa y el paso que
desbloquea.
