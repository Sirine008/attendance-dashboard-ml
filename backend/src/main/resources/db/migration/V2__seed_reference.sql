-- Données de référence : terminaux, jours fériés tunisiens 2024-2025, périodes spéciales, paramètres.

INSERT INTO terminal (code, label, is_building_boundary) VALUES
    ('TB',    'Tourniquet bâtiment B (accès principal)', TRUE),
    ('TA',    'Tourniquet bâtiment A',                   TRUE),
    ('SS1 A', 'Sous-sol 1 — zone A (interne)',           FALSE),
    ('SS1 B', 'Sous-sol 1 — zone B (interne)',           FALSE),
    ('SS2 A', 'Sous-sol 2 — zone A (interne)',           FALSE),
    ('SS2 B', 'Sous-sol 2 — zone B (interne)',           FALSE);

-- Fériés : fixes + religieux (dates à vérifier, cf. DECISIONS.md). Le 14 janvier n'est plus férié
-- depuis 2021 (Fête de la Révolution déplacée au 17 décembre) — confirmé par les données (14/01/2025 travaillé).
INSERT INTO public_holiday (holiday_date, label, is_religious, to_verify) VALUES
    ('2024-01-01', 'Jour de l''An', FALSE, FALSE),
    ('2024-03-20', 'Fête de l''Indépendance', FALSE, FALSE),
    ('2024-04-09', 'Journée des Martyrs', FALSE, FALSE),
    ('2024-04-10', 'Aïd el-Fitr', TRUE, TRUE),
    ('2024-04-11', 'Aïd el-Fitr (2e jour)', TRUE, TRUE),
    ('2024-05-01', 'Fête du Travail', FALSE, FALSE),
    ('2024-06-16', 'Aïd el-Adha', TRUE, TRUE),
    ('2024-06-17', 'Aïd el-Adha (2e jour)', TRUE, TRUE),
    ('2024-07-07', 'Ras El Am El Hejri', TRUE, TRUE),
    ('2024-07-25', 'Fête de la République', FALSE, FALSE),
    ('2024-08-13', 'Fête de la Femme', FALSE, FALSE),
    ('2024-09-15', 'Mouled', TRUE, TRUE),
    ('2024-10-15', 'Fête de l''Évacuation', FALSE, FALSE),
    ('2024-12-17', 'Fête de la Révolution', FALSE, FALSE),
    ('2025-01-01', 'Jour de l''An', FALSE, FALSE),
    ('2025-03-20', 'Fête de l''Indépendance', FALSE, FALSE),
    ('2025-03-30', 'Aïd el-Fitr', TRUE, TRUE),
    ('2025-03-31', 'Aïd el-Fitr (2e jour)', TRUE, TRUE),
    ('2025-04-01', 'Aïd el-Fitr (3e jour)', TRUE, TRUE),
    ('2025-04-09', 'Journée des Martyrs', FALSE, FALSE),
    ('2025-05-01', 'Fête du Travail', FALSE, FALSE),
    ('2025-06-06', 'Aïd el-Adha', TRUE, TRUE),
    ('2025-06-07', 'Aïd el-Adha (2e jour)', TRUE, TRUE),
    ('2025-06-26', 'Ras El Am El Hejri', TRUE, TRUE),
    ('2025-07-25', 'Fête de la République', FALSE, FALSE),
    ('2025-08-13', 'Fête de la Femme', FALSE, FALSE),
    ('2025-09-04', 'Mouled', TRUE, TRUE),
    ('2025-10-15', 'Fête de l''Évacuation', FALSE, FALSE),
    ('2025-12-17', 'Fête de la Révolution', FALSE, FALSE);

-- Périodes spéciales. Pas d'horaire imposé par défaut : l'analyse des données (docs/data_profile.md §7)
-- ne montre pas de décalage des arrivées ; RH peut renseigner ref_start_time pour l'imposer.
INSERT INTO special_period (kind, label, start_date, end_date, ref_start_time, ref_end_time) VALUES
    ('RAMADAN',       'Ramadan 1445',            '2024-03-11', '2024-04-09', NULL, NULL),
    ('SEANCE_UNIQUE', 'Séance unique été 2024',  '2024-07-01', '2024-08-31', NULL, NULL),
    ('RAMADAN',       'Ramadan 1446',            '2025-03-01', '2025-03-29', NULL, NULL),
    ('SEANCE_UNIQUE', 'Séance unique été 2025',  '2025-07-01', '2025-08-31', NULL, NULL);

INSERT INTO app_settings (setting_key, setting_value, description) VALUES
    ('late_tolerance_minutes',     '10',  'Tolérance de retard par défaut (minutes)'),
    ('duplicate_window_seconds',   '120', 'Deux pointages même sens / même porte à moins de N secondes = doublon'),
    ('long_absence_min_days',      '3',   'Nb de jours ouvrés consécutifs à partir duquel une absence est « longue »'),
    ('schedule_learning_days',     '60',  'Fenêtre (jours) pour déduire l''horaire de référence par défaut'),
    ('schedule_rounding_minutes',  '15',  'Arrondi de l''horaire de référence déduit'),
    ('prediction_min_history_days','30',  'Historique minimal (jours ouvrés) pour afficher une prédiction');
