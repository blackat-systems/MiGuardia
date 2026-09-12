# Recepción MAIN — auditoría final de seguridad

Fecha: 2026-09-10. Handoff leído completo desde adjunto de Joaquin; anexos en
.local-evidence/security-final/20260910-audit. HEAD Android e91e6f7394131d622153d9cd8fdbc857e3accfb3;
checkout modificado preservado. No implementación ni ensayo de explotación en esta recepción.

## Decisión

Mantener bloqueada la publicación. La auditoría entregada no acepta la candidata
para release; MAIN tampoco acepta los riesgos por silencio. Las 530 pruebas
Samsung anteriores acreditan su matriz, no ausencia universal de vulnerabilidades.

## Comprobación nueva de MAIN

- Leído Manifest: MainActivity exportada. Leídos PendingMainDestination.from y
  handlePendingDestination: admiten DIRECTIONS y, tras lookup/gates, abren geo
  con dirección del snapshot sin confirmación local adicional. AND-01 confirmado
  como ruta de código; no ataque instalado demostrado, no bypass de Bloqueo.
- Leído UpdateDocumentWriter: escritura/flush/cierre síncronos dentro de IO.
  Leído resultado del probe U-01: cancelación deja worker/stream activos hasta
  liberación del proveedor. Reproducción de la dependencia, no nueva ejecución MAIN.
- Leído rate-shared.json: 120 inválidos consumen cuota, siguiente válido 429,
  luego de 60s 200. Evidencia local heredada; no ataque remoto ejecutado.
- Leídos reporte firmado y preservación. SHA del APK recalculado por MAIN:
  a5398f194806b1791dcf12612af33e9e63591329c7b49c48042426c0c99afa8a, coincidente.
  SIGNED_ARTIFACT_CHECKS_PASSED es veredicto del verificador de la dependencia;
  no se volvió a ejecutar firma/verificador completo en esta recepción.

## Tratamiento recomendado

Un bloque correctivo cohesivo: AND-01 y U-01 Android con regresiones negativas;
W01 y variante malformada W02 web, sin presentar esa variante corregida como
protección distribuida. Completar análisis de toolchain DEP-01 y configuración
operativa, sin actualización beta automática ni aceptación implícita de riesgos.
No se atribuye el aviso del plugin a runtime Android; aplicabilidad por resolver.

Si cambia Android, conservar APK anterior y generar/verificar/firmar el final
con la misma clave, renovar QA pertinente y completar upgrade poblado. Web no
requiere refirmar bytes Android idénticos. TLS/headers/IAM/cuotas reales y descarga
publicada siguen siendo puertas separadas. No cuentas, RLS o cifrado adicional
de Room por automatismo de checklist.

INC-01 de la dependencia se conserva como desviación: tráfico incidental del
arnés Miniflare y caché fuera de alcance, posteriormente corregidos/repetidos.
MAIN no inspeccionó contenido de cuarentena ni lo eliminó. No declarar aislamiento
perfecto histórico ni convertir este incidente en un defecto de producto.

Sin cambios de producto, dependencias, claves, dispositivos, servicios remotos,
commit, push o publicación en esta recepción. Sólo registro de decisión en STATUS
y esta nota; no se considera realizada la corrección por haberla recomendado.
