[CmdletBinding()]
param([switch]$Execute, [switch]$ResumeEmptyWorkingDirectory)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# A new Windows PowerShell console can inherit PowerShell 7 module paths.
# Load the host's own modules, not incompatible inherited PowerShell 7 modules.
foreach ($moduleName in @('Microsoft.PowerShell.Management', 'Microsoft.PowerShell.Utility', 'Microsoft.PowerShell.Security')) {
    Import-Module (Join-Path $PSHOME ("Modules\$moduleName\$moduleName.psd1")) -Force -ErrorAction Stop
}

function Assert-PlainLocalPath([string]$Path) {
    if ($Path -notmatch '^[A-Za-z]:\\' -or $Path -match '[*?\[\]]') {
        throw 'Se requiere una ruta local absoluta sin patrones.'
    }
    $full = [IO.Path]::GetFullPath($Path)
    $node = $full
    while ($node) {
        $item = Get-Item -LiteralPath $node -Force -ErrorAction SilentlyContinue
        if ($item -and (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw 'No se permiten enlaces ni junctions en la ruta.'
        }
        $parent = [IO.Path]::GetDirectoryName($node.TrimEnd('\'))
        if (-not $parent -or $parent -eq $node) { break }
        $node = $parent
    }
    return $full
}

function Assert-Absent([string]$Path) {
    $null = Assert-PlainLocalPath $Path
    if (Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue) {
        throw 'El destino ya existe. No se reemplaza ni se regenera material.'
    }
}

function Assert-EmptyDirectory([string]$Path) {
    $null = Assert-PlainLocalPath $Path
    $item = Get-Item -LiteralPath $Path -Force
    if (-not $item.PSIsContainer -or @(Get-ChildItem -LiteralPath $Path -Force).Count -ne 0) {
        throw 'La reanudacion de preparacion requiere una carpeta completamente vacia.'
    }
}

function New-PrivateDirectory([string]$Path, [switch]$ReuseEmpty) {
    if ($ReuseEmpty) { Assert-EmptyDirectory $Path } else { Assert-Absent $Path }
    $current = [Security.Principal.WindowsIdentity]::GetCurrent().User
    if ($current.Value -eq 'S-1-5-18') { throw 'No ejecutar como SYSTEM.' }
    $system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    if (-not $ReuseEmpty) { $null = New-Item -ItemType Directory -Path $Path }
    # Persist only the DACL. Never request owner, group or audit/SACL changes.
    $acl = [Security.AccessControl.DirectorySecurity]::new(
        $Path, [Security.AccessControl.AccessControlSections]::Access)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($existingRule in @($acl.GetAccessRules($true, $false,
        [Security.Principal.SecurityIdentifier]))) {
        $acl.RemoveAccessRuleSpecific($existingRule)
    }
    foreach ($sid in @($current, $system)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $sid, 'FullControl', 'ContainerInherit, ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    if ($PSVersionTable.PSEdition -eq 'Desktop') {
        [IO.Directory]::SetAccessControl($Path, $acl)
    } else {
        [IO.FileSystemAclExtensions]::SetAccessControl([IO.DirectoryInfo]::new($Path), $acl)
    }
    $actual = [Security.AccessControl.DirectorySecurity]::new(
        $Path, [Security.AccessControl.AccessControlSections]::Access)
    $rules = @($actual.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    if (-not $actual.AreAccessRulesProtected -or $rules.Count -ne 2) {
        throw 'No se pudo comprobar la privacidad NTFS del directorio.'
    }
    foreach ($rule in $rules) {
        if ($rule.IdentityReference.Value -notin @($current.Value, $system.Value) -or
            $rule.AccessControlType -ne 'Allow' -or $rule.IsInherited -or
            $rule.FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl -or
            $rule.InheritanceFlags -ne [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit') {
            throw 'Permisos NTFS inesperados.'
        }
    }
}

function Copy-NewFile([string]$Source, [string]$Destination) {
    $null = Assert-PlainLocalPath $Source
    Assert-Absent $Destination
    $sourceItem = Get-Item -LiteralPath $Source -Force
    if ($sourceItem.PSIsContainer -or $sourceItem.Length -eq 0) { throw 'Origen invalido.' }
    [IO.File]::Copy($Source, $Destination, $false)
    $left = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
    $right = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
    if ($left -ne $right) { throw 'La copia no coincide. Conservar ambos para revision.' }
    return $left
}

function Get-CustodyPlan([switch]$ResumeEmptyWorkingDirectory) {
    $plan = [ordered]@{
        work = Join-Path $env:LOCALAPPDATA 'BlackatSystems\MiGuardiaSigning'
        backup = 'D:\MiGuardia-Firma\release-key-01'
        keytool = Join-Path $env:LOCALAPPDATA 'Programs\android-studio\jbr\bin\keytool.exe'
    }
    foreach ($path in $plan.Values) { $null = Assert-PlainLocalPath $path }
    if ($ResumeEmptyWorkingDirectory) { Assert-EmptyDirectory $plan.work } else { Assert-Absent $plan.work }
    Assert-Absent $plan.backup
    if (-not (Test-Path -LiteralPath $plan.keytool -PathType Leaf)) { throw 'Falta keytool revisado.' }
    $volume = Get-Volume -DriveLetter D
    $backupDisk = Get-Partition -DriveLetter D | Get-Disk
    $workDisk = Get-Partition -DriveLetter ([IO.Path]::GetPathRoot($plan.work).Substring(0, 1)) | Get-Disk
    if ($volume.FileSystem -ne 'NTFS' -or $backupDisk.BusType -ne 'USB' -or
        $backupDisk.Number -eq $workDisk.Number -or $backupDisk.IsReadOnly -or $backupDisk.IsOffline) {
        throw 'La unidad USB independiente autorizada no esta disponible.'
    }
    if ((Get-ChildItem -LiteralPath 'D:\MiGuardia-Firma' -Force | Where-Object Name -ne 'LEEME.txt')) {
        throw 'El destino tiene contenido adicional; revisar antes de generar.'
    }
    foreach ($name in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')) {
        if ([Environment]::GetEnvironmentVariable($name)) {
            throw 'Hay opciones externas de Java. Revisarlas sin mostrar su contenido.'
        }
    }
    return $plan
}

function Invoke-KeytoolStep([string]$Tool, [string[]]$Options, [string]$ExpectedFile) {
    Assert-Absent $ExpectedFile
    # Native console owns the password. Never redirect, pipe, capture or log it.
    & $Tool @Options
    if ($LASTEXITCODE -ne 0) { throw 'keytool no completo el paso.' }
    $item = Get-Item -LiteralPath $ExpectedFile -Force
    if ($item.PSIsContainer -or $item.Length -lt 100) { throw 'El artefacto no es valido.' }
}

function Invoke-Custody([switch]$ResumeEmptyWorkingDirectory) {
    if (-not [Environment]::UserInteractive -or [Console]::IsInputRedirected -or [Console]::IsOutputRedirected) {
        throw 'Ejecutar solamente en una consola local visible, sin redireccion.'
    }
    $plan = Get-CustodyPlan -ResumeEmptyWorkingDirectory:$ResumeEmptyWorkingDirectory
    $Host.UI.RawUI.WindowTitle = 'MiGuardia - clave privada - contrasena SOLO en esta ventana'
    Write-Host 'MIGUARDIA: CREAR LA CLAVE PERMANENTE Y SU RESPALDO USB' -ForegroundColor Cyan
    Write-Host 'No es una clave de acceso beta. No firma ni publica la aplicacion.'
    Write-Host 'Usa una contrasena unica y larga: por ejemplo, 20 o mas caracteres aleatorios.'
    Write-Host 'Guardala en un gestor o lugar seguro SEPARADO de estas copias. No la envies al chat.'
    Write-Host 'keytool no mostrara letras ni asteriscos al escribir: es normal.'
    Write-Host 'No cierres esta ventana ni retires D: durante el procedimiento.'
    Write-Host 'Si cancelas o falla, los archivos se conservan; NO vuelvas a generar otra clave.'
    New-PrivateDirectory $plan.work -ReuseEmpty:$ResumeEmptyWorkingDirectory
    New-PrivateDirectory $plan.backup
    $statusPath = Join-Path $plan.work 'custody-status.json'
    $state = [ordered]@{ status = 'PREPARED'; updatedUtc = ''; apkSigned = $false }
    function Save-PublicState {
        $state.updatedUtc = [DateTime]::UtcNow.ToString('o')
        [IO.File]::WriteAllText($statusPath, ($state | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    Save-PublicState
    $completed = $false
    try {
        $workingKey = Join-Path $plan.work 'release-signing.p12'
        $backupKey = Join-Path $plan.backup 'release-signing.p12'
        $request = Join-Path $plan.backup 'recovery-check.csr'
        $certificate = Join-Path $plan.backup 'release-certificate.cer'
        Write-Host "`n1/3 CREA LA CONTRASENA Y REPETILA CUANDO KEYTOOL LO PIDA." -ForegroundColor Cyan
        $options = @(
            '-J-Dkeystore.pkcs12.keyProtectionAlgorithm=PBEWithHmacSHA256AndAES_256',
            '-J-Dkeystore.pkcs12.certProtectionAlgorithm=PBEWithHmacSHA256AndAES_256',
            '-J-Dkeystore.pkcs12.keyPbeIterationCount=600000',
            '-J-Dkeystore.pkcs12.certPbeIterationCount=600000',
            '-J-Dkeystore.pkcs12.macAlgorithm=HmacPBESHA256',
            '-J-Dkeystore.pkcs12.macIterationCount=600000',
            '-genkeypair', '-storetype', 'PKCS12', '-keyalg', 'RSA', '-keysize', '4096',
            '-sigalg', 'SHA256withRSA', '-validity', '10950',
            '-dname', 'CN=MiGuardia, O=Blackat Systems', '-keystore', $workingKey)
        Invoke-KeytoolStep $plan.keytool $options $workingKey
        $state.status = 'KEY_CREATED'
        Save-PublicState
        $state['encryptedKeystoreSha256'] = Copy-NewFile $workingKey $backupKey
        $state.status = 'BACKUP_COPIED'
        Save-PublicState
        Write-Host "`n2/3 INGRESA LA MISMA CONTRASENA PARA COMPROBAR LA COPIA USB." -ForegroundColor Cyan
        Invoke-KeytoolStep $plan.keytool @('-certreq', '-storetype', 'PKCS12',
            '-keystore', $backupKey, '-sigalg', 'SHA256withRSA', '-file', $request) $request
        $state.status = 'BACKUP_PRIVATE_KEY_USED'
        Save-PublicState
        Write-Host "`n3/3 REPETI LA CONTRASENA PARA EXPORTAR SOLO EL CERTIFICADO PUBLICO." -ForegroundColor Cyan
        Invoke-KeytoolStep $plan.keytool @('-exportcert', '-storetype', 'PKCS12',
            '-keystore', $backupKey, '-file', $certificate) $certificate
        $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate)
        try {
            $subjectParts = @($cert.Subject -split ',\s*')
            if ($subjectParts.Count -ne 2 -or 'CN=MiGuardia' -notin $subjectParts -or
                'O=Blackat Systems' -notin $subjectParts -or
                $cert.HasPrivateKey -or $cert.PublicKey.Key.KeySize -ne 4096 -or
                $cert.PublicKey.Oid.Value -ne '1.2.840.113549.1.1.1' -or
                $cert.SignatureAlgorithm.Value -ne '1.2.840.113549.1.1.11' -or
                $cert.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false) -ne 'MiGuardia' -or
                $cert.NotAfter -lt (Get-Date).AddYears(29) -or $cert.NotBefore -gt (Get-Date)) {
                throw 'El certificado no satisface el contrato.'
            }
            foreach ($path in @($workingKey, $backupKey)) {
                if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $state.encryptedKeystoreSha256) {
                    throw 'El almacen cambio durante la comprobacion.'
                }
            }
            $state['certificateSha256'] = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash
            $state['certificateNotAfterUtc'] = $cert.NotAfter.ToUniversalTime().ToString('o')
            $state['recoveryCheck'] = 'KEYTOOL_CERTREQ_FROM_USB_BACKUP_ON_THIS_PC'
            $state.status = 'COMPLETE'
            Save-PublicState
        } finally { $cert.Dispose() }
        $null = Copy-NewFile $statusPath (Join-Path $plan.backup 'custody-status.json')
        $completed = $true
        Write-Host "`nCLAVE Y COPIA USB COMPROBADAS. No se firmo ni publico ninguna APK." -ForegroundColor Green
        Write-Host 'Conserva D: en un lugar seguro y la contrasena en otro. No borres estas copias.'
    } catch {
        $state.status = 'INCOMPLETE_REVIEW_REQUIRED'
        Save-PublicState
        Write-Host 'No se completo la custodia. Los archivos quedan preservados.' -ForegroundColor Yellow
        Write-Host 'No vuelvas a ejecutar la generacion. Volve al chat para revisar el paso pendiente.'
    }
    if (-not $completed) { throw 'Custodia incompleta; requiere revision.' }
    $null = Read-Host 'Presiona Enter para cerrar esta ventana (no ingreses una contrasena aqui)'
}

if ($MyInvocation.InvocationName -ne '.') {
    if ($Execute) {
        try {
            # Direct statement: wrapping this call would capture native stdout.
            Invoke-Custody -ResumeEmptyWorkingDirectory:$ResumeEmptyWorkingDirectory
        } catch {
            Write-Host 'No se inicio o completo el procedimiento. Volve al chat; no se descarta ningun archivo.' -ForegroundColor Yellow
            $null = Read-Host 'Enter para cerrar'
            exit 1
        }
    } else {
        Get-CustodyPlan -ResumeEmptyWorkingDirectory:$ResumeEmptyWorkingDirectory | ConvertTo-Json
        Write-Host 'SOLO PLAN. No se crean directorios, claves ni copias sin -Execute y consola local.'
    }
}
