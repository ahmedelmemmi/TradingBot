"""
Model registry – holds the currently active model in memory.
"""

import os
import re
import joblib
import logging
from datetime import datetime
from typing import Optional
import numpy as np

logger = logging.getLogger(__name__)

# Only allow alphanumeric characters, underscores, and hyphens in version strings
# to prevent path traversal attacks.
_VERSION_RE = re.compile(r'^[A-Za-z0-9_\-]+$')


class ModelRegistry:
    """In-memory registry for the active trading model."""

    def __init__(self, models_dir: str):
        self.models_dir    = os.path.realpath(models_dir)
        self.model         = None
        self.active_version: Optional[str] = None
        self.loaded_at:      Optional[datetime] = None
        self.algorithm:      Optional[str] = None
        self.feature_names   = []

    def load_active_model(self):
        """Load the model marked as 'active' (latest version file)."""
        versions = self._list_versions()
        if not versions:
            raise FileNotFoundError("No model files found in " + self.models_dir)
        latest = sorted(versions)[-1]
        self.load_version(latest)

    def load_version(self, version: str):
        if not _VERSION_RE.match(version):
            raise ValueError(f"Invalid model version format: {version!r}")
        # Resolve the absolute path and confirm it stays within models_dir
        model_path = os.path.realpath(
            os.path.join(self.models_dir, f"{version}.joblib")
        )
        if not model_path.startswith(self.models_dir + os.sep):
            raise ValueError(f"Path traversal detected for version: {version!r}")
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")
        bundle = joblib.load(model_path)
        self.model          = bundle["model"]
        self.algorithm      = bundle.get("algorithm", "unknown")
        self.feature_names  = bundle.get("feature_names", [])
        self.active_version = version
        self.loaded_at      = datetime.utcnow()
        logger.info("Loaded model version: %s (%s)", version, self.algorithm)

    def predict_proba(self, X: np.ndarray) -> float:
        """Returns the probability of a BUY outcome (class 1)."""
        if hasattr(self.model, "predict_proba"):
            probs = self.model.predict_proba(X)
            return float(probs[0][1])
        return float(self.model.predict(X)[0])

    def _list_versions(self):
        return [
            f[:-7] for f in os.listdir(self.models_dir)
            if f.endswith(".joblib")
        ]
