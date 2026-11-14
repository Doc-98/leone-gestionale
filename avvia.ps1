<#
.SYNOPSIS
  Avvia il gestionale in locale: database, backend e frontend, poi apre il browser.

.DESCRIPTION
  Fa da solo i passi di «Avviare in locale» del README. La cartella del progetto è quella dove sta questo file, quindi
  funziona su qualunque computer senza configurare niente. Backend e frontend si aprono ciascuno nella sua finestra
  (per vederne i messaggi); chiudendo la finestra si ferma quella parte, oppure: .\avvia.ps1 -Ferma

  Il modo più semplice: doppio clic su avvia.cmd (che chiama questo script).

.EXAMPLE
  .\avvia.ps1                 # dati demo
  .\avvia.ps1 -Locale         # dati demo + i dati veri di backend\dati-locali (profilo «locale»)
  .\avvia.ps1 -Aggiorna       # prima scarica l'ultima versione (git pull) e aggiorna le dipendenze
  .\avvia.ps1 -Azzera         # cancella il database: i dati demo ripartono da capo
  .\avvia.ps1 -Ferma          # ferma backend, frontend e database (i dati restano)
#>
param(
    [switch]$Locale,
    [switch]$Aggiorna,
    [switch]$Azzera,
    [switch]$Ferma,
    [switch]$SenzaBrowser
)

# Con i programmi esterni (docker, git, npm) si guarda il codice d'uscita: in Windows PowerShell 5.1 «Stop» farebbe
# fallire anche i semplici messaggi che scrivono sull'errore standard.
$ErrorActionPreference = 'Continue'
$progetto = $PSScriptRoot
$compose = Join-Path $progetto 'infra\docker-compose.yml'
$fileFinestre = Join-Path $progetto '.avvio-finestre.json'
$urlBackend = 'http://localhost:8080/actuator/health'
$urlFrontend = 'http://localhost:5173'
$urlVetrina = 'http://localhost:5173/demo.html'  # la vetrina della demo (ADR 0027)

function Passo($testo) { Write-Host "`n> $testo" -ForegroundColor Cyan }
function Ok($testo) { Write-Host "  $testo" -ForegroundColor Green }
function Avviso($testo) { Write-Host "  $testo" -ForegroundColor Yellow }
function Fine($testo) {
    Write-Host "`n$testo" -ForegroundColor Red
    Read-Host "`nPremi Invio per chiudere"
    exit 1
}
function Esiste($comando) { [bool](Get-Command $comando -ErrorAction SilentlyContinue) }
function Silenzioso($riga) { cmd /c "$riga >nul 2>&1"; return ($LASTEXITCODE -eq 0) }

function Risponde($url) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
        return ($r.StatusCode -lt 500)
    } catch { return $false }
}

function Attendi($url, $secondi, $processo, $nome) {
    $limite = (Get-Date).AddSeconds($secondi)
    while ((Get-Date) -lt $limite) {
        if (Risponde $url) { return $true }
        if ($processo -and $processo.HasExited) { Fine "La finestra del $nome si è chiusa: avvialo a mano per leggere l'errore (vedi README, «Avviare in locale»)." }
        Start-Sleep -Seconds 2
        Write-Host '.' -NoNewline
    }
    Write-Host ''
    return $false
}

# Le finestre aperte dall'ultimo avvio, per poterle chiudere con -Ferma
function Leggi-Finestre {
    if (Test-Path $fileFinestre) { try { return Get-Content $fileFinestre -Raw | ConvertFrom-Json } catch { } }
    return $null
}

function Chiudi-Finestre {
    $f = Leggi-Finestre
    if (-not $f) { return }
    foreach ($id in @($f.backend, $f.frontend)) {
        # /T chiude anche Java e Node partiti dalla finestra
        if ($id -and (Get-Process -Id $id -ErrorAction SilentlyContinue)) { Silenzioso "taskkill /PID $id /T /F" | Out-Null }
    }
    Remove-Item $fileFinestre -ErrorAction SilentlyContinue
}

# Una finestra di PowerShell che esegue un comando nella cartella indicata e resta aperta
function Apri-Finestra($titolo, $cartella, $comando) {
    $shell = if (Esiste 'pwsh') { 'pwsh' } else { 'powershell' }
    $cartella = $cartella -replace "'", "''"   # una cartella come C:\Users\D'Angelo
    $script = "`$host.UI.RawUI.WindowTitle = '$titolo'; Set-Location -LiteralPath '$cartella'; $comando"
    return Start-Process $shell -ArgumentList '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $script -PassThru
}

function Docker-Acceso {
    if (Silenzioso 'docker info') { return $true }
    $docker = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $docker)) { return $false }
    Avviso 'Docker Desktop è spento: lo apro (la prima volta può volerci un minuto)'
    Start-Process $docker | Out-Null
    $limite = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $limite) {
        Start-Sleep -Seconds 3
        Write-Host '.' -NoNewline
        if (Silenzioso 'docker info') { Write-Host ''; return $true }
    }
    Write-Host ''
    return $false
}

Write-Host "Leone Gestionale · cartella del progetto: $progetto" -ForegroundColor White

# --- Fermare tutto -------------------------------------------------------------------------------------------------
if ($Ferma) {
    Passo 'Fermo backend e frontend'
    Chiudi-Finestre
    Ok 'Fatto (se li avevi avviati a mano, chiudi le loro finestre)'
    Passo 'Fermo il database (i dati restano)'
    if (Silenzioso 'docker info') { docker compose -f $compose stop } else { Ok 'Docker è già spento' }
    exit 0
}

# --- Controlli ------------------------------------------------------------------------------------------------------
Passo 'Controllo i programmi'
$mancano = @()
foreach ($p in 'git', 'java', 'node', 'npm', 'docker') { if (-not (Esiste $p)) { $mancano += $p } }
if ($mancano.Count -gt 0) {
    Fine "Mancano: $($mancano -join ', '). Installali (README, «Installare i programmi necessari») e riapri PowerShell."
}
$versioneJava = (cmd /c 'java -version 2>&1' | Select-Object -First 1)
if ($versioneJava -notmatch '"21') { Avviso "Java dovrebbe essere la 21; trovato: $versioneJava" }
Ok 'Git, Java, Node e Docker ci sono'

# --- Aggiornare -----------------------------------------------------------------------------------------------------
if ($Aggiorna) {
    Passo "Scarico l'ultima versione"
    git -C $progetto pull --ff-only
    if ($LASTEXITCODE -ne 0) { Fine 'git pull non è riuscito: hai modifiche non salvate, o la versione locale si è allontanata da quella su GitHub.' }
}

# --- Database -------------------------------------------------------------------------------------------------------
Passo 'Database'
if (-not (Docker-Acceso)) { Fine 'Docker Desktop non si avvia: aprilo a mano, aspetta che dica «Engine running» e riprova.' }
if ($Azzera) {
    Chiudi-Finestre
    Avviso 'Cancello il database: al primo avvio i dati demo ripartono da capo'
    docker compose -f $compose down -v
}
docker compose -f $compose up -d --wait
if ($LASTEXITCODE -ne 0) { Fine 'Il database non parte. Se dice «port is already allocated», guarda «Problemi frequenti» nel README.' }
Ok 'Acceso'

# --- Backend --------------------------------------------------------------------------------------------------------
Passo 'Backend'
$finestre = @{ backend = $null; frontend = $null }
if (Risponde $urlBackend) {
    Ok 'È già acceso: lo lascio com''è'
    $processoBackend = $null
} else {
    $profili = if ($Locale) { 'demo,locale' } else { 'demo' }
    if ($Locale -and -not (Test-Path (Join-Path $progetto 'backend\dati-locali'))) { Avviso 'Profilo «locale», ma la cartella backend\dati-locali non c''è: partiranno solo i dati demo' }
    $processoBackend = Apri-Finestra 'Leone · backend' (Join-Path $progetto 'backend') ".\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=$profili'"
    $finestre.backend = $processoBackend.Id
    Write-Host "  Avviato con i profili «$profili»; aspetto che sia pronto (la prima volta qualche minuto)" -NoNewline
    if (-not (Attendi $urlBackend 600 $processoBackend 'backend')) { Fine 'Il backend non risponde dopo 10 minuti: guarda la sua finestra.' }
    Ok 'Pronto'
}

# --- Frontend -------------------------------------------------------------------------------------------------------
Passo 'Frontend'
$cartellaFrontend = Join-Path $progetto 'frontend'
# le dipendenze si installano la prima volta e quando package-lock.json è cambiato (es. dopo un git pull)
$installate = Join-Path $cartellaFrontend 'node_modules\.package-lock.json'
$lock = Join-Path $cartellaFrontend 'package-lock.json'
if (-not (Test-Path $installate) -or (Get-Item -Force $lock).LastWriteTime -gt (Get-Item -Force $installate).LastWriteTime) {
    Avviso 'Installo le dipendenze del frontend (npm install)'
    Push-Location $cartellaFrontend
    npm install
    $esito = $LASTEXITCODE
    Pop-Location
    if ($esito -ne 0) { Fine 'npm install non è riuscito: guarda i messaggi qui sopra.' }
}
if (Risponde $urlFrontend) {
    Ok 'È già acceso: lo lascio com''è'
} else {
    $processoFrontend = Apri-Finestra 'Leone · frontend' $cartellaFrontend 'npm run dev:telefono'  # anche nella rete locale: il QR della vetrina
    $finestre.frontend = $processoFrontend.Id
    Write-Host '  Aspetto che sia pronto' -NoNewline
    if (-not (Attendi $urlFrontend 90 $processoFrontend 'frontend')) { Fine 'Il frontend non risponde: guarda la sua finestra.' }
    Ok 'Pronto'
}

# ricordo le finestre aperte ora (insieme a quelle di prima ancora vive), per -Ferma
$prima = Leggi-Finestre
if ($prima) {
    if (-not $finestre.backend) { $finestre.backend = $prima.backend }
    if (-not $finestre.frontend) { $finestre.frontend = $prima.frontend }
}
$finestre | ConvertTo-Json | Set-Content $fileFinestre

# --- Fatto ----------------------------------------------------------------------------------------------------------
Write-Host "`nLa demo è su $urlVetrina (il gestionale da solo: $urlFrontend)" -ForegroundColor Green
Write-Host '  Account demo: segreteria@demo.leone, coordinatore@demo.leone, staff@demo.leone, admin@demo.leone · password demo1234'
Write-Host '  Per fermare tutto: .\avvia.ps1 -Ferma (o chiudi le finestre di backend e frontend)'
if (-not $SenzaBrowser) { Start-Process $urlVetrina }
