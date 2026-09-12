# ADR 0038: ubicación puntual y clima por objetivo

- Estado: aceptada, implementada y verificada por MAIN
- Fecha: 2026-09-02
- Autoridad: instrucción explícita de Joaquin

## Contexto

El clima fijo de Córdoba puede ser incorrecto cuando una persona trabaja en
objetivos distintos. La dirección escrita puede estar vacía o ser imprecisa,
pero Joaquin decidió que, cuando exista, la persona debe poder usarla
conscientemente para buscar las coordenadas del objetivo. También debe ser
posible crear el objetivo sin dirección y usar la ubicación aproximada de la
ciudad actual como respaldo consciente para su clima.

## Decisión

- Cada objetivo conserva opcionalmente `weatherLatitude` y
  `weatherLongitude`. Ambas existen juntas o ambas son nulas y se validan como
  números finitos dentro de los rangos geográficos.
- Crear un objetivo no exige dirección, ubicación ni permiso. Si no escribió
  una dirección, puede elegir `Usar mi ciudad actual para el clima` desde
  `Mi forma de trabajar > Opciones avanzadas`. Sólo entonces MiGuardia solicita
  ubicación aproximada y guarda esas coordenadas para ese objetivo.
- Si existe una dirección, `Usar esta dirección para el clima` consulta el
  `Geocoder` de Android sólo después del toque. No integra Google Maps ni Places,
  no agrega una clave ni un mapa embebido. El servicio puede necesitar red,
  puede no estar disponible y no garantiza precisión; por eso se muestra la
  dirección encontrada y la persona confirma antes de guardar.
- El permiso aproximado `ACCESS_COARSE_LOCATION` se solicita únicamente al
  tocar la acción de usar la ciudad actual. No hay permiso preciso, ubicación en segundo plano,
  seguimiento, servicio, historial de posiciones ni captura automática. La
  captura usa sólo el proveedor de red compatible con ese permiso también en
  Android 10 y versiones anteriores; no intenta caer silenciosamente a GPS,
  que allí exigiría ubicación precisa.
- Denegar el permiso, apagar la ubicación o no obtener una posición no bloquea
  la creación ni edición del objetivo. Tras un rechazo, la interfaz mantiene
  las alternativas sin ubicación y ofrece abrir los permisos de MiGuardia en
  Ajustes para recuperar incluso una denegación permanente.
- La dirección se entrega al servicio de geocodificación de Android únicamente
  al tocar su acción. Open-Meteo no recibe esa dirección, el nombre, la
  abreviatura, la nota, las jornadas ni los horarios: recibe las coordenadas del
  objetivo consultado y puede observar la IP normal de la conexión.
- El runtime resuelve el objetivo de cada guardia, usa su zona horaria y separa
  los pronósticos guardados por una huella de ID, nombre, coordenadas y zona. Un
  resultado anterior no puede aplicarse a otra jornada ni a una ubicación que
  cambió. Borrar un objetivo cancela sólo su descarga antes de limpiar; las
  solicitudes nuevas del widget se encolan y no se pierden si otra descarga ya
  estaba activa.
- `Quitar ubicación guardada` exige confirmación, vuelve ambas coordenadas a
  nulas y elimina los pronósticos locales de todas las versiones de ese
  objetivo. Las copias de seguridad ya exportadas son archivos independientes y
  no se reescriben.
- Room exclusivo V2 pasa de 5 a 6 con dos columnas `REAL` nulas. Las copias
  nuevas declaran Room V6. Al leer una copia lógica V5 válida, MiGuardia agrega
  las dos columnas con valor nulo antes de compararla o restaurarla; no inventa
  una ubicación.

La captura usa la API de ubicación actual de Android y respeta el modelo de
permiso aproximado documentado por Android:

- <https://developer.android.com/reference/android/location/LocationManager>
- <https://developer.android.com/develop/sensors-and-location/location/permissions>
- <https://developer.android.com/develop/sensors-and-location/location/retrieve-current>
- <https://developer.android.com/reference/android/location/Geocoder>

## Alternativas descartadas

### Google Maps o Places

Se descartan porque sumarían una integración, clave y condiciones comerciales
innecesarias para este alcance. El `Geocoder` del sistema cubre la búsqueda
puntual, pero se trata como mejor esfuerzo y nunca como coordenada exacta.

### Seguir al trabajador en segundo plano

Se descarta porque no aporta al clima de un objetivo ya guardado, aumenta el
riesgo de privacidad y exige permisos y comportamiento permanente.

### Mantener Córdoba como respaldo silencioso

Se descarta porque mostraría un pronóstico de otro lugar como si perteneciera
al objetivo. Sin ubicación se informa el estado y el resto de la aplicación
continúa funcionando.

## Consecuencias

- Un objetivo sin coordenadas no muestra ni descarga clima, pero conserva todas
  las demás funciones.
- Cambiar la ubicación invalida lógicamente el caché anterior; no modifica
  jornadas ni sus instantáneas históricas.
- Buscar por dirección no pide el permiso de ubicación. Usar la ciudad actual
  sí pide sólo ubicación aproximada y únicamente en contexto.
- El pronóstico sigue siendo remoto, opcional y reemplazable. Calendario,
  notificaciones y widget no dependen de que la red o la ubicación funcionen.
- La captura y el permiso requieren QA en dispositivo físico; la compilación o
  una prueba sin teléfono no demuestran el diálogo real ni la precisión
  entregada por Android.
