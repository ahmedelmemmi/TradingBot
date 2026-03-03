package com.tradingbot.trading.Bot.broker;

import com.ib.client.*;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.market.LiveCandleBuffer;
import com.tradingbot.trading.Bot.position.Position;
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
    private final AtomicInteger nextOrderId = new AtomicInteger();
    private final EJavaSignal signal = new EJavaSignal();
    private final EClientSocket client = new EClientSocket(this, signal);
    private final BrokerStateService brokerStateService;
    private final PositionManager positionManager;
    private final Map<Integer, TradeDecision> pendingOrders = new ConcurrentHashMap<>();
    private final LiveCandleBuffer liveCandleBuffer;
    private final TradeDecisionService tradeDecisionService;
    private final List<Candle> currentBatch = new ArrayList<>();

    private static final boolean ALLOW_LIVE_TRADING = true;
    public IBKRPaperBrokerAdapter(BrokerStateService brokerStateService, PositionManager positionManager, LiveCandleBuffer liveCandleBuffer, TradeDecisionService tradeDecisionService) {
        // DO NOTHING HERE
        this.brokerStateService = brokerStateService;
        this.positionManager = positionManager;
        this.liveCandleBuffer = liveCandleBuffer;
        this.tradeDecisionService = tradeDecisionService;
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
//        client.reqMarketDataType(3);
//        startMarketDataStream();
    }
    private void requestInitialAccountData() {

        System.out.println("Requesting account summary...");
        client.reqAccountSummary(1001, "All", "$LEDGER");

        System.out.println("Requesting positions...");
        client.reqPositions();
    }

//    private void startMarketDataStream() {
//
//        Contract contract = new Contract();
//        contract.symbol("AAPL"); // start with one symbol
//        contract.secType("STK");
//        contract.currency("USD");
//        contract.exchange("SMART");
//
//        client.reqRealTimeBars(
//                5001,           // request id
//                contract,
//                5,              // 5-second bars
//                "TRADES",
//                true,
//                null
//        );
//
//        System.out.println("Realtime bars requested...");
//    }

//    @Override
//    public void realtimeBar(int reqId,
//                            long time,
//                            double open,
//                            double high,
//                            double low,
//                            double close,
//                            Decimal volume,
//                            Decimal wap,
//                            int count) {
//
//        System.out.println("Bar close: " + close);
//
//        // We will push this into strategy engine next
//    }

    @Override
    public void orderStatus(int orderId,
                            String status,
                            Decimal filled,
                            Decimal remaining,
                            double avgFillPrice,
                            long permId,
                            int parentId,
                            double lastFillPrice,
                            int clientId,
                            String whyHeld,
                            double mktCapPrice) {

        System.out.println("Order " + orderId + " status: " + status);
    }

    @Override
    public void error(int id, long errorTime, int errorCode,
                      String errorMsg, String advancedOrderRejectJson) {

        System.err.println("IB ERROR " + errorCode + ": " + errorMsg);
    }

    public void submitOrder(TradeDecision decision) {

        if (nextOrderId.get() == 0) {
            System.out.println("Still waiting for nextValidId...");
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

        System.out.println("Order sent. ID=" + orderId);
    }
    @Override
    public void execDetails(int reqId,
                            Contract contract,
                            Execution execution) {

        int orderId = execution.orderId();
        String symbol = contract.symbol();
        String side = execution.side();

        BigDecimal fillPrice = BigDecimal.valueOf(execution.price());
        BigDecimal quantity =
                new BigDecimal(execution.shares().toString());

        System.out.println("EXECUTION RECEIVED:");
        System.out.println("Symbol: " + symbol);
        System.out.println("Side: " + side);
        System.out.println("Quantity: " + quantity);
        System.out.println("Fill Price: " + fillPrice);

        // =====================
        // BUY EXECUTION
        // =====================
        if ("BOT".equalsIgnoreCase(side) ||
                "BUY".equalsIgnoreCase(side)) {

            TradeDecision decision = pendingOrders.get(orderId);

            if (decision == null) {
                System.out.println("No pending decision for BUY orderId=" + orderId);
                return;
            }

            Position position = new Position(
                    symbol,
                    fillPrice,
                    quantity,
                    decision.getStopLoss(),
                    decision.getTakeProfit()
            );

            positionManager.openPosition(position);

            System.out.println("Position opened AFTER BUY fill: " + symbol);

            pendingOrders.remove(orderId);
            return;
        }

        // =====================
        // SELL EXECUTION
        // =====================
        if ("SLD".equalsIgnoreCase(side) ||
                "SELL".equalsIgnoreCase(side)) {

            positionManager.closePosition(symbol, fillPrice);

            System.out.println("Position closed AFTER SELL fill: " + symbol);

            return;
        }

        System.out.println("Unknown execution side: " + side);
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

        System.out.println(tag + " = " + value);
    }

    @Override
    public void position(String account,
                         Contract contract,
                         Decimal pos,
                         double avgCost) {

        BigDecimal quantity = new BigDecimal(pos.toString());

        brokerStateService.updatePosition(contract.symbol(), quantity);

        System.out.println("Synced Position: " + contract.symbol() +
                " Qty: " + quantity);
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

    public void requestHistoricalBars() {

        // Force delayed data (important for paper account without subscription)
        client.reqMarketDataType(3);

        Contract contract = new Contract();
        contract.symbol("AAPL");
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("NASDAQ");
        contract.primaryExch("NASDAQ");

        client.reqHistoricalData(
                4001,
                contract,
                null,          // 🔥 IMPORTANT: use null instead of ""
                "2 D",
                "1 min",
                "TRADES",
                1,
                1,
                false,
                null
        );

        System.out.println("Historical bars requested...");
    }

    @Override
    public void historicalData(int reqId, Bar bar) {

        System.out.println("historicalData callback received");

        if (bar == null) return;

        if (Double.isNaN(bar.close()) || bar.close() <= 0) return;

        Candle candle = new Candle(
                "AAPL",
                LocalDateTime.now(),
                BigDecimal.valueOf(bar.open()),
                BigDecimal.valueOf(bar.high()),
                BigDecimal.valueOf(bar.low()),
                BigDecimal.valueOf(bar.close()),
                bar.volume().longValue()
        );

        currentBatch.add(candle);
    }

    @Override
    public void historicalDataEnd(int reqId, String start, String end) {

        System.out.println("=== historicalDataEnd triggered ===");

        // Replace buffer atomically
        liveCandleBuffer.clear();

        for (Candle candle : currentBatch) {
            liveCandleBuffer.add(candle);
        }

        System.out.println("Candles loaded into buffer: " + liveCandleBuffer.size());

        currentBatch.clear();

        if (!liveCandleBuffer.isReady()) {
            System.out.println("Buffer not ready for RSI.");
            return;
        }
        checkExitConditions("AAPL");
        TradeDecision decision =
                tradeDecisionService.evaluate(
                        "AAPL",
                        liveCandleBuffer.getCandles()
                );

        if (AUTO_TRADING_ENABLED && decision.isExecute()) {
            submitOrder(decision);
        }
    }

    private void checkExitConditions(String symbol) {

        var optionalPosition = positionManager.getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) {
            return;
        }

        var position = optionalPosition.get();

        var candles = liveCandleBuffer.getCandles();
        if (candles.isEmpty()) {
            return;
        }

        BigDecimal currentPrice =
                candles.get(candles.size() - 1).getClose();

        if (currentPrice.compareTo(position.getStopLoss()) <= 0) {

            System.out.println("STOP LOSS HIT. Closing position.");

            submitSellOrder(symbol, position.getQuantity());

            return;
        }

        if (currentPrice.compareTo(position.getTakeProfit()) >= 0) {

            System.out.println("TAKE PROFIT HIT. Closing position.");

            submitSellOrder(symbol, position.getQuantity());
        }
    }
    private void submitSellOrder(String symbol, BigDecimal quantity) {

        if (nextOrderId.get() == 0) {
            return;
        }

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

        System.out.println("SELL order sent. ID=" + orderId);
    }
}
