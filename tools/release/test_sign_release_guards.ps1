$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'sign_release.ps1')

$passed = 0
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Fallo: $Name" }
    $script:passed++
}
function Reject([scriptblock]$Action, [string]$Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}
function Put-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
}
function Put-Synthetic([string]$Path) {
    $parent = [IO.Path]::GetDirectoryName($Path)
    if (-not (Test-Path -LiteralPath $parent)) { $null = New-Item -ItemType Directory -Path $parent }
    [IO.File]::WriteAllText($Path, 'synthetic bytes; not an executable, APK, key or password')
}

$helper = Join-Path $PSScriptRoot 'sign_release.ps1'
$source = [IO.File]::ReadAllText($helper)
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$temporary = Join-Path $tempBase ('miguardia-release-sign-tests-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $temporary
$link = Join-Path $temporary 'junction'
$originalRoot = $script:ReleaseRoot
$environmentNames = @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')
$savedEnvironment = @{}
try {
    foreach ($name in $environmentNames) {
        $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
        [Environment]::SetEnvironmentVariable($name, $null)
    }
    $script:ReleaseRoot = Join-Path $temporary 'repo'
    $script:FakeLocalData = Join-Path $temporary 'local-data'
    function Get-ReleaseLocalAppData { return $script:FakeLocalData }
    # The sandbox has no Git checkout. The double refuses paths outside its own repo.
    function Assert-IgnoredReleasePath([string]$Path) {
        $null = Assert-WithinReleaseRoot $Path $script:ReleaseRoot
    }
    $evidence = Join-Path $script:ReleaseRoot '.local-evidence\release'
    $null = New-Item -ItemType Directory -Path $evidence
    $apk = Join-Path $evidence 'original.apk'; Put-Synthetic $apk
    $policyPath = Join-Path $evidence 'policy.json'
    $reportPath = Join-Path $evidence 'inspection.json'
    $destination = Join-Path $evidence 'signing-new'
    $java = Join-Path $script:FakeLocalData 'Programs\android-studio\jbr\bin\java.exe'
    $align = Join-Path $script:FakeLocalData 'Android\Sdk\build-tools\37.0.0\zipalign.exe'
    $signer = Join-Path $script:FakeLocalData 'Android\Sdk\build-tools\37.0.0\lib\apksigner.jar'
    foreach ($tool in @($java, $align, $signer)) { Put-Synthetic $tool }
    $key = Join-Path $script:FakeLocalData 'BlackatSystems\MiGuardiaSigning\release-signing.p12'
    New-PrivateDirectory ([IO.Path]::GetDirectoryName($key))
    Put-Synthetic $key
    $statusPath = Join-Path ([IO.Path]::GetDirectoryName($key)) 'custody-status.json'
    $status = @{ status = 'COMPLETE'; certificateSha256 = $script:ReleaseCertificate;
        recoveryCheck = 'KEYTOOL_CERTREQ_FROM_USB_BACKUP_ON_THIS_PC'; encryptedKeystoreSha256 = Get-ReleaseFileHash $key }
    Put-Json $statusPath $status
    $policyData = @{ package = 'com.blackatsystems.miguardia'; version_code = 3; version_name = '2.0.0';
        min_sdk = 26; target_sdk = 37; compile_sdk = 37; max_apk_bytes = 134217728 }
    Put-Json $policyPath $policyData
    $report = @{ verdict = 'UNSIGNED_CANDIDATE_CHECKS_PASSED'; errors = @();
        signature = 'EXPECTED_UNSIGNED_REJECTION'; zipalign_16_kib = $true;
        artifact = @{name = 'original.apk'; size = (Get-Item $apk).Length; sha256 = Get-ReleaseFileHash $apk};
        policy_sha256 = Get-ReleaseFileHash $policyPath; metadata = $policyData.Clone();
        tools = @{ java = @{sha256 = Get-ReleaseFileHash $java}; zipalign = @{sha256 = Get-ReleaseFileHash $align};
            apksigner_jar = @{sha256 = Get-ReleaseFileHash $signer} } }
    Put-Json $reportPath $report
    $approved = Get-ReleaseFileHash $apk
    function Plan { Get-ReleaseSigningPlan $apk $approved $reportPath $policyPath $destination }
    $p = Plan
    Check (-not (Test-Path $destination)) 'Plan no crea destinos ni firma'
    Check ($p.custody.keyHash -eq (Get-ReleaseFileHash $key)) 'Custodia sintetica ligada por hash'
    Reject { Assert-PrivateCustodyAccess $evidence $apk } 'Directorio sin ACL de custodia rechazado'
    foreach ($value in @('relative.apk', '\\server\share\file', 'C:\*.apk', ($apk + ':stream'), 'C:\folder\..\file')) {
        Reject { Assert-ReleasePath $value } 'Ruta ambigua rechazada'
    }
    $null = New-Item -ItemType Junction -Path $link -Target $evidence
    Reject { Assert-ReleasePath (Join-Path $link 'new.apk') } 'Junction rechazada'
    Reject { Get-ReleaseSigningPlan $apk ('0' * 64) $reportPath $policyPath $destination } 'SHA aprobado distinto'
    Reject { Get-ReleaseSigningPlan $apk $approved $reportPath $policyPath (Join-Path $temporary 'outside') } 'Salida fuera de evidencia'
    $null = New-Item -ItemType Directory -Path $destination
    Reject { Plan } 'No sobrescribe destino existente'
    [IO.Directory]::Delete($destination)
    $status.status = 'PREPARED'; Put-Json $statusPath $status
    Reject { Plan } 'Custodia incompleta'
    $status.status = 'COMPLETE'; $status.certificateSha256 = '0' * 64; Put-Json $statusPath $status
    Reject { Plan } 'Certificado ajeno'
    $status.certificateSha256 = $script:ReleaseCertificate; $status.encryptedKeystoreSha256 = '0' * 64; Put-Json $statusPath $status
    Reject { Plan } 'Almacen ajeno'
    $status.encryptedKeystoreSha256 = Get-ReleaseFileHash $key; Put-Json $statusPath $status
    $report.verdict = 'FAILED'; Put-Json $reportPath $report
    Reject { Plan } 'Informe no aprobado'
    $report.verdict = 'UNSIGNED_CANDIDATE_CHECKS_PASSED'; $report.tools.java.sha256 = '0' * 64; Put-Json $reportPath $report
    Reject { Plan } 'Java distinto del inspector'
    $report.tools.java.sha256 = Get-ReleaseFileHash $java; Put-Json $reportPath $report
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'synthetic-option')
    Reject { Plan } 'Opciones Java externas'
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $null)
    $p = Plan
    [IO.File]::AppendAllText($key, 'changed')
    Reject { Assert-SigningInputsUnchanged $p } 'Cambio de almacen despues del plan'
    Put-Synthetic $key
    Check ((Get-ReleaseFileHash $key) -eq $status.encryptedKeystoreSha256) 'Restauracion fixture exacta'
    Reject { Assert-PrivateReleaseConsole $false $false } 'No ejecuta sin confirmaciones'
    $tokens = $null; $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($helper, [ref]$tokens, [ref]$errors)
    Check ($errors.Count -eq 0) 'Sintaxis valida'
    Check (@($ast.FindAll({param($n) $n -is [Management.Automation.Language.RedirectionAst]}, $true)).Count -eq 0) 'Sin redirecciones'
    Check (@($tokens | Where-Object {$_.Text -in @('Start-Transcript', 'Read-Host', '--ks-pass', '--key-pass', '--ks-key-alias', 'RedirectStandardInput', 'ReadToEnd')}).Count -eq 0) 'Sin transporte de contrasena ni enumeracion de alias'
    Check ($source.Contains("'--v1-signing-enabled' 'false' '--v2-signing-enabled' 'true' '--v3-signing-enabled' 'true' '--v4-signing-enabled' 'false'")) 'Esquemas firma exactos'
    Check ($source.Contains("status = 'SIGNED_UNVERIFIED'")) 'No acredita verificacion firmada'
    $nativeCalls = @($ast.FindAll({param($n) $n -is [Management.Automation.Language.CommandAst] -and $n.Extent.Text.StartsWith('& $Plan.tools.')}, $true))
    Check ($nativeCalls.Count -eq 2 -and @($nativeCalls | Where-Object { $_.Parent.Parent -isnot [Management.Automation.Language.StatementBlockAst] }).Count -eq 0) 'Invocaciones nativas directas, sin captura'

    # Exercise the actual CLI binding and entry point in child PowerShell, replacing
    # every plan/console/execution boundary with a harmless double before entry.
    $cli = Join-Path $temporary 'cli'
    $null = New-Item -ItemType Directory -Path $cli
    [IO.File]::Copy((Join-Path $PSScriptRoot 'create_signing_key.ps1'), (Join-Path $cli 'create_signing_key.ps1'))
    $entry = "if (`$MyInvocation.InvocationName -ne '.') {"
    $doubles = @'
function Assert-PrivateReleaseConsole { }
function Get-ReleaseSigningPlan { return @{approved='synthetic';policyHash='synthetic';output='synthetic'} }
function Invoke-ReleaseSigning { Write-Output 'MOCK_SIGN_CALL_NO_NATIVE_OR_KEY' }
'@
    Check ($source.Split([string[]]@($entry), [StringSplitOptions]::None).Count -eq 2) 'Entry point unico'
    $cliPath = Join-Path $cli 'sign_release.ps1'
    [IO.File]::WriteAllText($cliPath, $source.Replace($entry, $doubles + "`n" + $entry), [Text.UTF8Encoding]::new($false))
    $powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $executed = & $powershell -NoProfile -File $cliPath -Execute -PrivateConsoleConfirmed -NoRecordingConfirmed
    Check ($LASTEXITCODE -eq 0 -and ($executed -join "`n") -eq 'MOCK_SIGN_CALL_NO_NATIVE_OR_KEY') 'Execute CLI no perdido por dot-source'
    $planned = & $powershell -NoProfile -File $cliPath
    Check ($LASTEXITCODE -eq 0 -and ($planned -join "`n").Contains('PLAN_ONLY') -and -not ($planned -join "`n").Contains('MOCK_SIGN_CALL')) 'CLI defecto solo plan'
    $failedDouble = '$script:SigningPhase = ''SIGN''; [IO.File]::WriteAllText((Join-Path $PSScriptRoot ''partial.apk''), ''synthetic partial''); throw ''private diagnostic not echoed'''
    $failedSource = [IO.File]::ReadAllText($cliPath).Replace("Write-Output 'MOCK_SIGN_CALL_NO_NATIVE_OR_KEY'", $failedDouble)
    [IO.File]::WriteAllText($cliPath, $failedSource, [Text.UTF8Encoding]::new($false))
    $beforeKey = Get-ReleaseFileHash $key
    $failed = & $powershell -NoProfile -File $cliPath -Execute -PrivateConsoleConfirmed -NoRecordingConfirmed
    Check ($LASTEXITCODE -eq 1 -and ($failed -join "`n").Contains('Fase: SIGN')) 'Fallo CLI devuelve fase segura y exit no cero'
    Check ((Test-Path -LiteralPath (Join-Path $cli 'partial.apk')) -and (Get-ReleaseFileHash $key) -eq $beforeKey -and
        (Get-ReleaseFileHash $apk) -eq $approved) 'Fallo conserva parciales y entradas sinteticas'
    Check (-not ($failed -join "`n").Contains('private diagnostic') -and -not ($failed -join "`n").Contains($key)) 'Fallo no expone diagnostico privado o custodia'
    Write-Output "$passed comprobaciones aprobadas; sin leer custodia real, claves, firma, Gradle o dispositivos."
} finally {
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }
    $script:ReleaseRoot = $originalRoot
    if (Get-Item -LiteralPath $link -Force -ErrorAction SilentlyContinue) { [IO.Directory]::Delete($link) }
    $resolved = [IO.Path]::GetFullPath($temporary)
    if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^miguardia-release-sign-tests-[0-9a-f]{32}$' -or
        ((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'No borrar una ruta ajena al temporal sintetico.'
    }
    Remove-Item -LiteralPath $resolved -Recurse
}
