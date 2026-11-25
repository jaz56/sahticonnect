package tn.sahticonnect.cnam.mapper;

import tn.sahticonnect.cnam.ws.AnnulerFeuilleDeSoinsResponse;
import org.springframework.stereotype.Component;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.EntreeHistorique;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;
import tn.sahticonnect.cnam.entity.Filiere;
import tn.sahticonnect.cnam.entity.LettreCle;
import tn.sahticonnect.cnam.entity.LigneActe;
import tn.sahticonnect.cnam.service.model.ComparaisonGaranties;
import tn.sahticonnect.cnam.service.model.ConsultationHistorique;
import tn.sahticonnect.cnam.service.model.DemandeFeuilleDeSoins;
import tn.sahticonnect.cnam.service.model.OffreSoins;
import tn.sahticonnect.cnam.service.model.ResultatCouverture;
import tn.sahticonnect.cnam.service.model.ResultatGarantie;
import tn.sahticonnect.cnam.service.model.VerificationDroits;
import tn.sahticonnect.cnam.ws.ComparerGarantiesResponse;
import tn.sahticonnect.cnam.ws.ConsulterHistoriqueResponse;
import tn.sahticonnect.cnam.ws.Couverture;
import tn.sahticonnect.cnam.ws.EmettreFeuilleDeSoinsRequest;
import tn.sahticonnect.cnam.ws.EmettreFeuilleDeSoinsResponse;
import tn.sahticonnect.cnam.ws.GarantieCalculee;
import tn.sahticonnect.cnam.ws.PriseEnCharge;
import tn.sahticonnect.cnam.ws.VerifierDroitsCNAMResponse;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.util.List;

/**
 * Conversion entre le modèle métier (entités JPA, records) et les classes JAXB générées
 * depuis le XSD (package {@code tn.sahticonnect.cnam.ws}).
 * Les classes JAXB qui portent le même nom qu'une entité (Assure, ActeCote, FeuilleDeSoins,
 * LigneActe, EntreeHistorique) sont écrites avec leur nom complet.
 */
@Component
public class DossierCnamMapper {

    private final DatatypeFactory datatypeFactory;

    public DossierCnamMapper() {
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("DatatypeFactory indisponible", e);
        }
    }

    // ===================== Requêtes JAXB → modèle métier =====================

    public ActeCote versActe(tn.sahticonnect.cnam.ws.ActeCote acte) {
        return new ActeCote(LettreCle.valueOf(acte.getLettreCle().value()), acte.getCoefficient(), acte.getLibelle());
    }

    public List<ActeCote> versActes(List<tn.sahticonnect.cnam.ws.ActeCote> actes) {
        return actes.stream().map(this::versActe).toList();
    }

    public DemandeFeuilleDeSoins versDemande(EmettreFeuilleDeSoinsRequest requete) {
        return new DemandeFeuilleDeSoins(
                requete.getIdentifiantPatient(),
                requete.getCodeVignetteCNAM(),
                versLocalDate(requete.getDateSoins()),
                requete.getEtablissement(),
                requete.getMedecin(),
                versActes(requete.getActes()),
                requete.getReferenceIntervention());
    }

    public OffreSoins versOffre(PriseEnCharge priseEnCharge) {
        return new OffreSoins(
                priseEnCharge.getLibelle(),
                Filiere.valueOf(priseEnCharge.getFiliere().value()),
                versActes(priseEnCharge.getActes()));
    }

    // ===================== Modèle métier → réponses JAXB =====================

    public VerifierDroitsCNAMResponse versReponse(VerificationDroits verification) {
        VerifierDroitsCNAMResponse reponse = new VerifierDroitsCNAMResponse();
        reponse.setAssure(versAssure(verification.assure()));
        reponse.setCouverture(versCouverture(verification.couverture()));
        reponse.setMontantEstime(verification.montants().total());
        reponse.setPartCNAMEstimee(verification.montants().partCNAM());
        reponse.setPartPatientEstimee(verification.montants().partPatient());
        return reponse;
    }

    public EmettreFeuilleDeSoinsResponse versReponse(FeuilleDeSoins feuille) {
        EmettreFeuilleDeSoinsResponse reponse = new EmettreFeuilleDeSoinsResponse();
        reponse.setFeuilleDeSoins(versFeuilleXml(feuille));
        return reponse;
    }

    public AnnulerFeuilleDeSoinsResponse versReponseAnnulation(FeuilleDeSoins feuille) {
        AnnulerFeuilleDeSoinsResponse reponse = new AnnulerFeuilleDeSoinsResponse();
        reponse.setFeuilleDeSoins(versFeuilleXml(feuille));
        return reponse;
    }

    public tn.sahticonnect.cnam.ws.FeuilleDeSoins versFeuilleXml(FeuilleDeSoins feuille) {
        tn.sahticonnect.cnam.ws.FeuilleDeSoins xml = new tn.sahticonnect.cnam.ws.FeuilleDeSoins();
        xml.setNumero(feuille.getNumero());
        xml.setIdentifiantPatient(feuille.getAssure().getIdentifiantPatient());
        xml.setDateSoins(versXml(feuille.getDateSoins()));
        xml.setEtablissement(feuille.getEtablissement());
        xml.setMedecin(feuille.getMedecin());
        for (LigneActe ligne : feuille.getLignes()) {
            tn.sahticonnect.cnam.ws.LigneActe ligneXml = new tn.sahticonnect.cnam.ws.LigneActe();
            ligneXml.setActe(versActeXml(ligne.getActe()));
            ligneXml.setMontant(ligne.getMontant());
            xml.getLignes().add(ligneXml);
        }
        xml.setMontantTotal(feuille.getMontantTotal());
        xml.setTauxApplique(feuille.getTauxApplique());
        xml.setPartCNAM(feuille.getPartCNAM());
        xml.setPartPatient(feuille.getPartPatient());
        xml.setStatut(tn.sahticonnect.cnam.ws.StatutFeuille.fromValue(feuille.getStatut().name()));
        xml.setCodeCertification(feuille.getCodeCertification());
        xml.setReferenceIntervention(feuille.getReferenceIntervention());
        return xml;
    }

    public ConsulterHistoriqueResponse versReponse(ConsultationHistorique consultation) {
        ConsulterHistoriqueResponse reponse = new ConsulterHistoriqueResponse();
        reponse.setAssure(versAssure(consultation.assure()));
        for (EntreeHistorique entree : consultation.entrees()) {
            reponse.getEntrees().add(versEntree(entree));
        }
        return reponse;
    }

    public ComparerGarantiesResponse versReponse(ComparaisonGaranties comparaison) {
        ComparerGarantiesResponse reponse = new ComparerGarantiesResponse();
        reponse.setResultatA(versGarantie(comparaison.resultatA()));
        reponse.setResultatB(versGarantie(comparaison.resultatB()));
        reponse.setDifferencePartPatient(comparaison.differencePartPatient());
        reponse.setRecommandation(comparaison.recommandation());
        return reponse;
    }

    // ===================== Conversions élémentaires =====================

    public tn.sahticonnect.cnam.ws.Assure versAssure(Assure assure) {
        tn.sahticonnect.cnam.ws.Assure xml = new tn.sahticonnect.cnam.ws.Assure();
        xml.setIdentifiantPatient(assure.getIdentifiantPatient());
        xml.setNom(assure.getNom());
        xml.setPrenom(assure.getPrenom());
        xml.setDateNaissance(versXml(assure.getDateNaissance()));
        xml.setMatriculeCNAM(assure.getMatriculeCNAM());
        xml.setCodeVignetteCNAM(assure.getCodeVignetteCNAM());
        xml.setFiliere(tn.sahticonnect.cnam.ws.Filiere.fromValue(assure.getFiliere().name()));
        xml.setDateFinValidite(versXml(assure.getDateFinValidite()));
        xml.setApci(assure.isApci());
        xml.setCodeAPCI(assure.getCodeAPCI());
        return xml;
    }

    public Couverture versCouverture(ResultatCouverture couverture) {
        Couverture xml = new Couverture();
        xml.setCouvert(couverture.couvert());
        xml.setTauxRemboursement(couverture.taux());
        xml.setMotif(couverture.motif());
        return xml;
    }

    public tn.sahticonnect.cnam.ws.ActeCote versActeXml(ActeCote acte) {
        tn.sahticonnect.cnam.ws.ActeCote xml = new tn.sahticonnect.cnam.ws.ActeCote();
        xml.setLettreCle(tn.sahticonnect.cnam.ws.LettreCle.fromValue(acte.getLettreCle().name()));
        xml.setCoefficient(acte.getCoefficient());
        xml.setLibelle(acte.getLibelle());
        return xml;
    }

    private tn.sahticonnect.cnam.ws.EntreeHistorique versEntree(EntreeHistorique entree) {
        tn.sahticonnect.cnam.ws.EntreeHistorique xml = new tn.sahticonnect.cnam.ws.EntreeHistorique();
        xml.setDate(versXml(entree.getDate()));
        xml.setEtablissement(entree.getEtablissement());
        xml.setDiagnostic(entree.getDiagnostic());
        entree.getActes().forEach(acte -> xml.getActes().add(versActeXml(acte)));
        xml.setNumeroFeuilleSoins(entree.getNumeroFeuilleSoins());
        return xml;
    }

    private GarantieCalculee versGarantie(ResultatGarantie garantie) {
        GarantieCalculee xml = new GarantieCalculee();
        xml.setLibelle(garantie.libelle());
        xml.setMontantTotal(garantie.montants().total());
        xml.setTauxApplique(garantie.taux());
        xml.setPartCNAM(garantie.montants().partCNAM());
        xml.setPartPatient(garantie.montants().partPatient());
        return xml;
    }

    /** xs:date → LocalDate (null conservé). */
    public LocalDate versLocalDate(XMLGregorianCalendar date) {
        return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    /** LocalDate → xs:date sans fuseau horaire (ex. 2026-11-22). */
    public XMLGregorianCalendar versXml(LocalDate date) {
        if (date == null) {
            return null;
        }
        return datatypeFactory.newXMLGregorianCalendarDate(
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(), DatatypeConstants.FIELD_UNDEFINED);
    }
}
