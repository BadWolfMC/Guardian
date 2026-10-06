[CmdletBinding()]
param(
    [string]$Version = '1.0.0-ci.smoke+windows'
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Gradle = Join-Path $Root 'gradlew.bat'
$ReleaseManager = Join-Path $PSScriptRoot 'release-manager.ps1'
$TempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("guardian-release-smoke-" + [guid]::NewGuid().ToString('N'))

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Get-SourceCommit {
    if ($env:GITHUB_SHA -match '^[0-9a-fA-F]{40}$') { return $env:GITHUB_SHA.ToLowerInvariant() }
    try {
        $sha = (& git -C $Root rev-parse HEAD 2>$null).Trim()
        if ($sha -match '^[0-9a-fA-F]{40}$') { return $sha.ToLowerInvariant() }
    } catch {
        # A source archive without .git is still useful for a local smoke rehearsal.
    }
    return ('0' * 40)
}

Push-Location $Root
try {
    [System.IO.Directory]::CreateDirectory($TempRoot) | Out-Null
    $releaseIdentity = Join-Path $TempRoot 'release-identity'
    $serverIdentity = Join-Path $TempRoot 'server-identity'
    $input = Join-Path $TempRoot 'release-input'
    $final = Join-Path $TempRoot 'release-final'
    [System.IO.Directory]::CreateDirectory($input) | Out-Null

    Write-Host "Building disposable release-workflow smoke artifacts for $Version"
    & $Gradle --no-daemon clean :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build :cerberus-fabric:releaseToolJar "-PguardianVersion=$Version"
    if ($LASTEXITCODE -ne 0) { throw "Gradle release-workflow smoke build failed with exit code $LASTEXITCODE" }

    & $ReleaseManager -Action generate-release-key -OutputDirectory $releaseIdentity
    & $ReleaseManager -Action generate-server-identity -OutputDirectory $serverIdentity

    $paperName = "guardian-paper-$Version.jar"
    $velocityName = "guardian-velocity-$Version.jar"
    $unsignedName = "cerberus-fabric-$Version-unsigned.jar"
    $toolName = "cerberus-release-tools-$Version.jar"

    Copy-Item -LiteralPath (Join-Path $Root "guardian-paper\build\libs\guardian-paper-$Version.jar") -Destination (Join-Path $input $paperName)
    Copy-Item -LiteralPath (Join-Path $Root "guardian-velocity\build\libs\guardian-velocity-$Version.jar") -Destination (Join-Path $input $velocityName)
    Copy-Item -LiteralPath (Join-Path $Root "cerberus-fabric\build\libs\cerberus-fabric-$Version.jar") -Destination (Join-Path $input $unsignedName)
    Copy-Item -LiteralPath (Join-Path $Root "cerberus-fabric\build\libs\cerberus-release-tools-$Version.jar") -Destination (Join-Path $input $toolName)
    Copy-Item -LiteralPath (Join-Path $Root 'LICENSE') -Destination (Join-Path $input 'LICENSE')
    Copy-Item -LiteralPath (Join-Path $Root 'THIRD_PARTY_NOTICES.md') -Destination (Join-Path $input 'THIRD_PARTY_NOTICES.md')

    $repository = if ($env:GITHUB_REPOSITORY -match '^[^/\s]+/[^/\s]+$') { $env:GITHUB_REPOSITORY } else { 'local/Guardian' }
    $runId = if ($env:GITHUB_RUN_ID -match '^[0-9]+$') { $env:GITHUB_RUN_ID } else { '0' }
    $runAttempt = if ($env:GITHUB_RUN_ATTEMPT -match '^[0-9]+$') { $env:GITHUB_RUN_ATTEMPT } else { '0' }
    $manifest = [ordered]@{
        schema = 1
        version = $Version
        sourceCommit = Get-SourceCommit
        repository = $repository
        workflowRunId = $runId
        workflowRunAttempt = $runAttempt
    }
    $manifestPath = Join-Path $input 'RELEASE_INPUT.json'
    Write-Utf8NoBom $manifestPath (($manifest | ConvertTo-Json) + "`n")

    $checksumNames = @(
        $paperName,
        $velocityName,
        $unsignedName,
        $toolName,
        'LICENSE',
        'THIRD_PARTY_NOTICES.md',
        'RELEASE_INPUT.json'
    )
    $checksumLines = foreach ($name in $checksumNames) {
        "$(Get-Sha256 (Join-Path $input $name))  $name"
    }
    Write-Utf8NoBom (Join-Path $input 'SHA256SUMS-CI.txt') (($checksumLines -join "`n") + "`n")

    $releasePrivate = Join-Path $releaseIdentity 'cerberus-release-signing.key'
    $releasePublic = Join-Path $releaseIdentity 'cerberus-release-signing.pub'
    $serverPublic = Join-Path $serverIdentity 'guardian-server-auth.pub'

    & $ReleaseManager `
        -Action finalize-release `
        -Version $Version `
        -InputDirectory $input `
        -OutputDirectory $final `
        -ReleasePrivateKey $releasePrivate `
        -ReleasePublicKey $releasePublic `
        -GuardianServerPublicKeys $serverPublic

    & $ReleaseManager `
        -Action verify-release `
        -Version $Version `
        -ArtifactDirectory $final `
        -ReleasePublicKey $releasePublic `
        -ReleaseToolJar (Join-Path $input $toolName) `
        -GuardianServerPublicKeys $serverPublic

    $publishedPublic = Join-Path $final 'cerberus-release-signing.pub'
    if (-not (Test-Path -LiteralPath $publishedPublic -PathType Leaf)) {
        throw 'Final release smoke output did not publish the Cerberus release public key.'
    }
    if ((Get-Sha256 $publishedPublic) -ne (Get-Sha256 $releasePublic)) {
        throw 'Published release public key does not match the disposable verification key.'
    }

    Write-Host 'PowerShell release-workflow smoke passed with disposable keys.'
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $TempRoot) {
        Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
