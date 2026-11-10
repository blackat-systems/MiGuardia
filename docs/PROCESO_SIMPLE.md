# Proceso simple (VIGENTE desde 7/11/2026)

Estado: **aprobado por Joaquín el 7/11/2026**. Reemplaza a `docs/WORKFLOW.md` en todo
lo que lo contradiga; WORKFLOW queda como referencia técnica.

## Por qué

El circuito heredado de Codex (coordinador y ejecutor en chats separados, recibos,
sellos, admisiones, intentos numerados) servía para coordinar varios chats sin memoria
compartida. Llegó a necesitar 14 intentos para una sola prueba y documentos que
sólo entiende la máquina. Con Claude, una sola sesión puede programar, probar y
revisar, y lanzar ayudantes cuando conviene.

## Cómo trabajamos

1. **Una tarea por vez, con nombre en castellano.** Por ejemplo «Probar
   Disponibilidad en el Samsung», no «B1-residual-r3».
2. **Cada tarea dice tres cosas:** qué se quiere, cómo se prueba y cuándo está lista.
3. **Claude programa, prueba y revisa** en esta sesión. Si una parte es grande e
   independiente, usa un ayudante (subagente) y revisa su trabajo antes de aceptarlo.
   Sin recibos, sellos ni comprobantes de entrega entre chats.
4. **Pruebas en dos niveles:**
   - En la PC: `python tools/verify.py --profile local` (y `build` si cambia la app).
   - En el Samsung: una lista de pasos claros. Vos autorizás cada sesión con el teléfono;
     los pasos que requieren tu dedo se te piden uno por uno, en castellano.
5. **Si algo falla dos veces igual, se para y se piensa** (o se te pregunta) antes de
   un tercer intento. Nada de intentos 13 y 14 a ciegas.
6. **Registro mínimo:** `docs/STATUS.md` en lenguaje llano (dónde estamos, qué falta)
   y una lista de verificación por etapa con «✅ probado / ⏳ falta / ❌ falla». Los
   detalles técnicos van en el commit o en el código, no en JSON de evidencia.
7. **Commits locales** al terminar cada tarea probada. Push, firma y publicación
   siempre con tu pedido explícito.

## Lo que no cambia

- Reglas del producto y de privacidad de `CLAUDE.md`.
- Nada en el teléfono sin tu autorización; no se borran datos del teléfono.
- No perder datos: las migraciones y Copias se siguen probando en serio.
- Los 441 casos del catálogo siguen siendo la lista de qué probar; se agrupan por
  pantalla para probarlos en tandas, no uno por uno con su propio circuito.

## Decisiones pendientes

1. ~~¿Aprobás este proceso?~~ Aprobado el 7/11/2026.
2. ~~Pasadas completas finales~~ **Decidido el 7/11/2026:** una sola pasada completa
   de los 441 casos sobre la versión final, en lugar de las dos del plan viejo. Si
   después hay un arreglo, se repite lo afectado más una **prueba corta** de lo
   principal (cargar, editar y ver jornadas; horas y Resumen; Copias; avisos y Widget)
   sobre el APK corregido. Esa prueba corta no reemplaza la pasada completa si el
   arreglo toca muchas áreas.
3. ~~Evidencia vieja~~ **Decidido el 7/11/2026:** `.local-evidence/` se copió de vuelta
   a esta carpeta desde el respaldo de D: y se verificó idéntica.
