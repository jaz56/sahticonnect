<#
.SYNOPSIS
    Déclenche manuellement un workflow sur orchestrateur-service, suit sa progression étape par étape,
    puis vérifie l'idempotence et les erreurs du contrat.

.DESCRIPTION
    Compatible Windows PowerShell 5.1 et PowerShell 7. Pour un workflow FACTUREE, les 4 autres services
    doivent tourner (dossier-cnam 8081, portail-patient 8082, dashboard 8083). Sinon, le workflow se termine
    en ECHEC, ce qui montre aussi les replis et la gestion des pannes.

.EXAMPLE
    .\tester-orchestrateur.ps1
    .\tester-orchestrateur.ps1 -Patient PAT-0001 -Niveau CRITIQUE
    .\tester-orchestrateur.ps1 -ViaGateway     # http://localhost:8080/api/orchestrateur
#>
param(
    [string]$Url = "http://localhost:8085",
    [string]$Patient = "PAT-0003",
    [ValidateSet("ELEVEE", "CRITIQUE")][string]$Niveau = "ELEVEE",
    [switch]$ViaGateway,                              # toutes les URL passent par l'API Gateway
    [string]$Gateway = "http://localhost:8080"
)
if ($ViaGateway) { $Url = "$Gateway/api/orchestrateur" }

$ErrorActionPreference = "Stop"
$script:echecs = 0

function Send-Json([string]$Methode, [string]$Chemin, $Corps = $null) {
    $parametres = @{ Uri = "$Url$Chemin"; Method = $Methode; UseBasicParsing = $true }
    if ($null -ne $Corps) {
        $parametres.Body = [System.Text.Encoding]::UTF8.GetBytes(($Corps | ConvertTo-Json -Depth 5 -Compress))
        $parametres.ContentType = "application/json; charset=utf-8"
    }
    try {
        $r = Invoke-WebRequest @parametres
        $texte = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return [pscustomobject]@{ Statut = [int]$r.StatusCode; Corps = ($texte | ConvertFrom-Json); Location = $r.Headers["Location"] }
    } catch {
        if ($null -eq $_.Exception.Response) { throw "Orchestrateur injoignable sur $Url : $($_.Exception.Message)" }
        $flux = $_.Exception.Response.GetResponseStream()
        if ($flux.CanSeek) { $flux.Position = 0 }
        $texte = (New-Object System.IO.StreamReader($flux, [System.Text.Encoding]::UTF8)).ReadToEnd()
        if (-not $texte -and $_.ErrorDetails) { $texte = $_.ErrorDetails.Message }
        return [pscustomobject]@{ Statut = [int]$_.Exception.Response.StatusCode; Corps = ($texte | ConvertFrom-Json); Location = $null }
    }
}

function Assert-Statut($reponse, [int]$attendu, [string]$titre) {
    $couleur = if ($reponse.Statut -eq $attendu) { "Green" } else { "Red"; $script:echecs++ }
    $code = if ($reponse.Corps.code) { " $($reponse.Corps.code)" } else { "" }
    Write-Host ("{0} → HTTP {1}{2} (attendu {3})" -f $titre, $reponse.Statut, $code, $attendu) -ForegroundColor $couleur
}

function Show-Etapes($workflow) {
    foreach ($e in $workflow.etapes) {
        $couleur = switch ($e.statut) { "OK" { "Green" } "ECHEC" { "Red" } default { "Yellow" } }
        Write-Host ("   #{0} {1,-24} {2,-7} {3,6} ms  {4}" -f $e.tentative, $e.nom, $e.statut, $e.dureeMs, $e.message) -ForegroundColor $couleur
    }
}

Write-Host "Orchestrateur sur $Url" -ForegroundColor White

# ===================== 1. Déclenchement manuel =====================
$suffixe = (Get-Date).Ticks % 100000
$alerteId = "ALR-{0}-6{1:D5}" -f (Get-Date).Year, $suffixe
$valeur = if ($Niveau -eq "CRITIQUE") { 142 } else { 118 }
$seuil = if ($Niveau -eq "CRITIQUE") { "FC > 130" } else { "FC ≥ 110" }
$alerte = [ordered]@{
    alerteId = $alerteId; patientId = $Patient; niveau = $Niveau; typeMesure = "FREQUENCE_CARDIAQUE"; valeur = $valeur
    seuilFranchi = $seuil; message = "Alerte $Niveau : fréquence cardiaque $valeur bpm ($seuil)"
    horodatage = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
}
Write-Host ""
Write-Host "--- POST /workflows/declencher ($alerteId, $Patient, $Niveau)" -ForegroundColor Cyan
$reponse = Send-Json POST "/workflows/declencher" $alerte
Assert-Statut $reponse 202 "Déclenchement"
$id = $reponse.Corps.id
Write-Host "Location : $($reponse.Location)"

# ===================== 2. Suivi =====================
Write-Host ""
Write-Host "--- Suivi de $id" -ForegroundColor Cyan
$dernierEtat = ""
for ($i = 0; $i -lt 90; $i++) {
    $wf = (Send-Json GET "/workflows/$id").Corps
    if ($wf.etat -ne $dernierEtat) { Write-Host ("   {0:HH:mm:ss}  état {1}" -f (Get-Date), $wf.etat); $dernierEtat = $wf.etat }
    if (-not $wf.enCours) { break }
    Start-Sleep -Milliseconds 500
}
Write-Host ""
$couleur = if ($wf.etat -eq "FACTUREE") { "Green" } else { "Yellow" }
Write-Host ("Workflow {0} : {1}{2}" -f $wf.id, $wf.etat, $(if ($wf.etapeEnEchec) { " (étape en cause : $($wf.etapeEnEchec))" })) -ForegroundColor $couleur
Show-Etapes $wf
Write-Host ("   intervention {0} {1}, fiche {2}, feuille {3}, facturation {4}" -f $wf.typeIntervention, $wf.referenceExterne,
    $wf.interventionId, $wf.numeroFeuilleSoins, $wf.statutFacturation)

# ===================== 3. Idempotence et erreurs =====================
Write-Host ""
Write-Host "--- Idempotence et erreurs du contrat" -ForegroundColor Cyan
$rejouee = Send-Json POST "/workflows/declencher" $alerte
Assert-Statut $rejouee 200 "Même alerte rejouée (workflow existant renvoyé)"
if ($rejouee.Corps.id -ne $id) { Write-Host "   id différent : $($rejouee.Corps.id)" -ForegroundColor Red; $script:echecs++ }
Assert-Statut (Send-Json GET "/workflows/WF-2026-999999") 404 "Workflow inconnu"
Assert-Statut (Send-Json GET "/workflows/WF-1") 400 "Identifiant mal formé"
if ($wf.etat -eq "ECHEC") {
    Assert-Statut (Send-Json POST "/workflows/$id/relancer") 202 "Relance du workflow en ECHEC"
} else {
    Assert-Statut (Send-Json POST "/workflows/$id/relancer") 409 "Relance d'un workflow qui n'est pas en ECHEC"
}
# sous PowerShell 5.1, ConvertFrom-Json renvoie un tableau JSON comme UN seul objet : on l'énumère explicitement
$liste = @((Send-Json GET "/workflows?patientId=$Patient").Corps | ForEach-Object { $_ })
Write-Host ("Workflows de {0} : {1} (le plus récent : {2})" -f $Patient, $liste.Count, $liste[0].id)

Write-Host ""
if ($script:echecs -gt 0) { Write-Host "$($script:echecs) résultat(s) inattendu(s)" -ForegroundColor Red; exit 1 }
Write-Host "Scénario terminé." -ForegroundColor Green
