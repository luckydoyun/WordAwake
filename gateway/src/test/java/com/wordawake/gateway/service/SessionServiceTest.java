package com.wordawake.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionService 단위 테스트.
 * 등록/해제/조회/발송이 설계대로 동작하는지 검증.
 */
class SessionServiceTest {

    SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService();
    }

    @Test
    void registerAndGet_returnsRegisteredSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        sessionService.register("s1", session);

        assertSame(session, sessionService.get("s1"));
        assertNull(sessionService.get("unknown"));
    }

    @Test
    void unregister_removesSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        sessionService.register("s1", session);
        assertNotNull(sessionService.get("s1"));

        sessionService.unregister("s1");

        assertNull(sessionService.get("s1"));
    }

    @Test
    void sendToSession_whenSessionOpen_sendsAndReturnsTrue() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        sessionService.register("s1", session);

        boolean sent = sessionService.sendToSession("s1", "hello");

        assertTrue(sent);
        verify(session).sendMessage(argThat(msg ->
                msg instanceof TextMessage && "hello".equals(((TextMessage) msg).getPayload())));
    }

    @Test
    void sendToSession_whenSessionClosed_returnsFalse() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        sessionService.register("s1", session);

        boolean sent = sessionService.sendToSession("s1", "hello");

        assertFalse(sent);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendToSession_whenSessionMissing_returnsFalse() {
        boolean sent = sessionService.sendToSession("none", "hello");
        assertFalse(sent);
    }
}
