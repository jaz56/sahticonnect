-- Séquence de numérotation des feuilles de soins (FS-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS feuille_soins_numero_seq START WITH 1 INCREMENT BY 1;
