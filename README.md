# 📈 Automated Trading Bot (IBKR Paper Trading)

## 🚀 Overview

This project is a fully automated trading bot built with:

-   Java 17
-   Spring Boot 3
-   Interactive Brokers (IBKR) API
-   Maven
-   H2 (local persistence)

The bot connects to IBKR Paper Trading, retrieves historical market
data, computes trading signals (RSI or MACD-based strategies), applies
risk management rules, and executes trades automatically.

---

## 🏗 Architecture Overview

```
IBKR TWS / Gateway (Paper Account)
        ↓
IBKRPaperBrokerAdapter (EWrapper)
        ↓
Historical Data Polling (every 60 s)
        ↓
LiveCandleBuffer
        ↓
Strategy (RsiStrategyService | MacdStrategyService)
        ↓
TradeDecisionService
        ↓
RiskEngine + PortfolioRiskService
        ↓
Order Submission
        ↓
Execution Handling
        ↓
PositionManager + BrokerStateService + TradeRepository (H2)
```

---

## 📊 Strategies

### RSI Pullback Trend Strategy (`RsiStrategyService`)
Requires ≥ 60 candles.

| Condition | Value |
|---|---|
| MA20 > MA50 | trend must be up |
| Price below MA20 | pullback confirmed |
| RSI | 35 – 50 |

### MACD + RSI Confluence Strategy (`MacdStrategyService`)
Requires ≥ 60 candles.

| Condition | Description |
|---|---|
| MACD Histogram > 0 | bullish momentum crossover |
| RSI 40 – 60 | momentum confirmation (not over-extended) |
| Price > MA50 | trend filter |

### RSI Adaptive Backtest Strategy (`RSIBacktestStrategy`)
Adapts RSI thresholds based on the current market regime:

| Regime | RSI threshold |
|---|---|
| STRONG_UPTREND | < 45 + price above MA20 |
| SIDEWAYS | < 30 |
| HIGH_VOLATILITY | < 25 + price above MA20 |
| CRASH | < 20 + price above MA20 |
| STRONG_DOWNTREND | no trades |

---

## 📐 Technical Indicators

| Indicator | Class | Notes |
|---|---|---|
| RSI | `RsiCalculator` | 14-period Wilder RSI |
| ATR | `AtrCalculator` | 14-period Average True Range |
| MACD | `MacdCalculator` | EMA(12) − EMA(26), Signal EMA(9) |
| Moving Average | Inline helpers | SMA used for trend filters |

---

## 💰 Risk Management

| Parameter | Value |
|---|---|
| Stop Loss | 2% below entry (trailing) |
| Take Profit | 4% above entry |
| Risk per trade | 1% of account equity |
| Max open positions | 3 (live), 5 (portfolio) |
| Daily loss limit | 2% of starting balance |
| Crash regime | trading blocked |
| Drawdown > 10% | position size reduced to 50% |
| Drawdown > 20% | position size reduced to 25% |

Position sizing formula:
```
Quantity = (Account × 0.01) / |EntryPrice − StopLoss|
```

---

## 🌡 Market Regime Detection (`MarketRegimeService`)

Detected from price slope, momentum, volatility, and drawdown speed:

| Regime | Trigger |
|---|---|
| CRASH | drawdown > 12% over 10 candles |
| HIGH_VOLATILITY | average range > 1% per candle |
| STRONG_UPTREND | slope > 1.5% + positive momentum |
| STRONG_DOWNTREND | slope < -1.5% + negative momentum |
| SIDEWAYS | everything else |

---

## 🔁 Trading Flow

1. Request historical bars from IBKR (every 60 seconds)
2. Build candle batch and replace `LiveCandleBuffer`
3. Check open position exit conditions (trailing stop / take-profit)
4. If no position → evaluate strategy signal
5. Apply risk checks (daily loss limit, max positions, regime)
6. Calculate position size (ATR-based stop for portfolio mode)
7. Submit order to IBKR
8. Handle execution callback
9. Sync position state and persist trade to H2

---

## 🗄 Persistence

Trades are saved to an H2 file-based database (`./data/tradingdb`).  
Schema is auto-managed by Hibernate (`ddl-auto=update`).

Access the H2 console at: `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:file:./data/tradingdb`

---

## 🌐 REST API Endpoints

### Strategy / Decision

| Method | Path | Description |
|---|---|---|
| GET | `/test` | Evaluate RSI strategy on 50 random candles |

### RSI Backtests

| Method | Path | Scenario |
|---|---|---|
| GET | `/backtest/random` | Random price walk |
| GET | `/backtest/uptrend` | Strong uptrend |
| GET | `/backtest/crash` | Market crash |
| GET | `/backtest/volatile` | Sideways volatile |
| GET | `/backtest/portfolio` | Per-symbol random (AAPL, MSFT, NVDA, TSLA) |
| GET | `/backtest/portfolio/pro` | Multi-symbol with regime-aware risk |

### MACD Backtests

| Method | Path | Scenario |
|---|---|---|
| GET | `/backtest/macd/random` | Random price walk |
| GET | `/backtest/macd/uptrend` | Strong uptrend |
| GET | `/backtest/macd/crash` | Market crash |

### Broker / State

| Method | Path | Description |
|---|---|---|
| GET | `/broker-state` | IBKR broker position snapshot |
| GET | `/test-order` | Submit a paper test order |

### Market Regime

| Method | Path | Description |
|---|---|---|
| GET | `/regime-test` | Detect regime on downtrend candles |
| GET | `/regime-debug` | Debug metrics (slope, volatility) |

### Trade History

| Method | Path | Description |
|---|---|---|
| GET | `/trades` | All trades from H2 |
| GET | `/trades/open` | Only currently open trades |

---

### Backtest Response Fields

```json
{
  "startingCapital": 10000.00,
  "endingCapital":   10547.32,
  "totalTrades":     38,
  "winningTrades":   22,
  "losingTrades":    16,
  "totalPnL":        547.32,
  "winRate":         0.5789,
  "profitFactor":    1.84,
  "avgWin":          42.15,
  "avgLoss":         22.90,
  "expectancy":      11.21,
  "maxDrawdown":     0.0423
}
```

---

## ⚙️ Requirements

-   Java 17+
-   Maven 3.9+
-   Interactive Brokers TWS or IB Gateway (for live/paper trading)
-   IBKR Paper Account

---

## 🖥 Setup Instructions

### 1️⃣ Install the IBKR Java API JAR

The IBKR API is **not** available in Maven Central. Download it from
[Interactive Brokers API](https://interactivebrokers.github.io/) and
install it into your local Maven repository:

```bash
mvn install:install-file \
  -Dfile=TwsApi.jar \
  -DgroupId=com.ib.client \
  -DartifactId=ibapi \
  -Dversion=10.37.02 \
  -Dpackaging=jar
```

### 2️⃣ Configure TWS / IB Gateway

- Install IBKR TWS and log into Paper Trading
- Enable API:
  - Settings → API → Enable ActiveX and Socket Clients
  - Trusted IP: `127.0.0.1`
  - Port: `7497` (Paper)

### 3️⃣ Configure `application.properties`

```properties
ib.host=127.0.0.1
ib.port=7497
ib.clientId=1
```

### 4️⃣ Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

> **Note:** Set `AUTO_TRADING_ENABLED = true` in `IBKRPaperBrokerAdapter`
> to enable live order submission.

---

## ⚠ Disclaimer

Always test extensively in paper mode before going live.

---

## 👨‍💻 Author

Ahmed El Memmi  
Senior Fullstack Developer  
Spring Boot | Angular | AWS

