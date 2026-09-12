# Custodia de clave Release — MAIN

Fecha local: 2026-09-06. Cierre de generación: 2026-09-07T02:41:06Z.
Base de entrada: `d5746f49a92ebed5f353e64c2cd28b5b2758e320`.
Contrato: `docs/prompts/CUSTODIA_CLAVE_RELEASE_V2.md`.
Checkpoint local de herramientas y documentación: `7d29f4c642ca4a982c1b63c32fe48758fbdad50d`.
Los almacenes cifrados y sus respaldos no forman parte del commit.

## Resultado

**CLAVE PERMANENTE CREADA Y RESPALDO USB COMPROBADO.** No APK firmada,
instalación, envío, servicio remoto ni publicación.

Joaquin eligió D: y autorizó la generación con contraseña local. Confirmó el
mensaje final de la consola; MAIN contrastó los dos registros COMPLETE,
archivos, certificado, huellas y permisos. La contraseña no pasó por MAIN,
argumentos, entorno, archivos de texto o registros. No se acredita su fuerza
ni que Joaquin haya guardado la contraseña por separado.

## Material y comprobaciones

- Trabajo: `%LOCALAPPDATA%/BlackatSystems/MiGuardiaSigning/release-signing.p12`.
- Respaldo: `D:/MiGuardia-Firma/release-key-01/release-signing.p12`.
- D: USB NTFS, disco distinto del que contiene Windows. Sin enlaces/reparse.
- PKCS12 nativo generado por keytool/JBR 25: RSA 4096, SHA256withRSA,
  protección PBE AES-256/HMAC-SHA256 y MAC nativa, 600000 iteraciones indicadas.
- Cada almacén cifrado tiene 4281 bytes; SHA-256 iguales antes y después de
  abrir/usar la copia. Hashes y resultado completo conservados fuera de Git en
  `custody-status.json` junto a cada copia; ningún almacén se incorpora aquí.
- Directorios con DACL protegida, exactamente usuario actual y SYSTEM con
  FullControl e herencia a hijos; sin cambios de propietario/SACL/elevación.
- Sujeto público: `CN=MiGuardia, O=Blackat Systems`.
- Certificado válido desde `2026-09-07T02:40:52Z` hasta `2056-08-30T02:40:52Z`.
- SHA-256 del certificado público:
  `AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D`.
- Certificado exportado sin clave privada; distinto del certificado Debug/QA.
- keytool volvió a solicitar la contraseña y ejecutó `certreq` desde el
  respaldo USB, no desde el almacén de trabajo. El CSR no fue enviado.
- MAIN verificó además la firma de ese CSR con la API criptográfica nativa
  .NET y comparó su clave pública con el certificado: coincidencia. Un control
  alterando un bit de firma sólo en memoria fue rechazado. El CSR no cambió.
- keytool usa la etiqueta histórica `NEW CERTIFICATE REQUEST`; el primer
  lector .NET la rechazó. Se normalizaron sólo las etiquetas PEM en memoria
  al nombre admitido `CERTIFICATE REQUEST`, sin cambiar DER ni archivo. Se
  usó `CertificateRequestLoadOptions.Default`, nunca omisión de validación.

Esta evidencia acredita reapertura/uso del respaldo **en esta PC**. No prueba
restauración en otro equipo ni actualización de una instalación Android.

## Helper, fallos y revisión

Fuentes: `tools/release/create_signing_key.ps1`, `test_signing_guards.ps1` y
`SIGNING_CUSTODY.md`. Por defecto sólo plan; ejecución exige consola local
separada sin captura. Rutas nuevas, copia exclusiva y rechazo de parciales.

La primera ventana falló antes de crear una clave: Windows PowerShell 5 heredó
rutas de módulos PowerShell 7. La corrección carga módulos del propio PSHOME.
El módulo nativo también expuso que Set-Acl solicitaba SeSecurityPrivilege;
se reemplazó por persistencia exclusiva de Access/DACL, sin elevar privilegios.
Se conservó la carpeta de trabajo vacía y se retiró sólo el subdirectorio vacío
propio de diagnóstico. La reanudación explícita valida carpeta vacía incluso
de ocultos, sin enlaces y respaldo ausente; no es regeneración de una clave.

La revisión independiente detectó y cerró antes del uso: fallo con exit 0,
comprobación parcial del sujeto y captura indirecta de stdout al envolver la
función en una expresión. La llamada nativa final es directa. Una PTY de
herramientas no fue usada para introducir contraseñas.

Validación final: **22 guardas sintéticas aprobadas**, también recorridas en
consola hija Windows PowerShell 5.1. Nueva repetición tras la generación: 22/22.
Pruebas sin secretos, documentos y diff comprobados; no Gradle por ritual.
La revisión independiente final no encontró bloqueadores.

## Conservación y siguientes puertas

Sin cambios en Android, Room, Gradle, APK, website, datos laborales o teléfono.
Ningún secreto/keystore/APK preparado para Git. Clave APK separada de confianza
del servidor, pepper y accesos beta; todavía no se crearon esos secretos.

Falta atribución completa de clima, confianza pública, firma del APK definitivo,
QA instalada y publicación. No confundir este certificado con una APK ya
firmada ni la autorización general con push/tag/movimiento de main.

Fuentes primarias: [keytool 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html),
[firma Android](https://developer.android.com/studio/publish/app-signing),
[comprobación de CSR .NET](https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.x509certificates.certificaterequest.loadsigningrequestpem?view=net-10.0).
