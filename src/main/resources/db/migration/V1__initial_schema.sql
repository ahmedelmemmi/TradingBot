-- ============================================================
-- V1: Initial schema for Pocket Option AI Trading Bot
-- ============================================================

-- ─── CANDLES ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS candles (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20)    NOT NULL,
    time_frame  VARCHAR(10)    NOT NULL,
    open_time   TIMESTAMPTZ    NOT NULL,
    open        NUMERIC(18,6)  NOT NULL,
    high        NUMERIC(18,6)  NOT NULL,
    low         NUMERIC(18,6)  NOT NULL,
    close       NUMERIC(18,6)  NOT NULL,
    volume      BIGINT         NOT NULL DEFAULT 0,
    session     VARCHAR(20),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (symbol, time_frame, open_time)
);
CREATE INDEX idx_candles_symbol_time ON candles (symbol, open_time DESC);

-- ─── INDICATORS ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS indicators (
    id              BIGSERIAL PRIMARY KEY,
    candle_id       BIGINT         REFERENCES candles(id) ON DELETE CASCADE,
    symbol          VARCHAR(20)    NOT NULL,
    time_frame      VARCHAR(10)    NOT NULL,
    calc_time       TIMESTAMPTZ    NOT NULL,
    ema_20          NUMERIC(18,6),
    ema_50          NUMERIC(18,6),
    ema_100         NUMERIC(18,6),
    ema_200         NUMERIC(18,6),
    rsi_14          NUMERIC(8,4),
    macd_line       NUMERIC(18,6),
    macd_signal     NUMERIC(18,6),
    macd_histogram  NUMERIC(18,6),
    stoch_rsi_k     NUMERIC(8,4),
    stoch_rsi_d     NUMERIC(8,4),
    atr_14          NUMERIC(18,6),
    bb_upper        NUMERIC(18,6),
    bb_middle       NUMERIC(18,6),
    bb_lower        NUMERIC(18,6),
    adx_14          NUMERIC(8,4),
    di_plus         NUMERIC(8,4),
    di_minus        NUMERIC(8,4),
    volume_ma_20    NUMERIC(18,2),
    relative_volume NUMERIC(8,4),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_indicators_symbol_time ON indicators (symbol, calc_time DESC);

-- ─── SIGNALS ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS signals (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20)    NOT NULL,
    time_frame      VARCHAR(10)    NOT NULL,
    signal_time     TIMESTAMPTZ    NOT NULL,
    direction       VARCHAR(4)     NOT NULL,     -- BUY | SELL
    strategy_name   VARCHAR(100)   NOT NULL,
    rule_score      NUMERIC(5,2),                -- % of rules passed
    price_at_signal NUMERIC(18,6),
    candle_pattern  VARCHAR(50),
    notes           TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_signals_symbol_time ON signals (symbol, signal_time DESC);

-- ─── ML PREDICTIONS ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS predictions (
    id               BIGSERIAL PRIMARY KEY,
    signal_id        BIGINT         REFERENCES signals(id),
    model_version    VARCHAR(50)    NOT NULL,
    predicted_at     TIMESTAMPTZ    NOT NULL,
    buy_probability  NUMERIC(5,4)   NOT NULL,
    sell_probability NUMERIC(5,4)   NOT NULL,
    confidence       NUMERIC(5,4)   NOT NULL,
    features_json    JSONB,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_predictions_signal ON predictions (signal_id);

-- ─── TRADES ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS trades (
    id               BIGSERIAL PRIMARY KEY,
    signal_id        BIGINT         REFERENCES signals(id),
    prediction_id    BIGINT         REFERENCES predictions(id),
    symbol           VARCHAR(20)    NOT NULL,
    direction        VARCHAR(4)     NOT NULL,     -- BUY | SELL
    entry_price      NUMERIC(18,6)  NOT NULL,
    entry_time       TIMESTAMPTZ    NOT NULL,
    expiry_seconds   INT            NOT NULL,
    stake_amount     NUMERIC(18,2)  NOT NULL,
    payout_percent   NUMERIC(5,2),
    mode             VARCHAR(10)    NOT NULL DEFAULT 'PAPER',  -- LIVE | PAPER
    broker_order_id  VARCHAR(100),
    status           VARCHAR(20)    NOT NULL DEFAULT 'OPEN',   -- OPEN | WIN | LOSS | EXPIRED
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trades_symbol_time ON trades (symbol, entry_time DESC);
CREATE INDEX idx_trades_status      ON trades (status);

-- ─── TRADE RESULTS ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS trade_results (
    id            BIGSERIAL PRIMARY KEY,
    trade_id      BIGINT         NOT NULL REFERENCES trades(id),
    exit_price    NUMERIC(18,6),
    exit_time     TIMESTAMPTZ,
    outcome       VARCHAR(10)    NOT NULL,  -- WIN | LOSS | EXPIRED
    profit_loss   NUMERIC(18,2)  NOT NULL,
    return_pct    NUMERIC(8,4),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_trade_results_trade ON trade_results (trade_id);

-- ─── MODEL VERSIONS ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS model_versions (
    id              BIGSERIAL PRIMARY KEY,
    version_tag     VARCHAR(50)    NOT NULL UNIQUE,
    algorithm       VARCHAR(50)    NOT NULL,  -- XGBOOST | LIGHTGBM | ENSEMBLE
    trained_at      TIMESTAMPTZ    NOT NULL,
    training_rows   INT,
    val_accuracy    NUMERIC(6,4),
    val_auc         NUMERIC(6,4),
    val_log_loss    NUMERIC(10,6),
    feature_importance_json JSONB,
    is_active       BOOLEAN        NOT NULL DEFAULT FALSE,
    notes           TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- ─── BACKTEST RESULTS ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backtest_results (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               VARCHAR(100)   NOT NULL UNIQUE,
    strategy_name        VARCHAR(100)   NOT NULL,
    symbol               VARCHAR(20)    NOT NULL,
    time_frame           VARCHAR(10)    NOT NULL,
    from_date            TIMESTAMPTZ    NOT NULL,
    to_date              TIMESTAMPTZ    NOT NULL,
    total_trades         INT            NOT NULL DEFAULT 0,
    winning_trades       INT            NOT NULL DEFAULT 0,
    losing_trades        INT            NOT NULL DEFAULT 0,
    win_rate             NUMERIC(6,4),
    profit_factor        NUMERIC(10,4),
    max_drawdown         NUMERIC(8,4),
    sharpe_ratio         NUMERIC(10,4),
    total_pnl            NUMERIC(18,2),
    max_consecutive_wins INT,
    max_consecutive_loss INT,
    parameters_json      JSONB,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_backtest_strategy ON backtest_results (strategy_name, created_at DESC);

-- ─── AUDIT LOGS ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)    NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    description TEXT,
    payload     JSONB,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_event_time ON audit_logs (event_type, created_at DESC);
