# Custodia local, no firma del APK

Contrato: `docs/prompts/CUSTODIA_CLAVE_RELEASE_V2.md`. Consultar STATUS antes de
ejecutar. Este helper no necesita Gradle, ADB, servidor ni acceso a cuentas.

`create_signing_key.ps1` sin `-Execute` muestra un plan y comprueba entradas sin
escribir. Con `-Execute` requiere una consola Windows **separada y visible**,
sin transcripción, redirección o captura de terminal. No ejecutarlo desde una
PTY de herramientas: que la consola sea interactiva no prueba que no se grabe.
MAIN no recibe la contraseña; la pide directamente el `keytool` del JBR local.

Secuencia: directorios NTFS nuevos usuario/SYSTEM; una clave PKCS12 RSA 4096,
10950 días, sujeto público CN=MiGuardia/O=Blackat Systems; copia exclusiva en
USB; SHA-256; reapertura y CSR con privada desde USB; certificado público y
huella. El PBE nativo usa AES-256/HMAC-SHA256 con 600000 iteraciones. No se usa
ZIP adicional, contraseña en entorno/argumentos ni algoritmo criptográfico propio.
El alias por defecto de keytool no se registra ni transporta como parámetro.

Elegir una contraseña larga y única y conservarla aparte es responsabilidad
de Joaquin. El helper no mide ni acredita su fuerza. Las credenciales nunca
deben aparecer en Git, notas, conversación o `custody-status.json`.

Rutas exclusivas de esta operación:

- Trabajo: `%LOCALAPPDATA%/BlackatSystems/MiGuardiaSigning`.
- Copia: `D:/MiGuardia-Firma/release-key-01`.
- Nombre del almacén cifrado en ambos: `release-signing.p12`.

El archivo privado `.p12` no debe imprimirse ni adjuntarse. Se puede comprobar
su hash y tamaño. `release-certificate.cer` y `recovery-check.csr` son públicos;
el CSR no se envía ni equivale a un APK firmado. El JSON de estado sólo informa
fases, huellas y resultado. `COMPLETE` acredita reapertura desde USB **en esta
PC**, no otro equipo ni actualización de Android. Las ACL no sustituyen el
cifrado; al recuperar en otro Windows puede requerirse reasignar propiedad de
la copia NTFS y luego conocer la contraseña.

Fallo/cancelación: conservar archivos y volver a MAIN. Si existe cualquier
directorio parcial, el helper se niega a empezar de nuevo. No borrar, regenerar
ni importar otra identidad para superar un fallo. MAIN identificará el paso
pendiente sin leer secretos. Única reanudación automatizable: si la creación de
permisos falló **antes de cualquier archivo**, MAIN puede usar
`-ResumeEmptyWorkingDirectory` después de comprobar que la carpeta de trabajo
está completamente vacía y la carpeta de copia aún no existe. Se revalidan rutas
y ACL; cualquier archivo, incluso oculto, impide esa opción. No es una reanudación
de generación ni de una clave parcial. No modificar las copias después de distribución.

El helper importa explícitamente Management, Utility y Security de su propio `$PSHOME`:
un proceso Windows PowerShell 5 abierto desde PowerShell 7 puede heredar rutas
de módulos incompatibles. Se probó también en una consola hija, no sólo mediante
una invocación nativa desde la shell de MAIN. Los permisos se persisten sólo
como DACL (sección Access), sin solicitar cambios de propietario o auditoría ni
elevación administrativa.

Pruebas sin claves/contraseñas:

```powershell
powershell.exe -NoProfile -File tools/release/test_signing_guards.ps1
powershell.exe -NoProfile -File tools/release/create_signing_key.ps1
python tools/docs_check.py
```

La primera usa sólo archivos sintéticos y prueba rutas, enlaces, ACL, copia
exclusiva, errores y ausencia de redirecciones. La segunda no inicia generación.
El lanzamiento real pertenece a MAIN autorizado y no se automatiza en pruebas.

Fuentes: [keytool 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html),
[firma Android](https://developer.android.com/studio/publish/app-signing),
propiedades PKCS12 del `conf/security/java.security` del JBR instalado.
