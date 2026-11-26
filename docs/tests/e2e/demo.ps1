<#
.SYNOPSIS
    Démo de bout en bout : capteur → telemetrie (gRPC) → orchestrateur → CNAM (SOAP), portail (REST),
    dashboard (GraphQL).

.DESCRIPTION
    1. vérifie que les 5 services répondent ;
    2. lance le capteur simulé (tachycardie) ;
    3. attend la fin des workflows déclenchés par ses alertes ;
    4. affiche le workflow, la fiche GraphQL du patient et la feuille de soins (SOAP) ;
    5. saga d'escalade : l'alerte ELEVEE puis CRITIQUE de la tachycardie → le workflow ELEVEE est COMPENSE
       (feuille CS 1 ANNULEE, RDV d'urgence ANNULE), une seule facture active (celle de l'ambulance).
    Compatible Windows PowerShell 5.1 et PowerShell 7.
    -ViaGateway : toutes les URL HTTP passent par l'API Gateway (http://localhost:8080/api/...). Le capteur, lui,
    parle gRPC directement à telemetrie : la gateway n'expose pas gRPC (ce n'est pas un client navigateur).
    -Docker : les services tournent dans docker compose, et le capteur est lancé dans un conteneur
    (docker compose --profile capteur run), joignant telemetrie par le réseau Docker (telemetrie:9090).

.EXAMPLE
    .\demo.ps1
    .\demo.ps1 -Patient PAT-0002 -Telemetrie localhost:9090
    .\demo.ps1 -Docker
    .\demo.ps1 -Docker -ViaGateway
#>
param(
    [string]$Patient = "PAT-0001",
    [string]$Telemetrie = "localhost:9090",
    [string]$Cnam = "http://localhost:8081/ws",
    [string]$Portail = "http://localhost:8082",
    [string]$Dashboard = "http://localhost:8083/graphql",
    [string]$Orchestrateur = "http://localhost:8085",
    [int]$DureeCapteur = 30,
    [switch]$Docker,
    [switch]$ViaGateway,                              # toutes les URL passent par l'API Gateway
    [string]$Gateway = "http://localhost:8080",
    [string]$Utilisateur = "admin",                   # compte Keycloak (mot de passe demo) pour -ViaGateway
    [string]$Keycloak = "http://localhost:8180"
)
if ($ViaGateway) {
    $Cnam = "$Gateway/api/cnam/ws"; $Portail = "$Gateway/api/portail"
    $Dashboard = "$Gateway/api/graphql"; $Orchestrateur = "$Gateway/api/orchestrateur"
}
if ($ViaGateway) {
    # la gateway exige un jeton : client Keycloak « scripts » (password grant, réservé aux tests)
    . (Join-Path $PSScriptRoot "..\jeton-keycloak.ps1")
    Enable-JetonGateway -Utilisateur $Utilisateur -Keycloak $Keycloak
}

$ErrorActionPreference = "Stop"
$racine = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")   # docs\tests\e2e → racine du dépôt
$dossierCapteur = Join-Path $racine "capteur-simulator"

function Get-Utf8([string]$uri, [string]$methode = "GET", [string]$corps = $null, [string]$type = "application/json") {
    $p = @{ Uri = $uri; Method = $methode; UseBasicParsing = $true; TimeoutSec = 5 }
    if ($corps) { $p.Body = [System.Text.Encoding]::UTF8.GetBytes($corps); $p.ContentType = "$type; charset=utf-8" }
    try {
        $r = Invoke-WebRequest @p
    } catch {
        throw "$methode $uri : $($_.Exception.Message)"
    }
    return [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
}

# Workflows du patient, un élément par workflow (sous PowerShell 5.1, ConvertFrom-Json renvoie un tableau
# JSON comme UN seul objet : on l'énumère explicitement)
function Get-Workflows {
    $json = (Get-Utf8 "$Orchestrateur/workflows?patientId=$Patient") | ConvertFrom-Json
    return @($json | ForEach-Object { $_ })
}

function Test-Port([string]$hoteEtPort) {
    $hote, $port = $hoteEtPort.Split(":")
    $client = New-Object System.Net.Sockets.TcpClient
    try { return $client.ConnectAsync($hote, [int]$port).Wait(2000) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}

function Invoke-GraphQL([string]$requete) {
    $corps = @{ query = $requete } | ConvertTo-Json -Compress
    return (Get-Utf8 $Dashboard "POST" $corps) | ConvertFrom-Json
}

# ===================== 1. Les services répondent ? =====================
Write-Host "1. Vérification des services" -ForegroundColor Cyan
$verifications = [ordered]@{
    "dossier-cnam (SOAP)     $Cnam"       = { (Get-Utf8 "$Cnam/dossier-cnam.wsdl") -match "DossierCnamPort" }
    "portail-patient (REST)  $Portail"    = { ((Get-Utf8 "$Portail/medecins") | ConvertFrom-Json).Count -gt 0 }
    "dashboard (GraphQL)     $Dashboard"  = { (Invoke-GraphQL "{ patient(id: `"$Patient`") { id } }").data.patient.id -eq $Patient }
    "telemetrie (gRPC)       $Telemetrie" = { Test-Port $Telemetrie }
    "orchestrateur (REST)    $Orchestrateur" = { $null -ne (Get-Utf8 "$Orchestrateur/workflows") }
}
$manquants = 0
foreach ($nom in $verifications.Keys) {
    $ok = $false
    try { $ok = & $verifications[$nom] } catch { $ok = $false }
    Write-Host ("   {0} {1}" -f $(if ($ok) { "OK " } else { "KO " }), $nom) -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
    if (-not $ok) { $manquants++ }
}
if ($manquants -gt 0) {
    Write-Host "$manquants service(s) ne répondent pas : démarrez-les (voir README racine), puis relancez la démo." -ForegroundColor Red
    exit 1
}

# ===================== 2. Capteur =====================
Write-Host ""
Write-Host ("2. Capteur simulé : $Patient, tachycardie, $DureeCapteur s" + $(if ($Docker) { " (conteneur docker compose)" } else { "" })) -ForegroundColor Cyan
$argumentsCapteur = "--patient", $Patient, "--scenario", "tachycardie", "--duree", $DureeCapteur, "--bascule", "3"
$avant = @(Get-Workflows | ForEach-Object { $_.id })
$journal = Join-Path ([System.IO.Path]::GetTempPath()) "sahticonnect-capteur.log"
if ($Docker) {
    # conteneur éphémère du profil "capteur" ; -T : pas de terminal (sortie redirigée vers le journal)
    $capteur = Start-Process -FilePath "docker" -WorkingDirectory $racine -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $journal -RedirectStandardError "$journal.docker" `
        -ArgumentList (@("compose", "--profile", "capteur", "run", "--rm", "-T", "capteur") + $argumentsCapteur + @("--hote", "telemetrie:9090"))
} else {
    $python = Join-Path $dossierCapteur ".venv\Scripts\python.exe"
    if (-not (Test-Path $python)) { $python = "python" }
    if (-not (Test-Path (Join-Path $dossierCapteur "telemetrie_pb2.py"))) {
        $env:Path = (Split-Path $python) + ";" + $env:Path
        & (Join-Path $dossierCapteur "generer_stubs.ps1") | Out-Null
    }
    $capteur = Start-Process -FilePath $python -WorkingDirectory $dossierCapteur -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $journal `
        -ArgumentList (@("capteur.py") + $argumentsCapteur + @("--hote", $Telemetrie))
}
Write-Host "   capteur lancé (PID $($capteur.Id)), journal : $journal"

# ===================== 3. Attente des workflows =====================
Write-Host ""
Write-Host "3. Attente des workflows déclenchés par les alertes" -ForegroundColor Cyan
$limite = (Get-Date).AddSeconds($DureeCapteur + 90)   # + démarrage éventuel du conteneur
$nouveaux = @()
do {
    Start-Sleep -Seconds 2
    $tous = @(Get-Workflows)
    $nouveaux = @($tous | Where-Object { $avant -notcontains $_.id })
    $enCours = @($nouveaux | Where-Object { $_.enCours })
    $etat = ($nouveaux | ForEach-Object { "$($_.id) $($_.niveau) $($_.etat)" }) -join " | "
    Write-Host ("   {0:HH:mm:ss}  {1}" -f (Get-Date), $(if ($etat) { $etat } else { "aucune alerte pour l'instant…" }))
    $fini = $capteur.HasExited -and $nouveaux.Count -gt 0 -and $enCours.Count -eq 0
} until ($fini -or (Get-Date) -gt $limite)
if (-not $capteur.HasExited) { Stop-Process -Id $capteur.Id -Force }
if ($nouveaux.Count -eq 0) { Write-Host "Aucun workflow : l'orchestrateur est-il abonné à $Telemetrie ?" -ForegroundColor Red; exit 1 }

# ===================== 4. Résultat =====================
Write-Host ""
Write-Host "4. Workflows" -ForegroundColor Cyan
$details = @()
foreach ($wf in ($nouveaux | Sort-Object id)) {
    $detail = (Get-Utf8 "$Orchestrateur/workflows/$($wf.id)") | ConvertFrom-Json
    $details += $detail
    $couleur = switch ($detail.etat) { "FACTUREE" { "Green" } "COMPENSE" { "Magenta" } default { "Yellow" } }
    $suffixe = if ($detail.compensePar) { "  (compensé par $($detail.compensePar))" } else { "" }
    Write-Host ("   {0}  alerte {1} {2} ({3})  →  {4}{5}" -f $detail.id, $detail.niveau, $detail.alerteId, $detail.seuilFranchi, $detail.etat, $suffixe) `
        -ForegroundColor $couleur
    foreach ($e in $detail.etapes) {
        # compensation (saga) : même nom que l'étape annulée, marquée ↩
        $nom = if ($e.type -eq "COMPENSATION") { "↩ " + $e.nom } else { $e.nom }
        Write-Host ("      {0,-26} {1,-7} {2,5} ms  {3}" -f $nom, $e.statut, $e.dureeMs, $e.message) `
            -ForegroundColor $(if ($e.type -eq "COMPENSATION") { "Magenta" } else { "Gray" })
    }
}

# ===================== 5. Saga d'escalade =====================
$compenses = @($details | Where-Object { $_.etat -eq "COMPENSE" })
Write-Host ""
Write-Host "5. Saga d'escalade (alerte ELEVEE puis CRITIQUE du même patient)" -ForegroundColor Cyan
if ($compenses.Count -eq 0) {
    Write-Host "   Aucun workflow compensé (pas d'alerte ELEVEE suivie d'une CRITIQUE avec un RDV à venir)."
} else {
    foreach ($c in $compenses) {
        $rdv = (Get-Utf8 "$Portail/rendez-vous/$($c.referenceExterne)") | ConvertFrom-Json
        Write-Host ("   {0} COMPENSE par {1} : RDV {2} → {3}, feuille {4} annulée, facturation {5}" -f $c.id, $c.compensePar,
            $rdv.id, $rdv.statut, $c.numeroFeuilleSoins, $c.statutFacturation) -ForegroundColor Magenta
    }
}

Write-Host ""
Write-Host "   Fiche GraphQL (dashboard) :" -ForegroundColor Cyan
$fiche = Invoke-GraphQL "{ patient(id: `"$Patient`") { nom niveauRisque couvert tauxRemboursement constantes(type: FREQUENCE_CARDIAQUE, limite: 3) { valeur niveau } interventions { id type etat statutFacturation referenceExterne numeroFeuilleSoins montantTotal } } }"
Write-Host ($fiche.data.patient | ConvertTo-Json -Depth 5)

$feuilles = @($nouveaux | ForEach-Object { ((Get-Utf8 "$Orchestrateur/workflows/$($_.id)") | ConvertFrom-Json).numeroFeuilleSoins } | Where-Object { $_ })
Write-Host ""
Write-Host "   Feuille(s) de soins dans l'historique CNAM (SOAP consulterHistorique) :" -ForegroundColor Cyan
$soap = @"
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cnam="http://sahticonnect.tn/cnam">
  <soapenv:Body><cnam:consulterHistoriqueRequest><cnam:identifiantPatient>$Patient</cnam:identifiantPatient></cnam:consulterHistoriqueRequest></soapenv:Body>
</soapenv:Envelope>
"@
[xml]$historique = Get-Utf8 $Cnam "POST" $soap "text/xml"
$espaces = New-Object System.Xml.XmlNamespaceManager($historique.NameTable)
$espaces.AddNamespace("c", "http://sahticonnect.tn/cnam")
$actives = @()
foreach ($numero in $feuilles) {
    # émission, puis, si la feuille a été annulée par compensation, la ligne « Annulation de la feuille … »
    $entrees = @($historique.SelectNodes("//c:entrees[c:numeroFeuilleSoins='$numero']", $espaces))
    if ($entrees.Count -eq 0) {
        Write-Host "      $numero absente de l'historique" -ForegroundColor Red
        continue
    }
    $annulee = @($entrees | Where-Object { $_.diagnostic -like "Annulation de la feuille*" }).Count -gt 0
    foreach ($entree in $entrees) {
        $actes = ($entree.SelectNodes("c:actes", $espaces) | ForEach-Object { "$($_.lettreCle) $($_.coefficient)" }) -join " + "
        Write-Host ("      {0}  {1}  {2}  actes : {3}" -f $numero, $entree.date, $entree.diagnostic, $actes) `
            -ForegroundColor $(if ($annulee) { "Magenta" } else { "Green" })
    }
    if (-not $annulee) { $actives += $numero }
}
$montants = @($details | Where-Object { $actives -contains $_.numeroFeuilleSoins } | ForEach-Object { "{0} ({1:N3} DT)" -f $_.numeroFeuilleSoins, $_.montantTotal })
Write-Host ""
Write-Host ("   Facture(s) active(s) : {0} → {1}" -f $actives.Count, ($montants -join ", ")) -ForegroundColor Cyan
Write-Host ""
Write-Host "Démo terminée." -ForegroundColor Green
