# api-gateway (Spring Cloud Gateway, port 8080)

Point d'entrée unique de SahtiConnect : le client web (Angular, port 4200) ne connaît qu'une adresse,
http://localhost:8080. La gateway relaie chaque appel vers le bon service, et ajoute les fonctions transverses :
CORS, identifiant de corrélation, timeouts, circuit breakers et Swagger UI regroupé.

## Rôle

- **Routage** : `/api/<service>/…` → le service concerné ; le préfixe est retiré avant de relayer.
- **Protocoles relayés** : REST, GraphQL (HTTP et WebSocket pour les subscriptions), SOAP (et le WSDL), flux SSE.
- **Fonctions transverses** :
  - **CORS** : seul http://localhost:4200 (le futur client Angular) est autorisé, préflights `OPTIONS` compris. La même
    vérification d'origine protège la poignée de main WebSocket : un autre site reçoit 403.
  - **`X-Correlation-Id`** : repris de la requête s'il est fourni (et valide), sinon généré. Il est transmis au service,
    renvoyé dans la réponse et écrit dans le journal de la gateway :
    `[3f2c…] GET /api/portail/patients/PAT-0001 → portail-patient 200 OK (12 ms)`.
  - **Timeout de réponse** : 5 s ; 10 s pour GraphQL (réponses partielles, voir Choix techniques) ; désactivé pour le
    flux SSE et les WebSocket.
  - **Circuit breaker par route**, avec un repli au format du contrat quand un service ne répond pas.
  - **Swagger UI regroupé** et **actuator** (`/actuator/health`, `/actuator/gateway/routes`).

### Routes

| Route (id) | Chemin public | Service relayé | Particularités |
|---|---|---|---|
| `portail-patient` | `/api/portail/**` | `portail-patient:8082/**` | REST |
| `orchestrateur-flux` | `/api/orchestrateur/workflows/flux` | `orchestrateur:8085/workflows/flux` | SSE : relayé au fil de l'eau, `response-timeout: -1` |
| `orchestrateur` | `/api/orchestrateur/**` | `orchestrateur:8085/**` | REST |
| `dashboard-medical` | `/api/graphql` | `dashboard-medical:8083/graphql` | GraphQL en HTTP **et** en WebSocket (`graphql-transport-ws`), `response-timeout: 10000` |
| `dossier-cnam` | `/api/cnam/ws/**` | `dossier-cnam:8081/ws/**` | SOAP et WSDL (`/api/cnam/ws/dossier-cnam.wsdl`) |
| `portail-patient-openapi` | `/api/portail/openapi/**` | `portail-patient:8082/openapi/**` | contrat relu pour Swagger UI, `servers` réécrit |
| `orchestrateur-openapi` | `/api/orchestrateur/openapi/**` | `orchestrateur:8085/openapi/**` | idem |

- **gRPC (`telemetrie-service`) n'est pas exposé**. Le navigateur ne parle pas gRPC : gRPC exige HTTP/2 avec des
  trailers, qu'aucune API JavaScript de navigateur ne permet de lire, et gRPC-Web demanderait un proxy de conversion
  (Envoy) et un client généré en plus.
  - Le front n'en a pas besoin : il reçoit les alertes par la **subscription GraphQL** `nouvelleAlerte` (WebSocket) et
    l'avancement des workflows par le **flux SSE** de l'orchestrateur. Les constantes lui arrivent par GraphQL.
  - gRPC reste un protocole **interne**, entre services et avec les capteurs (machines, pas navigateurs) : le capteur
    continue de parler directement à `telemetrie:9090`.
- **WSDL via la gateway** : la gateway envoie `X-Forwarded-Host`, `-Port`, `-Proto` et `-Prefix` (`/api/cnam`).
  `dossier-cnam-service` (`server.forward-headers-strategy=framework` + `transformWsdlLocations`) annonce alors
  `http://localhost:8080/api/cnam/ws` dans le WSDL lu via la gateway, et garde `http://localhost:8081/ws` en direct.

### Réponses de repli (circuit ouvert, service arrêté, délai dépassé)

| Route | Réponse |
|---|---|
| REST, GraphQL, SSE | `503`, format `Erreur` du contrat (`horodatage`, `statut`, `code`, `message`, `chemin`) + `service` |
| SOAP | SOAP Fault `Server` (`faultactor` = `api-gateway`, détail `SERVICE_INDISPONIBLE` + service), en HTTP 500 |

```json
{
  "horodatage": "2026-11-24T19:54:07+01:00",
  "statut": 503,
  "code": "SERVICE_INDISPONIBLE",
  "message": "Service portail-patient indisponible : connection timed out after 2000 ms: portail-patient/172.19.0.3:8082",
  "chemin": "/api/portail/patients/PAT-0001",
  "service": "portail-patient"
}
```

- Le schéma `Erreur` des contrats n'interdit pas les propriétés supplémentaires : `service` s'ajoute sans modifier
  le contrat.
- Pour SOAP, la Fault part en **HTTP 500**, comme l'exigent SOAP 1.1 et le WS-I Basic Profile (R1126) pour toute
  Fault : un client SOAP (`WebServiceTemplate`, SoapUI) lit alors la Fault au lieu de signaler une erreur de transport.

## Contrat

La gateway n'a pas de contrat propre : elle expose ceux des services, sous `/api/…`.

| Interface | URL via la gateway |
|---|---|
| Swagger UI regroupé (liste déroulante : portail-patient, orchestrateur) | http://localhost:8080/swagger-ui.html |
| Contrat REST du portail | http://localhost:8080/api/portail/openapi/portail-patient.yaml |
| Contrat de l'orchestrateur | http://localhost:8080/api/orchestrateur/openapi/orchestrateur.yaml |
| GraphQL (HTTP) / subscriptions | `POST http://localhost:8080/api/graphql` / `ws://localhost:8080/api/graphql` |
| WSDL | http://localhost:8080/api/cnam/ws/dossier-cnam.wsdl |
| Flux SSE des workflows | http://localhost:8080/api/orchestrateur/workflows/flux |
| Routes de la gateway | http://localhost:8080/actuator/gateway/routes |

**Swagger UI** : les contrats de `docs/` annoncent l'adresse directe du service (`servers: http://localhost:8082`).
Lus tels quels, « Try it out » appellerait le service directement : autre origine, et gateway contournée. Le filtre
`ServeursOpenApi` réécrit donc `servers` en `/api/portail` dans la réponse relayée, sans modifier les fichiers de
`docs/`. Un test vérifie sur les vrais contrats que seule cette section change.

## Lancer

```powershell
cd api-gateway
.\mvnw.cmd spring-boot:run
```

- **Profil par défaut** : services sur localhost (8081, 8082, 8083, 8085). Un service arrêté → 503 sur sa route
  seulement, les autres routes continuent de fonctionner.
- **Profil `docker`** : `PORTAIL_URL`, `ORCHESTRATEUR_URL`, `DASHBOARD_URL`, `CNAM_URL` (par défaut les noms des conteneurs).
- Dans `docker-compose.yml`, la gateway attend que les 5 services soient *healthy*. **En production, seul le port 8080
  serait publié** : les ports des services ne le sont que pour le développement et les tests en accès direct.

```powershell
docker build -t sahticonnect/api-gateway .
```

## Tester

```powershell
# 26 tests :
#  - RoutesIntegrationTest (WireMock joue les 4 services) : chaque route et le retrait du préfixe, en-têtes
#    X-Forwarded-* (et ceux envoyés par le client ignorés), préflight CORS accepté / refusé, en-têtes exposés,
#    X-Correlation-Id généré / conservé / remplacé s'il est invalide, timeout de 5 s → 503, SSE relayé sans buffering
#    et au-delà de 5 s de silence, réécriture de servers, Swagger UI, actuator ;
#  - FallbackIntegrationTest (port fermé) : 503 au format Erreur, SOAP Fault Server, ouverture du circuit ;
#  - WebSocketGraphQlIntegrationTest : subscription graphql-transport-ws relayée vers un faux dashboard (Reactor Netty),
#    sous-protocole négocié, X-Correlation-Id transmis ;
#  - ServeursOpenApiTest : réécriture sur les vrais contrats de docs/openapi
.\mvnw.cmd verify

# Tous les scripts passent par la gateway avec -ViaGateway (stack docker compose démarrée)
..\docs\tests\soap\tester-soap.ps1 -ViaGateway
..\docs\tests\rest\tester-rest.ps1 -ViaGateway
..\docs\tests\graphql\tester-graphql.ps1 -ViaGateway          # dont la subscription en WebSocket
..\docs\tests\orchestrateur\tester-orchestrateur.ps1 -ViaGateway
..\docs\tests\e2e\demo.ps1 -Docker -ViaGateway
```

En plus des tests, vérifié à la main contre la stack Docker : WSDL via la gateway (adresse 8080) et en direct
(adresse 8081) ; SSE reçu en direct après 6 s de silence ; WebSocket ouverte avec `Origin: http://localhost:4200`
(`connection_ack` reçu) et refusée (403) depuis une autre origine ; `docker compose stop portail-patient dossier-cnam`
→ 503 JSON et SOAP Fault, puis retour à 200 dès le redémarrage.

## Choix techniques

- **Spring Cloud 2025.0.3 « Northfields »** : le release train compatible Spring Boot 3.5 (2025.1 vise Boot 4).
  Gateway 4.3.5, artefact `spring-cloud-starter-gateway-server-webflux` (nouveau nom de `spring-cloud-starter-gateway`
  depuis la 4.3), propriétés sous `spring.cloud.gateway.server.webflux.*`.
- **WebFlux (Netty)** plutôt que la variante MVC : non bloquant, adapté aux connexions longues (SSE, WebSocket) qui
  occupent une connexion sans occuper un thread.
- **Routes déclarées en YAML** (lisibles et visibles dans `/actuator/gateway/routes`). Un seul filtre écrit en Java :
  `ServeursOpenApi`.
- **Timeouts** : délai de connexion 2 s, délai de réponse 5 s par le client HTTP Netty, désactivé par route
  (`metadata.response-timeout: -1`) pour le SSE. **10 s pour GraphQL** : quand dossier-cnam est en panne, le dashboard
  attend jusqu'à 2 × 3 s (vignette puis droits) avant de renvoyer sa réponse partielle. Avec 5 s, la gateway coupait
  avant et le client recevait un 503 au lieu des données disponibles (constaté avec le client Angular, corrigé et testé).
  Règle : le délai d'une route d'agrégation couvre la somme des délais des appels qu'elle enchaîne. Le TimeLimiter de Resilience4j est désactivé : il couperait aussi
  le SSE et les WebSocket. Le circuit breaker ne compte pas un flux long comme un « appel lent ».
- **Circuit breaker** (Resilience4j, une instance par route) : 50 % d'échecs sur 10 appels → ouvert 30 s. Pendant ce
  temps, la gateway répond tout de suite 503 au lieu d'attendre le délai à chaque appel.
- **X-Forwarded-\*** : depuis la 4.3 (CVE-2025-41235), la gateway ne les ajoute que si le client direct figure dans
  `trusted-proxies` (adresses locales et réseaux Docker ici, la gateway étant le point d'entrée). Les X-Forwarded-*
  envoyés par le client sont supprimés au préalable : un client ne peut pas faire annoncer un autre hôte dans le WSDL.
- **`X-Correlation-Id`** en `WebFilter` (et non `GlobalFilter`) : il couvre aussi les réponses qui ne passent par
  aucune route (actuator, Swagger, fallback). La valeur reçue est contrôlée (64 caractères sûrs au plus), sinon elle
  est remplacée : pas d'injection dans les journaux.
