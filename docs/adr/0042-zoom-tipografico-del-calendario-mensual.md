# ADR 0042: zoom tipográfico del calendario mensual

- Vigencia: PARCIAL
- Sucesores: `docs/adr/0043-letra-nativa-android-sin-zoom-interno.md`
- Alcance vigente: Mes completo, objetivos de 48 dp y prioridad del contenido esencial conservados; zoom interno y densidad propia sustituidos por ADR 0043. Estado y evidencia en STATUS.
- Fecha: 2026-10-03
- Autoridad: Joaquín, respuesta «autorizo» a la propuesta concreta de MAIN.

## Contexto

El contrato 3.0 exige el mes completo visible sin desplazamiento principal. La
implementación conserva densidad geométrica escalada y un ancho mínimo heredado.
La QA produjo 32/48 combinaciones fallidas. En una ventana de 1440 px de ancho,
siete objetivos de 48 dp a zoom 200 requieren 1890 px aun sin márgenes.
La corrección necesita cambiar la política, no sólo reducir relleno.

## Decisión

La superficie mensual usa densidad geométrica base estable y zoom tipográfico
100/150/200. Casillas, encabezados y acciones contextuales se adaptan al espacio
disponible con objetivos táctiles de al menos 48 dp base, sin solapamiento. Los
siete días y todas las filas del mes se muestran simultáneamente. No se conserva
el ancho mínimo que requiere desplazamiento horizontal, ni semana forzada.

En ventanas bajas o con texto grande se reduce información secundaria en la
grilla, conservando número, condiciones identificables, selección y acceso al
detalle. La información completa permanece en detalle y descripción accesible.
El aumento tipográfico no se neutraliza con reducción automática de fechas.

Detalle, formularios, menú y demás destinos mantienen su escalado previo. La
descripción de Apariencia informa la excepción mensual. No se consultan ni
modifican tamaño de pantalla o fuente globales de Android.

Esta decisión sustituye únicamente la ampliación geométrica y desplazamiento
del Calendario en ADR 0003. Conserva sus contratos de consulta y proyección.

## Validación y límites

La matriz declara densidad base y escala tipográfica por separado; comprueba
objetivos reales contenidos, sin solapamiento, texto visible y siete columnas.
Se repiten orientación, temas, meses de cuatro/cinco/seis filas y zoom. La nueva
unidad de medida está autorizada; no permite rebajar mínimos o aprobar recortes.
La legibilidad humana y TalkBack siguen siendo verificaciones separadas.

Estado, resultados y autorización de recursos viven en docs/STATUS.md; la
decisión no implica aceptación de la candidata ni autorización de distribución.
