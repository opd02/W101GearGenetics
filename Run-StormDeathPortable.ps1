param(
    [string]$Request = 'examples/death-storm-request.json',
    [string]$Output = 'target/wizard101/storm-death-portable-results.json',
    [string]$JavaPath = '',
    [string]$JavaHeap = '3g'
)

$ErrorActionPreference = 'Stop'
$runTimer = [System.Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $PSScriptRoot
try {
    $bundle = Join-Path $PSScriptRoot 'runtime/storm-death'
    $manifestPath = Join-Path $bundle 'manifest.json'
    $jarPath = Join-Path $bundle 'optimizer.jar'
    $compressedPath = Join-Path $bundle 'catalog.json.gz'
    foreach ($inputPath in @($Request, $manifestPath, $jarPath, $compressedPath)) {
        if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
            throw "Missing portable input: $inputPath. Pull the runtime/storm-death folder with the repository."
        }
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1) { throw 'Unsupported portable runtime manifest.' }
    $settings = Get-Content -LiteralPath $Request -Raw -Encoding UTF8 | ConvertFrom-Json
    $requestPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Request)
    if ($settings.wizard.school -ne $manifest.coverage.school -or
        $settings.wizard.level -gt $manifest.coverage.maximumLevel) {
        throw "This catalog covers $($manifest.coverage.school) primary through level $($manifest.coverage.maximumLevel). Use the extraction launcher for another school or a higher level."
    }
    foreach ($slot in $settings.requiredSlots) {
        if ($slot -notin $manifest.coverage.slots) { throw "Slot $slot is outside this portable catalog's coverage." }
    }
    foreach ($name in @('optimizer.jar', 'catalog.json.gz')) {
        $actualHash = (Get-FileHash -LiteralPath (Join-Path $bundle $name) -Algorithm SHA256).Hash
        if ($actualHash -ne $manifest.files.$name.sha256) {
            throw "Portable file checksum mismatch: $name. Pull a matching runtime bundle."
        }
    }
    if (-not $JavaPath) {
        if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
            $JavaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
        } elseif (Get-Command java -ErrorAction SilentlyContinue) {
            $JavaPath = (Get-Command java).Source
        } elseif (Test-Path -LiteralPath 'C:/Program Files/Java/jdk-22/bin/java.exe') {
            $JavaPath = 'C:/Program Files/Java/jdk-22/bin/java.exe'
        } else {
            throw 'Install Java 22+ and set JAVA_HOME, add java to PATH, or pass -JavaPath.'
        }
    }
    # Java writes version and generation progress to stderr, including on success.
    $ErrorActionPreference = 'Continue'
    $javaVersion = & $JavaPath '-version' 2>&1
    $javaExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $versionText = ($javaVersion | Out-String)
    if ($javaExit -ne 0 -or $versionText -notmatch 'version\s+"(\d+)') {
        throw "Could not identify Java at $JavaPath. Java 22+ is required."
    }
    if ([int]$Matches[1] -lt $manifest.minimumJava) { throw "Java $($manifest.minimumJava)+ is required; found $($Matches[1])." }
    if ($JavaHeap -notmatch '^\d+[mMgG]$') { throw 'JavaHeap must be a size such as 3g or 2048m.' }

    $cacheDirectory = Join-Path $PSScriptRoot 'target/portable/storm-death'
    $catalogPath = Join-Path $cacheDirectory 'catalog.json'
    # .NET's process directory can differ from PowerShell's Push-Location.
    $outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
    $bundlePrefix = $bundle + [System.IO.Path]::DirectorySeparatorChar
    if ($outputPath.StartsWith($bundlePrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
        $outputPath -eq $requestPath -or $outputPath -eq $catalogPath) {
        throw 'Choose a result path outside the runtime bundle, request and cached catalog.'
    }
    [System.IO.Directory]::CreateDirectory($cacheDirectory) | Out-Null
    if (-not (Test-Path -LiteralPath $catalogPath) -or
        (Get-FileHash -LiteralPath $catalogPath -Algorithm SHA256).Hash -ne $manifest.files.'catalog.json'.sha256) {
        Write-Host 'Unpacking the bundled catalog...'
        $temporaryPath = Join-Path $cacheDirectory ([guid]::NewGuid().ToString() + '.tmp')
        try {
            $inputStream = [System.IO.File]::OpenRead($compressedPath)
            try {
                $gzip = New-Object System.IO.Compression.GZipStream($inputStream, [System.IO.Compression.CompressionMode]::Decompress)
                try {
                    $outputStream = [System.IO.File]::Create($temporaryPath)
                    try { $gzip.CopyTo($outputStream) } finally { $outputStream.Dispose() }
                } finally { $gzip.Dispose() }
            } finally { $inputStream.Dispose() }
            if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash -ne $manifest.files.'catalog.json'.sha256) {
                throw 'Unpacked catalog checksum mismatch.'
            }
            Move-Item -LiteralPath $temporaryPath -Destination $catalogPath -Force
        } finally {
            if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath }
        }
    }
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputPath)) | Out-Null
    Write-Host "Request: $Request"
    Write-Host "Portable BROAD catalog: $($manifest.counts.gear) gear, $($manifest.counts.jewels) jewels/pins, $($manifest.counts.petTalents) pet talents."
    Write-Host 'Provisional archive data; inspect feasibility and result warnings. Preparing candidates...'
    $ErrorActionPreference = 'Continue'
    & $JavaPath "-Xmx$JavaHeap" '-jar' $jarPath 'optimize' '--request' $requestPath '--catalog' $catalogPath '--output' $outputPath 2>&1 |
        ForEach-Object { Write-Host $_.ToString() }
    $javaExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($javaExit -ne 0) { throw "Optimizer failed (exit $javaExit). Any previous result file is unchanged." }
    $runTimer.Stop()
    Write-Host ('Completed in {0:N1} seconds.' -f $runTimer.Elapsed.TotalSeconds)
    Write-Host "Results saved to $outputPath"
} finally {
    Pop-Location
}
