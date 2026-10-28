"""Chargement (une fois au démarrage) et rechargement du modèle actif."""
from __future__ import annotations

import logging
import os
import threading
from pathlib import Path

import joblib

log = logging.getLogger(__name__)
MODEL_DIR = Path(os.environ.get("MODEL_DIR", Path(__file__).resolve().parents[1] / "models"))


class ModelStore:
    def __init__(self, model_dir: Path = MODEL_DIR):
        self.model_dir = Path(model_dir)
        self._artifact: dict | None = None
        self._lock = threading.Lock()

    def load_active(self) -> bool:
        active = self.model_dir / "ACTIVE"
        if not active.exists():
            log.warning("Aucun modèle actif dans %s", self.model_dir)
            return False
        version = active.read_text(encoding="utf-8").strip()
        path = self.model_dir / f"{version}.joblib"
        if not path.exists():
            log.error("Artefact introuvable : %s", path)
            return False
        # joblib (pickle) n'est sûr que pour des fichiers de confiance : MODEL_DIR ne contient que les artefacts
        # écrits par pipeline.train (volume privé du conteneur), jamais de fichier fourni par un utilisateur.
        artifact = joblib.load(path)
        with self._lock:
            self._artifact = artifact
        log.info("Modèle %s chargé", version)
        return True

    @property
    def loaded(self) -> bool:
        return self._artifact is not None

    @property
    def version(self) -> str | None:
        return self._artifact["version"] if self._artifact else None

    @property
    def summary(self) -> dict | None:
        return self._artifact["summary"] if self._artifact else None

    def model(self, horizon: str):
        if not self._artifact:
            raise RuntimeError("Aucun modèle chargé")
        return self._artifact["models"][horizon]
