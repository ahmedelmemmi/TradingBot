package com.tradingbot.trading.Bot.broker;

import org.springframework.stereotype.Service;

@Service
public class IBKRPaperBrokerAdapter implements EWrapper{
    private final EClientSocket client;
    private int orderId = 1;

    public IBKRPaperBrokerAdapter() {
        this.client = new EClientSocket(this);
        connect();
    }

    private void connect() {
        // host, port, clientId
        client.eConnect("127.0.0.1", 7497, 0); // Paper trading default port
        System.out.println("[IBKR] Connected to IBKR TWS / Gateway paper account");
    }

    public boolean submitOrder(TradeDecision decision) {
        if (!decision.isExecute()) return false;

        Contract contract = createContract(decision.getSymbol());
        Order order = createOrder(decision);

        System.out.println("[IBKR] Submitting order: " + decision.getSymbol() +
                " Qty: " + decision.getQuantity() +
                " Entry: " + decision.getEntryPrice());

        client.placeOrder(orderId++, contract, order);

        return true;
    }

    private Contract createContract(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        return contract;
    }

    private Order createOrder(TradeDecision decision) {
        Order order = new Order();
        order.action("BUY");
        order.orderType("MKT");
        order.totalQuantity(decision.getQuantity().doubleValue());
        return order;
    }

    // EWrapper methods (empty implementations for now)
    @Override public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {}
    @Override public void tickSize(int tickerId, int field, int size) {}
    @Override public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
                                                double optPrice, double pvDividend, double gamma,
                                                double vega, double theta, double undPrice) {}
    @Override public void tickGeneric(int tickerId, int tickType, double value) {}
    @Override public void tickString(int tickerId, int tickType, String value) {}
    @Override public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
                                  double totalDividends, int holdDays, String futureExpiry, double dividendImpact,
                                  double dividendsToExpiry) {}
    @Override public void orderStatus(int orderId, String status, double filled, double remaining,
                                      double avgFillPrice, int permId, int parentId, double lastFillPrice,
                                      int clientId, String whyHeld, double mktCapPrice) {}
    @Override public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {}
    @Override public void openOrderEnd() {}
    @Override public void updateAccountValue(String key, String value, String currency, String accountName) {}
    @Override public void updatePortfolio(Contract contract, double position, double marketPrice,
                                          double marketValue, double averageCost, double unrealizedPNL,
                                          double realizedPNL, String accountName) {}
    @Override public void updateAccountTime(String timeStamp) {}
    @Override public void accountDownloadEnd(String accountName) {}
    @Override public void nextValidId(int orderId) {}
    @Override public void contractDetails(int reqId, ContractDetails contractDetails) {}
    @Override public void bondContractDetails(int reqId, ContractDetails contractDetails) {}
    @Override public void contractDetailsEnd(int reqId) {}
    @Override public void execDetails(int reqId, Contract contract, Execution execution) {}
    @Override public void execDetailsEnd(int reqId) {}
    @Override public void error(Exception e) { e.printStackTrace(); }
    @Override public void error(String str) { System.err.println(str); }
    @Override public void error(int id, int errorCode, String errorMsg) {
        System.err.println("Error: " + id + " Code: " + errorCode + " Msg: " + errorMsg);
    }
    @Override public void connectionClosed() {}
    @Override public void updateMktDepth(int tickerId, int position, int operation, int side, double price,
                                         int size) {}
    @Override public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation,
                                           int side, double price, int size, boolean isSmartDepth) {}
    @Override public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {}
    @Override public void managedAccounts(String accountsList) {}
    @Override public void receiveFA(int faDataType, String xml) {}
    @Override public void historicalData(int reqId, Bar bar) {}
    @Override public void scannerParameters(String xml) {}
    @Override public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
                                      String benchmark, String projection, String legsStr) {}
    @Override public void scannerDataEnd(int reqId) {}
    @Override public void realtimeBar(int reqId, long time, double open, double high, double low,
                                      double close, long volume, double wap, int count) {}
    @Override public void currentTime(long time) {}
    @Override public void fundamentalData(int reqId, String data) {}
    @Override public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {}
    @Override public void tickSnapshotEnd(int reqId) {}
    @Override public void marketDataType(int reqId, int marketDataType) {}
    @Override public void commissionReport(CommissionReport commissionReport) {}
    @Override public void position(String account, Contract contract, double pos, double avgCost) {}
    @Override public void positionEnd() {}
    @Override public void accountSummary(int reqId, String account, String tag, String value, String currency) {}
    @Override public void accountSummaryEnd(int reqId) {}
    @Override public void verifyMessageAPI(String apiData) {}
    @Override public void verifyCompleted(boolean isSuccessful, String errorText) {}
    @Override public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {}
    @Override public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {}
    @Override public void displayGroupList(int reqId, String groups) {}
    @Override public void displayGroupUpdated(int reqId, String contractInfo) {}
    @Override public void connectAck() {
        if (client.isAsyncEConnect()) client.startAPI();
    }
}
