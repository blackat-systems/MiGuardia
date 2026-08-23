# ADR 0024: continuidad de código sin migración de datos V1

- Estado: aceptada
- Fecha: 2026-08-23

## Contexto

MiGuardia 1.0 fue una prueba interna de Joaquin. No existe una población de
usuarios con instalaciones o datos V1 que MiGuardia 2.0 deba adoptar. Al mismo
tiempo, el código de 1.0 es la base técnica real de 2.0 y contiene funciones
que deben conservarse, adaptarse o reutilizarse.

Las fuentes anteriores mezclaban esas dos continuidades. De la continuidad del
código inferían una obligación de migrar bases Room, preferencias y estados de
una instalación V1. Esa inferencia ya no representa la decisión del producto.

El árbol actual todavía contiene la cadena Room V1 a V7, el origen
`MIGRATED_V1`, un motor heredado y recorridos de interfaz para una raíz V1. Son
hechos de la implementación presente, no requisitos del producto terminado.

## Decisión

1. El tag `v1.0.0`, `main` y su código continúan intactos como historia y base
   técnica de MiGuardia 2.0.
2. MiGuardia 2.0 reutiliza el código y las funciones útiles de 1.0; no implica
   reescribir la aplicación desde cero.
3. MiGuardia 2.0 no soportará la adopción, activación o migración de datos desde
   una instalación V1. Esto comprende Room, DataStore, preferencias, archivos,
   permisos, alarmas y cualquier otro estado local de aquella prueba.
4. La primera apertura de V2 parte limpia y comienza por la elección de uno de
   los cuatro sectores vigentes: Vigilancia privada, Policía, Enfermería o
   Medicina.
5. El producto final no necesita `MIGRATED_V1`, un modo V1 visible ni un motor
   heredado seleccionado por el origen de la raíz.
6. El `applicationId` actual se conserva por ahora. Esta decisión no constituye
   una promesa de compatibilidad ni de actualización de datos V1; cambiarlo
   requeriría una decisión separada.
7. No se borrarán silenciosamente datos ni paquetes en tiempo de ejecución. La
   limpieza de una instalación de prueba o del dispositivo requiere una acción
   explícita y separada.
8. La cadena Room y las rutas heredadas actuales se retirarán únicamente en un
   bloque de implementación dedicado. Ese bloque debe ejecutarse antes de
   ampliar nuevamente el esquema o cerrar el candidato final, pero no bloquea
   funciones que reutilicen Room v7 sin cambiarlo.
9. Una vez establecida la primera base pública de V2, sus migraciones futuras
   sí deberán preservar los datos creados dentro de V2.
10. La selección inicial conserva un solo rubro. Un eventual cambio posterior
    de profesión no forma parte de la secuencia actual y requerirá una decisión
    de producto separada si aparece un caso real.

## Consecuencias

- queda cancelado el bloque llamado “activar MiGuardia 2.0 desde una
  instalación anterior”;
- la edición y eliminación individual puede avanzar sobre Room v7 sin
  reconstruir la base ni perder código útil;
- el retiro del modo V1 continúa pendiente antes de una futura ampliación del
  esquema o del candidato final, pero no es la próxima pantalla funcional;
- las pruebas de migración V1 a V2 dejan de ser criterios de aceptación del
  producto final;
- el estado actual no se modifica de manera oportunista: el retiro requiere su
  propio prompt, auditoría, pruebas y checkpoint;
- este ADR no autoriza cambios de código, Room, DataStore, `applicationId`,
  versión, teléfono, producción, push, tag ni Release.

Esta decisión reemplaza únicamente las obligaciones de compatibilidad de datos
V1 expresadas en los ADR 0017, 0019, 0020, 0021 y 0022. Sus decisiones sobre
catálogo laboral, vigencia, lugares, tipos, plantillas y fotografías históricas
de jornadas V2 continúan vigentes.

## Alternativas descartadas

### Mantener una migración completa desde V1

Agrega complejidad, estados y pruebas para usuarios y datos que no existen.

### Reescribir toda la aplicación

Confunde ausencia de datos heredados con ausencia de una base técnica. Perdería
código probado y aumentaría innecesariamente el riesgo.

### Borrar datos heredados silenciosamente al arrancar

Una limpieza oculta dificulta las pruebas, puede afectar instalaciones locales
de Joaquin y mezcla una decisión de producto con una acción destructiva.

## Verificación requerida para el bloque futuro

- una instalación limpia abre el selector de cuatro sectores;
- no existen rutas visibles de activación, adopción o modo V1;
- Room y DataStore parten de un contrato V2 explícito;
- el código útil heredado sigue funcionando dentro de V2;
- la configuración inicial y la carga manual V2 mantienen sus pruebas;
- no se toca producción y cualquier limpieza de QA se informa expresamente.
