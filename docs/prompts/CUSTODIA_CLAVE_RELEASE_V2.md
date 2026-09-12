# Crear y comprobar el resguardo de la clave Release

## Qué hace y por qué existe

Crea una única clave permanente para las futuras APK de MiGuardia y comprueba
su copia cifrada fuera del disco de Windows. No firma ni distribuye APK.
Joaquin eligió `D:\` y autorizó continuar con la creación mediante ingreso de
contraseña local, fuera del chat.

## Alcance y entradas

Base `d5746f49a92ebed5f353e64c2cd28b5b2758e320`; el único cambio de entrada es
STATUS registrando la elección de destino. Preparación unsigned 29 ya verificada.
Consultar ADR 0039, planificación §22 y `tools/release/README.md`.

Recursos: JBR instalado, directorio de trabajo nuevo bajo
`%LOCALAPPDATA%/BlackatSystems/MiGuardiaSigning`, y directorio nuevo
`D:\MiGuardia-Firma\release-key-01`. La carpeta padre USB preparada sólo contiene
una nota no sensible. Verificar de nuevo unidad, archivos y enlaces antes de usar.

## Límites

- Generación mediante `keytool` oficial, PKCS12, RSA 4096, SHA256withRSA y
  10950 días. Sujeto público de producto: CN=MiGuardia, O=Blackat Systems;
  sin nombre legal, dirección, correo o ubicación personal.
- Cifrado PKCS12 nativo AES-256/PBKDF2-HMAC-SHA256 y MAC nativo; no criptografía
  propia. Contraseña sólo en la consola nativa sin eco. No argumento, variable
  de entorno, archivo de contraseña, transcripción, pipe o captura de entrada.
- Recomendar contraseña larga/única y custodia separada. No afirmar fuerza de
  contraseña verificada: el helper no recibe ni inspecciona ese secreto.
- Directorios nuevos con permisos NTFS usuario actual/SYSTEM antes de crear
  material. No modificar permisos de ancestros ni archivos ajenos.
- No reemplazar, importar, regenerar o eliminar material existente. Un estado
  parcial necesita revisión antes de continuar; no repetir generación. Si falló
  sólo preparar permisos antes de crear archivos, MAIN puede reanudar esa
  preparación únicamente con carpeta de trabajo completamente vacía, carpeta
  de copia ausente y comprobación de ACL. Un archivo, incluso oculto, lo impide.
- Separar clave APK, claves del servidor, pepper y códigos beta. No consultar
  servidor, Google Drive, dispositivos ni datos laborales.
- No editar Android, Gradle, Room o website. No firmar APK, publicar, hacer push,
  tag ni mover referencias protegidas.

## Aceptación y comprobación

- Revisar helper y probar sus guardas con archivos sintéticos sin secretos.
- Ejecutar sólo en ventana local interactiva; no usar una terminal capturada
  por herramientas para ingresar contraseña.
- Generar una única clave. Copiar sin sobrescritura el PKCS12 cifrado a USB y
  comparar SHA-256 antes y después de las comprobaciones.
- Solicitar nuevamente contraseña y usar la clave de la copia para generar un
  CSR local con `keytool`; no enviarlo. Exportar certificado público y verificar
  RSA/tamaño, sujeto, vigencia y SHA-256 del certificado.
- Estado público sólo con fases, huellas y resultado, sin alias ni contraseñas.
  Un fallo conserva archivos y no se presenta como respaldo recuperable.
- La prueba acredita reapertura y operación privada desde USB en esta PC;
  no se atribuye a otro equipo ni a una actualización instalada.
- `python tools/docs_check.py`, pruebas del helper y diff; no Gradle por ritual.

## Entrega

Registrar estados reales por separado: helper preparado, ventana abierta,
clave generada, copia comprobada y pendientes. Atribución de clima, confianza
pública, firma, QA instalada y publicación conservan sus propias puertas.
