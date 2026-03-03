# 📈 Automated Trading Bot (IBKR Paper Trading)

## 🚀 Overview

This project is a fully automated trading bot built with:

-   Java 17
-   Spring Boot
-   Interactive Brokers (IBKR) API
-   Maven
-   H2 (optional local persistence)

The bot connects to IBKR Paper Trading, retrieves historical market
data, computes an RSI-based trading strategy, applies risk management
rules, and executes trades automatically.

------------------------------------------------------------------------

## 🏗 Architecture Overview

IBKR TWS / Gateway (Paper Account) ↓ IBKRPaperBrokerAdapter (EWrapper) ↓
Historical Data Polling ↓ LiveCandleBuffer ↓ RsiStrategyService ↓
TradeDecisionService ↓ RiskEngine ↓ Order Submission ↓ Execution
Handling ↓ PositionManager + BrokerStateService

------------------------------------------------------------------------

## 📊 Strategy

RSI Strategy (1-minute candles)

-   BUY when RSI \< 30
-   HOLD otherwise

Indicator: - 14-period RSI calculated on latest candles

------------------------------------------------------------------------

## 💰 Risk Management

-   Stop Loss: 2%
-   Take Profit: 4%
-   Position sizing based on:
    -   Account balance
    -   Risk per trade
    -   Stop-loss distance

Duplicate Protection: - No new trade if broker already holds position -
No duplicate local positions

------------------------------------------------------------------------

## 🔁 Trading Flow

1.  Request historical bars from IBKR
2.  Build candle batch
3.  Replace buffer
4.  Check open position (exit logic)
5.  If no position → evaluate strategy
6.  If BUY → calculate position size
7.  Submit order to IBKR
8.  Handle execution callback
9.  Sync position state

------------------------------------------------------------------------

## ⚙️ Requirements

-   Java 17+
-   Maven
-   Interactive Brokers TWS or IB Gateway
-   IBKR Paper Account

------------------------------------------------------------------------

## 🖥 Setup Instructions

### 1️⃣ Install TWS

-   Install IBKR TWS
-   Log into Paper Trading
-   Enable API:
    -   Settings → API → Enable ActiveX and Socket Clients
    -   Trusted IP: 127.0.0.1
    -   Port: 7497 (Paper)

### 2️⃣ Configure application.properties

ib.host=127.0.0.1 ib.port=7497 ib.clientId=1

### 3️⃣ Build and Run

mvn clean install mvn spring-boot:run

------------------------------------------------------------------------

## ⚠ Disclaimer

Always test extensively in paper mode before going live.

------------------------------------------------------------------------

## 👨‍💻 Author

Ahmed El Memmi\
Senior Fullstack Developer\
Spring Boot \| Angular \| AWS
