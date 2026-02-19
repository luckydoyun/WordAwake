package com.wordawake.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 세션 보관소.
 * - Hotword 감지 결과를 "어느 클라이언트에게 보낼지" 알기 위해 sessionId → WebSocketSession 매핑 유지
 * - 연결 시 등록, 종료 시 해제
 */
@Slf4j
@Service
public class SessionService {

    /** sessionId(WebSocketSession.getId()) → WebSocketSession */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 연결 직후 호출. 세션을 저장해 두어 나중에 Hotword 결과를 해당 클라이언트에게 전달할 수 있게 함.
     */
    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.debug("[SessionService] 세션 등록 - sessionId={}, 현재 수={}", sessionId, sessions.size());
    }

    /**
     * 연결 종료 시 호출. 보관 중이던 세션 제거.
     */
    public void unregister(String sessionId) {
        WebSocketSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("[SessionService] 세션 해제 - sessionId={}, 현재 수={}", sessionId, sessions.size());
        }
    }

    /**
     * sessionId에 해당하는 WebSocketSession 조회. 없으면 null.
     */
    public WebSocketSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 해당 sessionId의 클라이언트에게 텍스트 메시지 전송.
     * 세션이 없거나 이미 닫혀 있으면 전송하지 않고 false 반환.
     *
     * @return 전송 성공 여부
     */
    public boolean sendToSession(String sessionId, String text) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("[SessionService] 세션 없음 - sessionId={}", sessionId);
            return false;
        }
        if (!session.isOpen()) {
            log.warn("[SessionService] 세션 이미 닫힘 - sessionId={}", sessionId);
            return false;
        }
        try {
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (IOException e) {
            log.error("[SessionService] 메시지 전송 실패 - sessionId={}", sessionId, e);
            return false;
        }
    }
}
