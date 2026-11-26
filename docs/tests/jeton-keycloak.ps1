<#
.SYNOPSIS
    Jeton d'accès Keycloak pour les scripts de test lancés avec -ViaGateway (à charger par dot-sourcing).

.DESCRIPTION
    La gateway exige un jeton JWT (Authorization: Bearer). Un script n'a pas de navigateur pour suivre le parcours
    Authorization Code + PKCE : il utilise le client « scripts » du realm, seul client autorisé à échanger
    directement un identifiant et un mot de passe contre un jeton (Direct Access Grants, « password grant »).
    Ce client est RÉSERVÉ AUX SCRIPTS DE TEST : le password grant est déconseillé (OAuth 2.1 le retire) car
    l'application voit le mot de passe. Le client web, lui, ne l'autorise pas.

    Enable-JetonGateway installe des valeurs par défaut pour Invoke-RestMethod et Invoke-WebRequest (portée du script
    appelant) : chaque appel reçoit l'en-tête Authorization, avec un jeton renouvelé automatiquement avant son
    expiration (5 min). Get-JetonCourant donne le jeton brut (ex. WebSocket : ?access_token=...).

.EXAMPLE
    . (Join-Path $PSScriptRoot "..\jeton-keycloak.ps1")
    Enable-JetonGateway -Utilisateur admin -Keycloak http://localhost:8180
#>

$script:JetonKeycloak = $null
$script:JetonExpiration = [DateTime]::MinValue
$script:JetonUtilisateur = "admin"
$script:JetonMotDePasse = "demo"
$script:JetonUrlKeycloak = "http://localhost:8180"

function Get-JetonCourant {
    # 30 s de marge : un jeton qui expire pendant la requête serait refusé (401)
    if ($null -eq $script:JetonKeycloak -or [DateTime]::UtcNow -ge $script:JetonExpiration.AddSeconds(-30)) {
        $corps = @{
            grant_type = "password"; client_id = "scripts"
            username = $script:JetonUtilisateur; password = $script:JetonMotDePasse
        }
        try {
            # -Headers @{} explicite : pas d'en-tête Authorization (valeurs par défaut) vers Keycloak
            $reponse = Invoke-RestMethod -Method Post -UseBasicParsing -Headers @{} `
                -Uri "$($script:JetonUrlKeycloak)/realms/sahticonnect/protocol/openid-connect/token" `
                -ContentType "application/x-www-form-urlencoded" -Body $corps
        } catch {
            throw "Impossible d'obtenir un jeton Keycloak pour '$($script:JetonUtilisateur)' ($($script:JetonUrlKeycloak)) : $($_.Exception.Message). Keycloak est-il démarré (docker compose up -d keycloak) ?"
        }
        $script:JetonKeycloak = $reponse.access_token
        $script:JetonExpiration = [DateTime]::UtcNow.AddSeconds([int]$reponse.expires_in)
    }
    return $script:JetonKeycloak
}

function Get-EntetesAuth {
    return @{ Authorization = "Bearer $(Get-JetonCourant)" }
}

function Enable-JetonGateway {
    param(
        [string]$Utilisateur = "admin",
        [string]$MotDePasse = "demo",
        [string]$Keycloak = "http://localhost:8180"
    )
    $script:JetonUtilisateur = $Utilisateur
    $script:JetonMotDePasse = $MotDePasse
    $script:JetonUrlKeycloak = $Keycloak
    $script:JetonKeycloak = $null
    $null = Get-JetonCourant   # échoue tout de suite si Keycloak est injoignable ou le compte inconnu
    Write-Host "Jeton Keycloak obtenu pour '$Utilisateur' (client scripts, réservé aux tests)" -ForegroundColor DarkGray
    # valeurs par défaut évaluées à chaque appel (bloc de script) : jeton toujours valide
    $script:PSDefaultParameterValues = @{
        "Invoke-RestMethod:Headers" = { Get-EntetesAuth }
        "Invoke-WebRequest:Headers" = { Get-EntetesAuth }
    }
}
