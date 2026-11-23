"""
S'abonne aux alertes du service telemetrie-service (RPC SouscrireAlertes, server streaming)
et les affiche en direct : pratique pour la démo, à côté d'un capteur.

Si le service est injoignable ou si le flux se coupe (redémarrage du service, réseau), l'abonné
réessaie automatiquement toutes les 3 s (--delai). Ctrl+C pour quitter.

Exemples :
    python abonne_alertes.py
    python abonne_alertes.py --patients PAT-0001 PAT-0003 --niveau CRITIQUE --hote localhost:9090
"""
import argparse
import sys
import time
from datetime import datetime

import grpc

import telemetrie_pb2 as pb
import telemetrie_pb2_grpc as pb_grpc
from affichage import CYAN, GRIS, JAUNE, RESET, ROUGE, VERT, formater_alerte

# Erreurs dues à la requête elle-même : réessayer ne changerait rien
ERREURS_DEFINITIVES = {
    grpc.StatusCode.INVALID_ARGUMENT,
    grpc.StatusCode.NOT_FOUND,
    grpc.StatusCode.UNIMPLEMENTED,
    grpc.StatusCode.PERMISSION_DENIED,
    grpc.StatusCode.UNAUTHENTICATED,
}


def heure():
    return datetime.now().strftime("%H:%M:%S")


def ecouter(stub, requete, connecte):
    """
    Un abonnement : affiche les alertes jusqu'à la fin du flux.
    Renvoie quand le serveur termine le flux ; lève grpc.RpcError si le flux échoue.
    """
    flux = stub.SouscrireAlertes(requete)
    try:
        for alerte in flux:
            if not connecte[0]:
                connecte[0] = True
            print(f"{GRIS}{heure()}{RESET} {formater_alerte(alerte)}", flush=True)
    except KeyboardInterrupt:
        flux.cancel()  # le serveur retire l'abonnement (setOnCancelHandler)
        raise


def main():
    parser = argparse.ArgumentParser(description="Affiche en direct les alertes de télémétrie")
    parser.add_argument("--patients", nargs="*", default=[], help="patients suivis (défaut : tous)")
    parser.add_argument("--niveau", choices=["ELEVEE", "CRITIQUE"], default="ELEVEE", help="niveau minimum")
    parser.add_argument("--hote", default="localhost:9090", help="adresse du service gRPC")
    parser.add_argument("--delai", type=float, default=3.0, help="secondes entre deux tentatives de connexion")
    args = parser.parse_args()

    requete = pb.SouscrireAlertesRequest(identifiants_patients=args.patients,
                                         niveau_minimum=pb.NiveauAlerte.Value(args.niveau))
    print(f"{CYAN}Abonné aux alertes de {args.hote} | patients : {' '.join(args.patients) or 'tous'} "
          f"| niveau ≥ {args.niveau} | Ctrl+C pour quitter{RESET}", flush=True)

    try:
        with grpc.insecure_channel(args.hote) as canal:
            stub = pb_grpc.TelemetrieStub(canal)
            while True:
                # attend que le canal soit prêt pour annoncer la connexion (ou échouer au bout du délai)
                try:
                    grpc.channel_ready_future(canal).result(timeout=args.delai)
                    print(f"{VERT}{heure()} Connecté à {args.hote}, en attente d'alertes…{RESET}", flush=True)
                except grpc.FutureTimeoutError:
                    print(f"{JAUNE}{heure()} Service injoignable sur {args.hote}. "
                          f"Nouvelle tentative…{RESET}", flush=True)
                    continue  # channel_ready_future a déjà attendu --delai secondes

                connecte = [False]
                try:
                    ecouter(stub, requete, connecte)
                    message = "Flux terminé par le serveur"
                except grpc.RpcError as erreur:
                    if erreur.code() in ERREURS_DEFINITIVES:
                        print(f"{ROUGE}Erreur gRPC {erreur.code().name} : {erreur.details()}{RESET}")
                        return 1
                    message = f"Flux interrompu ({erreur.code().name})"
                print(f"{JAUNE}{heure()} {message}. Nouvelle tentative dans {args.delai:g} s…{RESET}", flush=True)
                time.sleep(args.delai)
    except KeyboardInterrupt:
        print(f"\n{GRIS}Abonnement terminé.{RESET}")
        return 0


if __name__ == "__main__":
    sys.exit(main())
