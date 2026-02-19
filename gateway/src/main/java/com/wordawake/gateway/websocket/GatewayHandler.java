package com.wordawake.gateway.websocket;

import com.google.gson.Gson;
import com.wordawake.gateway.kafka.AudioStreamProducer;
import com.wordawake.gateway.service.SessionService;
import com.wordawake.gateway.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket 요청 처리.
 * - 연결 시: 세션 등록 + 클라이언트에 sessionId 안내 (Hotword 결과 수신 시 식별용)
 * - 바이너리(음성 청크): Kafka audio-stream 토픽에 발행 → Hotword 서비스가 구독
 * - 텍스트: 에코 (설정/핑 등용)
 * - 종료 시: 세션 해제
 */
@Slf4j
@Component
public class GatewayHandler extends AbstractWebSocketHandler {

    private final SessionService sessionService;
    private final AudioStreamProducer audioStreamProducer;
    private final GatewaySessionManager gatewaySessionManager;

    public GatewayHandler(SessionService sessionService, AudioStreamProducer audioStreamProducer, GatewaySessionManager gatewaySessionManager) {
        this.sessionService = sessionService;
        this.audioStreamProducer = audioStreamProducer;
        this.gatewaySessionManager = gatewaySessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        Gson gson = new Gson();  // 임시 직접 생성
        log.info("[Gateway] WebSocket 연결 수립 - sessionId={}", sessionId);

        // 세션 저장 : 테스트용
        gatewaySessionManager.addSession(session);
        // 세션 저장: Hotword 감지 시 이 sessionId로 클라이언트에게 전달하기 위함
        sessionService.register(sessionId, session);

        // 클라이언트가 자신의 sessionId를 알 수 있도록 전달.
        // Hotword 서비스가 hotword-events 발행 시 이 sessionId를 key로 사용하면, 이 클라이언트에게만 결과 전달됨.
        // response 생성
        Map<String, Object> response = new HashMap<String, Object>();
        response.put(Constants.GW_MSG_STATUS, "101");
        response.put(Constants.GW_MSG_MESSAGE, "success");
        response.put(Constants.GW_MSG_SESSION_ID, sessionId);

        // response 전송
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String text = message.getPayload();
        log.info("[Gateway] TEXT 수신 - sessionId={}, payload={}", session.getId(), text);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] audioData = message.getPayload().array();
//        log.info("[Gateway] BINARY(음성) 수신 - sessionId={}, size={} bytes", session.getId(), audioData.length);

        // 청크 단위로 Kafka에 발행 → Hotword 서비스가 구독해 분석
        // Kafka 비동기 발행 (블로킹 최소화)
        CompletableFuture.runAsync(() -> audioStreamProducer.send(session.getId(), audioData));

        // 필요 시 클라이언트 ACK
//        session.sendMessage(new TextMessage("{\"ack\":\"received\"}"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[Gateway] 전송 에러 - sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("[Gateway] WebSocket 연결 종료 - sessionId={}, code={}, reason={}",
                session.getId(), closeStatus.getCode(), closeStatus.getReason());
        sessionService.unregister(session.getId());
        gatewaySessionManager.removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
