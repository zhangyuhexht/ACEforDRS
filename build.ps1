# Build ACEforDRS with the JDK directly (no Maven required).
# Usage: powershell -ExecutionPolicy Bypass -File .\build.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root 'build\classes'
$lib = Join-Path $root 'build\lib'

if (-not (Test-Path $lib)) {
    throw "Missing dependency directory: $lib"
}

if (Test-Path $classes) {
    Remove-Item $classes -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$classpath = (Get-ChildItem $lib -Filter '*.jar' | ForEach-Object { $_.FullName }) -join ';'
$sources = Get-ChildItem (Join-Path $root 'src\main\java\com\acefordrs') -Filter '*.java' |
    ForEach-Object { $_.FullName }

& javac -nowarn -encoding UTF-8 --release 21 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item (Join-Path $root 'src\main\resources\*') -Destination $classes -Force

$versionLine = Get-Content (Join-Path $root 'src\main\resources\plugin.yml') |
    Where-Object { $_ -match '^version:\s*(\S+)' } |
    Select-Object -First 1
$version = [regex]::Match($versionLine, '^version:\s*(\S+)').Groups[1].Value
if ([string]::IsNullOrWhiteSpace($version)) {
    throw 'Could not read version from plugin.yml'
}

$jarPath = Join-Path $root "ACEforDRS-$version.jar"
if (Test-Path $jarPath) {
    Remove-Item $jarPath -Force
}

& jar --create --file $jarPath -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

$size = (Get-Item $jarPath).Length
Write-Host "Built $jarPath ($size bytes)"
