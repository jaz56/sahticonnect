# dashboard-medical-service (GraphQL, port 8083)

Service GraphQL de SahtiConnect pour le médecin. Il donne une vue consolidée de chaque patient en une seule
requête :
- les données locales (patients, alertes, interventions, traitements) ;
- les dernières constantes, demandées en **gRPC** à `telemetrie-service` ;
- les droits CNAM et l'historique de soins, demandés en **SOAP** à `dossier-cnam-service`.

L'orchestrateur y enregistre les alertes et les interventions, et les écrans s'abonnent aux nouveautés
par des **subscriptions**.

- GraphQL (HTTP) : `POST http://localhost:8083/graphql`
- Subscriptions : `ws://localhost:8083/graphql`, protocole `graphql-transport-ws`
- GraphiQL : http://localhost:8083/graphiql (exemples dans [`docs/tests/graphql/requetes.graphql`](../docs/tests/graphql/requetes.graphql))
- Schéma : http://localhost:8083/graphql/schema

## Contrat (schema-first)

[`docs/graphql/dashboard-medical.graphqls`](../docs/graphql/dashboard-medical.graphqls), copié à l'identique dans
`src/main/resources/graphql/`. Chaque champ indique sa source en commentaire. Les champs qui viennent d'un autre
service sont **nullables**, pour permettre les réponses partielles. Scalaires `Date` et `DateTime` :
graphql-java-extended-scalars.

Les contrats des services appelés sont copiés de la même façon :
- `src/main/proto/telemetrie.proto` : stubs gRPC ;
- `src/main/xsd/dossier-cnam.xsd` : classes JAXB.

Ils sont générés au build, et l'image Docker se construit depuis le seul dossier du service.

## Sources des données

| Champ | Source | Résolution |
|---|---|---|
| patients, alertes, interventions, traitements | base locale (H2 / PostgreSQL) | chargement groupé (pas de N+1) |
| `PatientSuivi.constantes` | gRPC `DernieresMesures` (`sahti.telemetrie.adresse`, client Spring gRPC 0.12) | `@SchemaMapping`, deadline 3 s |
| `PatientSuivi.couvert`, `tauxRemboursement` | SOAP `consulterHistorique` (vignette) puis `verifierDroitsCNAM` avec un acte « C 1 » | `@SchemaMapping`, timeouts 3 s |
| `Query.historiqueSoins` | SOAP `consulterHistorique`, paginé par le dashboard | timeouts 3 s |

- Les champs distants ne sont appelés **que si le client les demande** : une requête `{ patients { nom } }`
  ne contacte aucun autre service.
- Dans une même requête, les appels SOAP sont partagés. Demander `couvert`, `tauxRemboursement` et
  `historiqueSoins` ensemble ne fait qu'un `consulterHistorique` et un `verifierDroitsCNAM`.
- Les champs distants sont résolus en parallèle (un thread virtuel par appel).
- `ConstanteVitale.niveau` vient de `Mesure.niveau`, calculé par `telemetrie-service`. Le dashboard n'applique
  aucun seuil : la règle existe à un seul endroit.

## Réponses partielles

Un service ne répond pas (panne, délai de 3 s dépassé) : le champ vaut `null`, une erreur est ajoutée et le reste
de la réponse est renvoyé normalement.

```json
{
  "errors": [{
    "message": "Service dossier-cnam indisponible : Connection refused: getsockopt",
    "path": ["patient", "couvert"],
    "extensions": { "code": "SERVICE_INDISPONIBLE", "service": "dossier-cnam", "classification": "INTERNAL_ERROR" }
  }],
  "data": { "patient": { "nom": "Trabelsi", "niveauRisque": "ELEVE", "couvert": null, "alertes": [ … ] } }
}
```

Autres erreurs (`extensions.code`) :
- `NOT_FOUND` : patient, alerte ou intervention inconnu dans une mutation ou `historiqueSoins` ;
  `patient(id)` renvoie simplement `null` ;
- `BAD_REQUEST` : données invalides (identifiant, pagination, montant négatif…).

## Chargement groupé (problème N+1)

Les champs de liste et de relation sont chargés en une requête SQL pour toute la réponse, et non une par patient :
- `@BatchMapping` pour `PatientSuivi.interventions`, `Intervention.patient`, `Intervention.alerte` et `Alerte.intervention` ;
- `PatientSuivi.alertes(statut)` et `traitements(actifs)` ont un argument, que `@BatchMapping` ne transmet pas.
  Ils utilisent donc le même mécanisme : un DataLoader enregistré dans `ChargeursGroupes`, appelé depuis un
  `@SchemaMapping` qui filtre ensuite selon l'argument.

Le test `chargementGroupe_nombreDeRequetesSqlIndependantDuNombreDePatients` vérifie, par les statistiques
Hibernate, qu'une requête imbriquée fait le même nombre de requêtes SQL (8) pour 1 patient et pour 5.

## Mutations et subscriptions

| Opération | Comportement |
|---|---|
| `enregistrerAlerte` | **Idempotente** : le même identifiant `ALR-…` envoyé deux fois renvoie l'alerte existante, sans doublon ni nouvel événement. L'orchestrateur peut donc réessayer. Le niveau de risque est ensuite recalculé. |
| `creerIntervention` | Identifiant `INT-AAAA-NNNNNN` (séquence `intervention_seq`). Valeurs par défaut `ALERTE_RECUE` et `EN_ATTENTE`. L'alerte liée passe en `PRISE_EN_CHARGE`. L'intervention est publiée sur `nouvelleIntervention`. |
| `mettreAJourIntervention` | Seuls les champs fournis changent ; `dateMiseAJour` est mise à jour. |
| `nouvelleAlerte(patientId, niveauMinimum)` | Flux Reactor (Sinks multicast) filtré ; `niveauMinimum` par défaut `ELEVEE`. |
| `nouvelleIntervention(patientId, service)` | Flux filtré par patient et par service hospitalier. |

**Niveau de risque**, stocké et recalculé à chaque alerte enregistrée ou changement de statut d'alerte :
- `CRITIQUE` : une alerte CRITIQUE non résolue dans les 24 h ;
- `ELEVE` : une alerte ELEVEE non résolue dans les 24 h ;
- `MODERE` : au moins une alerte dans les 7 jours ;
- `FAIBLE` sinon.

## Données de démonstration

| Patient | Service | Médecin référent | Démo |
|---|---|---|---|
| PAT-0001 Ahmed Ben Salah | Cardiologie | Dr Mohamed Chaabane | Alerte CRITIQUE (FC 142) prise en charge, ambulance facturée → risque CRITIQUE |
| PAT-0002 Leila Trabelsi | Cardiologie | Dr Ines Haddad | Alerte ÉLEVÉE (systolique 172), RDV d'urgence **à régulariser** (vignette expirée) → ELEVE |
| PAT-0003 Karim Jaziri | Pneumologie | Dr Sami Kallel | Alerte résolue il y a 3 jours → MODERE |
| PAT-0004 Sonia Mansour | Médecine interne | Dr Ines Haddad | Aucune alerte → FAIBLE |
| PAT-0005 Hedi Gharbi | Médecine interne | Dr Ines Haddad | Chute il y a 30 min, non prise en charge → CRITIQUE |

Les traitements correspondent aux ordonnances de `portail-patient-service`. Les alertes de démonstration ont des
identifiants `ALR-AAAA-9NNNNN`, distincts de ceux que produit `telemetrie-service`.

## Lancer

```powershell
cd dashboard-medical-service
.\mvnw.cmd spring-boot:run
```

Pour des champs distants renseignés, démarrer aussi `telemetrie-service` (9090) et `dossier-cnam-service` (8081).
Sans eux, le dashboard fonctionne : les champs distants valent `null`, avec l'erreur `SERVICE_INDISPONIBLE`.

- Profil par défaut : H2 en mémoire. Console : http://localhost:8083/h2-console (`jdbc:h2:mem:dashboard`, utilisateur `sa`).
- Profil `docker` : PostgreSQL (`DB_URL`, `DB_USER`, `DB_PASSWORD`). Adresses des autres services : `TELEMETRIE_ADRESSE`
  (défaut `telemetrie-service:9090`) et `CNAM_URL` (défaut `http://dossier-cnam-service:8081/ws`).

```powershell
docker build -t sahticonnect/dashboard-medical-service .
```

## Tester

```powershell
# GraphQlTester (clients gRPC et SOAP simulés) : queries, filtres, pagination, erreurs, réponses partielles,
# appels distants seulement si demandés, N+1, mutations, idempotence, niveau de risque, subscriptions (StepVerifier)
.\mvnw.cmd verify

# Service démarré : queries, mutations, erreurs et une subscription en WebSocket
..\docs\tests\graphql\tester-graphql.ps1
```
