$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskLock = Get-Content -LiteralPath (Join-Path $taskRoot 'dependencies.lock.json') -Raw | ConvertFrom-Json
$taskLibs = Join-Path $taskRoot 'libs'
New-Item -ItemType Directory -Force -Path $taskLibs | Out-Null
foreach ($taskArtifact in $taskLock.artifacts) {
    if ([IO.Path]::GetFileName($taskArtifact.file) -ne $taskArtifact.file) { throw 'Invalid dependency filename' }
    $taskTarget = Join-Path $taskLibs $taskArtifact.file
    if ((Test-Path -LiteralPath $taskTarget) -and
        (Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -eq $taskArtifact.sha256) {
        Write-Host "Verified $($taskArtifact.file)"
        continue
    }
    $taskTemporary = "$taskTarget.download"
    Invoke-WebRequest -Uri $taskArtifact.url -OutFile $taskTemporary
    if ((Get-FileHash -LiteralPath $taskTemporary -Algorithm SHA256).Hash -ne $taskArtifact.sha256) {
        throw "SHA256 mismatch: $($taskArtifact.file). Download retained for inspection."
    }
    Move-Item -LiteralPath $taskTemporary -Destination $taskTarget -Force
    Write-Host "Downloaded and verified $($taskArtifact.file)"
}
