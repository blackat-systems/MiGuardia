# Auditoría de preparación — Próximo evento y notificaciones V2

Fecha: 2026-08-27

Responsable: MAIN 2.0

Resultado: **PROMPT HABILITADO — TAREA PENDIENTE DE APERTURA TRAS EL
CHECKPOINT DOCUMENTAL**

## 1. Objetivo

Preparar un contrato durable y verificable para adaptar próximo evento y
notificaciones después del cierre y publicación del Resumen personalizable.

Esta entrega es exclusivamente documental. No modifica el motor ni programa,
publica o prueba avisos.

## 2. Puerta 0 verificada

- Ruta: `C:\Users\Joaquin\Desktop\chatgptprojects\MiGuardia-2.0`.
- Rama: `codex/miguardia-2.0`.
- HEAD y upstream al comenzar:
  `ad777bbe7b57bf0fbc4903ef2c2949b31b5357ce`.
- Base protegida `v1.0.0^{}`:
  `82db6fd8eb2c511205968894dc9857a96b16ed20`.
- Checkout inicial: limpio.
- Remoto: `https://github.com/blackat-systems/MiGuardia.git`.
- Autor efectivo: `joaquin <blackat.systems@gmail.com>`.
- Worktrees históricos: observados y preservados sin cambios.
- Push del Resumen: ejecutado y verificado en `ad777bb`; autorización
  consumida.
- Push del próximo checkpoint documental: no autorizado.

## 3. Fuentes contrastadas

MAIN leyó la jerarquía documental activa, las cuatro fichas sectoriales, la
planificación, el mapa, estado e índice; los ADR aplicables; los contratos
históricos de próximo evento y Pulso Vigilia; la auditoría de visibilidad; y el
código y pruebas actuales de tarjeta, observación, avisos, jornadas,
fotografías, horario real, protecciones y disponibilidad.

También se contrastó el comportamiento de Android con documentación oficial
vigente sobre:

- permiso runtime de notificaciones desde Android 13;
- acceso especial y fallback de alarmas exactas;
- cambio de concesión inicial desde Android 14;
- inmutabilidad de los canales una vez creados.

## 4. Estado real encontrado

### Próximo evento

- El motor puro conserva `[inicio, fin)`, prioridad y orden estable.
- La tarjeta final ya representa todas las jornadas de hoy, estados,
  nocturna activa, horario real y protecciones.
- El observador ya consume vacaciones, carpetas médicas y horario real.
- Todavía no consume disponibilidad.
- La proyección recibe `Shift` aislado y no toda la fotografía laboral V2, por
  lo que no posee el tipo histórico como dato propio.
- Un texto de franco todavía puede consultar una zona global distinta de la
  zona inyectada.
- Un DTO que se describe como seguro todavía arrastra el objeto `Shift`
  completo.

### Notificaciones

- Plan, runtime, reconciliador y receptor continúan centrados en jornadas,
  vacaciones, excepciones y preferencias.
- Carpeta médica y horario real existen en el dominio de elegibilidad, pero
  pueden omitirse por parámetros opcionales y no llegan a toda la cadena.
- Disponibilidad no posee plan, identidad, presentación ni revalidación.
- Las recurrencias ya quedan cubiertas mediante jornadas concretas.
- Las excepciones particulares por jornada existen, pero su pantalla quedó sin
  acceso desde el detalle V2.
- DataStore rastrea alarmas, avisos visibles y ocultados con identidades de
  jornada; generalizarlo exige compatibilidad para no revivir ni dejar
  alarmas huérfanas.
- Pulso Vigilia, canales, alarma exacta con fallback, receivers, privacidad,
  cronómetro, agrupación, ocultamiento y restauración son infraestructura
  reutilizable.
- Persisten textos históricos como `Guardia` u `Objetivo` que no funcionan
  como vocabulario general de cuatro rubros.

## 5. Decisiones fijadas

El nuevo contrato establece:

- una única proyección V2 para tarjeta y avisos;
- jornadas leídas como `Shift + ShiftWorkSnapshot`;
- recurrencias consumidas sólo por sus jornadas materializadas;
- extras independientes excluidos como futuro porque ya son trabajo realizado;
- disponibilidad derivada mediante sus tramos efectivos existentes;
- jornada activa antes que disponibilidad activa;
- próximo inicio exacto con desempate determinista;
- franco explícito sólo como fallback visual, nunca como aviso;
- protecciones, cancelación, ausencia y horario real como invalidadores de una
  frontera planificada obsoleta;
- preferencias globales para jornada y disponibilidad;
- excepciones particulares sólo para jornadas;
- tracking tipado con interpretación compatible de UUID históricos;
- `Jornada` como vocabulario común y etiqueta histórica exacta para
  disponibilidad;
- conservación de privacidad, canales, ritmos, cronómetro, ocultar/restaurar y
  fallback inexacto;
- Room, permisos, manifiesto, dependencias y arquitectura Android intactos.

`Informar novedad` no vuelve: pertenecía al flujo V1 retirado. La expresión
`extras programadas` se resuelve sin nueva entidad: una jornada concreta puede
tener un tipo laboral que la persona considere extra; los extras independientes
actuales no generan futuro.

No apareció una contradicción material que requiera otra decisión de producto
antes de habilitar la tarea.

## 6. Revisiones independientes

Tres revisiones de sólo lectura contrastaron por separado:

- dominio, tarjeta, observación y disponibilidad;
- infraestructura Android, permisos, plan, receiver, presentación y tracking;
- coherencia del contrato conjunto y sus límites.

Coincidieron en:

- extender la infraestructura existente en vez de reemplazarla;
- usar una sola proyección tipada;
- no modificar Room ni agregar permisos;
- no inventar un extra futuro;
- recuperar el acceso a la excepción particular de una jornada;
- probar compatibilidad de identidades instaladas y ocultadas;
- ejecutar API 26 y un Android con permiso runtime además del Samsung API 36;
- mantener un reinicio físico y una alarma exacta real como autorizaciones
  inmediatas separadas.

No editaron archivos ni ejecutaron Gradle o ADB.

## 7. Persistencia preservada

- Base: `miguardia-v2.db`.
- Room: versión 5.
- Tablas: 27.
- Identity hash: `77adbc875d0f4ee466cdbd0dd74d5c5c`.
- Esquemas 1–5: sin cambios en esta preparación.
- Código, pruebas, Gradle y manifiestos: sin cambios.

SHA-256 vigentes:

```text
1.json 5769C0F57667F7FA5A7C1C1DA5474474537094A759F8FA4A0D66E6EF37C1287E
2.json E5A79603A6DD79532EF9F4A8F9FF241A6588424513107837AEE707186C046C50
3.json 39B7C4AEB0C2098ACBE9FE9FFC7FB308C4AA30AA04F30A3A69B770A5CDDA9428
4.json 796F1E7A02E095B956160B4135303DF3BAE49B1644D0D9AC6D226878D5B1CC6B
5.json 40B43C38D5FBCAB0DCC871A0996749FFAAE577B6AA67740E97AA10D005A8ACC4
```

## 8. Verificación proporcional prevista

Para este checkpoint documental corresponde:

- revisar referencias Markdown y consistencia de estados;
- ejecutar `git diff --check`;
- confirmar un diff exclusivamente documental;
- comprobar que no ingresen secretos ni artefactos generados;
- no ejecutar Gradle porque no cambia código;
- no usar ADB, Samsung ni emulador;
- no hacer otro push.

La autorización anterior para usar el Samsung perteneció al cierre del
Resumen y quedó consumida con esa validación. La dependencia debe recibir una
autorización nueva antes de tocar dispositivos.

## 9. Próximo paso

1. Crear el checkpoint documental local.
2. Comprobar checkout limpio.
3. Entregar a Joaquin el prompt listo para copiar.
4. Abrir una única tarea especializada sólo cuando Joaquin lo indique.
5. Recibir el candidato sin commit.
6. Auditar cada hunk desde MAIN.
7. Ejecutar pruebas locales y pedir autorización separada para dispositivos.
8. Integrar únicamente si todo queda verde.

Mensaje recomendado para este checkpoint:

```text
docs: define V2 next event and notifications
```
