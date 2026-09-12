$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'create_signing_key.ps1')

$passed = 0
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Fallo: $Name" }
    $script:passed++
}
function Expect-Rejected([scriptblock]$Action, [string]$Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}

$base = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$root = Join-Path $base ('miguardia-signing-guards-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $root
$link = Join-Path $root 'junction'
try {
    Expect-Rejected { Assert-PlainLocalPath 'relative\file' } 'Ruta relativa rechazada'
    Expect-Rejected { Assert-PlainLocalPath '\\server\share\file' } 'UNC rechazada'
    Expect-Rejected { Assert-PlainLocalPath 'D:\*.p12' } 'Wildcard rechazado'
    Expect-Rejected { Assert-Absent $root } 'Directorio existente preservado'

    $private = Join-Path $root 'private'
    New-PrivateDirectory $private
    $rules = @((Get-Acl -LiteralPath $private).GetAccessRules($true, $true,
        [Security.Principal.SecurityIdentifier]))
    Check ($rules.Count -eq 2 -and @($rules | Where-Object IsInherited).Count -eq 0) 'ACL privada comprobada'
    Expect-Rejected { New-PrivateDirectory $private } 'No reemplaza directorio privado'
    New-PrivateDirectory $private -ReuseEmpty
    Check ((Get-Acl -LiteralPath $private).AreAccessRulesProtected) 'Reanuda solamente preparacion de carpeta vacia'

    $source = Join-Path $private 'synthetic-source.txt'
    $destination = Join-Path $private 'synthetic-copy.txt'
    [IO.File]::WriteAllText($source, 'synthetic data, not a key or password')
    Expect-Rejected { New-PrivateDirectory $private -ReuseEmpty } 'Reanudacion rechaza cualquier archivo existente'
    $hash = Copy-NewFile $source $destination
    Check ($hash -eq (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) 'Copia exacta'
    Expect-Rejected { Copy-NewFile $source $destination } 'No sobrescribe copia existente'
    Check ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -eq $hash) 'Origen intacto'

    $null = New-Item -ItemType Junction -Path $link -Target $private
    Expect-Rejected { Assert-PlainLocalPath (Join-Path $link 'new.txt') } 'Ancestro junction rechazado'
    Expect-Rejected { Assert-PlainLocalPath $link } 'Junction directa rechazada'
    Expect-Rejected { Assert-EmptyDirectory $link } 'Reanudacion rechaza enlaces'

    $empty = Join-Path $private 'empty.txt'
    [IO.File]::WriteAllBytes($empty, [byte[]]@())
    Expect-Rejected { Copy-NewFile $empty (Join-Path $private 'empty-copy.txt') } 'Archivo vacio rechazado'
    Expect-Rejected { Copy-NewFile $private (Join-Path $private 'directory-copy.txt') } 'Directorio como archivo rechazado'

    function Fake-FailedTool { $global:LASTEXITCODE = 1 }
    Expect-Rejected {
        Invoke-KeytoolStep 'Fake-FailedTool' @('synthetic') (Join-Path $private 'never-created.txt')
    } 'Exit code fallido no se acepta'
    function Fake-NoOutputTool { $global:LASTEXITCODE = 0 }
    Expect-Rejected {
        Invoke-KeytoolStep 'Fake-NoOutputTool' @('synthetic') (Join-Path $private 'missing.txt')
    } 'Exit cero sin artefacto no se acepta'

    $tokens = $null
    $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'create_signing_key.ps1'), [ref]$tokens, [ref]$errors)
    Check ($errors.Count -eq 0) 'Sintaxis PowerShell valida'
    $unsafeTokens = @($tokens | Where-Object {
        $_.Text -match '^-((store|key)pass)(:|$)' -or
        $_.Text -in @('Start-Transcript', 'ConvertFrom-SecureString', 'ReadToEnd', 'RedirectStandardInput')
    })
    Check ($unsafeTokens.Count -eq 0) 'Sin transporte de contrasena ni transcripcion'
    Check (@($ast.FindAll({ param($node)
        $node -is [System.Management.Automation.Language.RedirectionAst]
    }, $true)).Count -eq 0) 'Sin redireccion de consola'
    $custodyCalls = @($ast.FindAll({ param($node)
        $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -eq 'Invoke-Custody'
    }, $true))
    Check ($custodyCalls.Count -eq 1 -and
        $custodyCalls[0].Parent.Parent -is [System.Management.Automation.Language.StatementBlockAst]) 'Invocacion directa sin capturar salida nativa'
    Write-Output "$passed comprobaciones aprobadas; sin claves ni contrasenas generadas."
} finally {
    # Delete only this run's synthetic fixture directory; never follow its junction.
    if (Get-Item -LiteralPath $link -Force -ErrorAction SilentlyContinue) {
        [IO.Directory]::Delete($link)
    }
    $resolved = [IO.Path]::GetFullPath($root)
    if ($resolved.StartsWith($base, [StringComparison]::OrdinalIgnoreCase) -and
        [IO.Path]::GetFileName($resolved) -match '^miguardia-signing-guards-[0-9a-f]{32}$' -and
        (Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'No se limpia una raiz convertida en enlace.'
    }
    if (-not $resolved.StartsWith($base, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolved) -notmatch '^miguardia-signing-guards-[0-9a-f]{32}$') {
        throw 'No se limpia una ruta fuera del temporal propio.'
    }
    Remove-Item -LiteralPath $resolved -Recurse
}
