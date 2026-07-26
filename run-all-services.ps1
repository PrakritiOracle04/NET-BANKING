$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $projectRoot '.env'

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaHomes = @()
    foreach ($javaRoot in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium')) {
        if (Test-Path $javaRoot) {
            $javaHomes += Get-ChildItem -Path $javaRoot -Directory |
                Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') }
        }
    }

    $javaHome = $javaHomes | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $javaHome) {
        throw 'No JDK was found. Install JDK 17 or set JAVA_HOME to its installation folder.'
    }

    $env:JAVA_HOME = $javaHome.FullName
    Write-Host ("Using JDK: {0}" -f $env:JAVA_HOME)
}

if (-not (Test-Path $envFile)) {
    throw 'Missing .env. Copy .env.example to .env and provide the local values.'
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
        Set-Item -Path ("Env:{0}" -f $matches[1]) -Value $matches[2]
    }
}

Write-Host 'Building Phase 1 services...'
Push-Location $projectRoot
try {
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

$logDirectory = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$services = @(
    @{ Name = 'customer-service'; Port = 8083 },
    @{ Name = 'twofa-service'; Port = 8082 },
    @{ Name = 'auth-service'; Port = 8081 },
    @{ Name = 'branch-service'; Port = 8084 },
    @{ Name = 'api-gateway'; Port = 8080 }
)

foreach ($service in $services) {
    $existing = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host ("{0} is already running on port {1}; skipped." -f $service.Name, $service.Port) -ForegroundColor Yellow
        continue
    }

    $targetDirectory = Join-Path $projectRoot (Join-Path $service.Name 'target')
    $jar = Get-ChildItem -Path $targetDirectory -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '^original-' } |
        Select-Object -First 1

    if (-not $jar) {
        throw ("No runnable JAR was produced for {0}." -f $service.Name)
    }

    $standardLog = Join-Path $logDirectory ("{0}.log" -f $service.Name)
    $errorLog = Join-Path $logDirectory ("{0}.error.log" -f $service.Name)
    Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar.FullName) `
        -WorkingDirectory (Join-Path $projectRoot $service.Name) `
        -RedirectStandardOutput $standardLog -RedirectStandardError $errorLog -WindowStyle Hidden
    Write-Host ("Started {0} on port {1}." -f $service.Name, $service.Port) -ForegroundColor Green
}

Write-Host 'All Phase 1 services are starting. Gateway: http://localhost:8080'
