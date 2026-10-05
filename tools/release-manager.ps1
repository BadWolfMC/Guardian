[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('generate-release-key','generate-server-identity','sign-cerberus','checksums')]
    [string]$Action,
    [string]$OutputDirectory,
    [string]$ReleasePrivateKey,
    [string]$GuardianServerPublicKeys,
    [string]$SignedOutput,
    [string]$Version,
    [string]$ArtifactDirectory = 'release-artifacts'
)
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Gradle=Join-Path $Root 'gradlew.bat'
function Require-PathArgument([string]$Value,[string]$Name){ if([string]::IsNullOrWhiteSpace($Value)){throw "$Name is required for action '$Action'."}; return [System.IO.Path]::GetFullPath($Value) }
function Require-Version([string]$Value){
    if([string]::IsNullOrWhiteSpace($Value)){throw "Version is required for action '$Action'."}
    if($Value -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$'){throw 'Version must match ^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$'}
    return $Value
}
Push-Location $Root
try {
    switch($Action){
        'generate-release-key' { $out=Require-PathArgument $OutputDirectory 'OutputDirectory'; & $Gradle ':cerberus-fabric:generateCerberusReleaseIdentity' "-PcerberusReleaseIdentityDirectory=$out"; if($LASTEXITCODE -ne 0){throw "Gradle failed with exit code $LASTEXITCODE"} }
        'generate-server-identity' { $out=Require-PathArgument $OutputDirectory 'OutputDirectory'; & $Gradle ':cerberus-fabric:generateGuardianServerIdentity' "-PguardianServerIdentityDirectory=$out"; if($LASTEXITCODE -ne 0){throw "Gradle failed with exit code $LASTEXITCODE"} }
        'sign-cerberus' {
            $key=Require-PathArgument $ReleasePrivateKey 'ReleasePrivateKey'; $releaseVersion=Require-Version $Version
            if(-not [string]::IsNullOrWhiteSpace($SignedOutput) -and -not [string]::IsNullOrWhiteSpace($OutputDirectory)){
                throw "Use either SignedOutput or OutputDirectory for action '$Action', not both."
            }
            if(-not [string]::IsNullOrWhiteSpace($SignedOutput)){
                $signed=Require-PathArgument $SignedOutput 'SignedOutput'
                $signedName=[System.IO.Path]::GetFileName($signed)
                if($signedName -notlike "*$releaseVersion*"){
                    throw "SignedOutput filename must include release version '$releaseVersion'. Use -OutputDirectory to let the helper choose the versioned filename automatically."
                }
            } else {
                $out=Require-PathArgument $OutputDirectory 'OutputDirectory'
                [System.IO.Directory]::CreateDirectory($out) | Out-Null
                $signed=Join-Path $out "cerberus-fabric-$releaseVersion-signed.jar"
            }
            $gradleArgs=@(':cerberus-fabric:signCerberusRelease',"-PguardianVersion=$releaseVersion","-PcerberusReleasePrivateKey=$key","-PcerberusSignedOutput=$signed")
            if(-not [string]::IsNullOrWhiteSpace($GuardianServerPublicKeys)){ $trust=[System.IO.Path]::GetFullPath($GuardianServerPublicKeys); $gradleArgs+="-PguardianServerAuthPublicKeys=$trust" }
            & $Gradle @gradleArgs; if($LASTEXITCODE -ne 0){throw "Gradle failed with exit code $LASTEXITCODE"}
            $hash=(Get-FileHash -Algorithm SHA256 $signed).Hash.ToLowerInvariant(); Write-Host "Signed JAR: $signed"; Write-Host "Finished JAR SHA-256: $hash"; Write-Host 'Note: this finished-file hash is distinct from the canonical SHA-256 printed by the signer.'
        }
        'checksums' {
            $dir=Require-PathArgument $ArtifactDirectory 'ArtifactDirectory'; if(-not(Test-Path -LiteralPath $dir -PathType Container)){throw "ArtifactDirectory does not exist: $dir"}
            $files=Get-ChildItem -LiteralPath $dir -File -Filter '*.jar'|Sort-Object Name; if($files.Count -eq 0){throw "No JAR files found in $dir"}
            $lines=foreach($file in $files){$hash=(Get-FileHash -Algorithm SHA256 $file.FullName).Hash.ToLowerInvariant(); "$hash  $($file.Name)"}
            $checksumPath=Join-Path $dir 'SHA256SUMS.txt'; [System.IO.File]::WriteAllLines($checksumPath,$lines,[System.Text.UTF8Encoding]::new($false)); Write-Host "Wrote $checksumPath"
        }
    }
} finally { Pop-Location }
