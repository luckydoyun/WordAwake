package com.wordawake.gateway.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 클라이언트가 WebSocket으로 보낸 음성 청크를 Kafka 토픽(audio-stream)에 발행.
 * Hotword 서비스(Python 등)가 이 토픽을 구독해 스트리밍 인식 수행.
 *
 * 메시지 형식: key = sessionId, value = 오디오 바이트(원본 그대로)
 * - key=sessionId 로 같은 클라이언트 청크가 같은 파티션으로 가서 순서 유지
 */
@Slf4j
@Component
public class AudioStreamProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final String topicName;

    public AudioStreamProducer(
            KafkaTemplate<String, byte[]> audioStreamKafkaTemplate,
            @Qualifier("audioStreamTopicName") String topicName) {
        this.kafkaTemplate = audioStreamKafkaTemplate;
        this.topicName = topicName;
    }

    /**
     * 오디오 청크를 audio-stream 토픽에 발행.
     * key=sessionId 로 파티션되어, 같은 클라이언트의 청크 순서가 유지됨.
     */
    public void send(String sessionId, byte[] audioChunk) {
        kafkaTemplate.send(topicName, sessionId, audioChunk);
        log.debug("[AudioStreamProducer] 발행 - sessionId={}, size={} bytes", sessionId, audioChunk.length);
    }
}
