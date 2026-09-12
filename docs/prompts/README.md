# Contratos y vigencia

El bloque actual, su estado y las autorizaciones de continuidad están en `docs/STATUS.md`. Esta tabla clasifica contratos; no constituye otra hoja de estado ni autoriza trabajo por sí sola.

MAIN despacha según `docs/ORQUESTACION_MAIN.md` y el cursor de STATUS. Los números del mapa son hitos de capacidades, no nuevos contratos ni tareas automáticas; reutilizar los contratos existentes antes de preparar otro encargo.

| Contrato | Vigencia contractual |
|---|---|
| `docs/prompts/OPTIMIZACION_DEL_PROYECTO.md` | CERRADO; antecedente integrado, no reejecutar |
| `docs/prompts/PREPARAR_APK_BETA_OFICIAL_PARA_WEB_V2.md` | CERRADO; preparación unsigned integrada, no acredita firma/publicación |
| `docs/prompts/CUSTODIA_CLAVE_RELEASE_V2.md` | CERRADO; clave permanente y copia USB comprobadas, no regenerar |
| `docs/prompts/CIERRE_CREDITOS_CLIMA_V2.md` | CERRADO; integrado con QA proporcional, límites de distribución en STATUS |
| `docs/prompts/CREAR_DESDE_LA_CARGA_V2.md` | CERRADO; creación contextual y terminación visual integradas |
| `docs/prompts/DISTRIBUCION_PUBLICA_Y_SEGURIDAD_V2.md` | CERRADO en distribución code4; límites y seguimiento en STATUS |
| `docs/prompts/CORRECCION_FRANCOS_DESDE_CALENDARIO.md` | CERRADO / PUBLICADO en 2.0.2; evidencia y límites en STATUS |
| `docs/prompts/HIGIENE_PREVIA_MIGUARDIA_3.md` | AUTORIZADO / CERRADO; saneamiento estructural sin cambios de producto, evidencia y límites en STATUS |
| `docs/prompts/DISTRIBUCION_PUBLICA_WEB_Y_CONTRATO_V2.md` | CERRADO localmente; contrato/API aceptados, no distribución |
| `docs/prompts/CIERRE_ANDROID_PUBLICO_SEGURIDAD_Y_ENTREGA_V2.md` | CERRADO localmente en 19; reutilizable para firma/QA 20–21 sólo con insumos y recursos definidos en STATUS |
| `docs/prompts/TEMPLATE.md` | PLANTILLA; no ejecutable |
| `docs/prompts/AUDITORIA_FINAL_SEGURIDAD_RELEASE_V2.md` | Complemento de revisión de los veinte controles; alcance y despacho en STATUS |

Los contratos cerrados o reemplazados conservan su ruta anterior como enlace de compatibilidad. Su contenido exacto y estado al cierre están en `docs/archive/README.md`. No volver a implementar un candidato o bloque cerrado por leer un antecedente.

Un contrato define alcance y aceptación. STATUS distingue implementación, revisión, commit real, validación local, matriz física y distribución. Ninguno de esos niveles se infiere de la palabra “cerrado”.
