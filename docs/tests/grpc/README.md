# Tester telemetrie-service à la main (grpcurl, Postman)

Le service doit être démarré (`.\mvnw.cmd spring-boot:run` dans `telemetrie-service`) et écouter sur `localhost:9090`.
La réflexion gRPC est activée : ni grpcurl ni Postman n'ont besoin du fichier `.proto`.

## grpcurl

Installation : télécharger `grpcurl_<version>_windows_x86_64.zip` depuis la page *Releases* du dépôt GitHub
`fullstorydev/grpcurl`, puis placer `grpcurl.exe` dans un dossier du `PATH`.

> **PowerShell** : les guillemets d'un JSON passé en argument (`-d '{"a":1}'`) sont altérés avant
> d'arriver à un programme externe sous Windows PowerShell 5.1. Les commandes ci-dessous envoient donc le JSON
> sur l'entrée standard (`-d '@'`) avec une *here-string* `@' … '@`. Le `'@` final doit être en début de ligne.
> Elles fonctionnent aussi sous PowerShell 7.

### Découverte (réflexion) et santé

```powershell
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9090 describe sahticonnect.telemetrie.Telemetrie
grpcurl -plaintext localhost:9090 describe sahticonnect.telemetrie.Mesure
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
```

### EnvoyerMesures (bidirectionnel)

Chaque objet JSON est un message du flux, et chaque message reçoit son accusé :
mesure normale, puis alerte CRITIQUE, puis mesure filtrée par l'anti-rebond, puis rejet.

```powershell
@'
{ "identifiantPatient": "PAT-0001", "capteurId": "CAP-CLI", "type": "FREQUENCE_CARDIAQUE", "valeur": 72, "unite": "bpm", "sequence": 1 }
{ "identifiantPatient": "PAT-0001", "capteurId": "CAP-CLI", "type": "FREQUENCE_CARDIAQUE", "valeur": 142, "unite": "bpm", "sequence": 2 }
{ "identifiantPatient": "PAT-0001", "capteurId": "CAP-CLI", "type": "FREQUENCE_CARDIAQUE", "valeur": 145, "unite": "bpm", "sequence": 3 }
{ "identifiantPatient": "PAT-0099", "capteurId": "CAP-CLI", "type": "SPO2", "valeur": 97, "unite": "%", "sequence": 4 }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/EnvoyerMesures
```

### SouscrireAlertes (server streaming)

Le flux reste ouvert : lancer les mesures dans un autre terminal, puis quitter avec Ctrl+C.

```powershell
# Toutes les alertes : sans -d, grpcurl envoie une requête vide (niveau minimum non spécifié = ELEVEE)
grpcurl -plaintext localhost:9090 sahticonnect.telemetrie.Telemetrie/SouscrireAlertes
```

Avec un filtre :

```powershell
@'
{ "identifiantsPatients": ["PAT-0001", "PAT-0003"], "niveauMinimum": "CRITIQUE" }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/SouscrireAlertes
```

### DernieresMesures (unaire)

```powershell
# 10 dernières mesures (par défaut) de chaque type
@'
{ "identifiantPatient": "PAT-0001" }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/DernieresMesures

# 5 dernières fréquences cardiaques
@'
{ "identifiantPatient": "PAT-0001", "types": ["FREQUENCE_CARDIAQUE"], "nombre": 5 }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/DernieresMesures
```

### Erreurs attendues

```powershell
# NOT_FOUND : patient non suivi
@'
{ "identifiantPatient": "PAT-0099" }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/DernieresMesures

# INVALID_ARGUMENT : identifiant mal formé, nombre hors 0–100
@'
{ "identifiantPatient": "P1" }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/DernieresMesures

@'
{ "identifiantPatient": "PAT-0001", "nombre": 500 }
'@ | grpcurl -plaintext -d '@' localhost:9090 sahticonnect.telemetrie.Telemetrie/DernieresMesures
```

## Postman (requête gRPC)

1. **New → gRPC**, puis saisir l'URL `localhost:9090` (connexion non chiffrée : laisser TLS désactivé).
2. Choisir la méthode :
   - **avec la réflexion** : onglet *Service definition* → *Using server reflection*. La liste
     `sahticonnect.telemetrie.Telemetrie/…` apparaît ;
   - **ou en important le contrat** : *Service definition* → *Import .proto file* → choisir
     `docs/proto/telemetrie.proto`. `google/protobuf/timestamp.proto` fait partie des types standard connus de Postman.
3. **Message** : coller un JSON, par exemple `{ "identifiantPatient": "PAT-0001", "nombre": 5 }` pour `DernieresMesures`.
   *Use Example Message* génère un modèle.
4. Selon le type d'appel :
   - **unaire** : *Invoke* ;
   - **SouscrireAlertes** : *Invoke*, puis les alertes arrivent dans l'onglet *Response* tant que l'appel reste ouvert ;
   - **EnvoyerMesures** : *Invoke* ouvre le flux. Chaque *Send* envoie une mesure et son accusé s'affiche.
     *End streaming* ferme le flux, et le serveur répond `onCompleted`.
