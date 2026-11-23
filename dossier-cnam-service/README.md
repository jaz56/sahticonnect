# dossier-cnam-service (SOAP, port 8081)

Service SOAP de SahtiConnect pour le dossier CNAM : vérification des droits, émission de
feuilles de soins certifiées, historique des soins et comparaison de garanties.

Approche **contract-first** : le contrat `src/main/resources/xsd/dossier-cnam.xsd` (copie de
`docs/wsdl/dossier-cnam.xsd`) est écrit en premier. Les classes Java (JAXB) et le WSDL en sont
générés.

## Opérations

Namespace : `http://sahticonnect.tn/cnam`. Adresse : `http://localhost:8081/ws`.

| Opération | Rôle |
|---|---|
| `verifierDroitsCNAM` | Assuré, couverture (couvert, taux, motif) et montants estimés d'un acte |
| `emettreFeuilleDeSoins` | Feuille `FS-AAAA-NNNNNN` : CERTIFIEE si le patient est couvert, sinon EMISE à taux 0 ; ajoutée à l'historique |
| `consulterHistorique` | Historique du patient, filtre facultatif `dateDebut` / `dateFin`, du plus récent au plus ancien |
| `comparerGaranties` | Compare deux prises en charge et recommande la moins chère pour le patient |

Règles de tarification (`TarificationService`, BigDecimal, 3 décimales, arrondi HALF_UP) :
- montant d'un acte = valeur de la lettre-clé × coefficient (C = 45, CS = 60, V = 55, K = 3, B = 0,3, Z = 2 DT) ;
- taux : APCI → 100 % ; sinon filière PUBLIQUE → 80 % ; sinon 50 % ;
- vignette expirée à la date de l'acte → non couvert, taux 0.

### Erreurs (SOAP Fault `Client`)

Chaque opération déclare une fault dans le WSDL (`verifierDroitsCNAMFault`, `emettreFeuilleDeSoinsFault`,
`consulterHistoriqueFault`, `comparerGarantiesFault`, de type `ErreurCnam`). `ErreurCnamExceptionResolver`
place cet élément dans le `<detail>`, avec un code stable que les clients peuvent tester :

| Code | Exception | Cas |
|---|---|---|
| `PATIENT_INCONNU` | `PatientInconnuException` | identifiant patient inconnu |
| `VIGNETTE_INVALIDE` | `VignetteInvalideException` | la vignette ne correspond pas au patient |
| `ACTE_INVALIDE` | `ActeInvalideException` | coefficient ≤ 0 |

```xml
<SOAP-ENV:Fault>
  <faultcode>SOAP-ENV:Client</faultcode>
  <faultstring xml:lang="fr">La vignette VG-2026-000003 ne correspond pas au patient PAT-0001</faultstring>
  <detail>
    <ns2:verifierDroitsCNAMFault xmlns:ns2="http://sahticonnect.tn/cnam">
      <ns2:code>VIGNETTE_INVALIDE</ns2:code>
      <ns2:message>La vignette VG-2026-000003 ne correspond pas au patient PAT-0001</ns2:message>
    </ns2:verifierDroitsCNAMFault>
  </detail>
</SOAP-ENV:Fault>
```

Une requête non conforme au XSD est rejetée avant l'endpoint par `PayloadValidatingInterceptor`
(fault `Client` de Spring WS, sans `<detail>` métier).

## WSDL

- Généré au démarrage : http://localhost:8081/ws/dossier-cnam.wsdl
- Copie exportée : [`docs/wsdl/dossier-cnam.wsdl`](../docs/wsdl/dossier-cnam.wsdl)

## Architecture

```
endpoint/DossierCnamEndpoint      ← @Endpoint, 4 @PayloadRoot
mapper/DossierCnamMapper          ← classes JAXB (tn.sahticonnect.cnam.ws) ↔ modèle métier
service/DossierCnamService        ← logique des 4 opérations
service/TarificationService       ← règles de calcul (sans état, testé unitairement)
repository/*                      ← Spring Data JPA
entity/*                          ← Assure, FeuilleDeSoins (+ LigneActe), EntreeHistorique (+ actes)
init/DonneesInitiales             ← 5 patients de test et leur historique
config/WebServiceConfig           ← MessageDispatcherServlet /ws/*, WSDL, validation XSD
config/ErreurCnamExceptionResolver ← exceptions métier → SOAP Fault + <detail> ErreurCnam
```

## Lancer

Prérequis : un JDK 21 ou plus récent, avec `JAVA_HOME` qui pointe dessus.

```powershell
cd dossier-cnam-service
.\mvnw.cmd spring-boot:run
```

Profil par défaut : H2 en mémoire. Console : http://localhost:8081/h2-console
(JDBC URL `jdbc:h2:mem:cnam`, utilisateur `sa`, sans mot de passe).

Profil `docker` : PostgreSQL, configuré par les variables `DB_URL`, `DB_USER` et `DB_PASSWORD`
(valeurs par défaut : `jdbc:postgresql://postgres-cnam:5432/cnam`, `cnam`, `cnam`).

```powershell
docker build -t sahticonnect/dossier-cnam-service .
docker run -p 8081:8081 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/cnam sahticonnect/dossier-cnam-service
```

## Tester

```powershell
# Tests unitaires (TarificationService) et d'intégration (MockWebServiceClient : 4 opérations + faults)
.\mvnw.cmd verify

# Service démarré : envoie les requêtes de docs/tests/soap et affiche les réponses
..\docs\tests\soap\tester-soap.ps1
```

Les requêtes d'exemple (`docs/tests/soap/*.xml`) peuvent aussi être importées dans SoapUI
ou Postman à partir du WSDL.

## Données de test

| Patient | Vignette | Filière | Particularité |
|---|---|---|---|
| PAT-0001 Ahmed Ben Salah | VG-2026-000001 | PUBLIQUE | APCI (100 %) |
| PAT-0002 Leila Trabelsi | VG-2026-000002 | PRIVEE | Vignette expirée le 30/06/2026 |
| PAT-0003 Karim Jaziri | VG-2026-000003 | PUBLIQUE | 80 % |
| PAT-0004 Sonia Mansour | VG-2026-000004 | REMBOURSEMENT | 50 % |
| PAT-0005 Hedi Gharbi | VG-2026-000005 | PUBLIQUE | 80 % |

Les vignettes valides expirent le 31/12/2027.
