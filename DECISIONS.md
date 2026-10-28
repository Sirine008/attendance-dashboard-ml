# Journal des décisions

Chaque entrée : date, décision, alternatives envisagées, raison. Les éléments marqués **[À VÉRIFIER]** doivent être confirmés par l'équipe Attijari.

## Phase 1 — Outillage et scaffolding

### 2026-10-28 — Spring Boot 3.5.16 (pom écrit à la main)
- **Décision** : `spring-boot-starter-parent` 3.5.16, Java 21 (`maven.compiler.release=21`), wrapper Maven 3.9.16.
- **Alternatives** : Spring Boot 4.x (seule version proposée par start.spring.io aujourd'hui).
- **Raison** : la spec impose Spring Boot 3.x ; 3.5.x est la dernière branche 3.

### 2026-10-28 — JDK 21 et Node 22 portables
- **Décision** : le poste ne contient qu'un JDK 8 fonctionnel (le dossier `jdk-23` est vide) et Node 20.11. Installation de Temurin 21 et Node 22.23 en versions portables dans `%USERPROFILE%\tools` (aucune modification système). `scripts/env.ps1` / `scripts/env.sh` les ajoutent au PATH.
- **Alternatives** : installer via winget (modifie la machine) ; Angular 19 compatible Node 20.11.
- **Raison** : la spec demande la « dernière LTS » d'Angular (v21, qui exige Node ≥ 20.19) et Java 21.

### 2026-10-28 — Angular 21 + Angular Material + Vitest
- **Décision** : Angular 21 (LTS), composants standalone, signals, **Angular Material** (pas PrimeNG), ngx-echarts 21. Tests unitaires frontend avec **Vitest** (runner par défaut d'Angular 21).
- **Alternatives** : PrimeNG ; Karma/Jasmine.
- **Raison** : Material est maintenu par l'équipe Angular, thémable par tokens CSS (pratique pour la charte). Vitest est le runner officiel d'Angular 21, son API (`describe/it/expect`) est compatible Jest, alors que Karma est déprécié — c'est l'équivalent moderne de l'option « Jest » de la spec.

### 2026-10-28 — npm `legacy-peer-deps`
- **Décision** : `frontend/.npmrc` contient `legacy-peer-deps=true`.
- **Raison** : npm 10.9 plante (`Cannot read properties of null (reading 'edgesOut')`) sur la chaîne de peers optionnels `vitest → jsdom → canvas`. Contournement standard, sans impact à l'exécution.

### 2026-10-28 — Postgres de dev sur le port 5440
- **Décision** : le conteneur Postgres de Docker Compose est publié sur `5440` ; le profil local par défaut pointe sur `localhost:5440`.
- **Raison** : deux PostgreSQL natifs occupent déjà `5432` et `127.0.0.1:5433` sur le poste ; on évite tout conflit.

## Phase 2 — Données

### 2026-10-28 — Volume réel : 11 367 pointages (et non ~12 400)
- **Décision** : on s'appuie sur le volume réellement lu (11 367 lignes horodatées, 9 doublons exacts). L'écart avec la spec est une estimation initiale ; les tests d'intégration vérifient ce chiffre.

### 2026-10-28 — Dates d'embauche réelles de MMMMMM et OOOOOO
- **Décision** : le fichier contient une note « Date d'embauche le 06/08/2024 » sur les feuilles MMMMMM et OOOOOO (colonne hors en-tête). L'import la détecte (regex `embauche … dd/MM/yyyy`) et la stocke avec `hire_date_fictive = false`. Les 6 autres dates d'embauche sont **fictives** (seed V3).
- **Raison** : utiliser l'information réelle quand elle existe ; la spec indiquait « aucune ancienneté » mais le fichier en contient deux.

### 2026-10-28 — Seuil de doublon : 120 s, même sens et même porte
- **Alternatives** : 30 s, 60 s, 300 s. **Raison** : la courbe du nombre de doublons sature dès 60 s (`docs/data_profile.md` §3) ; 120 s laisse une marge sans fusionner de vrais allers-retours. Paramètre `duplicate_window_seconds`.

### 2026-10-28 — Calendrier des fériés **[À VÉRIFIER pour les fêtes religieuses]**
- Fixes : 1/1, 20/3, 9/4, 1/5, 25/7, 13/8, 15/10, **17/12** (Fête de la Révolution depuis le décret de 2021 ; le 14/01/2025 est travaillé dans les données, ce qui le confirme).
- Religieux 2024 : Aïd el-Fitr 10–11/04, Aïd el-Adha 16–17/06, Ras El Am El Hejri 07/07, Mouled 15/09. 2025 : Aïd el-Fitr 30/03–01/04, Aïd el-Adha 06–07/06, Hejri 26/06, Mouled 04/09.
- **Contrôle sur données** : les 16 jours ouvrés sans aucun pointage de toute l'équipe coïncident tous avec ce calendrier ; aucun férié du calendrier n'a de pointage. Les dates religieuses de 2025 postérieures à mai (hors données) restent à vérifier.

### 2026-10-28 — Périodes spéciales sans horaire imposé par défaut
- **Décision** : Ramadan (11/03–09/04/2024, 01/03–29/03/2025) et séance unique (juillet–août) sont créés dans `special_period` avec `ref_start_time = NULL` : l'horaire personnel reste la référence. RH peut saisir un horaire propre, qui remplace alors l'horaire personnel pendant la période (testé).
- **Raison** : les médianes d'arrivée ne changent pas pendant ces périodes (`data_profile.md` §7). Imposer 7h30 en été aurait marqué la plupart des collaborateurs en retard à tort.

## Phase 3 — Règles de calcul

### 2026-10-28 — Début et fin de suivi
- **Décision** : un collaborateur est suivi de `max(date d'embauche, 1re date du fichier)` jusqu'à la **dernière date du fichier** (19/05/2025, toutes personnes confondues). Les jours ouvrés sans pointage dans cet intervalle sont des absences.
- **Raison** : une date de début par personne (1er pointage) masquerait les absences de début de période (ex. XXXXXX absent les 2 et 3/01/2024).

### 2026-10-28 — Journée avec pointages mais sans entrée frontière
- **Décision** : présente mais `incomplete = true` (pas d'heure d'arrivée, pas de retard calculable). **Raison** : la spec définit l'absence comme « jour ouvré sans aucun pointage » ; un oubli de badge à l'entrée n'est pas une absence. Un seul cas dans les données.

### 2026-10-28 — Heures travaillées partielles
- **Décision** : pour une journée incomplète, seules les paires IN→OUT consécutives valides sont additionnées (`worked_minutes` peut être partiel ou NULL) ; ces journées sont exclues des moyennes d'heures et signalées dans l'UI. Aucune heure n'est extrapolée.

### 2026-10-28 — Minutes de retard
- **Décision** : `late_minutes` = 1re entrée − horaire de référence (et non − (référence + tolérance)) ; la tolérance ne sert qu'à décider s'il y a retard.

### 2026-10-28 — Séries d'absence
- **Décision** : les week-ends et fériés ne cassent pas une série d'absence (vendredi + lundi + mardi = 3 jours ouvrés consécutifs → longue). Un congé déclaré ou un samedi travaillé la cassent.

### 2026-10-28 — Horaires stockés en heure locale
- **Décision** : `raw_punch.punch_time` est un `TIMESTAMP` sans fuseau (heure murale Africa/Tunis), sans conversion Hibernate. Un test d'API a mis en évidence un décalage d'une heure avec `hibernate.jdbc.time_zone=UTC`, retiré.

### 2026-10-28 — Écritures en masse via JdbcTemplate
- **Décision** : l'insertion de `raw_punch` (`ON CONFLICT DO NOTHING` → idempotence) et de `daily_attendance` passe par JdbcTemplate en batch ; le reste en JPA. Recalcul complet (tous collaborateurs) après chaque import ou modification d'administration : ~1–2 s sur les données réelles.

## Phase 4 — API et sécurité

### 2026-10-28 — JWT HS256 via Spring Security Resource Server
- **Décision** : pas de bibliothèque JWT tierce ; `NimbusJwtEncoder/Decoder` (inclus dans `spring-boot-starter-oauth2-resource-server`), secret ≥ 32 octets (`APP_JWT_SECRET`), durée 8 h. Claims : `role`, `teamId`, `name`.

### 2026-10-28 — Comptes de démonstration (SEED FICTIF)
- `admin`, `rh`, `manager.a` (équipe A : X, Y, Z, T), `manager.b` (équipe B : W, V, M, O), mot de passe commun `Demo@2025` (`APP_DEMO_PASSWORD`). Créés au démarrage si la table est vide. Répartition des équipes arbitraire.

### 2026-10-28 — Définition des KPIs
- Taux de présence = jours présents / jours ouvrés attendus, où « attendus » exclut les congés **déclarés** (mais pas les absences longues, qui restent visibles séparément). Retard moyen = moyenne sur les seuls jours de retard. Heures moyennes = journées complètes uniquement. Variation = même indicateur sur la période précédente de même durée.

### 2026-10-28 — Périmètre MANAGER
- Filtrage côté serveur dans `AccessScopeService` : toute requête d'un manager sur une autre équipe (filtre `teamId`, fiche, calendrier, timeline, prédictions) renvoie **403**. Testé dans `SecurityIntegrationTest`.

### 2026-10-28 — Liste des risques triée alphabétiquement
- **Décision** : `/api/predictions/latest` trie par nom, jamais par probabilité décroissante. **Raison** : la spec interdit de mettre en scène un classement « des pires » ; le filtre par niveau suffit à l'usage.

## Phase 5 — Machine Learning

### 2026-10-28 — Convention de date de référence
- **Décision** : une prédiction est faite à la date de référence R = dernière journée de données connue. D1 = prochain jour ouvré après R ; W1 = 5 prochains jours ouvrés après R. Le job de 6h00 prédit donc la journée qui commence et la semaine glissante.
- **Raison** : à 6h00 les pointages du jour ne sont pas encore disponibles ; c'est la seule convention sans fuite.

### 2026-10-28 — Classification causale de la série d'absence en cours
- **Décision** : dans les features, une série d'absence en cours à R est classée avec sa longueur **connue à R** (2 jours → encore « ponctuelle », même si elle deviendra longue). Testé par `test_features_only_use_data_up_to_reference_date` (features identiques avec données tronquées à R) et `test_future_perturbation_does_not_change_features`.

### 2026-10-28 — Lissage des taux historiques (baselines et features)
- **Décision** : taux = (k + 20 × 5 %) / (n + 20) (poids 5 pour le taux par jour de semaine). **Raison** : éviter des taux de 0 % ou 100 % en début d'historique ; le prior 5 % est le taux observé d'absence ponctuelle.

### 2026-10-28 — Baselines pour W1
- **Décision** : probabilité d'au moins une absence sur 5 jours = 1 − (1 − p)^5 avec p le taux de la baseline. (Le **modèle** W1 reste direct, comme exigé ; seule la baseline est dérivée du taux journalier.)

### 2026-10-28 — Calibration
- **Décision** : `CalibratedClassifierCV(method="sigmoid", cv=StratifiedKFold(5) sans mélange, ensemble=False)` : un seul modèle de base sur tout l'entraînement + une sigmoïde apprise hors-pli (plis contigus).
- **Alternative écartée** : ensemble sur `TimeSeriesSplit(3)` — mesuré : il dégradait fortement le classement (ROC-AUC W1 de LightGBM 0,30 contre 0,57 sans calibration), car le 1er membre n'est entraîné que sur un quart des données.

### 2026-10-28 — Hyperparamètres fixés a priori
- **Décision** : logistique L2 C = 0,1 ; LightGBM peu profond (7 feuilles, profondeur 3, 250 arbres, lr 0,03, min 40 échantillons/feuille), `class_weight="balanced"` puis calibration. Pas de recherche d'hyperparamètres. **Raison** : ~90 positifs en entraînement D1 ; une recherche sur une si petite validation sur-apprendrait la validation.

### 2026-10-28 — Résultat : les baselines sont conservées (règle de non-promotion appliquée)
- **Constat** (exécution réelle, `ml-service/reports/model_card.md`) : en validation walk-forward (juillet–décembre 2024), aucun modèle ne bat la baseline (b) « taux du collaborateur pour ce jour de semaine » : D1 PR-AUC 0,152 (LightGBM) contre 0,162 ; W1 0,225 contre 0,311.
- **Décision** : la baseline (b) est le prédicteur actif pour D1 et W1. Sur le test 2025, la logistique D1 (0,193) et LightGBM W1 (0,301) font mieux que la baseline, mais choisir sur le test serait une fuite : ce constat est documenté sans changer le choix. Le pipeline promouvra automatiquement un modèle au prochain ré-entraînement s'il bat la baseline en validation.
- **Contrôle** : sur 60 collaborateurs synthétiques, les modèles battent nettement les baselines en D1 (PR-AUC test 0,21 contre 0,13) : le pipeline apprend bien quand le signal et le volume existent.

### 2026-10-28 — Seuils de risque
- **Décision** : MODÉRÉ = quantile 70 %, ÉLEVÉ = quantile 90 % des probabilités de validation du prédicteur retenu (≈ 30 % / 10 % des cas signalés). Stockés dans l'artefact et dans `model_registry.thresholds`.

### 2026-10-28 — Facteurs affichés quand une baseline est active
- **Décision** : le 1er facteur est toujours le taux réellement utilisé par la baseline ; les deux suivants décrivent l'historique (taux global, absences ponctuelles sur 30 jours), comparés au taux moyen de la population (log-ratio). Formulation « facteurs associés », jamais « causes » (testé).

### 2026-10-28 — Nombre de jours d'absence attendu (W1)
- **Décision** : probabilité W1 × nombre moyen de jours d'absence ponctuelle dans les semaines positives de l'entraînement (1,29).

### 2026-10-28 — Modèle déployé ré-entraîné sur toutes les données
- **Décision** : les métriques publiées viennent du protocole entraînement 2024 / test 2025 ; l'artefact déployé est ré-ajusté sur l'ensemble des données avec les mêmes réglages et les mêmes seuils.

### 2026-10-28 — Données synthétiques
- **Décision** : 60 collaborateurs `SYN001…SYN060`, graine 42, calibrés sur les données réelles (`reports/synthetic_calibration.json`), exportés au format du fichier réel (`data/synthetic/Pointage_synthetique.xlsx`) pour être importés par le **même** chemin d'import. Équipe dédiée « Équipe démo (données synthétiques) », `is_synthetic = true`. Le modèle officiel est entraîné sur les données réelles uniquement.

## Phase 6 — Microservice et job planifié

### 2026-10-28 — ml-service lit directement la base
- **Décision** : FastAPI lit `daily_attendance`, `employee`, `public_holiday`, `special_period` (lecture seule) et écrit `model_registry` lors d'un entraînement. Spring envoie seulement la date de référence et la liste des collaborateurs (`POST /predict`), puis historise les résultats dans `prediction`.
- **Alternative** : Spring envoie tout l'historique dans la requête. **Raison** : un seul calcul de features (Python), mêmes données pour l'entraînement et l'inférence, requêtes légères.

### 2026-10-28 — Modèle embarqué dans l'image + enregistrement automatique
- **Décision** : l'artefact entraîné (`ml-service/models/`) est livré dans l'image (et copié dans le volume `mlmodels`). Au démarrage, ml-service l'inscrit dans `model_registry` s'il n'y figure pas (base neuve), avec nouvelles tentatives tant que Flyway n'a pas créé la table. `POST /train` (jeton `X-Admin-Token`) crée une nouvelle version.

### 2026-10-28 — Job planifié et historique initial
- **Décision** : cron `0 0 6 * * MON-FRI` (fuseau Africa/Tunis, configurable `APP_PREDICTION_CRON`). Date de référence = min(veille, dernière journée importée). Au premier démarrage, en tâche de fond : attente de ml-service (jusqu'à 10 min), puis **backfill** des 40 derniers jours ouvrés (prédictions « comme si » le job avait tourné ces jours-là, avec le modèle actif) pour alimenter dès le départ l'historique prédit vs réel, puis prédiction courante.
- **Limite** : ces prédictions rétroactives utilisent le modèle actuel ; avec une baseline (taux calculés uniquement sur le passé) elles sont hors échantillon, mais ce ne serait plus le cas avec un modèle appris — c'est signalé dans la note du backtest.

### 2026-10-28 — Résilience
- **Décision** : 3 tentatives espacées de 5 s (`app.ml.max-attempts`, `app.ml.retry-delay`), puis échec journalisé ; `runSafely` ne lève jamais d'exception. `/api/predictions/status` (santé ml-service mise en cache 30 s) alimente le bandeau « prédictions indisponibles » ; les indicateurs descriptifs ne dépendent pas de ml-service (testé avec un faux service en panne).

### 2026-10-28 — Données synthétiques importées par défaut dans Docker
- **Décision** : `APP_IMPORT_SYNTHETICFILE` pointe vers `data/synthetic/Pointage_synthetique.xlsx` ; les 60 collaborateurs `SYN…` sont importés par le même chemin que le fichier réel, dans une équipe dédiée. Ils sont **exclus par défaut** de tous les écrans (paramètre `synthetic=false`) et n'apparaissent qu'avec l'option « Inclure les données synthétiques (démo) » ou en filtrant sur leur équipe ; ils sont toujours badgés « Synthétique ». Pour une installation sans démo : `APP_IMPORT_SYNTHETICFILE=` (vide).

## Phase 7 — Frontend

### 2026-10-28 — Polices auto-hébergées
- **Décision** : Inter (`@fontsource-variable/inter`) et Material Symbols (`material-symbols`) sont empaquetées via npm, sans appel à Google Fonts. **Raison** : poste bancaire potentiellement sans accès Internet ; aucune dépendance externe à l'exécution.

### 2026-10-28 — Couleurs de marque **[À REMPLACER PAR LA CHARTE OFFICIELLE]**
- Rouge `#D71920`, jaune/or `#F6A800` dans `frontend/src/styles/_tokens.scss` (fichier unique), plus des variantes « strong » assombries pour le texte (contraste AA sur fond clair) et un jeu de tokens sombres. Logo : placeholder textuel `frontend/src/assets/brand/logo.svg`, remplaçable sans toucher au code.

### 2026-10-28 — Choix d'interface
- Filtres globaux (période, équipe, synthétique) partagés par les pages et conservés dans `sessionStorage` ; période par défaut = dernier mois de données (le fichier s'arrête en mai 2025).
- Le document défile (barre supérieure collante, navigation fixe repliable en rail de 72 px) ; tiroir superposé sous 900 px.
- Statuts : icône + libellé court + couleur (jamais la couleur seule) ; variations des KPI : flèche + texte + `aria-label` « favorable / défavorable ».
- Page Risques : tri alphabétique, filtre « Élevé » par défaut (conforme à la spec) avec un bouton pour élargir quand la liste est vide ; probabilités affichées arrondies (« ≈ 35 % », au pas de 5 au-delà de 10 %).
- ECharts importé à la carte (ligne, barres, heatmap, nuage) et chargé paresseusement ; couleurs relues dans les tokens CSS à chaque changement de thème.
- Budget de build initial relevé à 1,2 Mo (avertissement) / 2 Mo (erreur) : Angular Material + polices ; ECharts est hors bundle initial.

## Phase 8 — Vérification

### 2026-10-28 — E2E contre la stack Docker
- **Décision** : le scénario Playwright cible `http://localhost:4200` (nginx de la stack Compose, `E2E_BASE_URL` pour changer) plutôt qu'un `ng serve` : il valide l'ensemble réel (nginx → backend → ml-service → Postgres). Projets desktop (1440×900) et mobile (Pixel 7).
- Corrections issues de la revue visuelle : heatmap sans couleurs (format des données ECharts), légendes qui chevauchaient les titres d'axe (passées en bas), courbe lissée dépassant 100 %, colonnes de risque hors écran, initiales, barre collante dans les captures pleine page, dates ISO sur les axes, chargement d'ECharts (`init is not a function`).
