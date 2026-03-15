package com.tradingbot.trading.Bot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Swagger/OpenAPI spec exposed at {@code /swagger-ui.html}.
 *
 * <p>After starting the application, navigate to:
 * <ul>
 *   <li>Swagger UI: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON: <a href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradingBotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TradingBot API")
                        .version("1.0.0")
                        .description(
                                "Minimal, robust uptrend-breakout TradingBot. " +
                                "Uses RobustTrendBreakoutStrategy (MA20>MA50 + 20-bar close breakout + RSI>50). " +
                                "Quality gates: win rate ≥60%, profit factor ≥1.2, max drawdown ≤25%, trades ≥5.")
                        .contact(new Contact()
                                .name("TradingBot")
                                .url("https://github.com/ahmedelmemmi/TradingBot")));
    }
}
