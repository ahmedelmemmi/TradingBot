package com.tradingbot.trading.Bot.broker;

import com.ib.client.*;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.market.LiveCandleBuffer;
import com.tradingbot.trading.Bot.position.PositionManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Service
public class IBKRPaperBrokerAdapter extends BaseEWrapper {
    private static final boolean AUTO_TRADING_ENABLED = false;
    private final AtomicInteger completedRequests = new AtomicInteger();
    private final AtomicInteger nextOrderId = new AtomicInteger();
    private final AtomicInteger requestIdGenerator = new AtomicInteger(4000);

    private final EJavaSignal signal = new EJavaSignal();
    private final EClientSocket client = new EClientSocket(this, signal);

    private final BrokerStateService brokerStateService;
    private final PositionManager positionManager;
    private final TradeDecisionService tradeDecisionService;

    private final Map<Integer, TradeDecision> pendingOrders = new ConcurrentHashMap<>();

//     🔹 Multi symbol support
    private final List<String> symbols = List.of(
            "AAPL",
            "MSFT",
            "NVDA",
            "TSLA"
    );
//    private final List<String> symbols = List.of("AAPL");

    private final Map<String, LiveCandleBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<Integer, String> requestToSymbol = new ConcurrentHashMap<>();
    private final Map<String, List<Candle>> currentBatches = new ConcurrentHashMap<>();


    public IBKRPaperBrokerAdapter(
            BrokerStateService brokerStateService,
            PositionManager positionManager,
            TradeDecisionService tradeDecisionService) {

        this.brokerStateService = brokerStateService;
        this.positionManager = positionManager;
        this.tradeDecisionService = tradeDecisionService;

        for (String symbol : symbols) {
            buffers.put(symbol, new LiveCandleBuffer());
            currentBatches.put(symbol, new ArrayList<>());
        }
    }

    @PostConstruct
    public void init() {
        connect();
        startMessageProcessing();
    }

    private void connect() {

        client.eConnect("127.0.0.1", 7497, 0);

        if (client.isConnected()) {
            System.out.println("Connected to IBKR Paper Account");
        } else {
            System.out.println("Connection failed");
        }
    }

    private void startMessageProcessing() {

        final EReader reader = new EReader(client, signal);
        reader.start();

        new Thread(() -> {
            while (client.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void nextValidId(int orderId) {

        System.out.println("Next Valid ID: " + orderId);

        nextOrderId.set(orderId);

        requestInitialAccountData();
// Add delay before requesting bars
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        requestHistoricalBars();
    }

    private void requestInitialAccountData() {

        System.out.println("Requesting account summary...");
        client.reqAccountSummary(1001, "All", "$LEDGER");

        System.out.println("Requesting positions...");
        client.reqPositions();
    }

    public void requestHistoricalBars() {

        client.reqMarketDataType(3);

        for (String symbol : symbols) {

            int requestId = requestIdGenerator.incrementAndGet();

            requestToSymbol.put(requestId, symbol);

            Contract contract = new Contract();
            contract.symbol(symbol);
            contract.secType("STK");
            contract.currency("USD");
            contract.exchange("SMART");
            contract.primaryExch("NASDAQ");
            System.out.println("📤 Requesting historical data for " + symbol + " (reqId=" + requestId + ")");

            client.reqHistoricalData(
                    requestId,
                    contract,
                    "",
                    "1800 S",
                    "1 min",
                    "TRADES",
                    1,
                    1,
                    false,
                    null
            );

            System.out.println("Historical bars requested for " + symbol + " (reqId=" + requestId + ")");
        }
    }
    @Override
    public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        System.err.println("❌ IB ERROR [" + errorCode + "] " + errorMsg + " (reqId: " + id + ")");

        // Check if error is related to historical data request
        if (requestToSymbol.containsKey(id)) {
            System.err.println("   This error is for historical data request of: " + requestToSymbol.get(id));
        }
    }
    @Override
    public void historicalData(int reqId, Bar bar) {
        String symbol = requestToSymbol.get(reqId);
        System.out.println("🔹 Bar received for " + symbol + " - Close: " + bar.close() + " Volume: " + bar.volume());

        if (symbol == null) {
            System.out.println("⚠️ Symbol not found for reqId: " + reqId);
            return;
        }

        if (bar == null) {
            System.out.println("⚠️ Bar is null for " + symbol);
            return;
        }

        if (Double.isNaN(bar.close()) || bar.close() <= 0) {
            System.out.println("⚠️ Invalid bar data for " + symbol);
            return;
        }

        Candle candle = new Candle(
                symbol,
                LocalDateTime.now(),
                BigDecimal.valueOf(bar.open()),
                BigDecimal.valueOf(bar.high()),
                BigDecimal.valueOf(bar.low()),
                BigDecimal.valueOf(bar.close()),
                bar.volume().longValue()
        );

        currentBatches.get(symbol).add(candle);
    }

    @Override
    public void historicalDataEnd(int reqId, String start, String end) {
        String symbol = requestToSymbol.get(reqId);

        if (symbol == null) return;

        System.out.println("=== historicalDataEnd triggered for " + symbol + " ===");

        LiveCandleBuffer buffer = buffers.get(symbol);
        buffer.clear();

        for (Candle candle : currentBatches.get(symbol)) {
            buffer.add(candle);
        }

        currentBatches.get(symbol).clear();

        System.out.println("Candles loaded for " + symbol + ": " + buffer.size());

        if (!buffer.isReady()) {
            System.out.println("Buffer not ready for " + symbol);
            return;
        }

        checkExitConditions(symbol, buffer);

        TradeDecision decision = tradeDecisionService.evaluate(symbol, buffer.getCandles());

        if (AUTO_TRADING_ENABLED && decision.isExecute()) {
            submitOrder(decision);
        }

        // Track completed symbols
        int finished = completedRequests.incrementAndGet();

        System.out.println("Completed: " + finished + "/" + symbols.size());

        if (finished >= symbols.size()) {
            completedRequests.set(0);
            System.out.println("=== All symbols processed. Waiting before next cycle ===");

            // Add a delay before requesting again (e.g., 5 seconds)
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // Wait 5 seconds
                    requestHistoricalBars();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private void checkExitConditions(String symbol, LiveCandleBuffer buffer) {

        var optionalPosition = positionManager.getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) {
            return;
        }

        var position = optionalPosition.get();

        var candles = buffer.getCandles();

        if (candles.isEmpty()) {
            return;
        }

        BigDecimal currentPrice =
                candles.get(candles.size() - 1).getClose();

        position.updateTrailingStop(currentPrice);

        if (currentPrice.compareTo(position.getStopLoss()) <= 0) {

            System.out.println(symbol + " STOP LOSS HIT");

            submitSellOrder(symbol, position.getQuantity());

            return;
        }

        if (currentPrice.compareTo(position.getTakeProfit()) >= 0) {

            System.out.println(symbol + " TAKE PROFIT HIT");

            submitSellOrder(symbol, position.getQuantity());
        }
    }

    public void submitOrder(TradeDecision decision) {

        if (nextOrderId.get() == 0) {
            return;
        }

        Contract contract = new Contract();
        contract.symbol(decision.getSymbol());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        contract.primaryExch("NASDAQ");

        Order order = new Order();
        order.action("BUY");
        order.orderType("MKT");
        order.totalQuantity(Decimal.get(decision.getQuantity().doubleValue()));

        int orderId = nextOrderId.getAndIncrement();

        pendingOrders.put(orderId, decision);

        client.placeOrder(orderId, contract, order);

        System.out.println("BUY order sent for " + decision.getSymbol());
    }

    private void submitSellOrder(String symbol, BigDecimal quantity) {

        if (nextOrderId.get() == 0) return;

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        contract.primaryExch("NASDAQ");

        Order order = new Order();
        order.action("SELL");
        order.orderType("MKT");
        order.totalQuantity(Decimal.get(quantity.doubleValue()));

        int orderId = nextOrderId.getAndIncrement();

        client.placeOrder(orderId, contract, order);

        System.out.println("SELL order sent for " + symbol);
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {

        int orderId = execution.orderId();
        String symbol = contract.symbol();
        String side = execution.side();

        BigDecimal fillPrice = BigDecimal.valueOf(execution.price());
        BigDecimal quantity = new BigDecimal(execution.shares().toString());

        if ("BOT".equalsIgnoreCase(side) || "BUY".equalsIgnoreCase(side)) {

            TradeDecision decision = pendingOrders.get(orderId);

            if (decision == null) return;

            Position position = new Position(
                    symbol,
                    fillPrice,
                    quantity,
                    decision.getStopLoss(),
                    decision.getTakeProfit()
            );

            positionManager.openPosition(position);

            pendingOrders.remove(orderId);
        }

        if ("SLD".equalsIgnoreCase(side) || "SELL".equalsIgnoreCase(side)) {

            positionManager.closePosition(symbol, fillPrice);
        }
    }

    @Override
    public void accountSummary(int reqId,
                               String account,
                               String tag,
                               String value,
                               String currency) {

        if ("CashBalance".equals(tag)) {
            brokerStateService.updateCash(value);
        }

        if ("NetLiquidation".equals(tag)) {
            brokerStateService.updateNetLiquidation(value);
        }
    }

    @Override
    public void position(String account,
                         Contract contract,
                         Decimal pos,
                         double avgCost) {

        BigDecimal quantity = new BigDecimal(pos.toString());

        brokerStateService.updatePosition(contract.symbol(), quantity);

        System.out.println("Synced Position: " + contract.symbol() + " Qty: " + quantity);
    }

    @Override
    public void positionEnd() {
        System.out.println("Position stream completed.");
    }

    @Override
    public void connectionClosed() {
        System.out.println("Connection lost!");
    }

    public boolean isConnected() {
        return client.isConnected();
    }
}
