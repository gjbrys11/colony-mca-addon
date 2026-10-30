param([Parameter(Mandatory=$true)][string]$GameDirectory,[switch]$SkipDownload,[switch]$ConfigOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$gameRoot=[IO.Path]::GetFullPath($GameDirectory)
if (!(Test-Path -LiteralPath $gameRoot -PathType Container)) { throw 'Game directory must already exist' }
$launcherFile=Join-Path $gameRoot ((Split-Path -Leaf $gameRoot)+'.json')
if(Test-Path -LiteralPath $launcherFile){
    $launcherText=[IO.File]::ReadAllText($launcherFile)
    $launcher=$launcherText | ConvertFrom-Json
    $minecraftVersion=if($launcher.clientVersion){$launcher.clientVersion}elseif($launcher.inheritsFrom){$launcher.inheritsFrom}else{$null}
    if(($minecraftVersion -and $minecraftVersion -ne '1.21.1') -or $launcherText -match 'net\.minecraftforge:forge:'){
        throw 'This profile requires an existing Minecraft 1.21.1 NeoForge instance. No files were changed.'
    }
}
if (!$SkipDownload) { & (Join-Path $PSScriptRoot 'Fetch-Dependencies.ps1') }
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$backup=Join-Path $gameRoot "colony-mca-profile-backups/$stamp"
function Save-Original([string]$Path) {
    if(Test-Path -LiteralPath $Path) {
        $relative=[IO.Path]::GetRelativePath($gameRoot,$Path)
        $destination=Join-Path $backup $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        Copy-Item -LiteralPath $Path -Destination $destination
    }
}
function Set-Settings([string]$Relative,$Settings) {
    $target=Join-Path $gameRoot $Relative
    Save-Original $target
    $text=if(Test-Path -LiteralPath $target){[IO.File]::ReadAllText($target)}else{''}
    foreach($setting in $Settings) {
        $section='['+(($setting.section | ForEach-Object {'"'+$_+'"'}) -join '.')+']'
        # Existing files may use unquoted ASCII section names. Compare the parsed path text.
        $sectionParts=@($setting.section)
        $lines=[Collections.Generic.List[string]]::new()
        foreach($line in ($text -split '\r?\n')){$lines.Add($line)}
        $start=-1;$end=$lines.Count
        for($i=0;$i -lt $lines.Count;$i++) {
            if($lines[$i].Trim() -match '^\[(.+)\]$') {
                $candidate=$Matches[1] -replace '"',''
                if($candidate -eq ($sectionParts -join '.')){$start=$i;break}
            }
        }
        if($start -lt 0){$lines.Add('');$lines.Add($section);$start=$lines.Count-1}
        $end=$lines.Count
        for($i=$start+1;$i -lt $lines.Count;$i++){if($lines[$i].Trim().StartsWith('[')){$end=$i;break}}
        $value=if($setting.value -is [bool]){$setting.value.ToString().ToLowerInvariant()}elseif($setting.value -is [string]){'"'+$setting.value+'"'}else{[Convert]::ToString($setting.value,[Globalization.CultureInfo]::InvariantCulture)}
        $replacement=$setting.key+' = '+$value;$found=$false
        for($i=$start+1;$i -lt $end;$i++){if($lines[$i] -match ('^\s*'+[regex]::Escape($setting.key)+'\s*=')){$lines[$i]=$replacement;$found=$true;break}}
        if(!$found){$lines.Insert($end,$replacement)}
        $text=$lines -join "`n"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    [IO.File]::WriteAllText($target,$text,[Text.UTF8Encoding]::new($false))
}
$lock=Get-Content -LiteralPath (Join-Path $projectRoot 'dependencies.lock.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path (Join-Path $gameRoot 'mods') | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-ModIds([string]$Path) {
    $archive=[IO.Compression.ZipFile]::OpenRead($Path)
    try{
        $entry=$archive.GetEntry('META-INF/neoforge.mods.toml')
        if($null -eq $entry){$entry=$archive.GetEntry('META-INF/mods.toml')}
        if($null -eq $entry){return @()}
        $reader=[IO.StreamReader]::new($entry.Open())
        try{$metadata=$reader.ReadToEnd()}finally{$reader.Dispose()}
        foreach($block in [regex]::Matches($metadata,'(?ms)^\s*\[\[mods\]\](.*?)(?=^\s*\[\[|\z)')) {
            if($block.Groups[1].Value -match '(?m)^\s*modId\s*=\s*"([^\"]+)"'){$Matches[1]}
        }
    }finally{$archive.Dispose()}
}
if(!$ConfigOnly){
    $expected=@{}
    foreach($artifact in $lock.artifacts){
        $source=Join-Path $projectRoot "libs/$($artifact.file)"
        if(!(Test-Path -LiteralPath $source) -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $artifact.sha256){throw "Missing or invalid dependency: $($artifact.file)"}
        foreach($id in Get-ModIds $source){$expected[$id]=$artifact.file}
    }
    $ownJar=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'mods') -Filter 'colony-mca-addon-*.jar' -ErrorAction SilentlyContinue)
    if(!$ownJar.Count){$ownJar=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter 'colony-mca-addon-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1)}
    if($ownJar.Count -ne 1){throw 'Expected one addon JAR in package mods/ or latest build/libs/'}
    $expected['colony_mca']=$ownJar[0].Name
    foreach($existing in Get-ChildItem -LiteralPath (Join-Path $gameRoot 'mods') -Filter '*.jar'){
        foreach($id in Get-ModIds $existing.FullName){
            if($expected.ContainsKey($id) -and $existing.Name -ne $expected[$id]){
                $destination=Join-Path $backup ('mods/'+$existing.Name)
                New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
                Move-Item -LiteralPath $existing.FullName -Destination $destination
                break
            }
        }
    }
    $target=Join-Path $gameRoot ('mods/'+$ownJar[0].Name)
    if($ownJar[0].FullName -ne $target){Save-Original $target;Copy-Item -LiteralPath $ownJar[0].FullName -Destination $target}
}
foreach($artifact in $(if($ConfigOnly){@()}else{$lock.artifacts})) {
    $source=Join-Path $projectRoot "libs/$($artifact.file)"
    if(!(Test-Path -LiteralPath $source) -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $artifact.sha256){throw "Missing or invalid dependency: $($artifact.file)"}
    $target=Join-Path $gameRoot "mods/$($artifact.file)"
    if((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $artifact.sha256){continue}
    Save-Original $target;Copy-Item -LiteralPath $source -Destination $target
}
$settings=Get-Content -LiteralPath (Join-Path $projectRoot 'profile/settings.json') -Raw | ConvertFrom-Json
foreach($property in $settings.PSObject.Properties){Set-Settings $property.Name $property.Value}
# Apply the same scoped server settings to existing worlds, without copying other pack rules.
$worldConfigs=@()
if(Test-Path -LiteralPath (Join-Path $gameRoot 'saves')){
    $worldConfigs+=Get-ChildItem -LiteralPath (Join-Path $gameRoot 'saves') -Directory | ForEach-Object {Join-Path $_.FullName 'serverconfig/minecolonies_tweaks-server.toml'}
}
foreach($directory in Get-ChildItem -LiteralPath $gameRoot -Directory){
    if(Test-Path -LiteralPath (Join-Path $directory.FullName 'level.dat')){$worldConfigs+=Join-Path $directory.FullName 'serverconfig/minecolonies_tweaks-server.toml'}
}
foreach($file in $worldConfigs | Select-Object -Unique){Set-Settings ([IO.Path]::GetRelativePath($gameRoot,$file)) $settings.'config/minecolonies_tweaks-server.toml'}
# Preseed the author's startup scripts before the first JVM initializes the item registry.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $projectRoot 'libs/material_unification-neoforge-1.21.1-3.4.1.jar'))
try {
    $mapping=@{
        'startup_scripts/item_registration.js'='startup_scripts/material_unification_items.js'
        'server_scripts/global_replace.js'='server_scripts/material_unification_global.js'
        'server_scripts/migration_notice.js'='server_scripts/material_unification_migration_notice.js'
        'server_scripts/tooltips.js'='server_scripts/material_unification_tooltips.js'
        'client_scripts/jei_integration.js'='client_scripts/material_unification_jei.js'
    }
    foreach($entry in $mapping.GetEnumerator()){
        $item=$zip.GetEntry('assets/material_unification/kubejs/'+$entry.Key)
        if($null -eq $item){throw "Missing author script: $($entry.Key)"}
        $target=Join-Path $gameRoot ('kubejs/'+$entry.Value)
        Save-Original $target;New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($item,$target,$true)
    }
}finally{$zip.Dispose()}
Write-Host "Gameplay profile installed: $gameRoot"
Write-Host "Previous files backed up under: $backup"
