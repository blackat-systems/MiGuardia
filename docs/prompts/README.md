# Índice canónico de prompts — MiGuardia

- Estado: activo
- Última auditoría completa: 2026-08-23
- Regla: un prompt sólo se ejecuta si este índice lo marca `ACTIVO` o
  `HABILITADO` y Joaquin autoriza la tarea

## Estado operativo actual

- **PLANIFICACIÓN:** cerrada por autorización expresa de Joaquin.
- **MAIN 2.0:** reactivada y habilitada.
- **Ejecución:** guiada por los handoffs y pedidos expresos de Joaquin.
- **Orquestación secuencial:** MAIN recibe, audita, integra y cierra un handoff
  por vez mediante `ORQUESTACION_SECUENCIAL_MAIN_2_0.md`; no crea el prompt ni
  la tarea siguiente por su cuenta.
- **Commits locales:** MAIN los crea automáticamente como checkpoints de
  bloques comprobados.
- **Push puntual de la rama 2.0:** ejecutado y verificado hasta `836d908`; la
  autorización quedó consumida al fijar la base de `Cargar jornadas`.
- **Otros pushes, tag, Release, `main` y producción:** no autorizados.

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
| `PERFIL_LABORAL_Y_CONFIGURACION.md` | Perfil local V1 | No sirve para configurar cuatro sectores; compatibilidad solamente |
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
4. **Próximo recomendado, no habilitado:** retirar el modo V1 y establecer una
   base exclusiva de V2, conservando el código útil;
5. cambios de sector desde una fecha dentro de V2;
6. recurrencias y edición puntual/masiva;
7. horario real, extras y avance contra la referencia;
8. disponibilidad, situaciones especiales y consolidación final del motor de
   horas y cumplimiento;
9. Calendario final y tarjeta superior;
10. Resumen personalizable;
11. adaptación de próximo evento y notificaciones;
12. auditoría integral del núcleo;
13. segunda capa: widget, informes, copias, bloqueo y Ayuda/recorrido inicial;
14. auditoría de la aplicación completa y candidato local.

Cuando Joaquin pide un prompt, MAIN lo habilita en este índice después de cerrar
su contrato y dependencias, y crea automáticamente el checkpoint documental
local verificado. Pedir el prompt no abre por sí solo la tarea. La autorización
puntual de publicación indicada arriba se agotó al fijar esta base; cualquier
otra publicación requiere una autorización nueva.

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
