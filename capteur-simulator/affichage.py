"""Affichage console commun au capteur et à l'abonné (couleurs ANSI selon le niveau)."""
import os
import sys

import telemetrie_pb2 as pb

# Active les séquences ANSI dans la console Windows classique
if os.name == "nt":
    os.system("")

# Sortie redirigée (fichier, docker logs) : UTF-8 ; console : caractère remplacé plutôt qu'une erreur
for _flux in (sys.stdout, sys.stderr):
    if _flux.isatty():
        _flux.reconfigure(errors="replace")
    else:
        _flux.reconfigure(encoding="utf-8", errors="replace")

RESET = "\033[0m"
GRIS = "\033[90m"
VERT = "\033[32m"
JAUNE = "\033[33m"
ROUGE = "\033[31m"
ROUGE_GRAS = "\033[1;31m"
CYAN = "\033[36m"

COULEUR_NIVEAU = {
    pb.NORMAL: VERT,
    pb.ELEVEE: JAUNE,
    pb.CRITIQUE: ROUGE_GRAS,
}

ABREVIATION = {
    pb.FREQUENCE_CARDIAQUE: "FC",
    pb.SPO2: "SpO2",
    pb.TENSION_SYSTOLIQUE: "Systolique",
    pb.TENSION_DIASTOLIQUE: "Diastolique",
    pb.TEMPERATURE: "Température",
    pb.CHUTE: "Chute",
}


def nom_niveau(niveau):
    return pb.NiveauAlerte.Name(niveau)


def colorer_niveau(niveau, texte=None):
    texte = texte if texte is not None else nom_niveau(niveau)
    return COULEUR_NIVEAU.get(niveau, GRIS) + texte + RESET


def formater_valeur(type_mesure, valeur, unite=""):
    if type_mesure == pb.CHUTE:
        return "chute !" if valeur == 1 else "aucune"
    texte = f"{valeur:.1f}".rstrip("0").rstrip(".").replace(".", ",")
    return f"{texte} {unite}".strip()


def formater_alerte(alerte):
    return (f"{colorer_niveau(alerte.niveau, '⚠ ' + nom_niveau(alerte.niveau))} {alerte.id} "
            f"{alerte.identifiant_patient} – {alerte.message}")
