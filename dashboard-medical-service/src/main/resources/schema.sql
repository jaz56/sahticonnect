-- Séquence de numérotation des interventions (INT-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS intervention_seq START WITH 1 INCREMENT BY 1;

-- Migration (base PostgreSQL existante, profil docker) : Hibernate a créé des contraintes CHECK sur les valeurs des
-- enums, et ddl-auto=update ne les met pas à jour. Sans cela, COMPENSE et ANNULEE (saga de l'orchestrateur) seraient
-- refusées. Idempotent ; sur une base neuve, la table n'existe pas encore et Hibernate la crée avec toutes les valeurs.
ALTER TABLE IF EXISTS intervention DROP CONSTRAINT IF EXISTS intervention_etat_check;
ALTER TABLE IF EXISTS intervention DROP CONSTRAINT IF EXISTS intervention_statut_facturation_check;
