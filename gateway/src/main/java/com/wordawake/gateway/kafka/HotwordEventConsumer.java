package com.wordawake.gateway.kafka;

import com.wordawake.gateway.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Hotword 서비스가 발행한 감지 결과(hotword-events)를 구독.
 * 수신한 메시지의 key(sessionId)로 SessionService에서 WebSocket 세션을 찾아,
 * 해당 클라이언트에게만 value(JSON)를 텍스트 메시지로 전달.
 *
 * 수신 메시지: key = sessionId, value = JSON 예: {"word":"헤이 워드"}
 */
@Slf4j
@Component
public class HotwordEventConsumer {

    private final SessionService sessionService;

    public HotwordEventConsumer(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @KafkaListener(topics = "${app.kafka.topic.hotword-events:hotword-events}", groupId = "${spring.kafka.consumer.group-id:gateway-hotword-consumer}")
    public void onHotwordEvent(
            String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String sessionId) {
        if (sessionId == null) {
            log.warn("[HotwordEventConsumer] sessionId 없음 - payload={}", payload);
            return;
        }
        // payload(JSON)를 그대로 클라이언트에 전달. "word" 필드 여부는 로그용으로만 확인
        if (payload != null && !payload.contains("\"word\"")) {
            log.debug("[HotwordEventConsumer] payload에 'word' 필드 없을 수 있음 - payload={}", payload);
        }

        boolean sent = sessionService.sendToSession(sessionId, payload);
        if (sent) {
            log.info("[HotwordEventConsumer] 클라이언트 전달 완료 - sessionId={}, payload={}", sessionId, payload);
        } else {
            log.warn("[HotwordEventConsumer] 클라이언트 전달 실패(세션 없음/종료) - sessionId={}", sessionId);
        }
    }
}
