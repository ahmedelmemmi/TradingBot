package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Dynamic slippage model that simulates realistic execution costs.
 * Slippage scales with market volatility (ATR) and market regime.
 * Includes gap-risk simulation for a realistic backtest.
 */
@Service
public class SlippageService {

    private static final BigDecimal NORMAL_SLIPPAGE    = BigDecimal.valueOf(0.0003); // 3 bps
    private static final BigDecimal VOLATILE_SLIPPAGE  = BigDecimal.valueOf(0.0015); // 15 bps
    private static final BigDecimal CRASH_SLIPPAGE     = BigDecimal.valueOf(0.0050); // 50 bps

    /** Probability of an adverse gap event per trade (30 %) */
    private static final double GAP_RISK_PROBABILITY = 0.30;
    /** Additional slippage when a gap event fires (0.5 – 2 %) */
    private static final BigDecimal GAP_SLIPPAGE_MIN = BigDecimal.valueOf(0.005);
    private static final BigDecimal GAP_SLIPPAGE_MAX = BigDecimal.valueOf(0.020);

    private final Random random;

    public SlippageService() {
        this.random = new Random();
    }

    /**
     * Calculates realistic slippage for a trade fill.
     *
     * @param atr             14-period ATR at time of trade
     * @param price           current market price
     * @param regime          current market regime
     * @return total slippage fraction (multiply with price to get $ slippage)
     */
    public BigDecimal calculateSlippage(BigDecimal atr,
                                        BigDecimal price,
                                        MarketRegime regime) {

        BigDecimal baseSlippage = baseSlippage(regime);

        // Volatility scaling: if ATR > 1% of price, apply extra slippage
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal atrPct = atr.divide(price, 6, RoundingMode.HALF_UP);
            if (atrPct.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                // ATR-based additional slippage: scale linearly up to 3x
                BigDecimal scale = atrPct.divide(BigDecimal.valueOf(0.01), 6, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(3.0));
                baseSlippage = baseSlippage.multiply(scale).setScale(6, RoundingMode.HALF_UP);
            }
        }

        // Gap risk: 30% chance of extra 0.5–2% slippage
        if (random.nextDouble() < GAP_RISK_PROBABILITY) {
            double gapRange = GAP_SLIPPAGE_MAX.subtract(GAP_SLIPPAGE_MIN).doubleValue();
            BigDecimal gapSlippage = GAP_SLIPPAGE_MIN.add(
                    BigDecimal.valueOf(random.nextDouble() * gapRange));
            baseSlippage = baseSlippage.add(gapSlippage);

            System.out.println("[SlippageService] Gap event fired. Additional slippage="
                    + gapSlippage.setScale(4, RoundingMode.HALF_UP));
        }

        System.out.println("[SlippageService] regime=" + regime
                + " atr=" + (atr != null ? atr.setScale(4, RoundingMode.HALF_UP) : "N/A")
                + " totalSlippage=" + baseSlippage.setScale(6, RoundingMode.HALF_UP));

        return baseSlippage;
    }

    private BigDecimal baseSlippage(MarketRegime regime) {
        if (regime == null) return NORMAL_SLIPPAGE;
        return switch (regime) {
            case CRASH          -> CRASH_SLIPPAGE;
            case HIGH_VOLATILITY -> VOLATILE_SLIPPAGE;
            default             -> NORMAL_SLIPPAGE;
        };
    }
}
