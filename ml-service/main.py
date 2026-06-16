"""
Pocket Option AI Trading Bot – ML Service
==========================================
FastAPI service exposing:
  POST /predict   – Generate buy/sell probabilities for a feature vector
  POST /train     – Retrain the model on recent historical data
  GET  /model/info – Current active model metadata
  GET  /health    – Liveness probe
"""

import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel, Field

from model_trainer import ModelTrainer
from model_registry import ModelRegistry

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ── Models directory ──────────────────────────────────────────────────────────
MODELS_DIR = os.getenv("MODELS_DIR", "./models")
os.makedirs(MODELS_DIR, exist_ok=True)

registry = ModelRegistry(MODELS_DIR)
trainer  = ModelTrainer(MODELS_DIR)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load the active model on startup."""
    try:
        registry.load_active_model()
        logger.info("Active model loaded: %s", registry.active_version)
    except FileNotFoundError:
        logger.warning("No trained model found. Train one via POST /train first.")
    yield


app = FastAPI(
    title="Trading Bot ML Service",
    description="XGBoost / LightGBM ensemble for binary options signal prediction",
    version="1.0.0",
    lifespan=lifespan,
)

Instrumentator().instrument(app).expose(app)


# ── Request / Response schemas ────────────────────────────────────────────────

class FeatureVector(BaseModel):
    rsi14:                 Optional[float] = Field(50.0, description="RSI 14")
    macd_line:             Optional[float] = Field(0.0)
    macd_histogram:        Optional[float] = Field(0.0)
    atr14:                 Optional[float] = Field(0.0)
    adx14:                 Optional[float] = Field(0.0)
    ema20_dist:            Optional[float] = Field(0.0, description="(price-EMA20)/EMA20")
    ema50_dist:            Optional[float] = Field(0.0)
    ema200_dist:           Optional[float] = Field(0.0)
    bb_pct_b:              Optional[float] = Field(0.5, description="Bollinger %B [0-1]")
    relative_volume:       Optional[float] = Field(1.0)
    hour_of_day:           Optional[int]   = Field(12)
    day_of_week:           Optional[int]   = Field(3)
    prev_candle_body_ratio: Optional[float] = Field(0.5)
    prev_candle_upper_wick: Optional[float] = Field(0.25)
    prev_candle_lower_wick: Optional[float] = Field(0.25)
    stoch_rsi_k:           Optional[float] = Field(50.0)
    stoch_rsi_d:           Optional[float] = Field(50.0)
    ema50_above_ema200:    Optional[int]   = Field(0)
    macd_bullish:          Optional[int]   = Field(0)
    adx_strong:            Optional[int]   = Field(0)


class PredictionResponse(BaseModel):
    buyProbability:  float
    sellProbability: float
    confidence:      float
    modelVersion:    str


class TrainRequest(BaseModel):
    db_url:          Optional[str]  = None
    min_samples:     int            = 500
    test_size:       float          = 0.2
    algorithm:       str            = "ensemble"   # xgboost | lightgbm | ensemble


class TrainResponse(BaseModel):
    version:         str
    algorithm:       str
    training_rows:   int
    val_accuracy:    float
    val_auc:         float
    message:         str


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "model": registry.active_version}


@app.get("/model/info")
def model_info():
    if registry.active_version is None:
        raise HTTPException(status_code=404, detail="No active model loaded")
    return {
        "version":    registry.active_version,
        "loadedAt":   registry.loaded_at.isoformat() if registry.loaded_at else None,
        "algorithm":  registry.algorithm,
        "features":   registry.feature_names,
    }


@app.post("/predict", response_model=PredictionResponse)
def predict(features: FeatureVector):
    if registry.model is None:
        raise HTTPException(status_code=503, detail="No model loaded. Call POST /train first.")

    X = _to_array(features)
    buy_prob = float(registry.predict_proba(X))
    sell_prob = 1.0 - buy_prob
    confidence = max(buy_prob, sell_prob)

    return PredictionResponse(
        buyProbability=round(buy_prob, 4),
        sellProbability=round(sell_prob, 4),
        confidence=round(confidence, 4),
        modelVersion=registry.active_version or "unknown",
    )


@app.post("/train", response_model=TrainResponse)
def train(request: TrainRequest):
    """
    Retrain the model on recent trade data stored in PostgreSQL.
    Falls back to synthetic data when a DB URL is not provided.
    """
    db_url = request.db_url or os.getenv("DB_URL")

    result = trainer.train(
        db_url=db_url,
        min_samples=request.min_samples,
        test_size=request.test_size,
        algorithm=request.algorithm,
    )

    # Activate the newly trained model
    registry.load_version(result["version"])

    logger.info("Model trained: %s (AUC=%.4f)", result["version"], result["val_auc"])

    return TrainResponse(
        version=result["version"],
        algorithm=result["algorithm"],
        training_rows=result["training_rows"],
        val_accuracy=result["val_accuracy"],
        val_auc=result["val_auc"],
        message="Training complete. Model is now active.",
    )


# ── Helpers ───────────────────────────────────────────────────────────────────

FEATURE_NAMES = [
    "rsi14", "macd_line", "macd_histogram", "atr14", "adx14",
    "ema20_dist", "ema50_dist", "ema200_dist", "bb_pct_b",
    "relative_volume", "hour_of_day", "day_of_week",
    "prev_candle_body_ratio", "prev_candle_upper_wick", "prev_candle_lower_wick",
    "stoch_rsi_k", "stoch_rsi_d",
    "ema50_above_ema200", "macd_bullish", "adx_strong",
]


def _to_array(fv: FeatureVector) -> np.ndarray:
    values = [
        fv.rsi14 or 50, fv.macd_line or 0, fv.macd_histogram or 0,
        fv.atr14 or 0, fv.adx14 or 0,
        fv.ema20_dist or 0, fv.ema50_dist or 0, fv.ema200_dist or 0,
        fv.bb_pct_b or 0.5, fv.relative_volume or 1,
        fv.hour_of_day or 12, fv.day_of_week or 3,
        fv.prev_candle_body_ratio or 0.5,
        fv.prev_candle_upper_wick or 0.25,
        fv.prev_candle_lower_wick or 0.25,
        fv.stoch_rsi_k or 50, fv.stoch_rsi_d or 50,
        fv.ema50_above_ema200 or 0,
        fv.macd_bullish or 0,
        fv.adx_strong or 0,
    ]
    return np.array(values, dtype=np.float32).reshape(1, -1)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
