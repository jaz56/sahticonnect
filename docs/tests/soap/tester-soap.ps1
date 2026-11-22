<#
.SYNOPSIS
    Envoie les requêtes SOAP d'exemple (*.xml de ce dossier) au service dossier-cnam
    et affiche les réponses (ou les SOAP Faults) mises en forme.

.EXAMPLE
    .\tester-soap.ps1
    .\tester-soap.ps1 -Url http://localhost:8080/ws    # via la gateway, plus tard
#>
param(
    [string]$Url = "http://localhost:8081/ws"
)

$ErrorActionPreference = "Stop"

function Format-Xml([string]$texte) {
    try {
        $doc = New-Object System.Xml.XmlDocument
        $doc.LoadXml($texte)
        $sortie = New-Object System.IO.StringWriter
        $ecrivain = New-Object System.Xml.XmlTextWriter($sortie)
        $ecrivain.Formatting = [System.Xml.Formatting]::Indented
        $ecrivain.Indentation = 2
        $doc.WriteContentTo($ecrivain)
        $ecrivain.Flush()
        return $sortie.ToString()
    } catch {
        return $texte
    }
}

function Read-CorpsErreur($erreur) {
    # Un SOAP Fault est renvoyé avec le statut HTTP 500 : on récupère quand même le corps XML.
    # Windows PowerShell 5.1 a déjà lu le flux : on le rembobine (ErrorDetails perd les balises).
    $reponse = $erreur.Exception.Response
    if ($null -ne $reponse) {
        $flux = $reponse.GetResponseStream()
        if ($flux.CanSeek) { $flux.Position = 0 }
        $texte = (New-Object System.IO.StreamReader($flux, [System.Text.Encoding]::UTF8)).ReadToEnd()
        if ($texte) { return $texte }
    }
    if ($erreur.ErrorDetails -and $erreur.ErrorDetails.Message) { return $erreur.ErrorDetails.Message }
    return $erreur.Exception.Message
}

$fichiers = Get-ChildItem -Path $PSScriptRoot -Filter *.xml | Sort-Object Name
$echecs = 0

foreach ($fichier in $fichiers) {
    Write-Host ""
    Write-Host ("=" * 80) -ForegroundColor DarkGray
    Write-Host "Requête : $($fichier.Name)" -ForegroundColor Cyan

    $corps = [System.IO.File]::ReadAllBytes($fichier.FullName)
    try {
        $reponse = Invoke-WebRequest -Uri $Url -Method Post -Body $corps `
            -ContentType "text/xml; charset=utf-8" -Headers @{ SOAPAction = '""' } -UseBasicParsing
        $texte = [System.Text.Encoding]::UTF8.GetString($reponse.RawContentStream.ToArray())
        Write-Host "HTTP $($reponse.StatusCode)" -ForegroundColor Green
    } catch [System.Net.WebException] {
        $texte = Read-CorpsErreur $_
        $statut = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "?" }
        if ($texte -match "Fault") {
            Write-Host "HTTP $statut - SOAP Fault" -ForegroundColor Yellow
        } else {
            Write-Host "HTTP $statut - erreur" -ForegroundColor Red
            $echecs++
        }
    }
    Write-Host (Format-Xml $texte)
}

Write-Host ""
if ($echecs -gt 0) {
    Write-Host "$echecs requête(s) en erreur (service démarré sur $Url ?)" -ForegroundColor Red
    exit 1
}
Write-Host "$($fichiers.Count) requête(s) envoyée(s)." -ForegroundColor Green
