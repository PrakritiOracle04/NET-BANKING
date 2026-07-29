$ErrorActionPreference = 'Stop'

$services = @(
    @{ Name = 'API Gateway'; Port = 8080 },
    @{ Name = 'Auth Service'; Port = 8081 },
    @{ Name = '2FA Service'; Port = 8082 },
    @{ Name = 'Customer Service'; Port = 8083 },
    @{ Name = 'Branch Service'; Port = 8084 },
    @{ Name = 'Account Service'; Port = 8085 },
    @{ Name = 'Beneficiary Service'; Port = 8086 },
    @{ Name = 'Transaction Service'; Port = 8087 },
    @{ Name = 'Banking Workflow Service'; Port = 8088 }
)

foreach ($service in $services) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    if (-not $listeners) {
        Write-Host ("{0} is not running." -f $service.Name) -ForegroundColor Yellow
        continue
    }

    foreach ($listener in $listeners) {
        Stop-Process -Id $listener.OwningProcess -Force
        Write-Host ("Stopped {0} on port {1}." -f $service.Name, $service.Port) -ForegroundColor Green
    }
}
