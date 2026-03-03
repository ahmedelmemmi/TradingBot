package com.tradingbot.trading.Bot.broker;

import com.ib.client.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

import static javax.management.remote.JMXConnectorFactory.connect;

@Service
public class IBKRPaperBrokerAdapter extends BaseEWrapper {
    private final AtomicInteger nextOrderId = new AtomicInteger();
    private final EJavaSignal signal = new EJavaSignal();
    private final EClientSocket client = new EClientSocket(this, signal);
    public IBKRPaperBrokerAdapter() {
        // DO NOTHING HERE
    }
//    @PostConstruct
//    public void init() {
//        connect();
//        startMessageProcessing();
//    }

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
    }

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

    public void submitMarketOrder(String symbol, double quantity) {

        if (nextOrderId.get() == 0) {
            System.out.println("Still waiting for nextValidId...");
            return;
        }

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        Order order = new Order();
        order.action("BUY");
        order.orderType("MKT");
        order.totalQuantity(Decimal.get(quantity));

        int orderId = nextOrderId.getAndIncrement();

        client.placeOrder(orderId, contract, order);

        System.out.println("Order sent. ID=" + orderId);
    }
}
