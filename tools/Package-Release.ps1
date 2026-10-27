param([string]$Version = '0.2.1-dev')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$projectRoot = Split-Path $PSScriptRoot -Parent
$output = Join-Path $projectRoot "build/release/$Version"
$null = New-Item -ItemType Directory -Force -Path $output
$jarName = "colony-mca-addon-$Version.jar"
$jarPath = Join-Path $projectRoot "build/libs/$jarName"
if (!(Test-Path -LiteralPath $jarPath)) { throw "Build the mod first: $jarPath" }
Copy-Item -LiteralPath $jarPath -Destination $output

function Write-Zip([string]$Path, [object[]]$Entries) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Create)
    try {
        $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($entry in $Entries) {
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $entry.Source, $entry.Name) | Out-Null
            }
        } finally { $zip.Dispose() }
    } finally { $stream.Dispose() }
}

$sources = @()
foreach ($directory in @('src', 'gradle', 'tools')) {
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -Recurse -File) {
        $sources += @{ Source=$file.FullName; Name=[IO.Path]::GetRelativePath($projectRoot, $file.FullName).Replace('\','/') }
    }
}
foreach ($name in @('.gitignore', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'dependencies.lock.json', 'LICENSE', 'README.md', 'CHANGELOG.md')) {
    $sources += @{ Source=(Join-Path $projectRoot $name); Name=$name }
}
Write-Zip (Join-Path $output "colony-mca-addon-$Version-sources.zip") $sources
$runtime = @(@{ Source=$jarPath; Name="mods/$jarName" })
foreach ($name in @('README.md', 'CHANGELOG.md', 'LICENSE', 'dependencies.lock.json')) {
    $runtime += @{ Source=(Join-Path $projectRoot $name); Name=$name }
}
Write-Zip (Join-Path $output "colony-mca-addon-$Version.zip") $runtime
$hashes = Get-ChildItem -LiteralPath $output -File | Where-Object { $_.Extension -in '.jar','.zip' } | ForEach-Object {
    "{0}  {1}" -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name
}
[IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS.txt'), [string[]]$hashes)
Get-ChildItem -LiteralPath $output | Select-Object Name, Length
