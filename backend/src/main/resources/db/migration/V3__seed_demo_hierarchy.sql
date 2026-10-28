-- SEED FICTIF (démonstration) : le fichier source ne contient ni hiérarchie ni ancienneté.
-- Équipes, rattachements et dates d'embauche ci-dessous sont INVENTÉS, sauf MMMMMM et OOOOOO
-- dont la date d'embauche (06/08/2024) figure dans le fichier de pointage.

INSERT INTO team (name) VALUES
    ('Équipe Études A (fictive)'),
    ('Équipe Études B (fictive)');

INSERT INTO employee (code, display_name, team_id, hire_date, hire_date_fictive) VALUES
    ('XXXXXX', 'Collaborateur X', (SELECT id FROM team WHERE name = 'Équipe Études A (fictive)'), '2016-09-01', TRUE),
    ('YYYYYY', 'Collaborateur Y', (SELECT id FROM team WHERE name = 'Équipe Études A (fictive)'), '2019-03-04', TRUE),
    ('ZZZZZZ', 'Collaborateur Z', (SELECT id FROM team WHERE name = 'Équipe Études A (fictive)'), '2014-01-06', TRUE),
    ('TTTTTT', 'Collaborateur T', (SELECT id FROM team WHERE name = 'Équipe Études A (fictive)'), '2021-10-01', TRUE),
    ('WWWWWW', 'Collaborateur W', (SELECT id FROM team WHERE name = 'Équipe Études B (fictive)'), '2012-05-14', TRUE),
    ('VVVVVV', 'Collaborateur V', (SELECT id FROM team WHERE name = 'Équipe Études B (fictive)'), '2018-02-12', TRUE),
    ('MMMMMM', 'Collaborateur M', (SELECT id FROM team WHERE name = 'Équipe Études B (fictive)'), '2024-08-06', FALSE),
    ('OOOOOO', 'Collaborateur O', (SELECT id FROM team WHERE name = 'Équipe Études B (fictive)'), '2024-08-06', FALSE);
