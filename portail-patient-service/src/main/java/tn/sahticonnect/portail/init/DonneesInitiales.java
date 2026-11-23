package tn.sahticonnect.portail.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.ContactUrgence;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Filiere;
import tn.sahticonnect.portail.entity.LigneOrdonnance;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.Ordonnance;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.entity.Sexe;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.MedecinRepository;
import tn.sahticonnect.portail.repository.OrdonnanceRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.repository.RendezVousRepository;
import tn.sahticonnect.portail.service.GenerateurIdentifiants;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Données de test, relatives à la date du jour (horloge injectée) :
 * 3 médecins, les 5 patients de CLAUDE.md (même identité que dans dossier-cnam-service),
 * les créneaux des 14 prochains jours, des ordonnances et quelques rendez-vous.
 * N'insère rien si la base contient déjà des médecins (profil docker, redémarrage).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonneesInitiales implements CommandLineRunner {

    static final int NOMBRE_JOURS = 14;
    private static final int DUREE_CRENEAU_MINUTES = 30;
    private static final DateTimeFormatter FORMAT_JOUR = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HHmm");

    private final MedecinRepository medecinRepository;
    private final PatientRepository patientRepository;
    private final CreneauRepository creneauRepository;
    private final OrdonnanceRepository ordonnanceRepository;
    private final RendezVousRepository rendezVousRepository;
    private final GenerateurIdentifiants identifiants;
    private final Clock horloge;

    @Override
    @Transactional
    public void run(String... args) {
        if (medecinRepository.count() > 0) {
            log.info("Données déjà présentes : initialisation ignorée");
            return;
        }
        LocalDateTime maintenant = LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        LocalDate aujourdhui = maintenant.toLocalDate();

        // ---------- Médecins ----------
        Medecin chaabane = medecinRepository.save(Medecin.builder().id("MED-001").nom("Chaabane").prenom("Mohamed")
                .specialite("Cardiologie").etablissement("Hôpital Charles Nicolle").ville("Tunis")
                .filiere(Filiere.PUBLIQUE).teleconsultation(true).tarifConsultation(new BigDecimal("60.000")).build());
        Medecin haddad = medecinRepository.save(Medecin.builder().id("MED-002").nom("Haddad").prenom("Ines")
                .specialite("Médecine générale").etablissement("Cabinet Dr Haddad").ville("Ariana")
                .filiere(Filiere.PRIVEE).teleconsultation(true).tarifConsultation(new BigDecimal("45.000")).build());
        Medecin kallel = medecinRepository.save(Medecin.builder().id("MED-003").nom("Kallel").prenom("Sami")
                .specialite("Pneumologie").etablissement("Hôpital Abderrahmen Mami").ville("Ariana")
                .filiere(Filiere.PUBLIQUE).teleconsultation(false).tarifConsultation(new BigDecimal("60.000")).build());
        List<Medecin> medecins = List.of(chaabane, haddad, kallel);

        // ---------- Patients (identité identique au service SOAP) ----------
        Patient ahmed = patientRepository.save(Patient.builder().id("PAT-0001").nom("Ben Salah").prenom("Ahmed")
                .dateNaissance(LocalDate.of(1958, 3, 14)).sexe(Sexe.M).telephone("+216 98 123 456")
                .email("ahmed.bensalah@exemple.tn").adresse("12 rue de Marseille, 1000 Tunis")
                .pathologie("Insuffisance cardiaque").medecinTraitant(chaabane)
                .contactUrgence(new ContactUrgence("Fatma Ben Salah", "Épouse", "+216 22 333 444")).build());
        Patient leila = patientRepository.save(Patient.builder().id("PAT-0002").nom("Trabelsi").prenom("Leila")
                .dateNaissance(LocalDate.of(1965, 7, 22)).sexe(Sexe.F).telephone("+216 97 456 789")
                .email("leila.trabelsi@exemple.tn").adresse("5 avenue Habib Bourguiba, 2080 Ariana")
                .pathologie("Hypertension").medecinTraitant(haddad).build());
        Patient karim = patientRepository.save(Patient.builder().id("PAT-0003").nom("Jaziri").prenom("Karim")
                .dateNaissance(LocalDate.of(1952, 11, 5)).sexe(Sexe.M).telephone("+216 95 222 111")
                .email("karim.jaziri@exemple.tn").adresse("18 rue Ibn Khaldoun, 2037 Ariana")
                .pathologie("BPCO").medecinTraitant(kallel)
                .contactUrgence(new ContactUrgence("Amel Jaziri", "Fille", "+216 23 444 555")).build());
        Patient sonia = patientRepository.save(Patient.builder().id("PAT-0004").nom("Mansour").prenom("Sonia")
                .dateNaissance(LocalDate.of(1970, 2, 18)).sexe(Sexe.F).telephone("+216 99 777 888")
                .email("sonia.mansour@exemple.tn").adresse("3 rue de Rome, 1001 Tunis")
                .pathologie("Diabète").medecinTraitant(haddad).build());
        Patient hedi = patientRepository.save(Patient.builder().id("PAT-0005").nom("Gharbi").prenom("Hedi")
                .dateNaissance(LocalDate.of(1940, 9, 9)).sexe(Sexe.M).telephone("+216 71 555 666")
                .email("hedi.gharbi@exemple.tn").adresse("27 rue d'Espagne, 1000 Tunis")
                .pathologie("Personne âgée (chutes)").medecinTraitant(haddad)
                .contactUrgence(new ContactUrgence("Nadia Gharbi", "Fille", "+216 24 666 777")).build());

        // ---------- Créneaux ----------
        Map<String, Creneau> creneaux = new HashMap<>();
        for (int j = 0; j < NOMBRE_JOURS; j++) {
            LocalDate jour = aujourdhui.plusDays(j);
            for (Medecin medecin : medecins) {
                genererCreneaux(medecin, jour, maintenant).forEach(c -> creneaux.put(c.getId(), c));
            }
        }
        creneauRepository.saveAll(creneaux.values());

        // ---------- Ordonnances (au moins une expirée) ----------
        ordonnanceRepository.saveAll(List.of(
                ordonnance(ahmed, chaabane, aujourdhui.minusDays(20), aujourdhui.plusDays(70), true,
                        "Contrôle du poids quotidien",
                        new LigneOrdonnance("Furosémide 40 mg", "1 comprimé le matin", 90),
                        new LigneOrdonnance("Bisoprolol 5 mg", "1 comprimé par jour", 90)),
                ordonnance(ahmed, chaabane, aujourdhui.minusDays(200), aujourdhui.minusDays(110), false, null,
                        new LigneOrdonnance("Ramipril 5 mg", "1 comprimé le soir", 90)),
                ordonnance(leila, haddad, aujourdhui.minusDays(10), aujourdhui.plusDays(80), true,
                        "Mesurer la tension matin et soir",
                        new LigneOrdonnance("Amlodipine 5 mg", "1 comprimé par jour", 90)),
                ordonnance(karim, kallel, aujourdhui.minusDays(30), aujourdhui.plusDays(60), true, null,
                        new LigneOrdonnance("Tiotropium 18 µg", "1 inhalation par jour", 90),
                        new LigneOrdonnance("Salbutamol 100 µg", "2 bouffées si gêne respiratoire", 90)),
                ordonnance(karim, kallel, aujourdhui.minusDays(150), aujourdhui.minusDays(143), false,
                        "Exacerbation", new LigneOrdonnance("Prednisolone 20 mg", "2 comprimés le matin", 7)),
                ordonnance(sonia, haddad, aujourdhui.minusDays(15), aujourdhui.plusDays(75), true,
                        "HbA1c dans 3 mois",
                        new LigneOrdonnance("Metformine 850 mg", "1 comprimé matin et soir", 90)),
                ordonnance(hedi, haddad, aujourdhui.minusDays(60), aujourdhui.minusDays(30), false, null,
                        new LigneOrdonnance("Paracétamol 1 g", "1 comprimé jusqu'à 3 fois par jour", 30),
                        new LigneOrdonnance("Vitamine D 100 000 UI", "1 ampoule", 1))
        ));

        // ---------- Rendez-vous existants ----------
        List<RendezVous> rendezVous = new ArrayList<>();
        rendezVous.add(rdv(ahmed, creneaux.get(idCreneau(chaabane, jourDeConsultation(aujourdhui, 2), LocalTime.of(9, 0))),
                TypeConsultation.PRESENTIEL, StatutRendezVous.CONFIRME, "Contrôle trimestriel", maintenant));
        rendezVous.add(rdv(sonia, creneaux.get(idCreneau(haddad, jourDeConsultation(aujourdhui, 3), LocalTime.of(14, 30))),
                TypeConsultation.TELECONSULTATION, StatutRendezVous.CONFIRME, "Suivi du diabète", maintenant));
        rendezVous.add(rdv(karim, creneaux.get(idCreneau(kallel, jourDeConsultation(aujourdhui, 4), LocalTime.of(10, 0))),
                TypeConsultation.PRESENTIEL, StatutRendezVous.CONFIRME, "Suivi BPCO", maintenant));
        // rendez-vous passé, sans créneau (antérieur à la période générée)
        RendezVous passe = rdv(hedi, null, TypeConsultation.PRESENTIEL, StatutRendezVous.TERMINE,
                "Bilan après chute", maintenant);
        passe.setMedecin(haddad);
        passe.setDebut(aujourdhui.minusDays(10).atTime(10, 0));
        passe.setFin(aujourdhui.minusDays(10).atTime(10, 30));
        rendezVous.add(passe);
        rendezVousRepository.saveAll(rendezVous);

        log.info("Données de test chargées : {} médecins, {} patients, {} créneaux, {} ordonnances, {} rendez-vous",
                medecins.size(), patientRepository.count(), creneaux.size(), ordonnanceRepository.count(),
                rendezVous.size());
    }

    /**
     * Créneaux d'un médecin pour un jour (uniquement ceux qui ne sont pas encore passés) :
     * <ul>
     *   <li>du lundi au samedi, 09:00–12:00 et 14:00–17:00 : PRESENTIEL à l'heure pile,
     *       TELECONSULTATION à la demi-heure (PRESENTIEL si le médecin ne fait pas de téléconsultation) ;</li>
     *   <li>tous les jours, garde URGENCE 24 h/24, décalée d'un quart d'heure (00:15, 00:45…) pour que
     *       l'identifiant CRN-aaaaMMjj-NNN-HHmm reste unique sans changer son format.</li>
     * </ul>
     */
    static List<Creneau> genererCreneaux(Medecin medecin, LocalDate jour, LocalDateTime apres) {
        List<Creneau> resultat = new ArrayList<>();
        if (jour.getDayOfWeek() != DayOfWeek.SUNDAY) {
            for (LocalTime[] plage : new LocalTime[][]{{LocalTime.of(9, 0), LocalTime.of(12, 0)},
                    {LocalTime.of(14, 0), LocalTime.of(17, 0)}}) {
                for (LocalTime h = plage[0]; h.isBefore(plage[1]); h = h.plusMinutes(DUREE_CRENEAU_MINUTES)) {
                    TypeConsultation type = h.getMinute() == 30 && medecin.isTeleconsultation()
                            ? TypeConsultation.TELECONSULTATION : TypeConsultation.PRESENTIEL;
                    ajouter(resultat, medecin, jour, h, type, apres);
                }
            }
        }
        for (LocalTime h = LocalTime.of(0, 15); ; h = h.plusMinutes(DUREE_CRENEAU_MINUTES)) {
            ajouter(resultat, medecin, jour, h, TypeConsultation.URGENCE, apres);
            if (h.equals(LocalTime.of(23, 45))) {
                break;
            }
        }
        return resultat;
    }

    private static void ajouter(List<Creneau> resultat, Medecin medecin, LocalDate jour, LocalTime heure,
                                TypeConsultation type, LocalDateTime apres) {
        LocalDateTime debut = jour.atTime(heure);
        if (debut.isAfter(apres)) {
            resultat.add(new Creneau(idCreneau(medecin, jour, heure), medecin, debut,
                    debut.plusMinutes(DUREE_CRENEAU_MINUTES), type));
        }
    }

    static String idCreneau(Medecin medecin, LocalDate jour, LocalTime heure) {
        return "CRN-" + jour.format(FORMAT_JOUR) + "-" + medecin.numero() + "-" + heure.format(FORMAT_HEURE);
    }

    /** Premier jour de consultation (hors dimanche) au moins {@code decalage} jours après aujourd'hui. */
    static LocalDate jourDeConsultation(LocalDate aujourdhui, int decalage) {
        LocalDate jour = aujourdhui.plusDays(decalage);
        return jour.getDayOfWeek() == DayOfWeek.SUNDAY ? jour.plusDays(1) : jour;
    }

    private Ordonnance ordonnance(Patient patient, Medecin medecin, LocalDate emission, LocalDate finValidite,
                                  boolean renouvelable, String remarques, LigneOrdonnance... lignes) {
        Ordonnance o = new Ordonnance();
        o.setId(identifiants.ordonnance(emission));
        o.setPatient(patient);
        o.setMedecin(medecin);
        o.setDateEmission(emission);
        o.setDateFinValidite(finValidite);
        o.setRenouvelable(renouvelable);
        o.setRemarques(remarques);
        o.setLignes(new ArrayList<>(List.of(lignes)));
        return o;
    }

    private RendezVous rdv(Patient patient, Creneau creneau, TypeConsultation type, StatutRendezVous statut,
                           String motif, LocalDateTime maintenant) {
        RendezVous r = new RendezVous();
        r.setId(identifiants.rendezVous());
        r.setPatient(patient);
        r.setType(type);
        r.setStatut(statut);
        r.setMotif(motif);
        r.setDateCreation(maintenant);
        if (creneau != null) {
            creneau.setDisponible(false);
            r.setCreneau(creneau);
            r.setMedecin(creneau.getMedecin());
            r.setDebut(creneau.getDebut());
            r.setFin(creneau.getFin());
        }
        return r;
    }
}
