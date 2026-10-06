[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('generate-release-key','generate-server-identity','sign-cerberus','verify-release','finalize-release','checksums')]
    [string]$Action,
    [string]$InputDirectory,
    [string]$OutputDirectory,
    [string]$ReleasePrivateKey,
    [string]$ReleasePublicKey,
    [string]$ReleaseToolJar,
    [string]$GuardianServerPublicKeys,
    [string]$UnsignedCerberusJar,
    [string]$SignedOutput,
    [string]$Version,
    [string]$ArtifactDirectory = 'release-final'
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Gradle = Join-Path $Root 'gradlew.bat'
$SemVerPattern = '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'

function Require-PathArgument([string]$Value, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Name is required for action '$Action'." }
    return [System.IO.Path]::GetFullPath($Value)
}

function Require-ExistingFile([string]$Value, [string]$Name) {
    $path = Require-PathArgument $Value $Name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "$Name does not exist or is not a file: $path" }
    $item = Get-Item -LiteralPath $path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Name must not be a symlink/reparse-point file: $path"
    }
    return $path
}

function Require-ExistingDirectory([string]$Value, [string]$Name) {
    $path = Require-PathArgument $Value $Name
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "$Name does not exist or is not a directory: $path" }
    $item = Get-Item -LiteralPath $path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Name must not be a symlink/reparse-point directory: $path"
    }
    return $path
}

function Require-Version([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Version is required for action '$Action'." }
    if ($Value -notmatch $SemVerPattern) {
        throw 'Version must be SemVer-like MAJOR.MINOR.PATCH with optional prerelease/build metadata (for example 1.0.0-rc.1).'
    }
    return $Value
}

function Invoke-GuardianGradle([string[]]$Arguments) {
    & $Gradle @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Ensure-EmptyDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        $item = Get-Item -LiteralPath $Path -Force
        if (-not $item.PSIsContainer) { throw "Output path exists but is not a directory: $Path" }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing to use symlink/reparse-point output directory: $Path"
        }
        $items = @(Get-ChildItem -LiteralPath $Path -Force)
        if ($items.Count -ne 0) { throw "Refusing to use non-empty output directory: $Path" }
    } else {
        [System.IO.Directory]::CreateDirectory($Path) | Out-Null
    }
}

function Get-JavaExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $command) { $command = Get-Command java -ErrorAction SilentlyContinue }
    if ($null -eq $command) { throw 'Java was not found. Install Java 25 or set JAVA_HOME before using release tooling.' }
    return $command.Source
}

function Invoke-ReleaseTool([string]$ToolJar, [string]$MainClass, [string[]]$ToolArguments) {
    $tool = Require-ExistingFile $ToolJar 'ReleaseToolJar'
    Assert-JarEntries $tool @(
        'com/badwolfmc/cerberus/release/CerberusReleaseSigner.class',
        'com/badwolfmc/cerberus/release/CerberusReleaseVerifier.class',
        'com/badwolfmc/guardian/protocol/CerberusReleaseArtifact.class'
    )
    $java = Get-JavaExecutable
    & $java '-cp' $tool $MainClass @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "Release tool failed with exit code $LASTEXITCODE ($MainClass)" }
}

function Assert-ExactDirectoryFiles([string]$Directory, [string[]]$ExpectedNames, [string]$Description) {
    $items = @(Get-ChildItem -LiteralPath $Directory -Force)
    $nonFiles = @($items | Where-Object { -not $_.PSIsContainer -and -not (Test-Path -LiteralPath $_.FullName -PathType Leaf) })
    $directories = @($items | Where-Object { $_.PSIsContainer })
    $reparsePoints = @($items | Where-Object { ($_.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 })
    if ($directories.Count -ne 0 -or $nonFiles.Count -ne 0 -or $reparsePoints.Count -ne 0) {
        throw "$Description must contain regular non-symlink files only; unexpected directory/special/reparse-point entry found."
    }
    $actual = @($items | ForEach-Object { $_.Name } | Sort-Object)
    $expected = @($ExpectedNames | Sort-Object)
    if ((Compare-Object -ReferenceObject $expected -DifferenceObject $actual).Count -ne 0) {
        throw "$Description must contain exactly: $($expected -join ', '). Actual: $($actual -join ', ')"
    }
}

function Read-KeyValueFile([string]$Path) {
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { throw "Malformed key/value line in ${Path}: $line" }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($result.ContainsKey($key)) { throw "Duplicate key '$key' in $Path" }
        $result[$key] = $value
    }
    return $result
}

function Get-ZipEntryText([string]$JarPath, [string]$EntryName, [int64]$MaxBytes = 131072) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry($EntryName)
        if ($null -eq $entry) { throw "Missing required JAR entry '$EntryName' in $JarPath" }
        if ($entry.Length -lt 0 -or $entry.Length -gt $MaxBytes) { throw "JAR entry '$EntryName' exceeds release-verification size limit" }
        $stream = $entry.Open()
        try {
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.UTF8Encoding]::new($false, $true), $true, 4096, $true)
            try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
        } finally { $stream.Dispose() }
    } finally { $archive.Dispose() }
}

function Get-JarVersion([string]$JarPath, [ValidateSet('paper','velocity','cerberus')] [string]$Kind) {
    switch ($Kind) {
        'paper' {
            $text = Get-ZipEntryText $JarPath 'plugin.yml'
            $match = [regex]::Match($text, '(?m)^version:\s*["'']?([^"''\r\n]+)["'']?\s*$')
            if (-not $match.Success) { throw "Could not read Paper version from $JarPath" }
            return $match.Groups[1].Value.Trim()
        }
        'velocity' {
            $json = Get-ZipEntryText $JarPath 'velocity-plugin.json' | ConvertFrom-Json
            if ([string]::IsNullOrWhiteSpace([string]$json.version)) { throw "Could not read Velocity version from $JarPath" }
            return [string]$json.version
        }
        'cerberus' {
            $json = Get-ZipEntryText $JarPath 'fabric.mod.json' | ConvertFrom-Json
            if ([string]::IsNullOrWhiteSpace([string]$json.version)) { throw "Could not read Cerberus version from $JarPath" }
            return [string]$json.version
        }
    }
}

function Assert-JarVersion([string]$JarPath, [string]$Kind, [string]$ExpectedVersion) {
    $actual = Get-JarVersion $JarPath $Kind
    if ($actual -ne $ExpectedVersion) { throw "$Kind artifact version '$actual' does not match requested release version '$ExpectedVersion': $JarPath" }
}

function Read-ChecksumFile([string]$ChecksumPath) {
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $ChecksumPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $match = [regex]::Match($line, '^([0-9a-fA-F]{64})\s+\*?(.+)$')
        if (-not $match.Success) { throw "Malformed checksum line in ${ChecksumPath}: $line" }
        $name = $match.Groups[2].Value.Trim()
        if ([System.IO.Path]::GetFileName($name) -ne $name) { throw "Checksum entry must be a basename only: $name" }
        if ($result.ContainsKey($name)) { throw "Duplicate checksum entry: $name" }
        $result[$name] = $match.Groups[1].Value.ToLowerInvariant()
    }
    return $result
}

function Assert-Checksums([string]$Directory, [string]$ChecksumPath, [string[]]$RequiredNames) {
    $expected = Read-ChecksumFile $ChecksumPath
    foreach ($name in $RequiredNames) {
        if (-not $expected.ContainsKey($name)) { throw "Checksum file is missing required entry: $name" }
        $path = Join-Path $Directory $name
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Checksum target is missing: $path" }
        $actual = Get-Sha256 $path
        if ($actual -ne $expected[$name]) { throw "SHA-256 mismatch for $name. Expected $($expected[$name]); got $actual" }
    }
    foreach ($name in $expected.Keys) {
        if ($RequiredNames -notcontains $name) { throw "Checksum file contains unexpected entry: $name" }
    }
}

function Assert-JarEntries([string]$JarPath, [string[]]$RequiredEntries) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $names = @{}
        foreach ($entry in $archive.Entries) { $names[$entry.FullName] = $true }
        foreach ($required in $RequiredEntries) {
            if (-not $names.ContainsKey($required)) { throw "Required JAR entry '$required' is missing from $JarPath" }
        }
    } finally { $archive.Dispose() }
}

function Assert-NoSensitiveArtifactLeakage([string]$JarPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $privateNamePattern = '(^|/)(proxy-assertion\.key|guardian-server-auth\.key|cerberus-release-signing\.key|[^/]+\.(pem|key|p8|pk8))$'
    $textEntryPattern = '(?i)(^|/)(plugin\.yml|fabric\.mod\.json|velocity-plugin\.json|[^/]+\.(yml|yaml|json|properties|txt|md|xml|mf|license))$'
    $localPathMarkers = @('/mnt/data/', '/home/', '/Users/', 'C:\Users\', 'C:/Users/')
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match $privateNamePattern) { throw "Private-key-like resource name found in ${JarPath}: $($entry.FullName)" }
            if ($entry.FullName -notmatch $textEntryPattern -or $entry.Length -le 0 -or $entry.Length -gt 1048576) { continue }
            $stream = $entry.Open()
            try {
                $reader = [System.IO.StreamReader]::new($stream, [System.Text.UTF8Encoding]::new($false, $false), $true, 4096, $true)
                try {
                    $text = $reader.ReadToEnd()
                    if ($text -match '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----') {
                        throw "PEM private-key material found in $JarPath entry $($entry.FullName)"
                    }
                    foreach ($marker in $localPathMarkers) {
                        if ($text.IndexOf($marker, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
                            throw "Machine-local build path marker '$marker' found in $JarPath entry $($entry.FullName)"
                        }
                    }
                } finally { $reader.Dispose() }
            } finally { $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
}

function Write-Checksums([string]$Directory, [string[]]$Names, [string]$OutputPath) {
    $lines = foreach ($name in ($Names | Sort-Object)) {
        $path = Join-Path $Directory $name
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Cannot checksum missing file: $path" }
        "$(Get-Sha256 $path)  $name"
    }
    [System.IO.File]::WriteAllLines($OutputPath, $lines, [System.Text.UTF8Encoding]::new($false))
}

function Verify-FinalRelease([string]$Directory, [string]$ReleaseVersion, [string]$PublicKey, [string]$ServerTrust, [string]$ToolJar) {
    $paperName = "guardian-paper-$ReleaseVersion.jar"
    $velocityName = "guardian-velocity-$ReleaseVersion.jar"
    $cerberusName = "cerberus-fabric-$ReleaseVersion-signed.jar"
    $required = @($paperName, $velocityName, $cerberusName, 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'RELEASE_PROVENANCE.txt', 'SHA256SUMS.txt')
    Assert-ExactDirectoryFiles $Directory $required 'Final release directory'

    $checksumNames = @($required | Where-Object { $_ -ne 'SHA256SUMS.txt' })
    Assert-Checksums $Directory (Join-Path $Directory 'SHA256SUMS.txt') $checksumNames

    $paper = Join-Path $Directory $paperName
    $velocity = Join-Path $Directory $velocityName
    $cerberus = Join-Path $Directory $cerberusName
    Assert-JarVersion $paper 'paper' $ReleaseVersion
    Assert-JarVersion $velocity 'velocity' $ReleaseVersion
    Assert-JarVersion $cerberus 'cerberus' $ReleaseVersion

    Assert-JarEntries $paper @('META-INF/LICENSE-GPL-3.0.txt','META-INF/THIRD-PARTY-NOTICES.md','META-INF/licenses/Apache-2.0.txt')
    Assert-JarEntries $velocity @('META-INF/LICENSE-GPL-3.0.txt','META-INF/THIRD-PARTY-NOTICES.md','META-INF/licenses/Apache-2.0.txt')
    Assert-JarEntries $cerberus @('META-INF/LICENSE-GPL-3.0.txt','META-INF/THIRD-PARTY-NOTICES.md','META-INF/guardian/cerberus-release.bin','assets/cerberus/icon.png')
    Assert-NoSensitiveArtifactLeakage $paper
    Assert-NoSensitiveArtifactLeakage $velocity
    Assert-NoSensitiveArtifactLeakage $cerberus

    $provenancePath = Join-Path $Directory 'RELEASE_PROVENANCE.txt'
    $provenance = Read-KeyValueFile $provenancePath
    $requiredProvenance = @(
        'version','source_commit','repository','workflow_run_id','workflow_run_attempt',
        'release_input_manifest_sha256','release_input_checksums_sha256','unsigned_cerberus_sha256','signed_cerberus_sha256',
        'paper_sha256','velocity_sha256','release_tool_sha256','release_public_key_sha256','server_auth_trust_sha256'
    )
    foreach ($key in $requiredProvenance) {
        if (-not $provenance.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$provenance[$key])) {
            throw "Release provenance is missing required key '$key'"
        }
    }
    if ($provenance.Count -ne $requiredProvenance.Count) { throw 'Release provenance contains unexpected keys.' }
    if ([string]$provenance.version -ne $ReleaseVersion) { throw "Release provenance version does not match '$ReleaseVersion'." }
    if ([string]$provenance.source_commit -notmatch '^[0-9a-fA-F]{40}$') { throw 'Release provenance source_commit is invalid.' }
    if ([string]$provenance.repository -notmatch '^[^/\s]+/[^/\s]+$') { throw 'Release provenance repository is invalid.' }
    if ([string]$provenance.workflow_run_id -notmatch '^[0-9]+$' -or [string]$provenance.workflow_run_attempt -notmatch '^[0-9]+$') {
        throw 'Release provenance workflow run identity is invalid.'
    }
    foreach ($key in @('release_input_manifest_sha256','release_input_checksums_sha256','unsigned_cerberus_sha256','signed_cerberus_sha256','paper_sha256','velocity_sha256','release_tool_sha256','release_public_key_sha256')) {
        if ([string]$provenance[$key] -notmatch '^[0-9a-f]{64}$') { throw "Release provenance $key is not a lowercase SHA-256." }
    }
    if ([string]$provenance.signed_cerberus_sha256 -ne (Get-Sha256 $cerberus)) { throw 'Release provenance signed Cerberus hash does not match final artifact.' }
    if ([string]$provenance.paper_sha256 -ne (Get-Sha256 $paper)) { throw 'Release provenance Paper hash does not match final artifact.' }
    if ([string]$provenance.velocity_sha256 -ne (Get-Sha256 $velocity)) { throw 'Release provenance Velocity hash does not match final artifact.' }
    if ([string]$provenance.release_tool_sha256 -ne (Get-Sha256 $ToolJar)) { throw 'Release provenance release-tool hash does not match supplied CI release-tool JAR.' }
    if ([string]$provenance.release_public_key_sha256 -ne (Get-Sha256 $PublicKey)) { throw 'Release provenance release-public-key hash does not match supplied verification key.' }
    if ($null -eq $ServerTrust) {
        if ([string]$provenance.server_auth_trust_sha256 -ne 'none') { throw 'Release provenance expects Guardian server-auth trust anchors, but no trust file was supplied.' }
    } else {
        if ([string]$provenance.server_auth_trust_sha256 -notmatch '^[0-9a-f]{64}$') { throw 'Release provenance server_auth_trust_sha256 is invalid.' }
        if ([string]$provenance.server_auth_trust_sha256 -ne (Get-Sha256 $ServerTrust)) { throw 'Release provenance server-auth trust hash does not match supplied trust file.' }
    }

    $verifyArgs = @($cerberus, $ReleaseVersion, $PublicKey)
    if (-not [string]::IsNullOrWhiteSpace($ServerTrust)) { $verifyArgs += $ServerTrust }
    Invoke-ReleaseTool $ToolJar 'com.badwolfmc.cerberus.release.CerberusReleaseVerifier' $verifyArgs

    Write-Host "Release verification passed: $Directory"
}

Push-Location $Root
try {
    switch ($Action) {
        'generate-release-key' {
            $out = Require-PathArgument $OutputDirectory 'OutputDirectory'
            Invoke-GuardianGradle @(':cerberus-fabric:generateCerberusReleaseIdentity', "-PcerberusReleaseIdentityDirectory=$out")
        }
        'generate-server-identity' {
            $out = Require-PathArgument $OutputDirectory 'OutputDirectory'
            Invoke-GuardianGradle @(':cerberus-fabric:generateGuardianServerIdentity', "-PguardianServerIdentityDirectory=$out")
        }
        'sign-cerberus' {
            $key = Require-ExistingFile $ReleasePrivateKey 'ReleasePrivateKey'
            $unsigned = Require-ExistingFile $UnsignedCerberusJar 'UnsignedCerberusJar'
            $releaseVersion = Require-Version $Version
            if (-not [string]::IsNullOrWhiteSpace($SignedOutput) -and -not [string]::IsNullOrWhiteSpace($OutputDirectory)) {
                throw "Use either SignedOutput or OutputDirectory for action '$Action', not both."
            }
            if (-not [string]::IsNullOrWhiteSpace($SignedOutput)) {
                $signed = Require-PathArgument $SignedOutput 'SignedOutput'
                $signedName = [System.IO.Path]::GetFileName($signed)
                if ($signedName -notlike "*$releaseVersion*") { throw "SignedOutput filename must include release version '$releaseVersion'." }
            } else {
                $out = Require-PathArgument $OutputDirectory 'OutputDirectory'
                [System.IO.Directory]::CreateDirectory($out) | Out-Null
                $signed = Join-Path $out "cerberus-fabric-$releaseVersion-signed.jar"
            }
            if (Test-Path -LiteralPath $signed) { throw "Refusing to overwrite existing signed Cerberus output: $signed" }
            Assert-JarVersion $unsigned 'cerberus' $releaseVersion
            $tool = Require-ExistingFile $ReleaseToolJar 'ReleaseToolJar'
            $signArgs = @($unsigned, $signed, $key, $releaseVersion)
            if (-not [string]::IsNullOrWhiteSpace($GuardianServerPublicKeys)) {
                $trust = Require-ExistingFile $GuardianServerPublicKeys 'GuardianServerPublicKeys'
                $signArgs += $trust
            }
            Invoke-ReleaseTool $tool 'com.badwolfmc.cerberus.release.CerberusReleaseSigner' $signArgs
            Write-Host "Signed JAR: $signed"
            Write-Host "Finished JAR SHA-256: $(Get-Sha256 $signed)"
            Write-Host 'Note: this finished-file hash is distinct from the canonical SHA-256 printed by the signer.'
        }
        'checksums' {
            $dir = Require-ExistingDirectory $ArtifactDirectory 'ArtifactDirectory'
            $files = @(Get-ChildItem -LiteralPath $dir -File -Filter '*.jar' | Sort-Object Name)
            if ($files.Count -eq 0) { throw "No JAR files found in $dir" }
            Write-Checksums $dir @($files | ForEach-Object { $_.Name }) (Join-Path $dir 'SHA256SUMS.txt')
            Write-Host "Wrote $(Join-Path $dir 'SHA256SUMS.txt')"
        }
        'finalize-release' {
            $releaseVersion = Require-Version $Version
            $input = Require-ExistingDirectory $InputDirectory 'InputDirectory'
            $out = Require-PathArgument $OutputDirectory 'OutputDirectory'
            $privateKey = Require-ExistingFile $ReleasePrivateKey 'ReleasePrivateKey'
            $publicKey = Require-ExistingFile $ReleasePublicKey 'ReleasePublicKey'
            $trust = $null
            if (-not [string]::IsNullOrWhiteSpace($GuardianServerPublicKeys)) { $trust = Require-ExistingFile $GuardianServerPublicKeys 'GuardianServerPublicKeys' }
            Ensure-EmptyDirectory $out

            $paperName = "guardian-paper-$releaseVersion.jar"
            $velocityName = "guardian-velocity-$releaseVersion.jar"
            $unsignedName = "cerberus-fabric-$releaseVersion-unsigned.jar"
            $toolName = "cerberus-release-tools-$releaseVersion.jar"
            $candidateRequired = @($paperName, $velocityName, $unsignedName, $toolName, 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'RELEASE_INPUT.json')
            $candidateAll = @($candidateRequired + 'SHA256SUMS-CI.txt')
            $manifestPath = Join-Path $input 'RELEASE_INPUT.json'
            $checksumPath = Join-Path $input 'SHA256SUMS-CI.txt'
            if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Release input is missing RELEASE_INPUT.json" }
            if (-not (Test-Path -LiteralPath $checksumPath -PathType Leaf)) { throw "Release input is missing SHA256SUMS-CI.txt" }
            $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if ([int]$manifest.schema -ne 1) { throw "Unsupported RELEASE_INPUT.json schema: $($manifest.schema)" }
            if ([string]$manifest.version -ne $releaseVersion) { throw "Release input version '$($manifest.version)' does not match requested version '$releaseVersion'" }
            if ([string]$manifest.sourceCommit -notmatch '^[0-9a-fA-F]{40}$') { throw 'Release input sourceCommit is not a 40-character Git SHA.' }
            if ([string]$manifest.repository -notmatch '^[^/\s]+/[^/\s]+$') { throw 'Release input repository is invalid.' }
            if ([string]$manifest.workflowRunId -notmatch '^[0-9]+$') { throw 'Release input workflowRunId is invalid.' }
            if ([string]$manifest.workflowRunAttempt -notmatch '^[0-9]+$') { throw 'Release input workflowRunAttempt is invalid.' }
            Assert-ExactDirectoryFiles $input $candidateAll 'Release input directory'
            Assert-Checksums $input $checksumPath $candidateRequired

            $paperInput = Join-Path $input $paperName
            $velocityInput = Join-Path $input $velocityName
            $unsignedInput = Join-Path $input $unsignedName
            $toolInput = Join-Path $input $toolName
            Assert-JarVersion $paperInput 'paper' $releaseVersion
            Assert-JarVersion $velocityInput 'velocity' $releaseVersion
            Assert-JarVersion $unsignedInput 'cerberus' $releaseVersion

            $paperOutput = Join-Path $out $paperName
            $velocityOutput = Join-Path $out $velocityName
            Copy-Item -LiteralPath $paperInput -Destination $paperOutput
            Copy-Item -LiteralPath $velocityInput -Destination $velocityOutput
            Copy-Item -LiteralPath (Join-Path $input 'LICENSE') -Destination (Join-Path $out 'LICENSE')
            Copy-Item -LiteralPath (Join-Path $input 'THIRD_PARTY_NOTICES.md') -Destination (Join-Path $out 'THIRD_PARTY_NOTICES.md')
            if ((Get-Sha256 $paperOutput) -ne (Get-Sha256 $paperInput)) { throw 'Final Paper artifact is not byte-identical to the checked CI input.' }
            if ((Get-Sha256 $velocityOutput) -ne (Get-Sha256 $velocityInput)) { throw 'Final Velocity artifact is not byte-identical to the checked CI input.' }

            $signedName = "cerberus-fabric-$releaseVersion-signed.jar"
            $signed = Join-Path $out $signedName
            $signArgs = @($unsignedInput, $signed, $privateKey, $releaseVersion)
            if ($null -ne $trust) { $signArgs += $trust }
            Invoke-ReleaseTool $toolInput 'com.badwolfmc.cerberus.release.CerberusReleaseSigner' $signArgs

            $provenanceLines = @(
                "version=$releaseVersion",
                "source_commit=$($manifest.sourceCommit)",
                "repository=$($manifest.repository)",
                "workflow_run_id=$($manifest.workflowRunId)",
                "workflow_run_attempt=$($manifest.workflowRunAttempt)",
                "release_input_manifest_sha256=$(Get-Sha256 $manifestPath)",
                "release_input_checksums_sha256=$(Get-Sha256 $checksumPath)",
                "unsigned_cerberus_sha256=$(Get-Sha256 $unsignedInput)",
                "signed_cerberus_sha256=$(Get-Sha256 $signed)",
                "paper_sha256=$(Get-Sha256 (Join-Path $out $paperName))",
                "velocity_sha256=$(Get-Sha256 (Join-Path $out $velocityName))",
                "release_tool_sha256=$(Get-Sha256 $toolInput)",
                "release_public_key_sha256=$(Get-Sha256 $publicKey)",
                "server_auth_trust_sha256=$(if ($null -eq $trust) { 'none' } else { Get-Sha256 $trust })"
            )
            [System.IO.File]::WriteAllLines((Join-Path $out 'RELEASE_PROVENANCE.txt'), $provenanceLines, [System.Text.UTF8Encoding]::new($false))
            $finalChecksumNames = @($paperName, $velocityName, $signedName, 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'RELEASE_PROVENANCE.txt')
            Write-Checksums $out $finalChecksumNames (Join-Path $out 'SHA256SUMS.txt')
            Verify-FinalRelease $out $releaseVersion $publicKey $trust $toolInput
            Write-Host 'READY TO UPLOAD: final release directory contains only publishable artifacts.'
        }
        'verify-release' {
            $releaseVersion = Require-Version $Version
            $dir = Require-ExistingDirectory $ArtifactDirectory 'ArtifactDirectory'
            $publicKey = Require-ExistingFile $ReleasePublicKey 'ReleasePublicKey'
            $tool = Require-ExistingFile $ReleaseToolJar 'ReleaseToolJar'
            $trust = $null
            if (-not [string]::IsNullOrWhiteSpace($GuardianServerPublicKeys)) { $trust = Require-ExistingFile $GuardianServerPublicKeys 'GuardianServerPublicKeys' }
            Verify-FinalRelease $dir $releaseVersion $publicKey $trust $tool
        }
    }
} finally {
    Pop-Location
}
