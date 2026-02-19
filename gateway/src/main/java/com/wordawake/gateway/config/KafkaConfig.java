package com.wordawake.gateway.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정.
 * - audio-stream: 오디오 청크 발행용 Producer (key=sessionId, value=byte[])
 * - hotword-events: Hotword 서비스가 발행한 감지 결과 구독은 Spring Boot 기본 Consumer 설정 사용
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.audio-stream:audio-stream}")
    private String audioStreamTopic;

    @Value("${app.kafka.topic.hotword-events:hotword-events}")
    private String hotwordEventsTopic;

    /** 테스트/운영 공통: 토픽이 없으면 생성 (파티션 1, 복제 1) */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic audioStreamTopicBean() {
        return TopicBuilder.name(audioStreamTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic hotwordEventsTopicBean() {
        return TopicBuilder.name(hotwordEventsTopic).partitions(1).replicas(1).build();
    }

    /** 오디오 청크 전용 Producer 설정 (value = byte[]) */
    @Bean
    public ProducerFactory<String, byte[]> audioProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /** 오디오 스트림 토픽에 발행할 때 사용하는 KafkaTemplate */
    @Bean
    public KafkaTemplate<String, byte[]> audioStreamKafkaTemplate(
            ProducerFactory<String, byte[]> audioProducerFactory) {
        return new KafkaTemplate<>(audioProducerFactory);
    }

    /** 토픽 이름을 다른 빈에서 주입받을 때 사용 (선택) */
    @Bean(name = "audioStreamTopicName")
    public String audioStreamTopicName() {
        return audioStreamTopic;
    }
}
