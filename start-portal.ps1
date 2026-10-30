$ErrorActionPreference = 'Stop'
$portalUrl = 'http://127.0.0.1:8080/'
try {
    $portalExisting = Invoke-WebRequest $portalUrl -UseBasicParsing -TimeoutSec 2
    if ($portalExisting.StatusCode -eq 200 -and $portalExisting.Content.Contains('runForm')) {
        Write-Output "Portal is already running: $portalUrl"
        return
    }
} catch { }

$portalJar = Join-Path $PSScriptRoot 'target/Pennydroponly-1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $portalJar)) {
    throw 'Executable JAR is missing. Run .\mvnw.cmd verify first.'
}
$portalJava = Join-Path $env:USERPROFILE '.jdks/temurin-21.0.12.1/bin/java.exe'
if (-not (Test-Path -LiteralPath $portalJava)) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        $portalJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
    } else {
        $portalJava = (Get-Command java.exe -ErrorAction Stop).Source
    }
}
$portalLogs = Join-Path $PSScriptRoot 'inspection'
New-Item -ItemType Directory -Path $portalLogs -Force | Out-Null
$env:SE_CACHE_PATH = Join-Path $PSScriptRoot '.mvn/selenium-cache'
$env:SE_AVOID_STATS = 'true'
$preflightLog = Join-Path $portalLogs 'browser-preflight.log'
$preflightErrorLog = Join-Path $portalLogs 'browser-preflight-error.log'
$preflightProcess = Start-Process -FilePath $portalJava -WorkingDirectory $PSScriptRoot `
    -ArgumentList @('-Djava.net.preferIPv4Stack=true', '-Dloader.main=org.example.ChromeConnectivityCheck', '-cp', ('"' + $portalJar + '"'), 'org.springframework.boot.loader.launch.PropertiesLauncher') `
    -WindowStyle Hidden -RedirectStandardOutput $preflightLog -RedirectStandardError $preflightErrorLog -Wait -PassThru
if ($preflightProcess.ExitCode -ne 0) {
    throw 'Chrome/DevTools preflight failed. The portal was not started; check inspection/browser-preflight-error.log.'
}
$portalProcess = Start-Process -FilePath $portalJava -WorkingDirectory $PSScriptRoot `
    -ArgumentList @('-Djava.net.preferIPv4Stack=true', '-jar', ('"' + $portalJar + '"')) -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $portalLogs 'portal.log') `
    -RedirectStandardError (Join-Path $portalLogs 'portal-error.log') -PassThru
Set-Content -LiteralPath (Join-Path $portalLogs 'portal.pid') -Value $portalProcess.Id
$portalDeadline = (Get-Date).AddSeconds(90)
do {
    if ($portalProcess.HasExited) {
        throw 'Portal exited during startup. Check inspection/portal.log and portal-error.log.'
    }
    try {
        $portalResponse = Invoke-WebRequest $portalUrl -UseBasicParsing -TimeoutSec 2
        if ($portalResponse.StatusCode -eq 200 -and $portalResponse.Content.Contains('runForm')) {
            Write-Output "Portal ready: $portalUrl (PID $($portalProcess.Id))"
            return
        }
    } catch { }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $portalDeadline)
throw 'Portal did not become ready within 90 seconds. Check inspection/portal.log and portal-error.log.'
