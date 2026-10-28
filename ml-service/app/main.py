"""Microservice IA — estimation du risque d'absence ponctuelle (J+1 et semaine).

Outil d'aide à l'anticipation (organisation du travail) : jamais d'évaluation ni de sanction.
Les facteurs renvoyés sont des facteurs **associés** à l'estimation, pas des causes.
"""
from __future__ import annotations

import logging
import os
import threading
import time
from contextlib import asynccontextmanager
from datetime import date, timedelta

import numpy as np
import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from sklearn.metrics import average_precision_score, brier_score_loss

from pipeline import data as data_mod
from pipeline import features as F

from .model_store import ModelStore
from .schemas import (BacktestResponse, BacktestWeek, Factor, Health, ModelInfo, PredictionOut, PredictRequest,
                      PredictResponse)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s : %(message)s")
log = logging.getLogger("ml-service")
ADMIN_TOKEN = os.environ.get("ML_ADMIN_TOKEN", "change-me-ml-admin-token")


class Services:
    """Dépendances remplaçables dans les tests (chargement des données, moteur SQL, entraînement)."""

    def __init__(self):
        self.store = ModelStore()
        self._engine = None

    @property
    def engine(self):
        if self._engine is None:
            self._engine = data_mod.engine()
        return self._engine

    def load_data(self) -> data_mod.Dataset:
        return data_mod.load(self.engine)

    def train(self) -> dict:
        from pipeline.train import train_and_save
        return train_and_save(self.engine, log=log.info)


services = Services()


def _ensure_registered(attempts: int = 60, delay: float = 5.0) -> None:
    """Inscrit le modèle embarqué dans model_registry s'il n'y figure pas (base neuve). Les tables sont créées
    par Flyway côté backend, éventuellement après le démarrage de ce service : on réessaie."""
    from sqlalchemy import text

    from pipeline.train import register
    for _ in range(attempts):
        try:
            with services.engine.connect() as c:
                known = c.execute(text("SELECT count(*) FROM model_registry WHERE version = :v"),
                                  {"v": services.store.version}).scalar()
            if not known:
                register(services.engine, services.store.summary)
                log.info("Modèle %s inscrit dans model_registry", services.store.version)
            return
        except Exception as e:  # base pas encore prête
            log.info("model_registry pas encore accessible (%s)", type(e).__name__)
            time.sleep(delay)


@asynccontextmanager
async def lifespan(app: FastAPI):
    services.store.load_active()  # chargé une seule fois au démarrage
    if services.store.loaded and os.environ.get("ML_REGISTER_ON_STARTUP", "true") == "true":
        threading.Thread(target=_ensure_registered, daemon=True).start()
    yield


app = FastAPI(title="Attijari — service de prédiction d'absences", version="1.0.0", lifespan=lifespan,
              description="Estimations statistiques d'aide à l'organisation du travail. Facteurs associés, jamais des causes.")


def get_services() -> Services:
    return services


def require_admin(x_admin_token: str | None = Header(default=None)) -> None:
    if x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Jeton d'administration invalide")


def require_model(s: Services = Depends(get_services)) -> Services:
    if not s.store.loaded:
        raise HTTPException(status_code=503, detail="Aucun modèle chargé")
    return s


@app.get("/health", response_model=Health)
def health(s: Services = Depends(get_services)) -> Health:
    return Health(status="UP" if s.store.loaded else "DEGRADED", model_loaded=s.store.loaded, model_version=s.store.version)


def _predict(ds: data_mod.Dataset, s: Services, ref: date, employee_ids: list[int], horizons: list[str]) -> list[PredictionOut]:
    out: list[PredictionOut] = []
    for h in horizons:
        model = s.store.model(h)
        rows = F.inference_rows(ds, employee_ids, ref, h)
        ok = [r for r in rows if r["status"] == "OK"]
        if ok:
            df = pd.DataFrame(ok)
            proba = model.predict_proba(df)
            factors = model.explain(df)
        for r in rows:
            if r["status"] != "OK":
                out.append(PredictionOut(employee_id=r["employee_id"], horizon=h, status="INSUFFICIENT_HISTORY",
                                         reference_date=ref, target_date=r.get("target_date"), target_end=r.get("target_end")))
                continue
            k = ok.index(r)
            p = float(proba[k])
            out.append(PredictionOut(
                employee_id=r["employee_id"], horizon=h, status="OK", reference_date=ref,
                target_date=r["target_date"], target_end=r["target_end"], probability=round(p, 5),
                risk_level=model.risk_level(p), expected_days=model.expected_days(p) if h == "W1" else None,
                factors=[Factor(**f) for f in factors[k]]))
    return out


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest, s: Services = Depends(require_model)) -> PredictResponse:
    ds = s.load_data()
    preds = _predict(ds, s, req.reference_date, req.employee_ids, req.horizons)
    return PredictResponse(model_version=s.store.version, reference_date=req.reference_date, predictions=preds)


@app.post("/train", dependencies=[Depends(require_admin)])
def train(s: Services = Depends(get_services)) -> dict:
    """Réentraîne sur les données réelles, enregistre une nouvelle version dans model_registry et l'active."""
    summary = s.train()
    s.store.load_active()
    return {"version": summary["version"],
            "horizons": {h: {"algorithm": v["algorithm"], "promoted_model": v.get("promoted_model")} for h, v in summary["horizons"].items()}}


@app.get("/model/info", response_model=ModelInfo)
def model_info(s: Services = Depends(require_model)) -> ModelInfo:
    sm = s.store.summary
    return ModelInfo(version=sm["version"], trained_at=sm["trained_at"], horizons=sm["horizons"], data=sm["data"])


@app.get("/model/backtest", response_model=BacktestResponse)
def backtest(weeks: int = Query(8, ge=1, le=26), horizon: str = Query("D1", pattern="^(D1|W1)$"),
             synthetic: bool = Query(False, description="true : évaluer sur les collaborateurs synthétiques"),
             s: Services = Depends(require_model)) -> BacktestResponse:
    """Prédit vs réel sur les `weeks` dernières semaines de données (collaborateurs réels par défaut)."""
    ds = s.load_data().subset(synthetic=synthetic)
    samples = F.build_samples(ds, horizon, min_history=F.MIN_DISPLAY_HISTORY)
    if samples.empty:
        raise HTTPException(status_code=404, detail="Pas de données")
    end = samples.target_date.max()
    window = samples[samples.target_date > end - timedelta(weeks=weeks)].copy()
    model = s.store.model(horizon)
    window["p"] = model.predict_proba(window)
    window["high"] = window["p"] >= model.thresholds["ELEVE"]
    window["week"] = window.target_date.dt.to_period("W-SUN").dt.start_time
    out = []
    for wk, g in window.groupby("week"):
        out.append(BacktestWeek(week_start=wk.date(), samples=len(g), expected_absences=round(float(g.p.sum()), 2),
                                actual_absences=int(g.y.sum()), flagged_high=int(g.high.sum()),
                                flagged_high_absent=int((g.high & (g.y == 1)).sum())))
    y = window.y.to_numpy(dtype=int)
    pr = float(average_precision_score(y, window.p)) if 0 < y.sum() < len(y) else None
    note = ("Prédicteur de type baseline : il n'apprend rien des semaines évaluées (taux calculés uniquement sur le passé), "
            "le backtest est donc hors échantillon." if model.is_baseline else
            "Le modèle déployé a été ré-entraîné sur toutes les données : ce backtest est en partie dans l'échantillon "
            "(voir model_card.md pour l'évaluation hors échantillon).")
    return BacktestResponse(model_version=s.store.version, horizon=horizon, weeks=out,
                            pr_auc=round(pr, 4) if pr is not None else None,
                            brier=round(float(brier_score_loss(y, window.p)), 4), note=note)
