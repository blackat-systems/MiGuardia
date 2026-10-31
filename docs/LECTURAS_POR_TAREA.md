# Lecturas por tarea

Arranque común: `AGENTS.md` y `docs/STATUS.md`. Esta tabla dirige la lectura adicional; no obliga a leer todos sus destinos.

| Si la tarea afecta… | Consultar antes de cambiar |
|---|---|
| MAIN: elegir, retomar o delegar | STATUS, su contrato y `docs/WORKFLOW.md`; `docs/ORQUESTACION_MAIN.md` es historia 2.0, no despacho 3.0 |
| Sólo documentación o flujo | `docs/WORKFLOW.md`, documento afectado y `docs/adr/README.md` si cambia vigencia |
| Producto, vocabulario o reglas laborales | Sección pertinente de `docs/PLANIFICACION_MIGUARDIA_2_0.md`, ficha en `docs/sectores/`, ADR del tema, `core/domain/src/` y sus pruebas |
| Persistencia/migraciones | Planificación §16, ADR 0026–0030/0038, `core/database/src/` y `core/database/schemas/` |
| Calendario/navegación/letra | Planificación §12, ADR 0003/0015/0032/0042 y 0043 (letra Android), `app/src/main/java/com/blackatsystems/miguardia/ui/`, Activity y pruebas afectadas |
| Próximo evento/avisos/Widget | Planificación §15, ADR 0033 y contrato histórico sólo para garantías aún usadas; módulos next-event/notifications/widget |
| Fotos | ADR 0008/0026, `photos/`, FileProvider y pruebas de orientación/almacenamiento privado |
| Informes/Copias | Planificación §16, ADR 0034/0035, `reports/`, `backup/`, repositorios y pruebas de consistencia |
| Bloqueo/Ayuda | ADR 0036/0037, `security/`, `ui/help/` y puertas de arranque |
| Clima/ubicación | ADR 0038, `weather/` y adaptadores de ubicación |
| Distribución pública/retirada beta/actualizaciones | Planificación §22, ADR 0041 y alcance conservado de 0039/0040, `beta/`, `updates/`, contrato API del proyecto web en sólo lectura hasta su Puerta 0 propia |
| Builds/pruebas/entorno | `docs/VALIDACION.md`, `tools/`, Gradle y pruebas afectadas |
| Preparar distribución | `docs/prompts/PREPARAR_APK_BETA_OFICIAL_PARA_WEB_V2.md` sólo si STATUS lo autoriza; matriz vigente, contrato web y ADR 0039/0040 |

Los prefijos de paquetes anteriores se ubican dentro de `app/src/main/java/com/blackatsystems/miguardia/` salvo indicación distinta. Los números ADR se resuelven en `docs/adr/README.md`.

## Buscar sin cargar historia

`rg` respeta `.gitignore` y `.ignore`: código, reglas actuales y contratos accesibles primero. Las auditorías, snapshots y stubs históricos están excluidos de la búsqueda general. Para una regresión o evidencia previa, buscar explícitamente en su archivo, o usar `rg --no-ignore` sobre el directorio histórico concreto. No usar `-uu` sobre toda la carpeta salvo un inventario intencional.

`docs/archive/README.md` conserva el índice. No leer MAIN V1 ni todos los prompts cerrados para una tarea V2 sin relación. Un enlace histórico es una referencia, no una ampliación de alcance.
