$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $projectRoot '.env'

$services = @(
    @{ Name = 'customer-service'; Port = 8083 },
    @{ Name = 'twofa-service'; Port = 8082 },
    @{ Name = 'auth-service'; Port = 8081 },
    @{ Name = 'branch-service'; Port = 8084 },
    @{ Name = 'account-service'; Port = 8085 },
    @{ Name = 'beneficiary-service'; Port = 8086 },
    @{ Name = 'transaction-service'; Port = 8087 },
    @{ Name = 'banking-workflow-service'; Port = 8088 },
    @{ Name = 'api-gateway'; Port = 8080 }
)

function Get-ServiceJar([hashtable]$service) {
    $targetDirectory = Join-Path $projectRoot (Join-Path $service.Name 'target')
    if (-not (Test-Path $targetDirectory)) {
        return $null
    }

    Get-ChildItem -Path $targetDirectory -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.jar.original' } |
        Select-Object -First 1
}

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

$requiredEnvironmentVariables = @(
    'AUTH_DB_URL', 'AUTH_DB_USERNAME', 'AUTH_DB_PASSWORD',
    'TWOFA_DB_URL', 'TWOFA_DB_USERNAME', 'TWOFA_DB_PASSWORD',
    'CUSTOMER_DB_URL', 'CUSTOMER_DB_USERNAME', 'CUSTOMER_DB_PASSWORD',
    'BRANCH_DB_URL', 'BRANCH_DB_USERNAME', 'BRANCH_DB_PASSWORD',
    'ACCOUNT_DB_URL', 'ACCOUNT_DB_USERNAME', 'ACCOUNT_DB_PASSWORD',
    'BENEFICIARY_DB_URL', 'BENEFICIARY_DB_USERNAME', 'BENEFICIARY_DB_PASSWORD',
    'TRANSACTION_DB_URL', 'TRANSACTION_DB_USERNAME', 'TRANSACTION_DB_PASSWORD',
    'JWT_SECRET', 'INTERNAL_API_KEY', 'TWOFA_ENCRYPTION_KEY'
)

$missingEnvironmentVariables = @($requiredEnvironmentVariables | Where-Object { -not [Environment]::GetEnvironmentVariable($_, 'Process') })
if ($missingEnvironmentVariables.Count -gt 0) {
    throw ("Missing required .env values: {0}" -f ($missingEnvironmentVariables -join ', '))
}

# Some Windows shells expose both Path and PATH. Start-Process cannot copy
# that duplicate environment block, so retain the normal Windows Path entry.
Remove-Item Env:PATH -ErrorAction SilentlyContinue

$missingJars = @($services | Where-Object { -not (Get-ServiceJar $_) })
if ($missingJars.Count -gt 0) {
    Write-Host 'Building banking platform services...'
    Push-Location $projectRoot
    try {
        $mavenHome = Join-Path $env:ProgramFiles 'Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd'
        $env:MAVEN_OPTS = "-Duser.home=`"$env:USERPROFILE`""
        if (Test-Path $mavenHome) {
            & $mavenHome -q -DskipTests package
        } else {
            & .\mvnw.cmd -q -DskipTests package
        }
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    finally {
        Pop-Location
    }
} else {
    Write-Host 'Runnable service JARs already exist; skipping Maven package.'
}

$logDirectory = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Wait-ForHealthyService([hashtable]$service) {
    $deadline = (Get-Date).AddSeconds(180)

    while ((Get-Date) -lt $deadline) {
        if (Test-NetConnection -ComputerName 'localhost' -Port $service.Port -InformationLevel Quiet -WarningAction SilentlyContinue) {
            Write-Host ("{0} is listening." -f $service.Name) -ForegroundColor Green
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw ("{0} did not become healthy within 180 seconds. Check logs\\{0}.error.log." -f $service.Name)
}

foreach ($service in $services) {
    $existing = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host ("{0} is already running on port {1}; skipped." -f $service.Name, $service.Port) -ForegroundColor Yellow
        Wait-ForHealthyService $service
        continue
    }

    $jar = Get-ServiceJar $service

    if (-not $jar) {
        throw ("No runnable JAR was produced for {0}." -f $service.Name)
    }

    $standardLog = Join-Path $logDirectory ("{0}.log" -f $service.Name)
    $errorLog = Join-Path $logDirectory ("{0}.error.log" -f $service.Name)
    Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList @('-jar', "`"$($jar.FullName)`"") `
        -WorkingDirectory (Join-Path $projectRoot $service.Name) `
        -RedirectStandardOutput $standardLog -RedirectStandardError $errorLog -WindowStyle Hidden
    Write-Host ("Started {0} on port {1}." -f $service.Name, $service.Port) -ForegroundColor Green
    Wait-ForHealthyService $service
}

Write-Host 'All banking platform services are healthy. Gateway: http://localhost:8080'
