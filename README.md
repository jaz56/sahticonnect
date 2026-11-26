# SahtiConnect – Plateforme Smart Healthcare

Projet individuel de *Service Oriented Computing* (3ING GINF, ENICarthage).

## Démarrage rapide

Prérequis : **Docker Desktop** (Compose v2) et PowerShell. Rien d'autre à installer : Java, Node et Python tournent
dans les conteneurs.

```powershell
git clone https://github.com/jaz56/sahticonnect.git; cd sahticonnect
Copy-Item .env.example .env                       # mots de passe de démo (fichier ignoré par git)
docker compose up --build -d --wait               # 1er build : 5 à 10 min ; rend la main quand les 9 conteneurs sont healthy
powershell -ExecutionPolicy Bypass -File .\docs\tests\e2e\demo.ps1 -Docker -ViaGateway   # démo de bout en bout
```

Ensuite : **http://localhost:4200**, mot de passe `demo` pour tous les comptes. Trois comptes suffisent pour voir les
trois rôles : `ahmed` (patient), `dr.chaabane` (médecin), `admin` (tout, dont les workflows et la CNAM).
Le détail des comptes est dans [Utilisateurs de démo](#sécurité-bonus--keycloak-oidc-et-jwt).

## Fonctionnalités

| | Fonctionnalité | Où |
|---|---|---|
| **Énoncé** | Service SOAP contract-first : droits CNAM, feuilles de soins, historique, comparaison de garanties, SOAP Faults | [dossier-cnam-service](dossier-cnam-service/README.md) |
| | Service REST : profil, créneaux, CRUD des RDV, ordonnances, ambulances (OpenAPI, Swagger UI) | [portail-patient-service](portail-patient-service/README.md) |
| | Service GraphQL schema-first : vue consolidée du médecin, filtres, interventions, subscriptions WebSocket, DataLoaders | [dashboard-medical-service](dashboard-medical-service/README.md) |
| | Service gRPC : streaming bidirectionnel des mesures, détection des seuils, anti-rebond, flux d'alertes | [telemetrie-service](telemetrie-service/README.md) |
| | Capteur IoT simulé (Python grpcio, 5 scénarios : normal, tachycardie, désaturation, hypertension, chute) | [capteur-simulator](capteur-simulator/README.md) |
| | Orchestrateur : workflow d'alerte sur les 4 protocoles, une file par patient, idempotence, relance, Resilience4j | [orchestrateur-service](orchestrateur-service/README.md) |
| | API Gateway : point d'entrée unique, CORS, timeouts, circuit breakers, fallbacks au format du contrat | [api-gateway](api-gateway/README.md) |
| | Client web Angular : un écran par rôle, clients générés depuis les contrats, alertes en direct, SSE | [web-client](web-client/README.md) |
| | Database-per-service (PostgreSQL), Docker Compose, healthchecks, ordre de démarrage | [Lancer avec Docker](#lancer-avec-docker) |
| **Bonus 1** | Saga d'escalade avec compensation (ÉLEVÉE puis CRITIQUE → RDV annulé, feuille annulée, fiche compensée) | [orchestrateur-service](orchestrateur-service/README.md) |
| **Bonus 2** | Sécurité Keycloak : OIDC Authorization Code + PKCE, JWT vérifié par la gateway, rôles, contrôle d'appartenance | [Sécurité](#sécurité-bonus--keycloak-oidc-et-jwt) |
| **Bonus 3** | Identifiant de corrélation dans tous les services (HTTP et gRPC), propagé sur chaque appel, même format de journal | [Suivre une requête](#suivre-une-requête-dans-tous-les-services-bonus) |
| **Tests** | `mvnw verify` par service, Vitest, Playwright (12 tests contre la stack), scripts PowerShell par protocole | [Tester](#tester) |

## Contrats et captures

Les contrats de [`docs/`](docs/) sont la **source de vérité** : le code de chaque service et du client web en est généré.

| Protocole | Contrat |
|---|---|
| SOAP | [`docs/wsdl/dossier-cnam.xsd`](docs/wsdl/dossier-cnam.xsd) (contrat), [`docs/wsdl/dossier-cnam.wsdl`](docs/wsdl/dossier-cnam.wsdl) (exporté) |
| REST | [`docs/openapi/portail-patient.yaml`](docs/openapi/portail-patient.yaml), [`docs/openapi/orchestrateur.yaml`](docs/openapi/orchestrateur.yaml) |
| GraphQL | [`docs/graphql/dashboard-medical.graphqls`](docs/graphql/dashboard-medical.graphqls) |
| gRPC | [`docs/proto/telemetrie.proto`](docs/proto/telemetrie.proto) |

Captures du client web (régénérées par Playwright : `cd web-client; npx playwright test captures`) :

| | | |
|---|---|---|
| [Accueil](docs/captures/01-accueil.png) | [Prise de RDV](docs/captures/02-prise-rdv.png) | [Tableau de bord](docs/captures/03-tableau-de-bord.png) |
| [Fiche patient](docs/captures/04-fiche-patient.png) | [Alertes en direct](docs/captures/05-alertes-direct.png) | [Workflows](docs/captures/06-workflows.png) |
| [CNAM (SOAP)](docs/captures/07-cnam-soap.png) | [SOAP Fault (XML)](docs/captures/08-cnam-fault-xml.png) | [Réponse partielle](docs/captures/09-reponse-partielle.png) |
| [Saga : compensation](docs/captures/10-saga-compensation.png) | [Connexion Keycloak](docs/captures/11-connexion-keycloak.png) | [Accès refusé](docs/captures/12-acces-refuse.png) |

## Présentation

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
 Point d'entrée              api-gateway :8080   JWT (Keycloak :8180), CORS, X-Correlation-Id, timeouts, circuit breakers
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

**Saga d'escalade (bonus)** : une alerte CRITIQUE qui suit, dans les 30 min, une alerte ÉLEVÉE du même patient
dont le RDV d'urgence est encore à venir **compense** le workflow ÉLEVÉE avant de lancer l'ambulance, dans l'ordre
inverse de ses étapes : feuille de soins annulée (SOAP `annulerFeuilleDeSoins`), fiche COMPENSE et alerte résolue
(GraphQL), RDV annulé (REST). Le workflow ÉLEVÉE passe à `COMPENSE` ; il ne reste qu'une facture active.
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

[`docker-compose.yml`](docker-compose.yml) démarre **9 conteneurs** avec **une seule commande** : le client web,
l'API Gateway, Keycloak, les 5 services et PostgreSQL, sur les mêmes ports qu'en local (le capteur est un 10e
conteneur, éphémère, lancé à la demande). **En production, seul le port 8080 (gateway) serait publié** : les ports
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
| `keycloak` | `quay.io/keycloak/keycloak:26.8.2` (realm importé) | 8180 | `GET :9000/health/ready` |
| `capteur` | `capteur-simulator` (profil `capteur`) | – | – |

- **Ordre de démarrage** imposé par `depends_on: condition: service_healthy` :
  `postgres` → `dossier-cnam`, `portail-patient`, `telemetrie` → `dashboard-medical` → `orchestrateur`, qui attend
  que les 4 autres soient prêts → `api-gateway`, qui attend les 5 services → `web-client`.
- **Healthchecks** : un service n'est « healthy » qu'une fois démarré ET ses données de test chargées. Les sondes
  d'actuator incluent `readinessState`. `telemetrie` est un service gRPC pur, sans serveur HTTP : plutôt que
  d'ajouter Tomcat et actuator juste pour la santé, l'image embarque `grpc_health_probe`, qui interroge le service
  standard `grpc.health.v1.Health` sur le port 9090, celui qu'utilisent vraiment les clients.
- **Mémoire** : `mem_limit: 512m` par service Java (768m pour l'orchestrateur, voir les mesures plus bas), 1g pour
  Keycloak, et `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (tas max = 75 % de la limite).
- **Données** : volume nommé `postgres-data`. Les données de test ne sont chargées que si la base est vide, et
  `schema.sql` est idempotent (`CREATE SEQUENCE IF NOT EXISTS`). Un redémarrage conserve donc tout, sans doublon.

```powershell
# Une fois : mots de passe des bases (le fichier .env est ignoré par git)
Copy-Item .env.example .env        # puis remplacer les "changer-moi"

# Construire et démarrer (premier build : quelques minutes, Maven télécharge les dépendances)
docker compose up --build -d --wait   # --wait : rend la main quand les 9 conteneurs sont "healthy"
docker compose ps                     # état et healthchecks ; puis http://localhost:4200

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

**Mesuré** (`docker stats` toutes les 2 s **pendant** `demo.ps1 -Docker -ViaGateway` : pic de chaque conteneur) :

| Conteneur | Pic / limite |
|---|---|
| dossier-cnam | 296 Mo / 512 Mo (58 %) |
| portail-patient | 296 Mo / 512 Mo (58 %) |
| telemetrie | 248 Mo / 512 Mo (48 %) |
| dashboard-medical | 342 Mo / 512 Mo (67 %) |
| orchestrateur | 406 Mo / 768 Mo (53 %) |
| api-gateway | 295 Mo / 512 Mo (58 %) |
| keycloak | 665 Mo / 1 Go (65 %) |
| web-client (nginx) | 14 Mo / 64 Mo |
| postgres (5 bases) | 132 Mo |
| capteur (éphémère) | 24 Mo / 128 Mo |
| **Total** | **≈ 2,7 Go** |

L'orchestrateur est passé de 512m à 768m : il atteignait 85 % de 512m (437 Mo) après une série de workflows. C'est
le service qui porte le plus de clients (SOAP, REST, GraphQL, gRPC, Resilience4j) et un thread virtuel par workflow.

Démarrage complet à froid (images déjà construites) : environ 75 s. Redémarrage avec des données existantes : environ 45 s.

Après un redémarrage de la machine, si des services redémarrent en boucle (« health: starting »), c'est que
`postgres` est resté arrêté : `docker compose up -d` le relance, et les services repartent.

## Sécurité (bonus) : Keycloak, OIDC et JWT

**La gateway est le point d'application de la sécurité.** Keycloak (`quay.io/keycloak/keycloak:26.8.2`, port 8180)
authentifie les utilisateurs et signe les jetons ; la gateway vérifie chaque jeton et applique la matrice des droits ;
les services internes ne sont joignables que par le réseau Docker et font confiance aux en-têtes posés par la gateway.

```
 Navigateur (Angular, angular-auth-oidc-client)            Keycloak :8180 (realm sahticonnect)
   1. « Se connecter » ─ redirection /auth?code_challenge=S256(verifier) ──►  page de connexion (ahmed / demo)
   2. ◄──────────────── redirection http://localhost:4200/?code=…&state=… ─┘
   3. POST /token (code + code_verifier) ────────────────────────────────►  vérifie S256(verifier) = challenge
   4. ◄──────────────── access_token (JWT RS256, 5 min) + refresh_token ──┘  (renouvelé 30 s avant l'expiration)
   5. GET /api/portail/… Authorization: Bearer <JWT>
          ▼
 api-gateway :8080 (resource server)   clés publiques JWKS récupérées UNE fois (http://keycloak:8080/…/certs), en cache
   - signature RS256, exp, iss = http://localhost:8180/realms/sahticonnect, aud ∋ sahticonnect-gateway
   - rôles realm_access.roles → matrice ci-dessous (401 NON_AUTHENTIFIE / 403 ACCES_REFUSE, SOAP Fault sur la CNAM)
   - supprime X-Patient-Id et X-Roles venus du client ; ajoute X-Roles, et X-Patient-Id = patient_id pour un PATIENT
          ▼ réseau Docker
 portail-patient (contrôle d'appartenance : X-Patient-Id ≠ patient demandé → 403), dashboard, orchestrateur, CNAM
```

**Le piège de l'issuer** : dans le réseau Docker, la gateway joint Keycloak par `http://keycloak:8080`, le navigateur
par `http://localhost:8180`. Le claim `iss` doit être le même pour tous : `KC_HOSTNAME=http://localhost:8180` fixe
l'adresse publique, et la gateway télécharge les clés par l'adresse interne (`jwk-set-uri`) tout en exigeant l'issuer
public (`issuer-uri`, sans découverte). Vérifié : un jeton demandé par `keycloak:8080` porte bien `iss = localhost:8180`.

**Utilisateurs de démo** (mot de passe `demo` pour tous ; console Keycloak : http://localhost:8180, `admin` /
`KEYCLOAK_ADMIN_PASSWORD` du `.env`) :

| Utilisateur | Rôle | `patient_id` | Voit |
|---|---|---|---|
| `ahmed`, `leila`, `karim`, `sonia`, `hedi` | PATIENT | PAT-0001 … PAT-0005 | Son dossier seulement (profil, RDV, ordonnances, ambulance) |
| `dr.chaabane`, `dr.haddad`, `dr.kallel` | MEDECIN | – | Dossiers des patients, tableau de bord, fiches, alertes en direct |
| `admin` | ADMIN | – | Tout, plus workflows (saga) et CNAM (SOAP) |

**Matrice des droits** (appliquée par la gateway ; le client web masque en plus les menus et garde ses routes,
pour le confort seulement) :

| Route | PATIENT | MEDECIN | ADMIN | Sans jeton |
|---|---|---|---|---|
| `/api/portail/**` | ✅ ses données (403 sinon) | ✅ | ✅ | 401 |
| `/api/graphql` (HTTP et WebSocket) | 403 | ✅ | ✅ | 401 |
| `POST /api/orchestrateur/workflows/declencher` | 403 | ✅ | ✅ | 401 |
| `/api/orchestrateur/**` (workflows, SSE, relance) | 403 | 403 | ✅ | 401 |
| `/api/cnam/ws/**` (SOAP) | Fault 403 | Fault 403 | ✅ | Fault 401 |
| `GET` WSDL, Swagger UI, YAML des contrats, `/actuator/health`, fallbacks | public | public | public | public |

- **Navigateur** : Authorization Code + PKCE (client public `web-client`, sans secret). WebSocket GraphQL : le
  navigateur ne peut pas poser d'en-tête sur une WebSocket, le jeton passe donc dans l'URL de l'upgrade
  (`?access_token=`), accepté pour cette seule route, retiré avant le relais et masqué dans les logs. SSE : pas de
  jeton dans l'URL, `@microsoft/fetch-event-source` envoie l'en-tête `Authorization`.
- **Scripts de test** : le client `scripts` (Direct Access Grants, « password grant ») est **réservé aux scripts de
  test** (`-ViaGateway`) : il échange identifiant et mot de passe contre un jeton, ce qui est déconseillé pour une
  vraie application (l'application voit le mot de passe ; OAuth 2.1 retire ce flux). Le client `web-client` le refuse.
- **Local sans Keycloak** : `SAHTI_SECURITE_ACTIVE=false` pour la gateway (`sahti.securite.active`), activé en Docker.

**Limites et perspectives** :
- *Confiance dans le réseau interne* : les services croient `X-Patient-Id` et `X-Roles` parce que seule la gateway
  peut les joindre (réseau Docker, ports non publiés en production). En *zero trust*, chaque service validerait
  lui-même le JWT (resource server), et les appels de service à service (orchestrateur → portail…) utiliseraient un
  jeton *client credentials* propre à l'orchestrateur, ou du mTLS entre conteneurs.
- *Audience* : un seul public `sahticonnect-gateway` ; avec des services resource servers, une audience par service
  limiterait la portée d'un jeton volé.
- *Révocation* : un jeton reste valable jusqu'à son expiration (5 min) même après déconnexion : c'est le prix d'une
  vérification locale sans appel à Keycloak (l'introspection à chaque requête l'éviterait, au prix de la latence).
- Keycloak tourne en mode `start-dev` (HTTP, base H2 intégrée) : en production, HTTPS, base PostgreSQL et
  `start --optimized`.

## Suivre une requête dans tous les services (bonus)

Chaque demande porte un **identifiant de corrélation**, transmis d'un service à l'autre et écrit **en tête de chaque
ligne de journal**, dans le même format partout : `[identifiant] date niveau [thread] logger : message`.

| Où | Comment |
|---|---|
| api-gateway | reprend `X-Correlation-Id` du client (s'il est valide) ou en génère un (UUID), le relaie et le renvoie |
| portail, dashboard, CNAM, orchestrateur | filtre servlet `FiltreCorrelation` : en-tête lu (ou généré), mis dans le MDC, renvoyé ; une ligne par requête (méthode, chemin, statut, durée) |
| telemetrie | `IntercepteurCorrelationServeur` (gRPC) : métadonnée `x-correlation-id`, MDC posé à chaque étape de l'appel |
| Appels sortants (orchestrateur, dashboard) | REST et GraphQL : intercepteur `RestClient` ; SOAP : intercepteur Spring WS (en-tête HTTP, le WSDL ne change pas) ; gRPC : intercepteur client global |
| Changements de thread | file par patient (orchestrateur) et exécuteur des appels distants (dashboard) : le MDC est recopié sur le thread de la tâche |
| Workflow déclenché par une **alerte gRPC** (pas de requête HTTP) | l'identifiant est **celui du workflow** (`WF-…`) ; déclenché par `POST /workflows/declencher`, il garde celui de la requête |
| Capteur, abonnement de l'orchestrateur | `CAP-0001-20261126T120550` (un par flux), `orchestrateur-abonnement-1` |

`demo.ps1` affiche à la fin la commande pour le dernier workflow. Exemple réel : alerte CRITIQUE de la tachycardie,
qui compense d'abord le workflow ÉLEVÉE (saga) puis envoie l'ambulance. Les 5 conteneurs concernés, triés par heure
(extrait) :

```powershell
docker compose logs | Select-String "WF-2026-000053"
```

```
orchestrateur-1     | [WF-2026-000053] 12:06:06.076  INFO  OrchestrateurAlertes  : Alerte CRITIQUE ALR-2026-000020 pour PAT-0001 → workflow WF-2026-000053
dashboard-medical-1 | [WF-2026-000053] 12:06:06.150  INFO  FiltreCorrelation     : POST /graphql → 200 (52 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.152  INFO  WorkflowAlerteService : Étape ALERTE_RECUE : OK (60 ms) – Alerte ALR-2026-000020 enregistrée sur le dashboard
dossier-cnam-1      | [WF-2026-000053] 12:06:06.205  INFO  FiltreCorrelation     : POST /ws → 200 (38 ms)
dossier-cnam-1      | [WF-2026-000053] 12:06:06.232  INFO  FiltreCorrelation     : POST /ws → 200 (11 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.236  INFO  WorkflowAlerteService : Étape DROITS_VERIFIES : OK (74 ms) – Couvert : APCI 100 % (acte V 1)
portail-patient-1   | [WF-2026-000053] 12:06:06.304  INFO  FiltreCorrelation     : GET /rendez-vous/RDV-2026-000051 → 200 (27 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.306  INFO  SagaEscalade          : Escalade : alerte CRITIQUE ALR-2026-000020 → compensation du workflow ELEVEE WF-2026-000052 (RDV RDV-2026-000051)
dossier-cnam-1      | [WF-2026-000053] 12:06:06.373  INFO  FiltreCorrelation     : POST /ws → 200 (52 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.378  INFO  SagaEscalade          : Compensation FACTUREE de WF-2026-000052 : OK (61 ms) – Feuille FS-2026-000056 ANNULEE
portail-patient-1   | [WF-2026-000053] 12:06:06.490  INFO  FiltreCorrelation     : DELETE /rendez-vous/RDV-2026-000051 → 204 (22 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.516  INFO  WorkflowAlerteService : Étape ESCALADE : OK (210 ms) – WF-2026-000052 compensé : l'ambulance prend le relais
portail-patient-1   | [WF-2026-000053] 12:06:06.586  INFO  FiltreCorrelation     : POST /urgences/ambulances → 201 (51 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.595  INFO  WorkflowAlerteService : Étape INTERVENTION_DECLENCHEE : OK (68 ms) – Ambulance AMB-2026-000017 dépêchée
orchestrateur-1     | [WF-2026-000053] 12:06:06.644  INFO  WorkflowAlerteService : Étape FICHE_PUBLIEE : OK (35 ms) – Intervention INT-2026-000059 publiée
dossier-cnam-1      | [WF-2026-000053] 12:06:06.715  INFO  FiltreCorrelation     : POST /ws → 200 (26 ms)
orchestrateur-1     | [WF-2026-000053] 12:06:06.750  INFO  WorkflowAlerteService : Étape FACTUREE : OK (65 ms) – Feuille FS-2026-000057 CERTIFIEE : 145,000 DT
orchestrateur-1     | [WF-2026-000053] 12:06:06.779  INFO  WorkflowAlerteService : Workflow WF-2026-000053 terminé : FACTUREE
```

Une requête du navigateur garde l'UUID donné par la gateway jusqu'au bout. Par exemple, la fiche GraphQL de la démo
(constantes et couverture) :

```
dossier-cnam-1      | [1e4d73e8-816d-4302-b17a-514055d0b11f] 12:06:24.114  INFO  FiltreCorrelation        : POST /ws → 200 (41 ms)
telemetrie-1        | [1e4d73e8-816d-4302-b17a-514055d0b11f] 12:06:24.128  INFO  IntercepteurCorrelationServeur : gRPC sahticonnect.telemetrie.Telemetrie/DernieresMesures → OK (8 ms)
dossier-cnam-1      | [1e4d73e8-816d-4302-b17a-514055d0b11f] 12:06:24.227  INFO  FiltreCorrelation        : POST /ws → 200 (26 ms)
dashboard-medical-1 | [1e4d73e8-816d-4302-b17a-514055d0b11f] 12:06:24.249  INFO  FiltreCorrelation        : POST /graphql → 200 (630 ms)
api-gateway-1       | [1e4d73e8-816d-4302-b17a-514055d0b11f] 12:06:24.253  INFO  CorrelationIdFilter      : POST /api/graphql → dashboard-medical 200 OK (679 ms)
```

Pour imposer son propre identifiant : `curl.exe -H "X-Correlation-Id: test-oral-1" ...`, puis
`docker compose logs | Select-String "test-oral-1"`. Les lignes ci-dessus sont raccourcies (date, thread et nom du
logger abrégés) ; le format complet est `logging.pattern.console` dans chaque `application.yml`.

## Scénario de démo : capteur → alerte → workflow → dashboard

**Avec le client web** (http://localhost:4200, stack Docker démarrée ; connexion Keycloak, mot de passe `demo`) :
1. Connecté en `dr.chaabane` : « Alertes en direct » (subscription GraphQL ouverte).
2. Dans une fenêtre de navigation privée, connecté en `admin` : Accueil → « Simuler une alerte » (PAT-0001,
   CRITIQUE) : l'alerte puis l'intervention apparaissent en direct côté médecin.
3. `admin` → « Workflows » : la frise des 5 étapes avance en direct (SSE) jusqu'à FACTUREE.
4. `admin` → « CNAM (SOAP) » : la feuille de soins, et l'onglet XML pour montrer l'enveloppe.
5. Connecté en `ahmed` : seulement « Mon dossier » (PAT-0001) ; `/medecin/tableau-de-bord` affiche « Accès refusé ».
6. `docker compose stop dossier-cnam` : la fiche patient reste affichée, la couverture est remplacée par
   « Service indisponible » (réponse partielle) ; `docker compose start dossier-cnam` pour revenir.

Le même parcours est automatisé par Playwright : `cd web-client; npm run e2e`. Captures d'écran :
[`docs/captures/`](docs/captures/).

**En ligne de commande** (les 5 services démarrés) : `.\docs\tests\e2e\demo.ps1`. Le script vérifie les services,
lance le capteur en tachycardie sur PAT-0001, attend la fin des workflows, puis affiche leurs étapes, la fiche
GraphQL du patient et les feuilles de soins de l'historique CNAM. La tachycardie produit une alerte ÉLEVÉE puis
CRITIQUE : la démo montre aussi la **saga d'escalade** (workflow ÉLEVÉE COMPENSE, RDV ANNULE, feuille CS 1 annulée,
une seule facture active).

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
http://localhost:8080 (par exemple `.\docs\tests\rest\tester-rest.ps1 -ViaGateway`). Le script obtient alors un
jeton Keycloak avec le client `scripts` ([`docs/tests/jeton-keycloak.ps1`](docs/tests/jeton-keycloak.ps1), compte
`admin` par défaut, `-Utilisateur dr.chaabane` pour un autre) et l'envoie à chaque requête, renouvelé avant expiration.

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
| Client web | `cd web-client; npm test` | 36 tests unitaires (Vitest) : SOAP, intercepteurs (erreurs, jeton), gardes de rôle, SSE, pipe DT, fiche partielle, frise |
| Client web | `cd web-client; npm run e2e` | Playwright contre la stack Docker, avec connexion Keycloak : ahmed prend un RDV ; ahmed bloqué sur l'écran médecin et sur le dossier d'un autre (403) ; alerte en direct chez dr.chaabane → FACTUREE chez admin ; saga chez admin |
| Sécurité | `cd api-gateway; .\mvnw.cmd verify` | Matrice rôle × route, 401/403 (JSON et SOAP Fault), X-Patient-Id injecté et usurpation supprimée, jeton WebSocket, routes publiques |
| Corrélation | `.\mvnw.cmd verify` (chaque service) | Filtre (en-tête repris, généré, refusé s'il est invalide ; MDC sur un autre thread), intercepteurs gRPC serveur et client, SOAP via l'exécuteur du dashboard, workflow de l'orchestrateur : même identifiant sur tous ses appels SOAP/REST/GraphQL (requête HTTP, puis alerte gRPC → WF-…) |

## Structure du dépôt

```
sahticonnect/
├── CLAUDE.md                     ← cadrage, règles métier, avancement
├── docker-compose.yml            ← stack complète (client, gateway, 5 services, PostgreSQL, capteur)
├── .env.example                  ← modèle du .env (mots de passe des bases, ports publiés)
├── docker/postgres/init.sql      ← une base + un utilisateur par service
├── docker/keycloak/              ← sahticonnect-realm.json : rôles, clients, utilisateurs de démo (importé au démarrage)
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
