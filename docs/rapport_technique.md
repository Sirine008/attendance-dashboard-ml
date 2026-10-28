# Rapport technique — Tableau de bord d'assiduité et anticipation des absences

*Attijari Bank — Département Études Informatiques. Document de synthèse réutilisable pour le rapport de stage.*

## 1. Contexte et objectifs

Le département suit la présence de ses collaborateurs à partir des pointages des portes d'accès. L'objectif est double :

1. **Descriptif** : donner aux RH et aux responsables N+1 une vision fiable des présences, retards et absences (jour, mois, année), par équipe et par personne ;
2. **Anticipation** : estimer le risque d'absence ponctuelle au prochain jour ouvré et sur la semaine suivante pour **organiser le travail** (relais, réunions, continuité de service).

Le module prédictif n'est **pas** un outil d'évaluation ni de sanction : les estimations sont des probabilités, les facteurs affichés sont *associés* à l'estimation, aucune estimation n'est affichée sous 30 jours ouvrés d'historique, l'accès est restreint par rôle et chaque consultation de fiche est tracée (loi organique n° 2004-63).

## 2. Architecture

Quatre services orchestrés par Docker Compose : PostgreSQL 16 ; backend Spring Boot 3.5 (Java 21 : import, règles métier, API REST sécurisée par JWT, Swagger, job planifié) ; microservice FastAPI (Python 3.11 : features, modèles scikit-learn / LightGBM, explications SHAP) ; frontend Angular 21 (Material, ECharts) servi par nginx. Le schéma est versionné par Flyway (3 migrations : schéma, référentiels, seed fictif de hiérarchie).

## 3. Données

Fichier `Pointage_PFA.xls` : 8 feuilles (une par collaborateur anonymisé), **11 367 pointages** du 02/01/2024 au 19/05/2025 (profil complet : `docs/data_profile.md`).

Constats principaux :
- en-tête en ligne 2, colonnes variables (`ordre` ou vide) → détection par nom ; libellé de terminal « sens + porte » avec espaces multiples ;
- 9 doublons exacts et 74 doublons proches (même sens, même porte, < 120 s) ;
- 14 % des pointages concernent des portes de sous-sol (zones internes), qui ne sont pas des sorties du bâtiment ;
- 58 journées avec un nombre impair de pointages frontière (2,7 %) ;
- 16 jours ouvrés sans aucun pointage de toute l'équipe, tous des jours fériés (ce qui confirme le 17 décembre comme Fête de la Révolution) ;
- horaires très hétérogènes (1re entrée médiane de 7h48 à 9h32) et stables pendant le Ramadan et l'été ;
- 2 456 jours-personnes ouvrés, dont 132 d'absence ponctuelle (5,4 %) et 166 d'absence longue (6,8 %), l'essentiel de ces derniers pour un seul collaborateur ;
- deux dates d'embauche réelles présentes dans le fichier (06/08/2024).

## 4. Modélisation des données

Tables principales : `employee`, `team`, `app_user` (rôles ADMIN / RH / MANAGER), `terminal` (avec `is_building_boundary`), `raw_punch` (brut, `source_file`, `import_batch_id`, drapeau de doublon, contrainte d'unicité qui rend l'import idempotent), `daily_attendance` (une ligne par collaborateur × jour ouvré, plus les jours non ouvrés travaillés), `public_holiday`, `special_period`, `work_schedule`, `leave`, `app_settings`, `import_batch` (rapport JSON), `prediction` (historisée, jamais mise à jour), `model_registry` (version, métriques, seuils, version active), `audit_log`.

## 5. Règles métier

| Règle | Implémentation |
|---|---|
| Jour ouvré | lundi–vendredi hors `public_holiday` |
| Doublon | même sens + même porte qu'un pointage retenu il y a ≤ 120 s (paramétrable) ; conservé mais ignoré |
| Présence | au moins un pointage ; sans entrée frontière → présent mais incomplet |
| Retard | 1re entrée frontière > horaire de référence + tolérance (10 min) ; minutes de retard mesurées depuis la référence |
| Horaire de référence | médiane de la 1re entrée sur les 60 premiers jours, arrondie au quart d'heure ; modifiable par RH ; une période spéciale peut imposer son propre horaire |
| Heures | somme des paires IN→OUT successives sur portes frontière ; séquence impaire ou incohérente → `incomplete`, aucune heure inventée |
| Absence | jour ouvré sans pointage ni congé déclaré ; ≥ 3 jours ouvrés consécutifs (week-ends et fériés ne cassent pas la série) → absence longue, sinon ponctuelle |
| Congé déclaré | un jour couvert par `leave` n'est jamais une absence |

Le calcul est une fonction pure (`AttendanceCalculator`) testée sur 17 cas limites, puis recoupée avec un calcul Python indépendant (mêmes 132 / 166 jours d'absence) et vérifiée à la main sur 4 journées (`docs/verification.md`).

## 6. Machine Learning

**Cibles.** `y_d1` : absence ponctuelle au prochain jour ouvré ; `y_w1` : au moins une absence ponctuelle dans les 5 prochains jours ouvrés (modèle direct). Jours cibles en congé, absence longue, férié ou week-end exclus.

**Features** (à la date de référence R, dernière journée connue, sans aucune information postérieure) : calendrier du jour cible (jour de semaine, mois, veille / lendemain de férié, pont, distance au prochain férié, Ramadan, séance unique, nombre de jours depuis R), historique d'absence (7 / 30 / 90 jours, jours depuis la dernière absence, série en cours, taux lissés global et par jour de semaine), retards (nombre, minutes, écart moyen à l'horaire personnel, pente de l'heure d'arrivée), charge (heures moyennes, pente, journées incomplètes), profil (ancienneté, équipe). La classification ponctuelle / longue de la série d'absence en cours est recalculée avec sa longueur connue à R. Deux tests le vérifient : features identiques avec des données tronquées à R, et insensibles à une perturbation des jours ≥ cible.

**Protocole.** Baselines : (a) taux historique d'absence du collaborateur, (b) taux pour le jour de semaine visé. Candidats : régression logistique L2 et LightGBM peu profond, pondération des classes puis **calibration sigmoïde**. Sélection sur la PR-AUC d'une validation walk-forward mensuelle (juillet–décembre 2024) ; test sur 2025 ; un modèle qui ne bat pas la meilleure baseline n'est pas promu. Seuils de risque : quantiles 70 % / 90 % des probabilités de validation. Explications : SHAP (LightGBM), coefficient × valeur standardisée (logistique), description de l'historique (baseline), traduites en phrases.

## 7. Résultats

| Horizon | Retenu | PR-AUC validation (retenu / meilleur modèle) | PR-AUC test (retenu) | Taux de base test |
|---|---|---|---|---|
| J+1 | baseline (b) | 0,162 / 0,152 (LightGBM) | 0,135 | 5,2 % |
| Semaine | baseline (b) | 0,311 / 0,225 (LightGBM) | 0,270 | 20,9 % |

Aucun modèle appris ne bat la baseline « taux par jour de semaine » en validation : la règle de non-promotion s'applique et le système utilise cette baseline transparente. Sur le test 2025, la logistique J+1 (0,193) et LightGBM semaine (0,301) font mieux, mais ce constat ne peut pas servir à choisir sans fuite. Sur 60 collaborateurs synthétiques calibrés sur les statistiques réelles, les modèles battent nettement les baselines en J+1 (0,21–0,22 contre 0,11–0,13) : la limite vient du volume de données, pas du pipeline. Les effets les plus nets dans les données sont calendaires : lundis et vendredis (≈ 9 % et 8 % d'absences contre ≈ 3 %), veilles et lendemains de férié (14 % et 18 % contre 4 %).

## 8. Qualité

36 tests backend (dont intégration Testcontainers et sécurité par rôle), 20 tests ML (dont non-fuite temporelle), 12 tests frontend, 1 scénario E2E Playwright exécuté en desktop et en mobile sur la stack Docker complète, revue visuelle des captures et corrections associées.

## 9. Perspectives

- Connecter la source de pointage en flux quotidien et le référentiel RH réel (congés, hiérarchie, embauches) : la cible « absence ponctuelle » deviendrait beaucoup plus propre.
- Ré-entraîner régulièrement : le modèle appris sera promu automatiquement dès qu'il battra la baseline en validation ; surveiller la calibration (Brier) et la dérive.
- Ajuster les seuils de risque avec les utilisateurs (coût d'une alerte inutile vs absence non anticipée), notamment pour l'horizon semaine.
- Étendre aux autres départements (volume plus important), avec un modèle global et des features de profil.
- Remplacer le logo et les couleurs par la charte officielle ; valider les dates des fêtes religieuses ; passer en production (secrets, HTTPS, désactivation des comptes de démo).
