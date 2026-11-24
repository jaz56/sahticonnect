-- Initialisation du conteneur PostgreSQL de SahtiConnect (database-per-service).
-- Exécuté UNE SEULE FOIS par l'image postgres, au premier démarrage, quand le volume est vide
-- (monté dans /docker-entrypoint-initdb.d). Pour le rejouer : docker compose down -v.
--
-- Un utilisateur et une base par service. Chaque utilisateur n'a accès qu'à SA base : un service ne peut
-- ni lire ni modifier les données d'un autre, il doit passer par son API (SOAP, REST, GraphQL, gRPC).
-- Les mots de passe viennent des variables d'environnement du conteneur (fichier .env), lues par \getenv (psql ≥ 15).

\getenv mdp_cnam CNAM_DB_PASSWORD
\getenv mdp_portail PORTAIL_DB_PASSWORD
\getenv mdp_dashboard DASHBOARD_DB_PASSWORD
\getenv mdp_telemetrie TELEMETRIE_DB_PASSWORD
\getenv mdp_orchestrateur ORCHESTRATEUR_DB_PASSWORD

-- ---------- dossier-cnam-service (SOAP) ----------
CREATE USER cnam WITH PASSWORD :'mdp_cnam';
CREATE DATABASE cnam OWNER cnam ENCODING 'UTF8';
REVOKE ALL ON DATABASE cnam FROM PUBLIC;

-- ---------- portail-patient-service (REST) ----------
CREATE USER portail WITH PASSWORD :'mdp_portail';
CREATE DATABASE portail OWNER portail ENCODING 'UTF8';
REVOKE ALL ON DATABASE portail FROM PUBLIC;

-- ---------- dashboard-medical-service (GraphQL) ----------
CREATE USER dashboard WITH PASSWORD :'mdp_dashboard';
CREATE DATABASE dashboard OWNER dashboard ENCODING 'UTF8';
REVOKE ALL ON DATABASE dashboard FROM PUBLIC;

-- ---------- telemetrie-service (gRPC) ----------
CREATE USER telemetrie WITH PASSWORD :'mdp_telemetrie';
CREATE DATABASE telemetrie OWNER telemetrie ENCODING 'UTF8';
REVOKE ALL ON DATABASE telemetrie FROM PUBLIC;

-- ---------- orchestrateur-service ----------
CREATE USER orchestrateur WITH PASSWORD :'mdp_orchestrateur';
CREATE DATABASE orchestrateur OWNER orchestrateur ENCODING 'UTF8';
REVOKE ALL ON DATABASE orchestrateur FROM PUBLIC;
