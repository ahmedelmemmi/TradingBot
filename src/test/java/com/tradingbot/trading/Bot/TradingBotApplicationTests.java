package com.tradingbot.trading.Bot;

import com.tradingbot.trading.Bot.domain.TradeLog;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TradingBotApplicationTests {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void contextLoads() {
	}

	@Test
	void tradeLogIsNotManagedByJpa() {
		assertThat(entityManagerFactory.getMetamodel().getManagedTypes())
				.noneMatch(type -> type.getJavaType().equals(TradeLog.class));
	}

}
