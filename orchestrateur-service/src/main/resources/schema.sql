-- Séquence de numérotation des workflows (WF-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS workflow_seq START WITH 1 INCREMENT BY 1;
