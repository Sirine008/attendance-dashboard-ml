from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

Horizon = Literal["D1", "W1"]


class PredictRequest(BaseModel):
    reference_date: date = Field(description="Dernière journée de données connue (R)")
    employee_ids: list[int] = Field(min_length=1, max_length=2000)
    horizons: list[Horizon] = Field(default_factory=lambda: ["D1", "W1"])


class Factor(BaseModel):
    feature: str
    label: str
    direction: Literal["UP", "DOWN"]
    weight: float


class PredictionOut(BaseModel):
    employee_id: int
    horizon: Horizon
    status: Literal["OK", "INSUFFICIENT_HISTORY"]
    reference_date: date
    target_date: date | None = None
    target_end: date | None = None
    probability: float | None = None
    risk_level: Literal["FAIBLE", "MODERE", "ELEVE"] | None = None
    expected_days: float | None = None
    factors: list[Factor] = Field(default_factory=list)


class PredictResponse(BaseModel):
    model_version: str
    reference_date: date
    predictions: list[PredictionOut]


class Health(BaseModel):
    status: Literal["UP", "DEGRADED"]
    model_loaded: bool
    model_version: str | None = None


class ModelInfo(BaseModel):
    version: str
    trained_at: str
    horizons: dict
    data: dict


class BacktestWeek(BaseModel):
    week_start: date
    samples: int
    expected_absences: float
    actual_absences: int
    flagged_high: int
    flagged_high_absent: int


class BacktestResponse(BaseModel):
    model_version: str
    horizon: Horizon
    weeks: list[BacktestWeek]
    pr_auc: float | None
    brier: float | None
    note: str
