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
- [ ] Étape 4 : orchestrateur
- [ ] Étape 5 : gateway (+ Keycloak en bonus)
- [ ] Étape 6 : client Angular
- [ ] Étape 7 : Docker et docker-compose
- [ ] Étape 8 : tests et documentation

Avance étape par étape : explique ce que tu fais, puis commit à la fin de chaque étape.