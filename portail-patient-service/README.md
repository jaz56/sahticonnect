# portail-patient-service (REST, port 8082)

Service REST de SahtiConnect côté patient : profil, annuaire des médecins, créneaux, rendez-vous,
ordonnances et demandes d'ambulance. L'orchestrateur l'appelle pour les alertes de télémétrie :
alerte ÉLEVÉE → RDV d'urgence dans les 2 h, alerte CRITIQUE → ambulance.

## Contract-first

Le contrat [`docs/openapi/portail-patient.yaml`](../docs/openapi/portail-patient.yaml) est la source de vérité.
Sa copie, `src/main/resources/openapi/portail-patient.yaml`, doit rester identique.

À chaque build, `openapi-generator-maven-plugin` (générateur `spring`, `interfaceOnly=true`) produit
dans `target/generated-sources/openapi` :
- les **interfaces** `PatientsApi`, `MedecinsApi`, `CreneauxApi`, `RendezVousApi` et `UrgencesApi`,
  avec les routes, les paramètres et les annotations de validation (`@Pattern`, `@NotNull`…) ;
- les **DTO** du package `tn.sahticonnect.portail.api.model`, suffixés `Dto` pour ne pas les confondre
  avec les entités.

Les contrôleurs de `web/` implémentent ces interfaces et `PortailMapper` convertit les DTO en entités et inversement.

`useTags=false` : les interfaces sont nommées d'après le premier segment du chemin (`/medecins` → `MedecinsApi`)
et non d'après les tags. Le générateur retire les accents des tags (« Médecins » donnerait `MdecinsApi`), alors
que le contrat garde ses tags accentués, qui servent à l'affichage dans Swagger UI. Les ordonnances
(`/patients/{id}/ordonnances`) se retrouvent donc dans `PatientsApi`, ce qui est voulu : c'est une sous-ressource du patient.

- Swagger UI : http://localhost:8082/swagger-ui.html (affiche le contrat lui-même)
- Contrat publié : http://localhost:8082/openapi/portail-patient.yaml

## Endpoints

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

## Règles métier

- **Identifiants** `RDV-AAAA-NNNNNN`, `AMB-AAAA-NNNNNN`, `ORD-AAAA-NNNNNN` : année et valeur suivante d'une
  séquence de base de données (`schema.sql`, même syntaxe sous H2 et PostgreSQL).
- **RDV standard** : `medecinId` et `creneauId` sont obligatoires. Le créneau doit exister (sinon 404),
  appartenir au médecin (sinon 400 `CRENEAU_INCOHERENT`), être du bon type (sinon 400
  `TYPE_CRENEAU_INCOMPATIBLE`) et être libre (sinon 409 `CRENEAU_DEJA_RESERVE`).
- **RDV d'urgence** (`type = URGENCE`, sans `creneauId`) : le médecin traitant est pris par défaut, avec
  le premier créneau URGENCE libre qui commence dans les 2 h. S'il n'y en a pas, réponse 409 `AUCUN_CRENEAU_URGENCE`.
- **Verrou optimiste** : `@Version` sur `Creneau`. Si deux réservations simultanées visent le même créneau,
  une seule réussit et l'autre reçoit 409 `CRENEAU_DEJA_RESERVE`.
- **PUT `/rendez-vous/{id}`** : changer de créneau réserve le nouveau puis libère l'ancien. Un `statut`
  peut seulement passer à `TERMINE` ou `ABSENT` (clôture). Un RDV qui n'est plus CONFIRME n'est plus
  modifiable (409 `RDV_NON_MODIFIABLE`).
- **DELETE** : statut `ANNULE` et créneau libéré ; 409 `RDV_NON_ANNULABLE` si le RDV est déjà TERMINE ou ANNULE.
- **Ambulance** :
  - par défaut, l'adresse est celle du profil ;
  - le véhicule est le premier libre parmi `SAMU-TN-01` à `05`, et le délai estimé est tiré entre 8 et 20 min ;
  - la mission est créée au statut `DEPECHEE` ;
  - réponse 409 `MISSION_DEJA_EN_COURS` si une mission non terminée existe pour ce patient, 409
    `AUCUN_VEHICULE_DISPONIBLE` si les 5 ambulances sont prises.
- **Simulation** : `ProgressionAmbulances` (`@Scheduled`) fait avancer chaque mission
  DEPECHEE → SUR_PLACE → TRANSPORT → TERMINEE toutes les 30 s. Elle se désactive avec
  `portail.ambulances.simulation.active=false`, ce qui est le cas pendant les tests.
- L'heure courante vient d'une `Clock` injectée (fuseau `Africa/Tunis`). Les tests unitaires la remplacent
  par une horloge fixe.

## Erreurs

Toutes les erreurs, y compris les 404 de route et les 500, suivent le schéma `Erreur` du contrat :

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

## Données de test

Elles sont chargées au démarrage et calculées par rapport à la date du jour.

| Médecin | Spécialité | Établissement | Filière |
|---|---|---|---|
| MED-001 Mohamed Chaabane | Cardiologie | Hôpital Charles Nicolle, Tunis | PUBLIQUE |
| MED-002 Ines Haddad | Médecine générale | Cabinet Dr Haddad, Ariana | PRIVEE |
| MED-003 Sami Kallel | Pneumologie | Hôpital Abderrahmen Mami, Ariana | PUBLIQUE |

- **Patients** : les 5 patients de CLAUDE.md, avec la même identité que dans `dossier-cnam-service`.
  Médecin traitant : PAT-0001 → MED-001, PAT-0003 → MED-003, les autres → MED-002.
- **Créneaux de 30 min sur 14 jours**, identifiant `CRN-aaaaMMjj-NNN-HHmm` :
  - du lundi au samedi, 09:00–12:00 et 14:00–17:00 : PRESENTIEL à l'heure pile et TELECONSULTATION à la
    demi-heure (MED-003 ne fait pas de téléconsultation) ;
  - garde URGENCE 24 h/24, **décalée d'un quart d'heure** (00:15, 00:45…) pour que l'identifiant reste
    unique sans changer son format.
- **Ordonnances** : 7 au total, dont 3 expirées.
- **Rendez-vous** : 3 rendez-vous à venir et 1 rendez-vous terminé.

## Lancer

```powershell
cd portail-patient-service
.\mvnw.cmd spring-boot:run
```

Profil par défaut : H2 en mémoire. Console : http://localhost:8082/h2-console
(JDBC URL `jdbc:h2:mem:portail`, utilisateur `sa`).

Profil `docker` : PostgreSQL, configuré par `DB_URL`, `DB_USER` et `DB_PASSWORD`
(par défaut `jdbc:postgresql://postgres-portail:5432/portail`, `portail`, `portail`).

```powershell
docker build -t sahticonnect/portail-patient-service .
docker run -p 8082:8082 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/portail sahticonnect/portail-patient-service
```

## Tester

```powershell
# Tests unitaires des services, tests d'intégration MockMvc (tous les endpoints et codes d'erreur)
# et test de concurrence (2 réservations simultanées → une 201, une 409)
.\mvnw.cmd verify

# Service démarré : scénario complet profil → créneaux → RDV → modification → annulation → urgence → ambulance → erreurs
..\docs\tests\rest\tester-rest.ps1
```

Une collection Postman équivalente se trouve dans `docs/tests/rest/portail-patient.postman_collection.json`
(à lancer dans l'ordre avec le Collection Runner).
