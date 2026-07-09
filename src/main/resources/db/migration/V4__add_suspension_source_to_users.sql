-- Sprint 7.8 : distingue l'origine d'une suspension de compte
-- (détection d'abus automatique vs action admin explicite), jusqu'ici
-- indiscernable autrement qu'en comparant le texte libre de
-- suspended_reason.
ALTER TABLE users
    ADD COLUMN suspension_source VARCHAR(20);
