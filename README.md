# SahtiConnect – Plateforme Smart Healthcare

Projet individuel de *Service Oriented Computing* (3ING GINF, ENICarthage).

SahtiConnect suit à distance des patients chroniques. Des capteurs envoient leurs constantes en continu.
Au franchissement d'un seuil, une alerte déclenche une intervention : ambulance pour une alerte CRITIQUE,
rendez-vous d'urgence dans les 2 h pour une alerte ÉLEVÉE. Les droits CNAM du patient sont vérifiés et la
feuille de soins est émise. Le médecin suit tout depuis un tableau de bord.

Chaque service utilise un **protocole différent**, chacun choisi pour son usage :
- **SOAP** pour l'échange formel avec la CNAM ;
- **REST** pour les ressources du portail patient ;
- **GraphQL** pour la vue consolidée du médecin ;
- **gRPC** pour le streaming des capteurs.

Chaque service a sa propre base de données (*database-per-service*).

## Architecture

```
 Présentation              web-client (Angular) :4200   un écran par rôle : patient, médecin, admin
                                     │ HTTP + WebSocket + SSE (un seul hôte : http://localhost:8080)
 Point d'entrée              api-gateway :8080   CORS, X-Correlation-Id, timeouts, circuit breakers
              ┌──────────────┬───────┴──────┬─────────────────────┐
              ▼ /api/portail ▼ /api/graphql ▼ /api/orchestrateur  ▼ /api/cnam/ws
              REST           GraphQL + WS   REST + SSE            SOAP + WSDL   (gRPC : non exposé)
 Services       portail-patient            dashboard-medical ──── gRPC DernieresMesures ───► telemetrie
                REST :8082                 GraphQL :8083     ──── SOAP historique, droits ─► dossier-cnam
                RDV, ordonnances,          vue consolidée
                ambulances                 du médecin
                          ▲                      ▲
                          │ REST                 │ GraphQL
                          │ ambulance / RDV      │ enregistrerAlerte, creerIntervention…
 Workflow                 └───── orchestrateur :8085 ─────── SOAP droits, feuille de soins ──► dossier-cnam
                                       ▲                                                   SOAP :8081
                                       │ gRPC SouscrireAlertes (server streaming)
 Télémétrie                 telemetrie-service  gRPC :9090
                            seuils, anti-rebond, alertes
                                       ▲
                                       │ gRPC EnvoyerMesures (bidirectionnel)
 Capteurs                   capteur-simulator (Python)
```

| Appelant | Appelé | Protocole | Pour quoi |
|---|---|---|---|
| capteur-simulator | telemetrie-service | gRPC `EnvoyerMesures` (bidirectionnel) | Envoyer les mesures, recevoir un accusé par mesure |
| orchestrateur | telemetrie-service | gRPC `SouscrireAlertes` (server streaming) | Recevoir les alertes en direct |
| orchestrateur | dossier-cnam-service | SOAP `verifierDroitsCNAM`, `emettreFeuilleDeSoins` | Droits, puis facturation |
| orchestrateur | portail-patient-service | REST `POST /urgences/ambulances`, `POST /rendez-vous` | CRITIQUE → ambulance, ÉLEVÉE → RDV d'urgence |
| orchestrateur | dashboard-medical-service | GraphQL `enregistrerAlerte`, `creerIntervention`, `mettreAJourIntervention` | Fiche de suivi du médecin |
| dashboard-medical-service | telemetrie-service | gRPC `DernieresMesures` (unaire) | Constantes affichées |
| dashboard-medical-service | dossier-cnam-service | SOAP `consulterHistorique`, `verifierDroitsCNAM` | Historique et couverture |
| client web (navigateur) | api-gateway | HTTP, WebSocket, SSE | Point d'entrée unique, relayé vers portail, dashboard, orchestrateur, CNAM |

Le client web ne parle **qu'à la gateway**. gRPC n'est pas exposé au navigateur, qui ne sait pas le parler :
les alertes lui arrivent par la subscription GraphQL `nouvelleAlerte` et l'avancement des workflows par le flux SSE.
Détails et tableau des routes : [README de la gateway](api-gateway/README.md).

Workflow d'alerte, piloté par l'orchestrateur (une file par patient, chaque étape tracée et relançable) :
`ALERTE_RECUE → DROITS_VERIFIES → INTERVENTION_DECLENCHEE → FICHE_PUBLIEE → FACTUREE` (ou `ECHEC`).
Le diagramme de séquence est dans le [README de l'orchestrateur](orchestrateur-service/README.md).

## Services

| Service | Protocole | Port | Statut | Documentation | Contrat |
|---|---|---|---|---|---|
| dossier-cnam-service | SOAP (Spring WS, contract-first) | 8081 | ✅ | [README](dossier-cnam-service/README.md) | [XSD](docs/wsdl/dossier-cnam.xsd), [WSDL](docs/wsdl/dossier-cnam.wsdl) |
| portail-patient-service | REST (Spring Web, openapi-generator) | 8082 | ✅ | [README](portail-patient-service/README.md) | [OpenAPI](docs/openapi/portail-patient.yaml) |
| dashboard-medical-service | GraphQL (Spring for GraphQL, schema-first) | 8083 | ✅ | [README](dashboard-medical-service/README.md) | [SDL](docs/graphql/dashboard-medical.graphqls) |
| telemetrie-service | gRPC (Spring gRPC) | 9090 | ✅ | [README](telemetrie-service/README.md) | [proto](docs/proto/telemetrie.proto) |
| capteur-simulator | Python grpcio | – | ✅ | [README](capteur-simulator/README.md) | [proto](docs/proto/telemetrie.proto) |
| orchestrateur-service | REST + SSE, clients des 4 protocoles | 8085 | ✅ | [README](orchestrateur-service/README.md) | [OpenAPI](docs/openapi/orchestrateur.yaml) |
| api-gateway | Spring Cloud Gateway (WebFlux) | 8080 | ✅ | [README](api-gateway/README.md) | expose les contrats des services sous `/api/…` |
| web-client | Angular 22 + Material + Apollo | 4200 | ✅ | [README](web-client/README.md) | clients générés depuis `docs/openapi` et `docs/graphql` |

Les contrats de `docs/` sont la **source de vérité**. Chaque service en garde une copie identique, à partir de
laquelle le code (JAXB, interfaces Spring, stubs gRPC, schéma GraphQL) est généré au build.

## Prérequis

| Outil | Version | Rôle |
|---|---|---|
| JDK | 25 (le code est compilé en `release 21`) ; `JAVA_HOME` doit pointer dessus | services Java |
| Maven | inutile à installer : chaque service fournit le Maven Wrapper (`mvnw.cmd`) | build |
| Python | 3.12 ou plus récent (testé en 3.14) | capteur simulé |
| Node.js | 22.22 ou plus récent (Angular 22) | client web (`npm start`) ; inutile avec Docker |
| Docker Desktop | récent (Compose v2) | stack complète : `docker compose up --build -d` |

Les commandes ci-dessous sont en **PowerShell**, depuis la racine du dépôt. Pour des accents corrects dans la
console Windows PowerShell 5.1 (logs, scripts) : `[Console]::OutputEncoding = [Text.Encoding]::UTF8`.

## Lancer en local

Chaque service démarre avec une base **H2 en mémoire** et des données de test (les 5 patients `PAT-0001` … `PAT-0005`).
Un terminal par service :

```powershell
# 1. dossier-cnam-service (SOAP, 8081)
cd dossier-cnam-service; .\mvnw.cmd spring-boot:run

# 2. portail-patient-service (REST, 8082)
cd portail-patient-service; .\mvnw.cmd spring-boot:run

# 3. telemetrie-service (gRPC, 9090)
cd telemetrie-service; .\mvnw.cmd spring-boot:run

# 4. dashboard-medical-service (GraphQL, 8083) : appelle telemetrie et dossier-cnam
cd dashboard-medical-service; .\mvnw.cmd spring-boot:run

# 5. orchestrateur-service (REST, 8085) : s'abonne aux alertes de telemetrie, appelle les 3 autres
cd orchestrateur-service; .\mvnw.cmd spring-boot:run

# 6. api-gateway (8080) : point d'entrée unique, relaie vers les services ci-dessus
cd api-gateway; .\mvnw.cmd spring-boot:run

# 7. web-client (Angular, 4200) : génère les clients depuis les contrats, puis http://localhost:4200
cd web-client; npm install; npm start

# 8. capteur simulé (Python) : installation unique, puis lancement
cd capteur-simulator
python -m venv .venv; .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt; .\generer_stubs.ps1
python capteur.py --patient PAT-0001 --scenario tachycardie
```

**Ordre de démarrage conseillé** : `dossier-cnam` et `portail-patient` (aucune dépendance), puis `telemetrie`,
puis `dashboard`, qui interroge `telemetrie` et `dossier-cnam`, puis `orchestrateur`, puis le capteur.
L'orchestrateur démarre aussi seul : il retente l'abonnement gRPC (1 s → 30 s) jusqu'à ce que telemetrie réponde.

Le dashboard démarre aussi sans les autres services. Les champs distants valent alors `null` avec une erreur
`SERVICE_INDISPONIBLE` : c'est une réponse partielle.

| Interface | URL |
|---|---|
| **Client web** (patient, médecin, admin) | http://localhost:4200 |
| **Via la gateway** : Swagger UI regroupé (portail, orchestrateur) | http://localhost:8080/swagger-ui.html |
| **Via la gateway** : GraphQL, WSDL, SSE | http://localhost:8080/api/graphql, /api/cnam/ws/dossier-cnam.wsdl, /api/orchestrateur/workflows/flux |
| Routes de la gateway | http://localhost:8080/actuator/gateway/routes |
| WSDL du service SOAP | http://localhost:8081/ws/dossier-cnam.wsdl |
| Swagger UI (contrat REST) | http://localhost:8082/swagger-ui.html |
| GraphiQL | http://localhost:8083/graphiql |
| Swagger UI (orchestrateur) | http://localhost:8085/swagger-ui.html |
| Flux SSE des workflows | http://localhost:8085/workflows/flux |
| gRPC (réflexion, health check) | `localhost:9090` |
| Consoles H2 | http://localhost:8081/h2-console, :8082/h2-console, :8083/h2-console, :8085/h2-console |

## Lancer avec Docker

[`docker-compose.yml`](docker-compose.yml) démarre le client web, l'API Gateway, les 5 services et PostgreSQL avec
**une seule commande**, sur les mêmes ports qu'en local. **En production, seul le port 8080 (gateway) serait publié** : les ports
des services ne le sont ici que pour le développement et les tests en accès direct.

| Conteneur | Image | Port | Healthcheck |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` : une base et un utilisateur par service | – (réseau interne) | `pg_isready` |
| `dossier-cnam` | `dossier-cnam-service` | 8081 | `GET /actuator/health` |
| `portail-patient` | `portail-patient-service` | 8082 | `GET /actuator/health` |
| `telemetrie` | `telemetrie-service` | 9090 | `grpc_health_probe` (gRPC `Health/Check`) |
| `dashboard-medical` | `dashboard-medical-service` | 8083 | `GET /actuator/health` |
| `orchestrateur` | `orchestrateur-service` | 8085 | `GET /actuator/health` |
| `api-gateway` | `api-gateway` | 8080 | `GET /actuator/health` |
| `web-client` | `web-client` (nginx) | 4200 | `wget http://127.0.0.1/` |
| `capteur` | `capteur-simulator` (profil `capteur`) | – | – |

- **Ordre de démarrage** imposé par `depends_on: condition: service_healthy` :
  `postgres` → `dossier-cnam`, `portail-patient`, `telemetrie` → `dashboard-medical` → `orchestrateur`, qui attend
  que les 4 autres soient prêts → `api-gateway`, qui attend les 5 services → `web-client`.
- **Healthchecks** : un service n'est « healthy » qu'une fois démarré ET ses données de test chargées. Les sondes
  d'actuator incluent `readinessState`. `telemetrie` est un service gRPC pur, sans serveur HTTP : plutôt que
  d'ajouter Tomcat et actuator juste pour la santé, l'image embarque `grpc_health_probe`, qui interroge le service
  standard `grpc.health.v1.Health` sur le port 9090, celui qu'utilisent vraiment les clients.
- **Mémoire** : `mem_limit: 512m` par service Java, et `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (tas max 384 Mo).
- **Données** : volume nommé `postgres-data`. Les données de test ne sont chargées que si la base est vide, et
  `schema.sql` est idempotent (`CREATE SEQUENCE IF NOT EXISTS`). Un redémarrage conserve donc tout, sans doublon.

```powershell
# Une fois : mots de passe des bases (le fichier .env est ignoré par git)
Copy-Item .env.example .env        # puis remplacer les "changer-moi"

# Construire et démarrer (premier build : quelques minutes, Maven télécharge les dépendances)
docker compose up --build -d
docker compose ps                  # STATUS doit passer à "healthy" pour les 8 conteneurs (1 à 2 min)
                                   # puis http://localhost:4200

# Logs
docker compose logs -f orchestrateur
docker compose logs --tail 50 dossier-cnam portail-patient

# Capteur simulé (conteneur éphémère, joint telemetrie par le réseau Docker)
docker compose --profile capteur run --rm capteur --patient PAT-0001 --scenario tachycardie --hote telemetrie:9090

# Démo de bout en bout contre la stack Docker (capteur lancé en conteneur), en direct ou via la gateway
.\docs\tests\e2e\demo.ps1 -Docker
.\docs\tests\e2e\demo.ps1 -Docker -ViaGateway

# Arrêter (les données restent dans le volume) / réinitialiser (efface les bases : données de test rechargées)
docker compose down
docker compose down -v

# Console SQL d'une base
docker compose exec postgres psql -U postgres -d orchestrateur
```

- **Un service tourne déjà en local** sur le même port (`mvnw spring-boot:run`) : changer le port publié dans
  `.env` (`CNAM_PORT`, `PORTAIL_PORT`, `DASHBOARD_PORT`, `ORCHESTRATEUR_PORT`, `TELEMETRIE_PORT`), puis passer les
  URL aux scripts, par exemple `.\docs\tests\soap\tester-soap.ps1 -Url http://localhost:18081/ws`.
- **Accents dans les logs** : la console Windows PowerShell 5.1 n'est pas en UTF-8 par défaut. Avant
  `docker compose logs` (ou les scripts de test), exécuter :

  ```powershell
  [Console]::OutputEncoding = [Text.Encoding]::UTF8
  ```

**Mesuré** (`docker stats`, stack au repos, après tous les scripts de test et la démo via la gateway) :

| Conteneur | Mémoire / limite |
|---|---|
| dossier-cnam | 265 Mo / 512 Mo |
| portail-patient | 276 Mo / 512 Mo |
| telemetrie | 288 Mo / 512 Mo |
| dashboard-medical | 388 Mo / 512 Mo |
| orchestrateur | 407 Mo / 512 Mo |
| api-gateway | 308 Mo / 512 Mo |
| web-client (nginx) | 13 Mo / 64 Mo |
| postgres (5 bases) | 132 Mo |
| **Total** | **≈ 2,1 Go** |

Démarrage complet à froid (images déjà construites) : environ 75 s. Redémarrage avec des données existantes : environ 45 s.

Après un redémarrage de la machine, si des services redémarrent en boucle (« health: starting »), c'est que
`postgres` est resté arrêté : `docker compose up -d` le relance, et les services repartent.

## Scénario de démo : capteur → alerte → workflow → dashboard

**Avec le client web** (http://localhost:4200, stack Docker démarrée) :
1. Médecin → « Alertes en direct » (subscription GraphQL ouverte).
2. Dans un autre onglet, Accueil → « Simuler une alerte » (PAT-0001, CRITIQUE) : l'alerte puis l'intervention
   apparaissent en direct côté médecin.
3. Admin → « Workflows » : la frise des 5 étapes avance en direct (SSE) jusqu'à FACTUREE.
4. Admin → « CNAM (SOAP) » : la feuille de soins, et l'onglet XML pour montrer l'enveloppe.
5. `docker compose stop dossier-cnam` : la fiche patient reste affichée, la couverture est remplacée par
   « Service indisponible » (réponse partielle) ; `docker compose start dossier-cnam` pour revenir.

Le même parcours est automatisé par Playwright : `cd web-client; npm run e2e`. Captures d'écran :
[`docs/captures/`](docs/captures/).

**En ligne de commande** (les 5 services démarrés) : `.\docs\tests\e2e\demo.ps1`. Le script vérifie les services,
lance le capteur en tachycardie sur PAT-0001, attend la fin des workflows, puis affiche leurs étapes, la fiche
GraphQL du patient et les feuilles de soins de l'historique CNAM.

Pas à pas :

1. Démarrer les 5 services (voir ci-dessus).
2. **Terminal A**, alertes en direct :
   `cd capteur-simulator; python abonne_alertes.py`
3. **Terminal B**, capteur en tachycardie :
   `python capteur.py --patient PAT-0001 --scenario tachycardie --duree 60`.
   La FC monte progressivement :
   - une alerte ÉLEVÉE (`FC ≥ 110`) ;
   - l'anti-rebond filtre les répétitions ;
   - puis l'aggravation en CRITIQUE (`FC > 130`) est émise immédiatement.

   L'abonné affiche les deux alertes. L'orchestrateur lance un workflow pour chacune : RDV d'urgence pour
   l'ÉLEVÉE, ambulance pour la CRITIQUE (`curl.exe -N http://localhost:8085/workflows/flux` pour les suivre).
4. **GraphiQL** (http://localhost:8083/graphiql), opération `FichePatient` de
   [`docs/tests/graphql/requetes.graphql`](docs/tests/graphql/requetes.graphql). En une requête :
   - les constantes du capteur, avec le niveau CRITIQUE calculé par telemetrie ;
   - la couverture CNAM (APCI, 100 %, via SOAP) ;
   - les alertes, interventions et traitements.
5. **Workflows** : `GET http://localhost:8085/workflows?patientId=PAT-0001` montre les 5 étapes de chacun
   (statut, durée, message), jusqu'à `FACTUREE`. `.\docs\tests\orchestrateur\tester-orchestrateur.ps1`
   déclenche un workflow à la main (idempotence, relance, erreurs). Dans GraphiQL, la mutation `ResoudreAlerte`
   fait redescendre le niveau de risque du patient.
6. **Ambulance** : `..\docs\tests\rest\tester-rest.ps1` crée une mission. Son statut avance tout seul
   (DEPECHEE → SUR_PLACE → TRANSPORT → TERMINEE, toutes les 30 s).

## Tester

Chaque service se teste avec `.\mvnw.cmd verify` : tests unitaires et d'intégration. Avec les services démarrés,
il y a en plus les scripts ci-dessous. Chacun accepte **`-ViaGateway`** : toutes ses URL passent alors par
http://localhost:8080 (par exemple `.\docs\tests\rest\tester-rest.ps1 -ViaGateway`).

| Service | Script / ressource | Contenu |
|---|---|---|
| SOAP | [`docs/tests/soap/tester-soap.ps1`](docs/tests/soap/tester-soap.ps1) + `*.xml` | Les 4 opérations et une SOAP Fault |
| REST | [`docs/tests/rest/tester-rest.ps1`](docs/tests/rest/tester-rest.ps1) | Profil → créneaux → RDV → modification → annulation → urgence → ambulance → erreurs |
| REST | [`docs/tests/rest/portail-patient.postman_collection.json`](docs/tests/rest/portail-patient.postman_collection.json) | Même scénario dans Postman (Collection Runner) |
| gRPC | [`docs/tests/grpc/README.md`](docs/tests/grpc/README.md) | Commandes grpcurl prêtes à copier, import dans Postman |
| gRPC | [`capteur-simulator/`](capteur-simulator/README.md) | Capteur (streaming bidirectionnel) et abonné aux alertes |
| GraphQL | [`docs/tests/graphql/requetes.graphql`](docs/tests/graphql/requetes.graphql) | Requêtes prêtes pour GraphiQL (queries, mutations, subscriptions) |
| GraphQL | [`docs/tests/graphql/tester-graphql.ps1`](docs/tests/graphql/tester-graphql.ps1) | Queries, mutations, erreurs et une subscription en WebSocket |
| Orchestrateur | [`docs/tests/orchestrateur/tester-orchestrateur.ps1`](docs/tests/orchestrateur/tester-orchestrateur.ps1) | Déclenchement manuel, suivi des étapes, idempotence, relance, erreurs |
| Bout en bout | [`docs/tests/e2e/demo.ps1`](docs/tests/e2e/demo.ps1) | Capteur → telemetrie → orchestrateur → CNAM, portail, dashboard |
| Client web | `cd web-client; npm test` | 24 tests unitaires (Vitest) : SOAP, intercepteur, SSE, pipe DT, fiche partielle, frise |
| Client web | `cd web-client; npm run e2e` | Playwright contre la stack Docker : RDV patient ; alerte simulée → en direct chez le médecin → FACTUREE chez l'admin |

## Structure du dépôt

```
sahticonnect/
├── CLAUDE.md                     ← cadrage, règles métier, avancement
├── docker-compose.yml            ← stack complète (client, gateway, 5 services, PostgreSQL, capteur)
├── .env.example                  ← modèle du .env (mots de passe des bases, ports publiés)
├── docker/postgres/init.sql      ← une base + un utilisateur par service
├── docs/
│   ├── wsdl/                     ← dossier-cnam.xsd (contrat) + dossier-cnam.wsdl (exporté)
│   ├── openapi/                  ← portail-patient.yaml, orchestrateur.yaml
│   ├── graphql/                  ← dashboard-medical.graphqls
│   ├── proto/                    ← telemetrie.proto
│   ├── tests/                    ← soap/, rest/, grpc/, graphql/, orchestrateur/, e2e/ : scripts de test
│   └── captures/                 ← captures d'écran du client web (Playwright)
├── dossier-cnam-service/         ← SOAP   :8081
├── portail-patient-service/      ← REST   :8082
├── dashboard-medical-service/    ← GraphQL :8083
├── telemetrie-service/           ← gRPC   :9090
├── orchestrateur-service/        ← REST + clients des 4 protocoles :8085
├── api-gateway/                  ← Spring Cloud Gateway :8080 (point d'entrée unique)
├── web-client/                   ← Angular :4200 (client unique, n'appelle que la gateway)
└── capteur-simulator/            ← Python (client gRPC)
```

Chaque service Java suit la même organisation en couches :
- un point d'entrée par protocole : `endpoint/`, `web/`, `graphql/` ou `grpc/` ;
- puis `service/`, `repository/` et `entity/` ;
- avec un `Dockerfile` multi-stage (`eclipse-temurin:21`) et un profil `docker` pour PostgreSQL.
