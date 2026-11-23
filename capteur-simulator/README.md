# capteur-simulator (Python, client gRPC)

Capteur IoT simulé et abonné aux alertes, pour tester et faire la démo de `telemetrie-service` (port 9090).

## Rôle

| Script | RPC | Rôle |
|---|---|---|
| `capteur.py` | `EnvoyerMesures` (bidirectionnel) | Envoie FC, SpO2, tension systolique et diastolique, et température à chaque tick ; affiche chaque accusé en couleur selon le niveau |
| `abonne_alertes.py` | `SouscrireAlertes` (server streaming) | Affiche les alertes en direct ; se reconnecte toutes les 3 s si le service est injoignable ou si le flux se coupe |

### Scénarios

Chaque scénario démarre avec des valeurs normales réalistes, avec un léger bruit. Après `--bascule` secondes,
l'anomalie s'installe en 15 s : la mesure passe par ÉLEVÉE avant CRITIQUE, ce qui montre l'anti-rebond et
l'aggravation.

| Scénario | Anomalie |
|---|---|
| `normal` | Aucune |
| `tachycardie` | FC 72 → 145 bpm |
| `desaturation` | SpO2 97,5 → 86 % |
| `hypertension` | Systolique 122 → 190, diastolique 78 → 118 mmHg |
| `chute` | Une chute signalée à la bascule (et FC 95) |

Exemple de sortie (scénario tachycardie) :

```
18:25:56 #51   FC          109,7 bpm  NORMAL
18:25:57 #56   FC          111,8 bpm  ELEVEE    ⚠ ELEVEE ALR-2026-000001 PAT-0001 – Alerte ELEVEE : fréquence cardiaque 111,8 bpm (FC ≥ 110)
18:25:58 #61   FC          113,8 bpm  ELEVEE    (alerte déjà émise, anti-rebond)
18:26:01 #76   FC          130,5 bpm  CRITIQUE  ⚠ CRITIQUE ALR-2026-000002 PAT-0001 – Alerte CRITIQUE : fréquence cardiaque 130,5 bpm (FC > 130)
18:26:02 #81   FC          138,3 bpm  CRITIQUE  (alerte déjà émise, anti-rebond)
Flux terminé : le serveur a confirmé la fin (onCompleted).
```

## Contrat

- Contrat : [`docs/proto/telemetrie.proto`](../docs/proto/telemetrie.proto), utilisé directement, sans copie.
- `generer_stubs.ps1` génère `telemetrie_pb2.py`, `telemetrie_pb2.pyi` et `telemetrie_pb2_grpc.py`. Ces fichiers ne
  sont pas versionnés : il faut les régénérer après chaque évolution du `.proto`.

## Lancer

```powershell
cd capteur-simulator
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
.\generer_stubs.ps1
# (équivalent : python -m grpc_tools.protoc -I ..\docs\proto --python_out=. --pyi_out=. --grpc_python_out=. ..\docs\proto\telemetrie.proto)

# Terminal 1 : les alertes en direct (Ctrl+C pour quitter)
python abonne_alertes.py
python abonne_alertes.py --patients PAT-0001 PAT-0003 --niveau CRITIQUE

# Terminal 2 : un capteur
python capteur.py --patient PAT-0001 --scenario tachycardie --intervalle 1 --duree 60 --hote localhost:9090
```

| Option de `capteur.py` | Défaut | Rôle |
|---|---|---|
| `--patient` | `PAT-0001` | Patient (PAT-0001 … PAT-0005) |
| `--scenario` | `normal` | Voir le tableau des scénarios |
| `--intervalle` | `1` | Secondes entre deux séries de mesures |
| `--duree` | `60` | Durée totale (s) ; à la fin, le capteur ferme son flux et le serveur répond `onCompleted` |
| `--bascule` | `10` | Secondes de valeurs normales avant l'anomalie |
| `--hote` | `localhost:9090` | Adresse du service gRPC |
| `--capteur` | `CAP-<n° patient>` | Identifiant du capteur |

`abonne_alertes.py` accepte `--patients` (défaut : tous), `--niveau` (`ELEVEE` ou `CRITIQUE`), `--hote` et
`--delai` (3 s entre deux tentatives de connexion).

### Docker

Le `.proto` est dans `docs/proto` : l'image se construit depuis la **racine du dépôt**. `Dockerfile.dockerignore`
limite ce qui est envoyé au build.

```powershell
docker build -f capteur-simulator/Dockerfile -t sahticonnect/capteur-simulator .
docker run --rm sahticonnect/capteur-simulator --patient PAT-0002 --scenario hypertension --hote host.docker.internal:9090
```

## Tester

Avec `telemetrie-service` démarré, lancer `abonne_alertes.py` dans un terminal, puis un scénario de `capteur.py`
dans un autre. On doit voir :
- les accusés NORMAL, puis une alerte ÉLEVÉE ;
- des mesures filtrées par l'anti-rebond ;
- l'aggravation CRITIQUE, émise immédiatement ;
- les mêmes alertes chez l'abonné ;
- `onCompleted` à la fin du flux.

## Choix techniques

- **grpcio 1.76** : client gRPC officiel, avec des stubs générés depuis le même contrat que le serveur Java.
- **Générateur Python** pour le flux client du streaming bidirectionnel ; les accusés sont lus sur le flux de retour
  pendant l'envoi.
- **Reconnexion automatique de l'abonné** : un redémarrage du service ne casse pas la démo. Il s'arrête seulement sur
  une erreur due à la requête elle-même (`INVALID_ARGUMENT`…).
- **Affichage robuste sous Windows** : couleurs ANSI activées, et sortie en UTF-8 quand elle est redirigée.
