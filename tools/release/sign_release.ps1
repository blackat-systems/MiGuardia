[CmdletBinding()]
param(
    [string]$UnsignedApk,
    [string]$ApprovedSha256,
    [string]$UnsignedReport,
    [string]$Policy,
    [string]$OutputDirectory,
    [switch]$Execute,
    [switch]$PrivateConsoleConfirmed,
    [switch]$NoRecordingConfirmed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Dot-source only definitions; the custody generator's entry point is not called.
$signingExecute = $Execute
. (Join-Path $PSScriptRoot 'create_signing_key.ps1')
$script:ReleaseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:ReleaseCertificate = 'AB6A80E649D10A63D4524278AAAE62588C29B5FF459A91063DEC62B534C69B0D'
$script:SigningPhase = 'PREFLIGHT'

function Assert-ReleasePath([string]$Path) {
    $full = Assert-PlainLocalPath $Path
    if ($full.Substring(2).Contains(':') -or $Path.Contains('..') -or
        $Path -match '[<>|"\x00-\x1f]') { throw 'Ruta ambigua o flujo alternativo rechazado.' }
    return $full
}

function Assert-WithinReleaseRoot([string]$Path, [string]$Root) {
    $full = Assert-ReleasePath $Path
    $base = (Assert-ReleasePath $Root).TrimEnd('\') + '\'
    if (-not $full.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La ruta no pertenece al directorio permitido.'
    }
    return $full
}

function Get-ReleaseFileHash([string]$Path) {
    $full = Assert-ReleasePath $Path
    $item = Get-Item -LiteralPath $full -Force
    if ($item.PSIsContainer -or $item.Length -eq 0) { throw 'Archivo ausente o vacio.' }
    return (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash
}

function Read-ReleaseJson([string]$Path) {
    $null = Get-ReleaseFileHash $Path
    if ((Get-Item -LiteralPath $Path).Length -gt 8388608) { throw 'Informe demasiado grande.' }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}

function Assert-IgnoredReleasePath([string]$Path) {
    $full = Assert-WithinReleaseRoot $Path $script:ReleaseRoot
    $relative = $full.Substring($script:ReleaseRoot.Length + 1).Replace('\', '/')
    & git -C $script:ReleaseRoot check-ignore -q -- $relative
    if ($LASTEXITCODE -ne 0) { throw 'Artefactos e informes deben permanecer ignorados por Git.' }
}

function Assert-SafeJavaEnvironment {
    foreach ($name in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
        if ([Environment]::GetEnvironmentVariable($name)) {
            throw 'Opciones externas Java presentes; revisar sin imprimir sus valores.'
        }
    }
}

function Get-ReleaseLocalAppData {
    return [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
}

function Assert-PrivateCustodyAccess([string]$Directory, [string]$Key) {
    $current = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ($current -eq 'S-1-5-18') { throw 'No ejecutar firma como SYSTEM.' }
    foreach ($path in @($Directory, $Key)) {
        $acl = Get-Acl -LiteralPath $path
        if ($path -eq $Directory -and -not $acl.AreAccessRulesProtected) { throw 'Custodia sin ACL privada protegida.' }
        $rules = @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
        if ($rules.Count -ne 2) { throw 'Custodia con permisos inesperados.' }
        foreach ($rule in $rules) {
            if ($rule.IdentityReference.Value -notin @($current, 'S-1-5-18') -or
                $rule.AccessControlType -ne 'Allow' -or
                $rule.FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl) {
                throw 'La custodia no permanece exclusiva del usuario y SYSTEM.'
            }
        }
    }
}

function Get-ExistingReleaseCustody {
    $directory = Assert-ReleasePath (Join-Path (Get-ReleaseLocalAppData) 'BlackatSystems\MiGuardiaSigning')
    if ($directory.StartsWith($script:ReleaseRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La custodia privada no puede estar dentro del repositorio.'
    }
    $key = Assert-WithinReleaseRoot (Join-Path $directory 'release-signing.p12') $directory
    Assert-PrivateCustodyAccess $directory $key
    $status = Join-Path $directory 'custody-status.json'
    $record = Read-ReleaseJson $status
    if ($record.status -ne 'COMPLETE' -or
        $record.certificateSha256 -ne $script:ReleaseCertificate -or
        $record.recoveryCheck -ne 'KEYTOOL_CERTREQ_FROM_USB_BACKUP_ON_THIS_PC' -or
        $record.encryptedKeystoreSha256 -notmatch '^[a-fA-F0-9]{64}$') {
        throw 'Custodia incompleta, incompatible o sin respaldo comprobado.'
    }
    $hash = Get-ReleaseFileHash $key
    if ($hash -ne $record.encryptedKeystoreSha256) { throw 'El almacen difiere de la custodia completa.' }
    # This checks encrypted bytes, never loads PKCS12, asks for a password or enumerates aliases.
    return @{ key = $key; keyHash = $hash; status = $status; statusHash = Get-ReleaseFileHash $status }
}

function Get-ReleaseSigningPlan([string]$Apk, [string]$Approved, [string]$ReportPath,
    [string]$PolicyPath, [string]$Destination) {
    if ($Approved -notmatch '^[a-fA-F0-9]{64}$') { throw 'Falta SHA-256 aprobado exacto.' }
    Assert-SafeJavaEnvironment
    $apkPath = Assert-WithinReleaseRoot $Apk $script:ReleaseRoot
    $reportFile = Assert-WithinReleaseRoot $ReportPath $script:ReleaseRoot
    $policyFile = Assert-WithinReleaseRoot $PolicyPath $script:ReleaseRoot
    $output = Assert-WithinReleaseRoot $Destination (Join-Path $script:ReleaseRoot '.local-evidence\release')
    foreach ($path in @($apkPath, $reportFile, $output)) { Assert-IgnoredReleasePath $path }
    Assert-Absent $output
    if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($output)) -PathType Container)) {
        throw 'Preparar conscientemente el directorio padre de evidencia antes de firmar.'
    }
    $hash = Get-ReleaseFileHash $apkPath
    if ($hash -ne $Approved) { throw 'El APK no coincide con el SHA-256 aprobado.' }
    $policyData = Read-ReleaseJson $policyFile
    $report = Read-ReleaseJson $reportFile
    if ($report.verdict -ne 'UNSIGNED_CANDIDATE_CHECKS_PASSED' -or @($report.errors).Count -ne 0 -or
        $report.signature -ne 'EXPECTED_UNSIGNED_REJECTION' -or $report.zipalign_16_kib -ne $true -or
        $report.policy_sha256 -ne (Get-ReleaseFileHash $policyFile) -or
        $report.artifact.sha256 -ne $hash -or $report.artifact.name -ne [IO.Path]::GetFileName($apkPath) -or
        $report.artifact.size -ne (Get-Item -LiteralPath $apkPath).Length) {
        throw 'Informe unsigned no aprobado o no ligado a estos bytes y politica.'
    }
    foreach ($field in @('package', 'version_code', 'version_name', 'min_sdk', 'target_sdk', 'compile_sdk')) {
        if ($report.metadata.$field -ne $policyData.$field) { throw 'Identidad de informe y politica diferente.' }
    }
    if ($policyData.package -ne 'com.blackatsystems.miguardia' -or
        $policyData.version_code -lt 2 -or $policyData.max_apk_bytes -gt 134217728 -or
        (Get-Item -LiteralPath $apkPath).Length -gt $policyData.max_apk_bytes) {
        throw 'Politica fuera de la entrega MiGuardia.'
    }
    $java = Assert-ReleasePath (Join-Path (Get-ReleaseLocalAppData) 'Programs\android-studio\jbr\bin\java.exe')
    $sdk = Assert-ReleasePath (Join-Path (Get-ReleaseLocalAppData) 'Android\Sdk\build-tools\37.0.0')
    $align = Join-Path $sdk 'zipalign.exe'
    $signer = Join-Path $sdk 'lib\apksigner.jar'
    $tools = @{ java = $java; zipalign = $align; apksigner_jar = $signer }
    $toolHashes = @{}
    foreach ($name in $tools.Keys) {
        $toolHashes[$name] = Get-ReleaseFileHash $tools[$name]
        if ($report.tools.$name.sha256 -ne $toolHashes[$name]) { throw 'Herramienta distinta de la inspeccion aprobada.' }
    }
    $custody = Get-ExistingReleaseCustody
    return @{ apk = $apkPath; approved = $hash; report = $reportFile; reportHash = Get-ReleaseFileHash $reportFile;
        policy = $policyFile; policyHash = Get-ReleaseFileHash $policyFile; output = $output;
        tools = $tools; toolHashes = $toolHashes; custody = $custody;
        aligned = Join-Path $output 'aligned-unsigned.apk'; signed = Join-Path $output 'signed-unverified.apk' }
}

function Assert-PrivateReleaseConsole([bool]$PrivateConfirmed, [bool]$RecordingOff) {
    if (-not $PrivateConfirmed -or -not $RecordingOff -or $Host.Name -ne 'ConsoleHost' -or
        -not [Environment]::UserInteractive -or [Console]::IsInputRedirected -or
        [Console]::IsOutputRedirected -or [Console]::IsErrorRedirected -or $env:WT_SESSION) {
        throw 'Requiere consola Windows separada visible, sin PTY, redireccion o grabacion.'
    }
    foreach ($hive in @('HKCU:', 'HKLM:')) {
        foreach ($tail in @('SOFTWARE\Policies\Microsoft\Windows\PowerShell\Transcription',
            'SOFTWARE\WOW6432Node\Policies\Microsoft\Windows\PowerShell\Transcription',
            'SOFTWARE\Policies\Microsoft\PowerShellCore\Transcription')) {
            $path = "$hive\$tail"
            if (Test-Path -LiteralPath $path) {
                $settings = Get-ItemProperty -LiteralPath $path
                if ($settings.PSObject.Properties.Name -contains 'EnableTranscripting' -and $settings.EnableTranscripting -ne 0) {
                    throw 'La politica exige transcripcion; no iniciar firma.'
                }
            }
        }
    }
    if (-not ('MiGuardiaReleaseConsole' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class MiGuardiaReleaseConsole {
    [DllImport("kernel32.dll")] public static extern IntPtr GetConsoleWindow();
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr window);
}
'@
    }
    $window = [MiGuardiaReleaseConsole]::GetConsoleWindow()
    if ($window -eq [IntPtr]::Zero -or -not [MiGuardiaReleaseConsole]::IsWindowVisible($window)) {
        throw 'Consola clasica visible requerida; una PTY no es admisible.'
    }
    # No software can attest absence of external recording. These explicit confirmations
    # are operator preconditions, not a claim to detect every recorder/transcript.
}

function Assert-SigningInputsUnchanged($Plan) {
    Assert-PrivateCustodyAccess ([IO.Path]::GetDirectoryName($Plan.custody.key)) $Plan.custody.key
    foreach ($pair in @(@($Plan.apk, $Plan.approved), @($Plan.report, $Plan.reportHash),
        @($Plan.policy, $Plan.policyHash), @($Plan.custody.key, $Plan.custody.keyHash),
        @($Plan.custody.status, $Plan.custody.statusHash))) {
        if ((Get-ReleaseFileHash $pair[0]) -ne $pair[1]) { throw 'Un insumo aprobado cambio; conservar archivos y volver a MAIN.' }
    }
    foreach ($name in $Plan.tools.Keys) {
        if ((Get-ReleaseFileHash $Plan.tools[$name]) -ne $Plan.toolHashes[$name]) { throw 'Herramienta modificada.' }
    }
}

function Invoke-ReleaseSigning($Plan, [bool]$PrivateConfirmed, [bool]$RecordingOff) {
    Assert-PrivateReleaseConsole $PrivateConfirmed $RecordingOff
    Assert-SafeJavaEnvironment
    Assert-SigningInputsUnchanged $Plan
    Assert-Absent $Plan.output
    New-PrivateDirectory $Plan.output
    Write-Host 'MIGUARDIA: FIRMA LOCAL. CONTRASENA SOLO EN EL PROCESO OFICIAL.' -ForegroundColor Cyan
    Write-Host 'No transcribas, captures ni envies la contrasena al chat. No se publica ni instala nada.'
    Write-Host 'Si se interrumpe, conserva los parciales; no se reemplazan ni se borran.'
    $alignedHash = $null
    try {
        $script:SigningPhase = 'ALIGN'
        Assert-Absent $Plan.aligned
        & $Plan.tools.zipalign '-P' '16' '-v' '4' $Plan.apk $Plan.aligned
        if ($LASTEXITCODE -ne 0) { throw 'Alineacion incompleta.' }
        $alignedHash = Get-ReleaseFileHash $Plan.aligned
        Assert-SigningInputsUnchanged $Plan
        Assert-Absent $Plan.signed
        $script:SigningPhase = 'SIGN'
        # Direct native invocation: no assignment, pipe, capture, environment password,
        # alias discovery or redirection. The official signer inherits this console.
        & $Plan.tools.java '-jar' $Plan.tools.apksigner_jar 'sign' '--ks' $Plan.custody.key '--v1-signing-enabled' 'false' '--v2-signing-enabled' 'true' '--v3-signing-enabled' 'true' '--v4-signing-enabled' 'false' '--out' $Plan.signed $Plan.aligned
        if ($LASTEXITCODE -ne 0) { throw 'Firma no completada.' }
        $signedHash = Get-ReleaseFileHash $Plan.signed
    } finally {
        $priorPhase = $script:SigningPhase
        $script:SigningPhase = 'POSTCHECK'
        Assert-SigningInputsUnchanged $Plan
        if ($alignedHash -and (Get-ReleaseFileHash $Plan.aligned) -ne $alignedHash) { throw 'La copia alineada cambio durante la firma.' }
        $script:SigningPhase = $priorPhase
    }
    $record = @{ status = 'SIGNED_UNVERIFIED'; unsignedSha256 = $Plan.approved;
        alignedSha256 = $alignedHash; signedSha256 = $signedHash;
        certificateExpected = $script:ReleaseCertificate; policySha256 = $Plan.policyHash;
        recordedUtc = [DateTime]::UtcNow.ToString('o'); published = $false; installed = $false }
    $receipt = Join-Path $Plan.output 'signing-unverified.json'
    $script:SigningPhase = 'RECEIPT'
    Assert-Absent $receipt
    $stream = [IO.File]::Open($receipt, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($record | ConvertTo-Json))
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
    Write-Host 'FIRMA PRODUCIDA, NO APROBADA. MAIN debe ejecutar el verificador firmado separado.' -ForegroundColor Yellow
}

if ($MyInvocation.InvocationName -ne '.') {
    try {
        if ($signingExecute) { Assert-PrivateReleaseConsole $PrivateConsoleConfirmed.IsPresent $NoRecordingConfirmed.IsPresent }
        $plan = Get-ReleaseSigningPlan $UnsignedApk $ApprovedSha256 $UnsignedReport $Policy $OutputDirectory
        if ($signingExecute) {
            Invoke-ReleaseSigning $plan $PrivateConsoleConfirmed.IsPresent $NoRecordingConfirmed.IsPresent
        } else {
            @{ mode = 'PLAN_ONLY'; unsignedSha256 = $plan.approved; policySha256 = $plan.policyHash;
                custody = 'EXISTING_COMPLETE_HASH_MATCH'; output = $plan.output;
                next = 'Separate private classic console and explicit execution; signed verifier mandatory.' } | ConvertTo-Json
        }
    } catch {
        Write-Host 'Firma no iniciada o incompleta. Conservar todos los archivos; volver a MAIN. No se aprueba ni publica nada.' -ForegroundColor Yellow
        Write-Host ('Fase: ' + $script:SigningPhase)
        exit 1
    }
}
