# Attijari Bank — Tableau de bord d'assiduité & anticipation des absences

Application web du Département Études Informatiques (siège Attijari Bank, Tunisie) qui permet aux RH et aux responsables directs (N+1) de suivre présences, retards et absences, et qui estime le **risque d'absence ponctuelle au prochain jour ouvré et sur la semaine suivante**.

> **Usage autorisé.** Le module de prédiction est un **outil d'aide à l'anticipation (organisation du travail)**, **pas un outil d'évaluation ni de sanction**. Les facteurs affichés sont des facteurs *associés* à l'estimation, jamais des causes. Aucune estimation n'est affichée pour un collaborateur ayant moins de 30 jours ouvrés d'historique. Conformité à la loi organique tunisienne n° 2004-63 : minimisation (seuls les horodatages de passage sont utilisés), accès restreint par rôle, traçabilité des consultations (`audit_log`).

![Vue d'ensemble](docs/screenshots/desktop-02-vue-ensemble.png)

## Sommaire

1. [Architecture](#architecture)
2. [Lancement avec Docker](#lancement-avec-docker)
3. [Lancement sans Docker (Windows)](#lancement-sans-docker-windows)
4. [Comptes de démonstration](#comptes-de-démonstration)
5. [Fonctionnalités et captures](#fonctionnalités-et-captures)
6. [Résultats du modèle (données réelles)](#résultats-du-modèle-données-réelles)
7. [Tests](#tests)
8. [Limites connues](#limites-connues)
9. [Documentation](#documentation)

## Architecture

```mermaid
flowchart LR
    U[Navigateur<br/>RH · Manager · Admin] -->|HTTPS| N[frontend<br/>Angular 21 + Material<br/>servi par nginx :4200]
    N -->|/api| B[backend<br/>Spring Boot 3.5 · Java 21<br/>JWT · Flyway · Swagger :8080]
    B -->|JPA / JDBC| P[(PostgreSQL 16<br/>:5440)]
    B -->|POST /predict<br/>POST /train<br/>GET /model/*| M[ml-service<br/>FastAPI · scikit-learn<br/>LightGBM · SHAP :8000]
    M -->|lecture daily_attendance<br/>écriture model_registry| P
    X[[data/raw/Pointage_PFA.xls]] -->|import idempotent<br/>au démarrage ou upload| B
    S[[Job @Scheduled<br/>6h00 jours ouvrés]] -.-> B
```

| Dossier | Contenu |
|---|---|
| `backend/` | API Spring Boot : import Excel, calcul d'assiduité, KPIs, sécurité par rôle, job de prédiction |
| `frontend/` | Angular 21 (standalone, signals), Angular Material, ECharts ; tests Vitest + E2E Playwright |
| `ml-service/` | `pipeline/` (features, modèles, évaluation, model card), `synthetic/` (générateur), `app/` (FastAPI) |
| `data/raw/` | Fichier source réel `Pointage_PFA.xls` |
| `data/synthetic/` | 60 collaborateurs **synthétiques** générés (démo à plus grande échelle) |
| `docs/` | Profil des données, rapport technique, vérification manuelle, captures |
| `scripts/` | CI locale (`ci.ps1`), environnement (`env.ps1`, `env.sh`) |

Flux : le fichier de pointage est importé (lignes brutes dans `raw_punch`, sans doublon) → `daily_attendance` est recalculée (présence, retard relatif à l'horaire personnel, heures, absences ponctuelles / longues) → chaque matin le job demande à `ml-service` les estimations J+1 et semaine, historisées dans `prediction`.

## Lancement avec Docker

Prérequis : Docker Desktop (Windows, CPU uniquement).

```powershell
docker compose up --build
```

| Service | URL |
|---|---|
| Application | http://localhost:4200 |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| ml-service (OpenAPI) | http://localhost:8000/docs |
| PostgreSQL | `localhost:5440` (base/utilisateur/mot de passe `assiduite`) |

Au premier démarrage (≈ 1 min une fois les images construites ; le premier build de l'image ml-service est long) :
1. Flyway crée le schéma et les référentiels (terminaux, fériés 2024–2025, périodes spéciales, équipes fictives) ;
2. le backend importe `data/raw/Pointage_PFA.xls` puis `data/synthetic/Pointage_synthetique.xlsx` ;
3. `ml-service` charge le modèle livré et l'inscrit dans `model_registry` ;
4. le backend reconstitue 40 jours d'historique de prédictions puis génère les estimations courantes.

Variables utiles (fichier `.env` ou environnement) : `APP_JWT_SECRET`, `ML_ADMIN_TOKEN`, `APP_PREDICTION_CRON` (défaut `0 0 6 * * MON-FRI`), `APP_IMPORT_SYNTHETICFILE=` (vide = pas de données synthétiques), `APP_DEMO_PASSWORD`.

Réinitialiser complètement : `docker compose down -v`.

## Lancement sans Docker (Windows)

Prérequis : JDK 21, Node 22 (≥ 20.19), Python 3.11, PostgreSQL 16. Les scripts `scripts\env.ps1` / `scripts/env.sh` ajoutent au PATH un JDK 21 et un Node 22 portables placés dans `%USERPROFILE%\tools` s'ils existent.

```powershell
# 1. Base de données (PostgreSQL natif ou conteneur seul)
docker compose up -d postgres          # ou : créer la base « assiduite » (utilisateur/mot de passe assiduite) dans un PostgreSQL 16 local

# 2. Backend (port 8080) — importe le fichier réel au premier démarrage
. .\scripts\env.ps1
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5440/assiduite"   # adapter au PostgreSQL utilisé
.\mvnw.cmd spring-boot:run

# 3. ml-service (port 8000), dans un autre terminal
cd ml-service
py -3.11 -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements-dev.txt
$env:DATABASE_URL = "postgresql+psycopg://assiduite:assiduite@localhost:5440/assiduite"
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000

# 4. Frontend (port 4200, proxy /api -> 8080), dans un autre terminal
. .\scripts\env.ps1
cd frontend
npm ci
npx ng serve
```

Pipeline ML en ligne de commande (depuis `ml-service/`, base alimentée par le backend) :

```powershell
.\.venv\Scripts\python -m analysis.profile_data           # -> docs/data_profile.md
.\.venv\Scripts\python -m pipeline.train                  # entraîne, évalue, écrit reports/model_card.md, enregistre une version
.\.venv\Scripts\python -m synthetic.generator --calibrate-from-db   # -> data/synthetic/Pointage_synthetique.xlsx
.\.venv\Scripts\python -m pipeline.train --synthetic-report         # -> reports/synthetic_report.md (étiqueté SYNTHÉTIQUE)
```

## Comptes de démonstration

**Seed fictif** (la hiérarchie n'existe pas dans le fichier source). Mot de passe commun : `Demo@2025`.

| Identifiant | Rôle | Périmètre |
|---|---|---|
| `admin` | ADMIN | tout, y compris utilisateurs et journal d'audit |
| `rh` | RH | tous les collaborateurs, administration, santé du modèle |
| `manager.a` | MANAGER | Équipe Études A (fictive) : X, Y, Z, T |
| `manager.b` | MANAGER | Équipe Études B (fictive) : W, V, M, O |

Un manager ne voit **que son équipe** (filtrage côté serveur, toute autre demande renvoie 403).

## Fonctionnalités et captures

| Page | Contenu |
|---|---|
| Connexion | logo (placeholder remplaçable `frontend/src/assets/brand/logo.svg`), thème clair / sombre |
| Vue d'ensemble | période Jour / Mois / Année / plage + équipe ; 7 KPI avec variation vs période précédente ; tendance ; heatmap jour × semaine ; répartition des arrivées et écart à l'horaire de référence |
| Collaborateurs | tri, recherche, pagination, export CSV, sparkline de présence, badges de risque |
| Fiche collaborateur | profil, KPI, calendrier mensuel (présent / retard / absent / congé / férié / incomplet), timeline des pointages d'un jour par porte, arrivées vs référence, risques J+1 et semaine avec facteurs associés, historique estimé vs réel (consultation tracée) |
| Risques & prédictions | cartes par collaborateur (niveau, « ≈ 35 % », facteurs, « Estimation statistique, non une certitude »), tri alphabétique |
| Santé du modèle (RH/Admin) | version active, métriques vs baselines, calibration, backtest 8 semaines, réentraînement |
| Administration (RH/Admin) | import avec rapport, horaires de référence, fériés, périodes spéciales, congés, terminaux, paramètres, utilisateurs, audit |
| À propos du modèle | méthode, limites, usage autorisé, protection des données |

| Desktop | Mobile |
|---|---|
| ![Fiche](docs/screenshots/desktop-04-fiche-collaborateur.png) | ![Fiche mobile](docs/screenshots/mobile-04-fiche-collaborateur.png) |
| ![Risques](docs/screenshots/desktop-05-risques.png) | ![Vue d'ensemble mobile](docs/screenshots/mobile-02-vue-ensemble.png) |

Toutes les captures (générées par le test E2E) : [`docs/screenshots/`](docs/screenshots/).

## Résultats du modèle (données réelles)

Chiffres issus de l'exécution réelle de `python -m pipeline.train` (version `v20261028-155209`, détail et graphiques : [`ml-service/reports/model_card.md`](ml-service/reports/model_card.md)). Données : 8 collaborateurs réels, 02/01/2024 → 19/05/2025. Split temporel : entraînement sur les cibles 2024, test sur 2025 ; sélection sur une validation walk-forward mensuelle (juillet–décembre 2024).

**J+1 (prochain jour ouvré)** — taux de positifs en test : 5,2 % (36 / 686)

| Prédicteur | PR-AUC validation | PR-AUC test | ROC-AUC test | Brier test | Précision@3 test |
|---|---|---|---|---|---|
| Régression logistique | 0,108 | 0,193 | 0,717 | 0,049 | 0,079 |
| LightGBM | 0,152 | 0,124 | 0,654 | 0,049 | 0,075 |
| Baseline (a) taux historique | 0,091 | 0,067 | 0,563 | 0,050 | 0,061 |
| **Baseline (b) taux par jour de semaine — utilisée** | **0,162** | 0,135 | 0,614 | 0,049 | 0,068 |

**Semaine (5 prochains jours ouvrés)** — taux de positifs en test : 20,9 % (137 / 657)

| Prédicteur | PR-AUC validation | PR-AUC test | ROC-AUC test | Brier test | Précision@3 test |
|---|---|---|---|---|---|
| Régression logistique | 0,204 | 0,173 | 0,350 | 0,167 | 0,150 |
| LightGBM | 0,225 | 0,301 | 0,634 | 0,165 | 0,240 |
| Baseline (a) taux historique | 0,304 | 0,279 | 0,589 | 0,163 | 0,251 |
| **Baseline (b) taux par jour de semaine — utilisée** | **0,311** | 0,270 | 0,583 | 0,165 | 0,258 |

**Lecture honnête.** En validation, **aucun modèle appris ne bat la baseline (b)** : conformément à la règle de non-promotion, c'est elle qui produit les estimations, pour les deux horizons. Sur le test 2025, la logistique J+1 et LightGBM semaine font mieux, mais choisir un modèle sur le test serait une fuite : le constat est documenté sans changer le choix. Au seuil « élevé », la baseline J+1 signale ~10 % des cas avec un rappel de 0,28 et une précision de 0,15 (taux de base 0,05).

**Contrôle sur données synthétiques** (60 collaborateurs générés, rapporté à part : [`ml-service/reports/synthetic_report.md`](ml-service/reports/synthetic_report.md)) : en J+1, les modèles battent nettement les baselines (PR-AUC test 0,21–0,22 contre 0,11–0,13). Le pipeline apprend donc bien quand le signal et le volume existent ; la faiblesse sur données réelles tient à leur taille.

## Tests

| Module | Commande | Contenu |
|---|---|---|
| Tout | `powershell -ExecutionPolicy Bypass -File scripts\ci.ps1` | build + tests des 3 modules |
| Backend | `cd backend; .\mvnw.cmd verify` | 36 tests JUnit 5 : 17 cas limites du calcul d'assiduité (impair, doublon, porte interne, férié, séance unique, congé…), parseur Excel, import réel + idempotence (Testcontainers PostgreSQL), sécurité par rôle, contrats d'API, job de prédiction avec faux ml-service (historisation, panne) — **Docker requis** |
| ML | `cd ml-service; .\.venv\Scripts\python -m pytest` | 20 tests : **non-fuite temporelle** (features identiques avec données tronquées à la date de référence, perturbation du futur sans effet), cibles, calendrier, calibration, formulation des facteurs, API FastAPI |
| Frontend | `cd frontend; npx ng test --watch=false` | 12 tests Vitest (KPI, badge de risque, formats / CSV, filtres) |
| E2E | `docker compose up -d` puis `cd frontend; npx playwright test` | parcours manager connexion → vue d'ensemble → fiche → risques (desktop + mobile, captures dans `docs/screenshots/`) + refus de l'administration |

## Limites connues

- **Très petit échantillon** (8 personnes, ~130 jours d'absence ponctuelle) : métriques très variables ; les écarts de quelques points ne sont pas significatifs.
- **Pas de données de congés** dans le fichier : « absence longue » (≥ 3 jours ouvrés) est une heuristique ; un congé d'un ou deux jours non déclaré dans l'administration est compté comme absence ponctuelle.
- **Hiérarchie, équipes et 6 dates d'embauche sur 8 fictives** (seed indiqué comme tel) ; MMMMMM et OOOOOO ont une date d'embauche réelle lue dans le fichier.
- **Données arrêtées au 19/05/2025** : les estimations portent sur les jours suivant la dernière journée importée (date de référence = min(veille, dernière journée connue)).
- Les seuils de risque (quantiles de la validation 2024) se révèlent hauts pour la semaine en 2025 : très peu d'estimations « élevé » sur cet horizon (rappel 0,007 au seuil élevé, 0,44 au seuil modéré).
- Les dates des fêtes religieuses 2025 postérieures aux données et les couleurs de marque sont **à vérifier / remplacer** (voir `DECISIONS.md`).
- L'historique de prédictions initial est reconstitué au premier démarrage avec le modèle actif (backfill), il ne s'agit pas de prédictions faites « en temps réel » à l'époque.

## Documentation

- [`docs/rapport_technique.md`](docs/rapport_technique.md) — rapport technique (contexte, données, règles métier, ML, résultats, perspectives)
- [`docs/data_profile.md`](docs/data_profile.md) — profil des données (qualité, anomalies, statistiques)
- [`docs/verification.md`](docs/verification.md) — vérification manuelle de journées contre le fichier Excel
- [`ml-service/reports/model_card.md`](ml-service/reports/model_card.md) — model card générée
- [`DECISIONS.md`](DECISIONS.md) — journal des décisions · [`PROGRESS.md`](PROGRESS.md) — avancement
- Swagger : http://localhost:8080/swagger-ui.html
