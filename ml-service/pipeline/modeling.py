"""Modèles candidats, baselines et prédicteur sérialisable (utilisé par l'API)."""
from __future__ import annotations

import warnings
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier
from sklearn.calibration import CalibratedClassifierCV
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from . import explain
from .features import feature_columns, to_matrix

CANDIDATES = ("logistic", "lgbm")
BASELINES = ("baseline_hist", "baseline_dow")
RISK_LEVELS = ("FAIBLE", "MODERE", "ELEVE")


def make_estimator(kind: str):
    if kind == "logistic":
        return Pipeline([
            ("imp", SimpleImputer(strategy="median")),
            ("sc", StandardScaler()),
            ("lr", LogisticRegression(C=0.1, class_weight="balanced", max_iter=5000)),
        ])
    if kind == "lgbm":
        return LGBMClassifier(n_estimators=250, learning_rate=0.03, num_leaves=7, max_depth=3, min_child_samples=40,
                              subsample=0.8, subsample_freq=1, colsample_bytree=0.8, reg_lambda=5.0,
                              class_weight="balanced", random_state=42, verbose=-1, n_jobs=2)
    raise ValueError(kind)


def baseline_proba(df: pd.DataFrame, kind: str, horizon: str) -> np.ndarray:
    """(a) taux historique lissé du collaborateur ; (b) taux pour le(s) jour(s) de semaine ciblé(s).
    Pour W1 : probabilité d'au moins une absence sur 5 jours, 1 − (1 − p)^5."""
    p = df["hist_rate" if kind == "baseline_hist" else "dow_rate"].to_numpy(dtype=float)
    return 1 - (1 - p) ** 5 if horizon == "W1" else p


@dataclass
class HorizonModel:
    horizon: str
    kind: str                       # logistic | lgbm | baseline_hist | baseline_dow
    teams: list[str]
    fill: dict[str, float]
    thresholds: dict[str, float]    # {"MODERE": p1, "ELEVE": p2}
    calibrated: object | None = None
    explainer_model: object | None = None
    expected_days_factor: float | None = None
    columns: list[str] = field(default_factory=list)
    population_rate: float | None = None   # taux journalier moyen d'absence ponctuelle (entraînement)

    @property
    def is_baseline(self) -> bool:
        return self.kind in BASELINES

    def matrix(self, df: pd.DataFrame) -> pd.DataFrame:
        return to_matrix(df, self.teams, self.fill)[self.columns]

    def predict_proba(self, df: pd.DataFrame) -> np.ndarray:
        if self.is_baseline:
            return baseline_proba(df, self.kind, self.horizon)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            return self.calibrated.predict_proba(self.matrix(df))[:, 1]

    def risk_level(self, p: float) -> str:
        if p >= self.thresholds["ELEVE"]:
            return "ELEVE"
        if p >= self.thresholds["MODERE"]:
            return "MODERE"
        return "FAIBLE"

    def expected_days(self, p: float) -> float | None:
        return None if self.expected_days_factor is None else round(float(p) * self.expected_days_factor, 2)

    def contributions(self, df: pd.DataFrame) -> pd.DataFrame:
        """Contributions par feature (log-odds) : SHAP pour LightGBM, coef × valeur standardisée pour la logistique."""
        if self.is_baseline:
            # Une baseline est entièrement déterminée par l'historique : on décrit cet historique par rapport au
            # taux moyen de la population (log-ratio), le taux utilisé par la baseline étant le facteur principal.
            pr = self.population_rate or 0.05
            main, other = ("hist_rate", "dow_rate") if self.kind == "baseline_hist" else ("dow_rate", "hist_rate")
            recent = df["abs_p_30"].to_numpy(dtype=float) / 21.0  # ~21 jours ouvrés dans 30 jours calendaires
            c_main = np.log(df[main].to_numpy(dtype=float) / pr)
            cap = 0.9 * np.abs(c_main)  # le taux réellement utilisé reste le 1er facteur cité
            return pd.DataFrame({
                main: c_main,
                other: np.clip(0.5 * np.log(df[other].to_numpy(dtype=float) / pr), -cap, cap),
                "abs_p_30": np.clip(0.5 * np.log((recent + 0.01) / (pr + 0.01)), -cap, cap),
            }, index=df.index)
        X = self.matrix(df)
        if self.kind == "logistic":
            imp, sc, lr = (self.explainer_model.named_steps[k] for k in ("imp", "sc", "lr"))
            Z = sc.transform(imp.transform(X))
            return pd.DataFrame(Z * lr.coef_[0], columns=self.columns, index=df.index)
        import shap  # import local : coûteux
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            sv = shap.TreeExplainer(self.explainer_model).shap_values(X)
        if isinstance(sv, list):
            sv = sv[1]
        return pd.DataFrame(np.asarray(sv), columns=self.columns, index=df.index)

    def explain(self, df: pd.DataFrame, k: int = 3) -> list[list[dict]]:
        contrib = self.contributions(df)
        return [explain.top_factors(df.loc[idx], contrib.loc[idx], self.horizon, k) for idx in df.index]


def fit_candidate(kind: str, train: pd.DataFrame, teams: list[str], horizon: str) -> HorizonModel:
    """Ajuste un candidat + calibration sigmoïde (validation croisée temporelle sur l'entraînement)."""
    columns = feature_columns(teams)
    fill = to_matrix(train, teams).median(numeric_only=True).fillna(0).to_dict()
    X = to_matrix(train, teams, fill)[columns]
    y = train["y"].to_numpy(dtype=int)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        # Un seul modèle de base (toutes les données d'entraînement) + calibrateur sigmoïde appris sur des
        # prédictions hors-pli (plis contigus dans le temps, non mélangés). Une sigmoïde étant monotone, le
        # classement du modèle est préservé. (Un ensemble sur TimeSeriesSplit dégradait le classement :
        # le 1er membre n'était entraîné que sur un quart des données.)
        cal = CalibratedClassifierCV(make_estimator(kind), method="sigmoid", cv=StratifiedKFold(n_splits=5), ensemble=False)
        cal.fit(X, y)
        base = make_estimator(kind).fit(X, y)
    factor = None
    if horizon == "W1" and (train["y"] == 1).any():
        factor = float(train.loc[train["y"] == 1, "n_abs"].mean())
    return HorizonModel(horizon, kind, teams, fill, {"MODERE": 0.0, "ELEVE": 0.0}, cal, base, factor, columns,
                        float(train["hist_rate"].mean()))


def baseline_model(kind: str, train: pd.DataFrame, teams: list[str], horizon: str) -> HorizonModel:
    factor = float(train.loc[train["y"] == 1, "n_abs"].mean()) if horizon == "W1" and (train["y"] == 1).any() else None
    return HorizonModel(horizon, kind, teams, {}, {"MODERE": 0.0, "ELEVE": 0.0}, expected_days_factor=factor,
                        columns=feature_columns(teams), population_rate=float(train["hist_rate"].mean()))


def predict_any(kind: str, train: pd.DataFrame, test: pd.DataFrame, teams: list[str], horizon: str) -> np.ndarray:
    if kind in BASELINES:
        return baseline_proba(test, kind, horizon)
    return fit_candidate(kind, train, teams, horizon).predict_proba(test)
