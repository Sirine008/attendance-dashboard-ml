# Vérification manuelle de `daily_attendance` contre le fichier Excel

Méthode : pour chaque journée, les lignes brutes de `data/raw/Pointage_PFA.xls` (numéro de ligne Excel indiqué) ont été extraites, puis le résultat a été recalculé **à la main** avec les règles métier (doublon = même sens et même porte à moins de 120 s ; seules les portes TA/TB comptent pour la présence et les heures ; minutes arrondies à la minute inférieure par paire IN→OUT ; retard si 1re entrée > référence + 10 min). Le résultat est comparé à la ligne produite par le backend dans `daily_attendance` (stack Docker du 28/10/2026).

## 1. YYYYYY — mercredi 06/03/2024 : portes internes, doublon et retard

| Ligne Excel | Horodatage | Terminal | Lecture |
|---|---|---|---|
| 274 | 08:19:02 | Entrée TB | 1re entrée frontière |
| 275 | 11:34:05 | Sortie SS1 B | zone interne (ignorée pour les heures) |
| 276 | 11:53:56 | Entrée SS1 B | zone interne |
| 277 | 12:52:11 | Sortie TB | |
| 278 | 13:31:39 | Entrée TB | |
| 279 | 13:32:29 | Entrée TB | **doublon** (même sens/porte, 50 s après) → ignoré |
| 280 | 16:21:53 | Sortie SS1 B | zone interne |
| 281 | 16:37:59 | Entrée SS1 B | zone interne |
| 282 | 16:58:56 | Sortie TB | dernière sortie |

Calcul manuel : 08:19:02 → 12:52:11 = 273 min ; 13:31:39 → 16:58:56 = 207 min ; **total 480 min**. Référence 08:00 (médiane des 60 premiers jours), 08:19 > 08:10 → **retard de 19 min**.

| Champ | Attendu | `daily_attendance` |
|---|---|---|
| statut | RETARD | RETARD ✅ |
| 1re entrée / dernière sortie | 08:19:02 / 16:58:56 | 08:19:02 / 16:58:56 ✅ |
| minutes de retard | 19 | 19 ✅ |
| minutes travaillées | 480 | 480 ✅ |
| pointages retenus / frontière / doublons | 8 / 4 / 1 | 8 / 4 / 1 ✅ |
| incomplet | non | non ✅ |

## 2. XXXXXX — mardi 30/04/2024 : nombre impair de pointages

| Ligne Excel | Horodatage | Terminal |
|---|---|---|
| 245 | 09:54:10 | Entrée TB |
| 246 | 12:40:49 | Sortie TB |
| 247 | 14:29:07 | Entrée TB (aucune sortie ensuite) |

Calcul manuel : 3 pointages frontière → **journée incomplète** ; seule la paire 09:54:10 → 12:40:49 = **166 min** est comptée (pas d'heure inventée pour l'après-midi). Référence 09:45 : 09:54 ≤ 09:55 → pas de retard.

| Champ | Attendu | `daily_attendance` |
|---|---|---|
| statut | PRESENT | PRESENT ✅ |
| incomplet | oui | oui ✅ |
| minutes travaillées | 166 (partiel) | 166 ✅ |
| minutes de retard | 0 | 0 ✅ |

## 3. VVVVVV — vendredi 07/06/2024 : nombreux allers-retours

13 lignes Excel (889 à 901). La ligne 896 (Entrée SS1 B 12:14:46, 14 s après la ligne 895) est un doublon. Paires frontière : 08:00:01→08:04:34 (4 min), 08:13:12→10:45:00 (151 min), 10:49:20→12:15:30 (86 min), 13:54:13→16:48:05 (173 min) → **414 min**. 08:00:01 ≤ 08:10 → présent à l'heure.

| Champ | Attendu | `daily_attendance` |
|---|---|---|
| statut | PRESENT | PRESENT ✅ |
| minutes travaillées | 414 | 414 ✅ |
| pointages retenus / frontière / doublons | 12 / 8 / 1 | 12 / 8 / 1 ✅ |
| 1re entrée / dernière sortie | 08:00:01 / 16:48:05 | 08:00:01 / 16:48:05 ✅ |

## 4. XXXXXX — mercredi 03/01/2024 : absence ponctuelle

Aucune ligne Excel ce jour-là ni la veille (02/01, jour ouvré) ; présence le 04/01 (ligne 3 de la feuille). Série de 2 jours ouvrés sans pointage → **ABSENCE_PONCTUELLE** (et non longue). `daily_attendance` : ABSENCE_PONCTUELLE ✅.

## Contrôles globaux

- Volumes : 11 367 lignes lues, 11 358 insérées, 9 doublons exacts ignorés ; ré-import = 0 insertion (test `ImportIntegrationTest`).
- Absences : 132 jours ponctuels et 166 jours longs, **identiques** au calcul indépendant en Python de `docs/data_profile.md` §6 (test d'intégration).
- Les 16 jours ouvrés sans aucun pointage de toute l'équipe correspondent tous à un jour férié du référentiel (`data_profile.md` §5).
