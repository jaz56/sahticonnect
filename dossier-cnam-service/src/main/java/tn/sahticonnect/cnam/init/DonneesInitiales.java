package tn.sahticonnect.cnam.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.EntreeHistorique;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;
import tn.sahticonnect.cnam.entity.Filiere;
import tn.sahticonnect.cnam.entity.LettreCle;
import tn.sahticonnect.cnam.entity.LigneActe;
import tn.sahticonnect.cnam.entity.StatutFeuille;
import tn.sahticonnect.cnam.repository.AssureRepository;
import tn.sahticonnect.cnam.repository.EntreeHistoriqueRepository;
import tn.sahticonnect.cnam.repository.FeuilleDeSoinsRepository;
import tn.sahticonnect.cnam.service.DossierCnamService;
import tn.sahticonnect.cnam.service.TarificationService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Données de test : les 5 patients de CLAUDE.md et 2 à 3 entrées d'historique chacun.
 * N'insère rien si la base contient déjà des assurés (profil docker, redémarrage).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonneesInitiales implements CommandLineRunner {

    /** Fin de validité des vignettes valides. */
    private static final LocalDate VALIDE_JUSQU_AU = LocalDate.of(2027, 12, 31);

    private final AssureRepository assureRepository;
    private final EntreeHistoriqueRepository historiqueRepository;
    private final FeuilleDeSoinsRepository feuilleRepository;
    private final TarificationService tarification;
    private final Clock horloge;

    @Override
    @Transactional
    public void run(String... args) {
        if (assureRepository.count() > 0) {
            log.info("Données déjà présentes : initialisation ignorée");
            return;
        }

        Assure ahmed = assureRepository.save(Assure.builder()
                .identifiantPatient("PAT-0001").nom("Ben Salah").prenom("Ahmed")
                .dateNaissance(LocalDate.of(1958, 3, 14)).matriculeCNAM("CNAM-1958-0001")
                .codeVignetteCNAM("VG-2026-000001").filiere(Filiere.PUBLIQUE)
                .dateFinValidite(VALIDE_JUSQU_AU).apci(true).codeAPCI("APCI-IC-0001")
                .build());
        Assure leila = assureRepository.save(Assure.builder()
                .identifiantPatient("PAT-0002").nom("Trabelsi").prenom("Leila")
                .dateNaissance(LocalDate.of(1965, 7, 22)).matriculeCNAM("CNAM-1965-0002")
                .codeVignetteCNAM("VG-2026-000002").filiere(Filiere.PRIVEE)
                .dateFinValidite(LocalDate.of(2026, 6, 30)).apci(false)
                .build());
        Assure karim = assureRepository.save(Assure.builder()
                .identifiantPatient("PAT-0003").nom("Jaziri").prenom("Karim")
                .dateNaissance(LocalDate.of(1952, 11, 5)).matriculeCNAM("CNAM-1952-0003")
                .codeVignetteCNAM("VG-2026-000003").filiere(Filiere.PUBLIQUE)
                .dateFinValidite(VALIDE_JUSQU_AU).apci(false)
                .build());
        Assure sonia = assureRepository.save(Assure.builder()
                .identifiantPatient("PAT-0004").nom("Mansour").prenom("Sonia")
                .dateNaissance(LocalDate.of(1970, 2, 18)).matriculeCNAM("CNAM-1970-0004")
                .codeVignetteCNAM("VG-2026-000004").filiere(Filiere.REMBOURSEMENT)
                .dateFinValidite(VALIDE_JUSQU_AU).apci(false)
                .build());
        Assure hedi = assureRepository.save(Assure.builder()
                .identifiantPatient("PAT-0005").nom("Gharbi").prenom("Hedi")
                .dateNaissance(LocalDate.of(1940, 9, 9)).matriculeCNAM("CNAM-1940-0005")
                .codeVignetteCNAM("VG-2026-000005").filiere(Filiere.PUBLIQUE)
                .dateFinValidite(VALIDE_JUSQU_AU).apci(false)
                .build());

        historiqueRepository.saveAll(List.of(
                // PAT-0001 – insuffisance cardiaque
                entree(ahmed, "2026-02-10", "Hôpital Charles Nicolle", "Insuffisance cardiaque – décompensation",
                        "FS-2026-900001", acte(LettreCle.CS, "1", "Consultation cardiologie"),
                        acte(LettreCle.Z, "30", "Radiographie thoracique")),
                entree(ahmed, "2026-05-18", "Hôpital Charles Nicolle", "Suivi insuffisance cardiaque",
                        null, acte(LettreCle.CS, "1", "Consultation cardiologie"),
                        acte(LettreCle.B, "40", "Bilan BNP")),
                entree(ahmed, "2026-09-03", "Centre de santé de base Bab Saadoun", "Contrôle tensionnel",
                        null, acte(LettreCle.C, "1", "Consultation généraliste")),
                // PAT-0002 – hypertension
                entree(leila, "2025-11-12", "Clinique Les Oliviers", "Hypertension artérielle",
                        null, acte(LettreCle.CS, "1", "Consultation cardiologie"),
                        acte(LettreCle.K, "15", "ECG")),
                entree(leila, "2026-04-02", "Clinique Les Oliviers", "Hypertension – ajustement du traitement",
                        null, acte(LettreCle.CS, "1", "Consultation cardiologie")),
                // PAT-0003 – BPCO
                entree(karim, "2026-01-20", "Hôpital Abderrahmen Mami", "BPCO – exacerbation",
                        "FS-2026-900002", acte(LettreCle.CS, "1", "Consultation pneumologie"),
                        acte(LettreCle.K, "20", "Spirométrie")),
                entree(karim, "2026-07-08", "Hôpital Abderrahmen Mami", "Suivi BPCO",
                        null, acte(LettreCle.CS, "1", "Consultation pneumologie"),
                        acte(LettreCle.B, "25", "Gazométrie")),
                // PAT-0004 – diabète
                entree(sonia, "2026-03-15", "Institut National de Nutrition", "Diabète de type 2",
                        null, acte(LettreCle.CS, "1", "Consultation endocrinologie"),
                        acte(LettreCle.B, "30", "HbA1c et glycémie")),
                entree(sonia, "2026-06-21", "Cabinet Dr Haddad", "Diabète – suivi trimestriel",
                        null, acte(LettreCle.C, "1", "Consultation généraliste")),
                entree(sonia, "2026-10-04", "Institut National de Nutrition", "Diabète – fond d'œil",
                        null, acte(LettreCle.CS, "1", "Consultation ophtalmologie"),
                        acte(LettreCle.K, "10", "Fond d'œil")),
                // PAT-0005 – personne âgée, chutes
                entree(hedi, "2026-04-27", "Hôpital Habib Thameur", "Chute à domicile – contusion de hanche",
                        "FS-2026-900003", acte(LettreCle.V, "1", "Visite à domicile"),
                        acte(LettreCle.Z, "25", "Radiographie de hanche")),
                entree(hedi, "2026-08-14", "Hôpital Habib Thameur", "Bilan de chute – gériatrie",
                        null, acte(LettreCle.CS, "1", "Consultation gériatrie"))
        ));
        feuilleAmbulanceDeDemonstration(ahmed);
        log.info("Données de test chargées : 5 assurés, {} entrées d'historique, {} feuille(s) de soins",
                historiqueRepository.count(), feuilleRepository.count());
    }

    /**
     * Feuille de l'ambulance de démonstration du dashboard (intervention INT-AAAA-000001 de PAT-0001, il y a 2 h) :
     * V 1 + K 30 = 145,000 DT, APCI 100 %, certifiée. Numéro FS-AAAA-900004 (série 9xxxxx des données de démo,
     * distincte de la séquence des feuilles émises).
     */
    private void feuilleAmbulanceDeDemonstration(Assure ahmed) {
        LocalDate date = LocalDateTime.now(horloge).minusHours(2).toLocalDate();
        int annee = LocalDate.now(horloge).getYear();
        List<ActeCote> actes = List.of(acte(LettreCle.V, "1", "Visite à domicile (intervention ambulance)"),
                acte(LettreCle.K, "30", "Prise en charge et transport médicalisé"));
        FeuilleDeSoins feuille = new FeuilleDeSoins();
        feuille.setNumero("FS-%d-900004".formatted(annee));
        feuille.setAssure(ahmed);
        feuille.setDateSoins(date);
        feuille.setEtablissement("SahtiConnect – intervention à domicile");
        feuille.setMedecin("Équipe d'intervention SahtiConnect");
        feuille.setReferenceIntervention("INT-%d-000001".formatted(annee));
        for (ActeCote a : actes) {
            feuille.ajouterLigne(new LigneActe(new ActeCote(a.getLettreCle(), a.getCoefficient(), a.getLibelle()),
                    tarification.montantActe(a)));
        }
        BigDecimal total = tarification.montantTotal(actes);   // 145,000 DT
        feuille.setMontantTotal(total);
        feuille.setTauxApplique(100);                           // APCI
        feuille.setPartCNAM(total);
        feuille.setPartPatient(new BigDecimal("0.000"));
        feuille.setStatut(StatutFeuille.CERTIFIEE);
        feuille.setCodeCertification(DossierCnamService.codeCertification(feuille));
        feuilleRepository.save(feuille);

        EntreeHistorique entree = entree(ahmed, date.toString(), feuille.getEtablissement(),
                "Prise en charge suite à l'intervention " + feuille.getReferenceIntervention(),
                feuille.getNumero(), actes.toArray(ActeCote[]::new));
        historiqueRepository.save(entree);
    }

    private static ActeCote acte(LettreCle lettreCle, String coefficient, String libelle) {
        return new ActeCote(lettreCle, new BigDecimal(coefficient), libelle);
    }

    private static EntreeHistorique entree(Assure assure, String date, String etablissement, String diagnostic,
                                           String numeroFeuille, ActeCote... actes) {
        EntreeHistorique entree = new EntreeHistorique();
        entree.setAssure(assure);
        entree.setDate(LocalDate.parse(date));
        entree.setEtablissement(etablissement);
        entree.setDiagnostic(diagnostic);
        entree.setNumeroFeuilleSoins(numeroFeuille);
        entree.setActes(new ArrayList<>(List.of(actes)));
        return entree;
    }
}
