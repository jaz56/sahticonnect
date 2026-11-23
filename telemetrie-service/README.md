# telemetrie-service (gRPC, port 9090)

Service gRPC de SahtiConnect : il reçoit en streaming les mesures des capteurs, détecte le franchissement
des seuils et diffuse les alertes à l'orchestrateur, au dashboard et aux autres abonnés.

## Contrat et génération

- Contrat : [`docs/proto/telemetrie.proto`](../docs/proto/telemetrie.proto) (proto3). Sa copie,
  `src/main/proto/telemetrie.proto`, doit rester identique.
- `protobuf-maven-plugin`, avec `os-maven-plugin` qui choisit le binaire `protoc` selon l'OS (`windows-x86_64`
  ou `linux-x86_64`…), génère les messages et le stub `TelemetrieGrpc` dans `tn.sahticonnect.telemetrie`.
- `TelemetrieGrpcService` étend `TelemetrieGrpc.TelemetrieImplBase`.

**Starter : Spring gRPC 0.12.0**, avec gRPC Java 1.76.0 et protobuf 4.32.1.
- Spring gRPC 1.x exige Spring Framework 7, c'est-à-dire Spring Boot 4.
- La branche 0.x vise Spring Boot 3.5, et sa dernière version, 0.12.0, est construite contre Boot 3.5.6.
- C'est le projet officiel Spring. `net.devh grpc-spring-boot-starter` (3.1.0) ne publie plus de nouvelle
  version depuis longtemps.
- `grpc-services` (réflexion et health check) est optionnel dans le starter : il est déclaré explicitement.

## RPC

| RPC | Type | Rôle |
|---|---|---|
| `EnvoyerMesures` | bidirectionnel | Le capteur envoie un flux de mesures ; chaque mesure reçoit un `AccuseReception` (ACCEPTEE/REJETEE, niveau, alerte éventuelle) |
| `SouscrireAlertes` | server streaming | Flux d'alertes sans fin, filtré par patients (vide = tous) et niveau minimum (`NON_SPECIFIE` = ELEVEE) |
| `DernieresMesures` | unaire | Dernières mesures d'un patient, la plus récente en premier ; `nombre` par type, 10 par défaut |

Erreurs gRPC :
- `NOT_FOUND` : patient non suivi (`DernieresMesures`) ;
- `INVALID_ARGUMENT` :
  - identifiant mal formé ;
  - `nombre` hors de l'intervalle 0–100 ;
  - type `NON_SPECIFIE` dans `types` ;
  - niveau inconnu.

Services techniques, activés par `spring.grpc.server.reflection.enabled` et `spring.grpc.server.health.enabled` :
- `grpc.reflection.v1.ServerReflection` : grpcurl et Postman découvrent l'API sans le `.proto` ;
- `grpc.health.v1.Health` : `Check` renvoie `SERVING`.

## Traitement d'une mesure

```
Mesure → validation → stockage mémoire → détection des seuils → anti-rebond → alerte (base + diffusion) → accusé
```

1. **Validation** (`ValidateurMesures`) : une mesure invalide est REJETEE avec un motif clair, sans être stockée.
   - patient inconnu : la liste vient de `telemetrie.patients` (PAT-0001 … PAT-0005) ;
   - type `NON_SPECIFIE` ;
   - valeur hors de la plage physique : FC 0–300, SpO2 0–100, tension 0–300, température 25–45, chute 0 ou 1.
2. **Stockage** (`StockageMesures`) : en mémoire, les 100 dernières mesures par patient et par type
   (`ConcurrentHashMap` + `LinkedBlockingDeque` bornée).
3. **Détection** (`DetecteurSeuils`, classe pure) :

   | Constante | CRITIQUE | ÉLEVÉE |
   |---|---|---|
   | FC | < 40 ou > 130 | 110 à 130 |
   | SpO2 | < 90 | 90 à 94 |
   | Systolique | > 180 ou < 90 | 160 à 180 |
   | Diastolique | > 120 | > 100 |
   | Température | > 39,5 | 38,5 à 39,5 |
   | Chute | valeur 1 | – |

   Les bornes « à » sont incluses : FC 130 → ÉLEVÉE, FC 131 → CRITIQUE, FC 40 → normal, FC 39 → CRITIQUE.
   Le seuil franchi est lisible : « FC > 130 », « SpO2 < 90 »…
4. **Anti-rebond** (`AntiRebond`, fenêtre `telemetrie.anti-rebond`, 60 s par défaut) :
   - une alerte au plus par (patient, type, niveau) sur la fenêtre ;
   - une aggravation (ÉLEVÉE puis CRITIQUE) est émise immédiatement ;
   - un niveau inférieur n'est pas réémis tant qu'une alerte plus grave est récente ;
   - les mesures filtrées restent ACCEPTEE, avec leur niveau, mais sans alerte.
5. **Alerte** : persistée (entité `Alerte`, identifiant `ALR-AAAA-NNNNNN` tiré de la séquence `alerte_seq`),
   puis diffusée à tous les abonnés dont le filtre l'accepte (`DiffuseurAlertes`). Quand un client se
   déconnecte, son abonnement est retiré (`ServerCallStreamObserver.setOnCancelHandler`).

## Lancer

```powershell
cd telemetrie-service
.\mvnw.cmd spring-boot:run
```

Profil par défaut : H2 en mémoire. Le service n'expose pas de HTTP, donc pas de console H2.
Profil `docker` : PostgreSQL, configuré par `DB_URL`, `DB_USER` et `DB_PASSWORD`
(par défaut `jdbc:postgresql://postgres-telemetrie:5432/telemetrie`, `telemetrie`, `telemetrie`).

```powershell
docker build -t sahticonnect/telemetrie-service .
docker run -p 9090:9090 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/telemetrie sahticonnect/telemetrie-service
```

## Tester

```powershell
# Tests unitaires (toutes les bornes des seuils, anti-rebond avec horloge fixe, stockage, validation)
# et tests d'intégration sur un serveur gRPC in-process (3 RPC, rejets, filtre, NOT_FOUND, INVALID_ARGUMENT,
# réflexion, health check)
.\mvnw.cmd verify
```

Service démarré :
- capteur simulé : [`capteur-simulator/README.md`](../capteur-simulator/README.md) ;
- commandes grpcurl et import dans Postman : [`docs/tests/grpc/README.md`](../docs/tests/grpc/README.md).
