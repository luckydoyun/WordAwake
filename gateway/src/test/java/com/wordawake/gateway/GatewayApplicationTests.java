package com.wordawake.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** 컨텍스트 로드 검증. WebSocket·Kafka 사용으로 RANDOM_PORT + EmbeddedKafka 필요 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class GatewayApplicationTests {

	@Test
	void contextLoads() {
	}
}
