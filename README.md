# SahtiConnect – Plateforme Smart Healthcare (SOC 3ING GINF)

Plateforme de services interopérables : SOAP (dossier & CNAM), REST (portail patient),
GraphQL (tableau de bord médical), gRPC (télémétrie temps réel), orchestrées
derrière une API Gateway et consommées par un client web Angular.

## Services
| Service | Protocole | Port |
|---|---|---|
| dossier-cnam-service | SOAP | 8081 |
| portail-patient-service | REST | 8082 |
| dashboard-medical-service | GraphQL | 8083 |
| telemetrie-service | gRPC | 9090 |
| orchestrateur-service | REST | 8085 |
| api-gateway | HTTP | 8080 |
| web-client | Angular | 4200 |

## Lancement
docker compose up --build
