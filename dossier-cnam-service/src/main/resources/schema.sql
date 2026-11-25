-- Séquence de numérotation des feuilles de soins (FS-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS feuille_soins_numero_seq START WITH 1 INCREMENT BY 1;

-- Migration (base PostgreSQL existante, profil docker) : Hibernate a créé une contrainte CHECK sur les valeurs
-- de l'enum StatutFeuille, et ddl-auto=update ne la met pas à jour. Sans cela, ANNULEE serait refusée.
-- Idempotent ; sur une base neuve, la table n'existe pas encore et Hibernate la crée avec toutes les valeurs.
ALTER TABLE IF EXISTS feuille_de_soins DROP CONSTRAINT IF EXISTS feuille_de_soins_statut_check;
