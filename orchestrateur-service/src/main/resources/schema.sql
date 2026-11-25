-- Séquence de numérotation des workflows (WF-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS workflow_seq START WITH 1 INCREMENT BY 1;

-- Migration (base PostgreSQL existante, profil docker) : Hibernate a créé des contraintes CHECK sur les valeurs des
-- enums, et ddl-auto=update ne les met pas à jour. Sans cela, les nouvelles valeurs de la saga (COMPENSE, ANNULEE,
-- ESCALADE) seraient refusées. Idempotent ; sur une base neuve, Hibernate crée les tables avec toutes les valeurs.
ALTER TABLE IF EXISTS workflow DROP CONSTRAINT IF EXISTS workflow_etat_check;
ALTER TABLE IF EXISTS workflow DROP CONSTRAINT IF EXISTS workflow_statut_facturation_check;
ALTER TABLE IF EXISTS workflow DROP CONSTRAINT IF EXISTS workflow_etape_en_echec_check;
ALTER TABLE IF EXISTS etape_workflow DROP CONSTRAINT IF EXISTS etape_workflow_nom_check;
