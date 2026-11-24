# dossier-cnam-service (SOAP, port 8081)

Service SOAP du dossier CNAM : droits de l'assuré, feuilles de soins certifiées, historique des soins
et comparaison de garanties.

## Rôle

L'orchestrateur appelle ce service pour vérifier les droits avant une intervention, puis pour émettre la
feuille de soins. Le dashboard l'appelle pour afficher la couverture et l'historique du patient.

### Opérations

Namespace `http://sahticonnect.tn/cnam`, adresse `http://localhost:8081/ws`.

| Opération | Rôle |
|---|---|
| `verifierDroitsCNAM` | Assuré, couverture (couvert, taux, motif) et montants estimés d'un acte |
| `emettreFeuilleDeSoins` | Feuille `FS-AAAA-NNNNNN` : CERTIFIEE si le patient est couvert, sinon EMISE à taux 0 (à régulariser) ; ajoutée à l'historique |
| `consulterHistorique` | Historique du patient, filtre facultatif `dateDebut` / `dateFin`, du plus récent au plus ancien |
| `comparerGaranties` | Compare deux prises en charge et recommande la moins chère pour le patient. Si la vignette est expirée aujourd'hui, le calcul est conservé et la recommandation commence par « Simulation (vignette expirée) : » |

### Règles métier

Elles sont implémentées dans `TarificationService` : BigDecimal, 3 décimales, arrondi HALF_UP.
- **Montant d'un acte** = valeur de la lettre-clé × coefficient (C = 45, CS = 60, V = 55, K = 3, B = 0,3, Z = 2 DT).
- **Taux** : APCI → 100 % ; sinon filière PUBLIQUE → 80 % ; sinon 50 %.
- **Vignette expirée** à la date de l'acte : non couvert, taux 0.
- **Numérotation des feuilles** : `FS-<année des soins>-<valeur suivante de la séquence feuille_soins_numero_seq>`,
  avec une contrainte d'unicité sur la colonne `numero`.

### Erreurs (SOAP Fault `Client`)

Chaque opération déclare une fault dans le WSDL, de type `ErreurCnam`. Le `<detail>` contient un code stable :

| Code | Cas |
|---|---|
| `PATIENT_INCONNU` | Identifiant patient inconnu |
| `VIGNETTE_INVALIDE` | La vignette ne correspond pas au patient |
| `ACTE_INVALIDE` | Coefficient ≤ 0 |

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

Une requête non conforme au XSD est rejetée avant l'endpoint par la validation : fault `Client` de Spring WS,
sans `<detail>` métier.

### Données de test

| Patient | Vignette | Filière | Particularité |
|---|---|---|---|
| PAT-0001 Ahmed Ben Salah | VG-2026-000001 | PUBLIQUE | APCI (100 %) |
| PAT-0002 Leila Trabelsi | VG-2026-000002 | PRIVEE | Vignette expirée le 30/06/2026 |
| PAT-0003 Karim Jaziri | VG-2026-000003 | PUBLIQUE | 80 % |
| PAT-0004 Sonia Mansour | VG-2026-000004 | REMBOURSEMENT | 50 % |
| PAT-0005 Hedi Gharbi | VG-2026-000005 | PUBLIQUE | 80 % |

Les vignettes valides expirent le 31/12/2027. Chaque patient a 2 ou 3 entrées d'historique.

## Contrat

- Contrat : [`docs/wsdl/dossier-cnam.xsd`](../docs/wsdl/dossier-cnam.xsd), copié à l'identique dans
  `src/main/resources/xsd/`. Le `jaxb2-maven-plugin` en génère les classes JAXB (`tn.sahticonnect.cnam.ws`).
- WSDL généré au démarrage par `DefaultWsdl11Definition` : http://localhost:8081/ws/dossier-cnam.wsdl.
- Via l'API Gateway : http://localhost:8080/api/cnam/ws/dossier-cnam.wsdl. L'adresse du service annoncée dans le
  WSDL est celle par laquelle il a été lu (`transformWsdlLocations` + `server.forward-headers-strategy=framework`,
  qui lit les en-têtes `X-Forwarded-*` de la gateway) : `http://localhost:8080/api/cnam/ws` via la gateway,
  `http://localhost:8081/ws` en direct. Un test couvre chaque cas (`WsdlAdresseIntegrationTest`).
  Une copie exportée se trouve dans [`docs/wsdl/dossier-cnam.wsdl`](../docs/wsdl/dossier-cnam.wsdl).

## Lancer

```powershell
cd dossier-cnam-service
.\mvnw.cmd spring-boot:run
```

- **Profil par défaut** : H2 en mémoire. Console : http://localhost:8081/h2-console
  (`jdbc:h2:mem:cnam`, utilisateur `sa`, sans mot de passe).
- **Profil `docker`** : PostgreSQL, configuré par `DB_URL`, `DB_USER` et `DB_PASSWORD`
  (par défaut `jdbc:postgresql://postgres:5432/cnam`, `cnam`, `cnam`).

```powershell
docker build -t sahticonnect/dossier-cnam-service .
docker run -p 8081:8081 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/cnam sahticonnect/dossier-cnam-service
```

## Tester

```powershell
# Tests unitaires (TarificationService : taux, vignette expirée, arrondis), numérotation par séquence,
# tests d'intégration MockWebServiceClient (4 opérations, faults et leur <detail>)
.\mvnw.cmd verify

# Service démarré : envoie les requêtes de docs/tests/soap et affiche les réponses (dont une fault)
..\docs\tests\soap\tester-soap.ps1
```

Les requêtes d'exemple (`docs/tests/soap/*.xml`) peuvent aussi être importées dans SoapUI ou Postman à partir du WSDL.

## Choix techniques

- **Contract-first** : le XSD est écrit d'abord, et le Java ainsi que le WSDL en découlent. Le contrat ne peut donc
  pas dériver du code.
- **Organisation en couches** :
  - `DossierCnamEndpoint` (`@Endpoint`, un `@PayloadRoot` par opération) ;
  - `DossierCnamMapper`, qui convertit entre JAXB et le modèle métier ;
  - `DossierCnamService` et `TarificationService` (sans état, testé unitairement) ;
  - les repositories JPA.
- **Faults** : `ErreurCnamExceptionResolver` remplace `@SoapFault`, pour ajouter un `<detail>` typé dans le WSDL,
  avec un code que les clients peuvent tester.
- **Numérotation par séquence de base**, et non par `count + 1` : jamais deux fois le même numéro, même en cas
  d'appels simultanés. Le SQL `next value` / `nextval` est fourni par le dialecte Hibernate (H2 ou PostgreSQL).
- **Montants en `BigDecimal`** à 3 décimales (millimes), pour éviter les erreurs d'arrondi des `double`.
