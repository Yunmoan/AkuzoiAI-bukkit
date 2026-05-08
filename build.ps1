param(
    [string]$ApiJar = "",
    [string]$Version = "0.1.7"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$ResourcesDir = Join-Path $ProjectRoot "src\main\resources"
$SourceDir = Join-Path $ProjectRoot "src\main\java"
$LibsDir = Join-Path $BuildDir "libs"
$JarPath = Join-Path $LibsDir "AkuzoiAI-bukkit-$Version.jar"
$DependencyDir = Join-Path $BuildDir "dependencies"
$PaperVersion = "1.20.1-R0.1-SNAPSHOT"
$DefaultApiJar = Join-Path $DependencyDir "paper-api-$PaperVersion.jar"

function Test-CommandExists([string]$CommandName) {
    return $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

function Assert-CommandExists([string]$CommandName) {
    if (-not (Test-CommandExists $CommandName)) {
        throw "Command '$CommandName' was not found. Please install JDK 17+ and make sure it is in PATH."
    }
}

function Download-PaperApi([string]$TargetPath) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $TargetPath) | Out-Null
    $BaseUrl = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$PaperVersion"
    $MetadataUrl = "$BaseUrl/maven-metadata.xml"

    try {
        Write-Host "Resolving Paper API snapshot metadata..."
        [xml]$Metadata = (Invoke-WebRequest -Uri $MetadataUrl -UseBasicParsing).Content
        $SnapshotVersion = $Metadata.metadata.versioning.snapshotVersions.snapshotVersion |
            Where-Object { $_.extension -eq "jar" -and [string]::IsNullOrWhiteSpace($_.classifier) } |
            Select-Object -First 1

        if ($null -eq $SnapshotVersion -or [string]::IsNullOrWhiteSpace($SnapshotVersion.value)) {
            throw "No jar snapshotVersion found in metadata."
        }

        $JarUrl = "$BaseUrl/paper-api-$($SnapshotVersion.value).jar"
        Write-Host "Downloading Paper API from $JarUrl"
        Invoke-WebRequest -Uri $JarUrl -OutFile $TargetPath -UseBasicParsing
    } catch {
        throw "Could not download Paper API: $($_.Exception.Message). Re-run with -ApiJar C:\path\to\paper-api.jar"
    }

    if (-not (Test-Path $TargetPath) -or (Get-Item $TargetPath).Length -le 0) {
        throw "Downloaded Paper API jar is missing or empty."
    }
}

if (Test-CommandExists "gradle") {
    Write-Host "Using Gradle build..."
    & gradle build
    exit $LASTEXITCODE
}

if (Test-CommandExists "mvn") {
    Write-Host "Using Maven build..."
    & mvn package
    exit $LASTEXITCODE
}

Write-Host "Gradle and Maven were not found. Falling back to direct JDK compilation."
Assert-CommandExists "javac"
Assert-CommandExists "jar"

if (Test-Path $BuildDir) {
    Remove-Item $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Force -Path $LibsDir | Out-Null

if ([string]::IsNullOrWhiteSpace($ApiJar)) {
    $ApiJar = $DefaultApiJar
}

if (-not (Test-Path $ApiJar)) {
    Download-PaperApi $ApiJar
}

$Sources = Get-ChildItem -Path $SourceDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
if ($Sources.Count -eq 0) {
    throw "No Java source files found."
}

Write-Host "Compiling Java sources..."
& javac -encoding UTF-8 --release 17 -cp $ApiJar -d $ClassesDir @Sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE. Direct JDK compilation may need Paper API transitive dependencies. Please install Gradle or Maven and run .\build.ps1 again."
}

Write-Host "Copying resources..."
Copy-Item -Path (Join-Path $ResourcesDir "*") -Destination $ClassesDir -Recurse -Force
$PluginYml = Join-Path $ClassesDir "plugin.yml"
(Get-Content $PluginYml -Raw -Encoding UTF8).Replace('${version}', $Version) | Set-Content $PluginYml -Encoding UTF8

Write-Host "Creating jar..."
& jar --create --file $JarPath -C $ClassesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Write-Host "Built $JarPath"
