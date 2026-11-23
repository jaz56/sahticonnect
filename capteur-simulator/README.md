# capteur-simulator (Python, gRPC)

Capteur IoT simulé : il envoie les constantes d'un patient à `telemetrie-service` (port 9090) par un flux
**bidirectionnel** (`EnvoyerMesures`) et affiche chaque accusé de réception, en couleur selon le niveau.

| Script | RPC | Rôle |
|---|---|---|
| `capteur.py` | `EnvoyerMesures` (bidirectionnel) | Envoie FC, SpO2, tension systolique et diastolique, et température à chaque tick |
| `abonne_alertes.py` | `SouscrireAlertes` (server streaming) | Affiche les alertes en direct (démo) |

## Installation

```powershell
cd capteur-simulator
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Stubs Python générés depuis le contrat docs/proto/telemetrie.proto
.\generer_stubs.ps1
# (équivalent : python -m grpc_tools.protoc -I ..\docs\proto --python_out=. --pyi_out=. --grpc_python_out=. ..\docs\proto\telemetrie.proto)
```

Les fichiers `telemetrie_pb2*.py` ne sont pas versionnés : ils se régénèrent depuis le `.proto`.

## Utilisation

```powershell
# Terminal 1 : les alertes en direct
python abonne_alertes.py
python abonne_alertes.py --patients PAT-0001 PAT-0003 --niveau CRITIQUE

# Terminal 2 : un capteur
python capteur.py --patient PAT-0001 --scenario tachycardie --intervalle 1 --duree 60 --hote localhost:9090
```

| Option | Défaut | Rôle |
|---|---|---|
| `--patient` | `PAT-0001` | Patient (PAT-0001 … PAT-0005) |
| `--scenario` | `normal` | `normal`, `tachycardie`, `desaturation`, `hypertension`, `chute` |
| `--intervalle` | `1` | Secondes entre deux séries de mesures |
| `--duree` | `60` | Durée totale (s) ; à la fin, le capteur ferme son flux et le serveur répond `onCompleted` |
| `--bascule` | `10` | Secondes de valeurs normales avant l'anomalie |
| `--hote` | `localhost:9090` | Adresse du service gRPC |
| `--capteur` | `CAP-<n° patient>` | Identifiant du capteur |

Chaque scénario démarre avec des valeurs normales réalistes, avec un léger bruit. Après `--bascule`
secondes, l'anomalie s'installe progressivement en 15 s : la mesure passe donc par ÉLEVÉE avant CRITIQUE,
ce qui montre l'anti-rebond et l'aggravation.

| Scénario | Anomalie |
|---|---|
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

## Docker

Le `.proto` est dans `docs/proto` : l'image se construit depuis la **racine du dépôt**. Le fichier
`Dockerfile.dockerignore` limite ce qui est envoyé au build.

```powershell
docker build -f capteur-simulator/Dockerfile -t sahticonnect/capteur-simulator .
docker run --rm sahticonnect/capteur-simulator --patient PAT-0002 --scenario hypertension --hote host.docker.internal:9090
```
