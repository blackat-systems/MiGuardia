# Índice canónico de prompts — MiGuardia

- Estado: activo
- Última auditoría completa: 2026-08-21
- Regla: un prompt sólo se ejecuta si este índice lo marca `ACTIVO` o
  `HABILITADO` y Joaquin autoriza la tarea

## Estado operativo actual

- **PLANIFICACIÓN:** cerrada por autorización expresa de Joaquin.
- **MAIN 2.0:** reactivada y habilitada.
- **Ejecución:** autorizada por bloques pequeños, ordenados por dependencias y
  verificados antes de continuar.
- **Commits locales:** permitidos como checkpoints de bloques comprobados.
- **Push, tag, Release y producción:** no autorizados.

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
| `docs/PROMPT_MAESTRO_MAIN_2_0.md` | **ACTIVO / HABILITADO** | Ejecutar el plan por bloques pequeños, dependientes y verificables |
| `docs/PROMPT_MAESTRO_MAIN.md` | **HISTÓRICO V1** | Consultar sólo comportamiento heredado de 1.0 |
| `docs/PROMPT_MAESTRO_PAUSA_REVISION_Y_REANUDACION.md` | **HISTÓRICO V1** | Fotografía antigua de Git; nunca reanudar desde sus SHA |

## Prompts de MiGuardia 2.0

| Archivo | Estado | Explicación humana |
|---|---|---|
| `UX_UI_CALENDARIO_ADAPTABLE_2_0.md` | **CERRADO** | El Calendario ya se adapta y conserva todo su contenido alcanzable |
| `REGLAS_CONFIGURACION_LABORAL_POR_MES.md` | **DESCARTADO / HISTÓRICO** | El código candidato no está en el árbol y no debe recuperarse; la vigencia mensual fue reemplazada por cambios desde una fecha concreta |
| `REGLAS_DOMINIO_CONFIGURACION_Y_HORAS_V2.md` | **CERRADO** | Reglas puras verificadas de sector, vigencia por fecha, referencias de horas, extras y reglas por lugar, sin Room ni pantallas |
| `CONFIGURACION_PERSISTENTE_Y_MIGRACION_ROOM_V6.md` | **CERRADO** | La configuración global ya se guarda y Room migra 5→6 sin alterar las trece familias históricas |
| `LUGARES_TIPOS_PLANTILLAS_Y_PRIMERA_CARGA_V2.md` | **HABILITADO** | Elegir sector, crear lugares, tipos y plantillas, y cargar jornadas V2 con historia propia |
| `PRIMERA_APERTURA_Y_CONFIGURACION_LABORAL_VISIBLE_V2.md` | **HABILITADO** | Dependencia acotada: primera pantalla de rubro y creación visible del primer lugar y horario |

## Contratos históricos de MiGuardia 1.0

Todos los archivos de esta tabla están rotulados `HISTÓRICO V1 — NO
EJECUTAR`. Sus cuerpos se conservan para entender decisiones, pruebas y
compatibilidad.

| Archivo | Qué construyó o documentó | Situación en 2.0 |
|---|---|---|
| `DATA_LOCAL.md` | Primer Room local | Integrado y evolucionado hasta Room v5 |
| `CALENDARIO_MENSUAL.md` | Primera grilla mensual | Reemplazado por la experiencia de Calendario actual |
| `OBJETIVOS_Y_GUARDIAS.md` | Lugares, horarios y cargas | Herencia funcional; adaptar sólo vocabulario visible |
| `MOTOR_BASICO_DE_HORAS.md` | Horas V1 de Vigilancia | Conservar únicamente como cálculo histórico de 1.0 |
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

## Próximos contratos por escribir

MAIN crea o actualiza el prompt de cada bloque antes de implementarlo. El primer
contrato ya está cerrado; los siguientes nacen únicamente cuando su dependencia
anterior esté cerrada:

1. Room v6 y configuración inicial: **cerrado**;
2. **Activo:** lugares, tipos, plantillas y primera carga V2 — Corte A de
   contratos y Room v7 verificado; la primera dependencia del Corte B es la
   apertura y configuración laboral visible, antes de adaptar la carga manual;
3. recurrencias y edición puntual/masiva;
4. motor de horario real, extras y cumplimiento;
5. disponibilidad y situaciones especiales;
6. Calendario final y tarjeta superior;
7. Resumen personalizable;
8. adaptación de próximo evento y notificaciones;
9. widget, informes, copias y bloqueo cuando llegue su capa.

MAIN habilita cada prompt en este índice cuando su contrato y dependencias estén
cerrados. Los checkpoints pueden confirmarse localmente después de la
verificación; cualquier publicación requiere una autorización nueva.

## Nombres humanos obligatorios

No presentar a Joaquin códigos como `1A`, `1B` o `Incremento 2`. Usar el objetivo
real, por ejemplo:

- `Reglas internas para configurar el trabajo y aplicar cambios desde una fecha`;
- `Guardar la configuración y permitir elegirla`;
- `Registrar horas adicionales y guardias pasivas`;
- `Mostrar horas por sector en Calendario y Resumen`.

Las siglas técnicas pueden quedar dentro de especificaciones internas, siempre
acompañadas por su traducción.
