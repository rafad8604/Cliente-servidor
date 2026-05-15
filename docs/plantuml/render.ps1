# Genera PNG/SVG leyendo los .puml como UTF-8 (evita tildes rotas en Windows con charset por defecto CP1252).
# Uso: .\render.ps1          -> PNG
#       .\render.ps1 svg     -> SVG
# Vista previa en VS Code/Cursor: plantuml.commandArgs: ["-charset","UTF-8"]
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jar = Join-Path $root ".tools\plantuml-1.2024.7.jar"
if (-not (Test-Path $jar)) {
    Write-Host "Descargando PlantUML con Maven..."
    $repoRoot = (Resolve-Path (Join-Path $root "..\..")).Path
    $pom = Join-Path $repoRoot "pom.xml"
    mvn -q -f $pom dependency:copy `
        "-Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7" `
        "-DoutputDirectory=$root\.tools"
    $found = Get-ChildItem (Join-Path $root ".tools") -Filter "plantuml*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $jar = $found.FullName }
}
if (-not (Test-Path $jar)) {
    Write-Error "No se encontro plantuml jar en .tools. Ejecuta desde la raiz del repo: mvn dependency:copy ""-Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7"" ""-DoutputDirectory=docs/plantuml/.tools"""
    exit 1
}

$format = if ($args.Count -ge 1) { $args[0] } else { "png" }
$ext = if ($format -eq "svg") { "svg" } else { "png" }
Write-Host "Renderizando *.$ext con charset UTF-8..."
Get-ChildItem $root -Filter "*.puml" | ForEach-Object {
    java "-Dfile.encoding=UTF-8" -jar $jar "-charset" "UTF-8" "-t$ext" $_.FullName
}
