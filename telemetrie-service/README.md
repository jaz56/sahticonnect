# telemetrie-service (gRPC, port 9090)

Service gRPC de télémétrie : il reçoit en streaming les mesures des capteurs, détecte le franchissement des
seuils et diffuse les alertes.

## Rôle

- Les capteurs (`capteur-simulator`) envoient leurs mesures en flux.
- L'orchestrateur s'abonne aux alertes pour déclencher les interventions.
- Le dashboard lit les dernières mesures, avec leur niveau.
- C'est la **seule source de vérité des seuils** : le niveau de chaque mesure est calculé ici et transmis aux
  autres services dans `Mesure.niveau`.

### RPC

| RPC | Type | Rôle |
|---|---|---|
| `EnvoyerMesures` | bidirectionnel | Le capteur envoie un flux de mesures ; chaque mesure reçoit un `AccuseReception` (ACCEPTEE/REJETEE, niveau, alerte éventuelle) |
| `SouscrireAlertes` | server streaming | Flux d'alertes sans fin, filtré par patients (vide = tous) et niveau minimum (`NON_SPECIFIE` = ELEVEE) |
| `DernieresMesures` | unaire | Dernières mesures d'un patient, la plus récente en premier ; `nombre` par type, 10 par défaut ; chaque mesure porte son `niveau` |

Erreurs gRPC :
- `NOT_FOUND` : patient non suivi ;
- `INVALID_ARGUMENT` : identifiant mal formé, `nombre` hors de 0–100, type `NON_SPECIFIE`, niveau inconnu.

### Traitement d'une mesure

```
Mesure → validation → détection des seuils → stockage (avec le niveau) → anti-rebond → alerte (base + diffusion) → accusé
```

1. **Validation** : une mesure invalide est REJETEE avec un motif clair, sans être stockée.
   - patient inconnu (la liste vient de `telemetrie.patients`) ;
   - type `NON_SPECIFIE` ;
   - valeur hors de la plage physique : FC 0–300, SpO2 0–100, tension 0–300, température 25–45, chute 0 ou 1.
2. **Détection** (`DetecteurSeuils`, classe pure) :

   | Constante | CRITIQUE | ÉLEVÉE |
   |---|---|---|
   | FC | < 40 ou > 130 | 110 à 130 |
   | SpO2 | < 90 | 90 à 94 |
   | Systolique | > 180 ou < 90 | 160 à 180 |
   | Diastolique | > 120 | > 100 |
   | Température | > 39,5 | 38,5 à 39,5 |
   | Chute | valeur 1 | – |

   Les bornes « à » sont incluses : FC 130 → ÉLEVÉE, FC 131 → CRITIQUE. Le seuil franchi est lisible (« FC > 130 »).
3. **Stockage** : en mémoire, les 100 dernières mesures par patient et par type, **avec leur niveau**. Le champ
   `Mesure.niveau` envoyé par un client est ignoré.
4. **Anti-rebond** (fenêtre `telemetrie.anti-rebond`, 60 s par défaut) :
   - une alerte au plus par (patient, type, niveau) sur la fenêtre ;
   - une aggravation (ÉLEVÉE puis CRITIQUE) est émise immédiatement ;
   - les mesures filtrées restent ACCEPTEE, avec leur niveau.
5. **Alerte** : persistée (identifiant `ALR-AAAA-NNNNNN` tiré de la séquence `alerte_seq`), puis diffusée aux abonnés
   dont le filtre l'accepte. Un client qui se déconnecte est retiré.

## Contrat

- Contrat : [`docs/proto/telemetrie.proto`](../docs/proto/telemetrie.proto) (proto3), copié à l'identique dans
  `src/main/proto/`.
- `protobuf-maven-plugin` génère les messages et le stub `TelemetrieGrpc` dans `tn.sahticonnect.telemetrie`.
  `os-maven-plugin` choisit le binaire `protoc` selon l'OS.
- **Évolution compatible** : `Mesure.niveau` (champ 8) a été ajouté avec un nouveau numéro de champ. Un client
  généré avec l'ancien contrat continue de fonctionner et ignore simplement ce champ.
- Services techniques :
  - `grpc.reflection.v1.ServerReflection` : grpcurl et Postman découvrent l'API sans le `.proto` ;
  - `grpc.health.v1.Health` : `Check` renvoie `SERVING`.

## Lancer

```powershell
cd telemetrie-service
.\mvnw.cmd spring-boot:run
```

- **Profil par défaut** : H2 en mémoire. Le service n'expose pas de HTTP, donc pas de console H2.
- **Profil `docker`** : PostgreSQL, configuré par `DB_URL`, `DB_USER` et `DB_PASSWORD`
  (par défaut `jdbc:postgresql://postgres-telemetrie:5432/telemetrie`, `telemetrie`, `telemetrie`).

```powershell
docker build -t sahticonnect/telemetrie-service .
docker run -p 9090:9090 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/telemetrie sahticonnect/telemetrie-service
```

## Tester

```powershell
# Tests unitaires (toutes les bornes des seuils, anti-rebond avec horloge fixe, stockage, validation) et tests
# d'intégration sur un serveur gRPC in-process (3 RPC, niveau renvoyé, rejets, filtre et nettoyage des abonnés,
# NOT_FOUND, INVALID_ARGUMENT, réflexion, health check)
.\mvnw.cmd verify
```

Service démarré :
- capteur simulé et abonné aux alertes : [`capteur-simulator/README.md`](../capteur-simulator/README.md) ;
- commandes grpcurl et import dans Postman : [`docs/tests/grpc/README.md`](../docs/tests/grpc/README.md).

## Choix techniques

- **Spring gRPC 0.12.0** (gRPC Java 1.76, protobuf 4.32) :
  - Spring gRPC 1.x exige Spring Framework 7, c'est-à-dire Spring Boot 4 ;
  - la branche 0.x vise Boot 3.5, et sa dernière version est construite contre Boot 3.5.6 ;
  - c'est le projet officiel Spring, alors que `net.devh` ne publie plus de nouvelle version ;
  - `grpc-services` (réflexion, health) est optionnel dans le starter : il est déclaré explicitement.
- **Streaming bidirectionnel** pour les capteurs : une connexion ouverte en continu, et un accusé immédiat par mesure.
- **Une seule source des seuils** : le dashboard affiche `Mesure.niveau` au lieu de recopier la règle.
- **Anti-rebond** : sans lui, une FC à 140 pendant une minute produirait 60 alertes, donc 60 ambulances.
- **Stockage des mesures en mémoire** (`ConcurrentHashMap` + `LinkedBlockingDeque` bornée, thread-safe), et
  **alertes en base** : seules les alertes ont besoin d'un historique durable.
