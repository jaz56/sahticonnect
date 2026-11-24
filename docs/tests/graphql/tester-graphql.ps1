<#
.SYNOPSIS
    Scénario GraphQL sur dashboard-medical-service : queries, mutations, erreurs et une subscription
    en WebSocket (protocole graphql-transport-ws).

.DESCRIPTION
    Compatible Windows PowerShell 5.1 et PowerShell 7. Peut être relancé : les identifiants d'alerte
    sont générés à chaque exécution. Si telemetrie-service ou dossier-cnam-service ne tourne pas,
    les champs concernés valent null avec une erreur SERVICE_INDISPONIBLE (réponse partielle).

.EXAMPLE
    .\tester-graphql.ps1
    .\tester-graphql.ps1 -ViaGateway     # HTTP et WebSocket via http(s)://localhost:8080/api/graphql
#>
param(
    [string]$Url = "http://localhost:8083/graphql",
    [switch]$ViaGateway,                              # toutes les URL passent par l'API Gateway
    [string]$Gateway = "http://localhost:8080"
)
if ($ViaGateway) { $Url = "$Gateway/api/graphql" }

$ErrorActionPreference = "Stop"
$script:echecs = 0

<# Envoie une opération GraphQL et affiche data / errors. Renvoie la réponse. #>
function Invoke-GraphQL {
    param(
        [string]$Titre,
        [string]$Requete,
        [hashtable]$Variables = @{},
        [string[]]$CodesAttendus = @()   # codes d'erreur attendus (extensions.code)
    )
    Write-Host ""
    Write-Host "--- $Titre" -ForegroundColor Cyan
    $corps = @{ query = $Requete; variables = $Variables } | ConvertTo-Json -Depth 10 -Compress
    $reponse = Invoke-RestMethod -Uri $Url -Method Post -UseBasicParsing `
        -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($corps))
    if ($reponse.data) { Write-Host ($reponse.data | ConvertTo-Json -Depth 10) }
    # (sous PowerShell 5.1, « $null | ForEach-Object » s'exécute une fois : on teste d'abord)
    $codes = if ($reponse.errors) { @($reponse.errors | ForEach-Object { $_.extensions.code }) } else { @() }
    foreach ($erreur in $reponse.errors) {
        $couleur = if ($CodesAttendus -contains $erreur.extensions.code -or $erreur.extensions.code -eq "SERVICE_INDISPONIBLE") { "Yellow" } else { "Red" }
        $service = if ($erreur.extensions.service) { " [$($erreur.extensions.service)]" } else { "" }
        Write-Host ("erreur {0}{1} sur {2} : {3}" -f $erreur.extensions.code, $service, ($erreur.path -join "."), $erreur.message) -ForegroundColor $couleur
    }
    $inattendues = @($codes | Where-Object { $_ -ne "SERVICE_INDISPONIBLE" -and $CodesAttendus -notcontains $_ })
    $manquantes = @($CodesAttendus | Where-Object { $codes -notcontains $_ })
    if ($inattendues.Count -gt 0 -or $manquantes.Count -gt 0) {
        Write-Host "Résultat inattendu (attendu : $($CodesAttendus -join ', '))" -ForegroundColor Red
        $script:echecs++
    }
    return $reponse
}

<# Subscription en WebSocket : s'abonne, exécute $Declencheur, attend un message « next ». #>
function Test-Subscription {
    param([string]$Titre, [string]$Requete, [scriptblock]$Declencheur)
    Write-Host ""
    Write-Host "--- $Titre (WebSocket graphql-transport-ws)" -ForegroundColor Cyan
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ws.Options.AddSubProtocol("graphql-transport-ws")
    $annulation = [System.Threading.CancellationToken]::None
    $tampon = New-Object byte[] 65536

    function Send-Message($objet) {
        $octets = [System.Text.Encoding]::UTF8.GetBytes(($objet | ConvertTo-Json -Depth 10 -Compress))
        $segment = New-Object System.ArraySegment[byte] -ArgumentList @(, $octets)
        $null = $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $annulation).GetAwaiter().GetResult()
    }
    function Receive-Message([int]$delaiMs) {
        $segment = New-Object System.ArraySegment[byte] -ArgumentList @(, $tampon)
        $tache = $ws.ReceiveAsync($segment, $annulation)
        if (-not $tache.Wait($delaiMs)) { return $null }
        return ([System.Text.Encoding]::UTF8.GetString($tampon, 0, $tache.Result.Count) | ConvertFrom-Json)
    }

    try {
        $null = $ws.ConnectAsync([Uri]($Url -replace "^http", "ws"), $annulation).GetAwaiter().GetResult()
        Send-Message @{ type = "connection_init" }
        $ack = Receive-Message 5000
        if ($ack.type -ne "connection_ack") { throw "pas de connection_ack" }
        Send-Message @{ id = "1"; type = "subscribe"; payload = @{ query = $Requete } }
        Start-Sleep -Milliseconds 300
        & $Declencheur | Out-Null
        $message = Receive-Message 5000
        if ($message -and $message.type -eq "next") {
            Write-Host "Événement reçu :" -ForegroundColor Green
            Write-Host ($message.payload.data | ConvertTo-Json -Depth 10)
        } else {
            Write-Host "Aucun événement reçu dans les 5 s" -ForegroundColor Red
            $script:echecs++
        }
        Send-Message @{ id = "1"; type = "complete" }
        $null = $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "fin", $annulation).GetAwaiter().GetResult()
    } catch {
        Write-Host "Subscription en échec : $($_.Exception.Message)" -ForegroundColor Red
        $script:echecs++
    } finally {
        $ws.Dispose()
    }
}

# Identifiants d'alerte uniques à chaque exécution (ALR-AAAA-8NNNNN)
$annee = (Get-Date).Year
$suffixe = (Get-Date).Ticks % 100000
$idAlerte = "ALR-$annee-8{0:D5}" -f $suffixe
$idAlerteSub = "ALR-$annee-7{0:D5}" -f $suffixe

Write-Host "Scénario GraphQL sur $Url" -ForegroundColor White

# Le service répond avant la fin du chargement des données de démonstration : on attend PAT-0001
$pret = $false
for ($i = 0; $i -lt 60 -and -not $pret; $i++) {
    try {
        $r = Invoke-RestMethod -Uri $Url -Method Post -UseBasicParsing -ContentType "application/json" `
            -Body '{"query":"{ patient(id: \"PAT-0001\") { id } }"}'
        $pret = $null -ne $r.data.patient
    } catch { }
    if (-not $pret) { Start-Sleep -Seconds 1 }
}
if (-not $pret) { Write-Host "Service indisponible sur $Url" -ForegroundColor Red; exit 1 }

# ===================== Queries =====================
Invoke-GraphQL "Fiche patient complète (local + gRPC + SOAP) en une requête" @'
query { patient(id: "PAT-0001") {
  nom age service niveauRisque medecinReferent couvert tauxRemboursement
  constantes(limite: 3) { type valeur niveau }
  alertes { id niveau statut intervention { id type etat } }
  traitements { medicament }
} }
'@ | Out-Null

Invoke-GraphQL "Filtre : cardiologie" '{ patients(filtre: { service: "Cardiologie" }) { id nom niveauRisque } }' | Out-Null
Invoke-GraphQL "Filtre : risque CRITIQUE" '{ patients(filtre: { niveauRisque: CRITIQUE }) { id nom service } }' | Out-Null
Invoke-GraphQL "Historique paginé (page 0, taille 2)" @'
{ historiqueSoins(patientId: "PAT-0004", page: 0, taille: 2) { totalElements totalPages elements { date diagnostic } } }
'@ | Out-Null

# ===================== Mutations =====================
$alerte = @{ id = $idAlerte; patientId = "PAT-0004"; niveau = "CRITIQUE"; typeConstante = "SPO2"; valeur = 86;
    seuilFranchi = "SpO2 < 90"; message = "Alerte CRITIQUE : saturation 86 %"; horodatage = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz") }
$mutationAlerte = 'mutation($input: EnregistrerAlerteInput!) { enregistrerAlerte(input: $input) { id statut niveau } }'
Invoke-GraphQL "enregistrerAlerte $idAlerte" $mutationAlerte @{ input = $alerte } | Out-Null
Invoke-GraphQL "enregistrerAlerte rejouée (idempotence : même alerte, pas de doublon)" $mutationAlerte @{ input = $alerte } | Out-Null
Invoke-GraphQL "Niveau de risque recalculé" '{ patient(id: "PAT-0004") { niveauRisque alertes { id statut } } }' | Out-Null

$intervention = Invoke-GraphQL "creerIntervention (alerte → PRISE_EN_CHARGE)" @'
mutation($alerte: ID!) { creerIntervention(input: { patientId: "PAT-0004", type: AMBULANCE, alerteId: $alerte, referenceExterne: "AMB-2026-000007" }) {
  id etat statutFacturation alerte { id statut } } }
'@ @{ alerte = $idAlerte }
$idIntervention = $intervention.data.creerIntervention.id

Invoke-GraphQL "mettreAJourIntervention $idIntervention" @'
mutation($id: ID!) { mettreAJourIntervention(id: $id, input: { etat: FACTUREE, statutFacturation: FACTUREE, montantTotal: 60, partCNAM: 30, partPatient: 30 }) {
  id etat statutFacturation montantTotal dateMiseAJour } }
'@ @{ id = $idIntervention } | Out-Null

# ===================== Erreurs =====================
Invoke-GraphQL "NOT_FOUND : intervention inconnue" 'mutation { mettreAJourIntervention(id: "INT-2026-999999", input: { etat: ECHEC }) { id } }' -CodesAttendus NOT_FOUND | Out-Null
Invoke-GraphQL "NOT_FOUND : patient inconnu" 'mutation { creerIntervention(input: { patientId: "PAT-0099", type: AMBULANCE }) { id } }' -CodesAttendus NOT_FOUND | Out-Null
Invoke-GraphQL "BAD_REQUEST : taille de page invalide" '{ historiqueSoins(patientId: "PAT-0001", taille: 0) { totalElements } }' -CodesAttendus BAD_REQUEST | Out-Null

# ===================== Subscription =====================
Test-Subscription "nouvelleAlerte (PAT-0005, niveau ≥ CRITIQUE)" `
    'subscription { nouvelleAlerte(patientId: "PAT-0005", niveauMinimum: CRITIQUE) { id patientId niveau message } }' {
    $a = @{ id = $idAlerteSub; patientId = "PAT-0005"; niveau = "CRITIQUE"; typeConstante = "CHUTE"; valeur = 1;
        seuilFranchi = "Chute détectée"; message = "Alerte CRITIQUE : chute détectée"; horodatage = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz") }
    $corps = @{ query = $mutationAlerte; variables = @{ input = $a } } | ConvertTo-Json -Depth 10 -Compress
    Invoke-RestMethod -Uri $Url -Method Post -UseBasicParsing -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($corps))
}

Write-Host ""
if ($script:echecs -gt 0) {
    Write-Host "$($script:echecs) étape(s) avec un résultat inattendu" -ForegroundColor Red
    exit 1
}
Write-Host "Scénario terminé : tous les résultats sont conformes." -ForegroundColor Green
