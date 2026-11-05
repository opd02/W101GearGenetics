param(
    [string]$Request = 'examples/wad-mock-request.json',
    [string]$Database = 'target/wizard101/optimizer.sqlite',
    [string]$Output = 'target/wizard101/mock-results.json',
    [string]$JavaPath = '',
    [switch]$IncludePins,
    [string]$PetCatalog = '',
    [string]$CatalogOutput = 'target/wizard101/mock-catalog.json',
    [ValidateSet('FILTERED', 'BROAD')]
    [string]$GearPool = 'FILTERED'
)

$ErrorActionPreference = 'Stop'
$runTimer = [System.Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $PSScriptRoot
try {
    $jarPath = 'target/GearGenetics-1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw 'Build the optimizer first with JDK 22+ and mvn package.'
    }
    $inputPaths = @($Request, $Database)
    if ($PetCatalog) { $inputPaths += $PetCatalog }
    foreach ($inputPath in $inputPaths) {
        if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
            throw "Missing input: $inputPath. See docs/WAD_CATALOG.md for extraction/import steps."
        }
    }
    if (-not $JavaPath) {
        $installedJdk = Join-Path $env:ProgramFiles 'Java/jdk-22/bin/java.exe'
        if (Test-Path -LiteralPath $installedJdk) {
            $JavaPath = $installedJdk
        } elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
            $JavaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
        } else {
            $JavaPath = (Get-Command java -ErrorAction Stop).Source
        }
    }
    Write-Host "Request: $Request"
    Write-Host "Source database: $Database"
    Write-Host "Gear pool: $GearPool"
    Write-Host 'Manual base overrides; provisional WAD records enabled. Check the request and result warnings.'
    $mockCatalog = $CatalogOutput
    $prepareArgs = @('bridge/prepare_mock_catalog.py', '--db', $Database, '--request', $Request, '--output', $mockCatalog)
    if ($IncludePins) { $prepareArgs += '--include-pins' }
    if ($GearPool -eq 'BROAD') { $prepareArgs += '--include-unresolved-gear' }
    if ($PetCatalog) { $prepareArgs += @('--pet-catalog', $PetCatalog) }
    & python @prepareArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the extracted mock catalog.' }
    Write-Host 'Preparing candidate gear and sockets before generation progress begins...'
    # The CLI sends progress to stderr; display it as ordinary console text.
    $ErrorActionPreference = 'Continue'
    & $JavaPath '-Xmx3g' '-jar' $jarPath 'optimize' '--request' $Request '--catalog' $mockCatalog '--output' $Output 2>&1 |
        ForEach-Object { Write-Host $_.ToString() }
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "Optimizer failed (exit $LASTEXITCODE). JDK 22+ is required." }
    $runTimer.Stop()
    Write-Host ('Completed in {0:N1} seconds.' -f $runTimer.Elapsed.TotalSeconds)
    Write-Host "Results saved to $Output"
} finally {
    Pop-Location
}
