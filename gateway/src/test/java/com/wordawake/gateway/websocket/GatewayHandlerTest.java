package com.wordawake.gateway.websocket;

import com.wordawake.gateway.kafka.AudioStreamProducer;
import com.wordawake.gateway.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * GatewayHandler 단위 테스트.
 * - 연결 시 세션 등록 + welcome(sessionId) 전송
 * - 텍스트 메시지 시 에코
 * - 바이너리 메시지 시 Kafka 발행
 * - 종료 시 세션 해제
 */
@ExtendWith(MockitoExtension.class)
class GatewayHandlerTest {

    GatewayHandler handler;

    @Mock
    SessionService sessionService;

    @Mock
    AudioStreamProducer audioStreamProducer;

    @Mock
    WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new GatewayHandler(sessionService, audioStreamProducer, null);
        // sessionId 사용 테스트에서만 쓰이므로 lenient (미사용 시 UnnecessaryStubbing 방지)
        lenient().when(session.getId()).thenReturn("test-session-id");
    }

    @Test
    void afterConnectionEstablished_registersSessionAndSendsWelcome() throws Exception {
        handler.afterConnectionEstablished(session);

        verify(sessionService).register(eq("test-session-id"), same(session));
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"sessionId\":\"test-session-id\""));
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"connected\""));
    }

    @Test
    void handleTextMessage_sendsGatewayEcho() throws Exception {
        handler.handleMessage(session, new TextMessage("ping"));

        verify(session).sendMessage(argThat((WebSocketMessage<?> msg) ->
                msg instanceof TextMessage && "gateway echo: ping".equals(((TextMessage) msg).getPayload())));
    }

    @Test
    void handleBinaryMessage_sendsToAudioStreamProducerAndAck() throws Exception {
        byte[] chunk = new byte[]{1, 2, 3};
        handler.handleBinaryMessage(session, new BinaryMessage(ByteBuffer.wrap(chunk)));

        verify(audioStreamProducer).send(eq("test-session-id"), eq(chunk));
        verify(session).sendMessage(argThat((WebSocketMessage<?> msg) ->
                msg instanceof TextMessage && ((TextMessage) msg).getPayload().contains("\"ack\":\"received\"")));
    }

    @Test
    void afterConnectionClosed_unregistersSession() throws Exception {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionService).unregister("test-session-id");
    }

    @Test
    void supportsPartialMessages_returnsFalse() {
        assertFalse(handler.supportsPartialMessages());
    }
}
