package com.wordawake.gateway.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.util.Collections;

/**
 * 프로필이 "embedded" 일 때 내장 Kafka를 먼저 띄우고, 그 주소를
 * spring.kafka.bootstrap-servers 에 넣어서 Gateway가 별도 Kafka 없이 기동되게 함.
 * <p>
 * META-INF/spring.factories 에 등록되어 컨텍스트 준비 단계에서 실행됨.
 */
public class EmbeddedKafkaInitializer implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {

	private static EmbeddedKafkaBroker broker; // EmbeddedKafkaKraftBroker 인스턴스

	@Override
	public void initialize(org.springframework.context.ConfigurableApplicationContext applicationContext) {
		ConfigurableEnvironment env = applicationContext.getEnvironment();
		if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("embedded"))) {
			return;
		}
		// 내장 브로커 1개, 파티션 1, audio-stream / hotword-events 토픽 자동 생성 (KRaft 모드)
		broker = new EmbeddedKafkaKraftBroker(1, 1, "audio-stream", "hotword-events");
		broker.afterPropertiesSet();
		String bootstrapServers = broker.getBrokersAsString();
		env.getPropertySources().addFirst(
				new MapPropertySource("embeddedKafka",
						Collections.singletonMap("spring.kafka.bootstrap-servers", bootstrapServers)));
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (broker != null) {
				broker.destroy();
			}
		}));
	}
}
