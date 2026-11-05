param(
    [string]$Request = 'examples/death-storm-request.json',
    [string]$PetProfiles = 'examples/pet-profiles.json',
    [string]$ResourceCapture = 'target/pet-resources-20261102',
    [string]$StagingDatabase = 'target/wizard101/staging.sqlite',
    [string]$Database = 'target/wizard101/optimizer.sqlite',
    [string]$Output = 'target/wizard101/death-storm-expanded-results.json',
    [string]$JavaPath = ''
)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    foreach ($inputPath in @($Request, $PetProfiles, $StagingDatabase, $Database,
        (Join-Path $ResourceCapture 'capture.json'), (Join-Path $ResourceCapture 'resources.ndjson'))) {
        if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
            throw "Missing input: $inputPath. Follow docs/PINS_AND_PETS.md for the offline capture/build steps."
        }
    }
    $petCatalog = 'target/wizard101/pet-catalog.json'
    & python 'bridge/build_pet_catalog.py' '--staging' $StagingDatabase '--resource-capture' $ResourceCapture `
        '--profiles' $PetProfiles '--output' $petCatalog '--replace'
    if ($LASTEXITCODE -ne 0) { throw 'Could not build pet configurations and star jewels.' }
    $requestSettings = Get-Content -LiteralPath $Request -Raw | ConvertFrom-Json
    if ($requestSettings.petSearch.mode -eq 'AUTO') {
        Write-Host 'Searching pins and automatic five-talent pets from the configured pool, with separate star jewels.'
    } else {
        Write-Host 'Searching pins and predefined pet configurations, with separate star jewels.'
    }
    & (Join-Path $PSScriptRoot 'Run-MockOptimizer.ps1') -Request $Request -Database $Database `
        -Output $Output -JavaPath $JavaPath -IncludePins -PetCatalog $petCatalog `
        -CatalogOutput 'target/wizard101/death-storm-expanded-catalog.json'
} finally {
    Pop-Location
}
