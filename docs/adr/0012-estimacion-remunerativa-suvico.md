# ADR 0012: estimación remunerativa SUVICO

- Estado: aceptada por MAIN
- Fecha: 2026-08-16
- Autoridad: decisión explícita de Joa e integración de MAIN

## Contexto

MiGuardia ya calcula horas trabajadas, pendientes, nocturnas, feriadas y excedentes sobre 204 horas. El repositorio conserva seis imágenes SUVICO para la categoría Vigilador, vigentes de julio a diciembre de 2026, con componentes mensuales, valores unitarios y una tabla de antigüedad. Esas imágenes no resuelven deducciones personales, pérdida de presentismo, prorrateos, vacaciones ni la asignación concreta de cada hora extra al 50 % o al 100 %.

## Decisión

- La estimación aparece al final de Resumen y se etiqueta como bruta, orientativa y no equivalente a un recibo de sueldo.
- Las escalas se modelan como datos puros versionados por `YearMonth`, con cada archivo fuente identificado. No se extrapolan meses sin fuente.
- La antigüedad se ingresa en años enteros de 0 a 60 y se persiste en un DataStore exclusivo. La tabla aplica 0 % para cero años, los porcentajes publicados de 1 a 20 y un punto adicional por año después de 20.
- La base remunerativa es básico + antigüedad + presentismo. El valor horario usa divisor 200. La nocturnidad usa `(básico + antigüedad) × 0,1 %` por hora y el feriado usa el valor horario por dos por cada hora intersectada.
- La proyección usa guardias `PLANNED` elegibles ya trabajadas o pendientes. Vacaciones, carpeta médica, ausencia y cancelación no incorporan horas proyectadas al dinero.
- El excedente proyectado se calcula sobre 204 horas. Como la evidencia no permite asignar cada hora al 50 % o al 100 %, la interfaz presenta ambos extremos como rango.
- Se mantienen completos básico, presentismo, suma no remunerativa y viáticos. Esta es una hipótesis visible, no una regla de liquidación para meses parciales.
- No se calcula neto, descuentos, pérdida de presentismo, remuneración vacacional, prorrateos ni efectos monetarios de ausencias o carpetas.
- Este incremento no modifica Room, repositorios ni esquemas.

## Consecuencias

- El usuario obtiene una aproximación útil con supuestos auditables y puede ajustar su antigüedad sin exponer información fuera del teléfono.
- La estimación puede diferir del recibo real. La incertidumbre se muestra en lugar de ocultarse bajo un total falso.
- Incorporar meses nuevos, vacaciones pagas, neto o reglas de extras requerirá nuevas fuentes verificables y otra decisión documental.
