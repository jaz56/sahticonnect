# SahtiConnect – Projet Service Oriented Computing (3ING GINF, ENICarthage)

Projet individuel. Plateforme Smart Healthcare : 4 services, chacun avec un protocole différent,
un orchestrateur, une API Gateway et un client web Angular. Tout est dockerisé.

## Environnement
- Windows + PowerShell : toujours donner des commandes PowerShell (pas de bash).
- Java 21, Spring Boot 3, Maven. Angular pour le front. Python pour le capteur simulé.

## Services
| Dossier | Protocole | Port | Rôle |
|---|---|---|---|
| dossier-cnam-service | SOAP (Spring WS, contract-first) | 8081 | Droits CNAM, feuilles de soins, historique, comparaison de garanties |
| portail-patient-service | REST (Spring Web + springdoc) | 8082 | CRUD créneaux/RDV, ordonnances, profil, ambulances |
| dashboard-medical-service | GraphQL (Spring for GraphQL, schema-first) | 8083 | Vue consolidée médecin, filtres, interventions, subscriptions |
| telemetrie-service | gRPC | 9090 | Streaming des mesures, détection des seuils, alertes |
| orchestrateur-service | REST + clients des 4 protocoles | 8085 | Workflow d'alerte |
| api-gateway | Spring Cloud Gateway | 8080 | Point d'entrée unique |
| capteur-simulator | Python grpcio | – | Capteur IoT simulé |
| web-client | Angular + Apollo | 4200 | Client web unique |

Une base PostgreSQL par service (database-per-service).

## Conventions
- Identifiant patient commun à tous les services : `PAT-0001` … `PAT-0005`.
- Namespace SOAP : `http://sahticonnect.tn/cnam`. Les opérations suivent le couple `xxxRequest`/`xxxResponse`.
- Montants en DT avec 3 décimales. Code et commentaires en français.
- Les contrats sont dans `docs/` : wsdl/ (XSD + WSDL exporté), openapi/, graphql/, proto/.
  Les contrats sont la source de vérité : ne pas les modifier sans le signaler.

## Règles métier
- Seuils CRITIQUE : FC < 40 ou > 130 ; SpO2 < 90 ; systolique > 180 ou < 90 ; diastolique > 120 ; température > 39,5 ; chute.
- Seuils ÉLEVÉE : FC 110–130 ; SpO2 90–94 ; systolique 160–180 ; diastolique > 100 ; température 38,5–39,5.
- CRITIQUE → ambulance (REST). ÉLEVÉE → RDV d'urgence dans les 2 h (REST).
- Remboursement : APCI 100 % ; filière publique 80 % ; autres cas 50 % ; vignette expirée → non couvert.
- `statutFacturation` : `EN_ATTENTE` | `FACTUREE` | `A_REGULARISER`.
- Patient non couvert → intervention quand même, `statutFacturation = A_REGULARISER`.
- Lettres-clés (valeurs fictives) : C = 45, CS = 60, V = 55, K = 3, B = 0,3, Z = 2 DT.
- États du workflow : ALERTE_RECUE → DROITS_VERIFIES → INTERVENTION_DECLENCHEE → FICHE_PUBLIEE → FACTUREE | ECHEC.
- Seuils : une seule source de vérité, `telemetrie-service`. Le niveau de chaque mesure est renvoyé dans
  `Mesure.niveau` (champ 8 du .proto, rempli par le serveur, ignoré en entrée). Les autres services
  (dashboard…) l'utilisent tel quel et ne recalculent jamais les seuils.
- Anti-rebond (telemetrie) : une alerte max. par (patient, type, niveau) sur 60 s ; une aggravation est émise immédiatement.
- Niveau de risque (dashboard) : CRITIQUE = alerte CRITIQUE non résolue < 24 h ; ELEVE = alerte ELEVEE non résolue < 24 h ;
  MODERE = une alerte < 7 jours ; sinon FAIBLE. Recalculé à chaque alerte enregistrée / résolue / prise en charge
  ET périodiquement (`@Scheduled`, 5 min, `sahti.risque.recalcul.*`), pour qu'une alerte de plus de 24 h fasse baisser le niveau.
- Statut d'alerte (dashboard) : NOUVELLE → PRISE_EN_CHARGE (creerIntervention) → RESOLUE (mutation `resoudreAlerte`).
  `enregistrerAlerte` est idempotente (même id ALR-… → alerte existante) : l'orchestrateur peut réessayer.

## Patients de test
| ID | Nom | Pathologie | Couverture |
|---|---|---|---|
| PAT-0001 | Ahmed Ben Salah | Insuffisance cardiaque | APCI 100 %, vignette VG-2026-000001 |
| PAT-0002 | Leila Trabelsi | Hypertension | Vignette expirée |
| PAT-0003 | Karim Jaziri | BPCO | Filière publique 80 % |
| PAT-0004 | Sonia Mansour | Diabète | Remboursement 50 % |
| PAT-0005 | Hedi Gharbi | Personne âgée (chutes) | Filière publique 80 % |

## Plan et avancement
- [x] Étape 0 : cadrage et structure du dépôt
- [x] Étape 1 : contrats (XSD SOAP, OpenAPI, SDL GraphQL, .proto)
- [x] Étape 2 : les 4 services
  - REST : générer les interfaces depuis `docs/openapi/portail-patient.yaml` avec
    `openapi-generator-maven-plugin` (`interfaceOnly=true`) ; les contrôleurs les implémentent.
  - gRPC : la détection des seuils (`telemetrie-service`) applique les règles ci-dessus, y compris
    la diastolique (`TENSION_DIASTOLIQUE` : ÉLEVÉE > 100, CRITIQUE > 120), comme documenté dans
    `docs/proto/telemetrie.proto`.
- [x] Étape 3 : capteur simulé et détection des seuils
- [x] Étape 4 : orchestrateur
  - Contrat `docs/openapi/orchestrateur.yaml` (ids `WF-AAAA-NNNNNN`, même schéma `Erreur` que le portail, flux SSE).
  - Workflow : une file par patient (ordre garanti), patients en parallèle, `alerteId` unique (idempotence).
    CNAM en panne → étape ECHEC mais on continue (`A_REGULARISER`) ; facturation seulement si l'intervention a réussi ;
    relance = uniquement les étapes manquantes, feuille de soins jamais réémise.
  - Résilience4j : timeout 3 s, retry 3 tentatives sur erreur technique seulement (jamais sur 4xx métier ni
    SOAP Fault Client), créations réessayées uniquement si la connexion a échoué, circuit breaker par service.
- [x] Étape 7 : Docker et docker-compose (avancée avant la gateway)
  - `docker-compose.yml` à la racine : un conteneur `postgres:16-alpine`, une base + un utilisateur par service
    (`docker/postgres/init.sql`, mots de passe dans `.env`, modèle `.env.example`), les 5 services (profil `docker`),
    le capteur dans le profil compose `capteur`.
  - Noms des conteneurs = adresses dans le réseau : `postgres`, `dossier-cnam`, `portail-patient`, `dashboard-medical`,
    `telemetrie`, `orchestrateur`. Mêmes ports qu'en local (surchargeables par `*_PORT` dans `.env`).
  - Healthchecks : `/actuator/health` (sondes activées : UP seulement après le chargement des données) ;
    `grpc_health_probe` pour telemetrie. `depends_on: service_healthy` ; l'orchestrateur attend les 4 autres.
  - PostgreSQL : `schema.sql` idempotent (`IF NOT EXISTS`), données de test chargées seulement si la base est vide.
- [x] Étape 5 : gateway (Keycloak en bonus, plus tard)
  - `api-gateway` (8080), Spring Cloud 2025.0.3 (Boot 3.5), WebFlux. Routes : `/api/portail/**`, `/api/orchestrateur/**`
    (SSE `/workflows/flux` sans timeout), `/api/graphql` (HTTP + WebSocket), `/api/cnam/ws/**` (SOAP + WSDL) ;
    préfixe retiré avant de relayer. gRPC n'est PAS exposé (navigateur → GraphQL subscription et SSE).
  - CORS http://localhost:4200, `X-Correlation-Id`, timeout 5 s, circuit breaker par route ; fallback 503 au format
    `Erreur` (+ `service`), SOAP Fault Server pour la CNAM. Swagger UI regroupé (contrats relayés, `servers` réécrit).
  - dossier-cnam : `forward-headers-strategy=framework` → le WSDL annonce l'adresse par laquelle il est lu.
  - Scripts de test : paramètre `-ViaGateway`. En production, seul le port 8080 serait publié.
- [x] Étape 6 : client Angular (`web-client`, 4200)
  - Angular 22.3 (Node ≥ 22.22), standalone + signals + zoneless, lazy loading, Material 3, locale `fr-TN`, pipe `dt`.
  - Toutes les requêtes passent par la gateway (`environment.gatewayUrl`), jamais directement par un service.
  - Contract-first : client REST généré (openapi-generator `typescript-angular`, `npm run generate:api`),
    GraphQL généré (graphql-codegen + Apollo, `npm run generate:graphql`) ; code généré non versionné.
    SOAP : `CnamSoapService` (enveloppes à la main, DOMParser, Faults) ; SSE : EventSource → Observable.
  - Erreurs : intercepteur (schéma Erreur + X-Correlation-Id) ; réponses GraphQL partielles → puce « Service indisponible ».
  - Gateway : timeout de la route GraphQL porté à 10 s (réponses partielles quand la CNAM est en panne).
  - Tests : Vitest (`npm test`), Playwright contre la stack Docker (`npm run e2e`) ; captures dans `docs/captures/`.
- [x] Bonus 1 : escalade avec compensation (saga) dans l'orchestrateur
  - Contrats : SOAP `annulerFeuilleDeSoins` (+ fault `FEUILLE_INCONNUE`, `StatutFeuille` ANNULEE) ; GraphQL
    `EtatWorkflow` COMPENSE, `StatutFacturation` ANNULEE ; orchestrateur.yaml : état COMPENSE, `TypeEtape`
    (EXECUTION | COMPENSATION), étape ESCALADE, `compensePar`, `enCompensation`.
  - Règle (`sahti.escalade.fenetre` = 30 min) : alerte CRITIQUE + workflow ELEVEE du même patient dans la fenêtre,
    RDV d'urgence CONFIRME et à venir → compensation AVANT l'ambulance (étape ESCALADE du workflow CRITIQUE).
  - Compensation en ordre inverse, seulement des effets qui existent : FACTUREE → annulerFeuilleDeSoins ;
    FICHE_PUBLIEE → mettreAJourIntervention (COMPENSE, ANNULEE) + resoudreAlerte ; INTERVENTION_DECLENCHEE →
    DELETE /rendez-vous (409 + TERMINE → IGNOREE). Idempotentes, retry `appel-idempotent` ; échec → ECHEC,
    `POST /relancer` reprend les compensations manquantes.
  - Migration PostgreSQL : `schema.sql` supprime les contraintes CHECK des enums (Hibernate ne les met pas à jour).
- [x] Bonus 2 : sécurité Keycloak (la gateway est le point d'application)
  - Keycloak 26.8.2 (compose, port 8180, `start-dev --import-realm`, `docker/keycloak/sahticonnect-realm.json`) :
    rôles PATIENT/MEDECIN/ADMIN, client public `web-client` (Code + PKCE), client `scripts` (password grant, RÉSERVÉ
    aux scripts de test), 9 comptes mot de passe `demo` (ahmed…hedi → claim `patient_id`, dr.chaabane/haddad/kallel, admin),
    audience `sahticonnect-gateway`. Issuer public fixé par `KC_HOSTNAME=http://localhost:8180` ; la gateway lit les
    clés par `http://keycloak:8080` (`jwk-set-uri`) et exige l'issuer public.
  - Gateway resource server (`sahti.securite.active`, false en local sans Keycloak) : matrice portail P/M/A, graphql M/A,
    declencher M/A, orchestrateur et CNAM ADMIN (WSDL public) ; 401 `NON_AUTHENTIFIE` / 403 `ACCES_REFUSE` (Erreur ou
    SOAP Fault) ; X-Patient-Id/X-Roles du client toujours supprimés, X-Roles ajouté, X-Patient-Id = patient_id (PATIENT) ;
    `?access_token=` accepté seulement pour l'upgrade WebSocket `/api/graphql`, retiré et masqué.
  - Portail : contrôle d'appartenance par X-Patient-Id → 403 `ACCES_REFUSE` (ajouté au contrat) ; sans en-tête, accès complet.
  - Angular : angular-auth-oidc-client 22 (Code + PKCE, refresh), intercepteur Bearer (gateway seulement), `roleGuard`,
    menu selon les rôles du jeton, patient limité à son `patient_id`, écran `/acces-refuse` ; WebSocket `?access_token=` ;
    SSE via `@microsoft/fetch-event-source` (en-tête Authorization).
  - Scripts `-ViaGateway` : jeton via `docs/tests/jeton-keycloak.ps1` (client `scripts`, `-Utilisateur`, admin par défaut).
- [x] Bonus 3 : identifiant de corrélation dans tous les services
  - Classe `Correlation` recopiée dans chaque service (paquetage `correlation`, pas de bibliothèque partagée) : en-tête
    `X-Correlation-Id`, métadonnée gRPC `x-correlation-id`, clé MDC `correlationId`, valeur `[A-Za-z0-9._:-]{1,64}` sinon UUID.
  - Entrée : `FiltreCorrelation` (servlet, portail/dashboard/cnam/orchestrateur, une ligne par requête sauf /actuator) ;
    `IntercepteurCorrelationServeur` (`@GlobalServerInterceptor`, telemetrie, sauf grpc.health).
  - Sortie : `RestClientCustomizer` (REST + GraphQL), `IntercepteurCorrelationSoap` (en-tête HTTP, WSDL inchangé),
    `IntercepteurCorrelationGrpc` (`@GlobalClientInterceptor`) ; MDC recopié par `ExecuteurParPatient` et
    `ExecuteurAvecCorrelation` (dashboard). Alerte gRPC sans requête → identifiant = WF-… ; capteur : `CAP-0001-<date>`.
  - Format de journal commun (`logging.pattern.console`) : `[corrId] date niveau [thread] logger : message`, gateway comprise.
  - Orchestrateur `mem_limit: 768m` (85 % de 512m mesurés). Dockerfiles : 3 essais Maven (-U), eol=lf.
- [ ] Étape 8 : tests et documentation

Ordre de réalisation : 7 → 5 → 6 → bonus (Keycloak) → 8.

Avance étape par étape : explique ce que tu fais, puis commit à la fin de chaque étape.