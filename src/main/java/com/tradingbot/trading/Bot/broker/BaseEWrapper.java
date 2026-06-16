package com.tradingbot.trading.Bot.broker;

/**
 * Stub retained for reference. The IBKR EWrapper implementation requires the
 * TWS API jar (com.ib.client:ibapi) to be installed locally.
 *
 * <p>Install it with:
 * {@code mvn install:install-file -Dfile=TwsApi.jar
 *         -DgroupId=com.ib.client -DartifactId=ibapi
 *         -Dversion=10.37.02 -Dpackaging=jar}
 * </p>
 *
 * <p>This class is intentionally a no-op stub so that the project compiles
 * without the IBKR SDK on CI/CD environments. The full IBKR integration
 * has been superseded by the Pocket Option live-trading adapter.</p>
 */
public class BaseEWrapper {
    // Full IBKR implementation requires TWS API jar — see class Javadoc
}
