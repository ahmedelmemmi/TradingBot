"""
Model trainer – trains XGBoost, LightGBM, or an ensemble on trade history.

Data source priority:
  1. PostgreSQL (trades + trade_results tables) when DB_URL is supplied.
  2. Synthetic data generated from rule-based heuristics (fallback / demo).
"""

import logging
import os
from datetime import datetime, timezone

import joblib
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.ensemble import VotingClassifier
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier

logger = logging.getLogger(__name__)

FEATURE_NAMES = [
    "rsi14", "macd_line", "macd_histogram", "atr14", "adx14",
    "ema20_dist", "ema50_dist", "ema200_dist", "bb_pct_b",
    "relative_volume", "hour_of_day", "day_of_week",
    "prev_candle_body_ratio", "prev_candle_upper_wick", "prev_candle_lower_wick",
    "stoch_rsi_k", "stoch_rsi_d",
    "ema50_above_ema200", "macd_bullish", "adx_strong",
]


class ModelTrainer:
    def __init__(self, models_dir: str):
        self.models_dir = models_dir

    def train(self, db_url: str = None, min_samples: int = 500,
              test_size: float = 0.2, algorithm: str = "ensemble") -> dict:

        X, y = self._load_data(db_url, min_samples)

        if len(X) < min_samples:
            logger.warning(
                "Only %d samples available (min=%d). Using synthetic data.", len(X), min_samples
            )
            X, y = self._generate_synthetic_data(min_samples)

        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=42, stratify=y
        )

        model = self._build_model(algorithm)
        model.fit(X_train, y_train)

        y_pred       = model.predict(X_test)
        y_proba      = model.predict_proba(X_test)[:, 1]
        val_accuracy = float(accuracy_score(y_test, y_pred))
        val_auc      = float(roc_auc_score(y_test, y_proba))

        version    = f"v{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}"
        model_path = os.path.join(self.models_dir, f"{version}.joblib")

        feature_importance = self._get_feature_importance(model, algorithm)

        joblib.dump(
            {
                "model":             model,
                "algorithm":         algorithm,
                "feature_names":     FEATURE_NAMES,
                "val_accuracy":      val_accuracy,
                "val_auc":           val_auc,
                "training_rows":     len(X_train),
                "feature_importance": feature_importance,
                "trained_at":        datetime.now(timezone.utc).isoformat(),
            },
            model_path,
        )
        logger.info("Model saved: %s (AUC=%.4f)", model_path, val_auc)

        return {
            "version":       version,
            "algorithm":     algorithm,
            "training_rows": len(X_train),
            "val_accuracy":  val_accuracy,
            "val_auc":       val_auc,
        }

    # ── Model construction ───────────────────────────────────────────────────

    def _build_model(self, algorithm: str):
        xgb = XGBClassifier(
            n_estimators=300,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            use_label_encoder=False,
            eval_metric="logloss",
            random_state=42,
            n_jobs=-1,
        )
        lgbm = lgb.LGBMClassifier(
            n_estimators=300,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            random_state=42,
            n_jobs=-1,
            verbose=-1,
        )

        if algorithm == "xgboost":
            return xgb
        if algorithm == "lightgbm":
            return lgbm
        # ensemble (default)
        return VotingClassifier(
            estimators=[("xgb", xgb), ("lgbm", lgbm)],
            voting="soft",
        )

    # ── Data loading ─────────────────────────────────────────────────────────

    def _load_data(self, db_url: str, min_samples: int):
        if not db_url:
            return pd.DataFrame(), pd.Series(dtype=int)

        try:
            from sqlalchemy import create_engine, text

            engine = create_engine(db_url)
            query = text("""
                SELECT
                    i.rsi_14           AS rsi14,
                    i.macd_line,
                    i.macd_histogram,
                    i.atr_14           AS atr14,
                    i.adx_14           AS adx14,
                    (t.entry_price - i.ema_20) / NULLIF(i.ema_20, 0)   AS ema20_dist,
                    (t.entry_price - i.ema_50) / NULLIF(i.ema_50, 0)   AS ema50_dist,
                    (t.entry_price - i.ema_200) / NULLIF(i.ema_200, 0) AS ema200_dist,
                    CASE WHEN (i.bb_upper - i.bb_lower) > 0
                         THEN (t.entry_price - i.bb_lower) / (i.bb_upper - i.bb_lower)
                         ELSE 0.5 END                                  AS bb_pct_b,
                    i.relative_volume,
                    EXTRACT(HOUR FROM t.entry_time)   AS hour_of_day,
                    EXTRACT(DOW  FROM t.entry_time)   AS day_of_week,
                    0.5 AS prev_candle_body_ratio,
                    0.25 AS prev_candle_upper_wick,
                    0.25 AS prev_candle_lower_wick,
                    i.stoch_rsi_k,
                    i.stoch_rsi_d,
                    CASE WHEN i.ema_50 > i.ema_200 THEN 1 ELSE 0 END  AS ema50_above_ema200,
                    CASE WHEN i.macd_histogram > 0   THEN 1 ELSE 0 END AS macd_bullish,
                    CASE WHEN i.adx_14 > 25          THEN 1 ELSE 0 END AS adx_strong,
                    CASE WHEN r.outcome = 'WIN'       THEN 1 ELSE 0 END AS label
                FROM trades t
                JOIN signals s   ON s.id = t.signal_id
                JOIN indicators i ON i.symbol = t.symbol
                    AND i.time_frame = '1m'
                    AND i.calc_time <= t.entry_time
                JOIN trade_results r ON r.trade_id = t.id
                WHERE t.status <> 'OPEN'
                ORDER BY t.entry_time DESC
                LIMIT 50000
            """)
            df = pd.read_sql(query, engine)
            engine.dispose()

            if df.empty:
                return pd.DataFrame(), pd.Series(dtype=int)

            X = df[FEATURE_NAMES].fillna(0)
            y = df["label"].astype(int)
            return X, y

        except Exception as exc:
            logger.error("DB data load failed: %s", exc)
            return pd.DataFrame(), pd.Series(dtype=int)

    # ── Synthetic data for cold-start / demo ─────────────────────────────────

    def _generate_synthetic_data(self, n: int = 1000):
        rng = np.random.default_rng(42)

        # Create a balanced dataset with some signal
        X = pd.DataFrame({
            "rsi14":                  rng.uniform(20, 80, n),
            "macd_line":              rng.normal(0, 0.001, n),
            "macd_histogram":         rng.normal(0, 0.0005, n),
            "atr14":                  rng.uniform(0.0005, 0.005, n),
            "adx14":                  rng.uniform(10, 60, n),
            "ema20_dist":             rng.normal(0, 0.005, n),
            "ema50_dist":             rng.normal(0, 0.01, n),
            "ema200_dist":            rng.normal(0, 0.02, n),
            "bb_pct_b":               rng.uniform(0, 1, n),
            "relative_volume":        rng.uniform(0.3, 3.0, n),
            "hour_of_day":            rng.integers(0, 24, n),
            "day_of_week":            rng.integers(1, 6, n),
            "prev_candle_body_ratio": rng.uniform(0, 1, n),
            "prev_candle_upper_wick": rng.uniform(0, 0.5, n),
            "prev_candle_lower_wick": rng.uniform(0, 0.5, n),
            "stoch_rsi_k":            rng.uniform(0, 100, n),
            "stoch_rsi_d":            rng.uniform(0, 100, n),
            "ema50_above_ema200":     rng.integers(0, 2, n),
            "macd_bullish":           rng.integers(0, 2, n),
            "adx_strong":             rng.integers(0, 2, n),
        })

        # Synthesise a label that correlates with the signals
        score = (
            (X["rsi14"] > 55).astype(float) * 0.2
            + (X["macd_histogram"] > 0).astype(float) * 0.2
            + (X["adx14"] > 25).astype(float) * 0.15
            + (X["ema50_above_ema200"] == 1).astype(float) * 0.2
            + rng.uniform(0, 0.25, n)
        )
        y = (score >= 0.55).astype(int)
        return X, y

    # ── Feature importance ───────────────────────────────────────────────────

    def _get_feature_importance(self, model, algorithm: str) -> dict:
        try:
            if algorithm == "xgboost" and hasattr(model, "feature_importances_"):
                return dict(zip(FEATURE_NAMES, model.feature_importances_.tolist()))
            if algorithm == "lightgbm" and hasattr(model, "feature_importances_"):
                return dict(zip(FEATURE_NAMES, model.feature_importances_.tolist()))
            if algorithm == "ensemble":
                # Average importances from both estimators
                xgb_imp  = model.estimators_[0].feature_importances_
                lgbm_imp = model.estimators_[1].feature_importances_
                avg = ((xgb_imp + lgbm_imp) / 2).tolist()
                return dict(zip(FEATURE_NAMES, avg))
        except Exception:
            pass
        return {}
