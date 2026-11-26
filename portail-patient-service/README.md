# portail-patient-service (REST, port 8082)

Service REST côté patient : profil, annuaire des médecins, créneaux, rendez-vous, ordonnances et
demandes d'ambulance.

## Rôle

Le patient (via le client web) gère ici ses rendez-vous et consulte ses ordonnances. L'orchestrateur appelle ce
service pour les alertes de télémétrie : alerte ÉLEVÉE → RDV d'urgence dans les 2 h, alerte CRITIQUE → ambulance.

### Endpoints

| Méthode | Chemin | Rôle |
|---|---|---|
| GET / PUT | `/patients/{id}` | Profil ; PUT ne modifie que les coordonnées (409 `EMAIL_DEJA_UTILISE`) |
| GET | `/patients/{id}/ordonnances?actives=` | Ordonnances ; `actives=true` → `dateFinValidite` ≥ aujourd'hui |
| GET | `/medecins?specialite=&ville=` | Annuaire |
| GET | `/creneaux?medecinId=&date=&type=&disponibles=` | Créneaux d'un médecin pour une date (libres par défaut) |
| GET / POST | `/rendez-vous` | Liste filtrable / prise de RDV (standard ou URGENCE) |
| GET / PUT / DELETE | `/rendez-vous/{id}` | Consultation / déplacement / annulation logique |
| POST | `/urgences/ambulances` | Demande d'ambulance |
| GET | `/urgences/ambulances/{id}` | Suivi d'une mission |

Chaque réponse 201 porte un en-tête `Location`, par exemple `/rendez-vous/RDV-2026-000042`.

### Règles métier

- **Identifiants** `RDV-`, `AMB-` et `ORD-AAAA-NNNNNN` : année et valeur suivante d'une séquence de base de données.
- **RDV standard** : `medecinId` et `creneauId` sont obligatoires. Le créneau doit :
  - exister (sinon 404) ;
  - appartenir au médecin (sinon 400 `CRENEAU_INCOHERENT`) ;
  - être du bon type (sinon 400 `TYPE_CRENEAU_INCOMPATIBLE`) ;
  - être libre (sinon 409 `CRENEAU_DEJA_RESERVE`).
- **RDV d'urgence** (`type = URGENCE`, sans `creneauId`) : le médecin traitant est pris par défaut, avec le premier
  créneau URGENCE libre qui commence dans les 2 h. Sinon, réponse 409 `AUCUN_CRENEAU_URGENCE`.
- **Double réservation** : deux réservations simultanées du même créneau donnent une 201 et une 409 (verrou optimiste).
- **PUT `/rendez-vous/{id}`** :
  - changer de créneau réserve le nouveau puis libère l'ancien ;
  - le nouveau créneau doit appartenir au même médecin (sinon 400 `CRENEAU_INCOHERENT`) : changer de médecin, c'est
    annuler puis recréer ;
  - `statut` peut seulement passer à `TERMINE` ou `ABSENT` ;
  - un RDV qui n'est plus CONFIRME n'est plus modifiable (409 `RDV_NON_MODIFIABLE`).
- **DELETE** : statut `ANNULE` et créneau libéré ; 409 `RDV_NON_ANNULABLE` si le RDV est déjà TERMINE ou ANNULE.
- **Ambulance** :
  - l'adresse est par défaut celle du profil ;
  - le véhicule est le premier libre parmi `SAMU-TN-01` à `05` ;
  - le délai estimé est tiré entre 8 et 20 min, et la mission est créée au statut `DEPECHEE` ;
  - 409 `MISSION_DEJA_EN_COURS` si une mission non terminée existe, 409 `AUCUN_VEHICULE_DISPONIBLE`
    si les 5 ambulances sont prises.
- **Simulation** : toutes les 30 s, chaque mission avance d'une étape (DEPECHEE → SUR_PLACE → TRANSPORT → TERMINEE).

### Erreurs

Toutes les erreurs, y compris les 404 de route et les 500, suivent le schéma `Erreur` du contrat, avec un code stable.
La liste complète figure dans `info.description` du contrat.

```json
{
  "horodatage": "2026-11-23T11:39:06+01:00",
  "statut": 400,
  "code": "REQUETE_INVALIDE",
  "message": "La requête contient des champs invalides",
  "chemin": "/rendez-vous",
  "details": [{ "champ": "identifiantPatient", "message": "doit correspondre à \"^PAT-\\d{4}$\"" }]
}
```

### Contrôle d'appartenance (bonus sécurité)

L'authentification est faite par la gateway (JWT Keycloak). Pour un utilisateur seulement PATIENT, elle pose
`X-Patient-Id` = claim `patient_id` du jeton (et supprime toujours celui qu'enverrait le client). Le portail
vérifie alors que le patient demandé est bien le sien (`web/Appartenance`) :

- `GET`/`PUT /patients/{id}`, ordonnances, `POST /rendez-vous`, `GET /rendez-vous?patientId=`, un RDV ou une
  ambulance d'un autre patient → **403 `ACCES_REFUSE`** (nouveau code, ajouté au contrat) ;
- sans `?patientId=`, la liste des RDV est filtrée sur le patient connecté ;
- **sans en-tête** (médecin, admin, orchestrateur sur le réseau interne) : accès complet, comme avant.

Le portail fait confiance à l'en-tête parce que seule la gateway peut le joindre (réseau Docker) : voir les limites
dans le README racine (zero trust, client credentials, mTLS).

### Données de test

Elles sont chargées au démarrage et calculées par rapport à la date du jour.

| Médecin | Spécialité | Établissement | Filière |
|---|---|---|---|
| MED-001 Mohamed Chaabane | Cardiologie | Hôpital Charles Nicolle, Tunis | PUBLIQUE |
| MED-002 Ines Haddad | Médecine générale | Cabinet Dr Haddad, Ariana | PRIVEE |
| MED-003 Sami Kallel | Pneumologie | Hôpital Abderrahmen Mami, Ariana | PUBLIQUE |

- **Patients** : les 5 patients de CLAUDE.md, avec la même identité que dans les autres services.
  Médecin traitant : PAT-0001 → MED-001, PAT-0003 → MED-003, les autres → MED-002.
- **Créneaux de 30 min sur 14 jours**, identifiant `CRN-aaaaMMjj-NNN-HHmm` :
  - du lundi au samedi, 09:00–12:00 et 14:00–17:00 : PRESENTIEL à l'heure pile, TELECONSULTATION à la demi-heure ;
  - garde URGENCE 24 h/24, décalée d'un quart d'heure (00:15, 00:45…) pour que l'identifiant reste unique.
- **Ordonnances** : 7 au total, dont 3 expirées.
- **Rendez-vous** : 3 rendez-vous à venir et 1 rendez-vous terminé.

## Contrat

- Contrat : [`docs/openapi/portail-patient.yaml`](../docs/openapi/portail-patient.yaml) (OpenAPI 3.0), copié à
  l'identique dans `src/main/resources/openapi/`.
- À chaque build, `openapi-generator-maven-plugin` (générateur `spring`, `interfaceOnly=true`) produit :
  - les **interfaces** `PatientsApi`, `MedecinsApi`, `CreneauxApi`, `RendezVousApi` et `UrgencesApi`, avec les
    routes, les paramètres et la validation (`@Pattern`, `@NotNull`…) ;
  - les **DTO** suffixés `Dto`.
- Swagger UI affiche le contrat lui-même : http://localhost:8082/swagger-ui.html
  (contrat publié sur http://localhost:8082/openapi/portail-patient.yaml).

## Lancer

```powershell
cd portail-patient-service
.\mvnw.cmd spring-boot:run
```

- **Profil par défaut** : H2 en mémoire. Console : http://localhost:8082/h2-console (`jdbc:h2:mem:portail`, utilisateur `sa`).
- **Profil `docker`** : PostgreSQL, configuré par `DB_URL`, `DB_USER` et `DB_PASSWORD`
  (par défaut `jdbc:postgresql://postgres:5432/portail`, `portail`, `portail`).

```powershell
docker build -t sahticonnect/portail-patient-service .
docker run -p 8082:8082 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/portail sahticonnect/portail-patient-service
```

## Tester

```powershell
# Tests unitaires des services (horloge fixe), tests MockMvc de tous les endpoints et codes d'erreur,
# test de concurrence (2 réservations simultanées → une 201, une 409)
# AppartenanceIntegrationTest (7) : X-Patient-Id d'un autre patient → 403 ACCES_REFUSE, sans en-tête → accès complet
.\mvnw.cmd verify

# Service démarré : profil → créneaux → RDV → modification → annulation → urgence → ambulance → erreurs
..\docs\tests\rest\tester-rest.ps1
```

La même chose se trouve sous forme de collection Postman dans
[`docs/tests/rest/portail-patient.postman_collection.json`](../docs/tests/rest/portail-patient.postman_collection.json)
(à lancer dans l'ordre avec le Collection Runner).

## Choix techniques

- **Contract-first avec openapi-generator** : les contrôleurs de `web/` implémentent les interfaces générées, et
  `PortailMapper` convertit les DTO en entités et inversement. Si le contrat change, le code qui ne le respecte plus
  ne compile plus.
- **`useTags=false`** : les interfaces sont nommées d'après le premier segment du chemin (`/medecins` → `MedecinsApi`).
  Le générateur retirerait les accents des tags (« Médecins » → `MdecinsApi`), alors que le contrat garde ses tags
  accentués pour Swagger UI. Les ordonnances (`/patients/{id}/ordonnances`) sont donc dans `PatientsApi`, ce qui est
  voulu : c'est une sous-ressource du patient.
- **Verrou optimiste** (`@Version` sur `Creneau`) : l'UPDATE vérifie la version lue. Si une autre transaction a
  réservé entre-temps, 0 ligne est modifiée, et le service répond 409 au lieu d'une double réservation.
- **Séquences de base** pour les identifiants, et une **`Clock` injectée** (fuseau `Africa/Tunis`) pour des tests
  déterministes.
- **Réponses en `charset=UTF-8`**, pour que les accents s'affichent correctement avec `Invoke-RestMethod` sous PowerShell 5.1.
