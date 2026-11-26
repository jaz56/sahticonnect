"""
Capteur IoT simulé : envoie les constantes d'un patient au service telemetrie-service en streaming
bidirectionnel (RPC EnvoyerMesures) et affiche chaque accusé de réception.

Exemple :
    python capteur.py --patient PAT-0001 --scenario tachycardie --intervalle 1 --duree 60 --hote localhost:9090

Chaque scénario commence par des valeurs normales réalistes (avec du bruit), puis bascule vers
l'anomalie au bout de --bascule secondes ; l'anomalie s'installe progressivement (rampe de 15 s),
ce qui fait passer la mesure par ÉLEVÉE avant CRITIQUE.
"""
import argparse
import random
import sys
import threading
import time
from datetime import datetime

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

import telemetrie_pb2 as pb
import telemetrie_pb2_grpc as pb_grpc
from affichage import (ABREVIATION, CYAN, GRIS, RESET, ROUGE, colorer_niveau, formater_alerte,
                       formater_valeur)

SCENARIOS = ["normal", "tachycardie", "desaturation", "hypertension", "chute"]
DUREE_RAMPE = 15.0  # secondes pour passer des valeurs normales à l'anomalie

# Valeurs normales : (moyenne, écart-type du bruit)
NORMALES = {
    pb.FREQUENCE_CARDIAQUE: (72.0, 2.5),
    pb.SPO2: (97.5, 0.6),
    pb.TENSION_SYSTOLIQUE: (122.0, 3.0),
    pb.TENSION_DIASTOLIQUE: (78.0, 2.0),
    pb.TEMPERATURE: (36.8, 0.1),
}
UNITES = {
    pb.FREQUENCE_CARDIAQUE: "bpm",
    pb.SPO2: "%",
    pb.TENSION_SYSTOLIQUE: "mmHg",
    pb.TENSION_DIASTOLIQUE: "mmHg",
    pb.TEMPERATURE: "°C",
    pb.CHUTE: "",
}
# Valeur cible de l'anomalie, par scénario
ANOMALIES = {
    "tachycardie": {pb.FREQUENCE_CARDIAQUE: 145.0},
    "desaturation": {pb.SPO2: 86.0, pb.FREQUENCE_CARDIAQUE: 98.0},
    "hypertension": {pb.TENSION_SYSTOLIQUE: 190.0, pb.TENSION_DIASTOLIQUE: 118.0},
    "chute": {pb.FREQUENCE_CARDIAQUE: 95.0},
}
BORNES = {pb.SPO2: (0.0, 100.0)}


def valeurs_du_tick(scenario, ecoule, bascule, chute_envoyee):
    """Valeurs à envoyer à l'instant « ecoule » (secondes depuis le début)."""
    progression = 0.0 if ecoule < bascule else min(1.0, (ecoule - bascule) / DUREE_RAMPE)
    valeurs = {}
    for type_mesure, (moyenne, bruit) in NORMALES.items():
        cible = ANOMALIES.get(scenario, {}).get(type_mesure, moyenne)
        valeur = moyenne + (cible - moyenne) * progression + random.gauss(0, bruit)
        bas, haut = BORNES.get(type_mesure, (0.0, 1000.0))
        valeurs[type_mesure] = round(min(haut, max(bas, valeur)), 1)
    if scenario == "chute":
        # la chute est signalée une seule fois, au moment de la bascule
        valeurs[pb.CHUTE] = 1.0 if (ecoule >= bascule and not chute_envoyee) else 0.0
    return valeurs


def generer_mesures(args, envoyees, arret):
    """Générateur gRPC : produit le flux client, une série de mesures par tick."""
    sequence = 0
    debut = time.monotonic()
    chute_envoyee = False
    while not arret.is_set():
        ecoule = time.monotonic() - debut
        if ecoule >= args.duree:
            break
        horodatage = Timestamp()
        horodatage.GetCurrentTime()
        valeurs = valeurs_du_tick(args.scenario, ecoule, args.bascule, chute_envoyee)
        if valeurs.get(pb.CHUTE) == 1.0:
            chute_envoyee = True
        for type_mesure, valeur in valeurs.items():
            sequence += 1
            envoyees[sequence] = (type_mesure, valeur)
            yield pb.Mesure(identifiant_patient=args.patient, capteur_id=args.capteur, type=type_mesure,
                            valeur=valeur, unite=UNITES[type_mesure], horodatage=horodatage,
                            sequence=sequence)
        arret.wait(args.intervalle)


def afficher_accuse(accuse, envoyees):
    type_mesure, valeur = envoyees.pop(accuse.sequence, (None, None))
    heure = datetime.now().strftime("%H:%M:%S")
    nom = ABREVIATION.get(type_mesure, "?")
    texte_valeur = formater_valeur(type_mesure, valeur, UNITES.get(type_mesure, "")) if type_mesure else "?"
    if accuse.statut == pb.REJETEE:
        print(f"{GRIS}{heure} #{accuse.sequence:<4}{RESET} {nom:<11} {texte_valeur:<10} "
              f"{ROUGE}REJETEE{RESET}  {accuse.message}")
        return
    ligne = (f"{GRIS}{heure} #{accuse.sequence:<4}{RESET} {nom:<11} {texte_valeur:<10} "
             f"{colorer_niveau(accuse.niveau, f'{pb.NiveauAlerte.Name(accuse.niveau):<8}')}")
    if accuse.HasField("alerte"):
        ligne += "  " + formater_alerte(accuse.alerte)
    elif accuse.niveau != pb.NORMAL:
        ligne += f"  {GRIS}(alerte déjà émise, anti-rebond){RESET}"
    print(ligne, flush=True)


def main():
    parser = argparse.ArgumentParser(description="Capteur IoT simulé SahtiConnect (gRPC, streaming bidirectionnel)")
    parser.add_argument("--patient", default="PAT-0001", help="identifiant patient (PAT-0001 … PAT-0005)")
    parser.add_argument("--scenario", choices=SCENARIOS, default="normal")
    parser.add_argument("--intervalle", type=float, default=1.0, help="secondes entre deux séries de mesures")
    parser.add_argument("--duree", type=float, default=60.0, help="durée totale en secondes")
    parser.add_argument("--bascule", type=float, default=10.0, help="secondes avant le début de l'anomalie")
    parser.add_argument("--hote", default="localhost:9090", help="adresse du service gRPC")
    parser.add_argument("--capteur", default=None, help="identifiant du capteur (défaut : CAP-<n° patient>)")
    args = parser.parse_args()
    args.capteur = args.capteur or "CAP-" + args.patient.split("-")[-1]

    # identifiant de corrélation du flux (métadonnée gRPC x-correlation-id) : retrouvé dans les journaux de telemetrie
    correlation = f"{args.capteur}-{time.strftime('%Y%m%dT%H%M%S')}"
    print(f"{CYAN}Capteur {args.capteur} → {args.hote} | patient {args.patient} | scénario {args.scenario} "
          f"| bascule à {args.bascule:g} s | durée {args.duree:g} s | corrélation {correlation}{RESET}")

    envoyees = {}
    arret = threading.Event()
    with grpc.insecure_channel(args.hote) as canal:
        stub = pb_grpc.TelemetrieStub(canal)
        appel = stub.EnvoyerMesures(generer_mesures(args, envoyees, arret),
                                    metadata=(("x-correlation-id", correlation),))
        try:
            for accuse in appel:
                afficher_accuse(accuse, envoyees)
        except KeyboardInterrupt:
            arret.set()
            appel.cancel()
            print(f"\n{GRIS}Capteur arrêté.{RESET}")
            return 0
        except grpc.RpcError as erreur:
            if erreur.code() == grpc.StatusCode.UNAVAILABLE:
                print(f"{ROUGE}Service injoignable sur {args.hote} : est-il démarré ?{RESET}")
            else:
                print(f"{ROUGE}Erreur gRPC {erreur.code().name} : {erreur.details()}{RESET}")
            return 1
    print(f"{CYAN}Flux terminé : le serveur a confirmé la fin (onCompleted).{RESET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
