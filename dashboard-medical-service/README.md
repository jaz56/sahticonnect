# dashboard-medical-service (GraphQL, port 8083)

Service GraphQL du médecin : une vue consolidée de chaque patient en une seule requête, des mutations pour
l'orchestrateur et des subscriptions pour suivre les alertes et interventions en direct.

## Rôle

- **Lecture** : le médecin obtient en une requête :
  - les données locales (patients, alertes, interventions, traitements) ;
  - les dernières constantes, demandées en gRPC à `telemetrie-service` ;
  - les droits CNAM et l'historique de soins, demandés en SOAP à `dossier-cnam-service`.
- **Écriture** : l'orchestrateur enregistre les alertes et suit les interventions au fil du workflow. Le médecin
  résout les alertes traitées.
- **Temps réel** : les écrans s'abonnent à `nouvelleAlerte` et `nouvelleIntervention`.

### Sources des données

| Champ | Source | Résolution |
|---|---|---|
| patients, alertes, interventions, traitements | base locale (H2 / PostgreSQL) | chargement groupé (pas de N+1) |
| `PatientSuivi.constantes` | gRPC `DernieresMesures` ; le `niveau` vient de `Mesure.niveau`, calculé par telemetrie | `@SchemaMapping`, deadline 3 s |
| `PatientSuivi.couvert`, `tauxRemboursement` | SOAP `consulterHistorique` (vignette) puis `verifierDroitsCNAM` avec un acte « C 1 » | `@SchemaMapping`, timeouts 3 s |
| `Query.historiqueSoins` | SOAP `consulterHistorique`, paginé par le dashboard | timeouts 3 s |

- Les champs distants ne sont appelés **que si le client les demande** : `{ patients { nom } }` ne contacte aucun
  autre service.
- Ils sont résolus en parallèle, un thread virtuel par appel.
- Dans une même requête, les appels SOAP sont partagés : `couvert`, `tauxRemboursement` et `historiqueSoins`
  demandés ensemble ne font qu'un appel de chaque opération.

### Réponses partielles

Si un service ne répond pas (panne, délai de 3 s dépassé), le champ vaut `null`, une erreur est ajoutée et le reste
de la réponse est renvoyé normalement :

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
- `NOT_FOUND` : patient, alerte ou intervention inconnu dans une mutation ou `historiqueSoins`
  (`patient(id)` renvoie simplement `null`) ;
- `BAD_REQUEST` : données invalides.

### Mutations et subscriptions

| Opération | Comportement |
|---|---|
| `enregistrerAlerte` | **Idempotente** : le même identifiant `ALR-…` envoyé deux fois renvoie l'alerte existante, sans doublon ni nouvel événement (l'orchestrateur peut réessayer). Le niveau de risque est recalculé. |
| `resoudreAlerte(id, commentaire)` | Statut `RESOLUE` (commentaire et date enregistrés), puis niveau de risque recalculé ; `NOT_FOUND` si l'id est inconnu. Une alerte déjà résolue est renvoyée telle quelle. |
| `creerIntervention` | Identifiant `INT-AAAA-NNNNNN`. Valeurs par défaut `ALERTE_RECUE` et `EN_ATTENTE`. L'alerte liée passe en `PRISE_EN_CHARGE`, et l'intervention est publiée sur `nouvelleIntervention`. |
| `mettreAJourIntervention` | Seuls les champs fournis changent ; `dateMiseAJour` est mise à jour. |
| `nouvelleAlerte(patientId, niveauMinimum)` | Flux filtré ; `niveauMinimum` par défaut `ELEVEE`. |
| `nouvelleIntervention(patientId, service)` | Flux filtré par patient et par service hospitalier. |

**Niveau de risque**, stocké et recalculé à chaque alerte enregistrée, résolue ou prise en charge, et toutes les 5 min :
- `CRITIQUE` : une alerte CRITIQUE non résolue dans les 24 h ;
- `ELEVE` : une alerte ELEVEE non résolue dans les 24 h ;
- `MODERE` : au moins une alerte dans les 7 jours ;
- `FAIBLE` sinon.

### Données de démonstration

| Patient | Service | Médecin référent | Démo |
|---|---|---|---|
| PAT-0001 Ahmed Ben Salah | Cardiologie | Dr Mohamed Chaabane | Alerte CRITIQUE (FC 142) prise en charge, ambulance facturée → CRITIQUE |
| PAT-0002 Leila Trabelsi | Cardiologie | Dr Ines Haddad | Alerte ÉLEVÉE (systolique 172), RDV d'urgence **à régulariser** (vignette expirée) → ELEVE |
| PAT-0003 Karim Jaziri | Pneumologie | Dr Sami Kallel | Alerte résolue il y a 3 jours → MODERE |
| PAT-0004 Sonia Mansour | Médecine interne | Dr Ines Haddad | Aucune alerte → FAIBLE |
| PAT-0005 Hedi Gharbi | Médecine interne | Dr Ines Haddad | Chute il y a 30 min, non prise en charge → CRITIQUE |

Les traitements correspondent aux ordonnances de `portail-patient-service`. Les alertes de démonstration ont des
identifiants `ALR-AAAA-9NNNNN`, distincts de ceux que produit `telemetrie-service`.

## Contrat

- Contrat : [`docs/graphql/dashboard-medical.graphqls`](../docs/graphql/dashboard-medical.graphqls) (schema-first),
  copié à l'identique dans `src/main/resources/graphql/`. Chaque champ indique sa source en commentaire, et les champs
  distants sont nullables.
- Les contrats des services appelés sont copiés de la même façon et génèrent le code au build :
  - `src/main/proto/telemetrie.proto` → stubs gRPC ;
  - `src/main/xsd/dossier-cnam.xsd` → classes JAXB.
- Points d'accès :
  - GraphQL : `POST http://localhost:8083/graphql` ;
  - subscriptions : `ws://localhost:8083/graphql`, protocole `graphql-transport-ws` ;
  - GraphiQL : http://localhost:8083/graphiql ;
  - schéma : http://localhost:8083/graphql/schema.

## Lancer

```powershell
cd dashboard-medical-service
.\mvnw.cmd spring-boot:run
```

Pour des champs distants renseignés, démarrer aussi `telemetrie-service` (9090) et `dossier-cnam-service` (8081).
Sans eux, le dashboard fonctionne, avec des réponses partielles.

- **Profil par défaut** : H2 en mémoire. Console : http://localhost:8083/h2-console (`jdbc:h2:mem:dashboard`, utilisateur `sa`).
- **Profil `docker`** : PostgreSQL (`DB_URL`, `DB_USER`, `DB_PASSWORD`). Adresses des autres services :
  `TELEMETRIE_ADRESSE` (défaut `telemetrie-service:9090`) et `CNAM_URL` (défaut `http://dossier-cnam-service:8081/ws`).
- **Propriétés** :
  - `sahti.timeout` (3 s) : délai de chaque appel distant ;
  - `sahti.risque.recalcul.actif` (`true`) et `sahti.risque.recalcul.intervalle-ms` (300000) : recalcul périodique.

```powershell
docker build -t sahticonnect/dashboard-medical-service .
```

## Tester

```powershell
# GraphQlTester (clients gRPC et SOAP simulés) :
#  - queries, filtres, pagination, erreurs ;
#  - réponses partielles, appels distants seulement si demandés, N+1 ;
#  - mutations, idempotence, resoudreAlerte, niveau de risque, subscriptions (StepVerifier) ;
#  - client gRPC contre un faux serveur in-process, recalcul périodique avec horloge fixe
.\mvnw.cmd verify

# Service démarré : queries, mutations, erreurs et une subscription en WebSocket
..\docs\tests\graphql\tester-graphql.ps1
```

Requêtes prêtes pour GraphiQL : [`docs/tests/graphql/requetes.graphql`](../docs/tests/graphql/requetes.graphql).

## Choix techniques

- **Chargement groupé (problème N+1)** :
  - `@BatchMapping` pour `PatientSuivi.interventions`, `Intervention.patient`, `Intervention.alerte` et `Alerte.intervention` ;
  - `alertes(statut)` et `traitements(actifs)` ont un argument, que `@BatchMapping` ne transmet pas : ils utilisent le
    même mécanisme (un DataLoader enregistré dans `ChargeursGroupes`) depuis un `@SchemaMapping` qui filtre ensuite ;
  - un test vérifie, par les statistiques Hibernate, 8 requêtes SQL pour 1 patient comme pour 5.
- **Champs distants nullables et `GestionErreurs`** : la panne d'un service ne fait pas tomber le dashboard.
- **Une seule source des seuils** : `ConstanteVitale.niveau` vient de `Mesure.niveau` (telemetrie) ; le dashboard
  n'applique aucun seuil.
- **Idempotence de `enregistrerAlerte`** : l'identifiant gRPC `ALR-…` est la clé primaire. Si deux envois
  simultanés se croisent, le second relit l'alerte créée par le premier.
- **Recalcul périodique du risque** (`@Scheduled`) : le temps qui passe n'est pas un événement, et sans cette tâche
  un patient resterait CRITIQUE après les 24 h de son alerte. Elle est désactivée pendant les tests.
- **Subscriptions** : Reactor `Sinks.many().multicast()`, avec publication après validation de la transaction.
- **Client gRPC Spring gRPC 0.12** (canal nommé `telemetrie`, client seul) et **client SOAP `WebServiceTemplate`**.
