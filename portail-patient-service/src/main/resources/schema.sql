-- Séquences de numérotation (RDV-AAAA-NNNNNN, AMB-AAAA-NNNNNN, ORD-AAAA-NNNNNN).
-- Syntaxe commune à H2 et PostgreSQL ; exécuté au démarrage avant la création du schéma JPA.
CREATE SEQUENCE IF NOT EXISTS rendez_vous_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS mission_ambulance_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ordonnance_seq START WITH 1 INCREMENT BY 1;
