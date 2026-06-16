# 📈 Automated Trading Bot (Pocket Option)

## 🚀 Overview

This project is a Spring Boot + Python ML trading bot focused on **Pocket Option workflows** (paper and live-mode ready architecture), with strategy evaluation, risk controls, and trade execution orchestration.

## ✅ Platform Scope

- Pocket Option only
- No legacy broker integration
- Yahoo Finance historical data for backtesting/paper diagnostics

## ⚙️ Run Locally

1. Copy `.env.example` to `.env` and set values.
2. Start dependencies:
   - `docker compose up -d`
3. Run Java service:
   - `./mvnw spring-boot:run`
4. (Optional) Run ML service:
   - `cd /home/runner/work/TradingBot/TradingBot/ml-service && python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt && uvicorn main:app --host 0.0.0.0 --port 8000`

## 🧪 Useful Endpoints

- `GET /paper-trade/pocket-option`
- `GET /paper-trade/small-capital`
- `GET /backtest/real/multi`
- `GET /broker-state`

## ⚠ Disclaimer

Use paper mode extensively before any live deployment.
