"""
S'abonne aux alertes du service telemetrie-service (RPC SouscrireAlertes, server streaming)
et les affiche en direct : pratique pour la démo, à côté d'un capteur.

Exemples :
    python abonne_alertes.py
    python abonne_alertes.py --patients PAT-0001 PAT-0003 --niveau CRITIQUE --hote localhost:9090
"""
import argparse
import sys
from datetime import datetime

import grpc

import telemetrie_pb2 as pb
import telemetrie_pb2_grpc as pb_grpc
from affichage import CYAN, GRIS, RESET, ROUGE, formater_alerte


def main():
    parser = argparse.ArgumentParser(description="Affiche en direct les alertes de télémétrie")
    parser.add_argument("--patients", nargs="*", default=[], help="patients suivis (défaut : tous)")
    parser.add_argument("--niveau", choices=["ELEVEE", "CRITIQUE"], default="ELEVEE", help="niveau minimum")
    parser.add_argument("--hote", default="localhost:9090", help="adresse du service gRPC")
    args = parser.parse_args()

    requete = pb.SouscrireAlertesRequest(identifiants_patients=args.patients,
                                         niveau_minimum=pb.NiveauAlerte.Value(args.niveau))
    print(f"{CYAN}Abonné aux alertes de {args.hote} | patients : {' '.join(args.patients) or 'tous'} "
          f"| niveau ≥ {args.niveau} | Ctrl+C pour quitter{RESET}")

    with grpc.insecure_channel(args.hote) as canal:
        flux = pb_grpc.TelemetrieStub(canal).SouscrireAlertes(requete)
        try:
            for alerte in flux:
                heure = datetime.now().strftime("%H:%M:%S")
                print(f"{GRIS}{heure}{RESET} {formater_alerte(alerte)}", flush=True)
        except KeyboardInterrupt:
            flux.cancel()  # le serveur retire l'abonnement (setOnCancelHandler)
            print(f"\n{GRIS}Abonnement terminé.{RESET}")
        except grpc.RpcError as erreur:
            if erreur.code() == grpc.StatusCode.CANCELLED:
                return 0
            if erreur.code() == grpc.StatusCode.UNAVAILABLE:
                print(f"{ROUGE}Service injoignable sur {args.hote} : est-il démarré ?{RESET}")
            else:
                print(f"{ROUGE}Erreur gRPC {erreur.code().name} : {erreur.details()}{RESET}")
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
