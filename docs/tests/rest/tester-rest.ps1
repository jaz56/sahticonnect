<#
.SYNOPSIS
    Scénario complet sur le service REST portail-patient (Invoke-RestMethod) :
    profil → créneaux → RDV → modification → annulation → RDV d'urgence → ambulance → erreurs.

.DESCRIPTION
    Compatible Windows PowerShell 5.1 et PowerShell 7. Le script peut être relancé :
    il choisit à chaque fois des créneaux libres.

.EXAMPLE
    .\tester-rest.ps1
    .\tester-rest.ps1 -ViaGateway                       # via l'API Gateway : http://localhost:8080/api/portail
#>
param(
    [string]$Url = "http://localhost:8082",
    [switch]$ViaGateway,                              # toutes les URL passent par l'API Gateway
    [string]$Gateway = "http://localhost:8080",
    [string]$Utilisateur = "admin",                   # compte Keycloak (mot de passe demo) pour -ViaGateway
    [string]$Keycloak = "http://localhost:8180"
)
if ($ViaGateway) { $Url = "$Gateway/api/portail" }
if ($ViaGateway) {
    # la gateway exige un jeton : client Keycloak « scripts » (password grant, réservé aux tests)
    . (Join-Path $PSScriptRoot "..\jeton-keycloak.ps1")
    Enable-JetonGateway -Utilisateur $Utilisateur -Keycloak $Keycloak
}

$ErrorActionPreference = "Stop"
$script:echecs = 0

function Read-CorpsErreur($erreur) {
    if ($erreur.ErrorDetails -and $erreur.ErrorDetails.Message) { return $erreur.ErrorDetails.Message }
    $reponse = $erreur.Exception.Response
    if ($null -eq $reponse) { return $null }
    $flux = $reponse.GetResponseStream()
    if ($flux.CanSeek) { $flux.Position = 0 }
    return (New-Object System.IO.StreamReader($flux, [System.Text.Encoding]::UTF8)).ReadToEnd()
}

<#
    Appelle l'API et affiche le résultat.
    -Attendu : codes HTTP attendus (2xx pour un succès, ou un code d'erreur du contrat).
    Renvoie le corps JSON (objet PowerShell), y compris pour une erreur.
#>
function Invoke-Api {
    param(
        [string]$Titre,
        [string]$Methode,
        [string]$Chemin,
        $Corps = $null,
        [int[]]$Attendu = @(200)
    )
    Write-Host ""
    Write-Host "--- $Titre" -ForegroundColor Cyan
    Write-Host "$Methode $Chemin" -ForegroundColor DarkGray

    $parametres = @{ Uri = "$Url$Chemin"; Method = $Methode; UseBasicParsing = $true }
    if ($null -ne $Corps) {
        $json = $Corps | ConvertTo-Json -Depth 5 -Compress
        Write-Host $json -ForegroundColor DarkGray
        $parametres.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parametres.ContentType = "application/json; charset=utf-8"
    }

    try {
        $reponse = Invoke-RestMethod @parametres
        $succesAttendu = @($Attendu | Where-Object { $_ -lt 300 }).Count -gt 0
        if ($succesAttendu) {
            Write-Host "Succès (attendu : $($Attendu -join ' ou '))" -ForegroundColor Green
        } else {
            Write-Host "Succès alors qu'une erreur $($Attendu -join ' ou ') était attendue" -ForegroundColor Red
            $script:echecs++
        }
        if ($null -ne $reponse -and "$reponse" -ne "") { Write-Host ($reponse | ConvertTo-Json -Depth 6) }
        return $reponse
    } catch {
        if ($null -eq $_.Exception.Response) { throw "Service injoignable sur $Url : $($_.Exception.Message)" }
        $statut = [int]$_.Exception.Response.StatusCode
        $texte = Read-CorpsErreur $_
        $erreur = if ($texte) { $texte | ConvertFrom-Json } else { $null }
        $code = if ($erreur) { $erreur.code } else { "?" }
        if ($Attendu -contains $statut) {
            Write-Host "HTTP $statut $code (attendu)" -ForegroundColor Yellow
        } else {
            Write-Host "HTTP $statut $code (attendu : $($Attendu -join ' ou '))" -ForegroundColor Red
            $script:echecs++
        }
        if ($erreur) { Write-Host ($erreur | ConvertTo-Json -Depth 4) }
        return $erreur
    }
}

# Prochain jour de consultation (hors dimanche) dans au moins $decalage jours
function Get-JourConsultation([int]$decalage) {
    $jour = (Get-Date).Date.AddDays($decalage)
    if ($jour.DayOfWeek -eq [DayOfWeek]::Sunday) { $jour = $jour.AddDays(1) }
    return $jour.ToString("yyyy-MM-dd")
}

Write-Host "Scénario portail-patient sur $Url" -ForegroundColor White

# Tomcat répond avant la fin du chargement des données de test : on attend que PAT-0001 existe
$pret = $false
for ($i = 0; $i -lt 60 -and -not $pret; $i++) {
    try {
        Invoke-RestMethod -Uri "$Url/patients/PAT-0001" -UseBasicParsing | Out-Null
        $pret = $true
    } catch {
        Start-Sleep -Seconds 1
    }
}
if (-not $pret) {
    Write-Host "Service indisponible sur $Url (démarré ? données chargées ?)" -ForegroundColor Red
    exit 1
}

# ===================== 1. Profil =====================
Invoke-Api "Profil de PAT-0001" GET "/patients/PAT-0001" | Out-Null
Invoke-Api "Mise à jour des coordonnées" PUT "/patients/PAT-0001" -Corps ([ordered]@{
    telephone = "+216 98 123 456"; adresse = "12 rue de Marseille, 1000 Tunis" }) | Out-Null
Invoke-Api "Ordonnances actives" GET "/patients/PAT-0001/ordonnances?actives=true" | Out-Null

# ===================== 2. Médecins et créneaux =====================
Invoke-Api "Cardiologues" GET "/medecins?specialite=Cardiologie" | Out-Null
$jour = Get-JourConsultation 2
$presentiels = Invoke-Api "Créneaux PRESENTIEL libres de MED-001 le $jour" GET `
    "/creneaux?medecinId=MED-001&date=$jour&type=PRESENTIEL"
$teleconsultations = Invoke-Api "Créneaux TELECONSULTATION libres de MED-001 le $jour" GET `
    "/creneaux?medecinId=MED-001&date=$jour&type=TELECONSULTATION"
$creneau = @($presentiels)[0]
$creneauTele = @($teleconsultations)[0]

# ===================== 3. Rendez-vous =====================
$rdv = Invoke-Api "Prise de RDV sur $($creneau.id)" POST "/rendez-vous" -Attendu 201 -Corps ([ordered]@{
    identifiantPatient = "PAT-0001"; medecinId = "MED-001"; creneauId = $creneau.id
    type = "PRESENTIEL"; motif = "Contrôle trimestriel" })
Invoke-Api "Consultation du RDV" GET "/rendez-vous/$($rdv.id)" | Out-Null
Invoke-Api "Passage en téléconsultation sur $($creneauTele.id)" PUT "/rendez-vous/$($rdv.id)" -Corps ([ordered]@{
    creneauId = $creneauTele.id; type = "TELECONSULTATION"; motif = "Contrôle trimestriel (à distance)" }) | Out-Null
Invoke-Api "Annulation du RDV" DELETE "/rendez-vous/$($rdv.id)" -Attendu 204 | Out-Null
Invoke-Api "RDV de PAT-0001" GET "/rendez-vous?patientId=PAT-0001" | Out-Null

# ===================== 4. Urgences =====================
Invoke-Api "RDV d'urgence (alerte ÉLEVÉE), médecin traitant par défaut" POST "/rendez-vous" -Attendu 201 -Corps ([ordered]@{
    identifiantPatient = "PAT-0002"; type = "URGENCE"
    motif = "Alerte ÉLEVÉE : tension systolique 172 mmHg"; referenceAlerte = "ALR-2026-000317" }) | Out-Null

$demandeAmbulance = [ordered]@{
    identifiantPatient = "PAT-0001"; referenceAlerte = "ALR-2026-000318"
    motif = "Alerte CRITIQUE : fréquence cardiaque 142 bpm"
    position = [ordered]@{ latitude = 36.8008; longitude = 10.18 } }
# 409 possible si le script est relancé avant la fin de la mission précédente (simulation : ~90 s)
$mission = Invoke-Api "Demande d'ambulance (alerte CRITIQUE)" POST "/urgences/ambulances" `
    -Attendu 201, 409 -Corps $demandeAmbulance
$idMission = if ($mission.id) { $mission.id } elseif ($mission.message -match "AMB-\d{4}-\d{6}") { $Matches[0] }
Invoke-Api "Suivi de la mission $idMission" GET "/urgences/ambulances/$idMission" | Out-Null

# ===================== 5. Erreurs du contrat =====================
Invoke-Api "Patient inconnu" GET "/patients/PAT-0099" -Attendu 404 | Out-Null
Invoke-Api "Identifiant mal formé" GET "/patients/P1" -Attendu 400 | Out-Null
Invoke-Api "Email déjà utilisé" PUT "/patients/PAT-0001" -Attendu 409 -Corps @{ email = "leila.trabelsi@exemple.tn" } | Out-Null
$rdv2 = Invoke-Api "Nouveau RDV sur $($creneau.id)" POST "/rendez-vous" -Attendu 201 -Corps ([ordered]@{
    identifiantPatient = "PAT-0001"; medecinId = "MED-001"; creneauId = $creneau.id; type = "PRESENTIEL" })
Invoke-Api "Même créneau pour un autre patient" POST "/rendez-vous" -Attendu 409 -Corps ([ordered]@{
    identifiantPatient = "PAT-0004"; medecinId = "MED-001"; creneauId = $creneau.id; type = "PRESENTIEL" }) | Out-Null
Invoke-Api "Annulation" DELETE "/rendez-vous/$($rdv2.id)" -Attendu 204 | Out-Null
Invoke-Api "Annulation d'un RDV déjà annulé" DELETE "/rendez-vous/$($rdv2.id)" -Attendu 409 | Out-Null
Invoke-Api "Corps non conforme au contrat" POST "/rendez-vous" -Attendu 400 -Corps @{
    identifiantPatient = "X"; type = "PRESENTIEL" } | Out-Null
Invoke-Api "Ambulance déjà en route" POST "/urgences/ambulances" -Attendu 409 -Corps $demandeAmbulance | Out-Null
Invoke-Api "Mission inconnue" GET "/urgences/ambulances/AMB-2026-999999" -Attendu 404 | Out-Null

Write-Host ""
if ($script:echecs -gt 0) {
    Write-Host "$($script:echecs) étape(s) avec un résultat inattendu" -ForegroundColor Red
    exit 1
}
Write-Host "Scénario terminé : tous les résultats sont conformes." -ForegroundColor Green
