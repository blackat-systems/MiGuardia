# Índice canónico de prompts — MiGuardia

- Estado: activo
- Última auditoría completa: 2026-08-27
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
- **Última dependencia cerrada:** `Resumen personalizable`, auditada, corregida
  integrada y publicada por MAIN sin modificar Room V2 versión 5.
- **Próxima dependencia habilitada:** `Próximo evento y avisos`, mediante
  `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md`. El prompt está preparado; la tarea
  todavía no fue abierta.
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
| `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md` | **HABILITADO** | Una sola verdad V2 para la tarjeta y los avisos de jornadas o disponibilidad; tarea todavía no abierta |

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
| `MOTOR_DE_PROXIMO_EVENTO.md` | Próximo evento compartido | Conservar motor V1; adaptar después de definir tipos sectoriales |
| `NOTIFICACIONES.md` | Avisos locales y Room v5 | Motor heredado; adaptación 2.0 necesita prompt nuevo |
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
| `ONBOARDING_Y_PRIMERA_CARGA.md` | Diseño de onboarding V1 | **REESCRIBIR PARA V2** después de configurar los cuatro sectores |

## Secuencia de contratos

MAIN crea o actualiza el prompt de un bloque cuando Joaquin se lo pide. Una
dependencia nueva sólo puede habilitarse cuando la anterior está cerrada y
Joaquin indicó que quiere preparar o abrir la siguiente:

1. Room v6 y configuración inicial: **cerrado**;
2. **Cerrado:** lugares, tipos, plantillas y primera apertura visible — Corte A
   de contratos y Room v7, más la configuración inicial, verificados;
3. **Cerrado:** carga manual V2 de jornadas nuevas sobre la única grilla;
4. **Cerrado:** edición y eliminación individual de una jornada V2 en su fecha
   original;
5. **Cerrado:** modo V1 retirado, código común preservado y Room V2 versión 1
   fijada y verificada;
6. **Cerrado:** repetir jornadas y decidir si un cambio afecta sólo una fecha o
   todo lo futuro, mediante
   `REPETIR_JORNADAS_Y_CAMBIAR_DESDE_UNA_FECHA_V2.md`;
7. **Cerrado:** horario real y clasificación exacta de la diferencia
   adicional en jornadas existentes, mediante
   `REGISTRAR_HORARIO_REAL_Y_CLASIFICAR_HORAS_EXTRA_V2.md`;
8. **Cerrado:** extras independientes y avance contra la referencia mediante
   `EXTRAS_INDEPENDIENTES_Y_AVANCE_DE_HORAS_V2.md`; la persona elige la fecha
   de reinicio y no existe prorrateo automático;
9. **Cerrado:** guardias pasivas y disponibilidad mediante
   `GUARDIAS_PASIVAS_Y_DISPONIBILIDAD_V2.md`;
10. **Cerrado:** Calendario final y tarjeta superior mediante
    `CALENDARIO_FINAL_Y_TARJETA_SUPERIOR_V2.md`;
11. **Cerrado:** Resumen personalizable mediante
    `RESUMEN_PERSONALIZABLE_V2.md`;
12. **Habilitado, tarea no abierta:** adaptación de próximo evento y
    notificaciones mediante `PROXIMO_EVENTO_Y_NOTIFICACIONES_V2.md`;
13. auditoría integral del núcleo;
14. segunda capa: widget, informes, copias, bloqueo y Ayuda/recorrido inicial;
15. auditoría de la aplicación completa y candidato local.

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
